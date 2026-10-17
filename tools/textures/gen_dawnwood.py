"""Dawnwood: every texture of the wood that comes back from a Rare Seed. All original pixel art. Deterministic.

Dawnwood is a relict: a conifer thought long gone, whose seeds still turn up in old soil. It is drawn to sit apart
from every vanilla wood - a shaggy cinnamon bark that peels in long strips, over pale sage-grey heartwood, under
soft, feathery bright-green needles. The heartwood is a pale olive, greener than oak and duller than bamboo.

block/dawnwood_log, dawnwood_log_top                 bark in long peeling strips; the cut end, rings round a pith
block/stripped_dawnwood_log, stripped_dawnwood_log_top
block/dawnwood_planks
block/dawnwood_leaves                                 feathery sprays with gaps between (cutout), not tinted
block/dawnwood_sapling                                a young tree: tiers of drooping sprays on a thin stem
block/dawnwood_door_top, dawnwood_door_bottom, dawnwood_trapdoor
item/dawnwood_door, item/rare_seed

Every face is drawn flat in a few shades, then finished with texkit (which gives each shade its hue-shifted ramp and
a little grain, as vanilla's woods are shaded); outlines and the colours that mark something are kept exact.

Run from the repo root:  python tools/textures/gen_dawnwood.py
"""
import math
import random
from pathlib import Path

from PIL import Image

import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"
ITEM = ROOT / "src/main/resources/assets/nookworks/textures/item"
CLEAR = (0, 0, 0, 0)

# Cinnamon bark, darkest to palest, and the pale inner bark that shows where a strip has peeled: a brown cinnamon,
# not a red one, so the bark and the olive wood inside it read as one tree.
BARK = [(46, 30, 22), (68, 47, 34), (90, 64, 44), (114, 82, 56), (140, 104, 72), (166, 129, 92)]
INNER = (188, 155, 112)
# Sage heartwood, darkest to palest.
SAGE = [(76, 72, 45), (96, 92, 59), (118, 114, 75), (140, 136, 91), (160, 156, 107), (180, 176, 125), (200, 196, 146)]
# Feathery needles, deepest shade to the lit tips.
NEEDLE = [(30, 66, 32), (46, 94, 38), (68, 124, 48), (96, 158, 62), (114, 170, 72), (140, 190, 94)]  # the lit tips soft, not glaring


def px(img, x, y, colour, wrap=False):
    if wrap:
        x, y = x % img.width, y % img.height
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), colour if len(colour) == 4 else colour + (255,))


