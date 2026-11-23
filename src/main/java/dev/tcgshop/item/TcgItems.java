package dev.tcgshop.item;

import dev.tcgshop.TcgShop;
import dev.tcgshop.binder.PersonalBinderData;
import dev.tcgshop.entity.TcgEntities;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class TcgItems {
    public static final ComponentType<PersonalBinderData> PERSONAL_BINDER_DATA = Registry.register(Registries.DATA_COMPONENT_TYPE,
            Identifier.of(TcgShop.MOD_ID, "personal_binder"),
            ComponentType.<PersonalBinderData>builder().codec(PersonalBinderData.CODEC).packetCodec(PersonalBinderData.PACKET_CODEC.cast()).build());

    public static final ComponentType<dev.tcgshop.collection.BinderTag> BINDER_TAG = Registry.register(Registries.DATA_COMPONENT_TYPE,
            Identifier.of(TcgShop.MOD_ID, "binder_tag"),
            ComponentType.<dev.tcgshop.collection.BinderTag>builder().codec(dev.tcgshop.collection.BinderTag.CODEC)
                    .packetCodec(dev.tcgshop.collection.BinderTag.PACKET_CODEC.cast()).build());

    public static final Item GENERAL_BINDER = register("general_binder",
            new GeneralBinderItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
    public static final Item PERSONAL_BINDER = register("personal_binder",
            new PersonalBinderItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)
                    .component(PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT)));
    public static final Item NERD_GLOVES = register("nerd_gloves", new NerdGlovesItem(new Item.Settings().maxCount(1)));
    public static final Item MANAGER_GOGGLES = register("manager_goggles", new ManagerGogglesItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
    public static final Item TCG_PHONE = register("tcg_phone", new PhoneItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
    public static final Item DEODORANT_SPRAY = register("deodorant_spray", new DeodorantSprayItem(new Item.Settings().maxDamage(72)));
    /** 0.9 events: mop (puddles, droppings, mess) and repair kit (sparks, scorch marks, showcases). */
    public static final Item MOP = register("mop", new dev.tcgshop.events.incident.IncidentToolItem(new Item.Settings().maxDamage(64), "mop"));
    public static final Item REPAIR_KIT = register("repair_kit", new dev.tcgshop.events.incident.IncidentToolItem(new Item.Settings().maxDamage(12), "repair_kit"));
    public static final Item WHOLESALER_SPAWN_EGG = register("wholesaler_spawn_egg",
            new SpawnEggItem(TcgEntities.WHOLESALER, 0x5B3A1E, 0xF2C230, new Item.Settings()));
    public static final Item FURNITURE_SELLER_SPAWN_EGG = register("furniture_seller_spawn_egg",
            new SpawnEggItem(TcgEntities.FURNITURE_SELLER, 0x8D6E63, 0xFFD9A0, new Item.Settings()));
    public static final Item TRADER_SPAWN_EGG = register("trader_spawn_egg",
            new SpawnEggItem(TcgEntities.TRADER, 0x2E6B5A, 0xF5E6A8, new Item.Settings()));
    public static final Item SCALPER_SPAWN_EGG = register("scalper_spawn_egg",
            new SpawnEggItem(TcgEntities.SCALPER, 0x3B3F4A, 0xE0B33C, new Item.Settings()));

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, Identifier.of(TcgShop.MOD_ID, "main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(GENERAL_BINDER))
                    .displayName(Text.translatable("itemGroup.tcgshop"))
                    .entries((ctx, e) -> {
                        e.add(GENERAL_BINDER);
                        e.add(PERSONAL_BINDER);
                        e.add(NERD_GLOVES);
                        e.add(MANAGER_GOGGLES);
                        e.add(TCG_PHONE);
                        e.add(DEODORANT_SPRAY);
                        e.add(MOP);
                        e.add(REPAIR_KIT);
                        for (dev.tcgshop.shop.ShopTier t : dev.tcgshop.shop.ShopTier.values()) e.add(dev.tcgshop.shop.TcgBlocks.CORE_ITEMS.get(t));
                        e.add(dev.tcgshop.shop.TcgBlocks.DISPLAY_CASE_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.CARD_STAND_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.PACK_SHELF_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.CASH_REGISTER_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.GONDOLA_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.BOX_PALLET_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.PEDESTAL_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.CARD_WALL_ITEM);
                        for (Item it : dev.tcgshop.shop.TcgBlocks.NEW_SHOP_DISPLAYS) e.add(it);
                        for (Item it : dev.tcgshop.shop.TcgBlocks.HOME_DISPLAYS) e.add(it);
                        for (Item it : dev.tcgshop.shop.RgbDisplays.ITEMS) e.add(it); // 1.0 RGB displays
                        e.add(dev.tcgshop.shop.TcgBlocks.POSTER_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.PLAY_TABLE_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.THEFT_DETECTOR_ITEM);
                        e.add(dev.tcgshop.shop.TcgBlocks.ROULETTE_ITEM);
                        for (Item it : dev.tcgshop.casino.CasinoBlocks.ITEMS) e.add(it); // 1.0 beta 3 casino
                        e.add(dev.tcgshop.shop.TcgBlocks.MYSTERY_BOX);
                        for (Item it : dev.tcgshop.luxury.LuxuryBlocks.items()) e.add(it);
                        e.add(dev.tcgshop.world.WorldLoot.TRASH_CAN_ITEM); // 1.0 decorative trash can
                        for (String set : new String[]{"mew151", "prismaticevolutions", "eskies", "baseset", "drivals", "crising"})
                            e.add(dev.tcgshop.shop.BoosterBox.of(set));
                        e.add(WHOLESALER_SPAWN_EGG);
                        e.add(FURNITURE_SELLER_SPAWN_EGG);
                        e.add(TRADER_SPAWN_EGG);
                        e.add(SCALPER_SPAWN_EGG);
                    })
                    .build());

    private TcgItems() {}

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, RegistryKey.of(RegistryKeys.ITEM, Identifier.of(TcgShop.MOD_ID, name)), item);
    }

    public static void init() {}
}
