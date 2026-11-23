# -*- coding: utf-8 -*-
"""0.9 - random events, ACTORS part (Cobblemon Pokémon that visit the shop): names, descriptions,
notices and fixes for the actor events, module messages (tcgshop.actor.*), customer lines
(tcgshop.evline.act_*) and memory reviews specific to each Pokémon (tcgshop.review.ev_pkmn_* and
tcgshop.reviewtext.ev_pkmn_*.<1-5>.<0-7>, same pattern as gen_lang_09_events_core.py / gen_lang_reviews.py).

Only sets ITS OWN keys in es_es.json and en_us.json (idempotent). Reloads the JSON right before writing.
Usage: python -I -X utf8 tools/gen_lang_09_events_actors.py
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


def event(id, name, desc, start, fix=None):
    base = "tcgshop.event." + id
    k(base + ".name", name[0], name[1])
    k(base + ".desc", desc[0], desc[1])
    k(base + ".start", start[0], start[1])
    if fix:
        k(base + ".fix", fix[0], fix[1])


SHOO_FEED_BATTLE = ("Espántalo con la mano vacía (clic derecho varias veces), dale una baya o, agachado + clic derecho, combate contra él.",
                    "Shoo it away with an empty hand (right-click a few times), give it a berry or, sneaking + right-click, battle it.")

# ================================================================ negative
event("blocked_entrance", ("Un Pokémon bloquea la entrada", "A Pokémon blocks the entrance"),
      ("Un Pokémon enorme (un Snorlax dormilón, un Wobbuffet testarudo o la familia de Psyduck) se ha plantado junto a la puerta. Mientras siga ahí, los clientes no entran por esa puerta y los que esperan se impacientan antes. Nunca tapa el hueco de la puerta: puedes pasar.",
       "A huge Pokémon (a sleepy Snorlax, a stubborn Wobbuffet or the Psyduck family) has settled next to the door. While it stays there, customers won't come in through that door and those waiting get impatient sooner. It never covers the doorway itself: you can get through."),
      ("¡Hay un Pokémon enorme plantado junto a la puerta de la tienda!", "There's a huge Pokémon parked next to the shop door!"),
      ("Despiértalo y que se vaya: clic derecho con la mano vacía muchas veces, dale una baya o, agachado + clic derecho, combate contra él.",
       "Wake it up and get it to leave: right-click with an empty hand many times, give it a berry or, sneaking + right-click, battle it."))
event("charmander_scorch", ("Charmander descontrolado", "Out-of-control Charmander"),
      ("Un Charmander nervioso se ha colado en la tienda y ha dejado marcas de quemado junto a uno o dos expositores. Esos expositores no se pueden usar hasta que limpies las marcas. Nunca hay fuego de verdad.",
       "A nervous Charmander slipped into the shop and left scorch marks next to one or two displays. Those displays can't be used until you clean the marks. There's never real fire."),
      ("¡Un Charmander ha entrado y ha chamuscado los expositores!", "A Charmander got in and scorched the displays!"),
      ("Limpia las marcas de quemado (kit de reparación). Además puedes echar al Charmander: mano vacía, una baya o un combate (agachado + clic derecho).",
       "Clean the scorch marks (repair kit). You can also get the Charmander out: empty hand, a berry or a battle (sneaking + right-click)."))
event("geodude_rampage", ("Geodude revoltoso", "Rowdy Geodude"),
      ("Un Geodude ha bajado de la montaña rodando y ha dejado escombros junto a los expositores. Hasta que lo recojas, la decoración de la tienda cuenta menos.",
       "A Geodude came rolling down from the mountain and left debris next to the displays. Until you clear it up, the shop's decoration counts for less."),
      ("¡Un Geodude ha entrado rodando y lo ha llenado todo de piedras!", "A Geodude came rolling in and covered everything in rocks!"),
      ("Recoge los escombros (con la mano vacía o el kit). El Geodude se va si lo espantas, le das una baya o le vences en combate.",
       "Clear the debris (empty hand or the kit). The Geodude leaves if you shoo it, give it a berry or beat it in battle."))
event("psyduck_confused", ("Psyduck confundido", "Confused Psyduck"),
      ("Un Psyduck con dolor de cabeza se ha chocado con un expositor y ha tirado productos. Esas casillas no se pueden comprar hasta que lo arregles (tus objetos no se mueven de su sitio).",
       "A Psyduck with a headache bumped into a display and knocked products over. Those slots can't be bought until you sort it out (your items don't move from their place)."),
      ("¡Un Psyduck confundido se ha chocado contra un expositor!", "A confused Psyduck crashed into a display!"),
      SHOO_FEED_BATTLE)
event("jigglypuff_song", ("Jigglypuff cantante", "Singing Jigglypuff"),
      ("Un Jigglypuff ha decidido dar un concierto en tu tienda. Los clientes llegan de peor humor y con menos paciencia, y cada poco algunos de los que están dentro se van (dejando lo que iban a comprar en su sitio).",
       "A Jigglypuff has decided to give a concert in your shop. Customers arrive in a worse mood and with less patience, and every so often some of those inside leave (putting back what they were going to buy)."),
      ("¡Un Jigglypuff está cantando en la tienda y los clientes se duermen!", "A Jigglypuff is singing in the shop and customers are falling asleep!"),
      ("Haz que se vaya: clic derecho con la mano vacía, dale una baya o, agachado + clic derecho, combate contra él.",
       "Get it to leave: right-click with an empty hand, give it a berry or, sneaking + right-click, battle it."))
event("murkrow_thief", ("Murkrow ladrón", "Thieving Murkrow"),
      ("Un Murkrow se ha llevado algo de un expositor (lo más barato). Esa casilla queda vacía para los clientes hasta que lo recuperes. Si el evento termina sin recuperarlo, pierdes su valor de la caja. Un guardia lo recupera solo.",
       "A Murkrow made off with something from a display (the cheapest item). That slot looks empty to customers until you get it back. If the event ends before you do, you lose its value from the register. A guard gets it back on their own."),
      ("¡Un Murkrow ha entrado volando y va a por tus expositores!", "A Murkrow flew in and is going for your displays!"),
      ("Espántalo con la mano vacía (clic derecho varias veces) o, agachado + clic derecho, combate contra él. Si tienes guardia, él se encarga.",
       "Shoo it away with an empty hand (right-click a few times) or, sneaking + right-click, battle it. If you have a guard, they'll handle it."))
event("diglett_holes", ("Diglett excavador", "Digging Diglett"),
      ("Un Diglett ha hecho agujeros por toda la tienda y ha dejado montones de tierra por el suelo.",
       "A Diglett has dug holes all over the shop and left piles of dirt on the floor."),
      ("¡Un Diglett está excavando en el suelo de tu tienda!", "A Diglett is digging up your shop floor!"),
      ("Limpia la tierra (escoba o mano vacía). Puedes echar al Diglett con la mano vacía, una baya o un combate (agachado + clic derecho).",
       "Clean up the dirt (broom or empty hand). You can get the Diglett out with an empty hand, a berry or a battle (sneaking + right-click)."))
event("pidgey_invasion", ("Invasión de Pidgey", "Pidgey invasion"),
      ("Una bandada de Pidgey se ha colado por la puerta y lo ha dejado todo perdido de excrementos junto a la entrada.",
       "A flock of Pidgey got in through the door and made a mess of droppings all around the entrance."),
      ("¡Una bandada de Pidgey ha invadido la tienda!", "A flock of Pidgey has invaded the shop!"),
      ("Limpia los excrementos. Espantar a los Pidgey (un clic derecho con la mano vacía a cada uno) evita que sigan molestando, pero lo que dejaron sigue ahí.",
       "Clean up the droppings. Shooing the Pidgey (one empty-hand right-click each) stops them bothering people, but what they left is still there."))

# ================================================================ neutral
event("rotom_register", ("Rotom en la caja registradora", "Rotom in the cash register"),
      ("Un Rotom se ha metido en la caja registradora. El cobro va a tirones: a ratos va rapidísimo y a ratos se atasca.",
       "A Rotom got into the cash register. Checkout is hit-and-miss: sometimes it's super fast, sometimes it jams."),
      ("¡Un Rotom se ha metido en la caja registradora!", "A Rotom got into the cash register!"),
      ("Si te molesta, sácalo con la mano vacía (clic derecho varias veces).", "If it bothers you, get it out with an empty hand (right-click a few times)."))
event("meowth_explorer", ("Meowth explorador", "Explorer Meowth"),
      ("Un Meowth curioso ha entrado a husmear entre los expositores. No hace daño a nadie.",
       "A curious Meowth came in to sniff around the displays. It doesn't hurt anyone."),
      ("Un Meowth ha entrado a curiosear por la tienda.", "A Meowth came in to look around the shop."))
event("vulpix_warmth", ("Vulpix busca calor", "Vulpix seeks warmth"),
      ("Hace frío y un Vulpix se ha acurrucado en el rincón más iluminado de la tienda.",
       "It's cold and a Vulpix has curled up in the brightest corner of the shop."),
      ("Un Vulpix con frío se ha refugiado en tu tienda.", "A cold Vulpix has taken shelter in your shop."))
event("combee_flowers", ("Combee en las flores", "Combee in the flowers"),
      ("Unos Combee zumban junto a la entrada, buscando néctar.", "Some Combee are buzzing by the entrance, looking for nectar."),
      ("Unos Combee revolotean junto a la puerta de la tienda.", "Some Combee are fluttering by the shop door."))

# ================================================================ positive
event("pikachu_visit", ("Visita de Pikachu", "Pikachu visit"),
      ("Un Pikachu salvaje pasea por la tienda. Los clientes se animan: llegan algo más y de mejor humor mientras esté.",
       "A wild Pikachu is wandering around the shop. Customers love it: a few more come in, and in a better mood, while it's here."),
      ("¡Un Pikachu salvaje ha entrado en la tienda!", "A wild Pikachu came into the shop!"))
event("eevee_curious", ("Eevee curioso", "Curious Eevee"),
      ("Un Eevee curiosea entre los expositores. Los clientes, encantados: llegan algo más y de mejor humor.",
       "An Eevee is poking around the displays. Customers are delighted: a few more come in, in a better mood."),
      ("Un Eevee curioso está explorando tu tienda.", "A curious Eevee is exploring your shop."))
event("chansey_kind", ("Chansey amable", "Kind Chansey"),
      ("Una Chansey amable recorre la tienda y tranquiliza a todo el mundo: los clientes tienen más paciencia y mejor humor.",
       "A kind Chansey walks around the shop calming everyone down: customers are more patient and in a better mood."),
      ("Una Chansey amable ha venido a ayudar en la tienda.", "A kind Chansey has come to help in the shop."))
event("butterfree_spring", ("Butterfree primaveral", "Springtime Butterfree"),
      ("Un Butterfree revolotea entre los expositores. Es tan bonito que atrae a algún coleccionista más de lo normal.",
       "A Butterfree is fluttering between the displays. It's so pretty it draws in a few more collectors than usual."),
      ("Un Butterfree ha entrado revoloteando en la tienda.", "A Butterfree came fluttering into the shop."))
event("lucky_meowth", ("Meowth afortunado", "Lucky Meowth"),
      ("Un Meowth con una moneda brillante en la frente pasea por la tienda. Si le acaricias o le das una baya, a veces suelta alguna moneda (poca cosa).",
       "A Meowth with a shiny coin on its forehead is wandering the shop. If you pet it or give it a berry, it sometimes drops a coin (nothing big)."),
      ("Un Meowth muy elegante ha entrado en la tienda. ¿Será de la suerte?", "A very fancy Meowth came into the shop. Could it be lucky?"))
event("togepi_family", ("Togepi adorable", "Adorable Togepi"),
      ("Un Togepi se ha colado en la tienda y una familia viene a verlo. Gastan poco y prefieren sobres, pero se lo pasan en grande.",
       "A Togepi wandered into the shop and a family is coming to see it. They spend little and prefer packs, but they have a great time."),
      ("¡Un Togepi adorable está en tu tienda! Viene una familia a verlo.", "An adorable Togepi is in your shop! A family is coming to see it."))
event("bulbasaur_gardener", ("Bulbasaur jardinero", "Gardener Bulbasaur"),
      ("Un Bulbasaur se ha puesto a cuidar las plantas de la entrada. Hoy la decoración de la tienda cuenta un poco más (no coloca ni quita bloques).",
       "A Bulbasaur has started tending the plants by the entrance. Today the shop's decoration counts a little more (it doesn't place or remove blocks)."),
      ("Un Bulbasaur está arreglando las plantas de la entrada.", "A Bulbasaur is tidying up the plants by the entrance."))
event("calm_ambient", ("Ambiente tranquilo", "Calm atmosphere"),
      ("Un Pokémon tranquilo (un Psyduck, un Abra dormido o un Slowpoke) se ha quedado en la tienda y todo el mundo se relaja: más paciencia y mejor humor. Si tocas al Abra, se teletransporta.",
       "A calm Pokémon (a Psyduck, a sleeping Abra or a Slowpoke) has settled in the shop and everyone relaxes: more patience and better mood. If you touch the Abra, it teleports away."),
      ("Un Pokémon muy tranquilo se ha quedado en la tienda.", "A very calm Pokémon has settled in the shop."))
event("flaaffy_glow", ("Flaaffy luminoso", "Glowing Flaaffy"),
      ("Un Flaaffy ilumina la tienda con la luz de su cola. Si había un apagón o un cortocircuito, lo arregla al momento.",
       "A Flaaffy lights up the shop with its glowing tail. If there was a power failure or a short circuit, it fixes it right away."),
      ("Un Flaaffy ha entrado y la tienda se ha iluminado.", "A Flaaffy came in and the shop lit up."))
event("tropius_fruit", ("Tropius frutal", "Fruity Tropius"),
      ("Un Tropius descansa junto a la entrada. Si le acaricias suelta algo de fruta, y si le das una baya, alguna baya más (una vez cada cosa).",
       "A Tropius is resting by the entrance. If you pet it, it drops some fruit, and if you give it a berry, a few more berries (once each)."),
      ("¡Un Tropius ha venido a descansar junto a tu tienda!", "A Tropius has come to rest by your shop!"))
event("guardian_pokemon", ("Pokémon protector", "Guardian Pokémon"),
      ("Un Pokémon fuerte (un Lucario en zonas de montaña o nieve, un Growlithe en el resto) vigila la entrada todo el día. Hoy no se acerca ningún ladrón.",
       "A strong Pokémon (a Lucario in mountain or snowy areas, a Growlithe elsewhere) guards the entrance all day. No thief will come near today."),
      ("Un Pokémon protector se ha quedado vigilando la puerta de tu tienda.", "A guardian Pokémon is keeping watch at your shop door."))

# ================================================================ module messages (tcgshop.actor.*)
k("tcgshop.actor.only_owner", "Sólo el dueño de la tienda puede encargarse de este Pokémon.", "Only the shop owner can deal with this Pokémon.")
k("tcgshop.actor.shoo_progress", "Espantando a %s… %s/%s", "Shooing %s… %s/%s")
k("tcgshop.actor.shooed", "%s se ha ido.", "%s left.")
k("tcgshop.actor.fed", "%s se come la baya encantado.", "%s happily eats the berry.")
k("tcgshop.actor.fed_leaves", "%s se come la baya y se marcha contento.", "%s eats the berry and leaves happy.")
k("tcgshop.actor.full", "%s ya ha comido.", "%s has already eaten.")
k("tcgshop.actor.pet", "%s parece muy contento.", "%s looks very happy.")
k("tcgshop.actor.teleported", "¡%s se ha despertado del susto y se ha teletransportado!", "%s woke up startled and teleported away!")
k("tcgshop.actor.battle_start", "¡Combate contra %s!", "Battle against %s!")
k("tcgshop.actor.battle_cant", "Ahora no se puede combatir (¿tienes un Pokémon listo y no estás en otro combate?).",
  "You can't battle right now (do you have a Pokémon ready and aren't in another battle?).")
k("tcgshop.actor.battle_won", "¡Has vencido a %s! Se marcha de la tienda.", "You beat %s! It leaves the shop.")
k("tcgshop.actor.battle_lost", "%s sigue en la tienda. Puedes volver a intentarlo.", "%s is still in the shop. You can try again.")
k("tcgshop.actor.hint", "%s: %s", "%s: %s")
k("tcgshop.actor.way.shoo", "mano vacía ×%s para espantarlo", "empty hand ×%s to shoo it")
k("tcgshop.actor.way.feed", "dale una baya", "give it a berry")
k("tcgshop.actor.way.battle", "agachado + clic derecho para combatir", "sneak + right-click to battle")
k("tcgshop.actor.way.pet", "mano vacía para acariciarlo", "empty hand to pet it")
k("tcgshop.actor.gone_rest", "%s se ha ido, pero aún queda trabajo en la tienda.", "%s left, but there's still work to do in the shop.")
k("tcgshop.actor.theft", "¡%s se ha llevado %s de un expositor! Recupéralo antes de que acabe el evento.",
  "%s made off with %s from a display! Get it back before the event ends.")
k("tcgshop.actor.theft_guard", "Tu guardia ha espantado a %s y ha recuperado lo robado.", "Your guard scared off %s and got the stolen item back.")
k("tcgshop.actor.theft_recovered", "Has recuperado lo que se llevó %s.", "You got back what %s took.")
k("tcgshop.actor.theft_lost", "%s se escapó con lo robado: pierdes %s de la caja.", "%s got away with the loot: you lose %s from the register.")
k("tcgshop.actor.slots", "%s ha tirado productos: %s casillas no se pueden comprar hasta que lo arregles.",
  "%s knocked products over: %s slots can't be bought until you sort it out.")
k("tcgshop.actor.customers_left", "El canto de %s ha hecho que %s clientes se vayan.", "%s's singing made %s customers leave.")
k("tcgshop.actor.helped", "¡%s ha arreglado «%s»!", "%s fixed \"%s\"!")

# ================================================================ customer lines (tcgshop.evline.act_*.<0-5>)
L = {
    "act_charmander": [("¿Huele a quemado o soy yo?", "Does it smell like something's burning or is it just me?"),
                       ("¡Cuidado, que ese Charmander está que echa chispas!", "Careful, that Charmander is fuming!"),
                       ("Mira esas marcas negras en el expositor...", "Look at those black marks on the display..."),
                       ("Un Charmander en una tienda de cartas, qué peligro.", "A Charmander in a card shop, how dangerous."),
                       ("Espero que no se queme ninguna carta.", "I hope none of the cards get burned."),
                       ("¡Que alguien le dé una baya a ese Charmander!", "Somebody give that Charmander a berry!")],
    "act_blocked": [("¡Hay un Pokémon gigante en la puerta!", "There's a giant Pokémon at the door!"),
                    ("He tenido que dar la vuelta entera para entrar.", "I had to go all the way around to get in."),
                    ("¿Eso que ronca en la entrada es un Snorlax?", "Is that a Snorlax snoring at the entrance?"),
                    ("Menudo atasco hay en la puerta.", "What a traffic jam at the door."),
                    ("No sabía si podía pasar por ahí...", "I didn't know if I could get through there..."),
                    ("Alguien debería despertar a ese Pokémon.", "Someone should wake that Pokémon up.")],
    "act_geodude": [("¡Casi me tropiezo con una piedra!", "I almost tripped on a rock!"),
                    ("¿Ha habido un derrumbe aquí dentro?", "Was there a rockslide in here?"),
                    ("Ese Geodude no para de rodar.", "That Geodude won't stop rolling around."),
                    ("Cuidado con los escombros, que hay muchos.", "Watch out for the debris, there's a lot."),
                    ("Vaya, la decoración ha quedado hecha polvo.", "Wow, the decoration got wrecked."),
                    ("Esto parece una cueva más que una tienda.", "This looks more like a cave than a shop.")],
    "act_psyduck": [("Pobre Psyduck, qué dolor de cabeza tiene.", "Poor Psyduck, what a headache it has."),
                    ("¡Se ha chocado contra el expositor otra vez!", "It crashed into the display again!"),
                    ("Psy... ¿ay? Ese Psyduck no sabe ni dónde está.", "Psy... ow? That Psyduck doesn't even know where it is."),
                    ("Hay cosas por el suelo, ¿eh?", "There's stuff on the floor, huh?"),
                    ("Quería mirar ese expositor, pero está patas arriba.", "I wanted to look at that display, but it's a mess."),
                    ("Alguien debería ayudar a ese Psyduck.", "Someone should help that Psyduck.")],
    "act_jigglypuff": [("Qué sueño me está entrando...", "I'm getting so sleepy..."),
                       ("¡Que alguien pare esa canción!", "Somebody stop that song!"),
                       ("Jiggly... jiggly... zzz...", "Jiggly... jiggly... zzz..."),
                       ("Me encanta la canción, pero no puedo ni mirar las cartas.", "I love the song, but I can't even look at the cards."),
                       ("Si me duermo, que alguien me despierte.", "If I fall asleep, someone wake me up."),
                       ("Ese Jigglypuff está muy enfadado, ¿no?", "That Jigglypuff looks really grumpy, doesn't it?")],
    "act_murkrow": [("¿Has visto ese Murkrow? Va a por lo que brilla.", "Did you see that Murkrow? It goes for anything shiny."),
                    ("Agarro bien mi cartera, que hay un Murkrow suelto.", "I'm holding my wallet tight, there's a Murkrow on the loose."),
                    ("Juraría que en ese hueco había algo antes.", "I'd swear there was something in that slot before."),
                    ("Los Murkrow traen mala suerte, dicen.", "Murkrow bring bad luck, they say."),
                    ("¡Ese pájaro negro se ha llevado algo!", "That black bird just took something!"),
                    ("¿No hay guardia en esta tienda?", "Isn't there a guard in this shop?")],
    "act_diglett": [("¡El suelo está lleno de agujeros!", "The floor is full of holes!"),
                    ("Ha salido una cabeza del suelo, ¿lo has visto?", "A head just popped out of the floor, did you see it?"),
                    ("Cuidado, que te hundes en la tierra.", "Careful, you'll sink into the dirt."),
                    ("Dig, dig, dig... ese Diglett no descansa.", "Dig, dig, dig... that Diglett never rests."),
                    ("Me he manchado los zapatos de tierra.", "I got dirt all over my shoes."),
                    ("Esto parece un huerto, no una tienda.", "This looks like a garden, not a shop.")],
    "act_pidgey": [("¡Cuidado, que vienen los Pidgey!", "Watch out, here come the Pidgey!"),
                   ("Puaj, ¿qué es eso de la entrada?", "Yuck, what's that at the entrance?"),
                   ("Hay más Pidgey que clientes.", "There are more Pidgey than customers."),
                   ("Casi me cae algo en la cabeza.", "Something almost landed on my head."),
                   ("No piso por ahí ni loco.", "No way I'm stepping over there."),
                   ("¿Quién ha dejado la puerta abierta a los pájaros?", "Who let the birds in?")],
    "act_rotom": [("La caja hace ruidos raros...", "The register is making weird noises..."),
                  ("¡La pantalla de la caja me ha guiñado un ojo!", "The register screen just winked at me!"),
                  ("Hoy cobran rapidísimo... o nada.", "Today checkout is super fast... or not at all."),
                  ("Juraría que esa caja se ríe.", "I'd swear that register is laughing."),
                  ("Bzzzt... ¿eso era un Rotom?", "Bzzzt... was that a Rotom?"),
                  ("Espero que me cobren bien con la caja así.", "I hope they charge me right with the register like that.")],
    "act_cute": [("¡Ay, qué Pokémon tan mono!", "Aww, what a cute Pokémon!"),
                 ("He venido solo para verlo.", "I came just to see it."),
                 ("¿Puedo hacerle una foto?", "Can I take a picture of it?"),
                 ("Qué buen ambiente con ese Pokémon por aquí.", "Such a nice vibe with that Pokémon around."),
                 ("Me han dicho que había un Pokémon en la tienda.", "Someone told me there was a Pokémon in the shop."),
                 ("¡Mira, mira, ahí está!", "Look, look, there it is!")],
    "act_chansey": [("Qué tranquila se está con esa Chansey.", "It's so calm with that Chansey around."),
                    ("La Chansey me ha sonreído.", "The Chansey smiled at me."),
                    ("No me importa esperar, de verdad.", "I don't mind waiting, really."),
                    ("Esa Chansey debería trabajar aquí.", "That Chansey should work here."),
                    ("Me siento mejor solo de verla.", "I feel better just looking at it."),
                    ("¡Chansey, Chansey!", "Chansey, Chansey!")],
    "act_butterfree": [("¡Mira qué Butterfree tan bonito!", "Look at that beautiful Butterfree!"),
                       ("Huele a primavera aquí dentro.", "It smells like spring in here."),
                       ("He seguido al Butterfree hasta la tienda.", "I followed the Butterfree to the shop."),
                       ("Qué alas tan preciosas.", "What gorgeous wings."),
                       ("Ya que estoy, miro alguna carta buena.", "While I'm here, I'll look at some good cards."),
                       ("Un día perfecto para coleccionar.", "A perfect day for collecting.")],
    "act_meowth": [("¡Un Meowth! ¿Tendrá monedas?", "A Meowth! Does it have coins?"),
                   ("Ese Meowth no para de mirar el dinero.", "That Meowth keeps staring at the money."),
                   ("Dicen que los Meowth traen suerte.", "They say Meowth bring good luck."),
                   ("¿Es el del Team Rocket? No, este es otro.", "Is it the Team Rocket one? No, this is a different one."),
                   ("Miau... digo, hola.", "Meow... I mean, hi."),
                   ("Qué Meowth tan elegante.", "What a classy Meowth.")],
    "act_bulbasaur": [("Qué bonita está la entrada hoy.", "The entrance looks so nice today."),
                      ("¡Hay un Bulbasaur regando las plantas!", "There's a Bulbasaur watering the plants!"),
                      ("Se nota que alguien ha cuidado las flores.", "You can tell someone has looked after the flowers."),
                      ("Bulba... ¡qué mono!", "Bulba... so cute!"),
                      ("Da gusto entrar con este jardín.", "It's a pleasure coming in with this garden."),
                      ("Esta tienda tiene muy buen gusto.", "This shop has great taste.")],
    "act_calm": [("Qué paz se respira aquí.", "It's so peaceful in here."),
                 ("Ese Pokémon está más tranquilo que yo.", "That Pokémon is calmer than I am."),
                 ("Sin prisas, hoy miro todo con calma.", "No rush, today I'll take my time."),
                 ("¿Está dormido? Shhh...", "Is it asleep? Shhh..."),
                 ("Me quedaría aquí toda la tarde.", "I could stay here all afternoon."),
                 ("Qué Pokémon tan relajado.", "What a relaxed Pokémon.")],
    "act_flaaffy": [("¡Qué luz tan bonita da ese Flaaffy!", "What a lovely light that Flaaffy gives off!"),
                    ("Ahora sí que se ven bien las cartas.", "Now you can really see the cards."),
                    ("Esa cola brilla como una lámpara.", "That tail glows like a lamp."),
                    ("Menos mal, estaba todo muy oscuro.", "Thank goodness, it was so dark."),
                    ("Flaaffy, ¡eres la mejor bombilla!", "Flaaffy, you're the best light bulb!"),
                    ("Se me ponen los pelos de punta con tanta electricidad.", "All this electricity is making my hair stand on end.")],
    "act_combee": [("Cuidado con los Combee de la puerta.", "Careful with the Combee by the door."),
                   ("Bzzz... ¿eso son Combee?", "Bzzz... are those Combee?"),
                   ("Las flores de fuera están llenas de vida.", "The flowers outside are full of life."),
                   ("Me encanta ver Combee trabajando.", "I love watching Combee at work."),
                   ("Espero que no me piquen.", "I hope they don't sting me."),
                   ("Qué entrada tan animada.", "What a lively entrance.")],
    "act_tropius": [("¡Un Tropius! Y huele a fruta.", "A Tropius! And it smells like fruit."),
                    ("No sabía que había Tropius por aquí.", "I didn't know there were Tropius around here."),
                    ("¿Se le pueden coger los plátanos?", "Can you pick its bananas?"),
                    ("Qué Pokémon tan enorme y tan tranquilo.", "Such a huge and calm Pokémon."),
                    ("Esto hay que contarlo.", "I have to tell people about this."),
                    ("He venido corriendo al enterarme.", "I came running when I heard.")],
    "act_guardian": [("Con ese Pokémon en la puerta me siento seguro.", "With that Pokémon at the door I feel safe."),
                     ("Ningún ladrón se atreverá hoy.", "No thief will dare today."),
                     ("Qué Pokémon tan fuerte vigila la tienda.", "What a strong Pokémon guarding the shop."),
                     ("Así da gusto comprar tranquilo.", "This is how you shop in peace."),
                     ("Me ha mirado fijamente al entrar.", "It stared right at me when I came in."),
                     ("¿Lo habéis entrenado vosotros?", "Did you train it yourselves?")],
}
for cat, lines in L.items():
    assert len(lines) == 6, cat
    for i, (es, en) in enumerate(lines):
        k("tcgshop.evline.%s.%d" % (cat, i), es, en)

# ================================================================ memory reviews (tcgshop.review.ev_pkmn_*)
# (ES label, EN label, 4 lines for 4-5 stars, 4 lines for 1-2 stars). 3 stars mixes both.
R = {
    "pkmn_snorlax": ("Snorlax en la puerta", "Snorlax at the door",
                     [("Había un Snorlax roncando en la entrada, pero mereció la pena rodearlo", "There was a Snorlax snoring at the entrance, but it was worth going around it"),
                      ("Un Snorlax dormía junto a la puerta y aun así compré muy a gusto", "A Snorlax was sleeping by the door and I still had a great time shopping"),
                      ("Entré esquivando a un Snorlax gigante, ¡menuda anécdota!", "I came in dodging a giant Snorlax, what a story!"),
                      ("Con Snorlax en la puerta o sin él, esta tienda es genial", "Snorlax at the door or not, this shop is great")],
                     [("Un Snorlax tapaba la entrada y casi me doy la vuelta", "A Snorlax was blocking the entrance and I almost turned back"),
                      ("No pude entrar por la puerta principal por culpa de un Snorlax", "I couldn't use the main door because of a Snorlax"),
                      ("Esperé fuera un buen rato con un Snorlax roncando al lado", "I waited outside for ages with a Snorlax snoring next to me"),
                      ("¿Nadie piensa despertar al Snorlax de la entrada?", "Is nobody going to wake up the Snorlax at the entrance?")]),
    "pkmn_door": ("Pokémon en la entrada", "Pokémon at the entrance",
                  [("Había un Pokémon plantado en la puerta, pero dentro todo genial", "There was a Pokémon parked at the door, but inside everything was great"),
                   ("Tuve que rodear a un Pokémon para entrar y valió la pena", "I had to go around a Pokémon to get in and it was worth it"),
                   ("Un Pokémon cabezota en la entrada, una tienda estupenda dentro", "A stubborn Pokémon at the entrance, a wonderful shop inside"),
                   ("Me hizo gracia el Pokémon de la puerta, y compré muy bien", "The Pokémon at the door made me laugh, and I got great stuff")],
                  [("Un Pokémon no me dejaba entrar por la puerta", "A Pokémon wouldn't let me in through the door"),
                   ("La entrada estaba bloqueada por unos Pokémon salvajes", "The entrance was blocked by some wild Pokémon"),
                   ("Esperé fuera mucho rato por culpa de un Pokémon en la puerta", "I waited outside for ages because of a Pokémon at the door"),
                   ("Nadie se encargaba del Pokémon que tapaba la entrada", "Nobody was dealing with the Pokémon blocking the entrance")]),
    "pkmn_charmander": ("Charmander suelto", "Loose Charmander",
                        [("Un Charmander chamuscó un expositor, pero el resto estaba perfecto", "A Charmander scorched a display, but the rest was perfect"),
                         ("Había un Charmander nervioso, aun así compré muy a gusto", "There was a nervous Charmander, but I still enjoyed shopping"),
                         ("Menudo susto con el Charmander, pero la tienda es de diez", "What a scare with the Charmander, but the shop is top notch"),
                         ("Un Charmander por la tienda le dio emoción a la visita", "A Charmander around the shop made the visit exciting")],
                        [("Un Charmander había dejado los expositores chamuscados", "A Charmander had left the displays scorched"),
                         ("Olía a quemado por culpa de un Charmander suelto", "It smelled burnt because of a loose Charmander"),
                         ("No pude mirar la mitad de los expositores, estaban quemados", "I couldn't look at half the displays, they were scorched"),
                         ("Un Charmander campando a sus anchas entre cartas, qué peligro", "A Charmander running free among cards, how dangerous")]),
    "pkmn_geodude": ("Geodude revoltoso", "Rowdy Geodude",
                     [("Había piedras por un Geodude, pero lo demás estaba genial", "There were rocks because of a Geodude, but everything else was great"),
                      ("Un Geodude rodaba por la tienda y me hizo mucha gracia", "A Geodude was rolling around the shop and it made me laugh"),
                      ("Pese al Geodude y sus escombros, encontré lo que buscaba", "Despite the Geodude and its debris, I found what I was looking for"),
                      ("Un poco de desorden por un Geodude, nada grave", "A bit of mess from a Geodude, nothing serious")],
                     [("La tienda estaba llena de escombros por un Geodude", "The shop was full of debris because of a Geodude"),
                      ("Casi me tropiezo con las piedras que dejó un Geodude", "I nearly tripped over the rocks a Geodude left"),
                      ("La decoración estaba destrozada por un Geodude", "The decoration had been wrecked by a Geodude"),
                      ("Un Geodude rodando entre los clientes, un caos", "A Geodude rolling between customers, total chaos")]),
    "pkmn_psyduck": ("Psyduck confundido", "Confused Psyduck",
                     [("Un Psyduck despistado tiró algunos productos, pero me encantó", "A clueless Psyduck knocked some products over, but I loved it"),
                      ("El Psyduck con dolor de cabeza me dio mucha ternura", "The Psyduck with a headache was so endearing"),
                      ("Algún producto por el suelo por culpa de un Psyduck, nada grave", "A few products on the floor because of a Psyduck, nothing serious"),
                      ("Pese al Psyduck chocándose con todo, compré muy bien", "Despite the Psyduck bumping into everything, I got great stuff")],
                     [("Un Psyduck había tirado los productos que yo quería", "A Psyduck had knocked over the products I wanted"),
                      ("El expositor estaba patas arriba por un Psyduck", "The display was upside down because of a Psyduck"),
                      ("No pude comprar lo que buscaba: un Psyduck lo había tirado todo", "I couldn't buy what I wanted: a Psyduck had knocked it all over"),
                      ("Un Psyduck confundido chocándose con todo y nadie hacía nada", "A confused Psyduck crashing into everything and nobody did anything")]),
    "pkmn_song": ("Concierto de Jigglypuff", "Jigglypuff concert",
                  [("Un Jigglypuff cantaba y casi me duermo, ¡pero qué tienda!", "A Jigglypuff was singing and I nearly fell asleep, but what a shop!"),
                   ("Compré escuchando la canción de un Jigglypuff, muy original", "I shopped while listening to a Jigglypuff's song, very original"),
                   ("Pese al Jigglypuff cantando, encontré lo que buscaba", "Despite the singing Jigglypuff, I found what I was looking for"),
                   ("La canción del Jigglypuff me relajó bastante, la verdad", "The Jigglypuff's song was quite relaxing, honestly")],
                  [("Un Jigglypuff me dejó dormido en mitad de la tienda", "A Jigglypuff put me to sleep in the middle of the shop"),
                   ("No podía concentrarme con un Jigglypuff cantando sin parar", "I couldn't concentrate with a Jigglypuff singing non-stop"),
                   ("Me fui antes de comprar por culpa de la canción de un Jigglypuff", "I left before buying anything because of a Jigglypuff's song"),
                   ("Un Jigglypuff dando un concierto y nadie hacía nada", "A Jigglypuff giving a concert and nobody did anything")]),
    "pkmn_thief": ("Murkrow ladrón", "Thieving Murkrow",
                   [("Un Murkrow intentó llevarse algo, pero la tienda es estupenda", "A Murkrow tried to steal something, but the shop is wonderful"),
                    ("Vi a un Murkrow robando, ¡qué película!", "I saw a Murkrow stealing, what a show!"),
                    ("Pese al Murkrow ladrón, compré tranquilo", "Despite the thieving Murkrow, I shopped calmly"),
                    ("Un Murkrow rondaba los expositores, pero todo bien", "A Murkrow was hanging around the displays, but all good")],
                   [("Un Murkrow se llevó lo que yo quería comprar", "A Murkrow took what I wanted to buy"),
                    ("Había un Murkrow robando de los expositores y nadie lo paraba", "A Murkrow was stealing from the displays and nobody stopped it"),
                    ("Con un Murkrow ladrón suelto no se compra tranquilo", "You can't shop in peace with a thieving Murkrow on the loose"),
                    ("Esta tienda necesita un guardia: hasta los Murkrow roban", "This shop needs a guard: even Murkrow steal here")]),
    "pkmn_diglett": ("Agujeros de Diglett", "Diglett holes",
                     [("Un Diglett había hecho agujeros, pero la tienda es genial", "A Diglett had dug holes, but the shop is great"),
                      ("Vi salir a un Diglett del suelo, ¡me encantó!", "I saw a Diglett pop out of the floor, I loved it!"),
                      ("Algo de tierra por un Diglett, nada que me quitara las ganas", "Some dirt from a Diglett, nothing that put me off"),
                      ("Pese a los agujeros del Diglett, compré muy a gusto", "Despite the Diglett holes, I had a great time shopping")],
                     [("El suelo estaba lleno de tierra por culpa de un Diglett", "The floor was full of dirt because of a Diglett"),
                      ("Me manché entero con la tierra que dejó un Diglett", "I got covered in the dirt a Diglett left"),
                      ("Agujeros por todas partes: un Diglett campaba a sus anchas", "Holes everywhere: a Diglett was running wild"),
                      ("Una tienda de cartas no debería parecer una madriguera de Diglett", "A card shop shouldn't look like a Diglett burrow")]),
    "pkmn_pidgey": ("Invasión de Pidgey", "Pidgey invasion",
                    [("Una bandada de Pidgey se coló, pero dentro todo estupendo", "A flock of Pidgey got in, but everything inside was great"),
                     ("Ver a los Pidgey revolotear por la tienda fue divertido", "Watching the Pidgey flutter around the shop was fun"),
                     ("Pese a los Pidgey de la entrada, compré muy bien", "Despite the Pidgey at the entrance, I got great stuff"),
                     ("Un poco de lío con unos Pidgey, nada grave", "A bit of trouble with some Pidgey, nothing serious")],
                    [("La entrada estaba llena de excrementos de Pidgey", "The entrance was covered in Pidgey droppings"),
                     ("Había Pidgey por todas partes y nadie limpiaba", "There were Pidgey everywhere and nobody was cleaning"),
                     ("Un Pidgey casi me deja un regalito en la cabeza", "A Pidgey almost left a present on my head"),
                     ("Qué asco de entrada, toda llena de lo que dejaron los Pidgey", "What a gross entrance, full of what the Pidgey left")]),
    "pkmn_squirtle": ("Squirtle travieso", "Mischievous Squirtle",
                      [("Un Squirtle lo mojó todo, pero me lo pasé en grande", "A Squirtle got everything wet, but I had a blast"),
                       ("Había charcos por un Squirtle travieso, ¡qué gracia!", "There were puddles from a mischievous Squirtle, so funny!"),
                       ("Pese al Squirtle y sus chorros de agua, compré muy a gusto", "Despite the Squirtle and its water jets, I enjoyed shopping"),
                       ("Un Squirtle salpicando por la tienda, original cuanto menos", "A Squirtle splashing around the shop, original to say the least")],
                      [("Un Squirtle tenía la tienda inundada y no pude comprar tranquilo", "A Squirtle had flooded the shop and I couldn't shop in peace"),
                       ("Me empapó un Squirtle nada más entrar", "A Squirtle soaked me as soon as I walked in"),
                       ("Charcos por todas partes por culpa de un Squirtle", "Puddles everywhere because of a Squirtle"),
                       ("Casi me resbalo con el agua que dejó un Squirtle", "I almost slipped on the water a Squirtle left")]),
    "pkmn_pichu": ("Pichu travieso", "Mischievous Pichu",
                   [("Un Pichu dio un calambrazo a la caja, pero la tienda es genial", "A Pichu shocked the register, but the shop is great"),
                    ("Se fue la luz por un Pichu, ¡pero qué mono era!", "The lights went out because of a Pichu, but it was so cute!"),
                    ("Pese al Pichu y sus chispas, compré muy a gusto", "Despite the Pichu and its sparks, I enjoyed shopping"),
                    ("Un Pichu haciendo de las suyas, nada grave", "A Pichu up to its tricks, nothing serious")],
                   [("Un Pichu dejó la tienda a oscuras", "A Pichu left the shop in the dark"),
                    ("No me pudieron cobrar porque un Pichu había fundido la caja", "They couldn't ring me up because a Pichu had fried the register"),
                    ("Chispas por todas partes por culpa de un Pichu", "Sparks everywhere because of a Pichu"),
                    ("Un Pichu suelto y la luz parpadeando, un desastre", "A loose Pichu and flickering lights, a disaster")]),
    "pkmn_zubat": ("Zubat en el almacén", "Zubat in the stockroom",
                   [("Había un Zubat revoloteando, pero me atendieron genial", "There was a Zubat flapping around, but the service was great"),
                    ("Un Zubat se coló en la tienda, menuda aventura", "A Zubat got into the shop, what an adventure"),
                    ("Pese al Zubat y el desorden, encontré lo que buscaba", "Despite the Zubat and the mess, I found what I was looking for"),
                    ("Un poco de lío por un Zubat, nada que importe", "A bit of a mess from a Zubat, nothing that matters")],
                   [("Un Zubat me pasó rozando la cabeza, qué susto", "A Zubat brushed past my head, what a fright"),
                    ("Todo desordenado y con un Zubat volando por ahí", "Everything messy and a Zubat flying around"),
                    ("Tardaron muchísimo en cobrarme, había un Zubat en el almacén", "It took ages to ring me up, there was a Zubat in the stockroom"),
                    ("Una tienda con Zubat dentro no da confianza", "A shop with Zubat inside doesn't inspire confidence")]),
    # own positive ones
    "pkmn_meowth": ("Meowth afortunado", "Lucky Meowth",
                    [("¡Había un Meowth con una moneda brillante! Da buena suerte", "There was a Meowth with a shiny coin! It brings good luck"),
                     ("Acaricié al Meowth de la tienda y me dio suerte", "I petted the shop's Meowth and it brought me luck"),
                     ("Un Meowth precioso paseaba entre los expositores", "A gorgeous Meowth was walking between the displays"),
                     ("Con ese Meowth por aquí la visita fue redonda", "With that Meowth around the visit was perfect")],
                    [("Un Meowth muy mono, pero la tienda deja que desear", "A very cute Meowth, but the shop leaves a lot to be desired"),
                     ("Me quedé con el Meowth y poco más", "I'll remember the Meowth and not much else"),
                     ("Ni el Meowth de la suerte salva esta tienda", "Not even the lucky Meowth saves this shop"),
                     ("Lo mejor fue el Meowth, lo peor todo lo demás", "The best part was the Meowth, the worst was everything else")]),
    "pkmn_guard": ("Pokémon protector", "Guardian Pokémon",
                   [("Un Pokémon fuerte vigilaba la puerta: se compra con total tranquilidad", "A strong Pokémon was guarding the door: you can shop in total peace"),
                    ("Con ese Pokémon en la entrada ningún ladrón se acerca", "With that Pokémon at the entrance no thief comes near"),
                    ("Me encantó el Pokémon guardián de la entrada", "I loved the guardian Pokémon at the entrance"),
                    ("Segura, bonita y con un Pokémon protector, ¡de diez!", "Safe, pretty and with a guardian Pokémon, top marks!")],
                   [("Muy seguro todo con el Pokémon de la puerta, pero poco más", "Very safe with the Pokémon at the door, but not much else"),
                    ("El Pokémon guardián imponía, la tienda no tanto", "The guardian Pokémon was impressive, the shop not so much"),
                    ("Un Pokémon protector no arregla unos precios así", "A guardian Pokémon doesn't fix prices like these"),
                    ("Lo único que me gustó fue el Pokémon de la entrada", "The only thing I liked was the Pokémon at the entrance")]),
    "pkmn_glow": ("Flaaffy luminoso", "Glowing Flaaffy",
                  [("Un Flaaffy iluminaba la tienda, ¡qué ambiente tan bonito!", "A Flaaffy was lighting up the shop, such a lovely atmosphere!"),
                   ("Las cartas brillaban con la luz de un Flaaffy", "The cards sparkled in a Flaaffy's light"),
                   ("Se había ido la luz y un Flaaffy lo arregló todo", "The power had gone out and a Flaaffy fixed everything"),
                   ("Me encantó el Flaaffy de la tienda, toda una lámpara", "I loved the shop's Flaaffy, a proper lamp")],
                  [("Mucha luz gracias a un Flaaffy, pero la tienda regular", "Plenty of light thanks to a Flaaffy, but the shop was so-so"),
                   ("El Flaaffy brillaba más que la atención", "The Flaaffy shone brighter than the service"),
                   ("Un Flaaffy precioso no salva esta tienda", "A beautiful Flaaffy doesn't save this shop"),
                   ("Lo mejor, el Flaaffy; lo demás, para olvidar", "The best part, the Flaaffy; the rest, forgettable")]),
}

# _fixed: a Pokémon was causing trouble, but it was dealt with before this customer arrived ({d} = detail)
FIX_GOOD = [("Me contaron que hubo jaleo {d}, pero cuando llegué ya estaba todo resuelto", "I heard there'd been a commotion {d}, but when I got there it was all sorted"),
            ("Al parecer hubo un problema {d}; lo arreglaron rapidísimo", "Apparently there was a problem {d}; they fixed it super fast"),
            ("Ni rastro del lío {d} del que hablaba la gente", "No sign of the trouble {d} people were talking about"),
            ("Se encargaron enseguida del asunto {d}, así da gusto", "They dealt with the business {d} right away, that's how it should be")]
FIX_BAD = [("Lo resolvieron, pero aún se notaba el lío {d}", "They sorted it, but you could still tell about the trouble {d}"),
           ("Llegué después del jaleo {d} y la tienda seguía revuelta", "I arrived after the commotion {d} and the shop was still a mess"),
           ("Me hablaron del problema {d} y no me dio buena espina", "They told me about the problem {d} and it didn't sit right with me"),
           ("Aunque ya no estaba el Pokémon, se notaba todo lo que había pasado", "Even though the Pokémon was gone, you could tell everything that had happened")]
FIX_DETAIL = {
    "pkmn_snorlax": ("con un Snorlax en la puerta", "with a Snorlax at the door"),
    "pkmn_door": ("con un Pokémon en la entrada", "with a Pokémon at the entrance"),
    "pkmn_charmander": ("con un Charmander", "with a Charmander"),
    "pkmn_geodude": ("con un Geodude", "with a Geodude"),
    "pkmn_psyduck": ("con un Psyduck", "with a Psyduck"),
    "pkmn_song": ("con un Jigglypuff cantando", "with a singing Jigglypuff"),
    "pkmn_thief": ("con un Murkrow ladrón", "with a thieving Murkrow"),
    "pkmn_diglett": ("con un Diglett", "with a Diglett"),
    "pkmn_pidgey": ("con unos Pidgey", "with some Pidgey"),
    "pkmn_squirtle": ("con un Squirtle", "with a Squirtle"),
    "pkmn_pichu": ("con un Pichu", "with a Pichu"),
    "pkmn_zubat": ("con un Zubat", "with a Zubat"),
}
for tag, (es_d, en_d) in FIX_DETAIL.items():
    good = [(a.replace("{d}", es_d), b.replace("{d}", en_d)) for a, b in FIX_GOOD]
    bad = [(a.replace("{d}", es_d), b.replace("{d}", en_d)) for a, b in FIX_BAD]
    R[tag + "_fixed"] = (R[tag][0] + " (resuelto)", R[tag][1] + " (sorted)", good, bad)


def load_reviews_module():
    spec = importlib.util.spec_from_file_location("gen_lang_reviews_actors", os.path.join(HERE, "gen_lang_reviews.py"))
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
    n1 = merge("es_es.json", ES)
    n2 = merge("en_us.json", EN)
    print("gen_lang_09_events_actors: %d keys in es_es.json, %d in en_us.json" % (n1, n2))
