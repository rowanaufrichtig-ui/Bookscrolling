package com.bookscrolling.app.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bookscrolling.app.data.Book
import com.bookscrolling.app.data.BookRepository
import com.bookscrolling.app.data.Comment
import com.bookscrolling.app.data.Para
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private class ReaderData(val book: Book, val posts: List<Para>, val likes: Set<Int>, val commentCounts: Map<Int, Int>)

/**
 * The feed. Post 0 is the cover; post i (i >= 1) is paragraph i - 1.
 * Likes and comments are keyed by post index.
 */
@Composable
fun ReaderScreen(repo: BookRepository, bookId: String, onBack: () -> Unit) {
    var data by remember(bookId) { mutableStateOf<ReaderData?>(null) }
    var failed by remember(bookId) { mutableStateOf(false) }
    LaunchedEffect(bookId) {
        try {
            data = withContext(Dispatchers.IO) {
                val book = repo.db.book(bookId) ?: error("missing")
                ReaderData(book, repo.posts(bookId), repo.db.likes(bookId), repo.db.commentCounts(bookId))
            }
        } catch (e: Exception) {
            failed = true
        }
    }
    val d = data
    when {
        d != null -> Feed(repo, d, onBack)
        failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("Couldn't open this book", "Try removing it and adding it again.")
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
private fun Feed(repo: BookRepository, data: ReaderData, onBack: () -> Unit) {
    val book = data.book
    val scope = rememberCoroutineScope()
    val listState = remember { LazyListState(firstVisibleItemIndex = book.position.coerceIn(0, data.posts.size)) }
    val likes = remember { mutableStateMapOf<Int, Boolean>().apply { data.likes.forEach { put(it, true) } } }
    val commentCounts = remember { mutableStateMapOf<Int, Int>().apply { putAll(data.commentCounts) } }
    var commentsFor by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }.debounce(400).collect { pos ->
            withContext(Dispatchers.IO) { repo.db.setPosition(book.id, pos) }
        }
    }
    DisposableEffect(Unit) {
        onDispose { repo.db.setPosition(book.id, listState.firstVisibleItemIndex) }
    }

    fun toggleLike(post: Int, forceOn: Boolean = false) {
        val liked = forceOn || likes[post] != true
        if (liked) likes[post] = true else likes.remove(post)
        scope.launch(Dispatchers.IO) { repo.db.setLiked(book.id, post, liked) }
    }

    val total = data.posts.size + 1
    val progress by remember {
        androidx.compose.runtime.derivedStateOf {
            if (total <= 1) 0 else (listState.firstVisibleItemIndex * 100 / (total - 1))
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
            Text(
                book.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$progress%", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(count = total, key = { it }) { index ->
                val liked = likes[index] == true
                val comments = commentCounts[index] ?: 0
                if (index == 0) {
                    CoverPost(book, total - 1)
                } else {
                    ParagraphPost(book, index, data.posts[index - 1], onDoubleTap = { toggleLike(index, forceOn = true) })
                }
                Actions(
                    liked = liked,
                    comments = comments,
                    onLike = { toggleLike(index) },
                    onComment = { commentsFor = index },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) }
        }
    }

    commentsFor?.let { post ->
        ModalBottomSheet(
            onDismissRequest = { commentsFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            CommentsSheet(
                repo = repo,
                bookId = book.id,
                post = post,
                excerpt = if (post == 0) book.title else data.posts[post - 1].text,
                onCountChange = { n -> if (n == 0) commentCounts.remove(post) else commentCounts[post] = n },
            )
        }
    }
}

@Composable
private fun CoverPost(book: Book, paragraphs: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (book.coverPath != null) {
            AsyncImage(
                model = File(book.coverPath),
                contentDescription = "Cover of ${book.title}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(
                Modifier.fillMaxWidth(0.7f).heightIn(min = 360.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(book.title, fontFamily = FontFamily.Serif, fontSize = 26.sp, textAlign = TextAlign.Center, lineHeight = 32.sp)
                    Spacer(Modifier.padding(8.dp))
                    Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        Text(
            book.title, fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 30.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp),
        )
        Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Text(
            "$paragraphs posts · scroll to read",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ParagraphPost(book: Book, index: Int, para: Para, onDoubleTap: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .pointerInput(index) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
            .padding(start = 20.dp, end = 20.dp, top = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    book.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "¶",
                    fontFamily = FontFamily.Serif, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    book.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (para.chapter.isNotBlank() && !para.heading) Text(
                    para.chapter, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("¶ $index", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (para.heading) {
            Text(
                para.text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium,
                fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            )
        } else {
            Text(
                para.text, fontFamily = FontFamily.Serif, fontSize = 17.sp, lineHeight = 27.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun Actions(liked: Boolean, comments: Int, onLike: () -> Unit, onComment: () -> Unit) {
    val scale by animateFloatAsState(if (liked) 1.15f else 1f, label = "like")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onLike) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (liked) "Unlike" else "Like",
                tint = if (liked) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).scale(scale),
            )
        }
        IconButton(onClick = onComment) {
            Icon(
                Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp),
            )
        }
        if (comments > 0) Text(
            "$comments", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 0.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentsSheet(repo: BookRepository, bookId: String, post: Int, excerpt: String, onCountChange: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val comments = remember { mutableStateListOf<Comment>() }
    var draft by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Comment?>(null) }

    LaunchedEffect(post) {
        comments.clear()
        comments.addAll(withContext(Dispatchers.IO) { repo.db.comments(bookId, post) })
    }

    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
        Text("Comments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Text(
            excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 380.dp)) {
            if (comments.isEmpty()) item {
                Text(
                    "No comments yet. Share a thought about this passage.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(20.dp),
                )
            }
            items(comments, key = { it.id }) { c ->
                Column(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = {}, onLongClick = { pendingDelete = c })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("You", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "  " + DateUtils.getRelativeTimeSpanString(c.createdAt).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        if (pendingDelete?.id == c.id) {
                            Text(
                                "Delete", style = MaterialTheme.typography.labelLarge, color = LikeRed,
                                modifier = Modifier.combinedClickable(onClick = {
                                    comments.remove(c)
                                    pendingDelete = null
                                    onCountChange(comments.size)
                                    scope.launch(Dispatchers.IO) { repo.db.deleteComment(c.id) }
                                }),
                            )
                        }
                    }
                    Text(c.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextField(
                value = draft, onValueChange = { draft = it },
                placeholder = { Text("Add a comment…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            IconButton(
                enabled = draft.isNotBlank(),
                onClick = {
                    val body = draft.trim()
                    draft = ""
                    scope.launch {
                        val c = withContext(Dispatchers.IO) { repo.db.addComment(bookId, post, body) }
                        comments.add(c)
                        onCountChange(comments.size)
                    }
                },
            ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Post") }
        }
    }
}
