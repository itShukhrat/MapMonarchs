// v2: MapMonarchs tactical basemap, tuned for RICH Uzbek coverage.
// Same restrained cool-black board as v1, but the real place/road/geography
// layers are un-gated into a deliberate hierarchy instead of hidden:
//   country > capital > regional cities > towns > districts
//   motorway/trunk > primary > secondary > street  (native width ramp)
//   + authoritative geography (rivers, seas, deserts)
// POIs and footpaths stay out: a command board is not a navigation app.
const fs = require("fs");
const SP = process.argv[2];
const src = JSON.parse(fs.readFileSync(SP + "/dark-v11.json", "utf8"));

// ---- tone system (cool, hue ~206-220, low saturation) ----
const LAND        = "hsl(218, 24%, 7%)";
const LAND_DEEP   = "hsl(218, 26%, 5%)";
const WATER       = "hsl(208, 34%, 5%)";
const WATER_LINE  = "hsl(208, 30%, 8%)";
const PARK        = "hsl(206, 16%, 8%)";
const BUILDING    = "hsl(216, 20%, 9%)";
const BUILDING_OUT= "hsl(216, 24%, 6%)";
const ROAD        = "hsl(214, 15%, 22%)";  // slightly lifted vs v1: roads are wanted now
const ROAD_DIM    = "hsl(214, 12%, 14%)";
const AEROWAY     = "hsl(214, 12%, 15%)";
const ADMIN0      = "hsl(214, 12%, 24%)";
const ADMIN1      = "hsl(214, 10%, 16%)";
const ADMIN_BG    = LAND_DEEP;
const HALO        = "hsl(220, 45%, 4%)";
// label hierarchy by brightness
const LABEL_STRONG= "hsl(214, 18%, 78%)";  // country + capital
const LABEL       = "hsl(216, 15%, 64%)";  // regional cities
const LABEL_DIM   = "hsl(216, 13%, 50%)";  // towns, districts, roads, geography

// Footpaths / steps / cycleways / rail are not strategic roads -> stay hidden.
// Everything else (place/road/geography names) is un-hidden and re-toned.
const HIDE = new Set([
  "tunnel-path-trail","tunnel-path-cycleway-piste","tunnel-path","tunnel-steps","tunnel-pedestrian",
  "road-path-trail","road-path-cycleway-piste","road-path","road-steps","road-pedestrian",
  "road-rail","bridge-path-trail","bridge-path-cycleway-piste","bridge-path","bridge-steps",
  "bridge-pedestrian","bridge-rail",
  "poi-label",            // navigation clutter
  "natural-line-label",   // ridge lines: noisy
  "water-line-label",     // duplicates waterway-label
]);

function hide(l){ l.layout = Object.assign({}, l.layout, { visibility:"none" }); }
function show(l){ if(l.layout) delete l.layout.visibility; }
function paint(l,k,v){ l.paint = Object.assign({}, l.paint); l.paint[k]=v; }

for (const l of src.layers) {
  if (HIDE.has(l.id)) { hide(l); continue; }
  switch (l.id) {
    case "land": paint(l,"background-color",LAND); break;
    case "national-park": paint(l,"fill-color",PARK); paint(l,"fill-opacity",0.5); break;
    case "landuse": paint(l,"fill-color",PARK); break;
    case "waterway": paint(l,"line-color",WATER_LINE); break;
    case "water": paint(l,"fill-color",WATER); break;
    case "land-structure-polygon": paint(l,"fill-color",LAND_DEEP); break;
    case "land-structure-line": paint(l,"line-color",LAND_DEEP); break;
    case "aeroway-polygon": paint(l,"fill-color",AEROWAY); break;
    case "aeroway-line": paint(l,"line-color",AEROWAY); break;
    case "building": paint(l,"fill-color",BUILDING); paint(l,"fill-outline-color",BUILDING_OUT); break;

    // --- roads: native class hierarchy restored (no arterial cap), kept muted ---
    case "tunnel-simple": paint(l,"line-color",ROAD_DIM); break;
    case "road-simple": paint(l,"line-color",ROAD); break;
    case "bridge-case-simple": paint(l,"line-color",ROAD_DIM); break;
    case "bridge-simple": paint(l,"line-color",ROAD); break;

    // --- admin: faint; UZ gets the app's lit Sovereign border instead ---
    case "admin-1-boundary-bg": paint(l,"line-color",ADMIN_BG); break;
    case "admin-0-boundary-bg": paint(l,"line-color",ADMIN_BG); break;
    case "admin-1-boundary": paint(l,"line-color",ADMIN1); break;
    case "admin-0-boundary": paint(l,"line-color",ADMIN0); break;
    case "admin-0-boundary-disputed": paint(l,"line-color",ADMIN0); break;

    // --- settlements: full hierarchy, capital emphasised by brightness ---
    case "settlement-minor-label":
      show(l); paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO);
      l.minzoom = 5;                                  // towns/villages return earlier
      break;
    case "settlement-major-label":
      show(l);
      // capital (Tashkent) is the brightest place on the board; others one step down.
      paint(l,"text-color",["case",["==",["get","capital"],2],LABEL_STRONG,LABEL]);
      paint(l,"text-halo-color",HALO);
      break;
    case "settlement-subdivision-label":                // city districts
      show(l); paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO); break;
    case "state-label":                                 // regions/viloyats
      show(l); paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO); break;
    case "country-label":
      paint(l,"text-color",LABEL_STRONG); paint(l,"text-halo-color",HALO); break;
    case "continent-label":
      paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO); break;

    // --- roads + geography labels: restored, restrained ---
    case "road-label-simple":                           // street/road names
      show(l); paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO); break;
    case "airport-label":                               // airfields as landmarks
      show(l); paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO); break;
    case "waterway-label":                              // Amu Darya, Syr Darya
      show(l); paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO); break;
    case "water-point-label":                           // Aral Sea, reservoirs
      show(l); paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO); break;
    case "natural-point-label":                         // Kyzylkum, Fergana Valley
      show(l); paint(l,"text-color",LABEL_DIM); paint(l,"text-halo-color",HALO); break;
    default: break;
  }
}

const out = {
  version: src.version,
  name: "MapMonarchs Tactical Dark",
  metadata: { "mapmonarchs:purpose": "tactical dark basemap; rich Uzbek coverage; single Sovereign accent lives in-app" },
  center: [64.5853, 41.3775], zoom: 5.4, bearing: 0, pitch: 0,
  sources: src.sources, sprite: src.sprite, glyphs: src.glyphs,
  projection: src.projection || { name: "mercator" },
  layers: src.layers,
};
const hidden = src.layers.filter(l=>l.layout&&l.layout.visibility==="none").length;
fs.writeFileSync(SP + "/mm-style2.json", JSON.stringify(out));
console.log("layers:", out.layers.length, "| hidden:", hidden, "| bytes:", JSON.stringify(out).length);
