package com.yunx.app.tools.step

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yunx.app.R
import com.yunx.app.tools.toolsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ZeppAccount(
    val id: String = UUID.randomUUID().toString(),
    var remark: String = "",
    var email: String = "",
    var password: String = "",
    var steps: Int = 8000,
    var appToken: String = "",
    var userId: String = "",
    var deviceId: String = "",
    var mac: String = "",
    var lastResult: String = "",
    var lastSuccess: Boolean = false,
)

object ZeppStore {
    private val gson = Gson()
    private val type = object : TypeToken<MutableList<ZeppAccount>>() {}.type

    fun load(context: Context): MutableList<ZeppAccount> {
        val raw = toolsPrefs(context).getString("zepp_accounts", null) ?: return mutableListOf()
        return runCatching { gson.fromJson<MutableList<ZeppAccount>>(raw, type) }.getOrNull()
            ?: mutableListOf()
    }

    fun save(context: Context, accounts: List<ZeppAccount>) {
        toolsPrefs(context).edit().putString("zepp_accounts", gson.toJson(accounts)).apply()
    }
}

class ZeppException(message: String) : Exception(message)

class ZeppClient(private val context: Context) {
    private val loginClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val client = OkHttpClient.Builder().build()
    private val gson = Gson()

    private val template: String by lazy {
        context.resources.openRawResource(R.raw.zepp_band_template)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
    }

    suspend fun login(email: String, password: String): ZeppAccount = withContext(Dispatchers.IO) {
        val access = requestAccessToken(email.trim(), password)
        val token = exchangeAppToken(access)
        val userId = token.second
        val appToken = token.first
        if (userId.isBlank() || appToken.isBlank()) throw ZeppException("登录返回数据不完整")

        val device = findOrBindDevice(userId, appToken)
        ZeppAccount(
            remark = "",
            email = email.trim(),
            password = password,
            appToken = appToken,
            userId = userId,
            deviceId = device.first,
            mac = device.second,
        )
    }

    suspend fun applySteps(account: ZeppAccount, steps: Int): ZeppAccount = withContext(Dispatchers.IO) {
        if (steps !in 1..98000) throw ZeppException("步数需在 1–98000 之间")
        val access = requestAccessToken(account.email.trim(), account.password)
        val (appToken, userId) = exchangeAppToken(access)
        val device = findOrBindDevice(userId, appToken)
        uploadBandData(appToken, userId, device.first, steps)
        account.copy(
            appToken = appToken,
            userId = userId,
            deviceId = device.first,
            mac = device.second,
        )
    }

    private fun requestAccessToken(email: String, password: String): String {
        val accountValue = if (email.contains("@")) email else "+86$email"
        val form = "emailOrPhone=${URLEncoder.encode(accountValue, "UTF-8")}" +
            "&password=${URLEncoder.encode(password, "UTF-8")}" +
            "&state=REDIRECTION&client_id=HuaMi&country_code=CN&token=access" +
            "&redirect_uri=${URLEncoder.encode("https://s3-us-west-2.amazonaws.com/hm-registration/successsignin.html}", "UTF-8")}"
        val encrypted = aesEncrypt(form.toByteArray(Charsets.UTF_8))
        val request = Request.Builder()
            .url("https://api-user.zepp.com/v2/registrations/tokens")
            .header("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("user-agent", "MiFit6.14.0 (M2007J1SC; Android 12; Density/2.75)")
            .header("app_name", "com.huami.midong")
            .header("appname", "com.huami.midong")
            .header("appplatform", "android_phone")
            .header("x-hm-ekv", "1")
            .header("hm-privacy-ceip", "false")
            .post(encrypted.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        return loginClient.newCall(request).execute().use { response ->
            if (response.code != 303) {
                throw ZeppException(if (response.code == 401) "账号或密码错误" else "登录异常，状态码 ${response.code}")
            }
            val location = response.header("Location") ?: throw ZeppException("登录未返回跳转地址")
            val value = when {
                "access=" in location -> location.substringAfter("access=").substringBefore("&")
                "refresh=" in location -> location.substringAfter("refresh=").substringBefore("&")
                "error=" in location -> throw ZeppException("账号或密码错误")
                else -> throw ZeppException("登录 token 接口请求失败")
            }
            if (value.isBlank()) throw ZeppException("账号或密码错误")
            value
        }
    }

    private fun exchangeAppToken(access: String): Pair<String, String> {
        val form = FormBody.Builder()
            .add("app_name", "com.xiaomi.hm.health")
            .add("app_version", "6.14.0")
            .add("code", access)
            .add("country_code", "CN")
            .add("device_id", "2C8B4939-0CCD-4E94-8CBA-CB8EA6E613A1")
            .add("device_model", "android_phone")
            .add("grant_type", "access_token")
            .add("third_name", "huami")
            .add(
                "dn",
                "account.zepp.com,api-user.zepp.com,api-mifit.zepp.com,api-watch.zepp.com," +
                    "app-analytics.zepp.com,api-analytics.huami.com,auth.zepp.com"
            )
            .add("source", "com.xiaomi.hm.health:6.14.0:50818")
            .add("lang", "zh")
            .build()
        val request = Request.Builder()
            .url("https://account.zepp.com/v2/client/login")
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN,zh;q=0.8")
            .header("Connection", "keep-alive")
            .header("app_name", "com.xiaomi.hm.health")
            .header("appname", "com.xiaomi.hm.health")
            .header("appplatform", "android_phone")
            .header("User-Agent", "MiFit6.14.0 (2211133C; Android 15; Density/2.75)")
            .post(form)
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code != 200) throw ZeppException("登录接口请求失败：${response.code}")
            val json = runCatching { JSONObject(body) }.getOrNull() ?: throw ZeppException("登录返回格式错误")
            if (json.optString("result") != "ok") throw ZeppException("登录失败：${json.optString("message").ifBlank { body.take(120) }}")
            val info = json.optJSONObject("token_info") ?: throw ZeppException("登录返回缺少 token")
            info.optString("app_token") to info.optString("user_id")
        }
    }

