"""Texts for the 1.0 display fixes (shop/ShopDisplayBlock): placement inside the shop and why a display takes nothing.

Usage: python tools/gen_lang_10_displays.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    "tcgshop.display.must_fit": (
        "El expositor (%s×%s×%s) tiene que caber entero dentro de tu tienda",
        "The display (%s×%s×%s) must fit entirely inside your shop",
    ),
    "tcgshop.display.sticks_out": (
        "Este expositor se sale de tu tienda y no puede vender: rómpelo y colócalo entero dentro de la zona",
        "This display sticks out of your shop and can't sell: break it and place it fully inside the area",
    ),
    "tcgshop.display.no_shop": (
        "Este expositor no está dentro de tu tienda, así que no vende nada. Los expositores de casa funcionan en cualquier sitio",
        "This display isn't inside your shop, so it can't sell anything. Home displays work anywhere",
    ),
    "tcgshop.display.broken": (
        "A este expositor le falta una parte: rómpelo y vuelve a colocarlo",
        "This display is missing a part: break it and place it again",
    ),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
