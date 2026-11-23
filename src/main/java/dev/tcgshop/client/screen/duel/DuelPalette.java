package dev.tcgshop.client.screen.duel;

/**
 * Palette for the Pokémon TCG PvP mode: colors for each Energy type, accents for each player (you in blue, the opponent
 * in red), green→yellow→red health and the log colors per category (damage, healing, K.O., prizes, system).
 * All are ARGB ints: no per-frame allocations.
 */
public final class DuelPalette {
    private DuelPalette() {}

    // ------------------------------------------------------------------ players
    public static final int ME = 0xFF2F8CFF, ME_DARK = 0xFF1F5FC4, ME_SOFT = 0xFFE4F0FF;
    public static final int OPP = 0xFFFF4D6D, OPP_DARK = 0xFFC2253F, OPP_SOFT = 0xFFFFE6EB;

    public static int side(boolean mine) { return mine ? ME : OPP; }

    public static int sideDark(boolean mine) { return mine ? ME_DARK : OPP_DARK; }

    public static int sideSoft(boolean mine) { return mine ? ME_SOFT : OPP_SOFT; }

    // ------------------------------------------------------------------ health
    public static final int HP_HIGH = 0xFF2FCB6E, HP_MID = 0xFFFFC234, HP_LOW = 0xFFFF4545;
    /** "Ghost" bar that lags behind when taking damage, and the one that moves ahead when healing. */
    public static final int HP_GHOST = 0xFFFFE7A8, HP_HEAL = 0xFFB9F5CF;

    /** Green (full health) → yellow (half) → red (nearly K.O.), with a smooth transition. */
    public static int hp(float frac) {
        frac = Math.max(0, Math.min(1, frac));
        if (frac > 0.5f) return lerp(HP_MID, HP_HIGH, (frac - 0.5f) / 0.5f);
        if (frac > 0.2f) return lerp(HP_LOW, HP_MID, (frac - 0.2f) / 0.3f);
        return HP_LOW;
    }

    // ------------------------------------------------------------------ Energy types (engine indices)
    // 0 *, 1 Grass, 2 Fire, 3 Water, 4 Lightning, 5 Psychic, 6 Fighting, 7 Darkness, 8 Metal, 9 Colorless, 10 Fairy, 11 Dragon
    private static final int[] ENERGY = {
            0xFF9AA3B2, // * wildcard
            0xFF43B649, // Grass: green
            0xFFF05A28, // Fire: reddish orange
            0xFF2E8DEB, // Water: blue
            0xFFF7C928, // Lightning: yellow
            0xFF9C5BD6, // Psychic: purple
            0xFFB86B34, // Fighting: brown
            0xFF1F5E63, // Darkness: dark teal
            0xFF8C9AAB, // Metal: steel
            0xFFD9DEE5, // Colorless: light gray
            0xFFF27DB6, // Fairy: pink
            0xFFC9A227  // Dragon: gold
    };

    public static int energy(int type) { return type >= 0 && type < ENERGY.length ? ENERGY[type] : ENERGY[0]; }

    /** Readable text over the type color (dark on Lightning / Colorless, white on the rest). */
    public static int energyInk(int type) { return type == 4 || type == 9 ? 0xFF2A2F3A : 0xFFFFFFFF; }

    // ------------------------------------------------------------------ log
    public static final int LOG_ME = ME_DARK, LOG_OPP = OPP_DARK;
    public static final int LOG_DAMAGE = 0xFFE23A4E, LOG_HEAL = 0xFF1FA35A, LOG_KO = 0xFF8E1230, LOG_PRIZE = 0xFFC98700,
            LOG_SYSTEM = 0xFF7A8AA0, LOG_EVOLVE = 0xFF8B55E8, LOG_STADIUM = 0xFF2E9E6A;

    /** Categories of a log line (for the icon and the color). */
    public static final int K_PLAY = 0, K_DAMAGE = 1, K_HEAL = 2, K_KO = 3, K_PRIZE = 4, K_SYSTEM = 5, K_TURN = 6;

    public static String icon(int kind) {
        return switch (kind) {
            case K_DAMAGE -> "⚔";
            case K_HEAL -> "✚";
            case K_KO -> "✖";
            case K_PRIZE -> "★";
            case K_SYSTEM -> "•";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ panels
    public static final int PANEL_TOP = 0xFFFFFFFF, PANEL_BOTTOM = 0xFFEEF4FB, PANEL_LINE = 0xFFD3E0EE, PANEL_SHINE = 0x66FFFFFF;

    // ------------------------------------------------------------------ utilities
    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    public static int alpha(int argb, float a) {
        int base = (argb >>> 24) & 0xFF;
        int al = Math.max(0, Math.min(255, Math.round(base * a)));
        return (al << 24) | (argb & 0xFFFFFF);
    }
}
