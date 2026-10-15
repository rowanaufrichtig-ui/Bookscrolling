package com.bookscrolling.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookscrolling.app.AppViewModel
import com.bookscrolling.app.data.Book
import java.io.File

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val books by vm.library.collectAsStateWithLifecycleCompat()
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importEpub(uri)
    }
    val addBook = { picker.launch(arrayOf("application/epub+zip", "application/octet-stream", "application/zip", "*/*")) }

    var alphabetical by rememberSaveable { mutableStateOf(true) }
    val filtered = remember(books, query, alphabetical) {
        val q = query.trim()
        val matching = if (q.isEmpty()) books
        else books.filter { it.title.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true) }
        if (alphabetical) matching.sortedWith(
            compareBy<Book>({ ALPHABET.indexOf(indexLetter(it.title, ignoreArticles = true)) }, { sortTitle(it.title).lowercase() }),
        ) else matching
    }

    Column(Modifier.fillMaxSize()) {
        Header("My Books", "${books.size} on your shelf") {
            if (books.isNotEmpty()) TextButton(onClick = { alphabetical = !alphabetical }) {
                Text(if (alphabetical) "A–Z" else "Recent", style = MaterialTheme.typography.labelLarge)
            }
            IconButton(onClick = addBook) { Icon(Icons.Outlined.Add, contentDescription = "Add EPUB") }
        }
        if (books.isEmpty()) {
            EmptyState(
                "Your shelf is empty",
                "Add your own EPUB files, or pick a free classic from the Gutenberg tab.",
                Modifier.padding(top = 48.dp),
            ) {
                OutlinedButton(onClick = addBook) { Text("Add an EPUB") }
            }
            return@Column
        }
        SearchField(query, { query = it }, "Search your books", Modifier.padding(horizontal = 16.dp))
        val listState = rememberLazyListState()
        val letters = remember(filtered, alphabetical) {
            if (alphabetical) filtered.map { indexLetter(it.title, ignoreArticles = true) } else emptyList()
        }
        Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
        LazyColumn(Modifier.fillMaxSize().padding(end = if (letters.size >= 15) 24.dp else 0.dp), state = listState) {
            items(filtered, key = { it.id }) { book ->
                LibraryRow(book, onOpen = { vm.openBookNow(book.id) }, onLongPress = { pendingDelete = book })
            }
            if (filtered.isEmpty()) item {
                Text(
                    "No matches", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        AlphabetIndex(listState, letters)
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove book?") },
            text = { Text("“${book.title}” and its likes and comments will be deleted from this device.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteBook(book.id); pendingDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(book: Book, onOpen: () -> Unit, onLongPress: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(book.coverPath?.let(::File), book.title, 52.dp, 78.dp)
        Column(Modifier.weight(1f)) {
            Text(
                book.title, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Serif,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                book.author, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.width(80.dp).height(2.dp).clip(MaterialTheme.shapes.small),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    drawStopIndicator = {},
                )
                Text(
                    "  ${(book.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
