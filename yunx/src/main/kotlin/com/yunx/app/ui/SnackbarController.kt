/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 顺序保存提示；仅最上层仍在组合中的宿主有权显示和消费。 */
internal class SnackbarEventQueue {
    internal data class Event(val seq: Long, val message: String)
    internal data class Snapshot(val events: List<Event> = emptyList(), val hosts: List<Any> = emptyList())
    private val lock = Any()
    private var sequence = 0L
    private val hostPriorities = mutableMapOf<Any, Int>()
    internal val state = MutableStateFlow(Snapshot())

    fun show(message: String) = synchronized(lock) {
        state.value = state.value.copy(events = state.value.events + Event(++sequence, message))
    }

    fun register(host: Any, priority: Int = 0) = synchronized(lock) {
        hostPriorities[host] = priority
        state.value = state.value.copy(hosts = state.value.hosts.filterNot { it === host } + host)
    }

    fun unregister(host: Any) = synchronized(lock) {
        hostPriorities.remove(host)
        state.value = state.value.copy(hosts = state.value.hosts.filterNot { it === host })
    }

    fun consume(host: Any, sequence: Long) = synchronized(lock) {
        val current = state.value
        if (activeHost(current) === host && current.events.firstOrNull()?.seq == sequence) {
            state.value = current.copy(events = current.events.drop(1))
        }
    }

    fun eventsFor(host: Any) = state.map { snapshot ->
        snapshot.events.firstOrNull().takeIf { activeHost(snapshot) === host }
    }.distinctUntilChanged()

    /** 全局顶部宿主优先；弹窗/覆盖页的局部宿主只在没有全局宿主时接管。 */
    private fun activeHost(snapshot: Snapshot): Any? =
        snapshot.hosts.maxByOrNull { hostPriorities[it] ?: 0 }
}

/** 全局入口保持不变；连续调用按顺序排队，弹窗和覆盖页接管提示宿主。 */
object SnackbarController {
    internal val queue = SnackbarEventQueue()
    /** 登录类错误的中央弹窗；由 NieveShell 统一承载，避免被底栏遮挡。 */
    internal val loginError = MutableStateFlow<String?>(null)
    fun show(message: String) { queue.show(message) }
    fun showLoginError(message: String) { loginError.value = message }
    fun dismissLoginError() { loginError.value = null }
}

@Composable
fun GlobalSnackbarHost(modifier: Modifier = Modifier) {
    val hostState = rememberGlobalSnackbarHostState(priority = 1)
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 12.dp, end = 12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top),
        )
    }
}

/** 宿主切换取消旧显示，但保留队首消息，避免遮挡、重复显示和丢失。 */
@Composable
fun rememberGlobalSnackbarHostState(priority: Int = 0): SnackbarHostState {
    val hostState = remember { SnackbarHostState() }
    val host = remember { Any() }
    DisposableEffect(host) {
        SnackbarController.queue.register(host, priority)
        onDispose { SnackbarController.queue.unregister(host) }
    }
    LaunchedEffect(hostState, host) {
        SnackbarController.queue.eventsFor(host).collectLatest { event ->
            if (event != null) {
                hostState.showSnackbar(event.message)
                SnackbarController.queue.consume(host, event.seq)
            }
        }
    }
    return hostState
}
