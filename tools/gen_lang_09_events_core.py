# -*- coding: utf-8 -*-
"""0.9 - random events, CORE part: names/descriptions/fixes of the phase 1 events, notices,
commands, the phone's Events tab, visitor roles, customer lines (tcgshop.evline.*) and the reviews
with memory (tcgshop.review.ev_<tag> and tcgshop.reviewtext.ev_<tag>.<1-5>.<0-7>, same pattern as gen_lang_reviews.py).

Only sets ITS OWN keys in es_es.json and en_us.json (idempotent). Reloads the JSON right before writing.
Usage: python -I -X utf8 tools/gen_lang_09_events_core.py
"""
import importlib.util
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
LANG = os.path.join(HERE, "..", "src", "main", "resources", "assets", "tcgshop", "lang")

ES = {}
EN = {}


def k(key, es, en):
    ES[key] = es
    EN[key] = en


# ================================================================ events (name, description, on start, fix)
def event(id, name, desc, start, fix=None):
    base = "tcgshop.event." + id
    k(base + ".name", name[0], name[1])
    k(base + ".desc", desc[0], desc[1])
    k(base + ".start", start[0], start[1])
    if fix:
        k(base + ".fix", fix[0], fix[1])


# ---- negative
event("inspection", ("Inspección inesperada", "Surprise inspection"),
      ("Un inspector de comercio va a pasar hoy por la tienda. Mirará el olor, la luz, si hay monstruos cerca, el techo y las paredes, si los expositores tienen género y si hay otros problemas sin arreglar. Una buena nota sube la reputación un día; una mala la baja medio día.",
       "A trade inspector will drop by the shop today. They'll check the smell, the lighting, nearby monsters, the roof and walls, whether the displays are stocked and whether there are other unfixed problems. A good grade boosts reputation for a day; a bad one lowers it for half a day."),
      ("Han avisado de que hoy viene un inspector. Tienes un rato para prepararte.", "You've been told an inspector is coming today. You have a little time to get ready."),
      ("Prepara la tienda antes de que llegue: quita el mal olor (spray), buena luz, sin monstruos cerca, techo y paredes, expositores con género y ningún otro problema sin arreglar.",
       "Get the shop ready before they arrive: no bad smell (spray), good lighting, no monsters nearby, roof and walls, stocked displays and no other unfixed problems."))
event("demanding_customer", ("Cliente muy exigente", "Very demanding customer"),
      ("Viene un cliente quisquilloso y con poca paciencia: sólo compra por debajo del mercado y se queja de todo.",
       "A picky, impatient customer is on the way: they only buy below market price and complain about everything."),
      ("Un cliente muy exigente viene hacia tu tienda.", "A very demanding customer is heading to your shop."),
      ("Atiéndele bien: algún producto por debajo del mercado y cóbrale rápido. Si se va con 3 estrellas o más, asunto resuelto.",
       "Serve them well: put something below market price and check them out quickly. If they leave 3 stars or more, it's solved."))
event("overprice_rumor", ("Rumor de sobrevaloración", "Overpricing rumor"),
      ("Corre por el pueblo que tu tienda es carísima. Los clientes ven los precios más altos de lo que son y la reputación efectiva baja un poco.",
       "Word around town is that your shop is a rip-off. Customers see prices as higher than they are and your effective reputation drops a bit."),
      ("Alguien va diciendo por ahí que tu tienda es carísima.", "Someone's going around saying your shop is way too expensive."),
      ("Pon precios razonables (el factor de precio de la reputación en 0 o más) y mantenlos unos 3 minutos.",
       "Set reasonable prices (the reputation price factor at 0 or above) and keep them for about 3 minutes."))
event("impatient_group", ("Grupo impaciente", "Impatient group"),
      ("Llega un grupo de 3-4 clientes con muy poca paciencia, todos a la vez.", "A group of 3-4 very impatient customers arrives all at once."),
      ("Se acerca un grupo de clientes con prisa.", "A group of customers in a hurry is coming."),
      ("Cóbrales rápido (cajero o tú en la caja) y que encuentren algo a buen precio. Si salen con 3 estrellas de media, resuelto.",
       "Check them out fast (cashier or you at the register) and make sure they find something at a good price. If they average 3 stars, it's solved."))
event("troublemaker", ("Cliente problemático", "Troublesome customer"),
      ("Un cliente maleducado viene a regatear y a molestar: mientras esté, los demás clientes llegan de peor humor.",
       "A rude customer is coming to haggle and cause trouble: while they're around, other customers arrive in a worse mood."),
      ("Un cliente con ganas de bronca se dirige a tu tienda.", "A customer looking for trouble is heading to your shop."),
      ("Haz clic derecho sobre él para calmarlo, o ten un guardia: lo calmará en cuanto lleve un rato dentro.",
       "Right-click them to calm them down, or have a guard: they'll calm them once they've been inside a little while."))
event("aggressive_competition", ("Competencia agresiva", "Aggressive competition"),
      ("Una tienda rival ha lanzado ofertas: hoy llega algo menos de gente y tus precios parecen un poco más caros.",
       "A rival shop launched deals: fewer people come today and your prices look a bit more expensive."),
      ("Una tienda rival ha empezado una guerra de precios.", "A rival shop has started a price war."),
      ("Haz 5 ventas a precio de mercado o menos para demostrar que tus precios son buenos.",
       "Make 5 sales at market price or below to prove your prices are good."))
event("robbery_wave", ("Ola de robos", "Robbery wave"),
      ("Hay una banda de ladrones por la zona: durante unas horas vienen muchos más ladrones a tu tienda.",
       "There's a gang of thieves in the area: for a few hours, many more thieves come to your shop."),
      ("¡Cuidado! Hay una banda de ladrones rondando la zona.", "Watch out! A gang of thieves is roaming the area."),
      ("Contrata un guardia o coloca al menos 2 detectores de robo.", "Hire a guard or place at least 2 theft detectors."))
event("distracted_employee", ("Empleado distraído", "Distracted employee"),
      ("Tu cajero está en las nubes y cobra mucho más despacio. Se le ve tarareando.", "Your cashier has their head in the clouds and checks people out much slower. You can hear them humming."),
      ("Tu cajero está distraído hoy.", "Your cashier is distracted today."),
      ("Haz clic derecho a tu cajero para que vuelva a centrarse.", "Right-click your cashier to get them focused again."))
# ---- positive
event("generous_tip", ("Propina generosa", "Generous tip"),
      ("Un cliente dejará una propina (un 10% extra) en la próxima venta de hoy.", "A customer will leave a tip (10% extra) on today's next sale."),
      ("Hoy alguien viene con ganas de dejar propina.", "Someone's in a tipping mood today."))
event("family_shoppers", ("Familia de compradores", "Family of shoppers"),
      ("Una familia viene de compras: gastan poco y prefieren sobres, pero vienen contentos.", "A family comes shopping: they don't spend much and prefer packs, but they're in a good mood."),
      ("Una familia viene a tu tienda.", "A family is coming to your shop."))
event("tcg_influencer", ("Influencer TCG", "TCG influencer"),
      ("Un influencer de cartas pasa por la tienda. Si sale encantado (4-5 estrellas), mañana llegará más gente; si sale descontento, se notará en tu fama.",
       "A card influencer visits the shop. If they leave delighted (4-5 stars), more people will come tomorrow; if they leave unhappy, your reputation will feel it."),
      ("Un influencer de cartas ha pasado por tu tienda.", "A card influencer stopped by your shop."))
event("returning_customer", ("Vuelve un habitual", "A regular returns"),
      ("Uno de tus clientes habituales vuelve de muy buen humor y con ganas de comprar.", "One of your regulars comes back in a great mood and ready to buy."),
      ("Un cliente habitual ha vuelto.", "A regular has come back."))
event("nostalgic_collector", ("Coleccionista nostálgico", "Nostalgic collector"),
      ("Un coleccionista busca cartas de sets antiguos y paga con gusto (hasta un poco por encima del mercado).", "A collector is looking for cards from old sets and pays gladly (up to a bit above market)."),
      ("Un coleccionista de cartas antiguas ha pasado por aquí.", "A collector of old cards stopped by."))
event("beginners_day", ("Día de principiantes", "Beginners' day"),
      ("Mucha gente nueva se anima con las cartas: llegan más clientes normales y compran sobres con más ganas.", "Lots of newcomers are getting into cards: more regular customers come and they're keener on packs."),
      ("¡Hoy es día de principiantes! Mucha gente quiere empezar su colección.", "It's beginners' day! Lots of people want to start a collection."))
event("wholesale_buyer", ("Comprador al por mayor", "Wholesale buyer"),
      ("Un comprador quiere llevarse varias cosas de una vez (hasta 3), siempre que no estén muy por encima del mercado.", "A buyer wants to take several things at once (up to 3), as long as they're not far above market."),
      ("Un comprador al por mayor ha visitado tu tienda.", "A wholesale buyer visited your shop."))
