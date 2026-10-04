# Google Play

Perfect Sound goes to friends through Play's **internal testing** track: up to 100 testers by
email, no review wait, and not listed publicly. This file has what the Play Console asks for,
ready to paste, and how to send each new version.

The developer name on Play is **Bookmatched Apps**.

The app on Play is the same app as the GitHub download: same package name, same version numbers
and the same signing key (see [Signing](#signing)), so either can update the other.

## Signing

Play re-signs every app with an **app signing key**. Use the GitHub release key for it, so Play
installs and GitHub installs stay interchangeable. If Google generates a new key instead, a
friend who installed from GitHub has to uninstall (losing their playlist) before Play can
install.

When creating the first release, under **App integrity → Change app signing key**, pick
**Export and upload a key from Java keystore**, download `pepk.jar` and the encryption key, and
run (it asks for the keystore and key passwords from `~/.config/perfect-sound/keystore.properties`):

```sh
java -jar pepk.jar --keystore=$HOME/.config/perfect-sound/release.jks --alias=perfect-sound \
  --output=build/play/encrypted-key.zip --include-cert --rsa-aes-encryption \
  --encryption-key-path=encryption_public_key.pem
```

Upload `encrypted-key.zip`. With no separate upload key, uploads are signed with the same key.

## Build

```sh
./gradlew testDebugUnitTest bundleRelease
tools/play_images.py          # icon, feature graphic and screenshots in build/play
```

Upload `app/build/outputs/bundle/release/app-release.aab`. Play takes the bundle, not the APK.

## Store listing

**App name** (30 characters at most)

> Perfect Sound

**Short description** (80 at most)

> A '90s-style desktop music player with a live visualizer and a proper playlist

**Full description** (4,000 at most)

> Perfect Sound is a free, open-source music player that brings back the feel of the desktop
> players of the late '90s: a dark panel with an LCD clock, a title that scrolls, bars that bounce
> to the music and a playlist you drive with the keyboard. Everything is drawn as vector
> graphics, so it stays sharp at any window size and display scaling. It's made for Googlebooks
> and other large screens with a keyboard and mouse.
>
> YOUR OWN MUSIC
> • MP3, FLAC, AAC, Ogg, Opus, WAV and more, with gapless playback
> • A media notification, lock-screen and media-key controls
> • Picks up where you left off
>
> A VISUALIZER
> • Six modes: bands, spectrum, LED, curve, scope and VU meters
> • Four colour themes
>
> REMOTE MODE
> • See and control what Spotify or a Chrome tab is playing, with the visualizer bouncing to
>   it too
>
> A REAL PLAYLIST
> • Add files or whole folders, drop files onto the window, or use Open with from Files
> • Select with Ctrl and Shift, drag to reorder, right-click for more
> • The classic keyboard shortcuts: Z X C V B for the transport
>
> PRIVATE BY DESIGN
> Perfect Sound has no internet permission, no accounts, no analytics and no ads. It sees only
> the files you give it.
>
> Perfect Sound is a personal project, MIT-licensed, with its source on GitHub. It isn't
> affiliated with or endorsed by Spotify or Google; they're named only to describe what it works
> with.

**Graphics** (from `tools/play_images.py`, in `build/play`)

| Field | File |
| --- | --- |
| App icon | `icon-512.png` |
| Feature graphic | `feature.png` |
| Phone screenshots | `screenshot-1.png` to `screenshot-3.png` |

**Store settings:** category **Music & Audio**; contact email (shown publicly on the listing, so
use one you're happy to show); website `https://github.com/sanjaynathwani-blip/perfect-sound`.

## App content

Under **Policy and programs → App content**:

| Form | Answer |
| --- | --- |
| Privacy policy | `https://github.com/sanjaynathwani-blip/perfect-sound/blob/master/PRIVACY.md` |
| Ads | No, the app doesn't contain ads |
| App access | All functionality is available without any special access |
| Content rating | Category **Utility, Productivity, Communication, or Other**; answer **No** to every content question (no violence, sexuality, language, drugs, gambling, user interaction, sharing of location or purchases) |
| Target audience | **18 and over**; the app isn't designed for children |
| News app | No |
| Data safety | **No** to "Does your app collect or share any of the required user data types?" Audio for the visualizer is analysed on the device and never leaves it, which Play doesn't count as collection |
| Advertising ID | No, the app doesn't use an advertising ID |
| Government app | No |
| Financial features | None |
| Health | None |

### Foreground service permissions

Play asks what each foreground service type is for, and for a link to a short video showing it
(an unlisted YouTube video works).

**Media playback** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`)

> Plays the music files the user chose, and keeps playing when the app is in the background,
> with a media notification and lock-screen controls. The user starts and stops it with the
> play and stop buttons. If it were deferred or stopped, the music would stop.

**Media projection** (`FOREGROUND_SERVICE_MEDIA_PROJECTION`)

> In remote mode, the visualizer can move to the audio another app is playing (Spotify or
> Chrome). The user turns it on with the visualizer's ON button and accepts Android's screen
> sharing prompt; the app uses AudioPlaybackCapture for other apps' playback audio only, never
> the microphone, and captures no screen content. The audio is analysed on the device as it
> plays and is never recorded, stored or sent. The user turns it off with the same button or
> from Android's sharing indicator. If it were deferred or stopped, the visualizer would stop
> moving.

## Testers

**Testing → Internal testing → Testers:** create an email list with each friend's Google account
(the one they use for Play on their device), then copy the **opt-in link** to send them. They
open it on their device, accept, and install from Play. They need Android 10 or later.

Friends who already installed the GitHub APK can install from Play over it, keeping their
playlist, as long as Play's signing key is the release key.

## Sending a new version

Bump `versionCode` and `versionName` (shared with GitHub releases, see [RELEASING.md](RELEASING.md)),
build the bundle, then **Internal testing → Create new release**, upload it, paste the
CHANGELOG entry as release notes, and roll out. Testers get it as a normal Play update.
