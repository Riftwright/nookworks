"""The Wind Chime: a garden chime of metal tubes hung from a wooden disc, hung as a bottle is - from a Thin Rope's end
or on a line - that chimes now and then in the wind, more in rain and in a storm (the user, 2026-11-10:
「できそうなことはぜんぶやっていいよ」, of the sound session's survey: only the Thin Chain was heard of what hangs). Its own
picture, drawn here (no vanilla pixels): a loop of string, the disc, four tubes of four lengths, the clapper between
them on its string and the sail under it that the wind catches.

- textures/item/wind_chime.png   the item, and what is drawn hanging (HungItemRenderer draws the item's picture)
- models/item/wind_chime.json, items/wind_chime.json
- data: the recipe; the item is in #nookworks:hangs_from_rope

Run from the repo root:  python tools/textures/gen_wind_chime.py
"""
import json
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


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    itemkit.sprite(itemkit.check(icon()), COLOURS, lit=0.9, dark=0.72, ring_from=RING).save(ASSETS / "textures/item/wind_chime.png")
    write_json(ASSETS / "models/item/wind_chime.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/wind_chime"}})
    write_json(ASSETS / "items/wind_chime.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/wind_chime"}})
    write_json(DATA / "recipe/wind_chime.json", recipe())
    print("wrote the wind chime")
