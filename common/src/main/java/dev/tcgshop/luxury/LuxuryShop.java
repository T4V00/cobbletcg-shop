package dev.tcgshop.luxury;

import dev.masterball.api.platform.Net;
import dev.masterball.api.platform.event.PlayerEvents;
import dev.masterball.api.platform.event.ServerEvents;
import dev.tcgshop.TcgShop;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.FurnitureSellerEntity;
import dev.tcgshop.entity.NpcLines;
import dev.tcgshop.entity.TcgNpc;
import dev.tcgshop.network.FurniturePayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sale of the luxury statues in the furniture shop (Furniture Seller and phone app), "Exclusives" tab.
 * Fixed price from the config (no shipping surcharge or seller discount), one at a time, charged on the server
 * just like furniture. They cannot be resold: they are a money sink and a status symbol.
 */
public final class LuxuryShop {
    private LuxuryShop() {}

    /** Is anything for sale in Exclusives? (statues or the mansion key) */
    public static boolean enabled() {
        return LuxuryConfig.INSTANCE.enabled || LuxuryConfig.INSTANCE.mansionEnabled;
    }

    @Nullable
    public static StatueTier tierOf(@Nullable ResourceLocation id) {
        if (id == null || !TcgShop.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("statue_")) return null;
        return StatueTier.byKey(id.getPath().substring("statue_".length()));
    }

    public static boolean isStatue(@Nullable ResourceLocation id) {
        return tierOf(id) != null;
    }

    public static boolean isStatue(ItemStack st) {
        return !st.isEmpty() && st.getItem() instanceof StatueItem;
    }

    /** Id of the MasterBall Mansion Key (0.9.5). */
    public static final ResourceLocation MANSION_KEY_ID = ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "mansion_key");

    public static boolean isMansionKey(@Nullable ResourceLocation id) {
        return MANSION_KEY_ID.equals(id);
    }

    public static boolean mansionEnabled() {
        return LuxuryConfig.INSTANCE.mansionEnabled;
    }

    /** Is it an item from the Exclusives tab (statue or mansion key)? Fixed price, one at a time, no resale. */
    public static boolean isExclusive(@Nullable ResourceLocation id) {
        return isStatue(id) || isMansionKey(id);
    }

    /** Real (server) price of an exclusive, or -1 if it is not one. */
    public static long priceOf(@Nullable ResourceLocation id) {
        StatueTier t = tierOf(id);
        if (t != null) return t.price();
        if (isMansionKey(id)) return LuxuryConfig.INSTANCE.mansionPrice;
        return -1;
    }

    /** Ids of the exclusives for sale (statues from cheapest to most expensive, and the mansion key). */
    public static List<ResourceLocation> catalog() {
        List<ResourceLocation> out = new ArrayList<>();
        if (LuxuryConfig.INSTANCE.enabled) for (StatueTier t : StatueTier.values()) out.add(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, t.blockName()));
        if (mansionEnabled()) out.add(MANSION_KEY_ID); // the most expensive, at the end
        return out;
    }

    /** Buys a statue (always one at a time). Validate the source first (distance to the NPC / phone). */
    public static void buy(ServerPlayer player, ResourceLocation id, @Nullable Entity npc) {
        StatueTier t = tierOf(id);
        boolean key = isMansionKey(id);
        if (key ? !mansionEnabled() : (t == null || !LuxuryConfig.INSTANCE.enabled)) return;
        long price = priceOf(id);
        ItemStack shown = key ? new ItemStack(LuxuryBlocks.MANSION_KEY) : new ItemStack(LuxuryBlocks.item(t));
        // never onto the ground: a dropped statue worth up to 10^15 (e.g. bought by phone) could be picked up by anyone or despawn
        if (player.getInventory().getFreeSlot() < 0) {
            player.displayClientMessage(Component.translatable("tcgshop.lux.inv_full").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(player, price)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(price)).withStyle(ChatFormatting.RED), true);
            if (npc instanceof FurnitureSellerEntity s) NpcLines.say(s, player, "furn_no_money");
            return;
        }
        player.getInventory().placeItemBackInInventory(shown.copy()); // there is a free slot (checked above)
        ShopZones.get(player.serverLevel()).addCost(player.getUUID(), price);
        int level = key ? StatueTier.values().length : t.index(); // the key counts as the highest tier
        player.displayClientMessage(Component.translatable("tcgshop.lux.bought", shown.getHoverName(),
                Component.literal(PriceService.format(price)).withStyle(ChatFormatting.GOLD)), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS,
                0.5f + 0.05f * level, 1.0f);
        if (npc instanceof FurnitureSellerEntity s) {
            NpcLines.say(s, player, "furn_buy");
            s.flashEmote(TcgNpc.EMOTE_MONEY);
        }
        int from = LuxuryConfig.INSTANCE.announceFromTier;
        if (from > 0 && level >= from && player.getServer() != null)
            player.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("tcgshop.lux.announce", player.getDisplayName(), shown.getHoverName(),
                    Component.literal(PriceService.format(price)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        ShopServer.syncZones(player);
    }

    // ------------------------------------------------------------------ client sync

    @Nullable private static MinecraftServer server;

    /**
     * Tells the client whether statues are sold and at what price (SERVER config): the phone app, the
     * Exclusives button and the tooltip price depend on this, not on the client's local config. Sent in a
     * FurniturePayloads.Open with source = {@link FurniturePayloads#SOURCE_LUXURY_SYNC}: ids = statues for sale (empty if
     * disabled), buy = their prices.
     */
    public static void syncTo(ServerPlayer player) {
        if (!Net.INSTANCE.canSend(player, FurniturePayloads.Open.ID)) return;
        List<String> ids = new ArrayList<>();
        List<Long> buy = new ArrayList<>(), sell = new ArrayList<>();
        for (ResourceLocation id : catalog()) {
            long pr = priceOf(id);
            if (pr <= 0) continue;
            ids.add(id.toString());
            buy.add(pr);
            sell.add(0L);
        }
        Net.INSTANCE.send(player, new FurniturePayloads.Open(FurniturePayloads.SOURCE_LUXURY_SYNC, false, true, -1, ids, buy, sell, ""));
    }

    /** Resends the info to everyone online (after /tcgshop reload, which re-reads the config). */
    public static void syncAll() {
        MinecraftServer s = server;
        if (s == null) return;
        for (ServerPlayer pl : s.getPlayerList().getPlayers()) syncTo(pl);
    }

    /** Server hooks: info on join and a server reference for resending after a reload. */
    static void registerSync() {
        ServerEvents.STARTED.register(s -> server = s);
        ServerEvents.STOPPED.register(s -> server = null);
        PlayerEvents.JOIN.register(player -> syncTo(player));
    }

    /** Attempt to sell a statue: explains that they cannot be resold. */
    public static void refuseSale(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("tcgshop.lux.no_resale").withStyle(ChatFormatting.RED), true);
    }

    /** Attempt to sell an exclusive (statue or mansion key). */
    public static void refuseSale(ServerPlayer player, @Nullable ResourceLocation id) {
        if (isMansionKey(id)) player.displayClientMessage(Component.translatable("tcgshop.mansion.no_resale").withStyle(ChatFormatting.RED), true);
        else refuseSale(player);
    }
}
