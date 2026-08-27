#!/usr/bin/env python3
"""
Scrape Google Maps Timeline for the past N days and produce a JSON seed file
that the Office Tracker Android app can import into its Room database.
"""
import argparse
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = "192.168.1.52:5555"
DUMP_PATH = "/sdcard/seed_dump.xml"
LOCAL_DUMP = "/tmp/seed_dump.xml"
OFFICE_NAME = "Work (Primes & Zooms)"
SCROLL_AREA_TOP = 1398
SCROLL_AREA_BOTTOM = 2340
SCROLL_STEP = 600

def adb(cmd):
    return subprocess.run(["adb", "-s", SERIAL, "shell", cmd],
                          capture_output=True, text=True, timeout=15).stdout.strip()

def dump_ui():
    adb(f"uiautomator dump {DUMP_PATH}")
    time.sleep(0.4)
    subprocess.run(["adb", "-s", SERIAL, "pull", DUMP_PATH, LOCAL_DUMP],
                   capture_output=True, timeout=10)
    time.sleep(0.3)
    tree = ET.parse(LOCAL_DUMP)
    nodes = []
    for n in tree.getroot().iter("node"):
        text = n.get("text", ""); desc = n.get("content-desc", ""); b = n.get("bounds", "")
        if text or desc:
            nodes.append({"text": text, "desc": desc, "bounds": b})
    return nodes

def center(b):
    p = b.replace("][", ",").replace("[", "").replace("]", "").split(",")
    return (int(p[0]) + int(p[2])) // 2, (int(p[1]) + int(p[3])) // 2

def get_day_label():
    for n in dump_ui():
        t = n["text"]
        if t in ("Today", "Yesterday") or ("," in t and any(m in t for m in
            ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"])):
            return t
    return "Unknown"

def scroll_down():
    adb(f"input swipe 540 {SCROLL_AREA_TOP+100} 540 {SCROLL_AREA_TOP+100-SCROLL_STEP} 300")
    time.sleep(1)

def scroll_up():
    adb(f"input swipe 540 {SCROLL_AREA_TOP+100} 540 {SCROLL_AREA_BOTTOM-100} 300")
    time.sleep(1)

def collect_entries():
    seen = set(); entries = []
    for _ in range(8):
        new = False
        for n in dump_ui():
            d = n["desc"]
            if d and d not in seen and any(k in d for k in
                ["am","pm","Motorcycling","Driving","Walking","Transit","Work","Home","Visited","Missing"]):
                seen.add(d); entries.append(d); new = True
        if not new:
            break
        scroll_down()
    scroll_up()
    return entries

def extract_times(desc):
    return re.findall(r'(\d{1,2}:\d{2})\s*(am|pm)', desc, re.IGNORECASE)

def parse_day(entries):
    arrivals = []; departures = []; currently = False
    for d in entries:
        if OFFICE_NAME not in d:
            continue
        if "Here" in d:
            currently = True
        times = extract_times(d)
        if len(times) >= 2:
            arrivals.append(f"{times[0][0]} {times[0][1]}")
            departures.append(f"{times[1][0]} {times[1][1]}")
        elif len(times) == 1:
            if "Left" in d:
                departures.append(f"{times[0][0]} {times[0][1]}")
            else:
                arrivals.append(f"{times[0][0]} {times[0][1]}")
    result = {}
    if arrivals:
        result["arrival"] = arrivals[0]
    if currently:
        result["departure"] = None
        result["current"] = True
    elif departures:
        result["departure"] = departures[-1]
    return result

def hour_min_to_24h(hhmm, ap):
    h = int(hhmm.split(":")[0]); m = int(hhmm.split(":")[1])
    if ap.lower() == "pm" and h != 12:
        h += 12
    elif ap.lower() == "am" and h == 12:
        h = 0
    return h, m

def to_timestamp(date_str, hhmm, ap):
    import datetime
    y, mo, d = date_str.split("-")
    h, m = hour_min_to_24h(hhmm, ap)
    return int(datetime.datetime(int(y), int(mo), int(d), h, m).timestamp() * 1000)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--days", type=int, default=10)
    ap.add_argument("--out", default="/tmp/office_seed.json")
    args = ap.parse_args()

    import datetime
    today = datetime.date.today()
    dates = [(today - datetime.timedelta(days=i)).isoformat() for i in range(args.days)]
    labels = []
    records = {}

    print(f"Scraping {args.days} days from Timeline...")
    for i in range(args.days):
        label = get_day_label()
        entries = collect_entries()
        parsed = parse_day(entries)
        date = dates[i]
        # store as day-label -> date for output assembly
        labels.append((label, date, entries))
        print(f"  {label} ({date}): {parsed}")
        records[date] = parsed
        if i < args.days - 1:
            found = False
            for n in dump_ui():
                if n["desc"] == "Previous day":
                    cx, cy = center(n["bounds"])
                    adb(f"input tap {cx} {cy}")
                    time.sleep(2.5)
                    found = True
                    break
            if not found:
                print("  Could not find Previous day. Stopping.")
                break

    # Build output list
    visits = []
    for date in dates:
        r = records.get(date, {})
        out = {"date": date}
        if "arrival" in r:
            hh, ap = r["arrival"].split()
            out["arrivalTime"] = f"{hh} {ap}"
            out["arrivalTimestamp"] = to_timestamp(date, hh, ap)
        if r.get("current"):
            out["isCurrentlyAtOffice"] = True
        elif "departure" in r:
            hh, ap = r["departure"].split()
            out["departureTime"] = f"{hh} {ap}"
            out["departureTimestamp"] = to_timestamp(date, hh, ap)
        visits.append(out)

    payload = {"visits": visits}
    with open(args.out, "w") as f:
        json.dump(payload, f, indent=2)
    print(f"\nWrote {len(visits)} visits to {args.out}")

if __name__ == "__main__":
    main()
