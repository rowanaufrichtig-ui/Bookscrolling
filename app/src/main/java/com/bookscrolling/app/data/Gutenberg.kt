package com.bookscrolling.app.data

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GutenbergBook(val id: Int, val title: String, val author: String) {
    val coverUrl get() = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg"
}

data class GutenbergPage(val books: List<GutenbergBook>, val nextUrl: String?)

/** Talks to Project Gutenberg's public OPDS catalog. */
object Gutenberg {
    private const val BASE = "https://www.gutenberg.org"
    private val bookId = Regex("/ebooks/(\\d+)\\.opds$")
    private val downloadsOnly = Regex("^[\\d,]+ downloads$")

    fun searchUrl(query: String): String {
        val q = query.trim()
        return if (q.isEmpty()) "$BASE/ebooks/search.opds/?sort_order=downloads"
        else "$BASE/ebooks/search.opds/?sort_order=downloads&query=" + URLEncoder.encode(q, "UTF-8")
    }

    fun fetchPage(url: String): GutenbergPage = parseFeed(httpGet(url).toString(Charsets.UTF_8))

    fun parseFeed(xml: String): GutenbergPage {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val books = doc.getElementsByTag("entry").mapNotNull { entry ->
            val id = bookId.find(entry.getElementsByTag("id").text().trim())?.groupValues?.get(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val content = entry.getElementsByTag("content").text().trim()
            GutenbergBook(
                id = id,
                title = entry.getElementsByTag("title").text().trim(),
                author = if (downloadsOnly.matches(content)) "" else content,
            )
        }
        val next = doc.getElementsByTag("link").firstOrNull { it.attr("rel") == "next" }?.attr("href")
        return GutenbergPage(books, next?.let { if (it.startsWith("http")) it else BASE + it })
    }

    /** Downloads the plain (image-free, but cover-included) EPUB to [dest]. */
    fun downloadEpub(id: Int, dest: File, onProgress: (Float) -> Unit) {
        download("$BASE/ebooks/$id.epub.noimages", dest, onProgress)
    }

    fun download(url: String, dest: File, onProgress: (Float) -> Unit = {}) {
        val conn = open(url)
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(url: String): ByteArray {
        val conn = open(url)
        try {
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Bookscrolling/1.0 (Android)")
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw java.io.IOException("Bad redirect")
                current = URL(URL(current), location).toString()
                conn.disconnect()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw java.io.IOException("Server returned $code")
            }
            return conn
        }
        throw java.io.IOException("Too many redirects")
    }
}
