-- ============================================================================
-- MapMonarchs — v0 schema  (Supabase / Postgres)
-- Run this in the Supabase SQL Editor.  Contains NO secrets.
--
-- Design rule: the client NEVER writes territory. Ownership is mutated only by
-- the SECURITY DEFINER function attempt_capture(). RLS blocks all direct writes
-- to hex_ownership, so a hacked client cannot mint territory.
-- ============================================================================

-- ---- Extensions (available on Supabase) ------------------------------------
create extension if not exists postgis     with schema extensions;
create extension if not exists h3          with schema extensions;
create extension if not exists h3_postgis  with schema extensions;

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

-- ============================================================================
-- attempt_capture — the only path that mutates territory.
-- Client sends raw lat/lng (+ accuracy/speed/mock flag). Server derives the hex
-- itself (never trusts a client-sent hex), runs anti-cheat gates, logs the
-- attempt, and only then applies the capture.
-- ============================================================================
create or replace function public.attempt_capture(
  _lat      double precision,
  _lng      double precision,
  _accuracy real    default null,
  _speed    real    default null,
  _is_mock  boolean default false
) returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  _uid  uuid := auth.uid();
  _res  constant int := 9;          -- H3 resolution (edge ~174m). Playtest dial.
  _atk  constant real := 40;        -- damage dealt to an enemy hex per capture
  _h3   text;
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

  -- Derive hex server-side from submitted coordinates. NOTE: h3_latlng_to_cell
  -- takes POINT(longitude, latitude). The sanity check at the bottom proves this.
  _h3 := h3_latlng_to_cell(point(_lng, _lat), _res)::text;

  -- ---- anti-cheat gates ----
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

-- Postgres grants EXECUTE on new functions to PUBLIC by default, so the grant
-- below does not by itself keep anon out. The body already returns
-- not_authenticated when auth.uid() is null, so this is defence in depth --
-- but an unauthenticated caller should not reach the body at all.
revoke execute on function public.attempt_capture(
  double precision, double precision, real, real, boolean
) from public;
grant execute on function public.attempt_capture(
  double precision, double precision, real, real, boolean
) to authenticated;

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

-- ============================================================================
-- SANITY CHECK — run this once. It MUST return 8928308280fffff.
-- If it returns something else, the POINT() order is flipped: swap to
-- point(_lat, _lng) in attempt_capture and re-run. This is the H3 lng/lat gotcha.
-- ============================================================================
select h3_latlng_to_cell(point(-122.41795063018799, 37.775938728915946), 9)::text
       as should_be_8928308280fffff;