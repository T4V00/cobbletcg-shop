#!/usr/bin/env python3
"""1.1 texts (second batch), Group B review fixes: the fourth charm option (small talk: button, description, help
text and its NPC lines in tools/npc_lines_v4_f.py) and help texts for the charm buttons when the customer is selling to
you (a success lowers their price instead of raising a tip).
Code: shop/Charisma.java, entity/CustomerEntity.java, client/screen/DealScreen.java, entity/NpcLines.java.

Usage: python tools/gen_lang_12_fixb.py  (idempotent; runs gen_lang_12_customers.py with the new line module first)
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LANG = HERE.parent / "common/src/main/resources/assets/tcgshop/lang"
GEN_NPC = HERE / "gen_npc_lines.py"
NEW_MODULE = "npc_lines_v4_f"

T = {
    "tcgshop.haggle.a.smalltalk": ("☁ Charla · %s", "☁ Small talk · %s"),
    "tcgshop.haggle.d.smalltalk": ("Propina chica, muy segura", "Tiny tip, very safe"),
    "tcgshop.haggle.d.smalltalk_sell": ("Descuento chico, muy seguro", "Tiny discount, very safe"),
    "tcgshop.haggle.tip.smalltalk": (
        "Platica un rato. Casi siempre funciona, pero la propina sube poquito. A los clientes casuales les encanta; a los desesperados no tanto.",
        "Make some small talk. Almost always works, but the tip only goes up a little. Casual customers love it; desperate ones less so."),
    "tcgshop.haggle.tip.joke_sell": (
        "Cuenta un chiste. Si funciona, baja mucho su precio; si no, se molesta un poco. Depende de tu carisma y de su personalidad.",
        "Tell a joke. If it lands, their price drops a lot; if not, they get a little annoyed. Depends on your charisma and their personality."),
    "tcgshop.haggle.tip.compliment_sell": (
        "Hazle un cumplido. Suele funcionar, pero el descuento es chico. A los negociadores no les gustan los halagos.",
        "Pay them a compliment. Usually works but gives a small discount. Negotiators dislike flattery."),
    "tcgshop.haggle.tip.story_sell": (
        "Cuenta una historia de la tienda. A coleccionistas y expertos les encanta y bajan su precio; si los aburres, pierden paciencia.",
        "Tell a shop story. Collectors and experts love it and lower their price; if it bores them, they lose patience."),
    "tcgshop.haggle.tip.smalltalk_sell": (
        "Platica un rato. Casi siempre funciona, pero su precio baja poquito. A los clientes casuales les encanta; a los desesperados no tanto.",
        "Make some small talk. Almost always works, but their price only drops a little. Casual customers love it; desperate ones less so."),
}


def register_module():
    """Adds the new line module to gen_lang_12_customers.py and gen_npc_lines.py (so their later runs keep the lines)."""
    for path, old, new in (
            (HERE / "gen_lang_12_customers.py", '"npc_lines_v4_e")', f'"npc_lines_v4_e", "{NEW_MODULE}")'),
            (GEN_NPC, '"npc_lines_v4_e"):', f'"npc_lines_v4_e", "{NEW_MODULE}"):')):
        raw = path.read_bytes().decode("utf-8")
        if NEW_MODULE in raw:
            continue
        if old not in raw:
            raise SystemExit(f"cannot register {NEW_MODULE} in {path.name}")
        path.write_bytes(raw.replace(old, new, 1).encode("utf-8"))


def main():
    register_module()
    sys.path.insert(0, str(HERE))
    import gen_lang_12_customers as cust
    if NEW_MODULE not in cust.MODULES:
        cust.MODULES = cust.MODULES + (NEW_MODULE,)
    cust.main()  # merges every v4 module (the small-talk lines included) and updates NpcLines.COUNTS
    pe, pn = LANG / "es_es.json", LANG / "en_us.json"
    es = json.loads(pe.read_text(encoding="utf-8"))
    en = json.loads(pn.read_text(encoding="utf-8"))
    for k, (a, b) in T.items():
        es[k], en[k] = a, b
    pe.write_text(json.dumps(es, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    pn.write_text(json.dumps(en, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
