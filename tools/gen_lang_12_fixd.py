#!/usr/bin/env python3
"""1.1 texts (Group D review fixes): the Mayor taking a card from the inventory (binders empty), refusing when no
battle can be set up at all, and the CREATive Engineer refusing a buy after his catalog changed (/tcgshop reload).
Code: npc/TaxOffice.java, entity/EngineerEntity.java.

Usage: python tools/gen_lang_12_fixd.py  (idempotent: sets these keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

# key: (Spanish, English)
T = {
    "tcgshop.mayor.took_inv": (
        "%s Tus binders estaban vacíos, así que el Alcalde se llevó %s (%s) de tu inventario.",
        "%s Your binders were empty, so the Mayor took %s (%s) from your inventory."),
    "tcgshop.mayor.msg.took_inv": (
        "Embargo realizado: %s (%s), tomada de tu inventario, pasa a la colección de la alcaldía.",
        "Seizure done: %s (%s), taken from your inventory, now belongs to the town hall's collection."),
    "tcgshop.mayor.cant_battle": (
        "No se pudo organizar ninguna batalla, así que negarte te cuesta una carta.",
        "No battle could be set up, so refusing costs you a card."),
    "tcgshop.engineer.catalog_changed": (
        "El catálogo del Ingeniero cambió. Revisa los precios actualizados antes de comprar.",
        "The Engineer's catalog changed. Check the updated prices before buying."),
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
