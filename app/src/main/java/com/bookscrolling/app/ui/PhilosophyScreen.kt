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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.bookscrolling.app.data.SepEntry

@Composable
fun PhilosophyScreen(vm: AppViewModel) {
    val state by vm.sep.collectAsStateWithLifecycleCompat()
    val downloading by vm.sepDownloads.collectAsStateWithLifecycleCompat()
    val library by vm.library.collectAsStateWithLifecycleCompat()
    val progress = remember(library) { library.associate { it.id to it.progress } }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadSep() }

    val filtered = remember(state.entries, query) {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) state.entries
        else state.entries.filter { e ->
            val hay = (e.title + " " + e.author).lowercase()
            words.all { it in hay }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Philosophy", "Stanford Encyclopedia of Philosophy") {
            IconButton(
                onClick = { state.entries.randomOrNull()?.let(vm::openSep) },
                enabled = state.entries.isNotEmpty(),
            ) { Icon(Icons.Outlined.Shuffle, contentDescription = "Random article") }
        }
        SearchField(query, { query = it }, "Search ${state.entries.size.takeIf { it > 0 } ?: ""} articles", Modifier.padding(horizontal = 16.dp))
        val listState = rememberLazyListState()
        val letters = remember(filtered) { filtered.map { indexLetter(it.title) } }
        Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
        LazyColumn(Modifier.fillMaxSize().padding(end = if (letters.size >= 15) 24.dp else 0.dp), state = listState) {
            items(filtered, key = { it.slug }) { entry ->
                SepRow(
                    entry = entry,
                    loading = entry.slug in downloading,
                    progress = progress[BookRepository.sepId(entry.slug)],
                    onClick = { vm.openSep(entry) },
                )
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { vm.loadSep(forceRefresh = true) }) { Text("Try again") }
                        }
                        state.entries.isNotEmpty() && filtered.isEmpty() ->
                            Text("No articles found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        AlphabetIndex(listState, letters)
        }
    }
}

@Composable
private fun SepRow(entry: SepEntry, loading: Boolean, progress: Float?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.title.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (entry.author.isNotBlank()) Text(
                entry.author, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            progress != null -> Text(
                "${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
