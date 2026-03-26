#!/bin/bash
# ota_deploy.sh (GitHub Releases版)
# 使用法: bash scripts/ota_deploy.sh [--skip-build] [--skip-adb]
set -e

PROJECT_DIR="/home/ysuzuki/programs/Claude_codings/coding/speedtest-android"
ADB="/mnt/c/Android/Sdk/platform-tools/adb.exe"

# ADBデバイス（5555を先に試してダメなら39665）
find_device() {
    for port in 5555 39665; do
        if "$ADB" -s "100.64.220.111:${port}" get-state 2>/dev/null | grep -q "device"; then
            echo "100.64.220.111:${port}"
            return
        fi
    done
    echo ""
}

# GitHub設定
GH_REPO="yudaipe/speedtest-android-releases"

# フラグ
SKIP_BUILD=false
SKIP_ADB=false
for arg in "$@"; do
    case $arg in
        --skip-build) SKIP_BUILD=true ;;
        --skip-adb) SKIP_ADB=true ;;
    esac
done

cd "$PROJECT_DIR"

# ===== Step 1: ビルド =====
if $SKIP_BUILD; then
    echo "=== [1/3] ビルドスキップ ==="
else
    echo "=== [1/3] assembleDebug ==="
    JAVA_HOME=/home/ysuzuki/jdk17 ./gradlew assembleDebug
fi

# ===== Step 2: バージョン取得 =====
VERSION_CODE=$(grep 'versionCode' "$PROJECT_DIR/app/build.gradle.kts" \
    | grep -oP '\d+' | head -1 || echo "1")
VERSION_NAME=$(grep 'versionName' "$PROJECT_DIR/app/build.gradle.kts" \
    | grep -oP '"[^"]+"' | head -1 | tr -d '"' || echo "1.0.0")
APK_SRC="$PROJECT_DIR/app/build/outputs/apk/debug/speed_test_monitor-v${VERSION_NAME}.apk"
TAG="v${VERSION_NAME}"

echo "  versionCode=$VERSION_CODE, versionName=$VERSION_NAME, tag=$TAG"

if [[ ! -f "$APK_SRC" ]]; then
    echo "ERROR: APKが見つかりません: $APK_SRC"
    exit 1
fi

# ===== Step 3: GitHub Release作成 + アセットアップロード =====
echo ""
echo "=== [2/3] GitHub Release: $TAG ==="

APK_URL="https://github.com/${GH_REPO}/releases/download/${TAG}/speed_test_monitor-v${VERSION_NAME}.apk"

# version.json生成
cat > /tmp/version.json << JSONEOF
{
  "version_code": ${VERSION_CODE},
  "version_name": "${VERSION_NAME}",
  "apk_url": "${APK_URL}",
  "release_notes": "v${VERSION_NAME} (${VERSION_CODE})"
}
JSONEOF

echo "  version.json:"
cat /tmp/version.json

# 既存タグ・リリース削除（再デプロイ対応）
gh release delete "$TAG" --repo "$GH_REPO" --yes 2>/dev/null || true
gh api "repos/${GH_REPO}/git/refs/tags/${TAG}" -X DELETE 2>/dev/null || true

# リリース作成 + APK + version.json アップロード
gh release create "$TAG" \
    --repo "$GH_REPO" \
    --title "v${VERSION_NAME}" \
    --notes "versionCode=${VERSION_CODE}" \
    "$APK_SRC" \
    /tmp/version.json

echo "  ✓ Release $TAG 完了"
echo "  APK URL: $APK_URL"
echo "  version.json URL: https://github.com/${GH_REPO}/releases/latest/download/version.json"

# ===== Step 4: adb install =====
echo ""
echo "=== [3/3] adb install ==="
if $SKIP_ADB; then
    echo "  スキップ (--skip-adb)"
else
    DEVICE=$(find_device)
    if [[ -z "$DEVICE" ]]; then
        echo "  ⚠️  デバイス未接続 → スキップ"
    else
        "$ADB" -s "$DEVICE" install -r "$(wslpath -w "$APK_SRC")"
        echo "  ✓ インストール完了 ($DEVICE)"
    fi
fi

echo ""
echo "=== Deploy complete ==="
