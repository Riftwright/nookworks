"""Generate the Velarium gear textures (all original pixel art). Deterministic.

Items:  velarium_sword / _pickaxe / _hoe / _axe / _shovel, and the four armour pieces (placed by hand)
Entity: textures/entity/equipment/{humanoid,humanoid_leggings}/velarium.png (worn armour, 64x32) and
        humanoid_baby/velarium.png (all four pieces on a baby's smaller model, one 64x64 sheet)

Velarium is a metal - past netherite, and drawn as one: a deep violet steel with a cold blue-green sheen running
through it, the colours of the hand-finished Block of Velarium. The tool and armour icons are placed pixel by pixel,
shaded as vanilla shades its metal gear: an edge catching the light on the upper left, the far side in shadow, a dark
outline all round, and the sheen as a fleck or two of teal along the lit edge where a real blade would flash. The
hammer's sprite lives in gen_hammers.py, which has a "velarium" palette of its own.

Run from the repo root:  python tools/textures/gen_velarium_gear.py
"""
import random
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ITEM = ROOT / "src/main/resources/assets/nookworks/textures/item"
EQUIP = ROOT / "src/main/resources/assets/nookworks/textures/entity/equipment"

CLEAR = (0, 0, 0, 0)
OUTLINE = (26, 14, 46)
DEEP, DARK, MID, LIGHT, EDGE = (52, 30, 94), (78, 50, 138), (112, 80, 182), (150, 122, 218), (196, 178, 244)
SHEEN, GLINT = (92, 210, 172), (172, 250, 222)  # the Block of Velarium's teal
# the handle: the stick's shades (outline, dark, mid, light), wound with a dark leather grip near the hand
STICK = ((40, 30, 11), (73, 54, 21), (104, 78, 30), (137, 103, 39))
GRIP = ((30, 22, 18), (58, 42, 34), (84, 62, 48))
BRASS = ((96, 66, 26), (150, 110, 44), (206, 160, 70), (240, 206, 120))


def blank(w=16, h=16):
    return Image.new("RGBA", (w, h), CLEAR)


def rgba(c):
    return c + (255,) if len(c) == 3 else c


