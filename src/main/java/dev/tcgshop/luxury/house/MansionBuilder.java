package dev.tcgshop.luxury.house;

import dev.tcgshop.TcgShop;
import dev.tcgshop.luxury.LuxuryConfig;
import dev.tcgshop.shop.ShopDisplayBlock;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Obra de la mansión, repartida en muchos ticks (cola en memoria, se atiende una obra a la vez):
 *  0. despejar de arriba abajo lo que sobra por encima del suelo (sin actualizar vecinos: nada se cae ni se rompe);
 *  1. cimientos: rellenar con tierra/piedra los huecos bajo la finca (hasta 7 bloques de hondo);
 *  2. estructura de abajo arriba (con actualizaciones de forma: escaleras, vallas y cristales se conectan);
 *  3. decoración en el orden del diseño (muebles, alfombras, faroles, agua, camas, puertas...);
 *  4. Pokémon residentes;  5. fin.
 * Cada tick coloca como mucho {@code mansionBlocksPerTick} bloques y gasta como mucho ~10 ms (menos si el servidor ya
 * va cargado). Los chunks de la finca se mantienen cargados con un ticket propio mientras dura la obra. Un bloque que
 * falla (un bloque de otro mod que lanza al colocarlo...) se apunta en el log y se salta: no para la obra. Si algo
 * grave tumba la obra entera, se vuelve a poner en la cola (desde el principio, saltando lo que ya está bien) sin
 * esperar a reiniciar. Si el servidor se para a medias no pasa nada: la obra figura sin terminar en {@link MansionState}
 * y se reanuda al arrancar de nuevo. Como mucho {@link #MAX_ATTEMPTS} intentos; después, un op puede usar
 * {@code /tcgshop debug mansion resume|remove <dueño>}.
 */
public final class MansionBuilder {
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("tcgshop_mansion", Comparator.comparingLong(ChunkPos::toLong));
    /** Presupuesto de tiempo por tick con el servidor holgado / cargado / muy cargado. */
    private static final long NANOS_IDLE = 10_000_000L, NANOS_BUSY = 6_000_000L, NANOS_LAGGING = 3_000_000L;
    /** Intentos de obra (fallos graves + reanudaciones al arrancar) antes de rendirse. */
    public static final int MAX_ATTEMPTS = 5;
    /** Bloques que fallan en una obra antes de darla por rota (algo va muy mal si fallan tantos). */
    private static final int MAX_BLOCK_ERRORS = 2000;
    /** Los cimientos de los bordes bajan hasta tocar suelo firme, como mucho estos bloques por debajo de Y_MIN. */
    static final int EDGE_FILL_EXTRA = 24;
    private static final List<Job> JOBS = new ArrayList<>();

    private MansionBuilder() {}

    private static final class Job {
        final ServerWorld world;
        final MansionState.Record rec;
        final MansionDesign design;
        final MansionPlan plan;
        final List<MansionDesign.Deco> decor;
        final Map<BlockState, BlockState> rotated = new HashMap<>();
        final List<ChunkPos> chunks = new ArrayList<>();
        final BlockPos.Mutable pos = new BlockPos.Mutable();
        int phase, cursor, lastPct = -1, errors;
        boolean ticketed;

        Job(ServerWorld world, MansionState.Record rec, MansionDesign design) {
            this.world = world;
            this.rec = rec;
            this.design = design;
            this.plan = rec.plan();
            this.decor = new ArrayList<>(design.decor());
        }
    }

    /** ¿Hay alguna obra de ese jugador en marcha? */
    public static boolean busy(java.util.UUID owner) {
        for (Job j : JOBS) if (j.rec.owner.equals(owner)) return true;
        return false;
    }

    public static int queued() {
        return JOBS.size();
    }

    /** Mete la obra en la cola. */
    static void start(ServerWorld world, MansionState.Record rec, MansionDesign design) {
        JOBS.add(new Job(world, rec, design));
    }

    /** Al parar el servidor: se olvida la cola (los tickets de chunk desaparecen con el servidor). */
    static void clear() {
        JOBS.clear();
    }

    /** Al arrancar: reanuda las obras que se quedaron a medias. */
    static void resumeAll(MinecraftServer server) {
        for (ServerWorld w : server.getWorlds()) {
            MansionState st = MansionState.get(w);
            for (MansionState.Record r : st.all()) {
                if (r.done || r.attempts >= MAX_ATTEMPTS) continue;
                r.attempts++;
                st.markDirty();
                try {
                    start(w, r, MansionDesign.generate(r.seed));
                    TcgShop.LOGGER.info("[mansión] se reanuda la obra de {} en {} (intento {})", r.ownerName, r.origin, r.attempts);
                } catch (Throwable t) {
                    TcgShop.LOGGER.error("[mansión] no se pudo reanudar la obra de {}", r.ownerName, t);
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
            TcgShop.LOGGER.error("[mansión] la obra de {} falló en la fase {}", job.rec.ownerName, job.phase, t);
            retry(job);
        }
    }

    /** Fallo grave: la obra vuelve a la cola (al final) sin esperar a reiniciar, hasta MAX_ATTEMPTS intentos. */
    private static void retry(Job job) {
        if (job.rec.attempts >= MAX_ATTEMPTS) {
            finish(job, false);
            return;
        }
        JOBS.remove(job);
        releaseTickets(job);
        job.rec.attempts++;
        MansionState.get(job.world).markDirty();
        JOBS.add(new Job(job.world, job.rec, job.design));
        TcgShop.LOGGER.warn("[mansión] la obra de {} vuelve a la cola (intento {})", job.rec.ownerName, job.rec.attempts);
        ServerPlayerEntity owner = job.world.getServer().getPlayerManager().getPlayer(job.rec.owner);
        if (owner != null) owner.sendMessage(Text.translatable("tcgshop.mansion.retrying", job.rec.attempts).formatted(Formatting.YELLOW), false);
    }

    /** Comando de op: vuelve a poner en la cola la obra de ese registro (repara lo que falte). false si ya está en marcha. */
    static boolean requeue(ServerWorld world, MansionState.Record rec) {
        if (running(rec)) return false;
        MansionDesign design = MansionDesign.generate(rec.seed);
        rec.done = false;
        rec.attempts = 1;
        MansionState.get(world).markDirty();
        start(world, rec, design);
        return true;
    }

    /** Comando de op: quita de la cola la obra de ese registro (si estaba en marcha). */
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

    /** Presupuesto de este tick: más pequeño cuanto más cargado va ya el servidor (media de los últimos ticks). */
    private static long budget(MinecraftServer server) {
        long avg;
        try {
            avg = server.getAverageNanosPerTick();
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
        st.markDirty();
        ServerPlayerEntity owner = job.world.getServer().getPlayerManager().getPlayer(job.rec.owner);
        if (owner == null) return;
        if (ok) {
            owner.sendMessage(Text.translatable("tcgshop.mansion.done").formatted(Formatting.GOLD, Formatting.BOLD), false);
            owner.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
        } else {
            owner.sendMessage(Text.translatable("tcgshop.mansion.failed").formatted(Formatting.RED), false);
        }
    }

    private static void ensureTickets(Job job) {
        if (job.ticketed) return;
        job.ticketed = true;
        Box b = job.plan.box();
        int cx0 = ((int) Math.floor(b.minX)) >> 4, cx1 = ((int) Math.floor(b.maxX - 1)) >> 4;
        int cz0 = ((int) Math.floor(b.minZ)) >> 4, cz1 = ((int) Math.floor(b.maxZ - 1)) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                job.world.getChunkManager().addTicket(TICKET, cp, 1, cp);
                job.chunks.add(cp);
            }
    }

    private static void releaseTickets(Job job) {
        for (ChunkPos cp : job.chunks) {
            try {
                job.world.getChunkManager().removeTicket(TICKET, cp, 1, cp);
            } catch (Throwable ignored) {}
        }
        job.chunks.clear();
    }

    /** Un tick de trabajo. true = obra terminada. */
    private static boolean step(Job job) {
        ensureTickets(job);
        int maxSets = LuxuryConfig.INSTANCE.mansionBlocksPerTick, maxChecks = maxSets * 12;
        long deadline = System.nanoTime() + budget(job.world.getServer());
        int sets = 0, checks = 0;
        ServerWorld w = job.world;
        final int layer = MansionDesign.W * MansionDesign.D;
        int iter = 0;
        while (sets < maxSets && checks < maxChecks) {
            if ((++iter & 63) == 0 && System.nanoTime() > deadline) break;
            try {
            switch (job.phase) {
                case 0 -> { // despejar, de arriba abajo, sólo por encima del suelo
                    int total = (MansionDesign.Y_MAX + 1) * layer;
                    if (job.cursor >= total) { next(job); continue; }
                    int c = job.cursor++;
                    int y = MansionDesign.Y_MAX - c / layer, rem = c % layer, z = rem / MansionDesign.W, x = rem % MansionDesign.W;
                    checks++;
                    if (job.design.cell(x, y, z) > MansionDesign.AIR) continue; // ahí va estructura: se sustituye luego
                    job.plan.world(x, y, z, job.pos);
                    if (!w.getBlockState(job.pos).isAir()) {
                        w.setBlockState(job.pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                        sets++;
                    }
                }
                case 1 -> { // cimientos: rellenar huecos bajo la capa de hierba
                    if (job.cursor >= layer) { next(job); continue; }
                    int c = job.cursor++;
                    int z = c / MansionDesign.W, x = c % MansionDesign.W;
                    boolean firm = false;
                    for (int y = -2; y >= MansionDesign.Y_MIN; y--) {
                        checks++;
                        if (job.design.cell(x, y, z) != MansionDesign.KEEP) continue; // lo pone la estructura
                        job.plan.world(x, y, z, job.pos);
                        int r = fillCell(w, job.pos, y >= -4 ? Blocks.DIRT : Blocks.STONE);
                        if (r < 0) { firm = true; break; }
                        sets += r;
                    }
                    // bordes: sobre un barranco, un acantilado o una cueva se sigue bajando hasta suelo firme para que
                    // la finca no quede colgando con los lados al aire
                    boolean edge = x == 0 || z == 0 || x == MansionDesign.W - 1 || z == MansionDesign.D - 1;
                    if (!firm && edge) {
                        for (int y = MansionDesign.Y_MIN - 1; y >= MansionDesign.Y_MIN - EDGE_FILL_EXTRA; y--) {
                            checks++;
                            job.plan.world(x, y, z, job.pos);
                            if (job.pos.getY() < w.getBottomY()) break;
                            int r = fillCell(w, job.pos, Blocks.STONE);
                            if (r < 0) break;
                            sets += r;
                        }
                    }
                }
                case 2 -> { // estructura, de abajo arriba
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
                        w.setBlockState(job.pos, target, Block.NOTIFY_LISTENERS);
                        sets++;
                    }
                }
                case 3 -> { // decoración
                    if (job.cursor >= job.decor.size()) { next(job); continue; }
                    MansionDesign.Deco d = job.decor.get(job.cursor++);
                    checks++;
                    BlockState target = rotate(job, d.state());
                    BlockPos p = job.plan.world(d.x(), d.y(), d.z());
                    job.pos.set(p); // para el log si falla
                    if (w.getBlockState(p) == target) continue;
                    w.setBlockState(p, target, Block.NOTIFY_LISTENERS);
                    sets++;
                    if (d.hook()) placedHook(job, p, target);
                }
                case 4 -> { // residentes (un fallo aquí no rompe la obra: la casa ya está hecha)
                    if (LuxuryConfig.INSTANCE.mansionPokemon && !job.rec.residentsSpawned) {
                        job.rec.residentsSpawned = true; // antes de intentarlo: nunca se repite en bucle
                        MansionState.get(w).markDirty();
                        try {
                            int n = MansionPokemon.spawn(w, job.rec, job.design, job.plan);
                            if (n > 0) TcgShop.LOGGER.info("[mansión] {} Pokémon residentes en la finca de {}", n, job.rec.ownerName);
                        } catch (Throwable t) {
                            TcgShop.LOGGER.error("[mansión] no se pudieron soltar los residentes de {}", job.rec.ownerName, t);
                        }
                    }
                    next(job);
                }
                default -> {
                    return true;
                }
            }
            } catch (Throwable t) {
                // un bloque concreto ha fallado: se apunta y se sigue (el cursor ya avanzó)
                job.errors++;
                if (job.errors <= 20)
                    TcgShop.LOGGER.warn("[mansión] fase {}: falló el bloque en {} ({}); se salta", job.phase, job.pos.toShortString(), t.toString());
                if (job.errors >= MAX_BLOCK_ERRORS) throw new IllegalStateException("demasiados bloques fallidos: " + job.errors, t);
            }
        }
        progress(job);
        return job.phase >= 5;
    }

    /**
     * Cimientos: rellena esa celda si está hueca. -1 = ya hay suelo firme (se para la columna), 0 = no se toca (bloque con
     * datos: un cofre o similar no se pisa nunca), 1 = rellenada.
     */
    private static int fillCell(ServerWorld w, BlockPos pos, Block fill) {
        BlockState cur = w.getBlockState(pos);
        if (cur.isSolidBlock(w, pos)) return -1;
        if (w.getBlockEntity(pos) != null) return 0;
        w.setBlockState(pos, fill.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        return 1;
    }

    private static void next(Job job) {
        job.phase++;
        job.cursor = 0;
    }

    /** Estatuas y expositores: piezas de encima (onPlaced) y dueño = el de la finca. */
    private static void placedHook(Job job, BlockPos p, BlockState state) {
        ServerPlayerEntity owner = job.world.getServer().getPlayerManager().getPlayer(job.rec.owner);
        try {
            state.getBlock().onPlaced(job.world, p, state, owner, new ItemStack(state.getBlock()));
        } catch (Throwable t) {
            TcgShop.LOGGER.warn("[mansión] onPlaced de {} falló: {}", state.getBlock(), t.toString());
        }
        if (state.getBlock() instanceof ShopDisplayBlock) {
            ShopDisplayBlockEntity be = ShopDisplayBlock.masterEntity(job.world, state, p);
            if (be != null) {
                be.owner = job.rec.owner;
                be.changed();
            }
        }
    }

    /** Estado girado a la orientación del mundo (con caché). */
    private static BlockState rotate(Job job, BlockState s) {
        return job.rotated.computeIfAbsent(s, k -> {
            BlockState r = k.rotate(job.plan.rotation);
            Direction f = facingOf(k);
            // muebles de otros mods que no implementen rotate(): se fuerza su "facing"
            if (f != null) r = MansionDesign.face(r, job.plan.dir(f));
            return r;
        });
    }

    @Nullable
    private static Direction facingOf(BlockState s) {
        for (Property<?> p : s.getProperties())
            if (p.getName().equals("facing") && s.get(p) instanceof Direction d && d.getAxis().isHorizontal()) return d;
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
        ServerPlayerEntity owner = job.world.getServer().getPlayerManager().getPlayer(job.rec.owner);
        if (owner != null) owner.sendMessage(Text.translatable("tcgshop.mansion.progress", pct).formatted(Formatting.GOLD), true);
    }

    /** ¿Hay una obra en marcha que pise esa caja? */
    static boolean overlapsJob(ServerWorld w, Box box) {
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (j.world == w && j.plan.box().intersects(box)) return true;
        }
        return false;
    }
}
