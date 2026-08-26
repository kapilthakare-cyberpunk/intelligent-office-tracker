#!/usr/bin/env python3
"""
Get office arrival and last departure times for the past N days
from Google Maps Timeline via ADB UI automation.

Prerequisites:
  - Phone connected via ADB
  - Google Maps open on Timeline (Day) view
  - Location History enabled

Usage:
  python3 office_times.py [--days 10]
"""

import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from pathlib import Path

OFFICE_NAME = "Work (Primes & Zooms)"
DUMP_PATH = "/sdcard/timeline_dump.xml"
LOCAL_DUMP = "/tmp/timeline_dump.xml"
SCROLL_AREA_TOP = 1398
SCROLL_AREA_BOTTOM = 2340
SCROLL_STEP = 600


def adb(cmd: str, timeout: int = 10) -> str:
    """Run an ADB shell command and return stdout."""
    result = subprocess.run(
        ["adb", "shell", cmd],
        capture_output=True, text=True, timeout=timeout
    )
    return result.stdout.strip()


def dump_ui() -> list[dict]:
    """Dump the current UI hierarchy and return parsed nodes."""
    adb(f"uiautomator dump {DUMP_PATH}")
    time.sleep(0.5)
    subprocess.run(["adb", "pull", DUMP_PATH, LOCAL_DUMP],
                    capture_output=True, timeout=10)
    time.sleep(0.3)

    tree = ET.parse(LOCAL_DUMP)
    root = tree.getroot()
    nodes = []
    for node in root.iter("node"):
        text = node.get("text", "")
        desc = node.get("content-desc", "")
        bounds = node.get("bounds", "")
        if text or desc:
            nodes.append({"text": text, "desc": desc, "bounds": bounds})
    return nodes


def parse_bounds(bounds_str: str) -> tuple[int, int, int, int]:
    """Parse '[x1,y1][x2,y2]' into (x1, y1, x2, y2)."""
    parts = bounds_str.replace("][", ",").replace("[", "").replace("]", "").split(",")
    return int(parts[0]), int(parts[1]), int(parts[2]), int(parts[3])


def center_of(bounds_str: str) -> tuple[int, int]:
    x1, y1, x2, y2 = parse_bounds(bounds_str)
    return (x1 + x2) // 2, (y1 + y2) // 2


def extract_time_from_desc(desc: str) -> list[str]:
    """Extract time strings like '10:18 am' from a content-desc."""
    import re
    # Match times like 10:18 am, 2:06 pm, 12:07 am
    times = re.findall(r'(\d{1,2}:\d{2}\s*(?:am|pm))', desc, re.IGNORECASE)
    return times


def scroll_down():
    """Scroll the timeline list down to reveal more entries."""
    mid_x = 540
    start_y = SCROLL_AREA_TOP + 100
    end_y = SCROLL_AREA_TOP + 100 - SCROLL_STEP
    adb(f"input swipe {mid_x} {start_y} {mid_x} {end_y} 300")
    time.sleep(1)


def scroll_up():
    """Scroll the timeline list back to the top."""
    mid_x = 540
    start_y = SCROLL_AREA_TOP + 100
    end_y = SCROLL_AREA_BOTTOM - 100
    adb(f"input swipe {mid_x} {start_y} {mid_x} {end_y} 300")
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
            if desc and desc not in seen:
                # Only collect timeline-relevant entries (contain times or travel info)
                if any(kw in desc for kw in ["am", "pm", "Motorcycling", "Driving",
                                               "Walking", "Transit", "Work", "Home",
                                               "Visited", "Missing", "Add"]):
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
        if OFFICE_NAME not in desc:
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
    nodes = dump_ui()
    for n in nodes:
        if n["desc"] == "Previous day":
            cx, cy = center_of(n["bounds"])
            adb(f"input tap {cx} {cy}")
            time.sleep(2.5)  # Wait for timeline to load
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

    # Small delay to let user verify
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


if __name__ == "__main__":
    main()
