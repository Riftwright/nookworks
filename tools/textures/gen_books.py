"""Books laid down on a block (the user, 2026-10-19), and the Bookend that holds them up. Original pixel art, drawn
here; models and blockstates too.

A book is drawn in two sizes (SIZES): lying in a pile, nine pixels wide, thirteen long and three thick; standing,
leant against a wall or held up by a Bookend, ten by fourteen (the user, comparing four sizes on smooth stone: the
first, seven by eleven, read as a notebook; a standing book wants to be a little bigger than a lying one). A cover
under and over, the pages between (their edges showing on three sides) and the spine on the fourth. PlacedBooksRenderer
and BookendRenderer lay each book where it is. What each is shows on its cover:
- plain: brown leather with a lighter label, as the game's book;
- enchanted: vanilla's enchanted book, its red strap and gold clasp;
- written: oxblood with a gold title band, and a red ribbon out of its pages;
- writable (a book and quill): oxblood, the quill tucked in its pages, its feather out at the side.

The Bookend (the user's photograph of one): a thin L of dark iron, its upright at one side of the block and its foot
under the books, from vanilla's anvil texture by name.

Drawn flat in vanilla's own book colours (leather, gold, grey-white leaves), then grained by texkit as vanilla's hides
are (the user: 表紙背表紙のテクスチャをもう少しバニラのブロックの質感に寄せられないかな).

Run from the repo root:  python tools/textures/gen_books.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)
KINDS = ("plain", "enchanted", "written", "writable")
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
# each book of a pile: turned (degrees about y) and set off (pixels, x and z) from the block's middle
LAYERS = [(0.0, 0.0, 0.0)]  # one book: the renderer turns and sets off each
# by the models' and textures' suffix: (across, along, thick) in pixels; PlacedBooksBlock.TALL and WIDE are the
# standing one's, THICK both's
SIZES = {"": (9, 13, 3), "_tall": (10, 14, 3)}
WIDE, LONG, THICK = SIZES[""]
X0, Z0 = 8 - WIDE / 2, 8 - LONG / 2  # the cover's corner, the book in the block's middle


def size(wide, long, thick):
    """Draws and builds at this size from here on."""
    global WIDE, LONG, THICK, X0, Z0
    WIDE, LONG, THICK = wide, long, thick
    X0, Z0 = 8 - WIDE / 2, 8 - LONG / 2
    assert WIDE <= 10 and LONG <= 16 and THICK - 1 <= 4, "the cover's extras sit from x 11; a sheet is sixteen"


SPINE_Y = 0  # on the side sheet: the spine's rows, then the edges
EDGE_Y = 4

# Leather, gold and pages by vanilla's book colours (the book, written book and shelves' ramps - colours only, never
# its pixels): an olive-brown hide for a book and an enchanted book (vanilla's enchanted book is that brown with a red
# strap and gold; its glint is the purple), oxblood for a written book and a book and quill.
LEATHER = {
    "plain": {"rim": (49, 33, 4), "dark": (68, 51, 16), "base": (101, 75, 23), "lit": (124, 95, 36), "shine": (146, 114, 50)},
    "enchanted": {"rim": (49, 33, 4), "dark": (68, 51, 16), "base": (101, 75, 23), "lit": (124, 95, 36), "shine": (146, 114, 50)},
    "written": {"rim": (57, 16, 3), "dark": (77, 24, 7), "base": (117, 50, 30), "lit": (138, 66, 42), "shine": (158, 84, 56)},
    "writable": {"rim": (57, 16, 3), "dark": (77, 24, 7), "base": (117, 50, 30), "lit": (138, 66, 42), "shine": (158, 84, 56)},
}
GOLD = (214, 160, 20)
GOLD_DARK = (150, 104, 0)
STRAP = [(197, 19, 57), (150, 30, 40)]
PAGES = [(214, 214, 214), (183, 183, 183), (153, 153, 153)]
RIBBON = [(197, 19, 57), (150, 30, 40)]
QUILL = {"tip": (30, 28, 32), "shaft": (124, 112, 96), "vane": (240, 240, 234), "vane_dark": (190, 190, 184)}
ACCENTS = [GOLD, GOLD_DARK] + STRAP + list(QUILL.values())
BAYER = [[0.25, 0.75], [1.0, 0.5]]  # a 2x2 ordered dither's thresholds


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def cover(kind):
    """A kind's two sheets. The cover's: the cover (WIDE x LONG at 0,0) and the kind's extra - the ribbon (1x2 at
    12,0) or the quill's feather (4x1 at 11,3). The side's: the spine (LONG x THICK-1 at 0,0) and the covers' edges
    (WIDE x 1 at 0,EDGE_Y, LONG x 1 at 0,EDGE_Y+1). Drawn flat in vanilla's leather, then grained by texkit as vanilla's hides are."""
    c = LEATHER[kind]
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    # the light from the top left, falling off towards the bottom right: the hide's shades laid as a soft ramp, the
    # steps between them mixed by an ordered dither as vanilla's own soft ramps are (the user: うっすら光に合わせて
    # グラデーション); the board's two raised bands catch the light along their top and shade along their foot
    shades = texkit.ramp(c["base"])  # 0 two steps darker .. 4 two lighter
    for x in range(WIDE):
        for y in range(LONG):
            t = 1.0 - ((x / (WIDE - 1)) * 0.45 + (y / (LONG - 1)) * 0.55)  # 1 at the top left, 0 at the bottom right
            level = 1.0 + t * 2.0  # from a step darker to a step lighter
            low = int(level)
            dither = BAYER[y % 2][x % 2]
            px[x, y] = shades[min(4, low + (1 if level - low > dither else 0))]
    for y in (2, LONG - 3):
        for x in range(2, WIDE - 1):
            px[x, y - 1] = shades[min(4, shades.index(px[x, y - 1][:3]) + 1)] if px[x, y - 1][:3] in shades else px[x, y - 1]
            px[x, y] = shades[max(0, shades.index(px[x, y][:3]) - 1)] if px[x, y][:3] in shades else px[x, y]
    for x in range(WIDE):
        for y in range(LONG):
            if x in (0, WIDE - 1) or y in (0, LONG - 1):
                px[x, y] = c["rim"]
    mx, my = WIDE // 2, LONG // 2
    if kind == "enchanted":
        # vanilla's enchanted book: a red strap across, its gold clasp at the edge
        for x in range(1, WIDE):
            px[x, my] = STRAP[0]
            px[x, my + 1] = STRAP[1]
        px[WIDE - 1, my], px[WIDE - 1, my + 1] = GOLD, GOLD_DARK
    elif kind == "written":
        for x in range(2, WIDE - 1):
            px[x, my - 1] = GOLD
            px[x, my] = GOLD_DARK
    # a fine grain over it, as of paper or cloth over the board: small soft patches, a few single pixels
    img = texkit.shade(img, 31 + KINDS.index(kind), clusters=0.45, grain=0.3, size=2, reach=1, material="plain",
                        keep=ACCENTS + [c["rim"]])
    px = img.load()
    side = Image.new("RGBA", (16, 16), CLEAR)
    sx = side.load()
    for x in range(LONG):  # the spine: rim at the ends, gold bands as on the shelves' books, lit above and dark below
        for y in range(SPINE_Y, SPINE_Y + THICK - 1):
            top = y == SPINE_Y
            if x in (0, LONG - 1):
                sx[x, y] = c["rim"]
            elif x in (2, LONG - 3):
                sx[x, y] = GOLD if top else GOLD_DARK
            else:
                sx[x, y] = c["lit"] if top else c["dark"]
    for x in range(WIDE):
        sx[x, EDGE_Y] = c["rim"] if x % 3 == 0 else c["dark"]
    for x in range(LONG):
        sx[x, EDGE_Y + 1] = c["rim"] if x % 4 == 0 else c["dark"]
    if kind == "written":
        px[12, 0], px[12, 1] = RIBBON
    if kind == "writable":
        for x, colour in enumerate((QUILL["shaft"], QUILL["vane_dark"], QUILL["vane"], QUILL["vane"])):
            px[11 + x, 3] = colour
        px[15, 3] = QUILL["tip"]
    return img, side


def pages():
    """The pages' edges, two pixels high: the side across (rows 0-1) and the side along (rows 3-4), leaves of three
    shades."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    for x in range(WIDE):  # the leaves white, a grey shadow line under the top cover and between them, as vanilla's
        px[x, 0] = PAGES[0] if x % 3 else PAGES[1]
        for y in range(1, THICK - 1):
            px[x, y] = PAGES[1] if x % 2 else PAGES[2]
    for x in range(LONG):
        px[x, 3] = PAGES[0] if x % 4 else PAGES[1]
        for y in range(4, 3 + THICK - 1):
            px[x, y] = PAGES[1] if x % 3 else PAGES[2]
    return img


