"""The mod's liquids, still and flowing: vegetable oil, biodiesel, mercury and liquid experience.

Each is an animation in vanilla's layout - still 16 px wide, flowing 32 px wide (the game draws a flowing face from a
16 px window of it), 32 frames either way - and loops without a seam in space or time.

Drawn the way vanilla draws its liquids: a surface of many close shades that swell and shrink slowly (soft noise,
banded into flat steps, never airbrushed), and on it short strokes of light - ripples catching the sky - lying across
a still surface and running down a flowing one. How far apart the shades are, how large the swells and how many and
how bright the strokes are is what tells one liquid from another:

- Vegetable oil: pale gold and see-through; broad, gentle swells and long soft highlights, slow - oil is thick.
- Biodiesel: a clear deep amber, darker and a little less see-through than the oil, with fewer highlights: a fuel,
  not food, told from the oil at a glance.
- Mercury: opaque liquid silver, bright as a mirror of the sky, with white glints where the light is broken; still,
  its swells are gentle (hard ones would show every block of a pool as a tile), falling, they are dark and bright in
  hard streaks, as a metal pours.
- Liquid experience: the green of an experience orb, glowing in round swells, with sparks that flare and fade.

The alpha of every pixel is what makes the game draw a liquid translucent: all opaque and it is drawn solid. A block of
liquid is looked through twice (its near face and its far one), and a bright liquid reads as a wall well before a dark
one does, so these sit under water's 180: oil and experience about 140, biodiesel, darker, about 160 (at 200 or so,
two faces of gold were a solid golden block). Original work, deterministic. Run from the repo root:
python tools/textures/gen_liquids.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/nookworks/textures/block"
FRAMES = 32


class Waves:
    """
    A surface as a sum of waves, as a real one is: each a sine across the tile with a whole number of crests to its
    width and height (so the tile repeats without a seam), running on a whole number of times over the 32 frames (so
    the loop does too), in its own direction. A handful of them, bent a little by a second handful, and there is no
    grid to see - value noise shows its lattice as a waffle once it is cut into shades. {@code fall}: whole tiles the
    surface is carried down over the loop (flowing liquid); {@code upright}: the surface is drawn out twice as long down
    the tile, so its swells run down it as streaks do down a fall.
    """

    def __init__(self, size, count, seed, reach=(1, 3), upright=False, fall=0):
        rng = random.Random(seed)
        self.size, self.fall = size, fall
        tiles = size // 16  # crests to the block, not to the tile: a flowing tile is two blocks across

        taken = set()

        def wave(lo, hi, towards):
            """A wave near the direction {@code towards} (radians), its crests lo..hi to the block apart."""
            for tries in range(200):
                length = rng.uniform(lo, hi)
                angle = towards + rng.uniform(-0.35, 0.35) * (1 + tries / 50)
                kx, ky = round(length * math.cos(angle)), round(length * math.sin(angle))
                if (kx, ky) != (0, 0) and (kx, ky) not in taken and (-kx, -ky) not in taken:
                    break
            taken.add((kx, ky))
            # upright, the crests are twice as far apart down the tile as across it: the same surface, drawn out long
            return (kx * tiles, ky * (1 if upright else tiles), rng.random() * 2 * math.pi,
                    rng.choice((-1, 1)) * rng.randint(1, 2), 1.0 / math.sqrt(math.hypot(kx, ky)))

        # directions spread evenly round the half circle (a wave and its opposite are the same wave), so no one way
        # of lying wins and the surface does not look combed
        turn = rng.random() * math.pi
        self.waves = [wave(*reach, towards=turn + math.pi * i / count) for i in range(count)]
        self.bends = [wave(1, 2, towards=turn + math.pi * (i + 0.5) / 3) for i in range(3)]
        self.total = sum(w[4] for w in self.waves)

    def _sum(self, waves, x, y, t):
        s = self.size
        return sum(a * math.sin(2 * math.pi * (kx * x + ky * y) / s + p + n * t) for kx, ky, p, n, a in waves)

    def at(self, x, y, frame):
        t = 2 * math.pi * frame / FRAMES
        y = y - self.fall * self.size * frame / FRAMES
        bend = self._sum(self.bends, x, y, t) * 0.45
        v = self._sum(self.waves, x + bend, y - bend * 0.6, t) / self.total
        return 0.5 + 0.5 * v


def blend(stops, v):
    """The colour {@code v} (0..1) of the way along a list of colour stops."""
    v = min(max(v, 0.0), 1.0) * (len(stops) - 1)
    i = min(int(v), len(stops) - 2)
    t = v - i
    return tuple(round(stops[i][c] * (1 - t) + stops[i + 1][c] * t) for c in range(len(stops[0])))


def liquid(size, flow, seed, stops, steps, big, fine, contrast, lift=1.0, strokes=0, stroke_len=(2, 5), stroke_colour=None,
           glints=0, glint_colour=None, sparks=0, spark_colour=None, bands=None, slant=False, soft=False, life=(8, 16)):
    """
    One strip of FRAMES frames. The surface: broad waves ({@code big}: crests to the block, least and most; never
    one, or every block is dark on one side and light on the other and a pool shows its grid in stripes) and a
    quarter as much of fine ones ({@code fine}), pushed apart by {@code contrast} about the middle and cut into
    {@code steps} flat shades along {@code stops} (RGBA); {@code lift} below 1 gives most of the surface to the light
    shades and leaves the dark ones to narrow hollows (a metal: mostly sky, a little shadow). On it: {@code strokes} ripples of {@code stroke_colour},
    {@code glints} brighter and shorter, {@code sparks} single flares with a cross at their peak (experience).
    Flowing: the surface is drawn out long and carried down with the flow, and the strokes stand upright.

    {@code bands} (shares, adding up to 1) cuts the surface by how much of it each shade takes instead: the darkest
    {@code bands[0]} of every pixel of the strip is {@code stops[0]}, and so on - so each of a few close shades covers a
    good part of the surface, where even steps along the waves left most of it one shade. {@code slant}: a still
    liquid's strokes lie on a long diagonal, down to the right, not across. {@code soft}: a stroke's two ends are
    blended halfway into the surface, so it fades out rather than stopping in a dash. {@code life}: how many frames
    a stroke lasts, least and most (it grows, holds and shrinks over them); a glint keeps to 8 to 16.
    """
    img = Image.new("RGBA", (size, size * FRAMES))
    fall = 1 if flow else 0
    tiles = size // 16  # a flowing tile is two blocks across: as many cells to the block as a still one
    base = Waves(size, 7, seed, reach=big, upright=flow, fall=fall)
    detail = Waves(size, 5, seed + 7, reach=fine, upright=flow, fall=fall)
    rng = random.Random(seed + 13)
    area = tiles * tiles

    def make(n, colour, length, lasting=(8, 16)):
        return [{"x": rng.randrange(size), "y": rng.randrange(size), "start": rng.randrange(FRAMES),
                 "life": rng.randint(*lasting), "len": rng.randint(*length), "colour": colour, "bend": rng.randrange(3)}
                for _ in range(n * area)]

    lines = make(strokes, stroke_colour, stroke_len, life) + make(glints, glint_colour, (1, 3))
    flares = [(rng.randrange(size), rng.randrange(size), rng.randrange(FRAMES), rng.randint(3, 6)) for _ in range(sparks * area)]
    if bands:
        surface = [[[base.at(x, y, f) * 0.75 + detail.at(x, y, f) * 0.25 for x in range(size)] for y in range(size)]
                   for f in range(FRAMES)]
        ranked = sorted(v for frame in surface for row in frame for v in row)
        cuts, share = [], 0.0
        for b in bands[:-1]:
            share += b
            cuts.append(ranked[min(len(ranked) - 1, round(share * len(ranked)))])
    for f in range(FRAMES):
        for y in range(size):
            for x in range(size):
                if bands:
                    v = surface[f][y][x]
                    img.putpixel((x, y + f * size), stops[sum(1 for c in cuts if v >= c)])
                    continue
                v = base.at(x, y, f) * 0.75 + detail.at(x, y, f) * 0.25
                v = 0.5 + (v - 0.5) * contrast
                v = min(max(v, 0.0), 0.999) ** lift
                img.putpixel((x, y + f * size), blend(stops, (math.floor(v * steps) + 0.5) / steps))
        for st in lines:
            age = (f - st["start"]) % FRAMES
            if age >= st["life"]:
                continue
            grow = min(age + 1, st["life"] - age, st["len"])
            if flow:
                x0, y0 = st["x"], st["y"] + age * 2
                pts = [((x0 + (1 if st["bend"] == 1 and i >= 2 else 0)) % size, (y0 + i) % size) for i in range(grow)]
            elif slant:
                x0, y0 = st["x"] + age // 4, st["y"]
                pts = [((x0 + i) % size, (y0 + i // 2) % size) for i in range(grow)]
            else:
                x0, y0 = st["x"] + age // 4, st["y"]
                pts = [((x0 + i) % size, (y0 + (1 if st["bend"] == 1 and i >= 3 else 0)) % size) for i in range(grow)]
            # at the start and end of its life a ripple is only half there: blended halfway into the surface
            faint = age < 2 or age >= st["life"] - 2
            for i, (px, py) in enumerate(pts):
                under = img.getpixel((px, py + f * size))
                c = st["colour"]
                if faint or (soft and len(pts) > 2 and i in (0, len(pts) - 1)):
                    c = tuple((a + b) // 2 for a, b in zip(under, c))
                img.putpixel((px, py + f * size), c)
        for sx, sy, start, life in flares:
            age = (f - start) % FRAMES
            if age < life:
                y = (sy + (age * 2 if flow else 0)) % size
                peak = 1 < age < life - 2
                img.putpixel((sx, y + f * size), spark_colour if peak or age == 1 else stroke_colour)
                if peak:
                    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        img.putpixel(((sx + ox) % size, (y + oy) % size + f * size), stroke_colour)
    return img


def save(img, name, frametime):
    img.save(BLOCK / f"{name}.png")
    (BLOCK / f"{name}.png.mcmeta").write_text(json.dumps({"animation": {"frametime": frametime}}) + "\n", encoding="utf-8", newline="\n")


def rgba(rgb, a):
    return tuple(rgb) + (a,)


# The oil and biodiesel are cut into four close shades by share (BANDS), deepest first, each a step warmer-yellow
# than the last as a lit gold is: the oil a pale gold (its mustard read as khaki sand), biodiesel its deeper amber.
BANDS = (0.24, 0.28, 0.28, 0.20)
OIL = [rgba((210, 164, 50), 150), rgba((222, 180, 66), 146), rgba((232, 195, 86), 142), rgba((241, 210, 112), 142)]
OIL_LIGHT, OIL_GLINT = rgba((250, 232, 170), 168), rgba((255, 246, 210), 190)

DIESEL = [rgba((104, 63, 17), 170), rgba((120, 73, 20), 164), rgba((134, 83, 23), 160), rgba((148, 94, 28), 158)]
DIESEL_LIGHT, DIESEL_GLINT = rgba((186, 128, 50), 170), rgba((226, 176, 102), 192)

MERCURY = [rgba((84, 90, 102), 255), rgba((122, 128, 142), 255), rgba((162, 168, 182), 255), rgba((200, 206, 218), 255),
           rgba((232, 236, 244), 255)]
MERCURY_LIGHT, MERCURY_GLINT = rgba((218, 222, 232), 255), rgba((252, 253, 255), 255)

EXPERIENCE = [rgba((58, 126, 22), 150), rgba((92, 168, 32), 142), rgba((128, 206, 50), 138), rgba((176, 236, 86), 146)]
EXPERIENCE_LIGHT, EXPERIENCE_SPARK = rgba((206, 250, 124), 164), rgba((244, 255, 188), 210)


def main():
    for flow, size, suffix in ((False, 16, "still"), (True, 32, "flow")):
        # a few long soft highlights each, on a slant while still (short dashes tiled into a grid across a pool)
        save(liquid(size, flow, 11, OIL, steps=5, big=(2, 3), fine=(3, 5), contrast=0.75, strokes=2, stroke_len=(6, 9),
                    stroke_colour=OIL_LIGHT, glints=1, glint_colour=OIL_GLINT, bands=BANDS, slant=not flow, soft=True,
                    life=(14, 22)), f"vegetable_oil_{suffix}", 3)
        save(liquid(size, flow, 23, DIESEL, steps=5, big=(2, 3), fine=(3, 5), contrast=0.8, strokes=1, stroke_len=(6, 9),
                    stroke_colour=DIESEL_LIGHT, bands=BANDS, slant=not flow, soft=True, life=(14, 22)),
             f"biodiesel_{suffix}", 2)
        # still mercury is a mirror: gentle swells and few glints, or a pool shows its blocks as tiles
        save(liquid(size, flow, 37, MERCURY, steps=6, big=(2, 3), fine=(3, 4), contrast=1.5 if flow else 1.05,
                    lift=0.7 if flow else 0.8, strokes=3 if flow else 2, stroke_len=(2, 4), stroke_colour=MERCURY_LIGHT,
                    glints=3 if flow else 1, glint_colour=MERCURY_GLINT), f"mercury_{suffix}", 3)
        save(liquid(size, flow, 53, EXPERIENCE, steps=6, big=(2, 3), fine=(3, 5), contrast=1.1, strokes=3, stroke_len=(2, 4),
                    stroke_colour=EXPERIENCE_LIGHT, sparks=3, spark_colour=EXPERIENCE_SPARK), f"liquid_experience_{suffix}", 2)
    print("wrote the four liquids, still and flowing")


if __name__ == "__main__":
    main()
