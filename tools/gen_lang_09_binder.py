#!/usr/bin/env python3
"""0.9 · Binder: enlarged card view (click the card in the right panel of the General Binder; Shift+click or right-
click in the Personal Binder). Merges the keys into es_es / en_us (idempotent, keeps the key order).
Usage: python tools/gen_lang_09_binder.py"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

# key -> (Spanish, English)
KEYS = {
    "tcgshop.binder.zoom_hint": ("Clic para ver en grande", "Click to view large"),
    "tcgshop.binder.zoom_hint_personal": ("Mayús+clic o clic derecho: ver en grande", "Shift+click or right-click: view large"),
    "tcgshop.binder.zoom_close": ("ESC, clic derecho o clic fuera para cerrar", "ESC, right-click or click outside to close"),
}


def merge(file_name: str, idx: int) -> None:
    path = LANG / file_name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, v in KEYS.items():
        data[k] = v[idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    merge("es_es.json", 0)
    merge("en_us.json", 1)
    print(f"{len(KEYS)} keys merged into es_es.json and en_us.json")
