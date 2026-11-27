#!/usr/bin/env python3
"""
Matches each cobbletcg card with the equivalent card in the real engine (twinleaf.gg).
Input: engine/twinleaf_cards.json (exported by the engine) and the cobbletcg en_us lang.
Output: common/src/main/resources/data/tcgshop/tcgshop_ptcg/cards.json -> {"cobbletcg:<set>/cards/<id>": "<engine fullName>"}
Rules: same set code and number; otherwise (secret rarities with a different number), same name within the set;
Trainers and Energies also match by name in any set (they are reprints with the same effect).
Usage: python tools/gen_ptcg_map.py   (cards: catalog from ../masterball-api)
"""
import json
import re
import sys
import zipfile
from collections import defaultdict, Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/data/tcgshop/tcgshop_ptcg/cards.json"
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

    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from fetch_prices import load_cards  # MasterBall Traders API catalog
    cards = defaultdict(list)  # set -> [(id, code, number, name)]
    for c in load_cards():
        if c["num"] > 0 and not c["promo"]:
            cards[c["set"]].append((c["id"], c["code"], str(c["num"]), c["name"]))

    out, stats = {}, {}
    for cset, lst in cards.items():
        # engine code for this set: the one with the most number + name matches
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
                anyset = [c for c in by_name_any.get(norm(name), []) if c["st"] in (2, 3)]  # Trainer / Energy
                t = anyset[0] if anyset else None
            if t:
                out[cid] = t["f"]
                ok += 1
        stats[cset] = (tcode, ok, len(lst))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, indent=0, sort_keys=True), encoding="utf-8")
    # identical copy for the client (duel screen)
    (ROOT / "common/src/main/resources/assets/tcgshop/ptcg/cards.json").write_text(json.dumps(out, indent=0, sort_keys=True), encoding="utf-8")
    total = sum(v[2] for v in stats.values())
    for k, (code, ok, n) in sorted(stats.items(), key=lambda x: x[1][1] / max(1, x[1][2])):
        print(f"{k:22s} -> {code:4s} {ok:4d}/{n:4d}")
    print("TOTAL", len(out), "/", total)


if __name__ == "__main__":
    main()
