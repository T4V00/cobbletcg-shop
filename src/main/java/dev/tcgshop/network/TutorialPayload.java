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
 * Server -> client (only to the shop owner): scripted speech bubble of the Team Rocket Meowth tutorial.
 * Drawn with ClientSpeech over entity {@code entityId} (any entity, not just the mod's NPCs), lasts at
 * least {@code duration} ticks and is visible even if the player has NPC speech bubbles turned off.
 * (Since 0.9 it no longer opens any screen: it used to be TutorialScreen.)
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
