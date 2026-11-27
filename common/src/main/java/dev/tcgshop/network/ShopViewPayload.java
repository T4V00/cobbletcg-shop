package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server -> client: contents of a shop fixture (to buy, or to manage it if you are the owner). */
public record ShopViewPayload(BlockPos pos, List<ItemStack> items, List<Long> prices, boolean owner, String ownerName) implements CustomPacketPayload {
    public static final Type<ShopViewPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "shop_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopViewPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShopViewPayload::pos,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, ShopViewPayload::items,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), ShopViewPayload::prices,
            ByteBufCodecs.BOOL, ShopViewPayload::owner,
            ByteBufCodecs.STRING_UTF8, ShopViewPayload::ownerName,
            ShopViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
