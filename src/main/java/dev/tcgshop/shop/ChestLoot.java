package dev.tcgshop.shop;

import dev.masterball.api.card.CardInfo;
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
 * Surprise: world-generated chests (villages, temples, mines, other mods' structures...) have a
 * very small chance of containing a random sealed pack from any expansion.
 */
public final class ChestLoot {
    /** 1 in 100 chests. */
    public static float CHANCE = 0.01f; // EconomyConfig (applied on data reload)

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
            // ultra-rare prize: a pack from the locked sets (0.001% per chest by default)
            LootPool.Builder rare = LootPool.builder().rolls(ConstantLootNumberProvider.create(1)).conditionally(PackLock.RareRoll::new);
            for (Item it : packs) {
                String set = CardInfo.setId(Registries.ITEM.getId(it));
                rare.with(ItemEntry.builder(it).conditionally(() -> new PackLock.Locked(set)));
            }
            builder.pool(rare);
        });
    }
}
