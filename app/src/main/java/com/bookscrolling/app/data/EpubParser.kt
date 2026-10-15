package com.bookscrolling.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
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

    private val leafBlocks = setOf(
        "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre", "dt", "dd",
        "figcaption", "caption",
    )
    private val containers = setOf(
        "div", "section", "article", "blockquote", "ul", "ol", "dl", "figure", "header", "footer", "main", "aside", "nav", "body",
    )
    private val skipped = setOf("script", "style", "img", "svg", "image", "hr", "br", "head")
    private const val BREAK = ' '

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
        var chapter = ""
        for (item in spine) {
            val html = readText(item.href) ?: continue
            val body = Jsoup.parse(html).body()
            extract(body) { heading, text ->
                if (heading) chapter = text.lineSequence().first().take(60)
                paragraphs += Para(heading, text, chapter)
            }
        }
        if (paragraphs.isEmpty()) throw EpubException("No readable text found in this EPUB")
        return ParsedEpub(title, author, coverBytes, coverExt, paragraphs)
    }

    private fun extract(root: Element, emit: (Boolean, String) -> Unit) {
        val hasStructure = root.children().any { hasBlock(it) || it.normalName() in leafBlocks }
        if (!hasStructure) {
            textOf(root)?.let { emit(false, it) }
            return
        }
        for (child in root.children()) {
            val name = child.normalName()
            when {
                name in skipped -> Unit
                // A table (often a table of contents) becomes one post: one line per row.
                name == "table" -> tableText(child)?.let { emit(false, it) }
                name in leafBlocks && !hasBlock(child) -> textOf(child)?.let { emit(isHeading(name), it) }
                name in containers || hasBlock(child) -> extract(child, emit)
                else -> textOf(child)?.let { emit(false, it) }
            }
        }
    }

    private fun tableText(table: Element): String? = table.select("tr")
        .mapNotNull { row -> row.select("td, th").mapNotNull(::textOf).joinToString("  ·  ").ifBlank { null } }
        .joinToString("\n")
        .ifBlank { null }

    private fun hasBlock(el: Element): Boolean =
        el.children().any { it.normalName().let { n -> n in leafBlocks || n in containers || n == "table" } || hasBlock(it) }

    private fun isHeading(name: String) = name.length == 2 && name[0] == 'h' && name[1].isDigit()

    private fun textOf(el: Element): String? {
        val copy = el.clone()
        copy.select("br").forEach { it.replaceWith(TextNode(BREAK.toString())) }
        val text = copy.text()
            .split(BREAK)
            .joinToString("\n") { it.trim() }
            .trim('\n', ' ')
            .replace(Regex("\n{3,}"), "\n\n")
        return text.takeIf { it.any(Char::isLetterOrDigit) }
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
