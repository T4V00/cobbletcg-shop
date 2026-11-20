package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: abre la pantalla de un binder. kind 0 = general, 1 = personal (hand = mano que lo sostiene). */
public record OpenBinderPayload(int kind, int hand, java.util.List<String> unlocked) implements CustomPayload {
    public static final Id<OpenBinderPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "open_binder"));
    public static final PacketCodec<RegistryByteBuf, OpenBinderPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, OpenBinderPayload::kind,
            PacketCodecs.VAR_INT, OpenBinderPayload::hand,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), OpenBinderPayload::unlocked,
            OpenBinderPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
