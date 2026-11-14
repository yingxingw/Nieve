package com.yunx.app.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface GateState {
    data object Checking : GateState
    data object Ready : GateState
    data class Disabled(val message: String) : GateState
    data class ForceUpdate(val config: NieveRemote.Config) : GateState
}

/**
 * 启动时的远程开关：
 * - disabled=true：整个应用只显示停用页，不进入 Shell；
 * - forceUpdate=true 且版本落后：只显示强制更新页；
 * - 其他情况（含网络失败）：正常进入。
 */
@Composable
fun NieveRemoteGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<GateState>(GateState.Checking) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(retry) {
        val versionCode = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        }.getOrDefault(0)

        state = when (val result = NieveRemote.fetchConfig()) {
            is NieveRemote.ConfigResult.Failed -> GateState.Ready
            is NieveRemote.ConfigResult.Ok -> {
                val config = result.config
                val required = maxOf(config.versionCode, config.minVersionCode)
                when {
                    config.disabled -> GateState.Disabled(config.disabledMessage)
                    (config.forceUpdate && required > versionCode) ||
                        config.minVersionCode > versionCode -> GateState.ForceUpdate(config)
                    else -> GateState.Ready
                }
            }
        }
    }

    when (val current = state) {
        GateState.Checking, GateState.Ready -> content()
        is GateState.Disabled -> RemoteDisabledScreen(
            message = current.message,
            onRetry = { retry++ },
        )
        is GateState.ForceUpdate -> RemoteForceUpdateScreen(
            config = current.config,
            onRetry = { retry++ },
        )
    }
}

@Composable
private fun RemoteDisabledScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Nieve 暂停使用",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message.ifBlank { "请等待后续版本。" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
            TextButton(onClick = onRetry) {
                Text("重新检测")
            }
        }
    }
}

@Composable
private fun RemoteForceUpdateScreen(config: NieveRemote.Config, onRetry: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "需要更新到 v${config.versionName.ifBlank { "新版本" }}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (config.changelog.isNotBlank()) {
                Text(
                    text = config.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = {
                    openRemoteUrl(
                        context,
                        config.downloadUrl.ifBlank { NieveRemote.FALLBACK_WEB },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往更新")
            }
            TextButton(onClick = onRetry) {
                Text("重新检测")
            }
        }
    }
}

private fun openRemoteUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
