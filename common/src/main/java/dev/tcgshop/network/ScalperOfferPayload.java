package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server -> client: a Scalper offers you a pack. */
public record ScalperOfferPayload(int entityId, ItemStack pack, long price, long market) implements CustomPacketPayload {
    public static final Type<ScalperOfferPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "scalper_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScalperOfferPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScalperOfferPayload::entityId,
            ItemStack.STREAM_CODEC, ScalperOfferPayload::pack,
            ByteBufCodecs.VAR_LONG, ScalperOfferPayload::price,
            ByteBufCodecs.VAR_LONG, ScalperOfferPayload::market,
            ScalperOfferPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
