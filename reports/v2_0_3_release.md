# cmd_122 v2.0.3 release report

## Summary

- versionCode/versionName updated to `15` / `2.0.3`
- Added Supabase columns for cmd_116 cellular/network/device metrics
- Built debug APK for OTA asset upload
- Created GitHub Release `v2.0.3` in `yudaipe/speedtest-android-releases`

## Included changes

- cmd_116: cellular/network/device telemetry fields for Android v2
- cmd_120: review fixes for cellular collection threading and network probe handling

## Artifacts

- APK: `app/build/outputs/apk/debug/speed_test_monitor-v2.0.3.apk`
- OTA metadata: `version.json` uploaded to GitHub Releases latest asset

## Verification

- Supabase ALTER TABLE executed through Management API
- `JAVA_HOME=/home/ysuzuki/jdk17 ./gradlew assembleDebug`
