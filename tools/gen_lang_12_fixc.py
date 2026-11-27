#!/usr/bin/env python3
"""1.1 texts (Group C review fixes): graffiti on shop walls (appears in grimy shops, erased by wall fresheners or the
deodorant spray), wide-door placement help and refusals, the phone's "Call Seller" app label and its result notice,
the "seller already at your shop" refusal, and the dirty-shop con in the Stats app.
Code: shop/GraffitiBlock.java, shop/Cleanliness.java, shop/WideDoorBlock.java, shop/CleanBlocks.java,
client/phone/PhoneClient.java, entity/FurnitureSellerEntity.java, phone/PhoneStats.java.

Usage: python tools/gen_lang_12_fixc.py  (idempotent: sets these keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

# key: (Spanish, English)
T = {
    # graffiti
    "block.tcgshop.graffiti": ("Grafiti", "Graffiti"),
    "tcgshop.graffiti.appeared": (
        "¡Alguien rayó una pared de tu tienda! Los aromatizantes de pared lo borran, o tállalo con el spray desodorante.",
        "Someone tagged a wall in your shop! Wall fresheners erase it, or scrub it off with the deodorant spray."),
    "tcgshop.graffiti.need_spray": (
        "Necesitas el spray desodorante para quitar ese grafiti (o un aromatizante de pared cerca).",
        "You need the deodorant spray to remove that graffiti (or a wall freshener nearby)."),
    "tcgshop.graffiti.cleaned": ("¡Pared limpia! Limpieza de la tienda: %s%%", "Wall cleaned! Shop cleanliness: %s%%"),
    "tcgshop.status.graffiti": ("Grafitis", "Graffiti"),
    # updated cleaning texts (now mention graffiti)
    "block.tcgshop.wall_freshener.tip": (
        "Se cuelga en la pared. Dentro de tu tienda suelta un rocío cada 30 s que quita mal olor, mugre y grafitis cercanos.",
        "Hangs on a wall. Inside your shop it puffs every 30 s, removing bad smell, grime and nearby graffiti."),
    "tcgshop.freshener.status": (
        "Aromatizante: %s/%s cargas. Rocía cada 30 s si hay mal olor, mugre o grafitis.",
        "Air freshener: %s/%s charges. Puffs every 30 s while there is bad smell, grime or graffiti."),
    "item.tcgshop.deodorant_spray.tip": (
        "Clic derecho para rociar. Quita el mal olor de tu tienda y de los clientes apestosos. Clic derecho en un grafiti para borrarlo.",
        "Right-click to spray. Removes bad smell from your shop and from smelly customers. Right-click a graffiti to scrub it off."),
    "tcgshop.rep.tip.clean": (
        "La tienda está sucia: limpia la popó con el trapeador, trapea el piso, borra los grafitis, usa el spray desodorante y cuelga aromatizantes de pared.",
        "The shop is dirty: clean droppings with the mop, mop the floor, remove graffiti, use the deodorant spray and hang wall fresheners."),
    # wide door
    "block.tcgshop.wide_door.tip": (
        "Cortina de 3 bloques de ancho y 2 de alto que abre y cierra entera. Los clientes la usan como cualquier puerta.",
        "A shutter 3 blocks wide and 2 high that opens and closes as one. Customers use it like any door."),
    "block.tcgshop.wide_door.tip2": (
        "Se coloca desde el centro: necesita 3x2 espacios libres y 3 bloques sólidos de piso.",
        "Placed from the center: needs 3x2 free spaces and 3 solid floor blocks."),
    "tcgshop.wide_door.no_room": (
        "La cortina necesita 3x2 espacios libres, con el bloque que apuntas en el centro.",
        "The shutter needs 3x2 free spaces, with the block you aim at in the middle."),
    "tcgshop.wide_door.no_floor": (
        "La cortina necesita 3 bloques sólidos de piso debajo.",
        "The shutter needs 3 solid floor blocks under it."),
    "tcgshop.wide_door.occupied": (
        "Hay alguien parado donde va la cortina.",
        "Someone is standing where the shutter would go."),
    # phone: Furniture Seller call
    "tcgshop.phone.furncall": ("Mueblero", "Call Seller"),
    "tcgshop.phone.furncall.calling": (
        "¡Va en camino! Llegará a tu tienda en un momento.",
        "On the way! They'll be at your shop in a moment."),
    "tcgshop.phoneup.furn_call.near_shop": (
        "El Vendedor de Muebles ya está en tu tienda. Ve a buscarlo.",
        "The Furniture Seller is already at your shop. Go and find them."),
    # stats app
    "tcgshop.stats.con.dirty": (
        "La tienda está sucia (limpieza %s%%): popó, grafitis o mugre.",
        "The shop is dirty (cleanliness %s%%): droppings, graffiti or grime."),
    "tcgshop.stats.pro.clean": ("Tienda impecable y sin malos olores.", "Spotless shop with no bad smells."),
}


def main():
    pe, pn = LANG / "es_es.json", LANG / "en_us.json"
    es = json.loads(pe.read_text(encoding="utf-8"))
    en = json.loads(pn.read_text(encoding="utf-8"))
    for k, (a, b) in T.items():
        es[k], en[k] = a, b
    pe.write_text(json.dumps(es, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    pn.write_text(json.dumps(en, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
