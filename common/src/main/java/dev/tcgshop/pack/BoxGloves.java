package dev.tcgshop.pack;

import dev.masterball.api.platform.Net;
import dev.tcgshop.TcgSounds;
import dev.tcgshop.item.BoxGlovesItem;
import dev.tcgshop.phone.PhonePayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 1.1 Box-Opening Gloves, server side. Right-clicking a booster box while wearing them sends the client a question
 * (PhonePayloads.Apps k = "box_ask"); the answer comes back as PhonePayloads.Act a = "box_open" with mode 0 (just the 36
 * packs, as without gloves), 1 (open this whole box) or 2 (open every box of the stack, up to {@link #MAX_BOXES}).
 * Opening is done by {@link PackOpener#openDirect}: every pack is rolled like a normal opening with a perfect cut and the
 * cards go to the binder (or the inventory); a chat summary lists the total and the best pulls.
 */
public final class BoxGloves {
    private BoxGloves() {}

    /** Most boxes opened by one "open them all". */
    public static final int MAX_BOXES = 16;

    /** Called from BoxItem.use on the server: true if the question was sent (the box is then left untouched). */
    public static boolean ask(ServerPlayer p, InteractionHand hand, ItemStack box) {
        if (!BoxGlovesItem.isWearing(p) || !Net.INSTANCE.canSend(p, PhonePayloads.Apps.ID)) return false;
        String set = box.getOrDefault(TcgBlocks.BOX_SET, "mew151");
        if (BoosterBox.sealedFor(set) == null) return false;
        CompoundTag d = new CompoundTag();
        d.putString("k", "box_ask");
        d.putString("set", set);
        d.putInt("hand", hand.ordinal());
        d.putInt("count", box.getCount());
        d.putInt("packs", BoosterBox.PACKS);
        d.putBoolean("binder", dev.tcgshop.collection.CollectionService.ownedBinderId(p) != null);
        Net.INSTANCE.send(p, new PhonePayloads.Apps(d));
        return true;
    }

    /** The client's answer (a = "box_open"). */
    public static void handle(ServerPlayer p, CompoundTag d) {
        InteractionHand hand = d.getInt("hand") == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack box = p.getItemInHand(hand);
        String set = d.getString("set");
        if (!box.is(TcgBlocks.BOOSTER_BOX_ITEM) || !set.equals(box.getOrDefault(TcgBlocks.BOX_SET, "mew151"))) return; // the hand changed meanwhile
        int mode = d.getInt("mode");
        if (mode == 0) { // no: the plain 36 packs, as without gloves
            BoosterBox.BoxItem.openToPacks(p, box);
            return;
        }
        if (!BoxGlovesItem.isWearing(p)) return;
        // review fix: a whole box (up to 16 = 576 packs, thousands of cards) is only opened straight into a General Binder;
        // a Personal Binder holds 25 and the rest would spill into the inventory and onto the ground in one tick
        if (dev.tcgshop.collection.CollectionService.ownedBinderId(p) == null) {
            p.displayClientMessage(Component.translatable("tcgshop.box_gloves.need_binder").withStyle(ChatFormatting.RED), false);
            return;
        }
        Item sealed = BoosterBox.sealedFor(set);
        if (sealed == null) return;
        int boxes = mode == 2 ? Math.min(MAX_BOXES, box.getCount()) : 1;
        box.shrink(boxes);
        PackOpener.DirectResult r = PackOpener.openDirect(p, set, boxes * BoosterBox.PACKS);
        p.level().playSound(null, p.blockPosition(), TcgSounds.BOX_OPEN_ALL, SoundSource.PLAYERS, 1f, 1f);
        p.sendSystemMessage(Component.translatable(r.toBinder() ? "tcgshop.box_gloves.done_binder" : "tcgshop.box_gloves.done_inv",
                boxes, r.packs(), r.cards(), Component.literal(PriceService.format(r.value())).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < r.best().size(); i++) {
            ItemStack c = r.best().get(i);
            p.sendSystemMessage(Component.literal("  " + (i + 1) + ". ").withStyle(ChatFormatting.GRAY).append(c.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + PriceService.format(r.bestPrices().get(i))).withStyle(ChatFormatting.GOLD)));
        }
        if (r.godPacks() > 0) p.sendSystemMessage(Component.translatable("tcgshop.box_gloves.god", r.godPacks()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
