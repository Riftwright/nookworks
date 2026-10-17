"""Water Pump and Copper Pipe: textures (all original pixel art), models and blockstates. Deterministic.

The pipe is the Mercury Pipe's shape in copper, with verdigris collars at the joints, so the two read as one family
and are told apart at a glance. Its models are children of the Mercury Pipe's with the textures swapped.

- copper_pipe.png, copper_pipe_collar.png, copper_pipe_end.png   (the models sample the middle 8x8, as the mercury ones)
- models/block/copper_pipe_pull.json: the steel flange on an arm whose side pulls (pull_flange, also the Experience
  Pipe's, in gen_experience.py); no texture of its own, the Mercury Pipe's steel

Run from the repo root:  python tools/textures/gen_water.py
"""
import json
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"

COPPER = (190, 106, 74, 255)
COPPER_LIGHT = (214, 136, 98, 255)
COPPER_DARK = (160, 84, 60, 255)
COPPER_DEEP = (118, 60, 44, 255)
VERDIGRIS = (74, 150, 128, 255)
VERDIGRIS_LIGHT = (104, 180, 156, 255)
VERDIGRIS_DARK = (48, 106, 92, 255)
BOLT = (226, 212, 190, 255)
BOLT_SHADE = (164, 140, 116, 255)
IRON = (58, 60, 66, 255)
IRON_LIGHT = (84, 86, 94, 255)
IRON_DARK = (36, 37, 42, 255)
BORE = (16, 22, 30, 255)
WATER = (52, 92, 170, 255)
WATER_LIGHT = (86, 128, 204, 255)


def mottle(img, rng, base, light, dark, box=(0, 0, 16, 16)):
    for y in range(box[1], box[3]):
        for x in range(box[0], box[2]):
            roll = rng.random()
            img.putpixel((x, y), light if roll < 0.13 else dark if roll < 0.26 else base)


def pipe_barrel():
    img = Image.new("RGBA", (16, 16))
    mottle(img, random.Random(5), COPPER, COPPER_LIGHT, COPPER_DARK)
    for x in range(16):  # a lit edge and a shaded one along the barrel, which the models show lengthways
        img.putpixel((x, 4), COPPER_LIGHT)
        img.putpixel((x, 11), COPPER_DARK)
    # Across the pipe - columns 4 to 11, the width every arm's face takes (uv 4..12) - shade it round: dark at both
    # edges, a bright line just off the lit one, as light runs along a copper tube. Flat, it read as terracotta.
    round_shade(img, (COPPER_DEEP, (240, 166, 124, 255), COPPER_LIGHT, None, None, None, COPPER_DARK, COPPER_DEEP))
    return img


def round_shade(img, columns):
    """Paints columns 4..11 in the given colours top to bottom (None leaves the mottle), so a face reads as a tube."""
    for i, colour in enumerate(columns):
        if colour is not None:
            for y in range(16):
                img.putpixel((4 + i, y), colour)


def pipe_collar():
    """A verdigris band with a bolt at each end; the models sample rows 6-7."""
    img = Image.new("RGBA", (16, 16))
    mottle(img, random.Random(17), VERDIGRIS, VERDIGRIS_LIGHT, VERDIGRIS_DARK)
    for x in range(16):
        img.putpixel((x, 6), VERDIGRIS_LIGHT)
        img.putpixel((x, 7), VERDIGRIS_DARK)
    for bolt_x in (4, 10):
        for y in range(6, 8):
            for x in range(bolt_x, bolt_x + 2):
                img.putpixel((x, y), BOLT_SHADE if y == 7 else BOLT)
    return img


def pipe_end():
    """The bore: a copper flange round a dark opening with water glinting in it. Only the middle 8x8 shows."""
    img = Image.new("RGBA", (16, 16))
    mottle(img, random.Random(23), COPPER, COPPER_LIGHT, COPPER_DARK)
    for y in range(6, 10):
        for x in range(6, 10):
            img.putpixel((x, y), BORE)
    img.putpixel((7, 8), WATER)
    img.putpixel((8, 8), WATER_LIGHT)
    for i in range(5, 11):
        for p in ((i, 5), (i, 10), (5, i), (10, i)):
            img.putpixel(p, VERDIGRIS_DARK)
    for corner in ((4, 4), (10, 4), (4, 10), (10, 10)):
        for y in range(corner[1], corner[1] + 2):
            for x in range(corner[0], corner[0] + 2):
                img.putpixel((x, y), BOLT_SHADE if (x, y) == (corner[0] + 1, corner[1] + 1) else BOLT)
    return img


