#!/usr/bin/env python3
"""
Repara los logros rotos de cobbletcg 1.6.0 (sin modificar su jar).

Varios logros de colección no cargan por errores en sus archivos: comas dentro de los ids
("cobbletcg:oflames/cards/ofl231,"), comas sobrantes, o ítems que no existen (p.ej. celebr25/riped4_celebr25).
Minecraft los descarta enteros, así que nunca se pueden conseguir.

Este script genera versiones corregidas en src/main/resources/data/cobbletcg/advancement/, que sustituyen a las
originales (mismo id). Necesita la lista de ítems registrados: en el servidor de desarrollo, ejecutar
`/tcgshop debug dumpitems` (crea run/cobbletcg_items.txt).

Uso: python tools/fix_cobbletcg_advancements.py <cobbletcg.jar> <cobbletcg_items.txt>
"""
import json
import re
import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/cobbletcg/advancement"


def repair_text(s):
    s = re.sub(r'"(cobbletcg:[^"]*?)[,\s]+"', r'"\1"', s)   # comas/espacios dentro del id
    s = re.sub(r',(\s*[}\]])', r'\1', s)                      # comas sobrantes
    return s


def main():
    jar, items_file = sys.argv[1], sys.argv[2]
    valid = set(Path(items_file).read_text(encoding="utf-8").split())
    z = zipfile.ZipFile(jar)
    OUT.mkdir(parents=True, exist_ok=True)
    fixed = 0
    for name in sorted(z.namelist()):
        m = re.match(r"^data/cobbletcg/advancement/([a-z0-9_]+)\.json$", name)
        if not m:
            continue
        text = z.read(name).decode("utf-8")
        broken = False
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            broken = True
            data = json.loads(repair_text(text))
        # ids con caracteres inválidos o ítems inexistentes
        removed = []
        crits = data.get("criteria", {})
        for cname in list(crits):
            cond = crits[cname].get("conditions", {})
            for pred in cond.get("items", []):
                ids = pred.get("items")
                if isinstance(ids, str):
                    ids = [ids]
                    pred["items"] = ids
                if not isinstance(ids, list):
                    continue
                clean = [i.strip().rstrip(",") for i in ids]
                ok = [i for i in clean if i in valid]
                if len(ok) != len(ids) or clean != ids:
                    broken = True
                pred["items"] = ok
            if any(isinstance(p.get("items"), list) and not p["items"] for p in cond.get("items", [])):
                del crits[cname]
                removed.append(cname)
        if removed and "requirements" in data:
            data["requirements"] = [[c for c in grp if c not in removed] for grp in data["requirements"]]
            data["requirements"] = [g for g in data["requirements"] if g]
        icon = data.get("display", {}).get("icon", {})
        if icon.get("id") and icon["id"] not in valid and icon["id"].startswith("cobbletcg:"):
            icon["id"] = "cobbletcg:mew151/sealed_mew151"
            broken = True
        if broken:
            (OUT / f"{m.group(1)}.json").write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
            fixed += 1
            print(f"reparado {m.group(1)} (criterios quitados: {len(removed)})")
    print(f"{fixed} logros reparados")


if __name__ == "__main__":
    main()
