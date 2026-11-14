# 功能映射表（Nieve：YunX / SKIP / WeaveMask）

> 「全部功能」的落实位置。YunX 与 SKIP 的源码以库模块整体并入（功能 1:1 保留），
> 应用壳使用 WeaveMask 的 Miuix UI。应用名 **Nieve**，开发者 **酷安初雪**。

## YunX 云析（`:yunx`，代码包 `com.yunx.app`，applicationId `com.nieve.app`）

| 功能 | 核心实现 | Shell 入口 |
|---|---|---|
| 分享链接识别 + 提取码匹配（夸克/UC/迅雷/百度/139/123/115/光鸭/蓝奏云/优享版） | `data/network/ShareLinkParser`、`QuarkApi`、`UCApi`、`XunleiApi`、`BaiduApi`、`C139Api`、`Pan123Api`、`Pan115Api`、`GuangYaApi`、`LanzouApi`、`ILanzouApi`、`StarSeaApi` | 云析页 → 解析 Tab（分享/深链接自动进入） |
| 视频/图集解析（抖音/小红书/快手/微博/AcFun/B站/Pixiv） | `data/network/video/*`、`ui/resolve/*`、`ui/preview/*` | 云析页 → 解析 Tab |
| 高速下载：Range 分片并发 + 断点续传 | `data/download/{DownloadManager,ChunkDownloader,HttpRangePolicy,DownloadSaver,DownloadService}` | 云析页 → 下载 Tab |
| HLS / DASH 下载与音轨合成 | `data/download/{HlsDownloader,DashMuxer,AudioExtractor}` | 同上 |
| 磁力/直链下载（Gopeed 引擎 + 内置引擎） | `data/gopeed/GopeedEngine`、`data/download/MagnetLink` | 下载 Tab「添加下载任务」 |
| 图片/视频/音频在线预览、实况照片合成 | `ui/preview/*`、`data/download/MotionPhotoWriter`、media3/ExoPlayer | 解析结果预览 |
| 多网盘账号管理（登录/切换/加密备份） | `data/db/*AccountEntity(Dao)`、`ui/login/*`、`data/backup/{AuthBackupManager,AuthCrypto}` | 云析页 → 账号 Tab |
| 115/123 加密与设备指纹 | `data/network/{Pan115Crypto,Pan123DeviceId,XunleiDeviceFingerprint}` | 随登录/请求自动生效 |
| 下载通知、前台服务、断点恢复 | `data/download/DownloadService`、`DownloadPlatform` | 自动 |
| 应用内语言、深链接/系统分享/选中文本解析 | `ui/theme/LocaleController`、`util/DeepLinks`、`MainActivity.handleDeepLink` | 自动（MainActivity 入口） |
| HTTP 代理、诊断日志、下载调试日志 | `data/network/HttpClients.setProxy`、`util/DiagnosticLog`、`data/download/DownloadDebugLog` | 云析页 → 设置 Tab |
| 下载引擎管理（内核导入/自愈）、下载目录策略 | `data/gopeed/KernelProvisioner`、`data/download/DownloadPathPolicy` | 设置 Tab |
| 崩溃上报（含防二次打包探针） | `crash/*`、`util/ArchiveProbe` | 自动（LaunchGate/入口自检） |
| 链接唤起（activity-alias 图标切换） | `util/LinkOpener`、清单 `activity-alias`×12 | 深链接设置 |

## SKIP 自动跳过（`:skip`，包 `com.android.skip`）

