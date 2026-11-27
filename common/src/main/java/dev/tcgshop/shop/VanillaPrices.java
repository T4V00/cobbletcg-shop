package dev.tcgshop.shop;

import dev.masterball.api.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.tcgshop.TcgShop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the Wholesaler pays for vanilla Minecraft items (CobbleDollars per unit, with decimals:
 * everything you sell is summed and rounded down). Full, editable table in
 * config/tcgshop-vanilla-prices.json; generated the first time with reference prices:
 * basic materials ≈ 0.5, iron/copper ≈ 1; since 1.0 gold ingots pay 300, emeralds and diamonds 50
 * (their compact blocks 9×, hence MAX = 5,000). Easy-to-farm items pay very little
 * and compact blocks are worth exactly the same as their pieces (no profit from crafting).
 * 0 = the Wholesaler does not buy it.
 * Non-vanilla items: Cobblemon Poké Balls and Mega Stones (prices in tcgshop-economy.json, see {@link TraderGoods}).
 */
public final class VanillaPrices {
    private VanillaPrices() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Platform.INSTANCE.configDir().resolve("tcgshop-vanilla-prices.json");
    public static final double MAX = 5000;
    /**
     * Version of the reference prices, stored in the file as "_version" (files from before 1.0 have none = 1).
     * v2 (1.0): emerald / gold ingot 300, diamond 500 (and their nuggets/blocks, raw gold) are overwritten in old files.
     * v3 (1.0 release): emerald and diamond drop to 50 (blocks 450), also overwritten in old files.
     */
    private static final int VERSION = 3;
    private static final String[] V3_KEYS = {"emerald", "emerald_block", "diamond", "diamond_block"};
    private static final String[] V2_KEYS = {"emerald", "emerald_block", "gold_ingot", "gold_nugget", "gold_block", "raw_gold", "raw_gold_block",
            "diamond", "diamond_block"};

    private static Map<String, Double> table = new TreeMap<>();

    public static Map<String, Double> table() { return table; }

    /** Price per unit (0 = not bought). Takes tool and armor wear into account. */
    public static double price(ItemStack st) {
        if (st.isEmpty()) return 0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
        if (!id.getNamespace().equals("minecraft")) return TraderGoods.unitPrice(st); // 1.0: Poké Balls and Mega Stones
        Double v = table.get(id.getPath());
        if (v == null || v <= 0) return 0;
        double p = v;
        if (st.isDamageableItem() && st.getMaxDamage() > 0) p *= Math.max(0.1, 1 - st.getDamageValue() / (double) st.getMaxDamage());
        if (st.isEnchanted() && !st.is(Items.ENCHANTED_BOOK)) p *= 1.25;
        if (st.has(DataComponents.CUSTOM_NAME)) p *= 1; // renaming adds no value
        return Math.min(MAX, p);
    }

    public static boolean sellable(ItemStack st) {
        return price(st) > 0;
    }

    // ------------------------------------------------------------------ file

