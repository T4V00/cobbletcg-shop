#!/usr/bin/env python3
"""1.0 beta 3 casino: block models, blockstates, item models, loot tables, original pixel-art textures and the
casino advancements (dev.tcgshop.casino).

Models face north (-Z) like gen_furniture_models.py. The moving parts are drawn by casino/client/CasinoRenderer.java
on top of these models, so the coordinates below must match it:
  * slot machines: reel window x 3.5..12.5, y 8.5..12.5 px, reels drawn at z = 3.95 px; marquee lights at
    y 14.2..14.45, z 2.6..2.75 (in front of the topper face at z = 3);
  * roulette table: wheel centre (8, 14.05, 9.92) px, radius 4.8 px, inside the bowl rim;
  * fortune wheel: wheel centre (8, 16.5, 4.9) px, radius 7.4 px, in front of the back board face at z = 5;
  * blackjack table: felt top at y = 13 px.

Usage: python tools/gen_casino_assets.py  (only writes new files under common/src/main/resources)
"""
import importlib.util
import json
import math
import random
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("gfm", HERE / "gen_furniture_models.py")
gfm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gfm)

ROOT, A = gfm.ROOT, gfm.A
png, hexc, T = gfm.png, gfm.hexc, gfm.T
box, model, write, shade, grid, V = gfm.box, gfm.model, gfm.write, gfm.shade, gfm.grid, gfm.V
B = "tcgshop:block/"


# ---------------------------------------------------------------- textures (original pixel art)

def tex_lacquer(base, trim):
    rnd = random.Random(base)
    b, t = hexc(base), hexc(trim)

    def f(x, y):
        if y in (0, 15):
            return t
        if y in (1, 14):
            return shade(t, 0.7)
        k = 0.92 + rnd.random() * 0.06 + (0.1 if (x + y * 2) % 11 == 0 else 0)
        return shade(b, k)
    return grid(f)


def tex_chrome():
    def f(x, y):
        k = 0.75 + 0.35 * abs(math.sin((y + x * 0.25) / 3.0))
        return shade(hexc("C9CED6"), k)
    return grid(f)


def tex_slot_face():
    """Reel windows (what the item shows; in the world the renderer draws the live reels on top)."""
    sym = {0: ("D3122A", [(3, 7), (4, 7), (3, 8), (4, 8)]),
           1: ("E8132B", [(7, 5), (8, 5), (9, 5), (9, 6), (8, 7), (8, 8), (7, 9), (7, 10)]),
           2: ("FFC928", [(12, 6), (11, 7), (12, 7), (13, 7), (12, 8), (11, 9), (13, 9)])}

    def f(x, y):
        if y < 3 or y > 12:
            return hexc("2A0A0E")
        if x in (0, 5, 10, 15):
            return hexc("1A1A1A")
        for k, (c, pts) in sym.items():
            if (x, y) in pts:
                return hexc(c)
        if y in (7, 8) and x in (1, 6, 11):
            return hexc("FF3030")
        return hexc("F4F4F4") if 4 <= y <= 11 else hexc("BDBDBD")
    return grid(f)


