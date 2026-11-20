package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: has abierto una caja de sobres desde la mano (los sobres ya están en tu inventario): animación. */
public record BoxOpenPayload(String set, int packs) implements CustomPayload {
    public static final Id<BoxOpenPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "box_open"));
    public static final PacketCodec<RegistryByteBuf, BoxOpenPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, BoxOpenPayload::set, PacketCodecs.VAR_INT, BoxOpenPayload::packs, BoxOpenPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
