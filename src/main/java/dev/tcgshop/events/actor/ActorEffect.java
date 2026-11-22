package dev.tcgshop.events.actor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tcgshop.compat.CobblemonActors;
import dev.tcgshop.compat.CobblemonParty;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventInstance;
import dev.tcgshop.events.EventManager;
import dev.tcgshop.events.EventRegistry;
import dev.tcgshop.events.EventState;
import dev.tcgshop.events.Mods;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopLife;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Efecto "actor": uno o varios Pokémon salvajes REALES de Cobblemon que visitan la tienda durante el evento.
 * Ver EFFECTS.md (sección 8). Campos del JSON (todos opcionales salvo species):
 * <pre>
 * species         "psyduck" o ["growlithe","vulpix"] (una al azar entre las implementadas en este Cobblemon)
 * speciesByBiome  [{"biomes":["#minecraft:is_taiga"],"species":["lucario"]}, {"species":["growlithe"]}] (la primera que encaje)
 * level [5,15]    nunca por encima de la media del equipo del dueño ni de ActorConfig.maxLevel
 * count 1|[1,3]   topes: ActorConfig.maxPerShop (3) y maxGlobal (12)
 * behaviour       stay | wander | goto_door | goto_display | goto_register | goto_light | outside (fuera de la puerta pero
 *                 DENTRO de la zona) | block_door (junto a la puerta, nunca en el hueco ni delante, y cierra esa puerta)
 * resolve         ["shoo","feed","battle","expire"]: lo que ARREGLA el evento (shoo = mano vacía ×shooHits; feed =
 *                 cualquier cobblemon:*_berry, gasta 1; battle = agachado + clic derecho, sólo el dueño)
 * shooHits 3      feed/pet (bool): dar de comer / acariciar sin arreglar nada (positivos); petLeaves: se teletransporta al acariciarlo
 * closeDoor, registerDown, lighting (0-15), disable ("display"|"slot"|"slots") + disableCount: bloqueos mientras esté
 * reward          {"onFeed"|"onPet"|"onShoo"|"onBattle": {"money":[a,b], "charity":n, "loot":"tcgshop:events/x", "rolls":1}}
 *                 una vez por tipo y evento, con el tope de dinero por dueño
 * theft           {"maxValue":600, "guardSeconds":[30,60]} robo VIRTUAL (sólo con goto_display)
 * serviceFlicker  [0.6,1.5] serviceMult cambiante (Rotom); resolveEvents ["#plantilla","ns:id"]: arregla esos eventos activos
 * leaveCustomers  {"chance":0.3, "every":1200, "reviewKey":"ev_noise"} (necesita CustomerEntity.leaveForEvent)
 * particles       splash|drip|spark|note|heart|smoke|flame|leaf|cherry|angry|glow|wax|happy|cloud|dust|nectar|spore|portal|bubble
 * cry true, endWhenGone true (si se van todos sin arreglarlo, termina el evento), resolveEvent "auto"|"never",
 * reviewTag       tag de reseña de la instancia (sólo si el Pokémon apareció de verdad)
 * </pre>
 *
 * Reglas (crítica B/G7): siempre no capturables (uncatchable=yes + cancelar la Poké Ball), invulnerables, no
 * combatibles salvo el combate del dueño (nivel limitado a la media de su equipo), etiquetas EVENT_TAG +
 * "tcgshop_ev_&lt;uid&gt;" + {@link ActorModule#ACTOR_TAG}, registrados en EventState ANTES de spawnEntity, nunca se guardan
 * en el mundo (POKEMON_ENTITY_SAVE_TO_WORLD cancelado), nunca reaparecen (si falta uno, ese paso termina), topes de
 * 3 por tienda y 12 en total (ActorConfig), se buscan por UUID en cada tick (nunca se guarda la entidad).
 *
 * Estado por instancia en ctx.data():
 *  a      lista de actores {u uuid, g "" | cómo se fue, sh espantadas, lc último clic, ph fase, t/tx/ty/tz destino,
 *         nc próximo grito, ms ticks sin encontrarlo, wt próximo paseo, sp especie}
 *  fl     suelos candidatos (long[]), door/dout puerta y su lado de fuera, disp/dslot expositor objetivo
 *  lk_*   bloqueos que puso ESTE efecto (para quitarlos si los actores se van y el evento sigue)
 *  tv/ts  robo virtual (valor y si ya "robó"), ga momento en que el guardia lo recupera
 *  pend   premios pendientes (feed/pet/shoo/battle) que da el tick; rw_* ya dados
 */
public final class ActorEffect implements EventEffect {
    enum Behaviour { STAY, WANDER, GOTO_DOOR, GOTO_DISPLAY, GOTO_REGISTER, GOTO_LIGHT, OUTSIDE, BLOCK_DOOR }

    record SpeciesRule(List<String> biomes, List<String> species) {}

    record Reward(long moneyMin, long moneyMax, int charity, String loot, int rolls) {}

    final String defId;
    final List<String> species = new ArrayList<>();
    final List<SpeciesRule> byBiome = new ArrayList<>();
    final int levelMin, levelMax, countMin, countMax;
    final Behaviour behaviour;
    final Set<String> resolve = new HashSet<>();
    final int shooHits;
    final boolean feed, pet, petLeaves, closeDoor, registerDown, endWhenGone, cry;
    final String disable, particles, resolveEvent;
    final int disableMin, disableMax, lighting;
    final Map<String, Reward> rewards = new LinkedHashMap<>();
    final long theftMax;
    final int guardMin, guardMax;
    final boolean theft;
    final double flickerMin, flickerMax;
    final List<String> resolveEvents = new ArrayList<>();
    final double leaveChance;
    final int leaveEvery;
    final String leaveKey, reviewTag;

    public ActorEffect(JsonObject o, String defId) {
        this.defId = defId;
        JsonElement sp = o.get("species");
        if (sp != null && sp.isJsonArray()) for (JsonElement e : sp.getAsJsonArray()) species.add(CobblemonActors.normalize(e.getAsString()));
        else if (sp != null) species.add(CobblemonActors.normalize(sp.getAsString()));
        if (o.has("speciesByBiome")) {
            for (JsonElement e : o.getAsJsonArray("speciesByBiome")) {
                JsonObject r = e.getAsJsonObject();
                List<String> bl = strings(r, "biomes"), sl = new ArrayList<>();
                for (String s : strings(r, "species")) sl.add(CobblemonActors.normalize(s));
                if (!sl.isEmpty()) byBiome.add(new SpeciesRule(bl, sl));
            }
        }
        if (species.isEmpty() && byBiome.isEmpty()) throw new IllegalArgumentException("actor sin species");
        long[] lv = EventDef.range(o.get("level"), 10);
        levelMin = (int) Math.max(1, Math.min(100, lv[0]));
        levelMax = (int) Math.max(levelMin, Math.min(100, lv[1]));
        long[] ct = EventDef.range(o.get("count"), 1);
        countMin = (int) Math.max(1, Math.min(6, ct[0]));
        countMax = (int) Math.max(countMin, Math.min(6, ct[1]));
        String b = EventDef.str(o, "behaviour", "wander").toUpperCase(Locale.ROOT);
        try {
            behaviour = Behaviour.valueOf(b);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("actor.behaviour inválido: " + b.toLowerCase(Locale.ROOT)
                    + " (stay|wander|goto_door|goto_display|goto_register|goto_light|outside|block_door)");
        }
        for (String r : strings(o, "resolve")) {
            String x = r.toLowerCase(Locale.ROOT);
            if (!List.of("shoo", "feed", "battle", "expire").contains(x)) throw new IllegalArgumentException("actor.resolve inválido: " + r);
            resolve.add(x);
        }
        shooHits = Math.max(1, Math.min(20, EventDef.integer(o, "shooHits", 3)));
        feed = EventDef.bool(o, "feed", resolve.contains("feed"));
        pet = EventDef.bool(o, "pet", false);
        petLeaves = EventDef.bool(o, "petLeaves", false);
        closeDoor = EventDef.bool(o, "closeDoor", behaviour == Behaviour.BLOCK_DOOR);
        registerDown = EventDef.bool(o, "registerDown", false);
        lighting = Math.max(-1, Math.min(15, EventDef.integer(o, "lighting", -1)));
        disable = EventDef.str(o, "disable", "").toLowerCase(Locale.ROOT);
        if (!List.of("", "display", "slot", "slots").contains(disable)) throw new IllegalArgumentException("actor.disable inválido: " + disable);
        long[] dc = EventDef.range(o.get("disableCount"), 1);
        disableMin = (int) Math.max(1, Math.min(9, dc[0]));
        disableMax = (int) Math.max(disableMin, Math.min(9, dc[1]));
        particles = EventDef.str(o, "particles", "").toLowerCase(Locale.ROOT);
        cry = EventDef.bool(o, "cry", true);
        endWhenGone = EventDef.bool(o, "endWhenGone", true);
        resolveEvent = EventDef.str(o, "resolveEvent", "auto").toLowerCase(Locale.ROOT);
        if (!List.of("auto", "never").contains(resolveEvent)) throw new IllegalArgumentException("actor.resolveEvent inválido: " + resolveEvent);
        if (o.has("reward")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("reward").entrySet()) {
                String k = e.getKey().toLowerCase(Locale.ROOT).replace("on", "");
                if (!List.of("feed", "pet", "shoo", "battle").contains(k)) throw new IllegalArgumentException("actor.reward: clave inválida " + e.getKey());
                JsonObject r = e.getValue().getAsJsonObject();
                long[] m = EventDef.range(r.get("money"), 0);
                String loot = EventDef.str(r, "loot", "");
                if (!loot.isEmpty()) {
                    Identifier id = Identifier.tryParse(loot);
                    if (id == null || !id.getNamespace().equals("tcgshop") || !id.getPath().startsWith("events/"))
                        throw new IllegalArgumentException("actor.reward.loot sólo puede usar tablas tcgshop:events/*");
                }
                rewards.put(k, new Reward(Math.max(0, m[0]), Math.max(Math.max(0, m[0]), m[1]),
                        Math.max(-3, Math.min(3, EventDef.integer(r, "charity", 0))), loot, Math.max(1, Math.min(3, EventDef.integer(r, "rolls", 1)))));
            }
        }
        JsonObject th = o.has("theft") ? o.getAsJsonObject("theft") : null;
        theft = th != null;
        theftMax = th == null ? 0 : Math.max(0, EventDef.integer(th, "maxValue", 600));
        long[] gs = th == null ? new long[]{30, 60} : EventDef.range(th.get("guardSeconds"), 45);
        guardMin = (int) Math.max(5, gs[0]);
        guardMax = (int) Math.max(guardMin, gs[1]);
        if (o.has("serviceFlicker")) {
            JsonArray a = o.getAsJsonArray("serviceFlicker");
            flickerMin = EventDef.clamp(a.get(0).getAsDouble(), 0.5, 2);
            flickerMax = EventDef.clamp(a.size() > 1 ? a.get(1).getAsDouble() : flickerMin, flickerMin, 2);
        } else flickerMin = flickerMax = 0;
        for (String s : strings(o, "resolveEvents")) resolveEvents.add(s);
        JsonObject lc = o.has("leaveCustomers") ? o.getAsJsonObject("leaveCustomers") : null;
        leaveChance = lc == null ? 0 : EventDef.clamp(EventDef.dbl(lc, "chance", 0.3), 0, 1);
        leaveEvery = lc == null ? 0 : Math.max(200, EventDef.integer(lc, "every", 1200));
        leaveKey = lc == null ? "ev_noise" : EventDef.str(lc, "reviewKey", "ev_noise");
        reviewTag = EventDef.str(o, "reviewTag", "");
        if (theft && behaviour != Behaviour.GOTO_DISPLAY) throw new IllegalArgumentException("actor.theft necesita behaviour goto_display");
    }

    private static List<String> strings(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (!o.has(k)) return out;
        JsonElement e = o.get(k);
        if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        else out.add(e.getAsString());
        return out;
    }

    // ------------------------------------------------------------------ contrato

    /** Formas de arreglarlo que dependen del jugador (expire no cuenta). */
    boolean fixWays() {
        return resolve.contains("shoo") || resolve.contains("feed") || resolve.contains("battle");
    }

    @Override
    public boolean providesFix() { return fixWays(); }

    @Override
    public Set<String> implicitRequires() { return Set.of("cobblemon", "actors"); }

    @Override
    public void contribute(Mods.Builder b, EventInstance inst, long now) {
        if (flickerMax <= 0) return;
        NbtCompound d = dataOf(inst);
        if (d == null || d.getBoolean("done") || !d.contains("svc")) return;
        b.serviceMult *= d.getDouble("svc");
    }

    /** Datos de ESTE efecto en la instancia (sin contexto: busca su índice en la definición). */
    @Nullable
    NbtCompound dataOf(EventInstance inst) {
        EventDef def = EventRegistry.get(inst.defId);
        if (def == null) return null;
        int idx = def.effectsFor(inst.variant).indexOf(this);
        String k = "e" + idx;
        return idx < 0 || !inst.data.contains(k) ? null : inst.data.getCompound(k);
    }

    // ------------------------------------------------------------------ empezar

    @Override
    public void start(EventContext ctx) {
        NbtCompound d = ctx.data();
        d.putBoolean("sp", true);
        ServerWorld w = ctx.world;
        ShopZones.Zone z = ctx.zone;
        if (!ActorModule.ready() || !ShopLife.zoneLoaded(w, z)) { d.putBoolean("asap", true); return; }
        String sp = pickSpecies(w, z, ctx.rng);
        if (sp == null) { d.putBoolean("asap", true); return; }
        d.putString("spn", sp);
        EventState st = EventState.get(w.getServer());
        ActorConfig cfg = ActorConfig.INSTANCE;
        int inZone = 0;
        for (EventInstance e : EventManager.activeFor(z)) inZone += st.actorsOf(e.uid).size();
        int room = Math.min(cfg.maxPerShop - inZone, cfg.maxGlobal - st.actorCount());
        int count = Math.min(room, countMin + (countMax > countMin ? ctx.rng.nextInt(countMax - countMin + 1) : 0));
        if (count <= 0) { d.putBoolean("asap", true); return; }

        // anclas (una sola vez: nunca escanear bloques cada 20 ticks)
        List<BlockPos> floor = sampleFloor(w, z, ctx.rng);
        long[] fl = new long[floor.size()];
        for (int i = 0; i < fl.length; i++) fl[i] = floor.get(i).asLong();
        d.putLongArray("fl", fl);
        BlockPos door = null;
        Direction out = null;
        if (needsDoor()) {
            List<BlockPos> doors = z.events.doors;
            if (doors.isEmpty() && z.events.lastRecompute < 0) doors = ShopLife.shopDoors(w, z);
            List<BlockPos> ok = new ArrayList<>();
            for (BlockPos p : doors) if (w.getBlockState(p).getBlock() instanceof DoorBlock && !EventManager.mods(z).isDoorClosed(p)) ok.add(p);
            if (!ok.isEmpty()) {
                door = ok.get(ctx.rng.nextInt(ok.size()));
                out = outward(w, z, door);
                d.putLong("door", door.asLong());
                d.putInt("dout", out.getId());
            }
        }
        if (behaviour == Behaviour.BLOCK_DOOR && door == null) { d.putBoolean("asap", true); return; }
        // objetivo de los "goto"
        BlockPos target = null;
        if (behaviour == Behaviour.GOTO_DISPLAY || !disable.isEmpty() || theft) {
            Target t = pickDisplay(w, z, ctx.rng, theft);
            if (t != null) {
                d.putLong("disp", t.pos.asLong());
                d.putInt("dslot", t.slot);
                if (behaviour == Behaviour.GOTO_DISPLAY) target = t.pos;
            } else if (theft || behaviour == Behaviour.GOTO_DISPLAY) { d.putBoolean("asap", true); return; }
        }
        if (behaviour == Behaviour.GOTO_REGISTER) {
            target = findRegister(w, z);
            if (target == null) { d.putBoolean("asap", true); return; }
        }
        if (behaviour == Behaviour.GOTO_LIGHT) target = brightest(w, floor);
        if (behaviour == Behaviour.GOTO_DOOR && door != null) target = door;

        int level = level(ctx);
        NbtList list = new NbtList();
        Set<Long> used = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Entity e = CobblemonActors.create(w, sp, level);
            if (e == null) break;
            Vec3d spot = spawnSpot(w, z, e, floor, door, out, used, ctx.rng);
            if (spot == null) break;
            e.refreshPositionAndAngles(spot.x, spot.y, spot.z, ctx.rng.nextFloat() * 360f, 0);
            NbtCompound a = new NbtCompound();
            a.putUuid("u", e.getUuid());
            a.putString("g", "");
            a.putLong("nc", ctx.now() + 40 + ctx.rng.nextInt(120));
            if (target != null) {
                Vec3d ts = spotNear(w, z, e, target, ctx.rng);
                if (ts != null) {
                    a.putBoolean("t", true);
                    a.putDouble("tx", ts.x);
                    a.putDouble("ty", ts.y);
                    a.putDouble("tz", ts.z);
                }
            }
            if (!ActorModule.spawn(w, e, ctx.instance.uid, ctx.index, stays())) continue;
            list.add(a);
            w.spawnParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.5, spot.z, 10, 0.3, 0.3, 0.3, 0.02);
            if (cry) CobblemonActors.cry(e);
        }
        d.put("a", list);
        if (list.isEmpty()) { d.putBoolean("asap", true); return; }
        // la reseña recuerda al Pokémon concreto (sólo si de verdad apareció)
        if (!reviewTag.isEmpty() && ctx.instance.reviewTag.isEmpty()) ctx.instance.reviewTag = reviewTag;
        // bloqueos desde el principio (los de "llegar al expositor" se ponen al llegar)
        if (closeDoor && door != null) {
            ctx.instance.closeDoor(door);
            d.putLong("lk_door", door.asLong());
        }
        if (registerDown) {
            ctx.instance.setRegisterDown(true);
            d.putBoolean("lk_reg", true);
        }
        if (lighting >= 0) {
            ctx.instance.setLightingOverride(lighting);
            d.putBoolean("lk_light", true);
        }
        if (!disable.isEmpty() && behaviour != Behaviour.GOTO_DISPLAY) applyDisable(ctx, d);
        if (flickerMax > 0) d.putDouble("svc", flickerMin + ctx.rng.nextDouble() * (flickerMax - flickerMin));
        if (leaveEvery > 0) d.putLong("sing", ctx.now() + 100);
        ctx.invalidate();
        ctx.markDirty();
    }

    private boolean needsDoor() {
        return behaviour == Behaviour.BLOCK_DOOR || behaviour == Behaviour.OUTSIDE || behaviour == Behaviour.GOTO_DOOR || closeDoor
                || behaviour == Behaviour.GOTO_DISPLAY || behaviour == Behaviour.GOTO_REGISTER || behaviour == Behaviour.GOTO_LIGHT;
    }

    boolean stays() {
        return behaviour == Behaviour.STAY || behaviour == Behaviour.OUTSIDE || behaviour == Behaviour.BLOCK_DOOR;
    }

    @Nullable
    private String pickSpecies(ServerWorld w, ShopZones.Zone z, Random r) {
        List<String> pool = species;
        for (SpeciesRule rule : byBiome) {
            if (rule.biomes().isEmpty() || rule.biomes().stream().anyMatch(b -> biomeMatches(w, z, b))) { pool = rule.species(); break; }
        }
        List<String> ok = new ArrayList<>();
        for (String s : pool) if (CobblemonActors.speciesOk(s)) ok.add(s);
        if (ok.isEmpty()) for (String s : species) if (CobblemonActors.speciesOk(s)) ok.add(s);
        return ok.isEmpty() ? null : ok.get(r.nextInt(ok.size()));
    }

    private static boolean biomeMatches(ServerWorld w, ShopZones.Zone z, String spec) {
        var biome = w.getBiome(z.core);
        if (spec.startsWith("#")) {
            Identifier id = Identifier.tryParse(spec.substring(1));
            return id != null && biome.isIn(net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.BIOME, id));
        }
        Identifier id = Identifier.tryParse(spec);
        return id != null && biome.matchesId(id);
    }

    /** Nivel: al azar en [min,max], nunca por encima de la media del equipo del dueño ni de ActorConfig.maxLevel. */
    private int level(EventContext ctx) {
        int lv = levelMin + (levelMax > levelMin ? ctx.rng.nextInt(levelMax - levelMin + 1) : 0);
        int cap = ActorConfig.INSTANCE.maxLevel;
        ServerPlayerEntity owner = ctx.owner();
        if (owner != null) {
            List<CobblemonParty.Mon> party = CobblemonParty.list(owner);
            if (!party.isEmpty()) {
                int sum = 0;
                for (CobblemonParty.Mon m : party) sum += m.level();
                cap = Math.min(cap, Math.max(1, Math.round(sum / (float) party.size())));
            }
        }
        return Math.max(1, Math.min(cap, lv));
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick(EventContext ctx) {
        NbtCompound d = ctx.data();
        if (!d.getBoolean("sp")) return;
        if (d.getBoolean("asap")) {
            d.putBoolean("asap", false);
            d.putBoolean("done", true);
            d.putBoolean("tpaid", true);
            if (!otherFix(ctx) && endWhenGone) EventManager.end(ctx.instance.uid, "left");
            return;
        }
        if (d.getBoolean("done")) return;
        ServerWorld w = ctx.world;
        ShopZones.Zone z = ctx.zone;
        long now = ctx.now();
        NbtList list = d.getList("a", NbtElement.COMPOUND_TYPE);
        boolean any = false;
        for (int i = 0; i < list.size(); i++) {
            NbtCompound a = list.getCompound(i);
            if (!a.getString("g").isEmpty() || !a.containsUuid("u")) continue;
            UUID id = a.getUuid("u");
            if (ActorModule.isLeaving(id)) { if (a.getString("g").isEmpty()) a.putString("g", "left"); continue; }
            Entity e = w.getEntity(id);
            if (e == null || e.isRemoved()) {
                a.putInt("ms", a.getInt("ms") + 1);
                if (a.getInt("ms") >= 2) {
                    a.putString("g", "lost");
                    ActorModule.forget(w, id);
                } else any = true;
                continue;
            }
            a.putInt("ms", 0);
            any = true;
            if (ActorModule.inBattle(id)) continue;
            // correa: si se ha ido lejos de la tienda, se da por ido (nunca se trae de vuelta ni se repone)
            if (!z.box().expand(ActorConfig.INSTANCE.leashBlocks).contains(e.getPos())) {
                a.putString("g", "lost");
                ActorModule.vanish(w, e);
                continue;
            }
            behave(ctx, d, a, e, now);
            ambient(ctx, a, e, now);
        }
        // premios pendientes (dar de comer / acariciar en positivos...)
        if (d.contains("pend")) {
            NbtList pend = d.getList("pend", NbtElement.STRING_TYPE);
            for (int i = 0; i < pend.size(); i++) giveReward(ctx, pend.getString(i));
            d.remove("pend");
        }
        // guardia que recupera lo robado
        if (theft && d.getBoolean("ts") && z.guard && d.getLong("ga") > 0 && now >= d.getLong("ga")) {
            d.putLong("ga", 0);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound a = list.getCompound(i);
                if (!a.getString("g").isEmpty()) continue;
                Entity e = w.getEntity(a.getUuid("u"));
                if (e != null) ActorModule.leave(w, e, awayFrom(w, z, e, d), false);
                a.putString("g", "guard");
            }
            ctx.tellOwner(Text.translatable("tcgshop.actor.theft_guard", speciesName(d)).formatted(Formatting.GREEN), false);
        }
        if (flickerMax > 0 && now % 200 < 20) {
            d.putDouble("svc", flickerMin + ctx.rng.nextDouble() * (flickerMax - flickerMin));
            ctx.invalidate();
        }
        if (leaveEvery > 0 && any && now >= d.getLong("sing")) {
            d.putLong("sing", now + leaveEvery);
            scareCustomers(ctx);
        }
        if (!resolveEvents.isEmpty() && any && !d.getBoolean("rev")) {
            d.putBoolean("rev", true);
            helpOthers(ctx, d);
        }
        // ¿se han ido todos?
        boolean allGone = true;
        String how = "";
        for (int i = 0; i < list.size(); i++) {
            String g = list.getCompound(i).getString("g");
            if (g.isEmpty()) { allGone = false; break; }
            if (!g.equals("lost") && !g.equals("left")) how = g;
        }
        if (allGone) allGone(ctx, d, how);
        ctx.markDirty();
    }

    private void behave(EventContext ctx, NbtCompound d, NbtCompound a, Entity e, long now) {
        ShopZones.Zone z = ctx.zone;
        ServerWorld w = ctx.world;
        int ph = a.getInt("ph");
        boolean hasT = a.getBoolean("t");
        switch (behaviour) {
            case STAY, OUTSIDE, BLOCK_DOOR -> ActorModule.hold(e, w);
            case WANDER -> {
                ActorModule.release(e);
                if (!z.contains(e.getBlockPos()) || now >= a.getLong("wt")) {
                    BlockPos p = randomFloor(d, ctx.rng);
                    if (p != null) ActorModule.walkTo(e, Vec3d.ofBottomCenter(p), 1.0);
                    a.putLong("wt", now + 100 + ctx.rng.nextInt(160));
                }
            }
            default -> { // GOTO_*
                if (ph >= 2) { ActorModule.hold(e, w); return; }
                Vec3d t = hasT ? new Vec3d(a.getDouble("tx"), a.getDouble("ty"), a.getDouble("tz")) : null;
                if (!a.contains("pt")) a.putLong("pt", now);
                long age = now - a.getLong("pt");
                if (t == null || e.squaredDistanceTo(t) < 2.6 || age > (ph == 0 ? 600 : 300)) {
                    if (ph == 0) arrive(ctx, d, a, e);
                    // el ladrón, tras "llevarse" algo, se aparta a un rincón
                    if (ph == 0 && theft) {
                        BlockPos far = farFloor(d, e.getBlockPos());
                        if (far != null) {
                            a.putInt("ph", 1);
                            a.putLong("pt", now);
                            a.putBoolean("t", true);
                            a.putDouble("tx", far.getX() + 0.5);
                            a.putDouble("ty", far.getY());
                            a.putDouble("tz", far.getZ() + 0.5);
                            ActorModule.release(e);
                            ActorModule.walkTo(e, Vec3d.ofBottomCenter(far), 1.2);
                            return;
                        }
                    }
                    a.putInt("ph", 2);
                    ActorModule.hold(e, w);
                } else {
                    ActorModule.release(e);
                    ActorModule.walkTo(e, t, 1.0);
                }
            }
        }
    }

    /** Llega a su objetivo: expositor (casillas bloqueadas / robo virtual). */
    private void arrive(EventContext ctx, NbtCompound d, NbtCompound a, Entity e) {
        if (behaviour != Behaviour.GOTO_DISPLAY) return;
        if (theft && !d.getBoolean("ts")) steal(ctx, d, e);
        else if (!disable.isEmpty() && !d.contains("lk_disp") && !d.contains("lk_slots")) applyDisable(ctx, d);
    }

    /** Robo VIRTUAL: la casilla queda "vacía" para los clientes (el objeto no se mueve) y se apunta su valor. */
    private void steal(EventContext ctx, NbtCompound d, Entity e) {
        d.putBoolean("ts", true);
        if (!d.contains("disp")) return;
        BlockPos pos = BlockPos.fromLong(d.getLong("disp"));
        int slot = d.getInt("dslot");
        if (!(ctx.world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity be) || slot < 0 || slot >= be.size() || be.getStack(slot).isEmpty()) return;
        ItemStack st = be.getStack(slot);
        long value = Math.min(Math.min(theftMax, ActorConfig.INSTANCE.theftMaxValue), Math.max(1, ShopServer.priceOf(st.copyWithCount(1), 1.0)));
        d.putLong("tv", value);
        ctx.instance.disableSlot(pos, slot);
        NbtList sl = new NbtList();
        sl.add(NbtString.of(pos.asLong() + ":" + slot));
        d.put("lk_slots", sl);
        if (ctx.zone.guard) d.putLong("ga", ctx.now() + 20L * (guardMin + ctx.rng.nextInt(guardMax - guardMin + 1)));
        ctx.invalidate();
        ctx.world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.01);
        ctx.world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.8f, 0.7f);
        ctx.tellOwner(Text.translatable("tcgshop.actor.theft", speciesName(d), st.getName().copy().formatted(Formatting.YELLOW))
                .formatted(Formatting.RED), false);
    }

    private void applyDisable(EventContext ctx, NbtCompound d) {
        if (!d.contains("disp")) return;
        BlockPos pos = BlockPos.fromLong(d.getLong("disp"));
        if (disable.equals("display")) {
            ctx.instance.disableDisplay(pos);
            d.putLong("lk_disp", pos.asLong());
        } else if (ctx.world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity be) {
            List<Integer> stocked = new ArrayList<>();
            for (int i = 0; i < be.size(); i++) if (!be.getStack(i).isEmpty()) stocked.add(i);
            java.util.Collections.shuffle(stocked, ctx.rng);
            int n = disable.equals("slot") ? 1 : disableMin + (disableMax > disableMin ? ctx.rng.nextInt(disableMax - disableMin + 1) : 0);
            NbtList sl = new NbtList();
            for (int i = 0; i < Math.min(n, stocked.size()); i++) {
                ctx.instance.disableSlot(pos, stocked.get(i));
                sl.add(NbtString.of(pos.asLong() + ":" + stocked.get(i)));
            }
            d.put("lk_slots", sl);
            if (!sl.isEmpty()) {
                ctx.world.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
                ctx.tellOwner(Text.translatable("tcgshop.actor.slots", speciesName(d), sl.size()).formatted(Formatting.GOLD), false);
            }
        }
        ctx.invalidate();
    }

    private void ambient(EventContext ctx, NbtCompound a, Entity e, long now) {
        if (cry && now >= a.getLong("nc")) {
            CobblemonActors.cry(e);
            ActorConfig c = ActorConfig.INSTANCE;
            a.putLong("nc", now + c.cryMinTicks + ctx.rng.nextInt(Math.max(1, c.cryMaxTicks - c.cryMinTicks + 1)));
        }
        if (!particles.isEmpty() && ActorConfig.INSTANCE.particles && ctx.rng.nextFloat() < 0.45f) {
            var p = ActorModule.particle(particles);
            if (p != null) ctx.world.spawnParticles(p, e.getX(), e.getY() + e.getHeight() * 0.7, e.getZ(), 3, e.getWidth() * 0.4, 0.25, e.getWidth() * 0.4, 0.01);
        }
    }

    /** Jigglypuff y compañía: algunos clientes que están dentro se van (por el camino normal, devolviendo lo que llevan). */
    private void scareCustomers(EventContext ctx) {
        if (leaveChance <= 0) return;
        ShopZones.Zone z = ctx.zone;
        int n = 0;
        for (CustomerEntity c : ctx.world.getEntitiesByClass(CustomerEntity.class, z.box(), x -> x.isAlive() && z.contains(x.getBlockPos()))) {
            if (ctx.rng.nextDouble() >= leaveChance) continue;
            if (ActorModule.customerLeave(c, leaveKey)) n++;
        }
        if (n > 0) ctx.tellOwner(Text.translatable("tcgshop.actor.customers_left", speciesName(ctx.data()), n).formatted(Formatting.GOLD), true);
    }

    /** Flaaffy y compañía: arregla otros eventos activos de la tienda (por id o "#plantilla"). */
    private void helpOthers(EventContext ctx, NbtCompound d) {
        for (EventInstance e : new ArrayList<>(EventManager.activeFor(ctx.zone))) {
            if (e.uid == ctx.instance.uid || e.resolved()) continue;
            EventDef od = EventRegistry.get(e.defId);
            if (od == null) continue;
            boolean match = resolveEvents.contains(od.id()) || resolveEvents.contains("#" + od.template());
            if (match && EventManager.resolve(e.uid, "pokemon"))
                ctx.tellOwner(Text.translatable("tcgshop.actor.helped", speciesName(d), EventManager.name(od)).formatted(Formatting.GREEN), false);
        }
    }

    private void allGone(EventContext ctx, NbtCompound d, String how) {
        d.putBoolean("done", true);
        undoLocks(ctx, d);
        if (resolveEvent.equals("never")) return;
        boolean byPlayer = !how.isEmpty();
        boolean other = otherFix(ctx);
        if (byPlayer && fixWays() && !ctx.instance.resolved()) {
            if (!other) ctx.resolve(how);
            else ctx.tellOwner(Text.translatable("tcgshop.actor.gone_rest", speciesName(d)).formatted(Formatting.YELLOW), false);
            return;
        }
        if (other || !endWhenGone || ctx.instance.resolved()) return;
        // se fue solo (zona descargada, se alejó...): nunca se castiga al dueño por no estar
        if (!byPlayer) d.putBoolean("tpaid", true);
        EventManager.end(ctx.instance.uid, byPlayer ? "done" : "left");
    }

    /**
     * ¿Tiene el evento otra forma de arreglarse que no sea este actor? (incidentes, objetivos... u otro actor que
     * todavía siga en la tienda: así el último actor en irse es el que arregla el evento)
     */
    private boolean otherFix(EventContext ctx) {
        for (EventEffect e : ctx.def.effectsFor(ctx.instance.variant)) {
            if (e == this || !e.providesFix()) continue;
            if (e instanceof ActorEffect ae && !ae.hasPresent(ctx.instance)) continue;
            return true;
        }
        return false;
    }

    /** ¿Le queda algún actor en la tienda a este efecto en esta instancia? */
    boolean hasPresent(EventInstance inst) {
        NbtCompound d = dataOf(inst);
        if (d == null || d.getBoolean("done") || !d.getBoolean("sp")) return false;
        NbtList list = d.getList("a", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getString("g").isEmpty()) return true;
        return false;
    }

    private void undoLocks(EventContext ctx, NbtCompound d) {
        if (ctx.instance.resolved()) return; // el núcleo ya los quitó
        boolean ch = false;
        if (d.contains("lk_door")) { ctx.instance.openDoor(BlockPos.fromLong(d.getLong("lk_door"))); d.remove("lk_door"); ch = true; }
        if (d.contains("lk_disp")) { ctx.instance.enableDisplay(BlockPos.fromLong(d.getLong("lk_disp"))); d.remove("lk_disp"); ch = true; }
        if (d.contains("lk_slots")) {
            NbtList sl = d.getList("lk_slots", NbtElement.STRING_TYPE);
            for (int i = 0; i < sl.size(); i++) {
                String s = sl.getString(i);
                int c = s.indexOf(':');
                if (c > 0) try { ctx.instance.enableDisplay(BlockPos.fromLong(Long.parseLong(s.substring(0, c)))); } catch (NumberFormatException ignored) {}
            }
            d.remove("lk_slots");
            ch = true;
        }
        if (d.getBoolean("lk_reg")) { ctx.instance.setRegisterDown(false); d.remove("lk_reg"); ch = true; }
        if (d.getBoolean("lk_light")) { ctx.instance.setLightingOverride(-1); d.remove("lk_light"); ch = true; }
        if (ch) dev.tcgshop.events.incident.IncidentEffect.reapplyLocks(ctx.instance, ctx.def);
        if (ch) ctx.invalidate();
    }

    // ------------------------------------------------------------------ arreglar / terminar

    @Override
    public void resolve(EventContext ctx, String how) {
        NbtCompound d = ctx.data();
        if (theft && d.getLong("tv") > 0 && d.getBoolean("ts") && !how.equals("guard"))
            ctx.tellOwner(Text.translatable("tcgshop.actor.theft_recovered", speciesName(d)).formatted(Formatting.GREEN), false);
        sendAway(ctx, d, "resolved");
    }

    @Override
    public void end(EventContext ctx) {
        NbtCompound d = ctx.data();
        // robo sin recuperar: se pierde su valor (dinero de la caja, nunca un objeto)
        // (sólo si el dueño sigue conectado y la tienda cargada: nunca se castiga a un dueño ausente)
        if (theft && !ctx.instance.resolved() && d.getBoolean("ts") && d.getLong("tv") > 0 && !d.getBoolean("tpaid")
                && ctx.owner() != null && ShopLife.zoneLoaded(ctx.world, ctx.zone)) {
            d.putBoolean("tpaid", true);
            long loss = Math.min(d.getLong("tv"), Math.max(0, ctx.zone.balance));
            if (loss > 0) {
                ctx.zone.balance -= loss;
                ctx.zone.stolen++;
                ctx.markDirty();
            }
            ctx.tellOwner(Text.translatable("tcgshop.actor.theft_lost", speciesName(d),
                    Text.literal(dev.tcgshop.pricing.PriceService.format(loss)).formatted(Formatting.GOLD)).formatted(Formatting.RED), false);
        }
        sendAway(ctx, d, "ended");
        EventState st = EventManager.state();
        if (st != null) st.clearActorsOf(ctx.instance.uid);
    }

    /** Todos los que queden se van andando (y desaparecen). Si la zona no está cargada no hay nada que quitar: nunca se guardaron. */
    private void sendAway(EventContext ctx, NbtCompound d, String how) {
        NbtList list = d.getList("a", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound a = list.getCompound(i);
            if (!a.containsUuid("u")) continue;
            UUID id = a.getUuid("u");
            if (a.getString("g").isEmpty()) a.putString("g", how);
            if (ActorModule.isLeaving(id)) continue;
            Entity e = ctx.world.getEntity(id);
            if (e != null && !e.isRemoved()) ActorModule.leave(ctx.world, e, awayFrom(ctx.world, ctx.zone, e, d), true);
            else ActorModule.forget(ctx.world, id);
        }
    }

    // ------------------------------------------------------------------ interacción (la llama ActorModule desde UseEntityCallback)

    /** El dueño hace clic derecho sobre un actor de este efecto. Devuelve true si hizo algo. */
    boolean interact(ServerWorld w, ShopZones.Zone z, EventInstance inst, NbtCompound d, NbtCompound a, Entity e, ServerPlayerEntity p) {
        long now = w.getTime();
        if (now - a.getLong("lc") < 8) return false;
        // en pleno combate no se le puede echar, dar de comer ni acariciar (lo resuelve el combate)
        if (ActorModule.inBattle(e.getUuid())) return false;
        a.putLong("lc", now);
        ItemStack stack = p.getMainHandStack();
        MutableText name = speciesName(d);
        boolean negativeFix = fixWays();
        // combate (agachado), sólo si el evento lo permite
        if (p.isSneaking() && resolve.contains("battle") && ActorConfig.INSTANCE.allowBattles) {
            if (ActorModule.inBattle(e.getUuid())) return false;
            if (ActorModule.startBattle(w, e, p)) {
                p.sendMessage(Text.translatable("tcgshop.actor.battle_start", name).formatted(Formatting.GOLD), true);
            } else p.sendMessage(Text.translatable("tcgshop.actor.battle_cant").formatted(Formatting.GRAY), true);
            return true;
        }
        Identifier itemId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        boolean berry = !stack.isEmpty() && itemId.getNamespace().equals("cobblemon") && itemId.getPath().endsWith("_berry");
        if (berry && (feed || resolve.contains("feed"))) {
            if (a.getBoolean("fed") && !resolve.contains("feed")) {
                p.sendMessage(Text.translatable("tcgshop.actor.full", name).formatted(Formatting.GRAY), true);
                return true;
            }
            if (!p.getAbilities().creativeMode) stack.decrement(1);
            a.putBoolean("fed", true);
            w.spawnParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getHeight() + 0.2, e.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
            w.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.8f, 1.2f);
            pend(d, "feed");
            if (resolve.contains("feed")) {
                a.putString("g", "feed");
                ActorModule.leave(w, e, awayFrom(w, z, e, d), true);
                p.sendMessage(Text.translatable("tcgshop.actor.fed_leaves", name).formatted(Formatting.GREEN), true);
            } else p.sendMessage(Text.translatable("tcgshop.actor.fed", name).formatted(Formatting.GREEN), true);
            return true;
        }
        if (stack.isEmpty() && resolve.contains("shoo")) {
            int n = a.getInt("sh") + 1;
            a.putInt("sh", n);
            w.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
            w.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 0.7f, 1.3f);
            if (n >= shooHits) {
                a.putString("g", "shoo");
                pend(d, "shoo");
                if (cry) CobblemonActors.cry(e);
                ActorModule.leave(w, e, awayFrom(w, z, e, d), false);
                p.sendMessage(Text.translatable("tcgshop.actor.shooed", name).formatted(Formatting.GREEN), true);
            } else {
                Vec3d push = e.getPos().subtract(p.getPos()).multiply(1, 0, 1);
                if (push.lengthSquared() > 1e-4 && !ActorModule.isHeld(e)) e.addVelocity(push.normalize().multiply(0.25).add(0, 0.2, 0));
                else e.addVelocity(0, 0.25, 0);
                e.velocityModified = true;
                p.sendMessage(Text.translatable("tcgshop.actor.shoo_progress", name, n, shooHits).formatted(Formatting.YELLOW), true);
            }
            return true;
        }
        if (stack.isEmpty() && pet) {
            w.spawnParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getHeight() + 0.2, e.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
            if (!a.getBoolean("pet")) {
                a.putBoolean("pet", true);
                pend(d, "pet");
            }
            if (petLeaves) {
                a.putString("g", "pet");
                w.spawnParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + 0.6, e.getZ(), 30, 0.4, 0.5, 0.4, 0.4);
                w.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.NEUTRAL, 0.6f, 1.4f);
                ActorModule.vanish(w, e);
                p.sendMessage(Text.translatable("tcgshop.actor.teleported", name).formatted(Formatting.LIGHT_PURPLE), true);
            } else p.sendMessage(Text.translatable("tcgshop.actor.pet", name).formatted(Formatting.LIGHT_PURPLE), true);
            return true;
        }
        // pista de lo que funciona
        MutableText ways = Text.empty();
        int k = 0;
        if (resolve.contains("shoo")) { ways.append(Text.translatable("tcgshop.actor.way.shoo", shooHits)); k++; }
        if (feed || resolve.contains("feed")) { if (k++ > 0) ways.append(" · "); ways.append(Text.translatable("tcgshop.actor.way.feed")); }
        if (resolve.contains("battle") && ActorConfig.INSTANCE.allowBattles) { if (k++ > 0) ways.append(" · "); ways.append(Text.translatable("tcgshop.actor.way.battle")); }
        if (pet) { if (k++ > 0) ways.append(" · "); ways.append(Text.translatable("tcgshop.actor.way.pet")); }
        if (k > 0) p.sendMessage(Text.translatable("tcgshop.actor.hint", name, ways).formatted(Formatting.GRAY), true);
        return negativeFix || k > 0;
    }

    /** Combate terminado (lo llama ActorModule): won = el Pokémon se debilitó. */
    void battleOver(ServerWorld w, NbtCompound d, NbtCompound a, @Nullable Entity e, ServerPlayerEntity p, boolean won) {
        MutableText name = speciesName(d);
        if (won) {
            a.putString("g", "battle");
            pend(d, "battle");
            if (e != null) ActorModule.vanish(w, e);
            if (p != null) p.sendMessage(Text.translatable("tcgshop.actor.battle_won", name).formatted(Formatting.GREEN), false);
        } else if (p != null) p.sendMessage(Text.translatable("tcgshop.actor.battle_lost", name).formatted(Formatting.GRAY), false);
    }

    private static void pend(NbtCompound d, String kind) {
        NbtList l = d.getList("pend", NbtElement.STRING_TYPE);
        l.add(NbtString.of(kind));
        d.put("pend", l);
    }

    /** Premio pequeño (una vez por tipo y evento), siempre con el tope de dinero por dueño. */
    private void giveReward(EventContext ctx, String kind) {
        Reward r = rewards.get(kind);
        NbtCompound d = ctx.data();
        if (r == null || d.getBoolean("rw_" + kind)) return;
        d.putBoolean("rw_" + kind, true);
        long money = r.moneyMax() > 0 ? r.moneyMin() + (r.moneyMax() > r.moneyMin() ? (long) (ctx.rng.nextDouble() * (r.moneyMax() - r.moneyMin())) : 0) : 0;
        if (money > 0) EventManager.grantMoney(ctx.world, ctx.zone, money, true);
        if (r.charity() != 0) {
            ctx.zone.charity = Math.max(-10, Math.min(10, ctx.zone.charity + r.charity()));
            ctx.markDirty();
        }
        if (!r.loot().isEmpty()) ActorModule.giveLoot(ctx.world, ctx.zone, ctx.owner(), r.loot(), r.rolls());
    }

    MutableText speciesName(NbtCompound d) {
        String sp = d.getString("spn");
        if (sp.isEmpty()) sp = species.isEmpty() ? "pokemon" : species.get(0);
        return Text.translatable("cobblemon.species." + sp + ".name");
    }

    // ------------------------------------------------------------------ sitios (sólo al empezar o con datos ya cacheados)

    /** Suelos candidatos dentro de la zona (muestreo de columnas al azar, no un escaneo completo). */
    private static List<BlockPos> sampleFloor(ServerWorld w, ShopZones.Zone z, Random r) {
        int half = z.tierInfo().size / 2;
        List<BlockPos> roofed = new ArrayList<>(), open = new ArrayList<>();
        int y0 = z.core.getY();
        for (int k = 0; k < 220 && roofed.size() < 40; k++) {
            int x = z.core.getX() - half + 1 + r.nextInt(Math.max(1, half * 2 - 1));
            int zz = z.core.getZ() - half + 1 + r.nextInt(Math.max(1, half * 2 - 1));
            for (int dy : new int[]{0, 1, -1, 2, -2, 3}) {
                BlockPos p = new BlockPos(x, y0 + dy, zz);
                if (!z.contains(p) || !standable(w, p)) continue;
                (w.isSkyVisible(p.up()) ? open : roofed).add(p);
                break;
            }
        }
        return roofed.size() >= 4 ? roofed : roofed.isEmpty() ? open : concat(roofed, open);
    }

    private static List<BlockPos> concat(List<BlockPos> a, List<BlockPos> b) {
        List<BlockPos> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    static boolean standable(ServerWorld w, BlockPos p) {
        BlockState below = w.getBlockState(p.down());
        if (!below.isSideSolidFullSquare(w, p.down(), Direction.UP)) return false;
        if (below.getBlock() instanceof DoorBlock) return false;
        return w.getBlockState(p).getCollisionShape(w, p).isEmpty() && w.getBlockState(p.up()).getCollisionShape(w, p.up()).isEmpty()
                && w.getFluidState(p).isEmpty() && !(w.getBlockState(p).getBlock() instanceof DoorBlock);
    }

    @Nullable
    private static BlockPos randomFloor(NbtCompound d, Random r) {
        long[] fl = d.getLongArray("fl");
        return fl.length == 0 ? null : BlockPos.fromLong(fl[r.nextInt(fl.length)]);
    }

    @Nullable
    private static BlockPos farFloor(NbtCompound d, BlockPos from) {
        long[] fl = d.getLongArray("fl");
        BlockPos best = null;
        double bd = -1;
        for (long l : fl) {
            BlockPos p = BlockPos.fromLong(l);
            double dd = p.getSquaredDistance(from);
            if (dd > bd) { bd = dd; best = p; }
        }
        return best;
    }

    @Nullable
    private static BlockPos brightest(ServerWorld w, List<BlockPos> floor) {
        BlockPos best = null;
        int bl = -1;
        for (BlockPos p : floor) {
            int l = w.getLightLevel(LightType.BLOCK, p);
            if (l > bl) { bl = l; best = p; }
        }
        return best;
    }

    /** Lado de fuera de una puerta (mismo criterio que ShopLife: el que da al cielo o el más lejano del núcleo). */
    static Direction outward(ServerWorld w, ShopZones.Zone z, BlockPos door) {
        BlockState s = w.getBlockState(door);
        Direction f = s.contains(DoorBlock.FACING) ? s.get(DoorBlock.FACING) : Direction.NORTH;
        boolean a = w.isSkyVisible(door.offset(f).up()), b = w.isSkyVisible(door.offset(f.getOpposite()).up());
        if (a != b) return a ? f : f.getOpposite();
        double da = door.offset(f).getSquaredDistance(z.core), db = door.offset(f.getOpposite()).getSquaredDistance(z.core);
        return da >= db ? f : f.getOpposite();
    }

    /** Dónde aparece cada actor según su comportamiento (dentro de la zona, en suelo firme y con hueco para su tamaño). */
    @Nullable
    private Vec3d spawnSpot(ServerWorld w, ShopZones.Zone z, Entity e, List<BlockPos> floor, @Nullable BlockPos door, @Nullable Direction out,
                            Set<Long> used, Random r) {
        List<BlockPos> cands = new ArrayList<>();
        Box forbid = null;
        if (door != null && out != null) {
            Direction side = out.rotateYClockwise();
            // nunca en la puerta ni en la casilla de delante/detrás (que nadie se quede encerrado)
            forbid = new Box(door).union(new Box(door.offset(out))).union(new Box(door.offset(out.getOpposite()))).expand(0.05, 0, 0.05).stretch(0, 1, 0);
            switch (behaviour) {
                case OUTSIDE, BLOCK_DOOR -> {
                    for (int dist : new int[]{2, 3}) for (int lat : new int[]{1, -1, 2, -2, 0}) cands.add(door.offset(out, dist).offset(side, lat));
                    for (int dist : new int[]{2, 3}) for (int lat : new int[]{1, -1, 2, -2}) cands.add(door.offset(out.getOpposite(), dist).offset(side, lat));
                }
                case GOTO_DISPLAY, GOTO_REGISTER, GOTO_LIGHT, GOTO_DOOR -> {
                    for (int dist : new int[]{2, 3}) for (int lat : new int[]{0, 1, -1}) cands.add(door.offset(out.getOpposite(), dist).offset(side, lat));
                }
                default -> {}
            }
        }
        List<BlockPos> shuffled = new ArrayList<>(floor);
        java.util.Collections.shuffle(shuffled, r);
        cands.addAll(shuffled);
        for (BlockPos c : cands) {
            if (used.contains(c.asLong())) continue;
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos p = c.up(dy);
                if (!z.contains(p) || !standable(w, p)) continue;
                Vec3d v = Vec3d.ofBottomCenter(p);
                e.refreshPositionAndAngles(v.x, v.y, v.z, 0, 0);
                Box bb = e.getBoundingBox();
                if (forbid != null && bb.intersects(forbid)) continue;
                if (!w.isSpaceEmpty(e, bb)) continue;
                if (!z.box().contains(v)) continue;
                used.add(c.asLong());
                return v;
            }
        }
        return null;
    }

    /** Sitio libre pegado a un bloque objetivo (expositor, caja, luz), el más cercano. */
    @Nullable
    private static Vec3d spotNear(ServerWorld w, ShopZones.Zone z, Entity e, BlockPos target, Random r) {
        Vec3d best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos p = target.add(dx, dy, dz);
                    if (!z.contains(p) || !standable(w, p)) continue;
                    double dd = p.getSquaredDistance(target) + r.nextDouble() * 0.1;
                    if (dd < bd) { bd = dd; best = Vec3d.ofBottomCenter(p); }
                }
        return best;
    }

    record Target(BlockPos pos, int slot) {}

    /** Un expositor con género (el más barato si es un robo; si no, uno al azar). */
    @Nullable
    private static Target pickDisplay(ServerWorld w, ShopZones.Zone z, Random r, boolean cheapest) {
        List<Target> all = new ArrayList<>();
        Target cheap = null;
        long cv = Long.MAX_VALUE;
        Mods mods = EventManager.mods(z);
        for (BlockEntity be : zoneBlockEntities(w, z)) {
            if (!(be instanceof ShopDisplayBlockEntity d) || d.isHome()) continue;
            for (int i = 0; i < d.size(); i++) {
                ItemStack st = d.getStack(i);
                if (st.isEmpty() || mods.isDisplayDisabled(d.getPos(), i)) continue;
                Target t = new Target(d.getPos().toImmutable(), i);
                all.add(t);
                if (cheapest) {
                    long v = ShopServer.priceOf(st.copyWithCount(1), 1.0);
                    if (v < cv) { cv = v; cheap = t; }
                }
            }
        }
        if (all.isEmpty()) return null;
        return cheapest ? cheap : all.get(r.nextInt(all.size()));
    }

    @Nullable
    private static BlockPos findRegister(ServerWorld w, ShopZones.Zone z) {
        for (BlockEntity be : zoneBlockEntities(w, z))
            if (be.getCachedState().isOf(TcgBlocks.CASH_REGISTER)) return be.getPos().toImmutable();
        return null;
    }

    private static List<BlockEntity> zoneBlockEntities(ServerWorld w, ShopZones.Zone z) {
        List<BlockEntity> out = new ArrayList<>();
        int half = z.tierInfo().size / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) if (z.contains(be.getPos())) out.add(be);
            }
        return out;
    }

    /** Hacia dónde se va al marcharse: fuera por la puerta si la hay; si no, lejos del dueño. */
    static Vec3d awayFrom(ServerWorld w, ShopZones.Zone z, Entity e, NbtCompound d) {
        if (d.contains("door")) {
            BlockPos door = BlockPos.fromLong(d.getLong("door"));
            Direction out = Direction.byId(d.getInt("dout"));
            return Vec3d.ofBottomCenter(door.offset(out, 7));
        }
        ServerPlayerEntity owner = w.getServer().getPlayerManager().getPlayer(z.owner);
        Vec3d dir = owner == null ? new Vec3d(1, 0, 0) : e.getPos().subtract(owner.getPos()).multiply(1, 0, 1);
        if (dir.lengthSquared() < 1e-4) dir = new Vec3d(1, 0, 0);
        return e.getPos().add(dir.normalize().multiply(9));
    }
}
