"""Generate the Wrench and Building Wand textures. Deterministic.
(The Repair Box has gen_repair_box.py, the Trash Can gen_trash_can.py.)

Items:  wrench, building_wand

Run from the repo root:  python tools/textures/gen_tools.py
"""
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"
ITEM = ROOT / "src/main/resources/assets/nookworks/textures/item"

CLEAR = (0, 0, 0, 0)


def blank(size=16):
    return Image.new("RGBA", (size, size), CLEAR)


def draw(rows, palette):
    img = blank()
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            colour = palette.get(ch)
            if colour is not None:
                img.putpixel((x, y), colour)
    return img


STEEL = [(34, 38, 46, 255), (74, 82, 94, 255), (118, 126, 138, 255), (160, 168, 178, 255), (204, 210, 218, 255),
         (240, 244, 248, 255)]
GRIP = [(60, 18, 16, 255), (128, 36, 30, 255), (176, 58, 46, 255), (214, 92, 70, 255)]


# An open-ended spanner lying corner to corner: the jaw at the top right opens away from the handle, a red rubber
# grip on the shaft, a ring at the foot. o outline, 1-5 steel dark to bright, d r R L the grip dark to bright.
WRENCH = [
    "..........oo....",
    ".........o43o...",
    "........o432o...",
    "........o43o..oo",
    "........o32o.o3o",
    "........o3211o2o",
    ".......o3211121o",
    "......o3211oooo.",
    ".....dLRrd......",
    "....dLRrd.......",
    "..oo321o........",
    ".o44321o........",
    ".o3..21o........",
    ".o2..11o........",
    ".o21111o........",
    "..oooooo........",
]


def wrench():
    return draw(WRENCH, {"o": STEEL[0], "1": STEEL[1], "2": STEEL[2], "3": STEEL[3], "4": STEEL[4], "5": STEEL[5],
                         "d": GRIP[0], "r": GRIP[1], "R": GRIP[2], "L": GRIP[3]})


# The Building Wand (itemkit), held as vanilla holds a stick: a shaft of wood corner to corner, lit along its upper left
# edge (L), its body (M) and shade (S) by turns down its lower right as a stick's are, ringed in the wood's own dark; a
# two-pixel brass ferrule (B, b) at its head; on that a cut gem - a light facet to the upper left (t), the mid (T), a
# deep facet to the lower right (D), a white glint at its crown (W) - ringed in deep teal, and one sparkle beside it,
# unringed. (The old one's shaft was lit from the wrong side, its head a flat checker, its ring near black.)
WAND = itemkit.check([
    "................",
    "................",
    "...........W....",
    "..........tWT...",
    ".........tTTD...",
    "..........TDD...",
    "........BbD.....",
    ".......LS.......",
    "......LM........",
    ".....LS.........",
    "....LM..........",
    "...LS...........",
    "..LM............",
    "..S.............",
    "................",
    "................",
])
WAND_COLOURS = {"L": (176, 128, 72), "M": (136, 96, 52), "S": (96, 64, 34), "B": (238, 204, 112), "b": (172, 124, 44),
                "D": (18, 92, 98), "T": (34, 156, 156), "t": (104, 218, 204), "W": (236, 255, 248)}
# the ring is the wood's under the shaft and the ferrule, and a deep teal round the gem
WAND_RING = dict(WAND_COLOURS, B=WAND_COLOURS["S"], b=WAND_COLOURS["S"], **{ch: (28, 108, 112) for ch in "DTtW"})
WAND_SPARKLE = (13, 2)


def building_wand():
    img = itemkit.sprite(WAND, WAND_COLOURS, ring_from=WAND_RING)
    img.putpixel(WAND_SPARKLE, (255, 255, 255, 255))
    return img


def main():
    BLOCK.mkdir(parents=True, exist_ok=True)
    ITEM.mkdir(parents=True, exist_ok=True)
    # vanilla's two outline tones: a step lighter where the edge faces the light, near black where it faces away
    texkit.retone(wrench(), STEEL[0], STEEL[1]).save(ITEM / "wrench.png")
    building_wand().save(ITEM / "building_wand.png")
    print("wrote tool textures")


if __name__ == "__main__":
    main()
