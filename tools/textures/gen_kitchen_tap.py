"""Kitchen Tap (蛇口): its pictures, its two models, its blockstate and its item.

Made after the user's photograph of an old brass bib tap (2026-11-15), and to their words on the trials: 「普通に真鍮の
蛇口として作っていいけど、真鍮の色はもう少し落ち着いた感じにしたらいい」, 「接続部だけ黒くしてみたら?」 - copper (「身体に
悪そう」) and steel (「素材がどこから来ましたか?」) were turned down. So:
- a round flange of black iron, a Copper Pipe's end wide, in the middle of the face it is set on, and a short black
  neck out of it - the joint, the one dark part (the pipe's face and the tap's meet: 「接続面が銅管の中心にくるように」);
- the brass body, a bonnet standing up from it with a ring round it, a cap on top, and a cross handle with a ball at
  each end, square to the tap shut and turned an eighth open;
- the spout out of the body's front and down, a knurled collar round its foot and a short nozzle under that, its mouth
  one pixel up over the vessel in the block below.
The brass is drawn here, quieter than the fittings' cast brass: a brushed, olive-gold metal (kitchen_tap_brass), and
the collar's knurling in it (kitchen_tap_knurl). The iron is the Iron Pot's black iron. Drawn facing north, set on the
block to its south; the blockstate turns it. No two faces lie in one plane: where parts meet, a face between is left out.
Run from the repo root:  python tools/textures/gen_kitchen_tap.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
NW = "nookworks:block/"
ALL = ("north", "south", "east", "west", "up", "down")

# an old brass, brushed: olive-gold, quieter than the fittings' (brass_fitting's mid is 226, 186, 80)
BRASS = [(96, 74, 38), (124, 98, 54), (150, 122, 70), (176, 148, 94), (204, 180, 128)]


def brass():
    """The tap's brass: a field of its middle tone, brushed - fine rows a shade up or down, never a pixel's speckle -
    shaded as metal is (texkit.shade)."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        # a row now and then a half shade lighter or darker: brushed, not banded (a whole shade read as wood grain)
        lift = 0.5 if y % 6 == 1 else -0.5 if y % 6 == 4 else 0.0
        mid, to = BRASS[2], BRASS[3] if lift > 0 else BRASS[1]
        colour = tuple(round(m + (o - m) * abs(lift)) for m, o in zip(mid, to))
        for x in range(16):
            img.putpixel((x, y), colour + (255,))
    return texkit.shade(img, 1501, material="metal", **texkit.SMOOTH)


def knurl():
    """The collar's knurling: upright ridges, each lit on its left and in shadow on its right."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), BRASS[(3, 2, 1)[x % 3]] + (255,))
    return texkit.shade(img, 1502, material="metal", keep=[BRASS[1], BRASS[3]], **texkit.SMOOTH)


def box(frm, to, texture, leave=(), cull=None, turn=None):
    """An element with every face on {@code texture} but those in {@code leave} (they lie against another part)."""
    faces = {}
    for side in ALL:
        if side in leave:
            continue
        face = {"texture": "#" + texture}
        if cull == side:
            face["cullface"] = side
        faces[side] = face
    element = {"from": frm, "to": to, "faces": faces}
    if turn:
        element["rotation"] = {"angle": turn, "axis": "y", "origin": [8, 12.5, 11.5]}
    return element


def model(open_):
    turn = 45 if open_ else None
    elements = [
        # the flange, round as a pipe's end and as wide, its back against what it is set on; the neck out of it
        box([5, 4, 15], [11, 12, 16], "iron", cull="south"),
        box([4, 5, 15], [5, 11, 16], "iron", leave=("east",), cull="south"),
        box([11, 5, 15], [12, 11, 16], "iron", leave=("west",), cull="south"),
        box([6.5, 6.5, 13], [9.5, 9.5, 15], "iron", leave=("north", "south")),
        # the brass body
        box([6.5, 6, 10], [9.5, 10, 13], "brass"),
        # the bonnet up from it, a ring round it, the cap on top
        box([7, 10, 10.5], [9, 13, 12.5], "brass", leave=("down", "up")),
        box([6.5, 11, 10], [9.5, 11.5, 13], "brass"),
        box([6.5, 13, 10], [9.5, 13.5, 13], "brass"),
        # the cross handle through the bonnet under the cap, a ball at each end
        box([5, 12, 11], [11, 13, 12], "brass", turn=turn),
        box([7.5, 12, 9], [8.5, 13, 11], "brass", leave=("south",), turn=turn),
        box([7.5, 12, 12], [8.5, 13, 14], "brass", leave=("north",), turn=turn),
        box([3.75, 11.75, 10.75], [5, 13.25, 12.25], "brass", turn=turn),
        box([11, 11.75, 10.75], [12.25, 13.25, 12.25], "brass", turn=turn),
        box([7.25, 11.75, 7.75], [8.75, 13.25, 9], "brass", turn=turn),
        box([7.25, 11.75, 14], [8.75, 13.25, 15.25], "brass", turn=turn),
        # the spout out of the body's front and down, the knurled collar round its foot, the nozzle under it
        box([7, 6.5, 9], [9, 8.5, 10], "brass", leave=("north", "south")),
        box([7, 4, 7], [9, 8.5, 9], "brass", leave=("down",)),
        box([6.5, 2.5, 6.5], [9.5, 4, 9.5], "knurl"),
        box([7.25, 1, 7.25], [8.75, 2.5, 8.75], "brass", leave=("up",)),
    ]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": NW + "kitchen_tap_brass", "brass": NW + "kitchen_tap_brass",
                         "knurl": NW + "kitchen_tap_knurl", "iron": NW + "black_iron"},
            "elements": elements}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    brass().save(ASSETS / "textures/block/kitchen_tap_brass.png")
    knurl().save(ASSETS / "textures/block/kitchen_tap_knurl.png")
    write(ASSETS / "models/block/kitchen_tap.json", model(False))
    write(ASSETS / "models/block/kitchen_tap_open.json", model(True))
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for open_ in (False, True):
            for powered in (False, True):
                for pouring in (False, True):
                    entry = {"model": NW + ("kitchen_tap_open" if open_ else "kitchen_tap")}
                    if y:
                        entry["y"] = y
                    variants[f"facing={facing},open={str(open_).lower()},powered={str(powered).lower()},"
                             f"pouring={str(pouring).lower()}"] = entry
    write(ASSETS / "blockstates/kitchen_tap.json", {"variants": variants})
    write(ASSETS / "items/kitchen_tap.json", {"model": {"type": "minecraft:model", "model": NW + "kitchen_tap"}})
    print("wrote the kitchen tap")


if __name__ == "__main__":
    main()
