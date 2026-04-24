# cmd_259b dumpsys pivot

## Summary

- Hidden radio collection was rewritten from app-side reflection / telephony registry binder listener logic to `dumpsys telephony.registry` parsing.
- Release version was bumped to `0.2.9-shizuku-beta` (`versionCode=209`).
- `:app:assembleRelease` succeeded with `JAVA_HOME=/home/ysuzuki/jdk17`.

## Implementation Details

- `HiddenRadioCollector` now launches `sh -c "dumpsys telephony.registry"` through Shizuku and parses `mPhysicalChannelConfigs=` blocks from stdout.
- Parsed fields:
  - `mConnectionStatus`
  - `mCellBandwidthDownlinkKhz`
  - `mCellBandwidthUplinkKhz`
  - `mNetworkType`
  - `mFrequencyRange`
  - `mDownlinkFrequency`
  - `mDownlinkChannelNumber`
  - `mPhysicalCellId`
  - `mBand`
- The debug panel raw dump now stores the full command result string (`command`, `exitCode`, `stderr`, `stdout`) instead of only `PhysicalChannelConfig` objects.
- The Shizuku API exposes `newProcess` as a private method in this dependency version, so the collector reaches it reflectively while still executing the intended Shizuku-side process path.

## Removed Paths

- Removed `collectViaReflection`
- Removed `collectViaListener`
- Removed `collectViaPrivilegedBinder`
- Removed `createPrivilegedRegistry`
- Removed `invokeRegistryListen`
- Deleted `PhysicalChannelConfigListenerBinder.kt`
- Removed all `ShizukuBinderWrapper`, `SystemServiceHelper`, `ITelephonyRegistry`, and `listenWithEventList` usage from the collector

## Verification

- `./gradlew :app:assembleRelease`
  - Result: success
- `git diff main..feature/shizuku-beta -- app/src/main/java/com/shogun/speedtest/network/CellularInfoCollector.kt`
  - Result: no diff

## Artifacts

- APK: `app/build/outputs/apk/release/speed_test_monitor-v0.2.9-shizuku-beta.apk`
