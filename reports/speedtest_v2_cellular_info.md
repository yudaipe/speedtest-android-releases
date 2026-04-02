# speedtest-android v2 cellular/network info SQL

```sql
alter table public.speedtest_results add column if not exists wifi_ssid text;
alter table public.speedtest_results add column if not exists connection_type text;
alter table public.speedtest_results add column if not exists rsrp_dbm integer;
alter table public.speedtest_results add column if not exists rsrq_db integer;
alter table public.speedtest_results add column if not exists sinr_db integer;
alter table public.speedtest_results add column if not exists rssi_dbm integer;
alter table public.speedtest_results add column if not exists pci integer;
alter table public.speedtest_results add column if not exists tac integer;
alter table public.speedtest_results add column if not exists earfcn integer;
alter table public.speedtest_results add column if not exists band_number integer;
alter table public.speedtest_results add column if not exists network_type text;
alter table public.speedtest_results add column if not exists carrier_name text;
alter table public.speedtest_results add column if not exists is_carrier_aggregation boolean;
alter table public.speedtest_results add column if not exists ca_bandwidth_mhz integer;
alter table public.speedtest_results add column if not exists ca_band_config text;
alter table public.speedtest_results add column if not exists nr_state text;
alter table public.speedtest_results add column if not exists mcc text;
alter table public.speedtest_results add column if not exists mnc text;
alter table public.speedtest_results add column if not exists cqi integer;
alter table public.speedtest_results add column if not exists timing_advance integer;
alter table public.speedtest_results add column if not exists visible_cell_count integer;
alter table public.speedtest_results add column if not exists handover_count integer;
alter table public.speedtest_results add column if not exists endc_available boolean;
alter table public.speedtest_results add column if not exists dns_resolve_ms double precision;
alter table public.speedtest_results add column if not exists ttfb_ms double precision;
alter table public.speedtest_results add column if not exists tcp_connect_ms double precision;
alter table public.speedtest_results add column if not exists rsrp_variance double precision;
alter table public.speedtest_results add column if not exists ram_usage_percent double precision;
alter table public.speedtest_results add column if not exists cpu_usage_percent double precision;
alter table public.speedtest_results add column if not exists bg_app_count integer;
```
