# Localization plan (MapMonarchs)

Status: **not yet executed.** This is the authorized "list the exact strings"
deliverable. Executing it is a single atomic refactor best done as its own
focused block with a stable emulator, because `AppError.playerMessage()` and
`Blocker.explanation()` change return type and every caller must change in the
same commit — there is no compiling half-migrated state, and the brief forbids a
partial one.

## Why it is deferred, not skipped

- **Atomic and wide.** `playerMessage(): String → UiText` breaks all 7 render
  sites + 2 `explanation()` sites + 8 test files at once. Either the whole unit
  lands or the repo does not compile.
- **Two affected tests are instrumented** (`DiagnosticsScreenTest`,
  `SignInScreenTest`). The emulator has been unreliable this session, so a
  completion claim would lack device proof — which the working rules forbid.
- **Uzbek must not be machine-guessed.** English resources can be authored now;
  Uzbek needs a native speaker. The strings to hand over are listed below.

## Architecture (keeps domain pure Kotlin — no Android Context)

```
domain/                         pure Kotlin, no android.* imports
  StringKey.kt      enum of every player-facing key
  UiText.kt         sealed: Res(key, args: List<UiText>) | Literal(String)
  AppError.playerMessage(): UiText          // returns Res(key[, args])
  Blocker.explanation(): UiText             // Res, composing args for the
                                            //   IdentityUnavailable case
feature|ui/
  UiTextResolver.kt @Composable fun UiText.resolve(): String
                    // Res -> stringResource(map[key]); recurse args; Literal -> value
res/values/strings.xml       English (authored now)
res/values-uz/strings.xml    Uzbek (native review; keys mirror English)
```

Testability is preserved: domain tests assert the **key/structure** (pure JVM);
a new instrumented `UiTextResolverTest` asserts each key resolves to non-blank
text and re-homes the content-property checks (no technical leak, distinct
messages, punctuation, rate-limit "wait", exact interval).

## String inventory

### AppError.playerMessage() — 11 keys (domain)

| Key | English |
|---|---|
| `error_offline` | No connection. Territory may be out of date. |
| `error_schema_missing` | Territory service is not deployed yet. |
| `error_unauthorized` | Access refused. Sign-in may have expired. |
| `error_malformed` | Territory data could not be read. |
| `error_backend_failure` | Territory service error. |
| `error_auth_unavailable` | Sign-in unavailable. |
| `error_invalid_email` | That email address does not look right. |
| `error_invalid_code` | That code is not valid. It may have expired. |
| `error_mail_not_delivered` | The code could not be sent. Email delivery is not configured. |
| `error_unexpected` | Something went wrong. |
| `error_rate_limited_generic` | Too many attempts. Wait a minute and try again. |

**Special case — RateLimited with a known interval.** Needs a plural, not a
fixed string. Android `plurals`:

```xml
<plurals name="error_rate_limited_seconds">
  <item quantity="one">Too many attempts. Wait %1$d second and try again.</item>
  <item quantity="other">Too many attempts. Wait %1$d seconds and try again.</item>
</plurals>
```
`UiText` must therefore carry an int quantity for this key (e.g. a
`UiText.Plural(key, quantity, args)` variant, or reuse `Res` with a numeric arg).

### Blocker.explanation() — keys (domain)

| Key | Notes |
|---|---|
| `blocker_none` | plain |
| `blocker_permission_missing` | plain; names SETTINGS |
| `blocker_acquiring_fix` | plain |
| `blocker_fix_mock` / `_low_accuracy` / `_too_fast` / `_teleport` | one per RejectionReason |
| `blocker_outside_world_named` | takes the country as a `%1$s` arg |
| `blocker_outside_world_unnamed` | no country |
| `blocker_identity_resolving` | plain |
| `blocker_identity_unavailable` | **composes** `%1$s` = resolved `AppError.playerMessage()` (the coupling) |
| `blocker_not_signed_in` | plain |
| `blocker_capture_not_deployed` | plain |

`PLAYABLE_COUNTRY_NAME` ("Uzbekistan") stays a shared constant fed as an arg, so
the boundary layer, the out-of-world blocker, and onboarding cannot disagree.

### Feature-layer strings (separate, later slices)

Not domain-coupled; standard `stringResource` migration, no `UiText` needed:
- `FeatureLock` (Route.kt): 6 locks × {why, unlocksWhen, insteadNow} = 18.
- Screen bodies: Onboarding, Help, Safety, About, Settings, Identity, Sign-in,
  Diagnostics, LockedScreen. ~230 literals. Migrate screen-by-screen; each screen
  is a complete unit, so no screen is left half-abstract.

## Call sites to update in the domain slice (9)

- `feature/auth/SignInScreen.kt:102, 156`
- `feature/diagnostics/DiagnosticsScreen.kt:85 (explanation), 111, 310, 383`
- `feature/help/HelpScreen.kt:43 (explanation)`
- `feature/identity/IdentityScreen.kt:126`
- `feature/map/MapHud.kt:102`

Three of these concatenate fixed text + `playerMessage()`
(`DiagnosticsScreen.kt:310, 383`) — those fixed prefixes become their own keys so
the whole rendered sentence is one resolvable unit rather than a runtime string
join.

## Tests to update (8)

`AppErrorMessageTest`, `ErrorMapperTest`, `AuthErrorMappingTest`,
`DeliveryProofMappingTest`, `BlockerResolutionTest` (JVM → assert keys);
`DiagnosticsScreenTest`, `SignInScreenTest`, `SupabaseAuthRepositoryTest`
(instrumented → assert resolved text or key presence).

## Strings needing Uzbek native review

**All English strings above.** They must be translated by a native Uzbek
speaker, not machine-translated. Priority order for review (most-seen first):
`error_schema_missing`, `blocker_capture_not_deployed`,
`blocker_outside_world_named/unnamed`, `blocker_not_signed_in`,
`blocker_permission_missing`, the four `blocker_fix_*`, then the remaining
`error_*`, then the feature-layer screen bodies.
