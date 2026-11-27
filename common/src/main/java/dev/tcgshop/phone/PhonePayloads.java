package dev.tcgshop.phone;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Phone upgrades. Sync (server -> client): tier, costs, call and locks of the catalog about to be opened.
 * Act (client -> server): "buy" (tier up) or "call" (daily call). The server validates everything.
 */
public final class PhonePayloads {
    private PhonePayloads() {}

    public record Sync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Sync> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "phone_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Sync::data, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record Act(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Act> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "phone_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Act::data, Act::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /**
     * 1.0, server -> client: result of a purchase made from the phone Market, the Wholesaler or the Upgrades app, shown
     * as a notice inside the open phone / stall. Keys: "k" (bought, no_money, locked, no_bank, upgraded), "src" (catalog
     * source), "item" (encoded stack, optional), "qty", "amount" (paid or needed), "bal" (balance), "tier".
     */
    public record Result(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Result> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "phone_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Result::data, Result::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /**
     * 1.0 beta 2, server -> client: data of the phone apps added in 1.0 (PhoneApps). "k" says what it carries:
     * "messages" (the whole inbox, PhoneMessages.sync), "loans" (loan + credit history, Loans.sync), "stats" (Stats app,
     * PhoneStats.send) or "notice" (a notice shown inside the phone: kind, title, body, args). The client -> server
     * side of these apps reuses {@link Act} with new "a" values (msg_*, loan_*, stats).
     */
    public record Apps(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Apps> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "phone_apps"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Apps> CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, Apps::data, Apps::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return ID; }
    }
}
