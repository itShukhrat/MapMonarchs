# MapMonarchs — Supabase backend (project `gacptifoxlpribgdqhtz`, Postgres 17.6)

`../schema.sql` is the single-file, runnable, idempotent form of the deployed
backend -- paste it into the SQL Editor and Run (verified live: applies with no
error). These migration files are the SAME content split for the Supabase CLI.
Apply in lexical order; idempotent, so re-running is safe.

All six are **applied and verified live** (1–5 on 2026-07-25; 6 on 2026-08-02).

| # | file | what it deploys |
|---|------|-----------------|
| 1 | `20260722120001_foundation.sql` | `profiles`, `hex_ownership`, `location_events`, RLS, decay (`effective_strength`), the signup trigger, backfill, realtime |
| 2 | `20260722120002_capture_apply.sql` | `apply_capture()` — the write-path, EXECUTE-revoked from clients |
| 3 | `20260725120003_product_surfaces.sql` | `badge_definitions` (seeded, 9), `user_badges`, `record_events`, `entitlements`, RLS (SELECT-only for clients), `get_player_records()` |
| 4 | `20260725120004_avatar_storage.sql` | `avatars` storage bucket + owner-only, **Pro-gated** write policies (the DB enforces "profile picture is Pro") |
| 5 | `20260725120005_capture_awards.sql` | `on_hex_change()` trigger on `hex_ownership` — server-authoritative badge awarding (`first_capture`, `hold_10`, `hold_50`) + record logging |
| 6 | `20260802120006_harden_function_grants.sql` | **SECURITY:** re-revoke `apply_capture` from `anon`/`authenticated` (deployed grants had drifted, exposing it to the anon key — a full anti-cheat bypass), lock down the trigger functions from RPC, pin invoker search_path |

**Anti-cheat invariant across 3–5:** `user_badges`, `record_events`, and
`entitlements` have a SELECT policy and **no client write policy**. They are
written only by the awarding trigger (runs as owner) and by service-role paths
(verified purchase / admin grant). A player can read their dossier; they can
never forge it.

**Anti-cheat invariant for capture (2 + 6):** `apply_capture` is `SECURITY
DEFINER` and callable ONLY by `service_role` (the `attempt_capture` Edge
Function). It trusts its `_uid` parameter precisely because no client can reach
it — migration 6 re-asserts that after the deployed grant to `anon` was found and
revoked. Verified: an anon `POST /rest/v1/rpc/apply_capture` now returns
`42501 permission denied`.

## The H3 extension is NOT available on this project — verified, not assumed

The original design (`../schema.sql`) computed the H3 cell **inside Postgres**
with the `h3` extension. That extension cannot be enabled on this host.

**Exact SQL attempted** (as `postgres` superuser, via the Management API):

```sql
create extension if not exists h3 with schema extensions;
create extension if not exists h3;
create extension if not exists "h3-pg";
create extension if not exists h3_postgis with schema extensions;
```

**Exact error returned (identical for every variant):**

```
ERROR: 0A000: extension "h3" is not available
DETAIL: Could not open extension control file
        "/usr/lib/postgresql/share/postgresql/extension/h3.control": No such file or directory.
HINT: The extension must first be installed on the system where PostgreSQL is running.
```

And `select name from pg_available_extensions where name ilike '%h3%'` returns `[]`.

**Classification: hosting / missing package.** SQLSTATE `0A000` (feature_not_supported).
Not permissions (connected as `postgres` superuser). Not a schema conflict (the
DDL is valid). The extension's control file does not exist on the Supabase
managed image, and OS-level packages cannot be installed on a hosted project.
**This is not fixable from the repo or the SQL layer.**

## Where H3 actually runs

Real H3 is used — in the **Edge Function** `../functions/attempt_capture/index.ts`,
which imports `npm:h3-js@4` (the official Uber H3 JS binding) in the Deno runtime.
The client sends raw lat/lng; the Edge Function derives the H3 cell and calls
`apply_capture(_h3, ...)`. The anti-cheat contract is preserved: the client never
names its own cell, and `apply_capture` is EXECUTE-revoked from clients.

## Verification (run any time; these are the live results at deploy)

```sql
-- 1. H3 extension absent:
select name from pg_available_extensions where name ilike '%h3%';        -- []

-- 2. deployed objects present:
select tablename from pg_tables where schemaname='public';               -- hex_ownership, location_events, profiles
select proname from pg_proc where proname='apply_capture';               -- apply_capture

-- 3. apply_capture has NO postgres-h3 dependency (cell is a text param):
select position('h3_latlng' in pg_get_functiondef('public.apply_capture'::regproc)::text);  -- 0
```
