package dev.tcgshop.shop;

import dev.tcgshop.TcgShop;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.events.SetHelper;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.grading.GradingService;
import dev.tcgshop.network.BoxOpenPayload;
import dev.tcgshop.pricing.PriceService;
import dev.masterball.api.card.CardCatalog;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mystery Box (1.0 rework): a product for the player. Right-click opens it and gives exactly ONE card.
 *
 * <p>The "box cost" is the box's own market price ({@link ShopServer#priceOf}, i.e. the fixed price of
 * {@code tcgshop:mystery_box}, 25,000 by default and overridable in config/tcgshop-economy.json "itemPrices").
 * Every value below is the card's current market value as the shop shows it (ShopServer.priceOf(card, 1.0)),
 * which for a graded card already includes its Grade multiplier (PriceService.priceCd -> GradeModel.mult).
 *
 * <pre>
 * Odds (exact, per box):
 *   1 / 10,000  (0.01 %)   JACKPOT: a "decent" card (Ultra Rare, Illustration Rare, Special Illustration Rare or
 *                          Hyper Rare) from the most recent sets that have packs, graded 10 (GEM MINT). Cards whose
 *                          Grade 10 value is above the box cost are preferred; if no such card exists, one of the
 *                          most valuable candidates is used.
 *   9,999 / 10,000 (99.99 %) a card worth LESS than the box:
 *     50 %  (49.995 % overall) graded: card + grade rolled with the normal CobbleGrade odds
 *                              (Grading.roll, age-aware); the pair is only accepted when the GRADED value lands
 *                              in [10 %, 95 %] of the box cost. If no pair is found after GRADED_TRIES attempts, a
 *                              card from the raw pool gets a grade below 5.0 (multiplier 0.40-0.99, so its value
 *                              can only go down and stays under the box cost).
 *     50 %  (49.995 % overall) ungraded: a uniformly random card whose value is in [10 %, 95 %] of the box cost.
 * </pre>
 * The 95 % cap is checked at the moment of opening; the daily market trend may move the value a little afterwards.
 * Cards of locked sets (PackLock) and removed sets are never drawn.
 *
 * <p>The shop owner can still sell it on a display: then {@link #ownerEarning} applies (x0.3 - x1.9).
 */
public class MysteryBoxItem extends Item {
    /** Jackpot: 1 in JACKPOT_ONE_IN boxes (0.01 %). */
    public static final int JACKPOT_ONE_IN = 10_000;
    /** Of the non-jackpot boxes, chance that the card comes graded. */
    public static final double GRADED_CHANCE = 0.5;
    /** Value window of the non-jackpot card, as a fraction of the box cost. */
    public static final double MIN_VALUE_FRACTION = 0.10, MAX_VALUE_FRACTION = 0.95;
    /** Graded draw: cards considered before rolling the grade (raw value in [min/4, max/0.4]: a Grade can multiply x0.4 .. x100). */
    private static final double GRADED_RAW_LOW = 0.25, GRADED_RAW_HIGH = 1 / 0.40;
    private static final int GRADED_TRIES = 400;
    /** Jackpot: the N newest sets (by release year; ties with the N-th included). */
    public static final int RECENT_SETS = 6;
    private static final Set<Rarity.Family> DECENT = Set.of(Rarity.Family.ULTRA_RARE, Rarity.Family.ILLUSTRATION_RARE,
            Rarity.Family.SPECIAL_ILLUSTRATION_RARE, Rarity.Family.HYPER_RARE);

    public MysteryBoxItem(Settings settings) {
        super(settings);
    }

    /** What the shop owner earns when selling a Mystery Box priced `price`. */
    public static long ownerEarning(long price, Random r) {
        return Math.round(price * (0.3 + r.nextDouble() * 1.6));
    }

    /** Market price of one Mystery Box (what the player paid for it). */
    public static long boxCost() {
        return Math.max(1, ShopServer.priceOf(new ItemStack(TcgBlocks.MYSTERY_BOX), 1.0));
    }

    // ------------------------------------------------------------------ card index and pools

    private record Entry(Identifier id, Item item, Rarity rarity, String set) {}

    private static List<Entry> index;

    /** Every card item of the MasterBall Traders API (built once: the registry is frozen by then). */
    private static synchronized List<Entry> index() {
        if (index == null) {
            List<Entry> out = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) {
                if (!CardInfo.isCard(id) || CardCatalog.isRemoved(id)) continue;
                out.add(new Entry(id, Registries.ITEM.get(id), Rarity.of(id), CardInfo.setId(id)));
            }
            index = out;
        }
        return index;
    }

    /** Cards valued for a given box cost and market day (recomputed only when something changes). */
    private record Pools(long cost, long day, double rate, Object priceData, Set<String> locked, List<Entry> raw, List<Entry> wide, List<Entry> below) {}

    private static Pools pools;

    private static synchronized Pools pools(long cost) {
        long day = PriceService.marketDay();
        double rate = PriceService.cdPerUsd();
        Object priceData = PriceService.cardUsdView();
        Set<String> locked = PackLock.lockedSets();
        Pools p = pools;
        if (p != null && p.cost == cost && p.day == day && p.rate == rate && p.priceData == priceData && p.locked == locked) return p;
        long lo = lo(cost), hi = hi(cost);
        double wideLo = lo * GRADED_RAW_LOW, wideHi = hi * GRADED_RAW_HIGH;
        List<Entry> raw = new ArrayList<>(), wide = new ArrayList<>(), below = new ArrayList<>();
        for (Entry e : index()) {
            if (locked.contains(e.set)) continue;
            long v = Math.max(1, PriceService.priceCd(e.id, e.rarity));
            if (v >= lo && v <= hi) raw.add(e);
            if (v >= wideLo && v <= wideHi) wide.add(e);
            if (v < cost) below.add(e);
        }
        return pools = new Pools(cost, day, rate, priceData, locked, raw, wide, below);
    }

    private static long lo(long cost) { return Math.max(1, Math.round(cost * MIN_VALUE_FRACTION)); }

    private static long hi(long cost) { return Math.max(1, Math.min(cost - 1, (long) Math.floor(cost * MAX_VALUE_FRACTION))); }

    /** Value of that card at Grade g (0 = ungraded), as the shop prices it. */
    private static long value(Entry e, int g) {
        ItemStack s = new ItemStack(e.item);
        if (g > 0) s.set(Grading.GRADE, g);
        return ShopServer.priceOf(s, 1.0);
    }

    // ------------------------------------------------------------------ draw

    /** Result of opening a box: the card and whether it was the jackpot. */
    public record Result(ItemStack card, boolean jackpot) {}

    /** Draws the content of one box (null if there are no cards at all). The odds are in the class comment. */
    @Nullable
    public static Result draw(Random r, long cost, @Nullable MinecraftServer server) {
        if (r.nextInt(JACKPOT_ONE_IN) == 0) {
            ItemStack j = jackpot(r, cost, server);
            if (j != null) return new Result(j, true);
        }
        Pools p = pools(cost);
        long lo = lo(cost), hi = hi(cost);
        if (r.nextDouble() < GRADED_CHANCE) {
            ItemStack g = graded(r, p, lo, hi, cost, server);
            if (g != null) return new Result(g, false);
        }
        List<Entry> pool = !p.raw.isEmpty() ? p.raw : p.below;
        if (pool.isEmpty()) return null;
        Entry e = pool.get(r.nextInt(pool.size()));
        // the pools use the raw market price; the shop price adds a minimum (minCardPrice): re-check the cap
        for (int i = 0; i < 20 && value(e, 0) >= cost; i++) e = pool.get(r.nextInt(pool.size()));
        if (value(e, 0) >= cost) { // still too expensive (e.g. a huge minCardPrice): look for any card under the cost
            e = null;
            for (Entry c : pool) if (value(c, 0) < cost) { e = c; break; }
            if (e == null) return null; // never give a card worth the box or more outside the jackpot
        }
        return new Result(new ItemStack(e.item), false);
    }

    @Nullable
    private static ItemStack graded(Random r, Pools p, long lo, long hi, long cost, @Nullable MinecraftServer server) {
        if (!p.wide.isEmpty()) {
            for (int i = 0; i < GRADED_TRIES; i++) {
                Entry e = p.wide.get(r.nextInt(p.wide.size()));
                int g = Grading.roll(r, e.id);
                long v = value(e, g);
                if (v >= lo && v <= hi) return Grading.apply(new ItemStack(e.item), g, cert(server));
            }
        }
        // Fallback: a raw-pool card with a grade below 5.0 (GradeModel: x0.40 .. x0.99) can only lose value.
        List<Entry> pool = !p.raw.isEmpty() ? p.raw : p.below;
        if (pool.isEmpty()) return null;
        Entry e = pool.get(r.nextInt(pool.size()));
        int g = 10 + r.nextInt(40); // 1.0 .. 4.9
        if (value(e, g) >= cost) return null; // cannot happen with the current GradeModel, but never break the rule
        return Grading.apply(new ItemStack(e.item), g, cert(server));
    }

    /** Jackpot card: decent rarity, newest sets with packs, Grade 10. */
    @Nullable
    private static ItemStack jackpot(Random r, long cost, @Nullable MinecraftServer server) {
        // newest sets that are sold as packs (and are not locked)
        Map<String, Integer> years = new HashMap<>();
        for (Entry e : index()) {
            if (years.containsKey(e.set) || PackLock.locked(e.set)) continue;
            Identifier sealed = CardInfo.sealedOf(e.set);
            int y = SetHelper.year(e.set);
            if (sealed == null || !Registries.ITEM.containsId(sealed) || y <= 0) continue;
            years.put(e.set, y);
        }
        if (years.isEmpty()) return null;
        List<Integer> sorted = new ArrayList<>(years.values());
        sorted.sort(Comparator.reverseOrder());
        int minYear = sorted.get(Math.min(RECENT_SETS, sorted.size()) - 1);

        List<Entry> decent = new ArrayList<>(), any = new ArrayList<>();
        for (Entry e : index()) {
            Integer y = years.get(e.set);
            if (y == null || y < minYear) continue;
            any.add(e);
            if (DECENT.contains(e.rarity.family)) decent.add(e);
        }
        List<Entry> candidates = decent.isEmpty() ? any : decent;
        if (candidates.isEmpty()) return null;
        // prefer cards that beat the box once graded 10; otherwise the 10 most valuable candidates
        List<Entry> good = new ArrayList<>();
        Map<Entry, Long> v10 = new HashMap<>();
        for (Entry e : candidates) {
            long v = value(e, 100);
            v10.put(e, v);
            if (v > cost) good.add(e);
        }
        if (good.isEmpty()) {
            List<Entry> top = new ArrayList<>(candidates);
            top.sort(Comparator.comparingLong((Entry e) -> v10.get(e)).reversed());
            good = top.subList(0, Math.min(10, top.size()));
        }
        Entry e = good.get(r.nextInt(good.size()));
        return Grading.apply(new ItemStack(e.item), 100, cert(server));
    }

    private static int cert(@Nullable MinecraftServer server) {
        return server == null ? 0 : GradingService.nextCert(server);
    }

    // ------------------------------------------------------------------ use

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            long cost = boxCost();
            Result d = draw(world.random, cost, sp.getServer());
            if (d == null) {
                sp.sendMessage(Text.translatable("tcgshop.mystery.empty").formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            ItemStack shown = d.card().copy(); // offerOrDrop may empty the stack it is given
            sp.getInventory().offerOrDrop(d.card());
            sp.sendMessage(Text.translatable("tcgshop.mystery.got", shown.toHoverableText()).formatted(Formatting.LIGHT_PURPLE), false);
            if (d.jackpot()) {
                TcgShop.LOGGER.info("{} hit the Mystery Box jackpot: {}", sp.getName().getString(), CardInfo.id(shown));
                sp.getServer().getPlayerManager().broadcast(Text.translatable("tcgshop.mystery.jackpot_broadcast",
                        sp.getDisplayName(), shown.toHoverableText()).formatted(Formatting.GOLD), false);
            }
            if (ServerPlayNetworking.canSend(sp, BoxOpenPayload.ID)) {
                ServerPlayNetworking.send(sp, BoxOpenPayload.mystery(shown, cost, d.jackpot()));
            } else {
                world.playSound(null, user.getBlockPos(), d.jackpot() ? TcgSounds.CARD_LEGENDARY : TcgSounds.CARD_RARE,
                        SoundCategory.PLAYERS, 1f, 0.8f);
            }
            stack.decrementUnlessCreative(1, user);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tcgshop.mystery_box.tip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.tcgshop.mystery_box.tip_odds").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.tcgshop.mystery_box.tip2").formatted(Formatting.DARK_GRAY));
    }
}
