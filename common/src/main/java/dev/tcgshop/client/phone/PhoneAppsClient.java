package dev.tcgshop.client.phone;

import dev.tcgshop.item.TcgItems;
import dev.tcgshop.phone.PhoneApps;
import dev.tcgshop.phone.PhonePayloads;
import dev.masterball.api.platform.ClientPlatform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
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
    public record Msg(long id, String sender, String senderName, String contact, Component body, boolean read, boolean gold, long day,
                      String kind, CompoundTag extra) {
        /** Display name of the sender (a player's name or the translated NPC / system name). */
        public Component from() {
            return senderName.isEmpty() ? Component.translatable(sender) : Component.literal(senderName);
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
    /** 1.1: last Ranking sync (see dev.tcgshop.phone.Ranking); null until it arrives. */
    public static CompoundTag ranking;
    /** Last loans sync (see Loans.sync); empty until it arrives. */
    public static CompoundTag loans = new CompoundTag();
    /** Last Stats sync (see PhoneStats.build), or null. */
    public static @Nullable CompoundTag stats;
    private static boolean joinNoticeShown;
    private static long lastStatsRequest;

    /** Called from PhoneClient.register(). */
    static void register() {
        ClientPlatform.INSTANCE.receive(PhonePayloads.Apps.ID, p -> Minecraft.getInstance().execute(() -> apply(p.data())));
    }

    static void reset() {
        inbox.clear();
        day = 0;
        loans = new CompoundTag();
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
        return loans.contains("loan", Tag.TAG_COMPOUND);
    }

    public static CompoundTag loan() {
        return loans.getCompound("loan");
    }

    /** Is a loan offer waiting for an answer? */
    public static boolean pendingOffer() {
        if (!loans.contains("offer")) return false;
        Msg m = find(loans.getLong("offer"));
        return m != null && m.offerState().isEmpty();
    }

    /** Text of a server argument (a String starting with PhoneApps.KEY_ARG is a translation key). */
    public static Component arg(String s) {
        return s.startsWith(PhoneApps.KEY_ARG) ? Component.translatable(s.substring(PhoneApps.KEY_ARG.length())) : Component.literal(s);
    }

    public static Object[] args(ListTag l) {
        Object[] out = new Object[l.size()];
        for (int i = 0; i < l.size(); i++) out[i] = arg(l.getString(i));
        return out;
    }

    // ------------------------------------------------------------------ receive

    private static void apply(CompoundTag d) {
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
            case "box_ask" -> Minecraft.getInstance().setScreen(new dev.tcgshop.client.screen.BoxGlovesScreen(d)); // 1.1: Box-Opening Gloves
            case "ranking" -> ranking = d; // 1.1: Ranking app
            case "notice" -> {
                PhoneNotices.Kind kind = switch (d.getString("kind")) {
                    case "success" -> PhoneNotices.Kind.SUCCESS;
                    case "error" -> PhoneNotices.Kind.ERROR;
                    default -> PhoneNotices.Kind.INFO;
                };
                Object[] a = args(d.getList("args", Tag.TAG_STRING));
                PhoneNotices.push(kind, Component.translatable(d.getString("title")), Component.translatable(d.getString("body"), a),
                        new ItemStack(TcgItems.TCG_PHONE));
                if (!phoneOpen()) overlay(Component.translatable(d.getString("body"), a).withStyle(kind == PhoneNotices.Kind.ERROR ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
            default -> {}
        }
    }

    private static void onMessages(CompoundTag d) {
        Minecraft mc = Minecraft.getInstance();
        day = d.getLong("day");
        inbox.clear();
        ListTag l = d.getList("list", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            inbox.add(new Msg(c.getLong("id"), c.contains("s") ? c.getString("s") : "tcgshop.msg.sender.system", c.getString("sn"),
                    c.getString("ct"), body(mc, c.getString("b")), c.getBoolean("r"), c.getBoolean("g"), c.getLong("d"),
                    c.contains("k") ? c.getString("k") : "text", c.getCompound("x")));
        }
        if (d.contains("fresh")) {
            Msg m = find(d.getLong("fresh"));
            if (m != null) {
                Component title = Component.translatable("tcgshop.msg.notice.new.title", m.from());
                PhoneNotices.push(PhoneNotices.Kind.INFO, title, m.body, new ItemStack(TcgItems.TCG_PHONE), m.id);
                if (!phoneOpen()) overlay(Component.translatable("tcgshop.msg.notice.new_overlay", m.from()).withStyle(m.gold ? ChatFormatting.GOLD : ChatFormatting.AQUA));
                if (mc.getSoundManager() != null)
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, m.gold ? 1.2f : 1.7f));
            }
        } else if (!joinNoticeShown) { // first sync after joining: remind of unread messages
            joinNoticeShown = true;
            int n = unread();
            if (n > 0) overlay(Component.translatable("tcgshop.msg.notice.unread_join", n).withStyle(ChatFormatting.AQUA));
        }
    }

    private static Component body(Minecraft mc, String json) {
        if (json == null || json.isEmpty()) return Component.empty();
        try {
            MutableComponent t = mc.level != null ? Component.Serializer.fromJson(json, mc.level.registryAccess()) : null;
            if (t != null) return t;
        } catch (Exception ignored) {
            // falls back to the raw text
        }
        return Component.literal(json);
    }

    private static boolean phoneOpen() {
        var s = Minecraft.getInstance().screen;
        return s instanceof dev.tcgshop.client.screen.PhoneScreen || s instanceof dev.tcgshop.client.screen.StoreScreen;
    }

    private static void overlay(Component t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.gui.setOverlayMessage(Component.literal("✉ ").append(t), false);
    }

    // ------------------------------------------------------------------ actions

    private static void act(String a, java.util.function.Consumer<CompoundTag> fill) {
        CompoundTag d = new CompoundTag();
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

    /** 1.1: asks the Wholesaler for the Diamond loan (arrives as an offer message). */
    public static void requestDiamondLoan() { act("loan_diamond", null); }

    public static void declineLoan(long id) { act("loan_decline", d -> d.putLong("id", id)); }

    /** amount <= 0 = repay everything. */
    public static void repay(long amount) { act("loan_repay", d -> d.putLong("amount", amount)); }
}
