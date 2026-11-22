#!/usr/bin/env python3
"""0.9.5 - Llave de la Mansión MasterBall (Exclusivos): textos es_es / en_us. Idempotente; sólo toca sus claves."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "tcgshop" / "lang"

ES = {
    "item.tcgshop.mansion_key": "Llave de la Mansión MasterBall",
    "tcgshop.mansion.tip_size": "Finca de 100×100 con mansión, jardín, fuentes y piscina",
    "tcgshop.mansion.flavor": "Una finca entera en el bolsillo. Pokémon residentes incluidos.",
    "tcgshop.mansion.tip_use1": "Clic derecho en el suelo: vista previa delante de ti",
    "tcgshop.mansion.tip_use2": "Agachado + clic derecho en 10 s: ¡a construir!",
    "tcgshop.mansion.preview": "Vista previa: la finca ocupará 100×100 bloques delante de ti, con la verja mirando hacia ti (contorno dorado, verja en verde).",
    "tcgshop.mansion.confirm_hint": "Haz clic derecho otra vez agachado en %s s para construir",
    "tcgshop.mansion.warn_terrain": "Aviso: se allanará el terreno de la zona (unos %s bloques desaparecerán)",
    "tcgshop.mansion.warn_water": "Aviso: hay agua en la zona; se secará y podría volver a entrar por los bordes",
    "tcgshop.mansion.warn_own_shop": "¡Cuidado! La finca pisa tu propia tienda y la destruirá",
    "tcgshop.mansion.err_ground": "Tienes que estar de pie en el suelo",
    "tcgshop.mansion.err_dimension": "La mansión sólo se puede construir en un mundo con cielo abierto (ni en el Nether ni en el End)",
    "tcgshop.mansion.err_height": "No hay altura suficiente aquí: hacen falta %s bloques por encima del suelo y %s por debajo",
    "tcgshop.mansion.err_border": "La finca se saldría del borde del mundo o pisaría una zona protegida",
    "tcgshop.mansion.err_shop": "La finca pisaría la tienda de %s",
    "tcgshop.mansion.err_mansion": "La finca pisaría otra mansión (de %s)",
    "tcgshop.mansion.err_busy": "Ya tienes una mansión en obras; espera a que termine",
    "tcgshop.mansion.err_queue": "Hay demasiadas mansiones en obras ahora mismo; prueba dentro de un rato",
    "tcgshop.mansion.err_expired": "La vista previa de la mansión ha caducado; vuelve a hacer clic derecho",
    "tcgshop.mansion.started": "¡Empieza la construcción de tu Mansión MasterBall! Quédate cerca hasta que termine.",
    "tcgshop.mansion.progress": "Construyendo la Mansión MasterBall... %s%%",
    "tcgshop.mansion.done": "¡Tu Mansión MasterBall está terminada! Bienvenido a casa.",
    "tcgshop.mansion.failed": "La construcción de la mansión ha fallado; avisa a un administrador",
    "tcgshop.mansion.announce": "✦ %s acaba de estrenar una Mansión MasterBall ✦",
    "tcgshop.mansion.no_resale": "La Llave de la Mansión no se puede revender",
    "tcgshop.mansion.err_cooldown": "Espera un momento antes de volver a ver la vista previa",
    "tcgshop.mansion.err_display": "La finca pisaría un expositor de otro jugador (en %s)",
    "tcgshop.mansion.err_container": "Hay un cofre o contenedor con cosas en la zona (en %s); vacíalo o retíralo antes",
    "tcgshop.mansion.err_statue": "Hay una estatua de lujo en la zona (en %s); recógela antes o se perdería",
    "tcgshop.mansion.warn_unloaded": "Parte de la zona está lejos y no se ha podido revisar; se comprobará al confirmar",
    "tcgshop.mansion.warn_own_displays": "¡Cuidado! Hay %s expositores tuyos con cartas en la zona; se perderán con todo lo que tengan",
    "tcgshop.mansion.warn_overhang": "¡Cuidado! Hay terreno a más de %s bloques de altura que no se despejará y quedará colgando sobre la finca",
    "tcgshop.mansion.warn_hollow": "Aviso: hay huecos (barranco, cueva o acantilado) bajo la zona; los bordes se rellenarán hasta tocar suelo",
    "tcgshop.mansion.retrying": "La obra de la mansión ha tenido un problema; se vuelve a intentar (intento %s)",
}

EN = {
    "item.tcgshop.mansion_key": "MasterBall Mansion Key",
    "tcgshop.mansion.tip_size": "100×100 estate with mansion, garden, fountains and pool",
    "tcgshop.mansion.flavor": "A whole estate in your pocket. Resident Pokémon included.",
    "tcgshop.mansion.tip_use1": "Right-click on the ground: preview in front of you",
    "tcgshop.mansion.tip_use2": "Sneak + right-click within 10 s: build it!",
    "tcgshop.mansion.preview": "Preview: the estate will take up 100×100 blocks in front of you, gate facing you (gold outline, gate in green).",
    "tcgshop.mansion.confirm_hint": "Sneak and right-click again within %s s to build",
    "tcgshop.mansion.warn_terrain": "Warning: the terrain in the area will be levelled (about %s blocks will be removed)",
    "tcgshop.mansion.warn_water": "Warning: there is water in the area; it will be drained and may flow back in from the edges",
    "tcgshop.mansion.warn_own_shop": "Careful! The estate overlaps your own shop and will destroy it",
    "tcgshop.mansion.err_ground": "You must be standing on the ground",
    "tcgshop.mansion.err_dimension": "The mansion can only be built in a world with open sky (not in the Nether or the End)",
    "tcgshop.mansion.err_height": "Not enough height here: it needs %s blocks above the ground and %s below",
    "tcgshop.mansion.err_border": "The estate would go past the world border or into a protected area",
    "tcgshop.mansion.err_shop": "The estate would overlap %s's shop",
    "tcgshop.mansion.err_mansion": "The estate would overlap another mansion (%s's)",
    "tcgshop.mansion.err_busy": "You already have a mansion under construction; wait until it is finished",
    "tcgshop.mansion.err_queue": "Too many mansions are being built right now; try again in a while",
    "tcgshop.mansion.err_expired": "The mansion preview has expired; right-click again",
    "tcgshop.mansion.started": "Construction of your MasterBall Mansion has begun! Stay nearby until it is done.",
    "tcgshop.mansion.progress": "Building the MasterBall Mansion... %s%%",
    "tcgshop.mansion.done": "Your MasterBall Mansion is finished! Welcome home.",
    "tcgshop.mansion.failed": "Building the mansion failed; please tell an administrator",
    "tcgshop.mansion.announce": "✦ %s just moved into a MasterBall Mansion ✦",
    "tcgshop.mansion.no_resale": "The Mansion Key cannot be resold",
    "tcgshop.mansion.err_cooldown": "Wait a moment before previewing again",
    "tcgshop.mansion.err_display": "The estate would overlap another player's display (at %s)",
    "tcgshop.mansion.err_container": "There is a chest or container with items in the area (at %s); empty or remove it first",
    "tcgshop.mansion.err_statue": "There is a luxury statue in the area (at %s); pick it up first or it would be lost",
    "tcgshop.mansion.warn_unloaded": "Part of the area is too far away to check; it will be checked when you confirm",
    "tcgshop.mansion.warn_own_displays": "Careful! %s of your displays with cards are in the area; they will be lost with everything in them",
    "tcgshop.mansion.warn_overhang": "Careful! There is terrain more than %s blocks up that will not be cleared and will hang over the estate",
    "tcgshop.mansion.warn_hollow": "Warning: there are gaps (ravine, cave or cliff) under the area; the edges will be filled down to solid ground",
    "tcgshop.mansion.retrying": "The mansion build ran into a problem; trying again (attempt %s)",
}


def apply(name, values):
    path = LANG / name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, v in values.items():
        data[k] = v  # las claves existentes conservan su posición; las nuevas van al final
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    assert set(ES) == set(EN)
    apply("es_es.json", ES)
    apply("en_us.json", EN)
    print("gen_lang_095_mansion:", len(ES), "claves")