event("word_of_mouth", ("Boca a boca", "Word of mouth"),
      ("Tus buenas reseñas corren por el pueblo: hoy llega más gente.", "Your good reviews are spreading around town: more people come today."),
      ("Se habla muy bien de tu tienda por el pueblo.", "People are talking about your shop around town."))
event("loyalty_bonus", ("Bonus de fidelidad", "Loyalty bonus"),
      ("Tus clientes habituales te recompensan: un poco de dinero por cada uno (con tope) y algo de comer.", "Your regulars reward you: a little money for each of them (capped) and some snacks."),
      ("Tus clientes habituales te han dejado un detalle.", "Your regulars left you a little something."))
event("collectors_day", ("Día de coleccionistas", "Collectors' day"),
      ("Hoy se mueven los coleccionistas: vienen más coleccionistas adinerados y más gente que quiere cambiar cartas.", "Collectors are out today: more wealthy collectors and more people wanting to trade cards."),
      ("¡Día de coleccionistas! Hoy hay mucho movimiento.", "Collectors' day! Lots going on today."))
event("good_ambience", ("Buen ambiente", "Great atmosphere"),
      ("La decoración de tu tienda está gustando mucho: +3 de reputación durante el día.", "People love your shop's decoration: +3 reputation for the day."),
      ("A la gente le encanta el ambiente de tu tienda.", "People love the atmosphere in your shop."))
event("outstanding_employee", ("Empleado sobresaliente", "Outstanding employee"),
      ("Tu cajero está en racha: hoy cobra bastante más rápido.", "Your cashier is on a roll: they check people out much faster today."),
      ("Tu cajero está hoy imparable.", "Your cashier is unstoppable today."))
event("spotless_shop", ("Tienda impecable", "Spotless shop"),
      ("Sin malos olores y sin monstruos cerca: los clientes lo notan y la reputación sube +3 durante el día.", "No bad smells and no monsters nearby: customers notice and reputation rises +3 for the day."),
      ("Tu tienda está impecable y se nota.", "Your shop is spotless and it shows."))
event("small_sponsorship", ("Pequeño patrocinio", "Small sponsorship"),
      ("Un negocio local patrocina tu tienda: un poco de dinero en la caja y buena fama.", "A local business sponsors your shop: a bit of money in the register and a good name."),
      ("¡Un negocio local quiere patrocinar tu tienda!", "A local business wants to sponsor your shop!"))
event("set_collector", ("Coleccionista de un set", "Set collector"),
      ("Un coleccionista busca cartas de un set concreto (el que más tienes expuesto) y puede llevarse varias.", "A collector is hunting cards from one particular set (the one you display most) and may take several."),
      ("Un coleccionista que busca un set concreto ha pasado por aquí.", "A collector looking for a specific set stopped by."))
event("international_collector", ("Coleccionista internacional", "International collector"),
      ("Un coleccionista de fuera quiere completar un set: se lleva hasta 3 cartas y paga hasta un poco más que el mercado.", "A collector from abroad wants to complete a set: they take up to 3 cards and pay up to a bit over market."),
      ("Un coleccionista internacional ha visitado tu tienda.", "An international collector visited your shop."))
event("lucky_day", ("Día de suerte", "Lucky day"),
      ("Hoy todo sale bien: vienen más coleccionistas adinerados y la gente compra con más ganas.", "Everything goes right today: more wealthy collectors come and people are keener to buy."),
      ("Tienes la sensación de que hoy va a ser un buen día.", "You have a feeling today is going to be a good day."))
event("collection_exhibition", ("Exposición de colección", "Collection exhibition"),
      ("Tus expositores llaman la atención: reputación extra durante el día según cuántos tengas.", "Your displays draw attention: extra reputation for the day depending on how many you have."),
      ("La gente viene a ver tus expositores como si fuera un museo.", "People come to see your displays like it's a museum."))
event("legendary_customer", ("Cliente legendario", "Legendary customer"),
      ("Un coleccionista legendario visita la tienda: compra varias cosas y, si sale con 5 estrellas, tu fama crece.", "A legendary collector visits the shop: they buy several things and, if they leave 5 stars, your fame grows."),
      ("¡Un cliente legendario ha pasado por tu tienda!", "A legendary customer stopped by your shop!"))
event("expansion_party", ("Celebración de expansión", "Expansion celebration"),
      ("¡Ha salido la nueva expansión! Llega más gente y el set más nuevo se vende con más ganas.", "The new expansion is out! More people come and the newest set sells more eagerly."),
      ("¡Hoy se celebra la nueva expansión!", "Today we celebrate the new expansion!"))
event("limited_offer", ("Oferta por tiempo limitado", "Limited-time offer"),
      ("Durante unas horas, lo que tengas a precio de mercado o menos se vende mucho mejor.", "For a few hours, anything at market price or below sells much better."),
      ("¡Oferta por tiempo limitado! Lo que esté a buen precio volará.", "Limited-time offer! Anything at a good price will fly."))
event("supplier_discount", ("Descuento del proveedor", "Supplier discount"),
      ("El Mayorista viene a verte y tu próxima compra (al Mayorista o en el Mercado del teléfono) sale un 15% más barata hoy.", "The Wholesaler comes to see you and your next purchase (Wholesaler or phone Market) is 15% cheaper today."),
      ("El Mayorista viene con un descuento para ti.", "The Wholesaler is coming with a discount for you."))
event("early_delivery", ("Entrega anticipada", "Early delivery"),
      ("Uno de tus pedidos a CobbleGrade llegará bastante antes.", "One of your CobbleGrade orders will arrive quite a bit earlier."),
      ("CobbleGrade ha adelantado uno de tus pedidos.", "CobbleGrade has moved one of your orders forward."))
event("expert_visit", ("Visita de un experto", "Expert visit"),
      ("Un experto en gradeo pasa por la tienda y te deja un 20% de descuento para tu próximo envío a CobbleGrade (hoy).", "A grading expert stops by and leaves you a 20% discount on your next CobbleGrade submission (today)."),
      ("Un experto en gradeo viene a la tienda.", "A grading expert is coming to the shop."))
# ---- neutral
event("decoration_contest", ("Concurso de decoración", "Decoration contest"),
      ("El pueblo organiza un concurso de decoración de tiendas: si la tuya está bien decorada, hay un pequeño premio.", "The town holds a shop decoration contest: if yours is well decorated, there's a small prize."),
      ("Ha empezado el concurso de decoración de tiendas.", "The shop decoration contest has begun."))
event("shop_competition", ("Comparativa de tiendas", "Shop comparison"),
      ("Una revista compara las tiendas de cartas: la mejor valorada gana buena fama y un pequeño premio.", "A magazine compares card shops: the best rated one gets a good name and a small prize."),
      ("Una revista está comparando las tiendas de cartas.", "A magazine is comparing card shops."))
event("job_seeker", ("Alguien busca trabajo", "Someone's looking for work"),
      ("Una persona busca trabajo y viene a ofrecerse: hoy tu próxima contratación cuesta la mitad.", "Someone's looking for a job and comes to offer themselves: today your next hire costs half."),
      ("Alguien viene a preguntar si necesitas personal.", "Someone's coming to ask if you need staff."))
event("indecisive_customer", ("Cliente indeciso", "Indecisive customer"),
      ("Un cliente lo mira todo dos veces y no termina de decidirse.", "A customer looks at everything twice and can't make up their mind."),
      ("Un cliente indeciso ha pasado por la tienda.", "An indecisive customer stopped by."))
event("collectors_argue", ("Coleccionistas discutiendo", "Collectors arguing"),
      ("Dos coleccionistas discuten en tu tienda sobre precios y cartas.", "Two collectors argue in your shop about prices and cards."),
      ("Dos coleccionistas han montado un debate en tu tienda.", "Two collectors started a debate in your shop."))
event("trade_fever", ("Fiebre de intercambios", "Trading fever"),
      ("Hoy todo el mundo quiere cambiar cartas: vienen más coleccionistas con ofertas de intercambio.", "Everyone wants to trade cards today: more collectors come with trade offers."),
      ("¡Hoy hay fiebre de intercambios!", "It's trading fever today!"))
event("mystery_collector", ("Coleccionista misterioso", "Mysterious collector"),
      ("Un coleccionista misterioso lo mira todo con lupa, pero nunca compra nada... Quizá vuelva alguien buscando algo.", "A mysterious collector examines everything closely but never buys... Maybe someone will come looking for something."),
      ("Un coleccionista misterioso ha estado mirando tus expositores.", "A mysterious collector has been looking at your displays."))
event("players_meetup", ("Quedada de jugadores", "Players' meetup"),
      ("Unos jugadores han quedado en tu mesa de juego: vienen a jugar y atraen a algo más de gente.", "Some players are meeting at your play table: they come to play and draw a few more people."),
      ("Unos jugadores han quedado en tu tienda para jugar.", "Some players are meeting at your shop to play."))
