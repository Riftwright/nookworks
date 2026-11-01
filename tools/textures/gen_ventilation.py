"""Ventilation: the Ventilation Fan, the Range Hood, Ducts and the Chimney - one texture of our own (galvanized sheet
steel, its zinc crystallised into the spangle it has), the rest vanilla textures composed in JSON: bricks and a
terracotta chimney pot, beaten copper for the hood, black iron for the fan guard. Deterministic.

- block/galvanized_steel.png   spangled sheet steel, with the seam of the sheet folded along one edge
- models/block/ventilation_fan.json (the housing, the guard, the motor and the louvres behind; facing north),
  ventilation_fan_blades.json (turned by client/render/VentFanRenderer), ventilation_fan_item.json (both, for the item)
- models/block/range_hood.json: a copper canopy in tiers, open underneath, a collar on top
- models/block/duct_core.json, duct_arm.json (multipart: the arm turned to each side joined)
- models/block/chimney.json (the stack), chimney_top.json (the cap and pot), chimney_collar.json (a duct's collar)
- their blockstates and items

Run from the repo root:  python tools/textures/gen_ventilation.py
"""
import json
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

# Galvanized steel: the pale blue-grey of fresh zinc, its spangle a patchwork of crystals a shade apart.
ZINC = [(122, 130, 138), (150, 158, 166), (172, 180, 188), (192, 200, 206), (214, 220, 224)]


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def galvanized():
    """
    Sheet steel dipped in zinc: the zinc sets in crystals, a patchwork of flat facets a few pixels across, each
    catching the light a little differently (the spangle), with a fine dark line where two meet. The sheet's folded
    seam runs along the top edge.
    """
    rng = random.Random(77)
    seeds = [(rng.uniform(0, 16), rng.uniform(0, 16), rng.choice((1, 2, 2, 3, 3, 4))) for _ in range(9)]
    img = Image.new("RGBA", (16, 16))
    owner = {}
    for y in range(16):
        for x in range(16):
            best, shade = None, 2
            for i, (sx, sy, s) in enumerate(seeds):  # the nearest crystal's centre, the texture wrapped round
                d = min(((x - sx + ox) ** 2 + (y - sy + oy) ** 2) for ox in (-16, 0, 16) for oy in (-16, 0, 16))
                if best is None or d < best[0]:
                    best, shade = (d, i), s
            owner[(x, y)] = best[1]
            px(img, x, y, ZINC[shade])
    for y in range(16):  # the boundaries between crystals, a fine line a shade down
        for x in range(16):
            if owner[(x, y)] != owner[((x + 1) % 16, y)] or owner[(x, y)] != owner[(x, (y + 1) % 16)]:
                r, g, b, _ = img.getpixel((x, y))
                px(img, x, y, (max(0, r - 14), max(0, g - 14), max(0, b - 12)))
    for x in range(16):  # the seam: the sheet folded over on itself along one edge
        px(img, x, 0, ZINC[4])
        px(img, x, 1, ZINC[1])
    return img


def box(frm, to, tex, faces="nsewud", uv=None):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    out = {}
    for f in faces:
        face = {"texture": tex[f] if isinstance(tex, dict) else tex}
        if uv:
            face["uv"] = uv
        out[names[f]] = face
    return {"from": frm, "to": to, "faces": out}


# ---------------------------------------------------------------------------------------------------------- the fan
FAN_TEX = {"particle": "nookworks:block/galvanized_steel", "steel": "nookworks:block/galvanized_steel",
           "guard": "nookworks:block/black_iron", "blade": "minecraft:block/iron_block", "motor": "nookworks:block/black_iron",
           "louvre": "nookworks:block/galvanized_steel"}


