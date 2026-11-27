package dev.tcgshop.shop;

import dev.tcgshop.TcgShopConfig;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.WholesalerEntity;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.MarketPayloads;
import dev.tcgshop.pricing.PriceService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Quick card selling: from the phone at 50% of their value (to get rid of them instantly) or to the Mysterious
 * Wholesaler at 40%. The Wholesaler also buys emeralds and almost any vanilla item ({@link VanillaPrices}), plus Cobblemon
 * Poké Balls and Mega Stones ({@link TraderGoods}) for CobbleDollars. Both also buy old shop cores ({@link CoreResale}, 1.1).
 * Plus the starter kit (5 random packs).
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

    public static void handle(ServerPlayer player, MarketPayloads.Sell p) {
        double rate;
        if (p.source() == -1) {
            if (!player.getInventory().contains(st -> st.is(TcgItems.TCG_PHONE))) return;
            if (p.mode() != MarketPayloads.SELL_CARDS) return;
            rate = PHONE_RATE + dev.tcgshop.phone.PhoneUpgrades.sellBonus(player);
        } else if (player.level().getEntity(p.source()) instanceof WholesalerEntity w && w.distanceToSqr(player) < 10 * 10) {
            rate = WHOLESALER_RATE;
        } else return;
        if (!CobbleDollarsBridge.isAvailable()) {
            player.displayClientMessage(Component.translatable("tcgshop.sell.no_bank").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (p.mode() == MarketPayloads.SELL_EMERALDS) {
            sellEmeralds(player);
            return;
        }
        double sum = 0;
        int cards = 0;
        boolean wholesaler = p.source() != -1;
        boolean fromBinder = false, hot = false, lockedCore = false, lockedOther = false;
        int cores = 0;
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
            ItemStack st = player.getInventory().getItem(slot);
            // 1.0 beta 3: a card stolen from a collector who is still hunting for it is not bought
            if (CardInfo.isCard(st) && dev.tcgshop.entity.CollectorTheft.isHot(player.getServer(), st)) { hot = true; continue; }
            if (CoreResale.isCore(st)) { // 1.1: old shop cores, phone and Wholesaler alike, at coreResaleRate of their price
                java.util.UUID keyOf = CoreResale.dormantOwner(player.getServer(), st); // the key of a dormant shop: never sold
                if (keyOf != null) { if (keyOf.equals(player.getUUID())) lockedCore = true; else lockedOther = true; continue; }
                long cp = CoreResale.unitPrice(st);
                if (cp <= 0) continue;
                sum += cp * (double) st.getCount();
                cores += st.getCount();
            } else if (CardInfo.isCard(st)) sum += offer(st, rate) * (double) st.getCount();
            else if (wholesaler && VanillaPrices.sellable(st)) sum += VanillaPrices.price(st) * st.getCount(); // the Wholesaler buys almost everything
            else continue;
            cards += st.getCount();
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        if (fromBinder) dev.tcgshop.collection.BinderSlots.sync(player);
        if (hot) dev.tcgshop.entity.CollectorTheft.sayHot(player);
        if (lockedCore) player.displayClientMessage(Component.translatable("tcgshop.sell.core_linked").withStyle(ChatFormatting.RED), false);
        if (lockedOther) player.displayClientMessage(Component.translatable("tcgshop.sell.core_linked_other").withStyle(ChatFormatting.RED), false);
        if (cards == 0) return;
        long total = (long) Math.floor(sum);
        CobbleDollarsBridge.add(player, total);
        player.displayClientMessage(Component.translatable("tcgshop.sell.done", cards, Component.literal(PriceService.format(total)).withStyle(ChatFormatting.GOLD)), false);
        if (cores > 0) dev.tcgshop.TcgShop.LOGGER.info("[shop] {} sold {} old shop core(s) ({} in the sale)", player.getGameProfile().getName(), cores, PriceService.format(total));
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.3f);
        ShopServer.syncZones(player);
    }

    private static void sellEmeralds(ServerPlayer player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(Items.EMERALD)) { n += st.getCount(); inv.setItem(i, ItemStack.EMPTY); }
            else if (st.is(Items.EMERALD_BLOCK)) { n += st.getCount() * 9; inv.setItem(i, ItemStack.EMPTY); }
        }
        if (n == 0) {
            player.displayClientMessage(Component.translatable("tcgshop.sell.no_emeralds").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long total = (long) Math.floor(n * VanillaPrices.price(new ItemStack(Items.EMERALD)));
        CobbleDollarsBridge.add(player, total);
        player.displayClientMessage(Component.translatable("tcgshop.sell.emeralds", n, Component.literal(PriceService.format(total)).withStyle(ChatFormatting.GOLD)), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    // ------------------------------------------------------------------ starter kit

    /** The first time you join: 5 random sealed packs from any collection. */
    public static void starterKit(ServerPlayer player) {
        if (!TcgShopConfig.INSTANCE.starterPacks || player.getTags().contains(STARTER_TAG)) return;
        List<Item> sealed = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !dev.tcgshop.shop.PackLock.locked(id)) sealed.add(BuiltInRegistries.ITEM.get(id));
        if (sealed.isEmpty()) return;
        player.addTag(STARTER_TAG);
        int n = Math.max(0, TcgShopConfig.INSTANCE.starterPackCount);
        for (int i = 0; i < n; i++) player.getInventory().placeItemBackInInventory(new ItemStack(sealed.get(player.getRandom().nextInt(sealed.size()))));
        player.displayClientMessage(Component.translatable("tcgshop.starter.packs", n).withStyle(ChatFormatting.GOLD), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
    }
}
