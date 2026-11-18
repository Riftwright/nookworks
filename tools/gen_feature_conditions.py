"""The nookworks:available conditions on our recipes and their recipe-book unlocks, from what can go when parts of the mod
are switched off (tools/part_graph.py).

A pack can switch a part of the mod off (nookworks-startup.toml, [features]), and a world can leave parts out. Then no
recipe makes an item of that part, or one nothing reaches any more without it (caustic soda without the Electrolytic
Cell), nor takes one in to make another: every recipe whose result can go carries
{"type": "nookworks:available", "items": [...]} naming its result and the items of ours it names that can go, and so
does the hidden advancement that unlocks it in the recipe book. A recipe whose result is vanilla's, or one of ours that
is always there, only uses up what a world already holds (a Lode Pearl washed back into an ender pearl, risen dough
baked) and stays, as what a world holds stays and works. Conditions of other kinds a file already has are kept; the
nookworks:feature conditions this tool wrote before 1.3 give way to the new one.

A file that gains its first condition gets the block put in at its top, the rest left as it was written; one whose
conditions change otherwise is written out whole. Line endings are kept. Run from the repo root after adding recipes
(gen_all.py runs it after gen_recipe_unlocks.py; part_graph.py first):  python tools/gen_feature_conditions.py
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import part_graph  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/nookworks"
OURS = ("nookworks:feature", "nookworks:available")


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


def items_of(recipe, can_go):
    """What a recipe's condition names: its result, if it can go, and then the items of ours it names that can."""
    result = result_of(recipe)
    if result not in can_go:
        return []  # vanilla's, or ours always there: what a world holds keeps working
    found = [result]
    for i in named_items(recipe):
        if i in can_go and i not in found:
            found.append(i)
    return found


def with_conditions(path, items, extra=()):
    """Writes {path} with exactly this availability condition (none for no items), and {extra} (a recipe's other
    conditions, for its unlock); returns whether it changed."""
    raw = path.read_bytes().decode("utf-8")
    eol = "\r\n" if "\r\n" in raw else "\n"
    text = raw.replace("\r\n", "\n")
    data = json.loads(text)
    others = [c for c in data.get("neoforge:conditions", []) if c.get("type") not in OURS and c not in extra]
    wanted = others + list(extra) + ([{"type": "nookworks:available", "items": list(items)}] if items else [])
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
    graph = {"sources": part_graph.sources()}
    can_go = set(part_graph.can_go(graph))
    changed = 0
    items_by_recipe = {}
    settings_by_recipe = {}  # a recipe's own other conditions (a startup setting's, a part switched OFF): its unlock is loaded as it is
    for path in sorted((DATA / "recipe").rglob("*.json")):
        recipe = json.loads(path.read_text(encoding="utf-8"))
        items = items_of(recipe, can_go)
        name = "nookworks:" + path.relative_to(DATA / "recipe").with_suffix("").as_posix()
        items_by_recipe[name] = items
        settings_by_recipe[name] = [c for c in recipe.get("neoforge:conditions", []) if c.get("type") not in OURS]
        changed += with_conditions(path, items)
    for path in sorted((DATA / "advancement/recipes").rglob("*.json")):
        unlock = json.loads(path.read_text(encoding="utf-8"))
        rewards = unlock.get("rewards", {}).get("recipes", [])
        items = []
        extra = []
        for r in rewards:
            for i in items_by_recipe.get(r, []):
                if i not in items:
                    items.append(i)
            for c in settings_by_recipe.get(r, []):
                if c not in extra:
                    extra.append(c)
        changed += with_conditions(path, items, extra)
    gated = sum(1 for i in items_by_recipe.values() if i)
    print(f"{gated} of {len(items_by_recipe)} recipes make what parts switched off can take away; {changed} files changed")


if __name__ == "__main__":
    main()
