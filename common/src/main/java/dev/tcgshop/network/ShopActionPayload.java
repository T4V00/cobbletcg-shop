package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: buy / change price / withdraw a slot of a shop fixture. */
public record ShopActionPayload(BlockPos pos, int action, int slot, long value) implements CustomPacketPayload {
    /** 1.1: MARKUP_ALL changes every price in the fixture by value percent (-90..+500); MARKET_ALL sets them all to the market price. */
    public static final int BUY = 0, SET_PRICE = 1, TAKE = 2, MARKUP_ALL = 3, MARKET_ALL = 4;
    public static final Type<ShopActionPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "shop_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopActionPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShopActionPayload::pos,
            ByteBufCodecs.VAR_INT, ShopActionPayload::action,
            ByteBufCodecs.VAR_INT, ShopActionPayload::slot,
            ByteBufCodecs.VAR_LONG, ShopActionPayload::value,
            ShopActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
