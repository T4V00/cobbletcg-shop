package dev.tcgshop.entity;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.compat.CobblemonParty;
import dev.tcgshop.shop.PackLock;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Black marketeer: very rarely appears near a player, hooded, and offers them ONE specific sealed pack in
 * exchange for any Pokémon from their party. The pack is shown before accepting, it must be confirmed, and the Pokémon only
 * disappears if Cobblemon confirms it left the party (and then the pack is handed over). Never leaves you without Pokémon.
 * Vanishes after a few minutes or after the deal.
 */
public class BlackMarketEntity extends PathAwareEntity implements TcgNpc {
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(BlackMarketEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private int lifeTicks = 3600;
    private ItemStack offer = ItemStack.EMPTY;
    private boolean traded;
    private UUID forPlayer;

    public BlackMarketEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, 0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 16f));
    }

    @Override public String roleKey() { return "tcgshop.role.black_market"; }
    @Override public String npcTitle() { return ""; }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return EMOTE_NONE; }
    @Override public boolean wantsAttention() { return !traded; }

    /** Picks the pack it offers: almost always a normal one; sometimes, one from the locked sets. */
    public void rollOffer() {
        List<Item> normal = new ArrayList<>(), rare = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) (PackLock.locked(id) ? rare : normal).add(Registries.ITEM.get(id));
        double rareChance = dev.tcgshop.EconomyConfig.INSTANCE.blackMarketLockedChance;
        List<Item> pool = !rare.isEmpty() && random.nextDouble() < rareChance ? rare : normal;
        if (pool.isEmpty()) pool = rare;
        if (!pool.isEmpty()) offer = new ItemStack(pool.get(random.nextInt(pool.size())));
    }

    public void setForPlayer(UUID id) { forPlayer = id; }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            if (age % 6 == 0) getWorld().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.2, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            return;
        }
        if (!hasCustomName()) {
            setCustomName(Text.translatable("tcgshop.bm.name").formatted(Formatting.DARK_PURPLE));
            setCustomNameVisible(true);
            dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (offer.isEmpty()) rollOffer();
        if (age % 200 == 0 && !traded && getWorld().getClosestPlayer(this, 12) != null && random.nextFloat() < 0.4f) NpcLines.sayNear(this, "bm_ambient");
        if (--lifeTicks <= 0) vanish();
    }

    private void vanish() {
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.02);
            w.playSound(null, getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.NEUTRAL, 1f, 0.7f);
        }
        discard();
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.success(getWorld().isClient);
        if (traded || offer.isEmpty()) { NpcLines.say(this, sp, "bm_leave"); return ActionResult.SUCCESS; }
        List<CobblemonParty.Mon> mons = CobblemonParty.list(sp);
        if (mons.size() <= 1) {
            NpcLines.say(this, sp, "bm_no");
            sp.sendMessage(Text.translatable("tcgshop.bm.need_two").formatted(Formatting.GRAY), true);
            return ActionResult.SUCCESS;
        }
        NpcLines.say(this, sp, "bm_hello");
        NbtCompound d = new NbtCompound();
        d.putInt("id", getId());
        d.put("pack", offer.encode(sp.getRegistryManager()));
        d.putBoolean("rare", PackLock.locked(offer));
        NbtList l = new NbtList();
        for (CobblemonParty.Mon m : mons) {
            NbtCompound c = new NbtCompound();
            c.putUuid("u", m.uuid());
            c.putString("n", Text.Serialization.toJsonString(m.name(), sp.getRegistryManager()));
            c.putInt("l", m.level());
            c.putBoolean("s", m.shiny());
            c.putString("sp", m.species());
            l.add(c);
        }
        d.put("mons", l);
        if (ServerPlayNetworking.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID))
            ServerPlayNetworking.send(sp, new dev.tcgshop.network.UiPayloads.Open("black_market", d));
        return ActionResult.SUCCESS;
    }

    /** The player confirmed: hands over that Pokémon and gets the pack (only if the Pokémon really left the party). */
    public void trade(ServerPlayerEntity p, UUID mon) {
        if (traded || offer.isEmpty() || !isAlive() || squaredDistanceTo(p) > 8 * 8) return;
        traded = true; // blocks double clicks / two players at once
        boolean removed = CobblemonParty.remove(p, mon);
        if (!removed) {
            traded = false;
            p.sendMessage(Text.translatable("tcgshop.bm.failed").formatted(Formatting.RED), false);
            return;
        }
        ItemStack pack = offer.copy();
        offer = ItemStack.EMPTY;
        p.getInventory().offerOrDrop(pack);
        p.sendMessage(Text.translatable("tcgshop.bm.done", pack.toHoverableText()).formatted(Formatting.DARK_PURPLE), false);
        p.playSoundToPlayer(SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 0.8f, 0.8f);
        NpcLines.sayNear(this, "bm_deal");
        dev.tcgshop.advancement.CardAdvancements.grant(p, "black_market");
        lifeTicks = Math.min(lifeTicks, 60);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Life", lifeTicks);
        nbt.putBoolean("Traded", traded);
        if (!offer.isEmpty()) nbt.put("Offer", offer.encode(getRegistryManager()));
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        traded = nbt.getBoolean("Traded");
        offer = nbt.contains("Offer") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Offer")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        dataTracker.set(SKIN, nbt.getInt("NpcSkin"));
    }
}
