package dev.tcgshop.onboarding;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.TcgShopConfig;
import dev.tcgshop.compat.CobblemonSpawn;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bienvenida al mod: N sobres (TcgShopConfig.starterPackCount, 3 por defecto) sellados al azar de los sets más baratos la primera vez que entras al mundo, y el
 * tutorial del "Meowth del Team Rocket" la primera vez que creas una tienda (aparece un Meowth salvaje normal y
 * capturable a tu lado, se queda quieto mirándote y te da unos consejos cortos en bocadillos: ver MeowthTalk). Las marcas se guardan como etiquetas del jugador
 * (se guardan con sus datos, así que valen para siempre en ese mundo).
 */
public final class Onboarding {
    /** Misma etiqueta que el kit de inicio antiguo (CardSales): quien ya lo recibió no vuelve a recibir sobres. */
    public static final String STARTER_TAG = CardSales.STARTER_TAG;
    public static final String TUTORIAL_TAG = "tcgshop_tutorial";
    /** Sobres que se regalan al empezar si la config no dice otra cosa (TcgShopConfig.starterPackCount). */
    public static final int STARTER_COUNT = 3;
    /** Se eligen entre los N sets más baratos (por precio del sobre). */
    public static final int CHEAPEST_SETS = 8;
    /** Propiedades de Cobblemon del Meowth del tutorial. */
    public static final String MEOWTH_PROPS = "meowth level=10";

    private Onboarding() {}

    // ------------------------------------------------------------------ sobres de inicio

