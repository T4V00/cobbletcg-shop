#!/usr/bin/env python3
"""Detailed block models for the shop furniture (front = north, -Z), their own textures,
blockstates, item models and loot tables. Coordinates match DisplayKind / ShopRenderers:
the renderer draws the products on top; only the furniture goes here."""
import json
import random
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "common/src/main/resources"
A = ROOT / "assets/tcgshop"
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


def grid(fill):
    return [[fill(x, y) for x in range(16)] for y in range(16)]


# ---------------------------------------------------------------- own textures

def tex_pegboard():
    base = hexc("C9CDD3")
    rnd = random.Random(1)
    return grid(lambda x, y: hexc("6E747C") if (x % 4 == 1 and y % 4 == 1) else shade(base, 0.94 + rnd.random() * 0.1))


def tex_price_strip():
    def f(x, y):
        if y in (0, 15):
            return hexc("8A6A10")
        if 5 <= y <= 10 and x % 8 in (2, 3, 4):
            return hexc("1A1A1A") if y in (6, 9) or x % 8 == 3 else hexc("FFE680")
        return hexc("F2C230") if y < 8 else hexc("E5B21E")
    return grid(f)


def tex_header():
    def f(x, y):
        if y < 3 or y > 12:
            return hexc("8E1F1F")
        return hexc("D63A3A") if (x + y) % 6 else hexc("E85454")
    return grid(f)


def tex_felt():
    rnd = random.Random(7)
    g = hexc("1F6E3A")

    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return hexc("F2F2F2")
        if y in (7, 8):
            return hexc("D9D9D9")
        if (x in (2, 13) and 2 <= y <= 5) or (y in (2, 5) and 2 <= x <= 13):
            return hexc("BFE8C8")
        if (x in (2, 13) and 10 <= y <= 13) or (y in (10, 13) and 2 <= x <= 13):
            return hexc("BFE8C8")
        return shade(g, 0.92 + rnd.random() * 0.12)
    return grid(f)


def tex_keys():
    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return hexc("2A2D33")
        if x % 3 == 0 or y % 3 == 0:
            return hexc("3A3E46")
        if y >= 12 and x >= 10:
            return hexc("3CB043")
        if y >= 12 and x <= 4:
            return hexc("D63A3A")
        return hexc("E8E8E8")
    return grid(f)


def tex_screen():
    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return hexc("1C1C1C")
        return hexc("0E2A1A") if (y % 2) else hexc("123522")
    return grid(f)


def tex_register_body():
    rnd = random.Random(3)
    return grid(lambda x, y: hexc("2B2F38") if y in (0, 15) else shade(hexc("3E4450"), 0.95 + rnd.random() * 0.08))


def tex_detector(on):
    light = hexc("FF3B3B") if on else hexc("6E2A2A")
    hi = hexc("FFB0B0") if on else hexc("8A3A3A")

    def f(x, y):
        if x in (0, 15):
            return hexc("8C9198")
        if x in (1, 14):
            return hexc("B5BAC1")
        if y % 5 == 2:
            return hi if 3 <= x <= 12 else hexc("B5BAC1")
        if y % 5 == 3:
            return light if 3 <= x <= 12 else hexc("B5BAC1")
        return hexc("DDE3EA") if (x + y) % 7 else hexc("F4F8FC")
    return grid(f)


def tex_detector_light(on):
    c = hexc("FF2020") if on else hexc("5A1C1C")
    return grid(lambda x, y: hexc("FFC0C0") if on and (x + y) % 5 == 0 else shade(c, 0.9 + ((x * 7 + y * 3) % 5) * 0.04))


def tex_mystery():
    P, D, G, W = hexc("7B3FB0"), hexc("4A2370"), hexc("F2C230"), hexc("FFFFFF")
    Q = {(6, 4), (7, 3), (8, 3), (9, 4), (9, 5), (8, 6), (7, 7), (7, 8), (7, 10)}

    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return D
        if x in (7, 8) and not (3 <= y <= 10):
            return G
        if (x, y) in Q or (x - 1, y) in Q and (x, y) not in Q and False:
            return W
        return P if (x + y) % 5 else shade(P, 1.12)
    return grid(f)


