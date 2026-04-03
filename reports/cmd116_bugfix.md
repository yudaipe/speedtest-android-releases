# cmd_120 cmd_116 bugfix report

- 対象: `SpeedtestWorker.kt`, `CellularInfoCollector.kt`, `NetworkQualityProbe.kt`
- P1修正:
  - `CellularInfoCollector.startTracking()` 呼び出しを `withContext(Dispatchers.Main)` で Main Looper 実行へ変更
  - `sampleRsrp()` を `(primary ?: fallback)?.let { rsrpSamples.add(it) }` に修正
  - Android 12(API 31) 以上は `TelephonyCallback`、それ未満は `PhoneStateListener` の分岐を実装
- P2修正:
  - `rsrpSamples` を `Collections.synchronizedList(...)` 化し、分散計算時はスナップショットへ退避
  - `allCellInfo` 取得前に `ACCESS_FINE_LOCATION` 権限チェックを追加
  - `NetworkQualityProbe.collect()/measure()` を `suspend` 化し、I/O 計測は `withContext(Dispatchers.IO)` へ移動
- 補足:
  - 指示にあった `Thread.sleep(100)` は `CellularInfoCollector.kt` 内に存在しなかったため、該当置換は不要であった
- 検証:
  - `JAVA_HOME=/home/ysuzuki/jdk17 ./gradlew assembleDebug`
  - 結果: `BUILD SUCCESSFUL`
