"""Pebbles: small smooth stones to scatter, one to four in a block, as sea pickles lie (the user, 2026-10-06:
"サイコロくらいのサイズの、小石"). Stone-textured from vanilla's own picture by name (minecraft:block/stone, never a
copy); the icon is ours. Deterministic.

- models/block/pebbles_1.json ... pebbles_4.json: the stones, each an oversized stone button - a flat slab with its
  corners cut back and a half-pixel bevel round its top - the first 6 by 4 and 2 high, the others smaller (the user,
  2026-10-06, of four drafts seen in the game: a large stone button, a flat river stone, mixed kinds of stone, one big
  and small round ones; before that round chunky stones, "やっぱり大きめの石のボタンみたいな感じで"). A stone added joins
  those already there without moving them, each set a little apart, turned its own way and its own size, so they lie
  scattered rather than in a grid. Each face shows the stone picture texel for pixel, as a whole block's face would.
- blockstates/pebbles.json: each count turned a quarter at random, place to place (as vanilla turns sea pickles), so a
  path of them never repeats; the block's outline is square about the middle (PebblesBlock), so it holds them every way.
- item/pebbles.png (itemkit): three grey pebbles, lit from the upper left, each with its shaded side under it and a
  smooth stone's gleam; ringed in their own dark grey. models/item/pebbles.json, items/pebbles.json.

Run from the repo root:  python tools/textures/gen_pebbles.py
"""
import json
import math
from pathlib import Path

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

# The stones in the order they are added: the middle of each (x, z), its length and breadth, its height, and the angle
# it is turned by about y.
STONES = [
    (7.2, 7.0, 6.0, 4.0, 2.0, 18),
    (11.7, 11.9, 4.5, 3.0, 1.5, -30),
    (12.1, 3.5, 4.0, 3.0, 1.5, 8),
    (3.8, 11.9, 4.0, 2.5, 1.5, -56),
]
# PebblesBlock's outline, square about the middle: its width for each count, and its height. Checked below: a stone
# outside it would be drawn where a click does not reach.
OUTLINE = {1: 10.0, 2: 14.0, 3: 14.0, 4: 14.0}
OUTLINE_HEIGHT = 3.0
APART = 0.5  # the least gap between two stones, so none is drawn into another


def footprint(stone):
    """The corners of a stone's foot, turned as the game turns an element about y."""
    cx, cz, w, d, _, angle = stone
    t = math.radians(angle)
    return [(cx + x * math.cos(t) + z * math.sin(t), cz - x * math.sin(t) + z * math.cos(t))
            for x, z in ((-w / 2, -d / 2), (w / 2, -d / 2), (w / 2, d / 2), (-w / 2, d / 2))]


def _inside(point, corners):
    x, z = point
    signs = set()
    for (x1, z1), (x2, z2) in zip(corners, corners[1:] + corners[:1]):
        cross = (x2 - x1) * (z - z1) - (z2 - z1) * (x - x1)
        if abs(cross) > 1e-9:
            signs.add(cross > 0)
    return len(signs) == 1


def _to_edge(point, a, b):
    (px, pz), (ax, az), (bx, bz) = point, a, b
    dx, dz = bx - ax, bz - az
    t = max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz)))
    return math.hypot(px - ax - t * dx, pz - az - t * dz)


def gap(a, b):
    """How far apart two feet are; below 0 where they overlap."""
    if any(_inside(p, b) for p in a) or any(_inside(p, a) for p in b):
        return -1.0
    return min(_to_edge(point, *edge) for corners, other in ((a, b), (b, a)) for point in corners
               for edge in zip(other, other[1:] + other[:1]))


def check():
    feet = [footprint(s) for s in STONES]
    for i in range(len(feet)):
        for j in range(i + 1, len(feet)):
            assert gap(feet[i], feet[j]) >= APART, f"stones {i + 1} and {j + 1} lie too close"
    for count, width in OUTLINE.items():
        for i in range(count):
            for x, z in feet[i]:
                assert abs(x - 8) <= width / 2 and abs(z - 8) <= width / 2, \
                    f"stone {i + 1} reaches past the outline of {count}"
            assert STONES[i][4] <= OUTLINE_HEIGHT


