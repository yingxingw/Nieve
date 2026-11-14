/*
 * Nieve - 网盘 / 视频链接解析与高速下载工具 for Android.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.yunx.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** 应用对外的功能入口（只保留 GitHub 令牌创建页，不再包含原作者社群或仓库入口）。 */
object AppLinks {
    /**
     * GitHub 个人访问令牌（classic）创建页，带 `scopes` 与 `description` 预填。
     *
     * ★ 为什么不用列表页 `https://github.com/settings/tokens`：登录后还要再点一次「Generate new token」，
     *   手机上翻 GitHub 设置的成本比电脑高，直接把权限和备注填好省两步。
     */
    const val GITHUB_TOKEN_PAGE = "https://github.com/settings/tokens/new?scopes=public_repo&description=Nieve"

    /**
     * 用系统默认浏览器打开 [GITHUB_TOKEN_PAGE]；返回 false 表示这台设备上没有应用能处理 https。
     *
     * ★ 不做 `resolveActivity()` 预检（Android 11+ 包可见性会把「装了浏览器」误判成「没装」），失败交给调用方退化成复制链接。
     */
    fun openGitHubTokenPage(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_TOKEN_PAGE))
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
