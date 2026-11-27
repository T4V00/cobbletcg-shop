package dev.tcgshop.luxury.house;

import dev.masterball.api.platform.event.ServerEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tcgshop.TcgShop;
import dev.tcgshop.luxury.LuxuryBlocks;
import dev.tcgshop.luxury.StatueBlock;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Logic of the MasterBall Mansion Key: particle preview, sneak-to-confirm, safety
 * checks, construction start ({@link MansionBuilder}) and test commands.
 * <p>
 * Rules: only in open-sky worlds (neither Nether nor End), standing on the ground, with height for the foundations and the
 * roof, inside the world border and outside spawn protection, without overlapping other players' shops (ShopZones) or another
 * mansion. Overlapping your own shop is allowed, with a warning (it would destroy it). The terrain is flattened (warning).
 */
public final class MansionService {
    /** Seconds to confirm after the preview. */
    public static final int CONFIRM_SECONDS = 10;
    private static final int MAX_JOBS = 4;
    /** Ticks between two previews by the same player (each one samples a lot of terrain). */
    private static final int PREVIEW_COOLDOWN = 50;

    private record Pending(MansionPlan plan, ResourceKey<Level> world, long expires) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> LAST_PREVIEW = new HashMap<>();
    private static final DustParticleOptions OUTLINE = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.25f), 1.6f);

    private MansionService() {}

    /** Server hooks (called from LuxuryBlocks.register). */
    public static void register() {
        ServerEvents.TICK_END.register(server -> {
            MansionBuilder.tick(server);
            tickPreviews(server);
            MansionPokemon.tick(server);
        });
        ServerEvents.STARTED.register(server -> {
            MansionPokemon.installHooks();
            MansionBuilder.resumeAll(server);
        });
        ServerEvents.STOPPING.register(server -> {
            MansionBuilder.clear();
            PENDING.clear();
            LAST_PREVIEW.clear();
        });
        ServerEvents.ENTITY_LOAD.register((entity, world) -> MansionPokemon.onLoad(entity));
    }

    // ------------------------------------------------------------------ key

    /** Right-click with the key (server). */
    public static void onKeyUse(ServerPlayer player, InteractionHand hand) {
        ServerLevel world = player.serverLevel();
        long now = world.getServer().getTickCount();
        Pending p = PENDING.get(player.getUUID());
        if (player.isShiftKeyDown() && p != null) {
            PENDING.remove(player.getUUID());
            if (now > p.expires || !p.world.equals(world.dimension())) {
                player.displayClientMessage(Component.translatable("tcgshop.mansion.err_expired").withStyle(ChatFormatting.RED), false);
                return;
            }
            confirm(player, hand, p.plan);
            return;
        }
        preview(player);
    }

    private static void preview(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        if (!player.onGround()) {
            player.displayClientMessage(Component.translatable("tcgshop.mansion.err_ground").withStyle(ChatFormatting.RED), true);
            return;
        }
        long now = world.getServer().getTickCount();
        Long last = LAST_PREVIEW.get(player.getUUID());
        if (last != null && now >= last && now - last < PREVIEW_COOLDOWN) {
            player.displayClientMessage(Component.translatable("tcgshop.mansion.err_cooldown").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        LAST_PREVIEW.put(player.getUUID(), now);
        MansionPlan plan = MansionPlan.inFrontOf(player.blockPosition(), player.getDirection());
        List<Component> warnings = new ArrayList<>();
        Component err = check(player, world, plan, warnings, false, false);
        if (err != null) {
            player.displayClientMessage(err.copy().withStyle(ChatFormatting.RED), false);
            PENDING.remove(player.getUUID());
            return;
        }
        PENDING.put(player.getUUID(), new Pending(plan, world.dimension(), world.getServer().getTickCount() + CONFIRM_SECONDS * 20L));
        player.displayClientMessage(Component.translatable("tcgshop.mansion.preview").withStyle(ChatFormatting.GOLD), false);
        for (Component w : warnings) player.displayClientMessage(warning(w), false);
        player.displayClientMessage(Component.translatable("tcgshop.mansion.confirm_hint", CONFIRM_SECONDS).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        showOutline(player, plan);
    }

    private static void confirm(ServerPlayer player, InteractionHand hand, MansionPlan plan) {
        ItemStack st = player.getItemInHand(hand);
        if (!st.is(LuxuryBlocks.MANSION_KEY)) return;
        ServerLevel world = player.serverLevel();
        Component err = check(player, world, plan, new ArrayList<>(), false, true);
        if (err != null) {
            player.displayClientMessage(err.copy().withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!start(world, player, plan)) return;
        if (!player.isCreative()) st.shrink(1); // the key is only consumed if construction has started
        player.displayClientMessage(Component.translatable("tcgshop.mansion.started").withStyle(ChatFormatting.GOLD), false);
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
        if (player.getServer() != null)
            player.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("tcgshop.mansion.announce", player.getDisplayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /** Registers the mansion and queues the construction job. false (with a message) if not possible. */
    static boolean start(ServerLevel world, ServerPlayer owner, MansionPlan plan) {
        if (MansionBuilder.busy(owner.getUUID())) {
            owner.displayClientMessage(Component.translatable("tcgshop.mansion.err_busy").withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (MansionBuilder.queued() >= MAX_JOBS) {
            owner.displayClientMessage(Component.translatable("tcgshop.mansion.err_queue").withStyle(ChatFormatting.RED), false);
            return false;
        }
        MansionState.Record r = new MansionState.Record(UUID.randomUUID(), owner.getUUID(), owner.getName().getString(),
                plan.origin, plan.facing, world.getRandom().nextLong());
        r.createdDay = world.getDayTime() / 24000L;
        r.attempts = 1;
        MansionDesign design;
        try {
            design = MansionDesign.generate(r.seed);
        } catch (Throwable t) {
            TcgShop.LOGGER.error("[mansion] design failed", t);
            owner.displayClientMessage(Component.translatable("tcgshop.mansion.failed").withStyle(ChatFormatting.RED), false);
            return false;
        }
        MansionState.get(world).add(r);
        MansionBuilder.start(world, r, design);
        TcgShop.LOGGER.info("[mansion] {} starts a mansion at {} facing {} ({} blocks)", r.ownerName, plan.origin,
                plan.facing.getSerializedName(), design.blockCount());
        return true;
    }

    /**
     * Safety checks. null = allowed; otherwise, the reason. warnings receives the warnings (terrain, water, own
     * shop). force = skip the shop and mansion checks (test command only).
     */
    @Nullable
    static Component check(ServerPlayer player, ServerLevel world, MansionPlan plan, List<Component> warnings, boolean force, boolean loadChunks) {
        if (world.dimensionType().hasCeiling() || !world.dimensionType().hasSkyLight() || world.dimension() == Level.END)
            return Component.translatable("tcgshop.mansion.err_dimension");
        AABB box = plan.box();
        if (box.minY < world.getMinBuildHeight() || box.maxY > world.getMaxBuildHeight())
            return Component.translatable("tcgshop.mansion.err_height", MansionDesign.Y_MAX + 1, -MansionDesign.Y_MIN);
        // world border and protections (from other mods too): probe grid every 14 blocks at most
        WorldBorder border = world.getWorldBorder();
        for (int lz : PROBE)
            for (int lx : PROBE) {
                BlockPos p = plan.world(lx, 0, lz);
                if (!border.isWithinBounds(p) || !world.mayInteract(player, p)) return Component.translatable("tcgshop.mansion.err_border");
            }
        // spawn protection: the whole square against the box (it fits between two probes if the radius is small)
        MinecraftServer server = world.getServer();
        int radius = server.getSpawnProtectionRadius();
        BlockPos spawn = world.getSharedSpawnPos();
        if (radius > 0 && server.isUnderSpawnProtection(world, spawn, player)
                && spawn.getX() - radius < box.maxX && spawn.getX() + radius >= box.minX
                && spawn.getZ() - radius < box.maxZ && spawn.getZ() + radius >= box.minZ)
            return Component.translatable("tcgshop.mansion.err_border");
        List<AABB> ownZones = new ArrayList<>();
        if (!force) {
            for (ShopZones.Zone z : ShopZones.get(world).all()) {
                if (!z.box().intersects(box)) continue;
                if (z.owner.equals(player.getUUID())) {
                    ownZones.add(z.box());
                    warnings.add(Component.translatable("tcgshop.mansion.warn_own_shop").withStyle(ChatFormatting.RED));
                } else return Component.translatable("tcgshop.mansion.err_shop", z.ownerName);
            }
            MansionState.Record other = MansionState.get(world).overlapping(box);
            if (other != null) return Component.translatable("tcgshop.mansion.err_mansion", other.ownerName);
            if (MansionBuilder.overlapsJob(world, box)) return Component.translatable("tcgshop.mansion.err_mansion", "?");
        }
        // what construction would erase without a trace: displays, non-empty chests and luxury statues
        int x0 = (int) box.minX, x1 = (int) box.maxX - 1, y0 = (int) box.minY, y1 = (int) box.maxY - 1, z0 = (int) box.minZ, z1 = (int) box.maxZ - 1;
        int unloaded = 0, ownDisplays = 0;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                // the preview does not load chunks (no lag spikes); confirming does, once
                LevelChunk ch = loadChunks ? world.getChunk(cx, cz) : world.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) {
                    unloaded++;
                    continue;
                }
                if (force) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    BlockPos bp = be.getBlockPos();
                    if (!inside(bp, x0, y0, z0, x1, y1, z1)) continue;
                    if (be instanceof ShopDisplayBlockEntity d) {
                        boolean mine = d.owner != null ? d.owner.equals(player.getUUID()) : (d.isEmpty() || inAny(ownZones, bp));
                        if (!mine) return Component.translatable("tcgshop.mansion.err_display", bp.toShortString());
                        if (!d.isEmpty()) ownDisplays++;
                        continue;
                    }
                    if (be instanceof RandomizableContainerBlockEntity l && l.getLootTable() != null) continue; // unopened natural chest
                    if (be instanceof Container inv && !inv.isEmpty())
                        return Component.translatable("tcgshop.mansion.err_container", bp.toShortString());
                }
                BlockPos statue = findStatue(ch, x0, y0, z0, x1, y1, z1);
                if (statue != null) return Component.translatable("tcgshop.mansion.err_statue", statue.toShortString());
            }
        if (unloaded > 0) warnings.add(Component.translatable("tcgshop.mansion.warn_unloaded"));
        if (ownDisplays > 0) warnings.add(Component.translatable("tcgshop.mansion.warn_own_displays", ownDisplays).withStyle(ChatFormatting.RED));
        // samples 1 in every 9 columns (loaded chunks only): terrain to flatten, water, overhangs above the
        // roof (anything past Y_MAX is not cleared) and gaps below the foundations
        int solid = 0, water = 0, overhang = 0, hollow = 0;
        int roof = plan.origin.getY() + MansionDesign.Y_MAX;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int z = 1; z < MansionDesign.D; z += 3)
            for (int x = 1; x < MansionDesign.W; x += 3) {
                plan.world(x, 0, z, m);
                if (!world.getChunkSource().hasChunk(m.getX() >> 4, m.getZ() >> 4)) continue;
                if (world.getHeight(Heightmap.Types.WORLD_SURFACE, m.getX(), m.getZ()) - 1 > roof) overhang++;
                for (int y = 0; y <= 30; y++) {
                    plan.world(x, y, z, m);
                    var s = world.getBlockState(m);
                    if (s.isAir()) continue;
                    if (!s.getFluidState().isEmpty()) water++;
                    else solid++;
                }
                boolean firm = false;
                for (int y = -1; y >= MansionDesign.Y_MIN && !firm; y--) {
                    plan.world(x, y, z, m);
                    firm = world.getBlockState(m).isRedstoneConductor(world, m);
                }
                if (!firm) hollow++;
            }
        if (solid > 0) warnings.add(Component.translatable("tcgshop.mansion.warn_terrain", solid * 9));
        if (water > 0) warnings.add(Component.translatable("tcgshop.mansion.warn_water"));
        if (overhang > 0) warnings.add(Component.translatable("tcgshop.mansion.warn_overhang", MansionDesign.Y_MAX).withStyle(ChatFormatting.RED));
        if (hollow > 8) warnings.add(Component.translatable("tcgshop.mansion.warn_hollow"));
        return null;
    }

    /** Local probes every 14 blocks at most (smaller than any default spawn protection). */
    private static final int[] PROBE = {0, 14, 28, 42, 56, 70, 84, 98, MansionDesign.W - 1};

    private static boolean inside(BlockPos p, int x0, int y0, int z0, int x1, int y1, int z1) {
        return p.getX() >= x0 && p.getX() <= x1 && p.getY() >= y0 && p.getY() <= y1 && p.getZ() >= z0 && p.getZ() <= z1;
    }

    private static boolean inAny(List<AABB> boxes, BlockPos p) {
        for (AABB b : boxes) if (b.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) return true;
        return false;
    }

    /** First luxury statue of that chunk inside the box (checks each section's palette before scanning it). */
    @Nullable
    private static BlockPos findStatue(LevelChunk ch, int x0, int y0, int z0, int x1, int y1, int z1) {
        LevelChunkSection[] secs = ch.getSections();
        int bx = ch.getPos().getMinBlockX(), bz = ch.getPos().getMinBlockZ();
        for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
            int idx = ch.getSectionIndexFromSectionY(sy);
            if (idx < 0 || idx >= secs.length) continue;
            LevelChunkSection sec = secs[idx];
            if (sec == null || sec.hasOnlyAir() || !sec.getStates().maybeHas(s -> s.getBlock() instanceof StatueBlock)) continue;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        if (!(sec.getBlockState(x, y, z).getBlock() instanceof StatueBlock)) continue;
                        BlockPos p = new BlockPos(bx + x, (sy << 4) + y, bz + z);
                        if (inside(p, x0, y0, z0, x1, y1, z1)) return p;
                    }
        }
        return null;
    }

    // ------------------------------------------------------------------ preview

    private static void tickPreviews(MinecraftServer server) {
        if (PENDING.isEmpty() || server.getTickCount() % 10 != 0) return;
        long now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> e = it.next();
            Pending p = e.getValue();
            ServerPlayer pl = server.getPlayerList().getPlayer(e.getKey());
            if (pl == null || now > p.expires || !pl.serverLevel().dimension().equals(p.world)) {
                if (pl != null && now > p.expires) pl.displayClientMessage(Component.translatable("tcgshop.mansion.err_expired").withStyle(ChatFormatting.GRAY), true);
                it.remove();
                continue;
            }
            showOutline(pl, p.plan);
        }
    }

    /** Outline of the estate (every 2 blocks) and the gate marked in green, only for that player. */
    private static void showOutline(ServerPlayer pl, MansionPlan plan) {
        ServerLevel w = pl.serverLevel();
        int W = MansionDesign.W, D = MansionDesign.D;
        for (int i = 0; i < W; i += 2) {
            dot(pl, w, plan.world(i, 1, 0), false);
            dot(pl, w, plan.world(i, 1, D - 1), false);
        }
        for (int i = 0; i < D; i += 2) {
            dot(pl, w, plan.world(0, 1, i), false);
            dot(pl, w, plan.world(W - 1, 1, i), false);
        }
        for (int x = 45; x <= 54; x++) dot(pl, w, plan.world(x, 1, 0), true);
    }

    private static void dot(ServerPlayer pl, ServerLevel w, BlockPos p, boolean gate) {
        double x = p.getX() + 0.5, y = p.getY() + 0.2, z = p.getZ() + 0.5;
        if (gate) w.sendParticles(pl, ParticleTypes.HAPPY_VILLAGER, true, x, y, z, 1, 0.1, 0.3, 0.1, 0);
        else w.sendParticles(pl, OUTLINE, true, x, y, z, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ tests

    /** Warning in yellow, except those that already carry their color (severe ones are red). */
    private static Component warning(Component w) {
        return w.getStyle().getColor() != null ? w : w.copy().withStyle(ChatFormatting.YELLOW);
    }

    /**
     * /tcgshop debug mansion [force|dry]: builds in front of the player without a key (op).
     * /tcgshop debug mansion list | resume &lt;owner&gt; | remove &lt;owner&gt;: list, put back under construction (repairs whatever
     * is missing) or delete a mansion's record (the blocks stay).
     */
    public static LiteralArgumentBuilder<CommandSourceStack> debugNode() {
        return Commands.literal("mansion")
                .executes(ctx -> debugBuild(ctx.getSource(), false))
                .then(Commands.literal("force").executes(ctx -> debugBuild(ctx.getSource(), true)))
                .then(Commands.literal("list").executes(ctx -> debugList(ctx.getSource())))
                .then(Commands.literal("resume").then(Commands.argument("owner", StringArgumentType.word())
                        .executes(ctx -> debugResume(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
                .then(Commands.literal("remove").then(Commands.argument("owner", StringArgumentType.word())
                        .executes(ctx -> debugRemove(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
                .then(Commands.literal("dry").executes(ctx -> {
                    int n = selfTest();
                    ctx.getSource().sendSuccess(() -> Component.literal("[mansion] design OK: " + n + " blocks"), false);
                    return 1;
                }));
    }

    private static int debugBuild(CommandSourceStack src, boolean force) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel world = player.serverLevel();
        MansionPlan plan = MansionPlan.inFrontOf(player.blockPosition(), player.getDirection());
        List<Component> warnings = new ArrayList<>();
        Component err = check(player, world, plan, warnings, force, true);
        if (err != null) {
            src.sendFailure(err);
            return 0;
        }
        for (Component w : warnings) player.displayClientMessage(warning(w), false);
        if (!start(world, player, plan)) return 0;
        src.sendSuccess(() -> Component.translatable("tcgshop.mansion.started").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private record Found(ServerLevel world, MansionState.Record rec) {}

    /** That owner's mansion (name or UUID): the latest unfinished one first, otherwise the latest. */
    @Nullable
    private static Found find(MinecraftServer server, String who) {
        Found unfinished = null, any = null;
        for (ServerLevel w : server.getAllLevels())
            for (MansionState.Record r : MansionState.get(w).all()) {
                if (!who.equalsIgnoreCase(r.ownerName) && !who.equalsIgnoreCase(r.owner.toString())) continue;
                any = new Found(w, r);
                if (!r.done) unfinished = any;
            }
        return unfinished != null ? unfinished : any;
    }

    private static int debugList(CommandSourceStack src) {
        int n = 0;
        for (ServerLevel w : src.getServer().getAllLevels())
            for (MansionState.Record r : MansionState.get(w).all()) {
                n++;
                String line = "[mansion] " + r.ownerName + " in " + w.dimension().location() + " " + r.origin.toShortString()
                        + " (" + r.facing.getSerializedName() + ") " + (MansionBuilder.running(r) ? "BUILDING" : r.done ? "finished" : "UNFINISHED")
                        + ", attempts " + r.attempts;
                src.sendSuccess(() -> Component.literal(line), false);
            }
        if (n == 0) src.sendSuccess(() -> Component.literal("[mansion] no mansions registered"), false);
        return n;
    }

    private static int debugResume(CommandSourceStack src, String who) {
        Found f = find(src.getServer(), who);
        if (f == null) {
            src.sendFailure(Component.literal("[mansion] no mansion found for " + who));
            return 0;
        }
        if (!MansionBuilder.requeue(f.world, f.rec)) {
            src.sendFailure(Component.literal("[mansion] " + f.rec.ownerName + "'s build is already running"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("[mansion] " + f.rec.ownerName + "'s build at " + f.rec.origin.toShortString() + " is back in the queue"), true);
        return 1;
    }

    private static int debugRemove(CommandSourceStack src, String who) {
        Found f = find(src.getServer(), who);
        if (f == null) {
            src.sendFailure(Component.literal("[mansion] no mansion found for " + who));
            return 0;
        }
        MansionBuilder.cancel(f.rec);
        MansionState.get(f.world).remove(f.rec);
        src.sendSuccess(() -> Component.literal("[mansion] record of " + f.rec.ownerName + "'s mansion at " + f.rec.origin.toShortString()
                + " deleted (the blocks and residents stay)"), true);
        return 1;
    }

    /** Dry run of the generator (selftest): generates the estate and checks that it makes sense. Throws on failure. */
    public static int selfTest() {
        MansionDesign d = MansionDesign.generate(12345L);
        int n = d.blockCount();
        int deco = d.decor().size();
        TcgShop.LOGGER.info("[selftest] mansion: {} blocks ({} decoration, {} residents; furnies={}, cobblemon={}, plushies={})",
                n, deco, d.residents.size(), d.furnies, d.cobblemon, d.plush);
        if (n < 25_000) throw new IllegalStateException("the mansion has too few blocks: " + n);
        if (deco < 500) throw new IllegalStateException("the mansion has too little decoration: " + deco);
        // orientation: local south points where the player is facing
        MansionPlan p = MansionPlan.inFrontOf(BlockPos.ZERO, net.minecraft.core.Direction.EAST);
        if (p.world(50, 0, 99).getX() <= p.world(50, 0, 0).getX()) throw new IllegalStateException("wrong estate orientation");
        return n;
    }

    /** For future use: does this player have a pending preview? */
    public static boolean hasPending(Player p) {
        return PENDING.containsKey(p.getUUID());
    }
}