    private fun findOrBindDevice(userId: String, appToken: String): Pair<String, String> {
        val now = System.currentTimeMillis() / 1000
        val query = "t=$now&callid=$now&userid=$userId&device=android_35&device_type=android_phone" +
            "&enableMultiDevice=false&v=2.0&lang=zh_CN&channel=Normal&country=CN" +
            "&timezone=Asia/Shanghai&cv=50813_6.14.0"
        val request = Request.Builder()
            .url("https://api-mifit-cn.huami.com/v1/device/lists.json?$query")
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN,zh;q=0.8")
            .header("Connection", "keep-alive")
            .header("app_name", "com.xiaomi.hm.health")
            .header("appname", "com.xiaomi.hm.health")
            .header("appplatform", "android_phone")
            .header("User-Agent", "MiFit6.14.0 (2211133C; Android 15; Density/2.75)")
            .header("apptoken", appToken)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw ZeppException("Token 已失效，请重新登录")
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: throw ZeppException("获取设备列表失败")
            val data = json.optJSONArray("data")
            if (json.optInt("code") == 1 && data != null && data.length() > 0) {
                val first = data.optJSONObject(0)
                val deviceId = first?.optString("deviceid").orEmpty()
                val mac = first?.optString("mac").orEmpty()
                if (deviceId.isNotBlank()) return deviceId to mac
            }
            if (json.optInt("code") != 1 && response.code != 200) {
                throw ZeppException("获取设备列表失败：${json.optString("message").ifBlank { body.take(120) }}")
            }
        }

        val mac = randomMac()
        val deviceId = randomHex(16)
        val form = FormBody.Builder()
            .add("app_time", (System.currentTimeMillis() / 1000).toString())
            .add("code", "0")
            .add("activeStatus", "0")
            .add("bind_timezone", "32")
            .add("device_type", "0")
            .add("crcedUserId", "0")
            .add("userid", userId)
            .add("device", "android_29")
            .add("deviceid", deviceId)
            .add("enableMultiDevice", "true")
            .add("mac", mac)
            .add("productVersion", "256")
            .add("brandType", "-1")
            .add("productId", "61")
            .add("device_source", "58")
            .add("brand", "XiaoMi")
            .add("fw_version", "V1.0.0.04")
            .add("hardwareVersion", "V0.44.131.18")
            .add("soft_version", "6.13.1")
            .add("sys_model", "Xiaomi 10 Pro")
            .add("sys_version", "Android_35")
            .add("v", "2.0")
            .add("lang", "zh_CN")
            .add("channel", "Normal")
            .add("country", "CN")
            .add("timezone", "Asia/Shanghai")
            .add("cv", "50813_6.14.0")
            .build()
        val bindRequest = Request.Builder()
            .url("https://api-mifit-cn.huami.com/v1/device/binds.json")
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN,zh;q=0.8")
            .header("Connection", "keep-alive")
            .header("app_name", "com.xiaomi.hm.health")
            .header("appname", "com.xiaomi.hm.health")
            .header("appplatform", "android_phone")
            .header("User-Agent", "MiFit6.14.0 (2211133C; Android 15; Density/2.75)")
            .header("apptoken", appToken)
            .post(form)
            .build()
        client.newCall(bindRequest).execute().use { response ->
            if (response.code == 401) throw ZeppException("Token 已失效，请重新登录")
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull() ?: throw ZeppException("绑定设备请求失败")
            if (json.optInt("code") != 1) throw ZeppException("绑定设备失败")
        }
        return deviceId to mac
    }

    private fun uploadBandData(appToken: String, userId: String, deviceId: String, steps: Int) {
        val nowMs = System.currentTimeMillis()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(Date(nowMs))
        var dataJson = template
        dataJson = dataJson.replaceFirst(
            Regex("date%22%3A%22(.*?)%22%2C%22data"),
            "date%22%3A%22$date%22%2C%22data",
        )
        dataJson = dataJson.replaceFirst(
            Regex("ttl%5C%22%3A(.*?)%2C%5C%22dis"),
            "ttl%5C%22%3A$steps%2C%5C%22dis",
        )
        val body = "userid=$userId&last_sync_data_time=$nowMs&device_type=0" +
            "&last_deviceid=${deviceId.ifBlank { "DA932FFFFE8816E7" }}&data_json=$dataJson"
        val request = Request.Builder()
            .url("https://api-mifit-cn.huami.com/v1/data/band_data.json?&t=$nowMs")
            .header("apptoken", appToken)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw ZeppException("Token 已失效，请重新登录")
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw ZeppException("修改步数失败：${text.take(120)}")
            if (json.optInt("code") != 1) {
                throw ZeppException("修改步数失败：${json.optString("message").ifBlank { text.take(120) }}")
            }
        }
    }

    private fun aesEncrypt(input: ByteArray): ByteArray {
        val key = "xeNtBVqzDc6tuNTh".toByteArray(Charsets.UTF_8)
        val iv = "MAAAYAAAAAAAAABg".toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(input)
    }

    private fun randomMac(): String = (0 until 6).joinToString(":") {
        "%02X".format((0..255).random())
    }

    private fun randomHex(length: Int): String {
        val chars = "0123456789ABCDEF"
        return buildString { repeat(length) { append(chars.random()) } }
    }
}
