"""The Wind Chime: a garden chime of metal tubes hung from a wooden disc, hung as a bottle is - from a Thin Rope's end
or on a line - that chimes now and then in the wind, more in rain and in a storm (the user, 2026-11-10:
「できそうなことはぜんぶやっていいよ」, of the sound session's survey: only the Thin Chain was heard of what hangs). Its own
picture, drawn here (no vanilla pixels): a loop of string, the disc, four tubes of four lengths, the clapper between
them on its string and the sail under it that the wind catches.

A block since the play-test after 1.5 (the user, 2026-11-12: 「ちゃんとブロックとして作ろう。アイコンぶら下げるだけはダサすぎる。」):
its model is the chime in the round - a cord, a wooden disc, five metal tubes of five lengths round the clapper on its
string, and the sail under them - set hanging under a block as a lantern is, and drawn the same on a Thin Rope (it hangs
as a lantern does there: #nookworks:hangs_as_lantern). Vanilla's textures by name: spruce for the disc, iron for the
tubes, dark oak for the clapper, oak for the sail; our thin rope for the cords.

- textures/item/wind_chime.png   the item in the inventory
- models/block/wind_chime.json, blockstates/wind_chime.json, models/item/wind_chime.json, items/wind_chime.json
- data: the recipe and the loot table; the item is in #nookworks:hangs_from_rope and #nookworks:hangs_as_lantern

Run from the repo root:  python tools/textures/gen_wind_chime.py
"""
import json
import math
from pathlib import Path

import itemkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
DATA = ROOT / "src/main/resources/data/nookworks"

COLOURS = {"s": (206, 192, 152), "W": (150, 106, 62), "D": (102, 70, 40), "L": (226, 230, 236), "T": (176, 182, 192),
           "S": (120, 126, 138), "C": (112, 80, 48), "P": (170, 124, 74), "p": (122, 86, 50)}
RING = {ch: (COLOURS["S"] if ch in "LTS" else COLOURS["D"] if ch in "WDCPp" else (150, 136, 100)) for ch in COLOURS}


def icon():
    grid = [["."] * 16 for _ in range(16)]

    def put(x, y, ch):
        grid[y][x] = ch

    put(7, 1, "s"), put(8, 1, "s"), put(6, 2, "s"), put(9, 2, "s")  # the loop it hangs by
    for x in range(2, 14):
        put(x, 3, "W")
        put(x, 4, "D")
    # four tubes, two pixels each, lit on their left; four lengths, the longest inside
    for left, bottom in ((2, 11), (5, 13), (9, 12), (12, 10)):
        for y in range(5, bottom + 1):
            put(left, y, "L" if y < bottom else "S")
            put(left + 1, y, "T" if y < bottom else "S")
    for y in range(5, 14):
        put(7, y, "s")  # the clapper's string, down between the tubes to the sail
    for x in (7, 8):
        for y in (8, 9):
            put(x, y, "C")  # the clapper
    for x in range(6, 10):
        put(x, 14, "P")
        put(x, 15, "p")  # the sail
    return ["".join(row) for row in grid]


def recipe():
    return {"type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": [" S ", "WWW", "NNN"],
            "key": {"S": "minecraft:string", "W": "#minecraft:wooden_slabs", "N": "minecraft:iron_nugget"},
            "result": {"id": "nookworks:wind_chime", "count": 1}}


def box(frm, to, tex):
    return {"from": [round(c, 3) for c in frm], "to": [round(c, 3) for c in to],
            "faces": {side: {"texture": tex} for side in ("north", "south", "east", "west", "up", "down")}}


# The tubes round the clapper, (angle in degrees, length in px), hung from just under the disc on short strings.
TUBES = ((18, 7.0), (90, 5.0), (162, 6.0), (234, 4.5), (306, 5.5))
TUBE_R = 2.2      # how far from the middle
TUBE = 0.8        # how thick
DISC_Y = 12.0     # the disc's underside


def block_model():
    """The chime hanging from the top of its block: a cord up to the top, the disc, the tubes, the clapper's string with
    the clapper among the tubes and the sail at its foot."""
    els = [box([7.75, 13, 7.75], [8.25, 16, 8.25], "#cord")]
    els.append(box([5, DISC_Y, 6], [11, DISC_Y + 1, 10], "#disc"))
    els.append(box([6, DISC_Y, 5], [10, DISC_Y + 1, 11], "#disc"))
    for angle, length in TUBES:
        cx = 8 + TUBE_R * math.cos(math.radians(angle))
        cz = 8 + TUBE_R * math.sin(math.radians(angle))
        top = DISC_Y - 0.6
        els.append(box([cx - 0.1, top, cz - 0.1], [cx + 0.1, DISC_Y, cz + 0.1], "#cord"))  # its string
        els.append(box([cx - TUBE / 2, top - length, cz - TUBE / 2], [cx + TUBE / 2, top, cz + TUBE / 2], "#tube"))
    els.append(box([7.85, 3.5, 7.85], [8.15, DISC_Y, 8.15], "#cord"))  # the clapper's string, on to the sail
    els.append(box([6.9, 7.6, 6.9], [9.1, 8.3, 9.1], "#clapper"))
    els.append(box([6.5, 0.5, 7.9], [9.5, 3.5, 8.1], "#sail"))
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"cord": "nookworks:block/thin_rope", "disc": "minecraft:block/spruce_planks",
                         "tube": "minecraft:block/iron_block", "clapper": "minecraft:block/dark_oak_planks",
                         "sail": "minecraft:block/oak_planks", "particle": "minecraft:block/spruce_planks"},
            "elements": els}


def blockstate():
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    return {"variants": {f"facing={f}": ({"model": "nookworks:block/wind_chime", "y": y} if y else {"model": "nookworks:block/wind_chime"})
                         for f, y in turn.items()}}


def loot():
    return {"type": "minecraft:block", "random_sequence": "nookworks:blocks/wind_chime", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "nookworks:wind_chime"}],
         "condition": {"type": "minecraft:survives_explosion"}}]}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    itemkit.sprite(itemkit.check(icon()), COLOURS, lit=0.9, dark=0.72, ring_from=RING).save(ASSETS / "textures/item/wind_chime.png")
    write_json(ASSETS / "models/item/wind_chime.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/wind_chime"}})
    write_json(ASSETS / "items/wind_chime.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/wind_chime"}})
    write_json(ASSETS / "models/block/wind_chime.json", block_model())
    write_json(ASSETS / "blockstates/wind_chime.json", blockstate())
    write_json(DATA / "loot_table/blocks/wind_chime.json", loot())
    recipe_file = DATA / "recipe/wind_chime.json"
    kept = json.loads(recipe_file.read_text(encoding="utf-8")).get("neoforge:conditions") if recipe_file.exists() else None
    write_json(recipe_file, {"neoforge:conditions": kept, **recipe()} if kept else recipe())
    print("wrote the wind chime")
