#!/usr/bin/env python3
"""1.0 world loot assets: the trash can (textures, 4 block models, blockstate, item model, loot table, recipe)
and the world generation data (trash can features, Bankrupt Card Shop structure + structure set, biome tags).

All art is our own 16x16 pixel art generated here. Front of the models = north (-Z), like the shop furniture.
Usage: python tools/gen_worldloot_assets.py  (only writes files of the world loot area)
"""
import json
import random
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
A = ROOT / "assets/tcgshop"
DATA = ROOT / "data/tcgshop"
spec = importlib.util.spec_from_file_location("gt", Path(__file__).resolve().parent / "gen_textures.py")
gt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gt)
png, hexc = gt.png, gt.hexc


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1), encoding="utf-8")


def shade(c, k):
    r, g, b, a = c
    return (max(0, min(255, int(r * k))), max(0, min(255, int(g * k))), max(0, min(255, int(b * k))), a)


def mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,)


def grid(fill):
    return [[fill(x, y) for x in range(16)] for y in range(16)]


# ---------------------------------------------------------------- textures (all opaque)

def tex_metal(rust=False):
    """Galvanized steel with vertical ribs and a couple of dents (or the same can eaten by rust)."""
    rnd = random.Random(21 if not rust else 22)
    base = hexc("9AA3AD")
    rust_c = [hexc("8A4B2A"), hexc("A65E32"), hexc("6E3A20"), hexc("B8743E")]
    dents = {(4, 6), (5, 6), (4, 7), (11, 11), (12, 11)}

    def f(x, y):
        if y in (0, 15):
            c = hexc("6F7882")
        elif x % 3 == 0:
            c = hexc("C3CAD2")
        elif x % 3 == 2:
            c = hexc("727B86")
        else:
            c = shade(base, 0.94 + rnd.random() * 0.1)
        if (x, y) in dents:
            c = shade(c, 0.72)
        if rust:
            # big rust patches + streaks running down
            n = (x * 7 + y * 13) % 11
            if n < 7 or (x % 5 == 1 and y > 4) or rnd.random() < 0.35:
                c = mix(c, rust_c[rnd.randrange(len(rust_c))], 0.75 + rnd.random() * 0.25)
        return c
    return grid(f)


def tex_rim(rust=False):
    rnd = random.Random(31 if not rust else 32)

    def f(x, y):
        c = hexc("8C949D") if y in (0, 1) else hexc("5E666F")
        if y == 0:
            c = hexc("B4BCC4")
        c = shade(c, 0.95 + rnd.random() * 0.08)
        if rust and rnd.random() < 0.6:
            c = mix(c, hexc("7A3F22"), 0.6 + rnd.random() * 0.3)
        return c
    return grid(f)


def tex_lid(rust=False):
    """Round lid seen from above: concentric pressed rings."""
    rnd = random.Random(41 if not rust else 42)

    def f(x, y):
        d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
        if d > 7.2:
            c = hexc("6A727C")
        elif 5.2 < d <= 6.2:
            c = hexc("C7CED6")
        elif 2.6 < d <= 3.4:
            c = hexc("7E8791")
        else:
            c = shade(hexc("A9B1BA"), 0.95 + rnd.random() * 0.08)
        if rust and rnd.random() < 0.55:
            c = mix(c, hexc("94502C"), 0.55 + rnd.random() * 0.4)
        return c
    return grid(f)


def tex_inside():
    rnd = random.Random(51)
    return grid(lambda x, y: shade(hexc("2A2D31"), 0.85 + rnd.random() * 0.3))


def tex_bag():
    """Black garbage bag with diagonal plastic sheen."""
    rnd = random.Random(61)

    def f(x, y):
        d = (x + y) % 9
        if d == 0:
            return hexc("4A4E57")
        if d == 1:
            return hexc("34373E")
        return shade(hexc("1E1F22"), 0.9 + rnd.random() * 0.25)
    return grid(f)


def tex_paper():
    """Crumpled off-white paper with grey creases and a few printed lines."""
    rnd = random.Random(71)

    def f(x, y):
        if (x * 3 + y * 5) % 13 == 0 or (x - y) % 7 == 0 and rnd.random() < 0.5:
            return hexc("B9B4A6")
        if y % 4 == 2 and 2 <= x <= 13 and rnd.random() < 0.7:
            return hexc("8E8A80")
        return shade(hexc("E8E4D8"), 0.94 + rnd.random() * 0.08)
    return grid(f)


def tex_peel():
    """Banana peel: yellow with brown ripeness spots."""
    rnd = random.Random(81)

    def f(x, y):
        if rnd.random() < 0.12:
            return hexc("6B4A1E")
        if x % 5 == 0:
            return hexc("D9B52C")
        return shade(hexc("F2D447"), 0.92 + rnd.random() * 0.1)
    return grid(f)


