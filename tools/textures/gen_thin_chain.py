"""Thin Chain: the links it is drawn with strung between posts, and its icon. All original pixel art, drawn here - not
vanilla's chain (no vanilla pixels, and not its look: ours is thinner, links of half-pixel wire).

- textures/entity/thin_chain.png   one link, 8 x 8 at two texels a pixel (client/render/ChainDrawing draws a link 3.5 x 2
  pixels): face on in the top four rows - a ring seven texels long and four across, its hole clear - and edge on in
  row 5, a bar as thick as the wire. Every link of a chain is this one texture, flat and edge on by turns.
- textures/item/thin_chain.png     the icon: a length of fine chain hanging as it is strung, sagging from the top left
  to a lower right, its small links face on and edge on by turns, finished with texkit.icon (grain and the ring round
  it), the links' holes kept clear. (It was two big links lying corner to corner, which did not look thin.)
- models/item/thin_chain.json, items/thin_chain.json

The link is hand-shaded, not put through texkit.shade: its wire is a texel thick, every pixel a thin line, which
texkit leaves without grain anyway - and its patches would only make one end of the link darker than the other, the
same on every link of every chain, a stripe along it rather than grain. The shades are set by hand instead: the long
sides lit along their middles and darker into the ends, where the wire turns away.

Run from the repo root:  python tools/textures/gen_thin_chain.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)
# iron greys, a little cool: highlight, lit, body, shade, deep
HI, LIT, BODY, SHADE, DEEP = (236, 236, 240), (198, 198, 205), (152, 152, 161), (108, 108, 119), (74, 74, 85)


def px(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), tuple(c) + (255,))


# --------------------------------------------------------------------------------------------------------- the link
def link():
    """The link face on (rows 0-3) and edge on (row 5)."""
    img = Image.new("RGBA", (8, 8), CLEAR)
    # face on: a rounded ring, its corners clear
    top = [None, LIT, HI, LIT, LIT, BODY, None]
    bottom = [None, BODY, LIT, BODY, BODY, SHADE, None]
    for x, c in enumerate(top):
        if c:
            px(img, x, 0, c)
    for x, c in enumerate(bottom):
        if c:
            px(img, x, 3, c)
    for y in (1, 2):
        px(img, 0, y, BODY if y == 1 else SHADE)  # the ends, where the wire turns away
        px(img, 6, y, SHADE if y == 1 else DEEP)
    # edge on: both sides of the link one behind the other, the ends turning away
    for x, c in enumerate([SHADE, BODY, LIT, HI, LIT, BODY, SHADE]):
        px(img, x, 5, c)
    return img


# --------------------------------------------------------------------------------------------------------- the icon
# The links, in icon pixels: a link face on is a ring round a stadium A long and B across (halves), of wire T; one edge
# on, a bar of the wire's thickness. They hang from the top left to the top right, sagging between, PITCH apart.
A, B, T = 1.5, 1.05, 0.55
PITCH = 2.4
LEFT, RIGHT, SAG = (1.0, 2.5), (15.0, 6.5), 5.5


def _stadium(u, v, a, b):
    """Whether (u, v) is inside a stadium a long and b across (halves), its long way along u."""
    return math.hypot(max(0.0, abs(u) - (a - b)), v) <= b


def _hanging():
    """(x, y, angle along the chain) for each link: a chain hanging between two posts, the right one lower."""
    pts = []
    for i in range(201):
        t = i / 200
        pts.append((LEFT[0] + (RIGHT[0] - LEFT[0]) * t, LEFT[1] + (RIGHT[1] - LEFT[1]) * t + SAG * 4 * t * (1 - t)))
    links, left = [], 0.0
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        seg = math.hypot(bx - ax, by - ay)
        angle = math.atan2(by - ay, bx - ax)
        d = left
        while d <= seg:
            links.append((ax + (bx - ax) * d / seg, ay + (by - ay) * d / seg, angle))
            d += PITCH
        left = d - seg
    return links


def icon():
    """
    A length of chain hanging as it is strung, sagging from the top left to the right (the user, 2026-10-10: the icon of
    two big links lying corner to corner did not look thin, and was like another mod's big chain). Small links, face on
    and edge on by turns, each drawn as a shape and sampled at sixteen points a pixel (a pixel is a link's where seven
    fall in it); an edge-on link lies over the rings it threads. A ring is lit on its side towards the top left and in
    shade on the other; the links edge on between them are in shade, so each ring stands apart.
    """
    img = Image.new("RGBA", (16, 16), CLEAR)
    cover = {}
    for k, (cx, cy, angle) in enumerate(_hanging()):
        face = k % 2 == 0
        ux, uy = math.cos(angle), math.sin(angle)
        vx, vy = -uy, ux
        for y in range(16):
            for x in range(16):
                hits, us, vs = 0, 0.0, 0.0
                for sy in (0.125, 0.375, 0.625, 0.875):
                    for sx in (0.125, 0.375, 0.625, 0.875):
                        dx, dy = x + sx - cx, y + sy - cy
                        u, v = dx * ux + dy * uy, dx * vx + dy * vy
                        inside = (_stadium(u, v, A, B) and not _stadium(u, v, A - T, B - T)) if face                             else _stadium(u, v, A * 0.9, 0.42)
                        if inside:
                            hits, us, vs = hits + 1, us + u, vs + v
                if hits >= 7:
                    prev = cover.get((x, y))
                    if prev is None or not face or prev[0]:
                        cover[(x, y)] = (face, vs / hits, angle)
    for (x, y), (face, v, angle) in cover.items():
        if face:
            towards = -(-math.sin(angle) * v) - math.cos(angle) * v  # across the ring, how far towards the top left
            c = HI if towards > 0.5 else LIT if towards > -0.2 else SHADE
        else:
            c = SHADE
        px(img, x, y, c)
    holes = [(x, y) for y in range(16) for x in range(16) if img.getpixel((x, y))[3] == 0 and (x, y) not in _outside(img)]
    done = texkit.icon(img, 1471, material="metal")
    for hole in holes:
        done.putpixel(hole, CLEAR)
    return done


def _outside(img):
    """The clear pixels reached from the edge of the picture: the rest are the links' holes."""
    w, h = img.size
    src = img.load()
    seen = set()
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    while stack:
        x, y = stack.pop()
        if (x, y) in seen or not (0 <= x < w and 0 <= y < h) or src[x, y][3]:
            continue
        seen.add((x, y))
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return seen


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    tex = ASSETS / "textures"
    (tex / "entity").mkdir(parents=True, exist_ok=True)
    link().save(tex / "entity/thin_chain.png")
    icon().save(tex / "item/thin_chain.png")
    write_json(ASSETS / "models/item/thin_chain.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "nookworks:item/thin_chain"}})
    write_json(ASSETS / "items/thin_chain.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/thin_chain"}})
    print("wrote the chain's link and icon")


if __name__ == "__main__":
    main()
