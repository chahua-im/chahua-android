# Chahua-Android

[简体中文](README.md) | English

![Min SDK](https://img.shields.io/badge/minSdk-24-brightgreen)
![Target SDK](https://img.shields.io/badge/targetSdk-36-brightgreen)

The open-source Android client of [Chahua](https://github.com/chahua-im/chahua).

[Chahua](https://github.com/chahua-im/chahua) is an instant messaging platform. This repository implements the full Android client, covering group chats, topics, DMs, friends, sticker packs, and other core chat scenarios, with background message notifications, rich media messages, and message management.

## Implemented Features

- **Chat**: Group chats, topics (Threads), DMs, friend list and friend verification
- **Messages**: Reply, edit, recall, delete, quote, copy link, pin, reactions, save messages, message search, @ mentions
- **Rich media**: Images, videos (compressible before sending), voice messages, files; view and save images/videos
- **Emojis & stickers**: Built-in emoji picker, sticker pack subscription and favorites, custom sticker packs, quick reaction bar
- **Group management**: Create group chats, edit group info and avatar, member management, join via invite link/code, mute, browse group media and files
- **UI**: Dark/light/follow-system, multiple theme colors and custom colors, font size adjustment, Simplified Chinese / Traditional Chinese / English, tablet split-screen support
- **Notifications & background**: New-message notifications, persistent notification toggle, ignore battery optimization, live connection status and latency display

## Download

Get the latest APK from [GitHub Releases](https://github.com/chahua-im/chahua-android/releases).

The in-app "Settings → General → Check for Updates" automatically detects new versions via GitHub Releases.

## Server Configuration

The app uses the official server by default:

- Default: `https://chahui.app/_api`

## Building from Source

Requirements:

- Android Studio (latest stable)
- JDK 17+
- Android SDK (compileSdk 36, minSdk 24, targetSdk 36; supports Android 7.0 and above)

Steps:

```bash
git clone https://github.com/chahua-im/chahua-android.git
cd chahua-android
```

Open the project in Android Studio, wait for Gradle Sync to finish, and you can run it. You can also build the debug APK from the command line:

```bash
./gradlew assembleDebug
```

> Release builds require you to configure your own signing; after that, run `./gradlew assembleRelease`.

## Tech Stack

- **Language & build**: Kotlin 2.2, AGP 9.2, Gradle 9.4
- **UI**: Jetpack Compose + Material 3 (Compose BOM)
- **Networking**: OkHttp 4.12, kotlinx.serialization
- **Image loading**: Coil 3 (GIF / SVG / AVIF support)
- **Audio & video**: Media3 (ExoPlayer, Transformer video compression, UI)
- **Storage**: DataStore Preferences (settings & session), file cache
- **Other**: Emoji2 emoji picker, Jetpack Window (tablet split-screen support), foreground service + notifications

## Project Structure

```
app/src/main/java/net/paigu/chahua/
├── core/      # App startup, dependency injection, battery optimization, tablet layout
├── data/      # API wrapper, data models, session/settings management, update check, logging
├── service/   # Background messaging service (foreground service + new-message notifications)
└── ui/        # Compose UI: auth, home, chat, groups, media, settings, etc.
```

## License

GNU General Public License v3.0
