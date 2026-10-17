"""Generate the Nook Dowsing Rod (all original pixel art). Deterministic.

A forked wooden rod, a Y held by its two arms, with a small copper dial at its tip, and in the dial a needle tipped in
cinnabar red. The rod at rest (nook_dowsing_rod.png: the needle hanging down) and its 32 needle frames (nook_dowsing_rod_00..31.png), in
the order vanilla's compass frames go: frame 16 points straight up - at what it found - and each next one turns the
needle 11.25 degrees clockwise. Their models are written too, and items/nook_dowsing_rod.json, which picks the frame by
vanilla's own compass angle to the rod's lodestone_tracker (as a lodestone compass does), and the rest pose without one.

The rod and dial are drawn flat and finished with texkit (the dial's face kept flat, as a compass face is); the needle
is drawn over the finished base, crisp, so each frame is the base and only its needle.

Run from the repo root:  python tools/textures/gen_nook_dowsing.py
"""
import json
import math
from pathlib import Path

from PIL import Image

import texkit

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
OUT = ASSETS / "textures/item"

CLEAR = (0, 0, 0, 0)
WOOD = {"light": (150, 112, 64), "mid": (114, 84, 47), "dark": (82, 59, 33)}
COPPER = {"light": (232, 152, 104), "mid": (196, 112, 74), "dark": (142, 76, 50)}
FACE = (40, 37, 46)
TIP = (214, 48, 40)       # cinnabar
TIP_END = (246, 104, 82)  # where it catches the light
TAIL = (156, 154, 166)
PIVOT = (232, 152, 104)

CENTRE = (8, 5)
RIM_OUT, RIM_IN = 4.4, 3.2


def rgba(c):
    return c + (255,)


def base():
    img = Image.new("RGBA", (16, 16), CLEAR)
    put = lambda x, y, c: img.putpixel((x, y), rgba(c))
    # the Y: two arms, held one in each hand, running down from the dial at its tip; each two wide, lit on its
    # upper side
    for t in range(6):
        put(5 - t, 9 + t, WOOD["light"])
        put(6 - t, 9 + t, WOOD["mid"]) if t < 5 else None
        put(10 + t, 9 + t, WOOD["mid"])
        put(11 + t, 9 + t, WOOD["dark"]) if t < 5 else None
    # the dial: a copper rim lit at its upper left, dark at its lower right, round a dark face
    cx, cy = CENTRE
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d < RIM_IN:
                put(x, y, FACE)
            elif d < RIM_OUT:
                light = (x - cx) + (y - cy)  # negative towards the upper left
                put(x, y, COPPER["light"] if light < -2 else COPPER["dark"] if light > 2 else COPPER["mid"])
    return texkit.icon(img, 5207, keep=(FACE,))


def needle(img, frame):
    """The needle at {@code frame}: 16 up, turning clockwise 11.25 degrees a frame. Tip red, tail grey."""
    angle = math.radians((frame - 16) * 11.25)
    dx, dy = math.sin(angle), -math.cos(angle)
    cx, cy = CENTRE
    out = img.copy()
    for step, colour in ((-1.6, TAIL), (-0.8, TAIL), (0.9, TIP), (1.8, TIP), (2.6, TIP_END)):
        x = round(cx + dx * step)
        y = round(cy + dy * step)
        out.putpixel((x, y), rgba(colour))
    out.putpixel((cx, cy), rgba(PIVOT))
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    finished = base()
    needle(finished, 0).save(OUT / "nook_dowsing_rod.png")  # at rest, hanging down
    for frame in range(32):
        needle(finished, frame).save(OUT / f"nook_dowsing_rod_{frame:02d}.png")
    models()
    print("wrote the Nook Dowsing Rod and its 32 frames")


def models():
    """A plain item model a frame, and the item definition choosing among them as vanilla's compass.json does."""
    names = ["nook_dowsing_rod"] + [f"nook_dowsing_rod_{frame:02d}" for frame in range(32)]
    for name in names:
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/{name}"}}
        path = ASSETS / "models/item" / f"{name}.json"
        if not path.exists():  # gen_item_displays.py adds its transforms to what is there: written only once
            path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8", newline="\n")
    # angle 0 (the needle at what was found) is frame 16, as vanilla's compass: entry i, from threshold i - 0.5, is
    # frame (i + 16) mod 32, and the last wraps round to 16 again
    entries = [{"model": {"type": "minecraft:model", "model": f"nookworks:item/nook_dowsing_rod_{(i + 16) % 32:02d}"},
                "threshold": 0.0 if i == 0 else i - 0.5} for i in range(33)]
    definition = {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:has_component",
        "component": "minecraft:lodestone_tracker",
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone",
                    "scale": 32.0, "entries": entries},
        "on_false": {"type": "minecraft:model", "model": "nookworks:item/nook_dowsing_rod"},
    }}
    (ASSETS / "items/nook_dowsing_rod.json").write_text(json.dumps(definition, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
