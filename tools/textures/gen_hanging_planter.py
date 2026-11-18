"""Hanging Planter: a round glazed bowl hung from the ceiling on four cords, with a trailing plant spilling over it,
and the Thin Rope to hang it from. All original pixel art, and the models and blockstates. Deterministic.

- block/hanging_planter_cord.png                      the cords and the hook
- block/hanging_planter_<plant>.png                   the plant in the bowl: a mound of leaves over the rim, and
  strands spilling down past the bowl near the edges of the plane (cutout)
- block/hanging_planter_<plant>_trail.png, _tip.png   the strands grown on below the planter, one block each: _trail
  tiles top to bottom (each strand leaves the block where it came in), _tip ends them
- block/thin_rope.png, item/thin_rope.png             the thin rope, and the black iron fitting it hangs from
- models/block/..., blockstates/, items/

All the plant textures go on the same four planes (two diagonal, two straight), so a strand leaving the bottom of
the planter carries on at the same place in the block below. The bowl is the Glazed Flower Pot's glaze
(block/glazed_flower_pot*), so the two read as one set.

Run from the repo root:  python tools/textures/gen_hanging_planter.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

PLANTS = {
    # dark edge, mid, light, highlight, stem
    "green": [(30, 84, 36), (58, 126, 52), (96, 164, 74), (150, 204, 112), (52, 98, 44)],
    "pink": [(112, 30, 58), (176, 62, 104), (214, 104, 142), (240, 160, 184), (132, 58, 82)],
}
CORD = [(150, 128, 96), (196, 176, 140), (224, 208, 176)]
HOOK = (170, 172, 178)
IRON = [(22, 22, 26), (40, 40, 46), (62, 62, 70), (92, 92, 102)]  # the thin rope's fitting, dark to light
OUTLINE = (58, 46, 30)

# The strands on each plane: centre column, how far it swings either way, where in its swing it starts, and the
# rows (of every block) that put out a leaf, full or small. One near each side, clear of the bowl in the middle,
# each to its own rhythm so the two never move in step.
STRANDS = ((2.5, 1.5, 0, ((1, True), (4, False), (7, True), (9, False), (12, True), (14, False))),
           (12.5, 2.0, 5, ((0, False), (2, True), (6, True), (8, False), (10, True), (14, True))))


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c if len(c) == 4 else c + (255,))


def cord():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px(img, x, y, CORD[(x + y * 2) % 3])  # a twisted cord
    for x in range(12, 16):
        for y in range(0, 4):
            px(img, x, y, HOOK)
    return img


def sway(strand, row):
    """A strand's column on a row: a slow, even sway either side of its centre, one full swing per block, so the
    row after the last of one block is the first of the next and the strands run on unbroken."""
    centre, reach, phase = strand[:3]
    return round(centre + reach * math.sin(2 * math.pi * (row + phase) / 16))


def strand(img, spec, top, colours, tip_at=None):
    """Draws a strand down the rows from {@code top} to the bottom (or to {@code tip_at}, where it ends in a pale
    growing tip): a thin stem with leaves drooping off it at its own uneven rhythm, mostly on the outside of each
    bend, some small and some full."""
    edge, mid, light, high, stem = colours
    last = 15 if tip_at is None else tip_at
    for y in range(top, last + 1):
        px(img, sway(spec, y), y, stem)
    for row, big in spec[3]:
        if not top <= row <= last - 1:
            continue
        x = sway(spec, row)
        bend = sway(spec, row + 1) - sway(spec, row - 1)
        side = -1 if bend > 0 else 1 if bend < 0 else (1 if row % 2 else -1)  # the outside of the bend
        px(img, x + side, row, mid)
        px(img, x + side, row + 1, light)
        if big:
            px(img, x + side * 2, row + 1, high)
            px(img, x + side * 2, row + 2, mid)
            px(img, x + side, row + 2, edge)
        else:
            px(img, x + side, row + 2, edge)
    if tip_at is not None:
        px(img, sway(spec, tip_at + 1), tip_at + 1, light)


def leaf(img, rng, x, y, colours, over):
    """One small leaf of the mound: lit on top, shaded under."""
    edge, mid, light, high, _ = colours
    for dx, dy, c in ((0, 0, light), (1, 0, mid), (-1, 0, mid), (0, -1, high if over else light), (0, 1, edge),
                      (1, 1, edge if rng.random() < 0.6 else CLEAR)):
        if c != CLEAR and rng.random() < 0.92:
            px(img, x + dx, y + dy, c)


def plant(colours):
    """Rows 0-9 are the mound of leaves over the rim (row 8 is the rim); strands spill from it down the sides."""
    rng = random.Random(8)
    img = Image.new("RGBA", (16, 16), CLEAR)
    for spec in STRANDS:  # the strands first, so the mound sits over where they start
        strand(img, spec, 6, colours)
    for _ in range(64):  # a full mound, highest in the middle, spilling a little over the rim at the sides
        x = rng.randint(0, 15)
        crest = 8 - round(6.5 * math.sqrt(max(0.0, 1 - ((x - 7.5) / 8.5) ** 2)))
        y = rng.randint(crest, 9)
        leaf(img, rng, x, y, colours, y < 5)
    return img


def trail(colours, tip):
    """The strands through one block below the planter; {@code tip} ends each at its own length."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for spec, end in zip(STRANDS, (9, 12)):
        strand(img, spec, 0, colours, end if tip else None)
    return img


