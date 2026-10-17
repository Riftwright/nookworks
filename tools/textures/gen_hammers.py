"""Generate hammer item textures (16x16) for every material.

Colours per material follow the vanilla tool of the same material (5 shades, dark -> light), so the hammers sit
naturally next to vanilla pickaxes; copper is copper, not the salmon it had drifted to, and Velarium is the violet
and teal of its other gear. The shape is drawn procedurally: a heavy head lying across the handle's end, its two
striking faces polished and set off from the body by a groove, the body lit along its upper edge, the handle's
wedge showing in the eye on top, and a leather grip wound round the handle's end.

Run from the repo root:  python tools/textures/gen_hammers.py [--preview out.png]
"""
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/textures/item"

# outline, dark, mid, light, highlight
PALETTES = {
    "stone": [(24, 24, 24), (73, 73, 73), (127, 127, 127), (137, 137, 137), (154, 154, 154)],
    "copper": [(64, 28, 14), (120, 56, 30), (184, 96, 56), (220, 132, 86), (246, 176, 128)],
    "iron": [(24, 24, 24), (68, 68, 68), (193, 193, 193), (216, 216, 216), (255, 255, 255)],
    "golden": [(63, 46, 14), (130, 93, 22), (233, 177, 21), (234, 238, 87), (253, 255, 118)],
    "diamond": [(8, 37, 32), (14, 63, 54), (39, 178, 154), (43, 199, 172), (51, 235, 203)],
    "netherite": [(35, 16, 18), (59, 57, 59), (93, 86, 93), (112, 103, 112), (134, 123, 134)],
    "velarium": [(26, 14, 46), (78, 50, 138), (112, 80, 182), (150, 122, 218), (196, 178, 244)],
}
# Stick shades, dark -> light (same as the vanilla stick), and the leather wound round its end.
HANDLE = [(40, 30, 11), (73, 54, 21), (104, 78, 30), (137, 103, 39)]
GRIP = [(30, 22, 18), (58, 42, 34), (92, 68, 52)]
# Velarium's sheen, flashing along its lit edge as on its other tools.
SHEEN = (92, 210, 172)

HEAD_CENTER = (10.3, 5.3)
HEAD_HALF_LENGTH = 5.6  # along the top-left -> bottom-right diagonal
HEAD_HALF_WIDTH = 3.2   # across it


def in_head(x, y):
    cx, cy = x + 0.5 - HEAD_CENTER[0], y + 0.5 - HEAD_CENTER[1]
    along = (cx + cy) / math.sqrt(2)
    across = (cx - cy) / math.sqrt(2)  # positive = towards the top-right (lit side)
    return abs(along) <= HEAD_HALF_LENGTH and abs(across) <= HEAD_HALF_WIDTH, along, across


def draw(palette, sheen=False):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # Handle: a diagonal from the bottom left towards the head, built as vanilla builds a tool's stick - a pixel of
    # wood a row, edged a step darker on its upper left, where the light falls, and darkest on its lower right; the
    # wood two shades by turns, for the grain. Leather wound round its end, edged the same way.
    for i in range(10):
        x, y = 2 + i, 14 - i
        if i < 3:
            row = ((-1, GRIP[1]), (0, GRIP[2] if i % 2 else GRIP[1]), (1, GRIP[0]))
        else:
            row = ((-1, HANDLE[1]), (0, HANDLE[3] if i % 2 else HANDLE[2]), (1, HANDLE[0]))
        for dx, colour in row:
            if 0 <= x + dx < 16:
                img.putpixel((x + dx, y), colour + (255,))
    # Head on top of the handle.
    head = {}
    for y in range(16):
        for x in range(16):
            inside, along, across = in_head(x, y)
            if inside:
                head[(x, y)] = (along, across)
    face_start = HEAD_HALF_LENGTH - 1.6
    for (x, y), (along, across) in head.items():
        open_sides = [(dx, dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (x + dx, y + dy) not in head]
        if open_sides:
            # vanilla's two outlines: a dark grey where the edge faces the light, near black where it faces away
            shade = 1 if all(side in ((-1, 0), (0, -1)) for side in open_sides) else 0
        elif abs(along) > face_start:
            shade = 4 if across > 0 else 3  # the striking faces, polished
        elif abs(along) > face_start - 0.9:
            shade = 1  # the groove that sets each face off from the body
        elif abs(along) < 0.7:
            shade = 1  # the eye, the handle's wedge showing in it
        elif across > 0.8:
            shade = 4
        elif across > -0.2:
            shade = 3
        elif across > -1.6:
            shade = 2
        else:
            shade = 1
        img.putpixel((x, y), palette[shade] + (255,))
    ex, ey = int(HEAD_CENTER[0]), int(HEAD_CENTER[1])
    img.putpixel((ex, ey), HANDLE[2] + (255,))  # the wedge
    if sheen:
        for (x, y), (along, across) in head.items():
            if 0.8 < across < 1.8 and abs(along) < face_start - 1 and (x + y) % 3 == 0:
                img.putpixel((x, y), SHEEN + (255,))
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    images = {}
    for name, palette in PALETTES.items():
        img = draw(palette, sheen=name == "velarium")
        img.save(OUT / f"{name}_hammer.png")
        images[name] = img
    if "--preview" in sys.argv:
        target = Path(sys.argv[sys.argv.index("--preview") + 1])
        sheet = Image.new("RGBA", (16 * len(images) + 2 * (len(images) + 1), 20), (139, 139, 139, 255))
        for i, img in enumerate(images.values()):
            sheet.paste(img, (2 + i * 18, 2), img)
        sheet.resize((sheet.width * 10, sheet.height * 10), Image.NEAREST).save(target)
    print("wrote", len(images), "hammer textures")


if __name__ == "__main__":
    main()
