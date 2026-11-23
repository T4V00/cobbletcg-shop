package dev.tcgshop.phone;

import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.StoreBuyPayload;
import dev.tcgshop.shop.ShopServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * 1.0: purchases from the phone Market and the Wholesaler with a result for the client. The purchase itself is still
 * ShopServer.buy (validation, charge, delivery, chat message); this wrapper works out what happened and sends a
 * {@link PhonePayloads.Result} so the open phone / stall can show a readable notice (bought, not enough money, locked).
 */
public final class PhoneStore {
    private PhoneStore() {}

    public static final String BOUGHT = "bought", NO_MONEY = "no_money", LOCKED = "locked", NO_BANK = "no_bank", UPGRADED = "upgraded";

    /** Same arguments as ShopServer.buy (goods = the catalog BEFORE storeView, factor = the base factor). */
    public static void buy(StoreBuyPayload p, ServerPlayerEntity player, List<ItemStack> goods, double factor) {
        if (p.source() == -1 && Loans.blocksPhonePurchase(player)) return; // 1.0: no phone purchases while a loan is active
        List<ItemStack> view = PhoneUpgrades.storeView(player, p.source(), goods);
        if (p.index() < 0 || p.index() >= view.size()) {
            ShopServer.buy(p, player, goods, factor); // ignores it too (stale catalog)
            return;
        }
        ItemStack item = view.get(p.index()).copy();
        int qty = Math.max(1, Math.min(64, p.qty()));
        int need = PhoneUpgrades.requiredTier(item, p.source() == -1);
        boolean locked = need > PhoneUpgrades.level(player);
        // expected total, computed like ShopServer.buy does (without consuming the event discount)
        double f = PhoneUpgrades.priceFactor(player, p.source(), factor)
                * dev.tcgshop.events.EventManager.purchaseFactor(player, p.source(), false);
        long expected = ShopServer.storePrice(item, f) * qty; // 1.0: with the store sale event, like ShopServer.buy
        boolean bank = CobbleDollarsBridge.isAvailable();
        long before = CobbleDollarsBridge.getBalance(player);
        ShopServer.buy(p, player, goods, factor);
        long after = CobbleDollarsBridge.getBalance(player);
        if (locked) sendResult(player, LOCKED, p.source(), item, qty, expected, need, after);
        else if (after < before || expected <= 0) sendResult(player, BOUGHT, p.source(), item, qty, Math.max(0, before - after), 0, after);
        else if (!bank) sendResult(player, NO_BANK, p.source(), item, qty, expected, 0, after);
        else if (before < expected) sendResult(player, NO_MONEY, p.source(), item, qty, expected, 0, after);
        // otherwise ShopServer.buy refused it for another reason: its own chat message explains it
    }

    public static void sendResult(ServerPlayerEntity p, String kind, int source, ItemStack item, int qty, long amount, int tier) {
        sendResult(p, kind, source, item, qty, amount, tier, CobbleDollarsBridge.getBalance(p));
    }

    public static void sendResult(ServerPlayerEntity p, String kind, int source, ItemStack item, int qty, long amount, int tier, long balance) {
        if (!ServerPlayNetworking.canSend(p, PhonePayloads.Result.ID)) return;
        NbtCompound d = new NbtCompound();
        d.putString("k", kind);
        d.putInt("src", source);
        if (item != null && !item.isEmpty()) {
            try {
                d.put("item", item.copyWithCount(1).encode(p.getRegistryManager()));
            } catch (Exception ignored) {
                // the notice simply shows no item
            }
        }
        d.putInt("qty", qty);
        d.putLong("amount", amount);
        d.putLong("bal", balance);
        d.putInt("tier", tier);
        ServerPlayNetworking.send(p, new PhonePayloads.Result(d));
    }
}
