package dev.tcgshop.entity;

import dev.tcgshop.onboarding.OnboardingVisits;
import dev.tcgshop.shop.CardSales;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 1.0 onboarding: the one-time taunting rival (see OnboardingVisits). A cocky, well-off shop owner who visits a player
 * that has been playing for a while without opening a shop: walks up, throws a card worth about
 * EconomyConfig.taunterCardValue at their feet (only that player can pick it up), calls them a beggar and teases them
 * into opening a shop, then walks off and vanishes. A small script of speech bubbles, one step every few seconds; the
 * step is saved, so a restart in the middle continues where it was (the card is never thrown twice).
 */
public class RivalTaunterEntity extends PathAwareEntity implements TcgNpc {
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(RivalTaunterEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EMOTE = DataTracker.registerData(RivalTaunterEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Script steps. */
    private static final int APPROACH = 0, INTRO = 1, THROW = 2, BEGGAR = 3, PUSH = 4, BYE = 5, GONE = 6;
    /** Max ticks walking towards the player before talking from where he is. */
    private static final int APPROACH_MAX = 400;
    /** Ticks without his player around (offline / other dimension) before he gives up and leaves. */
    private static final int ABSENT_MAX = 2400;

    private UUID forPlayer;
    private long cardValue;
    private int step = APPROACH, wait, walked, absent, leaveTicks = -1, pokeCooldown;

    public RivalTaunterEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27);
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
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    /** role "rival.scalper": label tcgshop.role.rival.scalper, the reseller's hoodie skin (NpcSkins keys on the last part). */
    @Override public String roleKey() { return "tcgshop.role.rival.scalper"; }
    @Override public String npcTitle() { return ""; }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return dataTracker.get(EMOTE); }

    /** Called before spawning (OnboardingVisits.spawnTaunter). */
    public void setup(ServerPlayerEntity player, long cardValue) {
        this.forPlayer = player.getUuid();
        this.cardValue = Math.max(1, cardValue);
        setInvulnerable(true);
        setPersistent();
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!hasCustomName()) {
            setCustomName(Text.translatable("tcgshop.onb.taunter.name"));
            setCustomNameVisible(true);
            dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (forPlayer == null) { // spawned by hand (command): nobody to taunt
            discard();
            return;
        }
        if (pokeCooldown > 0) pokeCooldown--;
        if (leaveTicks >= 0) {
            if (--leaveTicks <= 0) {
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                discard();
            }
            return;
        }
        ServerPlayerEntity target = getServer() == null ? null : getServer().getPlayerManager().getPlayer(forPlayer);
        if (target == null || target.getWorld() != getWorld() || squaredDistanceTo(target) > 64 * 64) {
            if (++absent > ABSENT_MAX) leave(null);
            return;
        }
        absent = 0;
        getLookControl().lookAt(target, 30f, 30f);
        if (step == APPROACH) {
            double d2 = squaredDistanceTo(target);
            walked++;
            if (d2 <= 3.5 * 3.5 || walked > APPROACH_MAX) {
                getNavigation().stop();
                step = INTRO;
                wait = 0;
            } else if (age % 20 == 0) {
                getNavigation().startMovingTo(target, 0.75);
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
                if (!card.isEmpty()) equipStack(EquipmentSlot.MAINHAND, card); // he shows it off before throwing it
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.intro"), 70);
                dataTracker.set(EMOTE, EMOTE_THINK);
                next(THROW, 75);
            }
            case THROW -> {
                ItemStack card = getMainHandStack().copy();
                if (card.isEmpty()) card = OnboardingVisits.pickCardWorth(random, cardValue);
                equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                if (!card.isEmpty()) throwAt(target, card);
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.throw"), 80);
                dataTracker.set(EMOTE, EMOTE_MONEY);
                next(BEGGAR, 85);
            }
            case BEGGAR -> {
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.beggar", target.getName()), 80);
                next(PUSH, 85);
            }
            case PUSH -> {
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.push"), 90);
                dataTracker.set(EMOTE, EMOTE_NONE);
                next(BYE, 95);
            }
            case BYE -> {
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "taunter.leave"), 60);
                target.sendMessage(Text.translatable("tcgshop.onb.taunter.hint").formatted(Formatting.GRAY, Formatting.ITALIC), false);
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
    private void throwAt(ServerPlayerEntity target, ItemStack card) {
        swingHand(Hand.MAIN_HAND);
        Vec3d from = getPos().add(0, 1.2, 0);
        ItemEntity ie = new ItemEntity(getWorld(), target.getX(), target.getY() + 0.25, target.getZ(), card.copyWithCount(1));
        Vec3d dir = target.getPos().subtract(from).multiply(0.03);
        ie.setVelocity(dir.x, 0.15, dir.z);
        ie.setOwner(target.getUuid());
        ie.setThrower(this);
        ie.setPickupDelay(10);
        getWorld().spawnEntity(ie);
        playSound(SoundEvents.ENTITY_SNOWBALL_THROW, 0.8f, 1.2f);
        target.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.6f, 0.8f);
        target.sendMessage(Text.translatable("tcgshop.onb.taunter.card", getDisplayName(), card.toHoverableText(),
                OnboardingVisits.money(CardSales.unitValue(card))).formatted(Formatting.YELLOW), false);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp && leaveTicks < 0 && pokeCooldown <= 0) {
            pokeCooldown = 40;
            boolean mine = forPlayer != null && forPlayer.equals(sp.getUuid());
            OnboardingVisits.speak(this, sp, OnboardingVisits.line(random, mine ? "taunter.poke" : "taunter.other"), 50);
        }
        return ActionResult.success(getWorld().isClient);
    }

    /** Walks off and vanishes a few seconds later. */
    private void leave(ServerPlayerEntity from) {
        if (leaveTicks >= 0) return;
        leaveTicks = 110;
        step = GONE;
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        double dx = from == null ? random.nextGaussian() : getX() - from.getX(), dz = from == null ? random.nextGaussian() : getZ() - from.getZ();
        double len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        getNavigation().startMovingTo(getX() + dx / len * 16, getY(), getZ() + dz / len * 16, 0.8);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (forPlayer != null) nbt.putUuid("ForPlayer", forPlayer);
        nbt.putLong("CardValue", cardValue);
        nbt.putInt("Step", step);
        nbt.putInt("Wait", wait);
        nbt.putInt("Leave", leaveTicks);
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        forPlayer = nbt.containsUuid("ForPlayer") ? nbt.getUuid("ForPlayer") : null;
        cardValue = nbt.getLong("CardValue");
        step = nbt.getInt("Step");
        wait = nbt.getInt("Wait");
        leaveTicks = nbt.contains("Leave") ? nbt.getInt("Leave") : -1;
        dataTracker.set(SKIN, nbt.getInt("NpcSkin"));
    }
}
