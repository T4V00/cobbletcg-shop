package dev.tcgshop.shop;

import dev.tcgshop.card.CardInfo;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Sorpresa: los cofres generados del mundo (aldeas, templos, minas, estructuras de otros mods...) tienen una
 * probabilidad muy pequeña de traer un sobre sellado al azar de cualquier expansión.
 */
public final class ChestLoot {
    /** 1 de cada 100 cofres. */
    public static float CHANCE = 0.01f; // EconomyConfig (se aplica al recargar datos)

    private ChestLoot() {}

    public static void register() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (!source.isBuiltin() || !key.getValue().getPath().startsWith("chests/")) return;
            List<Item> packs = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) packs.add(Registries.ITEM.get(id));
            if (packs.isEmpty()) return;
            LootPool.Builder pool = LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                    .conditionally(RandomChanceLootCondition.builder(CHANCE));
            for (Item it : packs) {
                String set = CardInfo.setId(Registries.ITEM.getId(it));
                pool.with(ItemEntry.builder(it).conditionally(() -> new PackLock.Unlocked(set)));
            }
            builder.pool(pool);
            // premio rarísimo: un sobre de los sets bloqueados (por defecto 0,001% por cofre)
            LootPool.Builder rare = LootPool.builder().rolls(ConstantLootNumberProvider.create(1)).conditionally(PackLock.RareRoll::new);
            for (Item it : packs) {
                String set = CardInfo.setId(Registries.ITEM.getId(it));
                rare.with(ItemEntry.builder(it).conditionally(() -> new PackLock.Locked(set)));
            }
            builder.pool(rare);
        });
    }
}
