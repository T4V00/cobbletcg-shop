# -*- coding: utf-8 -*-
"""0.9 - "shop" stream: shop missions, capacity and queue (ShopMissions, ShopLife.queueGate, ShopStatusScreen).

Only adds / updates its own keys in es_es.json and en_us.json (idempotent, keeps the order of the rest).
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
LANG = os.path.join(HERE, "..", "common", "src", "main", "resources", "assets", "tcgshop", "lang")

ES = {}
EN = {}


def k(key, es, en):
    ES[key] = es
    EN[key] = en


# ---------------------------------------------------------------- shop status window
k("tcgshop.status.stats", "Estadísticas", "Statistics")
k("tcgshop.status.rep", "Reputación", "Reputation")
k("tcgshop.status.revenue", "Ingresos", "Revenue")
k("tcgshop.status.sales", "Ventas", "Sales")
k("tcgshop.status.balance", "En caja", "In the register")
k("tcgshop.status.profit", "Beneficio total", "Total profit")
k("tcgshop.status.reviews", "Reseñas", "Reviews")
k("tcgshop.status.reviews_val", "%s★ (%s)", "%s★ (%s)")
k("tcgshop.status.capacity", "Aforo", "Capacity")
k("tcgshop.status.capacity_base", "Base: %s clientes (tienda de %s)", "Base: %s customers (%s shop)")
k("tcgshop.status.capacity_bonus", "Bonus: +%s (máximo +%s)", "Bonus: +%s (max +%s)")
k("tcgshop.status.capacity_how",
  "+1 con %s misiones, +1 con todas (%s) y +1 con una mesa de juego o 3 asientos.",
  "+1 at %s missions, +1 with all of them (%s) and +1 with a play table or 3 seats.")
k("tcgshop.status.capacity_queue",
  "Con la tienda llena, los clientes esperan fuera; si tardas mucho, se van enfadados.",
  "When the shop is full, customers wait outside; if it takes too long, they leave annoyed.")
k("tcgshop.status.queue", "Esperando fuera", "Waiting outside")
k("tcgshop.status.overflow", "Se quedaron fuera hoy", "Turned away today")
k("tcgshop.status.flow", "Flujo de clientes", "Customer flow")
k("tcgshop.status.flow_tip",
  "Sin misiones llega el %s de la gente; con todas, el %s.",
  "With no missions you get %s of the customers; with all of them, %s.")
k("tcgshop.status.flow_hint",
  "Cada misión cumplida atrae a más clientes.",
  "Every completed mission brings in more customers.")
k("tcgshop.status.upgrade",
  "¡Tu tienda se llena y la gente se va! Mejórala a %s: aforo %s.",
  "Your shop keeps filling up and people leave! Upgrade it to %s: capacity %s.")
k("tcgshop.status.missions", "Misiones", "Missions")
k("tcgshop.status.next", "Siguiente paso", "Next step")
k("tcgshop.status.all_done",
  "¡Todas las misiones cumplidas! Tu tienda atrae al máximo de clientes.",
  "All missions complete! Your shop draws the most customers possible.")
k("tcgshop.status.not_calc",
  "Aún no se ha podido revisar la tienda (¿hay partes sin cargar?). Acércate y vuelve a mirar.",
  "The shop couldn't be checked yet (are parts of it unloaded?). Get closer and look again.")
k("tcgshop.status.done", "Hecho", "Done")
k("tcgshop.status.close", "Cerrar", "Close")
k("tcgshop.status.chat_line",
  "Aforo %s/%s · Misiones %s/%s · Flujo de clientes %s",
  "Capacity %s/%s · Missions %s/%s · Customer flow %s")
k("tcgshop.status.chat_next", "Siguiente paso: %s — %s", "Next step: %s — %s")

# ---------------------------------------------------------------- missions
MISSIONS = [
    ("stock", "Vitrinas con producto", "Products on display",
     "Pon algo a la venta en al menos 2 vitrinas o expositores.",
     "Put something for sale in at least 2 display cases or stands."),
    ("register", "Caja registradora", "Cash register",
     "Coloca una caja registradora en la tienda o contrata a un cajero.",
     "Place a cash register in the shop or hire a cashier."),
    ("building", "Un local de verdad", "A real building",
     "Pon techo y paredes alrededor de tus muebles: nadie compra a la intemperie.",
     "Put a roof and walls around your furniture: nobody shops out in the open."),
    ("light", "Buena iluminación", "Good lighting",
     "Pon antorchas, faroles o lámparas hasta que la luz media llegue a 8.",
     "Add torches, lanterns or lamps until the average light reaches 8."),
    ("prices", "Precios justos", "Fair prices",
     "Ajusta los precios al valor de mercado o un poco por debajo.",
     "Set your prices at market value or slightly below."),
    ("decor", "Decoración mínima", "Basic decoration",
     "Coloca al menos 3 pósteres, pedestales, mesas de juego o peluches.",
     "Place at least 3 posters, pedestals, play tables or plushies."),
    ("variety", "Variedad", "Variety",
     "Ten a la venta al menos 6 productos distintos.",
     "Have at least 6 different products for sale."),
    ("box", "Una caja de sobres", "A booster box",
     "Compra una caja de sobres al Mayorista y ponla a la venta en una vitrina.",
     "Buy a booster box from the Wholesaler and put it for sale in a display."),
    ("clean", "Tienda limpia y segura", "Clean and safe shop",
     "Quita el mal olor con el spray desodorante y que no haya monstruos dentro.",
     "Remove bad smells with the deodorant spray and keep monsters out."),
    ("play", "Zona de juego", "Play area",
     "Pon una mesa de juego o al menos 2 sillas, bancos o sofás.",
     "Place a play table or at least 2 chairs, benches or sofas."),
    ("reviews", "Buenas reseñas", "Good reviews",
     "Consigue 5 reseñas con una media de 3★ o más.",
     "Get 5 reviews averaging 3★ or more."),
    ("tier", "Tienda más grande", "Bigger shop",
     "Mejora tu tienda a Piedra o más: más espacio y más aforo.",
     "Upgrade your shop to Stone or better: more space and more capacity."),
]
for key, es, en, hes, hen in MISSIONS:
    k("tcgshop.mission." + key, es, en)
    k("tcgshop.mission." + key + ".hint", hes, hen)
k("tcgshop.mission.completed",
  "✔ Misión cumplida: %s (%s/%s) · Flujo de clientes %s",
  "✔ Mission complete: %s (%s/%s) · Customer flow %s")

# ---------------------------------------------------------------- capacity / queue
k("tcgshop.shop.full_queue",
  "Tienda llena (aforo %s): hay clientes esperando fuera.",
  "Shop full (capacity %s): customers are waiting outside.")
k("tcgshop.shop.full_left",
  "%s se cansó de esperar fuera y se fue (aforo %s). Mejora la tienda para que quepa más gente.",
  "%s got tired of waiting outside and left (capacity %s). Upgrade the shop to fit more people.")
k("tcgshop.review.shop_full", "La tienda estaba llena", "The shop was full")

WAIT_ES = [
    "Uf, está lleno. Espero un poco.",
    "Hay mucha gente dentro... me pongo a la cola.",
    "Esperaré aquí fuera a que salga alguien.",
    "¡Qué éxito tiene esta tienda! Toca esperar.",
    "Me quedo en la puerta, que no cabe ni un alfiler.",
    "No pasa nada, espero mi turno.",
    "Lleno hasta arriba... ojalá no tarden.",
    "Vaya cola. Bueno, aguantaré un rato.",
]
WAIT_EN = [
    "Phew, it's packed. I'll wait a bit.",
    "So many people inside... I'll join the line.",
    "I'll wait out here until someone leaves.",
    "This shop is a hit! Guess I'll wait.",
    "I'll stay by the door, it's completely full.",
    "No problem, I'll wait my turn.",
    "Packed to the brim... hope it's quick.",
    "What a line. Fine, I'll hang on for a while.",
]
LEAVE_ES = [
    "Demasiada gente. Otro día será.",
    "No pienso esperar más, me voy.",
    "Siempre está llena esta tienda... adiós.",
    "Volveré cuando haya sitio. Si me acuerdo.",
    "Pues nada, me voy a otra tienda.",
    "Ni para entrar se puede. Qué pena.",
    "Me he cansado de esperar en la puerta.",
    "Que amplíen el local, que no cabemos.",
]
LEAVE_EN = [
    "Too many people. Maybe another day.",
    "I'm not waiting any longer, I'm off.",
    "This shop is always full... bye.",
    "I'll come back when there's room. If I remember.",
    "Oh well, I'll try another shop.",
    "Can't even get in. What a shame.",
    "I'm tired of waiting at the door.",
    "They should make this place bigger, we don't fit.",
]
for i in range(8):
    k("tcgshop.npc.shop_full_wait.%d" % i, WAIT_ES[i], WAIT_EN[i])
    k("tcgshop.npc.shop_full_leave.%d" % i, LEAVE_ES[i], LEAVE_EN[i])

# "shop_full" reviews: tcgshop.reviewtext.shop_full.<stars>.<0-7> (ShopZones.Review.textKey)
REV_ES = {
    1: ["Horrible. Ni siquiera pude entrar.", "Una hora en la puerta para nada. No vuelvo.",
        "Tienda llena y nadie hace nada. Una estrella.", "Me dejaron fuera esperando. Pésimo.",
        "Imposible entrar. Que amplíen el local.", "Lo peor: esperé fuera y me tuve que ir.",
        "No cabía nadie más. Qué desastre.", "Cola eterna en la calle. Evitadla."],
    2: ["Flojita: estaba tan llena que no pude entrar.", "Esperé fuera demasiado. Se les queda pequeña.",
        "Parece buena, pero no cabe la gente.", "Decepcionante. Me fui sin poder entrar.",
        "Mucha cola en la puerta. Tienen que crecer.", "Ni la vi por dentro: estaba llena.",
        "Dos estrellas por la espera en la calle.", "Se les llena enseguida. A ver si amplían."],
    3: ["Normalita. Estaba llena y no pude esperar más.", "Mucha gente, señal de que algo hacen bien.",
        "Volveré otro día, hoy no cabía.", "Pasable: el local se les queda pequeño.",
        "Ni bien ni mal, sólo llena.", "Se nota que tiene éxito, pero no entré.",
        "Regular. Mucha cola en la puerta.", "Tres estrellas: buen ambiente, poco sitio."],
    4: ["Tiene tanto éxito que no cabía. Volveré.", "Buena señal que esté llena. Probaré otro día.",
        "Desde fuera pinta genial. Hoy no pude entrar.", "Recomendable, si consigues entrar.",
        "Me gustó lo que vi desde la puerta.", "Muy popular. Sólo le falta más espacio.",
        "Casi perfecta, pero estaba a reventar.", "Volveré temprano para pillar sitio."],
    5: ["¡Llena de gente! Se nota que es la mejor.", "No pude entrar, pero volveré seguro.",
        "Tanto éxito tiene que no cabe nadie. ¡Cinco estrellas!", "La tienda de moda del pueblo.",
        "Me encanta aunque haya cola.", "Si está llena por algo será. Recomendadísima.",
        "Volveré mañana, ¡qué ambientazo!", "Ojalá amplíen: esta tienda se lo merece."],
}
REV_EN = {
    1: ["Awful. I couldn't even get in.", "An hour at the door for nothing. Never again.",
        "Shop full and nobody does anything. One star.", "They left me waiting outside. Terrible.",
        "Impossible to get in. They need a bigger place.", "The worst: I waited outside and had to leave.",
        "No room for anyone else. What a mess.", "Endless line in the street. Avoid it."],
    2: ["Meh: so full I couldn't get in.", "Waited outside too long. It's too small for them.",
        "Looks good, but people don't fit.", "Disappointing. I left without getting in.",
        "Long line at the door. They need to grow.", "Never saw the inside: it was full.",
        "Two stars for the wait in the street.", "It fills up instantly. Hope they expand."],
    3: ["Average. It was full and I couldn't wait any longer.", "Lots of people, so they must be doing something right.",
        "I'll come back another day, no room today.", "Okay: the place is too small for them.",
        "Not good, not bad, just full.", "It's clearly popular, but I didn't get in.",
        "So-so. Long line at the door.", "Three stars: nice vibe, little room."],
    4: ["So popular there was no room. I'll be back.", "A full shop is a good sign. I'll try another day.",
        "Looks great from outside. Couldn't get in today.", "Recommended, if you can get in.",
        "I liked what I saw from the door.", "Very popular. It just needs more space.",
        "Almost perfect, but it was packed.", "I'll come early to get a spot."],
    5: ["Packed with people! Clearly the best.", "Couldn't get in, but I'll definitely be back.",
        "So popular nobody fits. Five stars!", "The trendiest shop in town.",
        "I love it even with the line.", "If it's full there's a reason. Highly recommended.",
        "Coming back tomorrow, what a vibe!", "I hope they expand: this shop deserves it."],
}
for st in range(1, 6):
    for i in range(8):
        k("tcgshop.reviewtext.shop_full.%d.%d" % (st, i), REV_ES[st][i], REV_EN[st][i])


def apply(fname, data):
    path = os.path.join(LANG, fname)
    with open(path, encoding="utf-8") as f:
        cur = json.load(f)
    for key, val in data.items():
        cur[key] = val
    with open(path, "w", encoding="utf-8") as f:
        json.dump(cur, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("%s: %d keys" % (fname, len(data)))


if __name__ == "__main__":
    assert set(ES) == set(EN)
    apply("es_es.json", ES)
    apply("en_us.json", EN)
