"""Fine Shears (上質なはさみ; the item id stays wool_shears): the item's icon. All original pixel art, placed pixel by
pixel (itemkit). Deterministic.

Spring shears, as in the photo the user showed (2026-10-05): two long steel blades open in a V on a bolt, two brass
handles spread wide below it, and between the handles the coil of the spring that holds them open, the slot showing
through it. Drawn so after three tries the user set aside: blade shears (a bow and two arms, read as tongs; again with
the arms swelling into a loop), and scissors with finger rings ("ハサミに見えないなぁ…").

- item/wool_shears.png
and the item models (handheld, as vanilla's shears). Run from the repo root:  python tools/textures/gen_wool_shears.py
"""
import json
from pathlib import Path

import itemkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CLEAR = (0, 0, 0, 0)

COLOURS = {
    # steel, deep to gleam
    "E": (58, 64, 76), "D": (104, 112, 126), "S": (156, 164, 176), "L": (204, 210, 218), "W": (242, 246, 248),
    # brass, as the ingot has it, deep to shine
    "e": (96, 58, 16), "d": (138, 94, 28), "b": (180, 132, 48), "l": (222, 178, 88), "w": (248, 224, 152),
    # the bolt, and the spring's wire
    "r": (64, 60, 58), "g": (150, 150, 146),
}

# The blades lit along the upper left, a gleam on each, open on the bolt (r); the handles lit above and left (l), deep
# below and right (d); the spring's coil between them (g).
ART = itemkit.check([
    "................",
    "...........W....",
    "..........LS....",
    "..........LD....",
    ".........LS.....",
    ".........LD...W.",
    "........LS...LS.",
    "........LD.LWSD.",
    ".......brLSSD...",
    "......lbbd......",
    ".....lb..bd.....",
    "....lb.gg.bd....",
    "...lb.g..g.bd...",
    "..lb...gg..bd...",
    ".lbd........bd..",
    ".dd.........dd..",
])


def enclosed(rows):
    """The clear pixels the shape closes round (the coil's middle): those the border cannot reach."""
    clear = {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "."}
    seen, todo = set(), [(x, y) for (x, y) in clear if x in (0, 15) or y in (0, 15)]
    while todo:
        x, y = todo.pop()
        if (x, y) in seen or (x, y) not in clear:
            continue
        seen.add((x, y))
        todo += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return clear - seen


def icon():
    img = itemkit.sprite(ART, COLOURS)
    for x, y in enclosed(ART):  # the ring round the shape is drawn inside the coil too: open it again
        img.putpixel((x, y), CLEAR)
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    icon().save(ASSETS / "textures/item/wool_shears.png")
    write_json(ASSETS / "models/item/wool_shears.json",
               {"parent": "minecraft:item/handheld", "textures": {"layer0": "nookworks:item/wool_shears"}})
    write_json(ASSETS / "items/wool_shears.json", {"model": {"type": "minecraft:model", "model": "nookworks:item/wool_shears"}})
    print("wrote the Fine Shears")


if __name__ == "__main__":
    main()
