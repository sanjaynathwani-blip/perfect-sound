# Perfect Sound

A free, open-source music player for Android, inspired by the classic desktop players of the late '90s.
It is built with Kotlin and Jetpack Compose and designed for big screens and laptops such as
Googlebooks, and it works on phones too.

Everything is drawn as vector graphics in a single window: a seven-segment time display, a
scrolling title, an equalizer panel and a playlist. It stays crisp at any size and the layout adapts
when you resize the window.

## Download

Get the latest APK from the [Releases page](https://github.com/sanjaynathwani-blip/perfect-sound/releases/latest)
and open it on your device (Android 10 or later). Android will ask you to allow installing apps from
your browser or Files app the first time.

## Features

- **Local playback**: play your own files, gapless, with a background media notification,
  lock-screen and media-key controls, and resume where you left off.
- **Remote mode**: show and control what **Spotify** or **Chrome** (for example YouTube Music on
  the web) is playing: title, artist, album art, position, play/pause/skip/seek, plus shuffle and
  repeat for Spotify. The audio stays in those apps.
- **Bouncing equalizer**: live levels for the ten classic bands (60 Hz – 16 kHz). For your own
  files this needs no permissions. For streaming apps, turn on **EQ LEVELS** (Android asks to
  capture audio each session).
- **Playlist**: add files or whole folders, drop files onto the window, or "Open with" from the
  Files app. Ctrl/Shift multi-select, drag to reorder, and right-click menus.
- **Keyboard**: the classic shortcuts (see below).

Coming in Phase 2: a working 10-band equalizer with preamp and presets, and a visualizer.

## Keyboard shortcuts

| Keys | Action |
| --- | --- |
| Z / X / C / V / B | Previous / Play / Pause / Stop / Next |
| Space | Pause / resume |
| ← / → | Seek 5 seconds |
| ↑ / ↓ | Volume (in the playlist: move the selection) |
| L / Shift+L | Open files / Add folder |
| S / R | Shuffle / Repeat |
| Alt+G / Alt+E | Show equalizer / playlist |
| Enter / Delete / Ctrl+A | Play / Remove / Select all (playlist) |

## Permissions

| Permission | Why |
| --- | --- |
| Notification access (optional) | Required by Android to see and control Spotify / Chrome |
| Microphone + screen-capture prompt (optional) | Required by Android to read other apps' audio for the EQ levels. Only other apps' media playback is captured, never the mic |
| Notifications | Media controls while playing in the background |

## Building

Requires JDK 17+ and the Android SDK (platform 35).

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # signed only if ~/.config/perfect-sound/keystore.properties exists
./gradlew testDebugUnitTest      # unit tests
```

## License

MIT, see [LICENSE](LICENSE). Perfect Sound is an independent project and is not affiliated with
Winamp, Spotify or YouTube.
