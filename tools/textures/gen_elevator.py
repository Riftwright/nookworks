"""Elevator, drawn as the machines are: the black steel frame with pale studs at its corners, and inside it

- elevator_top    : a tread plate to stand on, raised studs in rows, and in its middle a dark panel let into it with
                    two small brass arrowheads, one up and one down - jump for the one above, sneak for the one below
                    (two arrows filling a brass ring, they read as a mask with eyes)
- elevator_side   : a dark panel between two pale guide rails, the same two arrows in brass up its middle
- elevator_bottom : the frame round a plain dark plate

(It used to be smooth stone with black arrows laid over it: a white slab among black machines.)
It also writes the elevator's model, a cube with a texture a face.
Drawn flat, then shaded with texkit.shade as metal. Original work, deterministic.
Run from the repo root:  python tools/textures/gen_elevator.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
OUT = ASSETS / "textures/block"

STUD_HI, STUD, STUD_LO, STUD_DK = (219, 222, 227), (174, 177, 182), (134, 137, 142), (89, 92, 96)
BAR, BAR_IN, BAR_SH = (36, 38, 42), (22, 24, 27), (12, 13, 15)
PALE_HI, PALE, PALE_LO, PALE_DK = (224, 227, 231), (199, 202, 207), (164, 168, 175), (124, 128, 136)
DARK_HI, DARK, DARK_LO, DARK_DK = (72, 76, 82), (50, 53, 58), (38, 40, 44), (26, 28, 31)
BRASS_HI, BRASS, BRASS_LO, BRASS_DK = (238, 204, 116), (200, 156, 68), (146, 106, 42), (96, 66, 26)
KEEP = (STUD_HI, STUD, STUD_LO, STUD_DK, BAR, BAR_IN, BAR_SH, BRASS_HI, BRASS, BRASS_LO)


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c) + (255,))


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def frame(img):
    rect(img, 0, 0, 15, 1, BAR)
    rect(img, 0, 14, 15, 15, BAR)
    rect(img, 0, 0, 1, 15, BAR)
    rect(img, 14, 0, 15, 15, BAR)
    rect(img, 1, 1, 14, 1, BAR_IN)
    rect(img, 1, 14, 14, 14, BAR_SH)
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        put(img, x, y, STUD_HI if (x, y) == (0, 0) else STUD)
        put(img, x + 1, y, STUD)
        put(img, x, y + 1, STUD_LO)
        put(img, x + 1, y + 1, STUD_DK)


def arrows(img, x0, up_y, down_y):
    """A small arrow pointing up with its tip at row up_y, and one pointing down with its tip at down_y, centred on the
    two columns x0, x0 + 1."""
    for i, half in enumerate((0, 1, 2)):
        rect(img, x0 - half, up_y + i, x0 + 1 + half, up_y + i, BRASS_HI if i < 2 else BRASS)
        rect(img, x0 - half, down_y - i, x0 + 1 + half, down_y - i, BRASS if i < 2 else BRASS_LO)


def top():
    img = blank()
    rect(img, 2, 2, 13, 13, PALE_LO)
    for y in range(2, 14):  # the tread: raised studs in offset rows
        for x in range(2, 14):
            if (x + (y // 2) * 2) % 4 == 0 and y % 2 == 0:
                put(img, x, y, PALE_HI)
                put(img, x + 1, y + 1, PALE_DK)
    rect(img, 4, 4, 11, 11, DARK)  # the panel let into the tread: its far and left edges in shadow, its near lips lit
    rect(img, 4, 4, 11, 4, DARK_DK)
    rect(img, 4, 4, 4, 11, DARK_DK)
    rect(img, 5, 11, 11, 11, DARK_HI)
    rect(img, 11, 5, 11, 11, DARK_HI)
    for y, (x0, x1), c in ((5, (7, 8), BRASS_HI), (6, (6, 9), BRASS_HI),  # the arrowhead up, lit
                           (9, (6, 9), BRASS), (10, (7, 8), BRASS_LO)):  # and the one down, two rows of dark panel apart
        rect(img, x0, y, x1, y, c)
    frame(img)
    return img


def side():
    img = blank()
    rect(img, 2, 2, 13, 13, DARK)
    rect(img, 3, 2, 3, 13, PALE)  # the guide rails
    rect(img, 4, 2, 4, 13, PALE_DK)
    rect(img, 11, 2, 11, 13, PALE)
    rect(img, 12, 2, 12, 13, PALE_DK)
    for y in (4, 8, 12):
        put(img, 3, y, STUD_HI)
        put(img, 11, y, STUD_HI)
    rect(img, 5, 2, 10, 13, DARK_LO)
    arrows(img, 7, 3, 12)
    rect(img, 6, 7, 9, 8, BAR_SH)  # between the arrows, the shaft's slot
    frame(img)
    return img


def bottom():
    img = blank()
    rect(img, 2, 2, 13, 13, DARK_LO)
    rect(img, 2, 7, 13, 7, DARK_DK)
    frame(img)
    return img


def main():
    for i, (name, img) in enumerate((("elevator_top", top()), ("elevator_side", side()), ("elevator_bottom", bottom()))):
        texkit.shade(img, 520 + i, keep=KEEP, material="metal", **texkit.SMOOTH).save(OUT / f"{name}.png")
    faces = {"north": "#side", "east": "#side", "south": "#side", "west": "#side", "up": "#top", "down": "#bottom"}
    model = {"parent": "minecraft:block/block",
             "textures": {"particle": "nookworks:block/elevator_side", "side": "nookworks:block/elevator_side",
                          "top": "nookworks:block/elevator_top", "bottom": "nookworks:block/elevator_bottom"},
             "elements": [{"from": [0, 0, 0], "to": [16, 16, 16],
                           "faces": {s: {"texture": f, "cullface": s} for s, f in faces.items()}}]}
    (ASSETS / "models/block/elevator.json").write_bytes((json.dumps(model, indent=2) + "\n").encode("utf8"))
    print("wrote the elevator")


if __name__ == "__main__":
    main()
