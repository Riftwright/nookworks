"""A page to listen to the mod's own sounds on, for the author to judge them by ear (a script cannot): every sound
with its variants on buttons, what it stood in for before, what it is meant to be, a verdict and a note for each that
the page keeps and copies out as text.

    python tools/sounds/listen_page.py OUT.html [--nooktech PATH/TO/nook_technology/mod]

The page is in Japanese (it is for the author) and carries the .ogg files inside it, so it is one file to open or
publish. With --nooktech the add-on's sounds are on it too.
"""
import base64
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# group, event, what it played before, what it is meant to be, the volume and pitch the code plays it at, kind
# kind: work = a machine's sound now and then (the "room" button plays these), cue = heard when something happens,
# loop = played round and round
NOOKWORKS = [
    ("機械の作業音", [
        ("block.generator.run", "かまどのパチパチ", "火室で燃料がはぜる。たまに薪が崩れる", 1.0, 1.0, "work"),
        ("block.diesel_generator.run", "トロッコの走行音を低く", "小型ディーゼルのアイドリング。爆発がつながった低い音と排気、バルブのカタカタ", 1.0, 1.0, "work"),
        ("block.crusher.run", "砥石を低く", "あごが岩を噛み砕く。2〜3回の噛み込み", 1.0, 1.0, "work"),
        ("block.compressor.run", "ピストンを低く", "吸気、ラムのドスン、排気のプシュー", 1.0, 1.0, "work"),
        ("block.recycler.run", "金床の落下＋砥石", "歯付きローラーが金属を噛みちぎる。ときどき破片がカランと通る", 1.0, 1.0, "work"),
        ("block.stone_maker.run", "溶岩が消える音を高く", "溶岩に水が触れるジュッ、できた石のコツン", 1.0, 1.0, "work"),
        ("block.electric_furnace.run", "ビーコンを高く", "電熱線の低いブーンという音と熱い空気。熱い金属がチッと鳴る", 1.0, 1.0, "work"),
        ("block.laser.run", "ビーコンをさらに高く", "細く伸びる高い音と、空気が焦げるチリチリ", 1.0, 1.0, "work"),
        ("block.retort.run", "かまど＋醸造台", "密閉した鉄の釜の中で煮える、こもった音。下で火がはぜる", 1.0, 1.0, "work"),
        ("block.stirring_tank.run", "渦潮の音", "羽根が液体をかき回す。うねりと泡", 1.0, 1.0, "work"),
        ("block.distiller.run", "醸造台", "ガラス器具の中の軽い沸騰と、受け器に落ちるしずく", 1.0, 1.0, "work"),
        ("block.electrolytic_cell.run", "泡がはじける音", "電極から出る細かい泡のシュワシュワ", 1.0, 1.0, "work"),
        ("block.water_pump.run", "水の環境音", "ポンプの2ストローク。弁のカタン、水を吸い上げるゴボッ", 1.0, 1.0, "work"),
        ("block.ventilation_fan.run", "ビーコンを高く、ごく小さく", "羽根の低い回転音と、やわらかい風音", 1.0, 1.0, "work"),
        ("block.printing_press.run", "クラフターの音", "レバー、圧盤のドン、紙を引く音、レバーが戻る", 1.0, 1.0, "work"),
        ("block.drive_wheel.turn", "以前選んだ低いうなり（Kenney の音）", "ベルトの継ぎ目が1回転ごとにコトッと当たり、その間をベルトが擦れる", 1.0, 1.0, "work"),
    ]),
    ("手回しの機械と台所", [
        ("block.rice_polisher.turn", "砂を踏む音", "米どうしがこすれる乾いた音（0.2秒。短さはそのまま）", 1.0, 1.0, "cue"),
        ("block.hand_quern.turn", "砥石", "石臼が石の上を回るザリザリ。粒が砕ける", 0.35, 1.0, "cue"),
        ("block.oil_press.turn", "砥石を低く", "種の殻がパリパリと割れる乾いた音。木のきしみは入れていない", 0.35, 1.0, "cue"),
        ("block.iron_pot.fry", "焚き火のパチパチを高く", "油のジュージュー。ときどき大きくはねる", 0.5, 1.0, "work"),
        ("block.iron_pot.boil", "泡が1つはじける音", "鍋がぐつぐつ煮える（鳴る頻度も揚げ物と同じくらいに上げた）", 0.4, 1.0, "work"),
        ("block.grill.sizzle", "焚き火のパチパチを高く", "脂が炭に落ちるジュッ", 0.4, 1.0, "work"),
    ]),
    ("小物と道具", [
        ("block.repair_box.mend", "鍛冶台", "小さな金床を小さなハンマーで2〜3回", 1.0, 1.0, "cue"),
        ("block.repair_box.done", "金床を高く", "修理完了のチーン。工房の向こうからでも分かる", 1.0, 1.0, "cue"),
        ("block.dice.roll", "骨ブロックを叩く音", "机の上で跳ねて、だんだん細かく転がって止まる", 0.6, 1.6, "cue"),
        ("item.camera.shutter", "レバーを高く", "真鍮カメラのカシャッ。レリーズ、羽根の走る音、閉じるカチッ", 0.9, 1.0, "cue"),
        ("item.wrench.turn", "鉄のトラップドア", "ラチェットのカチカチカチ、最後に1歯かかる", 0.6, 1.0, "cue"),
        ("item.oil_can.squirt", "ハチミツブロックを滑る音", "油差しの底を押すペコ、戻るペコ", 0.5, 1.0, "cue"),
    ]),
    ("新しい機能の音", [
        ("block.machine.stop", "ビーコンが止まる音を高く", "機械が止まった合図。接触器がカチッと落ち、駆動部がゴトン…ゴトと止まる。警報ではなく機械そのものの音", 1.0, 1.0, "cue"),
        ("block.machine_bell.ring", "村の鐘を高く", "機械の横に付ける小さな電鈴。ジリリリンと1秒ほど鳴って余韻が消える", 1.0, 1.0, "cue"),
        ("block.sorter.sort", "木のボタンを高く", "仕分け口の小さな蓋がパタッと閉じる", 1.0, 1.0, "cue"),
        ("block.seat.sit", "木を置く音を低く", "服が擦れ、体重が木の座面にトンと乗る。きしみは入れていない", 1.0, 1.0, "cue"),
        ("block.wind_chime.chime", "アメジストの響き", "金属の筒が数本、風で続けて鳴る（ソ・ラ・シ・レ・ミの5音なので、どの組み合わせでも濁らない）", 1.0, 1.0, "cue"),
    ]),
    ("参考：以前あなたが選んだ音（そのまま残してある）", [
        ("block.motor.run", None, "モーターの低いうなり。ほかの作業音の大きさはこれを目安にした", 1.0, 1.0, "ref"),
        ("block.spawner_controller.run", None, "スポナーコントローラー", 1.0, 1.0, "ref"),
        ("block.trash_can.swallow", None, "ゴミ箱", 1.0, 1.0, "ref"),
    ]),
]

