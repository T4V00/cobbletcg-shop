#!/usr/bin/env python3
"""1.1 texts (second batch), area "customers": simpler haggling window, generous customers (greed punishment and the
charm options: joke, compliment, story), Persuade, luxury statue remarks, the decoration liking (goggles bar and decor
tooltips), Scalpers visiting shops, and 1000+ new NPC lines (tools/npc_lines_v4_*.py) appended to the existing
tcgshop.npc.<category>.<n> line system (NpcLines.COUNTS is updated to match).
Code: entity/CustomerEntity.java, client/screen/DealScreen.java, shop/Charisma.java, shop/DecorScore.java,
client/DecorTooltip.java, entity/ScalperEntity.java, entity/NpcLines.java.

Usage: python tools/gen_lang_12_customers.py  (idempotent: lines already present are not added twice)
"""
import importlib
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LANG = HERE.parent / "common/src/main/resources/assets/tcgshop/lang"
NPC_JAVA = HERE.parent / "common/src/main/java/dev/tcgshop/entity/NpcLines.java"
GEN_NPC = HERE / "gen_npc_lines.py"
MODULES = ("npc_lines_v4_a", "npc_lines_v4_b", "npc_lines_v4_c", "npc_lines_v4_d", "npc_lines_v4_e", "npc_lines_v4_f")

