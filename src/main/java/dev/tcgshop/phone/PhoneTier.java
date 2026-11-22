package dev.tcgshop.phone;

import java.util.Map;

/**
 * Niveles del teléfono TCG (0.9). El nivel es del JUGADOR (no del objeto): si pierdes el teléfono, el nuevo sigue igual.
 *
 * <pre>
 * Nivel  Nombre               Coste       Desbloquea
 * 0      Básico               gratis      Mercado básico (sobres modernos, muebles, bloques de tienda, herramientas),
 *                                         Mi tienda, Colección, Venta rápida, Amigos, Títulos, Logros, Ajustes, Mejoras
 * 1      Plus                 20.000      app Precios · sobres hasta $20 · Mystery Box en el Mercado
 * 2      Pro                  60.000      apps Expositores y Muebles · cajas de sobres en el Mercado · sobres hasta $50 · −3% Mercado
 * 3      Ultra                150.000     app CobbleGrade · peluches Poké · sobres hasta $150 · venta rápida +3%
 * 4      Max                  400.000     app Liga TCG · Ruleta Poké · sobres hasta $500 · −6% Mercado
 * 5      Élite                1.200.000   sobres hasta $2000 (vintage) · Mayorista con +4 sets de cajas y −3% · −8% Mercado · venta +5%
 * 6      MasterBall Edition   5.000.000   TODO (también los sets bloqueados por nivel de tienda) · −12% Mercado · Mayorista −5% ·
 *                                         venta +8% · 1 llamada al día: Coleccionista o Mayorista
 * </pre>
 * Los costes y los límites de precio se cambian en config/tcgshop-phone.json.
 */
public enum PhoneTier {
    BASIC("basic", 0xFF8A93A6),
    PLUS("plus", 0xFF43A047),
    PRO("pro", 0xFF2F80ED),
    ULTRA("ultra", 0xFF9B51E0),
    MAX("max", 0xFFF2994A),
    ELITE("elite", 0xFFE04848),
    MASTERBALL("masterball", 0xFFB3262E);

    public final String key;
    public final int color;

    PhoneTier(String key, int color) {
        this.key = key;
        this.color = color;
    }

    public static final int MAX_LEVEL = values().length - 1;

    public static PhoneTier of(int level) {
        return values()[Math.max(0, Math.min(MAX_LEVEL, level))];
    }

    /** Coste por defecto de subir A este nivel (índice = nivel). */
    public static final long[] DEFAULT_COSTS = {0, 20_000, 60_000, 150_000, 400_000, 1_200_000, 5_000_000};
    /** Precio máximo (USD de mercado del SOBRE del set) que se puede comprar en cada nivel; el último no tiene límite. */
    public static final double[] DEFAULT_PACK_USD = {8, 20, 50, 150, 500, 2_000};

    /** Multiplicador del precio del Mercado del teléfono por nivel. */
    public static final double[] PHONE_DISCOUNT = {1.0, 1.0, 0.97, 0.97, 0.94, 0.92, 0.88};
    /** Multiplicador del precio del Mayorista por nivel. */
    public static final double[] WHOLESALE_DISCOUNT = {1.0, 1.0, 1.0, 1.0, 1.0, 0.97, 0.95};
    /** Extra sobre la tasa de venta rápida del teléfono (0.03 = +3 puntos). */
    public static final double[] SELL_BONUS = {0, 0, 0, 0.03, 0.03, 0.05, 0.08};
    /** Sets de cajas extra que trae el Mayorista. */
    public static final int[] EXTRA_BOX_SETS = {0, 0, 0, 0, 0, 4, 8};

    /** Nivel mínimo de cada app del teléfono (las que no salen aquí: nivel 0). */
    public static final Map<String, Integer> APP_TIER = Map.of(
            "prices", 1,
            "displays", 2,
            "furniture", 2,
            "grade", 3,
            "league", 4);

    public static int appTier(String app) {
        return APP_TIER.getOrDefault(app, 0);
    }

    /** Nivel con la llamada diaria (Coleccionista / Mayorista). */
    public static final int CALL_TIER = MAX_LEVEL;
    /** Nivel que se salta el bloqueo de sets por nivel de tienda (PackLock). */
    public static final int PACKLOCK_BYPASS_TIER = MAX_LEVEL;
    /** Nivel desde el que el Mercado del teléfono vende cajas / Mystery Box / peluches / Ruleta. */
    public static final int PHONE_BOX_TIER = 2, PHONE_MYSTERY_TIER = 1, PHONE_PLUSH_TIER = 3, ROULETTE_TIER = 4;
}
