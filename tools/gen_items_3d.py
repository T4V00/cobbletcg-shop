#!/usr/bin/env python3
"""
1.0 beta 3: proper 3D models for the tool-like items (no dependencies: PNGs written by hand with zlib).

For every tool item it writes
  models/item/<name>.json       entry model: parent builtin/entity + the display transforms (hand, ground, frame, head).
                                The client renderer (dev.tcgshop.client.render.ToolItemRenderer) draws, inside it:
  models/item/icon/<name>.json  the 2D icon in the GUI (inventory, hotbar, store) - keeps the classic pixel-art sprite;
  models/item/3d/<name>.json    the Blockbench-style 3D model everywhere else (no display of its own: the entry model's
                                transforms already apply, so it must stay identity);
  textures/item/3d/<name>.png   the 3D model's texture (material swatches + painted art regions).

The TCG phone gets one 3D model + texture + GUI icon PER TIER (dev.tcgshop.phone.PhoneTier): own body colour/material,
frame, camera layout and the app icons of that tier on its screen (client/render/PhoneItemRenderer picks the tier).

Items: tcg_phone (7 tiers), nerd_gloves, manager_goggles, deodorant_spray, mop, repair_kit, general_binder,
personal_binder, mansion_key. (cash_register is a block item whose model is already the 3D block model.)

Everything is generated here from simple shapes; no external art. Run: python tools/gen_items_3d.py
"""
import json
import math
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
A = ROOT / "common/src/main/resources/assets/tcgshop"
NS = "tcgshop"

# ----------------------------------------------------------------------------------------------------------- png / colour

T = (0, 0, 0, 0)


