package dev.tcgshop.entity;

import dev.masterball.api.platform.Net;
import dev.tcgshop.network.NpcSpeechPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;

/**
 * NPC lines (keys tcgshop.npc.<category>.<n> in the language files). The number of lines in each
 * category must match tools/gen_npc_lines.py.
 */
public final class NpcLines {
    public static final Map<String, Integer> COUNTS = Map.ofEntries(
            Map.entry("greet", 110), Map.entry("buy", 110), Map.entry("nobuy", 89), Map.entry("expensive", 90),
            Map.entry("picky", 62), Map.entry("whale", 52), Map.entry("whale_talk", 68), Map.entry("thief_talk", 28),
            Map.entry("thief_caught", 30), Map.entry("thief_escape", 23), Map.entry("haggle", 65), Map.entry("haggle_offer", 80),
            Map.entry("seller_hello", 52), Map.entry("deal_accepted", 67), Map.entry("deal_rejected", 57), Map.entry("no_cashier", 23),
            Map.entry("nothing", 38), Map.entry("deal_timeout", 21), Map.entry("cashier", 35), Map.entry("guard", 35),
            Map.entry("reason", 224), Map.entry("trader_hello", 32), Map.entry("regular_back", 71), Map.entry("leave_happy", 65),
            Map.entry("leave_sad", 58), Map.entry("ouch", 23), Map.entry("thief_battle", 21), Map.entry("checkout", 58),
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
            Map.entry("neg_arg_broke_bad", 3), Map.entry("seller_back", 20), Map.entry("comment_no_roof", 10), Map.entry("comment_no_walls", 10),
            Map.entry("comment_dark", 20), Map.entry("comment_bright", 20), Map.entry("comment_hostile", 5), Map.entry("comment_farm_animals", 4),
            Map.entry("comment_pets", 4), Map.entry("comment_pokemon", 15), Map.entry("comment_cozy", 36), Map.entry("comment_cheap", 19),
            Map.entry("comment_pricey", 25), Map.entry("comment_empty", 22), Map.entry("comment_infamy", 5), Map.entry("murder_witness", 6),
            Map.entry("cashier_bye", 25), Map.entry("queue_leave", 45), Map.entry("wait_register", 46), Map.entry("beg_hello", 20),
            Map.entry("beg_ambient", 20), Map.entry("beg_thanks", 18), Map.entry("beg_thanks_big", 5), Map.entry("beg_card_love", 5),
            Map.entry("beg_polite_no", 5), Map.entry("beg_rude", 5), Map.entry("beg_gift", 4), Map.entry("bm_hello", 16),
            Map.entry("bm_ambient", 15), Map.entry("bm_deal", 5), Map.entry("bm_no", 4), Map.entry("bm_leave", 4),
            Map.entry("furn_hello", 25), Map.entry("furn_ambient", 25), Map.entry("furn_buy", 20), Map.entry("furn_sell", 20),
            Map.entry("furn_no_money", 10), Map.entry("furn_bye", 15), Map.entry("overpay_happy", 58),
            // 1.0 tips (tools/gen_lang_10_deals.py; %s = the tip): offer above your price, accepted deal and checkout
            Map.entry("tip_offer", 24), Map.entry("tip_accept", 24), Map.entry("tip_checkout", 26),
            // 1.1 customers update (tools/npc_lines_v4_*.py via gen_lang_12_customers.py)
            Map.entry("owner_joke", 40), Map.entry("owner_compliment", 30), Map.entry("owner_story", 30), Map.entry("owner_smalltalk", 20), Map.entry("charm_smalltalk_good", 15),
            Map.entry("charm_smalltalk_bad", 15), Map.entry("charm_joke_good", 25),
            Map.entry("charm_joke_bad", 25), Map.entry("charm_compliment_good", 20), Map.entry("charm_compliment_bad", 20), Map.entry("charm_story_good", 20),
            Map.entry("charm_story_bad", 20), Map.entry("charm_enough", 10), Map.entry("greedy_leave", 30), Map.entry("statue_missing", 20),
            Map.entry("statue_love", 25), Map.entry("statue_more", 20), Map.entry("comment_decor_low", 20), Map.entry("comment_decor_high", 20),
            // 1.1 cleanliness update (tools/gen_lang_12_clean.py)
            Map.entry("dirty_complain", 16), Map.entry("poop_complain", 16), Map.entry("clean_praise", 12));

    private NpcLines() {}

    public static int count(String cat) {
        return COUNTS.getOrDefault(cat, 1);
    }

    public static Component random(RandomSource r, String cat) {
        return Component.translatable("tcgshop.npc." + cat + "." + r.nextInt(count(cat)));
    }

    /** Same, with arguments (%s) in the line. */
    public static net.minecraft.network.chat.MutableComponent random(RandomSource r, String cat, Object... args) {
        return Component.translatable("tcgshop.npc." + cat + "." + r.nextInt(count(cat)), args);
    }

    /**
     * The NPC says something: a speech bubble above its head (with "talking" sounds) for the players who see it.
     * Players without the add-on on the client receive it through chat.
     */
    public static void speak(LivingEntity who, Component line) {
        if (who.level().isClientSide) return;
        NpcSpeechPayload payload = new NpcSpeechPayload(who.getId(), line);
        for (ServerPlayer p : dev.masterball.api.platform.Platform.INSTANCE.trackingPlayers(who)) {
            if (p.distanceToSqr(who) > 40 * 40) continue;
            if (Net.INSTANCE.canSend(p, NpcSpeechPayload.ID)) Net.INSTANCE.send(p, payload);
            else p.displayClientMessage(Component.literal("<").append(who.getDisplayName().copy().withStyle(ChatFormatting.YELLOW)).append("> ")
                    .append(line.copy().withStyle(ChatFormatting.WHITE)), false);
        }
    }

    /** Line when talking to a player (shown in the speech bubble). */
    public static void say(LivingEntity who, ServerPlayer to, String cat) {
        speak(who, random(who.getRandom(), cat));
    }

    /** Line spoken to no one in particular (speech bubble). */
    public static void sayNear(LivingEntity who, String cat) {
        speak(who, random(who.getRandom(), cat));
    }
}
