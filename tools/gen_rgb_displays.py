#!/usr/bin/env python3
"""1.0 beta 2: RGB display fixtures (models, textures, blockstates, item models, loot tables and LED layouts).

Each fixture is a multi-block display (same DisplayKind slot system as the classic displays, see
shop/RgbDisplays.java). The static furniture goes into block models (sliced per part like gen_furniture_models.py);
the LEDs are described once here and written twice:
  * as dark "diffuser" channel elements in the block models (what you see with the lights off), and
  * as segments in assets/tcgshop/rgb/<name>.json, which client/render/RgbLights.java draws animated on top
    (color cycling, full-bright core + soft halo, floor underglow and wall backglow).
Coordinates are structure pixels: x in [0, 16W], y in [0, 16H], z in [0, 16D], z = 0 is the front (north),
exactly like gen_furniture_models.py and DisplayKind (x grows towards the customer's left).

Usage: python tools/gen_rgb_displays.py  (only writes new files under common/src/main/resources)
"""
import colorsys
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
box, slice_model, item_of, write, shade, grid = gfm.box, gfm.slice_model, gfm.item_of, gfm.write, gfm.shade, gfm.grid
V = gfm.V


# ---------------------------------------------------------------- textures (original pixel art)

def tex_carbon():
    a, b, hi = hexc("17181C"), hexc("24262C"), hexc("33363E")

    def f(x, y):
        cell = ((x // 2) + (y // 2)) % 2
        c = a if cell else b
        if (x + y) % 4 == 0:
            c = hi if cell else shade(b, 1.15)
        return c
    return grid(f)


def tex_panel():
    rnd = random.Random(21)
    base = hexc("2A2D34")

    def f(x, y):
        if y in (0, 15):
            return hexc("1A1C21")
        k = 0.94 + rnd.random() * 0.08 + (0.05 if y % 5 == 2 else 0)
        return shade(base, k)
    return grid(f)


def tex_glass():
    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return (120, 130, 150, 200)
        if (x + y) in (5, 6, 20):
            return (220, 230, 255, 120)
        return (40, 44, 60, 80)
    return grid(f)


def tex_led_off():
    def f(x, y):
        d = abs(y - 7.5)
        if d < 2:
            return hexc("4A4D57")
        if d < 5:
            return hexc("383A42")
        return hexc("26282E")
    return grid(f)


def tex_led_rainbow():
    def f(x, y):
        r, g, b = colorsys.hsv_to_rgb(((x + y) % 16) / 16.0, 0.85, 1.0)
        return (int(r * 255), int(g * 255), int(b * 255), 255)
    return grid(f)


def tex_grill():
    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return hexc("2E3138")
        return hexc("0E0F12") if y % 3 == 1 else hexc("3A3D45")
    return grid(f)


def tex_velvet():
    rnd = random.Random(33)
    base = hexc("1C1430")
    return grid(lambda x, y: shade(base, 0.9 + rnd.random() * 0.22))


def tex_chrome():
    def f(x, y):
        v = 150 + int(70 * math.sin((x * 0.6 + y * 0.25)))
        v = max(90, min(235, v))
        return (v, v, min(255, v + 12), 255)
    return grid(f)


def tex_pegboard_dark():
    rnd = random.Random(5)
    base = hexc("23252B")
    return grid(lambda x, y: hexc("0B0C0F") if (x % 4 == 1 and y % 4 == 1) else shade(base, 0.92 + rnd.random() * 0.12))


TEX = {"rgb_carbon": tex_carbon, "rgb_panel": tex_panel, "rgb_glass": tex_glass, "rgb_led": tex_led_off,
       "rgb_led_rainbow": tex_led_rainbow, "rgb_grill": tex_grill, "rgb_velvet": tex_velvet, "rgb_chrome": tex_chrome,
       "rgb_pegboard": tex_pegboard_dark}

TEXTURES = {"carbon": "tcgshop:block/rgb_carbon", "panel": "tcgshop:block/rgb_panel", "glass": "tcgshop:block/rgb_glass",
            "led": "tcgshop:block/rgb_led", "grill": "tcgshop:block/rgb_grill", "velvet": "tcgshop:block/rgb_velvet",
            "chrome": "tcgshop:block/rgb_chrome", "peg": "tcgshop:block/rgb_pegboard", "base": V + "polished_blackstone",
            "gold": V + "gold_block", "particle": "tcgshop:block/rgb_panel"}


# ---------------------------------------------------------------- LED layout helper

class Leds:
    """Collects LED segments (for the renderer) and their dark channel elements (for the block model)."""

    def __init__(self):
        self.segs, self.under, self.back, self.channels = [], [], [], []
        self.dist = 0.0  # path length in blocks: the "chase" animation flows along it

    def bar(self, a, b, t=0.6, step=4.0, channel=True):
        """Straight axis-aligned LED from point a to point b (center line), square cross-section t."""
        axis = [i for i in range(3) if a[i] != b[i]]
        assert len(axis) <= 1, ("LED bars must be axis-aligned", a, b)
        length = abs(b[axis[0]] - a[axis[0]]) if axis else 0.0
        n = max(1, round(length / step))
        h = t / 2
        for k in range(n):
            p0 = [a[i] + (b[i] - a[i]) * k / n for i in range(3)]
            p1 = [a[i] + (b[i] - a[i]) * (k + 1) / n for i in range(3)]
            lo = [min(p0[i], p1[i]) - (0 if axis and i == axis[0] else h) for i in range(3)]
            hi = [max(p0[i], p1[i]) + (0 if axis and i == axis[0] else h) for i in range(3)]
            if not axis:
                lo, hi = [p0[i] - h for i in range(3)], [p0[i] + h for i in range(3)]
            phase = self.dist + length * (k + 0.5) / n / 16.0
            self.segs.append([round(v, 4) for v in lo + hi] + [round(phase, 4)])
        self.dist += length / 16.0
        if channel:
            lo = [min(a[i], b[i]) - (0 if axis and i == axis[0] else h) for i in range(3)]
            hi = [max(a[i], b[i]) + (0 if axis and i == axis[0] else h) for i in range(3)]
            self.channels.append(box([round(v, 4) for v in lo], [round(v, 4) for v in hi], "#led"))

    def path(self, pts, t=0.6, closed=False):
        seq = list(pts) + ([pts[0]] if closed else [])
        for p, q in zip(seq, seq[1:]):
            self.bar(p, q, t)

    def rect_z(self, x0, y0, x1, y1, z, t=0.6):
        """Front-facing rectangle (vertical plane at depth z)."""
        self.path([(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)], t, closed=True)

    def rect_y(self, x0, z0, x1, z1, y, t=0.6):
        """Horizontal rectangle at height y (a band around a box)."""
        self.path([(x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1)], t, closed=True)

    def ring(self, cx, y, cz, r, t=0.9, n=24):
        """Circle of LED dots (no channel: the model provides the plate they sit on)."""
        chord = 2 * math.pi * r / n / 16.0
        for k in range(n):
            ang = 2 * math.pi * k / n
            x, z = cx + math.cos(ang) * r, cz + math.sin(ang) * r
            h = t / 2
            self.segs.append([round(v, 4) for v in (x - h, y - h, z - h, x + h, y + h, z + h)] + [round(self.dist + chord * k, 4)])
        self.dist += chord * n

    def gap(self, blocks=0.5):
        self.dist += blocks

    def underglow(self, x0, z0, x1, z1):
        self.under.append([x0, z0, x1, z1])

    def backglow(self, x0, y0, x1, y1):
        self.back.append([x0, y0, x1, y1])

    def json(self):
        return {"segs": self.segs, "under": self.under, "back": self.back}


# ---------------------------------------------------------------- fixtures
# Each returns (W, H, D, elements, Leds). Slot coordinates (DisplayKind) are noted where the geometry depends on them.

def frame_elems(ox=0.0):
    return [box([ox + 1, 1, 15], [ox + 15, 15, 16], "#velvet"),
            box([ox + 0.5, 0.5, 14], [ox + 15.5, 1.5, 16], "#carbon"), box([ox + 0.5, 14.5, 14], [ox + 15.5, 15.5, 16], "#carbon"),
            box([ox + 0.5, 1.5, 14], [ox + 1.5, 14.5, 16], "#carbon"), box([ox + 14.5, 1.5, 14], [ox + 15.5, 14.5, 16], "#carbon")]


def rgb_card_frame():  # FRAME: card at (0.5, 0.5, z 0.84)
    L = Leds()
    L.rect_z(1, 1, 15, 15, 13.75, 0.5)
    L.backglow(-3, -3, 19, 19)
    return 1, 1, 1, frame_elems(), L


def rgb_wall_trio():  # WALL_TRIO: three FRAME slots in a row
    L = Leds()
    e = []
    for i in range(3):
        e += frame_elems(16 * i)
        L.rect_z(16 * i + 1, 1, 16 * i + 15, 15, 13.75, 0.5)
    e.append(box([15.5, 7, 15], [16.5, 9, 16], "#chrome"))
    e.append(box([31.5, 7, 15], [32.5, 9, 16], "#chrome"))
    L.backglow(-3, -3, 51, 19)
    return 3, 1, 1, e, L


def rgb_card_ledge():  # LEDGE: cards at y 0.52, z 0.62
    L = Leds()
    e = [box([0, 1, 13], [32, 14, 16], {"*": "#carbon", "north": "#panel"}),
         box([0, 1, 5], [32, 2.4, 16], "#carbon"),
         box([0, 2.4, 5], [32, 3.4, 5.8], "#chrome")]
    for x in (2, 15, 28):
        e.append(box([x, 0, 8], [x + 2, 1, 15], "#chrome"))
    L.bar((0, 1.7, 4.7), (32, 1.7, 4.7), 0.6)
    L.gap()
    L.bar((0, 14.3, 14.5), (32, 14.3, 14.5), 0.6)
    L.backglow(-3, -2, 35, 17)
    return 2, 1, 1, e, L


def rgb_counter():  # COUNTER: 3 tilted cards at y 0.86, z 0.52
    L = Leds()
    e = [box([0, 0, 1], [32, 1, 15], "#base"),
         box([0.5, 1, 1.5], [31.5, 9, 15.5], {"*": "#carbon", "north": "#grill"}),
         box([0, 9, 1], [32, 9.6, 16], "#chrome"),
         box([0.5, 9.6, 2], [31.5, 10, 15], "#velvet"),
         box([1, 10, 3.2], [31, 11, 4.8], "#carbon"),
         box([0.5, 9.6, 13], [31.5, 15, 15.5], "#carbon")]
    for x in (5.6, 15.5, 25.4):
        e.append(box([x, 10, 9], [x + 1, 14, 13], "#chrome"))
    L.bar((0, 8.6, 0.7), (32, 8.6, 0.7), 0.6)
    L.gap()
    L.bar((0.5, 1.3, 1.1), (31.5, 1.3, 1.1), 0.5)
    L.gap()
    L.bar((0.5, 15.25, 14.25), (31.5, 15.25, 14.25), 0.5)
    L.underglow(0, 1, 32, 15)
    return 2, 1, 1, e, L


def rgb_card_stand():  # CARD_STAND: 2 big cards at y 1.13, z 0.48
    L = Leds()
    e = [box([0, 0, 2], [32, 3, 14], "#carbon"),
         box([0.5, 3, 2.5], [31.5, 3.5, 13.5], "#chrome"),
         box([1, 3.5, 3], [31, 8, 13], {"*": "#panel", "north": "#grill"}),
         box([1, 8, 5], [31, 9.6, 7.5], "#carbon"),
         box([1, 9.6, 5], [31, 10.2, 5.6], "#chrome"),
         box([1.5, 9.6, 10.6], [30.5, 29, 11.6], "#velvet"),
         box([0.5, 8, 10], [1.5, 31, 12.2], "#carbon"), box([30.5, 8, 10], [31.5, 31, 12.2], "#carbon"),
         box([15.5, 9.6, 10.2], [16.5, 29, 11.2], "#chrome"),
         box([0.5, 29, 10], [31.5, 31.5, 12.2], "#carbon")]
    L.path([(1, 8, 9.7), (1, 30.25, 9.7), (31, 30.25, 9.7), (31, 8, 9.7)], 0.5)
    L.gap()
    L.bar((0, 1.5, 1.7), (32, 1.5, 1.7), 0.6)
    L.underglow(0, 2, 32, 14)
    return 2, 2, 1, e, L


def rgb_turntable():  # TURNTABLE: one product spinning at y 0.64
    L = Leds()
    e = [box([2, 0, 2], [14, 3, 14], {"*": "#carbon", "north": "#grill", "south": "#grill"}),
         box([1.5, 3, 1.5], [14.5, 4, 14.5], "#panel"),
         box([3, 4, 3], [13, 4.6, 13], "#chrome"),
         box([7.5, 4.6, 7.5], [8.5, 5.4, 8.5], "#carbon")]
    L.ring(8, 4.2, 8, 6.0, 0.9, 24)
    L.gap()
    L.rect_y(1.7, 1.7, 14.3, 14.3, 0.8, 0.6)
    L.underglow(2, 2, 14, 14)
    return 1, 1, 1, e, L


def glass_case(W):
    """Glass counter (LYING cards at y 0.63, z 0.5): W blocks wide."""
    X = 16 * W
    L = Leds()
    e = [box([0.5, 0, 0.5], [X - 0.5, 1, 15.5], "#base"),
         box([0, 1, 0.6], [X, 6.6, 16], {"*": "#carbon", "north": "#grill"}),
         box([0, 6.6, 0], [X, 7.2, 16], "#chrome"),
         box([0.6, 7.2, 0.6], [X - 0.6, 7.4, 15.4], "#velvet"),
         box([1, 7.4, 0.3], [X - 1, 15.6, 0.7], "#glass", faces=("north", "south")),
         box([1, 7.4, 15.3], [X - 1, 15.6, 15.7], "#glass", faces=("north", "south")),
         box([0.3, 7.4, 1], [0.7, 15.6, 15], "#glass", faces=("east", "west")),
         box([X - 0.7, 7.4, 1], [X - 0.3, 15.6, 15], "#glass", faces=("east", "west")),
         box([1, 15.6, 0], [X - 1, 16, 1], "#carbon"), box([1, 15.6, 15], [X - 1, 16, 16], "#carbon"),
         box([0, 15.6, 1], [1, 16, 15], "#carbon"), box([X - 1, 15.6, 1], [X, 16, 15], "#carbon"),
         box([1, 15.7, 1], [X - 1, 15.9, 15], "#glass", faces=("up", "down"))]
    for x, z in ((0.5, 0.5), (X - 0.5, 0.5), (X - 0.5, 15.5), (0.5, 15.5)):
        L.bar((x, 7.2, z), (x, 15.6, z), 0.9)
    L.gap()
    L.bar((0, 4, 0.3), (X, 4, 0.3), 0.5)  # front light bar across the cabinet
    L.underglow(0, 0, X, 16)
    return W, 1, 1, e, L


def rgb_glass_cube():
    return glass_case(2)


def rgb_display_case():
    return glass_case(3)


def rgb_pack_shelf():  # PACK_SHELF: packs at y 2.42/1.47/0.52, z 0.42
    L = Leds()
    e = [box([0, 0, 14], [48, 48, 16], {"*": "#carbon", "north": "#peg"}),
         box([0, 0, 1], [1, 48, 14], "#carbon"), box([47, 0, 1], [48, 48, 14], "#carbon"),
         box([1, 0, 1], [47, 0.8, 14], "#carbon")]
    for top in (1.8, 17.0, 32.2):
        e.append(box([1, top - 1, 2], [47, top, 14], "#panel"))
    e.append(box([0, 45.5, 0.5], [48, 48, 14], {"*": "#carbon", "north": "#grill"}))
    for top in (1.8, 17.0, 32.2):
        L.bar((1, top - 0.6, 1.6), (47, top - 0.6, 1.6), 0.7)
        L.gap(0.2)
    L.bar((0.5, 0, 0.7), (0.5, 45.5, 0.7), 0.6)
    L.bar((0.5, 45.5, 0.25), (47.5, 45.5, 0.25), 0.5)
    L.bar((47.5, 45.5, 0.7), (47.5, 0, 0.7), 0.6)
    L.underglow(0, 0, 48, 16)
    return 3, 3, 1, e, L


def rgb_showcase():  # SHOWCASE: cards at y 2.30/1.45/0.60, z 0.5
    L = Leds()
    e = [box([0.5, 0, 0.5], [15.5, 3, 15.5], {"*": "#carbon", "north": "#grill"}),
         box([1.5, 3, 1.5], [14.5, 3.8, 14.5], "#velvet"),
         box([1.5, 16.6, 1.5], [14.5, 17.4, 14.5], "#chrome"),
         box([1.5, 30.2, 1.5], [14.5, 31, 14.5], "#chrome"),
         box([1, 3, 0.6], [15, 44, 1], "#glass", faces=("north", "south")),
         box([1, 3, 15], [15, 44, 15.4], "#glass", faces=("north", "south")),
         box([0.6, 3, 1], [1, 44, 15], "#glass", faces=("east", "west")),
         box([15, 3, 1], [15.4, 44, 15], "#glass", faces=("east", "west")),
         box([0.5, 44, 0.5], [15.5, 48, 15.5], {"*": "#carbon", "up": "#panel"})]
    for x, z in ((0.8, 0.8), (15.2, 0.8), (15.2, 15.2), (0.8, 15.2)):
        L.bar((x, 3, z), (x, 44, z), 0.9)
    L.gap()
    L.rect_y(0.25, 0.25, 15.75, 15.75, 46, 0.5)
    L.gap()
    L.bar((1.5, 17, 1.25), (14.5, 17, 1.25), 0.5)
    L.bar((14.5, 30.6, 1.25), (1.5, 30.6, 1.25), 0.5)
    L.gap()
    L.rect_y(0.25, 0.25, 15.75, 15.75, 2.5, 0.5)
    L.underglow(0.5, 0.5, 15.5, 15.5)
    return 1, 3, 1, e, L


def rgb_box_shelf():  # BOX_SHELF: boxes on y 0.08 / 1.06, z 0.5
    L = Leds()
    e = [box([0, 0, 0.5], [1, 32, 16], "#carbon"), box([47, 0, 0.5], [48, 32, 16], "#carbon"),
         box([15.5, 0, 0.5], [16.5, 30.5, 16], "#carbon"), box([31.5, 0, 0.5], [32.5, 30.5, 16], "#carbon"),
         box([1, 0, 15], [47, 30.5, 16], {"*": "#carbon", "north": "#peg"}),
         box([1, 0, 0.5], [47, 1.3, 15], "#panel"),
         box([1, 15.8, 0.5], [47, 17, 15], "#panel"),
         box([0, 30.5, 0.6], [48, 32, 16], "#carbon")]
    L.bar((1, 16.4, 0.25), (47, 16.4, 0.25), 0.5)
    L.gap(0.2)
    L.bar((0, 31.25, 0.3), (48, 31.25, 0.3), 0.5)
    L.gap(0.2)
    for x in (0.5, 16, 32, 47.5):
        L.bar((x, 0, 0.25), (x, 30.5, 0.25), 0.5)
    L.underglow(0, 0.5, 48, 16)
    return 3, 2, 1, e, L


def rgb_gondola():  # GONDOLA: both sides, rows at y 1.42 / 0.50 (z 0.27 and 0.73)
    L = Leds()
    e = [box([0, 0, 0.5], [48, 1, 15.5], {"*": "#base", "north": "#grill", "south": "#grill"}),
         box([0, 1, 7], [48, 29.5, 9], {"*": "#carbon", "north": "#peg", "south": "#peg"}),
         box([0, 0, 6.8], [0.8, 32, 9.2], "#carbon"), box([47.2, 0, 6.8], [48, 32, 9.2], "#carbon"),
         box([0.8, 1, 0.5], [47.2, 1.7, 7], "#panel"),
         box([0.8, 15.8, 0.8], [47.2, 16.4, 7], "#panel"),
         box([0.8, 1, 9], [47.2, 1.7, 15.5], "#panel"),
         box([0.8, 15.8, 9], [47.2, 16.4, 15.2], "#panel"),
         box([0.5, 29.5, 6.4], [47.5, 32, 9.6], "#carbon")]
    for z, kick in ((0.4, 0.25), (15.6, 15.75)):
        L.bar((0.8, 16.1, z), (47.2, 16.1, z), 0.7)
        L.gap(0.2)
        L.bar((0, 0.5, kick), (48, 0.5, kick), 0.5)
        L.gap(0.2)
    L.bar((0.5, 30.75, 6.15), (47.5, 30.75, 6.15), 0.5)
    L.bar((47.5, 30.75, 9.85), (0.5, 30.75, 9.85), 0.5)
    L.underglow(0, 0.5, 48, 15.5)
    return 3, 2, 1, e, L


def rgb_slab_tower():  # SLAB_TOWER: 2 x 3 slabs at x 0.5/1.5, y 2.30/1.40/0.50, z 0.5
    L = Leds()
    e = [box([0, 0, 0.5], [32, 2.4, 15.5], {"*": "#carbon", "north": "#grill"}),
         box([0.5, 2.4, 14], [31.5, 45.5, 15.5], "#velvet"),
         box([0, 2.4, 0.5], [1, 45.5, 15.5], "#carbon"), box([31, 2.4, 0.5], [32, 45.5, 15.5], "#carbon"),
         box([15.6, 2.4, 6], [16.4, 45.5, 14], "#chrome"),
         box([1, 16, 1], [31, 16.8, 14], "#panel"),
         box([1, 30.4, 1], [31, 31.2, 14], "#panel"),
         box([0, 45.5, 0.5], [32, 48, 15.5], {"*": "#carbon", "north": "#grill"})]
    L.bar((0, 1.2, 0.25), (32, 1.2, 0.25), 0.5)
    L.gap(0.2)
    L.bar((0.5, 2.4, 0.25), (0.5, 45.5, 0.25), 0.5)
    L.bar((0, 46.75, 0.25), (32, 46.75, 0.25), 0.5)
    L.bar((31.5, 45.5, 0.25), (31.5, 2.4, 0.25), 0.5)
    L.gap(0.2)
    L.bar((1, 16.4, 0.75), (31, 16.4, 0.75), 0.6)
    L.bar((31, 30.8, 0.75), (1, 30.8, 0.75), 0.6)
    L.underglow(0, 0.5, 32, 15.5)
    return 2, 3, 1, e, L


def rgb_box_stand():  # BOX_STAND: 2 x 3 booster boxes, bases at y 0.08 / 1.06 / 2.04, z 0.5
    L = Leds()
    e = [box([0, 0, 0.5], [1, 46, 16], "#carbon"), box([31, 0, 0.5], [32, 46, 16], "#carbon"),
         box([15.5, 0, 0.5], [16.5, 46, 16], "#carbon"),
         box([1, 0, 15], [31, 46, 16], {"*": "#carbon", "north": "#peg"}),
         box([1, 0, 0.5], [31, 1.3, 15], "#panel"),
         box([1, 15.8, 0.5], [31, 17, 15], "#panel"),
         box([1, 31.5, 0.5], [31, 32.6, 15], "#panel"),
         box([0, 46, 0.6], [32, 48, 16], {"*": "#carbon", "north": "#grill"})]
    for y in (0.65, 16.4, 32.05):
        L.bar((1, y, 0.25), (31, y, 0.25), 0.5)
        L.gap(0.2)
    for x in (0.5, 16, 31.5):
        L.bar((x, 0, 0.25), (x, 46, 0.25), 0.5)
    L.bar((0, 47, 0.3), (32, 47, 0.3), 0.5)
    L.underglow(0, 0.5, 32, 16)
    return 2, 3, 1, e, L


def rgb_card_wall():  # CARD_WALL: 4 x 3 cards at z 0.66
    L = Leds()
    e = [box([1.5, 1.5, 12], [46.5, 46.5, 16], "#velvet"),
         box([0, 0, 11], [48, 1.5, 16], "#carbon"),
         box([0, 46.5, 11], [48, 48, 16], "#carbon"),
         box([0, 1.5, 11], [1.5, 46.5, 16], "#carbon"), box([46.5, 1.5, 11], [48, 46.5, 16], "#carbon")]
    for top in (2.5, 17.7, 32.9):
        e.append(box([2, top - 0.6, 9.5], [46, top, 12], "#chrome"))
    L.rect_z(0.75, 0.75, 47.25, 47.25, 10.75, 0.5)
    L.gap()
    for top in (2.5, 17.7, 32.9):
        L.bar((2, top - 0.3, 9.25), (46, top - 0.3, 9.25), 0.5)
        L.gap(0.2)
    L.backglow(-4, -4, 52, 52)
    return 3, 3, 1, e, L


def rgb_grand_wall():  # GRAND_WALL: 5 x 3 cards at z 0.66 (y 2.36 / 1.42 / 0.48)
    L = Leds()
    e = [box([1.5, 1.5, 12], [46.5, 46.5, 16], "#velvet"),
         box([0, 0, 11], [48, 1.5, 16], "#carbon"),
         box([0, 46.5, 11], [48, 48, 16], "#carbon"),
         box([0, 1.5, 11], [1.5, 46.5, 16], "#carbon"), box([46.5, 1.5, 11], [48, 46.5, 16], "#carbon")]
    for x in (11.1, 20.5, 27.5, 36.9):  # thin chrome column dividers behind the cards
        e.append(box([x - 0.3, 1.5, 11.6], [x + 0.3, 46.5, 12], "#chrome"))
    L.rect_z(0.75, 0.75, 47.25, 47.25, 10.75, 0.5)
    L.gap()
    L.rect_z(2.2, 2.2, 45.8, 45.8, 11.7, 0.5)
    L.backglow(-5, -5, 53, 53)
    return 3, 3, 1, e, L


def rgb_multi_shelf():  # MULTI_SHELF: 5 x 2 cards at y 1.47 / 0.53, z 0.5, tilt 8
    L = Leds()
    e = [box([0, 0, 1], [48, 2.5, 15], {"*": "#carbon", "north": "#grill"}),
         box([0, 2.5, 14], [48, 30.5, 16], {"*": "#carbon", "north": "#velvet"}),
         box([0, 2.5, 1], [1, 30.5, 14], "#carbon"), box([47, 2.5, 1], [48, 30.5, 14], "#carbon"),
         box([1, 16.85, 1], [47, 17.55, 14], "#panel"),
         box([0, 30.5, 12], [48, 32, 16], "#carbon")]
    L.bar((0, 1.9, 0.75), (48, 1.9, 0.75), 0.5)
    L.gap(0.2)
    L.bar((0.5, 2.5, 0.75), (0.5, 30.5, 0.75), 0.5)
    L.bar((0, 31.25, 11.75), (48, 31.25, 11.75), 0.5)
    L.bar((47.5, 30.5, 0.75), (47.5, 2.5, 0.75), 0.5)
    L.gap(0.2)
    L.bar((1, 17.2, 0.75), (47, 17.2, 0.75), 0.5)
    L.underglow(0, 1, 48, 15)
    return 3, 2, 1, e, L


def rgb_neon_poster():  # BIG_FRAME: one huge card at (1.0, 0.92, z 0.84)
    L = Leds()
    e = [box([1, 1, 15], [31, 31, 16], "#velvet"),
         box([0, 0, 13.5], [32, 2, 16], "#carbon"), box([0, 30, 13.5], [32, 32, 16], "#carbon"),
         box([0, 2, 13.5], [2, 30, 16], "#carbon"), box([30, 2, 13.5], [32, 30, 16], "#carbon")]
    L.rect_z(1, 1, 31, 31, 13.1, 0.8)
    L.gap()
    L.rect_z(3, 3, 29, 29, 14.6, 0.6)
    L.backglow(-4, -4, 36, 36)
    return 2, 2, 1, e, L


def pedestal_body(premium):
    e = [box([2, 0, 2], [14, 2, 14], {"*": "#carbon", "up": "#panel"}),
         box([2.5, 2, 2.5], [13.5, 2.6, 13.5], "#gold" if premium else "#chrome"),
         box([4, 2.6, 4], [12, 13, 12], {"*": "#grill" if not premium else "#chrome", "up": "#panel", "down": "#panel"}),
         box([3, 13, 3], [13, 13.6, 13], "#gold" if premium else "#chrome"),
         box([1, 13.6, 1], [15, 16, 15], {"*": "#carbon", "up": "#panel"}),
         box([3, 16, 3], [13, 16.4, 13], "#velvet"),
         box([0.9, 16, 0.5], [15.1, 44, 0.9], "#glass", faces=("north", "south")),
         box([0.9, 16, 15.1], [15.1, 44, 15.5], "#glass", faces=("north", "south")),
         box([0.5, 16, 0.9], [0.9, 44, 15.1], "#glass", faces=("east", "west")),
         box([15.1, 16, 0.9], [15.5, 44, 15.1], "#glass", faces=("east", "west")),
         box([0.2, 44, 0.2], [15.8, 46, 15.8], {"*": "#carbon", "up": "#panel"}),
         box([0, 46, 0], [16, 46.6, 16], "#gold" if premium else "#chrome"),
         box([5, 46.6, 5], [11, 48, 11], "#gold" if premium else "#carbon")]
    return e


def rgb_pedestal():  # PEDESTAL: one big product spinning at y 1.82
    L = Leds()
    e = pedestal_body(False)
    for x, z in ((0.7, 0.7), (15.3, 0.7), (15.3, 15.3), (0.7, 15.3)):
        L.bar((x, 16, z), (x, 44, z), 1.0)
    L.gap()
    L.rect_y(0.75, 0.75, 15.25, 15.25, 15.6, 0.5)
    L.gap()
    L.rect_y(2.75, 2.75, 13.25, 13.25, 13.3, 0.5)
    L.gap()
    L.rect_y(1.75, 1.75, 14.25, 14.25, 1.0, 0.5)
    L.underglow(2, 2, 14, 14)
    return 1, 3, 1, e, L


def rgb_prism_pedestal():  # PEDESTAL (premium): more rings and a crown
    L = Leds()
    e = pedestal_body(True)
    e.append(box([1, 0, 1], [15, 0.6, 15], "#chrome"))
    for x, z in ((0.7, 0.7), (15.3, 0.7), (15.3, 15.3), (0.7, 15.3)):
        L.bar((x, 16, z), (x, 44, z), 1.0)
    L.gap()
    for y in (23, 30, 37):
        L.rect_y(0.25, 0.25, 15.75, 15.75, y, 0.5)
        L.gap(0.2)
    L.rect_y(0.75, 0.75, 15.25, 15.25, 15.6, 0.5)
    L.gap()
    L.rect_y(0.1, 0.1, 15.9, 15.9, 45, 0.5)
    L.gap()
    L.ring(8, 16.7, 8, 4.5, 0.7, 20)
    L.gap()
    L.rect_y(2.75, 2.75, 13.25, 13.25, 13.3, 0.5)
    L.rect_y(1.75, 1.75, 14.25, 14.25, 1.0, 0.5)
    L.rect_y(0.75, 0.75, 15.25, 15.25, 0.3, 0.4)
    L.underglow(1, 1, 15, 15)
    return 1, 3, 1, e, L


FIXTURES = {"rgb_card_frame": rgb_card_frame, "rgb_card_ledge": rgb_card_ledge, "rgb_counter": rgb_counter,
            "rgb_card_stand": rgb_card_stand, "rgb_turntable": rgb_turntable, "rgb_wall_trio": rgb_wall_trio,
            "rgb_glass_cube": rgb_glass_cube, "rgb_pack_shelf": rgb_pack_shelf, "rgb_display_case": rgb_display_case,
            "rgb_showcase": rgb_showcase, "rgb_box_shelf": rgb_box_shelf, "rgb_gondola": rgb_gondola,
            "rgb_slab_tower": rgb_slab_tower, "rgb_box_stand": rgb_box_stand, "rgb_card_wall": rgb_card_wall,
            "rgb_multi_shelf": rgb_multi_shelf, "rgb_neon_poster": rgb_neon_poster, "rgb_grand_wall": rgb_grand_wall,
            "rgb_pedestal": rgb_pedestal, "rgb_prism_pedestal": rgb_prism_pedestal}


def used_textures(elements):
    keys = set()
    for el in elements:
        for fv in el["faces"].values():
            keys.add(fv["texture"].lstrip("#"))
    keys.add("particle")
    return {k: TEXTURES[k] for k in keys}


def main():
    for name, fn in TEX.items():
        png(A / f"textures/block/{name}.png", fn())
    count = 0
    for name, fn in FIXTURES.items():
        W, H, D, els, L = fn()
        els = els + L.channels
        t = used_textures(els)
        parts = slice_model(t, els, W, H, D)
        v = {}
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for p in range(16):
                d = {"model": f"tcgshop:block/{name}_p{p if p in parts else 0}"}
                if y:
                    d["y"] = y
                v[f"facing={f},part={p}"] = d
        for p, m in parts.items():
            write(A / f"models/block/{name}_p{p}.json", m)
        write(A / f"blockstates/{name}.json", {"variants": v})
        ti = dict(t)
        if "led" in ti:
            ti["led"] = "tcgshop:block/rgb_led_rainbow"  # inventory icon: lit LEDs
        write(A / f"models/item/{name}.json", item_of(ti, els, W, H, D))
        write(ROOT / f"data/tcgshop/loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
            {"type": "minecraft:item", "name": f"tcgshop:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"},
            {"condition": "minecraft:block_state_property", "block": f"tcgshop:{name}", "properties": {"part": "0"}}]}]})
        write(A / f"rgb/{name}.json", L.json())
        count += 1
    print("rgb displays generated:", count, "| textures:", len(TEX))


if __name__ == "__main__":
    main()
