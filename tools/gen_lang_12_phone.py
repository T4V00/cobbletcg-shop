"""1.1 texts (Group E), area "phone": Grade 10 values in the Prices app, the Ranking app (beat Don Cobbledome), the
$1,000,000 Diamond loan, the binder HUD "closest collections" section and completion reward, receiving every graded
card at once, shelf markup, the Box-Opening Gloves, binders kept on death, the new gambling tables and sound subtitles.
Code: client/screen/PhoneScreen.java, phone/Ranking.java, client/phone/RankingApp.java, phone/Loans.java,
client/phone/LoansApp.java, advancement/CardAdvancements.java, client/binder/GeneralBinderScreen.java,
grading/GradingService.java, client/screen/ShopScreen.java, shop/ShopServer.java, item/BoxGlovesItem.java,
pack/BoxGloves.java, client/screen/BoxGlovesScreen.java, binder/BinderKeep.java, casino/*.

Usage: python tools/gen_lang_12_phone.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ E1 Prices app: Grade 10 value
    "tcgshop.phone.g10_value": ("Grado 10: %s", "Grade 10: %s"),
    "tcgshop.phone.g10_toggle": ("Mostrar lo que vale cada carta en Grado 10", "Show what each card is worth at Grade 10"),

    # ------------------------------------------------------------------ E2 Ranking app
    "tcgshop.phone.ranking": ("Ranking", "Ranking"),
    "tcgshop.rank.goal": ("Tu meta", "Your goal"),
    "tcgshop.rank.goal_text": ("Ganar más que Don Cobbledomo, dueño del Cobbledomo de Cobbledale.",
                               "Earn more than Don Cobbledome, owner of the Cobbledale Cobbledome."),
    "tcgshop.rank.your_rank": ("Puesto %s de %s", "Rank %s of %s"),
    "tcgshop.rank.profit": ("Rentabilidad %s", "Profit %s"),
    "tcgshop.rank.part.wallet": ("Billetera", "Wallet"),
    "tcgshop.rank.part.registers": ("Cajas registradoras", "Registers"),
    "tcgshop.rank.part.shops": ("Valor de tus tiendas", "Your shops' value"),
    "tcgshop.rank.part.owed": ("Deuda con el Mayorista", "Owed to the Wholesaler"),
    "tcgshop.rank.next": ("Te faltan %2$s para pasar a %1$s.", "%2$s more to pass %1$s."),
    "tcgshop.rank.don_gap": ("Don Cobbledomo te saca %s", "Don Cobbledome is %s ahead"),
    "tcgshop.rank.don_beaten": ("¡Superaste a Don Cobbledomo!", "You beat Don Cobbledome!"),
    "tcgshop.rank.col.owner": ("Dueño · tienda", "Owner · shop"),
    "tcgshop.rank.col.capital": ("Capital", "Capital"),
    "tcgshop.rank.col.profit": ("Rent.", "Profit"),
    "tcgshop.rank.shop_of": ("Tienda de cartas de %s", "%s's Card Shop"),
    "tcgshop.rank.shop_player": ("Las tiendas de %s", "%s's shops"),
    "tcgshop.rank.notice.title": ("Ranking", "Ranking"),
    "tcgshop.rank.notice.climb": ("¡Subiste al puesto #%s del ranking! (antes #%s)", "You climbed to #%s in the ranking! (was #%s)"),
    "tcgshop.rank.beat_don": ("🏆 ¡%s ya gana más que Don Cobbledomo, dueño del Cobbledomo de Cobbledale! Es el nuevo número 1 del ranking.",
                              "🏆 %s now earns more than Don Cobbledome, owner of the Cobbledale Cobbledome! The new number 1 of the ranking."),
    "tcgshop.rank.npc.0": ("Don Cobbledomo", "Don Cobbledome"),
    "tcgshop.rank.npc.0.shop": ("Cobbledomo de Cobbledale", "Cobbledale Cobbledome"),
    "tcgshop.rank.npc.1": ("Madame Holofoil", "Madame Holofoil"),
    "tcgshop.rank.npc.1.shop": ("Palacio Holográfico", "The Holofoil Palace"),
    "tcgshop.rank.npc.2": ("Sir Reginaldo Menta", "Sir Reginald Mint"),
    "tcgshop.rank.npc.2.shop": ("Estado Impecable S.A.", "Mint Condition Ltd."),
    "tcgshop.rank.npc.3": ("Baronesa Booster", "Baroness Booster"),
    "tcgshop.rank.npc.3.shop": ("Importadora de la Baronesa", "Baroness Booster Imports"),
    "tcgshop.rank.npc.4": ("Conde Graduado", "Count Graded"),
    "tcgshop.rank.npc.4.shop": ("Slab e Hijos", "Slab & Sons"),
    "tcgshop.rank.npc.5": ("Lady Prismia", "Lady Prismia"),
    "tcgshop.rank.npc.5.shop": ("Galería Prisma", "Prisma Gallery"),
    "tcgshop.rank.npc.6": ("Señor Funda", "Mr. Sleeve"),
    "tcgshop.rank.npc.6.shop": ("Reino de las Fundas", "Sleeve Kingdom"),
    "tcgshop.rank.npc.7": ("El Viejo Sobres", "Old Man Packs"),
    "tcgshop.rank.npc.7.shop": ("Emporio Rata de Sobres", "Pack Rat Emporium"),
    "tcgshop.rank.npc.8": ("Dra. Cárdigan", "Dr. Cardigan"),
    "tcgshop.rank.npc.8.shop": ("Coleccionables Cárdigan", "Cardigan Collectibles"),
    "tcgshop.rank.npc.9": ("Berta la del Álbum", "Big Bertha Binder"),
    "tcgshop.rank.npc.9.shop": ("El Granero del Álbum", "The Binder Barn"),

    # ------------------------------------------------------------------ E3 Diamond loan
    "tcgshop.loan.diamond.title": ("Préstamo Diamante", "Diamond loan"),
    "tcgshop.loan.diamond.card": ("Préstamo Diamante · %s", "Diamond loan · %s"),
    "tcgshop.loan.diamond.terms": ("Para dueños de tienda Diamante: %s días, interés según tu crédito.",
                                   "For Diamond shop owners: %s days, interest by your credit."),
    "tcgshop.loan.diamond.request": ("Pedir préstamo", "Request loan"),
    "tcgshop.loan.diamond.off": ("El préstamo Diamante está desactivado en este servidor.", "The Diamond loan is turned off on this server."),
    "tcgshop.loan.diamond.need_shop": ("Se desbloquea con una tienda Diamante (o mejor).", "Unlocked with a Diamond (or better) shop."),
    "tcgshop.loan.diamond.need_phone": ("El Mayorista solo presta a quien tenga un teléfono Pro o mejor.", "The Wholesaler only lends to owners of a Pro phone or better."),
    "tcgshop.loan.diamond.has_loan": ("Primero termina de pagar tu préstamo actual.", "Finish repaying your current loan first."),
    "tcgshop.loan.diamond.sent": ("El Mayorista te envió la oferta del préstamo Diamante a Mensajes.", "The Wholesaler sent the Diamond loan offer to your Messages."),
    "tcgshop.loan.diamond.offer": ("Vaya, vaya… una tienda Diamante. Para alguien de tu nivel tengo algo especial: %1$s al %2$s%% de interés, a devolver en %3$s días (%4$s en total). La oferta dura %5$s días. Mientras me debas, nada de compras desde el teléfono. ¿Trato hecho?",
                                   "Well, well… a Diamond shop. For someone of your standing I have something special: %1$s at %2$s%% interest, to repay within %3$s days (%4$s in total). The offer lasts %5$s days. While you owe me, no buying from the phone. Deal?"),

    # ------------------------------------------------------------------ E4 binder: closest sets + completion reward
    "tcgshop.binder.closest": ("Colecciones más cerca de completarse", "Closest to completion"),
    "tcgshop.binder.closest_none": ("Consigue cartas de una colección para ver aquí tu progreso.", "Get cards of a set to see your progress here."),
    "tcgshop.binder.closest_tip": ("Te faltan %s cartas · clic para abrir la colección", "%s cards missing · click to open the set"),
    "tcgshop.binder.reward": ("Premio por completar una colección: %s Master Ball + %s", "Reward for completing a set: %s Master Ball + %s"),
    "tcgshop.binder.reward_balls": ("Premio por completar una colección: %s Master Ball", "Reward for completing a set: %s Master Ball"),
    "tcgshop.binder.reward_money": ("Premio por completar una colección: %s", "Reward for completing a set: %s"),
    "tcgshop.collection.reward": ("🏆 ¡Completaste la colección %s! Premio: %s Master Ball y %s.", "🏆 You completed the %s set! Reward: %s Master Ball and %s."),

    # ------------------------------------------------------------------ E5 grading: collect everything
    "tcgshop.grade.collect_all": ("Recibir todo (%s pedidos · %s cartas)", "Collect all (%s orders · %s cards)"),
    "tcgshop.grade.skip_hint": ("Clic derecho: saltar al resumen", "Right click: skip to the summary"),

    # ------------------------------------------------------------------ E6 shelf markup
    "tcgshop.shop.markup_title": ("Subir todo", "Raise all"),
    "tcgshop.shop.markup_tip": ("Cambia en %s el precio de todo lo que hay en este mueble", "Changes the price of everything in this fixture by %s"),
    "tcgshop.shop.market_all": ("Todo a mercado", "All to market"),
    "tcgshop.shop.markup_done": ("Precios de %s productos cambiados en %s.", "Prices of %s products changed by %s."),
    "tcgshop.shop.market_all_done": ("%s productos ahora cuestan el precio de mercado.", "%s products now cost the market price."),

    # ------------------------------------------------------------------ E7 sound subtitles
    "subtitles.tcgshop.shop.sale": ("Venta en la caja", "Register: sale"),
    "subtitles.tcgshop.shop.sale_big": ("¡Gran venta!", "Big sale!"),
    "subtitles.tcgshop.shop.price_tag": ("Etiquetas de precio", "Price tags"),
    "subtitles.tcgshop.phone.notify": ("Notificación del teléfono", "Phone notification"),
    "subtitles.tcgshop.phone.buy": ("Compra en el teléfono", "Phone purchase"),
    "subtitles.tcgshop.phone.ranking_up": ("Subes en el ranking", "Ranking up"),
    "subtitles.tcgshop.collection.complete": ("Colección completa", "Set completed"),
    "subtitles.tcgshop.box.open_all": ("Caja abierta entera", "Whole box opened"),
    "subtitles.tcgshop.casino.chip": ("Fichas de casino", "Casino chips"),
    "subtitles.tcgshop.casino.win": ("Premio en el casino", "Casino win"),

    # ------------------------------------------------------------------ E8 Box-Opening Gloves
    "item.tcgshop.box_gloves": ("Guantes abrecajas", "Box-Opening Gloves"),
    "item.tcgshop.box_gloves.tip1": ("Con ellos puestos, clic derecho a una caja de sobres: ¿abrir la caja entera?", "While worn, right-click a booster box: open the whole box?"),
    "tcgshop.box_gloves.title": ("¿Abrir la caja entera?", "Open the whole box?"),
    "tcgshop.box_gloves.question": ("Tus guantes pueden abrir los %s sobres de una sola vez.", "Your gloves can open all %s packs at once."),
    "tcgshop.box_gloves.yes": ("Sí, abrir los %s sobres", "Yes, open all %s packs"),
    "tcgshop.box_gloves.all": ("Abrir todas las cajas (%s cajas · %s sobres)", "Open every box (%s boxes · %s packs)"),
    "tcgshop.box_gloves.no": ("No, solo dame los %s sobres", "No, just give me the %s packs"),
    "tcgshop.box_gloves.esc": ("Esc para cancelar", "Esc to cancel"),
    "tcgshop.box_gloves.done_binder": ("📦 Abriste %s caja(s): %s sobres, %s cartas por %s, guardadas en tu álbum. Lo mejor:",
                                       "📦 You opened %s box(es): %s packs, %s cards worth %s, filed in your binder. Best pulls:"),
    "tcgshop.box_gloves.done_inv": ("📦 Abriste %s caja(s): %s sobres, %s cartas por %s (en tu inventario). Lo mejor:",
                                    "📦 You opened %s box(es): %s packs, %s cards worth %s (in your inventory). Best pulls:"),
    "tcgshop.box_gloves.god": ("✨ ¡Y %s sobre(s) dios!", "✨ And %s god pack(s)!"),

    # ------------------------------------------------------------------ E9 binders kept on death
    "tcgshop.binder.kept": ("Tus álbumes (%s) se quedaron contigo.", "Your binders (%s) stayed with you."),

    # ------------------------------------------------------------------ E10 gambling tables
    "block.tcgshop.dice_table": ("Mesa de dados", "Dice Table"),
    "block.tcgshop.coin_toss": ("Moneda al aire", "Coin Toss Stand"),
    "tcgshop.casino.tip.dice": ("«Siete de la suerte»: apuesta a menos de 7, al 7, a más de 7, a dobles o a los extremos. Paga de 1:1 a 30:1.",
                                "\"Lucky Seven\": bet under 7, on 7, over 7, doubles or the long shots. Pays 1:1 to 30:1."),
    "tcgshop.casino.tip.coin": ("«Doble o nada»: cara o cruz pagan 1:1 (98%); si cae de canto paga 40:1.",
                                "\"Double or Nothing\": heads or tails pay 1:1 (98%); landing on its edge pays 40:1."),
    "tcgshop.casino.game.dice": ("la mesa de dados", "the dice table"),
    "tcgshop.casino.game.coin": ("la moneda al aire", "the coin toss"),
    "tcgshop.casino.dice.felt": ("SIETE DE LA SUERTE", "LUCKY SEVEN"),
    "tcgshop.casino.dice.pick": ("Elige tu apuesta", "Pick your bet"),
    "tcgshop.casino.dice.under": ("Menos de 7", "Under 7"),
    "tcgshop.casino.dice.seven": ("Exactamente 7", "Exactly 7"),
    "tcgshop.casino.dice.over": ("Más de 7", "Over 7"),
    "tcgshop.casino.dice.doubles": ("Dobles", "Doubles"),
    "tcgshop.casino.dice.snake": ("Ojos de serpiente (1-1)", "Snake eyes (1-1)"),
    "tcgshop.casino.dice.boxcars": ("Doble seis (6-6)", "Boxcars (6-6)"),
    "tcgshop.casino.dice.pays": ("paga %s:1", "pays %s:1"),
    "tcgshop.casino.dice.roll": ("Tirar", "Roll"),
    "tcgshop.casino.dice.rolled": ("Salió %s", "Rolled %s"),
    "tcgshop.casino.coin.pick": ("¿Cara, cruz o canto?", "Heads, tails or edge?"),
    "tcgshop.casino.coin.heads": ("Cara", "Heads"),
    "tcgshop.casino.coin.tails": ("Cruz", "Tails"),
    "tcgshop.casino.coin.edge": ("Canto", "Edge"),
    "tcgshop.casino.coin.odds": ("paga %s:1 · %s%%", "pays %s:1 · %s%%"),
    "tcgshop.casino.coin.flip": ("Lanzar", "Flip"),
    "tcgshop.casino.coin.landed": ("Salió %s", "Landed on %s"),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
