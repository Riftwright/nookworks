"""Crate: a box of sawn boards - a frame of pale, heavy boards standing proud round each face, slats set back inside
it, darker in its shade, with dark gaps between them - braced corner to corner on its sides and nailed. Original pixel
art (no vanilla pixels). Deterministic.

It used to be vanilla's spruce planks inside a frame of stripped dark oak (log grain running every way along the
edges), which read as a block of planks with a dark border rather than a crate. Redrawn 2026-10-06 at the user's word
("木箱の見た目ももっといい感じにできないかな"). The frame is the lighter wood: it stands out into the light, and
darker boards behind a lighter frame read as set back, where a dark frame round light boards read as a border drawn on.

- block/crate_side.png: the frame round the edge - rails across the top and bottom, lying over the ends of the
  uprights down the sides - and three slats running across inside it, with gaps dark from the inside of the box; a
  brace from the bottom left to the top right; a nail at each corner and at each end of the brace. The frame stands
  1 px proud, so the slats are shadowed under the top rail and beside the left upright, and the brace throws its
  shadow down its right side.
- block/crate_top.png: the lid, the top and the bottom alike: the frame and its slats, with no brace.
- models/block/crate.json: the box as it was - the twelve edges of the frame 2 px square, the slatted faces 1 px in
  from them - with every face taking the texture of its side by where it lies (no uv given), so the edges show the
  frame drawn round the texture and, where three of them overlap at a corner, the same pixels: nothing flickers.

Drawn flat in a few tones a board, the grain in streaks along each board, then finished with texkit.shade as wood (lightly: sawn
boards, not stone); the brace and the nails go on after the finish, so its noise does not break them up.

Run from the repo root:  python tools/textures/gen_crate.py
"""
import json
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

# shadow, shade, body, lit, edge: the frame's pale sawn boards, and the slats set back in its shade
FRAME = [(130, 96, 62), (152, 116, 76), (172, 134, 88), (190, 150, 100), (204, 166, 114)]
SLAT = [(92, 67, 43), (110, 81, 52), (128, 95, 61), (144, 108, 70), (158, 120, 79)]
GAP = (58, 41, 27)  # between two slats: the inside of the box, in shadow (a dark brown, never black)
NAIL = (150, 147, 140)  # a nail head: plain iron, a step under the pale wood so it does not glare
SEED = 19
INNER = range(2, 14)  # the slats' part of a face, inside the 2 px frame
SIDE_GAPS = (6, 10)  # the sides' three slats: 4, 3 and 3 px, sawn by hand and not quite even
TOP_GAPS = (5, 9)  # the lid's
FINISH = {"clusters": 0.45, "grain": 0.08, "size": 4, "reach": 1}


def px(img, x, y, c):
    img.putpixel((x, y), tuple(c[:3]) + (255,))


def clamp(i):
    return max(0, min(4, i))


def streaks(rng, lines, span, count):
    """Runs of grain along a board, {(line, position): step}: a few px of a shade (mostly) or a light."""
    out = {}
    for _ in range(count):
        line = rng.choice(lines)
        length = rng.randint(2, 5)
        start = rng.randint(span[0], span[1] - length)
        step = rng.choice((-1, -1, 1))
        for p in range(start, start + length):
            out[(line, p)] = step
    return out


def frame(img, rng):
    """The 2 px frame round the face: the rails across the top and bottom run the whole width, over the uprights'
    ends; the uprights run down between them. Each board's edge on the side the light comes from (above, left) is lit,
    and the box's own lower and right edges are a shade down."""
    across = streaks(rng, [0, 1, 14, 15], (0, 16), 6)
    down = streaks(rng, [0, 1, 14, 15], (2, 14), 4)
    for y in range(16):
        for x in range(16):
            if y < 2 or y > 13:
                base = 3 if y in (0, 14) else 2 if y == 1 else 1
                base += across.get((y, x), 0)
            elif x < 2 or x > 13:
                base = 3 if x in (0, 14) else 2 if x == 1 else 1
                base += down.get((x, y), 0)
                if y == 2:
                    base = 1  # an upright's top end, in under the rail
            else:
                continue
            px(img, x, y, FRAME[clamp(base)])


