#!/usr/bin/env python3
"""
Textos del motor real del TCG: mensajes de prompts/errores (GAME_MESSAGES) y registro de partida (GAME_LOGS) tomados
de las traducciones del cliente de twinleaf.gg (MIT) -> tcgshop.ptcg.msg.<CLAVE> / tcgshop.ptcg.log.<CLAVE>.
Los parámetros quedan como {{ nombre }}; el cliente los sustituye. También copia el mapa de cartas a assets
(el cliente lo usa para dibujar las cartas) y escribe el aviso de licencia del motor.
Uso: python tools/gen_lang_ptcg.py
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
I18N = ROOT.parent / "vendor/twinleafgg/ptcg-play/src/assets/i18n"
LANG = ROOT / "src/main/resources/assets/tcgshop/lang"
PTCG = ROOT / "src/main/resources/assets/tcgshop/ptcg"

LICENSE = """MasterBall Traders Co. incluye el motor de juego de Pokémon TCG de twinleaf.gg (https://github.com/twinleafgg/twinleafgg),
basado en ryuu-play (https://github.com/keeshii/ryuu-play), ambos publicados bajo licencia MIT, y textos de su cliente.

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

Pokémon y los nombres/textos de las cartas son propiedad de Nintendo, Creatures Inc., GAME FREAK inc. y The Pokémon Company.
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
        print(dst, len(tl.get("GAME_MESSAGES", {})), "mensajes,", len(tl.get("GAME_LOGS", {})), "registros")
    PTCG.mkdir(parents=True, exist_ok=True)
    shutil.copy(ROOT / "src/main/resources/data/tcgshop/tcgshop_ptcg/cards.json", PTCG / "cards.json")
    (PTCG / "LICENSE-twinleaf.txt").write_text(LICENSE, encoding="utf-8")
    print("mapa de cartas y licencia copiados")


if __name__ == "__main__":
    main()
