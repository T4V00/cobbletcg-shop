#!/usr/bin/env python3
"""
Textos en español de las cartas del TCG real (nombre, ataques, habilidades y texto de Entrenador/Energía) desde la API
abierta de TCGdex (https://tcgdex.dev). Empareja cada carta del motor (set oficial + número) con la de TCGdex.
Salida: src/main/resources/assets/tcgshop/ptcg/i18n/es.json -> {fullName: {"n": nombre, "a": [[ataque, efecto]], "p": [[habilidad, efecto]], "t": texto}}
Uso: python tools/fetch_card_i18n.py [lang=es]
"""
import json
import re
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG = sys.argv[1] if len(sys.argv) > 1 else "es"
OUT = ROOT / f"src/main/resources/assets/tcgshop/ptcg/i18n/{LANG}.json"
API = f"https://api.tcgdex.net/v2/{LANG}"
# códigos del motor que en TCGdex tienen otra abreviatura
ALIASES = {"SVP": ["PR-SV", "SVP"], "SSH": ["SSH"], "BS": ["BS", "BS1"], "TR": ["TR"], "CEL": ["CEL"], "G2": ["G2", "GYM2"]}


def get(url, tries=4):
    for k in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "tcgshop-i18n/1.0"})
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:
            if k == tries - 1:
                return None
            time.sleep(1.5 * (k + 1))


def num_key(s):
    m = re.match(r"^\D*(\d+)", str(s))
    return int(m.group(1)) if m else str(s)


def main():
    tl = json.loads((ROOT / "engine/twinleaf_cards.json").read_text(encoding="utf-8"))
    by = {c["f"]: c for c in tl}
    mapped = set(json.loads((ROOT / "src/main/resources/assets/tcgshop/ptcg/cards.json").read_text(encoding="utf-8")).values())
    extra = ROOT / "engine/meta_decks.json"
    if extra.exists():
        for d in json.loads(extra.read_text(encoding="utf-8")):
            mapped.update(d["cards"])
    wanted = [by[n] for n in mapped if n in by]
    sets = get(f"{API}/sets") or []
    print("sets en TCGdex:", len(sets))
    details = {}
    with ThreadPoolExecutor(12) as ex:
        for sid, d in zip([s["id"] for s in sets], ex.map(lambda s: get(f"{API}/sets/{s['id']}"), sets)):
            if d:
                details[sid] = d
    by_abbr = {}
    for sid, d in details.items():
        ab = (d.get("abbreviation") or {}).get("official")
        if ab:
            by_abbr.setdefault(ab.upper(), []).append(d)
    # carta del motor -> id de TCGdex
    jobs, missing_sets = {}, {}
    for c in wanted:
        code = c["s"].upper()
        cands = []
        for ab in ALIASES.get(code, [code]):
            cands += by_abbr.get(ab.upper(), [])
        found = None
        for d in cands:
            for card in d.get("cards", []):
                if num_key(card["localId"]) == num_key(c["num"]):
                    found = card["id"]
                    break
            if found:
                break
        if found:
            jobs[c["f"]] = found
        else:
            missing_sets[code] = missing_sets.get(code, 0) + 1
    print("cartas a traducir:", len(jobs), "/", len(wanted), " sin set/numero en TCGdex:", missing_sets)
    out = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {}
    todo = [(f, cid) for f, cid in jobs.items() if f not in out]

    def fetch(item):
        f, cid = item
        d = get(f"{API}/cards/{cid}")
        if not d:
            return f, None
        e = {"n": d.get("name", "")}
        if d.get("attacks"):
            e["a"] = [[a.get("name", ""), a.get("effect", "") or ""] for a in d["attacks"]]
        if d.get("abilities"):
            e["p"] = [[a.get("name", ""), a.get("effect", "") or ""] for a in d["abilities"]]
        if d.get("effect"):
            e["t"] = d["effect"]
        return f, e

    done = 0
    with ThreadPoolExecutor(12) as ex:
        for f, e in ex.map(fetch, todo):
            done += 1
            if e:
                out[f] = e
            if done % 250 == 0:
                print(f"  {done}/{len(todo)}")
                OUT.parent.mkdir(parents=True, exist_ok=True)
                OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("traducidas:", len(out), "->", OUT)


if __name__ == "__main__":
    main()
