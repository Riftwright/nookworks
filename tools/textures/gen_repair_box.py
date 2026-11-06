"""Chest textures for the Repair Box: one for a single box, and a left and a right half for a double.

The box is drawn on its own model (RepairBoxModel): shaped as the vanilla chest is, but hollow - a floor and four walls
round a well the gear lies in - with a carrying handle standing on the lid. The sheets are 128 by 64, laid out as that
model's boxes are: the lid and latch where vanilla's chest has them, the floor below them, the four walls of the well
and the handle on the right-hand half. (The liquid experience over the well's floor, at a height by how full the box
is, is the fluid's own still texture: RepairBoxRenderer.)

What goes on them is ours: a tool chest, chic rather than a machine (the user, 2026-10-10: "シックでかっこいい", no need
to keep to the machines' black). Deep navy enamel over steel, worn a little, with aged brass fittings: big brackets on
every corner, a lip where the lid closes, hinges at the back, the latch, a leather handle on brass mounts. Inside, the
walls darken going down to a dark floor; the lid's underside is a lined panel in its frame; the seam is dark.

History: first a green sight glass over a brass anvil (read as a face); then, 2026-10-09, the machines' near-black
steel in hard 1-pixel stripes with a black frame, white corner studs and a neon seam (the user, 2026-10-10: too much
like pixel art, a poor feel). Now drawn flat in a few colours with soft, irregular shading, and finished with texkit
as every texture is (texkit.shade since 2026-11-05: the brass metal, the leather plain). The same day: a hammer and wrench crossed on the front went (it did not read), and the
experience's light at the seam (it made the box look as if it did not shut); of five decorations tried - none, straps
round the box, a fine frame on each face, a nameplate, big corner brackets - the user took the brackets.

A double box's two halves leave out the fittings and the edge shading where they meet, so a pair reads as one box
with one lid, and one handle runs across both lids.

- entity/chest/repair_box.png        single
- entity/chest/repair_box_left.png   left half of a double
- entity/chest/repair_box_right.png  right half
- block/repair_box.png               what breaking it scatters: the enamel, a brass corner (it threw the machines'
                                     black steel)

Run from the repo root:  python tools/textures/gen_repair_box.py
"""
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/textures/entity/chest"

# navy enamel, lightest to darkest: the edge's light, the face's own five close shades, the edge's shadow. The shades
# lie close together on purpose - texkit's step on a colour this dark is about a third brighter, which on enamel reads
# as camouflage - so the enamel is shaded here, softly, and texkit leaves it alone.
ENAMEL_HI, ENAMEL_DK = (80, 100, 126), (31, 40, 56)
SHADES = [(63, 81, 106), (59, 76, 100), (55, 71, 94), (51, 66, 88), (47, 61, 82)]
ENAMEL, ENAMEL_LO = SHADES[2], SHADES[4]
SEAM = (22, 27, 36)
# aged brass: warm, a little dull, never the bright yellow of a gold block
BRASS_HI, BRASS, BRASS_LO, BRASS_DK = (214, 186, 122), (176, 144, 86), (132, 102, 58), (90, 68, 40)
LEATHER_HI, LEATHER, LEATHER_LO = (110, 74, 50), (82, 54, 37), (56, 36, 25)
# the lid's lining, a dark felt: close shades, shaded softly here as the enamel is
LINING_SHADES = [(42, 52, 68), (39, 48, 63), (36, 45, 59)]
LINING_LO = (31, 39, 52)
# The well inside: its walls' inner faces, lighter just under the rim and darker going down; its floor, dark.
INSIDE = [(40, 51, 67), (35, 45, 60), (31, 40, 54), (27, 35, 47)]
FLOOR = [(24, 31, 38), (27, 35, 43)]
# The handle stands on the lid in the model: the grip's faces at u 64 v 0, the mounts' at u 84.
GRIP_U, GRIP_V, MOUNT_U = 64, 0, 84
FRONT = "south"  # the sheet region the chest model wears on its front, where the lock is
# enamel is smooth: soft patches a step off, hardly a single pixel out of place
FINISH = dict(texkit.SMOOTH, clusters=0.9, grain=0.05, size=4,
              material={**{c: "plain" for c in (LEATHER_HI, LEATHER, LEATHER_LO)}, None: "metal"})


