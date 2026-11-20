package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Cliente -> servidor: comprar del catálogo (source = id del Mayorista, o -1 = teléfono). */
public record StoreBuyPayload(int source, int index, int qty) implements CustomPayload {
    public static final Id<StoreBuyPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "store_buy"));
    public static final PacketCodec<RegistryByteBuf, StoreBuyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StoreBuyPayload::source,
            PacketCodecs.VAR_INT, StoreBuyPayload::index,
            PacketCodecs.VAR_INT, StoreBuyPayload::qty,
            StoreBuyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
