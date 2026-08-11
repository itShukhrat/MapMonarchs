// ============================================================================
// MapMonarchs — territory_geometry Edge Function
//
// WHY THIS EXISTS: the client must draw owned territory, which means turning each
// server-issued H3 cell id into a boundary polygon. Doing that on the client
// needed a native H3 library (h3-java), whose prebuilt .so is not 16 KB-aligned
// and cannot ship to modern Android. H3 already lives server-side here (the
// attempt_capture function derives cells with h3-js), so boundary conversion
// belongs here too. This returns ready-to-draw GeoJSON; the client renders it and
// decides nothing.
//
// AUTHORITY IS UNCHANGED. Ownership is still decided only by apply_capture writing
// hex_ownership. This endpoint READS that authoritative table and converts it. It
// never trusts a client-sent owner, cell, or geometry. The optional caller JWT is
// used ONLY to tag each feature `mine` so the client can colour its own ground --
// the server makes that call, the client does not.
//
// PUBLIC READ. hex_ownership is world-readable (the map is public), so this is an
// unauthenticated endpoint (verify_jwt = false): a signed-out player still sees
// the board, with everything tagged not-mine. A valid user JWT, when present,
// personalises the `mine` flags.
//
// Env vars auto-injected by Supabase: SUPABASE_URL, SUPABASE_ANON_KEY,
// SUPABASE_SERVICE_ROLE_KEY.
// ============================================================================
import { createClient } from "npm:@supabase/supabase-js@2";
import { cellToBoundary } from "npm:h3-js@4";

// A global cap. Territory is empty today; when it grows, a viewport-bounded query
// is the next step (see repo notes) -- this keeps the first payloads bounded and
// is far above any realistic near-term hold count.
const MAX_CELLS = 3000;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

Deno.serve(async (req) => {
  const url = Deno.env.get("SUPABASE_URL")!;
  const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

  // Optional caller identity, ONLY to tag `mine`. No JWT (or the anon key) => the
  // whole board renders as not-mine, which is correct for a signed-out viewer.
  let uid: string | null = null;
  const authHeader = req.headers.get("Authorization");
  if (authHeader) {
    const token = authHeader.split(" ").pop()?.trim();
    if (token && token !== anon) {
      try {
        const { data: { user } } = await createClient(url, anon).auth.getUser(token);
        uid = user?.id ?? null;
      } catch { /* unauthenticated view; leave uid null */ }
    }
  }

  // service_role read of the authoritative table (world-readable anyway).
  const admin = createClient(url, service);
  const { data, error } = await admin
    .from("hex_ownership")
    .select("h3_index, owner_id, strength")
    .limit(MAX_CELLS);
  if (error) {
    return json({ type: "FeatureCollection", features: [], error: "backend_failure" }, 500);
  }

  const features: unknown[] = [];
  for (const row of (data ?? [])) {
    let ring: number[][];
    try {
      // formatAsGeoJson=true => [lng, lat] pairs, ring already closed.
      ring = cellToBoundary(row.h3_index as string, true) as unknown as number[][];
    } catch {
      continue; // a malformed cell costs one hex, never the response
    }
    if (!Array.isArray(ring) || ring.length < 4) continue;
    if (ring.some((p) => !Number.isFinite(p[0]) || !Number.isFinite(p[1]))) continue;
    features.push({
      type: "Feature",
      properties: {
        h3: row.h3_index,
        mine: uid !== null && row.owner_id === uid,
        strength: row.strength,
      },
      geometry: { type: "Polygon", coordinates: [ring] },
    });
  }

  // Empty territory is an empty FeatureCollection, never an error.
  return json({ type: "FeatureCollection", features }, 200);
});