def write_png(path, px):
    h, w = len(px), len(px[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(c1, c2, t):
    return tuple(clamp(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,)


def shade(c, f):
    """f > 1 lightens towards white, f < 1 darkens."""
    if f >= 1:
        return mix(c, (255, 255, 255, 255), f - 1)
    return tuple(clamp(c[i] * f) for i in range(3)) + (c[3] if len(c) > 3 else 255,)


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=1) + "\n", encoding="utf-8")


class Tex:
    def __init__(self, w, h, seed):
        self.w, self.h = w, h
        self.px = [[T] * w for _ in range(h)]
        self.rng = random.Random(seed)

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x]

    def blend(self, x, y, c, a):
        if 0 <= x < self.w and 0 <= y < self.h:
            b = self.px[y][x]
            if b[3] == 0:
                self.px[y][x] = c[:3] + (255,)
            else:
                self.px[y][x] = mix(b, c, a)

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, c)

    def outline(self, x, y, w, h, c):
        for xx in range(x, x + w):
            self.set(xx, y, c)
            self.set(xx, y + h - 1, c)
        for yy in range(y, y + h):
            self.set(x, yy, c)
            self.set(x + w - 1, yy, c)

    def disc(self, cx, cy, r, c, a=1.0):
        for yy in range(int(cy - r - 1), int(cy + r + 2)):
            for xx in range(int(cx - r - 1), int(cx + r + 2)):
                if (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2 <= r * r:
                    if a >= 1:
                        self.set(xx, yy, c)
                    else:
                        self.blend(xx, yy, c, a)

    def ring(self, cx, cy, r0, r1, c):
        for yy in range(int(cy - r1 - 1), int(cy + r1 + 2)):
            for xx in range(int(cx - r1 - 1), int(cx + r1 + 2)):
                d = ((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2) ** 0.5
                if r0 <= d <= r1:
                    self.set(xx, yy, c)

    def material(self, x, y, w, h, c, amp=7, style="noise"):
        """Fills a swatch with a base colour + deterministic luma noise (style: noise, brushed_h, brushed_v, ribbed)."""
        rows = [self.rng.uniform(-amp, amp) for _ in range(h)]
        cols = [self.rng.uniform(-amp, amp) for _ in range(w)]
        for yy in range(h):
            for xx in range(w):
                n = self.rng.uniform(-amp, amp)
                if style == "brushed_h":
                    n = rows[yy] + n * 0.35
                elif style == "brushed_v":
                    n = cols[xx] + n * 0.35
                elif style == "ribbed":
                    n = (amp * 1.6 if xx % 2 == 0 else -amp) + n * 0.3
                self.set(x + xx, y + yy, tuple(clamp(c[i] + n) for i in range(3)) + (255,))
        return (x, y, w, h)

    def save(self, path):
        write_png(path, self.px)


def glyph(tex, rows, x, y, c, scale=1):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                tex.rect(x + i * scale, y + j * scale, scale, scale, c)


# ---------------------------------------------------------------------------------------------------------- model helpers

DIRS = ("north", "south", "east", "west", "up", "down")


class Model:
    def __init__(self, tex_path, size):
        self.tex = tex_path
        self.size = size
        self.elements = []

    def uv(self, r):
        x, y, w, h = r[:4]
        s = 16.0 / self.size
        return [round(x * s, 4), round(y * s, 4), round((x + w) * s, 4), round((y + h) * s, 4)]

    def box(self, frm, to, faces, rot=None):
        """faces: {dir: region | (region, uv_rotation) | None}. Regions are pixel rects (x, y, w, h) of the texture."""
        out = {}
        for d, r in faces.items():
            if r is None:
                continue
            face_rot = 0
            if isinstance(r, tuple) and len(r) == 2 and isinstance(r[0], tuple):
                r, face_rot = r
            f = {"uv": self.uv(r), "texture": "#0"}
            if face_rot:
                f["rotation"] = face_rot
            out[d] = f
        e = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": out}
        if rot:
            e["rotation"] = {"angle": rot[0], "axis": rot[1], "origin": list(rot[2])}
        self.elements.append(e)
        return e

    def json(self):
        return {
            "credit": "MasterBall Traders Co. - generated by tools/gen_items_3d.py",
            "texture_size": [self.size, self.size],
            "textures": {"0": self.tex, "particle": self.tex},
            "elements": self.elements,
        }


def all6(r, **over):
    d = {k: r for k in DIRS}
    d.update(over)
    return d


def tf(rot=(0, 0, 0), tr=(0, 0, 0), sc=(1, 1, 1)):
    if isinstance(sc, (int, float)):
        sc = (sc, sc, sc)
    return {"rotation": list(rot), "translation": list(tr), "scale": list(sc)}


# Display presets. Only right-hand entries: Minecraft mirrors them for the left hand (Transformation.apply(leftHanded)).
# Models face +Z (south) like vanilla sprites unless noted; in item frames the viewer sees the north face at identity,
# hence the 180 degree "fixed" rotation (same as item/generated).
HANDHELD = {  # long tools laid diagonally in the XY plane like a vanilla sprite (handle bottom-left, head top-right)
    "thirdperson_righthand": tf((0, -90, 55), (0, 4, 0.5), 0.85),
    "firstperson_righthand": tf((0, -90, 25), (1.13, 3.2, 1.13), 0.68),
    "ground": tf((0, 0, 0), (0, 2, 0), 0.5),
    "fixed": tf((0, 180, 0), (0, 0, 0), 1),
    "head": tf((0, 180, 0), (0, 13, 7), 1),
}
# Flat-ish things looked at (phone, binders): upright in first person, the face turned to the eye; in third person
# held in the palm tilted towards the face (+40 degrees about X lifts the top and turns the face back/up).
HELD_FLAT = {
    "thirdperson_righthand": tf((40, 0, 0), (0, 2, 1.5), 0.5),
    "firstperson_righthand": tf((-12, -22, 0), (-3, 4.6, 0.6), 0.42),
    "ground": tf((0, 0, 0), (0, 2, 0), 0.5),
    "fixed": tf((0, 180, 0), (0, 0, 0), 1),
    "head": tf((0, 180, 0), (0, 13, 7), 1),
}


def entry_model(name, display, particle):
    """models/item/<name>.json: builtin/entity so ToolItemRenderer draws the icon (GUI) or the 3D model (elsewhere).
    gui_light front: the 2D icon is lit flat like any vanilla sprite."""
    dump(A / "models/item" / f"{name}.json", {
        "parent": "minecraft:builtin/entity",
        "gui_light": "front",
        "textures": {"particle": particle},
        "display": display,
    })


def icon_model(name, sprite):
    dump(A / "models/item/icon" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": sprite}})


def save_3d(name, model, tex):
    tex.save(A / "textures/item/3d" / f"{name}.png")
    dump(A / "models/item/3d" / f"{name}.json", model.json())


# ================================================================================================================= PHONE

TIERS = [
    # key, back colours (top, bottom), side frame, front lip (None = plastic phone without metal rim), bezel,
    # wallpaper (top, bottom), accent, body depth, back style, clock text
    dict(key="basic", body="858c99", body2="676e7a", side="6f7783", rim=None, bezel="2a2e36",
         wall=("3d5176", "151b2a"), accent="9aa3b5", depth=1.5, style="basic", clock="12:30"),
    dict(key="plus", body="45a552", body2="2c7a37", side="3a8d45", rim=None, bezel="16191d",
         wall=("2e9a62", "0f3523"), accent="7ddc8a", depth=1.3, style="dots", clock="10:15"),
    dict(key="pro", body="3478cc", body2="1f4c8c", side="a9bdd3", rim="cfdbe9", bezel="0c0e12",
         wall=("2f80ed", "0c1a44"), accent="8fc1ff", depth=1.1, style="brushed", clock="08:42"),
    dict(key="ultra", body="8446cc", body2="45207a", side="bba8dc", rim="ddd2f3", bezel="0a0a10",
         wall=("a55ff0", "1f0d42"), accent="e0c4ff", depth=1.0, style="sheen", clock="11:11"),
    dict(key="max", body="e8892f", body2="a2500f", side="e2b64a", rim="f5d985", bezel="0a0a0d",
         wall=("f7a75a", "3f1906"), accent="ffd27a", depth=1.0, style="brushed_gold", clock="07:07"),
    dict(key="elite", body="b9282d", body2="5e0c10", side="cfd4db", rim="f1f4f8", bezel="060607",
         wall=("e8504f", "22050a"), accent="ffb3b0", depth=0.9, style="carbon", clock="23:59"),
    dict(key="masterball", body="6c31a6", body2="40165f", side="e0b33c", rim="f7da72", bezel="07050b",
         wall=("8f3fd0", "2a0b4a"), accent="ff7ad0", depth=1.0, style="masterball", clock="10:00"),
]

# (key, colour, min tier, glyph 6x6) - same keys/colours as the phone apps (client/screen/PhoneScreen) + tier perks.
G = {
    "market": ["#.....", "######", ".####.", ".####.", "......", ".#..#."],
    "messages": [".####.", "######", "######", ".####.", ".#....", "#....."],
    "collection": [".####.", ".#..##", ".#..##", ".#..##", ".####.", "..###."],
    "shop": ["######", "#.#.#.", "######", "#....#", "#.##.#", "#.##.#"],
    "sell": ["..##..", ".####.", "##..##", "##..##", ".####.", "..##.."],
    "stats": [".....#", "...#.#", "...#.#", ".#.#.#", ".#.#.#", "######"],
    "titles": [".####.", "######", "######", ".####.", ".#..#.", "#....#"],
    "advancements": ["..##..", "..##..", "######", ".####.", ".#..#.", "#....#"],
    "friends": ["..##..", "..##..", "......", ".####.", "######", "######"],
    "upgrades": ["..##..", ".####.", "######", "..##..", "..##..", "..##.."],
    "settings": ["#.##.#", ".####.", "##..##", "##..##", ".####.", "#.##.#"],
    "prices": ["......", "....##", "#..#..", ".##...", "......", "######"],
    "displays": ["######", "#....#", "#.##.#", "#.##.#", "#....#", "######"],
    "furniture": ["#.....", "#.....", "#.....", "######", "#....#", "#....#"],
    "league": ["######", "######", ".####.", "..##..", "..##..", ".####."],
    "loans": ["..##..", ".####.", "######", "#.#.#.", "#.#.#.", "######"],
    "grade": ["######", "#....#", "######", "#....#", "#....#", "######"],
    "roulette": [".####.", "##..##", "#.##.#", "#.##.#", "##..##", ".####."],
    "wholesale": ["######", "#.##.#", "######", "#....#", "#....#", "######"],
    "call": ["##....", "##....", ".#....", ".##...", "..####", "...###"],
}
APPS = [
    ("market", "d39b1a", 0), ("messages", "2e8be8", 0), ("collection", "b3262e", 0), ("shop", "2f6fb0", 0),
    ("sell", "d9472b", 0), ("stats", "7c4dff", 0), ("titles", "8e44ad", 0), ("advancements", "e07a1f", 0),
    ("friends", "26a69a", 0), ("upgrades", "b3262e", 0), ("settings", "607d8b", 0),
    ("prices", "2e9c5a", 1),
    ("displays", "5c6bc0", 2), ("furniture", "8d6e63", 2), ("league", "c62828", 2), ("loans", "b8860b", 2),
    ("grade", "1e8fa8", 3),
    ("roulette", "e0398a", 4),
    ("wholesale", "8d5a2b", 5),
    ("call", "2ea44f", 6),
]

DIGITS = {
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"], "3": ["###", "..#", "###", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "###"],
    ":": [".", "#", ".", "#", "."],
}


def text_w(s, scale):
    return sum((len(DIGITS[ch][0]) + 1) * scale for ch in s) - scale


def draw_text(tex, s, x, y, c, scale=1):
    for ch in s:
        glyph(tex, DIGITS[ch], x, y, c, scale)
        x += (len(DIGITS[ch][0]) + 1) * scale


def app_icon(tex, x, y, key, col, badge=False):
    """10x10 rounded app icon with a vertical gradient and a white 6x6 glyph."""
    base = hexc(col)
    for j in range(10):
        for i in range(10):
            if (i in (0, 9)) and (j in (0, 9)):
                continue
            tex.set(x + i, y + j, mix(shade(base, 1.22), shade(base, 0.82), j / 9))
    for i in range(1, 9):  # top highlight
        tex.blend(x + i, y, (255, 255, 255, 255), 0.25)
    glyph(tex, G[key], x + 2, y + 2, (255, 255, 255, 255))
    if badge:
        tex.disc(x + 9, y + 1, 2.2, hexc("ff3b30"))
        tex.set(x + 9, y + 1, (255, 255, 255, 255))


def wallpaper(tex, t, x0, y0, w, h, tier_idx):
    top, bot = hexc(t["wall"][0]), hexc(t["wall"][1])
    for j in range(h):
        for i in range(w):
            tex.set(x0 + i, y0 + j, mix(top, bot, j / (h - 1)))
    rng = random.Random(1000 + tier_idx)
    acc = hexc(t["accent"])
    if t["style"] == "masterball":
        for _ in range(9):  # soft pink and violet dots
            cx, cy, r = x0 + rng.uniform(4, w - 4), y0 + rng.uniform(4, h - 4), rng.uniform(2, 6)
            tex.disc(cx, cy, r, acc if rng.random() < 0.6 else hexc("c58cff"), 0.28)
    else:
        for _ in range(3):  # big soft circles
            cx, cy, r = x0 + rng.uniform(0, w), y0 + rng.uniform(h * 0.15, h * 0.9), rng.uniform(w * 0.3, w * 0.6)
            tex.disc(cx, cy, r, acc, 0.12)


def front_face(tex, t, ti, X, Y):
    """Front of the phone (56x104 px, 8 px per model unit): bezel + screen + status bar + clock + app grid + dock."""
    W, H = 56, 104
    bez = hexc(t["bezel"])
    edge = hexc(t["rim"] or t["side"])
    tex.rect(X, Y, W, H, bez)
    tex.outline(X, Y, W, H, shade(edge, 0.85))
    for (cx, cy) in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):  # rounded corners in the frame colour
        tex.set(X + cx, Y + cy, shade(edge, 0.7))
    small = ti < 2
    if small:  # chunky bezels: 3 x 3 icons
        sx, sy, sw, sh, cols, rows, gap, dock_n = 6, 14, 44, 72, 3, 3, 4, 3
    else:
        sx, sy, sw, sh, cols, rows, gap, dock_n = 3, 4, 50, 96, 4, 4, 2, 4
    sx, sy = X + sx, Y + sy
    wallpaper(tex, t, sx, sy, sw, sh, ti)
    white = (255, 255, 255, 255)
    # status bar: signal bars, battery
    for b in range(4):
        tex.rect(sx + 2 + b * 2, sy + 4 - b, 1, b + 1, white)
    tex.outline(sx + sw - 10, sy + 1, 7, 4, white)
    tex.set(sx + sw - 3, sy + 2, white)
    tex.set(sx + sw - 3, sy + 3, white)
    tex.rect(sx + sw - 9, sy + 2, 1 + ti * 4 // 6, 2, hexc("7dff9a") if ti < 6 else hexc("ffd34d"))
    if ti == 2:  # notch
        tex.rect(sx + sw // 2 - 7, sy, 14, 4, bez)
    elif ti >= 3:  # punch-hole camera
        tex.disc(sx + sw / 2, sy + 3, 1.8, hexc("050507"))
    # clock widget
    if small:
        tw = text_w(t["clock"], 1)
        draw_text(tex, t["clock"], sx + (sw - tw) // 2, sy + 8, white, 1)
        for i in range(16):  # date line
            tex.blend(sx + sw // 2 - 8 + i, sy + 15, white, 0.55 if i % 4 else 0.2)
        grid_y, pitch = sy + 21, 12
    else:
        tw = text_w(t["clock"], 2)
        draw_text(tex, t["clock"], sx + (sw - tw) // 2, sy + 9, white, 2)
        for i in range(22):  # date line
            tex.blend(sx + sw // 2 - 11 + i, sy + 22, white, 0.55 if i % 4 else 0.2)
        grid_y, pitch = sy + 31, 12
    apps = [a for a in APPS if a[2] <= ti]
    dock, grid = apps[:dock_n], apps[dock_n:]
    margin = (sw - cols * 10 - (cols - 1) * gap) // 2
    for n, (key, col, _) in enumerate(grid[:cols * rows]):
        cx = sx + margin + (n % cols) * (10 + gap)
        cy = grid_y + (n // cols) * pitch
        app_icon(tex, cx, cy, key, col)
    # page dots + dock
    dots_y = grid_y + rows * pitch + (1 if small else 2)
    for d in range(3):
        tex.set(sx + sw // 2 - 3 + d * 3, dots_y, white if d == 0 else shade(white, 0.6))
    dock_y = sy + sh - 12
    for j in range(12):
        for i in range(1, sw - 1):
            tex.blend(sx + i, dock_y + j, white, 0.18)
    dgap = (sw - dock_n * 10) // (dock_n + 1)
    for n, (key, col, _) in enumerate(dock):
        app_icon(tex, sx + dgap + n * (10 + dgap), dock_y + 1, key, col, badge=(key == "messages"))
    # glass sheen: a soft diagonal band
    for j in range(sh):
        for i in range(sw):
            d = (i + j * 0.45) - sw * 0.55
            if 0 <= d < 7:
                tex.blend(sx + i, sy + j, white, 0.07)
    # bezel details
    if small:
        tex.rect(X + 22, Y + 6, 12, 2, hexc("050505"))  # earpiece
        tex.disc(X + 38, Y + 7, 1.3, hexc("1b2a4a"))  # front camera
        tex.ring(X + 28, Y + 95, 4.2, 5.6, shade(edge, 1.15))  # home button ring (the 3D button sits on top)
    else:
        tex.rect(X + 22, Y + 1, 12, 1, hexc("1d1f24"))  # speaker slit
        tex.rect(X + 21, Y + 101, 14, 1, shade(white, 0.85))  # home indicator


def back_face(tex, t, ti, X, Y):
    """Back of the phone (56x104 px) as seen from behind (u grows towards -X)."""
    W, H = 56, 104
    b1, b2 = hexc(t["body"]), hexc(t["body2"])
    rng = random.Random(77 + ti)
    style = t["style"]
    rows = [rng.uniform(-5, 5) for _ in range(H)]
    for j in range(H):
        for i in range(W):
            c = mix(b1, b2, j / (H - 1))
            n = rng.uniform(-4, 4)
            if style in ("brushed", "brushed_gold"):
                n = rows[j] + n * 0.4
            elif style == "dots" and i % 4 == 1 and j % 4 == 1:
                n += 14
            elif style == "sheen":
                d = abs((i - j * 0.55) - 2)
                if d < 9:
                    n += 26 * (1 - d / 9)
            elif style == "carbon":
                cell = ((i // 3) + (j // 3)) % 2
                stripe = (i + j) % 3 if cell else (i - j) % 3
                c = shade(mix(hexc("2a2a2e"), b1, 0.35), 0.8 + stripe * 0.12)
            tex.set(X + i, Y + j, tuple(clamp(c[k] + n) for k in range(3)) + (255,))
    if style == "masterball":  # violet top with pink circles, black band, white bottom
        for j in range(H):
            for i in range(W):
                if j >= 56:
                    tex.set(X + i, Y + j, mix(hexc("f4f4f6"), hexc("d4d4da"), (j - 56) / 47))
                elif j >= 48:
                    tex.set(X + i, Y + j, hexc("141218"))
        tex.disc(X + 14, Y + 30, 7, hexc("e0559f"))
        tex.disc(X + 42, Y + 30, 7, hexc("e0559f"))
        tex.disc(X + 14, Y + 30, 4.5, hexc("f07dbb"))
        tex.disc(X + 42, Y + 30, 4.5, hexc("f07dbb"))
        tex.disc(X + 28, Y + 52, 10, hexc("141218"))
        tex.disc(X + 28, Y + 52, 7.5, hexc("e0b33c"))
    elif style == "brushed_gold":
        tex.rect(X + 4, Y + 82, W - 8, 2, hexc("f5d985"))
    if style != "masterball":
        acc = shade(hexc(t["rim"] or t["accent"]), 1.05)
        tex.ring(X + 28, Y + 60, 6, 8, acc)  # the brand's ball emblem
        tex.rect(X + 21, Y + 59, 14, 2, acc)
        tex.disc(X + 28, Y + 60, 2.2, acc)
    for k in range(5):  # tiny "regulatory" marks
        tex.rect(X + 15 + k * 6, Y + 95, 4, 1, shade(hexc(t["body2"]), 0.7 if style != "masterball" else 0.5))
    if style == "basic":  # battery cover seam + speaker grille
        tex.outline(X + 4, Y + 38, W - 8, 62, shade(b2, 0.75))
        for gy in range(3):
            for gx in range(6):
                tex.set(X + 20 + gx * 3, Y + 86 + gy * 3, shade(b2, 0.55))
    tex.outline(X, Y, W, H, shade(hexc(t["side"]), 0.8))


def phone(ti, t):
    S = 128
    tex = Tex(S, S, 500 + ti)
    front_face(tex, t, ti, 0, 0)
    back_face(tex, t, ti, 56, 0)
    side_style = "brushed_v" if t["rim"] else "noise"
    side = tex.material(112, 0, 16, 16, hexc(t["side"]), 5, side_style)
    rim = tex.material(0, 104, 16, 16, hexc(t["rim"] or t["side"]), 4, "brushed_h")
    button = tex.material(16, 104, 16, 16, shade(hexc(t["side"]), 0.8), 4)
    dark = tex.material(32, 104, 16, 16, hexc("1a1b20"), 4)
    bump = tex.material(48, 104, 16, 16, shade(hexc(t["body2"]), 0.6) if t["style"] != "masterball" else hexc("e0b33c"), 4)
    # lens: dark glass, coloured coating ring, glare
    lens = (64, 104, 16, 16)
    tex.rect(64, 104, 16, 16, hexc("15161b"))
    tex.disc(72, 112, 7.5, shade(hexc(t["rim"] or t["side"]), 0.9))
    tex.disc(72, 112, 6, hexc("0b0c10"))
    tex.disc(72, 112, 4, hexc("1d2f55"))
    tex.disc(72, 112, 2, hexc("06070a"))
    tex.disc(70, 110, 1.2, (255, 255, 255, 255), 0.8)
    flash = (80, 104, 16, 16)
    tex.rect(80, 104, 16, 16, hexc("f2ecd0"))
    tex.disc(88, 112, 5, hexc("fff8d8"))
    home = (96, 104, 16, 16)  # home button (front) / masterball centre button (back)
    if t["style"] == "masterball":
        tex.rect(96, 104, 16, 16, hexc("141218"))
        tex.disc(104, 112, 7.5, hexc("f4f4f6"))
        tex.ring(104, 112, 4.5, 6, hexc("c9c9d2"))
    else:
        tex.rect(96, 104, 16, 16, shade(hexc(t["bezel"]), 1.25))
        tex.ring(104, 112, 5.5, 7.5, shade(hexc(t["side"]), 1.1))
        tex.outline(101, 109, 6, 6, shade(hexc(t["side"]), 1.3))
    gold = tex.material(112, 104, 16, 16, hexc("e0b33c"), 6, "brushed_h")

    m = Model(f"{NS}:item/3d/tcg_phone_{t['key']}", S)
    d = t["depth"]
    z0, z1 = 8 - d / 2, 8 + d / 2
    x0, x1, y0, y1 = 4.5, 11.5, 1.5, 14.5
    m.box((x0, y0, z0), (x1, y1, z1), {"south": (0, 0, 56, 104), "north": (56, 0, 56, 104),
                                        "east": side, "west": side, "up": side, "down": side})
    lip = 0.07
    if t["rim"]:  # metal frame lip around the glass
        rr = 0.22
        m.box((x0, y1 - rr, z1), (x1, y1, z1 + lip), all6(rim, north=None))
        m.box((x0, y0, z1), (x1, y0 + rr, z1 + lip), all6(rim, north=None))
        m.box((x0, y0 + rr, z1), (x0 + rr, y1 - rr, z1 + lip), all6(rim, north=None, up=None, down=None))
        m.box((x1 - rr, y0 + rr, z1), (x1, y1 - rr, z1 + lip), all6(rim, north=None, up=None, down=None))
    else:  # physical home button (front bottom bezel)
        m.box((7.35, 1.98, z1), (8.65, 3.28, z1 + 0.12), all6(button, south=home, north=None))
    # side buttons: power (right), volume (left)
    zc = 8
    m.box((x1, 10.0, zc - 0.25), (x1 + 0.12, 11.3, zc + 0.25), all6(button, west=None))
    m.box((x0 - 0.12, 10.5, zc - 0.25), (x0, 11.3, zc + 0.25), all6(button, east=None))
    m.box((x0 - 0.12, 9.3, zc - 0.25), (x0, 10.1, zc + 0.25), all6(button, east=None))
    if ti == 0:  # retro antenna
        m.box((10.0, y1, 7.6), (10.8, 15.5, 8.4), all6(dark, down=None))
        m.box((10.1, 15.5, 7.7), (10.7, 15.8, 8.3), all6(dark, down=None))

    def lens_at(x, y, s, base_z):
        m.box((x, y, base_z - 0.2), (x + s, y + s, base_z), all6(dark, north=lens, south=None))

    # cameras on the back (north face, -Z); seen from behind the top-left corner is +X
    if ti == 0:
        lens_at(9.4, 12.6, 1.0, z0)
    elif ti == 1:
        lens_at(9.2, 12.4, 1.2, z0)
        m.box((8.3, 13.0, z0 - 0.1), (8.9, 13.6, z0), all6(dark, north=flash, south=None))
    else:
        bz = z0 - 0.22
        if ti == 2:
            plate, lenses, fl = (8.6, 10.2, 10.8, 13.8), [(9.0, 12.2), (9.0, 10.5)], None
            s = 1.4
        elif ti in (3, 4):
            plate, lenses, fl = (7.2, 10.2, 10.8, 13.8), [(9.2, 12.2), (9.2, 10.4), (7.5, 11.3)], (7.75, 13.0)
            s = 1.4 if ti == 3 else 1.5
        elif ti == 5:
            plate, lenses, fl = (7.0, 9.9, 10.9, 13.8), [(9.2, 12.1), (7.3, 12.1), (9.2, 10.2), (7.3, 10.2)], None
            s = 1.5
        else:
            plate, lenses, fl = (5.4, 12.2, 10.6, 13.8), [(9.2, 12.4), (7.4, 12.4), (5.6, 12.4)], None
            s = 1.2
        px0, py0, px1, py1 = plate
        m.box((px0, py0, bz), (px1, py1, z0), all6(gold if ti == 6 else bump, south=None))
        for (lx, ly) in lenses:
            lens_at(lx, ly, s, bz)
        if fl:
            m.box((fl[0], fl[1], bz - 0.1), (fl[0] + 0.6, fl[1] + 0.6, bz), all6(dark, north=flash, south=None))
        if ti == 6:  # the ball's centre button on the back band
            m.box((7.0, 7.0, z0 - 0.3), (9.0, 9.0, z0), all6(dark, north=home, south=None))
    save_3d(f"tcg_phone_{t['key']}", m, tex)


def phone_icon(ti, t):
    """16x16 GUI icon per tier: frame in the tier colour, screen with as many app squares as the tier unlocks."""
    tex = Tex(16, 16, 900 + ti)
    frame = hexc(t["side"] if t["key"] != "masterball" else "6c31a6")
    hi, lo = shade(frame, 1.25), shade(frame, 0.6)
    for y in range(1, 15):
        for x in range(3, 13):
            tex.set(x, y, frame)
    for y in range(2, 14):
        tex.set(3, y, hi)
        tex.set(12, y, lo)
    for x in range(4, 12):
        tex.set(x, 1, hi)
        tex.set(x, 14, lo)
    tex.set(3, 1, T)
    tex.set(12, 1, T)
    tex.set(3, 14, T)
    tex.set(12, 14, T)
    bez = hexc(t["bezel"])
    tex.rect(4, 2, 8, 12, bez)
    top, bot = hexc(t["wall"][0]), hexc(t["wall"][1])
    for y in range(3, 12):
        for x in range(4, 12):
            tex.set(x, y, mix(top, bot, (y - 3) / 8))
    count = [5, 6, 7, 8, 9, 9, 9][ti]
    for n in range(count):
        key, col, _ = APPS[n]
        x, y = 4 + (n % 3) * 3, 3 + (n // 3) * 3
        c = hexc(col)
        tex.set(x, y, shade(c, 1.2))
        tex.set(x + 1, y, c)
        tex.set(x, y + 1, c)
        tex.set(x + 1, y + 1, shade(c, 0.8))
    if ti < 2:
        tex.set(7, 13, shade(frame, 1.1))
        tex.set(8, 13, shade(frame, 1.1))
    else:
        tex.rect(6, 12, 4, 1, (220, 220, 225, 255))
    if t["key"] == "masterball":  # gold corners + pink jewel
        for (x, y) in ((4, 1), (11, 1), (4, 14), (11, 14), (3, 2), (12, 2), (3, 13), (12, 13)):
            tex.set(x, y, hexc("e0b33c"))
        tex.set(7, 1, hexc("ff7ad0"))
        tex.set(8, 1, hexc("ff7ad0"))
    elif t["key"] == "max":
        tex.set(7, 1, hexc("ffd27a"))
        tex.set(8, 1, hexc("ffd27a"))
    tex.save(A / "textures/item/phone_icon" / f"{t['key']}.png")
    icon_model(f"tcg_phone_{t['key']}", f"{NS}:item/phone_icon/{t['key']}")


def phones():
    for ti, t in enumerate(TIERS):
        phone(ti, t)
        phone_icon(ti, t)
    # the entry model: particles use the basic icon
    entry_model("tcg_phone", HELD_FLAT, f"{NS}:item/tcg_phone")


# ============================================================================================================ TOOLS

def mop():
    tex = Tex(64, 64, 11)
    handle = tex.material(0, 0, 16, 16, hexc("3a7bc8"), 6, "brushed_v")
    grip = tex.material(16, 0, 16, 16, hexc("222428"), 5, "ribbed")
    steel = tex.material(32, 0, 16, 16, hexc("b9bec6"), 6, "brushed_v")
    plastic = tex.material(48, 0, 16, 16, hexc("e2b03a"), 5)
    cotton = tex.material(0, 16, 16, 16, hexc("e8e6df"), 10, "brushed_v")
    cotton2 = tex.material(16, 16, 16, 16, hexc("d6d2c6"), 10, "brushed_v")
    tip = tex.material(32, 16, 16, 16, hexc("c9c4b4"), 8)
    m = Model(f"{NS}:item/3d/mop", 64)
    R = (-45, "z", (8, 8, 8))
    m.box((7.5, -3.6, 7.5), (8.5, 14.6, 8.5), all6(handle), R)
    m.box((7.3, -4.2, 7.3), (8.7, 1.0, 8.7), all6(grip), R)
    m.box((7.75, -4.8, 7.75), (8.25, -4.2, 8.25), all6(steel), R)  # hanging loop
    m.box((7.2, 13.6, 7.2), (8.8, 14.8, 8.8), all6(steel), R)  # collar
    m.box((4.6, 14.8, 6.8), (11.4, 16.0, 9.2), all6(plastic), R)  # head bracket
    strands = [(4.7, 19.2, 0), (5.7, 20.3, 1), (6.7, 19.7, 0), (7.7, 20.6, 1), (8.7, 19.9, 0), (9.7, 20.4, 1), (10.5, 19.4, 0)]
    for i, (x, end, back) in enumerate(strands):
        z = (6.9, 8.1) if back == 0 else (7.9, 9.1)
        mat = cotton if i % 2 == 0 else cotton2
        m.box((x, 16.0, z[0]), (x + 0.85, end, z[1]), all6(mat, up=None, down=tip), R)
    save_3d("mop", m, tex)
    icon_model("mop", f"{NS}:item/mop")
    entry_model("mop", HANDHELD, f"{NS}:item/mop")


def repair_kit():
    tex = Tex(64, 64, 12)
    red = tex.material(0, 0, 16, 16, hexc("c62c27"), 6)
    red_dark = tex.material(16, 0, 16, 16, hexc("8c1e1c"), 5)
    metal = tex.material(32, 0, 16, 16, hexc("c3c7cf"), 6, "brushed_h")
    black = tex.material(48, 0, 16, 16, hexc("2a2a2f"), 4, "ribbed")
    # lid top with a stamped wrench + cross emblem
    lid_top = (0, 16, 32, 16)
    tex.material(0, 16, 32, 16, hexc("d23a33"), 5)
    yel = hexc("ebcd50")
    tex.rect(13, 19, 6, 10, yel)
    tex.rect(10, 22, 12, 4, yel)
    tex.rect(14, 20, 4, 8, hexc("f6e27e"))
    tex.outline(0, 16, 32, 16, hexc("a32420"))
    # front plate label
    label = (32, 16, 16, 16)
    tex.rect(32, 16, 16, 16, yel)
    tex.outline(32, 16, 16, 16, hexc("b8961e"))
    tex.rect(38, 19, 4, 10, hexc("c62c27"))
    tex.rect(35, 22, 10, 4, hexc("c62c27"))
    m = Model(f"{NS}:item/3d/repair_kit", 64)
    m.box((2, 1.2, 5), (14, 8, 11), all6(red, up=None))  # box
    m.box((1.8, 0.6, 4.8), (14.2, 1.2, 11.2), all6(red_dark))  # base trim
    m.box((1.8, 8, 4.8), (14.2, 10, 11.2), all6(red_dark, up=lid_top))  # lid
    m.box((4, 7, 11.2), (5.2, 9, 11.45), all6(metal, north=None))  # latches
    m.box((10.8, 7, 11.2), (12, 9, 11.45), all6(metal, north=None))
    m.box((6.4, 3, 11), (9.6, 5.6, 11.12), all6(label, north=None))  # label plate
    m.box((5, 10, 7.5), (6, 12, 8.5), all6(metal, up=None, down=None))  # handle posts
    m.box((10, 10, 7.5), (11, 12, 8.5), all6(metal, up=None, down=None))
    m.box((4.6, 12, 7.3), (11.4, 13.1, 8.7), all6(black))  # grip
    m.box((1.6, 4, 7.4), (1.8, 6, 8.6), all6(metal, east=None))  # hinge pins
    m.box((14.2, 4, 7.4), (14.4, 6, 8.6), all6(metal, west=None))
    save_3d("repair_kit", m, tex)
    icon_model("repair_kit", f"{NS}:item/repair_kit")
    entry_model("repair_kit", {
        # first person: a small box, front turned to the eye with the lid visible
        "firstperson_righthand": tf((12, -32, 0), (-1, 2.2, 0), 0.5),
        # third person: carried like a suitcase (front facing sideways, long side along the walking direction)
        "thirdperson_righthand": tf((90, 90, 0), (0, 1, -1.5), 0.55),
        "ground": tf((0, 0, 0), (0, 2, 0), 0.45),
        "fixed": tf((0, 180, 0), (0, 0, 0), 0.75),
        "head": tf((0, 180, 0), (0, 13, 7), 0.8),
    }, f"{NS}:item/repair_kit")


def deodorant_spray():
    tex = Tex(64, 64, 13)
    silver = tex.material(0, 0, 16, 16, hexc("c8ccd3"), 6, "brushed_h")
    cap = tex.material(16, 0, 16, 16, hexc("f2f4f6"), 4)
    dark = tex.material(32, 0, 16, 16, hexc("2a2d33"), 4)
    blue = tex.material(48, 0, 16, 16, hexc("1f6e9e"), 5)
    # label wrapped around the can: blue gradient, white band with a green leaf and a "fresh" wave
    label = (0, 16, 16, 40)
    for j in range(40):
        for i in range(16):
            tex.set(i, 16 + j, mix(hexc("8fd3f7"), hexc("2a8ccc"), j / 39))
    tex.rect(0, 30, 16, 12, hexc("ffffff"))
    for i in range(16):
        y = 35 + int(2 * math.sin(i / 2.2))
        tex.set(i, y, hexc("3fa7e0"))
    tex.disc(8, 36, 3.2, hexc("7bc96f"))
    tex.rect(8, 33, 1, 6, hexc("4e9a44"))
    for j in range(16, 56, 4):
        tex.blend(1, j, (255, 255, 255, 255), 0.5)
    m = Model(f"{NS}:item/3d/deodorant_spray", 64)
    # chamfered "round" can: two crossed boxes
    m.box((5, 1, 6), (11, 10.5, 10), all6(label, up=blue, down=dark))
    m.box((6, 1, 5), (10, 10.5, 11), all6(label, up=blue, down=dark))
    m.box((5.4, 0.4, 6.4), (10.6, 1, 9.6), all6(silver))  # bottom rim
    m.box((6.4, 0.4, 5.4), (9.6, 1, 10.6), all6(silver))
    m.box((5.5, 10.5, 6.5), (10.5, 11.3, 9.5), all6(silver, down=None))  # shoulder
    m.box((6.5, 10.5, 5.5), (9.5, 11.3, 10.5), all6(silver, down=None))
    m.box((6.6, 11.3, 6.6), (9.4, 13.2, 9.4), all6(cap, down=None))  # actuator cap
    m.box((7.6, 12.2, 9.4), (8.4, 12.9, 10.1), all6(dark, north=None))  # nozzle (front)
    save_3d("deodorant_spray", m, tex)
    icon_model("deodorant_spray", f"{NS}:item/deodorant_spray")
    entry_model("deodorant_spray", {
        # first person: upright can, nozzle towards the middle of the screen
        "firstperson_righthand": tf((0, -70, 8), (0.5, 2.6, 0.5), 0.6),
        # third person: upright in the fist, nozzle forward
        "thirdperson_righthand": tf((90, 180, 0), (0, 0.5, 1.5), 0.55),
        "ground": tf((0, 0, 0), (0, 2, 0), 0.5),
        "fixed": tf((0, 180, 0), (0, 0, 0), 1),
        "head": tf((0, 180, 0), (0, 13, 7), 0.8),
    }, f"{NS}:item/deodorant_spray")


def nerd_gloves():
    tex = Tex(64, 64, 14)
    leather = tex.material(0, 0, 16, 16, hexc("8b5a2b"), 7)
    leather_d = tex.material(16, 0, 16, 16, hexc("6e4520"), 6)
    cuff = tex.material(32, 0, 16, 16, hexc("e6e6e6"), 6, "ribbed")
    tipc = tex.material(48, 0, 16, 16, hexc("5e3b1a"), 5)
    # back of the hand with three stitched lines
    back = (0, 16, 24, 24)
    tex.material(0, 16, 24, 24, hexc("8b5a2b"), 7)
    for k in range(3):
        x = 5 + k * 6
        for y in range(18, 34, 2):
            tex.set(x, y, hexc("d8c39a"))
    tex.outline(0, 16, 24, 24, hexc("6e4520"))
    m = Model(f"{NS}:item/3d/nerd_gloves", 64)
    m.box((4, 1, 6.5), (11, 4, 9.5), all6(cuff))  # cuff
    m.box((4.5, 4, 6.8), (10.5, 10, 9.2), all6(leather, south=back, north=back))  # palm
    fingers = [(4.6, 13.6), (6.1, 14.6), (7.6, 14.2), (9.1, 12.8)]
    for (x, top) in fingers:
        m.box((x, 10, 7.1), (x + 1.35, top - 0.5, 8.9), all6(leather, up=None))
        m.box((x, top - 0.5, 7.1), (x + 1.35, top, 8.9), all6(tipc, down=None))
    m.box((3.2, 5, 7.2), (4.6, 9.2, 8.8), all6(leather_d), (22.5, "z", (4.6, 5, 8)))  # thumb
    save_3d("nerd_gloves", m, tex)
    icon_model("nerd_gloves", f"{NS}:item/nerd_gloves")
    entry_model("nerd_gloves", {
        "firstperson_righthand": tf((0, -25, 5), (-1, 3, 0), 0.5),
        "thirdperson_righthand": tf((0, 0, 0), (0, 3, 1), 0.55),
        "ground": tf((0, 0, 0), (0, 2, 0), 0.5),
        "fixed": tf((0, 180, 0), (0, 0, 0), 1),
        "head": tf((0, 180, 0), (0, 13, 7), 1),
    }, f"{NS}:item/nerd_gloves")


def manager_goggles():
    """Designed in WORN orientation (head transform = identity): HeadFeatureRenderer scales the model by 0.625, so the
    head spans 1.6..14.4 model units on every axis; the face is the north side (z = 1.6), the eyes at y 6.4..8.0."""
    tex = Tex(64, 64, 15)
    frame = tex.material(0, 0, 16, 16, hexc("3a2a1a"), 5)
    brass = tex.material(16, 0, 16, 16, hexc("c99a3a"), 7, "brushed_h")
    strap = tex.material(32, 0, 16, 16, hexc("2b2b30"), 4, "ribbed")
    led = (48, 0, 16, 16)
    tex.rect(48, 0, 16, 16, hexc("ff3b30"))
    tex.disc(56, 8, 4, hexc("ffb0a8"))
    lens = (0, 16, 32, 24)
    tex.rect(0, 16, 32, 24, hexc("c99a3a"))
    tex.outline(0, 16, 32, 24, hexc("8a6420"))
    for j in range(20):
        for i in range(28):
            tex.set(2 + i, 18 + j, mix(hexc("d8f4ff"), hexc("3aa6d8"), (i + j) / 47))
    for k in range(6):  # glare
        tex.set(6 + k, 21 + k // 2, (255, 255, 255, 255))
        tex.set(7 + k, 21 + k // 2, (255, 255, 255, 255))
    for i in range(28):  # HUD line (the manager view)
        if i % 3:
            tex.blend(2 + i, 31, hexc("7dff9a"), 0.55)
    m = Model(f"{NS}:item/3d/manager_goggles", 64)
    for x in (2.8, 9.0):
        m.box((x, 5.4, 0.0), (x + 4.2, 9.0, 0.9), all6(frame, north=lens))
        m.box((x - 0.2, 9.0, 0.1), (x + 4.4, 9.3, 0.8), all6(brass, north=brass))  # brow rim
    m.box((7.0, 6.8, 0.3), (9.0, 7.6, 0.8), all6(brass))  # bridge
    m.box((0.6, 6.4, 0.4), (2.8, 8.0, 0.8), all6(strap))
    m.box((13.2, 6.4, 0.4), (15.4, 8.0, 0.8), all6(strap))
    m.box((0.4, 6.4, 0.4), (0.8, 8.0, 15.6), all6(strap))
    m.box((15.2, 6.4, 0.4), (15.6, 8.0, 15.6), all6(strap))
    m.box((0.8, 6.4, 15.2), (15.2, 8.0, 15.6), all6(strap))
    m.box((15.6, 6.2, 7.0), (15.8, 8.2, 9.0), all6(brass))  # buckle
    m.box((12.2, 9.3, 0.3), (12.9, 9.7, 0.8), all6(led))  # status LED
    save_3d("manager_goggles", m, tex)
    icon_model("manager_goggles", f"{NS}:item/manager_goggles")
    entry_model("manager_goggles", {
        # head: identity (the model is built on the head); in hand the lenses (north) are turned to the eye
        "firstperson_righthand": tf((8, 150, 0), (-1, 3, 0), 0.42),
        "thirdperson_righthand": tf((90, 0, 0), (0, 2, 1), 0.35),
        "ground": tf((0, 0, 0), (0, 1, 0), 0.4),
        "fixed": tf((0, 0, 0), (0, 0, 0), 0.6),
    }, f"{NS}:item/manager_goggles")


def binder(name, cover, cover_dark, emblem):
    tex = Tex(64, 64, 16 + len(name))
    c1, c2 = hexc(cover), hexc(cover_dark)
    gold = hexc("e0b33c")

    def cover_art(x0):
        for j in range(28):
            for i in range(24):
                n = tex.rng.uniform(-6, 6)
                c = c1 if (i + j) % 7 else c2
                tex.set(x0 + i, j, tuple(clamp(c[k] + n) for k in range(3)) + (255,))
        tex.outline(x0, 0, 24, 28, shade(c2, 0.8))
        tex.outline(x0 + 2, 2, 20, 24, shade(c1, 1.15))

    cover_art(0)
    emblem(tex, 12, 14)
    front = (0, 0, 24, 28)
    cover_art(24)
    back = (24, 0, 24, 28)
    # spine (west face: u grows towards +Z) with a label and ring rivets
    for j in range(28):
        for i in range(8):
            tex.set(48 + i, j, shade(c2, 0.95 + (i % 4) * 0.03))
    tex.rect(49, 6, 6, 10, hexc("ede6d2"))
    tex.outline(49, 6, 6, 10, gold)
    for y in (3, 22, 25):
        tex.set(51, y, gold)
        tex.set(52, y, gold)
    spine = (48, 0, 8, 28)
    edge = tex.material(56, 0, 8, 8, c2, 5)
    pages = (56, 8, 8, 20)
    for j in range(20):
        for i in range(8):
            tex.set(56 + i, 8 + j, hexc("ede6d2") if j % 2 else hexc("d6cfbb"))
    metal = tex.material(0, 32, 8, 8, gold, 8, "brushed_h")
    dark = tex.material(8, 32, 8, 8, hexc("1a1a1a"), 4)
    m = Model(f"{NS}:item/3d/{name}", 64)
    m.box((2, 1, 9), (14, 15, 9.5), all6(edge, south=front, north=None))  # front cover
    m.box((2, 1, 6.5), (14, 15, 7), all6(edge, north=back, south=None))  # back cover
    m.box((1.4, 1, 6.5), (2.4, 15, 9.5), all6(edge, west=spine))  # spine
    m.box((2.4, 1.4, 7), (13.6, 14.6, 9), {"east": pages, "up": (pages[0], pages[1], 8, 4), "down": (pages[0], pages[1], 8, 4)})
    for (x, y) in ((2.2, 1.2), (12.8, 1.2), (2.2, 13.8), (12.8, 13.8)):  # metal corners
        m.box((x, y, 9.5), (x + 1, y + 1, 9.6), all6(metal, north=None))
    if name == "personal_binder":  # elastic strap
        m.box((11, 1, 9.5), (11.8, 15, 9.66), all6(dark, north=None))
    else:  # snap clasp
        m.box((12.6, 7, 9.5), (14.4, 9, 9.7), all6(metal, north=None))
    save_3d(name, m, tex)
    icon_model(name, f"{NS}:item/{name}")
    entry_model(name, HELD_FLAT, f"{NS}:item/{name}")


def emblem_ball(tex, cx, cy):
    tex.disc(cx, cy, 7, hexc("151515"))
    for y in range(cy - 7, cy + 8):
        for x in range(cx - 7, cx + 8):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= 36:
                tex.set(x, y, hexc("e8463c") if y < cy - 1 else hexc("f5f5f5") if y > cy else hexc("151515"))
    tex.disc(cx, cy, 2.6, hexc("151515"))
    tex.disc(cx, cy, 1.6, hexc("ffffff"))


def emblem_star(tex, cx, cy):
    star = ["......#......", ".....###.....", ".....###.....", "#############", ".###########.", "..#########..",
            "...#######...", "...###.###...", "..###...###..", "..##.....##..", ".#.........#."]
    glyph(tex, star, cx - 6, cy - 5, hexc("ffd34d"))
    tex.set(cx, cy - 2, hexc("fff2b0"))
    tex.set(cx, cy - 1, hexc("fff2b0"))


def mansion_key():
    tex = Tex(64, 64, 17)
    gold = tex.material(0, 0, 16, 16, hexc("f2c230"), 9, "brushed_v")
    gold_d = tex.material(16, 0, 16, 16, hexc("b07a12"), 7)
    ruby = (32, 0, 16, 16)
    tex.rect(32, 0, 16, 16, hexc("a3121a"))
    tex.disc(40, 8, 6.5, hexc("e3262e"))
    tex.disc(38, 6, 2, hexc("ff9a9a"))
    pearl = tex.material(48, 0, 16, 16, hexc("f4f4f4"), 5)
    m = Model(f"{NS}:item/3d/mansion_key", 64)
    R = (-45, "z", (8, 8, 8))
    o = 1.4  # centres the key along its axis
    m.box((5.8, -3 + o, 7.4), (10.2, -1.8 + o, 8.6), all6(gold), R)  # bow ring
    m.box((5.8, 1.8 + o, 7.4), (10.2, 3 + o, 8.6), all6(gold), R)
    m.box((5, -2.2 + o, 7.4), (6.2, 2.2 + o, 8.6), all6(gold), R)
    m.box((9.8, -2.2 + o, 7.4), (11, 2.2 + o, 8.6), all6(gold), R)
    m.box((7.2, -0.8 + o, 7.6), (8.8, 0.8 + o, 8.4), all6(ruby), R)  # ruby
    m.box((6.8, 3 + o, 7.2), (9.2, 4.2 + o, 8.8), all6(gold_d), R)  # collar
    m.box((7.4, 4.2 + o, 7.6), (8.6, 16 + o, 8.4), all6(gold), R)  # shaft
    m.box((7.6, 16 + o, 7.7), (8.4, 16.6 + o, 8.3), all6(pearl), R)  # tip
    m.box((8.6, 12 + o, 7.6), (10.6, 13.2 + o, 8.4), all6(gold_d), R)  # bit
    m.box((8.6, 14.4 + o, 7.6), (11.2, 15.6 + o, 8.4), all6(gold_d), R)
    m.box((9.8, 13.2 + o, 7.6), (10.6, 14.4 + o, 8.4), all6(gold_d), R)
    save_3d("mansion_key", m, tex)
    icon_model("mansion_key", f"{NS}:item/mansion_key")
    entry_model("mansion_key", HANDHELD, f"{NS}:item/mansion_key")


def main():
    phones()
    mop()
    repair_kit()
    deodorant_spray()
    nerd_gloves()
    manager_goggles()
    binder("general_binder", "8e2a2e", "6a1d21", emblem_ball)
    binder("personal_binder", "2e5fa8", "1d3f73", emblem_star)
    mansion_key()
    print("gen_items_3d: 7 phone tiers + 8 tools (3D models, textures, GUI icons, entry models)")


if __name__ == "__main__":
    main()
