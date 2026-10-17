"""Before and after for item sprites: each as it is in a git revision (default HEAD) and as it is now, at 8x, then both
at the inventory's 3x on a slot's grey - for showing a redraw.

    python tools/textures/compare_items.py OUT.png NAME [NAME ...] [--rev REV]

NAME is an item texture's name (textures/item/NAME.png) or a path under textures/ ("block/dawnwood_planks").
"""
import io
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEX = "src/main/resources/assets/nookworks/textures"
SLOT = (139, 139, 139, 255)


def path_of(name):
    return f"{TEX}/{name if '/' in name else 'item/' + name}.png"


def at_rev(rev, name):
    shown = subprocess.run(["git", "-C", str(ROOT), "show", f"{rev}:{path_of(name)}"], capture_output=True)
    if shown.returncode != 0:
        return None
    return Image.open(io.BytesIO(shown.stdout)).convert("RGBA")


def first_frame(img):
    return img.crop((0, 0, img.width, img.width)) if img is not None and img.height > img.width else img


def main():
    args = sys.argv[1:]
    rev = "HEAD"
    if "--rev" in args:
        i = args.index("--rev")
        rev = args[i + 1]
        del args[i:i + 2]
    out, names = Path(args[0]), args[1:]
    big = 8
    cell = 16 * big + 16
    sheet = Image.new("RGBA", (max(1, len(names)) * cell, 2 * cell + 64), SLOT)
    for i, name in enumerate(names):
        before = first_frame(at_rev(rev, name))
        now_file = ROOT / path_of(name)
        after = first_frame(Image.open(now_file).convert("RGBA")) if now_file.exists() else None
        for row, img in ((0, before), (1, after)):
            if img is not None:
                sheet.alpha_composite(img.resize((16 * big, 16 * big), Image.NEAREST), (i * cell + 8, row * cell + 8))
        for k, img in enumerate((before, after)):
            if img is not None:
                sheet.alpha_composite(img.resize((48, 48), Image.NEAREST), (i * cell + 16 + k * 64, 2 * cell + 8))
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
