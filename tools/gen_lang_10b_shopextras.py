"""1.0 beta 2 texts, area "shopextras": play table chairs / customer challenges / exhibitions (ptcg/PlayTables),
statue packs (luxury/StatuePacks) and the store sale event (shop/Discounts).

Usage: python tools/gen_lang_10b_shopextras.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ play table: chairs requirement
    "tcgshop.table.need_chairs": ("Esta mesa necesita al menos %1$s sillas (de CobbleFurnies o de otro mod de muebles) a %2$s bloques o menos. Ahora tiene %3$s.",
                                  "This table needs at least %1$s chairs (from CobbleFurnies or another furniture mod) within %2$s blocks. It has %3$s."),
    "tcgshop.table.need_seats": ("Esta mesa necesita al menos %1$s asientos (sillas de muebles o escaleras como banco) a %2$s bloques o menos. Ahora tiene %3$s.",
                                 "This table needs at least %1$s seats (furniture chairs or stairs used as a bench) within %2$s blocks. It has %3$s."),
    "tcgshop.table.busy_npc": ("Dos clientes están jugando una partida en esta mesa. Espera a que terminen.",
                               "Two customers are playing a match at this table. Wait until they finish."),
    "tcgshop.table.reserved": ("Esta mesa está reservada para un reto.", "This table is reserved for a challenge."),

    # ------------------------------------------------------------------ play table: customer challenge
    "tcgshop.table.challenge.received": ("%1$s te reta a una partida amistosa de TCG en la mesa de juego (nivel: %2$s).",
                                         "%1$s challenges you to a friendly TCG match at the play table (level: %2$s)."),
    "tcgshop.table.challenge.accept": ("[Aceptar]", "[Accept]"),
    "tcgshop.table.challenge.decline": ("[Rechazar]", "[Decline]"),
    "tcgshop.table.challenge.accept_hint": ("Partida amistosa contra este cliente: si ganas te llevas el premio de siempre y si pierdes no pagas nada. También puedes hacer clic en la mesa.",
                                            "Friendly match against this customer: win and you get the usual prize, lose and you pay nothing. You can also click the table."),
    "tcgshop.table.challenge.none": ("No tienes ningún reto pendiente.", "You have no pending challenge."),
    "tcgshop.table.challenge.expired": ("%s se ha cansado de esperar tu respuesta.", "%s got tired of waiting for your answer."),
    "tcgshop.table.challenge.failed": ("La partida no ha podido empezar.", "The match could not start."),
    "tcgshop.table.level.0": ("Novato", "Rookie"),
    "tcgshop.table.level.1": ("Entrenador", "Trainer"),
    "tcgshop.table.level.2": ("Veterano", "Veteran"),

    # NPC lines (speech bubbles)
    "tcgshop.table.ask.0": ("¡Eh! ¿Echamos una partida en la mesa?", "Hey! Fancy a match at the table?"),
    "tcgshop.table.ask.1": ("Traigo mi mazo... ¿te atreves?", "I brought my deck... dare to play?"),
    "tcgshop.table.ask.2": ("Esa mesa está pidiendo a gritos un duelo. ¿Jugamos?", "That table is begging for a duel. Shall we?"),
    "tcgshop.table.ask.3": ("Dicen que el dueño juega fatal. ¿Lo comprobamos?", "They say the owner plays terribly. Shall we find out?"),
    "tcgshop.table.ask.4": ("Una partidita rápida, ¿vale? Prometo no llorar si pierdo.", "A quick match, okay? I promise not to cry if I lose."),
    "tcgshop.table.yes.0": ("¡Genial! Voy sentándome.", "Great! I'll take a seat."),
    "tcgshop.table.yes.1": ("¡Así me gusta! Baraja bien.", "That's the spirit! Shuffle well."),
    "tcgshop.table.yes.2": ("Prepárate, que vengo con todo.", "Get ready, I'm going all in."),
    "tcgshop.table.no.0": ("Vaya... otra vez será.", "Oh well... maybe next time."),
    "tcgshop.table.no.1": ("Bueno, seguiré mirando cartas.", "Fine, I'll keep browsing cards."),
    "tcgshop.table.no.2": ("¿Miedo? Tranquilo, lo entiendo.", "Scared? Don't worry, I get it."),
    "tcgshop.table.gg.0": ("¡Buena partida!", "Good game!"),
    "tcgshop.table.gg.1": ("Gracias por jugar. ¡La próxima no te libras!", "Thanks for playing. Next time you won't get away!"),
    "tcgshop.table.gg.2": ("Qué partidón. Volveré a por la revancha.", "What a match. I'll be back for a rematch."),
    "tcgshop.table.gg.3": ("Me quedo con las ganas de otra. ¡GG!", "I could go for another one. GG!"),
    "tcgshop.table.invite.0": ("Oye, ¿una partida rápida en la mesa?", "Hey, a quick match at the table?"),
    "tcgshop.table.invite.1": ("¡Mesa libre! ¿Te echo un reto?", "Free table! Up for a challenge?"),
    "tcgshop.table.invite.2": ("Vamos, que quiero probar mi mazo nuevo.", "Come on, I want to try my new deck."),
    "tcgshop.table.play.0": ("¡Ataco con todo!", "All-out attack!"),
    "tcgshop.table.play.1": ("Bajo un Pokémon a la banca.", "I'm putting a Pokémon on the bench."),
    "tcgshop.table.play.2": ("¿En serio? ¿Esa carta?", "Seriously? That card?"),
    "tcgshop.table.play.3": ("Uno más y te quedas sin premios.", "One more and you're out of prizes."),
    "tcgshop.table.play.4": ("Uy, no me esperaba eso...", "Ooh, didn't see that coming..."),
    "tcgshop.table.play.5": ("Uso una carta de Entrenador.", "I play a Trainer card."),
    "tcgshop.table.play.6": ("¡Evoluciona!", "Evolve!"),
    "tcgshop.table.play.7": ("Te toca. Piénsatelo bien.", "Your turn. Think it through."),
    "tcgshop.table.win.0": ("¡Gané! Ha sido muy reñido.", "I won! That was close."),
    "tcgshop.table.win.1": ("¡Victoria! Mi mazo es imparable.", "Victory! My deck is unstoppable."),
    "tcgshop.table.win.2": ("Buena partida, pero hoy era mi día.", "Good game, but today was my day."),
    "tcgshop.table.win.3": ("¡Toma ya! Último premio.", "Yes! Last prize card."),
    "tcgshop.table.lose.0": ("Bah, mala mano inicial...", "Bah, bad opening hand..."),
    "tcgshop.table.lose.1": ("Me has ganado bien. ¡Revancha otro día!", "Fair win. Rematch another day!"),
    "tcgshop.table.lose.2": ("Tengo que comprar más sobres...", "I need to buy more packs..."),
    "tcgshop.table.lose.3": ("La próxima no tendrás tanta suerte.", "You won't be so lucky next time."),

    # ------------------------------------------------------------------ statue packs
    "tcgshop.statue.pack_dropped": ("¡Un cliente se ha despistado mirando tu %s y se le ha caído un sobre!",
                                    "A customer got distracted by your %s and dropped a pack!"),

    # ------------------------------------------------------------------ store sale event
    "tcgshop.discount.msg": ("¡Oferta! %1$s tiene un %2$s%% de descuento en la tienda del móvil y en el Mayorista durante %3$s día(s) de Minecraft. ¡No te la pierdas!",
                             "Sale! %1$s is %2$s%% off in the phone store and at the Wholesaler for %3$s Minecraft day(s). Don't miss it!"),
    "tcgshop.discount.cmd.started": ("Oferta iniciada: %1$s con un %2$s%% de descuento durante %3$s día(s).",
                                     "Sale started: %1$s at %2$s%% off for %3$s day(s)."),
    "tcgshop.discount.cmd.cleared": ("Oferta terminada.", "Sale ended."),
    "tcgshop.discount.cmd.none": ("No hay ningún artículo que se pueda rebajar.", "There is no item that can go on sale."),
}

# Owned by another area (phone Messages); only written if nobody defined it yet.
SOFT = {
    "tcgshop.msg.sender.shop": ("Tienda", "Shop"),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        for k, v in SOFT.items():
            d.setdefault(k, v[i])
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
