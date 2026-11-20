package dev.tcgshop.pricing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tcgshop.TcgShop;
import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Precios de cartas y productos sellados en CobbleDollars.
 *
 * <h2>Precio base (USD)</h2>
 * <ol>
 *   <li>Precio real de mercado TCGplayer de esa carta (data/&lt;ns&gt;/tcgshop_prices/*.json, campo cards_usd),
 *       generado con tools/fetch_prices.py desde tcgcsv.com. Cubre el 99% de las cartas de cobbletcg 1.6.0.</li>
 *   <li>Si no hay: mediana real de su set y rareza (rarity_median_usd) × variación estable por carta.</li>
 *   <li>Si tampoco: mediana global de la rareza ("*") o el valor de {@link Rarity#baseUsd}.</li>
 * </ol>
 *
 * <h2>Precio actual (CobbleDollars)</h2>
 * base × {@link MarketModel tendencia del mercado del día} × {@link TcgShopConfig#cobbleDollarsPerUsd}.
 *
 * Los datapacks pueden sobreescribir cualquier precio: los archivos se fusionan en orden de carga.
 * El servidor envía la tabla al cliente al entrar (PriceSyncPayload) y el día de mercado al cambiar (MarketDayPayload).
 */
public final class PriceService {
    public static final String DATA_FOLDER = "tcgshop_prices";

    /** id de carta -> USD */
    private static Map<String, Double> cardUsd = new HashMap<>();
    /** "set|tipo" -> USD   (tipo = pack, box, etb) */
    private static Map<String, Double> sealedUsd = new HashMap<>();
    /** "set|rareza" -> USD  (set "*" = global) */
    private static Map<String, Double> rarityMedianUsd = new HashMap<>();

    private static double cdPerUsd = 250.0;
    private static long marketSeed = 0;
    private static double marketVolatility = 1.0;
    private static long marketDay = 0;

    private PriceService() {}

    // ------------------------------------------------------------------ cartas

    public static long priceCd(ItemStack stack) {
        if (!CardInfo.isCard(stack)) return 0;
        long raw = priceCd(CardInfo.id(stack), Rarity.of(stack));
        int g = dev.tcgshop.grading.Grading.grade(stack);
        return g > 0 ? Math.max(1, Math.round(raw * dev.tcgshop.grading.Grading.multiplier(g))) : raw;
    }

    public static long priceCd(Identifier id, Rarity rarity) {
        return priceCdOnDay(id, rarity, marketDay);
    }

    /** Precio en un día de mercado concreto (para historial y gráficas). */
    public static long priceCdOnDay(Identifier id, Rarity rarity, long day) {
        double usd = baseUsd(id, rarity) * marketMultiplier(id, rarity, day);
        return toCd(usd);
    }

    public static double marketMultiplier(Identifier id, Rarity rarity, long day) {
        return MarketModel.multiplier(marketSeed, CardInfo.setId(id), id.toString(), rarity.tier, day, marketVolatility);
    }

    /** Precio de referencia sin la tendencia del mercado, en USD. */
    public static double baseUsd(Identifier id, Rarity rarity) {
        Double real = cardUsd.get(id.toString());
        if (real != null) return real;
        Double median = rarityMedianUsd.get(CardInfo.setId(id) + "|" + rarity.key);
        if (median == null) median = rarityMedianUsd.get("*|" + rarity.key);
        if (median == null) median = rarity.baseUsd;
        return median * variance(id);
    }

    public static boolean hasRealPrice(Identifier id) {
        return cardUsd.containsKey(id.toString());
    }

    // ------------------------------------------------------------------ sellados

    public enum Sealed {
        PACK("pack", 4.5), BOX("box", 160.0), ETB("etb", 55.0);
        public final String key;
        public final double defaultUsd;
        Sealed(String key, double defaultUsd) { this.key = key; this.defaultUsd = defaultUsd; }
    }

    /** Precio de mercado de un producto sellado del set (cobbletcg set id, p.ej. "mew151"). */
    public static long sealedCd(String setId, Sealed kind) {
        Double usd = sealedUsd.get(setId + "|" + kind.key);
        if (usd == null && kind == Sealed.PACK) {
            Double box = sealedUsd.get(setId + "|box");
            if (box != null) usd = box / 36.0;
        }
        if (usd == null && kind != Sealed.PACK) { // sin dato: a partir del sobre del set (36 por caja, ~10 por ETB)
            Double pack = sealedUsd.get(setId + "|pack");
            if (pack != null) usd = pack * (kind == Sealed.BOX ? 36 : 10);
        }
        if (usd == null) usd = kind.defaultUsd;
        double mult = MarketModel.multiplier(marketSeed, setId, setId + "#" + kind.key, 0, marketDay, marketVolatility);
        return toCd(usd * mult);
    }

    // ------------------------------------------------------------------ util

    private static long toCd(double usd) {
        return Math.max(1L, Math.round(usd * cdPerUsd));
    }

    /** Variación estable por carta (0.41x .. 2.46x, aprox. log-normal) para las estimaciones. */
    private static double variance(Identifier id) {
        long h = id.toString().hashCode() * 0x9E3779B97F4A7C15L;
        double a = ((h >>> 1) & 0xFFFF) / 65535.0;
        double b = ((h >>> 17) & 0xFFFF) / 65535.0;
        double c = ((h >>> 33) & 0xFFFF) / 65535.0;
        return Math.exp(((a + b + c) / 3.0 - 0.5) * 1.8);
    }

    public static String format(long cd) {
        String n = NumberFormat.getIntegerInstance(Locale.ROOT).format(cd).replace(',', '.');
        return n + TcgShopConfig.INSTANCE.currencySuffix;
    }

    // ------------------------------------------------------------------ sincronización

    public static Map<String, Double> cardUsdView() { return cardUsd; }
    public static Map<String, Double> sealedUsdView() { return sealedUsd; }
    public static Map<String, Double> rarityMedianView() { return rarityMedianUsd; }
    public static double cdPerUsd() { return cdPerUsd; }
    public static long marketSeed() { return marketSeed; }
    public static double marketVolatility() { return marketVolatility; }
    public static long marketDay() { return marketDay; }

    public static void setMarketDay(long day) { marketDay = day; }

    /** Cliente: aplica la tabla recibida del servidor. */
    public static void applySync(Map<String, Double> cards, Map<String, Double> sealed, Map<String, Double> medians,
                                 double rate, long seed, double volatility, long day) {
        cardUsd = new HashMap<>(cards);
        sealedUsd = new HashMap<>(sealed);
        rarityMedianUsd = new HashMap<>(medians);
        cdPerUsd = rate;
        marketSeed = seed;
        marketVolatility = volatility;
        marketDay = day;
    }

    /** Servidor: refresca los parámetros de la config (al cargar datos o el mundo). */
    public static void applyConfig() {
        TcgShopConfig c = TcgShopConfig.INSTANCE;
        cdPerUsd = c.cobbleDollarsPerUsd;
        marketSeed = c.marketSeed;
        marketVolatility = c.marketEnabled ? c.marketVolatility : 0;
    }

    // ------------------------------------------------------------------ carga de datos

    public static class ReloadListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return Identifier.of(TcgShop.MOD_ID, "prices");
        }

        @Override
        public void reload(ResourceManager manager) {
            Map<String, Double> cards = new HashMap<>(), sealed = new HashMap<>(), medians = new HashMap<>();
            Map<Identifier, List<Resource>> files = manager.findAllResources(DATA_FOLDER, p -> p.getPath().endsWith(".json"));
            for (Map.Entry<Identifier, List<Resource>> e : files.entrySet()) {
                for (Resource res : e.getValue()) { // todos los datapacks, en orden: el último gana
                    try (Reader r = res.getReader()) {
                        JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                        readFlat(root, "cards_usd", cards);
                        readNested(root, "sealed_usd", sealed);
                        readNested(root, "rarity_median_usd", medians);
                    } catch (Exception ex) {
                        TcgShop.LOGGER.error("Archivo de precios inválido: {}", e.getKey(), ex);
                    }
                }
            }
            cardUsd = cards;
            sealedUsd = sealed;
            rarityMedianUsd = medians;
            applyConfig();
            TcgShop.LOGGER.info("Precios: {} cartas con precio real, {} productos sellados, {} medianas", cards.size(), sealed.size(), medians.size());
        }

        private static void readFlat(JsonObject root, String key, Map<String, Double> out) {
            if (!root.has(key)) return;
            for (Map.Entry<String, JsonElement> c : root.getAsJsonObject(key).entrySet())
                out.put(c.getKey(), c.getValue().getAsDouble());
        }

        private static void readNested(JsonObject root, String key, Map<String, Double> out) {
            if (!root.has(key)) return;
            for (Map.Entry<String, JsonElement> s : root.getAsJsonObject(key).entrySet())
                for (Map.Entry<String, JsonElement> k : s.getValue().getAsJsonObject().entrySet())
                    out.put(s.getKey() + "|" + k.getKey(), k.getValue().getAsDouble());
        }
    }
}