def book_elements(kind, layer):
    angle, dx, dz = LAYERS[layer]
    y0 = THICK * layer
    x1, z1 = X0 + WIDE, Z0 + LONG
    leaf_top = THICK - 0.5

    def box(frm, to, faces):
        return {"from": [frm[0] + dx, frm[1] + y0, frm[2] + dz], "to": [to[0] + dx, to[1] + y0, to[2] + dz],
                "rotation": {"origin": [8 + dx, y0 + THICK / 2, 8 + dz], "axis": "y", "angle": angle}, "faces": faces}

    edge_across = {"uv": [0, EDGE_Y, WIDE, EDGE_Y + 0.5], "texture": "#side"}
    edge_along = {"uv": [0, EDGE_Y + 1, LONG, EDGE_Y + 1.5], "texture": "#side"}
    art = {"uv": [0, 0, WIDE, LONG], "texture": "#cover"}
    elements = [
        # the lower cover: its underside the cover's art, its edges the leather's; its top shows where the pages are
        # set in from it (the inside of the cover is drawn as the outside)
        box([X0, 0, Z0], [x1, 0.5, z1], {"down": art, "up": art, "north": edge_across, "south": edge_across, "east": edge_along,
                                         "west": edge_along}),
        # the pages, set in a little from the covers on three sides
        box([X0 + 0.5, 0.5, Z0 + 0.25], [x1 - 0.25, leaf_top, z1 - 0.25], {
            "north": {"uv": [0, 0, WIDE - 0.75, THICK - 1], "texture": "#pages"},
            "south": {"uv": [0, 0, WIDE - 0.75, THICK - 1], "texture": "#pages"},
            "east": {"uv": [0, 3, LONG - 0.5, 3 + THICK - 1], "texture": "#pages"}}),
        # the spine, closing the fourth side (its inside shows past the pages' ends, set in from the covers)
        box([X0, 0.5, Z0], [X0 + 0.5, leaf_top, z1], {"west": {"uv": [0, SPINE_Y, LONG, SPINE_Y + THICK - 1], "texture": "#side"},
                                                      "east": {"uv": [0, SPINE_Y, LONG, SPINE_Y + THICK - 1], "texture": "#side"},
                                                      "north": {"uv": [0, SPINE_Y, 0.5, SPINE_Y + THICK - 1], "texture": "#side"},
                                                      "south": {"uv": [0, SPINE_Y, 0.5, SPINE_Y + THICK - 1], "texture": "#side"}}),
        # the upper cover
        box([X0, leaf_top, Z0], [x1, THICK, z1], {"up": art, "down": art, "north": edge_across, "south": edge_across,
                                                  "east": edge_along, "west": edge_along}),
    ]
    middle = THICK / 2
    if kind == "written":
        # a ribbon out of the pages at the foot, lying between the covers
        elements.append(box([7.5, middle - 0.1, z1 - 0.25], [8.5, middle + 0.1, z1 + 1.25], {
            "up": {"uv": [12, 0, 13, 1.5], "texture": "#cover"}, "down": {"uv": [12, 0, 13, 1.5], "texture": "#cover"},
            "south": {"uv": [12, 0, 13, 0.2], "texture": "#cover"},
            # its edges, out past the covers
            "west": {"uv": [12, 0, 13, 0.2], "texture": "#cover"}, "east": {"uv": [12, 0, 13, 0.2], "texture": "#cover"}}))
    if kind == "writable":
        # the quill between the pages, its feather out past the book's side
        elements.append(box([x1 - 1, middle - 0.1, 7.5], [x1 + 3, middle + 0.1, 8.5], {
            "up": {"uv": [11, 3, 15, 4], "texture": "#cover"}, "down": {"uv": [11, 3, 15, 4], "texture": "#cover"},
            "east": {"uv": [15, 3, 16, 3.2], "texture": "#cover"}, "north": {"uv": [11, 3, 15, 3.2], "texture": "#cover"},
            "south": {"uv": [11, 3, 15, 3.2], "texture": "#cover"}}))
    return elements