def fan_body():
    """Facing north (the room): a square housing of galvanized steel, two pixels thick, through the wall; a black iron
    guard across its front - a ring and a cross of bars; the motor on its stays behind the blades; and at the back,
    the louvres that let the air out and keep the rain from coming in."""
    els = [
        box([0, 14, 0], [16, 16, 16], "#steel"), box([0, 0, 0], [16, 2, 16], "#steel"),
        box([0, 2, 0], [2, 14, 16], "#steel"), box([14, 2, 0], [16, 14, 16], "#steel"),
        # the guard: a square ring set in from the housing's lip, and bars across it, a hair in front of the blades
        box([3, 3, 1], [13, 4, 1.5], "#guard"), box([3, 12, 1], [13, 13, 1.5], "#guard"),
        box([3, 4, 1], [4, 12, 1.5], "#guard"), box([12, 4, 1], [13, 12, 1.5], "#guard"),
        box([7.5, 4, 1.1], [8.5, 12, 1.4], "#guard"), box([4, 7.5, 1.1], [12, 8.5, 1.4], "#guard"),
        box([5.5, 4, 1.15], [6, 12, 1.35], "#guard"), box([10, 4, 1.15], [10.5, 12, 1.35], "#guard"),
        box([4, 5.5, 1.15], [12, 6, 1.35], "#guard"), box([4, 10, 1.15], [12, 10.5, 1.35], "#guard"),
        # the motor, on two stays from the housing, behind the blades
        box([6, 6, 7], [10, 10, 11], "#motor"), box([2, 7.5, 9], [6, 8.5, 10], "#motor"), box([10, 7.5, 9], [14, 8.5, 10], "#motor"),
    ]
    for y in (3, 6, 9, 12):  # the louvres at the back
        els.append(box([2, y, 13.5], [14, y + 2, 14.5], "#louvre"))
    return els


def fan_blades():
    """Four broad blades on a hub, turned a little each way as a fan's are, round the centre of the housing."""
    els = [box([7, 7, 4.5], [9, 9, 6.5], "#motor")]
    els += [box([6.5, 9, 5], [9.5, 13, 5.5], "#blade"), box([6.5, 3, 5], [9.5, 7, 5.5], "#blade"),
            box([9, 6.5, 5.2], [13, 9.5, 5.7], "#blade"), box([3, 6.5, 5.2], [7, 9.5, 5.7], "#blade")]
    return els


# ---------------------------------------------------------------------------------------------------------- the hood
def hood():
    """A canopy of beaten copper in three tiers, stepping in from the stove's width to its collar, each tier a wall of
    copper a pixel thick with the inside open, so it is hollow seen from under it; a band of brighter copper round
    its hem."""
    t = {"particle": "minecraft:block/copper_block", "copper": "minecraft:block/cut_copper", "hem": "minecraft:block/copper_block",
         "inner": "minecraft:block/exposed_copper"}
    els = []

    def ring(x0, x1, y0, y1, tex, inner="#inner"):
        faces_out = {"n": tex, "s": tex, "e": tex, "w": tex, "u": tex, "d": inner}
        els.append(box([x0, y0, x0], [x1, y1, x0 + 1], faces_out))
        els.append(box([x0, y0, x1 - 1], [x1, y1, x1], faces_out))
        els.append(box([x0, y0, x0 + 1], [x0 + 1, y1, x1 - 1], faces_out))
        els.append(box([x1 - 1, y0, x0 + 1], [x1, y1, x1 - 1], faces_out))

    ring(0, 16, 0, 2, "#hem")
    ring(1, 15, 2, 5, "#copper")
    ring(3, 13, 5, 8, "#copper")
    ring(4.5, 11.5, 8, 11, "#copper")
    els.append(box([1, 4.99, 1], [15, 5, 15], "#inner", faces="d"))   # the underside of the shoulders, seen from below
    # the shoulders' tops, the steps between the tiers seen from above: open, the ground showed through (the user,
    # 2026-10-26) - each inside the ring below's inner edge, so no face lies on another
    els.append(box([2, 4.99, 2], [14, 5, 14], "#copper", faces="u"))
    els.append(box([4, 7.99, 4], [12, 8, 12], "#copper", faces="u"))
    els.append(box([5, 11, 5], [11, 16, 11], {"n": "#copper", "s": "#copper", "e": "#copper", "w": "#copper",
                                              "u": "#inner", "d": "#inner"}))  # the collar a duct fits onto
    return {"parent": "minecraft:block/block", "textures": t, "elements": els}


# ---------------------------------------------------------------------------------------------------------- ducts
DUCT_TEX = {"particle": "nookworks:block/galvanized_steel", "steel": "nookworks:block/galvanized_steel"}


def duct_core():
    return {"parent": "minecraft:block/block", "textures": DUCT_TEX, "elements": [box([5, 5, 5], [11, 11, 11], "#steel")]}


