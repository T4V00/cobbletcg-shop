"""Texts for the 1.0 onboarding (beta 2): starter kit (5 packs + equipped starter binder), first-pack starting money,
the one-time unique collector, the one-time taunting rival and the extra Wholesaler visits for players without a shop
(onboarding/Onboarding, onboarding/OnboardingVisits, entity/UniqueCollectorEntity, entity/RivalTaunterEntity,
client/screen/UniqueCollectorScreen, entity/WholesalerEntity.newcomerSpawner).

The number of lines of each speech category (tcgshop.onb.<category>.<n>) must match OnboardingVisits.LINES.

Usage: python tools/gen_lang_10b_onboarding.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ starter kit and first-pack money
    "tcgshop.onboarding.binder_equipped": (
        "Tu binder personal (25 cartas, con tu nombre) ya va equipado en tu ranura de accesorios: las cartas de tus sobres irán directas a él.",
        "Your personal binder (25 cards, with your name) is already equipped in your accessory slot: cards from your packs go straight into it."),
    "tcgshop.onboarding.binder_inventory": (
        "Tu binder personal (25 cartas, con tu nombre) está en tu inventario. Llévalo encima: las cartas de tus sobres irán directas a él.",
        "Your personal binder (25 cards, with your name) is in your inventory. Keep it on you: cards from your packs go straight into it."),
    "tcgshop.onboarding.money_hint": (
        "Cuando abras tu primer sobre recibirás %s para empezar.",
        "When you open your first pack you'll receive %s to get started."),
    "tcgshop.onboarding.start_money": (
        "¡Primer sobre abierto! Recibes %s para empezar tu aventura de coleccionista.",
        "First pack opened! You receive %s to start your collector adventure."),
    "tcgshop.onboarding.start_money_hint": (
        "Ahorra para tu primer bloque de tienda (lo vende el Mayorista)... o sigue abriendo sobres, tú sabrás.",
        "Save up for your first shop block (the Wholesaler sells them)... or keep opening packs, your call."),
    "tcgshop.onb.wholesaler_newcomer": (
        "Aún no tienes tienda: el Mayorista vende bloques de tienda para empezar.",
        "You don't have a shop yet: the Wholesaler sells shop blocks to get you started."),

    # ------------------------------------------------------------------ entities and roles
    "entity.tcgshop.unique_collector": ("Coleccionista único", "Unique Collector"),
    "entity.tcgshop.rival": ("Rival presumido", "Smug Rival"),
    "tcgshop.role.unique.trader": ("Coleccionista único", "Unique collector"),
    "tcgshop.role.rival.scalper": ("Dueño de tienda rival", "Rival shop owner"),

    # ------------------------------------------------------------------ unique collector
    "tcgshop.onb.collector.name": ("Don Aurelio", "Mr. Aurelio"),
    "tcgshop.onb.collector.arrived": (
        "Un coleccionista muy peculiar te está buscando... dicen que solo aparece una vez.",
        "A very peculiar collector is looking for you... they say he only shows up once."),
    "tcgshop.onb.collector.title": ("Oferta única", "One-time offer"),
    "tcgshop.onb.collector.ask": (
        "Llevo años buscando esta carta y tú la tienes. Te hago una oferta que no voy a repetir.",
        "I've been after this card for years and you have it. Here's an offer I won't repeat."),
    "tcgshop.onb.collector.value": ("Valor de mercado: %s", "Market value: %s"),
    "tcgshop.onb.collector.offer": ("Te ofrece: %s", "He offers: %s"),
    "tcgshop.onb.collector.once": (
        "Solo te visitará esta vez: si la rechazas, no volverá.",
        "He only visits once: if you decline, he won't come back."),
    "tcgshop.onb.collector.accept_btn": ("Vender", "Sell"),
    "tcgshop.onb.collector.decline_btn": ("No, gracias", "No, thanks"),
    "tcgshop.onb.collector.need_mod": (
        "Le encanta tu %1$s y te ofrece %2$s, pero necesitas el add-on en tu cliente para responderle.",
        "He loves your %1$s and offers %2$s, but you need the add-on on your client to answer him."),
    "tcgshop.onb.collector.declined": (
        "Has rechazado la oferta. El coleccionista se marcha... para siempre.",
        "You turned the offer down. The collector leaves... for good."),
    "tcgshop.onb.collector.missing": ("Ya no tienes esa carta.", "You don't have that card anymore."),
    "tcgshop.onb.collector.pay_failed": (
        "No se pudo completar el pago; tu carta vuelve a ti.",
        "The payment could not be completed; your card is back with you."),
    "tcgshop.onb.collector.sold": ("Vendiste %1$s al coleccionista por %2$s.", "You sold %1$s to the collector for %2$s."),

    "tcgshop.onb.collector.arrive.0": ("¡Por fin te encuentro! Llevo días siguiéndote la pista.", "Finally, I found you! I've been on your trail for days."),
    "tcgshop.onb.collector.arrive.1": ("Disculpa, joven... ¿tienes un minuto para un viejo coleccionista?", "Excuse me, youngster... got a minute for an old collector?"),
    "tcgshop.onb.collector.arrive.2": ("Me han dicho que abres sobres como nadie. Vengo a comprobarlo.", "They told me you open packs like nobody else. I came to see for myself."),
    "tcgshop.onb.collector.arrive.3": ("No suelo dejarme ver, pero hoy hago una excepción contigo.", "I don't usually show myself, but today I'm making an exception for you."),
    "tcgshop.onb.collector.arrive.4": ("Mis rodillas ya no están para caminatas... ¡pero valía la pena!", "My knees aren't made for long walks anymore... but it was worth it!"),

    "tcgshop.onb.collector.like.0": ("Tienes %1$s... ¡la carta que me falta! Te doy %2$s por ella.", "You have %1$s... the card I'm missing! I'll give you %2$s for it."),
    "tcgshop.onb.collector.like.1": ("Ay, %1$s... me recuerda a mi juventud. %2$s y es tuya... digo, mía.", "Ah, %1$s... reminds me of my youth. %2$s and it's yours... I mean, mine."),
    "tcgshop.onb.collector.like.2": ("Mi colección no está completa sin %1$s. ¿Qué tal %2$s?", "My collection isn't complete without %1$s. How about %2$s?"),
    "tcgshop.onb.collector.like.3": ("Haz clic en mí y hablamos de %1$s. Ofrezco %2$s, ni una moneda más.", "Click on me and let's talk about %1$s. I'm offering %2$s, not a coin more."),
    "tcgshop.onb.collector.like.4": ("He soñado con %1$s tres noches seguidas. %2$s, ¿trato hecho?", "I've dreamed about %1$s three nights in a row. %2$s, deal?"),

    "tcgshop.onb.collector.accept.0": ("¡Maravilloso! Esta va directa a mi vitrina de honor.", "Wonderful! This one goes straight to my hall of fame cabinet."),
    "tcgshop.onb.collector.accept.1": ("Un placer hacer negocios contigo. Cuídate, coleccionista.", "A pleasure doing business with you. Take care, collector."),
    "tcgshop.onb.collector.accept.2": ("¡Por fin! Ya puedo jubilarme tranquilo.", "Finally! Now I can retire in peace."),
    "tcgshop.onb.collector.accept.3": ("Gasta bien ese dinero... ¡en más sobres, claro!", "Spend that money wisely... on more packs, of course!"),
    "tcgshop.onb.collector.accept.4": ("Mi colección te lo agradecerá siempre. ¡Adiós!", "My collection will be forever grateful. Farewell!"),

    "tcgshop.onb.collector.decline.0": ("Lo entiendo... hay cartas que no se venden. Adiós.", "I understand... some cards are not for sale. Farewell."),
    "tcgshop.onb.collector.decline.1": ("Qué lástima. Bueno, buscaré otra en otro mundo.", "What a pity. Well, I'll look for another one in another world."),
    "tcgshop.onb.collector.decline.2": ("Respeto a quien quiere a sus cartas. Que te traiga suerte.", "I respect someone who loves their cards. May it bring you luck."),
    "tcgshop.onb.collector.decline.3": ("Ay... mis rodillas y yo nos volvemos por donde vinimos.", "Oh well... my knees and I will head back the way we came."),
    "tcgshop.onb.collector.decline.4": ("Una oferta así no se repite, pero tú sabrás. ¡Suerte!", "An offer like this won't come again, but it's your call. Good luck!"),

    "tcgshop.onb.collector.gone.0": ("¿Eh? ¿Ya no tienes esa carta? Qué rápido vuelan...", "Huh? You don't have that card anymore? They fly away so fast..."),
    "tcgshop.onb.collector.gone.1": ("Vaya, juraría que la tenías hace un momento.", "Huh, I could swear you had it a moment ago."),
    "tcgshop.onb.collector.gone.2": ("Sin cartas no hay trato, joven. Otra vez será... o no.", "No cards, no deal, youngster. Maybe another time... or not."),

    "tcgshop.onb.collector.other.0": ("Lo siento, vengo a ver a otra persona.", "Sorry, I came to see someone else."),
    "tcgshop.onb.collector.other.1": ("Hoy no le compro nada a nadie más.", "I'm not buying from anyone else today."),
    "tcgshop.onb.collector.other.2": ("Tú no eres a quien busco... pero bonita mochila.", "You're not who I'm looking for... nice backpack, though."),

    "tcgshop.onb.collector.idle_leave.0": ("Veo que estás ocupado. Me marcho... no volverás a verme.", "I see you're busy. I'll be going... you won't see me again."),
    "tcgshop.onb.collector.idle_leave.1": ("El tiempo de un viejo vale oro. ¡Adiós!", "An old man's time is worth gold. Goodbye!"),
    "tcgshop.onb.collector.idle_leave.2": ("Ofertas como la mía no esperan para siempre.", "Offers like mine don't wait forever."),

    "tcgshop.onb.collector.wait.0": ("Clic derecho y te enseño mi oferta.", "Right-click me and I'll show you my offer."),
    "tcgshop.onb.collector.wait.1": ("No tengo prisa... bueno, un poco sí.", "I'm in no hurry... well, maybe a little."),
    "tcgshop.onb.collector.wait.2": ("¿Y bien? ¿Hacemos trato?", "So? Do we have a deal?"),
    "tcgshop.onb.collector.wait.3": ("Esa carta me está llamando, ¿no la oyes?", "That card is calling me, can't you hear it?"),

    # ------------------------------------------------------------------ taunting rival
    "tcgshop.onb.taunter.name": ("Ricardo «el Rico»", "Rich Rick"),
    "tcgshop.onb.taunter.arrived": (
        "Alguien con muchos aires de grandeza viene hacia ti...",
        "Someone with a very big ego is heading your way..."),
    "tcgshop.onb.taunter.card": ("%1$s te ha tirado %2$s (vale unos %3$s).", "%1$s tossed you %2$s (worth about %3$s)."),
    "tcgshop.onb.taunter.hint": (
        "Pista: el Mayorista vende bloques de tienda. Coloca uno para reclamar tu zona y abrir tu propia tienda.",
        "Tip: the Wholesaler sells shop blocks. Place one to claim your zone and open your own shop."),

    "tcgshop.onb.taunter.intro.0": ("Vaya, vaya... ¿tú eres el famoso coleccionista sin tienda?", "Well, well... so you're the famous collector without a shop?"),
    "tcgshop.onb.taunter.intro.1": ("¡Mira quién está aquí! Todavía sin tienda, ¿eh?", "Look who it is! Still no shop, huh?"),
    "tcgshop.onb.taunter.intro.2": ("Me han hablado de ti. Dicen que llevas días dando vueltas sin montar nada.", "I've heard about you. They say you've been wandering around for days without setting anything up."),
    "tcgshop.onb.taunter.intro.3": ("Yo a tu edad ya tenía tres tiendas. Y una estatua.", "At your age I already had three shops. And a statue."),
    "tcgshop.onb.taunter.intro.4": ("Huelo a sobres sin abrir y a falta de ambición...", "I smell unopened packs and a lack of ambition..."),
    "tcgshop.onb.taunter.intro.5": ("Pasaba por aquí con la cartera llena y me acordé de ti.", "I was passing by with my wallet full and thought of you."),

    "tcgshop.onb.taunter.throw.0": ("Toma, para que te animes. A mí me sobran.", "Here, to cheer you up. I've got plenty."),
    "tcgshop.onb.taunter.throw.1": ("¡Atrapa! Considéralo caridad.", "Catch! Consider it charity."),
    "tcgshop.onb.taunter.throw.2": ("Esta carta la usaba de posavasos. Toda tuya.", "I was using this card as a coaster. All yours."),
    "tcgshop.onb.taunter.throw.3": ("Ahí va una cartita. No te la gastes toda en pan.", "There goes a little card. Don't spend it all on bread."),
    "tcgshop.onb.taunter.throw.4": ("Para mí es calderilla. Para ti, un sueño.", "For me it's pocket change. For you, a dream."),
    "tcgshop.onb.taunter.throw.5": ("Toma, que me da penita verte así.", "Here, it pains me to see you like this."),

    "tcgshop.onb.taunter.beggar.0": ("¡Ay, %s, pareces un mendigo de cartas!", "Oh, %s, you look like a card beggar!"),
    "tcgshop.onb.taunter.beggar.1": ("Sin tienda, sin clientes... eres un mendigo con mochila.", "No shop, no customers... you're a beggar with a backpack."),
    "tcgshop.onb.taunter.beggar.2": ("¿Eso que llevas es un binder o la limosna del día, mendigo?", "Is that a binder you're carrying or today's handouts, beggar?"),
    "tcgshop.onb.taunter.beggar.3": ("Mendigo, mendigo... ¡qué bien te queda el título!", "Beggar, beggar... the title suits you so well!"),
    "tcgshop.onb.taunter.beggar.4": ("Hasta el mendigo de cartas de mi barrio tiene más ambición que tú.", "Even the card beggar in my neighborhood has more ambition than you."),
    "tcgshop.onb.taunter.beggar.5": ("Tranquilo, mendigo, no se lo diré a nadie... bueno, a casi nadie.", "Relax, beggar, I won't tell anyone... well, almost anyone."),
    "tcgshop.onb.taunter.beggar.6": ("Mendigo de día, mendigo de noche. ¿Cuándo vas a ser empresario?", "Beggar by day, beggar by night. When are you going to be a businessperson?"),
    "tcgshop.onb.taunter.beggar.7": ("No te ofendas, %s, pero hasta mi Pokémon de compañía gana más que tú, mendigo.", "No offense, %s, but even my partner Pokémon earns more than you, beggar."),

    "tcgshop.onb.taunter.push.0": ("Monta una tienda de una vez. ¡Las cartas no se venden solas!", "Open a shop already. Cards don't sell themselves!"),
    "tcgshop.onb.taunter.push.1": ("Consigue un bloque de tienda, reclama tu zona y llena los expositores. No es tan difícil.", "Get a shop block, claim your zone and fill the displays. It's not that hard."),
    "tcgshop.onb.taunter.push.2": ("Cuando tengas tienda, ven a competir conmigo... si te atreves.", "When you have a shop, come compete with me... if you dare."),
    "tcgshop.onb.taunter.push.3": ("El Mayorista vende bloques de tienda. Hasta tú podrías permitirte uno.", "The Wholesaler sells shop blocks. Even you could afford one."),
    "tcgshop.onb.taunter.push.4": ("Vende esa carta, cómprate una tienda y deja de dar penita.", "Sell that card, buy yourself a shop and stop being so pitiful."),
    "tcgshop.onb.taunter.push.5": ("Un día de estos me contarán que abriste tienda... y no me lo voy a creer.", "One of these days they'll tell me you opened a shop... and I won't believe it."),
    "tcgshop.onb.taunter.push.6": ("Una tienda, unos expositores y un poco de cara. ¡Es todo lo que necesitas!", "A shop, some displays and a bit of nerve. That's all you need!"),
    "tcgshop.onb.taunter.push.7": ("Demuéstrame que me equivoco: abre tu tienda y hazte rico.", "Prove me wrong: open your shop and get rich."),

    "tcgshop.onb.taunter.leave.0": ("Bueno, me voy, que mi tienda no se atiende sola. ¡Chao, mendigo!", "Well, I'm off, my shop doesn't run itself. Bye, beggar!"),
    "tcgshop.onb.taunter.leave.1": ("Tengo clientes esperando. ¿Tú no? Ah, claro...", "I have customers waiting. Don't you? Oh, right..."),
    "tcgshop.onb.taunter.leave.2": ("Nos vemos en la cima... bueno, yo te veré desde allí.", "See you at the top... well, I'll see you from up there."),
    "tcgshop.onb.taunter.leave.3": ("No me des las gracias. Dámelas cuando tengas tienda.", "Don't thank me. Thank me when you have a shop."),
    "tcgshop.onb.taunter.leave.4": ("¡Adiós! Y quita esa cara de sobre sin abrir.", "Bye! And wipe off that unopened-pack face."),

    "tcgshop.onb.taunter.poke.0": ("¿Qué? ¿Quieres otra? Ni lo sueñes.", "What? You want another one? Dream on."),
    "tcgshop.onb.taunter.poke.1": ("No toques, que esta chaqueta vale más que tu inventario.", "Hands off, this jacket is worth more than your inventory."),
    "tcgshop.onb.taunter.poke.2": ("Sí, sí, soy increíble. Lo sé.", "Yes, yes, I'm amazing. I know."),
    "tcgshop.onb.taunter.poke.3": ("Los autógrafos, cuando tengas tienda.", "Autographs once you have a shop."),
    "tcgshop.onb.taunter.poke.4": ("¿Me estás pidiendo limosna otra vez?", "Are you begging again?"),
    "tcgshop.onb.taunter.poke.5": ("Ojo, que la falta de ambición se pega.", "Careful, lack of ambition is contagious."),

    "tcgshop.onb.taunter.other.0": ("Tú no, vengo a reírme de otra persona.", "Not you, I came to laugh at someone else."),
    "tcgshop.onb.taunter.other.1": ("Hoy no reparto cartas a cualquiera.", "I'm not handing out cards to just anyone today."),
    "tcgshop.onb.taunter.other.2": ("Si tienes tienda, ya me caes mejor que al que vengo a ver.", "If you have a shop, I already like you more than the one I came to see."),
}

# Must match OnboardingVisits.LINES (category -> number of lines).
COUNTS = {
    "collector.arrive": 5, "collector.like": 5, "collector.accept": 5, "collector.decline": 5, "collector.gone": 3,
    "collector.other": 3, "collector.idle_leave": 3, "collector.wait": 4,
    "taunter.intro": 6, "taunter.throw": 6, "taunter.beggar": 8, "taunter.push": 8, "taunter.leave": 5,
    "taunter.poke": 6, "taunter.other": 3,
}


def check():
    for cat, n in COUNTS.items():
        for i in range(n):
            k = f"tcgshop.onb.{cat}.{i}"
            assert k in T, "missing line " + k
        assert f"tcgshop.onb.{cat}.{n}" not in T, "extra line in " + cat


def main():
    check()
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
