# IFTTT Applet Setup — Office Arrival & Departure

> Companion automation to the **intelligent-office-tracker** Android app.
> IFTTT is used as a *best-effort* push/Telegram/Sheets notifier — it is **not** a
> precise timestamp logger (see caveats at the end). Exact times are handled by the
> native app / Tasker.
>
> **Office location (geofence):**
>
> | Field | Value |
> |---|---|
> | Place | Work (Primes & Zooms) |
> | Latitude | `18.531555` |
> | Longitude | `73.842193` |
> | Radius | ~ `100 m` |
>
> **Status:** You have NOT created these applets yet. Follow the steps below to do so.
> Total: **2 applets** (Arrival + Departure).

---

## Prerequisites

- IFTTT app installed, signed in.
- (Recommended) Google account connected for Google Sheets, and/or Telegram authorized.
- IFTTT background permissions set (see "After creating both applets").

---

## Applet 1 — Departure (do the evening one first)

### If This (trigger)

1. In IFTTT, go to **Create** → **Classic**.
2. Tap **"+ Add"** next to **"If This"**.
3. Search for **`Location`** → tap the **Location** service (NOT "Group Location").
4. Choose the trigger: **"You exit an area"**.
5. **Choose the office area:**
   - Tap the location/map-pin field.
   - Enter the address **"Work, Primes & Zooms, Model Colony, Shivajinagar, Pune"**
     or the coordinates:
     - Latitude `18.531555`
     - Longitude `73.842193`
   - Drop the pin; set the radius to about **100 m**.
6. Tap **Save** (checkmark).

### Then That (action) — choose ONE or TWO:

**Option A — Telegram message (recommended for your use):**
1. Tap **"+ Add"** next to **"Then That"**.
2. Search **"Telegram"** → **Telegram** → **"Send message"**.
3. Authorize IFTTT with Telegram if prompted.
4. Pick the chat (e.g. a chat with yourself / "Saved Messages").
5. Message text:
   ```
   🚪 Left office at {{OccurredAt}}
   Location: {{Latitude}}, {{Longitude}}
   ```
6. **Continue → Finish.**

**Option B — Notification:**
1. **"+ Add"** → **"Notifications"** → **"Send a notification from the IFTTT app"**.
2. Body:
   ```
   🚪 Left office at {{OccurredAt}}
   ```
3. **Continue → Finish.**

**Option C — Google Sheets mirror:**
1. **"+ Add"** → **"Google Sheets"** → **"Add row to spreadsheet"**.
2. Connect Google if prompted.
3. Columns: `Date`, `Time`, `Event` from ingredients `{{OccurredAt}}`, `{{Latitude}}`,
   `{{Longitude}}`.
4. **Continue → Finish.**

### Finish Applet 1
- Name it: **`Office — Departure`**.
- Tap **Finish / Turn on**.

---

## Applet 2 — Arrival

Repeat the same flow with these changes:

### If This (trigger)
1. **"+ Add"** → search **"Location"** → **Location** service.
2. Choose the trigger: **"You enter an area"**.
3. **Same office location:** `18.531555, 73.842193`, radius ~**100 m**.
4. **Save.**

### Then That (action)
Use the **same action service(s)** as Applet 1 (Telegram / Notification / Sheets):
```
🏢 Arrived at office at {{OccurredAt}}
Location: {{Latitude}}, {{Longitude}}
```

### Finish Applet 2
- Name: **`Office — Arrival`**.
- **Finish / Turn on.**

---

## After creating both applets

1. **Location permission (critical for background firing):**
   - **Settings → Apps → IFTTT → Permissions → Location → Allow all the time.**
2. **Keep IFTTT alive (Samsung/OneUI):**
   - **Settings → Battery → Background usage limits → Never sleeping apps → IFTTT.**
   - **Settings → Apps → IFTTT → Battery → Unrestricted.**
3. **Verify both applets are ON** in **My Applets** (not paused).

---

## Testing

- Enter the office geofence → expect **"🏢 Arrived at office…"**.
- Exit it → expect **"🚪 Left office…"**.

---

## Important caveats (please read)

- **IFTTT's Android Location service is coarse.** It uses battery-friendly
  "significant change" checks and often fires **minutes late** or, in background,
  **sometimes not at all**. Treat IFTTT as a *best-effort* alert, not a precise log.
- `{{OccurredAt}}` is when IFTTT fired, not the exact second you crossed the geofence.
- For **exact** arrival/departure timestamps, rely on the **intelligent-office-tracker**
  app (foreground service + exact alarms) or **Tasker**.
- Radius of 100 m is a starting point; if IFTTT misses events, increase to 150–250 m
  (at the cost of slightly earlier/later triggers).

---

## File location

This document is saved at both:

- `~/Desktop/ifttt-office-tracker-setup.md`
- `~/intelligent-office-tracker/automation/ifttt-office-tracker-setup.md`
