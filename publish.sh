#!/usr/bin/env bash
# Publish the current release APK to Supabase Storage so the in-app updater finds it.
# Reads the service key from ~/.reminders-publish.env (same project as the Reminders app).
# Run AFTER building the release:  ./gradlew assembleRelease  &&  ./publish.sh
set -euo pipefail

ENV_FILE="$HOME/.reminders-publish.env"
[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE (needs SUPABASE_URL + SUPABASE_SERVICE_KEY)"; exit 1; }
# shellcheck disable=SC1090
source "$ENV_FILE"

APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "No release APK — run ./gradlew assembleRelease first."; exit 1; }

VNAME=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | grep -oE '"[^"]+"' | tr -d '"')
VCODE=$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')
BASE="${SUPABASE_URL%/}/storage/v1/object/app"
PUBLIC="${SUPABASE_URL%/}/storage/v1/object/public/app"

echo "Publishing Band Log v$VNAME (versionCode $VCODE)…"

APK_NAME="BandLog-$VCODE.apk"
curl -fsS -X POST "$BASE/$APK_NAME" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_KEY" -H "x-upsert: true" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$APK" >/dev/null

printf '{"versionCode": %s, "versionName": "%s", "url": "%s/%s"}' "$VCODE" "$VNAME" "$PUBLIC" "$APK_NAME" > /tmp/bandlog-version.json
curl -fsS -X POST "$BASE/bandlog-version.json" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_KEY" -H "x-upsert: true" \
  -H "cache-control: max-age=0" \
  -H "Content-Type: application/json" \
  --data-binary @/tmp/bandlog-version.json >/dev/null
rm -f /tmp/bandlog-version.json

echo "Done. Latest: v$VNAME ($APK_NAME)  ->  $PUBLIC/$APK_NAME"
