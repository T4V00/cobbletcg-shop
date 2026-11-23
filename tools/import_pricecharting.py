#!/usr/bin/env python3
"""
Imports PriceCharting prices (ungraded + graded) for MasterBall Traders Co..

PriceCharting does not allow automatic downloads without an account: you need your own export.
  * Option A (recommended): on pricecharting.com with a subscription, "Price Guide Download" for the
    Pokémon Cards category -> save the CSV and pass it with --csv.
  * Option B: your API token (Legendary/Retailer) with --token; the script downloads the same official CSV
    (https://www.pricecharting.com/price-guide/download-custom?t=TOKEN&category=pokemon-cards).

Columns it uses (this is how PriceCharting defines them for cards):
  loose-price        -> ungraded (raw)
  cib-price          -> Grade 7
  new-price          -> Grade 8
  graded-price       -> Grade 9
  box-only-price     -> Grade 9.5
  manual-only-price  -> PSA 10
CSV prices come as "$12.34"; API prices in cents (detected automatically).

Output: src/main/resources/data/tcgshop/tcgshop_prices/pricecharting.json with
  { "cards_usd":  { id: raw },
    "graded_usd": { id: {"7": x, "8": y, "9": z, "9.5": w, "10": v} } }
It is loaded AFTER cards.json (alphabetical order: "p" > "c"), so its prices win where they exist;
cards PriceCharting does not have keep the TCGplayer price.
You can also put it in a world datapack (data/<ns>/tcgshop_prices/) and run /reload.

Usage:
  python tools/import_pricecharting.py <cobbletcg-1.6.0.jar> --csv pokemon-cards.csv
  python tools/import_pricecharting.py <cobbletcg-1.6.0.jar> --token YOUR_TOKEN
Standard library only.
"""
import argparse
import csv
import datetime
import io
import json
import re
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fetch_prices import load_cards, norm, clean_name  # noqa: E402

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/data/tcgshop/tcgshop_prices/pricecharting.json"
DOWNLOAD = "https://www.pricecharting.com/price-guide/download-custom?t={token}&category=pokemon-cards"

# cobbletcg set code -> "console-name" name(s) in PriceCharting.
SET_CONSOLES = {
    "BS": ["Pokemon Base Set"], "TR": ["Pokemon Team Rocket"], "GYM": ["Pokemon Gym Heroes", "Pokemon Gym Challenge"],
    "ROS": ["Pokemon Roaring Skies"], "HIF": ["Pokemon Hidden Fates"], "TEU": ["Pokemon Team Up"],
    "UNB": ["Pokemon Unbroken Bonds"], "UNM": ["Pokemon Unified Minds"], "VIV": ["Pokemon Vivid Voltage"],
    "BAS": ["Pokemon Battle Styles"], "CRE": ["Pokemon Chilling Reign"], "EVS": ["Pokemon Evolving Skies"],
    "C25": ["Pokemon Celebrations"], "FST": ["Pokemon Fusion Strike"], "BRS": ["Pokemon Brilliant Stars"],
    "ASR": ["Pokemon Astral Radiance"], "LOR": ["Pokemon Lost Origin"], "STE": ["Pokemon Silver Tempest"],
    "CRZ": ["Pokemon Crown Zenith"], "SVI": ["Pokemon Scarlet & Violet"], "PAL": ["Pokemon Paldea Evolved"],
    "OFL": ["Pokemon Obsidian Flames"], "MEW": ["Pokemon Scarlet & Violet 151"], "PAR": ["Pokemon Paradox Rift"],
    "PAF": ["Pokemon Paldean Fates"], "TEF": ["Pokemon Temporal Forces"], "TWM": ["Pokemon Twilight Masquerade"],
    "SFA": ["Pokemon Shrouded Fable"], "SCR": ["Pokemon Stellar Crown"], "SSP": ["Pokemon Surging Sparks"],
    "PRE": ["Pokemon Prismatic Evolutions"], "JTG": ["Pokemon Journey Together"], "DRI": ["Pokemon Destined Rivals"],
    "BLK": ["Pokemon Black Bolt"], "WHT": ["Pokemon White Flare"], "MEG": ["Pokemon Mega Evolution"],
    "PFL": ["Pokemon Phantasmal Flames"], "ASC": ["Pokemon Ascended Heroes"], "POR": ["Pokemon Perfect Order"],
    "CRI": ["Pokemon Chaos Rising"], "PBL": ["Pokemon Pitch Black"],
}
GRADES = [("cib-price", "7"), ("new-price", "8"), ("graded-price", "9"), ("box-only-price", "9.5"), ("manual-only-price", "10")]
PRODUCT_RE = re.compile(r"^(.*?)\s*(?:\[(.*?)\])?\s*#\s*([A-Za-z]*)(\d+)")


def money(v, cents):
    v = (v or "").strip().replace("$", "").replace(",", "")
    if not v:
        return None
    try:
        x = float(v)
    except ValueError:
        return None
    if cents:
        x /= 100.0
    return round(x, 2) if x > 0 else None


def read_rows(args):
    if args.csv:
        text = Path(args.csv).read_text(encoding="utf-8-sig")
    else:
        req = urllib.request.Request(DOWNLOAD.format(token=args.token), headers={"User-Agent": "tcgshop-price-tool/1.0"})
        with urllib.request.urlopen(req, timeout=120) as r:
            text = r.read().decode("utf-8-sig")
    rows = list(csv.DictReader(io.StringIO(text)))
    if not rows or "product-name" not in rows[0]:
        sys.exit("The CSV has no 'product-name' column: is it the PriceCharting export?")
    sample = [r.get("loose-price", "") for r in rows[:200]]
    cents = not any("$" in s or "." in s for s in sample if s)
    return rows, cents


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jar", nargs="?", help="(no longer used: MasterBall Traders API catalog)")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--csv")
    g.add_argument("--token")
    args = ap.parse_args()

    rows, cents = read_rows(args)
    by_console = defaultdict(list)
    for r in rows:
        by_console[norm(r.get("console-name", ""))].append(r)

    cards = load_cards(args.jar)
    raw_out, graded_out, misses = {}, {}, 0
    for c in cards:
        consoles = SET_CONSOLES.get(c["code"], [])
        cand = [r for con in consoles for r in by_console.get(norm(con), [])]
        want = norm(clean_name(c["name"]))
        best = None
        for r in cand:
            m = PRODUCT_RE.match(r["product-name"])
            if not m or int(m.group(4)) != c["num"]:
                continue
            name = norm(m.group(1))
            score = 2 if name == want else 1 if (want in name or name in want) else 0
            if score and (best is None or score > best[0]):
                best = (score, r)
        if not best:
            misses += 1
            continue
        r = best[1]
        raw = money(r.get("loose-price"), cents)
        if raw:
            raw_out[c["id"]] = raw
        grades = {k: p for col, k in GRADES if (p := money(r.get(col), cents))}
        if grades:
            graded_out[c["id"]] = grades

    OUT.write_text(json.dumps({
        "source": "PriceCharting (user's export)",
        "generated": datetime.date.today().isoformat(),
        "cards_usd": dict(sorted(raw_out.items())),
        "graded_usd": dict(sorted(graded_out.items())),
    }, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"{len(raw_out)} cards with ungraded price, {len(graded_out)} with graded prices, {misses} unmatched")
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
