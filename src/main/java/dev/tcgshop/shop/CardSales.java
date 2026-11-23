package dev.tcgshop.shop;

import dev.tcgshop.TcgShopConfig;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.WholesalerEntity;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.MarketPayloads;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Quick card selling: from the phone at 50% of their value (to get rid of them instantly) or to the Mysterious
 * Wholesaler at 40%. The Wholesaler also buys emeralds and almost any vanilla item ({@link VanillaPrices}), plus Cobblemon
 * Poké Balls and Mega Stones ({@link TraderGoods}) for CobbleDollars. Plus the starter kit (5 random packs).
 */
public final class CardSales {
    public static double PHONE_RATE = 0.5, WHOLESALER_RATE = 0.4; // EconomyConfig
    /** Starter kit tag (reused by Onboarding: anyone who already got the old kit receives no more packs). */
    public static final String STARTER_TAG = "tcgshop_starter";

    private CardSales() {}

    /** Market value of one unit. Items the Wholesaler buys by table (vanilla, Poké Balls, Mega Stones): what he pays. */
    public static long unitValue(ItemStack st) {
        if (!st.isEmpty() && !CardInfo.isCard(st) && !ShopServer.isProduct(st)) {
            double v = VanillaPrices.price(st);
            if (v > 0) return Math.max(1, (long) Math.floor(v));
        }
        return ShopServer.priceOf(st.copyWithCount(1), 1.0);
    }

    /** What you get paid for one unit. */
    public static long offer(ItemStack st, double rate) {
        return Math.max(1, Math.round(unitValue(st) * rate));
    }

    /** Emerald price: the one from the vanilla table (config/tcgshop-vanilla-prices.json). */
    public static long emeraldPrice() {
        return Math.max(1, Math.round(VanillaPrices.price(new ItemStack(Items.EMERALD))));
    }

    public static void handle(ServerPlayerEntity player, MarketPayloads.Sell p) {
        double rate;
        if (p.source() == -1) {
            if (!player.getInventory().contains(st -> st.isOf(TcgItems.TCG_PHONE))) return;
            if (p.mode() != MarketPayloads.SELL_CARDS) return;
            rate = PHONE_RATE + dev.tcgshop.phone.PhoneUpgrades.sellBonus(player);
        } else if (player.getWorld().getEntityById(p.source()) instanceof WholesalerEntity w && w.squaredDistanceTo(player) < 10 * 10) {
            rate = WHOLESALER_RATE;
        } else return;
        if (!CobbleDollarsBridge.isAvailable()) {
            player.sendMessage(Text.translatable("tcgshop.sell.no_bank").formatted(Formatting.RED), true);
            return;
        }
        if (p.mode() == MarketPayloads.SELL_EMERALDS) {
            sellEmeralds(player);
            return;
        }
        double sum = 0;
        int cards = 0;
        boolean wholesaler = p.source() != -1;
        boolean fromBinder = false;
        // from highest to lowest slot: removing a card from the Binder doesn't change the index of the ones still to process
        java.util.List<Integer> order = new java.util.ArrayList<>(new LinkedHashSet<>(p.slots()));
        order.sort((x, y) -> Integer.compare(y, x));
        for (int slot : order) {
            if (dev.tcgshop.collection.BinderSlots.isBinder(slot)) { // Binder cards (other items are not emptied)
                ItemStack view = dev.tcgshop.collection.BinderSlots.peek(player, slot);
                if (!CardInfo.isCard(view)) continue;
                ItemStack got = dev.tcgshop.collection.BinderSlots.take(player, slot, view.getCount());
                sum += offer(got, rate) * (double) got.getCount();
                cards += got.getCount();
                fromBinder = true;
                continue;
            }
            if (slot < 0 || slot >= 36) continue;
            ItemStack st = player.getInventory().getStack(slot);
            if (CardInfo.isCard(st)) sum += offer(st, rate) * (double) st.getCount();
            else if (wholesaler && VanillaPrices.sellable(st)) sum += VanillaPrices.price(st) * st.getCount(); // the Wholesaler buys almost everything
            else continue;
            cards += st.getCount();
            player.getInventory().setStack(slot, ItemStack.EMPTY);
        }
        if (fromBinder) dev.tcgshop.collection.BinderSlots.sync(player);
        if (cards == 0) return;
        long total = (long) Math.floor(sum);
        CobbleDollarsBridge.add(player, total);
        player.sendMessage(Text.translatable("tcgshop.sell.done", cards, Text.literal(PriceService.format(total)).formatted(Formatting.GOLD)), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.3f);
        ShopServer.syncZones(player);
    }

    private static void sellEmeralds(ServerPlayerEntity player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(Items.EMERALD)) { n += st.getCount(); inv.setStack(i, ItemStack.EMPTY); }
            else if (st.isOf(Items.EMERALD_BLOCK)) { n += st.getCount() * 9; inv.setStack(i, ItemStack.EMPTY); }
        }
        if (n == 0) {
            player.sendMessage(Text.translatable("tcgshop.sell.no_emeralds").formatted(Formatting.GRAY), true);
            return;
        }
        long total = (long) Math.floor(n * VanillaPrices.price(new ItemStack(Items.EMERALD)));
        CobbleDollarsBridge.add(player, total);
        player.sendMessage(Text.translatable("tcgshop.sell.emeralds", n, Text.literal(PriceService.format(total)).formatted(Formatting.GOLD)), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.8f, 1.1f);
    }

    // ------------------------------------------------------------------ starter kit

    /** The first time you join: 5 random sealed packs from any collection. */
    public static void starterKit(ServerPlayerEntity player) {
        if (!TcgShopConfig.INSTANCE.starterPacks || player.getCommandTags().contains(STARTER_TAG)) return;
        List<Item> sealed = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !dev.tcgshop.shop.PackLock.locked(id)) sealed.add(Registries.ITEM.get(id));
        if (sealed.isEmpty()) return;
        player.addCommandTag(STARTER_TAG);
        int n = Math.max(0, TcgShopConfig.INSTANCE.starterPackCount);
        for (int i = 0; i < n; i++) player.getInventory().offerOrDrop(new ItemStack(sealed.get(player.getRandom().nextInt(sealed.size()))));
        player.sendMessage(Text.translatable("tcgshop.starter.packs", n).formatted(Formatting.GOLD), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }
}
