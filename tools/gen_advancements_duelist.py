#!/usr/bin/env python3
"""1.0 Wandering Duelist advancement: writes data/tcgshop/advancement/duelist_win.json (same format as
tools/gen_advancements.py; granted by code with CardAdvancements.grant). Its texts are in tools/gen_lang_10c_duelist.py.

Usage: python tools/gen_advancements_duelist.py
"""
import json
from pathlib import Path

ADV = Path(__file__).resolve().parent.parent / "common/src/main/resources/data/tcgshop/advancement"

# id, parent, icon, frame
ADVS = [
    ("duelist_win", "duel_house_win", "tcgshop:play_table", "goal"),
]


def main():
    ADV.mkdir(parents=True, exist_ok=True)
    for aid, parent, icon, frame in ADVS:
        data = {
            "display": {
                "icon": {"id": icon},
                "title": {"translate": f"advancement.tcgshop.{aid}.title"},
                "description": {"translate": f"advancement.tcgshop.{aid}.desc"},
                "frame": frame,
                "show_toast": True,
                "announce_to_chat": False,
                "hidden": False,
            },
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
            "parent": f"tcgshop:{parent}",
        }
        (ADV / f"{aid}.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(len(ADVS), "advancements")


if __name__ == "__main__":
    main()
