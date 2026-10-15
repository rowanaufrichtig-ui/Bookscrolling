package com.bookscrolling.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(context: Context) : SQLiteOpenHelper(context, "bookscrolling.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE books(
                id TEXT PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, cover TEXT,
                source TEXT NOT NULL, post_count INTEGER NOT NULL, position INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE TABLE likes(book_id TEXT NOT NULL, post INTEGER NOT NULL, PRIMARY KEY(book_id, post))")
        db.execSQL(
            """CREATE TABLE comments(
                id INTEGER PRIMARY KEY AUTOINCREMENT, book_id TEXT NOT NULL, post INTEGER NOT NULL,
                body TEXT NOT NULL, created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX comments_post ON comments(book_id, post)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun books(): List<Book> = readableDatabase.rawQuery(
        "SELECT id, title, author, cover, source, post_count, position, added_at FROM books ORDER BY added_at DESC", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Book(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5), c.getInt(6), c.getLong(7))
            )
        }
    }

    fun book(id: String): Book? = books().firstOrNull { it.id == id }

    fun upsertBook(book: Book) {
        writableDatabase.insertWithOnConflict("books", null, ContentValues().apply {
            put("id", book.id); put("title", book.title); put("author", book.author); put("cover", book.coverPath)
            put("source", book.source); put("post_count", book.postCount); put("position", book.position)
            put("added_at", book.addedAt)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun setPosition(id: String, position: Int) {
        writableDatabase.execSQL("UPDATE books SET position = ? WHERE id = ?", arrayOf<Any>(position, id))
    }

    fun deleteBook(id: String) {
        writableDatabase.apply {
            delete("books", "id = ?", arrayOf(id))
            delete("likes", "book_id = ?", arrayOf(id))
            delete("comments", "book_id = ?", arrayOf(id))
        }
    }

    fun likes(bookId: String): Set<Int> =
        readableDatabase.rawQuery("SELECT post FROM likes WHERE book_id = ?", arrayOf(bookId)).use { c ->
            buildSet { while (c.moveToNext()) add(c.getInt(0)) }
        }

    fun setLiked(bookId: String, post: Int, liked: Boolean) {
        if (liked) writableDatabase.execSQL("INSERT OR IGNORE INTO likes(book_id, post) VALUES(?, ?)", arrayOf<Any>(bookId, post))
        else writableDatabase.delete("likes", "book_id = ? AND post = ?", arrayOf(bookId, post.toString()))
    }

    fun commentCounts(bookId: String): Map<Int, Int> =
        readableDatabase.rawQuery("SELECT post, COUNT(*) FROM comments WHERE book_id = ? GROUP BY post", arrayOf(bookId)).use { c ->
            buildMap { while (c.moveToNext()) put(c.getInt(0), c.getInt(1)) }
        }

    fun comments(bookId: String, post: Int): List<Comment> = readableDatabase.rawQuery(
        "SELECT id, post, body, created_at FROM comments WHERE book_id = ? AND post = ? ORDER BY created_at",
        arrayOf(bookId, post.toString())
    ).use { c ->
        buildList { while (c.moveToNext()) add(Comment(c.getLong(0), c.getInt(1), c.getString(2), c.getLong(3))) }
    }

    fun addComment(bookId: String, post: Int, body: String): Comment {
        val now = System.currentTimeMillis()
        val id = writableDatabase.insert("comments", null, ContentValues().apply {
            put("book_id", bookId); put("post", post); put("body", body); put("created_at", now)
        })
        return Comment(id, post, body, now)
    }

    fun deleteComment(id: Long) {
        writableDatabase.delete("comments", "id = ?", arrayOf(id.toString()))
    }
}
