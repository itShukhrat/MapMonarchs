-- Bounded retention for the precise-location anti-cheat trail. location_events is
-- diagnostic, not gameplay: 30 days is enough to spot movement abuse, and
-- unbounded precise-location retention is a privacy liability we choose not to
-- carry. SECURITY DEFINER so the scheduled job can delete regardless of RLS.
create extension if not exists pg_cron;

create or replace function public.purge_old_location_events()
returns void
language sql
security definer
set search_path = public
as $$
  delete from public.location_events where created_at < now() - interval '30 days';
$$;

-- Daily at 03:00 UTC. cron.schedule upserts by job name.
select cron.schedule(
  'purge-location-events',
  '0 3 * * *',
  $$select public.purge_old_location_events();$$
);
