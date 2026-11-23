package dev.tcgshop.advancement;

import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.network.FactPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Fun facts when reaching certain numbers of distinct cards, in the style of the TCG Pocket messages
 * ("number 25 matches Pikachu"), with many more facts and some special milestones.
 *
 * To avoid being annoying: only specific numbers, each one once, and at most ONE message per binder
 * opening (the highest milestone reached since last time).
 */
public final class FunFacts {
    /** Each number has its texts in the lang: tcgshop.fact.<n> */
    public static final int[] MILESTONES = {
            1, 4, 7, 10, 25, 39, 52, 60, 94, 100, 102, 108, 129, 133, 143, 150, 151, 197, 249, 250, 251,
            282, 384, 386, 448, 493, 500, 570, 649, 658, 700, 721, 778, 809, 888, 898, 905, 999, 1000,
            1025, 1500, 1996, 1999, 2500, 3000, 4000, 5000, 6000, 7000, 8000
    };

    private FunFacts() {}

    public static void check(ServerPlayerEntity player, CardCollection c, int oldUnique, int newUnique) {
        int best = -1;
        for (int m : MILESTONES) {
            if (m > oldUnique && m <= newUnique && !c.facts.contains(m)) best = Math.max(best, m);
        }
        if (best < 0) return;
        // also mark the skipped intermediate milestones, so they are not shown later
        for (int m : MILESTONES) if (m <= best) c.facts.add(m);

        Text title = Text.translatable("tcgshop.fact.title", best);
        Text body = Text.translatable("tcgshop.fact." + best);
        if (ServerPlayNetworking.canSend(player, FactPayload.ID)) ServerPlayNetworking.send(player, new FactPayload(title, body));
        player.sendMessage(Text.literal("✦ ").formatted(Formatting.GOLD).append(body.copy().formatted(Formatting.GRAY, Formatting.ITALIC)), false);
    }
}
