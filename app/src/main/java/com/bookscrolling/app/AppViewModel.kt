package com.bookscrolling.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bookscrolling.app.data.Book
import com.bookscrolling.app.data.BookRepository
import com.bookscrolling.app.data.EpubException
import com.bookscrolling.app.data.Gutenberg
import com.bookscrolling.app.data.GutenbergBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiscoverState(
    val query: String = "",
    val books: List<GutenbergBook> = emptyList(),
    val nextUrl: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = BookRepository(app)

    private val _library = MutableStateFlow<List<Book>>(emptyList())
    val library = _library.asStateFlow()

    private val _discover = MutableStateFlow(DiscoverState())
    val discover = _discover.asStateFlow()

    /** Gutenberg id -> download progress (0..1, or -1 while unknown). */
    private val _downloads = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloads = _downloads.asStateFlow()

    /** Non-null while a local EPUB is being imported. */
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _openBook = Channel<String>(Channel.BUFFERED)
    val openBook = _openBook.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        refreshLibrary()
        search("")
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _library.value = withContext(Dispatchers.IO) { repo.db.books() }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        _discover.value = DiscoverState(query = query, loading = true)
        searchJob = viewModelScope.launch { loadPage(Gutenberg.searchUrl(query), append = false) }
    }

    fun loadMore() {
        val state = _discover.value
        val next = state.nextUrl ?: return
        if (state.loading) return
        _discover.update { it.copy(loading = true) }
        searchJob = viewModelScope.launch { loadPage(next, append = true) }
    }

    private suspend fun loadPage(url: String, append: Boolean) {
        try {
            val page = withContext(Dispatchers.IO) { Gutenberg.fetchPage(url) }
            _discover.update {
                val merged = if (append) (it.books + page.books).distinctBy { b -> b.id } else page.books
                it.copy(books = merged, nextUrl = page.nextUrl, loading = false, error = null, loaded = true)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _discover.update { it.copy(loading = false, error = "Couldn't reach Project Gutenberg", loaded = true) }
        }
    }

    fun openGutenberg(book: GutenbergBook) {
        val id = BookRepository.gutenbergId(book.id)
        if (_downloads.value.containsKey(book.id)) return
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { repo.hasBook(id) }) {
                _openBook.send(id); return@launch
            }
            _downloads.update { it + (book.id to -1f) }
            try {
                val stored = withContext(Dispatchers.IO) {
                    repo.addGutenberg(book) { p -> _downloads.update { it + (book.id to p) } }
                }
                refreshLibrary()
                _openBook.send(stored.id)
            } catch (e: Exception) {
                _messages.send(if (e is EpubException) e.message ?: "Couldn't read book" else "Download failed. Check your connection.")
            } finally {
                _downloads.update { it - book.id }
            }
        }
    }

    fun importEpub(uri: Uri) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val book = withContext(Dispatchers.IO) { repo.addLocal(uri) }
                refreshLibrary()
                _openBook.send(book.id)
            } catch (e: Exception) {
                _messages.send((e as? EpubException)?.message ?: "Couldn't open that file as an EPUB")
            } finally {
                _importing.value = false
            }
        }
    }

    fun openBookNow(id: String) {
        viewModelScope.launch { _openBook.send(id) }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            refreshLibrary()
        }
    }
}
