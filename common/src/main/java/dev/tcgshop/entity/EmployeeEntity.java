package dev.tcgshop.entity;

import dev.tcgshop.shop.CashierUpgrades;
import dev.tcgshop.shop.GuardUpgrades;
import dev.tcgshop.shop.RegisterSpots;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Employee hired from the phone:
 *  · Cashier: runs the cash register. Without a cashier, the shop cannot sell packs, boxes or mystery boxes.
 *  · Guard: patrols the shop, chases thieves and recovers stolen goods (metrics: detained and cards recovered).
 *    1.0 beta 3: wears the shop's upgrades (GuardUpgrades: armor, weapon, training, detection), fights armed robbers and
 *    heist crews (ThiefTiers) and can fall doing it: then the shop has no guard until you hire a new one. The owner
 *    sneak + right-clicks it for the upgrade menu.
 *  1.1 (staff update): cashiers walk up to customers waiting in the queue and ring them up where they stand (Range and
 *  Speed upgrades, CashierUpgrades), and with the top upgrade close haggling customers themselves. Up to three guards
 *  (slot 0-2), who split up when chasing and never wander far from the shop.
 */
public class EmployeeEntity extends PathfinderMob implements TcgNpc {
    private static final EntityDataAccessor<String> TITLE = SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GUARD = SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.INT);

    private static final String[] GUARD_POOL = {"growlithe", "arcanine", "lucario", "riolu", "machoke", "herdier", "rockruff", "boldore"};
    private static final String[] THIEF_DUEL_POOL = {"purrloin", "nickit", "sneasel", "murkrow", "zorua", "meowth", "poochyena"};

    private UUID zoneId;
    private BlockPos home = BlockPos.ZERO;
    private int talkCooldown;
    /** 1.0 beta 3: signature of the upgrade levels last applied (-1 = not yet), swing cooldown, grab cooldown after a slip. */
    private int appliedSig = -1, attackCd, grabCd, combatTicks;
    /** 1.0 beta 3: has already warned about the masked robber it is tailing. */
    private boolean tailWarned;

    public EmployeeEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0); // 1.0 beta 3: guards fight armed thieves (+ weapon upgrade)
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TITLE, "");
        builder.define(SKIN, 0);
        builder.define(GUARD, false);
        builder.define(EMOTE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, CustomerEntity.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public boolean isGuard() { return entityData.get(GUARD); }
    public UUID zoneId() { return zoneId; }

    @Override public String roleKey() { return isGuard() ? "tcgshop.role.guard" : "tcgshop.role.cashier"; }
    @Override public String npcTitle() { return entityData.get(TITLE); }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return entityData.get(EMOTE); }

    public void setEmote(int e) { entityData.set(EMOTE, e); }

    // ------------------------------------------------------------------ hire / fire

    private int slot;

    public int slot() { return slot; }

    public static void spawnFor(ServerLevel world, ShopZones.Zone z, boolean cashier) {
        spawnFor(world, z, cashier, 0);
    }

    /**
     * slot: cashiers = register post (0 = first cash register, 1 = the second closest); guards = guard slot 0-2 (1.1: up to
     * three guards, see ShopZones.Zone.guardCount).
     */
    public static void spawnFor(ServerLevel world, ShopZones.Zone z, boolean cashier, int slot) {
        EmployeeEntity e = TcgEntities.EMPLOYEE.create(world);
        if (e == null) return;
        e.zoneId = z.id;
        e.slot = slot;
        e.entityData.set(GUARD, !cashier);
        e.entityData.set(SKIN, NpcIdentity.randomSkin(world.random));
        e.entityData.set(TITLE, NpcIdentity.randomTitle(world.random, 0.35f));
        e.setCustomName(Component.literal(cashier ? (slot == 1 ? z.cashier2Name : z.cashierName) : z.guardNameOf(slot)));
        e.setCustomNameVisible(true);
        BlockPos home = cashier ? cashierHome(world, z, slot) : guardHome(world, z, slot);
        e.home = home.immutable();
        e.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
        world.addFreshEntity(e);
    }

    /** The register a cashier of `slot` works at (the slot-th closest to the shop block), or null. */
    @org.jetbrains.annotations.Nullable
    private static BlockPos registerOf(ServerLevel world, ShopZones.Zone z, int slot) {
        int seen = 0;
        for (BlockPos p : BlockPos.withinManhattan(z.core, z.size() / 2, 4, z.size() / 2))
            if (z.contains(p) && world.getBlockState(p).is(TcgBlocks.CASH_REGISTER) && seen++ == slot) return p.immutable();
        return null;
    }

    /** 1.1: behind its register on the floor (RegisterSpots: the side away from the customers, even on a counter). */
    private static BlockPos cashierHome(ServerLevel world, ShopZones.Zone z, int slot) {
        BlockPos reg = registerOf(world, z, slot);
        return reg != null ? RegisterSpots.cashierPost(world, reg) : z.core.above();
    }

    /** Guards start next to the shop block, a couple of blocks apart, on the floor. */
    private static BlockPos guardHome(ServerLevel world, ShopZones.Zone z, int slot) {
        BlockPos base = z.core.above().relative(slot == 1 ? Direction.EAST : Direction.WEST, slot == 0 ? 0 : 2);
        BlockPos f = RegisterSpots.floorNear(world, base);
        return f != null ? f : z.core.above();
    }

    public static void removeFor(ServerLevel world, ShopZones.Zone z, boolean cashier) {
        removeFor(world, z, cashier, -1);
    }

    /** slot -1 = all of that role. */
    public static void removeFor(ServerLevel world, ShopZones.Zone z, boolean cashier, int slot) {
        List<EmployeeEntity> list = world.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(16),
                e -> z.id.equals(e.zoneId) && e.isGuard() == !cashier && (slot < 0 || e.slot == slot));
        for (EmployeeEntity e : list) e.discard();
    }

    /** 1.1: the guard of slot `removed` is gone: the (loaded) guards after it move down one slot and take their new name. */
    public static void shiftGuardSlots(ServerLevel world, ShopZones.Zone z, int removed) {
        for (EmployeeEntity e : world.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(64), e -> z.id.equals(e.zoneId) && e.isGuard() && e.slot > removed)) {
            e.slot--;
            e.setCustomName(Component.literal(z.guardNameOf(e.slot)));
        }
    }

    /** 1.1: spawns every hired guard slot that has no guard around (see ShopServer.respawnMissingGuard for the safety checks). */
    public static void spawnMissingGuards(ServerLevel world, ShopZones.Zone z) {
        List<EmployeeEntity> present = world.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(48), e -> z.id.equals(e.zoneId) && e.isGuard() && e.isAlive());
        for (int s = 0; s < z.guardCount(); s++) {
            final int sl = s;
            if (present.stream().noneMatch(e -> e.slot == sl)) spawnFor(world, z, false, s);
        }
    }

    // ------------------------------------------------------------------ behavior

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 10 != 0) return;
        // 1.1 fix: a Pokémon duel / battle freezes the NPC with NoAI; when the world was saved mid-duel that flag stayed on
        // forever (it is saved with the entity) and the employee stood still for good while its logic kept "chasing".
        if (isNoAi() && !dev.tcgshop.compat.NpcPokemonDuel.busy(this) && !dev.tcgshop.compat.CobblemonBattles.isFighting(this)) setNoAi(false);
        if (dev.tcgshop.compat.NpcPokemonDuel.busy(this)) return;
        ServerLevel world = (ServerLevel) level();
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z == null || (isGuard() ? slot >= z.guardCount() : (slot == 1 ? !z.cashier2 : !z.cashier))) { discard(); return; }
        if (!isGuard() && (z.closed || !dev.tcgshop.shop.ShopServer.isDayShift(world))) { // end of shift (1.1: or shop closed): goes home
            NpcLines.sayNear(this, "cashier_bye");
            discard();
            return;
        }
        if (tickCount % 200 == 0 && duplicate(world, z)) { discard(); return; } // 1.1: never two employees on the same post
        if (talkCooldown > 0) talkCooldown -= 10;
        if (isGuard()) guardTick(world, z);
        else cashierTick(world, z);
    }

    /** Another loaded employee already holds this post (older entity id wins). */
    private boolean duplicate(ServerLevel world, ShopZones.Zone z) {
        return !world.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(64),
                e -> e != this && e.isAlive() && z.id.equals(e.zoneId) && e.isGuard() == isGuard() && e.slot == slot && e.getId() < getId()).isEmpty();
    }

    // ------------------------------------------------------------------ cashier (1.1)

    /** Customer this cashier is walking to (null = at its register), and whether it goes to close a haggle. */
    @org.jetbrains.annotations.Nullable
    private CustomerEntity serving;
    private boolean servingHaggle;
    private int serveTicks;

    /**
     * 1.1: cashiers no longer only wait behind the counter. Every half second:
     *  · with the "handles hagglers" upgrade, a haggler the owner is not dealing with gets its deal closed at a fair price;
     *  · a customer waiting in the register queue away from the counter (further back in the line, or stuck on its way)
     *    within the Range upgrade's reach is walked up to and rung up where it stands (the most impatient first);
     *  · otherwise back to its post behind the register (the first in line is served there, CustomerEntity.checkoutTick).
     */
    private void cashierTick(ServerLevel world, ShopZones.Zone z) {
        int[] lv = CashierUpgrades.levels(world.getServer(), z.id);
        double speed = CashierUpgrades.walkSpeed(lv);
        int range = CashierUpgrades.range(lv);
        dev.tcgshop.events.Mods mods = dev.tcgshop.events.EventManager.mods(z);
        if (mods.registerDown) { serving = null; walkHome(speed); return; } // 0.9 events: broken register, nobody can be rung up
        Vec3 post = home.getCenter();
        boolean valid = serving != null && serving.isAlive() && (servingHaggle ? serving.waitingHaggle() : serving.awaitingCashier())
                && serving.distanceToSqr(post) <= (range + 3.0) * (range + 3.0);
        if (!valid) {
            serving = null;
            serveTicks = 0;
            pickCustomer(world, z, lv, range, post);
        }
        if (serving == null) { walkHome(speed); return; }
        double d = distanceToSqr(serving);
        if (d > 2.2 * 2.2) {
            if (getNavigation().isDone() || tickCount % 20 == 0) getNavigation().moveTo(serving, speed);
            serveTicks = 0;
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(serving);
        serveTicks += 10;
        if (serveTicks < Math.round(40 * CashierUpgrades.serviceMult(lv) * mods.serviceMult)) return;
        swing(InteractionHand.MAIN_HAND);
        if (servingHaggle) serving.closeByCashier(this);
        else serving.serveByStaff(this);
        serving = null;
        serveTicks = 0;
    }

    private void pickCustomer(ServerLevel world, ShopZones.Zone z, int[] lv, int range, Vec3 post) {
        double r2 = (double) range * range;
        List<EmployeeEntity> others = world.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(8), e -> e != this && !e.isGuard() && z.id.equals(e.zoneId));
        CustomerEntity best = null;
        boolean haggle = false;
        if (CashierUpgrades.handlesHagglers(lv)) {
            double bd = Double.MAX_VALUE;
            for (CustomerEntity c : world.getEntitiesOfClass(CustomerEntity.class, z.box(), c -> c.isAlive() && c.waitingHaggle())) {
                double d = c.distanceToSqr(post);
                if (d > r2 + 16 || claimed(others, c)) continue;
                if (d < bd) { bd = d; best = c; haggle = true; }
            }
        }
        if (best == null) {
            int bp = Integer.MAX_VALUE;
            for (CustomerEntity c : world.getEntitiesOfClass(CustomerEntity.class, z.box().inflate(2), c -> c.isAlive() && c.awaitingCashier())) {
                if (c.distanceToSqr(post) > r2 || claimed(others, c)) continue;
                if (c.patienceLeft() < bp) { bp = c.patienceLeft(); best = c; }
            }
        }
        serving = best;
        servingHaggle = haggle;
        if (best != null && talkCooldown <= 0 && random.nextFloat() < 0.35f) {
            talkCooldown = 200;
            announce(Component.translatable((haggle ? "tcgshop.cashier.to_haggle." : "tcgshop.cashier.walkup.") + random.nextInt(4), best.getDisplayName()));
        }
    }

    /** Selftest only: what this cashier is doing. */
    public String debugState() {
        return "at=" + blockPosition().toShortString() + " home=" + home.toShortString() + " serving=" + (serving == null ? "-" : serving.debugState()) + " ticks=" + serveTicks + " tc=" + tickCount + " ground=" + onGround() + " noai=" + isNoAi() + " navDone=" + getNavigation().isDone()
                + " path=" + (getNavigation().getPath() == null ? "-" : getNavigation().getPath().getNodeCount() + "/" + getNavigation().getPath().getTarget().toShortString());
    }

    private static boolean claimed(List<EmployeeEntity> others, CustomerEntity c) {
        for (EmployeeEntity e : others) if (e.serving == c) return true;
        return false;
    }

    private void walkHome(double speed) {
        if (distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 1.2 * 1.2) {
            if (getNavigation().isDone() || tickCount % 40 == 0) getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, speed);
        } else getNavigation().stop();
    }

    // ------------------------------------------------------------------ guard

    /**
     * How far outside the shop a guard follows someone before giving up and coming back (1.1: before, an upgraded guard's
     * detection radius let it chase a robber up to 40 blocks out, or a detected thief anywhere, leaving the shop with
     * nobody on watch).
     */
    private static int leash(int[] lv) {
        return 8 + 2 * Math.max(0, Math.min(GuardUpgrades.MAX_LEVEL, lv[GuardUpgrades.Stat.DETECTION.ordinal()]));
    }

    private void guardTick(ServerLevel world, ShopZones.Zone z) {
        // 1.0 beta 3: the shop's gear (re-applied when the levels change)
        int[] lv = GuardUpgrades.levels(world.getServer(), z.id);
        int sig = lv[0] | lv[1] << 4 | lv[2] << 8 | lv[3] << 12;
        if (sig != appliedSig) { GuardUpgrades.apply(this, lv); appliedSig = sig; }
        if (attackCd > 0) attackCd -= 10;
        if (grabCd > 0) grabCd -= 10;
        if (combatTicks > 0) combatTicks -= 10;
        else if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(GuardUpgrades.regen(lv));
        int radius = GuardUpgrades.radius(world.getServer(), z.id);
        net.minecraft.world.phys.AABB beat = z.box().inflate(leash(lv));
        // 1) armed robbers / heist crews that struck (or attack it): fight them
        CustomerEntity robber = null;
        double rb = Double.MAX_VALUE;
        for (CustomerEntity c : world.getEntitiesOfClass(CustomerEntity.class, z.box().inflate(radius + 8),
                c -> c.isAlive() && (c.isArmedAndHostile() || (c.isArmedThief() && getLastHurtByMob() == c)))) {
            boolean hitMe = getLastHurtByMob() == c && combatTicks > 0;
            if (!hitMe && !beat.contains(c.position())) continue; // too far from the shop: not worth leaving it unguarded
            double d = distanceToSqr(c);
            if (d < rb) { rb = d; robber = c; }
        }
        if (robber != null && (rb < (double) (radius + 8) * (radius + 8) || getLastHurtByMob() == robber)) {
            combatTicks = 100;
            setEmote(EMOTE_ANGRY);
            getNavigation().moveTo(robber, 1.4);
            getLookControl().setLookAt(robber);
            if (attackCd <= 0 && rb < 2.4 * 2.4) {
                swing(InteractionHand.MAIN_HAND);
                doHurtTarget(robber);
                attackCd = 20;
            }
            if (talkCooldown <= 0 && random.nextFloat() < 0.15f) {
                talkCooldown = 100;
                announce(Component.translatable("tcgshop.guard.fight." + random.nextInt(4)));
            }
            return;
        }
        // 2) chase thieves carrying loot inside (or near) the shop (radius: detection upgrade, 14 by default). 1.1: several
        // guards split up (a thief another guard is already on is left to it unless it is the only one)
        CustomerEntity thief = null;
        double best = Double.MAX_VALUE;
        List<EmployeeEntity> mates = world.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(32), e -> e != this && e.isGuard() && z.id.equals(e.zoneId));
        for (CustomerEntity c : world.getEntitiesOfClass(CustomerEntity.class, z.box().inflate(Math.max(12, radius - 2)), CustomerEntity::isFleeingThief)) {
            if (!beat.contains(c.position())) continue;
            double d = distanceToSqr(c) / (c.isDetected() ? 3 : 1);
            for (EmployeeEntity m : mates) if (m.chasing == c && m.distanceToSqr(c) < distanceToSqr(c)) d *= 4;
            if (d < best) { best = d; thief = c; }
        }
        chasing = null;
        if (thief != null && (thief.isDetected() || distanceToSqr(thief) < (double) radius * radius)) {
            chasing = thief;
            getNavigation().moveTo(thief, thief.isDetected() ? 1.45 : 1.3);
            setEmote(EMOTE_ANGRY);
            if (grabCd <= 0 && distanceToSqr(thief) < 2.6 * 2.6) {
                // sometimes the thief resists and they send out their Pokémon (the guard almost always wins)
                CustomerEntity t = thief;
                if (!t.duelled && random.nextFloat() < 0.4f) {
                    t.duelled = true;
                    if (dev.tcgshop.compat.NpcPokemonDuel.start(this, t, GUARD_POOL, THIEF_DUEL_POOL, 35, 0.4, (winner, loser) -> {
                        if (winner == this && t.isAlive()) t.detain(world, z, this);
                    })) return;
                }
                // 1.0 beta 3: a skilled thief may wriggle free (then the guard needs a moment to grab it again)
                if (thief.tryDetain(world, z, this)) setEmote(EMOTE_NONE);
                else grabCd = 30;
            }
            return;
        }
        // 3) detection level 3: tails a masked robber before it strikes
        if (lv[GuardUpgrades.Stat.DETECTION.ordinal()] >= 3) {
            for (CustomerEntity c : world.getEntitiesOfClass(CustomerEntity.class, z.box(), c -> c.isAlive() && c.isArmedThief() && !c.isArmedAndHostile())) {
                if (distanceToSqr(c) > 3.5 * 3.5) getNavigation().moveTo(c, 1.0);
                else getNavigation().stop();
                getLookControl().setLookAt(c);
                if (!tailWarned) {
                    tailWarned = true;
                    announce(Component.translatable("tcgshop.guard.tail." + random.nextInt(4), c.getDisplayName()));
                }
                return;
            }
        }
        tailWarned = false;
        setEmote(EMOTE_NONE);
        // back inside after a chase
        if (!z.contains(blockPosition())) {
            if (getNavigation().isDone() || tickCount % 40 == 0) getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            return;
        }
        // patrol inside the zone. 1.1: on the shop's floor (the old heightmap target was the roof of a roofed shop, so the
        // guard stood still or tried to climb onto it)
        if (getNavigation().isDone() && random.nextInt(6) == 0) {
            int half = z.size() / 2 - 1;
            int x = z.core.getX() + random.nextInt(half * 2 + 1) - half, zz = z.core.getZ() + random.nextInt(half * 2 + 1) - half;
            BlockPos f = RegisterSpots.floorNear(world, new BlockPos(x, z.core.getY() + 1, zz));
            if (f == null) f = RegisterSpots.floorNear(world, new BlockPos(x, z.core.getY(), zz));
            if (f != null && z.contains(f)) getNavigation().moveTo(f.getX() + 0.5, f.getY(), f.getZ() + 0.5, 0.8);
        }
    }

    /** 1.1: the fleeing thief this guard is after (so several guards split up). Not saved. */
    @org.jetbrains.annotations.Nullable
    private CustomerEntity chasing;

    /** 1.0 beta 3: a line after the owner bought it an upgrade (tcgshop.guardup.thanks.<stat>.<n>). */
    public void upgradedLine(GuardUpgrades.Stat s) {
        NpcLines.speak(this, Component.translatable("tcgshop.guardup.thanks." + s.name().toLowerCase(java.util.Locale.ROOT) + "." + random.nextInt(3)));
        appliedSig = -1;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && !isGuard() && hand == InteractionHand.MAIN_HAND) {
            // the cashier hands the register money to the owner (with the queue in front, the register is sometimes unreachable)
            ShopZones.Zone z = zoneId == null ? null : ShopZones.get(sp.serverLevel()).byId(zoneId);
            if (z != null && z.owner.equals(sp.getUUID())) {
                if (dev.tcgshop.events.EventManager.onEmployeeInteract(sp, z, this)) return InteractionResult.SUCCESS; // 0.9: distracted cashier
                dev.tcgshop.shop.ShopServer.useRegister(sp, z);
                return InteractionResult.SUCCESS;
            }
        }
        if (player instanceof ServerPlayer sp && isGuard() && hand == InteractionHand.MAIN_HAND) {
            // 1.0 beta 3: the owner sneak + right-clicks the guard for the upgrade menu
            ShopZones.Zone z = zoneId == null ? null : ShopZones.get(sp.serverLevel()).byId(zoneId);
            if (z != null && z.owner.equals(sp.getUUID())) {
                if (sp.isShiftKeyDown()) {
                    GuardUpgrades.showMenu(sp, z);
                    return InteractionResult.SUCCESS;
                }
                sp.displayClientMessage(Component.translatable("tcgshop.guardup.hint").withStyle(ChatFormatting.GRAY), true);
            }
        }
        if (player instanceof ServerPlayer sp && talkCooldown <= 0) {
            talkCooldown = 60;
            NpcLines.say(this, sp, isGuard() ? "guard" : "cashier");
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 1.0 beta 3: armed robbers and heist crews can hurt (and kill) guards; nothing else can
        if (isGuard() && source.getEntity() instanceof CustomerEntity c && c.isArmedThief()) {
            combatTicks = 100;
            return super.hurt(source, amount);
        }
        return source.isCreativePlayer() || source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL) ? super.hurt(source, amount) : false;
    }

    /**
     * 1.0 beta 3: a guard killed by a robber: that guard post is empty until the owner hires a new one (the gear stays
     * with the shop). 1.1: with several guards only this one leaves; the guards after it move down one slot.
     */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel world) || !isGuard() || !(source.getEntity() instanceof CustomerEntity)) return;
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone z = zoneId == null ? null : zones.byId(zoneId);
        if (z == null || !z.guard || slot >= z.guardCount()) return;
        String name = getCustomName() != null ? getCustomName().getString() : z.guardNameOf(slot);
        z.removeGuard(slot);
        shiftGuardSlots(world, z, slot);
        zones.setDirty();
        dev.tcgshop.phone.PhoneMessages.send(world.getServer(), z.owner, "tcgshop.msg.sender.security",
                Component.translatable("tcgshop.guard.fallen_msg", name, source.getEntity().getDisplayName()), true);
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        if (owner != null) owner.displayClientMessage(Component.translatable("tcgshop.guard.fallen", name, source.getEntity().getDisplayName()).withStyle(ChatFormatting.DARK_RED), false);
        dev.tcgshop.shop.ShopServer.syncZones(world.getServer());
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (zoneId != null) nbt.putUUID("Zone", zoneId);
        nbt.putInt("Slot", slot);
        nbt.putLong("Home", home.asLong());
        nbt.putBoolean("Guard", isGuard());
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.hasUUID("Zone")) zoneId = nbt.getUUID("Zone");
        slot = nbt.getInt("Slot");
        home = BlockPos.of(nbt.getLong("Home"));
        entityData.set(GUARD, nbt.getBoolean("Guard"));
        entityData.set(TITLE, nbt.getString("NpcTitle"));
        entityData.set(SKIN, nbt.getInt("NpcSkin"));
    }

    /** Chat message to nearby players, signed by the employee. */
    public void announce(Component msg) {
        NpcLines.speak(this, msg);
    }
}
