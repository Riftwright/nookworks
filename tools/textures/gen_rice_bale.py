"""The Rice Bale (kome-dawara): its textures, its model and its blockstate. Original pixel art, drawn in a few clean
colours and finished lightly with texkit.shade (plain straw, SMOOTH, the ropes and the knot kept exact). Deterministic.

The bale lies on its side, as hay does: a block of golden rice straw, its corners taken off twice so its section is an
octagon and it reads round - and stacks into a storehouse pile with the gaps showing between the bales. Its stalks run
its length, bound round with two twisted straw ropes that stand proud of it (their shadow on the straw under them), and
a rope runs its length down the middle of each side, over the ends. Its ends are the round straw lids (sandawara):
eight wedges of straw laid out from the middle under a rim of straw bent round and twisted, and across each the rope
from the sides, crossing in a knot at the middle; round the lid the sack's straw is gathered in.

- block/rice_bale_side.png   the straw of the sack, the stalks running down the texture (the bale's length), the two
                             ropes across it and the one along it
- block/rice_bale_end.png    the lid, the rope crossing it
- models/block/rice_bale.json (lying along z), blockstates/rice_bale.json (along x, z; y stands it on end), items/

Run from the repo root:  python tools/textures/gen_rice_bale.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

# Rice straw, paler and softer than hay: shadow, shade, body, light, highlight.
STRAW = [(150, 118, 58), (196, 162, 92), (222, 194, 124), (238, 216, 154), (250, 236, 190)]
# Straw rope, twisted, browner and darker than the sack so it reads at a glance.
ROPE = [(84, 54, 22), (122, 82, 34), (160, 116, 56), (196, 154, 86)]
KNOT = [(70, 46, 18), (104, 70, 28), (150, 106, 50)]

# A stalk to a column (its tone in STRAW); a node or break in a stalk, and a glint of light, at (column, row).
COLUMNS = (2, 3, 2, 2, 3, 1, 2, 3, 2, 3, 2, 1, 3, 2, 3, 2)
NODES = ((5, 8), (11, 9), (2, 1), (14, 14), (8, 6))
GLINTS = ((1, 8), (7, 9), (12, 7), (9, 1), (4, 14))
# The two ropes round the bale, as the rows they take: two each, a third of the way in from either end.
ROPES = ((3, 4), (11, 12))


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def darken(img, x, y, f):
    r, g, b, a = img.getpixel((x, y))
    img.putpixel((x, y), (int(r * f), int(g * f), int(b * f), a))


def side():
    """The sack: stalks down the texture, a shade darker where the sack turns in to its lids (the top and bottom rows);
    across it the two ropes, each strand a light over a shade running down to the right, lit from above, and the
    rope's shadow on the straw under it."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            tone = COLUMNS[x] - (1 if y in (0, 15) else 0)
            px(img, x, y, STRAW[tone])
    for x, y in NODES:
        px(img, x, y, STRAW[0])
    for x, y in GLINTS:
        px(img, x, y, STRAW[4])
    for top, bottom in ROPES:
        for x in range(16):
            px(img, x, top, ROPE[(3, 3, 2)[x % 3]])
            px(img, x, bottom, ROPE[(2, 1, 2)[(x + 2) % 3]])
        for x in range(16):
            darken(img, x, bottom + 1, 0.82)
    for y in range(16):  # the rope along the bale, over the ones round it, lit from the left, its shadow to the right
        px(img, 7, y, ROPE[(3, 3, 2)[y % 3]])
        px(img, 8, y, ROPE[(2, 1, 2)[(y + 2) % 3]])
        darken(img, 9, y, 0.85)
    return img


def end():
    """The lid, filling the end: round it the sack's straw gathered in; its rim of twisted straw; eight wedges of
    straw, light and shade, laid out from the middle, darker under the rim; over it the ropes from the sides, up and
    across, knotted where they cross (a knot alone in the middle read as a mole on a face)."""
    img = Image.new("RGBA", (16, 16))
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            turn = (math.atan2(y - c, x - c) + math.pi) / (2 * math.pi)
            if d > 7.7:
                px(img, x, y, STRAW[1] if (x * 3 + y) % 4 else STRAW[0])
            elif d > 6.2:
                px(img, x, y, ROPE[1 + int(turn * 18) % 3])
            else:
                px(img, x, y, STRAW[(3 if int(turn * 8) % 2 else 2) - (1 if d > 5.2 else 0)])
    for t in range(16):  # the rope, up the lid and across it, as far as its rim
        if abs(t - c) <= 7.2:
            px(img, 7, t, ROPE[(3, 3, 2)[t % 3]])
            px(img, 8, t, ROPE[(2, 1, 2)[(t + 2) % 3]])
            px(img, t, 7, ROPE[(3, 3, 2)[t % 3]])
            px(img, t, 8, ROPE[(2, 1, 2)[(t + 2) % 3]])
    for x, y, k in ((7, 7, 2), (8, 7, 1), (7, 8, 1), (8, 8, 0)):  # the knot where they cross
        px(img, x, y, KNOT[k])
    return img


# The section, three boxes over each other: the full height a notch in from the sides, the full width a notch in from
# top and bottom, and one a pixel in all round between them - an octagon with its corners stepped twice.
SECTION = ((2, 0, 14, 16), (0, 2, 16, 14), (1, 1, 15, 15))


def model():
    """Lying along z, the lids north and south. The sides take the straw with its rows along the bale (turned a
    quarter on the east and west faces, whose texture runs up the face)."""
    def face(uv, rotation=0, cull=None):
        f = {"uv": uv, "texture": "#end" if cull in ("north", "south") else "#side"}
        if rotation:
            f["rotation"] = rotation
        if cull:
            f["cullface"] = cull
        return f
    elements = []
    for x0, y0, x1, y1 in SECTION:
        elements.append({"from": [x0, y0, 0], "to": [x1, y1, 16], "faces": {
            "north": face([16 - x1, 16 - y1, 16 - x0, 16 - y0], cull="north"),
            "south": face([x0, 16 - y1, x1, 16 - y0], cull="south"),
            "up": face([x0, 0, x1, 16]), "down": face([x0, 0, x1, 16]),
            "east": face([y0, 0, y1, 16], 90), "west": face([y0, 0, y1, 16], 90)}})
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "nookworks:block/rice_bale_side", "side": "nookworks:block/rice_bale_side",
                         "end": "nookworks:block/rice_bale_end"},
            "elements": elements}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures/block"
    keep = ROPE + KNOT
    texkit.shade(side(), 700, keep=keep, tile=True, **texkit.SMOOTH).save(tex / "rice_bale_side.png")
    texkit.shade(end(), 701, keep=keep, **texkit.SMOOTH).save(tex / "rice_bale_end.png")
    for gone in ("rice_bale_side_turned", "rice_bale_rope", "rice_bale_knot"):  # the round bale's, before
        (tex / f"{gone}.png").unlink(missing_ok=True)
    write_json(ASSETS / "models/block/rice_bale.json", model())
    write_json(ASSETS / "blockstates/rice_bale.json", {"variants": {
        "axis=z": {"model": "nookworks:block/rice_bale"},
        "axis=x": {"model": "nookworks:block/rice_bale", "y": 90},
        "axis=y": {"model": "nookworks:block/rice_bale", "x": 90}}})
    write_json(ASSETS / "items/rice_bale.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/rice_bale"}})
    print("wrote the rice bale")


if __name__ == "__main__":
    main()
