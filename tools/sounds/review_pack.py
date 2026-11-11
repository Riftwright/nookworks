"""A folder to hand to another listener (a model that takes sound, as Gemini in Antigravity): the sounds as numbered
.wav files with nothing in their names, a paper saying what is asked, sheets to fill in, and a hearing test.

    python tools/sounds/review_pack.py OUT_DIR [--nooktech PATH/TO/nook_technology/mod]
    python tools/sounds/review_pack.py OUT_DIR --against REV [--nooktech PATH --nooktech-against REV] [--previous-key KEY.json]

The second form is for a second look: every sound whose recipe changed since REV, as it was and as it is (a blind
pair), the files the user once picked from Kenney's packs against the recipes that would replace them, and a few
sounds left alone - if the listener marks the same sound differently the second time, its marks are worth that much
less. --previous-key names the first look's key, so each of those is tied to the number it had then.

In OUT_DIR:
  README.md             what this is and what to do (Japanese: it is for the author to hand over and read)
  hearing_test/         three sounds whose answers are known, asked first: a reviewer that cannot say how many beeps
                        there were is not listening, whatever marks it writes after
  blind/NNN.wav         one file a sound, its variants one after another with a pause between, in shuffled order: all
                        of ours as they are now (those held back as well), the first version of a number of them (so
                        a pair shows whether the rework is heard), and the four the user picked from Kenney's packs
  scores_part1.csv      to fill in blind; scores_part2.csv after step2.b64.txt is decoded
  step2.b64.txt         what each number is meant to be, and which numbers are a pair - in base64, so a reviewer that
                        reads every file on arrival does not learn it before it has listened
Beside OUT_DIR, OUT_DIR_key.json says what each number really is (which version, held back or not): that stays with us.

No sound of vanilla's is in it: those are Mojang's, and this folder goes to somebody else's service.
"""
import base64
import importlib.util
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import sfx  # noqa: E402
import listen_page  # noqa: E402

ROOT = HERE.parents[1]
SR = sfx.SR
PLAYED_AT = {"dice.roll": 1.6, "gondola.rattle": 0.7}
# the first version, for a pair: the commits it was last whole in, and the events whose recipes changed most since
FIRST = {"nookworks": "48bf291d", "nooktech": "cdcfcd5"}
PAIRED = ["nookworks:block.crusher.run", "nookworks:block.printing_press.run", "nookworks:block.ventilation_fan.run",
          "nookworks:block.electric_furnace.run", "nookworks:block.dice.roll", "nookworks:block.oil_press.turn",
          "nookworks:block.recycler.run", "nookworks:block.compressor.run", "nookworks:block.iron_pot.fry",
          "nooktech:block.arc_furnace.run", "nooktech:block.auto_crafter.run", "nooktech:block.brick_furnace.formed",
          "nooktech:block.tar_still.run", "nooktech:block.loading_arm.pick", "nooktech:block.mob_data_bank.rewind",
          "nooktech:block.ropeway_tower.clatter", "nooktech:entity.gondola.rattle", "nooktech:block.block_breaker.run",
          "nooktech:block.centrifuge.run", "nooktech:block.storage_terminal.run"]
KENNEY = {"block.motor.run": "小さなモーターが回る音", "block.drive_wheel.turn": "ベルトをかけた滑車が回る音",
          "block.spawner_controller.run": "不思議な装置が作動している音", "block.trash_can.swallow": "ゴミ箱が物を飲み込む、ブリキを叩くような音"}
# left alone between the two looks, and sent again: does the listener mark the same sound the same way twice?
ANCHORS = ["nookworks:block.crusher.run", "nookworks:item.wrench.turn", "nookworks:block.oil_press.turn",
           "nookworks:block.printing_press.run", "nooktech:block.shaft.shift", "nooktech:block.block_breaker.run"]


def load(path, name):
    sys.path.insert(0, str(path.parent))
    for cached in ("sfx", "gen_sounds"):
        sys.modules.pop(cached, None)
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    sys.path.pop(0)
    return module


def as_played(x, event):
    rate = next((r for k, r in PLAYED_AT.items() if k in event), 1.0)
    return signal.resample_poly(x, 100, int(round(rate * 100))) if rate != 1.0 else x


