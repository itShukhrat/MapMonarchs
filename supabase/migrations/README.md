# MapMonarchs — Supabase backend (project `gacptifoxlpribgdqhtz`, Postgres 17.6)

These migrations are the durable record of the **deployed** backend. Apply in
lexical order. They are idempotent, so re-running is safe.

| # | file | what it deploys |
|---|------|-----------------|
| 1 | `20260722120001_foundation.sql` | `profiles`, `hex_ownership`, `location_events`, RLS, decay (`effective_strength`), the signup trigger, backfill, realtime |
| 2 | `20260722120002_capture_apply.sql` | `apply_capture()` — the write-path, EXECUTE-revoked from clients |

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