NOOKTECH = [
    ("機械の作業音", [
        ("block.electric_sifter.run", "砂利と砂を叩く音", "ふるいが左右に振られ、小石が転がる。受け皿がカタカタ", 1.0, 1.0, "work"),
        ("block.fishing_machine.run", "釣り糸を巻く音", "リールのラチェットがカチカチと回る", 1.0, 1.0, "work"),
        ("block.auto_brewer.run", "醸造台", "ガラス瓶の中の沸騰と、バーナーの息", 1.0, 1.0, "work"),
        ("block.wire_drawer.run", "鎖＋砥石", "張りつめた線が少しずつ送られるチン、チン。ダイスのかすかな擦れ", 1.0, 1.0, "work"),
        ("block.auto_crafter.run", "クラフター＋ピストン", "ラムが板に落ちるガシャン（動きに合わせて鳴る）", 1.0, 1.0, "cue"),
        ("block.chunk_loader.run", "ビーコンを低く", "低いうなりが静かに続く", 1.0, 1.0, "work"),
        ("block.mob_data_bank.run", "コンパレーター", "リールが少しずつ送られるコツ、コツ", 1.0, 1.0, "work"),
        ("block.mob_data_bank.rewind", "クロスボウ＋砥石", "テープを巻き戻すキュルルル、止まるカタン", 0.8, 1.1, "cue"),
        ("block.centrifuge.run", "トロッコ＋ビーコン", "高速回転のなめらかなうなりと風", 1.0, 1.0, "work"),
        ("block.dye_works.run", "泡＋醸造台", "染液の釜がゆっくり大きな泡で煮える", 1.0, 1.0, "work"),
        ("block.storage_terminal.run", "ビーコン", "端末の小さなファンと、検索のかすかなチチッ", 1.0, 1.0, "work"),
    ]),
    ("掘る・置く・運ぶ", [
        ("block.quarry.run", "砥石を低く", "ドリルの刃が岩を削るガリガリ。岩が割れて落ちる", 1.0, 1.0, "work"),
        ("block.block_breaker.run", "砥石＋石を叩く音", "のみを連打して石を割る", 1.0, 1.0, "work"),
        ("block.block_placer.run", "ピストン", "空気が入り、ブロックを押し出してコトンと置く", 1.0, 1.0, "work"),
        ("block.conveyor_belt.run", "トロッコを高く", "ローラーの上を流れる静かな転がり音。継ぎ目がときどきコトッ", 1.0, 1.0, "work"),
        ("block.loading_arm.pick", "ピストンが戻る音", "関節のウィン、指がカチッと閉じる", 1.0, 1.0, "cue"),
        ("block.loading_arm.drop", "バンドルに入れる音", "指が開き、機械のホッパーにコトンと落ちる", 1.0, 1.0, "cue"),
    ]),
    ("操作したときの音", [
        ("block.machine.wrench_set", "鉄のトラップドア", "設定がカチャッと切り替わる手応え", 1.0, 1.0, "cue"),
        ("block.shaft.shift", "鉄のトラップドア", "歯が一瞬ガリッと当たってから、ガコンとかみ合う", 1.0, 1.0, "cue"),
    ]),
    ("製鉄所", [
        ("block.brick_furnace.formed", "鉄のドアを低く", "重い鉄扉がゴーンと閉まり、かんぬきが落ちる", 1.0, 1.0, "cue"),
        ("block.brick_furnace.unformed", "鉄のドアを低く", "かんぬきが外れ、扉が開く", 1.0, 1.0, "cue"),
        ("block.brick_furnace.roar", "溶鉱炉のパチパチ", "高炉の深いごうごうという燃焼音と送風", 1.0, 1.0, "work"),
        ("block.coke_oven.run", "燻製器の煙", "扉の奥で石炭がくすぶる。小さなパチッと、ガスの息", 1.0, 1.0, "work"),
        ("block.sinter_machine.run", "かまどのパチパチ", "バーナーの下で鉱石が鋭くパチパチはぜる", 1.0, 1.0, "work"),
        ("block.converter.run", "炎の環境音", "溶けた鉄に空気を吹き込む激しい噴流と火花", 1.0, 1.0, "work"),
        ("block.converter.done", "ファイヤーチャージ", "轟音が沈んで消えていく。最後の火花", 1.0, 1.0, "cue"),
        ("block.arc_furnace.run", "リスポーンアンカー", "アークが途切れ途切れにバチバチ、ジジッと弾ける", 1.0, 1.0, "work"),
        ("block.arc_furnace.done", "リスポーンアンカーが減る音", "アークが消えていき、遮断器がガコンと落ちる", 1.0, 1.0, "cue"),
        ("block.blast_furnace.tap", "溶岩が消える音", "出銑口を突くガチャン、続いて溶けた鉄が流れ出る", 1.0, 1.0, "cue"),
        ("block.tar_still.run", "溶岩の泡", "タールの大きく重い泡がぼこっ、ぼこっ", 1.0, 1.0, "work"),
        ("block.desulfurizer.run", "火が消える音", "配管を抜けるガスのシュー", 1.0, 1.0, "work"),
    ]),
    ("ジップラインとロープウェイ", [
        ("entity.zipline_trolley.hook", "鎖を置く音", "滑車がケーブルにカチンと掛かり、ケーブルが鳴る", 1.0, 1.0, "cue"),
        ("entity.zipline_trolley.roll", "トロッコを高く", "滑車がケーブルを走る（ループ。速いほど高く大きく鳴る。ボタンは加速して減速する6秒）", 0.6, 1.0, "loop"),
        ("entity.zipline_trolley.unhook", "鎖が壊れる音", "滑車が外れ、ケーブルが少し跳ねる", 1.0, 1.0, "cue"),
        ("entity.zipline_trolley.blocked", "金床の落下を高く", "障害物に当たってガツンと止まる", 1.0, 1.0, "cue"),
        ("block.ropeway_station.run", "トロッコを低く", "駆動の低いうなりと、索が滑車を流れる音。継ぎ目がカチッと通る", 1.0, 1.0, "work"),
        ("block.ropeway_tower.clatter", "鎖を低く", "搬器が鉄塔の滑車を越えるカタカタカタン。鉄塔が響く", 0.9, 1.0, "cue"),
        ("entity.gondola.rattle", "鎖を踏む音", "走行中のゴンドラの吊り金具がカタカタ", 0.8, 0.7, "cue"),
    ]),
]


