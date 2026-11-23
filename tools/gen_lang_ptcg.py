#!/usr/bin/env python3
"""
Texts for the real TCG engine: prompt/error messages (GAME_MESSAGES) and game log (GAME_LOGS) taken
from the twinleaf.gg client translations (MIT) -> tcgshop.ptcg.msg.<KEY> / tcgshop.ptcg.log.<KEY>.
Parameters stay as {{ name }}; the client substitutes them. It also copies the card map into assets
(the client uses it to draw the cards) and writes the engine's license notice.
Usage: python tools/gen_lang_ptcg.py
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
I18N = ROOT.parent / "vendor/twinleafgg/ptcg-play/src/assets/i18n"
LANG = ROOT / "src/main/resources/assets/tcgshop/lang"
PTCG = ROOT / "src/main/resources/assets/tcgshop/ptcg"

LICENSE = """MasterBall Traders Co. includes the Pokémon TCG game engine from twinleaf.gg (https://github.com/twinleafgg/twinleafgg),
based on ryuu-play (https://github.com/keeshii/ryuu-play), both released under the MIT license, and texts from its client.

The MIT License (MIT)

Copyright (c) 2020 keeshii <keeshii@ptcg.eu>
Copyright (c) twinleaf.gg contributors (Keeshii + TheEPSD and others)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Pokémon and the card names/texts are the property of Nintendo, Creatures Inc., GAME FREAK inc. and The Pokémon Company.
"""


def main():
    for src, dst in (("es.json", "es_es.json"), ("en.json", "en_us.json")):
        tl = json.loads((I18N / src).read_text(encoding="utf-8"))
        d = json.loads((LANG / dst).read_text(encoding="utf-8"))
        d = {k: v for k, v in d.items() if not k.startswith("tcgshop.ptcg.msg.") and not k.startswith("tcgshop.ptcg.log.")}
        for k, v in tl.get("GAME_MESSAGES", {}).items():
            d["tcgshop.ptcg.msg." + k] = v.replace("%", "%%")
        for k, v in tl.get("GAME_LOGS", {}).items():
            d["tcgshop.ptcg.log." + k] = v.replace("%", "%%")
        (LANG / dst).write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
        print(dst, len(tl.get("GAME_MESSAGES", {})), "messages,", len(tl.get("GAME_LOGS", {})), "logs")
    PTCG.mkdir(parents=True, exist_ok=True)
    shutil.copy(ROOT / "src/main/resources/data/tcgshop/tcgshop_ptcg/cards.json", PTCG / "cards.json")
    (PTCG / "LICENSE-twinleaf.txt").write_text(LICENSE, encoding="utf-8")
    print("card map and license copied")


if __name__ == "__main__":
    main()
