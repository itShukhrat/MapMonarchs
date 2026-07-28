-- ============================================================================
-- MapMonarchs -- server-side awarding: badges + record events on capture
--
-- WHY A TRIGGER, NOT CLIENT CODE: badges and records must be unforgeable. The
-- tables have no client write policy, so only something running as the table
-- owner can populate them. This trigger fires on the ONLY source of truth for
-- ground changing hands -- an insert or an ownership change on hex_ownership,
-- which only apply_capture() can produce -- and writes the earned badges and the
-- tactical log from that. A player cannot call it; they can only capture ground,
-- which is the point.
--
-- IT IS DECOUPLED FROM apply_capture ON PURPOSE: apply_capture stays the single
-- place that arbitrates a claim, and this reacts to the result. Retuning the
-- capture maths never risks the awarding logic and vice versa.
--
-- WHAT IT AWARDS, AND WHAT IT HONESTLY DOES NOT:
--   first_capture  -- first ground ever taken.                     (awarded here)
--   hold_10 / hold_50 -- holding N cells at once.                  (awarded here)
--   first_hold     -- a hex surviving into the next day.  needs a scheduled job.
--   border_hold    -- a hex on the national border.       needs cell geometry.
--   streak_7 / _30 -- active days in a row.               needs day-bucketing job.
--   rank_top_*     -- leaderboard standing.               needs the leaderboard.
-- The four it cannot compute at capture time are left unearned -- the client
-- shows them LOCKED, which is true. Faking them would be the lie this refuses.
--
-- Runs as owner (SECURITY DEFINER), so it bypasses the read-only RLS on
-- user_badges/record_events exactly as intended: the server writes, clients read.
-- Idempotent (badge inserts use on conflict do nothing).
-- ============================================================================

create or replace function public.on_hex_change()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $$
declare
  _held int;
begin
  -- A GAIN is a fresh capture (INSERT) or an ownership change (UPDATE where the
  -- owner is now someone else). Reinforcing your own hex or merely damaging an
  -- enemy's is not a gain and awards nothing.
  if tg_op = 'INSERT'
     or (tg_op = 'UPDATE' and NEW.owner_id is distinct from OLD.owner_id) then

    -- On a takeover, the previous holder lost this ground -- log it for them.
    if tg_op = 'UPDATE'
       and OLD.owner_id is not null
       and OLD.owner_id is distinct from NEW.owner_id then
      insert into public.record_events(user_id, kind, h3_index)
        values (OLD.owner_id, 'lost', NEW.h3_index);
    end if;

    -- The new holder gained it: log the capture and award what is computable now.
    insert into public.record_events(user_id, kind, h3_index)
      values (NEW.owner_id, 'capture', NEW.h3_index);

    insert into public.user_badges(user_id, badge_key)
      values (NEW.owner_id, 'first_capture')
      on conflict (user_id, badge_key) do nothing;

    select count(*) into _held
      from public.hex_ownership where owner_id = NEW.owner_id;

    if _held >= 10 then
      insert into public.user_badges(user_id, badge_key)
        values (NEW.owner_id, 'hold_10') on conflict (user_id, badge_key) do nothing;
    end if;
    if _held >= 50 then
      insert into public.user_badges(user_id, badge_key)
        values (NEW.owner_id, 'hold_50') on conflict (user_id, badge_key) do nothing;
    end if;

  -- A REINFORCE (own hex, strength topped up) is worth logging as tactical
  -- history, but earns no badge.
  elsif tg_op = 'UPDATE'
        and NEW.owner_id = OLD.owner_id
        and NEW.strength > OLD.strength then
    insert into public.record_events(user_id, kind, h3_index)
      values (NEW.owner_id, 'reinforce', NEW.h3_index);
  end if;

  return null; -- AFTER trigger; result ignored
end;
$$;

drop trigger if exists trg_hex_change on public.hex_ownership;
create trigger trg_hex_change
  after insert or update on public.hex_ownership
  for each row execute function public.on_hex_change();
