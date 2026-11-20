package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: un NPC dice algo (se muestra en un bocadillo sobre su cabeza, con sonidos de "hablar"). */
public record NpcSpeechPayload(int entityId, Text text) implements CustomPayload {
    public static final Id<NpcSpeechPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "npc_speech"));
    public static final PacketCodec<RegistryByteBuf, NpcSpeechPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, NpcSpeechPayload::entityId, TextCodecs.REGISTRY_PACKET_CODEC, NpcSpeechPayload::text, NpcSpeechPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
