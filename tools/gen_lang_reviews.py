#!/usr/bin/env python3
"""
Customer reviews: lines by stars (1-5) and reason (tcgshop.review.<reason>).
Generates tcgshop.reviewtext.<reason>.<stars>.<n> (n = 0..7) in es_es and en_us: 28 reasons x 5 x 8 = 1120 per language.

Each reason has 4 "good" lines (for 4-5 stars) and 4 "bad" ones (1-2 stars); 3 stars mixes both.
They are combined with 8 different lead-ins per star count, so no line repeats.
Usage: python tools/gen_lang_reviews.py   (merges into the lang json files)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"
PER = 8

FRAME = {
    "es": {
        5: ["¡Cinco estrellas! {c}", "{c}. Volveré seguro.", "Experiencia de diez: {c}", "{c}. ¡La mejor tienda de cartas del pueblo!",
            "Me encantó. {c}", "{c}. Recomendadísima.", "{c}. Ojalá todas las tiendas fueran así.", "Sin pegas: {c}"],
        4: ["Muy bien. {c}", "{c}. Casi perfecta.", "Buena tienda. {c}", "{c}. Volveré pronto.",
            "Me gustó bastante: {c}", "{c}. Le falta poco para las cinco estrellas.", "Recomendable. {c}", "{c}. Buen sitio para coleccionistas."],
        3: ["Normalita. {c}", "{c}. Ni bien ni mal.", "Pasable: {c}", "{c}. Puede mejorar.",
            "Regular. {c}", "{c}. Quizá vuelva, quizá no.", "Correcta sin más. {c}", "{c}. Tres estrellas y gracias."],
        2: ["Flojita. {c}", "{c}. Esperaba más.", "No me convenció: {c}", "{c}. Tienen que mejorar.",
            "Decepcionante. {c}", "{c}. No sé si volveré.", "Mal rato. {c}", "{c}. Dos estrellas siendo generoso."],
        1: ["Horrible. {c}", "{c}. No vuelvo nunca.", "Una estrella porque no se puede poner cero: {c}", "{c}. Evitadla.",
            "Pésima experiencia. {c}", "{c}. Qué desastre.", "Lo peor: {c}", "{c}. Se lo voy a contar a todo el pueblo."],
    },
    "en": {
        5: ["Five stars! {c}", "{c}. I'll definitely be back.", "Ten out of ten: {c}", "{c}. Best card shop in town!",
            "Loved it. {c}", "{c}. Highly recommended.", "{c}. Wish every shop were like this.", "No complaints: {c}"],
        4: ["Very good. {c}", "{c}. Almost perfect.", "Good shop. {c}", "{c}. I'll be back soon.",
            "Quite liked it: {c}", "{c}. Close to five stars.", "Recommended. {c}", "{c}. Nice spot for collectors."],
        3: ["Average. {c}", "{c}. Not good, not bad.", "Okay-ish: {c}", "{c}. Room for improvement.",
            "So-so. {c}", "{c}. Maybe I'll be back, maybe not.", "Fine, nothing more. {c}", "{c}. Three stars and thanks."],
        2: ["Meh. {c}", "{c}. I expected more.", "Didn't convince me: {c}", "{c}. They need to improve.",
            "Disappointing. {c}", "{c}. Not sure I'll return.", "Bad time. {c}", "{c}. Two stars, being generous."],
        1: ["Horrible. {c}", "{c}. Never coming back.", "One star because zero isn't an option: {c}", "{c}. Avoid it.",
            "Terrible experience. {c}", "{c}. What a mess.", "The worst: {c}", "{c}. I'll warn the whole town."],
    },
}

# reason: (good, bad), each a list of (es, en)
CORE = {
    "bargain": ([("Los precios eran una ganga", "Prices were a steal"),
                 ("Me llevé una carta por menos de lo que vale", "I got a card for less than it's worth"),
                 ("Encontré ofertas que no hay en ningún otro sitio", "I found deals you won't see anywhere else"),
                 ("El dueño pone precios honestos", "The owner sets honest prices")],
                [("Barato, sí, pero la tienda deja mucho que desear", "Cheap, sure, but the shop leaves a lot to be desired"),
                 ("Los precios bien, el resto no tanto", "Prices were fine, the rest not so much"),
                 ("Una ganga no arregla todo lo demás", "A bargain doesn't fix everything else"),
                 ("Compré barato, pero no me sentí a gusto", "I bought cheap but didn't feel comfortable")]),
    "browse": ([("Solo vine a mirar y me lo pasé bien", "I only came to look and had a good time"),
                ("Muchas cartas bonitas para ver", "Lots of nice cards to look at"),
                ("Nadie me agobió mientras miraba", "Nobody pressured me while I browsed"),
                ("La próxima vez traeré más dinero", "Next time I'll bring more money")],
               [("Miré todo y no encontré nada para mí", "I looked at everything and found nothing for me"),
                ("Di una vuelta y me fui con las manos vacías", "I took a lap and left empty-handed"),
                ("Nada me llamó la atención", "Nothing caught my eye"),
                ("Mucho mirar y poco que comprar", "Lots to look at, little worth buying")]),
    "deal_no": ([("No aceptaron mi oferta, pero fueron amables", "They didn't take my offer, but were polite"),
                 ("Me dijeron que no con educación", "They said no politely"),
                 ("Mi oferta no coló, lo entiendo", "My offer didn't fly, I get it"),
                 ("Otra vez será, el trato estuvo cerca", "Maybe next time, the deal was close")],
                [("Rechazaron mi oferta", "They turned down my offer"),
                 ("Mi carta no les interesó nada", "They weren't interested in my card at all"),
                 ("Vine a vender y me fui con lo mismo", "I came to sell and left with the same card"),
                 ("No quisieron ni negociar", "They wouldn't even negotiate")]),
    "deal_no_bad": ([("Me rechazaron rápido, pero al menos fue claro", "They turned me down fast, but at least it was clear"),
                     ("No hubo trato, aunque la tienda está bien", "No deal, though the shop is nice"),
                     ("Un no rotundo, pero sin malas caras", "A flat no, but no dirty looks"),
                     ("No compraron mi carta, pero el sitio me gusta", "They didn't buy my card, but I like the place")],
                    [("Rechazaron mi oferta sin pensarlo", "They rejected my offer without a second thought"),
                     ("Ni siquiera miraron bien mi carta", "They didn't even look at my card properly"),
                     ("Me despacharon en dos segundos", "They brushed me off in two seconds"),
                     ("Mi oferta era buena y ni la consideraron", "My offer was good and they didn't even consider it")]),
    "deal_no_neutral": ([("No llegamos a un trato, sin problema", "We didn't reach a deal, no problem"),
                         ("Hablamos con calma y nos despedimos bien", "We talked calmly and parted on good terms"),
                         ("No cerramos nada, pero el trato fue correcto", "Nothing closed, but I was treated fairly"),
                         ("Volveré con otra carta a ver si hay suerte", "I'll come back with another card and try my luck")],
                        [("Al final no hubo acuerdo", "In the end there was no agreement"),
                         ("Perdí un rato para nada", "I wasted my time for nothing"),
                         ("No nos entendimos con el precio", "We couldn't agree on a price"),
                         ("Me fui sin vender lo que traía", "I left without selling what I brought")]),
    "deal_rude": ([("El dueño fue algo brusco, pero la tienda vale la pena", "The owner was a bit rough, but the shop is worth it"),
                   ("Mal tono al rechazarme, aunque el resto bien", "Rude when turning me down, though the rest was fine"),
                   ("Me contestaron feo, pero las cartas son buenas", "They answered rudely, but the cards are good"),
                   ("Hoy el dueño tenía un mal día, supongo", "I guess the owner was having a bad day")],
                  [("Me trataron fatal", "They treated me terribly"),
                   ("El dueño me faltó al respeto", "The owner was disrespectful"),
                   ("Me echaron de malas maneras", "They sent me off rudely"),
                   ("Nunca me habían hablado así en una tienda", "No shop has ever spoken to me like that")]),
    "deal_walk": ([("No hubo acuerdo con el precio, pero me trataron bien", "We couldn't settle a price, but they treated me well"),
                   ("Regateamos un rato y fue divertido", "We haggled a while and it was fun"),
                   ("Casi cerramos el trato", "We almost closed the deal"),
                   ("El dueño sabe negociar", "The owner knows how to bargain")],
                  [("No nos pusimos de acuerdo en el precio", "We didn't agree on the price"),
                   ("Me pedían demasiado en el regateo", "They asked way too much while haggling"),
                   ("El regateo fue una pérdida de tiempo", "Haggling was a waste of time"),
                   ("Me fui antes de que me tomaran el pelo", "I left before they could rip me off")]),
    "deal_yes": ([("Trato cerrado, genial", "Deal closed, great"),
                  ("Me pagaron bien por mi carta", "They paid me well for my card"),
                  ("Negociar aquí es fácil y justo", "Negotiating here is easy and fair"),
                  ("Vendí lo que traía en un momento", "I sold what I brought in no time")],
                 [("Cerramos el trato, aunque me supo a poco", "We closed the deal, though it felt like too little"),
                  ("Vendí, pero creo que me quedé corto", "I sold, but I think I undersold"),
                  ("Hubo trato, pero la tienda no me gustó", "There was a deal, but I didn't like the shop"),
                  ("El trato bien, el ambiente no tanto", "The deal was fine, the vibe not so much")]),
    "env_bright": ([("Muy bien iluminada", "Very well lit"),
                    ("Se ven todas las cartas con claridad", "You can see every card clearly"),
                    ("Luz perfecta para revisar el centrado", "Perfect light to check centering"),
                    ("Un local luminoso y agradable", "A bright, pleasant place")],
                   [("Mucha luz, pero poco más", "Lots of light, but not much else"),
                    ("Bien iluminada, pero no encontré lo que buscaba", "Well lit, but I didn't find what I wanted"),
                    ("La luz ayuda, el resto tiene que mejorar", "The lighting helps, the rest needs work"),
                    ("Bonita luz para una experiencia floja", "Nice lighting for a weak experience")]),
    "env_cheap": ([("Precios estupendos", "Great prices"),
                   ("Todo a buen precio", "Everything reasonably priced"),
                   ("Mi cartera lo agradece", "My wallet thanks them"),
                   ("Difícil encontrar precios mejores", "Hard to find better prices")],
                  [("Barata, pero se nota", "Cheap, and it shows"),
                   ("Precios bajos y poco cuidado", "Low prices and little care"),
                   ("Lo barato no lo compensa todo", "Low prices don't make up for everything"),
                   ("Buenos precios en una tienda descuidada", "Good prices in a neglected shop")]),
    "env_cozy": ([("Una tienda preciosa y ordenada", "A lovely, tidy shop"),
                  ("Se nota el cariño en la decoración", "You can tell they care about the decor"),
                  ("Da gusto quedarse a mirar", "It's a pleasure to hang around"),
                  ("Todo bien colocado y acogedor", "Everything well placed and cozy")],
                 [("Bonita, pero eso no basta", "Pretty, but that's not enough"),
                  ("Mucha decoración y poco servicio", "Lots of decor, little service"),
                  ("Acogedora, aunque salí descontento", "Cozy, though I left unhappy"),
                  ("La tienda es bonita, la experiencia no", "The shop is pretty, the experience wasn't")]),
    "env_dark": ([("Algo oscura, pero con encanto", "A bit dark, but charming"),
                  ("Le falta luz, aunque las cartas están bien", "It needs light, though the cards are good"),
                  ("Con un par de antorchas sería perfecta", "A couple of torches and it'd be perfect"),
                  ("Ambiente misterioso, no me disgustó", "Mysterious vibe, I didn't mind")],
                 [("Demasiado oscura", "Way too dark"),
                  ("No se veían ni los precios", "You couldn't even see the prices"),
                  ("Casi tropiezo con un mueble", "I nearly tripped over a shelf"),
                  ("Parecía una cueva, no una tienda", "It felt like a cave, not a shop")]),
    "env_empty": ([("Poca mercancía, pero lo que había era bueno", "Not much stock, but what was there was good"),
                   ("Estanterías algo vacías, volveré cuando repongan", "Shelves were a bit empty, I'll come back after restock"),
                   ("Le falta producto, por lo demás bien", "It needs more stock, otherwise fine"),
                   ("Pocas cosas, pero bien elegidas", "Few items, but well chosen")],
                  [("Casi no había producto", "There was barely any stock"),
                   ("Estanterías vacías por todas partes", "Empty shelves everywhere"),
                   ("Vine a comprar y no había nada", "I came to buy and there was nothing"),
                   ("Más polvo que cartas", "More dust than cards")]),
    "env_no_roof": ([("Sin techo, pero las vistas al cielo están bien", "No roof, but the sky view is nice"),
                     ("Una tienda al aire libre, curioso", "An open-air shop, interesting"),
                     ("Original, aunque espero que no llueva", "Original, though I hope it doesn't rain"),
                     ("Sin techo, pero el resto bien montado", "No roof, but the rest well set up")],
                    [("La tienda no tiene techo", "The shop has no roof"),
                     ("Me llovió encima mientras miraba cartas", "It rained on me while I looked at cards"),
                     ("¿Quién monta una tienda sin techo?", "Who builds a shop without a roof?"),
                     ("Las cartas se van a mojar sin techo", "The cards will get wet without a roof")]),
    "env_no_walls": ([("Sin paredes, pero muy abierta y cómoda", "No walls, but very open and comfy"),
                      ("Concepto abierto, me gustó la idea", "Open concept, I liked the idea"),
                      ("Se entra por cualquier lado, práctico", "You can walk in from any side, handy"),
                      ("Rara sin paredes, pero bien surtida", "Odd without walls, but well stocked")],
                     [("La tienda no tiene paredes", "The shop has no walls"),
                      ("Sin paredes cualquiera se lleva algo", "With no walls anyone could walk off with stuff"),
                      ("Más que una tienda parece un puesto", "It's more a stall than a shop"),
                      ("Hacía frío y viento sin paredes", "Cold and windy with no walls")]),
    "env_pets": ([("Tenía una mascota adorable", "It had an adorable pet"),
                  ("La mascota de la tienda me alegró el día", "The shop pet made my day"),
                  ("Acaricié a la mascota y volví a mirar cartas", "I petted the pet and went back to the cards"),
                  ("Una mascota preciosa vigilando el mostrador", "A lovely pet watching the counter")],
                 [("La mascota era lo mejor, y eso dice mucho", "The pet was the best part, which says a lot"),
                  ("Bonita mascota, mala experiencia", "Cute pet, bad experience"),
                  ("Ni la mascota salva la visita", "Not even the pet saved the visit"),
                  ("Me quedo con la mascota, lo demás no", "I'd keep the pet, not the rest")]),
    "env_pokemon": ([("Me encantaron los Pokémon de la tienda", "I loved the shop's Pokémon"),
                     ("Ver Pokémon de verdad entre cartas es genial", "Seeing real Pokémon among the cards is great"),
                     ("Los Pokémon le dan mucha vida al local", "The Pokémon bring the place to life"),
                     ("Un ambiente muy pokémon, como debe ser", "A very Pokémon atmosphere, as it should be")],
                    [("Los Pokémon bien, el servicio no", "The Pokémon were fine, the service wasn't"),
                     ("Muchos Pokémon y poca atención", "Lots of Pokémon, little attention"),
                     ("Ni los Pokémon me animaron", "Not even the Pokémon cheered me up"),
                     ("Bonitos Pokémon en una tienda floja", "Nice Pokémon in a weak shop")]),
    "expensive": ([("Algo cara, aunque la calidad se nota", "A bit pricey, though the quality shows"),
                   ("Precios altos, pero cartas difíciles de encontrar", "High prices, but hard-to-find cards"),
                   ("Cara, pero el producto lo vale", "Expensive, but the product is worth it"),
                   ("No es barata, pero merece la visita", "Not cheap, but worth a visit")],
                  [("Todo carísimo", "Everything was way overpriced"),
                   ("Precios de especulador", "Scalper prices"),
                   ("Por ese precio me lo compro por internet", "At that price I'll buy it online"),
                   ("Me pedían el doble de lo que vale", "They wanted twice what it's worth")]),
    "fair": ([("Precio justo, volveré", "Fair price, I'll be back"),
              ("Pagué lo que vale, ni más ni menos", "I paid what it's worth, no more, no less"),
              ("Precios honrados y buen trato", "Honest prices and good service"),
              ("Compra rápida y a buen precio", "Quick purchase at a good price")],
             [("El precio justo, el resto mejorable", "Fair price, the rest could be better"),
              ("Compré, pero sin ganas de volver", "I bought something, but don't feel like coming back"),
              ("Precio correcto en un sitio poco cuidado", "Fair price in a poorly kept place"),
              ("Nada que objetar al precio, sí a todo lo demás", "Nothing against the price, plenty against the rest")]),
    "hit": ([("El dueño me empujó, aunque luego se disculpó", "The owner shoved me, though apologized later"),
             ("Un golpe raro, pero la tienda es buena", "A weird hit, but the shop is good"),
             ("Me dieron un manotazo, supongo que sin querer", "I got slapped, I suppose by accident"),
             ("Raro el golpe, buenas las cartas", "Strange hit, good cards")],
            [("El dueño me pegó", "The owner hit me"),
             ("Me atacaron dentro de la tienda", "I was attacked inside the shop"),
             ("Vine a comprar y me llevé un golpe", "I came to shop and got hit"),
             ("Voy a denunciar este sitio", "I'm reporting this place")]),
    "ignored": ([("Tardaron en atenderme, pero al final bien", "They took a while to serve me, but fine in the end"),
                 ("El dueño estaba ocupado, lo entiendo", "The owner was busy, I get it"),
                 ("Esperé un poco, pero la tienda está bien", "I waited a bit, but the shop is nice"),
                 ("Me ignoraron un rato, nada grave", "They ignored me for a bit, nothing serious")],
                [("Me ignoraron", "They ignored me"),
                 ("Nadie me hizo caso", "Nobody paid attention to me"),
                 ("Esperé y esperé y nadie vino", "I waited and waited and nobody came"),
                 ("Como si fuera invisible", "As if I were invisible")]),
    "infamy": ([("Tiene mala fama, pero a mí me fue bien", "It has a bad reputation, but it went fine for me"),
                ("No es para tanto lo que dicen", "It's not as bad as people say"),
                ("Fui con miedo y salí contento", "I went in nervous and left happy"),
                ("Los rumores exageran", "The rumors are exaggerated")],
               [("Esta tienda tiene muy mala fama", "This shop has a terrible reputation"),
                ("Todo el pueblo habla mal de este sitio", "The whole town badmouths this place"),
                ("Con razón tiene mala fama", "No wonder it has a bad reputation"),
                ("No me fío de una tienda con esa fama", "I don't trust a shop with that reputation")]),
    "murder": ([("Pasó algo horrible, pero el dueño actuó rápido", "Something horrible happened, but the owner acted fast"),
                ("Un susto enorme, aunque a mí no me hicieron nada", "A huge scare, though nobody hurt me"),
                ("Vi algo terrible, espero que no se repita", "I saw something terrible, I hope it doesn't happen again"),
                ("Me temblaban las manos, pero compré igual", "My hands were shaking, but I bought anyway")],
               [("Vi un asesinato en la tienda", "I saw a murder in the shop"),
                ("Alguien murió delante de mí", "Someone died right in front of me"),
                ("Esto es la escena de un crimen", "This is a crime scene"),
                ("No pienso volver a pisar este sitio", "I'm never setting foot here again")]),
    "no_cashier": ([("Tardaron en cobrarme, pero al final alguien vino", "It took a while to get rung up, but someone came eventually"),
                    ("Un rato sin nadie en la caja, nada grave", "Nobody at the register for a while, no big deal"),
                    ("Les falta un cajero, por lo demás bien", "They need a cashier, otherwise fine"),
                    ("Esperé en la caja, pero el producto valía la pena", "I waited at the register, but the product was worth it")],
                   [("No había nadie en la caja", "There was nobody at the register"),
                    ("Esperé con el sobre en la mano y nadie cobró", "I waited with a pack in hand and nobody rang me up"),
                    ("Me fui sin comprar porque nadie atendía", "I left without buying because nobody was serving"),
                    ("Una tienda sin nadie que cobre", "A shop with nobody to take your money")]),
    "pricey": ([("Algo caro, pero lo quería", "A bit pricey, but I wanted it"),
                ("Pagué un poco de más, pero merecía la pena", "I paid a bit extra, but it was worth it"),
                ("No es barato, pero tenían justo lo que buscaba", "Not cheap, but they had exactly what I wanted"),
                ("Un capricho caro, sin arrepentimientos", "A pricey treat, no regrets")],
               [("Pagué de más", "I overpaid"),
                ("Me dolió la cartera", "My wallet hurt"),
                ("Caro para lo que es", "Pricey for what it is"),
                ("Me arrepiento un poco de la compra", "I kind of regret the purchase")]),
    "queue_slow": ([("La fila fue lenta, pero me atendieron bien", "The line was slow, but I was served well"),
                    ("Esperé un poco en la caja, nada grave", "I waited a little at the register, no big deal"),
                    ("Mucha gente, señal de que es buena tienda", "Lots of people, a sign it's a good shop"),
                    ("Cola larga, pero valió la pena", "Long line, but worth it")],
                   [("Esperé demasiado en la caja", "I waited too long at the register"),
                    ("La cola no avanzaba", "The line didn't move"),
                    ("Dejé el producto y me fui de la fila", "I put the item back and left the line"),
                    ("Necesitan más gente en la caja", "They need more people at the register")]),
    "smell": ([("Olía raro, pero las cartas compensan", "It smelled weird, but the cards make up for it"),
               ("Un olor extraño, nada que un ambientador no arregle", "A strange smell, nothing an air freshener can't fix"),
               ("Huele un poco, pero el resto genial", "It smells a bit, but the rest is great"),
               ("Olor mejorable, tienda estupenda", "The smell could be better, the shop is great")],
              [("Olía fatal", "It smelled awful"),
               ("Tuve que taparme la nariz", "I had to hold my nose"),
               ("El olor no se aguantaba", "The smell was unbearable"),
               ("Mis cartas ahora huelen a esa tienda", "My cards now smell like that shop")]),
    "unsafe": ([("Había monstruos cerca, pero llegué entero", "There were monsters nearby, but I made it"),
                ("Algo peligroso fuera, dentro todo bien", "A bit dangerous outside, fine inside"),
                ("Vi monstruos de camino, aunque la tienda es buena", "I saw monsters on the way, though the shop is good"),
                ("Un poco de miedo al entrar, nada más", "A little scary getting in, that's all")],
               [("Había monstruos cerca, no me sentí seguro", "There were monsters nearby, I didn't feel safe"),
                ("Casi me ataca un zombi en la puerta", "A zombie nearly attacked me at the door"),
                ("Nadie protege a los clientes", "Nobody protects the customers"),
                ("Ir de compras aquí es arriesgado", "Shopping here is risky")]),
}

KEEP_CAPS = ("I ", "I'", "Pokémon")


def fit(frame, core):
    """Fits the line into the lead-in: lowercase after ':' and no double punctuation."""
    after_colon = frame.rstrip().endswith(": {c}")
    c = core
    if after_colon and not c.startswith(KEEP_CAPS) and not (len(c) > 1 and c[1].isupper()):
        c = c[0].lower() + c[1:]
    elif after_colon and c.startswith(("¿", "¡")):
        c = c[0] + c[1].lower() + c[2:]
    out = frame.replace("{c}", c)
    if out.endswith(("{c}", c)) and not out.endswith(("?", "!", ".")):
        out += "."
    out = out.replace("?.", "?").replace("!.", "!")
    return out


def build(lang):
    li = 0 if lang == "es" else 1
    out = {}
    for key, (good, bad) in CORE.items():
        assert len(good) == 4 and len(bad) == 4, key
        for stars in range(1, 6):
            band = good if stars >= 4 else bad if stars <= 2 else [good[0], bad[0], good[1], bad[1]]
            frames = FRAME[lang][stars]
            for n in range(PER):
                out[f"tcgshop.reviewtext.{key}.{stars}.{n}"] = fit(frames[n], band[n % 4][li])
    return out


def main():
    for lang, f in (("es", "es_es.json"), ("en", "en_us.json")):
        p = LANG / f
        data = json.loads(p.read_text(encoding="utf-8"))
        add = build(lang)
        assert len(set(add.values())) == len(add), "repeated lines"
        data.update(add)
        p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{f}: {len(add)} review lines")


if __name__ == "__main__":
    main()
