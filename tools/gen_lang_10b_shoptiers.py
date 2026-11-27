"""Texts for the 1.0 shop tiers (shop/ShopTier, ShopCoreBlock, ShopProtection): 12 tiers, Paper stall,
one shop per player until Amethyst, in-place upgrades.

Usage: python tools/gen_lang_10b_shoptiers.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ shop blocks (the name shows the new side)
    "block.tcgshop.shop_core_paper": ("Puesto de Papel (5×5)", "Paper Stall (5×5)"),
    "block.tcgshop.shop_core_wood": ("Tienda de Madera (10×10)", "Wooden Shop (10×10)"),
    "block.tcgshop.shop_core_brick": ("Tienda de Ladrillo (16×16)", "Brick Shop (16×16)"),
    "block.tcgshop.shop_core_copper": ("Tienda de Cobre (32×32)", "Copper Shop (32×32)"),
    "block.tcgshop.shop_core_iron": ("Tienda de Hierro (40×40)", "Iron Shop (40×40)"),
    "block.tcgshop.shop_core_lapis": ("Tienda de Lapislázuli (56×56)", "Lapis Shop (56×56)"),
    "block.tcgshop.shop_core_amethyst": ("Tienda de Amatista (72×72)", "Amethyst Shop (72×72)"),

    # ------------------------------------------------------------------ tier names
    "tcgshop.shop.tier.paper": ("Papel", "Paper"),
    "tcgshop.shop.tier.brick": ("Ladrillo", "Brick"),
    "tcgshop.shop.tier.copper": ("Cobre", "Copper"),
    "tcgshop.shop.tier.lapis": ("Lapislázuli", "Lapis"),
    "tcgshop.shop.tier.amethyst": ("Amatista", "Amethyst"),

    # ------------------------------------------------------------------ one shop per player
    "tcgshop.shop.one_per_player": ("Solo puedes tener una tienda hasta que una de las tuyas sea de %s o superior.",
                                    "You can only own one shop until one of yours is %s or higher."),
    "tcgshop.shop.one_per_player.hint": ("Mejor mejora la que ya tienes: clic derecho en su bloque de tienda con uno de nivel superior y lo conservas todo.",
                                         "Upgrade the one you have instead: right-click its shop block with a higher-tier one and you keep everything."),
    "tcgshop.shop.multi_unlocked": ("¡Enhorabuena! Tu tienda ya es de %s: a partir de ahora puedes tener más de una tienda.",
                                    "Congratulations! Your shop is now %s: from now on you can own more than one shop."),
    "tcgshop.shop.max_shops": ("Ya tienes el máximo de tiendas permitido por jugador (%s).",
                               "You already own the most shops allowed per player (%s)."),
    "tcgshop.shop.max_shops.hint": ("Mejor mejora una de las que tienes: clic derecho en su bloque de tienda con uno de nivel superior y lo conservas todo.",
                                    "Upgrade one of the shops you have instead: right-click its shop block with a higher-tier one and you keep everything."),

    # ------------------------------------------------------------------ Paper stall
    "tcgshop.shop.stall_hint": ("Un comienzo humilde: en el puesto cabe %s expositor pequeño, atiende a %s cliente a la vez y pasa poca gente. ¡Mejóralo cuando puedas!",
                                "A humble start: the stall holds %s small display, serves %s customer at a time and few people stop by. Upgrade it when you can!"),
    "tcgshop.shop.stall_display_limit": ("Un puesto de %s solo tiene sitio para %s expositor de venta. Mejóralo para poner más.",
                                         "A %s stall only has room for %s selling display. Upgrade it to add more."),
    "tcgshop.shop.stall_display_too_big": ("En un puesto de %s solo cabe un expositor pequeño (hasta %s huecos y sin RGB). Mejóralo para usar uno más grande.",
                                           "A %s stall only fits a small display (up to %s slots, no RGB). Upgrade it to use a bigger one."),

    # ------------------------------------------------------------------ in-place upgrade
    "tcgshop.shop.upgrade_not_higher": ("Tu tienda ya es de %s: usa un bloque de tienda de nivel superior para mejorarla.",
                                        "Your shop is already %s: use a higher-tier shop block to upgrade it."),
    "tcgshop.shop.upgrade_overlap": ("No se puede mejorar: la zona de %s×%s se solaparía con otra tienda.",
                                     "Can't upgrade: the %s×%s area would overlap another shop."),
    "tcgshop.shop.upgraded": ("¡Tienda mejorada a %s! Zona protegida de %s×%s bloques. Lo conservas todo: género, empleados, reseñas y estadísticas.",
                              "Shop upgraded to %s! Protected area of %s×%s blocks. You keep everything: stock, staff, reviews and stats."),
    "tcgshop.status.upgrade": ("¡Tu tienda se llena y la gente se va! Mejórala a %s (clic derecho en el bloque de tu tienda con el nuevo): aforo %s.",
                               "Your shop keeps filling up and people leave! Upgrade it to %s (right-click your shop block with the new one): capacity %s."),
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
