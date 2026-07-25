-- ============================================================================
-- MapMonarchs -- product surfaces: badges, records, entitlements
--
-- WHY THIS EXISTS: the Profile / Badges / Records / Subscription screens read
-- from tables that did not exist. This migration creates them so the client can
-- read REAL data the moment there is any, rather than the client faking it. It
-- writes NO rows -- every player starts with an empty cabinet, an empty log, and
-- the free tier, which is the honest cold-start state and exactly what the UI
-- renders today.
--
-- THE ANTI-CHEAT STANCE IS THE SAME AS hex_ownership: the tables a player must
-- not be able to forge (earned badges, recorded history, paid entitlement) have
-- SELECT policies but NO client write policies. They are written only by
-- service_role -- the capture backend awards badges and logs events, and a
-- verified purchase writes entitlement. A client that could insert its own
-- badge, its own history, or its own Pro tier would make every one of those a
-- fabrication, which is the whole thing the product refuses to do.
--
-- Idempotent: safe to run more than once (create ... if not exists, drop policy
-- if exists before create).
-- ============================================================================

-- ---- Badge definitions: what CAN be earned ---------------------------------
-- Mirrors the client BadgeCatalog. Kept server-side too so a future admin can
-- add seasonal/event badges without shipping an app update, and so user_badges
-- can key against a known set.
create table if not exists public.badge_definitions (
  key         text primary key,
  category    text not null check (category in ('field','endurance','command')),
  sort        int  not null default 0,
  active      boolean not null default true,
  created_at  timestamptz not null default now()
);

-- ---- Earned badges: what a player HAS earned -------------------------------
create table if not exists public.user_badges (
  user_id    uuid not null references public.profiles(id) on delete cascade,
  badge_key  text not null references public.badge_definitions(key) on delete cascade,
  earned_at  timestamptz not null default now(),
  primary key (user_id, badge_key)
);
create index if not exists idx_user_badges_user on public.user_badges(user_id);

-- ---- Record events: append-only game history -------------------------------
-- GAME history, not fitness. kind is a capture-domain event; there is no
-- distance/duration column because there is no distance/duration concept.
create table if not exists public.record_events (
  id         bigint generated always as identity primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  kind       text not null check (kind in ('capture','reinforce','lost')),
  h3_index   text,
  at         timestamptz not null default now()
);
create index if not exists idx_record_events_user on public.record_events(user_id, at desc);

-- ---- Entitlements: the paid tier, server-owned -----------------------------
-- One row per user, absent = free. Written ONLY by verified purchase handling
-- (service_role); a client cannot grant itself Pro. expires_at supports lapsing
-- a subscription without deleting the row's history.
create table if not exists public.entitlements (
  user_id    uuid primary key references public.profiles(id) on delete cascade,
  tier       text not null default 'pro' check (tier in ('pro')),
  source     text not null check (source in ('play','promo','grant')),
  updated_at timestamptz not null default now(),
  expires_at timestamptz
);

-- ---- Row Level Security ----------------------------------------------------
alter table public.badge_definitions enable row level security;
alter table public.user_badges       enable row level security;
alter table public.record_events      enable row level security;
alter table public.entitlements       enable row level security;

-- badge_definitions: world-readable (the whole cabinet is public), no client writes.
drop policy if exists "badge defs readable" on public.badge_definitions;
create policy "badge defs readable" on public.badge_definitions for select using (true);

-- user_badges: world-readable (other players may see which medals you hold),
-- but NO client write policy -- badges are awarded by the server, never claimed.
drop policy if exists "user badges readable" on public.user_badges;
create policy "user badges readable" on public.user_badges for select using (true);

-- record_events: a user reads only their own history. No direct insert.
drop policy if exists "own records readable" on public.record_events;
create policy "own records readable" on public.record_events for select using (auth.uid() = user_id);

-- entitlements: a user reads only their own. No client write -- a paid tier is
-- granted only by verified purchase running as service_role.
drop policy if exists "own entitlement readable" on public.entitlements;
create policy "own entitlement readable" on public.entitlements for select using (auth.uid() = user_id);

-- ---- Aggregate read: the player's records ----------------------------------
-- One round-trip for the intelligence log. Returns nulls (not zeros) for a
-- player with no history, matching PlayerRecords.NONE on the client: unknown is
-- a statement about the data, zero would be a statement about the player.
create or replace function public.get_player_records(uid uuid)
returns table (
  first_capture       timestamptz,
  total_captures      bigint,
  current_streak_days int,
  longest_streak_days int,
  total_hexes_held    bigint,
  peak_hexes_held     bigint
) language sql stable as $$
  select
    (select min(at) from public.record_events e where e.user_id = uid and e.kind = 'capture'),
    (select nullif(count(*), 0) from public.record_events e where e.user_id = uid and e.kind = 'capture'),
    null::int,   -- streaks need day-bucketing; added when capture history is real
    null::int,
    (select nullif(count(distinct h3_index), 0) from public.record_events e where e.user_id = uid and e.kind = 'capture'),
    (select nullif(count(*), 0) from public.hex_ownership o where o.owner_id = uid)
$$;

-- Seed the badge catalogue. Matches the client BadgeCatalog keys exactly; the
-- two must stay in step (client renders criteria, server awards against these).
insert into public.badge_definitions (key, category, sort) values
  ('first_capture','field',10),
  ('first_hold','field',20),
  ('hold_10','field',30),
  ('hold_50','field',40),
  ('border_hold','field',50),
  ('streak_7','endurance',10),
  ('streak_30','endurance',20),
  ('rank_top_100','command',10),
  ('rank_top_10','command',20)
on conflict (key) do nothing;
