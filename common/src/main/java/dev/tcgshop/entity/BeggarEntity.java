package dev.tcgshop.entity;

import dev.masterball.api.platform.Net;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Card beggar: a broke TCG fan who hangs around shops. Asks you for a few coins or a card.
 * Giving him something improves your shops' "good standing" (and sometimes he returns the favor with a card or a pack);
 * sending him off rudely worsens it. Turning him down politely has no effect. Leaves after a while.
 */
public class BeggarEntity extends PathfinderMob implements TcgNpc {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(BeggarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(BeggarEntity.class, EntityDataSerializers.INT);
    private int lifeTicks = 6000, talkCooldown;
    private final Set<UUID> helped = new HashSet<>();

    public BeggarEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.MOVEMENT_SPEED, 0.22);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
        builder.define(EMOTE, EMOTE_NONE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.45));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override public String roleKey() { return "tcgshop.role.beggar"; }
    @Override public String npcTitle() { return ""; }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return entityData.get(EMOTE); }
    @Override public boolean wantsAttention() { return helped.isEmpty() && lifeTicks > 600; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!hasCustomName()) {
            setCustomName(Component.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (talkCooldown > 0) talkCooldown--;
        if (talkCooldown <= 0 && tickCount % 40 == 0 && level().getNearestPlayer(this, 10) != null && random.nextFloat() < 0.25f) {
            NpcLines.sayNear(this, helped.isEmpty() ? "beg_ambient" : "beg_thanks");
            talkCooldown = 600;
        }
        if (--lifeTicks <= 0) {
            ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            discard();
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            NpcLines.say(this, sp, "beg_hello");
            CompoundTag d = new CompoundTag();
            d.putInt("id", getId());
            d.putString("name", getName().getString());
            d.putBoolean("card", CardInfo.isCard(sp.getMainHandItem()));
            if (Net.INSTANCE.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID))
                Net.INSTANCE.send(sp, new dev.tcgshop.network.UiPayloads.Open("beggar", d));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Player's answer: give_50 / give_250 / give_1000 / give_card / polite / rude. */
    public void answer(ServerPlayer p, String action) {
        if (distanceToSqr(p) > 8 * 8 || !isAlive()) return;
        ServerLevel world = p.serverLevel();
        switch (action) {
            case "give_50", "give_250", "give_1000" -> {
                long amount = Long.parseLong(action.substring(5));
                if (!CobbleDollarsBridge.tryCharge(p, amount)) {
                    p.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(amount)).withStyle(ChatFormatting.RED), true);
                    return;
                }
                charity(p, (int) Math.max(1, amount / 50));
                NpcLines.sayNear(this, amount >= 1000 ? "beg_thanks_big" : "beg_thanks");
                entityData.set(EMOTE, EMOTE_LOVE);
                double card = amount >= 1000 ? 0.25 : amount >= 250 ? 0.10 : 0.03, pack = amount >= 1000 ? 0.08 : amount >= 250 ? 0.02 : 0;
                reward(p, card, pack, amount >= 1000);
                helped.add(p.getUUID());
                lifeTicks = Math.min(lifeTicks, 900);
            }
            case "give_card" -> {
                ItemStack st = p.getMainHandItem();
                if (!CardInfo.isCard(st)) return;
                int tier = Rarity.of(st).tier;
                st.shrink(1);
                charity(p, 3 + tier * 2);
                NpcLines.sayNear(this, "beg_card_love");
                entityData.set(EMOTE, EMOTE_LOVE);
                reward(p, 0.12, 0.03, tier >= 2);
                helped.add(p.getUUID());
                lifeTicks = Math.min(lifeTicks, 900);
            }
            case "polite" -> {
                NpcLines.sayNear(this, "beg_polite_no");
                lifeTicks = Math.min(lifeTicks, 1200);
            }
            case "rude" -> {
                NpcLines.sayNear(this, "beg_rude");
                entityData.set(EMOTE, EMOTE_ANGRY);
                charity(p, -5);
                lifeTicks = Math.min(lifeTicks, 200);
                getNavigation().moveTo(getX() + (getX() - p.getX()) * 3, getY(), getZ() + (getZ() - p.getZ()) * 3, 0.7);
            }
            default -> {}
        }
        world.sendParticles(action.equals("rude") ? net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER : net.minecraft.core.particles.ParticleTypes.HEART,
                getX(), getY() + 2, getZ(), 3, 0.3, 0.2, 0.3, 0);
    }

    /** Good (or bad) standing across all of the player's shops. */
    private void charity(ServerPlayer p, int points) {
        boolean any = false;
        for (ServerLevel w : p.getServer().getAllLevels()) {
            ShopZones zs = ShopZones.get(w);
            for (ShopZones.Zone z : zs.all())
                if (z.owner.equals(p.getUUID())) {
                    z.charity = Math.max(-15, Math.min(15, z.charity + points));
                    zs.setDirty();
                    any = true;
                }
        }
        if (any) p.displayClientMessage(Component.translatable(points >= 0 ? "tcgshop.beggar.karma_up" : "tcgshop.beggar.karma_down").withStyle(points >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    /** Sometimes returns the favor: a card (better if you were generous) or, rarely, a pack. */
    private void reward(ServerPlayer p, double cardChance, double packChance, boolean good) {
        if (random.nextDouble() < packChance) {
            List<Item> packs = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !PackLock.locked(id)) packs.add(BuiltInRegistries.ITEM.get(id));
            if (!packs.isEmpty()) {
                give(p, new ItemStack(packs.get(random.nextInt(packs.size()))));
                NpcLines.sayNear(this, "beg_gift");
                return;
            }
        }
        if (random.nextDouble() < cardChance) {
            List<Item> cards = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) if (CardInfo.isCard(id)) cards.add(BuiltInRegistries.ITEM.get(id));
            if (cards.isEmpty()) return;
            ItemStack pick = new ItemStack(cards.get(random.nextInt(cards.size())));
            for (int k = 0; k < 8 && good && Rarity.of(pick).tier == 0; k++) pick = new ItemStack(cards.get(random.nextInt(cards.size())));
            give(p, pick);
            NpcLines.sayNear(this, "beg_gift");
        }
    }

    private static void give(ServerPlayer p, ItemStack st) {
        p.displayClientMessage(Component.translatable("tcgshop.beggar.gift", st.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.5f);
        dev.tcgshop.advancement.CardAdvancements.noteObtained(p, st); // 1.1: an NPC gift counts for the set reward
        if (CardInfo.isCard(st) && CollectionService.store(p, List.of(st.copy()))) return;
        p.getInventory().placeItemBackInInventory(st);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("Life", lifeTicks);
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        entityData.set(SKIN, nbt.getInt("NpcSkin"));
    }
}
