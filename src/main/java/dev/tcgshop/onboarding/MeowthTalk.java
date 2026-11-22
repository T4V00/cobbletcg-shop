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
 * La charla del "Meowth del Team Rocket": una serie CORTA de bocadillos sobre su cabeza (los mismos bocadillos que los
 * NPC de la tienda, ClientSpeech) que sólo ve el dueño de la tienda. Mientras habla el Meowth se queda quieto (IA
 * apagada, sin navegar) y mirando al jugador; clic derecho sobre él pasa al siguiente bocadillo. Al terminar vuelve a
 * ser un Meowth salvaje normal y capturable.
 * <p>
 * Si el jugador se aleja más de {@link #PAUSE_DIST} bloques la charla se pausa (y se reanuda al volver); si tarda
 * demasiado, se desconecta, el Meowth desaparece/es capturado o entra en combate, se termina limpiamente. La IA
 * apagada se guarda en el NBT del Meowth: por eso lleva la etiqueta {@link #FROZEN_TAG} mientras está congelado y, si
 * el servidor se cerró a mitad (o se cayó), se le devuelve la IA al cargarlo.
 */
public final class MeowthTalk {
    /** Etiqueta de entidad del Meowth del tutorial (para encontrarlo con /tcgshop tutorial y no sacar otro). */
    public static final String MEOWTH_TAG = "tcgshop_tutorial_meowth";
    /** Prefijo de la etiqueta con el dueño: tcgshop_meowth_owner_<uuid>. */
    public static final String OWNER_PREFIX = "tcgshop_meowth_owner_";
    /** Puesta mientras está congelado por la charla. */
    public static final String FROZEN_TAG = "tcgshop_meowth_frozen";

    /** Bocadillos de la charla (tcgshop.tutorial.bubble.1 .. N). */
    public static final int LINES = 8;
    public static final double PAUSE_DIST = 24, RESUME_DIST = 16, FIND_DIST = 24;
    /** Pausada más de 1 minuto: se termina. */
    public static final int MAX_PAUSE = 20 * 60;
    /** Ticks antes del primer bocadillo (para ver al Meowth aparecer). */
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
    /** Pokemon.isWild() / Pokemon.getOwnerUUID() por clase (null guardado = no existe). */
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
        // Meowth que se quedó congelado (servidor cerrado o caído a mitad de la charla): se le devuelve la IA
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!entity.getCommandTags().contains(FROZEN_TAG)) return;
            for (Session s : SESSIONS.values()) if (s.meowth.equals(entity.getUuid())) return;
            entity.removeCommandTag(FROZEN_TAG);
            if (entity instanceof MobEntity mob) mob.setAiDisabled(false);
        });
        // clic derecho del dueño sobre el Meowth: siguiente bocadillo (y no hace nada más mientras habla)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            Session s = SESSIONS.get(sp.getUuid());
            if (s == null || !entity.getUuid().equals(s.meowth)) return ActionResult.PASS;
            long now = world.getTime();
            if (hand == Hand.MAIN_HAND && now - s.lastClick >= 8 && s.index >= 0 && s.paused == 0) {
                s.lastClick = now;
                s.left = 0; // el tick siguiente pasa al próximo bocadillo
            }
            return ActionResult.SUCCESS;
        });
    }

    public static boolean talking(ServerPlayerEntity player) {
        return SESSIONS.containsKey(player.getUuid());
    }

    /** Termina (si la hay) la charla de este jugador y devuelve la IA a su Meowth. */
    public static void stop(ServerPlayerEntity player) {
        Session s = SESSIONS.remove(player.getUuid());
        if (s != null && player.getServer() != null) release(player.getServer(), s, false);
    }

    /** Marca la entidad como el Meowth del tutorial de este jugador. */
    public static void tag(Entity meowth, ServerPlayerEntity owner) {
        meowth.addCommandTag(MEOWTH_TAG);
        meowth.addCommandTag(OWNER_PREFIX + owner.getUuid());
    }

    /** Empieza (o reinicia) la charla de {@code meowth} para {@code player}. */
    public static boolean start(ServerPlayerEntity player, Entity meowth) {
        if (!(meowth instanceof LivingEntity) || meowth.isRemoved()) return false;
        MinecraftServer server = player.getServer();
        Session old = SESSIONS.remove(player.getUuid());
        boolean wasDisabled = meowth instanceof MobEntity mob && mob.isAiDisabled() && !meowth.getCommandTags().contains(FROZEN_TAG);
        if (old != null) {
            if (old.meowth.equals(meowth.getUuid())) wasDisabled = old.wasAiDisabled;
            else if (server != null) release(server, old, false);
        }
        // otro jugador ya está escuchando a este mismo Meowth: no se pisan
        for (Session s : SESSIONS.values()) if (s.meowth.equals(meowth.getUuid())) return false;
        Session s = new Session(player.getUuid(), meowth.getUuid(), meowth.getWorld().getRegistryKey(), wasDisabled);
        SESSIONS.put(player.getUuid(), s);
        freeze(meowth);
        face(meowth, player, 180f);
        return true;
    }

    /**
     * El Meowth del tutorial de este jugador cerca de él (o, para mundos de antes de la 0.9, cualquier Pokémon con el
     * mote "Meowth del Team Rocket"), o null.
     */
    public static Entity findNear(ServerPlayerEntity player) {
        String owner = OWNER_PREFIX + player.getUuid();
        List<LivingEntity> list = player.getServerWorld().getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(FIND_DIST), e -> e.isAlive() && !(e instanceof ServerPlayerEntity) && isTutorialMeowth(e, owner));
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            double d = e.squaredDistanceTo(player);
            if (e.getCommandTags().contains(owner)) d -= 10_000; // el suyo primero
            if (d < bd) { bd = d; best = e; }
        }
        if (best != null && !best.getCommandTags().contains(MEOWTH_TAG)) tag(best, player); // Meowth de antes de la 0.9
        return best;
    }

    private static boolean isTutorialMeowth(LivingEntity e, String ownerTag) {
        if (!(e instanceof MobEntity)) return false;
        if (e instanceof TameableEntity t && (t.isTamed() || t.getOwnerUuid() != null)) return false;
        // ya capturado (sacado de la Poké Ball / en un pasto): es la mascota de alguien, nunca el del tutorial
        Boolean wild = wildState(e);
        if (Boolean.FALSE.equals(wild)) return false;
        var tags = e.getCommandTags();
        if (tags.contains(MEOWTH_TAG)) {
            for (String t : tags) if (t.startsWith(OWNER_PREFIX) && !t.equals(ownerTag)) return false; // el de otro jugador
            return true;
        }
        // Meowth de antes de la 0.9 (sin etiqueta): sólo si se ha podido confirmar que es salvaje
        if (!Boolean.TRUE.equals(wild)) return false;
        return hasSpeakerName(e.getDisplayName()) || e.getCustomName() != null && hasSpeakerName(e.getCustomName());
    }

    /**
     * TRUE si es un Pokémon salvaje (sin entrenador), FALSE si tiene dueño, null si no se puede saber (no es un
     * Pokémon de Cobblemon o falla la reflexión).
     */
    private static Boolean wildState(Entity e) {
        Object pk = CobblemonActors.pokemonOf(e);
        if (pk == null) return null;
        Method isWild = cachedMethod(IS_WILD, pk.getClass(), "isWild");
        if (isWild != null && isWild.getReturnType() == boolean.class) {
            try {
                return (boolean) isWild.invoke(pk);
            } catch (Throwable ignored) {
                // se prueba con el dueño
            }
        }
        Method owner = cachedMethod(OWNER_UUID, pk.getClass(), "getOwnerUUID");
        if (owner != null) {
            try {
                return owner.invoke(pk) == null;
            } catch (Throwable ignored) {
                // desconocido
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
        cache.put(c, m); // también se guarda el null: no se vuelve a buscar
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
                    if (s.index >= 0) s.left = say(player, meowth, s.index); // repite el bocadillo en el que iba
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

    /** Manda el bocadillo {@code i} sólo al dueño; devuelve los ticks que dura. */
    private static int say(ServerPlayerEntity player, Entity meowth, int i) {
        Text line = line(i);
        int len = line.getString().length();
        // ~1.6 letras por tick al escribirse + tiempo de lectura: 4,5-6 s
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

    /** "<Meowth del Team Rocket> texto" para el chat (repetición sin Meowth cerca). */
    public static Text chatLine(Text line) {
        return Text.literal("<").append(Onboarding.meowthName().copy().formatted(Formatting.GOLD)).append("> ")
                .append(line.copy().formatted(Formatting.WHITE));
    }

    // ------------------------------------------------------------------ congelar / mirar

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
        e.setVelocity(0, Math.min(0, e.getVelocity().y), 0); // sin deslizarse, pero cae si está en el aire
    }

    /** Termina la charla: devuelve la IA (vuelve a ser un Meowth salvaje normal). */
    private static void release(MinecraftServer server, Session s, boolean finished) {
        ServerWorld world = server.getWorld(s.world);
        Entity e = world == null ? null : world.getEntity(s.meowth);
        if (e == null) return; // descargado: ENTITY_LOAD le devuelve la IA al volver a cargarse
        e.removeCommandTag(FROZEN_TAG);
        if (e instanceof MobEntity mob) mob.setAiDisabled(s.wasAiDisabled);
        if (finished && world != null && e.isAlive()) {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getHeight() + 0.2, e.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
            world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.8f, 1.1f);
        }
    }

    /** Gira cuerpo, cabeza y mirada hacia los ojos del jugador (como mucho {@code maxStep} grados por tick). */
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

    /** PokemonEntity.isBattling() por reflexión (false si no existe). */
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
            BATTLING.put(e.getClass(), m); // también se guarda el null: no se vuelve a buscar
        }
        if (m == null) return false;
        try {
            return (boolean) m.invoke(e);
        } catch (Throwable t) {
            return false;
        }
    }
}
