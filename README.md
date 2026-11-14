# Nieve

由初雪独立开发与调试的 Android 多功能工具。

- 应用名：Nieve
- 开发者：酷安初雪
- 版本：3.1.2（versionCode 312）
- 源码仓库：<https://github.com/yingxingw/Nieve>
- 安装包：`releases/Nieve-3.1.2.apk`

Nieve 围绕日常使用和 Android 深度折腾持续迭代，把常用的小工具收进同一个轻量界面，不接入广告 SDK，不做账号系统，也不上传个人数据。

## 功能

### 工具中心
- 修改步数：登录 Zepp / 小米运动账号后调整并同步步数
- 七猫小说：榜单、搜索、在线阅读、整本下载
- 影视大全：影片检索、详情与播放
- VIP 解析：内置小T（腾讯视频）、小Q（爱奇艺）、小B（哔哩哔哩）、小Y（优酷）桌面站，可缩放、收藏、切换线路
- 音乐：搜索、试听与下载，覆盖酷狗音乐、网易云音乐、QQ 音乐
- 网页源码抓取：输入网址查看页面源码
- 随机视频：随机刷取短视频内容

### 解析与下载
- 网盘分享链接识别与提取码匹配：夸克 / UC / 迅雷 / 百度 / 139 / 123 / 115 / 光鸭 / 蓝奏云 / 蓝奏云优享版
- 视频 / 图集链接解析：抖音 / 小红书 / 快手 / 微博 / AcFun / 哔哩哔哩 / Pixiv
- 分片并发下载、断点续传、HLS / DASH、图片 / 视频 / 音频在线预览
- 多网盘账号管理、深链接 / 系统分享 / 磁力直链接入

### 自动跳过
- 无障碍服务自动点击开屏广告「跳过」按钮
- 云端规则同步、应用白名单、跳过检视、保活引导与本地日志

### 界面
- 液态玻璃外壳：浮动底栏、玻璃卡片、自绘分段导航
- 主题模式、Monet 取色、毛玻璃 BarBlur、动态背景着色

### 远程能力
- 公告：从公告仓库读取多份公告，应用内「公告」页按顺序展示
- 一键禁用：远程配置 `disabled=true` 后，启动即显示停用页
- 一键强制更新：远程配置 `forceUpdate=true` 或 `minVersionCode` 高于当前版本时，启动阻断并引导更新
- 检查更新：读取当前源码仓库根目录的 `update.json`
- 网络不可用时按正常可用处理，不会因为拉取失败把应用锁死

## 远程配置

更新 / 禁用 / 强制更新配置文件：

```text
https://raw.githubusercontent.com/yingxingw/Nieve/main/update.json
```

应用优先通过 GitHub Contents API 读取该文件，提交后立即生效；API 不可用时回退 raw 地址。

字段：

```json
{
  "versionCode": 312,
  "versionName": "3.1.2",
  "minVersionCode": 0,
  "downloadUrl": "https://raw.githubusercontent.com/yingxingw/Nieve/f09ef396e2327b8fd3e88cfeb06a129c48da1aa4/releases/Nieve-3.1.2.apk",
  "changelog": "更新内容",
  "updateDate": "2026-11-15",
  "forceUpdate": false,
  "disabled": false,
  "disabledMessage": "Nieve 暂时停止使用，请等待后续版本。"
}
```

公告文件放在旧仓库：

```text
https://raw.githubusercontent.com/yingxingw/nieve-update/main/announcements.json
```

支持多份公告：

```json
{
  "items": [
    {
      "id": "2026-11-15-release",
      "title": "Nieve 3.1.2 已发布",
      "content": "公告正文",
      "date": "2026-11-15"
    }
  ]
}
```

## 工程结构

| 模块 | 职责 | 许可证 |
|---|---|---|
| `:yunx`（APK 模块） | Nieve 主工程：壳、工具中心、解析下载、账号、下载器、设置 | AGPL-3.0 |
| `:skip` | 自动跳过服务、规则同步、白名单 | GPL-3.0 |
| `:weavekit` | UI 组件、主题、动效 | GPL-3.0 |

技术栈：AGP 9.4.1（内置 Kotlin）/ Kotlin 2.4.20 / KSP 2.3.12 / Compose BOM 2026.08.00 /
Material3 1.5.0-alpha18 / Miuix 0.9.4 / Hilt 2.60.1 / Room 2.8.5 / WorkManager 2.12。

## 构建

需要 JDK 21 与 Android SDK（platform 37.0 / build-tools 37.0.0）。

```bash
./gradlew :yunx:assembleDebug
# 产物：yunx/build/outputs/apk/debug/yunx-debug.apk
```

七猫接口的请求签名不写入公开仓库。本地构建时在 `local.properties` 增加：

```properties
qimao.secret=你的本地签名值
qimao.aesKey=你的本地内容解密值
```

Gradle 会将它写入 `BuildConfig.QIMAO_SECRET` / `BuildConfig.QIMAO_AES_KEY`；未配置时七猫相关功能会提示缺少本地配置，其他功能不受影响。

## 开源许可与致谢

- 许可证：GNU AGPL-3.0（见 `LICENSE`）
- 附加约定：见 `EXTRA_TERMS.md`
- 本项目使用或参考的开源组件按各自许可证保留署名：
  - YunX（云析）— AGPL-3.0
  - SKIP — GPL-3.0
  - WeaveMask — GPL-3.0

各源文件保留原始版权与许可证头；Nieve 的壳层、工具中心与合并改动注明在
`YunXApp.kt`、`MainActivity.kt`、`MyApp.kt`、`AccessibilityStateUtils.kt` 等文件。

## 免责声明

- 解析 / 下载能力仅用于个人合法内容存档，请遵守目标网盘 / 平台的服务条款。
- 自动跳过依赖 Android 无障碍服务，仅供个人使用。
- VIP 解析使用第三方线路，应用本身不存储、不托管任何视频内容。
