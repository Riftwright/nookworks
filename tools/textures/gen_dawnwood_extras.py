"""Dawnwood's signs, hanging signs, shelf and boats: their textures. Original pixel art. Deterministic.

Each is laid out as vanilla lays out a wood's (the block models and the boat model are vanilla's, and read their
texture by those regions), and filled with Dawnwood's own wood as gen_dawnwood.py draws it: the sage planks and the
stripped wood, cut to fit, with edges, end grain, iron and shadow drawn here flat and finished with texkit.

block/dawnwood_sign.png           32x32: the board's faces (planks, three boards across), its edges, the post
block/dawnwood_hanging_sign.png   32x32: the bar it hangs from (stripped wood, iron straps where the chains go round),
                                  the chains, the board (stripped wood in a darker rim)
block/dawnwood_shelf.png          32x32: the rails, the back inside (three bays), the back outside, and the four
                                  inside backs of a powered row (open, framed at the row's ends)
gui/signs/dawnwood.png            24x26: the sign as its edit screen shows it
gui/hanging_signs/dawnwood.png    16x16: the hanging sign as its edit screen shows it
entity/boat/dawnwood.png          128x64: hull boards, floor and paddles
entity/chest_boat/dawnwood.png    128x128: the same, and the chest it carries
item/dawnwood_sign.png, item/dawnwood_hanging_sign.png
items/dawnwood_[chest_]boat.json, models/item/dawnwood_[chest_]boat.json: the boats as items are vanilla's birch boat
                                  and birch chest boat, referenced by name and tinted to Dawnwood's colour in the item
                                  definition - nothing of vanilla's copied; a resource pack's birch boat shows through

Run from the repo root:  python tools/textures/gen_dawnwood_extras.py
"""
from pathlib import Path

from PIL import Image

import json

import gen_dawnwood as dw
import itemkit
import texkit

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src/main/resources/assets/nookworks/textures"
SAGE = dw.SAGE
CLEAR = (0, 0, 0, 0)
IRON = [(34, 36, 44), (58, 62, 74), (92, 98, 112), (132, 138, 152)]
OUT = (52, 48, 30)  # the darkest line, for outlines on the items

PLANKS = dw.planks()
STRIPPED = dw.stripped_side()  # grain running up
STRIPPED_ACROSS = STRIPPED.rotate(90)  # grain running across


def px(img, x, y, c):
    dw.px(img, x, y, c)


def cut(src, x0, y0, x1, y1, ox=0, oy=0):
    """A w x h piece of a tiling texture, from (ox, oy) on, wrapping."""
    w, h = x1 - x0, y1 - y0
    piece = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            piece.putpixel((x, y), src.getpixel(((x + ox) % src.width, (y + oy) % src.height)))
    return piece


def fill(img, rect, src, ox=0, oy=0):
    x0, y0, x1, y1 = rect
    img.paste(cut(src, x0, y0, x1, y1, ox, oy), (x0, y0))


def darker(img, steps=1):
    """Every sage shade in {@code img} moved {@code steps} darker, the rest left alone."""
    out = img.copy()
    ramp = {c: SAGE[max(0, i - steps)] for i, c in enumerate(SAGE)}
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            nearest = min(SAGE, key=lambda c: (c[0] - r) ** 2 + (c[1] - g) ** 2 + (c[2] - b) ** 2)
            shade = ramp[nearest]
            out.putpixel((x, y), (r + shade[0] - nearest[0], g + shade[1] - nearest[1], b + shade[2] - nearest[2], a))
    return out


def rect(img, r, c):
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            px(img, x, y, c)


def finish_flat(canvas, flat, seed):
    """Finishes what was drawn flat and lays it over the wood already cut in."""
    canvas.alpha_composite(texkit.finish(flat, seed, keep=tuple(IRON[:1]) + (OUT,), **texkit.SMOOTH))
    return canvas