# The Bookend, its upright to the west (x 0-1) and the books' spines to the south: the upright eight wide and ten
# high, its foot half a pixel thick under the first books (hidden by them once there are three).
ANVIL = "minecraft:block/anvil"


def bookend_elements(mirror):
    def box(frm, to, faces):
        if mirror:  # its upright to the east
            frm, to = [16 - to[0], frm[1], frm[2]], [16 - frm[0], to[1], to[2]]
            faces = {{"east": "west", "west": "east"}.get(side, side): f for side, f in faces.items()}
        return {"from": frm, "to": to, "faces": faces}

    def face(u0, v0, u1, v1, cull=None):
        f = {"uv": [u0, v0, u1, v1], "texture": "#metal"}
        if cull:
            f["cullface"] = cull
        return f
    upright = box([0, 0, 4], [1, 10, 12], {
        "north": face(4, 3, 5, 13), "south": face(4, 3, 5, 13), "east": face(4, 3, 12, 13), "west": face(4, 3, 12, 13),
        "up": face(4, 3, 5, 11)})
    foot = box([1, 0, 4], [8, 0.5, 12], {
        "north": face(4, 3, 11, 3.5), "south": face(4, 3, 11, 3.5), "east": face(4, 3, 12, 3.5), "up": face(4, 3, 11, 11),
        "down": face(4, 3, 11, 11, "down")})
    return [upright, foot]


