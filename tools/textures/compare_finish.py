"""A before/after sheet for moving a generator from texkit.finish to texkit.shade (2026-11-04): runs the generator with
nothing saved, catches each image it hands to finish (or icon), and draws a row per image - the flat drawing, finish's
result, shade's result, and shade's tiled 2x2 for block faces - at 8x. Nothing in the repo is written.

    python tools/textures/compare_finish.py gen_tableware [material] [--out build/texcompare]
    python tools/textures/compare_finish.py gen_tableware '{"(196, 194, 184)": "stone"}'

material: one name (metal, brick, mortar, wood, stone, plain) or a JSON dict of "(r, g, b)" to name; default plain.
The tuning each finish call was given (clusters, grain, size, keep, tile, reach) is passed on to shade as it is.
"""
import importlib
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import texkit  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SCALE = 8


def material_arg(text):
    if not text:
        return "plain"
    if text.startswith("{"):
        return {tuple(int(v) for v in k.strip("() ").split(",")): m for k, m in json.loads(text).items()}
    return text


def capture(module_name):
    """Runs the generator's main with saving turned off; returns [(flat, finished, kwargs)]."""
    caught = []
    real_finish, real_icon = texkit.finish, texkit.icon

    def finish(img, seed, **kw):
        out = real_finish(img, seed, **kw)
        caught.append((img.convert("RGBA").copy(), out, seed, kw, False))
        return out

    def icon(img, seed, keep=(), **kw):
        out = real_icon(img, seed, keep=keep, **kw)
        caught.append((img.convert("RGBA").copy(), out, seed, dict(kw, keep=keep), True))
        return out

    texkit.finish, texkit.icon = finish, icon
    real_save = Image.Image.save
    Image.Image.save = lambda self, *a, **k: None
    real_write = Path.write_text
    real_write_bytes = Path.write_bytes
    Path.write_text = lambda self, *a, **k: None
    Path.write_bytes = lambda self, *a, **k: None
    try:
        module = importlib.import_module(module_name)
        if hasattr(module, "main"):
            module.main()
    finally:
        texkit.finish, texkit.icon = real_finish, real_icon
        Image.Image.save = real_save
        Path.write_text, Path.write_bytes = real_write, real_write_bytes
    return caught


def shaded(flat, seed, kw, is_icon, material):
    tuning = {k: v for k, v in kw.items() if k in ("clusters", "grain", "size", "keep", "tile", "reach")}
    if is_icon:
        settings = dict(texkit.ICON)
        settings.update(tuning)
        return texkit.outline(texkit.shade(flat, seed, material=material, **settings))
    return texkit.shade(flat, seed, material=material, **tuning)


def big(img):
    return img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out_dir = ROOT / "build/texcompare"
    if "--out" in sys.argv:
        out_dir = Path(sys.argv[sys.argv.index("--out") + 1])
    name = args[0]
    material = material_arg(args[1] if len(args) > 1 else "")
    rows = capture(name)
    if not rows:
        print("no finish calls caught")
        return
    cell = max(max(f.width, f.height) for f, *_ in rows) * SCALE
    cols = 4
    pad = 8
    sheet = Image.new("RGBA", (cols * (cell + pad) + pad, len(rows) * (cell + pad) + pad + 20), (60, 60, 64, 255))
    d = ImageDraw.Draw(sheet)
    for k, label in enumerate(("flat", "finish", "shade", "shade tiled")):
        d.text((pad + k * (cell + pad), 4), label, fill=(230, 230, 230, 255))
    for r, (flat, done, seed, kw, is_icon) in enumerate(rows):
        new = shaded(flat, seed, kw, is_icon, material)
        y = 20 + pad + r * (cell + pad)
        for k, img in enumerate((flat, done, new)):
            sheet.alpha_composite(big(img), (pad + k * (cell + pad), y))
        tiled = Image.new("RGBA", (new.width * 2, new.height * 2))
        for i in range(2):
            for j in range(2):
                tiled.alpha_composite(new, (i * new.width, j * new.height))
        sheet.alpha_composite(tiled.resize((cell, cell), Image.NEAREST), (pad + 3 * (cell + pad), y))
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"{name}.png"
    sheet.save(path)
    print(path, len(rows), "images")


if __name__ == "__main__":
    main()
