#!/usr/bin/env python3
"""Generates assets/whitemist/textures/entity/listener.png (64x64) and icon.png (128x128).
UV layout MUST match ListenerModel.java (see the javadoc there)."""
import random
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/whitemist"

SKIN = (201, 203, 196)
SKIN_DARK = (150, 153, 146)
SKIN_LIGHT = (222, 224, 218)
EAR = (168, 138, 138)
EAR_INNER = (120, 88, 92)
STITCH = (38, 38, 40)
RIB = (128, 131, 126)
NAIL = (70, 72, 70)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def fill(img, x, y, w, h, base, rng, vertical_dark=0.0, noise=7):
    for yy in range(h):
        k = 1.0 - vertical_dark * (yy / max(1, h - 1))
        for xx in range(w):
            n = rng.randint(-noise, noise)
            c = tuple(max(0, min(255, int(v * k) + n)) for v in base)
            img.putpixel((x + xx, y + yy), c)


def paint_box(img, u, v, w, h, d, base, rng, side_dark=0.18):
    """Standard Minecraft cuboid net: top/bottom above, four sides below."""
    fill(img, u + d, v, w, d, shade(base, 1.08), rng)                    # top
    fill(img, u + d + w, v, w, d, shade(base, 0.7), rng)                 # bottom
    fill(img, u, v + d, d, h, base, rng, side_dark)                      # right (west)
    fill(img, u + d, v + d, w, h, base, rng, side_dark)                  # front
    fill(img, u + d + w, v + d, d, h, base, rng, side_dark)              # left (east)
    fill(img, u + 2 * d + w, v + d, w, h, shade(base, 0.9), rng, side_dark)  # back


def make_texture():
    rng = random.Random(1337)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    img = img.convert("RGB").convert("RGBA")
    # clear to transparent
    for y in range(64):
        for x in range(64):
            img.putpixel((x, y), (0, 0, 0, 0))

    # head (0,0) 8x8x8, ears (32,0) 3x6x1, body (0,16) 6x14x4, arms (20,16) 2x18x2, legs (28,16) 3x14x3
    paint_box(img, 0, 0, 8, 8, 8, SKIN, rng, 0.10)
    paint_box(img, 32, 0, 3, 6, 1, EAR, rng, 0.08)
    paint_box(img, 0, 16, 6, 14, 4, SKIN, rng, 0.22)
    paint_box(img, 20, 16, 2, 18, 2, SKIN_DARK, rng, 0.15)
    paint_box(img, 28, 16, 3, 14, 3, SKIN_DARK, rng, 0.25)

    d = ImageDraw.Draw(img)

    # --- face: front of the head is (8,8)-(15,15). Faceless, eyes sewn shut. ---
    for ex in (9, 13):  # two sewn eyes: a seam with stitches only at its ends and middle
        d.line([(ex, 11), (ex + 2, 11)], fill=STITCH + (255,))
        for sx in (ex, ex + 2):
            img.putpixel((sx, 10), STITCH + (255,))
            img.putpixel((sx, 12), STITCH + (255,))
        img.putpixel((ex + 1, 12), shade(STITCH, 1.6) + (255,))
    # faint nostrils
    img.putpixel((11, 13), SKIN_DARK + (255,))
    img.putpixel((12, 13), SKIN_DARK + (255,))

    # --- ears: darker inner on the big faces (front and back of each ear net) ---
    # ear net at (32,0), box 3x6x1: front face (33,1) 3x6, back face (37,1) 3x6
    for fx in (33, 37):
        for yy in range(2, 6):
            for xx in range(fx, fx + 3):
                img.putpixel((xx, yy), EAR_INNER + (255,))

    # --- torso: gaunt ribs on the front. Body front face is (4,20)-(9,33) size 6x14 ---
    for row in (22, 24, 26, 28):
        d.line([(5, row), (8, row)], fill=RIB + (255,))
    d.line([(6, 21), (6, 30)], fill=RIB + (255,))  # sternum

    # --- hands: darker fingertips at the bottom of the arm front/side faces (arm net at (20,16)) ---
    # arm faces start at v+d=18 and are 18 high -> last rows are 33..35
    for y in range(33, 36):
        for x in range(22, 28):
            img.putpixel((x, y), NAIL + (255,))
    return img


def make_icon():
    img = Image.new("RGBA", (128, 128), (22, 24, 27, 255))
    d = ImageDraw.Draw(img)
    for r in range(60, 0, -4):  # soft white mist glow
        a = int(255 * (1 - r / 60) ** 1.6)
        d.ellipse([64 - r, 64 - r, 64 + r, 64 + r], fill=(220, 226, 230, a))
    # a sewn-shut eye
    d.line([(44, 64), (84, 64)], fill=(20, 20, 22, 255), width=3)
    for x in range(48, 82, 8):
        d.line([(x, 58), (x, 70)], fill=(20, 20, 22, 255), width=2)
    return img


if __name__ == "__main__":
    (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    make_texture().save(ASSETS / "textures/entity/listener.png")
    make_icon().save(ASSETS / "icon.png")
    print("generated")
