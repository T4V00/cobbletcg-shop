#!/usr/bin/env python3
"""Generates textures, models, blockstates and loot tables for the shop blocks."""
import json
import random
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
A = ROOT / "assets/tcgshop"
spec = importlib.util.spec_from_file_location("gt", Path(__file__).resolve().parent / "gen_textures.py")
gt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gt)
png, hexc, T = gt.png, gt.hexc, gt.T

TIERS = {"wood": "9C6B3C", "stone": "8A8A8A", "iron": "D8D8D8", "gold": "F2C230", "diamond": "4EE2EC", "emerald": "2ECC71", "netherite": "4A3F45"}


def shade(c, k):
    r, g, b, a = c
    return (max(0, min(255, int(r * k))), max(0, min(255, int(g * k))), max(0, min(255, int(b * k))), a)


def core_side(base):
    rnd = random.Random(base)
    c = hexc(base)
    px = [[shade(c, 0.85 + rnd.random() * 0.25) for _ in range(16)] for _ in range(16)]
    # red/white striped awning
    for y in range(0, 5):
        for x in range(16):
            px[y][x] = hexc("D63A3A") if (x // 2) % 2 == 0 else hexc("F4F0E8")
    for x in range(16):
        px[5][x] = hexc("8E1F1F") if x % 2 == 0 else hexc("C9C2B4")
    # shop window with a card
    for y in range(7, 14):
        for x in range(3, 13):
            px[y][x] = hexc("9FD8F0") if (x + y) % 5 else hexc("CDEFFB")
    for y in range(8, 13):
        for x in range(6, 10):
            px[y][x] = hexc("F2C230") if y in (8, 12) or x in (6, 9) else hexc("3A6FD8")
    for x in range(2, 14):
        px[6][x] = shade(c, 0.6)
        px[14][x] = shade(c, 0.6)
    for y in range(6, 15):
        px[y][2] = shade(c, 0.6)
        px[y][13] = shade(c, 0.6)
    return px


def core_top(base):
    rnd = random.Random(base + "t")
    c = hexc(base)
    px = [[shade(c, 0.85 + rnd.random() * 0.25) for _ in range(16)] for _ in range(16)]
    for i in range(16):
        px[0][i] = px[15][i] = px[i][0] = px[i][15] = shade(c, 0.55)
    # simplified ₽ symbol
    for (x, y) in [(6, 4), (7, 4), (8, 4), (9, 4), (6, 5), (10, 5), (6, 6), (10, 6), (6, 7), (7, 7), (8, 7), (9, 7),
                   (6, 8), (6, 9), (6, 10), (6, 11), (4, 9), (5, 9), (7, 9), (8, 9)]:
        px[y][x] = hexc("FFFFFF")
    return px


def goggles():
    F = hexc("3A2A1A"); L = hexc("7FD6FF"); H = hexc("D8F4FF"); S = hexc("5E4630")
    px = [[T] * 16 for _ in range(16)]
    for y in range(6, 11):
        for x in range(1, 15):
            px[y][x] = S if y in (6, 10) else T
    for cx in (4, 11):
        for y in range(5, 12):
            for x in range(cx - 3, cx + 4):
                d = (x - cx) ** 2 + (y - 8) ** 2
                if d <= 9:
                    px[y][x] = F
                if d <= 4:
                    px[y][x] = L
        px[7][cx - 1] = H
    for x in range(7, 9):
        px[7][x] = F
    return px


def phone():
    B = hexc("1C1E24"); S = hexc("2F6FB0"); G = hexc("F2C230"); W = hexc("DDE6F0")
    px = [[T] * 16 for _ in range(16)]
    for y in range(1, 15):
        for x in range(4, 12):
            px[y][x] = B
    for y in range(3, 12):
        for x in range(5, 11):
            px[y][x] = S if (y + x) % 4 else hexc("4C8FD6")
    for (x, y) in [(6, 5), (7, 5), (9, 5), (6, 8), (8, 8), (9, 8)]:
        px[y][x] = G
    px[13][7] = W
    px[13][8] = W
    px[2][7] = hexc("3A3E4A")
    px[2][8] = hexc("3A3E4A")
    return px


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")


def facing_states(model):
    return {"variants": {f"facing={f}": ({"model": model, "y": y} if y else {"model": model})
                         for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}


def el(frm, to, tex, faces="all"):
    fs = ["north", "south", "east", "west", "up", "down"] if faces == "all" else faces
    return {"from": frm, "to": to, "faces": {f: {"texture": tex} for f in fs}}


def main():
    tex = A / "textures/block"
    tex.mkdir(parents=True, exist_ok=True)
    for t, col in TIERS.items():
        png(tex / f"shop_core_{t}_side.png", core_side(col))
        png(tex / f"shop_core_{t}_top.png", core_top(col))
        write(A / f"models/block/shop_core_{t}.json", {"parent": "minecraft:block/cube_bottom_top",
              "textures": {"side": f"tcgshop:block/shop_core_{t}_side", "top": f"tcgshop:block/shop_core_{t}_top",
                           "bottom": f"tcgshop:block/shop_core_{t}_top"}})
        write(A / f"blockstates/shop_core_{t}.json", {"variants": {"": {"model": f"tcgshop:block/shop_core_{t}"}}})
        write(A / f"models/item/shop_core_{t}.json", {"parent": f"tcgshop:block/shop_core_{t}"})
    png(A / "textures/item/manager_goggles.png", goggles())
    png(A / "textures/item/tcg_phone.png", phone())
    for n in ("manager_goggles", "tcg_phone"):
        write(A / f"models/item/{n}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"tcgshop:item/{n}"}})
    write(A / "models/item/wholesaler_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})

    loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": ""}],
                                                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    write(A / "models/block/booster_box.json", {"textures": {"particle": "minecraft:block/white_wool"}})
    write(A / "blockstates/booster_box.json", facing_states("tcgshop:block/booster_box"))
    write(A / "models/item/booster_box.json", {"parent": "minecraft:builtin/entity", "gui_light": "front", "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.9, 0.9, 0.9]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]}}})

    lt = ROOT / "data/tcgshop/loot_table/blocks"
    for name in [f"shop_core_{t}" for t in TIERS]:
        d = json.loads(json.dumps(loot))
        d["pools"][0]["entries"][0]["name"] = f"tcgshop:{name}"
        write(lt / f"{name}.json", d)
    write(ROOT / "data/accessories/tags/item/face.json", {"replace": False, "values": ["tcgshop:manager_goggles"]})
    fm = importlib.util.spec_from_file_location("fm", Path(__file__).resolve().parent / "gen_furniture_models.py")
    mod = importlib.util.module_from_spec(fm)
    fm.loader.exec_module(mod)
    mod.main()
    print("shop assets generated")


if __name__ == "__main__":
    main()
