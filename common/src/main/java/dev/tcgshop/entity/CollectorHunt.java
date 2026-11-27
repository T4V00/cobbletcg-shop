package dev.tcgshop.entity;

import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.npc.NpcConfig;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.1: the hunting AI of a robbed Wandering Collector (FEATURES_1_1_B D2). Only robbed collectors use it
 * ({@link HuntGoal} replaces their old melee goal); CollectorTheft still decides who is hunted and every outcome.
 * <ul>
 *   <li><b>Walk and hit</b>: paths to the thief and hits when in reach (his health/damage come from EconomyConfig).</li>
 *   <li><b>Blocks</b>: when there is no full path or he is stuck, he breaks what is in his way (feet and head level,
 *       or below him to follow you down), places blocks to bridge gaps, builds steps and pillars up to reach you. Never:
 *       blocks inside any shop zone, bedrock/unbreakable or harder than collectorMaxHardness, liquids, anything with a
 *       block entity (chests, furnaces, displays...), doors, beds and other two-part blocks, nor a block something
 *       depends on (torches, signs, rails, carpets, crops, ladders, item frames, paintings on it; sand or gravel on
 *       top), so breaking never pops anything off. Needs the mobGriefing gamerule. At most collectorMaxBlockEdits per
 *       hunt ({@link TraderEntity#editsMade}, teleport restores don't reset it), and every edit is put back when he
 *       leaves, dies or follows a teleport ({@link #restore}; saved in his NBT while he hunts). Edits that can't be
 *       put back yet (chunk unloaded, someone standing in or on the spot) wait in {@link PendingRestores}, saved
 *       with the dimension, and are put back as soon as they can.</li>
 *   <li><b>Elytra</b>: if you fly (elytra) or are far above him with no path, he puts on an elytra and flies after you.</li>
 *   <li><b>Teleports</b>: if you jump more than {@link #TELEPORT_JUMP} blocks in a tick or change dimension, he reappears
 *       about collectorTeleportDistance blocks from you and stands still for {@link #ARRIVAL_STUN} ticks so you can react
 *       (collectorTeleportFollows times per hunt).</li>
 * </ul>
 * The boss bar lives in TraderEntity (shown to players tracking him while he hunts).
 */
public final class CollectorHunt {
    private CollectorHunt() {}

    static final double TELEPORT_JUMP = 24;
    static final int ARRIVAL_STUN = 40;
    /** Fastest flight speed (blocks per tick): a bit slower than a player boosting with rockets. */
    static final double FLY_SPEED = 1.35;

    /** One block he changed: what was there before, and whether he placed it (else he broke it). */
    public record Edit(BlockPos pos, BlockState before, boolean placed) {}

    // ------------------------------------------------------------------ the goal

    /** Chases, hits, digs, builds and flies. Only while robbed, not leaving and with a target (CollectorTheft sets it). */
    static final class HuntGoal extends Goal {
        private final TraderEntity t;
        private int repath, cooldown, stuck, breakTicks, flyTicks, editCooldown;
        private boolean pathOk = true;
        @Nullable private BlockPos breaking;
        private Vec3 lastPos = Vec3.ZERO;

        HuntGoal(TraderEntity t) {
            this.t = t;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Nullable
        private LivingEntity target() {
            LivingEntity e = t.getTarget();
            return e != null && e.isAlive() ? e : null;
        }

        @Override
        public boolean canUse() { return t.robber() != null && t.leaveTicks < 0 && target() != null; }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void stop() {
            t.getNavigation().stop();
            cancelBreak();
            if (t.isHuntFlying()) t.setHuntFlying(false);
        }

        @Override
        public void tick() {
            LivingEntity tgt = target();
            if (tgt == null) return;
            t.getLookControl().setLookAt(tgt, 30f, 30f);
            if (cooldown > 0) cooldown--;
            if (editCooldown > 0) editCooldown--;
            if (t.stunTicks > 0) { // just arrived after following a teleport: a moment to react
                t.stunTicks--;
                t.getNavigation().stop();
                return;
            }
            if (cooldown <= 0 && t.isWithinMeleeAttackRange(tgt) && t.getSensing().hasLineOfSight(tgt)) {
                t.swing(InteractionHand.MAIN_HAND);
                t.doHurtTarget(tgt);
                cooldown = 20;
            }
            NpcConfig c = NpcConfig.INSTANCE;
            boolean targetFlying = tgt instanceof Player p && p.isFallFlying();
            double dy = tgt.getY() - t.getY();
            double hd2 = horizontalDist2(t.position(), tgt.position());
            if (t.isHuntFlying()) {
                flyTicks++;
                fly(tgt);
                boolean landed = (t.onGround() || tgt.onGround() && Math.abs(dy) < 2.5 && hd2 < 6 * 6) && flyTicks > 20 && !targetFlying;
                if (landed || !c.collectorElytra) t.setHuntFlying(false);
                if (breaking != null) continueBreak(); // a block in the way of the flight: keep at it (he is pressed against it)
                else if (t.horizontalCollision && flyTicks % 10 == 0) clearAhead(tgt, true);
                return;
            }
            boolean wantFly = c.collectorElytra && (targetFlying || (dy > 8 && !pathOk && stuck > 20) || (dy > 4 && !tgt.onGround() && hd2 > 16 * 16));
            if (wantFly) {
                t.setHuntFlying(true);
                flyTicks = 0;
                cancelBreak();
                return;
            }
            // walk
            if (--repath <= 0) {
                repath = 8 + t.getRandom().nextInt(5);
                Path path = t.getNavigation().createPath(tgt, 0);
                pathOk = path != null && path.canReach();
                t.getNavigation().moveTo(path, CollectorTheft.CHASE_SPEED);
            }
            if (t.tickCount % 20 == 0) {
                boolean moved = t.position().distanceToSqr(lastPos) > 0.5;
                stuck = moved || t.distanceToSqr(tgt) < 3 * 3 ? 0 : stuck + 20;
                lastPos = t.position();
            }
            if (breaking != null) {
                continueBreak();
                return;
            }
            if ((!pathOk || stuck >= 40) && editCooldown <= 0 && t.distanceToSqr(tgt) > 2.5 * 2.5) {
                editCooldown = 10;
                clearAhead(tgt, false);
            }
        }

        /** Steers toward a point a little above the target. */
        private void fly(LivingEntity tgt) {
            Vec3 to = tgt.position().add(0, tgt.getBbHeight() * 0.6, 0).subtract(t.position());
            double d = to.length();
            if (d < 1e-3) return;
            double speed = Math.min(FLY_SPEED, 0.35 + d * 0.08);
            Vec3 want = to.scale(speed / d);
            t.setDeltaMovement(t.getDeltaMovement().lerp(want, 0.25));
            float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
            t.setYRot(yaw);
            t.setYHeadRot(yaw);
            t.setXRot((float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
            t.fallDistance = 0;
        }

        /** Works out the one block to break or place next to get closer to the target. */
        private void clearAhead(LivingEntity tgt, boolean flying) {
            if (!(t.level() instanceof ServerLevel w)) return;
            BlockPos feet = t.blockPosition();
            Direction dir = Direction.getNearest(tgt.getX() - t.getX(), 0, tgt.getZ() - t.getZ());
            if (dir.getAxis().isVertical()) dir = t.getDirection();
            double dy = tgt.getY() - t.getY();
            double hd2 = horizontalDist2(t.position(), tgt.position());
            if (flying) { // something in the way of the flight: break it
                BlockPos ahead = BlockPos.containing(t.position().add(t.getDeltaMovement().normalize().scale(1.2)).add(0, 0.5, 0));
                startBreak(w, ahead);
                return;
            }
            // 1) target above and close: pillar up (break the block over his head if needed)
            if (dy > 1.5 && hd2 < 2.5 * 2.5) {
                BlockPos head2 = feet.above(2);
                if (!w.getBlockState(head2).getCollisionShape(w, head2).isEmpty()) { startBreak(w, head2); return; }
                if (t.onGround() && place(w, feet, t, true)) t.setPos(t.getX(), feet.getY() + 1.0, t.getZ());
                return;
            }
            // 2) target below: dig down
            if (dy < -2 && hd2 < 3 * 3) {
                startBreak(w, feet.below());
                return;
            }
            BlockPos front = feet.relative(dir);
            boolean feetBlocked = !w.getBlockState(front).getCollisionShape(w, front).isEmpty();
            boolean headBlocked = !w.getBlockState(front.above()).getCollisionShape(w, front.above()).isEmpty();
            // 3) a step up toward a higher target: place a block in front to climb on (head room needed)
            if (dy > 1 && !feetBlocked && !headBlocked && w.getBlockState(front.above(2)).getCollisionShape(w, front.above(2)).isEmpty()) {
                place(w, front, t, false);
                return;
            }
            // 4) walls: break what blocks feet or head level
            if (headBlocked && (feetBlocked || dy <= 0.5)) { startBreak(w, front.above()); return; }
            if (feetBlocked) { startBreak(w, front); return; }
            // 5) a gap in front: bridge it
            if (w.getBlockState(front.below()).getCollisionShape(w, front.below()).isEmpty() && dy > -1.5) place(w, front.below(), t, false);
        }

        private void startBreak(ServerLevel w, BlockPos pos) {
            BlockState st = w.getBlockState(pos);
            if (st.isAir() || !canBreak(w, pos, st, t)) return;
            breaking = pos.immutable();
            breakTicks = 0;
            t.getNavigation().stop();
        }

        private void continueBreak() {
            if (!(t.level() instanceof ServerLevel w) || breaking == null) return;
            BlockState st = w.getBlockState(breaking);
            if (st.isAir() || !canBreak(w, breaking, st, t) || t.distanceToSqr(Vec3.atCenterOf(breaking)) > 5 * 5) { cancelBreak(); return; }
            t.getLookControl().setLookAt(Vec3.atCenterOf(breaking));
            int need = Math.max(10, Math.round(st.getDestroySpeed(w, breaking) * 20));
            breakTicks++;
            if (breakTicks % 4 == 0) t.swing(InteractionHand.MAIN_HAND);
            w.destroyBlockProgress(t.getId(), breaking, Math.min(9, breakTicks * 10 / need));
            if (breakTicks < need) return;
            w.destroyBlockProgress(t.getId(), breaking, -1);
            breakBlock(w, breaking, t);
            breaking = null;
            repath = 0;
        }

        private void cancelBreak() {
            if (breaking != null && t.level() instanceof ServerLevel w) w.destroyBlockProgress(t.getId(), breaking, -1);
            breaking = null;
        }
    }

    static double horizontalDist2(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    // ------------------------------------------------------------------ block rules (public: the selftest checks them)

    private static boolean editsLeft(TraderEntity t) {
        return t.editsMade < NpcConfig.INSTANCE.collectorMaxBlockEdits;
    }

    private static boolean griefing(ServerLevel w) {
        return w.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** May a hunting collector break this block? (see the class comment for the rules) */
    public static boolean canBreak(ServerLevel w, BlockPos pos, BlockState st, @Nullable TraderEntity t) {
        NpcConfig c = NpcConfig.INSTANCE;
        if (!c.collectorBreakBlocks || !griefing(w) || (t != null && !editsLeft(t))) return false;
        if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) return false;
        float hard = st.getDestroySpeed(w, pos);
        if (hard < 0 || hard > c.collectorMaxHardness) return false;
        if (st.is(BlockTags.WITHER_IMMUNE) || st.is(BlockTags.DRAGON_IMMUNE)) return false;
        if (st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || st.hasProperty(BlockStateProperties.BED_PART)) return false;
        if (st.getBlock() instanceof dev.tcgshop.shop.ShopFurniture) return false;
        if (hasDependents(w, pos)) return false;
        return !inShop(w, pos);
    }

    /**
     * Would breaking this block make something else pop off or fall? Torches, signs, banners, buttons, levers, carpets,
     * crops, ladders, vines on its sides or top; anything not a full block (rails, slabs, beds...) or a falling block
     * (sand, gravel) on top; an item frame or painting hanging on it. Such blocks are never broken (restore() could
     * not bring those back).
     */
    public static boolean hasDependents(ServerLevel w, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState ns = w.getBlockState(n);
            if (ns.isAir()) continue;
            if (ns.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
            // signs and banners hang on any side (block entities, so their push reaction says nothing); a chest
            // or furnace next to it stands on its own
            if (ns.is(BlockTags.ALL_SIGNS) || ns.is(BlockTags.BANNERS) || ns.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.DESTROY
                    || ns.is(BlockTags.CLIMBABLE)) return true;
            if (d == Direction.UP && (ns.getBlock() instanceof net.minecraft.world.level.block.FallingBlock || !ns.isCollisionShapeFullBlock(w, n)))
                return true;
        }
        return !w.getEntitiesOfClass(net.minecraft.world.entity.decoration.HangingEntity.class, new AABB(pos).inflate(0.1)).isEmpty();
    }

    /** May he put a block here? (air or replaceable, no fluid source, nothing standing in it, outside every shop) */
    public static boolean canPlace(ServerLevel w, BlockPos pos, @Nullable TraderEntity t, @Nullable Entity ignore) {
        NpcConfig c = NpcConfig.INSTANCE;
        if (!c.collectorPlaceBlocks || !griefing(w) || (t != null && !editsLeft(t))) return false;
        BlockState st = w.getBlockState(pos);
        if (!st.canBeReplaced() || st.hasBlockEntity() || w.isOutsideBuildHeight(pos)) return false;
        if (st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return false; // tall grass: the other half would pop
        if (st.getFluidState().isSource()) return false;
        if (inShop(w, pos)) return false;
        return w.getEntities(ignore, new AABB(pos), e -> e instanceof LivingEntity && e.isAlive()).isEmpty();
    }

    static boolean inShop(ServerLevel w, BlockPos pos) {
        if (ShopZones.get(w).at(pos) != null) return true;
        // just outside a wall still counts as the shop (no tunnels into it from the side)
        for (Direction d : Direction.values()) if (ShopZones.get(w).at(pos.relative(d)) != null) return true;
        return false;
    }

    /** Breaks it without drops and remembers it (put back by restore). */
    static void breakBlock(ServerLevel w, BlockPos pos, TraderEntity t) {
        BlockState st = w.getBlockState(pos);
        if (!canBreak(w, pos, st, t)) return;
        t.huntEdits.add(new Edit(pos.immutable(), st, false));
        t.editsMade++;
        w.levelEvent(2001, pos, Block.getId(st)); // break particles + sound
        w.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Places his building block (cobblestone) and remembers it. selfInside: he stands there (pillaring up, he is lifted next). */
    static boolean place(ServerLevel w, BlockPos pos, TraderEntity t, boolean selfInside) {
        if (!canPlace(w, pos, t, selfInside ? t : null)) return false;
        t.huntEdits.add(new Edit(pos.immutable(), w.getBlockState(pos), true));
        t.editsMade++;
        w.setBlock(pos, PLACED_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        w.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1f, 1f);
        return true;
    }

    // ------------------------------------------------------------------ restore

    /**
     * Puts every block he changed back (placed ones are removed if still his; broken ones come back if the spot is
     * free). An edit in an unloaded chunk, or with someone standing in the spot (a block coming back would trap them)
     * or on it (removing his cobblestone would drop them), is handed to {@link PendingRestores} instead of being lost.
     */
    public static void restore(TraderEntity t) {
        if (t.huntEdits.isEmpty() || !(t.level() instanceof ServerLevel w)) return;
        if (!NpcConfig.INSTANCE.collectorRestoreBlocks) {
            t.huntEdits.clear();
            return;
        }
        List<Edit> later = new ArrayList<>();
        for (int i = t.huntEdits.size() - 1; i >= 0; i--) {
            Edit e = t.huntEdits.get(i);
            if (tryRestore(w, e, t) == Restored.LATER) later.add(e);
        }
        t.huntEdits.clear();
        if (!later.isEmpty()) PendingRestores.get(w).addAll(later, w.getGameTime());
    }

    enum Restored { DONE, LATER, GONE }

    /** One edit back: DONE, LATER (unloaded or occupied: try again), GONE (the spot was changed since: leave it). */
    static Restored tryRestore(ServerLevel w, Edit e, @Nullable Entity ignore) {
        if (!w.isLoaded(e.pos())) return Restored.LATER;
        BlockState now = w.getBlockState(e.pos());
        if (e.placed()) {
            if (!now.is(PLACED_BLOCK)) return Restored.GONE;
            if (occupied(w, new AABB(e.pos().above()), ignore)) return Restored.LATER; // someone stands on it
            w.setBlock(e.pos(), e.before(), Block.UPDATE_ALL);
            return Restored.DONE;
        }
        if (!(now.isAir() || now.canBeReplaced() && !now.hasBlockEntity())) return Restored.GONE;
        if (!e.before().getCollisionShape(w, e.pos()).isEmpty() && occupied(w, new AABB(e.pos()), ignore)) return Restored.LATER;
        w.setBlock(e.pos(), e.before(), Block.UPDATE_ALL);
        return Restored.DONE;
    }

    private static boolean occupied(ServerLevel w, AABB box, @Nullable Entity ignore) {
        return !w.getEntities(ignore, box, en -> en instanceof LivingEntity && en.isAlive()).isEmpty();
    }

    /**
     * Collector edits that could not be put back yet (unloaded chunk, someone in the way), per dimension and saved
     * with it. Retried every 2 seconds; an edit whose spot was changed by someone else since is dropped.
     */
    public static final class PendingRestores extends net.minecraft.world.level.saveddata.SavedData {
        private static final Factory<PendingRestores> TYPE = new Factory<>(PendingRestores::new, PendingRestores::fromNbt, null);
        /** Hard cap (oldest dropped first) so a broken setup can never grow the file without end. */
        static final int MAX = 8192;
        private final List<Edit> edits = new ArrayList<>();

        public static PendingRestores get(ServerLevel w) {
            return w.getDataStorage().computeIfAbsent(TYPE, "tcgshop_hunt_restore");
        }

        public int size() { return edits.size(); }

        void addAll(List<Edit> list, long now) {
            edits.addAll(list);
            while (edits.size() > MAX) edits.remove(0);
            setDirty();
        }

        /** Tries every waiting edit once (newest first, like restore). Returns how many were put back. */
        int retry(ServerLevel w) {
            if (edits.isEmpty()) return 0;
            int done = 0;
            for (int i = edits.size() - 1; i >= 0; i--) {
                Restored r = tryRestore(w, edits.get(i), null);
                if (r == Restored.LATER) continue;
                if (r == Restored.DONE) done++;
                edits.remove(i);
                setDirty();
            }
            return done;
        }

        static PendingRestores fromNbt(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider lookup) {
            PendingRestores s = new PendingRestores();
            var blocks = lookup.lookupOrThrow(Registries.BLOCK);
            ListTag l = nbt.getList("edits", 10);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                s.edits.add(new Edit(BlockPos.of(c.getLong("pos")), NbtUtils.readBlockState(blocks, c.getCompound("state")), c.getBoolean("placed")));
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider lookup) {
            nbt.put("edits", saveEdits(edits));
            return nbt;
        }
    }

    static final Block PLACED_BLOCK = Blocks.COBBLESTONE;

    static ListTag saveEdits(List<Edit> edits) {
        ListTag l = new ListTag();
        for (Edit e : edits) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", e.pos().asLong());
            c.put("state", NbtUtils.writeBlockState(e.before()));
            c.putBoolean("placed", e.placed());
            l.add(c);
        }
        return l;
    }

    static void loadEdits(Level level, ListTag l, List<Edit> out) {
        out.clear();
        var blocks = level.holderLookup(Registries.BLOCK);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            out.add(new Edit(BlockPos.of(c.getLong("pos")), NbtUtils.readBlockState(blocks, c.getCompound("state")), c.getBoolean("placed")));
        }
    }

    // ------------------------------------------------------------------ flight

    /** Puts on (or takes off) his elytra (unbreakable: vanilla wears it down while gliding); TraderEntity.travel steers him. */
    static void equipElytra(TraderEntity t, boolean on) {
        if (on) {
            if (!t.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                ItemStack el = new ItemStack(Items.ELYTRA);
                el.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(false));
                t.setItemSlot(EquipmentSlot.CHEST, el);
                t.setDropChance(EquipmentSlot.CHEST, 0f);
            }
        } else if (t.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            t.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ teleport following

    private record Seen(ResourceKey<Level> dim, Vec3 pos) {}

    /** Where each hunted thief was last tick (collector uuid -> thief position). */
    private static final Map<UUID, Seen> LAST = new HashMap<>();
    /** Teleport follows still to do (collector uuid -> ticks left to retry: the chunks at the arrival may not be loaded yet). */
    private static final Map<UUID, Integer> PENDING_FOLLOW = new HashMap<>();
    static final int FOLLOW_RETRY_TICKS = 100;

    public static void register() {
        ServerEvents.TICK_END.register(CollectorHunt::tick);
        ServerEvents.STOPPING.register(s -> {
            LAST.clear();
            PENDING_FOLLOW.clear();
        });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 == 17) // edits that could not be put back yet
            for (ServerLevel w : server.getAllLevels()) {
                try {
                    PendingRestores.get(w).retry(w);
                } catch (Throwable e) {
                    dev.tcgshop.TcgShop.LOGGER.warn("[collector] pending block restore failed in {}: {}", w.dimension().location(), e.toString());
                }
            }
        CollectorTheft st = CollectorTheft.get(server);
        if (st.huntIds().isEmpty()) {
            if (!LAST.isEmpty()) LAST.clear();
            if (!PENDING_FOLLOW.isEmpty()) PENDING_FOLLOW.clear();
            return;
        }
        LAST.keySet().retainAll(st.huntIds());
        PENDING_FOLLOW.keySet().retainAll(st.huntIds());
        for (UUID cid : List.copyOf(st.huntIds())) {
            UUID thief = st.thiefOf(cid);
            ServerPlayer p = thief == null ? null : server.getPlayerList().getPlayer(thief);
            if (p == null || !p.isAlive() || p.isSpectator()) {
                LAST.remove(cid);
                PENDING_FOLLOW.remove(cid);
                continue;
            }
            Seen now = new Seen(p.level().dimension(), p.position());
            Seen before = LAST.put(cid, now);
            if (before == null) continue;
            boolean jumped = !before.dim().equals(now.dim()) || before.pos().distanceToSqr(now.pos()) > TELEPORT_JUMP * TELEPORT_JUMP;
            if (jumped) PENDING_FOLLOW.put(cid, FOLLOW_RETRY_TICKS);
            Integer left = PENDING_FOLLOW.get(cid);
            if (left == null) continue;
            Follow r = follow(server, cid, p);
            if (r == Follow.RETRY && left > 1) PENDING_FOLLOW.put(cid, left - 1);
            else PENDING_FOLLOW.remove(cid);
        }
    }

    enum Follow { DONE, RETRY, NO }

    /**
     * The thief teleported: he reappears about collectorTeleportDistance blocks from them (if he still can). RETRY:
     * no loaded free spot around the arrival yet (the caller tries again for {@link #FOLLOW_RETRY_TICKS} ticks).
     */
    static Follow follow(MinecraftServer server, UUID cid, ServerPlayer p) {
        NpcConfig c = NpcConfig.INSTANCE;
        if (!c.collectorTeleportFollow) return Follow.NO;
        TraderEntity t = null;
        for (ServerLevel w : server.getAllLevels()) if (w.getEntity(cid) instanceof TraderEntity tr && tr.isAlive()) { t = tr; break; }
        if (t == null || t.leaveTicks >= 0 || t.robber() == null || t.followsUsed >= c.collectorTeleportFollows) return Follow.NO;
        ServerLevel to = p.serverLevel();
        BlockPos spot = ringSpot(to, p.position(), c.collectorTeleportDistance);
        if (spot == null) return Follow.RETRY;
        ServerLevel from = (ServerLevel) t.level();
        from.sendParticles(ParticleTypes.PORTAL, t.getX(), t.getY() + 1, t.getZ(), 40, 0.4, 0.8, 0.4, 0.2);
        restore(t); // his blocks stay where they belong
        t.setHuntFlying(false);
        t.getNavigation().stop();
        Entity moved = t;
        if (from == to) t.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        else {
            t.teleportTo(to, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, java.util.Set.of(), t.getYRot(), t.getXRot());
            moved = to.getEntity(cid);
        }
        if (!(moved instanceof TraderEntity nt)) return Follow.NO;
        nt.followsUsed++;
        nt.stunTicks = ARRIVAL_STUN;
        nt.awayTicks = 0;
        nt.setTarget(p.isCreative() ? null : p);
        CollectorTheft.get(server).seen(cid, server.overworld().getGameTime());
        to.sendParticles(ParticleTypes.REVERSE_PORTAL, nt.getX(), nt.getY() + 1, nt.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
        to.playSound(null, nt.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 0.8f);
        int left = c.collectorTeleportFollows - nt.followsUsed;
        p.displayClientMessage(Component.translatable("tcgshop.collector_hunt.followed", nt.getDisplayName(), Math.round(Math.sqrt(nt.distanceToSqr(p))), left)
                .withStyle(ChatFormatting.RED), false);
        CollectorTheft.speak(nt, "chase");
        return Follow.DONE;
    }

    /** A free spot about `dist` blocks from the center (ground near the center's height), or null. */
    @Nullable
    public static BlockPos ringSpot(ServerLevel w, Vec3 c, int dist) {
        var r = w.random;
        for (int attempt = 0; attempt < 32; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = dist - 2 + r.nextInt(5);
            int x = (int) Math.floor(c.x + Math.cos(ang) * d), z = (int) Math.floor(c.z + Math.sin(ang) * d);
            if (!w.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int cy = (int) Math.floor(c.y);
            for (int dy = 4; dy >= -8; dy--) {
                BlockPos pos = new BlockPos(x, cy + dy, z);
                if (w.isOutsideBuildHeight(pos)) continue;
                if (!w.getBlockState(pos.below()).isFaceSturdy(w, pos.below(), Direction.UP)) continue;
                if (!w.getBlockState(pos).getCollisionShape(w, pos).isEmpty() || !w.getBlockState(pos.above()).getCollisionShape(w, pos.above()).isEmpty()) continue;
                if (!w.getFluidState(pos).isEmpty()) continue;
                if (ShopZones.get(w).at(pos) != null) continue;
                return pos;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ selftest (npc/NpcSelfTest)

    /**
     * Builds a small test pad at origin and checks: which blocks may be broken (stone yes; chest, bedrock, obsidian, a
     * block inside a shop zone, and anything with mobGriefing off: no), breaking + placing are recorded and restore puts
     * everything back, the elytra flight toggles, a teleport spot lands ~collectorTeleportDistance away, and his NBT keeps
     * the edits. Everything is removed afterwards. Returns a summary line.
     */
    public static String selfTest(ServerLevel w, BlockPos origin) {
        GameRules.BooleanValue rule = w.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean oldRule = rule.get();
        int r = 26;
        List<BlockPos> placed = new ArrayList<>();
        TraderEntity t = null;
        ShopZones.Zone zone = null;
        try {
            rule.set(true, w.getServer());
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                BlockPos f = origin.offset(dx, -1, dz);
                w.setBlock(f, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                placed.add(f);
            }
            BlockPos stone = origin.offset(2, 0, 0), chest = origin.offset(3, 0, 0), bedrock = origin.offset(4, 0, 0), obsidian = origin.offset(5, 0, 0);
            w.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            w.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            w.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
            w.setBlockAndUpdate(obsidian, Blocks.OBSIDIAN.defaultBlockState());
            placed.addAll(List.of(stone, chest, bedrock, obsidian));
            t = TcgEntities.TRADER.create(w);
            if (t == null) throw new IllegalStateException("no collector");
            t.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
            w.addFreshEntity(t);
            if (!canBreak(w, stone, w.getBlockState(stone), t)) throw new IllegalStateException("stone should be breakable");
            if (canBreak(w, chest, w.getBlockState(chest), t)) throw new IllegalStateException("a chest (block entity) must never be broken");
            if (canBreak(w, bedrock, w.getBlockState(bedrock), t)) throw new IllegalStateException("bedrock must never be broken");
            if (canBreak(w, obsidian, w.getBlockState(obsidian), t)) throw new IllegalStateException("obsidian is harder than the limit");
            rule.set(false, w.getServer());
            if (canBreak(w, stone, w.getBlockState(stone), t) || canPlace(w, origin.offset(0, 0, 3), t, null))
                throw new IllegalStateException("mobGriefing off must stop him");
            rule.set(true, w.getServer());
            // shop zones are off limits (and the block right outside their walls)
            zone = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", origin.offset(-12, 0, -12), dev.tcgshop.shop.ShopTier.PAPER.rank());
            ShopZones.get(w).add(zone);
            BlockPos inZone = zone.core.above();
            w.setBlockAndUpdate(inZone, Blocks.STONE.defaultBlockState());
            placed.add(inZone);
            if (canBreak(w, inZone, w.getBlockState(inZone), t)) throw new IllegalStateException("a block inside a shop zone was breakable");
            ShopZones.get(w).remove(zone);
            zone = null;
            // break + place, recorded, then restored
            breakBlock(w, stone, t);
            BlockPos bridge = origin.offset(0, 0, 3);
            boolean put = place(w, bridge, t, false);
            if (!w.getBlockState(stone).isAir() || !put || !w.getBlockState(bridge).is(PLACED_BLOCK) || t.huntEdits.size() != 2)
                throw new IllegalStateException("break/place not recorded: " + t.huntEdits.size());
            CompoundTag nbt = new CompoundTag();
            t.saveWithoutId(nbt);
            TraderEntity copy = TcgEntities.TRADER.create(w);
            if (copy == null) throw new IllegalStateException("no collector copy");
            copy.load(nbt);
            if (copy.huntEdits.size() != 2) throw new IllegalStateException("edits not saved: " + copy.huntEdits.size());
            restore(t);
            if (!w.getBlockState(stone).is(Blocks.STONE) || !w.getBlockState(bridge).isAir() || !t.huntEdits.isEmpty())
                throw new IllegalStateException("restore did not put the blocks back");
            if (t.editsMade != 2) throw new IllegalStateException("the edit budget lost count after a restore: " + t.editsMade);
            // dependent neighbours: a torch on it, sand on top -> never broken (nothing may pop off or fall)
            BlockPos wall = origin.offset(-3, 0, 0), sandBase = origin.offset(-5, 0, 0);
            w.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            w.setBlockAndUpdate(wall.east(), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.EAST));
            w.setBlockAndUpdate(sandBase, Blocks.STONE.defaultBlockState());
            w.setBlockAndUpdate(sandBase.above(), Blocks.SAND.defaultBlockState());
            placed.addAll(List.of(wall.east(), wall, sandBase.above(), sandBase));
            if (canBreak(w, wall, w.getBlockState(wall), t)) throw new IllegalStateException("a block with a torch on it was breakable");
            if (canBreak(w, sandBase, w.getBlockState(sandBase), t)) throw new IllegalStateException("a block under sand was breakable");
            // someone standing in the spot: the block waits (pending) instead of trapping them, then comes back
            breakBlock(w, stone, t);
            TraderEntity blocker = TcgEntities.TRADER.create(w);
            if (blocker == null) throw new IllegalStateException("no blocker");
            blocker.moveTo(stone.getX() + 0.5, stone.getY(), stone.getZ() + 0.5, 0, 0);
            w.addFreshEntity(blocker);
            PendingRestores pend = PendingRestores.get(w);
            int before = pend.size();
            restore(t);
            boolean waited = w.getBlockState(stone).isAir() && pend.size() == before + 1;
            blocker.discard();
            pend.retry(w);
            if (!waited || !w.getBlockState(stone).is(Blocks.STONE) || pend.size() > before)
                throw new IllegalStateException("an occupied spot was not deferred / restored later (waited " + waited + ")");
            // the edit budget
            int max = NpcConfig.INSTANCE.collectorMaxBlockEdits;
            t.editsMade = max;
            if (canBreak(w, stone, w.getBlockState(stone), t)) throw new IllegalStateException("the edit budget is not enforced");
            t.editsMade = 0;
            // flight on / off
            t.setHuntFlying(true);
            boolean flyOk = t.isHuntFlying() && t.isNoGravity() && t.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
            // saved mid-flight: comes back flying (and lands normally later), never stuck without gravity
            CompoundTag fn = new CompoundTag();
            t.saveWithoutId(fn);
            TraderEntity flier = TcgEntities.TRADER.create(w);
            if (flier == null) throw new IllegalStateException("no collector copy");
            flier.load(fn);
            boolean reloadFly = flier.isHuntFlying() && flier.isNoGravity();
            flier.setHuntFlying(false);
            if (!reloadFly || flier.isNoGravity()) throw new IllegalStateException("flight state not saved: gravity would stay off");
            t.setHuntFlying(false);
            if (!flyOk || t.isNoGravity()) throw new IllegalStateException("elytra flight did not toggle");
            // teleport follow spot
            BlockPos spot = ringSpot(w, Vec3.atBottomCenterOf(origin), NpcConfig.INSTANCE.collectorTeleportDistance);
            if (spot == null) throw new IllegalStateException("no teleport spot on a flat pad");
            double d = Math.sqrt(horizontalDist2(Vec3.atBottomCenterOf(spot), Vec3.atBottomCenterOf(origin)));
            if (d < NpcConfig.INSTANCE.collectorTeleportDistance - 4 || d > NpcConfig.INSTANCE.collectorTeleportDistance + 4)
                throw new IllegalStateException("teleport spot at " + d + " blocks");
            return String.format(java.util.Locale.ROOT, "block rules OK (dependents kept), break/place/restore OK, occupied spot deferred OK, NBT OK, budget %d, flight + save OK, teleport spot %.1f blocks away", max, d);
        } finally {
            rule.set(oldRule, w.getServer());
            if (zone != null) ShopZones.get(w).remove(zone);
            if (t != null) {
                t.huntEdits.clear();
                t.discard();
            }
            for (BlockPos p : placed) w.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
