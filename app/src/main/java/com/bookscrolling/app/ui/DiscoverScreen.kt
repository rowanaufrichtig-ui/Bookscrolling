package com.bookscrolling.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bookscrolling.app.AppViewModel
import com.bookscrolling.app.data.BookRepository
import com.bookscrolling.app.data.GutenbergBook

@Composable
fun DiscoverScreen(vm: AppViewModel) {
    val state by vm.discover.collectAsStateWithLifecycleCompat()
    val downloads by vm.downloads.collectAsStateWithLifecycleCompat()
    val library by vm.library.collectAsStateWithLifecycleCompat()
    val owned = remember(library) { library.map { it.id }.toSet() }
    var query by rememberSaveable { mutableStateOf(state.query) }
    val listState = rememberLazyListState()

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.nextUrl) { if (nearEnd && state.books.isNotEmpty()) vm.loadMore() }

    Column(Modifier.fillMaxSize()) {
        Header("Project Gutenberg", "70,000+ free public-domain books")
        SearchField(
            value = query, onValueChange = { query = it }, placeholder = "Search titles or authors",
            modifier = Modifier.padding(horizontal = 16.dp), onSearch = vm::search,
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            if (state.query.isBlank() && state.books.isNotEmpty()) item {
                SectionLabel("Most popular")
            }
            items(state.books, key = { it.id }) { book ->
                GutenbergRow(
                    book = book,
                    owned = BookRepository.gutenbergId(book.id) in owned,
                    progress = downloads[book.id],
                    onClick = { vm.openGutenberg(book) },
                )
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = {
                                if (state.books.isEmpty()) vm.search(state.query) else vm.loadMore()
                            }) { Text("Try again") }
                        }
                        state.loaded && state.books.isEmpty() ->
                            Text("No books found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GutenbergRow(book: GutenbergBook, owned: Boolean, progress: Float?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(book.coverUrl, book.title, 52.dp, 78.dp)
        Column(Modifier.weight(1f)) {
            Text(
                book.title, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Serif,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) Text(
                book.author, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            progress != null -> if (progress < 0f) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            owned -> Text("Read", style = MaterialTheme.typography.labelLarge)
            else -> Text("Get", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Header(title: String, subtitle: String? = null, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
            if (subtitle != null) Text(
                subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