def thin_rope():
    """Column 0 is the cord; the 8x8 at (4, 0) is the iron fitting's top and sides."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        px(img, 0, y, CORD[(y * 2) % 3])
    for y in range(0, 8):
        for x in range(4, 12):
            rim = x in (4, 11) or y in (0, 7)
            px(img, x, y, IRON[0] if rim else IRON[1 + (x + y) % 2])
    px(img, 5, 1, IRON[3])
    px(img, 6, 1, IRON[2])
    return img


def thin_rope_item():
    """A small coil of the cord with its end hanging loose."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    cx, cy = 7.5, 6.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, (y - cy) * 1.2)
            if 3.4 <= d <= 5.4:
                a = math.atan2(y - cy, x - cx)
                px(img, x, y, CORD[int((a + math.pi) / (2 * math.pi) * 12 + d) % 3])
    for i, (x, y) in enumerate(((11, 11), (11, 12), (12, 13), (12, 14))):
        px(img, x, y, CORD[i % 3])
    return texkit.outline(img)  # ringed in the cord's own colours


def planes(texture):
    """Four planes through the middle of the block, full height: straight across both ways and the two diagonals."""
    faces = {"texture": texture}
    out = []
    for angle in (-45, 0, 45):
        out.append({"from": [0, 0, 8], "to": [16, 16, 8], "shade_direction_override": "up",
                    **({"rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle, "rescale": False}} if angle else {}),
                    "faces": {"north": {**faces, "uv": [0, 0, 16, 16]}, "south": {**faces, "uv": [16, 0, 0, 16]}}})
    out.append({"from": [8, 0, 0], "to": [8, 16, 16], "shade_direction_override": "up",
                "faces": {"east": {**faces, "uv": [0, 0, 16, 16]}, "west": {**faces, "uv": [16, 0, 0, 16]}}})
    return out


def model(name):
    t = {"particle": f"nookworks:block/hanging_planter_{name}", "glazed": "nookworks:block/glazed_flower_pot",
         "glazed_top": "nookworks:block/glazed_flower_pot_top", "cord": "nookworks:block/hanging_planter_cord",
         "plant": f"nookworks:block/hanging_planter_{name}"}
    sides = ("north", "east", "south", "west")

    def box(frm, to, uv_y):
        return {"from": frm, "to": to, "faces": {**{d: {"texture": "#glazed", "uv": [frm[0], uv_y[0], to[0], uv_y[1]]} for d in sides},
                                                 "down": {"texture": "#glazed", "uv": [frm[0], frm[2], to[0], to[2]]}}}

    bowl = [
        box([6, 2, 6], [10, 3, 10], (14, 15)),        # the foot of the bowl
        box([4, 3, 4], [12, 5, 12], (12, 14)),        # the round of it
        {"from": [3, 5, 3], "to": [13, 8, 13],       # the wide top, with soil in the rim
         "faces": {**{d: {"texture": "#glazed", "uv": [3, 8, 13, 11]} for d in sides},
                   "up": {"texture": "#glazed_top", "uv": [3, 3, 13, 13]},
                   "down": {"texture": "#glazed", "uv": [3, 3, 13, 13]}}},
    ]
    # four cords from the rim up to the hook, each leaning in by 22.5 degrees
    cords = []
    for axis, sign, lo, hi in (("x", 1, [7.8, 7.6, 3.9], [8.2, 16.4, 4.3]), ("x", -1, [7.8, 7.6, 11.7], [8.2, 16.4, 12.1]),
                               ("z", -1, [3.9, 7.6, 7.8], [4.3, 16.4, 8.2]), ("z", 1, [11.7, 7.6, 7.8], [12.1, 16.4, 8.2])):
        centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
        cords.append({"from": lo, "to": hi, "shade_direction_override": "up",
                      "rotation": {"origin": [centre[0], 7.6, centre[2]], "axis": axis, "angle": 22.5 * sign},
                      "faces": {d: {"texture": "#cord", "uv": [0, 0, 1, 9]} for d in sides}})
    hook = {"from": [7.5, 15, 7.5], "to": [8.5, 16, 8.5],
            "faces": {d: {"texture": "#cord", "uv": [12, 0, 13, 1]} for d in sides + ("up", "down")}}

    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": t, "elements": bowl + cords + [hook] + planes("#plant"),
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.62, 0.62, 0.62]}}}


