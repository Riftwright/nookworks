"""The Energy Cable: its texture and its models - a cable, not a beam.

Four pixels thick, in black rubber insulation, with a pale clip round it midway along each run and a copper coupling
where it meets the next block, so a line of cable reads as lengths of cable joined end to end, and a cable that runs
into a machine ends in a copper terminal on its face. (Six pixels of dark hatched rubber with a copper stripe down every
face read as a rusted girder.) The clips and couplings are small rings a little proud of the cable, as parts of their
own, so the texture has no direction to get wrong on a face turned one way or another.

- block/energy_cable.png   three swatches: the rubber [0, 0, 16, 8], the clip [0, 8, 8, 16], the copper [8, 8, 16, 16]
- models/block/energy_cable_core.json, energy_cable_side.json (the arm to the north; the blockstate turns it),
  energy_cable_inventory.json (a length of cable, a coupling at each end)

The machines' own faces are drawn by gen_machine_faces.py, and the hand-finished ones are listed in HAND_FINISHED.md;
this script must not write any of them.

Run from the repo root:  python tools/textures/gen_machines.py
"""
import json
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
OUT = ASSETS / "textures/block"

RUBBER = [(26, 26, 30), (40, 40, 46), (56, 56, 64)]      # deep, body, sheen
CLIP = [(126, 128, 134), (170, 172, 178), (206, 208, 212)]
COPPER = [(142, 70, 40), (190, 104, 62), (224, 142, 92), (246, 186, 140)]
TEX = "#cable"


def cable():
    rng = random.Random(570)
    img = Image.new("RGBA", (16, 16))
    for y in range(8):
        for x in range(16):
            r = rng.random()
            img.putpixel((x, y), (RUBBER[2] if r < 0.1 else RUBBER[0] if r < 0.25 else RUBBER[1]) + (255,))
    for y in range(8, 16):
        for x in range(8):  # the clip: lit along its top
            img.putpixel((x, y), (CLIP[2] if y == 9 else CLIP[0] if y >= 13 else CLIP[1]) + (255,))
        for x in range(8, 16):  # the copper: a bright edge, then warm, darker below
            img.putpixel((x, y), (COPPER[3] if y == 9 else COPPER[2] if y < 12 else COPPER[1] if y < 14 else COPPER[0]) + (255,))
    return img


def face(uv, **extra):
    f = {"texture": TEX, "uv": uv}
    f.update(extra)
    return f


def model(elements):
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "nookworks:block/energy_cable", "cable": "nookworks:block/energy_cable"},
            "elements": elements}


def core():
    return model([{"from": [6, 6, 6], "to": [10, 10, 10],
                   "faces": {f: face([2, 2, 6, 6]) for f in ("north", "east", "south", "west", "up", "down")}}])


def side():
    """The arm to the north: rubber from the core to a pixel short of the face, a clip round it, the coupling at the face."""
    arm = {"from": [6, 6, 1], "to": [10, 10, 6],
           "faces": {"east": face([1, 2, 6, 6]), "west": face([1, 2, 6, 6]), "up": face([2, 1, 6, 6]), "down": face([2, 1, 6, 6])}}
    clip = {"from": [5.7, 5.7, 3], "to": [10.3, 10.3, 4],
            "faces": {"east": face([1, 9, 2, 13.6]), "west": face([1, 9, 2, 13.6]),
                      "up": face([1, 9, 5.6, 10]), "down": face([1, 9, 5.6, 10]),
                      "north": face([1, 9, 5.6, 13.6]), "south": face([1, 9, 5.6, 13.6])}}
    coupling = {"from": [5.5, 5.5, 0], "to": [10.5, 10.5, 1],
                "faces": {"east": face([9, 9, 10, 14]), "west": face([9, 9, 10, 14]),
                          "up": face([9, 9, 14, 10]), "down": face([9, 9, 14, 10]),
                          "north": face([9, 9, 14, 14], cullface="north"), "south": face([9, 9, 14, 14])}}
    return model([arm, clip, coupling])


def inventory():
    """A length of cable lying east to west: couplings at its ends, a clip at each third."""
    elements = [{"from": [1, 6, 6], "to": [15, 10, 10],
                 "faces": {f: face([1, 2, 15, 6]) for f in ("north", "south", "up", "down")}}]
    for x0 in (5, 10):
        elements.append({"from": [x0, 5.7, 5.7], "to": [x0 + 1, 10.3, 10.3],
                         "faces": {**{f: face([1, 9, 2, 13.6]) for f in ("north", "south", "up", "down")},
                                   "east": face([1, 9, 5.6, 13.6]), "west": face([1, 9, 5.6, 13.6])}})
    for x0 in (0, 15):
        elements.append({"from": [x0, 5.5, 5.5], "to": [x0 + 1, 10.5, 10.5],
                         "faces": {**{f: face([9, 9, 10, 14]) for f in ("north", "south", "up", "down")},
                                   "east": face([9, 9, 14, 14]), "west": face([9, 9, 14, 14])}})
    return model(elements)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    texkit.finish(cable(), 570, keep=(COPPER[3] + (255,), CLIP[2] + (255,)), **texkit.SMOOTH).save(OUT / "energy_cable.png")
    write_json(ASSETS / "models/block/energy_cable_core.json", core())
    write_json(ASSETS / "models/block/energy_cable_side.json", side())
    write_json(ASSETS / "models/block/energy_cable_inventory.json", inventory())
    print("wrote the energy cable")


if __name__ == "__main__":
    main()
