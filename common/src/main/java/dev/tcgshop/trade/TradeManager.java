package dev.tcgshop.trade;

import dev.masterball.api.platform.Net;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.network.TradePayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TCG Pocket-style card trading between players:
 *  · requested from the Cobblemon interaction wheel (or /tcgtrade) and the other player accepts;
 *  · each one offers ONE card from their inventory; they must be the SAME rarity and at most Illustration Rare;
 *  · both confirm; any change to an offer clears the confirmations;
 *  · the server re-checks that both still have the card and performs the swap in the same tick
 *    (remove both, then hand over both), so there are no duplicates or losses;
 *  · cancelled if someone disconnects, dies, changes dimension or moves more than 12 blocks away.
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

    /** invitee -> (inviter -> expiry tick). */
    private static final Map<UUID, Map<UUID, Long>> INVITES = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private TradeManager() {}

    public static boolean tradeable(ItemStack st) {
        if (!CardInfo.isCard(st) || dev.tcgshop.grading.Grading.isGraded(st)) return false;
        Rarity r = Rarity.of(st);
        return r != Rarity.UNKNOWN && r.tier <= 2;
    }

    // ------------------------------------------------------------------ invitations

    public static void request(ServerPlayer from, UUID targetId) {
        MinecraftServer server = from.getServer();
        if (server == null) return;
        ServerPlayer to = server.getPlayerList().getPlayer(targetId);
        if (to == null || to == from) return;
        if (!near(from, to)) { from.displayClientMessage(Component.translatable("tcgshop.trade.too_far").withStyle(ChatFormatting.RED), true); return; }
        if (SESSIONS.containsKey(from.getUUID())) { from.displayClientMessage(Component.translatable("tcgshop.trade.busy_self").withStyle(ChatFormatting.RED), true); return; }
        if (SESSIONS.containsKey(to.getUUID())) { from.displayClientMessage(Component.translatable("tcgshop.trade.busy", to.getDisplayName()).withStyle(ChatFormatting.RED), true); return; }
        long now = server.overworld().getGameTime();
        // if the other player had already invited me, accept directly
        Long theirs = INVITES.getOrDefault(from.getUUID(), Map.of()).get(to.getUUID());
        if (theirs != null && theirs > now) {
            INVITES.get(from.getUUID()).remove(to.getUUID());
            open(to, from);
            return;
        }
        INVITES.computeIfAbsent(to.getUUID(), k -> new HashMap<>()).put(from.getUUID(), now + INVITE_TICKS);
        String name = from.getGameProfile().getName();
        Component accept = Component.translatable("tcgshop.trade.accept_btn").setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgtrade accept " + name))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("tcgshop.trade.accept_hover"))));
        Component deny = Component.translatable("tcgshop.trade.deny_btn").setStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tcgtrade deny " + name)));
        to.displayClientMessage(Component.translatable("tcgshop.trade.invite", from.getDisplayName()).withStyle(ChatFormatting.AQUA)
                .append(" ").append(accept).append(" ").append(deny), false);
        to.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
        from.displayClientMessage(Component.translatable("tcgshop.trade.sent", to.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
    }

    public static void respond(ServerPlayer to, String fromName, boolean accept) {
        MinecraftServer server = to.getServer();
        if (server == null) return;
        ServerPlayer from = server.getPlayerList().getPlayerByName(fromName);
        Map<UUID, Long> mine = INVITES.get(to.getUUID());
        Long exp = from == null || mine == null ? null : mine.remove(from.getUUID());
        if (from == null || exp == null || exp < server.overworld().getGameTime()) {
            to.displayClientMessage(Component.translatable("tcgshop.trade.no_invite").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!accept) {
            from.displayClientMessage(Component.translatable("tcgshop.trade.denied", to.getDisplayName()).withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        if (SESSIONS.containsKey(from.getUUID()) || SESSIONS.containsKey(to.getUUID())) {
            to.displayClientMessage(Component.translatable("tcgshop.trade.busy", from.getDisplayName()).withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!near(from, to)) { to.displayClientMessage(Component.translatable("tcgshop.trade.too_far").withStyle(ChatFormatting.RED), false); return; }
        open(from, to);
    }

    private static void open(ServerPlayer a, ServerPlayer b) {
        Session s = new Session(a.getUUID(), b.getUUID());
        SESSIONS.put(a.getUUID(), s);
        SESSIONS.put(b.getUUID(), s);
        Net.INSTANCE.send(a, new TradePayloads.Open(b.getGameProfile().getName()));
        Net.INSTANCE.send(b, new TradePayloads.Open(a.getGameProfile().getName()));
        sync(a.getServer(), s, "");
    }

    // ------------------------------------------------------------------ inside the trade

    public static void select(ServerPlayer p, int slot) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || slot < 0 || (slot >= 36 && !dev.tcgshop.collection.BinderSlots.isBinder(slot))) return;
        ItemStack st = dev.tcgshop.collection.BinderSlots.peek(p, slot);
        if (!tradeable(st)) { sendState(p, s, "tcgshop.trade.not_tradeable"); return; }
        // 1.0 beta 3: a card stolen from a collector who is still hunting for it cannot change hands
        if (dev.tcgshop.entity.CollectorTheft.isHot(p.getServer(), st)) { sendState(p, s, "tcgshop.collector_theft.hot"); return; }
        ItemStack one = st.copyWithCount(1);
        if (s.isA(p.getUUID())) { s.offerA = one; s.slotA = slot; } else { s.offerB = one; s.slotB = slot; }
        s.okA = s.okB = false;
        sync(p.getServer(), s, "");
    }

    public static void answer(ServerPlayer p, boolean confirm) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        if (!confirm) { cancel(p.getServer(), s, "tcgshop.trade.cancelled_by", p.getGameProfile().getName()); return; }
        if (s.offerA.isEmpty() || s.offerB.isEmpty()) { sendState(p, s, "tcgshop.trade.need_both"); return; }
        if (Rarity.of(s.offerA).family != Rarity.of(s.offerB).family) { sendState(p, s, "tcgshop.trade.same_rarity"); return; }
        if (s.isA(p.getUUID())) s.okA = true; else s.okB = true;
        if (s.okA && s.okB) execute(p.getServer(), s);
        else sync(p.getServer(), s, "");
    }

    private static int find(ServerPlayer p, ItemStack want, int hint) {
        var inv = p.getInventory();
        if (hint >= 0 && hint < 36 && ItemStack.isSameItemSameComponents(inv.getItem(hint), want)) return hint;
        for (int i = 0; i < 36; i++) if (ItemStack.isSameItemSameComponents(inv.getItem(i), want)) return i;
        // also from the Binder, which only stores standard copies: a variant (reverse, 1st edition...) has to be in the inventory
        if (dev.masterball.api.card.PrintVariant.isSpecial(want)) return -1;
        return dev.tcgshop.collection.BinderSlots.findInBinder(p, want);
    }

    private static void execute(MinecraftServer server, Session s) {
        ServerPlayer a = server.getPlayerList().getPlayer(s.a), b = server.getPlayerList().getPlayer(s.b);
        if (a == null || b == null) { cancel(server, s, "tcgshop.trade.left", ""); return; }
        int ia = find(a, s.offerA, s.slotA), ib = find(b, s.offerB, s.slotB);
        if (ia < 0 || ib < 0 || !tradeable(s.offerA) || !tradeable(s.offerB) || Rarity.of(s.offerA).family != Rarity.of(s.offerB).family) {
            if (ia < 0) { s.offerA = ItemStack.EMPTY; s.slotA = -1; }
            if (ib < 0) { s.offerB = ItemStack.EMPTY; s.slotB = -1; }
            s.okA = s.okB = false;
            sync(server, s, "tcgshop.trade.changed");
            return;
        }
        // remove both cards first, then hand them over: all in the same server tick
        ItemStack fromA = dev.tcgshop.collection.BinderSlots.isBinder(ia) ? dev.tcgshop.collection.BinderSlots.take(a, ia, 1) : a.getInventory().removeItem(ia, 1);
        ItemStack fromB = dev.tcgshop.collection.BinderSlots.isBinder(ib) ? dev.tcgshop.collection.BinderSlots.take(b, ib, 1) : b.getInventory().removeItem(ib, 1);
        if (dev.tcgshop.collection.BinderSlots.isBinder(ia)) dev.tcgshop.collection.BinderSlots.sync(a);
        if (dev.tcgshop.collection.BinderSlots.isBinder(ib)) dev.tcgshop.collection.BinderSlots.sync(b);
        give(a, fromB.copy());
        give(b, fromA.copy());
        SESSIONS.remove(s.a);
        SESSIONS.remove(s.b);
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            boolean isA = p == a;
            Net.INSTANCE.send(p, new TradePayloads.Done(isA ? fromA : fromB, isA ? fromB : fromA, true, "tcgshop.trade.success"));
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.5f);
            int n = Stats.get(server).add(p.getUUID());
            CardAdvancements.grant(p, "trade_first");
            if (n >= 10) CardAdvancements.grant(p, "trade_10");
        }
        a.displayClientMessage(Component.translatable("tcgshop.trade.log", fromA.getDisplayName(), fromB.getDisplayName(), b.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        b.displayClientMessage(Component.translatable("tcgshop.trade.log", fromB.getDisplayName(), fromA.getDisplayName(), a.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
    }

    private static void give(ServerPlayer p, ItemStack st) {
        if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
        p.inventoryMenu.broadcastChanges();
    }

    private static void cancel(MinecraftServer server, Session s, String key, String arg) {
        SESSIONS.remove(s.a);
        SESSIONS.remove(s.b);
        for (UUID id : new UUID[]{s.a, s.b}) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) Net.INSTANCE.send(p, new TradePayloads.Done(ItemStack.EMPTY, ItemStack.EMPTY, false, key + (arg.isEmpty() ? "" : "|" + arg)));
        }
    }

    private static void sync(MinecraftServer server, Session s, String msg) {
        if (server == null) return;
        for (UUID id : new UUID[]{s.a, s.b}) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) sendState(p, s, msg);
        }
    }

    private static void sendState(ServerPlayer p, Session s, String msg) {
        boolean isA = s.isA(p.getUUID());
        Net.INSTANCE.send(p, new TradePayloads.State(isA ? s.offerA : s.offerB, isA ? s.offerB : s.offerA,
                isA ? s.okA : s.okB, isA ? s.okB : s.okA, msg));
    }

    private static boolean near(ServerPlayer a, ServerPlayer b) {
        return a.level() == b.level() && a.isAlive() && b.isAlive() && a.distanceToSqr(b) <= MAX_DIST * MAX_DIST;
    }

    // ------------------------------------------------------------------ maintenance

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        long now = server.overworld().getGameTime();
        INVITES.values().forEach(m -> m.values().removeIf(t -> t < now));
        INVITES.values().removeIf(Map::isEmpty);
        for (Session s : SESSIONS.values().stream().distinct().toList()) {
            ServerPlayer a = server.getPlayerList().getPlayer(s.a), b = server.getPlayerList().getPlayer(s.b);
            if (a == null || b == null) cancel(server, s, "tcgshop.trade.left", "");
            else if (!near(a, b)) cancel(server, s, "tcgshop.trade.too_far", "");
        }
    }

    public static void disconnect(ServerPlayer p) {
        INVITES.remove(p.getUUID());
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) cancel(p.getServer(), s, "tcgshop.trade.left", "");
    }

    // ------------------------------------------------------------------ persistent stats (for advancements)

    public static final class Stats extends SavedData {
        private static final Factory<Stats> TYPE = new Factory<>(Stats::new, Stats::fromNbt, null);
        private final Map<UUID, Integer> trades = new HashMap<>();

        static Stats get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_trades");
        }

        int add(UUID id) {
            int n = trades.merge(id, 1, Integer::sum);
            setDirty();
            return n;
        }

        private static Stats fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
            Stats s = new Stats();
            CompoundTag t = nbt.getCompound("trades");
            for (String k : t.getAllKeys()) {
                try { s.trades.put(UUID.fromString(k), t.getInt(k)); } catch (IllegalArgumentException ignored) {}
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
            CompoundTag t = new CompoundTag();
            trades.forEach((k, v) -> t.putInt(k.toString(), v));
            nbt.put("trades", t);
            return nbt;
        }
    }
}
