"""The colours a filled Mob Bottle's contents take for each vanilla mob: the two colours of its spawn egg.

Vanilla's spawn eggs are each drawn in their own picture now, not tinted from two colours as they were, so there is
no table of them to ask; this works the two out of each egg's picture and keeps only those numbers. No picture of
vanilla's is copied into the mod: assets/nookworks/mob_bottle_colors.json holds the colours alone, as "#rrggbb" pairs
by entity id.

An egg is drawn in shades of two or three colours inside a dark outline. The outline is the ring of pixels at the
edge of the egg (not the dark ones, or an Enderman's black egg would lose its black); the rest is split in two by
colour (k-means in CIELAB, which puts the shades of one colour together), and the larger half is the shell, the
smaller the spots, each the mean of its pixels.

A mob with no line here (another mod's) is coloured by its kind in dev.nookworks.client.MobBottleColors.

Run from the repo root, after a Gradle build has made the patched game jar:  python tools/textures/gen_mob_bottle_colors.py
"""
import glob
import io
import json
import re
import zipfile
from collections import Counter
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/nookworks/mob_bottle_colors.json"


def jar():
    found = [p for p in glob.glob(str(ROOT / "build/moddev/artifacts/minecraft-patched-*.jar")) if "sources" not in p and "merged" not in p]
    if not found:
        raise SystemExit("no patched game jar in build/moddev/artifacts: run a Gradle build first")
    # the version gradle.properties builds against: a build/ that has known an older one keeps it too (26.2's beside 26.3's)
    version = re.search(r"^neo_version=(\S+)", (ROOT / "gradle.properties").read_text(encoding="utf-8"), re.M).group(1)
    return next((p for p in found if f"-{version}." in p), found[-1])


def lab(rgb):
    """sRGB (0-255) to CIELAB (D65)."""
    def linear(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (linear(c) for c in rgb)
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883

    def f(t):
        return t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116
    return 116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))


def distance(a, b):
    return sum((p - q) ** 2 for p, q in zip(a, b))


def interior(picture):
    """The egg's pixels less the ring at its edge (the outline)."""
    img = picture.convert("RGBA")
    w, h = img.size

    def solid(x, y):
        return 0 <= x < w and 0 <= y < h and img.getpixel((x, y))[3] >= 128
    return [img.getpixel((x, y))[:3] for y in range(h) for x in range(w)
            if img.getpixel((x, y))[3] >= 200 and all(solid(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]


def mean(pixels):
    return tuple(round(sum(p[i] for p in pixels) / len(pixels)) for i in range(3))


def colours(picture):
    """The shell's colour and the spots', from an egg's picture."""
    pixels = interior(picture)
    counts = Counter(pixels)
    labs = {c: lab(c) for c in counts}
    # Start from the commonest colour and the one that, by its count and its distance from it, stands out most.
    first = counts.most_common(1)[0][0]
    second = max(counts, key=lambda c: counts[c] * distance(labs[c], labs[first]))
    centres = [labs[first], labs[second]]
    for _ in range(50):
        halves = ([], [])
        for c, n in counts.items():
            nearer = 0 if distance(labs[c], centres[0]) <= distance(labs[c], centres[1]) else 1
            halves[nearer].extend([c] * n)
        if not halves[1]:
            break
        moved = [tuple(sum(labs[c][i] for c in half) / len(half) for i in range(3)) for half in halves]
        if moved == centres:
            break
        centres = moved
    shell, spots = sorted(halves, key=len, reverse=True)
    return mean(shell), mean(spots or shell)


def hex_of(c):
    return "#%02x%02x%02x" % c


def main():
    table = {}
    with zipfile.ZipFile(jar()) as z:
        for name in sorted(z.namelist()):
            if name.startswith("assets/minecraft/textures/item/") and name.endswith("_spawn_egg.png"):
                mob = name.rsplit("/", 1)[1][:-len("_spawn_egg.png")]
                shell, spots = colours(Image.open(io.BytesIO(z.read(name))))
                table[f"minecraft:{mob}"] = [hex_of(shell), hex_of(spots)]
    OUT.write_text(json.dumps(table, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote the colours of {len(table)} mobs")


if __name__ == "__main__":
    main()
