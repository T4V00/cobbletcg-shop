package dev.tcgshop.ptcg;

import dev.masterball.api.card.CardCatalog;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.compat.CobbleFurniesCompat;
import dev.tcgshop.compat.CobblemonBattles;
import dev.tcgshop.compat.NpcPokemonDuel;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.NpcLines;
import dev.tcgshop.entity.ShopSeatEntity;
import dev.tcgshop.shop.DecorBlocks;
import dev.tcgshop.shop.ShopSeats;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 1.0 · Play table life (ROADMAP "TCG table"):
 * <ul>
 *   <li>A play table needs {@code tableChairsRequired} chairs (CobbleFurnies, or any modded seat such as Cozyhome's) within
 *   {@code tableChairRadius} blocks before anyone can play at it. Without CobbleFurnies/Cozyhome installed, bottom-half
 *   stairs count as chairs ({@code tableStairsWithoutFurnitureMods}) so the table never becomes useless.</li>
 *   <li>Customer challenge: in a shop, a customer may walk up to a player standing near a ready table and challenge
 *   them. Accept with the chat button ({@code /tcgtable accept}) or by clicking the table: the customer sits at a chair
 *   and a friendly real-TCG match against the AI starts, shown with the customer's name (PtcgDuels.startNpcMatch).</li>
 *   <li>Exhibition: two customers sit at the chairs facing each other and pretend to play a match for a while (cards on
 *   the table, page sounds, a few lines). The table is held meanwhile.</li>
 * </ul>
 * NPCs taking part are "held" ({@link NpcPokemonDuel#hold}): their own AI pauses while this class drives them, and
 * they are released exactly as they were (standing next to the chair) when the scene ends. Nothing is saved: on a
 * restart every scene simply ends.
 */
public final class PlayTables {
    /** NPC lines (tcgshop.table.<cat>.<n>, tools/gen_lang_10b_shopextras.py). */
    private static final Map<String, Integer> LINES = Map.of("ask", 5, "yes", 3, "no", 3, "gg", 4, "invite", 3, "play", 8, "win", 4, "lose", 4);

    private enum Kind { EXHIBITION, CHALLENGE }

    private enum Phase { WALK, PLAY, APPROACH, ASK, START, MATCH }

    private static final class Scene {
        final Kind kind;
        final ServerWorld world;
        final UUID zone;
        final BlockPos table;
        final CustomerEntity[] npc;
        final BlockPos[] chair;
        final Vec3d[] stand;
        @Nullable UUID player;
        Phase phase;
        long phaseAt, startedAt, endAt, lastStartTry = -1000, firstStartOk = -1;
        int level;
        /** Ticks since the scene began (advanced by 5 on every update). */
        int age;

        Scene(Kind kind, ServerWorld world, UUID zone, BlockPos table, int n) {
            this.kind = kind;
            this.world = world;
            this.zone = zone;
            this.table = table.toImmutable();
            this.npc = new CustomerEntity[n];
            this.chair = new BlockPos[n];
            this.stand = new Vec3d[n];
        }
    }

    private static final List<Scene> SCENES = new ArrayList<>();
    /** Last customer challenge per player (server tick). */
    private static final Map<UUID, Integer> LAST_CHALLENGE = new HashMap<>();
    private static List<String> cardIds;

    private PlayTables() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlayTables::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> endAll());
        CommandRegistrationCallback.EVENT.register((d, access, env) -> d.register(CommandManager.literal("tcgtable")
                .then(CommandManager.literal("accept").executes(ctx -> { answer(ctx.getSource().getPlayerOrThrow(), true); return 1; }))
                .then(CommandManager.literal("decline").executes(ctx -> { answer(ctx.getSource().getPlayerOrThrow(), false); return 1; }))));
    }

    // ------------------------------------------------------------------ configuration (EconomyConfig, clamped here)

    private static int chairsRequired() { return Math.max(0, Math.min(8, EconomyConfig.INSTANCE.tableChairsRequired)); }

    private static int radius() { return Math.max(1, Math.min(4, EconomyConfig.INSTANCE.tableChairRadius)); }

    private static boolean stairsCount() {
        return EconomyConfig.INSTANCE.tableStairsWithoutFurnitureMods
                && !FabricLoader.getInstance().isModLoaded(CobbleFurniesCompat.FURNIES)
                && !FabricLoader.getInstance().isModLoaded(CobbleFurniesCompat.COZYHOME);
    }

    private static double clamp01(double v) { return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v)); }

    // ------------------------------------------------------------------ chairs

    /** Chairs around the table (closest first): modded seats by registry name and, without furniture mods, stair benches. */
    public static List<BlockPos> chairs(ServerWorld w, BlockPos table) {
        int r = radius();
        boolean stairs = stairsCount();
        List<BlockPos> out = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos q = table.add(dx, dy, dz);
                    if (!w.isChunkLoaded(q)) continue;
                    BlockState s = w.getBlockState(q);
                    if (ShopSeats.isFurnitureSeat(s) || (stairs && s.getBlock() instanceof StairsBlock && s.get(StairsBlock.HALF) == BlockHalf.BOTTOM))
                        out.add(q.toImmutable());
                }
        out.sort(Comparator.comparingDouble(q -> q.getSquaredDistance(table)));
        return out;
    }

    /** Does the table have enough chairs to be played at? */
    public static boolean ready(ServerWorld w, BlockPos table) {
        int need = chairsRequired();
        return need <= 0 || chairs(w, table).size() >= need;
    }

    /**
     * Called by PtcgDuels.useTable before anything else (except reopening your own match). true = the click was handled
     * here: a pending customer challenge was accepted, the table is held by customers, or it lacks chairs (explained).
     */
    public static boolean intercept(ServerPlayerEntity p, BlockPos table) {
        ServerWorld w = p.getServerWorld();
        Scene s = sceneAt(w, table);
        if (s != null) {
            if (s.kind == Kind.CHALLENGE && p.getUuid().equals(s.player)) {
                if (s.phase == Phase.ASK) { accept(s, p); return true; }
                if (s.phase == Phase.START) return true; // already starting
                if (s.phase == Phase.MATCH) return false; // PtcgDuels handles it as usual
                end(s); // APPROACH: the customer had not asked yet and the player wants the table for something else
            } else {
                p.sendMessage(Text.translatable(s.kind == Kind.EXHIBITION ? "tcgshop.table.busy_npc" : "tcgshop.table.reserved").formatted(Formatting.RED), true);
                return true;
            }
        }
        int need = chairsRequired();
        if (need > 0) {
            int have = chairs(w, table).size();
            if (have < need) {
                p.sendMessage(Text.translatable(stairsCount() ? "tcgshop.table.need_seats" : "tcgshop.table.need_chairs", need, radius(), have)
                        .formatted(Formatting.GOLD), false);
                p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 0.6f, 1f);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ scene bookkeeping

    @Nullable
    private static Scene sceneAt(ServerWorld w, BlockPos table) {
        for (Scene s : SCENES) if (s.world == w && s.table.equals(table)) return s;
        return null;
    }

    private static boolean inScene(CustomerEntity c) {
        for (Scene s : SCENES) for (CustomerEntity n : s.npc) if (n == c) return true;
        return false;
    }

    /** A customer free to be borrowed: a normal shopper, not queuing, dealing, seated, fighting or in an event. */
    private static boolean eligible(CustomerEntity c) {
        if (!c.isAlive() || c.isRemoved() || c.hasVehicle() || c.hurtTime > 0) return false;
        CustomerEntity.Type t = c.type();
        if (t == CustomerEntity.Type.THIEF || t == CustomerEntity.Type.SELLER || t == CustomerEntity.Type.TRADER) return false;
        if (c.wantsAttention() || c.waitPermille() >= 0 || c.isFleeingThief()) return false;
        String role = c.roleKey();
        if (role == null || !(role.equals("tcgshop.role." + t.name().toLowerCase(Locale.ROOT)) || role.equals("tcgshop.role.regular_back")))
            return false; // an event actor (Jigglypuff, inspector...): its event drives it
        if (NpcPokemonDuel.busy(c) || CobblemonBattles.isFighting(c)) return false;
        return !ShopSeats.hasReservation(c.getUuid()) && !inScene(c);
    }

    private static boolean tableFree(ServerWorld w, BlockPos table) {
        return w.getBlockState(table).isOf(TcgBlocks.PLAY_TABLE) && sceneAt(w, table) == null && !PtcgDuels.tableInUse(table) && ready(w, table);
    }

    /** Chairs of that table a customer could sit on now (free, not reserved, with a spot next to them to sit down from). */
    private static List<ShopSeats.Pick> freeChairs(ServerWorld w, ShopZones.Zone z, BlockPos table, CustomerEntity who) {
        List<ShopSeats.Pick> out = new ArrayList<>();
        for (BlockPos ch : chairs(w, table)) {
            if (!z.contains(ch) || ShopSeats.reservedByOther(w, ch, who.getUuid()) || !ShopSeats.isFree(w, ch, who)) continue;
            ShopSeats.Seat seat = ShopSeats.seatFacing(w, ch, yawToward(ch, table));
            Vec3d stand = ShopSeats.standSpot(w, z, seat);
            if (stand != null) out.add(new ShopSeats.Pick(seat, stand));
        }
        return out;
    }

    private static float yawToward(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    private static void line(CustomerEntity c, String cat) {
        NpcLines.speak(c, Text.translatable("tcgshop.table." + cat + "." + c.getRandom().nextInt(LINES.getOrDefault(cat, 1))));
    }

    // ------------------------------------------------------------------ loop

    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        if (!SCENES.isEmpty() && t % 5 == 0)
            for (Scene s : new ArrayList<>(SCENES)) {
                try {
                    update(s);
                } catch (RuntimeException ex) {
                    dev.tcgshop.TcgShop.LOGGER.warn("Play table scene failed: {}", ex.toString());
                    end(s);
                }
            }
        if (t % 100 == 37) roll(server);
    }

    /** Every 5 s: maybe a customer challenge to a player near a ready table, maybe an exhibition at a free one. */
    private static void roll(MinecraftServer server) {
        EconomyConfig c = EconomyConfig.INSTANCE;
        double chChance = clamp01(c.tableChallengeChance), exChance = clamp01(c.tableExhibitionChance);
        if (chChance <= 0 && exChance <= 0) return;
        int cooldown = Math.max(0, c.tableChallengeCooldownSeconds) * 20;
        for (ServerWorld w : server.getWorlds()) {
            List<ServerPlayerEntity> players = w.getPlayers();
            if (players.isEmpty()) continue;
            for (ShopZones.Zone z : ShopZones.get(w).all()) {
                if (!w.isChunkLoaded(z.core)) continue;
                List<ServerPlayerEntity> near = new ArrayList<>();
                for (ServerPlayerEntity p : players) if (!p.isSpectator() && z.box().expand(16).contains(p.getPos())) near.add(p);
                if (near.isEmpty()) continue; // nobody would see it
                List<CustomerEntity> pool = w.getEntitiesByClass(CustomerEntity.class, z.box(), cc -> z.contains(cc.getBlockPos()) && eligible(cc));
                if (pool.isEmpty()) continue;
                List<BlockPos> tables = ShopSeats.tables(w, z);
                if (tables.isEmpty()) continue;
                Random r = w.getRandom();
                // customer challenges
                if (chChance > 0) for (ServerPlayerEntity p : near) {
                    if (!z.contains(p.getBlockPos()) || PtcgDuels.busy(p.getUuid()) || challengeFor(p.getUuid()) != null) continue;
                    Integer last = LAST_CHALLENGE.get(p.getUuid());
                    if (last != null && server.getTicks() - last < cooldown && server.getTicks() >= last) continue;
                    if (r.nextDouble() >= chChance) continue;
                    BlockPos table = null;
                    for (BlockPos tb : tables)
                        if (tb.getSquaredDistance(p.getPos()) <= 8 * 8 && tableFree(w, tb) && (table == null || tb.getSquaredDistance(p.getPos()) < table.getSquaredDistance(p.getPos())))
                            table = tb;
                    if (table == null) continue;
                    CustomerEntity cu = null;
                    for (CustomerEntity cc : pool)
                        if (cc.squaredDistanceTo(p) <= 24 * 24 && (cu == null || cc.squaredDistanceTo(p) < cu.squaredDistanceTo(p))) cu = cc;
                    if (cu == null) continue;
                    startChallenge(w, z, table, cu, p);
                    pool.remove(cu);
                    if (pool.isEmpty()) break;
                }
                // exhibitions: at most one per shop at a time
                if (exChance > 0 && pool.size() >= 2 && !hasExhibition(z.id)) {
                    for (BlockPos tb : tables) {
                        if (!tableFree(w, tb) || r.nextDouble() >= exChance) continue;
                        if (startExhibition(w, z, tb, pool)) break;
                    }
                }
            }
        }
    }

    private static boolean hasExhibition(UUID zone) {
        for (Scene s : SCENES) if (s.kind == Kind.EXHIBITION && s.zone.equals(zone)) return true;
        return false;
    }

    @Nullable
    private static Scene challengeFor(UUID player) {
        for (Scene s : SCENES) if (s.kind == Kind.CHALLENGE && player.equals(s.player)) return s;
        return null;
    }

    // ------------------------------------------------------------------ exhibition (two customers playing)

    private static boolean startExhibition(ServerWorld w, ShopZones.Zone z, BlockPos table, List<CustomerEntity> pool) {
        List<CustomerEntity> near = new ArrayList<>();
        for (CustomerEntity c : pool) if (c.squaredDistanceTo(table.toCenterPos()) <= 24 * 24) near.add(c);
        if (near.size() < 2) return false;
        near.sort(Comparator.comparingDouble(c -> c.squaredDistanceTo(table.toCenterPos())));
        CustomerEntity a = near.get(0), b = near.get(1);
        List<ShopSeats.Pick> chairs = freeChairs(w, z, table, a);
        if (chairs.size() < 2) return false;
        // the two chairs farthest apart: they sit facing each other across the table
        ShopSeats.Pick ca = null, cb = null;
        double best = -1;
        for (int i = 0; i < chairs.size(); i++)
            for (int j = i + 1; j < chairs.size(); j++) {
                double d = chairs.get(i).seat().pos().getSquaredDistance(chairs.get(j).seat().pos());
                if (d > best) { best = d; ca = chairs.get(i); cb = chairs.get(j); }
            }
        if (ca == null || !ShopSeats.reserve(w, ca.seat().pos(), a.getUuid())) return false;
        if (!ShopSeats.reserve(w, cb.seat().pos(), b.getUuid())) { ShopSeats.release(a.getUuid()); return false; }
        Scene s = new Scene(Kind.EXHIBITION, w, z.id, table, 2);
        s.npc[0] = a;
        s.npc[1] = b;
        s.chair[0] = ca.seat().pos();
        s.chair[1] = cb.seat().pos();
        s.stand[0] = ca.stand();
        s.stand[1] = cb.stand();
        s.phase = Phase.WALK;
        s.phaseAt = s.startedAt = w.getTime();
        for (int i = 0; i < 2; i++) {
            NpcPokemonDuel.hold(s.npc[i]);
            s.npc[i].getNavigation().startMovingTo(s.stand[i].x, s.stand[i].y, s.stand[i].z, 0.8);
        }
        SCENES.add(s);
        line(a, "invite");
        return true;
    }

    // ------------------------------------------------------------------ challenge (a customer challenges a player)

    private static void startChallenge(ServerWorld w, ShopZones.Zone z, BlockPos table, CustomerEntity c, ServerPlayerEntity p) {
        Scene s = new Scene(Kind.CHALLENGE, w, z.id, table, 1);
        s.npc[0] = c;
        s.player = p.getUuid();
        s.phase = Phase.APPROACH;
        s.phaseAt = s.startedAt = w.getTime();
        double roll = w.getRandom().nextDouble();
        s.level = roll < 0.55 ? 0 : roll < 0.9 ? 1 : 2; // mostly easy and normal players, sometimes a strong one
        NpcPokemonDuel.hold(c);
        c.getNavigation().startMovingTo(p.getX(), p.getY(), p.getZ(), 0.8);
        LAST_CHALLENGE.put(p.getUuid(), w.getServer().getTicks());
        SCENES.add(s);
        PtcgDuels.warmUp(); // the engine starts loading while the customer walks over
    }

    private static void ask(Scene s, ServerPlayerEntity p) {
        CustomerEntity c = s.npc[0];
        s.phase = Phase.ASK;
        s.phaseAt = s.world.getTime();
        c.getNavigation().stop();
        line(c, "ask");
        Text accept = Text.translatable("tcgshop.table.challenge.accept").styled(st -> st.withColor(Formatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgtable accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("tcgshop.table.challenge.accept_hint"))));
        Text decline = Text.translatable("tcgshop.table.challenge.decline").styled(st -> st.withColor(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgtable decline")));
        p.sendMessage(Text.translatable("tcgshop.table.challenge.received", c.getDisplayName(),
                Text.translatable("tcgshop.table.level." + s.level)).formatted(Formatting.GOLD), false);
        p.sendMessage(Text.literal("   ").append(accept).append(Text.literal("   ")).append(decline), false);
        p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
    }

    /** /tcgtable accept|decline. */
    private static void answer(ServerPlayerEntity p, boolean yes) {
        Scene s = challengeFor(p.getUuid());
        if (s == null || s.phase != Phase.ASK) {
            p.sendMessage(Text.translatable("tcgshop.table.challenge.none").formatted(Formatting.GRAY), true);
            return;
        }
        if (yes) accept(s, p);
        else {
            line(s.npc[0], "no");
            end(s);
        }
    }

    private static void accept(Scene s, ServerPlayerEntity p) {
        CustomerEntity c = s.npc[0];
        if (p.getServerWorld() != s.world || PtcgDuels.busy(p.getUuid())) {
            p.sendMessage(Text.translatable("tcgshop.ptcg.challenge.busy_self").formatted(Formatting.RED), true);
            return;
        }
        s.phase = Phase.START;
        s.phaseAt = s.world.getTime();
        line(c, "yes");
        // the customer takes a chair at the table (if none is free it just stands next to it)
        ShopZones.Zone z = ShopZones.get(s.world).byId(s.zone);
        if (z != null) {
            for (ShopSeats.Pick pick : freeChairs(s.world, z, s.table, c)) {
                if (!ShopSeats.reserve(s.world, pick.seat().pos(), c.getUuid())) continue;
                s.chair[0] = pick.seat().pos();
                s.stand[0] = pick.stand();
                break;
            }
        }
        Vec3d go = s.stand[0] != null ? s.stand[0] : s.table.toCenterPos();
        c.getNavigation().startMovingTo(go.x, go.y, go.z, 0.8);
        tryStart(s, p);
    }

    private static void tryStart(Scene s, ServerPlayerEntity p) {
        s.lastStartTry = s.world.getTime();
        if (PtcgDuels.startNpcMatch(p, s.table, s.npc[0].getName().getString(), s.level) && s.firstStartOk < 0) s.firstStartOk = s.world.getTime();
    }

    // ------------------------------------------------------------------ per-scene update (every 5 ticks)

    private static void update(Scene s) {
        ServerWorld w = s.world;
        long now = w.getTime();
        s.age += 5;
        for (CustomerEntity c : s.npc) if (c == null || c.isRemoved() || !c.isAlive() || c.getWorld() != w) { end(s); return; }
        if (!w.getBlockState(s.table).isOf(TcgBlocks.PLAY_TABLE)) { end(s); return; }
        if (s.age % 20 == 0) {
            for (CustomerEntity c : s.npc) if (c.hurtTime > 0) { end(s); return; } // someone hit them: the scene breaks up
            if (ShopSeats.alarmSince(s.zone, s.startedAt)) { end(s); return; } // theft alarm: everybody gets up
        }
        switch (s.phase) {
            case WALK -> {
                boolean all = true;
                for (int i = 0; i < s.npc.length; i++) if (!walkAndSit(s, i)) all = false;
                if (all) {
                    s.phase = Phase.PLAY;
                    s.phaseAt = now;
                    EconomyConfig c = EconomyConfig.INSTANCE;
                    int min = Math.max(10, c.tableExhibitionMinSeconds), max = Math.max(min, c.tableExhibitionMaxSeconds);
                    s.endAt = now + 20L * (min + w.getRandom().nextInt(max - min + 1));
                    dealBoard(s, true);
                    w.playSound(null, s.table, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.8f, 1.1f);
                } else if (now - s.phaseAt > 20 * 20) end(s); // could not reach the chairs
            }
            case PLAY -> {
                for (int i = 0; i < s.npc.length; i++) {
                    if (!(s.npc[i].getVehicle() instanceof ShopSeatEntity)) { end(s); return; } // the chair was removed
                    Vec3d c = s.table.toCenterPos();
                    s.npc[i].getLookControl().lookAt(c.x, c.y + 0.4, c.z);
                }
                if (now >= s.endAt) { finishExhibition(s); return; }
                if (s.age % 60 == 0) playBeat(s);
            }
            case APPROACH -> {
                ServerPlayerEntity p = challenged(s);
                if (p == null) { end(s); return; }
                CustomerEntity c = s.npc[0];
                if (c.squaredDistanceTo(p) < 3 * 3) { ask(s, p); return; }
                if (c.getNavigation().isIdle() || s.age % 20 == 0) c.getNavigation().startMovingTo(p.getX(), p.getY(), p.getZ(), 0.8);
                if (now - s.phaseAt > 20 * 20) end(s); // could not reach the player: quietly gives up
            }
            case ASK -> {
                ServerPlayerEntity p = challenged(s);
                if (p == null) { end(s); return; }
                CustomerEntity c = s.npc[0];
                c.getLookControl().lookAt(p, 30f, 30f);
                if (now - s.phaseAt > 30 * 20) {
                    p.sendMessage(Text.translatable("tcgshop.table.challenge.expired", c.getDisplayName()).formatted(Formatting.GRAY), false);
                    line(c, "no");
                    end(s);
                }
            }
            case START, MATCH -> {
                ServerPlayerEntity p = s.world.getServer().getPlayerManager().getPlayer(s.player);
                if (s.chair[0] != null) walkAndSit(s, 0);
                else if (s.npc[0].squaredDistanceTo(s.table.toCenterPos()) < 2.5 * 2.5) s.npc[0].getNavigation().stop();
                Vec3d tc = s.table.toCenterPos();
                if (s.npc[0].hasVehicle()) s.npc[0].getLookControl().lookAt(tc.x, tc.y + 0.4, tc.z);
                if (s.phase == Phase.START) {
                    if (PtcgDuels.tableMatchRunning(s.table) && p != null && PtcgDuels.busy(p.getUuid())) {
                        s.phase = Phase.MATCH;
                        s.phaseAt = now;
                        return;
                    }
                    if (p == null || p.getServerWorld() != w) { end(s); return; }
                    if (s.firstStartOk < 0) {
                        // the engine could not load: the first try already said so in chat, do not repeat it every 2 s
                        if (PtcgEngine.failed()) { failStart(s, p); return; }
                        // engine still loading: retry every 2 s for up to 40 s
                        if (now - s.lastStartTry >= 40) tryStart(s, p);
                        if (now - s.phaseAt > 40 * 20) { failStart(s, p); return; }
                    } else if (now - s.firstStartOk > 20 * 20) { failStart(s, p); return; } // deck refused: PtcgDuels said why
                } else if (!PtcgDuels.tableMatchRunning(s.table)) {
                    line(s.npc[0], "gg");
                    end(s);
                }
            }
        }
    }

    @Nullable
    private static ServerPlayerEntity challenged(Scene s) {
        ServerPlayerEntity p = s.player == null ? null : s.world.getServer().getPlayerManager().getPlayer(s.player);
        if (p == null || p.getServerWorld() != s.world || p.isSpectator() || PtcgDuels.busy(p.getUuid())) return null;
        if (p.squaredDistanceTo(s.table.toCenterPos()) > 24 * 24) return null; // walked away
        return p;
    }

    private static void failStart(Scene s, ServerPlayerEntity p) {
        p.sendMessage(Text.translatable("tcgshop.table.challenge.failed").formatted(Formatting.GRAY), false);
        line(s.npc[0], "no");
        end(s);
    }

    /** Walks NPC i to its chair and sits it down facing the table. true = seated. */
    private static boolean walkAndSit(Scene s, int i) {
        CustomerEntity c = s.npc[i];
        if (c.getVehicle() instanceof ShopSeatEntity) return true;
        BlockPos ch = s.chair[i];
        Vec3d st = s.stand[i];
        if (ch == null || st == null) return false;
        ServerWorld w = s.world;
        if (!ShopSeats.stillSeat(w, ch)) return false;
        Vec3d center = ch.toCenterPos();
        double dx = center.x - c.getX(), dz = center.z - c.getZ();
        if (dx * dx + dz * dz < 1.45 * 1.45 && Math.abs(c.getY() - ch.getY()) < 1.2 && ShopSeats.isFree(w, ch, c)) {
            float yaw = yawToward(ch, s.table);
            ShopSeats.Seat seat = ShopSeats.seatFacing(w, ch, yaw);
            c.getNavigation().stop();
            ShopSeatEntity e = ShopSeatEntity.spawn(w, ch, seat.seatY(), yaw, st);
            if (!c.startRiding(e, true)) {
                e.discard();
                return false;
            }
            return true;
        }
        if (c.getNavigation().isIdle() || s.age % 40 == 0) c.getNavigation().startMovingTo(st.x, st.y, st.z, 0.8);
        return false;
    }

    // ------------------------------------------------------------------ exhibition details

    private static List<String> cardIds() {
        if (cardIds == null || cardIds.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.isCard(id) && !CardCatalog.isRemovedId(id.toString())) l.add(id.toString());
            cardIds = l;
        }
        return cardIds;
    }

    /** Puts cards on the table (Active + Bench on each side; the table renderer draws them). fresh = a new board. */
    private static void dealBoard(Scene s, boolean fresh) {
        if (!(s.world.getBlockEntity(s.table) instanceof DecorBlocks.DecorEntity be)) return;
        List<String> ids = cardIds();
        if (ids.isEmpty()) return;
        Random r = s.world.getRandom();
        if (fresh || be.board.size() != 8) {
            be.board.clear();
            for (int side = 0; side < 2; side++) {
                be.board.add(ids.get(r.nextInt(ids.size())));
                int bench = 1 + r.nextInt(3);
                for (int k = 0; k < 3; k++) be.board.add(k < bench ? ids.get(r.nextInt(ids.size())) : "");
            }
        } else {
            // a "play": a new Active or a Pokémon added to / replaced on the Bench
            be.board.set(r.nextInt(8), ids.get(r.nextInt(ids.size())));
        }
        be.sync();
    }

    private static void clearBoard(Scene s) {
        if (PtcgDuels.tableInUse(s.table)) return; // a real match owns the board
        if (s.world.getBlockEntity(s.table) instanceof DecorBlocks.DecorEntity be && !be.board.isEmpty()) {
            be.board.clear();
            be.sync();
        }
    }

    /** One "turn" of the pretend match: a card played, a page sound, a hand swing, sometimes a line. */
    private static void playBeat(Scene s) {
        Random r = s.world.getRandom();
        CustomerEntity c = s.npc[r.nextInt(s.npc.length)];
        c.swingHand(Hand.MAIN_HAND);
        Vec3d t = s.table.toCenterPos();
        s.world.playSound(null, s.table, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.7f, 0.9f + r.nextFloat() * 0.3f);
        if (r.nextFloat() < 0.6f) dealBoard(s, false);
        if (r.nextFloat() < 0.35f) s.world.spawnParticles(ParticleTypes.ENCHANT, t.x, t.y + 0.5, t.z, 6, 0.3, 0.1, 0.3, 0.4);
        if (r.nextFloat() < 0.18f) line(c, "play");
    }

    private static void finishExhibition(Scene s) {
        Random r = s.world.getRandom();
        int win = r.nextInt(2);
        CustomerEntity w = s.npc[win], l = s.npc[1 - win];
        line(w, "win");
        if (r.nextFloat() < 0.7f) line(l, "lose");
        s.world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, w.getX(), w.getY() + 1.6, w.getZ(), 6, 0.3, 0.3, 0.3, 0);
        s.world.playSound(null, s.table, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.4f, 1.6f);
        end(s);
    }

    // ------------------------------------------------------------------ ending

    /** Ends a scene: everyone stands up next to their chair, frees it and gets their own AI back. */
    private static void end(Scene s) {
        SCENES.remove(s);
        for (CustomerEntity c : s.npc) {
            if (c == null) continue;
            if (c.getVehicle() instanceof ShopSeatEntity seat) {
                Vec3d st = seat.standPos();
                c.stopRiding();
                seat.discard();
                if (st != null && !c.isRemoved()) c.requestTeleport(st.x, st.y, st.z);
            }
            ShopSeats.release(c.getUuid());
            c.getNavigation().stop();
            NpcPokemonDuel.unhold(c);
        }
        if (s.kind == Kind.EXHIBITION) clearBoard(s);
    }

    private static void endAll() {
        for (Scene s : new ArrayList<>(SCENES)) end(s);
        SCENES.clear();
        LAST_CHALLENGE.clear();
    }
}
