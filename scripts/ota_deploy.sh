#!/bin/bash
# ota_deploy.sh (GitHub Releases版)
# 使用法: bash scripts/ota_deploy.sh <versionName> <releaseNotes> [--dry-run] [--skip-build] [--skip-adb]
# 例: bash scripts/ota_deploy.sh 1.1.8 "バグ修正内容"
# 例: bash scripts/ota_deploy.sh 1.1.8 "バグ修正内容" --dry-run
set -e

PROJECT_DIR="/home/ysuzuki/programs/Claude_codings/coding/speedtest-android"
ADB="powershell.exe -Command C:\\tools\\platform-tools\\adb.exe"

# ADBデバイス（5555を先に試してダメなら39665）
find_device() {
    for port in 5555 39665; do
        if powershell.exe -Command "C:\tools\platform-tools\adb.exe -s 100.64.220.111:${port} get-state" 2>/dev/null | grep -q "device"; then
            echo "100.64.220.111:${port}"
            return
        fi
    done
    echo ""
}

# GitHub設定
GH_REPO="yudaipe/speedtest-android-releases"

# 引数解析
VERSION_NAME=""
RELEASE_NOTES=""
SKIP_BUILD=false
SKIP_ADB=false
DRY_RUN=false

# 位置引数と名前付き引数を分けて処理
POSITIONAL=()
for arg in "$@"; do
    case $arg in
        --skip-build) SKIP_BUILD=true ;;
        --skip-adb)   SKIP_ADB=true ;;
        --dry-run)    DRY_RUN=true ;;
        *) POSITIONAL+=("$arg") ;;
    esac
done

VERSION_NAME="${POSITIONAL[0]:-}"
RELEASE_NOTES="${POSITIONAL[1]:-}"

if [[ -z "$VERSION_NAME" || -z "$RELEASE_NOTES" ]]; then
    echo "使用法: bash scripts/ota_deploy.sh <versionName> <releaseNotes> [--dry-run] [--skip-build] [--skip-adb]"
    echo "例: bash scripts/ota_deploy.sh 1.1.8 \"バグ修正内容\""
    echo "例: bash scripts/ota_deploy.sh 1.1.8 \"バグ修正内容\" --dry-run"
    exit 1
fi

if $DRY_RUN; then
    echo "=== [DRY RUN MODE] 実際の操作は行いません ==="
fi

cd "$PROJECT_DIR"

# ===== Step 0: build.gradle.kts のバージョン更新 =====
echo ""
echo "=== [0/4] バージョン更新: versionName=${VERSION_NAME} ==="

CURRENT_CODE=$(grep 'versionCode' "$PROJECT_DIR/app/build.gradle.kts" | grep -oP '\d+' | head -1)
NEW_CODE=$((CURRENT_CODE + 1))

echo "  versionCode: ${CURRENT_CODE} → ${NEW_CODE}"
echo "  versionName: → ${VERSION_NAME}"

if $DRY_RUN; then
    echo "  [DRY] sed -i versionName → \"${VERSION_NAME}\""
    echo "  [DRY] sed -i versionCode → ${NEW_CODE}"
else
    sed -i "s/versionName = \"[^\"]*\"/versionName = \"${VERSION_NAME}\"/" \
        "$PROJECT_DIR/app/build.gradle.kts"
    sed -i "s/versionCode = [0-9]*/versionCode = ${NEW_CODE}/" \
        "$PROJECT_DIR/app/build.gradle.kts"
    echo "  ✓ build.gradle.kts 更新完了"
fi

# ===== Step 1: ビルド =====
echo ""
if $SKIP_BUILD; then
    echo "=== [1/4] ビルドスキップ ==="
else
    echo "=== [1/4] assembleRelease ==="
    if $DRY_RUN; then
        echo "  [DRY] JAVA_HOME=/home/ysuzuki/jdk17 ./gradlew assembleRelease"
    else
        JAVA_HOME=/home/ysuzuki/jdk17 ./gradlew assembleRelease
        echo "  ✓ ビルド完了"
    fi
fi