def value_noise(size, cells, seed):
    """Smooth noise that wraps at the edges, 0..1, for irregular rings and patches."""
    rng = random.Random(seed)
    grid = [[rng.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        u, v = x * cells / size, y * cells / size
        x0, y0 = int(u) % cells, int(v) % cells
        x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
        fx, fy = u - int(u), v - int(v)
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        top = grid[y0][x0] * (1 - fx) + grid[y0][x1] * fx
        bottom = grid[y1][x0] * (1 - fx) + grid[y1][x1] * fx
        return top * (1 - fy) + bottom * fy
    return at


# ---------------------------------------------------------------- bark and the cut end


def bark():
    """
    Long vertical strips of bark, as a vanilla log's bark runs - four to a face, so each reads: a ridge lit along its
    left, the strip's own shade, and a furrow in shade down its right, the strip wandering a pixel left or right down
    its length. Here and there a strip peels back from its top, the pale inner bark showing under its curled lit edge.
    Tiles up and down: a log is a column of these.
    """
    rng = random.Random(21)
    img = Image.new("RGBA", (16, 16))
    # strips across the face: (start column, width, shade)
    strips = [(0, 4, 3), (4, 5, 2), (9, 3, 3), (12, 4, 2)]
    for y in range(16):
        for x in range(16):
            px(img, x, y, BARK[2])
    for x0, w, shade in strips:
        # each column of the strip in long runs of one shade, as bark's fibres run: no pixel-by-pixel speckle
        columns = []
        for i in range(w - 1):
            run, tones = 0, []
            while len(tones) < 16:
                tone = BARK[shade + 1] if i == 0 else BARK[shade] if rng.random() < 0.7 else BARK[shade - 1]
                tones += [tone] * rng.randint(3, 7)
            columns.append(tones[:16])
        furrow, tones = [], []
        while len(furrow) < 16:
            furrow += [BARK[0] if rng.random() < 0.35 else BARK[1]] * rng.randint(3, 6)
        drift = 0
        for y in range(16):
            if y in (5, 11) and rng.random() < 0.6:
                drift = rng.choice((-1, 0, 1))  # the strip wanders
            for i in range(w - 1):  # the strip: its lit ridge, then its body; the last column is the furrow
                px(img, x0 + i + drift, y, columns[i][y], wrap=True)
            px(img, x0 + w - 1 + drift, y, furrow[y], wrap=True)
    # peeling: one strip curling back from its top, the pale inner bark under its lit edge
    for x0, y0, length in ((6, 3, 4),):
        for i in range(length):
            px(img, x0, y0 + i, INNER, wrap=True)
        px(img, x0 + 1, y0, BARK[5], wrap=True)
        px(img, x0 + 1, y0 + 1, BARK[4], wrap=True)
        px(img, x0, y0 + length, BARK[0], wrap=True)
    return texkit.finish(img, 21, keep=(BARK[0], INNER), tile=True, clusters=0.45, grain=0.2, size=3)


def rings(stripped):
    """
    The cut end: growth rings round a dark pith, wobbling as a real stem's do, a lighter band of sapwood outside
    them, two checks (cracks) running out from the middle, and a rim of bark - or, once stripped, the pale outer wood.
    """
    rng = random.Random(33 if stripped else 31)
    wobble = value_noise(16, 4, 35 if stripped else 34)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            # between a square and a circle (vanilla's ends are squarish), and pushed about by the noise
            r = 0.7 * max(abs(dx), abs(dy)) + 0.3 * math.hypot(dx, dy) + (wobble(x, y) - 0.5) * 1.3
            if x in (0, 15) or y in (0, 15):
                c = SAGE[5] if stripped else BARK[rng.choice((2, 3, 3, 4))]
            elif x in (1, 14) or y in (1, 14):
                c = SAGE[6] if stripped else INNER  # the sapwood (or the inner bark) just under the rim
            elif r < 1.3:
                c = SAGE[1]  # the pith
            else:
                ring = int(r * 1.15)
                c = SAGE[4] if ring % 2 else SAGE[3]
                if ring % 3 == 0 and r > 2:
                    c = SAGE[2]  # a darker late-wood ring now and then
            px(img, x, y, c)
    # checks: thin cracks out from the pith
    for (sx, sy), (dx, dy), n in (((8, 8), (1, -1), 5), ((7, 8), (-1, 1), 4)):
        for i in range(1, n):
            px(img, sx + dx * i, sy + dy * i if i % 2 else sy + dy * (i - 1), SAGE[0])
    keep = (SAGE[0], SAGE[1]) + (() if stripped else tuple(BARK))
    return texkit.finish(img, 33 if stripped else 31, keep=keep, **texkit.SMOOTH)


def stripped_side():
    """
    Stripped: the smooth sage wood with its grain running straight up it - long fine lines of a darker and a paler
    shade - bending once round a small knot. Tiles up and down.
    """
    rng = random.Random(41)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            px(img, x, y, SAGE[4])
    knot = (10, 6)
    for x in range(16):
        if x % 2 == 0 or rng.random() < 0.3:
            shade = SAGE[3] if rng.random() < 0.75 else SAGE[5]
            start = rng.randrange(16)
            for i in range(rng.randint(7, 16)):
                y = (start + i) % 16
                bend = 0
                if abs(y - knot[1]) <= 2 and abs(x - knot[0]) <= 2 and x != knot[0]:
                    bend = 1 if x > knot[0] else -1  # the grain parts round the knot
                px(img, x + bend, y, shade, wrap=True)
    px(img, knot[0], knot[1], SAGE[1])
    px(img, knot[0], knot[1] - 1, SAGE[2])
    px(img, knot[0], knot[1] + 1, SAGE[2])
    return texkit.finish(img, 41, keep=(SAGE[1],), tile=True, **texkit.SMOOTH)


def planks():
    """
    Four boards across, all of one wood, read as wood by their grain as vanilla's planks are: every board on the same
    sage, its top row catching the light (dipping to the board's own shade here and there), runs of grain a shade
    darker along it and now and then a shade paler, the board mostly its own shade between them, and under it a seam
    two and three shades down, as vanilla's seams sit well under their boards - the same seam under every board, so a
    wall of them is an even run of boards. (Boards on bases of their own, a whole step apart, banded a wall into a
    stack of pillowed bricks beside oak; a seam only a step down did not read as one from across a room.) A butt joint
    where two boards meet end to end, staggered board to board, is the seam's shade, the end grain after it catching
    the light. The palest shade is only ever a single pixel on a lit edge.
    """
    rng = random.Random(51)
    img = Image.new("RGBA", (16, 16))
    base, darker, paler, lit, palest, seam = SAGE[4], SAGE[3], SAGE[5], SAGE[5], SAGE[6], SAGE[2]
    joints = (9, 3, 13, 6)
    for board in range(4):
        y0 = board * 4
        for x in range(16):
            px(img, x, y0, lit)  # the board's lit edge
            px(img, x, y0 + 1, base)
            px(img, x, y0 + 2, base)
            px(img, x, y0 + 3, seam)  # the seam under it
        x = rng.randrange(16)
        for _ in range(3):  # where the lit edge dips
            n = rng.randint(1, 3)
            for i in range(n):
                px(img, (x + i) % 16, y0, base)
            x = (x + n + rng.randint(3, 6)) % 16
        for row in (y0 + 1, y0 + 2):  # grain, in runs the length of the board
            x, covered = rng.randrange(16), 0
            while covered < 16:
                length = rng.randint(2, 5)
                shade = darker if rng.random() > 0.12 else paler
                for i in range(length):
                    px(img, (x + i) % 16, row, shade)
                gap = rng.randint(3, 6)
                x = (x + length + gap) % 16
                covered += length + gap
        touching = rng.sample(range(16), 10)
        for x in touching[:2]:  # the seam a step lighter where the boards touch, deeper where they part
            px(img, x, y0 + 3, SAGE[3])
        for x in touching[2:]:
            px(img, x, y0 + 3, SAGE[1])
        j = joints[board]
        px(img, j, y0, darker)  # the butt joint, over the grain
        for y in (y0 + 1, y0 + 2):
            px(img, j, y, seam)
        px(img, (j + 1) % 16, y0, palest)  # the end grain after it, lit
        for x in ((j + 6) % 16, (j + 11) % 16):  # and a pixel or two more of the edge catching the light
            if img.getpixel((x, y0))[:3] == lit:
                px(img, x, y0, palest)
    return texkit.finish(img, 51, keep=(seam, palest, SAGE[1]), tile=True, clusters=0.4, grain=0.02, size=5, reach=1)


# ---------------------------------------------------------------- needles


def spray(img, x, y, length, dx, dy_every, droop=True, wrap=True, tip=True):
    """
    One feathery spray: a thin twig with a needle off each side at every pixel along it, the upper needle lit, the
    lower in shade - a flat fern-like frond, as a dawn redwood's are. It runs {@code dx} (+1 right, -1 left) and falls a
    pixel every {@code dy_every} pixels.
    """
    for i in range(length):
        sx, sy = x + dx * i, y + (i // dy_every if droop else 0)
        px(img, sx, sy, NEEDLE[2], wrap)
        near_tip = i >= length - 2
        px(img, sx, sy - 1, NEEDLE[5 if near_tip and tip else 4], wrap)
        px(img, sx, sy + 1, NEEDLE[1 if i % 2 else 2], wrap)
        if i % 3 == 1 and not near_tip:  # longer needles now and then, feathering the edge
            px(img, sx - dx, sy - 2, NEEDLE[3], wrap)
            px(img, sx - dx, sy + 2, NEEDLE[1], wrap)


# The openings cut through the leaves: one to three pixels each, single, paired, bent or broken.
OPENINGS = [((0, 0),), ((0, 0), (1, 0)), ((0, 0), (0, 1)), ((0, 0), (1, 0), (1, 1)), ((0, 0), (1, 0), (0, 1)),
            ((0, 0), (1, 1)), ((0, 0), (1, 0), (2, 1)), ((1, 0), (0, 1), (1, 1))]


def leaves():
    """
    Many short sprays laid across the dark of the tree's inside, lit along their upper side, with small openings
    right through (cutout) - about sixty pixels, as vanilla's leaves have sixty to a hundred, so a canopy is airy, not a
    wall. Everything at the scale of a pixel or two, as vanilla's leaves are: the inside is a fine mottle of three
    shades (a big dark patch of it repeated block after block as diagonal streaks); the sprays go two to every band of
    four rows, in different quarters of it; and the openings go two or three to every four-by-four cell, so they are
    spread evenly, cut only where the inside or a spray's shaded underside shows - a lit spray lies over an opening,
    never cut by one. Wraps at the edges.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    fine, broad = value_noise(16, 8, 64), value_noise(16, 4, 65)
    field = {(x, y): fine(x, y) * 0.6 + broad(x, y) * 0.4 for y in range(16) for x in range(16)}
    for i, (x, y) in enumerate(sorted(field, key=field.get)):  # deep a fifth, in shade a half, the rest a little lit
        px(img, x, y, NEEDLE[0] if i < 52 else NEEDLE[1] if i < 180 else NEEDLE[2])
    rng = random.Random(63)
    sprays = []
    for gy in range(0, 16, 4):
        for gx in rng.sample(range(0, 16, 4), 2):
            sprays.append((gx + rng.randrange(4), gy + rng.randrange(3), rng.randint(3, 5), rng.choice((1, -1)), rng.choice((2, 3, 9))))
    for x, y, length, dx, every in sprays:
        spray(img, x, y, length, dx, every)
    shaded = {NEEDLE[0], NEEDLE[1], NEEDLE[2]}
    rng = random.Random(66)
    for cy in range(0, 16, 4):
        for cx in range(0, 16, 4):
            for _ in range(rng.randint(2, 3)):
                shape = rng.choice(OPENINGS)
                ox, oy = cx + rng.randrange(4), cy + rng.randrange(4)
                where = [((ox + a) % 16, (oy + b) % 16) for a, b in shape]
                if all(img.getpixel(p)[3] and img.getpixel(p)[:3] in shaded for p in where):
                    for p in where:
                        img.putpixel(p, CLEAR)
    return texkit.finish(img, 61, keep=(NEEDLE[5],), tile=True, reach=1, **texkit.PLANT)


def sapling():
    """
    A young tree: a thin cinnamon stem, and on it three tiers of drooping sprays, widest at the bottom, the leader
    standing up at the top - the shape of the grown tree, small.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(4, 16):
        px(img, 7, y, BARK[3] if y > 10 else BARK[2])
    px(img, 8, 15, BARK[2])
    px(img, 6, 15, BARK[1])
    tiers = ((11, 6), (7, 5), (4, 3))
    for y, reach in tiers:
        spray(img, 6, y, reach, -1, 3, wrap=False)
        spray(img, 8, y, reach, 1, 3, wrap=False)
    px(img, 7, 3, NEEDLE[3])
    px(img, 7, 2, NEEDLE[4])
    px(img, 7, 1, NEEDLE[5])
    return texkit.finish(img, 71, keep=(NEEDLE[5], BARK[1]), reach=1, **texkit.PLANT)


# ---------------------------------------------------------------- door and trapdoor


def frame_rect(img, x0, y0, x1, y1, lit, dark):
    """A raised frame's edge: lit along its top and left, shadowed along its bottom and right."""
    for x in range(x0, x1 + 1):
        px(img, x, y0, lit)
        px(img, x, y1, dark)
    for y in range(y0, y1 + 1):
        px(img, x0, y, lit)
        px(img, x1, y, dark)


def door(top):
    """
    A frame-and-panel door: stiles down both sides and rails across, a darker sage than the boards they hold. The
    upper half has a window of four panes (cut out, as vanilla's doors are see-through there); the lower half two
    boarded panels, set back in the frame, and the handle.
    """
    img = Image.new("RGBA", (16, 16))
    board = planks().rotate(90)  # boards standing up
    for y in range(16):
        for x in range(16):
            px(img, x, y, board.getpixel((x, y)))
    # stiles and rails: the frame, 2 px, in the darker wood
    for y in range(16):
        for x in (0, 1, 14, 15):
            px(img, x, y, SAGE[2] if x in (0, 15) else SAGE[3])
    rails = (0, 1) if top else (7, 8, 14, 15)
    for y in rails:
        for x in range(16):
            px(img, x, y, SAGE[3] if y % 2 == 0 else SAGE[2])
    for y in range(16):
        px(img, 0, y, SAGE[1])  # the door's edges, darkest
        px(img, 15, y, SAGE[1])
    if top:
        px(img, 0, 0, SAGE[1])
        for x in range(16):
            px(img, x, 0, SAGE[1])
        # the window: four panes in a cross of glazing bars, set in a lit frame
        frame_rect(img, 3, 3, 12, 12, SAGE[5], SAGE[1])
        for y in range(4, 12):
            for x in range(4, 12):
                if x in (7, 8) or y in (7, 8):
                    px(img, x, y, SAGE[3] if (x in (7,) or y in (7,)) else SAGE[2])  # the glazing bars
                else:
                    img.putpixel((x, y), CLEAR)
        # a rail under the window
        for x in range(2, 14):
            px(img, x, 14, SAGE[2])
            px(img, x, 15, SAGE[3])
    else:
        # two panels, set back: a shadow along their top and left edges, light along the bottom and right
        for y0, y1 in ((2, 6), (9, 13)):
            frame_rect(img, 3, y0, 12, y1, SAGE[1], SAGE[5])
        # the handle, iron
        px(img, 12, 3, (38, 36, 34))
        px(img, 12, 4, (84, 80, 74))
        px(img, 12, 5, (38, 36, 34))
        for x in range(16):
            px(img, x, 15, SAGE[1])
    return texkit.finish(img, 81 if top else 82, keep=(SAGE[1], (38, 36, 34), (84, 80, 74)), **texkit.SMOOTH)


def trapdoor():
    """
    A frame (2 px, the darker wood) holding a lattice of thin strips on the diagonals, crossing in the middle, a
    diamond open between them every eight pixels: light and a look through, as vanilla's wooden trapdoors give, in a
    pattern none of theirs has. (The user, 2026-10-11: oak's four windows in a cross, as it was, read as oak in another
    colour; this lattice picked over a kumiko diamond and boards in a chevron.)
    """
    img = Image.new("RGBA", (16, 16))
    for i in range(16):
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            px(img, x, y, SAGE[3])
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            px(img, x, y, SAGE[1])
    for y in range(2, 14):
        for x in range(2, 14):
            a, b = (x + y - 15) % 8, (x - y) % 8  # the diagonals through the middle, and every eight pixels out
            if a == 0 or b == 0:
                px(img, x, y, SAGE[4])  # a strip
            elif a == 1 or b == 1:
                px(img, x, y, SAGE[2])  # its shaded side
            else:
                img.putpixel((x, y), CLEAR)
    return texkit.finish(img, 92, keep=(SAGE[1], SAGE[2], SAGE[4]), **texkit.SMOOTH)


# The Dawnwood items' colours (itemkit): the sage of its planks with a wider range than a block face needs - a slot
# shows an item small, and a narrow range read as washed out - shadows a little cooler, lights a little warmer; the
# stripped log a step darker for posts and beams; iron, glass and nail heads for the fittings.
ITEM_WOOD = {"1": (88, 92, 58), "2": (112, 114, 74), "3": (136, 134, 88), "4": (160, 156, 104), "5": (184, 178, 124),
             "6": (210, 202, 150), "d": (66, 70, 44)}
ITEM_POST = {"p": (96, 90, 62), "q": (124, 116, 80), "r": (150, 142, 98)}
ITEM_IRON = {"k": (168, 170, 176), "j": (110, 112, 120), "K": (62, 64, 72)}
ITEM_GLASS = {"a": (220, 232, 216), "b": (180, 202, 190), "c": (146, 168, 158)}
ITEM_NAIL = {"n": (70, 70, 64), "m": (150, 150, 140)}


def door_item():
    """
    The door as an item, as vanilla draws its door items: the whole door in its ring - the four-paned window above,
    its glass catching the light, a rail lit along its top, the panel below set back in its frame (in shadow along its
    top and left, lit along its foot), the iron handle; the hinge stile lit, the far one in shade.
    """
    rows = itemkit.check([
        "................",
        "....66666665....",
        "....5ab3ab32....",
        "....5bc3bc32....",
        "....54444432....",
        "....5ab3ab32....",
        "....5bc3bc32....",
        "....56666662....",
        "....533333k2....",
        "....522222K2....",
        "....52444452....",
        "....52434452....",
        "....52444452....",
        "....55555552....",
        "....53333322....",
        "................",
    ])
    return itemkit.sprite(rows, {**ITEM_WOOD, **ITEM_GLASS, **ITEM_IRON})


# The Rare Seed: two of the tree's cones on a forked stalk, with a sprig of its needles where the stalk meets - a
# Metasequoia's cones hang on long bare stalks, their scales in opposite pairs, so in rows of diamonds. Drawn small, about
# twelve pixels across as vanilla's seeds are, with room round it in the slot (2026-10-02, the user's pick and nudge: a
# pixel higher). Shades 1 (deepest) to 7 are the bark's (and its inner wood); k and j the stalk, dark and lit; c to f the
# needles.
RARE_SEED = [
    "................",
    ".........e......",
    ".......dcd......",
    "........k.......",
    ".......j.k......",
    "......k...k.....",
    ".....k....53....",
    "...6746..6746...",
    "...5341..5341...",
    "...7463..7453...",
    "...3413..2412...",
    "...4634...53....",
    "................",
    "................",
    "................",
    "................",
]
STALK = {"k": (54, 36, 26), "j": (82, 58, 40)}


def rare_seed():
    """
    The cones ringed as vanilla rings an item (texkit.outline), the stalk and the needles not: a ring round a line one
    pixel wide makes it three, as a cherry's stem is drawn without one.
    """
    shades = {str(i + 1): c for i, c in enumerate(BARK)} | {"7": INNER} | STALK | dict(zip("abcdef", NEEDLE))
    img = Image.new("RGBA", (16, 16), CLEAR)
    cones = Image.new("RGBA", (16, 16), CLEAR)
    for y, row in enumerate(RARE_SEED):
        for x, ch in enumerate(row):
            if ch != ".":
                px(img, x, y, shades[ch])
                if ch.isdigit():
                    px(cones, x, y, shades[ch])
    ring = texkit.outline(cones)
    for y in range(16):
        for x in range(16):
            if not img.getpixel((x, y))[3] and ring.getpixel((x, y))[3]:
                img.putpixel((x, y), ring.getpixel((x, y)))
    return img


def main():
    out = {
        BLOCK / "dawnwood_log.png": bark(),
        BLOCK / "dawnwood_log_top.png": rings(False),
        BLOCK / "stripped_dawnwood_log.png": stripped_side(),
        BLOCK / "stripped_dawnwood_log_top.png": rings(True),
        BLOCK / "dawnwood_planks.png": planks(),
        BLOCK / "dawnwood_leaves.png": leaves(),
        BLOCK / "dawnwood_sapling.png": sapling(),
        BLOCK / "dawnwood_door_top.png": door(True),
        BLOCK / "dawnwood_door_bottom.png": door(False),
        BLOCK / "dawnwood_trapdoor.png": trapdoor(),
        ITEM / "dawnwood_door.png": door_item(),
        ITEM / "rare_seed.png": rare_seed(),
    }
    for path, img in out.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
    print(f"wrote {len(out)} dawnwood textures")


if __name__ == "__main__":
    main()
