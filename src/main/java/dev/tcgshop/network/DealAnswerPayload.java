package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client -> server: answer to an NPC's deal. counter = your counteroffer (only when haggling).
 * ACCEPT/REJECT_* work for any deal; the rest only with a merchant who is selling you something.
 */
public record DealAnswerPayload(int entityId, int action, long counter) implements CustomPayload {
    public static final int ACCEPT = 0, REJECT_POLITE = 1, REJECT_RUDE = 2, COUNTER = 3, ARG_MARKET = 4, ARG_ELSEWHERE = 5, ARG_BROKE = 6,
            ARG_MAX = 7, INTEREST = 8;
    public static final Id<DealAnswerPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "deal_answer"));
    public static final PacketCodec<RegistryByteBuf, DealAnswerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DealAnswerPayload::entityId, PacketCodecs.VAR_INT, DealAnswerPayload::action,
            PacketCodecs.VAR_LONG, DealAnswerPayload::counter, DealAnswerPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
