#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Makes the demo music shown in the README's screenshots.

    tools/demo_music.py [OUT_DIR]      (default build/demo-music)

Writes a few synthesized tracks as tagged FLAC files with embedded cover art,
so screenshots never show anyone's own music or someone else's artwork. The
tracks, artists and albums are made up. Needs numpy, Pillow and flac/metaflac
(Debian: python3-numpy python3-pil flac).
"""
import pathlib
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build" / "demo-music"
RATE = 44_100
rng = np.random.default_rng(7)

# (title, artist, album, number, bpm, key root in MIDI, chord progression as scale degrees, bars, cover)
TRACKS = [
    ("Neon Freeway", "The Seven Segments", "Vector Nights", 1, 118, 45, [0, 5, 3, 4], 48, "sunset"),
    ("Crisp at Any Size", "The Seven Segments", "Vector Nights", 2, 124, 43, [0, 3, 5, 4], 52, "sunset"),
    ("Midnight Scanlines", "The Seven Segments", "Vector Nights", 3, 110, 41, [5, 3, 0, 4], 44, "sunset"),
    ("Low Tide", "Mira Vale", "Open Water", 1, 96, 48, [0, 4, 5, 3], 40, "ocean"),
    ("Harbour Lights", "Mira Vale", "Open Water", 2, 102, 46, [0, 5, 3, 4], 44, "ocean"),
    ("Afterglow", "Mira Vale", "Open Water", 3, 90, 50, [3, 0, 4, 5], 36, "ocean"),
]

MINOR = [0, 2, 3, 5, 7, 8, 10]


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def chord(root, degree):
    """A seventh chord on the given degree of the natural minor scale, as MIDI notes."""
    return [root + MINOR[(degree + i) % 7] + 12 * ((degree + i) // 7) for i in (0, 2, 4, 6)]


def tone(freq, n, harmonics, phase=0.0):
    t = np.arange(n) / RATE
    out = np.zeros(n)
    for k, amp in enumerate(harmonics, start=1):
        out += amp * np.sin(2 * np.pi * freq * k * t + phase * k)
    return out


def env(n, attack, release):
    e = np.ones(n)
    a = max(1, int(attack * RATE))
    r = min(n, max(1, int(release * RATE)))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.exp(-np.linspace(0, 5, r))
    return e


def render(bpm, root, progression, bars):
    beat = int(RATE * 60 / bpm)
    bar = beat * 4
    n = bar * bars
    left = np.zeros(n)
    right = np.zeros(n)

    def add(sig, at, pan=0.0):
        end = min(n, at + len(sig))
        sig = sig[: end - at]
        left[at:end] += sig * (1 - pan) / 2 * 1.4
        right[at:end] += sig * (1 + pan) / 2 * 1.4

    for b in range(bars):
        start = b * bar
        notes = chord(root + 12, progression[b % len(progression)])
        section = (b // 8) % 4  # intro, build, full, break
        # Pad: detuned voices, wide.
        for i, m in enumerate(notes):
            for d, pan in ((-0.08, -0.7), (0.08, 0.7)):
                add(0.05 * tone(hz(m + d), bar, [1, 0.5, 0.25, 0.12]) * env(bar, 0.4, 0.5), start, pan * (0.5 + i / 8))
        # Bass: root on every beat, octave on the offbeat.
        if section != 0:
            for q in range(8):
                m = notes[0] - 12 + (12 if q % 2 else 0)
                add(0.16 * tone(hz(m), beat // 2, [1, 0.35, 0.1]) * env(beat // 2, 0.005, 0.12), start + q * beat // 2)
        # Arpeggio: sixteenths up the chord.
        if section in (1, 2):
            for s in range(16):
                m = notes[s % 4] + 12 + (12 if s % 8 >= 4 else 0)
                add(0.06 * tone(hz(m), beat // 4, [1, 0, 0.11, 0, 0.04]) * env(beat // 4, 0.002, 0.08), start + s * beat // 4,
                    np.sin(s / 16 * 2 * np.pi) * 0.6)
        # Drums.
        if section in (1, 2, 3):
            for q in range(4):
                if section == 3 and q % 2:
                    continue
                k = beat // 2
                t = np.arange(k) / RATE
                kick = np.sin(2 * np.pi * (50 * t + 90 * (1 - np.exp(-t * 30)) / 30)) * np.exp(-t * 9)
                add(0.5 * kick, start + q * beat)
        if section != 0:
            # Hi-hats: sixteenths, accented on the offbeat, with an open hat at the end of the bar.
            for s in range(16):
                k = beat // (2 if s == 14 else 6)
                decay = 18 if s == 14 else 70
                noise = np.diff(rng.standard_normal(k + 1), n=1) * np.exp(-np.arange(k) / RATE * decay)
                add((0.22 if s % 4 == 2 else 0.12) * noise, start + s * beat // 4, 0.35)
            # Bells: a bright line an octave above the arpeggio, once every two bars.
            if b % 2 == 0:
                for i, m in enumerate(notes[::-1]):
                    add(0.035 * tone(hz(m + 24), beat, [1, 0.6, 0.45, 0.3, 0.2, 0.12]) * env(beat, 0.001, 0.9),
                        start + i * beat, -0.4 + i * 0.25)
        if section == 2:
            for q in (1, 3):
                k = beat // 2
                snare = rng.standard_normal(k) * np.exp(-np.arange(k) / RATE * 22)
                snare += np.sin(2 * np.pi * 190 * np.arange(k) / RATE) * np.exp(-np.arange(k) / RATE * 30)
                add(0.12 * snare, start + q * beat, -0.1)

    # Air: quiet, bright noise under everything, so the top bands of the visualizer move too.
    air = np.diff(rng.standard_normal(n + 1)) * 0.012
    left += air
    right += np.roll(air, 441)
    stereo = np.stack([left, right], axis=1)
    fade = int(RATE * 3)
    stereo[-fade:] *= np.linspace(1, 0, fade)[:, None]
    stereo /= np.abs(stereo).max() / 0.89  # about -1 dBFS
    return (stereo * 32767).astype("<i2")


def cover(style, title, size=800):
    """Abstract square artwork: a synthwave sun over a grid, or a moonlit sea."""
    im = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(im)
    if style == "sunset":
        top, bottom = np.array([28, 12, 58]), np.array([236, 88, 120])
    else:
        top, bottom = np.array([8, 22, 48]), np.array([40, 120, 150])
    for y in range(size):
        f = y / size
        d.line([(0, y), (size, y)], fill=tuple(int(v) for v in top + (bottom - top) * f ** 1.4))
    horizon = int(size * 0.62)
    if style == "sunset":
        sun = Image.new("L", (size, size))
        ImageDraw.Draw(sun).ellipse((size * 0.24, size * 0.2, size * 0.76, size * 0.72), fill=255)
        glow = sun.filter(ImageFilter.GaussianBlur(40))
        im.paste((255, 170, 90), (0, 0), glow.point(lambda v: v * 0.6))
        sun_im = Image.new("RGB", (size, size))
        sd = ImageDraw.Draw(sun_im)
        for y in range(size):
            f = min(1, max(0, (y - size * 0.2) / (size * 0.52)))
            sd.line([(0, y), (size, y)], fill=(255, int(220 - 120 * f), int(110 - 40 * f)))
        stripes = sun.copy()
        sdraw = ImageDraw.Draw(stripes)
        for i, y in enumerate(range(int(size * 0.47), horizon, 22)):
            sdraw.rectangle((0, y, size, y + 4 + i * 2), fill=0)
        im.paste(sun_im, (0, 0), stripes)
        d.rectangle((0, horizon, size, size), fill=(18, 6, 40))
        for i in range(1, 14):
            y = horizon + int((size - horizon) * (i / 13) ** 2)
            d.line([(0, y), (size, y)], fill=(255, 70, 200), width=2)
        for i in range(-12, 13):
            d.line([(size / 2 + i * 12, horizon), (size / 2 + i * 90, size)], fill=(255, 70, 200), width=2)
    else:
        moon = Image.new("L", (size, size))
        ImageDraw.Draw(moon).ellipse((size * 0.58, size * 0.14, size * 0.78, size * 0.34), fill=255)
        im.paste((255, 250, 225), (0, 0), moon.filter(ImageFilter.GaussianBlur(2)))
        im.paste((200, 230, 255), (0, 0), moon.filter(ImageFilter.GaussianBlur(50)).point(lambda v: v * 0.5))
        d.rectangle((0, horizon, size, size), fill=(6, 30, 52))
        for i in range(40):
            y = horizon + int((size - horizon) * (i / 40) ** 1.6)
            w = 6 + i * 3
            x = size * 0.68 + rng.uniform(-1, 1) * i * 2
            d.line([(x - w, y), (x + w, y)], fill=(230, 240, 255), width=1 + i // 14)
        for i in range(60):
            y = horizon + rng.uniform(0, size - horizon)
            x = rng.uniform(0, size)
            d.line([(x, y), (x + rng.uniform(20, 80), y)], fill=(40, 110, 150), width=2)
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    covers = {}
    for title, artist, album, number, bpm, root, prog, bars, style in TRACKS:
        if album not in covers:
            path = OUT / f"{album}.jpg"
            cover(style, album).save(path, quality=90)
            covers[album] = path
        stem = f"{artist} - {number:02d} - {title}"
        wav = OUT / f"{stem}.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(render(bpm, root, prog, bars).tobytes())
        flac = OUT / f"{stem}.flac"
        subprocess.run(["flac", "-s", "-f", "-8", "-o", str(flac), str(wav)], check=True)
        wav.unlink()
        subprocess.run(["metaflac", "--remove-all-tags",
                        f"--set-tag=TITLE={title}", f"--set-tag=ARTIST={artist}", f"--set-tag=ALBUM={album}",
                        f"--set-tag=TRACKNUMBER={number}", "--set-tag=DATE=2026", "--set-tag=GENRE=Electronic",
                        f"--import-picture-from={covers[album]}", str(flac)], check=True)
        print(flac)


if __name__ == "__main__":
    main()
