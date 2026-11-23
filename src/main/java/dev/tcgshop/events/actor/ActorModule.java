package dev.tcgshop.events.actor;

import dev.tcgshop.TcgShop;
import dev.tcgshop.compat.CobblemonActors;
import dev.tcgshop.compat.TempPokemon;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.events.EffectTypes;
import dev.tcgshop.events.EventConditions;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventInstance;
import dev.tcgshop.events.EventManager;
import dev.tcgshop.events.EventRegistry;
import dev.tcgshop.events.EventState;
import dev.tcgshop.shop.ShopZones;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Actor module: REAL Cobblemon Pokémon (via reflection, compat/CobblemonActors) that visit, block or
 * help in the shop during an event. Registers the "actor" effect type ({@link ActorEffect}), the conditions'
 * nearby-Pokémon predicate and all the hooks:
 *  · Cobblemon hooks: no saving to the world, no capture and no loot (actors and the mod's temporary Pokémon);
 *    if the "no save" one cannot be installed, NO actor is spawned (warned once in the log);
 *  · ENTITY_LOAD: an actor (or temporary Pokémon) that shows up loaded without being alive in the registry is removed on
 *    the next tick (never inside the load event itself);
 *  · UseEntityCallback: only the owner (or an operator) shoos, feeds, pets or challenges (sneaking) an actor;
 *  · AttackEntityCallback: actors cannot be hit;
 *  · own tick: those walking away, those standing still facing the player, and battles in progress.
 * None of this affects the tutorial Meowth (different tags) or the world's normal Pokémon.
 */
public final class ActorModule {
    /** The actors' own tag (in addition to EventManager.EVENT_TAG and "tcgshop_ev_&lt;uid&gt;"). */
    public static final String ACTOR_TAG = "tcgshop_actor";

    private record Ref(long uid, int index) {}

    private record Leaving(RegistryKey<World> world, long until, Vec3d target) {}

    private record Battle(RegistryKey<World> world, UUID player, Object pokemon, long start, boolean wasHeld) {}

    /** Live actors of this session: uuid -> (event, effect index). Lost on stop (so are the actors). */
    private static final Map<UUID, Ref> RUNTIME = new HashMap<>();
    private static final Map<UUID, Leaving> LEAVING = new HashMap<>();
    /** Stationary actors facing the nearest player (uuid -> world). */
    private static final Map<UUID, RegistryKey<World>> HELD = new HashMap<>();
    private static final Map<UUID, Battle> BATTLES = new HashMap<>();
    /** Departure requested mid-battle: applied when the battle ends (target null = disappear right away). */
    private record PendingLeave(@Nullable Vec3d target, boolean happy) {}
    private static final Map<UUID, PendingLeave> PENDING_LEAVE = new HashMap<>();
    private static final ArrayDeque<Object[]> PURGE = new ArrayDeque<>();
    private static boolean registered, hooks;
    private static Method leaveForEvent;
    private static boolean leaveLookedUp;

    private ActorModule() {}

    /** Server and common (TcgShop.onInitialize, before datapacks load). */
    public static void init() {
        if (registered) return;
        registered = true;
        ActorConfig.load();
        EffectTypes.register("actor", ActorEffect::new);
        EventConditions.setNearbyPokemon(ActorModule::nearbyPokemon);
        if (CobblemonActors.available()) {
            hooks = CobblemonActors.installHooks(ActorModule::noSave, ActorModule::isOurs, ActorModule::isOurs);
            if (!hooks) TcgShop.LOGGER.warn("[events] actors disabled: could not prevent them from being saved to the world");
        }
        ServerTickEvents.END_SERVER_TICK.register(ActorModule::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(ActorModule::onStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ActorModule::onStopping);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            var tags = entity.getCommandTags();
            if (tags.isEmpty()) return;
            boolean orphan = false;
            if (tags.contains(ACTOR_TAG)) {
                EventState st = EventManager.state();
                orphan = st == null || !st.isLiveActor(entity.getUuid());
            } else if (tags.contains(TempPokemon.TAG)) orphan = !TempPokemon.isLive(entity.getUuid());
            if (orphan) PURGE.add(new Object[]{world.getRegistryKey(), entity.getUuid()});
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            if (!entity.getCommandTags().contains(ACTOR_TAG)) return ActionResult.PASS;
            return onUse(sw, sp, hand, entity);
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            return entity.getCommandTags().contains(ACTOR_TAG) || TempPokemon.isTemp(entity) ? ActionResult.FAIL : ActionResult.PASS;
        });
    }

    /** Can actors be spawned now? (Cobblemon, save hook installed and config) */
    public static boolean ready() {
        return hooks && CobblemonActors.hooksOk() && ActorConfig.INSTANCE.enabled && ActorConfig.INSTANCE.maxPerShop > 0;
    }

    /** For EventConditions ("actors" in requires): same as {@link #ready()}. */
    public static boolean available() { return ready(); }

    private static boolean isOurs(Entity e) {
        var t = e.getCommandTags();
        return t.contains(ACTOR_TAG) || t.contains(EventManager.EVENT_TAG) || t.contains(TempPokemon.TAG);
    }

    private static boolean noSave(Entity e) { return isOurs(e); }

    // ------------------------------------------------------------------ spawn / leave

    /**
     * Puts an actor into the world with all the rules: tags, invulnerable, persistent (but never saved: the
     * hook prevents it), not battleable, registered BEFORE spawnEntity. false if it failed (and nothing is left behind).
     */
    static boolean spawn(ServerWorld w, Entity e, long uid, int index, boolean hold) {
        EventState st = EventState.get(w.getServer());
        e.addCommandTag(EventManager.EVENT_TAG);
        e.addCommandTag("tcgshop_ev_" + uid);
        e.addCommandTag(ACTOR_TAG);
        e.setInvulnerable(true);
        if (e instanceof MobEntity mob) {
            mob.setPersistent();
            if (hold) mob.setAiDisabled(true);
        }
        CobblemonActors.setUnbattleable(e, true);
        CobblemonActors.noSpawnCap(e);
        st.registerActor(e.getUuid(), uid);
        RUNTIME.put(e.getUuid(), new Ref(uid, index));
        boolean ok;
        try {
            ok = w.spawnEntity(e);
        } catch (Throwable t) {
            ok = false;
        }
        if (!ok) {
            st.unregisterActor(e.getUuid());
            RUNTIME.remove(e.getUuid());
            return false;
        }
        if (hold) HELD.put(e.getUuid(), w.getRegistryKey());
        return true;
    }

    /** Walks away toward target and disappears (with a cloud) on arrival or after leaveTicks. */
    static void leave(ServerWorld w, Entity e, Vec3d target, boolean happy) {
        // in battle: it is not pulled out of it; it leaves when the battle ends (battleTick)
        if (BATTLES.containsKey(e.getUuid())) {
            PENDING_LEAVE.put(e.getUuid(), new PendingLeave(target, happy));
            return;
        }
        forget(w, e.getUuid());
        if (e instanceof MobEntity mob) mob.setAiDisabled(false);
        walkTo(e, target, 1.25);
        if (happy) w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getHeight(), e.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
        LEAVING.put(e.getUuid(), new Leaving(w.getRegistryKey(), w.getTime() + ActorConfig.INSTANCE.leaveTicks, target));
    }

    /** Disappears right away (cloud). */
    static void vanish(ServerWorld w, Entity e) {
        if (BATTLES.containsKey(e.getUuid())) {
            PENDING_LEAVE.put(e.getUuid(), new PendingLeave(null, false));
            return;
        }
        forget(w, e.getUuid());
        LEAVING.remove(e.getUuid());
        w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        e.discard();
    }

    /** Removes it from the registries (it is no longer a "live" actor of the event). */
    static void forget(ServerWorld w, UUID id) {
        RUNTIME.remove(id);
        HELD.remove(id);
        BATTLES.remove(id);
        PENDING_LEAVE.remove(id);
        EventState st = EventManager.state();
        if (st != null) st.unregisterActor(id);
    }

    static boolean isLeaving(UUID id) { return LEAVING.containsKey(id); }

    static boolean inBattle(UUID id) { return BATTLES.containsKey(id); }

    static boolean isHeld(Entity e) { return HELD.containsKey(e.getUuid()); }

    /** Stationary and facing the nearest player (the module tick turns it). */
    static void hold(Entity e, ServerWorld w) {
        if (e instanceof MobEntity mob) {
            mob.getNavigation().stop();
            if (!mob.isAiDisabled()) mob.setAiDisabled(true);
        }
        HELD.put(e.getUuid(), w.getRegistryKey());
    }

    static void release(Entity e) {
        HELD.remove(e.getUuid());
        if (e instanceof MobEntity mob && mob.isAiDisabled()) mob.setAiDisabled(false);
    }

    /** Walk to a point: the brain's WALK_TARGET if it has one (Cobblemon 1.8 uses a brain), otherwise navigation. */
    static void walkTo(Entity e, Vec3d t, double speed) {
        if (!(e instanceof MobEntity mob)) return;
        try {
            var brain = mob.getBrain();
            if (brain.hasMemoryModule(MemoryModuleType.WALK_TARGET)) {
                brain.remember(MemoryModuleType.WALK_TARGET, new WalkTarget(t, (float) speed, 1));
                return;
            }
        } catch (Throwable ignored) {}
        mob.getNavigation().startMovingTo(t.x, t.y, t.z, speed);
    }

    // ------------------------------------------------------------------ interaction

    private static ActionResult onUse(ServerWorld w, ServerPlayerEntity p, Hand hand, Entity e) {
        Ref ref = RUNTIME.get(e.getUuid());
        if (ref == null) return ActionResult.FAIL; // it is leaving or a leftover: do not touch
        EventInstance inst = EventManager.instance(ref.uid);
        ShopZones.Zone z = inst == null ? null : ShopZones.get(w).byId(inst.zoneId);
        if (inst == null || z == null) return ActionResult.FAIL;
        if (!z.owner.equals(p.getUuid()) && !p.hasPermissionLevel(2)) {
            if (hand == Hand.MAIN_HAND) p.sendMessage(Text.translatable("tcgshop.actor.only_owner").formatted(Formatting.GRAY), true);
            return ActionResult.FAIL;
        }
        if (hand != Hand.MAIN_HAND) return ActionResult.SUCCESS;
        EventDef def = EventRegistry.get(inst.defId);
        if (def == null) return ActionResult.FAIL;
        List<EventEffect> effs = def.effectsFor(inst.variant);
        if (ref.index < 0 || ref.index >= effs.size() || !(effs.get(ref.index) instanceof ActorEffect ae)) return ActionResult.FAIL;
        String k = "e" + ref.index;
        if (!inst.data.contains(k)) return ActionResult.FAIL;
        NbtCompound d = inst.data.getCompound(k);
        NbtCompound a = entry(d, e.getUuid());
        if (a == null || !a.getString("g").isEmpty()) return ActionResult.SUCCESS;
        if (ae.interact(w, z, inst, d, a, e, p)) ShopZones.get(w).markDirty();
        return ActionResult.SUCCESS;
    }

    @Nullable
    private static NbtCompound entry(NbtCompound d, UUID id) {
        NbtList list = d.getList("a", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound a = list.getCompound(i);
            if (a.containsUuid("u") && a.getUuid("u").equals(id)) return a;
        }
        return null;
    }

    static boolean startBattle(ServerWorld w, Entity e, ServerPlayerEntity p) {
        boolean wasHeld = HELD.containsKey(e.getUuid());
        release(e);
        Object pk = CobblemonActors.pokemonOf(e);
        if (pk == null || !CobblemonActors.startBattle(p, e)) {
            if (wasHeld) hold(e, w);
            return false;
        }
        BATTLES.put(e.getUuid(), new Battle(w.getRegistryKey(), p.getUuid(), pk, w.getTime(), wasHeld));
        return true;
    }

    // ------------------------------------------------------------------ module tick

    private static void tick(MinecraftServer s) {
        while (!PURGE.isEmpty()) {
            Object[] x = PURGE.poll();
            @SuppressWarnings("unchecked") ServerWorld w = s.getWorld((RegistryKey<World>) x[0]);
            Entity e = w == null ? null : w.getEntity((UUID) x[1]);
            if (e == null || e.isRemoved()) continue;
            var tags = e.getCommandTags();
            EventState st = EventManager.state();
            boolean live = tags.contains(ACTOR_TAG) ? st != null && st.isLiveActor(e.getUuid()) : TempPokemon.isLive(e.getUuid());
            if (!live) {
                TcgShop.LOGGER.info("[events] orphaned event Pokémon removed on load ({})", e.getUuid());
                e.discard();
            }
        }
        int t = s.getTicks();
        if (t % 4 == 0 && !HELD.isEmpty()) faceTick(s);
        if (t % 5 == 0 && !LEAVING.isEmpty()) leavingTick(s);
        if (t % 10 == 0 && !BATTLES.isEmpty()) battleTick(s);
    }

    private static void faceTick(MinecraftServer s) {
        Iterator<Map.Entry<UUID, RegistryKey<World>>> it = HELD.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, RegistryKey<World>> en = it.next();
            ServerWorld w = s.getWorld(en.getValue());
            Entity e = w == null ? null : w.getEntity(en.getKey());
            if (e == null || e.isRemoved()) { it.remove(); continue; }
            ServerPlayerEntity near = null;
            double best = 8 * 8;
            for (ServerPlayerEntity p : w.getPlayers()) {
                double dd = p.squaredDistanceTo(e);
                if (dd < best && !p.isSpectator()) { best = dd; near = p; }
            }
            if (near != null) face(e, near, 25f);
            e.setVelocity(0, Math.min(0, e.getVelocity().y), 0);
        }
    }

    private static void leavingTick(MinecraftServer s) {
        Iterator<Map.Entry<UUID, Leaving>> it = LEAVING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Leaving> en = it.next();
            Leaving l = en.getValue();
            ServerWorld w = s.getWorld(l.world());
            Entity e = w == null ? null : w.getEntity(en.getKey());
            if (e == null || e.isRemoved()) { it.remove(); continue; }
            long now = w.getTime();
            if (now >= l.until() || e.squaredDistanceTo(l.target()) < 2.5) {
                w.spawnParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
                e.discard();
                it.remove();
            } else if (now % 20 < 5) walkTo(e, l.target(), 1.25);
        }
    }

    private static void battleTick(MinecraftServer s) {
        Iterator<Map.Entry<UUID, Battle>> it = BATTLES.entrySet().iterator();
        List<Object[]> done = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Battle> en = it.next();
            Battle b = en.getValue();
            ServerWorld w = s.getWorld(b.world());
            Entity e = w == null ? null : w.getEntity(en.getKey());
            long age = w == null ? Long.MAX_VALUE : w.getTime() - b.start();
            boolean battling = e != null && !e.isRemoved() && CobblemonActors.isBattling(e);
            // while Cobblemon says it is still in battle it is not released, however long it takes (no cap)
            if (battling) continue;
            if (!battling && e != null && !e.isRemoved() && age < 40) continue; // the battle is still being set up
            it.remove();
            done.add(new Object[]{en.getKey(), b, w, e});
        }
        for (Object[] x : done) {
            UUID id = (UUID) x[0];
            Battle b = (Battle) x[1];
            ServerWorld w = (ServerWorld) x[2];
            Entity e = (Entity) x[3];
            boolean won = CobblemonActors.isFainted(b.pokemon());
            Ref ref = RUNTIME.get(id);
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(b.player());
            if (w == null || ref == null) {
                PENDING_LEAVE.remove(id);
                if (e != null && !e.isRemoved() && won) e.discard();
                continue;
            }
            EventInstance inst = EventManager.instance(ref.uid);
            EventDef def = inst == null ? null : EventRegistry.get(inst.defId);
            List<EventEffect> effs = def == null ? List.of() : def.effectsFor(inst.variant);
            if (inst == null || ref.index >= effs.size() || !(effs.get(ref.index) instanceof ActorEffect ae) || !inst.data.contains("e" + ref.index)) {
                if (e != null && !e.isRemoved()) { if (!applyPendingLeave(w, e)) vanish(w, e); }
                else PENDING_LEAVE.remove(id);
                continue;
            }
            NbtCompound d = inst.data.getCompound("e" + ref.index);
            NbtCompound a = entry(d, id);
            if (a != null) ae.battleOver(w, d, a, e == null || e.isRemoved() ? null : e, p, won);
            boolean alive = e != null && !e.isRemoved();
            // departure requested during the battle (shooed, event end, guard): applied now
            boolean left = alive && applyPendingLeave(w, e);
            if (!alive) PENDING_LEAVE.remove(id);
            if (!won && !left && alive && b.wasHeld()) hold(e, w);
            if (won && e == null) forget(w, id);
            ShopZones.get(w).markDirty();
        }
    }

    /** Applies the departure requested mid-battle (if any). true if it has left / disappeared. */
    private static boolean applyPendingLeave(ServerWorld w, Entity e) {
        PendingLeave pl = PENDING_LEAVE.remove(e.getUuid());
        if (pl == null || e.isRemoved()) return pl != null;
        if (pl.target() == null) vanish(w, e);
        else leave(w, e, pl.target(), pl.happy());
        return true;
    }

    /** Turns body, head and gaze toward the player (like the tutorial Meowth). */
    private static void face(Entity e, ServerPlayerEntity p, float maxStep) {
        double dx = p.getX() - e.getX(), dz = p.getZ() - e.getZ();
        double dy = p.getEyeY() - e.getEyeY();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1e-3) return;
        float target = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitchT = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, horiz)), -40f, 40f);
        float cur = e instanceof LivingEntity le ? le.getHeadYaw() : e.getYaw();
        float yaw = cur + MathHelper.clamp(MathHelper.wrapDegrees(target - cur), -maxStep, maxStep);
        float pitch = e.getPitch() + MathHelper.clamp(pitchT - e.getPitch(), -maxStep, maxStep);
        e.setYaw(yaw);
        e.setPitch(pitch);
        if (e instanceof LivingEntity le) {
            le.setHeadYaw(yaw);
            le.setBodyYaw(yaw);
        }
    }

    // ------------------------------------------------------------------ startup / shutdown

    private static void onStarted(MinecraftServer s) {
        ActorConfig.load();
        if (!hooks && CobblemonActors.available()) {
            hooks = CobblemonActors.installHooks(ActorModule::noSave, ActorModule::isOurs, ActorModule::isOurs);
            if (hooks) TcgShop.LOGGER.info("[events] Cobblemon actor hooks installed on server start");
        }
        RUNTIME.clear();
        LEAVING.clear();
        HELD.clear();
        BATTLES.clear();
        PENDING_LEAVE.clear();
        PURGE.clear();
        // actors are never saved: any listed in the registry from a previous session no longer exist
        EventState st = EventState.get(s);
        for (ServerWorld w : s.getWorlds())
            for (ShopZones.Zone z : ShopZones.get(w).all())
                for (EventInstance e : z.events.active) if (!st.actorsOf(e.uid).isEmpty()) st.clearActorsOf(e.uid);
    }

    private static void onStopping(MinecraftServer s) {
        Set<UUID> all = new HashSet<>(RUNTIME.keySet());
        all.addAll(LEAVING.keySet());
        for (ServerWorld w : s.getWorlds())
            for (UUID id : all) {
                Entity e = w.getEntity(id);
                if (e != null && !e.isRemoved()) e.discard();
            }
        EventState st = EventState.get(s);
        for (UUID id : RUNTIME.keySet()) st.unregisterActor(id);
        RUNTIME.clear();
        LEAVING.clear();
        HELD.clear();
        BATTLES.clear();
        PENDING_LEAVE.clear();
        PURGE.clear();
        TempPokemon.clear();
    }

    // ------------------------------------------------------------------ "nearbyPokemon" condition

    /** Real WILD Pokémon near the shop (ownerless, not counting the mod's actors or temporary ones). */
    private static boolean nearbyPokemon(ServerWorld w, ShopZones.Zone z, List<String> species, int radius, int min) {
        if (!CobblemonActors.available()) return false;
        Set<String> want = new HashSet<>();
        for (String sp : species) want.add(CobblemonActors.normalize(sp));
        int n = 0;
        for (Entity e : w.getOtherEntities(null, z.box().expand(radius),
                x -> Registries.ENTITY_TYPE.getId(x.getType()).getNamespace().equals("cobblemon"))) {
            if (isOurs(e)) continue;
            if (e instanceof TameableEntity t && t.getOwnerUuid() != null) continue;
            if (!want.isEmpty() && !want.contains(CobblemonActors.speciesOf(e))) continue;
            if (++n >= min) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ utilities

    /**
     * A customer leaves the normal way (returns what it was carrying). Uses CustomerEntity.leaveForEvent(String) if
     * it exists (hook requested from the orchestrator); otherwise does nothing (the event's modifiers already penalize it).
     */
    static boolean customerLeave(CustomerEntity c, String reviewKey) {
        if (!leaveLookedUp) {
            leaveLookedUp = true;
            try {
                leaveForEvent = CustomerEntity.class.getMethod("leaveForEvent", String.class);
            } catch (NoSuchMethodException e) {
                TcgShop.LOGGER.info("[events] CustomerEntity.leaveForEvent does not exist: customers will not leave because of the singing");
            }
        }
        if (leaveForEvent == null) return false;
        try {
            return Boolean.TRUE.equals(leaveForEvent.invoke(c, reviewKey));
        } catch (Throwable e) {
            return false;
        }
    }

    @Nullable
    static ParticleEffect particle(String name) {
        return switch (name) {
            case "splash" -> ParticleTypes.SPLASH;
            case "drip" -> ParticleTypes.FALLING_WATER;
            case "spark" -> ParticleTypes.ELECTRIC_SPARK;
            case "note" -> ParticleTypes.NOTE;
            case "heart" -> ParticleTypes.HEART;
            case "smoke" -> ParticleTypes.SMOKE;
            case "flame" -> ParticleTypes.SMALL_FLAME;
            case "leaf" -> ParticleTypes.COMPOSTER;
            case "cherry" -> ParticleTypes.CHERRY_LEAVES;
            case "angry" -> ParticleTypes.ANGRY_VILLAGER;
            case "glow" -> ParticleTypes.END_ROD;
            case "wax" -> ParticleTypes.WAX_ON;
            case "happy" -> ParticleTypes.HAPPY_VILLAGER;
            case "cloud" -> ParticleTypes.CLOUD;
            case "dust" -> ParticleTypes.WHITE_ASH;
            case "nectar" -> ParticleTypes.FALLING_NECTAR;
            case "spore" -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case "portal" -> ParticleTypes.PORTAL;
            case "bubble" -> ParticleTypes.BUBBLE_POP;
            default -> null;
        };
    }

    /** Small loot from a tcgshop:events/* table (never cards, packs or shop items), into the owner's inventory. */
    static void giveLoot(ServerWorld w, ShopZones.Zone z, @Nullable ServerPlayerEntity owner, String loot, int rolls) {
        if (owner == null) return;
        Identifier id = Identifier.tryParse(loot);
        if (id == null || !id.getNamespace().equals("tcgshop") || !id.getPath().startsWith("events/")) return;
        LootTable table = dev.tcgshop.events.effect.RewardEffect.lootTable(w.getServer(), id);
        if (table == LootTable.EMPTY) return;
        LootContextParameterSet params = new LootContextParameterSet.Builder(w)
                .add(LootContextParameters.ORIGIN, z.core.toCenterPos())
                .build(LootContextTypes.CHEST);
        int n = 0;
        for (int k = 0; k < rolls; k++) {
            for (ItemStack st : table.generateLoot(params)) {
                if (st.isEmpty() || !safe(st)) continue;
                n += st.getCount();
                owner.getInventory().offerOrDrop(st);
            }
        }
        if (n > 0) {
            owner.sendMessage(Text.translatable("tcgshop.event.reward.items", n).formatted(Formatting.GREEN), false);
            owner.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.6f, 1.2f);
        }
    }

    private static boolean safe(ItemStack st) {
        if (dev.masterball.api.card.CardInfo.isCard(st) || dev.masterball.api.card.CardInfo.isPack(st)) return false;
        String ns = Registries.ITEM.getId(st.getItem()).getNamespace();
        return !ns.equals("tcgshop") && !ns.equals(dev.masterball.api.card.CardInfo.NS);
    }
}
