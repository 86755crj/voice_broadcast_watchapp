# Voice Broadcast Watch App

国行 Galaxy Watch Ultra (SM-L7050) 自研口播播放器。消费 VPS 47.115.56.9 上的 RSS feed。

## 复古杂志风配色

- ParchmentBeige `#F5F1E8` 米白背景
- WineRed `#8B2635` 主按钮 / 顶栏
- WarmOrange `#E8833A` 进度环激活色
- DarkCoffee `#3D2817` 正文

## 编译

```bash
source ~/.config/voice_broadcast_watchapp/env.sh
./gradlew assembleDebug
./install.sh   # adb 装到手表
```

## 项目结构

- `app/src/main/kotlin/com/crj/voicebroadcast/` Kotlin 源码
  - `MainActivity.kt` 单 Activity 三屏切换
  - `ui/` Home / Category / Player 三屏 + theme
  - `data/` Room + RSS 解析 + Repository
  - `playback/PlayerService.kt` Media3 ExoPlayer + MediaSession
  - `work/SyncWorker.kt` 每日 06:30 后台 sync

## ABI

Universal APK，包含 armeabi-v7a / arm64-v8a / x86 / x86_64。手表只支持 v7a。
