package dev.tcgshop.luxury;

import dev.tcgshop.TcgShop;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.FurnitureSellerEntity;
import dev.tcgshop.entity.NpcLines;
import dev.tcgshop.entity.TcgNpc;
import dev.tcgshop.network.FurniturePayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Venta de las estatuas de lujo en la tienda de muebles (Vendedor de muebles y app del teléfono), pestaña "Exclusivos".
 * Precio fijo de la configuración (sin recargo de envío ni descuento del vendedor), una a una, cobrado en el servidor
 * igual que los muebles. No se pueden revender: son un sumidero de dinero y un símbolo de estatus.
 */
public final class LuxuryShop {
    private LuxuryShop() {}

    /** ¿Hay algo a la venta en Exclusivos? (estatuas o llave de la mansión) */
    public static boolean enabled() {
        return LuxuryConfig.INSTANCE.enabled || LuxuryConfig.INSTANCE.mansionEnabled;
    }

    @Nullable
    public static StatueTier tierOf(@Nullable Identifier id) {
        if (id == null || !TcgShop.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("statue_")) return null;
        return StatueTier.byKey(id.getPath().substring("statue_".length()));
    }

    public static boolean isStatue(@Nullable Identifier id) {
        return tierOf(id) != null;
    }

    public static boolean isStatue(ItemStack st) {
        return !st.isEmpty() && st.getItem() instanceof StatueItem;
    }

    /** Id de la Llave de la Mansión MasterBall (0.9.5). */
    public static final Identifier MANSION_KEY_ID = Identifier.of(TcgShop.MOD_ID, "mansion_key");

    public static boolean isMansionKey(@Nullable Identifier id) {
        return MANSION_KEY_ID.equals(id);
    }

    public static boolean mansionEnabled() {
        return LuxuryConfig.INSTANCE.mansionEnabled;
    }

    /** ¿Es un artículo de la pestaña Exclusivos (estatua o llave de la mansión)? Precio fijo, de uno en uno, sin reventa. */
    public static boolean isExclusive(@Nullable Identifier id) {
        return isStatue(id) || isMansionKey(id);
    }

    /** Precio real (servidor) de un exclusivo, o -1 si no lo es. */
    public static long priceOf(@Nullable Identifier id) {
        StatueTier t = tierOf(id);
        if (t != null) return t.price();
        if (isMansionKey(id)) return LuxuryConfig.INSTANCE.mansionPrice;
        return -1;
    }

    /** Ids de los exclusivos a la venta (estatuas de la más barata a la más cara y la llave de la mansión). */
    public static List<Identifier> catalog() {
        List<Identifier> out = new ArrayList<>();
        if (LuxuryConfig.INSTANCE.enabled) for (StatueTier t : StatueTier.values()) out.add(Identifier.of(TcgShop.MOD_ID, t.blockName()));
        if (mansionEnabled()) out.add(MANSION_KEY_ID); // la más cara, al final
        return out;
    }

    /** Compra una estatua (siempre de una en una). Validar antes la fuente (distancia al NPC / teléfono). */
    public static void buy(ServerPlayerEntity player, Identifier id, @Nullable Entity npc) {
        StatueTier t = tierOf(id);
        boolean key = isMansionKey(id);
        if (key ? !mansionEnabled() : (t == null || !LuxuryConfig.INSTANCE.enabled)) return;
        long price = priceOf(id);
        ItemStack shown = key ? new ItemStack(LuxuryBlocks.MANSION_KEY) : new ItemStack(LuxuryBlocks.item(t));
        // nunca al suelo: una estatua de hasta 10^15 tirada (p.ej. comprada por teléfono) la coge cualquiera o desaparece
        if (player.getInventory().getEmptySlot() < 0) {
            player.sendMessage(Text.translatable("tcgshop.lux.inv_full").formatted(Formatting.RED), true);
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(player, price)) {
            player.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(price)).formatted(Formatting.RED), true);
            if (npc instanceof FurnitureSellerEntity s) NpcLines.say(s, player, "furn_no_money");
            return;
        }
        player.getInventory().offerOrDrop(shown.copy()); // hay hueco libre (comprobado arriba)
        ShopZones.get(player.getServerWorld()).addCost(player.getUuid(), price);
        int level = key ? StatueTier.values().length : t.index(); // la llave cuenta como el nivel más alto
        player.sendMessage(Text.translatable("tcgshop.lux.bought", shown.getName(),
                Text.literal(PriceService.format(price)).formatted(Formatting.GOLD)), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS,
                0.5f + 0.05f * level, 1.0f);
        if (npc instanceof FurnitureSellerEntity s) {
            NpcLines.say(s, player, "furn_buy");
            s.flashEmote(TcgNpc.EMOTE_MONEY);
        }
        int from = LuxuryConfig.INSTANCE.announceFromTier;
        if (from > 0 && level >= from && player.getServer() != null)
            player.getServer().getPlayerManager().broadcast(Text.translatable("tcgshop.lux.announce", player.getDisplayName(), shown.getName(),
                    Text.literal(PriceService.format(price)).formatted(Formatting.GOLD)).formatted(Formatting.LIGHT_PURPLE), false);
        ShopServer.syncZones(player);
    }

    // ------------------------------------------------------------------ sincronización con el cliente

    @Nullable private static MinecraftServer server;

    /**
     * Manda al cliente si se venden estatuas y a qué precio (config del SERVIDOR): la app del teléfono, el botón
     * Exclusivos y el precio del tooltip dependen de esto, no de la config local del cliente. Va en un
     * FurniturePayloads.Open con source = {@link FurniturePayloads#SOURCE_LUXURY_SYNC}: ids = estatuas a la venta (vacío si
     * están desactivadas), buy = sus precios.
     */
    public static void syncTo(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, FurniturePayloads.Open.ID)) return;
        List<String> ids = new ArrayList<>();
        List<Long> buy = new ArrayList<>(), sell = new ArrayList<>();
        for (Identifier id : catalog()) {
            long pr = priceOf(id);
            if (pr <= 0) continue;
            ids.add(id.toString());
            buy.add(pr);
            sell.add(0L);
        }
        ServerPlayNetworking.send(player, new FurniturePayloads.Open(FurniturePayloads.SOURCE_LUXURY_SYNC, false, true, -1, ids, buy, sell, ""));
    }

    /** Reenvía la info a todos los conectados (tras /tcgshop reload, que relee la config). */
    public static void syncAll() {
        MinecraftServer s = server;
        if (s == null) return;
        for (ServerPlayerEntity pl : s.getPlayerManager().getPlayerList()) syncTo(pl);
    }

    /** Ganchos del servidor: info al entrar y referencia al servidor para el reenvío tras recargar. */
    static void registerSync() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> syncTo(handler.getPlayer()));
    }

    /** Intento de vender una estatua: se explica que no se revenden. */
    public static void refuseSale(ServerPlayerEntity player) {
        player.sendMessage(Text.translatable("tcgshop.lux.no_resale").formatted(Formatting.RED), true);
    }

    /** Intento de vender un exclusivo (estatua o llave de la mansión). */
    public static void refuseSale(ServerPlayerEntity player, @Nullable Identifier id) {
        if (isMansionKey(id)) player.sendMessage(Text.translatable("tcgshop.mansion.no_resale").formatted(Formatting.RED), true);
        else refuseSale(player);
    }
}
