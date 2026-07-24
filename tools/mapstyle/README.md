# MapMonarchs tactical basemap

The map's basemap is a custom Mapbox Studio style, referenced from
`MapScreen.kt` as `MAP_STYLE`:

```
mapbox://styles/shukhra-t/cmryd7j1700nf01qt76jvf82q   ("MapMonarchs Tactical Dark")
```

It is `dark-v11` retuned to the app palette (cool Void/Obsidian board, no
saturation of its own) with a deliberate label/road hierarchy tuned for rich
**Uzbek coverage** while staying a command board, not a navigation map:

- **Places:** country > capital (Tashkent, brightest) > regional city > town >
  city district — all real Mapbox Streets v8 / OpenStreetMap data.
- **Roads:** full road-class network, muted and widening by importance; street
  names appear at street zoom (~z13+).
- **Geography:** named rivers, seas, deserts (Amu Darya, Aral Sea, Kyzylkum…).
- **Held back:** POIs (shops/venues) and footpaths/steps/rail — clutter that
  would make it read as a travel app.

Nothing is invented: coverage tracks OSM density. It is dense in
Tashkent/Samarkand/Bukhara (street names + mahalla districts), and thinner in
sparser regions where OSM itself is thinner.

## Regenerating / editing the style

`build-style.js` is the single source of truth for the transform. It reads a
fresh `dark-v11.json` and emits the tuned style. To change tones, label
hierarchy, or which layers are shown, edit that file and re-run.

```sh
# needs a Mapbox SECRET token (styles:read/write) in $TK; never commit it
curl -s "https://api.mapbox.com/styles/v1/mapbox/dark-v11?access_token=$TK" -o dark-v11.json
node build-style.js .                      # writes mm-style2.json
# update the existing style in place (keeps the URL stable):
curl -s -X PATCH \
  "https://api.mapbox.com/styles/v1/shukhra-t/cmryd7j1700nf01qt76jvf82q?access_token=$TK" \
  -H "Content-Type: application/json" --data-binary @mm-style2.json
```

## Reversibility

Swap `MAP_STYLE` back to `mapbox://styles/mapbox/dark-v11` to restore the stock
basemap. Every runtime layer the app adds (fog, Sovereign border, player puck)
is style-independent, so nothing depends on this style existing.

Coverage evidence: `docs/mapstyle/coverage/` (static renders of the live style
across cities/zooms, plus an in-app device shot).
