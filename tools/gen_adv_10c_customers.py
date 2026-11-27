#!/usr/bin/env python3
"""1.0 beta 3 (customers area): advancement JSONs for the unique customers, thief tiers and guard upgrades.

Writes ONLY data/tcgshop/advancement/<id>.json (granted by code: "impossible" criterion, like tools/gen_advancements.py).
Their texts (advancement.tcgshop.<id>.title / .desc) live in tools/gen_lang_10c_customers.py.

Usage: python tools/gen_adv_10c_customers.py
"""
import json
from pathlib import Path

ADV = Path(__file__).resolve().parent.parent / "common/src/main/resources/data/tcgshop/advancement"

# id, parent, icon, frame
ADVS = [
    ("meet_fixed", "whale_customer", "minecraft:name_tag", "task"),
    ("meet_all_fixed", "meet_fixed", "minecraft:nether_star", "challenge"),
    ("aurelio_tip", "meet_fixed", "minecraft:gold_ingot", "goal"),
    ("catch_vinnie", "catch_thief", "minecraft:lead", "goal"),
    ("guard_upgrade", "hire_guard", "minecraft:iron_chestplate", "task"),
    ("guard_maxed", "guard_upgrade", "minecraft:diamond_chestplate", "goal"),
    ("stop_robbery", "catch_thief", "minecraft:iron_sword", "goal"),
    ("stop_heist", "stop_robbery", "minecraft:diamond_sword", "challenge"),
]


def main():
    ADV.mkdir(parents=True, exist_ok=True)
    for aid, parent, icon, frame in ADVS:
        d = {
            "display": {
                "icon": {"id": icon},
                "title": {"translate": f"advancement.tcgshop.{aid}.title"},
                "description": {"translate": f"advancement.tcgshop.{aid}.desc"},
                "frame": frame,
                "show_toast": True,
                "announce_to_chat": frame == "challenge",
                "hidden": False,
            },
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "parent": f"tcgshop:{parent}",
        }
        (ADV / f"{aid}.json").write_text(json.dumps(d, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"{len(ADVS)} advancements written")


if __name__ == "__main__":
    main()
