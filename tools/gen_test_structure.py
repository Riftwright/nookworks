"""Write the GameTest arena structure: a stone floor with air above.

Run from the repo root:  python tools/gen_test_structure.py
Output: src/main/resources/data/nookworks/structure/arena.nbt
"""
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/data/nookworks/structure/arena.nbt"
DATA_VERSION = 4903  # Minecraft 26.2 world_version: kept - the arena written at 26.3's 5023 left the tests' blocks
                     # unplaced (2026-10-12); 26.3 upgrades this one as it loads it
SIZE = (7, 10, 7)

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def _name(n):
    b = n.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _payload(tag, value):
    if tag == TAG_INT:
        return struct.pack(">i", value)
    if tag == TAG_STRING:
        return _name(value)
    if tag == TAG_LIST:
        elem_tag, items = value
        return struct.pack(">bi", elem_tag if items else TAG_END, len(items)) + b"".join(_payload(elem_tag, i) for i in items)
    if tag == TAG_COMPOUND:
        return b"".join(struct.pack(">b", t) + _name(k) + _payload(t, v) for k, (t, v) in value.items()) + b"\x00"
    raise ValueError(tag)


def compound(**fields):
    return (TAG_COMPOUND, fields)


def main():
    palette = [
        {"Name": (TAG_STRING, "minecraft:stone")},
        {"Name": (TAG_STRING, "minecraft:air")},
    ]
    blocks = []
    sx, sy, sz = SIZE
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                blocks.append({
                    "pos": (TAG_LIST, (TAG_INT, [x, y, z])),
                    "state": (TAG_INT, 0 if y == 0 else 1),
                })
    root = {
        "DataVersion": (TAG_INT, DATA_VERSION),
        "size": (TAG_LIST, (TAG_INT, list(SIZE))),
        "palette": (TAG_LIST, (TAG_COMPOUND, palette)),
        "blocks": (TAG_LIST, (TAG_COMPOUND, blocks)),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
    }
    data = struct.pack(">b", TAG_COMPOUND) + _name("") + _payload(TAG_COMPOUND, root)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(gzip.compress(data))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
