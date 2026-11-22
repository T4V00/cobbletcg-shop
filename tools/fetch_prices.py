#!/usr/bin/env python3
"""
Generador de precios reales para MasterBall Traders Co..

Fuente: tcgcsv.com — espejo público y gratuito de la API de TCGplayer, actualizado a diario
(~20:00 UTC), sin clave. Cubre los 72 sets del catálogo de MasterBall Traders API y también
productos sellados (sobres, cajas, ETB).

Proceso:
  1. Lee el catálogo de MasterBall Traders API (../masterball-api). Cada carta se llama
     "<CÓDIGO> <número>  <nombre> [sufijo]"  p.ej. "PAL 269  Iono SIR".
  2. Para cada código descarga productos y precios de su(s) grupo(s) TCGplayer (tabla SET_GROUPS).
  3. Empareja por número + nombre, con varios respaldos (sólo nombre, nombre normalizado, promos).
  4. Escribe src/main/resources/data/tcgshop/tcgshop_prices/cards.json

Formato de salida:
  { "source": "...", "generated": "YYYY-MM-DD",
    "cards_usd":  { "masterball:paldeanevolved/cards/pal269": 92.5, ... },      # precio de mercado (USD)
    "sealed_usd": { "paldeanevolved": {"pack": 4.9, "box": 210.0, "etb": 55.0}, ... } }

Uso:  python tools/fetch_prices.py [--cache carpeta]   (cartas: catálogo de ../masterball-api)
Sólo librería estándar.
"""
import argparse
import collections
import datetime
import json
import os
import re
import statistics
import sys
import time
import urllib.request
from pathlib import Path

BASE = "https://tcgcsv.com/tcgplayer/3"  # categoría 3 = Pokémon
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/tcgshop/tcgshop_prices/cards.json"
UA = {"User-Agent": "tcgshop-price-tool/1.0"}

# Código de set de cobbletcg -> grupos TCGplayer (verificados contra https://tcgcsv.com/tcgplayer/3/groups).
# El primer grupo es el set principal; los siguientes son subsets (Trainer Gallery, Galarian Gallery, Shiny Vault...).
SET_GROUPS = {
    "BS": [604], "TR": [1373], "GYM": [1440], "ROS": [1534],
    "HIF": [2480, 2594], "TEU": [2377], "UNB": [2420], "UNM": [2464],
    "VIV": [2701], "BAS": [2765], "CRE": [2807], "EVS": [2848], "C25": [2867, 2931], "FST": [2906],
    "BRS": [2948, 3020], "ASR": [3040, 3068], "LOR": [3118, 3172], "STE": [3170, 17674], "CRZ": [17688, 17689],
    "SVI": [22873], "PAL": [23120], "OFL": [23228], "MEW": [23237], "PAR": [23286], "PAF": [23353],
    "TEF": [23381], "TWM": [23473], "SFA": [23529], "SCR": [23537], "SSP": [23651], "PRE": [23821],
    "JTG": [24073], "DRI": [24269], "BLK": [24325], "WHT": [24326], "MEG": [24380], "PFL": [24448],
    "ASC": [24541], "POR": [24587], "CRI": [24655], "PBL": [24688],
}
# Grupos de promos donde buscar por nombre las cartas "ETB"/"UPC"/"Promo" de cobbletcg.
PROMO_GROUP_NAME = re.compile(r"(Promo|Black Star)", re.I)

SUFFIXES = r"(ETB|UPC|PROMO|PC|IR|SIR|SR|HR|UR|ACE|BS|BSP|TR|GC|NEO|RAINBOW|GOLD|FA|TG|GG|SV)"
# Sólo sufijos al final del nombre ("Zorua ETB", "Snorlax Promo ETB"); "Surprise Box" NO es promo.
PROMO_RE = re.compile(r"\s+(Promo\s+)?(ETB|UPC|PC|Promo)\s*$", re.I)


class Fetcher:
    def __init__(self, cache):
        self.cache = Path(cache) if cache else None
        if self.cache:
            self.cache.mkdir(parents=True, exist_ok=True)

    def get(self, path):
        f = self.cache / (path.replace("/", "_") + ".json") if self.cache else None
        if f and f.exists():
            return json.loads(f.read_text(encoding="utf-8"))["results"]
        for attempt in range(5):
            try:
                with urllib.request.urlopen(urllib.request.Request(f"{BASE}/{path}", headers=UA), timeout=60) as r:
                    raw = r.read()
                if f:
                    f.write_bytes(raw)
                time.sleep(0.15)  # ser amables con el servidor
                return json.loads(raw)["results"]
            except Exception as e:  # noqa: BLE001
                print(f"  {path}: {e} (reintento {attempt + 1})", file=sys.stderr)
                time.sleep(2 ** attempt)
        raise RuntimeError(path)


