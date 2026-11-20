package dev.tcgshop.entity;

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
 * Empleado contratado desde el teléfono:
 *  · Cajero: atiende la caja registradora. Sin cajero, la tienda no puede vender sobres, cajas ni mystery boxes.
 *  · Guardia: patrulla la tienda, persigue ladrones y recupera lo robado (métricas: detenidos y cartas recuperadas).
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

    public EmployeeEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
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

    // ------------------------------------------------------------------ contratar / despedir

    private int slot;

    public int slot() { return slot; }

    public static void spawnFor(ServerWorld world, ShopZones.Zone z, boolean cashier) {
        spawnFor(world, z, cashier, 0);
    }

    /** slot: puesto de caja (0 = primera caja registradora, 1 = la segunda más cercana). */
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
        if (cashier) { // detrás de la caja registradora más cercana al bloque de tienda
            int seen = 0;
            for (BlockPos p : BlockPos.iterateOutwards(z.core, z.tierInfo().size / 2, 4, z.tierInfo().size / 2)) {
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

    /** slot -1 = todos los de ese rol. */
    public static void removeFor(ServerWorld world, ShopZones.Zone z, boolean cashier, int slot) {
        List<EmployeeEntity> list = world.getEntitiesByClass(EmployeeEntity.class, z.box().expand(16),
                e -> z.id.equals(e.zoneId) && e.isGuard() == !cashier && (slot < 0 || !cashier || e.slot == slot));
        for (EmployeeEntity e : list) e.discard();
    }

    // ------------------------------------------------------------------ comportamiento

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient || age % 10 != 0 || dev.tcgshop.compat.NpcPokemonDuel.busy(this)) return;
        ServerWorld world = (ServerWorld) getWorld();
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z == null || (isGuard() ? !z.guard : (slot == 1 ? !z.cashier2 : !z.cashier))) { discard(); return; }
        if (!isGuard() && !dev.tcgshop.shop.ShopServer.isDayShift(world)) { // fin del turno: se va a casa
            NpcLines.sayNear(this, "cashier_bye");
            discard();
            return;
        }
        if (talkCooldown > 0) talkCooldown -= 10;
        if (isGuard()) guardTick(world, z);
        else if (squaredDistanceTo(home.toCenterPos()) > 4) getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
    }

    private void guardTick(ServerWorld world, ShopZones.Zone z) {
        // perseguir ladrones con botín dentro (o cerca) de la tienda
        CustomerEntity thief = null;
        double best = Double.MAX_VALUE;
        for (CustomerEntity c : world.getEntitiesByClass(CustomerEntity.class, z.box().expand(12), CustomerEntity::isFleeingThief)) {
            double d = squaredDistanceTo(c) / (c.isDetected() ? 3 : 1);
            if (d < best) { best = d; thief = c; }
        }
        if (thief != null && (thief.isDetected() || squaredDistanceTo(thief) < 14 * 14)) {
            getNavigation().startMovingTo(thief, thief.isDetected() ? 1.45 : 1.3);
            setEmote(EMOTE_ANGRY);
            if (squaredDistanceTo(thief) < 2.6 * 2.6) {
                // a veces el ladrón se resiste y sacan los Pokémon (el guardia casi siempre gana)
                CustomerEntity t = thief;
                if (!t.duelled && random.nextFloat() < 0.4f) {
                    t.duelled = true;
                    if (dev.tcgshop.compat.NpcPokemonDuel.start(this, t, GUARD_POOL, THIEF_DUEL_POOL, 35, 0.4, (winner, loser) -> {
                        if (winner == this && t.isAlive()) t.detain(world, z, this);
                    })) return;
                }
                thief.detain(world, z, this);
                setEmote(EMOTE_NONE);
            }
            return;
        }
        setEmote(EMOTE_NONE);
        // patrulla dentro de la zona
        if (getNavigation().isIdle() && random.nextInt(6) == 0) {
            int half = z.tierInfo().size / 2 - 1;
            int x = z.core.getX() + random.nextInt(half * 2 + 1) - half, zz = z.core.getZ() + random.nextInt(half * 2 + 1) - half;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, zz);
            if (Math.abs(y - z.core.getY()) <= 4) getNavigation().startMovingTo(x + 0.5, y, zz + 0.5, 0.8);
        }
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity sp && talkCooldown <= 0) {
            talkCooldown = 60;
            NpcLines.say(this, sp, isGuard() ? "guard" : "cashier");
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return source.isSourceCreativePlayer() || source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL) ? super.damage(source, amount) : false;
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

    /** Mensaje en el chat a los jugadores cercanos, firmado por el empleado. */
    public void announce(Text msg) {
        NpcLines.speak(this, msg);
    }
}
