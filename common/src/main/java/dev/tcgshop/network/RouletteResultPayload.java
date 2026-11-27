package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the number that came up, whether you won, the payout and your new balance. */
public record RouletteResultPayload(int number, boolean win, long payout, long balance) implements CustomPacketPayload {
    public static final Type<RouletteResultPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "roulette_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouletteResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RouletteResultPayload::number, ByteBufCodecs.BOOL, RouletteResultPayload::win,
            ByteBufCodecs.VAR_LONG, RouletteResultPayload::payout, ByteBufCodecs.VAR_LONG, RouletteResultPayload::balance,
            RouletteResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
