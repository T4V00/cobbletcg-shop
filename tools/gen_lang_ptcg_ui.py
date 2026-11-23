#!/usr/bin/env python3
"""Texts for the real TCG modern table, the phone's Play tab and the invalid deck reasons (es / en)."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

U = "tcgshop.ptcg.ui."
PAIRS = {
    # --- table
    U + "actions": ("Acciones", "Actions"),
    U + "attacks": ("Ataques", "Attacks"),
    U + "your_turn": ("Tu turno", "Your turn"),
    U + "opp_turn": ("Turno del rival", "Opponent's turn"),
    U + "finished": ("Partida terminada", "Game over"),
    U + "turn_n": ("Turno %s", "Turn %s"),
    U + "hand_n": ("Mano: %s", "Hand: %s"),
    U + "deck_n": ("Mazo: %s", "Deck: %s"),
    U + "deck": ("Mazo", "Deck"),
    U + "discard": ("Descartes", "Discard"),
    U + "discard_list": ("Pila de descartes (%s)", "Discard pile (%s)"),
    U + "deck_count": ("Quedan %s cartas en el mazo · %s en la mano", "%s cards left in the deck · %s in hand"),
    U + "lost_count": ("Zona Perdida: %s", "Lost Zone: %s"),
    U + "prizes": ("Premios: %s", "Prizes: %s"),
    U + "prizes_hint": ("Quedan %s cartas de Premio. Coge la última y ganas.", "%s Prize cards left. Take the last one to win."),
    U + "prize_taken": ("+%s premio", "+%s prize"),
    U + "supporter": ("Partidario", "Supporter"),
    U + "stadium": ("Estadio", "Stadium"),
    U + "use_stadium": ("Usar estadio", "Use stadium"),
    U + "retreat": ("Retirarse", "Retreat"),
    U + "cancel": ("Cancelar", "Cancel"),
    U + "suggest": ("Sugerencia (S)", "Hint (S)"),
    U + "thinking": ("Pensando…", "Thinking…"),
    U + "suggest_none": ("La IA no ve ninguna jugada clara: puedes pasar el turno.", "The AI sees no clear play: you can end your turn."),
    U + "suggest_play": ("Juega %s %s.", "Play %s %s."),
    U + "on_board": ("", ""),
    U + "to_bench": ("en tu Banca", "to your Bench"),
    U + "on_mon": ("sobre %s", "on %s"),
    U + "suggest_attack": ("Ataca con %s.", "Attack with %s."),
    U + "suggest_retreat": ("Retírate y saca a %s.", "Retreat and bring out %s."),
    U + "suggest_ability": ("Usa la habilidad %s.", "Use the ability %s."),
    U + "suggest_stadium": ("Usa el Estadio en juego.", "Use the Stadium in play."),
    U + "suggest_pass": ("Ya no hay nada útil: pasa el turno.", "Nothing useful left: end your turn."),
    U + "end_turn": ("Pasar turno", "End turn"),
    U + "space_hint": ("Espacio", "Space"),
    U + "concede": ("Rendirse", "Concede"),
    U + "concede_sure": ("¿Seguro? Clic otra vez para rendirte", "Sure? Click again to concede"),
    U + "log": ("Registro", "Log"),
    U + "hp": ("PS", "HP"),
    U + "hp_of": ("%s/%s PS", "%s/%s HP"),
    U + "active": ("Activo", "Active"),
    U + "bench": ("Banca", "Bench"),
    U + "yours": ("Tuyo", "Yours"),
    U + "rival": ("Rival", "Rival"),
    U + "no_energy": ("falta energía", "need energy"),
    U + "pick_target": ("Elige el destino iluminado", "Pick a glowing target"),
    U + "pick_retreat": ("Elige quién sale de la Banca", "Pick who comes in from the Bench"),
    U + "opp_thinking": ("El rival está pensando", "Opponent is thinking"),
    U + "ai_thinking": ("La IA está pensando", "The AI is thinking"),
    U + "drop_play": ("Suelta aquí para jugarla", "Drop here to play it"),
    U + "click_play": ("Clic aquí para jugarla", "Click here to play it"),
    U + "drop_on_glow": ("Suelta la carta sobre un destino iluminado.", "Drop the card on a glowing target."),
    U + "no_target": ("Esa carta no tiene ahora ningún destino válido.", "That card has no valid target right now."),
    U + "evolved": ("¡Evoluciona!", "Evolved!"),
    U + "uses_attack": ("%s ataca", "%s attacks"),
    U + "uses_ability": ("%s usa una habilidad", "%s uses an ability"),
    U + "coin_heads": ("¡Cara!", "Heads!"),
    U + "coin_tails": ("Cruz", "Tails"),
    U + "coin_h_short": ("C", "H"),
    U + "coin_t_short": ("X", "T"),
    U + "weak": ("Debilidad", "Weakness"),
    U + "resist": ("Resistencia", "Resistance"),
    U + "retreat_n": ("Retirada %s", "Retreat %s"),
    U + "evolves_from": ("Evoluciona de %s", "Evolves from %s"),
    U + "energy": ("Energía", "Energy"),
    U + "victory": ("Victoria", "Victory"),
    U + "defeat": ("Derrota", "Defeat"),
    U + "draw": ("Empate", "Draw"),
    U + "rating_change": ("Liga: %s pts", "League: %s pts"),
    U + "reward": ("Premio: $%s CobbleDollars", "Prize: $%s CobbleDollars"),
    U + "rematch": ("Revancha", "Rematch"),
    U + "close": ("Cerrar", "Close"),
    # --- decision panels
    U + "confirm": ("Confirmar", "Confirm"),
    U + "reset": ("Deshacer", "Undo"),
    U + "auto": ("Automático", "Auto"),
    U + "yes": ("Sí", "Yes"),
    U + "no": ("No", "No"),
    U + "see_board": ("Ver la mesa (Tab)", "See board (Tab)"),
    U + "back_to_choice": ("Volver a la decisión (Tab)", "Back to the choice (Tab)"),
    U + "pick_n": ("Elige entre %s y %s · elegidas: %s", "Pick %s to %s · picked: %s"),
    U + "pick_prizes": ("Coge %s carta(s) de Premio", "Take %s Prize card(s)"),
    U + "attach_hint": ("Clic en una Energía y luego en el Pokémon · unidas %s (mín. %s, máx. %s)",
                        "Click an Energy, then a Pokémon · attached %s (min %s, max %s)"),
    U + "attach_pick_energy": ("Primero elige una Energía de arriba.", "Pick an Energy from the top first."),
    U + "move_energy_hint": ("Clic en una Energía de un Pokémon y luego en el Pokémon de destino · movidas: %s",
                             "Click an Energy on a Pokémon, then the target Pokémon · moved: %s"),
    U + "move_pick_energy": ("Primero pulsa una Energía (los círculos bajo cada Pokémon).", "First click an Energy (the circles under each Pokémon)."),
    U + "discard_energy_hint": ("Pulsa las Energías que quieres descartar · %s (mín. %s, máx. %s)",
                                "Click the Energies to discard · %s (min %s, max %s)"),
    U + "choose_energy_hint": ("Elige con qué Energías pagar · elegidas: %s", "Choose which Energies pay · picked: %s"),
    U + "cost": ("Coste:", "Cost:"),
    U + "put_damage_hint": ("Clic en un Pokémon para poner contadores · %s de %s", "Click a Pokémon to place counters · %s of %s"),
    U + "damage_all_placed": ("Ya has colocado todo el daño.", "All damage is already placed."),
    U + "move_damage_hint": ("Clic en el Pokémon con daño y luego en el de destino · movido: %s", "Click the damaged Pokémon, then the target · moved: %s"),
    U + "remove_damage_hint": ("Clic en un Pokémon para quitarle 10 de daño · quitado: %s", "Click a Pokémon to remove 10 damage · removed: %s"),
    U + "no_damage_here": ("Ese Pokémon no tiene daño que mover.", "That Pokémon has no damage to move."),
    U + "order_hint": ("Pulsa las cartas en el orden en que quieres dejarlas · %s/%s", "Click the cards in the order you want · %s/%s"),
    U + "max_reached": ("Máximo %s.", "Maximum %s."),
    U + "same_target": ("Todas deben ir al mismo Pokémon.", "They must all go to the same Pokémon."),
    U + "different_targets": ("Cada una debe ir a un Pokémon distinto.", "Each must go to a different Pokémon."),
    U + "unknown_prompt": ("Esta decisión todavía no tiene panel propio: pulsa «Automático» y el motor la resolverá con las reglas.",
                           "This choice has no panel yet: press “Auto” and the engine will resolve it by the rules."),
    U + "prompt.cards": ("Elige cartas", "Choose cards"),
    U + "prompt.pokemon": ("Elige un Pokémon", "Choose a Pokémon"),
    U + "prompt.prize": ("Coge tus premios", "Take your prizes"),
    U + "prompt.confirm": ("¿Confirmas?", "Confirm?"),
    U + "prompt.select": ("Elige una opción", "Choose an option"),
    U + "prompt.ok": ("Información", "Information"),
    U + "prompt.attach": ("Une Energías", "Attach Energy"),
    U + "prompt.moveEnergy": ("Mueve Energías", "Move Energy"),
    U + "prompt.discardEnergy": ("Descarta Energías", "Discard Energy"),
    U + "prompt.chooseEnergy": ("Paga el coste", "Pay the cost"),
    U + "prompt.putDamage": ("Pon contadores de daño", "Place damage counters"),
    U + "prompt.moveDamage": ("Mueve contadores de daño", "Move damage counters"),
    U + "prompt.removeDamage": ("Quita contadores de daño", "Remove damage counters"),
    U + "prompt.order": ("Ordena las cartas", "Order the cards"),
    U + "prompt.chooseAttack": ("Elige un ataque", "Choose an attack"),
    U + "prompt.other": ("Decisión", "Choice"),
    # --- modes and levels
    U + "mode.table_pvp": ("Liga · mesa", "League · table"),
    U + "mode.queue_pvp": ("Liga · online", "League · online"),
    U + "mode.casual_ai": ("Amistosa vs IA", "Casual vs AI"),
    U + "mode.ranked_ai": ("Liga vs IA", "League vs AI"),
    U + "level.0": ("Fácil", "Easy"),
    U + "level.1": ("Normal", "Normal"),
    U + "level.2": ("Difícil", "Hard"),
    U + "level.3": ("Campeón", "Champion"),
    "tcgshop.ptcg.ai_name.easy": ("IA Novata", "Rookie AI"),
    "tcgshop.ptcg.ai_name.normal": ("IA Entrenadora", "Trainer AI"),
    "tcgshop.ptcg.ai_name.hard": ("IA Veterana", "Veteran AI"),
    "tcgshop.ptcg.ai_name.champion": ("IA Campeona", "Champion AI"),
    # --- server
    "tcgshop.ptcg.deck_invalid": ("Tu mazo guardado no es legal: %s.", "Your saved deck isn't legal: %s."),
    "tcgshop.ptcg.deck_fix_hint": ("Arréglalo en el teléfono → Liga TCG → Mazo (o pulsa «Automático»).",
                                   "Fix it on the phone → TCG League → Deck (or press “Auto”)."),
    "tcgshop.ptcg.using_auto_deck": ("No tienes mazo guardado: juegas con uno automático de tus cartas.", "No saved deck: playing an automatic one from your cards."),
    "tcgshop.ptcg.opp_no_deck": ("%s no tiene un mazo válido.", "%s doesn't have a valid deck."),
    "tcgshop.ptcg.match_found": ("¡Rival encontrado: %s!", "Opponent found: %s!"),
    "tcgshop.ptcg.queue_joined": ("Buscando rival… (%s en cola)", "Looking for an opponent… (%s queued)"),
    # --- invalid deck reasons
    "tcgshop.ptcg.deck_err.DECK_SIZE": ("debe tener exactamente 60 cartas (tiene %s)", "it must have exactly 60 cards (it has %s)"),
    "tcgshop.ptcg.deck_err.MAX_COPIES": ("más de 4 copias de %s", "more than 4 copies of %s"),
    "tcgshop.ptcg.deck_err.NO_BASIC": ("necesita al menos un Pokémon Básico", "it needs at least one Basic Pokémon"),
    "tcgshop.ptcg.deck_err.UNKNOWN_CARD": ("el motor no conoce la carta %s", "the engine doesn't know the card %s"),
    "tcgshop.ptcg.deck_err.NOT_OWNED": ("te faltan copias de %s (Binder General + inventario)", "you're missing copies of %s (General Binder + inventory)"),
    "tcgshop.ptcg.deck_err.EMPTY": ("no hay mazo guardado", "there's no saved deck"),
    "tcgshop.ptcg.deck_err.NO_CARDS": ("no tienes cartas jugables en tu colección", "you have no playable cards in your collection"),
    "tcgshop.ptcg.deck_err.ACE_SPEC": ("solo se permite 1 carta AS TÁCTICO (sobra %s)", "only 1 ACE SPEC card is allowed (%s is extra)"),
    "tcgshop.ptcg.deck_err.RADIANT": ("solo se permite 1 Pokémon Radiante (sobra %s)", "only 1 Radiant Pokémon is allowed (%s is extra)"),
    "tcgshop.ptcg.deck_err.STAR": ("solo se permite 1 carta Estrella (sobra %s)", "only 1 Star card is allowed (%s is extra)"),
    "tcgshop.ptcg.deck_err.PRISM_STAR": ("solo 1 copia de cada carta Prisma (%s)", "only 1 copy of each Prism Star card (%s)"),
    "tcgshop.ptcg.deck_err.PROFESSOR_COMBO": ("no se pueden mezclar Profesor Sycamore, Profesora Juniper e Investigación de Profesores",
                                              "Professor Sycamore, Professor Juniper and Professor's Research can't be mixed"),
    "tcgshop.ptcg.deck_err.BOSS_COMBO": ("no se pueden llevar a la vez Lysson y Órdenes de Jefe", "Lysandre and Boss's Orders can't be used together"),
    "tcgshop.ptcg.deck_err.ENGINE_RULES": ("no cumple las reglas del formato (revisa cartas especiales)", "it breaks the format rules (check special cards)"),
    # --- phone: Play tab
    "tcgshop.league.tab.play": ("Jugar", "Play"),
    "tcgshop.league.check": ("Comprobar", "Check"),
    "tcgshop.league.play.deck_ok": ("Mazo legal", "Deck is legal"),
    "tcgshop.league.play.deck_ok_hint": ("%s cartas, reglas oficiales. ¡Listo para jugar!", "%s cards, official rules. Ready to play!"),
    "tcgshop.league.play.deck_auto": ("Mazo automático", "Automatic deck"),
    "tcgshop.league.play.deck_auto_hint": ("No has guardado un mazo: se montará uno legal con tus mejores cartas al empezar.",
                                           "You haven't saved a deck: a legal one is built from your best cards when you start."),
    "tcgshop.league.play.deck_none": ("Sin cartas", "No cards"),
    "tcgshop.league.play.deck_bad": ("Mazo no válido", "Invalid deck"),
    "tcgshop.league.play.saved": ("Mazo guardado y validado", "Deck saved and validated"),
    "tcgshop.league.play.edit_deck": ("Editar mazo", "Edit deck"),
    "tcgshop.league.play.in_match": ("Tienes una partida en curso", "You have a match in progress"),
    "tcgshop.league.play.resume": ("Volver a la mesa", "Back to the table"),
    "tcgshop.league.play.vs_ai": ("Contra la IA", "Against the AI"),
    "tcgshop.league.play.ai_rating": ("%s pts", "%s pts"),
    "tcgshop.league.play.mode.casual": ("Amistosa", "Casual"),
    "tcgshop.league.play.mode.casual.sub": ("Sin puntos · premio al ganar", "No points · prize if you win"),
    "tcgshop.league.play.mode.ranked_ai": ("Liga", "League"),
    "tcgshop.league.play.mode.ranked_ai.sub": ("Te juegas puntos de rango", "Ranking points at stake"),
    "tcgshop.league.play.start": ("Clic para empezar", "Click to start"),
    "tcgshop.league.play.vs_players": ("Contra jugadores (liga)", "Against players (league)"),
    "tcgshop.league.play.queue_hint": ("Entra en la cola: cuando otro jugador busque partida, empezará en vuestras pantallas.",
                                       "Join the queue: when another player searches, the match starts on both screens."),
    "tcgshop.league.play.find": ("Buscar rival", "Find opponent"),
    "tcgshop.league.play.searching": ("Buscando rival…", "Searching…"),
    "tcgshop.league.play.queue_size": ("%s jugador(es) en cola", "%s player(s) queued"),
    "tcgshop.league.play.cancel": ("Cancelar", "Cancel"),
    # --- updated rules
    "tcgshop.ptcg.rules": (
        "POKÉMON TCG REAL\n\n"
        "Las partidas usan el motor de twinleaf.gg (código abierto, licencia MIT) con las reglas oficiales y los efectos reales de cada carta: "
        "mazo de 60, 6 premios, evolución, Energía por tipo, debilidad/resistencia, condiciones especiales, Partidarios, Estadios y Herramientas.\n\n"
        "• Jugar: desde el teléfono (Liga TCG → Jugar) contra la IA en 4 niveles, en Liga contra la IA (te juegas puntos) o buscando rival online. "
        "También en una Mesa de juego (clic = esperar rival, Mayús + clic = contra la IA).\n"
        "• Antes de cada partida se comprueba tu mazo: 60 cartas, máximo 4 por nombre, 1 AS TÁCTICO, 1 Radiante, combinaciones prohibidas y que tengas las copias.\n"
        "• En la mesa: arrastra cartas de la mano a su destino (se ilumina). Arrastra tu Activo a la Banca para retirarte. Clic en tus Pokémon para ver habilidades.\n"
        "• «Sugerencia» (tecla S) te marca la jugada que haría la IA. Espacio = pasar turno. Tab = ver la mesa durante una decisión.\n"
        "• Las decisiones (elegir cartas, unir/mover Energía, poner daño...) tienen su panel; «Automático» deja que el motor elija por ti.",
        "REAL POKÉMON TCG\n\n"
        "Matches use the twinleaf.gg engine (open source, MIT licence) with the official rules and each card's real effects: "
        "60-card decks, 6 prizes, evolution, typed Energy, weakness/resistance, special conditions, Supporters, Stadiums and Tools.\n\n"
        "• Play: from the phone (TCG League → Play) against the AI on 4 levels, League vs AI (points at stake) or find an online opponent. "
        "Also at a Play Table (click = wait for an opponent, Shift + click = vs the AI).\n"
        "• Your deck is checked before every match: 60 cards, max 4 per name, 1 ACE SPEC, 1 Radiant, banned combos and that you own the copies.\n"
        "• At the table: drag cards from your hand onto their target (it glows). Drag your Active onto the Bench to retreat. Click your Pokémon to see abilities.\n"
        "• “Hint” (S key) marks the play the AI would make. Space = end turn. Tab = look at the board during a choice.\n"
        "• Every choice (pick cards, attach/move Energy, place damage...) has its own panel; “Auto” lets the engine choose for you."),
}

EXTRA = {
    # --- clear table: zoom, detailed log, challenges
    U + "tool": ("Herramienta", "Tool"),
    U + "attached": ("Energía unida:", "Attached Energy:"),
    U + "use": ("Usar", "Use"),
    U + "play_card": ("Jugar esta carta", "Play this card"),
    U + "choose_target": ("Elegir destino", "Choose a target"),
    U + "zoom_close": ("Clic fuera o Esc para cerrar", "Click outside or Esc to close"),
    U + "mode.challenge": ("Desafío amistoso", "Friendly challenge"),
    U + "log_damage": ("%s (%s) recibe %s de daño · le quedan %s/%s PS", "%s (%s) takes %s damage · %s/%s HP left"),
    U + "log_heal": ("%s (%s) se cura %s · %s/%s PS", "%s (%s) heals %s · %s/%s HP"),
    U + "log_energy_lost": ("%s (%s) pierde %s Energía(s)", "%s (%s) loses %s Energy"),
    U + "log_cond_on": ("%s queda %s", "%s is now %s"),
    U + "log_cond_off": ("%s ya no está %s", "%s is no longer %s"),
    U + "log_new_active": ("%s saca a %s como Pokémon Activo", "%s sends out %s as the Active Pokémon"),
    U + "log_prizes": ("%s coge %s carta(s) de Premio · le quedan %s", "%s takes %s Prize card(s) · %s left"),
    U + "log_draw": ("%s roba %s carta(s) · quedan %s en su mazo", "%s draws %s card(s) · %s left in the deck"),
    U + "log_discard": ("%s descarta %s cartas", "%s discards %s cards"),
    U + "log_stadium": ("Nuevo Estadio en juego: %s", "New Stadium in play: %s"),
    "tcgshop.ptcg.challenge.wheel": ("Desafío TCG", "TCG challenge"),
    "tcgshop.ptcg.challenge.sent": ("Has retado a %s a una partida del TCG. Tiene 60 s para aceptar.", "You challenged %s to a TCG match. They have 60 s to accept."),
    "tcgshop.ptcg.challenge.received": ("¡%s te reta a una partida amistosa del Pokémon TCG!", "%s challenges you to a friendly Pokémon TCG match!"),
    "tcgshop.ptcg.challenge.accept": ("[Aceptar]", "[Accept]"),
    "tcgshop.ptcg.challenge.accept_hint": ("Se comprobará tu mazo y empezará la partida", "Your deck will be checked and the match will start"),
    "tcgshop.ptcg.challenge.decline": ("[Rechazar]", "[Decline]"),
    "tcgshop.ptcg.challenge.none": ("No tienes ningún desafío pendiente.", "You have no pending challenge."),
    "tcgshop.ptcg.challenge.declined": ("%s ha rechazado tu desafío.", "%s declined your challenge."),
    "tcgshop.ptcg.challenge.you_declined": ("Desafío rechazado.", "Challenge declined."),
    "tcgshop.ptcg.challenge.accepted": ("¡%s acepta el desafío!", "%s accepts the challenge!"),
    "tcgshop.ptcg.challenge.busy": ("%s ya está jugando una partida.", "%s is already in a match."),
    "tcgshop.ptcg.challenge.busy_self": ("Ya estás en una partida.", "You're already in a match."),
    "tcgshop.ptcg.rules": (
        "POKÉMON TCG REAL\n\n"
        "Las partidas usan el motor de twinleaf.gg (código abierto, licencia MIT) con las reglas oficiales y los efectos reales de cada carta: "
        "mazo de 60, 6 premios, evolución, Energía por tipo, debilidad/resistencia, condiciones especiales, Partidarios, Estadios y Herramientas.\n\n"
        "• Jugar: desde el teléfono (Liga TCG → Jugar) contra la IA en 4 niveles, Liga contra la IA (te juegas puntos) o buscando rival online. "
        "Reta a otro jugador desde la rueda de Cobblemon («Desafío TCG») o con /tcgduel <jugador>. También en una Mesa de juego.\n"
        "• Antes de cada partida se comprueba tu mazo: 60 cartas, máximo 4 por nombre, 1 AS TÁCTICO, 1 Radiante, combinaciones prohibidas y que tengas las copias.\n"
        "• En la mesa: clic en cualquier carta para ampliarla con sus ataques y habilidades (desde ahí atacas, usas habilidades o te retiras). "
        "Arrastra cartas de la mano a su destino iluminado; arrastra tu Activo a la Banca para retirarte.\n"
        "• ? (tecla S) = pista de la IA · ☰ (tecla L) = registro detallado y rendirse · Espacio = pasar turno · Tab = ver la mesa durante una decisión.",
        "REAL POKÉMON TCG\n\n"
        "Matches use the twinleaf.gg engine (open source, MIT licence) with the official rules and each card's real effects: "
        "60-card decks, 6 prizes, evolution, typed Energy, weakness/resistance, special conditions, Supporters, Stadiums and Tools.\n\n"
        "• Play: from the phone (TCG League → Play) against the AI on 4 levels, League vs AI (points at stake) or find an online opponent. "
        "Challenge another player from the Cobblemon wheel (“TCG challenge”) or with /tcgduel <player>. Also at a Play Table.\n"
        "• Your deck is checked before every match: 60 cards, max 4 per name, 1 ACE SPEC, 1 Radiant, banned combos and that you own the copies.\n"
        "• At the table: click any card to zoom in with its attacks and abilities (attack, use abilities or retreat from there). "
        "Drag cards from your hand onto their glowing target; drag your Active onto the Bench to retreat.\n"
        "• ? (S key) = AI hint · ☰ (L key) = detailed log and concede · Space = end turn · Tab = look at the board during a choice."),
}
EXTRA.update({
    U + "you_attack": ("¡Atacas!", "You attack!"),
    U + "opp_attacks": ("¡%s te ataca!", "%s attacks you!"),
    "tcgshop.league.deck.unsaved": ("Cambios sin guardar", "Unsaved changes"),
    "tcgshop.league.deck.clear": ("Vaciar", "Clear"),
    "tcgshop.league.deck.filter.all": ("Todas", "All"),
    "tcgshop.league.deck.filter.pokemon": ("Pokémon", "Pokémon"),
    "tcgshop.league.deck.filter.trainer": ("Entrenador", "Trainer"),
    "tcgshop.league.deck.filter.energy": ("Energía", "Energy"),
    "tcgshop.league.deck.split": ("%s Pokémon · %s Entrenador · %s Energía", "%s Pokémon · %s Trainer · %s Energy"),
    U + "no_evolve_target": ("No tienes en juego a %s para evolucionarlo", "You don't have %s in play to evolve"),
    U + "ready_attack": ("¡Listo!", "Ready!"),
    "tcgshop.ptcg.msg.BENCH_IS_FULL": ("Tu Banca está llena.", "Your Bench is full."),
    "tcgshop.ptcg.penalty": ("Derrota competitiva: pierdes %s CobbleDollars.", "Competitive defeat: you lose %s CobbleDollars."),
    U + "penalty": ("Pierdes $%s", "You lose $%s"),
    "tcgshop.league.play.penalty_hint": ("Perder en Liga cuesta dinero", "Losing in League costs money"),
    U + "opp_plays": ("%s juega", "%s plays"),
    U + "feed_title": ("Jugadas", "Plays"),
    U + "feed_more": ("Clic: registro completo", "Click: full log"),
    U + "log_count": ("%s entradas", "%s entries"),
    U + "coin_title": ("¿Quién empieza?", "Who goes first?"),
    U + "coin_flipper": ("%s lanza la moneda…", "%s flips the coin…"),
    U + "coin_you_choose": ("¡Tú eliges quién empieza!", "You choose who goes first!"),
    U + "coin_choose": ("%s elige quién empieza", "%s chooses who goes first"),
    U + "intro_skip": ("Clic para saltar", "Click to skip"),
    U + "discard_title": ("Pila de descartes", "Discard pile"),
    U + "discard_open": ("Clic: ver toda la pila", "Click: view the whole pile"),
    U + "discard_empty": ("La pila está vacía", "The pile is empty"),
    U + "you": ("Tú", "You"),
})
EXTRA["tcgshop.ptcg.rules"] = (EXTRA["tcgshop.ptcg.rules"][0].replace(
    "• Antes de cada partida", "• Las partidas de Liga (contra la IA o jugadores) dan puntos de rango; perderlas cuesta un poco de dinero. "
    "La IA Veterana y la Campeona juegan mazos top del metajuego.\n• Antes de cada partida"),
    EXTRA["tcgshop.ptcg.rules"][1].replace(
    "• Your deck is checked", "• League matches (vs the AI or players) give rank points; losing them costs a little money. "
    "The Veteran and Champion AIs play top meta decks.\n• Your deck is checked"))
PAIRS.update(EXTRA)

TYPES = {1: ("Planta", "Grass"), 2: ("Fuego", "Fire"), 3: ("Agua", "Water"), 4: ("Rayo", "Lightning"), 5: ("Psíquico", "Psychic"),
         6: ("Lucha", "Fighting"), 7: ("Oscuridad", "Darkness"), 8: ("Metálico", "Metal"), 9: ("Incoloro", "Colorless"), 10: ("Hada", "Fairy"),
         11: ("Dragón", "Dragon")}
STAGES = {1: ("Restaurado", "Restored"), 2: ("Básico", "Basic"), 3: ("Fase 1", "Stage 1"), 4: ("Fase 2", "Stage 2")}
TRAINERS = {0: ("Objeto", "Item"), 1: ("Partidario", "Supporter"), 2: ("Estadio", "Stadium"), 3: ("Herramienta", "Tool")}
CONDS = {0: ("Paralizado", "Paralyzed"), 1: ("Confundido", "Confused"), 2: ("Dormido", "Asleep"), 3: ("Envenenado", "Poisoned"), 4: ("Quemado", "Burned")}


def main():
    es, en = {}, {}
    for k, (a, b) in PAIRS.items():
        es[k], en[k] = a, b
    for prefix, table in (("type", TYPES), ("stage", STAGES), ("trainer", TRAINERS), ("cond", CONDS)):
        for n, (a, b) in table.items():
            es[f"{U}{prefix}.{n}"], en[f"{U}{prefix}.{n}"] = a, b
    for name, add in (("es_es.json", es), ("en_us.json", en)):
        p = LANG / name
        d = json.loads(p.read_text(encoding="utf-8"))
        d.update(add)
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
    print("table / play / deck texts:", len(es))


if __name__ == "__main__":
    main()
