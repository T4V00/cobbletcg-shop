"""1.0 phone texts: purchase notices inside the phone / Wholesaler stall, the Market's locked/unlocked filter and the
phone tier perks (TCG League at Pro, x36 boxes of every pack you can buy).

Usage: python tools/gen_lang_10_phone.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ purchase notices (client/phone/PhoneNotices)
    "tcgshop.phone.notice.item": ("artículo", "item"),
    "tcgshop.phone.notice.bought.title": ("¡Compra realizada!", "Purchase complete!"),
    # amounts come from PriceService.format, which already appends the currency suffix (" ₽"): no symbol here
    "tcgshop.phone.notice.bought": ("%s× %s por %s. Te quedan %s.",
                                    "%s× %s for %s. You have %s left."),
    "tcgshop.phone.notice.no_money.title": ("No te llega el dinero", "Not enough money"),
    "tcgshop.phone.notice.no_money": ("%s× %s cuesta %s y tienes %s: te faltan %s.",
                                      "%s× %s costs %s and you have %s: you are %s short."),
    "tcgshop.phone.notice.no_money_upgrade": ("Mejorar a %s cuesta %s y tienes %s: te faltan %s.",
                                              "Upgrading to %s costs %s and you have %s: you are %s short."),
    "tcgshop.phone.notice.locked.title": ("Producto bloqueado", "Locked product"),
    "tcgshop.phone.notice.locked": ("%s requiere el teléfono %s. Mejóralo desde la app Mejoras.",
                                    "%s needs the %s phone. Upgrade it from the Upgrades app."),
    "tcgshop.phone.notice.no_bank.title": ("Sin CobbleDollars", "No CobbleDollars"),
    "tcgshop.phone.notice.no_bank": ("Hace falta el mod CobbleDollars para comprar.",
                                     "The CobbleDollars mod is needed to buy."),
    "tcgshop.phone.notice.upgraded.title": ("¡Teléfono mejorado!", "Phone upgraded!"),
    "tcgshop.phone.notice.upgraded": ("Ya tienes el teléfono %s (%s). Mira en Mejoras todo lo que has desbloqueado.",
                                      "You now have the %s phone (%s). Check Upgrades to see everything you unlocked."),

    # existing upgrade tooltips: the amount already carries the currency suffix (showed "₽ 20.000 ₽")
    "tcgshop.phoneup.buy_tip": ("Mejorar a %s por %s (pulsa dos veces para confirmar)",
                                "Upgrade to %s for %s (click twice to confirm)"),
    "tcgshop.phoneup.cant_afford": ("Te faltan CobbleDollars: %s cuesta %s",
                                    "Not enough CobbleDollars: %s costs %s"),

    # ------------------------------------------------------------------ Market: locked / unlocked filter
    "tcgshop.phone.market.lock.0": ("Todo", "All"),
    "tcgshop.phone.market.lock.1": ("Disponibles", "Available"),
    "tcgshop.phone.market.lock.2": ("Bloqueados", "Locked"),
    "tcgshop.phone.market.lock.tip": ("Filtra el Mercado: todo, solo lo que tu teléfono ya puede comprar o solo lo bloqueado (necesita una mejora).",
                                      "Filters the Market: everything, only what your phone can already buy, or only what is locked (needs an upgrade)."),

    # ------------------------------------------------------------------ tier perks (League at Pro, boxes follow their pack)
    "tcgshop.phoneup.perks.basic": ("Mercado básico (sobres modernos baratos y sus cajas x36, muebles, bloques de tienda y herramientas), Mi tienda, Colección, Venta rápida, Amigos, Títulos, Logros y Ajustes.",
                                    "Basic Market (cheap modern packs and their x36 boxes, furniture, shop blocks and tools), My shop, Collection, Quick sell, Friends, Titles, Advancements and Settings."),
    "tcgshop.phoneup.perks.pro": ("Apps Expositores, Muebles y Liga TCG · sobres de gama alta (y sus cajas x36) · −3% en el Mercado.",
                                  "Displays, Furniture and TCG League apps · high-end packs (and their x36 boxes) · −3% in the Market."),
    "tcgshop.phoneup.perks.max": ("Ruleta Poké · sobres antiguos raros (y sus cajas) · −6% en el Mercado.",
                                  "Poké Roulette · rare older packs (and their boxes) · −6% in the Market."),
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
