package dev.tcgshop.client.phone;

import dev.tcgshop.phone.PhonePayloads;
import dev.tcgshop.phone.PhoneTier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/** Client-side state of the phone upgrades (sent by the server via PhonePayloads.Sync). */
public final class PhoneClient {
    private PhoneClient() {}

    public static boolean enabled = true, legacy, calls = true, callUsed;
    public static int tier;
    public static long[] costs = PhoneTier.DEFAULT_COSTS.clone();
    /** Locks of the last opened catalog: source and required level of each product. */
    private static int locksSource = Integer.MIN_VALUE;
    private static int[] locks = new int[0];

    /** Call ONCE from TcgShopClient.onInitializeClient. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PhonePayloads.Sync.ID, (p, ctx) -> ctx.client().execute(() -> apply(p.data())));
        ClientPlayNetworking.registerGlobalReceiver(PhonePayloads.Result.ID, (p, ctx) -> ctx.client().execute(() -> onResult(p.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PhoneClient::reset));
        PhoneAppsClient.register(); // 1.0: Messages, Loans and Stats apps
        PhoneWallpaper.load(); // 1.0: wallpaper (config/tcgshop-phone-client.json)
    }

    /** 1.0: purchase result (phone Market, Wholesaler, Upgrades) -> notice inside the open phone / stall. */
    private static void onResult(NbtCompound d) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.item.ItemStack icon = net.minecraft.item.ItemStack.EMPTY;
        if (d.contains("item") && mc.world != null)
            icon = net.minecraft.item.ItemStack.fromNbt(mc.world.getRegistryManager(), d.get("item")).orElse(net.minecraft.item.ItemStack.EMPTY);
        int qty = Math.max(1, d.getInt("qty")), lv = d.getInt("tier");
        long amount = d.getLong("amount"), bal = d.getLong("bal");
        Text name = icon.isEmpty() ? Text.translatable("tcgshop.phone.notice.item") : icon.getName();
        String fa = dev.tcgshop.pricing.PriceService.format(amount), fb = dev.tcgshop.pricing.PriceService.format(bal);
        switch (d.getString("k")) {
            case "bought" -> PhoneNotices.push(PhoneNotices.Kind.SUCCESS, Text.translatable("tcgshop.phone.notice.bought.title"),
                    Text.translatable("tcgshop.phone.notice.bought", qty, name, fa, fb), icon);
            case "no_money" -> {
                String missing = dev.tcgshop.pricing.PriceService.format(Math.max(0, amount - bal));
                Text body = icon.isEmpty() && lv > 0
                        ? Text.translatable("tcgshop.phone.notice.no_money_upgrade", tierName(lv), fa, fb, missing)
                        : Text.translatable("tcgshop.phone.notice.no_money", qty, name, fa, fb, missing);
                PhoneNotices.push(PhoneNotices.Kind.ERROR, Text.translatable("tcgshop.phone.notice.no_money.title"), body, icon);
            }
            case "locked" -> PhoneNotices.push(PhoneNotices.Kind.ERROR, Text.translatable("tcgshop.phone.notice.locked.title"),
                    Text.translatable("tcgshop.phone.notice.locked", name, tierName(lv)), icon);
            case "no_bank" -> PhoneNotices.push(PhoneNotices.Kind.ERROR, Text.translatable("tcgshop.phone.notice.no_bank.title"),
                    Text.translatable("tcgshop.phone.notice.no_bank"), icon);
            case "upgraded" -> PhoneNotices.push(PhoneNotices.Kind.SUCCESS, Text.translatable("tcgshop.phone.notice.upgraded.title"),
                    Text.translatable("tcgshop.phone.notice.upgraded", tierName(lv), fa), new net.minecraft.item.ItemStack(dev.tcgshop.item.TcgItems.TCG_PHONE));
            default -> {}
        }
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
        PhoneNotices.clear();
        PhoneAppsClient.reset();
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

    /** Required level of each product in a catalog (all 0 if no locks arrived for that catalog). */
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