def box(u, v, w, h, d):
    """The faces of a model box's texture, as the game unwraps a box from (u, v)."""
    return {
        "up": (u + d, v, u + d + w, v + d), "down": (u + d + w, v, u + d + 2 * w, v + d),
        "west": (u, v + d, u + d, v + d + h), "north": (u + d, v + d, u + d + w, v + d + h),
        "east": (u + d + w, v + d, u + 2 * d + w, v + d + h), "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# ---------------------------------------------------------------- signs


def sign():
    img = Image.new("RGBA", (32, 32), CLEAR)
    fill(img, (0, 2, 24, 14), PLANKS)  # front: three boards across
    fill(img, (0, 16, 24, 28), PLANKS, 7, 4)  # back
    post = darker(STRIPPED)
    for r in ((28, 0, 32, 14), (28, 16, 32, 30), (28, 30, 30, 32)):
        fill(img, r, post, r[0], r[1])
    flat = Image.new("RGBA", (32, 32), CLEAR)
    rect(flat, (0, 0, 24, 2), SAGE[5])  # the top edge, lit
    rect(flat, (0, 28, 24, 30), SAGE[2])  # the bottom edge
    for y0 in (2, 16):  # the ends: end grain, the boards' gaps running through
        rect(flat, (24, y0, 26, y0 + 12), SAGE[3])
        for gap in (3, 7, 11):
            rect(flat, (24, y0 + gap, 26, y0 + gap + 1), SAGE[1])
    return finish_flat(img, flat, 61)


def hanging_sign():
    img = Image.new("RGBA", (32, 32), CLEAR)
    fill(img, (0, 0, 16, 13), STRIPPED_ACROSS)  # the bar: top, south, north and bottom faces
    fill(img, (2, 16, 16, 26), STRIPPED_ACROSS, 3, 2)  # the board, front
    fill(img, (18, 16, 32, 26), STRIPPED_ACROSS, 9, 6)  # and back
    flat = Image.new("RGBA", (32, 32), CLEAR)
    rect(flat, (0, 6, 16, 7), CLEAR)
    for r in ((16, 4, 20, 6), (16, 7, 20, 9)):  # the bar's cut ends
        rect(flat, r, SAGE[3])
        px(flat, r[0] + 1, r[1], SAGE[2])
    for x in (3, 4, 11, 12):  # iron straps round the bar where the chains hang
        for y in list(range(0, 6)) + list(range(7, 13)):
            px(flat, x, y, IRON[1] if x in (3, 11) else IRON[2])
    for y in (0, 4, 7, 9):  # the straps' edges catch the light
        px(flat, 4, y, IRON[3])
        px(flat, 12, y, IRON[3])
    # the chains: links alternately face on and edge on
    for x0, y0, y1 in ((22, 7, 13), (28, 8, 12)):
        for i, y in enumerate(range(y0, y1)):
            if i % 3 == 0:
                px(flat, x0 + 1, y, IRON[2])
            else:
                px(flat, x0, y, IRON[1])
                px(flat, x0 + 2, y, IRON[1])
                px(flat, x0 + 1, y, IRON[0] if i % 3 == 1 else CLEAR)
    # the board: a rim a shade darker, lit along the top
    for x0 in (2, 18):
        for x in range(x0, x0 + 14):
            px(flat, x, 16, SAGE[5])
            px(flat, x, 25, SAGE[1])
        for y in range(16, 26):
            px(flat, x0, y, SAGE[2])
            px(flat, x0 + 13, y, SAGE[1])
    for r in ((0, 16, 2, 26), (16, 16, 18, 26)):  # the board's ends
        rect(flat, r, SAGE[2])
    rect(flat, (2, 14, 16, 16), SAGE[4])  # its top
    rect(flat, (2, 26, 16, 28), SAGE[1])  # its bottom
    return finish_flat(img, flat, 62)


def gui_sign():
    img = Image.new("RGBA", (24, 26), CLEAR)
    fill(img, (0, 0, 24, 12), PLANKS)
    post = darker(STRIPPED)
    fill(img, (11, 12, 13, 26), post, 11, 0)
    flat = Image.new("RGBA", (24, 26), CLEAR)
    for x in range(24):
        px(flat, x, 0, SAGE[5])
        px(flat, x, 11, SAGE[1])
    return finish_flat(img, flat, 63)


def gui_hanging_sign():
    img = Image.new("RGBA", (16, 16), CLEAR)
    fill(img, (0, 0, 16, 2), STRIPPED_ACROSS)
    fill(img, (1, 5, 15, 15), STRIPPED_ACROSS, 3, 4)
    flat = Image.new("RGBA", (16, 16), CLEAR)
    for x in (3, 12):
        px(flat, x, 0, IRON[2])
        px(flat, x, 1, IRON[1])
        for y in range(2, 5):
            px(flat, x, y, IRON[2] if y % 2 else IRON[1])
    for x in range(1, 15):
        px(flat, x, 5, SAGE[5])
        px(flat, x, 14, SAGE[1])
    for y in range(5, 15):
        px(flat, 1, y, SAGE[2])
        px(flat, 14, y, SAGE[1])
    return finish_flat(img, flat, 64)


# ---------------------------------------------------------------- shelf


def shelf():
    img = Image.new("RGBA", (32, 32), CLEAR)
    fill(img, (0, 0, 16, 4), STRIPPED_ACROSS)  # the top rail's front
    fill(img, (0, 12, 16, 16), STRIPPED_ACROSS, 5, 0)  # the bottom rail's front
    fill(img, (16, 0, 32, 16), PLANKS)  # the back, outside, and the tops and ends cut from it
    inside = darker(PLANKS, 2)
    fill(img, (0, 4, 16, 12), inside, 0, 1)  # the back inside
    open_back = darker(PLANKS, 3)
    for r in ((0, 16, 16, 24), (16, 16, 32, 24), (0, 24, 16, 32), (16, 24, 32, 32)):
        fill(img, r, open_back, r[0], r[1] + 1)
    flat = Image.new("RGBA", (32, 32), CLEAR)
    for y0 in (0, 12):  # rails: lit on top, shadowed under
        for x in range(16):
            px(flat, x, y0, SAGE[5])
            px(flat, x, y0 + 3, SAGE[2])
    for x in range(16):  # shadow under the top rail, inside
        px(flat, x, 4, SAGE[0])
        px(flat, x, 5, SAGE[1])
    for dx in (5, 10):  # two partitions: three bays, one to an item
        for y in range(5, 12):
            px(flat, dx, y, SAGE[4])
            px(flat, dx + 1, y, SAGE[1])
        px(flat, dx, 4, SAGE[2])
    # a powered row: open inside, the frame showing only at the row's ends
    for x0, y0, left, right in ((0, 16, True, False), (16, 16, False, True), (0, 24, False, False), (16, 24, True, True)):
        for x in range(x0, x0 + 16):
            px(flat, x, y0, SAGE[0])
            px(flat, x, y0 + 1, SAGE[1])
        if left:
            for y in range(y0, y0 + 8):
                px(flat, x0, y, SAGE[3])
        if right:
            for y in range(y0, y0 + 8):
                px(flat, x0 + 15, y, SAGE[2])
    return finish_flat(img, flat, 65)


# ---------------------------------------------------------------- boats


def hull_board(w, h, seed):
    """Boards along the hull, 3 px each, as the planks draw them, staggered."""
    return cut(PLANKS, 0, 0, w, h, seed * 5, seed % 4)


def boat(chest):
    img = Image.new("RGBA", (128, 128 if chest else 64), CLEAR)
    flat = Image.new("RGBA", img.size, CLEAR)
    parts = [(box(0, 0, 28, 16, 3), 0), (box(0, 19, 18, 6, 2), 1), (box(0, 27, 16, 6, 2), 2),
             (box(0, 35, 28, 6, 2), 3), (box(0, 43, 28, 6, 2), 4)]
    for faces, seed in parts:
        for name, r in faces.items():
            wide = r[2] - r[0] > 4 and r[3] - r[1] > 4
            if wide:
                img.paste(hull_board(r[2] - r[0], r[3] - r[1], seed + (name == "south")), (r[0], r[1]))
            else:  # an edge of a board: the gunwale on top, end grain elsewhere
                rect(flat, r, SAGE[5] if name == "up" else SAGE[2])
    # the floor inside, a shade darker than the hull outside, with the frames across it
    floor = box(0, 0, 28, 16, 3)["north"]
    img.paste(darker(cut(PLANKS, *floor, 3, 0)), floor[:2])
    for fx in range(floor[0] + 4, floor[2] - 2, 6):
        for y in range(floor[1], floor[3]):
            px(flat, fx, y, SAGE[1])
    # paddles: a shaft of darker stripped wood, the blade of planks
    for v in (0, 20):
        shaft = box(62, v, 2, 2, 18)
        blade = box(62, v, 1, 6, 7)
        for r in shaft.values():
            fill(img, r, darker(STRIPPED_ACROSS), r[0], r[1])
        for name, r in blade.items():
            fill(img, r, PLANKS if r[2] - r[0] > 2 else darker(PLANKS), r[0], r[1])
        for name in ("west", "east"):
            r = blade[name]
            for x in range(r[0], r[2]):
                px(flat, x, r[1], SAGE[5])
                px(flat, x, r[3] - 1, SAGE[1])
    if chest:
        body, lid, lock = box(0, 76, 12, 8, 12), box(0, 59, 12, 4, 12), box(0, 59, 2, 4, 1)
        for faces, oy in ((body, 1), (lid, 3)):
            for r in faces.values():
                fill(img, r, PLANKS, r[0] + 2, r[1] + oy)
                x0, y0, x1, y1 = r
                for x in range(x0, x1):  # a darker frame round every face, as a chest has
                    px(flat, x, y0, SAGE[2])
                    px(flat, x, y1 - 1, SAGE[1])
                for y in range(y0, y1):
                    px(flat, x0, y, SAGE[2])
                    px(flat, x1 - 1, y, SAGE[1])
        for r in lock.values():
            rect(flat, r, IRON[2])
        px(flat, 1, 61, IRON[3])
        px(flat, 2, 62, IRON[1])
    return finish_flat(img, flat, 66 + chest)


# ---------------------------------------------------------------- items


def outline(img):
    """A one-pixel ring round everything drawn, outside it, in the wood's own colours as vanilla rings its items."""
    return texkit.outline(img)


ITEM_PALETTE = {**dw.ITEM_WOOD, **dw.ITEM_POST, **dw.ITEM_IRON, **dw.ITEM_NAIL}


def sign_item():
    """The sign as an item (itemkit): two boards, each lit along its top and darker to its foot, the seam between them
    dark, a nail head at each end; the post a step darker, in the board's shadow at its top."""
    return itemkit.sprite(itemkit.check([
        "................",
        "................",
        ".56666666666665.",
        ".5m44444434444n.",
        ".44434444444433.",
        ".33333333333322.",
        ".11111111111111.",
        ".56666666666665.",
        ".5m44443444444n.",
        ".43333333333322.",
        ".......pp.......",
        ".......rq.......",
        ".......rq.......",
        ".......qp.......",
        ".......rq.......",
        ".......qp.......",
    ]), ITEM_PALETTE)


def hanging_sign_item():
    """The hanging sign as an item: the beam it hangs from, lit along its top; the board below as the sign's, nail heads
    where the chains take it; and two iron chains between, a pixel wide, their links in turn lit and dark - drawn over
    the ring, which would have made them heavy black bars."""
    img = itemkit.sprite(itemkit.check([
        "................",
        ".rrrrrrrrrrrrrr.",
        ".qqqqqqqqqqqqqp.",
        "................",
        "................",
        "................",
        "................",
        "..566666666665..",
        "..5m44443444n3..",
        "..544344444433..",
        "..444444434442..",
        "..434444444342..",
        "..333333333332..",
        "................",
        "................",
        "................",
    ]), ITEM_PALETTE)
    for x in (4, 11):
        for y in range(3, 7):
            colour = dw.ITEM_IRON["k" if y % 2 else "K"]
            img.putpixel((x, y), colour + (255,))
    return img


# The boats' items: vanilla's birch boats, multiplied by Dawnwood's planks over birch's (their mean colours: our planks'
# worked out here, so the tint follows them; birch's (192, 175, 121) measured once off vanilla's birch_planks), so the
# boat is the colour a boat of Dawnwood planks would be. (Our own drawings of a boat, side on and from above, read as a
# mortar and pestle or a tub; the user asked for vanilla's in Dawnwood's colour.)
BIRCH_MEAN = (192, 175, 121)
PLANKS_MEAN = tuple(round(sum(PLANKS.getpixel((x, y))[i] for y in range(16) for x in range(16)) / 256) for i in range(3))
BOAT_TINT = tuple(min(255, round(255 * d / b)) for d, b in zip(PLANKS_MEAN, BIRCH_MEAN))  # written as one RGB number: a list would be read as 0-1 floats


def write_boat_items():
    for name, vanilla in (("dawnwood_boat", "birch_boat"), ("dawnwood_chest_boat", "birch_chest_boat")):
        for path, data in (
                (ROOT / f"src/main/resources/assets/nookworks/models/item/{name}.json",
                 {"parent": "minecraft:item/generated", "textures": {"layer0": f"minecraft:item/{vanilla}"}}),
                (ROOT / f"src/main/resources/assets/nookworks/items/{name}.json",
                 {"model": {"type": "minecraft:model", "model": f"nookworks:item/{name}",
                            "tints": [{"type": "minecraft:constant", "value": BOAT_TINT[0] << 16 | BOAT_TINT[1] << 8 | BOAT_TINT[2]}]}})):
            path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    out = {
        "block/dawnwood_sign.png": sign(),
        "block/dawnwood_hanging_sign.png": hanging_sign(),
        "block/dawnwood_shelf.png": shelf(),
        "gui/signs/dawnwood.png": gui_sign(),
        "gui/hanging_signs/dawnwood.png": gui_hanging_sign(),
        "entity/boat/dawnwood.png": boat(False),
        "entity/chest_boat/dawnwood.png": boat(True),
        "item/dawnwood_sign.png": sign_item(),
        "item/dawnwood_hanging_sign.png": hanging_sign_item(),
    }
    for rel, img in out.items():
        path = TEX / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
    write_boat_items()
    print(f"wrote {len(out)} textures and the boats' items")


if __name__ == "__main__":
    main()