# ---------------------------------------------------------------- tools
# The tool icons, placed pixel by pixel (2026-10-09; the user: the first ones were not pretty, the axe's angle wrong -
# its head stood upright on a slanting handle). Every head is drawn along the handle's slant: the handle a one-pixel
# stair from the lower left, the head square to it, the blades shaded in bands from the edge facing the upper left (lit)
# to the far edge (shadow), as vanilla shades its tools, with one or two flecks of the Velarium teal on the lit edge.
# 0 the violet outline; 1-7 the metal, deepest to highlight; t u v the teal, shadow to glint; a-d the handle's wood
# (a its outline), e-g the leather grip (e its outline), w-z brass (w deepest).
TOOL_SHADES = {
    "0": OUTLINE, "1": (30, 14, 62), "2": DEEP, "3": DARK, "4": MID, "5": LIGHT, "6": (178, 154, 236), "7": (212, 198, 250),
    "t": (60, 160, 140), "u": SHEEN, "v": GLINT,
    "a": STICK[0], "b": STICK[1], "c": STICK[2], "d": STICK[3], "e": GRIP[0], "f": GRIP[1], "g": GRIP[2],
    "w": BRASS[0], "x": BRASS[1], "y": BRASS[2], "z": BRASS[3],
}
TOOL_ICONS = {
    # a blade three pixels across, a brass guard square to it, the grip wound in leather, a brass pommel
    "velarium_sword": [
        ".............0..",
        "............070.",
        "...........0640.",
        "..........0v420.",
        ".........06420..",
        "........06420...",
        "....0..0u420....",
        "...0z006420.....",
        "...0xz6420......",
        "....0xy20.......",
        "....efxy0.......",
        "...ege0xy0......",
        "..0fe..00.......",
        ".0y0............",
        "0z0.............",
        ".0..............",
    ],
    # the head arched over the handle's end, both points the same length from it
    "velarium_pickaxe": [
        "................",
        "................",
        "..0000..........",
        ".0245v00........",
        "..00235600......",
        "....0024670.....",
        "......024670....",
        ".......024u0....",
        "......ac02460...",
        ".....ada.0250...",
        "....aca...0360..",
        "...ega....0250..",
        "..efe......040..",
        ".ege.......020..",
        "..e.........0...",
        "................",
    ],
    # the broad blade out to the upper left, its edge square to the handle and lit, a short poll behind the eye
    "velarium_axe": [
        "......0760......",
        ".....076560.....",
        "....0765540.....",
        "...07v554440....",
        "....004443320...",
        ".....000232220..",
        "....0...02220...",
        ".......ad020....",
        "......aca.0.....",
        ".....ada........",
        "....aca.........",
        "...ega..........",
        "..efe...........",
        ".ege............",
        "..e.............",
        "................",
    ],
    # a pointed blade along the handle's line
    "velarium_shovel": [
        "................",
        "...........000..",
        "..........07640.",
        ".........076430.",
        "........0v64320.",
        ".......0764320..",
        "........04320...",
        ".......ad020....",
        "......aca.0.....",
        ".....ada........",
        "....aca.........",
        "...ega..........",
        "..efe...........",
        ".ege............",
        "..e.............",
        "................",
    ],
    # a bar out over the handle's top, its blade turned down at the end
    "velarium_hoe": [
        "................",
        ".....00000000...",
        "....0766v66650..",
        "...05333333330..",
        "...06300000c0...",
        "...0u30..ada....",
        "...0420.aca.....",
        "...030.ada......",
        "....0.aca.......",
        ".....ada........",
        "....aca.........",
        "...ega..........",
        "..efe...........",
        ".ege............",
        "..e.............",
        "................",
    ],
}


def tool_icon(rows):
    """One tool icon, as placed (the outline included: no grain pass, which would break its edges)."""
    img = blank()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(TOOL_SHADES[ch]))
    return img


def tools():
    return {name: tool_icon(rows) for name, rows in TOOL_ICONS.items()}


# The armour icons, placed pixel by pixel (2026-10-02, the user's pick of the proposals): each plate lit along its upper
# left rim and in shadow along its lower right, the dark of the inside where a head, body or legs go, and the
# Velarium sheen as one or two teal flecks where the light catches. Shades 1 (deepest) to 7 (highlight); t u v the
# teal from shadow to lit. The helmet is open-faced, its cheek guards down to the jaw.
ICON_SHADES = {
    "1": (30, 14, 62), "2": DEEP, "3": DARK, "4": MID, "5": LIGHT, "6": (178, 154, 236), "7": (212, 198, 250),
    "t": (60, 160, 140), "u": SHEEN, "v": GLINT,
}
ARMOUR_ICONS = {
    "velarium_helmet": [
        "................",
        "................",
        ".....666554.....",
        "....56v66544....",
        "...5666654432...",
        "...5555544332...",
        "...6766655443...",
        "...5411111132...",
        "...5411111132...",
        "...5411111122...",
        "...44......21...",
        "...43......21...",
        "....3......1....",
        "................",
        "................",
        "................",
    ],
    "velarium_chestplate": [
        "................",
        "................",
        ".56765....55432.",
        ".6v654....44332.",
        ".565443..333321.",
        ".453566v3443221.",
        ".34256563433221.",
        "...5655634332...",
        "...5554633322...",
        "...4544532221...",
        "...2222211111...",
        "...5655443322...",
        "....44433221....",
        ".....332211.....",
        "................",
        "................",
    ],
    # the legs three columns apart: the ring takes a column either side, so one stays clear between them (two apart,
    # the ring filled the gap and the icon read as a solid slab)
    "velarium_leggings": [
        "................",
        "................",
        "...4444uv3331...",
        "...7776436543...",
        "...6665435443...",
        "...5555334433...",
        "...6655...433...",
        "...5554...331...",
        "...7v76...543...",
        "...5554...431...",
        "...6655...433...",
        "...5554...331...",
        "...4443...311...",
        "................",
        "................",
        "................",
    ],
    "velarium_boots": [
        "................",
        "................",
        "................",
        "................",
        "...675....553...",
        "...443....331...",
        "...675....543...",
        "...665....443...",
        "..6765....5443..",
        ".7v665....54443.",
        ".33311....11111.",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
}


def armour_icon(rows):
    """
    One armour icon: the shades as placed, then vanilla's ring round it (texkit.outline), worked out from the metal
    alone - the teal read as the plate under it, so a fleck on the rim does not tint the ring. No grain pass: the
    shading is placed by hand, and noise would break its rims.
    """
    img = blank()
    metal = blank()
    plate = {"t": "3", "u": "4", "v": "5"}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(ICON_SHADES[ch]))
                metal.putpixel((x, y), rgba(ICON_SHADES[plate.get(ch, ch)]))
    ring = texkit.outline(metal)
    for y in range(16):
        for x in range(16):
            if not img.getpixel((x, y))[3] and ring.getpixel((x, y))[3]:
                img.putpixel((x, y), ring.getpixel((x, y)))
    return img


