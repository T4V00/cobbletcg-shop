# -*- coding: utf-8 -*-
"""Texts for the "onboarding" stream: welcome packs and the name of Team Rocket's Meowth.

The tutorial speech bubbles (0.9) are in tools/gen_lang_09_meowth.py.

Loads tcgshop's es_es.json and en_us.json, inserts/updates these keys and writes them back.
Usage: python tools/gen_lang_onboarding.py  (from the tcgshop project folder)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "tcgshop", "lang")

KEYS = {
    # --- welcome packs
    "tcgshop.onboarding.starter": (
        "¡Bienvenido a MasterBall Traders Co.! Recibes %s sobres sellados al azar para empezar tu colección.",
        "Welcome to MasterBall Traders Co.! You get %s random sealed packs to start your collection."),
    "tcgshop.onboarding.starter_hint": (
        "Usa un sobre (clic derecho) para abrirlo. Cuando tengas tu primera tienda, alguien vendrá a explicártelo todo...",
        "Use a pack (right-click) to open it. Once you own your first shop, someone will come to explain everything..."),

    # --- tutorial (since 0.9 the rest of the Meowth texts are in gen_lang_09_meowth.py)
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
    print("onboarding: %d keys written" % len(KEYS))
