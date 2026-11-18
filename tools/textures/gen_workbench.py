"""Workbenches: a crafting table built as a machinist's bench, in the machines' black steel or in brass and walnut.

A table, not a cube: four square legs on splayed feet, a top two pixels thick, an apron under it with a drawer at the
front, a shelf low between the legs with a toolbox on it, and on the top a bench vise at the front right, a blueprint
laid out (a crafting grid drawn on it) and a hammer put down behind it. Set among the machines it reads as part of the
workshop rather than the one wooden thing left in it.

The body's sides are drawn as elevations - the bench seen square from the front and from the side, as a draughtsman
draws it - and every body part takes its slice of them from where it stands, so the legs, apron, drawer and shelf of
one side are dressed by one drawing. The small things on and under the top have their own materials.

Drawn flat, then shaded with texkit.shade (walnut wood, the blueprint plain, the rest metal). Original work, deterministic.
Run from the repo root:  python tools/textures/gen_workbench.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"

# the machines' frame and steels (gen_machine_faces.py), and brass
STUD_HI, STUD, STUD_LO, STUD_DK = (219, 222, 227), (174, 177, 182), (134, 137, 142), (89, 92, 96)
BAR, BAR_IN, BAR_SH = (36, 38, 42), (22, 24, 27), (12, 13, 15)
PALE_HI, PALE, PALE_LO, PALE_DK = (224, 227, 231), (199, 202, 207), (164, 168, 175), (124, 128, 136)
DARK_HI, DARK, DARK_LO, DARK_DK = (72, 76, 82), (50, 53, 58), (38, 40, 44), (26, 28, 31)
BRASS_HI, BRASS, BRASS_LO, BRASS_DK = (238, 204, 116), (200, 156, 68), (146, 106, 42), (96, 66, 26)
WALNUT_HI, WALNUT, WALNUT_LO, WALNUT_DK = (138, 96, 62), (110, 74, 46), (86, 56, 34), (60, 38, 22)
PAPER_HI, PAPER, PAPER_LO, INK = (70, 128, 196), (46, 98, 168), (34, 74, 132), (214, 232, 246)
RED_HI, RED, RED_LO, RED_DK = (206, 72, 58), (168, 46, 38), (124, 30, 26), (80, 18, 16)


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c) + (255,))


def rect(img, x0, y0, x1, y1, c):
    """x0..x1, y0..y1, both inclusive."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


class Metal:
    """A variant's frame: the colours its legs, rim and apron are drawn in."""

    def __init__(self, hi, mid, lo, dark, stud_hi, stud, rivet):
        self.hi, self.mid, self.lo, self.dark = hi, mid, lo, dark
        self.stud_hi, self.stud, self.rivet = stud_hi, stud, rivet


IRON = Metal(DARK_HI, BAR, BAR_IN, BAR_SH, STUD_HI, STUD_LO, STUD)
BRASS_FRAME = Metal(BRASS_HI, BRASS, BRASS_LO, BRASS_DK, (255, 236, 170), BRASS_LO, BRASS_HI)


def elevation(m, drawer):
    """
    The bench seen square from one side (row = 16 - y): the top's edge in rows 2-3, the apron in 4-5, the legs in the
    outer two columns each side from row 4 down, their feet in row 15, the shelf's edge in row 12. At the front the
    drawer sits in the apron, rows 4-6, columns 5-10, with its brass pull in row 5.
    """
    img = blank()
    # the top's edge: a band of the frame, studs at both ends
    rect(img, 0, 2, 15, 3, m.mid)
    rect(img, 0, 2, 15, 2, m.hi)
    rect(img, 0, 3, 15, 3, m.lo)
    for x in (0, 15):
        put(img, x, 2, m.stud_hi)
        put(img, x, 3, m.stud)
    for x in (5, 10):
        put(img, x, 3, m.rivet)
    # the apron, set back under the top
    rect(img, 3, 4, 12, 5, m.lo)
    rect(img, 3, 5, 12, 5, m.dark)
    for x in (4, 11):
        put(img, x, 4, m.rivet)
    # the legs, lit on the left, and where they meet the top a stud
    for x0 in (1, 13):
        rect(img, x0, 4, x0 + 1, 14, m.mid)
        rect(img, x0, 4, x0, 14, m.hi)
        rect(img, x0 + 1, 4, x0 + 1, 14, m.lo)
        put(img, x0, 4, m.stud_hi)
        put(img, x0 + 1, 4, m.stud)
    # the feet, one row, wider than the legs
    for x0 in (0, 12):
        rect(img, x0, 15, x0 + 3, 15, m.lo)
        put(img, x0, 15, m.dark)
        put(img, x0 + 3, 15, m.dark)
        put(img, x0 + 1, 15, m.mid)
    # the shelf's edge between the legs
    rect(img, 2, 12, 13, 12, DARK if m is IRON else WALNUT_LO)
    put(img, 2, 12, m.dark)
    put(img, 13, 12, m.dark)
    if drawer:
        rect(img, 5, 4, 10, 6, DARK_LO if m is IRON else WALNUT)
        rect(img, 5, 4, 10, 4, DARK_HI if m is IRON else WALNUT_HI)
        rect(img, 5, 6, 10, 6, BAR_SH if m is IRON else WALNUT_DK)
        put(img, 5, 5, BAR_IN if m is IRON else WALNUT_LO)
        put(img, 10, 5, BAR_IN if m is IRON else WALNUT_DK)
        put(img, 7, 5, BRASS_HI)  # the pull (its own element carries brass; this is it seen from the front)
        put(img, 8, 5, BRASS)
    return img


