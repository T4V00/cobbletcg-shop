package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server -> client: cards of each pack in the group, their prices and whether they were destroyed. toBinder = they went to the General Binder. */
public record GroupRevealPayload(List<List<ItemStack>> cards, List<List<Long>> prices, List<Boolean> damaged, boolean toBinder) implements CustomPacketPayload {
    public static final Type<GroupRevealPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "group_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupRevealPayload> CODEC = StreamCodec.composite(
            ItemStack.LIST_STREAM_CODEC.apply(ByteBufCodecs.list()), GroupRevealPayload::cards,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()), GroupRevealPayload::prices,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()), GroupRevealPayload::damaged,
            ByteBufCodecs.BOOL, GroupRevealPayload::toBinder,
            GroupRevealPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
