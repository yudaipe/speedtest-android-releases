#!/bin/bash
# ota_deploy.sh
# OTAデプロイ自動化: ビルド → Google Drive APKアップロード → version.json更新 → adb install
# 使用法: bash scripts/ota_deploy.sh [--skip-build] [--skip-drive]
#
# 前提条件:
#   1. rclone が ~/.local/bin/rclone に導入済み
#   2. rclone remote "gdrive" が設定済み (初回: ~/.local/bin/rclone config)
#   3. Google Drive フォルダ DRIVE_FOLDER に version.json と APK を配置済み
#   4. adb.exe が ADB パスに存在

set -e

# ===== 設定 =====
PROJECT_DIR="/home/ysuzuki/programs/Claude_codings/coding/speedtest-android"
ADB="/mnt/c/Android/Sdk/platform-tools/adb.exe"
DEVICE="100.64.220.111:5555"
PACKAGE="com.shogun.speedtest"

# rclone設定
RCLONE="$HOME/.local/bin/rclone"
RCLONE_REMOTE="gdrive"
DRIVE_FOLDER="speedtest-ota"   # Google Drive上のフォルダ名（要変更）

# Google Drive FILE_ID（UpdateChecker.ktにハードコードされているもの）
VERSION_JSON_FILE_ID="1pP44PwqBCwoc457UMUNfUHGFA3AcshKh"

# APKパス（versionNameをbuild.gradle.ktsから動的取得）
_VER=$(grep 'versionName' "$PROJECT_DIR/app/build.gradle.kts" \
    | grep -oP '"[^"]+"' | head -1 | tr -d '"' || echo "1.0.0")
APK_SRC="$PROJECT_DIR/app/build/outputs/apk/debug/speed_test_monitor-v${_VER}.apk"

# ===== 引数処理 =====
SKIP_BUILD=false
SKIP_DRIVE=false
for arg in "$@"; do
    case $arg in
        --skip-build) SKIP_BUILD=true ;;
        --skip-drive) SKIP_DRIVE=true ;;
    esac
done

cd "$PROJECT_DIR"

# ===== Step 1: ビルド =====
if $SKIP_BUILD; then
    echo "=== [1/5] ビルドスキップ (--skip-build) ==="
else
    echo "=== [1/5] assembleDebug ==="
    JAVA_HOME=/home/ysuzuki/jdk17 ./gradlew assembleDebug
    echo "BUILD OK: $(ls -lh "$APK_SRC" | awk '{print $5, $9}')"
fi

# APK存在確認
if [[ ! -f "$APK_SRC" ]]; then
    echo "ERROR: APKが見つかりません: $APK_SRC"
    echo "先にビルドを実行するか --skip-build を外してください。"
    exit 1
fi

# ===== Step 2: versionCode取得 =====
echo ""
echo "=== [2/5] versionCode取得 ==="
VERSION_CODE=$(grep 'versionCode' "$PROJECT_DIR/app/build.gradle.kts" 2>/dev/null \
    | grep -oP '\d+' | head -1 || echo "1")
VERSION_NAME=$(grep 'versionName' "$PROJECT_DIR/app/build.gradle.kts" 2>/dev/null \
    | grep -oP '"[^"]+"' | head -1 | tr -d '"' || echo "1.0")
echo "  versionCode=$VERSION_CODE, versionName=$VERSION_NAME"

# ===== Step 3: Google Drive APKアップロード =====
if $SKIP_DRIVE; then
    echo ""
    echo "=== [3/5] Driveアップロードスキップ (--skip-drive) ==="
