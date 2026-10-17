"""Hong Kong Chinese (zh_hk) as Taiwan's Traditional Chinese: the game in 中文（香港） showed every text of ours in English,
having no file of its own (r13 review; the user, 2026-10-12: "修正してください"). Both write Traditional characters;
the words the two would choose differently are few, and a reader of either reads the other.

Run from the repo root (gen_all.py runs it):  python tools/gen_zh_hk.py
"""
from pathlib import Path

LANG = Path(__file__).resolve().parents[1] / "src/main/resources/assets/nookworks/lang"


def main():
    (LANG / "zh_hk.json").write_bytes((LANG / "zh_tw.json").read_bytes())
    print("wrote zh_hk.json from zh_tw.json")


if __name__ == "__main__":
    main()
