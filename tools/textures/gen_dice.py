"""The Dice: its six faces, its model and its blockstate. Original pixel art.

The die is small (half a block) and bone (it is crafted from one), with black pips - except the one, which is a single large red pip,
the way Japanese dice are marked. Opposite faces add up to seven.

- block/dice_1.png ... dice_6.png
- models/block/dice_1.json: the die unturned - 1 up, 6 down, 2 north, 5 south, 3 east, 4 west, as DiceTumble.FACE has
  them - which its renderer (DiceRenderer) turns and sets down wherever the die lies, rolling or at rest; and the item
- models/block/dice_drawn.json: nothing but the particle - the block draws nothing of itself, since a die lies where it
  came to rest, off the middle of its block and turned any way, which a block model cannot follow
- blockstates/dice.json: one variant, dice_drawn, for every state

Faces are drawn 8 px square, the die's own size, pixel for pixel; flat, then finished with texkit SMOOTH - polished
bone, a faint cloudiness - with the pips kept exact.

Run from the repo root:  python tools/textures/gen_dice.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

IVORY = (238, 230, 212)
IVORY_LIGHT = (250, 246, 234)
IVORY_SHADE = (214, 204, 184)
PIP = (30, 28, 32)
PIP_RIM = (120, 112, 104)
RED = (196, 40, 40)
RED_DARK = (132, 22, 26)

# The die is 8 px a side, so each face is drawn 8 px square, pixel for pixel (a 16 px face squeezed onto it would
# halve its pixels and blur the pips): in the top-left 8x8 of its texture, the rest filled for the break particles.
# A face is 7 px of bone with a pixel of shadow along its right and bottom edges; pips sit on a 7 px grid.
L, M, H = 1, 3, 5
PIPS = {1: [(M, M)], 2: [(L, L), (H, H)], 3: [(L, L), (M, M), (H, H)],
        4: [(L, L), (H, L), (L, H), (H, H)], 5: [(L, L), (H, L), (M, M), (L, H), (H, H)],
        6: [(L, L), (H, L), (L, M), (H, M), (L, H), (H, H)]}


def face(n):
    img = Image.new("RGBA", (16, 16), IVORY + (255,))
    for y in range(8):
        for x in range(8):
            colour = IVORY_SHADE if (x == 7 or y == 7) else IVORY_LIGHT if (x == 0 or y == 0) else IVORY
            img.putpixel((x, y), colour + (255,))
    for cx, cy in PIPS[n]:
        if n == 1:  # one large red pip, three across, its corners a darker red so it reads round, not square
            for y in range(cy - 1, cy + 2):
                for x in range(cx - 1, cx + 2):
                    corner = abs(x - cx) + abs(y - cy) == 2
                    img.putpixel((x, y), (RED_DARK if corner else RED) + (255,))
        else:
            img.putpixel((cx, cy), PIP + (255,))
    return img


# The die unturned: the number on each face (opposite faces add up to seven).
FACES = {"up": 1, "down": 6, "north": 2, "south": 5, "east": 3, "west": 4}


def model():
    """The die unturned, every face drawn (its renderer turns it any way up, so no face is left to the block below)."""
    textures = {"particle": "nookworks:block/dice_1"}
    faces = {}
    for side, n in FACES.items():
        textures[side] = f"nookworks:block/dice_{n}"
        faces[side] = {"texture": f"#{side}", "uv": [0, 0, 8, 8]}
    element = {"from": [4, 0, 4], "to": [12, 8, 12], "faces": faces}
    return {"parent": "minecraft:block/block", "textures": textures, "elements": [element]}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    for n in range(1, 7):
        texkit.finish(face(n), n, keep=[PIP, PIP_RIM, RED, RED_DARK], **texkit.SMOOTH).save(ASSETS / f"textures/block/dice_{n}.png")
    write_json(ASSETS / "models/block/dice_1.json", model())
    write_json(ASSETS / "models/block/dice_drawn.json", {"textures": {"particle": "nookworks:block/dice_1"}})
    write_json(ASSETS / "blockstates/dice.json", {"variants": {"": {"model": "nookworks:block/dice_drawn"}}})
    write_json(ASSETS / "items/dice.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/dice_1"}})
    print("wrote dice textures, model and blockstate")


if __name__ == "__main__":
    main()
