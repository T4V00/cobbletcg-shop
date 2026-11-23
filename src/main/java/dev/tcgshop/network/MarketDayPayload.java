package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: a new market day started (prices are recalculated on the client by itself). */
public record MarketDayPayload(long day) implements CustomPayload {
    public static final Id<MarketDayPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "market_day"));
    public static final PacketCodec<RegistryByteBuf, MarketDayPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_LONG, MarketDayPayload::day, MarketDayPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
