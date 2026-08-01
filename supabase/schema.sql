-- ============================================================================
-- MapMonarchs — schema.sql  (Supabase / Postgres 17)  RUNNABLE + idempotent.
-- Paste this whole file into the Supabase SQL Editor and Run. It applies with
-- NO error and is safe to re-run.
--
-- H3 NOTE (read this): the Postgres `h3` extension is NOT available on this
-- hosted project. `create extension h3` fails with:
--   ERROR 0A000: extension "h3" is not available / control file ... No such file.
-- That is a Supabase hosting limitation, not a permissions or SQL problem, and
-- cannot be fixed from SQL. So this file contains NO `create extension h3` and
-- NO h3_* SQL call -- which is exactly why it runs cleanly here.
--
-- Real H3 is computed in the Edge Function supabase/functions/attempt_capture
-- (npm:h3-js@4): the client sends raw lat/lng, the function derives the H3 cell
-- and calls apply_capture() below with it as the text parameter _h3. apply_capture
-- is EXECUTE-revoked from clients, so only that function can write territory.
-- Full rejection details + verification: supabase/migrations/README.md
-- ============================================================================

-- ---- FOUNDATION (tables, RLS, decay, signup trigger, backfill, realtime) ----
-- Applied to Supabase project gacptifoxlpribgdqhtz (Postgres 17.6).
-- Idempotent (if-not-exists / drop-if-exists / guarded publication add).
-- The durable record of the deployed schema; supersedes supabase/schema.sql.

-- ============================================================================
-- MapMonarchs — FOUNDATION schema (h3-free subset, deployable on Postgres 17)
--
-- WHY: this project's Postgres 17 does NOT offer h3 / h3_postgis (verified:
-- pg_available_extensions contains neither). The full schema.sql cannot run.
-- This is every statement from schema.sql that does not need h3.
--
-- SAFE BY CONSTRUCTION: the capture function is intentionally absent (it is the
-- one statement that calls an h3 function, and Postgres refuses to create a
-- function whose body references a missing function). hex_ownership has NO write
-- policy, so with no capture function NOTHING can write territory. These tables
-- are read-only truth until the capture path is solved (Edge Function + h3-js).
--
-- h3_index stays TEXT, identical to schema.sql, so a future capture path that
-- writes real H3 indices as text drops straight in with no migration.
-- ============================================================================

create extension if not exists postgis     with schema extensions;

