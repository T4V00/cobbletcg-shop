package dev.tcgshop.shop;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

/**
 * 1.0 RGB displays: LED animation modes and the owner's controls (common code; the drawing is client.render.RgbLights).
 * Sneak + right-click the display with:
 *  · glowstone dust: next animation (chase -> wave -> breathe -> sparkle -> off -> chase);
 *  · any dye: solid color of that dye (gently pulsing). Neither item is used up.
 * The choice is stored per display (ShopDisplayBlockEntity "RgbMode"/"RgbColor") and synced with the block entity.
 */
public final class RgbLighting {
    /** Rainbow flowing along the LED strips. */
    public static final int CHASE = 0;
    /** The whole fixture fades through the rainbow at once. */
    public static final int WAVE = 1;
    /** Slow color drift with a breathing brightness. */
    public static final int BREATHE = 2;
    /** Random twinkling LEDs. */
    public static final int SPARKLE = 3;
    /** One color (set with a dye). */
    public static final int STATIC = 4;
    /** Lights off. */
    public static final int OFF = 5;

    /** Order of the glowstone cycle (STATIC is reached with a dye). */
    private static final int[] CYCLE = {CHASE, WAVE, BREATHE, SPARKLE, OFF};

    private RgbLighting() {}

    /**
     * Server and client: sneak + right-click on an RGB display with a dye or glowstone dust. A UseBlockCallback because
     * vanilla never calls the block's use handlers while the player sneaks holding an item. The client answers SUCCESS
     * (hand swing + the packet that makes the server run this too); the server applies it.
     */
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player.isSpectator() || !player.isSneaking()) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (!isTool(stack)) return ActionResult.PASS;
            BlockState state = world.getBlockState(hit.getBlockPos());
            if (!(state.getBlock() instanceof ShopDisplayBlock b) || !b.isRgb()) return ActionResult.PASS;
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) b.configureLights(sw, sp, state, hit.getBlockPos(), stack);
            return ActionResult.SUCCESS;
        });
    }

    public static boolean valid(int mode) {
        return mode >= CHASE && mode <= OFF;
    }

    /** Translation key of a mode's name. */
    public static String modeKey(int mode) {
        return "tcgshop.rgb.mode." + (valid(mode) ? mode : CHASE);
    }

    public static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof DyeItem || stack.isOf(Items.GLOWSTONE_DUST);
    }

    public static int next(int mode) {
        for (int i = 0; i < CYCLE.length; i++) if (CYCLE[i] == mode) return CYCLE[(i + 1) % CYCLE.length];
        return CHASE; // from STATIC (or anything else) back to the start of the cycle
    }

    /** Applies the held tool to the display (server). `allowed`: the player may change this display's lights. */
    public static void configure(ServerWorld world, ServerPlayerEntity player, ShopDisplayBlockEntity be, ItemStack stack, boolean allowed) {
        if (!allowed) {
            player.sendMessage(Text.translatable("tcgshop.rgb.not_yours").formatted(Formatting.RED), true);
            return;
        }
        if (stack.getItem() instanceof DyeItem dye) {
            be.setRgb(STATIC, dye.getColor().getFireworkColor());
            player.sendMessage(Text.translatable("tcgshop.rgb.color", stack.getName().copy().formatted(Formatting.WHITE)).formatted(Formatting.AQUA), true);
            world.playSound(null, be.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.8f, 1.4f);
            return;
        }
        int mode = next(be.rgbMode());
        be.setRgb(mode, be.rgbColor());
        player.sendMessage(Text.translatable("tcgshop.rgb.mode", Text.translatable(modeKey(mode)).formatted(Formatting.WHITE)).formatted(Formatting.AQUA), true);
        world.playSound(null, be.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.8f, mode == OFF ? 0.7f : 1.1f + mode * 0.1f);
    }
}
