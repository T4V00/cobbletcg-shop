"""Phase B texts (part 2): Binder Market, friends on the phone and display manager."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    "tcgshop.phone.binder_market": ("Vender", "Sell cards"),
    "tcgshop.phone.displays": ("Expositores", "Displays"),
    "tcgshop.phone.friends": ("Amigos", "Friends"),
    # binder market
    "tcgshop.bm2.title": ("Mercado del Binder", "Binder Market"),
    "tcgshop.bm2.value": ("Valor de tu colección: %s (%s cartas)", "Collection value: %s (%s cards)"),
    "tcgshop.bm2.tab_cards": ("Cartas", "Cards"),
    "tcgshop.bm2.tab_history": ("Historial", "History"),
    "tcgshop.bm2.f_all_sets": ("Todas", "All"),
    "tcgshop.bm2.f_set": ("Expansión: %s", "Set: %s"),
    "tcgshop.bm2.f_all": ("Todas", "All"),
    "tcgshop.bm2.f_rarity": ("Rareza: %s", "Rarity: %s"),
    "tcgshop.bm2.f_value": ("Valor: %s", "Value: %s"),
    "tcgshop.bm2.band.all": ("todos", "any"),
    "tcgshop.bm2.band.lt1k": ("< 1.000", "< 1,000"),
    "tcgshop.bm2.band.1k10k": ("1.000-10.000", "1,000-10,000"),
    "tcgshop.bm2.band.10k100k": ("10.000-100.000", "10,000-100,000"),
    "tcgshop.bm2.band.gt100k": ("> 100.000", "> 100,000"),
    "tcgshop.bm2.f_qty": ("Cantidad: %s", "Quantity: %s"),
    "tcgshop.bm2.qty.0": ("todas", "all"),
    "tcgshop.bm2.qty.1": ("repetidas (2+)", "duplicates (2+)"),
    "tcgshop.bm2.qty.2": ("5 o más", "5 or more"),
    "tcgshop.bm2.sort.0": ("Orden: valor ↓", "Sort: value ↓"),
    "tcgshop.bm2.sort.1": ("Orden: cantidad ↓", "Sort: quantity ↓"),
    "tcgshop.bm2.sort.2": ("Orden: nombre", "Sort: name"),
    "tcgshop.bm2.sort.3": ("Orden: valor total ↓", "Sort: total value ↓"),
    "tcgshop.bm2.f_fav_on": ("★ Sólo favoritas", "★ Favourites only"),
    "tcgshop.bm2.f_fav_off": ("☆ Favoritas", "☆ Favourites"),
    "tcgshop.bm2.col_qty": ("Cant.", "Qty"),
    "tcgshop.bm2.col_market": ("Mercado", "Market"),
    "tcgshop.bm2.col_quick": ("Venta (%s%%)", "Sell (%s%%)"),
    "tcgshop.bm2.col_total": ("Total", "Total"),
    "tcgshop.bm2.empty": ("No hay cartas con estos filtros.", "No cards match these filters."),
    "tcgshop.bm2.selected": ("%s elegidas · vendes %s cartas por %s", "%s selected · selling %s cards for %s"),
    "tcgshop.bm2.fav_protected": ("%s favoritas protegidas (no se venden)", "%s favourites protected (not sold)"),
    "tcgshop.bm2.keep_one_on": ("✔ Conservar 1 de cada", "✔ Keep 1 of each"),
    "tcgshop.bm2.keep_one_off": ("Vender todas", "Sell all copies"),
    "tcgshop.bm2.select_all": ("Elegir / quitar todo", "Select / clear all"),
    "tcgshop.bm2.sell": ("Venta rápida", "Quick sell"),
    "tcgshop.bm2.confirm_sell": ("¿Confirmar? +%s", "Confirm? +%s"),
    "tcgshop.bm2.offer": ("Ofrecer a un jugador", "Offer to a player"),
    "tcgshop.bm2.offer_to": ("Para:", "To:"),
    "tcgshop.bm2.offer_qty": ("Cant.:", "Qty:"),
    "tcgshop.bm2.offer_price": ("Precio total:", "Total price:"),
    "tcgshop.bm2.offer_market": ("Mercado", "Market"),
    "tcgshop.bm2.offer_send": ("Enviar oferta", "Send offer"),
    "tcgshop.bm2.cancel": ("Cancelar", "Cancel"),
    "tcgshop.bm2.offer_note": ("Caduca en 2 min", "Expires in 2 min"),
    "tcgshop.bm2.no_history": ("Aún no has hecho ninguna operación.", "No operations yet."),
    "tcgshop.bm2.h.sell": ("Venta rápida: %s × %s por %s", "Quick sale: %s × %s for %s"),
    "tcgshop.bm2.h.offer_sold": ("Vendiste %s × %s por %s a %s", "Sold %s × %s for %s to %s"),
    "tcgshop.bm2.h.offer_bought": ("Compraste %s × %s por %s a %s", "Bought %s × %s for %s from %s"),
    "tcgshop.bm2.sold": ("Vendes %s cartas de tu Binder por %s.", "You sell %s cards from your Binder for %s."),
    "tcgshop.bm2.fav_skipped": ("%s cartas favoritas no se han vendido (están protegidas).", "%s favourite cards were not sold (they're protected)."),
    "tcgshop.bm2.not_enough": ("No tienes tantas copias de esa carta en tu Binder.", "You don't have that many copies in your Binder."),
    "tcgshop.bm2.offline": ("Ese jugador no está conectado.", "That player isn't online."),
    "tcgshop.bm2.offer_sent": ("Oferta enviada a %s: %s × %s por %s.", "Offer sent to %s: %s × %s for %s."),
    "tcgshop.bm2.offer_got": ("%s te ofrece %s × %s por %s.", "%s offers you %s × %s for %s."),
    "tcgshop.bm2.accept": ("[Aceptar]", "[Accept]"),
    "tcgshop.bm2.accept_tip": ("Pagas %s y la carta va a tu Binder", "You pay %s and the card goes to your Binder"),
    "tcgshop.bm2.deny": ("[Rechazar]", "[Decline]"),
    "tcgshop.bm2.expired": ("Esa oferta ya no existe.", "That offer no longer exists."),
    "tcgshop.bm2.denied_you": ("Has rechazado la oferta.", "You declined the offer."),
    "tcgshop.bm2.denied": ("%s ha rechazado tu oferta.", "%s declined your offer."),
    "tcgshop.bm2.gone": ("El vendedor ya no tiene esa carta. No se ha cobrado nada.", "The seller no longer has that card. Nothing was charged."),
    "tcgshop.bm2.bought": ("Compras %s × %s por %s. Está en tu Binder.", "You buy %s × %s for %s. It's in your Binder."),
    "tcgshop.bm2.sold_to": ("%s te ha comprado %s × %s por %s.", "%s bought %s × %s from you for %s."),
    # friends
    "tcgshop.friends.list": ("Amigos (%s)", "Friends (%s)"),
    "tcgshop.friends.none": ("Aún no tienes amigos.", "No friends yet."),
    "tcgshop.friends.requests": ("Solicitudes", "Requests"),
    "tcgshop.friends.add": ("Añadir (conectados)", "Add (online)"),
    "tcgshop.friends.tip": ("Elige un amigo para ver sus tiendas y estadísticas. Puedes añadir a cualquier jugador conectado; él tiene que aceptar. Sólo tus amigos ven dónde están tus tiendas.",
                            "Pick a friend to see their shops and stats. You can add any online player; they must accept. Only friends can see where your shops are."),
    "tcgshop.friends.remove": ("Eliminar", "Remove"),
    "tcgshop.friends.s_rating": ("Liga", "League"),
    "tcgshop.friends.s_wins": ("Vict./derr.", "Wins/losses"),
    "tcgshop.friends.s_unique": ("Cartas únicas", "Unique cards"),
    "tcgshop.friends.s_packs": ("Sobres abiertos", "Packs opened"),
    "tcgshop.friends.s_sales": ("Ventas", "Sales"),
    "tcgshop.friends.s_revenue": ("Facturado", "Revenue"),
    "tcgshop.friends.shops": ("Tiendas (%s)", "Shops (%s)"),
    "tcgshop.friends.shop_line": ("%s · reputación %s · %s★ · %s ventas", "%s · reputation %s · %s★ · %s sales"),
    "tcgshop.friends.shop_pos": ("En %s %s %s (%s)", "At %s %s %s (%s)"),
    "tcgshop.friends.request_got": ("%s quiere ser tu amigo. Acéptalo en el teléfono → Amigos.", "%s wants to be your friend. Accept in Phone → Friends."),
    "tcgshop.friends.request_sent": ("Solicitud de amistad enviada.", "Friend request sent."),
    "tcgshop.friends.accepted_you": ("%s y tú ya sois amigos.", "You and %s are now friends."),
    # displays
    "tcgshop.displays.all_market": ("Todo a mercado", "All at market"),
    "tcgshop.displays.all_90": ("Todo 90%", "All 90%"),
    "tcgshop.displays.all_110": ("Todo 110%", "All 110%"),
    "tcgshop.displays.all_125": ("Todo 125%", "All 125%"),
    "tcgshop.displays.summary": ("%s productos expuestos · valor a tu precio: %s", "%s products on display · value at your prices: %s"),
    "tcgshop.displays.no_shop": ("No tienes ninguna tienda en esta dimensión.", "You have no shop in this dimension."),
    "tcgshop.displays.empty": ("Tus expositores están vacíos (o fuera de los chunks cargados).", "Your displays are empty (or outside loaded chunks)."),
    "tcgshop.displays.all_set": ("Precios de toda la tienda al %s%% del mercado.", "All shop prices set to %s%% of market."),
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
