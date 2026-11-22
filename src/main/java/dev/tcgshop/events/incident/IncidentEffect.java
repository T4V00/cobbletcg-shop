package dev.tcgshop.events.incident;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventInstance;
import dev.tcgshop.events.EventManager;
import dev.tcgshop.events.actor.ActorEffect;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.Block;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * P2 "incident": pone bloques tcgshop:incident en la tienda y los conecta con los bloqueos del núcleo (expositor o
 * casilla inutilizable, caja averiada, puerta atascada, apagón). El jugador los arregla con la herramienta del tag
 * (fregona, kit de reparación, pala o mano vacía); el evento se resuelve cuando están todos (o uno, "any") arreglados.
 *
 * Estado por instancia en ctx.data() (se guarda con la zona):
 *   placed (bool), at (long, colocación retrasada), fail (bool: no había sitio), b (lista de incidentes:
 *   p = pos, k = tipo, f = arreglado, d = expositor bloqueado, s = casilla, o = puerta cerrada, r = caja averiada).
 *
 * JSON (EFFECTS.md §8 y extras):
 * <pre>
 * {"type":"incident","kind":"puddle","count":[3,6],"near":"floor",       // door|outside_door|display|register|core|floor
 *  "fix":{"items":"#tcgshop:fix/puddle","hand":false,"uses":1,"hits":1,"consume":false},
 *  "disable":"display",            // "" | display | slot (bloquea el expositor o una casilla con producto, al lado)
 *  "registerDown":false, "closeDoor":false, "lighting":-1, "resolveWhen":"all",
 *  "delay":[0,0],                  // ticks antes de aparecer (p. ej. cuando ya ha entrado el cliente patoso)
 *  "message":"",                   // clave de idioma: aviso extra al dueño cuando aparece
 *  "reviewTag":"",                 // cambia el tag de reseña del evento (inst.reviewTag)
 *  "loot":"tcgshop:events/package","rolls":1,"money":[0,0],"weeklyCap":2}   // sólo kind=package: premio al abrirlo
 * </pre>
 */
public final class IncidentEffect implements EventEffect {
    final IncidentKind kind;
    final int countMin, countMax;
    final String near, disable, resolveWhen, message, reviewTag, loot;
    final boolean registerDown, closeDoor;
    final int lighting, rolls, weeklyCap;
    final long delayMin, delayMax, moneyMin, moneyMax;
    final FixSpec fix;

    private static final Set<String> NEARS = Set.of("door", "outside_door", "display", "register", "core", "floor");

    public IncidentEffect(JsonObject o, String defId) {
        kind = IncidentKind.parse(EventDef.str(o, "kind", "puddle"));
        long[] c = EventDef.range(o.get("count"), 1);
        countMin = (int) Math.max(1, Math.min(8, c[0]));
        countMax = (int) Math.max(countMin, Math.min(8, c[1]));
        near = EventDef.str(o, "near", kind == IncidentKind.PACKAGE ? "door" : "floor").toLowerCase(Locale.ROOT);
        if (!NEARS.contains(near)) throw new IllegalArgumentException("incident.near inválido: " + near + " (" + NEARS + ")");
        disable = EventDef.str(o, "disable", "").toLowerCase(Locale.ROOT);
        if (!List.of("", "display", "slot").contains(disable)) throw new IllegalArgumentException("incident.disable inválido: " + disable);
        resolveWhen = EventDef.str(o, "resolveWhen", "all").toLowerCase(Locale.ROOT);
        if (!List.of("all", "any").contains(resolveWhen)) throw new IllegalArgumentException("incident.resolveWhen inválido: " + resolveWhen);
        registerDown = EventDef.bool(o, "registerDown", false);
        closeDoor = EventDef.bool(o, "closeDoor", false);
        lighting = Math.max(-1, Math.min(15, EventDef.integer(o, "lighting", -1)));
        long[] d = o.has("delay") ? EventDef.range(o.get("delay"), 0) : new long[]{0, 0};
        delayMin = Math.max(0, Math.min(12_000, d[0]));
        delayMax = Math.max(delayMin, Math.min(12_000, d[1]));
        message = EventDef.str(o, "message", "");
        reviewTag = EventDef.str(o, "reviewTag", "");
        fix = FixSpec.parse(o.has("fix") ? o.getAsJsonObject("fix") : new JsonObject(), kind);
        loot = EventDef.str(o, "loot", kind == IncidentKind.PACKAGE ? "tcgshop:events/package" : "");
        if (!loot.isEmpty()) {
            Identifier id = Identifier.tryParse(loot);
            if (id == null || !id.getNamespace().equals("tcgshop") || !id.getPath().startsWith("events/"))
                throw new IllegalArgumentException("incident.loot sólo puede usar tablas tcgshop:events/*");
        }
        rolls = Math.max(1, Math.min(3, EventDef.integer(o, "rolls", 1)));
        long[] m = EventDef.range(o.get("money"), 0);
        moneyMin = Math.max(0, m[0]);
        moneyMax = Math.max(moneyMin, m[1]);
        weeklyCap = Math.max(0, Math.min(14, EventDef.integer(o, "weeklyCap", 2)));
    }