event("weather_change", ("Cambio de tiempo", "Change in the weather"),
      ("Llueve: llega algo menos de gente, pero los que vienen tienen más paciencia. Sin techo o sin paredes, se mojan y llegan de peor humor.",
       "It's raining: fewer people come, but those who do are more patient. Without a roof or walls they get wet and arrive in a worse mood."),
      ("Ha cambiado el tiempo y la gente lo nota.", "The weather has changed and people notice."))
event("expansion_announced", ("Anuncian una expansión", "Expansion announced"),
      ("Se ha anunciado una nueva expansión de cartas. En los próximos días es probable que haya celebración.", "A new card expansion has been announced. A celebration is likely in the coming days."),
      ("¡Han anunciado una nueva expansión!", "A new expansion has been announced!"))
k("tcgshop.event.expansion_announced.chat", "Los clientes no hablan de otra cosa: dicen que pronto habrá fiesta de lanzamiento.",
  "Customers can't stop talking about it: they say there'll be a launch party soon.")
event("photo_visitor", ("Visitante haciendo fotos", "Visitor taking photos"),
      ("Alguien pasea por la tienda haciendo fotos de todo. A lo mejor las sube a internet...", "Someone wanders around the shop taking photos of everything. Maybe they'll post them online..."),
      ("Alguien ha estado haciendo fotos de tu tienda.", "Someone has been taking photos of your shop."))
event("offcatalog_vendor", ("Vendedor fuera de catálogo", "Off-catalog vendor"),
      ("Aparece el mercader negro cerca de ti con un sobre que no está en ningún catálogo (lo cambia por un Pokémon).", "The black market dealer shows up near you with a pack that isn't in any catalog (they trade it for a Pokémon)."),
      ("Alguien encapuchado te hace señas desde una esquina...", "Someone in a hood is beckoning you from a corner..."))
event("supplier_delay", ("Retraso del proveedor", "Supplier delay"),
      ("El proveedor va con retraso: hoy el Mercado del teléfono está un 10% más caro.", "The supplier is running late: the phone Market is 10% more expensive today."),
      ("El proveedor va con retraso hoy.", "The supplier is running late today."))
event("collector_seller", ("Coleccionista que vende", "Collector selling cards"),
      ("Un par de coleccionistas vienen a venderte cartas de su colección (nunca por debajo del mercado).", "A couple of collectors come to sell you cards from their collection (never below market)."),
      ("Unos coleccionistas vienen a venderte cartas.", "Some collectors are coming to sell you cards."))

# ================================================================ notices and messages
for t, es, en in (("common", "Común", "Common"), ("uncommon", "Poco común", "Uncommon"), ("rare", "Raro", "Rare"), ("extraordinary", "Extraordinario", "Extraordinary")):
    k("tcgshop.event.tier." + t, es, en)
k("tcgshop.event.fix_prefix", "Cómo arreglarlo:", "How to fix it:")
k("tcgshop.event.resolved", "✔ Arreglado: %s", "✔ Fixed: %s")
k("tcgshop.event.resolved_body", "Problema resuelto. ¡Bien hecho!", "Problem solved. Well done!")
k("tcgshop.event.expired", "El evento «%s» ha terminado sin que lo arreglaras.", "The event \"%s\" ended without you fixing it.")
k("tcgshop.event.money", "Evento: %s a la caja de la tienda.", "Event: %s added to the shop register.")
k("tcgshop.event.tip_paid", "¡Propina! %s extra a la caja.", "Tip! %s extra in the register.")
k("tcgshop.event.metric_progress", "Evento: objetivo %s/%s", "Event: goal %s/%s")
k("tcgshop.event.door_blocked_queue", "La entrada está bloqueada: los clientes esperan fuera.", "The entrance is blocked: customers are waiting outside.")
k("tcgshop.event.supplier.no_room", "No había sitio cerca de ti para que apareciera.", "There was no room near you for them to appear.")
k("tcgshop.event.supplier.grading_faster", "Uno de tus pedidos de CobbleGrade llegará antes.", "One of your CobbleGrade orders will arrive sooner.")
k("tcgshop.event.supplier.grading_none", "No tenías pedidos pendientes en CobbleGrade.", "You had no pending CobbleGrade orders.")
k("tcgshop.event.reward.not_enough_decor", "Decoración %s de %s necesaria: esta vez no hay premio.", "Decoration %s of %s needed: no prize this time.")
k("tcgshop.event.reward.rank_top", "¡Tu tienda es la mejor valorada! (puesto %s de %s)", "Your shop is the best rated! (place %s of %s)")
k("tcgshop.event.reward.rank_place", "Tu tienda quedó en el puesto %s de %s.", "Your shop came in place %s of %s.")
k("tcgshop.event.reward.items", "Evento: recibes %s objetos.", "Event: you receive %s items.")
k("tcgshop.event.visitor.inspector_here", "El inspector ha llegado a la tienda.", "The inspector has arrived at the shop.")
k("tcgshop.event.visitor.done_good", "«%s» ha salido muy bien.", "\"%s\" went really well.")
k("tcgshop.event.visitor.done_bad", "«%s» ha salido mal.", "\"%s\" went badly.")
k("tcgshop.event.visitor.done_neutral", "«%s» ha terminado.", "\"%s\" is over.")
# commands
k("tcgshop.event.cmd.ended", "Evento #%s terminado.", "Event #%s ended.")
k("tcgshop.event.cmd.unknown", "No hay ningún evento activo #%s.", "No active event #%s.")
k("tcgshop.event.cmd.list", "%s eventos activos · %s definiciones cargadas · %s descartadas", "%s active events · %s definitions loaded · %s rejected")
k("tcgshop.event.cmd.no_def", "No existe el evento %s.", "There's no event %s.")
k("tcgshop.event.cmd.no_shop", "No hay ninguna tienda en este mundo.", "There's no shop in this world.")
k("tcgshop.event.cmd.no_variant", "Ese evento no tiene la variante %s.", "That event has no variant %s.")
k("tcgshop.event.cmd.failed", "No se pudo empezar %s.", "Couldn't start %s.")
k("tcgshop.event.cmd.started", "Empieza «%s» (#%s) en la tienda de %s.", "Started \"%s\" (#%s) at %s's shop.")
k("tcgshop.event.cmd.not_eligible", "Aviso: de forma natural no habría salido (%s).", "Note: it wouldn't have fired naturally (%s).")
k("tcgshop.event.cmd.roll_state", "Día %s · última tirada día %s (%s) · empieza a las %s · hoy %s · dinero de premios disponible %s",
  "Day %s · last roll day %s (%s) · starts at %s · today %s · reward money left %s")
k("tcgshop.event.cmd.deck_tier", "Mazo %s: %s de %s eventos en este mundo", "Deck %s: %s of %s events in this world")
k("tcgshop.event.cmd.deck_missing", "No están en este mundo: %s", "Not in this world: %s")
k("tcgshop.event.cmd.deck_rerolled", "Mazo de eventos sorteado de nuevo.", "Event deck rerolled.")
k("tcgshop.event.cmd.rolled", "Tirada de hoy forzada: el evento empezará en cuanto estés cerca de una de tus tiendas.",
  "Today's roll forced: the event will start as soon as you're near one of your shops.")
# reputation (temporary event factor)
k("tcgshop.rep.event", "Eventos (temporal)", "Events (temporary)")
k("tcgshop.rep.tip.event", "Hay un evento afectando a tu reputación. Mira la pestaña Eventos de la app Tienda.", "An event is affecting your reputation. Check the Events tab in the Shop app.")
# phone
k("tcgshop.phone.tab.events", "Eventos", "Events")
k("tcgshop.phone.ev.none", "Ahora mismo no pasa nada especial en esta tienda.", "Nothing special is happening in this shop right now.")
k("tcgshop.phone.ev.fixed", "✔ Arreglado", "✔ Fixed")
k("tcgshop.phone.ev.ends_in", "Termina en %s:%s", "Ends in %s:%s")
k("tcgshop.phone.ev.history", "Hoy y últimos 7 días", "Today and the last 7 days")
k("tcgshop.phone.ev.no_history", "Todavía no ha pasado nada.", "Nothing has happened yet.")
for o, es, en in (("resolved", "Arreglado", "Fixed"), ("expired", "Sin arreglar", "Not fixed"), ("done", "Terminado", "Done"),
                  ("failed", "Salió mal", "Went badly"), ("left", "Se fue", "Left"), ("removed", "Cancelado", "Cancelled"),
                  ("command", "Cancelado", "Cancelled"), ("ended", "Terminado", "Done")):
    k("tcgshop.phone.ev.out." + o, es, en)
