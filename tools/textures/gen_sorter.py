"""The Item Sorter and the Precision Sorter, drawn as the machines are: the black steel frame two pixels wide with pale
studs at its corners, and inside it a panel - pale steel for the Item Sorter, dark steel with brass for the Precision
Sorter. On the front the panel is in the metal of the Sorter Gear fitted - gold, diamond, netherite, Velarium - so a
row of sorters shows at a glance which are geared.

- {kind}_sorter_front[_swapped][_{gear}] : the filter plate - three cells and a brass arrow to the outlet registered
                                           items leave by - in a panel of the gear's metal (the sorter's own with none)
- {kind}_sorter_side                     : an outlet port, left and right
- {kind}_sorter_top                      : the intake, a grated funnel
- {kind}_sorter_back / _bottom           : the panel alone

It also writes the sorters' models (a cube, a texture a face) and blockstates, and those of 0.2.0's Golden, Diamond and
Netherite Item Sorters, which show as Item Sorters with that gear for the moment they last.
Drawn flat, then finished with texkit. Original work, deterministic.
Run from the repo root:  python tools/textures/gen_sorter.py
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
DARK_HI, DARK, DARK_LO, DARK_DK = (72, 76, 82), (50, 53, 58), (38, 40, 44), (26, 28, 31)
BRASS_HI, BRASS, BRASS_LO = (238, 204, 116), (200, 156, 68), (146, 106, 42)
LAMP, LAMP_HI = (70, 200, 96), (170, 250, 170)
FRAME_KEEP = (STUD_HI, STUD, STUD_LO, STUD_DK, BAR, BAR_IN, BAR_SH)

# the panel metals: highlight, body, shade, deep shade (the storage barrels' bands, for one family)
KINDS = {
    "item": ((224, 227, 231), (199, 202, 207), (164, 168, 175), (124, 128, 136)),
    "precision": (DARK_HI, DARK, DARK_LO, DARK_DK),
}
GEARS = {
    "gold": ((255, 226, 96), (230, 184, 56), (178, 132, 38), (120, 84, 24)),
    "diamond": ((150, 250, 238), (86, 206, 194), (52, 150, 142), (30, 96, 92)),
    # netherite's dark metal with the warm sheen it takes where it catches the light: a pale edge over a reddish-brown
    # body. (Drawn as dark as the ingot, the ring vanished into the black frame: a netherite sorter looked ungeared.)
    "netherite": ((204, 176, 156), (122, 86, 74), (88, 58, 52), (56, 36, 34)),
    "velarium": ((196, 168, 240), (140, 100, 206), (98, 66, 164), (64, 40, 116)),
}
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c) + (255,))


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def framed(metal):
    """The frame, two pixels wide with a stud in each corner, round a panel of the tier's metal lit from the top left."""
    hi, mid, lo, dk = metal
    img = blank()
    rect(img, 0, 0, 15, 15, BAR)
    rect(img, 1, 1, 14, 14, BAR_IN)
    rect(img, 2, 2, 13, 13, mid)
    rect(img, 2, 2, 13, 2, hi)
    rect(img, 2, 2, 2, 13, hi)
    rect(img, 2, 13, 13, 13, lo)
    rect(img, 13, 3, 13, 13, lo)
    for x, y in ((0, 0), (14, 0), (0, 14), (14, 14)):
        put(img, x, y, STUD_HI if (x, y) == (0, 0) else STUD)
        put(img, x + 1, y, STUD)
        put(img, x, y + 1, STUD_LO)
        put(img, x + 1, y + 1, STUD_DK)
    return img


def side(metal):
    """An outlet port: a square mouth in a dark collar, its lip lit on the top and left."""
    hi, mid, lo, dk = metal
    img = framed(metal)
    rect(img, 4, 4, 11, 11, BAR)
    rect(img, 5, 5, 10, 10, BAR_SH)
    rect(img, 4, 4, 11, 4, dk)
    rect(img, 4, 4, 4, 11, dk)
    rect(img, 6, 6, 9, 9, (8, 8, 10))
    put(img, 5, 5, DARK_HI)
    for x in (4, 11):  # bolts at the collar's corners
        for y in (4, 11):
            put(img, x, y, STUD_LO)
    return img


def top(metal):
    """The intake: a funnel sunk into the panel, a grate of two bars across it."""
    hi, mid, lo, dk = metal
    img = framed(metal)
    rect(img, 3, 3, 12, 12, dk)
    rect(img, 4, 4, 11, 11, BAR)
    rect(img, 5, 5, 10, 10, BAR_SH)
    rect(img, 6, 6, 9, 9, (8, 8, 10))
    for y in (6, 9):
        rect(img, 4, y, 11, y, DARK_HI)
    rect(img, 3, 3, 12, 3, lo)
    return img


