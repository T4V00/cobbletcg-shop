"""Texts for quick-sell (phone / Wholesaler), emeralds, starter kit and the wandering Collector."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

T = {
    "entity.tcgshop.trader": ("Coleccionista ambulante", "Wandering Collector"),
    "item.tcgshop.trader_spawn_egg": ("Huevo generador de Coleccionista", "Wandering Collector Spawn Egg"),
    "tcgshop.role.trader": ("Coleccionista", "Collector"),
    "tcgshop.trader.arrived": ("Un Coleccionista ambulante ha llegado cerca... cambia cartas y paga bien por la que le guste",
                               "A Wandering Collector has arrived nearby... trades cards and pays well for the one he likes"),
    "tcgshop.trader.subtitle": ("Cambia cartas de valor parecido · compra la que le enamore", "Trades cards of similar value · buys the one he loves"),
    "tcgshop.trader.trades_left": ("Tratos: %s", "Deals left: %s"),
    "tcgshop.trader.his": ("Sus cartas", "His cards"),
    "tcgshop.trader.yours": ("Tus cartas", "Your cards"),
    "tcgshop.trader.pick_his": ("Elige una de sus cartas", "Pick one of his cards"),
    "tcgshop.trader.pick_yours": ("Elige una tuya", "Pick one of yours"),
    "tcgshop.trader.v_low": ("Poco para él", "Too little for him"),
    "tcgshop.trader.v_fair": ("Trato justo", "Fair deal"),
    "tcgshop.trader.v_generous": ("Le regalas valor", "You're giving value away"),
    "tcgshop.trader.trade": ("Intercambiar", "Trade"),
    "tcgshop.trader.wish": ("♥ Le encanta tu %s: te paga %s", "♥ He loves your %s: pays %s"),
    "tcgshop.trader.sell_wish": ("Vendérsela", "Sell it"),
    "tcgshop.trader.no_more": ("Ya ha hecho suficientes tratos por hoy.", "He's done enough deals for today."),
    "tcgshop.trader.sold": ("Le vendes %s por %s.", "You sell %s for %s."),
    "tcgshop.trader.too_low": ("Tu carta vale demasiado poco para ese cambio.", "Your card is worth too little for that trade."),
    "tcgshop.trader.traded": ("Cambias %s por %s.", "You trade %s for %s."),
    "tcgshop.wholesaler.tip_emeralds": ("¿Te faltan CobbleDollars? Tráeme esmeraldas: pago %s por cada una (pestaña Vender). También compro cartas.",
                                        "Short on CobbleDollars? Bring me emeralds: I pay %s each (Sell tab). I buy cards too."),
    "tcgshop.starter.packs": ("¡Bienvenido! Recibes %s sobres al azar para empezar tu colección.", "Welcome! You get %s random packs to start your collection."),
    "tcgshop.phone.sell": ("Venta rápida", "Quick sell"),
    "tcgshop.sell.no_bank": ("Hace falta CobbleDollars para vender.", "CobbleDollars is needed to sell."),
    "tcgshop.sell.done": ("Vendes %s cartas por %s.", "You sell %s cards for %s."),
    "tcgshop.sell.no_emeralds": ("No llevas esmeraldas.", "You have no emeralds."),
    "tcgshop.sell.emeralds": ("Vendes %s esmeraldas por %s.", "You sell %s emeralds for %s."),
    "tcgshop.sell.emerald_rate": ("Esmeraldas → %s cada una", "Emeralds → %s each"),
    "tcgshop.sell.emerald_have": ("Llevas %s (bloques cuentan x9) = %s", "You carry %s (blocks count x9) = %s"),
    "tcgshop.sell.emerald_btn": ("Vender todas", "Sell all"),
    "tcgshop.sell.wholesaler_hint": ("Te compro cartas al 40% de su valor de mercado:", "I buy cards at 40% of their market value:"),
    "tcgshop.sell.no_cards": ("No llevas cartas en el inventario.", "You have no cards in your inventory."),
    "tcgshop.sell.selected": ("%s elegidas · %s", "%s selected · %s"),
    "tcgshop.sell.sell_btn": ("Vender", "Sell"),
    "tcgshop.sell.confirm": ("¿Seguro? Clic otra vez", "Sure? Click again"),
    "tcgshop.sell.market": ("Valor de mercado: %s", "Market value: %s"),
    "tcgshop.sell.you_get": ("Te dan: %s", "You get: %s"),
    "tcgshop.sell.tab_buy": ("Comprar", "Buy"),
    "tcgshop.sell.tab_sell": ("Vender", "Sell"),
    "tcgshop.sell.phone_slogan": ("Dinero al instante", "Instant cash"),
    "tcgshop.sell.phone_hint": ("Véndelas ya al 50% de su valor. Para sacar más, ponlas en tu tienda o cámbialas con el Coleccionista.",
                                "Sell them now at 50% of their value. To get more, put them in your shop or trade with the Collector."),
    "tcgshop.sell.all": ("Todas", "All"),
    "tcgshop.sell.none": ("Ninguna", "None"),
    "tcgshop.sell.count": ("Cartas: %s", "Cards: %s"),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
