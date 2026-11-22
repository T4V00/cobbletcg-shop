# -*- coding: utf-8 -*-
"""Textos nuevos de la revisión de la tienda de muebles: motivos de rechazo del catálogo y regla de recompra
(sólo muebles comprados en la tienda y sin colocar).

Actualiza/inserta las claves en es_es.json y en_us.json (no borra nada).
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "tcgshop", "lang")

KEYS = {
    "tcgshop.furn.err_phone": ("Necesitas el Teléfono TCG en el inventario.", "You need the TCG Phone in your inventory."),
    "tcgshop.furn.err_far": ("Estás demasiado lejos del vendedor (o ya se ha ido).",
                             "You are too far from the seller (or they have left)."),
    "tcgshop.furn.not_from_shop": ("Sólo se recompran muebles comprados en la tienda y sin colocar.",
                                   "Only unused furniture bought from the shop can be sold back."),
    "tcgshop.furn.buyback_rule": ("Recompra: sólo muebles comprados aquí y sin colocar",
                                  "Buyback: only unused furniture bought here"),
}


def update(fname, idx):
    path = os.path.join(ROOT, fname)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for k, v in KEYS.items():
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    update("es_es.json", 0)
    update("en_us.json", 1)
    print(f"{len(KEYS)} claves actualizadas")
