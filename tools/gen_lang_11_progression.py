"""1.1 texts, area "progression": reselling old shop cores (phone quick sell and the Wholesaler) and the Wandering
Collector visiting your shop. Code: shop/CoreResale.java, client/screen/CardPickGrid.java, entity/TcgEntities.java.
(The tier difficulty has no player-facing text; its config help is in EconomyConfig.)

Usage: python tools/gen_lang_11_progression.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ old shop cores: selling
    "tcgshop.sell.core_linked": ("Ese bloque de tienda está vinculado a una tienda inactiva y no se puede vender. Colócalo para restaurarla o abandona la tienda desde el celular primero.",
                                 "That shop block is linked to a dormant shop and can't be sold. Place it to restore the shop, or abandon the shop from your phone first."),
    "tcgshop.sell.core_linked_other": ("Este bloque de tienda pertenece a la tienda de otra persona y no se puede vender.",
                                       "This shop block belongs to someone else's shop and can't be sold."),
    "tcgshop.sell.core_unknown_tip": ("Vinculado a otra tienda (de otra persona o que no se ve desde aquí): no se puede vender aquí",
                                      "Linked to another shop (someone else's, or one not visible from here): it can't be sold here"),
    "tcgshop.sell.locked": ("Bloqueado", "Locked"),
    "tcgshop.sell.core_locked_tip": ("Vinculado a tu tienda inactiva: colócalo o abandona la tienda para poder venderlo",
                                     "Linked to your dormant shop: place it or abandon the shop to sell it"),
    "tcgshop.sell.core_tip": ("Bloque de tienda usado: se vende al %s%% de su precio",
                              "Used shop block: sells for %s%% of its price"),
    "tcgshop.sell.core_hint": ("Los bloques de tienda que ya no uses se venden al %s%% de su precio (menos el de papel, que se puede fabricar).",
                               "Shop blocks you no longer use sell for %s%% of their price (except Paper ones, which can be crafted)."),
    "tcgshop.sell.core_short": ("Bloques de tienda al %s%%", "Shop blocks at %s%%"),

    # ------------------------------------------------------------------ the Wandering Collector visits your shop
    "tcgshop.trader.arrived_shop": ("¡Un coleccionista de cartas está visitando tu tienda!",
                                    "A card collector is visiting your shop!"),
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
