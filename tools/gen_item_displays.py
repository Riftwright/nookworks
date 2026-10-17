"""How the low and small blocks look held, on the ground and in an item frame.

A block item without display transforms of its own takes vanilla's block/block ones, which are made for a full cube:
a model 1 to 11 pixels high standing on the block's floor (the Absorption Pad, the Dice, the Iron Pot...) is then held
low in the corner of the screen, mostly out of sight, and sits in the bottom of an item frame. Vanilla does the same
for its carpets and pressure plates with block/thin_block, which lifts them in first person.

This works each such model's transforms out of its size, the way block/thin_block is set up: the model's middle is
brought to where a full block's would be (in first person a little higher still, as vanilla lifts a carpet), and a
small one is held a little bigger - up to half as big again - so a die in the hand is not a speck. The gui transform,
which the models set themselves where they need one, is left alone, as is any model with first-person transforms of
its own (the Sconce, the Filament Bulb).

A pipe or a duct drawn as a straight run north to south - a rod along z - is turned side on in an item frame, which
shows a model from the south: there it was only its end, a small square (the Experience Pipe a dot).

It writes into the models the item definitions point at, which other generators write too: run it after them.
AssetAudit fails when a low model has lost its transforms. Run from the repo root:  python tools/gen_item_displays.py
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/nookworks"

# vanilla's block/block, which the rest is worked from
FIRST = {"rotation": [0, 45, 0], "scale": 0.4}
THIRD = {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": 0.375}
GROUND = {"translation": [0, 3, 0], "scale": 0.25}
FIXED = {"scale": 0.5}
LIFT = 1.4  # how much further than the middle vanilla lifts a thin block in first person (4.2 for a carpet's 3.0)
# items whose models carry transforms set by hand, left as they are
HAND_SET = {"sconce", "filament_bulb"}


def model_ref(node):
    """The first model an item definition shows."""
    if isinstance(node, dict):
        if node.get("type") in ("minecraft:model", "model") and "model" in node:
            return node["model"]
        for value in node.values():
            found = model_ref(value)
            if found:
                return found
    elif isinstance(node, list):
        for value in node:
            found = model_ref(value)
            if found:
                return found
    return None


def model_path(ref):
    namespace, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    return ASSETS / "models" / (path + ".json") if namespace == "nookworks" else None


def elements_of(ref):
    """The model's elements, from it or the nearest parent of ours that has them."""
    while ref:
        path = model_path(ref)
        if path is None or not path.exists():
            return None
        model = json.loads(path.read_text(encoding="utf-8"))
        if "elements" in model:
            return model["elements"]
        ref = model.get("parent")
    return None


def low(elements):
    """Whether the model stands on the floor and is small: what block/block's transforms show badly."""
    lo = [min(e["from"][i] for e in elements) for i in range(3)]
    hi = [max(e["to"][i] for e in elements) for i in range(3)]
    height = hi[1] - lo[1]
    across = max(hi[0] - lo[0], hi[2] - lo[2])
    return lo[1] <= 1 and (height < 12 or across < 10), lo, hi


def rod_along_z(elements):
    """Whether the model is a straight run along z, a block long and thin across: a pipe, a duct."""
    lo = [min(e["from"][i] for e in elements) for i in range(3)]
    hi = [max(e["to"][i] for e in elements) for i in range(3)]
    return hi[2] - lo[2] >= 15.9 and hi[0] - lo[0] <= 12 and hi[1] - lo[1] <= 12


def round2(v):
    return [round(x, 2) + 0.0 for x in v]


def displays(lo, hi):
    """The transforms for a model spanning lo..hi (pixels)."""
    middle = (lo[1] + hi[1]) / 2
    drop = 8 - middle  # how far below a full block's middle this model's is
    size = max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2])
    grow = min(1.5, max(1.0, 16 / size))
    first = FIRST["scale"] * grow
    third = THIRD["scale"] * grow
    tilt = math.radians(THIRD["rotation"][0])
    out = {
        "firstperson_righthand": {"rotation": FIRST["rotation"], "translation": round2([0, drop * first * LIFT, 0]),
                                  "scale": round2([first] * 3)},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": round2([0, drop * first * LIFT, 0]),
                                 "scale": round2([first] * 3)},
        # held at the tilt: the model's middle, lifted, would swing out along the arm; it is brought back into the hand
        "thirdperson_righthand": {"rotation": THIRD["rotation"],
                                  "translation": round2([0, THIRD["translation"][1] + drop * third * math.cos(tilt),
                                                         drop * third * math.sin(tilt)]),
                                  "scale": round2([third] * 3)},
        # on the ground an item stands on its bottom, so only a small one is made bigger, not lifted
        "ground": {"rotation": [0, 0, 0], "translation": round2([0, GROUND["translation"][1] - (grow - 1) * 2, 0]),
                   "scale": round2([GROUND["scale"] * grow] * 3)},
        "fixed": {"rotation": [0, 0, 0], "translation": round2([0, drop * FIXED["scale"], 0]), "scale": round2([FIXED["scale"]] * 3)},
    }
    return out


def main():
    written = []
    for item in sorted((ASSETS / "items").glob("*.json")):
        if item.stem in HAND_SET:
            continue
        ref = model_ref(json.loads(item.read_text(encoding="utf-8"))["model"])
        path = model_path(ref) if ref else None
        if path is None or not path.exists():
            continue
        model = json.loads(path.read_text(encoding="utf-8"))
        own = model.get("display", {})
        elements = elements_of(ref)
        if not elements:
            continue
        is_low, lo, hi = low(elements)
        if is_low:
            display = dict(own)
            display.update(displays(lo, hi))
        elif rod_along_z(elements):
            display = dict(own)
            display["fixed"] = {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": round2([FIXED["scale"]] * 3)}
        else:
            continue
        model["display"] = {k: display[k] for k in sorted(display)}
        path.write_bytes((json.dumps(model, indent=2) + "\n").encode("utf-8"))
        written.append(item.stem)
    print("item transforms for: " + ", ".join(written))


if __name__ == "__main__":
    main()