def projected_uv(side, f, t):
    """The patch of the stone picture a face shows: what a whole block's face would show there, as the game works it
    out for a face given none - so the parts of one stone run on into each other, and no two stones show one patch."""
    return {"down": [f[0], 16 - t[2], t[0], 16 - f[2]], "up": [f[0], f[2], t[0], t[2]],
            "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]], "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
            "west": [f[2], 16 - t[1], t[2], 16 - f[1]], "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]}[side]


def part(stone, x0, x1, z0, z1, y0, y1, hidden=()):
    """A box of a stone, {@code x0} to {@code x1} and {@code z0} to {@code z1} from its middle, turned with it; without
    the faces {@code hidden} against another part, and its foot only on the ground."""
    cx, cz, _, _, _, angle = stone
    f = [round(cx + x0, 3), float(y0), round(cz + z0, 3)]
    t = [round(cx + x1, 3), float(y1), round(cz + z1, 3)]
    faces = {}
    for side in ("down", "up", "north", "south", "west", "east"):
        if side in hidden or (side == "down" and y0 > 0):
            continue
        faces[side] = {"uv": [round(v, 3) for v in projected_uv(side, f, t)], "texture": "#stone"}
        if side == "down":
            faces[side]["cullface"] = "down"
    return {"from": f, "to": t, "rotation": {"origin": [cx, 0, cz], "axis": "y", "angle": angle}, "faces": faces}


def layer(stone, w, d, corner, y0, y1):
    """A layer of a stone, {@code w} by {@code d}, its corners cut back by {@code corner}: a middle the full breadth,
    and a shorter piece at each end."""
    if corner <= 0:
        return [part(stone, -w / 2, w / 2, -d / 2, d / 2, y0, y1)]
    return [part(stone, -w / 2 + corner, w / 2 - corner, -d / 2, d / 2, y0, y1),
            part(stone, -w / 2, -w / 2 + corner, -d / 2 + corner, d / 2 - corner, y0, y1, hidden=("east",)),
            part(stone, w / 2 - corner, w / 2, -d / 2 + corner, d / 2 - corner, y0, y1, hidden=("west",))]


def stone_elements(stone):
    """A stone: an oversized stone button - a slab its full size with its corners cut back, and round its top a bevel
    half a pixel in and half a pixel high."""
    _, _, w, d, h, _ = stone
    return layer(stone, w, d, 0.75, 0, h - 0.5) + layer(stone, w - 1.0, d - 1.0, 0.5, h - 0.5, h)


def model(count):
    elements = [e for stone in STONES[:count] for e in stone_elements(stone)]
    return {"parent": "minecraft:block/block",
            "textures": {"particle": "minecraft:block/stone", "stone": "minecraft:block/stone"}, "elements": elements}


# The icon (itemkit): three pebbles, the big one in front at the left, a middle-sized one to its right and a small one
# behind - each a flat stone seen from above and in front: its top lit from the upper left (4), a gleam there (5),
# going to shade at its lower right (3, 2), and its side under it in shade (2, 1). Kept apart by a pixel, which the ring
# fills, so they read as three, not one lump.
GREY = {"5": (214, 212, 204), "4": (180, 179, 174), "3": (150, 150, 149), "2": (121, 122, 127), "1": (94, 96, 105)}
ICON = itemkit.check([
    "................",
    "................",
    "................",
    ".......4554.....",
    "......455443....",
    "......344332....",
    ".......22221....",
    "...4554.111.....",
    "..455544........",
    ".45544433..454..",
    ".34443332.45543.",
    ".23333222.34432.",
    "..2222221.22221.",
    "...11111...111..",
    "................",
    "................",
])


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    check()
    for count in range(1, 5):
        write_json(ASSETS / f"models/block/pebbles_{count}.json", model(count))
    variants = {f"pebbles={count}": [{"model": f"nookworks:block/pebbles_{count}", **({"y": y} if y else {})}
                                     for y in (0, 90, 180, 270)] for count in range(1, 5)}
    write_json(ASSETS / "blockstates/pebbles.json", {"variants": variants})
    # the grain a stone has, the gleam left exact
    texkit.icon(itemkit.paint(ICON, GREY), 61, keep=[GREY["5"]]).save(ASSETS / "textures/item/pebbles.png")
    write_json(ASSETS / "models/item/pebbles.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/pebbles"}})
    write_json(ASSETS / "items/pebbles.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/pebbles"}})
    print("wrote pebbles")


if __name__ == "__main__":
    main()
