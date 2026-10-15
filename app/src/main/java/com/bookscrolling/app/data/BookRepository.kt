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
        val tmp = File.createTempFile("dl", ".epub", context.cacheDir)
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

    private fun store(id: String, epub: File, source: String, fallbackCoverUrl: String?): Book {
        val parsed = EpubParser.parse(epub)
        val d = dir(id).apply { deleteRecursively(); mkdirs() }
        var cover: File? = null
        if (parsed.coverBytes != null) {
            cover = File(d, "cover.${parsed.coverExt}").apply { writeBytes(parsed.coverBytes) }
        } else if (fallbackCoverUrl != null) {
            cover = runCatching { File(d, "cover.jpg").also { Gutenberg.download(fallbackCoverUrl, it) } }.getOrNull()
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

    fun delete(id: String) {
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
    }
}