def tex_mystery_item():
    P, D, G, W = hexc("7B3FB0"), hexc("3A1A58"), hexc("F2C230"), hexc("FFFFFF")
    px = [[T] * 16 for _ in range(16)]
    for y in range(4, 15):
        for x in range(2, 14):
            px[y][x] = D if x in (2, 13) or y in (4, 14) else P
    for y in range(2, 5):
        for x in range(1, 15):
            px[y][x] = D if y == 2 or x in (1, 14) else shade(P, 1.15)
    for y in range(2, 15):
        px[y][7] = px[y][8] = G
    for (x, y) in [(5, 1), (6, 0), (6, 1), (9, 0), (9, 1), (10, 1)]:
        px[y][x] = G
    for (x, y) in [(4, 7), (5, 6), (6, 7), (6, 8), (5, 9), (5, 11), (10, 7), (11, 6), (12, 7), (12, 8), (11, 9), (11, 11)]:
        px[y][x] = W
    return px


def tex_cardboard():
    rnd = random.Random(11)
    base = hexc("C49868")

    def f(x, y):
        k = 0.93 + rnd.random() * 0.1
        if (y * 5 + x * 2) % 13 == 0:
            k -= 0.06
        return shade(base, k)
    return grid(f)


def tex_cardboard_edge():
    def f(x, y):
        if y in (0, 1, 14, 15):
            return hexc("B98C5E")
        ph = (x + (2 if 4 <= y <= 11 else 0)) % 4
        return hexc("8E6640") if ph == 0 else hexc("A97C50") if ph in (1, 3) else hexc("D2A876")
    return grid(f)


def tex_box_print():
    rnd = random.Random(5)

    def f(x, y):
        v = 235 + rnd.randint(-6, 6)
        if (x + y) % 8 < 2:
            v -= 22
        if x % 4 == 0 and y % 4 == 0:
            v -= 30
        return (v, v, v, 255)
    return grid(f)


def tex_shrink_wrap():
    def f(x, y):
        d = (x + y) % 16
        if d in (3, 4):
            return (255, 255, 255, 110)
        if d in (2, 5, 11):
            return (255, 255, 255, 45)
        return (255, 255, 255, 14)
    return grid(f)


TEX = {
    "cardboard": tex_cardboard, "cardboard_edge": tex_cardboard_edge, "box_print": tex_box_print, "shrink_wrap": tex_shrink_wrap,
    "pegboard": tex_pegboard, "price_strip": tex_price_strip, "header": tex_header, "felt": tex_felt,
    "register_keys": tex_keys, "register_screen": tex_screen, "register_body": tex_register_body,
    "detector_panel": lambda: tex_detector(False), "detector_panel_on": lambda: tex_detector(True),
    "detector_light": lambda: tex_detector_light(False), "detector_light_on": lambda: tex_detector_light(True),
    "mystery_box": tex_mystery,
}


# ---------------------------------------------------------------- model utilities

def box(frm, to, tex, faces=None, rot=None, skip=()):
    """tex: '#x' for all faces or dict face->'#x'."""
    names = ["north", "south", "east", "west", "up", "down"]
    if isinstance(tex, str):
        tex = {n: tex for n in names}
    fs = {}
    for n in names:
        if n in skip or (faces and n not in faces):
            continue
        t = tex.get(n, tex.get("*"))
        if t:
            fs[n] = {"texture": t}
    e = {"from": frm, "to": to, "faces": fs}
    if rot:
        e["rotation"] = rot
    return e


V = "minecraft:block/"


def model(textures, elements, parent="minecraft:block/block", **extra):
    d = {"parent": parent, "textures": textures, "elements": elements}
    d.update(extra)
    return d


# ---------------------------------------------------------------- multi-block furniture
# "Structure" coordinates in pixels: x in [0, 16W], y in [0, 16H], z in [0, 16D]; z = 0 is the front (north).
# slice_model() cuts each element into 16-pixel cells and generates one model per part (part = i + W*(j + H*k)).

