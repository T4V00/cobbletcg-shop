# -*- coding: utf-8 -*-
"""Textos del stream "onboarding": sobres de bienvenida y nombre del Meowth del Team Rocket.

Los bocadillos del tutorial (0.9) están en tools/gen_lang_09_meowth.py.

Carga es_es.json y en_us.json de tcgshop, inserta/actualiza estas claves y los vuelve a escribir.
Uso: python tools/gen_lang_onboarding.py  (desde la carpeta del proyecto tcgshop)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "tcgshop", "lang")

KEYS = {
    # --- sobres de bienvenida
    "tcgshop.onboarding.starter": (
        "¡Bienvenido a MasterBall Traders Co.! Recibes %s sobres sellados al azar para empezar tu colección.",
        "Welcome to MasterBall Traders Co.! You get %s random sealed packs to start your collection."),
    "tcgshop.onboarding.starter_hint": (
        "Usa un sobre (clic derecho) para abrirlo. Cuando tengas tu primera tienda, alguien vendrá a explicártelo todo...",
        "Use a pack (right-click) to open it. Once you own your first shop, someone will come to explain everything..."),

    # --- tutorial (desde la 0.9 el resto de textos del Meowth están en gen_lang_09_meowth.py)
    "tcgshop.tutorial.speaker": ("Meowth del Team Rocket", "Team Rocket's Meowth"),
}


def update(path, idx):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for k, v in KEYS.items():
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    update(os.path.join(LANG, "es_es.json"), 0)
    update(os.path.join(LANG, "en_us.json"), 1)
    print("onboarding: %d claves escritas" % len(KEYS))
