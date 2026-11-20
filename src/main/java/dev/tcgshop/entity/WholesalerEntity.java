package dev.tcgshop.entity;

import dev.tcgshop.card.CardInfo;
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
 * Mayorista misterioso: vendedor ambulante que aparece de vez en cuando cerca de los jugadores.
 * Vende cajas de sobres de 8 sets al azar (a precio de mayorista, más barato que el teléfono), los bloques de
 * tienda de todos los niveles, muebles y herramientas. Se marcha pasados dos días de Minecraft.
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
            // consejo (una vez por jugador): las esmeraldas se cambian aquí por CobbleDollars
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