T = {
    # ------------------------------------------------------------------ simpler haggling window
    "tcgshop.haggle.hint_simple": ("Elige tu precio y pulsa Ofrecer. Aceptar cierra al precio de la otra persona.",
                                   "Set your price, then press Offer. Accept closes at their price."),
    "tcgshop.haggle.hint_generous": ("Ya te ofrece un buen trato: si regateas, se va. Mejor encántalo.",
                                     "They're already giving you a good deal: haggle and they leave. Charm them instead."),
    "tcgshop.haggle.generous_buy": ("¡Gran oferta! Ya paga tu precio o más. No regatees: encántalo y deja más propina.",
                                    "Great offer! Already at or above your price. Don't haggle: charm them for a bigger tip."),
    "tcgshop.haggle.generous_sell": ("¡Buen precio! Ya está por debajo del mercado. No regatees: encántalo y te lo deja más barato.",
                                     "Great price! Already below market. Don't haggle: charm them for a discount."),
    "tcgshop.haggle.charms_left": ("Intentos: %s", "Tries left: %s"),
    "tcgshop.haggle.a.persuade": ("✦ Convencer · %s", "✦ Persuade · %s"),
    "tcgshop.haggle.a.joke": ("☺ Chiste · %s", "☺ Joke · %s"),
    "tcgshop.haggle.a.compliment": ("♥ Halago · %s", "♥ Compliment · %s"),
    "tcgshop.haggle.a.story": ("✎ Historia · %s", "✎ Story · %s"),
    "tcgshop.haggle.d.accept": ("Cierra el trato a su precio", "Close the deal at their price"),
    "tcgshop.haggle.d.counter_sell": ("Pide el precio de arriba", "Ask for the price above"),
    "tcgshop.haggle.d.counter_buy": ("Ofrece el precio de arriba", "Offer the price above"),
    "tcgshop.haggle.d.persuade": ("Tu carisma defiende tu precio", "Your charisma backs your price"),
    "tcgshop.haggle.d.no": ("Despídete con educación", "Say no politely"),
    "tcgshop.haggle.d.joke": ("Mucha propina, arriesgado", "Big tip, risky"),
    "tcgshop.haggle.d.compliment": ("Poca propina, seguro", "Small tip, safe"),
    "tcgshop.haggle.d.story": ("Propina media", "Medium tip"),
    "tcgshop.haggle.d.joke_sell": ("Gran descuento, arriesgado", "Big discount, risky"),
    "tcgshop.haggle.d.compliment_sell": ("Poco descuento, seguro", "Small discount, safe"),
    "tcgshop.haggle.d.story_sell": ("Descuento medio", "Medium discount"),
    "tcgshop.haggle.d.used": ("Ya lo usaste", "Already used"),
    "tcgshop.haggle.d.no_charms": ("No quedan intentos", "No tries left"),
    "tcgshop.haggle.tip.persuade": ("Tu carisma elige el mejor argumento para tu precio. Con más carisma acierta más; si falla, puede salir mal.",
                                    "Your charisma picks the best argument for your price. More charisma = more often right; a miss can backfire."),
    "tcgshop.haggle.tip.joke": ("Cuenta un chiste. Si le hace gracia, sube mucho la propina; si no, baja un poco. Depende de tu carisma y de su forma de ser.",
                                "Tell a joke. If it lands, the tip goes up a lot; if not, it drops a little. Depends on your charisma and their personality."),
    "tcgshop.haggle.tip.compliment": ("Hazle un cumplido. Casi siempre funciona, pero da poca propina. A los negociadores no les gusta la adulación.",
                                      "Pay them a compliment. Usually works but gives a small tip. Negotiators dislike flattery."),
    "tcgshop.haggle.tip.story": ("Cuéntale una historia de la tienda. A coleccionistas y expertos les encanta; si aburre, pierde paciencia.",
                                 "Tell a shop story. Collectors and experts love it; if it bores them, they lose patience."),
    "tcgshop.charm.exchange": ("Tú: «%s» — %s", "You: “%s” — %s"),
    "tcgshop.charm.tip_now": ("(Propina: %s)", "(Tip: %s)"),
    "tcgshop.review.deal_greedy": ("Quise ser generoso y me regatearon igual", "I offered generously and they still haggled"),

    # ------------------------------------------------------------------ statues, decoration, scalpers
    "tcgshop.lux.tip_customers": ("En tu tienda: +%s clientes y +%s de ganas de comprar (cuenta la mejor estatua)",
                                  "In your shop: +%s customers and +%s buy chance (the best statue counts)"),
    "tcgshop.statue.hint": ("Consejo: una estatua de lujo (Vendedor de Muebles) atrae clientes y les hace comprar más.",
                            "Tip: a luxury statue (Furniture Vendor) attracts customers and makes them buy more."),
    "tcgshop.decor.tooltip": ("✿ Decoración +%s", "✿ Decoration +%s"),
    "tcgshop.decor.tooltip_shop": ("  ≈ %s de decoración en tu tienda (ahora %s)", "  ≈ %s decoration in your shop (now %s)"),
    "tcgshop.decor.tooltip_empty": ("  ≈ %s de decoración en una tienda sin decorar", "  ≈ %s decoration in an undecorated shop"),
    "tcgshop.scalper.shop_arrived": ("Un revendedor entró a tu tienda... y viene directo hacia ti.",
                                     "A Scalper walked into your shop... and he's heading straight for you."),
    "tcgshop.scalper.shop_rejected": ("Tú te lo pierdes. Hay otras tiendas con dueños más listos.",
                                      "Your loss. Other shops have smarter owners."),
    "tcgshop.scalper.shop_ignored": ("¿Me ignoras en tu propia tienda? Ya me voy, ya me voy.",
                                     "Ignoring me in your own shop? Fine, I'm leaving."),
    "tcgshop.scalper.shop_no_money": ("¿Sin plata y con tienda? Vuelvo cuando vendas algo.",
                                      "Broke with a whole shop? I'll come back when you've sold something."),
}

