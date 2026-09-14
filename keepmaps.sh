#!/bin/bash
# Keep Google Maps foreground (host UI aggressively steals focus).
# HOME first prevents the agent WebView from instantly reclaiming focus.
MAPS="com.google.android.apps.maps/com.google.android.maps.MapsActivity"
STREAK=0
while true; do
  FOCUS=$(shizuku dumpsys window 2>/dev/null | grep -m1 mCurrentFocus)
  if echo "$FOCUS" | grep -q 'com.google.android.apps.maps'; then
    STREAK=0
  else
    STREAK=$((STREAK+1))
    if [ "$STREAK" -ge 3 ]; then
      shizuku input keyevent KEYCODE_HOME >/dev/null 2>&1
      sleep 0.8
      shizuku am start -n "$MAPS" >/dev/null 2>&1
      STREAK=0
    fi
  fi
  sleep 2
done
