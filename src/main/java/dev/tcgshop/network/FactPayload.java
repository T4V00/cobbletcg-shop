package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

/** Server -> client: fun fact / special milestone to show as a banner. */
public record FactPayload(Text title, Text body) implements CustomPayload {
    public static final Id<FactPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "fact"));
    public static final PacketCodec<RegistryByteBuf, FactPayload> CODEC = PacketCodec.tuple(
            TextCodecs.REGISTRY_PACKET_CODEC, FactPayload::title,
            TextCodecs.REGISTRY_PACKET_CODEC, FactPayload::body,
            FactPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
