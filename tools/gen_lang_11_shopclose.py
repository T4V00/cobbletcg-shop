"""1.1 texts, area "shopclose": shop OPEN / CLOSED (phone, status window, sneak-click on the core, goggles) and dormant
shops (core removed: linked core item, restore, abandon from the phone). Code: shop/ShopOpenClose.java.

Usage: python tools/gen_lang_11_shopclose.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ open / closed: chat
    "tcgshop.shop.now_closed": ("Tu tienda ahora está cerrada. Los clientes se van sin dejar reseña y todo queda en pausa hasta que la vuelvas a abrir.",
                                "Your shop is now closed. Customers leave without reviewing it and everything is paused until you open it again."),
    "tcgshop.shop.now_open": ("Tu tienda ahora está abierta. ¡Los clientes ya pueden volver!",
                              "Your shop is now open. Customers can come back!"),
    "tcgshop.shop.closed_no_sale": ("La tienda de %s está cerrada. Vuelve cuando abra.",
                                    "%s's shop is closed. Come back when it opens."),
    "tcgshop.shop.closed_no_customers": ("Tu tienda está cerrada, así que no puede recibir clientes. Ábrela primero.",
                                         "Your shop is closed, so it cannot receive customers. Open it first."),

    # ------------------------------------------------------------------ open / closed: phone and status window
    "tcgshop.phone.shop_close": ("Cerrar tienda", "Close shop"),
    "tcgshop.phone.shop_open": ("Abrir tienda", "Open shop"),
    "tcgshop.status.shop_close": ("Cerrar tienda", "Close shop"),
    "tcgshop.status.shop_open": ("Abrir tienda", "Open shop"),
    "tcgshop.status.state_open": ("Tienda abierta", "Shop open"),
    "tcgshop.status.state_closed": ("Tienda cerrada", "Shop closed"),
    "tcgshop.goggles.closed": ("CERRADA", "CLOSED"),
    "tcgshop.phone.ev.out.closed": ("Cancelado (tienda cerrada)", "Cancelled (shop closed)"),

    # ------------------------------------------------------------------ dormant shops
    "tcgshop.shop.dormant_now": ("Quitaste el bloque de tu tienda: la tienda queda inactiva y cerrada, pero no se pierde nada (caja, reputación, reseñas, personal y misiones).",
                                 "You removed your shop block: the shop is now dormant and closed, but nothing is lost (till, reputation, reviews, staff and missions)."),
    "tcgshop.shop.dormant_now.hint": ("Coloca de nuevo ese bloque de tienda (donde quieras) para restaurarla, o abandónala desde la app Tienda del celular.",
                                      "Place that shop block again (anywhere you like) to restore it, or abandon it from the phone's Shop app."),
    "tcgshop.shop.dormant_cant_open": ("Esta tienda está inactiva: coloca su bloque de tienda para restaurarla antes de abrirla.",
                                       "This shop is dormant: place its shop block to restore it before opening it."),
    "tcgshop.shop.restored": ("¡Tienda restaurada! %1$s, zona protegida de %2$s×%3$s bloques. Conserva todo su progreso.",
                              "Shop restored! %1$s, protected area of %2$s×%3$s blocks. All its progress is kept."),
    "tcgshop.shop.restored.hint": ("Sigue cerrada: ábrela desde el celular, desde su menú o haciendo clic derecho agachado en el bloque con la mano vacía.",
                                   "It is still closed: open it from the phone, from its menu, or by sneak + right-clicking the block with an empty hand."),
    "tcgshop.shop.restore_not_owner": ("Este bloque pertenece a la tienda de %s. Solo su dueño puede restaurarla.",
                                       "This block belongs to %s's shop. Only its owner can restore it."),
    "tcgshop.shop.abandon_not_dormant": ("Solo puedes abandonar una tienda inactiva (quita primero su bloque).",
                                         "You can only abandon a dormant shop (remove its block first)."),
    "tcgshop.shop.abandoned": ("Abandonaste la tienda. Su registro se borró para siempre.",
                               "You abandoned the shop. Its record has been deleted for good."),
    "tcgshop.core_item.linked": ("Tienda de %s", "%s's shop"),
    "tcgshop.core_item.linked_tier": ("%1$s · %2$s×%3$s", "%1$s · %2$s×%3$s"),
    "tcgshop.core_item.linked_hint": ("Colócalo para restaurar esta tienda con todo su progreso.",
                                      "Place it to restore this shop with all its progress."),
    "tcgshop.phone.dormant": ("Inactiva — coloca el bloque de tu tienda para restaurarla",
                              "Dormant — place your shop core to restore"),
    "tcgshop.phone.dormant_hint": ("Se conserva todo: caja, reputación, reseñas, clientes frecuentes, personal y misiones. Mientras esté inactiva no protege ninguna zona, no funciona nada y sigue contando para tu límite de tiendas.",
                                   "Everything is kept: till, reputation, reviews, regulars, staff and missions. While dormant it protects no area, nothing runs and it still counts toward your shop limit."),
    "tcgshop.shop.upgrade_linked": ("Ese bloque de tienda es la llave de una tienda inactiva y no sirve para mejorar. Colócalo en otro sitio para restaurar esa tienda (solo su dueño puede).",
                                    "That shop block is the key of a dormant shop and can't be used as an upgrade. Place it somewhere else to restore that shop (only its owner can)."),
    "tcgshop.phone.abandon": ("Abandonar tienda", "Abandon shop"),
    "tcgshop.phone.abandon_confirm": ("¿Seguro? Clic para confirmar", "Sure? Click to confirm"),
    "tcgshop.phone.abandon_warn": ("Se te pagará lo que queda en la caja (%s) y la tienda se borrará para siempre.",
                                   "The money left in the till (%s) will be paid to you and the shop will be deleted for good."),
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
