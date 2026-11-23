package dev.tcgshop.entity;

import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.compat.CobblemonBattles;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.DealOpenPayload;
import dev.tcgshop.network.DealAnswerPayload;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.DecorBlocks;
import dev.tcgshop.shop.ShopDisplayBlock;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shop customer. Walks in from outside, goes around the displays, looks at the products and decides based on the
 * price versus the market, their personality and the reputation. If they buy, they take the product (carry it in hand),
 * go pay at the cash register and leave with it. On leaving they write a review (1-5 stars) and, if they left
 * happy, may come back another day as a regular customer.
 *
 * Types:
 *  · REGULAR   buys if the price is reasonable.
 *  · PICKY     fussy: only buys below market and complains if it's expensive.
 *  · WHALE     wealthy collector: pays up to almost double for rare things.
 *  · THIEF     thief: steals a product and runs. With a guard, always caught; without a guard flees like a
 *              Scalper (you can chase it, hit it or challenge it to a Pokémon battle to recover the stolen item).
 *  · HAGGLER   haggler: offers to buy something above or below your price.
 *  · SELLER    seller: wants to sell you a card, a pack or a box, and tells you why.
 *  · TRADER    collector who wants to trade one of their cards for one from your displays.
 * Those who want a deal show a "!" above their head: right-click to see it.
 */
public class CustomerEntity extends PathAwareEntity implements TcgNpc, CobblemonBattles.Fighter {
    public enum Type { REGULAR, PICKY, WHALE, THIEF, HAGGLER, SELLER, TRADER }

    /** 0.9.5: SIT_GO (goes to a free chair), SIT (sitting for a while) and LOOK (stops to look at furniture or a plush). */
    private enum State { ENTER, GO, BROWSE, CHECKOUT, WAIT_DEAL, LEAVE, FLEE, SIT_GO, SIT, LOOK }

    private static final String[] THIEF_POOL = {"purrloin", "nickit", "sneasel", "murkrow", "zorua", "meowth", "sableye", "impidimp", "poochyena"};

