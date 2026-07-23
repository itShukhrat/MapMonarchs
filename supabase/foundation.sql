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
