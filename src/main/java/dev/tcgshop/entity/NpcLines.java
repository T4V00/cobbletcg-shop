package dev.tcgshop.entity;

import dev.tcgshop.network.NpcSpeechPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

import java.util.Map;

/**
 * Frases de los NPC (claves tcgshop.npc.<categoría>.<n> en los archivos de idioma). El número de frases de cada
 * categoría debe coincidir con tools/gen_npc_lines.py.
 */
public final class NpcLines {
    public static final Map<String, Integer> COUNTS = Map.ofEntries(
            Map.entry("greet", 20), Map.entry("buy", 20), Map.entry("nobuy", 15), Map.entry("expensive", 15), Map.entry("picky", 12),
            Map.entry("whale", 12), Map.entry("whale_talk", 10), Map.entry("thief_talk", 8), Map.entry("thief_caught", 10),
            Map.entry("thief_escape", 8), Map.entry("haggle", 10), Map.entry("haggle_offer", 15), Map.entry("seller_hello", 12),
            Map.entry("deal_accepted", 12), Map.entry("deal_rejected", 12), Map.entry("no_cashier", 8), Map.entry("nothing", 8),
            Map.entry("deal_timeout", 6), Map.entry("cashier", 15), Map.entry("guard", 15), Map.entry("reason", 224),
            Map.entry("trader_hello", 10), Map.entry("regular_back", 10), Map.entry("leave_happy", 10), Map.entry("leave_sad", 8),
            Map.entry("ouch", 8), Map.entry("thief_battle", 6), Map.entry("checkout", 8),
            Map.entry("smell_complain", 8), Map.entry("spray_thanks", 6),
            // regateo con comerciantes (por personalidad) y reacciones
            Map.entry("neg_accept_collector", 3), Map.entry("neg_accept_beginner", 3), Map.entry("neg_accept_expert", 3),
            Map.entry("neg_accept_negotiator", 3), Map.entry("neg_accept_desperate", 3), Map.entry("neg_accept_casual", 3),
            Map.entry("neg_counter_collector", 3), Map.entry("neg_counter_beginner", 3), Map.entry("neg_counter_expert", 3),
            Map.entry("neg_counter_negotiator", 3), Map.entry("neg_counter_desperate", 3), Map.entry("neg_counter_casual", 3),
            Map.entry("neg_hold_collector", 2), Map.entry("neg_hold_beginner", 2), Map.entry("neg_hold_expert", 2),
            Map.entry("neg_hold_negotiator", 2), Map.entry("neg_hold_desperate", 2), Map.entry("neg_hold_casual", 2),
            Map.entry("neg_insulted_collector", 3), Map.entry("neg_insulted_beginner", 3), Map.entry("neg_insulted_expert", 3),
            Map.entry("neg_insulted_negotiator", 3), Map.entry("neg_insulted_desperate", 3), Map.entry("neg_insulted_casual", 3),
            Map.entry("neg_walkaway_collector", 3), Map.entry("neg_walkaway_beginner", 3), Map.entry("neg_walkaway_expert", 3),
            Map.entry("neg_walkaway_negotiator", 3), Map.entry("neg_walkaway_desperate", 3), Map.entry("neg_walkaway_casual", 3),
            Map.entry("neg_think_lower", 5), Map.entry("neg_think_same", 5), Map.entry("neg_final_no", 5),
            Map.entry("neg_reject_ok", 6), Map.entry("neg_reject_bad", 6), Map.entry("neg_rude", 6), Map.entry("neg_no_money", 3),
            Map.entry("neg_arg_market_good", 3), Map.entry("neg_arg_market_bad", 3), Map.entry("neg_arg_elsewhere_good", 3),
            Map.entry("neg_arg_elsewhere_bad", 3), Map.entry("neg_arg_broke_good", 3), Map.entry("neg_arg_broke_bad", 3),
            Map.entry("seller_back", 5),
            // comentarios sobre el entorno de la tienda
            Map.entry("comment_no_roof", 4), Map.entry("comment_no_walls", 4), Map.entry("comment_dark", 4), Map.entry("comment_bright", 4),
            Map.entry("comment_hostile", 5), Map.entry("comment_farm_animals", 4), Map.entry("comment_pets", 4), Map.entry("comment_pokemon", 5),
            Map.entry("comment_cozy", 5), Map.entry("comment_cheap", 4), Map.entry("comment_pricey", 4), Map.entry("comment_empty", 4),
            Map.entry("comment_infamy", 5), Map.entry("murder_witness", 6),
            Map.entry("cashier_bye", 5), Map.entry("beg_hello", 8), Map.entry("beg_ambient", 8), Map.entry("beg_thanks", 8),
            Map.entry("beg_thanks_big", 5), Map.entry("beg_card_love", 5), Map.entry("beg_polite_no", 5), Map.entry("beg_rude", 5),
            Map.entry("beg_gift", 4), Map.entry("bm_hello", 6), Map.entry("bm_ambient", 5), Map.entry("bm_deal", 5), Map.entry("bm_no", 4),
            Map.entry("bm_leave", 4));

    private NpcLines() {}

    public static int count(String cat) {
        return COUNTS.getOrDefault(cat, 1);
    }

    public static Text random(Random r, String cat) {
        return Text.translatable("tcgshop.npc." + cat + "." + r.nextInt(count(cat)));
    }

    /** Igual, con argumentos (%s) en la frase. */
    public static net.minecraft.text.MutableText random(Random r, String cat, Object... args) {
        return Text.translatable("tcgshop.npc." + cat + "." + r.nextInt(count(cat)), args);
    }

    /**
     * El NPC dice algo: bocadillo sobre su cabeza (con sonidos de "hablar") para los jugadores que lo ven.
     * Los jugadores sin el add-on en el cliente lo reciben por el chat.
     */
    public static void speak(LivingEntity who, Text line) {
        if (who.getWorld().isClient) return;
        NpcSpeechPayload payload = new NpcSpeechPayload(who.getId(), line);
        for (ServerPlayerEntity p : PlayerLookup.tracking(who)) {
            if (p.squaredDistanceTo(who) > 40 * 40) continue;
            if (ServerPlayNetworking.canSend(p, NpcSpeechPayload.ID)) ServerPlayNetworking.send(p, payload);
            else p.sendMessage(Text.literal("<").append(who.getDisplayName().copy().formatted(Formatting.YELLOW)).append("> ")
                    .append(line.copy().formatted(Formatting.WHITE)), false);
        }
    }

    /** Frase al hablar con un jugador (se ve en el bocadillo). */
    public static void say(LivingEntity who, ServerPlayerEntity to, String cat) {
        speak(who, random(who.getRandom(), cat));
    }

    /** Frase al aire (bocadillo). */
    public static void sayNear(LivingEntity who, String cat) {
        speak(who, random(who.getRandom(), cat));
    }
}
