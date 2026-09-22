# Band Log (Android)

Native Kotlin + Jetpack Compose app for the Band Log tracker. Same Supabase project and `bandlog`
schema as the web app (`C:\Users\sohum\band-log-app`, live at https://web-production-ff1cf.up.railway.app).

- Sign in once with the Supabase email/password account; the session is stored on the phone and
  refreshed automatically.
- **Home**: calories-left ring, macro cards that flip to "Protein **over**" past the target, week
  strip, steps ring against your own step goal, Health Connect steps + burned calories, refresh button.
- **Meals**: dictate into the box (Wispr Flow), tap **Log meal**. The text goes to the web app's
  `/api/parse-meal` with the user's token; Haiku + the food table return priced items; edit grams; Save.
- **Workouts**: muscles, band level, kg, minutes, exercises, notes. Calendar shows muscle dots per day.
- **Onboarding**: after sign-up (and on any sign-in where weight, height or birthday are still
  empty) the app runs a Cal AI-shaped first run instead of the tab shell — gender, workouts a week,
  goal, height & weight, birthday, target weight on a draggable ruler, pace on the sloth/rabbit/
  cheetah slider, then what's getting in the way. It builds a plan on screen and saves the whole
  profile plus the Auto Generate targets in one write. "Skip for now" on the first screen.
- **Scan**: photograph a nutrition or ingredients label. ML Kit reads it **on the phone** (~8 s
  end to end); the recognised words show in an editable "What I read" card so a misread digit can
  be fixed before analysing. Only a short read (<120 characters) also uploads the photo. The report
  is an infographic: score out of 10 in a ring, verdict meter, one serving as a share of your day,
  sugar drawn as teaspoons, salt as a share of 2000 mg, protein gauge, traffic-light ingredients.
- **Progress**: weight card with sparkline, week/day streak, badges card, Weekly Energy
  (consumed vs burned line chart), sessions/week bars, protein bars, rest per muscle.
- **Badges**: twelve hex medals across three tiers — training streak (Rookie 3 → Immortal 1000),
  meals logged (Forking Around 5 → The Logfather 500) and calorie-goal days (One Hit Wonder 1 →
  Bullseye 30). Earned medals can be shared.
- **Profile tab**:
  - *Account* — Personal details (weight, height, DOB, gender, daily step goal), Preferences
    (appearance, Health Connect, add burned calories back).
  - *Goals & Tracking* — Edit Nutrition Goals (four macro rows + **✨ Auto Generate Goals**:
    Mifflin-St Jeor BMR × activity, adjusted for your goal speed), Goal & current weight
    (Lose/Maintain/Gain + a 0.1–1.5 kg/week speed slider), Tracking Reminders, Weight history,
    Ring colours explained.
  - *Support* — Request a feature, Check for updates, Sign out.
- **Tracking reminders**: Breakfast / Lunch / Snack / Dinner / End of day, each with its own time.
  Exact alarms via `AlarmManager`, re-armed nightly and after a reboot; tapping the notification
  opens Log → Meal. Stored in `profiles.reminders` and mirrored to SharedPreferences.
- **Weight history**: `bandlog.weight_log` rows, with the change since your first weigh-in; logging
  also updates `profiles.weight_kg`.
- Pure-black dark mode (`#000000` page, `#1C1C1E` cards).
- Self-update: reads `app/bandlog-version.json` from Supabase Storage (public bucket `app`).

## Build & ship
```
./gradlew assembleRelease && ./publish.sh
```
Bump `versionCode` / `versionName` in `app/build.gradle.kts` first. `publish.sh` uploads
`LockedIn-<versionCode>.apk` and rewrites `bandlog-version.json`; phones with the app see the
"Update available" dialog on next launch (or Profile → Check for updates).

Config lives in `local.properties` (gitignored): `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `API_BASE`.
