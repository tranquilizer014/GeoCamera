# GeoCamera

An Android camera app that stamps a location/date-time info card onto photos,
similar to "GPS Map Camera" style apps — with the location, date, and time fully
under your control.

## Features

**Location**
- Defaults to your phone's **real GPS/network location** automatically on launch.
- Override it any time with:
  - **My Location** — snap back to your real, current location.
  - **Pick Location** — choose any point on an OpenStreetMap satellite map (Esri World
    Imagery tiles, no API key needed), with exact coordinates shown live as you move the pin.
  - **Favorites** — save locations from the map screen. Selecting a favorite generates a
    **fresh random point within 10 meters** of it every time — the exact saved coordinates
    are never reused directly.
- Location names come from Android's built-in `Geocoder` (backed by Google's own data via
  Play Services, free, no API key or billing account), falling back to the free OSM Nominatim
  service if the native geocoder isn't available.

**Camera**
- Front and back camera, switchable in-app (CameraX).
- A live on-screen guide shows the overlay panel over the camera preview before you shoot,
  so you can frame the shot knowing exactly how the final photo will look.
- Capturing saves straight to `Pictures/GeoCamera` in your gallery — no extra confirmation
  step, so you can check the result in your gallery app right away.

**Date & time**
- Editable date and time, defaulting to **IST (Asia/Kolkata)**.
- Switchable to your device's default timezone or UTC.

**Overlay**
- Bottom info-card overlay: satellite thumbnail with a pin, place name, coordinates,
  an offline-computed Plus Code, and the date/time with GMT offset.
- Optional person-name field.
- **Overlay an Existing Photo** — pick any photo from your gallery and bake the current
  location/date-time overlay onto it (respects the photo's EXIF orientation), saved as a
  new file alongside your camera captures.

## Building the APK

This repo builds via GitHub Actions (`.github/workflows/build.yml`) — push to `main`/`master`
or run the workflow manually, then download the **debug, unsigned APK** from the run's
Artifacts section. No keystore/signing setup required (debug builds use Android's
auto-generated debug key).

To build locally instead:
```
gradle assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

## Installing

Since this is an unsigned debug build, Android/Play Protect will warn on install. On the
warning screen, tap **More details → Install anyway** — this approves just this one APK
without turning off Play Protect scanning for anything else.

## Permissions used

| Permission | Why |
|---|---|
| Camera | Taking photos |
| Location (fine/coarse) | Real-location default and map picker |
| Internet | Satellite tiles, Nominatim fallback geocoding, gallery-photo overlay |

## Notes

- Reverse geocoding and the satellite thumbnail need internet access at the moment you
  set a location (native `Geocoder` first, Esri + Nominatim as fallbacks — both free/keyless).
- Plus Codes are computed locally with a compact implementation of the Open Location Code
  algorithm — no network or extra library needed for that part.
- Minimum SDK 26 (Android 8.0+).
- CI builds on `ubuntu-22.04` (not `ubuntu-latest`) to avoid an AAPT2 compatibility issue
  with GitHub's Ubuntu 24.04 runners.

## Project structure

```
.github/workflows/build.yml       CI: builds and uploads the debug APK
app/
  build.gradle                    App module dependencies (CameraX, osmdroid, org.json)
  src/main/
    AndroidManifest.xml
    java/com/geocamera/app/
      MainActivity.kt             Camera, capture flow, location/date-time state
      MapPickerActivity.kt        OSM satellite map picker + favorites
      OverlayRenderer.kt          Shared drawing logic for the info-card overlay
      OverlayPreviewView.kt       Live on-screen overlay guide over the camera preview
      FavoritesManager.kt         SharedPreferences-backed favorites storage
      GeoUtils.kt                 Random-point-in-radius, reverse geocoding (native + Nominatim)
      TileSources.kt              Esri satellite tile source for osmdroid + thumbnail fetch
      PlusCode.kt                 Offline Open Location Code encoder
    res/
      layout/                     activity_main.xml, activity_map_picker.xml
      values/                     colors, strings, theme
      drawable/, mipmap-anydpi-v26/   Adaptive launcher icon (no PNG assets needed)
build.gradle, settings.gradle, gradle.properties   Root Gradle config
```
