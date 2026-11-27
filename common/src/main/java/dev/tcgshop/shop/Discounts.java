package dev.tcgshop.shop;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.TcgShop;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
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
public final class Discounts extends SavedData {
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

    private static final Factory<Discounts> TYPE = new Factory<>(Discounts::new, Discounts::fromNbt, null);

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
        if (!Platform.INSTANCE.isClient()) return true;
        MinecraftServer s = server;
        return s != null && s.isSameThread();
    }

    // ------------------------------------------------------------------ persistence

    public static Discounts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_discounts");
    }

    private static Discounts fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        Discounts d = new Discounts();
        d.item = nbt.getString("item");
        d.pct = nbt.getInt("pct");
        d.endTick = nbt.getLong("end");
        d.lastRollDay = nbt.contains("rollDay") ? nbt.getLong("rollDay") : -1;
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
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
        ResourceLocation id = ResourceLocation.tryParse(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item it = BuiltInRegistries.ITEM.get(id);
        return it == Items.AIR ? null : it;
    }

    // ------------------------------------------------------------------ registration and loop

    public static void register() {
        Net.INSTANCE.s2c(SyncPayload.ID, SyncPayload.CODEC);
        ServerEvents.STARTED.register(s -> {
            server = s;
            refreshView(get(s));
        });
        ServerEvents.STOPPED.register(s -> {
            server = null;
            serverView = Map.of();
        });
        ServerEvents.TICK_END.register(Discounts::tick);
        PlayerEvents.JOIN.register(player -> sync(player));
        ServerEvents.COMMANDS.register((d, access, env) -> d.register(Commands.literal("tcgdiscount")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("roll").executes(ctx -> {
                    MinecraftServer s = ctx.getSource().getServer();
                    if (!start(s, s.overworld().getRandom())) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("tcgshop.discount.cmd.none").withStyle(ChatFormatting.GRAY), false);
                        return 0;
                    }
                    Discounts st = get(s);
                    Item it = st.activeItem();
                    long days = Math.max(1, (st.endTick - s.overworld().getGameTime() + DAY - 1) / DAY);
                    ctx.getSource().sendSuccess(() -> Component.translatable("tcgshop.discount.cmd.started",
                            it == null ? Component.literal(st.item) : new ItemStack(it).getHoverName(), st.pct, days).withStyle(ChatFormatting.GREEN), true);
                    return 1;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    clear(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.translatable("tcgshop.discount.cmd.cleared").withStyle(ChatFormatting.GRAY), true);
                    return 1;
                }))));
    }

    private static void tick(MinecraftServer s) {
        if (s.getTickCount() % 100 != 53) return;
        Discounts st = get(s);
        long now = s.overworld().getGameTime();
        if (st.active() && (now >= st.endTick || st.activeItem() == null)) clear(s);
        long day = now / DAY;
        if (st.lastRollDay == day) return;
        boolean first = st.lastRollDay < 0;
        st.lastRollDay = day;
        st.setDirty();
        if (first || st.active()) return; // a brand-new world does not roll at once; one sale at a time
        double chance = EconomyConfig.INSTANCE.discountDailyChance;
        if (Double.isNaN(chance) || chance <= 0) return;
        RandomSource r = s.overworld().getRandom();
        if (r.nextDouble() < Math.min(1, chance)) start(s, r);
    }

    // ------------------------------------------------------------------ starting / ending a sale

    /** Starts a random sale now (replacing any current one). false = nothing in the store can go on sale. */
    public static boolean start(MinecraftServer s, RandomSource r) {
        ItemStack pick = pickItem(r);
        if (pick == null) return false;
        EconomyConfig c = EconomyConfig.INSTANCE;
        int pct = pickPercent(r, c.discountWeights, packLike(pick) ? MAX_PACK_INDEX : PERCENTS.length - 1);
        int min = Math.max(1, Math.min(30, c.discountMinDays)), max = Math.max(min, Math.min(30, c.discountMaxDays));
        int days = min + r.nextInt(max - min + 1);
        Discounts st = get(s);
        st.item = BuiltInRegistries.ITEM.getKey(pick.getItem()).toString();
        st.pct = pct;
        st.endTick = s.overworld().getGameTime() + days * DAY;
        st.setDirty();
        refreshView(st);
        syncAll(s);
        TcgShop.LOGGER.info("Store sale: {} at {}% off for {} day(s)", st.item, pct, days);
        Component body = Component.translatable("tcgshop.discount.msg", pick.getHoverName(), pct, days);
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
        st.setDirty();
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
    private static int pickPercent(RandomSource r, @Nullable int[] weights, int maxIndex) {
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
    private static ItemStack pickItem(RandomSource r) {
        Map<Item, ItemStack> unique = new LinkedHashMap<>();
        for (ItemStack st : ShopServer.phoneCatalog()) {
            if (st.isEmpty() || st.is(TcgBlocks.BOOSTER_BOX_ITEM) || PackLock.locked(st)) continue;
            // Pokédolls: the Furniture Vendor buys them back at up to 50 % of their base price (a 50 % sale would be free
            // money), and there are hundreds of them, so they would take almost every sale
            if (ShopServer.isPokedoll(st)) continue;
            // 1.0 beta 3: scratch tickets pay multiples of their face value (59-70% return): at 30-50 % off they would pay back over 100 %
            if (st.getItem() instanceof dev.tcgshop.casino.ScratchCardItem) continue;
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
        for (ServerPlayer p : s.getPlayerList().getPlayers()) out.add(p.getUUID());
        try {
            Path dir = s.getWorldPath(LevelResource.PLAYER_DATA_DIR);
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
            st.setDirty();
        }
        serverView = it == null ? Map.of() : Map.of(it, st.pct);
    }

    private static CompoundTag syncData(Discounts st) {
        CompoundTag d = new CompoundTag();
        if (st.active()) {
            CompoundTag items = new CompoundTag();
            items.putInt(st.item, st.pct);
            d.put("items", items);
            d.putLong("end", st.endTick);
        }
        return d;
    }

    public static void sync(ServerPlayer p) {
        if (Net.INSTANCE.canSend(p, SyncPayload.ID)) Net.INSTANCE.send(p, new SyncPayload(syncData(get(p.getServer()))));
    }

    private static void syncAll(MinecraftServer s) {
        SyncPayload payload = new SyncPayload(syncData(get(s)));
        for (ServerPlayer p : s.getPlayerList().getPlayers())
            if (Net.INSTANCE.canSend(p, SyncPayload.ID)) Net.INSTANCE.send(p, payload);
    }

    /** Client: applies a sync (an empty compound = no sale; also used on disconnect). */
    public static void applyClient(CompoundTag d) {
        Map<Item, Integer> m = new LinkedHashMap<>();
        CompoundTag items = d.getCompound("items");
        for (String k : items.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(k);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            int v = items.getInt(k);
            if (v > 0 && v < 100) m.put(BuiltInRegistries.ITEM.get(id), v);
        }
        clientView = Map.copyOf(m);
    }

    /** Server -> client: items on sale (id -> percent) and when the sale ends (world time). */
    public record SyncPayload(CompoundTag data) implements CustomPacketPayload {
        public static final Type<SyncPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "discounts"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, SyncPayload::data, SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
