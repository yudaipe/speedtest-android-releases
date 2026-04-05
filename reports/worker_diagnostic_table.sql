create table if not exists public.diagnostic (
  id bigserial primary key,
  timestamp timestamptz not null,
  event_type text not null,
  result text null,
  error_reason text null,
  duration_ms bigint null,
  app_type text not null
);
