"""Textures for the Trash Can, which is modelled as a bin a little narrower than a block, under an overhanging lid.

Galvanised steel, as a bin is (the user's pick of two, 2026-10-03): pale zinc with its spangle, the side ribbed and
lit from above, a band round its shoulder, a bevelled lid. Off, its slot is dark and its band is dull; switched on by
redstone, the band and the slot glow the red of something that is going to be destroyed. Drawn flat, then shaded by
texkit as vanilla shades its metal.

- trash_can_body.png / _on   the ribbed side of the bin, with the band round its shoulder
- trash_can_lid.png / _on    the lid seen from above: the slot things go in by
- trash_can_rim.png          the lid's edge and the bin's underside: a rolled edge

Run from the repo root:  python tools/textures/gen_trash_can.py
"""
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

HI = (214, 218, 220)
LIT = (192, 197, 200)
MID = (170, 176, 180)
LOW = (146, 152, 158)
SEAM = (112, 118, 124)
EDGE = (74, 78, 84)
BAND_OFF = (96, 88, 88)
DEEP = (20, 20, 24)
GLOW = (226, 72, 48)
GLOW_HOT = (255, 170, 110)
KEEP = [GLOW, GLOW_HOT, DEEP, EDGE]  # left as drawn: the glow, the dark slot and the outlines


def px(img, x, y, c):
    img.putpixel((x, y), c + (255,))


def body(on):
    """Ribs four wide, each lit on its left and shadowed in its seam; brighter towards the top, under the lid."""
    rng = random.Random(1)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            rib = x % 4
            c = (LIT, MID, MID, SEAM)[rib]
            if y >= 11 and rib in (1, 2):
                c = LOW  # the foot, in shadow
            if y <= 7 and rib == 0:
                c = HI
            px(img, x, y, c)
    for _ in range(14):  # the zinc's spangle: soft lighter and darker flakes across the ribs
        x, y = rng.randrange(16), rng.randrange(6, 15)
        if x % 4 != 3:
            px(img, x, y, HI if rng.random() < 0.5 else LOW)
    for x in range(16):  # the band round its shoulder, and the foot
        px(img, x, 3, EDGE)
        px(img, x, 4, (GLOW if x % 4 else GLOW_HOT) if on else BAND_OFF)
        px(img, x, 5, EDGE)
        px(img, x, 15, EDGE)
        px(img, x, 14, SEAM if x % 4 != 3 else EDGE)
    return img


def lid(on):
    """Bevelled, lit from the upper left, the slot's far edge in shadow and a lip lit along its near edge."""
    rng = random.Random(2)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = MID
            if x == 0 or y == 0:
                c = LIT
            if x == 15 or y == 15:
                c = EDGE
            if x == 1 and 0 < y < 15 or y == 1 and 0 < x < 15:
                c = HI
            if x == 14 and 0 < y < 15 or y == 14 and 0 < x < 15:
                c = LOW
            px(img, x, y, c)
    for _ in range(10):
        x, y = rng.randrange(2, 14), rng.randrange(2, 14)
        px(img, x, y, LIT if rng.random() < 0.5 else LOW)
    for y in range(6, 10):  # the slot
        for x in range(3, 13):
            px(img, x, y, (GLOW if y in (7, 8) and 4 < x < 11 else DEEP) if on else DEEP)
    for x in range(3, 13):
        px(img, x, 5, SEAM)
        px(img, x, 10, HI)
    if on:
        px(img, 7, 7, GLOW_HOT)
        px(img, 8, 8, GLOW_HOT)
    return img


def rim():
    """A rolled edge: plain steel in lines along it, with a little spangle."""
    rng = random.Random(3)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px(img, x, y, LOW if y % 2 else MID)
    for _ in range(12):
        px(img, rng.randrange(16), rng.randrange(16), LIT)
    return img


if __name__ == "__main__":
    faces = {"trash_can_body": body(False), "trash_can_body_on": body(True), "trash_can_lid": lid(False),
             "trash_can_lid_on": lid(True), "trash_can_rim": rim()}
    for seed, (name, image) in enumerate(faces.items(), start=400):
        done = texkit.finish(image, seed, keep=KEEP, tile=name == "trash_can_rim", **texkit.SMOOTH)
        done.save(BLOCK / f"{name}.png")
        print("wrote", BLOCK / f"{name}.png")