def px(img, x, y, c):
    img.putpixel((x, y), c + (255,))


def fill(img, region, c):
    x, y, w, h = region
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px(img, xx, yy, c)


def noise(x, y, salt):
    """A steady hash noise in [0, 1): the same pixel always gets the same value."""
    n = math.sin(x * 12.9898 + y * 78.233 + salt * 37.719) * 43758.5453
    return n - math.floor(n)


def smooth(x, y, salt, cell=4, period=None):
    """Value noise in [0, 1) a few pixels across: the hash noise on a coarse grid, blended between its points; with
    {@code period}, repeating every that many pixels each way, for a tile."""
    gx, gy = x / cell, y / cell
    x0, y0 = math.floor(gx), math.floor(gy)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    n = period // cell if period else None

    def at(i, j):
        return noise(i % n, j % n, salt) if n else noise(i, j, salt)

    a, b, c, d = at(x0, y0), at(x0 + 1, y0), at(x0, y0 + 1), at(x0 + 1, y0 + 1)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy


def enamel(img, x0, y0, w, h, salt, lit_top=True):
    """Enamel over the face in its close shades: lighter towards the top, darker towards the foot, in soft patches -
    no bands, no speckle; the light along the top edge is the edge's own."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = ((y - y0 + 0.5) / h if lit_top else 0.45) * 0.75 + smooth(x, y, salt) * 0.55 - 0.1
            px(img, x, y, SHADES[max(0, min(4, int(v * 5)))])


def bevel(img, x0, y0, w, h, left=True, right=True):
    """The face's own edges a shade off, the lit one lighter, in place of an outline."""
    for y in range(y0, y0 + h):
        if left:
            shade = SHADES.index(img.getpixel((x0, y))[:3]) if img.getpixel((x0, y))[:3] in SHADES else 2
            px(img, x0, y, SHADES[max(0, shade - 2)])
        if right:
            px(img, x0 + w - 1, y, ENAMEL_DK)


def seam(img, x0, y, w):
    """The seam where the lid closes on the box: dark, all along. (It showed the liquid experience's light once, which
    made a closed box look as if it did not shut - the user, 2026-10-10.)"""
    for x in range(x0, x0 + w):
        px(img, x, y, SEAM)


def bracket(img, cx, cy, dx, dy, arm):
    """A corner bracket: an L {@code arm} pixels each way from the corner (cx, cy) inwards (dx, dy), filled in by one
    pixel diagonally, a rivet at its heart."""
    for k in range(arm):
        px(img, cx + dx * k, cy, BRASS)
        px(img, cx, cy + dy * k, BRASS)
    px(img, cx, cy, BRASS_HI if dx > 0 and dy > 0 else BRASS)
    px(img, cx + dx, cy + dy, BRASS_HI)


def handle_faces(img, length):
    """The faces of the grip ({@code length} long, 1 high, 2 deep) and of a mount (1 by 1 by 2)."""
    for name, region in box_regions(GRIP_U, GRIP_V, length, 1, 2).items():
        fill(img, region, LEATHER_HI if name == "top_b" else LEATHER_LO if name == "top_a" else LEATHER)
    for name, region in box_regions(MOUNT_U, GRIP_V, 1, 1, 2).items():
        fill(img, region, BRASS_HI if name == "top_b" else BRASS_DK if name == "top_a" else BRASS)


def handle_shadow(img, x0, y0, cols, mounts):
    """Where the handle stands on the lid's top: a soft shadow under the grip, the mounts' feet darker."""
    for c in cols:
        for r in (6, 7):
            px(img, x0 + c, y0 + r, ENAMEL_DK if c in mounts else SHADES[4])


