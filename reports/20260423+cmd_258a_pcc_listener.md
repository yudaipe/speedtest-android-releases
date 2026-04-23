## API分岐の実装内容

- API 26-30 は reflection で `TelephonyManager.getPhysicalChannelConfigList()` を呼ぶよう修正した。
- API 31+ は `registerTelephonyCallback()` と `PhysicalChannelConfigListener` を使う one-shot 取得へ切り替えた。
- Listener は専用 executor 上で待ち受け、1.5 秒で timeout 扱いにして `empty_result` を残す。

## Shizuku binder権限の実装状況

- `Shizuku.checkSelfPermission()` によるアプリ側許可状態は既存経路のまま。
- ただし HiddenRadioCollector は依然として app-side `TelephonyManager` を使っており、Binder 越し privileged context への昇格は未実装。
- そのため `READ_PRIVILEGED_PHONE_STATE` 相当の制約に当たる端末では `security_denied` を記録し、失敗理由が debug UI に残る。

## デバッグUI追記内容

- `sdk_version` を必ず記録する。
- 失敗時は `method_not_found` / `security_denied` / `collect_fail` を stacktrace 付きで記録する。
- 成功しても 0 件なら `empty_result` を記録し、PhysicalChannelConfig dump は `"0 configs"` で表示される。
- `privileged_context` と `collect_path` / `listener_callback` により、reflection 経路か listener 経路かを追跡できる。
