#!/usr/bin/env python3
"""Texts for the TCG competitive mode, the League, Pokémon duels between NPCs, bad smell / spray and new and home displays."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

TYPES = {"normal": ("Normal", "Normal"), "fire": ("Fuego", "Fire"), "water": ("Agua", "Water"), "electric": ("Eléctrico", "Electric"),
         "grass": ("Planta", "Grass"), "ice": ("Hielo", "Ice"), "fighting": ("Lucha", "Fighting"), "poison": ("Veneno", "Poison"),
         "ground": ("Tierra", "Ground"), "flying": ("Volador", "Flying"), "psychic": ("Psíquico", "Psychic"), "bug": ("Bicho", "Bug"),
         "rock": ("Roca", "Rock"), "ghost": ("Fantasma", "Ghost"), "dragon": ("Dragón", "Dragon"), "dark": ("Siniestro", "Dark"),
         "steel": ("Acero", "Steel"), "fairy": ("Hada", "Fairy")}
MOVES = {"normal": (("Placaje", "Tackle"), ("Hiperrayo", "Hyper Beam")), "fire": (("Ascuas", "Ember"), ("Lanzallamas", "Flamethrower")),
         "water": (("Pistola Agua", "Water Gun"), ("Hidrobomba", "Hydro Pump")), "electric": (("Impactrueno", "Thunder Shock"), ("Rayo", "Thunderbolt")),
         "grass": (("Látigo Cepa", "Vine Whip"), ("Rayo Solar", "Solar Beam")), "ice": (("Nieve Polvo", "Powder Snow"), ("Ventisca", "Blizzard")),
         "fighting": (("Patada Baja", "Low Kick"), ("A Bocajarro", "Close Combat")), "poison": (("Picotazo Veneno", "Poison Sting"), ("Bomba Lodo", "Sludge Bomb")),
         "ground": (("Bofetón Lodo", "Mud-Slap"), ("Terremoto", "Earthquake")), "flying": (("Tornado", "Gust"), ("Pájaro Osado", "Brave Bird")),
         "psychic": (("Confusión", "Confusion"), ("Psíquico", "Psychic")), "bug": (("Picadura", "Bug Bite"), ("Zumbido", "Bug Buzz")),
         "rock": (("Lanzarrocas", "Rock Throw"), ("Roca Afilada", "Stone Edge")), "ghost": (("Lengüetazo", "Lick"), ("Bola Sombra", "Shadow Ball")),
         "dragon": (("Dragoaliento", "Dragon Breath"), ("Enfado", "Outrage")), "dark": (("Mordisco", "Bite"), ("Pulso Umbrío", "Dark Pulse")),
         "steel": (("Garra Metal", "Metal Claw"), ("Foco Resplandor", "Flash Cannon")), "fairy": (("Viento Feérico", "Fairy Wind"), ("Fuerza Lunar", "Moonblast"))}

PAIRS = {
    # new blocks
    "block.tcgshop.counter_display": ("Mostrador de caja con atriles", "Counter Display"),
    "block.tcgshop.showcase": ("Vitrina torre", "Showcase Tower"),
    "block.tcgshop.card_ledge": ("Repisa de cartas", "Card Ledge"),
    "block.tcgshop.box_shelf": ("Estantería de cajas", "Booster Box Shelf"),
    "block.tcgshop.card_frame": ("Marco de carta", "Card Frame"),
    "block.tcgshop.home_frame": ("Marco de carta (casa)", "Card Frame (Home)"),
    "block.tcgshop.home_ledge": ("Repisa de cartas (casa)", "Card Ledge (Home)"),
    "block.tcgshop.home_showcase": ("Vitrina torre (casa)", "Showcase Tower (Home)"),
    "block.tcgshop.home_card_wall": ("Muro de cartas (casa)", "Card Wall (Home)"),
    "block.tcgshop.home_pedestal": ("Pedestal con urna (casa)", "Pedestal Case (Home)"),
    "tcgshop.home.not_yours": ("Este expositor no es tuyo.", "This display isn't yours."),
    "tcgshop.home.hint": ("Expositor de casa: clic con una carta, sobre, caja o peluche para exponerlo (no se vende).",
                          "Home display: click with a card, pack, box or plush to show it off (nothing is sold)."),
    "tcgshop.home.hover": ("Clic derecho para retirar", "Right-click to take back"),
    # smell and spray
    "item.tcgshop.deodorant_spray": ("Spray desodorante", "Deodorant Spray"),
    "item.tcgshop.deodorant_spray.tip": ("Clic derecho: rocía. Quita el mal olor de tu tienda y de los clientes apestosos.",
                                         "Right-click to spray. Removes bad smell from your shop and from smelly customers."),
    "tcgshop.smell.sprayed": ("Olor de la tienda: %s%%", "Shop smell: %s%%"),
    "tcgshop.rep.smell": ("Mal olor", "Bad smell"),
    "tcgshop.rep.tip.smell": ("Huele mal: usa el spray desodorante (Mercado del teléfono) por la tienda y sobre los clientes con nubecillas verdes.",
                              "It stinks: use the deodorant spray (phone Market) around the shop and on customers with green clouds."),
    "tcgshop.review.smell": ("Olía fatal", "It smelled awful"),
    # Pokémon duels between NPCs
    "tcgshop.duel_npc.start": ("¡%s y %s se retan a un duelo Pokémon!", "%s and %s start a Pokémon duel!"),
    "tcgshop.duel_npc.send_out": ("¡Adelante, %s!", "Go, %s!"),
    "tcgshop.duel_npc.use": ("¡%s, usa %s!", "%s, use %s!"),
    "tcgshop.duel_npc.miss": ("¡%s esquivó el ataque! (%s%% PS)", "%s dodged the attack! (%s%% HP)"),
    "tcgshop.duel_npc.crit": ("¡Golpe crítico! %s: %s%% PS", "Critical hit! %s: %s%% HP"),
    "tcgshop.duel_npc.hp": ("%s: %s%% PS", "%s: %s%% HP"),
    "tcgshop.duel_npc.fainted": ("¡No, %s!", "No, %s!"),
    "tcgshop.duel_npc.won": ("%s gana el duelo Pokémon contra %s", "%s wins the Pokémon duel against %s"),
    "tcgshop.duel_npc.move.0": ("Placaje", "Tackle"), "tcgshop.duel_npc.move.1": ("Arañazo", "Scratch"),
    "tcgshop.duel_npc.move.2": ("Mordisco", "Bite"), "tcgshop.duel_npc.move.3": ("Golpe Cabeza", "Headbutt"),
    "tcgshop.duel_npc.move.4": ("Ataque Rápido", "Quick Attack"), "tcgshop.duel_npc.move.5": ("Finta", "Feint Attack"),
    "tcgshop.scalper.duel_lost": ("Vale, vale... el barrio es tuyo. Por ahora.", "Fine, fine... the turf is yours. For now."),
    # TCG duel
    "tcgshop.phone.league": ("Liga TCG", "TCG League"),
    "tcgshop.duel.title": ("Duelo TCG", "TCG Duel"),
    "tcgshop.duel.table_busy": ("Ya hay una partida en esta mesa.", "There's already a match at this table."),
    "tcgshop.duel.waiting": ("Te has sentado a jugar. Esperando rival (otro jugador debe hacer clic en la mesa). Mayús + clic: jugar contra la casa.",
                             "You sat down to play. Waiting for an opponent (another player must click the table). Sneak + click: play the house."),
    "tcgshop.duel.waiting_other": ("%s busca rival para una partida de liga: haz clic en su mesa de juego.", "%s is looking for a League opponent: click their play table."),
    "tcgshop.duel.wait_cancel": ("Te has levantado de la mesa.", "You left the table."),
    "tcgshop.duel.house": ("La casa", "The House"),
    "tcgshop.duel.no_deck": ("Necesitas un mazo con al menos %s Pokémon (cartas de tu Binder General o del inventario).",
                             "You need a deck with at least %s Pokémon (cards in your General Binder or inventory)."),
    "tcgshop.duel.reward": ("¡Has ganado a la casa! Premio: %s", "You beat the house! Prize: %s"),
    "tcgshop.duel.result": ("%s gana a %s (%s-%s)", "%s beats %s (%s-%s)"),
    "tcgshop.duel.log.start": ("Empieza %s.", "%s goes first."),
    "tcgshop.duel.log.concede": ("%s se rinde.", "%s concedes."),
    "tcgshop.duel.log.play": ("%s juega %s.", "%s plays %s."),
    "tcgshop.duel.log.trainer": ("%s usa %s.", "%s uses %s."),
    "tcgshop.duel.log.retreat": ("%s se retira y sale %s.", "%s retreats; %s comes in."),
    "tcgshop.duel.log.attack": ("%s usa %s: %s de daño.", "%s uses %s: %s damage."),
    "tcgshop.duel.log.attack_weak": ("%s usa %s: ¡%s de daño, es muy eficaz!", "%s uses %s: %s damage, super effective!"),
    "tcgshop.duel.log.ko": ("¡%s queda K.O.! %s gana %s punto(s).", "%s is knocked out! %s scores %s point(s)."),
    "tcgshop.duel.log.promote": ("%s saca a %s.", "%s sends out %s."),
    "tcgshop.duel.log.win": ("¡%s gana la partida!", "%s wins the match!"),
    "tcgshop.duel.err.over": ("La partida ha terminado.", "The match is over."),
    "tcgshop.duel.err.not_turn": ("No es tu turno.", "It's not your turn."),
    "tcgshop.duel.err.invalid": ("Acción no válida.", "Invalid action."),
    "tcgshop.duel.err.bench_full": ("Tu Banca está llena (máx. 3).", "Your Bench is full (max 3)."),
    "tcgshop.duel.err.no_target": ("No hay objetivo para eso.", "There's no target for that."),
    "tcgshop.duel.err.deck_empty": ("Tu mazo está vacío.", "Your deck is empty."),
    "tcgshop.duel.err.no_bench": ("No tienes Pokémon en la Banca.", "You have no Benched Pokémon."),
    "tcgshop.duel.err.first_turn": ("En el primer turno de la partida no se puede atacar ni unir energía.", "You can't attack or attach energy on the very first turn."),
    "tcgshop.duel.err.energy_used": ("Ya has unido energía este turno.", "You already attached energy this turn."),
    "tcgshop.duel.err.retreated": ("Ya te has retirado este turno.", "You already retreated this turn."),
    "tcgshop.duel.err.no_energy": ("No tiene suficiente energía.", "Not enough energy."),
    "tcgshop.duel.effect.none": ("—", "—"),
    "tcgshop.duel.effect.potion": ("Poción", "Potion"),
    "tcgshop.duel.effect.research": ("Investigación", "Research"),
    "tcgshop.duel.effect.switch": ("Cambio", "Switch"),
    "tcgshop.duel.effect.energy": ("Energía extra", "Extra Energy"),
    "tcgshop.duel.effect_desc.none": ("Sin efecto.", "No effect."),
    "tcgshop.duel.effect_desc.potion": ("Cura 30 de daño a tu Pokémon Activo.", "Heal 30 damage from your Active Pokémon."),
    "tcgshop.duel.effect_desc.research": ("Roba 2 cartas.", "Draw 2 cards."),
    "tcgshop.duel.effect_desc.switch": ("Cambia tu Activo por el Pokémon de la Banca con más PS (sin coste).", "Switch your Active with your healthiest Benched Pokémon (free)."),
    "tcgshop.duel.effect_desc.energy": ("Une 1 energía extra a tu Activo (además de la del turno).", "Attach 1 extra energy to your Active (on top of the turn's)."),
    "tcgshop.duel.trainer": ("Objeto (carta sin Pokémon)", "Item (non-Pokémon card)"),
    "tcgshop.duel.hp_type": ("PS %s/%s · Tipo %s", "HP %s/%s · %s type"),
    "tcgshop.duel.retreat_cost": ("Retirada: %s energía(s)", "Retreat: %s energy"),
    "tcgshop.duel.energy_now": ("Energía unida: %s", "Energy attached: %s"),
    "tcgshop.duel.resist": ("Resistente: recibe 10 menos de daño", "Sturdy: takes 10 less damage"),
    "tcgshop.duel.ex": ("ex: noquearlo da 2 puntos", "ex: knocking it out gives 2 points"),
    "tcgshop.duel.finished": ("Fin de la partida", "Match over"),
    "tcgshop.duel.your_turn": ("¡Tu turno!", "Your turn!"),
    "tcgshop.duel.opp_turn": ("Turno del rival", "Opponent's turn"),
    "tcgshop.duel.ranked": ("Partida de liga", "League match"),
    "tcgshop.duel.casual": ("Amistosa contra la casa", "Friendly vs the house"),
    "tcgshop.duel.energy": ("Unir energía", "Attach energy"),
    "tcgshop.duel.cancel": ("Cancelar", "Cancel"),
    "tcgshop.duel.retreat": ("Retirarse (%s)", "Retreat (%s)"),
    "tcgshop.duel.end_turn": ("Pasar turno", "End turn"),
    "tcgshop.duel.concede": ("Rendirse", "Concede"),
    "tcgshop.duel.rules_short": ("Clic en una carta de tu mano para jugarla. 1 energía por turno. Atacar termina el turno. Gana quien haga 3 puntos (los ex valen 2). Espacio: pasar turno.",
                                 "Click a card in your hand to play it. 1 energy per turn. Attacking ends your turn. First to 3 points wins (ex are worth 2). Space: end turn."),
    "tcgshop.duel.pick_energy": ("Elige a qué Pokémon unir la energía", "Pick a Pokémon to attach energy to"),
    "tcgshop.duel.pick_retreat": ("Elige el Pokémon de la Banca que sale", "Pick the Benched Pokémon to switch in"),
    "tcgshop.duel.esc_hint": ("Esc: salir sin rendirte (haz clic en la mesa para volver)", "Esc: leave without conceding (click the table to come back)"),
    "tcgshop.duel.victory": ("¡VICTORIA!", "VICTORY!"),
    "tcgshop.duel.defeat": ("DERROTA", "DEFEAT"),
    "tcgshop.duel.rating_change": ("Liga: %s pts", "League: %s pts"),
    "tcgshop.duel.close": ("Cerrar", "Close"),
    "tcgshop.duel.deck_hand": ("Mazo %s · Mano %s", "Deck %s · Hand %s"),
    # real TCG (twinleaf.gg engine)
    "tcgshop.ptcg.engine_failed": ("El motor del TCG no se ha podido cargar (mira el registro del servidor).", "The TCG engine failed to load (check the server log)."),
    "tcgshop.ptcg.loading": ("Cargando el motor del TCG... vuelve a hacer clic en unos segundos.", "Loading the TCG engine... click again in a few seconds."),
    "tcgshop.ptcg.start_failed": ("No se pudo empezar la partida: %s", "Could not start the match: %s"),
    "tcgshop.ptcg.no_deck": ("No tienes un mazo válido de 60 cartas. Necesitas Pokémon Básicos jugables en tu Binder General o inventario (la Energía básica es gratis). Móntalo en Teléfono → Liga TCG.",
                             "You don't have a valid 60-card deck. You need playable Basic Pokémon in your General Binder or inventory (Basic Energy is free). Build it in Phone → TCG League."),
    "tcgshop.ptcg.result": ("%s gana la partida de TCG contra %s", "%s wins the TCG match against %s"),
    "tcgshop.ptcg.opp_deciding": ("El rival está decidiendo...", "Your opponent is deciding..."),
    "tcgshop.ptcg.pick_target": ("Elige el Pokémon de destino (clic derecho: cancelar)", "Pick the target Pokémon (right-click: cancel)"),
    "tcgshop.ptcg.deck_count": ("Mazo %s/60", "Deck %s/60"),
    "tcgshop.ptcg.discard": ("Pila de descartes (%s)", "Discard pile (%s)"),
    "tcgshop.ptcg.prizes": ("Premios: %s", "Prizes: %s"),
    "tcgshop.ptcg.retreat": ("Retirarse", "Retreat"),
    "tcgshop.ptcg.use_stadium": ("Usar Estadio", "Use Stadium"),
    "tcgshop.ptcg.concede_sure": ("¿Seguro? Clic otra vez", "Sure? Click again"),
    "tcgshop.ptcg.selected": ("Elegidas: %s (mín. %s, máx. %s)", "Selected: %s (min %s, max %s)"),
    "tcgshop.ptcg.confirm": ("Confirmar", "Confirm"),
    "tcgshop.ptcg.auto": ("Automático", "Automatic"),
    "tcgshop.ptcg.complex_prompt": ("Esta decisión es compleja: pulsa «Automático» para que el motor la resuelva por ti.",
                                    "This decision is complex: press \"Automatic\" to let the engine resolve it for you."),
    "tcgshop.ptcg.draw": ("EMPATE", "DRAW"),
    "tcgshop.ptcg.deck_count_hint": ("", ""),
    "tcgshop.ptcg.click_remove": ("Clic: quitar una", "Click: remove one"),
    "tcgshop.ptcg.click_add": ("Clic: añadir una", "Click: add one"),
    "tcgshop.ptcg.energy_free": ("Energía básica: gratis e ilimitada", "Basic Energy: free and unlimited"),
    "tcgshop.ptcg.deck_empty_hint": ("Tu mazo está vacío. Pulsa «Automático» o añade cartas de la derecha.", "Your deck is empty. Press \"Auto\" or add cards from the right."),
    "tcgshop.ptcg.deck_err.DECK_SIZE": ("Debe tener exactamente 60 cartas", "It must have exactly 60 cards"),
    "tcgshop.ptcg.deck_err.MAX_COPIES": ("Máximo 4 copias por nombre (salvo Energía básica)", "Max 4 copies per name (except Basic Energy)"),
    "tcgshop.ptcg.deck_err.NO_BASIC": ("Necesita al menos un Pokémon Básico", "It needs at least one Basic Pokémon"),
    "tcgshop.ptcg.deck_err.UNKNOWN_CARD": ("Tiene una carta que el motor no conoce", "It has a card the engine doesn't know"),
    "tcgshop.ptcg.deck_err.NOT_OWNED": ("Te faltan copias de alguna carta", "You're missing copies of a card"),
    "tcgshop.ptcg.deck_err.EMPTY": ("Mazo vacío", "Empty deck"),
    "tcgshop.ptcg.rules": (
        "POKÉMON TCG REAL\n\n"
        "Las partidas usan el motor de twinleaf.gg (código abierto, licencia MIT) con las reglas oficiales y los efectos reales de cada carta: "
        "mazo de 60, 6 premios, evolución, Energía por tipo, debilidad/resistencia, condiciones especiales, Partidarios, Estadios y Herramientas.\n\n"
        "• Puedes usar las cartas de tu colección que el motor tiene programadas (unas 6.000). La Energía básica es gratis e ilimitada.\n"
        "• Montas el mazo aquí (o «Automático» con tus mejores líneas evolutivas). Máximo 4 copias por nombre.\n"
        "• En la mesa: clic en una carta de la mano para jugarla; Energía, Herramienta y evolución piden luego el Pokémon de destino. "
        "Clic en tus Pokémon para ver sus habilidades. Los ataques están a la derecha.\n"
        "• Las decisiones salen en un panel; «Automático» deja que el motor elija por ti.\n"
        "• Liga contra jugadores (ELO) o contra la casa (premio en CobbleDollars).",
        "REAL POKÉMON TCG\n\n"
        "Matches use the twinleaf.gg engine (open source, MIT licence) with the official rules and each card's real effects: "
        "60-card decks, 6 prizes, evolution, typed Energy, weakness/resistance, Special Conditions, Supporters, Stadiums and Tools.\n\n"
        "• You can use the cards in your collection that the engine implements (about 6,000). Basic Energy is free and unlimited.\n"
        "• Build your deck here (or \"Auto\" from your best evolution lines). Max 4 copies per name.\n"
        "• At the table: click a hand card to play it; Energy, Tools and evolutions then ask for the target Pokémon. "
        "Click your Pokémon to see their abilities. Attacks are on the right.\n"
        "• Decisions show up in a panel; \"Automatic\" lets the engine choose for you.\n"
        "• League against players (ELO) or against the house (CobbleDollars prize)."),
    # league
    "tcgshop.league.tab.rank": ("Clasificación", "Ranking"),
    "tcgshop.league.tab.deck": ("Mi mazo", "My deck"),
    "tcgshop.league.tab.rules": ("Reglas", "Rules"),
    "tcgshop.league.loading": ("Cargando...", "Loading..."),
    "tcgshop.league.rank.0": ("Bronce", "Bronze"), "tcgshop.league.rank.1": ("Plata", "Silver"), "tcgshop.league.rank.2": ("Oro", "Gold"),
    "tcgshop.league.rank.3": ("Platino", "Platinum"), "tcgshop.league.rank.4": ("Diamante", "Diamond"), "tcgshop.league.rank.5": ("Maestro", "Master"),
    "tcgshop.league.wl": ("%s V · %s D", "%s W · %s L"),
    "tcgshop.league.winrate": ("%s%% de victorias", "%s%% win rate"),
    "tcgshop.league.ai_wins": ("%s victorias contra la casa", "%s wins vs the house"),
    "tcgshop.league.next": ("%s pts para subir", "%s pts to rank up"),
    "tcgshop.league.top": ("Mejores jugadores", "Top players"),
    "tcgshop.league.no_top": ("Aún nadie ha jugado partidas de liga en este mundo.", "Nobody has played League matches in this world yet."),
    "tcgshop.league.how": ("Juega en una mesa de juego: clic para esperar rival (liga) · Mayús + clic: contra la casa.",
                           "Play at a play table: click to wait for an opponent (League) · Sneak + click: vs the house."),
    "tcgshop.league.deck_count": ("Mazo %s/%s · %s Pokémon · %s Objetos", "Deck %s/%s · %s Pokémon · %s Items"),
    "tcgshop.league.auto": ("Automático", "Auto"),
    "tcgshop.league.save": ("Guardar", "Save"),
    "tcgshop.league.pool_hint": ("Tus cartas (clic para añadir · clic en el mazo para quitar):", "Your cards (click to add · click the deck to remove):"),
    "tcgshop.league.no_pool": ("No tienes cartas. Abre sobres y guárdalas en tu Binder General.", "You have no cards. Open packs and keep them in your General Binder."),
    "tcgshop.league.saved": ("Mazo guardado (%s cartas).", "Deck saved (%s cards)."),
    "tcgshop.league.err.too_many": ("El mazo tiene más de 20 cartas.", "The deck has more than 20 cards."),
    "tcgshop.league.err.invalid": ("El mazo tiene una carta no válida.", "The deck has an invalid card."),
    "tcgshop.league.err.copies": ("Máximo 2 copias de cada carta.", "Max 2 copies of each card."),
    "tcgshop.league.err.not_owned": ("No tienes suficientes copias de alguna carta.", "You don't own enough copies of a card."),
    "tcgshop.league.err.few_pokemon": ("El mazo necesita al menos %s Pokémon.", "The deck needs at least %s Pokémon."),
    "tcgshop.league.err.trainers": ("Máximo 6 Objetos por mazo.", "Max 6 Items per deck."),
    "tcgshop.league.rules": (
        "MODO COMPETITIVO TCG\n\n"
        "• Mazo de hasta 20 cartas de tu colección (máx. 2 copias, mínimo 6 Pokémon, máximo 6 Objetos). Si no guardas uno, se monta solo con tus mejores cartas.\n"
        "• Cada Pokémon tiene PS, tipo y dos ataques según su especie y rareza. Las cartas sin Pokémon son Objetos: Poción, Investigación, Cambio o Energía extra.\n"
        "• Empiezas con 5 cartas y tu Pokémon más fuerte de Activo. Cada turno robas 1, puedes bajar Pokémon a la Banca (máx. 3), usar Objetos, unir 1 energía y retirarte pagando su coste.\n"
        "• Atacar termina el turno. Si el tipo del atacante es eficaz contra el defensor, hace +20.\n"
        "• Noquear da 1 punto (2 si es ex: Doble Rara o superior). Gana quien llegue a 3 o deje al rival sin Pokémon.\n"
        "• Partidas de liga entre jugadores: ganas o pierdes puntos ELO y subes de rango (Bronce → Maestro). Contra la casa: premio en CobbleDollars.",
        "COMPETITIVE TCG MODE\n\n"
        "• Deck of up to 20 cards from your collection (max 2 copies, at least 6 Pokémon, at most 6 Items). If you don't save one, it's built from your best cards.\n"
        "• Every Pokémon has HP, a type and two attacks based on its species and rarity. Non-Pokémon cards are Items: Potion, Research, Switch or Extra Energy.\n"
        "• You start with 5 cards and your strongest Pokémon as Active. Each turn draw 1, bench Pokémon (max 3), use Items, attach 1 energy and retreat by paying its cost.\n"
        "• Attacking ends your turn. If the attacker's type is super effective against the defender, it deals +20.\n"
        "• A knockout scores 1 point (2 for ex: Double Rare or higher). First to 3, or leaving the rival without Pokémon, wins.\n"
        "• League matches between players: win or lose ELO points and climb ranks (Bronze → Master). Against the house: a CobbleDollars prize."),
}


def main():
    es, en = {}, {}
    for k, (a, b) in PAIRS.items():
        es[k], en[k] = a, b
    for t, (a, b) in TYPES.items():
        es[f"tcgshop.duel.type.{t}"], en[f"tcgshop.duel.type.{t}"] = a, b
    for t, mv in MOVES.items():
        for i, (a, b) in enumerate(mv, start=1):
            es[f"tcgshop.duel.move.{t}.{i}"], en[f"tcgshop.duel.move.{t}.{i}"] = a, b
    for name, add in (("es_es.json", es), ("en_us.json", en)):
        p = LANG / name
        d = json.loads(p.read_text(encoding="utf-8"))
        d.update(add)
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
    print("duel / league / extras texts:", len(es))
    import gen_lang_ptcg_ui  # its texts (rules, deck reasons) take priority
    gen_lang_ptcg_ui.main()


if __name__ == "__main__":
    main()
