package dev.tcgshop.luxury.house;

import dev.tcgshop.TcgShop;
import dev.tcgshop.luxury.LuxuryConfig;
import dev.tcgshop.shop.ShopDisplayBlock;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Mansion construction, spread over many ticks (in-memory queue, one build is handled at a time):
 *  0. clear, top to bottom, whatever is in the way above the ground (without neighbor updates: nothing falls or breaks);
 *  1. foundations: fill gaps under the estate with dirt/stone (up to 7 blocks deep);
 *  2. structure bottom to top (with shape updates: stairs, fences and glass panes connect);
 *  3. decoration in design order (furniture, carpets, lanterns, water, beds, doors...);
 *  4. resident Pokémon;  5. done.
 * Each tick places at most {@code mansionBlocksPerTick} blocks and spends at most ~10 ms (less if the server is already
 * under load). The estate's chunks are kept loaded with a dedicated ticket while the build lasts. A block that
 * fails (a block from another mod that throws when placed...) is logged and skipped: it does not stop the build. If something
 * serious brings down the whole build, it is put back in the queue (from the start, skipping what is already fine) without
 * waiting for a restart. If the server stops midway nothing bad happens: the build is marked unfinished in {@link MansionState}
 * and resumes on the next startup. At most {@link #MAX_ATTEMPTS} attempts; after that, an op can use
 * {@code /tcgshop debug mansion resume|remove <dueño>}.
 */
public final class MansionBuilder {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("tcgshop_mansion", Comparator.comparingLong(ChunkPos::toLong));
    /** Time budget per tick with the server idle / busy / very busy. */
    private static final long NANOS_IDLE = 10_000_000L, NANOS_BUSY = 6_000_000L, NANOS_LAGGING = 3_000_000L;
    /** Build attempts (serious failures + resumes on startup) before giving up. */
    public static final int MAX_ATTEMPTS = 5;
    /** Failed blocks in a build before treating it as broken (something is very wrong if that many fail). */
    private static final int MAX_BLOCK_ERRORS = 2000;
    /** Edge foundations go down until they reach solid ground, at most this many blocks below Y_MIN. */
    static final int EDGE_FILL_EXTRA = 24;
    private static final List<Job> JOBS = new ArrayList<>();

    private MansionBuilder() {}

    private static final class Job {
        final ServerLevel world;
        final MansionState.Record rec;
        final MansionDesign design;
        final MansionPlan plan;
        final List<MansionDesign.Deco> decor;
        final Map<BlockState, BlockState> rotated = new HashMap<>();
        final List<ChunkPos> chunks = new ArrayList<>();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int phase, cursor, lastPct = -1, errors;
        boolean ticketed;

        Job(ServerLevel world, MansionState.Record rec, MansionDesign design) {
            this.world = world;
            this.rec = rec;
            this.design = design;
            this.plan = rec.plan();
            this.decor = new ArrayList<>(design.decor());
        }
    }

    /** Does that player have a build in progress? */
    public static boolean busy(java.util.UUID owner) {
        for (Job j : JOBS) if (j.rec.owner.equals(owner)) return true;
        return false;
    }

    public static int queued() {
        return JOBS.size();
    }

    /** Adds the build to the queue. */
    static void start(ServerLevel world, MansionState.Record rec, MansionDesign design) {
        JOBS.add(new Job(world, rec, design));
    }

    /** On server stop: the queue is forgotten (chunk tickets go away with the server). */
    static void clear() {
        JOBS.clear();
    }

    /** On startup: resumes builds that were left unfinished. */
    static void resumeAll(MinecraftServer server) {
        for (ServerLevel w : server.getAllLevels()) {
            MansionState st = MansionState.get(w);
            for (MansionState.Record r : st.all()) {
                if (r.done || r.attempts >= MAX_ATTEMPTS) continue;
                r.attempts++;
                st.setDirty();
                try {
                    start(w, r, MansionDesign.generate(r.seed));
                    TcgShop.LOGGER.info("[mansion] resuming {}'s build at {} (attempt {})", r.ownerName, r.origin, r.attempts);
                } catch (Throwable t) {
                    TcgShop.LOGGER.error("[mansion] could not resume {}'s build", r.ownerName, t);
                }
            }
        }
    }

    // ------------------------------------------------------------------ tick

    static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Job job = JOBS.get(0);
        try {
            if (step(job)) finish(job, true);
        } catch (Throwable t) {
            TcgShop.LOGGER.error("[mansion] {}'s build failed in phase {}", job.rec.ownerName, job.phase, t);
            retry(job);
        }
    }

    /** Serious failure: the build goes back into the queue (at the end) without waiting for a restart, up to MAX_ATTEMPTS attempts. */
    private static void retry(Job job) {
        if (job.rec.attempts >= MAX_ATTEMPTS) {
            finish(job, false);
            return;
        }
        JOBS.remove(job);
        releaseTickets(job);
        job.rec.attempts++;
        MansionState.get(job.world).setDirty();
        JOBS.add(new Job(job.world, job.rec, job.design));
        TcgShop.LOGGER.warn("[mansion] {}'s build goes back into the queue (attempt {})", job.rec.ownerName, job.rec.attempts);
        ServerPlayer owner = job.world.getServer().getPlayerList().getPlayer(job.rec.owner);
        if (owner != null) owner.displayClientMessage(Component.translatable("tcgshop.mansion.retrying", job.rec.attempts).withStyle(ChatFormatting.YELLOW), false);
    }

    /** Op command: re-queues that record's build (repairs whatever is missing). false if it is already running. */
    static boolean requeue(ServerLevel world, MansionState.Record rec) {
        if (running(rec)) return false;
        MansionDesign design = MansionDesign.generate(rec.seed);
        rec.done = false;
        rec.attempts = 1;
        MansionState.get(world).setDirty();
        start(world, rec, design);
        return true;
    }

    /** Op command: removes that record's build from the queue (if it was running). */
    static void cancel(MansionState.Record rec) {
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (!j.rec.id.equals(rec.id)) continue;
            it.remove();
            releaseTickets(j);
        }
    }

    static boolean running(MansionState.Record rec) {
        for (Job j : JOBS) if (j.rec.id.equals(rec.id)) return true;
        return false;
    }

    /** This tick's budget: smaller the more loaded the server already is (average of the last ticks). */
    private static long budget(MinecraftServer server) {
        long avg;
        try {
            avg = server.getAverageTickTimeNanos();
        } catch (Throwable t) {
            return NANOS_BUSY;
        }
        if (avg > 40_000_000L) return NANOS_LAGGING;
        if (avg > 25_000_000L) return NANOS_BUSY;
        return NANOS_IDLE;
    }

    private static void finish(Job job, boolean ok) {
        JOBS.remove(job);
        releaseTickets(job);
        MansionState st = MansionState.get(job.world);
        if (ok) job.rec.done = true;
        st.setDirty();
        ServerPlayer owner = job.world.getServer().getPlayerList().getPlayer(job.rec.owner);
        if (owner == null) return;
        if (ok) {
            owner.displayClientMessage(Component.translatable("tcgshop.mansion.done").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            owner.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        } else {
            owner.displayClientMessage(Component.translatable("tcgshop.mansion.failed").withStyle(ChatFormatting.RED), false);
        }
    }

    private static void ensureTickets(Job job) {
        if (job.ticketed) return;
        job.ticketed = true;
        AABB b = job.plan.box();
        int cx0 = ((int) Math.floor(b.minX)) >> 4, cx1 = ((int) Math.floor(b.maxX - 1)) >> 4;
        int cz0 = ((int) Math.floor(b.minZ)) >> 4, cz1 = ((int) Math.floor(b.maxZ - 1)) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                job.world.getChunkSource().addRegionTicket(TICKET, cp, 1, cp);
                job.chunks.add(cp);
            }
    }

    private static void releaseTickets(Job job) {
        for (ChunkPos cp : job.chunks) {
            try {
                job.world.getChunkSource().removeRegionTicket(TICKET, cp, 1, cp);
            } catch (Throwable ignored) {}
        }
        job.chunks.clear();
    }

    /** One tick of work. true = build finished. */
    private static boolean step(Job job) {
        ensureTickets(job);
        int maxSets = LuxuryConfig.INSTANCE.mansionBlocksPerTick, maxChecks = maxSets * 12;
        long deadline = System.nanoTime() + budget(job.world.getServer());
        int sets = 0, checks = 0;
        ServerLevel w = job.world;
        final int layer = MansionDesign.W * MansionDesign.D;
        int iter = 0;
        while (sets < maxSets && checks < maxChecks) {
            if ((++iter & 63) == 0 && System.nanoTime() > deadline) break;
            try {
            switch (job.phase) {
                case 0 -> { // clear, top to bottom, only above the ground
                    int total = (MansionDesign.Y_MAX + 1) * layer;
                    if (job.cursor >= total) { next(job); continue; }
                    int c = job.cursor++;
                    int y = MansionDesign.Y_MAX - c / layer, rem = c % layer, z = rem / MansionDesign.W, x = rem % MansionDesign.W;
                    checks++;
                    if (job.design.cell(x, y, z) > MansionDesign.AIR) continue; // structure goes there: replaced later
                    job.plan.world(x, y, z, job.pos);
                    if (!w.getBlockState(job.pos).isAir()) {
                        w.setBlock(job.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        sets++;
                    }
                }
                case 1 -> { // foundations: fill gaps under the grass layer
                    if (job.cursor >= layer) { next(job); continue; }
                    int c = job.cursor++;
                    int z = c / MansionDesign.W, x = c % MansionDesign.W;
                    boolean firm = false;
                    for (int y = -2; y >= MansionDesign.Y_MIN; y--) {
                        checks++;
                        if (job.design.cell(x, y, z) != MansionDesign.KEEP) continue; // placed by the structure
                        job.plan.world(x, y, z, job.pos);
                        int r = fillCell(w, job.pos, y >= -4 ? Blocks.DIRT : Blocks.STONE);
                        if (r < 0) { firm = true; break; }
                        sets += r;
                    }
                    // edges: over a ravine, a cliff or a cave keep going down to solid ground so that
                    // the estate isn't left hanging with its sides exposed
                    boolean edge = x == 0 || z == 0 || x == MansionDesign.W - 1 || z == MansionDesign.D - 1;
                    if (!firm && edge) {
                        for (int y = MansionDesign.Y_MIN - 1; y >= MansionDesign.Y_MIN - EDGE_FILL_EXTRA; y--) {
                            checks++;
                            job.plan.world(x, y, z, job.pos);
                            if (job.pos.getY() < w.getMinBuildHeight()) break;
                            int r = fillCell(w, job.pos, Blocks.STONE);
                            if (r < 0) break;
                            sets += r;
                        }
                    }
                }
                case 2 -> { // structure, bottom to top
                    int total = MansionDesign.H * layer;
                    if (job.cursor >= total) { next(job); continue; }
                    int c = job.cursor++;
                    int y = MansionDesign.Y_MIN + c / layer, rem = c % layer, z = rem / MansionDesign.W, x = rem % MansionDesign.W;
                    short v = job.design.cell(x, y, z);
                    if (v <= MansionDesign.AIR) continue;
                    checks++;
                    BlockState target = rotate(job, job.design.stateOf(v));
                    job.plan.world(x, y, z, job.pos);
                    if (w.getBlockState(job.pos) != target) {
                        w.setBlock(job.pos, target, Block.UPDATE_CLIENTS);
                        sets++;
                    }
                }
                case 3 -> { // decoration
                    if (job.cursor >= job.decor.size()) { next(job); continue; }
                    MansionDesign.Deco d = job.decor.get(job.cursor++);
                    checks++;
                    BlockState target = rotate(job, d.state());
                    BlockPos p = job.plan.world(d.x(), d.y(), d.z());
                    job.pos.set(p); // for the log if it fails
                    if (w.getBlockState(p) == target) continue;
                    w.setBlock(p, target, Block.UPDATE_CLIENTS);
                    sets++;
                    if (d.hook()) placedHook(job, p, target);
                }
                case 4 -> { // residents (a failure here doesn't break the build: the house is already done)
                    if (LuxuryConfig.INSTANCE.mansionPokemon && !job.rec.residentsSpawned) {
                        job.rec.residentsSpawned = true; // before trying: never repeats in a loop
                        MansionState.get(w).setDirty();
                        try {
                            int n = MansionPokemon.spawn(w, job.rec, job.design, job.plan);
                            if (n > 0) TcgShop.LOGGER.info("[mansion] {} resident Pokémon on {}'s estate", n, job.rec.ownerName);
                        } catch (Throwable t) {
                            TcgShop.LOGGER.error("[mansion] could not spawn {}'s residents", job.rec.ownerName, t);
                        }
                    }
                    next(job);
                }
                default -> {
                    return true;
                }
            }
            } catch (Throwable t) {
                // a specific block failed: log it and continue (the cursor already advanced)
                job.errors++;
                if (job.errors <= 20)
                    TcgShop.LOGGER.warn("[mansion] phase {}: block at {} failed ({}); skipping", job.phase, job.pos.toShortString(), t.toString());
                if (job.errors >= MAX_BLOCK_ERRORS) throw new IllegalStateException("too many failed blocks: " + job.errors, t);
            }
        }
        progress(job);
        return job.phase >= 5;
    }

    /**
     * Foundations: fills that cell if it is empty. -1 = solid ground already (the column stops), 0 = left untouched (block with
     * data: a chest or similar is never overwritten), 1 = filled.
     */
    private static int fillCell(ServerLevel w, BlockPos pos, Block fill) {
        BlockState cur = w.getBlockState(pos);
        if (cur.isRedstoneConductor(w, pos)) return -1;
        if (w.getBlockEntity(pos) != null) return 0;
        w.setBlock(pos, fill.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return 1;
    }

    private static void next(Job job) {
        job.phase++;
        job.cursor = 0;
    }

    /** Statues and displays: upper parts (onPlaced) and owner = the estate's owner. */
    private static void placedHook(Job job, BlockPos p, BlockState state) {
        ServerPlayer owner = job.world.getServer().getPlayerList().getPlayer(job.rec.owner);
        try {
            state.getBlock().setPlacedBy(job.world, p, state, owner, new ItemStack(state.getBlock()));
        } catch (Throwable t) {
            TcgShop.LOGGER.warn("[mansion] onPlaced of {} failed: {}", state.getBlock(), t.toString());
        }
        if (state.getBlock() instanceof ShopDisplayBlock) {
            ShopDisplayBlockEntity be = ShopDisplayBlock.masterEntity(job.world, state, p);
            if (be != null) {
                be.owner = job.rec.owner;
                be.changed();
            }
        }
    }

    /** State rotated to the world orientation (cached). */
    private static BlockState rotate(Job job, BlockState s) {
        return job.rotated.computeIfAbsent(s, k -> {
            BlockState r = k.rotate(job.plan.rotation);
            Direction f = facingOf(k);
            // furniture from other mods that doesn't implement rotate(): force its "facing"
            if (f != null) r = MansionDesign.face(r, job.plan.dir(f));
            return r;
        });
    }

    @Nullable
    private static Direction facingOf(BlockState s) {
        for (Property<?> p : s.getProperties())
            if (p.getName().equals("facing") && s.getValue(p) instanceof Direction d && d.getAxis().isHorizontal()) return d;
        return null;
    }

    private static void progress(Job job) {
        int layer = MansionDesign.W * MansionDesign.D;
        double[] weight = {0.25, 0.05, 0.5, 0.18, 0.02};
        double done = 0;
        for (int i = 0; i < Math.min(job.phase, 5); i++) done += weight[i];
        if (job.phase < 5) {
            int total = switch (job.phase) {
                case 0 -> (MansionDesign.Y_MAX + 1) * layer;
                case 1 -> layer;
                case 2 -> MansionDesign.H * layer;
                case 3 -> Math.max(1, job.decor.size());
                default -> 1;
            };
            done += weight[job.phase] * Math.min(1.0, job.cursor / (double) total);
        }
        int pct = (int) Math.floor(done * 100);
        if (pct / 5 == job.lastPct / 5 && job.lastPct >= 0) return;
        job.lastPct = pct;
        ServerPlayer owner = job.world.getServer().getPlayerList().getPlayer(job.rec.owner);
        if (owner != null) owner.displayClientMessage(Component.translatable("tcgshop.mansion.progress", pct).withStyle(ChatFormatting.GOLD), true);
    }

    /** Is there a build in progress that overlaps that box? */
    static boolean overlapsJob(ServerLevel w, AABB box) {
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (j.world == w && j.plan.box().intersects(box)) return true;
        }
        return false;
    }
}
