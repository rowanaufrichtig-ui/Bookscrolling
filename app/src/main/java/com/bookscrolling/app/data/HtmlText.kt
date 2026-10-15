package com.bookscrolling.app.data

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Splits an HTML subtree into posts: one per paragraph-like block, headings flagged. */
object HtmlText {
    private val leafBlocks = setOf(
        "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre", "dt", "dd",
        "figcaption", "caption",
    )
    private val containers = setOf(
        "div", "section", "article", "blockquote", "ul", "ol", "dl", "figure", "header", "footer", "main", "aside", "nav", "body",
    )
    private val skipped = setOf("script", "style", "img", "svg", "image", "hr", "br", "head")
    private const val BREAK = '\u2028'

    fun extract(root: Element, emit: (heading: Boolean, text: String) -> Unit) {
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

    fun textOf(el: Element): String? {
        val copy = el.clone()
        copy.select("br").forEach { it.replaceWith(TextNode(BREAK.toString())) }
        val text = copy.text()
            .split(BREAK)
            .joinToString("\n") { it.trim() }
            .trim('\n', ' ')
            .replace(Regex("\n{3,}"), "\n\n")
        return text.takeIf { it.any(Char::isLetterOrDigit) }
    }

}
