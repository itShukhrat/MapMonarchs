// ============================================================================
// MapMonarchs — attempt_capture Edge Function
//
// WHY THIS EXISTS: the project's Postgres 17 has no h3 extension, so the cell
// cannot be derived in SQL. This function is the ONE place the h3 dependency
// lives. It preserves the anti-cheat contract exactly:
//
//   client --(raw lat/lng only)--> this function (server, trusted)
//        --> derives H3 cell with h3-js --> calls apply_capture() in Postgres
//
// The client never names its own cell. apply_capture() is EXECUTE-revoked from
// clients, so nobody can skip this function and submit a hand-picked hex.
//
// Env vars (auto-injected by Supabase Edge Functions): SUPABASE_URL,
// SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY.
// ============================================================================
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { latLngToCell } from "https://esm.sh/h3-js@4";

const RES = 9; // H3 resolution — must match schema.sql (_res constant int := 9)

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, reason: "method_not_allowed" }, 405);

  const authHeader = req.headers.get("Authorization");
  if (!authHeader) return json({ ok: false, reason: "not_authenticated" }, 401);

  const url = Deno.env.get("SUPABASE_URL")!;
  const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

  // Verify the caller's JWT and read their id. Never trust a client-sent uid.
  const asUser = createClient(url, anon, {
    global: { headers: { Authorization: authHeader } },
  });
  const { data: { user }, error: authErr } = await asUser.auth.getUser();
  if (authErr || !user) return json({ ok: false, reason: "not_authenticated" }, 401);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ ok: false, reason: "bad_request" }, 400);
  }

  const lat = body["_lat"];
  const lng = body["_lng"];
  if (typeof lat !== "number" || typeof lng !== "number" ||
      !Number.isFinite(lat) || !Number.isFinite(lng)) {
    return json({ ok: false, reason: "bad_request" }, 400);
  }
  const accuracy = typeof body["_accuracy"] === "number" ? body["_accuracy"] : null;
  const speed = typeof body["_speed"] === "number" ? body["_speed"] : null;
  const isMock = body["_is_mock"] === true;

  // h3-js latLngToCell takes (lat, lng) — NOT the (lng, lat) point order the
  // Postgres h3_latlng_to_cell wanted. This order is the correct one for h3-js.
  const h3 = latLngToCell(lat, lng, RES);

  // Service-role client applies the capture atomically (row lock lives in SQL).
  const admin = createClient(url, service);
  const { data, error } = await admin.rpc("apply_capture", {
    _uid: user.id,
    _h3: h3,
    _lat: lat,
    _lng: lng,
    _accuracy: accuracy,
    _speed: speed,
    _is_mock: isMock,
  });

  if (error) {
    return json({ ok: false, reason: "backend_failure", detail: error.message }, 500);
  }
  return json(data, 200);
});
