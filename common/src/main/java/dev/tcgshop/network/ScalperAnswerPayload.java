package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: answer to the Scalper's offer. */
public record ScalperAnswerPayload(int entityId, boolean accept) implements CustomPacketPayload {
    public static final Type<ScalperAnswerPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "scalper_answer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScalperAnswerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScalperAnswerPayload::entityId,
            ByteBufCodecs.BOOL, ScalperAnswerPayload::accept,
            ScalperAnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
