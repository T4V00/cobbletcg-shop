package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: a new market day started (prices are recalculated on the client by itself). */
public record MarketDayPayload(long day) implements CustomPacketPayload {
    public static final Type<MarketDayPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "market_day"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketDayPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, MarketDayPayload::day, MarketDayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
