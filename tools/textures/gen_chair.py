"""The chair's shape, shared by every wood's chair (models/block/template_chair.json; the woods' own models name its
planks and stripped log, tools/gen_wood_data.py).

A dining chair in proportion (the user, 2026-10-04: "もっとバランスよく", picking the taller back of three drafts and
asking for a thicker seat): legs 2 px square set in from the corners, with a rung low between each side's pair; a seat
plate 3 px thick, its top at 9 px where the rider sits (SeatEntity.SEAT_HEIGHT); the back's two posts rising out of the
back legs' line to 22 px - above the block, as high over the seat as the seat is over the floor - with a deep crest
rail, a low rail just over the seat, and three slim splats between.

The chair faces south unturned (the blockstate turns it): its back is along the north edge. Every face names its uv:
the back reaches above the block, where a face's own projection would fall off the texture.
Run from the repo root:  python tools/textures/gen_chair.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/models/block/template_chair.json"


def uv(side, frm, to):
    """The face's own projection, as the game would take it, moved down onto the texture where it would run off its top."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    if side == "up":
        return [x1, z1, x2, z2]
    if side == "down":
        return [x1, 16 - z2, x2, 16 - z1]
    u = {"north": (16 - x2, 16 - x1), "south": (x1, x2), "west": (z1, z2), "east": (16 - z2, 16 - z1)}[side]
    v0, v1 = 16 - y2, 16 - y1
    if v0 < 0:
        v0, v1 = 0, v1 - v0
    return [u[0], v0, u[1], v1]


def box(name, frm, to, texture, cull_down=False):
    faces = {}
    for side in ("north", "east", "south", "west", "up", "down"):
        face = {"uv": uv(side, frm, to), "texture": texture}
        if side == "down" and cull_down:
            face["cullface"] = "down"
        faces[side] = face
    return {"name": name, "from": frm, "to": to, "faces": faces}


def elements():
    e = []
    for name, x, z in (("leg_nw", 2, 2), ("leg_ne", 12, 2), ("leg_sw", 2, 12), ("leg_se", 12, 12)):
        e.append(box(name, [x, 0, z], [x + 2, 6, z + 2], "#post", cull_down=True))
    e.append(box("rung_west", [2.5, 2, 4], [3.5, 3, 12], "#post"))
    e.append(box("rung_east", [12.5, 2, 4], [13.5, 3, 12], "#post"))
    e.append(box("seat", [2, 6, 2], [14, 9, 14], "#planks"))
    e.append(box("back_post_west", [2, 9, 2], [4, 22, 4], "#post"))
    e.append(box("back_post_east", [12, 9, 2], [14, 22, 4], "#post"))
    e.append(box("crest_rail", [4, 18.5, 2.5], [12, 21.5, 3.5], "#planks"))
    e.append(box("low_rail", [4, 10, 2.5], [12, 11, 3.5], "#post"))
    for i, x in enumerate((5.5, 7.5, 9.5)):
        e.append(box(f"splat_{i}", [x, 11, 2.75], [x + 1, 18.5, 3.25], "#post"))
    return e


def main():
    model = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/oak_planks", "planks": "minecraft:block/oak_planks",
                     "post": "minecraft:block/stripped_oak_log"},
        "elements": elements(),
        # in a slot as large as it fits - the back's top a pixel and a half under the slot's top edge, the legs' feet on
        # its bottom one - so the chair is not lost among the blocks beside it (at 0.55 it looked small - a review,
        # 2026-10-05; AssetAudit holds every item inside its slot)
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1.3, 0], "scale": [0.6, 0.6, 0.6]}},
    }
    OUT.write_bytes((json.dumps(model, indent=2) + "\n").encode("utf-8"))
    print("wrote", OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
