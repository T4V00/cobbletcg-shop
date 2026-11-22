package dev.tcgshop.phone;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.TcgShop;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.TraderEntity;
import dev.tcgshop.entity.WholesalerEntity;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mejoras del teléfono (0.9), lado servidor: nivel por jugador, compra de mejoras, candados del Mercado y del
 * Mayorista, descuentos, bonus de venta y la llamada diaria del último nivel. Ver la tabla en {@link PhoneTier}.
 */
public final class PhoneUpgrades {
    private PhoneUpgrades() {}

    /** Llamar UNA vez desde TcgShop.onInitialize (registra la red, la migración al entrar y lee la config). */
    public static void register() {
        PhoneConfig.load();
        PayloadTypeRegistry.playS2C().register(PhonePayloads.Sync.ID, PhonePayloads.Sync.CODEC);
        PayloadTypeRegistry.playC2S().register(PhonePayloads.Act.ID, PhonePayloads.Act.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PhonePayloads.Act.ID, (p, ctx) -> ctx.server().execute(() -> handle(ctx.player(), p.data())));
        // se ejecuta antes que el kit de inicio de Onboarding (que va diferido con server.execute): un jugador nuevo aún no tiene la etiqueta
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity sp = handler.getPlayer();
            migrate(sp);
            server.execute(() -> sync(sp, null, null));
        });
    }

    // ------------------------------------------------------------------ nivel

    private static PhoneState.Entry entry(ServerPlayerEntity p) {
        return PhoneState.get(p.getServer()).getOrCreate(p.getUuid());
    }

    /** Nivel efectivo (con el sistema desactivado, todos tienen el último: apps, catálogo y llamada diaria). */
    public static int level(ServerPlayerEntity p) {
        if (!PhoneConfig.INSTANCE.enabled) return PhoneTier.MAX_LEVEL;
        return entry(p).tier;
    }

    /**
     * Nivel para las ventajas económicas (descuentos, bonus de venta, sets extra del Mayorista, saltarse PackLock).
     * Con el sistema desactivado es 0: se vuelve a la economía de antes de la 0.9 en vez de regalar las del último nivel.
     */
    public static int perkLevel(ServerPlayerEntity p) {
        if (!PhoneConfig.INSTANCE.enabled) return 0;
        return entry(p).tier;
    }

    public static boolean appUnlocked(ServerPlayerEntity p, String app) {
        if (!PhoneConfig.INSTANCE.enabled) return true;
        PhoneState.Entry e = entry(p);
        return (e.legacy && PhoneConfig.INSTANCE.legacyKeepsApps) || e.tier >= PhoneTier.appTier(app);
    }

    /**
     * Comprobación en el servidor de una app del teléfono (los candados de la interfaz sólo frenan a los clientes
     * honrados): si está bloqueada avisa al jugador y devuelve false.
     */
    public static boolean requireApp(ServerPlayerEntity p, String app) {
        if (appUnlocked(p, app)) return true;
        p.sendMessage(Text.translatable("tcgshop.phoneup.requires", tierName(PhoneTier.appTier(app))).formatted(Formatting.RED), true);
        return false;
    }

    /**
     * Acciones de la Liga que sólo da la app Liga: partidas clasificatorias contra la IA, la cola online y la IA de
     * nivel alto. La revancha de una partida de mesa (casual, nivel 1), los desafíos y los cosméticos no se bloquean.
     */
    public static boolean leagueActionAllowed(ServerPlayerEntity p, String json) {
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (!o.has("a") || !"play".equals(o.get("a").getAsString())) return true;
            String mode = o.has("mode") ? o.get("mode").getAsString() : "casual";
            int lvl = o.has("level") ? o.get("level").getAsInt() : 1;
            if (mode.equals("cancel_queue") || (!mode.equals("ranked_ai") && !mode.equals("queue") && lvl <= 1)) return true;
        } catch (Exception ex) {
            return true; // PtcgDuels.league descarta el paquete malformado
        }
        return requireApp(p, "league");
    }

    /** Bonus sobre la tasa de venta rápida del teléfono (CardSales: rate = PHONE_RATE + sellBonus). */
    public static double sellBonus(ServerPlayerEntity p) {
        return PhoneTier.SELL_BONUS[perkLevel(p)];
    }

    /**
     * Migración a la 0.9: quien ya tenía tienda empieza en Pro y quien ya había jugado (kit de inicio o compras) en Plus;
     * ambos conservan todas las apps que ya usaban. Un jugador nuevo empieza en Básico.
     */
    static void migrate(ServerPlayerEntity sp) {
        PhoneState st = PhoneState.get(sp.getServer());
        if (st.find(sp.getUuid()) != null) return;
        PhoneState.Entry e = st.getOrCreate(sp.getUuid());
        boolean owner = false;
        long spent = 0;
        for (ServerWorld w : sp.getServer().getWorlds()) {
            ShopZones zones = ShopZones.get(w);
            spent += zones.costs(sp.getUuid());
            for (ShopZones.Zone z : zones.all()) if (z.owner.equals(sp.getUuid())) owner = true;
        }
        boolean played = sp.getCommandTags().contains(CardSales.STARTER_TAG) || spent > 0;
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (owner) { e.tier = c.legacyShopOwnerTier; e.legacy = true; }
        else if (played) { e.tier = c.legacyPlayerTier; e.legacy = true; }
        st.markDirty();
        if (e.legacy) TcgShop.LOGGER.info("Teléfono 0.9: {} migrado al nivel {} (conserva sus apps)", sp.getName().getString(), e.tier);
    }

    // ------------------------------------------------------------------ sincronización

    /** Manda el estado al cliente. source/locks: candados del catálogo que se va a abrir a continuación (o null). */
    public static void sync(ServerPlayerEntity p, @Nullable Integer source, int[] locks) {
        sync(p, source, locks, false);
    }

    /**
     * Igual, y refresh = el catálogo que sigue sólo debe refrescar un teléfono que ya esté abierto (tras comprar una
     * mejora): si el jugador lo cerró mientras tanto, no se le vuelve a abrir encima de otra pantalla.
     */
    public static void sync(ServerPlayerEntity p, @Nullable Integer source, int[] locks, boolean refresh) {
        if (!ServerPlayNetworking.canSend(p, PhonePayloads.Sync.ID)) return;
        PhoneConfig c = PhoneConfig.INSTANCE;
        PhoneState.Entry e = entry(p);
        NbtCompound d = new NbtCompound();
        d.putBoolean("enabled", c.enabled);
        d.putInt("tier", level(p));
        d.putBoolean("legacy", e.legacy && c.legacyKeepsApps);
        d.putLongArray("costs", c.tierCosts.clone());
        d.putBoolean("calls", c.callsEnabled);
        d.putBoolean("callUsed", callUsed(p.getServer(), e));
        if (source != null && locks != null) {
            d.putInt("src", source);
            d.putIntArray("locks", locks);
            if (refresh) d.putBoolean("refresh", true);
        }
        ServerPlayNetworking.send(p, new PhonePayloads.Sync(d));
    }

    /** Ticks entre dos llamadas (un día de Minecraft). */
    private static final long CALL_COOLDOWN = 24000L;

    /**
     * Llamada usada en los últimos 24000 ticks del mundo. Se mide con getTime(), que sólo avanza: /time set y
     * doDaylightCycle = false no la dejan bloqueada (con el número de día podía quedarse así cientos de días o siempre).
     */
    static boolean callUsed(MinecraftServer server, PhoneState.Entry e) {
        if (e.lastCallTick < 0) return false;
        long now = server.getOverworld().getTime();
        return now >= e.lastCallTick && now - e.lastCallTick < CALL_COOLDOWN;
    }

    // ------------------------------------------------------------------ catálogos (Mercado del teléfono y Mayorista)

    private static int tierForPackUsd(double usd) {
        double[] lim = PhoneConfig.INSTANCE.packUsdLimits;
        for (int i = 0; i < lim.length; i++) if (usd <= lim[i]) return i;
        return PhoneTier.MAX_LEVEL;
    }

    /** Nivel de teléfono necesario para comprar este producto (phone = Mercado del teléfono; si no, Mayorista). */
    public static int requiredTier(ItemStack st, boolean phone) {
        if (!PhoneConfig.INSTANCE.enabled) return 0;
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) {
            int t = tierForPackUsd(PriceService.sealedUsd(st.getOrDefault(TcgBlocks.BOX_SET, "mew151"), PriceService.Sealed.PACK));
            return phone ? Math.max(PhoneTier.PHONE_BOX_TIER, t) : t;
        }
        if (CardInfo.isPack(st)) return tierForPackUsd(PriceService.sealedUsd(CardInfo.setId(CardInfo.id(st)), PriceService.Sealed.PACK));
        if (st.isOf(TcgBlocks.ROULETTE_ITEM)) return PhoneTier.ROULETTE_TIER;
        if (!phone) return 0;
        if (st.isOf(TcgBlocks.MYSTERY_BOX)) return PhoneTier.PHONE_MYSTERY_TIER;
        if (ShopServer.isPokedoll(st)) return PhoneTier.PHONE_PLUSH_TIER;
        return 0;
    }

    /**
     * Lo que ve este jugador en ese catálogo: el Mayorista trae cajas de sets extra en los niveles altos y en el último
     * nivel se salta el bloqueo de sets por nivel de tienda. Es determinista: abrir y comprar dan la misma lista.
     */
    public static List<ItemStack> storeView(ServerPlayerEntity p, int source, List<ItemStack> goods) {
        return applyPackLock(p, withExtraSets(p, source, goods));
    }

    /** Primer paso de storeView: el catálogo con las cajas de sets extra del Mayorista (sin aplicar PackLock). */
    public static List<ItemStack> withExtraSets(ServerPlayerEntity p, int source, List<ItemStack> goods) {
        int lv = perkLevel(p);
        List<ItemStack> g = goods;
        int extra = PhoneTier.EXTRA_BOX_SETS[lv];
        if (source != -1 && extra > 0) {
            Set<String> have = new HashSet<>();
            for (ItemStack st : goods) if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) have.add(st.getOrDefault(TcgBlocks.BOX_SET, ""));
            List<String> all = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) {
                    String s = CardInfo.setId(id);
                    if (s != null && !have.contains(s) && !all.contains(s)) all.add(s);
                }
            Collections.sort(all);
            Collections.shuffle(all, new java.util.Random(source * 7919L + 104729L));
            g = new ArrayList<>(goods);
            for (int i = 0; i < Math.min(extra, all.size()); i++) g.add(BoosterBox.of(all.get(i)));
        }
        return g;
    }

    /** Segundo paso de storeView: quita los sets bloqueados por nivel de tienda (salvo con el último nivel). */
    public static List<ItemStack> applyPackLock(ServerPlayerEntity p, List<ItemStack> goods) {
        if (perkLevel(p) >= PhoneTier.PACKLOCK_BYPASS_TIER) return goods;
        return PackLock.visibleTo(p, goods);
    }

    /** Nivel necesario de cada producto de la lista (para los candados de la interfaz). */
    public static int[] locks(List<ItemStack> goods, int source) {
        int[] out = new int[goods.size()];
        for (int i = 0; i < out.length; i++) out[i] = requiredTier(goods.get(i), source == -1);
        return out;
    }

    /** Factor de precio con el descuento del nivel. */
    public static double priceFactor(ServerPlayerEntity p, int source, double base) {
        int lv = perkLevel(p);
        return base * (source == -1 ? PhoneTier.PHONE_DISCOUNT[lv] : PhoneTier.WHOLESALE_DISCOUNT[lv]);
    }

    public static Text tierName(int level) {
        return Text.translatable("tcgshop.phoneup.tier." + PhoneTier.of(level).key);
    }

    // ------------------------------------------------------------------ acciones

    private static void handle(ServerPlayerEntity p, NbtCompound d) {
        switch (d.getString("a")) {
            case "buy" -> buy(p, d.getInt("tier"));
            case "call" -> call(p, d.getString("kind"));
            case "sync" -> sync(p, null, null);
            default -> {}
        }
    }

    private static boolean hasPhone(ServerPlayerEntity p) {
        return p.getInventory().contains(st -> st.isOf(TcgItems.TCG_PHONE));
    }

    static void buy(ServerPlayerEntity p, int target) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!c.enabled || !hasPhone(p)) return;
        PhoneState.Entry e = entry(p);
        if (target != e.tier + 1 || target > PhoneTier.MAX_LEVEL) { sync(p, null, null); return; }
        if (!CobbleDollarsBridge.isAvailable()) {
            p.sendMessage(Text.translatable("tcgshop.sell.no_bank").formatted(Formatting.RED), true);
            return;
        }
        long cost = c.cost(target);
        if (!CobbleDollarsBridge.tryCharge(p, cost)) {
            p.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(cost)).formatted(Formatting.RED), true);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.6f, 1f);
            return;
        }
        e.tier = target;
        PhoneState.get(p.getServer()).markDirty();
        p.sendMessage(Text.translatable("tcgshop.phoneup.bought", tierName(target).copy().formatted(Formatting.BOLD),
                Text.literal(PriceService.format(cost)).formatted(Formatting.GOLD)).formatted(Formatting.GREEN), false);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.2f);
        // vuelve a mandar el Mercado (nuevos productos y precios); el teléfono abierto se actualiza sin cerrarse y,
        // si el jugador ya lo cerró, no se vuelve a abrir
        ShopServer.openStore(p, -1, ShopServer.phoneCatalog(), ShopServer.PHONE_FACTOR, true);
    }

    static void call(ServerPlayerEntity p, String kind) {
        PhoneConfig c = PhoneConfig.INSTANCE;
        if (!c.callsEnabled || !hasPhone(p)) return;
        if (level(p) < PhoneTier.CALL_TIER) return;
        PhoneState.Entry e = entry(p);
        if (callUsed(p.getServer(), e)) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.used").formatted(Formatting.YELLOW), true);
            sync(p, null, null);
            return;
        }
        boolean ok = switch (kind) {
            case "collector" -> callCollector(p);
            case "wholesaler" -> callWholesaler(p);
            default -> false;
        };
        if (!ok) return;
        e.lastCallTick = p.getServer().getOverworld().getTime();
        PhoneState.get(p.getServer()).markDirty();
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
        sync(p, null, null);
    }

    /** Si estás en tu tienda (o cerca) viene un cliente coleccionista de los que gastan mucho; si no, un Coleccionista ambulante. */
    private static boolean callCollector(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        ShopZones.Zone best = null;
        double bestD = 96 * 96;
        for (ShopZones.Zone z : ShopZones.get(w).all()) {
            if (!z.owner.equals(p.getUuid())) continue;
            double dd = p.squaredDistanceTo(z.core.toCenterPos());
            if (dd < bestD) { bestD = dd; best = z; }
        }
        // por el camino normal de los clientes: llega andando hasta una puerta abierta y respeta aforo y cola; si la
        // tienda no está cargada, está llena con la cola llena o todas sus puertas están cerradas, viene el ambulante
        if (best != null && dev.tcgshop.shop.ShopLife.forceSpawnCustomer(w, best, "WHALE")) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.collector_shop").formatted(Formatting.GOLD), false);
            return true;
        }
        if (!w.getEntitiesByClass(TraderEntity.class, p.getBoundingBox().expand(48), t -> true).isEmpty()) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.already_near").formatted(Formatting.YELLOW), true);
            return false;
        }
        if (spawnNear(w, p, TcgEntities.TRADER.create(w))) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.collector_near").formatted(Formatting.GOLD), false);
            return true;
        }
        p.sendMessage(Text.translatable("tcgshop.phoneup.call.no_room").formatted(Formatting.RED), true);
        return false;
    }

    private static boolean callWholesaler(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (!w.getEntitiesByClass(WholesalerEntity.class, p.getBoundingBox().expand(48), t -> true).isEmpty()) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.already_near").formatted(Formatting.YELLOW), true);
            return false;
        }
        if (spawnNear(w, p, TcgEntities.WHOLESALER.create(w))) {
            p.sendMessage(Text.translatable("tcgshop.phoneup.call.wholesaler").formatted(Formatting.GOLD), false);
            return true;
        }
        p.sendMessage(Text.translatable("tcgshop.phoneup.call.no_room").formatted(Formatting.RED), true);
        return false;
    }

    // ---- 0.9 eventos aleatorios: las mismas apariciones que las llamadas del teléfono, sin gastar la llamada del día

    /** Hace aparecer al Mayorista junto al jugador (si no hay ya uno cerca). */
    public static boolean spawnWholesalerNear(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (!w.getEntitiesByClass(WholesalerEntity.class, p.getBoundingBox().expand(48), t -> true).isEmpty()) return true;
        return spawnNear(w, p, TcgEntities.WHOLESALER.create(w));
    }

    /** Hace aparecer al mercader negro junto al jugador, con su oferta, sólo para él (si no hay ya uno cerca). */
    public static boolean spawnBlackMarketNear(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (!w.getEntitiesByClass(dev.tcgshop.entity.BlackMarketEntity.class, p.getBoundingBox().expand(128), t -> true).isEmpty()) return true;
        dev.tcgshop.entity.BlackMarketEntity m = TcgEntities.BLACK_MARKET.create(w);
        if (m == null) return false;
        m.setForPlayer(p.getUuid());
        m.rollOffer();
        return spawnNear(w, p, m);
    }

    /** Coloca una entidad en suelo libre a 4-9 bloques del jugador. */
    public static boolean spawnNearPlayer(ServerWorld w, ServerPlayerEntity p, @Nullable PathAwareEntity ent) {
        return spawnNear(w, p, ent);
    }

    private static boolean spawnNear(ServerWorld w, ServerPlayerEntity p, @Nullable PathAwareEntity ent) {
        if (ent == null) return false;
        for (int attempt = 0; attempt < 16; attempt++) {
            double ang = w.random.nextDouble() * Math.PI * 2;
            int dist = 4 + w.random.nextInt(6);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            BlockPos found = null;
            for (int dy = 3; dy >= -3 && found == null; dy--) { // primero cerca de la altura del jugador (sirve bajo techo)
                BlockPos c = new BlockPos(x, p.getBlockY() + dy, z);
                if (w.getBlockState(c.down()).isSolidBlock(w, c.down()) && w.isAir(c) && w.isAir(c.up())) found = c;
            }
            if (found == null) {
                int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos c = new BlockPos(x, y, z);
                if (Math.abs(y - p.getBlockY()) <= 6 && w.getBlockState(c.down()).isSolidBlock(w, c.down()) && w.isAir(c) && w.isAir(c.up())) found = c;
            }
            if (found == null) continue;
            ent.refreshPositionAndAngles(found.getX() + 0.5, found.getY(), found.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
            w.spawnEntity(ent);
            return true;
        }
        return false;
    }
}
