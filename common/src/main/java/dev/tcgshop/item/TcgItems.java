package dev.tcgshop.item;

import dev.masterball.api.platform.Platform;
import dev.tcgshop.TcgShop;
import dev.tcgshop.binder.PersonalBinderData;
import dev.tcgshop.entity.TcgEntities;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

public final class TcgItems {
    public static final DataComponentType<PersonalBinderData> PERSONAL_BINDER_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "personal_binder"),
            DataComponentType.<PersonalBinderData>builder().persistent(PersonalBinderData.CODEC).networkSynchronized(PersonalBinderData.PACKET_CODEC.cast()).build());

    public static final DataComponentType<dev.tcgshop.collection.BinderTag> BINDER_TAG = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "binder_tag"),
            DataComponentType.<dev.tcgshop.collection.BinderTag>builder().persistent(dev.tcgshop.collection.BinderTag.CODEC)
                    .networkSynchronized(dev.tcgshop.collection.BinderTag.PACKET_CODEC.cast()).build());

    public static final Item GENERAL_BINDER = register("general_binder",
            new GeneralBinderItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Item PERSONAL_BINDER = register("personal_binder",
            new PersonalBinderItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
                    .component(PERSONAL_BINDER_DATA, PersonalBinderData.DEFAULT)));
    public static final Item NERD_GLOVES = register("nerd_gloves", new NerdGlovesItem(new Item.Properties().stacksTo(1)));
    /** 1.1: Box-Opening Gloves (buy-only, no recipe): Nerd Gloves + "open the whole box" (pack.BoxGloves). */
    public static final Item BOX_GLOVES = register("box_gloves", new BoxGlovesItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final Item MANAGER_GOGGLES = register("manager_goggles", new ManagerGogglesItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item TCG_PHONE = register("tcg_phone", new PhoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Item DEODORANT_SPRAY = register("deodorant_spray", new DeodorantSprayItem(new Item.Properties().durability(72)));
    /** 0.9 events: mop (puddles, droppings, mess) and repair kit (sparks, scorch marks, showcases). */
    public static final Item MOP = register("mop", new dev.tcgshop.events.incident.IncidentToolItem(new Item.Properties().durability(64), "mop"));
    public static final Item REPAIR_KIT = register("repair_kit", new dev.tcgshop.events.incident.IncidentToolItem(new Item.Properties().durability(12), "repair_kit"));
    public static final Item WHOLESALER_SPAWN_EGG = register("wholesaler_spawn_egg",
            new SpawnEggItem(TcgEntities.WHOLESALER, 0x5B3A1E, 0xF2C230, new Item.Properties()));
    public static final Item FURNITURE_SELLER_SPAWN_EGG = register("furniture_seller_spawn_egg",
            new SpawnEggItem(TcgEntities.FURNITURE_SELLER, 0x8D6E63, 0xFFD9A0, new Item.Properties()));
    public static final Item TRADER_SPAWN_EGG = register("trader_spawn_egg",
            new SpawnEggItem(TcgEntities.TRADER, 0x2E6B5A, 0xF5E6A8, new Item.Properties()));
    public static final Item SCALPER_SPAWN_EGG = register("scalper_spawn_egg",
            new SpawnEggItem(TcgEntities.SCALPER, 0x3B3F4A, 0xE0B33C, new Item.Properties()));

    public static final CreativeModeTab GROUP = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "main"),
            Platform.INSTANCE.creativeTab()
                    .icon(() -> new ItemStack(GENERAL_BINDER))
                    .title(Component.translatable("itemGroup.tcgshop"))
                    .displayItems((ctx, e) -> {
                        e.accept(GENERAL_BINDER);
                        e.accept(PERSONAL_BINDER);
                        e.accept(NERD_GLOVES);
                        e.accept(BOX_GLOVES);
                        e.accept(MANAGER_GOGGLES);
                        e.accept(TCG_PHONE);
                        e.accept(DEODORANT_SPRAY);
                        e.accept(MOP);
                        e.accept(REPAIR_KIT);
                        for (dev.tcgshop.shop.ShopTier t : dev.tcgshop.shop.ShopTier.values()) e.accept(dev.tcgshop.shop.TcgBlocks.CORE_ITEMS.get(t));
                        e.accept(dev.tcgshop.shop.TcgBlocks.DISPLAY_CASE_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.CARD_STAND_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.PACK_SHELF_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.CASH_REGISTER_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.GONDOLA_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.BOX_PALLET_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.PEDESTAL_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.CARD_WALL_ITEM);
                        for (Item it : dev.tcgshop.shop.TcgBlocks.NEW_SHOP_DISPLAYS) e.accept(it);
                        for (Item it : dev.tcgshop.shop.TcgBlocks.HOME_DISPLAYS) e.accept(it);
                        for (Item it : dev.tcgshop.shop.RgbDisplays.ITEMS) e.accept(it); // 1.0 RGB displays
                        e.accept(dev.tcgshop.shop.TcgBlocks.POSTER_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.PLAY_TABLE_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.THEFT_DETECTOR_ITEM);
                        e.accept(dev.tcgshop.shop.TcgBlocks.ROULETTE_ITEM);
                        for (Item it : dev.tcgshop.casino.CasinoBlocks.ITEMS) e.accept(it); // 1.0 beta 3 casino
                        e.accept(dev.tcgshop.shop.TcgBlocks.MYSTERY_BOX);
                        for (Item it : dev.tcgshop.luxury.LuxuryBlocks.items()) e.accept(it);
                        e.accept(dev.tcgshop.world.WorldLoot.TRASH_CAN_ITEM); // 1.0 decorative trash can
                        for (Item it : dev.tcgshop.shop.CleanBlocks.CATALOG) e.accept(it); // 1.1 wall freshener, wide door, decor
                        for (String set : new String[]{"mew151", "prismaticevolutions", "eskies", "baseset", "drivals", "crising"})
                            e.accept(dev.tcgshop.shop.BoosterBox.of(set));
                        e.accept(WHOLESALER_SPAWN_EGG);
                        e.accept(FURNITURE_SELLER_SPAWN_EGG);
                        e.accept(TRADER_SPAWN_EGG);
                        e.accept(SCALPER_SPAWN_EGG);
                    })
                    .build());

    private TcgItems() {}

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, name)), item);
    }

    public static void init() {}
}
