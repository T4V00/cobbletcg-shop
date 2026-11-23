package dev.tcgshop.entity;

import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.ptcg.PtcgDuels;
import dev.tcgshop.ptcg.PtcgEngine;
import dev.tcgshop.shop.ShopSeats;
import dev.tcgshop.shop.TcgBlocks;
import dev.tcgshop.world.duelist.WanderingDuelists;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 1.0 · The Wandering Duelist (world/duelist/WanderingDuelists): a pro TCG player who sets up a play table and two
 * chairs on natural ground near a player and sits there waiting for a challenger.
 * <ul>
 *   <li>Right-click him (or his table): a window shows the stakes (win = {@code duelistPrize} + the stake back; lose =
 *   the stake: all the cash you carry up to {@code duelistMaxTake}, or one random inventory stack if you carry none)
 *   with Accept / Decline.</li>
 *   <li>Accept: the stake is held in escrow right away (WanderingDuelists.holdStake) and a real-TCG match against the
 *   AI at Hard level starts at his table, shown with his name (PtcgDuels.startNpcMatch with a hook). Walking away or
 *   disconnecting counts as a loss. Going back to waiting or being removed without a result refunds the stake.</li>
 *   <li>One match per encounter: win or lose, he says a line, packs up (table and chairs are removed, with particles)
 *   and vanishes. WanderingDuelists applies the stakes and sends the phone message.</li>
 * </ul>
 * His blocks are protected while he is there and removed when he leaves (only if they are still the blocks he placed).
 * Invulnerable, never despawns on his own; leaves after a while if nobody plays.
 */
public class WanderingDuelistEntity extends PathAwareEntity implements TcgNpc {
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(WanderingDuelistEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EMOTE = DataTracker.registerData(WanderingDuelistEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WAITING = DataTracker.registerData(WanderingDuelistEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> TITLE = DataTracker.registerData(WanderingDuelistEntity.class, TrackedDataHandlerRegistry.STRING);

    /** States (not saved: a restart always comes back to IDLE, or to leaving if the match had already started). */
    private static final int IDLE = 0, OFFER = 1, START = 2, MATCH = 3;
    /** Loaded ticks he waits for a challenger before packing up (a Minecraft day). */
    private static final int LIFE = 24_000;
    /** Loaded ticks with nobody around (64 blocks) before he quietly leaves. */
    private static final int ABSENT_MAX = 2_400;
    /** Ticks between the farewell line and vanishing. */
    private static final int LEAVE_DELAY = 100;
    /** AI level: hard. */
    public static final int LEVEL = 2;

    @Nullable private BlockPos table, chairHis, chairTheirs;
    private String chairBlock = "";
    private int state = IDLE;
    @Nullable private UUID challenger;
    private long stateAt, lastStartTry = -1000, firstStartOk = -1;
    private boolean dueled, registered, cleaned;
    private int life = LIFE, absent, leaveTicks = -1, ambientIn = 400, matchOverWait, arriveIn = -1;
    private final Set<UUID> greeted = new HashSet<>();

    public WanderingDuelistEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, 0);
        builder.add(EMOTE, EMOTE_NONE);
        builder.add(WAITING, true);
        builder.add(TITLE, "");
    }

    @Override
    protected void initGoals() {} // seated: WanderingDuelists drives everything from tick()

    /** role "duelist.trader": label tcgshop.role.duelist.trader, the collector's vest skin (NpcSkins keys on the last part). */
    @Override public String roleKey() { return "tcgshop.role.duelist.trader"; }
    @Override public String npcTitle() { return dataTracker.get(TITLE); }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return dataTracker.get(EMOTE); }
    @Override public boolean wantsAttention() { return dataTracker.get(WAITING); }

