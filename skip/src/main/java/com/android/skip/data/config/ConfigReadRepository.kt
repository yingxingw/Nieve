package com.android.skip.data.config

import android.graphics.Rect
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.skip.R
import com.android.skip.data.network.MyApiNetwork
import com.android.skip.dataclass.ConfigLoadSchema
import com.android.skip.dataclass.ConfigPostSchema
import com.android.skip.dataclass.ConfigReadSchema
import com.android.skip.dataclass.ConfigState
import com.android.skip.dataclass.LoadSkipBound
import com.android.skip.dataclass.LoadSkipId
import com.android.skip.dataclass.LoadSkipText
import com.blankj.utilcode.util.StringUtils.getString
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigReadRepository @Inject constructor(
    private val myApiNetwork: MyApiNetwork
) {
    private val _configPostState =
        MutableLiveData(ConfigPostSchema(ConfigState.FAIL, getString(R.string.invalid_config)))
    val configPostState: LiveData<ConfigPostSchema> = _configPostState

    /**
     * 读取所有启用的订阅源并合并。远程地址失败时优先回退到上一次缓存，
     * 因此断网也不会把已经导入的规则立刻清空。
     */
    suspend fun readConfig() = withContext(Dispatchers.IO) {
        val defaultUrl = getString(R.string.store_default_config)
        val subscriptions = SubscriptionStore.ensureSeeded(defaultUrl)
        val enabled = subscriptions.filter { it.enabled }
        if (enabled.isEmpty()) {
            return@withContext ConfigPostSchema(
                ConfigState.SUCCESS,
                "没有启用的订阅源",
                emptyList(),
            )
        }

        val allConfigs = mutableListOf<ConfigReadSchema>()
        val messages = mutableListOf<String>()
        var loadedAny = false

        for (subscription in enabled) {
            val remoteContent = if (subscription.url.isNotBlank() && !subscription.localOverride) {
                runCatching {
                    myApiNetwork.fetchConfigFromUrl(subscription.url)
                }.getOrNull()
            } else {
                null
            }
            val content = remoteContent ?: subscription.content
            if (content.isBlank()) {
                val message = if (subscription.url.isNotBlank()) {
                    "「${subscription.name}」拉取失败"
                } else {
                    "「${subscription.name}」没有规则内容"
                }
                messages.add(message)
                SubscriptionStore.update(subscription.copy(lastError = message))
                continue
            }

            val parsed = SubscriptionParser.parse(content)
            if (parsed.configs.isEmpty()) {
                val message = if (parsed.note.isNotBlank()) {
                    "「${subscription.name}」${parsed.note}"
                } else {
                    "「${subscription.name}」没有可识别的基础规则"
                }
                messages.add(message)
                SubscriptionStore.update(subscription.copy(lastError = message))
                continue
            }

            allConfigs.addAll(parsed.configs)
            loadedAny = true
            val fallbackMessage = if (remoteContent == null && subscription.url.isNotBlank()) {
                "远程更新失败，已使用上次缓存"
            } else {
                ""
            }
            SubscriptionStore.cacheParsed(
                subscription = subscription,
                content = content,
                parsed = parsed,
                error = fallbackMessage,
            )
        }

        val merged = mergeConfigs(allConfigs)
        if (!loadedAny) {
            return@withContext ConfigPostSchema(
                ConfigState.FAIL,
                messages.joinToString("；").ifBlank { getString(R.string.invalid_config) },
                null,
            )
        }

        ConfigPostSchema(
            ConfigState.SUCCESS,
            md5(Gson().toJson(merged)),
            merged,
        )
    }

    fun changeConfigPostState(configPostSchema: ConfigPostSchema) {
        _configPostState.postValue(configPostSchema)
    }

    fun handleConfig(configPostSchema: ConfigPostSchema): Map<String, ConfigLoadSchema>? {
        return try {
            val list = mergeConfigs(configPostSchema.configReadSchemaList.orEmpty())
            list.associate { config ->
                val newSkipTexts = config.skipTexts?.map { skipText ->
                    LoadSkipText(
                        text = skipText.text,
                        activityName = skipText.activityName,
                        length = skipText.length,
                        click = skipText.click?.let(::convertClick),
                    )
                }
                val newSkipIds = config.skipIds?.map { skipId ->
                    LoadSkipId(
                        id = skipId.id,
                        activityName = skipId.activityName,
                        click = skipId.click?.let(::convertClick),
                    )
                }
                val newSkipBounds = config.skipBounds?.map { skipBound ->
                    LoadSkipBound(
                        bound = convertBound(skipBound.bound),
                        activityName = skipBound.activityName,
                        click = skipBound.click?.let(::convertClick),
                    )
                }
                config.packageName to ConfigLoadSchema(
                    config.packageName,
                    skipTexts = newSkipTexts,
                    skipIds = newSkipIds,
                    skipBounds = newSkipBounds,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 同一应用的多个订阅合并，文本/ID/坐标去重，避免重复创建大量匹配任务。 */
    fun mergeConfigs(configs: List<ConfigReadSchema>): List<ConfigReadSchema> {
        val merged = LinkedHashMap<String, ConfigReadSchema>()
        for (config in configs) {
            val old = merged[config.packageName]
            if (old == null) {
                merged[config.packageName] = config
            } else {
                merged[config.packageName] = old.copy(
                    skipTexts = (old.skipTexts.orEmpty() + config.skipTexts.orEmpty())
                        .distinctBy { "${it.text}|${it.activityName}|${it.length}" },
                    skipIds = (old.skipIds.orEmpty() + config.skipIds.orEmpty())
                        .distinctBy { "${it.id}|${it.activityName}" },
                    skipBounds = (old.skipBounds.orEmpty() + config.skipBounds.orEmpty())
                        .distinctBy { "${it.bound}|${it.activityName}" },
                )
            }
        }
        return merged.values.toList()
    }

    private fun convertClick(click: String): Rect {
        val (x, y) = click.split(",").map { it.toInt() }
        return Rect(x, y, x + 1, y + 1)
    }

    private fun convertBound(bound: String): Rect {
        val (left, top, right, bottom) = bound.split(",").map { it.toInt() }
        return Rect(left, top, right, bottom)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
