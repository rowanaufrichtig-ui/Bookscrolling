package com.bookscrolling.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SepTest {
    @Test
    fun parsesContentsSkippingCrossReferences() {
        val html = """<ul>
            <li> <a href="entries/abduction/"><strong>abduction</strong></a> (Igor Douven) </li>
            <li> accidental properties &mdash; see <a href="entries/essential-accidental/">essential vs. accidental properties</a> </li>
            <li> aesthetics
               <ul><li> <a href="entries/aesthetics-19th-romantic/"><strong>19th Century Romantic</strong></a> (Keren Gorodeisky) </li></ul></li>
            <li> <a href="entries/mereology/"><strong>mereology</strong></a> (Achille Varzi)
               <ul><li> <a href="entries/mereology-medieval/"><strong>medieval</strong></a> (Andrew Arlig) </li></ul></li>
            <li> <a href="entries/abduction/"><strong>abduction</strong></a> (Igor Douven) </li>
            </ul>"""
        assertEquals(
            listOf(
                SepEntry("abduction", "abduction", "Igor Douven"),
                SepEntry("aesthetics-19th-romantic", "aesthetics: 19th Century Romantic", "Keren Gorodeisky"),
                SepEntry("mereology", "mereology", "Achille Varzi"),
                SepEntry("mereology-medieval", "mereology: medieval", "Andrew Arlig"),
            ),
            Sep.parseContents(html),
        )
    }

    @Test
    fun parsesArticle() {
        val html = """<div id="aueditable"><h1>Abduction</h1><div id="pubinfo"><em>First published 2011</em></div>
            <div id="preamble"><p>In the philosophical literature, the term abduction is used in two senses.</p></div>
            <div id="toc"><ul><li>1. Idea</li></ul></div>
            <div id="main-text"><h2><a name="x">1. The General Idea</a></h2><p>You happen to know that Tim and Harry had a row.</p></div>
            <div id="bibliography"><p>Some book.</p></div></div>
            <div id="article-copyright"><p><a href="../../info.html#c">Copyright &copy; 2025</a> by <br />
            Igor Douven &lt;<a href="m&#97;ilto:x"><em>igor</em></a>&gt;</p></div>"""
        val a = Sep.parseArticle(html)
        assertEquals("Abduction", a.title)
        assertEquals("Igor Douven", a.author)
        assertEquals(
            listOf(
                Para(false, "First published 2011", ""),
                Para(false, "In the philosophical literature, the term abduction is used in two senses.", ""),
                Para(true, "1. The General Idea", "1. The General Idea"),
                Para(false, "You happen to know that Tim and Harry had a row.", "1. The General Idea"),
            ),
            a.paragraphs,
        )
    }

    @Test
    fun cleansMath() {
        assertEquals("If □ p then p → q, and φ ∧ ψ", Sep.cleanMath("If \\(\\Box p\\) then \\(p \\to q\\), and \\(\\phi \\wedge \\psi\\)"))
        assertEquals("Plain text", Sep.cleanMath("Plain text"))
    }

    @Test
    fun live() {
        if (System.getenv("LIVE") == null) return
        val entries = Sep.parseContents(Sep.fetchContentsHtml())
        println("entries: ${entries.size} e.g. ${entries.take(3)}")
        assertTrue(entries.size > 1500)
        for (slug in listOf("abduction", "plato", "consciousness", "logic-modal")) {
            val a = Sep.fetchArticle(slug)
            println("$slug: ${a.title} / ${a.author} / ${a.paragraphs.size} posts")
            a.paragraphs.take(6).forEach { println((if (it.heading) "# " else "  ") + it.text.take(90).replace("\n", " / ")) }
        }
    }
}
