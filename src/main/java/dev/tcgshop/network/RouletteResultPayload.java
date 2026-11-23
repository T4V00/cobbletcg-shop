package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: the number that came up, whether you won, the payout and your new balance. */
public record RouletteResultPayload(int number, boolean win, long payout, long balance) implements CustomPayload {
    public static final Id<RouletteResultPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "roulette_result"));
    public static final PacketCodec<RegistryByteBuf, RouletteResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RouletteResultPayload::number, PacketCodecs.BOOL, RouletteResultPayload::win,
            PacketCodecs.VAR_LONG, RouletteResultPayload::payout, PacketCodecs.VAR_LONG, RouletteResultPayload::balance,
            RouletteResultPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
