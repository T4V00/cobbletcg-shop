package dev.tcgshop.entity;

import dev.masterball.api.platform.Net;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.MarketPayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wandering collector: peaceful NPC that shows up now and then with a few cards. It trades them for cards
 * of yours of similar value (at least 85% of its own) and always takes a fancy to one of yours: it buys it
 * above market price (115%). Meant for earning some money before owning a shop. Leaves after a day and a half.
 */
public class TraderEntity extends PathfinderMob implements TcgNpc {
    public static final double MIN_RATIO = 0.85, WISH_BONUS = 1.15;
    /**
     * TraderAction.action (besides MarketPayloads.TRADE / SELL_WISH): 1.0, sell him every Mega Stone (does not use up a deal).
     * A high value so it never collides with new MarketPayloads action codes.
     */
    public static final int SELL_MEGA_STONES = 100;
    /** TraderAction.action, 1.0 beta 3: steal one random card from his stall (CollectorTheft). Same high range as SELL_MEGA_STONES. */
    public static final int STEAL = 101;
    /** 1.0: most of your cards that can go into one trade (several of yours for one of his). 1.1: per phone tier, NpcConfig.collectorGivePerTier (this is the Basic default). */
    public static final int MAX_OFFER = 10;
    private static final int STOCK = 6, TRADES = 4;
    private static final long MAX_STOCK_VALUE = 150_000;
    private static List<ResourceLocation> cardPool;

    private final List<ItemStack> stock = new ArrayList<>();
    /** Card it wants from each player ("" = already sold to it). */
    private final Map<UUID, String> wishes = new HashMap<>();
    /** 1.1: deals each player made with him this visit (the limit depends on their phone tier) and charisma talks used. */
    private final Map<UUID, Integer> tradesUsed = new HashMap<>(), talksUsed = new HashMap<>();
    /** 1.1: deals made before the per-player counters existed (old saves: 4 minus the saved "Trades"), counted for everyone. */
    private int legacyUsed;
    private int lifeTicks = 36_000;
    // ---- 1.0 beta 3: theft (CollectorTheft holds the logic; these are only his own state, saved in his NBT)
    /** The player who robbed him (null = never robbed). While set he hunts that player. */
    @org.jetbrains.annotations.Nullable private UUID robber;
    /** Id of the card that was stolen from him (for messages; saved with him). */
    private String stolenCard = "";
    /** Ticks hunting / ticks with the thief out of reach / ticks left before leaving (-1 = not leaving). */
    int huntTicks, awayTicks, leaveTicks = -1;
    // ---- 1.1: visiting a shop (TcgEntities.shopCollectorSpawner). Null = a normal wandering visit.
    /** Core of the shop he came to see: he walks there and lingers around it (restrictTo). */
    @org.jetbrains.annotations.Nullable private net.minecraft.core.BlockPos shopHome;
    /** That shop's zone id: if it closes, goes dormant or disappears he stops lingering and wanders as usual. */
    @org.jetbrains.annotations.Nullable private UUID shopZone;
    // ---- 1.1: hunting AI (entity/CollectorHunt): blocks he changed (put back later), teleports followed, arrival pause, flight
    final List<CollectorHunt.Edit> huntEdits = new ArrayList<>();
    int followsUsed, stunTicks;
    /** Blocks broken + placed in this hunt (the collectorMaxBlockEdits budget; restores after a teleport don't reset it). */
    int editsMade;
    private boolean huntFlying;
    /** 1.1: his health bar while he hunts (shown to the players tracking him). */
    private final net.minecraft.server.level.ServerBossEvent bossBar = new net.minecraft.server.level.ServerBossEvent(Component.empty(),
            net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);

    private static final net.minecraft.network.syncher.EntityDataAccessor<String> TITLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(TraderEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SKIN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(TraderEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** 1.0 beta 3: robbed (angry emote on the client). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ROBBED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(TraderEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public TraderEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
        bossBar.setVisible(false); // 1.1: only while he hunts (updateBossBar)
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TITLE, "");
        builder.define(SKIN, 1);
        builder.define(ROBBED, false);
    }

    @Override public String roleKey() { return "tcgshop.role.trader"; }
    @Override public String npcTitle() { return entityData.get(TITLE); }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return entityData.get(ROBBED) ? EMOTE_ANGRY : EMOTE_DEAL; }
    @Override public boolean wantsAttention() { return !entityData.get(ROBBED); }

