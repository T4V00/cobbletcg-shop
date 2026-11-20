#!/usr/bin/env python3
"""
Empareja cada carta de cobbletcg con la carta equivalente del motor real (twinleaf.gg).
Entrada: engine/twinleaf_cards.json (lo exporta el motor) y el lang en_us de cobbletcg.
Salida: src/main/resources/data/tcgshop/tcgshop_ptcg/cards.json -> {"cobbletcg:<set>/cards/<id>": "<fullName del motor>"}
Reglas: mismo código de set y número; si no (rarezas secretas con otro número), mismo nombre dentro del set;
los Entrenadores y Energías también valen por nombre en cualquier set (son reimpresiones con el mismo efecto).
Uso: python tools/gen_ptcg_map.py <cobbletcg.jar>
"""
import json
import re
import sys
import zipfile
from collections import defaultdict, Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/data/tcgshop/tcgshop_ptcg/cards.json"
RARITY_SUFFIX = re.compile(r"\s+(SIR|IR|FA|HR|UR|SR|AR|TG|GG|SAR|CHR|CSR|ACE|PR|RR|RRR|S|SSR|Full Art|Secret|Gold|Rainbow)$", re.I)


def norm(s):
    s = s.replace("’", "'").replace("é", "e").replace("É", "E")
    s = RARITY_SUFFIX.sub("", s.strip())
    return re.sub(r"[^a-z0-9']", "", s.lower())


def main():
    tl = json.loads((ROOT / "engine/twinleaf_cards.json").read_text(encoding="utf-8"))
    by_code_num = {}
    by_code_name = defaultdict(list)
    by_name_any = defaultdict(list)
    for c in tl:
        by_code_num[(c["s"], str(c["num"]).lstrip("0"))] = c
        by_code_name[(c["s"], norm(c["n"]))].append(c)
        by_name_any[norm(c["n"])].append(c)
    codes = set(c["s"] for c in tl)

    z = zipfile.ZipFile(sys.argv[1])
    lang = json.loads(z.read("assets/cobbletcg/lang/en_us.json"))
    cards = defaultdict(list)  # set cobbletcg -> [(id, code, num, name)]
    for k, v in lang.items():
        m = re.match(r"item\.cobbletcg\.([^.]+)\.cards\.(.+)$", k)
        if not m:
            continue
        mm = re.match(r"^\s*([A-Z0-9]+)\s+(\d+)\s+(.*?)\s*$", v)
        if not mm:
            continue
        cards[m.group(1)].append((f"cobbletcg:{m.group(1)}/cards/{m.group(2)}", mm.group(1), mm.group(2).lstrip("0"), mm.group(3)))

    out, stats = {}, {}
    for cset, lst in cards.items():
        # código del motor para este set: el que más coincide por número + nombre
        votes = Counter()
        for _, code, num, name in lst:
            for cand in [code] + [c for c in codes if c != code]:
                t = by_code_num.get((cand, num))
                if t and norm(t["n"]) == norm(name):
                    votes[cand] += 1
                    break
        tcode = votes.most_common(1)[0][0] if votes else lst[0][1]
        ok = 0
        for cid, code, num, name in lst:
            t = by_code_num.get((tcode, num))
            if not (t and norm(t["n"]) == norm(name)):
                same = by_code_name.get((tcode, norm(name)))
                t = sorted(same, key=lambda c: int(re.sub(r"\D", "", str(c["num"])) or 0))[0] if same else None
            if t is None:
                anyset = [c for c in by_name_any.get(norm(name), []) if c["st"] in (2, 3)]  # Entrenador / Energía
                t = anyset[0] if anyset else None
            if t:
                out[cid] = t["f"]
                ok += 1
        stats[cset] = (tcode, ok, len(lst))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, indent=0, sort_keys=True), encoding="utf-8")
    total = sum(v[2] for v in stats.values())
    for k, (code, ok, n) in sorted(stats.items(), key=lambda x: x[1][1] / max(1, x[1][2])):
        print(f"{k:22s} -> {code:4s} {ok:4d}/{n:4d}")
    print("TOTAL", len(out), "/", total)


if __name__ == "__main__":
    main()
