package dev.tcgshop.client.phone;

import dev.masterball.api.platform.Platform;
import dev.masterball.api.platform.ClientPlatform;
import dev.tcgshop.phone.PhonePayloads;
import dev.tcgshop.phone.PhoneTier;
import dev.masterball.api.platform.event.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Client-side state of the phone upgrades (sent by the server via PhonePayloads.Sync). */
public final class PhoneClient {
    private PhoneClient() {}

    public static boolean enabled = true, legacy, calls = true, callUsed;
    public static int tier;
    /** 1.1: daily Furniture Seller call: minimum tier (server config) and whether it was used today. */
    public static int furnTier = PhoneTier.FURN_CALL_TIER;
    public static boolean furnUsed;
    /** 1.1: a Furniture Seller call was sent and the server has not answered yet (its sync shows the result). */
    private static boolean furnCallPending;
    public static long[] costs = PhoneTier.DEFAULT_COSTS.clone();
    /** Locks of the last opened catalog: source and required level of each product. */
    private static int locksSource = Integer.MIN_VALUE;
    private static int[] locks = new int[0];

    /** Call ONCE from TcgShopClient.onInitializeClient. */
    public static void register() {
        ClientPlatform.INSTANCE.receive(PhonePayloads.Sync.ID, p -> Minecraft.getInstance().execute(() -> apply(p.data())));
        ClientPlatform.INSTANCE.receive(PhonePayloads.Result.ID, p -> Minecraft.getInstance().execute(() -> onResult(p.data())));
        ClientEvents.DISCONNECT.register(client -> client.execute(PhoneClient::reset));
        PhoneAppsClient.register(); // 1.0: Messages, Loans and Stats apps
        PhoneWallpaper.load(); // 1.0: wallpaper (config/tcgshop-phone-client.json)
    }

