#!/usr/bin/env python3
"""1.1 (Group E): two more gambling tables, in the style of tools/gen_casino_assets.py (whose helpers it reuses).

  * dice_table (Lucky Seven): a pedestal table with a padded purple felt, the UNDER 7 / 7 / OVER 7 layout printed on it,
    a gold-trimmed wooden rail, a dice cup and a chip rack at the dealer side. The two dice are drawn by
    casino/client/CasinoRenderer.dice() on the felt top (y = 14 px).
  * coin_toss (Double or Nothing): a lacquered podium with gold bands, a chrome collar and a minted plate on top
    (y = 14 px) where CasinoRenderer.coin() spins and lands the big coin.

Every texture is original pixel art generated here (no vanilla textures). Writes block models, blockstates, item
models and loot tables. Run: python tools/gen_casino_tables_11.py
"""
import importlib.util
import math
import random
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("gca", HERE / "gen_casino_assets.py")
gca = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gca)

ROOT, A = gca.ROOT, gca.A
png, hexc, T = gca.png, gca.hexc, gca.T
box, model, write, shade, grid = gca.box, gca.model, gca.write, gca.shade, gca.grid
B = gca.B


# ---------------------------------------------------------------- textures

def tex_dice_felt():
    """Purple felt with the three main boxes (UNDER 7 blue, 7 gold, OVER 7 red) and a dotted gold border."""
    rnd = random.Random(7)

    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return hexc("FFE27A") if (x + y) % 2 == 0 else hexc("B8860B")
        if 9 <= y <= 13:
            if 1 <= x <= 4:
                return hexc("F2F2F2") if x in (1, 4) or y in (9, 13) else hexc("3A7BD5")
            if 6 <= x <= 9:
                return hexc("F2F2F2") if x in (6, 9) or y in (9, 13) else hexc("E5B21E")
            if 11 <= x <= 14:
                return hexc("F2F2F2") if x in (11, 14) or y in (9, 13) else hexc("D9472B")
        if 3 <= y <= 6 and 5 <= x <= 10:  # a big "7" in the middle
            if y == 3 or (x == 13 - y + 1 and 5 <= x <= 10):
                return hexc("FFE27A")
        return shade(hexc("4A2380"), 0.9 + rnd.random() * 0.12)
    return grid(f)


def tex_gold_trim():
    def f(x, y):
        k = 0.8 + 0.3 * abs(math.sin((x + y * 0.5) / 2.5))
        return shade(hexc("E5B21E"), k)
    return grid(f)


def tex_velvet():
    rnd = random.Random(11)
    return grid(lambda x, y: shade(hexc("7A1630"), 0.85 + rnd.random() * 0.2 + (0.08 if (x * 3 + y) % 7 == 0 else 0)))


def tex_coin_plate():
    """Minted plate: concentric gold rings with a star in the middle."""
    def f(x, y):
        r = math.hypot(x - 7.5, y - 7.5)
        if r > 7.4:
            return hexc("2A1A1C")
        if r > 6.6:
            return hexc("FFE27A")
        if r > 5.8:
            return hexc("B8860B")
        if abs(x - 7.5) < 0.6 or abs(y - 7.5) < 0.6 or abs(abs(x - 7.5) - abs(y - 7.5)) < 0.6:
            return hexc("FFF3C0") if r < 3.2 else shade(hexc("E5B21E"), 0.9)
        return shade(hexc("E5B21E"), 0.85 + 0.1 * math.cos(r * 1.6))
    return grid(f)


def tex_podium():
    return gca.tex_lacquer("1E3A5A", "E5B21E")


def tex_cup():
    rnd = random.Random(3)
    return grid(lambda x, y: hexc("D9A420") if y in (0, 1) else shade(hexc("5A2A10"), 0.85 + rnd.random() * 0.15))