    /** Al entrar al mundo (ServerPlayConnectionEvents.JOIN): sobres de bienvenida sólo la primera vez. */
    public static void onJoin(ServerPlayerEntity player) {
        // quien ya tiene tienda (mundos de antes del tutorial) no necesita el Meowth: se marca como visto
        if (!player.getCommandTags().contains(TUTORIAL_TAG) && ownedShops(player) > 0) player.addCommandTag(TUTORIAL_TAG);
        if (!TcgShopConfig.INSTANCE.starterPacks || player.getCommandTags().contains(STARTER_TAG)) return;
        int count = Math.max(0, TcgShopConfig.INSTANCE.starterPackCount);
        if (count == 0) { // config a 0: sin sobres, sólo se marca para no volver a mirarlo
            player.addCommandTag(STARTER_TAG);
            return;
        }
        List<Item> picks = pickStarterPacks(player.getRandom(), count);
        if (picks.isEmpty()) return; // aún no hay sobres registrados: se reintenta en la próxima entrada
        player.addCommandTag(STARTER_TAG);
        for (Item it : picks) player.getInventory().offerOrDrop(new ItemStack(it));
        player.sendMessage(Text.translatable("tcgshop.onboarding.starter", picks.size()).formatted(Formatting.GOLD), false);
        player.sendMessage(Text.translatable("tcgshop.onboarding.starter_hint").formatted(Formatting.GRAY), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    /**
     * {@code count} sobres sellados al azar de los {@link #CHEAPEST_SETS} sets (no bloqueados) con el sobre más barato.
     * Intenta que sean de sets distintos; si hay menos sets que sobres, repite.
     */
    public static List<Item> pickStarterPacks(Random random, int count) {
        Map<String, List<Item>> bySet = new LinkedHashMap<>();
        for (Identifier id : Registries.ITEM.getIds()) {
            if (!CardInfo.NS.equals(id.getNamespace()) || !id.getPath().contains("/sealed_")) continue;
            String set = CardInfo.setId(id);
            if (set == null || set.isEmpty() || PackLock.locked(set)) continue;
            bySet.computeIfAbsent(set, k -> new ArrayList<>()).add(Registries.ITEM.get(id));
        }
        List<Item> out = new ArrayList<>();
        if (bySet.isEmpty() || count <= 0) return out;
        Map<String, Double> usd = new LinkedHashMap<>();
        for (String s : bySet.keySet()) {
            double v;
            try {
                v = PriceService.sealedUsd(s, PriceService.Sealed.PACK);
            } catch (Throwable e) {
                v = PriceService.Sealed.PACK.defaultUsd;
            }
            usd.put(s, v);
        }
        List<String> sets = new ArrayList<>(bySet.keySet());
        sets.sort(Comparator.comparingDouble(usd::get));
        List<String> pool = new ArrayList<>(sets.subList(0, Math.min(CHEAPEST_SETS, sets.size())));
        List<String> left = new ArrayList<>(pool);
        for (int i = 0; i < count; i++) {
            if (left.isEmpty()) left.addAll(pool);
            String set = left.remove(random.nextInt(left.size()));
            List<Item> items = bySet.get(set);
            out.add(items.get(random.nextInt(items.size())));
        }
        return out;
    }

    // ------------------------------------------------------------------ tutorial del Meowth

    /** Nombre del Meowth (traducible: cada cliente lo ve en su idioma). */
    public static Text meowthName() {
        return Text.translatable("tcgshop.tutorial.speaker");
    }

    /**
     * Al crear una tienda (ShopCoreBlock.onPlaced, después de crear la zona): si es la primera tienda del jugador,
     * aparece el Meowth del Team Rocket a su lado, se queda quieto mirándole y le da unos consejos en bocadillos.
     * Nada de spam en el chat: como mucho una línea corta (y la pista de "clic derecho" va a la barra de acción).
     */
    public static void onShopCreated(ServerPlayerEntity player) {
        if (player.getCommandTags().contains(TUTORIAL_TAG)) return;
        player.addCommandTag(TUTORIAL_TAG);
        // la zona nueva ya está creada: si tiene más de una, es un veterano (mundo de antes del tutorial)
        if (ownedShops(player) > 1) return;
        Entity meowth = MeowthTalk.findNear(player); // nunca dos Meowth: si ya hay uno suyo cerca, habla ese
        if (meowth == null) meowth = spawnMeowth(player);
        if (meowth != null && MeowthTalk.start(player, meowth)) {
            player.sendMessage(Text.translatable("tcgshop.tutorial.appears", meowthName().copy().formatted(Formatting.GOLD)).formatted(Formatting.YELLOW), true);
        } else {
            player.sendMessage(Text.translatable("tcgshop.tutorial.no_meowth").formatted(Formatting.GRAY), false);
        }
    }

    /** Tiendas (zonas) del jugador en todas las dimensiones del servidor. */
    public static int ownedShops(ServerPlayerEntity player) {
        if (player.getServer() == null) return 0;
        int n = 0;
        for (ServerWorld w : player.getServer().getWorlds())
            for (ShopZones.Zone z : ShopZones.get(w).all())
                if (player.getUuid().equals(z.owner)) n++;
        return n;
    }

    /**
     * /tcgshop tutorial: si su Meowth está cerca, vuelve a decir los bocadillos; si no, las mismas frases cortas por
     * el chat (sin sacar otro Meowth).
     */
    public static int replay(ServerPlayerEntity player) {
        Entity meowth = MeowthTalk.findNear(player);
        if (meowth != null && MeowthTalk.start(player, meowth)) {
            player.sendMessage(Text.translatable("tcgshop.tutorial.appears", meowthName().copy().formatted(Formatting.GOLD)).formatted(Formatting.YELLOW), true);
            return 1;
        }
        MeowthTalk.stop(player); // una charla pausada lejos no se reanuda luego repitiendo lo mismo
        for (int i = 0; i < MeowthTalk.LINES; i++) player.sendMessage(MeowthTalk.chatLine(MeowthTalk.line(i)), false);
        return 1;
    }

    /** Meowth salvaje normal (capturable, persistente) con el mote "Meowth del Team Rocket". Null si no hay Cobblemon. */
    public static Entity spawnMeowth(ServerPlayerEntity player) {
        if (!CobblemonSpawn.available()) return null;
        ServerWorld world = player.getServerWorld();
        Vec3d spot = CobblemonSpawn.freeSpotNear(world, player.getPos(), player.getRotationVector());
        if (spot == null) spot = player.getPos();
        // que mire hacia el jugador
        Vec3d d = player.getPos().subtract(spot);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        Entity e = CobblemonSpawn.spawnWild(world, spot, yaw, MEOWTH_PROPS, Text.translatable("tcgshop.tutorial.speaker"));
        if (e == null) return null;
        MeowthTalk.tag(e, player);
        world.spawnParticles(ParticleTypes.POOF, spot.x, spot.y + 0.5, spot.z, 14, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.WAX_ON, spot.x, spot.y + 0.8, spot.z, 8, 0.4, 0.4, 0.4, 0.05);
        world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_CAT_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 1.1f);
        return e;
    }
}
