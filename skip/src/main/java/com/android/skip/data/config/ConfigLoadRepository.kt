package com.android.skip.data.config

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.android.skip.R
import com.android.skip.dataclass.ConfigLoadSchema
import com.android.skip.dataclass.LoadSkipBound
import com.android.skip.dataclass.LoadSkipId
import com.android.skip.dataclass.LoadSkipText
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.StringUtils.getString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ConfigLoadRepository @Inject constructor() {
    private var configLoadSchemaMap: Map<String, ConfigLoadSchema> = mutableMapOf()

    fun loadConfig(config: Map<String, ConfigLoadSchema>) {
        configLoadSchemaMap = config
    }

    suspend fun getTargetRect(
        rootNode: AccessibilityNodeInfo, activityName: String?, isStrict: Boolean?
    ): Rect? {
        val targetAppPackage = rootNode.packageName.toString()
        val targetConfig = configLoadSchemaMap[targetAppPackage]

        return withContext(Dispatchers.Default) {
            try {
                val globalConfig = configLoadSchemaMap["*"]
                val configs = listOfNotNull(targetConfig, globalConfig)
                val configuredTask = createConfiguredTask(this, rootNode, configs, activityName)
                // 内置兜底规则：云端配置缺失/过期时也能识别「跳过 / 我知道了 / 关闭」这类按钮。
                // 严格模式仍只用用户配置，避免误点。
                val builtinTasks = if (isStrict != true) {
                    createBuiltinSkipTasks(this, rootNode, activityName)
                } else {
                    emptyList()
                }
                val boundsTasks = createSkipByBoundTasks(
                    this,
                    rootNode,
                    configs.flatMap { it.skipBounds.orEmpty() },
                    activityName,
                )
                val tasks = listOf(configuredTask) + builtinTasks + boundsTasks
                awaitFirstNonNullOrComplete(this, tasks)
            } catch (e: Exception) {
                LogUtils.e(e)
                null
            }
        }
    }

    /**
     * 把当前应用（以及「*」全局规则）的文本/ID 规则合并成一次 BFS。
     * 订阅可能包含成百上千条规则，逐条 findAccessibilityNodeInfosByText 会把
     * 每次窗口变化都变成大量重复遍历；这里一次遍历同时比对文本、描述和控件 ID。
     */
    private fun createConfiguredTask(
        scope: CoroutineScope,
        rootNode: AccessibilityNodeInfo,
        configs: List<ConfigLoadSchema>,
        activityName: String?,
    ): Deferred<Rect?> = scope.async {
        val texts = configs.flatMap { it.skipTexts.orEmpty() }
            .filter { it.activityName == null || it.activityName == activityName }
            .map { it to normalizeRuleText(it.text) }
            .filter { it.second.isNotEmpty() }
        val ids = configs.flatMap { it.skipIds.orEmpty() }
            .filter { it.activityName == null || it.activityName == activityName }
        if (texts.isEmpty() && ids.isEmpty()) return@async null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 4000) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString().orEmpty()
            val nodeDesc = node.contentDescription?.toString().orEmpty()
            val nodeId = node.viewIdResourceName.orEmpty()
            val visible = node === rootNode || node.isVisibleToUser
            val normalizedText = if (visible) normalizeRuleText(nodeText) else ""
            val normalizedDesc = if (visible) normalizeRuleText(nodeDesc) else ""

            val matchedText = if (visible) texts.firstOrNull { (rule, keyword) ->
                val maxLength = rule.length
                val hitText = normalizedText.contains(keyword) &&
                    (maxLength == null || nodeText.length <= maxLength)
                val hitDesc = normalizedDesc.contains(keyword)
                hitText || hitDesc
            }?.first else null
            val matchedId = if (visible) ids.firstOrNull { it.id == nodeId } else null

            if (matchedText != null || matchedId != null) {
                matchedText?.click?.let { if (!it.isEmpty) return@async it }
                matchedId?.click?.let { if (!it.isEmpty) return@async it }
                val target = clickableSelfOrDescendant(node) ?: clickableAncestor(node) ?: node
                val rect = Rect()
                target.getBoundsInScreen(rect)
                if (!rect.isEmpty) return@async rect
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        null
    }

    /**
     * 内置按钮文案。云端规则没命中时这些仍然生效；带「同意」的低置信按钮只在
     * 开屏/广告类 Activity 中匹配，避免在隐私协议里替用户点同意。
     */
    private fun builtinKeywords(activityName: String?, adContext: Boolean): List<String> {
        val base = listOf(
            "跳过", "跳过广告", "跳过此广告", "跳过开屏广告", "点击跳过", "跳过本次",
            "关闭广告", "关闭弹窗", "点击关闭", "关闭",
            "我知道了", "知道了", "以后再说", "下次再说", "暂不",
            "同意并继续",
        )
        val adLikeActivity = activityName?.lowercase()?.let { name ->
            name.contains("splash") || name.contains("launch") ||
                name.contains("advert") || name.contains("adactivity")
        } == true
        return if (adLikeActivity || adContext) base + "同意" else base
    }

    /**
     * 页面本身出现「广告 / 开屏 / 跳过」时，也把「同意」视作广告场景；
     * 这样不会在普通隐私协议页无条件点同意。
     */
    private fun findAdContext(rootNode: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 1200) {
            val node = queue.removeFirst()
            val text = (
                node.text?.toString().orEmpty() + " " +
                    node.contentDescription?.toString().orEmpty()
                )
            if (text.contains("广告") || text.contains("开屏") || text.contains("跳过")) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun createBuiltinSkipTasks(
        scope: CoroutineScope,
        rootNode: AccessibilityNodeInfo,
        activityName: String?
    ): List<Deferred<Rect?>> {
        val adContext = findAdContext(rootNode)
        val keywords = builtinKeywords(activityName, adContext)
        return listOf(scope.async {
            val node = findMatchingNode(rootNode, keywords) ?: return@async null
            val target = clickableSelfOrDescendant(node) ?: clickableAncestor(node) ?: node
            val rect = Rect()
            target.getBoundsInScreen(rect)
            rect.takeIf { !it.isEmpty }
        })
    }

    /** 文本节点自己可点时优先点自己；否则再找可点子节点。 */
    private fun clickableSelfOrDescendant(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        if (node.isClickable) node else clickableDescendant(node)

    private fun normalizeRuleText(value: String): String =
        value.replace(Regex("[\\s\\u00A0\\u3000]+"), "").lowercase()

    /** 文本节点自己不可点击时，先看它的直接子节点（Compose/Flutter 常见）。 */
    private fun clickableDescendant(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { queue.add(it) }
        }
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 40) {
            val child = queue.removeFirst()
            if (child.isClickable) return child
            for (i in 0 until child.childCount) {
                child.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /** BFS 遍历一次，同时比对 text 与 contentDescription；优先返回可点击节点。 */
    private fun findMatchingNode(
        rootNode: AccessibilityNodeInfo,
        keywords: List<String>
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        var fallback: AccessibilityNodeInfo? = null
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 4000) {
            val node = queue.removeFirst()
            val text = (
                node.text?.toString().orEmpty() + " " +
                    node.contentDescription?.toString().orEmpty()
                )
            val normalized = normalizeRuleText(text)
            if (node === rootNode || node.isVisibleToUser) {
                if (keywords.any { normalized.contains(normalizeRuleText(it)) }) {
                    val selfTarget = clickableSelfOrDescendant(node)
                    if (selfTarget != null) return selfTarget
                    if (fallback == null) fallback = node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return fallback?.let { clickableAncestor(it) ?: clickableDescendant(it) ?: it }
    }

    /** 文本节点通常不可点击，向上找最多 4 层可点击父容器。 */
    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(4) {
            val parent = current?.parent ?: return null
            if (parent.isClickable) return parent
            current = parent
        }
        return null
    }

    private suspend fun <T> awaitFirstNonNullOrComplete(
        scope: CoroutineScope, tasks: List<Deferred<T?>>
    ): T? {
        val deferredResults = mutableListOf<Deferred<T?>>()

        tasks.forEach { deferred ->
            deferredResults.add(scope.async {
                val result = deferred.await()
                if (result != null) {
                    return@async result
                }
                null
            })
        }

        deferredResults.forEach { result ->
            val nonNullResult = result.await()
            if (nonNullResult != null) {
                return nonNullResult
            }
        }

        return null
    }

    private fun createSkipByTextTasks(
        scope: CoroutineScope,
        rootNode: AccessibilityNodeInfo,
        skipTexts: List<LoadSkipText>?,
        activityName: String?
    ): List<Deferred<Rect?>> {
        val deferredResults = mutableListOf<Deferred<Rect?>>()
        if (skipTexts.isNullOrEmpty()) return deferredResults

        for (skipText in skipTexts) {
            if (skipText.activityName != null && skipText.activityName != activityName) continue
            deferredResults.add(scope.async {
                val foundNode =
                    rootNode.findAccessibilityNodeInfosByText(skipText.text).firstOrNull()

                val targetNode = if (foundNode != null) {
                    if (skipText.length != null) {
                        if (foundNode.text != null && foundNode.text.length <= skipText.length) {
                            foundNode
                        } else {
                            null
                        }
                    } else {
                        foundNode
                    }
                } else {
                    null
                }

                if (targetNode != null) {
                    if (skipText.click != null) {
                        skipText.click
                    } else {
                        val rect = Rect()
                        targetNode.getBoundsInScreen(rect)
                        rect
                    }
                } else {
                    null
                }
            })
        }
        return deferredResults
    }

    private fun createSkipByIdTasks(
        scope: CoroutineScope,
        rootNode: AccessibilityNodeInfo,
        skipIds: List<LoadSkipId>?,
        activityName: String?
    ): List<Deferred<Rect?>> {
        val deferredResults = mutableListOf<Deferred<Rect?>>()
        if (skipIds.isNullOrEmpty()) return deferredResults

        for (skipId in skipIds) {
            if (skipId.activityName != null && skipId.activityName != activityName) continue
            deferredResults.add(scope.async {
                val foundNode = rootNode.findAccessibilityNodeInfosByViewId(skipId.id).firstOrNull()

                if (foundNode != null) {
                    if (skipId.click != null) {
                        skipId.click
                    } else {
                        val rect = Rect()
                        foundNode.getBoundsInScreen(rect)
                        rect
                    }
                } else {
                    null
                }
            })
        }
        return deferredResults
    }

    private fun createSkipByBoundTasks(
        scope: CoroutineScope,
        rootNode: AccessibilityNodeInfo,
        skipBounds: List<LoadSkipBound>?,
        activityName: String?
    ): List<Deferred<Rect?>> {
        val deferredResults = mutableListOf<Deferred<Rect?>>()
        if (skipBounds.isNullOrEmpty()) return deferredResults

        for (skipBound in skipBounds) {
            if (skipBound.activityName != null && skipBound.activityName != activityName) continue

            deferredResults.add(scope.async {
                val foundRect = traverseNode(rootNode, skipBound.bound)

                skipBound.click ?: foundRect
            })
        }
        return deferredResults
    }

    private fun traverseNode(
        rootNode: AccessibilityNodeInfo, targetRect: Rect
    ): Rect? {
        val queue: MutableList<AccessibilityNodeInfo> = mutableListOf(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)
            val nodeRect = Rect()
            node.getBoundsInScreen(nodeRect)
            if (abs(nodeRect.left - targetRect.left) <= 1 && abs(nodeRect.top - targetRect.top) <= 1 && abs(
                    nodeRect.right - targetRect.right
                ) <= 1 && abs(nodeRect.bottom - targetRect.bottom) <= 1
            ) {
                return nodeRect
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let {
                    queue.add(it)
                }
            }
        }
        return null
    }
}