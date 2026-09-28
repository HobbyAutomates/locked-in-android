-- v2.16 (schema_v40): profile cover presets.
-- Additive and idempotent: one nullable text column. Safe to re-run. Undo with docs/revert_v40.sql.
--
-- NOT APPLIED YET. The v2.16 clients tolerate it missing: Android reads profiles with select=* and
-- only PATCHes cover_preset when the column came back (Profile.coverSupported); until then the
-- choice lives on the device (SharedPreferences "cover_v216" / web localStorage "li-cover-preset").
--
-- Values are the shared preset ids (web src/lib/covers.ts, Android util/Covers.kt), e.g.
-- 'plates-light' (#01, the default), 'plates-dark', 'dumbbells-light' ... 'ember-dark' (34 in all).
-- The web repo's docs/schema_v40.sql is the one to apply if the two copies ever differ.

alter table bandlog.profiles add column if not exists cover_preset text;

alter table bandlog.profiles drop constraint if exists profiles_cover_preset_len;
alter table bandlog.profiles add constraint profiles_cover_preset_len
  check (cover_preset is null or char_length(cover_preset) between 1 and 40);
