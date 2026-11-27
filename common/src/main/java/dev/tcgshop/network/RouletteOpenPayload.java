package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: opens the roulette (with your current balance). */
public record RouletteOpenPayload(BlockPos pos, long balance) implements CustomPacketPayload {
    public static final Type<RouletteOpenPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "roulette_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouletteOpenPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RouletteOpenPayload::pos, ByteBufCodecs.VAR_LONG, RouletteOpenPayload::balance, RouletteOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
