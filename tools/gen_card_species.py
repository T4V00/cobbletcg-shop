#!/usr/bin/env python3
"""
Links each cobbletcg card to its Pokémon (for per-species titles and achievements).

Reads the Cobblemon species names (data/cobblemon/species/**.json) and each card's name from the cobbletcg
lang ("PAL 269  Iono SIR", "MEW 151  Mew ex", "ASC 1  Erika's Oddish", "ROS 61  Mega Rayquaza EX").
Looks for the longest species name contained in the card name (Trainer / Energy cards
have no species).

Writes common/src/main/resources/data/tcgshop/tcgshop_species/cards.json  ->  {"cobbletcg:...": "pikachu", ...}
Usage: python tools/gen_card_species.py <cobblemon.jar>   (cards: catalog from ../masterball-api)
"""
import json
import re
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "common/src/main/resources/data/tcgshop/tcgshop_species/cards.json"


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower().replace("é", "e").replace("♀", "f").replace("♂", "m"))


def main():
    cob = zipfile.ZipFile(sys.argv[1])
    species = {}  # normalized name -> id
    for n in cob.namelist():
        if re.match(r"^data/cobblemon/species/[^/]+/[^/]+\.json$", n):
            try:
                d = json.loads(cob.read(n))
            except Exception:
                continue
            name = d.get("name")
            if name:
                species[norm(name)] = name.lower().replace(" ", "_")
    names = sorted(species, key=len, reverse=True)

    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from fetch_prices import load_cards  # MasterBall Traders API catalog
    out = {}
    for c in load_cards():
        text = norm(c["name"].replace("Mega ", "").replace("Radiant ", ""))
        for s in names:
            if len(s) >= 3 and s in text:
                # avoids false positives with very short names inside other words (e.g. "mew" in "mewtwo")
                out[c["id"]] = species[s]
                break
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(dict(sorted(out.items())), indent=0), encoding="utf-8")
    from collections import Counter
    c = Counter(out.values())
    print(f"{len(out)} cards with a species, {len(c)} species. Top: {c.most_common(8)}")


if __name__ == "__main__":
    main()
