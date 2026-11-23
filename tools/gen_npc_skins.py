#!/usr/bin/env python3
"""NPC skin generator (1.0): 64x64 player-format skins with BOTH layers painted.

Writes src/main/resources/assets/tcgshop/textures/entity/npc/:
  <role>_<f|m>_<n>.png   procedural skins per role and sex (f = slim 3px arms, m = wide 4px arms)
  fixed_<n>.png          the 10 fixed unique customers (hand-designed, see FIXED below)
  backpack_<n>.png       32x32 textures of the 3D backpack drawn by TcgNpcRenderer

The outer layer (hat, jacket, sleeves, pants overlay) is used for everything that should stand out from the body:
hair volume, caps, glasses, hoods, jackets, aprons, skirts, coat tails and chunky sneakers. Everything is original
pixel art made from code: generic shapes and colors only, no official art.

The Java side (client/render/NpcSkins) finds the files by probing <role>_<f|m>_0.png, _1.png, ... so the counts
below can change freely. The backpack rule must match NpcSkins.backpack(): customers with n % 5 == 2 and scalpers
with n % 2 == 0 carry one, color index = (n * 3 + (male ? 1 : 0)) % 8.

Usage: python tools/gen_npc_skins.py [--preview <png>]   (the preview is a contact sheet of front/back views)
"""
import random
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/tcgshop/textures/entity/npc"

T = (0, 0, 0, 0)

# Skins per role and sex. Customers get the most variety.
COUNTS = {
    "customer": 64,
    "cashier": 12,
    "guard": 12,
    "wholesaler": 8,
    "beggar": 8,
    "black_market": 8,
    "furniture_seller": 8,
    "trader": 10,
    "scalper": 10,
}
BACKPACK_COLORS = 8


# ------------------------------------------------------------------ colors

def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, amt):
    if c[3] == 0:
        return c
    return (clamp(c[0] + amt), clamp(c[1] + amt), clamp(c[2] + amt), c[3])


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)


def alpha(c, a):
    return (c[0], c[1], c[2], a)


def lum(c):
    return c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11


def contrast(c):
    return hexc("1E1E28") if lum(c) > 140 else hexc("F4F4F4")


SKIN_TONES = [hexc(h) for h in ("FBE0CA", "F6D3B3", "EFC4A0", "E3B088", "D49E74", "C08A5E", "A87250", "8E5E3E", "6E4529", "553420")]
HAIR_NATURAL = [hexc(h) for h in ("1B1410", "2A1C12", "3D2716", "5A3A1E", "7A4A22", "9A6232", "B9874A", "D9B26A", "E8CF8E", "8A2E1A", "B4502A", "C9C9C9", "EDEDED", "4A4040")]
HAIR_DYED = [hexc(h) for h in ("3A5FC8", "E070B0", "5AC07A", "8A4AD0", "E04848", "40C8D0", "F0A040", "B8A0F0")]
EYES = [hexc(h) for h in ("3A2414", "4A2E1A", "2E6FC8", "3E8E4A", "6A5A3A", "202020", "5A7A9A")]
# clothing palette: saturated, pastel and neutral
CLOTH = [hexc(h) for h in (
    "E33B3B", "3A6FD8", "F2C14E", "4CAF50", "8E44AD", "FF8A3D", "26A69A", "37474F", "F5F5F5", "EC407A", "5C6BC0",
    "8D6E63", "1E1E24", "B0BEC5", "F48FB1", "90CAF9", "A5D6A7", "FFE082", "CE93D8", "FFAB91", "80CBC4", "6D4C41",
    "7B1FA2", "C62828", "1565C0", "2E7D32", "F9A825", "AD1457", "455A64", "D7CCC8", "3E2723", "263238")]
DENIM = [hexc(h) for h in ("2B4A7A", "35558A", "1F3557", "4A6FA5", "5B7FB5", "26324A")]
PANTS = [hexc(h) for h in ("2B3A5A", "3A3A40", "4A3A2A", "1F2A3A", "5A5A62", "6B4A2A", "22301F", "C9B48A", "8A7A5A", "1E1E22", "6A2A2A")]
SHOES = [hexc(h) for h in ("1E1E22", "F2F2F2", "6B3E20", "8A1E1E", "2A4A8A", "3A3A3A", "C04A8A", "E0B040")]
LIPS = [hexc(h) for h in ("C0505A", "D06070", "A03040", "B86A6A", "8E3A5A")]
WHITE = hexc("F6F6F6")
BLACK = hexc("16161C")
GOLD = hexc("F2C14E")
SILVER = hexc("C9D2DE")


# ------------------------------------------------------------------ png

def png(path, px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(data)


# ------------------------------------------------------------------ skin canvas

SIDES = ("right", "front", "left", "back")


class Skin:
    """64x64 player skin. Parts: head, body, rarm, larm, rleg, lleg; layers: base, over."""

    def __init__(self, slim, rng):
        self.px = [[T] * 64 for _ in range(64)]
        self.slim = slim
        self.aw = 3 if slim else 4
        self.rng = rng
        aw = self.aw
        self.parts = {
            "head": ((0, 0), (32, 0), (8, 8, 8)),
            "body": ((16, 16), (16, 32), (8, 12, 4)),
            "rarm": ((40, 16), (40, 32), (aw, 12, 4)),
            "larm": ((32, 48), (48, 48), (aw, 12, 4)),
            "rleg": ((0, 16), (0, 32), (4, 12, 4)),
            "lleg": ((16, 48), (0, 48), (4, 12, 4)),
        }

    def rect(self, part, layer, face):
        base, over, (w, h, d) = self.parts[part]
        u, v = base if layer == "base" else over
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + 2 * d + w, v + d, w, h),
        }[face]

    def jit(self, c, noise):
        if noise <= 0 or c[3] == 0:
            return c
        return shade(c, self.rng.randint(-noise, noise))

    def put(self, part, layer, face, x, y, c, noise=0):
        fx, fy, fw, fh = self.rect(part, layer, face)
        if 0 <= x < fw and 0 <= y < fh:
            self.px[fy + y][fx + x] = self.jit(c, noise)

    def get(self, part, layer, face, x, y):
        fx, fy, fw, fh = self.rect(part, layer, face)
        if 0 <= x < fw and 0 <= y < fh:
            return self.px[fy + y][fx + x]
        return T

    def size(self, part, face):
        return self.rect(part, "base", face)[2:]

    def fill(self, part, layer, face, c, noise=3, y0=0, y1=99, x0=0, x1=99):
        fw, fh = self.size(part, face)
        for y in range(max(0, y0), min(fh - 1, y1) + 1):
            for x in range(max(0, x0), min(fw - 1, x1) + 1):
                self.put(part, layer, face, x, y, c, noise)

    def rows(self, part, layer, y0, y1, c, noise=3, faces=SIDES):
        for f in faces:
            self.fill(part, layer, f, c, noise, y0=y0, y1=y1)

    def cube(self, part, layer, c, noise=3):
        self.fill(part, layer, "top", shade(c, 8), noise)
        self.fill(part, layer, "bottom", shade(c, -14), noise)
        self.fill(part, layer, "right", shade(c, -5), noise)
        self.fill(part, layer, "front", c, noise)
        self.fill(part, layer, "left", shade(c, -5), noise)
        self.fill(part, layer, "back", shade(c, -8), noise)

    def clear(self, part, layer, face, y0=0, y1=99, x0=0, x1=99):
        fw, fh = self.size(part, face)
        for y in range(max(0, y0), min(fh - 1, y1) + 1):
            for x in range(max(0, x0), min(fw - 1, x1) + 1):
                fx, fy, _, _ = self.rect(part, layer, face)
                self.px[fy + y][fx + x] = T

    def save(self, path):
        png(path, self.px)


ARMS = ("rarm", "larm")
LEGS = ("rleg", "lleg")


# ------------------------------------------------------------------ face and hair

def paint_face(s, look):
    skin, eye, hair = look["skin"], look["eye"], look["hair"]
    s.cube("head", "base", skin, noise=2)
    s.fill("head", "base", "front", shade(skin, -6), noise=1, y0=7, y1=7)
    for f, ex in (("right", 3), ("left", 4)):  # ears
        s.put("head", "base", f, ex, 4, shade(skin, -16))
        s.put("head", "base", f, ex, 5, shade(skin, -10))
    white = hexc("F4F4F4")
    s.put("head", "base", "front", 1, 4, white)
    s.put("head", "base", "front", 2, 4, eye)
    s.put("head", "base", "front", 5, 4, eye)
    s.put("head", "base", "front", 6, 4, white)
    brow = shade(hair, -30) if look["hair_style"] != "bald" else shade(skin, -40)
    if lum(brow) > 170:
        brow = shade(brow, -60)
    if look["female"]:
        s.put("head", "base", "front", 1, 3, shade(brow, -20))  # lashes at the outer corner
        s.put("head", "base", "front", 2, 3, brow)
        s.put("head", "base", "front", 5, 3, brow)
        s.put("head", "base", "front", 6, 3, shade(brow, -20))
    else:
        for x in (1, 2, 5, 6):
            s.put("head", "base", "front", x, 3, brow)
    s.put("head", "base", "front", 3, 5, shade(skin, -16))
    s.put("head", "base", "front", 4, 5, shade(skin, -9))
    mouth = look.get("lips") or shade(skin, -55)
    s.put("head", "base", "front", 3, 6, mouth)
    s.put("head", "base", "front", 4, 6, mouth)
    if look.get("smile"):
        s.put("head", "base", "front", 2, 6, shade(skin, -35))
        s.put("head", "base", "front", 5, 6, shade(skin, -35))
    if look.get("blush"):
        b = mix(skin, hexc("F06A7A"), 0.35)
        s.put("head", "base", "front", 1, 5, b)
        s.put("head", "base", "front", 6, 5, b)
    if look.get("freckles"):
        f = shade(skin, -28)
        s.put("head", "base", "front", 1, 5, f)
        s.put("head", "base", "front", 6, 5, f)
    fh = look.get("facial")
    beard = shade(hair, -8)
    if fh == "stubble":
        st = mix(skin, hair, 0.35)
        for x in range(1, 7):
            s.put("head", "base", "front", x, 7, st)
        s.put("head", "base", "front", 2, 6, st)
        s.put("head", "base", "front", 5, 6, st)
    elif fh == "mustache":
        s.put("head", "base", "front", 2, 5, beard)
        s.put("head", "base", "front", 5, 5, beard)
        s.fill("head", "base", "front", beard, 1, y0=5, y1=5, x0=3, x1=4)
        s.put("head", "base", "front", 3, 6, mouth)
    elif fh == "beard":
        s.fill("head", "base", "front", beard, 2, y0=6, y1=7, x0=0, x1=7)
        s.fill("head", "base", "front", beard, 2, y0=5, y1=5, x0=2, x1=5)
        s.put("head", "base", "front", 3, 6, mouth)
        s.put("head", "base", "front", 4, 6, mouth)
        for f, xs in (("right", range(5, 8)), ("left", range(0, 3))):
            for x in xs:
                s.put("head", "base", f, x, 6, beard, 2)
                s.put("head", "base", f, x, 7, beard, 2)
        s.fill("head", "base", "bottom", beard, 2)
        # beard volume on the hat layer: chin row
        s.fill("head", "over", "front", shade(beard, 6), 2, y0=7, y1=7, x0=1, x1=6)
    elif fh == "goatee":
        s.fill("head", "base", "front", beard, 1, y0=7, y1=7, x0=3, x1=4)
        s.put("head", "base", "front", 3, 5, beard)
        s.put("head", "base", "front", 4, 5, beard)