    /** Called by WanderingDuelists right after building his table and chairs (before spawning). */
    public void setup(BlockPos table, BlockPos chairHis, BlockPos chairTheirs, String chairBlock) {
        this.table = table.toImmutable();
        this.chairHis = chairHis.toImmutable();
        this.chairTheirs = chairTheirs.toImmutable();
        this.chairBlock = chairBlock;
        setInvulnerable(true);
        setPersistent();
        setCustomName(Text.literal(NpcIdentity.randomName(random)));
        setCustomNameVisible(true);
        dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
        dataTracker.set(TITLE, NpcIdentity.randomTitle(random, 0.6f));
        arriveIn = 20; // once players track him
    }

    @Nullable public BlockPos table() { return table; }
    @Nullable public BlockPos chairHis() { return chairHis; }
    @Nullable public BlockPos chairTheirs() { return chairTheirs; }
    public String chairBlock() { return chairBlock; }

    private void line(String cat) {
        NpcLines.speak(this, WanderingDuelists.line(random, cat));
    }

    private void setState(int s) {
        state = s;
        stateAt = getWorld().getTime();
        if (s == IDLE) {
            challenger = null;
            releaseStakes(); // back to waiting without a result (start failed, challenger gone...): the stake comes back
        }
        dataTracker.set(WAITING, s == IDLE && !dueled && leaveTicks < 0);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        if (table == null || chairHis == null || chairTheirs == null) { // spawned by hand (summon/egg): nothing to manage
            discard();
            return;
        }
        if (!registered) {
            registered = true;
            WanderingDuelists.protect(w, this);
            if (dueled && leaveTicks < 0) {
                if (PtcgDuels.tableMatchRunning(table)) { // only his chunk reloaded: the match goes on, the hook settles it
                    state = MATCH;
                    stateAt = w.getTime();
                    matchOverWait = 0;
                } else leave("draw"); // a restart interrupted his match: the stake comes back (leave → vanish), he just leaves
            } else if (!dueled) releaseStakes(); // reloaded while starting (states are not saved): back to waiting, stake back
        }
        if (leaveTicks >= 0) {
            if (leaveTicks > 0) leaveTicks--;
            if (leaveTicks == 0) vanish(w);
            return;
        }
        if (age % 5 != 0) return;
        if (!w.getBlockState(table).isOf(TcgBlocks.PLAY_TABLE)) { // his table is gone (an operator broke it, an explosion...)
            WanderingDuelists.collectDrops(w, table, chairBlock); // he takes the dropped table/chairs with him
            vanish(w);
            return;
        }
        if (!hasVehicle() && age % 20 == 0) sit(w);
        if (arriveIn > 0 && (arriveIn -= 5) <= 0) line("arrive");
        ServerPlayerEntity ch = challengerPlayer();
        ServerPlayerEntity near = ch != null ? ch : (ServerPlayerEntity) w.getClosestPlayer(getX(), getY(), getZ(), 16, e -> !e.isSpectator());
        if (near != null) getLookControl().lookAt(near, 30f, 30f);
        // nobody around for a while: he quietly packs up
        if (w.getClosestPlayer(getX(), getY(), getZ(), 64, e -> !e.isSpectator()) == null) {
            if ((absent += 5) >= ABSENT_MAX && state == IDLE) { vanish(w); return; }
        } else absent = 0;
        switch (state) {
            case IDLE -> idle(w);
            case OFFER -> {
                if (ch == null || ch.squaredDistanceTo(this) > 16 * 16 || w.getTime() - stateAt > 60 * 20) setState(IDLE);
            }
            case START -> starting(w, ch);
            case MATCH -> {
                // the result arrives through the hook (WanderingDuelists.onResult); this only covers a lost hook
                if (!PtcgDuels.tableMatchRunning(table) && (matchOverWait += 5) > 30 * 20) leave("draw");
            }
            default -> setState(IDLE);
        }
    }

