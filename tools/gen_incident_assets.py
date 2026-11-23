#!/usr/bin/env python3
"""
Incident module resources (v0.9 events): 16x16 textures for the tcgshop:incident block (puddle, debris,
dirt, droppings, scorch, sparks, mess, package) and for the tools (mop, repair kit),
block/item models, blockstate, #tcgshop:fix/* tags, recipes and the tcgshop:events/package loot table.
Standard library only (PNG with zlib + struct). Fixed seed: idempotent.

Usage:  python -I -X utf8 tools/gen_incident_assets.py
Writes only inside src/main/resources.
"""
import json
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
A = ROOT / "assets" / "tcgshop"
D = ROOT / "data" / "tcgshop"
KINDS = ["puddle", "debris", "dirt", "droppings", "scorch", "sparks", "mess", "package"]


# ------------------------------------------------------------------ PNG

def write_png(path, px):
    h = len(px)
    w = len(px[0])
    raw = bytearray()
    for row in px:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((max(0, min(255, int(r))), max(0, min(255, int(g))), max(0, min(255, int(b))), max(0, min(255, int(a)))))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    out = b"\x89PNG\r\n\x1a\n"
    out += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    out += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    out += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(out)


def blank():
    return [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]


def blob(rng, cx, cy, r, jitter=0.35):
    """Mask of an irregular blob (set of (x, y))."""
    pts = set()
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r * (1 - jitter + rng.random() * jitter * 2):
                pts.add((x, y))
    return pts


def shade(c, k):
    return (c[0] * k, c[1] * k, c[2] * k)


# ------------------------------------------------------------------ textures

def tex_puddle(rng):
    px = blank()
    m = blob(rng, 7.5, 8, 6.5) | blob(rng, 4, 5, 3) | blob(rng, 11, 11, 3.5)
    for (x, y) in m:
        k = 0.85 + rng.random() * 0.25
        px[y][x] = (60 * k, 120 * k, 205 * k, 150)
    for (x, y) in list(m):
        if (x + 1, y + 1) in m and rng.random() < 0.12:
            px[y][x] = (200, 230, 255, 190)
    return px


def tex_scorch(rng, base=True):
    px = blank()
    m = blob(rng, 8, 8, 6.8 if base else 4.5)
    for (x, y) in m:
        d = ((x - 8) ** 2 + (y - 8) ** 2) ** 0.5
        k = 0.25 + d / 18 + rng.random() * 0.15
        px[y][x] = (40 * k + 10, 32 * k + 8, 26 * k + 6, 225 - int(d * 6))
        if rng.random() < 0.08:
            px[y][x] = (120, 115, 110, 220)
    return px


def tex_sparks(rng):
    px = blank()
    for _ in range(9):
        x = rng.randint(2, 13)
        y = rng.randint(6, 15)
        for _ in range(rng.randint(4, 8)):
            if 0 <= x < 16 and 0 <= y < 16:
                px[y][x] = (255, 240, 120, 255) if rng.random() < 0.6 else (255, 255, 230, 255)
            x += rng.choice((-1, 0, 1))
            y -= 1
    return px


def tex_dirt(rng):
    px = blank()
    m = blob(rng, 6, 7, 4) | blob(rng, 11, 10, 3.5) | blob(rng, 5, 12, 2.5) | blob(rng, 12, 4, 2)
    for (x, y) in m:
        c = rng.choice(((121, 85, 58), (100, 70, 46), (139, 99, 66), (88, 60, 40)))
        px[y][x] = (*c, 255)
    return px


def tex_droppings(rng):
    px = blank()
    for (cx, cy, r) in ((5, 6, 1.8), (10, 9, 2.2), (6, 12, 1.5), (12, 4, 1.4)):
        for (x, y) in blob(rng, cx, cy, r, 0.2):
            c = rng.choice(((96, 70, 40), (80, 60, 34), (110, 90, 50)))
            px[y][x] = (*c, 255)
        if 0 <= int(cx) - 1 < 16:
            px[int(cy) - 1][int(cx) - 1] = (140, 160, 70, 255)
    return px


def tex_mess(rng):
    px = blank()
    colors = ((230, 70, 60), (70, 130, 220), (250, 210, 70), (90, 190, 110), (240, 240, 240), (170, 110, 200))
    for _ in range(16):
        w, h = rng.choice(((2, 3), (3, 2), (2, 2), (3, 4)))
        x, y = rng.randint(0, 16 - w), rng.randint(0, 16 - h)
        c = rng.choice(colors)
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                px[yy][xx] = (*shade(c, 0.85 + rng.random() * 0.2), 255)
    return px