else
    echo ""
    echo "=== [3/5] Google Drive APKアップロード ==="

    # rclone remote確認
    if ! "$RCLONE" listremotes 2>/dev/null | grep -q "^${RCLONE_REMOTE}:"; then
        echo "ERROR: rclone remote '${RCLONE_REMOTE}' が未設定です。"
        echo "セットアップ手順:"
        echo "  1. ~/.local/bin/rclone config を実行"
        echo "  2. n (new remote) → name: gdrive → storage: drive"
        echo "  3. OAuth認証: WSL2ではURLが表示されるのでWindowsブラウザで開く"
        echo "  4. 完了後このスクリプトを再実行"
        exit 1
    fi

    # APK名にversionCodeを含めて管理しやすくする
    APK_DRIVE_NAME="speedtest-v${VERSION_CODE}.apk"

    echo "  APKをアップロード中: $APK_DRIVE_NAME"
    "$RCLONE" copyto "$APK_SRC" "${RCLONE_REMOTE}:${DRIVE_FOLDER}/${APK_DRIVE_NAME}" --progress
    echo "  APKアップロード完了"

    # APK FILE_ID取得
    APK_FILE_ID=$("$RCLONE" lsjson "${RCLONE_REMOTE}:${DRIVE_FOLDER}/${APK_DRIVE_NAME}" 2>/dev/null \
        | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0].get('ID','REPLACE_WITH_APK_FILE_ID'))" 2>/dev/null \
        || echo "REPLACE_WITH_APK_FILE_ID")
    echo "  APK FILE_ID: $APK_FILE_ID"
    APK_DRIVE_URL="https://drive.usercontent.google.com/download?id=${APK_FILE_ID}&export=download&confirm=t"

    # ===== Step 4: version.json更新 =====
    echo ""
    echo "=== [4/5] version.json更新 ==="

    # version.jsonを一時ファイルに生成
    cat > /tmp/version.json << EOF
{
  "version_code": ${VERSION_CODE},
  "version_name": "${VERSION_NAME}",
  "apk_url": "${APK_DRIVE_URL}",
  "release_notes": "v${VERSION_NAME} (${VERSION_CODE})"
}
EOF
    echo "  生成したversion.json:"
    cat /tmp/version.json

    # -----------------------------------------------------------
    # FILE_ID維持戦略
    # -----------------------------------------------------------
    # rclone copyto は同名ファイルが存在する場合、Google Drive APIの
    # files.update を使ってファイル内容を更新するため、FILE_IDは維持される。
    # （新規ファイル作成 = files.create, 上書き = files.update で別APIになる）
    #
    # ただし DRIVE_FOLDER 配下に "version.json" という名前のファイルが
    # 既に存在していることが前提。初回アップロード後はFILE_IDが固定される。
    # -----------------------------------------------------------
    echo "  version.jsonをアップロード中 (FILE_ID: ${VERSION_JSON_FILE_ID})..."

    # 既存ファイルへの上書き（同名ファイルをrclone copytoで更新 = FILE_ID維持）
    "$RCLONE" copyto /tmp/version.json "${RCLONE_REMOTE}:${DRIVE_FOLDER}/version.json" \
        --progress \
        2>&1 | tee /tmp/rclone_upload.log

    # FILE_ID確認（アップロード後に確認）
    ACTUAL_FILE_ID=$("$RCLONE" lsjson "${RCLONE_REMOTE}:${DRIVE_FOLDER}/version.json" 2>/dev/null \
        | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0].get('ID','unknown'))" 2>/dev/null \
        || echo "unknown")

    echo "  アップロード後のFILE_ID: $ACTUAL_FILE_ID"

    if [[ "$ACTUAL_FILE_ID" != "$VERSION_JSON_FILE_ID" && "$ACTUAL_FILE_ID" != "unknown" ]]; then
        echo ""
        echo "⚠️  WARNING: FILE_IDが変化しています！"
        echo "  期待: $VERSION_JSON_FILE_ID"
        echo "  実際: $ACTUAL_FILE_ID"
        echo ""
        echo "  対処: UpdateChecker.kt の VERSION_JSON_URL を以下に更新してリビルドが必要:"
        echo "  https://drive.google.com/uc?export=download&id=${ACTUAL_FILE_ID}"
        echo ""
        echo "  ※ 初回デプロイ時（まだファイルが存在しない場合）は正常です。"
        echo "    その後のデプロイではFILE_IDは維持されます。"
    else
        echo "  ✓ FILE_ID維持確認: $ACTUAL_FILE_ID"
    fi
fi

# ===== Step 5: テスト機にインストール =====
echo ""
echo "=== [5/5] APKインストール → $DEVICE ==="
APK_WIN=$(wslpath -w "$APK_SRC")
"$ADB" -s "$DEVICE" install -r "$APK_WIN"
echo "=== Deploy complete ==="
echo ""
echo "次のステップ:"
echo "  1. scrcpy または端末画面で OTA確認ダイアログをテストせよ"
echo "  2. version.json の versionCode を現在より大きい値にして Drive に再アップロード"
echo "  3. アプリ起動 → 「アップデートあり」ダイアログが表示されることを確認"
