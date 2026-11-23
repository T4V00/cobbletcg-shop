package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Server -> client: opens the roulette (with your current balance). */
public record RouletteOpenPayload(BlockPos pos, long balance) implements CustomPayload {
    public static final Id<RouletteOpenPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "roulette_open"));
    public static final PacketCodec<RegistryByteBuf, RouletteOpenPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, RouletteOpenPayload::pos, PacketCodecs.VAR_LONG, RouletteOpenPayload::balance, RouletteOpenPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
