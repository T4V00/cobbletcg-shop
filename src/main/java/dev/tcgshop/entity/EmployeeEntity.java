package dev.tcgshop.entity;

import dev.tcgshop.shop.GuardUpgrades;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * Employee hired from the phone:
 *  · Cashier: runs the cash register. Without a cashier, the shop cannot sell packs, boxes or mystery boxes.
 *  · Guard: patrols the shop, chases thieves and recovers stolen goods (metrics: detained and cards recovered).
 *    1.0 beta 3: wears the shop's upgrades (GuardUpgrades: armor, weapon, training, detection), fights armed robbers and
 *    heist crews (ThiefTiers) and can fall doing it: then the shop has no guard until you hire a new one. The owner
 *    sneak + right-clicks it for the upgrade menu.
 */
public class EmployeeEntity extends PathAwareEntity implements TcgNpc {
    private static final TrackedData<String> TITLE = DataTracker.registerData(EmployeeEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(EmployeeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> GUARD = DataTracker.registerData(EmployeeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> EMOTE = DataTracker.registerData(EmployeeEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final String[] GUARD_POOL = {"growlithe", "arcanine", "lucario", "riolu", "machoke", "herdier", "rockruff", "boldore"};
    private static final String[] THIEF_DUEL_POOL = {"purrloin", "nickit", "sneasel", "murkrow", "zorua", "meowth", "poochyena"};

    private UUID zoneId;
    private BlockPos home = BlockPos.ORIGIN;
    private int talkCooldown;
    /** 1.0 beta 3: signature of the upgrade levels last applied (-1 = not yet), swing cooldown, grab cooldown after a slip. */
    private int appliedSig = -1, attackCd, grabCd, combatTicks;
    /** 1.0 beta 3: has already warned about the masked robber it is tailing. */
    private boolean tailWarned;

    public EmployeeEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0); // 1.0 beta 3: guards fight armed thieves (+ weapon upgrade)
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TITLE, "");
        builder.add(SKIN, 0);
        builder.add(GUARD, false);
        builder.add(EMOTE, 0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(6, new LookAtEntityGoal(this, CustomerEntity.class, 8f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    public boolean isGuard() { return dataTracker.get(GUARD); }
    public UUID zoneId() { return zoneId; }

    @Override public String roleKey() { return isGuard() ? "tcgshop.role.guard" : "tcgshop.role.cashier"; }
    @Override public String npcTitle() { return dataTracker.get(TITLE); }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return dataTracker.get(EMOTE); }

    public void setEmote(int e) { dataTracker.set(EMOTE, e); }

    // ------------------------------------------------------------------ hire / fire

    private int slot;

    public int slot() { return slot; }

    public static void spawnFor(ServerWorld world, ShopZones.Zone z, boolean cashier) {
        spawnFor(world, z, cashier, 0);
    }

    /** slot: register post (0 = first cash register, 1 = the second closest). */
    public static void spawnFor(ServerWorld world, ShopZones.Zone z, boolean cashier, int slot) {
        EmployeeEntity e = TcgEntities.EMPLOYEE.create(world);
        if (e == null) return;
        e.zoneId = z.id;
        e.slot = slot;
        e.dataTracker.set(GUARD, !cashier);
        e.dataTracker.set(SKIN, NpcIdentity.randomSkin(world.random));
        e.dataTracker.set(TITLE, NpcIdentity.randomTitle(world.random, 0.35f));
        e.setCustomName(Text.literal(cashier ? (slot == 1 ? z.cashier2Name : z.cashierName) : z.guardName));
        e.setCustomNameVisible(true);
        BlockPos home = z.core.up();
        if (cashier) { // behind the cash register closest to the shop block
            int seen = 0;
            for (BlockPos p : BlockPos.iterateOutwards(z.core, z.size() / 2, 4, z.size() / 2)) {
                if (world.getBlockState(p).isOf(TcgBlocks.CASH_REGISTER) && seen++ == slot) {
                    var facing = world.getBlockState(p).get(net.minecraft.state.property.Properties.HORIZONTAL_FACING);
                    BlockPos behind = p.offset(facing.getOpposite());
                    home = world.isAir(behind) ? behind : p.up();
                    break;
                }
            }
        }
        e.home = home.toImmutable();
        e.refreshPositionAndAngles(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
        world.spawnEntity(e);
    }

    public static void removeFor(ServerWorld world, ShopZones.Zone z, boolean cashier) {
        removeFor(world, z, cashier, -1);
    }

    /** slot -1 = all of that role. */
    public static void removeFor(ServerWorld world, ShopZones.Zone z, boolean cashier, int slot) {
        List<EmployeeEntity> list = world.getEntitiesByClass(EmployeeEntity.class, z.box().expand(16),
                e -> z.id.equals(e.zoneId) && e.isGuard() == !cashier && (slot < 0 || !cashier || e.slot == slot));
        for (EmployeeEntity e : list) e.discard();
    }

    // ------------------------------------------------------------------ behavior

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || age % 10 != 0 || dev.tcgshop.compat.NpcPokemonDuel.busy(this)) return;
        ServerWorld world = (ServerWorld) getWorld();
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z == null || (isGuard() ? !z.guard : (slot == 1 ? !z.cashier2 : !z.cashier))) { discard(); return; }
        if (!isGuard() && !dev.tcgshop.shop.ShopServer.isDayShift(world)) { // end of shift: goes home
            NpcLines.sayNear(this, "cashier_bye");
            discard();
            return;
        }
        if (talkCooldown > 0) talkCooldown -= 10;
        if (isGuard()) guardTick(world, z);
        else if (squaredDistanceTo(home.toCenterPos()) > 4) getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
    }

    private void guardTick(ServerWorld world, ShopZones.Zone z) {
        // 1.0 beta 3: the shop's gear (re-applied when the levels change)
        int[] lv = GuardUpgrades.levels(world.getServer(), z.id);
        int sig = lv[0] | lv[1] << 4 | lv[2] << 8 | lv[3] << 12;
        if (sig != appliedSig) { GuardUpgrades.apply(this, lv); appliedSig = sig; }
        if (attackCd > 0) attackCd -= 10;
        if (grabCd > 0) grabCd -= 10;
        if (combatTicks > 0) combatTicks -= 10;
        else if (age % 40 == 0 && getHealth() < getMaxHealth()) heal(GuardUpgrades.regen(lv));
        int radius = GuardUpgrades.radius(world.getServer(), z.id);
        // 1) armed robbers / heist crews that struck (or attack it): fight them
        CustomerEntity robber = null;
        double rb = Double.MAX_VALUE;
        for (CustomerEntity c : world.getEntitiesByClass(CustomerEntity.class, z.box().expand(radius + 8),
                c -> c.isAlive() && (c.isArmedAndHostile() || (c.isArmedThief() && getAttacker() == c)))) {
            double d = squaredDistanceTo(c);
            if (d < rb) { rb = d; robber = c; }
        }
        if (robber != null && (rb < (double) (radius + 8) * (radius + 8) || getAttacker() == robber)) {
            combatTicks = 100;
            setEmote(EMOTE_ANGRY);
            getNavigation().startMovingTo(robber, 1.4);
            getLookControl().lookAt(robber);
            if (attackCd <= 0 && rb < 2.4 * 2.4) {
                swingHand(Hand.MAIN_HAND);
                tryAttack(robber);
                attackCd = 20;
            }
            if (talkCooldown <= 0 && random.nextFloat() < 0.15f) {
                talkCooldown = 100;
                announce(Text.translatable("tcgshop.guard.fight." + random.nextInt(4)));
            }
            return;
        }
        // 2) chase thieves carrying loot inside (or near) the shop (radius: detection upgrade, 14 by default)
        CustomerEntity thief = null;
        double best = Double.MAX_VALUE;
        for (CustomerEntity c : world.getEntitiesByClass(CustomerEntity.class, z.box().expand(Math.max(12, radius - 2)), CustomerEntity::isFleeingThief)) {
            double d = squaredDistanceTo(c) / (c.isDetected() ? 3 : 1);
            if (d < best) { best = d; thief = c; }
        }
        if (thief != null && (thief.isDetected() || squaredDistanceTo(thief) < (double) radius * radius)) {
            getNavigation().startMovingTo(thief, thief.isDetected() ? 1.45 : 1.3);
            setEmote(EMOTE_ANGRY);
            if (grabCd <= 0 && squaredDistanceTo(thief) < 2.6 * 2.6) {
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
            for (CustomerEntity c : world.getEntitiesByClass(CustomerEntity.class, z.box(), c -> c.isAlive() && c.isArmedThief() && !c.isArmedAndHostile())) {
                if (squaredDistanceTo(c) > 3.5 * 3.5) getNavigation().startMovingTo(c, 1.0);
                else getNavigation().stop();
                getLookControl().lookAt(c);
                if (!tailWarned) {
                    tailWarned = true;
                    announce(Text.translatable("tcgshop.guard.tail." + random.nextInt(4), c.getDisplayName()));
                }
                return;
            }
        }
        tailWarned = false;
        setEmote(EMOTE_NONE);
        // patrol inside the zone
        if (getNavigation().isIdle() && random.nextInt(6) == 0) {
            int half = z.size() / 2 - 1;
            int x = z.core.getX() + random.nextInt(half * 2 + 1) - half, zz = z.core.getZ() + random.nextInt(half * 2 + 1) - half;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, zz);
            if (Math.abs(y - z.core.getY()) <= 4) getNavigation().startMovingTo(x + 0.5, y, zz + 0.5, 0.8);
        }
    }

    /** 1.0 beta 3: a line after the owner bought it an upgrade (tcgshop.guardup.thanks.<stat>.<n>). */
    public void upgradedLine(GuardUpgrades.Stat s) {
        NpcLines.speak(this, Text.translatable("tcgshop.guardup.thanks." + s.name().toLowerCase(java.util.Locale.ROOT) + "." + random.nextInt(3)));
        appliedSig = -1;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity sp && !isGuard()) {
            // the cashier hands the register money to the owner (with the queue in front, the register is sometimes unreachable)
            ShopZones.Zone z = zoneId == null ? null : ShopZones.get(sp.getServerWorld()).byId(zoneId);
            if (z != null && z.owner.equals(sp.getUuid())) {
                if (dev.tcgshop.events.EventManager.onEmployeeInteract(sp, z, this)) return ActionResult.SUCCESS; // 0.9: distracted cashier
                dev.tcgshop.shop.ShopServer.useRegister(sp, z);
                return ActionResult.SUCCESS;
            }
        }
        if (player instanceof ServerPlayerEntity sp && isGuard() && hand == Hand.MAIN_HAND) {
            // 1.0 beta 3: the owner sneak + right-clicks the guard for the upgrade menu
            ShopZones.Zone z = zoneId == null ? null : ShopZones.get(sp.getServerWorld()).byId(zoneId);
            if (z != null && z.owner.equals(sp.getUuid())) {
                if (sp.isSneaking()) {
                    GuardUpgrades.showMenu(sp, z);
                    return ActionResult.SUCCESS;
                }
                sp.sendMessage(Text.translatable("tcgshop.guardup.hint").formatted(Formatting.GRAY), true);
            }
        }
        if (player instanceof ServerPlayerEntity sp && talkCooldown <= 0) {
            talkCooldown = 60;
            NpcLines.say(this, sp, isGuard() ? "guard" : "cashier");
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // 1.0 beta 3: armed robbers and heist crews can hurt (and kill) guards; nothing else can
        if (isGuard() && source.getAttacker() instanceof CustomerEntity c && c.isArmedThief()) {
            combatTicks = 100;
            return super.damage(source, amount);
        }
        return source.isSourceCreativePlayer() || source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL) ? super.damage(source, amount) : false;
    }

    /** 1.0 beta 3: a guard killed by a robber: the shop has no guard until the owner hires a new one (the gear stays with the shop). */
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!(getWorld() instanceof ServerWorld world) || !isGuard() || !(source.getAttacker() instanceof CustomerEntity)) return;
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone z = zoneId == null ? null : zones.byId(zoneId);
        if (z == null || !z.guard) return;
        z.guard = false;
        zones.markDirty();
        String name = getCustomName() != null ? getCustomName().getString() : z.guardName;
        dev.tcgshop.phone.PhoneMessages.send(world.getServer(), z.owner, "tcgshop.msg.sender.security",
                Text.translatable("tcgshop.guard.fallen_msg", name, source.getAttacker().getDisplayName()), true);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null) owner.sendMessage(Text.translatable("tcgshop.guard.fallen", name, source.getAttacker().getDisplayName()).formatted(Formatting.DARK_RED), false);
        dev.tcgshop.shop.ShopServer.syncZones(world.getServer());
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (zoneId != null) nbt.putUuid("Zone", zoneId);
        nbt.putInt("Slot", slot);
        nbt.putLong("Home", home.asLong());
        nbt.putBoolean("Guard", isGuard());
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Zone")) zoneId = nbt.getUuid("Zone");
        slot = nbt.getInt("Slot");
        home = BlockPos.fromLong(nbt.getLong("Home"));
        dataTracker.set(GUARD, nbt.getBoolean("Guard"));
        dataTracker.set(TITLE, nbt.getString("NpcTitle"));
        dataTracker.set(SKIN, nbt.getInt("NpcSkin"));
    }

    /** Chat message to nearby players, signed by the employee. */
    public void announce(Text msg) {
        NpcLines.speak(this, msg);
    }
}
