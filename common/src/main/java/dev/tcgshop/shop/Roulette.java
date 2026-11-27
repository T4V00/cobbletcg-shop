package dev.tcgshop.shop;

import dev.masterball.api.platform.Net;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.RouletteOpenPayload;
import dev.tcgshop.network.RouletteResultPayload;
import dev.tcgshop.network.RouletteSpinPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.Set;

/**
 * Poké Roulette: 37-number European wheel (green 0). Bet on red or black; if you win you get double.
 * The green 0 gives the house edge (2.7%), like a real roulette.
 */
public final class Roulette {
    public static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    /** Order of the numbers on a European wheel (for the animation). */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14,
            31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};

    private Roulette() {}

    public static void open(ServerPlayer player, BlockPos pos) {
        if (Net.INSTANCE.canSend(player, RouletteOpenPayload.ID))
            Net.INSTANCE.send(player, new RouletteOpenPayload(pos, CobbleDollarsBridge.getBalance(player)));
    }

    public static void spin(RouletteSpinPayload p, ServerPlayer player) {
        if (player.distanceToSqr(p.pos().getCenter()) > 36) return;
        if (!(player.level().getBlockState(p.pos()).getBlock() instanceof DecorBlocks.RouletteBlock)) return;
        long amount = p.amount();
        if (amount <= 0 || !CobbleDollarsBridge.tryCharge(player, amount)) return;
        int result = player.getRandom().nextInt(37);
        boolean win = result != 0 && RED.contains(result) == p.red();
        long payout = win ? amount * 2 : 0;
        if (win) {
            CobbleDollarsBridge.add(player, payout);
            CardAdvancements.grant(player, "roulette_win");
            if (amount >= 100_000) CardAdvancements.grant(player, "roulette_high_roller");
        }
        if (player.level().getBlockEntity(p.pos()) instanceof DecorBlocks.DecorEntity be) be.animate(result);
        player.level().playSound(null, p.pos(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 1f, 1.5f);
        if (Net.INSTANCE.canSend(player, RouletteResultPayload.ID))
            Net.INSTANCE.send(player, new RouletteResultPayload(result, win, payout, CobbleDollarsBridge.getBalance(player)));
    }
}
