#!/usr/bin/env python3
"""1.1 cleanliness update assets: droppings, graffiti (4 designs), the wall air freshener, the 3-wide storefront shutter and ten decor objects
(potted palm, neon sign, shop rug, wall clock, gumball machine, aquarium, floor lamp, card banner, cardboard shopkeeper,
welcome mat). Writes textures, block / item models, blockstates, loot tables and block tags.

All art is our own 16x16 pixel art generated here (no vanilla, Pokemon or Create assets). Models face north (-Z) like
the other shop furniture; wall objects have their back on the south side (z = 16).
Code: shop/CleanBlocks.java, PoopBlock.java, WallFreshenerBlock.java, WideDoorBlock.java, DecorPropBlock.java.

Usage: python tools/gen_clean_assets.py  (only writes the files of this area)
"""
import json
import math
import random
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources"
A = ROOT / "assets/tcgshop"
DATA = ROOT / "data"
spec = importlib.util.spec_from_file_location("gt", Path(__file__).resolve().parent / "gen_textures.py")
gt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gt)
png, hexc, T = gt.png, gt.hexc, gt.T


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1), encoding="utf-8")


def shade(c, k):
    r, g, b, a = c
    return (max(0, min(255, int(r * k))), max(0, min(255, int(g * k))), max(0, min(255, int(b * k))), a)


def mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,)


def grid(fill, w=16, h=16):
    return [[fill(x, y) for x in range(w)] for y in range(h)]


def noise(base, seed, lo=0.9, hi=1.08):
    rnd = random.Random(seed)
    c = hexc(base)
    return grid(lambda x, y: shade(c, lo + rnd.random() * (hi - lo)))


# ======================================================================== textures

