package dev.tcgshop.events;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estado global de los eventos (overworld, "tcgshop_events"):
 *  · deck      qué eventos existen en este mundo (sorteado una vez por evento y guardado; /tcgshop event deck reroll)
 *  · owners    por dueño: tirada del día, enfriamientos, historial, dinero de premios y ventajas pendientes
 *  · actors    entidades vivas de los actores de eventos (uuid -> uid del evento): API para ActorModule
 *  · nextUid   contador de instancias
 */
public final class EventState extends PersistentState {
    private static final Type<EventState> TYPE = new Type<>(EventState::new, EventState::fromNbt, null);

    long deckSalt = 0;
    final Map<String, Boolean> deck = new LinkedHashMap<>();
    long nextUid = 1;
    final Map<UUID, OwnerData> owners = new HashMap<>();
    final Map<UUID, Long> actors = new HashMap<>();

    /** Ventaja pendiente de un dueño (descuento en la próxima compra al Mayorista, contratación, gradeo...). */
    public static final class Perk {
        public String kind;
        public double factor;
        public long until;
        public int uses;
        public long uid;

        NbtCompound toNbt() {
            NbtCompound c = new NbtCompound();
            c.putString("k", kind);
            c.putDouble("f", factor);
            c.putLong("u", until);
            c.putInt("n", uses);
            c.putLong("uid", uid);
            return c;
        }

        static Perk fromNbt(NbtCompound c) {
            Perk p = new Perk();
            p.kind = c.getString("k");
            p.factor = c.getDouble("f");
            p.until = c.getLong("u");
            p.uses = c.getInt("n");
            p.uid = c.getLong("uid");
            return p;
        }
    }

    public record Hist(String id, String template, long day) {}

    public static final class OwnerData {
        /** Día de la última tirada, si salió evento, momento del día (tick 0-24000) en que empieza y cuántos van hoy. */
        public long rollDay = -1;
        public boolean rollHit;
        public int fireTod = -1;
        public int firedToday;
        /** Evento ya elegido hoy que espera a su franja horaria (p. ej. de noche); empieza en fireTod. "" = ninguno. */
        public String pendingDef = "";
        /** Último día en que se usó cada evento (enfriamiento). */
        public final Map<String, Long> cooldown = new HashMap<>();
        /** Historial (el más reciente al final), hasta 40. */
        public final List<Hist> hist = new ArrayList<>();
        /** Dinero de premios: anillo de 7 días (money[0] = hoy). */
        public final long[] money = new long[7];
        public long moneyDay = -1;
        public final List<Perk> perks = new ArrayList<>();

        public String lastTemplate() { return hist.isEmpty() ? "" : hist.get(hist.size() - 1).template(); }

        public void moneyRoll(long day) {
            if (moneyDay < 0) { moneyDay = day; return; }
            if (day <= moneyDay) return;
            int shift = (int) Math.min(money.length, day - moneyDay);
            for (int i = money.length - 1; i >= 0; i--) money[i] = i - shift >= 0 ? money[i - shift] : 0;
            moneyDay = day;
        }

        public long moneyWeek() {
            long s = 0;
            for (long m : money) s += m;
            return s;
        }

        public void addHist(String id, String template, long day) {
            hist.add(new Hist(id, template, day));
            while (hist.size() > 40) hist.remove(0);
            cooldown.put(id, day);
        }

        public int countSince(String id, long fromDay) {
            int n = 0;
            for (Hist h : hist) if (h.id().equals(id) && h.day() >= fromDay) n++;
            return n;
        }

        public boolean inRecent(String id, int last) {
            for (int i = Math.max(0, hist.size() - last); i < hist.size(); i++) if (hist.get(i).id().equals(id)) return true;
            return false;
        }

        public long lastDayOf(String id) {
            for (int i = hist.size() - 1; i >= 0; i--) if (hist.get(i).id().equals(id)) return hist.get(i).day();
            return Long.MIN_VALUE;
        }
    }

