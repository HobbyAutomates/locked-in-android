-- Undo docs/schema_v40.sql (v2.16 profile cover presets). The apps fall back to the on-device choice.

alter table bandlog.profiles drop constraint if exists profiles_cover_preset_len;
alter table bandlog.profiles drop column if exists cover_preset;
