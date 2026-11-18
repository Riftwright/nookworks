"""Nested Timer: a smooth stone plate like a repeater's, with a small dark iron meter on it - six red bars rising
left to right, lit up to the chosen interval - and a redstone lamp at the front that lights with each pulse. The
plate and the lamp use vanilla textures by name; the meter and its case are ours, drawn flat and shaded with
texkit.shade as metal. Deterministic.

- block/nested_timer_meter_0..5.png  the meter's face, 1-6 bars lit (from 0.5, 1, 2, 5, 30 s and 5 min up)
- block/nested_timer_side.png        the iron case
- models/block/nested_timer_<i>[_on].json, blockstates/nested_timer.json, items/nested_timer.json

The models are drawn for FACING=south (the pulse leaves through the north side); the blockstate turns them.

Run from the repo root:  python tools/textures/gen_timer.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

CASE = [(34, 34, 38), (52, 52, 58), (74, 74, 82), (98, 98, 108)]  # dark iron: shadow, body, lit, highlight
GLASS = (22, 26, 24)
RED = (222, 44, 32)
RED_LIT = (255, 120, 90)
RED_OFF = (86, 22, 20)


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,))


def meter(interval):
    """The case (x 1-14, y 3-12) round a dark glass window, and six bars in it, each a step taller than the last."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(3, 13):
        for x in range(1, 15):
            c = CASE[2] if (y == 3 or x == 1) else CASE[0] if (y == 12 or x == 14) else CASE[1]
            px(img, x, y, c)
    px(img, 1, 3, CASE[3])
    for y in range(4, 12):
        for x in range(2, 14):
            px(img, x, y, GLASS)
    for k in range(6):
        x = 2 + 2 * k
        top = 10 - k
        lit = k <= interval
        for y in range(top, 12):
            px(img, x, y, (RED_LIT if y == top else RED) if lit else RED_OFF)
    return img


def side():
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            px(img, x, y, CASE[2] if y == 0 else CASE[1] if y < 15 else CASE[0])
    return img


def model(interval, on):
    t = {"particle": "minecraft:block/smooth_stone", "plate": "minecraft:block/smooth_stone",
         "meter": f"nookworks:block/nested_timer_meter_{interval}", "case": "nookworks:block/nested_timer_side",
         "lamp": "minecraft:block/redstone_torch" if on else "minecraft:block/redstone_torch_off"}
    sides = ("north", "east", "south", "west")
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": t, "elements": [
        {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            **{s: {"texture": "#plate", "uv": [0, 14, 16, 16], "cullface": s} for s in sides},
            "up": {"texture": "#plate", "uv": [0, 0, 16, 16]}, "down": {"texture": "#plate", "uv": [0, 0, 16, 16], "cullface": "down"}}},
        {"from": [1, 2, 3], "to": [15, 3, 13], "faces": {  # the meter, one pixel to one pixel
            "north": {"texture": "#case", "uv": [1, 0, 15, 1]}, "south": {"texture": "#case", "uv": [1, 0, 15, 1]},
            "east": {"texture": "#case", "uv": [3, 0, 13, 1]}, "west": {"texture": "#case", "uv": [3, 0, 13, 1]},
            "up": {"texture": "#meter", "uv": [1, 3, 15, 13]}}},
        # the lamp at the front: the head of a redstone torch, lit while the pulse is on
        {"from": [7, 2, 0.5], "to": [9, 4, 2.5], "faces": {
            **{s: {"texture": "#lamp", "uv": [7, 6, 9, 8]} for s in sides},
            "up": {"texture": "#lamp", "uv": [7, 6, 9, 8]}}},
    ]}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures/block"
    keep = [GLASS, RED, RED_LIT, RED_OFF]
    for i in range(6):
        texkit.shade(meter(i), 30 + i, keep=keep, material="metal", **texkit.SMOOTH).save(tex / f"nested_timer_meter_{i}.png")
        write_json(ASSETS / f"models/block/nested_timer_{i}.json", model(i, False))
        write_json(ASSETS / f"models/block/nested_timer_{i}_on.json", model(i, True))
    texkit.shade(side(), 40, material="metal", **texkit.SMOOTH).save(tex / "nested_timer_side.png")
    variants = {}
    for facing, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for i in range(6):
            for powered in ("false", "true"):
                v = {"model": f"nookworks:block/nested_timer_{i}" + ("_on" if powered == "true" else "")}
                if y:
                    v["y"] = y
                variants[f"facing={facing},interval={i},powered={powered}"] = v
    write_json(ASSETS / "blockstates/nested_timer.json", {"variants": variants})
    write_json(ASSETS / "items/nested_timer.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/nested_timer_1"}})
    print("wrote the redstone timer")


if __name__ == "__main__":
    main()