def armour_icons():
    return {name: armour_icon(rows) for name, rows in ARMOUR_ICONS.items()}


# ---------------------------------------------------------------- worn armour

# Where each face of the armour model sits on its 64x32 sheet: (x, y, width, height).
HEAD = {"top": (8, 0, 8, 8), "bottom": (16, 0, 8, 8), "right": (0, 8, 8, 8), "front": (8, 8, 8, 8),
        "left": (16, 8, 8, 8), "back": (24, 8, 8, 8)}
BODY = {"top": (20, 16, 8, 4), "bottom": (28, 16, 8, 4), "right": (16, 20, 4, 12), "front": (20, 20, 8, 12),
        "left": (28, 20, 4, 12), "back": (32, 20, 8, 12)}
ARM = {"top": (44, 16, 4, 4), "bottom": (48, 16, 4, 4), "right": (40, 20, 4, 12), "front": (44, 20, 4, 12),
       "left": (48, 20, 4, 12), "back": (52, 20, 4, 12)}
LEG = {"top": (4, 16, 4, 4), "bottom": (8, 16, 4, 4), "right": (0, 20, 4, 12), "front": (4, 20, 4, 12),
       "left": (8, 20, 4, 12), "back": (12, 20, 4, 12)}
SIDES = ("right", "front", "left", "back")


# The worn armour's own shades (the icons and tools keep theirs): the same violet steel, its shadows turning towards
# indigo and its lights towards a pinkish lilac, as a painter turns them. No near-black: a plate's end is one DEEP row,
# drawn only where a plate really ends.
W_DEEP, W_DARK, W_MID, W_LIGHT = (40, 30, 98), (70, 52, 144), (108, 80, 184), (148, 118, 218)
W_LILAC, W_EDGE = (180, 150, 234), (214, 188, 248)


