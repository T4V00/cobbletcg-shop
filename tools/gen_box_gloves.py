#!/usr/bin/env python3
"""
1.1 (Group E): art of the Box-Opening Gloves (tcgshop:box_gloves), in the style of tools/gen_items_3d.py and
tools/gen_textures.py. Original shapes only, no external art.

Writes
  textures/item/box_gloves.png       16x16 GUI sprite (deep purple leather, gold cuff, gold box-cutter claw);
  textures/item/3d/box_gloves.png    texture of the 3D model;
  models/item/3d/box_gloves.json     3D glove (cuff with a gold buckle, palm, four fingers, thumb, a gold blade on the
                                     index finger and a little box emblem on the back of the hand);
  models/item/icon/box_gloves.json   GUI icon model (the sprite);
  models/item/box_gloves.json        entry model (builtin/entity, drawn by client/render/ToolItemRenderer).

Run: python tools/gen_box_gloves.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_items_3d as g  # noqa: E402

NS = g.NS
T = g.T


def sprite():
    P = g.hexc("5B2A86"); D = g.hexc("3A1758"); H = g.hexc("7D45B0")
    G = g.hexc("E5B21E"); Y = g.hexc("FFE27A"); K = g.hexc("8A6A12"); B = g.hexc("C8D2DC")
    shape = [
        "..............B.",
        "....HHH......BY.",
        "...PHHPP.PPP.B..",
        "...PPPPPPPPPPB..",
        "..DPPPPPPPPPPP..",
        "..DPPGGGPPPPPP..",
        "..DDPGKGPPPPP...",
        "...DDGGGPPPPP...",
        "....DDPPPPPP....",
        "....GGGGGGGG....",
        "....GYGKKGYG....",
        "....KKKKKKKK....",
    ]
    px = [[T] * 16 for _ in range(16)]
    cmap = {"P": P, "D": D, "H": H, "G": G, "Y": Y, "K": K, "B": B}
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch != ".":
                px[y + 2][x] = cmap[ch]
    return px


def model():
    tex = g.Tex(64, 64, 21)
    leather = tex.material(0, 0, 16, 16, g.hexc("5b2a86"), 7)
    leather_d = tex.material(16, 0, 16, 16, g.hexc("3f1b60"), 6)
    cuff = tex.material(32, 0, 16, 16, g.hexc("d9a420"), 8, "brushed_h")
    tipc = tex.material(48, 0, 16, 16, g.hexc("2c1046"), 5)
    blade = tex.material(0, 40, 16, 8, g.hexc("cfd8e2"), 5, "brushed_v")
    buckle = tex.material(16, 40, 8, 8, g.hexc("ffe27a"), 4)
    # back of the hand: stitched lines and a small gold box emblem (lid + body)
    back = (0, 16, 24, 24)
    tex.material(0, 16, 24, 24, g.hexc("5b2a86"), 7)
    for k in (4, 19):
        for y in range(18, 38, 2):
            tex.set(k, y, g.hexc("b58ad8"))
    tex.rect(8, 24, 8, 7, g.hexc("e5b21e"))
    tex.rect(7, 22, 10, 2, g.hexc("ffe27a"))
    tex.outline(8, 24, 8, 7, g.hexc("8a6a12"))
    tex.rect(11, 25, 2, 5, g.hexc("8a6a12"))
    tex.outline(0, 16, 24, 24, g.hexc("3f1b60"))
    m = g.Model(f"{NS}:item/3d/box_gloves", 64)
    m.box((4, 1, 6.4), (11, 4.2, 9.6), g.all6(cuff))  # cuff
    m.box((6.8, 1.8, 6.1), (8.2, 3.4, 6.4), g.all6(buckle))  # buckle
    m.box((4.5, 4.2, 6.8), (10.5, 10, 9.2), g.all6(leather, south=back, north=back))  # palm
    fingers = [(4.6, 13.4), (6.1, 14.6), (7.6, 14.2), (9.1, 12.8)]
    for (x, top) in fingers:
        m.box((x, 10, 7.1), (x + 1.35, top - 0.5, 8.9), g.all6(leather, up=None))
        m.box((x, top - 0.5, 7.1), (x + 1.35, top, 8.9), g.all6(tipc, down=None))
    m.box((9.3, 12.8, 7.6), (9.95, 15.4, 8.4), g.all6(blade), (22.5, "z", (9.6, 12.8, 8)))  # box-cutter claw
    m.box((3.2, 5, 7.2), (4.6, 9.2, 8.8), g.all6(leather_d), (22.5, "z", (4.6, 5, 8)))  # thumb
    g.save_3d("box_gloves", m, tex)
    g.icon_model("box_gloves", f"{NS}:item/box_gloves")
    g.entry_model("box_gloves", {
        "firstperson_righthand": g.tf((0, -25, 5), (-1, 3, 0), 0.5),
        "thirdperson_righthand": g.tf((0, 0, 0), (0, 3, 1), 0.55),
        "ground": g.tf((0, 0, 0), (0, 2, 0), 0.5),
        "fixed": g.tf((0, 180, 0), (0, 0, 0), 1),
        "head": g.tf((0, 180, 0), (0, 13, 7), 1),
    }, f"{NS}:item/box_gloves")


def main():
    g.write_png(g.A / "textures/item/box_gloves.png", sprite())
    model()
    print("gen_box_gloves: sprite, 3D model, texture and item models written")


if __name__ == "__main__":
    main()
