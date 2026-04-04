cmd_127

# Speedtest Android Shizuku HiddenBandInfo Implementation

## Summary

- Added HiddenBandInfo raw-field persistence to `SpeedtestResult`.
- Updated Supabase sync payload to include Shizuku-derived fields when available.
- Confirmed `versionName = "2.1.0-beta"`.
- Built debug APK successfully.

## Implemented Fields

The following fields are now persisted locally and included in the Supabase payload:

- `band_number_direct`
- `ca_components_json`
- `nr_type`
- `en_dc_available`
- `neighbor_cells_json`
- `cell_id`
- `enb_id`

When Shizuku is unavailable, `HiddenBandInfoProvider.collect()` returns an empty object, so these fields remain `null`.

## Code Changes

- `CellularInfoCollector.kt`
  - Exposes raw HiddenBandInfo values via `CellularInfo`
  - Serializes CA components and neighbor cells into JSON strings
- `SpeedtestResult.kt`
  - Adds storage columns for HiddenBandInfo-derived values
- `SpeedtestDatabase.kt`
  - Bumps Room schema version from `5` to `6`
- `SpeedtestWorker.kt`
  - Stores new HiddenBandInfo fields in Room
  - Includes them in the Supabase POST payload

## Build Verification

Command used:

```bash
export JAVA_HOME=/home/ysuzuki/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```

APK:

`/home/ysuzuki/programs/Claude_codings/coding/speedtest-android/app/build/outputs/apk/debug/speed_test_monitor-v2.1.0-beta.apk`

## Required Supabase Migration

Run separately against Supabase:

```sql
ALTER TABLE speed_test_results ADD COLUMN IF NOT EXISTS band_number_direct INTEGER;
ALTER TABLE speed_test_results ADD COLUMN IF NOT EXISTS ca_components_json TEXT;
ALTER TABLE speed_test_results ADD COLUMN IF NOT EXISTS nr_type VARCHAR(10);
ALTER TABLE speed_test_results ADD COLUMN IF NOT EXISTS en_dc_available BOOLEAN;
ALTER TABLE speed_test_results ADD COLUMN IF NOT EXISTS neighbor_cells_json TEXT;
ALTER TABLE speed_test_results ADD COLUMN IF NOT EXISTS cell_id BIGINT;
ALTER TABLE speed_test_results ADD COLUMN IF NOT EXISTS enb_id BIGINT;
```