def tex_poop():
    """Brown coiled pile seen from above / the side: rings with a glossy highlight (cartoon style)."""
    rnd = random.Random(5)
    dark, mid, light, shine = hexc("4A2C14"), hexc("6E4320"), hexc("8C5A2E"), hexc("B98450")

    def f(x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        if d > 7.6:
            return T
        ring = int(d * 1.15) % 3
        c = [dark, mid, light][ring]
        if (x - 5) ** 2 + (y - 5) ** 2 < 3:
            c = shine
        return shade(c, 0.92 + rnd.random() * 0.12)
    return grid(f)


def tex_graffiti(seed):
    """Spray-paint tag on a transparent background: a few thick, wobbly strokes with a dark outline, drips and a
    sparkle of overspray. Each seed gives a different design and palette (all generated here)."""
    rnd = random.Random(1000 + seed)
    palettes = [("E8336B", "FFD23F"), ("36C5F0", "7CFC5A"), ("A05CFF", "FF8A3D"), ("2EE6A8", "FF4FD8")]
    main, accent = (hexc(c) for c in palettes[seed % len(palettes)])
    outline = hexc("1A1A22")
    paint = [[None] * 16 for _ in range(16)]
    # strokes: random walks with a 2-pixel brush
    for s in range(3 + seed % 2):
        x, y = rnd.uniform(2, 13), rnd.uniform(3, 12)
        ang = rnd.uniform(0, math.tau)
        col = accent if s == 0 else main
        for _ in range(rnd.randint(9, 15)):
            ang += rnd.uniform(-0.9, 0.9)
            x = min(13.5, max(1.5, x + math.cos(ang) * 1.1))
            y = min(12.5, max(2.5, y + math.sin(ang) * 0.9))
            for dx in (0, 1):
                for dy in (0, 1):
                    px, py = int(x) + dx - 1, int(y) + dy - 1
                    if 0 <= px < 16 and 0 <= py < 16:
                        paint[py][px] = col
    # drips under some painted pixels
    for _ in range(3):
        cols = [(px, py) for py in range(16) for px in range(16) if paint[py][px] is not None]
        if not cols:
            break
        px, py = rnd.choice(cols)
        for k in range(1, rnd.randint(2, 4)):
            if py + k < 16 and paint[py + k][px] is None:
                paint[py + k][px] = paint[py][px]
    out = [[T] * 16 for _ in range(16)]
    for py in range(16):
        for px in range(16):
            c = paint[py][px]
            if c is not None:
                out[py][px] = shade(c, 0.9 + rnd.random() * 0.15)
            elif any(0 <= px + dx < 16 and 0 <= py + dy < 16 and paint[py + dy][px + dx] is not None
                     for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[py][px] = outline
            elif rnd.random() < 0.025:
                out[py][px] = shade(main, 0.8)  # overspray dots
    return out


def tex_freshener(label):
    """White plastic dispenser with a coloured label band and a little window showing the can."""
    rnd = random.Random(11)
    white, edge = hexc("EEF1F4"), hexc("B9C1CA")
    lab = hexc(label)

    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return edge
        if 5 <= y <= 9:
            c = lab if not (6 <= x <= 9 and y == 7) else hexc("FFFFFF")
            if x in (1, 14):
                c = shade(lab, 0.8)
            return c
        if 11 <= y <= 13 and 5 <= x <= 10:
            return hexc("9BD6E8") if (x + y) % 4 else hexc("D8F2FA")
        return shade(white, 0.95 + rnd.random() * 0.06)
    return grid(f)


def tex_nozzle():
    return grid(lambda x, y: hexc("6C7680") if (x + y) % 3 else hexc("8A949E"))


# ---- wide door: one 48x32 storefront shutter design, sliced into six 16x16 textures

def door_design():
    W, H = 48, 32
    rnd = random.Random(33)
    frame, frame_hi = hexc("3B4148"), hexc("59616B")
    slat, slat_dark, slat_hi = hexc("A9B3BC"), hexc("6E7882"), hexc("D2D9DF")
    glass, glass_hi = hexc("7FC4DE"), hexc("CFEFF9")
    px = [[T] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if x < 2 or x >= W - 2 or y < 2:
                c = frame_hi if (x == 1 or y == 1 or x == W - 2) else frame
            elif y >= H - 1:
                c = frame
            else:
                band = (y - 2) % 3
                c = slat_hi if band == 0 else slat_dark if band == 2 else shade(slat, 0.95 + rnd.random() * 0.08)
                # vertical shading: darker near the bottom (dust), lighter at the top
                c = shade(c, 1.04 - (y / H) * 0.12)
            px[y][x] = c
    # window strip in the upper half (three panes split by the frame)
    for y in range(6, 12):
        for x in range(4, W - 4):
            if x in (15, 16, 31, 32):
                px[y][x] = frame
            else:
                px[y][x] = glass_hi if (x - y) % 7 in (0, 1) else glass
    for x in range(4, W - 4):
        px[5][x] = frame
        px[12][x] = frame
    # center handle bar and lock at the bottom of the lower half
    for x in range(20, 28):
        px[25][x] = hexc("2B2F35")
        px[26][x] = hexc("D9A93A")
    for y in range(27, 30):
        px[y][23] = hexc("2B2F35")
        px[y][24] = hexc("2B2F35")
    px[28][23] = hexc("E6C35C")
    # little part seams so the three columns read as one door
    for y in range(13, H - 1):
        if y % 6 == 0:
            px[y][16] = shade(px[y][16], 0.85)
            px[y][31] = shade(px[y][31], 0.85)
    return px


def slice_tex(px, cx, cy):
    """cx: 0..2 (left..right), cy: 0 = upper, 1 = lower."""
    return [row[cx * 16:(cx + 1) * 16] for row in px[cy * 16:(cy + 1) * 16]]


def tex_roll():
    """The rolled-up shutter in its housing: dark metal with a highlight line."""
    def f(x, y):
        if y in (0, 15):
            return hexc("2F343A")
        if y % 4 == 1:
            return hexc("8E98A2")
        return hexc("5A636C") if (x + y) % 5 else hexc("68717A")
    return grid(f)


def tex_door_item():
    """Inventory icon: the whole shutter squeezed into 16x16."""
    big = door_design()

    def f(x, y):
        sx, sy = int(x * 3), int(y * 2)
        if x < 1 or x > 14:
            return T
        return big[min(31, sy)][min(47, sx)]
    return grid(f)


# ---- decor

def tex_terracotta():
    return noise("B8643E", 41, 0.88, 1.06)


def tex_soil():
    return noise("4A3322", 42, 0.8, 1.2)


def tex_trunk():
    def f(x, y):
        c = hexc("7A5A34") if (y // 2) % 2 == 0 else hexc("5E4426")
        return shade(c, 1.1) if x % 4 == 0 else c
    return grid(f)


def tex_palm_leaves():
    """Fronds radiating from the center (transparent between them)."""
    rnd = random.Random(44)
    g1, g2, g3 = hexc("2E8B3E"), hexc("3FAA4C"), hexc("1F6B2E")

    def f(x, y):
        dx, dy = x - 7.5, y - 7.5
        ang = math.atan2(dy, dx)
        d = math.hypot(dx, dy)
        k = (ang / (math.pi / 3)) % 1.0
        if d < 1.5:
            return g3
        if abs(k - 0.5) < 0.22 + 0.08 * (1 - d / 8) and d < 7.8:
            return g2 if rnd.random() < 0.6 else g1
        return T
    return grid(f)


def tex_neon():
    """Dark backboard with a glowing card outline and a four-point star (generic, no text)."""
    back, pink, cyan = hexc("1B1D2A"), hexc("FF5FC8"), hexc("52E8FF")

    def f(x, y):
        c = back if (x + y) % 6 else hexc("232638")
        # card outline
        if 4 <= x <= 9 and 3 <= y <= 12 and (x in (4, 9) or y in (3, 12)):
            return pink
        # star
        if (x == 12 and 4 <= y <= 10) or (y == 7 and 9 <= x <= 15) or (abs(x - 12) == 1 and abs(y - 7) == 1):
            return cyan
        if x in (0, 15) or y in (0, 15):
            return hexc("3A3F55")
        return c
    return grid(f)


def tex_rug():
    """Navy rug, gold border, red diamond medallion."""
    navy, gold, red = hexc("24345E"), hexc("D9B44A"), hexc("B33A3A")

    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return gold
        if x in (1, 14) or y in (1, 14):
            return navy
        if x in (2, 13) or y in (2, 13):
            return shade(gold, 0.85)
        d = abs(x - 7.5) + abs(y - 7.5)
        if d < 3:
            return gold
        if d < 5:
            return red
        return navy if (x + y) % 2 else shade(navy, 1.12)
    return grid(f)


def tex_mat():
    """Coir doormat: brown weave with a darker border and three dots."""
    rnd = random.Random(61)

    def f(x, y):
        if x in (0, 15) or y in (0, 15) or x in (1, 14) or y in (1, 14):
            return hexc("5B3A1C")
        if y == 8 and x in (5, 8, 11):
            return hexc("3D2611")
        c = hexc("A9783E") if (x + y) % 2 else hexc("94672F")
        return shade(c, 0.92 + rnd.random() * 0.1)
    return grid(f)


def tex_clock_face():
    rim, face, tick, hand = hexc("6B4423"), hexc("F4EFE2"), hexc("2E2A26"), hexc("B3262E")

    def f(x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        if d > 7.6:
            return T
        if d > 6.4:
            return rim
        # ticks at 12 / 3 / 6 / 9
        if (x in (7, 8) and y in (2, 13)) or (y in (7, 8) and x in (2, 13)):
            return tick
        if x in (7, 8) and 4 <= y <= 8:
            return tick  # hour hand up
        if y == 8 and 8 <= x <= 11:
            return hand  # minute hand right
        return face
    return grid(f)


def tex_wood():
    return grid(lambda x, y: shade(hexc("7A4E2A"), 0.9 + ((x * 7 + y * 3) % 5) * 0.04))


def tex_gumball_glass():
    """Glass globe full of coloured gumballs (cutout: the rim area stays clear-ish)."""
    rnd = random.Random(71)
    cols = [hexc("E84B4B"), hexc("4BA3E8"), hexc("F2D23C"), hexc("5ACB6A"), hexc("F08CD0"), hexc("FFFFFF")]

    def f(x, y):
        if y < 3:
            return hexc("CDEFFB") if (x + y) % 5 == 0 else hexc("A9DDEE")
        c = cols[rnd.randrange(len(cols))]
        if (x + y) % 4 == 0:
            c = shade(c, 0.8)
        if x in (0, 15):
            c = mix(c, hexc("CDEFFB"), 0.5)
        return c
    return grid(f)


def tex_red_metal():
    return grid(lambda x, y: hexc("C0302F") if y % 5 else hexc("8E1F1F"))


def tex_steel():
    return grid(lambda x, y: hexc("B8C0C8") if (x + y) % 4 else hexc("DDE3E8"))


def tex_tank_side():
    """Aquarium glass side: water, gravel at the bottom, a plant and two fish."""
    rnd = random.Random(81)

    def f(x, y):
        if y >= 13:
            return [hexc("C9B48A"), hexc("A89167"), hexc("E0CFA6")][rnd.randrange(3)]
        c = hexc("3E9FCB") if (x + y) % 6 else hexc("62B9DE")
        if y < 2:
            c = hexc("8ED3EE")
        if x in (2, 3) and y >= 6 and (y + x) % 2 == 0:
            c = hexc("2F8F3F")
        if 6 <= x <= 9 and y == 6:
            c = hexc("F28C28")
        if x == 10 and y == 6:
            c = hexc("F2B628")
        if 9 <= x <= 11 and y == 10:
            c = hexc("F2E64A")
        return c
    return grid(f)


def tex_black_frame():
    return grid(lambda x, y: hexc("23262B") if (x + y) % 5 else hexc("33373E"))


def tex_shade():
    """Cream fabric lamp shade, warmer at the bottom."""
    def f(x, y):
        c = mix(hexc("F6EBD0"), hexc("F2C77A"), y / 15)
        return shade(c, 0.96) if x % 3 == 0 else c
    return grid(f)


def tex_banner():
    """Navy cloth, gold card emblem with a red star, fringe at the bottom."""
    navy, gold, red = hexc("1F2E66"), hexc("E3BE52"), hexc("C93838")

    def f(x, y):
        if y >= 14:
            return gold if x % 2 == 0 else T
        if x in (0, 15):
            return shade(navy, 0.8)
        if 4 <= x <= 11 and 2 <= y <= 11 and (x in (4, 11) or y in (2, 11)):
            return gold
        sx, sy = abs(x - 7.5), abs(y - 6.5)
        if sx + sy <= 2 or (sx < 1 and sy <= 3.5) or (sy < 1 and sx <= 3.5):  # four-point star
            return red
        return navy if (x + y) % 5 else shade(navy, 1.15)
    return grid(f)


def tex_rod():
    return grid(lambda x, y: hexc("C9A23A") if y % 4 else hexc("8C6E1F"))


def tex_standee():
    """Cardboard cutout of a cheerful (generic, human) shopkeeper: cap, smile, apron with a star. Transparent around."""
    skin, cap, apron, shirt, line = hexc("F1C79B"), hexc("2F6FB0"), hexc("2E9C5A"), hexc("F4F0E8"), hexc("3A2A1E")
    px = [[T] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            dx = x - 7.5
            if y <= 1 and abs(dx) <= 3.5:
                px[y][x] = cap
            elif y == 2 and abs(dx) <= 5:
                px[y][x] = shade(cap, 0.8)
            elif 3 <= y <= 7 and abs(dx) <= 3.5:
                px[y][x] = skin
            elif 8 <= y <= 15 and abs(dx) <= 5.5 - (0 if y > 9 else 1):
                px[y][x] = apron if abs(dx) <= 3.5 else shirt
    for (x, y) in [(6, 4), (9, 4)]:
        px[y][x] = line
    for x in range(6, 10):
        px[6][x] = line
    px[5][5] = px[5][10] = hexc("E89A8A")
    for (x, y) in [(7, 11), (8, 11), (7, 12), (8, 12), (6, 11), (9, 11), (7, 10), (8, 13)]:
        px[y][x] = hexc("F2D23C")
    return px


def tex_cardboard():
    return noise("B08A5A", 91, 0.88, 1.08)


TEX = {
    "poop": tex_poop,
    "graffiti_0": lambda: tex_graffiti(0),
    "graffiti_1": lambda: tex_graffiti(1),
    "graffiti_2": lambda: tex_graffiti(2),
    "graffiti_3": lambda: tex_graffiti(3),
    "wall_freshener": lambda: tex_freshener("2FB7A8"),
    "wall_freshener_empty": lambda: tex_freshener("8A929B"),
    "wall_freshener_nozzle": tex_nozzle,
    "wide_door_roll": tex_roll,
    "decor_terracotta": tex_terracotta,
    "decor_soil": tex_soil,
    "decor_trunk": tex_trunk,
    "decor_palm_leaves": tex_palm_leaves,
    "decor_neon": tex_neon,
    "decor_rug": tex_rug,
    "decor_mat": tex_mat,
    "decor_clock": tex_clock_face,
    "decor_wood": tex_wood,
    "decor_gumballs": tex_gumball_glass,
    "decor_red_metal": tex_red_metal,
    "decor_steel": tex_steel,
    "decor_tank": tex_tank_side,
    "decor_black_frame": tex_black_frame,
    "decor_shade": tex_shade,
    "decor_banner": tex_banner,
    "decor_rod": tex_rod,
    "decor_standee": tex_standee,
    "decor_cardboard": tex_cardboard,
}

DOOR_PARTS = [("left", 0), ("center", 1), ("right", 2)]


# ======================================================================== models

FACES = ["north", "south", "east", "west", "up", "down"]


def el(frm, to, tex, faces=None, rot=None, uv=None):
    """Element with automatic UVs. tex: '#var' for every face, or dict face -> '#var' ('*' = default)."""
    if isinstance(tex, str):
        tex = {"*": tex}
    fs = {}
    for n in faces or FACES:
        t = tex.get(n, tex.get("*"))
        if t:
            fs[n] = {"texture": t}
            if uv and n in uv:
                fs[n]["uv"] = uv[n]
    e = {"from": frm, "to": to, "faces": fs}
    if rot:
        e["rotation"] = rot
    return e


def model(textures, elements, particle=None, ao=True):
    t = dict(textures)
    t["particle"] = particle or next(iter(textures.values()))
    m = {"parent": "minecraft:block/block", "textures": t, "elements": elements}
    if not ao:
        m["ambientocclusion"] = False
    return m


def tx(name):
    return "tcgshop:block/" + name


def models():
    M = {}
    full = {"north": [0, 0, 16, 16], "south": [0, 0, 16, 16], "up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}
    # ---- droppings
    M["poop"] = model({"p": tx("poop")}, [
        el([3, 0, 3], [13, 1, 13], "#p", uv={"up": [0, 0, 16, 16]}),
        el([4.5, 1, 4.5], [11.5, 2, 11.5], "#p"),
        el([6, 2, 6], [10, 2.75, 10], "#p"),
    ], ao=False)
    # ---- graffiti: a paint layer just in front of the wall behind it (south side for a north-facing state)
    for i in range(4):
        M[f"graffiti_{i}"] = model({"t": tx(f"graffiti_{i}")}, [
            el([0, 0, 15.9], [16, 16, 15.9], "#t", faces=["north"], uv={"north": [0, 0, 16, 16]}),
        ], ao=False)
    M["poop_big"] = model({"p": tx("poop")}, [
        el([1, 0, 1], [15, 1, 15], "#p", uv={"up": [0, 0, 16, 16]}),
        el([2.5, 1, 2.5], [13.5, 2, 13.5], "#p"),
        el([4, 2, 4], [12, 3, 12], "#p"),
        el([6, 3, 6], [10, 4, 10], "#p"),
        el([7.25, 4, 7.25], [8.75, 4.75, 8.75], "#p"),
    ], ao=False)
    # ---- wall freshener (back on the south wall)
    for suffix in ("", "_empty"):
        M["wall_freshener" + suffix] = model({"body": tx("wall_freshener" + suffix), "noz": tx("wall_freshener_nozzle")}, [
            el([5, 4, 12.5], [11, 13, 16], {"*": "#body"}, uv={"north": [0, 0, 16, 16]}),
            el([7, 13, 13], [9, 14, 15], "#noz"),
            el([7.25, 11.5, 12], [8.75, 12.5, 12.5], "#noz"),
            el([4.5, 3.5, 15.5], [11.5, 4, 16], "#noz"),
        ])
    # ---- wide door: closed panels per part / half; open = floor rail and roll housing
    for name, cx in DOOR_PARTS:
        for half, cy in (("upper", 0), ("lower", 1)):
            t = tx(f"wide_door_{half}_{name}")
            M[f"wide_door_{half}_{name}"] = model({"face": t, "edge": tx("wide_door_roll")}, [
                el([0, 0, 13], [16, 16, 16], {"north": "#face", "south": "#face", "*": "#edge"},
                   uv={"north": [16, 0, 0, 16], "south": [0, 0, 16, 16]}),
            ], particle=t)
    M["wide_door_open_lower"] = model({"edge": tx("wide_door_roll")}, [el([0, 0, 13], [16, 1, 16], "#edge")])
    M["wide_door_open_upper"] = model({"roll": tx("wide_door_roll")}, [
        el([0, 12, 11], [16, 16, 16], "#roll"),
    ])
    # ---- decor
    M["potted_palm"] = model({"pot": tx("decor_terracotta"), "soil": tx("decor_soil"), "trunk": tx("decor_trunk"),
                              "leaves": tx("decor_palm_leaves")}, [
        el([4, 0, 4], [12, 6, 12], {"*": "#pot", "up": "#soil"}),
        el([3.5, 5, 3.5], [12.5, 7, 12.5], {"*": "#pot", "up": "#soil"}),
        el([7, 7, 7], [9, 12, 9], "#trunk"),
        el([1, 12, 1], [15, 12.01, 15], {"up": "#leaves", "down": "#leaves"}, faces=["up", "down"]),
        el([1, 9, 7.99], [15, 16, 8.01], {"north": "#leaves", "south": "#leaves"}, faces=["north", "south"],
           rot={"origin": [8, 12, 8], "axis": "y", "angle": 45}),
        el([1, 9, 7.99], [15, 16, 8.01], {"north": "#leaves", "south": "#leaves"}, faces=["north", "south"],
           rot={"origin": [8, 12, 8], "axis": "y", "angle": -45}),
    ], particle=tx("decor_terracotta"), ao=False)
    M["neon_sign"] = model({"n": tx("decor_neon"), "f": tx("decor_black_frame")}, [
        el([1, 3, 15], [15, 13, 16], {"*": "#f", "north": "#n"}, uv={"north": [0, 0, 16, 16]}),
        el([1, 3, 14.6], [15, 13, 14.99], {"north": "#n"}, faces=["north"], uv={"north": [0, 0, 16, 16]}),
    ], ao=False)
    M["shop_rug"] = model({"r": tx("decor_rug")}, [el([0, 0, 0], [16, 1, 16], "#r", uv=full)])
    M["welcome_mat"] = model({"m": tx("decor_mat")}, [el([1, 0, 3], [15, 1, 13], "#m", uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]})])
    M["wall_clock"] = model({"c": tx("decor_clock"), "w": tx("decor_wood")}, [
        el([3, 3, 15], [13, 13, 16], {"north": "#c", "south": "#w", "*": "#w"}, uv={"north": [0, 0, 16, 16]}),
    ], ao=False)
    M["gumball_machine"] = model({"g": tx("decor_gumballs"), "r": tx("decor_red_metal"), "s": tx("decor_steel")}, [
        el([5, 0, 5], [11, 1, 11], "#r"),
        el([6, 1, 6], [10, 6, 10], "#r"),
        el([7, 3, 5.5], [9, 5, 6], "#s"),
        el([5, 6, 5], [11, 7, 11], "#s"),
        el([4, 7, 4], [12, 14, 12], "#g"),
        el([6, 14, 6], [10, 15, 10], "#r"),
    ], particle=tx("decor_red_metal"))
    M["fish_tank"] = model({"t": tx("decor_tank"), "f": tx("decor_black_frame")}, [
        el([1, 0, 3], [15, 1.5, 13], "#f"),
        el([1.5, 1.5, 3.5], [14.5, 12.5, 12.5], {"*": "#t", "up": "#t", "down": "#f"}),
        el([1, 12.5, 3], [15, 14, 13], "#f"),
    ], particle=tx("decor_black_frame"))
    M["floor_lamp"] = model({"s": tx("decor_steel"), "sh": tx("decor_shade")}, [
        el([5, 0, 5], [11, 1.5, 11], "#s"),
        el([7.5, 1.5, 7.5], [8.5, 12, 8.5], "#s"),
        el([4, 12, 4], [12, 16, 12], {"*": "#sh", "up": "#s", "down": "#sh"}),
    ], ao=False)
    M["card_banner"] = model({"b": tx("decor_banner"), "r": tx("decor_rod")}, [
        el([1, 15, 14.5], [15, 16, 16], "#r"),
        el([2, 0, 15.2], [14, 15, 15.6], {"north": "#b", "south": "#b"}, faces=["north", "south"],
           uv={"north": [0, 0, 16, 16], "south": [16, 0, 0, 16]}),
    ], ao=False)
    M["shopkeeper_standee"] = model({"s": tx("decor_standee"), "c": tx("decor_cardboard")}, [
        el([3, 0, 7.5], [13, 16, 8.5], {"north": "#s", "south": "#c"}, faces=["north", "south"],
           uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}),
        el([6, 0, 8.5], [10, 1, 12], "#c"),
        el([7.5, 1, 8.5], [8.5, 10, 11], "#c"),
    ], particle=tx("decor_cardboard"), ao=False)
    return M


DECOR = ["potted_palm", "neon_sign", "shop_rug", "wall_clock", "gumball_machine", "fish_tank", "floor_lamp",
         "card_banner", "shopkeeper_standee", "welcome_mat"]
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def blockstates():
    B = {}
    B["poop"] = {"variants": {
        "big=false": [{"model": "tcgshop:block/poop", "y": r} for r in (0, 90, 180, 270)],
        "big=true": [{"model": "tcgshop:block/poop_big", "y": r} for r in (0, 90, 180, 270)],
    }}
    B["graffiti"] = {"variants": {
        f"facing={f},variant={i}": {"model": f"tcgshop:block/graffiti_{i}", "y": r} for f, r in ROT.items() for i in range(4)}}
    B["wall_freshener"] = {"variants": {
        f"empty={str(e).lower()},facing={f}": {"model": "tcgshop:block/wall_freshener" + ("_empty" if e else ""), "y": r}
        for e in (False, True) for f, r in ROT.items()}}
    v = {}
    for f, r in ROT.items():
        for name, _ in DOOR_PARTS:
            for half in ("lower", "upper"):
                v[f"facing={f},half={half},open=false,part={name}"] = {"model": f"tcgshop:block/wide_door_{half}_{name}", "y": r}
                v[f"facing={f},half={half},open=true,part={name}"] = {"model": f"tcgshop:block/wide_door_open_{half}", "y": r}
    B["wide_door"] = {"variants": v}
    for d in DECOR:
        B[d] = {"variants": {f"facing={f}": {"model": f"tcgshop:block/{d}", "y": r} for f, r in ROT.items()}}
    return B


def loot(name, extra=None, conditions=None):
    entry = {"type": "minecraft:item", "name": "tcgshop:" + name}
    if extra:
        entry["functions"] = extra
    if conditions:
        entry["conditions"] = conditions
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry],
                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def main():
    tex = A / "textures/block"
    tex.mkdir(parents=True, exist_ok=True)
    for name, fn in TEX.items():
        png(tex / f"{name}.png", fn())
    door = door_design()
    for name, cx in DOOR_PARTS:
        png(tex / f"wide_door_upper_{name}.png", slice_tex(door, cx, 0))
        png(tex / f"wide_door_lower_{name}.png", slice_tex(door, cx, 1))
    (A / "textures/item").mkdir(parents=True, exist_ok=True)
    png(A / "textures/item/wide_door.png", tex_door_item())
    for name, m in models().items():
        write(A / f"models/block/{name}.json", m)
    for name, b in blockstates().items():
        write(A / f"blockstates/{name}.json", b)
    # item models: the block model (the door gets a flat icon like vanilla doors)
    for name in DECOR + ["wall_freshener"]:
        write(A / f"models/item/{name}.json", {"parent": f"tcgshop:block/{name}"})
    write(A / "models/item/wide_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "tcgshop:item/wide_door"}})
    # loot tables (droppings and graffiti have none)
    lt = DATA / "tcgshop/loot_table/blocks"
    for d in DECOR:
        write(lt / f"{d}.json", loot(d))
    write(lt / "wall_freshener.json", loot("wall_freshener", extra=[
        {"function": "minecraft:copy_components", "source": "block_entity", "include": ["minecraft:damage"]}]))
    write(lt / "wide_door.json", loot("wide_door", conditions=[
        {"condition": "minecraft:block_state_property", "block": "tcgshop:wide_door", "properties": {"part": "center", "half": "lower"}}]))
    # tags: the wide door is a wooden door (customers path through it and open it; the shop counts it as a door)
    write(DATA / "minecraft/tags/block/wooden_doors.json", {"replace": False, "values": ["tcgshop:wide_door"]})
    write(DATA / "minecraft/tags/block/mineable/axe.json", {"replace": False, "values": [
        "tcgshop:wall_clock", "tcgshop:shopkeeper_standee", "tcgshop:potted_palm"]})
    pick = DATA / "minecraft/tags/block/mineable/pickaxe.json"
    p = json.loads(pick.read_text(encoding="utf-8"))
    for v in ["tcgshop:wide_door", "tcgshop:wall_freshener", "tcgshop:gumball_machine", "tcgshop:fish_tank", "tcgshop:floor_lamp",
              "tcgshop:neon_sign"]:
        if v not in p["values"]:
            p["values"].append(v)
    write(pick, p)
    print(len(TEX) + 7, "textures,", len(models()), "models,", len(blockstates()), "blockstates")


if __name__ == "__main__":
    main()