def tex_debris(rng, chunk=False):
    px = blank()
    if chunk:
        for y in range(16):
            for x in range(16):
                g = 120 + rng.randint(-25, 25)
                px[y][x] = (g, g - 4, g - 8, 255)
        return px
    m = blob(rng, 8, 8, 6, 0.45)
    for (x, y) in m:
        if rng.random() < 0.55:
            g = 110 + rng.randint(-30, 30)
            px[y][x] = (g, g - 5, g - 10, 255)
        elif rng.random() < 0.35:
            px[y][x] = (150, 110, 70, 255)  # wood splinters
    return px


def tex_package(rng, top=False):
    px = blank()
    for y in range(16):
        for x in range(16):
            k = 0.92 + rng.random() * 0.12
            px[y][x] = (190 * k, 145 * k, 95 * k, 255)
    for i in range(16):
        px[i][7] = px[i][8] = (225, 200, 140, 255)  # tape
        if top:
            px[7][i] = px[8][i] = (225, 200, 140, 255)
    for i in range(16):
        px[0][i] = px[15][i] = px[i][0] = px[i][15] = (150, 110, 70, 255)
    if not top:
        # white label with "?" (no sender)
        for y in range(10, 14):
            for x in range(2, 6):
                px[y][x] = (240, 240, 235, 255)
        px[11][3] = px[11][4] = px[12][4] = (60, 60, 60, 255)
    return px


def tex_mop(rng):
    px = blank()
    for i in range(3, 14):  # diagonal stick
        px[15 - i][i] = (150, 105, 60, 255)
        if i < 13:
            px[15 - i][i + 1] = (120, 85, 48, 255)
    for (x, y) in blob(rng, 4.5, 11.5, 3.6, 0.2):  # mop head
        if 0 <= x < 16 and 0 <= y < 16:
            g = 215 + rng.randint(-20, 20)
            px[y][x] = (g, g, g + 5, 255)
    for x in range(1, 7):
        if rng.random() < 0.6:
            px[15][x] = (180, 180, 190, 255)
    return px


def tex_repair_kit(rng):
    px = blank()
    for y in range(6, 15):
        for x in range(2, 14):
            k = 0.9 + rng.random() * 0.1
            px[y][x] = (200 * k, 45 * k, 40 * k, 255)
    for x in range(2, 14):
        px[6][x] = (230, 80, 70, 255)
        px[14][x] = (140, 30, 28, 255)
    for x in range(6, 10):  # handle
        px[3][x] = (90, 90, 95, 255)
    px[4][5] = px[5][5] = px[4][10] = px[5][10] = (90, 90, 95, 255)
    for x in range(7, 9):  # clasp
        px[9][x] = px[10][x] = (235, 205, 80, 255)
    return px


# ------------------------------------------------------------------ models

def flat(t, extra=None):
    m = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": t, "t": t},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 0.25, 16],
            "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#t"},
                      "down": {"uv": [0, 0, 16, 16], "texture": "#t", "cullface": "down"}}
        }]
    }
    if extra:
        m["textures"].update(extra["textures"])
        m["elements"] += extra["elements"]
    return m


def cube(fr, to, tex):
    f = {}
    for side in ("north", "south", "east", "west", "up", "down"):
        f[side] = {"texture": tex}
    return {"from": fr, "to": to, "faces": f}


