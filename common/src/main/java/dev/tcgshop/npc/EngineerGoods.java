package dev.tcgshop.npc;

import dev.masterball.api.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.1: what the CREATive Engineer sells. Items are looked up by id at runtime (no compile-time dependency on Create):
 * an id that is not registered is skipped, so with an older/newer Create the catalog just shrinks. Basic materials come
 * as one stack of 64 for about $3,000; machines and tools are $50,000 and up. Nothing very advanced (no mechanical
 * arms, crafters, schematicannons, blaze burners, steam engines, trains or creative items).
 */
public final class EngineerGoods {
    private EngineerGoods() {}

    public static final String NS = "create";
    /** Basic materials come in stacks of this size. */
    public static final int STACK = 64;

    /** A product: the stack he hands over and its price. */
    public record Offer(ItemStack stack, long price) {}

    public static boolean createInstalled() {
        return Platform.INSTANCE.isModLoaded(NS) && BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(NS, "andesite_alloy"));
    }

    public static Map<String, Long> defaultBasic() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("create:andesite_alloy", 2_500L);
        m.put("create:andesite_alloy_block", 3_500L);
        m.put("create:shaft", 2_500L);
        m.put("create:cogwheel", 3_000L);
        m.put("create:large_cogwheel", 3_500L);
        m.put("create:andesite_casing", 3_500L);
        m.put("create:iron_sheet", 3_000L);
        m.put("create:copper_sheet", 3_000L);
        m.put("create:zinc_ingot", 3_000L);
        m.put("create:fluid_pipe", 3_000L);
        m.put("create:metal_girder", 3_000L);
        m.put("create:industrial_iron_block", 3_000L);
        return m;
    }

    public static Map<String, Long> defaultMachines() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("create:hand_crank", 51_000L);
        m.put("create:cuckoo_clock", 51_000L);
        m.put("create:clipboard", 51_000L);
        m.put("create:basin", 52_000L);
        m.put("create:depot", 52_000L);
        m.put("create:chute", 52_000L);
        m.put("create:nixie_tube", 52_000L);
        m.put("create:gearbox", 54_000L);
        m.put("create:vertical_gearbox", 54_000L);
        m.put("create:turntable", 54_000L);
        m.put("create:goggles", 55_000L);
        m.put("create:millstone", 55_000L);
        m.put("create:mechanical_harvester", 55_000L);
        m.put("create:mechanical_plough", 55_000L);
        m.put("create:linear_chassis", 55_000L);
        m.put("create:speedometer", 55_000L);
        m.put("create:super_glue", 55_000L);
        m.put("create:andesite_tunnel", 55_000L);
        m.put("create:clutch", 56_000L);
        m.put("create:gearshift", 56_000L);
        m.put("create:encased_chain_drive", 58_000L);
        m.put("create:radial_chassis", 58_000L);
        m.put("create:display_board", 58_000L);
        m.put("create:wrench", 60_000L);
        m.put("create:encased_fan", 60_000L);
        m.put("create:water_wheel", 60_000L);
        m.put("create:fluid_tank", 60_000L);
        m.put("create:sticker", 60_000L);
        m.put("create:brass_funnel", 60_000L);
        m.put("create:item_drain", 60_000L);
        m.put("create:mechanical_pump", 62_000L);
        m.put("create:mechanical_bearing", 65_000L);
        m.put("create:item_vault", 65_000L);
        m.put("create:stressometer", 65_000L);
        m.put("create:brass_tunnel", 65_000L);
        m.put("create:mechanical_saw", 70_000L);
        m.put("create:mechanical_drill", 70_000L);
        m.put("create:cart_assembler", 70_000L);
        m.put("create:gantry_carriage", 70_000L);
        m.put("create:rope_pulley", 72_000L);
        m.put("create:mechanical_press", 75_000L);
        m.put("create:mechanical_piston", 75_000L);
        m.put("create:smart_chute", 75_000L);
        m.put("create:windmill_bearing", 80_000L);
        m.put("create:weighted_ejector", 80_000L);
        m.put("create:mechanical_mixer", 85_000L);
        m.put("create:spout", 90_000L);
        m.put("create:large_water_wheel", 95_000L);
        m.put("create:deployer", 120_000L);
        m.put("create:crushing_wheel", 150_000L);
        return m;
    }

    /** Ids the Engineer never sells, even if an admin adds them (too advanced for a wandering salesman). */
    public static final java.util.Set<String> NEVER = java.util.Set.of(
            "create:mechanical_arm", "create:mechanical_crafter", "create:schematicannon", "create:blaze_burner", "create:empty_blaze_burner",
            "create:steam_engine", "create:rotation_speed_controller", "create:sequenced_gearshift", "create:track_station", "create:controls",
            "create:creative_motor", "create:creative_crate", "create:creative_fluid_tank", "create:creative_blaze_cake", "create:netherite_backtank",
            "create:precision_mechanism", "create:wand_of_symmetry", "create:potato_cannon", "create:extendo_grip", "create:elevator_pulley");

    /** The catalog in order (basic materials first), with prices after engineerPriceMultiplier. Empty without Create. */
    public static List<Offer> catalog() {
        List<Offer> out = new ArrayList<>();
        if (!createInstalled()) return out;
        NpcConfig c = NpcConfig.INSTANCE;
        add(out, c.engineerBasic, STACK, c.engineerPriceMultiplier);
        add(out, c.engineerMachines, 1, c.engineerPriceMultiplier);
        return out;
    }

    private static void add(List<Offer> out, Map<String, Long> prices, int count, double mult) {
        for (Map.Entry<String, Long> e : prices.entrySet()) {
            Item it = item(e.getKey());
            if (it == null || e.getValue() == null || e.getValue() <= 0 || NEVER.contains(e.getKey())) continue;
            int n = Math.max(1, Math.min(count, new ItemStack(it).getMaxStackSize()));
            out.add(new Offer(new ItemStack(it, n), Math.max(1, Math.round(e.getValue() * mult))));
        }
    }

    /** Registered item for that id, or null (unknown id, or one that resolves to air). */
    @org.jetbrains.annotations.Nullable
    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        Item it = BuiltInRegistries.ITEM.get(rl);
        return it == Items.AIR ? null : it;
    }
}
