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
import com.bookscrolling.app.data.SavedItem
import com.bookscrolling.app.data.SepEntry
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

data class SepState(
    val entries: List<SepEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class SavedState(
    val bookmarks: List<SavedItem> = emptyList(),
    val comments: List<SavedItem> = emptyList(),
    val loaded: Boolean = false,
)

/** Open [bookId], optionally scrolled to [post] instead of the saved reading position. */
data class OpenRequest(val bookId: String, val post: Int? = null)

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

    private val _openBook = Channel<OpenRequest>(Channel.BUFFERED)
    val openBook = _openBook.receiveAsFlow()

    private val _sep = MutableStateFlow(SepState())
    val sep = _sep.asStateFlow()

    /** Slugs of SEP articles currently downloading. */
    private val _sepDownloads = MutableStateFlow<Set<String>>(emptySet())
    val sepDownloads = _sepDownloads.asStateFlow()

    private val _saved = MutableStateFlow(SavedState())
    val saved = _saved.asStateFlow()

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    private val _fontSize = MutableStateFlow(prefs.getFloat("font_size", DEFAULT_FONT_SIZE))
    val fontSize = _fontSize.asStateFlow()

    private var searchJob: Job? = null

    init {
        refreshLibrary()
        search("")
    }

    fun setFontSize(size: Float) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        _fontSize.value = clamped
        prefs.edit().putFloat("font_size", clamped).apply()
    }

    fun loadSep(forceRefresh: Boolean = false) {
        if (_sep.value.loading) return
        if (_sep.value.entries.isNotEmpty() && !forceRefresh) return
        _sep.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val entries = withContext(Dispatchers.IO) { repo.sepContents(forceRefresh) }
                _sep.value = SepState(entries = entries)
            } catch (e: Exception) {
                _sep.update { it.copy(loading = false, error = "Couldn't reach the Stanford Encyclopedia of Philosophy") }
            }
        }
    }

    fun openSep(entry: SepEntry) {
        if (entry.slug in _sepDownloads.value) return
        _sepDownloads.update { it + entry.slug }
        viewModelScope.launch {
            try {
                val book = withContext(Dispatchers.IO) { repo.addSep(entry) }
                refreshLibrary()
                _openBook.send(OpenRequest(book.id))
            } catch (e: Exception) {
                _messages.send(
                    when (e) {
                        is EpubException -> e.message ?: "Couldn't read this article"
                        is java.io.IOException -> "Download failed. Check your connection."
                        else -> "Couldn't open this article (${e.javaClass.simpleName})"
                    }
                )
            } finally {
                _sepDownloads.update { it - entry.slug }
            }
        }
    }

    fun loadSaved() {
        viewModelScope.launch {
            val (bookmarks, comments) = withContext(Dispatchers.IO) { repo.saved() }
            _saved.value = SavedState(bookmarks, comments, loaded = true)
        }
    }

    fun removeBookmark(item: SavedItem) {
        _saved.update { s -> s.copy(bookmarks = s.bookmarks.filterNot { it.bookId == item.bookId && it.post == item.post }) }
        viewModelScope.launch(Dispatchers.IO) { repo.db.setBookmarked(item.bookId, item.post, false) }
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
                _openBook.send(OpenRequest(id)); return@launch
            }
            _downloads.update { it + (book.id to -1f) }
            try {
                val stored = withContext(Dispatchers.IO) {
                    repo.addGutenberg(book) { p -> _downloads.update { it + (book.id to p) } }
                }
                refreshLibrary()
                _openBook.send(OpenRequest(stored.id))
            } catch (e: Exception) {
                _messages.send(
                    when (e) {
                        is EpubException -> e.message ?: "Couldn't read this book"
                        is java.io.IOException -> "Download failed. Check your connection."
                        else -> "Couldn't add this book (${e.javaClass.simpleName})"
                    }
                )
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
                _openBook.send(OpenRequest(book.id))
            } catch (e: Exception) {
                _messages.send((e as? EpubException)?.message ?: "Couldn't open that file as an EPUB")
            } finally {
                _importing.value = false
            }
        }
    }

    fun openBookNow(id: String, post: Int? = null) {
        viewModelScope.launch { _openBook.send(OpenRequest(id, post)) }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            refreshLibrary()
        }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 17f
        const val MIN_FONT_SIZE = 13f
        const val MAX_FONT_SIZE = 28f
    }
}