    /** 1.0: purchase result (phone Market, Wholesaler, Upgrades) -> notice inside the open phone / stall. */
    private static void onResult(CompoundTag d) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.item.ItemStack icon = net.minecraft.world.item.ItemStack.EMPTY;
        if (d.contains("item") && mc.level != null)
            icon = net.minecraft.world.item.ItemStack.parse(mc.level.registryAccess(), d.get("item")).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        int qty = Math.max(1, d.getInt("qty")), lv = d.getInt("tier");
        long amount = d.getLong("amount"), bal = d.getLong("bal");
        Component name = icon.isEmpty() ? Component.translatable("tcgshop.phone.notice.item") : icon.getHoverName();
        String fa = dev.tcgshop.pricing.PriceService.format(amount), fb = dev.tcgshop.pricing.PriceService.format(bal);
        switch (d.getString("k")) {
            case "bought" -> PhoneNotices.push(PhoneNotices.Kind.SUCCESS, Component.translatable("tcgshop.phone.notice.bought.title"),
                    Component.translatable("tcgshop.phone.notice.bought", qty, name, fa, fb), icon);
            case "no_money" -> {
                String missing = dev.tcgshop.pricing.PriceService.format(Math.max(0, amount - bal));
                Component body = icon.isEmpty() && lv > 0
                        ? Component.translatable("tcgshop.phone.notice.no_money_upgrade", tierName(lv), fa, fb, missing)
                        : Component.translatable("tcgshop.phone.notice.no_money", qty, name, fa, fb, missing);
                PhoneNotices.push(PhoneNotices.Kind.ERROR, Component.translatable("tcgshop.phone.notice.no_money.title"), body, icon);
            }
            case "locked" -> PhoneNotices.push(PhoneNotices.Kind.ERROR, Component.translatable("tcgshop.phone.notice.locked.title"),
                    Component.translatable("tcgshop.phone.notice.locked", name, tierName(lv)), icon);
            case "no_bank" -> PhoneNotices.push(PhoneNotices.Kind.ERROR, Component.translatable("tcgshop.phone.notice.no_bank.title"),
                    Component.translatable("tcgshop.phone.notice.no_bank"), icon);
            case "upgraded" -> PhoneNotices.push(PhoneNotices.Kind.SUCCESS, Component.translatable("tcgshop.phone.notice.upgraded.title"),
                    Component.translatable("tcgshop.phone.notice.upgraded", tierName(lv), fa), new net.minecraft.world.item.ItemStack(dev.tcgshop.item.TcgItems.TCG_PHONE));
            default -> {}
        }
    }

    private static void reset() {
        enabled = true;
        legacy = false;
        calls = true;
        callUsed = false;
        furnTier = PhoneTier.FURN_CALL_TIER;
        furnUsed = false;
        furnCallPending = false;
        tier = 0;
        costs = PhoneTier.DEFAULT_COSTS.clone();
        locksSource = Integer.MIN_VALUE;
        locks = new int[0];
        PhoneNotices.clear();
        PhoneAppsClient.reset();
    }

    private static void apply(CompoundTag d) {
        enabled = !d.contains("enabled") || d.getBoolean("enabled");
        tier = Math.max(0, Math.min(PhoneTier.MAX_LEVEL, d.getInt("tier")));
        legacy = d.getBoolean("legacy");
        long[] c = d.getLongArray("costs");
        if (c.length == PhoneTier.values().length) costs = c;
        calls = !d.contains("calls") || d.getBoolean("calls");
        callUsed = d.getBoolean("callUsed");
        furnTier = d.contains("furnTier") ? d.getInt("furnTier") : PhoneTier.FURN_CALL_TIER;
        boolean wasUsed = furnUsed;
        furnUsed = d.getBoolean("furnUsed");
        if (furnCallPending) { // the server answered the call: only a real success shows the green notice (refusals come as action-bar text)
            furnCallPending = false;
            if (furnUsed && !wasUsed)
                PhoneNotices.push(PhoneNotices.Kind.SUCCESS, Component.translatable("tcgshop.phone.furncall.title"),
                        Component.translatable("tcgshop.phone.furncall.calling"), new net.minecraft.world.item.ItemStack(dev.tcgshop.shop.CleanBlocks.POTTED_PALM));
        }
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

    /** Tier an app needs on this server (the Furniture Seller call follows PhoneConfig.furnitureCallMinTier). */
    public static int appTier(String app) {
        if (app.equals("furncall")) return Math.max(0, Math.min(PhoneTier.MAX_LEVEL, furnTier));
        return PhoneTier.appTier(app);
    }

    /** 1.1: the Furniture Seller call is turned off on this server (min tier above the last one): its app is hidden. */
    public static boolean furnCallDisabled() {
        return furnTier > PhoneTier.MAX_LEVEL;
    }

    public static boolean appUnlocked(String app) {
        if (app.equals("furncall")) return furnTier <= PhoneTier.MAX_LEVEL && (!enabled || tier >= furnTier); // 1.1: server-side tier
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

    public static Component tierName(int level) {
        return Component.translatable("tcgshop.phoneup.tier." + PhoneTier.of(level).key);
    }

    public static void send(CompoundTag d) {
        if (ClientPlatform.INSTANCE.canSend(PhonePayloads.Act.ID)) ClientPlatform.INSTANCE.send(new PhonePayloads.Act(d));
    }

    public static void buy(int level) {
        CompoundTag d = new CompoundTag();
        d.putString("a", "buy");
        d.putInt("tier", level);
        send(d);
    }

    /** 1.1: asks the server to send the Furniture Seller (once per Minecraft day). */
    public static void callFurniture() {
        CompoundTag d = new CompoundTag();
        d.putString("a", "call_furniture");
        furnCallPending = true;
        send(d);
    }

    public static void call(String kind) {
        CompoundTag d = new CompoundTag();
        d.putString("a", "call");
        d.putString("kind", kind);
        send(d);
    }
}
