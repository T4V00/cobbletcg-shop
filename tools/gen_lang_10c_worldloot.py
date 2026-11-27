"""1.0 beta 3 texts: searchable trash cans and the Bankrupt Card Shop (dev.tcgshop.world.*).

Usage: python tools/gen_lang_10c_worldloot.py  (updates/inserts the keys in es_es.json and en_us.json)
Messages with numbered variants (.0, .1, ...) are picked at random by TrashCanBlock.variant.
pack/card messages: %1$s = the item (hoverable), %2$s = its market price. junk: %1$s = the item.
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ block
    "block.tcgshop.trash_can": ("Cubo de basura", "Trash Can"),
    "block.tcgshop.trash_can.tip1": ("Decorativo: el que pones tú nunca tiene nada dentro.",
                                     "Decorative: the ones you place never have anything inside."),
    "block.tcgshop.trash_can.tip2": ("Los cubos de las calles de las aldeas sí se pueden rebuscar (clic derecho)…",
                                     "The ones on village streets can be searched though (right click)…"),

    # ------------------------------------------------------------------ search flow
    "tcgshop.trash.searching.0": ("Rebuscando en la basura…", "Rummaging through the trash…"),
    "tcgshop.trash.searching.1": ("Apartando cáscaras y papeles…", "Pushing aside peels and papers…"),
    "tcgshop.trash.searching.2": ("Con la nariz tapada, buscas algo de valor…", "Holding your nose, you dig for treasure…"),
    "tcgshop.trash.busy": ("Alguien ya está rebuscando en este cubo.", "Someone is already digging through this can."),
    "tcgshop.trash.cancel": ("Te has alejado: búsqueda cancelada.", "You walked away: search cancelled."),
    "tcgshop.trash.decor.0": ("Es tu cubo decorativo. Solo hay aire y dignidad.", "It's your decorative can. Only air and dignity inside."),
    "tcgshop.trash.decor.1": ("Impecable. Demasiado limpio para tener tesoros.", "Spotless. Way too clean to hide any treasure."),
    "tcgshop.trash.decor.2": ("Nada. Los cubos buenos están en las calles de las aldeas.", "Nothing. The good cans are out on village streets."),
    "tcgshop.trash.decor.3": ("Levantas la tapa… y la vuelves a bajar. Vacío.", "You lift the lid… and put it back. Empty."),
    "tcgshop.trash.empty.0": ("Ya está vacío. Vuelve dentro de unos días.", "Already emptied. Come back in a few days."),
    "tcgshop.trash.empty.1": ("Alguien ha llegado antes que tú. Vuelve dentro de unos días.","Someone beat you to it. Try again in a few days."),
    "tcgshop.trash.empty.2": ("Solo queda el olor. Y el olor no se vende.", "Only the smell is left. And the smell doesn't sell."),
    # wild cans (loot=wild) are searched once and never refill
    "tcgshop.trash.empty_wild.0": ("Ya lo rebuscaste. Por aquí no pasa ningún camión: no volverá a llenarse.",
                                   "Already searched. No garbage truck comes out here: it won't fill up again."),
    "tcgshop.trash.empty_wild.1": ("Vacío. En medio de la nada, nadie tira nada nuevo.",
                                   "Empty. Out in the middle of nowhere, nobody throws anything new away."),
    "tcgshop.trash.empty_wild.2": ("Solo quedan hojas secas. Este cubo ya dio todo lo que tenía.",
                                   "Just dry leaves left. This can has given all it had."),

    # ------------------------------------------------------------------ street can results
    "tcgshop.trash.pack.0": ("¡¿Un sobre sin abrir en la basura?! Te llevas %1$s (vale %2$s).",
                             "An unopened pack in the trash?! You grab %1$s (worth %2$s)."),
    "tcgshop.trash.pack.1": ("Alguien tiró %1$s sin abrir. Su pérdida, tu ganancia (%2$s).",
                             "Somebody threw away %1$s, still sealed. Their loss, your gain (%2$s)."),
    "tcgshop.trash.pack.2": ("Entre servilletas aparece %1$s. ¡Precintado! (%2$s)", "Between napkins: %1$s. Still sealed! (%2$s)"),
    "tcgshop.trash.pack.3": ("La suerte huele raro hoy: %1$s, valorado en %2$s.", "Luck smells funny today: %1$s, worth %2$s."),
    "tcgshop.trash.card.0": ("Rescatas %1$s de entre los restos. Vale %2$s.", "You rescue %1$s from the scraps. Worth %2$s."),
    "tcgshop.trash.card.1": ("¡Una carta! %1$s, un poco pegajosa, pero vale %2$s.", "A card! %1$s, a bit sticky, but worth %2$s."),
    "tcgshop.trash.card.2": ("Alguien no sabía lo que tiraba: %1$s (%2$s).", "Someone didn't know what they were throwing away: %1$s (%2$s)."),
    "tcgshop.trash.card.3": ("Debajo de una piel de plátano: %1$s. Precio de mercado: %2$s.", "Under a banana peel: %1$s. Market price: %2$s."),
    "tcgshop.trash.junk.0": ("Solo encuentras %1$s. Algo es algo.", "All you find is %1$s. Better than nothing."),
    "tcgshop.trash.junk.1": ("Premio… más o menos: %1$s.", "A prize… sort of: %1$s."),
    "tcgshop.trash.junk.2": ("Te llevas %1$s. Nadie lo echará de menos.", "You take %1$s. Nobody will miss it."),
    "tcgshop.trash.junk.3": ("Basura de verdad: %1$s.", "Actual garbage: %1$s."),
    "tcgshop.trash.nothing.0": ("Nada de valor. Solo basura de la basura.", "Nothing of value. Just trash-flavoured trash."),
    "tcgshop.trash.nothing.1": ("Ni una carta. Ni un sobre. Ni esperanza.", "Not a card. Not a pack. Not even hope."),
    "tcgshop.trash.nothing.2": ("Vacío… salvo por un olor que no olvidarás.", "Empty… except for a smell you won't forget."),
    "tcgshop.trash.nothing.3": ("Nada. Quizá el próximo cubo.", "Nothing. Maybe the next can."),

    # ------------------------------------------------------------------ Bankrupt Card Shop can
    "tcgshop.bankrupt.card.0": ("Entre facturas sin pagar, el dueño olvidó %1$s. ¡Vale %2$s!",
                                "Among unpaid bills, the owner forgot %1$s. Worth %2$s!"),
    "tcgshop.bankrupt.card.1": ("La última carta de una tienda en quiebra: %1$s (%2$s). Que descanse en paz.",
                                "The last card of a bankrupt shop: %1$s (%2$s). Rest in peace."),
    "tcgshop.bankrupt.card.2": ("Al fondo del cubo brilla %1$s. Valor: %2$s. Tú no acabarás así.",
                                "Something shines at the bottom: %1$s, worth %2$s. You won't end up like this place."),
    "tcgshop.bankrupt.pack.0": ("Un sobre que nunca llegó a venderse: %1$s (%2$s).", "A pack that never got sold: %1$s (%2$s)."),
    "tcgshop.bankrupt.pack.1": ("Stock olvidado: %1$s, valorado en %2$s.", "Forgotten stock: %1$s, worth %2$s."),
    "tcgshop.bankrupt.pack.2": ("Lo último del inventario: %1$s (%2$s).", "The last of the inventory: %1$s (%2$s)."),
    "tcgshop.bankrupt.junk.0": ("Ni una carta. Solo %1$s. Qué triste.", "Not a single card. Just %1$s. How sad."),
    "tcgshop.bankrupt.junk.1": ("Encuentras %1$s. Explica muchas cosas.", "You find %1$s. That explains a lot."),
    "tcgshop.bankrupt.junk.2": ("Aquí no queda nada… salvo %1$s. Toma nota.", "Nothing left here… except %1$s. Take notes."),
    "tcgshop.bankrupt.nothing.0": ("Nada. Hasta la basura se fue de esta tienda.", "Nothing. Even the trash left this shop."),
    "tcgshop.bankrupt.nothing.1": ("Vacío, como la caja registradora.", "Empty, like the cash register."),
    "tcgshop.bankrupt.nothing.2": ("Nada de nada. Ni para pagar el alquiler.", "Nothing at all. Not even rent money."),
    "tcgshop.bankrupt.nothing.3": ("El cubo está tan vacío como la tienda.", "The can is as empty as the shop."),
    "tcgshop.bankrupt.empty.0": ("Ya lo vaciaste. Esta tienda no tiene nada más que dar.", "You already emptied it. This shop has nothing left to give."),
    "tcgshop.bankrupt.empty.1": ("Vacío para siempre. Como las estanterías.", "Empty for good. Like the shelves."),
    "tcgshop.bankrupt.empty.2": ("Aquí ya no hay nada. Ni clientes, ni cartas, ni basura.", "Nothing here anymore. No customers, no cards, no trash."),

    # ------------------------------------------------------------------ junk items
    "tcgshop.trash.item.receipt": ("Ticket arrugado", "Crumpled Receipt"),
    "tcgshop.trash.item.receipt.lore": ("«3 sobres, 1 caja… y cero cartas buenas»", "\"3 packs, 1 box… and zero good pulls\""),
    "tcgshop.trash.item.notice": ("Aviso final", "Final Notice"),
    "tcgshop.trash.item.notice.lore": ("«Cerrado por quiebra. Gracias por nada.»", "\"Closed due to bankruptcy. Thanks for nothing.\""),
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
