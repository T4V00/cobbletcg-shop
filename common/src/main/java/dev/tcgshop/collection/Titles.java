package dev.tcgshop.collection;

import dev.masterball.api.platform.Net;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tcgshop.TcgShop;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.network.TitlesPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-Pokémon titles based on how many of its cards you have pulled from packs:
 * Fan (5) · Enthusiast (15) · Obsessed (40) · Totally Demented (100).
 * The chosen title is shown above your name to all players.
 */
public final class Titles {
    public enum Tier {
        FAN("fan", 5), ENTHUSIAST("enthusiast", 15), OBSESSED("obsessed", 40), DEMENTED("demented", 100);
        public final String key;
        public final int needed;
        Tier(String key, int needed) { this.key = key; this.needed = needed; }
    }

    /** Pokémon with their own advancements for each tier. */
    public static final Set<String> FEATURED = Set.of("pikachu", "charizard", "eevee", "mewtwo", "mew", "gengar", "umbreon",
            "rayquaza", "lucario", "greninja", "snorlax", "gardevoir");

    private static Map<String, String> cardSpecies = new HashMap<>();

    private Titles() {}

    /** All species that have cards (for the NPCs' random titles). */
    public static java.util.Collection<String> allSpecies() {
        return new java.util.HashSet<>(cardSpecies.values());
    }

    public static String speciesOf(ItemStack card) {
        return CardInfo.isCard(card) ? cardSpecies.get(CardInfo.id(card).toString()) : null;
    }

    public static Tier tierFor(int count) {
        Tier best = null;
        for (Tier t : Tier.values()) if (count >= t.needed) best = t;
        return best;
    }

    public static Component display(String titleKey) {
        if (titleKey == null || titleKey.isEmpty() || !titleKey.contains("|")) return Component.empty();
        String[] p = titleKey.split("\\|", 2);
        return Component.translatable("tcgshop.title." + p[1], Component.translatable("cobblemon.species." + p[0] + ".name"));
    }

    /** Adds up the cards pulled for each Pokémon and grants the tier advancements reached. */
    public static void creditPulls(ServerPlayer player, CardCollection c, List<ItemStack> cards) {
        for (ItemStack st : cards) {
            String sp = speciesOf(st);
            if (sp == null) continue;
            int n = c.species.merge(sp, 1, Integer::sum);
            Tier t = tierFor(n);
            if (t == null) continue;
            for (Tier reached : Tier.values()) {
                if (reached.ordinal() > t.ordinal()) break;
                CardAdvancements.grant(player, "title_" + reached.key);
                if (FEATURED.contains(sp)) CardAdvancements.grant(player, "species_" + sp + "_" + reached.key);
            }
            if (n == t.needed) {
                player.displayClientMessage(Component.translatable("tcgshop.title.unlocked", display(sp + "|" + t.key).copy()
                        .withStyle(net.minecraft.ChatFormatting.GOLD)), false);
            }
        }
    }

    /** Titles the player can choose ("species|tier"), from highest to lowest. */
    public static List<String> available(CardCollection c) {
        List<String> out = new ArrayList<>();
        c.species.entrySet().stream()
                .filter(e -> tierFor(e.getValue()) != null)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> out.add(e.getKey() + "|" + tierFor(e.getValue()).key));
        return out;
    }

    public static void select(ServerPlayer player, String key) {
        CardCollection c = CollectionService.playerData(player);
        if (!key.isEmpty() && !available(c).contains(key)) return;
        c.title = key;
        broadcast(player.getServer());
    }

    /** Sends everyone the titles of all online players. */
    public static void broadcast(MinecraftServer server) {
        Map<String, String> map = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String t = CollectionService.playerData(p).title;
            if (t != null && !t.isEmpty()) map.put(p.getStringUUID(), t);
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Net.INSTANCE.canSend(p, TitlesPayload.ID)) Net.INSTANCE.send(p, new TitlesPayload(map));
        }
    }

    public static class ReloadListener implements ResourceManagerReloadListener {
        public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "species");

        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("tcgshop_species", p -> p.getPath().endsWith(".json")).entrySet()) {
                try (Reader r = e.getValue().openAsReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> kv : o.entrySet()) map.put(kv.getKey(), kv.getValue().getAsString());
                } catch (Exception ex) {
                    TcgShop.LOGGER.error("Invalid species: {}", e.getKey(), ex);
                }
            }
            cardSpecies = map;
        }
    }
}
