package com.bookscrolling.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bookscrolling.app.AppViewModel

@Composable
fun App(vm: AppViewModel) {
    var openBook by rememberSaveable { mutableStateOf<String?>(null) }
    var openPost by rememberSaveable { mutableStateOf<Int?>(null) }
    val fontSize by vm.fontSize.collectAsStateWithLifecycleCompat()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val importing by vm.importing.collectAsStateWithLifecycleCompat()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.openBook.collect { openPost = it.post; openBook = it.bookId } }
    val closeBook = { openBook = null; openPost = null; vm.refreshLibrary(); if (tab == 3) vm.loadSaved() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val current = openBook
        if (current != null) {
            BackHandler(onBack = closeBook)
            ReaderScreen(
                repo = vm.repo, bookId = current, initialPost = openPost,
                fontSize = fontSize, onFontSize = vm::setFontSize, onBack = closeBook,
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                            val colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                            val tabs = listOf(
                                Triple("Gutenberg", Icons.Filled.Explore, Icons.Outlined.Explore),
                                Triple("Philosophy", Icons.Filled.Psychology, Icons.Outlined.Psychology),
                                Triple("My Books", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
                                Triple("Bookmarks", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
                            )
                            tabs.forEachIndexed { i, (label, selectedIcon, icon) ->
                                NavigationBarItem(
                                    selected = tab == i, onClick = { tab = i }, colors = colors,
                                    icon = { Icon(if (tab == i) selectedIcon else icon, null) },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (tab) {
                        0 -> DiscoverScreen(vm)
                        1 -> PhilosophyScreen(vm)
                        2 -> LibraryScreen(vm)
                        else -> SavedScreen(vm)
                    }
                }
            }
        }
        if (importing) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Preparing your book…", Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}
