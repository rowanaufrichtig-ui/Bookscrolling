package com.bookscrolling.app.data

/** Post-processing shared by every source (EPUB, SEP). */
object Posts {
    /**
     * Merges each run of consecutive headings ("BOOK ONE" + "CHAPTER I", or a title split over
     * several tags) into a single heading post, and labels the following paragraphs with it.
     *
     * Returns the new list and, for each input index, the index of the post it ended up in.
     */
    fun mergeHeadings(paras: List<Para>): Pair<List<Para>, IntArray> {
        val out = ArrayList<Para>(paras.size)
        val map = IntArray(paras.size)
        var chapter = ""
        var i = 0
        while (i < paras.size) {
            if (!paras[i].heading) {
                map[i] = out.size
                out += paras[i].copy(chapter = chapter)
                i++
                continue
            }
            val start = i
            val lines = ArrayList<String>()
            while (i < paras.size && paras[i].heading) {
                lines += paras[i].text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                map[i] = out.size
                i++
            }
            chapter = lines.take(2).joinToString(" · ").take(80)
            out += Para(heading = true, text = lines.joinToString("\n"), chapter = chapter)
            check(i > start)
        }
        return out to map
    }

    /** Converts a post index (0 = cover, n = paragraph n - 1) through a paragraph index map. */
    fun remapPost(post: Int, map: IntArray): Int =
        if (post <= 0) post else map.getOrNull(post - 1)?.plus(1) ?: (map.lastOrNull()?.plus(1) ?: post)
}
