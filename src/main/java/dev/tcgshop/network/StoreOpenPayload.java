package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Server -> client: catalog for sale. source = Wholesaler entity id, or -1 = phone (opens the phone).
 */
public record StoreOpenPayload(int source, List<ItemStack> goods, List<Long> prices) implements CustomPayload {
    public static final Id<StoreOpenPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "store_open"));
    public static final PacketCodec<RegistryByteBuf, StoreOpenPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StoreOpenPayload::source,
            ItemStack.LIST_PACKET_CODEC, StoreOpenPayload::goods,
            PacketCodecs.VAR_LONG.collect(PacketCodecs.toList()), StoreOpenPayload::prices,
            StoreOpenPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
