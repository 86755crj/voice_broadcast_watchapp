#!/usr/bin/env bash
# Side-load APK 到手表脚本
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
  echo "[!] APK 不存在：$APK"
  echo "    先跑 ./gradlew assembleDebug"
  exit 1
fi

DEV_COUNT=$(adb devices | grep -E "device$" | wc -l | tr -d ' ')
if [ "$DEV_COUNT" -lt 1 ]; then
  echo "[!] 没有 ADB 设备。手表打开 设置 → 开发者选项 → 无线调试，然后："
  echo "    adb pair <ip:port>   # 输入配对码"
  echo "    adb connect <ip:port>"
  exit 2
fi

echo "[*] 安装 $APK ..."
adb install -r -t "$APK"
echo "[+] 启动 app"
adb shell monkey -p com.crj.voicebroadcast -c android.intent.category.LAUNCHER 1 || true
