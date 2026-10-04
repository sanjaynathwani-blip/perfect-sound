#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Makes the Google Play store listing's images in build/play from window captures.

    tools/play_images.py [SHOTS_DIR]      (default ~/.cache/perfect-sound/shots)

SHOTS_DIR is the same set of captures that tools/readme_images.py uses (demo music only).
Play wants images without transparency, so each window sits on a dark backdrop:

  icon-512.png        the app icon, 512 x 512
  feature.png         the feature graphic, 1024 x 500
  screenshot-N.png    1920 x 1080, for the phone screenshots slot (Play needs at least two)

Needs rsvg-convert and the DejaVu fonts.
"""
import pathlib
import re
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from readme_images import VIS, VISUALIZER, app_only, rounded, shadowed  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "play"
SHOTS = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / ".cache/perfect-sound/shots")
LOGO = ROOT / "brand/perfect-sound-logo.svg"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
TOP, BOTTOM = (20, 22, 27), (38, 41, 49)  # backdrop gradient, ending in the panel colour
GREEN, WHITE = (88, 230, 104), (232, 234, 238)

SCREENSHOTS = [
    ("hero.png", "Your own music, with a live visualizer and a proper playlist"),
    ("remote.png", "Follows and controls Spotify or a Chrome tab"),
    (None, "Six visualizer modes, four colour themes"),  # None: the grid of modes
]


def backdrop(size):
    w, h = size
    im = Image.new("RGB", size)
    draw = ImageDraw.Draw(im)
    for y in range(h):
        t = y / (h - 1)
        draw.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    return im


def spaced(draw, xy, text, font, fill, tracking):
    """Draws text with extra letter spacing, like the app's panel titles."""
    widths = [draw.textlength(c, font=font) for c in text]
    x, y = xy
    for c, w in zip(text, widths):
        draw.text((x, y), c, font=font, fill=fill)
        x += w + tracking


def logo(px, full_bleed=False):
    """The logo; full_bleed fills the square with the tile, since Play rounds the icon's corners itself."""
    svg = LOGO.read_text()
    if full_bleed:
        svg = svg.replace('<rect x="16" y="16" width="480" height="480" rx="108"', '<rect width="512" height="512"')
        svg = re.sub(r'\n  <rect x="17.5"[^>]*/>', "", svg)
    src, png = OUT / f".logo-{px}.svg", OUT / f".logo-{px}.png"
    src.write_text(svg)
    subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), str(src), "-o", str(png)], check=True)
    return Image.open(png).convert("RGBA")


def save(im, name):
    im.save(OUT / name, optimize=True)
    print(OUT / name, im.size, im.mode)


def screenshot(capture, caption):
    canvas = backdrop((1920, 1080)).convert("RGBA")
    if capture:
        window = app_only(capture)
        window = window.resize((1560, round(window.height * 1560 / window.width)), Image.LANCZOS)
    else:
        window = visualizer_grid()
    # The caption, a gap and the window, centred as one block.
    caption_h, gap, pad = 50, 60, 60
    top = (1080 - caption_h - gap - window.height) // 2
    framed = shadowed(rounded(window, 22), blur=24, offset=(0, 14), alpha=150, pad=pad)
    canvas.alpha_composite(framed, ((1920 - framed.width) // 2, top + caption_h + gap - pad))
    draw = ImageDraw.Draw(canvas)
    draw.text((960, top + caption_h // 2), caption, font=ImageFont.truetype(FONT, 46), fill=WHITE, anchor="mm")
    return canvas.convert("RGB")


def visualizer_grid():
    """The six visualizer tiles at their captured size, on the panel colour."""
    tiles = [Image.open(SHOTS / f"vis-{v}.png").crop(VISUALIZER) for v in VIS]
    w, h = tiles[0].size
    gap = 20
    grid = Image.new("RGB", (3 * w + 4 * gap, 2 * h + 3 * gap), BOTTOM)
    for i, tile in enumerate(tiles):
        grid.paste(tile, (gap + (i % 3) * (w + gap), gap + (i // 3) * (h + gap)))
    return grid


def feature():
    canvas = backdrop((1024, 500)).convert("RGBA")
    canvas.alpha_composite(logo(220), (92, 140))
    draw = ImageDraw.Draw(canvas)
    spaced(draw, (372, 168), "PERFECT SOUND", ImageFont.truetype(FONT_BOLD, 50), WHITE, 9)
    tagline = ImageFont.truetype(FONT, 27)
    draw.text((374, 256), "A music player in the spirit of", font=tagline, fill=GREEN)
    draw.text((374, 294), "the classic '90s desktop players", font=tagline, fill=GREEN)
    return canvas.convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    save(logo(512, full_bleed=True).convert("RGB"), "icon-512.png")
    save(feature(), "feature.png")
    for i, (capture, caption) in enumerate(SCREENSHOTS, 1):
        save(screenshot(capture, caption), f"screenshot-{i}.png")
    for tmp in OUT.glob(".logo-*"):
        tmp.unlink()


if __name__ == "__main__":
    main()
