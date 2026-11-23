"""Texts for stealing from the Wandering Collector (1.0 beta 3: entity/CollectorTheft, TraderScreen "Steal" button,
the collector_* advancements and the "collector*" settings in tcgshop-economy.json).

Usage: python tools/gen_lang_10c_collectortheft.py  (updates/inserts the keys in es_es.json and en_us.json)
The number of variants of each line.<cat> / msg.<cat> must match CollectorTheft.LINES / CollectorTheft.MSGS.
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

P = "tcgshop.collector_theft."

T = {
    # ------------------------------------------------------------------ stall button (TraderScreen)
    P + "steal_btn": ("Robar", "Steal"),
    P + "steal_confirm": ("¿Seguro? Otra vez", "Sure? Click again"),
    P + "hint.title": ("Robar una carta", "Steal a card"),
    P + "hint.0": ("Te llevas una carta al azar de su puesto. Él se dará cuenta. Siempre se dan cuenta.",
                   "You pocket one random card from his stall. He will notice. They always notice."),
    P + "hint.1": ("Se pone hecho una furia: rápido, con armadura, mucha vida y una espada que pega fuerte. Te perseguirá.",
                   "He goes berserk: fast, armored, tough as nails and swinging a sword that hits hard. He will hunt you."),
    P + "hint.2": ("Si escapas sin matarlo, tus tiendas pierden MUCHA reputación. Si lo matas, 50% de que alguien lo vea y pase lo mismo.",
                   "Escape without killing him: your shops lose a LOT of reputation. Kill him: 50% chance someone saw it, with the same loss."),
    P + "hint.3": ("Si te mata él, pierdes reputación y la carta robada desaparece.",
                   "If he kills you, you lose reputation and the stolen card vanishes."),

    # ------------------------------------------------------------------ chat
    P + "too_far": ("Demasiado lejos para meter la mano en su puesto.", "Too far away to reach into his stall."),
    P + "nothing": ("No le queda nada que robar. Qué triste, ¿no?", "He has nothing left to steal. Kind of sad, huh?"),
    P + "stolen": ("Te guardas %s. ¡%s se ha vuelto loco de rabia!", "You pocket %s. %s goes berserk!"),
    P + "stolen_hint": ("Corre lejos, plántale cara... o devuélvesela de la peor manera.",
                        "Run far away, stand your ground... or give it back the hard way."),
    P + "escaped": ("Te has escapado con %s... pero el robo corre de boca en boca: tus tiendas pierden %s de reputación.",
                    "You got away with %s... but word of the robbery spreads: your shops lose %s reputation."),
    P + "escaped_noshop": ("Te has escapado con %s. Suerte que no tienes tienda: habría perdido %s de reputación.",
                           "You got away with %s. Lucky you don't own a shop: it would have lost %s reputation."),
    P + "killed_seen": ("Has acabado con %s, pero alguien lo ha visto todo: tus tiendas pierden %s de reputación.",
                        "You took down %s, but someone saw everything: your shops lose %s reputation."),
    P + "killed_seen_noshop": ("Has acabado con %s y alguien lo ha visto. Si tuvieras tienda, perdería %s de reputación.",
                               "You took down %s and someone saw it. If you had a shop, it would lose %s reputation."),
    P + "killed_clean": ("Has acabado con %s y nadie ha visto nada... %s es tuya. Por ahora.",
                         "You took down %s and nobody saw a thing... %s is yours. For now."),
    P + "died": ("%s ha recuperado %s. Tus tiendas pierden %s de reputación.",
                 "%s took %s back. Your shops lose %s reputation."),
    P + "died_noshop": ("%s ha recuperado %s. Menos mal que no tienes tienda, que habría perdido %s de reputación.",
                        "%s took %s back. Good thing you don't own a shop: it would have lost %s reputation."),
    P + "paid": ("No encontró %s encima, así que se ha cobrado %s de tu cartera. Los coleccionistas no perdonan.",
                 "He couldn't find %s on you, so he helped himself to %s from your wallet. Collectors never forgive."),
    P + "paid_register": ("No encontró %s encima, así que se ha cobrado %s de las cajas registradoras de tus tiendas. Los coleccionistas no perdonan.",
                          "He couldn't find %s on you, so he helped himself to %s from your shop registers. Collectors never forgive."),
    P + "paid_both": ("No encontró %s encima, así que se ha cobrado %s de tu cartera y %s de las cajas de tus tiendas. Los coleccionistas no perdonan.",
                      "He couldn't find %s on you, so he took %s from your wallet and %s from your shop registers. Collectors never forgive."),
    P + "hot": ("Esta carta quema: el coleccionista todavía te está buscando.",
                "This card is hot: the collector is still hunting you."),
    P + "vanished": ("La carta robada se ha esfumado. El coleccionista siempre cobra sus deudas.",
                     "The stolen card is gone. The collector always collects."),
    P + "tooltip": ("Robada: su dueño la quiere de vuelta", "Stolen: its owner wants it back"),

    # ------------------------------------------------------------------ phone messages
    P + "msg.escape.0": ("¿Crees que te has librado? Todos los coleccionistas de la región conocen ya tu cara. Disfruta de %s.",
                         "You think you got away? Every collector in the region knows your face now. Enjoy %s."),
    P + "msg.escape.1": ("Se lo he contado a todo el mundo. TODO el mundo. Espero que %s valga la pena.",
                         "I told everyone. EVERYONE. I hope %s was worth it."),
    P + "msg.escape.2": ("%s era la joya de mi colección. Tu reputación, en cambio, ya no vale ni una carta común.",
                         "%s was the gem of my collection. Your reputation, on the other hand, isn't worth a common anymore."),
    P + "msg.got_back.0": ("He recuperado %s. Ni se te ocurra acercarte otra vez a mi puesto.",
                           "Got my %s back. Don't you dare come near my stall again."),
    P + "msg.got_back.1": ("%s vuelve a estar en su funda, donde debe estar. Tú, en cambio, deberías estar en otra parte.",
                           "%s is back in its sleeve where it belongs. You, on the other hand, belong far away."),
    P + "msg.got_back.2": ("Lección gratis: nunca le robes a alguien que clasifica sus cartas por orden alfabético. %s, recuperada.",
                           "Free lesson: never rob someone who sorts their binder alphabetically. %s, recovered."),
    P + "msg.rumor": ("Los clientes cuchichean sobre un coleccionista que nunca volvió a casa... y miran hacia aquí. Reputación -%s.",
                      "Customers are whispering about a collector who never made it home... and looking this way. Reputation -%s."),

    # ------------------------------------------------------------------ NPC lines (CollectorTheft.LINES)
    P + "line.robbed.0": ("¡¿PERDONA?! ¡Devuélveme eso AHORA MISMO!", "EXCUSE ME?! Give that back RIGHT NOW!"),
    P + "line.robbed.1": ("¡Al ladrón! ¡Y el ladrón soy yo persiguiéndote!", "Stop, thief! ...and I'm the one doing the stopping!"),
    P + "line.robbed.2": ("Tres años buscando esa carta. TRES. AÑOS.", "Three years hunting that card. THREE. YEARS."),
    P + "line.robbed.3": ("Vale. Has elegido el camino difícil.", "Fine. You chose the hard way."),
    P + "line.robbed.4": ("¡Nadie toca mi colección y sale andando!", "Nobody touches my collection and walks away!"),
    P + "line.robbed.5": ("¿Sabes cuánto me costó ese protector? ¡VEN AQUÍ!", "Do you know what that sleeve cost me? GET BACK HERE!"),
    P + "line.chase.0": ("¡Corre, corre! ¡Te pillaré igual!", "Run, run! I'll catch you anyway!"),
    P + "line.chase.1": ("¡Llevo años persiguiendo cartas raras! ¡Tú eres pan comido!", "I've chased rare cards for years! You're easy!"),
    P + "line.chase.2": ("¡Esa carta tiene centrado perfecto y TÚ no la mereces!", "That card has perfect centering and YOU don't deserve it!"),
    P + "line.chase.3": ("¡Devuélvemela y quizá solo te dé un poquito!", "Give it back and maybe I'll only hit you a little!"),
    P + "line.chase.4": ("¡Que sepas que esto va a mi blog!", "Just so you know, this is going on my blog!"),
    P + "line.chase.5": ("¡Mis rodillas aguantan más que tu conciencia!", "My knees have more stamina than your conscience!"),
    P + "line.chase.6": ("¡No hay escapatoria! ¡Conozco cada rincón de cada feria!", "There's no escape! I know every corner of every card fair!"),
    P + "line.chase.7": ("¡Tengo la factura de compra! ¡Es MÍA!", "I have the receipt! It's MINE!"),
    P + "line.low_health.0": ("Esto... no entraba en mis planes...", "This... was not part of the plan..."),
    P + "line.low_health.1": ("¡Ni herido suelto una carta rara!", "Even hurt, I never let go of a rare!"),
    P + "line.low_health.2": ("¡Un coleccionista nunca se rinde!", "A collector never gives up!"),
    P + "line.low_health.3": ("Solo... necesito... recuperar el aliento...", "Just... need to... catch my breath..."),
    P + "line.give_up.0": ("¡Bah! Que se te pudra en el binder.", "Bah! May it rot in your binder."),
    P + "line.give_up.1": ("Me rindo... pero todo el mundo sabrá lo que has hecho.", "I give up... but everyone will know what you did."),
    P + "line.give_up.2": ("Pienso contárselo a toda la región. Con fotos.", "I'm telling the whole region. With pictures."),
    P + "line.give_up.3": ("Ya no estoy enfadado. Estoy decepcionado. Que es peor.", "I'm not angry anymore. I'm disappointed. Which is worse."),
    P + "line.give_up.4": ("Disfruta de tu carta robada. Tu reputación se queda conmigo.", "Enjoy your stolen card. Your reputation stays with me."),
    P + "line.got_back.0": ("¡Ajá! Vuelve con papá.", "Aha! Come back to daddy."),
    P + "line.got_back.1": ("Justicia de coleccionista. Rápida y en funda.", "Collector's justice. Swift and sleeved."),
    P + "line.got_back.2": ("Ni un arañazo en la carta. En ti, en cambio...", "Not a scratch on the card. On you, on the other hand..."),
    P + "line.got_back.3": ("Lo que es mío, vuelve a mí. Siempre.", "What's mine comes back to me. Always."),
    P + "line.got_back.4": ("La próxima vez, pregunta el precio.", "Next time, ask for the price."),
    P + "line.got_back.5": ("Y ahora, si me disculpas, tengo que desinfectarla.", "And now, if you'll excuse me, I need to disinfect it."),
    P + "line.thief_click.0": ("¿¡Encima vienes a saludar!?", "And now you come to say hi?!"),
    P + "line.thief_click.1": ("¡Ni se te ocurra tocar mi puesto otra vez!", "Don't even think about touching my stall again!"),
    P + "line.thief_click.2": ("¿Vienes a devolvérmela? ¡Pues ESTÁTE QUIETO!", "Here to give it back? Then HOLD STILL!"),
    P + "line.thief_click.3": ("¡Cara dura! ¡Cara muy, muy dura!", "The nerve! The absolute nerve!"),
    P + "line.victim.0": ("¡Hoy no hay tratos! ¡Me han robado!", "No deals today! I've been robbed!"),
    P + "line.victim.1": ("¿Has visto a alguien correr con una carta mía?", "Did you see someone running off with one of my cards?"),
    P + "line.victim.2": ("Puesto cerrado. Estoy ocupado persiguiendo a un ladrón.", "Stall's closed. I'm busy chasing a thief."),
    P + "line.victim.3": ("Si ves a ese sinvergüenza, dile que voy a por él.", "If you see that crook, tell them I'm coming."),

    # ------------------------------------------------------------------ advancements (data/tcgshop/advancement/collector_*.json)
    "advancement.tcgshop.collector_steal.title": ("Manos largas", "Sticky fingers"),
    "advancement.tcgshop.collector_steal.desc": ("Róbale una carta a un Coleccionista Ambulante", "Steal a card from a Wandering Collector"),
    "advancement.tcgshop.collector_slain.title": ("El que roba y remata", "Rob and run... over"),
    "advancement.tcgshop.collector_slain.desc": ("Mata al Coleccionista al que le has robado", "Kill the Collector you robbed"),
    "advancement.tcgshop.collector_revenge.title": ("El coleccionista siempre cobra", "The collector always collects"),
    "advancement.tcgshop.collector_revenge.desc": ("Muere a manos del Coleccionista al que le robaste", "Get killed by the Collector you robbed"),
    "advancement.tcgshop.collector_escape.title": ("Piernas de ladrón", "Thief's legs"),
    "advancement.tcgshop.collector_escape.desc": ("Escapa con la carta robada de un Coleccionista furioso", "Get away from a furious Collector with the stolen card"),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
