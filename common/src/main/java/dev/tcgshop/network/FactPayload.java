package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: fun fact / special milestone to show as a banner. */
public record FactPayload(Component title, Component body) implements CustomPacketPayload {
    public static final Type<FactPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "fact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FactPayload> CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, FactPayload::title,
            ComponentSerialization.STREAM_CODEC, FactPayload::body,
            FactPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
