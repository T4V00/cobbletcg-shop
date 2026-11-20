package dev.tcgshop.binder;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Personalizaciones del Personal Binder y el logro que desbloquea cada una (null = desbloqueada de serie).
 * Todo se dibuja por código (sin imágenes con copyright): fondos inspirados en biomas de Cobblemon,
 * patrones inspirados en tipos Pokémon, colores sólidos y degradados.
 */
public final class BinderStyles {
    public record Style(String kind, String id, int c1, int c2, @Nullable String advancement) {
        public String translationKey() { return "tcgshop.style." + kind + "." + id; }
    }

    public static final List<Style> ALL = new ArrayList<>();

    // --- paletas: colores sólidos (c1 == c2) y degradados (tapas del binder) ---
    static {
        palette("classic_red", 0xFFB3262E, 0xFFB3262E, null);
        palette("ocean_blue", 0xFF1F4FA8, 0xFF1F4FA8, null);
        palette("forest_green", 0xFF2E7D3A, 0xFF2E7D3A, null);
        palette("midnight", 0xFF20222B, 0xFF20222B, null);
        palette("sunny_yellow", 0xFFE8B923, 0xFFE8B923, "tcgshop:packs_10");
        palette("psychic_pink", 0xFFD94F9C, 0xFFD94F9C, "tcgshop:unique_25");
        palette("ghost_purple", 0xFF5B3A8C, 0xFF5B3A8C, "tcgshop:pull_double_rare");
        palette("ice_cyan", 0xFF5CC8E0, 0xFF5CC8E0, "tcgshop:unique_100");
        palette("grad_sunset", 0xFFFF7A45, 0xFF8E2DE2, "tcgshop:packs_50");
        palette("grad_ocean", 0xFF00C6FF, 0xFF0052D4, "tcgshop:pull_ultra_rare");
        palette("grad_forest", 0xFFA8E063, 0xFF2F6B2F, "tcgshop:unique_250");
        palette("grad_aurora", 0xFF43E97B, 0xFF38F9D7, "tcgshop:pull_illustration_rare");
        palette("grad_volcano", 0xFFFFD200, 0xFFE52D27, "tcgshop:packs_250");
        palette("grad_galaxy", 0xFF4B1D6E, 0xFF0F0C29, "tcgshop:pull_special_illustration");
        palette("grad_gold", 0xFFFFE27A, 0xFFB8860B, "tcgshop:pull_hyper_rare");
        palette("grad_master", 0xFF8E44AD, 0xFFE84393, "tcgshop:god_pack");

        // --- patrones (inspirados en tipos Pokémon) ---
        pattern("none", null);
        pattern("dots", null);
        pattern("pokeballs", "tcgshop:first_binder");
        pattern("lightning", "tcgshop:packs_10");
        pattern("flames", "tcgshop:unique_100");
        pattern("waves", "tcgshop:pull_double_rare");
        pattern("leaves", "tcgshop:unique_250");
        pattern("stars", "tcgshop:pull_illustration_rare");
        pattern("scales", "tcgshop:pull_special_illustration");
        pattern("paws", "tcgshop:packs_50");
        pattern("diamonds", "tcgshop:value_100k");
        pattern("crystals", "tcgshop:set_complete");

        // --- fondos (escenas inspiradas en biomas de Cobblemon) ---
        background("none", null);
        background("meadow", "tcgshop:first_binder");
        background("beach", "tcgshop:packs_50");
        background("night_sky", "tcgshop:unique_500");
        background("crystal_cave", "tcgshop:pull_hyper_rare");
        background("snowy_peaks", "tcgshop:unique_1000");
        background("berry_grove", "tcgshop:value_10k");
        background("volcano", "tcgshop:god_pack");
        background("aurora_tundra", "tcgshop:scalper_defeat");
        background("ancient_ruins", "tcgshop:set_complete");
    }

    private BinderStyles() {}

    private static void palette(String id, int c1, int c2, String adv) { ALL.add(new Style("palette", id, c1, c2, adv)); }
    private static void pattern(String id, String adv) { ALL.add(new Style("pattern", id, 0, 0, adv)); }
    private static void background(String id, String adv) { ALL.add(new Style("background", id, 0, 0, adv)); }

    @Nullable
    public static Style find(String kind, String id) {
        for (Style s : ALL) if (s.kind.equals(kind) && s.id.equals(id)) return s;
        return null;
    }

    public static List<Style> ofKind(String kind) {
        List<Style> l = new ArrayList<>();
        for (Style s : ALL) if (s.kind.equals(kind)) l.add(s);
        return l;
    }
}
