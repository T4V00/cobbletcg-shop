#!/usr/bin/env python3
"""
Texts (es_es / en_us) for the 0.9 events incident module: block, tools, fix hints, the 9
incident events (name, description, start, how to fix it, variants) and the customer lines.
Only writes ITS OWN keys; reloads the JSON right before writing. Idempotent.

Usage:  python -I -X utf8 tools/gen_lang_09_events_incidents.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "tcgshop" / "lang"

ES = {}
EN = {}


def put(key, es, en):
    ES[key] = es
    EN[key] = en


# ------------------------------------------------------------------ block and tools
put("block.tcgshop.incident", "Incidente", "Incident")
put("item.tcgshop.mop", "Fregona", "Mop")
put("item.tcgshop.mop.tip", "Limpia charcos, excrementos y desorden de tu tienda.", "Cleans puddles, droppings and mess in your shop.")
put("item.tcgshop.repair_kit", "Kit de reparación", "Repair Kit")
put("item.tcgshop.repair_kit.tip", "Arregla chispas, quemaduras, cajas y vitrinas averiadas.", "Fixes sparks, scorch marks, broken registers and displays.")
put("item.tcgshop.incident_tool.uses", "Usos: %s/%s", "Uses: %s/%s")

put("tcgshop.incident.not_owner", "Sólo el dueño de la tienda puede encargarse de esto.", "Only the shop owner can deal with this.")
put("tcgshop.incident.hits_left", "Un poco más... (faltan %s)", "A bit more... (%s left)")
put("tcgshop.incident.left", "¡Bien! Quedan %s por arreglar.", "Nice! %s left to fix.")
put("tcgshop.incident.package.opened", "Abres la caja: %s objetos dentro.", "You open the box: %s items inside.")
put("tcgshop.incident.package.empty", "La caja estaba vacía... (ya abriste bastantes esta semana)", "The box was empty... (you've opened plenty this week)")
NEED = {
    "puddle": ("Necesitas una fregona para secar el charco.", "You need a mop to dry the puddle."),
    "debris": ("Retira los escombros con la mano vacía.", "Clear the debris with an empty hand."),
    "dirt": ("Necesitas una pala para quitar la tierra.", "You need a shovel to clear the dirt."),
    "droppings": ("Necesitas una fregona para limpiar esto.", "You need a mop to clean this up."),
    "scorch": ("Necesitas un kit de reparación.", "You need a repair kit."),
    "sparks": ("Necesitas un kit de reparación para arreglar la avería.", "You need a repair kit to fix the fault."),
    "mess": ("Recógelo con la mano vacía o con una fregona.", "Pick it up with an empty hand or a mop."),
    "package": ("Ábrela con la mano vacía.", "Open it with an empty hand."),
}
for k, (es, en) in NEED.items():
    put(f"tcgshop.incident.need.{k}", es, en)

# ------------------------------------------------------------------ events
EV = {
    "power_failure": (
        ("Fallo eléctrico", "Power Failure"),
        ("Saltan chispas en el cuadro eléctrico: la tienda está casi a oscuras y la caja no funciona para el cajero.",
         "Sparks fly from the fuse box: the shop is nearly dark and the register won't work for the cashier."),
        ("¡Se ha ido la luz en la tienda!", "The power just went out in the shop!"),
        ("Usa un kit de reparación en las chispas, junto al núcleo de la tienda.", "Use a repair kit on the sparks next to the shop core."),
    ),
    "water_leak": (
        ("Fuga de agua", "Water Leak"),
        ("Hay charcos por el suelo: los clientes resbalan, tienen menos paciencia y compran menos.",
         "There are puddles on the floor: customers slip, lose patience and buy less."),
        ("¡Hay una fuga y el suelo se está llenando de charcos!", "There's a leak and the floor is filling with puddles!"),
        ("Seca todos los charcos con una fregona.", "Dry every puddle with a mop."),
    ),
    "register_broken": (
        ("Caja registradora averiada", "Broken Register"),
        ("La caja echa chispas: el cajero no puede cobrar sobres ni cajas (tú en persona sí).",
         "The register is sparking: the cashier can't ring up packs or boxes (you still can in person)."),
        ("¡La caja registradora se ha averiado!", "The cash register broke down!"),
        ("Usa un kit de reparación en las chispas junto a la caja.", "Use a repair kit on the sparks next to the register."),
    ),
    "ac_broken": (
        ("Aire acondicionado averiado", "Broken Air Conditioning"),
        ("Hace un calor horrible dentro: los clientes llegan de mal humor y se cansan antes.",
         "It's sweltering inside: customers arrive grumpy and give up sooner."),
        ("¡El aire acondicionado ha dejado de funcionar!", "The air conditioning stopped working!"),
        ("Usa un kit de reparación en el aparato que echa chispas.", "Use a repair kit on the sparking unit."),
    ),
    "stuck_door": (
        ("Puerta atascada", "Stuck Door"),
        ("Unos escombros bloquean una de las puertas: nadie puede entrar por ella.",
         "Some debris is blocking one of the doors: nobody can come in through it."),
        ("¡Una puerta se ha quedado atascada!", "One of the doors got stuck!"),
        ("Retira los escombros con la mano vacía (varios golpes).", "Clear the debris with an empty hand (a few hits)."),
    ),
    "broken_display": (
        ("Vitrina rota", "Broken Display"),
        ("Un expositor se ha averiado y nadie puede comprar de él. Tus productos siguen dentro, a salvo.",
         "A display broke down and nobody can buy from it. Your products are still inside, safe."),
        ("¡Uno de tus expositores se ha roto!", "One of your displays broke!"),
        ("Usa un kit de reparación en las chispas junto al expositor.", "Use a repair kit on the sparks next to the display."),
    ),
    "messy_stockroom": (
        ("Almacén desordenado", "Messy Stockroom"),
        ("Hay cajas tiradas por todas partes y cuesta encontrar nada: el servicio va lento.",
         "Boxes are scattered everywhere and nothing can be found: service is slow."),
        ("¡El almacén es un desastre!", "The stockroom is a mess!"),
        ("Recoge el desorden con la mano vacía o con una fregona.", "Tidy the mess with an empty hand or a mop."),
    ),
    "clumsy_customer": (
        ("Cliente patoso", "Clumsy Customer"),
        ("Un cliente torpe ha tirado productos al suelo: esos huecos del expositor no se pueden vender hasta recogerlos.",
         "A clumsy customer knocked products to the floor: those display slots can't sell until you pick them up."),
        ("Ha entrado un cliente que no para de tropezar...", "A customer who keeps tripping over things just walked in..."),
        ("Recoge lo que se ha caído con la mano vacía o con una fregona.", "Pick up what fell with an empty hand or a mop."),
    ),
    "mystery_package": (
        ("Caja misteriosa", "Mystery Box"),
        ("Alguien ha dejado una caja junto a la puerta. ¿Qué habrá dentro?",
         "Someone left a box by the door. What could be inside?"),
        ("Hay una caja misteriosa junto a la puerta. Ábrela con la mano vacía.", "There's a mystery box by the door. Open it with an empty hand."),
        ("Ábrela con la mano vacía antes de que se la lleven.", "Open it with an empty hand before someone takes it."),
    ),
}
for path, (name, desc, start, fix) in EV.items():
    p = f"tcgshop.event.{path}"
    put(p + ".name", *name)
    put(p + ".desc", *desc)
    put(p + ".start", *start)
    put(p + ".fix", *fix)

put("tcgshop.event.power_failure.var.storm", "La tormenta ha hecho saltar los plomos.", "The storm blew the fuses.")
put("tcgshop.event.power_failure.var.pichu", "¡Un Pichu travieso anda mordisqueando los cables cerca de la caja!",
    "A mischievous Pichu is chewing the cables near the register!")
put("tcgshop.event.water_leak.var.roof", "Entra agua de lluvia por el techo.", "Rainwater is dripping through the roof.")
put("tcgshop.event.water_leak.var.squirtle", "¡Un Squirtle juguetón está mojándolo todo!", "A playful Squirtle is soaking everything!")
put("tcgshop.event.messy_stockroom.var.zubat", "Un Zubat se ha colado en el almacén y lo ha revuelto todo.",
    "A Zubat got into the stockroom and knocked everything over.")
put("tcgshop.event.mystery_package.var.no_sender", "La caja no tiene remitente... sólo tu dirección.", "The box has no sender... just your address.")
put("tcgshop.event.clumsy_customer.dropped", "¡Ay! El cliente patoso ha tirado productos de un expositor.",
    "Oops! The clumsy customer knocked products off a display.")
put("tcgshop.role.ev_clumsy", "Cliente patoso", "Clumsy customer")

# ------------------------------------------------------------------ customer lines (on entering)
LINES = {
    "inc_power": [
        ("¿Habéis cerrado? Está todo a oscuras.", "Are you closed? It's pitch dark in here."),
        ("Casi no veo las cartas con esta luz.", "I can barely see the cards in this light."),
        ("Huele a cable quemado...", "Smells like burnt wiring..."),
        ("Voy a usar la linterna del móvil.", "I'll use my phone's flashlight."),
        ("¿Se ha ido la luz en todo el barrio?", "Is the whole block out of power?"),
        ("Qué ambiente tan... tenebroso.", "What a... gloomy atmosphere."),
    ],
    "inc_flood": [
        ("¡Cuidado, que resbala!", "Careful, it's slippery!"),
        ("Me he mojado los zapatos nuevos.", "I just soaked my new shoes."),
        ("¿Esto es una tienda o una piscina?", "Is this a shop or a swimming pool?"),
        ("Espero que el agua no llegue a las cartas.", "I hope the water doesn't reach the cards."),
        ("Voy a ir de puntillas...", "I'll tiptoe through..."),
        ("Hay charcos por todas partes.", "There are puddles everywhere."),
    ],
    "inc_register": [
        ("¿La caja echa chispas?", "Is the register sparking?"),
        ("Espero que pueda pagar con algo...", "I hope I can pay somehow..."),
        ("Esa caja hace un ruido rarísimo.", "That register is making a weird noise."),
        ("¿Sólo se puede pagar con el dueño?", "Can I only pay the owner?"),
        ("Vaya cola se va a formar.", "That's going to make a long line."),
        ("No me fío de esa máquina.", "I don't trust that machine."),
    ],
    "inc_hot": [
        ("¡Qué calor hace aquí dentro!", "It's so hot in here!"),
        ("Me estoy derritiendo.", "I'm melting."),
        ("¿No tenéis aire acondicionado?", "Don't you have air conditioning?"),
        ("Las fundas se me pegan a las manos.", "The sleeves are sticking to my hands."),
        ("Compro rápido y me voy, que me asfixio.", "I'll buy quickly and go, I'm suffocating."),
        ("Fuera hacía más fresco.", "It was cooler outside."),
    ],
    "inc_door": [
        ("La otra puerta está bloqueada.", "The other door is blocked."),
        ("Por poco me tropiezo con los escombros.", "I nearly tripped on the debris."),
        ("He tenido que dar toda la vuelta.", "I had to walk all the way around."),
        ("¿Estáis de obras?", "Are you renovating?"),
        ("Esa puerta no se abre ni a la de tres.", "That door won't budge at all."),
        ("Menos mal que había otra entrada.", "Good thing there was another entrance."),
    ],
    "inc_display": [
        ("Ese expositor echa chispas...", "That display is sparking..."),
        ("Quería mirar esa vitrina y no se puede.", "I wanted to check that display, but I can't."),
        ("¿Está roto ese mueble?", "Is that stand broken?"),
        ("Mejor no me acerco a esas chispas.", "I'd better stay away from those sparks."),
        ("Qué pena, ahí estaba lo bueno.", "Shame, that's where the good stuff was."),
        ("Avisaré cuando lo arreglen.", "I'll come back when it's fixed."),
    ],
    "inc_mess": [
        ("Madre mía, qué desorden.", "Wow, what a mess."),
        ("Casi piso una caja.", "I almost stepped on a box."),
        ("¿Acabáis de recibir mercancía?", "Did you just get a delivery?"),
        ("Así no hay quien encuentre nada.", "Nobody can find anything like this."),
        ("Tardan un montón en atender.", "Service is taking forever."),
        ("Parece que ha pasado un huracán.", "Looks like a hurricane came through."),
    ],
    "inc_clumsy": [
        ("¡Uy! Perdón, perdón...", "Oops! Sorry, sorry..."),
        ("Siempre me pasa lo mismo...", "This always happens to me..."),
        ("¿Esto estaba aquí antes?", "Was this here before?"),
        ("Tranquilos, no he roto nada... creo.", "Relax, I didn't break anything... I think."),
        ("Uf, qué estantes tan estrechos.", "Phew, these shelves are so tight."),
        ("¡Ay! ¿Quién ha puesto eso ahí?", "Ouch! Who put that there?"),
    ],
}
for fam, lines in LINES.items():
    for i, (es, en) in enumerate(lines):
        put(f"tcgshop.evline.{fam}.{i}", es, en)


def write(name, values):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))  # reload right before writing
    changed = sum(1 for k, v in values.items() if data.get(k) != v)
    data.update(values)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return changed


if __name__ == "__main__":
    assert set(ES) == set(EN)
    a = write("es_es.json", ES)
    b = write("en_us.json", EN)
    print(f"gen_lang_09_events_incidents: {len(ES)} keys per language ({a} es / {b} en changed)")
