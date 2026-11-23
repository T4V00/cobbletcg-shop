package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client: opening animation of something opened from the hand (the contents are already in the inventory).
 * <ul>
 *   <li>Booster box: {@code set} + {@code packs}, {@code card} empty.</li>
 *   <li>Mystery Box (1.0): {@code card} = the card obtained, {@code cost} = what the box is worth, {@code jackpot} =
 *       the 1-in-10,000 Grade 10 prize. {@code set} is empty and {@code packs} is 0.</li>
 * </ul>
 */
public record BoxOpenPayload(String set, int packs, ItemStack card, long cost, boolean jackpot) implements CustomPayload {
    public static final Id<BoxOpenPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "box_open"));
    public static final PacketCodec<RegistryByteBuf, BoxOpenPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, BoxOpenPayload::set, PacketCodecs.VAR_INT, BoxOpenPayload::packs,
            ItemStack.OPTIONAL_PACKET_CODEC, BoxOpenPayload::card, PacketCodecs.VAR_LONG, BoxOpenPayload::cost,
            PacketCodecs.BOOL, BoxOpenPayload::jackpot, BoxOpenPayload::new);

    /** Booster box opened from the hand. */
    public BoxOpenPayload(String set, int packs) {
        this(set, packs, ItemStack.EMPTY, 0L, false);
    }

    /** Mystery Box opened from the hand. */
    public static BoxOpenPayload mystery(ItemStack card, long cost, boolean jackpot) {
        return new BoxOpenPayload("", 0, card.copyWithCount(1), cost, jackpot);
    }

    public boolean isMystery() { return !card.isEmpty(); }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
