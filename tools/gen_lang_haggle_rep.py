"""Texts for the new haggling menu (DealScreen) and lines of the haggling customer when YOU sell to them
(tcgshop.haggle.buy.<cat>.<n>; the numbers must match CustomerEntity.BUY_LINES)."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ---- NPC panel
    "tcgshop.haggle.patience": ("Paciencia", "Patience"),
    "tcgshop.haggle.mood.0": ("Furioso", "Furious"),
    "tcgshop.haggle.mood.1": ("Molesto", "Annoyed"),
    "tcgshop.haggle.mood.2": ("Indeciso", "Unsure"),
    "tcgshop.haggle.mood.3": ("Interesado", "Interested"),
    "tcgshop.haggle.mood.4": ("Encantado", "Delighted"),
    "tcgshop.haggle.buyer.collector": ("Coleccionista", "Collector"),
    "tcgshop.haggle.buyer.beginner": ("Novato", "Newcomer"),
    "tcgshop.haggle.buyer.expert": ("Experto", "Expert"),
    "tcgshop.haggle.buyer.negotiator": ("Negociador", "Negotiator"),
    "tcgshop.haggle.buyer.desperate": ("Con prisa", "In a hurry"),
    "tcgshop.haggle.buyer.casual": ("Cliente casual", "Casual buyer"),
    # ---- prices and margin
    "tcgshop.haggle.market_diff": ("Mercado: %s (%s)", "Market: %s (%s)"),
    "tcgshop.haggle.margin_buy": ("Margen al revender: %s", "Resale margin: %s"),
    "tcgshop.haggle.margin_sell": ("Frente al mercado: %s", "Versus market: %s"),
    "tcgshop.haggle.rep_line": ("Reputación %s · pagan %s", "Reputation %s · they pay %s"),
    "tcgshop.haggle.rep_target": ("Objetivo %s · techo %s", "Target %s · cap %s"),
    "tcgshop.haggle.history": ("Historial", "History"),
    "tcgshop.haggle.h.you": ("Tú:", "You:"),
    "tcgshop.haggle.h.them": ("NPC:", "NPC:"),
    "tcgshop.haggle.h.open_sell": ("pide %s", "asks %s"),
    "tcgshop.haggle.h.open_buy": ("ofrece %s", "offers %s"),
    "tcgshop.haggle.h.you_offer": ("ofreces %s", "you offer %s"),
    "tcgshop.haggle.h.you_ask": ("pides %s", "you ask %s"),
    "tcgshop.haggle.h.arg.market": ("mercado", "market"),
    "tcgshop.haggle.h.arg.elsewhere": ("otro sitio", "elsewhere"),
    "tcgshop.haggle.h.arg.broke": ("sin dinero", "broke"),
    "tcgshop.haggle.h.arg.rep": ("fama", "reputation"),
    "tcgshop.haggle.h.arg.max": ("última", "final"),
    "tcgshop.haggle.h.arg.other": ("argumento", "argument"),
    "tcgshop.haggle.h.down": ("baja a %s", "drops to %s"),
    "tcgshop.haggle.h.up": ("sube a %s", "raises to %s"),
    "tcgshop.haggle.h.hold": ("se queda en %s", "holds at %s"),
    "tcgshop.haggle.h.insult": ("¡se ofende!", "is offended!"),
    "tcgshop.haggle.h.think": ("se lo piensa", "thinks it over"),
    "tcgshop.haggle.h.you_think": ("te lo piensas", "you think it over"),
    "tcgshop.haggle.h.accept": ("aceptas %s", "you accept %s"),
    "tcgshop.haggle.h.deal": ("¡trato! %s", "deal! %s"),
    "tcgshop.haggle.h.walk": ("se marcha", "walks away"),
    "tcgshop.haggle.h.reject": ("rechazado", "rejected"),
    "tcgshop.haggle.h.timeout": ("se cansó de esperar", "got tired of waiting"),
    # ---- your offer
    "tcgshop.haggle.your_offer": ("Tu oferta:", "Your offer:"),
    "tcgshop.haggle.your_ask": ("Tu precio:", "Your price:"),
    "tcgshop.haggle.of_market": ("= %s del mercado", "= %s of market"),
    "tcgshop.haggle.hint": ("Rueda o ↑↓: ±1%% · Escribe números para fijar tu precio · Intro para confirmar",
                            "Wheel or ↑↓: ±1%% · Type digits to set your price · Enter to confirm"),
    "tcgshop.haggle.tip.type": ("Haz clic o escribe números para poner el precio a mano. Intro confirma, Esc cancela.",
                                "Click or type digits to set the price by hand. Enter confirms, Esc cancels."),
    "tcgshop.haggle.tip.quick": ("Pone tu oferta en ese porcentaje del precio de mercado.", "Sets your offer to that share of the market price."),
    "tcgshop.haggle.tip.accept": ("Cierras el trato al precio actual. Deja al NPC contento.", "Close the deal at the current price. Leaves the NPC happy."),
    "tcgshop.haggle.tip.counter": ("Propones tu precio. Cada contraoferta gasta algo de su paciencia.", "Propose your price. Every counter costs some of their patience."),
    "tcgshop.haggle.tip.max": ("Última palabra: o acepta tu precio o se marcha. Sin término medio.", "Final word: they take your price or leave. No middle ground."),
    "tcgshop.haggle.tip.market": ("Argumentas con el precio de mercado. Funciona si tu cifra es razonable.", "Argue with the market price. Works if your number is reasonable."),
    "tcgshop.haggle.tip.elsewhere": ("Les dices que no encontrarán nada mejor. A los novatos les convence; a los expertos, no.",
                                     "Tell them they won't find better. Newcomers buy it; experts don't."),
    "tcgshop.haggle.tip.broke": ("Dices que no puedes pagar más. Ablanda a novatos y vendedores ocasionales.", "Say you can't pay more. Softens newcomers and casual sellers."),
    "tcgshop.haggle.tip.rep": ("Presumes de la fama de tu tienda: cuanta más reputación, más dispuestos a pagar de más.",
                               "Brag about your shop's fame: the higher your reputation, the more they'll overpay."),
    "tcgshop.haggle.tip.think": ("Pides tiempo. A veces mejoran la oferta; si insistes, se cansan.", "Ask for time. Sometimes they improve the offer; push it and they tire."),
    "tcgshop.haggle.tip.no": ("Rechazas con educación. Casi siempre deja una reseña neutral.", "Decline politely. Usually leaves a neutral review."),
    "tcgshop.haggle.tip.rude": ("Rechazas de malas maneras. Lo más probable es una mala reseña.", "Decline rudely. Most likely a bad review."),
    # ---- responses
    "tcgshop.haggle.a.accept": ("✔ Aceptar · %s", "✔ Accept · %s"),
    "tcgshop.haggle.a.counter_buy": ("⇄ Ofrecer %s", "⇄ Offer %s"),
    "tcgshop.haggle.a.counter_sell": ("⇄ Pedir %s", "⇄ Ask %s"),
    "tcgshop.haggle.a.max": ("★ Última oferta: %s", "★ Final offer: %s"),
    "tcgshop.haggle.a.market_buy": ("«El mercado dice esto»", "«The market says so»"),
    "tcgshop.haggle.a.market_sell": ("«Vale eso en el mercado»", "«That's its market value»"),
    "tcgshop.haggle.a.elsewhere_buy": ("«Nadie te dará más»", "«Nobody will pay you more»"),
    "tcgshop.haggle.a.elsewhere_sell": ("«No lo verás en otra tienda»", "«You won't see it elsewhere»"),
    "tcgshop.haggle.a.broke": ("«No puedo pagar más»", "«I can't pay more»"),
    "tcgshop.haggle.a.rep": ("«Aquí pagas la calidad»", "«Here you pay for quality»"),
    "tcgshop.haggle.a.think": ("… Déjame pensarlo", "… Let me think"),
    "tcgshop.haggle.a.no": ("✖ No, gracias", "✖ No, thanks"),
    "tcgshop.haggle.a.rude": ("☠ ¡Vaya timo!", "☠ What a rip-off!"),
    # ---- outcome
    "tcgshop.haggle.out.deal_buy": ("¡Trato hecho! Lo compras por %s", "Deal! You buy it for %s"),
    "tcgshop.haggle.out.deal_sell": ("¡Trato hecho! Lo vendes por %s", "Deal! You sell it for %s"),
    "tcgshop.haggle.out.walk": ("Se ha ido sin trato", "They left without a deal"),
    "tcgshop.haggle.out.reject": ("Has rechazado el trato", "You turned the deal down"),
    "tcgshop.haggle.out.timeout": ("Se cansó de esperar", "They got tired of waiting"),
    "tcgshop.haggle.out.good": ("%s frente al mercado: ¡buen negocio!", "%s versus market: good business!"),
    "tcgshop.haggle.out.bad": ("%s frente al mercado: podría haber ido mejor", "%s versus market: could have gone better"),
    "tcgshop.haggle.out.sub.0": ("El trato ha terminado.", "The deal is over."),
    "tcgshop.haggle.out.sub.1": ("Queda contento con el trato.", "They're happy with the deal."),
    "tcgshop.haggle.out.sub.2": ("No os habéis entendido. Reseña neutral.", "You couldn't agree. Neutral review."),
    "tcgshop.haggle.out.sub.3": ("Recuerda: la educación evita malas reseñas.", "Remember: manners avoid bad reviews."),
    "tcgshop.haggle.out.sub.4": ("Atiende antes a quien quiere un trato.", "Serve people who want a deal sooner."),
    "tcgshop.haggle.click_close": ("Clic para cerrar", "Click to close"),
}

# lines of the haggling customer who wants to buy something from you (you ask for more)
BUY = {
    "counter": [
        ("Vale, subo a %s. Ni un céntimo más... de momento.", "Fine, I'll go up to %s. Not a penny more... for now."),
        ("Te puedo dar %s.", "I can give you %s."),
        ("¿Y si lo dejamos en %s?", "What if we settle at %s?"),
        ("Hmm... %s. Es lo que veo justo.", "Hmm... %s. That's what feels fair."),
        ("Me estiras el bolsillo. %s.", "You're stretching my wallet. %s."),
        ("Por esa carta haría un esfuerzo: %s.", "For that one I'd make an effort: %s."),
        ("%s y nos damos la mano.", "%s and we shake on it."),
        ("Subo a %s, pero no te acostumbres.", "I'll raise to %s, but don't get used to it."),
    ],
    "hold": [
        ("Mi oferta sigue siendo %s.", "My offer is still %s."),
        ("%s. No me muevo de ahí.", "%s. I'm not budging."),
        ("Ya te he dicho %s.", "I already said %s."),
        ("Sigo en %s, piénsalo.", "Still at %s, think about it."),
        ("No puedo pasar de %s ahora mismo.", "I can't go past %s right now."),
        ("%s es más que razonable.", "%s is more than reasonable."),
    ],
    "insulted": [
        ("¿Eso? ¡Ni que fuera de primera edición!", "That much? It's not like it's first edition!"),
        ("Venga ya, no me tomes el pelo.", "Come on, don't pull my leg."),
        ("Con ese precio podría comprarme la tienda entera.", "At that price I could buy the whole shop."),
        ("Eso es un atraco a mano armada.", "That's daylight robbery."),
        ("Me ofende que me pidas eso.", "I'm offended you'd ask that."),
        ("Ja. Ja. No.", "Ha. Ha. No."),
    ],
    "walkaway": [
        ("Mira, lo dejo. Suerte vendiéndolo.", "Look, I'm out. Good luck selling it."),
        ("No nos vamos a entender. Adiós.", "We won't agree. Bye."),
        ("Otro día, quizá.", "Another day, maybe."),
        ("Me voy a mirar a otro sitio.", "I'll go look somewhere else."),
        ("Se me han quitado las ganas.", "I've lost the urge."),
        ("Demasiado regateo para mí. Hasta luego.", "Too much haggling for me. See you."),
    ],
    "accept": [
        ("¡Hecho! Me lo llevo.", "Done! I'll take it."),
        ("Trato. Un placer hacer negocios contigo.", "Deal. A pleasure doing business."),
        ("Vale, vale, tú ganas. Es mío.", "Okay, okay, you win. It's mine."),
        ("Perfecto, ya tengo mi carta.", "Perfect, I've got my card."),
        ("Me parece justo. ¡Gracias!", "Seems fair. Thanks!"),
        ("Aquí tienes el dinero. ¡Qué ilusión!", "Here's the money. I'm so excited!"),
        ("Cerrado. Volveré a por más.", "Done. I'll be back for more."),
        ("Lo quería sí o sí. Hecho.", "I wanted it no matter what. Done."),
    ],
    "arg_good": [
        ("Bueno, tienes razón en eso.", "Well, you have a point."),
        ("Sí, lo he visto más caro por ahí.", "Yeah, I've seen it pricier elsewhere."),
        ("Es verdad, no es fácil de encontrar.", "True, it's not easy to find."),
        ("Me has convencido un poco.", "You've convinced me a little."),
        ("Vale, lo reconozco.", "Okay, I'll admit it."),
    ],
    "arg_bad": [
        ("No me vengas con esas.", "Don't give me that."),
        ("Sé perfectamente lo que vale.", "I know exactly what it's worth."),
        ("Eso no cuela conmigo.", "That doesn't work on me."),
        ("He mirado los precios antes de venir.", "I checked prices before coming."),
        ("Ese argumento no me sirve.", "That argument won't fly."),
    ],
    "rep_good": [
        ("Es cierto, esta tienda tiene fama. Lo pago con gusto.", "True, this shop has a name. I'll gladly pay."),
        ("Aquí todo está impecable, eso se paga.", "Everything here is spotless; that's worth paying for."),
        ("Mis amigos hablan maravillas de este sitio.", "My friends rave about this place."),
        ("Comprar aquí da confianza, sí.", "Buying here feels trustworthy, yes."),
        ("Por una tienda así, merece la pena.", "For a shop like this, it's worth it."),
        ("Las reseñas no mienten. Vale.", "The reviews don't lie. Fine."),
    ],
    "rep_bad": [
        ("¿Calidad? He visto tiendas mejores.", "Quality? I've seen better shops."),
        ("Con la fama que tiene este sitio, ni hablar.", "With this place's reputation? No way."),
        ("Las reseñas dicen otra cosa.", "The reviews say otherwise."),
        ("Primero mejora la tienda y luego hablamos.", "Improve the shop first, then we'll talk."),
        ("Eso no justifica ese precio.", "That doesn't justify the price."),
    ],
    "think_raise": [
        ("Mientras lo piensas... te subo a %s.", "While you think... I'll raise to %s."),
        ("Para ayudarte a decidir: %s.", "To help you decide: %s."),
        ("Venga, %s y no le des más vueltas.", "Come on, %s and stop overthinking."),
        ("Te lo pongo fácil: %s.", "I'll make it easy: %s."),
        ("Vale, %s. Mi mejor esfuerzo.", "Okay, %s. My best effort."),
    ],
    "think_same": [
        ("Tómate tu tiempo, pero no mucho.", "Take your time, but not too long."),
        ("Te espero un momento.", "I'll wait a moment."),
        ("Mi oferta sigue en pie.", "My offer still stands."),
        ("No hay prisa... bueno, un poco sí.", "No rush... well, a little."),
        ("Piénsalo, yo no me muevo.", "Think it over, I'm not moving."),
    ],
    "final_no": [
        ("Entonces no hay trato.", "Then there's no deal."),
        ("Si eso es lo último, me voy.", "If that's final, I'm leaving."),
        ("No llego a tanto. Lo siento.", "I can't reach that. Sorry."),
        ("Así no me compensa.", "It's not worth it for me."),
        ("Pues me quedo sin carta.", "Then I'll go without the card."),
    ],
}


def main():
    entries = dict(T)
    for cat, lines in BUY.items():
        for i, (es, en) in enumerate(lines):
            entries[f"tcgshop.haggle.buy.{cat}.{i}"] = (es, en)
    for lang, idx in (("es_es", 0), ("en_us", 1)):
        path = LANG / f"{lang}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        for k, v in entries.items():
            data[k] = v[idx]
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(entries)} keys written")


if __name__ == "__main__":
    main()
