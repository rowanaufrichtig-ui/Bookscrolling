package com.bookscrolling.app.data

import org.junit.Test
import java.io.File

class LiveDownloadTest {
    @Test fun live() {
        if (System.getenv("LIVE") == null) return
        val f = File.createTempFile("download", ".epub")
        val page = Gutenberg.fetchPage(Gutenberg.searchUrl(""))
        println("page: ${page.books.take(3)} next=${page.nextUrl}")
        Gutenberg.downloadEpub(page.books.first().id, f) {}
        println("size ${f.length()}")
        val b = EpubParser.parse(f)
        println("${b.title} ${b.paragraphs.size}")
    }
}
