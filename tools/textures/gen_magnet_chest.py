"""The Magnet Chest's faces and model (2026-11-24; its textures before were from the mod's first days). Original pixel
art (no vanilla pixels). Deterministic.

What the user settled, over four rounds: a dark box of boards with iron fittings standing half a pixel proud of them -
a square block at each corner, a band round its middle, a handle on its lid - and on its front the mouth items are
drawn in by: an iron-rimmed opening really set into the box, dark inside. The look agrees with what the block is, a
box that pulls items in and keeps them. No picture of a magnet on it ("the name is Magnet, but drawing a U magnet is
too tacky"); not too much chest either (DECISIONS.md, "The Magnet Chest's look").

    python tools/textures/gen_magnet_chest.py                  the chest's model and textures
    python tools/textures/gen_magnet_chest.py --addon OUT_DIR  the industrial add-on's Short-Range Magnet Chest on the
                                                               same lines, for its own scripts to take: its front
                                                               texture and its model (which reads this mod's
                                                               magnet_chest_back/side/top/iron/chute by name)

How a face is made: boards first (a few tones a board: its upper edge lit, its lower edge in shade, a dark seam under
it, grain in short runs along it, a butt joint here and there), then the iron from the maps below, a letter a pixel,
laid over the boards after their grain so no noise breaks a fitting up; 'x' darkens the board under it a step (a
fitting's shadow).

How the model is made: the boards are a box half a pixel in from the block's four sides and its top, with the mouth
cut 2 px into its front (a box behind the mouth and a slab of four round it); each fitting is a box reaching the
block's own faces, so it stands that half pixel proud. No uv is given: every face takes the pixels of the face it lies
on by where it lies, so the pictures below are the model's plan too - a fitting's box must cover its pixels. A
fitting's faces that are not on the block's faces (its edges) take the plain iron texture, the mouth's walls the dark
one. A whole pixel of relief read as bolted-on slabs in the game; half is what was liked.
The block's occlusion shape must be within the boards' box (MagnetChestBlock), or the ground shows a gap round its foot.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
BLOCK = ASSETS / "textures/block"

# the boards, seam to lit edge: a dark walnut, as the chest has always been, a little less orange than it was
WOOD = [(52, 33, 21), (78, 50, 30), (96, 63, 37), (112, 75, 44), (128, 88, 53)]
TONES = {
    "k": (30, 32, 37), "d": (52, 55, 62), "m": (78, 82, 90), "l": (108, 112, 121), "h": (150, 154, 162),  # iron, dark
    "K": (14, 15, 18),  # the mouth's depth
    # the industrial add-on's steel and brass (its reducer on the Short-Range Magnet Chest), dark to light
    "t": (70, 72, 80), "s": (104, 106, 114), "S": (150, 152, 160), "H": (196, 198, 206),
    "y": (96, 66, 26), "b": (146, 106, 42), "B": (200, 156, 68), "Y": (238, 204, 116),
}
RELIEF = 0.5  # how far the fittings stand off the boards: a whole pixel read as bolted-on slabs in the game
BODY = [RELIEF, 0, RELIEF, 16 - RELIEF, 16 - RELIEF, 16 - RELIEF]


def boards(seed, seams=(3, 7, 11, 15)):
    """A face of boards lying across, a seam under each (the rows in seams)."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    top = 0
    for seam in seams:
        lean = rng.choice((0, 0, 0, -1))  # a board a shade darker than its neighbours now and then
        joint = rng.choice((None, rng.randint(3, 12)))
        runs = {}
        for _ in range(rng.randint(2, 3)):
            y, length = rng.randint(top, seam - 1), rng.randint(2, 5)
            start = rng.randint(0, 16 - length)
            for x in range(start, start + length):
                runs[(x, y)] = rng.choice((-1, -1, 1))
        for y in range(top, seam + 1):
            for x in range(16):
                if y == seam:
                    tone = 0
                else:
                    tone = 2 + lean + (1 if y == top else 0) - (1 if y == seam - 1 and seam - top > 2 else 0)
                    tone += runs.get((x, y), 0)
                    if joint is not None and x == joint:
                        tone = 1
                    tone = max(1, min(4, tone))
                img.putpixel((x, y), WOOD[tone] + (255,))
        top = seam + 1
    return img