def tex_card():
    """A generic (non-Pokemon) trading card back: navy with a gold frame and a pale emblem."""
    def f(x, y):
        if x in (0, 15) or y in (0, 15):
            return hexc("C9A23A")
        if x in (1, 14) or y in (1, 14):
            return hexc("E5C04A")
        d = ((x - 7.5) ** 2 + ((y - 7.5) * 0.8) ** 2) ** 0.5
        if d < 2.2:
            return hexc("E8EEF8")
        if d < 3.2:
            return hexc("6F8FD0")
        return hexc("1F3A7A") if (x + y) % 4 else hexc("26458C")
    return grid(f)


TEX = {
    "trash_can_metal": lambda: tex_metal(False),
    "trash_can_rim": lambda: tex_rim(False),
    "trash_can_lid": lambda: tex_lid(False),
    "trash_can_rust": lambda: tex_metal(True),
    "trash_can_rust_rim": lambda: tex_rim(True),
    "trash_can_rust_lid": lambda: tex_lid(True),
    "trash_can_inside": tex_inside,
    "trash_bag": tex_bag,
    "trash_paper": tex_paper,
    "trash_peel": tex_peel,
    "trash_card": tex_card,
}


# ---------------------------------------------------------------- models

FACES = ["north", "south", "east", "west", "up", "down"]


def el(frm, to, tex, rot=None, faces=None):
    """Element with auto UVs. tex: '#var' for every face, or dict face -> '#var' ('*' = default)."""
    if isinstance(tex, str):
        tex = {"*": tex}
    fs = {}
    for n in faces or FACES:
        t = tex.get(n, tex.get("*"))
        if t:
            fs[n] = {"texture": t}
    e = {"from": frm, "to": to, "faces": fs}
    if rot:
        e["rotation"] = rot
    return e


def rot(axis, angle, origin):
    return {"origin": origin, "axis": axis, "angle": angle}


def body_common():
    """Base ring, body, two bands and the side handles (shared by both models)."""
    return [
        el([3, 0, 3], [13, 1, 13], "#rim"),
        el([3.5, 1, 3.5], [12.5, 12, 12.5], {"*": "#metal", "up": "#inside", "down": "#rim"}),
        el([3.25, 3, 3.25], [12.75, 4, 12.75], "#rim"),
        el([3.25, 8, 3.25], [12.75, 9, 12.75], "#rim"),
        # side handles: two posts and a bar on each side
        el([2.25, 9.5, 6.5], [3.5, 10.5, 7.25], "#rim"),
        el([2.25, 9.5, 8.75], [3.5, 10.5, 9.5], "#rim"),
        el([1.75, 9.5, 6.5], [2.25, 10.5, 9.5], "#rim"),
        el([12.5, 9.5, 6.5], [13.75, 10.5, 7.25], "#rim"),
        el([12.5, 9.5, 8.75], [13.75, 10.5, 9.5], "#rim"),
        el([13.75, 9.5, 6.5], [14.25, 10.5, 9.5], "#rim"),
    ]


def model_closed():
    els = body_common() + [
        el([3, 12, 3], [13, 13, 13], "#rim"),
        # lid, dome and handle
        el([2.75, 13, 2.75], [13.25, 14, 13.25], {"*": "#rim", "up": "#lid", "down": "#lid"}),
        el([4.5, 14, 4.5], [11.5, 14.75, 11.5], {"*": "#rim", "up": "#lid"}),
        el([6, 14.75, 7.5], [6.75, 15.5, 8.5], "#rim"),
        el([9.25, 14.75, 7.5], [10, 15.5, 8.5], "#rim"),
        el([6, 15.5, 7.5], [10, 16, 8.5], "#rim"),
        # trash: a sheet caught under the lid, a peel draped over the back edge
        el([5, 12.25, 2.25], [8.5, 13.5, 2.75], "#paper", rot("x", 22.5, [6.75, 13, 2.5])),
        el([9.5, 13.75, 11.5], [11.5, 14.25, 13.75], "#peel"),
        el([9.5, 11, 13.25], [11.5, 13.75, 13.75], "#peel"),
        # on the ground: a paper ball and a dropped card leaning on the can
        el([12, 0, 1.5], [14, 1.75, 3.5], "#paper", rot("y", 22.5, [13, 0, 2.5])),
        el([1.25, 0, 3], [1.75, 3.5, 5.5], {"*": "#card", "up": "#paper", "down": "#paper"}, rot("z", -22.5, [1.5, 0, 4])),
    ]
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"particle": "tcgshop:block/trash_can_metal", "metal": "tcgshop:block/trash_can_metal",
                         "rim": "tcgshop:block/trash_can_rim", "lid": "tcgshop:block/trash_can_lid",
                         "inside": "tcgshop:block/trash_can_inside", "bag": "tcgshop:block/trash_bag",
                         "paper": "tcgshop:block/trash_paper", "peel": "tcgshop:block/trash_peel",
                         "card": "tcgshop:block/trash_card"},
            "elements": els}


