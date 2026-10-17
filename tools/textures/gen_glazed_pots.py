"""Glazed Flower Pot: its textures (original pixel art), and a glazed twin of every vanilla potted plant.

- block/glazed_flower_pot.png      the pot's side: cream glaze, a darker foot, a lit rim
- block/glazed_flower_pot_top.png  looking in: the rim round dark soil
- models/block/glazed_flower_pot.json and glazed_potted_<plant>.json, their blockstates, the pot's item,
  and loot tables for the full pots (the glazed pot and the plant)

The plants are vanilla's own: each vanilla potted model is read out of the game jar (JSON only), its pot and soil
elements are dropped, and the glazed pot is put in their place. Plants sit a little lower in the glazed pot; its
rim hides their foot. Vanilla's textures are referenced by name, never copied.

Run from the repo root after a Gradle build has unpacked the game jar:  python tools/textures/gen_glazed_pots.py
"""
import json
import re
import zipfile
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
DATA = ROOT / "src/main/resources/data/nookworks"
# the game jar of the version gradle.properties builds against: a build/ that has known an older one keeps it too,
# and the first found was the old one (26.2's beside 26.3's, 2026-10-12)
NEO_VERSION = re.search(r"^neo_version=(\S+)", (ROOT / "gradle.properties").read_text(encoding="utf-8"), re.M).group(1)
JAR = ROOT / f"build/moddev/artifacts/minecraft-patched-{NEO_VERSION}.jar"

GLAZE = [(150, 138, 118), (196, 184, 160), (226, 216, 194), (242, 234, 214), (252, 248, 236)]
POOLED = (204, 188, 150)  # the glaze's lower edge, where it ran thick: a little deeper and warmer
CLAY = [(150, 106, 76), (126, 88, 62), (100, 70, 50)]  # the stoneware under the glaze: lit, body, shade
SOIL = [(46, 32, 22), (62, 44, 30), (80, 58, 38)]
# where the glaze stops, column by column across the face the model shows (x 4-11): a dipped glaze's edge, level but
# for two drips that ran on down a row (alternating rows read as a dotted line, not a glaze)
GLAZE_ENDS = {4: 13, 5: 13, 6: 14, 7: 13, 8: 13, 9: 13, 10: 14, 11: 13}


def px(img, x, y, c):
    img.putpixel((x, y), c + (255,))


def pot_side():
    """
    The side as the model samples it: the rim in rows 8-9, the body in rows 10-14, the foot in row 15. Lit from the
    left: a glint down the left of the curve, the right falling away. The glaze runs from the rim to a wavering line
    (GLAZE_ENDS), its last pixel thicker where it pooled; under it the bare stoneware, the foot a shade darker.
    """
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            level = 2 + (1 if x in (5, 6) else 0) - (1 if x >= 10 else 0)  # the curve: a glint left, shade right
            end = GLAZE_ENDS.get(x, 14)
            if y == 15:
                c = CLAY[2] if x >= 10 else CLAY[1]
            elif y == 8:
                c = GLAZE[4] if x < 10 else GLAZE[3]  # the lip, lit along its top
            elif y == 9:
                c = GLAZE[3] if x < 10 else GLAZE[2]
            elif y == 10:
                c = GLAZE[1] if x < 10 else GLAZE[0]  # the shadow under the lip
            elif y < end:
                c = GLAZE[max(0, min(4, level - (1 if y == end - 1 and y > 11 else 0)))]
            elif y == end:
                c = POOLED
            else:
                c = CLAY[0] if x < 10 else CLAY[1]
            px(img, x, y, c)
    # a glaze is glossy: only the lightest of patches in it, no grain; the lip's light and the pooled edge kept clean
    return texkit.finish(img, 4411, keep=(GLAZE[4], POOLED), clusters=0.3, grain=0.0, size=5, reach=1)


def pot_top():
    """
    Looking in (the model shows x 4-11): the rim, lit on its near edges and duller on its far ones, round the soil sunk
    under it - in the shadow of the wall the light comes over (north and west), crumbly, no two rows alike.
    """
    img = Image.new("RGBA", (16, 16))
    soil = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            if 4 < x < 11 and 4 < y < 11:
                px(soil, x, y, SOIL[1])
    soil = texkit.finish(soil, 4412, **texkit.ROUGH)
    for y in range(16):
        for x in range(16):
            if 4 < x < 11 and 4 < y < 11:
                c = SOIL[0] if x == 5 or y == 5 else soil.getpixel((x, y))[:3]  # the far walls' shadow
            elif x in (4, 11) or y in (4, 11):
                c = GLAZE[2] if x == 4 or y == 4 else GLAZE[4]  # the far side of the rim dull, the near side lit
            else:
                c = GLAZE[2]
            px(img, x, y, c)
    return img


SIDES = ("north", "east", "south", "west")


def pot_elements():
    return [
        {"from": [5, 0, 5], "to": [11, 1, 11], "faces": {d: {"texture": "#glazed", "uv": [5, 15, 11, 16]} for d in SIDES}},
        {"from": [4.5, 0, 4.5], "to": [11.5, 5, 11.5],
         "faces": {**{d: {"texture": "#glazed", "uv": [4.5, 10, 11.5, 15]} for d in SIDES},
                   "down": {"texture": "#glazed", "uv": [4.5, 4.5, 11.5, 11.5], "cullface": "down"}}},
        {"from": [4, 5, 4], "to": [12, 7, 12],
         "faces": {**{d: {"texture": "#glazed", "uv": [4, 8, 12, 10]} for d in SIDES},
                   "up": {"texture": "#glazed_top", "uv": [4, 4, 12, 12]},
                   "down": {"texture": "#glazed", "uv": [4, 4, 12, 12]}}},
    ]


