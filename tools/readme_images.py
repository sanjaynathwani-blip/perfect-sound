#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Makes the README's images in docs/images from window captures.

    tools/readme_images.py [SHOTS_DIR]      (default ~/.cache/perfect-sound/shots)

SHOTS_DIR holds adb screen captures of a 1600x848 px Perfect Sound window
(`am task resize <task> 100 150 1700 998` on a Googlebook at 260 dpi), cropped
to the window, playing the demo music from tools/demo_music.py, never anyone's
own music:

  hero.png           local files: the player, Now Playing (BANDS) and the playlist
  remote.png         remote mode, following a Chrome tab (LED, RAINBOW)
  vis-*.png          one capture per visualizer mode and colour theme

The window's caption bar is cropped off (a debug build says "Perfect Sound Dev"
there); each image gets rounded corners and a soft shadow. icon.png is the logo
from brand/perfect-sound-logo.svg (needs rsvg-convert).
"""
import pathlib
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "images"
SHOTS = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / ".cache/perfect-sound/shots")
CAPTION = 65  # px of window caption above the app
# The visualizer, with its mode name and buttons, in the local layout's Now Playing panel.
VISUALIZER = (329, 480, 750, 790)
# Order of the visualizer grid, three across.
VIS = ["bands_green", "spectrum_ice", "led_rainbow", "curve_red", "scope_green", "led_red"]


def rounded(im, radius):
    im = im.convert("RGBA")
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius, fill=255)
    im.putalpha(ImageChops.multiply(im.getchannel("A"), mask))
    return im


def shadowed(im, blur=18, offset=(0, 10), alpha=110, pad=44):
    canvas = Image.new("RGBA", (im.width + 2 * pad, im.height + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    a = im.getchannel("A").point(lambda v: v * alpha // 255)
    shadow.paste(Image.new("RGBA", im.size, (10, 12, 30, 255)), (pad + offset[0], pad + offset[1]), a)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))
    canvas.alpha_composite(im, (pad, pad))
    return canvas


def app_only(name):
    im = Image.open(SHOTS / name)
    # Two more pixels off the bottom and sides: the window's own rounded corners show the desktop.
    return im.crop((2, CAPTION, im.width - 2, im.height - 2))


def save(im, name):
    im.save(OUT / name, optimize=True)
    print(OUT / name, im.size)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name in ("hero", "remote"):
        save(shadowed(rounded(app_only(f"{name}.png"), 18)), f"{name}.png")

    tiles = [Image.open(SHOTS / f"vis-{v}.png").crop(VISUALIZER) for v in VIS]
    w, h = tiles[0].size
    gap = 14
    grid = Image.new("RGB", (3 * w + 4 * gap, 2 * h + 3 * gap), (38, 41, 49))  # the panel colour
    for i, tile in enumerate(tiles):
        grid.paste(tile, (gap + (i % 3) * (w + gap), gap + (i // 3) * (h + gap)))
    save(shadowed(rounded(grid, 18)), "visualizers.png")

    subprocess.run(["rsvg-convert", "-w", "256", "-h", "256", str(ROOT / "brand/perfect-sound-logo.svg"),
                    "-o", str(OUT / "icon.png")], check=True)
    print(OUT / "icon.png")


if __name__ == "__main__":
    main()
