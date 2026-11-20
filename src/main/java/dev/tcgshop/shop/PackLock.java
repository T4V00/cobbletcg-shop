package dev.tcgshop.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.TcgShop;
import dev.tcgshop.card.CardInfo;
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
 * Sobres bloqueados: los N sets con el sobre más caro (EconomyConfig.lockTopPackSets) no salen en ningún sitio
 * (Mayorista, teléfono, cofres, kit de inicio, Mystery Box, revendedores ni clientes). Los que ya tengas se quedan.
 */
public final class PackLock {
    private PackLock() {}

    private static Map<String, Double> seenData;
    private static int seenN = -1;
    private static Set<String> locked = Set.of();

    /** Sets bloqueados ahora mismo (se recalcula si cambian los precios o la opción). */
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
        for (String s : sets) {
            Double usd = data.get(s + "|pack");
            if (usd == null && data.get(s + "|box") != null) usd = data.get(s + "|box") / 36.0;
            if (usd != null) ranked.add(Map.entry(s, usd));
        }
        ranked.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(n, ranked.size()); i++) out.add(ranked.get(i).getKey());
        TcgShop.LOGGER.info("Sobres bloqueados (top {} más caros): {}", n, out);
        return locked = out;
    }

    public static boolean locked(String setId) {
        return setId != null && lockedSets().contains(setId);
    }

    /** Para el id de un sobre sellado de cobbletcg. */
    public static boolean locked(Identifier itemId) {
        return locked(CardInfo.setId(itemId));
    }

    /** Sobre o caja de un set bloqueado. */
    public static boolean locked(ItemStack st) {
        if (CardInfo.isPack(st)) return locked(CardInfo.setId(CardInfo.id(st)));
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) return locked(st.getOrDefault(TcgBlocks.BOX_SET, ""));
        return false;
    }

    // ------------------------------------------------------------------ compra con tienda de nivel alto

    /** Nivel mínimo de tienda configurado (por defecto Diamante). */
    public static ShopTier minTier() {
        String k = EconomyConfig.INSTANCE.lockedPacksMinShopTier;
        for (ShopTier t : ShopTier.values()) if (t.key.equalsIgnoreCase(k) || t.name().equalsIgnoreCase(k)) return t;
        return ShopTier.DIAMOND;
    }

    /** ¿Tiene este jugador una tienda del nivel mínimo o superior (en cualquier dimensión)? */
    public static boolean canBuyLocked(net.minecraft.server.network.ServerPlayerEntity p) {
        int need = minTier().ordinal();
        for (net.minecraft.server.world.ServerWorld w : p.getServer().getWorlds())
            for (ShopZones.Zone z : ShopZones.get(w).all())
                if (z.owner.equals(p.getUuid()) && z.tier >= need) return true;
        return false;
    }

    /** Catálogo que ve este jugador: sin los sets bloqueados si no tiene la tienda necesaria. */
    public static List<ItemStack> visibleTo(net.minecraft.server.network.ServerPlayerEntity p, List<ItemStack> goods) {
        if (lockedSets().isEmpty() || canBuyLocked(p)) return goods;
        List<ItemStack> out = new ArrayList<>(goods.size());
        for (ItemStack st : goods) if (!locked(st)) out.add(st);
        return out;
    }

    // ------------------------------------------------------------------ cofres

    /** Condición de botín: el sobre sólo puede salir si su set no está bloqueado (se evalúa al abrir el cofre). */
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

    /** Condición de botín: el sobre SÍ es de un set bloqueado (para el premio rarísimo de cualquier cofre). */
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

    /** Tirada con la probabilidad configurada (lockedPackChestChance), leída al abrir el cofre. */
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
