#!/usr/bin/env python3
"""Textos de interfaz (binders, apertura en grupo, Scalper, estilos, teclas...). Español e inglés."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

T = {
    # ítems / entidad / grupo / teclas
    "item.tcgshop.general_binder": ("Binder General", "General Binder"),
    "item.tcgshop.general_binder.tip1": ("Guarda todas tus cartas en un solo inventario", "Stores all your cards in a single inventory"),
    "item.tcgshop.general_binder.tip2": ("Clic derecho, o tecla G si lo equipas en Accessories", "Right-click, or press G when equipped in Accessories"),
    "item.tcgshop.personal_binder": ("Personal Binder", "Personal Binder"),
    "item.tcgshop.personal_binder.tip": ("%s cartas · %s páginas", "%s cards · %s pages"),
    "item.tcgshop.scalper_spawn_egg": ("Huevo generador de Scalper", "Scalper Spawn Egg"),
    "entity.tcgshop.scalper": ("Scalper", "Scalper"),
    "itemGroup.tcgshop": ("MasterBall Traders Co.", "MasterBall Traders Co."),
    "key.tcgshop.open_binder": ("Abrir Binder General", "Open General Binder"),
    "key.categories.tcgshop": ("MasterBall Traders Co.", "MasterBall Traders Co."),

    # Binder General
    "tcgshop.binder.general": ("Binder General", "General Binder"),
    "tcgshop.binder.need": ("Necesitas un Binder General en el inventario o equipado.", "You need a General Binder in your inventory or equipped."),
    "tcgshop.binder.search": ("Buscar carta...", "Search card..."),
    "tcgshop.binder.stats": ("%s / %s cartas · %s · Valor %s", "%s / %s cards · %s · Value %s"),
    "tcgshop.binder.all_sets": ("★ Todos los packs", "★ All packs"),
    "tcgshop.binder.all_rarities": ("Todas", "All"),
    "tcgshop.binder.missing_on": ("Huecos: sí", "Gaps: on"),
    "tcgshop.binder.missing_off": ("Huecos: no", "Gaps: off"),
    "tcgshop.binder.empty": ("No hay cartas con estos filtros", "No cards match these filters"),
    "tcgshop.binder.have": ("Tienes: %s", "Owned: %s"),
    "tcgshop.binder.take1": ("Sacar 1", "Take 1"),
    "tcgshop.binder.take_all": ("Sacar todas", "Take all"),
    "tcgshop.binder.deposit": ("Guardar cartas del inventario", "Store inventory cards"),
    "tcgshop.binder.deposit_tip": ("Mueve todas las cartas de tu inventario a este binder", "Moves every card in your inventory into this binder"),
    "tcgshop.binder.packs": ("Sobres: %s · arruinados: %s", "Packs: %s · ruined: %s"),
    "tcgshop.binder.tip1": ("Pasa el cursor sobre una carta con ★ para marcarla como vista.",
                            "Hover a ★ card to mark it as seen."),
    "tcgshop.binder.tip2": ("Doble clic en una pestaña para limpiar todas sus notificaciones.",
                            "Double-click a tab to clear all of its notifications."),
    "tcgshop.binder.tip3": ("Con este binder encima, las cartas de los sobres se guardan aquí directamente.",
                            "While you carry this binder, cards from packs are stored here directly."),

    # Personal Binder
    "tcgshop.personal.customize": ("✎ Personalizar", "✎ Customize"),
    "tcgshop.personal.add_pages": ("Añadir dos páginas", "Add two pages"),
    "tcgshop.personal.pages": ("Páginas %s-%s de %s · %s cartas", "Pages %s-%s of %s · %s cards"),
    "tcgshop.personal.drawer_empty": ("Tu Binder General está vacío. Guarda cartas en él para colocarlas aquí.",
                                      "Your General Binder is empty. Store cards in it to place them here."),
    "tcgshop.style.tab.palette": ("Colores", "Colors"),
    "tcgshop.style.tab.pattern": ("Patrones", "Patterns"),
    "tcgshop.style.tab.background": ("Fondos", "Backgrounds"),
    "tcgshop.style.locked": ("Bloqueado · Logro: %s", "Locked · Advancement: %s"),

    # Panini
    "tcgshop.panini.page": ("Página %s / %s", "Page %s / %s"),
    "tcgshop.panini.visible": ("Cromos en esta doble página: %s / %s", "Stickers on this spread: %s / %s"),
    "tcgshop.panini.missing": ("Te falta este cromo", "You're missing this card"),

    # apertura
    "tcgshop.open.next_group": ("Siguiente rasgada (quedan %s)", "Next rip (%s left)"),
    "tcgshop.open.reveal_pack": ("Sobre %s/%s", "Pack %s/%s"),
    "tcgshop.open.to_binder": ("Guardadas en tu Binder General", "Stored in your General Binder"),
    "tcgshop.open.to_binder_variants": ("Guardadas en tu Binder General (las variantes especiales, en tu inventario)",
                                        "Stored in your General Binder (special print variants went to your inventory)"),
    "tcgshop.open.summary_group": ("¡%s sobres abiertos!", "%s packs opened!"),
    "tcgshop.open.tear_many": ("Corta los sobres por la línea punteada: un trazo puede cortar varios", "Cut the packs along the dotted line: one stroke can cut several"),

    # selección
    "tcgshop.select.title": ("Abrir sobres", "Open packs"),
    "tcgshop.select.controls": ("Clic +1 · Clic derecho -1 · Rueda ±1 · Mayús ±10", "Click +1 · Right-click -1 · Wheel ±1 · Shift ±10"),
    "tcgshop.select.market": ("Mercado: %s por sobre", "Market: %s per pack"),
    "tcgshop.select.per_tear": ("Sobres por rasgada", "Packs per rip"),
    "tcgshop.select.summary": ("%s sobres · %s rasgadas", "%s packs · %s rips"),
    "tcgshop.select.value": ("Valor de mercado ≈ %s", "Market value ≈ %s"),
    "tcgshop.select.state_sealed": ("Sellado", "Sealed"),
    "tcgshop.select.state_opened": ("Abierto", "Opened"),
    "tcgshop.select.open_one": ("¡Abrir %s sobre!", "Open %s pack!"),
    "tcgshop.select.open_many": ("¡Abrir %s sobres!", "Open %s packs!"),
    "tcgshop.select.cancel": ("Ahora no", "Not now"),

    # Scalper
    "tcgshop.scalper.offer_title": ("Psst... ¿un sobre?", "Psst... a pack?"),
    "tcgshop.scalper.offer_text": ("Tengo un %s recién salido de la caja. Te lo dejo barato... para mí.",
                                   "I've got a %s fresh out of the box. I'll give you a great deal... for me."),
    "tcgshop.scalper.price": ("Su precio: %s", "His price: %s"),
    "tcgshop.scalper.market": ("Precio de mercado: %s", "Market price: %s"),
    "tcgshop.scalper.markup": ("Sobreprecio: %s", "Markup: %s"),
    "tcgshop.scalper.buy": ("Comprar (%s)", "Buy (%s)"),
    "tcgshop.scalper.refuse": ("No, gracias", "No, thanks"),
    "tcgshop.scalper.offer_line": ("Eh, tú. Sí, tú. ¿Coleccionas cartas? Tengo algo que te va a interesar...",
                                   "Hey, you. Yes, you. Collect cards? I've got something you'll like..."),
    "tcgshop.scalper.saw_opening": ("¡Esos sobres son míos! ¡Dámelos!", "Those packs are mine! Hand them over!"),
    "tcgshop.scalper.ignored": ("¿Me ignoras? Entonces me lo cobraré de otra forma...", "Ignoring me? Then I'll get paid another way..."),
    "tcgshop.scalper.ran_away": ("¿Te vas sin comprar? Ni lo sueñes.", "Leaving without buying? Not a chance."),
    "tcgshop.scalper.rejected": ("Mala elección, coleccionista...", "Bad choice, collector..."),
    "tcgshop.scalper.no_money": ("¿Sin Pokédólares? Pues me llevo tus cartas.", "No Pokédollars? Then I'll take your cards."),
    "tcgshop.scalper.sold": ("Un placer hacer negocios contigo. Je, je. (%s)", "Pleasure doing business. Heh heh. (%s)"),
    "tcgshop.scalper.stole": ("¡Gracias por %s! ¡Hasta nunca!", "Thanks for %s! See ya never!"),
    "tcgshop.scalper.defeated": ("¡Mis... mis sobres...!", "My... my packs...!"),
}

STYLES = {
    "palette.classic_red": ("Rojo clásico", "Classic red"), "palette.ocean_blue": ("Azul océano", "Ocean blue"),
    "palette.forest_green": ("Verde bosque", "Forest green"), "palette.midnight": ("Medianoche", "Midnight"),
    "palette.sunny_yellow": ("Amarillo Pikachu", "Pikachu yellow"), "palette.psychic_pink": ("Rosa psíquico", "Psychic pink"),
    "palette.ghost_purple": ("Morado fantasma", "Ghost purple"), "palette.ice_cyan": ("Cian hielo", "Ice cyan"),
    "palette.grad_sunset": ("Atardecer", "Sunset"), "palette.grad_ocean": ("Corriente marina", "Ocean current"),
    "palette.grad_forest": ("Bosque Verde", "Viridian Forest"), "palette.grad_aurora": ("Aurora", "Aurora"),
    "palette.grad_volcano": ("Volcán", "Volcano"), "palette.grad_galaxy": ("Galaxia", "Galaxy"),
    "palette.grad_gold": ("Oro hiper raro", "Hyper rare gold"), "palette.grad_master": ("Master Ball", "Master Ball"),
    "pattern.none": ("Sin patrón", "No pattern"), "pattern.dots": ("Puntos", "Dots"),
    "pattern.pokeballs": ("Poké Balls", "Poké Balls"), "pattern.lightning": ("Rayos (Eléctrico)", "Lightning (Electric)"),
    "pattern.flames": ("Llamas (Fuego)", "Flames (Fire)"), "pattern.waves": ("Olas (Agua)", "Waves (Water)"),
    "pattern.leaves": ("Hojas (Planta)", "Leaves (Grass)"), "pattern.stars": ("Estrellas (Hada)", "Stars (Fairy)"),
    "pattern.scales": ("Escamas (Dragón)", "Scales (Dragon)"), "pattern.paws": ("Huellas (Normal)", "Paw prints (Normal)"),
    "pattern.diamonds": ("Diamantes (Acero)", "Diamonds (Steel)"), "pattern.crystals": ("Cristales (Roca)", "Crystals (Rock)"),
    "background.none": ("Sin fondo", "No background"), "background.meadow": ("Pradera", "Meadow"),
    "background.beach": ("Playa tropical", "Tropical beach"), "background.night_sky": ("Noche estrellada", "Starry night"),
    "background.crystal_cave": ("Cueva de cristal", "Crystal cave"), "background.snowy_peaks": ("Cumbres nevadas", "Snowy peaks"),
    "background.berry_grove": ("Arboleda de bayas", "Berry grove"), "background.volcano": ("Volcán", "Volcano"),
    "background.aurora_tundra": ("Tundra boreal", "Aurora tundra"), "background.ancient_ruins": ("Ruinas antiguas", "Ancient ruins"),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        for k, v in STYLES.items():
            d["tcgshop.style." + k] = v[i]
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
    print(len(T) + len(STYLES), "textos")


if __name__ == "__main__":
    main()
