package com.yunx.app.shell

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 远程配置读取：
 * - 版本 / 强制更新 / 禁用开关：当前源码仓库根目录的 update.json
 * - 多份公告：旧的 nieve-update 仓库根目录的 announcements.json
 *
 * 优先走 GitHub Contents API（提交后立即可读，不受 raw CDN 缓存影响），
 * API 失败时回退 raw 文件。读取失败一律回退为“正常可用”。
 */
object NieveRemote {
    const val UPDATE_URL =
        "https://raw.githubusercontent.com/yingxingw/Nieve/main/update.json"
    const val ANNOUNCEMENT_URL =
        "https://raw.githubusercontent.com/yingxingw/nieve-update/main/announcements.json"
    private const val UPDATE_API_URL =
        "https://api.github.com/repos/yingxingw/Nieve/contents/update.json"
    private const val ANNOUNCEMENT_API_URL =
        "https://api.github.com/repos/yingxingw/nieve-update/contents/announcements.json"
    const val FALLBACK_WEB = "https://github.com/yingxingw/Nieve"

    data class Config(
        val versionCode: Int,
        val versionName: String,
        val minVersionCode: Int,
        val downloadUrl: String,
        val changelog: String,
        val updateDate: String,
        val forceUpdate: Boolean,
        val disabled: Boolean,
        val disabledMessage: String,
    )

    data class Announcement(
        val id: String,
        val title: String,
        val content: String,
        val date: String,
    )

    data class AnnouncementFeed(
        val items: List<Announcement>,
        val error: String?,
    )

    sealed interface ConfigResult {
        data class Ok(val config: Config) : ConfigResult
        data class Failed(val message: String) : ConfigResult
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private fun fetchText(url: String): Result<String> = runCatching {
        // raw.githubusercontent.com 有 CDN 缓存；每次追加时间戳，保证远程开关能尽快生效。
        val cacheBusted = url + (if (url.contains('?')) "&" else "?") + "t=" + System.currentTimeMillis()
        val request = Request.Builder()
            .url(cacheBusted)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "Nieve")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            response.body?.string().orEmpty().takeIf { it.isNotBlank() }
                ?: error("响应为空")
        }
    }

    /** GitHub Contents API 会把文件内容放进 base64 的 content 字段。 */
    private fun decodeApiContent(body: String): String {
        val json = JSONObject(body)
        if (!json.has("content")) error("不是文件内容")
        if (json.optString("encoding") == "base64") {
            val encoded = json.optString("content").replace("\n", "")
            return String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        }
        return json.optString("content")
    }

    private fun fetchApiOrRaw(apiUrl: String, rawUrl: String): Result<String> {
        val api = fetchText(apiUrl).mapCatching { decodeApiContent(it) }
        if (api.isSuccess) return api
        return fetchText(rawUrl)
    }

    suspend fun fetchConfig(): ConfigResult = withContext(Dispatchers.IO) {
        val body = fetchApiOrRaw(UPDATE_API_URL, UPDATE_URL).getOrElse {
            return@withContext ConfigResult.Failed(it.message ?: "无法连接更新通道")
        }
        try {
            val json = JSONObject(body)
            val url = json.optString("downloadUrl").ifBlank { FALLBACK_WEB }
            ConfigResult.Ok(
                Config(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName", ""),
                    minVersionCode = json.optInt("minVersionCode", 0),
                    downloadUrl = url,
                    changelog = json.optString("changelog", ""),
                    updateDate = json.optString("updateDate", ""),
                    forceUpdate = json.optBoolean("forceUpdate", false),
                    disabled = json.optBoolean("disabled", false),
                    disabledMessage = json.optString(
                        "disabledMessage",
                        "Nieve 暂时停止使用，请等待后续版本。"
                    ),
                )
            )
        } catch (e: Exception) {
            ConfigResult.Failed("更新配置格式错误：${e.message ?: "unknown"}")
        }
    }

    suspend fun fetchAnnouncements(): AnnouncementFeed = withContext(Dispatchers.IO) {
        val body = fetchApiOrRaw(ANNOUNCEMENT_API_URL, ANNOUNCEMENT_URL).getOrElse {
            return@withContext AnnouncementFeed(emptyList(), it.message ?: "无法连接公告通道")
        }
        runCatching {
            val array = runCatching {
                JSONArray(body)
            }.getOrElse {
                JSONObject(body).optJSONArray("items") ?: JSONArray()
            }
            val items = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    val content = item.optString("content").trim()
                    if (title.isBlank() && content.isBlank()) continue
                    add(
                        Announcement(
                            id = item.optString("id").ifBlank { "announcement-$i" },
                            title = title.ifBlank { "公告" },
                            content = content,
                            date = item.optString("date"),
                        )
                    )
                }
            }
            AnnouncementFeed(items, null)
        }.getOrElse {
            AnnouncementFeed(emptyList(), "公告格式错误：${it.message ?: "unknown"}")
        }
    }
}
