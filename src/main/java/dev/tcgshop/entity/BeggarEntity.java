package dev.tcgshop.entity;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopZones;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
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
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mendigo de cartas: un fan del TCG sin blanca que ronda las tiendas. Te pide unas monedas o una carta.
 * Darle algo mejora la "buena fama" de tus tiendas (y a veces te devuelve el favor con una carta o un sobre);
 * echarle de malas maneras la empeora. Rechazarle con educación no pasa nada. Se va al rato.
 */
public class BeggarEntity extends PathAwareEntity implements TcgNpc {
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(BeggarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EMOTE = DataTracker.registerData(BeggarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private int lifeTicks = 6000, talkCooldown;
    private final Set<UUID> helped = new HashSet<>();

    public BeggarEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, 0);
        builder.add(EMOTE, EMOTE_NONE);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.45));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    @Override public String roleKey() { return "tcgshop.role.beggar"; }
    @Override public String npcTitle() { return ""; }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return dataTracker.get(EMOTE); }
    @Override public boolean wantsAttention() { return helped.isEmpty() && lifeTicks > 600; }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!hasCustomName()) {
            setCustomName(Text.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (talkCooldown > 0) talkCooldown--;
        if (talkCooldown <= 0 && age % 40 == 0 && getWorld().getClosestPlayer(this, 10) != null && random.nextFloat() < 0.25f) {
            NpcLines.sayNear(this, helped.isEmpty() ? "beg_ambient" : "beg_thanks");
            talkCooldown = 600;
        }
        if (--lifeTicks <= 0) {
            ((ServerWorld) getWorld()).spawnParticles(net.minecraft.particle.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
            discard();
        }
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp) {
            NpcLines.say(this, sp, "beg_hello");
            NbtCompound d = new NbtCompound();
            d.putInt("id", getId());
            d.putString("name", getName().getString());
            d.putBoolean("card", CardInfo.isCard(sp.getMainHandStack()));
            if (ServerPlayNetworking.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID))
                ServerPlayNetworking.send(sp, new dev.tcgshop.network.UiPayloads.Open("beggar", d));
        }
        return ActionResult.success(getWorld().isClient);
    }

    /** Respuesta del jugador: give_50 / give_250 / give_1000 / give_card / polite / rude. */
    public void answer(ServerPlayerEntity p, String action) {
        if (squaredDistanceTo(p) > 8 * 8 || !isAlive()) return;
        ServerWorld world = p.getServerWorld();
        switch (action) {
            case "give_50", "give_250", "give_1000" -> {
                long amount = Long.parseLong(action.substring(5));
                if (!CobbleDollarsBridge.tryCharge(p, amount)) {
                    p.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(amount)).formatted(Formatting.RED), true);
                    return;
                }
                charity(p, (int) Math.max(1, amount / 50));
                NpcLines.sayNear(this, amount >= 1000 ? "beg_thanks_big" : "beg_thanks");
                dataTracker.set(EMOTE, EMOTE_LOVE);
                double card = amount >= 1000 ? 0.25 : amount >= 250 ? 0.10 : 0.03, pack = amount >= 1000 ? 0.08 : amount >= 250 ? 0.02 : 0;
                reward(p, card, pack, amount >= 1000);
                helped.add(p.getUuid());
                lifeTicks = Math.min(lifeTicks, 900);
            }
            case "give_card" -> {
                ItemStack st = p.getMainHandStack();
                if (!CardInfo.isCard(st)) return;
                int tier = Rarity.of(st).tier;
                st.decrement(1);
                charity(p, 3 + tier * 2);
                NpcLines.sayNear(this, "beg_card_love");
                dataTracker.set(EMOTE, EMOTE_LOVE);
                reward(p, 0.12, 0.03, tier >= 2);
                helped.add(p.getUuid());
                lifeTicks = Math.min(lifeTicks, 900);
            }
            case "polite" -> {
                NpcLines.sayNear(this, "beg_polite_no");
                lifeTicks = Math.min(lifeTicks, 1200);
            }
            case "rude" -> {
                NpcLines.sayNear(this, "beg_rude");
                dataTracker.set(EMOTE, EMOTE_ANGRY);
                charity(p, -5);
                lifeTicks = Math.min(lifeTicks, 200);
                getNavigation().startMovingTo(getX() + (getX() - p.getX()) * 3, getY(), getZ() + (getZ() - p.getZ()) * 3, 0.7);
            }
            default -> {}
        }
        world.spawnParticles(action.equals("rude") ? net.minecraft.particle.ParticleTypes.ANGRY_VILLAGER : net.minecraft.particle.ParticleTypes.HEART,
                getX(), getY() + 2, getZ(), 3, 0.3, 0.2, 0.3, 0);
    }

    /** Buena (o mala) fama en todas las tiendas del jugador. */
    private void charity(ServerPlayerEntity p, int points) {
        boolean any = false;
        for (ServerWorld w : p.getServer().getWorlds()) {
            ShopZones zs = ShopZones.get(w);
            for (ShopZones.Zone z : zs.all())
                if (z.owner.equals(p.getUuid())) {
                    z.charity = Math.max(-15, Math.min(15, z.charity + points));
                    zs.markDirty();
                    any = true;
                }
        }
        if (any) p.sendMessage(Text.translatable(points >= 0 ? "tcgshop.beggar.karma_up" : "tcgshop.beggar.karma_down").formatted(points >= 0 ? Formatting.GREEN : Formatting.RED), true);
    }

    /** A veces te devuelve el favor: una carta (mejor si fuiste generoso) o, rara vez, un sobre. */
    private void reward(ServerPlayerEntity p, double cardChance, double packChance, boolean good) {
        if (random.nextDouble() < packChance) {
            List<Item> packs = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !PackLock.locked(id)) packs.add(Registries.ITEM.get(id));
            if (!packs.isEmpty()) {
                give(p, new ItemStack(packs.get(random.nextInt(packs.size()))));
                NpcLines.sayNear(this, "beg_gift");
                return;
            }
        }
        if (random.nextDouble() < cardChance) {
            List<Item> cards = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) if (CardInfo.isCard(id)) cards.add(Registries.ITEM.get(id));
            if (cards.isEmpty()) return;
            ItemStack pick = new ItemStack(cards.get(random.nextInt(cards.size())));
            for (int k = 0; k < 8 && good && Rarity.of(pick).tier == 0; k++) pick = new ItemStack(cards.get(random.nextInt(cards.size())));
            give(p, pick);
            NpcLines.sayNear(this, "beg_gift");
        }
    }

    private static void give(ServerPlayerEntity p, ItemStack st) {
        p.sendMessage(Text.translatable("tcgshop.beggar.gift", st.toHoverableText()).formatted(Formatting.GOLD), false);
        p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 1.5f);
        if (CardInfo.isCard(st) && CollectionService.store(p, List.of(st.copy()))) return;
        p.getInventory().offerOrDrop(st);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Life", lifeTicks);
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        dataTracker.set(SKIN, nbt.getInt("NpcSkin"));
    }
}
