package com.bookscrolling.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class SepEntry(val slug: String, val title: String, val author: String)

class SepArticle(val title: String, val author: String, val paragraphs: List<Para>)

/** Stanford Encyclopedia of Philosophy: table of contents and article text. */
object Sep {
    const val BASE = "https://plato.stanford.edu"
    private val slugPattern = Regex("^(?:\\.\\./)*entries/([^/]+)/?$")

    fun articleUrl(slug: String) = "$BASE/entries/$slug/"

    fun fetchContentsHtml(): String = Http.getText("$BASE/contents.html")

    /** Main entries only (links in bold); "see X" cross-references are skipped. */
    fun parseContents(html: String): List<SepEntry> {
        val doc = Jsoup.parse(html, BASE)
        val seen = HashSet<String>()
        return doc.select("li > a[href]:has(strong)").mapNotNull { a ->
            val slug = slugPattern.find(a.attr("href").trim())?.groupValues?.get(1) ?: return@mapNotNull null
            if (!seen.add(slug)) return@mapNotNull null
            val li = a.parent() ?: return@mapNotNull null
            val author = Regex("\\(([^)]*)\\)").find(li.ownText())?.groupValues?.get(1)?.trim().orEmpty()
            // Sub-entries ("aesthetics" > "19th Century Romantic") get their parent topic as a prefix.
            val parentTopic = li.parent()?.parent()?.takeIf { it.normalName() == "li" }?.let { parent ->
                parent.selectFirst("> a > strong")?.text()
                    ?: parent.ownText().replace(Regex("\\([^)]*\\)"), "").substringBefore("—")
            }?.trim()?.takeIf { it.isNotEmpty() }
            val name = a.text().trim()
            SepEntry(slug, if (parentTopic != null) "$parentTopic: $name" else name, author)
        }.sortedBy { it.title.lowercase() }
    }

    fun fetchArticle(slug: String): SepArticle = parseArticle(Http.getText(articleUrl(slug)))

    fun parseArticle(html: String): SepArticle {
        val doc = Jsoup.parse(html, BASE)
        val title = doc.selectFirst("#aueditable h1, #article h1, h1")?.text()?.trim().orEmpty().ifBlank { "Untitled" }
        val author = authorOf(doc.selectFirst("#article-copyright")) ?: "Stanford Encyclopedia of Philosophy"
        val paras = ArrayList<Para>()
        val pubinfo = doc.selectFirst("#pubinfo")?.text()?.trim()
        if (!pubinfo.isNullOrBlank()) paras += Para(false, pubinfo, "")
        for (section in listOfNotNull(doc.selectFirst("#preamble"), doc.selectFirst("#main-text"))) {
            HtmlText.extract(section) { heading, text -> paras += Para(heading, cleanMath(text), "") }
        }
        if (paras.none { !it.heading && it.text.length > 40 }) throw EpubException("Couldn't read this article")
        return SepArticle(title, author, Posts.mergeHeadings(paras).first)
    }

    private val latex = mapOf(
        "Box" to "□", "Diamond" to "◇", "neg" to "¬", "lnot" to "¬", "wedge" to "∧", "land" to "∧",
        "vee" to "∨", "lor" to "∨", "rightarrow" to "→", "to" to "→", "Rightarrow" to "⇒", "leftarrow" to "←",
        "leftrightarrow" to "↔", "Leftrightarrow" to "⇔", "supset" to "⊃", "subset" to "⊂", "subseteq" to "⊆",
        "supseteq" to "⊇", "equiv" to "≡", "forall" to "∀", "exists" to "∃", "vdash" to "⊢", "models" to "⊨",
        "vDash" to "⊨", "in" to "∈", "notin" to "∉", "cup" to "∪", "cap" to "∩", "emptyset" to "∅",
        "varnothing" to "∅", "ne" to "≠", "neq" to "≠", "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
        "times" to "×", "cdot" to "·", "infty" to "∞", "bot" to "⊥", "top" to "⊤", "approx" to "≈",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "Gamma" to "Γ", "delta" to "δ", "Delta" to "Δ",
        "epsilon" to "ε", "lambda" to "λ", "mu" to "μ", "pi" to "π", "sigma" to "σ", "Sigma" to "Σ",
        "phi" to "φ", "varphi" to "φ", "Phi" to "Φ", "psi" to "ψ", "Psi" to "Ψ", "chi" to "χ",
        "omega" to "ω", "Omega" to "Ω", "theta" to "θ", "tau" to "τ", "rho" to "ρ", "langle" to "⟨",
        "rangle" to "⟩", "ldots" to "…", "dots" to "…", "quad" to " ", "qquad" to "  ",
    )
    private val latexCommand = Regex("\\\\([A-Za-z]+)")
    private val latexText = Regex("\\\\(?:text|textrm|textit|mathrm|mathit|mathbf|mathsf|mathcal|textbf)\\{([^{}]*)\\}")

    /** SEP uses MathJax (\\( ... \\)); turn common symbols into Unicode so they read as text. */
    fun cleanMath(text: String): String {
        if (!text.contains('\\')) return text
        return Regex("\\\\\\((.*?)\\\\\\)|\\\\\\[(.*?)\\\\\\]", RegexOption.DOT_MATCHES_ALL).replace(text) { m ->
            var t = m.groupValues[1].ifEmpty { m.groupValues[2] }
            t = latexText.replace(t) { it.groupValues[1] }
            t = latexCommand.replace(t) { c -> latex[c.groupValues[1]] ?: c.value }
            t.replace("\\{", "{").replace("\\}", "}").replace("{", "").replace("}", "").trim()
        }
    }

    private fun authorOf(copyright: Element?): String? {
        val p = copyright?.clone() ?: return null
        p.select("a").forEach { a ->
            if (a.attr("href").contains("ilto:")) a.remove() else a.unwrap()
        }
        val text = p.text().substringAfter(" by ", "").replace(Regex("[<>]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return text.ifBlank { null }
    }
}