def trail_model(texture):
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": {"particle": texture, "plant": texture}, "elements": planes("#plant")}


def thin_rope_model(anchored):
    sides = ("north", "east", "south", "west")
    t = "#rope"
    rope = {"from": [7.5, 0, 7.5], "to": [8.5, 16, 8.5],
            "faces": {**{d: {"texture": t, "uv": [0, 0, 1, 16]} for d in sides},
                      "down": {"texture": t, "uv": [0, 0, 1, 1]}}}
    elements = [rope]
    if anchored:  # a small black iron plate on the ceiling, and a boss under it that the cord is tied through
        elements += [
            {"from": [6, 15, 6], "to": [10, 16, 10],
             "faces": {**{d: {"texture": t, "uv": [4, 0, 8, 1]} for d in sides},
                       "down": {"texture": t, "uv": [4, 0, 8, 4]}, "up": {"texture": t, "uv": [4, 0, 8, 4], "cullface": "up"}}},
            {"from": [7, 13.5, 7], "to": [9, 15, 9],
             "faces": {**{d: {"texture": t, "uv": [9, 2, 11, 3.5]} for d in sides},
                       "down": {"texture": t, "uv": [9, 2, 11, 4]}}},
        ]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "nookworks:block/thin_rope", "rope": "nookworks:block/thin_rope"}, "elements": elements}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures/block"
    cord().save(tex / "hanging_planter_cord.png")
    for name, colours in PLANTS.items():
        plant(colours).save(tex / f"hanging_planter_{name}.png")
        trail(colours, False).save(tex / f"hanging_planter_{name}_trail.png")
        trail(colours, True).save(tex / f"hanging_planter_{name}_tip.png")
        write_json(ASSETS / f"models/block/hanging_planter_{name}.json", model(name))
        write_json(ASSETS / f"blockstates/hanging_planter_{name}.json",
                   {"variants": {"": {"model": f"nookworks:block/hanging_planter_{name}"}}})
        write_json(ASSETS / f"items/hanging_planter_{name}.json",
                   {"model": {"type": "minecraft:model", "model": f"nookworks:block/hanging_planter_{name}"}})
        for part in ("trail", "tip"):
            write_json(ASSETS / f"models/block/hanging_planter_{name}_{part}.json",
                       trail_model(f"nookworks:block/hanging_planter_{name}_{part}"))
        write_json(ASSETS / f"blockstates/hanging_planter_{name}_trail.json", {"variants": {
            "tip=false": {"model": f"nookworks:block/hanging_planter_{name}_trail"},
            "tip=true": {"model": f"nookworks:block/hanging_planter_{name}_tip"}}})
    thin_rope().save(tex / "thin_rope.png")
    thin_rope_item().save(ASSETS / "textures/item/thin_rope.png")
    write_json(ASSETS / "models/block/thin_rope.json", thin_rope_model(False))
    write_json(ASSETS / "models/block/thin_rope_anchored.json", thin_rope_model(True))
    # the Thin Rope's blockstate is tools/gen_rope_ties.py's: it adds the ties up to a support's beam or a slab
    write_json(ASSETS / "models/item/thin_rope.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/thin_rope"}})
    write_json(ASSETS / "items/thin_rope.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/thin_rope"}})
    print("wrote the hanging planters and the thin rope")


if __name__ == "__main__":
    main()
