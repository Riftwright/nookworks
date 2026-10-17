"""The CurseForge project logo: a lit filament bulb, drawn as pixel art, on a deep green ground with its glow round it.

All original pixel art, drawn here (no game texture is copied): a cord, a brass cap and a round glass globe lit from within
by a coiled filament - the Filament Bulb as a logo rather than its model, so it reads at the 64 px of a search result.
The user picked it of four drafts (2026-10-04: a bulb on green, a bulb on plum, the Dock-a-Daemon Door's porthole, a
corner of a room); before it, the logo was a game capture of a workshop corner with the name across it, which said
nothing at 64 px.

Run from the repo root:  python tools/make_logo.py   -> docs/listing/icon.png (512 x 512) and the mod's own icon,
                         src/main/resources/nookworks.png (128 x 128)
                         python tools/make_logo.py --animated   -> also docs/listing/icon_animated.gif and .png (an
                         animated PNG), 400 x 400: the bulb swaying a little on its cord, its glow flickering (the user
                         asked for it to sway and glow, 2026-10-04). Its first frame is the still logo. CurseForge takes a
                         PNG logo and scales it, so there it may well stand still; the GIF is for anywhere else.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/listing/icon.png"
MOD_ICON = ROOT / "src/main/resources/nookworks.png"  # neoforge.mods.toml's iconFile; tools/make_guide.py's LOGO
MOD_ICON_SIZE = 128
GRID = 16
SIZE = 512
CELL = SIZE // GRID

BG_IN = (40, 92, 66)     # the ground, lighter round the bulb
BG_OUT = (12, 36, 26)    # and darker at the edges
GLOW = (255, 196, 110)
CORD = (34, 30, 30)
BRASS = {"hi": (240, 204, 128), "mid": (200, 154, 82), "lo": (140, 98, 50)}
GLASS_EDGE = (186, 104, 44)
GLASS = (244, 178, 92)
GLASS_IN = (255, 214, 132)
GLASS_CORE = (255, 236, 176)
COIL = (255, 255, 240)
WIRE = (150, 84, 36)


def bulb():
    """The bulb on a 16 x 16 grid: (x, y) -> colour."""
    px = {}
    for y in range(0, 4):
        px[(7, y)] = CORD
        px[(8, y)] = CORD
    for x in range(6, 10):
        px[(x, 4)] = BRASS["hi"] if x < 8 else BRASS["mid"]
    for x in range(5, 11):
        px[(x, 5)] = BRASS["mid"] if x < 9 else BRASS["lo"]
    rows = {6: (5, 10), 7: (4, 11), 8: (3, 12), 9: (3, 12), 10: (3, 12), 11: (3, 12), 12: (3, 12), 13: (4, 11), 14: (5, 10)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            edge = x in (a, b) or y in (6, 14) or (y in (7, 13) and x in (a + 1, b - 1))
            px[(x, y)] = GLASS_EDGE if edge else GLASS
    for y in range(7, 14):
        for x in range(4, 12):
            if px.get((x, y)) == GLASS:
                px[(x, y)] = GLASS_IN
    for y in range(8, 13):
        for x in range(5, 11):
            px[(x, y)] = GLASS_CORE
    for y in (6, 7, 8, 9):  # the two support wires down from the cap
        px[(6, y)] = WIRE
        px[(9, y)] = WIRE
    for x, y in ((6, 10), (7, 9), (7, 10), (8, 10), (8, 9), (9, 10), (7, 11), (8, 11)):  # the glowing coil
        px[(x, y)] = COIL
    px[(4, 9)] = (255, 250, 236)  # the glint
    px[(5, 8)] = (255, 255, 255)
    return px


def lerp(a, b, t):
    return tuple(round(a[k] + (b[k] - a[k]) * t) for k in range(3))


def main():
    img = Image.new("RGB", (SIZE, SIZE))
    d = img.load()
    c = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            d[x, y] = lerp(BG_IN, BG_OUT, min(1.0, math.hypot(x - c, y - c) / (SIZE * 0.72)))
    # the glow: warm light round the globe, fading out
    glow = Image.new("L", (SIZE, SIZE), 0)
    gd = ImageDraw.Draw(glow)
    cx, cy = 8 * CELL, 10.5 * CELL
    scale = CELL / 30
    for i in range(60, 0, -1):
        r = (60 + i * 3.2) * scale
        gd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=int(255 * (1 - i / 60) ** 1.6 * 0.9))
    glow = glow.filter(ImageFilter.GaussianBlur(18 * scale))
    img = Image.composite(Image.new("RGB", (SIZE, SIZE), GLOW), img, glow)
    draw = ImageDraw.Draw(img)
    for (x, y), colour in bulb().items():
        draw.rectangle((x * CELL, y * CELL, x * CELL + CELL - 1, y * CELL + CELL - 1), fill=colour)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT, optimize=True)
    print(f"wrote {OUT.relative_to(ROOT)} ({SIZE} x {SIZE})")
    # the mod's own icon (the mods list in game, the guide's tab and header): the same logo, 128 px (the user, 2026-10-17:
    # the guide still showed the old workshop-corner capture)
    img.resize((MOD_ICON_SIZE, MOD_ICON_SIZE), Image.LANCZOS).save(MOD_ICON, optimize=True)
    print(f"wrote {MOD_ICON.relative_to(ROOT)} ({MOD_ICON_SIZE} x {MOD_ICON_SIZE})")


FRAMES = 48       # one loop: a swing to each side and back
FRAME_MS = 60     # 2.88 s a loop, a slow sway
SWAY = 2.4        # degrees each way, about where the cord is tied
ANIMATED_SIZE = 400


def ground():
    img = Image.new("RGB", (SIZE, SIZE))
    d = img.load()
    c = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            d[x, y] = lerp(BG_IN, BG_OUT, min(1.0, math.hypot(x - c, y - c) / (SIZE * 0.72)))
    return img.convert("RGBA")


def glow_mask(cx, cy, k):
    """The glow round the globe at (cx, cy), {@code k} of its full strength (1 in the still logo)."""
    glow = Image.new("L", (SIZE, SIZE), 0)
    gd = ImageDraw.Draw(glow)
    scale = CELL / 30
    for i in range(60, 0, -1):
        r = (60 + i * 3.2) * scale * (0.94 + 0.06 * k)
        gd.ellipse((cx - r, cy - r, cx + r, cy + r), fill=int(255 * (1 - i / 60) ** 1.6 * 0.9 * k))
    return glow.filter(ImageFilter.GaussianBlur(18 * scale))


def frame(t, base):
    """The logo {@code t} of the way through the loop: swung by the cord, the filament a little brighter or dimmer."""
    angle = SWAY * math.sin(2 * math.pi * t)
    # the flicker: whole numbers of waves a loop, so the loop has no seam; 1 at the start, as the still logo is
    k = 0.92 + 0.08 * math.cos(2 * math.pi * 2 * t) + 0.04 * math.sin(2 * math.pi * 5 * t)
    pivot = (8 * CELL, 0)  # the top of the cord
    a = math.radians(-angle)
    gx, gy = 8 * CELL - pivot[0], 10.5 * CELL - pivot[1]
    cx = pivot[0] + gx * math.cos(a) - gy * math.sin(a)
    cy = pivot[1] + gx * math.sin(a) + gy * math.cos(a)
    img = Image.composite(Image.new("RGBA", (SIZE, SIZE), GLOW + (255,)), base, glow_mask(cx, cy, k))
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for (x, y), colour in bulb().items():
        if colour == COIL:
            colour = lerp((255, 214, 150), COIL, min(1.0, k))
        elif colour == GLASS_CORE:
            colour = lerp(GLASS_IN, GLASS_CORE, min(1.0, 0.4 + 0.6 * k))
        draw.rectangle((x * CELL, y * CELL, x * CELL + CELL - 1, y * CELL + CELL - 1), fill=colour + (255,))
    img.alpha_composite(layer.rotate(angle, resample=Image.BICUBIC, center=pivot))
    return img.convert("RGB").resize((ANIMATED_SIZE, ANIMATED_SIZE), Image.LANCZOS)


def animated():
    base = ground()
    frames = [frame(i / FRAMES, base) for i in range(FRAMES)]
    gif = OUT.with_name("icon_animated.gif")
    frames[0].save(gif, save_all=True, append_images=frames[1:], duration=FRAME_MS, loop=0, optimize=True)
    png = OUT.with_name("icon_animated.png")
    frames[0].save(png, save_all=True, append_images=frames[1:], duration=FRAME_MS, loop=0, format="PNG")
    for f in (gif, png):
        print(f"wrote {f.relative_to(ROOT)} ({ANIMATED_SIZE} x {ANIMATED_SIZE}, {FRAMES} frames, {f.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
    if "--animated" in sys.argv[1:]:
        animated()
