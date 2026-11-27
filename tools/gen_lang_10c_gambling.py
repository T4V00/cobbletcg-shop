"""1.0 beta 3 casino texts (dev.tcgshop.casino): machines, scratch tickets, screens, owner tray, customers who
gamble and the casino advancements (data files from tools/gen_casino_assets.py).

Usage: python tools/gen_lang_10c_gambling.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ blocks and items
    "block.tcgshop.slot_machine": ("Tragaperras", "Slot Machine"),
    "block.tcgshop.vip_slot_machine": ("Tragaperras VIP", "VIP Slot Machine"),
    "block.tcgshop.roulette_table": ("Mesa de ruleta europea", "European Roulette Table"),
    "block.tcgshop.blackjack_table": ("Mesa de blackjack", "Blackjack Table"),
    "block.tcgshop.fortune_wheel": ("Rueda de la fortuna", "Fortune Wheel"),
    "item.tcgshop.scratch_card_bronze": ("Rasca y gana: Moneda de la suerte", "Scratch card: Lucky Coin"),
    "item.tcgshop.scratch_card_silver": ("Rasca y gana: Fiebre del oro", "Scratch card: Gold Rush"),
    "item.tcgshop.scratch_card_gold": ("Rasca y gana: Megabote Master", "Scratch card: Master Jackpot"),
    "tcgshop.casino.scratch.bronze": ("Moneda de la suerte", "Lucky Coin"),
    "tcgshop.casino.scratch.silver": ("Fiebre del oro", "Gold Rush"),
    "tcgshop.casino.scratch.gold": ("Megabote Master", "Master Jackpot"),

    # ------------------------------------------------------------------ tooltips
    "tcgshop.casino.tip.slots": ("3 rodillos, 1 línea. Devuelve el 95,4%% de lo apostado a la larga.",
                                 "3 reels, 1 payline. Pays back 95.4%% of what's bet in the long run."),
    "tcgshop.casino.tip.vip_slots": ("La misma máquina, para bolsillos sin fondo: apuestas mucho más altas. RTP 95,4%%.",
                                     "Same machine, for bottomless pockets: much higher stakes. RTP 95.4%%."),
    "tcgshop.casino.tip.roulette": ("Ruleta europea de un solo cero: plenos, caballos, calles, cuadros, docenas... Ventaja de la casa: 2,7%%.",
                                    "Single-zero European roulette: straights, splits, streets, corners, dozens... House edge: 2.7%%."),
    "tcgshop.casino.tip.blackjack": ("El crupier se planta con 17 blando. Blackjack paga 3:2, doblar y separar. Casa: ~0,4%% jugando bien.",
                                     "Dealer stands on soft 17. Blackjack pays 3:2, double and split. House: ~0.4%% with good play."),
    "tcgshop.casino.tip.wheel": ("La rueda «Big Six» de 54 casillas: paga de 1:1 a 40:1. La peor apuesta del casino... y la más divertida.",
                                 "The 54-segment \"Big Six\" wheel: pays 1:1 to 40:1. The worst bet in the casino... and the most fun."),
    "tcgshop.casino.tip.max_bet": ("Apuesta máxima: %s", "Max bet: %s"),
    "tcgshop.casino.tip.owner": ("Colócala donde quieras. Te llevas una parte de lo que apuestan otros (agáchate y clic derecho para cobrar).",
                                 "Place it anywhere. You get a cut of what others bet (sneak + right click to collect)."),
    "tcgshop.casino.scratch.tip.rule": ("Rasca 9 casillas: si salen 3 cantidades iguales, ¡ganas esa cantidad!",
                                        "Scratch 9 boxes: 3 matching amounts and that amount is yours!"),
    "tcgshop.casino.scratch.tip.top": ("Premio gordo: %s", "Top prize: %s"),
    "tcgshop.casino.scratch.tip.odds": ("Toca 1 de cada %s · devuelve un %s%% de media", "Wins 1 in %s · returns %s%% on average"),
    "tcgshop.casino.scratch.tip.use": ("Clic derecho: rascar · Agachado: rascar de golpe", "Right click: scratch · Sneaking: scratch it all at once"),

    # ------------------------------------------------------------------ server messages
    "tcgshop.casino.disabled": ("El casino está cerrado en este servidor.", "The casino is closed on this server."),
    "tcgshop.casino.need_client": ("Para jugar necesitas MasterBall Traders Co. instalado en tu juego.", "You need MasterBall Traders Co. installed on your game to play."),
    "tcgshop.casino.bad_bet": ("Apuesta fuera de límites (de %s a %s).", "Bet out of limits (%s to %s)."),
    "tcgshop.casino.no_economy": ("Sin CobbleDollars no hay apuestas.", "No CobbleDollars, no bets."),
    "tcgshop.casino.no_money": ("No te llega para apostar %s.", "You can't afford a %s bet."),
    "tcgshop.casino.loan_blocked": ("Tienes un préstamo activo (debes %s): nada de apuestas ni rasca y gana hasta que lo devuelvas.",
                                    "You have an active loan (you owe %s): no betting or scratch cards until it is repaid."),
    "tcgshop.casino.game.slots": ("la tragaperras", "the slot machine"),
    "tcgshop.casino.game.vip_slots": ("la tragaperras VIP", "the VIP slot machine"),
    "tcgshop.casino.game.roulette": ("la ruleta", "the roulette"),
    "tcgshop.casino.game.blackjack": ("el blackjack", "blackjack"),
    "tcgshop.casino.game.wheel": ("la rueda de la fortuna", "the fortune wheel"),
    "tcgshop.casino.announce": ("★ ¡%s acaba de ganar %s en %s! ★", "★ %s just won %s at %s! ★"),
    "tcgshop.casino.bj.auto_stand": ("Dejaste una mano de blackjack a medias: te has plantado solo. Cobras %s.",
                                     "You left a blackjack hand unfinished: you stood automatically. You get %s."),
    "tcgshop.casino.scratch.won": ("¡Premio! Has ganado %s", "Winner! You won %s"),
    "tcgshop.casino.scratch.lost": ("Nada esta vez... ¡el siguiente seguro que sí!", "Nothing this time... surely the next one!"),
    "tcgshop.casino.collected": ("Recoges %s de la bandeja de la máquina.", "You collect %s from the machine's tray."),
    "tcgshop.casino.tray_empty": ("La bandeja está vacía.", "The tray is empty."),
    "tcgshop.casino.stats": ("Rondas: %s · apostado: %s · pagado: %s · devuelto: %s", "Rounds: %s · wagered: %s · paid out: %s · returned: %s"),

    # ------------------------------------------------------------------ screens: common
    "tcgshop.casino.balance": ("Saldo: %s", "Balance: %s"),
    "tcgshop.casino.owner": ("Casa: %s", "House: %s"),
    "tcgshop.casino.bet": ("Apuesta: %s", "Bet: %s"),
    "tcgshop.casino.win": ("¡GANAS %s!", "YOU WIN %s!"),
    "tcgshop.casino.lose": ("La casa se queda %s", "The house keeps %s"),
    "tcgshop.casino.limits": ("Mín. %s · máx. %s por jugada · juega con cabeza", "Min %s · max %s per play · play responsibly"),
    "tcgshop.casino.spin": ("¡GIRAR!", "SPIN!"),
    "tcgshop.casino.paytable": ("Premios", "Paytable"),
    "tcgshop.casino.chip": ("Ficha:", "Chip:"),
    "tcgshop.casino.total_bet": ("En la mesa: %s", "On the table: %s"),
    "tcgshop.casino.rebet": ("Repetir", "Rebet"),
    "tcgshop.casino.undo": ("Deshacer", "Undo"),
    "tcgshop.casino.clear": ("Limpiar", "Clear"),
    "tcgshop.casino.close": ("Cerrar", "Close"),

    # ------------------------------------------------------------------ slots
    "tcgshop.casino.slots.marquee": ("★ 7 7 7 ★", "★ 7 7 7 ★"),
    "tcgshop.casino.slots.vip_marquee": ("★ SALA VIP ★", "★ VIP LOUNGE ★"),
    "tcgshop.casino.slots.good_luck": ("¡Suerte!", "Good luck!"),
    "tcgshop.casino.slots.no_win": ("Casi... ¡otra!", "So close... again!"),
    "tcgshop.casino.slots.insert": ("Elige tu apuesta y tira de la palanca", "Pick your bet and pull the lever"),
    "tcgshop.casino.slots.any_premium": ("★/7/♦ mezclados ×%s", "Mixed ★/7/♦ ×%s"),
    "tcgshop.casino.slots.rtp": ("Devuelve un 95,4%% · 1 premio cada 4,4 tiradas", "Returns 95.4%% · a win every 4.4 spins"),

    # ------------------------------------------------------------------ roulette
    "tcgshop.casino.roulette.even": ("PAR", "EVEN"),
    "tcgshop.casino.roulette.odd": ("IMPAR", "ODD"),
    "tcgshop.casino.roulette.red": ("ROJO", "RED"),
    "tcgshop.casino.roulette.black": ("NEGRO", "BLACK"),
    "tcgshop.casino.roulette.rules": ("Pleno 35:1 · caballo 17:1 · calle 11:1 · cuadro 8:1 · seisena 5:1 · docena/columna 2:1 · sencillas 1:1 · máx. %s · clic derecho quita fichas",
                                      "Straight 35:1 · split 17:1 · street 11:1 · corner 8:1 · six line 5:1 · dozen/column 2:1 · even money 1:1 · max %s · right click removes chips"),

    # ------------------------------------------------------------------ fortune wheel
    "tcgshop.casino.wheel.joker": ("Comodín", "Joker"),
    "tcgshop.casino.wheel.logo": ("Estrella", "Star"),
    "tcgshop.casino.wheel.landed": ("Ha caído en %s", "It landed on %s"),
    "tcgshop.casino.wheel.pick": ("Elige casilla", "Pick a symbol"),
    "tcgshop.casino.wheel.pays": ("%s:1 · %s/54", "%s:1 · %s/54"),
    "tcgshop.casino.wheel.rtp": ("Esta casilla devuelve un %s%%", "This symbol returns %s%%"),

    # ------------------------------------------------------------------ blackjack
    "tcgshop.casino.bj.felt1": ("BLACKJACK PAGA 3 A 2", "BLACKJACK PAYS 3 TO 2"),
    "tcgshop.casino.bj.felt2": ("El crupier pide hasta 16 y se planta en todos los 17", "Dealer must draw to 16 and stand on all 17s"),
    "tcgshop.casino.bj.dealer": ("Crupier: %s", "Dealer: %s"),
    "tcgshop.casino.bj.hand_bet": ("Apuesta %s", "Bet %s"),
    "tcgshop.casino.bj.win": ("GANAS", "WIN"),
    "tcgshop.casino.bj.blackjack": ("¡BLACKJACK!", "BLACKJACK!"),
    "tcgshop.casino.bj.push": ("EMPATE", "PUSH"),
    "tcgshop.casino.bj.lose": ("PIERDES", "LOSE"),
    "tcgshop.casino.bj.bust": ("TE PASAS", "BUST"),
    "tcgshop.casino.bj.push_msg": ("Empate: recuperas tu apuesta", "Push: you get your bet back"),
    "tcgshop.casino.bj.place_bet": ("Haz tu apuesta y pulsa Repartir", "Place your bet and press Deal"),
    "tcgshop.casino.bj.hit": ("Pedir (H)", "Hit (H)"),
    "tcgshop.casino.bj.stand": ("Plantarse (S)", "Stand (S)"),
    "tcgshop.casino.bj.double": ("Doblar (D)", "Double (D)"),
    "tcgshop.casino.bj.split": ("Separar (P)", "Split (P)"),
    "tcgshop.casino.bj.deal": ("REPARTIR", "DEAL"),

    # ------------------------------------------------------------------ scratch screen
    "tcgshop.casino.scratch.rule": ("3 cantidades iguales = ¡premio!", "3 matching amounts = you win it!"),
    "tcgshop.casino.scratch.top_prize": ("Hasta %s", "Up to %s"),
    "tcgshop.casino.scratch.no_luck": ("Sin premio esta vez", "No prize this time"),
    "tcgshop.casino.scratch.hint": ("Arrastra el ratón para rascar", "Drag the mouse to scratch"),
    "tcgshop.casino.scratch.reveal_all": ("Rascar todo", "Reveal all"),

    # ------------------------------------------------------------------ advancements
    "advancement.tcgshop.casino_first_win.title": ("La suerte del principiante", "Beginner's luck"),
    "advancement.tcgshop.casino_first_win.desc": ("Gana algo en cualquier juego del casino", "Win something at any casino game"),
    "advancement.tcgshop.casino_jackpot.title": ("¡¡¡JACKPOT!!!", "JACKPOT!!!"),
    "advancement.tcgshop.casino_jackpot.desc": ("Saca tres diamantes en una tragaperras (1 entre 21.845)", "Line up three gems on a slot machine (1 in 21,845)"),
    "advancement.tcgshop.casino_blackjack.title": ("Veintiuno a la primera", "Twenty-one on the deal"),
    "advancement.tcgshop.casino_blackjack.desc": ("Consigue un blackjack natural", "Get a natural blackjack"),
    "advancement.tcgshop.casino_straight_up.title": ("Pleno al quince", "Straight up!"),
    "advancement.tcgshop.casino_straight_up.desc": ("Acierta un pleno en la ruleta europea", "Hit a straight-up number at the European roulette"),
    "advancement.tcgshop.casino_wheel_joker.title": ("Cuarenta a uno", "Forty to one"),
    "advancement.tcgshop.casino_wheel_joker.desc": ("Gana apostando al Comodín o a la Estrella de la rueda de la fortuna", "Win on the Joker or the Star of the fortune wheel"),
    "advancement.tcgshop.casino_scratch_top.title": ("Rasca, rasca... ¡RASCA!", "Scratch, scratch... SCRATCH!"),
    "advancement.tcgshop.casino_scratch_top.desc": ("Gana 100 veces o más el precio de un rasca y gana", "Win 100 times a scratch card's price or more"),
    "advancement.tcgshop.casino_millionaire.title": ("La banca no siempre gana", "The house doesn't always win"),
    "advancement.tcgshop.casino_millionaire.desc": ("Gana más de 1.000.000 limpio en una sola jugada", "Win over 1,000,000 net in a single play"),
}

# Shop customers who play a machine (speech bubbles; %s = amount). Casino.NPC_LINES = 8 per kind.
NPC = {
    "win": [
        ("¡Toma ya! ¡%s para mí!", "Yes! %s for me!"),
        ("¡Lo sabía! Hoy es mi día: %s.", "I knew it! Today's my day: %s."),
        ("Mamá, mira: ¡%s! Bueno, no se lo digas a mamá.", "Mom, look: %s! Actually, don't tell Mom."),
        ("Con %s me compro una caja entera de sobres.", "With %s I'm buying a whole booster box."),
        ("¡%s! Esto lo celebro abriendo sobres.", "%s! I'm celebrating by opening packs."),
        ("¿Has visto eso? ¡%s de golpe!", "Did you see that? %s just like that!"),
        ("Me retiro con %s. Siempre hay que irse ganando.", "I'm cashing out with %s. Always quit while you're ahead."),
        ("¡Ding, ding, ding! %s, señores.", "Ding, ding, ding! %s, folks."),
    ],
    "lose": [
        ("Adiós a mis %s... era para sobres.", "Bye bye %s... that was pack money."),
        ("Esta máquina está trucada. Bueno, no. Pero lo parece.", "This machine is rigged. Well, no. But it feels like it."),
        ("Una más y lo dejo. Mentira.", "One more and I quit. That's a lie."),
        ("%s menos. Mejor me compro una carta, que no se esfuma.", "%s gone. I should've bought a card, those don't vanish."),
        ("Casi... ¡casi! Me faltó nada.", "So close... SO close! Missed by nothing."),
        ("La casa gana. Como siempre.", "The house wins. As always."),
        ("Ni una mísera cereza... %s a la basura.", "Not one lousy cherry... %s down the drain."),
        ("Vale, la suerte la guardo para el próximo sobre.", "Fine, I'll save my luck for the next pack."),
    ],
    "push": [
        ("Ni gano ni pierdo: recupero mis %s.", "No win, no loss: I get my %s back."),
        ("Empate. Algo es algo.", "A push. Better than nothing."),
        ("Lo que entra, sale. %s otra vez en el bolsillo.", "What goes in comes out. %s back in my pocket."),
        ("Me quedo como estaba. Qué emoción...", "Right back where I started. How thrilling..."),
        ("Recupero los %s. ¿Otra?", "I got my %s back. Again?"),
        ("Ni frío ni calor.", "Neither here nor there."),
        ("Por lo menos no he perdido nada.", "At least I didn't lose anything."),
        ("Tablas. La máquina y yo, igualados.", "A draw. The machine and I are even."),
    ],
}


def main():
    texts = dict(T)
    for kind, lines in NPC.items():
        for i, v in enumerate(lines):
            texts[f"tcgshop.casino.npc.{kind}.{i}"] = v
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in texts.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(texts), "texts")


if __name__ == "__main__":
    main()
