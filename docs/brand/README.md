# MapMonarchs brand assets — the crown mark

`crown-logo-source.png` is the canonical logo as supplied. Everything else in the
launch path is derived from it by script, not redrawn by hand, so a future edit
never has to re-guess the shape.

## Why the artwork had to be taken apart

The supplied file is a **pre-composed app-icon tile**: the crown and its
blue-to-green gradient are baked into a single raster. An Android adaptive icon
needs those as two independent layers, because the launcher masks the background
layer to its own shape (circle, squircle, rounded square) and crops everything
in it. Shipping the flat tile as one layer would have let a circular mask slice
the crown's outer finials off — the mark occupies roughly 83% of the tile width,
while only the central 66dp circle (61%) is guaranteed on every mask shape.

So the crown is keyed onto its own transparent layer and the gradient is rebuilt
as a vector.

## Derived assets

| Asset | Source | Notes |
|---|---|---|
| `app/.../drawable-nodpi/ic_launcher_crown_fg.png` | `tools/extract-icon.ps1` | Crown keyed to transparency, centred in 432×432, scaled to 258px so it sits inside the safe circle. |
| `app/.../drawable-nodpi/ic_launcher_crown_mono.png` | same script | Flat white silhouette for Android 13+ themed icons. |
| `app/.../drawable-nodpi/ic_brand_crown.png` | `tools/gen-intro-crown.ps1` | **Tight** crop, 423×344, no safe-zone padding. Used by `BrandIntro` — the launcher foreground cannot be reused there because its padding would leave the mark floating small on a full screen. |
| `app/.../drawable/ic_launcher_crown_bg.xml` | measured, see below | Vector rebuild of the gradient. |

## Measured values (do not re-guess these)

- **Tile bounds in the source:** `140,165`, `544×543`.
- **Crown tight bounding box:** `200,263`, `423×344`.
- **Gradient endpoints:** `#1A3AA1` → `#0FAF87`, sampled from the most and least
  advanced points along the tile's own diagonal.

A first pass sampled `#6071A8` / `#77C8B3` — those are wrong. They came from
anti-aliased pixels where the tile's rounded edge blends into the white page,
which also key as "crown" because they are light and desaturated. Both the crown
bbox and the gradient sampling therefore **inset 7% inside the tile** before
measuring, and the gradient test additionally requires saturation > 0.42.

## Keying rule

A pixel is crown if it is light and unsaturated against the saturated gradient:

- luminance < 110 → not crown
- saturation ≤ 0.22 → fully crown; ≥ 0.42 → not crown; linear between
- luminance 110–150 fades alpha in, so the darker gradient never leaks

## Launch path

`launcher icon → system splash → BrandIntro` all use this same artwork. Android
12+ draws the adaptive icon as the splash, so icon and splash are identical by
construction, and `BrandIntro` animates `ic_brand_crown` — the same pixels, not
a reinterpretation. Device proof: `launch-crown-*.png`.

The intro shows the crown on the app's obsidian field rather than on the logo's
tile. The tile exists because an app icon must fill a square; reproducing it
full-screen reads as a screenshot of an icon pasted into the app, and pushes a
second colour across a surface the rest of the product keeps to one accent.

## Known divergence

The **in-app** identity (`IdentityCrest`, dock glyphs, HUD, capture button) is
still the concentric-hexagon crest. The launch path is crown end to end; the mark
changes at the onboarding handover. This is a deliberate, reported gap, not an
oversight — `IdentityCrest`'s core fill carries the player's territory colour,
which is a gameplay mechanic rather than decoration, so moving it to the crown is
a scoped decision that has not been taken.
