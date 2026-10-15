package com.bookscrolling.app.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun getText(url: String): String = open(url).let { conn ->
        try {
            conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, dest: File, onProgress: (Float) -> Unit = {}) {
        val conn = open(url)
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Bookscrolling/1.0 (Android)")
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Bad redirect")
                current = URL(URL(current), location).toString()
                conn.disconnect()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("Server returned $code")
            }
            return conn
        }
        throw IOException("Too many redirects")
    }
}
