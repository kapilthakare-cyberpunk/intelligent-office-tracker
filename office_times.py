#!/usr/bin/env python3
"""
Get office arrival and last departure times for the past N days
from Google Maps Timeline via Shizuku UI automation (runs on-device in proot).

Prerequisites:
  - Shizuku running (uid 2000/shell) on the phone
  - Google Maps installed and signed in (Kapil Thakare)
  - Location History enabled

Usage:
  python3 office_times.py [--days 10]
"""

import re
import subprocess
import sys
import time
import urllib.parse
from datetime import datetime, timedelta
from pathlib import Path

OFFICE_NAME = "Work (Primes & Zooms)"
OFFICE_KEYWORDS = ("primes", "zooms", "work (", "work -", "work –")
MAPS_ACTIVITY = "com.google.android.apps.maps/com.google.android.maps.MapsActivity"
DUMP_PATH = "/sdcard/timeline_dump.xml"
SCROLL_AREA_TOP = 1398
SCROLL_AREA_BOTTOM = 2340
SCROLL_STEP = 600


def is_office_desc(desc: str) -> bool:
    low = desc.lower()
    return OFFICE_NAME.lower() in low or any(k in low for k in OFFICE_KEYWORDS)


def sh(cmd: str, timeout: int = 20) -> str:
    """Run a privileged shell command on the phone via shizuku, with retries."""
    for _ in range(5):
        try:
            result = subprocess.run(
                ["shizuku", "sh", "-c", cmd],
                capture_output=True, text=True, timeout=timeout,
            )
            out = (result.stdout or "").strip()
            if out:
                return out
        except (subprocess.TimeoutExpired, OSError):
            pass
        time.sleep(1.5)
    return ""


def clean_attr(v: str) -> str:
    """Decode uiautomator attribute encodings (&#10; entities, %XX escapes)."""
    v = v.replace("&#10;", "\n").replace("&#13;", "\r")
    v = v.replace("&amp;", "&").replace("&quot;", '"')
    v = v.replace("&lt;", "<").replace("&gt;", ">")
    try:
        return urllib.parse.unquote(v)
    except Exception:
        return v


def lift_maps():
    """Re-raise Maps if something stole the foreground (agent UI, launcher)."""
    sh("input keyevent KEYCODE_HOME")
    time.sleep(0.8)
    sh(f"am start -n {MAPS_ACTIVITY}")
    time.sleep(3)


def dump_ui() -> list[dict]:
    """Dump the current UI hierarchy and return parsed nodes.

    Parsing happens ON-DEVICE (grep) so we never need to transfer the raw XML
    (the bridge caps stdout at ~64KB, which previously corrupted multi-byte
    UTF-8 during chunked pulls).  Self-heals focus: if the dump is not Maps,
    raise Maps again and re-dump.
    """
    for attempt in range(4):
        sh(f"uiautomator dump {DUMP_PATH}")
        time.sleep(0.5)
        pkg = sh(f"grep -oE 'package=\"[^\"]+\"' {DUMP_PATH} | head -1")
        if "com.google.android.apps.maps" not in pkg:
            lift_maps()
            continue

        nodes = []
        descs_out = sh(f"grep -oE 'content-desc=\"[^\"]*\"' {DUMP_PATH}")
        for line in descs_out.splitlines():
            m = re.match(r'content-desc="(.*)"$', line)
            if m and m.group(1).strip():
                nodes.append({"text": "", "desc": clean_attr(m.group(1)), "bounds": ""})
        texts_out = sh(f"grep -oE 'text=\"[^\"]*\"' {DUMP_PATH}")
        for line in texts_out.splitlines():
            m = re.match(r'text="(.*)"$', line)
            if m and m.group(1).strip():
                nodes.append({"text": clean_attr(m.group(1)), "desc": "", "bounds": ""})

        if not nodes:
            lift_maps()
            continue
        return nodes
    return []


def find_bounds(desc: str) -> str:
    """Find the bounds attribute of the node whose content-desc == desc."""
    pat = f'content-desc="{desc}"[^>]*bounds="[^"]*"'
    out = sh(f"grep -oE '{pat}' {DUMP_PATH} | head -1")
    m = re.search(r'bounds="([^"]+)"', out)
    return m.group(1) if m else ""


def parse_bounds(bounds_str: str) -> tuple[int, int, int, int]:
    """Parse '[x1,y1][x2,y2]' into (x1, y1, x2, y2)."""
    parts = bounds_str.replace("][", ",").replace("[", "").replace("]", "").split(",")
    return int(parts[0]), int(parts[1]), int(parts[2]), int(parts[3])


def center_of(bounds_str: str) -> tuple[int, int]:
    x1, y1, x2, y2 = parse_bounds(bounds_str)
    return (x1 + x2) // 2, (y1 + y2) // 2


def extract_time_from_desc(desc: str) -> list[str]:
    """Extract time strings like '10:18 am' from a content-desc."""
    return re.findall(r'(\d{1,2}:\d{2}\s*(?:am|pm))', desc, re.IGNORECASE)


