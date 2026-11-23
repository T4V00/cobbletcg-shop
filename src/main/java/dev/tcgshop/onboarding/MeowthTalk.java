package dev.tcgshop.onboarding;

import dev.tcgshop.compat.CobblemonActors;
import dev.tcgshop.network.TutorialPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The "Team Rocket Meowth" talk: a SHORT series of speech bubbles over its head (the same bubbles as the shop
 * NPCs, ClientSpeech) that only the shop owner sees. While talking, the Meowth stays still (AI
 * disabled, not navigating) and looks at the player; right-clicking it advances to the next bubble. When done it goes back to
 * being a normal, catchable wild Meowth.
 * <p>
 * If the player moves more than {@link #PAUSE_DIST} blocks away the talk pauses (and resumes on return); if it takes
 * too long, the player disconnects, the Meowth despawns/is caught or enters battle, it ends cleanly. The disabled
 * AI is saved in the Meowth's NBT: that is why it carries the {@link #FROZEN_TAG} tag while frozen and, if
 * the server shut down midway (or crashed), its AI is restored when it loads.
 */
public final class MeowthTalk {
    /** Entity tag of the tutorial Meowth (to find it with /tcgshop tutorial and not spawn another). */
    public static final String MEOWTH_TAG = "tcgshop_tutorial_meowth";
    /** Prefix of the owner tag: tcgshop_meowth_owner_<uuid>. */
    public static final String OWNER_PREFIX = "tcgshop_meowth_owner_";
    /** Set while frozen by the talk. */
    public static final String FROZEN_TAG = "tcgshop_meowth_frozen";

    /** Talk bubbles (tcgshop.tutorial.bubble.1 .. N). */
    public static final int LINES = 8;
    public static final double PAUSE_DIST = 24, RESUME_DIST = 16, FIND_DIST = 24;
    /** Paused for more than 1 minute: it ends. */
    public static final int MAX_PAUSE = 20 * 60;
    /** Ticks before the first bubble (to see the Meowth appear). */
    public static final int FIRST_DELAY = 20;

    private static final class Session {
        final UUID player, meowth;
        final RegistryKey<World> world;
        final boolean wasAiDisabled;
        int index = -1, left = FIRST_DELAY, paused;
        long lastClick = -100;

        Session(UUID player, UUID meowth, RegistryKey<World> world, boolean wasAiDisabled) {
            this.player = player;
            this.meowth = meowth;
            this.world = world;
            this.wasAiDisabled = wasAiDisabled;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<Class<?>, Method> BATTLING = new HashMap<>();
    /** Pokemon.isWild() / Pokemon.getOwnerUUID() per class (stored null = does not exist). */
    private static final Map<Class<?>, Method> IS_WILD = new HashMap<>(), OWNER_UUID = new HashMap<>();
    private static boolean registered;

    private MeowthTalk() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(MeowthTalk::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session s = SESSIONS.remove(handler.getPlayer().getUuid());
            if (s != null) release(server, s, false);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Session s : SESSIONS.values()) release(server, s, false);
            SESSIONS.clear();
        });
        // Meowth left frozen (server shut down or crashed in the middle of the talk): restore its AI
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!entity.getCommandTags().contains(FROZEN_TAG)) return;
            for (Session s : SESSIONS.values()) if (s.meowth.equals(entity.getUuid())) return;
            entity.removeCommandTag(FROZEN_TAG);
            if (entity instanceof MobEntity mob) mob.setAiDisabled(false);
        });
        // owner right-clicks the Meowth: next bubble (and does nothing else while it talks)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            Session s = SESSIONS.get(sp.getUuid());
            if (s == null || !entity.getUuid().equals(s.meowth)) return ActionResult.PASS;
            long now = world.getTime();
            if (hand == Hand.MAIN_HAND && now - s.lastClick >= 8 && s.index >= 0 && s.paused == 0) {
                s.lastClick = now;
                s.left = 0; // the next tick moves on to the next bubble
            }
            return ActionResult.SUCCESS;
        });
    }

    public static boolean talking(ServerPlayerEntity player) {
        return SESSIONS.containsKey(player.getUuid());
    }

    /** Ends this player's talk (if any) and restores its Meowth's AI. */
    public static void stop(ServerPlayerEntity player) {
        Session s = SESSIONS.remove(player.getUuid());
        if (s != null && player.getServer() != null) release(player.getServer(), s, false);
    }

    /** Marks the entity as this player's tutorial Meowth. */
    public static void tag(Entity meowth, ServerPlayerEntity owner) {
        meowth.addCommandTag(MEOWTH_TAG);
        meowth.addCommandTag(OWNER_PREFIX + owner.getUuid());
    }

    /** Starts (or restarts) {@code meowth}'s talk for {@code player}. */
    public static boolean start(ServerPlayerEntity player, Entity meowth) {
        if (!(meowth instanceof LivingEntity) || meowth.isRemoved()) return false;
        MinecraftServer server = player.getServer();
        Session old = SESSIONS.remove(player.getUuid());
        boolean wasDisabled = meowth instanceof MobEntity mob && mob.isAiDisabled() && !meowth.getCommandTags().contains(FROZEN_TAG);
        if (old != null) {
            if (old.meowth.equals(meowth.getUuid())) wasDisabled = old.wasAiDisabled;
            else if (server != null) release(server, old, false);
        }
        // another player is already listening to this same Meowth: don't overlap
        for (Session s : SESSIONS.values()) if (s.meowth.equals(meowth.getUuid())) return false;
        Session s = new Session(player.getUuid(), meowth.getUuid(), meowth.getWorld().getRegistryKey(), wasDisabled);
        SESSIONS.put(player.getUuid(), s);
        freeze(meowth);
        face(meowth, player, 180f);
        return true;
    }

    /**
     * This player's tutorial Meowth near them (or, for pre-0.9 worlds, any Pokémon with the
     * nickname "Meowth del Team Rocket"), or null.
     */
    public static Entity findNear(ServerPlayerEntity player) {
        String owner = OWNER_PREFIX + player.getUuid();
        List<LivingEntity> list = player.getServerWorld().getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(FIND_DIST), e -> e.isAlive() && !(e instanceof ServerPlayerEntity) && isTutorialMeowth(e, owner));
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = e.squaredDistanceTo(player);
            if (e.getCommandTags().contains(owner)) d -= 10_000; // their own first
            if (d < bd) { bd = d; best = e; }
        }
        if (best != null && !best.getCommandTags().contains(MEOWTH_TAG)) tag(best, player); // pre-0.9 Meowth
        return best;
    }

    private static boolean isTutorialMeowth(LivingEntity e, String ownerTag) {
        if (!(e instanceof MobEntity)) return false;
        if (e instanceof TameableEntity t && (t.isTamed() || t.getOwnerUuid() != null)) return false;
        // already caught (sent out of the Poké Ball / in a pasture): it is someone's pet, never the tutorial one
        Boolean wild = wildState(e);
        if (Boolean.FALSE.equals(wild)) return false;
        var tags = e.getCommandTags();
        if (tags.contains(MEOWTH_TAG)) {
            for (String t : tags) if (t.startsWith(OWNER_PREFIX) && !t.equals(ownerTag)) return false; // another player's
            return true;
        }
        // pre-0.9 Meowth (no tag): only if it could be confirmed to be wild
        if (!Boolean.TRUE.equals(wild)) return false;
        return hasSpeakerName(e.getDisplayName()) || e.getCustomName() != null && hasSpeakerName(e.getCustomName());
    }

    /**
     * TRUE if it is a wild Pokémon (no trainer), FALSE if it has an owner, null if it cannot be told (not a
     * Cobblemon Pokémon or reflection fails).
     */
    private static Boolean wildState(Entity e) {
        Object pk = CobblemonActors.pokemonOf(e);
        if (pk == null) return null;
        Method isWild = cachedMethod(IS_WILD, pk.getClass(), "isWild");
        if (isWild != null && isWild.getReturnType() == boolean.class) {
            try {
                return (boolean) isWild.invoke(pk);
            } catch (Throwable ignored) {
                // try the owner instead
            }
        }
        Method owner = cachedMethod(OWNER_UUID, pk.getClass(), "getOwnerUUID");
        if (owner != null) {
            try {
                return owner.invoke(pk) == null;
            } catch (Throwable ignored) {
                // unknown
            }
        }
        return null;
    }

    private static Method cachedMethod(Map<Class<?>, Method> cache, Class<?> c, String name) {
        if (cache.containsKey(c)) return cache.get(c);
        Method m;
        try {
            m = c.getMethod(name);
        } catch (Throwable t) {
            m = null;
        }
        cache.put(c, m); // null is stored too: not looked up again
        return m;
    }

    private static boolean hasSpeakerName(Text t) {
        if (t == null) return false;
        if (t.getContent() instanceof TranslatableTextContent tr && "tcgshop.tutorial.speaker".equals(tr.getKey())) return true;
        for (Text sib : t.getSiblings()) if (hasSpeakerName(sib)) return true;
        return false;
    }

    // ------------------------------------------------------------------ tick

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        Iterator<Session> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            Session s = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(s.player);
            ServerWorld world = server.getWorld(s.world);
            Entity meowth = world == null ? null : world.getEntity(s.meowth);
            if (player == null || meowth == null || meowth.isRemoved() || !meowth.isAlive() || battling(meowth)) {
                it.remove();
                release(server, s, false);
                continue;
            }
            boolean far = player.getWorld() != world || player.squaredDistanceTo(meowth) > PAUSE_DIST * PAUSE_DIST;
            if (s.paused > 0) {
                if (!far && player.squaredDistanceTo(meowth) <= RESUME_DIST * RESUME_DIST) {
                    s.paused = 0;
                    if (s.index >= 0) s.left = say(player, meowth, s.index); // repeat the bubble it was on
                } else if (++s.paused > MAX_PAUSE) {
                    it.remove();
                    release(server, s, false);
                }
                keepStill(meowth);
                continue;
            }
            if (far) {
                s.paused = 1;
                keepStill(meowth);
                continue;
            }
            keepStill(meowth);
            face(meowth, player, 25f);
            if (--s.left > 0) continue;
            s.index++;
            if (s.index >= LINES) {
                it.remove();
                release(server, s, true);
                continue;
            }
            s.left = say(player, meowth, s.index);
        }
    }

    /** Sends bubble {@code i} only to the owner; returns how many ticks it lasts. */
    private static int say(ServerPlayerEntity player, Entity meowth, int i) {
        Text line = line(i);
        int len = line.getString().length();
        // ~1.6 letters per tick while typing + reading time: 4.5-6 s
        int ticks = MathHelper.clamp(60 + Math.round(len * 0.6f), 90, 120);
        if (ServerPlayNetworking.canSend(player, TutorialPayload.ID)) {
            ServerPlayNetworking.send(player, new TutorialPayload(meowth.getId(), line, ticks + 10));
        } else {
            player.sendMessage(chatLine(line), false);
        }
        if (i == 0 || player.getRandom().nextInt(3) == 0)
            meowth.getWorld().playSound(null, meowth.getBlockPos(), SoundEvents.ENTITY_CAT_AMBIENT, SoundCategory.NEUTRAL, 0.7f, 1.15f);
        return ticks;
    }

    public static Text line(int i) {
        return Text.translatable("tcgshop.tutorial.bubble." + (i + 1));
    }

    /** "<Meowth del Team Rocket> text" for chat (fallback when no Meowth is nearby). */
    public static Text chatLine(Text line) {
        return Text.literal("<").append(Onboarding.meowthName().copy().formatted(Formatting.GOLD)).append("> ")
                .append(line.copy().formatted(Formatting.WHITE));
    }

    // ------------------------------------------------------------------ freeze / look

    private static void freeze(Entity e) {
        e.addCommandTag(FROZEN_TAG);
        if (e instanceof MobEntity mob) {
            mob.getNavigation().stop();
            mob.setAiDisabled(true);
        }
        keepStill(e);
    }

    private static void keepStill(Entity e) {
        if (e instanceof MobEntity mob) {
            mob.getNavigation().stop();
            if (!mob.isAiDisabled()) mob.setAiDisabled(true);
        }
        e.setVelocity(0, Math.min(0, e.getVelocity().y), 0); // no sliding, but falls if in the air
    }

    /** Ends the talk: restores the AI (it becomes a normal wild Meowth again). */
    private static void release(MinecraftServer server, Session s, boolean finished) {
        ServerWorld world = server.getWorld(s.world);
        Entity e = world == null ? null : world.getEntity(s.meowth);
        if (e == null) return; // unloaded: ENTITY_LOAD restores its AI when it loads again
        e.removeCommandTag(FROZEN_TAG);
        if (e instanceof MobEntity mob) mob.setAiDisabled(s.wasAiDisabled);
        if (finished && world != null && e.isAlive()) {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getHeight() + 0.2, e.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
            world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.8f, 1.1f);
        }
    }

    /** Turns body, head and gaze toward the player's eyes (at most {@code maxStep} degrees per tick). */
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
        e.prevYaw = e.getYaw();
        e.prevPitch = e.getPitch();
        e.setYaw(yaw);
        e.setPitch(pitch);
        if (e instanceof LivingEntity le) {
            le.prevHeadYaw = le.getHeadYaw();
            le.prevBodyYaw = le.bodyYaw;
            le.setHeadYaw(yaw);
            le.setBodyYaw(yaw);
        }
    }

    /** PokemonEntity.isBattling() via reflection (false if it does not exist). */
    private static boolean battling(Entity e) {
        Method m;
        if (BATTLING.containsKey(e.getClass())) m = BATTLING.get(e.getClass());
        else {
            try {
                Method found = e.getClass().getMethod("isBattling");
                m = found.getReturnType() == boolean.class ? found : null;
            } catch (Throwable t) {
                m = null;
            }
            BATTLING.put(e.getClass(), m); // null is stored too: not looked up again
        }
        if (m == null) return false;
        try {
            return (boolean) m.invoke(e);
        } catch (Throwable t) {
            return false;
        }
    }
}
