-- migration: 20260722120002_capture_apply
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
revoke execute on function public.apply_capture(
  uuid, text, double precision, double precision, real, real, boolean
) from public;
revoke execute on function public.apply_capture(
  uuid, text, double precision, double precision, real, real, boolean
) from authenticated;
grant execute on function public.apply_capture(
  uuid, text, double precision, double precision, real, real, boolean
) to service_role;