def lining(img, x0, y0, w, h, left=True, right=True):
    """The lid's underside: a rim of enamel round a lined panel, a shade darker at its edges."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            dl = x - x0 if left else 99
            dr = x0 + w - 1 - x if right else 99
            edge = min(dl, dr, y - y0, y0 + h - 1 - y)
            if edge == 0:
                c = ENAMEL_LO
            elif edge == 1:
                c = LINING_LO
            else:
                c = LINING_SHADES[min(2, int(smooth(x, y, 13, 3) * 3))]
            px(img, x, y, c)


def inner_wall(img, x0, y0, w, h):
    """A wall's face inside the well, painted the natural way round: lighter under the rim, darker going down."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = (y - y0 + 0.5) / h * 0.85 + (smooth(x, y, 31, 3) - 0.5) * 0.3
            px(img, x, y, INSIDE[max(0, min(3, int(v * 4)))])


def well_floor(img, x0, y0, w, h):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(img, x, y, FLOOR[1 if smooth(x, y, 12, 3) > 0.55 else 0])


def corner_brackets(img, lid, body, edges):
    """Big brass brackets on every corner of the box and of the lid (the user's pick of five trials) - where a pair's
    halves meet there is no corner, and none."""
    for side in ("west", "north", "east", FRONT):
        e = edges(side)
        x, y, w, h = body[side]
        if e["left"]:
            bracket(img, x, y + h - 1, 1, -1, 4)
        if e["right"]:
            bracket(img, x + w - 1, y + h - 1, -1, -1, 4)
        x, y, w, h = lid[side]
        if e["left"]:
            bracket(img, x, y, 1, 1, 3)
        if e["right"]:
            bracket(img, x + w - 1, y, -1, 1, 3)
    for face in ("top_b",):
        e = edges(face)
        x, y, w, h = lid[face]
        for cx, cy, dx, dy, ok in ((x, y, 1, 1, e["left"]), (x + w - 1, y, -1, 1, e["right"]),
                                   (x, y + h - 1, 1, -1, e["left"]), (x + w - 1, y + h - 1, -1, -1, e["right"])):
            if ok:
                bracket(img, cx, cy, dx, dy, 4)
    e = edges("top_a")
    x, y, w, h = body["top_a"]
    for cx, cy, dx, dy, ok in ((x, y, 1, 1, e["left"]), (x + w - 1, y, -1, 1, e["right"]),
                               (x, y + h - 1, 1, -1, e["left"]), (x + w - 1, y + h - 1, -1, -1, e["right"])):
        if ok:
            bracket(img, cx, cy, dx, dy, 3)


def flip(img, region):
    x, y, w, h = region
    img.paste(img.crop((x, y, x + w, y + h)).transpose(Image.Transpose.FLIP_TOP_BOTTOM), (x, y))


