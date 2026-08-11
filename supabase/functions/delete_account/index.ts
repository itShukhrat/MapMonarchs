import { createClient } from "npm:@supabase/supabase-js@2";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

// Deletes the CALLER's own account. The account to delete is taken from the
// verified JWT, never from the request body, so a user can only ever delete
// themselves.
//
// The database does the data work atomically via foreign keys:
//   auth.users --DELETE--> profiles (CASCADE)
//     profiles --CASCADE--> location_events, record_events, user_badges,
//                           entitlements  (personal data, removed)
//     profiles --SET NULL--> hex_ownership.owner_id  (territory VACATED, i.e.
//                           preserved on the board but no longer tied to anyone)
// So a single deleteUser() severs the identity, erases the precise-location
// trail and all personal rows, and leaves the shared game world intact.
Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, reason: "method_not_allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

  const token = req.headers.get("Authorization")?.split(" ").pop()?.trim();
  if (!token || token === anon) return json({ ok: false, reason: "not_authenticated" }, 401);
  const { data: { user }, error: uerr } = await createClient(url, anon).auth.getUser(token);
  if (uerr || !user) return json({ ok: false, reason: "not_authenticated" }, 401);

  const admin = createClient(url, service);
  const { error } = await admin.auth.admin.deleteUser(user.id);
  if (error) return json({ ok: false, reason: "delete_failed" }, 500);

  return json({ ok: true }, 200);
});
