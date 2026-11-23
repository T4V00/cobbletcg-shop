package dev.tcgshop.shop;

import dev.tcgshop.EconomyConfig;
import dev.tcgshop.TcgShop;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 1.0 · Store sale event (ROADMAP "Very rare discount event"): once per Minecraft day there is a small chance
 * (EconomyConfig.discountDailyChance) that ONE item of the store (phone Market / Wholesaler catalog) goes 10, 30 or 50 %
 * off for 1-3 Minecraft days. Cheap items are much more likely than expensive ones (weight = (cheapest / price) ^
 * discountPriceExponent, so a 150,000 item is thousands of times less likely than a 600 one) and 50 % is much rarer than
 * 10 % (discountWeights). Packs and anything pack-like (Mystery Box) only ever get 10 or 30 % off, never 50 %
 * (see {@link #packLike}). Booster boxes never go on sale: they are a single item whose set is a component, so a
 * per-item discount would cover every set at once. Pokédolls never go on sale either (resale arbitrage, see pickItem).
 * <p>
 * Public contract: {@link #percent(Item)} (0 = no discount) works on both sides: the server reads the saved state, the
 * client the copy sent by {@link SyncPayload} (on join and on every change). ShopServer.storePrice applies it to store
 * prices. When a sale starts every known player (online or not) gets a Messages app notice from the shop.
 * <p>
 * Saved in the Overworld (data/tcgshop_discounts.dat). Days are counted with the total world time (not /time set).
 */
public final class Discounts extends PersistentState {
    public static final int[] PERCENTS = {10, 30, 50};
    private static final long DAY = 24_000L;

    // ---- saved state
    /** Item on sale (registry id) or "" when there is none. */
    private String item = "";
    private int pct;
    /** World time (getTime) when the sale ends. */
    private long endTick;
    /** Last day (getTime / 24000) the daily roll was made; -1 = never. */
    private long lastRollDay = -1;

    private static final Type<Discounts> TYPE = new Type<>(Discounts::new, Discounts::fromNbt, null);

    // ---- live views
    private static volatile Map<Item, Integer> serverView = Map.of(), clientView = Map.of();
    @Nullable
    private static volatile MinecraftServer server;

    public Discounts() {}

    // ------------------------------------------------------------------ public contract

    /** Discount in percent for that item in the store (0 if none). Server: saved state; client: last sync. */
    public static int percent(Item item) {
        if (item == null) return 0;
        Map<Item, Integer> view = onServerSide() ? serverView : clientView;
        Integer v = view.get(item);
        return v == null ? 0 : v;
    }

    /** Price with the discount of that item applied (unchanged when there is none). */
    public static long apply(Item item, long price) {
        int p = percent(item);
        return p <= 0 || price <= 0 ? price : Math.max(1, Math.round(price * (100 - p) / 100.0));
    }

    private static boolean onServerSide() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) return true;
        MinecraftServer s = server;
        return s != null && s.isOnThread();
    }

    // ------------------------------------------------------------------ persistence

    public static Discounts get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_discounts");
    }

    private static Discounts fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        Discounts d = new Discounts();
        d.item = nbt.getString("item");
        d.pct = nbt.getInt("pct");
        d.endTick = nbt.getLong("end");
        d.lastRollDay = nbt.contains("rollDay") ? nbt.getLong("rollDay") : -1;
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putString("item", item);
        nbt.putInt("pct", pct);
        nbt.putLong("end", endTick);
        nbt.putLong("rollDay", lastRollDay);
        return nbt;
    }

    private boolean active() {
        return !item.isEmpty() && pct > 0;
    }

    @Nullable
    private Item activeItem() {
        Identifier id = Identifier.tryParse(item);
        if (id == null || !Registries.ITEM.containsId(id)) return null;
        Item it = Registries.ITEM.get(id);
        return it == Items.AIR ? null : it;
    }

    // ------------------------------------------------------------------ registration and loop

    public static void register() {
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            refreshView(get(s));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            serverView = Map.of();
        });
        ServerTickEvents.END_SERVER_TICK.register(Discounts::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> sync(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((d, access, env) -> d.register(CommandManager.literal("tcgdiscount")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("roll").executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    if (!start(s, s.getOverworld().getRandom())) {
                        ctx.getSource().sendFeedback(() -> Text.translatable("tcgshop.discount.cmd.none").formatted(Formatting.GRAY), false);
                        return 0;
                    }
                    Discounts st = get(s);
                    Item it = st.activeItem();
                    long days = Math.max(1, (st.endTick - s.getOverworld().getTime() + DAY - 1) / DAY);
                    ctx.getSource().sendFeedback(() -> Text.translatable("tcgshop.discount.cmd.started",
                            it == null ? Text.literal(st.item) : new ItemStack(it).getName(), st.pct, days).formatted(Formatting.GREEN), true);
                    return 1;
                }))
                .then(CommandManager.literal("clear").executes(ctx -> {
                    clear(ctx.getSource().getServer());
                    ctx.getSource().sendFeedback(() -> Text.translatable("tcgshop.discount.cmd.cleared").formatted(Formatting.GRAY), true);
                    return 1;
                }))));
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 100 != 53) return;
        Discounts st = get(s);
        long now = s.getOverworld().getTime();
        if (st.active() && (now >= st.endTick || st.activeItem() == null)) clear(s);
        long day = now / DAY;
        if (st.lastRollDay == day) return;
        boolean first = st.lastRollDay < 0;
        st.lastRollDay = day;
        st.markDirty();
        if (first || st.active()) return; // a brand-new world does not roll at once; one sale at a time
        double chance = EconomyConfig.INSTANCE.discountDailyChance;
        if (Double.isNaN(chance) || chance <= 0) return;
        Random r = s.getOverworld().getRandom();
        if (r.nextDouble() < Math.min(1, chance)) start(s, r);
    }

    // ------------------------------------------------------------------ starting / ending a sale

    /** Starts a random sale now (replacing any current one). false = nothing in the store can go on sale. */
    public static boolean start(MinecraftServer s, Random r) {
        ItemStack pick = pickItem(r);
        if (pick == null) return false;
        EconomyConfig c = EconomyConfig.INSTANCE;
        int pct = pickPercent(r, c.discountWeights, packLike(pick) ? MAX_PACK_INDEX : PERCENTS.length - 1);
        int min = Math.max(1, Math.min(30, c.discountMinDays)), max = Math.max(min, Math.min(30, c.discountMaxDays));
        int days = min + r.nextInt(max - min + 1);
        Discounts st = get(s);
        st.item = Registries.ITEM.getId(pick.getItem()).toString();
        st.pct = pct;
        st.endTick = s.getOverworld().getTime() + days * DAY;
        st.markDirty();
        refreshView(st);
        syncAll(s);
        TcgShop.LOGGER.info("Store sale: {} at {}% off for {} day(s)", st.item, pct, days);
        Text body = Text.translatable("tcgshop.discount.msg", pick.getName(), pct, days);
        for (UUID u : knownPlayers(s)) dev.tcgshop.phone.PhoneMessages.send(s, u, "tcgshop.msg.sender.shop", body, false);
        return true;
    }

    /** Ends the current sale (if any). */
    public static void clear(MinecraftServer s) {
        Discounts st = get(s);
        if (!st.active() && st.item.isEmpty()) return;
        st.item = "";
        st.pct = 0;
        st.endTick = 0;
        st.markDirty();
        refreshView(st);
        syncAll(s);
    }

    /** Packs, boxes and the Mystery Box (ShopZones.isSealed): only 10 or 30 % off, never 50 % (resale / opening arbitrage). */
    public static boolean packLike(ItemStack st) {
        return st != null && !st.isEmpty() && ShopZones.isSealed(st);
    }

    /** Highest PERCENTS index a pack-like item may roll (30 %). */
    private static final int MAX_PACK_INDEX = 1;
    /** Highest discount a pack-like item may have (30 %). */
    public static final int MAX_PACK_PERCENT = PERCENTS[MAX_PACK_INDEX];

    /** Weighted percent among PERCENTS[0..maxIndex] (discountWeights; the 50 % weight is ignored for pack-like items). */
    private static int pickPercent(Random r, @Nullable int[] weights, int maxIndex) {
        int[] w = weights == null || weights.length < PERCENTS.length ? new int[]{80, 17, 3} : weights;
        int n = Math.max(1, Math.min(PERCENTS.length, maxIndex + 1));
        long total = 0;
        for (int i = 0; i < n; i++) total += Math.max(0, w[i]);
        if (total <= 0) return PERCENTS[0];
        long roll = (long) (r.nextDouble() * total);
        for (int i = 0; i < n; i++) {
            roll -= Math.max(0, w[i]);
            if (roll < 0) return PERCENTS[i];
        }
        return PERCENTS[0];
    }

    /** Weighted pick from the store: cheap items much more likely, expensive ones almost never. */
    @Nullable
    private static ItemStack pickItem(Random r) {
        Map<Item, ItemStack> unique = new LinkedHashMap<>();
        for (ItemStack st : ShopServer.phoneCatalog()) {
            if (st.isEmpty() || st.isOf(TcgBlocks.BOOSTER_BOX_ITEM) || PackLock.locked(st)) continue;
            // Pokédolls: the Furniture Vendor buys them back at up to 50 % of their base price (a 50 % sale would be free
            // money), and there are hundreds of them, so they would take almost every sale
            if (ShopServer.isPokedoll(st)) continue;
            unique.putIfAbsent(st.getItem(), st.copyWithCount(1));
        }
        List<ItemStack> items = new ArrayList<>();
        List<Long> prices = new ArrayList<>();
        long cheapest = Long.MAX_VALUE;
        for (ItemStack st : unique.values()) {
            long p = ShopServer.priceOf(st, 1.0);
            if (p <= 0) continue;
            items.add(st);
            prices.add(p);
            cheapest = Math.min(cheapest, p);
        }
        if (items.isEmpty()) return null;
        double exp = EconomyConfig.INSTANCE.discountPriceExponent;
        exp = Double.isNaN(exp) ? 1.5 : Math.max(0, Math.min(6, exp));
        double[] weight = new double[items.size()];
        double total = 0;
        for (int i = 0; i < items.size(); i++) {
            weight[i] = Math.pow(cheapest / (double) prices.get(i), exp);
            total += weight[i];
        }
        double roll = r.nextDouble() * total;
        for (int i = 0; i < items.size(); i++) {
            roll -= weight[i];
            if (roll < 0) return items.get(i);
        }
        return items.get(items.size() - 1);
    }

    /** Every player who ever joined this world (player data files) plus whoever is online. */
    private static Set<UUID> knownPlayers(MinecraftServer s) {
        Set<UUID> out = new LinkedHashSet<>();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) out.add(p.getUuid());
        try {
            Path dir = s.getSavePath(WorldSavePath.PLAYERDATA);
            if (Files.isDirectory(dir)) try (Stream<Path> files = Files.list(dir)) {
                files.forEach(f -> {
                    String n = f.getFileName().toString();
                    if (!n.endsWith(".dat") || out.size() >= 2000) return;
                    try {
                        out.add(UUID.fromString(n.substring(0, n.length() - 4)));
                    } catch (IllegalArgumentException ignored) {
                        // not a player file (e.g. a backup with another name)
                    }
                });
            }
        } catch (Exception e) {
            TcgShop.LOGGER.warn("Could not list player data for the sale notice: {}", e.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------ sync

    private static void refreshView(Discounts st) {
        Item it = st.active() ? st.activeItem() : null;
        if (it != null && st.pct > MAX_PACK_PERCENT && packLike(new ItemStack(it))) { // a 50 % pack sale saved before 1.0 beta 2
            st.pct = MAX_PACK_PERCENT;
            st.markDirty();
        }
        serverView = it == null ? Map.of() : Map.of(it, st.pct);
    }

    private static NbtCompound syncData(Discounts st) {
        NbtCompound d = new NbtCompound();
        if (st.active()) {
            NbtCompound items = new NbtCompound();
            items.putInt(st.item, st.pct);
            d.put("items", items);
            d.putLong("end", st.endTick);
        }
        return d;
    }

    public static void sync(ServerPlayerEntity p) {
        if (ServerPlayNetworking.canSend(p, SyncPayload.ID)) ServerPlayNetworking.send(p, new SyncPayload(syncData(get(p.getServer()))));
    }

    private static void syncAll(MinecraftServer s) {
        SyncPayload payload = new SyncPayload(syncData(get(s)));
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList())
            if (ServerPlayNetworking.canSend(p, SyncPayload.ID)) ServerPlayNetworking.send(p, payload);
    }

    /** Client: applies a sync (an empty compound = no sale; also used on disconnect). */
    public static void applyClient(NbtCompound d) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        NbtCompound items = d.getCompound("items");
        for (String k : items.getKeys()) {
            Identifier id = Identifier.tryParse(k);
            if (id == null || !Registries.ITEM.containsId(id)) continue;
            int v = items.getInt(k);
            if (v > 0 && v < 100) m.put(Registries.ITEM.get(id), v);
        }
        clientView = Map.copyOf(m);
    }

    /** Server -> client: items on sale (id -> percent) and when the sale ends (world time). */
    public record SyncPayload(NbtCompound data) implements CustomPayload {
        public static final Id<SyncPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "discounts"));
        public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, SyncPayload::data, SyncPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}