def norm(s):
    s = s.lower().replace("é", "e").replace("’", "'")
    s = re.sub(r"\bmega\s+", "m ", s)
    s = re.sub(r"\(.*?\)", "", s)
    return re.sub(r"[^a-z0-9]", "", s)


def clean_name(name):
    """'Iono SIR' -> 'Iono'; 'Tsareena Radiant' -> 'Radiant Tsareena'."""
    n = re.sub(rf"\s+\b{SUFFIXES}\b\s*$", "", name.strip(), flags=re.I)
    n = re.sub(rf"\s+\b{SUFFIXES}\b\s*$", "", n, flags=re.I)
    m = re.match(r"^(.*)\s+Radiant$", n)
    if m:
        n = "Radiant " + m.group(1)
    return n.strip()


API = Path(__file__).resolve().parent.parent.parent / "masterball-api"
API_CATALOG = API / "src/main/resources/data/masterball/cards/catalog.json"
API_LANG = API / "src/main/resources/assets/masterball/lang/en_us.json"
API_RARITY = API / "src/main/resources/data/masterball/rarity/cards.json"


def catalog_sets():
    return json.loads(API_CATALOG.read_text(encoding="utf-8"))["sets"]


def load_cards(_jar=None):
    """Todas las cartas del catálogo de MasterBall Traders API (../masterball-api), con su nombre oficial
    (el del lang de la API, ya corregido con TCGplayer). El argumento antiguo (jar de ScalperTCG) se ignora."""
    lang = json.loads(API_LANG.read_text(encoding="utf-8"))
    out = []
    for set_id, s in catalog_sets().items():
        for leaf, c in s["cards"].items():
            disp = lang.get(f"item.masterball.{set_id}.cards.{leaf}", c.get("n") or leaf)
            name = re.sub(r"\s*\([^()]*\)$", "", disp).strip()  # quita el " (BS 4)" final
            no = c.get("no") or ""
            d = re.search(r"\d+", no)
            out.append({"id": f"masterball:{set_id}/cards/{leaf}", "set": set_id, "code": s["code"],
                        "num": int(d.group()) if d else 0, "name": name,
                        "promo": bool(PROMO_RE.search(name)) or bool(re.search(r"\b(ETB|UPC|DCK|Promo)\b", no))})
    return out


def load_rarities(_jar=None):
    """id de carta -> clave de rareza (MasterBall Traders API, rareza oficial)."""
    cards = json.loads(API_RARITY.read_text(encoding="utf-8"))["cards"]
    return {f"masterball:{k.split('/')[0]}/cards/{k.split('/')[1]}": v["r"] for k, v in cards.items()}


# Variante preferida de cada carta: la normal/holo (y la Unlimited en los sets antiguos) antes que la reverse
# holo o la 1ª edición, que valen otra cosa. Antes se cogía el máximo de todas y los sets antiguos salían a precio
# de 1ª edición.
SUB_RANK = {"Normal": 0, "Holofoil": 0, "Unlimited": 0, "Unlimited Holofoil": 0,
            "Reverse Holofoil": 1, "1st Edition": 2, "1st Edition Holofoil": 2}


def product_price(prices, pid):
    """Precio de mercado (ventas reales). Sin ventas: el anuncio más barato, nunca el precio medio de anuncios."""
    best = {}
    for p in prices.get(pid, []):
        v = p.get("marketPrice") or p.get("lowPrice")
        if v:
            best.setdefault(SUB_RANK.get(p.get("subTypeName"), 1), []).append(v)
    if not best:
        return None
    return max(best[min(best)])


SEALED_SKIP = ("pokemon center", "exclusive", "set of", "mini", "half", "case", "display", "sleeved", "blister",
               "bundle", "code", "1st edition", "dollar general", "japanese", "build & battle", "collection")


