package com.bookscrolling.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    private fun epub(files: Map<String, String>, binary: Map<String, ByteArray> = emptyMap()): File {
        val f = File.createTempFile("test", ".epub")
        f.deleteOnExit()
        ZipOutputStream(f.outputStream()).use { zip ->
            (files.mapValues { it.value.toByteArray() } + binary).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return f
    }

    private val container = """<?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>"""

    private val opf = """<?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Test Book</dc:title><dc:creator>Ada Writer</dc:creator>
            <meta name="cover" content="cov"/>
          </metadata>
          <manifest>
            <item id="cov" href="images/cover%20art.jpg" media-type="image/jpeg"/>
            <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
        </package>"""

    @Test
    fun parsesMetadataCoverAndParagraphsInOrder() {
        val file = epub(
            mapOf(
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to opf,
                "OEBPS/text/ch1.xhtml" to """<html><body><div class="chapter">
                    <h2>CHAPTER I.<br/>The Start</h2>
                    <p>First   paragraph
                       here.</p>
                    <p> </p>
                    <div><p>Nested <i>paragraph</i>.</p></div>
                    <img src="x.png"/>
                    </div></body></html>""",
                "OEBPS/text/ch2.xhtml" to """<html><body><h2>Two</h2><p>Line one<br/>Line two</p>
                    <blockquote>Bare quote text</blockquote>
                    <table><tr><td><a href="#">I.</a></td><td>Start</td></tr><tr><td>II.</td><td>Two</td></tr></table>
                    </body></html>""",
            ),
            mapOf("OEBPS/images/cover art.jpg" to byteArrayOf(1, 2, 3)),
        )
        val book = EpubParser.parse(file)
        assertEquals("Test Book", book.title)
        assertEquals("Ada Writer", book.author)
        assertNotNull(book.coverBytes)
        assertEquals("jpg", book.coverExt)
        assertEquals(
            listOf(
                Para(true, "CHAPTER I.\nThe Start", "CHAPTER I. · The Start"),
                Para(false, "First paragraph here.", "CHAPTER I. · The Start"),
                Para(false, "Nested paragraph.", "CHAPTER I. · The Start"),
                Para(true, "Two", "Two"),
                Para(false, "Line one\nLine two", "Two"),
                Para(false, "Bare quote text", "Two"),
                Para(false, "I.  ·  Start\nII.  ·  Two", "Two"),
            ),
            book.paragraphs,
        )
    }

    @Test(expected = EpubException::class)
    fun rejectsNonEpub() {
        val f = File.createTempFile("bad", ".epub")
        f.writeText("not a zip")
        EpubParser.parse(f)
    }

    @Test
    fun realGutenbergEpubIfAvailable() {
        val path = System.getenv("SAMPLE_EPUB") ?: return
        val book = EpubParser.parse(File(path))
        println("${book.title} / ${book.author}: ${book.paragraphs.size} paragraphs, cover=${book.coverBytes?.size}")
        book.paragraphs.take(40).forEach { println((if (it.heading) "# " else "  ") + it.text.take(100).replace("\n", " / ")) }
        assertTrue(book.paragraphs.size > 100)
    }
}

class GutenbergTest {
    @Test
    fun parsesOpdsFeed() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
            <link rel="next" href="/ebooks/search.opds/?query=x&amp;start_index=26"/>
            <entry><id>https://www.gutenberg.org/ebooks/authors/search.opds/?query=x</id><title>Authors</title></entry>
            <entry><id>https://www.gutenberg.org/ebooks/1342.opds</id><title>Pride and Prejudice</title><content type="text">Jane Austen</content></entry>
            <entry><id>https://www.gutenberg.org/ebooks/37683.opds</id><title>Dictionary</title><content type="text">94005 downloads</content></entry>
            </feed>"""
        val page = Gutenberg.parseFeed(xml)
        assertEquals(listOf(GutenbergBook(1342, "Pride and Prejudice", "Jane Austen"), GutenbergBook(37683, "Dictionary", "")), page.books)
        assertEquals("https://www.gutenberg.org/ebooks/search.opds/?query=x&start_index=26", page.nextUrl)
    }
}
