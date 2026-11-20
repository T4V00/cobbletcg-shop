#!/usr/bin/env python3
"""
Asocia cada carta de cobbletcg con su Pokémon (para títulos y logros por especie).

Lee los nombres de especie de Cobblemon (data/cobblemon/species/**.json) y el nombre de cada carta del lang
de cobbletcg ("PAL 269  Iono SIR", "MEW 151  Mew ex", "ASC 1  Erika's Oddish", "ROS 61  Mega Rayquaza EX").
Busca el nombre de especie más largo contenido en el nombre de la carta (las cartas de Entrenador / Energía
no tienen especie).

Escribe src/main/resources/data/tcgshop/tcgshop_species/cards.json  ->  {"cobbletcg:...": "pikachu", ...}
Uso: python tools/gen_card_species.py <cobblemon.jar> <cobbletcg.jar>
"""
import json
import re
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/tcgshop/tcgshop_species/cards.json"


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower().replace("é", "e").replace("♀", "f").replace("♂", "m"))


def main():
    cob = zipfile.ZipFile(sys.argv[1])
    species = {}  # nombre normalizado -> id
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

    tcg = zipfile.ZipFile(sys.argv[2])
    lang = json.loads(tcg.read("assets/cobbletcg/lang/en_us.json").decode("utf-8"))
    real = {}
    for n in tcg.namelist():
        if n.startswith("data/cobbletcg/loot_table/") and n.endswith(".json"):
            for cid in re.findall(r'"(cobbletcg:[a-z0-9_]+/cards/[a-z0-9_]+)"', tcg.read(n).decode("utf-8", "ignore")):
                real.setdefault(cid.rsplit("/", 1)[1], cid)

    out = {}
    for key, value in lang.items():
        m = re.match(r"^item\.cobbletcg\.([a-z0-9_]+)\.cards\.([a-z0-9_]+)$", key)
        nm = re.match(r"^\s*[A-Z0-9]+\s+\d+\s+(.+?)\s*$", value)
        if not m or not nm:
            continue
        cid = real.get(m.group(2), f"cobbletcg:{m.group(1)}/cards/{m.group(2)}")
        text = norm(nm.group(1).replace("Mega ", "").replace("Radiant ", ""))
        for s in names:
            if len(s) >= 3 and s in text:
                # evita falsos positivos con nombres muy cortos dentro de otras palabras (p.ej. "mew" en "mewtwo")
                out[cid] = species[s]
                break
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(dict(sorted(out.items())), indent=0), encoding="utf-8")
    from collections import Counter
    c = Counter(out.values())
    print(f"{len(out)} cartas con especie, {len(c)} especies. Top: {c.most_common(8)}")


if __name__ == "__main__":
    main()
