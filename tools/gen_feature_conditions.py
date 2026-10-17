"""The nookworks:feature conditions on our recipes and their recipe-book unlocks, from src/main/resources/nookworks/features.json.

A pack can switch a part of the mod off (nookworks-startup.toml, [features]). Then no recipe makes an item of that
part, nor takes one in to make another: every recipe whose result is an item of a part carries a
{"type": "nookworks:feature", "feature": <part>} condition for each part it touches (result and named ingredients),
and so does the hidden advancement that unlocks it in the recipe book. A recipe whose result is of no part only uses
up what a world already holds (a Lode Pearl washed back into an ender pearl, risen dough baked) and stays, as what a
world holds stays and works. Conditions of other kinds a file already has are kept.

A file that gains its first condition gets the block put in at its top, the rest left as it was written; one whose
conditions change otherwise is written out whole. Line endings are kept. Run from the repo root after adding recipes
(gen_all.py runs it after gen_recipe_unlocks.py):  python tools/gen_feature_conditions.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/nookworks"
FEATURES = json.loads((ROOT / "src/main/resources/nookworks/features.json").read_text(encoding="utf-8"))
PART_OF = {item: part for part, items in FEATURES.items() if not part.startswith("_") for item in items}
ORDER = [part for part in FEATURES if not part.startswith("_")]


def named_items(value):
    """Item ids a recipe names (not #tags), anywhere in it but its result."""
    found = []
    if isinstance(value, str):
        if ":" in value and not value.startswith("#"):
            found.append(value)
    elif isinstance(value, list):
        for v in value:
            found += named_items(v)
    elif isinstance(value, dict):
        for k, v in value.items():
            if k not in ("result", "neoforge:conditions", "type", "category", "group", "show_notification"):
                found += named_items(v)
    return found


def result_of(recipe):
    r = recipe.get("result")
    return r if isinstance(r, str) else r.get("id") if isinstance(r, dict) else None


def parts_of(recipe):
    items = named_items(recipe)
    result = result_of(recipe)
    if result:
        if result not in PART_OF:
            return []  # it uses up a part's item for something of no part: what a world holds keeps working
        items.append(result)
    return sorted({PART_OF[i] for i in items if i in PART_OF}, key=ORDER.index)


def with_conditions(path, parts, extra=()):
    """Writes {path} with exactly these feature conditions, and {extra} (a recipe's other conditions, for its unlock);
    returns whether it changed."""
    raw = path.read_bytes().decode("utf-8")
    eol = "\r\n" if "\r\n" in raw else "\n"
    text = raw.replace("\r\n", "\n")
    data = json.loads(text)
    others = [c for c in data.get("neoforge:conditions", []) if c.get("type") != "nookworks:feature" and c not in extra]
    wanted = others + list(extra) + [{"type": "nookworks:feature", "feature": p} for p in parts]
    if data.get("neoforge:conditions", []) == wanted:
        return False
    if "neoforge:conditions" not in data and text.startswith("{\n"):
        block = json.dumps({"neoforge:conditions": wanted}, indent=2)[2:-2]  # the key and its list, indented as a member
        text = "{\n" + block + ",\n" + text[2:]
    else:
        if wanted:
            data = {"neoforge:conditions": wanted, **{k: v for k, v in data.items() if k != "neoforge:conditions"}}
        else:
            data.pop("neoforge:conditions", None)
        text = json.dumps(data, indent=2) + "\n"
    json.loads(text)  # still JSON
    path.write_bytes(text.replace("\n", eol).encode("utf-8"))
    return True


def main():
    changed = 0
    parts_by_recipe = {}
    settings_by_recipe = {}  # a recipe's own other conditions (a startup setting's, a part switched OFF): its unlock is loaded as it is
    for path in sorted((DATA / "recipe").rglob("*.json")):
        recipe = json.loads(path.read_text(encoding="utf-8"))
        parts = parts_of(recipe)
        name = "nookworks:" + path.relative_to(DATA / "recipe").with_suffix("").as_posix()
        parts_by_recipe[name] = parts
        settings_by_recipe[name] = [c for c in recipe.get("neoforge:conditions", []) if c.get("type") != "nookworks:feature"]
        changed += with_conditions(path, parts)
    for path in sorted((DATA / "advancement/recipes").rglob("*.json")):
        unlock = json.loads(path.read_text(encoding="utf-8"))
        rewards = unlock.get("rewards", {}).get("recipes", [])
        parts = sorted({p for r in rewards for p in parts_by_recipe.get(r, [])}, key=ORDER.index)
        extra = []
        for r in rewards:
            for c in settings_by_recipe.get(r, []):
                if c not in extra:
                    extra.append(c)
        changed += with_conditions(path, parts, extra)
    gated = sum(1 for p in parts_by_recipe.values() if p)
    print(f"{gated} of {len(parts_by_recipe)} recipes belong to a part that can be switched off; {changed} files changed")


if __name__ == "__main__":
    main()