POT_TEXTURES = {"glazed": "nookworks:block/glazed_flower_pot", "glazed_top": "nookworks:block/glazed_flower_pot_top"}


def resolve(jar, name):
    """A model with its parents' textures merged in and the nearest elements, as the game would load it."""
    textures, elements, ao = {}, None, None
    while name:
        path = name.split(":", 1)[-1]
        if not path.startswith("block/"):
            break
        try:
            model = json.loads(jar.read(f"assets/minecraft/models/{path}.json"))
        except KeyError:
            break
        for k, v in model.get("textures", {}).items():
            textures.setdefault(k, v)
        if elements is None and "elements" in model:
            elements = model["elements"]
        if ao is None and "ambientocclusion" in model:
            ao = model["ambientocclusion"]
        name = model.get("parent")
    return textures, elements or [], ao


def is_pot(element):
    faces = element.get("faces", {}).values()
    return faces and all(f.get("texture") in ("#flowerpot", "#dirt") for f in faces)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def glazed_model(textures, plant_elements, ao=False):
    model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
             "textures": {**textures, **POT_TEXTURES, "particle": POT_TEXTURES["glazed"]},
             "elements": pot_elements() + plant_elements}
    if ao is False:
        model["ambientocclusion"] = False
    return model


# The pot's item in a slot: block/block's 0.625 drew the pot (8 across, 7 high) 7 px across, a speck; at full size it is
# about 11 px across and high, as vanilla's flower pot picture is, lifted so it sits in the middle of the slot.
POT_GUI = {"gui": {"rotation": [30, 225, 0], "translation": [0, 4, 0], "scale": [1, 1, 1]}}


def main():
    pot_side().save(ASSETS / "textures/block/glazed_flower_pot.png")
    pot_top().save(ASSETS / "textures/block/glazed_flower_pot_top.png")
    write(ASSETS / "models/block/glazed_flower_pot.json", {**glazed_model({}, [], False), "display": POT_GUI})
    write(ASSETS / "blockstates/glazed_flower_pot.json", {"variants": {"": {"model": "nookworks:block/glazed_flower_pot"}}})
    write(ASSETS / "items/glazed_flower_pot.json", {"model": {"type": "minecraft:model", "model": "nookworks:block/glazed_flower_pot"}})
    write(DATA / "loot_table/blocks/glazed_flower_pot.json", {
        "type": "minecraft:block", "random_sequence": "nookworks:blocks/glazed_flower_pot",
        "pools": [{"rolls": 1, "bonus_rolls": 0.0, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": "nookworks:glazed_flower_pot"}]}]})
    count = 0
    with zipfile.ZipFile(JAR) as jar:
        potted = sorted(n.rsplit("/", 1)[-1][:-5] for n in jar.namelist()
                        if n.startswith("assets/minecraft/blockstates/potted_") and n.endswith(".json"))
        for name in potted:
            state = json.loads(jar.read(f"assets/minecraft/blockstates/{name}.json"))
            variant = next(iter(state["variants"].values()))
            variant = variant[0] if isinstance(variant, list) else variant
            textures, elements, ao = resolve(jar, variant["model"])
            plant = [e for e in elements if not is_pot(e)]
            write(ASSETS / f"models/block/glazed_{name}.json", glazed_model(
                {k: (v if ":" in v else "minecraft:" + v) for k, v in textures.items()}, plant, ao))
            write(ASSETS / f"blockstates/glazed_{name}.json", {"variants": {"": {"model": f"nookworks:block/glazed_{name}"}}})
            loot = json.loads(jar.read(f"data/minecraft/loot_table/blocks/{name}.json").decode("utf-8")
                              .replace('"minecraft:flower_pot"', '"nookworks:glazed_flower_pot"')
                              .replace("minecraft:blocks/", "nookworks:blocks/glazed_"))
            write(DATA / f"loot_table/blocks/glazed_{name}.json", loot)
            count += 1
    # our own plant: the Dawnwood sapling, drawn as a cross like vanilla's saplings
    textures, elements, ao = resolve_cross()
    write(ASSETS / "models/block/glazed_potted_dawnwood_sapling.json",
          glazed_model({"plant": "nookworks:block/dawnwood_sapling"}, elements, False))
    write(ASSETS / "blockstates/glazed_potted_dawnwood_sapling.json",
          {"variants": {"": {"model": "nookworks:block/glazed_potted_dawnwood_sapling"}}})
    write(DATA / "loot_table/blocks/glazed_potted_dawnwood_sapling.json", {
        "type": "minecraft:block", "random_sequence": "nookworks:blocks/glazed_potted_dawnwood_sapling",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": "nookworks:glazed_flower_pot"}]},
                  {"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": "nookworks:rare_seed"}]}]})
    print(f"wrote the glazed pot and {count + 1} potted plants")


def resolve_cross():
    with zipfile.ZipFile(JAR) as jar:
        textures, elements, ao = resolve(jar, "minecraft:block/flower_pot_cross")
    return textures, [e for e in elements if not is_pot(e)], ao


if __name__ == "__main__":
    main()
