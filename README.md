# Band Log (Android)

Native Kotlin + Jetpack Compose app for the Band Log tracker. Same Supabase project and `bandlog`
schema as the web app (`C:\Users\sohum\band-log-app`, live at https://web-production-ff1cf.up.railway.app).

- Sign in once with the Supabase email/password account; the session is stored on the phone and
  refreshed automatically.
- Meals: dictate into the box (Wispr Flow), tap **Log meal**. The text goes to the web app's
  `/api/parse-meal` with the user's token; Haiku + the food table return priced items; edit grams; Save.
- Workouts: muscles, band level, kg, minutes, exercises, notes. Calendar shows muscle dots per day.
- Progress: week streak, day streak, sessions/week bars, protein last 14 days, rest per muscle.
- Self-update: reads `app/bandlog-version.json` from Supabase Storage (public bucket `app`).

## Build & ship
```
./gradlew assembleRelease && ./publish.sh
```
Bump `versionCode` / `versionName` in `app/build.gradle.kts` first. `publish.sh` uploads
`BandLog-<versionCode>.apk` and rewrites `bandlog-version.json`; phones with the app see the
"Update available" dialog on next launch (or Settings → Check for updates).

Config lives in `local.properties` (gitignored): `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `API_BASE`.
