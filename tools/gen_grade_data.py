#!/usr/bin/env python3
"""
Per-card data for the Grade system (GradeModel). No invented quantities: everything comes from
  * the user's popularity ranking (Popularity.txt, 1,025 Pokémon, #1 = Charizard),
  * the card type from the real TCG engine (engine/twinleaf_cards.json: st 1 Pokémon / 2 Trainer / 3 Energy;
    tt 0 Item, 1 Supporter, 2 Stadium, 3 Tool) via data/tcgshop/tcgshop_ptcg/cards.json,
  * real competitive usage (engine/meta_decks.json: copies in meta decks),
  * reprints of the same Trainer/Energy in the engine (versatility).
Output: common/src/main/resources/data/tcgshop/tcgshop_grade/cards.json
  { id: {"c": category, "r": rank (Pokémon only), "k": usage 0-1, "v": versatility 0-1} }
and tools/grade_unmatched.txt with the doubtful cases.
Usage: python tools/gen_grade_data.py <Popularity.txt>   (cards: catalog from ../masterball-api)
"""
import json
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fetch_prices import load_cards  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "common/src/main/resources/data/tcgshop/tcgshop_grade/cards.json"
LOG = ROOT / "tools/grade_unmatched.txt"
TT = {0: "item", 1: "supporter", 2: "stadium", 3: "tool"}


def words(s):
    s = s.replace("’", "'").replace("'s ", " ").replace("'", "")
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    s = s.replace("♀", " f").replace("♂", " m").replace("'s ", " ")
    return re.sub(r"[^a-z0-9]+", " ", s).split()


def load_popularity(path):
    ranks = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\s*(\d+)\.\s*(.+?)\s*$", line)
        if m:
            ranks[tuple(words(m.group(2)))] = int(m.group(1))
    return ranks


def find_pokemon(name, ranks, maxlen):
    """Pokémon present in the name as whole words (prefixes/suffixes like Alolan, ex, VMAX are left out on their own)."""
    w = words(name)
    found = []
    for i in range(len(w)):
        for n in range(maxlen, 0, -1):
            key = tuple(w[i:i + n])
            if key not in ranks and n == 1 and key[0].endswith("ex") and (key[0][:-2],) in ranks:
                key = (key[0][:-2],)  # "Wigglytuffex"
            if key in ranks:
                found.append((ranks[key], " ".join(key)))
                break
    return found


def main():
    pop = sys.argv[-1]  # before: <jar> <Popularity.txt>; now <Popularity.txt> is enough
    jar = None
    ranks = load_popularity(pop)
    assert len(ranks) >= 1000, f"incomplete ranking: {len(ranks)}"
    maxlen = max(len(k) for k in ranks)
    # single words that are Pokémon but also common words in card names
    risky = {("mew",): False}
    tl = {c["f"]: c for c in json.loads((ROOT / "engine/twinleaf_cards.json").read_text(encoding="utf-8"))}
    eng_map = json.loads((ROOT / "common/src/main/resources/data/tcgshop/tcgshop_ptcg/cards.json").read_text(encoding="utf-8"))
    meta = json.loads((ROOT / "engine/meta_decks.json").read_text(encoding="utf-8"))
    use = Counter()
    for d in meta:
        for f in d["cards"]:
            use[tl[f]["n"] if f in tl else re.sub(r"\s+\S+$", "", f)] += 1
    max_use = max(use.values())
    by_name = {}
    for t in tl.values():
        by_name.setdefault(t["n"].replace("’", "'"), t)
    prints = Counter(c["n"] for c in tl.values())
    max_prints = max(prints[c["n"]] for c in tl.values() if c["st"] != 1)

    out, log = {}, []
    for c in load_cards(jar):
        cid = c["id"]
        e = tl.get(eng_map.get(cid, "")) or by_name.get(re.sub(r"\s+(SIR|IR|SR|HR|UR|ETB|UPC|PC|Promo)$", "", c["name"]).replace("’", "'"))
        name = e["n"] if e else c["name"]
        if e:
            cat = "pokemon" if e["st"] == 1 else TT.get(e.get("tt"), "item") if e["st"] == 2 else \
                ("energy_basic" if not e.get("tt") and name.endswith("Energy") and len(name.split()) == 2 else "energy_special")
        else:
            cat = None
        rec = {}
        if cat in (None, "pokemon"):
            hits = find_pokemon(name, ranks, maxlen) or find_pokemon(c["name"], ranks, maxlen)
            if hits:
                rec["r"] = min(hits)[0]
                cat = "pokemon"
                if len({h[1] for h in hits}) > 1:
                    log.append(f"several Pokémon (using the most popular): {cid} '{name}' -> {sorted(set(hits))}")
            elif cat == "pokemon":
                log.append(f"unidentified Pokémon (neutral popularity): {cid} '{name}'")
            else:
                cat = "unknown"
                log.append(f"unknown category (no engine data): {cid} '{c['name']}'")
        rec["c"] = cat
        rec["k"] = round(use.get(name, 0) / max_use, 4)
        if cat != "pokemon":
            rec["v"] = round(min(1.0, (prints.get(name, 1) - 1) / max(1, max_prints - 1)), 4)
        out[cid] = rec

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(dict(sorted(out.items())), separators=(",", ":"), ensure_ascii=False), encoding="utf-8")
    LOG.write_text("\n".join(log) + "\n", encoding="utf-8")
    cnt = Counter(r["c"] for r in out.values())
    print(f"{len(out)} cards · {dict(cnt)} · {sum('r' in r for r in out.values())} Pokémon with rank · {len(log)} warnings -> {LOG.name}")


if __name__ == "__main__":
    main()
