package com.bookscrolling.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val importing by vm.importing.collectAsStateWithLifecycleCompat()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.openBook.collect { openBook = it } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val current = openBook
        if (current != null) {
            BackHandler { openBook = null; vm.refreshLibrary() }
            ReaderScreen(vm.repo, current, onBack = { openBook = null; vm.refreshLibrary() })
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                            val colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                            NavigationBarItem(
                                selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                                icon = { Icon(if (tab == 0) Icons.Filled.Explore else Icons.Outlined.Explore, null) },
                                label = { Text("Gutenberg") },
                            )
                            NavigationBarItem(
                                selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                                icon = { Icon(if (tab == 1) Icons.Filled.AutoStories else Icons.Outlined.AutoStories, null) },
                                label = { Text("My Books") },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (tab) {
                        0 -> DiscoverScreen(vm)
                        else -> LibraryScreen(vm)
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
