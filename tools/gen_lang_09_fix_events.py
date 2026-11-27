# -*- coding: utf-8 -*-
"""0.9 fix - events (core): missing keys.

Only sets ITS OWN keys in es_es.json and en_us.json (idempotent). Reloads the JSON right before writing.
Usage: python -I -X utf8 tools/gen_lang_09_fix_events.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
LANG = os.path.join(HERE, "..", "common", "src", "main", "resources", "assets", "tcgshop", "lang")

ES = {
    # history of the phone's Events tab: cancelled event (e.g. an incident whose fix disappeared)
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
    print("es_es.json:", merge("es_es.json", ES), "keys")
    print("en_us.json:", merge("en_us.json", EN), "keys")
