"""The computed sounds as a resource pack, to hear them in a game that does not have them yet (the released jar, or a
checkout of another branch): dropped into that game's resourcepacks folder and switched on, it gives every sound event
gen_sounds.py makes files for those files.

    python tools/sounds/preview_pack.py OUT.zip [--nooktech PATH/TO/nook_technology/mod]

Only events the installed mod already has are heard: an event this branch adds (the Hand Quern's, the Wrench's...) is
played by this branch's code alone. Not part of a release - the jar carries the sounds itself.
"""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FIXED = (2026, 1, 1, 0, 0, 0)  # one date for every entry: the same zip each run


def add(z, name, data):
    info = zipfile.ZipInfo(name, FIXED)
    info.compress_type = zipfile.ZIP_DEFLATED
    z.writestr(info, data)


def pack_mod(z, assets, modid):
    table = json.loads((assets / "sounds.json").read_bytes())
    out = {}
    for event, entry in table.items():
        files = [s for s in entry["sounds"] if isinstance(s, dict) and s.get("type") != "event"]
        if len(files) != len(entry["sounds"]):
            continue  # it plays vanilla's sounds: nothing to carry
        out[event] = {"replace": True, "subtitle": entry["subtitle"], "sounds": files}
        for s in files:
            path = s["name"].split(":", 1)[1]
            add(z, f"assets/{modid}/sounds/{path}.ogg", (assets / "sounds" / f"{path}.ogg").read_bytes())
    add(z, f"assets/{modid}/sounds.json", (json.dumps(out, indent=2) + "\n").encode("utf-8"))
    return len(out)


def main(args):
    if not args:
        sys.exit(__doc__)
    out = Path(args[0])
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w") as z:
        meta = {"pack": {"description": "Nookworks: the new sounds, to try before they are in the mod", "min_format": 97, "max_format": [97, 1]}}
        add(z, "pack.mcmeta", (json.dumps(meta, indent=2) + "\n").encode("utf-8"))
        count = pack_mod(z, ROOT / "src/main/resources/assets/nookworks", "nookworks")
        if "--nooktech" in args:
            tec = Path(args[args.index("--nooktech") + 1])
            count += pack_mod(z, tec / "src/main/resources/assets/nooktech", "nooktech")
    print(f"{out}  {out.stat().st_size / 1e6:.1f} MB, {count} sound events")


if __name__ == "__main__":
    main(sys.argv[1:])
