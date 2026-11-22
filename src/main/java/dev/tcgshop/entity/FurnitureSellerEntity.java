package dev.tcgshop.entity;

import dev.tcgshop.compat.CobbleFurniesCompat;
import dev.tcgshop.shop.FurnitureShop;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vendedor de muebles: visita a los jugadores que TIENEN tienda (como el Mendigo o el Mayorista, aparece cerca de su
 * tienda) y vende muebles de CobbleFurnies, peluches de Pokéblocks y las estatuas de lujo (Exclusivos, v0.9); te recompra
 * muebles y peluches a un precio reducido (las estatuas no).
 * Ronda cerca de la tienda y se marcha pasado un día de Minecraft. Si no están esos mods, no aparece.
 */
public class FurnitureSellerEntity extends PathAwareEntity implements TcgNpc {
    private static final TrackedData<String> TITLE = DataTracker.registerData(FurnitureSellerEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(FurnitureSellerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EMOTE = DataTracker.registerData(FurnitureSellerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Un día de Minecraft. */
    public static final int LIFE = 24_000;
    private int lifeTicks = LIFE, emoteTicks, helloCooldown;
    private boolean saidBye;
    @Nullable private UUID forPlayer;
    @Nullable private BlockPos home;

    public FurnitureSellerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TITLE, "");
        builder.add(SKIN, 2);
        builder.add(EMOTE, EMOTE_NONE);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.45));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override public String roleKey() { return "tcgshop.role.furniture_seller"; }
    @Override public String npcTitle() { return dataTracker.get(TITLE); }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return dataTracker.get(EMOTE); }

    /** Bocadillo de emoción unos segundos (tras una compra o una venta). */
    public void flashEmote(int emote) {
        dataTracker.set(EMOTE, emote);
        emoteTicks = 60;
    }

    public void setForPlayer(@Nullable UUID id) { forPlayer = id; }

    /** Se queda rondando cerca de este punto (la tienda del jugador). */
    public void setHome(BlockPos pos) {
        home = pos.toImmutable();
        setPositionTarget(home, 14);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.success(getWorld().isClient);
        if (!CobbleFurniesCompat.available() && !dev.tcgshop.luxury.LuxuryShop.enabled()) {
            sp.sendMessage(Text.translatable("tcgshop.furn.unavailable").formatted(Formatting.GRAY), true);
            return ActionResult.SUCCESS;
        }
        playSound(SoundEvents.ENTITY_WANDERING_TRADER_YES, 1f, 1.05f);
        getNavigation().stop();
        getLookControl().lookAt(sp, 30f, 30f);
        if (helloCooldown <= 0) {
            NpcLines.say(this, sp, "furn_hello");
            helloCooldown = 200;
        }
        FurnitureShop.open(sp, getId(), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!hasCustomName()) {
            setCustomName(Text.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            dataTracker.set(TITLE, NpcIdentity.randomTitle(random, 0.25f));
            dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (helloCooldown > 0) helloCooldown--;
        if (emoteTicks > 0 && --emoteTicks == 0) dataTracker.set(EMOTE, EMOTE_NONE);
        if (age % 260 == 0 && lifeTicks > 200 && getWorld().getClosestPlayer(this, 12) != null && random.nextFloat() < 0.35f)
            NpcLines.sayNear(this, "furn_ambient");
        lifeTicks--;
        if (lifeTicks == 100 && !saidBye) {
            saidBye = true;
            NpcLines.sayNear(this, "furn_bye");
        }
        if (lifeTicks <= 0) leave();
    }

    private void leave() {
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            w.playSound(null, getBlockPos(), SoundEvents.ENTITY_WANDERING_TRADER_DISAPPEARED, SoundCategory.NEUTRAL, 1f, 1f);
        }
        discard();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Life", lifeTicks);
        nbt.putBoolean("SaidBye", saidBye);
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
        if (forPlayer != null) nbt.putUuid("ForPlayer", forPlayer);
        if (home != null) nbt.putLong("Home", home.asLong());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        saidBye = nbt.getBoolean("SaidBye");
        dataTracker.set(TITLE, nbt.getString("NpcTitle"));
        dataTracker.set(SKIN, nbt.contains("NpcSkin") ? nbt.getInt("NpcSkin") : 2);
        forPlayer = nbt.containsUuid("ForPlayer") ? nbt.getUuid("ForPlayer") : null;
        if (nbt.contains("Home")) setHome(BlockPos.fromLong(nbt.getLong("Home")));
    }

    // ------------------------------------------------------------------ aparición

    /**
     * Cada medio día de Minecraft (fase 10500), con probabilidad FurnitureShop.SELLER_CHANCE, llega un Vendedor de
     * muebles a la tienda de un jugador conectado que tenga tienda (si está lejos de ella, cerca del jugador).
     * Registrar en ServerTickEvents.END_WORLD_TICK (ver hooks).
     */
    public static void spawner(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        if (world.getTime() % 12000 != 10500) return;
        if (world.random.nextDouble() > FurnitureShop.SELLER_CHANCE) return;
        trySpawn(world, null);
    }

    /**
     * Intenta traer al Vendedor de muebles. target = jugador concreto (pruebas / comando) o null = un dueño de tienda al
     * azar. Devuelve si apareció.
     */
    public static boolean trySpawn(ServerWorld world, @Nullable ServerPlayerEntity target) {
        // sin CobbleFurnies/Pokéblocks sigue viniendo si vende estatuas de lujo (pestaña Exclusivos)
        if (!CobbleFurniesCompat.available() && !dev.tcgshop.luxury.LuxuryShop.enabled()) return false;
        ShopZones zones = ShopZones.get(world);
        List<ServerPlayerEntity> owners = new ArrayList<>();
        if (target != null) owners.add(target);
        else for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator()) continue;
            for (ShopZones.Zone z : zones.all()) if (z.owner.equals(p.getUuid())) { owners.add(p); break; }
        }
        if (owners.isEmpty()) return false;
        ServerPlayerEntity p = owners.get(world.random.nextInt(owners.size()));
        // su tienda más cercana, si está a menos de 96 bloques; si no, junto al jugador
        Vec3d center = p.getPos();
        BlockPos shop = null;
        double best = 96 * 96;
        for (ShopZones.Zone z : zones.all()) {
            if (!z.owner.equals(p.getUuid())) continue;
            double d = z.core.toCenterPos().squaredDistanceTo(p.getPos());
            if (d < best) { best = d; shop = z.core; }
        }
        if (shop != null) center = shop.toCenterPos();
        // nada de vendedores repetidos: se mira alrededor del jugador Y alrededor del sitio donde va a aparecer (la tienda
        // puede estar a 96 bloques del jugador y el vendedor anterior seguir al otro lado de ella)
        if (!world.getEntitiesByClass(FurnitureSellerEntity.class, p.getBoundingBox().expand(96), e -> true).isEmpty()) return false;
        if (!world.getEntitiesByClass(FurnitureSellerEntity.class, Box.of(center, 192, 192, 192), e -> true).isEmpty()) return false;
        BlockPos pos = groundNear(world, center, shop != null ? 5 : 8, shop != null ? 14 : 18);
        if (pos == null) return false;
        FurnitureSellerEntity s = TcgEntities.FURNITURE_SELLER.create(world);
        if (s == null) return false;
        s.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
        s.setForPlayer(p.getUuid());
        s.setHome(shop != null ? shop : pos);
        world.spawnEntity(s);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.4, 0.6, 0.4, 0.02);
        p.sendMessage(Text.translatable("tcgshop.furn.arrived").formatted(Formatting.GOLD), false);
        return true;
    }

    /** Un sitio de suelo libre entre minD y maxD bloques de c (o null). */
    @Nullable
    private static BlockPos groundNear(ServerWorld world, Vec3d c, int minD, int maxD) {
        for (int attempt = 0; attempt < 14; attempt++) {
            double ang = world.random.nextDouble() * Math.PI * 2;
            int dist = minD + world.random.nextInt(Math.max(1, maxD - minD));
            int x = (int) Math.floor(c.x + Math.cos(ang) * dist), z = (int) Math.floor(c.z + Math.sin(ang) * dist);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (Math.abs(y - c.y) > 12) continue;
            if (world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) && world.isAir(pos) && world.isAir(pos.up())) return pos;
        }
        return null;
    }
}
