package dev.tcgshop.onboarding;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.TcgShop;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.RivalTaunterEntity;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.UniqueCollectorEntity;
import dev.tcgshop.entity.WholesalerEntity;
import dev.tcgshop.network.NpcSpeechPayload;
import dev.tcgshop.network.TutorialPayload;
import dev.tcgshop.phone.PhoneUpgrades;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import dev.tcgshop.shop.PackLock;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 one-time onboarding visitors (each at most once per player, flagged with a player tag at the moment it appears):
 * <ul>
 *   <li>the unique collector ({@link UniqueCollectorEntity}): after {@link EconomyConfig#uniqueCollectorPlayTicks} of play
 *   time he walks up to you, likes one card you own and offers {@link EconomyConfig#uniqueCollectorOffer} for it;</li>
 *   <li>the taunting rival ({@link RivalTaunterEntity}): after {@link EconomyConfig#taunterNoShopPlayTicks} of play time
 *   without ever having a shop he throws you a card worth about {@link EconomyConfig#taunterCardValue}, calls you a
 *   beggar and teases you into opening a shop, then leaves.</li>
 * </ul>
 * Play time is the vanilla PLAY_TIME statistic (saved with the player, counts only while online). Also hosts the extra
 * Wholesaler visits for players without a shop ({@link WholesalerEntity#newcomerSpawner}) and the first-pack money
 * fallback for packs opened without the opening animation.
 */
public final class OnboardingVisits {
    /** Player tags: the visitor already came (never again). */
    public static final String COLLECTOR_TAG = "tcgshop_unique_collector_seen";
    public static final String TAUNTER_TAG = "tcgshop_taunter_seen";
    /** How often (server ticks) online players are checked. */
    private static final int CHECK_EVERY = 200;
    /** Minimum server ticks between two onboarding visits to the same player (in this session). */
    private static final int GAP_TICKS = 6000;

    /** Line categories (keys tcgshop.onb.<category>.<n>) and how many lines each has: must match tools/gen_lang_10b_onboarding.py. */
    public static final Map<String, Integer> LINES = Map.ofEntries(
            Map.entry("collector.arrive", 5), Map.entry("collector.like", 5), Map.entry("collector.accept", 5),
            Map.entry("collector.decline", 5), Map.entry("collector.gone", 3), Map.entry("collector.other", 3),
            Map.entry("collector.idle_leave", 3), Map.entry("collector.wait", 4),
            Map.entry("taunter.intro", 6), Map.entry("taunter.throw", 6), Map.entry("taunter.beggar", 8),
            Map.entry("taunter.push", 8), Map.entry("taunter.leave", 5), Map.entry("taunter.poke", 6),
            Map.entry("taunter.other", 3));

    private static final Map<UUID, Integer> LAST_VISIT = new HashMap<>();
    private static List<Identifier> cardPool;
    private static boolean registered;

    private OnboardingVisits() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(OnboardingVisits::tick);
        ServerTickEvents.END_WORLD_TICK.register(WholesalerEntity::newcomerSpawner);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_VISIT.remove(handler.getPlayer().getUuid()));
        // packs opened without the animation (vanilla client or packOpeningAnimation off) never reach PackOpener:
        // the starting money is paid right after that use instead. With the animation the client cancels the use
        // before it is sent, so this only fires for the fallback path.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient && player instanceof ServerPlayerEntity sp && sp.getServer() != null
                    && sp.getCommandTags().contains(Onboarding.START_MONEY_TAG) && CardInfo.isPack(stack)) {
                int before = stack.getCount();
                net.minecraft.item.Item item = stack.getItem();
                // queued with send(), not execute(): on the server thread execute() runs the task inline, i.e. BEFORE
                // the item use happens, so the count would never have changed yet
                MinecraftServer server = sp.getServer();
                server.send(new net.minecraft.server.ServerTask(server.getTicks(), () -> { // only if the pack was really used up (a blocked use pays nothing)
                    ItemStack now = sp.getStackInHand(hand);
                    if (!sp.isRemoved() && (sp.isCreative() || !now.isOf(item) || now.getCount() < before)) Onboarding.onPackOpened(sp);
                }));
            }
            return TypedActionResult.pass(stack);
        });
    }

    // ------------------------------------------------------------------ scheduler

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % CHECK_EVERY != 0) return;
        for (ServerPlayerEntity p : List.copyOf(server.getPlayerManager().getPlayerList())) {
            try {
                check(p, server.getTicks());
            } catch (Throwable t) {
                TcgShop.LOGGER.warn("Onboarding visit check failed for {}", p.getName().getString(), t);
            }
        }
    }

    private static void check(ServerPlayerEntity p, int now) {
        if (p.isSpectator() || p.isDead() || p.isSleeping() || p.isRemoved()) return;
        Integer last = LAST_VISIT.get(p.getUuid());
        if (last != null && now - last < GAP_TICKS) return;
        EconomyConfig eco = EconomyConfig.INSTANCE;
        var tags = p.getCommandTags();
        boolean wantsTaunter = eco.taunterEnabled && !tags.contains(TAUNTER_TAG);
        boolean wantsCollector = eco.uniqueCollectorEnabled && !tags.contains(COLLECTOR_TAG);
        if (!wantsTaunter && !wantsCollector) return;
        int played = playTicks(p);
        // the taunter only for players who never had a shop (the Meowth tutorial tag marks anyone who ever created one)
        if (wantsTaunter && played >= eco.taunterNoShopPlayTicks && !tags.contains(Onboarding.TUTORIAL_TAG)
                && Onboarding.ownedShops(p) == 0 && spawnTaunter(p)) {
            LAST_VISIT.put(p.getUuid(), now);
            return;
        }
        if (wantsCollector && played >= eco.uniqueCollectorPlayTicks && CobbleDollarsBridge.isAvailable()) {
            if (spawnCollector(p)) LAST_VISIT.put(p.getUuid(), now);
            // failed (no card yet, or no free spot around the player): retry in about a minute instead of every check,
            // since picking the wish reads the whole General Binder with a price lookup per card
            else LAST_VISIT.put(p.getUuid(), now - GAP_TICKS + 1200);
        }
    }

    /** Ticks this player has played in this world (vanilla statistic). */
    public static int playTicks(ServerPlayerEntity p) {
        return p.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
    }

    // ------------------------------------------------------------------ unique collector

    /**
     * Spawns the unique collector next to the player (only if they own at least one card he can like). The tag is set
     * only once he is really in the world, so a failed spawn is retried on a later check.
     */
    public static boolean spawnCollector(ServerPlayerEntity p) {
        long offer = EconomyConfig.INSTANCE.uniqueCollectorOffer;
        OwnedCards.Want wish = pickWish(p, offer);
        if (wish == null) return false; // no cards yet: try again later
        ServerWorld w = p.getServerWorld();
        UniqueCollectorEntity e = TcgEntities.UNIQUE_COLLECTOR.create(w);
        if (e == null) return false;
        e.setup(p, wish, offer);
        if (!PhoneUpgrades.spawnNearPlayer(w, p, e)) return false;
        p.addCommandTag(COLLECTOR_TAG);
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1, e.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
        p.sendMessage(Text.translatable("tcgshop.onb.collector.arrived").formatted(Formatting.GOLD), false);
        p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.8f, 1.1f);
        return true;
    }

    /**
     * The card the collector likes: one of the most valuable cards the player owns that is still worth clearly less
     * than his offer (so the one-time offer is a real gift); if every card is worth more, the cheapest one. Null if the
     * player owns no card.
     */
    @Nullable
    public static OwnedCards.Want pickWish(ServerPlayerEntity p, long offer) {
        List<OwnedCards.Owned> all = OwnedCards.list(p);
        if (all.isEmpty()) return null;
        List<OwnedCards.Owned> below = new ArrayList<>();
        for (OwnedCards.Owned o : all) if (o.value() <= offer * 0.9) below.add(o);
        if (below.isEmpty()) {
            OwnedCards.Owned min = all.get(0);
            for (OwnedCards.Owned o : all) if (o.value() < min.value()) min = o;
            return min.want();
        }
        below.sort((a, b) -> Long.compare(b.value(), a.value()));
        int top = Math.max(1, Math.min(5, below.size()));
        return below.get(p.getRandom().nextInt(top)).want();
    }

    // ------------------------------------------------------------------ taunting rival

    public static boolean spawnTaunter(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        RivalTaunterEntity e = TcgEntities.RIVAL.create(w);
        if (e == null) return false;
        e.setup(p, EconomyConfig.INSTANCE.taunterCardValue);
        if (!PhoneUpgrades.spawnNearPlayer(w, p, e)) return false;
        p.addCommandTag(TAUNTER_TAG);
        w.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        p.sendMessage(Text.translatable("tcgshop.onb.taunter.arrived").formatted(Formatting.YELLOW), false);
        return true;
    }

    private static List<Identifier> cardPool() {
        if (cardPool == null) {
            List<Identifier> l = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) if (CardInfo.isCard(id)) l.add(id);
            cardPool = l;
        }
        return cardPool;
    }

    /**
     * A random (standard print) card worth about {@code target}: within -25% / +35% of it, from unlocked sets; if none is
     * found among the sampled cards, the closest one seen. EMPTY if there are no cards at all.
     */
    public static ItemStack pickCardWorth(Random r, long target) {
        List<Identifier> pool = cardPool();
        if (pool.isEmpty()) return ItemStack.EMPTY;
        List<ItemStack> good = new ArrayList<>();
        ItemStack closest = ItemStack.EMPTY;
        long bestGap = Long.MAX_VALUE;
        int samples = Math.min(pool.size(), 6000);
        for (int k = 0; k < samples && good.size() < 30; k++) {
            Identifier id = pool.get(r.nextInt(pool.size()));
            String set = CardInfo.setId(id);
            if (set != null && PackLock.locked(set)) continue;
            ItemStack st = new ItemStack(Registries.ITEM.get(id));
            if (st.isEmpty()) continue;
            long v = CardSales.unitValue(st);
            if (v >= target * 0.75 && v <= target * 1.35) good.add(st);
            long gap = Math.abs(v - target);
            if (gap < bestGap) { bestGap = gap; closest = st; }
        }
        return good.isEmpty() ? closest : good.get(r.nextInt(good.size()));
    }

    // ------------------------------------------------------------------ speech

    /** A random line of a category (tcgshop.onb.<category>.<n>), with arguments. */
    public static MutableText line(Random r, String category, Object... args) {
        int n = Math.max(1, LINES.getOrDefault(category, 1));
        return Text.translatable("tcgshop.onb." + category + "." + r.nextInt(n), args);
    }

    /**
     * Speech bubble over {@code who}: the visited player gets a scripted bubble (visible even with NPC bubbles turned
     * off, like the Meowth tutorial), other nearby players the normal NPC bubble. Without the add-on on the client the
     * visited player reads it in chat.
     */
    public static void speak(LivingEntity who, @Nullable ServerPlayerEntity to, Text line, int ticks) {
        boolean reached = false;
        for (ServerPlayerEntity p : PlayerLookup.tracking(who)) {
            if (p.squaredDistanceTo(who) > 40 * 40) continue;
            boolean target = to != null && p.getUuid().equals(to.getUuid());
            if (target && ServerPlayNetworking.canSend(p, TutorialPayload.ID)) {
                ServerPlayNetworking.send(p, new TutorialPayload(who.getId(), line, ticks));
                reached = true;
            } else if (ServerPlayNetworking.canSend(p, NpcSpeechPayload.ID)) {
                ServerPlayNetworking.send(p, new NpcSpeechPayload(who.getId(), line));
                if (target) reached = true;
            }
        }
        if (to != null && !reached)
            to.sendMessage(Text.literal("<").append(who.getDisplayName().copy().formatted(Formatting.YELLOW)).append("> ")
                    .append(line.copy().formatted(Formatting.WHITE)), false);
    }

    /** Money text in gold, for chat messages. */
    public static Text money(long amount) {
        return Text.literal(PriceService.format(amount)).formatted(Formatting.GOLD);
    }
}
