# Office Automation — Tasker + IFTTT (stand-in for the Office Tracker app)

This folder contains a ready-to-configure version of the **intelligent-office-tracker**
logic using **Tasker** and **IFTTT** instead of the native Android app.

Nothing here is tracked in git — it's a companion setup, not part of the app repo.

> **Honest reliability note (read first):**
> The app itself (foreground service + exact alarms) is the *most reliable* way to
> capture precise arrival/departure times on Android. Automation tools fall into two
> camps for this job:
>
> * **Tasker** — reads fine location / cell-tower / WiFi and writes exact timestamps
>   locally to a file. *Reliable enough* to be a genuine logger.
> * **IFTTT's built-in Android "Location" service** — uses coarse, battery-friendly
>   "significant change" checks; **unreliable on Android** (fires late or not at all
>   in the background). Treat IFTTT as a *best-effort notifier*, never the primary log.
>
> Recommended: **Tasker as the logger** + (optional) **IFTTT Webhooks → Google Sheets**
> as a cloud mirror and notification.

---

## Office location used

- **Name:** Work (Primes & Zooms)
- **Latitude:** `18.531555`
- **Longitude:** `73.842193`
- **Radius:** `100 m`

---

## Option A — Tasker (recommended, reliable logger)

### 1. Install & prep

1. Install **Tasker** (Play Store: `net.dinglisch.android.taskerm`).
2. Open Tasker → accept the superuser/Accessibility setup if offered.
3. Grant Tasker permissions:
   * **Location** (allow "all the time") so geofences work in the background.
   * **Notifications** (so the "Logged" toast/flash can appear).
4. Keep Tasker alive:
   * Samsung/OneUI: **Settings → Battery → Background usage limits → Never sleeping
     apps → add Tasker**. Also **Apps → Tasker → Battery → Unrestricted**.

### 2. Build the two logging tasks

Under the **Tasks** tab, add a task **"Log Arrival"** with these actions:

| # | Action | Settings |
|---|---|---|
| 1 | **Variable Set** | `%office_date` = `%DATE` |
| 2 | **Variable Set** | `%office_time` = `%TIME` |
| 3 | **Variable Set** | `%row` = `%office_date,%office_time,ARRIVAL` |
| 4 | **Write File** | File `/sdcard/Tasker/office_log.csv`<br>Text `%row`<br>☑ Append<br>☑ Insert Linefeed if needed<br>Create parent dir: ☑ |

Add task **"Log Departure"** identically but with `DEPARTURE`.

> To avoid double-logging, optionally gate each with a "Variable Clear/Set" check:
> set `%logged_today` on arrival, clear on departure, and have each task run only if
> `%logged_today` doesn't already reflect that state.

### 3. Build the two geofence profiles

Under **Profiles**, add:

**Profile "At Office"** → **Location** → set:
- Enable "Use GPS / Network", radius `100`, and **center on office
  (18.531555, 73.842193)**.
- Tap the location pin to place it on the map at the office.
- Save.
→ link entry task **"Log Arrival"**.

**Profile "Left Office"** → **Location** → check **"Inverse"** (fires when you leave
the geofence), same center/radius.
→ link entry task **"Log Departure"**.

### 4. Verify

Entering the office geofence appends an `ARRIVAL` row; leaving appends `DEPARTURE`:

```
date,time,event
2026-08-27,14:40:01,ARRIVAL
2026-08-27,18:49:33,DEPARTURE
```

Open `/sdcard/Tasker/office_log.csv` to confirm.

---

## Option B — IFTTT (best-effort notifier / cloud mirror)

Do **not** use IFTTT's native Android Location trigger for timing (see note above).
Instead, have Tasker **send a Webhook** on arrival/departure; IFTTT writes it to
**Google Sheets** and/or pushes a notification.

1. Create an IFTTT account; connect **Google Sheets** and **Webhooks**.
2. Create two applets (**Webhooks** trigger → **Google Sheets** action, "Add row to
   spreadsheet"), one for each event.
3. Copy your Webhooks key from `https://ifttt.com/maker_webhooks`.
4. In Tasker, add an **HTTP Request** action to each logging task:

   ```
   POST https://maker.ifttt.com/trigger/office_arrived/with/key/YOUR_KEY
   POST https://maker.ifttt.com/trigger/office_left/with/key/YOUR_KEY
   ```

5. Enter the geofence → a row lands in your Google Sheet.

---

## Notes & customization

- **Different office location?** Replace the latitude/longitude/radius above.
- **Add a "home" geofence?** Duplicate either profile with home coords
  (`18.4689666, 73.8676631`) and log `ARRIVED_HOME` / `LEFT_HOME`.
- **Log to the app's DB instead of CSV?** Not directly — the app reads its own Room
  DB, not a CSV. If you want Tasker data inside the app, push a `seed.json` and use
  the app's **Import seed** button, or export the CSV separately.