def lay(img, rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == ".":
                continue
            if c == "x":
                here = img.getpixel((x, y))[:3]
                if here in WOOD:
                    img.putpixel((x, y), WOOD[max(0, WOOD.index(here) - 1)] + (255,))
                continue
            img.putpixel((x, y), TONES[c] + (255,))
    return img


def iron():
    """The fittings' edges: plain iron, a little uneven, lit along its upper rows."""
    rng = random.Random(5)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), TONES[rng.choice("mmmddd")] + (255,))
    return img


PLAIN = ["................"] * 16
HANDLE = PLAIN[:6] + [".....dllllk.....", ".....lhhhhd.....", ".....lddddk.....", ".....dkkkkk.....", "......xxxxx....."] + PLAIN[11:]
HANDLE_BOX = [5, 16 - RELIEF, 6, 11, 16, 10]
DEPTH = 2.5  # how far in the mouth's back lies


def over(*layers):
    """Maps laid one over another: a later one's pixel where it has one."""
    return ["".join(next((l[y][x] for l in reversed(layers) if l[y][x] != "."), ".") for x in range(16)) for y in range(16)]


def rows(**at):
    """A map from its named rows (r3="...") - the rest empty."""
    return [at.get(f"r{y}", "................") for y in range(16)]


def mouth(lo, hi):
    """The mouth on the front: an iron rim two pixels broad from lo to hi (rows and columns alike), riveted at its
    corners, and inside it the opening's back - dark, darker still at its heart."""
    out = [list(r) for r in PLAIN]
    for y in range(lo, hi + 1):
        for x in range(lo, hi + 1):
            edge = min(x - lo, y - lo, hi - x, hi - y)
            if edge == 0:
                c = "l" if (y == lo or x == lo) and not (x == hi or y == hi) else "k"
            elif edge == 1:
                c = "m"
            elif edge == 2:
                c = "k"
            else:
                c = "K"
            out[y][x] = c
    for x, y in ((lo, lo), (hi, lo)):
        out[y][x] = "d"
    for x, y in ((lo + 1, lo + 1), (hi - 1, lo + 1), (lo + 1, hi - 1), (hi - 1, hi - 1)):
        out[y][x] = "h"
    if hi + 1 < 16:
        for x in range(lo + 1, hi + 1):
            out[hi + 1][x] = "x"
    return ["".join(r) for r in out]


def ring(x0, y0, z0, x1, y1, z1, lo, hi):
    """A slab from (x0, y0) to (x1, y1) between z0 and z1 with a square hole from lo to hi through it, as four
    boxes, each with the face of it that looks into the hole."""
    return [([x0, hi, z0, x1, y1, z1], "down"), ([x0, y0, z0, x1, lo, z1], "up"),
            ([x0, lo, z0, lo, hi, z1], "east"), ([hi, lo, z0, x1, hi, z1], "west")]


def band(y0, y1, lo, hi):
    """A band round the box at that height, left open on the front between lo and hi (where the mouth's rim is)."""
    r = RELIEF
    return [[0, y0, 0, r, y1, 16], [16 - r, y0, 0, 16, y1, 16], [r, y0, 16 - r, 16 - r, y1, 16],
            [r, y0, 0, lo, y1, r], [hi, y0, 0, 16 - r, y1, r]]


# P, the Magnet Chest: the chest as it was, drawn clean - a square iron block at each corner, the band round its middle, and on its
# front the mouth in its riveted rim.
CORNERS = rows(r0="mld..........mld", r1="lhd..........lhd", r2="ddk..........ddk", r3="xxx..........xxx",
               r13="mld..........mld", r14="lhd..........lhd", r15="ddk..........ddk")
BAND_P = rows(r7="llhlllhllhlllhll", r8="dddddddddddddddd", r9="xxxxxxxxxxxxxxxx")
MOUTH = mouth(3, 12)
P = {
    "front": over(CORNERS, BAND_P, MOUTH), "back": over(CORNERS, BAND_P), "side": over(CORNERS, BAND_P),
    "top": over(CORNERS, HANDLE), "mouth": (3, 13, 5, 11),
    "boxes": [[x, y, z, x + 3, y + 3, z + 3] for x in (0, 13) for y in (0, 13) for z in (0, 13)]
             + band(7, 9, 3, 13) + [HANDLE_BOX],
}

