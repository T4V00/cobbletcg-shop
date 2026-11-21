package dev.tcgshop.trade;

import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.network.TradePayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Intercambio de cartas entre jugadores al estilo TCG Pocket:
 *  · se pide desde la rueda de interacción de Cobblemon (o /tcgtrade) y el otro acepta;
 *  · cada uno ofrece UNA carta de su inventario; deben ser de la MISMA rareza y como mucho Ilustración Rara;
 *  · los dos confirman; cualquier cambio de oferta quita las confirmaciones;
 *  · el servidor vuelve a comprobar que ambos siguen teniendo la carta y hace el cambio en el mismo tick
 *    (quitar ambas y luego entregar ambas), así no hay duplicados ni pérdidas;
 *  · se cancela si alguien se desconecta, muere, cambia de dimensión o se aleja más de 12 bloques.
 */
public final class TradeManager {
    public static final double MAX_DIST = 12;
    private static final long INVITE_TICKS = 20 * 30;

    private static final class Session {
        final UUID a, b;
        ItemStack offerA = ItemStack.EMPTY, offerB = ItemStack.EMPTY;
        int slotA = -1, slotB = -1;
        boolean okA, okB;

        Session(UUID a, UUID b) { this.a = a; this.b = b; }

        boolean isA(UUID p) { return a.equals(p); }
        UUID other(UUID p) { return isA(p) ? b : a; }
    }

    /** invitado -> (quien invita -> tick de caducidad). */
    private static final Map<UUID, Map<UUID, Long>> INVITES = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private TradeManager() {}

    public static boolean tradeable(ItemStack st) {
        if (!CardInfo.isCard(st) || dev.tcgshop.grading.Grading.isGraded(st)) return false;
        Rarity r = Rarity.of(st);
        return r != Rarity.UNKNOWN && r.tier <= 2;
    }

    // ------------------------------------------------------------------ invitaciones