    // ------------------------------------------------------------------ arreglo (herramienta)

    /** Qué arregla un incidente: objetos (tag "#ns:path" o id), mano vacía, durabilidad por golpe, golpes. */
    record FixSpec(List<String> items, boolean hand, int uses, int hits, boolean consume) {
        static FixSpec parse(JsonObject o, IncidentKind kind) {
            List<String> items = new ArrayList<>();
            JsonElement e = o.get("items");
            if (e == null) {
                if (kind != IncidentKind.DEBRIS && kind != IncidentKind.PACKAGE) items.add("#tcgshop:fix/" + kind.key());
            } else if (e.isJsonArray()) {
                for (JsonElement x : (JsonArray) e) items.add(x.getAsString());
            } else if (!e.getAsString().isEmpty() && !e.getAsString().equals("hand")) items.add(e.getAsString());
            boolean hand = EventDef.bool(o, "hand", e != null && e.isJsonPrimitive() && e.getAsString().equals("hand") || e == null && kind.handByDefault);
            for (String s : items) {
                String id = s.startsWith("#") ? s.substring(1) : s;
                if (Identifier.tryParse(id) == null) throw new IllegalArgumentException("incident.fix.items: id inválido " + s);
                if (!s.startsWith("#") && (id.endsWith("bucket") || id.equals("minecraft:flint_and_steel") || id.equals("minecraft:fire_charge")))
                    throw new IllegalArgumentException("incident.fix.items: nunca cubos ni fuego (" + s + ")");
            }
            if (items.isEmpty() && !hand) throw new IllegalArgumentException("incident.fix: sin objetos ni mano, no se podría arreglar");
            int uses = Math.max(0, Math.min(16, EventDef.integer(o, "uses", 1)));
            int hits = Math.max(1, Math.min(10, EventDef.integer(o, "hits", kind.defaultHits)));
            return new FixSpec(List.copyOf(items), hand, uses, hits, EventDef.bool(o, "consume", false));
        }

        static FixSpec defaults(IncidentKind kind) { return parse(new JsonObject(), kind); }

