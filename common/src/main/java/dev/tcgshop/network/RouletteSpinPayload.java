package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: spin the roulette betting `amount` on red (red=true) or black. */
public record RouletteSpinPayload(BlockPos pos, boolean red, long amount) implements CustomPacketPayload {
    public static final Type<RouletteSpinPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "roulette_spin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouletteSpinPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RouletteSpinPayload::pos, ByteBufCodecs.BOOL, RouletteSpinPayload::red,
            ByteBufCodecs.VAR_LONG, RouletteSpinPayload::amount, RouletteSpinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
