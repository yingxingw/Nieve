# Nieve

**Nieve —— 网盘解析下载 · 开屏广告自动跳过 三合一 Android 应用**

- **应用名**：Nieve
- **开发者**：酷安初雪
- **版本**：2.0.1

以 [WeaveMask](https://github.com/Seyud/WeaveMask) 的 Miuix UI 为壳，完整集成
[YunX（云析）](https://github.com/CYQawa/YunX) 的网盘 / 视频解析下载与
[SKIP](https://github.com/GuoXiCheng/SKIP) 的开屏广告自动跳过全部功能。

## 开源与许可

- 源码仓库：<https://github.com/yingxingw/Nieve>
- 更新通道（应用内「检查更新」数据源）：<https://github.com/yingxingw/nieve-update>
- 许可证：整体 GNU AGPL-3.0（见 `LICENSE`），并遵守上游 YunX（AGPL-3.0）/ SKIP（GPL-3.0）/
  WeaveMask（GPL-3.0）等许可证
- 附加约定：见 `EXTRA_TERMS.md`（核心功能 ≥90% 免费、改编版必须开源、保留署名）

```
┌──────────────────────────────────────────────────┐
│  Nieve Shell（Miuix UI：主题 / 毛玻璃 / 浮动底栏）        │
│  ┌──────────┐ ┌──────────────┐ ┌──────────────┐  │
│  │ 首页 Hub  │ │ 解析（全屏）   │ │ 跳过 / 设置   │  │
│  └──────────┘ └──────────────┘ └──────────────┘  │
│        ┌───────────────┐  ┌───────────────┐      │
│        │ :yunx  YunX   │  │ :skip  SKIP   │      │
│        │ 解析+下载 全功能 │  │ 无障碍跳过 全功能│      │
│        └───────────────┘  └───────────────┘      │
│  ┌────────────────────────────────────────────┐  │
│  │ :weavekit  WeaveMask UI kit（GPL-3.0）      │  │
│  └────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

## 功能

### 解析下载（YunX 内核，AGPL-3.0）
- 网盘分享链接识别 + 自动匹配提取码：夸克 / UC / 迅雷 / 百度 / 139 / 123 / 115 / 光鸭 / 蓝奏云 / 蓝奏云优享版
- 视频/图集解析：抖音 / 小红书 / 快手 / 微博 / AcFun / 哔哩哔哩 / Pixiv
- Range 分片并发下载、断点续传、HLS/DASH、图片/视频/音频在线预览、实况通知下载进度
- 多网盘账号管理（Room 持久化）、Gopeed / 内置双下载引擎、深链接/系统分享/磁力直链接入
- 应用内语言切换、HTTP 代理、下载目录自定义、诊断日志

### 自动跳过（SKIP 全功能，GPL-3.0）
- 无障碍服务自动点击开屏广告「跳过」按钮（规则驱动 + 严格模式）
- 自定义规则订阅（云端规则同步）、应用白名单、跳过检视（悬浮窗抓取节点树）
- 保活引导（壳内 Miuix 页面：通知栏常驻/后台锁定）、跳过日志（壳内页面）、应用白名单（壳内列表）、跳过设置（壳内页面）、教程与免责声明
- 跳过主页 / 设置 / 日志 / 白名单 / 保活均在 Nieve Shell 内渲染，不再弹出 SKIP 自带 Activity 的旧界面

### 界面（WeaveMask UI kit，GPL-3.0）
- Miuix 组件体系：浮动底栏（液态玻璃/实时模糊）、Miuix 主题（6 种主题模式 + Monet 取色）
- 毛玻璃 BarBlur、BgEffect 动态背景着色、组合 Backdrop 效果

## 工程结构（Gradle 多模块）

| 模块 | 内容 | 来源 | 许可证 |
|---|---|---|---|
| `:yunx`（APK 模块） | YunX 全源码 + 合并后的 Application/MainActivity + Nieve Shell | CYQawa/YunX | AGPL-3.0 |
| `:skip` | SKIP 全源码（服务/检视/白名单/规则同步） | GuoXiCheng/SKIP | GPL-3.0 |
| `:weavekit` | WeaveMask UI kit（theme/component/navigation3/util/effect） | Seyud/WeaveMask | GPL-3.0 |

> 注：`@HiltAndroidApp` 要求位于 application 模块，且 YunX 的 internal 可见性跨模块受限，
> 因此合并入口（YunXApp/MainActivity）与 APK 产出都在 `:yunx` 模块内。

技术栈对齐（详见 `gradle/libs.versions.toml`）：AGP 9.4.1（内置 Kotlin）/ Kotlin 2.4.20 / KSP 2.3.12 /
Compose BOM 2026.08.00 + Material3 1.5.0-alpha18 / Miuix 0.9.4 / Hilt 2.60.1 / Room 2.8.5 / WorkManager 2.12。

## 关键集成说明

1. **应用入口是负载敏感的**：YunX 的入口自检（`entryMismatch`，见 `LaunchGate`）要求
   Application 类名以 `com.yunx.app.YunXApp` 结尾、启动 Activity 为 `com.yunx.app.MainActivity`。
   合并版的 `YunXApp`（Hilt + WorkManager + SKIP/WeaveKit 引导）与 `MainActivity`
   （Nieve Shell）保持了这两个类名，YunX 原有的防篡改探针（`ArchiveProbe`，检测注入/二次打包）
   原样保留且正常通过。
2. **applicationId = `com.nieve.app`**（namespace 仍为 `com.yunx.app`）：对外包名与
   代码包名分离，类名后缀不变，YunX 的 `entryMismatch` 自检与 FileProvider
   （`${applicationId}.fileprovider` / `.launchgate`）均正常；如需再改名只改
   `yunx/build.gradle.kts` 的 `applicationId`，不要动 `namespace`。
3. **资源合并**：跨模块重名资源已加前缀去重（SKIP 的 `skip_*`、weavekit 的 `wk_*`），
   报告见 `merge_report.txt`（构建目录生成）。
4. **SKIP 的 FileProvider** authority 改为 `${applicationId}.skip.fileprovider` 避免与
   YunX 的 `${applicationId}.fileprovider` 冲突；`AccessibilityStateUtils` 的服务名匹配
   已改为按 `packageName` 动态拼接。
5. **SKIP 原 `MyApp`（Application）改为 `object MyApp.bootstrap(app)`**，由合并后的
   `YunXApp.onCreate` 调用；WorkManager 的 `Configuration.Provider` 由 `YunXApp` 实现
   （HiltWorkerFactory 注入），行为与 SKIP 原来一致。
6. **不包含上游线上内容**：已移除 YunX 公告、开发者赞赏码、上游仓库/QQ 群入口、应用内更新检查，
   以及 SKIP 的自动更新与网页说明入口；`UpdateChecker` 仅保留镜像地址与内核 Release 拉取能力。

## 品牌

| 项目 | 值 |
|---|---|
| 应用名 | Nieve |
| 开发者 | 酷安初雪 |
| 包名 / applicationId | `com.nieve.app`（namespace 仍为 `com.yunx.app`，类名不变以通过 YunX 入口自检） |
| 应用图标 | 雪晶 N（经典位图 `icon` / `icon2` + 自适应 `ic_launcher_new` 双层资源） |
| 版本名 | 2.0.0 |

界面文案已全量替换为 Nieve（含简体中文、繁體中文、English、日本語、Français 五种语言）；
`yunx://` 深链接协议、`Pictures/YunX` 相册目录等**功能性标识保持原样**，不影响既有数据与外部调用。

## 构建

```bash
# 需要 JDK 21（AGP 9）与 Android SDK（platform 37.0 / build-tools 37.0.0）
./gradlew :yunx:assembleDebug
# 产物：yunx/build/outputs/apk/debug/yunx-debug.apk
```

✅ 已在本仓库环境实际编译通过（BUILD SUCCESSFUL，2026-11-14，JDK 21）；产物已复制为
`Nieve-2.0.0-debug.apk`（42.8 MB，application-label = Nieve，package = com.nieve.app）。合并清单验证：
launchable-activity = `com.yunx.app.MainActivity`，Application = `com.yunx.app.YunXApp`，
SKIP 无障碍服务 / 悬浮窗 / YunX 下载服务 / LaunchGate 等组件全部合并就位。

## 许可证与版权

本仓库是三个 GPL/AGPL 开源项目的衍生合并作品，**整体按 AGPL-3.0 发布**（GPL-3.0 与
AGPL-3.0 可组合，组合作品按 AGPL-3.0 条款分发）：

- WeaveMask UI kit — Copyright (C) Seyud, GPL-3.0（`LICENSES/GPL-3.0.txt`）
- YunX 云析 — Copyright (C) 2026 CYQawa, AGPL-3.0（`LICENSE`）
- SKIP — Copyright GuoXiCheng, GPL-3.0（`LICENSES/GPL-3.0.txt`）

各源文件保留原始版权与许可证头；Nieve 的改名、界面壳与合并改动注明在
`YunXApp.kt`、`MainActivity.kt`、`MyApp.kt`、`AccessibilityStateUtils.kt` 等文件。
分发本作品（含网络服务）须按 AGPL-3.0 提供完整源码。

## 免责声明

- 解析/下载能力仅用于个人合法内容存档，请遵守目标网盘/平台的服务条款。
- 自动跳过依赖 Android 无障碍服务，仅供个人使用；见 SKIP 的 `DISCLAIMER.md`。