def model_searched():
    els = body_common() + [
        # open rim (a ring, so the inside shows)
        el([3, 12, 3], [13, 13, 4], "#rim"),
        el([3, 12, 12], [13, 13, 13], "#rim"),
        el([3, 12, 4], [4, 13, 12], "#rim"),
        el([12, 12, 4], [13, 13, 12], "#rim"),
        # a tied garbage bag peeking out
        el([4.5, 12, 4.5], [11.5, 12.75, 11.5], "#bag"),
        el([7.25, 12.75, 7.25], [8.75, 13.75, 8.75], "#bag"),
        # the lid leaning against the east side
        el([14.75, 0, 3], [15.75, 9, 13], {"*": "#rim", "east": "#lid", "west": "#lid"}, rot("z", 22.5, [15.25, 0, 8])),
        # spilled trash on the ground
        el([0.5, 0, 0.5], [2.5, 1.75, 2.5], "#paper"),
        el([0.5, 0, 5], [3, 0.25, 8.5], "#paper", rot("y", 22.5, [1.75, 0, 6.75])),
        el([0.75, 0, 9], [2.75, 0.25, 11.75], {"*": "#card", "down": "#paper"}),
        el([1, 0, 12.5], [3, 0.5, 15], "#peel"),
    ]
    m = model_closed()
    m["elements"] = els
    return m


def child(parent, textures):
    return {"parent": "tcgshop:block/" + parent, "textures": textures}


RUST = {"particle": "tcgshop:block/trash_can_rust", "metal": "tcgshop:block/trash_can_rust",
        "rim": "tcgshop:block/trash_can_rust_rim", "lid": "tcgshop:block/trash_can_rust_lid"}


def blockstate():
    variants = {}
    rot_y = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in rot_y.items():
        for loot in ("none", "street", "bankrupt", "wild"):
            for searched in ("false", "true"):
                name = "trash_can" + ("_rusty" if loot == "bankrupt" else "") + ("_searched" if searched == "true" else "")
                v = {"model": "tcgshop:block/" + name}
                if y:
                    v["y"] = y
                variants[f"facing={facing},loot={loot},searched={searched}"] = v
    return {"variants": variants}


# ---------------------------------------------------------------- data

def loot_table():
    return {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "tcgshop:trash_can"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def recipe():
    return {"type": "minecraft:crafting_shaped", "category": "misc",
            "pattern": [" N ", "I I", "III"],
            "key": {"N": {"item": "minecraft:iron_nugget"}, "I": {"item": "minecraft:iron_ingot"}},
            "result": {"id": "tcgshop:trash_can", "count": 1}}


def worldgen():
    out = {}
    for name in ("trash_can_street", "trash_can_wild"):
        out[f"worldgen/configured_feature/{name}.json"] = {"type": f"tcgshop:{name}", "config": {}}
    # street: the feature scans its whole chunk for village paths; the origin is only used for the biome check
    out["worldgen/placed_feature/trash_can_street.json"] = {"feature": "tcgshop:trash_can_street", "placement": [
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
        {"type": "minecraft:biome"}]}
    # wild: one random surface spot per chunk; the per-chunk chance is in config (wildCanChance)
    out["worldgen/placed_feature/trash_can_wild.json"] = {"feature": "tcgshop:trash_can_wild", "placement": [
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
        {"type": "minecraft:biome"}]}
    out["worldgen/structure/bankrupt_card_shop.json"] = {
        "type": "tcgshop:bankrupt_card_shop",
        "biomes": "#tcgshop:has_structure/bankrupt_card_shop",
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin"}
    # one candidate per 20x20 chunk region; BankruptShopStructure moves it next to a village or (rarely) keeps it alone
    out["worldgen/structure_set/bankrupt_card_shop.json"] = {
        "structures": [{"structure": "tcgshop:bankrupt_card_shop", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": 20, "separation": 8, "salt": 1592743801}}
    villages = [f"#minecraft:has_structure/village_{v}" for v in ("plains", "desert", "savanna", "snowy", "taiga")]
    out["tags/worldgen/biome/has_structure/bankrupt_card_shop.json"] = {"replace": False, "values": villages + [
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest", "minecraft:birch_forest",
        {"id": "#c:is_plains", "required": False}]}
    out["tags/worldgen/biome/spawns_wild_trash_cans.json"] = {"replace": False, "values": [
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:savanna",
        {"id": "#c:is_plains", "required": False}]}
    return out


def main():
    tex_dir = A / "textures/block"
    tex_dir.mkdir(parents=True, exist_ok=True)
    for name, fn in TEX.items():
        png(tex_dir / f"{name}.png", fn())
    models = A / "models/block"
    write(models / "trash_can.json", model_closed())
    write(models / "trash_can_searched.json", model_searched())
    write(models / "trash_can_rusty.json", child("trash_can", RUST))
    write(models / "trash_can_rusty_searched.json", child("trash_can_searched", RUST))
    write(A / "models/item/trash_can.json", {"parent": "tcgshop:block/trash_can"})
    write(A / "blockstates/trash_can.json", blockstate())
    write(DATA / "loot_table/blocks/trash_can.json", loot_table())
    write(DATA / "recipe/trash_can.json", recipe())
    for rel, data in worldgen().items():
        write(DATA / rel, data)
    print("world loot assets written:", len(TEX), "textures, 5 models, 1 blockstate,", len(worldgen()) + 2, "data files")


if __name__ == "__main__":
    main()
