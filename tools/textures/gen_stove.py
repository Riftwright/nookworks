"""The Electric Stove (trial, 2026-11-14): its faces, models, blockstate and item definition. Original pixel art.
Deterministic.  python tools/textures/gen_stove.py

A kitchen range in the machines' family (gen_machine_faces.py's frame, dark steel panel and brass): a pot or pan stands
on its top, where a heating ring lies in a shallow well - two turns of a sheathed element about a hub, iron-dark while
it stands and glowing while it heats. Two brass knobs, a maker's plate and a towel rail on its front; louvres and the copper terminal
down its sides, as the Electric Furnace has them.

- block/electric_stove_top.png     the top, the ring cold
- block/electric_stove_coil.png    the ring hot, alone on a clear sheet: laid a hair over the top and lit from within
                                   (light_emission), so it glows in a dark kitchen as the lamp of a machine does
- block/electric_stove_front.png, electric_stove_side.png (the underside is the machines' own, block/machine_bottom)
- models/block/electric_stove.json, electric_stove_lit.json; blockstates/electric_stove.json; items/electric_stove.json
"""
import json
import math
from pathlib import Path

import gen_machine_faces as m
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"

ELEMENT = ((56, 54, 58), (92, 90, 96), (128, 126, 134))   # the sheathed element cold: shade, body, a glint
HOT = ((226, 86, 26), (255, 140, 44), (255, 206, 120))  # and hot
WELL = (20, 21, 24)


def ring(x, y):
    """Which part of the heating ring the pixel is: 'o' the outer turn, 'i' the inner, 'h' the hub, '.' the well, None outside."""
    d = math.hypot(x - 7.5, y - 7.5)
    if d < 1.2:
        return "h"
    if 2.3 <= d < 3.4:
        return "i"
    if 4.4 <= d < 5.6:
        return "o"
    return "." if d < 6.1 else None


def top():
    img = m.blank()
    m.plan_cabinet(img)
    for y in range(16):
        for x in range(16):
            part = ring(x, y)
            if part == ".":
                m.put(img, x, y, WELL)
            elif part == "h":
                m.put(img, x, y, m.DARK_DK)
            elif part:
                # lit from the top left, as the frame is
                m.put(img, x, y, ELEMENT[2] if (x + y) % 5 == 0 and x + y < 15 else ELEMENT[1] if x + y < 17 else ELEMENT[0])
    return img


def coil():
    img = m.blank()
    for y in range(16):
        for x in range(16):
            if ring(x, y) in ("o", "i"):
                m.put(img, x, y, HOT[2] if (x * 3 + y) % 4 == 0 else HOT[1] if (x + y) % 2 else HOT[0])
    return img


def front():
    img = m.blank()
    m.cabinet_elev(img, 0, 15)
    # two knobs to the right and the maker's plate to the left: set evenly either side of a rail they made a face
    for x in (8, 11):
        m.rect(img, x, 3, x + 1, 4, m.BRASS)
        m.put(img, x, 3, m.BRASS_HI)
        m.put(img, x + 1, 4, m.BRASS_DK)
    m.hline(img, 3, 5, 3, m.PALE_LO)
    m.put(img, 3, 3, m.PALE)
    m.hline(img, 3, 12, 8, m.PALE)   # a towel rail across the door
    m.put(img, 3, 8, m.PALE_HI)
    m.put(img, 12, 8, m.PALE_LO)
    m.hline(img, 3, 12, 9, m.BAR_SH)
    for x in range(4, 12, 2):  # a vent at its foot
        m.put(img, x, 13, m.BAR_SH)
        m.put(img, x + 1, 13, m.DARK_HI)
    return img


def side():
    img = m.blank()
    m.cabinet_elev(img, 0, 15)
    for y in (4, 7):  # louvres
        m.hline(img, 4, 11, y, m.BAR_SH)
        m.hline(img, 4, 11, y + 1, m.DARK_HI)
    m.rect(img, 6, 11, 9, 12, m.COPPER_LO)  # the terminal the power comes in by
    m.hline(img, 6, 9, 11, m.COPPER_HI)
    m.put(img, 7, 12, m.COPPER_DK)
    return img


def finish(img, seed, keep=()):
    stuff = m.material()
    stuff.update({c: "plain" for c in ELEMENT + HOT + (WELL,)})
    return texkit.shade(img, seed, keep=m.FRAME_KEEP + tuple(keep), material=stuff, **texkit.SMOOTH)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    finish(top(), 1501, keep=ELEMENT + (WELL,)).save(BLOCK / "electric_stove_top.png")
    coil().save(BLOCK / "electric_stove_coil.png")  # as drawn: it glows, and no shade is laid on a glow
    finish(front(), 1502).save(BLOCK / "electric_stove_front.png")
    finish(side(), 1503).save(BLOCK / "electric_stove_side.png")
    t = "nookworks:block/electric_stove_"
    textures = {"top": t + "top", "bottom": "nookworks:block/machine_bottom", "side": t + "side", "front": t + "front"}
    write(ASSETS / "models/block/electric_stove.json", {"parent": "minecraft:block/orientable_with_bottom", "textures": textures})
    cube = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "down": {"texture": "#bottom", "cullface": "down"}, "up": {"texture": "#top", "cullface": "up"},
        "north": {"texture": "#front", "cullface": "north"}, "south": {"texture": "#side", "cullface": "south"},
        "west": {"texture": "#side", "cullface": "west"}, "east": {"texture": "#side", "cullface": "east"}}}
    # the hot ring a hair over the top (in one plane with it, the two flicker), lit from within; under a pot's foot
    glow = {"from": [0, 16.02, 0], "to": [16, 16.02, 16], "shade": False, "light_emission": 15,
            "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#coil"}, "down": {"uv": [0, 16, 16, 0], "texture": "#coil"}}}
    write(ASSETS / "models/block/electric_stove_lit.json", {
        "parent": "minecraft:block/orientable_with_bottom", "render_type": "minecraft:cutout",
        "textures": dict(textures, coil=t + "coil"), "elements": [cube, glow]})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            variant = {"model": "nookworks:block/electric_stove" + ("_lit" if lit else "")}
            if y:
                variant["y"] = y
            variants[f"facing={facing},lit={str(lit).lower()}"] = variant
    write(ASSETS / "blockstates/electric_stove.json", {"variants": variants})
    write(ASSETS / "items/electric_stove.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/electric_stove"}})
    print("electric stove: 4 textures, 2 models, blockstate, item")


if __name__ == "__main__":
    main()
