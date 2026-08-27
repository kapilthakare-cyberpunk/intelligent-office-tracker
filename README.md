# Office Tracker

Never miss a day of logging your office arrival and departure times.

**Office Tracker** is an Android app (plus an ADB helper) that automatically records
when you arrive at and leave your office each day — locally on your device. It's
engineered around one priority: **reliability** — it almost never misses a day,
even on aggressive Android OEMs (Samsung/OneUI) that kill background apps.

## What it does

- **Arrival window** (default 9:00 AM – 12:00 PM): polls location; first fix within
  100m of the office logs your arrival time.
- **Departure window** (default 6:00 PM – 9:00 PM): first fix outside 100m logs your
  departure time.
- Stores everything in a local **Room** database — no cloud, no account, fully private.
- A **History table** shows Date / Arrived / Left for quick scanning, with a live
  "Now" badge on the current day.
- A companion **ADB script** backfills your past 10 days from Google Maps Timeline.

## What "never miss a day" means — the reliability design

The app runs a **foreground location service only during the two narrow daily windows**.
This is deliberate: on Samsung/OneUI an always-on background location service is far
*more* likely to be observed and killed. Instead, reliability comes from layered,
redundant safeguards:

| Feature | What it does | Failure it prevents |
|---|---|---|
| **Integrity sweep** | Fires 13:00 & 22:00 daily; if no arrival (or arrival without departure) is logged, posts an actionable "No arrival detected" / "Departure not logged" notification with one-tap fix. | A silently missed day |
| **Self-healing `ensureArmed`** | On app open, boot, settings save, and every integrity check, verifies alarms are armed and re-schedules if the earliest window is already past. | A dead schedule after force-stop / task killer / clock change |
| **Weekday-aware scheduling** | Tracking only runs on your configured work days (default Mon–Sat, Sunday off). | Pestering on off days (which erodes trust in notifications) |
| **Redundant alarms** | Arrival window is armed with both `setExactAndAllowWhileIdle` **and** `setAlarmClock` (the highest-priority, Doze-exempt alarm type). | A single lost alarm |
| **`START_STICKY` service** | If the system kills the service mid-window, it is restarted. | Service death mid-window |
| **Boot receiver** | Re-schedules all alarms + integrity checks after reboot and app update. | Lost alarms after reboot |
| **Manual Add/Edit fallback** | Add or edit any day's arrival/departure directly from History. | The rare case nothing could be logged |

Together these convert a background-kill or lost-alarm event from an invisible gap
into a caught-and-surfaced one, and give you a manual escape hatch on top.

## Project layout

```
office-tracker/
├── android-app/                  # Android app (Kotlin / Jetpack Compose)
│   └── app/src/main/java/com/office/tracker/
│       ├── OfficeApp.kt          # Application + notification channel + DB
│       ├── db/
│       │   ├── AppDatabase.kt    # Room database
│       │   ├── OfficeVisit.kt    # Entity + DAO
│       │   └── Seeder.kt         # Import historical JSON into Room
│       ├── service/
│       │   ├── OfficeTrackingService.kt   # Foreground service + geofence-ish detection
│       │   ├── WindowScheduler.kt         # AlarmManager windows + integrity + self-heal
│       │   ├── WindowAlarmReceiver.kt     # Receives window alarms
│       │   ├── IntegrityCheckReceiver.kt  # Missed-day detection & notifications
│       │   └── BootReceiver.kt            # Re-schedules after reboot
│       ├── ui/
│       │   ├── MainActivity.kt   # Permissions + launch
│       │   └── Screens.kt        # Dashboard / History (table) / Settings
│       └── util/Prefs.kt         # DataStore preferences
├── office_times.py               # ADB script: scrape Timeline for past N days
├── generate_seed.py              # Internal helper to build seed JSON
└── seed_data.json                # Backfilled historical data
```

## Setting up the Android app

### Prerequisites

- Android Studio (or Android SDK + JDK 17)
- Android 8.0+ device
- Location + notification permissions

### Build

```bash
cd android-app
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Install over USB/Wi‑Fi ADB

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### First run & permissions

1. Launch the app; grant **location** and **notification** permissions.
2. Open **Settings** → set your office latitude/longitude and work days.
3. Tap **Start** (or just open the app — the scheduler arms the windows).

### Samsung / OneUI battery optimization (important)

To stop Samsung from killing the service:

1. **Settings → Battery → Background usage limits → Never sleeping apps** → add Office Tracker
2. **Settings → Apps → Office Tracker → Battery → Unrestricted**
3. Ensure **exact alarms** are allowed (the app requests `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`).

## Backfilling history from Google Maps Timeline

1. Open Google Maps on the phone → **You / Profile → Your Timeline → Day view**.
2. Run the ADB script (USB or Wi‑Fi ADB):

```bash
python3 office_times.py --days 10
```

3. To load the produced JSON into the app, push it and tap **Import seed** on the History screen:

```bash
adb push /tmp/office_seed.json /sdcard/Android/data/com.office.tracker/files/seed.json
```

## Configuring the app

In **Settings** you can change:

- Office latitude / longitude (and the arrival-detection radius default 100 m)
- Arrival and departure window hours
- **Work days** (which days to track)
- Check interval

## Technology

- **Kotlin + Jetpack Compose (Material 3)**
- **Room** (local persistence)
- **Google Play Services Location** (FusedLocationProvider)
- **AlarmManager** (exact + alarm-clock scheduling)
- **DataStore** (preferences)

## License

MIT
