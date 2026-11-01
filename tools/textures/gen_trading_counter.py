"""Trading Counter: a village shop's counter, a ledger lying open on it and a brass service bell.

A cabinet a pixel shorter than a block under a top board that overhangs its front, built as joiners build one: a frame
of stiles and rails holding raised panels, a brass foot rail along the customer's side, and on the villager's side open
shelves with the shop's stock on them. On the top, the ledger (green leather, ruled pages, a red margin) at the front
left and the bell at the front right, with a few emeralds stacked by the ledger.

The model's north face is the customer's side: the blockstate turns it to face whoever set the counter down, so the
villager stands at its back, by the shelves.

Drawn flat, then finished with texkit. Original work, deterministic.
Run from the repo root:  python tools/textures/gen_trading_counter.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"
NAME = "trading_counter"

FRAME_HI, FRAME, FRAME_LO, FRAME_DK = (150, 104, 64), (122, 82, 48), (98, 64, 37), (68, 44, 25)
PANEL_HI, PANEL, PANEL_LO = (184, 136, 86), (160, 114, 70), (134, 92, 55)
TOP_HI, TOP, TOP_LO, TOP_DK = (128, 82, 48), (104, 64, 37), (84, 50, 29), (60, 35, 20)
HOLLOW, HOLLOW_DK = (46, 31, 21), (30, 20, 14)
BRASS_HI, BRASS, BRASS_LO = (238, 204, 116), (200, 156, 68), (146, 106, 42)
LEATHER_HI, LEATHER, LEATHER_LO = (76, 120, 80), (52, 90, 60), (36, 66, 44)
GOLD = (222, 186, 96)
PAPER_HI, PAPER, PAPER_LO = (244, 236, 210), (230, 219, 188), (206, 192, 158)
INK, MARGIN = (92, 98, 128), (186, 76, 64)
# the shop's stock on the back shelves
GLASS, GLASS_HI = (120, 160, 190), (196, 224, 236)
POTION = (176, 60, 86)
EMERALD_HI, EMERALD, EMERALD_LO = (130, 236, 156), (40, 190, 92), (20, 120, 56)
BREAD, BREAD_LO = (198, 142, 70), (150, 98, 44)
CLOTH = (182, 62, 54)


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c) + (255,))


def rect(img, x0, y0, x1, y1, c):
    """x0..x1, y0..y1, both inclusive."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def raised_panel(img, x0, y0, x1, y1):
    """A raised panel sunk in the frame: its bevel lit on the top and left, shaded on the bottom and right."""
    rect(img, x0, y0, x1, y1, PANEL)
    rect(img, x0, y0, x1, y0, FRAME_DK)       # the shadow the rail above casts into the groove
    rect(img, x0, y0, x0, y1, FRAME_LO)
    rect(img, x0 + 1, y0 + 1, x1, y0 + 1, PANEL_HI)
    rect(img, x0 + 1, y0 + 1, x0 + 1, y1, PANEL_HI)
    rect(img, x0 + 1, y1, x1, y1, PANEL_LO)
    rect(img, x1, y0 + 1, x1, y1, PANEL_LO)
    for x in range(x0 + 3, x1 - 1, 2):        # the grain, running up the panel
        for y in range(y0 + 3, y1 - 1):
            if (x * 7 + y * 3) % 5 == 0:
                put(img, x, y, PANEL_LO)


def frame(img, top=3):
    """The frame under the top board (rows top..15): rails along the top and bottom, stiles at the sides, a kick at the foot."""
    rect(img, 0, top, 15, 15, FRAME)
    rect(img, 0, top, 15, top, FRAME_DK)       # in the top board's shadow
    rect(img, 0, top + 1, 15, top + 1, FRAME_HI)
    rect(img, 0, 15, 15, 15, FRAME_DK)         # the kick, in shadow
    rect(img, 0, 14, 15, 14, FRAME_LO)
    for y in range(top + 1, 15):
        put(img, 0, y, FRAME_HI)
        put(img, 15, y, FRAME_LO)


def front():
    """The customer's side: the frame holding two raised panels side by side, a stile between them."""
    img = blank()
    frame(img)
    raised_panel(img, 2, 5, 6, 12)
    raised_panel(img, 9, 5, 13, 12)
    rect(img, 7, 5, 8, 12, FRAME)
    put(img, 7, 5, FRAME_HI)
    put(img, 8, 12, FRAME_LO)
    return img


def side():
    """An end: one wide raised panel."""
    img = blank()
    frame(img)
    raised_panel(img, 2, 5, 13, 12)
    return img