TEX = {
    "casino_dice_felt": tex_dice_felt,
    "casino_gold_trim": tex_gold_trim,
    "casino_velvet": tex_velvet,
    "casino_coin_plate": tex_coin_plate,
    "casino_podium": tex_podium,
    "casino_dice_cup": tex_cup,
}


# ---------------------------------------------------------------- models (north-facing)

def dice_table():
    t = {"wood": B + "casino_wood", "felt": B + "casino_dice_felt", "gold": B + "casino_gold_trim", "rail": B + "casino_velvet",
         "base": B + "casino_dark_body", "cup": B + "casino_dice_cup", "chrome": B + "casino_chrome", "particle": B + "casino_dice_felt"}
    e = [box([1, 0, 1], [15, 1.2, 15], "#base"),
         box([2, 1.2, 2], [14, 1.8, 14], "#gold"),
         box([5.5, 1.8, 5.5], [10.5, 11, 10.5], "#wood"),
         box([5, 9.5, 5], [11, 10.2, 11], "#gold"),
         box([0, 11, 0], [16, 13.4, 16], {"*": "#wood", "up": "#felt"}),
         box([0.4, 13.4, 0.4], [15.6, 14, 15.6], {"*": "#wood", "up": "#felt"}),  # padded felt top (y = 14)
         # velvet rail on three sides + gold lip at the front
         box([0, 13.4, 0], [16, 15, 0.9], {"*": "#rail", "up": "#gold"}),
         box([0, 13.4, 0.9], [0.9, 15, 16], {"*": "#rail", "up": "#gold"}),
         box([15.1, 13.4, 0.9], [16, 15, 16], {"*": "#rail", "up": "#gold"}),
         box([0, 12.9, -0.25], [16, 13.3, 0.1], "#gold"),
         # dealer side: chip rack and the dice cup
         box([3, 14, 14.4], [10, 14.8, 15.8], {"*": "#gold", "up": "#chrome"}),
         box([11.5, 14, 13.2], [14, 16.6, 15.6], {"*": "#cup", "up": "#gold"})]
    return model(t, e)


def coin_toss():
    t = {"body": B + "casino_podium", "gold": B + "casino_gold_trim", "chrome": B + "casino_chrome", "plate": B + "casino_coin_plate",
         "base": B + "casino_dark_body", "velvet": B + "casino_velvet", "particle": B + "casino_podium"}
    e = [box([2, 0, 2], [14, 1.5, 14], "#base"),
         box([2.5, 1.5, 2.5], [13.5, 2, 13.5], "#gold"),
         box([3.5, 2, 3.5], [12.5, 10.5, 12.5], "#body"),
         box([3.3, 5, 3.3], [12.7, 5.6, 12.7], "#gold"),
         box([3.3, 8.4, 3.3], [12.7, 9, 12.7], "#gold"),
         box([2.8, 10.5, 2.8], [13.2, 12, 13.2], "#chrome"),
         box([1.5, 12, 1.5], [14.5, 13.2, 14.5], {"*": "#velvet", "up": "#velvet"}),
         box([2.2, 13.2, 2.2], [13.8, 14, 13.8], {"*": "#gold", "up": "#plate"}),  # coin plate (y = 14)
         # a small sign at the front
         box([5, 6.2, 3.2], [11, 8, 3.5], {"north": "#gold"})]
    return model(t, e)


MODELS = {"dice_table": dice_table, "coin_toss": coin_toss}


def main():
    for name, fn in TEX.items():
        png(A / f"textures/block/{name}.png", fn())
    lt = ROOT / "data/tcgshop/loot_table/blocks"
    for name, fn in MODELS.items():
        write(A / f"models/block/{name}.json", fn())
        write(A / f"blockstates/{name}.json", {"variants": gca.facing_states(f"tcgshop:block/{name}")})
        write(A / f"models/item/{name}.json", {"parent": f"tcgshop:block/{name}"})
        write(lt / f"{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"tcgshop:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    print("casino 1.1 tables:", len(TEX), "textures,", len(MODELS), "tables")


if __name__ == "__main__":
    main()
