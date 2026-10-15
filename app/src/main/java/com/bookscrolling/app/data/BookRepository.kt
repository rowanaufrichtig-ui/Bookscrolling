package com.bookscrolling.app.data

import android.content.Context
import android.net.Uri
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

class BookRepository(private val context: Context) {
    val db = Db(context)
    private val root = File(context.filesDir, "books").apply { mkdirs() }

    private fun dir(id: String) = File(root, id)

    fun hasBook(id: String) = File(dir(id), POSTS).exists() && db.book(id) != null

    /** Downloads a Project Gutenberg book and adds it to the library. Blocking. */
    fun addGutenberg(g: GutenbergBook, onProgress: (Float) -> Unit): Book {
        val id = gutenbergId(g.id)
        db.book(id)?.let { if (File(dir(id), POSTS).exists()) return it }
        val tmp = File.createTempFile("download", ".epub", context.cacheDir)
        try {
            Gutenberg.downloadEpub(g.id, tmp, onProgress)
            return store(id, tmp, Book.SOURCE_GUTENBERG, fallbackCoverUrl = g.coverUrl)
        } finally {
            tmp.delete()
        }
    }

    /** Copies an EPUB picked by the user into the library. Blocking. */
    fun addLocal(uri: Uri): Book {
        val tmp = File.createTempFile("import", ".epub", context.cacheDir)
        try {
            val digest = MessageDigest.getInstance("SHA-1")
            (context.contentResolver.openInputStream(uri) ?: throw EpubException("Could not open file")).use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            val id = "l" + digest.digest().take(8).joinToString("") { "%02x".format(it) }
            db.book(id)?.let { if (File(dir(id), POSTS).exists()) return it }
            return store(id, tmp, Book.SOURCE_LOCAL, fallbackCoverUrl = null)
        } finally {
            tmp.delete()
        }
    }

    /** Downloads a Stanford Encyclopedia of Philosophy article and adds it to the library. Blocking. */
    fun addSep(entry: SepEntry): Book {
        val id = sepId(entry.slug)
        db.book(id)?.let { if (File(dir(id), POSTS).exists()) return it }
        val article = Sep.fetchArticle(entry.slug)
        val d = dir(id).apply { deleteRecursively(); mkdirs() }
        writePosts(File(d, POSTS), article.paragraphs)
        val book = Book(
            id = id, title = article.title, author = entry.author.ifBlank { article.author }, coverPath = null,
            source = Book.SOURCE_SEP, postCount = article.paragraphs.size + 1, position = 0,
            addedAt = System.currentTimeMillis(),
        )
        db.upsertBook(book)
        return book
    }

    private fun store(id: String, epub: File, source: String, fallbackCoverUrl: String?): Book {
        val parsed = EpubParser.parse(epub)
        val d = dir(id).apply { deleteRecursively(); mkdirs() }
        var cover: File? = null
        if (parsed.coverBytes != null) {
            cover = File(d, "cover.${parsed.coverExt}").apply { writeBytes(parsed.coverBytes) }
        } else if (fallbackCoverUrl != null) {
            cover = runCatching { File(d, "cover.jpg").also { Http.download(fallbackCoverUrl, it) } }.getOrNull()
        }
        writePosts(File(d, POSTS), parsed.paragraphs)
        val book = Book(
            id = id, title = parsed.title, author = parsed.author, coverPath = cover?.absolutePath,
            source = source, postCount = parsed.paragraphs.size + 1, position = 0,
            addedAt = System.currentTimeMillis(),
        )
        db.upsertBook(book)
        return book
    }

    fun posts(id: String): List<Para> = readPosts(File(dir(id), POSTS))

    private val postsCache = object : LinkedHashMap<String, List<Para>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Para>>) = size > 4
    }

    /** Text of a post (0 = cover) for the Bookmarks tab. */
    @Synchronized
    fun excerpt(bookId: String, post: Int, bookTitle: String): String {
        if (post == 0) return "Cover · $bookTitle"
        val posts = postsCache.getOrPut(bookId) { runCatching { posts(bookId) }.getOrDefault(emptyList()) }
        return posts.getOrNull(post - 1)?.text ?: ""
    }

    /** All bookmarks and comments with the passages they point at. */
    fun saved(): Pair<List<SavedItem>, List<SavedItem>> {
        val bookmarks = db.allBookmarks().map { it.copy(excerpt = excerpt(it.bookId, it.post, it.bookTitle)) }
        val comments = db.allComments().map { it.copy(excerpt = excerpt(it.bookId, it.post, it.bookTitle)) }
        return bookmarks to comments
    }

    /** Cached SEP table of contents (refreshed weekly). */
    fun sepContents(forceRefresh: Boolean = false): List<SepEntry> {
        val cache = File(context.filesDir, "sep-contents.html")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 7L * 24 * 3600 * 1000
        if (!fresh || forceRefresh) {
            try {
                val html = Sep.fetchContentsHtml()
                if (Sep.parseContents(html).isNotEmpty()) cache.writeText(html)
            } catch (e: java.io.IOException) {
                if (!cache.exists()) throw e
            }
        }
        return Sep.parseContents(cache.readText())
    }

    fun delete(id: String) {
        synchronized(this) { postsCache.remove(id) }
        db.deleteBook(id)
        dir(id).deleteRecursively()
    }

    private fun writePosts(file: File, paras: List<Para>) {
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.writeInt(paras.size)
            for (p in paras) {
                out.writeBoolean(p.heading)
                writeString(out, p.text)
                writeString(out, p.chapter)
            }
        }
    }

    private fun readPosts(file: File): List<Para> =
        DataInputStream(file.inputStream().buffered()).use { input ->
            val n = input.readInt()
            List(n) { Para(input.readBoolean(), readString(input), readString(input)) }
        }

    private fun writeString(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val bytes = ByteArray(input.readInt())
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        private const val POSTS = "posts.bin"
        fun gutenbergId(id: Int) = "g$id"
        fun sepId(slug: String) = "s$slug"
    }
}