def in_a_row(variants, gap=0.7):
    """A sound's variants one after another, a pause between: one file to listen to."""
    parts = [np.zeros(sfx.n_of(0.2))]
    for v in variants:
        parts += [v, np.zeros(sfx.n_of(gap))]
    return np.concatenate(parts)


def from_git(repo, rev, path):
    blob = subprocess.run(["git", "-C", str(repo), "show", f"{rev}:{path}"], capture_output=True)
    if blob.returncode != 0:
        return None
    with tempfile.NamedTemporaryFile(suffix=".ogg", delete=False) as f:
        f.write(blob.stdout)
    try:
        return sfx.read_audio(f.name)
    finally:
        Path(f.name).unlink(missing_ok=True)


def hearing_tests(out):
    """Three sounds anyone listening can answer, and the answers."""
    rng = sfx.rng_for("hearing")
    beep = sfx.fade(np.sin(2 * np.pi * 880 * np.arange(sfx.n_of(0.15)) / SR), 0.01, 0.01) * 0.4
    gap = np.zeros(sfx.n_of(0.15))
    a = np.concatenate([gap, beep, gap, beep, gap, beep, np.zeros(sfx.n_of(0.9)), beep, gap, beep, gap])
    n = sfx.n_of(1.6)
    f = 300.0 * (1200.0 / 300.0) ** np.linspace(0, 1, n)
    b = sfx.fade(np.sin(2 * np.pi * np.cumsum(f) / SR), 0.02, 0.05) * 0.4
    c = np.zeros(sfx.n_of(3.2))
    at = 0.2
    for k in range(7):
        sfx.place(c, 0.6 * sfx.norm(sfx.burst(0.12, rng, sfx.WOOD)), at)
        at += float(rng.uniform(0.25, 0.5))
    (out / "hearing_test").mkdir(parents=True, exist_ok=True)
    for name, x in (("a", a), ("b", b), ("c", c)):
        sfx.write_wav(out / "hearing_test" / f"{name}.wav", x)
    return {"a": "ビープは前半3回、後半2回（合計5回）", "b": "音程は上がっていく", "c": "ノックは7回"}


def gather(args):
    """Every sound to send, as dicts: mod, event, name, aim, version (now / first / kenney), held, wave."""
    mods = [("nookworks", ROOT, HERE / "gen_sounds.py", ROOT / "src/main/resources/assets/nookworks", listen_page.NOOKWORKS)]
    if "--nooktech" in args:
        tec = Path(args[args.index("--nooktech") + 1]).resolve()
        mods.append(("nooktech", tec.parent, tec / "tools/sounds/gen_sounds.py", tec / "src/main/resources/assets/nooktech", listen_page.NOOKTECH))
    items = []
    for modid, repo, gen_path, assets, table in mods:
        gen = load(gen_path, f"gen_{modid}")
        names = json.loads((assets / "lang" / "ja_jp.json").read_bytes())
        # what it is meant to be, without the notes in brackets that are about the game's code
        aims = {event: re.sub(r"（[^）]*）", "", aim) for _, rows in table for event, _, aim, _, _, _ in rows}
        held = set(getattr(gen, "HELD_BACK", ()))
        prefix = str(assets.relative_to(repo).as_posix())
        for event, (_, variants, _, extra) in gen.SOUNDS.items():
            loop = extra.get("loop", False)
            waves = [as_played(gen.render(event, v), event) for v in range(variants)]
            if loop:  # a loop is heard going round: three turns of it, eased in and out
                waves = [sfx.fade(np.tile(waves[0], 3), 0.3, 0.5)]
            base = dict(mod=modid, event=event, name=names[f"subtitles.{modid}.{event}"], aim=aims.get(event, ""))
            items.append(dict(base, version="now", held=event in held, wave=in_a_row(waves)))
            if f"{modid}:{event}" in PAIRED:
                first = []
                for v in range(1, 9):
                    x = from_git(repo, FIRST[modid], f"{prefix}/sounds/{gen.file_of(event)}_{v}.ogg")
                    if x is None:
                        break
                    first.append(as_played(x, event))
                if first:
                    items.append(dict(base, version="first", held=False, wave=in_a_row(first)))
        if modid == "nookworks":
            for event, aim in KENNEY.items():
                kind, thing, what = event.split(".")
                x = sfx.read_audio(assets / "sounds" / kind / f"{thing}_{what}.ogg")
                items.append(dict(mod=modid, event=event, name=names[f"subtitles.{modid}.{event}"], aim=aim,
                                  version="kenney", held=False, wave=in_a_row([x])))
    return items


