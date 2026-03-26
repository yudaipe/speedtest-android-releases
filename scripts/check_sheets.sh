#!/bin/bash
# check_sheets.sh — スプレッドシートCSV取得＋データ検証
# 使用法: bash scripts/check_sheets.sh [--rows N]
# 出力: 直近N件（デフォルト10）のレコードと検証結果

RCLONE="$HOME/.local/bin/rclone"
SHEETS_ID="1vzL7LBn6_7ZLztQsZZZExQCeRU4TF3jssBi7U6TyGBQ"
CSV_URL="https://docs.google.com/spreadsheets/d/${SHEETS_ID}/export?format=csv"
ROWS=10
for arg in "$@"; do
  case $arg in --rows) shift; ROWS=$1 ;;esac
done

# CSVダウンロード
TMP_CSV=$(mktemp /tmp/speedtest_XXXXXX.csv)
curl -sL "$CSV_URL" -o "$TMP_CSV"

TOTAL=$(wc -l < "$TMP_CSV")
echo "=== スプレッドシート検証 (直近${ROWS}件 / 全${TOTAL}行) ==="
echo ""

# ヘッダー表示
head -1 "$TMP_CSV"
echo "---"

# 直近N件表示
tail -n "$ROWS" "$TMP_CSV"
echo ""
echo "=== フィールド検証 ==="

# pythonで列検証
python3 << PYEOF
import csv, sys

with open("$TMP_CSV") as f:
    reader = csv.DictReader(f)
    rows = list(reader)

if not rows:
    print("データなし")
    sys.exit(0)

recent = rows[-${ROWS}:]
issues = []

for i, row in enumerate(recent):
    ts = row.get('timestamp', '')
    ssid = row.get('Wifi SSID', row.get('wifi_ssid', row.get('SSID', '')))
    sw_ver = row.get('software_version', row.get('Software Version', ''))
    server = row.get('server_name', row.get('Server Name', ''))
    dl = row.get('download_mbps', row.get('DL', ''))

    row_issues = []
    if not ts: row_issues.append('timestamp空')
    if not ssid: row_issues.append('SSID空')
    if not sw_ver: row_issues.append('software_version空')
    if not server: row_issues.append('server_name空')
    if not dl: row_issues.append('download空')

    if row_issues:
        issues.append(f"  行{len(rows)-len(recent)+i+2}: {', '.join(row_issues)}")

if issues:
    print(f"⚠️  問題あり ({len(issues)}件):")
    for iss in issues:
        print(iss)
else:
    print(f"✅ 直近{len(recent)}件: 全フィールド正常")
PYEOF

rm -f "$TMP_CSV"
