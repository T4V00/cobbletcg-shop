#!/usr/bin/env python3
"""
Repara los tags de rareza rotos de cobbletcg.

En cobbletcg 1.6.0 algunos tags por set (p.ej. creign/alternative.json, crising/double.json) se quedaron
con el contenido de la plantilla ("cobbletcg:colección/cards/prefijoitem008") y Minecraft no los carga,
así que esas cartas no tienen rareza. Las loot tables del set sí tienen las listas correctas.

Este script detecta los tags inválidos y genera la versión correcta en
src/main/resources/data/cobbletcg/tags/item/<set>/<tag>.json (los tags de datapacks se fusionan con los del mod).

Uso: python tools/fix_rarity_tags.py <ruta a cobbletcg-1.6.0.jar>
"""
import json
import re
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/cobbletcg/tags/item"
# tag por set -> loot table de cartas del set
TAG_TO_LOOT = {
    "commons": "com-uncom-rare",
    "double": "doublerare",
    "alternative": "alternativerare",
    "ilustration": "ilustrationrare",
    "special": "specialilustrationrare",
    "hyper": "hyperrare",
}
VALID_ID = re.compile(r"^#?[a-z0-9_.-]+:[a-z0-9_./-]+$")


def main():
    z = zipfile.ZipFile(sys.argv[1])
    names = set(z.namelist())
    fixed = 0
    for name in sorted(names):
        m = re.match(r"^data/cobbletcg/tags/item/([a-z0-9_]+)/([a-z]+)\.json$", name)
        if not m or m.group(2) not in TAG_TO_LOOT:
            continue
        set_id, tag = m.groups()
        try:
            values = json.loads(z.read(name).decode("utf-8"))["values"]
            ok = all(VALID_ID.match(v if isinstance(v, str) else v["id"]) for v in values)
        except Exception:  # noqa: BLE001
            ok = False
        if ok:
            continue
        loot = f"data/cobbletcg/loot_table/{set_id}/cards/{TAG_TO_LOOT[tag]}.json"
        if loot not in names:
            print(f"!! {set_id}/{tag}: tag roto y sin loot table {loot}")
            continue
        ids = sorted(set(re.findall(r'"(cobbletcg:[a-z0-9_]+/cards/[a-z0-9_]+)"', z.read(loot).decode("utf-8"))))
        out = OUT / set_id / f"{tag}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({"replace": False, "values": ids}, indent=1), encoding="utf-8")
        print(f"reparado {set_id}/{tag}: {len(ids)} cartas")
        fixed += 1
    print(f"{fixed} tags reparados")


if __name__ == "__main__":
    main()
