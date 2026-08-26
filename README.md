# office-tracker

Track your daily office arrival and departure times. Two approaches included:

1. **ADB Script** — Pulls historical data from Google Maps Timeline
2. **Android App** — Logs arrival/departure in real-time via geofencing

## 1. ADB Script (`office_times.py`)

Scrapes Google Maps Timeline via ADB to get past office visit times.

### Prerequisites

- **Python 3.10+**
- **ADB** installed and in PATH (`brew install android-platform-tools` on macOS)
- **Android phone** connected via USB with USB debugging enabled
- **Google Maps** with **Location History / Timeline** enabled
- Google Maps must be open on the **Timeline > Day** view before running

### Usage

```bash
python3 office_times.py           # past 10 days
python3 office_times.py --days 30 # custom range
```

### Configuration

| Constant | Default | Description |
|---|---|---|
| `OFFICE_NAME` | `"Work (Primes & Zooms)"` | Place name in Google Maps |
| `SCROLL_AREA_TOP` | `1398` | Timeline scroll area top Y |
| `SCROLL_AREA_BOTTOM` | `2340` | Timeline scroll area bottom Y |

---

## 2. Android App (`android-app/`)

A Kotlin/Jetpack Compose app that runs a foreground service during two daily time windows to detect when you arrive at and leave the office.

### How It Works

- **Arrival window** (default 9:00 AM – 12:00 PM): Checks location every 30 seconds. First detection within 100m of office = arrival logged.
- **Departure window** (default 6:00 PM – 9:00 PM): Checks location every 30 seconds. First detection outside 100m = departure logged.
- **Foreground service** with `START_STICKY` keeps the process alive.
- **AlarmManager** re-triggers windows even if the service was killed.
- **Boot receiver** re-schedules everything after device reboot.
- **Room database** stores all visits locally.

### Building

```bash
cd android-app
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

### Key Files

| File | Purpose |
|---|---|
| `service/OfficeTrackingService.kt` | Foreground service + location detection |
| `service/WindowScheduler.kt` | AlarmManager-based window scheduling |
| `service/WindowAlarmReceiver.kt` | Receives alarm broadcasts |
| `service/BootReceiver.kt` | Re-schedules after reboot |
| `db/OfficeVisit.kt` | Room entity + DAO |
| `ui/Screens.kt` | Compose UI (Dashboard, History, Settings) |
| `util/Prefs.kt` | DataStore preferences |

### Samsung Battery Optimization

To prevent Samsung from killing the service:

1. **Settings → Battery → Background usage limits → Never sleeping apps** → add Office Tracker
2. **Settings → Apps → Office Tracker → Battery → Unrestricted**

### Customization

The Settings screen in the app lets you configure:
- Office latitude/longitude
- Arrival and departure time windows (hour of day)
- Check interval

---

## Example Output (ADB Script)

```
=======================================================
Day                  Arrived         Last Left
-------------------------------------------------------
Today                10:18 am        Still here
Yesterday            2:40 pm         6:51 pm
Mon, 24 Aug 2026     10:50 am        7:17 pm
Sun, 23 Aug 2026     —               —
Sat, 22 Aug 2026     9:58 am         6:49 pm
=======================================================
```

## License

MIT
