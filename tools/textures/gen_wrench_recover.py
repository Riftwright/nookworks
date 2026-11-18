"""Generate the Wrench's Recover-mode icon: the Wrench (gen_tools.py) with a small red mark at its lower right.
Deterministic. The full redesign of the Wrench's icons comes later (the user, 2026-11-07); this only tells the two
modes apart in the hotbar.

Items:  wrench_recover

Run from the repo root:  python tools/textures/gen_wrench_recover.py
"""
import texkit
from gen_tools import ITEM, STEEL, wrench

# A round red badge in the empty corner under the jaw: d its dark rim, R the red, L a light at its upper left.
MARK = [
    ".ddd.",
    "dLRRd",
    "dRRRd",
    "dRRrd",
    ".ddd.",
]
MARK_COLOURS = {"d": (92, 12, 14, 255), "R": (206, 40, 36, 255), "r": (160, 26, 26, 255), "L": (246, 112, 90, 255)}
MARK_AT = (11, 11)


def wrench_recover():
    img = wrench()
    for y, row in enumerate(MARK):
        for x, ch in enumerate(row):
            colour = MARK_COLOURS.get(ch)
            if colour is not None:
                img.putpixel((MARK_AT[0] + x, MARK_AT[1] + y), colour)
    # the Wrench's own outline tones, as gen_tools.py gives it
    return texkit.retone(img, STEEL[0], STEEL[1])


def main():
    ITEM.mkdir(parents=True, exist_ok=True)
    wrench_recover().save(ITEM / "wrench_recover.png")
    print("wrote wrench_recover")


if __name__ == "__main__":
    main()
