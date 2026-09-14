# Implementation Plan (approved 2026-09-14)

Goal: harden, backfill, back up, and extend intelligent-office-tracker, testing each chunk via CI (build + unit tests) and on-device verification where possible, committing to this repo after every chunk.

Chunks (in order)
 1. Docs + CI .................. PLAN.md, .github/workflows/android.yml (build + unit tests + lint)
 2. Reliability core ........... per-day alarm identity (fix), honest self-heal, WindowType enum, pure WindowStateMachine + tests, VisitRepository
 3. Time model ................. epoch-millis as source of truth, java.time formatting, remove SimpleDateFormat hazards + tests
 4. Google Maps backfill ....... improved office_times.py, run against phone Timeline, merge into seed_data.json, apply to installed app DB, verify table
 5. Auto-backup ............... BackupManager (Room DB + prefs -> zip), schedule daily (WorkManager), push to GitHub repo API + optional Google Drive; on-device verification via run-as for the debug build
 6. AI features ............... Settings for Groq + Gemini API keys; AI Insights screen (weekly patterns + natural-language Q&A) with graceful no-key state
 7. UI polish ................. split Screens.kt, ViewModels, strings.xml, NotificationCompat, import flow

Test strategy
 - GitHub Actions: assembleDebug + testDebugUnitTest + lintDebug must pass on every push.
 - Pure-logic unit tests for scheduler request codes, state machine, time formatting.
 - On-device checks (debug build on S26): backfill import, backup export, DB verification via run-as.

Branch: master (repo default). Commit per chunk; push after green (or push then fix red).

Non-Shizuku compatibility (added 2026-09-14)
 - The Android app itself requires NO Shizuku, root, or adb: tracking uses standard
   location + foreground-service + WorkManager; backup uses HTTPS to GitHub/Drive;
   import uses the standard Android file APIs. Verified: no shizuku/rikka/root
   references in android-app sources or manifest; permissions are all standard runtime
   permissions.  => app runs with full features on plain, unrooted phones.
 - Shizuku is used ONLY by agent-side tooling (office_times.py Maps UI automation,
   keepmaps.sh focus watcher, device scans). These are helpers, not app features.
