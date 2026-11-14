package com.yunx.app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 远程公告列表：公告文件支持多份，按文件顺序展示。 */
@Composable
fun NieveAnnouncementsScreen(onBack: () -> Unit) {
    var feed by remember { mutableStateOf<NieveRemote.AnnouncementFeed?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(retry) {
        feed = NieveRemote.fetchAnnouncements()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(bottom = 108.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NieveGlassIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Text(
                text = "公告",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        val current = feed
        when {
            current == null -> {
                Text(
                    text = "正在获取公告…",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }

            current.items.isEmpty() && current.error != null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "公告获取失败",
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = current.error,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(10.dp))
                        NieveGlassChip(
                            text = "重新加载",
                            selected = false,
                            onClick = { retry++ },
                        )
                    }
                }
            }

            current.items.isEmpty() -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "暂无公告",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.items.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = item.title,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                )
                                if (item.date.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.date,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (item.content.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = item.content,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        lineHeight = 22.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
