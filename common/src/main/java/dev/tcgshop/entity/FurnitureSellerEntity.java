package dev.tcgshop.entity;

import dev.tcgshop.compat.CobbleFurniesCompat;
import dev.tcgshop.shop.FurnitureShop;
import dev.tcgshop.shop.ShopZones;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Furniture seller: visits players who HAVE a shop (like the Beggar or the Wholesaler, it appears near their
 * shop) and sells CobbleFurnies furniture, Pokéblocks plushies and the luxury statues (Exclusives, v0.9); it buys back
 * furniture and plushies at a reduced price (not statues).
 * Wanders near the shop and leaves after one Minecraft day. If those mods are missing, it does not appear.
 */
public class FurnitureSellerEntity extends PathfinderMob implements TcgNpc {
    private static final EntityDataAccessor<String> TITLE = SynchedEntityData.defineId(FurnitureSellerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(FurnitureSellerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(FurnitureSellerEntity.class, EntityDataSerializers.INT);

    /** One Minecraft day. */
    public static final int LIFE = 24_000;
    private int lifeTicks = LIFE, emoteTicks, helloCooldown;
    private boolean saidBye;
    @Nullable private UUID forPlayer;
    @Nullable private BlockPos home;

    public FurnitureSellerEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TITLE, "");
        builder.define(SKIN, 2);
        builder.define(EMOTE, EMOTE_NONE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.45));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override public String roleKey() { return "tcgshop.role.furniture_seller"; }
    @Override public String npcTitle() { return entityData.get(TITLE); }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return entityData.get(EMOTE); }

    /** Emotion bubble for a few seconds (after a purchase or a sale). */
    public void flashEmote(int emote) {
        entityData.set(EMOTE, emote);
        emoteTicks = 60;
    }

    public void setForPlayer(@Nullable UUID id) { forPlayer = id; }

    /** Stays wandering near this point (the player's shop). */
    public void setHome(BlockPos pos) {
        home = pos.immutable();
        restrictTo(home, 14);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level().isClientSide);
        // 1.1: it always has something to sell (our own decor, CleanBlocks.CATALOG), even without CobbleFurnies
        playSound(SoundEvents.WANDERING_TRADER_YES, 1f, 1.05f);
        getNavigation().stop();
        getLookControl().setLookAt(sp, 30f, 30f);
        if (helloCooldown <= 0) {
            NpcLines.say(this, sp, "furn_hello");
            helloCooldown = 200;
        }
        FurnitureShop.open(sp, getId(), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!hasCustomName()) {
            setCustomName(Component.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            entityData.set(TITLE, NpcIdentity.randomTitle(random, 0.25f));
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (helloCooldown > 0) helloCooldown--;
        if (emoteTicks > 0 && --emoteTicks == 0) entityData.set(EMOTE, EMOTE_NONE);
        if (tickCount % 260 == 0 && lifeTicks > 200 && level().getNearestPlayer(this, 12) != null && random.nextFloat() < 0.35f)
            NpcLines.sayNear(this, "furn_ambient");
        lifeTicks--;
        if (lifeTicks == 100 && !saidBye) {
            saidBye = true;
            NpcLines.sayNear(this, "furn_bye");
        }
        if (lifeTicks <= 0) leave();
    }

    private void leave() {
        if (level() instanceof ServerLevel w) {
            w.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            w.playSound(null, blockPosition(), SoundEvents.WANDERING_TRADER_DISAPPEARED, SoundSource.NEUTRAL, 1f, 1f);
        }
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Life", lifeTicks);
        nbt.putBoolean("SaidBye", saidBye);
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
        if (forPlayer != null) nbt.putUUID("ForPlayer", forPlayer);
        if (home != null) nbt.putLong("Home", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        saidBye = nbt.getBoolean("SaidBye");
        entityData.set(TITLE, nbt.getString("NpcTitle"));
        entityData.set(SKIN, nbt.contains("NpcSkin") ? nbt.getInt("NpcSkin") : 2);
        forPlayer = nbt.hasUUID("ForPlayer") ? nbt.getUUID("ForPlayer") : null;
        if (nbt.contains("Home")) setHome(BlockPos.of(nbt.getLong("Home")));
    }

    // ------------------------------------------------------------------ spawning

    /**
     * Every half Minecraft day (phase 10500), with probability FurnitureShop.SELLER_CHANCE, a Furniture seller
     * arrives at the shop of an online player who owns a shop (if they are far from it, near the player).
     * Register in ServerTickEvents.END_WORLD_TICK (see hooks).
     */
    public static void spawner(ServerLevel world) {
        if (world.dimension() != Level.OVERWORLD) return;
        if (world.getGameTime() % 12000 != 10500) return;
        if (world.random.nextDouble() > FurnitureShop.SELLER_CHANCE) return;
        trySpawn(world, null);
    }

    /**
     * Tries to bring the Furniture seller. target = specific player (testing / command) or null = a random shop
     * owner. Returns whether it spawned.
     */
    public static boolean trySpawn(ServerLevel world, @Nullable ServerPlayer target) {
        return spawnFor(world, target) == null;
    }

    /**
     * Like {@link #trySpawn} but says why it did not come: null = it spawned, else a lang key
     * (tcgshop.phoneup.furn_call.already_near / near_shop, tcgshop.phoneup.call.no_room).
     */
    @Nullable
    public static String spawnFor(ServerLevel world, @Nullable ServerPlayer target) {
        // 1.1: it always comes (our own decor is always on sale, even without CobbleFurnies/Pokéblocks or luxury statues)
        ShopZones zones = ShopZones.get(world);
        List<ServerPlayer> owners = new ArrayList<>();
        if (target != null) owners.add(target);
        else for (ServerPlayer p : world.players()) {
            if (p.isSpectator()) continue;
            for (ShopZones.Zone z : zones.all()) if (z.owner.equals(p.getUUID())) { owners.add(p); break; }
        }
        if (owners.isEmpty()) return "tcgshop.phoneup.call.no_room";
        ServerPlayer p = owners.get(world.random.nextInt(owners.size()));
        // their nearest shop, if it is within 96 blocks; otherwise, next to the player
        Vec3 center = p.position();
        BlockPos shop = null;
        double best = 96 * 96;
        for (ShopZones.Zone z : zones.all()) {
            if (!z.owner.equals(p.getUUID())) continue;
            double d = z.core.getCenter().distanceToSqr(p.position());
            if (d < best) { best = d; shop = z.core; }
        }
        if (shop != null) center = shop.getCenter();
        // no duplicate sellers: check around the player AND around the spot where it will spawn (the shop
        // may be 96 blocks from the player and the previous seller may still be on the other side of it)
        if (!world.getEntitiesOfClass(FurnitureSellerEntity.class, p.getBoundingBox().inflate(96), e -> true).isEmpty()) return "tcgshop.phoneup.furn_call.already_near";
        if (!world.getEntitiesOfClass(FurnitureSellerEntity.class, AABB.ofSize(center, 192, 192, 192), e -> true).isEmpty()) return "tcgshop.phoneup.furn_call.near_shop";
        BlockPos pos = groundNear(world, center, shop != null ? 5 : 8, shop != null ? 14 : 18);
        if (pos == null) return "tcgshop.phoneup.call.no_room";
        FurnitureSellerEntity s = TcgEntities.FURNITURE_SELLER.create(world);
        if (s == null) return "tcgshop.phoneup.call.no_room";
        s.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
        s.setForPlayer(p.getUUID());
        s.setHome(shop != null ? shop : pos);
        world.addFreshEntity(s);
        world.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.4, 0.6, 0.4, 0.02);
        p.displayClientMessage(Component.translatable("tcgshop.furn.arrived").withStyle(ChatFormatting.GOLD), false);
        return null;
    }

    /** A free ground spot between minD and maxD blocks from c (or null). */
    @Nullable
    private static BlockPos groundNear(ServerLevel world, Vec3 c, int minD, int maxD) {
        for (int attempt = 0; attempt < 14; attempt++) {
            double ang = world.random.nextDouble() * Math.PI * 2;
            int dist = minD + world.random.nextInt(Math.max(1, maxD - minD));
            int x = (int) Math.floor(c.x + Math.cos(ang) * dist), z = (int) Math.floor(c.z + Math.sin(ang) * dist);
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (Math.abs(y - c.y) > 12) continue;
            if (dev.tcgshop.shop.ShopZones.isShopRoof(world, pos)) continue; // 1.1: never on a shop's roof
            if (world.getBlockState(pos.below()).isRedstoneConductor(world, pos.below()) && world.isEmptyBlock(pos) && world.isEmptyBlock(pos.above())) return pos;
        }
        return null;
    }
}
