# Releasing MapMonarchs

The app is code-complete and device-verified in debug. This is the exact,
ordered list of EXTERNAL steps to produce a Play-ready build. None of these can
be done from inside the repository; each needs an account, a secret, or an admin
action.

## 1. Signing key (blocks: any release upload)

Play requires a signed artifact. The build is wired to sign automatically once
you supply a keystore; nothing is committed.

Create an upload key (once):

```
keytool -genkey -v -keystore mapmonarchs-upload.jks -alias mapmonarchs \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then add to `local.properties` (git-ignored) — or the CI environment:

```
RELEASE_STORE_FILE=mapmonarchs-upload.jks
RELEASE_STORE_PASSWORD=…
RELEASE_KEY_ALIAS=mapmonarchs
RELEASE_KEY_PASSWORD=…
```

With those present, `:app:bundleRelease` produces a **signed** AAB. Enrol the app
in **Play App Signing** (recommended) so Google manages the app-signing key and
this stays only the upload key.

## 2. The Play artifact

Upload the **AAB**, not the APK — Play splits it per-ABI, roughly halving the
download (the Mapbox native libraries are the bulk of the size):

```
./gradlew :app:bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

## 3. Apply the backend migrations (blocks: any signed-in data)

**APPLIED — 2026-07-25.** All migrations below are live on the MapMonarchs
project (`gacptifoxlpribgdqhtz`, Postgres 17.6), verified against the shipped
anon key:

- `20260722120001_foundation.sql` — profiles, hex_ownership, location_events *(already live)*
- `20260722120002_capture_apply.sql` — apply_capture() *(already live)*
- `20260725120003_product_surfaces.sql` — badges, records, entitlements, RLS, `get_player_records()` **← applied this pass**
- `20260725120004_avatar_storage.sql` — `avatars` bucket + Pro-gated write policies **← applied this pass**
- `20260725120005_capture_awards.sql` — awarding trigger on capture **← applied this pass**

Verified live: 4 product tables with RLS, exactly one SELECT policy each (no
client write path), `badge_definitions` seeded with all 9 keys, and
`get_player_records()` returning nulls (not zeros) for an unknown player. A
signed-in read now returns real (empty) data instead of "relation does not
exist"; signed-out, no read happens at all.

## 4. Server-authoritative writes

These tables are writable only by the server — a client cannot forge a badge, a
record, or a paid tier.

- **Award badges / log records — LIVE.** `20260725120005_capture_awards.sql`
  installs an `AFTER INSERT/UPDATE` trigger on `hex_ownership` (the only thing
  `apply_capture()` writes) that, running as owner, logs a `record_event` and
  awards the badges computable at capture time: **first_capture, hold_10,
  hold_50**. Verified with a controlled capture that awarded + logged, then
  rolled back to empty. When a real capture lands, these populate automatically.

- **Still needs a scheduled job / more data (honestly left LOCKED):**
  - `first_hold` — a hex surviving into the next day → a daily pg_cron job.
  - `streak_7` / `streak_30` — active days in a row → a daily day-bucketing job.
  - `border_hold` — a hex on the national border → needs cell geometry.
  - `rank_top_100` / `rank_top_10` — needs the leaderboard (not built).

- **Write entitlement** — insert into `entitlements` (`source='play'`) after
  server-side receipt verification, or (`source='grant'`) for an admin comp.
  The read side is already live.

## 5. Billing (blocks: buying Pro)

The subscription screen shows Free vs Pro honestly and keeps the upgrade action
disabled until a real purchase path exists. To enable it:

- Create the subscription product + price in **Play Console**.
- Add the Play Billing client + product-catalog mapping in the app.
- Verify receipts server-side and write `entitlements` (step 4).

Note: entitlement is **already** server-driven and live — an admin can
`source='grant'` a row in `entitlements` today and the app will honour it with
no billing client. To grant Pro to a user:
`insert into entitlements(user_id, source) values ('<uuid>', 'grant');`

## 6. Avatar storage

**Bucket + policies APPLIED** (`20260725120004_avatar_storage.sql`): a public
`avatars` bucket with owner-only, **Pro-gated** write policies — a free player
cannot upload even by calling the Storage API directly, because the insert/update
policies check `entitlements` in the database. What remains is client-side and
gated on billing existing:

- Add the upload path in the app (write to `avatars/<uid>/…`), surfaced only for
  `Entitlement.Pro`. Until billing (step 5) can produce a Pro user, no one
  reaches it — which is why the profile picture reads LOCKED today.

## Optional: enable R8 (size)

`optimization.enable = true` in the release buildType shrinks Kotlin/Java code.
`proguard-rules.pro` already holds the necessary keep rules. It must be
re-verified against a **signed-in** read (kotlinx-serialization resolves
serializers reflectively on that path) before shipping. Native libraries
dominate the artifact size, so the AAB per-ABI split (step 2) matters more.

## Rotate — reminder

The `sbp_` Supabase personal-access tokens and the Mapbox `sk.` secret token
that were pasted into chat during development should be rotated; they were never
committed but they are in conversation logs.