| 功能 | 核心实现 | Shell 入口 |
|---|---|---|
| 无障碍服务自动点击「跳过」 | `service/MyAccessibilityService` + `data/config/ConfigLoadRepository.getTargetRect` | 跳过 Tab → 服务开关 |
| 自定义规则云端订阅/同步 | `data/config/*`、`data/SyncWorker`（WorkManager + Hilt） | 自动 + 设置 |
| 严格模式 / 跳过提示 | `ui/settings/strict/*`、`ui/settings/tip/*` | 跳过设置 |
| 应用白名单（含搜索、系统应用显隐） | `ui/whitelist/*`（壳内 Miuix 列表，不再打开 WhiteListActivity） | 跳过 Tab / 设置页 |
| 节点检视（悬浮窗抓取、记录、截图回看） | `service/{InspectService,FloatingWindowService}`、`ui/inspect/*`、`ui/record/*` | 跳过 Tab → 检视 |
| 保活引导（通知栏常驻、后台锁定） | `ui/alive/*`、`service/MyForegroundService` | 跳过 Tab → 壳内保活页 |
| 跳过日志 | `ui/settings/log/*` | 跳过设置 → 壳内日志页 |
| 主题切换（深浅色） | 已并入 Shell 全局外观 `ShellSettingsStore` / `WeaveMagiskTheme` | 设置 → 主题模式 |
| 教程 / 免责声明 | `ui/main/{tutorial,disclaimer}` | 跳过 Tab（首次进入弹出） |
| 最近任务隐藏开关 | `ui/settings/recent/*` | 跳过设置 |

> SKIP 主界面、跳过设置、跳过日志、白名单、保活均已改为 `yunx/.../shell/SkipTab.kt` 内的 Miuix 页面；独立 `SettingsActivity` / `WhiteListActivity` 等仅保留为兼容入口，并在 manifest 中补齐 AppCompat 主题以防启动崩溃。

## WeaveMask UI（`:weavekit`，包 `io.github.seyud.weave.*`）

| UI 能力 | 实现 | 使用位置 |
|---|---|---|
| Miuix 主题系统（6 种模式 + Monet 取色/仿色） | `ui/theme/{WeaveMagiskTheme,Theme,EffectiveSystemMonetColors,MonetPresetPalette}` | 全局 |
| 浮动底栏（液态玻璃 / 实时模糊 Backdrop） | `ui/component/FloatingBottomBar` + `ui/util/BarBlur` + `ui/component/liquid/*` | Shell 底栏 |
| Miuix 组件（Card/SmallTitle/Preference/Dialog） | `ui/component/*`、`top.yukonga.miuix.kmp.*` | 首页 Hub、设置页 |
| BgEffect 动态背景着色（OS2/OS3 shader） | `ui/about/effect/*`、`ui/about/blend/*` | 首页 Hub 背景 |
| 搜索浮层 / Markdown 渲染 / 拖拽手势 | `ui/component/{SearchOverlay,MarkdownText,HtmlText}`、`ui/modifier/*`、`ui/animation/*` | 备用 UI 能力 |

## 合并改动清单（相对上游）

| 文件 | 改动 | 原因 |
|---|---|---|
| `yunx/…/YunXApp.kt` | 加 `@HiltAndroidApp`、WorkManager Provider、调用 SKIP/Weavekit 引导 | 单 Application 合并 |
| `yunx/…/MainActivity.kt` | setContent 改为 NieveShell（保留全部原 intent/深链接逻辑） | 统一 UI 壳 |
| `yunx/…/shell/*` | 新增（首页 Hub / 跳过 Tab / 设置 / Shell 状态） | WeaveMask 风格壳 |
| `skip/…/MyApp.kt` | Application → `object MyApp.bootstrap()` | 由合并入口调用 |
| `skip/…/AccessibilityStateUtils.kt` | 服务名按 packageName 动态匹配 | applicationId 合并 |
| `skip/…/AboutActivity.kt`、`InspectRecordActivity.kt` | FileProvider authority 加 `.skip.` | 避免与 YunX 冲突 |
| `skip/…/AppListColumn.kt` | coil 1.x → 2.x API | Compose 版本对齐 |
| 清单/资源 | 前缀去重（`skip_*` / `wk_*`），见 merge_report.txt | 单 APK 资源合并 |