def scroll_down():
    """Scroll the timeline list down to reveal more entries."""
    mid_x = 540
    start_y = SCROLL_AREA_TOP + 100
    end_y = SCROLL_AREA_TOP + 100 - SCROLL_STEP
    sh(f"input swipe {mid_x} {start_y} {mid_x} {end_y} 300")
    time.sleep(1)


def scroll_up():
    """Scroll the timeline list back to the top."""
    mid_x = 540
    start_y = SCROLL_AREA_TOP + 100
    end_y = SCROLL_AREA_BOTTOM - 100
    sh(f"input swipe {mid_x} {start_y} {mid_x} {end_y} 300")
    time.sleep(1)


def get_all_timeline_entries() -> list[str]:
    """Scroll through the timeline and collect all content-desc entries."""
    all_descs = []
    seen = set()

    for _ in range(8):  # max 8 scrolls to cover a full day
        nodes = dump_ui()
        new_found = False
        for n in nodes:
            desc = n["desc"]
            low = desc.lower()
            if desc and desc not in seen:
                # Only collect timeline-relevant entries (contain times or travel info)
                if any(kw in low for kw in ["am", "pm", "motorcycling", "driving",
                                            "walking", "transit", "work", "home",
                                            "visited", "missing", "add"]):
                    seen.add(desc)
                    all_descs.append(desc)
                    new_found = True

        if not new_found:
            break
        scroll_down()

    # Scroll back up for the next day
    scroll_up()
    time.sleep(0.5)

    return all_descs


def find_office_events(entries: list[str]) -> dict:
    """Find first arrival and last departure from office in timeline entries."""
    office_arrivals = []
    office_departures = []
    currently_at_office = False

    for desc in entries:
        if not is_office_desc(desc):
            continue

        # Check if user is currently at office
        if "Here" in desc or "Here now" in desc:
            currently_at_office = True

        times = extract_time_from_desc(desc)
        if not times:
            continue

        # Entry like: "Work (Primes & Zooms), 10:18 am – 1:09 pm, ..."
        if len(times) >= 2:
            office_arrivals.append(times[0])
            office_departures.append(times[1])
        elif len(times) == 1:
            if "Left" in desc:
                office_departures.append(times[0])
            else:
                office_arrivals.append(times[0])

    result = {}
    if office_arrivals:
        result["arrival"] = office_arrivals[0]

    if currently_at_office:
        result["departure"] = "Still here"
    elif office_departures:
        result["departure"] = office_departures[-1]

    return result


def click_previous_day():
    """Click the 'Previous day' button."""
    bounds = find_bounds("Previous day")
    if bounds:
        cx, cy = center_of(bounds)
        sh(f"input tap {cx} {cy}")
        time.sleep(2.5)  # Wait for timeline to load
        return True
    # Also try text label "Previous day" (some builds expose it as text)
    nodes = dump_ui()
    for n in nodes:
        if n["text"] == "Previous day":
            b = find_bounds(n["text"])
            if b:
                cx, cy = center_of(b)
                sh(f"input tap {cx} {cy}")
                time.sleep(2.5)
                return True
    return False


def get_day_label() -> str:
    """Get the current day label from the UI (e.g., 'Today', 'Yesterday', 'Mon, 25 Aug')."""
    nodes = dump_ui()
    for n in nodes:
        text = n["text"]
        if text in ("Today", "Yesterday") or (len(text) > 3 and "," in text and any(
            m in text for m in ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"])):
            return text
    return "Unknown"


def main():
    import argparse
    parser = argparse.ArgumentParser(description="Get office times from Google Maps Timeline")
    parser.add_argument("--days", type=int, default=10, help="Number of past days to check (default: 10)")
    args = parser.parse_args()

    days = args.days
    results = []

    print(f"Fetching office times for the past {days} days...\n")
    print("Make sure Google Maps Timeline (Day view) is open on your phone.\n")

    # Make sure Maps is in front before we start.
    lift_maps()
    time.sleep(2)

    for i in range(days):
        day_label = get_day_label()
        print(f"Processing {day_label}...", end=" ", flush=True)

        entries = get_all_timeline_entries()
        events = find_office_events(entries)

        arrival = events.get("arrival", "—")
        departure = events.get("departure", "—")

        results.append({
            "day": day_label,
            "arrival": arrival,
            "departure": departure,
        })

        print(f"Arrived: {arrival}  |  Left: {departure}")

        # Navigate to previous day (unless this is the last day)
        if i < days - 1:
            if not click_previous_day():
                print("  ⚠ Could not find 'Previous day' button. Stopping.")
                break

    # Print summary table
    print("\n" + "=" * 55)
    print(f"{'Day':<20} {'Arrived':<15} {'Last Left':<15}")
    print("-" * 55)
    for r in results:
        print(f"{r['day']:<20} {r['arrival']:<15} {r['departure']:<15}")
    print("=" * 55)
    # Machine-readable output for backfill
    import json
    out = {"results": results}
    with open("/root/office_times_results.json", "w") as f:
        json.dump(out, f, indent=2)
    print("Saved: /root/office_times_results.json")


if __name__ == "__main__":
    main()
