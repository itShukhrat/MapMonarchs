# MapMonarchs

Premium Android-first location-based strategy game. Walking creates territory,
empires, and status on a live map.

**Status:** v0 spike — proving the core loop (walk -> claim hex -> lose hex) is
fun and cheat-resistant. Empires / seasons / shop are intentionally NOT built yet.

## Stack
- **Client:** Kotlin + Jetpack Compose + Material 3 (min SDK 26)
- **Map:** Mapbox Maps SDK for Android v11 (custom dark style)
- **Backend:** Supabase (Postgres + PostGIS + H3, Auth, Realtime)
- **Grid:** Uber H3, resolution 9 (server-authoritative)

## Architecture rule that must never break
The client NEVER writes territory. Ownership changes only through the
`attempt_capture()` Postgres function. RLS blocks all direct writes to
`hex_ownership`. See `supabase/schema.sql`.

## Setup
1. Run `supabase/schema.sql` in the Supabase SQL Editor. Confirm the sanity
   check at the bottom returns `8928308280fffff`.
2. Copy `local.properties.example` -> `local.properties` and fill in:
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY`   (publishable/anon key — NOT the secret key)
   - `MAPBOX_PUBLIC_TOKEN` (pk.* token)
   `local.properties` is git-ignored. Secrets never go in source.

## Secrets policy
- The Supabase **secret / service_role** key must NEVER appear in this repo or
  the APK. It bypasses RLS. Client uses the anon key only.
- Privileged operations (schema, functions) are run in the Supabase dashboard.

## Folder plan (client, single module for v0)
```
com.mapmonarchs/
  app/       MainActivity, nav, DI
  core/      design system (theme, tokens), common utils (env with .trim())
  domain/    pure Kotlin: models, use cases, repo interfaces (unit-tested)
  data/      Supabase client, repo impls, DTOs
  location/  FusedLocation -> Flow<LocationFix>
  feature/   auth/, map/
```