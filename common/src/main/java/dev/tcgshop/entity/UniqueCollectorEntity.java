package dev.tcgshop.entity;

import dev.masterball.api.platform.Net;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.onboarding.OnboardingVisits;
import dev.tcgshop.onboarding.OwnedCards;
import dev.tcgshop.shop.CardSales;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.UUID;

/**
 * 1.0 onboarding: the one-time unique collector (see OnboardingVisits). He comes for one player only, walks up to them,
 * says he likes one specific card they own and offers a fixed amount (EconomyConfig.uniqueCollectorOffer) for it.
 * Right-click opens the offer (accept / decline). Accepting is validated on the server and atomic: the card is taken
 * first and, if paying fails, it goes straight back. Whatever the answer (or none, after a few minutes), he leaves for
 * good. Invulnerable, never despawns on his own before that.
 */
public class UniqueCollectorEntity extends PathfinderMob implements TcgNpc {
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(UniqueCollectorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMOTE = SynchedEntityData.defineId(UniqueCollectorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WAITING = SynchedEntityData.defineId(UniqueCollectorEntity.class, EntityDataSerializers.BOOLEAN);
    /** Time (loaded ticks) he waits for an answer before leaving. */
    private static final int PATIENCE = 6000;

    private UUID forPlayer;
    private String wish = "";
    private long offer;
    private int lifeTicks = PATIENCE, leaveTicks = -1, likeIn = -1, waitLineCooldown = 600;
    private boolean greeted;

    public UniqueCollectorEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
        builder.define(EMOTE, EMOTE_NONE);
        builder.define(WAITING, true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** role "unique.trader": label tcgshop.role.unique.trader, the collector's vest skin (NpcSkins keys on the last part). */
    @Override public String roleKey() { return "tcgshop.role.unique.trader"; }
    @Override public String npcTitle() { return ""; }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return entityData.get(EMOTE); }
    @Override public boolean wantsAttention() { return entityData.get(WAITING); }

    /** Called before spawning (OnboardingVisits.spawnCollector). */
    public void setup(ServerPlayer player, OwnedCards.Want want, long offer) {
        this.forPlayer = player.getUUID();
        this.wish = want.key();
        this.offer = Math.max(0, offer);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!hasCustomName()) {
            setCustomName(Component.translatable("tcgshop.onb.collector.name"));
            setCustomNameVisible(true);
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (forPlayer == null) { // spawned by hand (command): he has no one to visit
            discard();
            return;
        }
        if (leaveTicks >= 0) {
            if (--leaveTicks <= 0) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                discard();
            }
            return;
        }
        ServerPlayer target = getServer() == null ? null : getServer().getPlayerList().getPlayer(forPlayer);
        if (target != null && target.level() == level()) {
            double d2 = distanceToSqr(target);
            getLookControl().setLookAt(target, 30f, 30f);
            if (!greeted) {
                if (d2 <= 4 * 4) {
                    greeted = true;
                    getNavigation().stop();
                    OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "collector.arrive"), 70);
                    likeIn = 70;
                } else if (tickCount % 20 == 0 && d2 < 48 * 48) {
                    getNavigation().moveTo(target, 0.6);
                }
            } else if (d2 > 5 * 5 && d2 < 32 * 32 && tickCount % 40 == 0) {
                getNavigation().moveTo(target, 0.55); // stays close to the player while waiting for an answer
            }
        }
        if (likeIn > 0 && --likeIn == 0 && target != null) {
            OwnedCards.Want w = OwnedCards.Want.parse(wish);
            ItemStack card = w == null ? ItemStack.EMPTY : w.display();
            if (!card.isEmpty())
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "collector.like", card.getHoverName(), OnboardingVisits.money(offer)), 90);
            entityData.set(EMOTE, EMOTE_DEAL);
        }
        if (greeted && likeIn <= 0 && target != null && --waitLineCooldown <= 0) {
            waitLineCooldown = 900;
            if (distanceToSqr(target) < 16 * 16) OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "collector.wait"), 60);
        }
        if (--lifeTicks <= 0) {
            if (target != null && distanceToSqr(target) < 32 * 32)
                OnboardingVisits.speak(this, target, OnboardingVisits.line(random, "collector.idle_leave"), 60);
            leave(null);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp && leaveTicks < 0) {
            if (forPlayer == null || !forPlayer.equals(sp.getUUID())) {
                OnboardingVisits.speak(this, sp, OnboardingVisits.line(random, "collector.other"), 50);
            } else {
                greeted = true;
                likeIn = -1;
                getNavigation().stop();
                open(sp);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Sends the offer window. If the card he wanted is gone, he picks another one (or leaves if there is none). */
    private void open(ServerPlayer sp) {
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
        CompoundTag d = new CompoundTag();
        d.putInt("id", getId());
        d.put("card", card.save(sp.registryAccess()));
        d.putLong("offer", offer);
        d.putLong("value", CardSales.unitValue(card));
        if (Net.INSTANCE.canSend(sp, dev.tcgshop.network.UiPayloads.Open.ID))
            Net.INSTANCE.send(sp, new dev.tcgshop.network.UiPayloads.Open("unique_collector", d));
        else // vanilla client: no window, he just explains (the offer cannot be answered without the add-on)
            sp.displayClientMessage(Component.translatable("tcgshop.onb.collector.need_mod", card.getHoverName(), OnboardingVisits.money(offer)).withStyle(ChatFormatting.GRAY), false);
    }

    /** The player's answer from the window: "accept" or "decline". Revalidated here. */
    public void answer(ServerPlayer p, String action) {
        if (!isAlive() || leaveTicks >= 0 || forPlayer == null || !forPlayer.equals(p.getUUID()) || distanceToSqr(p) > 8 * 8) return;
        if ("decline".equals(action)) {
            OnboardingVisits.speak(this, p, OnboardingVisits.line(random, "collector.decline"), 70);
            p.displayClientMessage(Component.translatable("tcgshop.onb.collector.declined").withStyle(ChatFormatting.GRAY), false);
            leave(p);
            return;
        }
        if (!"accept".equals(action)) return;
        if (!CobbleDollarsBridge.isAvailable()) {
            p.displayClientMessage(Component.translatable("tcgshop.sell.no_bank").withStyle(ChatFormatting.RED), true);
            return;
        }
        OwnedCards.Want w = OwnedCards.Want.parse(wish);
        ItemStack got = w == null ? ItemStack.EMPTY : OwnedCards.take(p, w);
        if (got.isEmpty() || !CardInfo.isCard(got)) {
            if (!got.isEmpty()) p.getInventory().placeItemBackInInventory(got);
            OnboardingVisits.speak(this, p, OnboardingVisits.line(random, "collector.gone"), 60);
            p.displayClientMessage(Component.translatable("tcgshop.onb.collector.missing").withStyle(ChatFormatting.RED), true);
            return; // the next right-click picks another card
        }
        if (!CobbleDollarsBridge.add(p, offer)) { // paying failed: the card goes back, nothing is lost
            p.getInventory().placeItemBackInInventory(got);
            p.displayClientMessage(Component.translatable("tcgshop.onb.collector.pay_failed").withStyle(ChatFormatting.RED), false);
            return;
        }
        p.displayClientMessage(Component.translatable("tcgshop.onb.collector.sold", got.getDisplayName(), OnboardingVisits.money(offer)).withStyle(ChatFormatting.GREEN), false);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.3f);
        playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 1.1f);
        entityData.set(EMOTE, EMOTE_LOVE);
        ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getY() + 2, getZ(), 4, 0.3, 0.2, 0.3, 0);
        OnboardingVisits.speak(this, p, OnboardingVisits.line(random, "collector.accept"), 80);
        leave(p);
    }

    /** He goes away for good (walks off for a few seconds, then vanishes). */
    private void leave(ServerPlayer from) {
        if (leaveTicks >= 0) return;
        leaveTicks = 120;
        likeIn = -1;
        entityData.set(WAITING, false);
        double dx = from == null ? random.nextGaussian() : getX() - from.getX(), dz = from == null ? random.nextGaussian() : getZ() - from.getZ();
        double len = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        getNavigation().moveTo(getX() + dx / len * 14, getY(), getZ() + dz / len * 14, 0.6);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (forPlayer != null) nbt.putUUID("ForPlayer", forPlayer);
        nbt.putString("Wish", wish);
        nbt.putLong("Offer", offer);
        nbt.putInt("Life", lifeTicks);
        nbt.putInt("Leave", leaveTicks);
        nbt.putBoolean("Greeted", greeted);
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        forPlayer = nbt.hasUUID("ForPlayer") ? nbt.getUUID("ForPlayer") : null;
        wish = nbt.getString("Wish");
        offer = nbt.getLong("Offer");
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        leaveTicks = nbt.contains("Leave") ? nbt.getInt("Leave") : -1;
        greeted = nbt.getBoolean("Greeted");
        entityData.set(SKIN, nbt.getInt("NpcSkin"));
        entityData.set(WAITING, leaveTicks < 0);
    }
}
