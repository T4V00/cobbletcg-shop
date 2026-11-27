package dev.tcgshop.entity;

import dev.masterball.api.platform.Net;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.npc.EngineerGoods;
import dev.tcgshop.npc.NpcConfig;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopZones;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 1.1: the CREATive Engineer ("Ingeniero CREATivo"). A wandering salesman who only exists when Create is installed: he
 * sells Create materials by the stack (about $3,000 per 64) and mid-level machines and tools ($50,000 and up), see
 * npc/EngineerGoods. He visits shop owners and players near villages ({@link #spawner}) and leaves after
 * NpcConfig.engineerStayTicks. Without Create he never spawns, and one that is loaded anyway (the mod was removed) leaves.
 */
public class EngineerEntity extends PathfinderMob implements TcgNpc {
    private int lifeTicks = -1;

    private static final net.minecraft.network.syncher.EntityDataAccessor<String> TITLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(EngineerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SKIN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(EngineerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    public EngineerEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TITLE, "");
        builder.define(SKIN, 1);
    }

    @Override public String roleKey() { return "tcgshop.role.engineer"; }
    @Override public String npcTitle() { return entityData.get(TITLE); }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return EMOTE_MONEY; }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            playSound(SoundEvents.ANVIL_USE, 0.4f, 1.6f);
            getNavigation().stop();
            open(sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Stamp of the catalog each player's window shows: a buy is only honored against that same catalog. */
    private final java.util.Map<java.util.UUID, Integer> shown = new java.util.HashMap<>();

    /** Fingerprint of a catalog (order, item, count and price of every offer; pure, the selftest checks it). */
    public static int catalogStamp(List<EngineerGoods.Offer> cat) {
        int h = 1;
        for (EngineerGoods.Offer o : cat) {
            h = 31 * h + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(o.stack().getItem()).hashCode();
            h = 31 * h + o.stack().getCount();
            h = 31 * h + Long.hashCode(o.price());
        }
        return h;
    }

    /** Sends his catalog (UiPayloads.Open "engineer": entity id, goods, prices). */
    public void open(ServerPlayer p) {
        List<EngineerGoods.Offer> cat = EngineerGoods.catalog();
        if (cat.isEmpty()) {
            p.displayClientMessage(Component.translatable("tcgshop.engineer.no_create").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        shown.put(p.getUUID(), catalogStamp(cat));
        CompoundTag d = new CompoundTag();
        d.putInt("id", getId());
        ListTag goods = new ListTag();
        long[] prices = new long[cat.size()];
        for (int i = 0; i < cat.size(); i++) {
            goods.add(cat.get(i).stack().save(registryAccess()));
            prices[i] = cat.get(i).price();
        }
        d.put("goods", goods);
        d.putLongArray("prices", prices);
        var payload = new dev.tcgshop.network.UiPayloads.Open("engineer", d);
        if (Net.INSTANCE.canSend(p, payload.type())) Net.INSTANCE.send(p, payload);
    }

    /** StoreBuyPayload from his window: qty times the product at that index (a stack counts as one). */
    public void buy(ServerPlayer p, int index, int qtyRaw) {
        if (distanceToSqr(p) > 10 * 10 || !isAlive()) return;
        List<EngineerGoods.Offer> cat = EngineerGoods.catalog();
        Integer seen = shown.get(p.getUUID());
        if (seen == null || seen != catalogStamp(cat)) { // /tcgshop reload changed goods or prices since the window opened
            p.displayClientMessage(Component.translatable("tcgshop.engineer.catalog_changed").withStyle(ChatFormatting.YELLOW), false);
            if (!cat.isEmpty()) open(p); // fresh prices
            return;
        }
        if (index < 0 || index >= cat.size()) return;
        int qty = Math.max(1, Math.min(64, qtyRaw));
        EngineerGoods.Offer o = cat.get(index);
        long total;
        try { total = Math.multiplyExact(o.price(), qty); } catch (ArithmeticException e) { return; }
        if (!CobbleDollarsBridge.isAvailable()) {
            p.displayClientMessage(Component.translatable("tcgshop.sell.no_bank").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(p, total)) {
            p.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(total)).withStyle(ChatFormatting.RED), true);
            dev.tcgshop.phone.PhoneStore.sendResult(p, dev.tcgshop.phone.PhoneStore.NO_MONEY, getId(), o.stack(), qty, total, 0);
            return;
        }
        for (int i = 0; i < qty; i++) p.getInventory().placeItemBackInInventory(o.stack().copy());
        ShopZones.get(p.serverLevel()).addCost(p.getUUID(), total);
        p.displayClientMessage(Component.translatable("tcgshop.engineer.bought", qty, o.stack().getCount(), o.stack().getHoverName(),
                Component.literal(PriceService.format(total)).withStyle(ChatFormatting.GOLD)), false);
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.NEUTRAL, 0.8f, 1.1f);
        dev.tcgshop.phone.PhoneStore.sendResult(p, dev.tcgshop.phone.PhoneStore.BOUGHT, getId(), o.stack(), qty, total, 0);
        if (random.nextInt(3) == 0) NpcLines.speak(this, Component.translatable("tcgshop.engineer.line.sold." + random.nextInt(LINES_SOLD)));
    }

    static final int LINES_HELLO = 6, LINES_SOLD = 5;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (lifeTicks < 0) lifeTicks = NpcConfig.INSTANCE.engineerStayTicks;
        if (!hasCustomName()) {
            setCustomName(Component.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            entityData.set(TITLE, NpcIdentity.randomTitle(random, 0.2f));
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (tickCount % 600 == 300 && random.nextInt(3) == 0 && level().getNearestPlayer(this, 8) != null)
            NpcLines.speak(this, Component.translatable("tcgshop.engineer.line.hello." + random.nextInt(LINES_HELLO)));
        if (tickCount % 200 == 0 && !EngineerGoods.createInstalled()) lifeTicks = 0; // Create was removed: nothing to sell
        if (--lifeTicks <= 0) {
            if (level() instanceof ServerLevel w)
                w.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 16, 0.3, 0.6, 0.3, 0.02);
            discard();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Life", lifeTicks);
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        entityData.set(TITLE, nbt.getString("NpcTitle"));
        entityData.set(SKIN, nbt.contains("NpcSkin") ? nbt.getInt("NpcSkin") : 1);
    }

    // ------------------------------------------------------------------ visits

    /**
     * Every half Minecraft day (phase 4500, away from the other merchants), with Create installed: one random online
     * player rolls engineerChance if they own a shop or stand near a village, engineerWanderChance otherwise. He arrives
     * at the player's nearest own shop (within 96 blocks) or 10-24 blocks from the player. Any dimension with players.
     */
    public static void spawner(ServerLevel world) {
        if (world.getGameTime() % 12000 != 4500) return;
        NpcConfig c = NpcConfig.INSTANCE;
        if (!c.engineerEnabled || world.players().isEmpty() || !EngineerGoods.createInstalled()) return;
        ServerPlayer p = world.players().get(world.random.nextInt(world.players().size()));
        if (p.isSpectator()) return;
        boolean shop = !ShopZones.ownedBy(world.getServer(), p.getUUID()).isEmpty();
        boolean village = nearVillage(world, p.blockPosition());
        if (world.random.nextDouble() >= (shop || village ? c.engineerChance : c.engineerWanderChance)) return;
        spawnFor(world, p);
    }

    /** Brings him to that player (their nearest shop within 96 blocks, else next to them). Null = he came; else the reason (lang key). */
    @org.jetbrains.annotations.Nullable
    public static String spawnFor(ServerLevel world, ServerPlayer p) {
        if (!EngineerGoods.createInstalled()) return "tcgshop.engineer.no_create";
        if (!world.getEntitiesOfClass(EngineerEntity.class, p.getBoundingBox().inflate(96), e -> e.isAlive()).isEmpty()) return "tcgshop.engineer.already_near";
        net.minecraft.world.phys.Vec3 center = p.position();
        double best = 96 * 96;
        boolean atShop = false;
        for (ShopZones.Zone z : ShopZones.get(world).all()) {
            if (!z.owner.equals(p.getUUID())) continue;
            double dd = z.core.getCenter().distanceToSqr(p.position());
            if (dd < best) { best = dd; center = z.core.getCenter(); atShop = true; }
        }
        BlockPos pos = ground(world, center, atShop ? 6 : 10, 24);
        if (pos == null) return "tcgshop.engineer.no_room";
        EngineerEntity e = TcgEntities.ENGINEER.create(world);
        if (e == null) return "tcgshop.engineer.no_room";
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
        world.addFreshEntity(e);
        world.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.02);
        p.displayClientMessage(Component.translatable("tcgshop.engineer.arrived").withStyle(ChatFormatting.GOLD), false);
        return null;
    }

    private static boolean nearVillage(ServerLevel w, BlockPos pos) {
        try {
            return w.getPoiManager().findClosest(h -> h.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING), pos, 96,
                    net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    @org.jetbrains.annotations.Nullable
    private static BlockPos ground(ServerLevel world, net.minecraft.world.phys.Vec3 c, int minD, int maxD) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double ang = world.random.nextDouble() * Math.PI * 2;
            int dist = minD + world.random.nextInt(Math.max(1, maxD - minD));
            int x = (int) Math.floor(c.x + Math.cos(ang) * dist), z = (int) Math.floor(c.z + Math.sin(ang) * dist);
            if (!world.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (Math.abs(y - c.y) > 16) continue;
            if (ShopZones.isShopRoof(world, pos)) continue; // never on a shop's roof
            if (world.getBlockState(pos.below()).isRedstoneConductor(world, pos.below()) && world.isEmptyBlock(pos) && world.isEmptyBlock(pos.above())) return pos;
        }
        return null;
    }
}
