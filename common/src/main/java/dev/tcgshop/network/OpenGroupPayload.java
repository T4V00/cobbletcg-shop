package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: prepares a group of packs to tear them at once (1-100). */
public record OpenGroupPayload(List<ResourceLocation> items) implements CustomPacketPayload {
    public static final Type<OpenGroupPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "open_group"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGroupPayload> CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenGroupPayload::items, OpenGroupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