def tex_sign(bg, fg):
    """Topper sign: three sevens on a sunburst."""
    seven = [(1, 0), (2, 0), (3, 0), (3, 1), (2, 2), (2, 3), (1, 4)]

    def f(x, y):
        for ox in (1, 6, 11):
            for (sx, sy) in seven:
                if x == ox + sx and y == 5 + sy:
                    return hexc(fg)
        if y in (0, 15) or x in (0, 15):
            return hexc("FFD34D")
        ray = int(math.degrees(math.atan2(y - 7.5, x - 7.5)) // 20) % 2
        return shade(hexc(bg), 1.0 if ray else 0.82)
    return grid(f)


def tex_buttons():
    cols = ["E53935", "FDD835", "43A047", "1E88E5"]

    def f(x, y):
        if 5 <= y <= 10:
            i = x // 4
            if x % 4 in (1, 2):
                return hexc(cols[i]) if y > 5 else shade(hexc(cols[i]), 1.3)
        return hexc("2B2B30") if (x + y) % 5 else hexc("33333A")
    return grid(f)


def tex_felt(base="1F6E3A"):
    rnd = random.Random(base)
    return grid(lambda x, y: shade(hexc(base), 0.9 + rnd.random() * 0.14))


def tex_layout():
    """Roulette betting layout (miniature): zero, a red/black grid and the outside boxes."""
    def f(x, y):
        if y in (1, 13) or x in (1, 15):
            return hexc("F2F2F2")
        if 2 <= y <= 8:
            if x == 2:
                return hexc("1E8C3A")
            if 3 <= x <= 14:
                if x % 2 == 0 or y in (4, 6):
                    return hexc("F2F2F2") if y in (4, 6) else hexc("1B1B1F") if (x + y) % 4 == 0 else hexc("C62828")
                return hexc("C62828") if (x // 2 + y) % 2 else hexc("1B1B1F")
        if y == 9:
            return hexc("F2F2F2")
        if 10 <= y <= 12 and 3 <= x <= 14:
            return hexc("C62828") if 7 <= x <= 8 else hexc("1B1B1F") if 9 <= x <= 10 else hexc("1F6E3A")
        return shade(hexc("1F6E3A"), 0.95)
    return grid(f)


def tex_bj_felt():
    """Blackjack felt: the dealer arc and two card boxes."""
    def f(x, y):
        d = math.hypot(x - 7.5, y + 6)
        if abs(d - 12.5) < 0.6:
            return hexc("E8D27A")
        if 10 <= y <= 13 and (3 <= x <= 5 or 10 <= x <= 12):
            return hexc("D8E8DA") if x in (3, 5, 10, 12) or y in (10, 13) else hexc("17603A")
        return shade(hexc("17603A"), 0.94 + ((x * 7 + y * 3) % 5) * 0.02)
    return grid(f)


def tex_wood():
    rnd = random.Random(5)
    base = hexc("6A2E14")

    def f(x, y):
        k = 0.85 + 0.15 * math.sin((y * 1.7 + math.sin(x * 0.6) * 2)) + rnd.random() * 0.05
        return shade(base, k)
    return grid(f)


WHEEL_COLORS = ["3A7BD5", "43A047", "F4511E", "8E24AA", "FDD835", "212121", "E53935"]
WHEEL_LAYOUT = None  # filled from the Java layout rule below


def wheel_layout():
    """Same spreading rule as WheelMath.LAYOUT (only for the picture on the board)."""
    counts = [24, 15, 7, 4, 2, 1, 1]
    lay = [-1] * 54
    for s in range(6, -1, -1):
        free = sum(1 for v in lay if v < 0)
        n = counts[s]
        step = free / n
        at = free / 2.0 if s == 6 else 0
        for k in range(n):
            target = int(math.floor(at + k * step)) % free
            seen = 0
            for i in range(54):
                if lay[i] >= 0:
                    continue
                if seen == target:
                    lay[i] = s
                    break
                seen += 1
    return [0 if v < 0 else v for v in lay]


def tex_wheel_board():
    """Back board of the fortune wheel: the wheel picture (shown by the item; the world draws the live wheel)."""
    lay = wheel_layout()

    def f(x, y):
        dx, dy = x - 7.5, y - 7.5
        r = math.hypot(dx, dy)
        if r > 7.6:
            return hexc("3B1E5A") if (x in (0, 15) or y in (0, 15)) else hexc("2A1640")
        if r > 7.0:
            return hexc("FFD34D")
        if r < 2.2:
            return hexc("2A1640") if r > 1 else hexc("FFD34D")
        a = (math.degrees(math.atan2(dx, -dy)) + 360) % 360
        seg = int((a + 360 / 108) // (360 / 54)) % 54
        return hexc(WHEEL_COLORS[lay[seg]])
    return grid(f)


def tex_ticket(c1, c2, item_accent):
    """Scratch ticket item icon: printed card with a silver scratch area and a coin."""
    px = [[T] * 16 for _ in range(16)]
    for y in range(2, 14):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (2, 13)
            px[y][x] = hexc("1A1A1A") if edge else (hexc(c1) if y < 6 else hexc(c2))
    for y in range(7, 12):
        for x in range(3, 13):
            px[y][x] = hexc("B8BEC6") if (x + y) % 3 else hexc("D8DDE3")
    for (x, y) in [(5, 9), (6, 8), (7, 9), (8, 10), (9, 9), (10, 8)]:
        px[y][x] = hexc("7A8088")  # scratched zig-zag
    for (x, y) in [(11, 3), (12, 3), (11, 4), (12, 4)]:
        px[y][x] = hexc(item_accent)
    for x in range(3, 9):
        px[4][x] = hexc("FFFFFF") if x % 2 else hexc(c2)
    return px


TEX = {
    "casino_slot_body": lambda: tex_lacquer("B71C1C", "FFD34D"),
    "casino_vip_body": lambda: tex_lacquer("C9A227", "FFF3C0"),
    "casino_dark_body": lambda: tex_lacquer("2A1A1C", "6A5A40"),
    "casino_chrome": tex_chrome,
    "casino_slot_face": tex_slot_face,
    "casino_slot_sign": lambda: tex_sign("8E0E1A", "FFD34D"),
    "casino_vip_sign": lambda: tex_sign("1A1A1A", "FFD34D"),
    "casino_buttons": tex_buttons,
    "casino_felt": tex_felt,
    "casino_layout": tex_layout,
    "casino_bj_felt": tex_bj_felt,
    "casino_wood": tex_wood,
    "casino_wheel_board": tex_wheel_board,
}

TICKETS = {
    "scratch_card_bronze": ("CD7F32", "8A5222", "FFE0B2"),
    "scratch_card_silver": ("B8C4CF", "5A6670", "FFFFFF"),
    "scratch_card_gold": ("FFD34D", "9A6A00", "FF5252"),
}


# ---------------------------------------------------------------- models

def slot_machine(vip):
    body = B + ("casino_vip_body" if vip else "casino_slot_body")
    t = {"body": body, "dark": B + "casino_dark_body", "chrome": B + "casino_chrome", "face": B + "casino_slot_face",
         "sign": B + ("casino_vip_sign" if vip else "casino_slot_sign"), "buttons": B + "casino_buttons",
         "knob": V + ("gold_block" if vip else "red_concrete"), "particle": body}
    e = [box([2, 0, 3], [14, 1, 14], "#dark"),
         box([2.5, 1, 3.5], [13.5, 7, 14], "#body"),
         box([2, 6, 1.5], [14, 7.5, 4], {"*": "#chrome", "up": "#buttons"}),
         box([2.5, 7.5, 4], [13.5, 13.5, 14], {"*": "#body", "north": "#face"}),
         box([3, 8, 3.6], [3.5, 13, 4], "#chrome"), box([12.5, 8, 3.6], [13, 13, 4], "#chrome"),
         box([3, 12.5, 3.6], [13, 13, 4], "#chrome"), box([3, 8, 3.6], [13, 8.5, 4], "#chrome"),
         box([2, 13.5, 3], [14, 16, 14], {"*": "#body", "north": "#sign"}),
         box([1.8, 15.6, 2.8], [14.2, 16, 14.2], "#chrome"),
         box([14, 8, 8], [14.6, 12, 8.6], "#chrome"),
         box([13.8, 12, 7.7], [15, 13.2, 8.9], "#knob"),
         box([5, 2, 3.4], [11, 4, 3.5], {"north": "#dark"})]
    return model(t, e)


def roulette_table():
    t = {"wood": B + "casino_wood", "felt": B + "casino_felt", "layout": B + "casino_layout", "gold": V + "gold_block",
         "base": V + "polished_blackstone", "particle": B + "casino_felt"}
    e = [box([1, 0, 1], [15, 1, 15], "#base"),
         box([6, 1, 6], [10, 11, 10], "#wood"),
         box([0, 11, 0], [16, 13, 16], {"*": "#wood", "up": "#felt"}),
         box([0.6, 13, 0.4], [15.4, 13.05, 4.6], {"up": "#layout"}),
         # wheel bowl rim around the wheel (centre 8, 9.92; radius 4.8)
         box([2.6, 13, 4.5], [13.4, 14.3, 5.0], {"*": "#wood", "up": "#gold"}),
         box([2.6, 13, 14.8], [13.4, 14.3, 15.3], {"*": "#wood", "up": "#gold"}),
         box([2.6, 13, 5.0], [3.1, 14.3, 14.8], {"*": "#wood", "up": "#gold"}),
         box([12.9, 13, 5.0], [13.4, 14.3, 14.8], {"*": "#wood", "up": "#gold"}),
         box([3.1, 13, 5.0], [12.9, 13.6, 14.8], {"up": "#wood"}),
         box([0, 12.6, -0.2], [16, 13.2, 0.4], "#gold")]
    return model(t, e)


def blackjack_table():
    t = {"wood": B + "casino_wood", "felt": B + "casino_bj_felt", "rail": V + "black_wool", "gold": V + "gold_block",
         "base": V + "polished_blackstone", "shoe": B + "casino_dark_body", "particle": B + "casino_bj_felt"}
    e = [box([1, 0, 1], [15, 1, 15], "#base"),
         box([6, 1, 6], [10, 11, 10], "#wood"),
         box([0, 11, 0], [16, 13, 16], {"*": "#wood", "up": "#felt"}),
         box([0, 13, 0], [16, 13.8, 1.2], "#rail"),
         box([0, 13, 1.2], [1.2, 13.8, 16], "#rail"), box([14.8, 13, 1.2], [16, 13.8, 16], "#rail"),
         box([0, 12.8, -0.2], [16, 13.1, 0.2], "#gold"),
         # chip tray and card shoe at the dealer side (back)
         box([5, 13, 14], [11, 13.6, 15.6], {"*": "#gold", "up": "#shoe"}),
         box([12, 13, 12.5], [14, 14.6, 15.2], "#shoe"),
         box([12.3, 14.6, 12.8], [13.7, 14.7, 14.9], {"up": "#gold"})]
    return model(t, e)


def fortune_wheel():
    t = {"wood": B + "casino_wood", "board": B + "casino_wheel_board", "gold": V + "gold_block", "frame": B + "casino_dark_body",
         "base": V + "polished_blackstone", "particle": B + "casino_wheel_board"}
    e = [box([3, 0, 4], [13, 2, 12], "#base"),
         box([7, 2, 6.5], [9, 9.5, 9.5], "#wood"),
         box([0.5, 8.5, 5], [15.5, 24.5, 6], {"*": "#frame", "north": "#board"}),
         box([0.2, 8.2, 5.2], [15.8, 8.6, 6.2], "#gold"), box([0.2, 24.4, 5.2], [15.8, 24.8, 6.2], "#gold"),
         box([0.2, 8.6, 5.2], [0.6, 24.4, 6.2], "#gold"), box([15.4, 8.6, 5.2], [15.8, 24.4, 6.2], "#gold")]
    return model(t, e)


def facing_states(model_name):
    v = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        d = {"model": model_name}
        if y:
            d["y"] = y
        v[f"facing={f}"] = d
    return v


MODELS = {
    "slot_machine": lambda: slot_machine(False),
    "vip_slot_machine": lambda: slot_machine(True),
    "roulette_table": roulette_table,
    "blackjack_table": blackjack_table,
    "fortune_wheel": fortune_wheel,
}

# (id, icon, parent, frame); titles and descriptions live in tools/gen_lang_10c_gambling.py
ADVANCEMENTS = [
    ("casino_first_win", "tcgshop:slot_machine", "tcgshop:root", "task"),
    ("casino_jackpot", "tcgshop:vip_slot_machine", "tcgshop:casino_first_win", "challenge"),
    ("casino_blackjack", "tcgshop:blackjack_table", "tcgshop:casino_first_win", "goal"),
    ("casino_straight_up", "tcgshop:roulette_table", "tcgshop:casino_first_win", "goal"),
    ("casino_wheel_joker", "tcgshop:fortune_wheel", "tcgshop:casino_first_win", "goal"),
    ("casino_scratch_top", "tcgshop:scratch_card_gold", "tcgshop:casino_first_win", "challenge"),
    ("casino_millionaire", "minecraft:gold_block", "tcgshop:casino_first_win", "challenge"),
]


def main():
    for name, fn in TEX.items():
        png(A / f"textures/block/{name}.png", fn())
    for name, (c1, c2, acc) in TICKETS.items():
        png(A / f"textures/item/{name}.png", tex_ticket(c1, c2, acc))
        write(A / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"tcgshop:item/{name}"}})
    lt = ROOT / "data/tcgshop/loot_table/blocks"
    for name, fn in MODELS.items():
        write(A / f"models/block/{name}.json", fn())
        write(A / f"blockstates/{name}.json", {"variants": facing_states(f"tcgshop:block/{name}")})
        item = {"parent": f"tcgshop:block/{name}"}
        if name == "fortune_wheel":  # 1.5 blocks tall: smaller in slots and hands
            item["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.42, 0.42, 0.42]},
                               "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
                               "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.38, 0.38, 0.38]},
                               "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.25, 0.25, 0.25]},
                               "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.28, 0.28, 0.28]},
                               "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.28, 0.28, 0.28]}}
        write(A / f"models/item/{name}.json", item)
        write(lt / f"{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"tcgshop:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    adv = ROOT / "data/tcgshop/advancement"
    for aid, icon, parent, frame in ADVANCEMENTS:
        write(adv / f"{aid}.json", {
            "display": {"icon": {"id": icon}, "title": {"translate": f"advancement.tcgshop.{aid}.title"},
                        "description": {"translate": f"advancement.tcgshop.{aid}.desc"}, "frame": frame,
                        "show_toast": True, "announce_to_chat": frame == "challenge", "hidden": False},
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "parent": parent})
    print("casino assets:", len(TEX), "block textures,", len(TICKETS), "tickets,", len(MODELS), "machines,", len(ADVANCEMENTS), "advancements")


if __name__ == "__main__":
    main()
