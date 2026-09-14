#!/bin/bash
# Keep Google Maps in the foreground (host UI aggressively steals focus).
while true; do
  FOCUS=$(shizuku dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | grep -o 'com.google.android.apps.maps[^}]*' )
  if [ -z "$FOCUS" ]; then
    shizuku am start -n com.google.android.apps.maps/.MapsActivity >/dev/null 2>&1
  fi
  sleep 2
done