# Review texts for "deal_greedy" (stars 1..5 x 8 variants: ShopZones.Review.textKey)
GREEDY = {
    1: [("Horrible. Ofrecí más de lo que pedían y aun así me regatearon.", "Horrible. I offered more than they asked and they still haggled."),
        ("Pésimo trato. Mi generosidad no valió nada.", "Terrible service. My generosity meant nothing."),
        ("Avaros. Les ofrecí de más y quisieron más todavía.", "Greedy. I offered extra and they wanted even more."),
        ("Nunca más. Me trataron como cajero automático.", "Never again. They treated me like an ATM."),
        ("Una falta de respeto total con un buen cliente.", "Total disrespect toward a good customer."),
        ("Ofrecí con el corazón y me exprimieron. Cero estrellas si pudiera.", "I offered from the heart and got squeezed. Zero stars if I could."),
        ("Codicia pura. Me fui sin comprar.", "Pure greed. I left without buying."),
        ("La peor experiencia de regateo de mi vida.", "The worst haggling experience of my life."),
        ],
    2: [("Les ofrecí un buen precio y quisieron más. Me fui molesto.", "I offered a good price and they wanted more. I left annoyed."),
        ("Mal detalle regatear a quien ya paga de más.", "Bad move haggling with someone already overpaying."),
        ("Bonita tienda, dueño ambicioso.", "Nice shop, greedy owner."),
        ("Quise ser generoso y me salió caro el gesto.", "I tried to be generous and it backfired."),
        ("No vuelvo pronto. La codicia espanta clientes.", "Not coming back soon. Greed scares customers away."),
        ("Regatear hacia arriba no es buena idea, señor dueño.", "Haggling upward is not a good idea, dear owner."),
        ("Me ofendió que pidieran más. Una pena.", "I was offended they asked for more. A shame."),
        ("Tienen buenas cartas, pero poca vergüenza.", "Good cards, but no shame."),
        ],
    3: [("Ofrecí bien y me pidieron más. Raro, pero sin drama.", "I offered well and they asked for more. Odd, but no drama."),
        ("Un poco ambiciosos con los precios. Normal.", "A bit greedy with prices. Average."),
        ("Me fui sin comprar, pero sin enojo.", "Left without buying, but not angry."),
        ("Les faltó tacto cuando ya les ofrecía de más.", "They lacked tact when I was already overpaying."),
        ("Regular: buena tienda, mal momento.", "Average: good shop, bad moment."),
        ("No era para tanto, pero no compré.", "Not a big deal, but I didn't buy."),
        ("Pidieron más de la cuenta. Quizás otro día.", "They asked for too much. Maybe another day."),
        ("Ni bien ni mal. Un regateo innecesario.", "Neither good nor bad. An unnecessary haggle."),
        ],
    4: [("Buena tienda, aunque me regatearon cuando ya pagaba bien.", "Good shop, though they haggled when I was already paying well."),
        ("Me gustó todo menos el regateo final.", "Liked everything except the last haggle."),
        ("Casi perfecta, pero que no exprima a los buenos clientes.", "Almost perfect, but don't squeeze good customers."),
        ("Ambiente genial; el dueño, un poquito ambicioso.", "Great atmosphere; the owner, a little greedy."),
        ("Volveré, pero esta vez con mi oferta exacta.", "I'll be back, but with my exact offer this time."),
        ("Muy bonita, solo faltó un poco de tacto.", "Very nice, just lacked a bit of tact."),
        ("Me cayó bien igual. Buen surtido.", "I still liked it. Good selection."),
        ("Buena experiencia, salvo ese detalle.", "Good experience, except for that detail."),
        ],
    5: [("Excelente tienda; hasta el regateo fue divertido.", "Excellent shop; even the haggling was fun."),
        ("Me encantó todo, aunque el dueño es un tiburón para negociar.", "Loved everything, though the owner is a shark at haggling."),
        ("Cinco estrellas, y la próxima no ofrezco de más. Jaja.", "Five stars, and next time I won't overpay. Haha."),
        ("Una tienda de diez, con un dueño muy listo.", "A ten-out-of-ten shop, with a very sharp owner."),
        ("Perfecta. El regateo me sacó una sonrisa.", "Perfect. The haggling made me smile."),
        ("Lo mejor del barrio, aunque negocian duro.", "Best in the neighborhood, though they bargain hard."),
        ("Me fui sin la carta, pero con ganas de volver.", "Left without the card, but eager to come back."),
        ("Increíble lugar, volveré con más paciencia.", "Amazing place, I'll come back with more patience."),
        ],
}


def load_lines():
    sys.path.insert(0, str(HERE))
    merged = {}
    for name in MODULES:
        mod = importlib.import_module(name)
        for cat, lines in mod.EXTRA.items():
            merged.setdefault(cat, []).extend(lines)
    for cat, lines in merged.items():
        for es, en in lines:
            if es.count("%s") != en.count("%s") or (cat.startswith("tip_") and es.count("%s") != 1):
                raise SystemExit(f"placeholder mismatch in {cat}: {es} / {en}")
            if "%" in es.replace("%s", "") or "%" in en.replace("%s", ""):
                raise SystemExit(f"stray % in {cat}: {es} / {en}")
    return merged