def back():
    """
    The villager's side: two open shelves in the frame, dark inside, and the shop's stock on them - a bottle and a
    potion, a loaf and a folded cloth on the upper shelf, emeralds and a jar on the lower.
    """
    img = blank()
    frame(img)
    for y0, y1 in ((5, 8), (10, 13)):
        rect(img, 2, y0, 13, y1, HOLLOW)
        rect(img, 2, y0, 13, y0, HOLLOW_DK)   # the shelf above shades the top of the hollow
        rect(img, 2, y1 + 1, 13, y1 + 1, FRAME_HI)  # the shelf's lit front edge
    # upper shelf
    rect(img, 3, 7, 4, 8, GLASS)
    put(img, 3, 6, GLASS)
    put(img, 3, 7, GLASS_HI)
    rect(img, 6, 7, 7, 8, POTION)
    put(img, 6, 6, GLASS)
    rect(img, 9, 7, 11, 8, BREAD)
    rect(img, 9, 8, 11, 8, BREAD_LO)
    rect(img, 12, 7, 13, 8, CLOTH)
    # lower shelf
    rect(img, 3, 12, 5, 13, EMERALD)
    put(img, 4, 11, EMERALD)
    put(img, 3, 12, EMERALD_HI)
    put(img, 5, 13, EMERALD_LO)
    rect(img, 8, 11, 10, 13, GLASS)
    rect(img, 8, 11, 10, 11, CLOTH)            # the jar's cloth lid
    put(img, 9, 12, GLASS_HI)
    put(img, 12, 13, EMERALD)
    put(img, 13, 13, EMERALD_LO)
    return img


def top():
    """The top: three planed boards running left to right, their seams dark, a lit edge along each."""
    img = blank()
    rect(img, 0, 0, 15, 15, TOP)
    for y in (0, 5, 10):                       # each board's lit edge
        rect(img, 0, y, 15, y, TOP_HI)
    for y in (4, 9, 15):                       # the seams
        rect(img, 0, y, 15, y, TOP_DK)
    for x, y in ((2, 2), (3, 2), (9, 1), (10, 1), (13, 3), (5, 7), (6, 7), (11, 6), (1, 12), (2, 12), (8, 13), (14, 11)):
        put(img, x, y, TOP_LO)                 # long grain
    put(img, 6, 2, TOP_DK)                     # a knot
    return img


def bottom():
    img = blank()
    rect(img, 0, 0, 15, 15, FRAME_LO)
    rect(img, 0, 0, 15, 0, FRAME_DK)
    rect(img, 0, 15, 15, 15, FRAME_DK)
    return img


def ledger():
    """
    One sheet holding both parts of the ledger, each at the size it is shown: columns 0-6 of rows 0-4 the open pages
    (two pages either side of the gutter, a red margin rule, entries inked in), rows 0-4 of column 7 the pages' edges,
    and rows 10-15 of columns 0-7 its green leather with a gold tooled edge.
    """
    img = blank()
    rect(img, 0, 0, 6, 4, PAPER)
    rect(img, 0, 0, 6, 0, PAPER_HI)
    rect(img, 3, 0, 3, 4, PAPER_LO)            # the gutter
    rect(img, 0, 0, 0, 4, MARGIN)              # the margin rule on the left page
    for x0, x1, y in ((1, 2, 1), (1, 1, 3), (4, 6, 1), (4, 5, 3)):
        rect(img, x0, y, x1, y, INK)           # entries
    rect(img, 7, 0, 7, 4, PAPER_LO)            # the pages' edges
    rect(img, 0, 10, 7, 15, LEATHER)
    rect(img, 0, 10, 7, 10, LEATHER_HI)
    rect(img, 0, 15, 7, 15, LEATHER_LO)
    rect(img, 0, 11, 7, 11, GOLD)              # the tooled edge
    rect(img, 0, 14, 7, 14, GOLD)
    return img


def brass():
    img = blank()
    rect(img, 0, 0, 15, 15, BRASS)
    rect(img, 0, 0, 15, 3, BRASS_HI)
    rect(img, 0, 12, 15, 15, BRASS_LO)
    return img


# ---------------------------------------------------------------------------------------------------------- model

def face(tex, uv=None, cull=None, rotation=None):
    out = {"texture": tex}
    if uv:
        out["uv"] = uv
    if cull:
        out["cullface"] = cull
    if rotation:
        out["rotation"] = rotation
    return out


def box(frm, to, faces, rotation=None):
    el = {"from": frm, "to": to, "faces": faces}
    if rotation:
        el["rotation"] = rotation
    return el


