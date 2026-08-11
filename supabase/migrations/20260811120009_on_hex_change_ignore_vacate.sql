-- A hex whose owner becomes NULL has been VACATED (its holder deleted their
-- account), not captured. The gain/takeover path must not run for it: there is
-- no new holder to award, and logging a 'lost' record for the departing owner
-- both means nothing (they are being deleted) and violates the record_events FK
-- mid-cascade, which would abort the whole account deletion. Guard the gain block
-- on a real new owner; a vacate now falls through and does nothing.
create or replace function public.on_hex_change()
 returns trigger
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
declare
  _held int;
begin
  if (tg_op = 'INSERT'
      or (tg_op = 'UPDATE' and NEW.owner_id is distinct from OLD.owner_id))
     and NEW.owner_id is not null then

    if tg_op = 'UPDATE'
       and OLD.owner_id is not null
       and OLD.owner_id is distinct from NEW.owner_id then
      insert into public.record_events(user_id, kind, h3_index)
        values (OLD.owner_id, 'lost', NEW.h3_index);
    end if;

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

  elsif tg_op = 'UPDATE'
        and NEW.owner_id = OLD.owner_id
        and NEW.strength > OLD.strength then
    insert into public.record_events(user_id, kind, h3_index)
      values (NEW.owner_id, 'reinforce', NEW.h3_index);
  end if;

  return null;
end;
$function$;
