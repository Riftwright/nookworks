"""How much of each food a Plate and a Wooden Tray hold: data map nookworks:serving (dev.nookworks.cooking.Serving),
food by food (the user, 2026-11-14: お皿やトレイにおける数は、品目によって1つずつ見直して最適化して欲しいな。クッキーなら
お皿に数個置けてもいいと思う). Deterministic.  python tools/gen_servings.py [--table]

Each food's line below is a decision, made looking at its model on the table (models/dish/<namespace>/<item>.json):
  plate  how many of it a Plate holds (one kind to a plate)
  room   what one lot of it takes of a tray's board: quarter, half (a long thing lies across it), whole (none now:
         a plate on a tray is drawn as small as its quarter needs)
  tray   how many of it lie together in that room
A food not listed is one to a plate and one to a quarter of a tray, as every food was.

The numbers are checked against the models, by the layout the game uses (client/render/TableLots.places, kept the same
here): the pieces of a lot must lie flat, side by side, inside their room - unless the food is one that is piled (PILED:
doughnuts in a tower) - and one piece alone must fit its room without being drawn smaller than SMALLEST allows. So a
model redrawn larger, or a number raised too far, stops this script rather than showing as food run together in game.
--table prints the whole table, for a report.
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / "src/main/resources/assets/nookworks/models/dish"
OUT = ROOT / "src/main/resources/data/nookworks/data_maps/item/serving.json"

# name: (plate, room, tray)
Q, H, W = "quarter", "half", "whole"
SERVINGS = {
    # vanilla: fruit and roots, a couple to a plate
    "minecraft:apple": (2, Q, 1),
    "minecraft:golden_apple": (2, Q, 1),
    "minecraft:enchanted_golden_apple": (2, Q, 1),
    "minecraft:beetroot": (2, Q, 1),
    "minecraft:chorus_fruit": (2, Q, 1),
    "minecraft:potato": (3, Q, 1),
    "minecraft:baked_potato": (2, Q, 1),
    "minecraft:poisonous_potato": (2, Q, 1),
    "minecraft:sweet_berries": (2, Q, 1),
    "minecraft:glow_berries": (2, Q, 1),
    # long things lie across half a tray; carrots three abreast
    "minecraft:carrot": (3, H, 3),
    "minecraft:golden_carrot": (3, H, 3),
    "minecraft:cod": (1, H, 1),
    "minecraft:salmon": (1, H, 1),
    "minecraft:cooked_cod": (1, H, 1),
    "minecraft:cooked_salmon": (1, H, 1),
    # baking: two loaves to a plate; cookies five to a plate, and sixteen to a tray like a baking sheet
    "minecraft:bread": (2, Q, 1),
    "minecraft:cookie": (5, Q, 4),
    # a whole cake on a plate, or across half a tray (the user, 2026-11-14: ケーキだけは皿に載せられない…悲しいな)
    "minecraft:cake": (1, H, 1),
    # ours: pickles and small sides
    "nookworks:beetroot_nukazuke": (2, Q, 2),
    "nookworks:carrot_nukazuke": (3, Q, 2),
    "nookworks:boiled_egg": (2, Q, 1),
    "nookworks:onion": (2, Q, 1),
    "nookworks:salt_onigiri": (3, Q, 1),
    "nookworks:salmon_onigiri": (3, Q, 1),
    "nookworks:tamagoyaki": (2, H, 2),
    "nookworks:corn": (2, H, 2),
    "nookworks:grilled_corn": (2, H, 2),
    "nookworks:donut": (6, Q, 2),
    # ours: main dishes, each a serving with its trimmings - one to a plate, half a tray
    "nookworks:tonkatsu": (1, H, 1),
    "nookworks:karaage": (1, H, 1),
    "nookworks:fried_fish": (1, H, 1),
    "nookworks:dried_fish": (1, H, 1),
}
# piled one on another where they do not lie side by side: doughnuts in a tower (the user, 2026-11-14, of three to a
# plate: 3個でいいよ。なんならドーナッツタワーみたいにたくさん重ねられても面白いかも - so six, as tall as stays inside the
# plate's own block)
PILED = {"nookworks:donut"}
# on a plate, leant one on the next (the user, 2026-11-14, with a photograph of cookies on a plate: ちょっと重なる感じに
# したほうがおしゃれ) - the first flat, the others standing on an edge against it; on a tray they lie flat
LEANING = {"minecraft:cookie"}

# as client/render/TableLots and PlateRenderer have them
GAP, SMALLEST = 0.15, 0.78
LEAN, CLEAR, LEAN_ROW, ROW_GAP, ROW_SHIFT = 18, 0.06, 3, 0.8, 0.7
PLATE_ROOM, PLATE_SCALE = 10.6, 0.85
BOARD, SHARED = 12.0, 0.72


def size(item):
    """A dish model's reach: (across, deep, high) in pixels, element rotations about one axis taken into account."""
    namespace, name = item.split(":")
    path = MODELS / namespace / f"{name}.json"
    if not path.exists():
        return None
    lo, hi = [math.inf] * 3, [-math.inf] * 3
    for element in json.loads(path.read_text(encoding="utf-8"))["elements"]:
        corners = [[element["from" if (k >> axis) & 1 == 0 else "to"][axis] for axis in range(3)] for k in range(8)]
        turn = element.get("rotation")
        if turn:
            a, b = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[turn["axis"]]
            angle = math.radians(turn["angle"])
            for corner in corners:
                u, v = corner[a] - turn["origin"][a], corner[b] - turn["origin"][b]
                corner[a] = turn["origin"][a] + u * math.cos(angle) - v * math.sin(angle)
                corner[b] = turn["origin"][b] + u * math.sin(angle) + v * math.cos(angle)
        for corner in corners:
            lo = [min(x, y) for x, y in zip(lo, corner)]
            hi = [max(x, y) for x, y in zip(hi, corner)]
    return hi[0] - lo[0], hi[2] - lo[2], hi[1]