def ride(loop_wave):
    """A loop as the game plays it on a ride: off slowly, up to full speed and back, higher and louder the faster."""
    turns = np.tile(loop_wave, 8)
    n = sfx.n_of(6.0)
    t = np.arange(n) / SR
    pace = np.where(t < 3.0, t / 3.0, (6.0 - t) / 3.0)
    pos = np.cumsum(0.7 + 0.6 * pace)
    pos = pos[pos < len(turns) - 1]
    y = np.interp(pos, np.arange(len(turns)), turns) * (0.25 + 0.75 * pace[:len(pos)])
    return np.concatenate([np.zeros(sfx.n_of(0.2)), sfx.fade(y, 0.15, 0.3), np.zeros(sfx.n_of(0.3))])


def old_generator(repo, rev, rel, name):
    """gen_sounds.py and its kit as they were at rev, loaded from a folder of their own."""
    tmp = Path(tempfile.mkdtemp(prefix="sounds_before_"))
    for f in ("gen_sounds.py", "sfx.py"):
        blob = subprocess.run(["git", "-C", str(repo), "show", f"{rev}:{rel}/{f}"], capture_output=True, check=True).stdout
        (tmp / f).write_bytes(blob)
    return load(tmp / "gen_sounds.py", name)


def gather_changed(args):
    """The second look's sounds: pairs of what changed since the commits named, and the anchors."""
    revs = {"nookworks": args[args.index("--against") + 1]}
    mods = [("nookworks", ROOT, HERE / "gen_sounds.py", "tools/sounds", ROOT / "src/main/resources/assets/nookworks", listen_page.NOOKWORKS)]
    if "--nooktech" in args:
        tec = Path(args[args.index("--nooktech") + 1]).resolve()
        revs["nooktech"] = args[args.index("--nooktech-against") + 1]
        mods.append(("nooktech", tec.parent, tec / "tools/sounds/gen_sounds.py", "mod/tools/sounds", tec / "src/main/resources/assets/nooktech", listen_page.NOOKTECH))
    items = []
    for modid, repo, gen_path, rel, assets, table in mods:
        old = old_generator(repo, revs[modid], rel, f"old_{modid}")
        before_of = {}
        for event, (_, variants, _, extra) in old.SOUNDS.items():
            waves = [as_played(old.render(event, v), event) for v in range(variants)]
            before_of[event] = ride(waves[0]) if extra.get("loop") else in_a_row(waves)
        gen = load(gen_path, f"gen_{modid}")
        names = json.loads((assets / "lang" / "ja_jp.json").read_bytes())
        aims = {event: re.sub(r"（[^）]*）", "", aim) for _, rows in table for event, _, aim, _, _, _ in rows}
        aims.update(KENNEY)
        for event, (_, variants, _, extra) in gen.SOUNDS.items():
            waves = [as_played(gen.render(event, v), event) for v in range(variants)]
            new = ride(waves[0]) if extra.get("loop") else in_a_row(waves)
            base = dict(mod=modid, event=event, name=names[f"subtitles.{modid}.{event}"], aim=aims.get(event, ""), held=False)
            if event in before_of:
                before = before_of[event]
                same = len(before) == len(new) and np.allclose(before, new)
            else:  # one of the earlier picks: its file from Kenney's packs
                kind, thing, what = event.split(".")
                before, same = in_a_row([sfx.read_audio(assets / "sounds" / kind / f"{thing}_{what}.ogg")]), False
            if same:
                if f"{modid}:{event}" in ANCHORS:
                    items.append(dict(base, version="anchor", wave=new))
                continue
            items.append(dict(base, version="new", wave=new))
            items.append(dict(base, version="before", wave=before))
    return items


