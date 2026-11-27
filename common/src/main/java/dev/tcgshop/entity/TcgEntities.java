package dev.tcgshop.entity;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.TcgShop;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TcgEntities {
    public static final EntityType<ScalperEntity> SCALPER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "scalper"),
            EntityType.Builder.of(ScalperEntity::new, MobCategory.MONSTER).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("scalper"));

    public static final EntityType<WholesalerEntity> WHOLESALER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "wholesaler"),
            EntityType.Builder.of(WholesalerEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("wholesaler"));

    public static final EntityType<TraderEntity> TRADER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "trader"),
            EntityType.Builder.of(TraderEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("trader"));

    public static final EntityType<CustomerEntity> CUSTOMER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "customer"),
            EntityType.Builder.of(CustomerEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("customer"));
    public static final EntityType<EmployeeEntity> EMPLOYEE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "employee"),
            EntityType.Builder.of(EmployeeEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("employee"));

    public static final EntityType<BeggarEntity> BEGGAR = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "beggar"),
            EntityType.Builder.of(BeggarEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("beggar"));
    public static final EntityType<BlackMarketEntity> BLACK_MARKET = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "black_market"),
            EntityType.Builder.of(BlackMarketEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("black_market"));
    public static final EntityType<FurnitureSellerEntity> FURNITURE_SELLER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "furniture_seller"),
            EntityType.Builder.of(FurnitureSellerEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("furniture_seller"));
    // 1.0 onboarding one-time visitors (onboarding/OnboardingVisits)
    public static final EntityType<UniqueCollectorEntity> UNIQUE_COLLECTOR = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "unique_collector"),
            EntityType.Builder.of(UniqueCollectorEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("unique_collector"));
    public static final EntityType<RivalTaunterEntity> RIVAL = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "rival"),
            EntityType.Builder.of(RivalTaunterEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("rival"));
    // 1.0 Wandering Duelist (world/duelist/WanderingDuelists)
    public static final EntityType<WanderingDuelistEntity> WANDERING_DUELIST = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "wandering_duelist"),
            EntityType.Builder.of(WanderingDuelistEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("wandering_duelist"));

    // 1.1 NPC update: the Mayor (npc/TaxOffice) and the CREATive Engineer (only with Create)
    public static final EntityType<MayorEntity> MAYOR = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "mayor"),
            EntityType.Builder.of(MayorEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("mayor"));
    public static final EntityType<EngineerEntity> ENGINEER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "engineer"),
            EntityType.Builder.of(EngineerEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10)
                    .build("engineer"));

    private TcgEntities() {}

    public static void init() {
        Platform.INSTANCE.entityAttributes(SCALPER, ScalperEntity.createAttributes());
        // 1.1: never on a shop's roof
        Platform.INSTANCE.spawnPlacement(SCALPER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> Monster.checkMonsterSpawnRules(type, level, reason, pos, random)
                        && !dev.tcgshop.shop.ShopZones.isShopRoof(level.getLevel(), pos));
        // uncommon: spawns at night or in dark areas of the Overworld
        Platform.INSTANCE.addSpawn(Platform.BiomeFilter.overworld(), MobCategory.MONSTER, SCALPER, 3, 1, 1);
        Platform.INSTANCE.entityAttributes(WHOLESALER, WholesalerEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(TRADER, TraderEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(CUSTOMER, CustomerEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(EMPLOYEE, EmployeeEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(BEGGAR, BeggarEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(BLACK_MARKET, BlackMarketEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(FURNITURE_SELLER, FurnitureSellerEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(UNIQUE_COLLECTOR, UniqueCollectorEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(RIVAL, RivalTaunterEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(WANDERING_DUELIST, WanderingDuelistEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(MAYOR, MayorEntity.createAttributes());
        Platform.INSTANCE.entityAttributes(ENGINEER, EngineerEntity.createAttributes());
        ServerEvents.LEVEL_TICK_END.register(EngineerEntity::spawner); // 1.1: CREATive Engineer visits (Create installed only)
        // Furniture seller: visits players who have a shop (phase 10500 of every half day)
        ServerEvents.LEVEL_TICK_END.register(FurnitureSellerEntity::spawner);
        ServerEvents.LEVEL_TICK_END.register(TcgEntities::wholesalerSpawner);
        // 1.1: the Wandering Collector also visits open shops while their owner is around (any dimension)
        ServerEvents.LEVEL_TICK_END.register(TcgEntities::shopCollectorSpawner);
        // 1.1: from Stone tier, Scalpers walk into open shops to sell overpriced packs while the owner is inside
        ServerEvents.LEVEL_TICK_END.register(ScalperEntity::shopVisitSpawner);
        // regular Minecraft monsters also go after Scalpers (and they fight back)
        ServerEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Monster h && !(h instanceof ScalperEntity) && !(h instanceof net.minecraft.world.entity.NeutralMob)
                    && !(h instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin) && !(h instanceof net.minecraft.world.entity.monster.warden.Warden))
                ((dev.tcgshop.mixin.MobEntityAccessor) h).tcgshop$targetSelector()
                        .addGoal(3, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(h, ScalperEntity.class, true));
        });
    }

    /**
     * Every half Minecraft day, 35% chance that a mysterious Wholesaler arrives near a random player; halfway
     * through, 40% chance that a wandering Collector arrives (trades cards and buys the one it likes).
     */
    private static void wholesalerSpawner(net.minecraft.server.level.ServerLevel world) {
        if (world.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        long phase = world.getGameTime() % 12000;
        if (phase == 3000) blackMarketSpawner(world);
        if (phase == 9000) beggarSpawner(world);
        boolean trader = phase == 0;
        if (phase != 6000 && !trader) return;
        var players = world.players();
        if (players.isEmpty() || world.random.nextFloat() > (trader ? 0.40f : 0.35f)) return;
        var p = players.get(world.random.nextInt(players.size()));
        Class<? extends net.minecraft.world.entity.Entity> cls = trader ? TraderEntity.class : WholesalerEntity.class;
        if (!world.getEntitiesOfClass(cls, p.getBoundingBox().inflate(96), e -> true).isEmpty()) return;
        for (int attempt = 0; attempt < 10; attempt++) {
            double ang = world.random.nextDouble() * Math.PI * 2;
            int dist = 12 + world.random.nextInt(14);
            int x = (int) (p.getX() + Math.cos(ang) * dist), z = (int) (p.getZ() + Math.sin(ang) * dist);
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var pos = new net.minecraft.core.BlockPos(x, y, z);
            if (!world.getBlockState(pos.below()).isRedstoneConductor(world, pos.below()) || !world.isEmptyBlock(pos) || !world.isEmptyBlock(pos.above())) continue;
            if (dev.tcgshop.shop.ShopZones.isShopRoof(world, pos)) continue; // 1.1: never on a shop's roof
            net.minecraft.world.entity.PathfinderMob w = trader ? TRADER.create(world) : WHOLESALER.create(world);
            if (w == null) return;
            w.moveTo(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360f, 0f);
            world.addFreshEntity(w);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(trader ? "tcgshop.trader.arrived" : "tcgshop.wholesaler.arrived")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            return;
        }
    }

    // ------------------------------------------------------------------ 1.1: collectors visit shops

    /** How often each shop rolls for a collector visit (ticks). The half-day chance is spread over these checks. */
    private static final int SHOP_VISIT_CHECK = 1200;
    /** Minimum ticks between two visits to the same shop (in memory: a collector killed or gone is not replaced at once). */
    private static final int SHOP_VISIT_GAP = 12_000;
    private static final java.util.Map<java.util.UUID, Long> LAST_SHOP_VISIT = new java.util.HashMap<>();

    /**
     * Every minute, each OPEN shop with displays whose owner is within 96 blocks rolls
     * {@code shopCollectorVisitChance} (per half day, spread over the checks): a Wandering Collector walks to it and lingers
     * there for his normal stay. The exploration visits (wholesalerSpawner) are unchanged.
     */
    private static void shopCollectorSpawner(net.minecraft.server.level.ServerLevel world) {
        long t = world.getGameTime();
        if (t % SHOP_VISIT_CHECK != 700) return;
        double chance = dev.tcgshop.EconomyConfig.INSTANCE.shopCollectorVisitChance;
        if (chance <= 0 || world.players().isEmpty()) return;
        double perCheck = Math.min(1, chance * SHOP_VISIT_CHECK / 12_000.0);
        for (dev.tcgshop.shop.ShopZones.Zone z : new java.util.ArrayList<>(dev.tcgshop.shop.ShopZones.get(world).all())) {
            if (z.closed || z.displays <= 0) continue;
            if (world.random.nextDouble() >= perCheck) continue;
            trySpawnAtShop(world, z, false);
        }
    }

    /**
     * Brings a Wandering Collector to this shop. Needs: shop open and active (not dormant), the owner online in this
     * dimension within 96 blocks, no other collector within 96 blocks of the core, and (unless forced) no visit in the last
     * half day. Returns the collector, or null.
     */
    @org.jetbrains.annotations.Nullable
    public static TraderEntity trySpawnAtShop(net.minecraft.server.level.ServerLevel world, dev.tcgshop.shop.ShopZones.Zone z, boolean force) {
        if (z.closed || z.dormant) return null;
        var owner = world.getServer().getPlayerList().getPlayer(z.owner);
        net.minecraft.world.phys.Vec3 c = z.core.getCenter();
        if (owner == null || owner.level() != world || owner.isSpectator() || owner.distanceToSqr(c) > 96 * 96) return null;
        long t = world.getGameTime();
        Long last = LAST_SHOP_VISIT.get(z.id);
        if (!force && last != null && t - last >= 0 && t - last < SHOP_VISIT_GAP) return null;
        if (!world.hasChunkAt(z.core)) return null;
        if (!world.getEntitiesOfClass(TraderEntity.class, net.minecraft.world.phys.AABB.ofSize(c, 192, 192, 192), e -> e.isAlive()).isEmpty()) return null;
        net.minecraft.world.phys.Vec3 pos = dev.tcgshop.shop.ShopLife.arrivalSpot(world, z);
        if (pos == null) {
            var g = groundNear(world, c, z.size() / 2 + 3, z.size() / 2 + 12);
            if (g == null) return null;
            pos = net.minecraft.world.phys.Vec3.atBottomCenterOf(g);
        }
        TraderEntity tr = TRADER.create(world);
        if (tr == null) return null;
        tr.moveTo(pos.x, pos.y, pos.z, world.random.nextFloat() * 360f, 0f);
        world.addFreshEntity(tr);
        tr.visitShop(z);
        LAST_SHOP_VISIT.put(z.id, t);
        owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.trader.arrived_shop")
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        TcgShop.LOGGER.info("[shop] a Wandering Collector visits {}'s shop at {}", z.ownerName, z.core.toShortString());
        return tr;
    }

    /** Finds a free ground spot at dist blocks (or null). */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.core.BlockPos groundNear(net.minecraft.server.level.ServerLevel world, net.minecraft.world.phys.Vec3 c, int minD, int maxD) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double ang = world.random.nextDouble() * Math.PI * 2;
            int dist = minD + world.random.nextInt(Math.max(1, maxD - minD));
            int x = (int) Math.floor(c.x + Math.cos(ang) * dist), z = (int) Math.floor(c.z + Math.sin(ang) * dist);
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var pos = new net.minecraft.core.BlockPos(x, y, z);
            if (Math.abs(y - c.y) > 12) continue;
            if (dev.tcgshop.shop.ShopZones.isShopRoof(world, pos)) continue; // 1.1: never on a shop's roof
            if (world.getBlockState(pos.below()).isRedstoneConductor(world, pos.below()) && world.isEmptyBlock(pos) && world.isEmptyBlock(pos.above())) return pos;
        }
        return null;
    }

    /** Card beggar: sometimes hangs around an online player's shop (or near the player if they have none). */
    private static void beggarSpawner(net.minecraft.server.level.ServerLevel world) {
        var players = world.players();
        if (players.isEmpty() || world.random.nextDouble() > dev.tcgshop.EconomyConfig.INSTANCE.beggarChance) return;
        var p = players.get(world.random.nextInt(players.size()));
        if (!world.getEntitiesOfClass(BeggarEntity.class, p.getBoundingBox().inflate(96), e -> true).isEmpty()) return;
        net.minecraft.world.phys.Vec3 center = p.position();
        for (dev.tcgshop.shop.ShopZones.Zone z : dev.tcgshop.shop.ShopZones.get(world).all())
            if (z.owner.equals(p.getUUID()) && z.core.getCenter().distanceToSqr(p.position()) < 96 * 96) { center = z.core.getCenter(); break; }
        var pos = groundNear(world, center, 8, 20);
        if (pos == null) return;
        BeggarEntity b = BEGGAR.create(world);
        if (b == null) return;
        b.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
        world.addFreshEntity(b);
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.beggar.arrived").withStyle(net.minecraft.ChatFormatting.GRAY), false);
    }

    /** Black marketeer: extremely rare, appears near a player and only tells that player. */
    private static void blackMarketSpawner(net.minecraft.server.level.ServerLevel world) {
        var players = world.players();
        if (players.isEmpty() || world.random.nextDouble() > dev.tcgshop.EconomyConfig.INSTANCE.blackMarketChance) return;
        var p = players.get(world.random.nextInt(players.size()));
        if (!world.getEntitiesOfClass(BlackMarketEntity.class, p.getBoundingBox().inflate(128), e -> true).isEmpty()) return;
        var pos = groundNear(world, p.position(), 6, 12);
        if (pos == null) return;
        BlackMarketEntity m = BLACK_MARKET.create(world);
        if (m == null) return;
        m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
        m.setForPlayer(p.getUUID());
        m.rollOffer();
        world.addFreshEntity(m);
        world.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.02);
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.bm.arrived").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), false);
        p.playNotifySound(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, 0.8f);
    }

    /** For testing: force the spawn. */
    public static void debugSpawn(net.minecraft.server.level.ServerLevel world, boolean beggar) {
        if (beggar) beggarSpawner(world); else blackMarketSpawner(world);
    }
}