def main():
    textures = ASSETS / "textures/block"
    textures.mkdir(parents=True, exist_ok=True)
    models = ASSETS / "models/block"
    for suffix, dims in SIZES.items():
        size(*dims)
        for kind in KINDS:
            front, side = cover(kind)
            front.save(textures / f"placed_book_{kind}{suffix}.png")
            side.save(textures / f"placed_book_{kind}{suffix}_side.png")
        pages().save(textures / f"placed_book_pages{suffix}.png")
        for kind in KINDS:
            # one book, flat, in the block's middle: the renderers lay each where it is
            write(models / f"placed_book_{kind}{suffix}.json", {
                "ambientocclusion": False,
                "textures": {"cover": f"nookworks:block/placed_book_{kind}{suffix}",
                             "side": f"nookworks:block/placed_book_{kind}{suffix}_side",
                             "pages": f"nookworks:block/placed_book_pages{suffix}",
                             "particle": f"nookworks:block/placed_book_{kind}{suffix}"},
                "elements": book_elements(kind, 0)})
    write(models / "placed_books_drawn.json", {"textures": {"particle": "nookworks:block/placed_book_plain"}})
    write(ASSETS / "blockstates/placed_books.json", {"variants": {"": {"model": "nookworks:block/placed_books_drawn"}}})
    for hinge, mirror in (("left", False), ("right", True)):
        write(models / f"bookend_{hinge}.json", {"parent": "minecraft:block/block",
                                                 "textures": {"metal": ANVIL, "particle": ANVIL},
                                                 "elements": bookend_elements(mirror)})
    # the spines face FACING; the model's face south, and a blockstate's y turns it clockwise (south to west at 90)
    turn = {"south": 0, "west": 90, "north": 180, "east": 270}
    write(ASSETS / "blockstates/bookend.json", {"variants": {
        f"facing={facing},hinge={hinge}": {"model": f"nookworks:block/bookend_{hinge}", **({"y": y} if y else {})}
        for facing, y in turn.items() for hinge in ("left", "right")}})
    # a Row of Books draws nothing itself, as the placed books: BookendRenderer stands each
    write(ASSETS / "blockstates/book_row.json", {"variants": {"": {"model": "nookworks:block/placed_books_drawn"}}})
    # held turned and tipped so its L shows, the upright and the foot (seen square on, it was a sliver at the screen's
    # edge: the user's play, 2026-10-20); the other views as tools/gen_item_displays.py works them out
    held = {"rotation": [15, 240, 0], "translation": [2, 3, 0], "scale": [0.4, 0.4, 0.4]}
    write(ASSETS / "models/item/bookend.json", {"parent": "nookworks:block/bookend_left",
                                                 "display": {"firstperson_righthand": held, "firstperson_lefthand": held}})
    write(ASSETS / "items/bookend.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/bookend"}})
    print("wrote the placed books (two sizes: 8 sheets, the pages, 8 book models), the blockstate, and the Bookend")


if __name__ == "__main__":
    main()
