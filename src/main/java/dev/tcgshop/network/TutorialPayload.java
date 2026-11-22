package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/**
 * Servidor -> cliente (sólo al dueño de la tienda): bocadillo guionizado del tutorial del Meowth del Team Rocket.
 * Se pinta con ClientSpeech sobre la entidad {@code entityId} (cualquier entidad, no sólo los NPC del mod), dura al
 * menos {@code duration} ticks y se ve aunque el jugador tenga apagados los bocadillos de los NPC.
 * (Desde la 0.9 ya no abre ninguna pantalla: antes era TutorialScreen.)
 */
public record TutorialPayload(int entityId, Text text, int duration) implements CustomPayload {
    public static final Id<TutorialPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "tutorial"));
    public static final PacketCodec<RegistryByteBuf, TutorialPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TutorialPayload::entityId,
            TextCodecs.REGISTRY_PACKET_CODEC, TutorialPayload::text,
            PacketCodecs.VAR_INT, TutorialPayload::duration,
            TutorialPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
