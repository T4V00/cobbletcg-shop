package dev.tcgshop.collection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tcgshop.TcgShop;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.network.TitlesPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Títulos por Pokémon según cuántas cartas suyas has sacado de sobres:
 * Fan (5) · Entusiasta (15) · Obsesionado (40) · Totalmente demente (100).
 * El título elegido se muestra sobre tu nombre para todos los jugadores.
 */
public final class Titles {
    public enum Tier {
        FAN("fan", 5), ENTHUSIAST("enthusiast", 15), OBSESSED("obsessed", 40), DEMENTED("demented", 100);
        public final String key;
        public final int needed;
        Tier(String key, int needed) { this.key = key; this.needed = needed; }
    }

    /** Pokémon con logros propios por cada nivel. */
    public static final Set<String> FEATURED = Set.of("pikachu", "charizard", "eevee", "mewtwo", "mew", "gengar", "umbreon",
            "rayquaza", "lucario", "greninja", "snorlax", "gardevoir");

    private static Map<String, String> cardSpecies = new HashMap<>();

    private Titles() {}

    /** Todas las especies que tienen cartas (para los títulos aleatorios de los NPC). */
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

    public static Text display(String titleKey) {
        if (titleKey == null || titleKey.isEmpty() || !titleKey.contains("|")) return Text.empty();
        String[] p = titleKey.split("\\|", 2);
        return Text.translatable("tcgshop.title." + p[1], Text.translatable("cobblemon.species." + p[0] + ".name"));
    }

    /** Suma las cartas sacadas de cada Pokémon y otorga los logros de nivel alcanzados. */
    public static void creditPulls(ServerPlayerEntity player, CardCollection c, List<ItemStack> cards) {
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
                player.sendMessage(Text.translatable("tcgshop.title.unlocked", display(sp + "|" + t.key).copy()
                        .formatted(net.minecraft.util.Formatting.GOLD)), false);
            }
        }
    }

    /** Títulos que el jugador puede elegir ("especie|tier"), del más alto al más bajo. */
    public static List<String> available(CardCollection c) {
        List<String> out = new ArrayList<>();
        c.species.entrySet().stream()
                .filter(e -> tierFor(e.getValue()) != null)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> out.add(e.getKey() + "|" + tierFor(e.getValue()).key));
        return out;
    }

    public static void select(ServerPlayerEntity player, String key) {
        CardCollection c = CollectionService.playerData(player);
        if (!key.isEmpty() && !available(c).contains(key)) return;
        c.title = key;
        broadcast(player.getServer());
    }

    /** Envía a todos los títulos de todos los jugadores conectados. */
    public static void broadcast(MinecraftServer server) {
        Map<String, String> map = new HashMap<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            String t = CollectionService.playerData(p).title;
            if (t != null && !t.isEmpty()) map.put(p.getUuidAsString(), t);
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(p, TitlesPayload.ID)) ServerPlayNetworking.send(p, new TitlesPayload(map));
        }
    }

    public static class ReloadListener implements SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() { return Identifier.of(TcgShop.MOD_ID, "species"); }

        @Override
        public void reload(ResourceManager manager) {
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<Identifier, Resource> e : manager.findResources("tcgshop_species", p -> p.getPath().endsWith(".json")).entrySet()) {
                try (Reader r = e.getValue().getReader()) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> kv : o.entrySet()) map.put(kv.getKey(), kv.getValue().getAsString());
                } catch (Exception ex) {
                    TcgShop.LOGGER.error("Especies inválidas: {}", e.getKey(), ex);
                }
            }
            cardSpecies = map;
        }
    }
}