# visitor roles (line above the head)
for r, es, en in (("inspector", "Inspector de comercio", "Trade inspector"), ("demanding", "Cliente exigente", "Demanding customer"),
                  ("troublemaker", "Cliente problemático", "Troublemaker"), ("influencer", "Influencer TCG", "TCG influencer"),
                  ("nostalgic", "Coleccionista nostálgico", "Nostalgic collector"), ("wholesale", "Comprador al por mayor", "Wholesale buyer"),
                  ("set_collector", "Coleccionista de sets", "Set collector"), ("international", "Coleccionista internacional", "International collector"),
                  ("legendary", "Cliente legendario", "Legendary customer"), ("expert", "Experto en gradeo", "Grading expert"),
                  ("job_seeker", "Busca trabajo", "Job seeker"), ("mystery", "Coleccionista misterioso", "Mysterious collector"),
                  ("photographer", "Fotógrafo", "Photographer"), ("seller_collector", "Coleccionista vendedor", "Collector seller")):
    k("tcgshop.role.ev_" + r, es, en)

# ================================================================ customer and employee lines (tcgshop.evline.<cat>.<n>)
L = {
    "rumor": [("Dicen por ahí que aquí todo es carísimo...", "People say everything here is overpriced..."),
              ("Me han contado que esta tienda te cobra de más.", "I heard this shop overcharges."),
              ("A ver si es verdad lo de los precios...", "Let's see if the price rumors are true..."),
              ("Mi primo dice que aquí te sacan los cuartos.", "My cousin says they rip you off here."),
              ("Vengo con la cartera bien agarrada, por si acaso.", "I'm holding my wallet tight, just in case."),
              ("¿Es esta la tienda cara de la que habla todo el mundo?", "Is this the pricey shop everyone talks about?")],
    "competition": [("En la otra tienda tienen ofertas, ¿eh?", "The other shop has deals, you know?"),
                    ("Vengo a comparar con la tienda nueva.", "I'm here to compare with the new shop."),
                    ("La competencia está regalando sobres, casi.", "The competition is almost giving packs away."),
                    ("A ver quién tiene mejores precios hoy.", "Let's see who has better prices today."),
                    ("Me han dado un folleto de la tienda rival.", "Someone gave me a flyer from the rival shop."),
                    ("Si aquí está más caro, me voy a la otra.", "If it's pricier here, I'm going to the other one.")],
    "theft": [("Dicen que hay ladrones por la zona, cuidado.", "They say there are thieves around, careful."),
              ("He visto gente rara rondando la tienda.", "I've seen shady people hanging around the shop."),
              ("Agarro bien mi mochila, que hay mucho caco suelto.", "I'm holding my backpack tight, lots of crooks around."),
              ("¿No tenéis guardia? Con la que hay montada...", "No guard? With everything going on..."),
              ("Me han robado el bocadillo de camino aquí.", "Someone stole my sandwich on the way here."),
              ("Ojo con los expositores, que andan ladrones.", "Watch the displays, there are thieves about.")],
    "distracted": [("La la la... ¿eh? ¿Decías algo?", "La la la... huh? Did you say something?"),
                   ("Perdona, estaba pensando en mi mazo.", "Sorry, I was thinking about my deck."),
                   ("Mmm... ¿cuánto era esto? Se me ha ido.", "Hmm... how much was this? I forgot."),
                   ("¿Has visto la última expansión? Qué pasada...", "Have you seen the latest expansion? Amazing..."),
                   ("Un momentito, que estoy ordenando esto... otra vez.", "Just a sec, I'm sorting this... again."),
                   ("Zzz... ¡ah! ¡Sí, sí, ya cobro!", "Zzz... ah! Yes, yes, I'm on it!")],
    "focused": [("¡Perdona, jefe! Ya estoy a tope.", "Sorry, boss! I'm fully on it now."),
                ("Vale, vale, me centro. ¡Siguiente!", "Okay, okay, I'm focused. Next!"),
                ("Tienes razón, a trabajar.", "You're right, back to work."),
                ("Uy, se me fue la cabeza. Ya está.", "Oops, my mind wandered. All good now."),
                ("¡Modo cajero activado!", "Cashier mode activated!"),
                ("Gracias por el toque, ya voy.", "Thanks for the nudge, I'm on it.")],
    "calmed": [("Vale... quizá me he pasado un poco.", "Okay... maybe I went a bit too far."),
               ("Bueno, bueno, ya me calmo.", "Fine, fine, I'll calm down."),
               ("Perdona, hoy no es mi día.", "Sorry, today's not my day."),
               ("Tienes razón, no hace falta gritar.", "You're right, no need to shout."),
               ("Está bien, miraré con tranquilidad.", "Alright, I'll browse calmly."),
               ("Lo siento por el numerito.", "Sorry about the scene.")],
    "rude": [("¡Esto es un timo!", "This is a scam!"), ("¿Quién pone estos precios, un Magikarp?", "Who sets these prices, a Magikarp?"),
             ("¡Quiero hablar con el encargado!", "I want to speak to the manager!"), ("Qué tienda más cutre.", "What a shabby shop."),
             ("¡Apartaos, que tengo prisa!", "Out of my way, I'm in a hurry!"), ("En mi pueblo esto vale la mitad.", "Back home this costs half."),
             ("¿Aquí nadie atiende o qué?", "Does anyone work here or what?"), ("Menuda pérdida de tiempo.", "What a waste of time.")],
    "guard_calms": [("Tranquilo, amigo. Aquí se viene a comprar.", "Easy, friend. People come here to shop."),
                    ("Baje la voz, por favor, o tendrá que irse.", "Lower your voice, please, or you'll have to leave."),
                    ("Venga, sin numeritos en la tienda.", "Come on, no scenes in the shop."),
                    ("Calma. Si tiene una queja, hable con el dueño.", "Calm down. If you have a complaint, talk to the owner.")],
    "inspector": [("Inspección de comercio. Sigan a lo suyo.", "Trade inspection. Carry on."),
                  ("Veamos esa iluminación...", "Let's check that lighting..."),
                  ("Mmm, apuntado.", "Hmm, noted."),
                  ("¿Este olor es normal?", "Is this smell normal?"),
                  ("Revisaré los expositores, si no le importa.", "I'll check the displays, if you don't mind."),
                  ("Todo debe estar en orden para mi informe.", "Everything must be in order for my report.")],
    "demanding": [("Espero que aquí tengan algo decente.", "I hope you have something decent here."),
                  ("No pienso pagar ni un CobbleDollar de más.", "I won't pay a single CobbleDollar extra."),
                  ("Tengo poco tiempo y mucho criterio.", "I have little time and high standards."),
                  ("A ver si me sorprenden, que lo dudo.", "Let's see if you surprise me, I doubt it."),
                  ("Los precios, ¿están bien puestos?", "Are these prices right?"),
                  ("Ya he estado en mejores tiendas.", "I've been to better shops.")],
    "impatient": [("¡Rápido, que perdemos el autobús!", "Quick, we'll miss the bus!"),
                  ("Venga, venga, que no tenemos todo el día.", "Come on, come on, we don't have all day."),
                  ("¿Hay mucha cola? No podemos esperar.", "Is there a line? We can't wait."),
                  ("Entramos, compramos y nos vamos.", "In, buy, out."),
                  ("¡Que alguien nos cobre ya!", "Someone check us out already!"),
                  ("Cinco minutos y nos vamos, ¿eh?", "Five minutes and we're off, okay?")],
    "family": [("¡Mamá, mira cuántos sobres!", "Mom, look at all the packs!"),
               ("Uno para cada uno, ¿vale?", "One each, okay?"),
               ("Venimos toda la familia a ver cartas.", "The whole family came to look at cards."),
               ("¡Quiero un Pikachu!", "I want a Pikachu!"),
               ("Algo que no sea muy caro, chicos.", "Something not too expensive, kids."),
               ("Es la primera vez que venimos.", "It's our first time here.")],
    "influencer": [("¡Hola a todos! Hoy visitamos esta tienda.", "Hi everyone! Today we're visiting this shop."),
                   ("Dadle a seguir si os gusta la tienda.", "Hit follow if you like the shop."),
                   ("A ver qué tal está este sitio para el vídeo.", "Let's see how this place looks for the video."),
                   ("Mis seguidores van a flipar con esto.", "My followers are going to love this."),
                   ("Primera impresión: interesante...", "First impression: interesting..."),
                   ("Grabando, grabando... ¡acción!", "Recording, recording... action!")],
    "returning": [("¡Hola otra vez! No podía no volver.", "Hi again! I had to come back."),
                  ("Echaba de menos esta tienda.", "I missed this shop."),
                  ("¡Vuelvo con más ganas que nunca!", "I'm back and keener than ever!"),
                  ("La última vez me fui encantado.", "Last time I left delighted."),
                  ("Hoy vengo con la cartera llena.", "I came with a full wallet today."),
                  ("¡Ya sabéis que soy de la casa!", "You know I'm a regular!")],
    "nostalgic": [("¿Tenéis algo de los primeros sets?", "Do you have anything from the early sets?"),
                  ("Estas cartas me recuerdan a mi infancia.", "These cards remind me of my childhood."),
                  ("Busco cartas antiguas, de las de antes.", "I'm looking for old cards, the classic ones."),
                  ("Ay, qué tiempos aquellos...", "Ah, those were the days..."),
                  ("Por una carta antigua en buen estado pago lo que haga falta.", "I'll pay what it takes for an old card in good shape."),
                  ("Las de ahora están bien, pero las de antes...", "Today's cards are fine, but the old ones...")],
    "beginners": [("¿Cómo se juega a esto?", "How do you play this?"),
                  ("Quiero empezar mi colección.", "I want to start my collection."),
                  ("¿Qué sobre me recomiendas para empezar?", "Which pack do you recommend to start?"),
                  ("Mis amigos juegan y yo también quiero.", "My friends play and I want to as well."),
                  ("Es mi primera carta, ¡qué ilusión!", "It's my first card, so exciting!"),
                  ("No entiendo nada, pero me encanta.", "I don't understand anything, but I love it.")],
    "wholesale": [("Me llevo varias cosas, si el precio es justo.", "I'll take several things if the price is fair."),
                  ("Compro para mi club de coleccionistas.", "I'm buying for my collectors' club."),
                  ("Busco cantidad, no me importa la variedad.", "I'm after quantity, I don't mind variety."),
                  ("¿Hacéis descuento por volumen?", "Any bulk discount?"),
                  ("Vengo con una lista larga.", "I came with a long list."),
                  ("Si me gusta, me llevo tres.", "If I like it, I'll take three.")],
    "word_of_mouth": [("Me han hablado muy bien de esta tienda.", "I've heard great things about this shop."),
                      ("Vengo porque un amigo me la recomendó.", "I came because a friend recommended it."),
                      ("Dicen que aquí se compra a gusto.", "They say shopping here is a pleasure."),
                      ("He leído las reseñas, ¡cinco estrellas!", "I read the reviews, five stars!"),
                      ("Todo el pueblo habla de este sitio.", "The whole town talks about this place."),
                      ("A ver si es tan buena como dicen.", "Let's see if it's as good as they say.")],
    "collectors": [("Hoy es día de coleccionistas, ¿no?", "It's collectors' day, right?"),
                   ("Busco joyas para mi colección.", "I'm looking for gems for my collection."),
                   ("¿Alguien quiere cambiar cartas?", "Anyone want to trade cards?"),
                   ("Hoy vengo dispuesto a gastar.", "I'm ready to spend today."),
                   ("Hay muy buen ambiente de coleccionistas.", "There's a great collectors' vibe."),
                   ("Me falta una para completar el set.", "I'm one card short of the set.")],
    "star_employee": [("¡Siguiente, por favor! Rapidito.", "Next, please! Nice and quick."),
                      ("Hoy no se me escapa ni una cola.", "No line beats me today."),
                      ("¡Gracias por su compra, vuelva pronto!", "Thanks for shopping, come again!"),
                      ("Me siento imparable.", "I feel unstoppable."),
                      ("Caja abierta y lista.", "Register open and ready."),
                      ("¡Récord de cobros a la vista!", "Checkout record incoming!")],
    "set_collector": [("Busco cartas de un set muy concreto.", "I'm after cards from one specific set."),
                      ("Me faltan unas pocas para completarlo.", "I'm only missing a few to complete it."),
                      ("¡Ese set! Justo el que busco.", "That set! Exactly what I'm looking for."),
                      ("¿Tenéis más de esta expansión?", "Do you have more from this expansion?"),
                      ("Voy carta por carta, como debe ser.", "I go card by card, as it should be."),
                      ("Si encuentro lo que busco, me llevo varias.", "If I find what I want, I'll take several.")],
    "international": [("Vengo de muy lejos para completar mi set.", "I came from far away to complete my set."),
                      ("En mi país estas cartas no se encuentran.", "You can't find these cards in my country."),
                      ("Me han dicho que aquí tenéis buen surtido.", "I was told you have a good selection here."),
                      ("Pago bien si está en buen estado.", "I pay well if it's in good shape."),
                      ("Mi colección está casi completa.", "My collection is almost complete."),
                      ("¡Qué tienda tan bonita!", "What a lovely shop!")],
    "legendary": [("He oído hablar de esta tienda...", "I've heard about this shop..."),
                  ("Mi colección es legendaria. Veamos si me sorprendes.", "My collection is legendary. Let's see if you surprise me."),
                  ("Hoy me apetece darme un capricho.", "I feel like treating myself today."),
                  ("No todos los días entro en una tienda así.", "I don't walk into a shop like this every day."),
                  ("Lo mejor que tengáis, por favor.", "Your very best, please."),
                  ("Hacía años que no veía un sitio con tanto encanto.", "I haven't seen a place this charming in years.")],
    "expansion": [("¡Ya ha salido la nueva expansión!", "The new expansion is out!"),
                  ("Vengo a por los sobres nuevos.", "I'm here for the new packs."),
                  ("¿Habéis abierto ya alguno del set nuevo?", "Have you opened any from the new set yet?"),
                  ("Llevo semanas esperando este día.", "I've waited weeks for this day."),
                  ("¡Qué ilustraciones tan bonitas tiene el set nuevo!", "The new set's artwork is gorgeous!"),
                  ("Hoy toca celebrar.", "Time to celebrate today.")],
    "limited_offer": [("¡He visto que hay ofertas!", "I saw there are deals!"),
                      ("Rápido, que se acaba la oferta.", "Quick, the offer's ending."),
                      ("A buen precio me lo llevo sin pensar.", "At a good price I'll grab it without thinking."),
                      ("¿Esto entra en la oferta?", "Is this part of the offer?"),
                      ("No me pierdo una oferta por nada.", "I never miss a deal."),
                      ("Vengo corriendo por la promoción.", "I ran here for the promotion.")],
    "expert": [("Interesante colección. Muy interesante.", "Interesting collection. Very interesting."),
               ("Esta carta podría sacar muy buena nota.", "This card could get a great grade."),
               ("Llevo años gradeando cartas.", "I've been grading cards for years."),
               ("Si gradeas, te dejo un descuento.", "If you grade, I'll give you a discount."),
               ("Fíjate en las esquinas, ahí está la clave.", "Look at the corners, that's the key."),
               ("Buen centrado en esta. Muy bien.", "Good centering on this one. Very nice.")],
    "job_seeker": [("Hola, ¿necesitáis a alguien?", "Hi, do you need anyone?"),
                   ("Busco trabajo, se me dan bien las cartas.", "I'm looking for work, I'm good with cards."),
                   ("Puedo empezar mañana mismo.", "I can start tomorrow."),
                   ("Tengo experiencia en caja.", "I have register experience."),
                   ("Me encantaría trabajar aquí.", "I'd love to work here."),
                   ("Si me contratas hoy, te sale barato.", "Hire me today and it'll be cheap.")],
    "indecisive": [("¿Este o el otro? No sé...", "This one or that one? I don't know..."),
                   ("Vuelvo a mirar el precio, por si acaso.", "Let me check the price again, just in case."),
                   ("Mmm... me lo pienso.", "Hmm... let me think."),
                   ("Ayer estaba más barato, ¿no?", "Wasn't it cheaper yesterday?"),
                   ("Lo cojo. No. Bueno, sí. No sé.", "I'll take it. No. Well, yes. I don't know."),
                   ("Voy a dar otra vuelta.", "I'll take another lap.")],
    "argue": [("¡Esa carta vale el doble y lo sabes!", "That card's worth double and you know it!"),
              ("¡Ni hablar, el set antiguo es mejor!", "No way, the old set is better!"),
              ("Las holográficas están sobrevaloradas.", "Holos are overrated."),
              ("¿Pero tú has visto los precios de hoy?", "Have you even seen today's prices?"),
              ("¡Charizard no es para tanto!", "Charizard isn't that great!"),
              ("Te digo que esa edición es falsa.", "I'm telling you that edition is fake."),
              ("¡Mi mazo ganaría al tuyo con los ojos cerrados!", "My deck would beat yours with my eyes closed!"),
              ("Bueno, vale, quizá tengas razón...", "Okay, fine, maybe you're right...")],
    "trade": [("¿Alguien cambia cartas?", "Anyone trading cards?"),
              ("Traigo repetidas para cambiar.", "I brought duplicates to trade."),
              ("Te cambio esta por una de las tuyas.", "I'll swap this for one of yours."),
              ("Hoy todo el mundo quiere intercambiar.", "Everyone wants to trade today."),
              ("Mi carpeta está llena de repes.", "My binder is full of duplicates."),
              ("Un cambio justo, ¿qué me dices?", "A fair trade, what do you say?")],
    "mystery": [("...", "..."), ("Interesante...", "Interesting..."), ("Ya veo.", "I see."),
                ("Volveremos a vernos.", "We'll meet again."), ("Hmm.", "Hmm."), ("Alguien vendrá a por esto.", "Someone will come for this.")],
    "meetup": [("¡Hemos quedado aquí para jugar!", "We're meeting here to play!"),
               ("¿Echamos una partida en la mesa?", "Shall we play a game at the table?"),
               ("Traigo mi mazo nuevo, preparaos.", "I brought my new deck, get ready."),
               ("Esta tienda tiene la mejor mesa de juego.", "This shop has the best play table."),
               ("Hoy hay torneíllo entre amigos.", "There's a little tournament among friends today."),
               ("¡Barajad, que empezamos!", "Shuffle up, we're starting!")],
    "rain": [("Qué chaparrón, menos mal que hay tienda.", "What a downpour, good thing there's a shop."),
             ("Vengo calado hasta los huesos.", "I'm soaked to the bone."),
             ("Con esta lluvia apetece mirar cartas tranquilo.", "This rain makes you want to browse cards calmly."),
             ("¡Me he mojado las cartas de camino!", "My cards got wet on the way!"),
             ("Me quedo un rato hasta que escampe.", "I'll stay a while until it clears."),
             ("¿Tenéis paraguas? Es broma.", "Got an umbrella? Just kidding.")],
    "expansion_news": [("¿Te has enterado? ¡Anuncian una expansión nueva!", "Did you hear? A new expansion was announced!"),
                       ("Dicen que la nueva expansión será increíble.", "They say the new expansion will be amazing."),
                       ("Ya estoy ahorrando para el lanzamiento.", "I'm already saving for the launch."),
                       ("¿Haréis fiesta de lanzamiento?", "Will you have a launch party?"),
                       ("He visto las primeras cartas filtradas.", "I've seen the first leaked cards."),
                       ("Esta semana no se habla de otra cosa.", "Nobody talks about anything else this week.")],
    "photos": [("¡Qué foto tan buena va a salir!", "This photo's going to look great!"),
               ("¿Te importa si hago unas fotos?", "Mind if I take a few photos?"),
               ("Esta luz es perfecta para fotografiar.", "This light is perfect for photos."),
               ("Para mi blog de tiendas de cartas.", "For my card shop blog."),
               ("Una más desde este ángulo...", "One more from this angle..."),
               ("¡Sonríe, expositor!", "Say cheese, display!")],
    "seller_collector": [("Vengo a vender parte de mi colección.", "I'm here to sell part of my collection."),
                         ("Tengo cartas que quizá te interesen.", "I have cards you might be interested in."),
                         ("No regalo nada, pero pido lo justo.", "I don't give anything away, but I ask what's fair."),
                         ("Necesito espacio en mi carpeta.", "I need room in my binder."),
                         ("Son cartas bien cuidadas, mira.", "They're well cared for cards, look."),
                         ("Precio de mercado, ni más ni menos.", "Market price, no more, no less.")],
}
for cat, lines in L.items():
    for i, (es, en) in enumerate(lines):
        k(f"tcgshop.evline.{cat}.{i}", es, en)

