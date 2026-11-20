package dev.tcgshop.advancement;

import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.network.FactPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Datos curiosos al alcanzar ciertos números de cartas distintas, al estilo de los mensajes de TCG Pocket
 * ("el número 25 coincide con Pikachu"), con muchos más datos y algunos hitos especiales.
 *
 * Para no molestar: sólo números concretos, cada uno una vez, y como máximo UN mensaje por apertura de
 * binder (el hito más alto alcanzado desde la última vez).
 */
public final class FunFacts {
    /** Cada número tiene sus textos en el lang: tcgshop.fact.<n> */
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
        // marcar también los hitos intermedios saltados, para no mostrarlos después
        for (int m : MILESTONES) if (m <= best) c.facts.add(m);

        Text title = Text.translatable("tcgshop.fact.title", best);
        Text body = Text.translatable("tcgshop.fact." + best);
        if (ServerPlayNetworking.canSend(player, FactPayload.ID)) ServerPlayNetworking.send(player, new FactPayload(title, body));
        player.sendMessage(Text.literal("✦ ").formatted(Formatting.GOLD).append(body.copy().formatted(Formatting.GRAY, Formatting.ITALIC)), false);
    }
}
