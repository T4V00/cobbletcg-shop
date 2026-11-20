package dev.tcgshop.pricing;

/**
 * Mercado dinámico: multiplica el precio base por una tendencia que cambia cada "día de mercado".
 *
 * Es 100% determinista (ruido suave a partir de una semilla del servidor), así que:
 *  - cliente y servidor calculan exactamente lo mismo sin enviar miles de precios cada día,
 *  - el historial (gráficas del teléfono) se obtiene evaluando días pasados, sin guardar nada.
 *
 * Tendencia = ola lenta por set (hype de una colección) + ola rápida del set + oscilación propia de la carta.
 * Las cartas más raras son más volátiles, como en el mercado real.
 */
public final class MarketModel {
    public static final double MIN_MULT = 0.5, MAX_MULT = 2.0;

    private MarketModel() {}

    public static double multiplier(long seed, String setId, String cardId, int rarityTier, double day, double volatility) {
        if (volatility <= 0) return 1.0;
        double set = 0.16 * noise(seed, setId, day / 9.0) + 0.06 * noise(seed ^ 0x5DEECE66DL, setId, day / 3.0);
        double card = 0.10 * (1 + rarityTier * 0.4) * noise(seed ^ 0xBF58476D1CE4E5B9L, cardId, day / 4.0);
        double m = Math.exp(volatility * (set + card));
        return Math.max(MIN_MULT, Math.min(MAX_MULT, m));
    }

    /** Ruido de valor 1D suavizado, en [-1, 1]. */
    static double noise(long seed, String key, double x) {
        long i = (long) Math.floor(x);
        double f = x - i;
        double a = hash(seed, key, i), b = hash(seed, key, i + 1);
        double t = f * f * (3 - 2 * f);
        return a + (b - a) * t;
    }

    private static double hash(long seed, String key, long i) {
        long h = seed ^ (key.hashCode() * 0x9E3779B97F4A7C15L) ^ (i * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}
