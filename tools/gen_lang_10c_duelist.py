"""Texts for the 1.0 Wandering Duelist (world/duelist/WanderingDuelists, entity/WanderingDuelistEntity,
client/screen/DuelistStakesScreen).

Usage: python tools/gen_lang_10c_duelist.py  (updates/inserts the keys in es_es.json and en_us.json)

The number of NPC lines per category (tcgshop.duelist.line.<cat>.<n>) must match WanderingDuelists.LINES.
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    "entity.tcgshop.wandering_duelist": ("Duelista errante", "Wandering Duelist"),
    "tcgshop.role.duelist.trader": ("Duelista profesional", "Pro Duelist"),
    "tcgshop.msg.sender.duelist": ("Jugador profesional", "Pro player"),

    # ------------------------------------------------------------------ chat / action bar
    "tcgshop.duelist.arrived": ("Alguien está montando una mesa de cartas por aquí cerca... un jugador profesional busca rival.",
                                "Someone is setting up a card table nearby... a pro player is looking for a challenger."),
    "tcgshop.duelist.protected": ("Es la mesa del jugador profesional. ¡Las manos quietas!", "That's the pro player's table. Hands off!"),
    "tcgshop.duelist.already": ("Ya jugó su partida. Está recogiendo.", "He already played his match. He's packing up."),
    "tcgshop.duelist.no_bank": ("El profesional solo juega por dinero de verdad (hace falta CobbleDollars).",
                                "The pro only plays for real money (CobbleDollars is needed)."),
    "tcgshop.duelist.too_far": ("Acércate al jugador profesional.", "Get closer to the pro player."),
    "tcgshop.duelist.none": ("No tienes ningún desafío pendiente.", "You have no pending challenge."),
    "tcgshop.duelist.start_failed": ("La partida no pudo empezar. Revisa tu mazo y vuelve a hablar con él.",
                                     "The match couldn't start. Check your deck and talk to him again."),
    "tcgshop.duelist.chat.header": ("%s te reta a una partida de JCC a vida o muerte (Difícil):",
                                    "%s challenges you to a high-stakes TCG match (Hard):"),
    "tcgshop.duelist.chat.accept_hint": ("Aceptas las condiciones y empieza la partida en su mesa.",
                                         "You accept the stakes and the match starts at his table."),
    "tcgshop.duelist.won": ("¡Has ganado a %s! Te llevas %s.", "You beat %s! You take %s."),
    "tcgshop.duelist.lost_cash": ("%s se queda con los %s que apostaste.", "%s keeps the %s you staked."),
    "tcgshop.duelist.stake_held": ("Retiene %s de tu dinero como apuesta hasta que termine la partida.",
                                   "He holds %s of your cash as the stake until the match ends."),
    "tcgshop.duelist.stake_refunded": ("%s te devuelve los %s que tenía retenidos.", "%s returns the %s stake he was holding."),
    "tcgshop.duelist.min_stake": ("Solo juega si apuestas al menos %s... o si no llevas nada de dinero encima.",
                                  "He only plays for a stake of at least %s... or against someone carrying no cash at all."),
    "tcgshop.duelist.hold_failed": ("No se pudo retener tu apuesta. Inténtalo de nuevo.", "Your stake couldn't be held. Try again."),
    "tcgshop.duelist.lost_item": ("%s se lleva tu %s.", "%s takes your %s."),
    "tcgshop.duelist.lost_nothing": ("%s no encuentra nada que llevarse. Qué triste.", "%s finds nothing worth taking. How sad."),
    "tcgshop.duelist.forfeit_join": ("Mientras no estabas perdiste tu partida contra el jugador profesional (abandonar cuenta como derrota).",
                                     "While you were away you lost your match against the pro player (leaving counts as a loss)."),
    "tcgshop.duelist.stack": ("%s ×%s", "%s ×%s"),

    # ------------------------------------------------------------------ phone messages
    "tcgshop.duelist.msg.win": ("Buena partida. El profesional recogió sus cosas y se fue con una reverencia. Tu premio de %s ya está en tu cartera.",
                                "Good game. The pro player packed up his things and left with a bow. Your %s prize is already in your wallet."),
    "tcgshop.duelist.msg.cash": ("El jugador profesional se fue con sus cosas... y con %s de tu dinero.",
                                 "The pro player left with his things... and with %s of your cash."),
    "tcgshop.duelist.msg.item": ("El jugador profesional se fue con sus cosas y te robó tu %s.",
                                 "The pro player left with his things and stole your %s."),
    "tcgshop.duelist.msg.nothing": ("El jugador profesional se fue con sus cosas. No llevabas nada que le interesara... esta vez.",
                                    "The pro player left with his things. You had nothing he wanted... this time."),

    # ------------------------------------------------------------------ stakes window
    "tcgshop.duelist.ui.title": ("Desafío a vida o muerte", "High-stakes challenge"),
    "tcgshop.duelist.ui.level": ("Dificultad: %s", "Difficulty: %s"),
    "tcgshop.duelist.ui.hard": ("Difícil", "Hard"),
    "tcgshop.duelist.ui.intro": ("Una partida real de JCC contra un profesional. Una sola partida: luego recoge su mesa y se va.",
                                 "A real TCG match against a pro. Just one match: then he packs up his table and leaves."),
    "tcgshop.duelist.ui.if_win": ("Si ganas", "If you win"),
    "tcgshop.duelist.ui.win": ("Te llevas %s", "You get %s"),
    "tcgshop.duelist.ui.win_back": ("Te llevas %s y recuperas tu apuesta", "You get %s and your stake back"),
    "tcgshop.duelist.ui.if_lose": ("Si pierdes", "If you lose"),
    "tcgshop.duelist.ui.lose": ("Al aceptar retiene ya todo el dinero que lleves encima, hasta %s. Si pierdes, se lo queda.",
                                "When you accept he holds all the cash you carry right away, up to %s. If you lose, he keeps it."),
    "tcgshop.duelist.ui.lose_now": ("Ahora llevas %s: retendría %s.", "Right now you carry %s: he would hold %s."),
    "tcgshop.duelist.ui.lose_none": ("Ahora mismo no llevas dinero...", "Right now you carry no cash..."),
    "tcgshop.duelist.ui.lose_short": ("Ahora llevas %s: exige una apuesta de al menos %s (o no llevar nada de dinero).",
                                      "Right now you carry %s: he wants a stake of at least %s (or no cash at all)."),
    "tcgshop.duelist.ui.lose_item": ("Sin dinero encima no retiene nada, pero si pierdes se lleva un objeto al azar de tu inventario (nunca binders ni el móvil).",
                                     "With no cash on you nothing is held, but if you lose he takes one random item stack from your inventory (never binders or the phone)."),
    "tcgshop.duelist.ui.rules": ("Juegas con tu mazo de Liga guardado (o uno automático). Alejarte de la mesa o desconectarte cuenta como derrota. Un empate, o una partida que no llega a empezar, te devuelve la apuesta.",
                                 "You play with your saved League deck (or an automatic one). Walking away from the table or logging out counts as a loss. A draw, or a match that never starts, returns your stake."),
    "tcgshop.duelist.ui.accept": ("Aceptar", "Accept"),
    "tcgshop.duelist.ui.decline": ("Rechazar", "Decline"),

    # ------------------------------------------------------------------ operator command
    "tcgshop.duelist.cmd.spawned": ("Un Duelista errante se ha instalado en %s.", "A Wandering Duelist set up at %s."),
    "tcgshop.duelist.cmd.failed": ("No hay suelo natural y llano cerca (o ya hay un duelista).", "No flat natural ground nearby (or a duelist is already here)."),

    # ------------------------------------------------------------------ advancement (data/tcgshop/advancement/duelist_win.json)
    "advancement.tcgshop.duelist_win.title": ("Le has quitado la gorra", "Hat tip"),
    "advancement.tcgshop.duelist_win.desc": ("Gana al Duelista errante en su propia mesa", "Beat the Wandering Duelist at his own table"),
}

# NPC lines: (Spanish, English) per category; counts must match WanderingDuelists.LINES
LINES = {
    "arrive": [
        ("Este rincón servirá. Buena luz, buen viento, buenos rivales.", "This spot will do. Good light, good breeze, good opponents."),
        ("Mesa montada. Ahora solo falta alguien con agallas.", "Table's up. Now I just need someone with guts."),
        ("Otro pueblo, otra mesa, otro valiente que caerá.", "Another town, another table, another brave soul to fall."),
        ("Barajo, me siento y espero. Siempre aparece alguien.", "Shuffle, sit, wait. Someone always shows up."),
    ],
    "greet": [
        ("¡Eh, tú! Tienes cara de jugar a cartas. ¿Una partida de verdad?", "Hey, you! You look like you play cards. Fancy a real match?"),
        ("Diez mil para quien me gane. Nadie lo ha conseguido este mes.", "Ten thousand to whoever beats me. Nobody has this month."),
        ("¿Traes mazo? Siéntate, la silla no muerde. Yo sí.", "Got a deck? Sit down, the chair doesn't bite. I do."),
        ("Busco rival serio. Los turistas, que sigan andando.", "Looking for a serious opponent. Tourists, keep walking."),
        ("Psst. ¿Te atreves con un profesional?", "Psst. Brave enough to face a pro?"),
        ("He ganado torneos en tres regiones. ¿Quieres ser la cuarta?", "I've won tournaments in three regions. Want to be the fourth?"),
    ],
    "ambient": [
        ("Este mazo nunca ha perdido sobre hierba.", "This deck has never lost on grass."),
        ("*baraja con una sola mano*", "*shuffles one-handed*"),
        ("Cuenta las Energías... cincuenta y nueve, sesenta. Perfecto.", "Counting Energy... fifty-nine, sixty. Perfect."),
        ("La paciencia también es una carta. La mejor.", "Patience is a card too. The best one."),
        ("Mi abuela me enseñó a jugar. Ella tampoco perdía nunca.", "My grandma taught me to play. She never lost either."),
        ("¿Alguien? ¿Nadie? La gloria espera sentada.", "Anyone? No one? Glory waits sitting down."),
        ("*mira su reloj y suspira*", "*checks his watch and sighs*"),
        ("Hoy siento el primer robo con buena pinta.", "I've got a good feeling about my opening hand today."),
    ],
    "offer": [
        ("Las reglas son simples: tú ganas, cobras. Tú pierdes, cobro yo.", "Simple rules: you win, you get paid. You lose, I get paid."),
        ("Mira bien las condiciones. Aquí no hay revancha.", "Read the stakes carefully. There are no rematches here."),
        ("Una partida. Una. Luego me voy, pase lo que pase.", "One match. One. Then I'm gone, whatever happens."),
        ("Te enseño lo que hay en juego. Sin letra pequeña.", "Here's what's at stake. No fine print."),
        ("Piénsalo. Pero no mucho, que se me enfrían las cartas.", "Think it over. Not too long, my cards are getting cold."),
    ],
    "accept": [
        ("¡Eso es! Siéntate y baraja.", "That's the spirit! Sit down and shuffle."),
        ("Respeto. Que gane el mejor... o sea, yo.", "Respect. May the best player win... that's me."),
        ("Cara o cruz para empezar. Que la suerte te acompañe, la vas a necesitar.", "Coin flip to start. Good luck, you'll need it."),
        ("Por fin alguien con valor. Al lío.", "Finally someone with nerve. Let's go."),
        ("Robamos siete. Y que no tiemble el pulso.", "Draw seven. Keep your hands steady."),
    ],
    "decline": [
        ("¿Miedo? No te culpo.", "Scared? Can't blame you."),
        ("Sabia decisión. O cobarde. Depende de a quién preguntes.", "Wise choice. Or cowardly. Depends who you ask."),
        ("Aquí seguiré un rato, por si te lo piensas.", "I'll be here a while, in case you change your mind."),
        ("Vuelve cuando tu mazo tenga más dientes.", "Come back when your deck has more teeth."),
        ("Lo entiendo. No todo el mundo nace para la gloria.", "I get it. Not everyone is born for glory."),
    ],
    "busy": [
        ("Un rival cada vez, amigo.", "One challenger at a time, friend."),
        ("Ahora estoy ocupado. Mira y aprende.", "I'm busy right now. Watch and learn."),
        ("Espera tu turno. Literalmente.", "Wait your turn. Literally."),
        ("Esta mesa ya tiene dueño. Bueno, dos.", "This table's taken. By two, actually."),
    ],
    "deck_fail": [
        ("Vuelve con un mazo legal, novato.", "Come back with a legal deck, rookie."),
        ("Sin mazo no hay partida. Arréglalo y aquí estaré.", "No deck, no match. Fix it and I'll be here."),
        ("¿Eso es un mazo? Ordénalo y hablamos.", "Is that a deck? Sort it out and we'll talk."),
        ("Algo no cuadra. Revisa tus cartas y vuelve.", "Something's off. Check your cards and come back."),
    ],
    "win": [
        ("Bien jugado. Te has ganado cada moneda.", "Well played. You earned every coin."),
        ("Hacía tiempo que nadie me ganaba. Gracias por la lección.", "Nobody has beaten me in a long time. Thanks for the lesson."),
        ("Me quito el sombrero. Eres de los buenos.", "Hats off. You're one of the good ones."),
        ("Una victoria limpia. Nos veremos en algún torneo.", "A clean win. See you at some tournament."),
        ("Toma tu premio. Te lo has ganado a pulso.", "Take your prize. You earned it the hard way."),
        ("Recojo y me voy, que hoy me toca entrenar el doble.", "I'll pack up and go. Double practice for me today."),
    ],
    "lose_cash": [
        ("Gracias por la donación. ¡Suerte la próxima!", "Thanks for the donation. Better luck next time!"),
        ("El dinero va donde está el talento.", "Money goes where the talent is."),
        ("Un placer hacer negocios contigo.", "A pleasure doing business with you."),
        ("No te lo tomes a mal. Bueno, un poco sí.", "Don't take it personally. Well, a little."),
        ("Con esto me pago los sobres de la semana. ¡Adiós!", "That pays for this week's packs. Bye!"),
    ],
    "lose_item": [
        ("¿Sin dinero? Entonces me llevo esto de recuerdo.", "No cash? Then I'll take this as a souvenir."),
        ("Un trato es un trato. Esto ahora es mío.", "A deal's a deal. This is mine now."),
        ("Bonito objeto. Quedará genial en mi mochila.", "Nice item. It'll look great in my backpack."),
        ("La próxima vez trae dinero. O un mazo mejor.", "Next time bring cash. Or a better deck."),
        ("No llores, que es solo un objeto. Mío, ahora.", "Don't cry, it's just an item. Mine, now."),
    ],
    "lose_nothing": [
        ("¿Bolsillos vacíos y mochila vacía? Haré como que no ha pasado.", "Empty pockets and an empty bag? I'll pretend this never happened."),
        ("No tienes nada... me das hasta pena.", "You've got nothing... I almost feel sorry for you."),
        ("Ganar a alguien sin nada no tiene gracia. Adiós.", "Beating someone with nothing is no fun. Bye."),
        ("Al menos has aprendido algo. Gratis.", "At least you learned something. For free."),
    ],
    "runaway": [
        ("¿Huyendo? Ya cobraré igualmente.", "Running away? I'll collect anyway."),
        ("Abandonar cuenta como derrota. Ya lo sabías.", "Leaving counts as a loss. You knew that."),
        ("Se fue sin despedirse. Mi premio me lo llevo igual.", "Left without saying goodbye. I'll still take my prize."),
    ],
    "draw": [
        ("Mmm. Lo dejamos en tablas. Tengo sitios a los que ir.", "Hm. Let's call it a draw. I have places to be."),
        ("Esto no ha contado. Otro día será.", "That one didn't count. Some other day."),
        ("Interrupciones... así no se puede jugar. Me voy.", "Interruptions... no way to play like this. I'm off."),
        ("Sin ganador no hay premio. Hasta otra.", "No winner, no prize. Until next time."),
    ],
    "bored": [
        ("Nadie se atreve por aquí. Al siguiente pueblo.", "Nobody brave enough around here. Off to the next town."),
        ("Mucho mirón y poco valiente. Recojo.", "Lots of onlookers, no challengers. Packing up."),
        ("Se me ha hecho tarde. Otro día, otra mesa.", "It's getting late. Another day, another table."),
        ("Si cambias de opinión, búscame en otro sitio.", "If you change your mind, find me somewhere else."),
    ],
}

EXPECTED = {"arrive": 4, "greet": 6, "ambient": 8, "offer": 5, "accept": 5, "decline": 5, "busy": 4, "deck_fail": 4,
            "win": 6, "lose_cash": 5, "lose_item": 5, "lose_nothing": 4, "runaway": 3, "draw": 4, "bored": 4}


def main():
    for cat, n in EXPECTED.items():
        assert len(LINES[cat]) == n, f"{cat}: {len(LINES[cat])} lines, WanderingDuelists.LINES says {n}"
    texts = dict(T)
    for cat, lines in LINES.items():
        for i, v in enumerate(lines):
            texts[f"tcgshop.duelist.line.{cat}.{i}"] = v
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in texts.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(texts), "texts")


if __name__ == "__main__":
    main()
