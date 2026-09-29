# Changelog

## Unreleased

- **Spotify and Chrome work when Perfect Sound is installed from a download.** Android greys
  out notification access as a restricted setting for downloaded apps, so remote mode couldn't
  be turned on. Perfect Sound now shows Android's steps to allow it, with an **APP INFO** button.

## 0.4.0 (2026-09-29)

- **A tidier player:** the SOURCE buttons (LOCAL, SPOTIFY, CHROME) move to the end of the
  transport row, each with a small icon, and the main panel is a row shorter. When the playlist
  sits beside the player they show just their icons.
- **Picking Spotify or Chrome opens it,** ready to start something, so there's no separate OPEN
  button any more. Click the source again to bring the app back to the front.
- **A shorter volume slider.**
- **kbps for FLAC and WAV files:** formats that don't state a bitrate now show the average, from
  the file's size and length.
- **A new icon,** with a one-colour version for themed icons on the launcher and shelf.
- The Alt+G shortcut and menu item are now called **Visualizer**.

## 0.3.2 (2026-09-26)

- **Simpler main panel:** the SHUFFLE, REPEAT and PL buttons are gone. Shuffle, repeat and the
  playlist are still in the right-click menu and on the S, R and Alt+E keys.
- **Tidier Now Playing panel:** the buttons under the album art are as wide as the art.

## 0.3.1 (2026-09-26)

- The visualizer ends level with the album art and its buttons.
- **SHUFFLE and REPEAT grey out** when Spotify or Chrome won't accept them. Spotify only allows
  them for some of what it plays, and Chrome never does.
- **kbps and kHz in remote mode:** Android doesn't reveal another app's stream format, so these
  show typical values: Spotify 160 kbps / 44 kHz, Chrome 160 kbps / 48 kHz.

## 0.3.0 (2026-09-26)

- **Visualizer** in the Now Playing panel, with six modes: **BANDS**, **SPECTRUM**, **LED**,
  **CURVE**, **SCOPE** and **VU METER** (tape-deck style meters).
- **MODE** cycles the modes (or click the display); **COLOR** cycles the themes: green, rainbow,
  red and ice. Both are remembered.
- Works in remote mode too: press **ON** to start audio capture.

## 0.2.0 (2026-09-26)

- **Now Playing panel:** album art, track details and the levels in one panel.
- **Chrome** replaces the YouTube Music app as a source: follow and control whatever a Chrome tab
  is playing, such as music.youtube.com.
- **Switching sources pauses the old one,** so two streams never play at once.

## 0.1.0 (2026-09-26)

The first release: a local player with main, equalizer and playlist panels; remote mode for
Spotify; keyboard shortcuts, right-click menus, multi-select, drag to reorder, dropping files and
Open with.
