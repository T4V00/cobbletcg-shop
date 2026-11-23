package dev.tcgshop.client.phone;

import dev.tcgshop.item.TcgItems;
import dev.tcgshop.phone.PhoneApps;
import dev.tcgshop.phone.PhonePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side state of the 1.0 phone apps (Messages, Loans, Stats), filled by {@link PhonePayloads.Apps} and reset on
 * disconnect. Actions go to the server through {@link PhoneClient#send} (PhonePayloads.Act).
 */
public final class PhoneAppsClient {
    private PhoneAppsClient() {}

    /** One message of the inbox, as the server sent it. */
    public record Msg(long id, String sender, String senderName, String contact, Text body, boolean read, boolean gold, long day,
                      String kind, NbtCompound extra) {
        /** Display name of the sender (a player's name or the translated NPC / system name). */
        public Text from() {
            return senderName.isEmpty() ? Text.translatable(sender) : Text.literal(senderName);
        }

        public boolean loanOffer() {
            return "loan_offer".equals(kind);
        }

        /** Loan offer status: "" pending, "accepted", "declined", "expired". */
        public String offerState() {
            if (!loanOffer()) return "";
            String st = extra.getString("st");
            return st.isEmpty() && PhoneAppsClient.day > extra.getLong("exp") ? "expired" : st;
        }

        /** Who «Reply» writes to: the player's name, "@contact", or null if it cannot be answered. */
        public @Nullable String replyTo() {
            if (!senderName.isEmpty()) return senderName;
            if (!contact.isEmpty()) return "@" + contact;
            return null;
        }
    }

    public static final List<Msg> inbox = new ArrayList<>();
    /** In-game day of the server (timeOfDay / 24000) at the last sync. */
    public static long day;
    /** Last loans sync (see Loans.sync); empty until it arrives. */
    public static NbtCompound loans = new NbtCompound();
    /** Last Stats sync (see PhoneStats.build), or null. */
    public static @Nullable NbtCompound stats;
    private static boolean joinNoticeShown;
    private static long lastStatsRequest;

    /** Called from PhoneClient.register(). */
    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PhonePayloads.Apps.ID, (p, ctx) -> ctx.client().execute(() -> apply(p.data())));
    }

    static void reset() {
        inbox.clear();
        day = 0;
        loans = new NbtCompound();
        stats = null;
        joinNoticeShown = false;
        lastStatsRequest = 0;
    }

    public static int unread() {
        int n = 0;
        for (Msg m : inbox) if (!m.read) n++;
        return n;
    }

    public static @Nullable Msg find(long id) {
        for (Msg m : inbox) if (m.id == id) return m;
        return null;
    }

    public static boolean hasLoan() {
        return loans.contains("loan", NbtElement.COMPOUND_TYPE);
    }

    public static NbtCompound loan() {
        return loans.getCompound("loan");
    }

    /** Is a loan offer waiting for an answer? */
    public static boolean pendingOffer() {
        if (!loans.contains("offer")) return false;
        Msg m = find(loans.getLong("offer"));
        return m != null && m.offerState().isEmpty();
    }

    /** Text of a server argument (a String starting with PhoneApps.KEY_ARG is a translation key). */
    public static Text arg(String s) {
        return s.startsWith(PhoneApps.KEY_ARG) ? Text.translatable(s.substring(PhoneApps.KEY_ARG.length())) : Text.literal(s);
    }

    public static Object[] args(NbtList l) {
        Object[] out = new Object[l.size()];
        for (int i = 0; i < l.size(); i++) out[i] = arg(l.getString(i));
        return out;
    }

    // ------------------------------------------------------------------ receive

    private static void apply(NbtCompound d) {
        switch (d.getString("k")) {
            case "messages" -> onMessages(d);
            case "loans" -> {
                loans = d;
                day = d.getLong("day");
            }
            case "stats" -> {
                stats = d;
                day = d.getLong("day");
            }
            case "notice" -> {
                PhoneNotices.Kind kind = switch (d.getString("kind")) {
                    case "success" -> PhoneNotices.Kind.SUCCESS;
                    case "error" -> PhoneNotices.Kind.ERROR;
                    default -> PhoneNotices.Kind.INFO;
                };
                Object[] a = args(d.getList("args", NbtElement.STRING_TYPE));
                PhoneNotices.push(kind, Text.translatable(d.getString("title")), Text.translatable(d.getString("body"), a),
                        new ItemStack(TcgItems.TCG_PHONE));
                if (!phoneOpen()) overlay(Text.translatable(d.getString("body"), a).formatted(kind == PhoneNotices.Kind.ERROR ? Formatting.RED : Formatting.GREEN));
            }
            default -> {}
        }
    }

    private static void onMessages(NbtCompound d) {
        MinecraftClient mc = MinecraftClient.getInstance();
        day = d.getLong("day");
        inbox.clear();
        NbtList l = d.getList("list", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < l.size(); i++) {
            NbtCompound c = l.getCompound(i);
            inbox.add(new Msg(c.getLong("id"), c.contains("s") ? c.getString("s") : "tcgshop.msg.sender.system", c.getString("sn"),
                    c.getString("ct"), body(mc, c.getString("b")), c.getBoolean("r"), c.getBoolean("g"), c.getLong("d"),
                    c.contains("k") ? c.getString("k") : "text", c.getCompound("x")));
        }
        if (d.contains("fresh")) {
            Msg m = find(d.getLong("fresh"));
            if (m != null) {
                Text title = Text.translatable("tcgshop.msg.notice.new.title", m.from());
                PhoneNotices.push(PhoneNotices.Kind.INFO, title, m.body, new ItemStack(TcgItems.TCG_PHONE), m.id);
                if (!phoneOpen()) overlay(Text.translatable("tcgshop.msg.notice.new_overlay", m.from()).formatted(m.gold ? Formatting.GOLD : Formatting.AQUA));
                if (mc.getSoundManager() != null)
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, m.gold ? 1.2f : 1.7f));
            }
        } else if (!joinNoticeShown) { // first sync after joining: remind of unread messages
            joinNoticeShown = true;
            int n = unread();
            if (n > 0) overlay(Text.translatable("tcgshop.msg.notice.unread_join", n).formatted(Formatting.AQUA));
        }
    }

    private static Text body(MinecraftClient mc, String json) {
        if (json == null || json.isEmpty()) return Text.empty();
        try {
            MutableText t = mc.world != null ? Text.Serialization.fromJson(json, mc.world.getRegistryManager()) : null;
            if (t != null) return t;
        } catch (Exception ignored) {
            // falls back to the raw text
        }
        return Text.literal(json);
    }

    private static boolean phoneOpen() {
        var s = MinecraftClient.getInstance().currentScreen;
        return s instanceof dev.tcgshop.client.screen.PhoneScreen || s instanceof dev.tcgshop.client.screen.StoreScreen;
    }

    private static void overlay(Text t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal("✉ ").append(t), false);
    }

    // ------------------------------------------------------------------ actions

    private static void act(String a, java.util.function.Consumer<NbtCompound> fill) {
        NbtCompound d = new NbtCompound();
        d.putString("a", a);
        if (fill != null) fill.accept(d);
        PhoneClient.send(d);
    }

    public static void requestMessages() { act("messages", null); }

    public static void requestLoans() { act("loans", null); }

    /** Asks for fresh stats (at most once every 2 s). */
    public static void requestStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsRequest < 2000) return;
        lastStatsRequest = now;
        act("stats", null);
    }

    /** Marks a message as read (locally at once, then on the server). */
    public static void markRead(Msg m) {
        if (m.read) return;
        inbox.replaceAll(x -> x.id == m.id ? new Msg(x.id, x.sender, x.senderName, x.contact, x.body, true, x.gold, x.day, x.kind, x.extra) : x);
        act("msg_read", d -> d.putLong("id", m.id));
    }

    public static void readAll() { act("msg_read_all", null); }

    public static void clearRead() { act("msg_clear", null); }

    public static void delete(long id) {
        inbox.removeIf(m -> m.id == id);
        act("msg_delete", d -> d.putLong("id", id));
    }

    public static void sendMessage(String to, String body) {
        act("msg_send", d -> {
            d.putString("to", to);
            d.putString("body", body);
        });
    }

    public static void acceptLoan(long id) { act("loan_accept", d -> d.putLong("id", id)); }

    public static void declineLoan(long id) { act("loan_decline", d -> d.putLong("id", id)); }

    /** amount <= 0 = repay everything. */
    public static void repay(long amount) { act("loan_repay", d -> d.putLong("amount", amount)); }
}
