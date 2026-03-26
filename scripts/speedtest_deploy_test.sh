#!/bin/bash
# speedtest_deploy_test.sh
# ビルド → インストール → アプリ起動 → 手動テストトリガー → logcat確認 を自動化
# 使用法: bash scripts/speedtest_deploy_test.sh [--skip-build]

set -e

PROJECT_DIR="/home/ysuzuki/programs/Claude_codings/coding/speedtest-android"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
ADB="/mnt/c/Android/Sdk/platform-tools/adb.exe"
DEVICE="100.64.220.111:39241"
PACKAGE="com.shogun.speedtest"
ACTIVITY="$PACKAGE/.MainActivity"

cd "$PROJECT_DIR"

# ---- Step 1: ビルド ----
if [[ "$1" != "--skip-build" ]]; then
    echo "=== [1/5] assembleDebug ==="
    JAVA_HOME=/home/ysuzuki/jdk17 ./gradlew assembleDebug
    echo "BUILD OK: $(ls -lh "$APK" | awk '{print $5, $9}')"
else
    echo "=== [1/5] ビルドスキップ (--skip-build) ==="
fi

# ---- Step 2: インストール ----
echo ""
echo "=== [2/5] インストール → $DEVICE ==="
APK_WIN=$(wslpath -w "$APK")
"$ADB" -s "$DEVICE" install -r "$APK_WIN"

# ---- Step 3: アプリ起動 ----
echo ""
echo "=== [3/5] アプリ起動 ==="
"$ADB" -s "$DEVICE" shell am force-stop "$PACKAGE" 2>/dev/null || true
sleep 1
"$ADB" -s "$DEVICE" shell am start -n "$ACTIVITY"
sleep 3

# ---- Step 4: 手動実行トリガー（Broadcastまたはボタンタップ） ----
echo ""
echo "=== [4/5] テスト実行トリガー ==="
# WorkManagerワンショットトリガー（Broadcastで代替）
"$ADB" -s "$DEVICE" shell am broadcast \
    -a "$PACKAGE.ACTION_RUN_SPEEDTEST" \
    --receiver-foreground 2>/dev/null || true

# UIボタンタップ（メイン画面の中央付近 — 手動実行ボタン想定）
# 座標はデバイス解像度に応じて調整が必要な場合がある
echo "  → UIボタンタップ（中央）..."
"$ADB" -s "$DEVICE" shell input tap 540 960 2>/dev/null || true
sleep 2

echo "  → 計測完了まで待機（最大120秒）..."
sleep 30  # 最低限の待機（speedtest通常15〜30秒）

# ---- Step 5: logcat確認 ----
echo ""
echo "=== [5/5] logcat（SpeedtestWorker / SpeedtestExecutor） ==="
"$ADB" -s "$DEVICE" logcat -d \
    -s SpeedtestWorker:V SpeedtestExecutor:V \
    | tail -60

# ---- Step 6: スクリーンショット取得 ----
echo ""
echo "=== [6/6] スクリーンショット取得 ==="
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
SCREENSHOT_REMOTE="/sdcard/speedtest_result_${TIMESTAMP}.png"
SCREENSHOT_LOCAL="/tmp/speedtest_result_${TIMESTAMP}.png"
"$ADB" -s "$DEVICE" shell screencap -p "$SCREENSHOT_REMOTE" 2>/dev/null || true
"$ADB" -s "$DEVICE" pull "$SCREENSHOT_REMOTE" "$SCREENSHOT_LOCAL" 2>/dev/null || true
"$ADB" -s "$DEVICE" shell rm "$SCREENSHOT_REMOTE" 2>/dev/null || true
if [[ -f "$SCREENSHOT_LOCAL" ]]; then
    echo "  → スクリーンショット保存: $SCREENSHOT_LOCAL"
    # Windowsで開く（WSL2環境）
    SCREENSHOT_WIN=$(wslpath -w "$SCREENSHOT_LOCAL")
    cmd.exe /c "start $SCREENSHOT_WIN" 2>/dev/null || true
else
    echo "  → スクリーンショット取得失敗（スキップ）"
fi

echo ""
echo "=== 完了 ==="
echo "全 logcat を確認するには:"
echo "  $ADB -s $DEVICE logcat -d | grep -E '(SpeedtestExecutor|speedtest|exit|download|upload|Forms|error)'"
