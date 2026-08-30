# GeoCamera

An Android camera app that stamps a location/date-time info card onto photos,
similar to "GPS Map Camera" style apps.

## Features
- Front and back camera, switchable in-app (CameraX).
- Pick a location on an OpenStreetMap satellite map (Esri World Imagery tiles, no API key needed).
- Save favorite locations. Selecting a favorite generates a **fresh random point within
  10 meters** of it every time — the exact saved coordinates are never reused directly.
- Editable date & time, defaulting to **IST (Asia/Kolkata)**, switchable to device-default or UTC.
- Captured photo gets a bottom info-card overlay: satellite thumbnail with pin, place name,
  address, exact lat/long, offline-computed Plus Code, and the date/time with GMT offset.
- Saved photos go to `Pictures/GeoCamera` in the device gallery.

## Building the APK
This repo builds via GitHub Actions (`.github/workflows/build.yml`) — push to `main`/`master`
or run the workflow manually, then download the **debug, unsigned APK** from the run's
Artifacts section. No keystore/signing setup required (debug builds use Android's
auto-generated debug key).

To build locally instead:
```
./gradlew assembleDebug   # or: gradle assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

## Notes
- Reverse geocoding and the satellite thumbnail need internet access at the moment you
  pick a location (uses Nominatim + Esri, both free/keyless).
- Plus Codes are computed locally with a compact implementation of the Open Location Code
  algorithm — no network or extra library needed for that part.
- Minimum SDK 26 (Android 8.0+).
