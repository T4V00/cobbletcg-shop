package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/** Client -> server: prepares a group of packs to tear them at once (1-100). */
public record OpenGroupPayload(List<Identifier> items) implements CustomPayload {
    public static final Id<OpenGroupPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "open_group"));
    public static final PacketCodec<RegistryByteBuf, OpenGroupPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC.collect(PacketCodecs.toList()), OpenGroupPayload::items, OpenGroupPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
