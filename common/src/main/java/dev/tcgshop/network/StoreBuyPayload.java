package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: buy from the catalog (source = Wholesaler id, or -1 = phone). */
public record StoreBuyPayload(int source, int index, int qty) implements CustomPacketPayload {
    public static final Type<StoreBuyPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "store_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StoreBuyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StoreBuyPayload::source,
            ByteBufCodecs.VAR_INT, StoreBuyPayload::index,
            ByteBufCodecs.VAR_INT, StoreBuyPayload::qty,
            StoreBuyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
