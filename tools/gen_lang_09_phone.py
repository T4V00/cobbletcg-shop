# -*- coding: utf-8 -*-
"""0.9 · stream phone: textos de las mejoras del teléfono (es_es + en_us). Idempotente: sólo fija estas claves."""
import json
import os
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
LANG = os.path.join(HERE, "..", "src", "main", "resources", "assets", "tcgshop", "lang")

KEYS = {
    # app y ajustes
    "tcgshop.phone.upgrades": ("Mejoras", "Upgrades"),
    "tcgshop.settings.phone_upgrades": ("Mejoras del teléfono", "Phone upgrades"),
    # niveles
    "tcgshop.phoneup.tier.basic": ("Básico", "Basic"),
    "tcgshop.phoneup.tier.plus": ("Plus", "Plus"),
    "tcgshop.phoneup.tier.pro": ("Pro", "Pro"),
    "tcgshop.phoneup.tier.ultra": ("Ultra", "Ultra"),
    "tcgshop.phoneup.tier.max": ("Max", "Max"),
    "tcgshop.phoneup.tier.elite": ("Élite", "Elite"),
    "tcgshop.phoneup.tier.masterball": ("MasterBall Edition", "MasterBall Edition"),
    # ventajas de cada nivel
    "tcgshop.phoneup.perks.basic": (
        "Mercado básico (sobres modernos baratos, muebles, bloques de tienda y herramientas), Mi tienda, Colección, Venta rápida, Amigos, Títulos, Logros y Ajustes.",
        "Basic Market (cheap modern packs, furniture, shop blocks and tools), My shop, Collection, Quick sell, Friends, Titles, Advancements and Settings."),
    "tcgshop.phoneup.perks.plus": (
        "App Precios · sobres de gama media · Mystery Box en el Mercado.",
        "Prices app · mid-range packs · Mystery Box in the Market."),
    "tcgshop.phoneup.perks.pro": (
        "Apps Expositores y Muebles · cajas de sobres en el Mercado · sobres de gama alta · −3% en el Mercado.",
        "Displays and Furniture apps · booster boxes in the Market · high-end packs · −3% in the Market."),
    "tcgshop.phoneup.perks.ultra": (
        "App CobbleGrade · peluches Poké · sobres especiales y de colección · venta rápida +3%.",
        "CobbleGrade app · Poké plushies · special and collector packs · quick sell +3%."),
    "tcgshop.phoneup.perks.max": (
        "App Liga TCG · Ruleta Poké · sobres antiguos raros · −6% en el Mercado.",
        "TCG League app · Poké Roulette · rare older packs · −6% in the Market."),
    "tcgshop.phoneup.perks.elite": (
        "Sobres vintage · el Mayorista trae 4 sets de cajas más y te hace un −3% · −8% en el Mercado · venta rápida +5%.",
        "Vintage packs · the Wholesaler brings 4 extra box sets and gives you −3% · −8% in the Market · quick sell +5%."),
    "tcgshop.phoneup.perks.masterball": (
        "TODO desbloqueado (también los sets reservados a tiendas de nivel alto) · −12% en el Mercado · Mayorista −5% · venta rápida +8% · 1 llamada al día: Coleccionista o Mayorista.",
        "EVERYTHING unlocked (even the sets reserved for high-tier shops) · −12% in the Market · Wholesaler −5% · quick sell +8% · 1 call per day: Collector or Wholesaler."),
    # interfaz
    "tcgshop.phoneup.requires": ("Requiere teléfono %s", "Requires %s phone"),
    "tcgshop.phoneup.locked_btn": ("Bloqueado", "Locked"),
    "tcgshop.phoneup.disabled": ("Las mejoras del teléfono están desactivadas en este servidor: lo tienes todo.",
                                 "Phone upgrades are disabled on this server: you have everything."),
    "tcgshop.phoneup.your_phone": ("TU TELÉFONO", "YOUR PHONE"),
    "tcgshop.phoneup.level": ("Nivel %s/%s", "Level %s/%s"),
    "tcgshop.phoneup.lv": ("Nv. %s", "Lv. %s"),
    "tcgshop.phoneup.legacy": ("✔ Apps de veterano", "✔ Veteran apps"),
    "tcgshop.phoneup.now_have": ("Ahora tienes", "You now have"),
    "tcgshop.phoneup.next": ("Siguiente: %s", "Next: %s"),
    "tcgshop.phoneup.current": ("Actual", "Current"),
    "tcgshop.phoneup.owned": ("✔ Tuyo", "✔ Owned"),
    "tcgshop.phoneup.confirm": ("¿Confirmar?", "Confirm?"),
    "tcgshop.phoneup.buy_tip": ("Mejorar a %s por ₽ %s (pulsa dos veces para confirmar)",
                                "Upgrade to %s for ₽ %s (click twice to confirm)"),
    "tcgshop.phoneup.cant_afford": ("Te faltan CobbleDollars: %s cuesta ₽ %s", "Not enough CobbleDollars: %s costs ₽ %s"),
    "tcgshop.phoneup.app_locked_body": ("La app %s se desbloquea con el teléfono %s. Mejóralo desde la app Mejoras.",
                                        "The %s app unlocks with the %s phone. Upgrade it from the Upgrades app."),
    "tcgshop.phoneup.see_upgrades": ("Ver mejoras", "See upgrades"),
    "tcgshop.phoneup.close": ("Cerrar", "Close"),
    "tcgshop.phoneup.bought": ("¡Teléfono mejorado a %s! (%s)", "Phone upgraded to %s! (%s)"),
    # llamada diaria
    "tcgshop.phoneup.call.title": ("Llamada diaria", "Daily call"),
    "tcgshop.phoneup.call.ready": ("Disponible hoy", "Available today"),
    "tcgshop.phoneup.call.used_today": ("Usada hoy · vuelve mañana", "Used today · back tomorrow"),
    "tcgshop.phoneup.call.off": ("Desactivada en este servidor", "Disabled on this server"),
    "tcgshop.phoneup.call.collector": ("Coleccionista", "Collector"),
    "tcgshop.phoneup.call.wholesaler": ("Mayorista", "Wholesaler"),
    "tcgshop.phoneup.call.help": (
        "Coleccionista: si estás en tu tienda, viene un cliente coleccionista de los que gastan mucho; si no, un Coleccionista ambulante se acerca a ti. Mayorista: aparece a tu lado con su puesto. Una llamada al día entre las dos.",
        "Collector: if you are at your shop, a big-spending collector customer comes by; otherwise a travelling Collector walks up to you. Wholesaler: shows up next to you with his stall. One call per day between both."),
    "tcgshop.phoneup.call.used": ("Ya has usado la llamada de hoy. Vuelve a intentarlo mañana.", "You already used today's call. Try again tomorrow."),
    "tcgshop.phoneup.call.already_near": ("Ya tienes uno cerca: búscalo antes de llamar a otro.", "There is already one nearby: find it before calling another."),
    "tcgshop.phoneup.call.no_room": ("No hay sitio libre a tu alrededor. Sal a un lugar despejado y vuelve a llamar.",
                                     "There is no free space around you. Move to an open spot and call again."),
    "tcgshop.phoneup.call.collector_shop": ("Has llamado a un coleccionista: va de camino a tu tienda con la cartera llena.",
                                            "You called a collector: they are on their way to your shop with a full wallet."),
    "tcgshop.phoneup.call.collector_near": ("Has llamado a un Coleccionista ambulante: ya está aquí.", "You called a travelling Collector: they are here."),
    "tcgshop.phoneup.call.wholesaler": ("Has llamado al Mayorista: ha montado su puesto a tu lado.", "You called the Wholesaler: he set up his stall next to you."),
}


def update(path, idx):
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    for k, v in KEYS.items():
        data[k] = v[idx]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    update(os.path.join(LANG, "es_es.json"), 0)
    update(os.path.join(LANG, "en_us.json"), 1)
    print("gen_lang_09_phone: %d claves" % len(KEYS))
