package dev.tcgshop.entity;

import dev.masterball.api.platform.event.ServerEvents;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.collection.BinderSlots;
import dev.tcgshop.phone.PhoneMessages;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 beta 3: stealing from the Wandering Collector ({@link TraderEntity}).
 *
 * The "Steal" button of his stall (confirmed with a second click) pockets one random card from his stall. The card is
 * marked in CUSTOM_DATA ({@link #MARK} = the collector's UUID) and the collector turns into a near mini-boss: lots of
 * health, armor, an iron sword, Speed I, and he hunts the thief. Outcomes (ROADMAP "1.0 — Release", NPCs):
 * <ul>
 *   <li>The thief escapes (out of reach for {@link #GIVE_UP_AWAY} ticks, the hunt lasts {@link #HUNT_MAX} ticks, or the
 *       collector stays unloaded for {@link #UNSEEN_LIMIT} ticks): HUGE reputation loss in all the thief's shops, and
 *       the collector leaves.</li>
 *   <li>The collector dies: {@link EconomyConfig#collectorKillPenaltyChance} (50%) of the same huge loss. The usual
 *       "killed a shop NPC" infamy (ShopLife) does not apply to a robbed collector; this replaces it.</li>
 *   <li>He kills the thief: smaller reputation loss, and the stolen card vanishes. It is removed from the inventory,
 *       ender chest, cursor and the items dropped on death; if it is nowhere to be found, he charges its market value
 *       (CobbleDollars first, then the thief's shop registers). Marked copies that show up later (graves,
 *       keepInventory, chests) are removed on respawn, on join and every few seconds while the record lasts.</li>
 * </ul>
 * While the hunt is open the card is "hot" ({@link #isHot}): binders, displays, quick-sell, collector trades and P2P
 * trades refuse it, so it cannot be laundered out of reach.
 * "Reputation" is applied like a sudden drop of every shop the thief owns (reputation points now, which then recover
 * slowly) plus infamy (lowers the Safety score and fades a few points per day), the same infamy that killing NPCs uses.
 *
 * Pending hunts and voided thefts are kept in the overworld PersistentState "tcgshop_collector_thefts", so the outcome is
 * applied even if the collector's chunk unloads or the server restarts.
 */
public final class CollectorTheft extends SavedData {
    private static final Factory<CollectorTheft> TYPE = new Factory<>(CollectorTheft::new, CollectorTheft::fromNbt, null);

    /** CUSTOM_DATA key on the stolen card: the UUID of the collector it was stolen from. */
    public static final String MARK = "tcgshop_stolen_from";
    /** Speed multiplier of his melee goal (on top of Speed I): a bit slower than a sprinting player. */
    public static final double CHASE_SPEED = 1.05;
    /** XP he drops once robbed (only when a player kills him). */
    public static final int ROBBED_XP = 30;

    /** 1.1: REACH 40 -> 64 (he flies and follows teleports now, entity/CollectorHunt). */
    static final double STEAL_RANGE = 4.5, REACH = 64;
    /** Ticks with the thief out of reach before he gives up (20 s); longest hunt (5 min); ticks unloaded that count as an escape (60 s). */
    static final int GIVE_UP_AWAY = 400, HUNT_MAX = 6_000, UNSEEN_LIMIT = 1_200, LEAVE_TICKS = 60;
    /** How long a voided theft is remembered to remove marked copies that show up later (7 Minecraft days). */
    static final long VOID_KEEP = 7 * 24_000L;
    private static final double BASE_SPEED = 0.27, ARMOR = 10, TOUGHNESS = 2, KNOCKBACK_RES = 0.5, FOLLOW = 64;
    /** What the iron sword in his hand adds to his attack (vanilla modifier); the configured damage includes it. */
    private static final double SWORD_BONUS = 5;

    /** NPC speech lines: category -> variants (tcgshop.collector_theft.line.<cat>.<n>, tools/gen_lang_10c_collectortheft.py). */
    static final Map<String, Integer> LINES = Map.of("robbed", 6, "chase", 8, "low_health", 4, "give_up", 5, "got_back", 6,
            "thief_click", 4, "victim", 4);
    /** Phone message variants (tcgshop.collector_theft.msg.<cat>.<n>). */
    static final Map<String, Integer> MSGS = Map.of("escape", 3, "got_back", 3);

    /** A hunt in progress. */
    static final class Hunt {
        UUID thief;
        String card = "";
        long start, lastSeen;
    }

    /** The collector killed the thief: the stolen card must vanish wherever it turns up. */
    static final class Voided {
        UUID thief;
        String card = "";
        long time;
        /** A copy was already taken back (inventory, ender chest, cursor, drops) or its value paid in full: later marked copies only lose the mark. */
        boolean settled;
    }

    private final Map<UUID, Hunt> hunts = new HashMap<>();
    private final Map<UUID, Voided> voided = new HashMap<>();

    public static CollectorTheft get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_collector_thefts");
    }

    /** 1.1 (CollectorHunt): collectors with an open hunt. */
    java.util.Set<UUID> huntIds() { return hunts.keySet(); }

    /** 1.1: the thief that collector is hunting, or null. */
    @Nullable
    UUID thiefOf(UUID collector) {
        Hunt h = hunts.get(collector);
        return h == null ? null : h.thief;
    }

    /** 1.1: the collector was just seen (after following a teleport) so the unloaded-escape timer starts over. */
    void seen(UUID collector, long now) {
        Hunt h = hunts.get(collector);
        if (h != null) h.lastSeen = now;
    }

    public static void register() {
        ServerEvents.LIVING_DEATH.register(CollectorTheft::afterDeath);
        PlayerEvents.RESPAWN.register((oldPlayer, newPlayer, alive) -> scanMarks(newPlayer));
        PlayerEvents.JOIN.register(player -> scanMarks(player));
        ServerEvents.TICK_END.register(CollectorTheft::serverTick);
    }

    // ------------------------------------------------------------------ mark

    /** UUID (as text) of the collector this card was stolen from, or null. Common code (also used by the client tooltip). */
    @Nullable
    public static String stolenFrom(ItemStack st) {
        if (st == null || st.isEmpty()) return null;
        CustomData c = st.get(DataComponents.CUSTOM_DATA);
        if (c == null || !c.contains(MARK)) return null;
        String s = c.copyTag().getString(MARK);
        return s.isEmpty() ? null : s;
    }

    public static boolean isStolen(ItemStack st) {
        return stolenFrom(st) != null;
    }

    /**
     * Server: is this card "hot", i.e. stolen from a collector who is still hunting for it (open hunt, or a voided theft
     * whose card has not been taken back yet)? Our storage and sale paths refuse it: General / Personal Binder filing,
     * shop and home displays, quick-sell, collector trades and P2P trades. A stale mark (theft settled) is not hot.
     */
    public static boolean isHot(@Nullable MinecraftServer server, ItemStack st) {
        String from = stolenFrom(st);
        if (from == null) return false;
        if (server == null) return true; // no state to look at: assume the worst
        UUID cid;
        try { cid = UUID.fromString(from); } catch (IllegalArgumentException ex) { return false; }
        CollectorTheft s = get(server);
        if (s.hunts.containsKey(cid)) return true;
        Voided v = s.voided.get(cid);
        return v != null && !v.settled;
    }

    /** Refuses a hot card: says why and returns true. Normal cards (and stale marks) return false with no message. */
    public static boolean refuseHot(ServerPlayer p, ItemStack st) {
        if (!isHot(p.getServer(), st)) return false;
        sayHot(p);
        return true;
    }

    /** "This card is hot: the collector is still hunting you." (chat, so bulk actions don't hide it behind their own action-bar line) */
    public static void sayHot(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("tcgshop.collector_theft.hot").withStyle(ChatFormatting.RED), false);
    }

    private static void strip(ItemStack st) {
        CustomData.update(DataComponents.CUSTOM_DATA, st, nbt -> nbt.remove(MARK));
    }

    // ------------------------------------------------------------------ steal

    /** TraderAction STEAL: the player pockets a random card and the collector turns on them. */
    static void steal(TraderEntity t, ServerPlayer p) {
        if (!(t.level() instanceof ServerLevel w) || t.isRobbed() || !t.isAlive() || p.isSpectator()) return;
        if (t.distanceToSqr(p) > STEAL_RANGE * STEAL_RANGE) {
            p.displayClientMessage(Component.translatable("tcgshop.collector_theft.too_far").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack card = t.takeRandomStock();
        if (card.isEmpty()) {
            p.displayClientMessage(Component.translatable("tcgshop.collector_theft.nothing").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        String cardId = CardInfo.id(card).toString();
        String cid = t.getStringUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, card, nbt -> nbt.putString(MARK, cid));
        Component name = card.getHoverName();
        p.getInventory().placeItemBackInInventory(card);

        t.setRobbed(p.getUUID(), cardId);
        enrage(t, p);
        CollectorTheft st = get(w.getServer());
        Hunt h = new Hunt();
        h.thief = p.getUUID();
        h.card = cardId;
        h.start = h.lastSeen = w.getServer().overworld().getGameTime();
        st.hunts.put(t.getUUID(), h);
        st.setDirty();

        p.displayClientMessage(Component.translatable("tcgshop.collector_theft.stolen", name, t.getDisplayName()).withStyle(ChatFormatting.RED), false);
        p.displayClientMessage(Component.translatable("tcgshop.collector_theft.stolen_hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        speak(t, "robbed");
        t.playSound(SoundEvents.RAVAGER_ROAR, 1f, 1.3f);
        CardAdvancements.grant(p, "collector_steal");
    }

    /** Mini-boss stats (saved with his attributes and effects, so they survive reloads). */
    private static void enrage(TraderEntity t, ServerPlayer p) {
        EconomyConfig cfg = EconomyConfig.INSTANCE;
        double hp = clamp(cfg.collectorRobbedHealth, 20, 1024), dmg = clamp(cfg.collectorRobbedDamage, 1, 200);
        base(t, Attributes.MAX_HEALTH, hp);
        base(t, Attributes.ARMOR, ARMOR);
        base(t, Attributes.ARMOR_TOUGHNESS, TOUGHNESS);
        base(t, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RES);
        base(t, Attributes.MOVEMENT_SPEED, BASE_SPEED);
        base(t, Attributes.FOLLOW_RANGE, FOLLOW);
        base(t, Attributes.ATTACK_DAMAGE, Math.max(1, dmg - SWORD_BONUS));
        t.setHealth(t.getMaxHealth());
        t.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        t.setDropChance(EquipmentSlot.MAINHAND, 0f);
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, true));
        t.setPersistenceRequired();
        t.getNavigation().stop();
        if (!p.isCreative()) t.setTarget(p);
    }

    private static void base(LivingEntity e, Holder<Attribute> attr, double v) {
        AttributeInstance i = e.getAttribute(attr);
        if (i != null) i.setBaseValue(v);
    }

    /** Right-click on a robbed collector: no stall, just an angry line. */
    static void robbedInteract(TraderEntity t, ServerPlayer p) {
        if (t.leaveTicks >= 0) return;
        if (t.tickCount % 20 != 0 && p.getRandom().nextInt(3) != 0) return; // no spam when clicking repeatedly
        speak(t, p.getUUID().equals(t.robber()) ? "thief_click" : "victim");
    }

    // ------------------------------------------------------------------ hunt

    /** Every server tick of a robbed collector (TraderEntity.tick). */
    static void huntTick(TraderEntity t) {
        if (!(t.level() instanceof ServerLevel w) || !t.isAlive()) return; // dying: the normal death animation runs
        if (t.leaveTicks >= 0) { // settled: he stops and vanishes
            t.getNavigation().stop();
            if (t.getTarget() != null) t.setTarget(null);
            if (--t.leaveTicks <= 0) poof(w, t);
            return;
        }
        MinecraftServer server = w.getServer();
        CollectorTheft st = get(server);
        Hunt h = st.hunts.get(t.getUUID());
        if (h == null) { // settled while he was unloaded (escape by timeout) or the record is gone: he has left
            poof(w, t);
            return;
        }
        long now = server.overworld().getGameTime();
        h.lastSeen = now;
        if (now % 200 == 0) st.setDirty();
        t.huntTicks++;
        ServerPlayer thief = server.getPlayerList().getPlayer(h.thief);
        boolean reach = thief != null && thief.level() == w && thief.isAlive() && !thief.isSpectator()
                && t.distanceToSqr(thief) <= REACH * REACH;
        if (reach) {
            t.awayTicks = 0;
            if (!thief.isCreative() && t.getTarget() != thief) t.setTarget(thief);
            if (t.tickCount % 20 == 0) {
                float r = t.getRandom().nextFloat();
                if (t.getHealth() < t.getMaxHealth() * 0.25f && r < 0.2f) speak(t, "low_health");
                else if (r < 0.07f) speak(t, "chase");
            }
        } else {
            t.awayTicks++;
            if (t.getTarget() != null) t.setTarget(null);
        }
        if (t.awayTicks >= GIVE_UP_AWAY || t.huntTicks >= HUNT_MAX) st.settleEscape(server, t.getUUID(), t);
    }

    private static void serverTick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 37) return;
        CollectorTheft st = get(server);
        if (st.hunts.isEmpty() && st.voided.isEmpty()) return;
        long now = server.overworld().getGameTime();
        // collectors that stopped ticking (chunk unloaded: the thief ran far away, teleported or never came back)
        for (Map.Entry<UUID, Hunt> e : new ArrayList<>(st.hunts.entrySet()))
            if (now - e.getValue().lastSeen > UNSEEN_LIMIT) st.settleEscape(server, e.getKey(), null);
        if (st.voided.values().removeIf(v -> now - v.time > VOID_KEEP || now < v.time)) st.setDirty();
        // marked copies that show up after the thief's death (graves, chests): checked while the record is open
        for (Voided v : st.voided.values()) {
            if (v.settled) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(v.thief);
            if (p != null) scanMarks(p);
        }
    }

    // ------------------------------------------------------------------ outcomes

    /** The thief got away (or the hunt timed out): huge reputation loss, he leaves. */
    void settleEscape(MinecraftServer server, UUID collector, @Nullable TraderEntity t) {
        Hunt h = hunts.remove(collector);
        if (h == null) return;
        setDirty();
        EconomyConfig cfg = EconomyConfig.INSTANCE;
        int rep = clampInt(cfg.collectorTheftRepLoss), inf = clampInt(cfg.collectorTheftInfamy);
        boolean any = penalize(server, h.thief, rep, inf);
        Component card = cardName(h.card);
        ServerPlayer p = server.getPlayerList().getPlayer(h.thief);
        if (p != null) {
            p.displayClientMessage(Component.translatable(any ? "tcgshop.collector_theft.escaped" : "tcgshop.collector_theft.escaped_noshop", card, rep)
                    .withStyle(ChatFormatting.GOLD), false);
            CardAdvancements.grant(p, "collector_escape");
            stripMarks(p, collector.toString());
            if (any) ShopServer.syncZones(p);
        }
        PhoneMessages.send(server, h.thief, "tcgshop.msg.sender.collector", msg(server, "escape", card), false);
        if (t != null && t.isAlive()) {
            speak(t, "give_up");
            t.setTarget(null);
            t.leaveTicks = LEAVE_TICKS;
        }
    }

    /** The robbed collector died: 50% chance (config) of the huge loss. */
    void settleKilled(MinecraftServer server, TraderEntity t, DamageSource src) {
        Hunt h = hunts.remove(t.getUUID());
        if (h == null) return; // already settled (he was leaving)
        setDirty();
        ServerPlayer killer = killerOf(t, src);
        ServerPlayer p = server.getPlayerList().getPlayer(h.thief);
        if (killer != null && killer.getUUID().equals(h.thief)) CardAdvancements.grant(killer, "collector_slain");
        EconomyConfig cfg = EconomyConfig.INSTANCE;
        Component name = t.getDisplayName(), card = cardName(h.card);
        if (t.getRandom().nextDouble() < clamp(cfg.collectorKillPenaltyChance, 0, 1)) {
            int rep = clampInt(cfg.collectorTheftRepLoss), inf = clampInt(cfg.collectorTheftInfamy);
            boolean any = penalize(server, h.thief, rep, inf);
            if (p != null) {
                p.displayClientMessage(Component.translatable(any ? "tcgshop.collector_theft.killed_seen" : "tcgshop.collector_theft.killed_seen_noshop", name, rep)
                        .withStyle(ChatFormatting.RED), false);
                if (any) ShopServer.syncZones(p);
            }
            if (any) PhoneMessages.send(server, h.thief, PhoneMessages.SENDER_SHOP, Component.translatable("tcgshop.collector_theft.msg.rumor", rep), false);
        } else if (p != null) {
            p.displayClientMessage(Component.translatable("tcgshop.collector_theft.killed_clean", name, card).withStyle(ChatFormatting.DARK_GREEN), false);
        }
        if (p != null) stripMarks(p, t.getStringUUID());
        // shop customers who saw it run off, same as with any other killing
        if (t.level() instanceof ServerLevel w)
            for (CustomerEntity c : w.getEntitiesOfClass(CustomerEntity.class, t.getBoundingBox().inflate(24), x -> x.isAlive()))
                c.witnessMurder();
    }

    /** The collector killed the thief: the card vanishes, smaller reputation loss, he leaves satisfied. */
    void settleThiefDied(MinecraftServer server, TraderEntity t, ServerPlayer p) {
        Hunt h = hunts.remove(t.getUUID());
        if (h == null) return;
        String cid = t.getStringUUID();
        Voided v = new Voided();
        v.thief = p.getUUID();
        v.card = h.card;
        v.time = server.overworld().getGameTime();
        v.settled = confiscate(p, cid);
        // the card is nowhere (stashed, or cashed before the hunt refused it): he takes its market value instead, first
        // from the CobbleDollars the thief carries, then from their shop registers, so dying after getting rid of the card
        // is never better than escaping. Paid in full = settled; a stashed marked copy then only loses the mark. Paid in
        // part (or nothing to take): the record stays open and a marked copy still vanishes.
        if (!v.settled) v.settled = chargeValue(server, p, h.card);
        voided.put(t.getUUID(), v);
        setDirty();
        EconomyConfig cfg = EconomyConfig.INSTANCE;
        int rep = clampInt(cfg.collectorDeathRepLoss), inf = clampInt(cfg.collectorDeathInfamy);
        boolean any = penalize(server, p.getUUID(), rep, inf);
        Component card = cardName(h.card);
        p.displayClientMessage(Component.translatable(any ? "tcgshop.collector_theft.died" : "tcgshop.collector_theft.died_noshop", t.getDisplayName(), card, rep)
                .withStyle(ChatFormatting.RED), false);
        CardAdvancements.grant(p, "collector_revenge");
        if (any) ShopServer.syncZones(p);
        PhoneMessages.send(server, p.getUUID(), "tcgshop.msg.sender.collector", msg(server, "got_back", card), false);
        speak(t, "got_back");
        t.playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 0.9f);
        t.setTarget(null);
        t.leaveTicks = LEAVE_TICKS + 40;
    }

    private static void afterDeath(LivingEntity e, DamageSource src) {
        if (!(e.level() instanceof ServerLevel w)) return;
        MinecraftServer server = w.getServer();
        if (e instanceof TraderEntity dead) {
            if (dead.robber() != null) get(server).settleKilled(server, dead, src);
            return;
        }
        if (!(e instanceof ServerPlayer p)) return;
        TraderEntity hunter = null;
        if (src.getEntity() instanceof TraderEntity a) hunter = a;
        else if (p.getKillCredit() instanceof TraderEntity b) hunter = b; // knocked into lava, off a cliff...
        if (hunter == null || !p.getUUID().equals(hunter.robber()) || hunter.leaveTicks >= 0 || !hunter.isAlive()) return;
        get(server).settleThiefDied(server, hunter, p);
    }

    @Nullable
    private static ServerPlayer killerOf(TraderEntity t, DamageSource src) {
        if (src.getEntity() instanceof ServerPlayer sp) return sp;
        return t.getKillCredit() instanceof ServerPlayer sp ? sp : null;
    }

    // ------------------------------------------------------------------ the card

    /** Removes the marked card from the inventory, ender chest, cursor and the items dropped around the player. */
    private static boolean confiscate(ServerPlayer p, String cid) {
        boolean found = removeMarked(p.getInventory(), cid, true) | removeMarked(p.getEnderChestInventory(), cid, true);
        if (cid.equals(stolenFrom(p.containerMenu.getCarried()))) {
            p.containerMenu.setCarried(ItemStack.EMPTY);
            found = true;
        }
        for (ItemEntity ie : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8),
                ie -> cid.equals(stolenFrom(ie.getItem())))) {
            ie.discard();
            found = true;
        }
        return found;
    }

    /**
     * The marked copy is nowhere to be found (binders, displays and sales refuse a hot card, so it was stashed or got out
     * some other way): he charges its market value, first from the thief's CobbleDollars, then from the registers of
     * their shops (zone balance, any dimension). Tells the player what was taken. true = paid in full.
     */
    private static boolean chargeValue(MinecraftServer server, ServerPlayer p, String cardId) {
        long value = cardValue(cardId);
        if (value <= 0) return false;
        long fromWallet = 0;
        if (dev.tcgshop.economy.CobbleDollarsBridge.isAvailable()) {
            long take = Math.min(value, Math.max(0, dev.tcgshop.economy.CobbleDollarsBridge.getBalance(p)));
            if (take > 0 && dev.tcgshop.economy.CobbleDollarsBridge.tryCharge(p, take)) fromWallet = take;
        }
        long fromShops = fromWallet < value ? takeFromRegisters(server, p.getUUID(), value - fromWallet) : 0;
        if (fromWallet + fromShops <= 0) return false;
        Component card = cardName(cardId), wallet = money(fromWallet), shops = money(fromShops);
        Component msg = fromShops <= 0 ? Component.translatable("tcgshop.collector_theft.paid", card, wallet)
                : fromWallet <= 0 ? Component.translatable("tcgshop.collector_theft.paid_register", card, shops)
                : Component.translatable("tcgshop.collector_theft.paid_both", card, wallet, shops);
        p.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), false);
        if (fromShops > 0) ShopServer.syncZones(p);
        return fromWallet + fromShops >= value;
    }

    /** Takes up to `want` from the registers (zone balance) of the shops that player owns. Returns how much it took. */
    private static long takeFromRegisters(MinecraftServer server, UUID owner, long want) {
        long got = 0;
        for (ServerLevel sw : server.getAllLevels()) {
            if (got >= want) break;
            ShopZones zs = ShopZones.get(sw);
            boolean dirty = false;
            for (ShopZones.Zone z : zs.allAndDormant()) { // 1.1: a dormant shop's till too
                if (got >= want) break;
                if (!z.owner.equals(owner) || z.balance <= 0) continue;
                long take = Math.min(z.balance, want - got);
                z.balance -= take;
                got += take;
                dirty = true;
            }
            if (dirty) zs.setDirty();
        }
        return got;
    }

    private static Component money(long v) {
        return Component.literal(dev.tcgshop.pricing.PriceService.format(v)).withStyle(ChatFormatting.GOLD);
    }

    /** Removes (remove = true) or un-marks every card stolen from that collector in an inventory. */
    private static boolean removeMarked(Container inv, String cid, boolean remove) {
        boolean any = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!cid.equals(stolenFrom(s))) continue;
            if (remove) inv.setItem(i, ItemStack.EMPTY);
            else strip(s);
            inv.setChanged();
            any = true;
        }
        return any;
    }

    /** The theft is settled in the thief's favour: the card is theirs for good (no mark, stacks normally again). */
    private static void stripMarks(ServerPlayer p, String cid) {
        removeMarked(p.getInventory(), cid, false);
        removeMarked(p.getEnderChestInventory(), cid, false);
        ItemStack cur = p.containerMenu.getCarried();
        if (cid.equals(stolenFrom(cur))) strip(cur);
    }

    /**
     * Checks the marks in a player's inventory and ender chest: cards of an open hunt keep it, cards of a voided theft
     * vanish (or only lose the mark if a copy was already taken back), any other mark is stale and is removed.
     */
    static void scanMarks(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        CollectorTheft st = get(server);
        boolean took = false;
        for (Container inv : List.<Container>of(p.getInventory(), p.getEnderChestInventory())) {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                String from = stolenFrom(s);
                if (from == null) continue;
                UUID cid;
                try { cid = UUID.fromString(from); } catch (IllegalArgumentException ex) { strip(s); continue; }
                if (st.hunts.containsKey(cid)) continue;
                Voided v = st.voided.get(cid);
                if (v != null && !v.settled) {
                    inv.setItem(i, ItemStack.EMPTY);
                    v.settled = true;
                    st.setDirty();
                    took = true;
                } else strip(s);
                inv.setChanged();
            }
        }
        if (took) p.displayClientMessage(Component.translatable("tcgshop.collector_theft.vanished").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
    }

    // ------------------------------------------------------------------ reputation

    /**
     * Sudden drop of every shop the player owns (in any dimension): reputation points now (they recover slowly toward
     * the shop's target) and infamy (lowers Safety, fades EconomyConfig.infamyDecayPerDay a day). Works offline.
     * Returns whether they own any shop.
     */
    static boolean penalize(MinecraftServer server, UUID owner, int rep, int infamy) {
        boolean any = false;
        for (ServerLevel sw : server.getAllLevels()) {
            ShopZones zs = ShopZones.get(sw);
            for (ShopZones.Zone z : zs.allAndDormant()) { // 1.1: dormant shops too (no dodging it by removing the core)
                if (!z.owner.equals(owner)) continue;
                double cur = z.repExact < 0 ? z.reputation : z.repExact;
                z.repExact = Math.max(0, cur - rep);
                z.reputation = (int) Math.round(z.repExact);
                z.infamy = Math.min(100, z.infamy + infamy);
                zs.setDirty();
                any = true;
            }
        }
        return any;
    }

    // ------------------------------------------------------------------ helpers

    static void speak(TraderEntity t, String cat) {
        int n = Math.max(1, LINES.getOrDefault(cat, 1));
        NpcLines.speak(t, Component.translatable("tcgshop.collector_theft.line." + cat + "." + t.getRandom().nextInt(n)));
    }

    private static Component msg(MinecraftServer server, String cat, Object... args) {
        int n = Math.max(1, MSGS.getOrDefault(cat, 1));
        return Component.translatable("tcgshop.collector_theft.msg." + cat + "." + server.overworld().getRandom().nextInt(n), args);
    }

    private static Component cardName(String id) {
        ItemStack s = id == null || id.isEmpty() ? ItemStack.EMPTY : BinderSlots.stackFor(id, 1);
        return s.isEmpty() ? Component.literal(id == null ? "?" : id) : s.getHoverName();
    }

    /** Market value of one copy of that card (0 if unknown). */
    private static long cardValue(String id) {
        ItemStack s = id == null || id.isEmpty() ? ItemStack.EMPTY : BinderSlots.stackFor(id, 1);
        return s.isEmpty() ? 0 : Math.max(0, dev.tcgshop.shop.CardSales.unitValue(s));
    }

    private static void poof(ServerLevel w, Entity t) {
        if (t instanceof TraderEntity tr) CollectorHunt.restore(tr); // 1.1: the blocks he broke or placed go back
        w.sendParticles(ParticleTypes.POOF, t.getX(), t.getY() + 1, t.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        t.discard();
    }

    private static int clampInt(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ persistence

    private static CollectorTheft fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        CollectorTheft s = new CollectorTheft();
        CompoundTag hs = nbt.getCompound("hunts");
        for (String k : hs.getAllKeys()) {
            try {
                CompoundTag c = hs.getCompound(k);
                if (!c.hasUUID("thief")) continue;
                Hunt h = new Hunt();
                h.thief = c.getUUID("thief");
                h.card = c.getString("card");
                h.start = c.getLong("start");
                h.lastSeen = c.getLong("last_seen");
                s.hunts.put(UUID.fromString(k), h);
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag vs = nbt.getCompound("voided");
        for (String k : vs.getAllKeys()) {
            try {
                CompoundTag c = vs.getCompound(k);
                if (!c.hasUUID("thief")) continue;
                Voided v = new Voided();
                v.thief = c.getUUID("thief");
                v.card = c.getString("card");
                v.time = c.getLong("time");
                v.settled = c.getBoolean("settled");
                s.voided.put(UUID.fromString(k), v);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        CompoundTag hs = new CompoundTag();
        hunts.forEach((id, h) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("thief", h.thief);
            c.putString("card", h.card);
            c.putLong("start", h.start);
            c.putLong("last_seen", h.lastSeen);
            hs.put(id.toString(), c);
        });
        nbt.put("hunts", hs);
        CompoundTag vs = new CompoundTag();
        voided.forEach((id, v) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("thief", v.thief);
            c.putString("card", v.card);
            c.putLong("time", v.time);
            c.putBoolean("settled", v.settled);
            vs.put(id.toString(), c);
        });
        nbt.put("voided", vs);
        return nbt;
    }
}