def merge_npc(es, en, merged):
    counts, added = {}, 0
    for cat, lines in merged.items():
        have_es, have_en, i = [], [], 0
        while f"tcgshop.npc.{cat}.{i}" in es:
            have_es.append(es[f"tcgshop.npc.{cat}.{i}"])
            have_en.append(en.get(f"tcgshop.npc.{cat}.{i}", es[f"tcgshop.npc.{cat}.{i}"]))
            i += 1
        seen = set(have_es)
        for a, b in lines:
            if a in seen:
                continue
            seen.add(a)
            have_es.append(a)
            have_en.append(b)
            added += 1
        for j, (a, b) in enumerate(zip(have_es, have_en)):
            es[f"tcgshop.npc.{cat}.{j}"] = a
            en[f"tcgshop.npc.{cat}.{j}"] = b
        counts[cat] = len(have_es)
    return counts, added


def write_counts(counts):
    raw = NPC_JAVA.read_bytes().decode("utf-8")
    crlf = "\r\n" in raw
    java = raw.replace("\r\n", "\n")
    start = java.index("Map.ofEntries(") + len("Map.ofEntries(")
    end = java.index(");", start)
    body = java[start:end]
    for cat, n in counts.items():
        pat = re.compile(r'Map\.entry\("' + re.escape(cat) + r'", \d+\)')
        if pat.search(body):
            body = pat.sub(f'Map.entry("{cat}", {n})', body)
    new = [c for c in counts if not re.search(r'Map\.entry\("' + re.escape(c) + r'", \d+\)', body)]
    if new:
        entries = [f'Map.entry("{c}", {counts[c]})' for c in new]
        rows = ",\n            ".join(", ".join(entries[i:i + 4]) for i in range(0, len(entries), 4))
        body = body.rstrip() + ",\n            // 1.1 customers update (tools/npc_lines_v4_*.py via gen_lang_12_customers.py)\n            " + rows
    java = java[:start] + body + java[end:]
    if crlf:
        java = java.replace("\n", "\r\n")
    NPC_JAVA.write_bytes(java.encode("utf-8"))


def register_in_gen_npc():
    """So a later run of gen_npc_lines.py keeps these lines (it appends module lines after its own, deduplicated)."""
    raw = GEN_NPC.read_bytes().decode("utf-8")
    old = '("npc_lines_v3_a", "npc_lines_v3_b", "npc_lines_v3_c", "npc_lines_v3_d")'
    if old in raw:
        raw = raw.replace(old, '("npc_lines_v3_a", "npc_lines_v3_b", "npc_lines_v3_c", "npc_lines_v3_d",\n             '
                          + ", ".join(f'"{m}"' for m in MODULES) + ")")
        GEN_NPC.write_bytes(raw.encode("utf-8"))


def main():
    merged = load_lines()
    pe, pn = LANG / "es_es.json", LANG / "en_us.json"
    es = json.loads(pe.read_text(encoding="utf-8"))
    en = json.loads(pn.read_text(encoding="utf-8"))
    counts, added = merge_npc(es, en, merged)
    for k, (a, b) in T.items():
        es[k], en[k] = a, b
    for stars, rows in GREEDY.items():
        assert len(rows) == 8
        for n, (a, b) in enumerate(rows):
            es[f"tcgshop.reviewtext.deal_greedy.{stars}.{n}"] = a
            en[f"tcgshop.reviewtext.deal_greedy.{stars}.{n}"] = b
    pe.write_text(json.dumps(es, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    pn.write_text(json.dumps(en, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_counts(counts)
    register_in_gen_npc()
    total = sum(len(v) for v in merged.values())
    print(f"{len(T)} texts, 40 review texts, {total} lines in the v4 modules ({added} newly added) over {len(counts)} categories")


if __name__ == "__main__":
    main()
