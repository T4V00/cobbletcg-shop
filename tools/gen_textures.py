#!/usr/bin/env python3
"""Generates the 16x16 item textures (pixel art) with no dependencies: PNG written by hand with zlib."""
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/textures/item"


def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.write_bytes(data)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


T = (0, 0, 0, 0)


def book(cover, cover_dark, edge, emblem):
    """Book/binder seen from the front: cover with border, pages on the right and emblem in the center."""
    px = [[T] * 16 for _ in range(16)]
    for y in range(1, 15):
        for x in range(2, 14):
            px[y][x] = cover if (x + y) % 5 else cover_dark
        px[y][1] = edge                       # spine
        px[y][14] = hexc("EDE6D2")             # pages
        px[y][15] = hexc("C9C0A8") if y % 2 else hexc("DCD4BE")
    for x in range(1, 15):
        px[0][x] = hexc("1A1A1A")
        px[15][x] = hexc("1A1A1A")
    for y in range(16):
        px[y][0] = hexc("1A1A1A") if 0 < y < 15 else T
    # metal corners
    for (x, y) in [(2, 1), (3, 1), (2, 2), (13, 1), (12, 1), (13, 2), (2, 14), (3, 14), (2, 13), (13, 14), (12, 14), (13, 13)]:
        px[y][x] = hexc("E0B33C")
    emblem(px)
    return px


def pokeball(px):
    cx, cy = 8, 8
    for y in range(4, 13):
        for x in range(4, 13):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= 13:
                px[y][x] = hexc("E8463C") if y < cy else hexc("F5F5F5")
                if y == cy:
                    px[y][x] = hexc("151515")
    for (x, y) in [(7, 7), (8, 7), (9, 7), (7, 9), (8, 9), (9, 9), (7, 8), (9, 8)]:
        px[y][x] = hexc("151515")
    px[8][8] = hexc("FFFFFF")


def star(px):
    pts = [(8, 4), (8, 5), (7, 6), (8, 6), (9, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7),
           (6, 8), (7, 8), (8, 8), (9, 8), (10, 8), (7, 9), (8, 9), (9, 9), (6, 10), (7, 10), (9, 10), (10, 10), (6, 11), (10, 11)]
    for (x, y) in pts:
        px[y][x] = hexc("FFD34D")
    for (x, y) in [(8, 5), (8, 7)]:
        px[y][x] = hexc("FFF2B0")


def rings(px):
    for y in (4, 8, 12):
        px[y][1] = hexc("D9DDE3")
        px[y][2] = hexc("A8AEB8")


def bubble(inner):
    """White thought bubble (with little bubbles at the bottom left) and an icon inside."""
    W = hexc("FFFFFF"); B = hexc("2A2A2A")
    px = [[T] * 16 for _ in range(16)]
    for y in range(1, 11):
        for x in range(2, 15):
            d = ((x - 8.5) / 6.5) ** 2 + ((y - 5.5) / 4.8) ** 2
            if d <= 1.0:
                px[y][x] = W
            elif d <= 1.25:
                px[y][x] = B
    for (x, y) in [(4, 12), (5, 12), (4, 13), (5, 13), (2, 14), (3, 14), (2, 15)]:
        px[y][x] = W
    for (x, y) in [(3, 12), (6, 12), (3, 13), (6, 13), (4, 11), (5, 14), (4, 14), (1, 14), (1, 15), (3, 15)]:
        if px[y][x] == T:
            px[y][x] = B
    inner(px)
    return px


def coin(px):
    G = hexc("F2C230"); D = hexc("B8860B"); L = hexc("FFF0A0")
    for y in range(3, 9):
        for x in range(6, 12):
            if ((x - 8.5) ** 2 + (y - 5.5) ** 2) <= 8:
                px[y][x] = G
    for (x, y) in [(8, 3), (8, 4), (9, 4), (7, 5), (8, 5), (9, 6), (7, 7), (8, 7), (8, 8)]:
        px[y][x] = D
    px[4][7] = L


def heart(px):
    R = hexc("FF4F8B"); L = hexc("FFB3CC")
    pts = [(6, 3), (7, 3), (10, 3), (11, 3), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 4), (12, 4),
           (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5), (12, 5), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
           (7, 7), (8, 7), (9, 7), (10, 7), (8, 8), (9, 8)]
    for (x, y) in pts:
        px[y][x] = R
    px[4][6] = L


def gloves():
    L = hexc("8B5A2B"); D = hexc("5E3B1A"); W = hexc("D8D8D8"); S = hexc("F2F2F2")
    px = [[T] * 16 for _ in range(16)]
    shape = [
        "....LLL.........",
        "...LLLLL.LLL....",
        "...LLLLLLLLLL...",
        "...LLLLLLLLLLL..",
        "..DLLLLLLLLLLL..",
        "..DLLLLLLLLLL...",
        "..DDLLLLLLLLL...",
        "...DDLLLLLLL....",
        "....WWWWWWWW....",
        "....SWSWSWSW....",
        "....WWWWWWWW....",
    ]
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch != ".":
                px[y + 3][x] = {"L": L, "D": D, "W": W, "S": S}[ch]
    return px


def glyph(points, color):
    def f(px):
        for (x, y) in points:
            px[y][x] = hexc(color)
    return f


EXCLAIM = [(8, 2), (9, 2), (8, 3), (9, 3), (8, 4), (9, 4), (8, 5), (9, 5), (8, 6), (9, 6), (8, 8), (9, 8)]
QUESTION = [(7, 2), (8, 2), (9, 2), (10, 2), (6, 3), (11, 3), (11, 4), (10, 5), (9, 5), (8, 6), (8, 8)]
ANGRY = [(5, 3), (6, 4), (7, 5), (12, 3), (11, 4), (10, 5), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (11, 7), (5, 8), (12, 8)]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    png(OUT / "general_binder.png", book(hexc("8E2A2E"), hexc("7A2226"), hexc("4A1215"), pokeball))
    personal = book(hexc("2E5FA8"), hexc("27528F"), hexc("152E57"), star)
    rings(personal)
    png(OUT / "personal_binder.png", personal)
    ent = OUT.parent / "entity"
    ent.mkdir(parents=True, exist_ok=True)
    png(ent / "scalper_think_money.png", bubble(coin))
    png(ent / "scalper_think_love.png", bubble(heart))
    png(OUT / "nerd_gloves.png", gloves())
    png(ent / "npc_think_deal.png", bubble(glyph(EXCLAIM, "F2A20C")))
    png(ent / "npc_think_angry.png", bubble(glyph(ANGRY, "E53935")))
    png(ent / "npc_think_question.png", bubble(glyph(QUESTION, "3A7BD5")))
    print("textures generated in", OUT)


if __name__ == "__main__":
    main()
