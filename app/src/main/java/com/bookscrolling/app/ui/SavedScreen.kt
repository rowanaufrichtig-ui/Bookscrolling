package com.bookscrolling.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookscrolling.app.AppViewModel
import com.bookscrolling.app.data.SavedItem

@Composable
fun SavedScreen(vm: AppViewModel) {
    val state by vm.saved.collectAsStateWithLifecycleCompat()
    var section by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.loadSaved() }

    Column(Modifier.fillMaxSize()) {
        Header("Bookmarks", "Passages you saved and what you said about them")
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SegmentTab("Bookmarks · ${state.bookmarks.size}", section == 0) { section = 0 }
            SegmentTab("Comments · ${state.comments.size}", section == 1) { section = 1 }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val items = if (section == 0) state.bookmarks else state.comments
        if (state.loaded && items.isEmpty()) {
            EmptyState(
                if (section == 0) "No bookmarks yet" else "No comments yet",
                if (section == 0) "Tap the bookmark under any paragraph, or double-tap the text, to save it here."
                else "Swipe a paragraph to the right to comment on it.",
                Modifier.padding(top = 40.dp),
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { "${it.bookId}:${it.post}:${it.commentId}" }) { item ->
                SavedRow(
                    item = item,
                    onOpen = { vm.openBookNow(item.bookId, item.post) },
                    onRemoveBookmark = if (section == 0) ({ vm.removeBookmark(item) }) else null,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SegmentTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier.clickable(onClick = onClick)
            .drawBehind {
                if (selected) drawLine(color, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 4f)
            }
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SavedRow(item: SavedItem, onOpen: () -> Unit, onRemoveBookmark: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.bookTitle, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "  ¶ ${item.post}" + if (item.createdAt > 0) " · " + DateUtils.getRelativeTimeSpanString(item.createdAt) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (item.comment != null) {
                Text(item.comment, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 8.dp)) {
                    Box(
                        Modifier.width(2.dp).height(40.dp)
                            .drawBehind { drawRect(color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.4f)) },
                    )
                    Text(
                        item.excerpt, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            } else {
                Text(
                    item.excerpt, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (onRemoveBookmark != null) {
            IconButton(onClick = onRemoveBookmark) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Remove bookmark", tint = BookmarkGreen)
            }
        }
    }
}
