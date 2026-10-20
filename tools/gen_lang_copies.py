"""The lang files that are copies of another: Minecraft falls back from a regional language only to English, never to
its sibling, so a player in Español (México) saw every text of ours in English while es_es.json sat beside it.

- zh_hk from zh_tw: Hong Kong reads Taiwan's Traditional Chinese (the r13 review; the user, 2026-10-12: "修正してください").
  Both write Traditional characters; the words the two would choose differently are few.
- es_mx, es_ar, es_cl, es_ec, es_uy, es_ve from es_es: es_es is written in a neutral Spanish that reads right in Spain
  and in Latin America alike (the user, 2026-10-17, asked for Spanish, Russian and Portuguese).
- pt_pt from pt_br: Brazilian Portuguese reads a little foreign in Portugal (a few words, the "você" register), but a
  reader there follows it at once - far better than English.

Edit only the source file of each; this writes the rest. Run from the repo root (gen_all.py runs it):
    python tools/gen_lang_copies.py
"""
from pathlib import Path

LANG = Path(__file__).resolve().parents[1] / "src/main/resources/assets/nookworks/lang"

# the languages written by hand (en_us aside), each its own file: what tools/check_lang.py and the GameTests check
TRANSLATED = ["ja_jp", "zh_cn", "zh_tw", "ko_kr", "es_es", "ru_ru", "pt_br"]
# the file written -> the file it is a copy of
COPIES = {"zh_hk": "zh_tw", **{code: "es_es" for code in ("es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve")},
          "pt_pt": "pt_br"}


def main():
    for copy, source in COPIES.items():
        (LANG / f"{copy}.json").write_bytes((LANG / f"{source}.json").read_bytes())
    print("wrote " + ", ".join(f"{copy}.json from {source}.json" for copy, source in COPIES.items()))


if __name__ == "__main__":
    main()
