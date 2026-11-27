# -*- coding: utf-8 -*-
"""0.9 - "meowth" stream: short speech bubbles from Team Rocket's Meowth (they replace the TutorialScreen).

Loads tcgshop's es_es.json and en_us.json, sets/updates these keys, deletes the old tutorial ones (which nobody
uses anymore) and writes them back. Idempotent.
Usage: python tools/gen_lang_09_meowth.py  (from any folder)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "tcgshop", "lang")

# Each bubble <= ~90 letters (typed at 1.6 letters/tick and lasting 4.5-6 s).
KEYS = {
    "tcgshop.tutorial.speaker": ("Meowth del Team Rocket", "Team Rocket's Meowth"),
    # action bar (not chat) on appearing / replaying
    "tcgshop.tutorial.appears": (
        "¡%s quiere hablarte! Clic derecho en él para pasar.",
        "%s wants a word! Right-click him to skip ahead."),
    # without Cobblemon (or the Meowth couldn't spawn): single chat line
    "tcgshop.tutorial.no_meowth": (
        "¡Tienda lista! Usa /tcgshop tutorial para ver unos consejos rápidos.",
        "Shop ready! Use /tcgshop tutorial for some quick tips."),

    "tcgshop.tutorial.bubble.1": (
        "¡Miau! Soy el Meowth del Team Rocket. Ahora asesoro tiendas de cartas, ¡escucha!",
        "Meow! I'm Team Rocket's Meowth. These days I advise card shops, listen up!"),
    "tcgshop.tutorial.bubble.2": (
        "Llena los expositores con cartas y sobres y ponles precio: así vendrán clientes.",
        "Stock your displays with cards and packs and price them: that brings customers."),
    "tcgshop.tutorial.bubble.3": (
        "¿Te falta género? El Mayorista vende cajas y sobres al por mayor, más baratos.",
        "Low on stock? The Wholesaler sells boxes and packs in bulk, for cheaper."),
    "tcgshop.tutorial.bubble.4": (
        "Gradea tus mejores cartas con CobbleGrade: un buen grado multiplica su precio.",
        "Grade your best cards with CobbleGrade: a great grade multiplies their price."),
    "tcgshop.tutorial.bubble.5": (
        "En la Liga TCG juegas duelos con tu mazo y subes de rango en ranked. ¡Miau!",
        "In the TCG League you duel with your deck and climb the ranked ladder. Meow!"),
    "tcgshop.tutorial.bubble.6": (
        "Tu teléfono lo tiene todo: mercado, precios, tienda, liga y más. ¡No lo pierdas!",
        "Your phone has it all: market, prices, shop, league and more. Don't lose it!"),
    "tcgshop.tutorial.bubble.7": (
        "Ah, y puedes capturarme si quieres... pero un Meowth nunca trabaja gratis.",
        "Oh, and you can catch me if you like... but a Meowth never works for free."),
    "tcgshop.tutorial.bubble.8": (
        "¡Eso es todo, socio! Si lo olvidas: /tcgshop tutorial. ¡Suerte con la tienda!",
        "That's all, partner! If you forget: /tcgshop tutorial. Good luck with the shop!"),
}

# Old tutorial (TutorialScreen, removed in 0.9): no code references these anymore.
OBSOLETE_PREFIXES = ("tcgshop.tutorial.topic.", "tcgshop.tutorial.line.")
OBSOLETE_KEYS = {
    "tcgshop.tutorial.title", "tcgshop.tutorial.chat_fallback", "tcgshop.tutorial.skip", "tcgshop.tutorial.close",
    "tcgshop.tutorial.back", "tcgshop.tutorial.finish", "tcgshop.tutorial.hint", "tcgshop.tutorial.hint_replay",
    "tcgshop.tutorial.replay_cmd",
}


def update(path, idx):
    with open(path, encoding="utf-8", newline="") as f:
        raw = f.read()
    nl = "\r\n" if "\r\n" in raw else "\n"  # keep whatever line ending the file has
    data = json.loads(raw)
    removed = [k for k in data if k in OBSOLETE_KEYS or k.startswith(OBSOLETE_PREFIXES)]
    for k in removed:
        del data[k]
    for k, v in KEYS.items():
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8", newline=nl) as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    return len(removed)


if __name__ == "__main__":
    for k, (es, en) in KEYS.items():
        assert len(es) <= 95 and len(en) <= 95, k
    r1 = update(os.path.join(LANG, "es_es.json"), 0)
    r2 = update(os.path.join(LANG, "en_us.json"), 1)
    print("meowth: %d keys written, %d/%d obsolete deleted" % (len(KEYS), r1, r2))
