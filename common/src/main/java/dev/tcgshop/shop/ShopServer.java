package dev.tcgshop.shop;

import dev.masterball.api.platform.Net;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.ShopActionPayload;
import dev.tcgshop.network.ShopViewPayload;
import dev.tcgshop.network.ShopZonesPayload;
import dev.tcgshop.network.StoreBuyPayload;
import dev.tcgshop.network.StoreOpenPayload;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shop logic: products and prices, direct purchase, employees, register, catalog and sync. */
public final class ShopServer {
    private static final Map<Item, Long> FIXED = new java.util.LinkedHashMap<>();
    // Economy (v3): furniture, employees and phone pay for themselves in a few days of sales; wholesale stock leaves a margin.
    // Default values; the real ones come from config/tcgshop-economy.json (EconomyConfig.apply)
    public static long CASHIER_HIRE = 30_000, CASHIER_WAGE = 4_000, GUARD_HIRE = 45_000, GUARD_WAGE = 6_000;
    public static final long MYSTERY_PRICE = 25_000;
    private static final Map<String, Long> DEFAULT_FIXED = new java.util.LinkedHashMap<>();
    private static long defaultItemPrice = 5_000, pokedollPrice = 40_000, giantPokedollPrice = 150_000;
    private static long minPack = 100, minBox = 1_000, minCard = 10;
    /** Factor over the market price: Wholesaler (boxes much cheaper) and phone Market (convenient, somewhat pricier). */
    public static double WHOLESALE_FACTOR = 0.6, PHONE_FACTOR = 0.8;

    /** Default fixed prices (item id -> price), to fill in the economy file. */
    public static Map<String, Long> defaultFixedPrices() {
        if (DEFAULT_FIXED.isEmpty())
            for (Map.Entry<Item, Long> e : FIXED.entrySet()) DEFAULT_FIXED.put(BuiltInRegistries.ITEM.getKey(e.getKey()).toString(), e.getValue());
        return DEFAULT_FIXED;
    }

