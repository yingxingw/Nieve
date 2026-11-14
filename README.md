# Nieve

**网盘解析下载 + 开屏广告自动跳过，装进同一套液态玻璃界面。**

- 应用名：Nieve
- 开发者：酷安初雪
- 版本：2.0.1

Nieve 是一个独立项目：视觉语言借鉴 [WeaveMask](https://github.com/Seyud/WeaveMask) 的 Miuix 设计，
网盘 / 视频解析下载能力借鉴 [YunX（云析）](https://github.com/CYQawa/YunX)，
开屏广告自动跳过能力借鉴 [SKIP](https://github.com/GuoXiCheng/SKIP)，
并在 Nieve 内重新组织成一套统一的壳：统一入口、统一主题、统一底栏、统一设置。

## 功能

### 解析与下载
- 网盘分享链接识别 + 自动匹配提取码：夸克 / UC / 迅雷 / 百度 / 139 / 123 / 115 / 光鸭 / 蓝奏云 / 蓝奏云优享版
- 视频 / 图集解析：抖音 / 小红书 / 快手 / 微博 / AcFun / 哔哩哔哩 / Pixiv
- 内置分片并发下载：Range 分片、断点续传、HLS / DASH、图片 / 视频 / 音频在线预览
- 多网盘账号管理（Room 持久化）、深链接 / 系统分享 / 磁力直链接入
- 应用内语言切换、HTTP 代理、下载目录自定义、诊断日志

### 自动跳过
- 无障碍服务自动点击开屏广告「跳过」按钮（规则驱动 + 严格模式）
- 云端规则同步、应用白名单、跳过检视（悬浮窗抓取节点树）
- 保活引导、跳过日志、白名单、设置、教程均在 Nieve 壳内渲染，不再弹出旧 Activity

### 界面
- 液态玻璃外壳：浮动底栏、玻璃卡片、玻璃进度条、自绘分段导航
- Nieve 壳内页面：首页 / 解析 / 跳过 / 设置，四个入口常驻底栏
- 主题模式、Monet 取色、毛玻璃 BarBlur、BgEffect 动态背景着色

## 开源与许可

- 源码仓库：<https://github.com/yingxingw/Nieve>
- 更新通道（应用内「检查更新」数据源）：<https://github.com/yingxingw/nieve-update>
- 许可证：GNU AGPL-3.0（见 `LICENSE`），同时履行上游 YunX（AGPL-3.0）/ SKIP（GPL-3.0）/
  WeaveMask（GPL-3.0）的许可证义务
- 附加约定：见 `EXTRA_TERMS.md`（核心功能 ≥90% 免费、改编版必须开源、保留署名）

本仓库只提交 Nieve 这一个项目的工程源码，不附带上游项目的完整仓库、历史或 Release。

## 工程结构

| 模块 | 职责 | 许可证 |
|---|---|---|
| `:yunx`（APK 模块） | Nieve 主工程：壳、解析下载、账号、下载器、设置 | AGPL-3.0 |
| `:skip` | 自动跳过服务、规则同步、白名单 | GPL-3.0 |
| `:weavekit` | UI 组件、主题、动效 | GPL-3.0 |

> `@HiltAndroidApp` 必须位于 application 模块，且部分 internal 可见性跨模块受限，
> 所以入口（`YunXApp` / `MainActivity`）与 APK 产出都在 `:yunx` 模块内。

技术栈：AGP 9.4.1（内置 Kotlin）/ Kotlin 2.4.20 / KSP 2.3.12 / Compose BOM 2026.08.00 /
Material3 1.5.0-alpha18 / Miuix 0.9.4 / Hilt 2.60.1 / Room 2.8.5 / WorkManager 2.12。

## 集成说明

1. YunX 的入口自检要求 Application 类名以 `com.yunx.app.YunXApp` 结尾、启动 Activity 为
   `com.yunx.app.MainActivity`；Nieve 保留这两个类名，`applicationId` 改为 `com.nieve.app`，
   namespace 仍为 `com.yunx.app`。
2. SKIP 的 FileProvider authority 改为 `${applicationId}.skip.fileprovider`；
   `MyApp` 改为 `object MyApp.bootstrap(app)`，由 `YunXApp.onCreate` 调用。
3. 资源跨模块重名已加前缀去重（`skip_*` / `wk_*`）。
4. 已移除上游线上内容：YunX 公告、赞赏码、仓库 / QQ 群入口、应用内更新检查，
   SKIP 的自动更新与网页说明入口；保留跳广告云端规则同步。

## 构建

```bash
# 需要 JDK 21 与 Android SDK（platform 37.0 / build-tools 37.0.0）
./gradlew :yunx:assembleDebug
# 产物：yunx/build/outputs/apk/debug/yunx-debug.apk
```

## 致谢

- [YunX（云析）](https://github.com/CYQawa/YunX) — 网盘 / 视频解析与下载
- [SKIP](https://github.com/GuoXiCheng/SKIP) — 开屏广告自动跳过
- [WeaveMask](https://github.com/Seyud/WeaveMask) — Miuix UI 与液态玻璃视觉

各源文件保留原始版权与许可证头；Nieve 的改名、壳层与合并改动注明在
`YunXApp.kt`、`MainActivity.kt`、`MyApp.kt`、`AccessibilityStateUtils.kt` 等文件。

## 免责声明

- 解析 / 下载能力仅用于个人合法内容存档，请遵守目标网盘 / 平台的服务条款。
- 自动跳过依赖 Android 无障碍服务，仅供个人使用。
