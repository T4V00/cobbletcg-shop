package dev.tcgshop.world;

import dev.masterball.api.card.CardCatalog;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * What a trash can holds (1.0). Values are the cards' market price as the shop shows it
 * ({@link ShopServer#priceOf}(card, 1.0)).
 *
 * <pre>
 * Street / wild can (one roll per search):
 *   packChance  (10 %)  a sealed pack of an unlocked set; cheap packs are much more likely (weight 1 / price)
 *   cardChance  (30 %)  one uniformly random card worth less than cardMaxValue (2,000)
 *   otherwise           junk (junkChance of the rest, 50 %) or nothing
 * Bankrupt Card Shop can (searched once, never refills):
 *   bankruptCardChance (50 %) one card in [bankruptMinValue, bankruptMaxValue] (300 - 20,000). The window is split
 *     into 4 geometric bands (300-857, 857-2,449, 2,449-7,000, 7,000-20,000 with the defaults) picked with weights
 *     55 / 25 / 13 / 7 %; then a uniformly random card inside the band. An empty band falls back to the cheaper ones.
 *   otherwise           the shop's "Final Notice" (a named paper)
 * </pre>
 * Cards of locked sets (PackLock) and removed sets never come out of a trash can.
 */
public final class TrashLoot {
    private TrashLoot() {}

    public enum Kind { PACK, CARD, JUNK, NOTHING }

    /** Result of one search. value = market price of the find (0 for junk / nothing). */
    public record Find(Kind kind, ItemStack stack, long value) {
        static Find nothing() { return new Find(Kind.NOTHING, ItemStack.EMPTY, 0); }
    }

    /** Bands of the bankrupt card and their weights (cheap first). */
    private static final double[] BANKRUPT_WEIGHTS = {0.55, 0.25, 0.13, 0.07};
    private static final int VERIFY_TRIES = 24;

    // ------------------------------------------------------------------ rolls

    public static Find street(RandomSource r) {
        WorldLootConfig c = WorldLootConfig.INSTANCE;
        double d = r.nextDouble();
        if (d < c.packChance) {
            ItemStack pack = randomPack(r);
            if (!pack.isEmpty()) return new Find(Kind.PACK, pack, ShopServer.priceOf(pack, 1.0));
            d = c.packChance; // no packs at all: fall through to the card roll
        }
        if (d < c.packChance + c.cardChance) {
            ItemStack card = randomCard(r, 1, c.cardMaxValue - 1);
            if (!card.isEmpty()) return new Find(Kind.CARD, card, ShopServer.priceOf(card, 1.0));
        }
        if (r.nextDouble() < c.junkChance) return new Find(Kind.JUNK, junk(r), 0);
        return Find.nothing();
    }

    public static Find bankrupt(RandomSource r) {
        WorldLootConfig c = WorldLootConfig.INSTANCE;
        if (r.nextDouble() < c.bankruptCardChance) {
            ItemStack card = bankruptCard(r, c.bankruptMinValue, c.bankruptMaxValue);
            if (!card.isEmpty()) return new Find(Kind.CARD, card, ShopServer.priceOf(card, 1.0));
        }
        return new Find(Kind.JUNK, finalNotice(), 0);
    }

    /** Card in [lo, hi] with a strong bias to the cheap end (see the class comment). */
    static ItemStack bankruptCard(RandomSource r, long lo, long hi) {
        long[] edges = new long[BANKRUPT_WEIGHTS.length + 1];
        double ratio = (double) hi / Math.max(1, lo);
        for (int i = 0; i <= BANKRUPT_WEIGHTS.length; i++)
            edges[i] = Math.round(lo * Math.pow(ratio, (double) i / BANKRUPT_WEIGHTS.length));
        edges[0] = lo;
        edges[BANKRUPT_WEIGHTS.length] = hi;
        double roll = r.nextDouble(), acc = 0;
        int band = BANKRUPT_WEIGHTS.length - 1;
        for (int i = 0; i < BANKRUPT_WEIGHTS.length; i++) {
            acc += BANKRUPT_WEIGHTS[i];
            if (roll < acc) { band = i; break; }
        }
        // the chosen band, then the cheaper ones, then the pricier ones (never outside [lo, hi])
        for (int b = band; b >= 0; b--) {
            ItemStack s = randomCard(r, edges[b], b == BANKRUPT_WEIGHTS.length - 1 ? edges[b + 1] : edges[b + 1] - 1);
            if (!s.isEmpty()) return s;
        }
        for (int b = band + 1; b < BANKRUPT_WEIGHTS.length; b++) {
            ItemStack s = randomCard(r, edges[b], b == BANKRUPT_WEIGHTS.length - 1 ? edges[b + 1] : edges[b + 1] - 1);
            if (!s.isEmpty()) return s;
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ cards

    private record Entry(ResourceLocation id, Item item, Rarity rarity, String set) {}

    private static List<Entry> index;

    /** Every card item of the MasterBall Traders API (built once: the registry is frozen by then). */
    private static synchronized List<Entry> index() {
        if (index == null) {
            List<Entry> out = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                if (!CardInfo.isCard(id) || CardCatalog.isRemoved(id)) continue;
                out.add(new Entry(id, BuiltInRegistries.ITEM.get(id), Rarity.of(id), CardInfo.setId(id)));
            }
            if (out.isEmpty()) return out; // registry not ready: do not cache
            index = out;
        }
        return index;
    }

    /** Pools per value window, valid for one market day / price data / lock set. */
    private static final Map<String, List<Entry>> POOLS = new HashMap<>();
    private static long poolDay = Long.MIN_VALUE;
    private static double poolRate = Double.NaN;
    private static Object poolData;
    private static Set<String> poolLocked;

    private static synchronized List<Entry> pool(long lo, long hi) {
        long day = PriceService.marketDay();
        double rate = PriceService.cdPerUsd();
        Object data = PriceService.cardUsdView();
        Set<String> locked = PackLock.lockedSets();
        if (day != poolDay || rate != poolRate || data != poolData || locked != poolLocked) {
            POOLS.clear();
            poolDay = day;
            poolRate = rate;
            poolData = data;
            poolLocked = locked;
        }
        return POOLS.computeIfAbsent(lo + ".." + hi, k -> {
            List<Entry> out = new ArrayList<>();
            for (Entry e : index()) {
                if (locked.contains(e.set)) continue;
                long v = Math.max(1, PriceService.priceCd(e.id, e.rarity));
                if (v >= lo && v <= hi) out.add(e);
            }
            return out;
        });
    }

    /**
     * A uniformly random card whose shop price is in [lo, hi], or EMPTY. The pools use the raw market price; the
     * shop price adds a floor (minCardPrice), so the pick is re-checked with ShopServer.priceOf.
     */
    public static ItemStack randomCard(RandomSource r, long lo, long hi) {
        if (hi < lo) return ItemStack.EMPTY;
        List<Entry> pool = pool(lo, hi);
        if (pool.isEmpty()) return ItemStack.EMPTY;
        for (int i = 0; i < VERIFY_TRIES; i++) {
            Entry e = pool.get(r.nextInt(pool.size()));
            ItemStack s = new ItemStack(e.item);
            long v = ShopServer.priceOf(s, 1.0);
            if (v >= lo && v <= hi) return s;
        }
        int start = r.nextInt(pool.size());
        for (int i = 0; i < pool.size(); i++) {
            ItemStack s = new ItemStack(pool.get((start + i) % pool.size()).item);
            long v = ShopServer.priceOf(s, 1.0);
            if (v >= lo && v <= hi) return s;
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ packs

    private static List<Item> packs;

    private static synchronized List<Item> packs() {
        if (packs == null) {
            List<Item> out = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                if (!CardInfo.NS.equals(id.getNamespace()) || !id.getPath().contains("/sealed_")) continue;
                Item it = BuiltInRegistries.ITEM.get(id);
                if (CardInfo.isPack(new ItemStack(it)) && !CardCatalog.isRemoved(id)) out.add(it);
            }
            if (out.isEmpty()) return out;
            packs = out;
        }
        return packs;
    }

    /** A sealed pack of an unlocked set, weighted 1 / price (people throw away the cheap ones). */
    public static ItemStack randomPack(RandomSource r) {
        List<ItemStack> cands = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (Item it : packs()) {
            ItemStack s = new ItemStack(it);
            if (PackLock.locked(s)) continue;
            double w = 1.0 / Math.max(1, ShopServer.priceOf(s, 1.0));
            cands.add(s);
            weights.add(w);
            total += w;
        }
        if (cands.isEmpty() || total <= 0) return ItemStack.EMPTY;
        double x = r.nextDouble() * total;
        for (int i = 0; i < cands.size(); i++) {
            x -= weights.get(i);
            if (x <= 0) return cands.get(i);
        }
        return cands.get(cands.size() - 1);
    }

    // ------------------------------------------------------------------ junk

    private static final Item[] JUNK = {Items.PAPER, Items.PAPER, Items.PAPER, Items.ROTTEN_FLESH, Items.STRING, Items.STICK,
            Items.BONE, Items.BOWL, Items.GLASS_BOTTLE, Items.WHEAT_SEEDS, Items.FEATHER, Items.LEATHER, Items.DRIED_KELP};

    static ItemStack junk(RandomSource r) {
        if (r.nextInt(6) == 0) return named(Items.PAPER, "tcgshop.trash.item.receipt", "tcgshop.trash.item.receipt.lore");
        Item it = JUNK[r.nextInt(JUNK.length)];
        return new ItemStack(it, 1 + (r.nextInt(3) == 0 ? 1 : 0));
    }

    static ItemStack finalNotice() {
        return named(Items.PAPER, "tcgshop.trash.item.notice", "tcgshop.trash.item.notice.lore");
    }

    private static ItemStack named(Item item, String nameKey, String loreKey) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.ITEM_NAME, Component.translatable(nameKey));
        s.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(loreKey).withStyle(ChatFormatting.GRAY))));
        return s;
    }
}
