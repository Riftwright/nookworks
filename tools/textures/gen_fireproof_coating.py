"""Generate the Fireproof Coating item texture: a tin of amber coating with a brush in it, placed pixel by pixel
(itemkit).

Run from the repo root:  python tools/textures/gen_fireproof_coating.py [--preview out.png]
"""
import sys
from pathlib import Path

from PIL import Image

import itemkit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/textures/item/fireproof_coating.png"

COLOURS = {
    "1": (70, 72, 84), "2": (110, 112, 124), "3": (156, 158, 170), "4": (204, 206, 214), "5": (240, 242, 246),  # tin
    "a": (150, 76, 14), "b": (204, 120, 28), "c": (240, 170, 60), "d": (255, 220, 130),  # the amber coating
    "l": (168, 150, 116), "m": (212, 198, 164), "n": (238, 230, 204),  # the paper label
    "w": (96, 62, 32), "W": (150, 106, 60), "f": (184, 186, 196),  # the brush: its handle, its ferrule
}
TIN = (  # (row, column, pixels)
    (0, 13, "Ww"), (1, 12, "Ww"), (2, 11, "Ww"), (3, 10, "Ww"), (4, 9, "ff"),  # the brush, leaning in the coating
    (5, 4, "3444f443"),  # the tin's back rim
    (6, 3, "3cdcbbbba2"),  # the coating in it
    (7, 3, "4554433321"),  # the front rim, lit
    (8, 3, "3544333b21"),  # a drip down the side
    (9, 3, "nnmmmcmlll"),  # the label, a flame on it
    (10, 3, "nnmmcdbmll"),
    (11, 3, "nnmmbbamll"),
    (12, 3, "3544333221"),
    (13, 3, "3443333221"),
    (14, 4, "22222111"),
)


def draw():
    """A tin of amber coating seen a little from above, a brush leaning in it: the tin lit down its left as a
    cylinder is, its rims lit, the coating showing in its mouth and run down its side, a paper label round it with a
    flame on it."""
    rows = [["."] * 16 for _ in range(16)]
    for y, x, run in TIN:
        rows[y][x:x + len(run)] = run
    return itemkit.sprite(itemkit.check(["".join(r) for r in rows]), COLOURS)


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img = draw()
    img.save(OUT)
    if "--preview" in sys.argv:
        target = Path(sys.argv[sys.argv.index("--preview") + 1])
        bg = Image.new("RGBA", (18, 18), (139, 139, 139, 255))
        bg.paste(img, (1, 1), img)
        bg.resize((180, 180), Image.NEAREST).save(target)
    print("wrote", OUT)
