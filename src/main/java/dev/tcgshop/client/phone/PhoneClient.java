package dev.tcgshop.client.phone;

import dev.tcgshop.phone.PhonePayloads;
import dev.tcgshop.phone.PhoneTier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/** Estado de las mejoras del teléfono en el cliente (lo manda el servidor con PhonePayloads.Sync). */
public final class PhoneClient {
    private PhoneClient() {}

    public static boolean enabled = true, legacy, calls = true, callUsed;
    public static int tier;
    public static long[] costs = PhoneTier.DEFAULT_COSTS.clone();
    /** Candados del último catálogo abierto: source y nivel necesario de cada producto. */
    private static int locksSource = Integer.MIN_VALUE;
    private static int[] locks = new int[0];

    /** Llamar UNA vez desde TcgShopClient.onInitializeClient. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PhonePayloads.Sync.ID, (p, ctx) -> ctx.client().execute(() -> apply(p.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PhoneClient::reset));
    }

    private static void reset() {
        enabled = true;
        legacy = false;
        calls = true;
        callUsed = false;
        tier = 0;
        costs = PhoneTier.DEFAULT_COSTS.clone();
        locksSource = Integer.MIN_VALUE;
        locks = new int[0];
    }

    private static void apply(NbtCompound d) {
        enabled = !d.contains("enabled") || d.getBoolean("enabled");
        tier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, d.getInt("tier")));
        legacy = d.getBoolean("legacy");
        long[] c = d.getLongArray("costs");
        if (c.length == PhoneTier.values().length) costs = c;
        calls = !d.contains("calls") || d.getBoolean("calls");
        callUsed = d.getBoolean("callUsed");
        if (d.contains("src")) {
            locksSource = d.getInt("src");
            locks = d.getIntArray("locks");
        }
    }

    /** Nivel necesario de cada producto de un catálogo (todo 0 si no llegaron candados para ese catálogo). */
    public static int[] locksFor(int source, int size) {
        if (source == locksSource && locks.length == size) return locks.clone();
        return new int[size];
    }

    public static boolean appUnlocked(String app) {
        return !enabled || legacy || tier >= PhoneTier.appTier(app);
    }

    public static double sellBonus() {
        return PhoneTier.SELL_BONUS[tier];
    }

    public static boolean canCall() {
        return enabled && calls && tier >= PhoneTier.CALL_TIER && !callUsed;
    }

    public static long cost(int level) {
        return level <= 0 || level >= costs.length ? 0 : costs[level];
    }

    public static Text tierName(int level) {
        return Text.translatable("tcgshop.phoneup.tier." + PhoneTier.of(level).key);
    }

    public static void send(NbtCompound d) {
        if (ClientPlayNetworking.canSend(PhonePayloads.Act.ID)) ClientPlayNetworking.send(new PhonePayloads.Act(d));
    }

    public static void buy(int level) {
        NbtCompound d = new NbtCompound();
        d.putString("a", "buy");
        d.putInt("tier", level);
        send(d);
    }

    public static void call(String kind) {
        NbtCompound d = new NbtCompound();
        d.putString("a", "call");
        d.putString("kind", kind);
        send(d);
    }
}
