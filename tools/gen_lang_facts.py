#!/usr/bin/env python3
"""Textos de los datos curiosos por hitos de cartas distintas (FunFacts.MILESTONES)."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

FACTS = {
    1: ("Toda gran colección empieza igual que la Pokédex: por el número 001, Bulbasaur.",
        "Every great collection starts like the Pokédex: with number 001, Bulbasaur."),
    4: ("El número 004 coincide con Charmander. ¡Que tu colección arda con fuerza!",
        "Number 004 matches Charmander. May your collection burn bright!"),
    7: ("El número 007 coincide con Squirtle. Tu colección empieza a hacer olas.",
        "Number 007 matches Squirtle. Your collection is starting to make waves."),
    10: ("El número 010 coincide con Caterpie, el primer Pokémon que Ash atrapó en el anime.",
         "Number 010 matches Caterpie, the first Pokémon Ash caught in the anime."),
    25: ("¡El número 025 coincide con Pikachu, el Pokémon más famoso del mundo!",
         "Number 025 matches Pikachu, the most famous Pokémon in the world!"),
    39: ("El número 039 coincide con Jigglypuff. Estaría cantando de alegría... aunque todos se durmieran.",
         "Number 039 matches Jigglypuff. It would sing with joy... even if everyone fell asleep."),
    52: ("El número 052 coincide con Meowth. Con tantas cartas, ¡eso es todo un Día de Pago!",
         "Number 052 matches Meowth. With this many cards, that's quite a Pay Day!"),
    60: ("60 cartas es exactamente el tamaño de un mazo estándar del JCC Pokémon.",
         "60 cards is exactly the size of a standard Pokémon TCG deck."),
    94: ("El número 094 coincide con Gengar, que seguro te espía la colección desde tu sombra.",
         "Number 094 matches Gengar, who is surely peeking at your collection from your shadow."),
    100: ("El número 100 coincide con Voltorb. Cuidado, no explotes de emoción.",
          "Number 100 matches Voltorb. Careful, don't explode with excitement."),
    102: ("102 cartas: las mismas que tenía el Base Set original de 1999.",
          "102 cards: the same number as the original 1999 Base Set."),
    108: ("El número 108 coincide con Lickitung... y Spiritomb está formado por 108 almas.",
          "Number 108 matches Lickitung... and Spiritomb is made of 108 spirits."),
    129: ("El número 129 coincide con Magikarp. Todos lo subestiman hasta que evoluciona. Como tu colección.",
          "Number 129 matches Magikarp. Everyone underestimates it until it evolves. Like your collection."),
    133: ("El número 133 coincide con Eevee: tantas evoluciones como formas de ordenar un binder.",
          "Number 133 matches Eevee: as many evolutions as ways to sort a binder."),
    143: ("El número 143 coincide con Snorlax, que bloquea el camino... hacia tu siguiente sobre.",
          "Number 143 matches Snorlax, blocking the road... to your next pack."),
    150: ("El número 150 coincide con Mewtwo, el Pokémon creado por la ciencia.",
          "Number 150 matches Mewtwo, the Pokémon created by science."),
    151: ("¡151! El mismo número que la Pokédex original de Kanto, que termina con Mew.",
          "151! The same as the original Kanto Pokédex, which ends with Mew."),
    197: ("El número 197 coincide con Umbreon, protagonista de algunas de las cartas más caras de la era moderna.",
          "Number 197 matches Umbreon, star of some of the most expensive modern cards."),
    249: ("El número 249 coincide con Lugia, el guardián de los mares.",
          "Number 249 matches Lugia, guardian of the seas."),
    250: ("El número 250 coincide con Ho-Oh: dicen que quien lo ve encuentra la felicidad eterna.",
          "Number 250 matches Ho-Oh: they say whoever sees it finds eternal happiness."),
    251: ("El número 251 coincide con Celebi, que cierra la Pokédex de Johto. Tu colección viaja en el tiempo.",
          "Number 251 matches Celebi, who closes the Johto Pokédex. Your collection travels through time."),
    282: ("El número 282 coincide con Gardevoir, capaz de proteger a su entrenador con su vida.",
          "Number 282 matches Gardevoir, willing to protect its trainer with its life."),
    384: ("El número 384 coincide con Rayquaza, el señor de los cielos.",
          "Number 384 matches Rayquaza, lord of the skies."),
    386: ("El número 386 coincide con Deoxys, que cierra la Pokédex de Hoenn.",
          "Number 386 matches Deoxys, who closes the Hoenn Pokédex."),
    448: ("El número 448 coincide con Lucario, que percibe el aura... de las cartas raras.",
          "Number 448 matches Lucario, who can sense the aura... of rare cards."),
    493: ("El número 493 coincide con Arceus, que según los mitos creó el universo.",
          "Number 493 matches Arceus, who according to myth created the universe."),
    500: ("¡Medio millar de cartas distintas! Emboar, el número 500, lo celebra a lo grande.",
          "Half a thousand different cards! Emboar, number 500, celebrates big."),
    570: ("El número 570 coincide con Zorua, que podría hacerse pasar por cualquiera de tus cartas.",
          "Number 570 matches Zorua, who could pass for any of your cards."),
    649: ("El número 649 coincide con Genesect, que cierra la Pokédex de Teselia.",
          "Number 649 matches Genesect, who closes the Unova Pokédex."),
    658: ("El número 658 coincide con Greninja, maestro ninja de Kalos.",
          "Number 658 matches Greninja, the ninja master of Kalos."),
    700: ("El número 700 coincide con Sylveon, la Eeveelución de tipo Hada.",
          "Number 700 matches Sylveon, the Fairy-type Eeveelution."),
    721: ("El número 721 coincide con Volcanion, que cierra la Pokédex de Kalos.",
          "Number 721 matches Volcanion, who closes the Kalos Pokédex."),
    778: ("El número 778 coincide con Mimikyu, que sólo quiere ser querido... como tus cartas comunes.",
          "Number 778 matches Mimikyu, who just wants to be loved... like your common cards."),
    809: ("El número 809 coincide con Melmetal, que cierra la Pokédex de Alola (y vino de Pokémon GO).",
          "Number 809 matches Melmetal, who closes the Alola Pokédex (and came from Pokémon GO)."),
    888: ("El número 888 coincide con Zacian. ¡Triple ocho, número de la suerte!",
          "Number 888 matches Zacian. Triple eight, a lucky number!"),
    898: ("El número 898 coincide con Calyrex, que cierra la Pokédex de Galar.",
          "Number 898 matches Calyrex, who closes the Galar Pokédex."),
    905: ("El número 905 coincide con Enamorus, el último Pokémon descubierto en Hisui.",
          "Number 905 matches Enamorus, the last Pokémon discovered in Hisui."),
    999: ("El número 999 coincide con Gimmighoul. Necesitarías 999 de sus monedas para evolucionarlo.",
          "Number 999 matches Gimmighoul. You'd need 999 of its coins to evolve it."),
    1000: ("¡1.000 cartas distintas! Gholdengo, el número 1000, está hecho de 1.000 monedas. Tú, de 1.000 cartas.",
           "1,000 different cards! Gholdengo, number 1000, is made of 1,000 coins. You, of 1,000 cards."),
    1025: ("El número 1025 coincide con Pecharunt, el último Pokémon de la Pokédex Nacional.",
           "Number 1025 matches Pecharunt, the last Pokémon in the National Pokédex."),
    1500: ("1.500 cartas. Si cada una fuera una moneda de Gimmighoul, podrías evolucionar uno y medio.",
           "1,500 cards. If each were a Gimmighoul coin, you could evolve one and a half."),
    1996: ("1996: el año en que nació Pokémon con Rojo y Verde, el 27 de febrero.",
           "1996: the year Pokémon was born with Red and Green, on February 27th."),
    1999: ("1999: el año en que el JCC Pokémon llegó en inglés con el Base Set.",
           "1999: the year the Pokémon TCG arrived in English with Base Set."),
    2500: ("2.500 cartas distintas. Hay tiendas de verdad con menos variedad que tu binder.",
           "2,500 different cards. Some real shops have less variety than your binder."),
    3000: ("3.000 cartas distintas. Si las pusieras en fila, medirían más de 260 metros.",
           "3,000 different cards. Lined up, they'd stretch over 260 meters."),
    4000: ("4.000 cartas distintas. Ya conoces más ilustraciones que muchos profesores Pokémon.",
           "4,000 different cards. You know more artworks than many Pokémon Professors."),
    5000: ("5.000 cartas distintas. Tu colección ya tiene historia propia.",
           "5,000 different cards. Your collection has a history of its own."),
    6000: ("6.000 cartas distintas. El Profesor Oak querría ver tu binder.",
           "6,000 different cards. Professor Oak would want to see your binder."),
    7000: ("7.000 cartas distintas. Pocos entrenadores han llegado tan lejos.",
           "7,000 different cards. Few trainers have come this far."),
    8000: ("8.000 cartas distintas. Esto ya no es una colección: es un museo.",
           "8,000 different cards. This isn't a collection anymore: it's a museum."),
}


def main():
    for f, idx in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for n, texts in FACTS.items():
            d[f"tcgshop.fact.{n}"] = texts[idx]
        d["tcgshop.fact.title"] = "¡%s cartas distintas!" if idx == 0 else "%s different cards!"
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
    print(len(FACTS), "datos curiosos")


if __name__ == "__main__":
    main()