    /** 1.0 beta 3: has he been robbed (he is hunting or leaving)? Works on both sides. */
    public boolean isRobbed() { return robber != null || entityData.get(ROBBED); }
    @org.jetbrains.annotations.Nullable public UUID robber() { return robber; }
    public String stolenCard() { return stolenCard; }

    /** Marks him as robbed by that player (CollectorTheft sets up the mini-boss stats). */
    void setRobbed(UUID thief, String cardId) {
        robber = thief;
        stolenCard = cardId == null ? "" : cardId;
        endShopVisit(); // 1.1: a restriction would stop his chase (MeleeAttackGoal stays inside it)
        huntTicks = 0;
        awayTicks = 0;
        leaveTicks = -1;
        xpReward = CollectorTheft.ROBBED_XP;
        entityData.set(ROBBED, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // attack damage: only used once he is robbed (CollectorTheft raises it); the attribute must exist for melee attacks
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // 1.1: opens wooden doors only while visiting a shop (doors(true) in visitShop); otherwise his paths never use doors
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, true));
        // 1.0 beta 3: once robbed he chases the thief (CollectorTheft sets the target) and hits hard.
        // 1.1: his own hunting AI (CollectorHunt): digs, builds, flies with an elytra
        goalSelector.addGoal(2, new CollectorHunt.HuntGoal(this));
        // 1.1: visiting a shop he first walks to it, then strolls inside his restriction around the core
        goalSelector.addGoal(4, new net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal(this, 0.6) {
            @Override
            public boolean canUse() { return robber == null && shopHome != null && super.canUse(); }
        });
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5) {
            @Override
            public boolean canUse() { return robber == null && super.canUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    private static List<ResourceLocation> pool() {
        if (cardPool == null) {
            cardPool = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) if (CardInfo.isCard(id)) cardPool.add(id);
        }
        return cardPool;
    }

    // ------------------------------------------------------------------ 1.1: limits by phone tier, charisma talks

    /** Phone tier of that player. */
    private static int tier(ServerPlayer p) {
        return dev.tcgshop.phone.PhoneUpgrades.level(p);
    }

    public static int giveMax(int tier) { return dev.tcgshop.npc.NpcConfig.atTier(dev.tcgshop.npc.NpcConfig.INSTANCE.collectorGivePerTier, tier); }
    public static int takeMax(int tier) { return dev.tcgshop.npc.NpcConfig.atTier(dev.tcgshop.npc.NpcConfig.INSTANCE.collectorTakePerTier, tier); }
    public static int tradesMax(int tier) { return dev.tcgshop.npc.NpcConfig.atTier(dev.tcgshop.npc.NpcConfig.INSTANCE.collectorTradesPerTier, tier); }
    public static int talksMax(int tier) { return dev.tcgshop.npc.NpcConfig.atTier(dev.tcgshop.npc.NpcConfig.INSTANCE.collectorTalksPerTier, tier); }

    /** Deals that player still has with him this visit. */
    public int tradesLeft(ServerPlayer p) {
        return Math.max(0, tradesMax(tier(p)) - legacyUsed - tradesUsed.getOrDefault(p.getUUID(), 0));
    }

    public int talksLeft(ServerPlayer p) {
        return Math.max(0, talksMax(tier(p)) - talksUsed.getOrDefault(p.getUUID(), 0));
    }

    private void useTrade(ServerPlayer p) { tradesUsed.merge(p.getUUID(), 1, Integer::sum); }

    /** Charisma talk options when your offer is a bit short of what he wants: tell a card story, compliment his collection, promise future deals. */
    public static final int TALK_STORY = 0, TALK_COMPLIMENT = 1, TALK_PROMISE = 2, TALKS = 3;
    public static final String[] TALK_KEYS = {"story", "compliment", "promise"};
    private static final double[] TALK_BASE = {0.50, 0.62, 0.38}, TALK_REACH = {1.0, 0.7, 1.4};

    private static double lv(int charisma) {
        return (Math.max(1, Math.min(dev.tcgshop.shop.Charisma.MAX_LEVEL, charisma)) - 1) / (double) (dev.tcgshop.shop.Charisma.MAX_LEVEL - 1);
    }

    /** How far below MIN_RATIO (as a fraction of his cards' value) a talk option can still close the deal. Pure (client and server). */
    public static double talkReach(int kind, int charisma) {
        int k = Math.max(0, Math.min(TALKS - 1, kind));
        return (0.06 + 0.08 * lv(charisma)) * TALK_REACH[k];
    }

    /** Success chance of a talk option for an offer worth ratio x his cards (0 = out of reach, 1 = no talk needed). Pure (client and server). */
    public static double talkChance(int kind, int charisma, double ratio) {
        int k = Math.max(0, Math.min(TALKS - 1, kind));
        double gap = MIN_RATIO - ratio, reach = talkReach(k, charisma);
        if (gap <= 0) return 1;
        if (gap > reach) return 0;
        return Math.max(0.05, Math.min(0.9, TALK_BASE[k] + 0.30 * lv(charisma) - 0.35 * (gap / reach)));
    }

    /** 6 cards: 3 of any kind, 2 rare and 1 very rare (with a value cap). */
    private void rollStock() {
        if (!stock.isEmpty() || pool().isEmpty()) return;
        var r = net.minecraft.util.RandomSource.create(getUUID().getLeastSignificantBits());
        int[] minTier = {0, 0, 0, 1, 1, 2};
        for (int k = 0; k < STOCK; k++) {
            ItemStack best = ItemStack.EMPTY;
            for (int attempt = 0; attempt < 60; attempt++) {
                ItemStack st = new ItemStack(BuiltInRegistries.ITEM.get(pool().get(r.nextInt(pool().size()))));
                if (Rarity.of(st).tier < minTier[k] || CardSales.unitValue(st) > MAX_STOCK_VALUE) continue;
                best = st;
                break;
            }
            if (best.isEmpty()) best = new ItemStack(BuiltInRegistries.ITEM.get(pool().get(r.nextInt(pool().size()))));
            stock.add(best);
        }
    }

    /**
     * 1.0 beta 3: takes one random card off his stall (the pool he came with, plus whatever he got in trades) and returns
     * it, or EMPTY if he has nothing left.
     */
    ItemStack takeRandomStock() {
        rollStock();
        List<Integer> full = new ArrayList<>();
        for (int i = 0; i < stock.size(); i++) if (!stock.get(i).isEmpty() && CardInfo.isCard(stock.get(i))) full.add(i);
        if (full.isEmpty()) return ItemStack.EMPTY;
        return stock.remove((int) full.get(random.nextInt(full.size()))).copyWithCount(1);
    }

    /** The card in the player's inventory that it wants (slot), or -1. */
    private int wishSlot(ServerPlayer player) {
        String want = wishes.get(player.getUUID());
        if (want != null && want.isEmpty()) return -1;
        var inv = player.getInventory();
        if (want != null) {
            // "id" or "id#variant" (reverse, 1st edition...): the variant can only be in the inventory
            int hash = want.indexOf('#');
            String wid = hash < 0 ? want : want.substring(0, hash), wvar = hash < 0 ? "standard" : want.substring(hash + 1);
            for (int i = 0; i < 36; i++) {
                ItemStack st = inv.getItem(i);
                if (CardInfo.isCard(st) && CardInfo.id(st).toString().equals(wid) && dev.masterball.api.card.PrintVariant.of(st).key.equals(wvar)
                        && !CollectorTheft.isHot(player.getServer(), st)) return i; // 1.0 beta 3: never the hunted copy
            }
            if (hash < 0) {
                int b = dev.tcgshop.collection.BinderSlots.findInBinder(player, dev.tcgshop.collection.BinderSlots.stackFor(wid, 1));
                if (b >= 0) return b;
            }
        }
        // picks a new one from inventory and Binder: prefers the most valuable ones, with some randomness
        List<Integer> cards = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (CardInfo.isCard(inv.getItem(i)) && !CollectorTheft.isHot(player.getServer(), inv.getItem(i))) cards.add(i);
        var bd = dev.tcgshop.collection.CollectionService.ownedBinder(player);
        if (bd != null) {
            int n = dev.tcgshop.collection.BinderSlots.ids(bd.counts).size();
            for (int i = 0; i < n; i++) cards.add(dev.tcgshop.collection.BinderSlots.BASE + i);
        }
        if (cards.isEmpty()) return -1;
        cards.sort((a, b) -> Long.compare(CardSales.unitValue(dev.tcgshop.collection.BinderSlots.peek(player, b)),
                CardSales.unitValue(dev.tcgshop.collection.BinderSlots.peek(player, a))));
        int top = Math.max(1, Math.min(12, (cards.size() + 1) / 2));
        int slot = cards.get(random.nextInt(top));
        ItemStack picked = dev.tcgshop.collection.BinderSlots.peek(player, slot);
        dev.masterball.api.card.PrintVariant pv = dev.masterball.api.card.PrintVariant.of(picked);
        wishes.put(player.getUUID(), CardInfo.id(picked).toString() + (pv == dev.masterball.api.card.PrintVariant.STANDARD ? "" : "#" + pv.key));
        return slot;
    }

    public void open(ServerPlayer player) {
        rollStock();
        List<Long> prices = new ArrayList<>();
        for (ItemStack st : stock) prices.add(CardSales.unitValue(st));
        int left = tradesLeft(player), tier = tier(player);
        int ws = left > 0 ? wishSlot(player) : -1;
        long wp = ws < 0 ? 0 : Math.round(CardSales.unitValue(dev.tcgshop.collection.BinderSlots.peek(player, ws)) * WISH_BONUS);
        var p = new MarketPayloads.TraderOpen(getId(), List.copyOf(stock), prices, ws, wp, left, giveMax(tier), takeMax(tier), talksLeft(player),
                dev.tcgshop.shop.Charisma.level(player.getServer(), player.getUUID()));
        if (Net.INSTANCE.canSend(player, p.type())) Net.INSTANCE.send(player, p);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isRobbed()) { // 1.0 beta 3: no more business once robbed
            if (player instanceof ServerPlayer sp) CollectorTheft.robbedInteract(this, sp);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player instanceof ServerPlayer sp) {
            playSound(SoundEvents.WANDERING_TRADER_YES, 1f, 1.1f);
            getNavigation().stop();
            open(sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public void action(ServerPlayer player, MarketPayloads.TraderAction a) {
        if (distanceToSqr(player) > 10 * 10) return;
        if (isRobbed()) return; // 1.0 beta 3: no deals with a robbed collector
        if (a.action() == STEAL) { // 1.0 beta 3: steal one random card (confirmed on the client)
            CollectorTheft.steal(this, player);
            return;
        }
        if (a.action() == SELL_MEGA_STONES) { // 1.0: every trader buys Mega Stones
            if (dev.tcgshop.shop.TraderGoods.sellMegaStones(player) > 0) playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 1.1f);
            open(player);
            return;
        }
        if (tradesLeft(player) <= 0) {
            player.displayClientMessage(Component.translatable("tcgshop.trader.no_more").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (a.slot() < 0 || (a.slot() >= 36 && !dev.tcgshop.collection.BinderSlots.isBinder(a.slot()))) return;
        ItemStack mine = dev.tcgshop.collection.BinderSlots.peek(player, a.slot());
        if (!CardInfo.isCard(mine)) return;
        if (CollectorTheft.refuseHot(player, mine)) return; // 1.0 beta 3: no collector buys a card another one is hunting for
        if (a.action() == MarketPayloads.SELL_WISH) {
            if (a.slot() != wishSlot(player)) return;
            if (!CobbleDollarsBridge.isAvailable()) {
                player.displayClientMessage(Component.translatable("tcgshop.sell.no_bank").withStyle(ChatFormatting.RED), true);
                return;
            }
            long price = Math.round(CardSales.unitValue(mine) * WISH_BONUS);
            Component name = mine.getHoverName();
            if (dev.tcgshop.collection.BinderSlots.take(player, a.slot(), 1).isEmpty()) return;
            dev.tcgshop.collection.BinderSlots.sync(player);
            CobbleDollarsBridge.add(player, price);
            wishes.put(player.getUUID(), "");
            useTrade(player);
            player.displayClientMessage(Component.translatable("tcgshop.trader.sold", name, Component.literal(PriceService.format(price)).withStyle(ChatFormatting.GOLD)), false);
            playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 1.1f);
        } else if (a.action() == MarketPayloads.TRADE) {
            offer(player, List.of(a.index()), List.of(a.slot()), -1); // 1.0: one card is just an offer of one (same rules)
            return;
        }
        open(player);
    }

    /**
     * 1.0: you offer one or several of your cards (inventory slots or Binder slots) for one of his cards. Accepted when the
     * sum of their values reaches MIN_RATIO of his card's value. Everything is validated before anything moves; the cards
     * are then taken one by one and, if any of them cannot be taken, the ones already taken go back to you (no loss, no
     * duplication). He keeps the most valuable of your cards on his stall in place of the one he gave you.
     * <p>
     * 1.1: by phone tier you can give up to giveMax cards and take up to takeMax of his in one deal (their values add up
     * the same way), and make tradesMax deals per visit. talk (-1 = none, else TALK_*): when the offer is a little short
     * of MIN_RATIO (within {@link #talkReach}), your charisma may talk him into it ({@link #talkChance}); every attempt
     * uses one of your talks for this visit (talksMax by tier), success gives charisma XP.
     */
    public void offer(ServerPlayer player, List<Integer> rawIndices, List<Integer> rawSlots, int talk) {
        if (distanceToSqr(player) > 10 * 10 || rawSlots == null || rawIndices == null || isRobbed()) return;
        if (tradesLeft(player) <= 0) {
            player.displayClientMessage(Component.translatable("tcgshop.trader.no_more").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int tier = tier(player);
        List<Integer> his = new ArrayList<>(new java.util.LinkedHashSet<>(rawIndices));
        if (his.isEmpty()) return;
        if (his.size() > takeMax(tier)) {
            player.displayClientMessage(Component.translatable("tcgshop.trader.too_many_his", takeMax(tier)).withStyle(ChatFormatting.RED), true);
            return;
        }
        for (int index : his) if (index < 0 || index >= stock.size() || stock.get(index).isEmpty()) return;
        List<Integer> slots = new ArrayList<>(new java.util.LinkedHashSet<>(rawSlots)); // one unit per slot, no repeats
        if (slots.isEmpty()) return;
        if (slots.size() > giveMax(tier)) {
            player.displayClientMessage(Component.translatable("tcgshop.trader.too_many", giveMax(tier)).withStyle(ChatFormatting.RED), true);
            return;
        }
        long total = 0;
        for (int s : slots) {
            if (s < 0 || (s >= 36 && !dev.tcgshop.collection.BinderSlots.isBinder(s))) return;
            ItemStack st = dev.tcgshop.collection.BinderSlots.peek(player, s);
            if (!CardInfo.isCard(st)) return;
            if (CollectorTheft.refuseHot(player, st)) return; // 1.0 beta 3: stolen card still being hunted
            total += CardSales.unitValue(st);
        }
        long hv = 0;
        for (int index : his) hv += CardSales.unitValue(stock.get(index));
        boolean talked = false;
        if (total < hv * MIN_RATIO) {
            double ratio = hv <= 0 ? 1 : total / (double) hv;
            int cha = dev.tcgshop.shop.Charisma.level(player.getServer(), player.getUUID());
            if (talk < 0 || talk >= TALKS) {
                player.displayClientMessage((slots.size() == 1 && his.size() == 1 ? Component.translatable("tcgshop.trader.too_low")
                        : Component.translatable("tcgshop.trader.too_low_multi", PriceService.format(total), PriceService.format((long) Math.ceil(hv * MIN_RATIO))))
                        .withStyle(ChatFormatting.RED), true);
                playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
                return;
            }
            if (talksLeft(player) <= 0) {
                player.displayClientMessage(Component.translatable("tcgshop.trader.talk.none_left").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            double chance = talkChance(talk, cha, ratio);
            if (chance <= 0) {
                player.displayClientMessage(Component.translatable("tcgshop.trader.talk.too_far").withStyle(ChatFormatting.RED), true);
                playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
                return;
            }
            talksUsed.merge(player.getUUID(), 1, Integer::sum);
            if (random.nextDouble() >= chance) {
                NpcLines.speak(this, Component.translatable("tcgshop.trader.talk.fail." + TALK_KEYS[talk] + "." + random.nextInt(3)));
                player.displayClientMessage(Component.translatable("tcgshop.trader.talk.failed", Math.round(chance * 100)).withStyle(ChatFormatting.RED), false);
                playSound(SoundEvents.VILLAGER_NO, 1f, 0.9f);
                open(player);
                return;
            }
            talked = true;
            NpcLines.speak(this, Component.translatable("tcgshop.trader.talk.ok." + TALK_KEYS[talk] + "." + random.nextInt(3)));
            dev.tcgshop.shop.Charisma.addXp(player.getServer(), player.getUUID(), 6);
        }
        // highest slot first: taking the last copy of a Binder card removes it from the list and shifts the Binder slots after it
        slots.sort(java.util.Comparator.reverseOrder());
        List<ItemStack> given = new ArrayList<>();
        for (int s : slots) {
            ItemStack g = dev.tcgshop.collection.BinderSlots.take(player, s, 1);
            if (g.isEmpty() || !CardInfo.isCard(g)) { // should not happen (validated this same tick): undo and stop
                if (!g.isEmpty()) player.getInventory().placeItemBackInInventory(g);
                for (ItemStack back : given) player.getInventory().placeItemBackInInventory(back);
                dev.tcgshop.collection.BinderSlots.sync(player);
                open(player);
                return;
            }
            given.add(g);
        }
        dev.tcgshop.collection.BinderSlots.sync(player);
        // his cards go to you; your most valuable cards take their places on his stall (he pockets the rest)
        his.sort(java.util.Comparator.reverseOrder());
        List<ItemStack> got = new ArrayList<>();
        for (int index : his) got.add(stock.remove(index));
        for (ItemStack g : got) player.getInventory().placeItemBackInInventory(g.copy());
        List<ItemStack> keep = new ArrayList<>(given);
        keep.sort((a, b) -> Long.compare(CardSales.unitValue(b), CardSales.unitValue(a)));
        for (int k = 0; k < got.size() && k < keep.size(); k++) stock.add(keep.get(k).copyWithCount(1));
        useTrade(player);
        Component hisName = got.size() == 1 ? got.get(0).getHoverName() : Component.translatable("tcgshop.trader.n_cards", got.size());
        if (given.size() == 1 && got.size() == 1)
            player.displayClientMessage(Component.translatable("tcgshop.trader.traded", given.get(0).getHoverName(), hisName).withStyle(ChatFormatting.GREEN), false);
        else
            player.displayClientMessage(Component.translatable("tcgshop.trader.traded_multi", given.size(), hisName,
                    Component.literal(PriceService.format(total)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN), false);
        if (talked) player.displayClientMessage(Component.translatable("tcgshop.trader.talk.worked").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        playSound(SoundEvents.VILLAGER_TRADE, 1f, 1f);
        open(player);
    }

    /** 1.1 selftest: a stall of exactly these cards (replaces his random stock). */
    void debugStock(List<ItemStack> cards) {
        stock.clear();
        for (ItemStack c : cards) stock.add(c.copyWithCount(1));
    }

    /** 1.1 selftest: his stall as it is now. */
    List<ItemStack> debugStockView() { return List.copyOf(stock); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!hasCustomName()) {
            setCustomName(Component.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            entityData.set(TITLE, NpcIdentity.randomTitle(random, 0.6f));
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        updateBossBar();
        if (robber != null) { // 1.0 beta 3: hunting the thief (his normal stay does not run out meanwhile)
            CollectorTheft.huntTick(this);
            return;
        }
        if (shopZone != null && tickCount % 100 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sw) { // 1.1
            dev.tcgshop.shop.ShopZones.Zone z = dev.tcgshop.shop.ShopZones.get(sw).byId(shopZone);
            if (z == null || z.closed) endShopVisit();
        }
        if (--lifeTicks <= 0) discard();
    }

    /** 1.1: radius he lingers in around the shop core (about the shop's half-size, 6..20 blocks). */
    public static int shopRadius(int zoneSize) {
        return Math.max(6, Math.min(20, zoneSize / 2));
    }

    /** 1.1: comes to this shop: walks to its core and lingers around it for his normal stay. */
    public void visitShop(dev.tcgshop.shop.ShopZones.Zone z) {
        shopHome = z.core.immutable();
        shopZone = z.id;
        restrictTo(shopHome, shopRadius(z.size()));
        doors(true);
        getNavigation().moveTo(shopHome.getX() + 0.5, shopHome.getY(), shopHome.getZ() + 0.5, 0.6);
    }

    /** 1.1: stops lingering at the shop (closed, gone, or he was robbed). */
    public void endShopVisit() {
        if (shopHome == null && shopZone == null) return;
        shopHome = null;
        shopZone = null;
        clearRestriction();
        doors(false);
    }

    private void doors(boolean on) {
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation nav) {
            nav.setCanOpenDoors(on);
            nav.setCanPassDoors(true);
        }
    }

    @org.jetbrains.annotations.Nullable public net.minecraft.core.BlockPos shopHome() { return shopHome; }
    @org.jetbrains.annotations.Nullable public UUID shopZone() { return shopZone; }

    // ------------------------------------------------------------------ 1.1: hunting extras (CollectorHunt)

    public boolean isHuntFlying() { return huntFlying; }

    /** Starts / ends the elytra flight (the elytra stays on while he hunts, so you see it coming). */
    void setHuntFlying(boolean on) {
        if (on == huntFlying) return;
        huntFlying = on;
        if (on) {
            CollectorHunt.equipElytra(this, true);
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().add(0, 0.6, 0));
            playSound(SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 1f, 1f);
        }
        setNoGravity(on);
        setSharedFlag(7, on); // the fall-flying pose
        fallDistance = 0;
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (huntFlying && !level().isClientSide) { // steered by CollectorHunt.HuntGoal, no vanilla elytra physics
            // vanilla clears the fall-flying flag every tick he is not airborne with a usable elytra (aiStep runs that
            // before travel): keep both, so clients draw the elytra and the gliding pose for the whole flight
            CollectorHunt.equipElytra(this, true);
            setSharedFlag(7, true);
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.96));
            fallDistance = 0;
            return;
        }
        super.travel(input);
    }

    private void updateBossBar() {
        boolean show = robber != null && leaveTicks < 0 && isAlive() && dev.tcgshop.npc.NpcConfig.INSTANCE.collectorBossBar;
        bossBar.setVisible(show);
        if (!show) return;
        bossBar.setName(Component.translatable("tcgshop.collector_hunt.bossbar", getDisplayName()));
        bossBar.setProgress(Math.max(0, Math.min(1, getHealth() / Math.max(1, getMaxHealth()))));
    }

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bossBar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bossBar.removePlayer(p);
    }

    @Override
    public void remove(RemovalReason reason) {
        // killed or gone for good: his blocks go back (an unloaded chunk keeps them for later; a teleport already put them back)
        if (!level().isClientSide && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) CollectorHunt.restore(this);
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return robber == null && super.causeFallDamage(distance, multiplier, source); // a hunter takes no fall damage (flights, pillars)
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        rollStock();
        ListTag l = new ListTag();
        for (ItemStack st : stock) if (!st.isEmpty()) l.add(st.save(registryAccess()));
        nbt.put("Stock", l);
        CompoundTag w = new CompoundTag();
        wishes.forEach((u, id) -> w.putString(u.toString(), id));
        nbt.put("Wishes", w);
        nbt.putInt("Trades", Math.max(0, TRADES - legacyUsed)); // pre-1.1 builds read this
        nbt.putInt("LegacyUsed", legacyUsed);
        CompoundTag tu = new CompoundTag(), ku = new CompoundTag();
        tradesUsed.forEach((u, n) -> tu.putInt(u.toString(), n));
        talksUsed.forEach((u, n) -> ku.putInt(u.toString(), n));
        nbt.put("TradesUsed", tu);
        nbt.put("TalksUsed", ku);
        nbt.putInt("Life", lifeTicks);
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
        if (robber != null) { // 1.0 beta 3 (new keys; absent in older saves = never robbed)
            nbt.putUUID("Robber", robber);
            nbt.putString("StolenCard", stolenCard);
            nbt.putInt("HuntTicks", huntTicks);
            nbt.putInt("AwayTicks", awayTicks);
            nbt.putInt("LeaveTicks", leaveTicks);
            nbt.putInt("Follows", followsUsed);
            nbt.putInt("Stun", stunTicks);
            nbt.putInt("HuntEditCount", editsMade);
        }
        if (huntFlying) nbt.putBoolean("HuntFlying", true);
        if (!huntEdits.isEmpty()) nbt.put("HuntEdits", CollectorHunt.saveEdits(huntEdits));
        if (shopHome != null && shopZone != null) { // 1.1 (new keys; absent = a normal wandering visit)
            nbt.putLong("ShopHome", shopHome.asLong());
            nbt.putUUID("ShopZone", shopZone);
            nbt.putInt("ShopRadius", (int) getRestrictRadius());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        stock.clear();
        ListTag l = nbt.getList("Stock", 10);
        for (int i = 0; i < l.size(); i++) ItemStack.parse(registryAccess(), l.getCompound(i)).ifPresent(stock::add);
        wishes.clear();
        CompoundTag w = nbt.getCompound("Wishes");
        for (String k : w.getAllKeys()) {
            String wid = dev.masterball.api.card.CardInfo.migrate(w.getString(k));
            if (dev.masterball.api.card.CardCatalog.isRemovedId(wid)) continue; // TCG Pocket: removed in 0.9
            try { wishes.put(UUID.fromString(k), wid); } catch (IllegalArgumentException ignored) {}
        }
        tradesUsed.clear();
        talksUsed.clear();
        if (nbt.contains("TradesUsed")) {
            legacyUsed = nbt.getInt("LegacyUsed");
            readCounts(nbt.getCompound("TradesUsed"), tradesUsed);
            readCounts(nbt.getCompound("TalksUsed"), talksUsed);
        } else legacyUsed = nbt.contains("Trades") ? Math.max(0, TRADES - nbt.getInt("Trades")) : 0; // pre-1.1: one counter for everybody
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        entityData.set(TITLE, nbt.getString("NpcTitle"));
        entityData.set(SKIN, nbt.contains("NpcSkin") ? nbt.getInt("NpcSkin") : 1);
        if (nbt.hasUUID("Robber")) {
            robber = nbt.getUUID("Robber");
            stolenCard = nbt.getString("StolenCard");
            huntTicks = nbt.getInt("HuntTicks");
            awayTicks = nbt.getInt("AwayTicks");
            leaveTicks = nbt.contains("LeaveTicks") ? nbt.getInt("LeaveTicks") : -1;
            followsUsed = nbt.getInt("Follows");
            stunTicks = nbt.getInt("Stun");
            editsMade = nbt.getInt("HuntEditCount");
            entityData.set(ROBBED, true);
            xpReward = CollectorTheft.ROBBED_XP;
        }
        // the flight state is saved with him: vanilla keeps NoGravity, so a collector saved mid-flight would otherwise
        // reload hovering with gravity off for good (older saves without the key: on the ground)
        boolean fly = nbt.getBoolean("HuntFlying");
        if (fly || robber != null) {
            huntFlying = fly;
            setNoGravity(fly);
            if (!fly) setSharedFlag(7, false);
        }
        if (nbt.contains("HuntEdits")) CollectorHunt.loadEdits(level(), nbt.getList("HuntEdits", 10), huntEdits);
        else huntEdits.clear();
        if (nbt.contains("ShopHome") && nbt.hasUUID("ShopZone") && robber == null) {
            shopHome = net.minecraft.core.BlockPos.of(nbt.getLong("ShopHome"));
            shopZone = nbt.getUUID("ShopZone");
            restrictTo(shopHome, Math.max(6, nbt.getInt("ShopRadius")));
            doors(true);
        }
    }

    private static void readCounts(CompoundTag c, Map<UUID, Integer> out) {
        for (String k : c.getAllKeys()) {
            try { out.put(UUID.fromString(k), Math.max(0, c.getInt(k))); } catch (IllegalArgumentException ignored) {}
        }
    }
}
