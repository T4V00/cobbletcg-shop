package dev.tcgshop.network;

import dev.tcgshop.TcgShop;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: binder actions. See the constants. */
public record BinderActionPayload(int action, String arg, int amount) implements CustomPayload {
    public static final int OPEN_GENERAL = 0, WITHDRAW = 1, DEPOSIT_ALL = 2, MARK_SEEN = 3, CLEAR_FRESH = 4, WISH_TOGGLE = 5, TITLE_SET = 6;
    public static final int PERSONAL_PLACE = 10, PERSONAL_REMOVE = 11, PERSONAL_ADD_PAGE = 12, PERSONAL_STYLE = 13;
    /**
     * 1.0. WITHDRAW_MANY: arg = "key=amount;key=amount..." (key = card id, or "#" + signature for a variant / graded card).
     * PERSONAL_REMOVE_MANY: arg = pocket indexes separated by commas. PERSONAL_MOVE: arg = source pocket, amount = target
     * pocket (swaps if occupied). PERSONAL_DEPOSIT: files the inventory cards into free pockets.
     */
    public static final int WITHDRAW_MANY = 7, PERSONAL_REMOVE_MANY = 14, PERSONAL_MOVE = 15, PERSONAL_DEPOSIT = 16;

    public static final Id<BinderActionPayload> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "binder_action"));
    public static final PacketCodec<RegistryByteBuf, BinderActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BinderActionPayload::action,
            PacketCodecs.string(32767), BinderActionPayload::arg,
            PacketCodecs.VAR_INT, BinderActionPayload::amount,
            BinderActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
