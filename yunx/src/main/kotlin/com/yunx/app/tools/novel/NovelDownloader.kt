package com.yunx.app.tools.novel

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

class NovelDownloader(private val context: Context, private val api: QimaoApi) {

    suspend fun download(
        book: QmBook,
        chapters: List<QmChapter>,
        isCancelled: () -> Boolean,
        onProgress: suspend (done: Int, total: Int, chapter: String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val fileName = qmSafeFileName(book.title) + ".txt"
        val output = openOutput(fileName)
        output.stream.use { stream ->
            write(stream, "${book.title}\n")
            if (book.author.isNotBlank()) write(stream, "作者：${book.author}\n")
            write(stream, "\n")
            var done = 0
            for (chapter in chapters) {
                if (isCancelled()) break
                val content = runCatching { api.chapterContent(book.id, chapter.id) }.getOrElse {
                    "（本章抓取失败：${it.message ?: "未知错误"}）"
                }
                write(stream, chapter.title + "\n\n")
                write(stream, content + "\n\n")
                done++
                onProgress(done, chapters.size, chapter.title)
            }
        }
        output.finish()
        output.displayPath
    }

    private data class OutputHandle(
        val stream: OutputStream,
        val displayPath: String,
        val finish: () -> Unit,
    )

    private fun openOutput(fileName: String): OutputHandle {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Nieve/小说")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream != null) {
                    return OutputHandle(stream, "Download/Nieve/小说/$fileName") {
                        val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                        context.contentResolver.update(uri, done, null, null)
                    }
                }
            }
        }
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Nieve/小说"
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        return OutputHandle(file.outputStream(), file.absolutePath) {}
    }

    private fun write(stream: OutputStream, text: String) {
        stream.write(text.toByteArray(Charsets.UTF_8))
    }
}