-- ---- Tables ----------------------------------------------------------------
-- profiles is an ANCHOR, not an identity store.
--
-- It deliberately carries no handle and no colour. Both are derived on the
-- CLIENT from the user id (SHA-256 -> 6-char callsign; SHA-256 -> one of 8
-- palette colours), which makes them deterministic and identical for every
-- viewer. Storing a second copy here created two different truths for the same
-- player:
--
--   * handle was 'monarch_' || substr(id, 1, 8) in a WORLD-READABLE table,
--     publishing the first 8 characters of every user's UUID. The client
--     hashes the id precisely so no fragment of it is ever rendered; the
--     server was undoing that.
--   * colour was assigned by random() from a 6-colour list while the client
--     derives from an 8-colour list, so the same player had two different
--     colours depending on which source you read.
--
-- That divergence would surface only once other players render on the map --
-- by which point it is a data migration rather than a deletion.
create table if not exists public.profiles (
  id         uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.hex_ownership (
  h3_index           text primary key,
  owner_id           uuid not null references public.profiles(id) on delete cascade,
  captured_at        timestamptz not null default now(),
  last_reinforced_at timestamptz not null default now(),
  strength           real not null default 100
);

-- Raw audit trail for anti-cheat forensics (every attempt, accepted or not).
create table if not exists public.location_events (
  id         bigint generated always as identity primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  lat        double precision not null,
  lng        double precision not null,
  accuracy   real,
  speed      real,
  is_mock    boolean not null default false,
  h3_index   text,
  accepted   boolean not null default false,
  reason     text,
  created_at timestamptz not null default now()
);
create index if not exists idx_events_user_time on public.location_events(user_id, created_at desc);

-- "Which hexes do I hold?" is the most-read query in the game and is a
-- sequential scan without this.
create index if not exists idx_hex_owner on public.hex_ownership(owner_id);

-- ---- Row Level Security ----------------------------------------------------
alter table public.profiles       enable row level security;
alter table public.hex_ownership  enable row level security;
alter table public.location_events enable row level security;

-- profiles: world-readable; each user manages only their own row
drop policy if exists "profiles readable" on public.profiles;
create policy "profiles readable"  on public.profiles for select using (true);
drop policy if exists "own profile insert" on public.profiles;
create policy "own profile insert" on public.profiles for insert with check (auth.uid() = id);
drop policy if exists "own profile update" on public.profiles;
create policy "own profile update" on public.profiles for update using (auth.uid() = id);

-- hex_ownership: world-readable (the map is public). NO write policies exist,
-- so clients CANNOT insert/update/delete directly. Only attempt_capture() can.
drop policy if exists "hex readable" on public.hex_ownership;
create policy "hex readable" on public.hex_ownership for select using (true);

-- location_events: a user may read only their own events. No direct insert.
drop policy if exists "own events readable" on public.location_events;
create policy "own events readable" on public.location_events for select using (auth.uid() = user_id);

-- ---- Decay: effective strength computed on read ----------------------------
-- 100 strength decays at 5/hour -> a hex left alone neutralizes in ~20h. Tune later.
create or replace function public.effective_strength(h public.hex_ownership)
returns real language sql stable as $$
  select greatest(0, h.strength
    - (extract(epoch from (now() - h.last_reinforced_at)) / 3600.0) * 5.0)::real
$$;

-- ---- Auto-create a profile when a user signs up ----------------------------
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  -- Anchor row only. Identity is derived client-side from the user id.
  insert into public.profiles(id) values (new.id)
  on conflict (id) do nothing;
  return new;
end; $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Backfill. The trigger above fires on INSERT only, so it covers future signups
-- and nobody else. Every user who already exists in auth.users when this
-- migration runs would otherwise have no profiles row -- and both
-- hex_ownership.owner_id and location_events.user_id are FKs to profiles(id).
--
-- The failure that causes is nasty and late: the first attempt_capture by such a
-- user violates the FK on the location_events insert, raising an unhandled
-- plpgsql exception. The RPC then returns a 500 carrying a raw Postgres string
-- instead of {ok:false, reason:...}, which the client can only file under
-- "backend failure" -- indistinguishable from the server being down, and only
-- discovered by someone standing outdoors tapping CLAIM.
--
-- This matters for the sequencing we are actually in: email delivery is being
-- tested BEFORE this migration is applied, so the first real account will be
-- exactly the user this backfill exists for.
--
-- Anchor rows only, and idempotent, so re-running the file is harmless.
insert into public.profiles(id)
select u.id from auth.users u
on conflict (id) do nothing;
-- ---- Realtime: push hex ownership changes to the map live -------------------
-- Every other statement in this file is re-runnable; this one was not. ALTER
-- PUBLICATION ... ADD TABLE errors with "relation is already member" on a
-- second run -- and the most likely reason to re-run this file is the sanity
-- check below failing and the point() order needing a swap.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'hex_ownership'
  ) then
    alter publication supabase_realtime add table public.hex_ownership;
  end if;
end $$;

-- ---- CAPTURE WRITE-PATH (apply_capture; h3 cell arrives as a text param) ----
-- The capture write-path. H3 is NOT computed here: the Postgres h3 extension
-- is unavailable on this host (see migrations/README.md), so the cell is
-- derived by the Edge Function supabase/functions/attempt_capture (real H3 via
-- h3-js) and passed in as the text parameter _h3. apply_capture is
-- EXECUTE-revoked from clients, so only that function can call it.
-- Idempotent (create-or-replace / revoke / grant).

