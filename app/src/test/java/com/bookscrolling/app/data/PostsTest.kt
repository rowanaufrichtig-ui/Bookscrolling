package com.bookscrolling.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PostsTest {
    @Test
    fun mergesConsecutiveHeadingsIntoOnePost() {
        val input = listOf(
            Para(false, "Preface text", ""),
            Para(true, "BOOK ONE: 1805", ""),
            Para(true, "CHAPTER I\n\nTHE SALON", ""),
            Para(false, "Well, Prince…", ""),
            Para(true, "CHAPTER II", ""),
            Para(false, "Anna Pávlovna’s drawing room…", ""),
        )
        val (out, map) = Posts.mergeHeadings(input)
        assertEquals(
            listOf(
                Para(false, "Preface text", ""),
                Para(true, "BOOK ONE: 1805\nCHAPTER I\nTHE SALON", "BOOK ONE: 1805 · CHAPTER I"),
                Para(false, "Well, Prince…", "BOOK ONE: 1805 · CHAPTER I"),
                Para(true, "CHAPTER II", "CHAPTER II"),
                Para(false, "Anna Pávlovna’s drawing room…", "CHAPTER II"),
            ),
            out,
        )
        assertArrayEquals(intArrayOf(0, 1, 1, 2, 3, 4), map)
        // Posts are 1-based behind the cover (post 0).
        assertEquals(0, Posts.remapPost(0, map))
        assertEquals(3, Posts.remapPost(4, map))
        assertEquals(2, Posts.remapPost(3, map))
        assertEquals(5, Posts.remapPost(6, map))
    }

    @Test
    fun mergeIsIdempotent() {
        val once = Posts.mergeHeadings(
            listOf(Para(true, "A", ""), Para(true, "B", ""), Para(false, "text", "")),
        ).first
        assertEquals(once, Posts.mergeHeadings(once).first)
    }
}
