# cmd_259c IPhoneStateListener binder route

作成者: ashigaru7
作成日時: 2026-04-23T19:22:05+09:00
対象ブランチ: feature/shizuku-beta

## 実装結果

- `HiddenRadioCollector` の TelephonyCallback hidden `init()` / `callback` reflection 初期化を撤去した。
- `telephony_registry` に対して `listenWithEventList` を binder 直送する経路へ切り替えた。
- callback 側は `com.android.internal.telephony.IPhoneStateListener` と同じ descriptor / transaction code を持つ `PhysicalChannelConfigListenerBinder` を追加し、`onPhysicalChannelConfigChanged()` だけ受信する。
- version を `0.2.6-shizuku-beta` / `206` に更新した。

## 実装詳細

### HiddenRadioCollector

- `ShizukuBinderWrapper(SystemServiceHelper.getSystemService(...))` で `telephony.registry` binder を取得。
- `ITelephonyRegistry$Stub.asInterface()` を reflection で生成し、`listenWithEventList` の arity を確認。
- 実呼び出しは proxy reflection ではなく raw `IBinder.transact()` を使用。
- Android 14 系の `listenWithEventList(boolean, boolean, int, String, String, IPhoneStateListener, int[], boolean)` と旧 6 引数版の両方に対応。
- event は legacy bitmask `0x00200000` ではなく、`listenWithEventList` が要求する callback event id `33` (`EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED`) を使用。

### callback binder

- 途中で AIDL 生成も試したが、compileSdk 34 の public SDK に存在しない hidden telephony 型が多く、`IPhoneStateListener.aidl` をそのままアプリへ入れる構成は javac が通らなかった。
- そのため最終形は AIDL 生成 Stub ではなく、wire 互換の manual Binder 実装へ変更した。
- `PhysicalChannelConfigListenerBinder` は descriptor `com.android.internal.telephony.IPhoneStateListener` と transaction code `FIRST_CALL_TRANSACTION + 32` を使い、`PhysicalChannelConfig` の typed list を受信する。

## ビルド結果

- `JAVA_HOME=/home/ysuzuki/jdk17 ./gradlew :app:assembleRelease`
- 結果: 成功
- 出力 APK: `app/build/outputs/apk/release/speed_test_monitor-v0.2.6-shizuku-beta.apk`

## 非干渉確認

- `git diff main..feature/shizuku-beta -- app/src/main/java/com/shogun/speedtest/network/CellularInfoCollector.kt`
- 結果: 差分なし

## 補足

- `reports/20260423+cmd_259a_privileged_binder.md` は既存の未追跡メモとして残っていたが、本 commit には含めない。
- 実機確認は未実施。callback transaction code / registry transact 形状は AOSP generated source から合わせ込んだ。
