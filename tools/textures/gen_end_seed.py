"""The End Seed and the sprout it grows (the user, 2026-10-12). Original pixel art, drawn flat and finished with
texkit.shade; what the sprout opens into is vanilla's own plant, so nothing of it is drawn here.

- item/end_seed: one plump seed, end-on to the light, in the End's violet with flecks of gold, and a glint beside
  it - a seed out of the ordinary, from the End's gravel
- block/end_sprout_stage0..2: one plain green seedling seen side on, on the cross model as the sunflower crop is,
  low and with few leaves as vanilla's own crops start: two seed leaves in a V; the first true leaves above them; a
  modest leafy sprout with a fresh pair at its tip. Nothing in it says what it will open into - a poppy, a fern,
  bamboo or a sweet berry bush - so any of them follows it naturally (the user, 2026-10-14: the bud showing a flower's
  colours looked too strong to turn into a poppy or a bush). The End's strangeness is a faint purple mote that now and
  then rises from it, drawn in EndSproutBlock, not in the texture
- models/block/end_sprout_stage0..2, blockstates/end_sprout, models/item and items/end_seed

Run from the repo root:  python tools/textures/gen_end_seed.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

SEED = [(70, 40, 96), (108, 66, 140), (148, 102, 178), (196, 160, 214)]  # shade, body, lit, sheen
FLECK = (232, 194, 82)
GLINT = [(250, 242, 196), (232, 204, 120)]  # centre, arms
STEM = [(36, 90, 30), (54, 116, 32)]  # shade, lit
LEAF = [(34, 86, 30), (58, 128, 34), (84, 156, 42)]  # under, face, lit edge
# The sprout's stages drawn pixel by pixel, a row a line, the ground under the last: S the stalk, s its foot in shade,
# L a leaf's lit upper edge, l its face, d its underside.
STAGES = [
    [  # two seed leaves in a V on a short stalk
        "....LL...LL.....",
        "....llL.Lll.....",
        ".....ddSdd......",
        ".......S........",
        ".......s........",
    ],
    [  # the first true leaves, the seed leaves still under them
        "........LL......",
        "........ll......",
        "..LLL..S.d......",
        "..lllL.S.LLL....",
        "...ddlLSLllll...",
        ".......S..ddd...",
        ".....L.S.L......",
        ".....ldSdl......",
        ".......S........",
        ".......S........",
        ".......s........",
    ],
    [  # a leafy sprout, leaves turn about, a fresh pair opening at its tip
        ".....LL...L.....",
        "......lL.Ll.....",
        ".......lSd......",
        "..LLL..S........",
        ".Lllll.S.LLL....",
        "..dddlLSLlllL...",
        ".......S.ddlll..",
        "...LLL.S....dd..",
        "..LllllS..LL....",
        "...dddlSLllll...",
        ".......S..dd....",
        ".......s........",
    ],
]
COLOURS = {"S": STEM[1], "s": STEM[0], "L": LEAF[2], "l": LEAF[1], "d": LEAF[0]}


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c + (255,))


def seed():
    """A teardrop seed leaning right, its point up: lit along its upper left, a sheen there, flecked with gold; and a
    four-pointed glint off its shoulder."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    angle, (cx, cy) = math.radians(20), (7.2, 9.0)
    for y in range(16):
        for x in range(16):
            ax, ay = x + 0.5 - cx, y + 0.5 - cy
            u = (ax * math.sin(angle) + ay * math.cos(angle)) / 5.6   # -1 at the point, 1 at the base
            v = (ax * math.cos(angle) - ay * math.sin(angle)) / 3.8
            if abs(u) > 1:
                continue
            half = math.sqrt(1 - u * u) * (0.45 + 0.55 * min(1.0, (u + 1) / 1.3))  # narrow to the point
            if abs(v) > half:
                continue
            t = v / half if half else 0
            c = SEED[3] if t < -0.55 and u < 0.1 else SEED[2] if t < -0.1 else SEED[1] if t < 0.6 else SEED[0]
            if (x * 5 + y * 3) % 7 == 0 and -0.5 < t < 0.6 and abs(u) < 0.8:
                c = FLECK
            px(img, x, y, c)
    px(img, 12, 3, GLINT[0])
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        px(img, 12 + dx, 3 + dy, GLINT[1])
    return img


def crop(stage):
    img = Image.new("RGBA", (16, 16), CLEAR)
    rows = STAGES[stage]
    for y, row in enumerate(rows, start=16 - len(rows)):
        assert len(row) == 16, (stage, row)
        for x, ch in enumerate(row):
            if ch in COLOURS:
                px(img, x, y, COLOURS[ch])
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures"
    texkit.icon(seed(), 430, keep=[FLECK, *GLINT]).save(tex / "item/end_seed.png")
    write_json(ASSETS / "models/item/end_seed.json", {"parent": "minecraft:item/generated",
                                                          "textures": {"layer0": "nookworks:item/end_seed"}})
    write_json(ASSETS / "items/end_seed.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/end_seed"}})
    for stage in range(3):
        texkit.shade(crop(stage), 440 + stage, keep=[CLEAR], reach=1, **texkit.PLANT).save(tex / f"block/end_sprout_stage{stage}.png")
        write_json(ASSETS / f"models/block/end_sprout_stage{stage}.json", {
            "parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
            "textures": {"cross": f"nookworks:block/end_sprout_stage{stage}"}})
    write_json(ASSETS / "blockstates/end_sprout.json", {"variants": {
        f"age={a}": {"model": f"nookworks:block/end_sprout_stage{a}"} for a in range(3)}})
    print("wrote the End Seed and its sprout")


if __name__ == "__main__":
    main()
