package dev.tcgshop.shop;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.platform.event.ServerEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
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
        ServerEvents.LOOT_TABLE.register((key, builtin, registries, addPool) -> {
            if (!builtin || !key.location().getPath().startsWith("chests/")) return;
            List<Item> packs = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) packs.add(BuiltInRegistries.ITEM.get(id));
            if (packs.isEmpty()) return;
            LootPool.Builder pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(CHANCE));
            for (Item it : packs) {
                String set = CardInfo.setId(BuiltInRegistries.ITEM.getKey(it));
                pool.add(LootItem.lootTableItem(it).when(() -> new PackLock.Unlocked(set)));
            }
            addPool.accept(pool);
            // ultra-rare prize: a pack from the locked sets (0.001% per chest by default)
            LootPool.Builder rare = LootPool.lootPool().setRolls(ConstantValue.exactly(1)).when(PackLock.RareRoll::new);
            for (Item it : packs) {
                String set = CardInfo.setId(BuiltInRegistries.ITEM.getKey(it));
                rare.add(LootItem.lootTableItem(it).when(() -> new PackLock.Locked(set)));
            }
            addPool.accept(rare);
        });
    }
}