        boolean matches(ItemStack st) {
            if (st.isEmpty()) return hand;
            // jamás agua ni fuego de verdad, diga lo que diga un datapack
            if (st.getItem() instanceof BucketItem || st.getItem() instanceof FlintAndSteelItem || st.getItem() instanceof FireChargeItem) return false;
            Identifier id = Registries.ITEM.getId(st.getItem());
            for (String s : items) {
                if (s.startsWith("#")) {
                    Identifier t = Identifier.tryParse(s.substring(1));
                    if (t != null && st.isIn(TagKey.of(RegistryKeys.ITEM, t))) return true;
                } else if (id.toString().equals(s)) return true;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    public void start(EventContext ctx) {
        if (!reviewTag.isEmpty()) ctx.instance.reviewTag = reviewTag;
        long d = delayMin + (delayMax > delayMin ? (long) (ctx.rng.nextDouble() * (delayMax - delayMin)) : 0);
        if (d > 0) {
            ctx.data().putLong("at", ctx.now() + d);
            ctx.markDirty();
        } else place(ctx);
    }

    @Override
    public void tick(EventContext ctx) {
        NbtCompound d = ctx.data();
        if (ctx.instance.resolved()) return;
        if (!d.getBoolean("placed")) {
            if (ctx.now() >= d.getLong("at")) place(ctx);
            return;
        }
        if (d.getBoolean("fail")) {
            // sin sitio para el incidente: si nada más del evento se puede arreglar, se cancela (nunca un negativo sin arreglo)
            if (!otherFixExists(ctx)) {
                long uid = ctx.instance.uid;
                ctx.zone.events.log.removeIf(r -> r.uid == uid); // un evento que nunca llegó a pasar no deja reseña
                EventManager.end(uid, "cancelled");
            }
            return;
        }
        if (ctx.now() % 200 < 20) verify(ctx.world, ctx.zone, ctx.instance, ctx.def, ctx.index, d);
    }

    @Override
    public void onRecompute(EventContext ctx) {
        IncidentModule.cleanupZone(ctx.world, ctx.zone);
    }

    @Override
    public void resolve(EventContext ctx, String how) {
        removeAll(ctx.world, ctx.data());
    }

    @Override
    public void end(EventContext ctx) {
        removeAll(ctx.world, ctx.data());
    }

    @Override
    public boolean providesFix() { return kind != IncidentKind.PACKAGE; }

    @Override
    public Set<String> implicitRequires() {
        Set<String> s = new HashSet<>();
        if (near.equals("register")) s.add("register");
        if (closeDoor) s.add("door");
        return s;
    }

    /**
     * Precondición para el núcleo (EventEffect.canStart): ¿hay sitio para al menos un incidente? Así un evento sin
     * sitio no llega a empezar (sin aviso, sin reseña, sin gastar el historial del dueño). Con retraso se decide al colocar.
     */
    public boolean canStart(ServerWorld w, ShopZones.Zone z) {
        if (delayMax > 0) return true;
        if (!disable.isEmpty() && IncidentPlacer.displays(w, z).isEmpty()) return false; // su bloqueo no tendría dónde ir
        List<BlockPos> anchors = IncidentPlacer.anchors(w, z, near);
        Random rng = new Random(w.getTime() ^ z.id.getLeastSignificantBits());
        if (!IncidentPlacer.candidates(w, z, anchors, near.equals("outside_door"), rng, countMin).isEmpty()) return true;
        return !anchors.isEmpty() && !IncidentPlacer.candidates(w, z, List.of(), false, rng, countMin).isEmpty();
    }

    private boolean otherFixExists(EventContext ctx) {
        List<EventEffect> effs = ctx.def.effectsFor(ctx.instance.variant);
        for (int i = 0; i < effs.size(); i++) {
            if (i == ctx.index) continue;
            EventEffect e = effs.get(i);
            if (e instanceof IncidentEffect ie) {
                if (!ctx.instance.data.getCompound("e" + i).getBoolean("fail")) return true;
            } else if (e.providesFix()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ colocar

    private void place(EventContext ctx) {
        NbtCompound d = ctx.data();
        if (d.getBoolean("placed")) return;
        d.putBoolean("placed", true);
        ServerWorld w = ctx.world;
        ShopZones.Zone z = ctx.zone;
        EventInstance inst = ctx.instance;
        int want = countMin + (countMax > countMin ? ctx.rng.nextInt(countMax - countMin + 1) : 0);
        Random rng = ctx.rng;

        List<BlockPos> anchors = IncidentPlacer.anchors(w, z, near);
        List<BlockPos> cands = IncidentPlacer.candidates(w, z, anchors, near.equals("outside_door"), rng, want);
        boolean fallback = false;
        if (cands.isEmpty() && !anchors.isEmpty()) { // plan B: suelo
            cands = IncidentPlacer.candidates(w, z, List.of(), false, rng, want);
            fallback = true;
        }
        List<ShopDisplayBlockEntity> displays = disable.isEmpty() ? List.of() : IncidentPlacer.displays(w, z);
        List<BlockPos> displayPos = new ArrayList<>();
        for (ShopDisplayBlockEntity de : displays) displayPos.add(de.getPos());
        List<BlockPos> doors = closeDoor ? IncidentPlacer.doors(w, z) : List.of();

        NbtList list = new NbtList();
        boolean locked = false;
        for (BlockPos p : cands) {
            if (list.size() >= want) break;
            if (!IncidentPlacer.safe(w, z, p)) continue; // re-comprobar: el anterior puede estar al lado
            if (!w.setBlockState(p, TcgBlocks.INCIDENT.getDefaultState().with(IncidentBlock.KIND, kind), Block.NOTIFY_LISTENERS | Block.FORCE_STATE))
                continue;
            if (w.getBlockEntity(p) instanceof IncidentBlockEntity be) be.init(inst.uid, z.id, ctx.index, fix.hits(), inst.end);
            NbtCompound r = new NbtCompound();
            r.putLong("p", p.asLong());
            r.putString("k", kind.key());
            // bloqueos de esta pieza
            if (!disable.isEmpty()) {
                BlockPos dp = IncidentPlacer.nearest(p, displayPos, 3.5);
                // en el suelo al azar (plan B) o sin expositor al lado: el más cercano sin límite, para que el evento
                // tenga siempre el bloqueo que lo define
                if (dp == null && (fallback || !locked)) dp = IncidentPlacer.nearest(p, displayPos, 1024);
                if (dp != null) {
                    if (disable.equals("display")) {
                        inst.disableDisplay(dp);
                        r.putLong("d", dp.asLong());
                        locked = true;
                    } else {
                        int slot = stockedSlot(w, dp, rng, list);
                        if (slot >= 0) {
                            inst.disableSlot(dp, slot);
                            r.putLong("d", dp.asLong());
                            r.putInt("s", slot);
                            locked = true;
                        }
                    }
                }
            }
            if (closeDoor && !doors.isEmpty() && list.isEmpty()) { // una puerta por evento: la más cercana a la primera pieza
                BlockPos door = IncidentPlacer.nearest(p, doors, 64);
                if (door != null) {
                    inst.closeDoor(door);
                    r.putLong("o", door.asLong());
                }
            }
            if (registerDown) {
                inst.setRegisterDown(true);
                r.putBoolean("r", true);
            }
            list.add(r);
        }
        d.put("b", list);
        if (list.isEmpty()) {
            d.putBoolean("fail", true);
            dev.tcgshop.TcgShop.LOGGER.info("[eventos] {}: no hay sitio seguro para el incidente {} en la tienda de {}", inst.defId, kind.key(), z.ownerName);
        } else {
            if (lighting >= 0) inst.setLightingOverride(lighting);
            if (!message.isEmpty()) ctx.tellOwner(Text.translatable(message).formatted(Formatting.GOLD), false);
        }
        ctx.invalidate();
        ctx.markDirty();
    }

    /** Una casilla con producto del expositor que aún no esté bloqueada por esta pieza del evento (o -1). list = la que se está colocando. */
    private static int stockedSlot(ServerWorld w, BlockPos dp, Random rng, NbtList list) {
        if (!(w.getBlockEntity(dp) instanceof ShopDisplayBlockEntity de)) return -1;
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getLong("d") == dp.asLong()) used.add(list.getCompound(i).getInt("s"));
        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < de.size(); i++) if (!de.getStack(i).isEmpty() && !used.contains(i)) ok.add(i);
        return ok.isEmpty() ? -1 : ok.get(rng.nextInt(ok.size()));
    }

    // ------------------------------------------------------------------ arreglar / quitar

    /** ¿Esta pieza del evento ya está cumplida? */
    boolean done(NbtCompound d) {
        if (!d.getBoolean("placed")) return false;
        if (d.getBoolean("fail")) return true;
        NbtList list = d.getList("b", NbtElement.COMPOUND_TYPE);
        int fixed = 0;
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getBoolean("f")) fixed++;
        return resolveWhen.equals("any") ? fixed > 0 : fixed >= list.size();
    }

    /**
     * Un incidente de esta pieza se ha arreglado (herramienta del dueño, o quitado por otra vía): suelta sus bloqueos,
     * da el premio del paquete y, si ya están todos los incidentes del evento, lo resuelve.
     */
    void onFixed(ServerWorld w, ShopZones.Zone z, EventInstance inst, EventDef def, int index, BlockPos pos, String how,
                 ServerPlayerEntity player) {
        NbtCompound d = inst.data.getCompound("e" + index);
        NbtList list = d.getList("b", NbtElement.COMPOUND_TYPE);
        NbtCompound rec = null;
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getLong("p") == pos.asLong()) rec = list.getCompound(i);
        if (rec == null || rec.getBoolean("f")) return;
        rec.putBoolean("f", true);
        release(inst, rec);
        if (done(d) && lighting >= 0 && !actorHoldsLight(inst, def)) inst.setLightingOverride(-1);
        reapplyLocks(inst, def); // lo que siga bloqueando otra pieza (de este u otro efecto) vuelve a su sitio
        if (kind == IncidentKind.PACKAGE && player != null && player.getUuid().equals(z.owner)) openPackage(w, z, player, new Random(inst.uid * 31 + pos.asLong()));
        ShopZones.get(w).markDirty();
        if (allIncidentsDone(inst, def)) EventManager.resolve(inst.uid, how);
        else {
            EventManager.invalidate(z);
            if (player != null && kind != IncidentKind.PACKAGE) {
                int left = 0;
                for (int i = 0; i < list.size(); i++) if (!list.getCompound(i).getBoolean("f")) left++;
                player.sendMessage(Text.translatable("tcgshop.incident.left", left).formatted(Formatting.YELLOW), true);
            }
        }
    }

    /**
     * Suelta los bloqueos de una pieza. Los bloqueos de EventInstance no llevan cuenta de quién los puso, así que se
     * quitan sin más y luego {@link #reapplyLocks} vuelve a poner los que siga teniendo cualquier otra pieza viva.
     */
    private static void release(EventInstance inst, NbtCompound rec) {
        if (rec.contains("d")) inst.enableDisplay(BlockPos.fromLong(rec.getLong("d")));
        if (rec.contains("o")) inst.openDoor(BlockPos.fromLong(rec.getLong("o")));
        if (rec.getBoolean("r")) inst.setRegisterDown(false);
    }

    /**
     * Vuelve a poner todos los bloqueos que aún tienen las piezas vivas de la instancia: incidentes sin arreglar (de
     * cualquier efecto "incident") y los lk_* de los efectos "actor". Idempotente (son conjuntos). La luz de un actor
     * no se reaplica (su nivel no se guarda): quien la quita debe mirar antes {@link #actorHoldsLight}.
     * Para el núcleo/actores: llamarlo tras soltar bloqueos con enableDisplay/openDoor/setRegisterDown/setLightingOverride.
     */
    public static void reapplyLocks(EventInstance inst, EventDef def) {
        if (def == null || inst.resolved()) return;
        List<EventEffect> effs = def.effectsFor(inst.variant);
        for (int i = 0; i < effs.size(); i++) {
            NbtCompound d = inst.data.getCompound("e" + i);
            EventEffect e = effs.get(i);
            if (e instanceof IncidentEffect ie) {
                if (!d.getBoolean("placed") || d.getBoolean("fail")) continue;
                NbtList list = d.getList("b", NbtElement.COMPOUND_TYPE);
                for (int k = 0; k < list.size(); k++) {
                    NbtCompound r = list.getCompound(k);
                    if (r.getBoolean("f")) continue;
                    if (r.contains("d")) {
                        BlockPos dp = BlockPos.fromLong(r.getLong("d"));
                        if (r.contains("s")) inst.disableSlot(dp, r.getInt("s"));
                        else inst.disableDisplay(dp);
                    }
                    if (r.contains("o")) inst.closeDoor(BlockPos.fromLong(r.getLong("o")));
                    if (r.getBoolean("r")) inst.setRegisterDown(true);
                }
                if (ie.lighting >= 0 && !list.isEmpty() && !ie.done(d)) inst.setLightingOverride(ie.lighting);
            } else if (e instanceof ActorEffect) {
                if (d.contains("lk_disp")) inst.disableDisplay(BlockPos.fromLong(d.getLong("lk_disp")));
                NbtList sl = d.getList("lk_slots", NbtElement.STRING_TYPE);
                for (int k = 0; k < sl.size(); k++) {
                    String s = sl.getString(k);
                    int c = s.indexOf(':');
                    if (c <= 0) continue;
                    try {
                        inst.disableSlot(BlockPos.fromLong(Long.parseLong(s.substring(0, c))), Integer.parseInt(s.substring(c + 1)));
                    } catch (NumberFormatException ignored) {}
                }
                if (d.contains("lk_door")) inst.closeDoor(BlockPos.fromLong(d.getLong("lk_door")));
                if (d.getBoolean("lk_reg")) inst.setRegisterDown(true);
            }
        }
    }

    /** ¿Algún efecto "actor" de la instancia mantiene su luz? (entonces no se devuelve la luz real) */
    static boolean actorHoldsLight(EventInstance inst, EventDef def) {
        if (def == null) return false;
        List<EventEffect> effs = def.effectsFor(inst.variant);
        for (int i = 0; i < effs.size(); i++)
            if (effs.get(i) instanceof ActorEffect && inst.data.getCompound("e" + i).getBoolean("lk_light")) return true;
        return false;
    }

    /** ¿Están cumplidas TODAS las piezas "incident" de la instancia? */
    static boolean allIncidentsDone(EventInstance inst, EventDef def) {
        if (def == null) return true;
        List<EventEffect> effs = def.effectsFor(inst.variant);
        for (int i = 0; i < effs.size(); i++)
            if (effs.get(i) instanceof IncidentEffect ie && !ie.done(inst.data.getCompound("e" + i))) return false;
        return true;
    }

    /** Cada 10 s: si un incidente ya no está (comando, otro mod...), cuenta como arreglado. Sólo chunks cargados. */
    private void verify(ServerWorld w, ShopZones.Zone z, EventInstance inst, EventDef def, int index, NbtCompound d) {
        NbtList list = d.getList("b", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound r = list.getCompound(i);
            if (r.getBoolean("f")) continue;
            BlockPos p = BlockPos.fromLong(r.getLong("p"));
            if (!w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
            if (!w.getBlockState(p).isOf(TcgBlocks.INCIDENT)) {
                onFixed(w, z, inst, def, index, p, "gone", null);
                if (!EventManager.isUnresolved(inst.uid)) return;
            }
        }
    }

    /** Quita todos los incidentes que queden (sólo en chunks cargados: los demás se quitan solos al cargar). */
    private static void removeAll(ServerWorld w, NbtCompound d) {
        NbtList list = d.getList("b", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound r = list.getCompound(i);
            BlockPos p = BlockPos.fromLong(r.getLong("p"));
            if (w.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) IncidentModule.removeSilently(w, p);
        }
    }

    // ------------------------------------------------------------------ paquete

    /** Premio pequeño al abrir el paquete, con tope semanal por dueño (y el dinero con el tope del núcleo). */
    private void openPackage(ServerWorld w, ShopZones.Zone z, ServerPlayerEntity p, Random rng) {
        long day = w.getTimeOfDay() / 24000;
        IncidentState st = IncidentState.get(w.getServer());
        if (weeklyCap <= 0 || st.packagesThisWeek(z.owner, day) >= weeklyCap) {
            p.sendMessage(Text.translatable("tcgshop.incident.package.empty").formatted(Formatting.GRAY), false);
            return;
        }
        st.addPackage(z.owner, day);
        int n = 0;
        if (!loot.isEmpty()) {
            LootTable table = w.getServer().getReloadableRegistries().getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of(loot)));
            LootContextParameterSet params = new LootContextParameterSet.Builder(w)
                    .add(LootContextParameters.ORIGIN, p.getPos())
                    .build(LootContextTypes.CHEST);
            for (int k = 0; k < rolls; k++)
                for (ItemStack s : table.generateLoot(params)) {
                    if (s.isEmpty() || !IncidentModule.safeLoot(s)) continue;
                    n += s.getCount();
                    p.getInventory().offerOrDrop(s);
                }
        }
        long money = moneyMax > 0 ? moneyMin + (moneyMax > moneyMin ? (long) (rng.nextDouble() * (moneyMax - moneyMin)) : 0) : 0;
        long given = money > 0 ? EventManager.grantMoney(w, z, money, true) : 0;
        p.sendMessage(Text.translatable(n > 0 || given > 0 ? "tcgshop.incident.package.opened" : "tcgshop.incident.package.empty", n)
                .formatted(n > 0 || given > 0 ? Formatting.GREEN : Formatting.GRAY), false);
    }
}
