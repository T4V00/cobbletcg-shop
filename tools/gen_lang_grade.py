#!/usr/bin/env python3
"""CobbleGrade texts (card grading). Merges the keys into es_es / en_us."""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

ES = {
    "tcgshop.phone.grade": "CobbleGrade",
    "tcgshop.grade.slogan": "Gradeo profesional de cartas",
    "tcgshop.grade.tab.send": "Enviar cartas",
    "tcgshop.grade.tab.orders": "Mis pedidos",
    "tcgshop.grade.tab.odds": "Probabilidades",
    "tcgshop.grade.no_cards": "No tienes cartas sin gradear en el inventario. Saca algunas del Binder General para enviarlas.",
    "tcgshop.grade.service": "Servicio",
    "tcgshop.grade.service.economy": "Económico",
    "tcgshop.grade.service.standard": "Estándar",
    "tcgshop.grade.service.express": "Exprés",
    "tcgshop.grade.selected": "Cartas: %s / %s",
    "tcgshop.grade.total": "Total: %s",
    "tcgshop.grade.send": "Enviar a gradear",
    "tcgshop.grade.send_note": "Se envía una carta de cada hueco elegido. La nota se revela al recoger el pedido.",
    "tcgshop.grade.fee_one": "Gradeo: %s",
    "tcgshop.grade.no_orders": "No tienes pedidos en curso. Elige cartas en «Enviar cartas».",
    "tcgshop.grade.order": "Pedido #%s · %s cartas",
    "tcgshop.grade.collect": "Recoger",
    "tcgshop.grade.left": "Faltan %s",
    "tcgshop.grade.time_dh": "%s d %s h",
    "tcgshop.grade.time_hm": "%s h %s min",
    "tcgshop.grade.odds_title": "Probabilidad de cada nota",
    "tcgshop.grade.odds_note": "Cada décima dentro de un tramo es igual de probable. A la derecha, cuánto multiplica la nota el valor de mercado de la carta.",
    "tcgshop.grade.none_selected": "Ninguna de las cartas elegidas se puede gradear.",
    "tcgshop.grade.sent": "Has enviado %s cartas a CobbleGrade (servicio %s) por %s.",
    "tcgshop.grade.not_ready": "Ese pedido todavía no está listo.",
    "tcgshop.grade.ready": "¡CobbleGrade ha terminado tu pedido de %s cartas! Recógelo desde el teléfono.",
    "tcgshop.grade.cert": "Certificado #%s",
    "tcgshop.grade.value_now": "Valor: %s (%s)",
    "tcgshop.grade.summary": "%s cartas gradeadas",
    "tcgshop.grade.summary_value": "Valor: %s → %s",
    "tcgshop.grade.best": "Mejor nota: %s",
    "tcgshop.grade.label.10": "GEM MINT",
    "tcgshop.grade.label.95": "MINT+",
    "tcgshop.grade.label.9": "MINT",
    "tcgshop.grade.label.8": "NM-MT",
    "tcgshop.grade.label.7": "NEAR MINT",
    "tcgshop.grade.label.6": "EX-MT",
    "tcgshop.grade.label.5": "EXCELENTE",
    "tcgshop.grade.label.4": "VG-EX",
    "tcgshop.grade.label.3": "MUY BUENA",
    "tcgshop.grade.label.2": "BUENA",
    "tcgshop.grade.label.1": "POBRE",
}

EN = {
    "tcgshop.phone.grade": "CobbleGrade",
    "tcgshop.grade.slogan": "Professional card grading",
    "tcgshop.grade.tab.send": "Send cards",
    "tcgshop.grade.tab.orders": "My orders",
    "tcgshop.grade.tab.odds": "Odds",
    "tcgshop.grade.no_cards": "You have no ungraded cards in your inventory. Take some out of your General Binder to send them.",
    "tcgshop.grade.service": "Service",
    "tcgshop.grade.service.economy": "Economy",
    "tcgshop.grade.service.standard": "Standard",
    "tcgshop.grade.service.express": "Express",
    "tcgshop.grade.selected": "Cards: %s / %s",
    "tcgshop.grade.total": "Total: %s",
    "tcgshop.grade.send": "Send for grading",
    "tcgshop.grade.send_note": "One card is sent from each chosen slot. The grade is revealed when you collect the order.",
    "tcgshop.grade.fee_one": "Grading: %s",
    "tcgshop.grade.no_orders": "No orders in progress. Pick cards in \"Send cards\".",
    "tcgshop.grade.order": "Order #%s · %s cards",
    "tcgshop.grade.collect": "Collect",
    "tcgshop.grade.left": "%s left",
    "tcgshop.grade.time_dh": "%s d %s h",
    "tcgshop.grade.time_hm": "%s h %s min",
    "tcgshop.grade.odds_title": "Chance of each grade",
    "tcgshop.grade.odds_note": "Every tenth inside a range is equally likely. On the right, how much the grade multiplies the card's market value.",
    "tcgshop.grade.none_selected": "None of the chosen cards can be graded.",
    "tcgshop.grade.sent": "You sent %s cards to CobbleGrade (%s service) for %s.",
    "tcgshop.grade.not_ready": "That order isn't ready yet.",
    "tcgshop.grade.ready": "CobbleGrade finished your order of %s cards! Collect it from your phone.",
    "tcgshop.grade.cert": "Certificate #%s",
    "tcgshop.grade.value_now": "Value: %s (%s)",
    "tcgshop.grade.summary": "%s cards graded",
    "tcgshop.grade.summary_value": "Value: %s → %s",
    "tcgshop.grade.best": "Best grade: %s",
    "tcgshop.grade.label.10": "GEM MINT",
    "tcgshop.grade.label.95": "MINT+",
    "tcgshop.grade.label.9": "MINT",
    "tcgshop.grade.label.8": "NM-MT",
    "tcgshop.grade.label.7": "NEAR MINT",
    "tcgshop.grade.label.6": "EX-MT",
    "tcgshop.grade.label.5": "EXCELLENT",
    "tcgshop.grade.label.4": "VG-EX",
    "tcgshop.grade.label.3": "VERY GOOD",
    "tcgshop.grade.label.2": "GOOD",
    "tcgshop.grade.label.1": "POOR",
}


def main():
    assert set(ES) == set(EN), set(ES) ^ set(EN)
    for name, add in (("es_es.json", ES), ("en_us.json", EN)):
        p = LANG / name
        d = json.loads(p.read_text(encoding="utf-8"))
        d.update(add)
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
    print("CobbleGrade texts:", len(ES))


if __name__ == "__main__":
    main()
