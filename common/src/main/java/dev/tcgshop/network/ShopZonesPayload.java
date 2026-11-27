package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the dimension's shop zones + your own accounting (for the Manager Glasses and the phone). */
public record ShopZonesPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ShopZonesPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "shop_zones"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopZonesPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, ShopZonesPayload::data, ShopZonesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
