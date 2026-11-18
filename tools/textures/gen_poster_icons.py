"""A poster's item icon shows its own picture: the design's artwork made small, on a sheet with a paper margin, and
ringed as texkit rings the other icons. The artwork itself is the author's and is only read, never changed; each
icon is a thumbnail made from it, as the picture on a shop's price card is.

- textures/item/poster/<design>.png   one icon per design (data/nookworks/nookworks/poster/<design>.json)
- models/item/poster/<design>.json
- items/poster.json                   picks the icon by the item's nookworks:poster component; an item without
                                      a design (as JEI's catch-all shows it) keeps the rolled poster, item/poster,
                                      and a photo's print shows the photo (nookworks:photo, PhotoIconRenderer)

Run from the repo root after adding or removing a design:  python tools/textures/gen_poster_icons.py
(tools/import_posters.py add runs it for you.)
"""
import json
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import texkit  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
DESIGNS = ROOT / "src/main/resources/data/nookworks/nookworks/poster"
ART = 12  # the longest side of the picture on the icon: a pixel of margin and one of ring each side make 16
PAPER = (246, 244, 236, 255)
PAPER_SHADE = (222, 220, 212, 255)  # the margin along the bottom and right, where the sheet turns from the light


def icon(art, width, height):
    """The icon for a design {@code width} x {@code height} blocks, from its artwork."""
    art = art.convert("RGBA")
    if width >= height:
        w, h = ART, max(3, round(ART * height / width))
    else:
        w, h = max(3, round(ART * width / height)), ART
    small = art.resize((w, h), Image.BOX)  # each pixel the average of the ones it stands for
    sheet = Image.new("RGBA", (w + 2, h + 2), PAPER)
    for x in range(w + 2):
        sheet.putpixel((x, h + 1), PAPER_SHADE)
    for y in range(h + 2):
        sheet.putpixel((w + 1, y), PAPER_SHADE)
    backed = Image.new("RGBA", (w, h), PAPER)
    backed.alpha_composite(small)
    sheet.paste(backed, (1, 1))
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    out.paste(sheet, ((16 - sheet.width) // 2, (16 - sheet.height) // 2))
    return texkit.outline(out)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))


def main():
    cases = []
    made = set()
    for design_file in sorted(DESIGNS.glob("*.json")):
        name = design_file.stem
        design = json.loads(design_file.read_text(encoding="utf-8"))
        namespace, path = design["asset"].split(":", 1)
        art = Image.open(ROOT / f"src/main/resources/assets/{namespace}/textures/poster/{path}.png")
        target = ASSETS / f"textures/item/poster/{name}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        icon(art, design["width"], design["height"]).save(target)
        write_json(ASSETS / f"models/item/poster/{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"nookworks:item/poster/{name}"}})
        cases.append({"when": f"nookworks:{name}", "model": {"type": "minecraft:model", "model": f"nookworks:item/poster/{name}"}})
        made.add(name)
    for stale in list((ASSETS / "textures/item/poster").glob("*.png")) + list((ASSETS / "models/item/poster").glob("*.json")):
        if stale.stem not in made:
            stale.unlink()  # a design taken out takes its icon with it
    write_json(ASSETS / "items/poster.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:component", "component": "nookworks:poster",
        "cases": cases,
        # a photo printed as a poster (nookworks:photo/<number>/<size>) shows its picture, drawn by the client
        "fallback": {"type": "minecraft:condition", "property": "nookworks:photo",
                     "on_true": {"type": "minecraft:special", "base": "nookworks:item/poster", "model": {"type": "nookworks:photo"}},
                     "on_false": {"type": "minecraft:model", "model": "nookworks:item/poster"}}}})
    print(f"wrote {len(cases)} poster icons")


if __name__ == "__main__":
    main()
