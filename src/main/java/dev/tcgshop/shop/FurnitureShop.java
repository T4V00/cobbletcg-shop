package dev.tcgshop.shop;

import dev.tcgshop.compat.CobbleFurniesCompat;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.FurnitureSellerEntity;
import dev.tcgshop.entity.NpcLines;
import dev.tcgshop.entity.TcgNpc;
import dev.tcgshop.entity.WholesalerEntity;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.FurniturePayloads;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Furniture (CobbleFurnies) and plush (Pokéblocks) shop.
 * <ul>
 *   <li>Catalog: everything registered by those mods, with the category inferred from the words in the id
 *       (chair, sofa, stove, lamp, potted...).</li>
 *   <li>Prices in CobbleDollars: base by furniture type (a fridge costs more than a stool, a floor block
 *       very little, a statue a lot), surcharge for Poké Ball themed ones, × global multiplier; plushes use
 *       the economy's plush price (tcgshop-economy.json). A fixed price in itemPrices overrides everything.</li>
 *   <li>Where: Furniture Vendor (somewhat cheaper and better buyback), Wholesaler (Furniture tab) and phone
 *       (shipping surcharge).</li>
 *   <li>Buyback at a reduced % and only for furniture bought in the shop and still unused (they carry the
 *       {@link #STOCK_MARK} mark in CUSTOM_DATA; placing and breaking them loses it). This way you can't craft furniture from
 *       cheap materials in the Furnicrafter and sell it to the shop. Plushes aren't craftable: they are always bought back.</li>
 *   <li>Exclusives (v0.9): the luxury statues from dev.tcgshop.luxury, only at the Furniture Vendor and the phone,
 *       at a fixed price (config/tcgshop-luxury.json) and with no buyback.</li>
 *   <li>Everything is validated on the server (distance to the NPC, phone in the inventory, item
 *       in the catalog, balance and units you actually have).</li>
 *   <li>1.0: the Furniture Vendor (and the Wholesaler) also buy Mega Stones: Trade with item {@link TraderGoods#MEGA_TRADE_ID}
 *       and sell = true sells every one you carry ({@link TraderGoods#sellMegaStones}).</li>
 * </ul>
 */
public final class FurnitureShop {
    /** Categories in UI order. */
    public static final String[] CATEGORIES = {"all", "luxury", "seating", "tables", "kitchen", "storage", "lighting", "textiles",
            "building", "plants", "bathroom", "decor", "plush", "other"};

    public static final int KIND_NONE = -1, KIND_SELLER = 0, KIND_WHOLESALER = 1, KIND_PHONE = 2;

    // Default values; can be overridden from the economy (hook in EconomyConfig.apply)
    /** Global multiplier for furniture prices. */
    public static double PRICE_MULT = 1.0;
    /** Purchase factor by location: Furniture Vendor, Wholesaler, phone (home delivery). */
    public static double SELLER_FACTOR = 0.9, WHOLESALER_FACTOR = 1.0, PHONE_FACTOR = 1.15;
    /** Share of the base price you get back when selling: to the Furniture Vendor / to the Wholesaler or by phone. */
    public static double SELLER_BUYBACK = 0.5, OTHER_BUYBACK = 0.35;
    /** Chance that the Furniture Vendor appears on each attempt (2 per Minecraft day). */
    public static double SELLER_CHANCE = 0.35;
    /** Plushes: factor applied to the economy's plush price. */
    public static double PLUSH_FACTOR = 1.0;

    private static final double MAX_DISTANCE = 10.0;

    /** Mark (boolean in CUSTOM_DATA) on furniture sold by the shop: only those are bought back. */
    public static final String STOCK_MARK = "tcgshop_furniture";

    private FurnitureShop() {}

    // ------------------------------------------------------------------ "bought in the shop" mark (common)

    /** Can it be sold to the shop? Plushes always; furniture only if it carries the purchase mark (never placed). */
    public static boolean isShopStock(ItemStack st) {
        if (st.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(st.getItem());
        if (CobbleFurniesCompat.isPlush(id)) return true;
        NbtComponent data = st.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(STOCK_MARK);
    }

    /** n units exactly as the shop delivers them (furniture with the purchase mark). */
    public static ItemStack stockStack(Item item, int n) {
        ItemStack st = new ItemStack(item, n);
        if (!CobbleFurniesCompat.isPlush(Registries.ITEM.getId(item)))
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, st, nbt -> nbt.putBoolean(STOCK_MARK, true));
        return st;
    }

    // ------------------------------------------------------------------ catalog and prices (common client / server)

    private static Set<String> tokens(Identifier id) {
        return new HashSet<>(Arrays.asList(id.getPath().toLowerCase(Locale.ROOT).split("[_/]")));
    }

    private static boolean any(Set<String> t, String... words) {
        for (String w : words) if (t.contains(w)) return true;
        return false;
    }

    /** Category of a furniture/plush item from the words in its id. */
    public static String category(Identifier id) {
        if (dev.tcgshop.luxury.LuxuryShop.isExclusive(id)) return "luxury"; // luxury statues and the mansion key (Exclusives)
        if (CobbleFurniesCompat.isPlush(id)) return "plush";
        Set<String> t = tokens(id);
        String path = id.getPath();
        if (any(t, "toilet", "bath", "bathtub", "shower", "towel")) return "bathroom";
        if (any(t, "stove", "sink", "fridge", "oven", "microwave", "cabinetry", "kitchen", "toaster", "counter") || path.contains("upper_cabinet"))
            return "kitchen";
        if (any(t, "chair", "stool", "sofa", "armchair", "couch", "bench", "seat")) return "seating";
        if (any(t, "table", "desk", "nightstand")) return "tables";
        if (any(t, "cabinet", "drawer", "shelf", "bookshelf", "wardrobe", "dresser", "locker", "support", "crate")) return "storage";
        if (any(t, "lamp", "lantern", "chandelier", "sconce", "candle")) return "lighting";
        if (any(t, "curtain", "carpet", "rug", "wool", "pillow", "cushion")) return "textiles";
        if (any(t, "potted", "plant", "topiary", "bonsai", "pothos", "fern", "cactus", "flower", "vase")) return "plants";
        if (any(t, "floor", "planks", "mosaic", "porcelain", "ceramic", "tile", "tiles", "brick", "wall")) return "building";
        if (any(t, "tv", "statue", "painting", "clock", "mirror", "poster", "radio", "computer", "pc", "trophy")) return "decor";
        return "other";
    }

    /** Base price (before the where-you-buy factor) in CobbleDollars. */
    public static long basePrice(Identifier id) {
        Long fixed = dev.tcgshop.EconomyConfig.INSTANCE.itemPrices == null ? null : dev.tcgshop.EconomyConfig.INSTANCE.itemPrices.get(id.toString());
        if (fixed != null) return Math.max(1, fixed);
        if (CobbleFurniesCompat.isPlush(id)) {
            Item it = Registries.ITEM.get(id);
            return Math.max(1, Math.round(ShopServer.priceOf(new ItemStack(it), 1.0) * PLUSH_FACTOR));
        }
        Set<String> t = tokens(id);
        String path = id.getPath();
        double p;
        if (any(t, "statue")) p = t.contains("ancient") ? 60_000 : 35_000;
        else if (any(t, "tv", "computer", "pc")) p = 18_000;
        else if (any(t, "fridge")) p = 14_000;
        else if (any(t, "stove", "oven")) p = 10_000;
        else if (any(t, "sink", "toilet", "bathtub", "bath", "shower")) p = 7_000;
        else if (any(t, "sofa", "couch")) p = 7_500;
        else if (any(t, "armchair")) p = 5_500;
        else if (any(t, "desk")) p = 6_000;
        else if (any(t, "furnicrafter")) p = 9_000;
        else if (any(t, "bonsai")) p = 5_000;
        else if (path.contains("upper_cabinet") || any(t, "cabinetry")) p = 3_800;
        else if (any(t, "cabinet", "wardrobe", "dresser")) p = 4_500;
        else if (any(t, "drawer", "bookshelf", "shelf")) p = 4_000;
        else if (any(t, "table")) p = 3_800;
        else if (any(t, "nightstand")) p = 2_800;
        else if (any(t, "chair", "bench")) p = 2_600;
        else if (any(t, "stool", "seat")) p = 1_600;
        else if (any(t, "lamp", "lantern", "chandelier", "sconce")) p = 3_200;
        else if (any(t, "curtain")) p = 1_800;
        else if (any(t, "potted", "plant", "topiary", "pothos", "fern", "cactus", "flower", "vase")) p = 2_200;
        else if (any(t, "support")) p = 900;
        else if (any(t, "carpet", "rug")) p = 650;
        else if (any(t, "wool")) p = 900;
        else if (any(t, "floor", "planks", "mosaic", "porcelain", "ceramic", "tile", "tiles", "brick")) p = 450;
        else p = 3_000;
        // Poké Ball / "dark" themed: somewhat more expensive
        if (path.contains("poke_ball") || path.contains("pokeball")) p *= 1.6;
        else if (t.contains("dark")) p *= 1.1;
        // round nicely to 50
        long v = Math.round(p * PRICE_MULT / 50.0) * 50L;
        return Math.max(50, v);
    }

    private static double buyFactor(int kind) {
        return switch (kind) {
            case KIND_SELLER -> SELLER_FACTOR;
            case KIND_PHONE -> PHONE_FACTOR;
            default -> WHOLESALER_FACTOR;
        };
    }

    /** What you pay for one unit. */
    public static long buyPrice(Identifier id, int kind) {
        return Math.max(1, Math.round(basePrice(id) * buyFactor(kind)));
    }

    /**
     * What you get for one unit. Never more than the cheapest purchase price (no arbitrage with the shop itself;
     * arbitrage with the Furnicrafter is blocked by the purchase mark, see isShopStock).
     */
    public static long sellPrice(Identifier id, int kind) {
        double rate = kind == KIND_SELLER ? SELLER_BUYBACK : OTHER_BUYBACK;
        double minFactor = Math.min(SELLER_FACTOR, Math.min(WHOLESALER_FACTOR, PHONE_FACTOR));
        rate = Math.max(0, Math.min(rate, minFactor * 0.9));
        // plushes are also sold at the Market/Wholesaler at ~80% of their price: reselling them must never be profitable
        if (CobbleFurniesCompat.isPlush(id)) rate = Math.min(rate, 0.7);
        return Math.max(1, Math.round(basePrice(id) * rate));
    }

    // ------------------------------------------------------------------ server

    /** Shop kind for that source, or KIND_NONE if the player can't use it right now. */
    public static int kindOf(ServerPlayerEntity player, int source) {
        if (source == -1)
            // by item type, ignoring components (a phone renamed on an anvil also counts)
            return player.getInventory().contains(st -> st.isOf(TcgItems.TCG_PHONE)) ? KIND_PHONE : KIND_NONE;
        Entity e = player.getWorld().getEntityById(source);
        if (e == null || !e.isAlive() || e.squaredDistanceTo(player) > MAX_DISTANCE * MAX_DISTANCE) return KIND_NONE;
        if (e instanceof FurnitureSellerEntity) return KIND_SELLER;
        if (e instanceof WholesalerEntity) return KIND_WHOLESALER;
        return KIND_NONE;
    }

    /** Reason (translation key) why that source is unavailable. */
    private static String deniedReason(int source) {
        return source == -1 ? "tcgshop.furn.err_phone" : "tcgshop.furn.err_far";
    }

    private static void send(ServerPlayerEntity player, FurniturePayloads.Open p) {
        if (ServerPlayNetworking.canSend(player, FurniturePayloads.Open.ID)) ServerPlayNetworking.send(player, p);
    }

    /** Sends the catalog with that source's prices. openScreen = open the Vendor screen on the client. */
    public static void open(ServerPlayerEntity player, int source, boolean openScreen) {
        int kind = kindOf(player, source);
        // always reply: otherwise the panel stays on "Loading catalog" forever
        if (kind == KIND_NONE) {
            send(player, FurniturePayloads.Open.denied(source, balance(player), deniedReason(source)));
            return;
        }
        // luxury statues (Exclusives): only the Furniture Vendor and the phone, at a fixed price and with no buyback
        List<Identifier> statues = (kind == KIND_SELLER || kind == KIND_PHONE) ? dev.tcgshop.luxury.LuxuryShop.catalog() : List.of();
        if (!CobbleFurniesCompat.available() && statues.isEmpty()) {
            player.sendMessage(Text.translatable("tcgshop.furn.unavailable").formatted(Formatting.GRAY), true);
            send(player, FurniturePayloads.Open.denied(source, balance(player), "tcgshop.furn.unavailable"));
            return;
        }
        List<Identifier> all = CobbleFurniesCompat.available() ? CobbleFurniesCompat.all() : List.of();
        List<String> ids = new ArrayList<>(all.size() + statues.size());
        List<Long> buy = new ArrayList<>(all.size() + statues.size()), sell = new ArrayList<>(all.size() + statues.size());
        for (Identifier id : statues) {
            long pr = dev.tcgshop.luxury.LuxuryShop.priceOf(id);
            if (pr <= 0) continue;
            ids.add(id.toString());
            buy.add(pr);
            sell.add(0L); // not resellable
        }
        for (Identifier id : all) {
            if (ids.size() >= FurniturePayloads.MAX_ENTRIES) break;
            ids.add(id.toString());
            buy.add(buyPrice(id, kind));
            sell.add(sellPrice(id, kind));
        }
        send(player, new FurniturePayloads.Open(source, openScreen, true, balance(player), ids, buy, sell, ""));
    }

    private static long balance(ServerPlayerEntity player) {
        return CobbleDollarsBridge.isAvailable() ? CobbleDollarsBridge.getBalance(player) : -1;
    }

    /** Client -> server: requests the catalog (Wholesaler tab or phone app). */
    public static void handleRequest(ServerPlayerEntity player, FurniturePayloads.Request p) {
        open(player, p.source(), false);
    }

    /** Client -> server: buy or sell. */
    public static void handleTrade(ServerPlayerEntity player, FurniturePayloads.Trade p) {
        int kind = kindOf(player, p.source());
        if (kind == KIND_NONE) {
            // a click must never do nothing silently: explain why (without clearing the catalog already shown)
            player.sendMessage(Text.translatable(deniedReason(p.source())).formatted(Formatting.RED), true);
            return;
        }
        if (TraderGoods.MEGA_TRADE_ID.equals(p.item())) { // 1.0: any trader buys Mega Stones (not the phone: it isn't a trader)
            if (p.sell() && kind != KIND_PHONE && TraderGoods.sellMegaStones(player) > 0
                    && player.getWorld().getEntityById(p.source()) instanceof FurnitureSellerEntity s) {
                NpcLines.say(s, player, "furn_sell");
                s.flashEmote(TcgNpc.EMOTE_DEAL);
            }
            send(player, new FurniturePayloads.Open(p.source(), false, false, balance(player), List.of(), List.of(), List.of(), ""));
            return;
        }
        Identifier id = Identifier.tryParse(p.item());
        boolean statue = dev.tcgshop.luxury.LuxuryShop.isExclusive(id); // statues and the mansion key
        if (id == null || (!statue && !CobbleFurniesCompat.tradeable(id))) return;
        if (!CobbleDollarsBridge.isAvailable()) {
            player.sendMessage(Text.translatable("tcgshop.sell.no_bank").formatted(Formatting.RED), true);
            return;
        }
        if (!p.sell()) {
            // only charge the price the player saw and confirmed: if it changed (/tcgshop reload with the catalog
            // open), reject it and send them the catalog with the new prices
            long unit = statue ? dev.tcgshop.luxury.LuxuryShop.priceOf(id) : buyPrice(id, kind);
            if (p.price() != unit) {
                player.sendMessage(Text.translatable("tcgshop.furn.price_changed").formatted(Formatting.RED), true);
                open(player, p.source(), false);
                return;
            }
        }
        if (statue) {
            // luxury statues: exclusive to the Furniture Vendor and the phone, never resold
            if (p.sell()) dev.tcgshop.luxury.LuxuryShop.refuseSale(player, id);
            else if (kind == KIND_SELLER || kind == KIND_PHONE)
                dev.tcgshop.luxury.LuxuryShop.buy(player, id, p.source() == -1 ? null : player.getWorld().getEntityById(p.source()));
            send(player, new FurniturePayloads.Open(p.source(), false, false, balance(player), List.of(), List.of(), List.of(), ""));
            return;
        }
        Item item = Registries.ITEM.get(id);
        Entity npc = p.source() == -1 ? null : player.getWorld().getEntityById(p.source());
        if (p.sell()) sell(player, kind, id, item, p.qty(), npc);
        else buy(player, kind, id, item, p.qty(), npc);
        // updated balance (without resending the catalog)
        send(player, new FurniturePayloads.Open(p.source(), false, false, balance(player), List.of(), List.of(), List.of(), ""));
    }

    private static void buy(ServerPlayerEntity player, int kind, Identifier id, Item item, int qtyIn, Entity npc) {
        int qty = Math.max(1, Math.min(64, qtyIn));
        long total = buyPrice(id, kind) * qty;
        if (!CobbleDollarsBridge.tryCharge(player, total)) {
            player.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(total)).formatted(Formatting.RED), true);
            if (npc instanceof FurnitureSellerEntity s) NpcLines.say(s, player, "furn_no_money");
            return;
        }
        int max = Math.max(1, new ItemStack(item).getMaxCount());
        int left = qty;
        while (left > 0) {
            int n = Math.min(max, left);
            player.getInventory().offerOrDrop(stockStack(item, n)); // with the purchase mark (can be bought back)
            left -= n;
        }
        ShopZones.get(player.getServerWorld()).addCost(player.getUuid(), total);
        ItemStack shown = new ItemStack(item);
        player.sendMessage(Text.translatable("tcgshop.furn.bought", qty, shown.getName(),
                Text.literal(PriceService.format(total)).formatted(Formatting.GOLD)), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.6f, 1.2f);
        if (npc instanceof FurnitureSellerEntity s) {
            NpcLines.say(s, player, "furn_buy");
            s.flashEmote(TcgNpc.EMOTE_MONEY);
        }
        ShopServer.syncZones(player);
    }

    private static void sell(ServerPlayerEntity player, int kind, Identifier id, Item item, int qtyIn, Entity npc) {
        var inv = player.getInventory();
        int want = Math.max(1, Math.min(64 * 36, qtyIn));
        int removed = 0;
        boolean unmarked = false;
        // main inventory only (0-35): never armor or the offhand
        // and only unused items bought in the shop (crafted or already placed ones lack the mark)
        for (int i = 0; i < 36 && removed < want; i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty() || !st.isOf(item)) continue;
            if (!isShopStock(st)) { unmarked = true; continue; }
            int n = Math.min(st.getCount(), want - removed);
            st.decrement(n);
            if (st.isEmpty()) inv.setStack(i, ItemStack.EMPTY);
            removed += n;
        }
        if (removed == 0) {
            player.sendMessage(Text.translatable(unmarked ? "tcgshop.furn.not_from_shop" : "tcgshop.furn.none_to_sell")
                    .formatted(Formatting.GRAY), true);
            return;
        }
        inv.markDirty();
        long total = sellPrice(id, kind) * removed;
        CobbleDollarsBridge.add(player, total);
        player.sendMessage(Text.translatable("tcgshop.furn.sold", removed, new ItemStack(item).getName(),
                Text.literal(PriceService.format(total)).formatted(Formatting.GOLD)), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.3f);
        if (npc instanceof FurnitureSellerEntity s) {
            NpcLines.say(s, player, "furn_sell");
            s.flashEmote(TcgNpc.EMOTE_DEAL);
        }
        ShopServer.syncZones(player);
    }
}