def plate(img, x, y, w, h, rows=None, rivets=True, ends=True, lit_top=True, seed=0):
    """
    One armour plate on a face: its top rim catching the light, the plate lit towards the top and darkening down, lit
    down its left side and shaded down its right, rivets at its upper corners. {@code ends}: the plate ends at the
    bottom of {@code rows}, and its last row is in DEEP shade - only there: a dark row at the foot of every face cut
    the figure into stripes. {@code rows} limits it to part of the face (the rest stays clear), as a boot covers only
    the foot. A scuff or two catches the light towards the upper left, a dent or two is dark towards the lower right.
    """
    y0, y1 = rows if rows else (0, h)
    last = y1 - 1 if ends else y1
    for j in range(y0, y1):
        t = (j - y0) / max(1, last - y0 - 1)
        for i in range(w):
            if j == y0 and lit_top:
                c = W_EDGE if 0 < i < w - 1 else W_LIGHT
            elif j == last:
                c = W_DEEP
            elif i == 0:
                c = W_LIGHT
            elif i == w - 1:
                c = W_DARK
            else:
                c = W_LIGHT if t < 0.2 else W_MID if t < 0.6 else W_DARK
            img.putpixel((x + i, y + j), rgba(c))
    if rivets and w >= 5 and y1 - y0 >= 4:
        img.putpixel((x + 1, y + y0 + 1), rgba(W_EDGE))
        img.putpixel((x + w - 2, y + y0 + 1), rgba(W_EDGE))
    if w >= 4 and y1 - y0 >= 5:
        rng = random.Random(seed * 131 + x * 7 + y)
        inner_w, inner_h = w - 2, (last - y0 - 1)
        for _ in range(1 if w < 6 else 2):  # scuffs, upper left; dents, lower right
            sx = x + 1 + rng.randrange(max(1, inner_w // 2))
            sy = y + y0 + 1 + rng.randrange(max(1, inner_h // 2))
            if img.getpixel((sx, sy))[:3] in (W_MID, W_LIGHT):
                img.putpixel((sx, sy), rgba(W_LILAC))
            dx = x + w - 2 - rng.randrange(max(1, inner_w // 2))
            dy = y + last - 1 - rng.randrange(max(1, inner_h // 2))
            if img.getpixel((dx, dy))[:3] == W_MID:  # a step down only: deeper, a dent read as a hole
                img.putpixel((dx, dy), rgba(W_DARK))


def faces(img, box, names=SIDES, rows=None, rivets=True, ends=True):
    for n, name in enumerate(names):
        # a top or bottom face is not where a plate ends: it meets the side faces at its edges
        flat = name in ("top", "bottom")
        plate(img, *box[name], rows=rows, rivets=rivets and not flat, ends=ends and not flat, lit_top=not flat, seed=n)


def vein(img, x0, y0, x1, y1):
    """A sheen vein across a plate, the Block of Velarium's teal set into the metal as a broken inlay: a pixel, a pixel,
    a gap - a line drawn whole read as a scratch of paint."""
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        if i % 3 == 2:
            continue
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        img.putpixel((x, y), rgba(GLINT if i == 0 else SHEEN))


def worn_outer():
    """Helmet, chestplate and boots, which share the outer sheet."""
    img = Image.new("RGBA", (64, 32), CLEAR)
    # Helmet: a closed crown and back, the face open under a brow band with a sheen stone, cheek guards down the side.
    faces(img, HEAD, ("top", "right", "left", "back"))
    x, y, w, h = HEAD["front"]
    plate(img, x, y, w, h, rows=(0, 3), rivets=False, ends=False)
    for i in range(w):
        img.putpixel((x + i, y + 2), rgba(W_DARK))  # the brow band
    img.putpixel((x + 3, y + 2), rgba(SHEEN))
    img.putpixel((x + 4, y + 2), rgba(GLINT))
    for j in range(3, h):
        for i in (0, 1, w - 2, w - 1):  # the cheek guards, ending at the jaw
            c = W_DEEP if j == h - 1 else W_LIGHT if i == 0 else W_DARK if i == w - 1 else W_MID
            img.putpixel((x + i, y + j), rgba(c))
    tx = HEAD["top"][0]
    for j in range(8):  # a ridge down the middle of the crown
        img.putpixel((tx + 3, j), rgba(W_EDGE))
        img.putpixel((tx + 4, j), rgba(W_LIGHT))
    for side in ("right", "left"):  # the vein round the sides
        sx, sy, _, _ = HEAD[side]
        vein(img, sx + 1, sy + 5, sx + 6, sy + 3)
    # Chestplate: the body, and the upper half of each arm as pauldrons. The breastplate ends at the waist.
    faces(img, BODY, ("top",) + SIDES)
    x, y, w, h = BODY["front"]
    for j in range(2, h - 2):  # the seam where the breastplate's two halves meet
        img.putpixel((x + 3, y + j), rgba(W_DARK))
    img.putpixel((x + 3, y + 1), rgba(SHEEN))  # a stone at the collar
    img.putpixel((x + 4, y + 1), rgba(GLINT))
    vein(img, x + 1, y + 9, x + 2, y + 4)
    vein(img, x + 6, y + 4, x + 6, y + 8)
    bx, by, bw, bh = BODY["back"]
    vein(img, bx + 2, by + 3, bx + 5, by + 8)
    faces(img, ARM, ("top",))
    faces(img, ARM, rows=(0, 6))
    for name in SIDES:  # the pauldron's lower lame, its rim catching the light
        ax, ay, aw, _ = ARM[name]
        for i in range(aw):
            img.putpixel((ax + i, ay + 3), rgba(W_LILAC if 0 < i < aw - 1 else W_LIGHT))
    # Boots: the bottom of each leg only, so the leggings show above them; a cuff, and the sole where the boot ends.
    faces(img, LEG, ("bottom",), rivets=False)
    faces(img, LEG, rows=(7, 12), rivets=False)
    for name in SIDES:
        lx, ly, lw, _ = LEG[name]
        for i in range(lw):
            img.putpixel((lx + i, ly + 8), rgba(W_DARK))
    return img


def box(u, v, w, h, d):
    """Where each face of a model cube textured from (u, v) sits: the layout every vanilla entity model uses."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


# The baby armour model (HumanoidModel.createBabyArmorMesh): its own cubes, every piece on one sheet. Chest: the body
# and arms; legs: the legs and the waist; feet: a foot under each leg.
BABY_HEAD = box(0, 0, 9, 8, 8)
BABY_BODY = box(0, 17, 6, 5, 3)
BABY_ARMS = (box(30, 17, 2, 5, 3), box(30, 25, 2, 5, 3))
BABY_LEGS = (box(18, 17, 3, 4, 3), box(18, 24, 3, 4, 3))
BABY_FEET = (box(0, 25, 3, 1, 3), box(0, 29, 3, 1, 3))
# the waist is 5.9 x 2 x 2.9, so its faces fall between pixels: the belt is painted over the whole of its patch
BABY_WAIST = (0, 36, 18, 5)


def worn_baby():
    """
    The same armour on a baby (a zombie's, a piglin's), drawn to the smaller model's cubes. Its faces are four or five
    pixels tall, so a dark row ends a plate only where the armour ends - the helmet's rim, the breastplate at the waist,
    the pauldrons, the greaves at the ankle: two dark rows on every face left the sheet mostly dark.
    """
    img = Image.new("RGBA", (64, 64), CLEAR)
    # Helmet: as the grown one, nine wide, the stone in the middle of the brow.
    faces(img, BABY_HEAD, ("top", "right", "left", "back"))
    x, y, w, h = BABY_HEAD["front"]
    plate(img, x, y, w, h, rows=(0, 3), rivets=False, ends=False)
    for i in range(w):
        img.putpixel((x + i, y + 2), rgba(W_DARK))
    img.putpixel((x + 4, y + 2), rgba(GLINT))
    for j in range(3, h):
        for i in (0, 1, w - 2, w - 1):
            c = W_DEEP if j == h - 1 else W_LIGHT if i == 0 else W_DARK if i == w - 1 else W_MID
            img.putpixel((x + i, y + j), rgba(c))
    tx = BABY_HEAD["top"][0]
    for j in range(8):
        img.putpixel((tx + 4, j), rgba(W_EDGE))
    for side in ("right", "left"):
        sx, sy, _, _ = BABY_HEAD[side]
        vein(img, sx + 1, sy + 5, sx + 6, sy + 3)
    # Chestplate: the body, a stone at the collar and the seam under it; pauldrons over the top of the arms.
    faces(img, BABY_BODY, ("top",) + SIDES, rivets=False)
    x, y, w, h = BABY_BODY["front"]
    img.putpixel((x + 2, y + 1), rgba(SHEEN))
    img.putpixel((x + 3, y + 1), rgba(GLINT))
    for j in (2, 3):
        img.putpixel((x + 2, y + j), rgba(W_DARK))
    bx, by, _, _ = BABY_BODY["back"]
    vein(img, bx + 1, by + 3, bx + 4, by + 1)
    for arm in BABY_ARMS:
        faces(img, arm, ("top",), rivets=False)
        faces(img, arm, rows=(0, 4), rivets=False)
    # Leggings: the belt round the waist with its buckle, and the legs.
    x0, y0, w, h = BABY_WAIST
    for j in range(h):
        for i in range(w):
            img.putpixel((x0 + i, y0 + j), rgba(W_LIGHT if j == 0 else W_DARK if j == 3 else W_MID))
    img.putpixel((x0 + 5, y0 + 3), rgba(SHEEN))
    img.putpixel((x0 + 6, y0 + 3), rgba(GLINT))
    for leg in BABY_LEGS:
        faces(img, leg, ("top",), rivets=False)
        faces(img, leg, rivets=False)
        for name in SIDES:
            lx, ly, _, _ = leg[name]
            img.putpixel((lx + 1, ly + 1), rgba(SHEEN))
    # Boots: a foot, one pixel tall: a sole, and a lit band round it.
    for foot in BABY_FEET:
        faces(img, foot, ("top",), rivets=False)
        x, y, w, h = foot["bottom"]
        for j in range(h):
            for i in range(w):
                img.putpixel((x + i, y + j), rgba(W_DEEP))
        for name in SIDES:
            fx, fy, fw, _ = foot[name]
            for i in range(fw):
                img.putpixel((fx + i, fy), rgba(W_EDGE if i == 1 else W_MID))
    return img


def worn_leggings():
    """Leggings: a belt round the bottom of the body with a buckle, and the legs down to where the boots begin."""
    img = Image.new("RGBA", (64, 32), CLEAR)
    faces(img, BODY, rows=(8, 12), rivets=False, ends=False)  # the thigh plates go on below the belt
    for name in SIDES:
        x, y, w, _ = BODY[name]
        for i in range(w):
            img.putpixel((x + i, y + 9), rgba(W_DARK))  # the belt
    x, y, w, h = BODY["front"]
    img.putpixel((x + 3, y + 9), rgba(SHEEN))  # the buckle
    img.putpixel((x + 4, y + 9), rgba(GLINT))
    faces(img, LEG, ("top",))
    faces(img, LEG)
    for name in SIDES:  # a knee plate, and the vein down the thigh
        lx, ly, lw, _ = LEG[name]
        for i in range(lw):
            img.putpixel((lx + i, ly + 5), rgba(W_EDGE if 0 < i < lw - 1 else W_LIGHT))
            img.putpixel((lx + i, ly + 6), rgba(W_DARK))
        img.putpixel((lx + 1, ly + 3), rgba(SHEEN))
    return img


def main():
    ITEM.mkdir(parents=True, exist_ok=True)
    for name, img in {**tools(), **armour_icons()}.items():
        img.save(ITEM / f"{name}.png")
    for folder, sheet in (("humanoid", worn_outer), ("humanoid_baby", worn_baby), ("humanoid_leggings", worn_leggings)):
        out = EQUIP / folder
        out.mkdir(parents=True, exist_ok=True)
        sheet().save(out / "velarium.png")
    print("wrote velarium gear textures")


if __name__ == "__main__":
    main()
