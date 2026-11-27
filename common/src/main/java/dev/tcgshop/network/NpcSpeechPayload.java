package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: an NPC says something (shown in a speech bubble over its head, with "talking" sounds). */
public record NpcSpeechPayload(int entityId, Component text) implements CustomPacketPayload {
    public static final Type<NpcSpeechPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "npc_speech"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcSpeechPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcSpeechPayload::entityId, ComponentSerialization.STREAM_CODEC, NpcSpeechPayload::text, NpcSpeechPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
