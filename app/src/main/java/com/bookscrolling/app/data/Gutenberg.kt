package com.bookscrolling.app.data

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
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

    fun fetchPage(url: String): GutenbergPage = parseFeed(Http.getText(url))

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
        Http.download("$BASE/ebooks/$id.epub.noimages", dest, onProgress)
    }
}
