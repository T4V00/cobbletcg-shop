package dev.tcgshop;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Selftest step "loader_parity" (/tcgshop debug selftest): the same jar contents must end up registered and loaded the
 * same way on Fabric and NeoForge. Logs a census of every tcgshop entry in the vanilla built-in registries (registries a
 * loader adds itself, like NeoForge's attachment types, are listed apart), plus the
 * mod's recipes, advancements, loot tables and tags that were loaded from data, with one hash per group. Compare
 * the lines from both loaders' runserver.out: they must match. It also checks that the server mixins really were applied.
 * A headless server with no players may never load ServerPlayer, so a mixin that fails against NeoForge's patched
 * classes would otherwise go unnoticed until the first death.
 */
public final class LoaderParitySelfTest {
    private LoaderParitySelfTest() {}

    public static void run(ServerLevel world) {
        MinecraftServer server = world.getServer();
        // --- built-in registries: one count per registry that holds tcgshop entries, and a hash of every id
        TreeMap<String, Integer> counts = new TreeMap<>();
        List<String> ids = new ArrayList<>();
        TreeMap<String, Integer> loaderOnly = new TreeMap<>(); // registries the loader adds (e.g. neoforge:attachment_types): not compared
        for (Registry<?> reg : BuiltInRegistries.REGISTRY) {
            ResourceLocation regKey = reg.key().location();
            boolean vanilla = regKey.getNamespace().equals("minecraft");
            String regName = vanilla ? regKey.getPath() : regKey.toString();
            for (ResourceLocation id : reg.keySet()) {
                if (!id.getNamespace().equals(TcgShop.MOD_ID)) continue;
                if (!vanilla) { loaderOnly.merge(regName, 1, Integer::sum); continue; }
                counts.merge(regName, 1, Integer::sum);
                ids.add(regName + "/" + id.getPath());
            }
        }
        if (counts.getOrDefault("block", 0) == 0 || counts.getOrDefault("item", 0) == 0 || counts.getOrDefault("entity_type", 0) == 0
                || counts.getOrDefault("sound_event", 0) == 0)
            throw new IllegalStateException("missing registrations: " + counts);
        TcgShop.LOGGER.info("[selftest] loader_parity: registries {} hash {} (loader-only, not compared: {})", counts, hash(ids), loaderOnly);

        // --- data loaded from the datapack (recipes, advancements, loot tables, tags)
        List<String> recipes = new ArrayList<>();
        for (var h : server.getRecipeManager().getRecipes())
            if (h.id().getNamespace().equals(TcgShop.MOD_ID)) recipes.add(h.id().getPath());
        List<String> advancements = new ArrayList<>();
        for (var h : server.getAdvancements().getAllAdvancements())
            if (h.id().getNamespace().equals(TcgShop.MOD_ID)) advancements.add(h.id().getPath());
        List<String> loot = new ArrayList<>();
        for (ResourceLocation id : server.reloadableRegistries().getKeys(Registries.LOOT_TABLE))
            if (id.getNamespace().equals(TcgShop.MOD_ID)) loot.add(id.getPath());
        List<String> tags = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagNames().filter(t -> t.location().getNamespace().equals(TcgShop.MOD_ID)).forEach(t -> tags.add("item/" + t.location().getPath()));
        BuiltInRegistries.BLOCK.getTagNames().filter(t -> t.location().getNamespace().equals(TcgShop.MOD_ID)).forEach(t -> tags.add("block/" + t.location().getPath()));
        if (recipes.isEmpty() || advancements.isEmpty() || loot.isEmpty() || tags.isEmpty())
            throw new IllegalStateException("data not loaded: recipes " + recipes.size() + " advancements " + advancements.size()
                    + " loot tables " + loot.size() + " tags " + tags.size());
        TcgShop.LOGGER.info("[selftest] loader_parity: data recipes {} ({}) advancements {} ({}) loot tables {} ({}) tags {} ({})",
                recipes.size(), hash(recipes), advancements.size(), hash(advancements), loot.size(), hash(loot), tags.size(), hash(tags));

        // --- server mixins applied (handler methods are merged into the target class, named after the mixin method)
        StringBuilder missing = new StringBuilder();
        requireMerged(net.minecraft.server.level.ServerPlayer.class, "keepBinders", missing);
        requireMerged(net.minecraft.server.PlayerAdvancements.class, "announce", missing);
        if (!dev.tcgshop.mixin.MobEntityAccessor.class.isAssignableFrom(net.minecraft.world.entity.Mob.class)) missing.append(" MobEntityAccessor");
        if (!missing.isEmpty()) throw new IllegalStateException("mixins not applied:" + missing);
        TcgShop.LOGGER.info("[selftest] loader_parity: server mixins applied (ServerPlayer.die, PlayerAdvancements.award, Mob accessor)");
    }

    private static void requireMerged(Class<?> target, String handlerSuffix, StringBuilder missing) {
        for (Method m : target.getDeclaredMethods())
            if (m.getName().endsWith("tcgshop$" + handlerSuffix)) return;
        missing.append(' ').append(target.getSimpleName()).append('.').append(handlerSuffix);
    }

    /** Order-independent short hash of a list of ids. */
    private static String hash(List<String> ids) {
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(null);
        return String.format("%08x", String.join(",", sorted).hashCode());
    }
}