def top_iron():
    """A steel plate in a black rim, riveted round, worn bright where the work goes, with a stain of oil."""
    img = blank()
    rect(img, 1, 1, 14, 14, PALE_LO)
    rect(img, 0, 0, 15, 0, BAR)
    rect(img, 0, 15, 15, 15, BAR_SH)
    rect(img, 0, 0, 0, 15, BAR)
    rect(img, 15, 0, 15, 15, BAR_SH)
    for p in (0, 15):
        put(img, p, p, STUD_HI)
        put(img, 15 - p, p, STUD_LO)
    for x in (4, 8, 11):  # rivets along the rim, inside it
        put(img, x, 1, PALE_DK)
        put(img, x, 14, PALE_DK)
    for y in (4, 8, 11):
        put(img, 1, y, PALE_DK)
        put(img, 14, y, PALE_DK)
    rect(img, 1, 7, 14, 7, PALE_DK)  # the seam between two plates
    rect(img, 1, 8, 14, 8, PALE)
    for x, y in ((3, 3), (4, 3), (6, 12), (7, 12), (8, 13), (12, 5), (11, 10), (12, 10)):  # bright where it is worn
        put(img, x, y, PALE)
    for x, y in ((9, 3), (10, 3), (9, 4), (3, 10)):  # oil
        put(img, x, y, PALE_DK)
    return img


def top_brass():
    """Walnut boards in a brass rim, a brass strip inlaid across where two boards meet, worn pale where the work goes."""
    img = blank()
    rect(img, 1, 1, 14, 14, WALNUT)
    for y in (3, 6, 10, 13):  # grain along the boards
        for x in range(1, 15):
            if (x * 5 + y * 3) % 7 < 3:
                put(img, x, y, WALNUT_LO)
    for x, y in ((4, 4), (5, 4), (9, 11), (10, 12), (3, 12)):
        put(img, x, y, WALNUT_HI)
    rect(img, 1, 7, 14, 8, WALNUT_DK)
    rect(img, 1, 7, 14, 7, BRASS)  # the inlaid strip
    rect(img, 0, 0, 15, 0, BRASS_HI)
    rect(img, 0, 0, 0, 15, BRASS)
    rect(img, 15, 0, 15, 15, BRASS_LO)
    rect(img, 0, 15, 15, 15, BRASS_DK)
    for p in (0, 15):
        put(img, p, p, (255, 236, 170))
    for x in (4, 11):
        put(img, x, 1, BRASS_LO)
        put(img, x, 14, BRASS_LO)
    return img


def shelf(m):
    """The low shelf: a plate on the iron bench, a board on the brass one, its edges dark."""
    img = blank()
    if m is IRON:
        rect(img, 0, 0, 15, 15, DARK)
        for i in range(0, 16, 4):
            rect(img, 0, i, 15, i, DARK_LO)
        for x, y in ((3, 2), (9, 6), (12, 13), (6, 10)):
            put(img, x, y, DARK_HI)
    else:
        rect(img, 0, 0, 15, 15, WALNUT_LO)
        for i in range(0, 16, 4):
            rect(img, 0, i, 15, i, WALNUT_DK)
        for x, y in ((3, 2), (9, 6), (12, 13), (6, 10)):
            put(img, x, y, WALNUT)
    return img


def bottom(m):
    """Underneath: bare metal and shadow."""
    img = blank()
    rect(img, 0, 0, 15, 15, m.lo)
    rect(img, 0, 0, 15, 0, m.dark)
    rect(img, 0, 15, 15, 15, m.dark)
    rect(img, 0, 0, 0, 15, m.dark)
    rect(img, 15, 0, 15, 15, m.dark)
    return img


VISE_HI, VISE, VISE_LO = (128, 148, 170), (92, 112, 136), (72, 88, 108)