-- ============================================================================
-- MapMonarchs — capture path for Postgres 17 (no h3 extension in the DB)
--
-- This is attempt_capture() from schema.sql with exactly two changes:
--   * the H3 cell is a PARAMETER (_h3), not computed here -- it is computed by
--     the Edge Function (supabase/functions/attempt_capture) with h3-js, which
--     is the only place the h3 dependency now lives.
--   * the user id is a PARAMETER (_uid), because the Edge Function has already
--     verified the caller's JWT.
--
-- WHY THIS PRESERVES THE ANTI-CHEAT CONTRACT: the client still sends only raw
-- lat/lng to the Edge Function. The Edge Function (server-side, trusted) derives
-- the cell and calls this. This function is EXECUTE-revoked from clients, so a
-- hacked client cannot call apply_capture directly with a hand-picked _h3 -- it
-- can only reach the Edge Function, which never trusts a client-sent cell.
--
-- Atomicity is kept HERE (not in the Edge Function): the SELECT ... FOR UPDATE
-- row lock and all ownership writes stay inside one Postgres function, so two
-- simultaneous captures of the same hex still serialise correctly.
--
-- Requires foundation.sql (tables, RLS, effective_strength) applied first.
-- ============================================================================
create or replace function public.apply_capture(
  _uid      uuid,
  _h3       text,
  _lat      double precision,
  _lng      double precision,
  _accuracy real    default null,
  _speed    real    default null,
  _is_mock  boolean default false
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  _atk  constant real := 40;        -- damage dealt to an enemy hex per capture
  _last public.location_events;
  _dist double precision;
  _dt   double precision;
  _hex  public.hex_ownership;
  _eff  real;
  _reason text;
begin
  if _uid is null then
    return jsonb_build_object('ok', false, 'reason', 'not_authenticated');
  end if;
  if _h3 is null or _h3 = '' then
    return jsonb_build_object('ok', false, 'reason', 'no_cell');
  end if;

  -- ---- anti-cheat gates (identical thresholds to schema.sql) ----
  if _is_mock then
    _reason := 'mock_location';
  elsif _accuracy is not null and _accuracy > 30 then
    _reason := 'low_accuracy';
  elsif _speed is not null and _speed > 7 then          -- >7 m/s (~25 km/h) = not walking
    _reason := 'too_fast';
  end if;

  -- teleport gate: implied speed since last accepted fix (Haversine, metres)
  if _reason is null then
    select * into _last from public.location_events
      where user_id = _uid and accepted
      order by created_at desc limit 1;
    if found then
      _dt := extract(epoch from (now() - _last.created_at));
      _dist := 2 * 6371000 * asin(sqrt(
        power(sin(radians(_lat - _last.lat) / 2), 2) +
        cos(radians(_last.lat)) * cos(radians(_lat)) *
        power(sin(radians(_lng - _last.lng) / 2), 2)));
      if _dt > 0 and (_dist / _dt) > 15 then    -- >15 m/s between fixes = teleport
        _reason := 'teleport';
      end if;
    end if;
  end if;

  -- log every attempt (accepted or rejected)
  insert into public.location_events(user_id, lat, lng, accuracy, speed, is_mock, h3_index, accepted, reason)
    values (_uid, _lat, _lng, _accuracy, _speed, _is_mock, _h3, _reason is null, _reason);

  if _reason is not null then
    return jsonb_build_object('ok', false, 'reason', _reason, 'h3', _h3);
  end if;

  -- ---- apply capture ----
  select * into _hex from public.hex_ownership where h3_index = _h3 for update;

  if not found then
    insert into public.hex_ownership(h3_index, owner_id) values (_h3, _uid);
    return jsonb_build_object('ok', true, 'h3', _h3, 'action', 'captured', 'owner', _uid);
  end if;

  _eff := public.effective_strength(_hex);

  if _eff <= 0 then
    update public.hex_ownership
      set owner_id=_uid, captured_at=now(), last_reinforced_at=now(), strength=100
      where h3_index=_h3;
    return jsonb_build_object('ok', true, 'h3', _h3, 'action', 'captured_neutral', 'owner', _uid);

  elsif _hex.owner_id = _uid then
    update public.hex_ownership
      set last_reinforced_at=now(), strength=100
      where h3_index=_h3;
    return jsonb_build_object('ok', true, 'h3', _h3, 'action', 'reinforced', 'owner', _uid);

  elsif _eff - _atk <= 0 then
    update public.hex_ownership
      set owner_id=_uid, captured_at=now(), last_reinforced_at=now(), strength=100
      where h3_index=_h3;
    return jsonb_build_object('ok', true, 'h3', _h3, 'action', 'captured_from_enemy', 'owner', _uid);

  else
    update public.hex_ownership
      set strength=_eff - _atk, last_reinforced_at=now()
      where h3_index=_h3;
    return jsonb_build_object('ok', true, 'h3', _h3, 'action', 'attacked',
                             'owner', _hex.owner_id, 'remaining', _eff - _atk);
  end if;
end; $$;

-- Only the Edge Function (service_role) may call this. Clients are revoked, so a
-- hacked client cannot bypass the Edge Function and submit a hand-picked cell.
-- anon is revoked EXPLICITLY, not just via public: an explicit `grant ... to anon`
-- (which is how the deployed state once drifted -- see migration
-- 20260802120006_harden_function_grants) is NOT removed by `revoke ... from
-- public`, so this must name anon to be self-healing on re-apply.
revoke execute on function public.apply_capture(
  uuid, text, double precision, double precision, real, real, boolean
) from public, anon, authenticated;
grant execute on function public.apply_capture(
  uuid, text, double precision, double precision, real, real, boolean
) to service_role;
