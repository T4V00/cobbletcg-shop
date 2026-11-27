package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server -> client: catalog for sale. source = Wholesaler entity id, or -1 = phone (opens the phone).
 */
public record StoreOpenPayload(int source, List<ItemStack> goods, List<Long> prices) implements CustomPacketPayload {
    public static final Type<StoreOpenPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "store_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StoreOpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StoreOpenPayload::source,
            ItemStack.LIST_STREAM_CODEC, StoreOpenPayload::goods,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), StoreOpenPayload::prices,
            StoreOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
