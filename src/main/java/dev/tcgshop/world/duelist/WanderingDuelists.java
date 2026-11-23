package dev.tcgshop.world.duelist;

import dev.tcgshop.EconomyConfig;
import dev.tcgshop.TcgShop;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.compat.CobbleFurniesCompat;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.ShopSeatEntity;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.WanderingDuelistEntity;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.phone.PhoneMessages;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.ptcg.PtcgDuels;
import dev.tcgshop.shop.DecorBlocks;
import dev.tcgshop.shop.ShopSeats;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.DirtPathBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 · Wandering Duelist (ROADMAP "Wandering Duelist").
 * <p>
 * <b>Spawning — a wandering encounter that builds itself, not a worldgen structure.</b> It is more reliable: it only
 * appears near players who are actually there to meet him, it is easy to tune ({@code EconomyConfig.duelist*}), it
 * never piles up in unexplored chunks, and since the mod placed the blocks itself it can take exactly those blocks away
 * again when he leaves. During the day, every 30 s, each player on the Overworld surface who is outside every shop has
 * {@code duelistChance} of the duelist setting up 16–32 blocks away (per-player cooldown, never two near each other).
 * <p>
 * <b>No griefing.</b> The site must be natural flat ground in a plains/forest/savanna-like biome: a 5×3 area at one
 * height, on dirt/grass/sand/snow, open to the sky, with only air or plants above; nothing that looks built (block
 * entities except bee nests, planks, stairs, slabs, glass, fences, doors, torches, farmland, paths, wool, furniture...)
 * within 7 blocks; and not within 16 blocks of any shop zone. Only air and plants are replaced.
 * <p>
 * <b>The encounter.</b> A play table between two chairs (a CobbleFurnies chair when that mod is loaded, otherwise
 * spruce stairs as a bench) and the duelist sitting on one of them (WanderingDuelistEntity). His blocks are protected
 * while he is there. The stakes and the result are settled here. On accept he holds the stake in escrow: all the cash
 * you carry up to {@code duelistMaxTake} (you need at least {@code duelistMinStake}, or no cash at all), persisted in
 * {@link DuelistDebts} with the encounter. Win = the stake back + {@code duelistPrize}; lose = he keeps the stake, or,
 * if you carried no cash at all, one random stack from your main inventory (never binders, the phone or container
 * items such as shulker boxes); a draw, a match that never starts, the encounter going away or a dedicated server
 * stopping mid-match = the stake back. Then he vanishes with his table and chairs and you get a phone message. A result
 * or refund while offline (disconnecting mid-match is a loss) is settled when you join again ({@link DuelistDebts}).
 */
public final class WanderingDuelists {
    /** NPC lines (tcgshop.duelist.line.<cat>.<n>, tools/gen_lang_10c_duelist.py): counts must match. */
    public static final Map<String, Integer> LINES = Map.ofEntries(
            Map.entry("arrive", 4), Map.entry("greet", 6), Map.entry("ambient", 8), Map.entry("offer", 5), Map.entry("accept", 5),
            Map.entry("decline", 5), Map.entry("busy", 4), Map.entry("deck_fail", 4), Map.entry("win", 6), Map.entry("lose_cash", 5),
            Map.entry("lose_item", 5), Map.entry("lose_nothing", 4), Map.entry("runaway", 3), Map.entry("draw", 4), Map.entry("bored", 4));
    public static final String SENDER = "tcgshop.msg.sender.duelist";
    /** Command tag of the duelist's own (saved) seat armor stand. */
    public static final String SEAT_TAG = "tcgshop_duelist_seat";
    /** Checks (world ticks) for a new encounter. */
    private static final int CHECK_EVERY = 600;

    private record Key(RegistryKey<World> dim, long pos) {}

    /** Blocks that belong to a duelist who is here (loaded): they cannot be broken. */
    private static final Map<Key, UUID> PROTECTED = new HashMap<>();
    /** Last encounter per player (server ticks). */
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    @Nullable private static Block furniesChair;
    private static boolean furniesChecked;

