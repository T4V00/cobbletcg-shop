package dev.tcgshop.entity;

import dev.masterball.api.card.CardInfo;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.onboarding.OnboardingVisits;
import dev.tcgshop.onboarding.OwnedCards;
import dev.tcgshop.shop.CardSales;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
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
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 1.0 onboarding: the one-time unique collector (see OnboardingVisits). He comes for one player only, walks up to them,
 * says he likes one specific card they own and offers a fixed amount (EconomyConfig.uniqueCollectorOffer) for it.
 * Right-click opens the offer (accept / decline). Accepting is validated on the server and atomic: the card is taken
 * first and, if paying fails, it goes straight back. Whatever the answer (or none, after a few minutes), he leaves for
 * good. Invulnerable, never despawns on his own before that.
 */
public class UniqueCollectorEntity extends PathAwareEntity implements TcgNpc {
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(UniqueCollectorEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EMOTE = DataTracker.registerData(UniqueCollectorEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WAITING = DataTracker.registerData(UniqueCollectorEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Time (loaded ticks) he waits for an answer before leaving. */
    private static final int PATIENCE = 6000;

    private UUID forPlayer;
    private String wish = "";
    private long offer;
    private int lifeTicks = PATIENCE, leaveTicks = -1, likeIn = -1, waitLineCooldown = 600;
    private boolean greeted;

    public UniqueCollectorEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, 0);
        builder.add(EMOTE, EMOTE_NONE);
        builder.add(WAITING, true);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    /** role "unique.trader": label tcgshop.role.unique.trader, the collector's vest skin (NpcSkins keys on the last part). */
    @Override public String roleKey() { return "tcgshop.role.unique.trader"; }
    @Override public String npcTitle() { return ""; }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return dataTracker.get(EMOTE); }
    @Override public boolean wantsAttention() { return dataTracker.get(WAITING); }

    /** Called before spawning (OnboardingVisits.spawnCollector). */
    public void setup(ServerPlayerEntity player, OwnedCards.Want want, long offer) {
        this.forPlayer = player.getUuid();
        this.wish = want.key();
        this.offer = Math.max(0, offer);
        setInvulnerable(true);
        setPersistent();
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!hasCustomName()) {
            setCustomName(Text.translatable("tcgshop.onb.collector.name"));
            setCustomNameVisible(true);
            dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (forPlayer == null) { // spawned by hand (command): he has no one to visit
            discard();
            return;
        }
        if (leaveTicks >= 0) {
            if (--leaveTicks <= 0) {
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                discard();
            }
            return;
        }
        ServerPlayerEntity target = getServer() == null ? null : getServer().getPlayerManager().getPlayer(forPlayer);
        if (target != null && target.getWorld() == getWorld()) {
            double d2 = squaredDistanceTo(target);
            getLookControl().lookAt(target, 30f, 30f);
            if (!greeted) {
                if (d2 <= 4 * 4) {
                    greeted = true;
                    getNavigation().stop();
                    OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "collector.arrive"), 70);
                    likeIn = 70;
                } else if (age % 20 == 0 && d2 < 48 * 48) {
                    getNavigation().startMovingTo(target, 0.6);
                }
            } else if (d2 > 5 * 5 && d2 < 32 * 32 && age % 40 == 0) {
                getNavigation().startMovingTo(target, 0.55); // stays close to the player while waiting for an answer
            }
        }
        if (likeIn > 0 && --likeIn == 0 && target != null) {
            OwnedCards.Want w = OwnedCards.Want.parse(wish);
            ItemStack card = w == null ? ItemStack.EMPTY : w.display();
            if (!card.isEmpty())
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "collector.like", card.getName(), OnboardingVisits.money(offer)), 90);
            dataTracker.set(EMOTE, EMOTE_DEAL);
        }
        if (greeted && likeIn <= 0 && target != null && --waitLineCooldown <= 0) {
            waitLineCooldown = 900;
            if (squaredDistanceTo(target) < 16 * 16) OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "collector.wait"), 60);
        }
        if (--lifeTicks <= 0) {
            if (target != null && squaredDistanceTo(target) < 32 * 32)
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "collector.idle_leave"), 60);
            leave(null);
        }
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp && leaveTicks < 0) {
            if (forPlayer == null || !forPlayer.equals(sp.getUuid())) {
                OnboardingVisits.speak(this, sp, OnboardingVisits.line(random, "collector.other"), 50);
            } else {
                greeted = true;
                likeIn = -1;
                getNavigation().stop();
                open(sp);
            }
        }
        return ActionResult.success(getWorld().isClient);
    }

    /** Sends the offer window. If the card he wanted is gone, he picks another one (or leaves if there is none). */
    private void open(ServerPlayerEntity sp) {
        OwnedCards.Want w = OwnedCards.Want.parse(wish);
        if (w == null || w.display().isEmpty() || !OwnedCards.has(sp, w)) {
            w = OnboardingVisits.pickWish(sp, offer);
            if (w == null) {
                OnboardingVisits.speak(this, sp, OnboardingVisits.line(random, "collector.gone"), 60);
                leave(sp);
                return;
            }
            wish = w.key();
        }
        ItemStack card = w.display();
        if (card.isEmpty()) return;
        NbtCompound d = new NbtCompound();
        d.putInt("id", getId());
        d.put("card", card.encode(sp.getRegistryManager()));
        d.putLong("offer", offer);
        d.putLong("value", CardSales.unitValue(card));
        if (ServerPlayNetworking.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID))
            ServerPlayNetworking.send(sp, new dev.tcgshop.network.UiPayloads.Open("unique_collector", d));
        else // vanilla client: no window, he just explains (the offer cannot be answered without the add-on)
            sp.sendMessage(Text.translatable("tcgshop.onb.collector.need_mod", card.getName(), OnboardingVisits.money(offer)).formatted(Formatting.GRAY), false);
    }

    /** The player's answer from the window: "accept" or "decline". Revalidated here. */
    public void answer(ServerPlayerEntity p, String action) {
        if (!isAlive() || leaveTicks >= 0 || forPlayer == null || !forPlayer.equals(p.getUuid()) || squaredDistanceTo(p) > 8 * 8) return;
        if ("decline".equals(action)) {
            OnboardingVisits.speak(this, p, OnboardingVisits.line(random, "collector.decline"), 70);
            p.sendMessage(Text.translatable("tcgshop.onb.collector.declined").formatted(Formatting.GRAY), false);
            leave(p);
            return;
        }
        if (!"accept".equals(action)) return;
        if (!CobbleDollarsBridge.isAvailable()) {
            p.sendMessage(Text.translatable("tcgshop.sell.no_bank").formatted(Formatting.RED), true);
            return;
        }
        OwnedCards.Want w = OwnedCards.Want.parse(wish);
        ItemStack got = w == null ? ItemStack.EMPTY : OwnedCards.take(p, w);
        if (got.isEmpty() || !CardInfo.isCard(got)) {
            if (!got.isEmpty()) p.getInventory().offerOrDrop(got);
            OnboardingVisits.speak(this, p, OnboardingVisits.line(random, "collector.gone"), 60);
            p.sendMessage(Text.translatable("tcgshop.onb.collector.missing").formatted(Formatting.RED), true);
            return; // the next right-click picks another card
        }
        if (!CobbleDollarsBridge.add(p, offer)) { // paying failed: the card goes back, nothing is lost
            p.getInventory().offerOrDrop(got);
            p.sendMessage(Text.translatable("tcgshop.onb.collector.pay_failed").formatted(Formatting.RED), false);
            return;
        }
        p.sendMessage(Text.translatable("tcgshop.onb.collector.sold", got.toHoverableText(), OnboardingVisits.money(offer)).formatted(Formatting.GREEN), false);
        p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.3f);
        playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, 1f, 1.1f);
        dataTracker.set(EMOTE, EMOTE_LOVE);
        ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.HEART, getX(), getY() + 2, getZ(), 4, 0.3, 0.2, 0.3, 0);
        OnboardingVisits.speak(this, p, OnboardingVisits.line(random, "collector.accept"), 80);
        leave(p);
    }

    /** He goes away for good (walks off for a few seconds, then vanishes). */
    private void leave(ServerPlayerEntity from) {
        if (leaveTicks >= 0) return;
        leaveTicks = 120;
        likeIn = -1;
        dataTracker.set(WAITING, false);
        double dx = from == null ? random.nextGaussian() : getX() - from.getX(), dz = from == null ? random.nextGaussian() : getZ() - from.getZ();
        double len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        getNavigation().startMovingTo(getX() + dx / len * 14, getY(), getZ() + dz / len * 14, 0.6);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (forPlayer != null) nbt.putUuid("ForPlayer", forPlayer);
        nbt.putString("Wish", wish);
        nbt.putLong("Offer", offer);
        nbt.putInt("Life", lifeTicks);
        nbt.putInt("Leave", leaveTicks);
        nbt.putBoolean("Greeted", greeted);
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        forPlayer = nbt.containsUuid("ForPlayer") ? nbt.getUuid("ForPlayer") : null;
        wish = nbt.getString("Wish");
        offer = nbt.getLong("Offer");
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        leaveTicks = nbt.contains("Leave") ? nbt.getInt("Leave") : -1;
        greeted = nbt.getBoolean("Greeted");
        dataTracker.set(SKIN, nbt.getInt("NpcSkin"));
        dataTracker.set(WAITING, leaveTicks < 0);
    }
}
