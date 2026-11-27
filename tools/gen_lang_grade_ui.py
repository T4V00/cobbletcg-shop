#!/usr/bin/env python3
"""Texts for CobbleGrade's new «Submit cards» tab (filters, drag selection, services, expected
value). Merges the keys into es_es / en_us. Usage: python tools/gen_lang_grade_ui.py"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

# key -> (Spanish, English)
KEYS = {
    "tcgshop.grade.f.search": ("Buscar nombre, set o nº…", "Search name, set or no.…"),
    "tcgshop.grade.f.set_all": ("Todos los sets", "All sets"),
    "tcgshop.grade.f.set_tip": ("Filtrar por set (sólo los sets de las cartas que tienes)", "Filter by set (only sets of cards you own)"),
    "tcgshop.grade.f.sort.value": ("Orden: valor", "Sort: value"),
    "tcgshop.grade.f.sort.name": ("Orden: nombre", "Sort: name"),
    "tcgshop.grade.f.sort.set": ("Orden: set", "Sort: set"),
    "tcgshop.grade.f.sort.number": ("Orden: número", "Sort: number"),
    "tcgshop.grade.f.desc": ("Descendente (clic para invertir)", "Descending (click to flip)"),
    "tcgshop.grade.f.asc": ("Ascendente (clic para invertir)", "Ascending (click to flip)"),
    "tcgshop.grade.f.rarity_all": ("Todas", "All"),
    "tcgshop.grade.f.band.0": ("Cualquier valor", "Any value"),
    "tcgshop.grade.f.band.1": ("< 1k", "< 1k"),
    "tcgshop.grade.f.band.2": ("1k – 10k", "1k – 10k"),
    "tcgshop.grade.f.band.3": ("10k – 100k", "10k – 100k"),
    "tcgshop.grade.f.band.4": ("> 100k", "> 100k"),
    "tcgshop.grade.f.band_tip": ("Franja de valor de mercado (clic: siguiente · clic derecho: anterior)", "Market value band (click: next · right-click: previous)"),
    "tcgshop.grade.f.hide_graded": ("Ocultar ya gradeadas", "Hide already graded"),
    "tcgshop.grade.f.hide_graded_tip": ("Oculta las cartas de las que ya llevas una copia gradeada en el inventario", "Hides cards you already carry a graded copy of"),
    "tcgshop.grade.f.reset": ("Quitar filtros", "Clear filters"),
    "tcgshop.grade.f.no_cards": ("No tienes cartas sin gradear en el inventario ni en el Binder General.", "You have no ungraded cards in your inventory or General Binder."),
    "tcgshop.grade.f.none": ("Ninguna carta coincide con los filtros.", "No cards match the filters."),
    "tcgshop.grade.f.tip_value": ("Valor de mercado: %s", "Market value: %s"),
    "tcgshop.grade.f.tip_fee": ("Gradeo (%s): %s", "Grading (%s): %s"),
    "tcgshop.grade.f.tip_ev": ("Valor esperado gradeada: ~%s", "Expected graded value: ~%s"),
    "tcgshop.grade.f.tip_odds": ("Grado 10: %s · Grado 9+: %s", "Grade 10: %s · Grade 9+: %s"),
    "tcgshop.grade.f.tip_gain": ("Resultado esperado: %s", "Expected result: %s"),
    "tcgshop.grade.f.tip_binder": ("Está en tu Binder General", "It's in your General Binder"),
    "tcgshop.grade.f.tip_owned": ("Ya tienes una copia gradeada", "You already own a graded copy"),
    "tcgshop.grade.f.tip_help": ("Clic: elegir · Arrastra: elegir varias · Mayús+clic: rango · Clic dcho: quitar",
                                 "Click: pick · Drag: pick many · Shift+click: range · Right-click: remove"),
    "tcgshop.grade.f.cards": ("cartas", "cards"),
    "tcgshop.grade.f.select_shown": ("Elegir visibles (%s)", "Pick shown (%s)"),
    "tcgshop.grade.f.select_shown_tip": ("Elige las cartas que se ven con los filtros actuales, en orden, hasta el límite del pedido (Ctrl+A)",
                                         "Picks the cards shown by the current filters, in order, up to the order limit (Ctrl+A)"),
    "tcgshop.grade.f.clear": ("Limpiar", "Clear"),
    "tcgshop.grade.f.strip_empty": ("Pulsa y arrastra sobre las cartas para elegir varias", "Press and drag over cards to pick several"),
    "tcgshop.grade.f.strip_remove": ("Clic para quitarla del pedido", "Click to remove it from the order"),
    "tcgshop.grade.f.svc_time": ("Tarda: %s", "Takes: %s"),
    "tcgshop.grade.f.svc_fee": ("Tarifa: %s + %s%% del valor por carta", "Fee: %s + %s%% of value per card"),
    "tcgshop.grade.f.svc_total": ("Total de tu selección: %s", "Your selection total: %s"),
    "tcgshop.grade.f.stat_cards": ("Cartas", "Cards"),
    "tcgshop.grade.f.stat_value": ("Valor actual", "Current value"),
    "tcgshop.grade.f.stat_fee": ("Tarifa", "Fee"),
    "tcgshop.grade.f.stat_ev": ("Valor esperado", "Expected value"),
    "tcgshop.grade.f.stat_ev_tip": ("Media de 400 gradeos simulados por carta con las probabilidades reales (antigüedad incluida). Es una estimación: cada gradeo es al azar.",
                                    "Average of 400 simulated gradings per card with the real odds (age included). It's an estimate: every grading is random."),
    "tcgshop.grade.f.stat_gain": ("Resultado", "Result"),
    "tcgshop.grade.f.stat_gain_tip": ("Valor esperado − valor actual − tarifa. Punto verde en una carta: suele salir a cuenta; rojo: suele perder.",
                                      "Expected value − current value − fee. Green dot on a card: usually worth it; red: usually loses."),
    "tcgshop.grade.f.send_note": ("Se envía una copia de cada carta elegida. El Grado se revela al recoger.", "One copy of each picked card is sent. The grade is revealed on pickup."),
    "tcgshop.grade.f.confirm": ("¿Confirmar? Pagar %s", "Confirm? Pay %s"),
}


def main():
    for name, idx in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / name
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in KEYS.items():
            d[k] = v[idx]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("CobbleGrade UI texts:", len(KEYS))


if __name__ == "__main__":
    main()
