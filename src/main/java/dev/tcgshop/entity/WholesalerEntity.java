package dev.tcgshop.entity;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mysterious wholesaler: wandering merchant that shows up now and then near players.
 * Sells booster boxes from 8 random sets (at wholesale price, cheaper than the phone), shop blocks
 * of every tier, furniture and tools. Leaves after two Minecraft days.
 */
public class WholesalerEntity extends PathAwareEntity implements TcgNpc {
    private final List<String> boxSets = new ArrayList<>();
    private int lifeTicks = 48_000;
    private final java.util.Set<java.util.UUID> tipped = new java.util.HashSet<>();

    public WholesalerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setInvulnerable(false);
    }

    private static final net.minecraft.entity.data.TrackedData<String> TITLE =
            net.minecraft.entity.data.DataTracker.registerData(WholesalerEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.STRING);
    private static final net.minecraft.entity.data.TrackedData<Integer> SKIN =
            net.minecraft.entity.data.DataTracker.registerData(WholesalerEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TITLE, "");
        builder.add(SKIN, 1);
    }

    @Override public String roleKey() { return "tcgshop.role.wholesaler"; }
    @Override public String npcTitle() { return dataTracker.get(TITLE); }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return EMOTE_NONE; }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.5));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    private void rollStock() {
        if (!boxSets.isEmpty()) return;
        List<String> sets = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) sets.add(CardInfo.setId(id));
        Collections.shuffle(sets, new java.util.Random(getUuid().getLeastSignificantBits()));
        boxSets.addAll(sets.subList(0, Math.min(8, sets.size())));
    }

    public List<ItemStack> goods() {
        rollStock();
        return ShopServer.wholesaleGoods(boxSets, net.minecraft.util.math.random.Random.create(getUuid().getMostSignificantBits()));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity sp) {
            playSound(SoundEvents.ENTITY_WANDERING_TRADER_YES, 1f, 0.8f);
            ShopServer.openStore(sp, getId(), goods(), ShopServer.WHOLESALE_FACTOR);
            // tip (once per player): emeralds are exchanged here for CobbleDollars
            if (tipped.add(sp.getUuid())) sp.sendMessage(net.minecraft.text.Text.literal("[" + getName().getString() + "] ").formatted(net.minecraft.util.Formatting.GOLD)
                    .append(net.minecraft.text.Text.translatable("tcgshop.wholesaler.tip_emeralds",
                            dev.tcgshop.pricing.PriceService.format(dev.tcgshop.shop.CardSales.emeraldPrice())).formatted(net.minecraft.util.Formatting.YELLOW)), false);
        }
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!hasCustomName()) {
            setCustomName(net.minecraft.text.Text.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            dataTracker.set(TITLE, NpcIdentity.randomTitle(random, 0.3f));
            dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (--lifeTicks <= 0) discard();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    /**
     * 1.0 onboarding: extra visits for players who don't own a shop yet (registered by OnboardingVisits). On top of the
     * normal visit (TcgEntities.wholesalerSpawner, one random player every half day), every half Minecraft day each
     * shopless player in the Overworld gets two extra rolls (phases 1500 and 7500, away from the other merchants'
     * phases) with EconomyConfig.wholesalerNoShopChance each, so they can buy their first shop block sooner.
     */
    public static void newcomerSpawner(net.minecraft.server.world.ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        long phase = world.getTime() % 12000;
        if (phase != 1500 && phase != 7500) return;
        double chance = dev.tcgshop.EconomyConfig.INSTANCE.wholesalerNoShopChance;
        if (chance <= 0) return;
        for (ServerPlayerEntity p : List.copyOf(world.getPlayers())) {
            if (p.isSpectator() || world.random.nextDouble() >= chance) continue;
            if (dev.tcgshop.onboarding.Onboarding.ownedShops(p) > 0) continue;
            if (!world.getEntitiesByClass(WholesalerEntity.class, p.getBoundingBox().expand(96), e -> true).isEmpty()) continue;
            for (int attempt = 0; attempt < 10; attempt++) {
                double ang = world.random.nextDouble() * Math.PI * 2;
                int dist = 12 + world.random.nextInt(14);
                int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
                int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                var pos = new net.minecraft.util.math.BlockPos(x, y, z);
                if (Math.abs(y - p.getBlockY()) > 16) continue;
                if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) || !world.isAir(pos) || !world.isAir(pos.up())) continue;
                WholesalerEntity w = TcgEntities.WHOLESALER.create(world);
                if (w == null) break;
                w.refreshPositionAndAngles(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360f, 0f);
                world.spawnEntity(w);
                p.sendMessage(net.minecraft.text.Text.translatable("tcgshop.wholesaler.arrived").formatted(net.minecraft.util.Formatting.GOLD), false);
                p.sendMessage(net.minecraft.text.Text.translatable("tcgshop.onb.wholesaler_newcomer").formatted(net.minecraft.util.Formatting.GRAY), false);
                break;
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        NbtList l = new NbtList();
        for (String s : boxSets) l.add(NbtString.of(s));
        nbt.put("BoxSets", l);
        nbt.putInt("Life", lifeTicks);
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        boxSets.clear();
        NbtList l = nbt.getList("BoxSets", 8);
        for (int i = 0; i < l.size(); i++) boxSets.add(l.getString(i));
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        dataTracker.set(TITLE, nbt.getString("NpcTitle"));
        dataTracker.set(SKIN, nbt.contains("NpcSkin") ? nbt.getInt("NpcSkin") : 1);
    }
}
