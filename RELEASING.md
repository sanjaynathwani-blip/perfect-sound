# Releasing Perfect Sound

A release goes out as a **pre-release** first, is installed on a real device the way a user would
install it, and only then becomes the latest release that the README's download link points to.

The first-install check exists because development installs hide problems. `adb install` and
`cmd notification allow_listener` skip what a user goes through: Android restricts notification
access for apps installed from a downloaded APK, and granting it happens in a separate Settings
window. Version 0.4.0 shipped with remote mode unusable from a download because every test used
adb.

## 1. Prepare

1. In `app/build.gradle.kts`, bump `versionCode` by one and set `versionName`.
2. In `CHANGELOG.md`, rename **Unreleased** to the version and date.
3. Commit as "Version X.Y.Z" and push.

## 2. Build

The release key lives in `~/.config/perfect-sound/` (never in the repo). Every release must be
signed with it, or Android refuses to update over earlier installs.

```sh
./gradlew testDebugUnitTest assembleRelease
mkdir -p build/release && cp app/build/outputs/apk/release/app-release.apk build/release/PerfectSound.apk
(cd build/release && sha256sum PerfectSound.apk > SHA256SUMS)

# Same signing certificate as the current release?
gh release download --pattern PerfectSound.apk --dir build/prev --clobber
apksigner verify --print-certs build/release/PerfectSound.apk | grep SHA-256
apksigner verify --print-certs build/prev/PerfectSound.apk | grep SHA-256
```

## 3. Publish as a pre-release

```sh
git tag vX.Y.Z && git push origin vX.Y.Z
gh release create vX.Y.Z build/release/PerfectSound.apk build/release/SHA256SUMS --prerelease --title "Perfect Sound X.Y.Z" --notes-file build/release/notes.md
```

A pre-release doesn't move `releases/latest`, so the README's download link still serves the
previous version.

## 4. First-install check, on the device, without adb

1. Uninstall Perfect Sound (this clears its playlist and settings).
2. In Chrome on the device, open the pre-release's page, download **PerfectSound.apk**, open it
   and install it.
3. Open Perfect Sound, then **OPEN FILES** or **ADD FOLDER**: a local track plays and the
   visualizer moves.
4. Pick **SPOTIFY** with something playing in Spotify. Turn on notification access with the app's
   own **GRANT ACCESS** button. If the switch is greyed out, follow the steps the app shows.
5. Come back to Perfect Sound's window without closing it: Spotify's track and art appear.
6. Press **ON** and accept Android's prompt: the visualizer moves to Spotify.
7. Repeat 4 to 6 for **CHROME** with YouTube Music playing in a tab.

## 5. Make it the latest release

```sh
gh release edit vX.Y.Z --prerelease=false --latest
```

If a step in the check fails, fix it, bump the version again and start over; don't replace a
published APK.

## 6. Google Play

Upload the same version to Play's internal test as a bundle; see [PLAY.md](PLAY.md).
