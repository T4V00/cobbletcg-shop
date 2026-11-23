"""New texts for the TCG League deck editor (review fixes: save rejected / pending).

Usage: python tools/gen_lang_fix_league.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/tcgshop/lang"

T = {
    "tcgshop.league.db.save_bad": ("No se guardó: %s", "Not saved: %s"),
    "tcgshop.league.db.saving": ("Guardando… esperando al servidor", "Saving… waiting for the server"),
}


def main():
    for f, i in (("es_es.json", 0), ("en_us.json", 1)):
        p = LANG / f
        d = json.loads(p.read_text(encoding="utf-8"))
        for k, v in T.items():
            d[k] = v[i]
        p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(len(T), "texts")


if __name__ == "__main__":
    main()
