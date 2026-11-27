package dev.tcgshop.compat;

import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.TcgShop;
import dev.tcgshop.npc.NpcConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.1: a REAL Cobblemon trainer battle against the Mayor (npc/TaxOffice), all via reflection (no compile-time dependency).
 *
 * Cobblemon's BattleBuilder.pvn needs one of Cobblemon's own NPC entities, so the battle is put together the same way
 * pvn does it, with a trainer actor that has no entity:
 * <pre>
 *   player side : PlayerBattleActor(player uuid, party.toBattleTeam(false, false, null))
 *   Mayor side  : TrainerBattleActor(name, mayor uuid, [BattlePokemon.safeCopyOf(p) for his 6 Pokémon], StrongBattleAI(skill))
 *   BattleRegistry.startBattle(BattleFormat.GEN_9_SINGLES, BattleSide(player), BattleSide(mayor), false)
 * </pre>
 * His Pokémon are created with PokemonProperties ("garchomp level=60 nature=jolly ability=roughskin
 * held_item=cobblemon:life_orb moves=... min_perfect_ivs=6") plus 252/252/4 EVs; a set Cobblemon cannot parse falls
 * back to the plain species at that level, so one unknown move or item never stops the battle.
 *
 * Outcome, checked every 10 ticks: the battle ended with the player among the winners = WIN. Anything else is a LOSS:
 * the player lost or forfeited, fled, disconnected (Cobblemon ends the battle), walked more than mayorWalkAwayBlocks
 * from the Mayor (we stop the battle), changed dimension, or the battle ran for over 30 minutes. A server restart
 * mid-battle loses the in-memory session; TaxOffice then resolves the saved "dueling" state as a loss.
 */
public final class MayorBattle {
    private MayorBattle() {}

    public enum Result { WIN, LOSS }

    /** Called once per battle when it is over (reason = lang key suffix: lost, walked, timeout, gone). */
    public interface End {
        void onEnd(MinecraftServer server, UUID player, Result r, String reason);
    }

    /** team: the player's BattlePokemon (to tell a forfeit / flight, with Pokémon still standing, from a real loss). */
    private record Session(UUID player, UUID mayor, Object battle, @Nullable UUID battleId, long start, End end, @Nullable Object team) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static boolean warned;
    /** Longest battle before it counts as walking away (30 min). */
    static final long MAX_TICKS = 20L * 60 * 30;

    public static final String ERR_NO_POKEMON = "no_pokemon", ERR_BUSY = "busy", ERR_FAILED = "failed";

    public static void register() {
        ServerEvents.TICK_END.register(MayorBattle::tick);
        ServerEvents.STOPPING.register(s -> SESSIONS.clear());
    }

    public static boolean active(UUID player) {
        return SESSIONS.containsKey(player);
    }

    // ------------------------------------------------------------------ the Mayor's teams

    /** Three competitive teams of six (Showdown ids). One is picked at random for each battle. */
    static final String[][] TEAMS = {
            {"garchomp nature=jolly ability=roughskin held_item=cobblemon:life_orb moves=swordsdance,earthquake,dragonclaw,stoneedge|atk,spe",
                    "metagross nature=adamant ability=clearbody held_item=cobblemon:assault_vest moves=meteormash,zenheadbutt,earthquake,bulletpunch|atk,hp",
                    "gyarados nature=adamant ability=intimidate held_item=cobblemon:leftovers moves=dragondance,waterfall,earthquake,icefang|atk,spe",
                    "volcarona nature=timid ability=flamebody held_item=cobblemon:leftovers moves=quiverdance,flamethrower,bugbuzz,gigadrain|spa,spe",
                    "toxapex nature=bold ability=regenerator held_item=cobblemon:black_sludge moves=scald,toxic,recover,haze|hp,def",
                    "clefable nature=calm ability=magicguard held_item=cobblemon:leftovers moves=moonblast,flamethrower,softboiled,calmmind|hp,spd"},
            {"tyranitar nature=adamant ability=sandstream held_item=cobblemon:choice_band moves=stoneedge,crunch,earthquake,icepunch|atk,hp",
                    "excadrill nature=jolly ability=sandrush held_item=cobblemon:focus_sash moves=earthquake,ironhead,rockslide,swordsdance|atk,spe",
                    "corviknight nature=impish ability=mirrorarmor held_item=cobblemon:leftovers moves=bravebird,bodypress,roost,bulkup|hp,def",
                    "dragonite nature=adamant ability=multiscale held_item=cobblemon:choice_band moves=extremespeed,dragonclaw,earthquake,firepunch|atk,spe",
                    "gengar nature=timid ability=cursedbody held_item=cobblemon:choice_specs moves=shadowball,sludgebomb,focusblast,thunderbolt|spa,spe",
                    "azumarill nature=adamant ability=hugepower held_item=cobblemon:choice_band moves=aquajet,playrough,waterfall,superpower|atk,hp"},
            {"scizor nature=adamant ability=technician held_item=cobblemon:choice_band moves=bulletpunch,uturn,knockoff,superpower|atk,hp",
                    "hydreigon nature=timid ability=levitate held_item=cobblemon:choice_specs moves=dracometeor,darkpulse,flamethrower,earthpower|spa,spe",
                    "lucario nature=timid ability=justified held_item=cobblemon:life_orb moves=aurasphere,flashcannon,nastyplot,vacuumwave|spa,spe",
                    "togekiss nature=timid ability=serenegrace held_item=cobblemon:leftovers moves=airslash,dazzlinggleam,roost,nastyplot|spa,spe",
                    "ferrothorn nature=relaxed ability=ironbarbs held_item=cobblemon:leftovers moves=gyroball,powerwhip,leechseed,stealthrock|hp,def",
                    "salamence nature=naive ability=intimidate held_item=cobblemon:life_orb moves=dragondance,outrage,earthquake,fireblast|atk,spe"}};

    /** Species of every team (the fallback chain of wild battles uses them too). */
    public static String[] allSpecies() {
        List<String> out = new ArrayList<>();
        for (String[] t : TEAMS) for (String s : t) out.add(s.substring(0, s.indexOf(' ')));
        return out.toArray(new String[0]);
    }

    /** Level of the Mayor's team for this player: best party level + mayorTeamLevelBonus, clamped (config). */
    public static int teamLevel(int bestPartyLevel) {
        NpcConfig c = NpcConfig.INSTANCE;
        return Math.max(c.mayorTeamMinLevel, Math.min(c.mayorTeamMaxLevel, bestPartyLevel + c.mayorTeamLevelBonus));
    }

    /** Best level in the player's party (0 if it cannot be read). */
    public static int bestPartyLevel(ServerPlayer p) {
        int best = 0;
        for (CobblemonParty.Mon m : CobblemonParty.list(p)) best = Math.max(best, m.level());
        return best;
    }

    /** The PokemonProperties strings of one team at that level (pure; the selftest checks them). */
    public static List<String> teamSpecs(int team, int level) {
        List<String> out = new ArrayList<>();
        for (String s : TEAMS[Math.floorMod(team, TEAMS.length)]) {
            String props = s.substring(0, s.indexOf('|'));
            int sp = props.indexOf(' ');
            out.add(props.substring(0, sp) + " level=" + level + " min_perfect_ivs=6 uncatchable=yes" + props.substring(sp));
        }
        return out;
    }

    // ------------------------------------------------------------------ start

    /**
     * Starts the battle. Returns null when it started, otherwise ERR_NO_POKEMON (no Pokémon able to fight), ERR_BUSY
     * (already in a battle) or ERR_FAILED (Cobblemon refused it or its API changed).
     */
    @Nullable
    public static String start(ServerPlayer p, Entity mayor, String trainerName, End end) {
        if (!CobblemonBattles.available()) return ERR_FAILED;
        if (SESSIONS.containsKey(p.getUUID())) return ERR_BUSY;
        try {
            Class<?> ext = Class.forName("com.cobblemon.mod.common.util.PlayerExtensionsKt");
            Object party = ext.getMethod("party", ServerPlayer.class).invoke(null, p);
            int usable = 0;
            for (Object pk : (Iterable<?>) party) if (pk != null && !(boolean) pk.getClass().getMethod("isFainted").invoke(pk)) usable++;
            if (usable == 0) return ERR_NO_POKEMON;
            Class<?> registry = Class.forName("com.cobblemon.mod.common.battles.BattleRegistry");
            Object inBattle = registry.getMethod("getBattleByParticipatingPlayer", ServerPlayer.class).invoke(null, p);
            if (inBattle != null) return ERR_BUSY;

            Object team = party.getClass().getMethod("toBattleTeam", boolean.class, boolean.class, UUID.class).invoke(party, false, false, null);
            Class<?> playerActorC = Class.forName("com.cobblemon.mod.common.battles.actor.PlayerBattleActor");
            Object playerActor = playerActorC.getConstructor(UUID.class, List.class).newInstance(p.getUUID(), team);

            List<Object> mayorTeam = buildTeam(p.getRandom(), teamLevel(bestPartyLevel(p)));
            if (mayorTeam.isEmpty()) return ERR_FAILED;
            Class<?> aiI = Class.forName("com.cobblemon.mod.common.api.battles.model.ai.BattleAI");
            Object ai = Class.forName("com.cobblemon.mod.common.battles.ai.StrongBattleAI").getConstructor(int.class).newInstance(NpcConfig.INSTANCE.mayorAiSkill);
            Class<?> trainerC = Class.forName("com.cobblemon.mod.common.battles.actor.TrainerBattleActor");
            Object mayorActor = trainerC.getConstructor(String.class, UUID.class, List.class, aiI).newInstance(trainerName, mayor.getUUID(), mayorTeam, ai);

            Class<?> actorC = Class.forName("com.cobblemon.mod.common.api.battles.model.actor.BattleActor");
            Class<?> sideC = Class.forName("com.cobblemon.mod.common.battles.BattleSide");
            Object side1 = side(sideC, actorC, playerActor), side2 = side(sideC, actorC, mayorActor);
            Class<?> formatC = Class.forName("com.cobblemon.mod.common.battles.BattleFormat");
            Object fc = formatC.getField("Companion").get(null);
            Object format = fc.getClass().getMethod("getGEN_9_SINGLES").invoke(fc);
            Method startM = registry.getMethod("startBattle", formatC, sideC, sideC, boolean.class);
            Object result = startM.invoke(null, format, side1, side2, false);
            if (result == null || !result.getClass().getSimpleName().equals("SuccessfulBattleStart")) {
                TcgShop.LOGGER.warn("[mayor] Cobblemon refused the battle: {}", result);
                return ERR_FAILED;
            }
            Object battle = result.getClass().getMethod("getBattle").invoke(result);
            UUID id = null;
            try { id = (UUID) battle.getClass().getMethod("getBattleId").invoke(battle); } catch (Throwable ignored) {}
            SESSIONS.put(p.getUUID(), new Session(p.getUUID(), mayor.getUUID(), battle, id, p.serverLevel().getGameTime(), end, team));
            TcgShop.LOGGER.info("[mayor] {} battles the Mayor ({} Pokémon, level {})", p.getGameProfile().getName(), mayorTeam.size(), teamLevel(bestPartyLevel(p)));
            return null;
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                TcgShop.LOGGER.warn("Could not start the Mayor's Cobblemon battle: {}", e.toString());
            }
            return ERR_FAILED;
        }
    }

    private static Object side(Class<?> sideC, Class<?> actorC, Object actor) throws Exception {
        Object arr = Array.newInstance(actorC, 1);
        Array.set(arr, 0, actor);
        return sideC.getConstructor(arr.getClass()).newInstance(arr);
    }

    /** Six BattlePokemon of a random team (each set falls back to the bare species if Cobblemon can't parse it). */
    private static List<Object> buildTeam(RandomSource r, int level) throws Exception {
        Class<?> propsC = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
        Object companion = propsC.getField("Companion").get(null);
        Method parse = companion.getClass().getMethod("parse", String.class);
        Method create = propsC.getMethod("create");
        Class<?> bpC = Class.forName("com.cobblemon.mod.common.battles.pokemon.BattlePokemon");
        Object bpComp = bpC.getField("Companion").get(null);
        Method safeCopy = null;
        for (Method m : bpComp.getClass().getMethods()) if (m.getName().equals("safeCopyOf") && m.getParameterCount() == 1) safeCopy = m;
        if (safeCopy == null) return List.of();
        int t = r.nextInt(TEAMS.length);
        List<String> specs = teamSpecs(t, level);
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            String spec = specs.get(i), raw = TEAMS[t][i];
            Object pk;
            try {
                pk = create.invoke(parse.invoke(companion, spec));
            } catch (Throwable e) {
                pk = null;
            }
            if (pk == null) {
                try {
                    pk = create.invoke(parse.invoke(companion, spec.substring(0, spec.indexOf(' ')) + " level=" + level + " uncatchable=yes"));
                } catch (Throwable e) {
                    continue;
                }
            }
            evs(pk, raw.substring(raw.indexOf('|') + 1).split(","));
            try { pk.getClass().getMethod("heal").invoke(pk); } catch (Throwable ignored) {}
            out.add(safeCopy.invoke(bpComp, pk));
        }
        return out;
    }

    /** 252 EVs in the two listed stats and 4 in HP (or Speed if HP is one of them). Best effort. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void evs(Object pk, String[] stats) {
        try {
            Class statsC = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stats");
            Class<?> statI = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stat");
            Method set = pk.getClass().getMethod("setEV", statI, int.class);
            List<String> names = new ArrayList<>();
            for (String s : stats) names.add(switch (s.trim()) {
                case "hp" -> "HP";
                case "atk" -> "ATTACK";
                case "def" -> "DEFENCE";
                case "spa" -> "SPECIAL_ATTACK";
                case "spd" -> "SPECIAL_DEFENCE";
                default -> "SPEED";
            });
            for (String n : names) set.invoke(pk, Enum.valueOf(statsC, n), 252);
            set.invoke(pk, Enum.valueOf(statsC, names.contains("HP") ? "SPEED" : "HP"), 4);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ outcome

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty() || server.getTickCount() % 10 != 3) return;
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        List<Runnable> done = new ArrayList<>();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(s.player);
            boolean ended = bool(s.battle, "getEnded");
            boolean live = !ended && alive(s);
            if (live) {
                Entity m = find(server, s.mayor);
                long age = server.overworld().getGameTime() - s.start;
                String why = null;
                if (p == null) why = "gone";
                else if (m == null || m.level() != p.level() || m.distanceToSqr(p) > sq(NpcConfig.INSTANCE.mayorWalkAwayBlocks)) why = "walked";
                else if (age > MAX_TICKS || age < 0) why = "timeout";
                if (why == null) continue;
                stop(s.battle);
                String fw = why;
                it.remove();
                done.add(() -> s.end.onEnd(server, s.player, Result.LOSS, fw));
                continue;
            }
            boolean won = playerWon(s.battle, s.player);
            // lost with Pokémon still able to fight: the player forfeited or fled, it was no knock-out
            String reason = !won && anyStanding(s.team) ? "forfeit" : "lost";
            it.remove();
            done.add(() -> s.end.onEnd(server, s.player, won ? Result.WIN : Result.LOSS, reason));
        }
        for (Runnable r : done) {
            try { r.run(); } catch (Throwable t) { TcgShop.LOGGER.warn("[mayor] battle end: {}", t.toString()); }
        }
    }

    /** Is the battle still in Cobblemon's registry? (If the id can't be read, assume yes while it hasn't ended.) */
    private static boolean alive(Session s) {
        if (s.battleId == null) return true;
        try {
            Class<?> registry = Class.forName("com.cobblemon.mod.common.battles.BattleRegistry");
            return registry.getMethod("getBattle", UUID.class).invoke(null, s.battleId) != null;
        } catch (Throwable e) {
            return true;
        }
    }

    /** Does any of these BattlePokemon still have HP? (false if it cannot be read) */
    static boolean anyStanding(@Nullable Object team) {
        if (!(team instanceof Iterable<?> list)) return false;
        try {
            for (Object bp : list) {
                if (bp == null) continue;
                Object hp = bp.getClass().getMethod("getHealth").invoke(bp);
                if (hp instanceof Number n && n.intValue() > 0) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean playerWon(Object battle, UUID player) {
        try {
            Object winners = battle.getClass().getMethod("getWinners").invoke(battle);
            for (Object a : (Iterable<?>) winners) {
                Object id = a.getClass().getMethod("getUuid").invoke(a);
                if (player.equals(id)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void stop(Object battle) {
        try { battle.getClass().getMethod("stop").invoke(battle); } catch (Throwable e) { TcgShop.LOGGER.warn("[mayor] could not stop the battle: {}", e.toString()); }
    }

    private static boolean bool(Object o, String m) {
        try { return (boolean) o.getClass().getMethod(m).invoke(o); } catch (Throwable e) { return false; }
    }

    @Nullable
    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel w : server.getAllLevels()) {
            Entity e = w.getEntity(id);
            if (e != null && e.isAlive()) return e;
        }
        return null;
    }

    private static double sq(double v) { return v * v; }
}