def models():
    out = {}
    for k in ("puddle", "dirt", "droppings", "scorch"):
        out[k] = flat(f"tcgshop:block/incident_{k}")
    out["sparks"] = flat("tcgshop:block/incident_sparks_base", {
        "textures": {"s": "tcgshop:block/incident_sparks"},
        "elements": [
            {"from": [0.8, 0, 8], "to": [15.2, 12, 8], "shade": False,
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
             "faces": {"north": {"uv": [0, 4, 16, 16], "texture": "#s"}, "south": {"uv": [0, 4, 16, 16], "texture": "#s"}}},
            {"from": [8, 0, 0.8], "to": [8, 12, 15.2], "shade": False,
             "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
             "faces": {"west": {"uv": [0, 4, 16, 16], "texture": "#s"}, "east": {"uv": [0, 4, 16, 16], "texture": "#s"}}},
        ]})
    out["debris"] = flat("tcgshop:block/incident_debris", {
        "textures": {"c": "tcgshop:block/incident_debris_chunk"},
        "elements": [cube([3, 0, 4], [7, 3, 7], "#c"), cube([9, 0, 8], [13, 4, 12], "#c"), cube([5, 0, 10], [8, 2, 13], "#c"),
                     cube([10, 0, 3], [12, 2, 5], "#c")]})
    out["mess"] = flat("tcgshop:block/incident_mess", {
        "textures": {"b": "tcgshop:block/incident_package_side"},
        "elements": [cube([2, 0, 9], [6, 3, 13], "#b"), cube([10, 0, 2], [13, 2, 6], "#b")]})
    out["package"] = {
        "parent": "minecraft:block/block",
        "textures": {"particle": "tcgshop:block/incident_package_side", "s": "tcgshop:block/incident_package_side",
                     "t": "tcgshop:block/incident_package_top"},
        "elements": [{"from": [3, 0, 3], "to": [13, 9, 13], "faces": {
            "north": {"uv": [3, 7, 13, 16], "texture": "#s"}, "south": {"uv": [3, 7, 13, 16], "texture": "#s"},
            "east": {"uv": [3, 7, 13, 16], "texture": "#s"}, "west": {"uv": [3, 7, 13, 16], "texture": "#s"},
            "up": {"uv": [3, 3, 13, 13], "texture": "#t"}, "down": {"uv": [3, 3, 13, 13], "texture": "#s", "cullface": "down"}}}]
    }
    return out


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main():
    rng = random.Random(9090)
    tex = {
        "incident_puddle": tex_puddle(rng),
        "incident_scorch": tex_scorch(rng),
        "incident_sparks_base": tex_scorch(rng, base=False),
        "incident_sparks": tex_sparks(rng),
        "incident_dirt": tex_dirt(rng),
        "incident_droppings": tex_droppings(rng),
        "incident_mess": tex_mess(rng),
        "incident_debris": tex_debris(rng),
        "incident_debris_chunk": tex_debris(rng, chunk=True),
        "incident_package_side": tex_package(rng),
        "incident_package_top": tex_package(rng, top=True),
    }
    for name, px in tex.items():
        write_png(A / "textures" / "block" / f"{name}.png", px)
    write_png(A / "textures" / "item" / "mop.png", tex_mop(rng))
    write_png(A / "textures" / "item" / "repair_kit.png", tex_repair_kit(rng))

    for k, m in models().items():
        dump(A / "models" / "block" / f"incident_{k}.json", m)
    variants = {}
    for k in KINDS:
        variants[f"kind={k}"] = [{"model": f"tcgshop:block/incident_{k}", **({"y": r} if r else {})} for r in (0, 90, 180, 270)]
    dump(A / "blockstates" / "incident.json", {"variants": variants})
    for it in ("mop", "repair_kit"):
        dump(A / "models" / "item" / f"{it}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"tcgshop:item/{it}"}})

    # fix tags: never buckets or fire (review E3)
    fix = {"puddle": ["tcgshop:mop"], "droppings": ["tcgshop:mop"], "mess": ["tcgshop:mop"],
           "sparks": ["tcgshop:repair_kit"], "scorch": ["tcgshop:repair_kit"], "dirt": ["#minecraft:shovels"],
           "debris": [], "package": []}
    for k, vals in fix.items():
        dump(D / "tags" / "item" / "fix" / f"{k}.json", {"replace": False, "values": vals})

    dump(D / "recipe" / "mop.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" S ", " S ", " W "],
        "key": {"S": {"item": "minecraft:stick"}, "W": {"tag": "minecraft:wool"}},
        "result": {"id": "tcgshop:mop", "count": 1}})
    dump(D / "recipe" / "repair_kit.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" I ", "CRC"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "C": {"item": "minecraft:copper_ingot"}, "R": {"item": "minecraft:redstone"}},
        "result": {"id": "tcgshop:repair_kit", "count": 1}})

    def entry(item, w, lo=1, hi=1):
        e = {"type": "minecraft:item", "name": item, "weight": w}
        if hi > 1:
            e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return e

    dump(D / "loot_table" / "events" / "package.json", {
        "type": "minecraft:chest",
        "pools": [{"rolls": 1, "entries": [
            entry("minecraft:cookie", 4, 2, 5), entry("minecraft:bread", 3, 1, 3), entry("minecraft:paper", 3, 2, 6),
            entry("minecraft:string", 2, 1, 4), entry("minecraft:candle", 2, 1, 2), entry("minecraft:flower_pot", 2),
            entry("minecraft:sweet_berries", 2, 2, 5), entry("minecraft:lantern", 1), entry("minecraft:name_tag", 1),
            entry("minecraft:book", 1)]}]})
    print("gen_incident_assets: OK —", len(tex) + 2, "textures,", len(KINDS), "block models")


if __name__ == "__main__":
    main()
