# -*- coding: utf-8 -*-
"""0.9 fix - area "shop": new texts from the review (capacity with events, blocked door).

Only adds / updates its own keys in es_es.json and en_us.json (idempotent, keeps the order of the rest).
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
LANG = os.path.join(HERE, "..", "src", "main", "resources", "assets", "tcgshop", "lang")

ES = {}
EN = {}


def k(key, es, en):
    ES[key] = es
    EN[key] = en


# ShopStatusScreen: capacity tooltip line when an event changes it (Mods.maxCustAdd)
k("tcgshop.status.capacity_event", "Eventos: %s ahora mismo", "Events: %s right now")
# ShopLife.queueGate: a customer gets tired of waiting because an event blocks the door (not due to capacity)
k("tcgshop.event.door_blocked_left", "%s se cansó de esperar ante la puerta bloqueada y se fue.",
  "%s got tired of waiting at the blocked door and left.")


def apply(name, data):
    path = os.path.join(LANG, name)
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    d.update(data)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write("\n")


if __name__ == "__main__":
    apply("es_es.json", ES)
    apply("en_us.json", EN)
    print("ok: %d keys" % len(ES))
