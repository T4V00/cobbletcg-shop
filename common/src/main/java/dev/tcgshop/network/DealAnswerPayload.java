package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> server: answer to an NPC's deal. counter = your counteroffer (only when haggling).
 * ACCEPT/REJECT_* work for any deal; the rest only with a merchant who is selling you something.
 * 1.1: JOKE / COMPLIMENT / STORY / SMALLTALK (charm, only when the customer's first offer was already good for you) and PERSUADE
 * (charisma picks the best argument for your counteroffer).
 */
public record DealAnswerPayload(int entityId, int action, long counter) implements CustomPacketPayload {
    public static final int ACCEPT = 0, REJECT_POLITE = 1, REJECT_RUDE = 2, COUNTER = 3, ARG_MARKET = 4, ARG_ELSEWHERE = 5, ARG_BROKE = 6,
            ARG_MAX = 7, INTEREST = 8, JOKE = 9, COMPLIMENT = 10, STORY = 11, PERSUADE = 12, SMALLTALK = 13;
    public static final Type<DealAnswerPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "deal_answer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DealAnswerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DealAnswerPayload::entityId, ByteBufCodecs.VAR_INT, DealAnswerPayload::action,
            ByteBufCodecs.VAR_LONG, DealAnswerPayload::counter, DealAnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
