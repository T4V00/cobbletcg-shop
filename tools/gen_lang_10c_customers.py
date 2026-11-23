"""1.0 beta 3 (customers area): texts of the 10 unique customers, thief tiers / robberies / heists and guard upgrades.

Keys:
  tcgshop.fixed.<id>.<cat>.<n>       unique customers' own lines (entity/FixedCustomers: COMMON counts + 3 per special)
  tcgshop.fixed.guard_spot.<n>       a guard recognizing Vinnie Vex at the door (%s = his name)
  tcgshop.robbery.* / tcgshop.heist.* armed thieves (entity/ThiefTiers, entity/CustomerEntity)
  tcgshop.thief.*                    new theft messages (skilled thieves slipping, extra loot)
  tcgshop.guard.* / tcgshop.guardup.* guards fighting and their upgrades (entity/EmployeeEntity, shop/GuardUpgrades)
  tcgshop.role.fixed / thief_*       role line above the head
  advancement.tcgshop.<id>.*         texts of tools/gen_adv_10c_customers.py

Usage: python tools/gen_lang_10c_customers.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

# ---------------------------------------------------------------------------------------------- unique customers
# id -> category -> [(es, en), ...]. Counts must match entity/FixedCustomers.COMMON (hello 4, back 3, buy 3, nobuy 3,
# talk 4, bye_happy 3, bye_sad 3) and SPECIAL_LINES = 3 for the special categories.
FIXED = {
    0: {  # Don Aurelio: the excellent customer
        "hello": [
            ("Buenas tardes. Don Aurelio, para servirle. Muéstreme lo mejor que tenga.", "Good afternoon. Don Aurelio, at your service. Show me the finest you have."),
            ("Qué local tan encantador. Hoy vengo con la cartera generosa.", "What a charming little shop. My wallet feels generous today."),
            ("No me enseñe lo barato, joven. Yo vengo a por joyas.", "Don't show me the cheap stuff, young one. I'm here for jewels."),
            ("Mi mayordomo me habló de esta tienda. Espero que esté a la altura.", "My butler told me about this shop. I trust it lives up to it."),
        ],
        "back": [
            ("¡%s, querido amigo! Vuelvo porque usted tiene buen gusto.", "%s, my dear friend! I'm back because you have exquisite taste."),
            ("Ah, %s. La última vez me fui feliz. Repitamos.", "Ah, %s. I left happy last time. Let's do it again."),
            ("%s, guárdeme siempre lo más caro. Ya sabe que pago bien.", "%s, always save the priciest piece for me. You know I pay well."),
        ],
        "buy": [
            ("Me lo llevo. Preguntar el precio es cosa de gente con prisa.", "I'll take it. Asking the price is for people in a hurry."),
            ("Exquisito. Directo a la vitrina de mi salón.", "Exquisite. Straight to the display case in my parlor."),
            ("Esta pieza me estaba esperando. Envuélvamela, por favor.", "This piece was waiting for me. Wrap it up, please."),
        ],
        "nobuy": [
            ("Mmm, no termina de convencerme. Seguiré mirando.", "Hmm, it doesn't quite win me over. I'll keep looking."),
            ("Bonita, pero ya tengo tres iguales.", "Lovely, but I already own three of those."),
            ("No es lo bastante especial para mi colección.", "Not special enough for my collection."),
        ],
        "talk": [
            ("¿Sabe? Empecé coleccionando con mi abuelo. Hoy tengo una sala entera.", "You know, I started collecting with my grandfather. Now I have an entire room."),
            ("El dinero va y viene, joven. Una buena carta se queda.", "Money comes and goes, young one. A good card stays."),
            ("Siempre dejo propina. Los buenos tenderos son una especie protegida.", "I always tip. Good shopkeepers are a protected species."),
            ("Si algún día pone a la venta una carta de verdad cara, avíseme primero.", "If you ever put a truly expensive card on sale, call me first."),
        ],
        "bye_happy": [
            ("Un placer, como siempre. Volveré cuando mi mayordomo se despiste.", "A pleasure, as always. I'll be back when my butler isn't looking."),
            ("Magnífica compra. Le recomendaré en el club de caballeros.", "Splendid purchase. I'll recommend you at the gentlemen's club."),
            ("Hasta pronto. Guárdeme lo mejor.", "Until next time. Keep the best for me."),
        ],
        "bye_sad": [
            ("Hoy no ha habido suerte. Espero más la próxima vez.", "No luck today. I expect more next time."),
            ("Una tienda de su categoría debería cuidar más los detalles.", "A shop of your standing should mind the details better."),
            ("Me voy con las manos vacías. Qué desperdicio de tarde.", "Leaving empty-handed. What a waste of an afternoon."),
        ],
        "tip": [
            ("Quédese el cambio... y %s más. Se lo ha ganado.", "Keep the change... and %s more. You've earned it."),
            ("Para usted, %s de propina. Un buen servicio se paga.", "For you, a %s tip. Good service deserves its reward."),
            ("Tome %s. Cómprese algo bonito, joven.", "Take %s. Buy yourself something nice, young one."),
        ],
    },
    1: {  # Vinnie Vex: the known thief
        "hello": [
            ("Tranqui, tranqui... solo vengo a mirar. Mirar es gratis, ¿no?", "Easy, easy... just looking. Looking's free, right?"),
            ("Bonito local. Muchas cosas... muy al alcance.", "Nice place. Lots of stuff... very within reach."),
            ("¿Cámaras? ¿Guardias? Pregunto por curiosidad, ¿eh?", "Cameras? Guards? Just asking out of curiosity."),
            ("Vinnie Vex, ciudadano ejemplar. Encantado.", "Vinnie Vex, model citizen. Charmed."),
        ],
        "back": [
            ("¡%s, colega! ¿Has cambiado las cerraduras? Por nada, ¿eh?", "%s, buddy! Changed the locks? No reason."),
            ("Hola otra vez, %s. Esta vez me porto bien. Palabra.", "Hi again, %s. I'll behave this time. Promise."),
            ("%s, se te ve más rico que la última vez. Me alegro un montón.", "%s, you look richer than last time. So happy for you."),
        ],
        "buy": [
            ("Vale, esta la pago. Que conste en acta.", "Fine, I'll pay for this one. Put it on record."),
            ("Mira, hoy me siento honrado. Raro, ¿eh?", "Look, I feel honest today. Weird, huh?"),
            ("Pago, pago... no me mires así.", "I'm paying, I'm paying... don't look at me like that."),
        ],
        "nobuy": [
            ("Mmm, demasiado vigilado. Digo, demasiado caro.", "Hmm, too well guarded. I mean, too expensive."),
            ("No, eso no me cabe en el bolsillo. En el presupuesto, digo.", "Nah, that won't fit in my pocket. My budget, I mean."),
            ("Paso. Bueno, de momento.", "Pass. Well, for now."),
        ],
        "talk": [
            ("¿Yo? Soy inocente hasta que me pillen.", "Me? Innocent until caught."),
            ("Mi abuela decía: lo que no está atornillado es un regalo.", "My grandma used to say: if it isn't bolted down, it's a gift."),
            ("Tengo las manos muy rápidas. Para barajar, ¿eh?", "I've got very fast hands. For shuffling, I mean."),
            ("¿Ese guardia es nuevo? Parece majo. Y lento.", "Is that guard new? Seems nice. And slow."),
        ],
        "bye_happy": [
            ("Me voy sin nada... esta vez.", "Leaving with nothing... this time."),
            ("Un placer. Ya nos veremos, seguro.", "A pleasure. We'll meet again, I'm sure."),
            ("Vale, vale, ya me voy. ¡Sin prisas!", "Okay, okay, I'm leaving. No rush!"),
        ],
        "bye_sad": [
            ("Qué ambiente tan tenso. Así no se puede trabajar.", "Such a tense vibe. Can't work like this."),
            ("Me largo. Aquí hay demasiados ojos.", "I'm out. Too many eyes in here."),
            ("Tienda aburrida. Ni una oportunidad.", "Boring shop. Not a single opportunity."),
        ],
        "steal": [
            ("¡Gracias por la donación! ¡Vinnie se va!", "Thanks for the donation! Vinnie out!"),
            ("¡Esto me lo quedo en depósito! ¡Para siempre!", "I'll hold onto this! Forever!"),
            ("¡Pies, para qué os quiero!", "Feet, don't fail me now!"),
        ],
        "caught": [
            ("¡Vale, vale! Era una broma. ¿Una broma cara?", "Okay, okay! It was a joke. An expensive joke?"),
            ("¿Otra vez? ¡Esto es acoso!", "Again? This is harassment!"),
            ("Tenéis suerte de que hoy me dolía la rodilla.", "You're lucky my knee hurt today."),
        ],
        "escape": [
            ("¡Adiós, pringados! ¡Vinnie nunca pierde!", "Bye, suckers! Vinnie never loses!"),
            ("¡Ya lo venderé por ahí! ¡Gracias por todo!", "I'll flip it somewhere! Thanks for everything!"),
            ("¡Ni me habéis olido! ¡Hasta la próxima!", "Didn't even smell me! Till next time!"),
        ],
        "spotted": [
            ("¿Yo? ¿Robar? Qué ofensa... Me voy, me voy.", "Me? Steal? How rude... I'm going, I'm going."),
            ("Uy, el de seguridad me conoce. Mejor otro día.", "Oops, security knows me. Another day, then."),
            ("¡Que es la primera vez que vengo, lo juro! ...Adiós.", "I swear it's my first time here! ...Bye."),
        ],
    },
    2: {  # Nana Rosa
        "hello": [
            ("¡Hola, tesoro! Busco algo para mis nietos.", "Hello, sweetie! I'm looking for something for my grandkids."),
            ("Ay, cuántos colorines. Mi nieto se volvería loco aquí.", "Oh, so many colors! My grandson would go wild in here."),
            ("Buenas, cielo. No me metas prisa, que voy despacito.", "Hello, dear. Don't rush me, I'm a slow walker."),
            ("Te he traído galletas. Para ti, por ser tan majo.", "I brought you cookies. For being so lovely."),
        ],
        "back": [
            ("¡%s, mi niño! ¿Estás comiendo bien?", "%s, my dear! Are you eating properly?"),
            ("Ay, %s, mis nietos aún hablan de lo que les llevé.", "Oh, %s, my grandkids still talk about what I brought them."),
            ("%s, cariño, hoy vengo a por otro regalito.", "%s, darling, I'm here for another little present."),
        ],
        "buy": [
            ("Este, este. Seguro que al pequeño le encanta.", "This one. The little one will love it."),
            ("Qué brillante. Me lo llevo, cielo.", "So shiny! I'll take it, dear."),
            ("Para mi nieta. Dice que es una «rara». Como ella.", "For my granddaughter. She says it's a “rare”. Just like her."),
        ],
        "nobuy": [
            ("Ay, no sé cuáles tiene ya mi nieto.", "Oh dear, I don't know which ones my grandson already has."),
            ("Un poco caro para mi pensión, tesoro.", "A bit pricey for my pension, sweetie."),
            ("Lo pensaré mientras doy una vueltecita.", "I'll think it over on a little stroll."),
        ],
        "talk": [
            ("En mis tiempos coleccionábamos chapas. ¡Y éramos felices!", "Back in my day we collected bottle caps. And we were happy!"),
            ("Abrígate, que refresca.", "Wear a jacket, it's getting chilly."),
            ("Mi nieto dice que soy «la abuela más pro». No sé qué es, pero me gusta.", "My grandson says I'm “the most pro grandma”. No idea what it means, but I like it."),
            ("¿Tienes pareja? Tan majo y trabajando tanto...", "Are you seeing anyone? So lovely and working so hard..."),
        ],
        "bye_happy": [
            ("¡Gracias, tesoro! Que Dios te lo pague... y yo también.", "Thank you, sweetie! Bless you... and here's my business too."),
            ("Me voy contentísima. ¡Cuídate mucho!", "I'm leaving so happy. Take good care of yourself!"),
            ("Volveré con los nietos. ¡Prepárate!", "I'll come back with the grandkids. Brace yourself!"),
        ],
        "bye_sad": [
            ("Bueno, otro día será, cielo.", "Well, another day, dear."),
            ("Qué pena, no encontré nada para el niño.", "What a shame, nothing for the little one."),
            ("Ay, me he cansado. Me voy a casa.", "Oh, I'm worn out. Heading home."),
        ],
        "tip": [
            ("Toma, %s para ti. No se lo digas a nadie.", "Here, %s for you. Don't tell anyone."),
            ("Y %s para tu hucha, que trabajas mucho.", "And %s for your piggy bank, you work so hard."),
            ("Ten, %s. Para que te compres un bocadillo.", "Take %s. Buy yourself a sandwich."),
        ],
    },
    3: {  # Timmy Turbo
        "hello": [
            ("¡HALA! ¡Cuántas cartas! ¡¡Las quiero todas!!", "WHOA! So many cards! I want ALL of them!!"),
            ("¡Hola hola hola! ¿Tienes sobres? ¡Di que sí!", "Hi hi hi! Got packs? Say yes!"),
            ("¡Mi madre me ha dado la paga! ¡La paga entera!", "My mom gave me my allowance! The WHOLE allowance!"),
            ("¡Soy Timmy Turbo y hoy voy a sacar una brillante!", "I'm Timmy Turbo and I'm pulling a shiny today!"),
        ],
        "back": [
            ("¡¡%s!! ¡He vuelto! ¡Mi madre dice que vengo demasiado!", "%s!! I'm back! Mom says I come here too much!"),
            ("¡%s, la última vez saqué una brillante! ¡Bueno, casi!", "%s, last time I pulled a shiny! Well, almost!"),
            ("¡Eh, %s! ¿Me has guardado algo guay?", "Hey %s! Did you save me something cool?"),
        ],
        "buy": [
            ("¡¡SÍ!! ¡Este! ¡Este es el bueno!", "YES!! This one! This is THE one!"),
            ("¡Mis amigos van a flipar en el recreo!", "My friends are gonna freak out at recess!"),
            ("¡Me lo llevo! ¡Compra turbo!", "I'm getting it! Turbo purchase!"),
        ],
        "nobuy": [
            ("Jo... no me llega la paga.", "Aww... my allowance isn't enough."),
            ("Ese ya lo tengo. ¡Tres veces!", "Already got that one. Three times!"),
            ("Bah, ese no brilla.", "Meh, that one doesn't sparkle."),
        ],
        "talk": [
            ("¿Sabías que si soplas el sobre sale mejor carta? ¡Es ciencia!", "Did you know blowing on a pack gives better cards? It's science!"),
            ("¡De mayor quiero una tienda como esta! ¡O ser astronauta!", "When I grow up I want a shop like this! Or be an astronaut!"),
            ("¿Me dejas tocar la caja registradora? ¿Porfa?", "Can I touch the cash register? Pleeease?"),
            ("¡Mi primo dice que tiene una carta de un millón! Miente.", "My cousin says he has a million-dollar card! He's lying."),
        ],
        "bye_happy": [
            ("¡Adióóós! ¡Vuelvo mañana! ¡Y pasado!", "Byeeee! Back tomorrow! And the day after!"),
            ("¡La mejor tienda del universo!", "Best shop in the universe!"),
            ("¡Turbo se va! ¡Fiuuuum!", "Turbo out! Zoooom!"),
        ],
        "bye_sad": [
            ("Jo... me voy a casa a llorar un poquito.", "Aww... going home to cry a little."),
            ("Qué aburrido. ¡Ni una brillante!", "So boring. Not a single shiny!"),
            ("Me llama mi madre. Bueno, adiós.", "Mom's calling. Okay, bye."),
        ],
    },
    4: {  # Dr. Margarita
        "hello": [
            ("Doctora Margarita, tasadora certificada. Veamos este... establecimiento.", "Dr. Margarita, certified appraiser. Let's see this... establishment."),
            ("Espero que sepa distinguir una edición limitada de una ilimitada.", "I do hope you can tell a limited print from an unlimited one."),
            ("Este mes he leído tres libros sobre cartas. ¿Y usted?", "I've read three books on cards this month. And you?"),
            ("No toque nada mientras miro. Gracias.", "Don't touch anything while I look. Thank you."),
        ],
        "back": [
            ("%s. Vuelvo a comprobar si ha aprendido algo de precios.", "%s. Back to check whether you've learned anything about pricing."),
            ("Ah, %s. Su tienda ha mejorado. Ligeramente.", "Ah, %s. Your shop has improved. Slightly."),
            ("%s, he traído mi lupa. Por si acaso.", "%s, I brought my magnifier. Just in case."),
        ],
        "buy": [
            ("Precio correcto. Por fin alguien que consulta el mercado.", "Correct price. Finally someone who checks the market."),
            ("Aceptable. Me la llevo para mi archivo.", "Acceptable. I'll take it for my archive."),
            ("Una compra racional. Tómeselo como una lección.", "A rational purchase. Take it as a lesson."),
        ],
        "nobuy": [
            ("No. Esquinas gastadas, sin duda.", "No. Worn corners, clearly."),
            ("Hoy no. Mi análisis no lo justifica.", "Not today. My analysis doesn't justify it."),
            ("Interesante. Incorrecto, pero interesante.", "Interesting. Wrong, but interesting."),
        ],
        "talk": [
            ("¿Sabe la diferencia entre valor y precio? Evidentemente, no.", "Do you know the difference between value and price? Evidently not."),
            ("Le doy un consejo gratis: los consejos gratis valen lo que cuestan.", "Here's free advice: free advice is worth what it costs."),
            ("En mi tesis demostré que el 87 %% de las tiendas cobran de más.", "My thesis proved 87%% of shops overcharge."),  # %% = literal % in Text.translatable
            ("No me llame «señora». Doctora, por favor.", "Don't call me “ma'am”. Doctor, please."),
        ],
        "bye_happy": [
            ("Correcto. Le pondré un notable.", "Adequate. I'll grade you a B-plus."),
            ("Ha superado mi inspección. Por los pelos.", "You passed my inspection. Barely."),
            ("Volveré a auditar sus precios.", "I'll be back to audit your prices."),
        ],
        "bye_sad": [
            ("Suspenso. Estudie el mercado.", "Fail. Study the market."),
            ("Una decepción académica.", "An academic disappointment."),
            ("Escribiré un artículo sobre esto. No le va a gustar.", "I'll write a paper about this. You won't like it."),
        ],
        "complain": [
            ("¡Este precio es un insulto a la estadística!", "This price is an insult to statistics!"),
            ("¿Ha consultado el mercado alguna vez en su vida?", "Have you ever checked the market in your life?"),
            ("A este precio, la carta debería venir firmada por su ilustrador.", "At this price, the card should come signed by its artist."),
        ],
    },
    5: {  # Gus Grumble
        "hello": [
            ("Bah. Otra tienda de cartitas.", "Bah. Another little card shop."),
            ("¿Qué miras? Vengo a quejarme. Digo, a comprar.", "What are you looking at? I'm here to complain. I mean, to shop."),
            ("Qué calor hace aquí dentro... ¿o soy yo?", "Hot in here... or is it me?"),
            ("Gus Grumble. No me des la mano, que no me gusta.", "Gus Grumble. Don't shake my hand, I don't like it."),
        ],
        "back": [
            ("Otra vez tú, %s. Qué remedio.", "You again, %s. Can't be helped."),
            ("%s. No he vuelto porque me guste. Es que me pilla cerca.", "%s. I'm not back because I like it. It's just nearby."),
            ("Hola, %s. Sigues cobrando de más, supongo.", "Hi, %s. Still overcharging, I suppose."),
        ],
        "buy": [
            ("Me lo llevo. Y no sonrías, que no es para tanto.", "I'll take it. And don't smile, it's no big deal."),
            ("Toma. Y no me des las gracias.", "Here. And don't thank me."),
            ("Bueno. Peor es nada.", "Fine. Better than nothing."),
        ],
        "nobuy": [
            ("Ni regalado.", "Wouldn't take it for free."),
            ("Basura. Basura cara.", "Junk. Expensive junk."),
            ("En mis tiempos las cartas eran de cartón de verdad.", "In my day cards were made of real cardboard."),
        ],
        "talk": [
            ("¿Ducharme? ¿Para qué? Mañana me ensucio otra vez.", "Shower? Why? I'll just get dirty again tomorrow."),
            ("Todo está peor que antes. Todo.", "Everything's worse than it used to be. Everything."),
            ("No me hables, que me cansas.", "Don't talk to me, it tires me."),
            ("Tu música es mala. Tu luz es mala. Tú estás bien. Más o menos.", "Your music's bad. Your lights are bad. You're fine. Sort of."),
        ],
        "bye_happy": [
            ("No ha estado... tan mal. No se lo digas a nadie.", "It wasn't... that bad. Don't tell anyone."),
            ("Bah. Puede que vuelva. Puede.", "Bah. Maybe I'll come back. Maybe."),
            ("Gruñido de aprobación. Es todo lo que vas a sacar.", "Grunt of approval. That's all you're getting."),
        ],
        "bye_sad": [
            ("Pésimo. Como todo.", "Awful. Like everything."),
            ("Una estrella. Y porque no se puede poner cero.", "One star. Only because I can't give zero."),
            ("Me voy. Aquí huele raro. ...¿Qué? No soy yo.", "I'm leaving. It smells weird in here. ...What? It's not me."),
        ],
        "complain": [
            ("¡¿Esto cuesta ESO?! ¡Ladrones!", "THIS costs THAT?! Thieves!"),
            ("¡Con lo que pides me compro una casa!", "For what you're asking I could buy a house!"),
            ("¡Voy a escribir al periódico! Si es que todavía existe.", "I'm writing to the newspaper! If it still exists."),
        ],
    },
    6: {  # Chad Flipson
        "hello": [
            ("Bro. Chad Flipson. Compro barato, vendo caro. Respeta el grind.", "Bro. Chad Flipson. Buy low, sell high. Respect the grind."),
            ("¿Tienes algo infravalorado? Pregunto por mis seguidores.", "Got anything undervalued? Asking for my followers."),
            ("Vengo a hacerte un favor: quitarte stock de encima.", "I'm here to do you a favor: take stock off your hands."),
            ("Las tiendas físicas están muertas, bro. Pero bueno, aquí estoy.", "Physical shops are dead, bro. Anyway, here I am."),
        ],
        "back": [
            ("¡%s, mi socio! ¿Has bajado precios? Dime que sí.", "%s, my partner! Lowered prices yet? Tell me yes."),
            ("%s, la última que te compré la revendí al triple. Gracias, bro.", "%s, the last one I bought from you I flipped for triple. Thanks, bro."),
            ("Bro, %s, hoy vengo con ofertas que no podrás rechazar.", "Bro, %s, today I've got offers you can't refuse."),
        ],
        "buy": [
            ("Comprado. En una semana vale el doble, apúntalo.", "Copped. Worth double in a week, mark my words."),
            ("Inversión. No coleccionismo, bro. Inversión.", "Investment. Not collecting, bro. Investment."),
            ("Esta va directa a la reventa.", "This one's going straight to resale."),
        ],
        "nobuy": [
            ("Sin margen, bro. Sin margen.", "No margin, bro. No margin."),
            ("Eso no hay quien lo revenda.", "Nobody's flipping that."),
            ("Paso. Mala rentabilidad.", "Pass. Bad ROI."),
        ],
        "talk": [
            ("Mi lema: si no está enfundada, no está en mi corazón.", "My motto: if it's not sleeved, it's not in my heart."),
            ("Tengo un canal. Bueno, tengo cuarenta suscriptores.", "I've got a channel. Well, forty subscribers."),
            ("Lo que tú llamas «robo» yo lo llamo «negociación agresiva».", "What you call “robbery” I call “aggressive negotiation”."),
            ("¿Has pensado en vender la tienda? A mí, digo. Barata.", "Ever thought about selling your shop? To me. Cheap."),
        ],
        "bye_happy": [
            ("Negocio redondo. Para mí. Bueno, para los dos.", "Great deal. For me. Well, for both of us."),
            ("Bro, eres mi proveedor favorito.", "Bro, you're my favorite supplier."),
            ("Te mencionaré en mi próximo vídeo. Igual.", "I'll shout you out in my next video. Maybe."),
        ],
        "bye_sad": [
            ("Bro, así no se hacen negocios.", "Bro, that's not how business works."),
            ("Tus precios son de hace quince años. Y no en el buen sentido.", "Your prices are fifteen years old. And not in a good way."),
            ("Me voy a otra tienda con menos ego.", "I'm off to a shop with less ego."),
        ],
        "deal": [
            ("Te hago una oferta. Ya sé que es baja. Es estrategia, bro.", "Here's my offer. I know it's low. It's strategy, bro."),
            ("Bro, oferta relámpago: tómala o déjala. Tómala.", "Bro, flash offer: take it or leave it. Take it."),
            ("Vamos a hablar de números. De mis números.", "Let's talk numbers. My numbers."),
        ],
    },
    7: {  # Luna Star
        "hello": [
            ("¡Hola, chat! Estamos en una tienda nueva, ¡saludad!", "Hi chat! We're in a new shop, say hi!"),
            ("¡Qué estética tan bonita! El chat dice que cinco estrellas.", "Such a cute aesthetic! Chat says five stars."),
            ("¡Luna Star en directo! ¿Te importa salir en cámara?", "Luna Star, live! Mind being on camera?"),
            ("¡Chat, mirad cuántas cartas! Voy a abrir sobres aquí mismo.", "Chat, look at all these cards! I'm opening packs right here."),
        ],
        "back": [
            ("¡Chat, es %s! ¡Mi tendero favorito! Spam de corazones.", "Chat, it's %s! My favorite shopkeeper! Spam hearts."),
            ("¡%s! El chat pedía volver aquí, ¡y aquí estamos!", "%s! Chat begged to come back here, and here we are!"),
            ("Hola, %s. El último directo aquí fue un exitazo.", "Hi %s, last stream here was a total hit."),
        ],
        "buy": [
            ("¡Chat, nos lo llevamos! ¡Escribid «¡Sí!»!", "Chat, we're getting it! Type “Yes!”!"),
            ("Esto va directo al sorteo para mis seguidores.", "This goes straight to my follower giveaway."),
            ("¡Qué pasada! El chat ha votado y gana este.", "Amazing! Chat voted and this one won."),
        ],
        "nobuy": [
            ("El chat dice que no. ¡El chat manda!", "Chat says no. Chat is the boss!"),
            ("Mmm, eso no va mucho con mi estética.", "Hmm, not really my aesthetic."),
            ("Uy, se me corta la conexión. Seguimos mirando.", "Oops, my connection's dropping. Let's keep looking."),
        ],
        "talk": [
            ("¿Me sigues? Soy LunaStar, con tres estrellitas.", "Are you following me? It's LunaStar, with three little stars."),
            ("Mi récord en directo: cien sobres sin parpadear.", "My stream record: a hundred packs without blinking."),
            ("El chat pregunta si tienes pareja. Yo no he sido, ¿eh?", "Chat's asking if you're single. Wasn't me!"),
            ("¡Saluda a la cámara! ...¡Con más ganas!", "Wave at the camera! ...More energy!"),
        ],
        "bye_happy": [
            ("¡Adiós, chat! ¡Pasaos por esta tienda, en serio!", "Bye chat! Go visit this shop, seriously!"),
            ("¡Directo terminado! ¡Cinco estrellas de las de verdad!", "Stream's over! Real five stars!"),
            ("¡Gracias por todo! ¡Os quiero, chat!", "Thanks for everything! Love you, chat!"),
        ],
        "bye_sad": [
            ("Bueno, chat, no ha sido nuestro mejor directo.", "Well chat, not our best stream."),
            ("El chat se ha aburrido. Me piro.", "Chat got bored. I'm out."),
            ("Cortamos aquí. Este vídeo va a necesitar mucha edición.", "Cutting here. This one needs heavy editing."),
        ],
    },
    8: {  # Barnaby Brine
        "hello": [
            ("¡Ah del barco! Barnaby Brine, capitán retirado y coleccionista.", "Ahoy! Barnaby Brine, retired captain and collector."),
            ("Estas cartas las conseguí en los siete mares. Bueno, en cuatro.", "Got these cards across the seven seas. Well, four."),
            ("Huele a sobres nuevos. ¡Mejor que el salitre!", "Smells like fresh packs. Better than sea salt!"),
            ("Atraco en tu puerto. ¡Digo, atraco el barco! ¡Que amarro, que amarro!", "Docking at your harbor. Just docking! Not robbing!"),
        ],
        "back": [
            ("¡%s, viejo lobo de tienda! ¡Vuelvo a puerto!", "%s, you old shop-dog! Back in port!"),
            ("¡Ah, %s! Traigo cartas de un viaje muy lejano.", "Ah, %s! I bring cards from a far-off voyage."),
            ("%s, grumete, ¿listo para un buen trueque?", "%s, cabin kid, ready for a proper swap?"),
        ],
        "buy": [
            ("¡A la bodega con ella!", "Into the hold with it!"),
            ("¡Por los siete mares, menuda carta!", "By the seven seas, what a card!"),
            ("Me la llevo. Mi loro la va a adorar.", "I'll take it. My parrot will adore it."),
        ],
        "nobuy": [
            ("Esa no vale ni un doblón.", "That one's not worth a doubloon."),
            ("Demasiado cara para un marino jubilado.", "Too dear for a retired sailor."),
            ("Mmm, mar picada. Esperaré.", "Hmm, choppy waters. I'll wait."),
        ],
        "talk": [
            ("Una vez cambié una carta por un mapa del tesoro. Era falso. La carta también.", "Once I swapped a card for a treasure map. Fake. So was the card."),
            ("En alta mar no hay tiendas. Por eso vengo tanto.", "No shops out at sea. That's why I come so often."),
            ("Mi barco se llamaba «La Holográfica». La hundió una ola común.", "My ship was called “The Holo”. Sunk by a common wave."),
            ("¿Sabías que las cartas flotan? Lo comprobé. Sin querer.", "Did you know cards float? I checked. By accident."),
        ],
        "bye_happy": [
            ("¡Leven anclas! ¡Volveré con la marea!", "Anchors aweigh! I'll be back with the tide!"),
            ("¡Buen puerto, este! ¡Hasta la próxima!", "Fine harbor, this! See you next time!"),
            ("¡A toda vela! ¡Gracias, grumete!", "Full sail! Thanks, cabin kid!"),
        ],
        "bye_sad": [
            ("Mar en calma, bodega vacía. Qué se le va a hacer.", "Calm sea, empty hold. Oh well."),
            ("Hoy no sopla buen viento por aquí.", "No good wind blowing here today."),
            ("Me vuelvo al barco. Al menos allí me quiere el loro.", "Back to my ship. At least the parrot loves me."),
        ],
        "deal": [
            ("¡Trueque, grumete! Mi carta viajera por una de las tuyas.", "A swap, cabin kid! My well-traveled card for one of yours."),
            ("Esta la saqué de un cofre hundido. ¿Hacemos cambio?", "Pulled this from a sunken chest. Shall we trade?"),
            ("Un marino nunca regatea... intercambia. ¿Qué me dices?", "A sailor never haggles... he trades. What say you?"),
        ],
    },
    9: {  # Madame Noir
        "hello": [
            ("Bonsoir. No pregunte de dónde vienen mis cartas.", "Bonsoir. Don't ask where my cards come from."),
            ("Madame Noir. Tengo algo que usted desea. Lo sé.", "Madame Noir. I have something you want. I know it."),
            ("Su tienda es... pintoresca. Lo diré con amabilidad.", "Your shop is... quaint. I'll put it kindly."),
            ("Las cartas raras me encuentran a mí. No al revés.", "Rare cards find me. Not the other way round."),
        ],
        "back": [
            ("%s. Volvemos a vernos. Como estaba escrito.", "%s. We meet again. As it was written."),
            ("Ah, %s. Hoy traigo algo aún más... especial.", "Ah, %s. Today I bring something even more... special."),
            ("%s, no me haga esperar. No me gusta esperar.", "%s, don't keep me waiting. I dislike waiting."),
        ],
        "buy": [
            ("Me lo quedo. No lo comente con nadie.", "I'll keep this. Don't mention it to anyone."),
            ("Adecuado. Para mi colección privada.", "Adequate. For my private collection."),
            ("Tiene un aura interesante. Lo compro.", "It has an interesting aura. I'll buy it."),
        ],
        "nobuy": [
            ("Vulgar.", "Vulgar."),
            ("No. Carece de misterio.", "No. It lacks mystery."),
            ("He visto mejores en sueños.", "I've seen better in dreams."),
        ],
        "talk": [
            ("Algunas cartas traen suerte. Otras... la cobran.", "Some cards bring luck. Others... collect it."),
            ("No sonrío. Es una decisión estética.", "I don't smile. It's an aesthetic choice."),
            ("Mi gato negro tasa las cartas mejor que cualquier experto.", "My black cat appraises cards better than any expert."),
            ("Usted no me ha visto aquí. ¿Entendido?", "You haven't seen me here. Understood?"),
        ],
        "bye_happy": [
            ("Un trato digno. Quizá vuelva cuando la luna lo permita.", "A worthy deal. Perhaps I'll return when the moon allows."),
            ("Au revoir. Ha estado a la altura.", "Au revoir. You were up to it."),
            ("Recordaré su tienda. Eso es un honor.", "I'll remember your shop. That is an honor."),
        ],
        "bye_sad": [
            ("Qué pérdida de tiempo. Del mío.", "What a waste of time. Mine."),
            ("Adieu. No volverá a verme. Probablemente.", "Adieu. You won't see me again. Probably."),
            ("Su tienda carece de alma.", "Your shop lacks a soul."),
        ],
        "deal": [
            ("Una pieza única. Su precio también lo es.", "A one-of-a-kind piece. So is its price."),
            ("Esta carta tiene historia. Y la historia se paga.", "This card has history. And history isn't cheap."),
            ("Le ofrezco algo raro. No regatee con el destino.", "I offer you something rare. Don't haggle with fate."),
        ],
    },
}

COMMON_COUNTS = {"hello": 4, "back": 3, "buy": 3, "nobuy": 3, "talk": 4, "bye_happy": 3, "bye_sad": 3}
SPECIAL_COUNT = 3

# numbered line groups: prefix -> [(es, en), ...]
GROUPS = {
    "tcgshop.fixed.guard_spot": [
        ("Alto ahí, %s. Te conozco. Media vuelta.", "Hold it, %s. I know you. Turn around."),
        ("%s... tu foto está en mi taquilla. Fuera.", "%s... your photo is on my locker. Out."),
        ("Ni se te ocurra, %s. Te estoy vigilando.", "Don't even think about it, %s. I'm watching you."),
        ("Buenas, %s. Las manos donde pueda verlas. Mejor aún, en la calle.", "Hello, %s. Hands where I can see them. Better yet, outside."),
    ],
    "tcgshop.robbery.shout": [
        ("¡Esto es un atraco! ¡Que nadie se mueva y nadie saldrá herido!", "This is a robbery! Nobody moves, nobody gets hurt!"),
        ("¡Las manos donde pueda verlas! ¡Las cartas, a la bolsa!", "Hands where I can see them! Cards in the bag!"),
        ("¡Quietos todos! ¡Hoy cierro yo la caja!", "Freeze! I'm closing out the till today!"),
        ("¡Nada de héroes! ¡Solo vengo a por lo caro!", "No heroes! I'm only here for the pricey stuff!"),
    ],
    "tcgshop.heist.shout": [
        ("¡GOLPE! ¡Equipo, vaciad TODAS las vitrinas!", "HEIST! Crew, empty EVERY display!"),
        ("¡Todo el mundo al suelo! ¡Esta tienda ahora es nuestra!", "Everybody down! This shop belongs to us now!"),
        ("¡Meses planeando esto! ¡Hasta la última carta!", "Months planning this! Every last card!"),
        ("¡Sonríe, tendero! ¡Sales en el golpe del siglo!", "Smile, shopkeeper! You're in the heist of the century!"),
    ],
    "tcgshop.robbery.threat": [
        ("¡Atrás! ¡No me obligues!", "Back off! Don't make me!"),
        ("¿Quieres pelea? ¡Pues toma pelea!", "Want a fight? You got one!"),
        ("¡Apártate o lo lamentarás!", "Step aside or you'll regret it!"),
        ("¡Ni un paso más!", "Not one more step!"),
    ],
    "tcgshop.robbery.kill": [
        ("¡Te dije que no te metieras!", "Told you to stay out of it!"),
        ("¡Uno menos! ¿Quién es el siguiente?", "One down! Who's next?"),
        ("¡Eso te pasa por hacerte el héroe!", "That's what you get for playing hero!"),
        ("¡A este equipo no lo para nadie!", "Nobody stops this crew!"),
    ],
    "tcgshop.robbery.escape": [
        ("¡Nos vemos en otra vida, tendero!", "See you in another life, shopkeeper!"),
        ("¡Botín asegurado! ¡Desaparecemos!", "Loot secured! We vanish!"),
        ("¡Ha sido un placer hacer negocios!", "Pleasure doing business!"),
        ("¡Ni rastro! ¡Como si nunca hubiera estado aquí!", "Not a trace! Like I was never here!"),
    ],
    "tcgshop.thief.slip": [
        ("¡Ja! ¡Más escurridizo que una anguila!", "Ha! Slipperier than an eel!"),
        ("¡Suelta, que me haces cosquillas!", "Let go, that tickles!"),
        ("¡Truco de magia! ¡Ahora no me ves!", "Magic trick! Now you don't see me!"),
        ("¡Demasiado lento, segurata!", "Too slow, rent-a-cop!"),
    ],
    "tcgshop.thief.slipped_guard": [
        ("¡Se me ha escurrido %s! ¡A por él!", "%s slipped out of my grip! After them!"),
        ("¡Maldición, %s es rápido!", "Blast, %s is quick!"),
        ("¡No te escaparás, %s!", "You won't get away, %s!"),
        ("¡%s, vuelve aquí!", "%s, get back here!"),
    ],
    "tcgshop.guard.fight": [
        ("¡Alto! ¡Seguridad!", "Stop! Security!"),
        ("¡Suelta el arma y el botín!", "Drop the weapon and the loot!"),
        ("¡En esta tienda no roba nadie!", "Nobody robs this shop!"),
        ("¡Por el jefe y por el stock!", "For the boss and for the stock!"),
    ],
    "tcgshop.guard.tail": [
        ("Te tengo fichado, %s. Te sigo de cerca.", "I've got you flagged, %s. Sticking close."),
        ("%s, qué casualidad, yo también voy hacia allí.", "%s, what a coincidence, I'm heading that way too."),
        ("Ese abrigo esconde algo, %s. Te vigilo.", "That coat hides something, %s. I'm watching."),
        ("Jefe, %s me da mala espina. Lo sigo.", "Boss, %s gives me a bad feeling. Tailing them."),
    ],
    "tcgshop.guardup.thanks.armor": [
        ("¡Esto pesa, pero me siento invencible!", "It's heavy, but I feel invincible!"),
        ("¡Gracias, jefe! Ahora sí parezco de seguridad.", "Thanks, boss! Now I look like real security."),
        ("Que vengan. Que vengan.", "Let them come. Let them come."),
    ],
    "tcgshop.guardup.thanks.weapon": [
        ("¡Uy, cuidado, que corta!", "Whoa, careful, it's sharp!"),
        ("Con esto no se atreve ni el ladrón más listo.", "With this, not even the slickest thief will try."),
        ("Prometo usarla solo con los malos.", "I promise to use it only on the bad guys."),
    ],
    "tcgshop.guardup.thanks.training": [
        ("¡Cien flexiones al día, jefe!", "A hundred push-ups a day, boss!"),
        ("Me siento más fuerte. Y más guapo.", "I feel stronger. And better-looking."),
        ("¡El gimnasio ha merecido la pena!", "The gym was worth it!"),
    ],
    "tcgshop.guardup.thanks.detection": [
        ("Ahora veo hasta las intenciones.", "Now I can see right through intentions."),
        ("Aquí no se me escapa nadie. Nadie.", "No one slips past me. No one."),
        ("Tengo la ficha de todos los sospechosos del barrio.", "I've got mugshots of every suspect in town."),
    ],
}

T = {
    # roles (line above the head)
    "tcgshop.role.fixed": ("Cliente único", "Unique customer"),
    "tcgshop.phone.guard_upgrades": ("Mejoras", "Upgrades"),
    "tcgshop.role.thief_pickpocket": ("Cliente sospechoso", "Suspicious Customer"),
    "tcgshop.role.thief_shoplifter": ("Cliente escurridizo", "Shifty customer"),
    "tcgshop.role.thief_pro": ("Encapuchado", "Hooded stranger"),
    "tcgshop.role.thief_robber": ("Atracador armado", "Armed robber"),
    "tcgshop.role.thief_heist": ("Jefe de la banda", "Crew boss"),
    "tcgshop.msg.sender.security": ("Seguridad", "Security"),

    # unique customers
    "tcgshop.fixed.arrived": ("✦ %s, un cliente único, entra en tu tienda por primera vez.", "✦ %s, a unique customer, walks into your shop for the first time."),
    "tcgshop.fixed.arrived_back": ("✦ %s vuelve a tu tienda (visita n.º %s).", "✦ %s is back at your shop (visit #%s)."),
    "tcgshop.fixed.thief_spotted": ("%s ha reconocido a %s en la puerta y lo ha echado.", "%s recognized %s at the door and sent them packing."),

    # thieves
    "tcgshop.thief.loot_more": ("%s y %s cosas más", "%s and %s more items"),
    "tcgshop.thief.unseen": ("¡Te han robado y %s no lo ha visto! Era un ladrón hábil: mejora la detección de tu guardia.",
                             "You were robbed and %s didn't notice! A skilled thief: upgrade your guard's detection."),

    # armed robbery (tier 4)
    "tcgshop.robbery.radio": ("%s por radio: «Jefe, acaba de entrar alguien armado. Lo tengo vigilado.»",
                              "%s on the radio: “Boss, someone armed just walked in. I've got eyes on them.”"),
    "tcgshop.robbery.strike_title": ("¡ATRACO!", "ROBBERY!"),
    "tcgshop.robbery.strike_sub": ("Un atracador armado se lleva %s artículos", "An armed robber is taking %s items"),
    "tcgshop.robbery.strike_chat": ("¡%s está atracando tu tienda a punta de espada! Se lleva %s artículos (%s). Derrótalo antes de que escape para recuperarlo todo.",
                                    "%s is robbing your shop at sword point! Taking %s items (%s). Defeat them before they escape to get it all back."),
    "tcgshop.robbery.owner_down": ("%s te ha dejado fuera de combate. ¡Tu stock sigue en sus manos!", "%s knocked you out. Your stock is still in their hands!"),
    "tcgshop.robbery.foiled_msg": ("Atraco frustrado: el atracador ha caído y recuperas %s artículos (%s).",
                                   "Robbery foiled: the robber is down and you get back %s items (%s)."),
    "tcgshop.robbery.lost_msg": ("El atracador escapó con %s artículos (%s). Mejora a tu guardia para la próxima.",
                                 "The robber got away with %s items (%s). Upgrade your guard for next time."),

    # heist (tier 5)
    "tcgshop.heist.tip_msg": ("Soplo anónimo: una banda planea dar un golpe en tu tienda HOY y llevarse todo lo que haya en las vitrinas. %s",
                              "Anonymous tip: a crew plans to hit your shop TODAY and take everything on your displays. %s"),
    "tcgshop.heist.tip_guard": ("Tu guardia %s estará allí. ¿Va bien equipado?", "Your guard %s will be there. Are they geared up?"),
    "tcgshop.heist.tip_noguard": ("No tienes guardia. Prepárate para pelear... o vacía las vitrinas.", "You have no guard. Get ready to fight... or empty your displays."),
    "tcgshop.heist.incoming_title": ("⚠ GOLPE INMINENTE ⚠", "⚠ HEIST INCOMING ⚠"),
    "tcgshop.heist.incoming_sub": ("Una banda va a por tu tienda", "A crew is coming for your shop"),
    "tcgshop.heist.broadcast": ("⚠ ¡GOLPE EN LA TIENDA DE %s! La banda vacía todas las vitrinas: %s artículos (%s).",
                                "⚠ HEIST AT %s'S SHOP! The crew is emptying every display: %s items (%s)."),
    "tcgshop.heist.strike_title": ("¡EL GOLPE!", "THE HEIST!"),
    "tcgshop.heist.strike_sub": ("Se llevan %s artículos: ¡detenlos!", "They're taking %s items: stop them!"),
    "tcgshop.heist.foiled_msg": ("¡Golpe frustrado! El jefe de la banda ha caído: recuperas %s artículos (%s).",
                                 "Heist foiled! The crew boss is down: you get back %s items (%s)."),
    "tcgshop.heist.lost_msg": ("La banda escapó con %s artículos (%s). Un golpe así deja huella.",
                               "The crew escaped with %s items (%s). A heist like that leaves a mark."),
    "tcgshop.heist.foiled_broadcast": ("★ ¡La tienda de %s ha frustrado un golpe! ★", "★ %s's shop foiled a heist! ★"),

    # guards
    "tcgshop.guard.fallen": ("Tu guardia %s ha caído ante %s. Contrata a otro desde el móvil: su equipo se queda en la tienda.",
                             "Your guard %s fell to %s. Hire another one from the phone: the gear stays at the shop."),
    "tcgshop.guard.fallen_msg": ("%s cayó defendiendo la tienda contra %s. Ahora no tienes guardia: contrata uno nuevo (las mejoras se conservan).",
                                 "%s fell defending the shop against %s. You have no guard now: hire a new one (upgrades are kept)."),

    # guard upgrades
    "tcgshop.guardup.no_guard": ("Primero contrata a un guardia en esta tienda.", "Hire a guard for this shop first."),
    "tcgshop.guardup.maxed": ("%s ya está al máximo.", "%s is already maxed out."),
    "tcgshop.guardup.bought": ("Mejora comprada: %s nivel %s/%s para %s (%s).", "Upgrade bought: %s level %s/%s for %s (%s)."),
    "tcgshop.guardup.stat.armor": ("Armadura", "Armor"),
    "tcgshop.guardup.stat.weapon": ("Arma", "Weapon"),
    "tcgshop.guardup.stat.training": ("Entrenamiento", "Training"),
    "tcgshop.guardup.stat.detection": ("Detección", "Detection"),
    "tcgshop.guardup.menu_title": ("Equipo de %s (guardia)", "%s's gear (guard)"),
    "tcgshop.guardup.effect.armor.0": ("Sin armadura", "No armor"),
    "tcgshop.guardup.effect.armor.1": ("Uniforme de cuero", "Leather uniform"),
    "tcgshop.guardup.effect.armor.2": ("Armadura de hierro", "Iron armor"),
    "tcgshop.guardup.effect.armor.3": ("Armadura de diamante", "Diamond armor"),
    "tcgshop.guardup.effect.weapon.0": ("Puños (3 de daño)", "Fists (3 damage)"),
    "tcgshop.guardup.effect.weapon.1": ("Espada de piedra (7 de daño)", "Stone sword (7 damage)"),
    "tcgshop.guardup.effect.weapon.2": ("Espada de hierro (8 de daño)", "Iron sword (8 damage)"),
    "tcgshop.guardup.effect.weapon.3": ("Espada de diamante (9 de daño)", "Diamond sword (9 damage)"),
    "tcgshop.guardup.effect.training.0": ("40 de vida", "40 health"),
    "tcgshop.guardup.effect.training.1": ("55 de vida y se recupera antes", "55 health, recovers faster"),
    "tcgshop.guardup.effect.training.2": ("70 de vida y se recupera antes", "70 health, recovers faster"),
    "tcgshop.guardup.effect.training.3": ("90 de vida y se recupera muy rápido", "90 health, recovers very fast"),
    "tcgshop.guardup.effect.detection.0": ("Persigue en 14 bloques", "Chases within 14 blocks"),
    "tcgshop.guardup.effect.detection.1": ("20 bloques; ve mejor a los ladrones hábiles", "20 blocks; spots skilled thieves better"),
    "tcgshop.guardup.effect.detection.2": ("26 bloques; a veces reconoce a ladrones conocidos", "26 blocks; sometimes recognizes known thieves"),
    "tcgshop.guardup.effect.detection.3": ("32 bloques; reconoce a los ladrones conocidos y vigila a los atracadores antes del golpe",
                                           "32 blocks; recognizes known thieves and tails robbers before they strike"),
    "tcgshop.guardup.button": ("[Mejorar: %s]", "[Upgrade: %s]"),
    "tcgshop.guardup.max": ("[MÁX]", "[MAX]"),
    "tcgshop.guardup.record": ("Historial: %s ladrones detenidos · %s atracos frustrados · %s atracos perdidos",
                               "Record: %s thieves detained · %s robberies foiled · %s robberies lost"),
    "tcgshop.guardup.hint": ("Agáchate y haz clic derecho en tu guardia para mejorar su equipo.", "Sneak + right-click your guard to upgrade their gear."),

    # advancements (tools/gen_adv_10c_customers.py)
    "advancement.tcgshop.meet_fixed.title": ("Una cara conocida", "A familiar face"),
    "advancement.tcgshop.meet_fixed.desc": ("Recibe la visita de uno de los 10 clientes únicos", "Get a visit from one of the 10 unique customers"),
    "advancement.tcgshop.meet_all_fixed.title": ("Clientela legendaria", "Legendary clientele"),
    "advancement.tcgshop.meet_all_fixed.desc": ("Recibe la visita de los 10 clientes únicos", "Get visited by all 10 unique customers"),
    "advancement.tcgshop.aurelio_tip.title": ("Propina de caballero", "A gentleman's tip"),
    "advancement.tcgshop.aurelio_tip.desc": ("Recibe una propina de Don Aurelio", "Get a tip from Don Aurelio"),
    "advancement.tcgshop.catch_vinnie.title": ("Te pillé, Vinnie", "Gotcha, Vinnie"),
    "advancement.tcgshop.catch_vinnie.desc": ("Atrapa a Vinnie Vex con las manos en la masa", "Catch Vinnie Vex red-handed"),
    "advancement.tcgshop.guard_upgrade.title": ("Equipo reglamentario", "Standard issue"),
    "advancement.tcgshop.guard_upgrade.desc": ("Compra una mejora para tu guardia", "Buy an upgrade for your guard"),
    "advancement.tcgshop.guard_maxed.title": ("Fortaleza de cartón", "Cardboard fortress"),
    "advancement.tcgshop.guard_maxed.desc": ("Sube al máximo todas las mejoras de un guardia", "Max out every upgrade of a guard"),
    "advancement.tcgshop.stop_robbery.title": ("Hoy no, atracador", "Not today, robber"),
    "advancement.tcgshop.stop_robbery.desc": ("Derrota a un atracador armado antes de que escape", "Defeat an armed robber before they escape"),
    "advancement.tcgshop.stop_heist.title": ("El golpe del siglo... frustrado", "Heist of the century... foiled"),
    "advancement.tcgshop.stop_heist.desc": ("Frustra el golpe de una banda en tu tienda", "Foil a crew's heist at your shop"),
}


def build():
    out = dict(T)
    for fid, cats in FIXED.items():
        for cat, lines in cats.items():
            want = COMMON_COUNTS.get(cat, SPECIAL_COUNT)
            assert len(lines) == want, f"fixed {fid}.{cat}: {len(lines)} lines, expected {want}"
            for n, pair in enumerate(lines):
                out[f"tcgshop.fixed.{fid}.{cat}.{n}"] = pair
        for cat in COMMON_COUNTS:
            assert cat in cats, f"fixed {fid} lacks {cat}"
    for prefix, lines in GROUPS.items():
        for n, pair in enumerate(lines):
            out[f"{prefix}.{n}"] = pair
    return out


def main():
    texts = build()
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in texts.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(texts), "texts")


if __name__ == "__main__":
    main()
