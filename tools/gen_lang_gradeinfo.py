#!/usr/bin/env python3
"""Texts for the Grade system (phone → CobbleGrade → How it works) and the term «Grado» in the grading texts.
They must match dev.tcgshop.pricing.GradeModel. Usage: python tools/gen_lang_gradeinfo.py"""
import json
import re
from pathlib import Path

R = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

ES = {
    "tcgshop.grade.tab.info": "Cómo funciona",
    "tcgshop.grade.odds_title": "Probabilidad de cada Grado",
    "tcgshop.grade.odds_note": "Esta tabla es el reparto base. El Grado 10 depende de la antigüedad de cada carta: 0,45·e^(-1,7A) (45% en una carta nueva, 30% con 6 años, 11% con 21 años); el resto de Grados se reparten en la misma proporción que la tabla. En sets lanzados hasta 2017 (impresión antigua: peor centrado y bordes) el 30% de las cartas cae justo en la frontera: 4.9, 5.0 o 5.1 (10% cada uno). En el 70% restante cualquier Grado de 5.0 o más, incluido el 10, sale con sólo el 0,5% de su probabilidad, así que pasar de 5.1 es casi imposible y muchas se quedan por debajo de 5.0 y pierden valor al graduarse. A la derecha, qué parte del salto hasta el Grado 10 se alcanza: ((Grado-1)/9)^3.",
    "tcgshop.gradeinfo.0": "#Qué es el Grado",
    "tcgshop.gradeinfo.1": "Al gradear una carta recibe un Grado de 1.0 a 10.0 (también decimales como 9.5 o 9.9). Su precio pasa a ser: precio sin Grado × Mg. Por debajo de Grado 5 pierde valor; desde Grado 5 lo gana según la carta: no hay multiplicadores fijos ni primas añadidas.",
    "tcgshop.gradeinfo.2": "#Multiplicadores",
    "tcgshop.gradeinfo.3": "Primero se calcula el máximo de esa carta, M10 = min(100, 1 + 99·Z^4), con Z = 0,45A + 0,55Q. Desde Grado 5: Mg = 1 + (M10 - 1)·((Grado - 1)/9)^3. Con Grado 4.9 o menos la carta pierde valor: Mg = 0,40 + 0,59·((Grado - 1)/3,9)^1,5 (Grado 1 = x0,40, Grado 3 = x0,62, Grado 4.9 = x0,99). Desde Grado 5 el salto es cúbico: Grado 5 sólo alcanza el 9% del salto, Grado 8 el 47%, Grado 9 el 70%, Grado 9.5 el 84% y Grado 10 el 100%. Ninguna carta pasa de x100 y casi ninguna llega.",
    "tcgshop.gradeinfo.4": "#Antigüedad (A): 45% de Z",
    "tcgshop.gradeinfo.5": "A = años desde el lanzamiento / 25, entre 0 y 1 (Base Set 1999 → A = 1; una carta de 2024 → A = 0,08). La antigüedad sólo cuenta aquí, no en la escasez. Las cartas viejas valen mucho más en Grado alto, pero también es más difícil sacarlas en Grado 10: probabilidad = 0,45·e^(-1,7A). Y si el set salió en 2017 o antes, el 30% de las veces el Grado es 4.9, 5.0 o 5.1; fuera de eso, sacar Grado 5.0 o más es casi imposible (0,5% de su probabilidad normal).",
    "tcgshop.gradeinfo.6": "#Calidad de demanda (Q): 55% de Z",
    "tcgshop.gradeinfo.7": "Q = 0,70·Pfinal + 0,15·S + 0,10·R + 0,05·B. En cartas sin Pokémon, Pfinal se sustituye por T (los pesos siguen sumando 1). R = rareza de impresión (Común 0, Doble rara 0,25, Ilustración rara 0,50, Ultra rara 0,60, Hiper rara 0,85, Ilustración especial 1; sin rareza 0,10). B = atractivo de la ilustración (Ilustración especial 1, Ilustración rara 0,85, Ultra rara 0,75, Hiper rara 0,55, Doble rara 0,35, resto 0,15).",
    "tcgshop.gradeinfo.8": "#Popularidad del Pokémon",
    "tcgshop.gradeinfo.9": "Se usa un ranking de 1.025 Pokémon (#1 Charizard). Se busca el Pokémon dentro del nombre de la carta (con prefijos como Alolan o Radiant y sufijos como ex, V o VMAX). Si salen varios (Tag Team), cuenta el más popular. P = ((1025 - rank)/1024)^2. Top 300: Pfinal = 0,65 + 0,35·P (nunca baja de 0,65). Resto: Pfinal = 0,65·((1025 - rank)/725)^2. Así el Top 300 tiene una ventaja clara, porque es lo que más se colecciona y gradea, pero fuera de él siguen existiendo diferencias. Si el Pokémon no está en el ranking se usa el valor neutro del Pokémon mediano (#513).",
    "tcgshop.gradeinfo.10": "#Escasez (S)",
    "tcgshop.gradeinfo.11": "S = 0,45·R + 0,55·E. E = escasez de la colección según el precio real de su sobre: E = ln(precio del sobre / 4,5) / ln(100), entre 0 y 1 (sobre de 4,5 $ → 0; de 450 $ o más → 1). No se inventan tiradas de impresión.",
    "tcgshop.gradeinfo.12": "#Cartas sin Pokémon (T)",
    "tcgshop.gradeinfo.13": "T = 0,35·C + 0,30·R + 0,20·S + 0,15·U. C = importancia de la categoría: Partidario 0,85, Objeto 0,70, Herramienta 0,55, Estadio 0,50, Energía especial 0,45, Energía básica 0,10 (desconocida 0,50). U = utilidad = 0,40·K + 0,30·F + 0,20·V + 0,10·H: K = uso competitivo (copias en los mazos meta del motor; la más usada = 1); F = efectividad del efecto y H = relevancia histórica = 0,5 (neutro, no hay datos fiables); V = versatilidad (cuántas veces se ha reimpreso esa carta).",
    "tcgshop.gradeinfo.14": "#Categorías",
    "tcgshop.gradeinfo.15": "Pokémon: representa un Pokémon; su demanda sale del ranking. Entrenador se divide en Partidario (efecto de apoyo fuerte, normalmente uno por turno), Objeto (acciones y efectos durante tu turno), Herramienta (se equipa a un Pokémon para darle un efecto extra) y Estadio (cambia las condiciones de la partida y puede ayudar o perjudicar a ambos jugadores). Energía: básica o especial, paga los costes de ataques y habilidades.",
    "tcgshop.gradeinfo.16": "#¿Por qué dos cartas de la misma rareza valen distinto en Grado 10?",
    "tcgshop.gradeinfo.17": "La rareza sólo pesa un 10% de Q (más su parte en S). Lo que más pesa es la popularidad (70% de Q) y la antigüedad (45% de Z). Además Z va elevado a la cuarta: pequeñas diferencias arriba se convierten en grandes diferencias de precio. Una Ilustración especial de Charizard puede multiplicar varias veces su precio en Grado 10, y la misma rareza de un Pokémon poco conocido de un set reciente apenas sube.",
    "tcgshop.gradeinfo.ex_pop_head": "#Ejemplos de popularidad",
    "tcgshop.gradeinfo.ex_mult_head": "#Ejemplos de multiplicadores (Grado → xMg)",
    "item.tcgshop.deodorant_spray.uses": "Usos: %s/%s",
    "tcgshop.binder.tip1": "Mantén el cursor ~1,5 s sobre una carta con ★ para marcarla como vista. Las cartas nuevas no desaparecen de «Nuevas» mientras tengas el binder abierto.",
}
EN = {
    "tcgshop.grade.tab.info": "How it works",
    "tcgshop.grade.odds_title": "Chance of each Grade",
    "tcgshop.grade.odds_note": "This table is the base spread. Grade 10 depends on each card's age: 0.45·e^(-1.7A) (45% on a new card, 30% at 6 years, 11% at 21 years); other Grades keep the table's proportions. In sets released up to 2017 (old printing: worse centering and edges) 30% of cards land right on the edge: 4.9, 5.0 or 5.1 (10% each). In the other 70% any Grade of 5.0 or higher, Grade 10 included, comes up with only 0.5% of its chance, so going above 5.1 is almost impossible and many land below 5.0 and lose value when graded. On the right, how much of the jump to Grade 10 is reached: ((Grade-1)/9)^3.",
    "tcgshop.gradeinfo.0": "#What a Grade is",
    "tcgshop.gradeinfo.1": "A graded card gets a Grade from 1.0 to 10.0 (decimals like 9.5 or 9.9 too). Its price becomes: ungraded price × Mg. Below Grade 5 it loses value; from Grade 5 it gains depending on the card: no fixed multipliers and no added premiums.",
    "tcgshop.gradeinfo.2": "#Multipliers",
    "tcgshop.gradeinfo.3": "First the card's maximum: M10 = min(100, 1 + 99·Z^4), with Z = 0.45A + 0.55Q. From Grade 5: Mg = 1 + (M10 - 1)·((Grade - 1)/9)^3. At Grade 4.9 or lower the card loses value: Mg = 0.40 + 0.59·((Grade - 1)/3.9)^1.5 (Grade 1 = x0.40, Grade 3 = x0.62, Grade 4.9 = x0.99). From Grade 5 the jump is cubic: Grade 5 reaches only 9% of it, Grade 8 47%, Grade 9 70%, Grade 9.5 84% and Grade 10 100%. No card goes above x100 and almost none get there.",
    "tcgshop.gradeinfo.4": "#Age (A): 45% of Z",
    "tcgshop.gradeinfo.5": "A = years since release / 25, between 0 and 1 (Base Set 1999 → A = 1; a 2024 card → A = 0.08). Age only counts here, not in scarcity. Old cards are worth far more at high Grades, but Grade 10 is also harder: chance = 0.45·e^(-1.7A). And if the set came out in 2017 or earlier, 30% of the time the Grade is 4.9, 5.0 or 5.1; otherwise Grade 5.0 or higher is almost impossible (0.5% of its normal chance).",
    "tcgshop.gradeinfo.6": "#Demand quality (Q): 55% of Z",
    "tcgshop.gradeinfo.7": "Q = 0.70·Pfinal + 0.15·S + 0.10·R + 0.05·B. For cards without a Pokémon, Pfinal is replaced by T (weights still add up to 1). R = print rarity (Common 0, Double rare 0.25, Illustration rare 0.50, Ultra rare 0.60, Hyper rare 0.85, Special illustration 1; no rarity 0.10). B = artwork appeal (Special illustration 1, Illustration rare 0.85, Ultra rare 0.75, Hyper rare 0.55, Double rare 0.35, others 0.15).",
    "tcgshop.gradeinfo.8": "#Pokémon popularity",
    "tcgshop.gradeinfo.9": "A ranking of 1,025 Pokémon is used (#1 Charizard). The Pokémon is found inside the card name (with prefixes like Alolan or Radiant and suffixes like ex, V or VMAX). If there are several (Tag Team), the most popular counts. P = ((1025 - rank)/1024)^2. Top 300: Pfinal = 0.65 + 0.35·P (never below 0.65). Others: Pfinal = 0.65·((1025 - rank)/725)^2. The Top 300 gets a clear edge because that is what is collected and graded the most, but differences remain outside it. A Pokémon missing from the ranking gets the neutral value of the median Pokémon (#513).",
    "tcgshop.gradeinfo.10": "#Scarcity (S)",
    "tcgshop.gradeinfo.11": "S = 0.45·R + 0.55·E. E = collection scarcity from the real price of its pack: E = ln(pack price / 4.5) / ln(100), between 0 and 1 ($4.5 pack → 0; $450 or more → 1). No print runs are made up.",
    "tcgshop.gradeinfo.12": "#Cards without a Pokémon (T)",
    "tcgshop.gradeinfo.13": "T = 0.35·C + 0.30·R + 0.20·S + 0.15·U. C = category importance: Supporter 0.85, Item 0.70, Tool 0.55, Stadium 0.50, Special Energy 0.45, Basic Energy 0.10 (unknown 0.50). U = utility = 0.40·K + 0.30·F + 0.20·V + 0.10·H: K = competitive use (copies in the engine's meta decks; most used = 1); F = effect strength and H = historical relevance = 0.5 (neutral, no reliable data); V = versatility (how often the card was reprinted).",
    "tcgshop.gradeinfo.14": "#Categories",
    "tcgshop.gradeinfo.15": "Pokémon: depicts a Pokémon; its demand comes from the ranking. Trainer splits into Supporter (strong support effect, usually one per turn), Item (actions and effects during your turn), Tool (attached to a Pokémon for an extra effect) and Stadium (changes the game conditions and can help or hurt both players). Energy: basic or special, pays for attacks and abilities.",
    "tcgshop.gradeinfo.16": "#Why can two cards of the same rarity differ at Grade 10?",
    "tcgshop.gradeinfo.17": "Rarity is only 10% of Q (plus its share of S). Popularity (70% of Q) and age (45% of Z) weigh the most. Z is also raised to the fourth power: small differences at the top become big price gaps. A Charizard special illustration can multiply its price several times at Grade 10, while the same rarity of an obscure Pokémon from a recent set barely moves.",
    "tcgshop.gradeinfo.ex_pop_head": "#Popularity examples",
    "tcgshop.gradeinfo.ex_mult_head": "#Multiplier examples (Grade → xMg)",
    "item.tcgshop.deodorant_spray.uses": "Uses: %s/%s",
    "tcgshop.binder.tip1": "Hold the cursor ~1.5 s over a ★ card to mark it as seen. New cards stay in the New tab while the binder is open.",
}
SWAP = [("NOTA", "GRADO"), ("Mejor nota", "Mejor Grado"), ("La nota", "El Grado"), ("la nota", "el Grado"), ("una nota", "un Grado"),
        ("Una nota", "Un Grado"), ("cada nota", "cada Grado"), ("notas", "Grados"), ("nota", "Grado")]


def es_fix(k, v):
    if not (k.startswith(("tcgshop.grade.", "tcgshop.slab.", "advancement.tcgshop.grade"))):
        return v
    for a, b in SWAP:
        v = re.sub(r"\b" + a + r"\b", b, v)
    return v


for f, d, fix in (("es_es.json", ES, True), ("en_us.json", EN, False)):
    p = R / f
    j = json.loads(p.read_text(encoding="utf-8"))
    if fix:
        j = {k: es_fix(k, v) for k, v in j.items()}
    j.update(d)
    p.write_text(json.dumps(j, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("ok")
