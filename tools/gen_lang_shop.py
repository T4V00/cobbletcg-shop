#!/usr/bin/env python3
"""Texts for the mega update: shop, phone, goggles, unique binders, titles, gloves. Spanish and English."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # items and blocks
    "item.tcgshop.nerd_gloves": ("Guantes de friki", "Nerd Gloves"),
    "item.tcgshop.nerd_gloves.tip1": ("Con ellos puestos, Espacio abre los sobres con un corte perfecto", "While worn, Space opens packs with a perfect cut"),
    "item.tcgshop.nerd_gloves.tip2": ("Póntelos en el slot de mano de Accessories o en la mano secundaria", "Wear them in the Accessories hand slot or your off hand"),
    "item.tcgshop.manager_goggles": ("Gafas de Gerente", "Manager Goggles"),
    "item.tcgshop.manager_goggles.tip1": ("Muestran los bordes de las tiendas y de quién son", "Show shop borders and who owns them"),
    "item.tcgshop.manager_goggles.tip2": ("En tus tiendas: ingresos, gastos y beneficio", "In your shops: revenue, costs and profit"),
    "item.tcgshop.tcg_phone": ("Teléfono TCG", "TCG Phone"),
    "item.tcgshop.tcg_phone.tip": ("Mercado online, precios, colección, tiendas y títulos", "Online market, prices, collection, shops and titles"),
    "item.tcgshop.wholesaler_spawn_egg": ("Huevo generador de Mayorista", "Wholesaler Spawn Egg"),
    "item.tcgshop.booster_box": ("Caja de sobres", "Booster Box"),
    "item.tcgshop.booster_box.named": ("Caja de sobres: %s", "Booster Box: %s"),
    "item.tcgshop.booster_box.tip": ("Contiene %s sobres sellados. Clic derecho en el aire para abrirla", "Contains %s sealed packs. Right-click in the air to open"),
    "item.tcgshop.booster_box.tip2": ("Colócala en tu tienda para venderla", "Place it in your shop to sell it"),
    "block.tcgshop.booster_box": ("Caja de sobres", "Booster Box"),
    "block.tcgshop.display_case": ("Vitrina de cartas", "Card Display Case"),
    "block.tcgshop.card_stand": ("Expositor de carta", "Card Stand"),
    "block.tcgshop.pack_shelf": ("Estantería de sobres", "Pack Shelf"),
    "block.tcgshop.cash_register": ("Caja registradora", "Cash Register"),
    "block.tcgshop.shop_core_wood": ("Tienda de Madera (16×16)", "Wooden Shop (16×16)"),
    "block.tcgshop.shop_core_stone": ("Tienda de Piedra (24×24)", "Stone Shop (24×24)"),
    "block.tcgshop.shop_core_iron": ("Tienda de Hierro (32×32)", "Iron Shop (32×32)"),
    "block.tcgshop.shop_core_gold": ("Tienda de Oro (48×48)", "Golden Shop (48×48)"),
    "block.tcgshop.shop_core_diamond": ("Tienda de Diamante (64×64)", "Diamond Shop (64×64)"),
    "block.tcgshop.shop_core_emerald": ("Tienda de Esmeralda (80×80)", "Emerald Shop (80×80)"),
    "block.tcgshop.shop_core_netherite": ("Expansión de Tienda de Netherita (100×100)", "Netherite Shop Expansion (100×100)"),
    "entity.tcgshop.wholesaler": ("Mayorista misterioso", "Mysterious Wholesaler"),
    "tcgshop.shop.tier.wood": ("Madera", "Wood"), "tcgshop.shop.tier.stone": ("Piedra", "Stone"), "tcgshop.shop.tier.iron": ("Hierro", "Iron"),
    "tcgshop.shop.tier.gold": ("Oro", "Gold"), "tcgshop.shop.tier.diamond": ("Diamante", "Diamond"),
    "tcgshop.shop.tier.emerald": ("Esmeralda", "Emerald"), "tcgshop.shop.tier.netherite": ("Netherita", "Netherite"),

    # unique binders / general binder
    "tcgshop.binder.bound": ("Binder registrado a tu nombre", "Binder registered to your name"),
    "tcgshop.binder.foreign": ("Este binder pertenece a %s", "This binder belongs to %s"),
    "tcgshop.binder.owner": ("Registrado a: %s", "Registered to: %s"),
    "tcgshop.binder.unbound": ("Sin registrar: se vinculará a quien lo abra", "Unregistered: it will bind to whoever opens it"),
    "tcgshop.binder.of": ("de %s", "of %s"),
    "tcgshop.binder.wishlist": ("♥ Deseadas (%s)", "♥ Wishlist (%s)"),
    "tcgshop.binder.new_cards": ("✦ Nuevas", "✦ New"),
    "tcgshop.binder.not_owned": ("Aún no la tienes", "Not owned yet"),
    "tcgshop.binder.wish": ("♡ Añadir a deseadas", "♡ Add to wishlist"),
    "tcgshop.binder.wished": ("♥ En deseadas", "♥ In wishlist"),
    "tcgshop.binder.empty_wish": ("Aún no tienes cartas deseadas. Selecciona una carta y pulsa ♡.", "No wishlist cards yet. Select a card and press ♡."),
    "tcgshop.binder.empty_new": ("No hay cartas nuevas. ¡Abre más sobres!", "No new cards. Open more packs!"),
    "tcgshop.binder.tip4": ("Usa ♡ en una carta para añadirla a tu lista de deseadas.", "Use ♡ on a card to add it to your wishlist."),
    "tcgshop.binder.title_button": ("★ Título", "★ Title"),
    "tcgshop.binder.titles": ("Elige tu título", "Choose your title"),
    "tcgshop.binder.no_title": ("Sin título", "No title"),
    "tcgshop.binder.no_titles": ("Saca 5 cartas del mismo Pokémon para conseguir tu primer título.", "Pull 5 cards of the same Pokémon to earn your first title."),

    # personal binder
    "tcgshop.personal.tab_mine": ("Mis cartas", "My cards"),
    "tcgshop.personal.tab_wish": ("♥ Deseadas", "♥ Wishlist"),
    "tcgshop.personal.help": ("Arrastra una carta a un bolsillo · Doble clic = primer hueco libre · Clic en un bolsillo = devolver",
                              "Drag a card into a pocket · Double-click = first free pocket · Click a pocket = return it"),
    "tcgshop.personal.help_drag": ("Suelta en un bolsillo (verde) para colocarla · Suelta en el cajón para devolverla",
                                   "Drop on a pocket (green) to place it · Drop on the drawer to return it"),
    "tcgshop.personal.drawer_tip": ("Arrastra a un bolsillo o haz doble clic", "Drag to a pocket or double-click"),
    "tcgshop.personal.click_return": ("Clic: devolver al Binder General", "Click: return to General Binder"),
    "tcgshop.personal.wish_owned": ("¡Ya la tienes! Haz clic para colocarla", "You own it! Click to place it"),
    "tcgshop.personal.wish_missing": ("Deseada: aún no la tienes (clic = quitar)", "Wishlist: not owned yet (click = remove)"),
    "tcgshop.personal.wish_empty": ("Añade cartas a deseadas desde el Binder General (botón ♡) para reservarles un hueco aquí.",
                                    "Add cards to your wishlist from the General Binder (♡ button) to reserve a pocket here."),
    "tcgshop.personal.full": ("No quedan bolsillos libres: añade páginas con +", "No free pockets left: add pages with +"),

    # opening / gloves
    "tcgshop.open.need_gloves": ("Necesitas los Guantes de friki para abrir con Espacio", "You need Nerd Gloves to open with Space"),

    # Scalper
    "tcgshop.scalper.fight_back": ("¿Quieres pelea? ¡Pues la tendrás!", "Want a fight? You got one!"),

    # shop
    "tcgshop.shop.created": ("¡Tienda de %s creada! Zona protegida de %s×%s bloques", "%s shop created! Protected area of %s×%s blocks"),
    "tcgshop.shop.overlap": ("Aquí no: se solaparía con otra tienda", "Not here: it would overlap another shop"),
    "tcgshop.shop.protected": ("Esta zona es la tienda de %s", "This area is %s's shop"),
    "tcgshop.shop.furniture_outside": ("Los muebles de tienda sólo se pueden colocar dentro de tu zona de tienda", "Shop furniture can only be placed inside your own shop area"),
    "tcgshop.shop.atm_disabled": ("El ATM está fuera de servicio: compra sobres al Mayorista o con el Teléfono TCG", "The ATM is out of order: buy packs from the Wholesaler or with the TCG Phone"),
    "tcgshop.shop.stocked": ("A la venta: %s por %s", "For sale: %s at %s"),
    "tcgshop.shop.full": ("Este mueble está lleno", "This display is full"),
    "tcgshop.shop.manage": ("Gestionar expositor (%s)", "Manage display (%s)"),
    "tcgshop.shop.buy_title": ("Tienda de %s", "%s's shop"),
    "tcgshop.shop.empty_slot": ("Vacío", "Empty"),
    "tcgshop.shop.stock": ("Quedan %s", "%s left"),
    "tcgshop.shop.market_ref": ("Mercado: %s", "Market: %s"),
    "tcgshop.shop.set_price": ("Precio", "Price"),
    "tcgshop.shop.market_btn": ("Mercado", "Market"),
    "tcgshop.shop.take": ("Retirar", "Take"),
    "tcgshop.shop.buy_btn": ("Comprar", "Buy"),
    "tcgshop.shop.manage_hint": ("Clic derecho con una carta, sobre o caja en la mano para ponerla a la venta", "Right-click with a card, pack or box in hand to put it on sale"),
    "tcgshop.shop.no_money": ("No tienes suficientes Pokédólares (%s)", "Not enough Pokédollars (%s)"),
    "tcgshop.shop.bought": ("Has comprado %s por %s", "You bought %s for %s"),
    "tcgshop.shop.sale": ("¡Venta! %s compró %s por %s", "Sale! %s bought %s for %s"),
    "tcgshop.shop.describe": ("Tienda de %s · %s · %s×%s", "%s's shop · %s · %s×%s"),
    "tcgshop.shop.stats": ("Ingresos %s · %s ventas · En caja %s · Beneficio total %s", "Revenue %s · %s sales · In register %s · Total profit %s"),
    "tcgshop.shop.register_of": ("Caja registradora de %s", "%s's cash register"),
    "tcgshop.shop.collected": ("Has cobrado %s de la caja", "You collected %s from the register"),
    "tcgshop.shop.no_cobbledollars": ("CobbleDollars no está disponible: no se puede cobrar", "CobbleDollars isn't available: can't collect"),
    "tcgshop.store.bought": ("Compraste %s× %s por %s", "You bought %s× %s for %s"),
    "tcgshop.store.buy": ("Comprar", "Buy"),
    "tcgshop.store.total": ("Total: %s", "Total: %s"),
    "tcgshop.store.empty": ("Nada que coincida", "Nothing matches"),
    "tcgshop.box.opened": ("¡Caja abierta! +%s %s", "Box opened! +%s %s"),
    "tcgshop.wholesaler.arrived": ("Un Mayorista misterioso ha llegado cerca... vende cajas de sobres al por mayor", "A Mysterious Wholesaler has arrived nearby... selling booster boxes wholesale"),
    "tcgshop.wholesaler.subtitle": ("Cajas al por mayor y todo para montar tu tienda", "Wholesale boxes and everything to start your shop"),

    # goggles
    "tcgshop.goggles.outside": ("Fuera de cualquier tienda", "Outside any shop"),
    "tcgshop.goggles.shop_of": ("Tienda de %s", "%s's shop"),
    "tcgshop.goggles.tier": ("%s · %s×%s", "%s · %s×%s"),
    "tcgshop.goggles.revenue": ("Ingresos: %s", "Revenue: %s"),
    "tcgshop.goggles.costs": ("Gastos: %s", "Costs: %s"),
    "tcgshop.goggles.profit": ("Beneficio: %s", "Profit: %s"),
    "tcgshop.goggles.loss": ("Pérdida: %s", "Loss: %s"),
    "tcgshop.goggles.balance": ("En caja: %s · %s ventas", "In register: %s · %s sales"),

    # phone
    "tcgshop.phone.welcome": ("¡Hola, coleccionista!", "Hi, collector!"),
    "tcgshop.phone.market": ("Mercado", "Market"),
    "tcgshop.phone.prices": ("Precios", "Prices"),
    "tcgshop.phone.collection": ("Colección", "Collection"),
    "tcgshop.phone.shop": ("Mi tienda", "My shop"),
    "tcgshop.phone.titles": ("Títulos", "Titles"),
    "tcgshop.phone.advancements": ("Logros", "Advancements"),
    "tcgshop.phone.cards": ("Cartas distintas: %s", "Unique cards: %s"),
    "tcgshop.phone.packs": ("Sobres abiertos: %s", "Packs opened: %s"),
    "tcgshop.phone.chart": ("Últimos 30 días", "Last 30 days"),
    "tcgshop.phone.search_hint": ("Escribe al menos 2 letras", "Type at least 2 letters"),
    "tcgshop.phone.no_shop": ("Aún no tienes tienda. Compra un bloque de tienda al Mayorista misterioso.", "You don't have a shop yet. Buy a shop block from the Mysterious Wholesaler."),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
