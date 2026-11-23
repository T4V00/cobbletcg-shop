#!/usr/bin/env python3
"""v0.9 fix - statues / furniture shop: inventory full and price changed. Idempotent; only touches its own keys."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "tcgshop" / "lang"

ES = {
    "tcgshop.lux.inv_full": "Inventario lleno: deja un hueco libre para recibir la estatua (no se ha cobrado nada)",
    "tcgshop.furn.price_changed": "El precio ha cambiado: revisa el catálogo actualizado (no se ha cobrado nada)",
}

EN = {
    "tcgshop.lux.inv_full": "Inventory full: free up a slot to receive the statue (you were not charged)",
    "tcgshop.furn.price_changed": "The price has changed: check the updated catalog (you were not charged)",
}


def apply(name, values):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, v in values.items():
        data[k] = v
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    assert set(ES) == set(EN)
    apply("es_es.json", ES)
    apply("en_us.json", EN)
    print("gen_lang_09_fix_statues:", len(ES), "keys")
