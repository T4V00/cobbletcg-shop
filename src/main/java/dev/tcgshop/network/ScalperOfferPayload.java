package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: a Scalper offers you a pack. */
public record ScalperOfferPayload(int entityId, ItemStack pack, long price, long market) implements CustomPayload {
    public static final Id<ScalperOfferPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "scalper_offer"));
    public static final PacketCodec<RegistryByteBuf, ScalperOfferPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ScalperOfferPayload::entityId,
            ItemStack.PACKET_CODEC, ScalperOfferPayload::pack,
            PacketCodecs.VAR_LONG, ScalperOfferPayload::price,
            PacketCodecs.VAR_LONG, ScalperOfferPayload::market,
            ScalperOfferPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
