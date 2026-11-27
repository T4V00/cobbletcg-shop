#!/usr/bin/env python3
"""
Base stats and type of each species (for the competitive TCG mode: HP, damage, retreat and type of each card).
Reads data/cobblemon/species/**.json from the Cobblemon jar and writes
common/src/main/resources/data/tcgshop/tcgshop_stats/species.json -> {"pikachu": [hp, atk, def, spa, spd, spe, "electric"], ...}
The key is the same one gen_card_species.py uses (lowercase name with "_" instead of spaces).
Usage: python tools/gen_species_stats.py <cobblemon.jar>
"""
import json
import re
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/data/tcgshop/tcgshop_stats/species.json"


def main():
    z = zipfile.ZipFile(sys.argv[1])
    out = {}
    for n in z.namelist():
        if not re.match(r"^data/cobblemon/species/[^/]+/[^/]+\.json$", n):
            continue
        try:
            d = json.loads(z.read(n))
        except Exception:
            continue
        name, bs = d.get("name"), d.get("baseStats")
        if not name or not bs:
            continue
        key = name.lower().replace(" ", "_")
        out[key] = [bs.get("hp", 50), bs.get("attack", 50), bs.get("defence", 50), bs.get("special_attack", 50),
                    bs.get("special_defence", 50), bs.get("speed", 50), d.get("primaryType", "normal")]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, separators=(",", ":"), sort_keys=True), encoding="utf-8")
    print("species:", len(out))


if __name__ == "__main__":
    main()
