package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: title chosen by each connected player (uuid -> "species|tier"). */
public record TitlesPayload(Map<String, String> titles) implements CustomPacketPayload {
    public static final Type<TitlesPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "titles"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TitlesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, String, Map<String, String>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8),
            TitlesPayload::titles, TitlesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
