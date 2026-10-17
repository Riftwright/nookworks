"""Storage Barrel: every face of the five tiers, plus the models and blockstates that put them on a barrel.

The diamond barrel is the user's own drawing (2026-10-11, tools/textures/HAND_FINISHED.md): its side, lid and base
are the masters, never written here. Warm staves held by two bands of metal, lit along their top and dark underneath;
the lid sunk in a dark frame inside the rim, with brackets at its corners and a hasp at its front. The other tiers are
made from those faces, as the user asked: every pixel of the diamond's metal - bands, brackets, hasp - recoloured to the
tier's metal along its lightness, the wood copied across untouched. Open, every tier shows the master lid's rim round
the barrel's dark inside: the whole square opens, not a round lid.

- block/<tier>_storage_barrel_side.png       staves and the two bands (the lid's end at the top of the texture)
- block/<tier>_storage_barrel_top.png        the lid, closed
- block/<tier>_storage_barrel_top_open.png   the barrel open
- block/<tier>_storage_barrel_bottom.png     the base
- models/block/<tier>_storage_barrel.json, <tier>_storage_barrel_open.json   (cube_bottom_top, like the vanilla barrel)
- blockstates/<tier>_storage_barrel.json                               (FACING is the lid; rotations as the vanilla barrel)

Run from the repo root:  python tools/textures/gen_storage.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"
FACES = ("side", "top", "bottom")
TIERS = ("iron", "gold", "diamond", "netherite", "velarium")

# The barrel's inside, darkest to palest, seen with the lid off.
INSIDE = [(22, 15, 10), (32, 22, 14), (42, 30, 19), (58, 41, 26), (78, 56, 36)]

# Each tier's metal, dark to light: the shadowed edge, the face, the lit edge and a glint. The diamond's are the ones
# its master is drawn in, against which every metal pixel's lightness is placed.
METALS = {
    "iron": [(140, 142, 150), (200, 202, 208), (240, 240, 244), (255, 255, 255)],
    "gold": [(156, 116, 34), (230, 184, 56), (255, 226, 96), (255, 246, 176)],
    "diamond": [(46, 142, 134), (80, 206, 194), (126, 250, 238), (214, 255, 250)],
    # netherite's dark metal, lit along its top edge in a pale grey so a band shows against the wood
    "netherite": [(38, 32, 42), (82, 72, 86), (150, 138, 152), (196, 184, 200)],
    # the Block of Velarium's violet
    "velarium": [(70, 44, 124), (140, 100, 206), (206, 176, 246), (232, 216, 255)],
}


def is_metal(p):
    """A pixel of the diamond's metal, its near-white glints too: blue and green above red, where the wood is the other
    way round."""
    return p[2] > p[0] + 10 and p[1] > p[0] + 10


def lightness(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def recolour(img, tier):
    """{@code img} with its metal in the tier's: each pixel placed along the diamond's ramp by its lightness, and given
    the colour at the same place along the tier's (the user's shading kept, one band lit, the next in shadow)."""
    if tier == "diamond":
        return img.copy()
    stops = [lightness(c) for c in METALS["diamond"]]
    ramp = METALS[tier]
    out = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if a == 0 or not is_metal((r, g, b)):
                continue
            v = lightness((r, g, b))
            if v <= stops[0]:
                c = ramp[0]
            elif v >= stops[-1]:
                c = ramp[-1]
            else:
                k = next(i for i in range(len(stops) - 1) if stops[i] <= v <= stops[i + 1])
                t = (v - stops[k]) / (stops[k + 1] - stops[k])
                c = tuple(round(ramp[k][i] + (ramp[k + 1][i] - ramp[k][i]) * t) for i in range(3))
            out.putpixel((x, y), c + (a,))
    return out


def top_open(lid):
    """The lid off: the closed lid's rim, and the whole square inside it open down to the barrel's floor."""
    rng = random.Random(11)
    img = lid.copy()
    for y in range(1, 15):
        for x in range(1, 15):
            if y == 1 or x == 1:
                colour = INSIDE[0]  # the inner walls under the rim at the upper left, in shadow
            elif y == 14 or x == 14:
                colour = INSIDE[4] if y == 14 and x == 14 else INSIDE[3]  # the lower right walls, catching light
            elif x in (5, 9) or x == 12:
                colour = INSIDE[0]  # the joins of the floor's staves
            else:
                colour = INSIDE[rng.choice((1, 2, 2, 2))]
                if y == 2 or x == 2:
                    colour = INSIDE[1]
            img.putpixel((x, y), colour + (255,))
    return img


def model(tier, open_lid):
    return {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": f"nookworks:block/{tier}_storage_barrel_top" + ("_open" if open_lid else ""),
            "bottom": f"nookworks:block/{tier}_storage_barrel_bottom",
            "side": f"nookworks:block/{tier}_storage_barrel_side",
        },
    }


# The vanilla barrel's layout: the model's top is the lid, turned to face FACING.
ROTATIONS = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
             "west": {"x": 90, "y": 270}, "east": {"x": 90, "y": 90}}


def blockstate(tier):
    variants = {}
    for facing in ("north", "east", "south", "west", "up", "down"):
        for locked in ("false", "true"):
            for open_lid in ("false", "true"):
                variant = {"model": f"nookworks:block/{tier}_storage_barrel" + ("_open" if open_lid == "true" else "")}
                variant.update(ROTATIONS[facing])
                variants[f"facing={facing},locked={locked},open={open_lid}"] = variant
    return {"variants": variants}


def write_json(path, data):
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


if __name__ == "__main__":
    masters = {face: Image.open(BLOCK / f"diamond_storage_barrel_{face}.png").convert("RGBA") for face in FACES}
    for tier in TIERS:
        if tier != "diamond":  # the diamond's own faces are the masters (HAND_FINISHED.md)
            for face in FACES:
                recolour(masters[face], tier).save(BLOCK / f"{tier}_storage_barrel_{face}.png")
        top_open(recolour(masters["top"], tier)).save(BLOCK / f"{tier}_storage_barrel_top_open.png")
        write_json(ASSETS / f"models/block/{tier}_storage_barrel.json", model(tier, False))
        write_json(ASSETS / f"models/block/{tier}_storage_barrel_open.json", model(tier, True))
        write_json(ASSETS / f"blockstates/{tier}_storage_barrel.json", blockstate(tier))
    print("wrote storage barrel textures, models and blockstates")
