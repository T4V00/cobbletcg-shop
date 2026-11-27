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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * "actor" effect: one or more REAL wild Cobblemon Pokémon that visit the shop during the event.
 * See EFFECTS.md (section 8). JSON fields (all optional except species):
 * <pre>
 * species         "psyduck" or ["growlithe","vulpix"] (a random one among those implemented in this Cobblemon)
 * speciesByBiome  [{"biomes":["#minecraft:is_taiga"],"species":["lucario"]}, {"species":["growlithe"]}] (the first that matches)
 * level [5,15]    never above the owner's party average nor ActorConfig.maxLevel
 * count 1|[1,3]   caps: ActorConfig.maxPerShop (3) and maxGlobal (12)
 * behaviour       stay | wander | goto_door | goto_display | goto_register | goto_light | outside (outside the door but
 *                 INSIDE the zone) | block_door (next to the door, never in the doorway or in front, and closes that door)
 * resolve         ["shoo","feed","battle","expire"]: what FIXES the event (shoo = empty hand ×shooHits; feed =
 *                 any cobblemon:*_berry, uses 1; battle = sneaking + right click, owner only)
 * shooHits 3      feed/pet (bool): feed / pet without fixing anything (positive events); petLeaves: teleports away when petted
 * closeDoor, registerDown, lighting (0-15), disable ("display"|"slot"|"slots") + disableCount: locks while present
 * reward          {"onFeed"|"onPet"|"onShoo"|"onBattle": {"money":[a,b], "charity":n, "loot":"tcgshop:events/x", "rolls":1}}
 *                 once per kind and event, with the per-owner money cap
 * theft           {"maxValue":600, "guardSeconds":[30,60]} VIRTUAL theft (only with goto_display)
 * serviceFlicker  [0.6,1.5] fluctuating serviceMult (Rotom); resolveEvents ["#template","ns:id"]: fixes those active events
 * leaveCustomers  {"chance":0.3, "every":1200, "reviewKey":"ev_noise"} (needs CustomerEntity.leaveForEvent)
 * particles       splash|drip|spark|note|heart|smoke|flame|leaf|cherry|angry|glow|wax|happy|cloud|dust|nectar|spore|portal|bubble
 * cry true, endWhenGone true (if they all leave without it being fixed, the event ends), resolveEvent "auto"|"never",
 * reviewTag       the instance's review tag (only if the Pokémon actually appeared)
 * </pre>
 *
 * Rules (critique B/G7): always uncatchable (uncatchable=yes + cancel the Poké Ball), invulnerable, not
 * battleable except for the owner's battle (level capped at their party average), tags EVENT_TAG +
 * "tcgshop_ev_&lt;uid&gt;" + {@link ActorModule#ACTOR_TAG}, registered in EventState BEFORE spawnEntity, never saved
 * to the world (POKEMON_ENTITY_SAVE_TO_WORLD cancelled), never respawn (if one is missing, that step ends), caps of
 * 3 per shop and 12 in total (ActorConfig), looked up by UUID every tick (the entity is never stored).
 *
 * Per-instance state in ctx.data():
 *  a      list of actors {u uuid, g "" | how it left, sh shoos, lc last click, ph phase, t/tx/ty/tz destination,
 *         nc next cry, ms ticks not found, wt next stroll, sp species}
 *  fl     candidate floors (long[]), door/dout door and its outer side, disp/dslot target display
 *  lk_*   locks placed by THIS effect (to remove them if the actors leave and the event continues)
 *  tv/ts  virtual theft (value and whether it already "stole"), ga time when the guard recovers it
 *  pend   pending rewards (feed/pet/shoo/battle) handed out by the tick; rw_* already given
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
        if (species.isEmpty() && byBiome.isEmpty()) throw new IllegalArgumentException("actor without species");
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
            throw new IllegalArgumentException("invalid actor.behaviour: " + b.toLowerCase(Locale.ROOT)
                    + " (stay|wander|goto_door|goto_display|goto_register|goto_light|outside|block_door)");
        }
        for (String r : strings(o, "resolve")) {
            String x = r.toLowerCase(Locale.ROOT);
            if (!List.of("shoo", "feed", "battle", "expire").contains(x)) throw new IllegalArgumentException("invalid actor.resolve: " + r);
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
        if (!List.of("", "display", "slot", "slots").contains(disable)) throw new IllegalArgumentException("invalid actor.disable: " + disable);
        long[] dc = EventDef.range(o.get("disableCount"), 1);
        disableMin = (int) Math.max(1, Math.min(9, dc[0]));
        disableMax = (int) Math.max(disableMin, Math.min(9, dc[1]));
        particles = EventDef.str(o, "particles", "").toLowerCase(Locale.ROOT);
        cry = EventDef.bool(o, "cry", true);
        endWhenGone = EventDef.bool(o, "endWhenGone", true);
        resolveEvent = EventDef.str(o, "resolveEvent", "auto").toLowerCase(Locale.ROOT);
        if (!List.of("auto", "never").contains(resolveEvent)) throw new IllegalArgumentException("invalid actor.resolveEvent: " + resolveEvent);
        if (o.has("reward")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("reward").entrySet()) {
                String k = e.getKey().toLowerCase(Locale.ROOT).replace("on", "");
                if (!List.of("feed", "pet", "shoo", "battle").contains(k)) throw new IllegalArgumentException("actor.reward: invalid key " + e.getKey());
                JsonObject r = e.getValue().getAsJsonObject();
                long[] m = EventDef.range(r.get("money"), 0);
                String loot = EventDef.str(r, "loot", "");
                if (!loot.isEmpty()) {
                    ResourceLocation id = ResourceLocation.tryParse(loot);
                    if (id == null || !id.getNamespace().equals("tcgshop") || !id.getPath().startsWith("events/"))
                        throw new IllegalArgumentException("actor.reward.loot can only use tcgshop:events/* tables");
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
        if (theft && behaviour != Behaviour.GOTO_DISPLAY) throw new IllegalArgumentException("actor.theft requires behaviour goto_display");
    }

    private static List<String> strings(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (!o.has(k)) return out;
        JsonElement e = o.get(k);
        if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        else out.add(e.getAsString());
        return out;
    }

    // ------------------------------------------------------------------ contract

    /** Ways to fix it that depend on the player (expire does not count). */
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
        CompoundTag d = dataOf(inst);
        if (d == null || d.getBoolean("done") || !d.contains("svc")) return;
        b.serviceMult *= d.getDouble("svc");
    }

    /** THIS effect's data in the instance (no context: looks up its index in the definition). */
    @Nullable
    CompoundTag dataOf(EventInstance inst) {
        EventDef def = EventRegistry.get(inst.defId);
        if (def == null) return null;
        int idx = def.effectsFor(inst.variant).indexOf(this);
        String k = "e" + idx;
        return idx < 0 || !inst.data.contains(k) ? null : inst.data.getCompound(k);
    }

    // ------------------------------------------------------------------ start

    @Override
    public void start(EventContext ctx) {
        CompoundTag d = ctx.data();
        d.putBoolean("sp", true);
        ServerLevel w = ctx.world;
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

        // anchors (only once: never scan blocks every 20 ticks)
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
                d.putInt("dout", out.get3DDataValue());
            }
        }
        if (behaviour == Behaviour.BLOCK_DOOR && door == null) { d.putBoolean("asap", true); return; }
        // target of the "goto" behaviours
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
        ListTag list = new ListTag();
        Set<Long> used = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Entity e = CobblemonActors.create(w, sp, level);
            if (e == null) break;
            Vec3 spot = spawnSpot(w, z, e, floor, door, out, used, ctx.rng);
            if (spot == null) break;
            e.moveTo(spot.x, spot.y, spot.z, ctx.rng.nextFloat() * 360f, 0);
            CompoundTag a = new CompoundTag();
            a.putUUID("u", e.getUUID());
            a.putString("g", "");
            a.putLong("nc", ctx.now() + 40 + ctx.rng.nextInt(120));
            if (target != null) {
                Vec3 ts = spotNear(w, z, e, target, ctx.rng);
                if (ts != null) {
                    a.putBoolean("t", true);
                    a.putDouble("tx", ts.x);
                    a.putDouble("ty", ts.y);
                    a.putDouble("tz", ts.z);
                }
            }
            if (!ActorModule.spawn(w, e, ctx.instance.uid, ctx.index, stays())) continue;
            list.add(a);
            w.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.5, spot.z, 10, 0.3, 0.3, 0.3, 0.02);
            if (cry) CobblemonActors.cry(e);
        }
        d.put("a", list);
        if (list.isEmpty()) { d.putBoolean("asap", true); return; }
        // the review remembers the specific Pokémon (only if it actually appeared)
        if (!reviewTag.isEmpty() && ctx.instance.reviewTag.isEmpty()) ctx.instance.reviewTag = reviewTag;
        // locks from the start (the "reach the display" ones are placed on arrival)
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
    private String pickSpecies(ServerLevel w, ShopZones.Zone z, Random r) {
        List<String> pool = species;
        for (SpeciesRule rule : byBiome) {
            if (rule.biomes().isEmpty() || rule.biomes().stream().anyMatch(b -> biomeMatches(w, z, b))) { pool = rule.species(); break; }
        }
        List<String> ok = new ArrayList<>();
        for (String s : pool) if (CobblemonActors.speciesOk(s)) ok.add(s);
        if (ok.isEmpty()) for (String s : species) if (CobblemonActors.speciesOk(s)) ok.add(s);
        return ok.isEmpty() ? null : ok.get(r.nextInt(ok.size()));
    }

    private static boolean biomeMatches(ServerLevel w, ShopZones.Zone z, String spec) {
        var biome = w.getBiome(z.core);
        if (spec.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(spec.substring(1));
            return id != null && biome.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, id));
        }
        ResourceLocation id = ResourceLocation.tryParse(spec);
        return id != null && biome.is(id);
    }

    /** Level: random in [min,max], never above the owner's party average nor ActorConfig.maxLevel. */
    private int level(EventContext ctx) {
        int lv = levelMin + (levelMax > levelMin ? ctx.rng.nextInt(levelMax - levelMin + 1) : 0);
        int cap = ActorConfig.INSTANCE.maxLevel;
        ServerPlayer owner = ctx.owner();
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
        CompoundTag d = ctx.data();
        if (!d.getBoolean("sp")) return;
        if (d.getBoolean("asap")) {
            d.putBoolean("asap", false);
            d.putBoolean("done", true);
            d.putBoolean("tpaid", true);
            if (!otherFix(ctx) && endWhenGone) EventManager.end(ctx.instance.uid, "left");
            return;
        }
        if (d.getBoolean("done")) return;
        ServerLevel w = ctx.world;
        ShopZones.Zone z = ctx.zone;
        long now = ctx.now();
        ListTag list = d.getList("a", Tag.TAG_COMPOUND);
        boolean any = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag a = list.getCompound(i);
            if (!a.getString("g").isEmpty() || !a.hasUUID("u")) continue;
            UUID id = a.getUUID("u");
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
            // leash: if it has wandered far from the shop, it counts as gone (never brought back or replaced)
            if (!z.box().inflate(ActorConfig.INSTANCE.leashBlocks).contains(e.position())) {
                a.putString("g", "lost");
                ActorModule.vanish(w, e);
                continue;
            }
            behave(ctx, d, a, e, now);
            ambient(ctx, a, e, now);
        }
        // pending rewards (feeding / petting in positive events...)
        if (d.contains("pend")) {
            ListTag pend = d.getList("pend", Tag.TAG_STRING);
            for (int i = 0; i < pend.size(); i++) giveReward(ctx, pend.getString(i));
            d.remove("pend");
        }
        // guard who recovers the stolen goods
        if (theft && d.getBoolean("ts") && z.guard && d.getLong("ga") > 0 && now >= d.getLong("ga")) {
            d.putLong("ga", 0);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag a = list.getCompound(i);
                if (!a.getString("g").isEmpty()) continue;
                Entity e = w.getEntity(a.getUUID("u"));
                if (e != null) ActorModule.leave(w, e, awayFrom(w, z, e, d), false);
                a.putString("g", "guard");
            }
            ctx.tellOwner(Component.translatable("tcgshop.actor.theft_guard", speciesName(d)).withStyle(ChatFormatting.GREEN), false);
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
        // have they all left?
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

    private void behave(EventContext ctx, CompoundTag d, CompoundTag a, Entity e, long now) {
        ShopZones.Zone z = ctx.zone;
        ServerLevel w = ctx.world;
        int ph = a.getInt("ph");
        boolean hasT = a.getBoolean("t");
        switch (behaviour) {
            case STAY, OUTSIDE, BLOCK_DOOR -> ActorModule.hold(e, w);
            case WANDER -> {
                ActorModule.release(e);
                if (!z.contains(e.blockPosition()) || now >= a.getLong("wt")) {
                    BlockPos p = randomFloor(d, ctx.rng);
                    if (p != null) ActorModule.walkTo(e, Vec3.atBottomCenterOf(p), 1.0);
                    a.putLong("wt", now + 100 + ctx.rng.nextInt(160));
                }
            }
            default -> { // GOTO_*
                if (ph >= 2) { ActorModule.hold(e, w); return; }
                Vec3 t = hasT ? new Vec3(a.getDouble("tx"), a.getDouble("ty"), a.getDouble("tz")) : null;
                if (!a.contains("pt")) a.putLong("pt", now);
                long age = now - a.getLong("pt");
                if (t == null || e.distanceToSqr(t) < 2.6 || age > (ph == 0 ? 600 : 300)) {
                    if (ph == 0) arrive(ctx, d, a, e);
                    // the thief, after "taking" something, moves off to a corner
                    if (ph == 0 && theft) {
                        BlockPos far = farFloor(d, e.blockPosition());
                        if (far != null) {
                            a.putInt("ph", 1);
                            a.putLong("pt", now);
                            a.putBoolean("t", true);
                            a.putDouble("tx", far.getX() + 0.5);
                            a.putDouble("ty", far.getY());
                            a.putDouble("tz", far.getZ() + 0.5);
                            ActorModule.release(e);
                            ActorModule.walkTo(e, Vec3.atBottomCenterOf(far), 1.2);
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

    /** Reaches its target: display (locked slots / virtual theft). */
    private void arrive(EventContext ctx, CompoundTag d, CompoundTag a, Entity e) {
        if (behaviour != Behaviour.GOTO_DISPLAY) return;
        if (theft && !d.getBoolean("ts")) steal(ctx, d, e);
        else if (!disable.isEmpty() && !d.contains("lk_disp") && !d.contains("lk_slots")) applyDisable(ctx, d);
    }

    /** VIRTUAL theft: the slot becomes "empty" for customers (the item does not move) and its value is recorded. */
    private void steal(EventContext ctx, CompoundTag d, Entity e) {
        d.putBoolean("ts", true);
        if (!d.contains("disp")) return;
        BlockPos pos = BlockPos.of(d.getLong("disp"));
        int slot = d.getInt("dslot");
        if (!(ctx.world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity be) || slot < 0 || slot >= be.getContainerSize() || be.getItem(slot).isEmpty()) return;
        ItemStack st = be.getItem(slot);
        long value = Math.min(Math.min(theftMax, ActorConfig.INSTANCE.theftMaxValue), Math.max(1, ShopServer.priceOf(st.copyWithCount(1), 1.0)));
        d.putLong("tv", value);
        ctx.instance.disableSlot(pos, slot);
        ListTag sl = new ListTag();
        sl.add(StringTag.valueOf(pos.asLong() + ":" + slot));
        d.put("lk_slots", sl);
        if (ctx.zone.guard) d.putLong("ga", ctx.now() + 20L * (guardMin + ctx.rng.nextInt(guardMax - guardMin + 1)));
        ctx.invalidate();
        ctx.world.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.01);
        ctx.world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.8f, 0.7f);
        ctx.tellOwner(Component.translatable("tcgshop.actor.theft", speciesName(d), st.getHoverName().copy().withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.RED), false);
    }

    private void applyDisable(EventContext ctx, CompoundTag d) {
        if (!d.contains("disp")) return;
        BlockPos pos = BlockPos.of(d.getLong("disp"));
        if (disable.equals("display")) {
            ctx.instance.disableDisplay(pos);
            d.putLong("lk_disp", pos.asLong());
        } else if (ctx.world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity be) {
            List<Integer> stocked = new ArrayList<>();
            for (int i = 0; i < be.getContainerSize(); i++) if (!be.getItem(i).isEmpty()) stocked.add(i);
            java.util.Collections.shuffle(stocked, ctx.rng);
            int n = disable.equals("slot") ? 1 : disableMin + (disableMax > disableMin ? ctx.rng.nextInt(disableMax - disableMin + 1) : 0);
            ListTag sl = new ListTag();
            for (int i = 0; i < Math.min(n, stocked.size()); i++) {
                ctx.instance.disableSlot(pos, stocked.get(i));
                sl.add(StringTag.valueOf(pos.asLong() + ":" + stocked.get(i)));
            }
            d.put("lk_slots", sl);
            if (!sl.isEmpty()) {
                ctx.world.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
                ctx.tellOwner(Component.translatable("tcgshop.actor.slots", speciesName(d), sl.size()).withStyle(ChatFormatting.GOLD), false);
            }
        }
        ctx.invalidate();
    }

    private void ambient(EventContext ctx, CompoundTag a, Entity e, long now) {
        if (cry && now >= a.getLong("nc")) {
            CobblemonActors.cry(e);
            ActorConfig c = ActorConfig.INSTANCE;
            a.putLong("nc", now + c.cryMinTicks + ctx.rng.nextInt(Math.max(1, c.cryMaxTicks - c.cryMinTicks + 1)));
        }
        if (!particles.isEmpty() && ActorConfig.INSTANCE.particles && ctx.rng.nextFloat() < 0.45f) {
            var p = ActorModule.particle(particles);
            if (p != null) ctx.world.sendParticles(p, e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(), 3, e.getBbWidth() * 0.4, 0.25, e.getBbWidth() * 0.4, 0.01);
        }
    }

    /** Jigglypuff and co.: some customers inside leave (the normal way, returning what they carry). */
    private void scareCustomers(EventContext ctx) {
        if (leaveChance <= 0) return;
        ShopZones.Zone z = ctx.zone;
        int n = 0;
        for (CustomerEntity c : ctx.world.getEntitiesOfClass(CustomerEntity.class, z.box(), x -> x.isAlive() && z.contains(x.blockPosition()))) {
            if (ctx.rng.nextDouble() >= leaveChance) continue;
            if (ActorModule.customerLeave(c, leaveKey)) n++;
        }
        if (n > 0) ctx.tellOwner(Component.translatable("tcgshop.actor.customers_left", speciesName(ctx.data()), n).withStyle(ChatFormatting.GOLD), true);
    }

    /** Flaaffy and co.: fixes other active events of the shop (by id or "#template"). */
    private void helpOthers(EventContext ctx, CompoundTag d) {
        for (EventInstance e : new ArrayList<>(EventManager.activeFor(ctx.zone))) {
            if (e.uid == ctx.instance.uid || e.resolved()) continue;
            EventDef od = EventRegistry.get(e.defId);
            if (od == null) continue;
            boolean match = resolveEvents.contains(od.id()) || resolveEvents.contains("#" + od.template());
            if (match && EventManager.resolve(e.uid, "pokemon"))
                ctx.tellOwner(Component.translatable("tcgshop.actor.helped", speciesName(d), EventManager.name(od)).withStyle(ChatFormatting.GREEN), false);
        }
    }

    private void allGone(EventContext ctx, CompoundTag d, String how) {
        d.putBoolean("done", true);
        undoLocks(ctx, d);
        if (resolveEvent.equals("never")) return;
        boolean byPlayer = !how.isEmpty();
        boolean other = otherFix(ctx);
        if (byPlayer && fixWays() && !ctx.instance.resolved()) {
            if (!other) ctx.resolve(how);
            else ctx.tellOwner(Component.translatable("tcgshop.actor.gone_rest", speciesName(d)).withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (other || !endWhenGone || ctx.instance.resolved()) return;
        // it left on its own (zone unloaded, wandered off...): the owner is never punished for being away
        if (!byPlayer) d.putBoolean("tpaid", true);
        EventManager.end(ctx.instance.uid, byPlayer ? "done" : "left");
    }

    /**
     * Does the event have another way to be fixed besides this actor? (incidents, objectives... or another actor that
     * is still in the shop: this way the last actor to leave is the one that fixes the event)
     */
    private boolean otherFix(EventContext ctx) {
        for (EventEffect e : ctx.def.effectsFor(ctx.instance.variant)) {
            if (e == this || !e.providesFix()) continue;
            if (e instanceof ActorEffect ae && !ae.hasPresent(ctx.instance)) continue;
            return true;
        }
        return false;
    }

    /** Does this effect still have any actor in the shop in this instance? */
    boolean hasPresent(EventInstance inst) {
        CompoundTag d = dataOf(inst);
        if (d == null || d.getBoolean("done") || !d.getBoolean("sp")) return false;
        ListTag list = d.getList("a", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getString("g").isEmpty()) return true;
        return false;
    }

    private void undoLocks(EventContext ctx, CompoundTag d) {
        if (ctx.instance.resolved()) return; // the core already removed them
        boolean ch = false;
        if (d.contains("lk_door")) { ctx.instance.openDoor(BlockPos.of(d.getLong("lk_door"))); d.remove("lk_door"); ch = true; }
        if (d.contains("lk_disp")) { ctx.instance.enableDisplay(BlockPos.of(d.getLong("lk_disp"))); d.remove("lk_disp"); ch = true; }
        if (d.contains("lk_slots")) {
            ListTag sl = d.getList("lk_slots", Tag.TAG_STRING);
            for (int i = 0; i < sl.size(); i++) {
                String s = sl.getString(i);
                int c = s.indexOf(':');
                if (c > 0) try { ctx.instance.enableDisplay(BlockPos.of(Long.parseLong(s.substring(0, c)))); } catch (NumberFormatException ignored) {}
            }
            d.remove("lk_slots");
            ch = true;
        }
        if (d.getBoolean("lk_reg")) { ctx.instance.setRegisterDown(false); d.remove("lk_reg"); ch = true; }
        if (d.getBoolean("lk_light")) { ctx.instance.setLightingOverride(-1); d.remove("lk_light"); ch = true; }
        if (ch) dev.tcgshop.events.incident.IncidentEffect.reapplyLocks(ctx.instance, ctx.def);
        if (ch) ctx.invalidate();
    }

    // ------------------------------------------------------------------ fix / end

    @Override
    public void resolve(EventContext ctx, String how) {
        CompoundTag d = ctx.data();
        if (theft && d.getLong("tv") > 0 && d.getBoolean("ts") && !how.equals("guard"))
            ctx.tellOwner(Component.translatable("tcgshop.actor.theft_recovered", speciesName(d)).withStyle(ChatFormatting.GREEN), false);
        sendAway(ctx, d, "resolved");
    }

    @Override
    public void end(EventContext ctx) {
        CompoundTag d = ctx.data();
        // unrecovered theft: its value is lost (money from the register, never an item)
        // (only if the owner is still online and the shop loaded: an absent owner is never punished)
        if (theft && !ctx.instance.resolved() && d.getBoolean("ts") && d.getLong("tv") > 0 && !d.getBoolean("tpaid")
                && ctx.owner() != null && ShopLife.zoneLoaded(ctx.world, ctx.zone)) {
            d.putBoolean("tpaid", true);
            long loss = Math.min(d.getLong("tv"), Math.max(0, ctx.zone.balance));
            if (loss > 0) {
                ctx.zone.balance -= loss;
                ctx.zone.stolen++;
                ctx.markDirty();
            }
            ctx.tellOwner(Component.translatable("tcgshop.actor.theft_lost", speciesName(d),
                    Component.literal(dev.tcgshop.pricing.PriceService.format(loss)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.RED), false);
        }
        sendAway(ctx, d, "ended");
        EventState st = EventManager.state();
        if (st != null) st.clearActorsOf(ctx.instance.uid);
    }

    /** All remaining ones walk away (and disappear). If the zone is not loaded there is nothing to remove: they were never saved. */
    private void sendAway(EventContext ctx, CompoundTag d, String how) {
        ListTag list = d.getList("a", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag a = list.getCompound(i);
            if (!a.hasUUID("u")) continue;
            UUID id = a.getUUID("u");
            if (a.getString("g").isEmpty()) a.putString("g", how);
            if (ActorModule.isLeaving(id)) continue;
            Entity e = ctx.world.getEntity(id);
            if (e != null && !e.isRemoved()) ActorModule.leave(ctx.world, e, awayFrom(ctx.world, ctx.zone, e, d), true);
            else ActorModule.forget(ctx.world, id);
        }
    }

    // ------------------------------------------------------------------ interaction (called by ActorModule from UseEntityCallback)

    /** The owner right-clicks an actor of this effect. Returns true if it did something. */
    boolean interact(ServerLevel w, ShopZones.Zone z, EventInstance inst, CompoundTag d, CompoundTag a, Entity e, ServerPlayer p) {
        long now = w.getGameTime();
        if (now - a.getLong("lc") < 8) return false;
        // mid-battle it cannot be shooed, fed or petted (the battle settles it)
        if (ActorModule.inBattle(e.getUUID())) return false;
        a.putLong("lc", now);
        ItemStack stack = p.getMainHandItem();
        MutableComponent name = speciesName(d);
        boolean negativeFix = fixWays();
        // battle (sneaking), only if the event allows it
        if (p.isShiftKeyDown() && resolve.contains("battle") && ActorConfig.INSTANCE.allowBattles) {
            if (ActorModule.inBattle(e.getUUID())) return false;
            if (ActorModule.startBattle(w, e, p)) {
                p.displayClientMessage(Component.translatable("tcgshop.actor.battle_start", name).withStyle(ChatFormatting.GOLD), true);
            } else p.displayClientMessage(Component.translatable("tcgshop.actor.battle_cant").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean berry = !stack.isEmpty() && itemId.getNamespace().equals("cobblemon") && itemId.getPath().endsWith("_berry");
        if (berry && (feed || resolve.contains("feed"))) {
            if (a.getBoolean("fed") && !resolve.contains("feed")) {
                p.displayClientMessage(Component.translatable("tcgshop.actor.full", name).withStyle(ChatFormatting.GRAY), true);
                return true;
            }
            if (!p.getAbilities().instabuild) stack.shrink(1);
            a.putBoolean("fed", true);
            w.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
            w.playSound(null, e.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 1.2f);
            pend(d, "feed");
            if (resolve.contains("feed")) {
                a.putString("g", "feed");
                ActorModule.leave(w, e, awayFrom(w, z, e, d), true);
                p.displayClientMessage(Component.translatable("tcgshop.actor.fed_leaves", name).withStyle(ChatFormatting.GREEN), true);
            } else p.displayClientMessage(Component.translatable("tcgshop.actor.fed", name).withStyle(ChatFormatting.GREEN), true);
            return true;
        }
        if (stack.isEmpty() && resolve.contains("shoo")) {
            int n = a.getInt("sh") + 1;
            a.putInt("sh", n);
            w.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
            w.playSound(null, e.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7f, 1.3f);
            if (n >= shooHits) {
                a.putString("g", "shoo");
                pend(d, "shoo");
                if (cry) CobblemonActors.cry(e);
                ActorModule.leave(w, e, awayFrom(w, z, e, d), false);
                p.displayClientMessage(Component.translatable("tcgshop.actor.shooed", name).withStyle(ChatFormatting.GREEN), true);
            } else {
                Vec3 push = e.position().subtract(p.position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4 && !ActorModule.isHeld(e)) e.push(push.normalize().scale(0.25).add(0, 0.2, 0));
                else e.push(0, 0.25, 0);
                e.hurtMarked = true;
                p.displayClientMessage(Component.translatable("tcgshop.actor.shoo_progress", name, n, shooHits).withStyle(ChatFormatting.YELLOW), true);
            }
            return true;
        }
        if (stack.isEmpty() && pet) {
            w.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
            if (!a.getBoolean("pet")) {
                a.putBoolean("pet", true);
                pend(d, "pet");
            }
            if (petLeaves) {
                a.putString("g", "pet");
                w.sendParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + 0.6, e.getZ(), 30, 0.4, 0.5, 0.4, 0.4);
                w.playSound(null, e.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6f, 1.4f);
                ActorModule.vanish(w, e);
                p.displayClientMessage(Component.translatable("tcgshop.actor.teleported", name).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            } else p.displayClientMessage(Component.translatable("tcgshop.actor.pet", name).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return true;
        }
        // hint about what works
        MutableComponent ways = Component.empty();
        int k = 0;
        if (resolve.contains("shoo")) { ways.append(Component.translatable("tcgshop.actor.way.shoo", shooHits)); k++; }
        if (feed || resolve.contains("feed")) { if (k++ > 0) ways.append(" · "); ways.append(Component.translatable("tcgshop.actor.way.feed")); }
        if (resolve.contains("battle") && ActorConfig.INSTANCE.allowBattles) { if (k++ > 0) ways.append(" · "); ways.append(Component.translatable("tcgshop.actor.way.battle")); }
        if (pet) { if (k++ > 0) ways.append(" · "); ways.append(Component.translatable("tcgshop.actor.way.pet")); }
        if (k > 0) p.displayClientMessage(Component.translatable("tcgshop.actor.hint", name, ways).withStyle(ChatFormatting.GRAY), true);
        return negativeFix || k > 0;
    }

    /** Battle over (called by ActorModule): won = the Pokémon fainted. */
    void battleOver(ServerLevel w, CompoundTag d, CompoundTag a, @Nullable Entity e, ServerPlayer p, boolean won) {
        MutableComponent name = speciesName(d);
        if (won) {
            a.putString("g", "battle");
            pend(d, "battle");
            if (e != null) ActorModule.vanish(w, e);
            if (p != null) p.displayClientMessage(Component.translatable("tcgshop.actor.battle_won", name).withStyle(ChatFormatting.GREEN), false);
        } else if (p != null) p.displayClientMessage(Component.translatable("tcgshop.actor.battle_lost", name).withStyle(ChatFormatting.GRAY), false);
    }

    private static void pend(CompoundTag d, String kind) {
        ListTag l = d.getList("pend", Tag.TAG_STRING);
        l.add(StringTag.valueOf(kind));
        d.put("pend", l);
    }

    /** Small reward (once per kind and event), always with the per-owner money cap. */
    private void giveReward(EventContext ctx, String kind) {
        Reward r = rewards.get(kind);
        CompoundTag d = ctx.data();
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

    MutableComponent speciesName(CompoundTag d) {
        String sp = d.getString("spn");
        if (sp.isEmpty()) sp = species.isEmpty() ? "pokemon" : species.get(0);
        return Component.translatable("cobblemon.species." + sp + ".name");
    }

    // ------------------------------------------------------------------ spots (only at start or with already cached data)

    /** Candidate floors inside the zone (random column sampling, not a full scan). */
    private static List<BlockPos> sampleFloor(ServerLevel w, ShopZones.Zone z, Random r) {
        int half = z.size() / 2;
        List<BlockPos> roofed = new ArrayList<>(), open = new ArrayList<>();
        int y0 = z.core.getY();
        for (int k = 0; k < 220 && roofed.size() < 40; k++) {
            int x = z.core.getX() - half + 1 + r.nextInt(Math.max(1, half * 2 - 1));
            int zz = z.core.getZ() - half + 1 + r.nextInt(Math.max(1, half * 2 - 1));
            for (int dy : new int[]{0, 1, -1, 2, -2, 3}) {
                BlockPos p = new BlockPos(x, y0 + dy, zz);
                if (!z.contains(p) || !standable(w, p) || ShopZones.isShopRoof(w, p)) continue; // 1.1: never on the roof
                (w.canSeeSky(p.above()) ? open : roofed).add(p);
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

    static boolean standable(ServerLevel w, BlockPos p) {
        BlockState below = w.getBlockState(p.below());
        if (!below.isFaceSturdy(w, p.below(), Direction.UP)) return false;
        if (below.getBlock() instanceof DoorBlock) return false;
        return w.getBlockState(p).getCollisionShape(w, p).isEmpty() && w.getBlockState(p.above()).getCollisionShape(w, p.above()).isEmpty()
                && w.getFluidState(p).isEmpty() && !(w.getBlockState(p).getBlock() instanceof DoorBlock);
    }

    @Nullable
    private static BlockPos randomFloor(CompoundTag d, Random r) {
        long[] fl = d.getLongArray("fl");
        return fl.length == 0 ? null : BlockPos.of(fl[r.nextInt(fl.length)]);
    }

    @Nullable
    private static BlockPos farFloor(CompoundTag d, BlockPos from) {
        long[] fl = d.getLongArray("fl");
        BlockPos best = null;
        double bd = -1;
        for (long l : fl) {
            BlockPos p = BlockPos.of(l);
            double dd = p.distSqr(from);
            if (dd > bd) { bd = dd; best = p; }
        }
        return best;
    }

    @Nullable
    private static BlockPos brightest(ServerLevel w, List<BlockPos> floor) {
        BlockPos best = null;
        int bl = -1;
        for (BlockPos p : floor) {
            int l = w.getBrightness(LightLayer.BLOCK, p);
            if (l > bl) { bl = l; best = p; }
        }
        return best;
    }

    /** Outer side of a door (same criterion as ShopLife: the one facing the sky or the farthest from the core). */
    static Direction outward(ServerLevel w, ShopZones.Zone z, BlockPos door) {
        BlockState s = w.getBlockState(door);
        Direction f = s.hasProperty(DoorBlock.FACING) ? s.getValue(DoorBlock.FACING) : Direction.NORTH;
        boolean a = w.canSeeSky(door.relative(f).above()), b = w.canSeeSky(door.relative(f.getOpposite()).above());
        if (a != b) return a ? f : f.getOpposite();
        double da = door.relative(f).distSqr(z.core), db = door.relative(f.getOpposite()).distSqr(z.core);
        return da >= db ? f : f.getOpposite();
    }

    /** Where each actor spawns based on its behaviour (inside the zone, on solid ground and with room for its size). */
    @Nullable
    private Vec3 spawnSpot(ServerLevel w, ShopZones.Zone z, Entity e, List<BlockPos> floor, @Nullable BlockPos door, @Nullable Direction out,
                            Set<Long> used, Random r) {
        List<BlockPos> cands = new ArrayList<>();
        AABB forbid = null;
        if (door != null && out != null) {
            Direction side = out.getClockWise();
            // never in the doorway nor in the block in front/behind (so nobody gets trapped)
            forbid = new AABB(door).minmax(new AABB(door.relative(out))).minmax(new AABB(door.relative(out.getOpposite()))).inflate(0.05, 0, 0.05).expandTowards(0, 1, 0);
            switch (behaviour) {
                case OUTSIDE, BLOCK_DOOR -> {
                    for (int dist : new int[]{2, 3}) for (int lat : new int[]{1, -1, 2, -2, 0}) cands.add(door.relative(out, dist).relative(side, lat));
                    for (int dist : new int[]{2, 3}) for (int lat : new int[]{1, -1, 2, -2}) cands.add(door.relative(out.getOpposite(), dist).relative(side, lat));
                }
                case GOTO_DISPLAY, GOTO_REGISTER, GOTO_LIGHT, GOTO_DOOR -> {
                    for (int dist : new int[]{2, 3}) for (int lat : new int[]{0, 1, -1}) cands.add(door.relative(out.getOpposite(), dist).relative(side, lat));
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
                BlockPos p = c.above(dy);
                if (!z.contains(p) || !standable(w, p) || ShopZones.isShopRoof(w, p)) continue; // 1.1: never on the roof
                Vec3 v = Vec3.atBottomCenterOf(p);
                e.moveTo(v.x, v.y, v.z, 0, 0);
                AABB bb = e.getBoundingBox();
                if (forbid != null && bb.intersects(forbid)) continue;
                if (!w.noCollision(e, bb)) continue;
                if (!z.box().contains(v)) continue;
                used.add(c.asLong());
                return v;
            }
        }
        return null;
    }

    /** Free spot next to a target block (display, register, light), the closest one. */
    @Nullable
    private static Vec3 spotNear(ServerLevel w, ShopZones.Zone z, Entity e, BlockPos target, Random r) {
        Vec3 best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos p = target.offset(dx, dy, dz);
                    if (!z.contains(p) || !standable(w, p)) continue;
                    double dd = p.distSqr(target) + r.nextDouble() * 0.1;
                    if (dd < bd) { bd = dd; best = Vec3.atBottomCenterOf(p); }
                }
        return best;
    }

    record Target(BlockPos pos, int slot) {}

    /** A stocked display (the cheapest one if it is a theft; otherwise a random one). */
    @Nullable
    private static Target pickDisplay(ServerLevel w, ShopZones.Zone z, Random r, boolean cheapest) {
        List<Target> all = new ArrayList<>();
        Target cheap = null;
        long cv = Long.MAX_VALUE;
        Mods mods = EventManager.mods(z);
        for (BlockEntity be : zoneBlockEntities(w, z)) {
            if (!(be instanceof ShopDisplayBlockEntity d) || d.isHome()) continue;
            for (int i = 0; i < d.getContainerSize(); i++) {
                ItemStack st = d.getItem(i);
                if (st.isEmpty() || mods.isDisplayDisabled(d.getBlockPos(), i)) continue;
                Target t = new Target(d.getBlockPos().immutable(), i);
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
    private static BlockPos findRegister(ServerLevel w, ShopZones.Zone z) {
        for (BlockEntity be : zoneBlockEntities(w, z))
            if (be.getBlockState().is(TcgBlocks.CASH_REGISTER)) return be.getBlockPos().immutable();
        return null;
    }

    private static List<BlockEntity> zoneBlockEntities(ServerLevel w, ShopZones.Zone z) {
        List<BlockEntity> out = new ArrayList<>();
        int half = z.size() / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk ch = w.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) if (z.contains(be.getBlockPos())) out.add(be);
            }
        return out;
    }

    /** Where it heads when leaving: out through the door if there is one; otherwise away from the owner. */
    static Vec3 awayFrom(ServerLevel w, ShopZones.Zone z, Entity e, CompoundTag d) {
        if (d.contains("door")) {
            BlockPos door = BlockPos.of(d.getLong("door"));
            Direction out = Direction.from3DDataValue(d.getInt("dout"));
            return Vec3.atBottomCenterOf(door.relative(out, 7));
        }
        ServerPlayer owner = w.getServer().getPlayerList().getPlayer(z.owner);
        Vec3 dir = owner == null ? new Vec3(1, 0, 0) : e.position().subtract(owner.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-4) dir = new Vec3(1, 0, 0);
        return e.position().add(dir.normalize().scale(9));
    }
}