# The industrial add-on's Short-Range Magnet Chest (it pulls from one block round it, not five): the same chest, its
# mouth narrowed - the rim as the Magnet Chest's, the opening four across in place of six, a brass collar round it
# (the user's pick of two, 2026-11-24). Drawn here because it is this chest; the add-on takes it with --addon.
MOUTH_S1 = rows(r3="...dllllllllk...", r4="...lhmmmmmmhk...", r5="...lmYYYYYbmk...", r6="...lmBkkkkbmk...",
                r7="...lmBkKKkbmk...", r8="...lmBkKKkbmk...", r9="...lmBkkkkbmk...", r10="...lmbyyyyymk...",
                r11="...lhmmmmmmhk...", r12="...dkkkkkkkkk...", r13="....xxxxxxxxx...")
S1 = {**P, "front": over(CORNERS, BAND_P, MOUTH_S1), "mouth": (3, 13, 6, 10)}
PROPOSALS = {"P": P, "S1": S1}

SEEDS = {"front": 3, "back": 31, "side": 11, "top": 23}
FACES = {"north": "front", "south": "back", "east": "side", "west": "side", "up": "top", "down": "top"}
ON_FACE = {"north": (2, 0), "south": (5, 16), "west": (0, 0), "east": (3, 16), "down": (1, 0), "up": (4, 16)}


def faces(letter):
    return {face: lay(boards(SEEDS[face]), PROPOSALS[letter][face]) for face in SEEDS}


def chute():
    """The mouth's walls: iron in the dark (its floor is lit as a top face is: anything lighter read as a grey shelf)."""
    rng = random.Random(9)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), TONES[rng.choice("kkkK")] + (255,))
    return img


def model(letter, prefix):
    """The model: the boards (a box behind the mouth and a slab round it), the mouth's rim, the fittings; every face
    by where it lies."""
    r = RELIEF
    rim_lo, rim_hi, lo, hi = PROPOSALS[letter]["mouth"]
    elements = []

    def add(box, kind, inner=None):
        sides = {}
        for side, (axis, at) in ON_FACE.items():
            outer = box[axis] == at
            if side == inner:
                texture = "#chute"
            elif kind == "boards" or outer:
                texture = f"#{FACES[side]}"
            else:
                texture = "#iron"
            if kind != "boards" and not outer and side == "south" and box[5] <= r:
                continue  # the rim's and the front band's backs lie on the boards
            if kind == "iron" and not outer and side == "down" and box[1] == 16 - r:
                continue  # the handle's underside too
            face = {"texture": texture}
            if outer:
                face["cullface"] = side
            sides[side] = face
        elements.append({"from": box[:3], "to": box[3:], "faces": sides})

    add([r, 0, DEPTH, 16 - r, 16 - r, 16 - r], "boards")  # its north face is the mouth's back
    for box, inner in ring(r, 0, r, 16 - r, 16 - r, DEPTH, lo, hi):
        add(box, "boards", inner)
    for box, inner in ring(rim_lo, rim_lo, 0, rim_hi, rim_hi, r, lo, hi):
        add(box, "iron", inner)
    for box in PROPOSALS[letter]["boxes"]:
        add(box, "iron")
    textures = {face: f"nookworks:block/{prefix}_{face}" for face in SEEDS}
    textures["iron"] = "nookworks:block/magnet_chest_iron"
    textures["chute"] = "nookworks:block/magnet_chest_chute"
    textures["particle"] = textures["side"]
    return {"parent": "minecraft:block/block", "textures": textures, "elements": elements}


def write_json(path, data):
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def put(letter, prefix, model_name):
    for face, img in faces(letter).items():
        img.save(BLOCK / f"{prefix}_{face}.png")
    iron().save(BLOCK / "magnet_chest_iron.png")
    chute().save(BLOCK / "magnet_chest_chute.png")
    write_json(ASSETS / f"models/block/{model_name}.json", model(letter, prefix))


def main(args):
    if args and args[0] == "--addon":
        out = Path(args[1])
        out.mkdir(parents=True, exist_ok=True)
        faces("S1")["front"].save(out / "short_range_magnet_chest_front.png")
        made = model("S1", "magnet_chest")
        made["textures"]["front"] = "nooktech:block/short_range_magnet_chest_front"
        write_json(out / "short_range_magnet_chest.json", made)
        print("wrote the add-on's front and model to", out)
        return
    put("P", "magnet_chest", "magnet_chest")
    print("wrote the Magnet Chest")


if __name__ == "__main__":
    main(sys.argv[1:])
