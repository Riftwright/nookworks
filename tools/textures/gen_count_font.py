"""The Storage Barrel's count font: the digits a long count is drawn in (128, 1.2K, 65K). Original pixel art.

Vanilla's font drawn small enough for four characters to fit a slot is thin, and its lowercase k a smudge. These
digits are a row taller and a column wider than vanilla's (six by eight to its five by seven), so a count reads about a
fifth bigger at the same scale - the user's ask, 2026-10-01 - and the K (M, B) that says how many thousands is drawn
bold, with a two-pixel stem. StorageScreen draws a glyph's pixel as a whole number of screen pixels, so they stay crisp
at every GUI scale.

- font/count.json                  a bitmap provider: one row of cells, every character a count can use
- textures/font/count.png          white glyphs, tinted by the text colour as vanilla's are

Run from the repo root:  python tools/textures/gen_count_font.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/nookworks"
CELL_W, CELL_H = 8, 8

# '#' is ink. Digits six wide (the 1 five), one-pixel strokes like vanilla's; the point one pixel; the letters bold.
# The K is five wide so that the widest count of thousands (8.8K: 22 pixels with the spaces) fits a slot at three
# screen pixels to one at GUI scale 4, as at every scale: wider, it would be drawn a size down there.
GLYPHS = {
    "0": [".####.",
          "#....#",
          "#....#",
          "#....#",
          "#....#",
          "#....#",
          "#....#",
          ".####."],
    "1": ["..#..",
          ".##..",
          "#.#..",
          "..#..",
          "..#..",
          "..#..",
          "..#..",
          "#####"],
    "2": [".####.",
          "#....#",
          ".....#",
          "....#.",
          "..##..",
          ".#....",
          "#.....",
          "######"],
    "3": [".####.",
          "#....#",
          ".....#",
          "..###.",
          ".....#",
          ".....#",
          "#....#",
          ".####."],
    "4": ["....#.",
          "...##.",
          "..#.#.",
          ".#..#.",
          "#...#.",
          "######",
          "....#.",
          "....#."],
    "5": ["######",
          "#.....",
          "#####.",
          ".....#",
          ".....#",
          ".....#",
          "#....#",
          ".####."],
    "6": ["..###.",
          ".#....",
          "#.....",
          "#####.",
          "#....#",
          "#....#",
          "#....#",
          ".####."],
    "7": ["######",
          ".....#",
          "....#.",
          "...#..",
          "..#...",
          "..#...",
          "..#...",
          "..#..."],
    "8": [".####.",
          "#....#",
          "#....#",
          ".####.",
          "#....#",
          "#....#",
          "#....#",
          ".####."],
    "9": [".####.",
          "#....#",
          "#....#",
          "#....#",
          ".#####",
          ".....#",
          "....#.",
          ".###.."],
    ".": [".",
          ".",
          ".",
          ".",
          ".",
          ".",
          ".",
          "#"],
    "K": ["##..#",
          "##.##",
          "####.",
          "###..",
          "###..",
          "####.",
          "##.##",
          "##..#"],
    "M": ["##...##",
          "###.###",
          "#######",
          "##.#.##",
          "##...##",
          "##...##",
          "##...##",
          "##...##"],
    "B": ["#####.",
          "##..##",
          "##..##",
          "#####.",
          "##..##",
          "##..##",
          "##..##",
          "#####."],
}
CHARS = "0123456789.KMB"


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    img = Image.new("RGBA", (CELL_W * len(CHARS), CELL_H), (0, 0, 0, 0))
    px = img.load()
    for i, c in enumerate(CHARS):
        rows = GLYPHS[c]
        assert len(rows) == CELL_H and all(len(r) == len(rows[0]) <= CELL_W - 1 for r in rows), c
        for y, row in enumerate(rows):
            for x, ink in enumerate(row):
                if ink == "#":
                    px[i * CELL_W + x, y] = (255, 255, 255, 255)
    out = ASSETS / "textures/font/count.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    # ascent 8: the glyphs' bottom row sits where vanilla's digits end, one row taller at the top
    write_json(ASSETS / "font/count.json", {"providers": [
        {"type": "bitmap", "file": "nookworks:font/count.png", "height": CELL_H, "ascent": CELL_H, "chars": [CHARS]}]})
    print(f"count font: {len(CHARS)} glyphs")


if __name__ == "__main__":
    main()
