"""Generate the Magnet item textures: a horseshoe magnet seen from the front, tips pointing up.

- magnet.png          : red body, steel pole pieces
- advanced_magnet.png : deep blue body, diamond pole pieces held on by a brass band

The Magnet Chest's own textures are hand-finished and are not written here; see HAND_FINISHED.md.

Run from the repo root:  python tools/textures/gen_magnet.py
"""
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/textures/item"

# o outline; 1-5 the body dark to bright, each arm lit from the left like a bent bar; A-E the pole pieces;
# s the seam where the pole piece meets the body (a brass band on the Advanced Magnet).
SHAPE = [
    "................",
    ".ooooo....ooooo.",
    ".oEDCo....oDCBo.",
    ".oDCBo....oCBAo.",
    ".oCBAo....oBAAo.",
    ".ossso....ossso.",
    ".o542o....o321o.",
    ".o532o....o321o.",
    ".o432o....o321o.",
    ".o4322o..o3211o.",
    ".o43322oo22211o.",
    ".o433222222211o.",
    "..o3322222211o..",
    "...o12222221o...",
    "....oooooooo....",
    "................",
]


def build(outline, body, pole, seam):
    colours = {".": (0, 0, 0, 0), "o": outline, "s": seam}
    colours.update({str(i + 1): c for i, c in enumerate(body)})
    colours.update({"ABCDE"[i]: c for i, c in enumerate(pole)})
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(SHAPE):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            img.putpixel((x, y), colours[ch])
    return img


def rgb(code):
    return (int(code[0:2], 16), int(code[2:4], 16), int(code[4:6], 16), 255)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    red = [rgb(c) for c in ("8a1c22", "b62e30", "d8483e", "ee7a66", "ffc0a8")]
    steel = [rgb(c) for c in ("6c7480", "9aa2ae", "c4cad4", "e6eaf0", "ffffff")]
    # the outline a step lighter where it faces the light, as vanilla's two-tone outlines are
    texkit.retone(build(rgb("3a1418"), red, steel, rgb("5a1a1e")), rgb("3a1418"), rgb("6a2228")).save(OUT / "magnet.png")
    blue = [rgb(c) for c in ("1c2468", "2e3c9c", "4a60c8", "7a94e8", "c0d4ff")]
    diamond = [rgb(c) for c in ("2a8c8a", "4cc4be", "7ee8de", "b4fff4", "ffffff")]
    texkit.retone(build(rgb("141838"), blue, diamond, rgb("e0b048")), rgb("141838"), rgb("283070")).save(OUT / "advanced_magnet.png")
    print("wrote magnet textures to", OUT)
