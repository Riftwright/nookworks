"""The Sifter's fine-mesh tables, derived from its ordinary ones.

With a Brass Mesh fitted the Sifter rolls data/nookworks/loot_table/sifting_fine/<ns>/<item>.json instead of
sifting/<ns>/<item>.json. A sieve keeps what is bigger than its holes. The ordinary net keeps the coarse things
(flint, bones, clay) and loses the small ones through its wide holes with the dirt; the fine mesh keeps the coarse
things just the same and holds on to the small ones as well. So each fine table is its ordinary table with:

- SMALL things (nuggets, raw zinc, gems, seeds) weighing more,
- everything else weighing what it did, at the same chance as before,
- the empty roll (the ground that falls through) giving up the difference.

Edit the ordinary tables, then run this again from the repo root:  python tools/gen_fine_sifting.py
Tables with nothing small in them (Crude Salt) get no fine version. A datapack can still ship its own
sifting_fine table; an input with none just sifts as usual through the mesh.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TABLES = ROOT / "src/main/resources/data/nookworks/loot_table"

SMALL = {
    "nookworks:raw_zinc": 3.0,
    "minecraft:iron_nugget": 2.0, "minecraft:gold_nugget": 2.0, "minecraft:copper_nugget": 2.0,
    "minecraft:lapis_lazuli": 2.5, "minecraft:amethyst_shard": 2.5, "minecraft:emerald": 2.5,
    "minecraft:diamond": 2.5, "minecraft:quartz": 2.5, "minecraft:glowstone_dust": 2.0,
    "nookworks:rare_seed": 2.0, "minecraft:pitcher_pod": 2.0, "nookworks:brown_rice": 1.5,
    "minecraft:wheat_seeds": 1.5, "minecraft:beetroot_seeds": 1.5, "minecraft:pumpkin_seeds": 1.5, "minecraft:melon_seeds": 1.5,
    "nookworks:corn_kernels": 1.5, "nookworks:sunflower_seeds": 1.5,
}
# The share of rolls that still come up empty through the fine mesh, however much small stuff a table has.
MIN_EMPTY = 0.15


def finer(table):
    for pool in table["pools"]:
        entries = pool["entries"]
        total = sum(e.get("weight", 1) for e in entries)
        base = {}
        for entry in entries:
            if entry["type"] != "minecraft:empty" and entry.get("name") in SMALL:
                base[id(entry)] = entry.get("weight", 1)
                entry["weight"] = max(1, round(entry.get("weight", 1) * SMALL[entry["name"]]))
        # The small things kept come out of what used to fall through, so everything else keeps its chance - but at
        # least MIN_EMPTY of every roll still comes up empty: where the gains would eat into that, they are scaled
        # back until they fit - each by at least one, so whatever the mesh keeps does come up more often (gravel's
        # diamond went back to its 1 in 1080, against the Brass Mesh's page).
        empty = next((e for e in entries if e["type"] == "minecraft:empty"), None)
        if empty is not None:
            floor = round(total * MIN_EMPTY)
            room = max(0, empty.get("weight", 1) - floor)
            gains = {id(e): e["weight"] - base[id(e)] for e in entries if id(e) in base}
            gained = sum(gains.values())
            if gained > room:
                for entry in entries:
                    if id(entry) in gains:
                        gain = gains[id(entry)]
                        entry["weight"] = base[id(entry)] + (max(1, int(gain * room / gained)) if gain > 0 else 0)
            kept = sum(e.get("weight", 1) for e in entries if e is not empty)
            empty["weight"] = max(1, total - kept)
    if "random_sequence" in table:
        table["random_sequence"] = table["random_sequence"].replace("nookworks:sifting/", "nookworks:sifting_fine/")
    return table


def main():
    written = 0
    for source in sorted((TABLES / "sifting").rglob("*.json")):
        out = TABLES / "sifting_fine" / source.relative_to(TABLES / "sifting")
        table = json.loads(source.read_text(encoding="utf-8"))
        if not any(e.get("name") in SMALL for pool in table["pools"] for e in pool["entries"]):
            continue  # nothing small to keep (washing salt): the mesh would only slow it down
        table = finer(table)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(table, indent=2) + "\n", encoding="utf-8")
        written += 1
    print(f"wrote {written} fine sifting tables")


if __name__ == "__main__":
    main()
