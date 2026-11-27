"""1.0.1 hotfix: the "Official art" section of the options screen (client/options/ModOptionsScreen): status and
download / cancel / delete buttons for the official card art downloaded by MasterBall Traders API.

Usage: python tools/gen_lang_10d_download.py  (updates/inserts only these keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    "tcgshop.options.official": ("Arte oficial", "Official art"),
    "tcgshop.options.official.download": ("Descargar arte oficial", "Download official art"),
    "tcgshop.options.official.resume": ("Reanudar descarga", "Resume download"),
    "tcgshop.options.official.update": ("Actualizar arte oficial", "Update official art"),
    "tcgshop.options.official.cancel": ("Cancelar descarga", "Cancel download"),
    "tcgshop.options.official.delete": ("Borrar arte descargado", "Delete downloaded art"),
    "tcgshop.options.official.download.tip": (
        "Descarga las imágenes oficiales de las cartas (%s) desde GitHub en resourcepacks/%s. Se cargan solas y, al terminar, el arte de las cartas pasa a Oficial. Puedes seguir jugando mientras tanto.",
        "Downloads the official card images (%s) from GitHub into resourcepacks/%s. They load automatically and the card art switches to Official when it finishes. You can keep playing meanwhile."),
    "tcgshop.options.official.delete.tip": ("Borra la carpeta resourcepacks/%s y vuelve al arte original.",
                                            "Deletes the resourcepacks/%s folder and goes back to original art."),
    "tcgshop.options.official.delete.title": ("¿Borrar el arte descargado?", "Delete the downloaded art?"),
    "tcgshop.options.official.delete.body": (
        "Se borrará la carpeta resourcepacks/%s y las cartas volverán al arte original. Tus propias carpetas de imágenes no se tocan. Puedes volver a descargarlo cuando quieras.",
        "This deletes the resourcepacks/%s folder and switches the cards back to original art. Your own image folders are not touched. You can download it again at any time."),
    # replaces tcgshop.options.art.official.tip (it only talked about your own folders)
    "tcgshop.options.art.official.tip_v2": (
        "Usa las imágenes oficiales de las cartas: el arte descargado en resourcepacks/PKMNTCGART (ver «Arte oficial» más abajo) o tu propia carpeta (PNGS POKEMOMN o la indicada en config/masterball-api.json), y el reverso oficial si lo tienes.",
        "Uses the official card images: the art downloaded into resourcepacks/PKMNTCGART (see \"Official art\" below) or your own folder (PNGS POKEMOMN or the one set in config/masterball-api.json), and the official card back if you have it."),
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
