"""Texts for the 1.0 Mystery Box rework (shop/MysteryBoxItem.java, client/screen/BoxOpenScreen.java).

Usage: python tools/gen_lang_10_mysterybox.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ tooltip (tip1 changed: the box is now for the player)
    "item.tcgshop.mystery_box.tip1": ("Clic derecho para abrirla: una carta al azar, la mitad de las veces graduada.",
                                      "Right-click to open it: one random card, graded half of the time."),
    "item.tcgshop.mystery_box.tip_odds": ("Casi siempre vale menos que la caja... pero 1 de cada 10.000 esconde un Grado 10 de los sets más nuevos.",
                                          "Almost always worth less than the box... but 1 in 10,000 hides a Grade 10 from the newest sets."),
    "item.tcgshop.mystery_box.tip2": ("En un expositor, la tienda gana entre ×0,3 y ×1,9 de su precio según lo que le toque al cliente.",
                                      "On a display, the shop earns ×0.3 – ×1.9 of its price depending on what the customer gets."),

    # ------------------------------------------------------------------ opening
    "tcgshop.mystery.opening": ("Abriendo caja misteriosa", "Opening mystery box"),
    "tcgshop.mystery.value": ("Valor: %s · Caja: %s", "Value: %s · Box: %s"),
    "tcgshop.mystery.jackpot": ("¡PREMIO GORDO!", "JACKPOT!"),
    "tcgshop.mystery.jackpot_sub": ("Grado 10 de los sets más nuevos · 1 entre 10.000", "Grade 10 from the newest sets · 1 in 10,000"),
    "tcgshop.mystery.jackpot_broadcast": ("✦ ¡%s ha sacado el premio gordo de una caja misteriosa: %s! ✦",
                                          "✦ %s hit the Mystery Box jackpot: %s! ✦"),
    "tcgshop.mystery.empty": ("Esta caja misteriosa está vacía: no hay precios de cartas cargados.",
                              "This mystery box is empty: no card prices are loaded."),
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
