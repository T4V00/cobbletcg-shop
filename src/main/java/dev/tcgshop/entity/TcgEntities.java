package dev.tcgshop.entity;

import dev.tcgshop.TcgShop;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

public final class TcgEntities {
    public static final EntityType<ScalperEntity> SCALPER = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "scalper"),
            EntityType.Builder.create(ScalperEntity::new, SpawnGroup.MONSTER).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("scalper"));

    public static final EntityType<WholesalerEntity> WHOLESALER = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "wholesaler"),
            EntityType.Builder.create(WholesalerEntity::new, SpawnGroup.CREATURE).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("wholesaler"));

    public static final EntityType<TraderEntity> TRADER = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "trader"),
            EntityType.Builder.create(TraderEntity::new, SpawnGroup.CREATURE).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("trader"));

    public static final EntityType<CustomerEntity> CUSTOMER = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "customer"),
            EntityType.Builder.create(CustomerEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("customer"));
    public static final EntityType<EmployeeEntity> EMPLOYEE = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "employee"),
            EntityType.Builder.create(EmployeeEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("employee"));

    public static final EntityType<BeggarEntity> BEGGAR = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "beggar"),
            EntityType.Builder.create(BeggarEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("beggar"));
    public static final EntityType<BlackMarketEntity> BLACK_MARKET = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "black_market"),
            EntityType.Builder.create(BlackMarketEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("black_market"));
    public static final EntityType<FurnitureSellerEntity> FURNITURE_SELLER = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "furniture_seller"),
            EntityType.Builder.create(FurnitureSellerEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("furniture_seller"));
    // 1.0 onboarding one-time visitors (onboarding/OnboardingVisits)
    public static final EntityType<UniqueCollectorEntity> UNIQUE_COLLECTOR = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "unique_collector"),
            EntityType.Builder.create(UniqueCollectorEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("unique_collector"));
    public static final EntityType<RivalTaunterEntity> RIVAL = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "rival"),
            EntityType.Builder.create(RivalTaunterEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("rival"));
    // 1.0 Wandering Duelist (world/duelist/WanderingDuelists)
    public static final EntityType<WanderingDuelistEntity> WANDERING_DUELIST = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(TcgShop.MOD_ID, "wandering_duelist"),
            EntityType.Builder.create(WanderingDuelistEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10)
                    .build("wandering_duelist"));

    private TcgEntities() {}

    public static void init() {
        FabricDefaultAttributeRegistry.register(SCALPER, ScalperEntity.createAttributes());
        SpawnRestriction.register(SCALPER, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
        // uncommon: spawns at night or in dark areas of the Overworld
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, SCALPER, 3, 1, 1);
        FabricDefaultAttributeRegistry.register(WHOLESALER, WholesalerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TRADER, TraderEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CUSTOMER, CustomerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(EMPLOYEE, EmployeeEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BEGGAR, BeggarEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BLACK_MARKET, BlackMarketEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FURNITURE_SELLER, FurnitureSellerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(UNIQUE_COLLECTOR, UniqueCollectorEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RIVAL, RivalTaunterEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WANDERING_DUELIST, WanderingDuelistEntity.createAttributes());
        // Furniture seller: visits players who have a shop (phase 10500 of every half day)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(FurnitureSellerEntity::spawner);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(TcgEntities::wholesalerSpawner);
        // regular Minecraft monsters also go after Scalpers (and they fight back)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof HostileEntity h && !(h instanceof ScalperEntity) && !(h instanceof net.minecraft.entity.mob.Angerable)
                    && !(h instanceof net.minecraft.entity.mob.AbstractPiglinEntity) && !(h instanceof net.minecraft.entity.mob.WardenEntity))
                ((dev.tcgshop.mixin.MobEntityAccessor) h).tcgshop$targetSelector()
                        .add(3, new net.minecraft.entity.ai.goal.ActiveTargetGoal<>(h, ScalperEntity.class, true));
        });
    }

    /**
     * Every half Minecraft day, 35% chance that a mysterious Wholesaler arrives near a random player; halfway
     * through, 40% chance that a wandering Collector arrives (trades cards and buys the one it likes).
     */
    private static void wholesalerSpawner(net.minecraft.server.world.ServerWorld world) {
        if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;
        long phase = world.getTime() % 12000;
        if (phase == 3000) blackMarketSpawner(world);
        if (phase == 9000) beggarSpawner(world);
        boolean trader = phase == 0;
        if (phase != 6000 && !trader) return;
        var players = world.getPlayers();
        if (players.isEmpty() || world.random.nextFloat() > (trader ? 0.40f : 0.35f)) return;
        var p = players.get(world.random.nextInt(players.size()));
        Class<? extends net.minecraft.entity.Entity> cls = trader ? TraderEntity.class : WholesalerEntity.class;
        if (!world.getEntitiesByClass(cls, p.getBoundingBox().expand(96), e -> true).isEmpty()) return;
        for (int attempt = 0; attempt < 10; attempt++) {
            double ang = world.random.nextDouble() * Math.PI * 2;
            int dist = 12 + world.random.nextInt(14);
            int x = (int) (p.getX() + Math.cos(ang) * dist), z = (int) (p.getZ() + Math.sin(ang) * dist);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            var pos = new net.minecraft.util.math.BlockPos(x, y, z);
            if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) || !world.isAir(pos) || !world.isAir(pos.up())) continue;
            net.minecraft.entity.mob.PathAwareEntity w = trader ? TRADER.create(world) : WHOLESALER.create(world);
            if (w == null) return;
            w.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360f, 0f);
            world.spawnEntity(w);
            p.sendMessage(net.minecraft.text.Text.translatable(trader ? "tcgshop.trader.arrived" : "tcgshop.wholesaler.arrived")
                    .formatted(net.minecraft.util.Formatting.GOLD), false);
            return;
        }
    }

    /** Finds a free ground spot at dist blocks (or null). */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.util.math.BlockPos groundNear(net.minecraft.server.world.ServerWorld world, net.minecraft.util.math.Vec3d c, int minD, int maxD) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double ang = world.random.nextDouble() * Math.PI * 2;
            int dist = minD + world.random.nextInt(Math.max(1, maxD - minD));
            int x = (int) Math.floor(c.x + Math.cos(ang) * dist), z = (int) Math.floor(c.z + Math.sin(ang) * dist);
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            var pos = new net.minecraft.util.math.BlockPos(x, y, z);
            if (Math.abs(y - c.y) > 12) continue;
            if (world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) && world.isAir(pos) && world.isAir(pos.up())) return pos;
        }
        return null;
    }

    /** Card beggar: sometimes hangs around an online player's shop (or near the player if they have none). */
    private static void beggarSpawner(net.minecraft.server.world.ServerWorld world) {
        var players = world.getPlayers();
        if (players.isEmpty() || world.random.nextDouble() > dev.tcgshop.EconomyConfig.INSTANCE.beggarChance) return;
        var p = players.get(world.random.nextInt(players.size()));
        if (!world.getEntitiesByClass(BeggarEntity.class, p.getBoundingBox().expand(96), e -> true).isEmpty()) return;
        net.minecraft.util.math.Vec3d center = p.getPos();
        for (dev.tcgshop.shop.ShopZones.Zone z : dev.tcgshop.shop.ShopZones.get(world).all())
            if (z.owner.equals(p.getUuid()) && z.core.toCenterPos().squaredDistanceTo(p.getPos()) < 96 * 96) { center = z.core.toCenterPos(); break; }
        var pos = groundNear(world, center, 8, 20);
        if (pos == null) return;
        BeggarEntity b = BEGGAR.create(world);
        if (b == null) return;
        b.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
        world.spawnEntity(b);
        p.sendMessage(net.minecraft.text.Text.translatable("tcgshop.beggar.arrived").formatted(net.minecraft.util.Formatting.GRAY), false);
    }

    /** Black marketeer: extremely rare, appears near a player and only tells that player. */
    private static void blackMarketSpawner(net.minecraft.server.world.ServerWorld world) {
        var players = world.getPlayers();
        if (players.isEmpty() || world.random.nextDouble() > dev.tcgshop.EconomyConfig.INSTANCE.blackMarketChance) return;
        var p = players.get(world.random.nextInt(players.size()));
        if (!world.getEntitiesByClass(BlackMarketEntity.class, p.getBoundingBox().expand(128), e -> true).isEmpty()) return;
        var pos = groundNear(world, p.getPos(), 6, 12);
        if (pos == null) return;
        BlackMarketEntity m = BLACK_MARKET.create(world);
        if (m == null) return;
        m.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
        m.setForPlayer(p.getUuid());
        m.rollOffer();
        world.spawnEntity(m);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.02);
        p.sendMessage(net.minecraft.text.Text.translatable("tcgshop.bm.arrived").formatted(net.minecraft.util.Formatting.DARK_PURPLE, net.minecraft.util.Formatting.ITALIC), false);
        p.playSoundToPlayer(net.minecraft.sound.SoundEvents.AMBIENT_CAVE.value(), net.minecraft.sound.SoundCategory.AMBIENT, 1f, 0.8f);
    }

    /** For testing: force the spawn. */
    public static void debugSpawn(net.minecraft.server.world.ServerWorld world, boolean beggar) {
        if (beggar) beggarSpawner(world); else blackMarketSpawner(world);
    }
}
