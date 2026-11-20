package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Cliente -> servidor: respuesta a la oferta del Scalper. */
public record ScalperAnswerPayload(int entityId, boolean accept) implements CustomPayload {
    public static final Id<ScalperAnswerPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "scalper_answer"));
    public static final PacketCodec<RegistryByteBuf, ScalperAnswerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ScalperAnswerPayload::entityId,
            PacketCodecs.BOOL, ScalperAnswerPayload::accept,
            ScalperAnswerPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
