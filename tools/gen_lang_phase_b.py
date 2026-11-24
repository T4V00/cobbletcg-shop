"""Phase B texts: editing display case cards, two day-shift cashiers, beggar and black marketeer."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

N = {
    "cashier_bye": [("¡Se acabó mi turno! Hasta mañana.", "My shift's over! See you tomorrow."),
                    ("Me voy a casa, que ya es de noche.", "I'm heading home, it's night already."),
                    ("Cierro caja por hoy. ¡Buenas noches!", "Closing the till for today. Good night!"),
                    ("Mañana a primera hora vuelvo.", "I'll be back first thing tomorrow."),
                    ("Turno terminado. Te toca a ti, jefe.", "Shift done. Your turn, boss.")],
    "beg_hello": [("¿Tienes una moneda para un fan del TCG?", "Spare a coin for a TCG fan?"),
                  ("Hola... ¿me ayudas un poquito?", "Hi... could you help me a little?"),
                  ("Lo perdí todo abriendo sobres. ¿Una ayudita?", "I lost everything opening packs. A little help?"),
                  ("Cualquier carta me vale, de verdad.", "Any card will do, really."),
                  ("No pido mucho, sólo para un sobre.", "I'm not asking much, just enough for a pack."),
                  ("Antes tenía una colección enorme...", "I used to have a huge collection..."),
                  ("¿Te sobra alguna común? Las colecciono.", "Got a spare common? I collect them."),
                  ("Prometo que no me lo gasto en sobres... bueno, quizás uno.", "I promise I won't spend it on packs... well, maybe one.")],
    "beg_ambient": [("Una monedita para un coleccionista...", "A coin for a collector..."),
                    ("Mi binder está vacío desde hace meses.", "My binder has been empty for months."),
                    ("Cambio historias de torneos por una carta.", "I'll trade tournament stories for a card."),
                    ("Algún día tendré mi propia tienda.", "Someday I'll have my own shop."),
                    ("Ojalá me tocara un Charizard...", "I wish I'd pull a Charizard..."),
                    ("He dormido en sitios peores que esta esquina.", "I've slept in worse places than this corner."),
                    ("¿Alguien quiere oír cómo perdí mi colección?", "Anyone want to hear how I lost my collection?"),
                    ("Cualquier ayuda cuenta.", "Every little bit helps.")],
    "beg_thanks": [("¡Gracias! Eres muy amable.", "Thanks! You're very kind."),
                   ("No lo olvidaré.", "I won't forget this."),
                   ("Hoy como caliente gracias a ti.", "I'll eat a hot meal today thanks to you."),
                   ("¡Que te salgan holos en todos los sobres!", "May every pack you open have a holo!"),
                   ("Les diré a todos lo buena que es tu tienda.", "I'll tell everyone how good your shop is."),
                   ("Gracias, de corazón.", "Thank you, from the heart."),
                   ("Eres de los pocos que ayudan.", "You're one of the few who help."),
                   ("¡Mil gracias!", "Thanks a million!")],
    "beg_thanks_big": [("¡¿Tanto?! ¡Eres increíble!", "That much?! You're amazing!"),
                       ("No sé ni qué decir... ¡gracias!", "I don't even know what to say... thank you!"),
                       ("¡Con esto me compro una caja!", "With this I can buy a box!"),
                       ("Te debo una enorme.", "I owe you big time."),
                       ("¡La mejor tienda del mundo!", "The best shop in the world!")],
    "beg_card_love": [("¡Una carta! ¡Me encanta!", "A card! I love it!"),
                      ("Va directa a mi binder. Gracias.", "Straight into my binder. Thanks."),
                      ("¡Hacía tanto que no tenía una nueva!", "It's been so long since I had a new one!"),
                      ("La guardaré como un tesoro.", "I'll keep it like treasure."),
                      ("¡Esto es mejor que el dinero!", "This is better than money!")],
    "beg_polite_no": [("Lo entiendo, gracias igualmente.", "I understand, thanks anyway."),
                      ("No pasa nada. Que tengas buen día.", "No worries. Have a nice day."),
                      ("Vale, otro día será.", "Okay, maybe another day."),
                      ("Gracias por escucharme al menos.", "Thanks for at least listening."),
                      ("Suerte con la tienda.", "Good luck with the shop.")],
    "beg_rude": [("Vaya... no hacía falta.", "Wow... that wasn't necessary."),
                 ("Ya me voy, ya me voy.", "I'm going, I'm going."),
                 ("Algún día estarás tú en mi lugar.", "Someday you'll be in my place."),
                 ("Qué poca empatía.", "How little empathy."),
                 ("Se lo contaré a los del barrio.", "I'll tell the neighbourhood about this.")],
    "beg_gift": [("Toma, quiero devolverte el favor.", "Here, I want to return the favour."),
                 ("Esto lo guardaba para alguien especial.", "I was saving this for someone special."),
                 ("Para ti. Me trajiste suerte.", "For you. You brought me luck."),
                 ("Lo encontré por ahí. Te lo mereces.", "I found it around. You deserve it.")],
    "bm_hello": [("Psst... ¿te interesa algo exclusivo?", "Psst... interested in something exclusive?"),
                 ("Un sobre sellado. Sin preguntas.", "A sealed pack. No questions asked."),
                 ("No acepto dinero. Sólo Pokémon.", "I don't take money. Only Pokémon."),
                 ("Elige bien. No vuelvo dos veces.", "Choose wisely. I don't come back twice."),
                 ("Este sobre no lo encontrarás en ninguna tienda.", "You won't find this pack in any shop."),
                 ("Uno de tu equipo por esta maravilla.", "One of your team for this wonder.")],
    "bm_ambient": [("No me has visto.", "You haven't seen me."),
                   ("El tiempo corre...", "Time is ticking..."),
                   ("Tengo lo que nadie tiene.", "I have what no one has."),
                   ("Nadie sabe de dónde salen mis sobres.", "Nobody knows where my packs come from."),
                   ("¿Trato o no trato?", "Deal or no deal?")],
    "bm_deal": [("Un placer hacer negocios.", "A pleasure doing business."),
                ("Disfruta del sobre. Yo me esfumo.", "Enjoy the pack. I'm vanishing."),
                ("Buena elección...", "Good choice..."),
                ("Nunca me viste.", "You never saw me."),
                ("Hasta la próxima... si la hay.", "Until next time... if there is one.")],
    "bm_no": [("Vuelve cuando tengas más Pokémon.", "Come back when you have more Pokémon."),
              ("No te dejaré sin compañero. Así no.", "I won't leave you without a partner. Not like that."),
              ("No tienes nada que me interese.", "You have nothing I want."),
              ("Otra vez será.", "Maybe another time.")],
    "bm_leave": [("Ya tienes lo tuyo.", "You've got yours."),
                 ("El trato está hecho. Me voy.", "The deal is done. I'm leaving."),
                 ("No hay más sobres para ti.", "No more packs for you."),
                 ("Desaparezco...", "I'm disappearing...")],
}

T = {}
for cat, pairs in N.items():
    for i, (es, en) in enumerate(pairs):
        T[f"tcgshop.npc.{cat}.{i}"] = (es, en)

T.update({
    # display cases
    "tcgshop.slot.title": ("Carta del expositor", "Display card"),
    "tcgshop.slot.edit_title": ("Editar carta", "Edit card"),
    "tcgshop.slot.price_now": ("Precio: %s", "Price: %s"),
    "tcgshop.slot.take": ("Tomar carta", "Take card"),
    "tcgshop.slot.edit": ("Editar carta", "Edit card"),
    "tcgshop.slot.price": ("Precio", "Price"),
    "tcgshop.slot.market_price": ("Precio mercado", "Market price"),
    "tcgshop.slot.size": ("Tamaño", "Size"),
    "tcgshop.slot.rotate": ("Giro %s", "Rotate %s"),
    "tcgshop.slot.reset": ("Restaurar", "Reset"),
    "tcgshop.slot.save": ("Guardar", "Save"),
    "tcgshop.slot.saved": ("Cambios guardados.", "Changes saved."),
    # staff
    "tcgshop.role.cashier2": ("Cajero 2", "Cashier 2"),
    "tcgshop.role.beggar": ("Mendigo de cartas", "Card beggar"),
    "tcgshop.role.black_market": ("Mercader negro", "Black marketeer"),
    "tcgshop.staff.starts_morning": ("%s empieza mañana al amanecer (los cajeros sólo trabajan de día).", "%s starts tomorrow at dawn (cashiers only work during the day)."),
    "tcgshop.staff.shift_start": ("Amanece: tus cajeros abren la caja.", "Dawn: your cashiers open the till."),
    "tcgshop.staff.shift_end": ("Anochece: tus cajeros se van a casa. Si quieres vender sobres y cajas, atiende tú la caja registradora.", "Dusk: your cashiers go home. To sell packs and boxes, stand at the cash register yourself."),
    "tcgshop.shop.no_cashier_night": ("De noche los cajeros no trabajan: el dueño tiene que estar en la caja.", "Cashiers don't work at night: the owner must be at the register."),
    "tcgshop.phone.shift_day": ("☀ Turno de día: los cajeros están en sus cajas (6:00-18:30).", "☀ Day shift: cashiers are at their tills (6:00-18:30)."),
    "tcgshop.phone.shift_night": ("☾ De noche: los cajeros están en casa. Para vender sobres y cajas, quédate junto a una caja registradora.", "☾ Night: cashiers are home. To sell packs and boxes, stay next to a cash register."),
    "tcgshop.phone.staff_tip2": ("Puedes tener dos cajeros (uno por caja registradora; pon dos cajas) y un guardia, que trabaja día y noche. Los sueldos salen de la caja cada día.", "You can have two cashiers (one per cash register; place two) and a guard, who works day and night. Wages come out of the till every day."),
    # reputation
    "tcgshop.rep.charity": ("Trato al mendigo", "Treatment of the beggar"),
    "tcgshop.rep.tip.charity": ("Consejo: el mendigo cuenta por el barrio cómo le tratas. Ayudarle mejora la fama de tus tiendas.", "Tip: the beggar tells the neighbourhood how you treat him. Helping improves your shops' reputation."),
    # beggar
    "tcgshop.beggar.title": ("Mendigo de cartas", "Card beggar"),
    "tcgshop.beggar.ask": ("«Perdí toda mi colección... ¿me ayudas con unas monedas o una carta?»", "\"I lost my whole collection... could you help with some coins or a card?\""),
    "tcgshop.beggar.give_card": ("Darle la carta que llevo en la mano", "Give him the card in my hand"),
    "tcgshop.beggar.hold_card": ("(Lleva una carta en la mano para regalársela)", "(Hold a card in your hand to gift it)"),
    "tcgshop.beggar.polite": ("Lo siento, hoy no", "Sorry, not today"),
    "tcgshop.beggar.rude": ("¡Lárgate de aquí!", "Get out of here!"),
    "tcgshop.beggar.hint": ("Ayudarle mejora la fama de tus tiendas", "Helping him improves your shops' reputation"),
    "tcgshop.beggar.arrived": ("Un mendigo de cartas ronda por la zona.", "A card beggar is wandering nearby."),
    "tcgshop.beggar.karma_up": ("La gente del barrio habla bien de ti.", "The neighbourhood speaks well of you."),
    "tcgshop.beggar.karma_down": ("La gente del barrio ha visto cómo le tratas...", "The neighbourhood saw how you treated him..."),
    "tcgshop.beggar.gift": ("El mendigo te devuelve el favor: %s", "The beggar returns the favour: %s"),
    # black marketeer
    "tcgshop.bm.name": ("Mercader encapuchado", "Hooded merchant"),
    "tcgshop.bm.title": ("Mercado negro", "Black market"),
    "tcgshop.bm.rare": ("¡SET LEGENDARIO!", "LEGENDARY SET!"),
    "tcgshop.bm.pick": ("Elige qué Pokémon le das:", "Choose which Pokémon to give:"),
    "tcgshop.bm.offer": ("Entregarías a %s.", "You would hand over %s."),
    "tcgshop.bm.sure": ("¿Seguro? %s se irá para siempre a cambio de %s.", "Sure? %s will be gone forever in exchange for %s."),
    "tcgshop.bm.confirm": ("Sí, cambiar", "Yes, trade"),
    "tcgshop.bm.trade": ("Cambiar", "Trade"),
    "tcgshop.bm.choose": ("Elige un Pokémon de tu equipo.", "Pick a Pokémon from your team."),
    "tcgshop.bm.leave": ("Irme", "Leave"),
    "tcgshop.bm.arrived": ("Sientes que alguien te observa... Un mercader encapuchado ha aparecido muy cerca. No se quedará mucho.", "You feel someone watching you... A hooded merchant has appeared very close. He won't stay long."),
    "tcgshop.bm.need_two": ("Necesitas al menos dos Pokémon en el equipo para cambiar uno.", "You need at least two Pokémon in your team to trade one."),
    "tcgshop.bm.failed": ("El intercambio no se pudo hacer. No has perdido nada.", "The trade couldn't be done. You lost nothing."),
    "tcgshop.bm.done": ("El mercader se lleva a tu Pokémon y te entrega %s.", "The merchant takes your Pokémon and hands you %s."),
    "entity.tcgshop.beggar": ("Mendigo de cartas", "Card beggar"),
    "entity.tcgshop.black_market": ("Mercader negro", "Black marketeer"),
})


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
