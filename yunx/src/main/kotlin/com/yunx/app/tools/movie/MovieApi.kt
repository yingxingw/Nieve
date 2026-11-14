package com.yunx.app.tools.movie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class MovieSite(
    val name: String,
    val api: String,
)

val movieSites = listOf(
    MovieSite("非凡", "https://api.ffzyapi.com/api.php/provide/vod/"),
    MovieSite("极速", "https://jisuzy.com/api.php/provide/vod/"),
    MovieSite("无尽", "https://api.wujinapi.com/api.php/provide/vod/"),
    MovieSite("电影天堂", "https://caiji.dyttzyapi.com/api.php/provide/vod/"),
    MovieSite("红牛", "https://www.hongniuzy2.com/api.php/provide/vod/"),
    MovieSite("量子", "https://cj.lziapi.com/api.php/provide/vod/"),
    MovieSite("爱奇艺", "https://www.iqiyizyapi.com/api.php/provide/vod/"),
    MovieSite("如意", "https://ryzy1.tv/api.php/provide/vod/"),
)

data class Movie(
    val id: String,
    val name: String,
    val pic: String = "",
    val remarks: String = "",
    val year: String = "",
    val area: String = "",
    val type: String = "",
    val actor: String = "",
    val director: String = "",
    val content: String = "",
    val playFrom: String = "",
    val playUrl: String = "",
)

data class MovieEpisode(
    val name: String,
    val url: String,
)

data class MoviePlayLine(
    val name: String,
    val episodes: List<MovieEpisode>,
)

class MovieException(message: String) : Exception(message)

private data class CachedDetail(
    val at: Long,
    val movie: Movie,
    val lines: List<MoviePlayLine>,
)

private data class CachedPlayUrl(val at: Long, val url: String)

private data class CachedSearch(val at: Long, val movies: List<Movie>)

class MovieApi {
    private val client = sharedClient

    suspend fun search(site: MovieSite, keyword: String, page: Int = 1): List<Movie> = withContext(Dispatchers.IO) {
        val key = "${site.api}|$keyword|$page"
        searchCache[key]?.takeIf { System.currentTimeMillis() - it.at < SEARCH_TTL_MS }
            ?.let { return@withContext it.movies }

        val url = site.api.toHttpUrl().newBuilder()
            .addQueryParameter("ac", "detail")
            .addQueryParameter("wd", keyword)
            .addQueryParameter("pg", page.toString())
            .build()
        val json = request(url.toString())
        val list = json.optJSONArray("list")
        if (list == null) {
            val empty = emptyList<Movie>()
            searchCache[key] = CachedSearch(System.currentTimeMillis(), empty)
            return@withContext empty
        }
        val movies = (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("vod_id")
            if (id.isBlank()) return@mapNotNull null
            Movie(
                id = id,
                name = item.optString("vod_name"),
                pic = item.optString("vod_pic"),
                remarks = item.optString("vod_remarks"),
                year = item.optString("vod_year"),
                area = item.optString("vod_area"),
                type = item.optString("vod_class").ifBlank { item.optString("vod_type") },
                actor = item.optString("vod_actor"),
                director = item.optString("vod_director"),
                content = item.optString("vod_content"),
                playFrom = item.optString("vod_play_from"),
                playUrl = item.optString("vod_play_url"),
            )
        }
        searchCache[key] = CachedSearch(System.currentTimeMillis(), movies)
        movies
    }

    suspend fun detail(site: MovieSite, movie: Movie): Pair<Movie, List<MoviePlayLine>> = withContext(Dispatchers.IO) {
        val key = "${site.api}|${movie.id}"
        detailCache[key]?.takeIf { System.currentTimeMillis() - it.at < DETAIL_TTL_MS }
            ?.let { return@withContext it.movie to it.lines }

        val base = site.api.substringBefore("?").trimEnd('/') + "/"
        val url = base.toHttpUrl().newBuilder()
            .addQueryParameter("ac", "detail")
            .addQueryParameter("ids", movie.id)
            .build()
        val json = request(url.toString())
        val list = json.optJSONArray("list")
        val item = if (list != null && list.length() > 0) list.optJSONObject(0) else null
        val detailed = if (item == null) movie else movie.copy(
            name = item.optString("vod_name").ifBlank { movie.name },
            pic = item.optString("vod_pic").ifBlank { movie.pic },
            remarks = item.optString("vod_remarks").ifBlank { movie.remarks },
            year = item.optString("vod_year"),
            area = item.optString("vod_area"),
            type = item.optString("vod_class").ifBlank { item.optString("vod_type") },
            actor = item.optString("vod_actor"),
            director = item.optString("vod_director"),
            content = item.optString("vod_content"),
            playFrom = item.optString("vod_play_from").ifBlank { movie.playFrom },
            playUrl = item.optString("vod_play_url").ifBlank { movie.playUrl },
        )
        val lines = parsePlayLines(detailed.playFrom, detailed.playUrl)
        detailCache[key] = CachedDetail(System.currentTimeMillis(), detailed, lines)
        detailed to lines
    }

