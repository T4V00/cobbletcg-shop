package dev.tcgshop.entity;

import dev.masterball.api.platform.Net;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.compat.CobblemonParty;
import dev.tcgshop.shop.PackLock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Black marketeer: very rarely appears near a player, hooded, and offers them ONE specific sealed pack in
 * exchange for any Pokémon from their party. The pack is shown before accepting, it must be confirmed, and the Pokémon only
 * disappears if Cobblemon confirms it left the party (and then the pack is handed over). Never leaves you without Pokémon.
 * Vanishes after a few minutes or after the deal.
 */
public class BlackMarketEntity extends PathfinderMob implements TcgNpc {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(BlackMarketEntity.class, EntityDataSerializers.INT);
    private int lifeTicks = 3600;
    private ItemStack offer = ItemStack.EMPTY;
    private boolean traded;
    private UUID forPlayer;

    public BlackMarketEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16f));
    }

    @Override public String roleKey() { return "tcgshop.role.black_market"; }
    @Override public String npcTitle() { return ""; }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return EMOTE_NONE; }
    @Override public boolean wantsAttention() { return !traded; }

    /** Picks the pack it offers: almost always a normal one; sometimes, one from the locked sets. */
    public void rollOffer() {
        List<Item> normal = new ArrayList<>(), rare = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) (PackLock.locked(id) ? rare : normal).add(BuiltInRegistries.ITEM.get(id));
        double rareChance = dev.tcgshop.EconomyConfig.INSTANCE.blackMarketLockedChance;
        List<Item> pool = !rare.isEmpty() && random.nextDouble() < rareChance ? rare : normal;
        if (pool.isEmpty()) pool = rare;
        if (!pool.isEmpty()) offer = new ItemStack(pool.get(random.nextInt(pool.size())));
    }

    public void setForPlayer(UUID id) { forPlayer = id; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 6 == 0) level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.2, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            return;
        }
        if (!hasCustomName()) {
            setCustomName(Component.translatable("tcgshop.bm.name").withStyle(ChatFormatting.DARK_PURPLE));
            setCustomNameVisible(true);
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (offer.isEmpty()) rollOffer();
        if (tickCount % 200 == 0 && !traded && level().getNearestPlayer(this, 12) != null && random.nextFloat() < 0.4f) NpcLines.sayNear(this, "bm_ambient");
        if (--lifeTicks <= 0) vanish();
    }

    private void vanish() {
        if (level() instanceof ServerLevel w) {
            w.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 0.4, 0.8, 0.4, 0.02);
            w.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.NEUTRAL, 1f, 0.7f);
        }
        discard();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.sidedSuccess(level().isClientSide);
        if (traded || offer.isEmpty()) { NpcLines.say(this, sp, "bm_leave"); return InteractionResult.SUCCESS; }
        List<CobblemonParty.Mon> mons = CobblemonParty.list(sp);
        if (mons.size() <= 1) {
            NpcLines.say(this, sp, "bm_no");
            sp.displayClientMessage(Component.translatable("tcgshop.bm.need_two").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        NpcLines.say(this, sp, "bm_hello");
        CompoundTag d = new CompoundTag();
        d.putInt("id", getId());
        d.put("pack", offer.save(sp.registryAccess()));
        d.putBoolean("rare", PackLock.locked(offer));
        ListTag l = new ListTag();
        for (CobblemonParty.Mon m : mons) {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", m.uuid());
            c.putString("n", Component.Serializer.toJson(m.name(), sp.registryAccess()));
            c.putInt("l", m.level());
            c.putBoolean("s", m.shiny());
            c.putString("sp", m.species());
            l.add(c);
        }
        d.put("mons", l);
        if (Net.INSTANCE.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID))
            Net.INSTANCE.send(sp, new dev.tcgshop.network.UiPayloads.Open("black_market", d));
        return InteractionResult.SUCCESS;
    }

    /** The player confirmed: hands over that Pokémon and gets the pack (only if the Pokémon really left the party). */
    public void trade(ServerPlayer p, UUID mon) {
        if (traded || offer.isEmpty() || !isAlive() || distanceToSqr(p) > 8 * 8) return;
        traded = true; // blocks double clicks / two players at once
        boolean removed = CobblemonParty.remove(p, mon);
        if (!removed) {
            traded = false;
            p.displayClientMessage(Component.translatable("tcgshop.bm.failed").withStyle(ChatFormatting.RED), false);
            return;
        }
        ItemStack pack = offer.copy();
        offer = ItemStack.EMPTY;
        p.getInventory().placeItemBackInInventory(pack);
        p.displayClientMessage(Component.translatable("tcgshop.bm.done", pack.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE), false);
        p.playNotifySound(SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.8f);
        NpcLines.sayNear(this, "bm_deal");
        dev.tcgshop.advancement.CardAdvancements.grant(p, "black_market");
        lifeTicks = Math.min(lifeTicks, 60);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Life", lifeTicks);
        nbt.putBoolean("Traded", traded);
        if (!offer.isEmpty()) nbt.put("Offer", offer.save(registryAccess()));
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        traded = nbt.getBoolean("Traded");
        offer = nbt.contains("Offer") ? ItemStack.parse(registryAccess(), nbt.get("Offer")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        entityData.set(SKIN, nbt.getInt("NpcSkin"));
    }
}
