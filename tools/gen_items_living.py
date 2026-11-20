#!/usr/bin/env python3
"""Texturas y modelos de objetos nuevos: spray desodorante y tablero de duelo."""
import json
import importlib.util
from pathlib import Path

A = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop"
spec = importlib.util.spec_from_file_location("gt", Path(__file__).resolve().parent / "gen_textures.py")
gt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gt)
png, hexc, T = gt.png, gt.hexc, gt.T


def spray():
    px = [[T] * 16 for _ in range(16)]
    body, light, dark, cap, label = hexc("3FA7E0"), hexc("8FD3F7"), hexc("1F6E9E"), hexc("E8E8E8"), hexc("FFFFFF")
    for y in range(5, 16):
        for x in range(5, 11):
            px[y][x] = dark if x in (5, 10) or y == 15 else (light if x == 6 else body)
    for y in range(8, 12):
        for x in range(6, 10):
            px[y][x] = label
    px[9][7] = px[9][8] = px[10][7] = hexc("7BC96F")
    for y in range(2, 5):
        for x in range(6, 10):
            px[y][x] = cap if y > 2 else hexc("BDBDBD")
    px[3][10] = px[3][11] = hexc("9E9E9E")      # boquilla
    for (x, y) in [(13, 1), (12, 3), (14, 4), (13, 2)]:
        px[y][x] = hexc("CFEFFF")               # niebla
    return px


def main():
    png(A / "textures/item/deodorant_spray.png", spray())
    (A / "models/item").mkdir(parents=True, exist_ok=True)
    (A / "models/item/deodorant_spray.json").write_text(json.dumps(
        {"parent": "minecraft:item/handheld", "textures": {"layer0": "tcgshop:item/deodorant_spray"}}, indent=1), encoding="utf-8")
    print("objetos nuevos generados")


if __name__ == "__main__":
    main()
