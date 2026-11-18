"""Generate the Mob Bottle item textures and models from ASCII pixel maps.

The bottle is drawn as vanilla draws its glass: a light edge, lit on the upper left and shaded on the lower right,
clear inside, with a highlight. A lead is tied round its neck, with a loop to hold it by.

- mob_bottle.png           : the bottle, empty; also the glass over a filled one's contents
- mob_bottle_contents.png  : what fills it, in greys the item model tints in its mob's shell colour
- mob_bottle_spots.png     : the spots on it, tinted in the mob's spots colour
- mob_bottle_eyes.png      : two eyes looking out, tinted dark on a pale mob and light on a dark one

The two colours are the mob's spawn egg's (assets/nookworks/mob_bottle_colors.json, from gen_mob_bottle_colors.py;
see dev.nookworks.client.MobBottleColors). Also writes models/item/mob_bottle_filled.json and items/mob_bottle.json.

Run from the repo root:  python tools/textures/gen_mob_bottle.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/textures/item"

PALETTE = {
    ".": (0, 0, 0, 0),
    "O": (206, 232, 246, 255),     # glass edge, lit
    "o": (150, 190, 222, 255),     # glass edge
    "Q": (98, 136, 178, 255),      # glass edge, shaded
    "H": (246, 252, 255, 235),     # highlight
    "h": (220, 240, 252, 150),     # soft highlight
    "C": (196, 146, 96, 255),      # cork, lit
    "c": (158, 110, 66, 255),      # cork
    "k": (112, 74, 44, 255),       # cork, shaded
    "L": (190, 156, 110, 255),     # lead, lit
    "l": (140, 106, 70, 255),      # lead
    "m": (96, 70, 46, 255),        # lead, shaded
}

BOTTLE = [
    "................",
    "......CCCc......",
    "......Ccck..LL..",
    "......ckkk.L..l.",
    "......O..Q.L..m.",
    ".....LLLLLLlml..",
    ".....lmmmmlm....",
    "...OOh.....oQ...",
    "..OH.........Q..",
    "..OH.........Q..",
    "..O..........Q..",
    "..O..........Q..",
    "..o..........Q..",
    "...oo......QQ...",
    ".....QQQQQQ.....",
    "................",
]

# What fills the bottle, below a clear row of air: E its light top and side, e its body, d its shade; S the spots,
# Y the eyes. Greys, for the item model to tint.
FILL = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "...EEEEEEEEEe...",
    "...EeeeSeeeed...",
    "...eeYeeYeeed...",
    "...eSeeeeeeSd...",
    "...eeeeeSeedd...",
    ".....eeeddd.....",
    "................",
    "................",
]
GREYS = {"E": 255, "e": 214, "d": 150}


def build(rows, colours):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch in colours:
                img.putpixel((x, y), colours[ch])
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    build(BOTTLE, PALETTE).save(OUT / "mob_bottle.png")
    build(FILL, {ch: (g, g, g, 255) for ch, g in GREYS.items()}).save(OUT / "mob_bottle_contents.png")
    build(FILL, {"S": (240, 240, 240, 255)}).save(OUT / "mob_bottle_spots.png")
    build(FILL, {"Y": (255, 255, 255, 255)}).save(OUT / "mob_bottle_eyes.png")
    stale = OUT / "mob_bottle_filled.png"  # the glass of an earlier drawing, now mob_bottle.png's
    if stale.exists():
        stale.unlink()
    assets = OUT.parents[1]
    # The layers in the order they are drawn: the contents, their spots and eyes, then the glass over them.
    write_json(assets / "models/item/mob_bottle_filled.json", {"parent": "minecraft:item/generated", "textures": {
        "layer0": "nookworks:item/mob_bottle_contents", "layer1": "nookworks:item/mob_bottle_spots",
        "layer2": "nookworks:item/mob_bottle_eyes", "layer3": "nookworks:item/mob_bottle"}})
    tint = [{"type": "nookworks:captured_mob", "which": which} for which in ("shell", "spots", "eyes")]
    write_json(assets / "items/mob_bottle.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": "nookworks:captured_entity",
        "on_true": {"type": "minecraft:model", "model": "nookworks:item/mob_bottle_filled",
                    "tints": tint + [{"type": "minecraft:constant", "value": -1}]},
        "on_false": {"type": "minecraft:model", "model": "nookworks:item/mob_bottle"}}})
    print("wrote the mob bottle")
