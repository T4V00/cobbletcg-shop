"""Logo del proyecto (512x512): toldo de tienda y tres cartas en abanico con brillo holo. Sin marcas registradas."""
import math
import struct
import zlib
from pathlib import Path

N, SS = 512, 2  # supermuestreo 2x2 para bordes suaves
OUT = Path(__file__).resolve().parent.parent / "release/logo.png"


def png(path, w, h, rows):
    raw = b"".join(b"\x00" + bytes(r) for r in rows)
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def hsv(h, s, v):
    i = int(h * 6) % 6
    f = h * 6 - int(h * 6)
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    return [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)][i]


def rounded_rect_sdf(px, py, cx, cy, hw, hh, r, ang):
    # punto en el sistema de la carta (rotada ang radianes alrededor de su centro)
    dx, dy = px - cx, py - cy
    ca, sa = math.cos(-ang), math.sin(-ang)
    x, y = dx * ca - dy * sa, dx * sa + dy * ca
    qx, qy = abs(x) - hw + r, abs(y) - hh + r
    out = math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r
    return out, x, y


CARDS = [  # centro, rotación, tono holo
    (196, 300, math.radians(-18), 0.55),
    (316, 300, math.radians(18), 0.08),
    (256, 280, 0.0, 0.83),
]
HW, HH, R = 78, 108, 12


def shade(x, y):
    # fondo: degradado radial azul noche -> violeta
    d = math.hypot(x - 256, y - 300) / 360
    col = mix((0.20, 0.16, 0.42), (0.04, 0.04, 0.10), min(1, d))
    # destellos de fondo
    for sx, sy, s in ((80, 120, 3), (430, 150, 2.5), (60, 420, 2), (450, 430, 3), (380, 80, 2)):
        g = max(0, 1 - math.hypot(x - sx, y - sy) / (s * 4))
        col = mix(col, (1, 0.95, 0.8), g * 0.9)
    # toldo de tienda a rayas con borde ondulado
    stripe = int(x // 64) % 2
    wave = 120 + 14 * math.cos((x % 64) / 64 * math.pi * 2 - math.pi)
    if y < wave and y > 34:
        c = (0.86, 0.24, 0.24) if stripe == 0 else (0.97, 0.94, 0.88)
        c = mix(c, (0, 0, 0), max(0, (y - 34) / (wave - 34)) * 0.18)
        col = c
    if 22 <= y <= 40:
        col = (0.36, 0.22, 0.14)  # barra del toldo
    # cartas (de atrás hacia delante)
    for cx, cy, ang, hue in CARDS:
        dist, lx, ly = rounded_rect_sdf(x, y, cx, cy, HW, HH, R, ang)
        # sombra
        sd, _, _ = rounded_rect_sdf(x - 8, y - 12, cx, cy, HW, HH, R, ang)
        if sd < 10:
            col = mix(col, (0, 0, 0), max(0, min(1, (10 - sd) / 14)) * 0.5)
        if dist < 1:
            a = max(0, min(1, 1 - dist))
            border = dist > -7
            if border:
                c = mix((1.0, 0.82, 0.30), (0.75, 0.52, 0.12), (ly + HH) / (2 * HH))
            else:
                # cara holo: arcoíris en diagonal con bandas de brillo
                t = (lx * 0.6 + ly * 0.9) / 260 + hue
                c = hsv(t % 1, 0.45, 1.0)
                band = 0.5 + 0.5 * math.sin((lx - ly) / 18)
                c = mix(c, (1, 1, 1), band * 0.25)
                # ventana de ilustración
                if -HW + 14 < lx < HW - 14 and -HH + 22 < ly < 8:
                    c = mix(c, (0.12, 0.14, 0.30), 0.55)
                    # estrella central
                    rr = math.hypot(lx, ly + 40)
                    th = math.atan2(ly + 40, lx)
                    star = 24 * (0.55 + 0.45 * math.cos(5 * th))
                    if rr < star:
                        c = mix((1, 0.9, 0.35), (1, 1, 1), 1 - rr / max(1, star))
                # líneas de texto
                if 22 < ly < 26 and abs(lx) < HW - 18 or 36 < ly < 40 and abs(lx) < HW - 30:
                    c = mix(c, (0.2, 0.2, 0.3), 0.5)
            col = mix(col, c, a)
    # moneda dorada abajo a la derecha
    dc = math.hypot(x - 410, y - 430)
    if dc < 46:
        a = max(0, min(1, 46 - dc))
        c = mix((1.0, 0.85, 0.35), (0.72, 0.5, 0.1), (y - 384) / 92)
        if 30 < dc < 36:
            c = mix(c, (0.6, 0.4, 0.08), 0.6)
        if abs(x - 410) < 4 and abs(y - 430) < 20 or abs(y - 430) < 4 and abs(x - 410) < 20:
            c = (1, 0.96, 0.75)
        col = mix(col, c, a)
    return col


rows = []
for py in range(N):
    row = bytearray()
    for px in range(N):
        acc = [0.0, 0.0, 0.0]
        for sy in range(SS):
            for sx in range(SS):
                c = shade(px + (sx + 0.5) / SS, py + (sy + 0.5) / SS)
                for i in range(3):
                    acc[i] += c[i]
        row += bytes(max(0, min(255, int(v / (SS * SS) * 255))) for v in acc)
    rows.append(row)
OUT.parent.mkdir(parents=True, exist_ok=True)
png(OUT, N, N, rows)
print("logo:", OUT)