def front(metal, pointing_right, precision=False):
    """The filter plate: a nameplate strip, three cells for what is registered, and the brass arrow to its outlet."""
    hi, mid, lo, dk = metal
    img = framed(metal)
    rect(img, 3, 4, 12, 12, DARK_LO)
    rect(img, 3, 4, 12, 4, BAR)
    rect(img, 3, 12, 12, 12, DARK_HI)
    for cx in (4, 7, 10):  # the three cells
        rect(img, cx, 5, cx + 1, 6, BAR_SH)
        put(img, cx + 1, 6, DARK)
    shaft = range(4, 10) if pointing_right else range(6, 12)
    for x in shaft:
        put(img, x, 9, BRASS_HI)
        put(img, x, 10, BRASS_LO)
    tip = 10 if pointing_right else 5
    step = 1 if pointing_right else -1
    for i, h in enumerate((3, 2, 1)):
        x = tip + i * step
        for y in range(9 - h + 1, 10 + h):
            put(img, x, y, BRASS_HI if y <= 9 else BRASS_LO)
    if precision:  # the Precision Sorter's lamp, and brass for its frame's studs
        put(img, 12, 2, LAMP_HI)
        put(img, 11, 2, LAMP)
        rect(img, 3, 2, 9, 2, BRASS)
    return img


def plain(metal):
    img = framed(metal)
    rect(img, 4, 7, 11, 8, metal[2])  # a seam across the panel
    rect(img, 4, 7, 11, 7, metal[3])
    return img


def suffix(swapped, gear):
    return ("_swapped" if swapped else "") + ("" if gear == "none" else "_" + gear)


def model(kind, swapped, gear):
    t = lambda n: f"nookworks:block/{kind}_sorter_{n}"
    tex = {"particle": t("side"), "front": t("front" + suffix(swapped, gear)), "side": t("side"),
           "top": t("top"), "back": t("back"), "bottom": t("bottom")}
    faces = {"north": "#front", "east": "#side", "south": "#back", "west": "#side", "up": "#top", "down": "#bottom"}
    return {"parent": "minecraft:block/block", "textures": tex, "elements": [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {s: {"texture": f, "cullface": s} for s, f in faces.items()}}]}


def states(kind, fixed_gear=None):
    """Facing, swapped and gear; a sorter of 0.2.0 shows as an Item Sorter with its own gear whatever its state says."""
    variants = {}
    for facing, y in FACINGS:
        for swapped in (False, True):
            for gear in ("none",) + tuple(GEARS):
                shown = fixed_gear or gear
                variant = {"model": f"nookworks:block/{kind}_sorter" + suffix(swapped, shown)}
                if y:
                    variant["y"] = y
                variants[f"facing={facing},gear={gear},swapped={str(swapped).lower()}"] = variant
    return {"variants": variants}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf8"))


def main():
    seed = 500
    keep = FRAME_KEEP + (BRASS_HI, BRASS_LO, BRASS, LAMP, LAMP_HI)
    for kind, metal in KINDS.items():
        precise = kind == "precision"
        images = {"side": side(metal), "top": top(metal), "back": plain(metal), "bottom": plain(metal)}
        for gear, panel in (("none", metal),) + tuple(GEARS.items()):
            for swapped in (False, True):
                images["front" + suffix(swapped, gear)] = front(panel, not swapped, precise)
        for name, img in images.items():
            texkit.finish(img, seed, keep=keep, **texkit.SMOOTH).save(OUT / f"{kind}_sorter_{name}.png")
            seed += 1
        for gear in ("none",) + tuple(GEARS):
            for swapped in (False, True):
                write_json(ASSETS / f"models/block/{kind}_sorter{suffix(swapped, gear)}.json", model(kind, swapped, gear))
        write_json(ASSETS / f"blockstates/{kind}_sorter.json", states(kind))
        write_json(ASSETS / f"items/{kind}_sorter.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{kind}_sorter"}})
    for old in ("gold", "diamond", "netherite"):
        write_json(ASSETS / f"blockstates/{old}_sorter.json", states("item", old))
        write_json(ASSETS / f"items/{old}_sorter.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/item_sorter_{old}"}})
    print("wrote the sorters")


if __name__ == "__main__":
    main()
