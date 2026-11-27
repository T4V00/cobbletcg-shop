"""1.1 texts (second batch), area "npcs": the Mayor and his taxes (bill window, battle, card taken, phone messages,
speech lines), the robbed collector's hunt (teleport following, boss bar), collector trades by phone tier (limits,
charisma talk options, price filter) and the CREATive Engineer ("Ingeniero CREATivo").
Code: npc/TaxOffice.java, entity/MayorEntity.java, compat/MayorBattle.java, client/screen/MayorScreen.java,
entity/CollectorHunt.java, entity/TraderEntity.java, client/screen/TraderScreen.java, entity/EngineerEntity.java,
client/screen/EngineerScreen.java.

Usage: python tools/gen_lang_12_npcs.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ entities / roles
    "entity.tcgshop.mayor": ("Alcalde", "Mayor"),
    "entity.tcgshop.engineer": ("Ingeniero CREATivo", "CREATive Engineer"),
    "tcgshop.role.mayor": ("Alcalde", "Mayor"),
    "tcgshop.role.engineer": ("Ingeniero CREATivo", "CREATive Engineer"),
    "tcgshop.msg.sender.mayor": ("Alcaldía", "Town Hall"),

    # ------------------------------------------------------------------ Mayor: chat
    "tcgshop.mayor.name": ("Alcalde %s", "Mayor %s"),
    "tcgshop.mayor.title": ("Día de impuestos", "Tax day"),
    "tcgshop.mayor.due": ("¡Día de impuestos! Debes %s a la alcaldía. El Alcalde viene a cobrar.",
                          "Tax day! You owe the town %s. The Mayor is coming to collect."),
    "tcgshop.mayor.arrived": ("El Alcalde llegó a cobrar tus impuestos (%s). Haz clic derecho sobre él para pagar o negarte.",
                              "The Mayor is here for your taxes (%s). Right-click him to pay or refuse."),
    "tcgshop.mayor.in_battle": ("Primero termina la batalla contra el Alcalde.", "Finish your battle against the Mayor first."),
    "tcgshop.mayor.cant_afford": ("No te alcanza: debes %s y entre tu billetera y tus cajas tienes %s.",
                                  "You can't afford it: you owe %s and your wallet plus registers hold %s."),
    "tcgshop.mayor.paid": ("Pagaste %s de impuestos. El Alcalde te deja en paz por %s días.",
                           "You paid %s in taxes. The Mayor leaves you alone for %s days."),
    "tcgshop.mayor.battle_start": ("¡El Alcalde acepta el desafío y saca a su equipo competitivo!",
                                   "The Mayor accepts the challenge and sends out his competitive team!"),
    "tcgshop.mayor.battle_rules": ("Si pierdes, te rindes, huyes o te alejas más de %s bloques, se lleva una carta al azar de tu binder.",
                                   "Lose, forfeit, flee or walk more than %s blocks away and he takes a random card from your binder."),
    "tcgshop.mayor.no_pokemon": ("No tienes ningún Pokémon en condiciones de pelear: cuenta como rendirse.",
                                 "You have no Pokémon able to battle: that counts as a forfeit."),
    "tcgshop.mayor.busy": ("Ya estás en una batalla. Termínala y vuelve a hablar con el Alcalde.",
                           "You're already in a battle. Finish it and talk to the Mayor again."),
    "tcgshop.mayor.chain_start": ("El Alcalde te reta a %s combates seguidos. ¡Gánalos todos!",
                                  "The Mayor challenges you to %s battles in a row. Win them all!"),
    "tcgshop.mayor.chain_next": ("Llevas %s de %s. ¡Sigue el próximo!", "That's %s of %s. Here comes the next one!"),
    "tcgshop.mayor.cant_battle": ("No se pudo organizar ninguna batalla, así que negarte te cuesta una carta.", "No battle could be set up, so refusing costs you a card."),
    "tcgshop.mayor.won": ("¡Le ganaste al Alcalde! Este período no pagas impuestos. Vuelve en %s días.",
                          "You beat the Mayor! No taxes this period. He'll be back in %s days."),
    "tcgshop.mayor.took": ("%s El Alcalde se llevó %s (%s) de tu binder.", "%s The Mayor took %s (%s) from your binder."),
    "tcgshop.mayor.took_money": ("%s No tenías cartas, así que el Alcalde se cobró %s.", "%s You had no cards, so the Mayor took %s instead."),
    "tcgshop.mayor.forced": ("Ignoraste al Alcalde: cobró por la fuerza %s (con recargo por atraso).",
                             "You ignored the Mayor: he collected %s by force (late fee included)."),
    "tcgshop.mayor.reason.refused": ("Te negaste a pagar.", "You refused to pay."),
    "tcgshop.mayor.reason.forfeit": ("Te rendiste.", "You forfeited."),
    "tcgshop.mayor.reason.lost": ("Perdiste la batalla.", "You lost the battle."),
    "tcgshop.mayor.reason.walked": ("Abandonaste la batalla.", "You walked away from the battle."),
    "tcgshop.mayor.reason.timeout": ("La batalla se alargó demasiado.", "The battle dragged on far too long."),
    "tcgshop.mayor.reason.gone": ("Te fuiste en plena batalla.", "You left in the middle of the battle."),
    "tcgshop.mayor.reason.ignored": ("Ignoraste al Alcalde y no te alcanzaba para pagar.", "You ignored the Mayor and couldn't pay."),
    "tcgshop.mayor.immune": ("Al Alcalde no se le toca. Paga o desafíalo.", "You can't touch the Mayor. Pay or challenge him."),
    "tcgshop.mayor.not_you": ("%s no viene por ti.", "%s isn't here for you."),

    # ------------------------------------------------------------------ Mayor: phone
    "tcgshop.mayor.msg.registered": ("Tu tienda ya es de las grandes: la alcaldía la registró. El Alcalde cobrará impuestos cada %s días.",
                                     "Your shop is a big one now: the town hall registered it. The Mayor will collect taxes every %s days."),
    "tcgshop.mayor.msg.due": ("Vencieron tus impuestos: %s. El Alcalde va en camino. Tienes %s días antes del cobro forzoso.",
                              "Your taxes are due: %s. The Mayor is on his way. You have %s days before he collects by force."),
    "tcgshop.mayor.msg.paid": ("Recibimos tu pago de %s. ¡Gracias por aportar al pueblo!", "We received your payment of %s. Thanks for supporting the town!"),
    "tcgshop.mayor.msg.won": ("El Alcalde reconoce la derrota. Este período quedas exento de impuestos.",
                              "The Mayor admits defeat. You're exempt from taxes this period."),
    "tcgshop.mayor.msg.took": ("Embargo realizado: %s (%s) pasa a la colección de la alcaldía.",
                               "Seizure done: %s (%s) now belongs to the town hall's collection."),
    "tcgshop.mayor.msg.forced": ("Cobro forzoso de impuestos atrasados: %s.", "Forced collection of overdue taxes: %s."),

    # ------------------------------------------------------------------ Mayor: window
    "tcgshop.mayor.ui.ask": ("«Buenos días. Las tiendas %s o mejores pagan impuestos. Aquí está su cuenta.»",
                             "\"Good day. Shops of %s tier or better pay taxes. Here is your bill.\""),
    "tcgshop.mayor.ui.property": ("Propiedad (valor de tus tiendas)", "Property (value of your shops)"),
    "tcgshop.mayor.ui.income": ("Ingresos: %s de %s ganados (%s días)", "Income: %s of %s earned (%s days)"),
    "tcgshop.mayor.ui.total": ("Total a pagar", "Total due"),
    "tcgshop.mayor.ui.funds": ("Billetera: %s · Cajas: %s", "Wallet: %s · Registers: %s"),
    "tcgshop.mayor.ui.deadline": ("Si no respondes en %s días, cobra a la fuerza con recargo.",
                                  "If you don't answer within %s days he collects by force, with a late fee."),
    "tcgshop.mayor.ui.pay": ("Pagar %s", "Pay %s"),
    "tcgshop.mayor.ui.pay_hint": ("Se paga de tu billetera y luego de tus cajas. Te deja en paz %s días.",
                                  "Paid from your wallet, then your registers. He leaves you alone for %s days."),
    "tcgshop.mayor.ui.refuse_battle": ("Negarse: ¡batalla!", "Refuse: battle!"),
    "tcgshop.mayor.ui.refuse": ("Negarse", "Refuse"),
    "tcgshop.mayor.ui.refuse_confirm": ("¿Seguro? Clic otra vez", "Sure? Click again"),
    "tcgshop.mayor.ui.refuse_hint_battle": ("Batalla Pokémon contra su equipo de 6. Si ganas, no pagas. Si pierdes, te rindes o te vas: se lleva 1 carta al azar de tu binder (tienes %s).",
                                            "A Pokémon battle against his team of 6. Win: no tax. Lose, forfeit or leave: he takes 1 random card from your binder (you have %s)."),
    "tcgshop.mayor.ui.refuse_hint": ("Se lleva 1 carta al azar de tu binder (tienes %s).", "He takes 1 random card from your binder (you have %s)."),

    # ------------------------------------------------------------------ Engineer
    "tcgshop.engineer.no_create": ("El Ingeniero CREATivo solo trabaja si Create está instalado.", "The CREATive Engineer only works when Create is installed."),
    "tcgshop.engineer.bought": ("Compraste %s × %s %s por %s.", "You bought %s × %s %s for %s."),
    "tcgshop.engineer.arrived": ("Un Ingeniero CREATivo llegó con máquinas de Create para vender.", "A CREATive Engineer arrived with Create machines for sale."),
    "tcgshop.engineer.already_near": ("Ya hay un Ingeniero CREATivo cerca.", "There's already a CREATive Engineer nearby."),
    "tcgshop.engineer.no_room": ("El Ingeniero no encontró dónde instalarse.", "The Engineer found no room to set up."),
    "tcgshop.engineer.subtitle": ("Materiales por stack y máquinas de Create. Precios de ingeniero.", "Create materials by the stack and machines. Engineer prices."),
    "tcgshop.engineer.hint": ("Los materiales básicos vienen en stacks de 64 (x64). Las máquinas, de a una.",
                              "Basic materials come in stacks of 64 (x64). Machines come one at a time."),
    "tcgshop.engineer.line.hello.0": ("¿Ejes? ¿Engranajes? Todo gira con la plata correcta.", "Shafts? Cogwheels? Everything turns with the right money."),
    "tcgshop.engineer.line.hello.1": ("Ojo con la tensión, que se te para todo.", "Mind the stress, or everything grinds to a halt."),
    "tcgshop.engineer.line.hello.2": ("Traigo prensas recién aceitadas.", "Got freshly oiled presses right here."),
    "tcgshop.engineer.line.hello.3": ("Una buena máquina se paga sola... eventualmente.", "A good machine pays for itself... eventually."),
    "tcgshop.engineer.line.hello.4": ("No vendo nada que explote. Casi nada.", "I don't sell anything that explodes. Almost nothing."),
    "tcgshop.engineer.line.hello.5": ("¿Quieres automatizar tu tienda? Empieza por aquí.", "Want to automate your shop? Start here."),
    "tcgshop.engineer.line.sold.0": ("¡Que gire bonito!", "May it spin nicely!"),
    "tcgshop.engineer.line.sold.1": ("Garantía: hasta que lo conectes al revés.", "Warranty: until you hook it up backwards."),
    "tcgshop.engineer.line.sold.2": ("Excelente elección, colega.", "Excellent choice, colleague."),
    "tcgshop.engineer.line.sold.3": ("Ese engranaje va a hacer historia.", "That gear is going to make history."),
    "tcgshop.engineer.line.sold.4": ("Vuelve cuando quieras ampliar la fábrica.", "Come back when you want a bigger factory."),

    # ------------------------------------------------------------------ robbed collector hunt
    "tcgshop.collector_hunt.followed": ("¡%s te siguió! Apareció a unos %s bloques. (Puede seguirte %s veces más)",
                                        "%s followed you! He appeared about %s blocks away. (He can follow you %s more times)"),
    "tcgshop.collector_hunt.bossbar": ("%s (te está cazando)", "%s (hunting you)"),

    # ------------------------------------------------------------------ collector trades by phone tier
    "tcgshop.trader.too_many_his": ("Con tu teléfono puedes llevarte hasta %s de sus cartas por trato.", "With your phone you can take up to %s of his cards per deal."),
    "tcgshop.trader.limits": ("Por trato: hasta %s tuyas · %s suyas", "Per deal: up to %s of yours · %s of his"),
    "tcgshop.trader.talk.head": ("Le falta poco: convéncelo (%s intentos)", "Almost there: talk him into it (%s tries)"),
    "tcgshop.trader.talk.none_left": ("Ya no quiere escuchar más charla en esta visita.", "He won't listen to any more talk this visit."),
    "tcgshop.trader.talk.too_far": ("La oferta está demasiado lejos: ninguna charla lo convence.", "The offer is too far off: no talk will convince him."),
    "tcgshop.trader.talk.failed": ("No lo convenciste (tenías %s%%).", "He wasn't convinced (you had %s%%)."),
    "tcgshop.trader.talk.worked": ("¡Tu carisma lo convenció!", "Your charisma won him over!"),
    "tcgshop.trader.talk.story.btn": ("Historia %s", "Story %s"),
    "tcgshop.trader.talk.compliment.btn": ("Halago %s", "Praise %s"),
    "tcgshop.trader.talk.promise.btn": ("Promesa %s", "Promise %s"),
    "tcgshop.trader.talk.story.tip": ("Cuéntale cómo conseguiste tus cartas. Probabilidad media, alcance medio.",
                                      "Tell him how you got your cards. Medium chance, medium reach."),
    "tcgshop.trader.talk.compliment.tip": ("Halaga su colección. La más segura, pero solo sirve si te falta muy poco.",
                                           "Praise his collection. The safest, but only works when you're very close."),
    "tcgshop.trader.talk.promise.tip": ("Prométele futuros tratos. Arriesgada, pero cubre la mayor diferencia.",
                                        "Promise him future deals. Risky, but covers the biggest gap."),
    "tcgshop.trader.filter.all": ("Todas", "All"),
    "tcgshop.trader.filter.under_1k": ("< $1k", "< $1k"),
    "tcgshop.trader.filter.1k_10k": ("$1k–10k", "$1k–10k"),
    "tcgshop.trader.filter.10k_100k": ("$10k–100k", "$10k–100k"),
    "tcgshop.trader.filter.over_100k": ("> $100k", "> $100k"),
    "tcgshop.trader.filter.none": ("No tienes cartas en ese rango de precio.", "You have no cards in that price range."),
}

MAYOR_LINES = {
    "greet": [("¡Buen día! Vengo por los impuestos.", "Good day! I'm here for the taxes."),
              ("La alcaldía no olvida a sus contribuyentes.", "The town hall never forgets its taxpayers."),
              ("Su tienda prospera. El pueblo también quiere su parte.", "Your shop is thriving. The town wants its share too."),
              ("Tengo su cuenta aquí mismo.", "I have your bill right here."),
              ("Las calles no se pavimentan solas, ¿sabe?", "The streets don't pave themselves, you know."),
              ("Un ciudadano ejemplar paga a tiempo.", "A model citizen pays on time."),
              ("¿Hablamos de números?", "Shall we talk numbers?"),
              ("No me haga esperar, que tengo más tiendas que visitar.", "Don't keep me waiting, I have more shops to visit.")],
    "wait": [("Sigo esperando su respuesta.", "I'm still waiting for your answer."),
             ("¿Paga o me reta? No tengo todo el día.", "Pay or challenge me? I don't have all day."),
             ("Le advierto: mi equipo no es de adorno.", "I warn you: my team isn't for show."),
             ("El recargo por atraso no es broma.", "The late fee is no joke."),
             ("Tic, tac... los impuestos no esperan.", "Tick, tock... taxes don't wait."),
             ("Puedo seguirlo todo el día. Literalmente.", "I can follow you all day. Literally."),
             ("Mis Pokémon también cobran horas extra.", "My Pokémon charge overtime too."),
             ("Un clic y arreglamos esto.", "One click and we settle this.")],
    "paid": [("¡Un placer hacer negocios con usted!", "A pleasure doing business with you!"),
             ("El pueblo le agradece.", "The town thanks you."),
             ("Nos vemos en un mes.", "See you in a month."),
             ("Así me gusta, todo en orden.", "That's what I like, all in order."),
             ("Con esto arreglamos la fuente de la plaza.", "This will fix the fountain in the square.")],
    "won": [("¡Increíble! Me ganó limpiamente.", "Incredible! You beat me fair and square."),
            ("Este período queda exento. Pero volveré.", "You're exempt this period. But I'll be back."),
            ("Tengo que entrenar más...", "I need to train more..."),
            ("Respeto. Mucho respeto.", "Respect. A lot of respect.")],
    "took": [("Esta carta queda embargada.", "This card is now seized."),
             ("La ley es la ley.", "The law is the law."),
             ("Una pieza preciosa para el museo del pueblo.", "A lovely piece for the town museum."),
             ("La próxima vez pague a tiempo.", "Next time, pay on time."),
             ("No es personal, son impuestos.", "It's not personal, it's taxes.")],
    "duel": [("¡Muy bien! Que hablen los Pokémon.", "Very well! Let the Pokémon talk."),
             ("Mi equipo es de campeonato, se lo advierto.", "My team is championship level, I warn you."),
             ("Si pierde, me llevo una carta. Sin excusas.", "If you lose, I take a card. No excuses."),
             ("¡Que empiece la audiencia pública!", "Let the public hearing begin!"),
             ("Veamos si su equipo vale lo que debe.", "Let's see if your team is worth what you owe.")],
}

TALK_LINES = {
    "ok.story": [("¡Qué historia! Está bien, trato hecho.", "What a story! Fine, it's a deal."),
                 ("Me ganaste con esa anécdota.", "That anecdote won me over."),
                 ("Hace años que no oía algo así. Acepto.", "I haven't heard one like that in years. I accept.")],
    "fail.story": [("Bonita historia, pero no me convence.", "Nice story, but I'm not convinced."),
                   ("Eso ya me lo han contado antes...", "I've heard that one before..."),
                   ("Las historias no pagan cartas.", "Stories don't pay for cards.")],
    "ok.compliment": [("Bueno, sí tengo buen ojo... acepto.", "Well, I do have a good eye... I accept."),
                      ("Me halagas. Hagámoslo.", "You flatter me. Let's do it."),
                      ("Tienes buen gusto. Trato hecho.", "You have good taste. Deal.")],
    "fail.compliment": [("Los halagos no cambian los números.", "Flattery doesn't change the numbers."),
                        ("Gracias, pero no.", "Thanks, but no."),
                        ("Buen intento.", "Nice try.")],
    "ok.promise": [("Te tomo la palabra. Acepto.", "I'll hold you to that. I accept."),
                   ("Futuros tratos... me gusta. Hecho.", "Future deals... I like it. Done."),
                   ("Confío en ti. Esta vez.", "I trust you. This time.")],
    "fail.promise": [("Promesas, promesas...", "Promises, promises..."),
                     ("No compro humo.", "I don't buy hot air."),
                     ("Vuelve cuando tengas algo mejor.", "Come back when you have something better.")],
}


def main():
    t = dict(T)
    for cat, lines in MAYOR_LINES.items():
        for i, v in enumerate(lines):
            t[f"tcgshop.mayor.line.{cat}.{i}"] = v
    for cat, lines in TALK_LINES.items():
        for i, v in enumerate(lines):
            t[f"tcgshop.trader.talk.{cat}.{i}"] = v
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in t.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(t), "texts")


if __name__ == "__main__":
    main()