    public static EventState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_events");
    }

    public OwnerData owner(UUID id) { return owners.computeIfAbsent(id, k -> new OwnerData()); }

    public long nextUid() {
        markDirty();
        return nextUid++;
    }

    // ------------------------------------------------------------------ mazo del mundo

    /** ¿Existe este evento en este mundo? Se sortea la primera vez que se pregunta (semilla del mundo + sal) y se guarda. */
    public boolean inDeck(EventDef def, long worldSeed) {
        Boolean b = deck.get(def.id());
        if (b == null) {
            long h = worldSeed ^ deckSalt * 0x9E3779B97F4A7C15L ^ (long) def.id().hashCode() * 0xC2B2AE3D27D4EB4FL;
            b = new java.util.Random(h).nextDouble() < EventsConfig.INSTANCE.presence(def.tier());
            deck.put(def.id(), b);
            markDirty();
        }
        return b;
    }

    public void rerollDeck(long salt) {
        deckSalt = salt;
        deck.clear();
        markDirty();
    }

    public Map<String, Boolean> deckView() { return java.util.Collections.unmodifiableMap(deck); }

    // ------------------------------------------------------------------ actores (API para ActorModule)

    /** Registra una entidad de evento ANTES de hacer world.spawnEntity (ENTITY_LOAD salta al aparecer). */
    public void registerActor(UUID entity, long uid) {
        actors.put(entity, uid);
        markDirty();
    }

    public void unregisterActor(UUID entity) {
        if (actors.remove(entity) != null) markDirty();
    }

    public boolean isLiveActor(UUID entity) { return actors.containsKey(entity); }

    /** uid del evento al que pertenece la entidad, o -1. */
    public long actorEvent(UUID entity) { return actors.getOrDefault(entity, -1L); }

    public List<UUID> actorsOf(long uid) {
        List<UUID> out = new ArrayList<>();
        actors.forEach((k, v) -> { if (v == uid) out.add(k); });
        return out;
    }

    public void clearActorsOf(long uid) {
        if (actors.values().removeIf(v -> v == uid)) markDirty();
    }

    public int actorCount() { return actors.size(); }

    // ------------------------------------------------------------------ NBT

    private static EventState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        EventState s = new EventState();
        s.deckSalt = nbt.getLong("salt");
        NbtCompound d = nbt.getCompound("deck");
        for (String k : d.getKeys()) s.deck.put(k, d.getBoolean(k));
        s.nextUid = Math.max(1, nbt.getLong("next_uid"));
        NbtCompound ow = nbt.getCompound("owners");
        for (String k : ow.getKeys()) {
            UUID id;
            try { id = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
            NbtCompound c = ow.getCompound(k);
            OwnerData o = new OwnerData();
            o.rollDay = c.contains("roll_day") ? c.getLong("roll_day") : -1;
            o.rollHit = c.getBoolean("roll_hit");
            o.fireTod = c.contains("fire_tod") ? c.getInt("fire_tod") : -1;
            o.firedToday = c.getInt("fired");
            o.pendingDef = c.getString("pending");
            NbtCompound cd = c.getCompound("cd");
            for (String e : cd.getKeys()) o.cooldown.put(e, cd.getLong(e));
            NbtList hl = c.getList("hist", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < hl.size(); i++) {
                NbtCompound h = hl.getCompound(i);
                o.hist.add(new Hist(h.getString("i"), h.getString("t"), h.getLong("d")));
            }
            long[] m = c.getLongArray("money");
            System.arraycopy(m, 0, o.money, 0, Math.min(m.length, o.money.length));
            o.moneyDay = c.contains("money_day") ? c.getLong("money_day") : -1;
            NbtList pl = c.getList("perks", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < pl.size(); i++) o.perks.add(Perk.fromNbt(pl.getCompound(i)));
            s.owners.put(id, o);
        }
        NbtCompound ac = nbt.getCompound("actors");
        for (String k : ac.getKeys()) {
            try { s.actors.put(UUID.fromString(k), ac.getLong(k)); } catch (IllegalArgumentException ignored) {}
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putLong("salt", deckSalt);
        NbtCompound d = new NbtCompound();
        deck.forEach(d::putBoolean);
        nbt.put("deck", d);
        nbt.putLong("next_uid", nextUid);
        NbtCompound ow = new NbtCompound();
        owners.forEach((id, o) -> {
            NbtCompound c = new NbtCompound();
            c.putLong("roll_day", o.rollDay);
            c.putBoolean("roll_hit", o.rollHit);
            c.putInt("fire_tod", o.fireTod);
            c.putInt("fired", o.firedToday);
            if (!o.pendingDef.isEmpty()) c.putString("pending", o.pendingDef);
            NbtCompound cd = new NbtCompound();
            o.cooldown.forEach(cd::putLong);
            c.put("cd", cd);
            NbtList hl = new NbtList();
            for (Hist h : o.hist) {
                NbtCompound x = new NbtCompound();
                x.putString("i", h.id());
                x.putString("t", h.template());
                x.putLong("d", h.day());
                hl.add(x);
            }
            c.put("hist", hl);
            c.putLongArray("money", o.money.clone());
            c.putLong("money_day", o.moneyDay);
            NbtList pl = new NbtList();
            for (Perk p : o.perks) pl.add(p.toNbt());
            c.put("perks", pl);
            ow.put(id.toString(), c);
        });
        nbt.put("owners", ow);
        NbtCompound ac = new NbtCompound();
        actors.forEach((k, v) -> ac.putLong(k.toString(), v));
        nbt.put("actors", ac);
        return nbt;
    }

    /** Para eventtest: ida y vuelta por NBT. */
    EventState roundTrip(RegistryWrapper.WrapperLookup lookup) {
        return fromNbt(writeNbt(new NbtCompound(), lookup), lookup);
    }
}