# ================================================================ reviews with memory
# tag: (short label es, en, [4 good (es,en)], [4 bad (es,en)])
R = {
    "flood": ("Tienda inundada", "Flooded shop",
              [("Había agua por el suelo, pero me atendieron genial", "There was water on the floor, but I was served great"),
               ("Un charco en la entrada no me quitó las ganas de comprar", "A puddle at the entrance didn't put me off buying"),
               ("El suelo mojado, sí, pero las cartas impecables", "The floor was wet, sure, but the cards were spotless"),
               ("Algo de agua por ahí, nada que me importara", "Some water around, nothing that bothered me")],
              [("La tienda estaba inundada, no pude comprar tranquilamente", "The shop was flooded, I couldn't shop in peace"),
               ("Casi me resbalo con un charco enorme", "I nearly slipped on a huge puddle"),
               ("Agua por todas partes y nadie la recogía", "Water everywhere and nobody mopped it up"),
               ("Se me mojaron las zapatillas dentro de la tienda", "My shoes got wet inside the shop")]),
    "fire": ("Fuego en la tienda", "Fire in the shop",
             [("Hubo un susto con algo quemado, pero todo salió bien", "There was a scare with something burnt, but it all went fine"),
              ("Olía a chamuscado, aunque la tienda es estupenda", "It smelled scorched, though the shop is great"),
              ("Un expositor medio quemado, lo demás perfecto", "One display half burnt, the rest perfect"),
              ("Un poco de humo, nada grave", "A bit of smoke, nothing serious")],
             [("Había un expositor chamuscado y daba miedo acercarse", "There was a scorched display and it was scary to go near"),
              ("Olía a quemado nada más entrar", "It smelled burnt the moment I walked in"),
              ("Quemaduras en el suelo y nadie decía nada", "Burn marks on the floor and nobody said anything"),
              ("No me sentí seguro con ese fuego reciente", "I didn't feel safe with that recent fire")]),
    "dark": ("Tienda a oscuras", "Dark shop",
             [("Había poca luz, pero encontré lo que buscaba", "The lighting was poor, but I found what I wanted"),
              ("Se fue la luz un rato, lo llevaron con calma", "The power went out for a while, they handled it calmly"),
              ("Algo oscuro, aunque con mucho encanto", "A bit dark, though very charming"),
              ("Con la linterna del móvil me apañé", "I managed with my phone's flashlight")],
             [("Estaba todo a oscuras y no se veían las cartas", "It was all dark and you couldn't see the cards"),
              ("Se fue la luz y nadie hizo nada", "The power went out and nobody did anything"),
              ("Tuve que adivinar los precios en la oscuridad", "I had to guess the prices in the dark"),
              ("Tan oscuro que me tropecé con un expositor", "So dark I tripped over a display")]),
    "blocked": ("Entrada bloqueada", "Blocked entrance",
                [("Costó entrar, pero mereció la pena", "It was hard to get in, but worth it"),
                 ("La entrada estaba complicada, dentro todo bien", "The entrance was tricky, everything fine inside"),
                 ("Esperé fuera un poco, nada grave", "I waited outside a bit, nothing serious"),
                 ("Algo bloqueaba la puerta, pero al final entré", "Something was blocking the door, but I got in eventually")],
                [("No había forma de entrar en la tienda", "There was no way to get into the shop"),
                 ("La puerta bloqueada y yo esperando fuera", "The door blocked and me waiting outside"),
                 ("Me cansé de esperar a que despejaran la entrada", "I got tired of waiting for them to clear the entrance"),
                 ("Una tienda a la que no se puede entrar no es una tienda", "A shop you can't get into isn't a shop")]),
    "mess": ("Tienda desordenada", "Messy shop",
             [("Estaba algo desordenada, pero con buenas cartas", "It was a bit messy, but with good cards"),
              ("Un poco de lío por el suelo, nada importante", "A bit of a mess on the floor, nothing important"),
              ("Desorden con encanto, como una tienda de verdad", "Charming mess, like a real shop"),
              ("Cosas tiradas por ahí, pero encontré mi carta", "Stuff lying around, but I found my card")],
             [("Había cosas tiradas por todas partes", "There was stuff lying everywhere"),
              ("Un desorden horrible, parecía un almacén", "A horrible mess, it looked like a warehouse"),
              ("No encontraba nada entre tanto lío", "I couldn't find anything in all that mess"),
              ("Daba pena ver la tienda tan desordenada", "It was sad to see the shop so messy")]),
    "dirt": ("Tienda sucia", "Dirty shop",
             [("Había tierra por el suelo, pero buen trato", "There was dirt on the floor, but good service"),
              ("Algo sucio el suelo, las cartas limpias", "The floor a bit dirty, the cards clean"),
              ("Unos agujeros raros en el suelo, curioso", "Some strange holes in the floor, curious"),
              ("Barro en la entrada, nada que no se limpie", "Mud at the entrance, nothing that can't be cleaned")],
             [("El suelo estaba lleno de tierra y agujeros", "The floor was full of dirt and holes"),
              ("Qué suciedad, me fui con los zapatos llenos de barro", "So dirty, I left with my shoes full of mud"),
              ("Nadie limpiaba aquel desastre", "Nobody cleaned up that disaster"),
              ("Parecía más un huerto que una tienda", "It looked more like a garden plot than a shop")]),
    "noise": ("Mucho ruido", "Too much noise",
              [("Había algo de jaleo, pero me lo pasé bien", "It was a bit noisy, but I had fun"),
               ("Mucho ruido, mucha vida", "Lots of noise, lots of life"),
               ("Algo de alboroto, buen ambiente al fin y al cabo", "A bit of a ruckus, a nice atmosphere after all"),
               ("Un poco de escándalo, nada que me molestara", "A bit of a racket, nothing that bothered me")],
              [("No había quien aguantara tanto ruido", "Nobody could stand that much noise"),
               ("Un escándalo constante, me dolía la cabeza", "Constant racket, my head hurt"),
               ("Con tanto jaleo no podía ni pensar", "With all that noise I couldn't even think"),
               ("Me fui antes de tiempo por el ruido", "I left early because of the noise")]),
    "theft": ("Robos en la tienda", "Thefts in the shop",
              [("Había ladrones por la zona, pero la tienda bien protegida", "There were thieves around, but the shop was well protected"),
               ("Vi un intento de robo y lo pararon a tiempo", "I saw a theft attempt and they stopped it in time"),
               ("Algo de inseguridad fuera, dentro tranquilo", "A bit unsafe outside, calm inside"),
               ("Sustos aparte, buena tienda", "Scares aside, good shop")],
              [("Vi cómo robaban y nadie hizo nada", "I saw someone steal and nobody did anything"),
               ("No me sentí seguro con tanto ladrón suelto", "I didn't feel safe with so many thieves around"),
               ("Agarré mi mochila todo el rato", "I held onto my backpack the whole time"),
               ("Esta tienda necesita un guardia ya", "This shop needs a guard right now")]),
    "slow": ("Atención lenta", "Slow service",
             [("El cajero iba despacio, pero fue muy amable", "The cashier was slow, but very kind"),
              ("Tardaron en cobrarme, nada grave", "It took a while to check out, no big deal"),
              ("Algo lentos en caja, buena tienda igualmente", "A bit slow at the register, good shop anyway"),
              ("Esperé un poco más de la cuenta, valió la pena", "I waited a bit longer than usual, worth it")],
             [("El cajero estaba en las nubes", "The cashier had their head in the clouds"),
              ("Tardaron una eternidad en cobrarme", "It took forever to check me out"),
              ("Atención lentísima, me desesperé", "Painfully slow service, I lost my patience"),
              ("Parecía que nadie quería cobrar", "It seemed nobody wanted to take my money")]),
    "rude": ("Cliente maleducado", "Rude customer",
             [("Había un cliente gritando, pero el dueño lo manejó bien", "There was a customer shouting, but the owner handled it well"),
              ("Un maleducado dando la nota, la tienda genial", "Some rude guy making a scene, the shop was great"),
              ("Un cliente pesado, nada que ver con la tienda", "An annoying customer, nothing to do with the shop"),
              ("Hubo un numerito, pero se calmó rápido", "There was a scene, but it calmed down quickly")],
             [("Un cliente maleducado no paraba de gritar", "A rude customer wouldn't stop shouting"),
              ("Nadie paró al tipo que molestaba a todos", "Nobody stopped the guy bothering everyone"),
              ("Me fui por culpa de un cliente insoportable", "I left because of an unbearable customer"),
              ("Mal ambiente con aquel cliente faltón", "Bad atmosphere with that rude customer")]),
    "broken": ("Algo roto", "Something broken",
               [("Había algo roto, pero lo demás funcionaba", "Something was broken, but the rest worked"),
                ("Un expositor averiado, buen surtido igualmente", "One display out of order, good selection anyway"),
                ("Algún desperfecto, nada que importe", "A bit of damage, nothing that matters"),
                ("Se notaba algo estropeado, pero bien atendido", "You could tell something was broken, but service was good")],
               [("Medio tienda estaba rota o averiada", "Half the shop was broken or out of order"),
                ("No podía ver las cartas del expositor roto", "I couldn't see the cards in the broken display"),
                ("Todo estropeado y sin arreglar", "Everything broken and unfixed"),
                ("Una tienda que se cae a trozos", "A shop falling to pieces")]),
    "hot": ("Calor agobiante", "Stifling heat",
            [("Hacía calor, pero las cartas compensan", "It was hot, but the cards made up for it"),
             ("Un poco de bochorno, buena tienda", "A bit muggy, good shop"),
             ("Calor dentro, aunque me atendieron bien", "Hot inside, though I was served well"),
             ("Sudé un poco, nada grave", "I sweated a bit, nothing serious")],
            [("Hacía un calor insoportable dentro", "It was unbearably hot inside"),
             ("Me asé mientras miraba las cartas", "I roasted while browsing the cards"),
             ("Sin aire acondicionado no se puede", "You can't shop without air conditioning"),
             ("Salí empapado en sudor", "I left drenched in sweat")]),
    "crowd": ("Mucha gente", "Crowded",
              [("Había mucha gente, señal de que es buena tienda", "It was crowded, a sign it's a good shop"),
               ("Lleno de clientes, gran ambiente", "Full of customers, great atmosphere"),
               ("Mucho movimiento, me gustó", "Lots going on, I liked it"),
               ("Apretados pero contentos", "Packed but happy")],
              [("Estaba tan lleno que no se podía ni andar", "It was so packed you couldn't even walk"),
               ("Un grupo con prisas lo puso todo patas arriba", "A hurried group turned everything upside down"),
               ("Demasiada gente y poca paciencia", "Too many people and little patience"),
               ("Agobiante, con empujones y todo", "Stifling, with pushing and all")]),
    "competition": ("Competencia", "Competition",
                    [("La otra tienda tiene ofertas, pero esta me gusta más", "The other shop has deals, but I like this one more"),
                     ("Comparé precios y aquí salgo ganando", "I compared prices and I come out ahead here"),
                     ("Con tanta competencia, esta tienda se esfuerza", "With so much competition, this shop makes an effort"),
                     ("Fieles a esta tienda, digan lo que digan", "Loyal to this shop, whatever they say")],
                    [("En la tienda de al lado está todo más barato", "Everything's cheaper at the shop next door"),
                     ("La competencia tiene mejores ofertas", "The competition has better deals"),
                     ("Me voy a la otra tienda, aquí es más caro", "I'm going to the other shop, it's pricier here"),
                     ("Con la competencia que hay, esto no se sostiene", "With the competition around, this doesn't hold up")]),
    "rumor": ("Fama de cara", "Pricey reputation",
              [("Decían que era cara y no es para tanto", "They said it was pricey and it isn't that bad"),
               ("Vine por los rumores y me llevé una sorpresa", "I came because of the rumors and was pleasantly surprised"),
               ("Los rumores mienten: precios razonables", "The rumors lie: reasonable prices"),
               ("No hagáis caso de lo que dicen por ahí", "Don't listen to what people say")],
              [("Todo el pueblo dice que es carísima y es verdad", "The whole town says it's super pricey and it's true"),
               ("Los rumores tenían razón: precios abusivos", "The rumors were right: abusive prices"),
               ("Fui con miedo por los precios y acerté", "I went worried about the prices and I was right"),
               ("Fama de cara bien ganada", "A well-earned reputation for being expensive")]),
    "weather": ("El tiempo", "The weather",
                [("Llovía a cántaros y aquí se estaba de lujo", "It was pouring and it was lovely in here"),
                 ("Un refugio perfecto en un día de lluvia", "A perfect shelter on a rainy day"),
                 ("Con este tiempo, nada mejor que mirar cartas", "In this weather, nothing beats browsing cards"),
                 ("Entré mojado y salí contento", "I came in wet and left happy")],
                [("Me mojé dentro de la propia tienda", "I got wet inside the shop itself"),
                 ("Llovía dentro tanto como fuera", "It rained inside as much as outside"),
                 ("Sin techo y con lluvia, menudo plan", "No roof and rain, what a plan"),
                 ("El viento se colaba por todas partes", "The wind got in everywhere")]),
    "inspect_bad": ("Inspección suspendida", "Failed inspection",
                    [("La inspección no fue bien, pero la tienda tiene potencial", "The inspection didn't go well, but the shop has potential"),
                     ("Suspendieron la inspección, aunque yo me sentí a gusto", "They failed the inspection, though I felt comfortable"),
                     ("Algunos fallos, pero buena intención", "A few faults, but good intentions"),
                     ("No pasó la inspección, a la próxima seguro", "It didn't pass the inspection, surely next time")],
                    [("Suspendida la inspección: olor, poca luz y desorden", "Failed inspection: smell, poor lighting and mess"),
                     ("Mi informe no puede ser positivo", "My report can't be positive"),
                     ("Demasiadas deficiencias para una tienda", "Too many shortcomings for a shop"),
                     ("Esta tienda no cumple lo mínimo", "This shop doesn't meet the minimum")]),
    "inspect_good": ("Inspección aprobada", "Passed inspection",
                     [("Inspección superada con nota", "Inspection passed with flying colors"),
                      ("Todo en orden: limpia, luminosa y segura", "Everything in order: clean, bright and safe"),
                      ("Un ejemplo para las demás tiendas", "An example for other shops"),
                      ("Mi informe será excelente", "My report will be excellent")],
                     [("Aprobada por los pelos", "Passed by a hair"),
                      ("Cumple, pero sin brillar", "It complies, but without shining"),
                      ("Aprobada, aunque con algunas pegas", "Passed, though with a few issues"),
                      ("Lo justo para aprobar", "Just enough to pass")]),
    "pkmn_cute": ("Pokémon adorable", "Adorable Pokémon",
                  [("¡Había un Pokémon adorable paseando por la tienda!", "There was an adorable Pokémon wandering the shop!"),
                   ("Me hice una foto con un Pokémon precioso", "I took a photo with a beautiful Pokémon"),
                   ("Un Pokémon muy mono me alegró el día", "A super cute Pokémon made my day"),
                   ("Ver al Pokémon por la tienda fue lo mejor", "Seeing the Pokémon around the shop was the best part")],
                  [("Mucho Pokémon mono, pero la tienda deja que desear", "Very cute Pokémon, but the shop leaves something to be desired"),
                   ("Me distraje con el Pokémon y poco más", "I got distracted by the Pokémon and not much else"),
                   ("Un Pokémon simpático no salva la tienda", "A friendly Pokémon doesn't save the shop"),
                   ("Lo único bueno fue el Pokémon", "The only good thing was the Pokémon")]),
    "pkmn_rare": ("Pokémon raro", "Rare Pokémon",
                  [("¡Vi un Pokémon rarísimo junto a la tienda!", "I saw a super rare Pokémon by the shop!"),
                   ("No me creo lo que vi hoy por aquí", "I can't believe what I saw around here today"),
                   ("Un Pokémon de los que no se ven nunca", "One of those Pokémon you never see"),
                   ("Vine a por cartas y vi un Pokémon legendario... casi", "I came for cards and saw a legendary Pokémon... almost")],
                  [("Un Pokémon raro, sí, pero la tienda regular", "A rare Pokémon, sure, but the shop was so-so"),
                   ("Bonito Pokémon, mala experiencia de compra", "Nice Pokémon, bad shopping experience"),
                   ("El Pokémon raro fue lo único destacable", "The rare Pokémon was the only highlight"),
                   ("Menos Pokémon y más atención, por favor", "Fewer Pokémon and more attention, please")]),
    "fun_event": ("Día especial", "Special day",
                  [("Había un ambiente de fiesta genial", "There was a great party atmosphere"),
                   ("Hoy había algo especial en la tienda", "There was something special in the shop today"),
                   ("Me lo pasé en grande con tanto movimiento", "I had a blast with all the activity"),
                   ("Un día para recordar en esta tienda", "A day to remember in this shop")],
                  [("Mucho evento y poca atención", "Lots of event and little attention"),
                   ("Tanta fiesta y no pude comprar a gusto", "So much party and I couldn't shop comfortably"),
                   ("Un evento caótico", "A chaotic event"),
                   ("Demasiado jaleo para mi gusto", "Too much fuss for my taste")]),
    "influencer": ("Visita de influencer", "Influencer visit",
                   [("Mis seguidores tienen que conocer esta tienda", "My followers need to know this shop"),
                    ("Vídeo grabado: ¡tienda recomendadísima!", "Video recorded: highly recommended shop!"),
                    ("Contenido de diez en esta tienda", "Ten-out-of-ten content at this shop"),
                    ("La pondré en mi lista de favoritas", "I'll put it on my list of favorites")],
                   [("No pienso recomendar esta tienda a mis seguidores", "I won't recommend this shop to my followers"),
                    ("El vídeo de hoy será una crítica", "Today's video will be a critique"),
                    ("Mis seguidores sabrán la verdad de esta tienda", "My followers will know the truth about this shop"),
                    ("Mala experiencia, y la contaré", "Bad experience, and I'll tell everyone")]),
    "clean": ("Tienda limpia", "Clean shop",
              [("Una tienda impecable, da gusto", "A spotless shop, a pleasure"),
               ("Limpia, ordenada y sin olores", "Clean, tidy and odorless"),
               ("Hasta el suelo brillaba", "Even the floor was shining"),
               ("Así deberían estar todas las tiendas", "That's how every shop should be")],
              [("Limpia, sí, pero poco más", "Clean, sure, but not much else"),
               ("Mucha limpieza y poco surtido", "Very clean and little selection"),
               ("Impecable pero sin alma", "Spotless but soulless"),
               ("La limpieza no lo es todo", "Cleanliness isn't everything")]),
    "decor": ("Buena decoración", "Great decoration",
              [("La decoración es preciosa", "The decoration is beautiful"),
               ("Qué ambiente tan bonito tiene esta tienda", "What a lovely atmosphere this shop has"),
               ("Cada rincón está cuidado al detalle", "Every corner is carefully looked after"),
               ("Da gusto pasar el rato aquí", "It's a pleasure to spend time here")],
              [("Bonita decoración, pero poco más", "Nice decoration, but not much else"),
               ("Mucho adorno y poca atención", "Lots of decoration and little attention"),
               ("La decoración no compensa lo demás", "The decoration doesn't make up for the rest"),
               ("Bonita por fuera, floja por dentro", "Pretty outside, weak inside")]),
    "staff_good": ("Gran atención", "Great service",
                   [("El cajero fue rapidísimo y muy simpático", "The cashier was super fast and very friendly"),
                    ("Atención de diez en caja", "Ten-out-of-ten service at the register"),
                    ("Ni un minuto de espera, increíble", "Not a minute of waiting, incredible"),
                    ("El personal de esta tienda es lo mejor", "The staff at this shop are the best")],
                   [("El cajero fue rápido, lo demás no tanto", "The cashier was quick, the rest not so much"),
                    ("Buena atención, mala tienda", "Good service, bad shop"),
                    ("El empleado salva la visita, a medias", "The employee half saves the visit"),
                    ("Rápidos cobrando, pero poco más", "Quick at the register, but not much else")]),
}
# _fixed: there was a problem but it was fixed before this customer arrived ({d} = per-family detail)
FIX_GOOD = [("Hubo un problema {d}, pero lo solucionaron rápido", "There was a problem {d}, but they fixed it quickly"),
            ("Se notaba que había pasado algo, pero ya estaba arreglado", "You could tell something had happened, but it was already fixed"),
            ("Me contaron que hubo un lío {d}, pero cuando llegué todo estaba en orden", "I heard there had been trouble {d}, but when I arrived everything was in order"),
            ("Reaccionaron rápido ante el problema, se agradece", "They reacted quickly to the problem, much appreciated")]