    private WanderingDuelists() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(WanderingDuelists::worldTick);
        // chunk unloads do not call Entity.remove (they only mark the entity removed), so PROTECTED may hold stale
        // entries: only a block whose duelist is actually here is protected, and everything is dropped when the server stops
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PROTECTED.clear();
            LAST.clear();
        });
        // no match survives a restart: a stake still held in escrow (crash, quit before the match started, encounter
        // in a chunk that never loads again) is owed back, paid when the player joins
        ServerLifecycleEvents.SERVER_STARTED.register(server -> DuelistDebts.get(server).releaseAll());
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld sw) || owner(sw, pos) == null) return true;
            if (player.isCreative() && player.hasPermissionLevel(2)) return true; // operators may clean up (he then leaves)
            player.sendMessage(Text.translatable("tcgshop.duelist.protected").formatted(Formatting.RED), true);
            return false;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DuelistDebts.settle(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((d, access, env) -> d.register(CommandManager.literal("tcgduelist")
                .then(CommandManager.literal("accept").executes(ctx -> { answer(ctx.getSource().getPlayerOrThrow(), "accept"); return 1; }))
                .then(CommandManager.literal("decline").executes(ctx -> { answer(ctx.getSource().getPlayerOrThrow(), "decline"); return 1; }))
                .then(CommandManager.literal("spawn").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    WanderingDuelistEntity e = trySpawn(p.getServerWorld(), p, true);
                    if (e == null) {
                        ctx.getSource().sendError(Text.translatable("tcgshop.duelist.cmd.failed"));
                        return 0;
                    }
                    BlockPos t = e.table();
                    ctx.getSource().sendFeedback(() -> Text.translatable("tcgshop.duelist.cmd.spawned",
                            t == null ? "?" : t.getX() + " " + t.getY() + " " + t.getZ()), false);
                    return 1;
                }))));
    }

    // ------------------------------------------------------------------ configuration (EconomyConfig, clamped here)

    public static long prize() { return Math.max(0, EconomyConfig.INSTANCE.duelistPrize); }

    public static long maxTake() { return Math.max(0, EconomyConfig.INSTANCE.duelistMaxTake); }

    /** Smallest stake he accepts from someone who carries cash (never above the cap). */
    public static long minStake() { return Math.max(0, Math.min(EconomyConfig.INSTANCE.duelistMinStake, maxTake())); }

    private static double chance() {
        double c = EconomyConfig.INSTANCE.duelistChance;
        return Double.isNaN(c) ? 0 : Math.max(0, Math.min(1, c));
    }

    private static int cooldownTicks() { return Math.max(0, Math.min(7 * 24 * 3600, EconomyConfig.INSTANCE.duelistCooldownSeconds)) * 20; }

    public static Text line(Random r, String cat) {
        return Text.translatable("tcgshop.duelist.line." + cat + "." + r.nextInt(LINES.getOrDefault(cat, 1)));
    }

    // ------------------------------------------------------------------ geometry helpers (shared with the entity)

    public static float yawToward(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    /** The spot behind a chair (away from the table) where its sitter gets up. */
    public static Vec3d standSpot(BlockPos table, BlockPos chair) {
        BlockPos q = chair.add(chair.getX() - table.getX(), 0, chair.getZ() - table.getZ());
        return new Vec3d(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ protection

    public static void protect(ServerWorld w, WanderingDuelistEntity e) {
        for (BlockPos p : new BlockPos[]{e.table(), e.chairHis(), e.chairTheirs()})
            if (p != null) PROTECTED.put(new Key(w.getRegistryKey(), p.asLong()), e.getUuid());
    }

    public static void unprotect(ServerWorld w, WanderingDuelistEntity e) {
        PROTECTED.values().removeIf(u -> u.equals(e.getUuid()));
    }

    @Nullable
    private static WanderingDuelistEntity owner(ServerWorld w, BlockPos pos) {
        UUID u = PROTECTED.get(new Key(w.getRegistryKey(), pos.asLong()));
        return u != null && w.getEntity(u) instanceof WanderingDuelistEntity d && d.isAlive() ? d : null;
    }

    /** Called first by PtcgDuels.useTable: a click on a duelist's table talks to him. true = handled here. */
    public static boolean interceptTable(ServerPlayerEntity p, BlockPos table) {
        WanderingDuelistEntity d = owner(p.getServerWorld(), table);
        if (d == null || !table.equals(d.table())) return false;
        d.clicked(p);
        return true;
    }

    /** /tcgduelist accept|decline (chat fallback): the duelist whose stakes this player has open. */
    private static void answer(ServerPlayerEntity p, String action) {
        for (WanderingDuelistEntity d : p.getServerWorld().getEntitiesByClass(WanderingDuelistEntity.class, p.getBoundingBox().expand(16), e -> e.offeredTo(p.getUuid()))) {
            d.answer(p, action);
            return;
        }
        p.sendMessage(Text.translatable("tcgshop.duelist.none").formatted(Formatting.GRAY), true);
    }

    // ------------------------------------------------------------------ spawning

    private static void worldTick(ServerWorld w) {
        if (w.getRegistryKey() != World.OVERWORLD || w.getTime() % CHECK_EVERY != 217) return;
        double chance = chance();
        if (chance <= 0 || !w.isDay() || !CobbleDollarsBridge.isAvailable()) return;
        List<ServerPlayerEntity> players = new ArrayList<>(w.getPlayers(p -> !p.isSpectator() && !p.isCreative()));
        if (players.isEmpty()) return;
        java.util.Collections.shuffle(players, new java.util.Random(w.getTime()));
        int now = w.getServer().getTicks();
        for (ServerPlayerEntity p : players) {
            Integer last = LAST.get(p.getUuid());
            if (last != null && now - last < cooldownTicks() && now >= last) continue;
            if (w.random.nextDouble() >= chance) continue;
            if (!w.isSkyVisible(p.getBlockPos()) || PtcgDuels.busy(p.getUuid()) || nearShop(w, p.getBlockPos(), 24)) continue;
            if (trySpawn(w, p, false) != null) return; // at most one new duelist per check
        }
    }

    /** Builds the encounter 16–32 blocks from the player. force = command (ignores the cooldown, not the site rules). */
    @Nullable
    public static WanderingDuelistEntity trySpawn(ServerWorld w, ServerPlayerEntity p, boolean force) {
        if (w.getRegistryKey() != World.OVERWORLD) return null;
        if (!w.getEntitiesByClass(WanderingDuelistEntity.class, p.getBoundingBox().expand(force ? 48 : 192), e -> true).isEmpty()) return null;
        Random r = w.random;
        for (int attempt = 0; attempt < 24; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int dist = (force ? 6 : 16) + r.nextInt(force ? 10 : 17);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            Direction axis = r.nextBoolean() ? Direction.EAST : Direction.SOUTH;
            BlockPos t = site(w, x, z, axis, p.getY());
            if (t == null) continue;
            WanderingDuelistEntity e = build(w, t, axis);
            if (e == null) continue;
            LAST.put(p.getUuid(), w.getServer().getTicks());
            p.sendMessage(Text.translatable("tcgshop.duelist.arrived").formatted(Formatting.GOLD, Formatting.ITALIC), false);
            p.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.NEUTRAL, 0.8f, 0.7f);
            return e;
        }
        return null;
    }

    private static boolean nearShop(ServerWorld w, BlockPos pos, int margin) {
        Box b = new Box(pos).expand(8);
        for (ShopZones.Zone z : ShopZones.get(w).all()) if (z.box().expand(margin).intersects(b)) return true;
        return false;
    }

    /**
     * Checks a site whose table goes at column (x, z), chairs one block either side along axis. Returns the table
     * position or null when the ground is not natural, flat, open and free of anything built.
     */
    @Nullable
    private static BlockPos site(ServerWorld w, int x, int z, Direction axis, double nearY) {
        Direction side = axis.rotateYClockwise();
        // every chunk the checks touch must already be loaded (never load or generate terrain from here)
        for (int dx = -8; dx <= 8; dx += 16) for (int dz = -8; dz <= 8; dz += 16)
            if (!w.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return null;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos t = new BlockPos(x, y, z);
        if (Math.abs(y - nearY) > 10 || !w.shouldTickEntity(t) || !biomeOk(w.getBiome(t))) return null;
        if (nearShop(w, t, 16)) return null;
        // 5×3 footprint: chairs and stand spots along the axis, one block of margin on each side
        for (int i = -2; i <= 2; i++)
            for (int j = -1; j <= 1; j++) {
                BlockPos c = t.offset(axis, i).offset(side, j);
                if (w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()) != y) return null;
                BlockState ground = w.getBlockState(c.down());
                if (!naturalGround(ground) || !ground.isSolidBlock(w, c.down())) return null;
                if (!clearable(w.getBlockState(c)) || !clearable(w.getBlockState(c.up()))) return null;
                if (Math.abs(i) <= 1 && j == 0 && !w.isSkyVisible(c)) return null; // table and chairs in the open
            }
        // nothing that looks built nearby
        for (BlockPos q : BlockPos.iterate(t.add(-7, -3, -7), t.add(7, 6, 7)))
            if (artificial(w, q, w.getBlockState(q))) return null;
        return t;
    }

    private static boolean biomeOk(RegistryEntry<Biome> b) {
        if (b.isIn(BiomeTags.IS_OCEAN) || b.isIn(BiomeTags.IS_RIVER) || b.isIn(BiomeTags.IS_BEACH)) return false;
        if (b.isIn(BiomeTags.IS_FOREST) || b.isIn(BiomeTags.IS_TAIGA) || b.isIn(BiomeTags.IS_SAVANNA)) return true;
        if (b.matchesKey(BiomeKeys.PLAINS) || b.matchesKey(BiomeKeys.SUNFLOWER_PLAINS) || b.matchesKey(BiomeKeys.MEADOW)
                || b.matchesKey(BiomeKeys.CHERRY_GROVE) || b.matchesKey(BiomeKeys.SNOWY_PLAINS)) return true;
        // modded biomes (Terralith & co.) by name
        String id = b.getKey().map(k -> k.getValue().getPath()).orElse("").toLowerCase(Locale.ROOT);
        for (String w : new String[]{"plains", "forest", "savanna", "meadow", "grove", "field", "prairie", "woods", "taiga", "steppe", "shrubland"})
            if (id.contains(w)) return true;
        return false;
    }

    private static boolean naturalGround(BlockState s) {
        return s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.SAND) || s.isOf(Blocks.SNOW_BLOCK) || s.isOf(Blocks.GRAVEL);
    }

    /** Air or a plant (grass, flowers, snow layer...) that may be replaced; never fluids. */
    private static boolean clearable(BlockState s) {
        return s.isAir() || (s.isReplaceable() && s.getFluidState().isEmpty() && !s.hasBlockEntity());
    }

    /** Does that block look placed by a player? (then the site is rejected: the duelist never builds next to builds) */
    private static boolean artificial(ServerWorld w, BlockPos p, BlockState s) {
        if (s.isAir()) return false;
        if (s.hasBlockEntity() && !s.isIn(BlockTags.BEEHIVES)) return true;
        if (s.isIn(BlockTags.PLANKS) || s.isIn(BlockTags.STAIRS) || s.isIn(BlockTags.SLABS) || s.isIn(BlockTags.WALLS)
                || s.isIn(BlockTags.FENCES) || s.isIn(BlockTags.FENCE_GATES) || s.isIn(BlockTags.DOORS) || s.isIn(BlockTags.TRAPDOORS)
                || s.isIn(BlockTags.BEDS) || s.isIn(BlockTags.WOOL) || s.isIn(BlockTags.WOOL_CARPETS) || s.isIn(BlockTags.RAILS)
                || s.isIn(BlockTags.CANDLES) || s.isIn(BlockTags.BUTTONS) || s.isIn(BlockTags.PRESSURE_PLATES) || s.isIn(BlockTags.IMPERMEABLE)
                || s.isIn(BlockTags.CROPS) || s.isIn(BlockTags.ALL_SIGNS) || s.isIn(BlockTags.FLOWER_POTS)) return true;
        Block b = s.getBlock();
        if (b instanceof PaneBlock || b instanceof TorchBlock || b instanceof LanternBlock || b instanceof FarmlandBlock
                || b instanceof DirtPathBlock || b instanceof LadderBlock || b instanceof CarpetBlock) return true;
        if (s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.STONE_BRICKS) || s.isOf(Blocks.BRICKS) || s.isOf(Blocks.SMOOTH_STONE)
                || s.isOf(Blocks.CRAFTING_TABLE) || s.isOf(Blocks.HAY_BLOCK) || s.isOf(Blocks.SCAFFOLDING)) return true;
        Identifier id = Registries.BLOCK.getId(b);
        if (id.getPath().contains("concrete") || id.getPath().contains("polished")) return true;
        return !"minecraft".equals(id.getNamespace()) && (TcgShop.MOD_ID.equals(id.getNamespace()) || ShopSeats.isFurnitureSeat(s)
                || CobbleFurniesCompat.isFurnitureBlock(id) || CobbleFurniesCompat.isPlushBlock(id));
    }

    // ------------------------------------------------------------------ building and removing

    /** A CobbleFurnies chair (registry id with "chair" and a horizontal facing), if that mod is loaded. */
    @Nullable
    private static Block furniesChair() {
        if (furniesChecked) return furniesChair;
        furniesChecked = true;
        if (!CobbleFurniesCompat.furniesLoaded()) return null;
        Identifier best = null;
        for (Identifier id : Registries.BLOCK.getIds()) {
            if (!CobbleFurniesCompat.FURNIES.equals(id.getNamespace()) || !id.getPath().contains("chair") || id.getPath().contains("arm")) continue;
            Block b = Registries.BLOCK.get(id);
            if (!b.getDefaultState().contains(Properties.HORIZONTAL_FACING) || !ShopSeats.isFurnitureSeat(b.getDefaultState())) continue;
            boolean oak = id.getPath().contains("oak") && !id.getPath().contains("dark");
            if (best == null || (oak && !(best.getPath().contains("oak") && !best.getPath().contains("dark")))
                    || (oak == (best.getPath().contains("oak") && !best.getPath().contains("dark")) && id.toString().compareTo(best.toString()) < 0))
                best = id;
        }
        furniesChair = best == null ? null : Registries.BLOCK.get(best);
        return furniesChair;
    }

    /** The chair at that spot; its sitter faces {@code faces}. */
    private static BlockState chairState(Direction faces) {
        Block f = furniesChair();
        if (f != null) return f.getDefaultState().with(Properties.HORIZONTAL_FACING, faces);
        // stairs: the sitter faces away from the high back
        return Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, faces.getOpposite())
                .with(StairsBlock.HALF, BlockHalf.BOTTOM).with(StairsBlock.SHAPE, StairShape.STRAIGHT);
    }

    @Nullable
    private static WanderingDuelistEntity build(ServerWorld w, BlockPos t, Direction axis) {
        WanderingDuelistEntity e = TcgEntities.WANDERING_DUELIST.create(w);
        if (e == null) return null;
        BlockPos his = t.offset(axis.getOpposite()), theirs = t.offset(axis);
        BlockState chairHis = chairState(axis), chairTheirs = chairState(axis.getOpposite());
        for (BlockPos q : new BlockPos[]{his, t, theirs}) { // plants first (tall grass tops), then the spot itself
            if (!w.getBlockState(q.up()).isAir()) w.setBlockState(q.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            if (!w.getBlockState(q).isAir()) w.setBlockState(q, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        // the two sides of the table (Active + Bench rows) line up with the chairs
        w.setBlockState(t, TcgBlocks.PLAY_TABLE.getDefaultState().with(DecorBlocks.FacingDecor.FACING, axis), Block.NOTIFY_ALL);
        w.setBlockState(his, chairHis, Block.NOTIFY_ALL);
        w.setBlockState(theirs, chairTheirs, Block.NOTIFY_ALL);
        e.setup(t, his, theirs, Registries.BLOCK.getId(chairHis.getBlock()).toString());
        Vec3d stand = standSpot(t, his);
        float yaw = yawToward(his, t);
        e.refreshPositionAndAngles(stand.x, stand.y, stand.z, yaw, 0f);
        if (!w.spawnEntity(e)) {
            removeBlocks(w, t, his, theirs, Registries.BLOCK.getId(chairHis.getBlock()).toString());
            return null;
        }
        e.sitNow();
        for (BlockPos q : new BlockPos[]{his, t, theirs})
            w.spawnParticles(ParticleTypes.POOF, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        w.playSound(null, t, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 0.8f, 1f);
        return e;
    }

    /** Removes the duelist's table and chairs: only blocks that are still his (same table / same chair block). */
    public static void removeBlocks(ServerWorld w, @Nullable BlockPos table, @Nullable BlockPos chairA, @Nullable BlockPos chairB, String chairBlock) {
        if (table != null && w.isChunkLoaded(table) && w.getBlockState(table).isOf(TcgBlocks.PLAY_TABLE)) poofRemove(w, table);
        for (BlockPos c : new BlockPos[]{chairA, chairB}) {
            if (c == null || !w.isChunkLoaded(c)) continue;
            // the seats on it (his saved one, the challenger's ShopSeatEntity): whoever sits there stands up
            for (ArmorStandEntity s : w.getEntitiesByClass(ArmorStandEntity.class, new Box(c).expand(0.5),
                    x -> x.getCommandTags().contains(ShopSeatEntity.TAG)))
                s.discard();
            Identifier id = Registries.BLOCK.getId(w.getBlockState(c).getBlock());
            if (id.toString().equals(chairBlock)) poofRemove(w, c);
        }
    }

    /**
     * His table was destroyed by something other than him (an explosion: his blocks are only protected from players
     * breaking them): he takes the dropped table and chairs with him, so blowing up his stall is not a free play table.
     * Only item entities right next to his spot and only as many as he placed (1 table, 2 chairs).
     */
    public static void collectDrops(ServerWorld w, @Nullable BlockPos table, String chairBlock) {
        if (table == null) return;
        Identifier chairId = Identifier.tryParse(chairBlock);
        net.minecraft.item.Item chairItem = chairId == null ? null : Registries.BLOCK.get(chairId).asItem();
        int[] left = {1, 2}; // table, chairs
        for (net.minecraft.entity.ItemEntity it : w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new Box(table).expand(6), x -> x.isAlive())) {
            ItemStack st = it.getStack();
            int k = st.isOf(TcgBlocks.PLAY_TABLE_ITEM) ? 0 : chairItem != null && chairItem != net.minecraft.item.Items.AIR && st.isOf(chairItem) ? 1 : -1;
            if (k < 0 || left[k] <= 0) continue;
            int n = Math.min(left[k], st.getCount());
            left[k] -= n;
            st.decrement(n);
            if (st.isEmpty()) it.discard();
            else it.setStack(st.copy()); // a new instance so the tracked stack (count) syncs
        }
    }

    private static void poofRemove(ServerWorld w, BlockPos p) {
        w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        w.spawnParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.4, p.getZ() + 0.5, 10, 0.3, 0.25, 0.3, 0.02);
        w.spawnParticles(ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.03);
    }

    // ------------------------------------------------------------------ stakes

    /** What a hooked match needs to settle it even if the duelist entity is gone by then. */
    private record Ctx(RegistryKey<World> dim, UUID entity, BlockPos table, BlockPos chairA, BlockPos chairB, String chairBlock,
                       String npcName, long prize) {}

    public static PtcgDuels.NpcMatchHook hook(WanderingDuelistEntity e) {
        Ctx c = new Ctx(e.getWorld().getRegistryKey(), e.getUuid(), e.table(), e.chairHis(), e.chairTheirs(), e.chairBlock(),
                e.getName().getString(), prize());
        MinecraftServer server = e.getServer();
        return (player, outcome) -> onResult(server, c, player, outcome);
    }

    /**
     * On accept: holds the stake in escrow (all the cash carried, up to the cap) before the match starts. Someone who
     * carries cash must stake at least {@link #minStake()}; someone with no cash at all stakes an item (taken only on a
     * loss). false = refused (the player was told why); nothing was taken.
     */
    public static boolean holdStake(ServerPlayerEntity p, WanderingDuelistEntity e) {
        MinecraftServer server = p.getServer();
        if (server == null) return false;
        DuelistDebts st = DuelistDebts.get(server);
        DuelistDebts.Escrow old = st.escrow(p.getUuid());
        if (old != null) {
            if (old.encounter().equals(e.getUuid())) return true; // already held for this very encounter
            if (PtcgDuels.busy(p.getUuid())) {
                p.sendMessage(Text.translatable("tcgshop.ptcg.challenge.busy_self").formatted(Formatting.RED), true);
                return false;
            }
            // not in any match: a stake left with another encounter (its chunk unloaded before it could give it back)
            // is stale, so it comes back now instead of blocking every other duelist until a restart
            releaseStake(server, p.getUuid(), old.encounter());
        }
        long cash = Math.max(0, CobbleDollarsBridge.getBalance(p)), min = minStake();
        if (cash > 0 && cash < min) {
            p.sendMessage(Text.translatable("tcgshop.duelist.min_stake", PriceService.format(min)).formatted(Formatting.RED), false);
            return false;
        }
        long take = Math.min(cash, maxTake());
        if (take > 0 && !CobbleDollarsBridge.tryCharge(p, take)) {
            p.sendMessage(Text.translatable("tcgshop.duelist.hold_failed").formatted(Formatting.RED), false);
            return false;
        }
        st.hold(p.getUuid(), e.getUuid(), take, cash == 0, e.getName().getString());
        if (take > 0) p.sendMessage(Text.translatable("tcgshop.duelist.stake_held", PriceService.format(take)).formatted(Formatting.GOLD), false);
        return true;
    }

    /** Refunds this player's stake if that encounter holds it (no-op otherwise: each stake is settled once). */
    public static void releaseStake(MinecraftServer server, UUID player, UUID encounter) {
        DuelistDebts.Escrow s = DuelistDebts.get(server).take(player, encounter);
        if (s != null) refund(server, player, s.amount(), s.npc());
    }

    /** The encounter is over without a result for whoever it still holds (back to waiting, packing up, removed). */
    public static void releaseEncounter(MinecraftServer server, UUID encounter) {
        for (UUID u : DuelistDebts.get(server).heldBy(encounter)) releaseStake(server, u, encounter);
    }

    /** Pays a stake back now (online, server running) or queues it for the next join. */
    static void refund(MinecraftServer server, UUID player, long amount, String npcName) {
        if (amount <= 0) return;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        if (p != null && server.isRunning() && CobbleDollarsBridge.add(p, amount)) {
            p.sendMessage(Text.translatable("tcgshop.duelist.stake_refunded", Text.literal(npcName).formatted(Formatting.YELLOW),
                    PriceService.format(amount)).formatted(Formatting.GREEN), false);
            return;
        }
        DuelistDebts.get(server).addRefund(player, npcName, amount);
    }

    private static void onResult(MinecraftServer server, Ctx c, UUID player, int outcome) {
        if (server == null) return;
        ServerWorld w = server.getWorld(c.dim);
        WanderingDuelistEntity e = w != null && w.getEntity(c.entity) instanceof WanderingDuelistEntity d && d.isAlive() ? d : null;
        if (outcome == -2) { // never started: the stake comes back and he keeps waiting (the player was told why)
            releaseStake(server, player, c.entity);
            if (e != null) e.startFailed();
            return;
        }
        // a dedicated server shutting down mid-match is not the player's doing: called off, stake back (a single-player
        // quit stays a loss, or quitting would be a free escape from a lost match)
        if (outcome == 0 && !server.isRunning() && server.isDedicated()) outcome = -1;
        String kind = "draw";
        if (outcome == 0 || outcome == 1) {
            // the one settlement of this stake: refunded with the prize on a win, kept on a loss
            DuelistDebts.Escrow s = DuelistDebts.get(server).take(player, c.entity);
            long held = s == null ? 0 : s.amount();
            boolean noCash = s != null && s.noCash();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
            if (p == null || !server.isRunning()) {
                DuelistDebts.get(server).add(player, outcome == 1, c.npcName, c.prize, held, noCash);
                kind = outcome == 1 ? "win" : "runaway";
            } else kind = settle(p, outcome == 1, c.npcName, c.prize, held, noCash);
        } else releaseStake(server, player, c.entity); // draw / aborted
        if (e != null) e.duelOver(kind);
        else if (w != null) removeBlocks(w, c.table, c.chairA, c.chairB, c.chairBlock);
    }

    /** A result saved before the escrow existed: the stake is charged now, the old way (cash up to cap, or an item). */
    static String settleLegacy(ServerPlayerEntity p, boolean won, String npcName, long prize, long cap) {
        if (won) return settle(p, true, npcName, prize, 0, false);
        long cash = CobbleDollarsBridge.isAvailable() ? Math.max(0, CobbleDollarsBridge.getBalance(p)) : 0;
        long take = Math.min(cash, Math.max(0, cap));
        boolean charged = take > 0 && CobbleDollarsBridge.add(p, -take);
        return settle(p, false, npcName, prize, charged ? take : 0, cash == 0);
    }

    /**
     * Applies the result to an online player. held = the stake that was in escrow (already taken from the player).
     * Win: the prize + the stake back. Loss: the stake is kept, or, if they carried no cash at all on accept (noCash),
     * one random stack. Chat + phone message. Returns the kind of ending ("win", "lose_cash", "lose_item", "lose_nothing").
     */
    static String settle(ServerPlayerEntity p, boolean won, String npcName, long prize, long held, boolean noCash) {
        MinecraftServer server = p.getServer();
        Text name = Text.literal(npcName).formatted(Formatting.YELLOW);
        if (won) {
            if (prize > 0) CobbleDollarsBridge.add(p, prize);
            String money = PriceService.format(prize);
            p.sendMessage(Text.translatable("tcgshop.duelist.won", name, money).formatted(Formatting.GOLD), false);
            if (server != null) refund(server, p.getUuid(), held, npcName);
            p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1f);
            CardAdvancements.grant(p, "duelist_win");
            PhoneMessages.send(server, p.getUuid(), SENDER, Text.translatable("tcgshop.duelist.msg.win", money), false);
            return "win";
        }
        if (held > 0) { // the stake was taken on accept: he just keeps it
            String money = PriceService.format(held);
            p.sendMessage(Text.translatable("tcgshop.duelist.lost_cash", name, money).formatted(Formatting.RED), false);
            p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.8f, 0.8f);
            PhoneMessages.send(server, p.getUuid(), SENDER, Text.translatable("tcgshop.duelist.msg.cash", money), true);
            return "lose_cash";
        }
        // he only takes an item from someone who carried no cash at all on accept (cap 0, or a stake already refunded
        // because the encounter ended first: nothing is taken)
        ItemStack taken = noCash ? stealRandom(p) : ItemStack.EMPTY;
        if (taken.isEmpty()) {
            p.sendMessage(Text.translatable("tcgshop.duelist.lost_nothing", name).formatted(Formatting.GRAY), false);
            PhoneMessages.send(server, p.getUuid(), SENDER, Text.translatable("tcgshop.duelist.msg.nothing"), false);
            return "lose_nothing";
        }
        Text what = stackText(taken);
        p.sendMessage(Text.translatable("tcgshop.duelist.lost_item", name, what).formatted(Formatting.RED), false);
        p.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 0.6f);
        PhoneMessages.send(server, p.getUuid(), SENDER, Text.translatable("tcgshop.duelist.msg.item", what), true);
        TcgShop.LOGGER.info("Wandering Duelist {} took {} x{} from {}", npcName, Registries.ITEM.getId(taken.getItem()), taken.getCount(), p.getName().getString());
        return "lose_item";
    }

    private static Text stackText(ItemStack st) {
        MutableText n = st.getName().copy();
        return st.getCount() > 1 ? Text.translatable("tcgshop.duelist.stack", n, st.getCount()) : n;
    }

    /** Takes one random stack from the main inventory (hotbar included; armor and offhand never). Empty = nothing to take. */
    private static ItemStack stealRandom(ServerPlayerEntity p) {
        var inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.main.size(); i++) if (stealable(inv.main.get(i))) slots.add(i);
        if (slots.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = inv.removeStack(slots.get(p.getRandom().nextInt(slots.size())));
        inv.markDirty();
        return taken;
    }

    /** Never binders (they hold your collection), the phone, or items that carry other items (shulker boxes, bundles). */
    private static boolean stealable(ItemStack st) {
        if (st.isEmpty()) return false;
        if (st.isOf(TcgItems.GENERAL_BINDER) || st.isOf(TcgItems.PERSONAL_BINDER) || st.isOf(TcgItems.TCG_PHONE)) return false;
        if (st.contains(TcgItems.BINDER_TAG)) return false;
        return !st.contains(DataComponentTypes.CONTAINER) && !st.contains(DataComponentTypes.BUNDLE_CONTENTS);
    }
}
