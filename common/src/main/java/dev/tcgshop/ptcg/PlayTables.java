package dev.tcgshop.ptcg;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.event.ServerEvents;
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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
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
        final ServerLevel world;
        final UUID zone;
        final BlockPos table;
        final CustomerEntity[] npc;
        final BlockPos[] chair;
        final Vec3[] stand;
        @Nullable UUID player;
        Phase phase;
        long phaseAt, startedAt, endAt, lastStartTry = -1000, firstStartOk = -1;
        int level;
        /** Ticks since the scene began (advanced by 5 on every update). */
        int age;

        Scene(Kind kind, ServerLevel world, UUID zone, BlockPos table, int n) {
            this.kind = kind;
            this.world = world;
            this.zone = zone;
            this.table = table.immutable();
            this.npc = new CustomerEntity[n];
            this.chair = new BlockPos[n];
            this.stand = new Vec3[n];
        }
    }

    private static final List<Scene> SCENES = new ArrayList<>();
    /** Last customer challenge per player (server tick). */
    private static final Map<UUID, Integer> LAST_CHALLENGE = new HashMap<>();
    private static List<String> cardIds;

    private PlayTables() {}

    public static void register() {
        ServerEvents.TICK_END.register(PlayTables::tick);
        ServerEvents.STOPPING.register(s -> endAll());
        ServerEvents.COMMANDS.register((d, access, env) -> d.register(Commands.literal("tcgtable")
                .then(Commands.literal("accept").executes(ctx -> { answer(ctx.getSource().getPlayerOrException(), true); return 1; }))
                .then(Commands.literal("decline").executes(ctx -> { answer(ctx.getSource().getPlayerOrException(), false); return 1; }))));
    }

    // ------------------------------------------------------------------ configuration (EconomyConfig, clamped here)

    private static int chairsRequired() { return Math.max(0, Math.min(8, EconomyConfig.INSTANCE.tableChairsRequired)); }

    private static int radius() { return Math.max(1, Math.min(4, EconomyConfig.INSTANCE.tableChairRadius)); }

    private static boolean stairsCount() {
        return EconomyConfig.INSTANCE.tableStairsWithoutFurnitureMods
                && !Platform.INSTANCE.isModLoaded(CobbleFurniesCompat.FURNIES)
                && !Platform.INSTANCE.isModLoaded(CobbleFurniesCompat.COZYHOME);
    }

    private static double clamp01(double v) { return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v)); }

    // ------------------------------------------------------------------ chairs

    /** Chairs around the table (closest first): modded seats by registry name and, without furniture mods, stair benches. */
    public static List<BlockPos> chairs(ServerLevel w, BlockPos table) {
        int r = radius();
        boolean stairs = stairsCount();
        List<BlockPos> out = new ArrayList<>();
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos q = table.offset(dx, dy, dz);
                    if (!w.hasChunkAt(q)) continue;
                    BlockState s = w.getBlockState(q);
                    if (ShopSeats.isFurnitureSeat(s) || (stairs && s.getBlock() instanceof StairBlock && s.getValue(StairBlock.HALF) == Half.BOTTOM))
                        out.add(q.immutable());
                }
        out.sort(Comparator.comparingDouble(q -> q.distSqr(table)));
        return out;
    }

    /** Does the table have enough chairs to be played at? */
    public static boolean ready(ServerLevel w, BlockPos table) {
        int need = chairsRequired();
        return need <= 0 || chairs(w, table).size() >= need;
    }

    /**
     * Called by PtcgDuels.useTable before anything else (except reopening your own match). true = the click was handled
     * here: a pending customer challenge was accepted, the table is held by customers, or it lacks chairs (explained).
     */
    public static boolean intercept(ServerPlayer p, BlockPos table) {
        ServerLevel w = p.serverLevel();
        Scene s = sceneAt(w, table);
        if (s != null) {
            if (s.kind == Kind.CHALLENGE && p.getUUID().equals(s.player)) {
                if (s.phase == Phase.ASK) { accept(s, p); return true; }
                if (s.phase == Phase.START) return true; // already starting
                if (s.phase == Phase.MATCH) return false; // PtcgDuels handles it as usual
                end(s); // APPROACH: the customer had not asked yet and the player wants the table for something else
            } else {
                p.displayClientMessage(Component.translatable(s.kind == Kind.EXHIBITION ? "tcgshop.table.busy_npc" : "tcgshop.table.reserved").withStyle(ChatFormatting.RED), true);
                return true;
            }
        }
        int need = chairsRequired();
        if (need > 0) {
            int have = chairs(w, table).size();
            if (have < need) {
                p.displayClientMessage(Component.translatable(stairsCount() ? "tcgshop.table.need_seats" : "tcgshop.table.need_chairs", need, radius(), have)
                        .withStyle(ChatFormatting.GOLD), false);
                p.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6f, 1f);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ scene bookkeeping

    @Nullable
    private static Scene sceneAt(ServerLevel w, BlockPos table) {
        for (Scene s : SCENES) if (s.world == w && s.table.equals(table)) return s;
        return null;
    }

    private static boolean inScene(CustomerEntity c) {
        for (Scene s : SCENES) for (CustomerEntity n : s.npc) if (n == c) return true;
        return false;
    }

    /** A customer free to be borrowed: a normal shopper, not queuing, dealing, seated, fighting or in an event. */
    private static boolean eligible(CustomerEntity c) {
        if (!c.isAlive() || c.isRemoved() || c.isPassenger() || c.hurtTime > 0) return false;
        CustomerEntity.Type t = c.type();
        if (t == CustomerEntity.Type.THIEF || t == CustomerEntity.Type.SELLER || t == CustomerEntity.Type.TRADER) return false;
        if (c.wantsAttention() || c.waitPermille() >= 0 || c.isFleeingThief()) return false;
        String role = c.roleKey();
        if (role == null || !(role.equals("tcgshop.role." + t.name().toLowerCase(Locale.ROOT)) || role.equals("tcgshop.role.regular_back")))
            return false; // an event actor (Jigglypuff, inspector...): its event drives it
        if (NpcPokemonDuel.busy(c) || CobblemonBattles.isFighting(c)) return false;
        return !ShopSeats.hasReservation(c.getUUID()) && !inScene(c);
    }

    private static boolean tableFree(ServerLevel w, BlockPos table) {
        return w.getBlockState(table).is(TcgBlocks.PLAY_TABLE) && sceneAt(w, table) == null && !PtcgDuels.tableInUse(table) && ready(w, table);
    }

    /** Chairs of that table a customer could sit on now (free, not reserved, with a spot next to them to sit down from). */
    private static List<ShopSeats.Pick> freeChairs(ServerLevel w, ShopZones.Zone z, BlockPos table, CustomerEntity who) {
        List<ShopSeats.Pick> out = new ArrayList<>();
        for (BlockPos ch : chairs(w, table)) {
            if (!z.contains(ch) || ShopSeats.reservedByOther(w, ch, who.getUUID()) || !ShopSeats.isFree(w, ch, who)) continue;
            ShopSeats.Seat seat = ShopSeats.seatFacing(w, ch, yawToward(ch, table));
            Vec3 stand = ShopSeats.standSpot(w, z, seat);
            if (stand != null) out.add(new ShopSeats.Pick(seat, stand));
        }
        return out;
    }

    private static float yawToward(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }

    private static void line(CustomerEntity c, String cat) {
        NpcLines.speak(c, Component.translatable("tcgshop.table." + cat + "." + c.getRandom().nextInt(LINES.getOrDefault(cat, 1))));
    }

    // ------------------------------------------------------------------ loop

    private static void tick(MinecraftServer server) {
        int t = server.getTickCount();
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
        for (ServerLevel w : server.getAllLevels()) {
            List<ServerPlayer> players = w.players();
            if (players.isEmpty()) continue;
            for (ShopZones.Zone z : ShopZones.get(w).all()) {
                if (z.closed || !w.hasChunkAt(z.core)) continue; // 1.1: no challenges or exhibitions in a closed shop
                List<ServerPlayer> near = new ArrayList<>();
                for (ServerPlayer p : players) if (!p.isSpectator() && z.box().inflate(16).contains(p.position())) near.add(p);
                if (near.isEmpty()) continue; // nobody would see it
                List<CustomerEntity> pool = w.getEntitiesOfClass(CustomerEntity.class, z.box(), cc -> z.contains(cc.blockPosition()) && eligible(cc));
                if (pool.isEmpty()) continue;
                List<BlockPos> tables = ShopSeats.tables(w, z);
                if (tables.isEmpty()) continue;
                RandomSource r = w.getRandom();
                // customer challenges
                if (chChance > 0) for (ServerPlayer p : near) {
                    if (!z.contains(p.blockPosition()) || PtcgDuels.busy(p.getUUID()) || challengeFor(p.getUUID()) != null) continue;
                    Integer last = LAST_CHALLENGE.get(p.getUUID());
                    if (last != null && server.getTickCount() - last < cooldown && server.getTickCount() >= last) continue;
                    if (r.nextDouble() >= chChance) continue;
                    BlockPos table = null;
                    for (BlockPos tb : tables)
                        if (tb.distToCenterSqr(p.position()) <= 8 * 8 && tableFree(w, tb) && (table == null || tb.distToCenterSqr(p.position()) < table.distToCenterSqr(p.position())))
                            table = tb;
                    if (table == null) continue;
                    CustomerEntity cu = null;
                    for (CustomerEntity cc : pool)
                        if (cc.distanceToSqr(p) <= 24 * 24 && (cu == null || cc.distanceToSqr(p) < cu.distanceToSqr(p))) cu = cc;
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

    private static boolean startExhibition(ServerLevel w, ShopZones.Zone z, BlockPos table, List<CustomerEntity> pool) {
        List<CustomerEntity> near = new ArrayList<>();
        for (CustomerEntity c : pool) if (c.distanceToSqr(table.getCenter()) <= 24 * 24) near.add(c);
        if (near.size() < 2) return false;
        near.sort(Comparator.comparingDouble(c -> c.distanceToSqr(table.getCenter())));
        CustomerEntity a = near.get(0), b = near.get(1);
        List<ShopSeats.Pick> chairs = freeChairs(w, z, table, a);
        if (chairs.size() < 2) return false;
        // the two chairs farthest apart: they sit facing each other across the table
        ShopSeats.Pick ca = null, cb = null;
        double best = -1;
        for (int i = 0; i < chairs.size(); i++)
            for (int j = i + 1; j < chairs.size(); j++) {
                double d = chairs.get(i).seat().pos().distSqr(chairs.get(j).seat().pos());
                if (d > best) { best = d; ca = chairs.get(i); cb = chairs.get(j); }
            }
        if (ca == null || !ShopSeats.reserve(w, ca.seat().pos(), a.getUUID())) return false;
        if (!ShopSeats.reserve(w, cb.seat().pos(), b.getUUID())) { ShopSeats.release(a.getUUID()); return false; }
        Scene s = new Scene(Kind.EXHIBITION, w, z.id, table, 2);
        s.npc[0] = a;
        s.npc[1] = b;
        s.chair[0] = ca.seat().pos();
        s.chair[1] = cb.seat().pos();
        s.stand[0] = ca.stand();
        s.stand[1] = cb.stand();
        s.phase = Phase.WALK;
        s.phaseAt = s.startedAt = w.getGameTime();
        for (int i = 0; i < 2; i++) {
            NpcPokemonDuel.hold(s.npc[i]);
            s.npc[i].getNavigation().moveTo(s.stand[i].x, s.stand[i].y, s.stand[i].z, 0.8);
        }
        SCENES.add(s);
        line(a, "invite");
        return true;
    }

    // ------------------------------------------------------------------ challenge (a customer challenges a player)

    private static void startChallenge(ServerLevel w, ShopZones.Zone z, BlockPos table, CustomerEntity c, ServerPlayer p) {
        Scene s = new Scene(Kind.CHALLENGE, w, z.id, table, 1);
        s.npc[0] = c;
        s.player = p.getUUID();
        s.phase = Phase.APPROACH;
        s.phaseAt = s.startedAt = w.getGameTime();
        double roll = w.getRandom().nextDouble();
        s.level = roll < 0.55 ? 0 : roll < 0.9 ? 1 : 2; // mostly easy and normal players, sometimes a strong one
        NpcPokemonDuel.hold(c);
        c.getNavigation().moveTo(p.getX(), p.getY(), p.getZ(), 0.8);
        LAST_CHALLENGE.put(p.getUUID(), w.getServer().getTickCount());
        SCENES.add(s);
        PtcgDuels.warmUp(); // the engine starts loading while the customer walks over
    }

    private static void ask(Scene s, ServerPlayer p) {
        CustomerEntity c = s.npc[0];
        s.phase = Phase.ASK;
        s.phaseAt = s.world.getGameTime();
        c.getNavigation().stop();
        line(c, "ask");
        Component accept = Component.translatable("tcgshop.table.challenge.accept").withStyle(st -> st.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgtable accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("tcgshop.table.challenge.accept_hint"))));
        Component decline = Component.translatable("tcgshop.table.challenge.decline").withStyle(st -> st.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgtable decline")));
        p.displayClientMessage(Component.translatable("tcgshop.table.challenge.received", c.getDisplayName(),
                Component.translatable("tcgshop.table.level." + s.level)).withStyle(ChatFormatting.GOLD), false);
        p.displayClientMessage(Component.literal("   ").append(accept).append(Component.literal("   ")).append(decline), false);
        p.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** /tcgtable accept|decline. */
    private static void answer(ServerPlayer p, boolean yes) {
        Scene s = challengeFor(p.getUUID());
        if (s == null || s.phase != Phase.ASK) {
            p.displayClientMessage(Component.translatable("tcgshop.table.challenge.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (yes) accept(s, p);
        else {
            line(s.npc[0], "no");
            end(s);
        }
    }

    private static void accept(Scene s, ServerPlayer p) {
        CustomerEntity c = s.npc[0];
        if (p.serverLevel() != s.world || PtcgDuels.busy(p.getUUID())) {
            p.displayClientMessage(Component.translatable("tcgshop.ptcg.challenge.busy_self").withStyle(ChatFormatting.RED), true);
            return;
        }
        s.phase = Phase.START;
        s.phaseAt = s.world.getGameTime();
        line(c, "yes");
        // the customer takes a chair at the table (if none is free it just stands next to it)
        ShopZones.Zone z = ShopZones.get(s.world).byId(s.zone);
        if (z != null) {
            for (ShopSeats.Pick pick : freeChairs(s.world, z, s.table, c)) {
                if (!ShopSeats.reserve(s.world, pick.seat().pos(), c.getUUID())) continue;
                s.chair[0] = pick.seat().pos();
                s.stand[0] = pick.stand();
                break;
            }
        }
        Vec3 go = s.stand[0] != null ? s.stand[0] : s.table.getCenter();
        c.getNavigation().moveTo(go.x, go.y, go.z, 0.8);
        tryStart(s, p);
    }

    private static void tryStart(Scene s, ServerPlayer p) {
        s.lastStartTry = s.world.getGameTime();
        if (PtcgDuels.startNpcMatch(p, s.table, s.npc[0].getName().getString(), s.level) && s.firstStartOk < 0) s.firstStartOk = s.world.getGameTime();
    }

    // ------------------------------------------------------------------ per-scene update (every 5 ticks)

    private static void update(Scene s) {
        ServerLevel w = s.world;
        long now = w.getGameTime();
        s.age += 5;
        for (CustomerEntity c : s.npc) if (c == null || c.isRemoved() || !c.isAlive() || c.level() != w) { end(s); return; }
        if (!w.getBlockState(s.table).is(TcgBlocks.PLAY_TABLE)) { end(s); return; }
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
                    w.playSound(null, s.table, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.1f);
                } else if (now - s.phaseAt > 20 * 20) end(s); // could not reach the chairs
            }
            case PLAY -> {
                for (int i = 0; i < s.npc.length; i++) {
                    if (!(s.npc[i].getVehicle() instanceof ShopSeatEntity)) { end(s); return; } // the chair was removed
                    Vec3 c = s.table.getCenter();
                    s.npc[i].getLookControl().setLookAt(c.x, c.y + 0.4, c.z);
                }
                if (now >= s.endAt) { finishExhibition(s); return; }
                if (s.age % 60 == 0) playBeat(s);
            }
            case APPROACH -> {
                ServerPlayer p = challenged(s);
                if (p == null) { end(s); return; }
                CustomerEntity c = s.npc[0];
                if (c.distanceToSqr(p) < 3 * 3) { ask(s, p); return; }
                if (c.getNavigation().isDone() || s.age % 20 == 0) c.getNavigation().moveTo(p.getX(), p.getY(), p.getZ(), 0.8);
                if (now - s.phaseAt > 20 * 20) end(s); // could not reach the player: quietly gives up
            }
            case ASK -> {
                ServerPlayer p = challenged(s);
                if (p == null) { end(s); return; }
                CustomerEntity c = s.npc[0];
                c.getLookControl().setLookAt(p, 30f, 30f);
                if (now - s.phaseAt > 30 * 20) {
                    p.displayClientMessage(Component.translatable("tcgshop.table.challenge.expired", c.getDisplayName()).withStyle(ChatFormatting.GRAY), false);
                    line(c, "no");
                    end(s);
                }
            }
            case START, MATCH -> {
                ServerPlayer p = s.world.getServer().getPlayerList().getPlayer(s.player);
                if (s.chair[0] != null) walkAndSit(s, 0);
                else if (s.npc[0].distanceToSqr(s.table.getCenter()) < 2.5 * 2.5) s.npc[0].getNavigation().stop();
                Vec3 tc = s.table.getCenter();
                if (s.npc[0].isPassenger()) s.npc[0].getLookControl().setLookAt(tc.x, tc.y + 0.4, tc.z);
                if (s.phase == Phase.START) {
                    if (PtcgDuels.tableMatchRunning(s.table) && p != null && PtcgDuels.busy(p.getUUID())) {
                        s.phase = Phase.MATCH;
                        s.phaseAt = now;
                        return;
                    }
                    if (p == null || p.serverLevel() != w) { end(s); return; }
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
    private static ServerPlayer challenged(Scene s) {
        ServerPlayer p = s.player == null ? null : s.world.getServer().getPlayerList().getPlayer(s.player);
        if (p == null || p.serverLevel() != s.world || p.isSpectator() || PtcgDuels.busy(p.getUUID())) return null;
        if (p.distanceToSqr(s.table.getCenter()) > 24 * 24) return null; // walked away
        return p;
    }

    private static void failStart(Scene s, ServerPlayer p) {
        p.displayClientMessage(Component.translatable("tcgshop.table.challenge.failed").withStyle(ChatFormatting.GRAY), false);
        line(s.npc[0], "no");
        end(s);
    }

    /** Walks NPC i to its chair and sits it down facing the table. true = seated. */
    private static boolean walkAndSit(Scene s, int i) {
        CustomerEntity c = s.npc[i];
        if (c.getVehicle() instanceof ShopSeatEntity) return true;
        BlockPos ch = s.chair[i];
        Vec3 st = s.stand[i];
        if (ch == null || st == null) return false;
        ServerLevel w = s.world;
        if (!ShopSeats.stillSeat(w, ch)) return false;
        Vec3 center = ch.getCenter();
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
        if (c.getNavigation().isDone() || s.age % 40 == 0) c.getNavigation().moveTo(st.x, st.y, st.z, 0.8);
        return false;
    }

    // ------------------------------------------------------------------ exhibition details

    private static List<String> cardIds() {
        if (cardIds == null || cardIds.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
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
        RandomSource r = s.world.getRandom();
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
        RandomSource r = s.world.getRandom();
        CustomerEntity c = s.npc[r.nextInt(s.npc.length)];
        c.swing(InteractionHand.MAIN_HAND);
        Vec3 t = s.table.getCenter();
        s.world.playSound(null, s.table, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.7f, 0.9f + r.nextFloat() * 0.3f);
        if (r.nextFloat() < 0.6f) dealBoard(s, false);
        if (r.nextFloat() < 0.35f) s.world.sendParticles(ParticleTypes.ENCHANT, t.x, t.y + 0.5, t.z, 6, 0.3, 0.1, 0.3, 0.4);
        if (r.nextFloat() < 0.18f) line(c, "play");
    }

    private static void finishExhibition(Scene s) {
        RandomSource r = s.world.getRandom();
        int win = r.nextInt(2);
        CustomerEntity w = s.npc[win], l = s.npc[1 - win];
        line(w, "win");
        if (r.nextFloat() < 0.7f) line(l, "lose");
        s.world.sendParticles(ParticleTypes.HAPPY_VILLAGER, w.getX(), w.getY() + 1.6, w.getZ(), 6, 0.3, 0.3, 0.3, 0);
        s.world.playSound(null, s.table, SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.4f, 1.6f);
        end(s);
    }

    // ------------------------------------------------------------------ ending

    /** Ends a scene: everyone stands up next to their chair, frees it and gets their own AI back. */
    private static void end(Scene s) {
        SCENES.remove(s);
        for (CustomerEntity c : s.npc) {
            if (c == null) continue;
            if (c.getVehicle() instanceof ShopSeatEntity seat) {
                Vec3 st = seat.standPos();
                c.stopRiding();
                seat.discard();
                if (st != null && !c.isRemoved()) c.teleportTo(st.x, st.y, st.z);
            }
            ShopSeats.release(c.getUUID());
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
