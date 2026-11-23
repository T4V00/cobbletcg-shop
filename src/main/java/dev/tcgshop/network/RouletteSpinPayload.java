package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client -> server: spin the roulette betting `amount` on red (red=true) or black. */
public record RouletteSpinPayload(BlockPos pos, boolean red, long amount) implements CustomPayload {
    public static final Id<RouletteSpinPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "roulette_spin"));
    public static final PacketCodec<RegistryByteBuf, RouletteSpinPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, RouletteSpinPayload::pos, PacketCodecs.BOOL, RouletteSpinPayload::red,
            PacketCodecs.VAR_LONG, RouletteSpinPayload::amount, RouletteSpinPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
