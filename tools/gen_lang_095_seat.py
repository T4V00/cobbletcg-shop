#!/usr/bin/env python3
"""0.9.5 · Seats: customers sit for a while on chairs/sofas/benches (CobbleFurnies, Cozyhome...) and stop to
look at furniture and plushies (ShopSeats / CustomerEntity). Merges the keys into es_es / en_us (idempotent).
Usage: python -I -X utf8 tools/gen_lang_095_seat.py"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

# category -> [(Spanish, English), ...]  ->  tcgshop.seat.<cat>.<n>
# (counts must match ShopSeats.*_LINES)
LINES = {
    "sit": [  # sits down without having bought anything (just looking)
        ("Qué buena tienda para descansar un rato...", "What a nice shop to rest in for a bit..."),
        ("Uf, mis pies lo agradecen.", "Phew, my feet are grateful."),
        ("Me siento un momento y luego sigo.", "I'll sit for a moment and then carry on."),
        ("Qué silla más cómoda, ¿dónde la habrán comprado?", "What a comfy chair, I wonder where they got it?"),
        ("Aquí se está de maravilla.", "It's lovely in here."),
        ("Un descansito antes de seguir paseando.", "A little break before I keep walking."),
        ("Desde aquí se ven muy bien las vitrinas.", "You can see the display cases really well from here."),
        ("Hoy sólo vengo a mirar... y a sentarme.", "Today I'm just looking... and sitting."),
    ],
    "sit_buyer": [  # sits down after paying
        ("Déjame admirar mi compra un momento.", "Let me admire my purchase for a moment."),
        ("¡Por fin es mío! Voy a mirarlo con calma.", "Finally mine! Let me take a good look."),
        ("Un descanso antes de volver a casa.", "A short rest before heading home."),
        ("Qué ganas tenía de conseguirlo...", "I've wanted this for so long..."),
        ("¿Lo abro aquí? No, mejor en casa.", "Should I open it here? No, better at home."),
        ("Buena compra. Me lo he ganado.", "Good buy. I've earned it."),
    ],
    "stand": [  # stands up to leave
        ("Bueno, ya va siendo hora.", "Well, it's about time."),
        ("¡Gracias por la silla!", "Thanks for the seat!"),
        ("Volveré a descansar otro día.", "I'll come back to rest another day."),
        ("Ha estado bien. ¡Hasta luego!", "That was nice. See you!"),
        ("Venga, que se hace tarde.", "Come on, it's getting late."),
        ("Como nuevo. ¡Adiós!", "Good as new. Bye!"),
    ],
    "talk": [  # you talk to them while they're seated
        ("Estoy descansando un poco, ¿te importa?", "I'm resting a little, do you mind?"),
        ("Esta tienda es muy acogedora.", "This shop is really cozy."),
        ("Sólo estoy mirando, de verdad.", "I'm just looking, really."),
        ("Qué tranquilidad se respira aquí.", "It's so peaceful in here."),
        ("¿Estas sillas también se venden?", "Are these chairs for sale too?"),
    ],
    "startled": [  # jumps up suddenly (alarm, thief, monster...)
        ("¡¿Qué ha sido eso?!", "What was that?!"),
        ("¡Me voy de aquí!", "I'm out of here!"),
        ("Vaya, se acabó el descanso.", "Well, so much for my break."),
        ("¡Socorro! ¡Que alguien haga algo!", "Help! Somebody do something!"),
    ],
    "furniture": [  # stops to look at a piece of furniture
        ("Qué mueble más bonito.", "What a lovely piece of furniture."),
        ("Me encantaría tener uno así en casa.", "I'd love to have one like this at home."),
        ("Esto le da un toque muy acogedor.", "This gives the place a really cozy touch."),
        ("¿Estará a la venta?", "I wonder if it's for sale?"),
        ("Qué buen gusto tiene el dueño.", "The owner has great taste."),
        ("Mira qué detalle.", "Look at the detail on this."),
        ("Esta tienda está decorada con mucho cariño.", "This shop is decorated with a lot of love."),
    ],
    "plush": [  # stops to look at a plush
        ("¡Qué peluche más mono!", "What a cute plush!"),
        ("¡Quiero uno igual!", "I want one just like it!"),
        ("Aww, mira qué carita.", "Aww, look at that little face."),
        ("¿Se puede achuchar?", "Can I give it a squeeze?"),
        ("A mi hermano pequeño le encantaría.", "My little brother would love this."),
        ("¡Es adorable!", "It's adorable!"),
        ("Me quedaría aquí mirándolo todo el día.", "I could stare at it all day."),
    ],
}

KEYS = {}
for cat, lines in LINES.items():
    for i, (es, en) in enumerate(lines):
        KEYS[f"tcgshop.seat.{cat}.{i}"] = (es, en)


def merge(file_name: str, idx: int) -> None:
    path = LANG / file_name
    data = json.loads(path.read_text(encoding="utf-8"))
    for k, v in KEYS.items():
        data[k] = v[idx]
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    merge("es_es.json", 0)
    merge("en_us.json", 1)
    print(f"{len(KEYS)} keys merged into es_es.json and en_us.json")
