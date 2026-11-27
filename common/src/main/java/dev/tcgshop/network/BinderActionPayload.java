package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: binder actions. See the constants. */
public record BinderActionPayload(int action, String arg, int amount) implements CustomPacketPayload {
    public static final int OPEN_GENERAL = 0, WITHDRAW = 1, DEPOSIT_ALL = 2, MARK_SEEN = 3, CLEAR_FRESH = 4, WISH_TOGGLE = 5, TITLE_SET = 6;
    public static final int PERSONAL_PLACE = 10, PERSONAL_REMOVE = 11, PERSONAL_ADD_PAGE = 12, PERSONAL_STYLE = 13;
    /**
     * 1.0. WITHDRAW_MANY: arg = "key=amount;key=amount..." (key = card id, or "#" + signature for a variant / graded card).
     * PERSONAL_REMOVE_MANY: arg = pocket indexes separated by commas. PERSONAL_MOVE: arg = source pocket, amount = target
     * pocket (swaps if occupied). PERSONAL_DEPOSIT: files the inventory cards into free pockets.
     */
    public static final int WITHDRAW_MANY = 7, PERSONAL_REMOVE_MANY = 14, PERSONAL_MOVE = 15, PERSONAL_DEPOSIT = 16;

    public static final Type<BinderActionPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "binder_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BinderActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BinderActionPayload::action,
            ByteBufCodecs.stringUtf8(32767), BinderActionPayload::arg,
            ByteBufCodecs.VAR_INT, BinderActionPayload::amount,
            BinderActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
