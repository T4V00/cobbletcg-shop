package dev.tcgshop.phone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tcgshop.TcgShop;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** config/tcgshop-phone.json — mejoras del teléfono (0.9). Se lee al arrancar y con PhoneConfig.load(). */
public class PhoneConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tcgshop-phone.json");

    public static PhoneConfig INSTANCE = new PhoneConfig();

    public Map<String, String> _help = new LinkedHashMap<>();

    /** false = sistema de mejoras desactivado: todos tienen todo desbloqueado (menos la llamada diaria si callsEnabled = false). */
    public boolean enabled = true;
    /** Coste de cada nivel (índice 0 = Básico, siempre gratis). */
    public long[] tierCosts = PhoneTier.DEFAULT_COSTS.clone();
    /** Precio máximo del sobre (USD de mercado) por nivel 0..5; en el nivel 6 no hay límite. */
    public double[] packUsdLimits = PhoneTier.DEFAULT_PACK_USD.clone();
    /** Llamada diaria del último nivel (Coleccionista o Mayorista). */
    public boolean callsEnabled = true;
    /** Nivel con el que empiezan los jugadores que ya tenían tienda / ya habían jugado antes de la 0.9. */
    public int legacyShopOwnerTier = 2, legacyPlayerTier = 1;
    /** Los jugadores de antes de la 0.9 conservan TODAS las apps que ya usaban (Precios, CobbleGrade, Liga...). */
    public boolean legacyKeepsApps = true;

    private void fillHelp() {
        _help.clear();
        _help.put("enabled", "false = sin mejoras: todo el mundo tiene el teléfono completo.");
        _help.put("tierCosts", "Coste en CobbleDollars de cada nivel: Básico, Plus, Pro, Ultra, Max, Élite, MasterBall Edition.");
        _help.put("packUsdLimits", "Precio máximo del sobre (USD de mercado) de los sets que puedes comprar en los niveles 0..5 (cajas incluidas). El nivel 6 no tiene límite.");
        _help.put("callsEnabled", "Llamada diaria del nivel MasterBall Edition (Coleccionista o Mayorista).");
        _help.put("legacyShopOwnerTier/legacyPlayerTier", "Nivel inicial de quien ya tenía tienda / ya había jugado antes de la 0.9.");
        _help.put("legacyKeepsApps", "Los jugadores de antes de la 0.9 conservan todas las apps que ya tenían.");
    }

    private void sanitize() {
        long[] c = PhoneTier.DEFAULT_COSTS.clone();
        if (tierCosts != null) for (int i = 1; i < Math.min(c.length, tierCosts.length); i++) c[i] = Math.max(0, Math.min(1_000_000_000_000L, tierCosts[i]));
        c[0] = 0;
        tierCosts = c;
        double[] u = PhoneTier.DEFAULT_PACK_USD.clone();
        if (packUsdLimits != null) for (int i = 0; i < Math.min(u.length, packUsdLimits.length); i++) u[i] = Math.max(0, packUsdLimits[i]);
        packUsdLimits = u;
        legacyShopOwnerTier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, legacyShopOwnerTier));
        legacyPlayerTier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, legacyPlayerTier));
    }

    public long cost(int level) {
        return level <= 0 || level > PhoneTier.MAX_LEVEL ? 0 : tierCosts[level];
    }

    public static void load() {
        PhoneConfig c = null;
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) { c = GSON.fromJson(r, PhoneConfig.class); }
        } catch (Exception e) {
            TcgShop.LOGGER.error("No se pudo leer {} (se usan los valores por defecto)", FILE, e);
        }
        if (c == null) c = new PhoneConfig();
        c.sanitize();
        c.fillHelp();
        INSTANCE = c;
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(c, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("No se pudo guardar {}", FILE, e);
        }
    }
}
