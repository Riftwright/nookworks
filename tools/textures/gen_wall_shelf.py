"""The wall shelf's shape: a board a trapdoor's size on two black iron L brackets (the user, 2026-10-11, showing a shop's
shelf: "上の板はトラップドアサイズ"). The woods' own models name their planks (tools/gen_wood_data.py); the brackets are
the same for every wood, in the black iron of the Hook (textures/block/hook.png: a lit band for tops, the body for sides,
the shade for undersides - read here as gen_hook reads it).

Facing north unturned, its wall to the south (the blockstate turns it):
- block/template_wall_shelf_board   the board: the whole block across and deep, 3 px thick at its top
- block/wall_shelf_bracket_left     the bracket on the left as seen from in front (east): an arm 2 px wide down the
                                    wall from under the board, 7 px, and one under the board out from the wall, 9 px
- block/wall_shelf_bracket_right    the same on the right (west), 2 px in from the board's end as the left one is
- block/template_wall_shelf         all three: the item's model, a shelf on its own
Brackets at a shelf's joins with another are left out by its blockstate (WallShelfBlock).
Run from the repo root:  python tools/textures/gen_wall_shelf.py
"""
import json
from pathlib import Path

from gen_chair import box

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "src/main/resources/assets/nookworks/models/block"
IRON = "nookworks:block/hook"
# the iron's bands on hook.png: rows 0-3 lit, 4-11 body, 12-15 shade
BANDS = {"up": (0, 4), "down": (12, 16)}
BODY = (4, 12)


def iron(frm, to, skip=()):
    """A piece of a bracket, each face from its band of the iron, a texel to a pixel (AssetAudit allows no stretching):
    a face longer than its band is deep is laid along the band, its uv turned a quarter."""
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    size = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
    faces = {}
    for side, (w, h) in size.items():
        if side in skip:
            continue
        top, bottom = BANDS.get(side, BODY)
        if h > bottom - top:
            faces[side] = {"texture": "#iron", "uv": [4, top, 4 + h, top + w], "rotation": 90}
        else:
            faces[side] = {"texture": "#iron", "uv": [4, top, 4 + w, top + h]}
    return {"from": frm, "to": to, "faces": faces}


def bracket(x, on_wall=True):
    """A bracket {@code x} to {@code x + 2} across: down the wall, and out under the board. On a wall its back is
    against the wall and left out; the item's model (a shelf on its own, {@code on_wall} false) is seen from behind
    too, so there the back is drawn."""
    return [iron([x, 6, 15], [x + 2, 13, 16], skip=("south", "up") if on_wall else ("up",)),
            iron([x, 12, 6], [x + 2, 13, 15], skip=("up", "south"))]


def board():
    return [box("board", [0, 13, 0], [16, 16, 16], "#planks")]


def write(name, data):
    (MODELS / f"{name}.json").write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    planks = {"particle": "minecraft:block/oak_planks", "planks": "minecraft:block/oak_planks"}
    write("template_wall_shelf_board", {"parent": "minecraft:block/block", "textures": planks, "elements": board()})
    for side, x in (("left", 12), ("right", 2)):
        write(f"wall_shelf_bracket_{side}", {"parent": "minecraft:block/block", "ambientocclusion": False,
                                             "textures": {"iron": IRON, "particle": IRON}, "elements": bracket(x)})
    # the icon turned so both brackets show under the board, and brought down to the slot's middle (r21: a board high
    # in the slot with one leg, read as a broken table)
    write("template_wall_shelf", {"parent": "minecraft:block/block", "textures": dict(planks, iron=IRON),
                                  "elements": board() + bracket(12, False) + bracket(2, False),
                                  "display": {"gui": {"rotation": [30, 45, 0], "translation": [0, -3, 0],
                                                      "scale": [0.625, 0.625, 0.625]}}})
    print("wrote the wall shelf's models")


if __name__ == "__main__":
    main()
