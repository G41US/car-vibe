# Car Vibe

A home screen (launcher) for Android car head units that run CarPlay and
Android Auto through RLink. Built for a 10" 1024×600 unit running Android 8.1.

## What's on the home screen

- **Left strip:** your car's brand logo, model name and the weather. Nothing
  here is tappable, because the touch panel on this unit doesn't respond in
  the first ~148px. The strip is 170px wide by default; change it in Settings.
- **Clock** and a **GPS speedometer** (real speed from the unit's GPS).
- **Phone:** a glowing phone when your phone is connected, with its name and
  battery. Tap it to open RLink (CarPlay / Android Auto). Long-press for
  Bluetooth settings.
- **Music:** what's playing, with controls.
- **Maps:** a live mini map of where you are. Tap it to open your phone's Google
  Maps through RLink, or Google Maps on the unit when no phone is connected.
- **Dock:** Music, Radio, Camera, Bluetooth, Apps and Settings. Long-press
  Music, Radio, Camera or Bluetooth to choose which app it opens.

## Side buttons

The bezel keys (power, volume) sit on the dead part of the touch panel, so Car
Vibe adds a floating button on the right edge, over every app including
CarPlay. Tap it for Volume −/+, Mute and Screen off. Like the power key: tap
Mute to mute, hold it to turn the screen off. Touch the screen to wake it.
Drag the button up or down to move it. Needs "Display over other apps".

## First-time setup

1. Type your car brand. Car Vibe looks the logo up on Wikidata/Wikipedia and
   saves it on the unit (or use your own image).
2. Type your model.
3. Allow location, media access (notification access) and make Car Vibe the
   home screen.

## Install

Download `CarVibe.apk` from the latest release (or the build's artifact),
install it with Downloader or from a USB stick, then press Home and choose
Car Vibe → Always.

## Updates

Car Vibe updates itself from this repo. It checks every six hours (and when you
open Settings); when a newer build exists, an "Update available" chip appears
on the home screen. Download and install it from Settings › Car Vibe updates.
The first time, Android asks you to allow installs from Car Vibe.

## Build

Every push to `main` builds the APK on GitHub Actions and publishes it as the
`latest` release, so this link always points at the newest build:

    https://github.com/G41US/car-vibe/releases/latest/download/CarVibe.apk

The APK is signed with `app/carvibe.jks` so each new build installs over the
old one. Keep the repository private if you don't want others to reuse that key.

Locally: `./gradlew assembleRelease`. `./gradlew testDebugUnitTest` renders the
screens to `app/build/screens/*.png` on a simulated head unit.

Map data © OpenStreetMap contributors. Weather by Open-Meteo.
