package dev.tcgshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tcgshop.duel.Cosmetics;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.ptcg.PtcgDuels;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.ChestLoot;
import dev.tcgshop.shop.ShopLife;
import dev.tcgshop.shop.ShopServer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/tcgshop-economy.json — todos los números de la economía que no son precios de cartas/sobres
 * (esos vienen de los datos de mercado y de cobbleDollarsPerUsd en tcgshop.json).
 * Se puede editar con el juego abierto y aplicar con /tcgshop reload (los clientes reciben los valores nuevos).
 */
public class EconomyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-economy.json");

    public static EconomyConfig INSTANCE = new EconomyConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    /** Precio fijo de muebles, herramientas, núcleos de tienda y Mystery Box (id de objeto -> CobbleDollars). */
    public Map<String, Long> itemPrices = new LinkedHashMap<>();
    public long defaultItemPrice = 5_000;
    public long pokedollPrice = 40_000, giganticPokedollPrice = 150_000;

    /** Mayorista y Mercado del teléfono: factor sobre el precio de mercado de sobres, cajas y cartas. */
    public double wholesalerBuyFactor = 0.6, phoneBuyFactor = 0.8;
    /** Venta rápida de cartas: % del valor que pagan el teléfono y el Mayorista. */
    public double phoneSellRate = 0.5, wholesalerSellRate = 0.4;
    /** Precio mínimo de mercado. */
    public long minPackPrice = 100, minBoxPrice = 1_000, minCardPrice = 10;

    /** Empleados: contrato + sueldo diario. */
    public long cashierHire = 30_000, cashierWage = 4_000, guardHire = 45_000, guardWage = 6_000;
    /** Multiplicador de clientes que llegan a tu tienda (1 = normal, 2 = el doble). */
    public double customerDemand = 1.0;

    /** Duelos contra la casa por nivel (fácil, normal, difícil, experto): premio al ganar, multa al perder en liga. */
    public long[] houseDuelRewards = {800, 1_500, 3_000, 6_000};
    public long[] houseDuelPenalties = {200, 400, 800, 1_500};
    /** Multa al perder una partida de liga contra otro jugador. */
    public long pvpPenalty = 600;

    /** Gradeo: coste fijo por carta + % del valor (económico, estándar, exprés). */
    public long[] gradingBaseFees = {2_500, 6_000, 15_000};
    public double[] gradingValuePct = {0.03, 0.06, 0.10};

    /** Probabilidad de que un cofre de estructura traiga un sobre (necesita /reload para aplicarse). */
    public double chestPackChance = 0.01;
    /** Probabilidad por sobre abierto de que salga una moneda de botín. */
    public double lootCoinChancePerPack = 1 / 140.0;
    /** Bloquea los N sets con el sobre más caro: no se venden ni salen en cofres, kits, Mystery Box ni NPCs (0 = nada). */
    public int lockTopPackSets = 5;
    /** Nivel de tienda mínimo para poder COMPRAR esos sets en el teléfono y al Mayorista (wood, stone, iron, gold, diamond, emerald, netherite). */
    public String lockedPacksMinShopTier = "diamond";
    /** Probabilidad, en cualquier cofre, de encontrar un sobre de los sets bloqueados (0.00001 = 0,001 %). */
    public double lockedPackChestChance = 0.00001;

    // --- Clientes y comerciantes ---
    /** Segundos que espera como mucho un cliente que quiere un trato antes de irse. */
    public int customerWaitSeconds = 60;
    /** Al rechazar un trato: probabilidad de reseña negativa (si no, neutral). Educado / de malas maneras. */
    public double rejectNegativeChancePolite = 0.18, rejectNegativeChanceRude = 0.75;
    /** Como mucho estas reseñas negativas por rechazos al día y por tienda (las demás quedan neutrales). */
    public int maxRejectPenaltiesPerDay = 2;
    /** Paciencia de los comerciantes al regatear (multiplicador; 1 = normal). */
    public double merchantPatience = 1.0;

    // --- Reputación ---
    /** Peso de cada nota en la reputación total (se normalizan). */
    public double repWeightAesthetics = 0.35, repWeightValue = 0.40, repWeightSafety = 0.25;
    /** Reputación perdida por matar a un NPC de la tienda (clientes, Mayorista, comerciante, empleados) y cuánto se recupera por día. */
    public int infamyPerKill = 12, infamyDecayPerDay = 3;

    // --- Encuentros especiales ---
    /** Probabilidad (cada medio día) de que aparezca el mendigo de cartas / el mercader negro. */
    public double beggarChance = 0.25, blackMarketChance = 0.02;
    /** Probabilidad de que el mercader negro ofrezca un sobre de los sets bloqueados. */
    public double blackMarketLockedChance = 0.12;

    // --- Notificaciones ---
    /** Anunciar en el chat todos los logros del mod (no sólo los desafíos). */
    public boolean announceAllAdvancements = true;

    private void fillHelp() {
        _help.clear();
        _help.put("aplicar", "Edita este archivo y usa /tcgshop reload (operador) o reinicia. Los precios de cartas/sobres se ajustan con cobbleDollarsPerUsd en tcgshop.json o con un datapack.");
        _help.put("itemPrices", "Precio fijo de cada mueble/herramienta/núcleo de tienda (shop_core_*) en CobbleDollars.");
        _help.put("wholesalerBuyFactor/phoneBuyFactor", "Lo que cuestan sobres, cajas y cartas al comprarlos al Mayorista / en el teléfono, sobre el precio de mercado (0.6 = 60%).");
        _help.put("phoneSellRate/wholesalerSellRate", "Lo que pagan por tus cartas en la venta rápida (0.5 = 50% del valor).");
        _help.put("customerDemand", "Multiplicador de llegada de clientes a tu tienda.");
        _help.put("houseDuelRewards/houseDuelPenalties", "Premio y multa de los duelos contra la casa por nivel: fácil, normal, difícil, experto.");
        _help.put("lockTopPackSets", "Los N sets con el sobre sellado más caro (p.ej. Base Set) no se pueden conseguir: ni tiendas, ni cofres, ni kit de inicio, ni Mystery Box, ni NPCs. 0 = desactivado. Los que ya tengas no se tocan.");
        _help.put("lockedPacksMinShopTier", "Nivel de tienda que necesitas para comprar los sets bloqueados en el teléfono y al Mayorista.");
        _help.put("lockedPackChestChance", "Probabilidad por cofre de encontrar un sobre de los sets bloqueados (0.00001 = 0,001%).");
        _help.put("customerWaitSeconds", "Tiempo máximo que espera un cliente que quiere un trato (tiene una barra sobre la cabeza).");
        _help.put("rejectNegativeChancePolite/Rude", "Probabilidad de reseña negativa al rechazar un trato con educación / de malas maneras. Si no, la reseña es neutral.");
        _help.put("repWeightAesthetics/Value/Safety", "Peso de Estética, Calidad/precio y Seguridad en la reputación total.");
        _help.put("infamyPerKill/infamyDecayPerDay", "Reputación que pierdes por matar a un NPC de la tienda y cuánta recuperas cada día.");
        _help.put("beggarChance/blackMarketChance", "Probabilidad cada medio día de Minecraft de que aparezca el mendigo de cartas / el mercader negro (rarísimo).");
        _help.put("gradingBaseFees/gradingValuePct", "Coste del gradeo por carta: fijo + % del valor (económico, estándar, exprés).");
    }

    // ------------------------------------------------------------------ carga

    /** Llamar después de registrar los objetos (ShopServer.init) para conocer los precios por defecto. */
    public static void load() {
        EconomyConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, EconomyConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("No se pudo leer {} (se usan los valores por defecto)", FILE, e);
        }
        if (c == null) c = new EconomyConfig();
        c.sanitize();
        INSTANCE = c;
        c.fillHelp();
        save();
        apply(c);
    }

    private void sanitize() {
        if (itemPrices == null) itemPrices = new LinkedHashMap<>();
        // añade los objetos que falten con su precio por defecto (nuevos objetos en actualizaciones)
        Map<String, Long> merged = new LinkedHashMap<>(ShopServer.defaultFixedPrices());
        for (Map.Entry<String, Long> e : itemPrices.entrySet()) if (e.getValue() != null) merged.put(e.getKey(), Math.max(0, e.getValue()));
        itemPrices = merged;
        houseDuelRewards = len(houseDuelRewards, new long[]{800, 1_500, 3_000, 6_000});
        houseDuelPenalties = len(houseDuelPenalties, new long[]{200, 400, 800, 1_500});
        gradingBaseFees = len(gradingBaseFees, new long[]{2_500, 6_000, 15_000});
        if (gradingValuePct == null || gradingValuePct.length != 3) gradingValuePct = new double[]{0.03, 0.06, 0.10};
        wholesalerBuyFactor = clamp(wholesalerBuyFactor, 0.01, 100);
        phoneBuyFactor = clamp(phoneBuyFactor, 0.01, 100);
        phoneSellRate = clamp(phoneSellRate, 0, 10);
        wholesalerSellRate = clamp(wholesalerSellRate, 0, 10);
        customerDemand = clamp(customerDemand, 0, 20);
        chestPackChance = clamp(chestPackChance, 0, 1);
        lootCoinChancePerPack = clamp(lootCoinChancePerPack, 0, 1);
        lockTopPackSets = Math.max(0, Math.min(50, lockTopPackSets));
        if (lockedPacksMinShopTier == null) lockedPacksMinShopTier = "diamond";
        lockedPackChestChance = clamp(lockedPackChestChance, 0, 1);
        customerWaitSeconds = Math.max(10, Math.min(600, customerWaitSeconds));
        rejectNegativeChancePolite = clamp(rejectNegativeChancePolite, 0, 1);
        rejectNegativeChanceRude = clamp(rejectNegativeChanceRude, 0, 1);
        maxRejectPenaltiesPerDay = Math.max(0, maxRejectPenaltiesPerDay);
        merchantPatience = clamp(merchantPatience, 0.2, 5);
        repWeightAesthetics = clamp(repWeightAesthetics, 0, 10);
        repWeightValue = clamp(repWeightValue, 0, 10);
        repWeightSafety = clamp(repWeightSafety, 0, 10);
        if (repWeightAesthetics + repWeightValue + repWeightSafety <= 0) { repWeightAesthetics = 0.35; repWeightValue = 0.4; repWeightSafety = 0.25; }
        infamyPerKill = Math.max(0, Math.min(100, infamyPerKill));
        infamyDecayPerDay = Math.max(0, Math.min(100, infamyDecayPerDay));
        beggarChance = clamp(beggarChance, 0, 1);
        blackMarketChance = clamp(blackMarketChance, 0, 1);
        blackMarketLockedChance = clamp(blackMarketLockedChance, 0, 1);
        if (_help == null) _help = new LinkedHashMap<>();
    }

    private static long[] len(long[] a, long[] def) {
        if (a == null || a.length != def.length) return def;
        for (int i = 0; i < a.length; i++) a[i] = Math.max(0, a[i]);
        return a;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(INSTANCE, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("No se pudo guardar {}", FILE, e);
        }
    }

    /** Copia los valores a las constantes de cada sistema (servidor y, al recibir la sincronización, cliente). */
    private static void apply(EconomyConfig c) {
        ShopServer.applyEconomy(c);
        CardSales.PHONE_RATE = c.phoneSellRate;
        CardSales.WHOLESALER_RATE = c.wholesalerSellRate;
        PtcgDuels.LEVEL_REWARD = c.houseDuelRewards.clone();
        PtcgDuels.LEVEL_PENALTY = c.houseDuelPenalties.clone();
        PtcgDuels.PVP_PENALTY = c.pvpPenalty;
        Grading.Service[] sv = Grading.Service.values();
        for (int i = 0; i < sv.length && i < 3; i++) {
            sv[i].baseFee = c.gradingBaseFees[i];
            sv[i].pct = c.gradingValuePct[i];
        }
        ChestLoot.CHANCE = (float) c.chestPackChance;
        Cosmetics.LOOT_CHANCE = c.lootCoinChancePerPack;
        ShopLife.DEMAND = c.customerDemand;
    }

    /** Para enviar al cliente. */
    public static String toJson() {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.add("economy", GSON.toJsonTree(INSTANCE));
        o.add("vanilla", GSON.toJsonTree(dev.tcgshop.shop.VanillaPrices.table()));
        return GSON.toJson(o);
    }

    /** Cliente: aplica los valores del servidor (sin tocar el archivo local). */
    public static void applyJson(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (o.has("vanilla")) dev.tcgshop.shop.VanillaPrices.applySync(o.getAsJsonObject("vanilla"));
            EconomyConfig c = GSON.fromJson(o.has("economy") ? o.get("economy") : o, EconomyConfig.class);
            if (c == null) return;
            c.sanitize();
            INSTANCE = c;
            apply(c);
        } catch (Exception e) {
            TcgShop.LOGGER.warn("Configuración de economía del servidor inválida", e);
        }
    }
}