FIX_BAD = [("Lo arreglaron, pero aún se notaba lo que había pasado", "They fixed it, but you could still tell what had happened"),
           ("Arreglado tarde y mal, se veían los restos", "Fixed late and badly, you could see the leftovers"),
           ("Me hablaron del desastre de hoy {d} y no me dio confianza", "They told me about today's disaster {d} and it didn't inspire confidence"),
           ("Aunque lo solucionaron, el ambiente seguía raro", "Even though they solved it, the atmosphere was still odd")]
FIX_TAGS = ["flood", "fire", "dark", "blocked", "mess", "dirt", "noise", "theft", "slow", "rude", "broken", "hot", "crowd", "competition", "rumor"]
FIX_DETAIL = {
    "flood": ("con el agua", "with the water"), "fire": ("con el fuego", "with the fire"), "dark": ("con la luz", "with the lights"),
    "blocked": ("con la entrada", "with the entrance"), "mess": ("con el desorden", "with the mess"), "dirt": ("con la suciedad", "with the dirt"),
    "noise": ("con el ruido", "with the noise"), "theft": ("con los ladrones", "with the thieves"), "slow": ("en la caja", "at the register"),
    "rude": ("con un cliente maleducado", "with a rude customer"), "broken": ("con algo roto", "with something broken"),
    "hot": ("con el calor", "with the heat"), "crowd": ("con tanta gente", "with the crowd"), "competition": ("con la competencia", "with the competition"),
    "rumor": ("con los rumores", "with the rumors"),
}
for tag in FIX_TAGS:
    es_d, en_d = FIX_DETAIL[tag]
    good = [(a.replace("{d}", es_d), b.replace("{d}", en_d)) for a, b in FIX_GOOD]
    bad = [(a.replace("{d}", es_d), b.replace("{d}", en_d)) for a, b in FIX_BAD]
    lab_es, lab_en = R[tag][0], R[tag][1]
    R[tag + "_fixed"] = (lab_es + " (arreglado)", lab_en + " (fixed)", good, bad)


def load_reviews_module():
    spec = importlib.util.spec_from_file_location("gen_lang_reviews_core", os.path.join(HERE, "gen_lang_reviews.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


rv = load_reviews_module()
for tag, (les, len_, good, bad) in R.items():
    key = "ev_" + tag
    k("tcgshop.review." + key, les, len_)
    assert len(good) == 4 and len(bad) == 4, tag
    for lang, target in (("es", ES), ("en", EN)):
        li = 0 if lang == "es" else 1
        for stars in range(1, 6):
            band = good if stars >= 4 else bad if stars <= 2 else [good[0], bad[0], good[1], bad[1]]
            frames = rv.FRAME[lang][stars]
            for n in range(8):
                target[f"tcgshop.reviewtext.{key}.{stars}.{n}"] = rv.fit(frames[n], band[n % 4][li])


# ================================================================ write
def merge(fname, add):
    p = os.path.join(LANG, fname)
    with open(p, encoding="utf-8") as f:
        data = json.load(f)
    data.update(add)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return len(add)


if __name__ == "__main__":
    assert set(ES) == set(EN), set(ES) ^ set(EN)
    print("es_es.json:", merge("es_es.json", ES), "keys")
    print("en_us.json:", merge("en_us.json", EN), "keys")
