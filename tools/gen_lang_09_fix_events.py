# -*- coding: utf-8 -*-
"""0.9 fix - eventos (núcleo): claves que faltaban.

Sólo fija SUS claves en es_es.json y en_us.json (idempotente). Recarga el JSON justo antes de escribir.
Uso: python -I -X utf8 tools/gen_lang_09_fix_events.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
LANG = os.path.join(HERE, "..", "src", "main", "resources", "assets", "tcgshop", "lang")

ES = {
    # historial de la pestaña Eventos del teléfono: evento cancelado (p. ej. un incidente cuyo arreglo desapareció)
    "tcgshop.phone.ev.out.cancelled": "Cancelado",
}
EN = {
    "tcgshop.phone.ev.out.cancelled": "Cancelled",
}


def merge(fname, add):
    p = os.path.join(LANG, fname)
    with open(p, encoding="utf-8") as f:
        data = json.load(f)
    data.update(add)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return len(add)


if __name__ == "__main__":
    assert set(ES) == set(EN), set(ES) ^ set(EN)
    print("es_es.json:", merge("es_es.json", ES), "claves")
    print("en_us.json:", merge("en_us.json", EN), "claves")