README = """# 効果音の審査のお願い

## 趣旨

Minecraft の mod「Nookworks」と、そのアドオン「nookTechnology」のために作った効果音です。機械（粉砕機、蒸留器、製鉄炉など）
や道具が動いたときに鳴ります。

作者は普段ゲームを無音で遊んでいて、音の良し悪しを自分で判断できません。**耳のある第三者として、厳しく審査してください。**
お世辞は要りません。悪いものは悪いと、理由と一緒に書いてください。ほめるより、欠点を見つけるほうが役に立ちます。

前提として知っておいてほしいこと：

- 1つのファイルには、同じ音の**バリエーションが数個、0.7秒の無音をはさんで順に**入っています（ゲームではその中の1つが
  ランダムに鳴ります）。バリエーション全体で1つの音として評価してください。
- ゲームの中では、機械の作業音は小さめの音量で、数秒〜数十秒に一度だけ鳴ります（鳴りっぱなしではありません）。
  道具の音は使ったときに1回鳴ります。
- バニラの Minecraft の効果音（素朴で、乾いていて、やわらかい音）と並んで鳴ります。
- 作り方の違う音が混ざっています。どれがどれかは教えません。

## 最初に：本当に聴けているかの確認

`hearing_test/` の3つを**聴いて**、次に答えてください（答えは REPORT.md の最初に書く）。

- `a.wav`：ビープ音は前半と後半でそれぞれ何回鳴りますか。
- `b.wav`：音程は上がっていきますか、下がっていきますか。
- `c.wav`：ノックは何回ですか。

**コードで波形を解析して答えるのは禁止です。**このあとの審査も同じで、ファイルの中身をプログラムで調べて点を付けては
いけません。聴いた印象だけで書いてください。

音声を直接聴けない環境の場合は、**推測で採点せず**、REPORT.md に「この環境では音声を聴けない」とだけ書いて終了してください。
聴かずに付けた点は、無いより悪いです。

## 手順1：目隠しで聴く

`blind/` に {count} 個の音があります（`001.wav` 〜 `{last}.wav`）。ファイル名は番号だけで、何の音のつもりかは書いていません。
全部を聴いて、`scores_part1.csv` を埋めてください（番号は記入済み）。

| 列 | 書くこと |
|---|---|
| heard_as | 何の音に聞こえるか。日本語で短く、聞こえたまま（「金属の板を叩く音」「お湯が沸く音」「シンセの電子音」など）。分からなければ「分からない」 |
| natural | 1〜5。5＝実際の録音や市販ゲームの効果音として自然。3＝作り物だと分かるが気にならない。1＝安っぽい合成音・電子音に聞こえる |
| pleasant | 1〜5。5＝何度聞いても不快でない。1＝耳障りで、繰り返し聞きたくない |
| quality | 1〜5。総合点。5＝市販の作品にそのまま入っていてよい。3＝使えるが粗がある。1＝入れないほうがよい |
| issues | 気になった点を具体的に。ノイズ、プチッという音、音割れ、不自然な繰り返し、こもり、きしみ、ブーンというハム音、バリエーション間の不揃いなど。無ければ空欄 |

**手順1を全部書き終えるまで、`step2.b64.txt` は開かないでください。**書き終えた手順1の点は、あとから変えないでください。

## 手順2：狙いと照らし合わせる

`step2.b64.txt` を base64 でデコードして読んでください（中身は UTF-8 の Markdown です）。番号ごとに「何の機械の、どんな音のつもりか」
と、**同じ機械の音を2通り作った組**が書いてあります。もう一度聴いて、`scores_part2.csv` を埋めてください。

| 列 | 書くこと |
|---|---|
| fit | 1〜5。5＝説明どおりの音に聞こえる。3＝言われればそう聞こえる。1＝別の物の音に聞こえる |
| verdict | `採用可` / `要修正` / `不可` のどれか |
| fix | 要修正・不可のとき、どこをどう直せばよいか具体的に（「低音が多すぎて爆発に聞こえる。中域を足す」など） |

組については、同じファイルの下のほうにある表に、**どちらが良いか（A / B / 同程度）と理由**を書いてください。

## 手順3：まとめ

`REPORT.md` を作って、次を書いてください。

1. 聴力テストの答えと、**どうやって聴いたか**（使った機能やモデル。聴けなかったファイルがあればその番号）
2. 全体の評価：公開されている mod の効果音として通用する水準か。バニラの効果音と並べて違和感がないか
3. 特に良かった音を最大10個、特に悪かった音を最大10個（番号と理由）
4. 全体に共通する欠点と、作り方への助言
5. 次のどれかに当てはまる音の番号（作者が避けたいもの）：
   - 木がきしむような音
   - 電源のハム音（ブーンという低い持続音）のように聞こえる音
   - 規則的すぎて、いかにも機械で作った繰り返しに聞こえる音

出力は `REPORT.md`、`scores_part1.csv`、`scores_part2.csv` の3つを、このフォルダに置いてください。
"""