def duct_arm():
    """The arm to the north, turned to each side joined; a flange a half pixel proud where it meets the next."""
    return {"parent": "minecraft:block/block", "textures": DUCT_TEX,
            "elements": [box([5, 5, 0], [11, 11, 5], "#steel", faces="nsewu" + "d"),
                         box([4.5, 4.5, 0], [11.5, 11.5, 1], "#steel")]}


# ---------------------------------------------------------------------------------------------------------- chimney
CHIMNEY_TEX = {"particle": "minecraft:block/bricks", "brick": "minecraft:block/bricks", "cap": "minecraft:block/smooth_stone",
               "cap_side": "minecraft:block/smooth_stone_slab_side", "pot": "minecraft:block/terracotta",
               "soot": "minecraft:block/black_concrete"}


# each chimney's masonry (the user, 2026-10-24 and 10-31): (id prefix of the chimney, of the flue, vanilla texture);
# the brick chimney keeps its id, "chimney"
MASONRY = [("", "brick_", "minecraft:block/bricks"), ("stone_brick_", "stone_brick_", "minecraft:block/stone_bricks"),
           ("nether_brick_", "nether_brick_", "minecraft:block/nether_bricks"),
           ("cobblestone_", "cobblestone_", "minecraft:block/cobblestone")]


def masonry_tex(texture):
    return dict(CHIMNEY_TEX, particle=texture, brick=texture)


def chimney(top, tex=CHIMNEY_TEX):
    """The stack of brick; on the top one a stone cap over the brick and a round chimney pot (as round as sixteen
    pixels let it be) of terracotta, sooty inside - the clay pot on every masonry's (the user, 2026-10-31)."""
    if not top:
        return {"parent": "minecraft:block/block", "textures": tex, "elements": [box([3, 0, 3], [13, 16, 13], "#brick")]}
    cap = {"n": "#cap_side", "s": "#cap_side", "e": "#cap_side", "w": "#cap_side", "u": "#cap", "d": "#cap"}
    els = [box([3, 0, 3], [13, 12, 13], "#brick"), box([2, 12, 2], [14, 13, 14], cap),
           # the pot: four walls round its mouth (the corners cut, so it reads round), the soot down inside
           box([6, 13, 5], [10, 16, 6], "#pot"), box([6, 13, 10], [10, 16, 11], "#pot"),
           box([5, 13, 6], [6, 16, 10], "#pot"), box([10, 13, 6], [11, 16, 10], "#pot"),
           box([6, 13, 6], [10, 14.5, 10], {"n": "#soot", "s": "#soot", "e": "#soot", "w": "#soot", "u": "#soot", "d": "#pot"})]
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


def chimney_collar(tex=CHIMNEY_TEX):
    """Where a duct runs into the chimney's side: a collar of brick from the stack out to the edge, to the north."""
    return {"parent": "minecraft:block/block", "textures": tex, "elements": [box([4, 4, 0], [12, 12, 3], "#brick")]}


def flue(tex):
    """A whole block of the masonry with the flue down its middle, 6 px square, sooty inside: four walls, their outer
    faces culled against neighbours as a full block's are, the opening only at top and bottom."""
    walls = [([0, 0, 0], [16, 16, 5]), ([0, 0, 11], [16, 16, 16]), ([0, 0, 5], [5, 16, 11]), ([11, 0, 5], [16, 16, 11])]
    els = []
    for frm, to in walls:
        faces = {}
        for f, name in (("n", "north"), ("s", "south"), ("e", "east"), ("w", "west"), ("u", "up"), ("d", "down")):
            outer = (f == "n" and frm[2] == 0) or (f == "s" and to[2] == 16) or (f == "w" and frm[0] == 0) or (f == "e" and to[0] == 16)
            inner = not outer and f in "nsew"
            face = {"texture": "#soot" if inner else "#brick"}
            if outer or f in "ud":
                face["cullface"] = name
            faces[name] = face
        els.append({"from": frm, "to": to, "faces": faces})
    return {"parent": "minecraft:block/block", "textures": tex, "elements": els}


FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
SIDE_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    texkit.finish(galvanized(), 90, tile=True, keep=[ZINC[4], ZINC[1]], **texkit.SMOOTH).save(ASSETS / "textures/block/galvanized_steel.png")
    m = ASSETS / "models/block"
    write_json(m / "ventilation_fan.json", {"parent": "minecraft:block/block", "textures": FAN_TEX, "elements": fan_body()})
    write_json(m / "ventilation_fan_blades.json", {"textures": FAN_TEX, "elements": fan_blades()})
    write_json(m / "ventilation_fan_item.json", {"parent": "minecraft:block/block", "textures": FAN_TEX,
                                                 "elements": fan_body() + fan_blades()})
    write_json(ASSETS / "blockstates/ventilation_fan.json", {"variants": {
        f"facing={f}": dict({"model": "nookworks:block/ventilation_fan"}, **({"y": y} if y else {})) for f, y in FACINGS}})
    write_json(ASSETS / "items/ventilation_fan.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/ventilation_fan_item"}})
    write_json(m / "range_hood.json", hood())
    write_json(ASSETS / "blockstates/range_hood.json", {"variants": {
        f"facing={f}": dict({"model": "nookworks:block/range_hood"}, **({"y": y} if y else {})) for f, y in FACINGS}})
    write_json(ASSETS / "items/range_hood.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/range_hood"}})
    write_json(m / "duct_core.json", duct_core())
    write_json(m / "duct_arm.json", duct_arm())
    parts = [{"apply": {"model": "nookworks:block/duct_core"}}]
    for side, rot in SIDE_ROT.items():
        parts.append({"when": {side: "true"}, "apply": dict({"model": "nookworks:block/duct_arm", "uvlock": False}, **rot)})
    write_json(ASSETS / "blockstates/duct.json", {"multipart": parts})
    write_json(m / "duct_item.json", {"parent": "minecraft:block/block", "textures": DUCT_TEX,
                                      "elements": [box([5, 5, 0], [11, 11, 16], "#steel"), box([4.5, 4.5, 0], [11.5, 11.5, 1], "#steel"),
                                                   box([4.5, 4.5, 15], [11.5, 11.5, 16], "#steel")],
                                      # turned side on in an item frame, not end on (a small square)
                                      "display": {"fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}}})
    write_json(ASSETS / "items/duct.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/duct_item"}})
    for prefix, flue_prefix, texture in MASONRY:
        tex = masonry_tex(texture)
        name = prefix + "chimney"
        write_json(m / f"{name}.json", chimney(False, tex))
        write_json(m / f"{name}_top.json", chimney(True, tex))
        write_json(m / f"{name}_collar.json", chimney_collar(tex))
        parts = [{"when": {"top": "false"}, "apply": {"model": f"nookworks:block/{name}"}},
                 {"when": {"top": "true"}, "apply": {"model": f"nookworks:block/{name}_top"}}]
        for side, rot in SIDE_ROT.items():
            if side in ("up", "down"):
                continue
            parts.append({"when": {side: "true"}, "apply": dict({"model": f"nookworks:block/{name}_collar"}, **rot)})
        write_json(ASSETS / f"blockstates/{name}.json", {"multipart": parts})
        write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{name}_top"}})
        flue_name = flue_prefix + "flue"
        write_json(m / f"{flue_name}.json", flue(tex))
        write_json(ASSETS / f"blockstates/{flue_name}.json", {"multipart": [{"apply": {"model": f"nookworks:block/{flue_name}"}}]})
        write_json(ASSETS / f"items/{flue_name}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{flue_name}"}})
        loot = {"type": "minecraft:block", "pools": [{"bonus_rolls": 0.0, "rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                                                      "entries": [{"type": "minecraft:item", "name": "nookworks:{}"}]}]}
        data = ROOT / "src/main/resources/data/nookworks"
        for block in ([name] if prefix else []) + [flue_name]:
            l = json.loads(json.dumps(loot).replace("{}", block))
            l["random_sequence"] = f"nookworks:blocks/{block}"
            write_json(data / f"loot_table/blocks/{block}.json", l)
        material = texture.split("/")[-1]
        if prefix:
            write_json(data / f"recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "building",
                                                      "key": {"B": f"minecraft:{material}"}, "pattern": ["B B", "B B"],
                                                      "result": {"id": f"nookworks:{name}", "count": 4}})
        write_json(data / f"recipe/{flue_name}.json", {"type": "minecraft:crafting_shaped", "category": "building",
                                                       "key": {"B": f"minecraft:{material}"}, "pattern": ["B B", "B B", "B B"],
                                                       "result": {"id": f"nookworks:{flue_name}", "count": 6}})
    print("wrote the ventilation")


if __name__ == "__main__":
    main()
