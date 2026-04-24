# cmd_251 upgrade保全テスト報告

- 実施日時: 2026-04-23T00:54:40+09:00
- 対象: `v2.5.3 -> v2.6.0-rc2` 上書き install 保全確認
- 判定: `skipped`

## 前提確認

- `gh release view v2.5.3 --repo yudaipe/speedtest-android-releases` 成功
- `gh release view v2.6.0-rc2 --repo yudaipe/speedtest-android-releases` 成功
- `adb` 実体: `/home/ysuzuki/Android/Sdk/platform-tools/adb`
- 接続デバイス: `100.94.81.42:46861`
- 端末情報: `moto g66j 5G`, Android `15`

## 未実施理由

接続先が emulator ではなく実機相当であることを確認した。タスク注意事項に
「殿実機を使う場合は必ず家老（karo）への確認を先に入れよ」とあるため、
確認未了の状態では install 操作を実行しない。

## 殿実機での手順

1. 現在の計測データが存在することを確認（アプリ起動して画面確認）
2. `v2.6.0-rc2` APK をサイドロード（上書き install）
3. アプリ起動して crash がないことを確認
4. 計測履歴が残っていることを確認
5. 設定（Supabase URL 等）が保持されていることを確認

## 補足

- 接続済み実機が利用可能な点は確認済み
- 家老承認後は同端末で即試験に移行可能