    public static void applyEconomy(dev.tcgshop.EconomyConfig c) {
        defaultFixedPrices(); // captures the default values before overwriting
        CASHIER_HIRE = c.cashierHire;
        CASHIER_WAGE = c.cashierWage;
        GUARD_HIRE = c.guardHire;
        GUARD_WAGE = c.guardWage;
        WHOLESALE_FACTOR = c.wholesalerBuyFactor;
        PHONE_FACTOR = c.phoneBuyFactor;
        defaultItemPrice = c.defaultItemPrice;
        pokedollPrice = c.pokedollPrice;
        giantPokedollPrice = c.giganticPokedollPrice;
        minPack = c.minPackPrice;
        minBox = c.minBoxPrice;
        minCard = c.minCardPrice;
        for (Map.Entry<String, Long> e : c.itemPrices.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                dev.tcgshop.TcgShop.LOGGER.warn("tcgshop-economy.json: unknown item {}", e.getKey());
                continue;
            }
            FIXED.put(BuiltInRegistries.ITEM.get(id), e.getValue());
        }
    }

    private ShopServer() {}

    public static void init() {
        FIXED.put(TcgBlocks.CARD_STAND_ITEM, 6_000L);
        FIXED.put(TcgBlocks.DISPLAY_CASE_ITEM, 18_000L);
        FIXED.put(TcgBlocks.PACK_SHELF_ITEM, 16_000L);
        FIXED.put(TcgBlocks.GONDOLA_ITEM, 22_000L);
        FIXED.put(TcgBlocks.BOX_PALLET_ITEM, 9_000L);
        FIXED.put(TcgBlocks.PEDESTAL_ITEM, 28_000L);
        FIXED.put(TcgBlocks.CARD_WALL_ITEM, 20_000L);
        FIXED.put(TcgBlocks.COUNTER_DISPLAY_ITEM, 8_000L);
        FIXED.put(TcgBlocks.SHOWCASE_ITEM, 14_000L);
        FIXED.put(TcgBlocks.CARD_LEDGE_ITEM, 4_000L);
        FIXED.put(TcgBlocks.BOX_SHELF_ITEM, 12_000L);
        FIXED.put(TcgBlocks.CARD_FRAME_ITEM, 2_500L);
        FIXED.put(TcgBlocks.HOME_FRAME_ITEM, 2_000L);
        FIXED.put(TcgBlocks.HOME_LEDGE_ITEM, 3_000L);
        FIXED.put(TcgBlocks.HOME_SHOWCASE_ITEM, 12_000L);
        FIXED.put(TcgBlocks.HOME_CARD_WALL_ITEM, 18_000L);
        FIXED.put(TcgBlocks.HOME_PEDESTAL_ITEM, 24_000L);
        FIXED.put(TcgBlocks.CASH_REGISTER_ITEM, 2_000L); // 1.0 (EconomyConfig.traderPricesVersion migrates old files)
        FIXED.put(TcgBlocks.POSTER_ITEM, 1_500L);
        FIXED.put(TcgBlocks.PLAY_TABLE_ITEM, 9_000L);
        FIXED.put(TcgBlocks.THEFT_DETECTOR_ITEM, 20_000L);
        FIXED.put(TcgBlocks.ROULETTE_ITEM, 150_000L);
        FIXED.put(TcgBlocks.MYSTERY_BOX, MYSTERY_PRICE);
        FIXED.put(TcgItems.MANAGER_GOGGLES, 2_000L); // 1.0 (EconomyConfig.gogglesPriceVersion migrates old files)
        FIXED.put(TcgItems.TCG_PHONE, 5_000L); // 1.0 (PhoneConfig.configVersion 2 migrates old economy files)
        FIXED.put(TcgItems.DEODORANT_SPRAY, 2_500L);
        FIXED.put(TcgItems.MOP, 600L);
        FIXED.put(TcgItems.REPAIR_KIT, 1_500L);
        FIXED.putAll(CleanBlocks.PRICES); // 1.1 cleanliness update: wall freshener, wide door, decor objects
        FIXED.put(TcgItems.NERD_GLOVES, 3_000L);
        FIXED.put(TcgItems.BOX_GLOVES, 750_000L); // 1.1: buy-only Box-Opening Gloves
        FIXED.put(TcgItems.GENERAL_BINDER, 6_000L);
        FIXED.put(TcgItems.PERSONAL_BINDER, 5_000L);
        for (ShopTier t : ShopTier.values()) FIXED.put(TcgBlocks.CORE_ITEMS.get(t), t.price);
        FIXED.putAll(RgbDisplays.PRICES); // 1.0 RGB displays (25,000 - 500,000; itemPrices in tcgshop-economy.json override them)
        FIXED.putAll(dev.tcgshop.casino.CasinoBlocks.PRICES); // 1.0 beta 3 casino machines (60,000 - 500,000) and scratch tickets
    }

    // ------------------------------------------------------------------ products and prices

    public static boolean isPokedoll(ItemStack st) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
        return id.getNamespace().equals("pokeblocks") && id.getPath().contains("pokedoll");
    }

    /** Can it be put up for sale on a display? */
    public static boolean isProduct(ItemStack st) {
        if (BoosterBox.isRemovedBox(st)) return false; // 0.9: TCG Pocket set boxes
        return CardInfo.isCard(st) || CardInfo.isPack(st) || st.is(TcgBlocks.BOOSTER_BOX_ITEM) || st.is(TcgBlocks.MYSTERY_BOX) || isPokedoll(st);
    }

    /** Packs, boxes and mystery boxes are only sold with a hired cashier. */
    public static boolean needsCashier(ItemStack st) {
        return CardInfo.isPack(st) || st.is(TcgBlocks.BOOSTER_BOX_ITEM) || st.is(TcgBlocks.MYSTERY_BOX);
    }

    /**
     * 1.0: price in the store (phone Market / Wholesaler catalog) = priceOf with the store sale event applied
     * ({@link Discounts}). Booster boxes never take a per-item discount (one item covers every set).
     */
    public static long storePrice(ItemStack st, double factor) {
        long base = priceOf(st, factor);
        return st.is(TcgBlocks.BOOSTER_BOX_ITEM) ? base : Discounts.apply(st.getItem(), base);
    }

    /** Price. factor: WHOLESALE_FACTOR, PHONE_FACTOR or 1.0 (market). Furniture and tools: fixed price. */
    public static long priceOf(ItemStack st, double factor) {
        if (st.is(TcgBlocks.BOOSTER_BOX_ITEM)) {
            String set = st.getOrDefault(TcgBlocks.BOX_SET, "mew151");
            return Math.max(minBox, Math.round(PriceService.sealedCd(set, PriceService.Sealed.BOX) * factor));
        }
        if (CardInfo.isPack(st)) return Math.max(minPack, Math.round(PriceService.sealedCd(CardInfo.setId(CardInfo.id(st)), PriceService.Sealed.PACK) * factor));
        if (CardInfo.isCard(st)) return Math.max(minCard, Math.round(PriceService.priceCd(st) * factor));
        if (isPokedoll(st)) {
            boolean giant = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().contains("gigantic");
            return Math.round((giant ? giantPokedollPrice : pokedollPrice) * Math.max(0.8, factor));
        }
        Long fixed = FIXED.get(st.getItem());
        return fixed == null ? defaultItemPrice : fixed;
    }

    /** What the Wholesaler sells (and also the phone, which additionally has single packs and the roulette). */
    public static List<ItemStack> wholesaleGoods(List<String> boxSets, @Nullable net.minecraft.util.RandomSource r) {
        List<ItemStack> g = new ArrayList<>();
        for (String s : boxSets) g.add(BoosterBox.of(s));
        g.add(new ItemStack(TcgBlocks.MYSTERY_BOX));
        for (ShopTier t : ShopTier.values()) g.add(new ItemStack(TcgBlocks.CORE_ITEMS.get(t)));
        for (Item it : TcgBlocks.NEW_SHOP_DISPLAYS) g.add(new ItemStack(it));
        for (Item it : TcgBlocks.HOME_DISPLAYS) g.add(new ItemStack(it));
        for (Item it : RgbDisplays.ITEMS) g.add(new ItemStack(it)); // 1.0 RGB displays
        for (Item it : dev.tcgshop.casino.CasinoBlocks.ITEMS) g.add(new ItemStack(it)); // 1.0 beta 3 casino
        for (Item it : new Item[]{TcgBlocks.CARD_STAND_ITEM, TcgBlocks.DISPLAY_CASE_ITEM, TcgBlocks.PACK_SHELF_ITEM, TcgBlocks.GONDOLA_ITEM,
                TcgBlocks.BOX_PALLET_ITEM, TcgBlocks.PEDESTAL_ITEM, TcgBlocks.CARD_WALL_ITEM, TcgBlocks.CASH_REGISTER_ITEM, TcgBlocks.POSTER_ITEM, TcgBlocks.PLAY_TABLE_ITEM,
                TcgBlocks.THEFT_DETECTOR_ITEM, TcgItems.MANAGER_GOGGLES, TcgItems.TCG_PHONE, TcgItems.DEODORANT_SPRAY, TcgItems.NERD_GLOVES, TcgItems.BOX_GLOVES, TcgItems.GENERAL_BINDER, TcgItems.PERSONAL_BINDER, TcgItems.MOP, TcgItems.REPAIR_KIT,
                CleanBlocks.WALL_FRESHENER_ITEM})
            g.add(new ItemStack(it));
        List<Item> dolls = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (id.getNamespace().equals("pokeblocks") && id.getPath().contains("pokedoll")) dolls.add(BuiltInRegistries.ITEM.get(id));
        if (!dolls.isEmpty()) {
            if (r == null) for (Item d : dolls) g.add(new ItemStack(d));
            else for (int i = 0; i < 6; i++) g.add(new ItemStack(dolls.get(r.nextInt(dolls.size()))));
        }
        return g;
    }

    private static List<ItemStack> phoneCache;

    /** Phone catalog: EVERYTHING from the Wholesaler (all boxes), single packs, plushies and the Poké Roulette. */
    public static List<ItemStack> phoneCatalog() {
        if (phoneCache != null) return phoneCache;
        List<String> sets = new ArrayList<>();
        List<ItemStack> packs = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) {
                sets.add(CardInfo.setId(id));
                packs.add(new ItemStack(BuiltInRegistries.ITEM.get(id)));
            }
        List<ItemStack> out = wholesaleGoods(sets, null);
        out.addAll(packs);
        out.add(new ItemStack(TcgBlocks.ROULETTE_ITEM)); // only sold through the phone
        phoneCache = out;
        return out;
    }

    // ------------------------------------------------------------------ displays

    /** The owner puts what they're holding up for sale in slot `slot` (or in the first free one). */
    /** Can that slot be restocked with what you're carrying? (same product and there's still room). */
    public static boolean canMerge(ItemStack inSlot, ItemStack held) {
        return !inSlot.isEmpty() && !held.isEmpty() && inSlot.getMaxStackSize() > 1 && inSlot.getCount() < inSlot.getMaxStackSize()
                && ItemStack.isSameItemSameComponents(inSlot, held);
    }

    public static void stock(ServerPlayer owner, ShopDisplayBlockEntity be, ItemStack held, int slot) {
        if (slot >= 0 && canMerge(be.getItem(slot), held)) {
            ItemStack cur = be.getItem(slot).copy();
            int add = Math.min(held.getCount(), cur.getMaxStackSize() - cur.getCount());
            cur.grow(add);
            held.shrink(add);
            be.setItem(slot, cur);
            owner.displayClientMessage(Component.translatable("tcgshop.shop.restocked", add, cur.getDisplayName(), cur.getCount()), true);
            owner.level().playSound(null, be.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 1.3f);
            return;
        }
        int target = slot >= 0 && be.getItem(slot).isEmpty() ? slot : -1;
        if (target < 0) for (int i = 0; i < be.getContainerSize(); i++) if (be.getItem(i).isEmpty()) { target = i; break; }
        if (target < 0) {
            owner.displayClientMessage(Component.translatable("tcgshop.shop.full").withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack put = held.split(held.getMaxStackSize() > 1 ? held.getCount() : 1);
        be.setItem(target, put);
        be.setPrice(target, priceOf(put, 1.0));
        owner.displayClientMessage(Component.translatable("tcgshop.shop.stocked", put.getDisplayName(),
                Component.literal(PriceService.format(be.price(target))).withStyle(ChatFormatting.GOLD)), true);
        owner.level().playSound(null, be.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1f, 1.1f);
        if (needsCashier(put)) {
            ShopZones.Zone z = ShopZones.get(owner.serverLevel()).at(be.getBlockPos());
            if (z != null && !z.cashier) owner.displayClientMessage(Component.translatable("tcgshop.shop.needs_cashier_hint").withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /** Direct purchase with right click (the owner too, for testing): the money goes to the shop's register. */
    public static void buyDirect(ServerPlayer player, ShopDisplayBlockEntity be, int slot) {
        ServerLevel world = player.serverLevel();
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone z = zones.at(be.getBlockPos());
        ItemStack st = be.getItem(slot);
        if (z == null || st.isEmpty()) return;
        if (z.closed) { // 1.1: nothing is sold while the shop is closed (the owner still manages the stock)
            player.displayClientMessage(Component.translatable("tcgshop.shop.closed_no_sale", z.ownerName).withStyle(ChatFormatting.RED), true);
            world.playSound(null, be.getBlockPos(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6f, 1f);
            return;
        }
        if (needsCashier(st) && !cashierOnDuty(world, z)) {
            player.displayClientMessage(Component.translatable(z.cashier || z.cashier2 ? "tcgshop.shop.no_cashier_night" : "tcgshop.shop.no_cashier").withStyle(ChatFormatting.RED), true);
            return;
        }
        long price = be.price(slot);
        if (!CobbleDollarsBridge.tryCharge(player, price)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(price)).withStyle(ChatFormatting.RED), true);
            world.playSound(null, be.getBlockPos(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6f, 1f);
            return;
        }
        ItemStack bought = be.removeItem(slot, 1);
        player.getInventory().placeItemBackInInventory(bought);
        boolean own = z.owner.equals(player.getUUID());
        long earned = bought.is(TcgBlocks.MYSTERY_BOX) && !own ? MysteryBoxItem.ownerEarning(price, world.random) : price;
        z.balance += earned;
        if (!own) {
            z.revenue += earned;
            z.sales++;
            CardAdvancements.grant(player, "customer");
            ServerPlayer ownerP = world.getServer().getPlayerList().getPlayer(z.owner);
            if (ownerP != null) {
                ownerP.displayClientMessage(Component.translatable("tcgshop.shop.sale", player.getDisplayName(), bought.getDisplayName(),
                        Component.literal(PriceService.format(earned)).withStyle(ChatFormatting.GOLD)), false);
                saleAdvancements(ownerP, z);
            }
        }
        zones.setDirty();
        player.displayClientMessage(Component.translatable("tcgshop.shop.bought", bought.getDisplayName(),
                Component.literal(PriceService.format(price)).withStyle(ChatFormatting.GOLD)), true);
        ringRegister(world, be.getBlockPos());
        world.playSound(null, be.getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8f, 1.4f);
        syncZones(world.getServer());
    }

    /** Sale to an NPC: removes 1 unit from the display and charges (with the mystery box luck if applicable). */
    public static long sellToNpc(ServerLevel world, ShopZones.Zone z, ShopDisplayBlockEntity be, int slot, long price) {
        if (z.closed) return 0; // 1.1: no sales while closed (the item stays on the display)
        ItemStack bought = be.removeItem(slot, 1);
        if (bought.isEmpty()) return 0;
        return payFromNpc(world, z, bought, price, be.getBlockPos());
    }

    /** An NPC pays for a product it has already picked up (at the cash register closest to `at`). */
    public static long payFromNpc(ServerLevel world, ShopZones.Zone z, ItemStack bought, long price, BlockPos at) {
        long earned = bought.is(TcgBlocks.MYSTERY_BOX) ? MysteryBoxItem.ownerEarning(price, world.random) : price;
        z.balance += earned;
        z.revenue += earned;
        z.sales++;
        z.customers++;
        // 0.9 events: day's revenue (reward caps), event goals, tip and revenueMult (capped per owner)
        long bonus = dev.tcgshop.events.EventManager.onNpcSale(world, z, bought, price, earned);
        ShopZones.get(world).setDirty();
        ringRegister(world, at);
        if (earned >= 10_000) world.playSound(null, at, dev.tcgshop.TcgSounds.SALE_BIG, SoundSource.BLOCKS, 0.9f, 1f); // 1.1: big sale
        ServerPlayer ownerP = world.getServer().getPlayerList().getPlayer(z.owner);
        if (ownerP != null) saleAdvancements(ownerP, z);
        return earned + bonus;
    }

    public static void saleAdvancements(ServerPlayer owner, ShopZones.Zone z) {
        CardAdvancements.grant(owner, "first_sale");
        if (z.sales >= 100) CardAdvancements.grant(owner, "sales_100");
        if (z.sales >= 1000) CardAdvancements.grant(owner, "sales_1000");
        if (z.revenue >= 1_000_000) CardAdvancements.grant(owner, "revenue_1m");
    }

    /** Opens the drawer of the nearest cash register (animation). */
    public static void ringRegister(ServerLevel world, BlockPos near) {
        for (BlockPos p : BlockPos.withinManhattan(near, 8, 3, 8)) {
            if (world.getBlockEntity(p) instanceof DecorBlocks.DecorEntity be && world.getBlockState(p).is(TcgBlocks.CASH_REGISTER)) {
                be.animate(0);
                world.playSound(null, p, dev.tcgshop.TcgSounds.SALE, SoundSource.BLOCKS, 0.8f, 1f); // 1.1: the mod's own sale sound
                return;
            }
        }
    }

    public static void openDisplay(ServerPlayer player, ShopDisplayBlockEntity be) {
        ShopZones.Zone z = ShopZones.get(player.serverLevel()).at(be.getBlockPos());
        boolean owner = z != null && z.owner.equals(player.getUUID());
        List<ItemStack> items = new ArrayList<>();
        List<Long> prices = new ArrayList<>();
        for (int i = 0; i < be.getContainerSize(); i++) {
            items.add(be.getItem(i).copy());
            prices.add(be.price(i));
        }
        send(player, new ShopViewPayload(be.getBlockPos(), items, prices, owner, z == null ? "?" : z.ownerName));
    }

    public static void handleAction(ShopActionPayload p, ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        if (player.distanceToSqr(p.pos().getCenter()) > 64) return;
        if (!(world.getBlockEntity(p.pos()) instanceof ShopDisplayBlockEntity be)) return;
        ShopZones.Zone z = ShopZones.get(world).at(p.pos());
        if (z == null || p.slot() < 0 || p.slot() >= be.getContainerSize()) return;
        boolean owner = z.owner.equals(player.getUUID());
        switch (p.action()) {
            case ShopActionPayload.BUY -> buyDirect(player, be, p.slot());
            case ShopActionPayload.SET_PRICE -> { if (owner) be.setPrice(p.slot(), p.value()); }
            case ShopActionPayload.TAKE -> { if (owner && !be.getItem(p.slot()).isEmpty()) player.getInventory().placeItemBackInInventory(be.removeItemNoUpdate(p.slot())); }
            case ShopActionPayload.MARKUP_ALL -> { if (owner) markupAll(player, be, p.value()); } // 1.1: shelf markup
            case ShopActionPayload.MARKET_ALL -> { if (owner) marketAll(player, be); }
            default -> {}
        }
        openDisplay(player, be);
    }

    /**
     * 1.1: raises (or lowers) the price of everything in a fixture by pct percent (clamped to -90..+500), rounding to whole
     * CobbleDollars and never below 1. Returns how many slots changed.
     */
    public static int markupAll(ServerPlayer player, ShopDisplayBlockEntity be, long pct) {
        long k = Math.max(-90, Math.min(500, pct));
        int n = 0;
        for (int i = 0; i < be.getContainerSize(); i++) {
            if (be.getItem(i).isEmpty()) continue;
            long old = Math.max(1, be.price(i));
            be.setPrice(i, applyMarkup(old, k));
            n++;
        }
        if (n > 0 && player != null) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.markup_done", n, (k >= 0 ? "+" : "") + k + "%").withStyle(ChatFormatting.GREEN), true);
            player.level().playSound(null, be.getBlockPos(), dev.tcgshop.TcgSounds.PRICE_TAG, SoundSource.BLOCKS, 0.7f, k >= 0 ? 1.2f : 0.9f);
        }
        return n;
    }

    /** New price after a markup of pct percent (at least 1 and at least one CobbleDollar of change when pct != 0). */
    public static long applyMarkup(long price, long pct) {
        long v = Math.round(price * (1 + pct / 100.0));
        if (pct > 0 && v <= price) v = price + 1;
        if (pct < 0 && v >= price) v = price - 1;
        return Math.max(1, Math.min(1_000_000_000L, v));
    }

    /** 1.1: sets every price in the fixture to its market price. */
    private static void marketAll(ServerPlayer player, ShopDisplayBlockEntity be) {
        int n = 0;
        for (int i = 0; i < be.getContainerSize(); i++) {
            ItemStack st = be.getItem(i);
            if (st.isEmpty()) continue;
            be.setPrice(i, Math.max(1, priceOf(st.copyWithCount(1), 1.0)));
            n++;
        }
        if (n > 0) player.displayClientMessage(Component.translatable("tcgshop.shop.market_all_done", n).withStyle(ChatFormatting.GREEN), true);
    }

    // ------------------------------------------------------------------ editing a specific slot

    /** Opens the menu for a placed card/product: take it or edit price, size and rotation. */
    public static void openSlot(ServerPlayer player, ShopDisplayBlockEntity be, int slot) {
        ItemStack st = be.getItem(slot);
        if (st.isEmpty()) return;
        net.minecraft.nbt.CompoundTag d = new net.minecraft.nbt.CompoundTag();
        d.putLong("pos", be.getBlockPos().asLong());
        d.putInt("slot", slot);
        d.put("item", st.save(player.registryAccess()));
        d.putLong("price", be.price(slot));
        d.putLong("market", priceOf(st.copyWithCount(1), 1.0));
        d.putFloat("scale", be.lookScale(slot));
        d.putInt("rx", be.lookRot(slot, 0));
        d.putInt("ry", be.lookRot(slot, 1));
        d.putInt("rz", be.lookRot(slot, 2));
        d.putBoolean("home", be.isHome());
        if (Net.INSTANCE.canSend(player, dev.tcgshop.network.UiPayloads.Open.ID))
            Net.INSTANCE.send(player, new dev.tcgshop.network.UiPayloads.Open("slot", d));
    }

    /** Menu response: take (to your inventory), save (price + look) or reset (default look and market price). */
    public static void slotAction(ServerPlayer player, net.minecraft.nbt.CompoundTag d) {
        ServerLevel world = player.serverLevel();
        BlockPos pos = BlockPos.of(d.getLong("pos"));
        if (player.distanceToSqr(pos.getCenter()) > 10 * 10 || !(world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity be)) return;
        int slot = d.getInt("slot");
        if (slot < 0 || slot >= be.getContainerSize() || be.getItem(slot).isEmpty()) return;
        boolean allowed;
        if (be.isHome()) allowed = be.mayEdit(player);
        else {
            ShopZones.Zone z = ShopZones.get(world).at(pos);
            allowed = z != null && z.owner.equals(player.getUUID());
        }
        if (!allowed) return;
        switch (d.getString("action")) {
            case "take" -> {
                player.getInventory().placeItemBackInInventory(be.removeItemNoUpdate(slot));
                world.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f);
            }
            case "save" -> {
                if (!be.isHome() && d.contains("price")) be.setPrice(slot, d.getLong("price"));
                be.setLook(slot, d.getFloat("scale"), d.getInt("rx"), d.getInt("ry"), d.getInt("rz"));
                world.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1f, 1.2f);
                player.displayClientMessage(Component.translatable("tcgshop.slot.saved").withStyle(ChatFormatting.GREEN), true);
            }
            case "reset" -> {
                be.resetLook(slot);
                if (!be.isHome()) be.setPrice(slot, priceOf(be.getItem(slot).copyWithCount(1), 1.0));
                world.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1f, 0.9f);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ employees

    /** Hire / fire from the phone. role: "cashier" or "guard". */
    public static void manageStaff(ServerPlayer player, UUID zoneId, String role, boolean hire) {
        // 1.0 beta 3: guard upgrades from the phone (role "guard_up_<armor|weapon|training|detection>")
        if (role.startsWith("guard_up_")) { GuardUpgrades.buy(player, zoneId, role.substring("guard_up_".length())); return; }
        // 1.1: cashier upgrades from the phone (role "cashier_up_<speed|range|accuracy|haggle>")
        if (role.startsWith("cashier_up_")) { CashierUpgrades.buy(player, zoneId, role.substring("cashier_up_".length())); return; }
        ServerLevel world = player.serverLevel();
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone z = zones.byId(zoneId);
        if (z == null || !z.owner.equals(player.getUUID())) return;
        boolean cashier = role.equals("cashier") || role.equals("cashier2");
        boolean second = role.equals("cashier2");
        if (hire) {
            if (second ? z.cashier2 : cashier && z.cashier) return;
            // 1.1: up to EconomyConfig.maxGuards guards (3 by default); "hire" adds one more
            if (!cashier && z.guardCount() >= maxGuards()) {
                player.displayClientMessage(Component.translatable("tcgshop.staff.guard_max", maxGuards()).withStyle(ChatFormatting.YELLOW), false);
                return;
            }
            // 1.0: no hiring from the phone while a Wholesaler loan is active (firing is still allowed: it costs nothing)
            if (dev.tcgshop.phone.Loans.blocksPurchase(player, "tcgshop.loan.notice.blocked_staff")) return;
            double hf = dev.tcgshop.events.EventManager.hireFactor(player, false); // 0.9 events: "someone is looking for work"
            long cost = Math.round((cashier ? CASHIER_HIRE : GUARD_HIRE) * hf);
            if (!CobbleDollarsBridge.tryCharge(player, cost)) {
                player.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(cost)).withStyle(ChatFormatting.RED), false);
                return;
            }
            if (hf < 1) dev.tcgshop.events.EventManager.hireFactor(player, true);
            zones.addCost(player.getUUID(), cost);
            String name = dev.tcgshop.entity.NpcIdentity.randomName(world.random);
            int guardSlot = 0;
            if (second) { z.cashier2 = true; z.cashier2Name = name; }
            else if (cashier) { z.cashier = true; z.cashierName = name; } else guardSlot = z.addGuard(name);
            if (z.lastWageDay < 0) z.lastWageDay = world.getDayTime() / 24000;
            if (!cashier) dev.tcgshop.entity.EmployeeEntity.spawnFor(world, z, false, guardSlot);
            else if (isDayShift(world)) dev.tcgshop.entity.EmployeeEntity.spawnFor(world, z, true, second ? 1 : 0);
            else player.displayClientMessage(Component.translatable("tcgshop.staff.starts_morning", name).withStyle(ChatFormatting.YELLOW), false);
            player.displayClientMessage(Component.translatable(cashier ? "tcgshop.staff.hired_cashier" : "tcgshop.staff.hired_guard", name).withStyle(ChatFormatting.GREEN), false);
            CardAdvancements.grant(player, cashier ? "hire_cashier" : "hire_guard");
        } else {
            String who;
            if (cashier) {
                who = second ? z.cashier2Name : z.cashierName;
                if (second) z.cashier2 = false; else z.cashier = false;
                dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, true, second ? 1 : 0);
            } else { // 1.1: "fire" lets the last hired guard go
                if (!z.guard) return;
                int last = z.guardCount() - 1;
                who = z.removeGuard(last);
                dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, false, last);
            }
            player.displayClientMessage(Component.translatable("tcgshop.staff.fired", who).withStyle(ChatFormatting.YELLOW), false);
        }
        zones.setDirty();
        syncZones(world.getServer());
    }

    /** 1.1: most guards a shop can hire (EconomyConfig.maxGuards, 1-3). */
    public static int maxGuards() {
        return Math.max(1, Math.min(3, dev.tcgshop.EconomyConfig.INSTANCE.maxGuards));
    }

    /** Cashiers' shift: from 6:00 to 18:30 (at night they go home). */
    public static boolean isDayShift(ServerLevel world) {
        return Math.floorMod(world.getDayTime(), 24000L) < 12500;
    }

    /** Is anyone taking payments? A cashier on shift, or the owner next to one of the shop's cash registers. */
    public static boolean cashierOnDuty(ServerLevel world, ShopZones.Zone z) {
        // 0.9 events: with the register broken (registerDown) the CASHIER can't take payments; the owner next to a register can
        if ((z.cashier || z.cashier2) && isDayShift(world) && !dev.tcgshop.events.EventManager.mods(z).registerDown) return true;
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        if (owner == null || owner.serverLevel() != world || !z.contains(owner.blockPosition())) return false;
        for (BlockPos p : BlockPos.withinManhattan(owner.blockPosition(), 5, 2, 5))
            if (world.getBlockState(p).is(TcgBlocks.CASH_REGISTER)) return true;
        return false;
    }

    /** Every few seconds: cashiers come in at dawn and leave at dusk; the owner is notified. */
    public static void shiftTick(ServerLevel world) {
        boolean day = isDayShift(world);
        ShopZones zones = ShopZones.get(world);
        for (ShopZones.Zone z : zones.all()) {
            // 1.1: a hired guard who is missing (e.g. discarded while the shop was dormant) comes back, so wages are never
            // paid for a guard who isn't there
            if (z.guard && !z.closed) respawnMissingGuard(world, z);
            // 1.1: closed shop: no shifts (its cashiers go home on their own, EmployeeEntity.tick)
            if ((!z.cashier && !z.cashier2) || z.closed) { z.shiftSeen = null; continue; }
            if (day) {
                for (int slot = 0; slot < 2; slot++) {
                    if (slot == 0 ? !z.cashier : !z.cashier2) continue;
                    final int sl = slot;
                    boolean present = !world.getEntitiesOfClass(dev.tcgshop.entity.EmployeeEntity.class, z.box().inflate(16),
                            e -> z.id.equals(e.zoneId()) && !e.isGuard() && e.slot() == sl).isEmpty();
                    if (!present && world.hasChunkAt(z.core)) dev.tcgshop.entity.EmployeeEntity.spawnFor(world, z, true, slot);
                }
            }
            if (z.shiftSeen != null && z.shiftSeen != day) {
                ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
                if (owner != null) owner.displayClientMessage(Component.translatable(day ? "tcgshop.staff.shift_start" : "tcgshop.staff.shift_end").withStyle(day ? ChatFormatting.GREEN : ChatFormatting.GOLD), false);
                syncZones(world.getServer());
            }
            z.shiftSeen = day;
        }
    }

    /**
     * 1.1: spawns the shop's guard if it has one hired but none is around. Only when every chunk the guard could be in
     * has its entities loaded, so a guard that is merely not loaded yet is never doubled.
     */
    public static void respawnMissingGuard(ServerLevel world, ShopZones.Zone z) {
        net.minecraft.world.phys.AABB area = z.box().inflate(16);
        int x0 = net.minecraft.core.SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(area.minX));
        int x1 = net.minecraft.core.SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(area.maxX));
        int z0 = net.minecraft.core.SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(area.minZ));
        int z1 = net.minecraft.core.SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(area.maxZ));
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++)
                if (!world.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(cx, cz))) return;
        dev.tcgshop.entity.EmployeeEntity.spawnMissingGuards(world, z); // 1.1: every hired guard slot
    }

    /** Wages: once per Minecraft day they're paid from the shop's register (if there's no money, the employee leaves). */
    public static void payWages(ServerLevel world) {
        ShopZones zones = ShopZones.get(world);
        long day = world.getDayTime() / 24000;
        boolean changed = false;
        for (ShopZones.Zone z : zones.all()) {
            if ((!z.cashier && !z.cashier2 && !z.guard) || z.lastWageDay < 0 || z.lastWageDay >= day) continue;
            z.lastWageDay = day;
            if (z.closed) { changed = true; continue; } // 1.1: closed days are not paid (and nobody quits over them)
            long wage = dailyWages(z);
            ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
            if (z.balance >= wage) {
                z.balance -= wage;
                z.wagesPaid += wage;
                if (owner != null) owner.displayClientMessage(Component.translatable("tcgshop.staff.wages_paid", PriceService.format(wage)).withStyle(ChatFormatting.GRAY), false);
            } else {
                if (z.cashier2) { z.cashier2 = false; dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, true, 1); }
                else if (z.guard) { int last = z.guardCount() - 1; z.removeGuard(last); dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, false, last); }
                else if (z.cashier) { z.cashier = false; dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, true, 0); }
                if (owner != null) owner.displayClientMessage(Component.translatable("tcgshop.staff.quit_unpaid").withStyle(ChatFormatting.RED), false);
            }
            changed = true;
        }
        if (changed) {
            zones.setDirty();
            syncZones(world.getServer());
        }
    }

    // ------------------------------------------------------------------ zone / cash register

    public static void describe(ServerPlayer player, ShopZones.Zone z) {
        ShopZones zones = ShopZones.get(player.serverLevel());
        player.displayClientMessage(Component.translatable("tcgshop.shop.describe", z.ownerName, Component.translatable("tcgshop.shop.tier." + z.tierInfo().key),
                z.size(), z.size()).withStyle(ChatFormatting.GOLD), false);
        if (z.owner.equals(player.getUUID())) {
            long profit = zones.revenue(player.getUUID()) - zones.costs(player.getUUID());
            player.displayClientMessage(Component.translatable("tcgshop.shop.stats", PriceService.format(z.revenue), z.sales, PriceService.format(z.balance),
                    Component.literal(PriceService.format(profit)).withStyle(profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)).withStyle(ChatFormatting.GRAY), false);
            player.displayClientMessage(Component.translatable("tcgshop.shop.rep_line", z.reputation).withStyle(ChatFormatting.AQUA), false);
        }
    }

    public static void useRegister(ServerPlayer player, ShopZones.Zone z) {
        if (!z.owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.register_of", z.ownerName).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // 1.1: the register window (withdraw by percentage or a custom amount with a confirmation, deposit); clients
        // without it keep the old one-click withdraw
        if (Net.INSTANCE.canSend(player, dev.tcgshop.network.UiPayloads.Open.ID)) {
            syncZones(player);
            CompoundTag d = new CompoundTag();
            d.putUUID("zone", z.id);
            Net.INSTANCE.send(player, new dev.tcgshop.network.UiPayloads.Open("register", d));
            return;
        }
        if (z.balance <= 0) {
            describe(player, z);
            return;
        }
        collect(player, z, 0);
    }

    /**
     * 1.1: withdraws part of the register (register window / phone). pct 1-100 of the CURRENT balance (so a stale window
     * never takes more than is there), or a fixed amount when pct is 0; never more than the balance.
     */
    public static void withdraw(ServerPlayer player, ShopZones.Zone z, int pct, long amount) {
        if (!z.owner.equals(player.getUUID())) return;
        long take = pct > 0 ? (pct >= 100 ? z.balance : Math.round(z.balance * Math.min(100, pct) / 100.0)) : amount;
        take = Math.max(0, Math.min(z.balance, take));
        collect(player, z, z.balance - take);
    }

    /** 1.1: puts money from the owner's CobbleDollars into the register (e.g. to cover wages). Not counted as revenue. */
    public static void deposit(ServerPlayer player, ShopZones.Zone z, long amount) {
        if (!z.owner.equals(player.getUUID())) return;
        if (z.dormant) {
            player.displayClientMessage(Component.translatable("tcgshop.register.dormant_no_deposit").withStyle(ChatFormatting.RED), true);
            return;
        }
        amount = Math.max(0, Math.min(1_000_000_000_000L, amount));
        if (amount <= 0) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.deposit_none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(player, amount)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(amount)).withStyle(ChatFormatting.RED), true);
            return;
        }
        z.balance += amount;
        ShopZones.markDirty(player.getServer(), z);
        player.displayClientMessage(Component.translatable("tcgshop.shop.deposited", Component.literal(PriceService.format(amount)).withStyle(ChatFormatting.GOLD)), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
        syncZones(player.getServer());
    }

    /** One day's wages for the whole staff (for "withdraw leaving wages"). */
    public static long dailyWages(ShopZones.Zone z) {
        return (z.cashier ? CASHIER_WAGE : 0) + (z.cashier2 ? CASHIER_WAGE : 0) + GUARD_WAGE * z.guardCount(); // 1.1: each guard is paid
    }

    /** Withdraws the register's money to your CobbleDollars account; keep = what is left inside (e.g. for wages). */
    public static void collect(ServerPlayer player, ShopZones.Zone z, long keep) {
        if (!z.owner.equals(player.getUUID())) return;
        long amount = z.balance - Math.max(0, keep);
        if (amount <= 0) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.nothing_to_collect").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!CobbleDollarsBridge.add(player, amount)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.no_cobbledollars").withStyle(ChatFormatting.RED), false);
            return;
        }
        z.balance -= amount;
        ShopZones.markDirty(player.getServer(), z); // 1.1: the zone's own dimension (phone: a shop elsewhere, or dormant)
        player.displayClientMessage(Component.translatable("tcgshop.shop.collected", Component.literal(PriceService.format(amount)).withStyle(ChatFormatting.GOLD)), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 1.6f);
        syncZones(player.getServer());
    }

    // ------------------------------------------------------------------ catalog (Wholesaler / phone)

    /**
     * Opens the catalog (source = -1 phone, or the Wholesaler's id). What the player sees and its prices depend on the tier
     * of their phone (dev.tcgshop.phone): before the catalog, the required tier of each product is sent (locks).
     * Opening the phone is silent (0.9: locked sets are no longer announced in chat every time).
     */
    public static void openStore(ServerPlayer player, int source, List<ItemStack> goods, double factor) {
        openStore(player, source, goods, factor, false);
    }

    /** refreshOnly: only refreshes a phone that is already open (after buying an upgrade); doesn't reopen it. */
    public static void openStore(ServerPlayer player, int source, List<ItemStack> goods, double factor, boolean refreshOnly) {
        // the PackLock notice compares against the list ALREADY extended with the Wholesaler's extra sets (otherwise, at high
        // tiers the added sets masked the removed ones and the notice never showed)
        List<ItemStack> extended = dev.tcgshop.phone.PhoneUpgrades.withExtraSets(player, source, goods);
        goods = dev.tcgshop.phone.PhoneUpgrades.applyPackLock(player, extended);
        if (source != -1 && goods.size() < extended.size() && !PackLock.lockedSets().isEmpty() && !PackLock.canBuyLocked(player))
            player.displayClientMessage(Component.translatable("tcgshop.lock.need_tier", Component.translatable("tcgshop.shop.tier." + PackLock.minTier().key)).withStyle(ChatFormatting.GOLD), true);
        double f = dev.tcgshop.phone.PhoneUpgrades.priceFactor(player, source, factor)
                * dev.tcgshop.events.EventManager.purchaseFactor(player, source, false); // 0.9 events: supplier discount / delay
        List<Long> prices = new ArrayList<>();
        for (ItemStack st : goods) prices.add(storePrice(st, f)); // 1.0: with the store sale event (Discounts)
        dev.tcgshop.phone.PhoneUpgrades.sync(player, source, dev.tcgshop.phone.PhoneUpgrades.locks(goods, source), refreshOnly);
        send(player, new StoreOpenPayload(source, goods, prices));
    }

    public static void buy(StoreBuyPayload p, ServerPlayer player, List<ItemStack> goods, double factor) {
        goods = dev.tcgshop.phone.PhoneUpgrades.storeView(player, p.source(), goods);
        if (p.index() < 0 || p.index() >= goods.size()) return;
        int qty = Math.max(1, Math.min(64, p.qty()));
        ItemStack item = goods.get(p.index());
        int need = dev.tcgshop.phone.PhoneUpgrades.requiredTier(item, p.source() == -1);
        if (need > dev.tcgshop.phone.PhoneUpgrades.level(player)) {
            player.displayClientMessage(Component.translatable("tcgshop.phoneup.requires", dev.tcgshop.phone.PhoneUpgrades.tierName(need)).withStyle(ChatFormatting.RED), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
            return;
        }
        factor = dev.tcgshop.phone.PhoneUpgrades.priceFactor(player, p.source(), factor);
        double evf = dev.tcgshop.events.EventManager.purchaseFactor(player, p.source(), false);
        factor *= evf;
        long total = storePrice(item, factor) * qty; // 1.0: with the store sale event (Discounts)
        if (!CobbleDollarsBridge.tryCharge(player, total)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(total)).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (evf < 1) dev.tcgshop.events.EventManager.purchaseFactor(player, p.source(), true); // uses up the event discount
        for (int i = 0; i < qty; i++) player.getInventory().placeItemBackInInventory(item.copyWithCount(1));
        ShopZones.get(player.serverLevel()).addCost(player.getUUID(), total);
        player.displayClientMessage(Component.translatable("tcgshop.store.bought", qty, item.getHoverName(),
                Component.literal(PriceService.format(total)).withStyle(ChatFormatting.GOLD)), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.6f, 1.2f);
        if (item.is(TcgBlocks.BOOSTER_BOX_ITEM)) CardAdvancements.grant(player, "buy_box");
        syncZones(player.getServer());
    }

    // ------------------------------------------------------------------ zone sync

    public static void syncZones(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncZones(p);
    }

    public static void syncZones(ServerPlayer player) {
        ShopZones zones = ShopZones.get(player.serverLevel());
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (ShopZones.Zone z : zones.all()) list.add(zoneSync(player, z));
        // 1.1: the owner's dormant shops (core removed), from every dimension, for the phone (till, abandon)
        for (ServerLevel w : player.getServer().getAllLevels())
            for (ShopZones.Zone z : ShopZones.get(w).dormant()) if (z.owner.equals(player.getUUID())) list.add(zoneSync(player, z));
        root.put("zones", list);
        root.putLong("myCosts", zones.costs(player.getUUID()));
        root.putLong("myRevenue", zones.revenue(player.getUUID()));
        send(player, new ShopZonesPayload(root));
    }

    /** One zone of the ShopZonesPayload (details only for its owner). */
    private static CompoundTag zoneSync(ServerPlayer player, ShopZones.Zone z) {
        CompoundTag c = new CompoundTag();
        c.putUUID("id", z.id);
        c.putUUID("owner", z.owner);
        c.putString("name", z.ownerName);
        c.putLong("core", z.core.asLong());
        c.putInt("tier", z.tier);
        c.putInt("size", z.size()); // 1.0: real zone side (pre-1.0 shops keep their old size)
        c.putLong("revenue", z.revenue);
        c.putLong("balance", z.balance);
        c.putInt("sales", z.sales);
        // 1.1: open / closed is public (visitors' goggles show it); dormant only reaches the owner (see syncZones)
        if (z.closed) c.putBoolean("closed", true);
        if (z.dormant) c.putBoolean("dormant", true);
        if (z.owner.equals(player.getUUID())) { // details only for the owner
            c.putBoolean("cashier", z.cashier);
            c.putBoolean("guard", z.guard);
            c.putString("cashier_name", z.cashierName);
            c.putString("guard_name", z.guardName);
            CompoundTag ex = new CompoundTag();
            ex.putBoolean("cashier2", z.cashier2);
            ex.putString("cashier2_name", z.cashier2Name);
            ex.putBoolean("day", isDayShift(player.serverLevel()));
            // 1.1 staff update: guards (count, names), upgrade levels, theft risk and the goggles' bars
            ex.putInt("guards", z.guardCount());
            ex.putInt("guards_max", maxGuards());
            for (int i = 0; i < 3; i++) ex.putString("guard_name_" + i, z.guardNameOf(i));
            ex.putIntArray("gup", GuardUpgrades.levels(player.getServer(), z.id).clone());
            ex.putIntArray("cup", CashierUpgrades.levels(player.getServer(), z.id).clone());
            ex.putInt("theft", (int) Math.round(ShopLife.theftChance(player.getServer(), z) * 1000));
            ex.putInt("decor_f", z.factors.getOrDefault("decor", 0));
            ex.putInt("decor_pct", DecorScore.zonePct(z)); // 1.1 customers update: decoration liking (DecorScore)
            ex.putInt("clean", Cleanliness.score(z)); // 1.1 cleanliness update: smell + grime + droppings
            ex.putInt("grime", z.grime);
            ex.putInt("poops", z.poops.size());
            // the server's curve and copies per decor id, so the decor tooltips estimate what the recompute will give
            ex.putDouble("decor_k", DecorScore.k(z.size()));
            CompoundTag dc = new CompoundTag();
            z.decorCopies.forEach((id, n) -> dc.putInt(id.toString(), n));
            ex.put("decor_copies", dc);
            dev.tcgshop.shop.ShopMissions.writeSync(z, ex);
            ex.put("ev", dev.tcgshop.events.EventManager.writeSync(player.serverLevel(), z)); // 0.9: Events tab
            c.put("ext", ex);
            c.putLong("wages", z.wagesPaid);
            c.putIntArray("metrics", new int[]{z.stolen, z.detained, z.recovered, z.customers, z.complaints, z.dealsAccepted, z.whales,
                    z.reputation, z.decor, z.detectors, z.displays, z.buzz, z.smell});
            CompoundTag f = new CompoundTag();
            z.factors.forEach(f::putInt);
            c.put("factors", f);
            net.minecraft.nbt.ListTag rl = new net.minecraft.nbt.ListTag();
            for (ShopZones.Review r : z.reviews) rl.add(r.toNbt());
            c.put("reviews", rl);
            c.putInt("regulars", z.regulars.size());
        } else c.putIntArray("metrics", new int[]{0, 0, 0, 0, 0, 0, 0, z.reputation});
        return c;
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (Net.INSTANCE.canSend(player, payload.type())) Net.INSTANCE.send(player, payload);
    }
}