DEFAULT_UV = {
    "down": lambda f, t: [f[0], 16 - t[2], t[0], 16 - f[2]],
    "up": lambda f, t: [f[0], f[2], t[0], t[2]],
    "north": lambda f, t: [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
    "south": lambda f, t: [f[0], 16 - t[1], t[0], 16 - f[1]],
    "west": lambda f, t: [f[2], 16 - t[1], t[2], 16 - f[1]],
    "east": lambda f, t: [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
}


def fit_uv(uv):
    """Brings UVs into the [0,16] range (shifting by multiples of 16 and clamping) so they don't bleed into the atlas."""
    uv = list(uv)
    for i0, i1 in ((0, 2), (1, 3)):
        lo = min(uv[i0], uv[i1])
        shift = -16 * (lo // 16) if (lo < 0 or lo >= 16) else 0
        uv[i0] += shift
        uv[i1] += shift
        uv[i0] = max(0, min(16, uv[i0]))
        uv[i1] = max(0, min(16, uv[i1]))
    return [round(x, 4) for x in uv]


def with_uv(e):
    for n, face in e["faces"].items():
        if "uv" not in face:
            face["uv"] = fit_uv(DEFAULT_UV[n](e["from"], e["to"]))
    return e


def slice_model(textures, elements, W, H, D, **extra):
    parts = {}
    n = (W, H, D)
    for k in range(D):
        for j in range(H):
            for i in range(W):
                p = i + W * (j + H * k)
                idx = (i, j, k)
                lo = [16 * idx[a] if idx[a] > 0 else -1e9 for a in range(3)]
                hi = [16 * (idx[a] + 1) if idx[a] < n[a] - 1 else 1e9 for a in range(3)]
                off = [16 * i, 16 * j, 16 * k]
                els = []
                for e in elements:
                    f, t = e["from"], e["to"]
                    ok = True
                    nf, nt = [0, 0, 0], [0, 0, 0]
                    for a in range(3):
                        if f[a] == t[a]:  # flat plane: only in the cell that contains it
                            if not (lo[a] <= f[a] < hi[a]):
                                ok = False
                            nf[a] = nt[a] = f[a]
                        else:
                            nf[a], nt[a] = max(f[a], lo[a]), min(t[a], hi[a])
                            if nt[a] <= nf[a]:
                                ok = False
                    if not ok:
                        continue
                    faces = {fn: dict(fv) for fn, fv in e["faces"].items()}
                    for a, (neg, pos) in enumerate((("west", "east"), ("down", "up"), ("north", "south"))):
                        if nf[a] > f[a]:
                            faces.pop(neg, None)
                        if nt[a] < t[a]:
                            faces.pop(pos, None)
                    if not faces:
                        continue
                    for fv in faces.values():
                        fv.pop("uv", None)
                    ne = {"from": [round(nf[a] - off[a], 4) for a in range(3)],
                          "to": [round(nt[a] - off[a], 4) for a in range(3)], "faces": faces}
                    els.append(with_uv(ne))
                d = model(textures, els, **extra)
                d["ambientocclusion"] = False
                parts[p] = d
    return parts


def item_of(textures, elements, W, H, D):
    """Item model: the whole structure scaled to fit in one block."""
    sc = 16.0 / (16 * max(W, H, D))
    ox, oz = (16 - 16 * W * sc) / 2, (16 - 16 * D * sc) / 2
    els = []
    for e in elements:
        els.append({"from": [round(e["from"][0] * sc + ox, 4), round(e["from"][1] * sc, 4), round(e["from"][2] * sc + oz, 4)],
                    "to": [round(e["to"][0] * sc + ox, 4), round(e["to"][1] * sc, 4), round(e["to"][2] * sc + oz, 4)],
                    "faces": {fn: {"texture": fv["texture"], "uv": fit_uv(DEFAULT_UV[fn](e["from"], e["to"]))}
                              for fn, fv in e["faces"].items()}})
    return model(textures, els)


def card_stand():
    t = {"base": V + "stripped_dark_oak_log", "wood": V + "dark_oak_planks", "gold": V + "gold_block", "velvet": V + "red_wool",
         "head": "tcgshop:block/header", "particle": V + "dark_oak_planks"}
    e = [box([0, 0, 2], [32, 3, 14], "#base"),
         box([0.5, 3, 2.5], [31.5, 3.5, 13.5], "#gold"),
         box([1, 3.5, 3], [31, 8, 13], "#wood"),
         box([1, 8, 5], [31, 9.6, 7.5], "#wood"),                    # ledge the cards rest on
         box([1, 9.6, 5], [31, 10.2, 5.6], "#gold"),                 # gold lip
         box([1.5, 9.6, 10.6], [30.5, 29, 11.6], "#velvet"),         # velvet backing
         box([0.5, 8, 10], [1.5, 31, 12.2], "#base"), box([30.5, 8, 10], [31.5, 31, 12.2], "#base"),
         box([15.5, 9.6, 10.2], [16.5, 29, 11.2], "#gold"),          # center divider
         box([0.5, 29, 10], [31.5, 31.5, 12.2], {"*": "#base", "north": "#head"}),
         box([10, 0.6, 1.6], [22, 2.4, 2], "#gold")]                 # plaque
    return 2, 2, 1, t, e


def display_case():
    t = {"base": V + "polished_blackstone", "wood": V + "dark_oak_planks", "panel": V + "stripped_dark_oak_log",
         "velvet": V + "red_wool", "gold": V + "gold_block", "glass": V + "glass", "particle": V + "glass"}
    e = [box([0.5, 0, 0.5], [47.5, 1, 15.5], "#base"),
         box([0, 1, 0], [48, 6.6, 16], "#wood")]
    for x0, x1 in ((1.5, 15.5), (17, 31), (32.5, 46.5)):
        e.append(box([x0, 1.8, -0.5], [x1, 6, 0], "#panel", skip=("south",)))
        e.append(box([(x0 + x1) / 2 - 1, 3.4, -0.8], [(x0 + x1) / 2 + 1, 4.2, -0.5], "#gold", skip=("south",)))
    e += [box([0, 6.6, 0], [48, 7.2, 16], "#gold"),
          box([0.6, 7.2, 0.6], [47.4, 7.4, 15.4], "#velvet"),
          box([0, 7.2, 0], [1, 16, 1], "#gold"), box([47, 7.2, 0], [48, 16, 1], "#gold"),
          box([0, 7.2, 15], [1, 16, 16], "#gold"), box([47, 7.2, 15], [48, 16, 16], "#gold"),
          box([1, 7.4, 0.3], [47, 15.6, 0.7], "#glass", faces=("north", "south")),
          box([1, 7.4, 15.3], [47, 15.6, 15.7], "#glass", faces=("north", "south")),
          box([0.3, 7.4, 1], [0.7, 15.6, 15], "#glass", faces=("east", "west")),
          box([47.3, 7.4, 1], [47.7, 15.6, 15], "#glass", faces=("east", "west")),
          box([1, 15.6, 0], [47, 16, 1], "#wood"), box([1, 15.6, 15], [47, 16, 16], "#wood"),
          box([0, 15.6, 1], [1, 16, 15], "#wood"), box([47, 15.6, 1], [48, 16, 15], "#wood"),
          box([1, 15.7, 1], [47, 15.9, 15], "#glass", faces=("up", "down"))]
    return 3, 1, 1, t, e


def pack_shelf():
    t = {"wood": V + "spruce_planks", "trim": V + "stripped_spruce_log", "back": "tcgshop:block/pegboard",
         "strip": "tcgshop:block/price_strip", "head": "tcgshop:block/header", "particle": V + "spruce_planks"}
    e = [box([0, 0, 14], [48, 48, 16], {"*": "#wood", "north": "#back"}),
         box([0, 0, 1], [1, 48, 14], "#trim"), box([47, 0, 1], [48, 48, 14], "#trim"),
         box([1, 0, 1], [47, 0.8, 14], "#trim")]
    for top in (1.8, 17.0, 32.2):
        e.append(box([1, top - 1, 2], [47, top, 14], "#wood"))
        e.append(box([1, top - 1.4, 1.4], [47, top - 0.2, 2], {"*": "#trim", "north": "#strip"}))
    e.append(box([0, 45.5, 0.5], [48, 48, 14], {"*": "#trim", "north": "#head"}))
    return 3, 3, 1, t, e


def gondola():
    t = {"base": V + "light_gray_concrete", "kick": V + "gray_concrete", "peg": "tcgshop:block/pegboard",
         "metal": V + "iron_block", "shelf": V + "white_concrete", "strip": "tcgshop:block/price_strip",
         "head": "tcgshop:block/header", "particle": V + "iron_block"}
    e = [box([0, 0, 0.5], [48, 1, 15.5], {"*": "#base", "north": "#kick", "south": "#kick"}),
         box([0, 1, 7], [48, 29.5, 9], {"*": "#metal", "north": "#peg", "south": "#peg"}),
         box([0, 0, 6.8], [0.8, 32, 9.2], "#metal"), box([47.2, 0, 6.8], [48, 32, 9.2], "#metal"),
         box([0.8, 1, 0.5], [47.2, 1.7, 7], "#shelf"),
         box([0.8, 15.8, 0.8], [47.2, 16.4, 7], "#shelf"),
         box([0.8, 15.4, 0.3], [47.2, 16.6, 0.8], {"*": "#metal", "north": "#strip"}),
         box([0.8, 1, 9], [47.2, 1.7, 15.5], "#shelf"),
         box([0.8, 15.8, 9], [47.2, 16.4, 15.2], "#shelf"),
         box([0.8, 15.4, 15.2], [47.2, 16.6, 15.7], {"*": "#metal", "south": "#strip"}),
         box([0.5, 29.5, 6.4], [47.5, 32, 9.6], {"*": "#metal", "north": "#head", "south": "#head"})]
    return 3, 2, 1, t, e


def box_pallet():
    t = {"wood": V + "spruce_planks", "log": V + "stripped_spruce_log", "particle": V + "spruce_planks"}
    e = [box([0.5, 0, 1], [3, 1.3, 15], "#log"), box([14.75, 0, 1], [17.25, 1.3, 15], "#log"), box([29, 0, 1], [31.5, 1.3, 15], "#log")]
    for z in (0.5, 3.6, 6.7, 9.8, 12.9):
        e.append(box([0, 1.3, z], [32, 1.6, z + 2.6], "#wood"))
    return 2, 2, 1, t, e


def pedestal():
    t = {"q": V + "quartz_block_side", "qt": V + "quartz_block_top", "pillar": V + "quartz_pillar",
         "gold": V + "gold_block", "velvet": V + "red_wool", "glass": V + "glass", "particle": V + "quartz_block_side"}
    e = [box([2, 0, 2], [14, 2, 14], {"*": "#q", "up": "#qt", "down": "#qt"}),
         box([2.5, 2, 2.5], [13.5, 2.6, 13.5], "#gold"),
         box([4, 2.6, 4], [12, 13, 12], {"*": "#pillar", "up": "#qt", "down": "#qt"}),
         box([3, 13, 3], [13, 13.6, 13], "#gold"),
         box([1, 13.6, 1], [15, 16, 15], {"*": "#q", "up": "#qt", "down": "#qt"}),
         box([3, 16, 3], [13, 16.4, 13], "#velvet"),
         box([0.9, 16, 0.5], [15.1, 44, 0.9], "#glass", faces=("north", "south")),
         box([0.9, 16, 15.1], [15.1, 44, 15.5], "#glass", faces=("north", "south")),
         box([0.5, 16, 0.9], [0.9, 44, 15.1], "#glass", faces=("east", "west")),
         box([15.1, 16, 0.9], [15.5, 44, 15.1], "#glass", faces=("east", "west"))]
    for x, z in ((0.2, 0.2), (14.8, 0.2), (0.2, 14.8), (14.8, 14.8)):
        e.append(box([x, 16, z], [x + 1, 44, z + 1], "#gold"))
    e += [box([0.2, 44, 0.2], [15.8, 46, 15.8], {"*": "#q", "up": "#qt", "down": "#qt"}),
          box([0, 46, 0], [16, 46.6, 16], "#gold"),
          box([5, 46.6, 5], [11, 48, 11], "#gold")]
    return 1, 3, 1, t, e


def card_wall():
    t = {"frame": V + "stripped_dark_oak_log", "panel": V + "black_wool", "gold": V + "gold_block",
         "head": "tcgshop:block/header", "particle": V + "dark_oak_planks"}
    e = [box([1.5, 1.5, 12], [46.5, 46.5, 16], "#panel"),
         box([0, 0, 11], [48, 1.5, 16], "#frame"),
         box([0, 46.5, 11], [48, 48, 16], {"*": "#frame", "north": "#head"}),
         box([0, 1.5, 11], [1.5, 46.5, 16], "#frame"), box([46.5, 1.5, 11], [48, 46.5, 16], "#frame")]
    for top in (2.5, 17.7, 32.9):
        e.append(box([2, top - 0.6, 9.5], [46, top, 12], "#gold"))
    return 3, 3, 1, t, e


def counter():
    t = {"base": V + "polished_blackstone", "wood": V + "oak_planks", "panel": V + "stripped_oak_log", "gold": V + "gold_block",
         "velvet": V + "blue_wool", "head": "tcgshop:block/header", "particle": V + "oak_planks"}
    e = [box([0, 0, 1], [32, 1, 15], "#base"),
         box([0.5, 1, 1.5], [31.5, 9, 15.5], {"*": "#wood", "north": "#panel"}),
         box([0, 9, 1], [32, 9.6, 16], "#gold"),
         box([0.5, 9.6, 2], [31.5, 10, 15], "#velvet"),
         box([1, 10, 3.2], [31, 11, 4.8], "#wood"),                 # stop the cards rest against
         box([0.5, 9.6, 13], [31.5, 15, 15.5], {"*": "#panel", "north": "#head"})]
    for x in (5.6, 15.5, 25.4):
        e.append(box([x, 10, 9], [x + 1, 14, 13], "#wood"))          # rear stands
    return 2, 1, 1, t, e


def showcase():
    t = {"wood": V + "dark_oak_planks", "log": V + "stripped_dark_oak_log", "glass": V + "glass", "light": V + "glowstone",
         "velvet": V + "red_wool", "particle": V + "glass"}
    e = [box([0.5, 0, 0.5], [15.5, 3, 15.5], "#log"),
         box([1.5, 3, 1.5], [14.5, 3.8, 14.5], "#velvet"),
         box([1.5, 16.6, 1.5], [14.5, 17.4, 14.5], "#wood"),
         box([1.5, 30.2, 1.5], [14.5, 31, 14.5], "#wood"),
         box([1, 3, 0.6], [15, 44, 1], "#glass", faces=("north", "south")),
         box([1, 3, 15], [15, 44, 15.4], "#glass", faces=("north", "south")),
         box([0.6, 3, 1], [1, 44, 15], "#glass", faces=("east", "west")),
         box([15, 3, 1], [15.4, 44, 15], "#glass", faces=("east", "west"))]
    for x, z in ((0.3, 0.3), (14.7, 0.3), (0.3, 14.7), (14.7, 14.7)):
        e.append(box([x, 3, z], [x + 1, 44, z + 1], "#log"))
    e += [box([2, 43.4, 2], [14, 44, 14], "#light"),
          box([0, 44, 0], [16, 48, 16], {"*": "#wood", "up": "#log"})]
    return 1, 3, 1, t, e


def ledge():
    t = {"wood": V + "spruce_planks", "log": V + "stripped_spruce_log", "gold": V + "gold_block", "particle": V + "spruce_planks"}
    e = [box([0, 1, 13], [32, 14, 16], "#wood"),
         box([0, 1, 5], [32, 2.4, 16], "#log"),
         box([0, 2.4, 5], [32, 3.6, 5.8], "#gold")]
    for x in (2, 15, 28):
        e.append(box([x, 0, 8], [x + 2, 1, 15], "#log"))             # brackets
    return 2, 1, 1, t, e


def box_shelf():
    t = {"wood": V + "spruce_planks", "log": V + "stripped_spruce_log", "strip": "tcgshop:block/price_strip",
         "head": "tcgshop:block/header", "particle": V + "spruce_planks"}
    e = [box([0, 0, 0.5], [1, 32, 16], "#log"), box([47, 0, 0.5], [48, 32, 16], "#log"),
         box([15.5, 0, 0.5], [16.5, 30.5, 16], "#log"), box([31.5, 0, 0.5], [32.5, 30.5, 16], "#log"),
         box([1, 0, 15], [47, 30.5, 16], "#wood"),
         box([1, 0, 0.5], [47, 1.3, 15], "#wood"),
         box([1, 15.8, 0.5], [47, 17, 15], "#wood"),
         box([1, 15.4, 0], [47, 16.6, 0.5], {"*": "#log", "north": "#strip"}),
         box([0, 30.5, 0], [48, 32, 16], {"*": "#log", "north": "#head"})]
    return 3, 2, 1, t, e


def frame():
    t = {"back": V + "black_wool", "gold": V + "gold_block", "particle": V + "gold_block"}
    e = [box([1, 1, 15], [15, 15, 16], "#back"),
         box([0.5, 0.5, 14], [15.5, 1.5, 16], "#gold"), box([0.5, 14.5, 14], [15.5, 15.5, 16], "#gold"),
         box([0.5, 1.5, 14], [1.5, 14.5, 16], "#gold"), box([14.5, 1.5, 14], [15.5, 14.5, 16], "#gold")]
    return 1, 1, 1, t, e


MULTI = {"card_stand": card_stand, "display_case": display_case, "pack_shelf": pack_shelf, "gondola": gondola,
         "box_pallet": box_pallet, "pedestal": pedestal, "card_wall": card_wall,
         "counter_display": counter, "showcase": showcase, "card_ledge": ledge, "box_shelf": box_shelf, "card_frame": frame,
         "home_frame": frame, "home_ledge": ledge, "home_showcase": showcase, "home_card_wall": card_wall, "home_pedestal": pedestal}


def cash_register():
    t = {"base": V + "smooth_stone", "body": "tcgshop:block/register_body", "keys": "tcgshop:block/register_keys",
         "screen": "tcgshop:block/register_screen", "metal": V + "iron_block", "gold": V + "gold_block",
         "paper": V + "white_concrete", "particle": "tcgshop:block/register_body"}
    e = [box([2, 0, 3.5], [14, 3, 13], "#base"),
         box([2.5, 3, 7.5], [13.5, 7, 13], "#body"),
         box([3, 3, 3.6], [13, 4.6, 7.5], {"*": "#body", "up": "#keys"}),
         box([7, 7, 10.8], [9, 8.6, 11.8], "#metal"),
         box([4, 7.6, 9.6], [12, 11, 10.8], {"*": "#body", "north": "#screen"}),
         box([10.5, 7, 7.6], [13, 8.2, 10], "#metal"),           # receipt printer
         box([11, 8.2, 8.2], [12.5, 9.6, 8.4], "#paper"),        # receipt
         box([2.2, 1.2, 3.3], [13.8, 1.5, 3.5], "#gold")]        # drawer trim
    return model(t, e)


def poster():
    t = {"back": V + "black_concrete", "gold": V + "gold_block", "particle": V + "gold_block"}
    e = [box([3.5, 0.3, 15.2], [12.5, 15.7, 16], "#back"),
         box([3, 0, 14.6], [3.7, 16, 16], "#gold"), box([12.3, 0, 14.6], [13, 16, 16], "#gold"),
         box([3.7, 15.4, 14.6], [12.3, 16, 16], "#gold"), box([3.7, 0, 14.6], [12.3, 0.6, 16], "#gold")]
    return model(t, e)


def play_table():
    t = {"wood": V + "dark_oak_planks", "log": V + "dark_oak_log", "felt": "tcgshop:block/felt", "particle": V + "dark_oak_planks"}
    e = [box([0, 11.5, 0], [16, 12.9, 16], "#wood"),
         box([0.6, 12.9, 0.6], [15.4, 13.02, 15.4], {"up": "#felt"}),
         box([1.5, 10, 1.5], [14.5, 11.5, 14.5], "#wood")]
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        e.append(box([x, 0, z], [x + 2, 11.5, z + 2], "#log"))
    e.append(box([3, 3, 2], [13, 4, 3], "#wood"))
    e.append(box([3, 3, 13], [13, 4, 14], "#wood"))
    return model(t, e)


def theft_detector(on):
    s = "_on" if on else ""
    t = {"base": V + "gray_concrete", "panel": "tcgshop:block/detector_panel" + s, "cap": V + "black_concrete",
         "light": "tcgshop:block/detector_light" + s, "particle": V + "gray_concrete"}
    e = [box([2, 0, 6], [14, 1, 10], "#base"),
         box([3, 1, 7.2], [13, 14.5, 8.8], {"*": "#cap", "north": "#panel", "south": "#panel"}),
         box([2.6, 1, 6.8], [3.6, 15, 9.2], "#cap"), box([12.4, 1, 6.8], [13.4, 15, 9.2], "#cap"),
         box([2.5, 15, 6.5], [13.5, 16, 9.5], "#cap"),
         box([3.6, 14.5, 6.6], [12.4, 15, 9.4], "#light")]
    extra = {"ambientocclusion": False}
    return model(t, e, **extra)


def roulette():
    t = {"wood": V + "dark_oak_planks", "log": V + "stripped_dark_oak_log", "felt": V + "green_wool",
         "gold": V + "gold_block", "base": V + "polished_blackstone", "particle": V + "green_wool"}
    e = [box([1, 0, 1], [15, 1, 15], "#base"),
         box([1.5, 1, 1.5], [14.5, 12, 14.5], "#wood"),
         box([1, 4, 1], [15, 5, 15], "#gold"),
         box([0, 12, 0], [16, 13.5, 16], {"*": "#log", "up": "#felt"}),
         box([0.5, 13.5, 0.5], [15.5, 14.6, 1.3], "#log"), box([0.5, 13.5, 14.7], [15.5, 14.6, 15.5], "#log"),
         box([0.5, 13.5, 1.3], [1.3, 14.6, 14.7], "#log"), box([14.7, 13.5, 1.3], [15.5, 14.6, 14.7], "#log"),
         box([0.3, 14.6, 0.3], [15.7, 14.9, 15.7], "#gold", faces=("north", "south", "east", "west")),
         box([1.3, 13.5, 1.3], [14.7, 13.9, 14.7], {"up": "#gold"})]
    return model(t, e)


def facing_states(model_name, extra=""):
    v = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        d = {"model": model_name}
        if y:
            d["y"] = y
        v[f"facing={f}{extra}"] = d
    return v


MODELS = {"cash_register": cash_register, "poster": poster, "play_table": play_table, "roulette": roulette}


def main():
    for name, fn in TEX.items():
        png(A / f"textures/block/{name}.png", fn())
    png(A / "textures/item/mystery_box.png", tex_mystery_item())
    write(A / "models/item/mystery_box.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "tcgshop:item/mystery_box"}})

    for name, fn in MULTI.items():
        W, H, D, t, els = fn()
        parts = slice_model(t, els, W, H, D)
        v = {}
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for p in range(16):
                d = {"model": f"tcgshop:block/{name}_p{p if p in parts else 0}"}
                if y:
                    d["y"] = y
                v[f"facing={f},part={p}"] = d
        old = A / f"models/block/{name}.json"
        if old.exists():
            old.unlink()
        for p, m in parts.items():
            write(A / f"models/block/{name}_p{p}.json", m)
        write(A / f"blockstates/{name}.json", {"variants": v})
        write(A / f"models/item/{name}.json", item_of(t, els, W, H, D))
        write(ROOT / f"data/tcgshop/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"tcgshop:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"},
            {"condition": "minecraft:block_state_property", "block": f"tcgshop:{name}", "properties": {"part": "0"}}]}]})
    for name, fn in MODELS.items():
        write(A / f"models/block/{name}.json", fn())
        write(A / f"blockstates/{name}.json", {"variants": facing_states(f"tcgshop:block/{name}")})
        write(A / f"models/item/{name}.json", {"parent": f"tcgshop:block/{name}"})
    write(A / "models/block/theft_detector.json", theft_detector(False))
    write(A / "models/block/theft_detector_on.json", theft_detector(True))
    v = {}
    v.update(facing_states("tcgshop:block/theft_detector", ",alarm=false"))
    v.update(facing_states("tcgshop:block/theft_detector_on", ",alarm=true"))
    write(A / "blockstates/theft_detector.json", {"variants": v})
    write(A / "models/item/theft_detector.json", {"parent": "tcgshop:block/theft_detector"})

    lt = ROOT / "data/tcgshop/loot_table/blocks"
    for name in list(MODELS) + ["theft_detector"]:
        write(lt / f"{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"tcgshop:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    print("furniture generated:",len(MODELS) + len(MULTI) + 1)


if __name__ == "__main__":
    main()
