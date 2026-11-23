package dev.tcgshop.shop;

import dev.tcgshop.EconomyConfig;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 1.0: Cobblemon-side items the traders buy (common client / server, no compile-time dependency on Cobblemon or its add-ons).
 * <ul>
 *   <li><b>Poké Balls</b> (the Wholesaler only, {@link EconomyConfig#pokeBallSellPrice} each). Rule: namespace {@code cobblemon}
 *       and either in the {@code #cobblemon:poke_balls} item tag (all standard, apricorn and ancient balls) or, if the tag is
 *       missing/incomplete, a path ending in {@code _ball}. Held items that only share the suffix are excluded:
 *       {@code light_ball}, {@code smoke_ball}, {@code iron_ball}. Render-only ids such as {@code poke_ball_model} end in
 *       {@code _model} and never match.</li>
 *   <li><b>Mega Stones</b> (every trader: Wholesaler, Wandering Collector, Furniture Vendor; {@link EconomyConfig#megaStoneSellPrice}
 *       each). Rule: any item in one of the tags {@code #mega_showdown:mega_stone}, {@code #cobblemon:mega_stones},
 *       {@code #c:mega_stones}; or, as a fallback (Mega Showdown's own tag misses some stones, e.g. abomasite, and the
 *       Z-A add-on has no tag), an item of the namespaces {@code cobblemon}, {@code mega_showdown} or {@code zamega} whose path
 *       is a stone name: {@code <name>ite}, optionally followed by {@code x/y/z} or {@code _x/_y/_z} (charizardite_x,
 *       mewtwonite_y, raichunitex, absolitez...). Known false positives are excluded: paths ending in {@code white}
 *       (aprijuice_white, pokedex_white, tera_pouch_white...), {@code eviolite}, {@code black_augurite},
 *       {@code deoxys_meteorite}, {@code meteorite}.</li>
 * </ul>
 */
public final class TraderGoods {
    private TraderGoods() {}

    /** Item id sent in FurniturePayloads.Trade (sell = true) to sell every Mega Stone to the Furniture Vendor. */
    public static final String MEGA_TRADE_ID = "tcgshop:sell_mega_stones";

    private static final TagKey<Item> POKE_BALLS = TagKey.of(RegistryKeys.ITEM, Identifier.of("cobblemon", "poke_balls"));
    private static final List<TagKey<Item>> MEGA_TAGS = List.of(
            TagKey.of(RegistryKeys.ITEM, Identifier.of("mega_showdown", "mega_stone")),
            TagKey.of(RegistryKeys.ITEM, Identifier.of("cobblemon", "mega_stones")),
            TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "mega_stones")));
    private static final Set<String> NOT_BALLS = Set.of("light_ball", "smoke_ball", "iron_ball");
    private static final Set<String> MEGA_NAMESPACES = Set.of("cobblemon", "mega_showdown", "zamega");
    private static final Set<String> NOT_MEGA = Set.of("eviolite", "black_augurite", "deoxys_meteorite", "meteorite");
    private static final Pattern MEGA_PATH = Pattern.compile("[a-z0-9_]+ite(_?[xyz])?");

    // ------------------------------------------------------------------ detection

    public static boolean isPokeBall(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(st.getItem());
        if (!"cobblemon".equals(id.getNamespace())) return false;
        if (st.isIn(POKE_BALLS)) return true;
        String p = id.getPath();
        return p.endsWith("_ball") && !NOT_BALLS.contains(p);
    }

    public static boolean isMegaStone(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        for (TagKey<Item> t : MEGA_TAGS) if (st.isIn(t)) return true;
        Identifier id = Registries.ITEM.getId(st.getItem());
        if (!MEGA_NAMESPACES.contains(id.getNamespace())) return false;
        String p = id.getPath();
        return !p.endsWith("white") && !NOT_MEGA.contains(p) && MEGA_PATH.matcher(p).matches();
    }

    // ------------------------------------------------------------------ prices

    public static long megaStonePrice() {
        return Math.max(0, EconomyConfig.INSTANCE.megaStoneSellPrice);
    }

    public static long pokeBallPrice() {
        return Math.max(0, EconomyConfig.INSTANCE.pokeBallSellPrice);
    }

    /** What the Wholesaler pays per unit for a non-vanilla, non-card item (0 = not bought). Used by VanillaPrices.price. */
    public static double unitPrice(ItemStack st) {
        if (isMegaStone(st)) return megaStonePrice();
        if (isPokeBall(st)) return pokeBallPrice();
        return 0;
    }

    /** Mega Stones in the main inventory (slots 0-35, same as the server sale). */
    public static int countMegaStones(PlayerEntity p) {
        if (p == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (isMegaStone(st)) n += st.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------------ server

    /**
     * Sells every Mega Stone in the main inventory (never armor, offhand or accessory slots) for CobbleDollars.
     * The caller validates the trader (distance, alive). Returns the amount paid (0 = nothing sold).
     */
    public static long sellMegaStones(ServerPlayerEntity player) {
        if (!CobbleDollarsBridge.isAvailable()) {
            player.sendMessage(Text.translatable("tcgshop.sell.no_bank").formatted(Formatting.RED), true);
            return 0;
        }
        long unit = megaStonePrice();
        int n = unit <= 0 ? 0 : countMegaStones(player);
        if (n == 0) {
            player.sendMessage(Text.translatable("tcgshop.sell.no_mega").formatted(Formatting.GRAY), true);
            return 0;
        }
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) if (isMegaStone(inv.getStack(i))) inv.setStack(i, ItemStack.EMPTY);
        inv.markDirty();
        long total = unit * n;
        CobbleDollarsBridge.add(player, total);
        player.sendMessage(Text.translatable("tcgshop.sell.mega_done", n, Text.literal(PriceService.format(total)).formatted(Formatting.GOLD)), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.3f);
        ShopServer.syncZones(player);
        return total;
    }
}
