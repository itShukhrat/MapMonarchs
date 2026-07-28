-- ============================================================================
-- MapMonarchs -- avatar storage for the Pro profile picture
--
-- WHY THIS EXISTS: the one Pro benefit that needs storage is a real profile
-- picture in place of the crest. This creates the bucket and the policies so the
-- capability is server-authoritative before any client upload UI is built.
--
-- THE PRO GATE LIVES HERE, NOT ONLY IN THE APP. A free player who bypassed the
-- client and called the Storage API directly must still be refused. So the write
-- policies require an ACTIVE Pro entitlement, checked against public.entitlements
-- -- the same table the app reads, written only by verified purchase/grant. This
-- makes "profile picture is Pro" a fact the database enforces, not a client-side
-- suggestion, exactly like capture and badges.
--
-- READ IS PUBLIC: an avatar is meant to be seen (on your profile, by others who
-- view you), so the bucket is public, like the map. Writes are gated; reads are
-- not.
--
-- PATH CONVENTION: objects are stored under "<uid>/<file>", so a player can only
-- ever write inside their own folder. Idempotent.
-- ============================================================================

insert into storage.buckets (id, name, public)
values ('avatars', 'avatars', true)
on conflict (id) do nothing;

-- Public read of avatars (bucket is public; this makes the intent explicit for
-- any authenticated listing path too).
drop policy if exists "avatar read" on storage.objects;
create policy "avatar read" on storage.objects
  for select using (bucket_id = 'avatars');

-- Insert: own folder only, AND holding an active Pro entitlement.
drop policy if exists "avatar owner insert pro" on storage.objects;
create policy "avatar owner insert pro" on storage.objects
  for insert with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
    and exists (
      select 1 from public.entitlements e
      where e.user_id = auth.uid()
        and e.tier = 'pro'
        and (e.expires_at is null or e.expires_at > now())
    )
  );

-- Update: same gate.
drop policy if exists "avatar owner update pro" on storage.objects;
create policy "avatar owner update pro" on storage.objects
  for update using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
  ) with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
    and exists (
      select 1 from public.entitlements e
      where e.user_id = auth.uid()
        and e.tier = 'pro'
        and (e.expires_at is null or e.expires_at > now())
    )
  );

-- Delete: an owner may always remove their own avatar (no Pro check -- letting a
-- lapsed player clean up their own file is not a paid capability).
drop policy if exists "avatar owner delete" on storage.objects;
create policy "avatar owner delete" on storage.objects
  for delete using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.uid()::text
  );
