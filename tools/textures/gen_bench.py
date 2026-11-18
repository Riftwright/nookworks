"""The bench's shape, shared by every wood's bench (models/block/template_bench.json and its parts; the woods' own
models name its planks and stripped log, tools/gen_wood_data.py).

A long seat with no back that joins its neighbours, as desks do (offered beside the stool on 2026-10-11, made at the
user's word on 2026-10-22: 「作って」): a seat 2 px thick with its top at 7 px, the stool's height (BenchBlock.SEAT_TOP),
10 px deep and the block's whole width, so benches side by side are one seat; under it, at each end, a pair of legs 2 px
square with a rung between them across the seat; and a rail along the middle, under the seat, from end to end. Where
a bench is joined to another on a side, that end's legs go and the rail runs on into the next one, so a row of benches
stands on legs at its two ends only, as a row of desks does.

The bench faces south unturned (a rider looks south), its length along x; the blockstate turns it. Its parts:
  seat          the seat, always
  rail          the middle rail between the two ends' legs, always
  end_left      the legs and rung at the west end (on the left, to someone in front of it), where it is not joined
  end_right     the same at the east end
  rail_left     the rail run on from the west legs to the block's edge, where it is joined on that side
  rail_right    the same at the east
template_bench.json is the whole, unjoined bench (the item). Every face names its uv, as the chair's do (gen_chair.uv).
Run from the repo root:  python tools/textures/gen_bench.py
"""
import json
from pathlib import Path

from gen_chair import box

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "src/main/resources/assets/nookworks/models/block"
SEAT_TOP = 7
TEXTURES = {"particle": "minecraft:block/oak_planks", "planks": "minecraft:block/oak_planks",
            "post": "minecraft:block/stripped_oak_log"}


def end(name, x):
    """A pair of legs at x (their west side) and the rung between them."""
    return [
        box(f"{name}_leg_north", [x, 0, 4], [x + 2, SEAT_TOP - 2, 6], "#post", cull_down=True),
        box(f"{name}_leg_south", [x, 0, 10], [x + 2, SEAT_TOP - 2, 12], "#post", cull_down=True),
        box(f"{name}_rung", [x + 0.5, 1.5, 6], [x + 1.5, 2.5, 10], "#post"),
    ]


def rail(name, x0, x1):
    piece = box(name, [x0, 3, 7.5], [x1, 4, 8.5], "#post")
    if x0 == 0:
        del piece["faces"]["west"]  # it meets the next bench's
    if x1 == 16:
        del piece["faces"]["east"]
    return [piece]


PARTS = {
    # the seat's ends are left open: a joined bench's meets the next one's, and an unjoined one shows its end grain
    "seat": [box("seat", [0, SEAT_TOP - 2, 3], [16, SEAT_TOP, 13], "#planks")],
    "rail": rail("rail", 3, 13),
    "end_left": end("left", 1),
    "end_right": end("right", 13),
    "rail_left": rail("rail_left", 0, 3),
    "rail_right": rail("rail_right", 13, 16),
}
WHOLE = ("seat", "rail", "end_left", "end_right")


def write(path, model):
    path.write_bytes((json.dumps(model, indent=2) + "\n").encode("utf-8"))
    print("wrote", path.relative_to(ROOT))


def main():
    for part, elements in PARTS.items():
        write(MODELS / f"template_bench_{part}.json", {"parent": "minecraft:block/block", "textures": TEXTURES, "elements": elements})
    write(MODELS / "template_bench.json", {
        "parent": "minecraft:block/block", "textures": TEXTURES,
        "elements": [e for part in WHOLE for e in PARTS[part]],
        # low and long, it is lifted in its slot and shown a little smaller than a block, so it is not a sliver at the
        # slot's bottom beside the stool
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 2.5, 0], "scale": [0.7, 0.7, 0.7]}},
    })


if __name__ == "__main__":
    main()