def vise():
    """
    Cast iron painted slate blue, as bench vises are, the paint worn to bare metal along the edges. The vise's faces are
    a pixel or two high and take rows 0-3 of this (its jaws row 0, its base row 1, the base's top between the jaws rows
    1-3), so each row is one clean band: lit along the top of each part, the slate below, a shade under it. Finished
    smooth with the bands and the worn spots kept (a rough finish broke the bands into salt and pepper), and no shade
    darker than the paint's own.
    """
    img = blank()
    rect(img, 0, 0, 15, 15, VISE)
    for y in range(0, 16, 4):
        rect(img, 0, y, 15, y, VISE_HI)
    for y in range(3, 16, 4):
        rect(img, 0, y, 15, y, VISE_LO)
    for x, y in ((2, 1), (7, 2), (12, 1), (4, 6), (10, 9), (14, 13)):
        put(img, x, y, PALE_DK)
    return img


def blueprint():
    """
    The sheet on the bench (it covers columns 2-8, rows 4-10 of the top): blue paper with a gear drawn on it in white
    - a ring with four teeth and the bore in the middle - and a corner lifting. (A drawn 3x3 grid read as a window.)
    """
    img = blank()
    rect(img, 0, 0, 15, 15, PAPER)
    gear = ("..X.X..",
            ".XXXXX.",
            "XX...XX",
            ".X.X.X.",
            "XX...XX",
            ".XXXXX.",
            "..X.X..")
    for dy, row in enumerate(gear):
        for dx, c in enumerate(row):
            if c == "X":
                put(img, 2 + dx, 4 + dy, INK)
    put(img, 2, 4, PAPER_HI)  # the sheet's edge catching the light, and its far corner lifting
    put(img, 8, 10, PAPER_LO)
    put(img, 8, 4, PAPER_LO)
    return img


def toolbox():
    """A red steel box with a dark lid seam and a steel latch."""
    img = blank()
    rect(img, 0, 0, 15, 15, RED)
    rect(img, 0, 9, 15, 9, RED_DK)
    rect(img, 0, 10, 15, 10, RED_HI)
    rect(img, 0, 15, 15, 15, RED_LO)
    rect(img, 7, 10, 8, 11, PALE)
    rect(img, 5, 7, 10, 7, BAR)  # the handle, seen on the lid
    return img


def brass_part():
    img = blank()
    rect(img, 0, 0, 15, 15, BRASS)
    rect(img, 0, 0, 15, 4, BRASS_HI)
    rect(img, 0, 11, 15, 15, BRASS_LO)
    return img


def steel_part():
    img = blank()
    rect(img, 0, 0, 15, 15, PALE_LO)
    rect(img, 0, 0, 15, 4, PALE)
    rect(img, 0, 11, 15, 15, PALE_DK)
    return img


# ---------------------------------------------------------------------------------------------------------- models

def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": {side: {"texture": tex, **({"cullface": side} if cull else {})}
                                              for side, (tex, cull) in faces.items()}}


def body_faces(up=None, down=None, sides=("north", "east", "south", "west"), inner=()):
    """Faces of a body part: the front elevation to the north, the side elevation elsewhere, the dark bottom inside."""
    faces = {}
    for side in sides:
        faces[side] = ("#bottom" if side in inner else "#front" if side == "north" else "#side", False)
    if up:
        faces["up"] = (up, False)
    if down:
        faces["down"] = (down, down == "#bottom_cull")
    return faces


