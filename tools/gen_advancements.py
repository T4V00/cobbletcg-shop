#!/usr/bin/env python3
"""Genera data/tcgshop/advancement/*.json y sus textos (es/en). Ejecutar tras editar ADVS."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
ADV = ROOT / "data/tcgshop/advancement"
LANG = ROOT / "assets/tcgshop/lang"

# id, padre, icono, marco, trigger ("impossible" = lo otorga el código; "tick" = raíz; o un ítem para inventory_changed),
# (título, descripción) en español, (título, descripción) en inglés
ADVS = [
    ("root", None, "tcgshop:general_binder", "task", "tick",
     ("CobbleTCG Shop", "Colecciona, abre sobres y monta tu propia tienda de cartas"),
     ("CobbleTCG Shop", "Collect, open packs and run your own card shop")),
    ("get_general_binder", "root", "tcgshop:general_binder", "task", "tcgshop:general_binder",
     ("Todo en un solo lugar", "Consigue un Binder General"), ("All in one place", "Get a General Binder")),
    ("first_binder", "get_general_binder", "tcgshop:general_binder", "task", "impossible",
     ("Hojeando la colección", "Abre un binder con al menos una carta"), ("Flipping through", "Open a binder with at least one card")),
    ("get_personal_binder", "root", "tcgshop:personal_binder", "task", "tcgshop:personal_binder",
     ("A mi manera", "Consigue un Personal Binder"), ("My way", "Get a Personal Binder")),
    ("customize_binder", "get_personal_binder", "tcgshop:personal_binder", "task", "impossible",
     ("Diseñador de interiores", "Cambia el estilo de tu Personal Binder"), ("Interior designer", "Change the style of your Personal Binder")),
    ("packs_10", "root", "cobbletcg:mew151/sealed_mew151", "task", "impossible",
     ("Enganchado", "Abre 10 sobres"), ("Hooked", "Open 10 packs")),
    ("packs_50", "packs_10", "cobbletcg:silvertempest/sealed_silvertempest", "task", "impossible",
     ("Coleccionista en serie", "Abre 50 sobres"), ("Serial collector", "Open 50 packs")),
    ("packs_250", "packs_50", "cobbletcg:bundle_mew151", "goal", "impossible",
     ("Caja tras caja", "Abre 250 sobres"), ("Box after box", "Open 250 packs")),
    ("packs_1000", "packs_250", "cobbletcg:bundle_prismaticevolution", "challenge", "impossible",
     ("Rasgador legendario", "Abre 1.000 sobres"), ("Legendary ripper", "Open 1,000 packs")),
    ("multi_open_10", "packs_10", "cobbletcg:bundle_silvertempest", "goal", "impossible",
     ("¡De un tirón!", "Rasga 10 sobres a la vez"), ("All at once!", "Rip 10 packs at the same time")),
    ("semigod_pack", "packs_10", "cobbletcg:mew151/riped3_mew151", "goal", "impossible",
     ("Bendecido", "Abre un sobre Semi-Dios"), ("Blessed", "Open a Semi-God pack")),
    ("god_pack", "semigod_pack", "cobbletcg:mew151/riped4_mew151", "challenge", "impossible",
     ("Tocado por Arceus", "Abre un sobre Dios"), ("Touched by Arceus", "Open a God pack")),
    ("ruined_pack", "packs_10", "minecraft:shears", "task", "impossible",
     ("Manos de mantequilla", "Rompe las cartas al abrir mal un sobre"), ("Butterfingers", "Ruin the cards by opening a pack badly")),
    ("ruined_10", "ruined_pack", "minecraft:paper", "goal", "impossible",
     ("Trituradora humana", "Arruina 10 sobres. ¿Lo haces a propósito?"), ("Human shredder", "Ruin 10 packs. On purpose?")),
    ("pull_double_rare", "root", "minecraft:lapis_lazuli", "task", "impossible",
     ("¡Brilla!", "Saca una Doble Rara"), ("It shines!", "Pull a Double Rare")),
    ("pull_ultra_rare", "pull_double_rare", "minecraft:amethyst_shard", "task", "impossible",
     ("Ultra", "Saca una Ultra Rara"), ("Ultra", "Pull an Ultra Rare")),
    ("pull_illustration_rare", "pull_ultra_rare", "minecraft:painting", "goal", "impossible",
     ("Obra de arte", "Saca una Ilustración Rara"), ("Work of art", "Pull an Illustration Rare")),
    ("pull_special_illustration", "pull_illustration_rare", "minecraft:glow_item_frame", "challenge", "impossible",
     ("Pieza de museo", "Saca una Ilustración Especial Rara"), ("Museum piece", "Pull a Special Illustration Rare")),
    ("pull_hyper_rare", "pull_illustration_rare", "minecraft:gold_ingot", "challenge", "impossible",
     ("Oro puro", "Saca una Hiper Rara"), ("Pure gold", "Pull a Hyper Rare")),
    ("jackpot", "pull_double_rare", "minecraft:gold_block", "challenge", "impossible",
     ("¡Premio gordo!", "Saca una carta que valga 100.000 ₽ o más"), ("Jackpot!", "Pull a card worth 100,000 ₽ or more")),
    ("unique_25", "first_binder", "minecraft:book", "task", "impossible",
     ("Pequeña colección", "Ten 25 cartas distintas en tus binders"), ("Small collection", "Have 25 different cards in your binders")),
    ("unique_100", "unique_25", "minecraft:writable_book", "task", "impossible",
     ("Cien y contando", "Ten 100 cartas distintas en tus binders"), ("A hundred and counting", "Have 100 different cards in your binders")),
    ("unique_250", "unique_100", "minecraft:bookshelf", "goal", "impossible",
     ("Biblioteca", "Ten 250 cartas distintas"), ("Library", "Have 250 different cards")),
    ("unique_500", "unique_250", "minecraft:chiseled_bookshelf", "goal", "impossible",
     ("Archivo Pokémon", "Ten 500 cartas distintas"), ("Pokémon archive", "Have 500 different cards")),
    ("unique_1000", "unique_500", "minecraft:enchanted_book", "challenge", "impossible",
     ("Mil caras", "Ten 1.000 cartas distintas"), ("A thousand faces", "Have 1,000 different cards")),
    ("unique_2500", "unique_1000", "minecraft:lectern", "challenge", "impossible",
     ("Enciclopedia viviente", "Ten 2.500 cartas distintas"), ("Living encyclopedia", "Have 2,500 different cards")),
    ("unique_5000", "unique_2500", "minecraft:nether_star", "challenge", "impossible",
     ("Profesor de cartas", "Ten 5.000 cartas distintas"), ("Card Professor", "Have 5,000 different cards")),
    ("set_complete", "unique_100", "cobbletcg:album_mew_151", "challenge", "impossible",
     ("Álbum completo", "Ten todas las cartas de un set en tus binders"), ("Complete album", "Have every card of a set in your binders")),
    ("value_10k", "first_binder", "minecraft:emerald", "task", "impossible",
     ("Ahorros", "Tu Binder General vale 10.000 ₽"), ("Savings", "Your General Binder is worth 10,000 ₽")),
    ("value_100k", "value_10k", "minecraft:emerald_block", "goal", "impossible",
     ("Inversor", "Tu Binder General vale 100.000 ₽"), ("Investor", "Your General Binder is worth 100,000 ₽")),
    ("value_1m", "value_100k", "minecraft:diamond_block", "challenge", "impossible",
     ("Millonario de cartón", "Tu Binder General vale 1.000.000 ₽"), ("Cardboard millionaire", "Your General Binder is worth 1,000,000 ₽")),
    ("scalper_meet", "root", "tcgshop:scalper_spawn_egg", "task", "impossible",
     ("Psst... ¿un sobre?", "Te ha abordado un Scalper"), ("Psst... a pack?", "A Scalper approached you")),
    ("scalper_buy", "scalper_meet", "minecraft:gold_nugget", "task", "impossible",
     ("Precio de reventa", "Cómprale un sobre a un Scalper (¿en serio?)"), ("Resale price", "Buy a pack from a Scalper (really?)")),
    ("scalper_robbed", "scalper_meet", "minecraft:barrier", "task", "impossible",
     ("¡Al ladrón!", "Un Scalper te ha robado"), ("Stop, thief!", "A Scalper robbed you")),
    ("scalper_defeat", "scalper_meet", "minecraft:diamond_sword", "goal", "impossible",
     ("Justicia para los coleccionistas", "Derrota a un Scalper"), ("Justice for collectors", "Defeat a Scalper")),
    ("scalper_defeat_10", "scalper_defeat", "minecraft:netherite_sword", "challenge", "impossible",
     ("Azote de la reventa", "Derrota a 10 Scalpers"), ("Scourge of scalpers", "Defeat 10 Scalpers")),
]


# ---- títulos por Pokémon -----------------------------------------------------
TIERS = [("fan", 5, "Fan", "Fan", "task"), ("enthusiast", 15, "Entusiasta", "Enthusiast", "task"),
         ("obsessed", 40, "Obsesionado", "Obsessed", "goal"), ("demented", 100, "Totalmente demente", "Totally demented", "challenge")]
FEATURED = ["pikachu", "charizard", "eevee", "mewtwo", "mew", "gengar", "umbreon", "rayquaza", "lucario", "greninja", "snorlax", "gardevoir"]


def species_icons():
    f = ROOT / "data/tcgshop/tcgshop_species/cards.json"
    m = json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}
    icons = {}
    for cid, sp in m.items():
        icons.setdefault(sp, cid)
    return icons


def title_advs():
    out = []
    prev = "first_binder"
    for key, n, es, en, frame in TIERS:
        out.append((f"title_{key}", prev, "minecraft:name_tag", frame, "impossible",
                    (f"{es} de alguien", f"Saca {n} cartas del mismo Pokémon y consigue un título"),
                    (f"{en} of someone", f"Pull {n} cards of the same Pokémon and earn a title")))
        prev = f"title_{key}"
    icons = species_icons()
    for sp in FEATURED:
        name = sp.capitalize()
        parent = "title_fan"
        for key, n, es, en, frame in TIERS:
            out.append((f"species_{sp}_{key}", parent, icons.get(sp, "minecraft:name_tag"), frame, "impossible",
                        (f"{es}: {name}", f"Saca {n} cartas de {name}"), (f"{en}: {name}", f"Pull {n} {name} cards")))
            parent = f"species_{sp}_{key}"
    return out


ADVS = ADVS + title_advs()


SHOP_ADVS = [
    ("buy_box", "root", "tcgshop:booster_box", "task", "impossible",
     ("Al por mayor", "Compra una caja de sobres"), ("Wholesale", "Buy a booster box")),
    ("open_shop", "buy_box", "tcgshop:shop_core_wood", "task", "impossible",
     ("¡Abierto al público!", "Coloca tu primer bloque de tienda"), ("Open for business!", "Place your first shop block")),
    ("first_sale", "open_shop", "tcgshop:cash_register", "task", "impossible",
     ("Primer cliente", "Vende algo en tu tienda"), ("First customer", "Sell something in your shop")),
    ("sales_100", "first_sale", "tcgshop:display_case", "goal", "impossible",
     ("Negocio próspero", "Haz 100 ventas en una tienda"), ("Thriving business", "Make 100 sales in one shop")),
    ("shop_netherite", "open_shop", "tcgshop:shop_core_netherite", "challenge", "impossible",
     ("Imperio del cartón", "Coloca una Expansión de Tienda de Netherita (100×100)"), ("Cardboard empire", "Place a Netherite Shop Expansion (100×100)")),
    ("customer", "root", "minecraft:emerald", "task", "impossible",
     ("Comprador compulsivo", "Compra algo en la tienda de otro jugador"), ("Shopaholic", "Buy something in another player's shop")),
    ("hire_cashier", "first_sale", "tcgshop:cash_register", "task", "impossible",
     ("¡Siguiente, por favor!", "Contrata a un cajero para tu tienda"), ("Next, please!", "Hire a cashier for your shop")),
    ("hire_guard", "open_shop", "tcgshop:theft_detector", "task", "impossible",
     ("Seguridad privada", "Contrata a un guardia de seguridad"), ("Private security", "Hire a security guard")),
    ("catch_thief", "hire_guard", "minecraft:iron_bars", "task", "impossible",
     ("¡Alto, ladrón!", "Tu guardia detiene a un ladrón"), ("Stop, thief!", "Your guard detains a thief")),
    ("catch_thief_10", "catch_thief", "minecraft:chain", "goal", "impossible",
     ("Tolerancia cero", "Detén a 10 ladrones en una tienda"), ("Zero tolerance", "Detain 10 thieves in one shop")),
    ("reputation_50", "open_shop", "minecraft:glow_berries", "task", "impossible",
     ("Se corre la voz", "Alcanza 50 de reputación"), ("Word gets around", "Reach 50 reputation")),
    ("reputation_80", "reputation_50", "minecraft:amethyst_shard", "goal", "impossible",
     ("Tienda de referencia", "Alcanza 80 de reputación"), ("Go-to shop", "Reach 80 reputation")),
    ("reputation_100", "reputation_80", "minecraft:nether_star", "challenge", "impossible",
     ("Leyenda del barrio", "Alcanza 100 de reputación"), ("Neighbourhood legend", "Reach 100 reputation")),
    ("first_deal", "first_sale", "minecraft:paper", "task", "impossible",
     ("Hablando se entiende la gente", "Acepta un trato con un NPC"), ("Let's talk business", "Accept a deal from an NPC")),
    ("deal_bargain", "first_deal", "minecraft:gold_nugget", "goal", "impossible",
     ("Ojo para el negocio", "Acepta un trato al menos un 30% mejor que el mercado"), ("Eye for business", "Accept a deal at least 30% better than market")),
    ("deal_above_market", "first_deal", "minecraft:gold_ingot", "task", "impossible",
     ("El cliente siempre paga", "Vende a un NPC por encima del valor de mercado"), ("The customer always pays", "Sell to an NPC above market value")),
    ("whale_customer", "first_sale", "minecraft:heart_of_the_sea", "goal", "impossible",
     ("Ha llegado una ballena", "Véndele algo a un cliente VIP"), ("A whale appears", "Sell something to a VIP customer")),
    ("sales_1000", "sales_100", "minecraft:emerald_block", "challenge", "impossible",
     ("Cadena de tiendas", "Haz 1000 ventas en una tienda"), ("Chain store", "Make 1000 sales in one shop")),
    ("revenue_1m", "first_sale", "minecraft:gold_block", "challenge", "impossible",
     ("Millonario del cartón", "Factura 1.000.000 en una tienda"), ("Cardboard millionaire", "Earn 1,000,000 revenue in one shop")),
    ("roulette_win", "root", "tcgshop:roulette", "task", "impossible",
     ("¡Rojo, el veintisiete!", "Gana una tirada en la Ruleta Poké"), ("Red twenty-seven!", "Win a spin at the Poké Roulette")),
    ("roulette_high_roller", "roulette_win", "minecraft:diamond_block", "challenge", "impossible",
     ("Todo al rojo", "Gana una apuesta de 1.000.000 o más en la ruleta"), ("All on red", "Win a bet of 1,000,000 or more at the roulette")),
    ("trade_first", "root", "minecraft:emerald", "task", "impossible",
     ("¡Te la cambio!", "Intercambia una carta con otro jugador"), ("Wanna trade?", "Trade a card with another player")),
    ("trade_10", "trade_first", "minecraft:lead", "goal", "impossible",
     ("Mercader de patio", "Completa 10 intercambios de cartas"), ("Schoolyard trader", "Complete 10 card trades")),
    ("battle_scalper", "scalper_meet", "minecraft:target", "task", "impossible",
     ("¡Te reto!", "Reta a un Scalper a un combate Pokémon"), ("I challenge you!", "Challenge a Scalper to a Pokémon battle")),
    ("battle_thief", "catch_thief", "minecraft:spyglass", "task", "impossible",
     ("Justicia Pokémon", "Reta a un ladrón de tu tienda a un combate Pokémon"), ("Pokémon justice", "Challenge a shop thief to a Pokémon battle")),
    ("battle_win", "battle_scalper", "minecraft:totem_of_undying", "goal", "impossible",
     ("Entrenador justiciero", "Gana un combate Pokémon contra un Scalper o un ladrón"), ("Vigilante trainer", "Win a Pokémon battle against a Scalper or a thief")),
    ("spray_customer", "first_sale", "tcgshop:deodorant_spray", "task", "impossible",
     ("Higiene ante todo", "Echa desodorante a un cliente que huele mal"), ("Hygiene first", "Spray a smelly customer with deodorant")),
    ("duel_play", "root", "tcgshop:play_table", "task", "impossible",
     ("A la mesa", "Juega una partida del modo competitivo TCG"), ("At the table", "Play a competitive TCG match")),
    ("duel_win", "duel_play", "minecraft:iron_sword", "task", "impossible",
     ("Primera victoria", "Gana una partida de TCG"), ("First win", "Win a TCG match")),
    ("duel_house_win", "duel_win", "minecraft:gold_ingot", "task", "impossible",
     ("La banca pierde", "Gana una partida contra la casa"), ("The house loses", "Beat the house in a TCG match")),
    ("duel_ranked_win", "duel_win", "minecraft:diamond_sword", "goal", "impossible",
     ("Aspirante", "Gana una partida de liga contra otro jugador"), ("Contender", "Win a League match against another player")),
    ("duel_gold", "duel_ranked_win", "minecraft:gold_block", "goal", "impossible",
     ("Liga Oro... ¡Platino!", "Llega al rango Platino en la Liga TCG"), ("Platinum League", "Reach Platinum rank in the TCG League")),
    ("duel_master", "duel_gold", "minecraft:dragon_egg", "challenge", "impossible",
     ("Maestro TCG", "Llega al rango Maestro en la Liga TCG"), ("TCG Master", "Reach Master rank in the TCG League")),
    ("grade_send", "root", "minecraft:item_frame", "task", "impossible",
     ("Rumbo a CobbleGrade", "Envía cartas a gradear desde el teléfono"), ("Off to CobbleGrade", "Send cards for grading from your phone")),
    ("grade_95", "grade_send", "minecraft:glow_item_frame", "goal", "impossible",
     ("Casi perfecta", "Consigue una nota de 9.5 o más"), ("Nearly perfect", "Get a grade of 9.5 or higher")),
    ("grade_10", "grade_95", "minecraft:nether_star", "challenge", "impossible",
     ("GEM MINT 10", "Consigue un 10 perfecto en CobbleGrade"), ("GEM MINT 10", "Get a perfect 10 from CobbleGrade")),
    ("grade_bad", "grade_send", "minecraft:dead_bush", "task", "impossible",
     ("¿Esto es una carta?", "Recibe una nota de 2.0 o menos"), ("Is this even a card?", "Receive a grade of 2.0 or lower")),
    ("npc_trade", "first_deal", "minecraft:emerald", "task", "impossible",
     ("Trueque de mostrador", "Acepta un intercambio con un coleccionista en tu tienda"), ("Counter trade", "Accept a trade from a collector in your shop")),
]

ADVS = ADVS + SHOP_ADVS


def main():
    ADV.mkdir(parents=True, exist_ok=True)
    es, en = {}, {}
    for aid, parent, icon, frame, trig, tes, ten in ADVS:
        if trig == "tick":
            crit = {"trigger": "minecraft:tick"}
        elif trig == "impossible":
            crit = {"trigger": "minecraft:impossible"}
        else:
            crit = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": trig}]}}
        disp = {
            "icon": {"id": icon},
            "title": {"translate": f"advancement.tcgshop.{aid}.title"},
            "description": {"translate": f"advancement.tcgshop.{aid}.desc"},
            "frame": frame,
            "show_toast": aid != "root",
            "announce_to_chat": frame == "challenge",
            "hidden": False,
        }
        if aid == "root":
            disp["background"] = "minecraft:textures/block/blue_concrete.png"
        d = {"display": disp, "criteria": {"done": crit}}
        if parent:
            d["parent"] = f"tcgshop:{parent}"
        (ADV / f"{aid}.json").write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
        es[f"advancement.tcgshop.{aid}.title"], es[f"advancement.tcgshop.{aid}.desc"] = tes
        en[f"advancement.tcgshop.{aid}.title"], en[f"advancement.tcgshop.{aid}.desc"] = ten
    for f, add in (("es_es.json", es), ("en_us.json", en)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        d.update(add)
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"{len(ADVS)} logros generados")


if __name__ == "__main__":
    main()
