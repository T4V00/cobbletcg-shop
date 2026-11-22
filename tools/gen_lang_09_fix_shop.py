# -*- coding: utf-8 -*-
"""0.9 fix - area "shop": textos nuevos de la revisión (aforo con eventos, puerta bloqueada).

Sólo añade / actualiza sus propias claves en es_es.json y en_us.json (idempotente, conserva el orden del resto).
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


# ShopStatusScreen: línea del tooltip de aforo cuando un evento lo cambia (Mods.maxCustAdd)
k("tcgshop.status.capacity_event", "Eventos: %s ahora mismo", "Events: %s right now")
# ShopLife.queueGate: un cliente se cansa de esperar porque un evento bloquea la puerta (no por aforo)
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
    print("ok: %d claves" % len(ES))
