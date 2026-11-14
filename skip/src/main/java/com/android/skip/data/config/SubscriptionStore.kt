package com.android.skip.data.config

import com.android.skip.R
import com.android.skip.util.DataStoreUtils
import com.blankj.utilcode.util.StringUtils.getString
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 多订阅源存储。使用 DataStore 里的一个字符串键保存 JSON 列表；
 * 旧版单 URL 键 `store_custom_config` 会在首次读取时迁移成一条订阅。
 */
object SubscriptionStore {
    private const val KEY = "nieve_subscription_sources_v1"
    private val gson = Gson()
    private val type = object : TypeToken<List<SkipSubscription>>() {}.type

    fun load(): List<SkipSubscription> {
        val raw = DataStoreUtils.getSyncData(KEY, "")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<SkipSubscription>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun save(list: List<SkipSubscription>) {
        DataStoreUtils.putSyncData(KEY, gson.toJson(list, type))
    }

    /** 首次使用时迁移旧配置；返回可直接展示/读取的订阅列表。 */
    fun ensureSeeded(defaultUrl: String): List<SkipSubscription> {
        val raw = DataStoreUtils.getSyncData(KEY, "")
        if (raw.isNotBlank()) return load()
        val legacy = DataStoreUtils.getSyncData(
            getString(R.string.store_custom_config),
            "",
        )
        val seeded = if (legacy.isBlank()) {
            listOf(defaultSource(defaultUrl))
        } else if (legacy.startsWith("http://") || legacy.startsWith("https://")) {
            listOf(
                SkipSubscription(
                    id = "legacy-remote",
                    name = "迁移的旧订阅",
                    url = legacy,
                )
            )
        } else {
            listOf(
                SkipSubscription(
                    id = "legacy-local",
                    name = "迁移的旧自定义规则",
                    content = legacy,
                    localOverride = true,
                )
            )
        }
        save(seeded)
        return seeded
    }

    fun defaultSource(defaultUrl: String) = SkipSubscription(
        id = "nieve-default",
        name = "内置默认规则",
        url = defaultUrl,
        builtIn = true,
    )

    fun update(subscription: SkipSubscription) {
        val list = ensureSeeded("").toMutableList()
        val index = list.indexOfFirst { it.id == subscription.id }
        if (index >= 0) list[index] = subscription else list.add(subscription)
        save(list)
    }

    fun delete(id: String) {
        val list = ensureSeeded("").filterNot { it.id == id }
        save(list)
    }

    fun cacheParsed(
        subscription: SkipSubscription,
        content: String,
        parsed: ParsedSubscription,
        error: String = "",
    ): SkipSubscription {
        val updated = subscription.copy(
            name = subscription.name.ifBlank { parsed.name }.ifBlank { "规则订阅" },
            content = content,
            updatedAt = System.currentTimeMillis(),
            appCount = parsed.appCount,
            groupCount = parsed.groupCount,
            ruleCount = parsed.ruleCount,
            format = parsed.format,
            lastError = error,
        )
        update(updated)
        return updated
    }
}