def slats(img, rng, gaps):
    """The slats inside the frame, running across, the gaps between them dark; shadowed under the top rail and beside
    the left upright, which stand 1 px proud of them."""
    rows = [y for y in INNER if y not in gaps]
    grain = streaks(rng, rows, (2, 14), 8)
    for y in INNER:
        for x in INNER:
            if y in gaps:
                px(img, x, y, GAP)
                continue
            base = 2
            if y - 1 in gaps:
                base += 1  # a board's upper edge, lit
            elif y + 1 in gaps or y == 13:
                base -= 1  # its lower edge, rounding off into the gap
            base += grain.get((y, x), 0)
            if y == 2 or x == 2:
                base = min(base, 1) - (1 if (y == 2 and x == 2) else 0)  # in the frame's shadow
            px(img, x, y, SLAT[clamp(base)])


def brace(img):
    """A board from the bottom left corner to the top right, laid over the slats: its upper edge lit, a little grain
    along it, and its shadow on the slats below and right of it."""
    for y in INNER:
        for x in INNER:
            s = x + y
            if s == 14:
                px(img, x, y, FRAME[3])
            elif s in (15, 16):
                px(img, x, y, FRAME[1] if s == 16 and x in (5, 9, 10) else FRAME[2])
            elif s == 17:
                px(img, x, y, GAP if img.getpixel((x, y))[:3] == GAP else SLAT[0])


def side():
    rng = random.Random(SEED)
    img = Image.new("RGBA", (16, 16))
    frame(img, rng)
    slats(img, rng, SIDE_GAPS)
    img = texkit.shade(img, SEED, keep=[GAP], material="wood", **FINISH)
    brace(img)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (3, 12), (12, 3)):  # the frame's corners, the brace's ends
        px(img, x, y, NAIL)
    return img


def top():
    rng = random.Random(SEED + 1)
    img = Image.new("RGBA", (16, 16))
    frame(img, rng)
    slats(img, rng, TOP_GAPS)
    img = texkit.shade(img, SEED + 1, keep=[GAP], material="wood", **FINISH)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px(img, x, y, NAIL)
    return img


# The frame's twelve edges, each 2 px square, and the slatted box 1 px inside them: the model as it has been.
EDGES = [
    ((0, 0, 0), (16, 2, 2)), ((0, 0, 14), (16, 2, 16)), ((0, 14, 0), (16, 16, 2)), ((0, 14, 14), (16, 16, 16)),
    ((0, 0, 0), (2, 16, 2)), ((0, 0, 14), (2, 16, 16)), ((14, 0, 0), (16, 16, 2)), ((14, 0, 14), (16, 16, 16)),
    ((0, 0, 0), (2, 2, 16)), ((0, 14, 0), (2, 16, 16)), ((14, 0, 0), (16, 2, 16)), ((14, 14, 0), (16, 16, 16)),
]
TEXTURE_OF = {"north": "#side", "east": "#side", "south": "#side", "west": "#side", "up": "#top", "down": "#top"}


def element(lo, hi, cull):
    faces = {}
    for face in ("north", "east", "south", "west", "up", "down"):
        entry = {"texture": TEXTURE_OF[face]}
        on_edge = {"north": lo[2] == 0, "south": hi[2] == 16, "west": lo[0] == 0, "east": hi[0] == 16,
                   "down": lo[1] == 0, "up": hi[1] == 16}[face]
        if cull and on_edge:
            entry["cullface"] = face
        faces[face] = entry
    return {"from": list(lo), "to": list(hi), "faces": faces}


def model():
    elements = [element((1, 1, 1), (15, 15, 15), False)] + [element(lo, hi, True) for lo, hi in EDGES]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "nookworks:block/crate_side", "side": "nookworks:block/crate_side",
                         "top": "nookworks:block/crate_top"},
            "elements": elements}


def main():
    side().save(ASSETS / "textures/block/crate_side.png")
    top().save(ASSETS / "textures/block/crate_top.png")
    path = ASSETS / "models/block/crate.json"
    path.write_bytes((json.dumps(model(), indent=2) + "\n").encode("utf-8"))
    print("wrote the crate")


if __name__ == "__main__":
    main()
