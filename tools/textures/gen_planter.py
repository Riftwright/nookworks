"""Textures for the Planter: one of the machines, a seed drill in a steel cabinet.

It was a wooden seed box, the farm's own materials (it is the one farm machine). But it runs on power, opens the
machines' screen and carries their lamp, and beside the Crusher and the Stone Maker the box looked like something else
(the user, 2026-11-14: 他の機械と揃えて、シャーシとか使って、見た目も機械っぽく作っていい気がする). So it is drawn in
the family tools/textures/gen_machine_faces.py draws: the near-black frame with pale studs, a dark steel panel, brass
where there is a fitting - and what it is for shows in the brass: seed.

- planter_front.png   a seed window in a brass frame, the seed heaped against the glass, and under it the brass drill
                      tube the seed drops through (the face the field is in front of); the status lamp where
                      gen_machine_faces.LAMPS has it
- planter_side.png    the sides and back: louvres, and a brass hatch low down
- planter_top.png     the hopper's mouth: a brass rim round a dark funnel heaped with seed - where seed goes in
- planter_bottom.png  the machines' own underside

Every face is 16x16 and fully opaque: the block is a plain cube. Drawn flat, then finished with texkit.shade as the
other machines' faces are; the seed's three colours are kept as drawn.

Run from the repo root:  python tools/textures/gen_planter.py
"""
import gen_machine_faces as m
import texkit

SEED = ((136, 120, 62), (168, 150, 84), (198, 180, 110))
# the seed in the hopper's mouth, from above: t the shade, u the body, v the light
HEAP = ["uvvuvu",
        "vuvvut",
        "uvuutu",
        "vuutut",
        "utuutt",
        "tuttut"]
TONE = {"t": SEED[0], "u": SEED[1], "v": SEED[2]}


def front():
    img = m.blank()
    m.cabinet_elev(img, 0, 15)
    # the seed window: brass round dark glass, the seed heaped against it
    m.rect(img, 3, 3, 12, 8, m.BRASS_LO)
    m.hline(img, 3, 12, 3, m.BRASS_HI)
    m.vline(img, 3, 3, 8, m.BRASS)
    m.hline(img, 4, 12, 8, m.BRASS_DK)
    m.vline(img, 12, 4, 8, m.BRASS_DK)
    for y in range(4, 8):
        for x in range(4, 12):
            m.put(img, x, y, m.GLASS_DK)
    for x, top in zip(range(4, 12), (7, 6, 6, 5, 6, 6, 7, 7)):
        for y in range(top, 8):
            m.put(img, x, y, SEED[2] if y == top else SEED[1] if (x + y) % 2 else SEED[0])
    m.put(img, 5, 4, m.GLASS_HI)
    m.put(img, 6, 4, m.GLASS)
    # the drill tube: a collar, the pipe, its open mouth over the field
    m.hline(img, 5, 10, 10, m.BRASS_HI)
    m.put(img, 10, 10, m.BRASS_LO)
    for y in (11, 12):
        m.put(img, 6, y, m.BRASS)
        m.put(img, 7, y, m.BRASS_HI)
        m.put(img, 8, y, m.BRASS)
        m.put(img, 9, y, m.BRASS_DK)
    m.hline(img, 5, 10, 13, m.BRASS_LO)
    m.put(img, 5, 13, m.BRASS)
    m.hline(img, 6, 9, 13, m.BAR_SH)
    return img


def side():
    img = m.blank()
    m.cabinet_elev(img, 0, 15)
    for y in (4, 7):  # louvres
        m.hline(img, 4, 11, y, m.BAR_SH)
        m.hline(img, 4, 11, y + 1, m.DARK_HI)
    m.rect(img, 5, 10, 10, 13, m.BRASS_LO)  # the hatch the seed is looked at through
    m.hline(img, 5, 10, 10, m.BRASS_HI)
    m.vline(img, 5, 10, 13, m.BRASS)
    m.hline(img, 6, 10, 13, m.BRASS_DK)
    m.put(img, 9, 12, m.BOLT)
    return img


def top():
    img = m.blank()
    m.plan_cabinet(img)
    m.rect(img, 3, 3, 12, 12, m.BRASS_LO)  # the hopper's rim
    m.hline(img, 3, 12, 3, m.BRASS_HI)
    m.vline(img, 3, 3, 12, m.BRASS_HI)
    m.hline(img, 4, 12, 12, m.BRASS_DK)
    m.vline(img, 12, 4, 12, m.BRASS_DK)
    m.rect(img, 4, 4, 11, 11, m.BAR_SH)   # its walls: the far ones in shadow, the near ones catching the light
    m.hline(img, 5, 11, 11, m.DARK_HI)
    m.vline(img, 11, 5, 11, m.DARK_HI)
    for j, row in enumerate(HEAP):
        for i, ch in enumerate(row):
            m.put(img, 5 + i, 5 + j, TONE[ch])
    return img


def main():
    faces = {"front": front(), "side": side(), "top": top(), "bottom": m.machine_bottom()}
    keep = m.FRAME_KEEP + SEED
    for i, (name, img) in enumerate(faces.items()):
        stuff = m.material()
        stuff.update({c: "plain" for c in SEED})
        done = texkit.shade(img, 700 + i, keep=keep, material=stuff, **texkit.SMOOTH)
        if name == "front":
            m.lamp(done, *m.LAMPS["planter_front"])  # its status lamp, lit while it plants: after the finish, so it stays exact
        done.save(m.BLOCK / f"planter_{name}.png")
    print("wrote the planter's four faces")


if __name__ == "__main__":
    main()
