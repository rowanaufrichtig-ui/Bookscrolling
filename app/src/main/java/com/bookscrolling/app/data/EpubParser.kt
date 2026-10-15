package com.bookscrolling.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/** One post in the feed: a paragraph or a heading. */
data class Para(val heading: Boolean, val text: String, val chapter: String)

class ParsedEpub(
    val title: String,
    val author: String,
    val coverBytes: ByteArray?,
    val coverExt: String,
    val paragraphs: List<Para>,
)

class EpubException(message: String) : Exception(message)

/** Minimal EPUB 2/3 reader: metadata, cover image and paragraph text in spine order. */
object EpubParser {

    fun parse(file: File): ParsedEpub = try {
        ZipFile(file).use { parse(it) }
    } catch (e: java.util.zip.ZipException) {
        throw EpubException("This file is not a valid EPUB")
    }

    private fun parse(zip: ZipFile): ParsedEpub {
        fun read(path: String): ByteArray? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        fun readText(path: String): String? = read(path)?.toString(Charsets.UTF_8)

        val container = readText("META-INF/container.xml") ?: throw EpubException("This file is not a valid EPUB")
        val opfPath = Jsoup.parse(container, "", Parser.xmlParser())
            .allElements.firstOrNull { local(it) == "rootfile" }?.attr("full-path")
            ?.takeIf { it.isNotBlank() } ?: throw EpubException("EPUB has no package document")
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opf = Jsoup.parse(readText(opfPath) ?: throw EpubException("EPUB package document missing"), "", Parser.xmlParser())
        val all = opf.allElements

        val title = all.firstOrNull { local(it) == "title" }?.text()?.trim().orEmpty().ifBlank { "Untitled" }
        val author = all.filter { local(it) == "creator" }.joinToString(", ") { it.text().trim() }.ifBlank { "Unknown author" }

        class Item(val href: String, val type: String, val props: String)
        val manifest = all.filter { local(it) == "item" }.associate {
            it.attr("id") to Item(resolve(opfDir, it.attr("href")), it.attr("media-type"), it.attr("properties"))
        }

        val coverItem = manifest.values.firstOrNull { "cover-image" in it.props.split(' ') }
            ?: all.firstOrNull { local(it) == "meta" && it.attr("name") == "cover" }?.let { manifest[it.attr("content")] }
            ?: manifest.entries.firstOrNull { (id, item) ->
                item.type.startsWith("image/") && ("cover" in id.lowercase() || "cover" in item.href.lowercase())
            }?.value
        val coverBytes = coverItem?.takeIf { it.type.startsWith("image/") }?.let { read(it.href) }
        val coverExt = coverItem?.href?.substringAfterLast('.', "jpg")?.lowercase() ?: "jpg"

        val spine = all.filter { local(it) == "itemref" }.mapNotNull { manifest[it.attr("idref")] }
            .filter { it.type.contains("html") || it.href.endsWith("html") || it.href.endsWith("htm") }

        val paragraphs = ArrayList<Para>()
        for (item in spine) {
            val html = readText(item.href) ?: continue
            val body = Jsoup.parse(html).body()
            HtmlText.extract(body) { heading, text -> paragraphs += Para(heading, text, "") }
        }
        if (paragraphs.isEmpty()) throw EpubException("No readable text found in this EPUB")
        return ParsedEpub(title, author, coverBytes, coverExt, Posts.mergeHeadings(paragraphs).first)
    }

    private fun local(el: Element) = el.tagName().substringAfter(':').lowercase()

    private fun resolve(base: String, href: String): String {
        val decoded = URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8")
        val parts = ArrayList<String>()
        (if (base.isEmpty()) decoded else "$base/$decoded").split('/').forEach {
            when (it) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += it
            }
        }
        return parts.joinToString("/")
    }
}
