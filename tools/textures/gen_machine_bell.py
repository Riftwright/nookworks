"""The Machine Bell: a small electric alarm bell fitted beside a machine, that rings when the machine stops and gives a
redstone signal while it stays stopped (the user, 2026-11-10: 「できそうなことはぜんぶやっていいよ」, of the sound session's
survey). Its own picture, drawn here (no vanilla pixels): a red enamelled gong on a black iron plate, the coil's housing
under it and a brass hammer at its rim - the black iron and brass of our pipes and fittings.

- textures/block/machine_bell_enamel.png   the gong's red enamel, finished by texkit
- textures/item/machine_bell.png            the item: the bell as it hangs on a wall
- models/block/machine_bell_{wall,ceiling}.json, models/item/machine_bell.json, items/machine_bell.json,
  blockstates/machine_bell.json
- data: the loot table, the recipe

Run from the repo root:  python tools/textures/gen_machine_bell.py
"""
import json
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
DATA = ROOT / "src/main/resources/data/nookworks"

RED_LIT, RED, RED_DK = (206, 62, 54), (172, 36, 34), (118, 22, 24)
TURN = {"north": 0, "east": 90, "south": 180, "west": 270}


def enamel():
    """The gong's enamel: a red with a sheen across its upper left, deeper toward its rim below and right."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t = (x + y) / 30.0
            c = RED_LIT if t < 0.3 else RED if t < 0.75 else RED_DK
            img.putpixel((x, y), c + (255,))
    return texkit.finish(img, 4711, **texkit.SMOOTH)


def box(frm, to, tex):
    """A box of one material, its faces taking the texture where they lie (as a block's do)."""
    return {"from": frm, "to": to, "faces": {side: {"texture": tex} for side in ("north", "south", "east", "west", "up", "down")}}


# On a wall, pointing north, its plate on the wall to the south: the plate, the coil's housing low on it, the gong in
# three steps out from the plate with a boss at its middle, and the hammer's arm up from the housing to the gong's rim.
WALL = [
    ([5, 3, 15], [11, 13, 16], "#iron"),        # the plate
    ([6, 3.5, 13.5], [10, 5.5, 15], "#iron"),   # the coil's housing
    ([5, 6.5, 14], [11, 12.5, 15], "#enamel"),  # the gong's rim
    ([5.5, 7, 13], [10.5, 12, 14], "#enamel"),
    ([6.5, 8, 12], [9.5, 11, 13], "#enamel"),
    ([7.5, 9, 11.5], [8.5, 10, 12], "#brass"),  # the boss
    ([7.5, 5.5, 12.5], [8.5, 7, 13.5], "#brass"),  # the hammer's arm
    ([7, 6.5, 12], [9, 7.5, 13], "#brass"),     # its head, at the rim
]


def ceiling_of(frm, to):
    """The wall's box hung under a ceiling: its plate up, the gong facing down, what was up on the wall to the north."""
    ys = (frm[2], to[2])
    zs = (16 - frm[1], 16 - to[1])
    return [frm[0], min(ys), min(zs)], [to[0], max(ys), max(zs)]


def model(boxes):
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"iron": "nookworks:block/black_iron", "enamel": "nookworks:block/machine_bell_enamel",
                         "brass": "nookworks:block/brass_fitting", "particle": "nookworks:block/machine_bell_enamel"},
            "elements": [box(f, t, tex) for f, t, tex in boxes]}


def states():
    parts = []
    for face in ("wall", "ceiling", "floor"):
        for facing, turn in TURN.items():
            # on a floor, the ceiling's bell turned over
            rot = {"x": 180, "y": (turn + 180) % 360} if face == "floor" else {"y": turn} if turn else {}
            parts.append({"when": {"face": face, "facing": facing},
                          "apply": {"model": f"nookworks:block/machine_bell_{'ceiling' if face != 'wall' else 'wall'}", **rot}})
    return {"multipart": parts}


ICON_COLOURS = {"K": (40, 40, 46), "k": (70, 70, 80), "R": RED, "r": RED_LIT, "h": (236, 150, 128), "S": RED_DK,
                "B": (204, 164, 78), "b": (150, 112, 44)}
ICON_RING = {ch: ICON_COLOURS["K"] for ch in ICON_COLOURS}
ICON = [
    "................",
    "....kkkkkkkk....",
    "...kRRRRRRRRk...",
    "..kRhrrRRRRRSk..",
    "..kRrRRRRRRRSk..",
    "..kRRRRBBRRRSk..",
    "..kRRRRBBRRRSk..",
    "..kRRRRRRRRRSk..",
    "..kSRRRRRRRSSk..",
    "...kSSSSSSSSk...",
    "...KKkkBbkkKK...",
    "...KkkkBbkkkK...",
    "...KkkBBbbkkK...",
    "...KKKKKKKKKK...",
    "................",
    "................",
]


def loot():
    return {"type": "minecraft:block", "random_sequence": "nookworks:blocks/machine_bell", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "nookworks:machine_bell"}],
         "condition": {"type": "minecraft:survives_explosion"}}]}


def recipe():
    return {"type": "minecraft:crafting_shaped", "category": "redstone",
            "pattern": [" C ", "CRC", " I "],
            "key": {"C": "minecraft:copper_ingot", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"},
            "result": {"id": "nookworks:machine_bell", "count": 1}}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    enamel().save(ASSETS / "textures/block/machine_bell_enamel.png")
    itemkit.sprite(itemkit.check(ICON), ICON_COLOURS, lit=0.9, dark=0.72, ring_from=ICON_RING).save(
        ASSETS / "textures/item/machine_bell.png")
    write_json(ASSETS / "models/block/machine_bell_wall.json", model(WALL))
    write_json(ASSETS / "models/block/machine_bell_ceiling.json", model([(*ceiling_of(f, t), tex) for f, t, tex in WALL]))
    write_json(ASSETS / "models/item/machine_bell.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/machine_bell"}})
    write_json(ASSETS / "items/machine_bell.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/machine_bell"}})
    write_json(ASSETS / "blockstates/machine_bell.json", states())
    write_json(DATA / "loot_table/blocks/machine_bell.json", loot())
    write_json(DATA / "recipe/machine_bell.json", recipe())
    print("wrote the machine bell")
