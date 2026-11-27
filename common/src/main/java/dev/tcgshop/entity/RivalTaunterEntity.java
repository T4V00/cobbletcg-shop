package dev.tcgshop.entity;

import dev.tcgshop.onboarding.OnboardingVisits;
import dev.tcgshop.shop.CardSales;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.0 onboarding: the one-time taunting rival (see OnboardingVisits). A cocky, well-off shop owner who visits a player
 * that has been playing for a while without opening a shop: walks up, throws a card worth about
 * EconomyConfig.taunterCardValue at their feet (only that player can pick it up), calls them a beggar and teases them
 * into opening a shop, then walks off and vanishes. A small script of speech bubbles, one step every few seconds; the
 * step is saved, so a restart in the middle continues where it was (the card is never thrown twice).
 */
public class RivalTaunterEntity extends PathfinderMob implements TcgNpc {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(RivalTaunterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(RivalTaunterEntity.class, EntityDataSerializers.INT);
    /** Script steps. */
    private static final int APPROACH = 0, INTRO = 1, THROW = 2, BEGGAR = 3, PUSH = 4, BYE = 5, GONE = 6;
    /** Max ticks walking towards the player before talking from where he is. */
    private static final int APPROACH_MAX = 400;
    /** Ticks without his player around (offline / other dimension) before he gives up and leaves. */
    private static final int ABSENT_MAX = 2400;

    private UUID forPlayer;
    private long cardValue;
    private int step = APPROACH, wait, walked, absent, leaveTicks = -1, pokeCooldown;

    public RivalTaunterEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.27);
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
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** role "rival.scalper": label tcgshop.role.rival.scalper, the reseller's hoodie skin (NpcSkins keys on the last part). */
    @Override public String roleKey() { return "tcgshop.role.rival.scalper"; }
    @Override public String npcTitle() { return ""; }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return entityData.get(EMOTE); }

    /** Called before spawning (OnboardingVisits.spawnTaunter). */
    public void setup(ServerPlayer player, long cardValue) {
        this.forPlayer = player.getUUID();
        this.cardValue = Math.max(1, cardValue);
        setInvulnerable(true);
        setPersistenceRequired();
        setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!hasCustomName()) {
            setCustomName(Component.translatable("tcgshop.onb.taunter.name"));
            setCustomNameVisible(true);
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (forPlayer == null) { // spawned by hand (command): nobody to taunt
            discard();
            return;
        }
        if (pokeCooldown > 0) pokeCooldown--;
        if (leaveTicks >= 0) {
            if (--leaveTicks <= 0) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                discard();
            }
            return;
        }
        ServerPlayer target = getServer() == null ? null : getServer().getPlayerList().getPlayer(forPlayer);
        if (target == null || target.level() != level() || distanceToSqr(target) > 64 * 64) {
            if (++absent > ABSENT_MAX) leave(null);
            return;
        }
        absent = 0;
        getLookControl().setLookAt(target, 30f, 30f);
        if (step == APPROACH) {
            double d2 = distanceToSqr(target);
            walked++;
            if (d2 <= 3.5 * 3.5 || walked > APPROACH_MAX) {
                getNavigation().stop();
                step = INTRO;
                wait = 0;
            } else if (tickCount % 20 == 0) {
                getNavigation().moveTo(target, 0.75);
            }
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (step) {
            case INTRO -> {
                ItemStack card = OnboardingVisits.pickCardWorth(random, cardValue);
                if (!card.isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, card); // he shows it off before throwing it
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.intro"), 70);
                entityData.set(EMOTE, EMOTE_THINK);
                next(THROW, 75);
            }
            case THROW -> {
                ItemStack card = getMainHandItem().copy();
                if (card.isEmpty()) card = OnboardingVisits.pickCardWorth(random, cardValue);
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (!card.isEmpty()) throwAt(target, card);
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.throw"), 80);
                entityData.set(EMOTE, EMOTE_MONEY);
                next(BEGGAR, 85);
            }
            case BEGGAR -> {
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.beggar", target.getName()), 80);
                next(PUSH, 85);
            }
            case PUSH -> {
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.push"), 90);
                entityData.set(EMOTE, EMOTE_NONE);
                next(BYE, 95);
            }
            case BYE -> {
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.leave"), 60);
                target.displayClientMessage(Component.translatable("tcgshop.onb.taunter.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
                step = GONE;
                leave(target);
            }
            default -> leave(target);
        }
    }

    private void next(int s, int ticks) {
        step = s;
        wait = ticks;
    }

    /** The card lands at the player's feet; only that player can pick it up. */
    private void throwAt(ServerPlayer target, ItemStack card) {
        swing(InteractionHand.MAIN_HAND);
        Vec3 from = position().add(0, 1.2, 0);
        ItemEntity ie = new ItemEntity(level(), target.getX(), target.getY() + 0.25, target.getZ(), card.copyWithCount(1));
        Vec3 dir = target.position().subtract(from).scale(0.03);
        ie.setDeltaMovement(dir.x, 0.15, dir.z);
        ie.setTarget(target.getUUID());
        ie.setThrower(this);
        ie.setPickUpDelay(10);
        level().addFreshEntity(ie);
        playSound(SoundEvents.SNOWBALL_THROW, 0.8f, 1.2f);
        target.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 0.8f);
        target.displayClientMessage(Component.translatable("tcgshop.onb.taunter.card", getDisplayName(), card.getDisplayName(),
                OnboardingVisits.money(CardSales.unitValue(card))).withStyle(ChatFormatting.YELLOW), false);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp && leaveTicks < 0 && pokeCooldown <= 0) {
            pokeCooldown = 40;
            boolean mine = forPlayer != null && forPlayer.equals(sp.getUUID());
            OnboardingVisits.speak(this, sp, OnboardingVisits.line(random, mine ? "taunter.poke" : "taunter.other"), 50);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Walks off and vanishes a few seconds later. */
    private void leave(ServerPlayer from) {
        if (leaveTicks >= 0) return;
        leaveTicks = 110;
        step = GONE;
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        double dx = from == null ? random.nextGaussian() : getX() - from.getX(), dz = from == null ? random.nextGaussian() : getZ() - from.getZ();
        double len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        getNavigation().moveTo(getX() + dx / len * 16, getY(), getZ() + dz / len * 16, 0.8);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (forPlayer != null) nbt.putUUID("ForPlayer", forPlayer);
        nbt.putLong("CardValue", cardValue);
        nbt.putInt("Step", step);
        nbt.putInt("Wait", wait);
        nbt.putInt("Leave", leaveTicks);
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        forPlayer = nbt.hasUUID("ForPlayer") ? nbt.getUUID("ForPlayer") : null;
        cardValue = nbt.getLong("CardValue");
        step = nbt.getInt("Step");
        wait = nbt.getInt("Wait");
        leaveTicks = nbt.contains("Leave") ? nbt.getInt("Leave") : -1;
        entityData.set(SKIN, nbt.getInt("NpcSkin"));
    }
}