    public static void load() {
        Map<String, Double> user = new LinkedHashMap<>();
        try {
            if (Files.exists(FILE)) try (Reader r = Files.newBufferedReader(FILE)) {
                Map<String, Double> m = GSON.fromJson(r, new TypeToken<Map<String, Double>>() {}.getType());
                if (m != null) user.putAll(m);
            }
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not read {} (using the reference prices and NOT overwriting it)", FILE, e);
            table = defaults();
            return;
        }
        Double ver = user.get("_version");
        if (ver == null || ver < 2) { // 1.0 migration: the new reference prices replace the old values of these items
            for (String k : V2_KEYS) {
                user.remove(k);
                user.remove("minecraft:" + k);
            }
        }
        if (ver == null || ver < 3) { // 1.0 release: emerald / diamond lowered to 50
            for (String k : V3_KEYS) {
                user.remove(k);
                user.remove("minecraft:" + k);
            }
        }
        Map<String, Double> t = defaults();
        for (Map.Entry<String, Double> e : user.entrySet()) {
            String k = e.getKey().startsWith("minecraft:") ? e.getKey().substring(10) : e.getKey();
            if (e.getValue() != null && !k.startsWith("_")) t.put(k, Math.max(0, Math.min(MAX, e.getValue())));
        }
        table = t;
        Map<String, Double> out = new LinkedHashMap<>();
        out.put("_version", (double) VERSION);
        out.putAll(table);
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(out, w);
        } catch (Exception e) {
            TcgShop.LOGGER.error("Could not save {}", FILE, e);
        }
    }

    public static void applySync(JsonObject o) {
        Map<String, Double> t = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) t.put(e.getKey(), e.getValue().getAsDouble());
        table = t;
    }

    // ------------------------------------------------------------------ reference prices

    private static final Map<String, Double> FIXED = new LinkedHashMap<>();

    private static void f(double v, String... ids) {
        for (String id : ids) FIXED.put(id, v);
    }

    static {
        // mining
        f(0.5, "coal", "charcoal", "flint");
        f(1, "raw_iron", "iron_ingot", "raw_copper", "copper_ingot");
        f(0.11, "iron_nugget");
        f(300, "raw_gold", "gold_ingot"); // 1.0: the Wholesaler buys gold at a premium (raw = ingot, like iron/copper); emeralds and diamonds pay 50
        f(33.33, "gold_nugget");
        f(0.3, "redstone");
        f(0.4, "lapis_lazuli", "quartz", "amethyst_shard", "glowstone_dust", "prismarine_shard");
        f(0.6, "prismarine_crystals");
        f(50, "diamond");
        f(50, "emerald");
        f(120, "netherite_scrap", "ancient_debris");
        f(500, "netherite_ingot");
        f(4, "echo_shard");
        // compact blocks = 9 pieces (no profit from crafting)
        f(4.5, "coal_block");
        f(9, "iron_block", "raw_iron_block", "copper_block", "raw_copper_block");
        f(2700, "gold_block", "raw_gold_block");
        f(2.7, "redstone_block");
        f(3.6, "lapis_block");
        f(450, "diamond_block");
        f(450, "emerald_block");
        f(1000, "netherite_block");
        f(1.6, "quartz_block", "glowstone", "amethyst_block");
        // mob loot
        f(0.02, "rotten_flesh", "poisonous_potato");
        f(0.1, "bone", "string", "feather", "leather", "ink_sac", "egg", "arrow");
        f(0.03, "bone_meal");
        f(0.3, "gunpowder", "spider_eye", "slime_ball", "magma_cream", "rabbit_hide", "glow_ink_sac");
        f(0.9, "slime_block", "bone_block");
        f(2, "ender_pearl", "phantom_membrane", "rabbit_foot", "blaze_powder");
        f(3, "blaze_rod", "breeze_rod", "turtle_scute", "armadillo_scute");
        f(6, "ghast_tear", "ender_eye");
        f(15, "nautilus_shell", "experience_bottle");
        f(40, "shulker_shell");
        f(80, "shulker_box");
        f(10, "dragon_breath");
        f(120, "wither_skeleton_skull");
        f(30, "skeleton_skull", "zombie_head", "creeper_head", "piglin_head");
        f(150, "dragon_head", "totem_of_undying");
        f(250, "heart_of_the_sea", "conduit");
        f(300, "trident");
        f(400, "heavy_core");
        f(600, "mace", "enchanted_golden_apple");
        f(1000, "nether_star", "beacon", "elytra", "dragon_egg");
        f(60, "sniffer_egg", "trial_key");
        f(150, "ominous_trial_key");
        f(20, "golden_apple");
        f(2, "golden_carrot", "glistering_melon_slice");
        f(1, "cake", "pumpkin_pie", "honey_bottle", "honeycomb");
        f(0.3, "bread", "cookie", "baked_potato", "dried_kelp_block");
        f(0.3, "cooked_beef", "cooked_porkchop", "cooked_mutton", "cooked_chicken", "cooked_rabbit", "cooked_cod", "cooked_salmon", "rabbit_stew",
                "mushroom_stew", "beetroot_soup", "suspicious_stew");
        f(0.15, "beef", "porkchop", "mutton", "chicken", "rabbit", "cod", "salmon", "tropical_fish", "pufferfish");
        f(0.05, "wheat", "carrot", "potato", "beetroot", "apple", "melon_slice", "sweet_berries", "glow_berries", "sugar_cane", "sugar", "kelp",
                "dried_kelp", "cactus", "bamboo", "cocoa_beans", "nether_wart", "chorus_fruit", "wheat_seeds", "beetroot_seeds", "melon_seeds",
                "pumpkin_seeds", "torchflower_seeds", "pitcher_pod");
        f(0.4, "pumpkin", "melon", "carved_pumpkin", "hay_block");
        // wood and plants
        f(0.06, "stick");
        f(0.5, "bamboo_block", "crimson_stem", "warped_stem");
        // stone and dirt (renewable and extremely abundant: almost nothing)
        f(0.01, "dirt", "cobblestone", "netherrack", "cobbled_deepslate", "gravel", "sand", "red_sand", "stone", "deepslate", "andesite", "diorite",
                "granite", "tuff", "calcite", "end_stone", "blackstone", "basalt", "soul_sand", "soul_soil", "grass_block", "coarse_dirt", "rooted_dirt",
                "mud", "clay_ball", "snowball", "ice", "snow_block", "cobweb");
        f(0.05, "obsidian", "crying_obsidian", "clay", "terracotta", "glass", "packed_ice", "blue_ice", "moss_block", "mycelium", "podzol");
        f(0.5, "sponge", "wet_sponge");
        // miscellaneous utilities
        f(0.2, "paper", "book");
        f(5, "enchanted_book", "name_tag", "saddle");
        f(4, "lead");
        f(10, "music_disc_13", "music_disc_cat", "music_disc_blocks", "music_disc_chirp", "music_disc_far", "music_disc_mall", "music_disc_mellohi",
                "music_disc_stal", "music_disc_strad", "music_disc_ward", "music_disc_11", "music_disc_wait", "music_disc_otherside", "music_disc_5",
                "music_disc_pigstep", "music_disc_relic", "music_disc_creator", "music_disc_creator_music_box", "music_disc_precipice");
        f(40, "netherite_upgrade_smithing_template");
        // never bought
        f(0, "air", "bedrock", "barrier", "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
                "structure_block", "structure_void", "jigsaw", "debug_stick", "knowledge_book", "light", "spawner", "trial_spawner", "vault",
                "reinforced_deepslate", "end_portal_frame", "petrified_oak_slab", "farmland", "dirt_path", "budding_amethyst", "written_book", "filled_map");
    }

    /** Material cost of tools/armor per material. */
    private static double material(String path) {
        if (path.startsWith("netherite_")) return 525;
        if (path.startsWith("diamond_")) return 25;
        if (path.startsWith("golden_")) return 3;
        if (path.startsWith("iron_") || path.startsWith("chainmail_")) return 1;
        if (path.startsWith("stone_")) return 0.01;
        if (path.startsWith("wooden_")) return 0.12;
        if (path.startsWith("leather_")) return 0.1;
        if (path.startsWith("turtle_")) return 3;
        return -1;
    }

    private static double pieces(String path) {
        if (path.endsWith("_sword") || path.endsWith("_hoe")) return 2;
        if (path.endsWith("_pickaxe") || path.endsWith("_axe")) return 3;
        if (path.endsWith("_shovel")) return 1;
        if (path.endsWith("_helmet")) return 5;
        if (path.endsWith("_chestplate")) return 8;
        if (path.endsWith("_leggings")) return 7;
        if (path.endsWith("_boots")) return 4;
        if (path.endsWith("_horse_armor")) return 6;
        return -1;
    }

    private static double guess(Item item, String path) {
        Double fixed = FIXED.get(path);
        if (fixed != null) return fixed;
        if (item instanceof SpawnEggItem || path.contains("spawn_egg")) return 0;
        // tools and armor: 80% of the value of their materials
        double mat = material(path), pcs = pieces(path);
        if (mat >= 0 && pcs > 0) {
            if (path.startsWith("netherite_")) return Math.min(MAX, 525 + 25 * pcs * 0.8);
            return Math.max(0.05, Math.round(mat * pcs * 0.8 * 100) / 100.0);
        }
        if (item instanceof ArmorItem) return 2;
        if (path.endsWith("_smithing_template")) return 25;
        if (path.endsWith("_pottery_sherd")) return 8;
        if (path.endsWith("_banner_pattern")) return 3;
        if (path.startsWith("music_disc_")) return 10;
        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae")) return 0.5;
        if (path.endsWith("_planks")) return 0.12;
        if (path.endsWith("_sapling") || path.endsWith("_propagule") || path.equals("azalea") || path.equals("flowering_azalea")) return 0.5;
        if (path.endsWith("_leaves")) return 0.02;
        if (path.endsWith("_wool") || path.endsWith("_carpet") || path.endsWith("_bed")) return 0.15;
        if (path.endsWith("_dye")) return 0.08;
        if (path.contains("tulip") || path.endsWith("_orchid") || path.endsWith("dandelion") || path.endsWith("poppy") || path.contains("flower")
                || path.endsWith("allium") || path.endsWith("bluet") || path.endsWith("daisy") || path.endsWith("cornflower") || path.contains("lily")
                || path.endsWith("rose_bush") || path.endsWith("peony") || path.endsWith("lilac") || path.endsWith("sunflower")) return 0.1;
        if (path.contains("copper")) return 0.9; // cut copper, oxidized copper blocks, etc.
        if (path.contains("deepslate") || path.contains("stone") || path.contains("brick") || path.contains("tuff") || path.contains("blackstone")
                || path.contains("sandstone") || path.contains("terracotta") || path.contains("concrete") || path.contains("glass")) return 0.03;
        if (path.endsWith("_slab") || path.endsWith("_stairs") || path.endsWith("_wall") || path.endsWith("_fence") || path.endsWith("_fence_gate")
                || path.endsWith("_door") || path.endsWith("_trapdoor") || path.endsWith("_button") || path.endsWith("_pressure_plate")
                || path.endsWith("_sign") || path.endsWith("_hanging_sign")) return 0.05;
        if (path.endsWith("_boat") || path.endsWith("_raft") || path.endsWith("_chest_boat") || path.endsWith("_chest_raft")) return 0.6;
        if (path.endsWith("_ore")) return 1;
        if (path.contains("potion")) return 1;
        if (path.endsWith("_candle") || path.equals("candle")) return 0.2;
        if (path.endsWith("_shulker_box")) return 80;
        if (path.endsWith("_coral") || path.endsWith("_coral_block") || path.endsWith("_coral_fan")) return 0.3;
        if (path.endsWith("_mushroom") || path.endsWith("_fungus") || path.endsWith("_roots")) return 0.05;
        return item.getDefaultMaxStackSize() == 1 ? 1 : 0.1;
    }

    private static Map<String, Double> defaults() {
        Map<String, Double> t = new TreeMap<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (!id.getNamespace().equals("minecraft")) continue;
            double v = guess(BuiltInRegistries.ITEM.get(id), id.getPath());
            t.put(id.getPath(), Math.max(0, Math.min(MAX, Math.round(v * 100) / 100.0)));
        }
        return t;
    }
}
