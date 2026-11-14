package com.android.skip.data.config

import com.android.skip.dataclass.ConfigReadSchema
import com.android.skip.dataclass.ReadSkipId
import com.android.skip.dataclass.ReadSkipText
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import org.yaml.snakeyaml.Yaml
import java.io.StringReader

/**
 * 订阅解析器。
 *
 * 支持两类内容：
 * 1. SKIP 原生 YAML/JSON：顶层是 [{ packageName, skipTexts, skipIds, skipBounds }]。
 * 2. GKD JSON5 订阅：顶层是 { apps: [{ id, name, groups: [...] }], globalGroups: [...] }。
 *
 * GKD 的完整选择器语法非常复杂，这里只把广告/开屏/弹窗类规则组里可确定的部分
 * （[text=...]、[desc=...]、[vid=...]、[id=...]）转成 SKIP 的文本/控件 ID 规则；
 * 复杂父子关系选择器不会假装支持，UI 会明确显示转换数量。
 */
object SubscriptionParser {
    private const val MAX_RULES_PER_APP = 48
    private const val MAX_TEXT_LENGTH = 24

    private val textAttrRegex = Regex(
        "\\b(text|desc)\\s*(==|=|!=|\\*=|\\^=|\\$=)\\s*(?:\"([^\"]*)\"|'([^']*)')"
    )
    private val idAttrRegex = Regex(
        "\\b(vid|id)\\s*(==|=|!=)\\s*(?:\"([^\"]*)\"|'([^']*)')"
    )

    fun parse(raw: String): ParsedSubscription {
        val content = raw.trim()
        if (content.isEmpty()) return ParsedSubscription()
        return runCatching {
            if (content.startsWith("{")) {
                parseGkd(content)
            } else {
                parseSkip(content)
            }
        }.getOrElse {
            ParsedSubscription(note = "解析失败：${it.message ?: "格式不正确"}")
        }
    }

    private fun parseSkip(content: String): ParsedSubscription {
        val list = runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<ConfigReadSchema>>() {}.type
            Gson().fromJson<List<ConfigReadSchema>>(content, type)
        }.getOrNull() ?: runCatching {
            Yaml().load<List<ConfigReadSchema>>(content)
        }.getOrNull() ?: emptyList()

