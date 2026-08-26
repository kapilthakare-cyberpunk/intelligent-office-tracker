# office-tracker

Track your daily office arrival and departure times from Google Maps Timeline via ADB.

## How It Works

The script connects to your Android phone over ADB, opens Google Maps Timeline, and scrapes the UI hierarchy (`uiautomator`) to extract your office visit times for each day. It navigates backwards through your timeline day-by-day.

## Prerequisites

- **Python 3.10+** (uses `list[str]` type hints)
- **ADB** installed and in your PATH (`brew install android-platform-tools` on macOS)
- **Android phone** connected via USB with USB debugging enabled
- **Google Maps** with **Location History / Timeline** enabled
- Google Maps must be open on the **Timeline > Day** view before running

## Usage

```bash
# Default: past 10 days
python3 office_times.py

# Custom range
python3 office_times.py --days 30
```

## Configuration

Edit the constants at the top of `office_times.py` to customize:

| Constant | Default | Description |
|---|---|---|
| `OFFICE_NAME` | `"Work (Primes & Zooms)"` | The place name Google Maps uses for your office |
| `SCROLL_AREA_TOP` | `1398` | Y-coordinate of the timeline scroll area top |
| `SCROLL_AREA_BOTTOM` | `2340` | Y-coordinate of the timeline scroll area bottom |
| `SCROLL_STEP` | `600` | Pixels to scroll per swipe |

### Finding Your Office Name

1. Open Google Maps > Timeline > Day
2. Look at your office visit entry — the name after "Visited" or in the visit card is what you need
3. Update `OFFICE_NAME` in the script

### Adjusting Scroll Coordinates

If the timeline layout differs on your phone:

1. Run `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml /tmp/ui.xml`
2. Open `/tmp/ui.xml` and find the scrollable container's `bounds`
3. Update `SCROLL_AREA_TOP` and `SCROLL_AREA_BOTTOM`

## Example Output

```
Fetching office times for the past 10 days...

Processing Today...           Arrived: 10:18 am  |  Left: Still here
Processing Yesterday...       Arrived: 2:40 pm   |  Left: 6:51 pm
Processing Mon, 24 Aug...     Arrived: 10:50 am  |  Left: 7:17 pm
Processing Sun, 23 Aug...     Arrived: —         |  Left: —
Processing Sat, 22 Aug...     Arrived: 9:58 am   |  Left: 6:49 pm

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

## Limitations

- Requires Google Maps to be open on the Timeline Day view before execution
- Depends on the UI layout — may break if Google Maps updates the Timeline UI
- Scroll coordinates are hardcoded for a specific screen resolution (1080x2340)
- Only tracks one office location (the `OFFICE_NAME` constant)

## License

MIT
