#!/usr/bin/env python3
"""Textos de los comandos /tcgshop card (give, givepack, random, info). Uso: python tools/gen_lang_card_cmd.py"""
import json
from pathlib import Path

R = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

KEYS = {
    # errores
    "tcgshop.cmd.card.unknown": ("No existe ninguna carta «%s». Usa masterball:<set>/cards/<carta> o <set>/<carta>.",
                                 "There is no card \"%s\". Use masterball:<set>/cards/<card> or <set>/<card>."),
    "tcgshop.cmd.card.bad_variant": ("Variante desconocida: «%s».", "Unknown print variant: \"%s\"."),
    "tcgshop.cmd.card.unknown_set": ("No existe el set «%s».", "There is no set \"%s\"."),
    "tcgshop.cmd.card.bad_tier": ("Tipo de sobre desconocido: «%s» (sealed, normal, semigod o god).",
                                  "Unknown pack type: \"%s\" (sealed, normal, semigod or god)."),
    "tcgshop.cmd.card.bad_family": ("Familia de rareza desconocida: «%s».", "Unknown rarity family: \"%s\"."),
    "tcgshop.cmd.card.bad_grade": ("El Grado va de 1.0 a 10.0 (0 = sin gradear).", "Grade goes from 1.0 to 10.0 (0 = ungraded)."),
    "tcgshop.cmd.card.no_cards": ("No hay cartas de esa familia.", "There are no cards of that family."),
    # tipos de sobre y familias
    "tcgshop.cmd.card.tier.sealed": ("Sellado", "Sealed"),
    "tcgshop.cmd.card.tier.normal": ("Abierto normal", "Opened (normal)"),
    "tcgshop.cmd.card.tier.semigod": ("Abierto semi-dios", "Opened (semi-god)"),
    "tcgshop.cmd.card.tier.god": ("Abierto dios", "Opened (god)"),
    "tcgshop.cmd.card.any_family": ("cualquier rareza", "any rarity"),
    # resultados
    "tcgshop.cmd.card.give.one": ("Has dado %s × %s a %s.", "Gave %s × %s to %s."),
    "tcgshop.cmd.card.give.many": ("Has dado %s × %s a %s jugadores.", "Gave %s × %s to %s players."),
    "tcgshop.cmd.card.givepack.one": ("Has dado %s × %s a %s.", "Gave %s × %s to %s."),
    "tcgshop.cmd.card.givepack.many": ("Has dado %s × %s a %s jugadores.", "Gave %s × %s to %s players."),
    "tcgshop.cmd.card.random.one": ("Has dado %s cartas al azar (%s) a %s.", "Gave %s random cards (%s) to %s."),
    "tcgshop.cmd.card.random.many": ("Has dado %s cartas al azar (%s) a %s jugadores.", "Gave %s random cards (%s) to %s players."),
    # /tcgshop card info
    "tcgshop.cmd.card.info.header": ("— %s —", "— %s —"),
    "tcgshop.cmd.card.info.id": ("Id: %s", "Id: %s"),
    "tcgshop.cmd.card.info.set": ("Set: %s", "Set: %s"),
    "tcgshop.cmd.card.info.pack_tier": ("Tipo de sobre: %s", "Pack type: %s"),
    "tcgshop.cmd.card.info.rarity": ("Rareza: %s", "Rarity: %s"),
    "tcgshop.cmd.card.info.variant": ("Variante: %s", "Print variant: %s"),
    "tcgshop.cmd.card.info.grade": ("Grado: %s", "Grade: %s"),
    "tcgshop.cmd.card.info.cert": (" · Certificado nº %s", " · Cert #%s"),
    "tcgshop.cmd.card.info.ungraded": ("sin gradear", "ungraded"),
    "tcgshop.cmd.card.info.price": ("Precio de mercado: %s", "Market price: %s"),
    "tcgshop.cmd.card.info.price_stack": ("Precio de la pila: %s", "Stack price: %s"),
}


def update(name, idx):
    path = R / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, v in KEYS.items():
        data[k] = v[idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{name}: {len(KEYS)} claves")


if __name__ == "__main__":
    update("es_es.json", 0)
    update("en_us.json", 1)
