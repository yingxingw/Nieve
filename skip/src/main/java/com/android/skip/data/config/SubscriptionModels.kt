package com.android.skip.data.config

import com.android.skip.dataclass.ConfigReadSchema

/**
 * 一条可导入的规则订阅源。
 *
 * [content] 是最近一次成功拉取/导入的原文缓存，断网时仍然可以用来解析；
 * [localOverride] 为 true 时优先使用本地编辑后的 [content]，不再自动覆盖。
 */
data class SkipSubscription(
    val id: String,
    val name: String,
    val url: String = "",
    val enabled: Boolean = true,
    val content: String = "",
    val format: String = "auto",
    val updatedAt: Long = 0L,
    val builtIn: Boolean = false,
    val localOverride: Boolean = false,
    val appCount: Int = 0,
    val groupCount: Int = 0,
    val ruleCount: Int = 0,
    val lastError: String = "",
)

/** 订阅里一个应用的展示信息，用于「查看配置」，不参与实际匹配。 */
data class SubscriptionAppInfo(
    val packageName: String,
    val appName: String,
    val groups: List<SubscriptionGroupInfo> = emptyList(),
    val convertedRules: Int = 0,
)

/** 订阅里的一个规则组。 */
data class SubscriptionGroupInfo(
    val name: String,
    val desc: String = "",
    val enabled: Boolean = true,
    val ruleCount: Int = 0,
    val sampleRules: List<String> = emptyList(),
)

/** 一次解析的结果：既供匹配引擎使用，也供 UI 展示。 */
data class ParsedSubscription(
    val name: String = "",
    val version: String = "",
    val author: String = "",
    val format: String = "skip",
    val configs: List<ConfigReadSchema> = emptyList(),
    val apps: List<SubscriptionAppInfo> = emptyList(),
    val appCount: Int = 0,
    val groupCount: Int = 0,
    val ruleCount: Int = 0,
    val note: String = "",
)