    private void idle(ServerWorld w) {
        // a match he had given up on started late after all: never pack up (and end it) while it runs
        if (!dueled && table != null && PtcgDuels.tableMatchRunning(table)) {
            setState(MATCH);
            dueled = true;
            matchOverWait = 0;
            return;
        }
        if (dueled) return;
        for (ServerPlayerEntity p : w.getPlayers(p -> !p.isSpectator() && p.squaredDistanceTo(this) < 10 * 10)) {
            if (greeted.add(p.getUuid())) {
                line("greet");
                PtcgDuels.warmUp(); // the engine starts loading while the player walks over
                break;
            }
        }
        if ((ambientIn -= 5) <= 0) {
            ambientIn = 600 + random.nextInt(400);
            if (w.getClosestPlayer(getX(), getY(), getZ(), 16, e -> !e.isSpectator()) != null) {
                line("ambient");
                swingHand(Hand.MAIN_HAND);
                w.playSound(null, table, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.NEUTRAL, 0.6f, 1.1f);
            }
        }
        if ((life -= 5) <= 0) leave("bored");
    }

    private void starting(ServerWorld w, @Nullable ServerPlayerEntity p) {
        long now = w.getTime();
        if (p != null && PtcgDuels.tableMatchRunning(table) && PtcgDuels.busy(p.getUuid())) {
            setState(MATCH);
            dueled = true;
            matchOverWait = 0;
            dataTracker.set(WAITING, false);
            seatPlayer(w, p);
            return;
        }
        if (p == null || p.getServerWorld() != w || p.squaredDistanceTo(table.toCenterPos()) > 20 * 20) { setState(IDLE); return; }
        if (firstStartOk < 0) {
            if (PtcgEngine.failed()) { failStart(p); return; } // the first try already explained it in chat
            if (now - lastStartTry >= 40) tryStart(p); // engine still loading: retry every 2 s
            if (now - stateAt > 40 * 20) failStart(p);
        } else if (now - firstStartOk > 60 * 20 && (!PtcgDuels.busy(p.getUuid()) || now - firstStartOk > 180 * 20))
            failStart(p); // never started (the hook normally says so sooner; a slow deck build is not cut short)
    }

    @Nullable
    private ServerPlayerEntity challengerPlayer() {
        if (challenger == null || getServer() == null) return null;
        ServerPlayerEntity p = getServer().getPlayerManager().getPlayer(challenger);
        return p != null && p.getWorld() == getWorld() && !p.isSpectator() ? p : null;
    }

    // ------------------------------------------------------------------ sitting

