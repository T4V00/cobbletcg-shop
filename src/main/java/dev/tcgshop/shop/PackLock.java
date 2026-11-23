package dev.tcgshop.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.TcgShop;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.LootConditionType;
import net.minecraft.loot.context.LootContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Locked packs: the N sets with the most expensive pack (EconomyConfig.lockTopPackSets) don't appear anywhere
 * (Wholesaler, phone, chests, starter kit, Mystery Box, resellers or customers). The ones you already have stay.
 */
public final class PackLock {
    private PackLock() {}

    private static Map<String, Double> seenData;
    private static int seenN = -1;
    private static Set<String> locked = Set.of();

    /** Currently locked sets (recomputed if prices or the option change). */
    public static synchronized Set<String> lockedSets() {
        int n = EconomyConfig.INSTANCE.lockTopPackSets;
        Map<String, Double> data = PriceService.sealedUsdView();
        if (n == seenN && data == seenData) return locked;
        seenN = n;
        seenData = data;
        if (n <= 0 || data.isEmpty()) return locked = Set.of();
        Set<String> sets = new LinkedHashSet<>();
        for (Identifier id : Registries.ITEM.getIds())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) sets.add(CardInfo.setId(id));
        List<Map.Entry<String, Double>> ranked = new ArrayList<>();
        for (String s : sets) ranked.add(Map.entry(s, PriceService.sealedUsd(s, PriceService.Sealed.PACK)));
        ranked.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(n, ranked.size()); i++) out.add(ranked.get(i).getKey());
        TcgShop.LOGGER.info("Locked packs (top {} most expensive): {}", n, out);
        return locked = out;
    }

    public static boolean locked(String setId) {
        return setId != null && lockedSets().contains(setId);
    }

    /** For the id of a cobbletcg sealed pack. */
    public static boolean locked(Identifier itemId) {
        return locked(CardInfo.setId(itemId));
    }

    /** Pack or box of a locked set. */
    public static boolean locked(ItemStack st) {
        if (CardInfo.isPack(st)) return locked(CardInfo.setId(CardInfo.id(st)));
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) return locked(st.getOrDefault(TcgBlocks.BOX_SET, ""));
        return false;
    }

    // ------------------------------------------------------------------ purchase with a high-tier shop

    /** Configured minimum shop tier (Diamond by default). */
    public static ShopTier minTier() {
        String k = EconomyConfig.INSTANCE.lockedPacksMinShopTier;
        ShopTier t = ShopTier.byKey(k);
        return t != null ? t : ShopTier.DIAMOND;
    }

    /** Does this player own a shop of the minimum tier or higher (in any dimension)? */
    public static boolean canBuyLocked(net.minecraft.server.network.ServerPlayerEntity p) {
        ShopTier need = minTier();
        for (net.minecraft.server.world.ServerWorld w : p.getServer().getWorlds())
            for (ShopZones.Zone z : ShopZones.get(w).all())
                if (z.owner.equals(p.getUuid()) && z.tierInfo().atLeast(need)) return true;
        return false;
    }

    /** Catalog this player sees: without the locked sets if they lack the required shop. */
    public static List<ItemStack> visibleTo(net.minecraft.server.network.ServerPlayerEntity p, List<ItemStack> goods) {
        if (lockedSets().isEmpty() || canBuyLocked(p)) return goods;
        List<ItemStack> out = new ArrayList<>(goods.size());
        for (ItemStack st : goods) if (!locked(st)) out.add(st);
        return out;
    }

    // ------------------------------------------------------------------ chests

    /** Loot condition: the pack can only drop if its set isn't locked (evaluated when the chest is opened). */
    public record Unlocked(String set) implements LootCondition {
        public static final MapCodec<Unlocked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("set").forGetter(Unlocked::set)).apply(i, Unlocked::new));

        @Override
        public LootConditionType getType() {
            return TYPE;
        }

        @Override
        public boolean test(LootContext ctx) {
            return !locked(set);
        }
    }

    /** Loot condition: the pack IS from a locked set (for the ultra-rare prize in any chest). */
    public record Locked(String set) implements LootCondition {
        public static final MapCodec<Locked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("set").forGetter(Locked::set)).apply(i, Locked::new));

        @Override
        public LootConditionType getType() {
            return LOCKED_TYPE;
        }

        @Override
        public boolean test(LootContext ctx) {
            return locked(set);
        }
    }

    /** Roll with the configured chance (lockedPackChestChance), read when the chest is opened. */
    public record RareRoll() implements LootCondition {
        public static final MapCodec<RareRoll> CODEC = MapCodec.unit(new RareRoll());

        @Override
        public LootConditionType getType() {
            return ROLL_TYPE;
        }

        @Override
        public boolean test(LootContext ctx) {
            double ch = EconomyConfig.INSTANCE.lockedPackChestChance;
            return ch > 0 && !lockedSets().isEmpty() && ctx.getRandom().nextDouble() < ch;
        }
    }

    public static LootConditionType TYPE, LOCKED_TYPE, ROLL_TYPE;

    public static void register() {
        TYPE = Registry.register(Registries.LOOT_CONDITION_TYPE, Identifier.of(TcgShop.MOD_ID, "pack_unlocked"), new LootConditionType(Unlocked.CODEC));
        LOCKED_TYPE = Registry.register(Registries.LOOT_CONDITION_TYPE, Identifier.of(TcgShop.MOD_ID, "pack_locked"), new LootConditionType(Locked.CODEC));
        ROLL_TYPE = Registry.register(Registries.LOOT_CONDITION_TYPE, Identifier.of(TcgShop.MOD_ID, "locked_pack_roll"), new LootConditionType(RareRoll.CODEC));
    }
}
