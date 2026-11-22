#!/usr/bin/env python3
"""0.9 · Binder: vista ampliada de cartas (clic en la carta del panel derecho del Binder General; Shift+clic o clic
derecho en el Personal Binder). Fusiona las claves en es_es / en_us (idempotente, conserva el orden de claves).
Uso: python tools/gen_lang_09_binder.py"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

# clave -> (español, inglés)
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
    print(f"{len(KEYS)} claves fusionadas en es_es.json y en_us.json")
