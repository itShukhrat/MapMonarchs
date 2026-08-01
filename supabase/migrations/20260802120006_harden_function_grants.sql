-- migration: 20260802120006_harden_function_grants
-- Applied and verified live on project gacptifoxlpribgdqhtz (2026-08-02).
--
-- WHY THIS EXISTS: a security audit (Supabase advisor + direct pg_proc.proacl
-- inspection) found the DEPLOYED grants had DRIFTED from
-- 20260722120002_capture_apply.sql. The anon role had been granted EXECUTE on
-- public.apply_capture -- so any holder of the public anon key could call
-- /rest/v1/rpc/apply_capture directly with an arbitrary _uid and a hand-picked
-- _h3/_lat/_lng and FORGE territory: no sign-in, no walking, bypassing the
-- attempt_capture Edge Function's JWT verification and server-side H3 derivation.
--
-- apply_capture trusts its _uid parameter BY DESIGN, because only the Edge
-- Function (service_role) is supposed to call it, after verifying the caller's
-- JWT. A client-reachable apply_capture voids the entire anti-cheat contract.
-- This migration re-asserts that contract and is idempotent, so re-running is safe.

-- 1. Only service_role (the Edge Function) may call apply_capture. postgres (the
--    owner) keeps it implicitly. anon/authenticated/public are revoked.
revoke execute on function public.apply_capture(
  uuid, text, double precision, double precision, real, real, boolean
) from anon, authenticated, public;
grant execute on function public.apply_capture(
  uuid, text, double precision, double precision, real, real, boolean
) to service_role;

-- 2. Trigger functions must fire as triggers, never be invoked via PostgREST RPC.
--    Revoking EXECUTE does NOT stop trigger firing (trigger invocation is not
--    gated by the function's EXECUTE ACL), so this is pure hardening.
revoke execute on function public.handle_new_user() from anon, authenticated, public;
revoke execute on function public.on_hex_change()   from anon, authenticated, public;

-- 3. Pin search_path on the two SECURITY INVOKER helpers (advisor
--    function_search_path_mutable). No body change; both only touch pg_catalog
--    builtins and their arguments, so a fixed public search_path is safe.
alter function public.effective_strength(public.hex_ownership) set search_path = public;
alter function public.get_player_records(uuid)                 set search_path = public;