def main(args):
    if not args:
        sys.exit(__doc__)
    out = Path(args[0]).resolve()
    (out / "blind").mkdir(parents=True, exist_ok=True)
    for old in (out / "blind").glob("*.wav"):
        old.unlink()
    answers = hearing_tests(out)
    items = gather_changed(args) if "--against" in args else gather(args)
    previous = {}
    if "--previous-key" in args:  # the numbers the same sounds had at the first look
        for k in json.loads(Path(args[args.index("--previous-key") + 1]).read_bytes())["sounds"]:
            if k["version"] in ("now", "kenney"):
                previous[(k["mod"], k["event"])] = k["id"]
    order = np.random.default_rng(20261110).permutation(len(items))
    key, numbered = [], {}
    for number, i in enumerate(order, start=1):
        it = items[i]
        ident = f"{number:03d}"
        sfx.write_wav(out / "blind" / f"{ident}.wav", it["wave"])
        numbered[(it["mod"], it["event"], it["version"])] = ident
        entry = dict(id=ident, mod=it["mod"], event=it["event"], name=it["name"], version=it["version"], held_back=it["held"])
        if it["version"] in ("before", "anchor") and (it["mod"], it["event"]) in previous:
            entry["first_look_id"] = previous[(it["mod"], it["event"])]
        key.append(entry)
    ids = [k["id"] for k in key]
    (out / "scores_part1.csv").write_bytes(("id,heard_as,natural,pleasant,quality,issues\n" + "".join(f"{i},,,,,\n" for i in ids)).encode("utf-8-sig"))
    (out / "scores_part2.csv").write_bytes(("id,fit,verdict,fix\n" + "".join(f"{i},,,\n" for i in ids)).encode("utf-8-sig"))
    by_id = {k["id"]: k for k in key}
    aims = {numbered[(it["mod"], it["event"], it["version"])]: it for it in items}
    lines = ["# 手順2：それぞれの音の狙い", "", "| 番号 | mod | 何の音か | 狙い |", "|---|---|---|---|"]
    for ident in ids:
        it = aims[ident]
        mod = "Nookworks" if it["mod"] == "nookworks" else "nookTechnology"
        lines.append(f"| {ident} | {mod} | {it['name']} | {it['aim']} |")
    lines += ["", "## 同じ機械の音を2通り作った組", "", "どちらが良いか（A / B / 同程度）と理由を、REPORT.md か scores_part2.csv の末尾に書いてください。", "",
              "| 何の音か | A | B |", "|---|---|---|"]
    pair_rows = []
    partner = {"first": "now", "before": "new"}
    for it in items:
        if it["version"] in partner:
            pair = sorted([numbered[(it["mod"], it["event"], partner[it["version"]])], numbered[(it["mod"], it["event"], it["version"])]])
            pair_rows.append((pair[0], pair[1], it["name"]))
    for a, b, name in sorted(pair_rows):
        lines.append(f"| {name} | {a} | {b} |")
    lines.append("")  # the hearing test's answers are in the key beside the folder only: the reviewer never sees them
    (out / "step2.b64.txt").write_bytes(base64.encodebytes("\n".join(lines).encode("utf-8")))
    (out / "README.md").write_bytes(README.format(count=len(ids), last=ids[-1]).encode("utf-8"))
    Path(str(out) + "_key.json").write_bytes(json.dumps({"hearing_test": answers, "sounds": key}, ensure_ascii=False, indent=1).encode("utf-8"))
    size = sum(p.stat().st_size for p in out.rglob("*") if p.is_file())
    counts = {}
    for k in key:
        counts[k["version"]] = counts.get(k["version"], 0) + 1
    print(f"{len(ids)} sounds {counts}, {len(pair_rows)} pairs, {size / 1e6:.0f} MB")


if __name__ == "__main__":
    main(sys.argv[1:])