    /**
     * Sits on his chair facing the table. Unlike a customer's ShopSeatEntity (never saved, and a passenger is only
     * saved through its vehicle), his seat is a plain invisible marker armor stand that IS saved, so he is still there,
     * seated, after the chunk unloads or the server restarts. It is tagged and removed with him.
     */
    private void sit(ServerWorld w) {
        if (chairHis == null || table == null || !ShopSeats.stillSeat(w, chairHis)) return;
        for (ArmorStandEntity old : w.getEntitiesByClass(ArmorStandEntity.class, new Box(chairHis).expand(0.5),
                a -> a.getCommandTags().contains(WanderingDuelists.SEAT_TAG) && !a.hasPassengers()))
            old.discard(); // a stray seat of his (should not happen)
        float yaw = WanderingDuelists.yawToward(chairHis, table);
        ShopSeats.Seat seat = ShopSeats.seatFacing(w, chairHis, yaw);
        ArmorStandEntity e = new ArmorStandEntity(w, chairHis.getX() + 0.5, seat.seatY(), chairHis.getZ() + 0.5);
        e.setInvisible(true);
        e.setNoGravity(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.getDataTracker().set(ArmorStandEntity.ARMOR_STAND_FLAGS,
                (byte) (e.getDataTracker().get(ArmorStandEntity.ARMOR_STAND_FLAGS) | ArmorStandEntity.MARKER_FLAG));
        e.addCommandTag(ShopSeatEntity.TAG); // admins' /kill filters already exclude the mod's seats
        e.addCommandTag(WanderingDuelists.SEAT_TAG);
        e.refreshPositionAndAngles(chairHis.getX() + 0.5, seat.seatY(), chairHis.getZ() + 0.5, yaw, 0f);
        e.setBodyYaw(yaw);
        e.setHeadYaw(yaw);
        if (!w.spawnEntity(e)) return;
        if (!startRiding(e, true)) e.discard();
        else {
            setYaw(yaw);
            setBodyYaw(yaw);
            setHeadYaw(yaw);
        }
    }

    /** Public for WanderingDuelists: sits right after spawning. */
    public void sitNow() {
        if (getWorld() instanceof ServerWorld w) sit(w);
    }

    /** When the match starts the challenger takes the other chair (if they are close and it is free). Shift gets up. */
    private void seatPlayer(ServerWorld w, ServerPlayerEntity p) {
        try {
            if (p.hasVehicle() || chairTheirs == null || p.squaredDistanceTo(chairTheirs.toCenterPos()) > 6 * 6) return;
            if (!ShopSeats.stillSeat(w, chairTheirs) || !ShopSeats.isFree(w, chairTheirs, p)) return;
            float yaw = WanderingDuelists.yawToward(chairTheirs, table);
            ShopSeats.Seat seat = ShopSeats.seatFacing(w, chairTheirs, yaw);
            ShopSeatEntity e = ShopSeatEntity.spawn(w, chairTheirs, seat.seatY(), yaw, WanderingDuelists.standSpot(table, chairTheirs));
            if (!p.startRiding(e, true)) e.discard();
        } catch (RuntimeException ex) {
            dev.tcgshop.TcgShop.LOGGER.debug("Could not seat the challenger: {}", ex.toString());
        }
    }

    // ------------------------------------------------------------------ challenge

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp) clicked(sp);
        return ActionResult.success(getWorld().isClient);
    }

    /** Right-click on him or on his table (PtcgDuels.useTable → WanderingDuelists.interceptTable). */
    public void clicked(ServerPlayerEntity sp) {
        if (leaveTicks >= 0 || table == null) return;
        if (dueled) {
            sp.sendMessage(Text.translatable("tcgshop.duelist.already").formatted(Formatting.GRAY), true);
            return;
        }
        if (state != IDLE && !sp.getUuid().equals(challenger)) {
            line("busy");
            return;
        }
        if (state == START || state == MATCH) return; // starting: PtcgDuels shows the match when it is ready
        if (!CobbleDollarsBridge.isAvailable()) {
            sp.sendMessage(Text.translatable("tcgshop.duelist.no_bank").formatted(Formatting.RED), false);
            return;
        }
        if (sp.squaredDistanceTo(this) > 8 * 8) {
            sp.sendMessage(Text.translatable("tcgshop.duelist.too_far").formatted(Formatting.GRAY), true);
            return;
        }
        if (PtcgDuels.busy(sp.getUuid())) {
            sp.sendMessage(Text.translatable("tcgshop.ptcg.challenge.busy_self").formatted(Formatting.RED), true);
            return;
        }
        boolean fresh = state != OFFER;
        setState(OFFER);
        challenger = sp.getUuid();
        dataTracker.set(WAITING, false);
        if (fresh) line("offer");
        PtcgDuels.warmUp();
        openStakes(sp);
    }

    /** Sends the stakes window (or, for a client without the add-on, the stakes in chat with clickable answers). */
    private void openStakes(ServerPlayerEntity sp) {
        long prize = WanderingDuelists.prize(), cap = WanderingDuelists.maxTake();
        long cash = Math.max(0, CobbleDollarsBridge.getBalance(sp));
        NbtCompound d = new NbtCompound();
        d.putInt("id", getId());
        d.putString("name", getName().getString());
        d.putLong("prize", prize);
        d.putLong("cap", cap);
        d.putLong("cash", cash);
        d.putLong("take", Math.min(cash, cap));
        d.putLong("min", WanderingDuelists.minStake());
        if (ServerPlayNetworking.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID)) {
            ServerPlayNetworking.send(sp, new dev.tcgshop.network.UiPayloads.Open("duelist", d));
            return;
        }
        String money = dev.tcgshop.pricing.PriceService.format(prize), capS = dev.tcgshop.pricing.PriceService.format(cap);
        sp.sendMessage(Text.translatable("tcgshop.duelist.chat.header", getDisplayName()).formatted(Formatting.GOLD), false);
        sp.sendMessage(Text.translatable("tcgshop.duelist.ui.win", money).formatted(Formatting.GREEN), false);
        sp.sendMessage(Text.translatable("tcgshop.duelist.ui.lose", capS).formatted(Formatting.RED), false);
        sp.sendMessage(Text.translatable("tcgshop.duelist.ui.lose_item").formatted(Formatting.RED), false);
        long min = WanderingDuelists.minStake();
        if (cash > 0 && cash < min)
            sp.sendMessage(Text.translatable("tcgshop.duelist.ui.lose_short", dev.tcgshop.pricing.PriceService.format(cash),
                    dev.tcgshop.pricing.PriceService.format(min)).formatted(Formatting.GRAY), false);
        Text accept = Text.translatable("tcgshop.duelist.ui.accept").styled(st -> st.withColor(Formatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgduelist accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("tcgshop.duelist.chat.accept_hint"))));
        Text decline = Text.translatable("tcgshop.duelist.ui.decline").styled(st -> st.withColor(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgduelist decline")));
        sp.sendMessage(Text.literal("   [").append(accept).append(Text.literal("]   [")).append(decline).append(Text.literal("]")), false);
        sp.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
    }

    /** Is this player's stakes window open (for /tcgduelist accept|decline)? */
    public boolean offeredTo(UUID player) {
        return state == OFFER && player.equals(challenger) && leaveTicks < 0;
    }

    /** The answer from the stakes window ("accept" / "decline") or the chat command. Revalidated here. */
    public void answer(ServerPlayerEntity p, String action) {
        if (!isAlive() || leaveTicks >= 0 || dueled || state != OFFER || !p.getUuid().equals(challenger) || p.squaredDistanceTo(this) > 10 * 10) {
            if ("accept".equals(action)) p.sendMessage(Text.translatable("tcgshop.duelist.none").formatted(Formatting.GRAY), true);
            return;
        }
        if ("decline".equals(action)) {
            line("decline");
            setState(IDLE);
            return;
        }
        if (!"accept".equals(action)) return;
        if (PtcgDuels.busy(p.getUuid())) {
            p.sendMessage(Text.translatable("tcgshop.ptcg.challenge.busy_self").formatted(Formatting.RED), true);
            return;
        }
        if (!WanderingDuelists.holdStake(p, this)) return; // the stake is held now (escrow), before the match starts
        setState(START);
        challenger = p.getUuid();
        firstStartOk = -1;
        line("accept");
        p.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.9f, 0.8f);
        tryStart(p);
    }

    private void tryStart(ServerPlayerEntity p) {
        lastStartTry = getWorld().getTime();
        if (table == null) return;
        boolean ok = PtcgDuels.startNpcMatch(p, table, getName().getString(), LEVEL, WanderingDuelists.prize(), WanderingDuelists.hook(this));
        if (ok && firstStartOk < 0) firstStartOk = getWorld().getTime();
    }

    private void failStart(ServerPlayerEntity p) {
        p.sendMessage(Text.translatable("tcgshop.duelist.start_failed").formatted(Formatting.GRAY), false);
        line("deck_fail");
        setState(IDLE);
    }

    // ------------------------------------------------------------------ results (WanderingDuelists.onResult)

    /** The match could not start (deck refused, table taken...): back to waiting; the player may try again. */
    public void startFailed() {
        // MATCH too: the match may have been registered (and seen here) before the engine refused to create it
        if ((state != START && state != MATCH) || leaveTicks >= 0) return;
        line("deck_fail");
        firstStartOk = -1;
        dueled = false;
        setState(IDLE);
    }

    /**
     * The match ended and the stakes were settled. kind: "win" (the player won), "lose_cash", "lose_item",
     * "lose_nothing", "runaway" (lost while offline) or "draw". He says it and packs up.
     */
    public void duelOver(String kind) {
        dueled = true;
        if (kind.startsWith("lose") || kind.equals("runaway")) dataTracker.set(EMOTE, EMOTE_MONEY);
        else if (kind.equals("win")) {
            dataTracker.set(EMOTE, EMOTE_NONE);
            if (getWorld() instanceof ServerWorld w)
                w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.6, getZ(), 8, 0.3, 0.3, 0.3, 0);
        }
        leave(kind);
    }

    /** Says the farewell line of that category and vanishes a few seconds later (with his table and chairs). */
    private void leave(String cat) {
        if (leaveTicks >= 0) return;
        line(cat);
        leaveTicks = LEAVE_DELAY;
        dataTracker.set(WAITING, false);
    }

    /** Packs up: his table and chairs disappear (only if they are still his blocks), with particles, and so does he. */
    private void vanish(ServerWorld w) {
        cleanup(w);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 24, 0.4, 0.8, 0.4, 0.02);
        w.spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 16, 0.4, 0.6, 0.4, 0.04);
        w.playSound(null, getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.NEUTRAL, 0.8f, 1.2f);
        discard();
    }

    /**
     * Refunds any stake this encounter still holds in escrow (each stake is settled once: a no-op after a result).
     * Never while a match runs at his table: its hook settles that stake (even with him gone), so blowing up his table
     * or a late start cannot turn a losing match into a refund.
     */
    private void releaseStakes() {
        if (getServer() == null || (table != null && PtcgDuels.tableMatchRunning(table))) return;
        WanderingDuelists.releaseEncounter(getServer(), getUuid());
    }

    private void cleanup(ServerWorld w) {
        if (cleaned || table == null) return;
        cleaned = true;
        Entity v = getVehicle();
        if (v != null) {
            stopRiding();
            v.discard();
        }
        WanderingDuelists.removeBlocks(w, table, chairHis, chairTheirs, chairBlock);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (getWorld() instanceof ServerWorld w) {
            // killed by command or discarded: never leave his blocks behind; unloading keeps them (he comes back with the chunk)
            if (reason.shouldDestroy()) {
                cleanup(w);
                releaseStakes(); // the encounter is gone: a stake it still holds (no result yet) comes back
            }
            if (registered) WanderingDuelists.unprotect(w, this);
            registered = false;
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ misc

    @Override public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override public boolean isPushable() { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (table != null) nbt.putLong("Table", table.asLong());
        if (chairHis != null) nbt.putLong("ChairHis", chairHis.asLong());
        if (chairTheirs != null) nbt.putLong("ChairTheirs", chairTheirs.asLong());
        nbt.putString("ChairBlock", chairBlock);
        nbt.putBoolean("Dueled", dueled || state == MATCH);
        nbt.putInt("Life", life);
        nbt.putInt("Leave", leaveTicks);
        nbt.putInt("NpcSkin", npcSkin());
        nbt.putString("NpcTitle", npcTitle());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        table = nbt.contains("Table") ? BlockPos.fromLong(nbt.getLong("Table")) : null;
        chairHis = nbt.contains("ChairHis") ? BlockPos.fromLong(nbt.getLong("ChairHis")) : null;
        chairTheirs = nbt.contains("ChairTheirs") ? BlockPos.fromLong(nbt.getLong("ChairTheirs")) : null;
        chairBlock = nbt.getString("ChairBlock");
        dueled = nbt.getBoolean("Dueled");
        if (nbt.contains("Life")) life = nbt.getInt("Life");
        leaveTicks = nbt.contains("Leave") ? nbt.getInt("Leave") : -1;
        dataTracker.set(SKIN, nbt.getInt("NpcSkin"));
        dataTracker.set(TITLE, nbt.getString("NpcTitle"));
        dataTracker.set(WAITING, !dueled && leaveTicks < 0);
    }
}