def workbench_model(variant):
    els = []
    # feet and legs
    for x, z in ((0, 0), (12, 0), (0, 12), (12, 12)):
        els.append(box([x, 0, z], [x + 4, 1, z + 4], body_faces(up="#shelf", down="#bottom")))
        els.append(box([x + 1, 1, z + 1], [x + 3, 12, z + 3], body_faces()))
    # the top
    els.append(box([0, 12, 0], [16, 14, 16], body_faces(up="#top", down="#bottom")))
    # the apron, each side with its face out and its back in the dark; its underside shows from below, between the legs
    els.append(box([3, 10, 1], [13, 12, 2], body_faces(sides=("north", "south"), inner=("south",), down="#bottom")))
    els.append(box([3, 10, 14], [13, 12, 15], body_faces(sides=("north", "south"), inner=("north",), down="#bottom")))
    els.append(box([1, 10, 3], [2, 12, 13], body_faces(sides=("east", "west"), inner=("east",), down="#bottom")))
    els.append(box([14, 10, 3], [15, 12, 13], body_faces(sides=("east", "west"), inner=("west",), down="#bottom")))
    # the drawer and its pull; the drawer's back shows below the apron, so it is closed in the dark too
    els.append(box([5, 9, 0.5], [11, 12, 1], body_faces(inner=("south",), down="#bottom")))
    els.append(box([7, 10, 0], [9, 11, 0.5], {s: ("#brass", False) for s in ("north", "east", "west", "up", "down")}))
    # the shelf, and the toolbox on it
    els.append(box([2, 3, 2], [14, 4, 14], body_faces(up="#shelf", down="#bottom")))
    els.append(box([4, 4, 6], [10, 7, 10], {s: ("#toolbox", False) for s in ("north", "east", "south", "west", "up")}))
    # on the top: the vise at the front right (its base, the jaws, the screw), the blueprint, the hammer
    els.append(box([11, 14, 1], [15, 15, 4], {s: ("#vise", False) for s in ("north", "east", "south", "west", "up")}))
    els.append(box([11, 15, 3], [15, 16, 4], {s: ("#vise", False) for s in ("north", "east", "south", "west")} | {"up": ("#steel", False)}))
    els.append(box([11, 15, 1], [15, 16, 2], {s: ("#vise", False) for s in ("north", "east", "south", "west")} | {"up": ("#steel", False)}))
    # (the screw stands out past the jaw's front, and the blueprint's edges lie on the top: both show all round)
    els.append(box([12.5, 15, 0], [13.5, 16, 1], {s: ("#steel", False) for s in ("north", "east", "west", "up", "down")}))
    els.append(box([2, 14, 4], [9, 14.25, 11], {s: ("#blueprint", False) for s in ("north", "east", "south", "west", "up")}))
    els.append(box([10, 14, 10], [12, 15, 12], {s: ("#steel", False) for s in ("north", "east", "south", "west", "up")}))
    els.append(box([12, 14, 10.5], [15, 14.5, 11.5], {s: ("#handle", False) for s in ("north", "east", "south", "up")}))
    textures = {
        "particle": f"nookworks:block/{variant}_workbench_side",
        "top": f"nookworks:block/{variant}_workbench_top",
        "front": f"nookworks:block/{variant}_workbench_front",
        "side": f"nookworks:block/{variant}_workbench_side",
        "bottom": f"nookworks:block/{variant}_workbench_bottom",
        "shelf": f"nookworks:block/{variant}_workbench_shelf",
        "vise": "nookworks:block/workbench_vise",
        "blueprint": "nookworks:block/workbench_blueprint",
        "toolbox": "nookworks:block/workbench_toolbox",
        "brass": "nookworks:block/workbench_brass",
        "steel": "nookworks:block/workbench_steel",
        "handle": "minecraft:block/stripped_spruce_log",
    }
    return {"parent": "minecraft:block/block", "textures": textures, "elements": els}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf8"))


# texkit.shade's materials: the walnut wood, the blueprint plain, the rest (steel, brass, painted iron) metal
MATERIAL = {**{c: "wood" for c in (WALNUT_HI, WALNUT, WALNUT_LO, WALNUT_DK)},
            **{c: "plain" for c in (PAPER_HI, PAPER, PAPER_LO, INK)}, None: "metal"}


def save(img, name, seed, preset, keep=()):
    texkit.shade(img, seed, keep=keep, material=MATERIAL, **preset).save(BLOCK / f"{name}.png")


def main():
    for variant, metal, top, seed in (("iron", IRON, top_iron(), 410), ("brass", BRASS_FRAME, top_brass(), 420)):
        keep = (BRASS_HI, BRASS) if variant == "iron" else ()
        save(elevation(metal, True), f"{variant}_workbench_front", seed, texkit.SMOOTH, keep)
        save(elevation(metal, False), f"{variant}_workbench_side", seed + 1, texkit.SMOOTH)
        save(top, f"{variant}_workbench_top", seed + 2, texkit.SMOOTH)
        save(shelf(metal), f"{variant}_workbench_shelf", seed + 3, texkit.SMOOTH)
        save(bottom(metal), f"{variant}_workbench_bottom", seed + 4, texkit.SMOOTH)
        name = f"{variant}_workbench"
        write_json(ASSETS / f"models/block/{name}.json", workbench_model(variant))
        variants = {f"facing={f}": {"model": f"nookworks:block/{name}", **({"y": r} if r else {})}
                    for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        write_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{name}"}})
    save(vise(), "workbench_vise", 430, texkit.SMOOTH, keep=(VISE_HI, VISE_LO, PALE_DK))
    save(blueprint(), "workbench_blueprint", 431, texkit.SMOOTH, keep=(INK,))
    save(toolbox(), "workbench_toolbox", 432, texkit.SMOOTH)
    save(brass_part(), "workbench_brass", 433, texkit.SMOOTH)
    save(steel_part(), "workbench_steel", 434, texkit.SMOOTH)


if __name__ == "__main__":
    main()
