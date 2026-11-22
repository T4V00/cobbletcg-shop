#!/usr/bin/env python3
"""
Genera los recursos de las 7 estatuas de lujo (v0.9): texturas 16x16, modelos de bloque (base y piezas altas),
modelos de objeto, blockstates, loot tables y tags. Sólo biblioteca estándar (PNG con zlib + struct).
Diseños abstractos y originales (trofeo, obelisco, orbe, figura alada, corona, cristales, gran orbe): nada de arte Pokémon.

Uso:  python -I -X utf8 tools/gen_statue_assets.py
Escribe únicamente dentro de src/main/resources. Idempotente.
"""
import json
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
A = ROOT / "assets" / "tcgshop"
D = ROOT / "data"
NS = "tcgshop"


# ------------------------------------------------------------------ PNG

def write_png(path, px):
    """px: lista de filas de (r, g, b, a)."""
    h = len(px)
    w = len(px[0])
    raw = bytearray()
    for row in px:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    out = b"\x89PNG\r\n\x1a\n"
    out += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    out += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    out += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(out)


def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def mul(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def metal(seed, base, light, dark, streaks=True):
    """Metal pulido: degradado diagonal, vetas y algún brillo."""
    rnd = random.Random(seed)
    b, l, d = hexc(base), hexc(light), hexc(dark)
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            t = ((x + y) / 30.0)
            c = mix(l, b, min(1.0, t * 1.4)) if t < 0.5 else mix(b, d, (t - 0.5) * 1.6)
            c = mul(c, 0.94 + rnd.random() * 0.1)
            if streaks and (x - y) % 7 == 0:
                c = mix(c, l, 0.35)
            row.append(c)
        px.append(row)
    for (x, y) in [(3, 2), (4, 3), (11, 9), (12, 10)]:
        px[y][x] = mix(px[y][x], (255, 255, 255, 255), 0.55)
    return px


def trim(seed, base, dark):
    """Moldura: bandas horizontales con remaches."""
    rnd = random.Random(seed)
    b, d = hexc(base), hexc(dark)
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            band = (y % 4)
            c = b if band in (1, 2) else d
            row.append(mul(c, 0.92 + rnd.random() * 0.12))
        px.append(row)
    for y in range(0, 16, 4):
        for x in range(2, 16, 5):
            px[(y + 1) % 16][x] = mix(b, (255, 255, 255, 255), 0.5)
    return px


def stone(seed, base, vein, dark):
    """Mármol / obsidiana con vetas."""
    rnd = random.Random(seed)
    b, v, d = hexc(base), hexc(vein), hexc(dark)
    px = [[mul(b, 0.93 + rnd.random() * 0.1) for _ in range(16)] for _ in range(16)]
    for _ in range(3):
        x, y = rnd.randrange(16), 0
        while y < 16:
            px[y][x % 16] = mix(px[y][x % 16], v, 0.75)
            x += rnd.choice((-1, 0, 1))
            y += 1
    for i in range(16):
        px[15][i] = mix(px[15][i], d, 0.5)
        px[0][i] = mix(px[0][i], d, 0.3)
    return px


def glow(seed, core, edge):
    """Acento 'emisivo': centro casi blanco, chispas brillantes."""
    rnd = random.Random(seed)
    c, e = hexc(core), hexc(edge)
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            dist = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 / 10.6
            col = mix(mix(c, (255, 255, 255, 255), 0.45), e, min(1.0, dist))
            row.append(mul(col, 0.97 + rnd.random() * 0.06))
        px.append(row)
    for _ in range(6):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[y][x] = (255, 255, 255, 255)
    return px


def crystal(seed, base, light, dark):
    """Facetas de cristal (amatista / diamante)."""
    rnd = random.Random(seed)
    b, l, d = hexc(base), hexc(light), hexc(dark)
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            facet = ((x // 4) + (y // 5)) % 3
            c = (b, l, d)[facet]
            row.append(mul(c, 0.93 + rnd.random() * 0.12))
        px.append(row)
    for i in range(16):
        px[i][(i * 3) % 16] = mix(px[i][(i * 3) % 16], (255, 255, 255, 255), 0.5)
    return px


TEXTURES = {
    "statue_bronze": lambda: metal("bronze", "B87333", "E8A866", "6B3E1A"),
    "statue_bronze_trim": lambda: trim("bronze_t", "A0612A", "5A3414"),
    "statue_silver": lambda: metal("silver", "C9CED6", "F4F7FB", "7D848F"),
    "statue_silver_trim": lambda: trim("silver_t", "AEB4BE", "5E646E"),
    "statue_gold": lambda: metal("gold", "F2C230", "FFF0A0", "A87A10"),
    "statue_gold_trim": lambda: trim("gold_t", "E0AE20", "8A600A"),
    "statue_emerald": lambda: crystal("emerald", "2ECC71", "8CF5B4", "137A40"),
    "statue_emerald_trim": lambda: trim("emerald_t", "F2C230", "1E7A46"),
    "statue_diamond": lambda: crystal("diamond", "4EE2EC", "D8FBFF", "1E8E9C"),
    "statue_diamond_trim": lambda: trim("diamond_t", "E8F4F8", "5AA8B4"),
    "statue_netherite": lambda: metal("netherite", "4A3F45", "7A6C72", "231C20"),
    "statue_amethyst": lambda: crystal("amethyst", "9A5CC6", "D4A8F5", "5A2E80"),
    "statue_amethyst_glow": lambda: glow("amethyst_g", "E2B8FF", "9A5CC6"),
    "statue_masterball": lambda: metal("mb", "6A2BB0", "B27BF0", "2E0F55"),
    "statue_masterball_gold": lambda: metal("mb_gold", "F5C542", "FFF4B8", "A8780E"),
    "statue_masterball_glow": lambda: glow("mb_g", "FF8CF2", "B04BFF"),
    "statue_marble": lambda: stone("marble", "E8E4DC", "B9B2A6", "9A948A"),
    "statue_obsidian": lambda: stone("obsidian", "1E1428", "5A3C82", "0A0610"),
}


# ------------------------------------------------------------------ modelos

def B(f, t, tex, glow_=False, rot=None):
    """Cubo en píxeles de mundo: f/t = (x, y, z)."""
    return {"f": list(f), "t": list(t), "tex": tex, "glow": glow_, "rot": rot}


def uv_for(face, f, t):
    w = {"north": t[0] - f[0], "south": t[0] - f[0], "east": t[2] - f[2], "west": t[2] - f[2],
         "up": t[0] - f[0], "down": t[0] - f[0]}[face]
    h = {"north": t[1] - f[1], "south": t[1] - f[1], "east": t[1] - f[1], "west": t[1] - f[1],
         "up": t[2] - f[2], "down": t[2] - f[2]}[face]
    w = max(0.5, min(16.0, abs(w)))
    h = max(0.5, min(16.0, abs(h)))
    return [0, 0, round(w, 3), round(h, 3)]


def element(b, dy=0):
    f = [b["f"][0], b["f"][1] - dy, b["f"][2]]
    t = [b["t"][0], b["t"][1] - dy, b["t"][2]]
    el = {"from": f, "to": t, "faces": {}}
    for face in ("north", "south", "east", "west", "up", "down"):
        el["faces"][face] = {"uv": uv_for(face, f, t), "texture": "#" + b["tex"]}
    if b["glow"]:
        el["shade"] = False
        el["light_emission"] = 15  # ignorado por versiones sin soporte; el bloque además emite luz
    if b["rot"]:
        axis, ang, ox, oy, oz = b["rot"]
        el["rotation"] = {"origin": [ox, oy - dy, oz], "axis": axis, "angle": ang}
    for v in f + t:
        assert -16 <= v <= 32, ("fuera de rango", b)
    return el


def orb_layers(cx, cz, y0, radius, tex, step=2):
    """Esfera aproximada por capas (de abajo arriba), en píxeles de mundo."""
    out = []
    n = int(radius * 2 / step)
    for i in range(n):
        yc = y0 + i * step + step / 2.0 - (y0 + radius)
        half = max(1.5, (radius ** 2 - yc ** 2) ** 0.5)
        half = round(half * 2) / 2.0
        out.append(B((cx - half, y0 + i * step, cz - half), (cx + half, y0 + (i + 1) * step, cz + half), tex))
    return out


# Diseños: texturas por clave lógica ("main", "trim", "base", "glow"...)
def design_bronze():  # Emprendedor: trofeo de sobremesa
    return [
        B((4.5, 0, 4.5), (11.5, 1.5, 11.5), "base"),
        B((5.5, 1.5, 5.5), (10.5, 2.5, 10.5), "trim"),
        B((7, 2.5, 7), (9, 4.5, 9), "main"),
        B((6, 4.5, 6), (10, 5, 10), "trim"),
        B((5, 5, 5), (11, 8.5, 11), "main"),
        B((4, 5.5, 7.25), (5, 7.5, 8.75), "trim"),
        B((11, 5.5, 7.25), (12, 7.5, 8.75), "trim"),
        B((5.5, 8.5, 5.5), (10.5, 9, 10.5), "trim"),
    ]


def design_silver():  # Comerciante: obelisco
    return [
        B((3, 0, 3), (13, 2.5, 13), "base"),
        B((4, 2.5, 4), (12, 3.5, 12), "trim"),
        B((5.5, 3.5, 5.5), (10.5, 5, 10.5), "main"),
        B((6, 5, 6), (10, 12, 10), "main"),
        B((6.5, 12, 6.5), (9.5, 14, 9.5), "main"),
        B((7.25, 14, 7.25), (8.75, 16, 8.75), "trim"),
        B((5.75, 8, 5.75), (10.25, 8.5, 10.25), "trim"),
    ]


def design_gold():  # Mercader: orbe sobre columna (1,5 bloques)
    out = [
        B((2, 0, 2), (14, 2, 14), "base"),
        B((3, 2, 3), (13, 3, 13), "trim"),
        B((5, 3, 5), (11, 11, 11), "main"),
        B((4.5, 3, 4.5), (5.5, 11, 5.5), "trim"),
        B((10.5, 3, 4.5), (11.5, 11, 5.5), "trim"),
        B((4.5, 3, 10.5), (5.5, 11, 11.5), "trim"),
        B((10.5, 3, 10.5), (11.5, 11, 11.5), "trim"),
        B((3.5, 11, 3.5), (12.5, 12.5, 12.5), "trim"),
    ]
    out += orb_layers(8, 8, 12.5, 4.5, "main", step=1.5)
    out += [B((3, 16, 7.5), (13, 17, 8.5), "trim"), B((7.5, 16, 3), (8.5, 17, 13), "trim"),
            B((7.25, 21.5, 7.25), (8.75, 24, 8.75), "trim")]
    return out


def design_emerald():  # Inversor: figura alada abstracta con halo
    return [
        B((1, 0, 1), (15, 3, 15), "base"),
        B((2, 3, 2), (14, 4.5, 14), "trim"),
        B((5, 4.5, 5), (11, 7, 11), "trim"),
        B((6, 7, 6.5), (10, 17, 9.5), "main"),          # cuerpo
        B((5.5, 15, 6), (10.5, 18, 10), "main"),         # hombros
        B((6.5, 18, 6.5), (9.5, 22, 9.5), "main"),       # cabeza
        B((0.5, 13, 7.5), (6, 25, 8.5), "main", rot=("z", 22.5, 6, 17, 8)),    # ala izq.
        B((10, 13, 7.5), (15.5, 25, 8.5), "main", rot=("z", -22.5, 10, 17, 8)),  # ala der.
        B((5, 24.5, 7.5), (11, 25.5, 8.5), "trim"),      # halo
        B((5, 24.5, 7.5), (6, 28, 8.5), "trim"),
        B((10, 24.5, 7.5), (11, 28, 8.5), "trim"),
        B((5, 27, 7.5), (11, 28, 8.5), "trim"),
    ]


def design_diamond():  # Visionario: corona sobre columna (2 bloques)
    return [
        B((0, 0, 0), (16, 3, 16), "base"),
        B((1, 3, 1), (15, 4.5, 15), "trim"),
        B((4, 4.5, 4), (12, 20, 12), "main"),
        B((3.5, 4.5, 7), (4, 20, 9), "trim"), B((12, 4.5, 7), (12.5, 20, 9), "trim"),
        B((7, 4.5, 3.5), (9, 20, 4), "trim"), B((7, 4.5, 12), (9, 20, 12.5), "trim"),
        B((2, 20, 2), (14, 22.5, 14), "trim"),
        B((4, 22.5, 4), (12, 24, 12), "main"),
        B((3, 24, 3), (13, 27, 13), "trim"),              # aro de la corona
        B((3, 27, 3), (5, 31, 5), "trim"), B((11, 27, 3), (13, 31, 5), "trim"),
        B((3, 27, 11), (5, 31, 13), "trim"), B((11, 27, 11), (13, 31, 13), "trim"),
        B((7, 27, 2.5), (9, 30, 3.5), "trim"), B((7, 27, 12.5), (9, 30, 13.5), "trim"),
        B((2.5, 27, 7), (3.5, 30, 9), "trim"), B((12.5, 27, 7), (13.5, 30, 9), "trim"),
        B((6, 27, 6), (10, 32, 10), "main"),              # gran diamante
    ]


def design_netherite():  # Soberano: drusa de amatista sobre dais de netherita (más ancha)
    return [
        B((-4, 0, -4), (20, 3, 20), "base"),
        B((-2, 3, -2), (18, 5, 18), "trim"),
        B((1, 5, 1), (15, 7, 15), "trim"),
        B((5, 7, 5), (11, 28, 11), "main"),               # cristal central
        B((6, 28, 6), (10, 31, 10), "main"),
        B((7, 31, 7), (9, 32, 9), "glow"),
        B((6.5, 9, 6.5), (9.5, 26, 9.5), "glow"),         # núcleo luminoso (asoma por las caras)
        B((1, 6, 6), (5, 20, 10), "main", rot=("z", 22.5, 5, 7, 8)),
        B((11, 6, 6), (15, 20, 10), "main", rot=("z", -22.5, 11, 7, 8)),
        B((6, 6, 1), (10, 18, 5), "main", rot=("x", -22.5, 8, 7, 5)),
        B((6, 6, 11), (10, 18, 15), "main", rot=("x", 22.5, 8, 7, 11)),
        B((-3, 5, -3), (-1, 9, -1), "glow"), B((17, 5, -3), (19, 9, -1), "glow"),
        B((-3, 5, 17), (-1, 9, 19), "glow"), B((17, 5, 17), (19, 9, 19), "glow"),
    ]


def design_masterball():  # Magnate: gran orbe púrpura con aro dorado y corona, 3 bloques de alto y 2 de ancho
    out = [
        B((-8, 0, -8), (24, 3, 24), "base"),
        B((-6, 3, -6), (22, 5, 22), "trim"),
        B((-4, 5, -4), (20, 7, 20), "base"),
        # obeliscos dorados con remate luminoso en las esquinas
        B((-6, 5, -6), (-3, 18, -3), "trim"), B((19, 5, -6), (22, 18, -3), "trim"),
        B((-6, 5, 19), (-3, 18, 22), "trim"), B((19, 5, 19), (22, 18, 22), "trim"),
        B((-5.5, 18, -5.5), (-3.5, 20, -3.5), "glow"), B((19.5, 18, -5.5), (21.5, 20, -3.5), "glow"),
        B((-5.5, 18, 19.5), (-3.5, 20, 21.5), "glow"), B((19.5, 18, 19.5), (21.5, 20, 21.5), "glow"),
        # columna
        B((3, 7, 3), (13, 9, 13), "trim"),
        B((4, 9, 4), (12, 24, 12), "main"),
        B((3.5, 9, 7), (4, 24, 9), "glow"), B((12, 9, 7), (12.5, 24, 9), "glow"),
        B((7, 9, 3.5), (9, 24, 4), "glow"), B((7, 9, 12), (9, 24, 12.5), "glow"),
        B((2, 24, 2), (14, 26, 14), "trim"),
    ]
    # orbe: centro y=34, radio 8 -> capas 26..42 (las de y>=32 van a la pieza superior)
    out += [
        B((4, 26, 4), (12, 28, 12), "main"),
        B((2, 28, 2), (14, 30, 14), "main"),
        B((1, 30, 1), (15, 32, 15), "main"),
        B((0, 32, 0), (16, 36, 16), "main"),
        B((-0.5, 33, -0.5), (16.5, 35, 16.5), "trim"),     # aro dorado
        B((6, 32.5, -1), (10, 35.5, 0), "glow"),            # gemas del aro
        B((6, 32.5, 16), (10, 35.5, 17), "glow"),
        B((1, 36, 1), (15, 38, 15), "main"),
        B((2, 38, 2), (14, 40, 14), "main"),
        B((4, 40, 4), (12, 42, 12), "main"),
        # corona luminosa
        B((5, 42, 5), (11, 43, 11), "trim"),
        B((5, 43, 5), (6.5, 46, 6.5), "trim"), B((9.5, 43, 5), (11, 46, 6.5), "trim"),
        B((5, 43, 9.5), (6.5, 46, 11), "trim"), B((9.5, 43, 9.5), (11, 46, 11), "trim"),
        B((7, 43, 7), (9, 48, 9), "glow"),
    ]
    return out


TIERS = [
    # key, design, textures {logical: texture}, parts, item gui scale
    ("bronze", design_bronze, {"main": "statue_bronze", "trim": "statue_bronze_trim", "base": "statue_marble"}, 0),
    ("silver", design_silver, {"main": "statue_silver", "trim": "statue_silver_trim", "base": "statue_marble"}, 0),
    ("gold", design_gold, {"main": "statue_gold", "trim": "statue_gold_trim", "base": "statue_marble"}, 1),
    ("emerald", design_emerald, {"main": "statue_emerald", "trim": "statue_emerald_trim", "base": "statue_marble"}, 1),
    ("diamond", design_diamond, {"main": "statue_diamond", "trim": "statue_diamond_trim", "base": "statue_obsidian"}, 1),
    ("netherite", design_netherite, {"main": "statue_amethyst", "trim": "statue_netherite", "base": "statue_netherite",
                                     "glow": "statue_amethyst_glow"}, 1),
    ("masterball", design_masterball, {"main": "statue_masterball", "trim": "statue_masterball_gold", "base": "statue_obsidian",
                                       "glow": "statue_masterball_glow"}, 2),
]


def tex_map(tm):
    out = {k: f"{NS}:block/{v}" for k, v in tm.items()}
    out["particle"] = out["main"]
    return out


def bounds(boxes):
    xs = [b["f"][0] for b in boxes] + [b["t"][0] for b in boxes]
    ys = [b["f"][1] for b in boxes] + [b["t"][1] for b in boxes]
    zs = [b["f"][2] for b in boxes] + [b["t"][2] for b in boxes]
    return min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)


def item_display(boxes):
    x0, x1, y0, y1, z0, z1 = bounds(boxes)
    ext = max(x1 - x0, y1 - y0, z1 - z0)
    cy = (y0 + y1) / 2.0

    def tr(base_scale, rot, extra_y=0.0):
        s = round(min(base_scale * 1.6, base_scale * 16.0 / ext), 4)
        return {"rotation": rot, "translation": [0, round(-(cy - 8) * s + extra_y, 3), 0], "scale": [s, s, s]}

    return {
        "gui": tr(0.625, [30, 225, 0]),
        "ground": tr(0.25, [0, 0, 0], 3),
        "fixed": tr(0.5, [0, 0, 0]),
        "thirdperson_righthand": tr(0.375, [75, 45, 0], 2.5),
        "firstperson_righthand": tr(0.40, [0, 45, 0]),
        "firstperson_lefthand": tr(0.40, [0, 225, 0]),
        "head": tr(0.6, [0, 0, 0], 6),
    }


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


FACINGS = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]


def main():
    for name, fn in TEXTURES.items():
        write_png(A / "textures" / "block" / f"{name}.png", fn())

    part_variants = {}
    for idx, (key, design, tm, parts) in enumerate(TIERS, start=1):
        boxes = design()
        for b in boxes:
            assert b["tex"] in tm, (key, b["tex"])
        base_boxes = [b for b in boxes if b["t"][1] <= 32]
        top_boxes = [b for b in boxes if b["f"][1] >= 32]
        assert len(base_boxes) + len(top_boxes) == len(boxes), key
        name = f"statue_{key}"
        tall = idx >= 3
        model = {"parent": "minecraft:block/block", "textures": tex_map(tm),
                 "elements": [element(b) for b in base_boxes]}
        if tall:
            model["ambientocclusion"] = False
        dump(A / "models" / "block" / f"{name}.json", model)
        # pieza invisible (sólo partícula) y, si hace falta, la parte superior visible
        dump(A / "models" / "block" / f"{name}_part.json", {"textures": {"particle": f"{NS}:block/{tm['main']}"}})
        if top_boxes:
            dump(A / "models" / "block" / f"{name}_top.json", {
                "parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex_map(tm),
                "elements": [element(b, dy=32) for b in top_boxes]})
        # objeto: el modelo de la base con su propio encuadre
        dump(A / "models" / "item" / f"{name}.json", {"parent": f"{NS}:block/{name}", "display": item_display(base_boxes)})
        # blockstate de la base
        dump(A / "blockstates" / f"{name}.json", {"variants": {
            f"facing={f}": ({"model": f"{NS}:block/{name}"} | ({"y": r} if r else {})) for f, r in FACINGS}})
        # loot table: se suelta a sí misma (también si explota: es carísima)
        dump(D / NS / "loot_table" / "blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}]})
        for level in (1, 2):
            for f, r in FACINGS:
                m = f"{NS}:block/{name}_top" if (level == 2 and top_boxes) else f"{NS}:block/{name}_part"
                v = {"model": m}
                if r and m.endswith("_top"):
                    v["y"] = r
                part_variants[f"facing={f},level={level},tier={idx}"] = v
    dump(A / "blockstates" / "statue_part.json", {"variants": part_variants})

    names = [f"{NS}:statue_{t[0]}" for t in TIERS]
    dump(D / NS / "tags" / "block" / "luxury_statues.json", {"replace": False, "values": names})
    dump(D / NS / "tags" / "item" / "luxury_statues.json", {"replace": False, "values": names})
    dump(D / "minecraft" / "tags" / "block" / "mineable" / "pickaxe.json",
         {"replace": False, "values": names + [f"{NS}:statue_part"]})
    print("Estatuas generadas:", len(TIERS), "niveles,", len(TEXTURES), "texturas")


if __name__ == "__main__":
    main()