    public static void request(ServerPlayerEntity from, UUID targetId) {
        MinecraftServer server = from.getServer();
        if (server == null) return;
        ServerPlayerEntity to = server.getPlayerManager().getPlayer(targetId);
        if (to == null || to == from) return;
        if (!near(from, to)) { from.sendMessage(Text.translatable("tcgshop.trade.too_far").formatted(Formatting.RED), true); return; }
        if (SESSIONS.containsKey(from.getUuid())) { from.sendMessage(Text.translatable("tcgshop.trade.busy_self").formatted(Formatting.RED), true); return; }
        if (SESSIONS.containsKey(to.getUuid())) { from.sendMessage(Text.translatable("tcgshop.trade.busy", to.getDisplayName()).formatted(Formatting.RED), true); return; }
        long now = server.getOverworld().getTime();
        // si el otro ya me había invitado, aceptar directamente
        Long theirs = INVITES.getOrDefault(from.getUuid(), Map.of()).get(to.getUuid());
        if (theirs != null && theirs > now) {
            INVITES.get(from.getUuid()).remove(to.getUuid());
            open(to, from);
            return;
        }
        INVITES.computeIfAbsent(to.getUuid(), k -> new HashMap<>()).put(from.getUuid(), now + INVITE_TICKS);
        String name = from.getGameProfile().getName();
        Text accept = Text.translatable("tcgshop.trade.accept_btn").setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgtrade accept " + name))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("tcgshop.trade.accept_hover"))));
        Text deny = Text.translatable("tcgshop.trade.deny_btn").setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgtrade deny " + name)));
        to.sendMessage(Text.translatable("tcgshop.trade.invite", from.getDisplayName()).formatted(Formatting.AQUA)
                .append(" ").append(accept).append(" ").append(deny), false);
        to.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.8f, 1.4f);
        from.sendMessage(Text.translatable("tcgshop.trade.sent", to.getDisplayName()).formatted(Formatting.GRAY), true);
    }

    public static void respond(ServerPlayerEntity to, String fromName, boolean accept) {
        MinecraftServer server = to.getServer();
        if (server == null) return;
        ServerPlayerEntity from = server.getPlayerManager().getPlayer(fromName);
        Map<UUID, Long> mine = INVITES.get(to.getUuid());
        Long exp = from == null || mine == null ? null : mine.remove(from.getUuid());
        if (from == null || exp == null || exp < server.getOverworld().getTime()) {
            to.sendMessage(Text.translatable("tcgshop.trade.no_invite").formatted(Formatting.RED), false);
            return;
        }
        if (!accept) {
            from.sendMessage(Text.translatable("tcgshop.trade.denied", to.getDisplayName()).formatted(Formatting.YELLOW), false);
            return;
        }
        if (SESSIONS.containsKey(from.getUuid()) || SESSIONS.containsKey(to.getUuid())) {
            to.sendMessage(Text.translatable("tcgshop.trade.busy", from.getDisplayName()).formatted(Formatting.RED), false);
            return;
        }
        if (!near(from, to)) { to.sendMessage(Text.translatable("tcgshop.trade.too_far").formatted(Formatting.RED), false); return; }
        open(from, to);
    }

    private static void open(ServerPlayerEntity a, ServerPlayerEntity b) {
        Session s = new Session(a.getUuid(), b.getUuid());
        SESSIONS.put(a.getUuid(), s);
        SESSIONS.put(b.getUuid(), s);
        ServerPlayNetworking.send(a, new TradePayloads.Open(b.getGameProfile().getName()));
        ServerPlayNetworking.send(b, new TradePayloads.Open(a.getGameProfile().getName()));
        sync(a.getServer(), s, "");
    }

    // ------------------------------------------------------------------ dentro del intercambio

    public static void select(ServerPlayerEntity p, int slot) {
        Session s = SESSIONS.get(p.getUuid());
        if (s == null || slot < 0 || (slot >= 36 && !dev.tcgshop.collection.BinderSlots.isBinder(slot))) return;
        ItemStack st = dev.tcgshop.collection.BinderSlots.peek(p, slot);
        if (!tradeable(st)) { sendState(p, s, "tcgshop.trade.not_tradeable"); return; }
        ItemStack one = st.copyWithCount(1);
        if (s.isA(p.getUuid())) { s.offerA = one; s.slotA = slot; } else { s.offerB = one; s.slotB = slot; }
        s.okA = s.okB = false;
        sync(p.getServer(), s, "");
    }

    public static void answer(ServerPlayerEntity p, boolean confirm) {
        Session s = SESSIONS.get(p.getUuid());
        if (s == null) return;
        if (!confirm) { cancel(p.getServer(), s, "tcgshop.trade.cancelled_by", p.getGameProfile().getName()); return; }
        if (s.offerA.isEmpty() || s.offerB.isEmpty()) { sendState(p, s, "tcgshop.trade.need_both"); return; }
        if (Rarity.of(s.offerA) != Rarity.of(s.offerB)) { sendState(p, s, "tcgshop.trade.same_rarity"); return; }
        if (s.isA(p.getUuid())) s.okA = true; else s.okB = true;
        if (s.okA && s.okB) execute(p.getServer(), s);
        else sync(p.getServer(), s, "");
    }

    private static int find(ServerPlayerEntity p, ItemStack want, int hint) {
        var inv = p.getInventory();
        if (hint >= 0 && hint < 36 && ItemStack.areItemsAndComponentsEqual(inv.getStack(hint), want)) return hint;
        for (int i = 0; i < 36; i++) if (ItemStack.areItemsAndComponentsEqual(inv.getStack(i), want)) return i;
        return dev.tcgshop.collection.BinderSlots.findInBinder(p, want); // también desde el Binder
    }

    private static void execute(MinecraftServer server, Session s) {
        ServerPlayerEntity a = server.getPlayerManager().getPlayer(s.a), b = server.getPlayerManager().getPlayer(s.b);
        if (a == null || b == null) { cancel(server, s, "tcgshop.trade.left", ""); return; }
        int ia = find(a, s.offerA, s.slotA), ib = find(b, s.offerB, s.slotB);
        if (ia < 0 || ib < 0 || !tradeable(s.offerA) || !tradeable(s.offerB) || Rarity.of(s.offerA) != Rarity.of(s.offerB)) {
            if (ia < 0) { s.offerA = ItemStack.EMPTY; s.slotA = -1; }
            if (ib < 0) { s.offerB = ItemStack.EMPTY; s.slotB = -1; }
            s.okA = s.okB = false;
            sync(server, s, "tcgshop.trade.changed");
            return;
        }
        // quitar primero las dos cartas y luego entregar: todo en el mismo tick del servidor
        ItemStack fromA = dev.tcgshop.collection.BinderSlots.isBinder(ia) ? dev.tcgshop.collection.BinderSlots.take(a, ia, 1) : a.getInventory().removeStack(ia, 1);
        ItemStack fromB = dev.tcgshop.collection.BinderSlots.isBinder(ib) ? dev.tcgshop.collection.BinderSlots.take(b, ib, 1) : b.getInventory().removeStack(ib, 1);
        if (dev.tcgshop.collection.BinderSlots.isBinder(ia)) dev.tcgshop.collection.BinderSlots.sync(a);
        if (dev.tcgshop.collection.BinderSlots.isBinder(ib)) dev.tcgshop.collection.BinderSlots.sync(b);
        give(a, fromB.copy());
        give(b, fromA.copy());
        SESSIONS.remove(s.a);
        SESSIONS.remove(s.b);
        for (ServerPlayerEntity p : new ServerPlayerEntity[]{a, b}) {
            boolean isA = p == a;
            ServerPlayNetworking.send(p, new TradePayloads.Done(isA ? fromA : fromB, isA ? fromB : fromA, true, "tcgshop.trade.success"));
            p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.5f);
            int n = Stats.get(server).add(p.getUuid());
            CardAdvancements.grant(p, "trade_first");
            if (n >= 10) CardAdvancements.grant(p, "trade_10");
        }
        a.sendMessage(Text.translatable("tcgshop.trade.log", fromA.toHoverableText(), fromB.toHoverableText(), b.getDisplayName()).formatted(Formatting.GREEN), false);
        b.sendMessage(Text.translatable("tcgshop.trade.log", fromB.toHoverableText(), fromA.toHoverableText(), a.getDisplayName()).formatted(Formatting.GREEN), false);
    }

    private static void give(ServerPlayerEntity p, ItemStack st) {
        if (!p.getInventory().insertStack(st) && !st.isEmpty()) p.dropItem(st, false);
        p.playerScreenHandler.sendContentUpdates();
    }

    private static void cancel(MinecraftServer server, Session s, String key, String arg) {
        SESSIONS.remove(s.a);
        SESSIONS.remove(s.b);
        for (UUID id : new UUID[]{s.a, s.b}) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) ServerPlayNetworking.send(p, new TradePayloads.Done(ItemStack.EMPTY, ItemStack.EMPTY, false, key + (arg.isEmpty() ? "" : "|" + arg)));
        }
    }

    private static void sync(MinecraftServer server, Session s, String msg) {
        if (server == null) return;
        for (UUID id : new UUID[]{s.a, s.b}) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) sendState(p, s, msg);
        }
    }

    private static void sendState(ServerPlayerEntity p, Session s, String msg) {
        boolean isA = s.isA(p.getUuid());
        ServerPlayNetworking.send(p, new TradePayloads.State(isA ? s.offerA : s.offerB, isA ? s.offerB : s.offerA,
                isA ? s.okA : s.okB, isA ? s.okB : s.okA, msg));
    }

    private static boolean near(ServerPlayerEntity a, ServerPlayerEntity b) {
        return a.getWorld() == b.getWorld() && a.isAlive() && b.isAlive() && a.squaredDistanceTo(b) <= MAX_DIST * MAX_DIST;
    }

    // ------------------------------------------------------------------ mantenimiento

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return;
        long now = server.getOverworld().getTime();
        INVITES.values().forEach(m -> m.values().removeIf(t -> t < now));
        INVITES.values().removeIf(Map::isEmpty);
        for (Session s : SESSIONS.values().stream().distinct().toList()) {
            ServerPlayerEntity a = server.getPlayerManager().getPlayer(s.a), b = server.getPlayerManager().getPlayer(s.b);
            if (a == null || b == null) cancel(server, s, "tcgshop.trade.left", "");
            else if (!near(a, b)) cancel(server, s, "tcgshop.trade.too_far", "");
        }
    }

    public static void disconnect(ServerPlayerEntity p) {
        INVITES.remove(p.getUuid());
        Session s = SESSIONS.get(p.getUuid());
        if (s != null) cancel(p.getServer(), s, "tcgshop.trade.left", "");
    }

    // ------------------------------------------------------------------ estadística persistente (para logros)

    public static final class Stats extends PersistentState {
        private static final Type<Stats> TYPE = new Type<>(Stats::new, Stats::fromNbt, null);
        private final Map<UUID, Integer> trades = new HashMap<>();

        static Stats get(MinecraftServer server) {
            return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_trades");
        }

        int add(UUID id) {
            int n = trades.merge(id, 1, Integer::sum);
            markDirty();
            return n;
        }

        private static Stats fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            Stats s = new Stats();
            NbtCompound t = nbt.getCompound("trades");
            for (String k : t.getKeys()) {
                try { s.trades.put(UUID.fromString(k), t.getInt(k)); } catch (IllegalArgumentException ignored) {}
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            NbtCompound t = new NbtCompound();
            trades.forEach((k, v) -> t.putInt(k.toString(), v));
            nbt.put("trades", t);
            return nbt;
        }
    }
}
