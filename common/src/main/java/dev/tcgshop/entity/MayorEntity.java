package dev.tcgshop.entity;

import dev.tcgshop.npc.TaxOffice;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 1.1: the Mayor of the town, who collects the shop taxes (npc/TaxOffice holds the schedule, the bill and every outcome).
 * He follows the taxpayer around until the bill is settled (paid, battle won, or a card taken), cannot be hurt (no
 * dodging taxes by killing him), and stands still during his Cobblemon battle. If he loses track of the taxpayer
 * (offline, far away, another dimension) he leaves and the TaxOffice sends him again later: the bill stays open.
 * Fallback battles (compat/CobblemonBattles: a chain of strong wild Pokémon) use {@link CobblemonBattles.Fighter}.
 */
public class MayorEntity extends PathfinderMob implements TcgNpc, dev.tcgshop.compat.CobblemonBattles.Fighter {
    /** The player he came for (saved). */
    @Nullable private UUID taxpayer;
    /** Ticks with the taxpayer out of reach; ticks left before he vanishes once the bill is settled (-1 = not leaving). */
    private int awayTicks, leaveTicks = -1;
    /** Holding still (battle in progress). */
    private boolean dueling;

    private static final net.minecraft.network.syncher.EntityDataAccessor<String> TITLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MayorEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SKIN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MayorEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    static final int AWAY_LIMIT = 1200, LINES_GREET = 8, LINES_WAIT = 8, LINES_PAID = 5, LINES_WON = 4, LINES_TOOK = 5, LINES_DUEL = 5;

    public MayorEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TITLE, "");
        builder.define(SKIN, 1);
    }

    @Override public String roleKey() { return "tcgshop.role.mayor"; }
    @Override public String npcTitle() { return entityData.get(TITLE); }
    @Override public int npcSkin() { return entityData.get(SKIN); }
    @Override public int emote() { return leaveTicks >= 0 ? EMOTE_NONE : EMOTE_MONEY; }
    @Override public boolean wantsAttention() { return leaveTicks < 0 && !dueling; }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new FollowTaxpayerGoal());
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Walks to within 3 blocks of the taxpayer and keeps that distance (never during a battle or while leaving). */
    private final class FollowTaxpayerGoal extends Goal {
        private int repath;

        FollowTaxpayerGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Nullable
        private Player target() {
            return taxpayer == null ? null : level().getPlayerByUUID(taxpayer);
        }

        @Override
        public boolean canUse() {
            Player p = target();
            return !dueling && leaveTicks < 0 && p != null && !p.isSpectator() && distanceToSqr(p) > 4 * 4 && distanceToSqr(p) < 64 * 64;
        }

        @Override
        public boolean canContinueToUse() {
            Player p = target();
            return !dueling && leaveTicks < 0 && p != null && distanceToSqr(p) > 2.5 * 2.5 && distanceToSqr(p) < 64 * 64;
        }

        @Override
        public void tick() {
            Player p = target();
            if (p == null) return;
            getLookControl().setLookAt(p, 30f, 30f);
            if (--repath <= 0) {
                repath = 10;
                getNavigation().moveTo(p, distanceToSqr(p) > 12 * 12 ? 1.0 : 0.7);
            }
        }

        @Override
        public void stop() { getNavigation().stop(); }
    }

    // ------------------------------------------------------------------ state

    public void setTaxpayer(UUID player) { taxpayer = player; }
    @Nullable public UUID taxpayer() { return taxpayer; }
    public boolean isLeaving() { return leaveTicks >= 0; }

    public void setDueling(boolean on) {
        dueling = on;
        if (on) getNavigation().stop();
    }

    /** The bill is settled: he says his line (lang key category) and vanishes a few seconds later. */
    public void leave(@Nullable String lineCat) {
        if (lineCat != null) say(lineCat);
        dueling = false;
        leaveTicks = 60;
        getNavigation().stop();
    }

    public void say(String cat) {
        int n = switch (cat) {
            case "greet" -> LINES_GREET;
            case "wait" -> LINES_WAIT;
            case "paid" -> LINES_PAID;
            case "won" -> LINES_WON;
            case "took" -> LINES_TOOK;
            case "duel" -> LINES_DUEL;
            default -> 1;
        };
        NpcLines.speak(this, Component.translatable("tcgshop.mayor.line." + cat + "." + random.nextInt(n)));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount); // /kill, void
        if (!level().isClientSide && source.getEntity() instanceof ServerPlayer sp && random.nextInt(2) == 0)
            sp.displayClientMessage(Component.translatable("tcgshop.mayor.immune").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    @Override
    public boolean isPushable() { return !dueling; }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) {
            if (leaveTicks >= 0) return InteractionResult.sidedSuccess(level().isClientSide);
            if (sp.getUUID().equals(taxpayer)) {
                playSound(SoundEvents.VILLAGER_AMBIENT, 1f, 0.8f);
                getNavigation().stop();
                TaxOffice.open(sp, this);
            } else {
                sp.displayClientMessage(Component.translatable("tcgshop.mayor.not_you", getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (!hasCustomName()) {
            setCustomName(Component.translatable("tcgshop.mayor.name", NpcIdentity.randomName(random)).withStyle(ChatFormatting.GOLD));
            setCustomNameVisible(true);
            entityData.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (leaveTicks >= 0) {
            if (--leaveTicks <= 0) poof();
            return;
        }
        if (dueling) {
            getNavigation().stop();
            return;
        }
        ServerPlayer p = taxpayer == null ? null : (ServerPlayer) level().getPlayerByUUID(taxpayer);
        if (p == null || p.isSpectator() || distanceToSqr(p) > 96 * 96) {
            if (++awayTicks >= AWAY_LIMIT) poof(); // the TaxOffice sends him again later; the bill stays open
        } else {
            awayTicks = 0;
            if (tickCount % 400 == 200 && random.nextInt(2) == 0) say(distanceToSqr(p) < 8 * 8 ? "wait" : "greet");
        }
        if (tickCount % 100 == 0 && level() instanceof ServerLevel sw && taxpayer != null && !TaxOffice.expects(sw.getServer(), taxpayer, getUUID()))
            leave(null); // settled elsewhere, or another Mayor took over
    }

    private void poof() {
        if (level() instanceof ServerLevel w)
            w.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 16, 0.3, 0.6, 0.3, 0.02);
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    // ------------------------------------------------------------------ fallback battles (CobblemonBattles.Fighter)

    @Override
    public String[] battlePool() {
        return dev.tcgshop.compat.MayorBattle.allSpecies();
    }

    @Override
    public void onBattleEnd(ServerPlayer player, boolean playerWon) {
        TaxOffice.onFallbackBattle(player, this, playerWon);
    }

    // ------------------------------------------------------------------ save

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (taxpayer != null) nbt.putUUID("Taxpayer", taxpayer);
        nbt.putInt("Leave", leaveTicks);
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        taxpayer = nbt.hasUUID("Taxpayer") ? nbt.getUUID("Taxpayer") : null;
        leaveTicks = nbt.contains("Leave") ? nbt.getInt("Leave") : -1;
        entityData.set(SKIN, nbt.contains("NpcSkin") ? nbt.getInt("NpcSkin") : 1);
        dueling = false; // a battle never survives a reload (TaxOffice resolves it)
    }
}