def gather(title, key, assets, modid, groups):
    table = json.loads((assets / "sounds.json").read_bytes())
    names = json.loads((assets / "lang" / "ja_jp.json").read_bytes())
    out = []
    held = []
    for group, rows in groups:
        sounds = []
        for event, was, aim, code_volume, pitch, kind in rows:
            files = []
            volume = 1.0
            entries, scale = table[event]["sounds"], 1.0
            if len(entries) == 1 and isinstance(entries[0], dict) and entries[0].get("type") == "event" and entries[0]["name"].startswith(modid + ":"):
                # one that plays another of ours (the add-on's Blast Furnace roars as its Coke Oven smoulders)
                scale = entries[0].get("volume", 1.0)
                entries = table[entries[0]["name"].split(":", 1)[1]]["sounds"]
            for entry in entries:
                if isinstance(entry, dict) and entry.get("type") == "event":
                    continue
                name = entry["name"] if isinstance(entry, dict) else entry
                volume = scale * (entry.get("volume", 1.0) if isinstance(entry, dict) else 1.0)
                path = assets / "sounds" / (name.split(":", 1)[1] + ".ogg")
                files.append(base64.b64encode(path.read_bytes()).decode("ascii"))
            if not files:  # not ours (yet): it plays what it played, or the placeholder a new event waits behind
                held.append(names[f"subtitles.{modid}.{event}"])
                continue
            sounds.append({"id": event, "name": names[f"subtitles.{modid}.{event}"], "was": was, "aim": aim,
                           "vol": round(volume * code_volume, 3), "pitch": pitch, "kind": kind, "files": files})
        out.append({"group": group, "sounds": sounds})
    return {"title": title, "key": key, "groups": out, "held": held}


