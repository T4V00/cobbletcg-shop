package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: zonas de tienda de la dimensión + contabilidad propia (para las Gafas de Gerente y el teléfono). */
public record ShopZonesPayload(NbtCompound data) implements CustomPayload {
    public static final Id<ShopZonesPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "shop_zones"));
    public static final PacketCodec<RegistryByteBuf, ShopZonesPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, ShopZonesPayload::data, ShopZonesPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
