"""Itemkit: item sprites placed pixel by pixel, the way the mod's best ones are made.

The bar (the user, 2026-10-04, after Detail Brush - the quality to reach, never art to copy; "十分な線なんてないよ"):

- a silhouette that says what the thing is at 16 px, filling the slot as vanilla's items do;
- real volume, lit from the upper left: each material in at least three values (light, mid, shade) and a highlight
  where it is hard or wet, a form shadow on the far side and a contact shadow where one part sits on another;
- colour ramps that shift hue as they go, shadows cooler and lights warmer, never a colour simply darkened;
- vanilla's ring round the shape (texkit.outline): the shape's own darkest colour, lighter above and left, darker below
  and right, never black;
- materials that read: wood has a grain and plank seams, metal a sharp highlight, cloth a soft one, powder grains;
- detail where the eye goes, and none for its own sake - no noise over placed pixels.

A sprite is a list of 16 strings of 16 characters, each character a colour from its palette ("." is clear), drawn in
layers where parts overlap; then ringed.
"""
from PIL import Image

import texkit

CLEAR = (0, 0, 0, 0)


def paint(rows, palette, origin=(0, 0), img=None):
    """{@code rows} painted onto {@code img} (a new clear 16x16 if none) at {@code origin}; unknown characters are clear."""
    img = img if img is not None else Image.new("RGBA", (16, 16), CLEAR)
    ox, oy = origin
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            colour = palette.get(ch)
            if colour is None:
                continue
            px, py = ox + x, oy + y
            if 0 <= px < img.width and 0 <= py < img.height:
                img.putpixel((px, py), tuple(colour[:3]) + (colour[3] if len(colour) > 3 else 255,))
    return img


def sprite(rows, palette, ring=True, lit=0.8, dark=0.55, holes=False, ring_from=None):
    """A whole sprite from its rows, ringed as vanilla rings its items unless {@code ring} is false. {@code lit} and
    {@code dark} are texkit.outline's: how far the shape's darkest colour is taken down for the ring above and left, and
    below and right - a pale thing (rice, paper) wants a ring only a step under its own shade. With {@code holes} the
    clear pixels the shape closes round are opened again after the ring, so the slot shows through them.
    {@code ring_from} is a palette the ring is worked out from instead, saying what each pixel counts as: the material
    under a dark fleck, a window or a keyhole, so the ring stays the material's own darkest and never goes near black."""
    img = paint(rows, palette)
    if not ring:
        return img
    ringed = texkit.outline(paint(rows, ring_from) if ring_from else img, lit=lit, dark=dark)
    if ring_from:  # the shape's own pixels back over what the ring was worked out from
        for y in range(img.height):
            for x in range(img.width):
                if img.getpixel((x, y))[3]:
                    ringed.putpixel((x, y), img.getpixel((x, y)))
    if holes:
        for x, y in enclosed(rows):
            ringed.putpixel((x, y), CLEAR)
    return ringed


def enclosed(rows):
    """The clear pixels the shape closes round: those a walk in from the border cannot reach."""
    clear = {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "."}
    seen, todo = set(), [(x, y) for (x, y) in clear if x in (0, 15) or y in (0, 15)]
    while todo:
        x, y = todo.pop()
        if (x, y) in seen or (x, y) not in clear:
            continue
        seen.add((x, y))
        todo += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return clear - seen


def check(rows):
    """Rows must be 16 of 16: a typo in a placed sprite shifts everything after it."""
    assert len(rows) == 16, len(rows)
    for i, row in enumerate(rows):
        assert len(row) == 16, (i, row, len(row))
    return rows


# The mouth of vanilla's bucket (item/bucket.png in 26.2: its opening is rows 2 to 5), as (row, first column, shades):
# 0 deep, 1 mid, 2 light, 3 highlight - in the shadow of the back rim along the top, lit across the middle from the
# left, a gleam at the left. A liquid's bucket is the vanilla bucket with this laid over it in the liquid's colours.
BUCKET_MOUTH = ((2, 5, "001100"), (3, 3, "1223111110"), (4, 3, "1211111100"), (5, 5, "111000"))


def bucket_fill(ramp, glints=()):
    """The liquid in the vanilla bucket's mouth, in {@code ramp} (deep, mid, light, highlight), with {@code glints}
    as (x, y, colour) laid over it; the item model stacks it over minecraft:item/bucket."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y, x0, shades in BUCKET_MOUTH:
        for i, shade in enumerate(shades):
            img.putpixel((x0 + i, y), tuple(round(v) for v in ramp[int(shade)][:3]) + (255,))
    for x, y, colour in glints:
        img.putpixel((x, y), tuple(round(v) for v in colour[:3]) + (255,))
    return img