PAGE = r"""<title>Nookworks 試聴室</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DotGothic16&family=Zen+Kaku+Gothic+New:wght@400;500;700&display=swap">
<style>
/* layout: one column of rows, a sound a row - what it is on the left, its variants to press in the middle, a verdict on
   the right; a bar that stays at the top holds how it is heard and the way out for the verdicts */
:root {
  --bg: #e9edee; --panel: #f7f9f9; --ink: #1c2529; --muted: #5a686e; --line: #cdd5d8;
  --brass: #8a5d0c; --glow: #e6a122; --press: #dfe5e7;
  --ok: #2c774a; --fix: #a85c12; --no: #ad3636;
  --display: "DotGothic16", "Zen Kaku Gothic New", "Yu Gothic UI", "Hiragino Sans", "Meiryo", sans-serif;
  --body: "Zen Kaku Gothic New", "Yu Gothic UI", "Hiragino Sans", "Meiryo", sans-serif;
  --mono: ui-monospace, "Cascadia Mono", "Consolas", "Menlo", monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #14191c; --panel: #1c2327; --ink: #e5eaeb; --muted: #94a1a7; --line: #323c41;
  --brass: #e2ad4c; --glow: #f3bb3d; --press: #29333a;
  --ok: #62c28a; --fix: #e6a055; --no: #ee7b7b; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #14191c; --panel: #1c2327; --ink: #e5eaeb; --muted: #94a1a7; --line: #323c41;
  --brass: #e2ad4c; --glow: #f3bb3d; --press: #29333a;
  --ok: #62c28a; --fix: #e6a055; --no: #ee7b7b; color-scheme: dark }
body { background: var(--bg); color: var(--ink); font-family: var(--body); font-size: 15px; line-height: 1.65 }
body::before { content: ""; position: fixed; inset: 0 0 auto; height: env(safe-area-inset-top, 0px); z-index: 10; background: var(--bg) }
.wrap { max-width: 1040px; margin: 0 auto; padding-block: 28px 64px;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)) }
h1 { font-family: var(--display); font-weight: 400; font-size: 2rem; margin: 0 0 6px; letter-spacing: .04em; text-wrap: balance }
.lede { color: var(--muted); max-width: 44em; margin: 0 0 18px }
.lede b { color: var(--ink); font-weight: 700 }
.bar { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--bg); border-bottom: 1px solid var(--line);
  display: flex; flex-wrap: wrap; gap: 10px 18px; align-items: center; padding-block: 10px; margin-bottom: 8px }
.seg { display: inline-flex; border: 1px solid var(--line); border-radius: 6px; overflow: hidden }
.seg button { border: 0; background: var(--panel); color: var(--muted); padding: 6px 12px; font: inherit; font-size: 13px; cursor: pointer }
.seg button + button { border-left: 1px solid var(--line) }
.seg button[aria-pressed="true"] { background: var(--brass); color: var(--bg); font-weight: 700 }
.bar label { font-size: 13px; color: var(--muted); display: inline-flex; gap: 8px; align-items: center }
.bar input[type=range] { width: 120px; accent-color: var(--brass) }
.btn { border: 1px solid var(--line); background: var(--panel); color: var(--ink); border-radius: 6px; padding: 6px 12px; font: inherit; font-size: 13px; cursor: pointer }
.btn:hover, .seg button:hover, .v:hover { filter: brightness(1.06) }
.btn.main { border-color: var(--brass); color: var(--brass); font-weight: 700 }
.bar .push { margin-left: auto }
button:focus-visible, input:focus-visible { outline: 2px solid var(--glow); outline-offset: 2px }
.note { font-size: 13px; color: var(--muted); margin: 6px 0 0 }
.note.warn { color: var(--no) }
h2 { font-family: var(--display); font-weight: 400; font-size: 1.45rem; margin: 40px 0 0; letter-spacing: .04em; display: flex; flex-wrap: wrap; gap: 8px 16px; align-items: baseline }
h2 small { font-family: var(--body); font-size: 13px; color: var(--muted) }
h3 { font-size: 13px; font-weight: 700; color: var(--brass); letter-spacing: .08em; margin: 26px 0 4px; padding-bottom: 6px; border-bottom: 2px solid var(--line) }
.row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px 20px; padding: 12px 0; border-bottom: 1px solid var(--line); align-items: start }
.what { min-width: 0 }
.what .n { font-weight: 700; font-size: 15px }
.what .id { font-family: var(--mono); font-size: 11.5px; color: var(--muted); margin-left: 8px; overflow-wrap: anywhere }
.what p { margin: 2px 0 0; font-size: 13.5px; color: var(--ink) }
.what .was { color: var(--muted); font-size: 12.5px }
.do { display: flex; flex-direction: column; gap: 8px; align-items: flex-end }
.vs { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end }
.v { min-width: 44px; height: 36px; border-radius: 6px; border: 1px solid var(--line); background: var(--press); color: var(--ink);
  font-family: var(--mono); font-size: 13px; cursor: pointer; font-variant-numeric: tabular-nums }
.v.on { background: var(--glow); border-color: var(--glow); color: #1c1503 }
.v.all { font-family: var(--body); padding: 0 10px; color: var(--muted) }
.verdict { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end; align-items: center }
.verdict .seg button[aria-pressed="true"][data-v="ok"] { background: var(--ok) }
.verdict .seg button[aria-pressed="true"][data-v="fix"] { background: var(--fix) }
.verdict .seg button[aria-pressed="true"][data-v="no"] { background: var(--no) }
.verdict input { width: 210px; max-width: 100%; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); color: var(--ink); padding: 6px 9px; font: inherit; font-size: 13px }
.out { margin-top: 14px }
.out textarea { width: 100%; box-sizing: border-box; min-height: 160px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); color: var(--ink); font-family: var(--mono); font-size: 12.5px; padding: 10px }
@media (max-width: 720px) {
  .row { grid-template-columns: minmax(0, 1fr) }
  .do { align-items: flex-start } .vs, .verdict { justify-content: flex-start }
  .bar .push { margin-left: 0 } h1 { font-size: 1.6rem }
}
@media (prefers-reduced-motion: no-preference) { .v { transition: background .12s } }
</style>
<div class="wrap">
  <h1>Nookworks 試聴室</h1>
  <p class="lede">機械と道具のために計算で作った効果音です（録音や他作品の音は使っていません）。どちらのmodの音も、あなたが今までの音と聞き比べて選んだものです。番号のボタンで鳴ります。同じ音にも数種類あり、ゲームではその中からランダムに選ばれます。気になる音があれば「直して」「没」を付けて「評価をコピー」で送れます。印とメモはこのブラウザーに残ります。</p>
  <div class="bar" role="toolbar" aria-label="聴き方">
    <div class="seg" id="mode">
      <button type="button" id="mode-flat" aria-pressed="true" data-m="flat">同じ大きさで聴く</button>
      <button type="button" id="mode-game" aria-pressed="false" data-m="game">ゲーム内の音量で聴く</button>
    </div>
    <label for="vol">音量 <input type="range" id="vol" min="0" max="100" value="80"></label>
    <button type="button" class="btn" id="stop">止める</button>
    <button type="button" class="btn main push" id="copy">評価をコピー</button>
  </div>
  <p class="note">「ゲーム内の音量」は、音ごとに決めた音量の差をそのまま再現します。作業音はかなり小さく、道具の音は大きめです。音質を聴くときは「同じ大きさ」、バランスを聴くときは「ゲーム内の音量」が向いています。</p>
  <p class="note warn" id="fail" hidden>このブラウザーでは音を再生できませんでした。Chrome か Edge で開いてみてください。</p>
  <div class="out" id="out" hidden><textarea id="outtext" readonly aria-label="評価の内容"></textarea><p class="note">コピーできなかった場合は、上の文字を選んでコピーしてください。</p></div>
  <main id="list"></main>
</div>
<script type="application/json" id="data">__DATA__</script>
<script>
(function () {
  var DATA = JSON.parse(document.getElementById('data').textContent);
  var KEY = 'nookworks-sound-verdicts';
  var marks = {};
  try { marks = JSON.parse(localStorage.getItem(KEY) || '{}') || {}; } catch (e) { marks = {}; }
  function keep() { try { localStorage.setItem(KEY, JSON.stringify(marks)); } catch (e) {} }

  var ctx = null, master = null, mode = 'flat', live = [], timers = [], cache = {};
  function ready() {
    if (!ctx) {
      var AC = window.AudioContext || window.webkitAudioContext;
      ctx = new AC(); master = ctx.createGain(); master.connect(ctx.destination);
      master.gain.value = document.getElementById('vol').value / 100;
    }
    if (ctx.state === 'suspended') { ctx.resume(); }
  }
  function decode(sound, i) {
    var k = sound.id + '#' + i;
    if (cache[k]) { return cache[k]; }
    var bin = atob(sound.files[i]), bytes = new Uint8Array(bin.length);
    for (var j = 0; j < bin.length; j++) { bytes[j] = bin.charCodeAt(j); }
    cache[k] = ctx.decodeAudioData(bytes.buffer);
    return cache[k];
  }
  function stopAll() {
    timers.forEach(clearTimeout); timers = [];
    live.forEach(function (s) { try { s.stop(); } catch (e) {} }); live = [];
    document.querySelectorAll('.v.on').forEach(function (b) { b.classList.remove('on'); });
  }
  // one variant played; opts.game forces the in-game volume, opts.vary the pitch a work sound gets at random
  function play(sound, i, button, opts) {
    opts = opts || {};
    ready();
    return decode(sound, i).then(function (buffer) {
      var src = ctx.createBufferSource(), gain = ctx.createGain(), now = ctx.currentTime;
      var game = opts.game || mode === 'game';
      var level = game ? sound.vol : 1;
      var rate = sound.pitch * (opts.vary ? 0.9 + Math.random() * 0.2 : 1);
      src.buffer = buffer; src.connect(gain); gain.connect(master);
      if (sound.kind === 'loop') {  // a ride: off slowly, up to full speed, slowing again - pitch and volume as the game sets them
        src.loop = true;
        src.playbackRate.setValueAtTime(0.7, now); src.playbackRate.linearRampToValueAtTime(1.3, now + 3); src.playbackRate.linearRampToValueAtTime(0.7, now + 6);
        var lo = game ? 0.15 * sound.vol / 0.6 : 0.3, hi = game ? 0.7 * sound.vol / 0.6 : 1;
        gain.gain.setValueAtTime(0.0001, now); gain.gain.linearRampToValueAtTime(lo, now + 0.15);
        gain.gain.linearRampToValueAtTime(hi, now + 3); gain.gain.linearRampToValueAtTime(lo, now + 5.8); gain.gain.linearRampToValueAtTime(0.0001, now + 6);
        src.start(now); src.stop(now + 6.05);
      } else {
        src.playbackRate.value = rate; gain.gain.value = level; src.start(now);
      }
      live.push(src);
      if (button) { button.classList.add('on'); }
      return new Promise(function (done) {
        src.onended = function () {
          live = live.filter(function (s) { return s !== src; });
          if (button) { button.classList.remove('on'); }
          done();
        };
      });
    }).catch(function () { document.getElementById('fail').hidden = false; });
  }
  function playAll(sound, buttons) {
    stopAll();
    var chain = Promise.resolve();
    sound.files.forEach(function (_, i) {
      chain = chain.then(function () { return play(sound, i, buttons[i]); }).then(function () {
        return new Promise(function (r) { timers.push(setTimeout(r, 350)); });
      });
    });
  }
  // a room of machines for half a minute: their work sounds now and then, at the game's volumes, each a little higher or lower
  function room(mod) {
    stopAll(); ready();
    var pool = [];
    mod.groups.forEach(function (g) { g.sounds.forEach(function (s) { if (s.kind === 'work') { pool.push(s); } }); });
    var at = 0;
    while (at < 30000) {
      (function (when) {
        timers.push(setTimeout(function () {
          var s = pool[Math.floor(Math.random() * pool.length)];
          play(s, Math.floor(Math.random() * s.files.length), null, { game: true, vary: true });
        }, when));
      })(at);
      at += 500 + Math.random() * 1500;
    }
  }

  function el(tag, cls, text) { var e = document.createElement(tag); if (cls) { e.className = cls; } if (text != null) { e.textContent = text; } return e; }
  var list = document.getElementById('list');
  DATA.forEach(function (mod) {
    var h2 = el('h2', null, mod.title);
    var count = 0; mod.groups.forEach(function (g) { g.sounds.forEach(function (s) { if (s.kind !== 'ref') { count += s.files.length; } }); });
    h2.appendChild(el('small', null, count + ' ファイル'));
    var sim = el('button', 'btn', '工房の空気を30秒（作業音がときどき鳴る・ゲーム内の音量）');
    sim.type = 'button'; sim.id = 'room-' + mod.key; sim.addEventListener('click', function () { room(mod); });
    h2.appendChild(sim);
    list.appendChild(h2);
    if (mod.held && mod.held.length) {
      list.appendChild(el('p', 'note', '自作の音にしていないもの（今までどおりの音か、聞き比べ待ちの仮の音が鳴ります）：' + mod.held.join('、')));
    }
    mod.groups.forEach(function (g) {
      list.appendChild(el('h3', null, g.group));
      g.sounds.forEach(function (s) {
        var row = el('div', 'row'), what = el('div', 'what'), top = el('div');
        top.appendChild(el('span', 'n', s.name)); top.appendChild(el('span', 'id', s.id));
        what.appendChild(top); what.appendChild(el('p', null, s.aim));
        if (s.was) { what.appendChild(el('p', 'was', '今まで：' + s.was)); }
        var side = el('div', 'do'), vs = el('div', 'vs'), buttons = [];
        s.files.forEach(function (_, i) {
          var b = el('button', 'v', '▶ ' + (i + 1)); b.type = 'button'; b.id = 'p-' + mod.key + '-' + s.id + '-' + i;
          b.setAttribute('aria-label', s.name + ' ' + (i + 1) + ' を再生');
          b.addEventListener('click', function () { stopAll(); play(s, i, b); });
          buttons.push(b); vs.appendChild(b);
        });
        if (s.files.length > 1) {
          var all = el('button', 'v all', '順に'); all.type = 'button'; all.id = 'a-' + mod.key + '-' + s.id;
          all.addEventListener('click', function () { playAll(s, buttons); }); vs.appendChild(all);
        }
        side.appendChild(vs);
        if (s.kind !== 'ref') {
          var id = mod.key + ':' + s.id, mine = marks[id] || {};
          var verdict = el('div', 'verdict'), seg = el('div', 'seg');
          [['ok', 'いい'], ['fix', '直して'], ['no', '没']].forEach(function (pair) {
            var b = el('button', null, pair[1]); b.type = 'button'; b.dataset.v = pair[0]; b.id = 'v-' + id + '-' + pair[0];
            b.setAttribute('aria-pressed', mine.v === pair[0] ? 'true' : 'false');
            b.addEventListener('click', function () {
              var m = marks[id] || (marks[id] = {});
              m.v = m.v === pair[0] ? null : pair[0];
              seg.querySelectorAll('button').forEach(function (o) { o.setAttribute('aria-pressed', o.dataset.v === m.v ? 'true' : 'false'); });
              keep();
            });
            seg.appendChild(b);
          });
          var memo = el('input'); memo.type = 'text'; memo.id = 'c-' + id; memo.placeholder = 'ひとこと（任意）'; memo.value = mine.c || '';
          memo.setAttribute('aria-label', s.name + ' へのメモ');
          memo.addEventListener('input', function () { (marks[id] || (marks[id] = {})).c = memo.value; keep(); });
          verdict.appendChild(seg); verdict.appendChild(memo); side.appendChild(verdict);
        }
        row.appendChild(what); row.appendChild(side); list.appendChild(row);
      });
    });
  });

  document.getElementById('mode').addEventListener('click', function (e) {
    var b = e.target.closest('button'); if (!b) { return; }
    mode = b.dataset.m;
    this.querySelectorAll('button').forEach(function (o) { o.setAttribute('aria-pressed', o === b ? 'true' : 'false'); });
  });
  document.getElementById('vol').addEventListener('input', function () { if (master) { master.gain.value = this.value / 100; } });
  document.getElementById('stop').addEventListener('click', stopAll);
  document.getElementById('copy').addEventListener('click', function () {
    var words = { ok: 'いい', fix: '直して', no: '没' }, lines = [];
    DATA.forEach(function (mod) {
      var part = [];
      mod.groups.forEach(function (g) { g.sounds.forEach(function (s) {
        var m = marks[mod.key + ':' + s.id];
        if (m && (m.v || (m.c && m.c.trim()))) { part.push(s.id + '（' + s.name + '）: ' + (m.v ? words[m.v] : '（印なし）') + (m.c && m.c.trim() ? ' - ' + m.c.trim() : '')); }
      }); });
      if (part.length) { lines.push('[' + mod.title + ']'); lines = lines.concat(part); lines.push(''); }
    });
    var text = lines.length ? lines.join('\n') : 'まだ印もメモもありません。';
    var box = document.getElementById('outtext'), button = this;
    box.value = text; document.getElementById('out').hidden = false;
    function fallback() { box.focus(); box.select(); }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () {
        button.textContent = 'コピーしました'; setTimeout(function () { button.textContent = '評価をコピー'; }, 1800);
      }).catch(fallback);
    } else { fallback(); }
  });
})();
</script>
"""


def main(args):
    if not args:
        sys.exit(__doc__)
    out = Path(args[0])
    mods = [gather("Nookworks", "nookworks", ROOT / "src/main/resources/assets/nookworks", "nookworks", NOOKWORKS)]
    if "--nooktech" in args:
        tec = Path(args[args.index("--nooktech") + 1])
        mods.append(gather("nookTechnology", "nooktech", tec / "src/main/resources/assets/nooktech", "nooktech", NOOKTECH))
    data = json.dumps(mods, ensure_ascii=False).replace("</", "<\\/")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(PAGE.replace("__DATA__", data).encode("utf-8"))
    print(f"{out}  {out.stat().st_size / 1e6:.1f} MB, {sum(len(s['files']) for m in mods for g in m['groups'] for s in g['sounds'])} files")


if __name__ == "__main__":
    main(sys.argv[1:])
