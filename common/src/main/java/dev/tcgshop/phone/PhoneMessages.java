package dev.tcgshop.phone;

import dev.masterball.api.platform.Net;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 1.0 phone Messages app, server side: every player's inbox (stored in the Overworld: data/tcgshop_messages.dat), so a
 * message sent to an offline player waits for them. Messages come from the system, the Wholesaler (gold border, loan
 * offers with Accept / Decline), other players (sent by name, online or offline) and NPC contacts, who answer a message
 * with a canned reply after a few seconds.
 * <p>
 * Public contract (other systems call it): {@link #send(MinecraftServer, UUID, String, Component, boolean)}.
 * The client gets the whole inbox (it is small: at most {@link #MAX_PER_PLAYER}) through {@link PhonePayloads.Apps}
 * with k = "messages" on join and after every change.
 */
public final class PhoneMessages extends SavedData {
    /** Inbox size per player: the oldest read messages are dropped first. */
    public static final int MAX_PER_PLAYER = 60;
    /** Longest text a player may send. */
    public static final int MAX_BODY = 240;
    public static final String KIND_TEXT = "text", KIND_LOAN_OFFER = "loan_offer";
    public static final String SENDER_SYSTEM = "tcgshop.msg.sender.system", SENDER_WHOLESALER = "tcgshop.msg.sender.wholesaler",
            SENDER_SHOP = "tcgshop.msg.sender.shop", SENDER_PLAYER = "tcgshop.msg.sender.player";
    /** NPC contacts you can write to; each one answers with one of {@link #CANNED_REPLIES} lines (tcgshop.msg.npc.<id>.<n>). */
    public static final List<String> NPC_CONTACTS = List.of("wholesaler", "collector", "grader");
    public static final int CANNED_REPLIES = 6;
    /** Minimum ticks between two messages sent by the same player (anti-spam). */
    private static final long SEND_COOLDOWN = 40;

    /** One message of an inbox. */
    public static final class Msg {
        public long id;
        /** Translation key of the sender's display name (players use {@link #senderName} instead). */
        public String sender = SENDER_SYSTEM;
        /** Literal sender name (players), or "". */
        public String senderName = "";
        /** Sending player, or null. */
        public @Nullable UUID senderId;
        /** NPC contact that wrote it ("wholesaler", ...), or "": lets the client offer «Reply». */
        public String contact = "";
        /** Body as Text JSON (Text.Serialization), so translatable texts are shown in the reader's language. */
        public String body = "";
        public boolean read, gold;
        /** In-game day (timeOfDay / 24000) and world tick when it arrived. */
        public long day, tick;
        public String kind = KIND_TEXT;
        /** Kind-specific data (loan offer: amount, rate, term, exp, st). */
        public CompoundTag extra = new CompoundTag();

        CompoundTag write() {
            CompoundTag c = new CompoundTag();
            c.putLong("id", id);
            c.putString("s", sender);
            if (!senderName.isEmpty()) c.putString("sn", senderName);
            if (senderId != null) c.putUUID("su", senderId);
            if (!contact.isEmpty()) c.putString("ct", contact);
            c.putString("b", body);
            c.putBoolean("r", read);
            c.putBoolean("g", gold);
            c.putLong("d", day);
            c.putLong("t", tick);
            c.putString("k", kind);
            if (!extra.isEmpty()) c.put("x", extra.copy());
            return c;
        }

        static Msg read(CompoundTag c) {
            Msg m = new Msg();
            m.id = c.getLong("id");
            m.sender = c.contains("s") ? c.getString("s") : SENDER_SYSTEM;
            m.senderName = c.getString("sn");
            m.senderId = c.hasUUID("su") ? c.getUUID("su") : null;
            m.contact = c.getString("ct");
            m.body = c.getString("b");
            m.read = c.getBoolean("r");
            m.gold = c.getBoolean("g");
            m.day = c.getLong("d");
            m.tick = c.getLong("t");
            m.kind = c.contains("k") ? c.getString("k") : KIND_TEXT;
            m.extra = c.getCompound("x");
            return m;
        }
    }

    /** A canned reply waiting to be delivered. Not saved (a restart simply loses the reply). */
    private record PendingReply(UUID to, String contact, long dueTick) {}

    private static final Factory<PhoneMessages> TYPE = new Factory<>(PhoneMessages::new, PhoneMessages::fromNbt, null);

    private final Map<UUID, List<Msg>> inbox = new HashMap<>();
    private long nextId = 1;
    private static final List<PendingReply> PENDING = new ArrayList<>();
    private static final Map<UUID, Long> LAST_SEND = new HashMap<>();

    public static PhoneMessages get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE, "tcgshop_messages");
    }

    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    // ------------------------------------------------------------------ public API (contract)

    /**
     * Sends a message to a player: it is stored (works offline; they see it next time) and, if they are online, their
     * inbox is synced and a phone notice is shown. senderKey = translation key of the sender's name (e.g.
     * "tcgshop.msg.sender.wholesaler"); gold = gold border (Wholesaler offers).
     */
    public static void send(MinecraftServer server, UUID to, String senderKey, Component body, boolean gold) {
        sendFull(server, to, senderKey, "", null, "", body, gold, KIND_TEXT, null);
    }

    /** Full form used by the phone systems (loan offers, player and NPC messages). Returns the stored message. */
    public static @Nullable Msg sendFull(MinecraftServer server, UUID to, String senderKey, String senderName, @Nullable UUID senderId,
                                         String contact, Component body, boolean gold, String kind, @Nullable CompoundTag extra) {
        if (server == null || to == null || body == null) return null;
        PhoneMessages st = get(server);
        Msg m = new Msg();
        m.id = st.nextId++;
        m.sender = senderKey == null || senderKey.isEmpty() ? SENDER_SYSTEM : senderKey;
        m.senderName = senderName == null ? "" : senderName;
        m.senderId = senderId;
        m.contact = contact == null ? "" : contact;
        try {
            m.body = Component.Serializer.toJson(body, server.registryAccess());
        } catch (Exception e) {
            m.body = Component.Serializer.toJson(Component.literal(body.getString()), server.registryAccess());
        }
        m.gold = gold;
        m.day = day(server);
        m.tick = server.overworld().getGameTime();
        m.kind = kind == null ? KIND_TEXT : kind;
        if (extra != null) m.extra = extra.copy();
        List<Msg> list = st.inbox.computeIfAbsent(to, k -> new ArrayList<>());
        list.add(0, m);
        trim(list, m.day);
        st.setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(to);
        if (online != null) sync(online, m);
        return m;
    }

    /** The player's message with that id, or null. */
    public static @Nullable Msg find(MinecraftServer server, UUID player, long id) {
        List<Msg> list = get(server).inbox.get(player);
        if (list == null) return null;
        for (Msg m : list) if (m.id == id) return m;
        return null;
    }

    public static int unread(MinecraftServer server, UUID player) {
        List<Msg> list = get(server).inbox.get(player);
        int n = 0;
        if (list != null) for (Msg m : list) if (!m.read) n++;
        return n;
    }

    /** Marks a message as changed (e.g. a loan offer answered) and resends the inbox to its owner if online. */
    public static void changed(MinecraftServer server, UUID player) {
        get(server).setDirty();
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) sync(online, null);
    }

    /**
     * Keeps the inbox at MAX_PER_PLAYER: drops the oldest read message, else the oldest one. Unanswered (still valid)
     * loan offers are pinned and never dropped.
     */
    private static void trim(List<Msg> list, long day) {
        while (list.size() > MAX_PER_PLAYER) {
            int drop = -1, oldest = -1;
            for (int i = list.size() - 1; i >= 0; i--) {
                Msg m = list.get(i);
                if (isPinned(m, day)) continue;
                if (oldest < 0) oldest = i;
                if (m.read) { drop = i; break; }
            }
            if (drop < 0) drop = oldest;
            if (drop < 0) return; // only pinned offers left (cannot happen with one pending offer per player)
            list.remove(drop);
        }
    }

    /** An unanswered loan offer that can still be accepted: never evicted from a full inbox. */
    private static boolean isPinned(Msg m, long day) {
        return isPendingOffer(m) && day <= m.extra.getLong("exp");
    }

    // ------------------------------------------------------------------ sync

    /** Sends the whole inbox; fresh = the message that just arrived (the client shows a notice for it), or null. */
    public static void sync(ServerPlayer p, @Nullable Msg fresh) {
        if (!Net.INSTANCE.canSend(p, PhonePayloads.Apps.ID)) return;
        CompoundTag d = new CompoundTag();
        d.putString("k", "messages");
        ListTag l = new ListTag();
        List<Msg> list = get(p.getServer()).inbox.get(p.getUUID());
        if (list != null) for (Msg m : list) l.add(m.write());
        d.put("list", l);
        d.putLong("day", day(p.getServer()));
        if (fresh != null) d.putLong("fresh", fresh.id);
        Net.INSTANCE.send(p, new PhonePayloads.Apps(d));
    }

    // ------------------------------------------------------------------ client actions

    /** Client -> server actions of the Messages app (PhonePayloads.Act with a = msg_*). */
    static void handle(ServerPlayer p, CompoundTag d) {
        MinecraftServer server = p.getServer();
        PhoneMessages st = get(server);
        List<Msg> list = st.inbox.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        switch (d.getString("a")) {
            case "messages" -> sync(p, null);
            case "msg_read" -> {
                long id = d.getLong("id");
                for (Msg m : list) if (m.id == id && !m.read) { m.read = true; st.setDirty(); }
                sync(p, null);
            }
            case "msg_read_all" -> {
                for (Msg m : list) if (!m.read) { m.read = true; st.setDirty(); }
                sync(p, null);
            }
            case "msg_delete" -> {
                long id = d.getLong("id");
                if (list.removeIf(m -> m.id == id)) st.setDirty();
                sync(p, null);
            }
            case "msg_clear" -> { // deletes every read message (pending loan offers are kept)
                if (list.removeIf(m -> m.read && !isPendingOffer(m))) st.setDirty();
                sync(p, null);
            }
            case "msg_send" -> playerSend(p, d.getString("to"), d.getString("body"));
            default -> {}
        }
    }

    private static boolean isPendingOffer(Msg m) {
        return KIND_LOAN_OFFER.equals(m.kind) && m.extra.getString("st").isEmpty();
    }

    /** Cleans what a player typed: no formatting codes or control characters, at most MAX_BODY characters. */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < MAX_BODY; i++) {
            char c = s.charAt(i);
            if (c == '§' || Character.isISOControl(c)) continue;
            b.append(c);
        }
        return b.toString().trim();
    }

    private static void playerSend(ServerPlayer p, String toRaw, String bodyRaw) {
        MinecraftServer server = p.getServer();
        if (!PhoneUpgrades.hasPhone(p)) return;
        String body = clean(bodyRaw), to = toRaw == null ? "" : toRaw.trim();
        if (body.isEmpty() || to.isEmpty()) {
            PhoneApps.notice(p, "error", "tcgshop.msg.notice.empty.title", "tcgshop.msg.notice.empty");
            return;
        }
        if (to.length() > 20 || !to.matches("@?[A-Za-z0-9_.\\-]+")) { // player names (+ '.'/'-' for proxy-prefixed names); anything else never reaches the name lookup
            PhoneApps.notice(p, "error", "tcgshop.msg.notice.unknown.title", "tcgshop.msg.notice.unknown", clean(to));
            return;
        }
        long now = server.overworld().getGameTime();
        Long last = LAST_SEND.get(p.getUUID());
        if (last != null && now >= last && now - last < SEND_COOLDOWN) {
            PhoneApps.notice(p, "error", "tcgshop.msg.notice.slow.title", "tcgshop.msg.notice.slow");
            return;
        }
        LAST_SEND.put(p.getUUID(), now);
        String senderName = p.getGameProfile().getName();
        Component text = Component.literal(body);
        // NPC contact: "@wholesaler", "@collector", "@grader"
        if (to.startsWith("@")) {
            String contact = to.substring(1).toLowerCase(java.util.Locale.ROOT);
            if (!NPC_CONTACTS.contains(contact)) {
                PhoneApps.notice(p, "error", "tcgshop.msg.notice.unknown.title", "tcgshop.msg.notice.unknown", to);
                return;
            }
            PENDING.add(new PendingReply(p.getUUID(), contact, now + 40 + server.overworld().random.nextInt(80)));
            PhoneApps.notice(p, "success", "tcgshop.msg.notice.sent.title", "tcgshop.msg.notice.sent_npc",
                    PhoneApps.KEY_ARG + "tcgshop.msg.sender." + contact);
            return;
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(to);
        if (online != null) {
            deliver(p, online.getUUID(), online.getGameProfile().getName(), true, senderName, text, to);
            return;
        }
        // offline player: the name lookup can reach Mojang's API for unknown names, so it never runs on the server
        // thread (findByNameAsync); only players who have played on this server (player data file) can be written to
        var cache = server.getProfileCache();
        if (cache == null) {
            PhoneApps.notice(p, "error", "tcgshop.msg.notice.unknown.title", "tcgshop.msg.notice.unknown", to);
            return;
        }
        cache.getAsync(to).whenComplete((gp, err) -> server.execute(() -> {
            Optional<GameProfile> prof = err == null && gp != null ? gp : Optional.empty();
            UUID target = prof.isPresent() ? prof.get().getId() : null;
            if (target != null && !java.nio.file.Files.exists(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(target + ".dat")))
                target = null; // never joined this world
            boolean nowOnline = target != null && server.getPlayerList().getPlayer(target) != null;
            deliver(p, target, prof.map(GameProfile::getName).orElse(to), nowOnline, senderName, text, to);
        }));
    }

    /** Second half of a player message once the recipient is known (target null = unknown name). */
    private static void deliver(ServerPlayer p, @Nullable UUID target, String targetName, boolean online, String senderName, Component text, String typed) {
        MinecraftServer server = p.getServer();
        boolean here = !p.hasDisconnected();
        if (target == null) {
            if (here) PhoneApps.notice(p, "error", "tcgshop.msg.notice.unknown.title", "tcgshop.msg.notice.unknown", typed);
            return;
        }
        if (target.equals(p.getUUID())) {
            if (here) PhoneApps.notice(p, "error", "tcgshop.msg.notice.self.title", "tcgshop.msg.notice.self");
            return;
        }
        sendFull(server, target, SENDER_PLAYER, senderName, p.getUUID(), "", text, false, KIND_TEXT, null);
        if (here) PhoneApps.notice(p, "success", "tcgshop.msg.notice.sent.title",
                online ? "tcgshop.msg.notice.sent" : "tcgshop.msg.notice.sent_offline", targetName);
    }

    /** Server tick: delivers the NPC contacts' canned replies. */
    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Iterator<PendingReply> it = PENDING.iterator(); it.hasNext(); ) {
            PendingReply r = it.next();
            if (now < r.dueTick && r.dueTick - now < 20 * 60) continue; // (a time jump backwards never blocks it)
            it.remove();
            int n = server.overworld().random.nextInt(CANNED_REPLIES);
            sendFull(server, r.to, "tcgshop.msg.sender." + r.contact, "", null, r.contact,
                    Component.translatable("tcgshop.msg.npc." + r.contact + "." + n), r.contact.equals("wholesaler"), KIND_TEXT, null);
        }
    }

    static void forget(UUID player) {
        LAST_SEND.remove(player);
    }

    /** Server stopped: drops the pending replies and cooldowns (they belong to that world). */
    static void clearRuntime() {
        PENDING.clear();
        LAST_SEND.clear();
    }

    // ------------------------------------------------------------------ persistence

    private static PhoneMessages fromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {
        PhoneMessages s = new PhoneMessages();
        s.nextId = Math.max(1, nbt.getLong("next"));
        CompoundTag p = nbt.getCompound("players");
        for (String k : p.getAllKeys()) {
            try {
                UUID id = UUID.fromString(k);
                ListTag l = p.getList(k, Tag.TAG_COMPOUND);
                List<Msg> list = new ArrayList<>();
                for (int i = 0; i < l.size(); i++) {
                    Msg m = Msg.read(l.getCompound(i));
                    list.add(m);
                    s.nextId = Math.max(s.nextId, m.id + 1);
                }
                s.inbox.put(id, list);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookup) {
        nbt.putLong("next", nextId);
        CompoundTag p = new CompoundTag();
        inbox.forEach((k, v) -> {
            if (v.isEmpty()) return;
            ListTag l = new ListTag();
            for (Msg m : v) l.add(m.write());
            p.put(k.toString(), l);
        });
        nbt.put("players", p);
        return nbt;
    }
}
