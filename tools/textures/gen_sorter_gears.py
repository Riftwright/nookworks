"""The Sorter Gears: a cog wheel each, in its metal - gold, diamond, netherite, Velarium - eight teeth round a rim, a
dished web lit from the top left, and a bored hub. Original pixel art, shaded in the Storage Upgrades' metals and
ringed as vanilla rings its items, left without grain. Deterministic.

- item/{gold,diamond,netherite,velarium}_sorter_gear.png and their item models

Run from the repo root:  python tools/textures/gen_sorter_gears.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit
from gen_storage_upgrades import METALS as UPGRADE_METALS

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)
METALS = {name: UPGRADE_METALS[name] for name in ("gold", "diamond", "netherite", "velarium")}  # one family
TEETH = 8


def gear(metal):
    """
    The cog seen face on: eight teeth past the rim, set square on the axes and the diagonals so each is drawn alike
    (set off them, the pixel grid cut them all to different shapes); the rim lit towards the top left, a gleam on it
    there; the web between rim and hub dished in, in the rim's shadow at the top left; the hub standing up round the
    bore, which the ring fills dark.
    """
    shade, dark, mid, light, high = metal
    img = Image.new("RGBA", (16, 16), CLEAR)
    c = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy)
            tooth = math.cos(TEETH * math.atan2(dy, dx)) > 0
            if d > 7.0 or (d > 4.8 and not tooth) or d < 1.6:
                continue
            lit = (-dx - dy) / (d + 0.01)  # towards the top left, 1; away, -1
            if d > 4.2:  # the rim and the teeth
                colour = high if lit > 0.6 and d < 5.6 else light if lit > -0.2 else mid
            elif d > 2.6:  # the web
                colour = dark if lit > 0.2 else mid
            else:  # the hub
                colour = high if lit > 0.4 else light
            img.putpixel((x, y), colour + (255,))
    return texkit.outline(img)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    for name, metal in METALS.items():
        item = f"{name}_sorter_gear"
        gear(metal).save(ASSETS / f"textures/item/{item}.png")
        write_json(ASSETS / f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/{item}"}})
        write_json(ASSETS / f"items/{item}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:item/{item}"}})
    print("wrote the sorter gears")


if __name__ == "__main__":
    main()
