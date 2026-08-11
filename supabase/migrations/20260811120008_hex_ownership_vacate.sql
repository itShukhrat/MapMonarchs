-- Territory must survive the deletion of the player who held it: the game world
-- is shared, and cascading a personal deletion into the board would rewrite other
-- players' view of it. Switch owner_id from ON DELETE CASCADE to ON DELETE SET
-- NULL so a deleted account VACATES its ground (owner becomes null = neutral,
-- rendered as not-mine for everyone) instead of erasing it. Personal tables keep
-- their CASCADE and are removed with the account.
alter table hex_ownership alter column owner_id drop not null;
alter table hex_ownership drop constraint hex_ownership_owner_id_fkey;
alter table hex_ownership
  add constraint hex_ownership_owner_id_fkey
  foreign key (owner_id) references profiles(id) on delete set null;
