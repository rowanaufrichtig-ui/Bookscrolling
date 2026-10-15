package com.bookscrolling.app.data

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val source: String,
    val postCount: Int,
    val position: Int,
    val addedAt: Long,
) {
    val progress: Float get() = if (postCount <= 1) 0f else position.toFloat() / (postCount - 1)

    companion object {
        const val SOURCE_GUTENBERG = "gutenberg"
        const val SOURCE_LOCAL = "local"
    }
}

data class Comment(val id: Long, val post: Int, val body: String, val createdAt: Long)
