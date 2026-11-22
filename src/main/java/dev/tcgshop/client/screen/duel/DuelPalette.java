package dev.tcgshop.client.screen.duel;

/**
 * Paleta del modo PvP del Pokémon TCG: colores de cada tipo de Energía, acentos de cada jugador (tú en azul, el rival
 * en rojo), salud verde→amarillo→rojo y los colores del registro por categoría (daño, curación, K.O., premios, sistema).
 * Todo son enteros ARGB: sin asignaciones por fotograma.
 */
public final class DuelPalette {
    private DuelPalette() {}

    // ------------------------------------------------------------------ jugadores
    public static final int ME = 0xFF2F8CFF, ME_DARK = 0xFF1F5FC4, ME_SOFT = 0xFFE4F0FF;
    public static final int OPP = 0xFFFF4D6D, OPP_DARK = 0xFFC2253F, OPP_SOFT = 0xFFFFE6EB;

    public static int side(boolean mine) { return mine ? ME : OPP; }

    public static int sideDark(boolean mine) { return mine ? ME_DARK : OPP_DARK; }

    public static int sideSoft(boolean mine) { return mine ? ME_SOFT : OPP_SOFT; }

    // ------------------------------------------------------------------ salud
    public static final int HP_HIGH = 0xFF2FCB6E, HP_MID = 0xFFFFC234, HP_LOW = 0xFFFF4545;
    /** Barra "fantasma" que se queda atrás al recibir daño, y la que se adelanta al curarse. */
    public static final int HP_GHOST = 0xFFFFE7A8, HP_HEAL = 0xFFB9F5CF;

    /** Verde (salud llena) → amarillo (mitad) → rojo (casi K.O.), con transición suave. */
    public static int hp(float frac) {
        frac = Math.max(0, Math.min(1, frac));
        if (frac > 0.5f) return lerp(HP_MID, HP_HIGH, (frac - 0.5f) / 0.5f);
        if (frac > 0.2f) return lerp(HP_LOW, HP_MID, (frac - 0.2f) / 0.3f);
        return HP_LOW;
    }

    // ------------------------------------------------------------------ tipos de Energía (índices del motor)
    // 0 *, 1 Planta, 2 Fuego, 3 Agua, 4 Rayo, 5 Psíquico, 6 Lucha, 7 Oscuridad, 8 Metálico, 9 Incoloro, 10 Hada, 11 Dragón
    private static final int[] ENERGY = {
            0xFF9AA3B2, // * comodín
            0xFF43B649, // Planta: verde
            0xFFF05A28, // Fuego: naranja rojizo
            0xFF2E8DEB, // Agua: azul
            0xFFF7C928, // Rayo: amarillo
            0xFF9C5BD6, // Psíquico: morado
            0xFFB86B34, // Lucha: marrón
            0xFF1F5E63, // Oscuridad: verde azulado oscuro
            0xFF8C9AAB, // Metálico: acero
            0xFFD9DEE5, // Incoloro: gris claro
            0xFFF27DB6, // Hada: rosa
            0xFFC9A227  // Dragón: dorado
    };

    public static int energy(int type) { return type >= 0 && type < ENERGY.length ? ENERGY[type] : ENERGY[0]; }

    /** Texto legible encima del color del tipo (oscuro sobre Rayo / Incoloro, blanco en el resto). */
    public static int energyInk(int type) { return type == 4 || type == 9 ? 0xFF2A2F3A : 0xFFFFFFFF; }

    // ------------------------------------------------------------------ registro
    public static final int LOG_ME = ME_DARK, LOG_OPP = OPP_DARK;
    public static final int LOG_DAMAGE = 0xFFE23A4E, LOG_HEAL = 0xFF1FA35A, LOG_KO = 0xFF8E1230, LOG_PRIZE = 0xFFC98700,
            LOG_SYSTEM = 0xFF7A8AA0, LOG_EVOLVE = 0xFF8B55E8, LOG_STADIUM = 0xFF2E9E6A;

    /** Categorías de una línea del registro (para el icono y el color). */
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

    // ------------------------------------------------------------------ paneles
    public static final int PANEL_TOP = 0xFFFFFFFF, PANEL_BOTTOM = 0xFFEEF4FB, PANEL_LINE = 0xFFD3E0EE, PANEL_SHINE = 0x66FFFFFF;

    // ------------------------------------------------------------------ utilidades
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
