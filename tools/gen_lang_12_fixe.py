#!/usr/bin/env python3
"""1.1 texts (Group E review fixes): Box-Opening Gloves needing a General Binder and the single broadcast of a whole-box
opening, set completion reward wording without Cobblemon / CobbleDollars (and money paid later), the Ranking assets
line, and the binder reward lines saying that only your own pulls count.
Code: pack/BoxGloves.java, pack/PackOpener.java, advancement/CardAdvancements.java, phone/Ranking.java,
client/phone/RankingApp.java, client/binder/GeneralBinderScreen.java.

Usage: python tools/gen_lang_12_fixe.py  (idempotent: sets these keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

# key: (Spanish, English)
T = {
    "tcgshop.box_gloves.need_binder": (
        "Para abrir cajas completas necesitas llevar un Binder General: ahí se guardan todas las cartas.",
        "To open whole boxes you need to carry a General Binder: that is where all the cards go."),
    "tcgshop.broadcast.box": (
        "%s abrió %s sobres de una vez y sacó %s (%s) — %s, ¡y %s cartas buenas más!",
        "%s opened %s packs at once and pulled %s (%s) — %s, plus %s more big hits!"),
    "tcgshop.collection.reward": (
        "🏆 ¡Completaste la colección %s! Premio: %s Master Ball y %s.",
        "🏆 You completed the %s set! Reward: %s Master Ball and %s."),
    "tcgshop.collection.reward_balls": (
        "🏆 ¡Completaste la colección %s! Premio: %s Master Ball.",
        "🏆 You completed the %s set! Reward: %s Master Ball."),
    "tcgshop.collection.reward_money": (
        "🏆 ¡Completaste la colección %s! Premio: %s.",
        "🏆 You completed the %s set! Reward: %s."),
    "tcgshop.collection.reward_none": (
        "🏆 ¡Completaste la colección %s!",
        "🏆 You completed the %s set!"),
    "tcgshop.collection.reward_pending": (
        "🏆 ¡Completaste la colección %s! Tu premio de %s queda guardado: se te paga en cuanto el banco esté disponible.",
        "🏆 You completed the %s set! Your %s reward is saved: it is paid as soon as the bank is available."),
    "tcgshop.collection.reward_balls_pending": (
        "🏆 ¡Completaste la colección %s! Premio: %s Master Ball. Los %s quedan guardados y se te pagan en cuanto el banco esté disponible.",
        "🏆 You completed the %s set! Reward: %s Master Ball. The %s is saved and paid as soon as the bank is available."),
    "tcgshop.collection.reward_paid": (
        "💰 Te pagaron %s que te debían por completar colecciones.",
        "💰 You were paid the %s you were owed for completing sets."),
    "tcgshop.binder.reward": (
        "Premio por completar una colección con cartas que sacaste tú: %s Master Ball + %s",
        "Reward for completing a set with cards you pulled yourself: %s Master Ball + %s"),
    "tcgshop.binder.reward_balls": (
        "Premio por completar una colección con cartas que sacaste tú: %s Master Ball",
        "Reward for completing a set with cards you pulled yourself: %s Master Ball"),
    "tcgshop.binder.reward_money": (
        "Premio por completar una colección con cartas que sacaste tú: %s",
        "Reward for completing a set with cards you pulled yourself: %s"),
    "tcgshop.rank.part.assets": (
        "Stock y cartas",
        "Stock and cards"),
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
