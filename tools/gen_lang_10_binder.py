"""1.0 Beta 1 texts for the binders: every card can be filed (variants and graded cards), multi-select,
price filter per pack and the 25-card starter Personal Binder (binder/*, collection/*, client/binder/*).

Usage: python tools/gen_lang_10_binder.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ---------------------------------------------------------------- General Binder: filing / withdrawing
    "tcgshop.binder.took_many": ("Has sacado %s cartas del binder", "Took %s cards out of the binder"),
    "tcgshop.binder.inv_full": ("Inventario lleno: %s cartas se han quedado en el binder.",
                                "Inventory full: %s cards stayed in the binder."),
    "tcgshop.binder.deposited": ("%s cartas guardadas en el binder", "%s cards filed into the binder"),
    "tcgshop.binder.nothing_to_deposit": ("No llevas cartas en el inventario", "You aren't carrying any cards"),
    "tcgshop.binder.tip3": ("Con este binder encima, las cartas de los sobres se guardan aquí directamente (también variantes y gradeadas, en «◆ Variantes»).",
                            "While you carry this binder, cards from packs are stored here directly (variants and graded cards too, under \"◆ Variants\")."),
    "tcgshop.binder.tip5": ("Ctrl+clic (o Ctrl+arrastrar) para seleccionar varias cartas, Mayús+clic para un rango y Ctrl+A para todas las que ves. Luego, «Sacar 1 c/u» o «Sacar todas».",
                            "Ctrl+click (or Ctrl+drag) to select several cards, Shift+click for a range and Ctrl+A for everything shown. Then \"Take 1 each\" or \"Take all\"."),

    # special cards (print variants and graded slabs)
    "tcgshop.binder.specials": ("◆ Variantes (%s)", "◆ Variants (%s)"),
    "tcgshop.binder.specials_tip": ("Variantes de impresión (reverse holo, 1ª edición, Shadowless...) y cartas gradeadas que has guardado.",
                                    "Print variants (reverse holo, 1st edition, Shadowless...) and graded cards you have filed."),
    "tcgshop.binder.empty_special": ("Aún no tienes variantes ni cartas gradeadas en este binder.",
                                     "You don't have any variants or graded cards in this binder yet."),
    "tcgshop.binder.special.grade": ("Grado %s", "Grade %s"),

    # price filter / sort
    "tcgshop.binder.price": ("Precio: %s", "Price: %s"),
    "tcgshop.binder.price_any": ("todos", "any"),
    "tcgshop.binder.price_tip": ("Clic: siguiente rango · Mayús+clic: anterior. Se combina con el pack, la rareza, la búsqueda y el mínimo/máximo.",
                                 "Click: next range · Shift+click: previous. Combines with the pack, rarity, search and min/max."),
    "tcgshop.binder.price_min": ("mín.", "min"),
    "tcgshop.binder.price_max": ("máx.", "max"),
    "tcgshop.binder.price_minmax_tip": ("Precio mínimo y máximo (déjalo vacío para no limitar)",
                                        "Minimum and maximum price (leave empty for no limit)"),
    "tcgshop.binder.sort": ("Orden: %s", "Sort: %s"),
    "tcgshop.binder.sort.0": ("número", "number"),
    "tcgshop.binder.sort.1": ("precio ↓", "price ↓"),
    "tcgshop.binder.sort.2": ("precio ↑", "price ↑"),
    "tcgshop.binder.price_summary": ("%s cartas · %s", "%s cards · %s"),

    # multi-select
    "tcgshop.binder.sel_all": ("Seleccionar todo", "Select all"),
    "tcgshop.binder.sel_all_tip": ("Selecciona todas las cartas que tienes de las que se ven ahora (con los filtros actuales). Atajo: Ctrl+A",
                                   "Selects every card you own among the ones shown (with the current filters). Shortcut: Ctrl+A"),
    "tcgshop.binder.sel_clear": ("Quitar selección", "Clear selection"),
    "tcgshop.binder.sel_take1": ("Sacar 1 c/u", "Take 1 each"),
    "tcgshop.binder.sel_take1_tip": ("Saca una copia de cada carta seleccionada", "Takes out one copy of each selected card"),
    "tcgshop.binder.sel_take_all": ("Sacar todas", "Take all"),
    "tcgshop.binder.sel_take_all_tip": ("Saca todas las copias de las cartas seleccionadas (solo lo que quepa en el inventario)",
                                        "Takes out every copy of the selected cards (only what fits in your inventory)"),
    "tcgshop.binder.sel_info": ("%s seleccionadas · %s copias", "%s selected · %s copies"),

    # ---------------------------------------------------------------- Personal Binder
    "tcgshop.personal.help": ("Arrastra una carta a un bolsillo · Doble clic = primer hueco libre · Clic en un bolsillo = sacarla · Ctrl+clic = seleccionar",
                              "Drag a card into a pocket · Double-click = first free pocket · Click a pocket = take it out · Ctrl+click = select"),
    "tcgshop.personal.help_select": ("Ctrl+clic o Ctrl+arrastrar para añadir o quitar · «Sacar seleccionadas» abajo a la izquierda · ESC = quitar selección",
                                     "Ctrl+click or Ctrl+drag to add or remove · \"Take out selected\" at the bottom left · ESC = clear selection"),
    "tcgshop.personal.click_return_inv": ("Clic: sacarla al inventario", "Click: take it out to your inventory"),
    "tcgshop.personal.ctrl_select": ("Ctrl+clic: seleccionar", "Ctrl+click: select"),
    "tcgshop.personal.deposit": ("Guardar cartas del inventario", "File inventory cards"),
    "tcgshop.personal.deposit_tip": ("Coloca las cartas que llevas en el inventario en los bolsillos libres de este binder",
                                     "Puts the cards in your inventory into the free pockets of this binder"),
    "tcgshop.personal.select_pages": ("Seleccionar páginas", "Select pages"),
    "tcgshop.personal.take_selected": ("Sacar seleccionadas (%s)", "Take out selected (%s)"),
    "tcgshop.personal.take_selected_tip": ("Devuelve las cartas seleccionadas a tu Binder General",
                                           "Returns the selected cards to your General Binder"),
    "tcgshop.personal.take_selected_tip_inv": ("Saca las cartas seleccionadas a tu inventario",
                                               "Takes the selected cards out to your inventory"),
    "tcgshop.personal.took_many_general": ("%s cartas devueltas al Binder General", "%s cards returned to your General Binder"),
    "tcgshop.personal.took_many_inv": ("%s cartas sacadas al inventario", "%s cards taken out to your inventory"),
    "tcgshop.personal.inv_full": ("No te cabe en el inventario", "There's no room in your inventory"),
    "tcgshop.personal.inv_full_many": ("Inventario lleno: %s cartas se han quedado en el binder.",
                                       "Inventory full: %s cards stayed in the binder."),
    "tcgshop.personal.drawer_no_general": ("Sin Binder General, las cartas de tu inventario se guardan aquí con «Guardar cartas del inventario» (y las de los sobres, solas).",
                                           "Without a General Binder, file the cards in your inventory here with \"File inventory cards\" (pack cards go in on their own)."),

    # capacity (25-card starter binder)
    "tcgshop.personal.pages_capacity": ("Páginas %s-%s de %s · %s/%s cartas", "Pages %s-%s of %s · %s/%s cards"),
    "tcgshop.personal.locked_pocket": ("Este binder solo tiene %s bolsillos", "This binder only has %s pockets"),
    "tcgshop.personal.fixed_size": ("Este binder tiene un tamaño fijo de %s cartas: no se le pueden añadir páginas.",
                                    "This binder has a fixed size of %s cards: pages can't be added."),
    "tcgshop.personal.full_fixed": ("El binder está lleno (%s cartas)", "The binder is full (%s cards)"),
    "tcgshop.personal.stored": ("%s cartas guardadas en tu binder personal (%s/%s)", "%s cards filed into your Personal Binder (%s/%s)"),
    "tcgshop.personal.full_store": ("Tu binder personal está lleno (%2$s/%3$s): %1$s cartas han ido a tu inventario. Añade páginas con + o consigue un Binder General.",
                                    "Your Personal Binder is full (%2$s/%3$s): %1$s cards went to your inventory. Add pages with + or get a General Binder."),
    "tcgshop.personal.full_store_fixed": ("Tu binder personal está lleno (%2$s/%3$s): %1$s cartas han ido a tu inventario. Consigue un Binder General para guardar todas tus cartas.",
                                          "Your Personal Binder is full (%2$s/%3$s): %1$s cards went to your inventory. Get a General Binder to store all your cards."),
    "item.tcgshop.personal_binder.tip_capacity": ("%s/%s cartas", "%s/%s cards"),
    "item.tcgshop.personal_binder.starter_name": ("Binder personal de %s", "%s's Personal Binder"),

    # pack opening screen: cards may now go to the Personal (starter) Binder, and variants are filed too
    "tcgshop.open.to_binder": ("Guardadas en tu binder", "Stored in your binder"),
    "tcgshop.open.to_binder_variants": ("Guardadas en tu binder (las variantes especiales también)",
                                        "Stored in your binder (special print variants too)"),
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