def sealed_kind(name):
    """'pack' / 'box' / 'etb' para el producto sellado estándar del set; None para variantes y exclusivas."""
    raw = name.lower()
    if any(k in raw for k in SEALED_SKIP):
        return None
    base = re.sub(r"\s*[\[(][^\])]*[\])]", "", raw).strip()  # "Booster Pack [Unlimited Edition]" -> "booster pack"
    if base.endswith("booster pack"):
        return "pack"
    if base.endswith("booster box"):
        return "box"
    if base.endswith("elite trainer box"):
        return "etb"
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jar", nargs="?", help="(ya no se usa: las cartas salen del catálogo de MasterBall Traders API)")
    ap.add_argument("--cache")
    args = ap.parse_args()
    fx = Fetcher(args.cache)

    cards = load_cards(args.jar)
    print(f"{len(cards)} cartas en el jar")

    result, sealed, misses = {}, {}, []
    by_code = collections.defaultdict(list)
    for c in cards:
        by_code[c["code"]].append(c)

    # catálogo de promos (para cartas ETB/UPC)
    promo_index = collections.defaultdict(list)
    promo_prices = {}
    for g in fx.get("groups"):
        if PROMO_GROUP_NAME.search(g["name"]) and g["publishedOn"] >= "2019":
            for p in fx.get(f"{g['groupId']}/products"):
                promo_index[norm(p["name"])].append(p["productId"])
            for pr in fx.get(f"{g['groupId']}/prices"):
                promo_prices.setdefault(pr["productId"], []).append(pr)

    groups_by_code = dict(SET_GROUPS)
    for st in catalog_sets().values():
        if st.get("tcg_groups"):
            groups_by_code[st["code"]] = st["tcg_groups"]
    for code, group_ids in groups_by_code.items():
        prods, prices = [], {}
        for gid in group_ids:
            prods += fx.get(f"{gid}/products")
            for pr in fx.get(f"{gid}/prices"):
                prices.setdefault(pr["productId"], []).append(pr)

        by_num, by_name = collections.defaultdict(list), collections.defaultdict(list)
        set_sealed = {}
        for p in prods:
            ed = {e["name"]: e["value"] for e in p.get("extendedData", [])}
            num = ed.get("Number")
            if num:
                d = re.match(r"^[A-Z]*0*(\d+)", num)
                if d:
                    by_num[int(d.group(1))].append(p)
                by_name[norm(p["name"])].append(p)
            else:  # producto sellado: sólo ventas reales (las cajas antiguas sólo tienen anuncios de 100.000 $)
                kind = sealed_kind(p["name"])
                vals = [pr.get("marketPrice") for pr in prices.get(p["productId"], []) if pr.get("marketPrice")]
                if kind and vals:
                    set_sealed.setdefault(kind, []).append(min(vals))

        for c in by_code.get(code, []):
            base = norm(clean_name(PROMO_RE.sub("", c["name"])))
            price = None
            if not c["promo"]:
                # 1) número + nombre
                for p in by_num.get(c["num"], []):
                    pn = norm(p["name"])
                    if pn.startswith(base) or base.startswith(pn[:max(4, len(pn) - 2)]) or base in pn:
                        price = product_price(prices, p["productId"])
                        if price:
                            break
                # 2) nombre único dentro del set (numeración distinta en cobbletcg)
                if price is None:
                    same = [p for k, ps in by_name.items() if k.startswith(base) for p in ps]
                    if len(same) == 1:
                        price = product_price(prices, same[0]["productId"])
            else:
                # 3) promos ETB/UPC: buscar por nombre en grupos de promos, tomar la más barata
                vals = [product_price(promo_prices, pid) for k, pids in promo_index.items() if k.startswith(base) for pid in pids]
                vals = [v for v in vals if v]
                price = min(vals) if vals else None
            if price:
                result[c["id"]] = round(price, 2)
            else:
                misses.append(f'{c["code"]} {c["num"]} {c["name"]}')

        if set_sealed and by_code.get(code):
            # varias versiones estándar (p.ej. dos ETB con distinto arte): la más barata
            got = {k: min(v) for k, v in set_sealed.items()}
            if "box" not in got and "pack" in got:
                got["box"] = got["pack"] * 36  # sin ventas de caja: 36 sobres
            sealed[by_code[code][0]["set"]] = {k: round(v, 2) for k, v in got.items()}

    # Medianas reales por set y rareza: estimación para las cartas sin precio (y futuras cartas nuevas).
    rarity_of = load_rarities(args.jar)
    buckets = collections.defaultdict(list)
    global_buckets = collections.defaultdict(list)
    for cid, usd in result.items():
        r = rarity_of.get(cid, "unknown")
        buckets[(cid.split(":")[1].split("/")[0], r)].append(usd)
        global_buckets[r].append(usd)
    estimates = collections.defaultdict(dict)
    for (set_id, r), vals in buckets.items():
        if len(vals) >= 3:
            estimates[set_id][r] = round(statistics.median(vals), 2)
    estimates["*"] = {r: round(statistics.median(v), 2) for r, v in global_buckets.items()}

    unknown = sorted(set(by_code) - set(groups_by_code))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "source": "TCGplayer market price via tcgcsv.com",
        "generated": datetime.date.today().isoformat(),
        "cards_usd": dict(sorted(result.items())),
        "sealed_usd": dict(sorted(sealed.items())),
        "rarity_median_usd": dict(sorted(estimates.items())),
    }, indent=1, ensure_ascii=False), encoding="utf-8")
    print("Medianas globales por rareza (USD):", estimates["*"])

    print(f"\nCon precio: {len(result)}/{len(cards)} ({100 * len(result) / len(cards):.1f}%)  -> {OUT}")
    print(f"Sellados: {len(sealed)} sets")
    if unknown:
        print("Códigos sin grupo en SET_GROUPS:", ", ".join(unknown))
    if misses:
        print(f"Sin coincidencia ({len(misses)}, usarán precio estimado):")
        for m in misses[:40]:
            print("  ", m)


if __name__ == "__main__":
    main()