def plate_height(item):
    """How tall one of item stands on a Plate, in pixels, a tenth at most short: its model's height at the plate's scale -
    or, leant on the one before (cookies), its width tipped LEAN degrees up, and its thickness."""
    w, d, high = size(item)
    t = high * PLATE_SCALE
    if item in LEANING:
        tip = math.radians(LEAN)
        return math.ceil((w * PLATE_SCALE * math.sin(tip) + t * math.cos(tip)) * 10) / 10
    return math.ceil(t * 10) / 10


def flat(count, w, d, room_w, room_d):
    """Whether count pieces of w by d lie side by side in the room (TableLots.places lays what is over this on top)."""
    across = max(1, int((room_w + GAP) / (w + GAP)))
    deep = max(1, int((room_d + GAP) / (d + GAP)))
    return count <= across * deep


def check(item, plate, room, tray):
    reach = size(item)
    if reach is None:
        return [f"{item}: no model under models/dish"]
    w, d, high = reach
    wrong = []
    if item in LEANING:
        # as TableLots.leaning lays them: rows of up to three - the flat one, one leaning on it, one on that - one row
        # before another, each a little aside of the last
        tip, t = math.radians(LEAN), high * PLATE_SCALE
        rows = -(-plate // LEAN_ROW)
        most = -(-plate // rows)
        span = w * PLATE_SCALE + (t / math.tan(tip) + CLEAR + (most - 2) * (t / math.sin(tip) + CLEAR) + t * math.sin(tip) if most > 1 else 0)
        span += (rows - 1) * ROW_SHIFT
        deep = rows * d * PLATE_SCALE + (rows - 1) * ROW_GAP + 0.6
        if span > PLATE_ROOM or deep > PLATE_ROOM:
            wrong.append(f"{item}: {plate} leaning take {span:.1f} x {deep:.1f} px of a plate's {PLATE_ROOM}")
    elif plate > 1 and item not in PILED and not flat(plate, w * PLATE_SCALE, d * PLATE_SCALE, PLATE_ROOM, PLATE_ROOM):
        wrong.append(f"{item}: {plate} do not lie flat on a plate ({w:.1f} x {d:.1f})")
    if item in PILED and 1 + plate * high * PLATE_SCALE > 16:
        wrong.append(f"{item}: a pile of {plate} stands {1 + plate * high * PLATE_SCALE:.1f} px, out of the plate's block")
    room_w = BOARD if room != Q else BOARD / 2
    room_d = BOARD if room == W else BOARD / 2
    scale = 1.0 if room == W else SHARED
    if tray > 1 and item not in PILED and not flat(tray, w * scale, d * scale, room_w, room_d):
        wrong.append(f"{item}: {tray} do not lie flat in a {room} of a tray")
    if tray == 1 and min((room_w - GAP) / (w * scale), (room_d - GAP) / (d * scale)) < SMALLEST:
        wrong.append(f"{item}: too large for a {room} of a tray ({w * scale:.1f} x {d * scale:.1f})")
    return wrong


def main():
    wrong = [line for item, serving in SERVINGS.items() for line in check(item, *serving)]
    # and what is not listed: one to a quarter
    for path in sorted(MODELS.glob("*/*.json")):
        item = f"{path.parent.name}:{path.stem}"
        if item not in SERVINGS:
            wrong += check(item, 1, Q, 1)
    if wrong:
        print("\n".join(wrong))
        return 1
    values = {}
    for item, (plate, room, tray) in SERVINGS.items():
        entry = {}
        if plate != 1:
            entry["plate"] = plate
        if room != Q:
            entry["room"] = room
        if tray != 1:
            entry["tray"] = tray
        if item in LEANING:
            entry["leaning"] = True
        values[item] = entry
    # and every food with a model: how tall one stands on a Plate, as drawn there - the plate's shape is that tall, to
    # stand on and to click (the user, 2026-11-14, play-testing: a doughnut tower had none - 「埋まってしまう」)
    for path in sorted(MODELS.glob("*/*.json")):
        item = f"{path.parent.name}:{path.stem}"
        entry = values.setdefault(item, {})
        entry["height"] = plate_height(item)
        if item in PILED:
            entry["piled"] = True
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes((json.dumps({"values": values}, indent=2) + "\n").encode("utf-8"))
    print(f"servings: {len(values)} foods")
    if "--table" in sys.argv:
        for path in sorted(MODELS.glob("*/*.json")):
            item = f"{path.parent.name}:{path.stem}"
            plate, room, tray = SERVINGS.get(item, (1, Q, 1))
            lots = {Q: 4, H: 2, W: 1}[room]
            w, d, h = size(item)
            print(f"{item:42s} {w:5.1f} x {d:4.1f}  plate {plate}  tray {lots} x {tray} = {lots * tray}  ({room})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
