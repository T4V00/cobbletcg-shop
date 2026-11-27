"""1.0 texts for the traders: the Wholesaler buys gold, diamonds and Poke Balls, and every trader buys Mega Stones
(shop/TraderGoods, client/screen/TraderScreen, client/screen/FurnitureScreen).

Usage: python tools/gen_lang_10_traders.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ Mega Stones (any trader)
    "tcgshop.sell.mega_btn": ("Vender %s Megapiedra(s) · %s", "Sell %s Mega Stone(s) · %s"),
    "tcgshop.sell.mega_hint": ("Cualquier comerciante te paga %s por cada Megapiedra. Se venden todas las que lleves en el inventario.",
                               "Any trader pays you %s for each Mega Stone. Every one in your inventory is sold."),
    "tcgshop.sell.mega_done": ("Vendes %s Megapiedra(s) por %s.", "You sell %s Mega Stone(s) for %s."),
    "tcgshop.sell.no_mega": ("No llevas Megapiedras.", "You aren't carrying any Mega Stones."),

    # ------------------------------------------------------------------ Wholesaler (changed texts)
    "tcgshop.sell.wholesaler_hint": ("Cartas al 40% · también oro, diamantes, Poké Balls y Megapiedras:",
                                     "Cards at 40% · also gold, diamonds, Poké Balls and Mega Stones:"),
    "tcgshop.wholesaler.tip_emeralds": ("¿Te faltan CobbleDollars? Tráeme esmeraldas u oro: pago %s por cada uno. También compro diamantes, Poké Balls, Megapiedras y cartas (pestaña Vender).",
                                        "Short on CobbleDollars? Bring me emeralds or gold: I pay %s each. I also buy diamonds, Poké Balls, Mega Stones and cards (Sell tab)."),
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
