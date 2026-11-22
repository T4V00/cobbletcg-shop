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
            Map.entry("greet", 50), Map.entry("buy", 50), Map.entry("nobuy", 40), Map.entry("expensive", 40),
            Map.entry("picky", 32), Map.entry("whale", 32), Map.entry("whale_talk", 38), Map.entry("thief_talk", 28),
            Map.entry("thief_caught", 30), Map.entry("thief_escape", 23), Map.entry("haggle", 35), Map.entry("haggle_offer", 40),
            Map.entry("seller_hello", 32), Map.entry("deal_accepted", 37), Map.entry("deal_rejected", 37), Map.entry("no_cashier", 23),
            Map.entry("nothing", 23), Map.entry("deal_timeout", 21), Map.entry("cashier", 35), Map.entry("guard", 35),
            Map.entry("reason", 224), Map.entry("trader_hello", 22), Map.entry("regular_back", 41), Map.entry("leave_happy", 35),
            Map.entry("leave_sad", 28), Map.entry("ouch", 23), Map.entry("thief_battle", 21), Map.entry("checkout", 28),
            Map.entry("smell_complain", 20), Map.entry("spray_thanks", 18), Map.entry("neg_accept_collector", 3), Map.entry("neg_accept_beginner", 3),
            Map.entry("neg_accept_expert", 3), Map.entry("neg_accept_negotiator", 3), Map.entry("neg_accept_desperate", 3), Map.entry("neg_accept_casual", 3),
            Map.entry("neg_counter_collector", 3), Map.entry("neg_counter_beginner", 3), Map.entry("neg_counter_expert", 3), Map.entry("neg_counter_negotiator", 3),
            Map.entry("neg_counter_desperate", 3), Map.entry("neg_counter_casual", 3), Map.entry("neg_hold_collector", 2), Map.entry("neg_hold_beginner", 2),
            Map.entry("neg_hold_expert", 2), Map.entry("neg_hold_negotiator", 2), Map.entry("neg_hold_desperate", 2), Map.entry("neg_hold_casual", 2),
            Map.entry("neg_insulted_collector", 3), Map.entry("neg_insulted_beginner", 3), Map.entry("neg_insulted_expert", 3), Map.entry("neg_insulted_negotiator", 3),
            Map.entry("neg_insulted_desperate", 3), Map.entry("neg_insulted_casual", 3), Map.entry("neg_walkaway_collector", 3), Map.entry("neg_walkaway_beginner", 3),
            Map.entry("neg_walkaway_expert", 3), Map.entry("neg_walkaway_negotiator", 3), Map.entry("neg_walkaway_desperate", 3), Map.entry("neg_walkaway_casual", 3),
            Map.entry("neg_think_lower", 20), Map.entry("neg_think_same", 20), Map.entry("neg_final_no", 20), Map.entry("neg_reject_ok", 6),
            Map.entry("neg_reject_bad", 6), Map.entry("neg_rude", 21), Map.entry("neg_no_money", 3), Map.entry("neg_arg_market_good", 3),
            Map.entry("neg_arg_market_bad", 3), Map.entry("neg_arg_elsewhere_good", 3), Map.entry("neg_arg_elsewhere_bad", 3), Map.entry("neg_arg_broke_good", 3),
            Map.entry("neg_arg_broke_bad", 3), Map.entry("seller_back", 20), Map.entry("comment_no_roof", 4), Map.entry("comment_no_walls", 4),
            Map.entry("comment_dark", 12), Map.entry("comment_bright", 12), Map.entry("comment_hostile", 5), Map.entry("comment_farm_animals", 4),
            Map.entry("comment_pets", 4), Map.entry("comment_pokemon", 15), Map.entry("comment_cozy", 21), Map.entry("comment_cheap", 10),
            Map.entry("comment_pricey", 10), Map.entry("comment_empty", 12), Map.entry("comment_infamy", 5), Map.entry("murder_witness", 6),
            Map.entry("cashier_bye", 25), Map.entry("queue_leave", 25), Map.entry("wait_register", 26), Map.entry("beg_hello", 20),
            Map.entry("beg_ambient", 20), Map.entry("beg_thanks", 18), Map.entry("beg_thanks_big", 5), Map.entry("beg_card_love", 5),
            Map.entry("beg_polite_no", 5), Map.entry("beg_rude", 5), Map.entry("beg_gift", 4), Map.entry("bm_hello", 16),
            Map.entry("bm_ambient", 15), Map.entry("bm_deal", 5), Map.entry("bm_no", 4), Map.entry("bm_leave", 4),
            Map.entry("furn_hello", 25), Map.entry("furn_ambient", 25), Map.entry("furn_buy", 20), Map.entry("furn_sell", 20),
            Map.entry("furn_no_money", 10), Map.entry("furn_bye", 15), Map.entry("overpay_happy", 38));

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
