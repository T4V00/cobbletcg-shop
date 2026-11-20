package dev.tcgshop.shop;

import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.RouletteOpenPayload;
import dev.tcgshop.network.RouletteResultPayload;
import dev.tcgshop.network.RouletteSpinPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/**
 * Ruleta Poké: rueda europea de 37 números (0 verde). Apuestas a rojo o negro; si aciertas cobras el doble.
 * El 0 verde da la ventaja de la casa (2,7%), como en una ruleta real.
 */
public final class Roulette {
    public static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    /** Orden de los números en una rueda europea (para la animación). */
    public static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14,
            31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};

    private Roulette() {}

    public static void open(ServerPlayerEntity player, BlockPos pos) {
        if (ServerPlayNetworking.canSend(player, RouletteOpenPayload.ID))
            ServerPlayNetworking.send(player, new RouletteOpenPayload(pos, CobbleDollarsBridge.getBalance(player)));
    }

    public static void spin(RouletteSpinPayload p, ServerPlayerEntity player) {
        if (player.squaredDistanceTo(p.pos().toCenterPos()) > 36) return;
        if (!(player.getWorld().getBlockState(p.pos()).getBlock() instanceof DecorBlocks.RouletteBlock)) return;
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
        if (player.getWorld().getBlockEntity(p.pos()) instanceof DecorBlocks.DecorEntity be) be.animate(result);
        player.getWorld().playSound(null, p.pos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 1f, 1.5f);
        if (ServerPlayNetworking.canSend(player, RouletteResultPayload.ID))
            ServerPlayNetworking.send(player, new RouletteResultPayload(result, win, payout, CobbleDollarsBridge.getBalance(player)));
    }
}
