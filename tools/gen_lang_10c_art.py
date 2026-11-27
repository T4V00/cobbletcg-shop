"""1.0 beta 3 (art): mod options screen (key O), card art option (original / official) and the first-play notice
(client/options/ModOptions*, API client/art/CardArt).

Usage: python tools/gen_lang_10c_art.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ key and screen
    "key.tcgshop.options": ("Opciones de MasterBall Traders", "MasterBall Traders options"),
    "tcgshop.options.title": ("MasterBall Traders Co. · Opciones", "MasterBall Traders Co. · Options"),
    "tcgshop.options.general": ("General", "General"),
    "tcgshop.options.wallpaper": ("Fondo del móvil", "Phone wallpaper"),
    "tcgshop.options.controls": ("Controles…", "Controls…"),

    # ------------------------------------------------------------------ card art
    "tcgshop.options.art": ("Arte de las cartas", "Card art"),
    "tcgshop.options.art.original": ("Original", "Original"),
    "tcgshop.options.art.official": ("Oficial", "Official"),
    "tcgshop.options.art.original.tip": (
        "Cartas con arte propio en pixel art: cada carta conserva su nombre, número y PS, con un monstruo misterioso "
        "y el borde del color de su tipo. Siempre funciona, sin descargar nada.",
        "Cards with our own pixel art: each card keeps its name, number and HP, with a mysterious monster and a border "
        "in its type's color. Always works, nothing to download."),
    "tcgshop.options.art.official.tip": (
        "Usa las imágenes de cartas de tu carpeta (PNGS POKEMOMN, masterball/images o la que pongas en "
        "config/masterball-api.json) y el reverso de «back of cards/card_back.png» si lo tienes.",
        "Uses the card images in your folder (PNGS POKEMOMN, masterball/images or the one set in "
        "config/masterball-api.json) and the back from \"back of cards/card_back.png\" if you have it."),
    "tcgshop.options.art.hint.original": (
        "Arte original: monstruos pixelados hechos a mano por el código.",
        "Original art: pixel monsters handcrafted by code."),
    "tcgshop.options.art.hint.official": (
        "Arte oficial: las imágenes de tu carpeta de cartas.",
        "Official art: the images from your card folder."),
    "tcgshop.options.art.no_images": (
        "No encuentro imágenes oficiales: las cartas sin imagen enseñarán el reverso.",
        "No official images found: cards without one will show their back."),

    # ------------------------------------------------------------------ first-play notice
    "tcgshop.options.notice.title": ("Arte de las cartas", "Card art"),
    "tcgshop.options.notice.key": ("Pulsa %s para abrir las opciones.", "Press %s to open the options."),
    "tcgshop.options.notice.unbound": (
        "Asigna una tecla a «Opciones de MasterBall Traders» en Controles para abrirlas.",
        "Bind a key to \"MasterBall Traders options\" in Controls to open them."),
    "tcgshop.options.notice.chat.1": (
        "[MasterBall Traders] Tus cartas lucen arte ORIGINAL de la casa. ¿Tienes las imágenes oficiales? "
        "Cámbialo a «Oficial» en las opciones del mod. %s",
        "[MasterBall Traders] Your cards wear our own ORIGINAL art. Got the official images? "
        "Switch to \"Official\" in the mod options. %s"),
    "tcgshop.options.notice.chat.2": (
        "[MasterBall Traders] ¡Ojo, coleccionista! Las cartas vienen con arte original pixelado. "
        "Si prefieres tus imágenes oficiales, se cambia en las opciones. %s",
        "[MasterBall Traders] Heads up, collector! Cards come with original pixel art. "
        "Prefer your official images? Change it in the options. %s"),
    "tcgshop.options.notice.chat.3": (
        "[MasterBall Traders] Esos monstruos misteriosos son nuestro arte original. "
        "El arte de las cartas (original u oficial) se elige en las opciones. %s",
        "[MasterBall Traders] Those mysterious monsters are our original art. "
        "Card art (original or official) is chosen in the options. %s"),
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