# ===== Step 2: APKパス確認 =====
APK_SRC="$PROJECT_DIR/app/build/outputs/apk/release/speed_test_monitor-v${VERSION_NAME}.apk"
TAG="v${VERSION_NAME}"

echo ""
echo "=== [2/4] APK確認 ==="
echo "  APK: $APK_SRC"

if ! $DRY_RUN && ! $SKIP_BUILD; then
    if [[ ! -f "$APK_SRC" ]]; then
        echo "ERROR: APKが見つかりません: $APK_SRC"
        exit 1
    fi
    echo "  ✓ APK存在確認"
elif $DRY_RUN; then
    echo "  [DRY] APK存在確認スキップ"
fi

# ===== Step 3: git commit + tag + push =====
echo ""
echo "=== [3/4] git commit + tag + push ==="

if $DRY_RUN; then
    echo "  [DRY] git add app/build.gradle.kts"
    echo "  [DRY] git commit -m \"release: v${VERSION_NAME} — ${RELEASE_NOTES}\""
    echo "  [DRY] git tag \"v${VERSION_NAME}\""
    echo "  [DRY] git push origin master"
    echo "  [DRY] git push origin \"v${VERSION_NAME}\""
else
    git add app/build.gradle.kts
    git commit -m "release: v${VERSION_NAME} — ${RELEASE_NOTES}"
    git tag "v${VERSION_NAME}"
    git push origin master
    git push origin "v${VERSION_NAME}"
    echo "  ✓ git push 完了 (tag: v${VERSION_NAME})"
fi

# ===== Step 4: GitHub Release作成 + アセットアップロード =====
echo ""
echo "=== [4a/4] GitHub Release: $TAG ==="

APK_URL="https://github.com/${GH_REPO}/releases/download/${TAG}/speed_test_monitor-v${VERSION_NAME}.apk"

# version.json生成
cat > /tmp/version.json << JSONEOF
{
  "version_code": ${NEW_CODE},
  "version_name": "${VERSION_NAME}",
  "apk_url": "${APK_URL}"
}
JSONEOF

echo "  version.json:"
cat /tmp/version.json

if $DRY_RUN; then
    echo "  [DRY] gh release delete \"$TAG\" --repo \"$GH_REPO\" --yes"
    echo "  [DRY] gh release create \"$TAG\" --repo \"$GH_REPO\" --title \"v${VERSION_NAME}\" --notes \"${RELEASE_NOTES}\" <APK> version.json"
else
    # 既存タグ・リリース削除（再デプロイ対応）
    gh release delete "$TAG" --repo "$GH_REPO" --yes 2>/dev/null || true
    gh api "repos/${GH_REPO}/git/refs/tags/${TAG}" -X DELETE 2>/dev/null || true

    # リリース作成 + APK + version.json アップロード
    gh release create "$TAG" \
        --repo "$GH_REPO" \
        --title "v${VERSION_NAME}" \
        --notes "${RELEASE_NOTES}" \
        "$APK_SRC" \
        /tmp/version.json

    echo "  ✓ Release $TAG 完了"
    echo "  APK URL: $APK_URL"
    echo "  version.json URL: https://github.com/${GH_REPO}/releases/latest/download/version.json"
fi

# ===== Step 4b: adb install =====
echo ""
echo "=== [4b/4] adb install ==="
if $SKIP_ADB; then
    echo "  スキップ (--skip-adb)"
elif $DRY_RUN; then
    echo "  [DRY] powershell.exe -Command \"C:\\tools\\platform-tools\\adb.exe -s <DEVICE> install -r <APK>\""
else
    DEVICE=$(find_device)
    if [[ -z "$DEVICE" ]]; then
        echo "  ⚠️  デバイス未接続 → スキップ"
    else
        powershell.exe -Command "C:\tools\platform-tools\adb.exe -s ${DEVICE} install -r $(wslpath -w "${APK_SRC}")"
        echo "  ✓ インストール完了 ($DEVICE)"
    fi
fi

echo ""
echo "=== Deploy complete ==="
if $DRY_RUN; then
    echo "=== (DRY RUN — 実際の操作は行いませんでした) ==="
fi
