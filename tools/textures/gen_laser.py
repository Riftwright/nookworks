"""Textures for the Laser, which is modelled as a gun rather than a cube: a mounting plate at the back, the
housing, and a barrel ending in the lens. Drawn in the Generator's charcoal and steel so it reads as one of the
powered machines.

- laser_mount.png   the plate it is bolted on by (back and rim)
- laser_body.png    the housing: steel panel in the charcoal frame, cooling slots
- laser_barrel.png  the barrel's sides: a steel tube with a ring round it
- laser_lens.png    the muzzle: a red lens in a dark ring

The steel is shaded in a long ramp, not one flat grey (the user, 2026-11-10: のっぺり): each panel falls off from a lit
upper left to a duller lower right with a faint diagonal sheen across it and soft lumps, as vanilla's iron block and
heavy core are; the housing's edges are lit along the top and left where the model's faces end; the cooling slots are
louvres with lit blades; the barrel is shaded round, a bright line along it where the light strikes; the lens glows in
six reds from a dark rim to a hot middle, under a glint. Shadows go towards blue, lights towards warm - the charcoal of
the frame is the established colour and is kept exact.

Run from the repo root:  python tools/textures/gen_laser.py
"""
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"

# The charcoal and steel of the powered machines (once shared with the Crusher's old faces).
FRAME = (22, 24, 26)
FRAME_LIT = (42, 45, 50)
PANEL = (100, 106, 112)
PANEL_DARK = (68, 73, 78)
BEVEL = (180, 188, 195)
RIVET = (210, 215, 220)
# Roller steel.
ROLL_LIT = (196, 202, 208)
ROLL = (140, 146, 152)
ROLL_DARK = (78, 83, 88)

# The steel in ten shades, PANEL at 4: shadows cooler (bluer), lights a touch warmer.
STEEL = ((34, 38, 47), (50, 55, 64), (68, 73, 81), (85, 91, 98), (100, 106, 112), (118, 123, 128),
         (138, 142, 146), (160, 164, 166), (186, 190, 191), (214, 217, 216))

LENS_RED = ((58, 8, 10), (96, 14, 14), (146, 22, 18), (190, 36, 24), (228, 64, 38), (250, 120, 78))
LENS_GLINT = (255, 214, 196)


def px(img, x, y, c):
    img.putpixel((x, y), tuple(c) + (255,))


def steel(t):
    return STEEL[max(0, min(len(STEEL) - 1, round(t)))]


def lump(x, y, size, seed):
    return texkit._smooth(x, y, size, size, seed, 16, 16, False)


def field(x, y, seed, mid=4.0, fall=2.2, sheen=1.0):
    """The panel's tone at (x, y): lit upper left, duller lower right, a faint diagonal sheen, soft lumps."""
    u = (0.45 * x + 0.55 * y) / 15
    t = mid + fall * (0.5 - u)
    d = x + y + 0.7 * lump(x, y, 4, seed + 1)
    t += sheen * math.exp(-((d - 11) / 1.5) ** 2) - 0.5 * sheen * math.exp(-((d - 14.5) / 1.2) ** 2)
    return t + 0.45 * lump(x, y, 5, seed) + 0.2 * lump(x, y, 3, seed + 2)


def housing(seed, edges=()):
    """
    The Generator's frame and panel: charcoal edge, lit bevel top and left, rivets in the corners; the panel shaded
    as steel. {@code edges}: (x0, y0, x1, y1) windows a model face shows of the panel, each given a lit top and left
    edge and a shaded bottom and right one, so the housing's faces read as edges of a cast case.
    """
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = FRAME_LIT if (x == 0 or y == 0) else FRAME
            elif x == 1 or y == 1:
                c = BEVEL
            elif x == 14 or y == 14:
                c = PANEL_DARK
            else:
                t = field(x, y, seed)
                for x0, y0, x1, y1 in edges:
                    if x0 <= x <= x1 and y0 <= y <= y1:
                        if x == x0 or y == y0:
                            t += 1.3
                        elif x == x1 or y == y1:
                            t -= 1.3
                c = steel(t)
            px(img, x, y, c)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(img, x, y, RIVET)
    return img


def mount():
    img = housing(31)
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):  # bolts through to the wall: a hole, its lower lip lit
        px(img, x, y, STEEL[0])
        px(img, x + 1, y, STEEL[2])
        px(img, x, y + 1, STEEL[2])
        px(img, x + 1, y + 1, STEEL[7])
    return img


def body():
    # the housing's faces show 3..12 of it (laser.json): the front and top, and the sides' 3..10 and 5..12
    img = housing(33, edges=((3, 3, 12, 12),))
    for y in range(4, 12, 2):  # cooling slots: louvres, each a dark gap over a blade lit along its length
        for x in range(4, 12):
            f = (x - 4) / 7
            px(img, x, y, STEEL[1] if x in (4, 11) else STEEL[0])
            px(img, x, y + 1, steel(6.6 - 3.0 * f - 0.15 * (y - 4) + 0.3 * lump(x, y, 3, 35)))
    return img


def barrel():
    """
    The barrel's sides take their 5 x 6 from u 0..4, v 5..10 (laser.json): v runs round the tube, so it is shaded
    round - lit along row 6, duller to row 10 - with the ring, a dark groove and its lit lip, at rows 7 and 8 as before.
    Its two ends a shade down, where it meets the housing and the muzzle.
    """
    img = Image.new("RGBA", (16, 16))
    rows = {5: 8.0, 6: 9.0, 9: 7.6, 10: 6.3}
    for y in range(16):
        for x in range(16):
            band = 0 if x < 5 else -1.6 if x < 11 else -3.4  # the old bands, beyond what the model shows
            t = rows.get(y, 7.4 - 0.25 * abs(y - 8)) + band + 0.35 * lump(x, y, 3, 41)
            if x in (0, 4):
                t -= 0.7
            px(img, x, y, steel(t))
    for x in range(16):  # a ring round the barrel
        px(img, x, 7, FRAME)
        px(img, x, 8, FRAME_LIT)
    px(img, 2, 6, STEEL[9])
    px(img, 1, 6, STEEL[9])
    return img


def lens():
    """The muzzle, at the barrel's own 6 px, pixel for pixel (texture x/y 5..10, the model's uv [5, 5, 11, 11]): a red
    lens glowing hottest at its middle, darkening to its rim, a glint at its upper left; in a dark ring lit along its
    upper left; the corners the steel of the barrel's end."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px(img, x, y, steel(2.6 - 0.12 * (x + y - 15)))
    for y in range(5, 11):
        for x in range(5, 11):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d <= 4.5:
                glow = 1 - math.sqrt((x - 7.8) ** 2 + (y - 7.8) ** 2) / 2.6  # the glow a touch below the middle
                c = LENS_RED[max(0, min(5, round(5.4 * glow + 0.3)))]
            elif d <= 6.5 and y < 8:
                c = (60, 64, 71) if x < 8 else FRAME_LIT
            elif d <= 8.5:
                c = FRAME_LIT if (x + y) < 15 else FRAME
            else:
                c = steel(3.6 - 0.35 * (x + y - 15))
            px(img, x, y, c)
    px(img, 6, 7, LENS_GLINT)
    px(img, 7, 6, LENS_RED[4])
    return img


if __name__ == "__main__":
    faces = {"laser_mount": mount(), "laser_body": body(), "laser_barrel": barrel(), "laser_lens": lens()}
    for name, image in faces.items():
        image.save(BLOCK / f"{name}.png")
        print("wrote", BLOCK / f"{name}.png")