def frame(img):
    """A dark iron frame round the face, as on the other machines."""
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, IRON_DARK)
    for i in range(1, 15):
        img.putpixel((i, 1), IRON_LIGHT)
        img.putpixel((1, i), IRON_LIGHT)
        img.putpixel((i, 14), IRON)
        img.putpixel((14, i), IRON)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def pull_flange(lo, hi, depth, band_lo, band_hi, band_to):
    """The model over an arm whose side pulls (client/render/PipePullRenderer turns it to the side, facing north as the
    arm's own model does): a flange of the Mercury Pipe's pale steel against the block it draws from, `lo`..`hi` across
    and `depth` deep, wider than the pipe's own collar and hiding it; then a dark bolted band, narrower, out to
    `band_to` - a step down towards the pipe, the way the fluid goes. The steel stands out on copper and on glass alike.
    The flange's rim takes a plain column of the steel (the middle columns are shaded round, for the barrel)."""
    steel = "#steel"
    band = "#band"
    deep = band_to - depth
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "nookworks:block/mercury_pipe", "steel": "nookworks:block/mercury_pipe",
                         "band": "nookworks:block/mercury_pipe_collar"},
            "elements": [
                {"name": "flange", "from": [lo, lo, 0], "to": [hi, hi, depth], "faces": {
                    "north": {"texture": steel, "uv": [lo, lo, hi, hi]},
                    "south": {"texture": steel, "uv": [lo, lo, hi, hi]},
                    "east": {"texture": steel, "uv": [0, lo, depth, hi]},
                    "west": {"texture": steel, "uv": [16 - depth, lo, 16, hi]},
                    "up": {"texture": steel, "uv": [0, lo, depth, hi], "rotation": 90},
                    "down": {"texture": steel, "uv": [16 - depth, lo, 16, hi], "rotation": 90}}},
                {"name": "band", "from": [band_lo, band_lo, depth], "to": [band_hi, band_hi, band_to], "faces": {
                    "south": {"texture": band, "uv": [band_lo, band_lo, band_hi, band_hi]},
                    "east": {"texture": band, "uv": [band_lo, 6, band_hi, 6 + deep], "rotation": 90},
                    "west": {"texture": band, "uv": [band_lo, 6, band_hi, 6 + deep], "rotation": 90},
                    "up": {"texture": band, "uv": [band_lo, 6, band_hi, 6 + deep]},
                    "down": {"texture": band, "uv": [band_lo, 6, band_hi, 6 + deep]}}}]}


def main():
    for seed, (name, fn) in enumerate((("copper_pipe", pipe_barrel), ("copper_pipe_collar", pipe_collar), ("copper_pipe_end", pipe_end))):
        # finished as vanilla's copper is shaded: the flat copper picked out into close shades, the bore and bolts exact
        texkit.finish(fn(), 540 + seed, keep=(BORE, BOLT, BOLT_SHADE, WATER, WATER_LIGHT), **texkit.SMOOTH).save(BLOCK / f"{name}.png")
    textures = {"particle": "nookworks:block/copper_pipe", "barrel": "nookworks:block/copper_pipe",
                "collar": "nookworks:block/copper_pipe_collar", "bore": "nookworks:block/copper_pipe_end"}
    for part in ("core", "side", "end", "inventory"):
        write_json(ASSETS / f"models/block/copper_pipe_{part}.json",
                   {"parent": f"nookworks:block/mercury_pipe_{part}", "textures": textures})
    # an arm whose side pulls: the flange 12 across over the 10 px collar, the band round the 8 px pipe
    write_json(ASSETS / "models/block/copper_pipe_pull.json", pull_flange(2, 14, 2, 3, 13, 4))
    states = json.loads((ASSETS / "blockstates/mercury_pipe.json").read_text(encoding="utf-8"))
    write_json(ASSETS / "blockstates/copper_pipe.json",
               json.loads(json.dumps(states).replace("nookworks:block/mercury_pipe_", "nookworks:block/copper_pipe_")))
    write_json(ASSETS / "items/copper_pipe.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/copper_pipe_inventory"}})
    # the Water Pump's faces, model and blockstate (with its lamp) are the machines' (gen_machine_faces.py,
    # gen_machine_models.py)
    write_json(ASSETS / "items/water_pump.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/water_pump"}})
    print("wrote the water pump and pipe")


if __name__ == "__main__":
    main()
