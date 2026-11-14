package com.yunx.app.tools.vip

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yunx.app.tools.toolsPrefs

/** 第三方解析线路。 */
data class VipParseLine(
    val key: String,
    val label: String,
    val prefix: String,
) {
    fun build(videoUrl: String): String = prefix + Uri.encode(videoUrl)
}

val VipParseLines = listOf(
    VipParseLine("qige", "七哥", "https://jx.202617.xyz/tv.php?url="),
    VipParseLine("zhenxiang", "臻享", "https://aibox.eu.org/?url="),
    VipParseLine("lx", "LX", "https://jx.lxyy.club/index.php?url="),
    VipParseLine("txnq", "TXNQ", "https://bfq.txnp.cn/player?url="),
    VipParseLine("bingdou", "冰豆", "https://bd.jx.cn/?url="),
    VipParseLine("789", "789", "https://jiexi.789jiexi.com/?url="),
)

/** 内置桌面站点。 */
data class VipSite(
    val key: String,
    val label: String,
    val url: String,
)

val VipSites = listOf(
    VipSite("tx", "小T 腾讯", "https://v.qq.com/"),
    VipSite("iqiyi", "小Q 爱奇艺", "https://www.iqiyi.com/"),
    VipSite("bili", "小B 哔哩哔哩", "https://www.bilibili.com/"),
    VipSite("youku", "小Y 优酷", "https://www.youku.com/"),
)

private val parseHosts = setOf(
    "jx.202617.xyz",
    "aibox.eu.org",
    "jx.lxyy.club",
    "bfq.txnp.cn",
    "bd.jx.cn",
    "jiexi.789jiexi.com",
)

fun isVipParsePage(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
    return parseHosts.any { host == it || host.endsWith(".$it") }
}

fun vipSiteOf(url: String): VipSite? {
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return null
    return VipSites.firstOrNull { site ->
        val siteHost = Uri.parse(site.url).host?.lowercase().orEmpty().removePrefix("www.")
        val pageHost = host.removePrefix("www.")
        pageHost == siteHost || pageHost.endsWith(".$siteHost")
    }
}

/** 从解析页地址里取回原始视频页地址。 */
fun extractVipSourceUrl(url: String): String? {
    val raw = runCatching { Uri.parse(url).getQueryParameter("url") }.getOrNull()
    return raw?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

/** 从分享文案里提取第一个链接；纯域名补 https。 */
fun normalizeVipUrl(raw: String): String? {
    val text = raw.trim()
    if (text.isBlank()) return null
    val link = Regex("""https?://[^\s"'<>]+""").find(text)?.value
        ?: text.takeIf { it.contains('.') && !it.contains(' ') }?.let { "https://$it" }
        ?: return null
    return link.trimEnd('。', '，', ',', '.', ')', '）', ']', '】', '》')
}

data class VipFavorite(
    val title: String,
    val videoUrl: String,
    val lineKey: String,
    val savedAt: Long,
)

/** 解析页收藏，按视频页地址去重。 */
object VipFavoriteStore {
    private const val KEY = "vip_parse_favorites_v1"
    private const val LIMIT = 100
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<VipFavorite>>() {}.type

    fun load(context: Context): MutableList<VipFavorite> {
        val raw = toolsPrefs(context).getString(KEY, null) ?: return mutableListOf()
        return runCatching { gson.fromJson<MutableList<VipFavorite>>(raw, listType) }
            .getOrNull() ?: mutableListOf()
    }

    fun save(context: Context, favorites: List<VipFavorite>) {
        toolsPrefs(context).edit().putString(KEY, gson.toJson(favorites.take(LIMIT))).apply()
    }
}
