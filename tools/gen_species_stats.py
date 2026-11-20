#!/usr/bin/env python3
"""
Estadísticas base y tipo de cada especie (para el modo competitivo TCG: PS, daño, retirada y tipo de cada carta).
Lee data/cobblemon/species/**.json del jar de Cobblemon y escribe
src/main/resources/data/tcgshop/tcgshop_stats/species.json -> {"pikachu": [hp, atk, def, spa, spd, spe, "electric"], ...}
La clave es la misma que usa gen_card_species.py (nombre en minúsculas con "_" en vez de espacios).
Uso: python tools/gen_species_stats.py <cobblemon.jar>
"""
import json
import re
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/tcgshop/tcgshop_stats/species.json"


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
    print("especies:", len(out))


if __name__ == "__main__":
    main()
