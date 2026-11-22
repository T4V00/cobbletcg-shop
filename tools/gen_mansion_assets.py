#!/usr/bin/env python3
"""
0.9.5 - Llave de la Mansión MasterBall: textura 16x16 del objeto y su modelo.
Diseño original: llave dorada con el ojo en forma de bola roja y blanca (franja negra y botón). Nada de arte Pokémon.
Sólo biblioteca estándar (PNG con zlib + struct).

Uso:  python -I -X utf8 tools/gen_mansion_assets.py
Escribe únicamente dentro de src/main/resources. Idempotente.
"""
import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
A = ROOT / "assets" / "tcgshop"

T = (0, 0, 0, 0)


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


GOLD_L = hexc("#FFF1A6")
GOLD = hexc("#F2C230")
GOLD_D = hexc("#B07A12")
OUTLINE = hexc("#3B2606")
RED = hexc("#E3262E")
RED_D = hexc("#A3121A")
WHITE = hexc("#F4F4F4")
WHITE_D = hexc("#C9CED6")
BLACK = hexc("#1E1E24")


def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)))
    cx, cy = ax + vx * t, ay + vy * t
    return math.hypot(px - cx, py - cy), t


def key_texture():
    px = [[T for _ in range(16)] for _ in range(16)]
    # caña en diagonal (abajo-izquierda -> arriba-derecha) con brillo en el lado de arriba
    ax, ay, bx, by = 2.6, 13.4, 8.6, 7.4
    for y in range(16):
        for x in range(16):
            d, t = seg_dist(x + 0.5, y + 0.5, ax, ay, bx, by)
            if d <= 0.95:
                # lado de arriba-izquierda (x - y menor) más claro
                side = (x + 0.5 - ax) - (y + 0.5 - ay)
                px[y][x] = GOLD_L if side < -0.3 else GOLD if side < 0.6 else GOLD_D
    # dientes perpendiculares cerca de la punta
    for (tx, ty) in ((3, 13), (5, 11)):
        for k in (1, 2):
            x, y = tx + k, ty + k
            if 0 <= x < 16 and 0 <= y < 16:
                px[y][x] = GOLD if k == 1 else GOLD_D
    # ojo de la llave: aro dorado con una bola roja y blanca dentro
    cx, cy, r = 10.5, 5.0, 4.6
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            if d > 3.7:
                px[y][x] = GOLD_L if dy < -1.5 and dx < 1 else GOLD if dy < 1.5 else GOLD_D
                continue
            if d <= 1.0:
                px[y][x] = WHITE
            elif d <= 1.75:
                px[y][x] = BLACK
            elif abs(dy) <= 0.55:
                px[y][x] = BLACK
            elif dy < 0:
                px[y][x] = RED if dx < 1.2 else RED_D
            else:
                px[y][x] = WHITE if dx < 1.2 else WHITE_D
    # brillo en la bola
    px[2][9] = WHITE
    # contorno oscuro
    out = [row[:] for row in px]
    for y in range(16):
        for x in range(16):
            if px[y][x][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and px[ny][nx][3] and px[ny][nx] not in (OUTLINE,):
                    out[y][x] = OUTLINE
                    break
    # destello
    for (x, y) in ((15, 0), (14, 1), (15, 2)):
        if not out[y][x][3]:
            out[y][x] = (255, 255, 230, 200)
    return out


def main():
    write_png(A / "textures" / "item" / "mansion_key.png", key_texture())
    model = {"parent": "minecraft:item/generated", "textures": {"layer0": "tcgshop:item/mansion_key"}}
    p = A / "models" / "item" / "mansion_key.json"
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    print("gen_mansion_assets: textura y modelo de mansion_key")


if __name__ == "__main__":
    main()
