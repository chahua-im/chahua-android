# Chahua-Android

![Min SDK](https://img.shields.io/badge/minSdk-24-brightgreen)
![Target SDK](https://img.shields.io/badge/targetSdk-36-brightgreen)

[茶话](https://github.com/chahua-im/chahua)的开源 Android 客户端

[茶话](https://github.com/chahua-im/chahua)是一个即时通讯平台，本仓库实现了完整的 Android 客户端：覆盖群聊、话题、私聊、好友、表情包等核心聊天场景，提供后台消息通知、多媒体消息、消息管理等功能。

## 已实现的功能

- **聊天**：群聊、话题（Threads）、私聊（DM）、好友列表与好友验证
- **消息**：回复、编辑、撤回、删除、引用、复制链接、置顶、表态（Reactions）、保存消息、消息搜索、@ 提及
- **多媒体**：图片、视频（发送前可压缩）、语音消息、文件，支持图片/视频查看与保存
- **表情与贴纸**：内置 Emoji 面板、贴纸包订阅与收藏、自建表情包、快捷表态栏
- **群组管理**：创建群聊、群资料编辑、群头像、成员管理、邀请链接/邀请码入群、免打扰、群内媒体与文件浏览
- **界面**：深色/浅色/跟随系统、多套主题色与自定义颜色、字号调节、简中/繁中/英文、平板分屏适配
- **通知与后台**：新消息通知、常驻通知开关、忽略电池优化、实时连接状态与延迟显示


## 下载

前往 [GitHub Releases](https://github.com/chahua-im/chahua-android/releases) 下载最新版 APK。

应用内「设置 → 关于 → 检查更新」会通过 GitHub Releases 自动检测新版本。

## 服务器配置

应用默认使用官方服务器：

- 默认：`https://chahui.app/_api`


## 从源码构建

环境要求：

- Android Studio（最新稳定版）
- JDK 17+
- Android SDK（compileSdk 36，minSdk 24，targetSdk 36，支持 Android 7.0 及以上）

构建步骤：

```bash
git clone https://github.com/chahua-im/chahua-android.git
cd chahua-android
```

用 Android Studio 打开项目，等待 Gradle Sync 完成后即可运行。也可以直接用命令行构建调试包：

```bash
./gradlew assembleDebug
```

> Release 包需要自行配置签名，配置后执行 `./gradlew assembleRelease`。

## 技术栈

- **语言与构建**：Kotlin 2.2、AGP 9.2、Gradle 9.4
- **UI**：Jetpack Compose + Material 3（Compose BOM）
- **网络**：OkHttp 4.12、kotlinx.serialization
- **图片加载**：Coil 3（支持 GIF / SVG / AVIF）
- **音视频**：Media3（ExoPlayer、Transformer 视频压缩、UI）
- **存储**：DataStore Preferences（设置与会话）、文件缓存
- **其他**：Emoji2 表情选择器、Jetpack Window（平板分屏适配）、前台服务 + 通知

## 项目结构

```
app/src/main/java/net/paigu/chahua/
├── core/      # 应用启动、依赖注入、电池优化、平板布局适配
├── data/      # API 封装、数据模型、会话/设置管理、更新检查、日志
├── service/   # 后台消息服务（前台服务 + 新消息通知）
└── ui/        # Compose 界面：登录、主页、聊天、群组、媒体、设置等
```

## 许可

<!-- TODO: 确定开源协议后在此补充，例如 MIT / GPL-3.0 -->
暂未添加 LICENSE，待补充。
