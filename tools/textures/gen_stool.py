"""The stool's shape, shared by every wood's stool (models/block/template_stool.json; the woods' own models name its
planks and stripped log, tools/gen_wood_data.py).

A low stool with no back (the user, 2026-10-11: "背の低い、背もたれのない椅子"): a seat 2 px thick with its top at 7 px -
two lower than the chair's, where the rider sits (StoolBlock.SEAT_TOP) - and 10 px across, smaller than the chair's 12
(as wide, it read as a low table), its corners taken off by a pixel so it reads as a stool's top and not a crate's lid;
four legs 2 px square under it, a pixel in from its edge; and a rung between each pair, the front and back ones a pixel
lower than the sides', as a joiner staggers them.

The stool faces south unturned (the blockstate turns it, for the grain of its seat). Every face names its uv, as the
chair's do (gen_chair.uv). Run from the repo root:  python tools/textures/gen_stool.py
"""
import json
from pathlib import Path

from gen_chair import box

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/models/block/template_stool.json"
SEAT_TOP = 7


def elements():
    e = []
    for name, x, z in (("leg_nw", 4, 4), ("leg_ne", 10, 4), ("leg_sw", 4, 10), ("leg_se", 10, 10)):
        e.append(box(name, [x, 0, z], [x + 2, SEAT_TOP - 2, z + 2], "#post", cull_down=True))
    e.append(box("rung_north", [6, 1.5, 4.5], [10, 2.5, 5.5], "#post"))
    e.append(box("rung_south", [6, 1.5, 10.5], [10, 2.5, 11.5], "#post"))
    e.append(box("rung_west", [4.5, 2.5, 6], [5.5, 3.5, 10], "#post"))
    e.append(box("rung_east", [10.5, 2.5, 6], [11.5, 3.5, 10], "#post"))
    # the seat: a middle and four edges round it, its corners left out (no two faces in one plane over each other)
    bottom = SEAT_TOP - 2
    e.append(box("seat", [4, bottom, 4], [12, SEAT_TOP, 12], "#planks"))
    e.append(box("seat_north", [4, bottom, 3], [12, SEAT_TOP, 4], "#planks"))
    e.append(box("seat_south", [4, bottom, 12], [12, SEAT_TOP, 13], "#planks"))
    e.append(box("seat_west", [3, bottom, 4], [4, SEAT_TOP, 12], "#planks"))
    e.append(box("seat_east", [12, bottom, 4], [13, SEAT_TOP, 12], "#planks"))
    return e


def main():
    model = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/oak_planks", "planks": "minecraft:block/oak_planks",
                     "post": "minecraft:block/stripped_oak_log"},
        "elements": elements(),
        # low as it is, it is lifted to the middle of its slot and shown a little larger than a block, so it is not a
        # sliver at the bottom of the slot beside the chair
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 2.5, 0], "scale": [0.75, 0.75, 0.75]}},
    }
    OUT.write_bytes((json.dumps(model, indent=2) + "\n").encode("utf-8"))
    print("wrote", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
