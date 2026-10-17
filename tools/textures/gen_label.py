"""Labels: their textures and item model. Original pixel art. Deterministic.

A label is a small card of paper with an item's picture printed on it, stuck on the side of a chest or a barrel.
The picture is not in these textures: the game draws the item's own icon over the card, in the world
(client/render/LabelRenderer) and on the label item wherever it is drawn (client/render/LabelIconRenderer, a special
model the item definition lays over the card). What is drawn here is the card around it.

- entity/label.png  the card in the world: its face, 8x6 at the top left (corners cut, a ring a shade darker
                    round the edge so the picture sits in a frame), then one row for the top and bottom edges and one
                    column for the sides - the paper's thickness, a shade darker again
- item/label.png    the card in the inventory: 12x10 with a dark outline, the top right corner turned down; the
                    picture goes at half size on the 8x8 in its middle
- models/item/label.json, items/label.json

Run from the repo root:  python tools/textures/gen_label.py
"""
import json
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

PAPER = [(198, 180, 146), (220, 204, 170), (236, 226, 198), (246, 240, 220)]  # edge, ring, body, light
OUTLINE = (88, 76, 62)
FOLD = [(186, 174, 150), (214, 204, 180)]  # the back of the turned corner, and its crease


def px(img, x, y, c):
    img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def entity():
    img = Image.new("RGBA", (16, 16), CLEAR)
    w, h = 8, 6
    for y in range(h):
        for x in range(w):
            ring = x in (0, w - 1) or y in (0, h - 1)
            px(img, x, y, PAPER[1] if ring else PAPER[2])
    for x in range(1, w - 1):  # light falls on the top of the card
        px(img, x, 1, PAPER[3])
    for x, y in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        px(img, x, y, CLEAR)
    for x in range(w):  # the edges, seen side on
        px(img, x, 6, PAPER[0])
    for y in range(h):
        px(img, 8, y, PAPER[0])
    return texkit.finish(img, 11, keep=(PAPER[0],), clusters=0.3, grain=0.04, size=5, reach=1)


def item():
    img = Image.new("RGBA", (16, 16), CLEAR)
    x0, y0, x1, y1 = 2, 3, 13, 12  # the card, outline included
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            px(img, x, y, OUTLINE if edge else PAPER[2])
    for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):  # rounded corners
        px(img, x, y, CLEAR)
    for x in range(x0 + 1, x1):  # light on the top edge, shade along the bottom and the right
        px(img, x, y0 + 1, PAPER[3])
        px(img, x, y1 - 1, PAPER[1])
    for y in range(y0 + 2, y1):
        px(img, x1 - 1, y, PAPER[1])
    # the top right corner turned down over the face: cut on the diagonal, its back lying on the card
    for x, y in ((x1 - 1, y0), (x1, y0 + 1), (x1, y0 + 2)):
        px(img, x, y, CLEAR)
    for x, y in ((x1 - 2, y0), (x1 - 1, y0 + 1), (x1, y0 + 2)):
        px(img, x, y, OUTLINE)
    px(img, x1 - 2, y0 + 1, FOLD[0])
    px(img, x1 - 2, y0 + 2, FOLD[1])
    px(img, x1 - 1, y0 + 2, FOLD[0])
    return texkit.finish(img, 12, keep=(OUTLINE, FOLD[0], FOLD[1]), clusters=0.3, grain=0.05, size=4, reach=1)


def main():
    entity().save(ASSETS / "textures/entity/label.png")
    texkit.retone(item(), OUTLINE).save(ASSETS / "textures/item/label.png")  # the card's edge lighter on its lit sides
    (ASSETS / "models/item/label.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/label"}}, indent=2) + "\n")
    # the plain card, and over it what the label prints (client/render/LabelIconRenderer): a picture, or letters -
    # on a screen kept to whole pixels, anywhere else shrunk to fit
    def printed(gui):
        model = {"type": "nookworks:label", "gui": True} if gui else {"type": "nookworks:label"}
        return {"type": "minecraft:special", "base": "nookworks:item/label", "model": model}
    (ASSETS / "items/label.json").write_text(json.dumps(
        {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": "nookworks:item/label"},
            {"type": "minecraft:select", "property": "minecraft:display_context",
             "cases": [{"when": "gui", "model": printed(True)}], "fallback": printed(False)}]}}, indent=2) + "\n")


if __name__ == "__main__":
    main()
