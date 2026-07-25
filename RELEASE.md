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

The profile surfaces read tables that must exist. Apply, in order, against the
Supabase project:

- `supabase/migrations/20260722120001_foundation.sql`
- `supabase/migrations/20260722120002_capture_apply.sql`
- `supabase/migrations/20260725120003_product_surfaces.sql`  ← badges, records, entitlements, RLS, `get_player_records()`

Until `…_product_surfaces.sql` is applied, a signed-in read returns "relation
does not exist" and the client degrades to the honest empty state (no crash, no
fake data). Signed-out, no read happens at all.

## 4. Server-authoritative writes (blocks: real badges / records / Pro)

These tables are, by design, writable only by `service_role` — a client cannot
forge a badge, a record, or a paid tier. The following must be built server-side
(Edge Function / admin), because no client code can or should do them:

- **Award badges** — insert into `user_badges` when the capture backend confirms
  the earning condition (first capture, streak, hold count, border hold).
- **Log record events** — insert into `record_events` on each accepted claim, so
  `get_player_records()` has history to aggregate.
- **Write entitlement** — insert into `entitlements` (`source='play'`) after
  server-side receipt verification, or (`source='grant'`) for an admin comp.

## 5. Billing (blocks: buying Pro)

The subscription screen shows Free vs Pro honestly and keeps the upgrade action
disabled until a real purchase path exists. To enable it:

- Create the subscription product + price in **Play Console**.
- Add the Play Billing client + product-catalog mapping in the app.
- Verify receipts server-side and write `entitlements` (step 4).

Note: entitlement is **already** server-driven — an admin can `source='grant'`
Pro today (after step 3) and the app will honour it with no billing client.

## 6. Avatar storage (blocks: the one Pro benefit that needs storage)

The Pro "profile picture" benefit is marked SOON because it needs a bucket:

- Create a Supabase Storage bucket (e.g. `avatars`), owner-write RLS.
- Add the upload path in the app, gated behind `Entitlement.Pro`.

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
