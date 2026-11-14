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

package com.yunx.app.ui.resolve

import androidx.compose.ui.res.stringResource
import com.yunx.app.R
import com.yunx.app.util.AppStrings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.shell.NieveGlassDialog
import com.yunx.app.shell.NieveGlassPillButton
import com.yunx.app.shell.nieveGlass
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState

/**
 * 下载直链弹窗：展示文件名与直链（长按直链复制），支持「开始下载」（分片多线程下载）。
 * 点「关闭」或弹窗外（管壁）关闭 = 放弃下载，由上层清理临时转存。
 *
 * ★ 改用 NieveGlassDialog（玻璃面板 + 弹出/回落动效），与解析页其它弹窗同一材质；
 *   原来的 M3 AlertDialog 是白底方角，和整页玻璃语言对不上。
 */
@Composable
fun DownloadLinkDialog(
    link: DownloadLink,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Dialog 内提示宿主（弹窗是独立窗口，需要自带 Snackbar 宿主）
    val snackbarHostState = rememberGlobalSnackbarHostState()

    NieveGlassDialog(
        onDismissRequest = onDismiss,
        title = link.filename.ifBlank { stringResource(R.string.sh_start_download) },
        modifier = modifier,
        confirmText = stringResource(R.string.sh_start_download),
        onConfirm = onDownload,
        dismissText = stringResource(R.string.sh_close),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.sh_direct_link_generated),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = link.downloadUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .nieveGlass(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = {},
                        onLongClickLabel = stringResource(R.string.sh_copy_download_link),
                        onLongClick = {
                            copyToClipboard(context, link.downloadUrl)
                            SnackbarController.show(AppStrings.get(R.string.sh_download_link_copied))
                        }
                    )
                    .padding(12.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            NieveGlassPillButton(
                text = stringResource(R.string.sh_copy_download_link),
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    copyToClipboard(context, link.downloadUrl)
                    SnackbarController.show(AppStrings.get(R.string.sh_download_link_copied))
                }
            )
            Text(
                text = stringResource(R.string.sh_download_start_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            // Dialog 内提示（独立窗口，需自带 Snackbar 宿主）
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("download_url", text))
}
