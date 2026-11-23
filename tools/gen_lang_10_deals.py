"""1.0 texts for deals: customer tips (deal window, NPC lines, owner notices) and the wandering Collector's
multi-card trade (several of your cards for one of his).

NPC line counts must match entity/NpcLines.COUNTS: tip_offer 14, tip_accept 14, tip_checkout 16 (%s = the tip).

Usage: python tools/gen_lang_10_deals.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

# ---------------------------------------------------------------- NPC lines (es, en)
TIP_OFFER = [  # haggling customer offering above your asking price (speech bubble and deal window)
    ("Tu precio me parece bien, pero te doy %s más de propina.", "Your price is fine, but I'll add %s on top as a tip."),
    ("Te pago lo que pides y %s de propina. ¡Te lo has ganado!", "I'll pay what you ask plus a %s tip. You've earned it!"),
    ("Añado %s de propina. Tiendas así hay que cuidarlas.", "I'm adding a %s tip. Shops like this need looking after."),
    ("Lo que marca la etiqueta y %s de propina por el buen trato.", "The tag price and a %s tip for the good service."),
    ("No regateo: te doy tu precio y %s extra de propina.", "No haggling: your price and an extra %s as a tip."),
    ("Me encanta esta tienda. Pon %s de propina en la cuenta.", "I love this shop. Put a %s tip on the bill."),
    ("Esos %s de más son propina, que conste.", "Those extra %s are a tip, just so you know."),
    ("Te ofrezco tu precio y %s de propina. ¿Trato?", "I'm offering your price plus a %s tip. Deal?"),
    ("Llevo días buscándola. Toma %s de propina encima.", "I've been after this for days. Here's a %s tip on top."),
    ("Por cómo atiendes, %s de propina es lo mínimo.", "For service like yours, a %s tip is the least I can do."),
    ("Hoy estoy generoso: tu precio más %s de propina.", "I'm feeling generous today: your price plus a %s tip."),
    ("Cobra tu precio y quédate %s de propina.", "Charge your price and keep %s as a tip."),
    ("Pago de más a propósito: %s de propina para la tienda.", "I'm overpaying on purpose: a %s tip for the shop."),
    ("Con %s de propina, espero que me guardes las próximas novedades.", "With a %s tip, I hope you'll save me the next new arrivals."),
]

TIP_ACCEPT = [  # deal closed above your asking price
    ("¡Trato! Y los %s de más son propina, quédatelos.", "Deal! And the extra %s is a tip, keep it."),
    ("Perfecto. Esos %s extra, de propina.", "Perfect. That extra %s is your tip."),
    ("Aquí tienes, con %s de propina incluidos.", "Here you go, with a %s tip included."),
    ("Me lo llevo. %s de propina por tratarme tan bien.", "I'll take it. A %s tip for treating me so well."),
    ("Gracias. No me devuelvas nada: %s de propina.", "Thanks. No change needed: %s is your tip."),
    ("Hecho. La propina de %s es por tu buena fama.", "Done. The %s tip is for your good reputation."),
    ("¡Qué ilusión! Toma, y %s de propina.", "So excited! Here, and a %s tip."),
    ("Trato cerrado. Te dejo %s de propina, te lo mereces.", "Deal closed. I'm leaving you a %s tip, you deserve it."),
    ("Pago con gusto, y %s para el bote de propinas.", "Happy to pay, and %s for the tip jar."),
    ("Ya es mía. Los %s de más son de propina.", "It's mine now. The extra %s is a tip."),
    ("Gracias por todo. %s de propina, y volveré.", "Thanks for everything. A %s tip, and I'll be back."),
    ("Listo. Quédate el cambio: %s de propina.", "All set. Keep the change: a %s tip."),
    ("Me voy contento. Esos %s son propina.", "I'm leaving happy. That %s is a tip."),
    ("Trato justo y %s de propina. Así da gusto.", "A fair deal and a %s tip. That's how it should be."),
]

TIP_CHECKOUT = [  # customer happy with the price leaves a tip at the register
    ("Quédate el cambio: %s de propina.", "Keep the change: a %s tip."),
    ("Toma %s de propina. ¡Gran tienda!", "Here's a %s tip. Great shop!"),
    ("Un precio tan justo merece %s de propina.", "A price this fair deserves a %s tip."),
    ("Te dejo %s de propina por el buen trato.", "I'm leaving a %s tip for the good service."),
    ("Para el bote de las propinas: %s.", "For the tip jar: %s."),
    ("No me lo esperaba tan barato. %s de propina.", "Didn't expect it to be this cheap. A %s tip."),
    ("Siempre que vengo salgo contento. Toma %s de propina.", "I always leave happy. Here's a %s tip."),
    ("Esto vale más de lo que cobras. Ahí van %s de propina.", "This is worth more than you charge. Here's a %s tip."),
    ("%s de propina, y no se hable más.", "A %s tip, and that's that."),
    ("Por la buena atención, %s de propina.", "For the great service, a %s tip."),
    ("Redondeo hacia arriba: %s de propina.", "I'll round up: a %s tip."),
    ("Tu tienda me alegra el día. %s de propina.", "Your shop makes my day. A %s tip."),
    ("Un detalle: %s de propina para ti.", "A little something: a %s tip for you."),
    ("Que no se diga que no dejo propina: %s.", "Nobody can say I don't tip: %s."),
    ("Gracias por guardarme lo mejor. %s de propina.", "Thanks for keeping the best for me. A %s tip."),
    ("Buen precio, buena tienda: %s de propina.", "Good price, good shop: a %s tip."),
]

T = {
    # ------------------------------------------------------------------ tips: deal window and notices
    "tcgshop.deal.offer_tip": ("Ofrece: %s (tu precio %s + %s de propina)", "Offers: %s (your price %s + %s tip)"),
    "tcgshop.haggle.a.accept_tip": ("✔ Aceptar · %s (+%s propina)", "✔ Accept · %s (+%s tip)"),
    "tcgshop.haggle.out.tip": ("¡Incluye %s de propina! · %s frente al mercado", "Includes a %s tip! · %s vs market"),
    "tcgshop.tip.received": ("✦ %s te ha dejado %s de propina.", "✦ %s left you a %s tip."),
    "tcgshop.customer.bought_tip": ("%s ha comprado %s por %s (+%s de propina)", "%s bought %s for %s (+%s tip)"),
    # overpay line said when paying YOUR price above market (no tip there): it no longer says "keep the change"
    "tcgshop.npc.overpay_happy.13": ("Las reseñas no mentían. Pago encantado.", "The reviews weren't lying. Gladly paid."),

    # ------------------------------------------------------------------ wandering Collector: several cards for one
    "tcgshop.trader.subtitle": ("Cambia una o varias tuyas por una suya · compra su favorita",
                                "Trades one or more of yours for one of his · buys his favorite"),
    "tcgshop.trader.pick_yours": ("Elige una o varias tuyas", "Pick one or more of yours"),
    "tcgshop.trader.n_cards": ("%s cartas tuyas", "%s of your cards"),
    "tcgshop.trader.too_many": ("Como mucho %s cartas por trato", "At most %s cards per trade"),
    "tcgshop.trader.too_low_multi": ("Tus cartas suman %s; por esa pide al menos %s.", "Your cards add up to %s; he wants at least %s for that one."),
    "tcgshop.trader.traded_multi": ("Cambias %s cartas por %s (valor total %s).", "You trade %s cards for %s (total value %s)."),
}

for cat, lines in (("tip_offer", TIP_OFFER), ("tip_accept", TIP_ACCEPT), ("tip_checkout", TIP_CHECKOUT)):
    for i, (es, en) in enumerate(lines):
        T[f"tcgshop.npc.{cat}.{i}"] = (es, en)


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
