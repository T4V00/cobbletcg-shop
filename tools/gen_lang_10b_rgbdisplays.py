"""Texts for the 1.0 RGB displays (shop/RgbDisplays.java, shop/RgbLighting.java).

Usage: python tools/gen_lang_10b_rgbdisplays.py  (updates/inserts the keys in es_es.json and en_us.json)
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/tcgshop/lang"

T = {
    # ------------------------------------------------------------------ block names
    "block.tcgshop.rgb_card_frame": ("Marco neón RGB", "RGB Neon Frame"),
    "block.tcgshop.rgb_card_ledge": ("Repisa LED RGB", "RGB LED Ledge"),
    "block.tcgshop.rgb_counter": ("Mostrador gamer RGB", "RGB Gamer Counter"),
    "block.tcgshop.rgb_card_stand": ("Atril RGB", "RGB Card Stand"),
    "block.tcgshop.rgb_turntable": ("Plataforma giratoria RGB", "RGB Turntable"),
    "block.tcgshop.rgb_wall_trio": ("Marco triple RGB", "RGB Triple Frame"),
    "block.tcgshop.rgb_glass_cube": ("Cubo de cristal RGB", "RGB Glass Cube"),
    "block.tcgshop.rgb_pack_shelf": ("Estantería de sobres RGB", "RGB Pack Shelf"),
    "block.tcgshop.rgb_display_case": ("Vitrina mostrador RGB", "RGB Glass Counter"),
    "block.tcgshop.rgb_showcase": ("Torre vitrina RGB", "RGB Showcase Tower"),
    "block.tcgshop.rgb_box_shelf": ("Estante de cajas RGB", "RGB Box Shelf"),
    "block.tcgshop.rgb_gondola": ("Góndola RGB", "RGB Gondola"),
    "block.tcgshop.rgb_slab_tower": ("Torre de slabs RGB", "RGB Slab Tower"),
    "block.tcgshop.rgb_box_stand": ("Expositor de cajas RGB", "RGB Booster Box Stand"),
    "block.tcgshop.rgb_card_wall": ("Muro de cartas RGB", "RGB Card Wall"),
    "block.tcgshop.rgb_multi_shelf": ("Estante de coleccionista RGB", "RGB Collector Shelf"),
    "block.tcgshop.rgb_neon_poster": ("Marco neón gigante RGB", "RGB Giant Neon Frame"),
    "block.tcgshop.rgb_grand_wall": ("Gran muro galería RGB", "RGB Grand Gallery Wall"),
    "block.tcgshop.rgb_pedestal": ("Pedestal RGB", "RGB Pedestal"),
    "block.tcgshop.rgb_prism_pedestal": ("Pedestal prisma RGB", "RGB Prism Pedestal"),

    # ------------------------------------------------------------------ item tooltip
    "tcgshop.rgb.tip.slots": ("%s huecos para productos · %s×%s bloques", "%s product slots · %s×%s blocks"),
    "tcgshop.rgb.tip.lights": ("Iluminación RGB animada", "Animated RGB lighting"),
    "tcgshop.rgb.tip.controls": ("Agáchate + clic derecho: tinte = color, polvo de piedra luminosa = animación",
                                 "Sneak + right-click: dye = color, glowstone dust = animation"),

    # ------------------------------------------------------------------ LED controls
    "tcgshop.rgb.mode": ("Luces LED: %s", "LED lights: %s"),
    "tcgshop.rgb.color": ("Color LED: %s", "LED color: %s"),
    "tcgshop.rgb.not_yours": ("Solo el dueño puede cambiar estas luces.", "Only the owner can change these lights."),
    "tcgshop.rgb.mode.0": ("Arcoíris en movimiento", "Rainbow chase"),
    "tcgshop.rgb.mode.1": ("Ola de color", "Color wave"),
    "tcgshop.rgb.mode.2": ("Respiración", "Breathing"),
    "tcgshop.rgb.mode.3": ("Destellos", "Sparkle"),
    "tcgshop.rgb.mode.4": ("Color fijo", "Solid color"),
    "tcgshop.rgb.mode.5": ("Apagadas", "Off"),
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
