# -*- coding: utf-8 -*-
"""0.9 - stream "meowth": bocadillos cortos del Meowth del Team Rocket (sustituyen a la pantalla TutorialScreen).

Carga es_es.json y en_us.json de tcgshop, pone/actualiza estas claves, borra las del tutorial antiguo (que ya no usa
nadie) y los vuelve a escribir. Idempotente.
Uso: python tools/gen_lang_09_meowth.py  (desde cualquier carpeta)
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "tcgshop", "lang")

# Cada bocadillo <= ~90 letras (se escriben a 1.6 letras/tick y duran 4,5-6 s).
KEYS = {
    "tcgshop.tutorial.speaker": ("Meowth del Team Rocket", "Team Rocket's Meowth"),
    # barra de acción (no chat) al aparecer / repetir
    "tcgshop.tutorial.appears": (
        "¡%s quiere hablarte! Clic derecho en él para pasar.",
        "%s wants a word! Right-click him to skip ahead."),
    # sin Cobblemon (o el Meowth no pudo aparecer): única línea de chat
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

# Tutorial antiguo (pantalla TutorialScreen, eliminada en la 0.9): ya no los referencia ningún código.
OBSOLETE_PREFIXES = ("tcgshop.tutorial.topic.", "tcgshop.tutorial.line.")
OBSOLETE_KEYS = {
    "tcgshop.tutorial.title", "tcgshop.tutorial.chat_fallback", "tcgshop.tutorial.skip", "tcgshop.tutorial.close",
    "tcgshop.tutorial.back", "tcgshop.tutorial.finish", "tcgshop.tutorial.hint", "tcgshop.tutorial.hint_replay",
    "tcgshop.tutorial.replay_cmd",
}


def update(path, idx):
    with open(path, encoding="utf-8", newline="") as f:
        raw = f.read()
    nl = "\r\n" if "\r\n" in raw else "\n"  # se respeta el fin de linea que tenga el fichero
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
    print("meowth: %d claves escritas, %d/%d obsoletas borradas" % (len(KEYS), r1, r2))