    private static final TrackedData<String> TITLE = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EMOTE = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TYPE = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WANTS = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SMELLY = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> WAIT = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** 0.9 events: role shown above an event visitor's head (language key; empty = the one for its type). */
    private static final TrackedData<String> EV_ROLE = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.STRING);

    private UUID zoneId;
    private State state = State.ENTER;
    private BlockPos target;
    private int targetSlot = -1, timer, browsesLeft;
    private boolean detected, entered, reviewed, returning;
    /** Has already had a Pokémon duel with the guard (only one per escape). */
    public boolean duelled;
    private ItemStack loot = ItemStack.EMPTY;
    private ItemStack dealItem = ItemStack.EMPTY, dealWant = ItemStack.EMPTY;
    private int dealMode;
    /** The client already received the deal close (CLOSED): leave() must not send it again. */
    private boolean dealNotified = true;
    private long dealPrice;
    private int reasonKey;
    // merchant selling you something: personality, motive and haggling
    public static final int P_COLLECTOR = 0, P_BEGINNER = 1, P_EXPERT = 2, P_NEGOTIATOR = 3, P_DESPERATE = 4, P_CASUAL = 5;
    public static final String[] PERSONAS = {"collector", "beginner", "expert", "negotiator", "desperate", "casual"};
    private static final int[] PERSONA_W = {16, 18, 12, 18, 14, 22};
    /** Sale motives (tcgshop.motive.<cat>.<n>): urgent cash, making a profit and others. */
    public static final int MOTIVES_CASH = 40, MOTIVES_PROFIT = 35, MOTIVES_OTHER = 45;
    /** Weight of each motive type by personality (cash, profit, other): it is almost always about money. */
    private static final int[][] MOTIVE_W = {{25, 35, 40}, {35, 25, 40}, {25, 60, 15}, {20, 70, 10}, {85, 5, 10}, {40, 35, 25}};
    private int persona = -1, patience, maxPatience, thinks, waitMax = 1;
    private String motive = "";
    private long floorPrice;
    /** Haggling customer (buys something from you): the most they would pay (hidden from you). Rises with shop reputation. */
    private long ceilPrice;
    /**
     * 1.0 tips: your asking price in a BUY deal (the display price, then your latest counteroffer). What the customer pays
     * above it is a tip. 0 = unknown (no tip is counted).
     */
    private long askPrice;
    /** 1.0: the biggest tip a customer leaves voluntarily at checkout (on top of the price). */
    private static final long CHECKOUT_TIP_CAP = 25_000;
    /** Haggling customer's lines when YOU sell to them (tcgshop.haggle.buy.<cat>.<n>, tools/gen_lang_haggle_rep.py). */
    public static final java.util.Map<String, Integer> BUY_LINES = java.util.Map.ofEntries(
            java.util.Map.entry("counter", 8), java.util.Map.entry("hold", 6), java.util.Map.entry("insulted", 6), java.util.Map.entry("walkaway", 6),
            java.util.Map.entry("accept", 8), java.util.Map.entry("arg_good", 5), java.util.Map.entry("arg_bad", 5), java.util.Map.entry("rep_good", 6),
            java.util.Map.entry("rep_bad", 5), java.util.Map.entry("think_raise", 5), java.util.Map.entry("think_same", 5), java.util.Map.entry("final_no", 5));
    /** Outcome of a closed deal (goes in the patience field of the CLOSED DealOpenPayload; price = final price). */
    public static final int OUT_UNKNOWN = 0, OUT_DEAL = 1, OUT_WALK = 2, OUT_REJECT = 3, OUT_TIMEOUT = 4;
    private Vec3d exit, spot;
    /** Purchase pending payment at the register. */
    private ItemStack bought = ItemStack.EMPTY;
    private long payPrice;
    private BlockPos register;
    /** Mood for the final review (starts at 3 stars). */
    private int mood = 3;
    private String reviewKey = "browse";
    // Scalper-style escape
    private int fleePhaseTicks, lostTicks;
    private boolean admiring;
    // ---- 0.9 random events
    /** Event visitor (null = normal customer). Not saved: on reload it leaves like the rest. */
    @Nullable
    private dev.tcgshop.events.VisitorSpec visitor;
    /** When it appeared (world.getTime()), for the review memory. -1 = loaded from disk. */
    private long arrivedAt = -1;
    /** Purchases left for a visitor who buys several things. */
    private int visitorBuys = 1;
    // ---- 0.9.5 seats and furniture (ShopSeats)
    /** Reserved seat (heading to it or seated). */
    @Nullable
    private dev.tcgshop.shop.ShopSeats.Seat sitSeat;
    @Nullable
    private Vec3d sitStand;
    /** Already tried to sit this visit (only once); sits after paying; has bought something. */
    private boolean sitTried, sitBuyer, boughtAny, browsedOnce;
    private long sitSince;
    /** Consecutive checks with the chair occupied (on the way / seated): a passing bump doesn't make it give up. */
    private int sitBlocked, sitShared;
    /** Already tried another chair after losing its own (once per visit). */
    private boolean sitRetried;
    /** Furniture or plush it stopped to look at. */
    @Nullable
    private BlockPos lookPos;
    private boolean lookPlush, lookArrived;
    private int furnLooks;

    public CustomerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        if (getNavigation() instanceof net.minecraft.entity.ai.pathing.MobNavigation nav) {
            nav.setCanPathThroughDoors(true); // they open the shop's wooden doors
            nav.setCanEnterOpenDoors(true);
        }
    }

    /** Register queue tickets (first come, first paid). */
    private static final java.util.concurrent.atomic.AtomicLong QUEUE_TICKETS = new java.util.concurrent.atomic.AtomicLong();
    private long queueTicket;
    private int serviceTicks;
    private boolean askedForService;
    /** Door it enters through (and leaves through); null if the shop has no doors. */
    @Nullable
    private BlockPos entryDoor;
    @Nullable
    private Vec3d entryOutside;

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TITLE, "");
        builder.add(SKIN, 0);
        builder.add(EMOTE, 0);
        builder.add(TYPE, 0);
        builder.add(WANTS, false);
        builder.add(SMELLY, false);
        builder.add(WAIT, -1);
        builder.add(EV_ROLE, "");
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new net.minecraft.entity.ai.goal.LongDoorInteractGoal(this, true)); // opens and closes the door when passing
        goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 6f));
        goalSelector.add(8, new LookAroundGoal(this));
    }

    public Type type() { return Type.values()[Math.floorMod(dataTracker.get(TYPE), Type.values().length)]; }

    @Override public String roleKey() {
        String ev = dataTracker.get(EV_ROLE);
        if (!ev.isEmpty()) return ev;
        return returning ? "tcgshop.role.regular_back" : "tcgshop.role." + type().name().toLowerCase(java.util.Locale.ROOT);
    }
    @Override public String npcTitle() { return dataTracker.get(TITLE); }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return dataTracker.get(EMOTE); }
    @Override public boolean wantsAttention() { return dataTracker.get(WANTS); }

    private void emote(int e) { dataTracker.set(EMOTE, e); }

    public boolean isSmelly() { return dataTracker.get(SMELLY); }

    /** You sprayed deodorant on them: they stop smelling bad. */
    public void sprayed(PlayerEntity by) {
        if (!isSmelly()) return;
        dataTracker.set(SMELLY, false);
        if (getWorld() instanceof ServerWorld sw) sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.6, getZ(), 8, 0.3, 0.4, 0.3, 0);
        NpcLines.sayNear(this, "spray_thanks");
        if (by instanceof ServerPlayerEntity sp) CardAdvancements.grant(sp, "spray_customer");
    }

    public boolean isFleeingThief() { return type() == Type.THIEF && state == State.FLEE && !loot.isEmpty(); }
    public boolean isDetected() { return detected; }

    // ------------------------------------------------------------------ spawning

    public static CustomerEntity spawn(ServerWorld world, ShopZones.Zone z, Type type, Vec3d pos, Vec3d exit, @Nullable ShopZones.Regular regular) {
        return spawn(world, z, type, pos, exit, regular, null, null);
    }

    public static CustomerEntity spawn(ServerWorld world, ShopZones.Zone z, Type type, Vec3d pos, Vec3d exit, @Nullable ShopZones.Regular regular,
                                       @Nullable BlockPos door, @Nullable Vec3d doorOutside) {
        return spawn(world, z, type, pos, exit, regular, door, doorOutside, null);
    }

    /** 0.9: with VisitorSpec = visitor of a random event (ShopLife.forceSpawnVisitor). */
    public static CustomerEntity spawn(ServerWorld world, ShopZones.Zone z, Type type, Vec3d pos, Vec3d exit, @Nullable ShopZones.Regular regular,
                                       @Nullable BlockPos door, @Nullable Vec3d doorOutside, @Nullable dev.tcgshop.events.VisitorSpec spec) {
        CustomerEntity c = TcgEntities.CUSTOMER.create(world);
        if (c == null) return null;
        c.arrivedAt = world.getTime();
        c.mood += dev.tcgshop.events.EventManager.mods(z).moodStart;
        c.zoneId = z.id;
        c.entryDoor = door;
        c.entryOutside = doorOutside;
        c.dataTracker.set(TYPE, type.ordinal());
        if (regular != null) {
            c.returning = true;
            c.dataTracker.set(SKIN, regular.skin());
            c.dataTracker.set(TITLE, regular.title());
            c.setCustomName(Text.literal(regular.name()));
        } else {
            c.dataTracker.set(SKIN, NpcIdentity.randomSkin(world.random));
            float titleChance = switch (type) { case WHALE -> 0.8f; case PICKY, TRADER -> 0.45f; case THIEF -> 0.15f; default -> 0.3f; };
            c.dataTracker.set(TITLE, NpcIdentity.randomTitle(world.random, titleChance));
            c.setCustomName(Text.literal(NpcIdentity.randomName(world.random)));
        }
        c.setCustomNameVisible(true);
        c.exit = exit;
        c.browsesLeft = 1 + world.random.nextInt(type == Type.WHALE ? 4 : 3);
        c.refreshPositionAndAngles(pos.x, pos.y, pos.z, world.random.nextFloat() * 360, 0);
        if (spec != null) {
            c.visitor = spec;
            c.mood += spec.moodStart;
            if (spec.browses > 0) c.browsesLeft = spec.browses;
            c.visitorBuys = Math.max(1, spec.purchases);
            if (!spec.titleKey.isEmpty()) c.dataTracker.set(EV_ROLE, spec.titleKey);
        }
        if (type == Type.SELLER) c.prepareSellOffer(world);
        if (type != Type.WHALE && spec == null && world.random.nextFloat() < 0.12f) c.dataTracker.set(SMELLY, true);
        world.spawnEntity(c);
        dev.tcgshop.events.EventManager.onCustomerSpawn(world, z);
        return c;
    }

    /** What it brings to sell you: almost always a card, sometimes a pack and very occasionally a box. */
    private void prepareSellOffer(ServerWorld world) {
        float r = random.nextFloat();
        if (r < 0.08f) {
            List<String> sets = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !dev.tcgshop.shop.PackLock.locked(id)) sets.add(CardInfo.setId(id));
            if (!sets.isEmpty()) dealItem = BoosterBox.of(sets.get(random.nextInt(sets.size())));
        } else if (r < 0.3f) {
            List<Item> sealed = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !dev.tcgshop.shop.PackLock.locked(id)) sealed.add(Registries.ITEM.get(id));
            if (!sealed.isEmpty()) dealItem = new ItemStack(sealed.get(random.nextInt(sealed.size())));
        }
        if (dealItem.isEmpty()) {
            List<Item> cards = allCards();
            if (cards.isEmpty()) return;
            ItemStack pick = new ItemStack(cards.get(random.nextInt(cards.size())));
            for (int tries = 0; tries < 6 && Rarity.of(pick).tier == 0 && random.nextFloat() < 0.6f; tries++)
                pick = new ItemStack(cards.get(random.nextInt(cards.size())));
            dealItem = pick;
        }
        long market = Math.max(1, ShopServer.priceOf(dealItem, 1.0));
        persona = weighted(PERSONA_W);
        int cat = weighted(MOTIVE_W[persona]);
        motive = switch (cat) {
            case 0 -> "cash." + random.nextInt(MOTIVES_CASH);
            case 1 -> "profit." + random.nextInt(MOTIVES_PROFIT);
            default -> "other." + random.nextInt(MOTIVES_OTHER);
        };
        // what it asks (relative to market) and the minimum it would accept (hidden from you)
        double[] ask = {0.9, 1.3, 0.6, 1.1, 0.95, 1.25, 1.05, 1.35, 0.5, 0.85, 0.7, 1.05};
        double a = ask[persona * 2] + random.nextDouble() * (ask[persona * 2 + 1] - ask[persona * 2]);
        if (cat == 0) a -= 0.08; // needs the money now
        else if (cat == 1) a += 0.08; // wants to make money, doesn't care whether it works out for you
        dealPrice = Math.max(10, Math.round(market * a));
        double[] fl = {0.85, 0.97, 0.7, 0.85, 0.85, 0.95, 0.72, 0.85, 0.6, 0.75, 0.75, 0.9};
        double f = fl[persona * 2] + random.nextDouble() * (fl[persona * 2 + 1] - fl[persona * 2]);
        floorPrice = Math.min(dealPrice, Math.max(Math.round(market * 0.45), Math.round(dealPrice * f)));
        if (visitor != null && visitor.fairSeller) { // 0.9 events: collector who sells: never below market
            dealPrice = Math.max(dealPrice, market);
            floorPrice = Math.max(floorPrice, market);
        }
        int[] pat = {3, 4, 3, 5, 2, 3};
        maxPatience = patience = Math.max(1, (int) Math.round((pat[persona] + random.nextInt(2)) * EconomyConfig.INSTANCE.merchantPatience * eventPatience(world)));
        dealMode = DealOpenPayload.SELL;
        reasonKey = 0;
    }

    private int weighted(int[] w) {
        int sum = 0;
        for (int x : w) sum += x;
        int r = random.nextInt(Math.max(1, sum));
        for (int i = 0; i < w.length; i++) if ((r -= w[i]) < 0) return i;
        return w.length - 1;
    }

    private static List<Item> allCards() {
        List<Item> cards = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds()) if (CardInfo.isCard(id)) cards.add(Registries.ITEM.get(id));
        return cards;
    }

    /** Collector: picks a card from your displays and offers one of theirs of similar value in exchange. */
    private boolean prepareTrade(ServerWorld world, ShopZones.Zone z) {
        List<BlockPos> poss = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        forDisplays(world, z, (d, i) -> {
            if (CardInfo.isCard(d.getStack(i))) { poss.add(d.getPos()); slots.add(i); }
        });
        if (poss.isEmpty()) return false;
        int k = random.nextInt(poss.size());
        if (!(world.getBlockEntity(poss.get(k)) instanceof ShopDisplayBlockEntity be)) return false;
        ItemStack want = be.getStack(slots.get(k)).copyWithCount(1);
        long wantValue = ShopServer.priceOf(want, 1.0);
        List<Item> cards = allCards();
        ItemStack offer = ItemStack.EMPTY;
        double bestDiff = Double.MAX_VALUE;
        for (int tries = 0; tries < 80 && !cards.isEmpty(); tries++) {
            ItemStack c = new ItemStack(cards.get(random.nextInt(cards.size())));
            if (c.isOf(want.getItem())) continue;
            double ratio = ShopServer.priceOf(c, 1.0) / (double) Math.max(1, wantValue);
            double target = 0.75 + random.nextDouble() * 0.55; // sometimes it's worth it, sometimes not
            double diff = Math.abs(ratio - target);
            if (diff < bestDiff) { bestDiff = diff; offer = c; }
        }
        if (offer.isEmpty()) return false;
        target = poss.get(k);
        targetSlot = slots.get(k);
        dealWant = want;
        dealItem = offer;
        dealPrice = ShopServer.priceOf(offer, 1.0);
        dealMode = DealOpenPayload.TRADE;
        return true;
    }

    // ------------------------------------------------------------------ behavior

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (getVehicle() instanceof ShopSeatEntity seat) { // 0.9.5: seated, facing the way the chair faces
            setYaw(seat.getYaw());
            setBodyYaw(seat.getYaw());
        }
        if (age % 5 != 0 || CobblemonBattles.isFighting(this) || dev.tcgshop.compat.NpcPokemonDuel.busy(this)) return;
        ServerWorld world = (ServerWorld) getWorld();
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z == null) { discard(); return; }
        if (age > 20 * 60 * 8 && state != State.FLEE && state != State.LEAVE) leave(world, z, null);
        timer -= 5;
        if (isSmelly() && age % 20 == 0) {
            world.spawnParticles(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.45f, 0.62f, 0.2f), 1.2f),
                    getX(), getY() + 1.9, getZ(), 3, 0.25, 0.3, 0.25, 0);
            if (age % 100 == 0 && z.contains(getBlockPos())) { z.smell = Math.min(100, z.smell + 1); ShopZones.get(world).markDirty(); }
        }
        if (!entered && z.contains(getBlockPos())) {
            entered = true;
            dev.tcgshop.shop.ShopLife.onEntered(getUuid()); // 0.9: now inside -> releases the path reservation and the queue
            Text evLine = visitor != null && visitor.linesCount > 0 && random.nextFloat() < 0.8f
                    ? Text.translatable("tcgshop.evline." + visitor.lines + "." + random.nextInt(visitor.linesCount))
                    : dev.tcgshop.events.EventManager.enterLine(z, random); // 0.9 events: they comment on what is happening
            if (evLine != null) NpcLines.speak(this, evLine);
            else if (returning) NpcLines.sayNear(this, type() == Type.SELLER ? "seller_back" : "regular_back");
            else if (!z.flags.isEmpty() && random.nextFloat() < 0.35f) commentOnShop(z);
            else if (random.nextFloat() < 0.3f) NpcLines.sayNear(this, "greet");
        }
        switch (state) {
            case ENTER -> {
                if (!entered) { // 0.9: capacity -> with the shop full it waits outside in the queue (ShopLife.queueGate)
                    int gate = dev.tcgshop.shop.ShopLife.queueGate(this, world, z, entryDoor, entryOutside);
                    if (gate == dev.tcgshop.shop.ShopLife.GATE_WAIT) return;
                    if (gate == dev.tcgshop.shop.ShopLife.GATE_GIVE_UP) { mood -= dev.tcgshop.shop.ShopLife.FULL_MOOD_PENALTY; reviewKey = "shop_full"; leave(world, z, null); return; }
                    // 0.9 events: gave up because an event blocks the door: no "full" review and no capacity penalty
                    if (gate == dev.tcgshop.shop.ShopLife.GATE_BLOCKED_GIVE_UP) { leave(world, z, null); return; }
                }
                if (!entered && entryOutside != null && squaredDistanceTo(entryOutside) > 2.2 * 2.2) {
                    // walks from afar to the shop door (navigation opens it when passing)
                    if (getNavigation().isIdle() || age % 40 == 0) getNavigation().startMovingTo(entryOutside.x, entryOutside.y, entryOutside.z, 0.85);
                    if (timer < -20 * 90) { entryOutside = null; } // stuck: let it get in however it can
                    return;
                }
                if (type() == Type.SELLER) { goDeal(world, z, "seller_hello"); return; }
                if (type() == Type.TRADER) {
                    if (prepareTrade(world, z)) { goDeal(world, z, "trader_hello"); return; }
                    dataTracker.set(TYPE, Type.REGULAR.ordinal()); // no cards it's interested in: browses like anyone else
                }
                // 0.9.5: between products, sometimes stops to look at a piece of furniture or a plush in the shop
                if (entered && browsedOnce && furnLooks < 2 && type() != Type.THIEF && random.nextFloat() < 0.25f && startLook(world, z)) return;
                if (!pickTarget(world, z)) {
                    // 0.9.5: has looked at everything: sometimes sits on a chair for a while before leaving
                    if (trySit(world, z, boughtAny)) return;
                    leave(world, z, "nothing");
                    return;
                }
                state = State.GO;
                timer = 400;
            }
            case GO -> {
                ShopDisplayBlockEntity be = targetDisplay(world);
                if (be == null || be.getStack(targetSlot).isEmpty()) { state = State.ENTER; return; }
                if (spot == null || squaredDistanceTo(spot) < 1.6 * 1.6 || squaredDistanceTo(target.toCenterPos()) < 2.4 * 2.4) {
                    getNavigation().stop();
                    state = State.BROWSE;
                    timer = 50 + random.nextInt(70);
                    emote(EMOTE_THINK);
                } else if (getNavigation().isIdle()) getNavigation().startMovingTo(spot.x, spot.y, spot.z, 0.9);
                if (timer <= 0) state = State.ENTER;
            }
            case BROWSE -> {
                lookAtTarget(world);
                if (timer <= 0) decide(world, z);
            }
            case CHECKOUT -> checkoutTick(world, z);
            case WAIT_DEAL -> {
                ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
                if (owner != null && owner.getServerWorld() == world) {
                    double d = squaredDistanceTo(owner);
                    if (d > 3.5 * 3.5 && d < 32 * 32 && getNavigation().isIdle()) getNavigation().startMovingTo(owner, 0.8);
                    else if (d <= 3 * 3) getNavigation().stop();
                    getLookControl().lookAt(owner);
                }
                if (age % 40 == 0) world.spawnParticles(ParticleTypes.NOTE, getX(), getY() + 2.6, getZ(), 1, 0.1, 0.1, 0.1, 0);
                dataTracker.set(WAIT, (int) Math.max(0, Math.min(1000, timer * 1000L / Math.max(1, waitMax))));
                if (timer <= 0) {
                    mood -= 1;
                    reviewKey = "ignored";
                    z.unattended++;
                    ShopZones.get(world).markDirty();
                    if (owner != null) closeDeal(owner, NpcLines.random(random, "deal_timeout"), OUT_TIMEOUT, 0);
                    leave(world, z, "deal_timeout");
                }
            }
            case FLEE -> fleeTick(world, z);
            case SIT_GO -> sitGoTick(world, z);
            case SIT -> sitTick(world, z);
            case LOOK -> lookTick(world, z);
            case LEAVE -> {
                if (exit == null) exit = getPos();
                if (!reviewed && !z.contains(getBlockPos())) finishVisit(world, z);
                // walks away from the shop: on reaching a point, keeps going farther
                if (squaredDistanceTo(exit) < 2.5 * 2.5) {
                    Vec3d away = getPos().subtract(z.core.toCenterPos()).multiply(1, 0, 1);
                    if (away.lengthSquared() < 1e-3) away = new Vec3d(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
                    exit = getPos().add(away.normalize().multiply(12));
                    getNavigation().stop();
                }
                if (getNavigation().isIdle()) getNavigation().startMovingTo(exit.x, exit.y, exit.z, 0.9);
                // only despawns far from the shop and where no player can see it (or, if stuck, after 2 minutes)
                double half = z.tierInfo().size / 2.0 + 14;
                net.minecraft.entity.player.PlayerEntity near = world.getClosestPlayer(this, 48);
                boolean unseen = near == null || squaredDistanceTo(near) > 40 * 40 || (squaredDistanceTo(near) > 20 * 20 && !near.canSee(this));
                boolean far = !z.contains(getBlockPos()) && squaredDistanceTo(z.core.toCenterPos()) > half * half;
                if ((far && unseen) || timer < -2400) {
                    if (!reviewed) finishVisit(world, z);
                    if (!unseen) world.spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                    discard();
                }
            }
        }
    }

    private void lookAtTarget(ServerWorld world) {
        if (target == null) return;
        BlockState s = world.getBlockState(target);
        if (s.getBlock() instanceof ShopDisplayBlock b) {
            Vec3d rel = dev.tcgshop.shop.DisplayKind.toWorld(s.get(ShopDisplayBlock.FACING),
                    new Vec3d(b.kind.slots[targetSlot].x(), b.kind.slots[targetSlot].y(), b.kind.slots[targetSlot].z()));
            getLookControl().lookAt(target.getX() + rel.x, target.getY() + rel.y, target.getZ() + rel.z);
        } else getLookControl().lookAt(target.toCenterPos());
    }

    @Nullable
    private ShopDisplayBlockEntity targetDisplay(ServerWorld world) {
        return target != null && targetSlot >= 0 && world.getBlockEntity(target) instanceof ShopDisplayBlockEntity be && targetSlot < be.size() ? be : null;
    }

    private interface SlotVisitor { void visit(ShopDisplayBlockEntity d, int slot); }

    private static void forDisplays(ServerWorld world, ShopZones.Zone z, SlotVisitor v) {
        dev.tcgshop.events.Mods mods = dev.tcgshop.events.EventManager.mods(z);
        int half = z.tierInfo().size / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                WorldChunk ch = world.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof ShopDisplayBlockEntity d) || d.isHome() || !z.contains(be.getPos())) continue;
                    // 0.9 events: displays or slots out of action (burned, broken, product on the floor...)
                    for (int i = 0; i < d.size(); i++) if (!d.getStack(i).isEmpty() && !mods.isDisplayDisabled(d.getPos(), i)) v.visit(d, i);
                }
            }
    }

    /** Picks a displayed product (collectors and thieves prefer expensive items). */
    private boolean pickTarget(ServerWorld world, ShopZones.Zone z) {
        if (browsesLeft-- <= 0) return false;
        List<BlockPos> cands = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        forDisplays(world, z, (d, i) -> { cands.add(d.getPos()); slots.add(i); });
        if (cands.isEmpty()) return false;
        int pick = random.nextInt(cands.size());
        if (visitor != null && visitor.filter != null) { // 0.9 events: the visitor looks for something specific (a set, packs...)
            List<Integer> fit = new ArrayList<>();
            for (int i = 0; i < cands.size(); i++)
                if (world.getBlockEntity(cands.get(i)) instanceof ShopDisplayBlockEntity d && dev.tcgshop.events.Mods.matchesFilter(visitor.filter, d.getStack(slots.get(i)), 1.0))
                    fit.add(i);
            if (!fit.isEmpty()) pick = fit.get(random.nextInt(fit.size()));
        } else if (type() == Type.WHALE || type() == Type.THIEF) {
            long best = -1;
            for (int i = 0; i < cands.size(); i++) {
                if (world.getBlockEntity(cands.get(i)) instanceof ShopDisplayBlockEntity d) {
                    long p = d.price(slots.get(i));
                    if (p > best && random.nextFloat() < 0.8f) { best = p; pick = i; }
                }
            }
        }
        target = cands.get(pick);
        targetSlot = slots.get(pick);
        BlockState s = world.getBlockState(target);
        spot = s.getBlock() instanceof ShopDisplayBlock b ? b.kind.slotWorld(s.get(ShopDisplayBlock.FACING), target, targetSlot) : target.toCenterPos();
        return true;
    }

    private void decide(ServerWorld world, ShopZones.Zone z) {
        browsedOnce = true;
        ShopDisplayBlockEntity be = targetDisplay(world);
        if (be == null || be.getStack(targetSlot).isEmpty()) { state = State.ENTER; return; }
        dev.tcgshop.events.Mods mods = dev.tcgshop.events.EventManager.mods(z);
        if (mods.isDisplayDisabled(be.getPos(), targetSlot)) { state = State.ENTER; return; } // 0.9: broke while it was on its way
        ItemStack st = be.getStack(targetSlot);
        long price = be.price(targetSlot), market = ShopServer.priceOf(st, 1.0);
        double ratio = market <= 0 ? 1 : price / (double) market;
        double rep = 0.6 + dev.tcgshop.shop.ShopLife.effectiveRep(z) / 125.0; // with repDelta and event light/decoration
        // in a shop with a good reputation people pay more: the "perceived" price is the real one divided by (1 + accepted markup)
        double prem = z.premiumFor(st);
        double adj = ratio / Math.max(0.5, 1 + prem) * mods.perceivedMult;
        if (type() == Type.THIEF) { steal(world, z, be); return; }
        // Packs and boxes need someone at the till (cashier or you at the register), but they no longer complain right away:
        // they join the queue and only protest if they run out of patience without anyone serving them.
        if (type() == Type.HAGGLER) {
            dealItem = st.copyWithCount(1);
            persona = weighted(PERSONA_W);
            dealPrice = Math.max(10, Math.round(market * (0.75 + random.nextDouble() * 0.5) * (1 + prem * 0.6) * mods.haggleMult));
            // the most it would pay: based on its personality and the shop's reputation
            double[] cl = {1.05, 1.25, 0.95, 1.05, 0.95, 1.1, 1.0, 1.2, 0.95, 1.15, 0.95, 1.15};
            double cm = cl[persona * 2] + random.nextDouble() * (cl[persona * 2 + 1] - cl[persona * 2]);
            ceilPrice = Math.max(dealPrice, Math.round(market * cm * (1 + prem) * mods.haggleMult));
            if (visitor != null && visitor.priceCap > 0) ceilPrice = Math.max(dealPrice, Math.min(ceilPrice, Math.round(market * Math.max(visitor.priceCap, 1.0))));
            int[] pat = {3, 4, 3, 5, 2, 3};
            maxPatience = patience = Math.max(1, (int) Math.round((pat[persona] + random.nextInt(2)) * EconomyConfig.INSTANCE.merchantPatience * mods.patienceMult));
            thinks = 0;
            dealMode = DealOpenPayload.BUY;
            askPrice = Math.max(0, price); // 1.0: if its offer is above your price, the difference is a tip
            goDeal(world, z, "haggle");
            return;
        }
        double p = switch (type()) {
            case REGULAR -> Math.max(0.05, Math.min(0.9, 1.45 - adj)) * rep;
            case PICKY -> adj <= 0.92 ? 0.8 : 0;
            case WHALE -> adj <= 1.9 ? 0.92 : 0.1;
            default -> 0;
        };
        if (returning) p = Math.min(0.95, p + 0.15);
        // 0.9 events: desire to buy (modifiers) and visitors: price cap over market and "just looking"
        p *= mods.buyMultFor(st, ratio);
        if (visitor != null) {
            p *= visitor.buyMult;
            if (visitor.noBuy) p = 0;
            else if (visitor.priceCap > 0 && ratio > visitor.priceCap) {
                p = 0;
                if (random.nextFloat() < 0.5f) NpcLines.sayNear(this, "expensive");
            }
        }
        if (z.smell > 40) {
            p *= Math.max(0.2, 1 - (z.smell - 40) / 80.0);
            if (random.nextFloat() < 0.35f) {
                NpcLines.sayNear(this, "smell_complain");
                mood -= 1;
                reviewKey = "smell";
            }
        }
        if (random.nextDouble() < p) {
            // takes the product and goes to pay at the register
            bought = be.removeStack(targetSlot, 1);
            payPrice = price;
            if (bought.isEmpty()) { state = State.ENTER; return; }
            equipStack(EquipmentSlot.MAINHAND, bought.copy());
            setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
            emote(EMOTE_LOVE);
            mood += adj <= 0.9 ? 3 : adj <= 1.0 ? 2 : 1;
            reviewKey = adj <= 0.9 ? "bargain" : adj <= 1.15 ? "fair" : "pricey";
            // pays above market and happily, because the shop is worth it
            boolean overpay = prem > 0.01 && ratio > 1.03 && adj <= 1.15;
            if (type() == Type.WHALE) {
                mood += 1;
                z.whales++;
                ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
                if (owner != null) CardAdvancements.grant(owner, "whale_customer");
            }
            NpcLines.sayNear(this, overpay && random.nextFloat() < 0.7f ? "overpay_happy" : type() == Type.WHALE ? "whale" : "buy");
            playSound(SoundEvents.ENTITY_VILLAGER_YES, 0.8f, 1.1f);
            register = nearestRegister(world, z);
            if (register == null) {
                if (ShopServer.needsCashier(bought) && !ShopServer.cashierOnDuty(world, z)) { // without a register there's nobody to take payment
                    returnBought(world);
                    NpcLines.sayNear(this, "no_cashier");
                    z.complaints++;
                    mood -= 2;
                    reviewKey = "no_cashier";
                    leave(world, z, null);
                } else pay(world, z);
            } else joinQueue();
            return;
        }
        emote(adj > 1.15 ? EMOTE_ANGRY : EMOTE_THINK);
        if (adj > 1.15) {
            z.complaints++;
            mood -= 1;
            reviewKey = "expensive";
            NpcLines.sayNear(this, "expensive");
        } else if (random.nextFloat() < 0.4f) NpcLines.sayNear(this, "nobuy");
        ShopZones.get(world).markDirty();
        state = State.ENTER;
    }

    // ------------------------------------------------------------------ register queue

    /** Joins the register queue: takes a ticket and starts counting down its patience. */
    private void joinQueue() {
        state = State.CHECKOUT;
        queueTicket = QUEUE_TICKETS.incrementAndGet();
        serviceTicks = 0;
        askedForService = false;
        timer = waitMax = (int) Math.max(200, 20 * EconomyConfig.INSTANCE.customerWaitSeconds * eventPatience(getWorld()));
        dataTracker.set(WAIT, 1000);
    }

    /** 0.9 events: this customer's patience (shop and visitor modifiers). */
    private double eventPatience(World w) {
        double m = visitor != null ? visitor.patienceMult : 1.0;
        if (w instanceof ServerWorld sw && zoneId != null) {
            ShopZones.Zone z = ShopZones.get(sw).byId(zoneId);
            if (z != null) m *= dev.tcgshop.events.EventManager.mods(z).patienceMult;
        }
        return Math.max(0.3, Math.min(2.0, m));
    }

    /** How many are ahead at the same register. */
    private int queueIndex(ServerWorld world, ShopZones.Zone z) {
        final BlockPos reg = register;
        return world.getEntitiesByClass(CustomerEntity.class, z.box().expand(8),
                c -> c != this && c.isAlive() && c.state == State.CHECKOUT && reg.equals(c.register) && c.queueTicket < queueTicket).size();
    }

    private static boolean standable(ServerWorld world, BlockPos p) {
        return !world.getBlockState(p).blocksMovement() && !world.getBlockState(p.up()).blocksMovement()
                && world.getBlockState(p.down()).isSolidBlock(world, p.down());
    }

    /**
     * Spot number k in the queue: the first in front of the counter and the rest one behind another, moving away from the register;
     * if there's a wall or furniture, the queue turns to one side.
     */
    private static BlockPos queueSpot(ServerWorld world, BlockPos front, Direction f, int k) {
        java.util.List<BlockPos> line = new java.util.ArrayList<>();
        line.add(front);
        BlockPos cur = front;
        Direction[] prefs = {f, f.rotateYClockwise(), f.rotateYCounterclockwise()};
        while (line.size() <= k && line.size() < 12) {
            BlockPos next = null;
            for (Direction d : prefs) {
                BlockPos n = cur.offset(d);
                if (!line.contains(n) && standable(world, n)) { next = n; break; }
            }
            if (next == null) break;
            line.add(next);
            cur = next;
        }
        return line.get(Math.min(k, line.size() - 1));
    }

    /**
     * In the queue: goes to its spot (behind the one in front), waits facing the register and, when it's its turn and it's at the
     * counter, gets charged (faster with a cashier). Everyone has a patience bar: if it runs out, it puts the product
     * back and leaves without buying.
     */
    private void checkoutTick(ServerWorld world, ShopZones.Zone z) {
        if (register == null || !world.getBlockState(register).isOf(TcgBlocks.CASH_REGISTER)) { pay(world, z); return; }
        Direction f = world.getBlockState(register).get(Properties.HORIZONTAL_FACING);
        BlockPos front = register.offset(f);
        int idx = queueIndex(world, z);
        BlockPos spotPos = queueSpot(world, front, f, idx);
        double dx = spotPos.getX() + 0.5 - getX(), dz = spotPos.getZ() + 0.5 - getZ();
        boolean there = dx * dx + dz * dz < 0.7 * 0.7;
        if (!there) {
            if (getNavigation().isIdle() || age % 20 == 0) getNavigation().startMovingTo(spotPos.getX() + 0.5, spotPos.getY(), spotPos.getZ() + 0.5, 0.75);
        } else {
            getNavigation().stop();
            getLookControl().lookAt(register.toCenterPos());
        }
        boolean staffed = ShopServer.cashierOnDuty(world, z);
        boolean needsStaff = ShopServer.needsCashier(bought);
        if (idx == 0 && dx * dx + dz * dz < 1.3 * 1.3) {
            if (needsStaff && !staffed) {
                serviceTicks = 0; // waits for someone to come to the register (you or a cashier)
                if (!askedForService) {
                    askedForService = true;
                    NpcLines.sayNear(this, "wait_register");
                    notifyOwner(world, z, Text.translatable("tcgshop.customer.waiting_register", getDisplayName()).formatted(Formatting.YELLOW));
                }
            } else {
                serviceTicks += 5;
                if (serviceTicks >= Math.round((staffed ? 40 : 70) * dev.tcgshop.events.EventManager.mods(z).serviceMult)) { pay(world, z); return; }
            }
        } else serviceTicks = 0;
        dataTracker.set(WAIT, (int) Math.max(0, Math.min(1000, timer * 1000L / Math.max(1, waitMax))));
        if (timer <= 0) leaveQueue(world, z, needsStaff && !staffed);
    }

    /** The owner charges them by hand (right-click a customer in the queue). */
    public boolean serveByOwner(ServerPlayerEntity owner) {
        if (state != State.CHECKOUT || bought.isEmpty()) return false;
        ServerWorld world = owner.getServerWorld();
        ShopZones.Zone z = ShopZones.get(world).byId(zoneId);
        if (z == null || !z.owner.equals(owner.getUuid())) return false;
        mood += timer > waitMax / 2 ? 1 : 0; // served quickly: a bit happier
        if ("queue_slow".equals(reviewKey) || "no_cashier".equals(reviewKey)) reviewKey = "fair";
        owner.getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.NEUTRAL, 0.7f, 1.6f);
        pay(world, z);
        return true;
    }

    /** Gets tired of waiting: returns the product and leaves. */
    private void leaveQueue(ServerWorld world, ShopZones.Zone z, boolean nobody) {
        returnBought(world);
        NpcLines.sayNear(this, nobody ? "no_cashier" : "queue_leave");
        mood -= 2;
        reviewKey = nobody ? "no_cashier" : "queue_slow";
        z.unattended++;
        if (nobody) z.complaints++;
        ShopZones.get(world).markDirty();
        register = null;
        leave(world, z, null);
    }

    /** Puts the product it was going to buy back in its slot (or another free one on the same display; if none, on the floor). */
    /** 0.9 events (Jigglypuff...): the customer leaves, returning what it was carrying, with the event's review. */
    public boolean leaveForEvent(String key) {
        if (!(getWorld() instanceof ServerWorld world) || type() == Type.THIEF || visitor != null) return false;
        if (state == State.LEAVE || state == State.FLEE || state == State.WAIT_DEAL) return false;
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z == null) return false;
        returnBought(world);
        register = null;
        mood -= 1;
        if (key != null && (reviewKey == null || !java.util.Set.of("murder", "hit", "infamy", "shop_full").contains(reviewKey))) reviewKey = key;
        leave(world, z, null);
        return true;
    }

    private void returnBought(ServerWorld world) {
        if (bought.isEmpty()) return;
        ShopDisplayBlockEntity be = targetDisplay(world);
        boolean placed = false;
        if (be != null) {
            if (be.getStack(targetSlot).isEmpty()) { be.setStack(targetSlot, bought); placed = true; }
            else for (int i = 0; i < be.size() && !placed; i++)
                if (be.getStack(i).isEmpty()) { be.setStack(i, bought); placed = true; }
        }
        if (!placed) dropStack(bought);
        bought = ItemStack.EMPTY;
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Nullable
    private BlockPos nearestRegister(ServerWorld world, ShopZones.Zone z) {
        int half = z.tierInfo().size / 2;
        for (BlockPos p : BlockPos.iterateOutwards(getBlockPos(), half + 2, 4, half + 2)) {
            if (z.contains(p) && world.getBlockState(p).isOf(TcgBlocks.CASH_REGISTER)) return p.toImmutable();
        }
        return null;
    }

    /** Pays for what it's holding: the money goes into the shop's register. */
    private void pay(ServerWorld world, ShopZones.Zone z) {
        if (bought.isEmpty()) { leave(world, z, null); return; }
        long earned = ShopServer.payFromNpc(world, z, bought, payPrice, register != null ? register : getBlockPos());
        long tip = rollCheckoutTip(world, z);
        if (tip > 0) { // 1.0: pays more than your price: the difference is a tip (into the same till)
            z.balance += tip;
            z.revenue += tip;
            dev.tcgshop.shop.TipLedger.record(world.getServer(), z.owner, tip, world.getTimeOfDay() / 24000);
            ShopZones.get(world).markDirty();
            NpcLines.speak(this, NpcLines.random(random, "tip_checkout", PriceService.format(tip)));
            world.playSound(null, getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.6f, 1.6f);
            notifyOwner(world, z, Text.translatable("tcgshop.customer.bought_tip", getDisplayName(), bought.toHoverableText(),
                    Text.literal(PriceService.format(earned)).formatted(Formatting.GOLD), Text.literal(PriceService.format(tip)).formatted(Formatting.YELLOW)));
        } else {
            if (register != null && random.nextFloat() < 0.5f) NpcLines.sayNear(this, "checkout");
            notifyOwner(world, z, Text.translatable("tcgshop.customer.bought", getDisplayName(), bought.toHoverableText(),
                    Text.literal(PriceService.format(earned)).formatted(Formatting.GOLD)));
        }
        bought = ItemStack.EMPTY;
        register = null;
        boughtAny = true;
        ShopServer.syncZones(world.getServer());
        if (visitor != null && --visitorBuys > 0 && browsesLeft > 0) { // 0.9: the event visitor keeps buying
            equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            dataTracker.set(WAIT, -1);
            state = State.ENTER;
            return;
        }
        if (trySit(world, z, true)) return; // 0.9.5: sometimes sits down to admire its purchase before leaving
        leave(world, z, null);
    }

    /**
     * 1.0 tips: a customer happy with the price (bargain or fair) sometimes pays a bit more than your asking price.
     * More likely in a well-reputed shop, for regulars and for whales (who also tip more). Returns 0 = no tip.
     */
    /** True when the haggled price of the current purchase already included a tip (then no checkout tip). Not saved. */
    private boolean dealTipped;

    private long rollCheckoutTip(ServerWorld world, ShopZones.Zone z) {
        if (dealTipped) { dealTipped = false; return 0; }
        if (payPrice <= 0 || type() == Type.THIEF) return 0;
        boolean bargain = "bargain".equals(reviewKey);
        if (!bargain && !"fair".equals(reviewKey)) return 0;
        int rep = Math.max(0, Math.min(100, dev.tcgshop.shop.ShopLife.effectiveRep(z)));
        double chance = 0.04 + 0.06 * rep / 100.0 + (bargain ? 0.04 : 0) + (returning ? 0.04 : 0) + (type() == Type.WHALE ? 0.05 : 0)
                + dev.tcgshop.shop.TipLedger.chanceBonus.applyAsDouble(world.getServer(), z.owner);
        if (random.nextDouble() >= chance) return 0;
        double pct = type() == Type.WHALE ? 0.10 + random.nextDouble() * 0.15 : 0.05 + random.nextDouble() * 0.10;
        return Math.max(1, Math.min(CHECKOUT_TIP_CAP, Math.round(payPrice * pct)));
    }

    /** 1.0 tips: in a BUY deal, what the customer currently offers above your asking price. */
    private long currentTip() {
        return dealMode == DealOpenPayload.BUY && askPrice > 0 && dealPrice > askPrice ? dealPrice - askPrice : 0;
    }

    /** Your asking price as sent to the deal window (only BUY deals have one). */
    private long askForPayload() {
        return dealMode == DealOpenPayload.BUY ? askPrice : 0;
    }

    private void steal(ServerWorld world, ShopZones.Zone z, ShopDisplayBlockEntity be) {
        loot = be.removeStack(targetSlot, 1);
        if (loot.isEmpty()) { state = State.ENTER; return; }
        equipStack(EquipmentSlot.MAINHAND, loot.copy());
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        z.stolen++;
        ShopZones.get(world).markDirty();
        state = State.FLEE;
        fleePhaseTicks = 60;
        admiring = false;
        setPersistent();
        emote(EMOTE_MONEY);
        boolean guard = guardOf(world, z) != null;
        for (BlockPos p : BlockPos.iterateOutwards(getBlockPos(), 12, 3, 12)) {
            if (world.getBlockState(p).isOf(TcgBlocks.THEFT_DETECTOR)) {
                DecorBlocks.TheftDetectorBlock.trigger(world, p);
                detected = true;
                break;
            }
        }
        if (guard) detected = true; // the guard sees everything
        if (detected) {
            dev.tcgshop.shop.ShopSeats.alarm(z.id, world.getTime()); // 0.9.5: with the alarm, those seated stand up
            addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 40, 0, false, false));
            addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 30, guard ? 1 : 0, false, false));
        }
        notifyOwner(world, z, Text.translatable(detected ? "tcgshop.thief.alarm" : "tcgshop.thief.stole", getDisplayName(), loot.toHoverableText())
                .formatted(Formatting.RED));
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null && !guard) owner.sendMessage(Text.translatable(CobblemonBattles.available() ? "tcgshop.thief.chase_hint_battle" : "tcgshop.thief.chase_hint")
                .formatted(Formatting.YELLOW), false);
    }

    @Nullable
    private EmployeeEntity guardOf(ServerWorld world, ShopZones.Zone z) {
        if (!z.guard) return null;
        List<EmployeeEntity> g = world.getEntitiesByClass(EmployeeEntity.class, z.box().expand(48), e -> e.isGuard() && z.id.equals(e.zoneId()));
        return g.isEmpty() ? null : g.get(0);
    }

    /** Thief escape: with a guard it is always caught; without a guard it flees like a Scalper until you catch it or move away. */
    private void fleeTick(ServerWorld world, ShopZones.Zone z) {
        EmployeeEntity guard = guardOf(world, z);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        double escape = Math.pow(z.tierInfo().size / 2.0 + 8, 2);
        if (guard != null) {
            if (getNavigation().isIdle() || age % 20 == 0) {
                Vec3d away = getPos().subtract(z.core.toCenterPos()).normalize().multiply(14);
                getNavigation().startMovingTo(getX() + away.x, getY(), getZ() + away.z, 1.0);
            }
            // if it gets out, the guard radios it in and it's detained anyway
            if (squaredDistanceTo(z.core.toCenterPos()) > escape) detain(world, z, guard);
            return;
        }
        fleePhaseTicks -= 5;
        if (fleePhaseTicks <= 0) {
            admiring = !admiring;
            if (admiring) {
                fleePhaseTicks = 50 + random.nextInt(30);
                emote(1 + random.nextInt(2));
                playSound(SoundEvents.ENTITY_PIGLIN_ADMIRING_ITEM, 1f, 1.2f);
            } else {
                fleePhaseTicks = 60 + random.nextInt(50);
                emote(EMOTE_NONE);
            }
        }
        if (admiring) {
            getNavigation().stop();
            setPitch(45f);
            if (age % 20 == 0) world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2.2, getZ(), 2, 0.2, 0.1, 0.2, 0);
        } else if (getNavigation().isIdle() || age % 20 == 0) {
            Vec3d from = owner != null && owner.squaredDistanceTo(this) < 40 * 40 ? owner.getPos() : z.core.toCenterPos();
            Vec3d away = getPos().subtract(from).normalize().multiply(12);
            getNavigation().startMovingTo(getX() + away.x, getY(), getZ() + away.z, detected ? 1.15 : 1.35);
        }
        lostTicks = (owner == null || owner.getServerWorld() != world || squaredDistanceTo(owner) > 64 * 64) ? lostTicks + 5 : 0;
        if (lostTicks > 600) {
            NpcLines.sayNear(this, "thief_escape");
            if (owner != null) owner.sendMessage(Text.translatable("tcgshop.thief.escaped", getDisplayName(), loot.toHoverableText()).formatted(Formatting.RED), false);
            discard();
        }
    }

    /** Detained: the product goes back to its display (or is credited to the register). guard may be null (caught by the owner). */
    public void detain(ServerWorld world, ShopZones.Zone z, @Nullable EmployeeEntity guard) {
        if (loot.isEmpty()) return;
        returnLoot(world, z);
        z.detained++;
        ShopZones.get(world).markDirty();
        if (guard != null) guard.announce(Text.translatable("tcgshop.thief.caught", getDisplayName(), Text.translatable("tcgshop.thief.loot_back")));
        NpcLines.sayNear(this, "thief_caught");
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.8, getZ(), 6, 0.3, 0.3, 0.3, 0);
        playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1f, 0.8f);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null) {
            CardAdvancements.grant(owner, "catch_thief");
            if (z.detained >= 10) CardAdvancements.grant(owner, "catch_thief_10");
        }
        discard();
    }

    private void returnLoot(ServerWorld world, ShopZones.Zone z) {
        if (loot.isEmpty()) return;
        ShopDisplayBlockEntity be = targetDisplay(world);
        if (be != null && be.getStack(targetSlot).isEmpty()) be.setStack(targetSlot, loot);
        else z.balance += ShopServer.priceOf(loot, 1.0); // the slot is no longer free: its value is credited to the register
        z.recovered++;
        loot = ItemStack.EMPTY;
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ShopZones.get(world).markDirty();
    }

    private void goDeal(ServerWorld world, ShopZones.Zone z, String line) {
        if (dealItem.isEmpty()) { leave(world, z, null); return; }
        state = State.WAIT_DEAL;
        dealNotified = false;
        timer = waitMax = (int) Math.max(200, 20 * EconomyConfig.INSTANCE.customerWaitSeconds * eventPatience(world));
        dataTracker.set(WAIT, 1000);
        emote(EMOTE_NONE);
        dataTracker.set(WANTS, true);
        long tip = currentTip();
        if (tip > 0) NpcLines.speak(this, NpcLines.random(random, "tip_offer", PriceService.format(tip))); // 1.0: offers above your price
        else NpcLines.sayNear(this, line);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null && owner.squaredDistanceTo(this) < 64 * 64)
            owner.sendMessage(Text.translatable("tcgshop.deal.waiting", getDisplayName()).formatted(Formatting.YELLOW), true);
    }

    private void leave(ServerWorld world, ShopZones.Zone z, String line) {
        dev.tcgshop.shop.ShopLife.forget(getUuid()); // 0.9: out of the door queue and path reservations
        standUp(world); // 0.9.5: if it was seated (or going to sit), it stands up and releases the chair
        // if it leaves mid-deal without closing it (item gone, gets tired, gets scared...), notify the window
        if (state == State.WAIT_DEAL && !dealNotified && z != null) {
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
            if (owner != null) closeDeal(owner, Text.translatable("tcgshop.deal.gone"), OUT_UNKNOWN, 0);
            dealNotified = true;
        }
        if (line != null && random.nextFloat() < 0.5f) NpcLines.sayNear(this, line);
        dataTracker.set(WANTS, false);
        dataTracker.set(WAIT, -1);
        int stars = stars();
        emote(stars >= 4 ? EMOTE_LOVE : stars <= 2 ? EMOTE_ANGRY : EMOTE_NONE);
        state = State.LEAVE;
        timer = 0;
        getNavigation().stop();
    }

    /** Review stars: the 5th requires a very happy customer (mood >= fiveStarMood; starts at 3). */
    private int stars() {
        if (mood >= Math.max(5, EconomyConfig.INSTANCE.fiveStarMood)) return 5;
        return Math.max(1, Math.min(4, mood));
    }

    /** Comments on something it noticed about the shop (roof, light, nearby monsters, animals, prices...) and is slightly affected by it. */
    private void commentOnShop(ShopZones.Zone z) {
        List<String> f = new ArrayList<>(z.flags);
        String k = f.get(random.nextInt(f.size()));
        NpcLines.sayNear(this, "comment_" + k);
        switch (k) {
            case "hostile", "infamy" -> { mood -= 1; reviewKey = k.equals("hostile") ? "unsafe" : "infamy"; }
            case "no_roof", "no_walls", "dark", "empty" -> { if (random.nextBoolean()) { mood -= 1; reviewKey = "env_" + k; } }
            case "cozy", "bright", "pokemon", "pets", "cheap" -> { if (random.nextFloat() < 0.4f) { mood += 1; reviewKey = "env_" + k; } }
            default -> {}
        }
    }

    @Override
    public int waitPermille() { return dataTracker.get(WAIT); }

    /** Saw you kill someone: gets scared and leaves with the worst review. */
    public void witnessMurder() {
        if (!(getWorld() instanceof ServerWorld world) || type() == Type.THIEF) return;
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z == null || state == State.FLEE) return;
        NpcLines.sayNear(this, "murder_witness");
        mood = 1;
        reviewKey = "murder";
        dealItem = ItemStack.EMPTY;
        leave(world, z, null);
        getNavigation().stop();
    }

    /** On leaving the shop: writes its review and, if it left happy, signs up as a regular customer. */
    private void finishVisit(ServerWorld world, ShopZones.Zone z) {
        reviewed = true;
        if (type() == Type.THIEF) return;
        // 0.9 events: the review remembers what happened in the shop while it was there (or that it was fixed quickly)
        dev.tcgshop.events.EventManager.ReviewOutcome ro = dev.tcgshop.events.EventManager.reviewFor(z, arrivedAt, world.getTime(), reviewKey, random);
        if (ro.key() != null) reviewKey = ro.key();
        mood += ro.mood();
        if (visitor != null) {
            dev.tcgshop.events.EventManager.VisitorResult vr = dev.tcgshop.events.EventManager.visitorFinished(world, z, this, visitor, stars());
            if (vr.key() != null) reviewKey = vr.key();
            if (vr.stars() > 0) mood = vr.stars() >= 5 ? Math.max(5, EconomyConfig.INSTANCE.fiveStarMood) : vr.stars();
        }
        int stars = stars();
        long day = world.getTimeOfDay() / 24000;
        z.addReview(getName().getString(), stars, reviewKey, day);
        if (stars >= 4) z.remember(getName().getString(), npcSkin(), npcTitle());
        if (random.nextFloat() < 0.35f) NpcLines.sayNear(this, stars >= 4 ? "leave_happy" : stars <= 2 ? "leave_sad" : "nothing");
        ShopZones.get(world).markDirty();
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null && owner.squaredDistanceTo(this) < 96 * 96)
            owner.sendMessage(Text.translatable("tcgshop.review.left", getDisplayName(), "★".repeat(stars) + "☆".repeat(5 - stars),
                    Text.translatable(new ShopZones.Review(getName().getString(), stars, reviewKey, day).textKey())).formatted(stars >= 4 ? Formatting.GREEN : stars <= 2 ? Formatting.RED : Formatting.GRAY), true);
    }

    private void notifyOwner(ServerWorld world, ShopZones.Zone z, Text msg) {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null && owner.squaredDistanceTo(this) < 96 * 96) owner.sendMessage(msg, true);
    }

    // ------------------------------------------------------------------ interaction, deals and battle

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp) || getWorld().isClient) return ActionResult.success(getWorld().isClient);
        ShopZones.Zone z = ShopZones.get(sp.getServerWorld()).byId(zoneId);
        if (visitor != null && z != null && dev.tcgshop.events.EventManager.onVisitorInteract(sp, z, this, visitor)) return ActionResult.SUCCESS;
        if (type() == Type.THIEF && state == State.FLEE && !loot.isEmpty()) {
            if (CobblemonBattles.available() && CobblemonBattles.challenge(sp, this)) {
                NpcLines.sayNear(this, "thief_battle");
                CardAdvancements.grant(sp, "battle_thief");
            } else NpcLines.say(this, sp, "thief_talk");
            return ActionResult.SUCCESS;
        }
        if (state == State.CHECKOUT && z != null && z.owner.equals(sp.getUuid()) && serveByOwner(sp)) return ActionResult.SUCCESS;
        if (state == State.WAIT_DEAL && z != null && z.owner.equals(sp.getUuid()) && !dealItem.isEmpty()) {
            long market = ShopServer.priceOf(dealItem, 1.0);
            Text line = switch (dealMode) {
                case DealOpenPayload.SELL -> motive.isEmpty() ? Text.translatable("tcgshop.npc.reason." + reasonKey) : Text.translatable("tcgshop.motive." + motive, dealItem.getName());
                case DealOpenPayload.TRADE -> NpcLines.random(random, "trader_hello");
                default -> currentTip() > 0 ? NpcLines.random(random, "tip_offer", PriceService.format(currentTip())) // 1.0: tip on top of your price
                        : NpcLines.random(random, "haggle_offer");
            };
            long wantMarket = dealWant.isEmpty() ? 0 : ShopServer.priceOf(dealWant, 1.0);
            if (ServerPlayNetworking.canSend(sp, DealOpenPayload.ID))
                ServerPlayNetworking.send(sp, new DealOpenPayload(getId(), getDisplayName(), line, dealItem, dealPrice, market, dealMode, dealWant, wantMarket,
                        dealMode == DealOpenPayload.SELL || dealMode == DealOpenPayload.BUY ? persona : -1, patience, maxPatience, askForPayload()));
        } else if (state == State.SIT) {
            NpcLines.speak(this, dev.tcgshop.shop.ShopSeats.talkLine(random)); // 0.9.5: seated, resting
        } else {
            NpcLines.say(this, sp, switch (type()) { case PICKY -> "picky"; case WHALE -> "whale_talk"; case THIEF -> "thief_talk"; default -> "greet"; });
        }
        return ActionResult.SUCCESS;
    }

    /** Owner's answer to a deal. */
    public void answerDeal(ServerPlayerEntity owner, boolean accept) {
        respond(owner, accept ? DealAnswerPayload.ACCEPT : DealAnswerPayload.REJECT_POLITE, 0);
    }

    /** Owner's answer: accept, reject or haggle (only with someone selling you something). */
    public void respond(ServerPlayerEntity owner, int action, long counter) {
        ServerWorld world = owner.getServerWorld();
        ShopZones.Zone z = ShopZones.get(world).byId(zoneId);
        if (state != State.WAIT_DEAL || z == null || !z.owner.equals(owner.getUuid()) || dealItem.isEmpty()) {
            // the deal no longer exists (it left, the item was sold...): close the window instead of leaving it waiting
            sendClosed(owner, Text.translatable("tcgshop.deal.gone"), OUT_UNKNOWN, 0);
            return;
        }
        switch (action) {
            case DealAnswerPayload.ACCEPT -> { acceptDeal(owner); return; }
            case DealAnswerPayload.REJECT_POLITE, DealAnswerPayload.REJECT_RUDE -> { rejectDeal(owner, world, z, action == DealAnswerPayload.REJECT_RUDE); return; }
            default -> {}
        }
        if (persona < 0 || (dealMode != DealOpenPayload.SELL && dealMode != DealOpenPayload.BUY)) {
            updateDeal(owner, NpcLines.random(random, "haggle_offer")); // action not valid here: resend the deal so the window isn't left waiting
            return;
        }
        timer = Math.min(waitMax, timer + 20 * 10); // while you talk, it waits a bit longer
        if (dealMode == DealOpenPayload.BUY) {
            if (action == DealAnswerPayload.INTEREST) thinkBuy(owner, world, z);
            else negotiateBuy(owner, world, z, action, counter);
            return;
        }
        if (action == DealAnswerPayload.INTEREST) { think(owner, world, z); return; }
        negotiate(owner, world, z, action, counter);
    }

    private String pk() { return PERSONAS[Math.max(0, Math.min(PERSONAS.length - 1, persona))]; }

    private void rejectDeal(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z, boolean rude) {
        EconomyConfig c = EconomyConfig.INSTANCE;
        double neg = rude ? c.rejectNegativeChanceRude : c.rejectNegativeChancePolite;
        double[] pm = {1.2, 1.0, 0.8, 0.6, 1.3, 0.8};
        if (persona >= 0) neg *= pm[persona];
        long day = world.getTimeOfDay() / 24000;
        if (z.rejectDay != day) { z.rejectDay = day; z.rejectPenalties = 0; }
        // a neutral review is the norm; a negative one is possible but with a daily cap (no disproportionate penalties)
        boolean bad = z.rejectPenalties < c.maxRejectPenaltiesPerDay && random.nextDouble() < Math.min(1, neg);
        if (bad) {
            z.rejectPenalties++;
            mood = rude ? 1 : 2;
            reviewKey = rude ? "deal_rude" : "deal_no_bad";
            emote(EMOTE_ANGRY);
        } else {
            mood = 3;
            reviewKey = "deal_no_neutral";
        }
        Text line = NpcLines.random(random, rude ? "neg_rude" : bad ? "neg_reject_bad" : "neg_reject_ok");
        NpcLines.speak(this, line);
        closeDeal(owner, line, OUT_REJECT, 0);
        ShopZones.get(world).markDirty();
        leave(world, z, null);
    }

    private void negotiate(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z, int action, long counter) {
        long market = Math.max(1, ShopServer.priceOf(dealItem, 1.0));
        counter = Math.max(1, counter);
        if (counter >= dealPrice) { acceptDeal(owner); return; }
        int pi = Math.max(0, persona);
        double mod = 1;
        net.minecraft.text.MutableText say = Text.empty();
        switch (action) {
            case DealAnswerPayload.ARG_MARKET -> {
                boolean fair = counter >= market * 0.8;
                mod = fair ? new double[]{0.97, 0.98, 0.95, 0.97, 0.98, 0.98}[pi] : 1.05;
                say.append(NpcLines.random(random, fair ? "neg_arg_market_good" : "neg_arg_market_bad")).append(" ");
            }
            case DealAnswerPayload.ARG_ELSEWHERE -> {
                mod = new double[]{1.0, 0.92, 1.06, 1.03, 0.9, 0.96}[pi];
                say.append(NpcLines.random(random, mod < 1 ? "neg_arg_elsewhere_good" : "neg_arg_elsewhere_bad")).append(" ");
            }
            case DealAnswerPayload.ARG_BROKE -> {
                mod = new double[]{0.97, 0.95, 1.02, 1.04, 1.0, 0.95}[pi];
                say.append(NpcLines.random(random, mod < 1 ? "neg_arg_broke_good" : "neg_arg_broke_bad")).append(" ");
            }
            default -> {}
        }
        long eff = Math.round(floorPrice * mod);
        if (counter < floorPrice * 0.6) { // ridiculous offer: takes offense
            patience -= 2;
            mood -= 1;
            say.append(NpcLines.random(random, "neg_insulted_" + pk()));
            if (patience <= 0) { walkAway(owner, world, z, say); return; }
            updateDeal(owner, say);
            return;
        }
        boolean last = action == DealAnswerPayload.ARG_MAX;
        if (counter >= eff || (last && persona == P_DESPERATE && counter >= floorPrice * 0.92 && random.nextBoolean())) {
            dealPrice = counter;
            acceptDeal(owner);
            return;
        }
        if (last) { walkAway(owner, world, z, say.append(NpcLines.random(random, "neg_final_no"))); return; }
        patience--;
        if (patience <= 0) { walkAway(owner, world, z, say.append(NpcLines.random(random, "neg_walkaway_" + pk()))); return; }
        double[] conc = {0.3, 0.5, 0.25, 0.35, 0.6, 0.45};
        long next = Math.max(eff, Math.round(dealPrice - (dealPrice - counter) * conc[pi]));
        if (next >= dealPrice) say.append(NpcLines.random(random, "neg_hold_" + pk(), PriceService.format(dealPrice)));
        else {
            dealPrice = next;
            say.append(NpcLines.random(random, "neg_counter_" + pk(), PriceService.format(dealPrice)));
        }
        updateDeal(owner, say);
    }

    private net.minecraft.text.MutableText buyLine(String cat, Object... args) {
        return Text.translatable("tcgshop.haggle.buy." + cat + "." + random.nextInt(BUY_LINES.getOrDefault(cat, 1)), args);
    }

    /**
     * Haggling with a customer who wants to BUY something from you: you ask for more and they go up (or not) to their maximum, which depends on
     * their personality and the shop's reputation. Arguments: the market, "you won't find it anywhere else" and
     * the shop's reputation (works much better the more reputation you have).
     */
    private void negotiateBuy(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z, int action, long counter) {
        long market = Math.max(1, ShopServer.priceOf(dealItem, 1.0));
        counter = Math.max(1, counter);
        // you ask for what it offers (or less): done. The asking price is NOT lowered here, so a counter below its offer
        // (only possible from a modified client) cannot inflate the recorded tip
        if (counter <= dealPrice) { acceptDeal(owner); return; }
        askPrice = counter; // 1.0: your new asking price (whatever it pays above this is a tip)
        int pi = Math.max(0, Math.min(PERSONAS.length - 1, persona));
        double prem = z.premiumFor(dealItem);
        double mod = 1;
        net.minecraft.text.MutableText say = Text.empty();
        switch (action) {
            case DealAnswerPayload.ARG_MARKET -> {
                boolean fair = counter <= market * (1.1 + Math.max(0, prem));
                mod = fair ? new double[]{1.04, 1.03, 1.05, 1.02, 1.03, 1.03}[pi] : 0.95;
                say.append(buyLine(fair ? "arg_good" : "arg_bad")).append(" ");
            }
            case DealAnswerPayload.ARG_ELSEWHERE -> {
                mod = new double[]{1.06, 1.08, 0.96, 0.98, 1.04, 1.02}[pi];
                say.append(buyLine(mod > 1 ? "arg_good" : "arg_bad")).append(" ");
            }
            case DealAnswerPayload.ARG_BROKE -> { // when selling: "here you pay for the shop's quality"
                mod = prem > 0.02 ? 1 + prem * 0.5 : prem < 0 ? 0.94 : 0.99;
                say.append(buyLine(mod > 1 ? "rep_good" : "rep_bad")).append(" ");
            }
            default -> {}
        }
        long eff = Math.round(ceilPrice * mod);
        if (counter > ceilPrice * 1.6 + 5) { // you ask for an outrageous amount: takes offense
            patience -= 2;
            mood -= 1;
            say.append(buyLine("insulted"));
            if (patience <= 0) { walkAway(owner, world, z, say); return; }
            updateDeal(owner, say);
            return;
        }
        boolean last = action == DealAnswerPayload.ARG_MAX;
        if (counter <= eff || (last && counter <= eff * 1.05 && random.nextBoolean())) {
            dealPrice = counter;
            acceptDeal(owner);
            return;
        }
        if (last) { walkAway(owner, world, z, say.append(buyLine("final_no"))); return; }
        patience--;
        if (patience <= 0) { walkAway(owner, world, z, say.append(buyLine("walkaway"))); return; }
        double[] conc = {0.45, 0.5, 0.3, 0.25, 0.6, 0.45};
        long next = Math.min(eff, Math.round(dealPrice + (counter - dealPrice) * conc[pi]));
        if (next <= dealPrice) say.append(buyLine("hold", PriceService.format(dealPrice)));
        else {
            dealPrice = next;
            say.append(buyLine("counter", PriceService.format(dealPrice)));
        }
        updateDeal(owner, say);
    }

    /** "Let me think about it" with a customer buying from you: sometimes goes up a bit; if you push too much it gets tired. */
    private void thinkBuy(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z) {
        thinks++;
        if (thinks > 2) patience--;
        if (patience <= 0) { walkAway(owner, world, z, buyLine("walkaway")); return; }
        double[] raise = {0.35, 0.45, 0.2, 0.25, 0.6, 0.4};
        if (random.nextDouble() < raise[Math.max(0, Math.min(5, persona))] && dealPrice < ceilPrice) {
            dealPrice = Math.min(ceilPrice, Math.round(dealPrice * (1.03 + random.nextDouble() * 0.05)));
            long tip = currentTip(); // 1.0: now above your asking price: it says the extra is a tip
            updateDeal(owner, tip > 0 ? NpcLines.random(random, "tip_offer", PriceService.format(tip)) : buyLine("think_raise", PriceService.format(dealPrice)));
        } else updateDeal(owner, buyLine("think_same"));
    }

    /** "I'm interested, let me think about it": sometimes goes down a bit; if you push too much it gets tired. */
    private void think(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z) {
        thinks++;
        if (thinks > 2) patience--;
        if (patience <= 0) { walkAway(owner, world, z, NpcLines.random(random, "neg_walkaway_" + pk()).copy()); return; }
        double[] lower = {0.25, 0.5, 0.2, 0.35, 0.7, 0.45};
        if (random.nextDouble() < lower[Math.max(0, persona)] && dealPrice > floorPrice) {
            dealPrice = Math.max(floorPrice, Math.round(dealPrice * (0.92 + random.nextDouble() * 0.05)));
            updateDeal(owner, NpcLines.random(random, "neg_think_lower", PriceService.format(dealPrice)));
        } else updateDeal(owner, NpcLines.random(random, "neg_think_same").copy());
    }

    /** Leaves without a deal because you can't agree (not a rejection from you: neutral review). */
    private void walkAway(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z, Text line) {
        mood = Math.min(mood, 3);
        reviewKey = "deal_walk";
        NpcLines.speak(this, line);
        closeDeal(owner, line, OUT_WALK, 0);
        leave(world, z, null);
    }

    private void updateDeal(ServerPlayerEntity owner, Text line) {
        if (!ServerPlayNetworking.canSend(owner, DealOpenPayload.ID)) return;
        long market = ShopServer.priceOf(dealItem, 1.0);
        ServerPlayNetworking.send(owner, new DealOpenPayload(getId(), getDisplayName(), line, dealItem, dealPrice, market, dealMode, dealWant,
                dealWant.isEmpty() ? 0 : ShopServer.priceOf(dealWant, 1.0), persona, patience, maxPatience, askForPayload()));
    }

    /** Closes the deal window on the client showing the last line. */
    private void closeDeal(ServerPlayerEntity owner, Text line, int outcome, long finalPrice) {
        dealNotified = true;
        sendClosed(owner, line, outcome, finalPrice);
    }

    /** Sends the close to a player without marking the deal as closed (e.g. to someone answering a deal that isn't theirs). */
    private void sendClosed(ServerPlayerEntity owner, Text line, int outcome, long finalPrice) {
        if (!ServerPlayNetworking.canSend(owner, DealOpenPayload.ID)) return;
        // in a closed deal, patience carries the outcome (OUT_*) and price the final price, so the window can show it
        // ask: your asking price, so the window can show the tip included in the final price (BUY deals only)
        ServerPlayNetworking.send(owner, new DealOpenPayload(getId(), getDisplayName(), line, ItemStack.EMPTY, finalPrice, 0, DealOpenPayload.CLOSED,
                ItemStack.EMPTY, 0, persona, outcome, maxPatience, outcome == OUT_DEAL ? askForPayload() : 0));
    }

    private void acceptDeal(ServerPlayerEntity owner) {
        ServerWorld world = owner.getServerWorld();
        ShopZones.Zone z = ShopZones.get(world).byId(zoneId);
        if (state != State.WAIT_DEAL || z == null || !z.owner.equals(owner.getUuid()) || dealItem.isEmpty()) return;
        long market = ShopServer.priceOf(dealItem, 1.0);
        long tip = currentTip(); // 1.0: BUY deal above your asking price
        switch (dealMode) {
            case DealOpenPayload.SELL -> { // you buy from it
                if (!CobbleDollarsBridge.tryCharge(owner, dealPrice)) {
                    owner.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(dealPrice)).formatted(Formatting.RED), true);
                    updateDeal(owner, NpcLines.random(random, "neg_no_money"));
                    return;
                }
                give(owner, dealItem.copy());
                ShopZones.get(world).addCost(owner.getUuid(), dealPrice);
                if (dealPrice < market) CardAdvancements.grant(owner, "deal_bargain");
            }
            case DealOpenPayload.TRADE -> { // you trade your displayed card for theirs
                ShopDisplayBlockEntity be = targetDisplay(world);
                if (be == null || !ItemStack.areItemsAndComponentsEqual(be.getStack(targetSlot), dealWant)) {
                    owner.sendMessage(Text.translatable("tcgshop.deal.gone").formatted(Formatting.GRAY), true);
                    closeDeal(owner, Text.translatable("tcgshop.deal.gone"), OUT_UNKNOWN, 0);
                    leave(world, z, null);
                    return;
                }
                ItemStack got = be.removeStack(targetSlot, 1);
                give(owner, dealItem.copy());
                equipStack(EquipmentSlot.MAINHAND, got);
                setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
                CardAdvancements.grant(owner, "npc_trade");
            }
            default -> { // you sell your product at the price it offers
                ShopDisplayBlockEntity be = targetDisplay(world);
                if (be == null || !ItemStack.areItemsAndComponentsEqual(be.getStack(targetSlot), dealItem)) {
                    owner.sendMessage(Text.translatable("tcgshop.deal.gone").formatted(Formatting.GRAY), true);
                    closeDeal(owner, Text.translatable("tcgshop.deal.gone"), OUT_UNKNOWN, 0);
                    leave(world, z, null);
                    return;
                }
                ItemStack sold = be.getStack(targetSlot).copyWithCount(1);
                ShopServer.sellToNpc(world, z, be, targetSlot, dealPrice);
                equipStack(EquipmentSlot.MAINHAND, sold);
                setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
                if (dealPrice > market) CardAdvancements.grant(owner, "deal_above_market");
                if (tip > 0) { // the tip is already inside dealPrice (paid into the till): only recorded and announced
                    dealTipped = true; // no second, voluntary tip at checkout for this purchase
                    dev.tcgshop.shop.TipLedger.record(world.getServer(), owner.getUuid(), tip, world.getTimeOfDay() / 24000);
                    owner.sendMessage(Text.translatable("tcgshop.tip.received", getDisplayName(),
                            Text.literal(PriceService.format(tip)).formatted(Formatting.GOLD)).formatted(Formatting.YELLOW), false);
                }
            }
        }
        z.dealsAccepted++;
        mood += 2;
        reviewKey = "deal_yes";
        ShopZones.get(world).markDirty();
        CardAdvancements.grant(owner, "first_deal");
        Text done;
        if (persona >= 0 && dealMode == DealOpenPayload.SELL) done = NpcLines.random(random, "neg_accept_" + pk());
        else if (persona >= 0 && dealMode == DealOpenPayload.BUY && tip > 0)
            done = NpcLines.random(random, "tip_accept", PriceService.format(tip)); // 1.0: pays above your price: says it's a tip
        else if (persona >= 0 && dealMode == DealOpenPayload.BUY)
            // pays you above market and happily thanks to the shop's reputation
            done = dealPrice > market * 1.02 && z.premiumFor(dealItem) > 0.01 && random.nextFloat() < 0.75f
                    ? NpcLines.random(random, "overpay_happy") : buyLine("accept");
        else done = NpcLines.random(random, "deal_accepted");
        NpcLines.speak(this, done);
        closeDeal(owner, done, OUT_DEAL, dealPrice);
        dealItem = ItemStack.EMPTY;
        leave(world, z, null);
        ShopServer.syncZones(world.getServer());
    }

    private static void give(ServerPlayerEntity owner, ItemStack st) {
        if (CardInfo.isCard(st) && CollectionService.store(owner, List.of(st.copy()))) return;
        owner.getInventory().offerOrDrop(st);
    }

    @Override
    public String[] battlePool() { return THIEF_POOL; }

    @Override
    public void onBattleEnd(ServerPlayerEntity player, boolean playerWon) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (playerWon) {
            CardAdvancements.grant(player, "battle_win");
            damage(world.getDamageSources().playerAttack(player), 1000f);
        } else {
            ShopZones.Zone z = ShopZones.get(world).byId(zoneId);
            NpcLines.sayNear(this, "thief_escape");
            player.sendMessage(Text.translatable("tcgshop.thief.escaped", getDisplayName(), loot.toHoverableText()).formatted(Formatting.RED), false);
            if (z != null) ShopZones.get(world).markDirty();
            discard();
        }
    }

    /** Normal customers can't be hit (they complain); thieves can. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        // 0.9.5: if something hits it (player, creature, arrow...) while seated or going to sit, it jumps up and leaves
        if (getWorld() instanceof ServerWorld sw && (state == State.SIT || state == State.SIT_GO) && amount > 0
                && (source.getAttacker() != null || source.getSource() != null)) {
            ShopZones.Zone z = zoneId == null ? null : ShopZones.get(sw).byId(zoneId);
            if (state == State.SIT) emote(EMOTE_ANGRY);
            standUp(sw);
            if (z != null) leave(sw, z, null);
        }
        if (type() != Type.THIEF && source.getAttacker() instanceof PlayerEntity p && !p.isCreative()) {
            if (!getWorld().isClient && age % 2 == 0) {
                NpcLines.sayNear(this, "ouch");
                mood -= 2;
                reviewKey = "hit";
            }
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!(getWorld() instanceof ServerWorld world) || loot.isEmpty()) return;
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z != null) {
            returnLoot(world, z);
            z.detained++;
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
            if (owner != null) {
                owner.sendMessage(Text.translatable("tcgshop.thief.recovered", getDisplayName()).formatted(Formatting.GREEN), false);
                CardAdvancements.grant(owner, "catch_thief");
                if (z.detained >= 10) CardAdvancements.grant(owner, "catch_thief_10");
            }
        } else dropStack(loot);
        loot = ItemStack.EMPTY;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    /** 0.9: on despawning or dying, frees its spot in the door queue and its path reservation. */
    @Override
    public void remove(net.minecraft.entity.Entity.RemovalReason reason) {
        if (!getWorld().isClient) {
            dev.tcgshop.shop.ShopLife.forget(getUuid());
            // 0.9.5: releases the chair and removes the invisible seat (no orphaned seats are ever left)
            dev.tcgshop.shop.ShopSeats.release(getUuid());
            if (getVehicle() instanceof ShopSeatEntity seat) {
                stopRiding();
                seat.discard();
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ 0.9.5 seats and furniture

    /**
     * Tries to sit for a while (once per visit, by chance): looks for a free, reachable chair in the shop,
     * reserves it and heads to it. false = doesn't sit (carries on as usual).
     */
    private boolean trySit(ServerWorld world, ShopZones.Zone z, boolean buyer) {
        if (sitTried || !entered || type() == Type.THIEF || !dev.tcgshop.shop.ShopSeats.enabled()) return false;
        if (random.nextDouble() >= (buyer ? dev.tcgshop.shop.ShopSeats.buyerSitChance() : dev.tcgshop.shop.ShopSeats.sitChance())) return false;
        sitTried = true;
        if (hostileNear(world, z)) return false; // with a monster nearby nobody sits (they'd stand up right away)
        dev.tcgshop.shop.ShopSeats.Pick pick = dev.tcgshop.shop.ShopSeats.pickSeat(world, z, this);
        if (pick == null) return false;
        sitSeat = pick.seat();
        sitStand = pick.stand();
        sitBuyer = buyer;
        sitBlocked = 0;
        dataTracker.set(WANTS, false);
        dataTracker.set(WAIT, -1);
        emote(EMOTE_NONE);
        state = State.SIT_GO;
        timer = 20 * 20; // if it doesn't get there in 20 s, it forgets the chair
        getNavigation().startMovingTo(sitStand.x, sitStand.y, sitStand.z, 0.8);
        return true;
    }

    /**
     * On the way to the chair: sits down on reaching it. If it's broken or someone else reserves it, tries another chair (once) or leaves;
     * if someone is just passing over it (a pet, another customer...), waits a bit before giving up.
     */
    private void sitGoTick(ServerWorld world, ShopZones.Zone z) {
        if (sitSeat == null || sitStand == null || !dev.tcgshop.shop.ShopSeats.stillSeat(world, sitSeat.pos())
                || dev.tcgshop.shop.ShopSeats.reservedByOther(world, sitSeat.pos(), getUuid())) {
            sitGiveUp(world, z);
            return;
        }
        boolean free = dev.tcgshop.shop.ShopSeats.isFree(world, sitSeat.pos(), this);
        if (age % 5 == 0) sitBlocked = free ? 0 : sitBlocked + 1;
        if (sitBlocked >= 6) { sitGiveUp(world, z); return; } // ~1.5 s occupied in a row: it's someone else's
        Vec3d c = sitSeat.pos().toCenterPos();
        double dx = c.x - getX(), dz = c.z - getZ();
        if (free && dx * dx + dz * dz < 1.45 * 1.45 && Math.abs(getY() - sitSeat.pos().getY()) < 1.2) { sitDown(world, z); return; }
        if (getNavigation().isIdle() || age % 40 == 0) getNavigation().startMovingTo(sitStand.x, sitStand.y, sitStand.z, 0.8);
        if (timer <= 0) leave(world, z, null);
    }

    /** Lost its chair on the way: tries another free one only once; if there's none, leaves. */
    private void sitGiveUp(ServerWorld world, ShopZones.Zone z) {
        dev.tcgshop.shop.ShopSeats.release(getUuid());
        if (!sitRetried && !hostileNear(world, z)) {
            sitRetried = true;
            dev.tcgshop.shop.ShopSeats.Pick pick = dev.tcgshop.shop.ShopSeats.pickSeat(world, z, this);
            if (pick != null) {
                sitSeat = pick.seat();
                sitStand = pick.stand();
                sitBlocked = 0;
                timer = Math.max(timer, 20 * 12);
                getNavigation().startMovingTo(sitStand.x, sitStand.y, sitStand.z, 0.8);
                return;
            }
        }
        leave(world, z, null);
    }

    private void sitDown(ServerWorld world, ShopZones.Zone z) {
        getNavigation().stop();
        ShopSeatEntity seat = ShopSeatEntity.spawn(world, sitSeat.pos(), sitSeat.seatY(), sitSeat.yaw(), sitStand);
        if (!startRiding(seat, true)) {
            seat.discard();
            leave(world, z, null);
            return;
        }
        state = State.SIT;
        sitSince = world.getTime();
        sitShared = 0;
        timer = 20 * (10 + random.nextInt(31)); // 10-40 s
        emote(sitBuyer ? EMOTE_LOVE : EMOTE_NONE);
        if (random.nextFloat() < 0.65f) NpcLines.speak(this, dev.tcgshop.shop.ShopSeats.sitLine(random, sitBuyer));
        // resting comfortably in a cozy shop leaves a good memory (a little)
        if (!sitBuyer && "browse".equals(reviewKey) && random.nextFloat() < 0.3f) reviewKey = "env_cozy";
        if (!sitBuyer && mood < 4 && random.nextFloat() < 0.2f) mood++;
    }

    /** Seated: rests looking at the shop; stands up when done, on an alarm, a fleeing thief or a nearby monster. */
    private void sitTick(ServerWorld world, ShopZones.Zone z) {
        if (!(getVehicle() instanceof ShopSeatEntity)) { leave(world, z, null); return; } // its chair was taken away
        getNavigation().stop();
        if (age % 20 == 0 && sitDanger(world, z)) {
            emote(EMOTE_ANGRY);
            if (random.nextFloat() < 0.6f) NpcLines.speak(this, dev.tcgshop.shop.ShopSeats.startledLine(random));
            leave(world, z, null);
            return;
        }
        // someone else sat on the same chair (a player, the CobbleFurnies seat...): gives up the spot
        if (age % 20 == 0 && sitSeat != null) {
            sitShared = dev.tcgshop.shop.ShopSeats.isFree(world, sitSeat.pos(), this) ? 0 : sitShared + 1;
            if (sitShared >= 2) {
                if (random.nextFloat() < 0.6f) NpcLines.speak(this, dev.tcgshop.shop.ShopSeats.standLine(random));
                leave(world, z, null);
                return;
            }
        }
        if (timer <= 0) {
            if (random.nextFloat() < 0.45f) NpcLines.speak(this, dev.tcgshop.shop.ShopSeats.standLine(random));
            leave(world, z, null);
        }
    }

    private boolean sitDanger(ServerWorld world, ShopZones.Zone z) {
        if (dev.tcgshop.shop.ShopSeats.alarmSince(z.id, sitSince)) return true;
        if (!world.getEntitiesByClass(CustomerEntity.class, z.box().expand(4), CustomerEntity::isFleeingThief).isEmpty()) return true;
        return hostileNear(world, z);
    }

    /**
     * A scary monster nearby? Only counts if it's inside the shop or in sight (one on the other side of the wall
     * isn't scary), and a scalper only if it's going after someone.
     */
    private boolean hostileNear(ServerWorld world, ShopZones.Zone z) {
        return !world.getEntitiesByClass(net.minecraft.entity.mob.HostileEntity.class, getBoundingBox().expand(7), e -> e.isAlive()
                && (!(e instanceof ScalperEntity) || e.getTarget() != null)
                && (z.contains(e.getBlockPos()) || canSee(e))).isEmpty();
    }

    /** Stands up (if seated) and releases the reserved chair. */
    private void standUp(ServerWorld world) {
        if (getVehicle() instanceof ShopSeatEntity seat) {
            Vec3d st = seat.standPos();
            stopRiding();
            seat.discard();
            if (st != null) requestTeleport(st.x, st.y, st.z);
        }
        dev.tcgshop.shop.ShopSeats.release(getUuid());
        sitSeat = null;
        sitStand = null;
    }

    /** Stops to look at a nearby piece of furniture (CobbleFurnies, Cozyhome...) or plush. */
    private boolean startLook(ServerWorld world, ShopZones.Zone z) {
        furnLooks++;
        BlockPos p = dev.tcgshop.shop.ShopSeats.randomDecorNear(world, z, getBlockPos(), 12, random);
        if (p == null) return false;
        lookPos = p;
        lookPlush = dev.tcgshop.shop.ShopSeats.isPlushAt(world, p);
        lookArrived = false;
        state = State.LOOK;
        timer = 20 * 8; // time to get there
        getNavigation().startMovingTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.8);
        return true;
    }

    private void lookTick(ServerWorld world, ShopZones.Zone z) {
        if (lookPos == null || world.getBlockState(lookPos).isAir()) { state = State.ENTER; return; }
        Vec3d c = lookPos.toCenterPos();
        double dx = c.x - getX(), dz = c.z - getZ(), d2 = dx * dx + dz * dz;
        if (!lookArrived) {
            if (d2 < 2.4 * 2.4 || (getNavigation().isIdle() && d2 < 4.5 * 4.5)) {
                lookArrived = true;
                getNavigation().stop();
                timer = 50 + random.nextInt(70); // 2.5-6 s looking at it
                emote(lookPlush ? EMOTE_LOVE : EMOTE_THINK);
                if (random.nextFloat() < 0.5f) NpcLines.speak(this, dev.tcgshop.shop.ShopSeats.lookLine(random, lookPlush));
                if (lookPlush) world.spawnParticles(ParticleTypes.HEART, c.x, c.y + 0.6, c.z, 2, 0.2, 0.15, 0.2, 0);
            } else {
                if (getNavigation().isIdle() || age % 40 == 0) getNavigation().startMovingTo(c.x, lookPos.getY(), c.z, 0.8);
                if (timer <= 0) { state = State.ENTER; emote(EMOTE_NONE); } // can't get there: keeps browsing products
                return;
            }
        }
        getLookControl().lookAt(c.x, c.y, c.z);
        if (timer <= 0) {
            emote(EMOTE_NONE);
            lookPos = null;
            state = State.ENTER;
        }
    }

    /**
     * Seated on an invisible seat: the feet sit right at seat height (without the vanilla mount offset).
     * On the server the seat is a ShopSeatEntity; the client only sees a vanilla armor stand, so there it's recognized by
     * its synced flags (marker + invisible + no gravity). A shop customer only rides those.
     */
    @Override
    public Vec3d getVehicleAttachmentPos(net.minecraft.entity.Entity vehicle) {
        if (vehicle instanceof ShopSeatEntity || (vehicle instanceof net.minecraft.entity.decoration.ArmorStandEntity a
                && a.isMarker() && a.isInvisible() && a.hasNoGravity()))
            return vehicle.getPassengerRidingPos(this).subtract(vehicle.getPos());
        return super.getVehicleAttachmentPos(vehicle);
    }

    /** The seat isn't saved: when seated, the customer is saved alone (as if standing next to it). */
    @Override
    public boolean saveNbt(NbtCompound nbt) {
        if (getVehicle() instanceof ShopSeatEntity) return saveSelfNbt(nbt);
        return super.saveNbt(nbt);
    }

    /** 0.9 events: its door is blocked and another is open: enters through that one (ShopLife.queueGate). */
    public void redirectDoor(BlockPos door, Vec3d outside) {
        entryDoor = door;
        entryOutside = outside;
        getNavigation().stop();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (zoneId != null) nbt.putUuid("Zone", zoneId);
        if (!loot.isEmpty()) nbt.put("Loot", loot.encode(getRegistryManager()));
        if (!bought.isEmpty()) nbt.put("Bought", bought.encode(getRegistryManager()));
        nbt.putLong("PayPrice", payPrice);
        nbt.putInt("Type", dataTracker.get(TYPE));
        // 0.9.5: when seated it's saved at the chair's position: on load it appears standing next to it
        if (getVehicle() instanceof ShopSeatEntity seat && seat.standPos() != null) {
            Vec3d st = seat.standPos();
            nbt.putDouble("SitStandX", st.x);
            nbt.putDouble("SitStandY", st.y);
            nbt.putDouble("SitStandZ", st.z);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Zone")) zoneId = nbt.getUuid("Zone");
        dataTracker.set(TYPE, nbt.getInt("Type"));
        loot = nbt.contains("Loot") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Loot")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        bought = nbt.contains("Bought") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Bought")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        payPrice = nbt.getLong("PayPrice");
        if (nbt.contains("SitStandY")) setPosition(nbt.getDouble("SitStandX"), nbt.getDouble("SitStandY"), nbt.getDouble("SitStandZ"));
        exit = null; // on reload: walks away from wherever it is
        reviewed = true;
        dev.tcgshop.shop.ShopLife.forget(getUuid()); // 0.9: whatever it had left in the door queue is no longer valid
        // on reload: the thief keeps fleeing with its loot; whoever was going to pay pays; the rest leave
        if (!loot.isEmpty()) { state = State.FLEE; fleePhaseTicks = 40; }
        else if (!bought.isEmpty()) { joinQueue(); register = null; } // no register: will pay on recalculation (pay)
        else state = State.LEAVE;
    }
}