def hair_base_common(s, h, back_rows, side_rows):
    s.fill("head", "base", "top", h, 4)
    s.fill("head", "base", "back", h, 4, y0=0, y1=back_rows)
    for f in ("right", "left"):
        s.fill("head", "base", f, h, 4, y0=0, y1=side_rows)
    # the half of the side away from the face goes a bit lower
    s.fill("head", "base", "right", h, 4, y0=0, y1=min(7, side_rows + 2), x0=0, x1=3)
    s.fill("head", "base", "left", h, 4, y0=0, y1=min(7, side_rows + 2), x0=4, x1=7)


def hair_over_common(s, h, back_rows, side_rows):
    hl = shade(h, 14)
    for y in range(8):
        for x in range(8):
            s.put("head", "over", "top", x, y, hl if (x * 3 + y * 5) % 7 == 0 else h, 4)
    s.fill("head", "over", "back", h, 4, y0=0, y1=back_rows)
    s.fill("head", "over", "right", h, 4, y0=0, y1=side_rows, x0=0, x1=5)
    s.fill("head", "over", "left", h, 4, y0=0, y1=side_rows, x0=2, x1=7)


def paint_hair(s, look):
    st, h = look["hair_style"], look["hair"]
    hd, hl = shade(h, -22), shade(h, 18)
    F = "front"
    if st == "bald":
        s.fill("head", "base", "back", mix(look["skin"], h, 0.6), 2, y0=3, y1=5)
        for f, x0, x1 in (("right", 0, 4), ("left", 3, 7)):
            s.fill("head", "base", f, mix(look["skin"], h, 0.6), 2, y0=3, y1=4, x0=x0, x1=x1)
        return
    if st == "buzz":
        b = mix(look["skin"], h, 0.7)
        hair_base_common(s, b, 4, 1)
        s.fill("head", "base", F, b, 2, y0=0, y1=0)
        return
    if st == "mohawk":
        b = mix(look["skin"], h, 0.45)
        hair_base_common(s, b, 4, 1)
        s.fill("head", "base", "top", h, 3, x0=3, x1=4)
        s.fill("head", "over", "top", hl, 4, x0=3, x1=4)
        s.fill("head", "over", "back", h, 4, x0=3, x1=4, y0=0, y1=3)
        s.fill("head", "over", F, h, 4, x0=3, x1=4, y0=0, y1=0)
        return
    if st in ("short", "side_part", "spiky", "curtains", "messy", "pixie", "slick"):
        hair_base_common(s, h, 5, 2)
        s.fill("head", "base", F, h, 3, y0=0, y1=0)
        s.put("head", "base", F, 0, 1, h)
        s.put("head", "base", F, 7, 1, h)
        hair_over_common(s, h, 3, 1)
        if st == "short":
            s.fill("head", "over", F, h, 3, y0=0, y1=0, x0=1, x1=6)
        elif st == "side_part":
            s.fill("head", "over", F, h, 3, y0=0, y1=0)
            s.fill("head", "over", F, hl, 3, y0=1, y1=1, x0=0, x1=3)
            s.put("head", "over", F, 0, 2, h)
            s.fill("head", "base", F, h, 3, y0=1, y1=1, x0=0, x1=4)
        elif st == "spiky":
            for x in range(8):
                s.put("head", "over", F, x, 0, hl if x % 2 else h)
                if x % 2 == 0:
                    s.put("head", "over", F, x, 1, h)
            for f in ("right", "left"):
                for x in range(8):
                    if x % 2:
                        s.put("head", "over", f, x, 2, h)
        elif st == "curtains":
            s.fill("head", "over", F, h, 3, y0=0, y1=0)
            for x in (0, 1, 2, 5, 6, 7):
                s.put("head", "over", F, x, 1, h)
            for x in (0, 1, 6, 7):
                s.put("head", "over", F, x, 2, hd)
        elif st == "messy":
            for x in range(8):
                s.put("head", "over", F, x, 0, h, 6)
                if s.rng.random() < 0.6:
                    s.put("head", "over", F, x, 1, hd if x % 3 else h)
            for f in ("right", "left", "back"):
                fw, _ = s.size("head", f)
                for x in range(fw):
                    if s.rng.random() < 0.5:
                        s.put("head", "over", f, x, 4, h, 6)
        elif st == "pixie":
            s.fill("head", "over", F, h, 3, y0=0, y1=0)
            s.fill("head", "over", F, hl, 3, y0=1, y1=1, x0=0, x1=4)
            s.fill("head", "over", F, h, 3, y0=2, y1=2, x0=0, x1=1)
            s.fill("head", "base", F, h, 3, y0=1, y1=1, x0=0, x1=5)
        elif st == "slick":
            s.fill("head", "over", F, hl, 2, y0=0, y1=0)
            for y in range(8):
                s.put("head", "over", "top", 2, y, shade(h, 30))
        return
    # long and medium styles
    if st in ("long", "wavy", "bangs_long"):
        hair_base_common(s, h, 7, 7)
        s.fill("head", "base", F, h, 3, y0=0, y1=1)
        for x in (0, 7):
            s.fill("head", "base", F, h, 3, y0=2, y1=5, x0=x, x1=x)
        hair_over_common(s, h, 7, 7)
        s.fill("head", "over", F, h, 3, y0=0, y1=0)
        for x in (0, 7):
            s.fill("head", "over", F, hd, 3, y0=1, y1=7, x0=x, x1=x)
        if st == "bangs_long":
            s.fill("head", "over", F, h, 3, y0=1, y1=1)
            s.fill("head", "base", F, h, 3, y0=2, y1=2, x0=1, x1=6)
        else:
            s.fill("head", "over", F, h, 3, y0=1, y1=1, x0=0, x1=3)
        # down to the shoulders and the back
        for f in ("back",):
            s.fill("body", "over", f, h, 4, y0=0, y1=5 if st != "wavy" else 6)
        s.fill("body", "over", "top", h, 3, y0=0, y1=1)
        for f in ("right", "left"):
            s.fill("body", "over", f, hd, 3, y0=0, y1=2, x0=0 if f == "right" else 2, x1=1 if f == "right" else 3)
        if st == "wavy":
            for y in range(0, 7):
                for x in range(8):
                    if (x + y) % 3 == 0:
                        s.put("body", "over", "back", x, y, hl)
                        s.put("head", "over", "back", x, y, hl)
        return
    if st == "bob":
        hair_base_common(s, h, 6, 5)
        s.fill("head", "base", F, h, 3, y0=0, y1=1)
        hair_over_common(s, h, 6, 6)
        s.fill("head", "over", F, h, 3, y0=0, y1=1)
        for x in (0, 7):
            s.fill("head", "over", F, hd, 3, y0=2, y1=6, x0=x, x1=x)
        return
    if st in ("ponytail", "braid", "bun", "twintails", "manbun"):
        hair_base_common(s, h, 7, 3)
        s.fill("head", "base", F, h, 3, y0=0, y1=0)
        s.fill("head", "base", F, h, 3, y0=1, y1=1, x0=0, x1=2)
        hair_over_common(s, h, 3, 2)
        s.fill("head", "over", F, h, 3, y0=0, y1=0)
        s.fill("head", "over", F, hl, 3, y0=1, y1=1, x0=0, x1=1)
        tie = look.get("accent", hexc("E33B3B"))
        if st in ("ponytail", "braid"):
            s.fill("head", "over", "back", tie, 0, y0=3, y1=3, x0=3, x1=4)
            for y in range(4, 8):
                for x in (3, 4):
                    c = (hl if (x + y) % 2 else hd) if st == "braid" else (hl if x == 3 else h)
                    s.put("head", "over", "back", x, y, c)
            for y in range(0, 5 if st == "ponytail" else 6):
                for x in (3, 4):
                    c = (hl if (x + y) % 2 else hd) if st == "braid" else (hl if x == 3 else h)
                    s.put("body", "over", "back", x, y, c)
        elif st in ("bun", "manbun"):
            for y in range(0, 4):
                for x in range(2, 6):
                    s.put("head", "over", "top", x, y, hl if (x + y) % 2 else h)
            s.fill("head", "over", "back", hl, 2, y0=0, y1=2 if st == "bun" else 3, x0=2, x1=5)
            s.fill("head", "over", "back", tie, 0, y0=3 if st == "bun" else 4, y1=3 if st == "bun" else 4, x0=3, x1=4)
        elif st == "twintails":
            for f, x0, x1 in (("right", 0, 2), ("left", 5, 7)):
                s.fill("head", "over", f, tie, 0, y0=2, y1=2, x0=x0, x1=x1)
                s.fill("head", "over", f, h, 3, y0=3, y1=7, x0=x0, x1=x1)
            s.fill("body", "over", "right", h, 3, y0=0, y1=4, x0=0, x1=1)
            s.fill("body", "over", "left", h, 3, y0=0, y1=4, x0=2, x1=3)
        return
    if st in ("afro", "curly"):
        rows = 6 if st == "afro" else 4
        hair_base_common(s, h, 7, rows)
        s.fill("head", "base", F, h, 3, y0=0, y1=1)
        for f in ("top", "back", "right", "left", "front"):
            fw, fh = s.size("head", f)
            for y in range(fh):
                for x in range(fw):
                    if f == "front" and (y > 1 or (st == "curly" and y > 0 and 1 < x < 6)):
                        continue
                    if f in ("right", "left") and y > rows:
                        continue
                    if f == "right" and x > 5 and y > 2:
                        continue
                    if f == "left" and x < 2 and y > 2:
                        continue
                    c = hl if (x * 7 + y * 3) % 5 == 0 else (hd if (x + y * 2) % 4 == 0 else h)
                    s.put("head", "over", f, x, y, c, 3)
        return
    raise ValueError(st)


# ------------------------------------------------------------------ clothes

