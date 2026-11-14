package com.yunx.app.tools.novel

import android.content.Context
import android.util.Base64
import com.yunx.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class QmBook(
    val id: String,
    val title: String,
    val cover: String = "",
    val subtitle: String = "",
    val author: String = "",
    val intro: String = "",
    val score: String = "",
)

data class QmCategory(
    val title: String,
    val categoryId: String,
    val categoryType: String,
)

data class QmChapter(
    val id: String,
    val title: String,
    val index: Int,
    val words: Int,
)

class QimaoException(message: String) : Exception(message)

class QimaoApi(private val context: Context) {
    private val client = OkHttpClient.Builder().build()
    private val secret = com.yunx.app.BuildConfig.QIMAO_SECRET
    private val qmParams: String by lazy {
        context.resources.openRawResource(R.raw.qm_params)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
    }

    suspend fun leaderBoard(tabType: Int, categoryId: String, categoryType: String): Pair<List<QmCategory>, List<QmBook>> =
        withContext(Dispatchers.IO) {
            val params = linkedMapOf(
                "rank_type" to "rec_list",
                "category_id" to categoryId,
                "tab_type" to tabType.toString(),
                "category_type" to categoryType,
                "book_privacy" to "1",
            )
            val json = request("https://api-bc.wtzw.com/api/v1/leader-board", params)
            val data = json.optJSONObject("data") ?: throw QimaoException("榜单数据为空")
            val categories = mutableListOf<QmCategory>()
            data.optJSONArray("category_list")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val title = item.optString("title")
                    if (title.isBlank()) continue
                    categories.add(
                        QmCategory(
                            title = title,
                            categoryId = item.optString("category_id"),
                            categoryType = item.optString("category_type"),
                        )
                    )
                }
            }
            categories to parseBooks(data.optJSONArray("books"))
        }

    suspend fun search(keyword: String, page: Int = 1): List<QmBook> = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "extend" to "",
            "tab" to "0",
            "page" to page.toString(),
            "wd" to keyword,
        )
        val json = request("https://api-bc.wtzw.com/search/v1/words", params)
        val data = json.optJSONObject("data") ?: throw QimaoException("搜索无结果")
        parseBooks(data.optJSONArray("books"))
    }

    suspend fun chapters(bookId: String): List<QmChapter> = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "chapter_ver" to "0",
            "id" to bookId,
            "reader_type" to "0",
        )
        val json = request("https://api-ks.wtzw.com/api/v1/chapter/chapter-list", params)
        val data = json.optJSONObject("data") ?: throw QimaoException("章节列表为空")
        val arr = data.optJSONArray("chapter_lists") ?: throw QimaoException("章节列表为空")
        val result = mutableListOf<QmChapter>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            result.add(
                QmChapter(
                    id = id,
                    title = item.optString("title").ifBlank { "第 ${i + 1} 章" },
                    index = item.optInt("index", i + 1),
                    words = item.optInt("words"),
                )
            )
        }
        result
    }

    suspend fun chapterContent(bookId: String, chapterId: String): String = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "chapterIds" to chapterId,
            "id" to bookId,
        )
        val json = request("https://api-ks.wtzw.com/api/v1/chapter/preload-chapter-content", params)
        val data = json.optJSONObject("data") ?: throw QimaoException("正文为空")
        val arr = data.optJSONArray("chapter_contents") ?: throw QimaoException("正文为空")
        if (arr.length() == 0) throw QimaoException("正文为空")
        val item = arr.optJSONObject(0) ?: throw QimaoException("正文为空")
        val encrypted = item.optString("content")
        if (encrypted.isBlank()) throw QimaoException("正文为空")
        runCatching { decryptContent(encrypted) }.getOrElse {
            if (encrypted.any { ch -> ch.code > 127 }) encrypted else throw QimaoException("正文解密失败")
        }
    }

    private fun parseBooks(arr: org.json.JSONArray?): List<QmBook> {
        if (arr == null) return emptyList()
        val result = mutableListOf<QmBook>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            result.add(
                QmBook(
                    id = id,
                    title = item.optString("title"),
                    cover = item.optString("image_link"),
                    subtitle = item.optString("sub_title"),
                    author = item.optString("author"),
                    intro = item.optString("intro"),
                    score = item.optString("score"),
                )
            )
        }
        return result
    }

    private fun request(baseUrl: String, params: Map<String, String>): JSONObject {
        val signed = params.toMutableMap()
        signed["sign"] = sign(signed)
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
        signed.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
        val headers = linkedMapOf(
            "app-version" to "80400",
            "platform" to "android",
            "reg" to "",
            "application-id" to "com.kmxs.reader",
            "net-env" to "1",
            "channel" to "qm-oppo_lf",
            "qm-params" to qmParams,
        )
        headers["sign"] = sign(headers)
        val request = Request.Builder()
            .url(urlBuilder.build())
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401) throw QimaoException("七猫接口鉴权失败")
            if (response.code != 200) throw QimaoException("请求失败：${response.code}")
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw QimaoException("返回数据格式错误")
            if (json.optInt("code", 0) != 0 && json.has("code")) {
                val msg = json.optString("message").ifBlank { "接口返回错误码 ${json.optInt("code")}" }
                throw QimaoException(msg)
            }
            return json
        }
    }

    private fun sign(map: Map<String, String>): String {
        if (secret.isBlank()) throw QimaoException("缺少本地七猫签名配置")
        val raw = map.entries.sortedBy { it.key }.joinToString("") { "${it.key}=${it.value}" } + secret
        val digest = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decryptContent(base64: String): String {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        if (bytes.size < 16 || bytes.size % 16 != 0) throw QimaoException("密文长度异常")
        val key = com.yunx.app.BuildConfig.QIMAO_AES_KEY.toByteArray(Charsets.US_ASCII)
        if (key.size != 16) throw QimaoException("缺少本地内容解密配置")
        val zeroIv = ByteArray(16)
        val first = cbcDecrypt(bytes, key, zeroIv)
        val iv = first.copyOf(16)
        val second = cbcDecrypt(bytes, key, iv)
        val text = String(second, 16, second.size - 16, Charsets.UTF_8)
        return normalizeText(text)
    }

    private fun cbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }
}

fun normalizeText(text: String): String {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    return lines.joinToString("\n") { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) "" else "　　$trimmed"
    }.trim()
}

fun qmSafeFileName(title: String): String {
    val invalid = Regex("[\\\\/:*?\"<>|\\n\\r]")
    return invalid.replace(title, "_").trim().ifBlank { "小说" }
}
