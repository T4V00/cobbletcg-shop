#!/usr/bin/env python3
"""v0.9 - estatuas de lujo (Exclusivos): textos es_es / en_us. Idempotente; sólo toca sus claves."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "tcgshop" / "lang"

ES = {
    "block.tcgshop.statue_bronze": "Estatua de Bronce del Emprendedor",
    "block.tcgshop.statue_silver": "Obelisco de Plata del Comerciante",
    "block.tcgshop.statue_gold": "Orbe Dorado del Mercader",
    "block.tcgshop.statue_emerald": "Ángel de Esmeralda del Inversor",
    "block.tcgshop.statue_diamond": "Corona de Diamante del Visionario",
    "block.tcgshop.statue_netherite": "Monumento de Netherita y Amatista del Soberano",
    "block.tcgshop.statue_masterball": "Estatua MasterBall del Magnate",
    "block.tcgshop.statue_part": "Estatua",
    "tcgshop.furn.cat.luxury": "★ Exclusivos",
    "tcgshop.lux.tab_button": "★ Exclusivos",
    "tcgshop.lux.tip_tier": "Exclusivo · nivel %s de %s",
    "tcgshop.lux.tip_size": "Ocupa %s bloque(s) de alto",
    "tcgshop.lux.tip_price": "Precio de catálogo: %s",
    "tcgshop.lux.tip_no_resale": "Exclusivo: no se puede revender",
    "tcgshop.lux.tip_glow": "Desprende luz propia",
    "tcgshop.lux.flavor.bronze": "Tus primeros mil bien invertidos... o casi.",
    "tcgshop.lux.flavor.silver": "Para que el barrio sepa que tu tienda va en serio.",
    "tcgshop.lux.flavor.gold": "Brilla tanto como tus márgenes de beneficio.",
    "tcgshop.lux.flavor.emerald": "Un millón convertido en alas. Literalmente.",
    "tcgshop.lux.flavor.diamond": "Diez millones de razones para mirar hacia arriba.",
    "tcgshop.lux.flavor.netherite": "Forjado en el Nether y coronado de cristal vivo.",
    "tcgshop.lux.flavor.masterball": "Mil millones. Sólo hay una razón para tenerla: porque puedes.",
    "tcgshop.lux.no_space": "No cabe: necesita %s bloques libres de alto",
    "tcgshop.lux.bought": "Has adquirido %s por %s. ¡Que la envidia te acompañe!",
    "tcgshop.lux.announce": "✦ %s acaba de adquirir %s por %s ✦",
    "tcgshop.lux.no_resale": "Las estatuas exclusivas no se pueden revender",
}

EN = {
    "block.tcgshop.statue_bronze": "Bronze Entrepreneur Statue",
    "block.tcgshop.statue_silver": "Silver Dealer Obelisk",
    "block.tcgshop.statue_gold": "Golden Merchant Orb",
    "block.tcgshop.statue_emerald": "Emerald Investor Angel",
    "block.tcgshop.statue_diamond": "Diamond Visionary Crown",
    "block.tcgshop.statue_netherite": "Netherite-Amethyst Sovereign Monument",
    "block.tcgshop.statue_masterball": "MasterBall Tycoon Statue",
    "block.tcgshop.statue_part": "Statue",
    "tcgshop.furn.cat.luxury": "★ Exclusives",
    "tcgshop.lux.tab_button": "★ Exclusives",
    "tcgshop.lux.tip_tier": "Exclusive · tier %s of %s",
    "tcgshop.lux.tip_size": "%s block(s) tall",
    "tcgshop.lux.tip_price": "Catalogue price: %s",
    "tcgshop.lux.tip_no_resale": "Exclusive: cannot be resold",
    "tcgshop.lux.tip_glow": "Gives off its own light",
    "tcgshop.lux.flavor.bronze": "Your first thousand, well spent... mostly.",
    "tcgshop.lux.flavor.silver": "So the whole block knows your shop means business.",
    "tcgshop.lux.flavor.gold": "Shines as bright as your profit margins.",
    "tcgshop.lux.flavor.emerald": "A million turned into wings. Literally.",
    "tcgshop.lux.flavor.diamond": "Ten million reasons to look up.",
    "tcgshop.lux.flavor.netherite": "Forged in the Nether, crowned with living crystal.",
    "tcgshop.lux.flavor.masterball": "A billion. There is only one reason to own it: because you can.",
    "tcgshop.lux.no_space": "Not enough room: it needs %s free blocks of height",
    "tcgshop.lux.bought": "You acquired %s for %s. May envy be with you!",
    "tcgshop.lux.announce": "✦ %s just acquired %s for %s ✦",
    "tcgshop.lux.no_resale": "Exclusive statues cannot be resold",
}


def apply(name, values):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, v in values.items():
        data[k] = v  # las claves existentes conservan su posición; las nuevas van al final
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    assert set(ES) == set(EN)
    apply("es_es.json", ES)
    apply("en_us.json", EN)
    print("gen_lang_09_statues:", len(ES), "claves")