def model():
    els = []
    # the cabinet, its front set back a pixel under the top board
    els.append(box([0, 0, 1], [16, 13, 16], {
        "north": face("#front"),
        "south": face("#back", cull="south"),
        "east": face("#side", cull="east"),
        "west": face("#side", cull="west"),
        "down": face("#bottom", cull="down"),
    }))
    # the top board, overhanging the front; its edges are the boards' ends
    edge = [0, 1, 16, 3]
    els.append(box([0, 13, 0], [16, 15, 16], {
        "north": face("#top", uv=edge),
        "south": face("#top", uv=edge, cull="south"),
        "east": face("#top", uv=edge, cull="east"),
        "west": face("#top", uv=edge, cull="west"),
        "up": face("#top"),
        "down": face("#bottom"),
    }))
    # the brass foot rail along the customer's side, on three posts. The rail stands a quarter pixel off the cabinet's
    # front and the posts reach only half under it, so the rail's back and the posts' tops and bottoms show in the gaps
    els.append(box([1, 2, 0], [15, 3, 0.75], {s: face("#brass", uv=[1, 6, 15, 7])
                                              for s in ("north", "south", "up", "down", "east", "west")}))
    for x in (1.5, 7.5, 13.5):
        els.append(box([x, 1, 0.5], [x + 1, 2, 1], {s: face("#brass", uv=[0, 8, 1, 9])
                                                    for s in ("north", "east", "west", "up", "down")}))
    # the ledger, lying open at the front left and turned a little toward the customer: leather, then its pages
    turn = {"angle": 22.5, "axis": "y", "origin": [5.5, 15, 6]}
    els.append(box([1.5, 15, 3], [9.5, 15.5, 9], {
        "up": face("#ledger", uv=[0, 10, 8, 16]),
        "north": face("#ledger", uv=[0, 15, 8, 16]),
        "south": face("#ledger", uv=[0, 15, 8, 16]),
        "east": face("#ledger", uv=[0, 15, 6, 16]),
        "west": face("#ledger", uv=[0, 15, 6, 16]),
    }, turn))
    els.append(box([2, 15.5, 3.5], [9, 16, 8.5], {
        "up": face("#ledger", uv=[0, 0, 7, 5]),
        "north": face("#ledger", uv=[7, 0, 8, 1]),
        "south": face("#ledger", uv=[7, 0, 8, 1]),
        "east": face("#ledger", uv=[7, 0, 8, 5]),
        "west": face("#ledger", uv=[7, 0, 8, 5]),
    }, turn))
    # a few emeralds stacked behind the ledger
    gem = {s: face("#gem") for s in ("north", "south", "east", "west", "up")}  # a face names a texture by its #slot
    els.append(box([9, 15, 9.5], [10.5, 15.5, 11], gem))
    els.append(box([9.5, 15.5, 10], [10.5, 16, 11], gem))
    # the bell at the front right: its base, its dome in two steps, and the plunger
    els.append(box([10.5, 15, 3], [14.5, 15.5, 7], {s: face("#brass", uv=[0, 12, 4, 16]) for s in ("north", "south", "east", "west", "up")}))
    els.append(box([11, 15.5, 3.5], [14, 16.5, 6.5], {s: face("#brass", uv=[0, 4, 3, 5]) for s in ("north", "south", "east", "west")}
                   | {"up": face("#brass", uv=[0, 4, 3, 7])}))
    els.append(box([11.75, 16.5, 4.25], [13.25, 17, 5.75], {s: face("#brass", uv=[0, 0, 1.5, 0.5]) for s in ("north", "south", "east", "west")}
                   | {"up": face("#brass", uv=[0, 0, 1.5, 1.5])}))
    els.append(box([12.25, 17, 4.75], [12.75, 17.75, 5.25], {s: face("#brass", uv=[0, 0, 1, 1]) for s in ("north", "south", "east", "west", "up")}))
    textures = {
        "particle": f"nookworks:block/{NAME}_side",
        "front": f"nookworks:block/{NAME}_front",
        "side": f"nookworks:block/{NAME}_side",
        "back": f"nookworks:block/{NAME}_back",
        "top": f"nookworks:block/{NAME}_top",
        "bottom": f"nookworks:block/{NAME}_bottom",
        "ledger": f"nookworks:block/{NAME}_ledger",
        "brass": f"nookworks:block/{NAME}_brass",
        "gem": "minecraft:block/emerald_block",
    }
    return {"parent": "minecraft:block/block", "textures": textures, "elements": els}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf8"))


def save(img, name, seed, preset, keep=()):
    texkit.finish(img, seed, keep=keep, **preset).save(BLOCK / f"{NAME}_{name}.png")


def main():
    save(front(), "front", 610, texkit.SMOOTH)
    save(side(), "side", 611, texkit.SMOOTH)
    save(back(), "back", 612, texkit.SMOOTH, keep=(GLASS_HI, EMERALD_HI, POTION, CLOTH))
    save(top(), "top", 613, texkit.SMOOTH, keep=(TOP_DK,))
    save(bottom(), "bottom", 614, texkit.SMOOTH)
    save(ledger(), "ledger", 615, texkit.SMOOTH, keep=(INK, MARGIN, GOLD))
    save(brass(), "brass", 616, texkit.SMOOTH)
    write_json(ASSETS / f"models/block/{NAME}.json", model())
    variants = {f"facing={f}": {"model": f"nookworks:block/{NAME}", **({"y": r} if r else {})}
                for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
    write_json(ASSETS / f"blockstates/{NAME}.json", {"variants": variants})
    write_json(ASSETS / f"items/{NAME}.json", {"model": {"type": "minecraft:model", "model": f"nookworks:block/{NAME}"}})


if __name__ == "__main__":
    main()
