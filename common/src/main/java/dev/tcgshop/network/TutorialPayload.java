package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client (only to the shop owner): scripted speech bubble of the Team Rocket Meowth tutorial.
 * Drawn with ClientSpeech over entity {@code entityId} (any entity, not just the mod's NPCs), lasts at
 * least {@code duration} ticks and is visible even if the player has NPC speech bubbles turned off.
 * (Since 0.9 it no longer opens any screen: it used to be TutorialScreen.)
 */
public record TutorialPayload(int entityId, Component text, int duration) implements CustomPacketPayload {
    public static final Type<TutorialPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "tutorial"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TutorialPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TutorialPayload::entityId,
            ComponentSerialization.STREAM_CODEC, TutorialPayload::text,
            ByteBufCodecs.VAR_INT, TutorialPayload::duration,
            TutorialPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
