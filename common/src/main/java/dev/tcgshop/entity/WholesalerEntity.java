package dev.tcgshop.entity;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopTier;
import dev.tcgshop.shop.TcgBlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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

/**
 * Mysterious wholesaler: wandering merchant that shows up now and then near players.
 * Sells booster boxes from 8 random sets (at wholesale price, cheaper than the phone), shop blocks
 * of every tier, furniture and tools. Leaves after two Minecraft days.
 */
public class WholesalerEntity extends PathfinderMob implements TcgNpc {
    private final List<String> boxSets = new ArrayList<>();
    private int lifeTicks = 48_000;
    private final java.util.Set<java.util.UUID> tipped = new java.util.HashSet<>();

    public WholesalerEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
        setInvulnerable(false);
    }

    private static final net.minecraft.network.syncher.EntityDataAccessor<String> TITLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(WholesalerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SKIN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(WholesalerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TITLE, "");
        builder.define(SKIN, 1);
    }

    @Override public String roleKey() { return "tcgshop.role.wholesaler"; }
    @Override public String npcTitle() { return entityData.get(TITLE); }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return EMOTE_NONE; }

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

    private void rollStock() {
        if (!boxSets.isEmpty()) return;
        List<String> sets = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) sets.add(CardInfo.setId(id));
        Collections.shuffle(sets, new java.util.Random(getUUID().getLeastSignificantBits()));
        boxSets.addAll(sets.subList(0, Math.min(8, sets.size())));
    }

    public List<ItemStack> goods() {
        rollStock();
        return ShopServer.wholesaleGoods(boxSets, net.minecraft.util.RandomSource.create(getUUID().getMostSignificantBits()));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            playSound(SoundEvents.WANDERING_TRADER_YES, 1f, 0.8f);
            ShopServer.openStore(sp, getId(), goods(), ShopServer.WHOLESALE_FACTOR);
            // tip (once per player): emeralds are exchanged here for CobbleDollars
            if (tipped.add(sp.getUUID())) sp.displayClientMessage(net.minecraft.network.chat.Component.literal("[" + getName().getString() + "] ").withStyle(net.minecraft.ChatFormatting.GOLD)
                    .append(net.minecraft.network.chat.Component.translatable("tcgshop.wholesaler.tip_emeralds",
                            dev.tcgshop.pricing.PriceService.format(dev.tcgshop.shop.CardSales.emeraldPrice())).withStyle(net.minecraft.ChatFormatting.YELLOW)), false);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!hasCustomName()) {
            setCustomName(net.minecraft.network.chat.Component.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            entityData.set(TITLE, NpcIdentity.randomTitle(random, 0.3f));
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (--lifeTicks <= 0) discard();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    /**
     * 1.0 onboarding: extra visits for players who don't own a shop yet (registered by OnboardingVisits). On top of the
     * normal visit (TcgEntities.wholesalerSpawner, one random player every half day), every half Minecraft day each
     * shopless player in the Overworld gets two extra rolls (phases 1500 and 7500, away from the other merchants'
     * phases) with EconomyConfig.wholesalerNoShopChance each, so they can buy their first shop block sooner.
     */
    public static void newcomerSpawner(net.minecraft.server.level.ServerLevel world) {
        if (world.dimension() != Level.OVERWORLD) return;
        long phase = world.getGameTime() % 12000;
        if (phase != 1500 && phase != 7500) return;
        double chance = dev.tcgshop.EconomyConfig.INSTANCE.wholesalerNoShopChance;
        if (chance <= 0) return;
        for (ServerPlayer p : List.copyOf(world.players())) {
            if (p.isSpectator() || world.random.nextDouble() >= chance) continue;
            if (dev.tcgshop.onboarding.Onboarding.ownedShops(p) > 0) continue;
            if (!world.getEntitiesOfClass(WholesalerEntity.class, p.getBoundingBox().inflate(96), e -> true).isEmpty()) continue;
            for (int attempt = 0; attempt < 10; attempt++) {
                double ang = world.random.nextDouble() * Math.PI * 2;
                int dist = 12 + world.random.nextInt(14);
                int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
                int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                var pos = new net.minecraft.core.BlockPos(x, y, z);
                if (Math.abs(y - p.getBlockY()) > 16) continue;
                if (!world.getBlockState(pos.below()).isRedstoneConductor(world, pos.below()) || !world.isEmptyBlock(pos) || !world.isEmptyBlock(pos.above())) continue;
                if (dev.tcgshop.shop.ShopZones.isShopRoof(world, pos)) continue; // 1.1: never on a shop's roof
                WholesalerEntity w = TcgEntities.WHOLESALER.create(world);
                if (w == null) break;
                w.moveTo(x + 0.5, y, z + 0.5, world.random.nextFloat() * 360f, 0f);
                world.addFreshEntity(w);
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.wholesaler.arrived").withStyle(net.minecraft.ChatFormatting.GOLD), false);
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("tcgshop.onb.wholesaler_newcomer").withStyle(net.minecraft.ChatFormatting.GRAY), false);
                break;
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ListTag l = new ListTag();
        for (String s : boxSets) l.add(StringTag.valueOf(s));
        nbt.put("BoxSets", l);
        nbt.putInt("Life", lifeTicks);
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        boxSets.clear();
        ListTag l = nbt.getList("BoxSets", 8);
        for (int i = 0; i < l.size(); i++) boxSets.add(l.getString(i));
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        entityData.set(TITLE, nbt.getString("NpcTitle"));
        entityData.set(SKIN, nbt.contains("NpcSkin") ? nbt.getInt("NpcSkin") : 1);
    }
}
