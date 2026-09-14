# Pending Items — intelligent-office-tracker

Last updated: 2026-09-14 | Branch: master | CI: green at 81cdeed (assembleDebug + testDebugUnitTest + lintDebug)

Approved plan lives in `PLAN.md`. Items below are in priority order; commit + push after each, keep CI green.

## 1. Google Maps backfill (PAUSED on request — chunk 4)
- Scope: last 20 days (~10–20 min runtime), only days with a missed office visit are merged into the DB.
- Resume on the S26:
  - Ensure Shizuku is up: `shizuku id`; if dropped, `intent '{"start":"activity","component":"gptos.intelligence.assistant/app.anyclaw.MainActivity"}'` then poll `shizuku id` until uid=2000.
  - Start focus watcher: `nohup /root/keepmaps.sh &` (HOME-first pattern; host agent WebView steals focus otherwise).
  - Run `python3 office_times.py --days 20` from this repo (shizuku transport, on-device grep, self-healing dumps).
  - Validate `/root/office_times_results.json`.
- Merge results into `seed_data.json` via `generate_seed.py` (respect existing rows).
- Push seed into installed debug app: rebuild APK, `adb install`, `run-as com.office.tracker` to drop seed into files dir, run importer, verify DB table.
- Commit chunk 4 + generate the HTML phone-friendly report (existing pattern in phone-agent-setup).

## 2. Backup Settings UI (chunk 5 — backend done, UI missing)
- Add Settings UI: GitHub token / owner / repo / branch fields, backup_enabled toggle, "Backup now" button + last-backup time.
- Wire to existing `Prefs` keys (`gh_token`, `gh_owner`, `gh_repo`, `gh_branch`, `backup_enabled`, `backup_last`).
- Verify on debug build: local JSON export + GitHub push (Contents API create/update).
- NEVER commit real tokens. A GitHub classic token was shared earlier in chat
  (not written here); keep it out of git and rotate it when convenient.

## 3. AI features (chunk 6 — not started)
- Settings fields for Groq API key + Gemini API key.
- AI Insights screen: weekly patterns (avg arrival/departure, attendance) + natural-language Q&A over visit history.
- Graceful no-key state (hide/disable AI panel with a hint).

## 4. UI polish (chunk 7 — not started)
- Split `Screens.kt` into focused screens, move logic into ViewModels.
- Move hardcoded strings to `strings.xml`.
- `NotificationCompat` for notifications; review import/data-import flow.

## 5. Optional follow-ups
- Google Drive auto-backup destination (plan mentions "GitHub + optional Google Drive").
- On-device verification checklist for S23 Ultra (home-automation phone) if it runs the tracker too.

## Working notes
- This agent runs inside proot on the S26; adb = `shizuku` (real `/usr/bin/adb` on PATH is stale/offline).
- Bridge stdout cap ~64KB: never `cat` big XML dumps; parse on-device with grep (already done in office_times.py).
- CI uses `gradle/actions/setup-gradle@v4` with Gradle 8.11.1 (manual `gradle`, not `./gradlew`); committed wrapper jar is a dummy.
- Lint report on failure is printed by the workflow (`cat app/build/reports/lint-results-debug.txt`).
- User preference: every generated report should also get a phone-friendly HTML version opened in the device browser.
