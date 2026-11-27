"""1.1 texts (second batch), area "clean": the shop's cleanliness (score, droppings cleaned with the mop, grime, the
wall air freshener), the 3-wide storefront shutter, ten new decor objects sold by the Furniture Seller, and the daily
Furniture Seller call from the phone (Plus tier and up).
Code: shop/Cleanliness.java, CleanBlocks.java, PoopBlock.java, WallFreshenerBlock.java, WideDoorBlock.java,
DecorPropBlock.java, phone/PhoneUpgrades.java, client/screen/PhoneScreen.java, entity/CustomerEntity.java.

Usage: python tools/gen_lang_12_clean.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ blocks and items
    "block.tcgshop.poop": ("Popó", "Droppings"),
    "block.tcgshop.wall_freshener": ("Aromatizante de pared", "Wall Air Freshener"),
    "block.tcgshop.wall_freshener.tip": ("Se cuelga en la pared. Dentro de tu tienda suelta un rocío cada 30 s que quita mal olor y mugre.",
                                         "Hangs on a wall. Inside your shop it puffs every 30 s, removing bad smell and grime."),
    "block.tcgshop.wide_door": ("Cortina metálica de local", "Storefront Shutter"),
    "block.tcgshop.potted_palm": ("Palmera en maceta", "Potted Palm"),
    "block.tcgshop.potted_palm.tip": ("Un toque tropical para la entrada.", "A tropical touch for the entrance."),
    "block.tcgshop.neon_sign": ("Letrero de neón", "Neon Card Sign"),
    "block.tcgshop.neon_sign.tip": ("Una carta y una estrella de neón para la pared. Ilumina.", "A neon card and star for the wall. Gives light."),
    "block.tcgshop.shop_rug": ("Alfombra de tienda", "Shop Rug"),
    "block.tcgshop.shop_rug.tip": ("Alfombra azul con borde dorado. Se puede pisar.", "Navy rug with a gold border. You can walk over it."),
    "block.tcgshop.wall_clock": ("Reloj de pared", "Wall Clock"),
    "block.tcgshop.wall_clock.tip": ("Para que nadie diga que cerraste temprano.", "So nobody says you closed early."),
    "block.tcgshop.gumball_machine": ("Máquina de chicles", "Gumball Machine"),
    "block.tcgshop.gumball_machine.tip": ("Clásico de mostrador lleno de chicles de colores.", "A counter classic full of colorful gumballs."),
    "block.tcgshop.fish_tank": ("Pecera", "Aquarium"),
    "block.tcgshop.fish_tank.tip": ("Peces tranquilos que relajan a los clientes. Ilumina un poco.", "Calm fish that relax customers. Gives some light."),
    "block.tcgshop.floor_lamp": ("Lámpara de pie", "Floor Lamp"),
    "block.tcgshop.floor_lamp.tip": ("Luz cálida para cualquier rincón.", "Warm light for any corner."),
    "block.tcgshop.card_banner": ("Estandarte de cartas", "Card Banner"),
    "block.tcgshop.card_banner.tip": ("Estandarte azul con una carta dorada. Se cuelga en la pared.", "Navy banner with a gold card. Hangs on a wall."),
    "block.tcgshop.shopkeeper_standee": ("Figura de cartón del tendero", "Cardboard Shopkeeper"),
    "block.tcgshop.shopkeeper_standee.tip": ("Recibe a los clientes con una sonrisa que nunca se cansa.", "Greets customers with a smile that never gets tired."),
    "block.tcgshop.welcome_mat": ("Tapete de bienvenida", "Welcome Mat"),
    "block.tcgshop.welcome_mat.tip": ("Para limpiarse los pies antes de entrar.", "For wiping your feet before coming in."),
    "item.tcgshop.mop.tip": ("Limpia charcos, popó y desorden de tu tienda. En el piso de tu tienda, quita mugre.",
                             "Cleans puddles, droppings and mess in your shop. On your shop's floor it scrubs grime."),

    # ------------------------------------------------------------------ wall freshener
    "tcgshop.freshener.charges": ("Cargas: %s/%s", "Charges: %s/%s"),
    "tcgshop.freshener.refill_tip": ("Clic derecho con el spray desodorante para recargarlo.", "Right-click it with the deodorant spray to refill it."),
    "tcgshop.freshener.status": ("Aromatizante: %s/%s cargas. Rocía cada 30 s si hay mal olor o mugre.",
                                 "Air freshener: %s/%s charges. Puffs every 30 s while there's smell or grime."),
    "tcgshop.freshener.status_outside": ("Aromatizante: %s/%s cargas. Solo funciona dentro de una tienda.",
                                         "Air freshener: %s/%s charges. It only works inside a shop."),
    "tcgshop.freshener.full": ("Ya está lleno (%s/%s).", "It's already full (%s/%s)."),
    "tcgshop.freshener.need_spray": ("Al spray le faltan usos: necesitas %s.", "The spray doesn't have enough uses left: it needs %s."),
    "tcgshop.freshener.refilled": ("Recargado: %s/%s cargas.", "Refilled: %s/%s charges."),

    # ------------------------------------------------------------------ wide door
    "tcgshop.wide_door.blocked": ("Hay alguien en la puerta: no se puede cerrar.", "Someone is standing in the doorway: it can't close."),

    # ------------------------------------------------------------------ droppings and grime
    "tcgshop.poop.appeared": ("¡Guácala! %s dejó un regalito en tu tienda. Límpialo con el trapeador.",
                              "Ew! %s left a little present in your shop. Clean it with the mop."),
    "tcgshop.poop.appeared_pokemon": ("¡%s hizo popó en tu tienda! Límpiala con el trapeador antes de que la vean.",
                                      "%s pooped in your shop! Clean it with the mop before customers see it."),
    "tcgshop.poop.need_mop": ("Necesitas un trapeador para limpiar eso.", "You need a mop to clean that."),
    "tcgshop.poop.one_more": ("Una pasada más...", "One more swipe..."),
    "tcgshop.poop.cleaned": ("¡Limpio! Limpieza de la tienda: %s%%", "Clean! Shop cleanliness: %s%%"),
    "tcgshop.grime.scrubbed": ("Trapeaste el piso. Mugre: %s/100 · limpieza: %s%%", "You mopped the floor. Grime: %s/100 · cleanliness: %s%%"),
    "tcgshop.grime.clean_already": ("El piso ya está limpio (limpieza: %s%%).", "The floor is already clean (cleanliness: %s%%)."),

    # ------------------------------------------------------------------ status, reputation, missions
    "tcgshop.status.clean": ("Limpieza", "Cleanliness"),
    "tcgshop.status.grime": ("Mugre", "Grime"),
    "tcgshop.status.poops": ("Popó en el piso", "Droppings"),
    "tcgshop.rep.clean": ("Limpieza", "Cleanliness"),
    "tcgshop.rep.tip.clean": ("La tienda está sucia: limpia la popó con el trapeador, trapea el piso, usa el spray desodorante y cuelga aromatizantes de pared.",
                              "The shop is dirty: clean droppings with the mop, mop the floor, use the deodorant spray and hang wall air fresheners."),
    "tcgshop.mission.clean.hint": ("Sin mal olor, sin popó en el piso y sin monstruos dentro.", "No bad smell, no droppings on the floor and no monsters inside."),
    "tcgshop.mission.spotless.hint": ("Nada de mal olor, nada de popó y casi nada de mugre en una tienda con mercancía.",
                                      "No bad smell, no droppings and hardly any grime in a stocked shop."),
    "tcgshop.review.dirty": ("Estaba sucio", "It was dirty"),

    # ------------------------------------------------------------------ furniture seller and phone
    "tcgshop.furn.cat.shop": ("Tienda y limpieza", "Shop & cleaning"),
    "tcgshop.phone.furncall": ("Mueblero", "Furniture"),
    "tcgshop.phone.furncall.title": ("Llamar al mueblero", "Call the Furniture Seller"),
    "tcgshop.phone.furncall.calling": ("Llamando... si puede venir, llegará a tu tienda en un momento.",
                                       "Calling... if they can come, they'll be at your shop in a moment."),
    "tcgshop.phoneup.furn_call.ok": ("El Vendedor de Muebles va para tu tienda. ¡Ya puedes comprarle decoración!",
                                     "The Furniture Seller is on the way to your shop. Time to buy some decor!"),
    "tcgshop.phoneup.furn_call.used": ("Ya llamaste al mueblero hoy. Vuelve a intentarlo mañana.", "You already called the Furniture Seller today. Try again tomorrow."),
    "tcgshop.phoneup.furn_call.already_near": ("El Vendedor de Muebles ya está cerca.", "The Furniture Seller is already nearby."),
    "tcgshop.phoneup.furn_call.locked": ("Llamar al mueblero requiere el teléfono %s.", "Calling the Furniture Seller needs the %s phone."),
    "tcgshop.phoneup.perks.plus": ("App Precios · sobres de gama media · Mystery Box en el Mercado · llamar al mueblero una vez al día.",
                                   "Prices app · mid-range packs · Mystery Box in the Market · call the Furniture Seller once a day."),
}

# ---- customer lines (NpcLines: dirty_complain 16, poop_complain 16, clean_praise 12)
LINES = {
    "dirty_complain": [
        ("Este piso está pegajoso...", "This floor is sticky..."),
        ("¿Cuándo fue la última vez que trapearon aquí?", "When was the last time anyone mopped in here?"),
        ("Qué cochinero, la verdad.", "What a mess, honestly."),
        ("Hay polvo hasta en las vitrinas.", "There's dust even on the showcases."),
        ("No sé si quiero tocar nada aquí.", "I'm not sure I want to touch anything in here."),
        ("Mis zapatos van a quedar asquerosos.", "My shoes are going to be filthy."),
        ("Bonitas cartas, lástima lo sucio.", "Nice cards, shame about the dirt."),
        ("Esto necesita una buena limpieza.", "This place needs a good cleaning."),
        ("Huele a encerrado y el piso da pena.", "It smells stuffy and the floor is a disgrace."),
        ("¿Aquí nunca barren?", "Doesn't anyone ever sweep in here?"),
        ("Con este mugrero no me dan ganas de comprar.", "All this grime kills my urge to buy."),
        ("Ojalá el dueño tuviera un trapeador.", "I wish the owner owned a mop."),
        ("Pisé algo raro... mejor ni pregunto.", "I stepped on something weird... better not ask."),
        ("Una tienda sucia, qué decepción.", "A dirty shop, what a letdown."),
        ("Las cartas bien, la limpieza muy mal.", "Cards are fine, the cleaning is terrible."),
        ("Me voy antes de que se me pegue algo.", "I'm leaving before something sticks to me."),
    ],
    "poop_complain": [
        ("¡¿Eso es popó?! ¡Qué asco!", "Is that... poop?! Gross!"),
        ("¡Casi lo piso! ¿Nadie limpia esto?", "I almost stepped in it! Doesn't anyone clean this?"),
        ("Alguien dejó un regalito en el piso...", "Somebody left a little present on the floor..."),
        ("Guácala, ¿qué Pokémon hizo eso?", "Yuck, which Pokémon did that?"),
        ("No pienso comprar junto a eso.", "I'm not shopping next to that."),
        ("¡Mi zapato nuevo! Bueno, casi...", "My new shoes! Well, almost..."),
        ("Esto es una tienda de cartas, no un establo.", "This is a card shop, not a barn."),
        ("Qué olor... ah, ya vi de dónde viene.", "That smell... oh, now I see where it comes from."),
        ("¿Y el trapeador? ¡Urge!", "Where's the mop? It's urgent!"),
        ("Le voy a dejar una reseña sobre... eso.", "I'm leaving a review about... that."),
        ("Cuidado donde pisas, hay sorpresas.", "Watch your step, there are surprises."),
        ("¡Ay, no! ¿En serio nadie lo vio?", "Oh no! Seriously, nobody noticed?"),
        ("Se me quitaron las ganas de abrir sobres.", "I lost all interest in opening packs."),
        ("Alguien debería limpiar eso ya.", "Somebody should clean that right now."),
        ("Ni en la guardería Pokémon pasa esto.", "Not even the Pokémon Day Care looks like this."),
        ("Me tapo la nariz y sigo mirando... rapidito.", "Holding my nose and browsing... quickly."),
    ],
    "clean_praise": [
        ("¡Qué limpio está todo!", "Everything is so clean!"),
        ("Se nota que aquí trapean seguido.", "You can tell they mop here often."),
        ("Huele rico, da gusto comprar así.", "It smells nice, shopping here is a pleasure."),
        ("Ni una mota de polvo. Me encanta.", "Not a speck of dust. I love it."),
        ("Una tienda impecable, así da gusto.", "A spotless shop, that's how it should be."),
        ("Brilla el piso, ¿lo acaban de encerar?", "The floor shines, did they just wax it?"),
        ("Qué bien huele aquí dentro.", "It smells so good in here."),
        ("Limpio y ordenado, como debe ser.", "Clean and tidy, as it should be."),
        ("Aquí sí cuidan los detalles.", "They really care about the details here."),
        ("Da confianza una tienda tan limpia.", "A shop this clean inspires trust."),
        ("¡Hasta el aire se siente fresco!", "Even the air feels fresh!"),
        ("Así cualquiera vuelve.", "Anyone would come back to a place like this."),
    ],
}

# ---- reviews with the "dirty" reason: 8 per star count (ShopZones.Review.textKey)
REVIEWS = {
    1: [("Asqueroso. Había popó en el piso.", "Disgusting. There was poop on the floor."),
        ("Pisé algo que no quiero ni nombrar. Nunca más.", "I stepped in something I won't even name. Never again."),
        ("Una estrella porque no hay cero: sucísimo.", "One star because there's no zero: filthy."),
        ("Mugre por todos lados. Qué horror.", "Grime everywhere. Horrible."),
        ("No pude ni mirar las cartas del asco.", "I couldn't even look at the cards, it was so gross."),
        ("Que alguien le regale un trapeador al dueño.", "Someone please gift the owner a mop."),
        ("Pésimo. El piso estaba asqueroso.", "Terrible. The floor was disgusting."),
        ("Salí corriendo. Qué cochinero.", "I ran out. What a pigsty.")],
    2: [("Flojita. Todo se veía sucio.", "Meh. Everything looked dirty."),
        ("Buenas cartas, pero el piso daba pena.", "Good cards, but the floor was embarrassing."),
        ("No me convenció: demasiada mugre.", "Didn't convince me: too much grime."),
        ("Le falta una buena limpieza.", "It badly needs a good cleaning."),
        ("Decepcionante. Había algo en el piso...", "Disappointing. There was something on the floor..."),
        ("Esperaba más limpieza en una tienda así.", "I expected a cleaner shop."),
        ("Mal rato. Pegajoso y sucio.", "Not great. Sticky and dirty."),
        ("Dos estrellas siendo generoso: limpien, por favor.", "Two stars being generous: please clean up.")],
    3: [("Normalita. Algo sucia, pero tiene buenas cartas.", "Average. A bit dirty, but good cards."),
        ("Ni bien ni mal. Le hace falta trapear.", "Neither good nor bad. It needs mopping."),
        ("Pasable, aunque el piso estaba feo.", "Passable, though the floor was ugly."),
        ("Puede mejorar: un poco de limpieza y listo.", "Could be better: a little cleaning and done."),
        ("Regular. Cuidado donde pisas.", "So-so. Watch where you step."),
        ("Correcta sin más; la limpieza deja que desear.", "Fine, nothing more; the cleaning leaves a lot to be desired."),
        ("Tres estrellas: buenas cartas, piso sucio.", "Three stars: good cards, dirty floor."),
        ("Quizá vuelva cuando limpien.", "Maybe I'll come back once they clean.")],
    4: [("Muy bien, aunque el piso estaba algo sucio.", "Very good, although the floor was a bit dirty."),
        ("Casi perfecta: solo le faltó trapear.", "Almost perfect: it just needed mopping."),
        ("Buena tienda. Un poco de mugre, nada grave.", "Good shop. A little grime, nothing serious."),
        ("Me gustó bastante; que limpien un poco más.", "I liked it a lot; they should clean a bit more."),
        ("Recomendable, aunque vi algo feo en el piso.", "Recommended, although I saw something ugly on the floor."),
        ("Buen sitio para coleccionistas, falta limpieza.", "Good spot for collectors, needs cleaning."),
        ("Le falta poco para las cinco estrellas: limpieza.", "Just shy of five stars: cleanliness."),
        ("Volveré pronto, ojalá más limpia.", "I'll be back soon, hopefully cleaner.")],
    5: [("¡Cinco estrellas! Un poco sucia, pero las cartas lo valen.", "Five stars! A bit dirty, but the cards are worth it."),
        ("Me encantó, aunque el piso necesitaba trapeador.", "Loved it, although the floor needed a mop."),
        ("Experiencia de diez, salvo un detallito en el piso.", "A perfect ten, except for a little something on the floor."),
        ("La mejor tienda del pueblo... con un trapeo sería perfecta.", "The best shop in town... one mopping and it'd be perfect."),
        ("Recomendadísima, aunque le falta limpieza.", "Highly recommended, though it needs cleaning."),
        ("Volveré seguro. Que alguien limpie ese rincón.", "I'll definitely be back. Someone clean that corner."),
        ("Sin pegas en las cartas; la limpieza, mejorable.", "No complaints about the cards; the cleaning could improve."),
        ("Ojalá todas fueran así... pero más limpias.", "I wish they were all like this... but cleaner.")],
}


def main():
    entries = dict(T)
    for cat, lines in LINES.items():
        for i, v in enumerate(lines):
            entries[f"tcgshop.npc.{cat}.{i}"] = v
    for stars, lines in REVIEWS.items():
        assert len(lines) == 8
        for i, v in enumerate(lines):
            entries[f"tcgshop.reviewtext.dirty.{stars}.{i}"] = v
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in entries.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(entries), "texts")


if __name__ == "__main__":
    main()