def box_regions(u, v, w, h, d):
    """The faces of one model box on the sheet: the two top-view regions (down, up), then west, north, east, south."""
    return {
        "top_a": (u + d, v, w, d), "top_b": (u + d + w, v, w, d),
        "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }


def wall_boxes(width, half):
    """RepairBoxModel's boxes below the lid, as (u, v, w, h, d): the floor, the front and back walls, and the end walls
    a pair's half has (none at its seam: a left half's is its west side, a right half's its east)."""
    out = {"floor": (0, 19, width, 1, 14), "front": (64, 26, width, 9, 1), "back": (96, 26, width, 9, 1)}
    if half != "left":
        out["west"] = (64, 4, 1, 9, 12)
    if half != "right":
        out["east"] = (90, 4, 1, 9, 12)
    return out


def sheet(width, lock_w, half=None):
    """
    {@code half}: None for a single box; "left" or "right" for a half of a double, which leaves out the fittings and
    the edge shading on the edge that meets the other half (on the front, back, lid and floor) and has no side face on
    that edge showing.
    """
    # First the outside is painted as on a solid chest's 64 by 64 sheet - the lid, and the body's faces whole - and
    # then the body's faces are cut up onto the hollow box's floor and walls (hollow(), below).
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    lid = box_regions(0, 0, width, 5, 14)
    body = box_regions(0, 19, width, 10, 14)
    # the edge each face loses where the halves meet: which of its edges that is depends on how the face is laid
    # out on the sheet - the front comes out mirrored, the back and the top and bottom faces the right way round
    # (checked in the game with a mark on each edge)
    meet_front = {"left": "right", "right": "left"}.get(half)
    meet_back = {"left": "left", "right": "right"}.get(half)

    def edges(face):
        if half is None or face in ("west", "east"):
            return dict(left=True, right=True)
        meet = meet_front if face == FRONT else meet_back
        return dict(left=meet != "left", right=meet != "right")

    salt = {None: 0, "left": 100, "right": 200}[half]
    # Lid: enamel, lit along its top edge, a brass lip and then the seam along its bottom.
    x, y, w, h = lid["top_b"]
    enamel(img, x, y, w, h, salt + 1, lit_top=False)
    bevel(img, x, y, w, h, **edges("top_b"))
    lining(img, *lid["top_a"], **edges("top_a"))
    for i, side in enumerate(("west", "north", "east", "south")):
        x, y, w, h = lid[side]
        enamel(img, x, y, w, h, salt + 2 + i)
        bevel(img, x, y, w, h, **edges(side))
        for k in range(x, x + w):
            px(img, k, y, ENAMEL_HI)  # the lid's edge, catching the light
            px(img, k, y + h - 2, BRASS)  # the lip the lid closes with
        seam(img, x, y + h - 1, w)
    # The handle's shadow, as RepairBoxModel stands it on the lid (the lid's x less its own start: 1 for a single box
    # and a right half, 0 for a left half): over x 4-12 on a single box, mounts at 4 and 11; on a pair one handle
    # across both lids, each half's part from the seam - a right half's x 11-16 with its mount at 11, a left half's
    # 0-5 at 4.
    x, y, w, h = lid["top_b"]
    if half is None:
        handle_shadow(img, x, y, range(3, 11), (3, 10))
    elif meet_back == "right":
        handle_shadow(img, x, y, range(10, 15), (10,))
    else:
        handle_shadow(img, x, y, range(0, 5), (4,))
    # Body: enamel, darker under the lid's shadow and at its foot; the bottom.
    x, y, w, h = body["top_a"]
    enamel(img, x, y, w, h, salt + 9, lit_top=False)
    bevel(img, x, y, w, h, **edges("top_a"))
    for i, side in enumerate(("west", "north", "east", "south")):
        x, y, w, h = body[side]
        enamel(img, x, y, w, h, salt + 10 + i)
        bevel(img, x, y, w, h, **edges(side))
        seam(img, x, y, w)  # the same row as the lid's lowest: drawn alike, so neither shows through
        for k in range(x, x + w):
            px(img, k, y + 1, ENAMEL_DK)  # the lid's shadow
            px(img, k, y + h - 1, ENAMEL_DK)  # the foot
    # Hinges at the back, across the seam: a brass leaf on the lid and one on the box.
    for x0 in ((3, width - 5) if half is None else ((3,) if meet_back == "right" else (width - 5,))):
        lx, ly, lw, lh = lid["north"]
        bx, by, bw, bh = body["north"]
        for dx in (0, 1):
            px(img, lx + x0 + dx, ly + lh - 3, BRASS_LO)
            px(img, lx + x0 + dx, ly + lh - 2, BRASS)
            px(img, bx + x0 + dx, by + 1, BRASS_LO)
            px(img, bx + x0 + dx, by + 2, BRASS_DK)
    corner_brackets(img, lid, body, edges)
    img = hollow(img, width, half, body)
    handle_faces(img, 8 if half is None else 5)
    # The chest model is built y-up, so the sheet's side faces are read bottom to top. They are painted the
    # natural way round above and turned over here.
    for part in [lid] + [box_regions(*at) for at in wall_boxes(width, half).values()]:
        for side in ("west", "north", "east", "south"):
            flip(img, part[side])
    img = texkit.shade(img, 4100 + salt, keep=[BRASS_HI, ENAMEL_HI, ENAMEL_DK, SEAM, LINING_LO] + SHADES + LINING_SHADES
                       + INSIDE + FLOOR, **FINISH)
    # Lock: brass, lit on top, drawn after the finish so it stays clean.
    for region in box_regions(0, 0, lock_w, 4, 1).values():
        x, y, w, h = region
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                px(img, xx, yy, BRASS_HI if yy == y + h - 1 else BRASS_LO if yy == y else BRASS)
    return img


def hollow(old, width, half, body):
    """
    The 128 by 64 sheet of the hollow box: the lid as painted, and the solid body's faces cut onto the floor and the
    walls - each side's foot row onto the floor's edge, the rest onto its wall's outer face, an end face's first and
    last columns onto the ends of the back and front walls - then the inside: the walls' inner faces, their rims, the
    well's floor.
    """
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    img.paste(old.crop((0, 0, 64, 19)), (0, 0))  # the lid and the latch
    boxes = {name: box_regions(*at) for name, at in wall_boxes(width, half).items()}
    floor_box, front, back = boxes["floor"], boxes["front"], boxes["back"]

    def cut(src, dst, sx=0, sy=0):
        x, y, w, h = src
        dx, dy, dw, dh = dst
        img.paste(old.crop((x + sx, y + sy, x + sx + dw, y + sy + dh)), (dx, dy))

    cut(body["top_a"], floor_box["top_a"])  # the bottom
    well_floor(img, *floor_box["top_b"])
    for side in ("west", "north", "east", "south"):
        cut(body[side], floor_box[side], sy=9)  # the foot
    cut(body["south"], front["south"])
    cut(body["north"], back["north"])
    # an end face 14 wide: its middle 12 the end wall's outside, its first column the back wall's end, its last the
    # front wall's (the brackets at both ends are alike, so either way round reads the same)
    for side in ("west", "east"):
        if side in boxes:
            cut(body[side], boxes[side][side], sx=1)
            cut(body[side], back[side])
            cut(body[side], front[side], sx=13)
    for name, face in (("front", "north"), ("back", "south"), ("west", "east"), ("east", "west")):
        if name in boxes:
            inner_wall(img, *boxes[name][face])
    for name, wall in boxes.items():
        if name == "floor":
            continue
        fill(img, wall["top_b"], ENAMEL_HI)  # the rim
        fill(img, wall["top_a"], ENAMEL_DK)  # where it stands on the floor: never seen
        if name in ("west", "east"):  # an end wall's own ends, against the front and back walls: never seen
            fill(img, wall["north"], ENAMEL_DK)
            fill(img, wall["south"], ENAMEL_DK)
    return img


def particle():
    """16x16 of the box's enamel with a brass corner bracket, for the bits that fly when it is broken or stood on."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    enamel(img, 0, 0, 16, 16, 77)
    bracket(img, 0, 15, 1, -1, 5)
    bracket(img, 15, 0, -1, 1, 5)
    return texkit.shade(img, 4177, keep=[BRASS_HI] + SHADES, **FINISH)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    sheet(14, 2).save(OUT / "repair_box.png")
    sheet(15, 1, "left").save(OUT / "repair_box_left.png")
    sheet(15, 1, "right").save(OUT / "repair_box_right.png")
    particle().save(ROOT / "src/main/resources/assets/nookworks/textures/block/repair_box.png")
    print("wrote repair box chest sheets to", OUT)