    fun playLines(movie: Movie): List<MoviePlayLine> =
        parsePlayLines(movie.playFrom, movie.playUrl)

    private fun parsePlayLines(from: String, playUrl: String): List<MoviePlayLine> {
        if (playUrl.isBlank()) return emptyList()
        val fromNames = from.split("$$$").map { it.trim() }
        val urlGroups = playUrl.split("$$$")
        return urlGroups.mapIndexedNotNull { groupIndex, group ->
            val episodes = group.split("#").mapNotNull { raw ->
                val entry = raw.trim()
                if (entry.isBlank()) return@mapNotNull null
                val separator = entry.indexOf('$')
                if (separator <= 0) return@mapNotNull null
                val name = entry.substring(0, separator).trim()
                val url = entry.substring(separator + 1).trim()
                if (url.isBlank()) null else MovieEpisode(name.ifBlank { "正片" }, url)
            }
            if (episodes.isEmpty()) null
            else MoviePlayLine(fromNames.getOrElse(groupIndex) { "线路${groupIndex + 1}" }, episodes)
        }
    }

    private fun request(url: String): JSONObject {
        var lastError: Exception? = null
        for (attempt in 1..2) {
            try {
                return requestOnce(url)
            } catch (e: IOException) {
                lastError = e
                val timeout = e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException
                if (timeout || attempt == 2) break
            } catch (e: MovieException) {
                lastError = e
                val retryable = e.message?.contains("请求失败：5") == true
                if (!retryable || attempt == 2) throw e
            }
        }
        throw MovieException(lastError?.message ?: "网络连接失败，请重试")
    }

    private fun requestOnce(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept", "application/json,text/plain,*/*")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code != 200) throw MovieException("请求失败：${response.code}")
            return runCatching { JSONObject(body) }.getOrNull()
                ?: throw MovieException("接口返回不是 JSON，可能该线路已失效")
        }
    }

    suspend fun resolvePlayUrl(url: String): String = withContext(Dispatchers.IO) {
        var target = url.trim()
        if (target.startsWith("//")) target = "https:$target"
        if (target.contains(".m3u8")) return@withContext target

        playCache[target]?.takeIf { System.currentTimeMillis() - it.at < PLAY_TTL_MS }
            ?.let { return@withContext it.url }

        if (target.startsWith("http") && (target.contains("/share/") || target.endsWith(".html"))) {
            runCatching {
                val request = Request.Builder()
                    .url(target)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val html = response.body?.string().orEmpty()
                    val base = response.request.url
                    val direct = parseSharePage(html, base.toString())
                    if (direct.isNullOrBlank()) target else {
                        playCache[target] = CachedPlayUrl(System.currentTimeMillis(), direct)
                        direct
                    }
                }
            }.getOrDefault(target)
        } else target
    }

    private fun parseSharePage(html: String, pageUrl: String): String? {
        val patterns = listOf(
            Regex("""const\s+url\s*=\s*["'](.*?)["']"""),
            Regex(""""url"\s*:\s*"((?:https?:)?\\?/\\?/[^"]+\.m3u8[^"]*)""""),
            Regex("""url\s*:\s*['"]((?:https?:)?\\?/\\?/[^'"]+\.m3u8[^'"]*)['"]"""),
            Regex("""(?:main|source)\s*=\s*['"]((?:https?:)?\\?/\\?/[^'"]+\.m3u8[^'"]*)['"]"""),
        )
        for (pattern in patterns) {
            val value = pattern.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (value.isNotBlank()) {
                val clean = value.replace("\\/", "/")
                if (clean.startsWith("//")) return "https:$clean"
                if (clean.startsWith("http")) return clean
                return java.net.URI(pageUrl).resolve(clean).toString()
            }
        }
        val absolute = Regex("""https?://[^"'\s\\]+\.m3u8[^"'\s\\]*""").find(html)?.value
        return absolute?.replace("\\/", "/")
    }

    companion object {
        private const val DETAIL_TTL_MS = 3 * 60_000L
        private const val SEARCH_TTL_MS = 2 * 60_000L
        private const val PLAY_TTL_MS = 5 * 60_000L

        private val detailCache = ConcurrentHashMap<String, CachedDetail>()
        private val searchCache = ConcurrentHashMap<String, CachedSearch>()
        private val playCache = ConcurrentHashMap<String, CachedPlayUrl>()

        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
