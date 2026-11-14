package com.yunx.app.tools.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MusicException(message: String) : Exception(message)

private data class CachedSearch(val at: Long, val songs: List<Song>)
private data class CachedPlay(val at: Long, val url: String)

class MusicApi {
    private val client = sharedClient

    suspend fun search(source: MusicSource, keyword: String): List<Song> = withContext(Dispatchers.IO) {
        when (source) {
            MusicSource.KUGOU -> searchKugou(keyword)
            MusicSource.NETEASE -> searchNetease(keyword)
            MusicSource.QQ -> searchQq(keyword)
            MusicSource.KUWO -> emptyList()
            MusicSource.LOCAL -> emptyList()
        }
    }

    /** 一次搜完酷狗、网易云、QQ，三平台结果交替排列。 */
    suspend fun searchAll(keyword: String): List<Song> = coroutineScope {
        val cacheKey = keyword.trim().lowercase()
        searchCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.at < SEARCH_TTL_MS }
            ?.let { return@coroutineScope it.songs }

        val online = listOf(MusicSource.KUGOU, MusicSource.NETEASE, MusicSource.QQ)
        val groups = online.map { source ->
            async(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(7_000) { search(source, keyword) }
                }.getOrNull().orEmpty()
            }
        }.awaitAll()
        val maxSize = groups.maxOfOrNull { it.size } ?: 0
        val songs = buildList {
            for (index in 0 until maxSize) {
                groups.forEach { group -> group.getOrNull(index)?.let { add(it) } }
            }
        }
        searchCache[cacheKey] = CachedSearch(System.currentTimeMillis(), songs)
        songs
    }

    suspend fun playUrl(song: Song): String = withContext(Dispatchers.IO) {
        when (song.source) {
            MusicSource.LOCAL.key -> song.extra
            MusicSource.KUGOU.key -> kugouPlay(song.extra)
            MusicSource.NETEASE.key -> neteasePlay(song.id)
            MusicSource.QQ.key -> qqPlay(song.extra)
            else -> throw MusicException("未知音源")
        }
    }

    /** 先解析本平台；受版权限制时改用网易云同名歌曲，仍失败则用免费直链兜底。 */
    suspend fun resolvePlayable(song: Song): Pair<Song, String> = withContext(Dispatchers.IO) {
        if (song.source == MusicSource.LOCAL.key) return@withContext song to song.extra

        val cacheKey = "${song.source}:${song.id}"
        playCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.at < PLAY_TTL_MS }
            ?.let { return@withContext song to it.url }

        val direct = runCatching { playUrl(song) }
        direct.getOrNull()?.let {
            playCache[cacheKey] = CachedPlay(System.currentTimeMillis(), it)
            return@withContext song to it
        }

        val candidates = runCatching {
            search(MusicSource.NETEASE, "${song.name} ${song.artist}".trim())
        }.getOrDefault(emptyList())
        for (alt in candidates.take(4)) {
            if (alt.id == song.id && alt.source == song.source) continue
            val result = runCatching { playUrl(alt) }
            result.getOrNull()?.let {
                playCache[cacheKey] = CachedPlay(System.currentTimeMillis(), it)
                return@withContext alt to it
            }
        }
        kuwoResolve(song.name, song.artist)?.let {
            playCache[cacheKey] = CachedPlay(System.currentTimeMillis(), it)
            return@withContext song.copy(source = MusicSource.KUWO.key) to it
        }
        throw direct.exceptionOrNull() ?: MusicException("没有找到可播放的音源")
    }

    /** 酷我免费直链，作为三平台版权受限时的兜底音源。 */
    private fun kuwoResolve(name: String, artist: String): String? {
        val keyword = "$name $artist".trim()
        val url = "https://search.kuwo.cn/r.s".toHttpUrl().newBuilder()
            .addQueryParameter("all", keyword)
            .addQueryParameter("ft", "music")
            .addQueryParameter("itemset", "web_2013")
            .addQueryParameter("client", "kt")
            .addQueryParameter("pn", "0")
            .addQueryParameter("rn", "10")
            .addQueryParameter("rformat", "json")
            .addQueryParameter("encoding", "utf8")
            .build()
        val body = runCatching { getText(url.toString(), ua = "okhttp/3.10.0") }.getOrNull()
            ?: return null
        val normalizedName = normalize(name)
        val normalizedArtist = normalize(artist)
        val blocks = body.split("{'AARTIST'").drop(1)
        val best = blocks.mapNotNull { block ->
            val id = Regex("""'DC_TARGETID':'([^']+)'""").find(block)?.groupValues?.getOrNull(1)
                ?.ifBlank { null } ?: return@mapNotNull null
            val songName = Regex("""'SONGNAME':'([^']*)'""").find(block)?.groupValues?.getOrNull(1)
                .orEmpty().replace("&nbsp;", " ").trim()
            val songArtist = Regex("""'ARTIST':'([^']*)'""").find(block)?.groupValues?.getOrNull(1)
                .orEmpty().replace("&nbsp;", " ").trim()
            val normalizedSong = normalize(songName)
            val exactName = normalizedSong == normalizedName
            val containsName = normalizedSong.contains(normalizedName)
            val artistMatch = normalizedArtist.isNotBlank() && normalize(songArtist).contains(normalizedArtist)
            var score = when {
                exactName && artistMatch -> 5
                exactName -> 2
                containsName && artistMatch -> 3
                containsName -> 1
                else -> 0
            }
            if (songName.contains("伴奏") || songName.contains("KTV")) score -= 2
            if (score <= 0) null else Triple(score, id, songName)
        }.maxByOrNull { it.first } ?: return null

        val anti = "https://antiserver.kuwo.cn/anti.s".toHttpUrl().newBuilder()
            .addQueryParameter("type", "convert_url")
            .addQueryParameter("rid", "MUSIC_${best.second}")
            .addQueryParameter("format", "mp3|aac")
            .addQueryParameter("response", "url")
            .build()
        val direct = runCatching { getText(anti.toString(), ua = "okhttp/3.10.0") }.getOrNull()?.trim().orEmpty()
        return direct.takeIf { it.startsWith("http") }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("""[\s\-_/·.,，。()（）\[\]【】]"""), "")

    suspend fun lyric(song: Song): String = withContext(Dispatchers.IO) {
        when (song.source) {
            MusicSource.NETEASE.key -> neteaseLyric(song.id)
            MusicSource.QQ.key -> qqLyric(song.extra)
            MusicSource.KUGOU.key -> ""
            else -> ""
        }
    }

    private fun searchKugou(keyword: String): List<Song> {
        val url = "https://songsearch.kugou.com/song_search_v2".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", keyword)
            .addQueryParameter("page", "1")
            .addQueryParameter("pagesize", "30")
            .addQueryParameter("userid", "0")
            .addQueryParameter("clientver", "2000")
            .addQueryParameter("platform", "WebFilter")
            .addQueryParameter("filter", "2")
            .addQueryParameter("iscorrection", "1")
            .addQueryParameter("privilege_filter", "0")
            .addQueryParameter("area_code", "1")
            .build()
        val json = getJson(url.toString())
        val lists = json.optJSONObject("data")?.optJSONArray("lists") ?: return emptyList()
        return (0 until lists.length()).mapNotNull { i ->
            val item = lists.optJSONObject(i) ?: return@mapNotNull null
            val hash = item.optString("FileHash")
            if (hash.isBlank()) return@mapNotNull null
            Song(
                id = hash,
                name = item.optString("SongName").ifBlank { "未知歌曲" },
                artist = item.optString("SingerName"),
                album = item.optString("AlbumName"),
                source = MusicSource.KUGOU.key,
                extra = hash,
                cover = item.optString("Image"),
            )
        }
    }

    private fun searchNetease(keyword: String): List<Song> {
        val url = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("csrf_token", "")
            .addQueryParameter("s", keyword)
            .addQueryParameter("type", "1")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", "30")
            .build()
        val json = getJson(url.toString(), referer = "https://music.163.com/")
        val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        return (0 until songs.length()).mapNotNull { i ->
            val item = songs.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val artists = item.optJSONArray("artists")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }.joinToString(" / ")
            }.orEmpty()
            val album = item.optJSONObject("album")
            Song(
                id = id,
                name = item.optString("name"),
                artist = artists,
                album = album?.optString("name").orEmpty(),
                source = MusicSource.NETEASE.key,
                extra = id,
                cover = album?.optString("picUrl").orEmpty(),
            )
        }
    }

    private fun searchQq(keyword: String): List<Song> {
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp".toHttpUrl().newBuilder()
            .addQueryParameter("p", "1")
            .addQueryParameter("n", "30")
            .addQueryParameter("w", keyword)
            .addQueryParameter("format", "json")
            .addQueryParameter("cr", "1")
            .addQueryParameter("g_tk", "5381")
            .addQueryParameter("t", "0")
            .addQueryParameter("inCharset", "utf8")
            .addQueryParameter("outCharset", "utf-8")
            .addQueryParameter("notice", "0")
            .addQueryParameter("platform", "yqq")
            .addQueryParameter("needNewCode", "1")
            .build()
        val json = getJson(url.toString(), referer = "https://y.qq.com/")
        val songs = json.optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list") ?: return emptyList()
        return (0 until songs.length()).mapNotNull { i ->
            val item = songs.optJSONObject(i) ?: return@mapNotNull null
            val mid = item.optString("songmid")
            if (mid.isBlank()) return@mapNotNull null
            val singers = item.optJSONArray("singer")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }.joinToString(" / ")
            }.orEmpty()
            val albumMid = item.optString("albummid")
            Song(
                id = item.optString("songid").ifBlank { mid },
                name = item.optString("songname"),
                artist = singers,
                album = item.optString("albumname"),
                source = MusicSource.QQ.key,
                extra = mid,
                cover = if (albumMid.isBlank()) "" else "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg",
            )
        }
    }

    private fun neteasePlay(id: String): String {
        val outer = "https://music.163.com/song/media/outer/url?id=$id.mp3"
        val request = Request.Builder()
            .url(outer)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Range", "bytes=0-0")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw MusicException("网易云该歌曲暂时无法播放（${response.code}）")
            val finalUrl = response.request.url.toString()
            if (finalUrl.contains("music.163.com/404")) {
                throw MusicException("网易云该歌曲受版权限制")
            }
            return finalUrl.replaceFirst("http://", "https://")
        }
    }

    private fun kugouPlay(hash: String): String {
        val url = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=$hash"
        val body = getText(url, ua = "Mozilla/5.0 (Linux; Android 10)")
        val json = runCatching { JSONObject(body) }.getOrNull()
        val playUrl = json?.optString("url").orEmpty()
        if (playUrl.isBlank()) throw MusicException("酷狗该歌曲受版权限制，无法获取播放地址，可切换网易云或 QQ 音乐")
        return playUrl.replaceFirst("http://", "https://")
    }

    private fun qqPlay(mid: String): String {
        val payload = """{"req_0":{"module":"vkey.GetVkeyServer","method":"CgiGetVkey","param":{"guid":"10000","songmid":["$mid"],"songtype":[0],"uin":"0","loginflag":1,"platform":"20"}},"comm":{"uin":0,"format":"json","ct":24,"cv":0}}"""
        val requestUrl = "https://u.y.qq.com/cgi-bin/musicu.fcg".toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("data", payload)
            .build()
        val json = getJson(requestUrl.toString(), referer = "https://y.qq.com/")
        val data = json.optJSONObject("req_0")?.optJSONObject("data")
        val sip = data?.optJSONArray("sip")?.optString(0).orEmpty()
        val purl = data?.optJSONArray("midurlinfo")?.optJSONObject(0)?.optString("purl").orEmpty()
        if (purl.isBlank()) throw MusicException("QQ 音乐该歌曲受版权/会员限制，无法播放")
        val playUrl = if (purl.startsWith("http")) purl else sip + purl
        return playUrl.replaceFirst("http://", "https://")
    }

    private fun neteaseLyric(id: String): String {
        val url = "http://interface3.music.163.com/api/song/lyric?id=$id&os=Linux&lv=-1&kv=-1&tv=-1"
        val json = runCatching { getJson(url, referer = "https://music.163.com/") }.getOrNull() ?: return ""
        return json.optJSONObject("lrc")?.optString("lyric").orEmpty()
    }

    private fun qqLyric(mid: String): String {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg".toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("nobase64", "1")
            .addQueryParameter("songmid", mid)
            .build()
        val text = getText(url.toString(), referer = "https://y.qq.com/")
        val clean = text.trim().removePrefix("MusicJsonCallback(").removeSuffix(")").removeSuffix(";")
        val json = runCatching { JSONObject(clean) }.getOrNull() ?: return ""
        return json.optString("lyric").orEmpty()
    }

    private fun getJson(url: String, referer: String? = null, ua: String? = null): JSONObject {
        val text = getText(url, referer, ua)
        val clean = text.trim().let {
            if (it.startsWith("callback(") || it.startsWith("MusicJsonCallback(")) {
                it.substringAfter('(').substringBeforeLast(')')
            } else it
        }
        return runCatching { JSONObject(clean) }.getOrNull() ?: throw MusicException("接口返回格式错误")
    }

    private fun getText(url: String, referer: String? = null, ua: String? = null): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", ua ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .header("Accept", "application/json,text/plain,*/*")
        if (referer != null) builder.header("Referer", referer)
        client.newCall(builder.get().build()).execute().use { response ->
            if (response.code != 200) throw MusicException("请求失败：${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        private const val SEARCH_TTL_MS = 2 * 60_000L
        private const val PLAY_TTL_MS = 10 * 60_000L
        private val searchCache = ConcurrentHashMap<String, CachedSearch>()
        private val playCache = ConcurrentHashMap<String, CachedPlay>()

        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