        val apps = list.map { config ->
            val rules = listOfNotNull(
                config.skipTexts?.size,
                config.skipIds?.size,
                config.skipBounds?.size,
            ).sum()
            SubscriptionAppInfo(
                packageName = config.packageName,
                appName = config.packageName,
                groups = listOf(
                    SubscriptionGroupInfo(
                        name = "文本规则",
                        ruleCount = config.skipTexts?.size ?: 0,
                        sampleRules = config.skipTexts.orEmpty().take(3).map { it.text },
                    ),
                    SubscriptionGroupInfo(
                        name = "控件 ID 规则",
                        ruleCount = config.skipIds?.size ?: 0,
                        sampleRules = config.skipIds.orEmpty().take(3).map { it.id },
                    ),
                    SubscriptionGroupInfo(
                        name = "坐标规则",
                        ruleCount = config.skipBounds?.size ?: 0,
                    ),
                ).filter { it.ruleCount > 0 },
                convertedRules = rules,
            )
        }
        return ParsedSubscription(
            name = "本地/远程 SKIP 规则",
            format = "skip",
            configs = list,
            apps = apps,
            appCount = apps.size,
            groupCount = apps.sumOf { it.groups.size },
            ruleCount = apps.sumOf { it.convertedRules },
            note = "SKIP 原生规则，已完整载入",
        )
    }

    private fun parseGkd(content: String): ParsedSubscription {
        val reader = JsonReader(StringReader(content))
        reader.isLenient = true
        val root = com.google.gson.JsonParser.parseReader(reader) as? JsonObject
            ?: return ParsedSubscription(note = "GKD 订阅格式不正确：根节点不是对象")
        val name = jsonString(root, "name")
        val version = jsonString(root, "version")
        val author = jsonString(root, "author")
        val apps = jsonArray(root, "apps")

        val configs = mutableListOf<ConfigReadSchema>()
        val appInfos = mutableListOf<SubscriptionAppInfo>()
        var convertedTotal = 0

        if (apps != null) {
            for (ai in 0 until apps.size()) {
                val app = apps.get(ai) as? JsonObject ?: continue
                val packageName = jsonString(app, "id")
                if (packageName.isBlank()) continue
                val appName = jsonString(app, "name").ifBlank { packageName }
                val groups = jsonArray(app, "groups")
                val appTexts = mutableListOf<ReadSkipText>()
                val appIds = mutableListOf<ReadSkipId>()
                val groupInfos = mutableListOf<SubscriptionGroupInfo>()
                var appConverted = 0

                if (groups != null) {
                    for (gi in 0 until groups.size()) {
                        val group = groups.get(gi) as? JsonObject ?: continue
                        val groupName = jsonString(group, "name")
                        val groupDesc = jsonString(group, "desc")
                        val enabled = jsonBoolean(group, "enable", true)
                        val rules = jsonArray(group, "rules")
                        val ruleSamples = mutableListOf<String>()
                        var groupRules = 0

                        if (isAdGroup(groupName, groupDesc) && rules != null) {
                            for (ri in 0 until rules.size()) {
                                if (appConverted >= MAX_RULES_PER_APP) break
                                val rule = rules.get(ri) as? JsonObject ?: continue
                                val action = jsonString(rule, "action")
                                if (action.equals("back", true) ||
                                    action.equals("longClick", true) ||
                                    action.equals("swipe", true)
                                ) {
                                    continue
                                }
                                val selectors = buildList {
                                    add(jsonString(rule, "matches"))
                                    addAll(jsonStringList(rule, "anyMatches"))
                                }
                                val ruleTexts = mutableListOf<String>()
                                val ruleIds = mutableListOf<String>()
                                for (selector in selectors) {
                                    collectTexts(selector, ruleTexts)
                                    collectIds(selector, packageName, ruleIds)
                                }
                                for (text in ruleTexts.distinct()) {
                                    if (appConverted >= MAX_RULES_PER_APP) break
                                    appTexts.add(ReadSkipText(text = text))
                                    appConverted++
                                    groupRules++
                                    if (ruleSamples.size < 3) ruleSamples.add(text)
                                }
                                for (id in ruleIds.distinct()) {
                                    if (appConverted >= MAX_RULES_PER_APP) break
                                    appIds.add(ReadSkipId(id = id))
                                    appConverted++
                                    groupRules++
                                    if (ruleSamples.size < 3) {
                                        ruleSamples.add(id.substringAfterLast('/'))
                                    }
                                }
                            }
                        }
                        groupInfos.add(
                            SubscriptionGroupInfo(
                                name = groupName.ifBlank { "未命名规则组" },
                                desc = groupDesc,
                                enabled = enabled,
                                ruleCount = groupRules,
                                sampleRules = ruleSamples,
                            )
                        )
                    }
                }

                if (appTexts.isNotEmpty() || appIds.isNotEmpty()) {
                    configs.add(
                        ConfigReadSchema(
                            packageName = packageName,
                            skipTexts = appTexts,
                            skipIds = appIds,
                        )
                    )
                }
                appInfos.add(
                    SubscriptionAppInfo(
                        packageName = packageName,
                        appName = appName,
                        groups = groupInfos,
                        convertedRules = appConverted,
                    )
                )
                convertedTotal += appConverted
            }
        }

        // 全局规则里也只取广告/开屏类，作为「*」配置。
        val globalTexts = mutableListOf<ReadSkipText>()
        val globalGroups = jsonArray(root, "globalGroups")
        if (globalGroups != null) {
            for (gi in 0 until globalGroups.size()) {
                val group = globalGroups.get(gi) as? JsonObject ?: continue
                val groupName = jsonString(group, "name")
                val groupDesc = jsonString(group, "desc")
                if (!isAdGroup(groupName, groupDesc)) continue
                val rules = jsonArray(group, "rules") ?: continue
                for (ri in 0 until rules.size()) {
                    val rule = rules.get(ri) as? JsonObject ?: continue
                    val selectors = buildList {
                        add(jsonString(rule, "matches"))
                        addAll(jsonStringList(rule, "anyMatches"))
                    }
                    for (selector in selectors) {
                        val texts = mutableListOf<String>()
                        collectTexts(selector, texts)
                        for (text in texts.distinct()) {
                            if (text.length <= MAX_TEXT_LENGTH &&
                                globalTexts.none { it.text == text }
                            ) {
                                globalTexts.add(ReadSkipText(text = text))
                            }
                        }
                    }
                }
            }
        }
        if (globalTexts.isNotEmpty()) {
            configs.add(
                ConfigReadSchema(
                    packageName = "*",
                    skipTexts = globalTexts,
                )
            )
        }

        return ParsedSubscription(
            name = name,
            version = version,
            author = author,
            format = "gkd",
            configs = configs,
            apps = appInfos,
            appCount = appInfos.size,
            groupCount = appInfos.sumOf { it.groups.size },
            ruleCount = convertedTotal + globalTexts.size,
            note = "GKD 订阅：已在广告/开屏/弹窗类规则组中转换 $convertedTotal 条文本/ID 规则；复杂层级选择器未启用",
        )
    }

    private fun isAdGroup(name: String, desc: String): Boolean {
        val text = (name + " " + desc).lowercase()
        return text.contains("广告") || text.contains("开屏") || text.contains("跳过") ||
            text.contains("弹窗") || text.contains("关闭")
    }

    private fun collectTexts(selector: String, output: MutableList<String>) {
        if (selector.isBlank()) return
        for (match in textAttrRegex.findAll(selector)) {
            if (match.groupValues[2] == "!=") continue
            val value = match.groupValues[3].ifBlank { match.groupValues[4] }
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .trim()
            if (value.isNotBlank() &&
                value.length <= MAX_TEXT_LENGTH &&
                output.size < MAX_RULES_PER_APP
            ) {
                output.add(value)
            }
        }
    }

    private fun collectIds(selector: String, packageName: String, output: MutableList<String>) {
        if (selector.isBlank()) return
        for (match in idAttrRegex.findAll(selector)) {
            if (match.groupValues[2] == "!=") continue
            val type = match.groupValues[1]
            val raw = match.groupValues[3].ifBlank { match.groupValues[4] }.trim()
            if (raw.isBlank()) continue
            val id = if (type == "vid" && !raw.contains(":id/")) {
                "$packageName:id/$raw"
            } else {
                raw
            }
            if (output.size < MAX_RULES_PER_APP) output.add(id)
        }
    }

    private fun jsonString(obj: JsonObject, key: String): String {
        val value = obj.get(key) ?: return ""
        return runCatching { value.asString }.getOrDefault("")
    }

    private fun jsonArray(obj: JsonObject, key: String): JsonArray? =
        obj.get(key) as? JsonArray

    private fun jsonBoolean(obj: JsonObject, key: String, default: Boolean): Boolean {
        val value = obj.get(key) ?: return default
        return runCatching { value.asBoolean }.getOrDefault(default)
    }

    private fun jsonStringList(obj: JsonObject, key: String): List<String> {
        val value = obj.get(key) ?: return emptyList()
        return jsonToStringList(value)
    }

    private fun jsonToStringList(element: JsonElement): List<String> {
        val array = element as? JsonArray
        if (array != null) {
            return buildList {
                for (i in 0 until array.size()) {
                    runCatching { add(array.get(i).asString) }
                }
            }
        }
        return runCatching { listOf(element.asString) }.getOrDefault(emptyList())
    }
}
