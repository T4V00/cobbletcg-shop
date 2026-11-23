"""Texts for the duel cosmetics (3D coins, board backgrounds, achievements) and for placement in display cases."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

COINS = {
    "gold": ("Oro clásico", "Classic Gold"), "copper": ("Cobre", "Copper"), "silver": ("Plata", "Silver"),
    "great": ("Super Ball", "Great Ball"), "ultra": ("Ultra Ball", "Ultra Ball"), "platinum": ("Platino", "Platinum"),
    "diamond": ("Diamante", "Diamond"), "master": ("Master Ball", "Master Ball"), "champion": ("Corona de Campeón", "Champion's Crown"),
    "legend": ("Sol legendario", "Legendary Sun"), "volt": ("Voltio", "Volt"), "ember": ("Ascua", "Ember"), "tide": ("Marea", "Tide"),
    "shield": ("Escudo de acero", "Steel Shield"), "verdant": ("Hoja verde", "Verdant Leaf"), "frost": ("Escarcha", "Frost"),
    "lunar": ("Luna nueva", "New Moon"), "solar": ("Sol radiante", "Radiant Sun"), "heart": ("Corazón hada", "Fairy Heart"),
    "mind": ("Espiral psíquica", "Psychic Spiral"), "premier": ("Honor Ball", "Premier Ball"), "luxury": ("Lujo Ball", "Luxury Ball"),
    "obsidian": ("Obsidiana", "Obsidian"), "clover": ("Trébol de la suerte", "Lucky Clover"), "shiny": ("Estrella variocolor", "Shiny Star"),
    "phantom": ("Fantasma", "Phantom"), "wyrm": ("Llama de dragón", "Dragon Flame"),
}
BOARDS = {
    "classic": ("Clásico", "Classic"), "pokeball": ("Poké Balls", "Poké Balls"), "meadow": ("Pradera", "Meadow"),
    "ocean": ("Océano", "Ocean"), "blaze": ("Llamarada", "Blaze"), "storm": ("Tormenta eléctrica", "Thunderstorm"),
    "glacier": ("Glaciar", "Glacier"), "psyche": ("Mente psíquica", "Psychic Mind"), "haunt": ("Fuegos fatuos", "Will-o'-wisps"),
    "pixie": ("Polvo de hada", "Pixie Dust"), "forge": ("Forja de acero", "Steel Forge"), "canyon": ("Cañón", "Canyon"),
    "dragon": ("Escamas de dragón", "Dragon Scales"), "midnight": ("Medianoche", "Midnight"), "master": ("Master", "Master"),
    "champion": ("Salón del Campeón", "Champion's Hall"), "cosmos": ("Cosmos", "Cosmos"), "toxic": ("Pantano tóxico", "Toxic Swamp"),
    "dojo": ("Dojo", "Dojo"), "sakura": ("Sakura", "Sakura"), "prism": ("Prisma", "Prism"),
}

T = {
    "tcgshop.league.tab.coins": ("Monedas", "Coins"),
    "tcgshop.league.tab.boards": ("Fondos", "Boards"),
    "tcgshop.league.tab.goals": ("Logros", "Goals"),
    "tcgshop.cos.rule.free": ("Gratis desde el principio", "Free from the start"),
    "tcgshop.cos.rule.rank": ("Alcanza el rango %s en la Liga", "Reach %s rank in the League"),
    "tcgshop.cos.rule.peak": ("Llega a %s puntos de Liga", "Reach %s League points"),
    "tcgshop.cos.rule.wins": ("Gana %s partidas de Liga", "Win %s League matches"),
    "tcgshop.cos.rule.ai_wins": ("Gana %s partidas a la casa", "Win %s matches against the house"),
    "tcgshop.cos.rule.played": ("Juega %s partidas", "Play %s matches"),
    "tcgshop.cos.rule.streak": ("Consigue una racha de %s victorias de Liga", "Get a %s-win League streak"),
    "tcgshop.cos.rule.packs": ("Abre %s sobres", "Open %s packs"),
    "tcgshop.cos.rule.loot": ("Botín raro: puede salir al abrir sobres", "Rare loot: can drop when opening packs"),
    "tcgshop.cos.unlocked_coin": ("¡Nueva moneda desbloqueada: %s! Equípala en el teléfono → Liga → Monedas.",
                                  "New coin unlocked: %s! Equip it in Phone → League → Coins."),
    "tcgshop.cos.unlocked_board": ("¡Nuevo fondo de tablero: %s! Equípalo en el teléfono → Liga → Fondos.",
                                   "New board background: %s! Equip it in Phone → League → Boards."),
    "tcgshop.cos.loot_coin": ("¡Dentro del sobre había una moneda rara: %s!", "There was a rare coin inside the pack: %s!"),
    "tcgshop.cos.equipped": ("✔ Equipada", "✔ Equipped"),
    "tcgshop.cos.click_equip": ("Clic para equipar", "Click to equip"),
    "tcgshop.cos.coins_title": ("Monedas %s/%s", "Coins %s/%s"),
    "tcgshop.cos.boards_title": ("Fondos de tablero %s/%s", "Board backgrounds %s/%s"),
    "tcgshop.cos.goals_title": ("Logros %s/%s", "Goals %s/%s"),
    "tcgshop.cos.streak": ("Racha %s · mejor %s", "Streak %s · best %s"),
    "tcgshop.cos.kind_coin": ("Moneda: %s", "Coin: %s"),
    "tcgshop.cos.kind_board": ("Fondo: %s", "Board: %s"),
    "tcgshop.cos.done": ("Conseguido", "Unlocked"),
    "tcgshop.cos.loot": ("Botín", "Loot"),
    "tcgshop.display.place_here": ("▼ Colocar aquí", "▼ Place here"),
    "tcgshop.display.suggested": ("Precio de mercado: %s", "Market price: %s"),
    "tcgshop.display.restock": ("Clic derecho: reponer +%s", "Right-click: restock +%s"),
    "tcgshop.shop.restocked": ("Repones %s × %s (ahora %s)", "Restocked %s × %s (now %s)"),
}
for k, v in COINS.items():
    T["tcgshop.coin." + k] = v
for k, v in BOARDS.items():
    T["tcgshop.board." + k] = v


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
