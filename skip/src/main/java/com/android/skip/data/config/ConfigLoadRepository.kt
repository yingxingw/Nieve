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

        return withContext(Dispatchers.Main) {
            try {
                val configuredTexts = targetConfig?.skipTexts.orEmpty()
                val configuredTasks =
                    createSkipByTextTasks(this, rootNode, configuredTexts, activityName)
                // 内置兜底规则：云端配置缺失/过期时也能识别「跳过 / 我知道了 / 关闭」这类按钮。
                // 严格模式仍只用用户配置，避免误点。
                val builtinTasks = if (isStrict != true) {
                    createBuiltinSkipTasks(this, rootNode, activityName)
                } else {
                    emptyList()
                }
                val skipByIdTasks =
                    createSkipByIdTasks(this, rootNode, targetConfig?.skipIds, activityName)
                val skipByBoundTasks =
                    createSkipByBoundTasks(this, rootNode, targetConfig?.skipBounds, activityName)
                val tasks = configuredTasks + builtinTasks + skipByIdTasks + skipByBoundTasks
                awaitFirstNonNullOrComplete(this, tasks)
            } catch (e: Exception) {
                LogUtils.e(e)
                null
            }
        }
    }

    /**
     * 内置按钮文案。云端规则没命中时这些仍然生效；带「同意」的低置信按钮只在
     * 开屏/广告类 Activity 中匹配，避免在隐私协议里替用户点同意。
     */
    private fun builtinKeywords(activityName: String?): List<String> {
        val base = listOf(
            "跳过", "跳过广告", "跳过此广告", "跳过开屏广告", "点击跳过",
            "关闭广告", "关闭", "我知道了", "知道了", "以后再说", "下次再说", "暂不",
        )
        val adLikeActivity = activityName?.lowercase()?.let { name ->
            name.contains("splash") || name.contains("launch") ||
                name.contains("advert") || name.contains("adactivity")
        } == true
        return if (adLikeActivity) base + "同意" else base
    }

    private fun createBuiltinSkipTasks(
        scope: CoroutineScope,
        rootNode: AccessibilityNodeInfo,
        activityName: String?
    ): List<Deferred<Rect?>> {
        val keywords = builtinKeywords(activityName)
        return listOf(scope.async {
            val node = findMatchingNode(rootNode, keywords) ?: return@async null
            val target = clickableAncestor(node) ?: node
            val rect = Rect()
            target.getBoundsInScreen(rect)
            rect.takeIf { !it.isEmpty }
        })
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
                ).replace(" ", "")
            if (keywords.any { text.contains(it.replace(" ", "")) }) {
                if (node.isClickable) return node
                if (fallback == null) fallback = node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return fallback?.let { clickableAncestor(it) ?: it }
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