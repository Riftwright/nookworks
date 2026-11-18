"""Every generated file of the mod made again from its script, in an order that works.

Run from the repo root after a Gradle build (three texture scripts and the wood data read the game jar):
    python tools/gen_all.py
Afterwards nothing under src/ should differ from what is staged (or, with nothing staged, committed). A file that
does was either edited by hand - put the change into its script, or list it in tools/textures/HAND_FINISHED.md if it
is artwork no script draws - or its script was changed and not run.

The order: the texture scripts (tools/textures/gen_*.py) by name, as none reads what another writes; then the data
scripts; then gen_item_displays.py, last because it adds the in-hand and in-frame transforms to the item models the
others write bare. Left out: make_icon.py and make_logo.py (the desktop shortcut's icon and the page's logo, not the
mod's files) import_posters.py (it takes the poster packs the author supplies, by hand, one at a time),
tools/sounds/make_sounds.py (it cuts two sounds out of Kenney's packs, downloaded by hand) and
tools/sounds/gen_sounds.py (it computes the other sounds and needs ffmpeg; the same bytes on one machine, but another
numpy, scipy or ffmpeg may round a sample differently, so its files are kept as committed and made again on purpose).

1.21.1: the generators still write 26.3's formats, so in the port each is followed by
tools/port1211/convert_resources.py, which turns what it wrote into 1.21.1's (item definitions into models/item,
recipes, tags, the ids 1.21.1 lacks left out): the next one reads our files as the port has them, as several do (the
recipes, the part table). Then the converter's --check against 1.21.1's own data, and part_graph.py last.
The scripts that read vanilla's files (three texture scripts and the wood data) read 26.3's, as the converter expects:
the port's build has no 26.3 game jar, so they take 26.3's vanilla client jar out of Gradle's cache.
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# gen_recycling.py after the machine models: it builds the Recycler's item model from its two halves
DATA = ["gen_machine_models.py", "gen_recycling.py", "gen_rope_ties.py", "gen_wood_data.py", "gen_fine_sifting.py",
        "gen_advancements.py", "gen_recipe_unlocks.py", "gen_feature_conditions.py", "gen_test_structure.py", "gen_servings.py",
        "gen_lang_copies.py"]
LAST = "gen_item_displays.py"
# 1.21.1: the converter to 1.21.1's formats, run after each generator; at the end its check against 1.21.1's own data,
# then the part table last: it reads what all the others write, as converted
CONVERT = ["port1211/convert_resources.py"]
PORT = [CONVERT + ["--check"], ["part_graph.py"]]
ITEMS = ROOT / "src/main/resources/assets/nookworks/items"


def main():
    convert = [ROOT / "tools" / CONVERT[0]]
    generators = sorted((ROOT / "tools/textures").glob("gen_*.py")) + [ROOT / "tools" / name for name in DATA + [LAST]]
    scripts = [step for s in generators for step in ([s], convert)]
    scripts += [[ROOT / "tools" / step[0]] + step[1:] for step in PORT]
    for script, *args in scripts:
        # 1.21.1: the port has no item definitions, but the generators write theirs there for the converter to take
        ITEMS.mkdir(exist_ok=True)
        ran = subprocess.run([sys.executable, str(script)] + args, cwd=ROOT, capture_output=True, text=True)
        if ran.returncode != 0:
            print(ran.stdout + ran.stderr)
            print(f"{' '.join([script.relative_to(ROOT).as_posix()] + args)} failed; stopped there")
            return 1
    if ITEMS.is_dir() and not any(ITEMS.iterdir()):
        ITEMS.rmdir()  # every definition converted
    changed = subprocess.run(["git", "diff", "--name-status", "--", "src"], cwd=ROOT, capture_output=True, text=True).stdout
    untracked = subprocess.run(["git", "ls-files", "--others", "--exclude-standard", "--", "src"], cwd=ROOT,
                               capture_output=True, text=True).stdout
    changed += "".join(f"?\t{line}\n" for line in untracked.splitlines())
    print(f"ran {len(generators) + len(PORT)} scripts, and the converter after each generator")
    if changed:
        print("these differ from what is staged or committed:\n" + changed)
        return 1
    print("every generated file is as its script makes it")
    return 0


if __name__ == "__main__":
    sys.exit(main())