def emblem(s, kind, x0, y0, bg, layer="base"):
    """Small generic emblem (4x4) on the body front."""
    P = lambda x, y, c: s.put("body", layer, "front", x0 + x, y0 + y, c)
    if kind == "ball":
        red = hexc("E33B3B")
        for x, y, c in ((1, 0, red), (2, 0, red), (0, 1, red), (1, 1, red), (2, 1, red), (3, 1, red),
                        (0, 2, BLACK), (1, 2, WHITE), (2, 2, WHITE), (3, 2, BLACK), (1, 3, WHITE), (2, 3, WHITE)):
            P(x, y, c)
    elif kind == "bolt":
        c = hexc("FFD23F")
        for (x, y) in ((2, 0), (1, 1), (2, 1), (1, 2), (2, 2), (0, 3)):
            P(x, y, c)
    elif kind == "flame":
        P(1, 0, hexc("FFC23A")); P(1, 1, hexc("FF7A2A")); P(2, 1, hexc("FF7A2A"))
        P(1, 2, hexc("FF5A1A")); P(2, 2, hexc("FFD23F")); P(1, 3, hexc("FF5A1A")); P(2, 3, hexc("FF5A1A"))
    elif kind == "leaf":
        P(2, 0, hexc("6CC46F")); P(1, 1, hexc("4CAF50")); P(2, 1, hexc("4CAF50"))
        P(1, 2, hexc("4CAF50")); P(2, 2, hexc("2E7A33")); P(0, 3, hexc("2E7A33"))
    elif kind == "drop":
        P(1, 0, hexc("8FD8FF")); P(1, 1, hexc("4FA3E8")); P(2, 1, hexc("4FA3E8"))
        P(1, 2, hexc("2D64B8")); P(2, 2, hexc("4FA3E8"))
    elif kind == "star":
        c = contrast(bg) if lum(bg) < 200 else hexc("F2B01E")
        for (x, y) in ((1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (3, 1), (1, 2), (2, 2), (0, 3), (3, 3)):
            P(x, y, c)
    elif kind == "heart":
        c = hexc("E8405A") if lum(bg) > 90 else hexc("FF8AA0")
        for (x, y) in ((0, 0), (3, 0), (0, 1), (1, 1), (2, 1), (3, 1), (1, 2), (2, 2)):
            P(x, y, c)
        P(1, 0, c); P(2, 0, bg)
    elif kind == "stripe":
        c = contrast(bg)
        for x in range(-2, 6):
            P(x, 1, c)
    elif kind == "number":
        c = contrast(bg)
        for (x, y) in ((1, 0), (2, 0), (2, 1), (1, 2), (1, 3), (2, 3)):
            P(x, y, c)


EMBLEMS = ("ball", "bolt", "flame", "leaf", "drop", "star", "heart", "stripe", "number")


def top_base(s, look, kind, c, c2=None):
    """Shirt on the base body + sleeves on the base arms. kind: tee, long, tank, polo, plaid, blouse, striped, dress_top."""
    skin = look["skin"]
    c2 = c2 or shade(c, -35)
    s.cube("body", "base", c, 3)
    if kind == "plaid":
        for f in ("front", "back", "right", "left", "top"):
            fw, fh = s.size("body", f)
            for y in range(fh):
                for x in range(fw):
                    if x % 3 == 0 and y % 3 == 0:
                        s.put("body", "base", f, x, y, shade(c2, -10))
                    elif x % 3 == 0 or y % 3 == 0:
                        s.put("body", "base", f, x, y, c2, 2)
    elif kind == "striped":
        for f in SIDES:
            fw, fh = s.size("body", f)
            for y in range(fh):
                if (y // 2) % 2:
                    for x in range(fw):
                        s.put("body", "base", f, x, y, c2, 2)
    if kind in ("tee", "striped", "plaid", "dress_top"):
        s.put("body", "base", "front", 3, 0, skin)
        s.put("body", "base", "front", 4, 0, skin)
    if kind == "tank":
        s.fill("body", "base", "top", skin, 1)
        s.fill("body", "base", "top", c, 2, x0=1, x1=1)
        s.fill("body", "base", "top", c, 2, x0=6, x1=6)
        s.fill("body", "base", "front", skin, 1, y0=0, y1=0, x0=2, x1=5)
    if kind == "polo":
        s.fill("body", "base", "front", shade(c, 22), 0, y0=0, y1=0, x0=1, x1=6)
        s.put("body", "base", "front", 3, 0, shade(c, -30))
        s.put("body", "base", "front", 4, 1, shade(c, -30))
        s.put("body", "base", "front", 4, 2, shade(c, -30))
    if kind == "blouse":
        s.fill("body", "base", "front", shade(c, 20), 0, y0=0, y1=0, x0=2, x1=5)
        for y in (2, 4, 6):
            s.put("body", "base", "front", 4, y, shade(c, -25))
    # arms: skin + sleeves
    sleeve_rows = {"tee": 3, "striped": 3, "polo": 3, "dress_top": 2, "tank": -1, "long": 9, "plaid": 9, "blouse": 8}[kind]
    for a in ARMS:
        s.cube(a, "base", skin, 2)
        if sleeve_rows >= 0:
            s.rows(a, "base", 0, sleeve_rows, c, 3)
            s.fill(a, "base", "top", c, 3)
            if kind == "plaid":
                for f in SIDES:
                    fw, _ = s.size(a, f)
                    for y in range(0, sleeve_rows + 1):
                        for x in range(fw):
                            if x % 3 == 0 or y % 3 == 0:
                                s.put(a, "base", f, x, y, c2, 2)
            if kind == "striped":
                for y in range(0, sleeve_rows + 1):
                    if (y // 2) % 2:
                        s.rows(a, "base", y, y, c2, 2)
            if sleeve_rows >= 8:
                s.rows(a, "base", sleeve_rows, sleeve_rows, shade(c, -22), 2)


def bottom(s, look, kind, c, shoes, shoe_kind="sneakers"):
    """Trousers / shorts / skirt on the legs + waistband and shoes. kind: jeans, chinos, shorts, skirt_mini, skirt_knee,
    skirt_long, leggings, joggers, cargo, dress (skirt part of a dress, color c)."""
    skin = look["skin"]
    for l in LEGS:
        s.cube(l, "base", skin, 2)
    if kind in ("jeans", "chinos", "leggings", "joggers", "cargo"):
        for l in LEGS:
            s.cube(l, "base", c, 3)
        if kind == "jeans":
            for l in LEGS:
                s.fill(l, "base", "front", shade(c, 18), 2, x0=1, x1=1, y0=1, y1=9)
                if look.get("ripped"):
                    s.put(l, "base", "front", 2, 5, skin)
                    s.put(l, "base", "front", 1, 6, shade(skin, -10))
        if kind == "joggers":
            for l in LEGS:
                s.rows(l, "base", 9, 9, shade(c, -20), 2)
                s.fill(l, "base", "right" if l == "rleg" else "left", WHITE, 0, x0=1, x1=1, y0=0, y1=8)
        if kind == "cargo":
            for l, f in (("rleg", "right"), ("lleg", "left")):
                s.fill(l, "base", f, shade(c, -18), 2, x0=1, x1=2, y0=4, y1=6)
                s.fill(l, "over", f, shade(c, -8), 2, x0=1, x1=2, y0=4, y1=6)  # pocket with depth
    elif kind == "shorts":
        for l in LEGS:
            s.rows(l, "base", 0, 4, c, 3)
            s.fill(l, "base", "top", c, 3)
            s.rows(l, "over", 4, 4, shade(c, -12), 2)  # rolled hem
    elif kind.startswith("skirt") or kind == "dress":
        tights = look.get("tights")
        if tights:
            for l in LEGS:
                s.cube(l, "base", tights, 2)
        length = {"skirt_mini": 3, "skirt_knee": 6, "skirt_long": 9, "dress": 5}[kind]
        for l in LEGS:
            s.rows(l, "base", 0, min(length, 3), c, 3)
            s.fill(l, "base", "top", c, 3)
            s.rows(l, "over", 0, length, c, 3)
            s.rows(l, "over", length, length, shade(c, -18), 2)
            # inner faces stay open so the skirt does not cut between the legs when walking
            s.clear(l, "over", "left" if l == "rleg" else "right", y0=length - 1)
        s.rows("body", "over", 10, 11, c, 3)
    # socks + shoes (base) and chunky shoes on the pants overlay
    if kind in ("shorts", "skirt_mini", "skirt_knee") and not look.get("tights"):
        for l in LEGS:
            s.rows(l, "base", 9, 9, WHITE if shoe_kind == "sneakers" else skin, 1)
    for l in LEGS:
        s.rows(l, "base", 10, 11, shoes, 2)
        s.fill(l, "base", "bottom", shade(shoes, -20), 1)
        if shoe_kind == "sneakers":
            s.rows(l, "over", 10, 10, shoes, 2)
            s.rows(l, "over", 11, 11, WHITE if lum(shoes) < 200 else hexc("D0D0D0"), 1)
            s.put(l, "over", "front", 1, 10, contrast(shoes))
            s.put(l, "over", "front", 2, 10, contrast(shoes))
        elif shoe_kind == "boots":
            s.rows(l, "base", 8, 11, shoes, 2)
            s.rows(l, "over", 9, 11, shade(shoes, 6), 2)
            s.rows(l, "over", 9, 9, shade(shoes, -18), 1)
        elif shoe_kind == "dress":
            s.rows(l, "base", 11, 11, shade(shoes, 25), 1)
            s.put(l, "base", "front", 1, 10, shade(shoes, 40))
    # waistband / belt
    if kind not in ("dress",) and not kind.startswith("skirt"):
        belt = look.get("belt", shade(c, -25))
        s.rows("body", "base", 11, 11, belt, 2)
        s.put("body", "base", "front", 3, 11, look.get("buckle", SILVER))
        s.put("body", "base", "front", 4, 11, look.get("buckle", SILVER))
    elif kind.startswith("skirt"):
        s.rows("body", "base", 11, 11, c, 3)


def outer(s, look, kind, c, c2=None):
    """Outer garment on the jacket + sleeves overlay. kind: hoodie, hoodie_up, zip, denim, leather, cardigan, sweater,
    varsity, vest, puffer, blazer, coat, trench, labcoat."""
    c2 = c2 or shade(c, -30)
    if kind == "vest":
        s.rows("body", "over", 0, 10, c, 3, faces=("right", "left", "back"))
        s.fill("body", "over", "front", c, 3, x0=0, x1=2, y0=0, y1=10)
        s.fill("body", "over", "front", c, 3, x0=5, x1=7, y0=0, y1=10)
        s.fill("body", "over", "top", c, 3, x0=0, x1=2)
        s.fill("body", "over", "top", c, 3, x0=5, x1=7)
        for x, y in ((1, 3), (6, 3), (1, 7), (6, 7)):
            s.put("body", "over", "front", x, y, shade(c, -25))
        return
    sleeves_to = 9
    long_coat = kind in ("coat", "trench", "labcoat")
    s.rows("body", "over", 0, 11, c, 3)
    s.fill("body", "over", "top", shade(c, 6), 3)
    for a in ARMS:
        s.rows(a, "over", 0, sleeves_to, shade(c, -4), 3)
        s.fill(a, "over", "top", c, 3)
        s.rows(a, "over", sleeves_to, sleeves_to, shade(c, -22), 2)
    F = "front"
    if kind in ("hoodie", "hoodie_up"):
        s.fill("body", "over", F, shade(c, -14), 2, x0=1, x1=6, y0=7, y1=9)
        s.fill("body", "over", F, shade(c, -24), 0, x0=2, x1=5, y0=7, y1=7)
        if kind == "hoodie":
            s.fill("body", "over", F, WHITE, 0, x0=3, x1=3, y0=1, y1=3)
            s.fill("body", "over", F, WHITE, 0, x0=4, x1=4, y0=1, y1=2)
            s.fill("body", "over", "back", shade(c, -18), 2, y0=0, y1=2)  # hood down
            s.fill("body", "over", "top", shade(c, -10), 2)
        s.rows("body", "over", 11, 11, shade(c, -18), 2)
        if look.get("hood_print"):
            emblem(s, look["hood_print"], 2, 3, c, layer="over")
    elif kind in ("zip", "denim", "leather", "cardigan", "blazer", "coat", "trench", "labcoat", "varsity", "puffer"):
        open_cols = {"cardigan": (3, 4), "blazer": (3, 4), "coat": (3, 4), "trench": (4, 4), "labcoat": (3, 4),
                     "zip": (2, 5), "denim": (2, 5), "leather": (2, 5), "varsity": (2, 5), "puffer": (9, 9)}[kind]
        open_rows = {"blazer": 5, "trench": 3, "puffer": -1}.get(kind, 11)
        if open_cols[0] <= 7:
            s.clear("body", "over", F, x0=open_cols[0], x1=open_cols[1], y0=0, y1=open_rows)
        if kind in ("zip", "leather"):
            for y in range(0, 12):
                s.put("body", "over", F, 1, y, SILVER if y % 3 == 0 else shade(c, -12))
        if kind == "denim":
            for x in (0, 1, 6, 7):
                s.put("body", "over", F, x, 3, shade(c, -20))
            s.put("body", "over", F, 1, 2, SILVER); s.put("body", "over", F, 6, 2, SILVER)
            s.rows("body", "over", 11, 11, shade(c, -14), 2)
        if kind == "cardigan":
            for y in (2, 5, 8):
                s.put("body", "over", F, 2, y, c2)
            s.rows("body", "over", 11, 11, shade(c, -16), 2)
        if kind in ("blazer", "coat", "trench", "labcoat"):
            s.put("body", "over", F, 2, 0, shade(c, 16)); s.put("body", "over", F, 5, 0, shade(c, 16))
            s.put("body", "over", F, 2, 1, shade(c, 12)); s.put("body", "over", F, 5, 1, shade(c, 12))
            for y in (7, 9):
                s.put("body", "over", F, 2 if kind != "trench" else 3, y, c2)
        if kind == "trench":
            s.rows("body", "over", 6, 6, shade(c, -24), 1)
            s.put("body", "over", F, 4, 6, GOLD)
        if kind == "varsity":
            for a in ARMS:
                s.rows(a, "over", 0, sleeves_to - 1, c2, 3)
                s.fill(a, "over", "top", c2, 3)
            s.rows("body", "over", 11, 11, shade(c, -20), 0)
            s.rows("body", "over", 10, 10, c2, 0)
            ch = contrast(c)
            for (x, y) in ((6, 2), (6, 3), (6, 4), (7, 2)):
                s.put("body", "over", F, x, y, ch)
        if kind == "puffer":
            for f in SIDES:
                for y in (2, 5, 8, 11):
                    s.fill("body", "over", f, shade(c, -22), 1, y0=y, y1=y)
            for a in ARMS:
                for y in (2, 5, 8):
                    s.rows(a, "over", y, y, shade(c, -22), 1)
            s.fill("body", "over", F, SILVER, 0, x0=3, x1=3)
        if long_coat:  # tails over the legs
            tail = 6 if kind != "labcoat" else 5
            for l in LEGS:
                s.rows(l, "over", 0, tail, shade(c, -6), 3)
                s.rows(l, "over", tail, tail, shade(c, -22), 1)
                if kind != "trench":  # open coat: the inner half of each leg shows (front x=0 is the outer side of the right leg)
                    s.clear(l, "over", "front", x0=2 if l == "rleg" else 0, x1=3 if l == "rleg" else 1)
                s.clear(l, "over", "left" if l == "rleg" else "right")
    elif kind == "sweater":
        s.rows("body", "over", 11, 11, shade(c, -18), 1)
        s.rows("body", "over", 3, 4, c2, 2)
        for f in SIDES:
            fw, _ = s.size("body", f)
            for x in range(0, fw, 2):
                s.put("body", "over", f, x, 3, contrast(c2) if lum(c2) < 100 else shade(c2, -40))
        s.fill("body", "over", "front", shade(c, 18), 1, y0=0, y1=0, x0=2, x1=5)
        for a in ARMS:
            s.rows(a, "over", 3, 4, c2, 2)
    if kind == "hoodie_up":
        hood(s, c)
    if kind == "labcoat":
        s.fill("body", "over", F, hexc("5C8FD8"), 0, x0=1, x1=1, y0=2, y1=3)  # pens in the pocket


def hood(s, c):
    """Hood up: the whole hat layer except the face opening."""
    d = shade(c, -8)
    s.fill("head", "over", "top", c, 3)
    s.fill("head", "over", "back", d, 3)
    s.fill("head", "over", "right", c, 3)
    s.fill("head", "over", "left", c, 3)
    s.fill("head", "over", "front", c, 3, y0=0, y1=1)
    s.fill("head", "over", "front", shade(c, -20), 3, y0=2, y1=7, x0=0, x1=0)
    s.fill("head", "over", "front", shade(c, -20), 3, y0=2, y1=7, x0=7, x1=7)
    s.fill("head", "over", "bottom", d, 3)
    s.clear("head", "over", "bottom", x0=1, x1=6, y0=2, y1=7)


def cap(s, c, logo=None, backwards=False):
    """Cap on the hat layer; the visor is the darker row (front, or back when backwards)."""
    s.fill("head", "over", "top", c, 3)
    for f in SIDES:
        s.fill("head", "over", f, c, 3, y0=0, y1=1)
    vis = "back" if backwards else "front"
    s.fill("head", "over", vis, shade(c, -30), 1, y0=2, y1=2)
    s.fill("head", "over", "top", shade(c, 18), 0, x0=3, x1=4, y0=3, y1=4)
    if backwards:
        s.clear("head", "over", "back", x0=3, x1=4, y0=1, y1=1)
    elif logo:
        s.put("head", "over", "front", 3, 0, logo)
        s.put("head", "over", "front", 4, 0, logo)
        s.put("head", "over", "front", 3, 1, logo)
        s.put("head", "over", "front", 4, 1, contrast(logo) if logo != WHITE else hexc("E33B3B"))


def beanie(s, c, pom=True):
    s.fill("head", "over", "top", c, 4)
    for f in SIDES:
        s.fill("head", "over", f, c, 4, y0=0, y1=2)
        s.fill("head", "over", f, shade(c, 18), 2, y0=3, y1=3)
        fw, _ = s.size("head", f)
        for x in range(0, fw, 2):
            s.put("head", "over", f, x, 1, shade(c, -14))
    if pom:
        s.fill("head", "over", "top", WHITE, 2, x0=3, x1=4, y0=3, y1=4)


def bucket_hat(s, c):
    s.fill("head", "over", "top", c, 3)
    for f in SIDES:
        s.fill("head", "over", f, c, 3, y0=0, y1=1)
        s.fill("head", "over", f, shade(c, -20), 2, y0=2, y1=2)


def flat_cap(s, c):
    s.fill("head", "over", "top", c, 4)
    s.fill("head", "over", "front", shade(c, -24), 2, y0=0, y1=1)
    for f in ("right", "left", "back"):
        s.fill("head", "over", f, c, 3, y0=0, y1=1)


def top_hat(s, c, band):
    s.fill("head", "over", "top", c, 2)
    for f in SIDES:
        s.fill("head", "over", f, c, 2, y0=0, y1=1)
        s.fill("head", "over", f, band, 1, y0=1, y1=1)


def glasses(s, frame, lens=None):
    lens = lens or (200, 230, 255, 70)
    F = "front"
    for x in (1, 2, 5, 6):
        s.put("head", "over", F, x, 4, lens)
        s.put("head", "over", F, x, 3, frame)
        s.put("head", "over", F, x, 5, frame)
    for x in (0, 3, 4, 7):
        s.put("head", "over", F, x, 4, frame)
    for x in range(5, 8):
        s.put("head", "over", "right", x, 4, frame)
    for x in range(0, 3):
        s.put("head", "over", "left", x, 4, frame)


def sunglasses(s, c=BLACK, frame=None):
    frame = frame or c
    F = "front"
    for x in range(0, 8):
        s.put("head", "over", F, x, 4, c if x not in (3, 4) else frame)
    for x in (1, 2, 5, 6):
        s.put("head", "over", F, x, 3, frame)
    s.put("head", "over", F, 1, 4, shade(c, 50))  # glint
    for x in range(5, 8):
        s.put("head", "over", "right", x, 4, frame)
    for x in range(0, 3):
        s.put("head", "over", "left", x, 4, frame)


def headphones(s, c):
    for f, x0 in (("right", 2), ("left", 2)):
        s.fill("head", "over", f, c, 2, x0=x0, x1=x0 + 3, y0=2, y1=6)
        s.fill("head", "over", f, shade(c, 25), 0, x0=x0 + 1, x1=x0 + 2, y0=3, y1=5)
    s.fill("head", "over", "top", shade(c, -10), 2, x0=0, x1=7, y0=3, y1=4)


def headband(s, c):
    for f in SIDES:
        s.fill("head", "over", f, c, 1, y0=1, y1=1)


def bow(s, c):
    s.fill("head", "over", "top", c, 1, x0=5, x1=7, y0=1, y1=2)
    s.put("head", "over", "top", 6, 1, shade(c, -30))
    s.fill("head", "over", "left", c, 1, x0=1, x1=3, y0=0, y1=1)


def hairclip(s, c):
    s.put("head", "over", "front", 6, 1, c)
    s.put("head", "over", "front", 7, 1, c)


def earrings(s, c):
    s.put("head", "base", "right", 3, 6, c)
    s.put("head", "base", "left", 4, 6, c)


def scarf(s, c, c2=None):
    c2 = c2 or shade(c, -25)
    for f in SIDES:
        fw, _ = s.size("body", f)
        for x in range(fw):
            s.put("body", "over", f, x, 0, c if x % 2 else c2)
            s.put("body", "over", f, x, 1, c2 if x % 2 else c)
    s.fill("body", "over", "top", c, 2)
    s.fill("body", "over", "front", c, 2, x0=2, x1=2, y0=2, y1=6)
    s.put("body", "over", "front", 2, 6, c2)


def necklace(s, c, pendant=None):
    for x in (2, 5):
        s.put("body", "base", "front", x, 0, c)
    for x in (3, 4):
        s.put("body", "base", "front", x, 1, c)
    if pendant:
        s.put("body", "base", "front", 3, 2, pendant)


def chain(s, c=GOLD):
    for (x, y) in ((1, 0), (2, 1), (3, 2), (4, 2), (5, 1), (6, 0)):
        s.put("body", "over", "front", x, y, c)
    s.put("body", "over", "front", 3, 3, shade(c, 20))
    s.put("body", "over", "front", 4, 3, shade(c, 20))


def backpack_straps(s, c):
    for x in (1, 6):
        s.fill("body", "over", "front", c, 2, x0=x, x1=x, y0=0, y1=8)
        s.fill("body", "over", "top", c, 2, x0=x, x1=x)
        s.fill("body", "over", "back", c, 2, x0=x, x1=x, y0=0, y1=1)
    s.put("body", "over", "front", 1, 3, SILVER)
    s.put("body", "over", "front", 6, 3, SILVER)


def apron(s, c, pocket=None, tag=True):
    F = "front"
    s.fill("body", "over", F, c, 2, x0=1, x1=6, y0=2, y1=11)
    s.fill("body", "over", F, c, 2, x0=1, x1=1, y0=0, y1=1)
    s.fill("body", "over", F, c, 2, x0=6, x1=6, y0=0, y1=1)
    s.fill("body", "over", "top", c, 1, x0=1, x1=1)
    s.fill("body", "over", "top", c, 1, x0=6, x1=6)
    s.fill("body", "over", "back", c, 1, x0=1, x1=1, y0=0, y1=6)
    s.fill("body", "over", "back", c, 1, x0=6, x1=6, y0=0, y1=6)
    for f in ("right", "left", "back"):
        s.fill("body", "over", f, shade(c, -15), 1, y0=6, y1=6)
    s.fill("body", "over", F, pocket or shade(c, 18), 1, x0=2, x1=5, y0=7, y1=8)
    s.rows("body", "over", 6, 6, shade(c, -15), 1, faces=("front",))
    s.fill("body", "over", F, c, 2, x0=1, x1=6, y0=6, y1=6)
    if tag:
        s.put("body", "over", F, 5, 3, WHITE)
        s.put("body", "over", F, 6, 3, WHITE)
    # pants overlay: the apron continues over the thighs
    for l in LEGS:
        s.fill(l, "over", F, c, 2, y0=0, y1=3)
        s.fill(l, "over", F, shade(c, -16), 1, y0=3, y1=3)


def mask_lower(s, c):
    s.fill("head", "over", "front", c, 2, y0=5, y1=7)
    s.fill("head", "over", "right", c, 2, y0=5, y1=7, x0=4, x1=7)
    s.fill("head", "over", "left", c, 2, y0=5, y1=7, x0=0, x1=3)
    s.fill("head", "over", "back", shade(c, -10), 2, y0=5, y1=5)


def gloves(s, c):
    for a in ARMS:
        s.rows(a, "base", 10, 11, c, 2)
        s.fill(a, "base", "bottom", c, 2)


def tie(s, c):
    s.put("body", "base", "front", 3, 0, shade(c, 10))
    s.put("body", "base", "front", 4, 0, shade(c, 10))
    s.fill("body", "base", "front", c, 1, x0=3, x1=4, y0=1, y1=5)
    s.put("body", "base", "front", 3, 6, c)


def watch(s, c=GOLD):
    s.rows("larm", "over", 9, 9, c, 1)


def badge_pins(s, rng, n):
    for _ in range(n):
        x, y = rng.choice(((0, 2), (1, 4), (0, 6), (6, 2), (7, 4), (6, 6), (1, 8), (7, 8)))
        s.put("body", "over", "front", x, y, rng.choice(CLOTH[:12] + [GOLD, SILVER]))


# ------------------------------------------------------------------ looks

HAIR_F = ("long", "wavy", "bangs_long", "bob", "ponytail", "braid", "bun", "twintails", "curly", "afro", "pixie", "short")
HAIR_M = ("short", "side_part", "spiky", "curtains", "messy", "buzz", "slick", "curly", "afro", "manbun", "bald", "mohawk",
          "short", "side_part", "messy")


def base_look(rng, female, n=0, age=None):
    age = age or rng.choices(("young", "adult", "old"), (4, 5, 1))[0]
    hair = rng.choice(HAIR_NATURAL[:11]) if rng.random() < 0.86 else rng.choice(HAIR_DYED)
    if age == "old":
        hair = rng.choice((hexc("C9C9C9"), hexc("EDEDED"), hexc("9A9A9A"), hexc("B8B0A8")))
    styles = HAIR_F if female else HAIR_M
    style = styles[(n * 5 + rng.randrange(3)) % len(styles)]
    if age == "old" and style in ("twintails", "mohawk", "spiky", "afro"):
        style = "bun" if female else "side_part"
    look = {
        "female": female, "age": age,
        "skin": rng.choice(SKIN_TONES),
        "eye": rng.choice(EYES),
        "hair": hair,
        "hair_style": style,
        "accent": rng.choice(CLOTH[:12]),
        "smile": rng.random() < 0.5,
    }
    if female:
        if rng.random() < 0.45:
            look["lips"] = rng.choice(LIPS)
        look["blush"] = rng.random() < 0.3
    else:
        look["facial"] = rng.choices((None, "stubble", "beard", "mustache", "goatee"), (6, 2, 2 if age != "young" else 0.4, 1 if age != "young" else 0.2, 1))[0]
    look["freckles"] = (not look.get("blush")) and rng.random() < 0.12
    return look


def paint_head(s, look):
    paint_face(s, look)
    paint_hair(s, look)


def accessories_head(s, rng, look, hat_chance=0.35):
    """Random caps/glasses/etc. for civilians."""
    f = look["female"]
    r = rng.random()
    long_style = look["hair_style"] in ("long", "wavy", "bangs_long", "twintails", "afro", "curly")
    if r < hat_chance:
        kind = rng.choice(("cap", "cap", "cap_back", "beanie", "bucket") if not long_style else ("cap", "beanie", "bucket"))
        c = rng.choice(CLOTH)
        if kind == "cap":
            cap(s, c, logo=rng.choice((WHITE, hexc("E33B3B"), GOLD, BLACK)))
        elif kind == "cap_back":
            cap(s, c, backwards=True)
        elif kind == "beanie":
            beanie(s, c, pom=rng.random() < 0.5)
        else:
            bucket_hat(s, c)
    elif f and r < hat_chance + 0.15:
        rng.choice((headband, bow, hairclip))(s, rng.choice(CLOTH))
    elif r < hat_chance + 0.22:
        headphones(s, rng.choice((BLACK, WHITE, hexc("E33B3B"), hexc("3A6FD8"), hexc("EC407A"))))
    g = rng.random()
    if g < 0.2:
        glasses(s, rng.choice((BLACK, hexc("6B3E20"), hexc("8A1E1E"), SILVER, GOLD)))
    elif g < 0.27:
        sunglasses(s, rng.choice((BLACK, hexc("2A2A3A"), hexc("3A2414"))))
    if f and rng.random() < 0.35:
        earrings(s, rng.choice((GOLD, SILVER, hexc("E8405A"))))


def customer(n, female):
    rng = random.Random(f"customer-{'f' if female else 'm'}-{n}")
    s = Skin(female, rng)
    look = base_look(rng, female, n)
    paint_head(s, look)
    if female:
        outfit = ("tee_jeans", "hoodie_leggings", "dress", "skirt_top", "jacket_jeans", "cardigan_skirt", "sweater_jeans",
                  "tank_shorts", "blouse_trousers", "puffer_jeans", "denim_skirt", "longsleeve_skirt", "vest_jeans")[(n * 7 + rng.randrange(2)) % 13]
    else:
        outfit = ("tee_jeans", "hoodie_joggers", "jacket_jeans", "flannel_jeans", "polo_chinos", "sweater_chinos",
                  "varsity_jeans", "tank_shorts", "tee_cargo", "zip_jeans", "puffer_jeans", "denim_jeans", "blazer_chinos",
                  "striped_shorts")[(n * 5 + rng.randrange(2)) % 14]
    c = rng.choice(CLOTH)
    c2 = rng.choice([x for x in CLOTH if x != c])
    shoes = rng.choice(SHOES)
    shoe_kind = rng.choices(("sneakers", "boots", "dress"), (6, 2, 1))[0]
    denim = rng.choice(DENIM)
    pants = rng.choice(PANTS)
    look["ripped"] = rng.random() < 0.15
    if female and rng.random() < 0.25:
        look["tights"] = rng.choice((hexc("1E1E24"), hexc("3A2A3A"), hexc("D8B8A8")))
    tee = "tee" if rng.random() < 0.8 else "striped"

    def shirt(kind="tee"):
        top_base(s, look, kind, c, c2)
        if kind in ("tee", "long") and rng.random() < 0.6:
            emblem(s, rng.choice(EMBLEMS), 2, 3, c)

    o = outfit
    if o == "tee_jeans":
        shirt(tee); bottom(s, look, "jeans", denim, shoes, shoe_kind)
    elif o == "hoodie_leggings":
        top_base(s, look, "long", c2); bottom(s, look, "leggings", rng.choice((BLACK, hexc("2A2A3A"), hexc("4A3A5A"))), shoes)
        look["hood_print"] = rng.choice(EMBLEMS) if rng.random() < 0.5 else None
        outer(s, look, "hoodie", c)
    elif o == "hoodie_joggers":
        top_base(s, look, "long", c2); bottom(s, look, "joggers", rng.choice(PANTS), shoes)
        look["hood_print"] = rng.choice(EMBLEMS) if rng.random() < 0.5 else None
        outer(s, look, "hoodie", c)
    elif o == "dress":
        top_base(s, look, "dress_top", c, c2)
        if rng.random() < 0.5:
            s.rows("body", "base", 6, 6, c2, 1)  # waist ribbon
        bottom(s, look, "dress", c, shoes, rng.choice(("sneakers", "dress", "boots")))
        if rng.random() < 0.4:
            outer(s, look, "cardigan", c2)
    elif o == "skirt_top":
        shirt(rng.choice(("tee", "striped", "blouse"))); bottom(s, look, rng.choice(("skirt_mini", "skirt_knee", "skirt_long")), c2, shoes, shoe_kind)
    elif o == "jacket_jeans":
        shirt(tee); bottom(s, look, "jeans", denim, shoes, shoe_kind)
        outer(s, look, rng.choice(("leather", "zip", "zip")), rng.choice((BLACK, c2, hexc("5A3A2A"), hexc("37474F"))))
    elif o == "cardigan_skirt":
        shirt("tee"); bottom(s, look, rng.choice(("skirt_knee", "skirt_long")), rng.choice(PANTS + [c2]), shoes, shoe_kind)
        outer(s, look, "cardigan", c2)
    elif o == "sweater_jeans":
        top_base(s, look, "long", c); bottom(s, look, "jeans", denim, shoes, shoe_kind)
        outer(s, look, "sweater", c, c2)
    elif o == "tank_shorts":
        top_base(s, look, "tank", c); bottom(s, look, "shorts", rng.choice(DENIM + PANTS), shoes, "sneakers")
    elif o == "blouse_trousers":
        top_base(s, look, "blouse", c); bottom(s, look, "chinos", pants, shoes, "dress")
    elif o == "puffer_jeans":
        top_base(s, look, "long", c2); bottom(s, look, "jeans", denim, shoes, "boots")
        outer(s, look, "puffer", c)
    elif o == "denim_skirt":
        shirt(tee); bottom(s, look, "skirt_knee", denim, shoes, shoe_kind)
        if rng.random() < 0.5:
            outer(s, look, "denim", rng.choice(DENIM))
    elif o == "longsleeve_skirt":
        shirt("long"); bottom(s, look, "skirt_mini", c2, shoes, "boots")
    elif o == "vest_jeans":
        shirt("blouse"); bottom(s, look, "jeans", denim, shoes, shoe_kind)
        outer(s, look, "vest", c2)
    elif o == "flannel_jeans":
        top_base(s, look, "plaid", c, c2); bottom(s, look, "jeans", denim, shoes, rng.choice(("boots", "sneakers")))
    elif o == "polo_chinos":
        top_base(s, look, "polo", c); bottom(s, look, "chinos", pants, shoes, rng.choice(("dress", "sneakers")))
    elif o == "sweater_chinos":
        top_base(s, look, "long", c); bottom(s, look, "chinos", pants, shoes, shoe_kind)
        outer(s, look, "sweater", c, c2)
    elif o == "varsity_jeans":
        shirt("tee"); bottom(s, look, "jeans", denim, shoes, "sneakers")
        outer(s, look, "varsity", c2, rng.choice((WHITE, hexc("F2F2E0"))))
    elif o == "tee_cargo":
        shirt(tee); bottom(s, look, "cargo", rng.choice((hexc("6B6B3A"), hexc("8A7A5A"), hexc("4A4A4A"))), shoes, "boots")
    elif o == "zip_jeans":
        shirt("tee"); bottom(s, look, "jeans", denim, shoes, shoe_kind)
        outer(s, look, "zip", c2)
    elif o == "denim_jeans":
        shirt("tee"); bottom(s, look, "chinos", rng.choice(PANTS), shoes, shoe_kind)
        outer(s, look, "denim", rng.choice(DENIM))
    elif o == "blazer_chinos":
        top_base(s, look, "long", WHITE if rng.random() < 0.6 else c2)
        if rng.random() < 0.5:
            tie(s, c)
        bottom(s, look, "chinos", pants, shoes, "dress")
        outer(s, look, "blazer", rng.choice((hexc("2A2E3A"), hexc("4A3A2A"), hexc("3A4A3A"), hexc("6A6A70"))))
    elif o == "striped_shorts":
        top_base(s, look, "striped", c, c2); bottom(s, look, "shorts", pants, shoes, "sneakers")
    else:
        raise ValueError(o)
    if rng.random() < 0.15:
        scarf(s, rng.choice(CLOTH))
    elif rng.random() < 0.12:
        necklace(s, rng.choice((GOLD, SILVER)), rng.choice(CLOTH[:8]))
    if rng.random() < 0.15:
        watch(s, rng.choice((GOLD, SILVER, BLACK)))
    accessories_head(s, rng, look)
    if n % 5 == 2:
        backpack_straps(s, rng.choice((BLACK, hexc("3A3A44"), shade(c2, -30))))
    return s


def cashier(n, female):
    rng = random.Random(f"cashier-{n}-{female}")
    s = Skin(female, rng)
    look = base_look(rng, female, n * 3 + 1, age=rng.choice(("young", "young", "adult")))
    if look["hair_style"] in ("afro",):
        look["hair_style"] = "short"
    paint_head(s, look)
    shop = hexc("2E9C5A")
    shirt = WHITE if n % 3 else hexc("D9EFE0")
    top_base(s, look, "polo" if n % 2 else "tee", shirt)
    bottom(s, look, "chinos" if n % 4 else "jeans", rng.choice((hexc("2B3A5A"), hexc("1E1E24"), hexc("4A4A52"), DENIM[0])), rng.choice((BLACK, WHITE)), "sneakers")
    apron(s, shop if n % 4 != 3 else hexc("23804A"), pocket=hexc("43C463"))
    cap(s, shop, logo=WHITE) if n % 3 != 2 else (glasses(s, BLACK) if rng.random() < 0.5 else headband(s, shop))
    if female and rng.random() < 0.4:
        earrings(s, GOLD)
    return s


def guard(n, female):
    rng = random.Random(f"guard-{n}-{female}")
    s = Skin(female, rng)
    look = base_look(rng, female, n * 2, age=rng.choice(("young", "adult", "adult")))
    if female:
        look["hair_style"] = rng.choice(("bun", "ponytail", "short", "braid", "bob"))
    else:
        look["hair_style"] = rng.choice(("buzz", "short", "bald", "side_part", "buzz"))
    look["smile"] = False
    paint_head(s, look)
    navy, dark = hexc("263248"), hexc("1A2234")
    top_base(s, look, "long", navy, dark)
    bottom(s, look, "chinos", dark, BLACK, "boots")
    # tactical vest + utility belt
    outer(s, look, "vest", hexc("1E2636"))
    s.fill("body", "over", "front", GOLD, 0, x0=1, x1=2, y0=2, y1=2)
    s.put("body", "over", "front", 1, 3, GOLD)
    s.put("body", "over", "front", 6, 1, BLACK)  # radio
    s.put("body", "over", "front", 6, 2, hexc("3A3A3A"))
    s.rows("body", "over", 10, 11, BLACK, 1)
    s.put("body", "over", "front", 3, 10, SILVER); s.put("body", "over", "front", 4, 10, SILVER)
    s.fill("body", "over", "right", hexc("2A2A2A"), 1, x0=1, x1=2, y0=9, y1=11)
    for a in ARMS:  # shoulder patches
        s.fill(a, "base", "right" if a == "rarm" else "left", hexc("F2C14E"), 0, x0=1, x1=2, y0=1, y1=2)
    if n % 3 == 0:
        cap(s, dark, logo=GOLD)
    if n % 4 == 1:
        sunglasses(s)
    return s


def wholesaler(n, female):
    rng = random.Random(f"wholesaler-{n}-{female}")
    s = Skin(female, rng)
    look = base_look(rng, female, n, age=rng.choice(("adult", "adult", "old")))
    if not female and rng.random() < 0.7:
        look["facial"] = rng.choice(("mustache", "beard", "mustache"))
    paint_head(s, look)
    coat = shade(rng.choice((hexc("6B4A2A"), hexc("5A4030"), hexc("4A3A2A"), hexc("7A5A3A"), hexc("3A3A40"))), rng.randint(-8, 8))
    top_base(s, look, "long", rng.choice((WHITE, hexc("E0D8C8"), hexc("C8D0D8"))))
    tie(s, rng.choice((hexc("8A1E1E"), hexc("2A4A8A"), GOLD, hexc("2E7D32"))))
    bottom(s, look, "chinos", hexc("3A2A1E"), hexc("3E2414"), "dress")
    outer(s, look, "trench" if n % 2 == 0 else "coat", coat, GOLD)
    if n % 3 != 2:
        flat_cap(s, shade(coat, -10)) if not female else (bow(s, GOLD) if n % 2 else None)
    if n % 4 == 1:
        glasses(s, GOLD)
    watch(s, GOLD)
    return s


def beggar(n, female):
    rng = random.Random(f"beggar-{n}-{female}")
    s = Skin(female, rng)
    look = base_look(rng, female, n * 2 + 1, age=rng.choice(("adult", "old")))
    if not female:
        look["facial"] = rng.choice(("beard", "stubble", "beard"))
    look["smile"] = False
    look["lips"] = None
    look["blush"] = False
    paint_head(s, look)
    base = shade(hexc("7A6A52"), rng.randint(-12, 12))
    top_base(s, look, "long", base)
    look["ripped"] = True
    bottom(s, look, "jeans", shade(rng.choice(DENIM), -10), hexc("4A3A2A"), "boots")
    outer(s, look, rng.choice(("coat", "cardigan", "zip")), shade(hexc("5A5040"), rng.randint(-10, 10)))
    for _ in range(5):  # patches
        part = rng.choice(("body", "rleg", "lleg", "rarm"))
        layer = "over" if part in ("body", "rarm") else "base"
        f = rng.choice(("front", "back"))
        fw, fh = s.size(part, f)
        x, y = rng.randrange(max(1, fw - 1)), rng.randrange(1, fh - 3)
        p = shade(rng.choice(CLOTH), -30)
        s.fill(part, layer, f, p, 4, x0=x, x1=x + 1, y0=y, y1=y + 1)
    scarf(s, shade(rng.choice(CLOTH), -40))
    if n % 2 == 0:
        beanie(s, shade(rng.choice(CLOTH), -30), pom=False)
    gloves(s, hexc("3A3030"))
    for a in ARMS:  # fingerless: skin at the very bottom row
        s.fill(a, "base", "front", look["skin"], 1, y0=11, y1=11)
    return s


def black_market(n, female):
    rng = random.Random(f"black_market-{n}-{female}")
    s = Skin(female, rng)
    look = base_look(rng, female, n, age="adult")
    paint_head(s, look)
    # red glowing eyes
    s.put("head", "base", "front", 2, 4, hexc("FF3030"))
    s.put("head", "base", "front", 5, 4, hexc("FF3030"))
    s.put("head", "base", "front", 1, 4, hexc("FFB0B0"))
    s.put("head", "base", "front", 6, 4, hexc("FFB0B0"))
    hoodc = shade(hexc("22222C"), rng.randint(-6, 6))
    top_base(s, look, "long", hexc("1A1A22"))
    bottom(s, look, "chinos", hexc("121218"), BLACK, "boots")
    outer(s, look, "coat", hexc("15151C"), hexc("2A2A34"))
    hood(s, hoodc)
    if n % 2 == 0:
        mask_lower(s, hexc("101014"))
    gloves(s, BLACK)
    if n % 3 == 1:
        chain(s, SILVER)
    return s


def furniture_seller(n, female):
    rng = random.Random(f"furniture_seller-{n}-{female}")
    s = Skin(female, rng)
    look = base_look(rng, female, n, age=rng.choice(("adult", "adult", "old")))
    paint_head(s, look)
    shirt = rng.choice((hexc("C62828"), hexc("2E7D32"), hexc("1565C0"), hexc("F9A825")))
    top_base(s, look, "plaid", shirt, shade(shirt, -45))
    overalls = rng.choice(DENIM + [hexc("6B4A2A"), hexc("4A5A3A")])
    bottom(s, look, "jeans", overalls, hexc("6B3E20"), "boots")
    # overalls bib (base) with straps
    s.fill("body", "base", "front", overalls, 2, x0=1, x1=6, y0=4, y1=11)
    s.fill("body", "base", "back", overalls, 2, y0=6, y1=11)
    for x in (1, 6):
        s.fill("body", "base", "front", overalls, 2, x0=x, x1=x, y0=0, y1=3)
        s.fill("body", "base", "back", overalls, 2, x0=x, x1=x, y0=0, y1=5)
    s.put("body", "base", "front", 1, 4, SILVER); s.put("body", "base", "front", 6, 4, SILVER)
    # tool belt with depth
    s.rows("body", "over", 10, 11, hexc("8A5A2A"), 2)
    s.fill("body", "over", "right", hexc("6B3E20"), 1, x0=0, x1=3, y0=10, y1=11)
    s.put("body", "over", "front", 1, 9, hexc("C0C0C0"))  # hammer head
    s.put("body", "over", "front", 6, 9, hexc("F2C14E"))  # pencil
    if n % 2 == 0:
        cap(s, rng.choice((hexc("C62828"), hexc("F9A825"), hexc("2E7D32"))), logo=WHITE)
    elif n % 3 == 1:
        bucket_hat(s, hexc("C9B48A"))
    return s


def trader(n, female):
    rng = random.Random(f"trader-{n}-{female}")
    s = Skin(female, rng)
    look = base_look(rng, female, n * 3, age=rng.choice(("young", "adult", "adult", "old")))
    paint_head(s, look)
    c = rng.choice(CLOTH)
    top_base(s, look, rng.choice(("tee", "long", "polo")), c)
    if rng.random() < 0.6:
        emblem(s, rng.choice(EMBLEMS), 2, 3, c)
    bottom(s, look, rng.choice(("chinos", "cargo", "jeans")), rng.choice(PANTS + DENIM), rng.choice(SHOES), "sneakers")
    vest = rng.choice((hexc("6B6B3A"), hexc("8A7A5A"), hexc("4A5A3A"), hexc("37474F"), hexc("6D4C41")))
    outer(s, look, "vest", vest)
    badge_pins(s, rng, 6)
    if n % 2 == 0:
        cap(s, rng.choice(CLOTH), logo=WHITE)
    elif n % 3 == 1:
        bucket_hat(s, vest)
    if n % 3 == 0:
        glasses(s, rng.choice((BLACK, GOLD, SILVER)))
    return s


def scalper(n, female):
    rng = random.Random(f"scalper-{n}-{female}")
    s = Skin(female, rng)
    look = base_look(rng, female, n, age="young")
    if look["hair_style"] in ("long", "wavy", "bangs_long", "twintails") and n % 2:
        look["hair_style"] = "ponytail"
    look["smile"] = True
    paint_head(s, look)
    hoodie = shade(rng.choice((hexc("3A3A44"), hexc("1E1E24"), hexc("4A2A5A"), hexc("2A3A2A"), hexc("5A1E1E"))), rng.randint(-6, 10))
    top_base(s, look, "long", rng.choice(CLOTH))
    bottom(s, look, "joggers", rng.choice((hexc("2A2A30"), hexc("3A3A40"), hexc("1E1E24"))), rng.choice((WHITE, hexc("E33B3B"), GOLD, hexc("3A6FD8"))), "sneakers")
    look["hood_print"] = rng.choice(("ball", "star", "bolt"))
    outer(s, look, "hoodie", hoodie)
    chain(s, GOLD)
    cap(s, BLACK if n % 2 else hexc("E33B3B"), backwards=True)
    if n % 3 != 1:
        sunglasses(s, BLACK, frame=GOLD if n % 2 else BLACK)
    watch(s, GOLD)
    if n % 2 == 0:
        backpack_straps(s, BLACK)
    return s


ROLE_PAINTERS = {
    "customer": customer, "cashier": cashier, "guard": guard, "wholesaler": wholesaler, "beggar": beggar,
    "black_market": black_market, "furniture_seller": furniture_seller, "trader": trader, "scalper": scalper,
}


# ------------------------------------------------------------------ 10 fixed unique customers
# Ids match entity/FixedCustomers: 0 Don Aurelio (excellent customer), 1 Vinnie Vex (known thief), 2 Nana Rosa,
# 3 Timmy Turbo, 4 Dr. Margarita, 5 Gus Grumble, 6 Chad Flipson, 7 Luna Star, 8 Barnaby Brine, 9 Madame Noir.
# Their sex (slim/wide model) is NpcIdentity.FIXED_SEX.

def fixed_0():
    """Don Aurelio, the whale: navy pinstripe three-piece suit, gold tie, gold watch and chain, slicked silver hair, shades."""
    rng = random.Random("fixed-0")
    s = Skin(False, rng)
    look = {"female": False, "skin": SKIN_TONES[2], "eye": EYES[0], "hair": hexc("C9C9C9"), "hair_style": "slick",
            "facial": "mustache", "smile": True}
    paint_head(s, look)
    navy = hexc("1C2540")
    top_base(s, look, "long", WHITE)
    tie(s, GOLD)
    bottom(s, look, "chinos", navy, hexc("2A1A10"), "dress")
    for l in LEGS:  # pinstripes
        for f in SIDES:
            fw, _ = s.size(l, f)
            for x in range(0, fw, 2):
                s.fill(l, "base", f, hexc("3A4568"), 0, x0=x, x1=x, y0=0, y1=9)
    outer(s, look, "blazer", navy, GOLD)
    for f in SIDES:
        fw, _ = s.size("body", f)
        for x in range(0, fw, 2):
            for y in range(0, 12):
                if s.get("body", "over", f, x, y)[3]:
                    s.put("body", "over", f, x, y, hexc("2C365A"))
    s.put("body", "over", "front", 1, 2, hexc("E33B3B"))  # pocket square
    s.put("body", "over", "front", 1, 3, WHITE)
    sunglasses(s, BLACK, frame=GOLD)
    chain(s, GOLD)
    watch(s, GOLD)
    s.rows("rarm", "base", 10, 10, GOLD, 0)  # rings and cufflinks
    return s


def fixed_1():
    """Vinnie Vex, the known thief: black hoodie with the hood up, striped balaclava-style mask, gloves, shifty eyes."""
    rng = random.Random("fixed-1")
    s = Skin(False, rng)
    look = {"female": False, "skin": SKIN_TONES[4], "eye": hexc("D0C040"), "hair": hexc("1B1410"), "hair_style": "messy",
            "facial": "stubble", "smile": False}
    paint_head(s, look)
    s.put("head", "base", "front", 1, 3, BLACK)  # suspicious low brows
    s.put("head", "base", "front", 6, 3, BLACK)
    top_base(s, look, "long", hexc("2A2A30"))
    bottom(s, look, "joggers", hexc("1E1E24"), BLACK, "sneakers")
    look["hood_print"] = None
    outer(s, look, "hoodie_up", hexc("18181E"))
    mask_lower(s, hexc("2A2A2A"))
    for x in range(8):
        s.put("head", "over", "front", x, 6, hexc("E8E8E8") if x % 2 else hexc("2A2A2A"))
    gloves(s, BLACK)
    backpack_straps(s, hexc("3A3A3A"))  # loot bag
    # a stolen card sticking out of the hoodie pocket
    s.put("body", "over", "front", 5, 7, hexc("F2C14E"))
    s.put("body", "over", "front", 5, 6, hexc("3A6FD8"))
    return s


def fixed_2():
    """Nana Rosa, the granny collector: white bun, round glasses, lavender cardigan, long floral skirt, pearl necklace."""
    rng = random.Random("fixed-2")
    s = Skin(True, rng)
    look = {"female": True, "skin": SKIN_TONES[1], "eye": EYES[2], "hair": hexc("EDEDED"), "hair_style": "bun",
            "accent": hexc("B8A0F0"), "smile": True, "blush": True, "lips": hexc("C06A7A"), "age": "old"}
    paint_head(s, look)
    s.put("head", "base", "front", 1, 6, shade(SKIN_TONES[1], -15))  # smile wrinkles
    s.put("head", "base", "front", 6, 6, shade(SKIN_TONES[1], -15))
    top_base(s, look, "blouse", hexc("F4ECE0"))
    necklace(s, WHITE, WHITE)
    skirt = hexc("5A4A7A")
    bottom(s, look, "skirt_long", skirt, hexc("6B3E20"), "dress")
    for l in LEGS:  # tiny flowers
        for (x, y) in ((1, 1), (3, 4), (0, 7), (2, 9)):
            s.put(l, "over", "front", x, y, hexc("F4B0C8"))
            s.put(l, "over", "back", (x + 2) % 4, y, hexc("F4B0C8"))
    outer(s, look, "cardigan", hexc("B8A0F0"), hexc("F2E6A0"))
    glasses(s, GOLD, (220, 235, 255, 90))
    earrings(s, WHITE)
    return s


def fixed_3():
    """Timmy Turbo, the hyperactive kid: backwards red cap, yellow tee with a ball emblem, shorts, big sneakers and a backpack."""
    rng = random.Random("fixed-3")
    s = Skin(False, rng)
    look = {"female": False, "skin": SKIN_TONES[3], "eye": EYES[1], "hair": hexc("3D2716"), "hair_style": "spiky",
            "smile": True, "freckles": True}
    paint_head(s, look)
    top_base(s, look, "tee", hexc("FFD23F"), hexc("F2A01E"))
    emblem(s, "ball", 2, 3, hexc("FFD23F"))
    bottom(s, look, "shorts", hexc("3A6FD8"), hexc("E33B3B"), "sneakers")
    cap(s, hexc("E33B3B"), backwards=True)
    backpack_straps(s, hexc("2E7D32"))
    s.rows("larm", "over", 9, 9, hexc("3A6FD8"), 0)  # sweatband
    return s


def fixed_4():
    """Dr. Margarita, the haughty card expert: platinum asymmetric bob, gold reading glasses, magenta blazer, pearls."""
    rng = random.Random("fixed-4")
    s = Skin(True, rng)
    look = {"female": True, "skin": SKIN_TONES[2], "eye": EYES[2], "hair": hexc("F0E2B0"), "hair_style": "bob",
            "smile": False, "lips": hexc("C02040")}
    paint_head(s, look)
    s.put("head", "base", "front", 1, 3, hexc("3A2414"))  # one raised brow
    s.put("head", "base", "front", 2, 2, hexc("3A2414"))
    s.fill("head", "over", "front", hexc("F0E2B0"), 3, x0=0, x1=4, y0=1, y1=2)  # asymmetric fringe
    top_base(s, look, "blouse", WHITE)
    necklace(s, WHITE, WHITE)
    bottom(s, look, "skirt_knee", hexc("2A2A30"), hexc("C02040"), "dress")
    outer(s, look, "blazer", hexc("C2185B"), GOLD)
    glasses(s, GOLD, (230, 240, 255, 80))
    s.fill("head", "over", "right", GOLD, 0, x0=5, x1=5, y0=5, y1=7)  # glasses chain
    s.fill("head", "over", "left", GOLD, 0, x0=2, x1=2, y0=5, y1=7)
    s.put("body", "over", "front", 1, 2, hexc("F4F4F4"))  # loupe in the breast pocket
    s.put("body", "over", "front", 1, 3, GOLD)
    earrings(s, GOLD)
    watch(s, GOLD)
    return s


def fixed_5():
    """Gus Grumble, grumpy and smelly: frown and unibrow, stubble, stained tank top under a rumpled brown cardigan."""
    rng = random.Random("fixed-5")
    s = Skin(False, rng)
    look = {"female": False, "skin": SKIN_TONES[3], "eye": EYES[0], "hair": hexc("5A4A3A"), "hair_style": "bald",
            "facial": "stubble", "smile": False, "age": "old"}
    paint_head(s, look)
    brow = hexc("3A2A1E")
    s.fill("head", "base", "front", brow, 0, x0=1, x1=6, y0=3, y1=3)  # unibrow
    s.put("head", "base", "front", 3, 2, shade(look["skin"], -20))  # frown wrinkle
    s.put("head", "base", "front", 4, 2, shade(look["skin"], -20))
    s.put("head", "base", "front", 2, 7, shade(look["skin"], -60))  # mouth turned down
    s.put("head", "base", "front", 5, 7, shade(look["skin"], -60))
    s.fill("head", "base", "right", hexc("6A5A4A"), 2, x0=0, x1=4, y0=2, y1=4)  # grey tufts over the ears
    s.fill("head", "base", "left", hexc("6A5A4A"), 2, x0=3, x1=7, y0=2, y1=4)
    s.fill("head", "over", "right", hexc("7A6A5A"), 3, x0=1, x1=4, y0=2, y1=3)
    s.fill("head", "over", "left", hexc("7A6A5A"), 3, x0=3, x1=6, y0=2, y1=3)
    top_base(s, look, "tank", hexc("E8E0C8"))
    for (x, y) in ((2, 2), (3, 3), (5, 6), (4, 8), (2, 9)):  # stains
        s.put("body", "base", "front", x, y, hexc("A89058"))
    bottom(s, look, "chinos", hexc("5A5A4A"), hexc("4A3A2A"), "dress")
    s.rows("body", "base", 11, 11, hexc("2A2A2A"), 0)
    outer(s, look, "cardigan", hexc("6B4A2A"), hexc("3A2A1E"))
    for (x, y) in ((0, 5), (7, 8), (1, 10)):  # holes
        s.put("body", "over", "front", x, y, T)
    for a in ARMS:  # sweat marks
        s.put(a, "base", "left" if a == "rarm" else "right", 1, 3, hexc("B8A878"))
    for (x, y) in ((1, 0), (2, 1), (5, 0), (6, 1)):  # stink lines on the hat layer
        s.put("head", "over", "top", x, y, (150, 190, 80, 140))
    return s


def fixed_6():
    """Chad Flipson, the reseller: backwards cap, shades, puffer vest over a logo tee, gold chain, loud sneakers."""
    rng = random.Random("fixed-6")
    s = Skin(False, rng)
    look = {"female": False, "skin": SKIN_TONES[2], "eye": EYES[2], "hair": hexc("D9B26A"), "hair_style": "slick",
            "smile": True}
    paint_head(s, look)
    s.put("head", "base", "front", 5, 6, shade(look["skin"], -35))  # smirk
    top_base(s, look, "tee", WHITE)
    emblem(s, "number", 2, 3, WHITE)
    bottom(s, look, "joggers", hexc("2A2A30"), hexc("E33B3B"), "sneakers")
    for l in LEGS:  # two-tone designer sneakers
        s.put(l, "over", "front", 0, 10, GOLD)
        s.put(l, "over", "front", 3, 10, GOLD)
    outer(s, look, "puffer", hexc("FF8A3D"))
    for a in ARMS:  # sleeveless: a puffer VEST
        for f in SIDES + ("top",):
            s.clear(a, "over", f)
    s.clear("body", "over", "front", x0=2, x1=5, y0=0, y1=11)
    chain(s, GOLD)
    cap(s, hexc("1E1E24"), backwards=True)
    sunglasses(s, hexc("2A1A3A"), frame=GOLD)
    watch(s, GOLD)
    return s


def fixed_7():
    """Luna Star, the streamer: pink twin tails with space buns, star-shaped shades, headset mic, white crop jacket."""
    rng = random.Random("fixed-7")
    s = Skin(True, rng)
    look = {"female": True, "skin": SKIN_TONES[0], "eye": EYES[2], "hair": hexc("F07AB8"), "hair_style": "twintails",
            "accent": hexc("40E0F0"), "smile": True, "blush": True, "lips": hexc("E05080")}
    paint_head(s, look)
    for x0 in (0, 5):  # space buns on top
        s.fill("head", "over", "top", hexc("F8A0D0"), 2, x0=x0, x1=x0 + 2, y0=2, y1=4)
    top_base(s, look, "tee", hexc("1E1E24"))
    emblem(s, "star", 2, 3, hexc("1E1E24"))
    bottom(s, look, "skirt_mini", hexc("B8A0F0"), WHITE, "sneakers")
    for l in LEGS:  # holographic sheen
        for y in range(0, 4):
            for x in range(4):
                if (x + y) % 2:
                    s.put(l, "over", "front", x, y, hexc("A0E8F0"))
    outer(s, look, "zip", hexc("F6F6F6"), hexc("40E0F0"))
    s.rows("body", "over", 8, 11, T, 0)  # crop jacket
    for f in SIDES:
        s.fill("body", "over", f, hexc("40E0F0"), 0, y0=7, y1=7)
    for x in (0, 1, 2, 5, 6, 7):  # star-shaped shades
        s.put("head", "over", "front", x, 4, hexc("F2C14E"))
    for x in (1, 6):
        s.put("head", "over", "front", x, 3, hexc("F2C14E"))
        s.put("head", "over", "front", x, 5, hexc("F2C14E"))
    s.put("head", "over", "front", 3, 4, hexc("F07AB8"))
    s.put("head", "over", "front", 4, 4, hexc("F07AB8"))
    s.put("head", "over", "right", 5, 5, WHITE)  # headset mic
    s.put("head", "over", "right", 6, 6, WHITE)
    s.put("head", "over", "front", 0, 6, WHITE)
    s.put("head", "over", "front", 1, 6, hexc("40E0F0"))
    earrings(s, hexc("40E0F0"))
    return s


def fixed_8():
    """Barnaby Brine, the old sailor: captain's cap, white beard, navy peacoat with gold buttons, striped shirt."""
    rng = random.Random("fixed-8")
    s = Skin(False, rng)
    look = {"female": False, "skin": SKIN_TONES[4], "eye": EYES[2], "hair": hexc("EDEDED"), "hair_style": "short",
            "facial": "beard", "smile": True, "age": "old"}
    paint_head(s, look)
    s.put("head", "base", "front", 1, 5, hexc("D07A6A"))  # sun-burnt cheeks
    s.put("head", "base", "front", 6, 5, hexc("D07A6A"))
    top_base(s, look, "striped", WHITE, hexc("1C2A5A"))
    bottom(s, look, "chinos", hexc("1C2540"), hexc("1E1E22"), "boots")
    outer(s, look, "coat", hexc("1C2540"), GOLD)
    for y in (2, 4, 6):  # double row of gold buttons
        s.put("body", "over", "front", 1, y, GOLD)
        s.put("body", "over", "front", 6, y, GOLD)
    for a in ARMS:
        s.rows(a, "over", 8, 8, GOLD, 0)  # cuff braid
    # captain's cap: white top, black band and visor, gold badge
    s.fill("head", "over", "top", WHITE, 2)
    for f in SIDES:
        s.fill("head", "over", f, WHITE, 2, y0=0, y1=0)
        s.fill("head", "over", f, hexc("16161C"), 1, y0=1, y1=1)
    s.fill("head", "over", "front", hexc("16161C"), 0, y0=2, y1=2)
    s.put("head", "over", "front", 3, 1, GOLD)
    s.put("head", "over", "front", 4, 1, GOLD)
    s.put("head", "over", "front", 3, 0, GOLD)
    s.put("larm", "base", "front", 1, 10, hexc("2A4A8A"))  # anchor tattoo
    return s


def fixed_9():
    """Madame Noir, the mysterious seller: black hat with a veil, black hair with a purple streak, lace dress."""
    rng = random.Random("fixed-9")
    s = Skin(True, rng)
    look = {"female": True, "skin": hexc("F4E4DA"), "eye": hexc("8A4AD0"), "hair": hexc("141418"), "hair_style": "bangs_long",
            "smile": False, "lips": hexc("3A1030")}
    paint_head(s, look)
    s.fill("head", "over", "front", hexc("8A4AD0"), 2, x0=7, x1=7, y0=1, y1=7)  # purple streak
    s.fill("body", "over", "back", hexc("8A4AD0"), 2, x0=6, x1=6, y0=0, y1=5)
    s.put("head", "base", "front", 1, 4, hexc("D8D0E8"))
    s.put("head", "base", "front", 6, 4, hexc("D8D0E8"))
    for x in (0, 7):  # eyeliner wings
        s.put("head", "base", "front", x, 4, BLACK)
    top_base(s, look, "long", hexc("16161C"))
    for x in range(8):  # lace pattern
        for y in range(0, 12, 2):
            if (x + y // 2) % 2:
                s.put("body", "base", "front", x, y, hexc("2A2030"))
    bottom(s, look, "dress", hexc("1A1420"), hexc("16161C"), "boots")
    for l in LEGS:
        s.cube(l, "base", hexc("2A1A2A"), 2)  # dark tights
        s.rows(l, "base", 0, 3, hexc("1A1420"), 2)
        s.rows(l, "base", 8, 11, hexc("16161C"), 2)
        s.rows(l, "over", 5, 5, hexc("4A2A5A"), 1)
    s.fill("body", "base", "front", hexc("4A2A5A"), 0, x0=0, x1=7, y0=0, y1=0)  # choker
    s.put("body", "base", "front", 3, 1, hexc("C0A0F0"))
    for a in ARMS:  # lace sleeves
        s.rows(a, "over", 6, 9, hexc("2A2030"), 1)
        for y in (7, 9):
            s.rows(a, "over", y, y, T, 0)
    # black hat with a purple band and a see-through veil over the eyes
    s.fill("head", "over", "top", hexc("101014"), 2)
    for f in SIDES:
        s.fill("head", "over", f, hexc("101014"), 2, y0=0, y1=0)
        s.fill("head", "over", f, hexc("6A3A8A"), 1, y0=1, y1=1)
    for x in range(8):
        for y in (2, 3, 4):
            if (x + y) % 2 == 0:
                s.put("head", "over", "front", x, y, (16, 16, 20, 150))
    earrings(s, SILVER)
    return s


FIXED = [fixed_0, fixed_1, fixed_2, fixed_3, fixed_4, fixed_5, fixed_6, fixed_7, fixed_8, fixed_9]


# ------------------------------------------------------------------ 3D backpack texture (32x32)

def backpack_texture(i):
    """UV layout must match TcgNpcRenderer.BackpackFeature: bag 7x9x3 at (0,0), pocket 5x4x1 at (0,12),
    top handle 3x1x1 at (0,18). The outward (+Z) faces are the template 'back' faces."""
    rng = random.Random(f"backpack-{i}")
    cols = [hexc(h) for h in ("E33B3B", "3A6FD8", "2E7D32", "F2C14E", "8E44AD", "37474F", "FF8A3D", "EC407A")]
    c = cols[i % len(cols)]
    px = [[T] * 32 for _ in range(32)]

    def box(u, v, w, h, d, col):
        def rect(x, y, ww, hh, cc):
            for yy in range(hh):
                for xx in range(ww):
                    px[y + yy][x + xx] = shade(cc, rng.randint(-4, 4))
        rect(u + d, v, w, d, shade(col, 10))
        rect(u + d + w, v, w, d, shade(col, -16))
        rect(u, v + d, d, h, shade(col, -6))
        rect(u + d, v + d, w, h, shade(col, -10))
        rect(u + d + w, v + d, d, h, shade(col, -6))
        rect(u + 2 * d + w, v + d, w, h, col)

    box(0, 0, 7, 9, 3, c)
    bx, by = 0 + 2 * 3 + 7, 0 + 3  # outward face of the bag
    for x in range(7):  # top flap + zipper
        px[by][bx + x] = shade(c, -28)
        px[by + 1][bx + x] = shade(c, -20)
        px[by + 2][bx + x] = SILVER if x in (2, 3) else shade(c, -34)
    for y in range(9):  # side seams
        px[by + y][bx] = shade(c, -18)
        px[by + y][bx + 6] = shade(c, -18)
    px[by + 8] = px[by + 8][:bx] + [shade(c, -30)] * 7 + px[by + 8][bx + 7:]
    box(0, 12, 5, 4, 1, shade(c, -12))
    pxx, pyy = 0 + 2 * 1 + 5, 12 + 1
    for x in range(5):
        px[pyy][pxx + x] = shade(c, -36)
    px[pyy][pxx + 2] = SILVER
    # small generic emblem on the pocket
    em = (WHITE, hexc("F2C14E"), WHITE, BLACK, WHITE, WHITE, hexc("1E1E24"), WHITE)[i % 8]
    px[pyy + 2][pxx + 2] = em
    box(0, 18, 3, 1, 1, hexc("2A2A2A"))
    return px


# ------------------------------------------------------------------ preview

def preview(skins, path, cols=16, scale=3):
    """Front and back view of each skin (base + overlay), in a grid."""
    cell_w, cell_h = 36, 34
    rows_n = (len(skins) + cols - 1) // cols
    W, H = cols * cell_w * scale, rows_n * cell_h * scale
    img = [[(40, 44, 52, 255)] * W for _ in range(H)]

    def blit(s, part, face, dx, dy, ox, oy, mirror=False):
        for layer in ("base", "over"):
            fx, fy, fw, fh = s.rect(part, layer, face)
            for y in range(fh):
                for x in range(fw):
                    c = s.px[fy + y][fx + x]
                    if c[3] == 0 or (layer == "base" and c[3] < 255 and False):
                        continue
                    xx = (fw - 1 - x) if mirror else x
                    a = c[3] / 255
                    for sy in range(scale):
                        for sx in range(scale):
                            X, Y = (ox + dx + xx) * scale + sx, (oy + dy + y) * scale + sy
                            if 0 <= X < W and 0 <= Y < H:
                                o = img[Y][X]
                                img[Y][X] = (clamp(o[0] * (1 - a) + c[0] * a), clamp(o[1] * (1 - a) + c[1] * a), clamp(o[2] * (1 - a) + c[2] * a), 255)

    for i, s in enumerate(skins):
        ox, oy = (i % cols) * cell_w + 1, (i // cols) * cell_h + 1
        aw = s.aw
        # front
        blit(s, "head", "front", 4, 0, ox, oy)
        blit(s, "body", "front", 4, 8, ox, oy)
        blit(s, "rarm", "front", 4 - aw, 8, ox, oy)
        blit(s, "larm", "front", 12, 8, ox, oy)
        blit(s, "rleg", "front", 4, 20, ox, oy)
        blit(s, "lleg", "front", 8, 20, ox, oy)
        # back
        bx = ox + 18
        blit(s, "head", "back", 4, 0, bx, oy)
        blit(s, "body", "back", 4, 8, bx, oy)
        blit(s, "larm", "back", 4 - aw, 8, bx, oy)
        blit(s, "rarm", "back", 12, 8, bx, oy)
        blit(s, "lleg", "back", 4, 20, bx, oy)
        blit(s, "rleg", "back", 8, 20, bx, oy)
    png(Path(path), img)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    # remove stale procedural skins (counts may shrink)
    for old in OUT.glob("*.png"):
        old.unlink()
    total = 0
    sheet = []
    for role, count in COUNTS.items():
        painter = ROLE_PAINTERS[role]
        for sex in ("f", "m"):
            for n in range(count):
                s = painter(n, sex == "f")
                s.save(OUT / f"{role}_{sex}_{n}.png")
                total += 1
                if role != "customer" or n < 16:
                    sheet.append(s)
    for i, f in enumerate(FIXED):
        s = f()
        s.save(OUT / f"fixed_{i}.png")
        sheet.append(s)
        total += 1
    for i in range(BACKPACK_COLORS):
        png(OUT / f"backpack_{i}.png", backpack_texture(i))
    print(f"{total} skins + {BACKPACK_COLORS} backpacks -> {OUT}")
    if "--preview" in sys.argv:
        p = sys.argv[sys.argv.index("--preview") + 1]
        preview(sheet, p)
        print("preview ->", p)
    if "--preview-customers" in sys.argv:
        p = sys.argv[sys.argv.index("--preview-customers") + 1]
        cs = [customer(n, sex == "f") for sex in ("f", "m") for n in range(COUNTS["customer"])]
        preview(cs, p)
        print("customer preview ->", p)


if __name__ == "__main__":
    main()
