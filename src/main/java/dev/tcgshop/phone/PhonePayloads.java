package dev.tcgshop.phone;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Phone upgrades. Sync (server -> client): tier, costs, call and locks of the catalog about to be opened.
 * Act (client -> server): "buy" (tier up) or "call" (daily call). The server validates everything.
 */
public final class PhonePayloads {
    private PhonePayloads() {}

    public record Sync(NbtCompound data) implements CustomPayload {
        public static final Id<Sync> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "phone_sync"));
        public static final PacketCodec<RegistryByteBuf, Sync> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, Sync::data, Sync::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Act(NbtCompound data) implements CustomPayload {
        public static final Id<Act> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "phone_act"));
        public static final PacketCodec<RegistryByteBuf, Act> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, Act::data, Act::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * 1.0, server -> client: result of a purchase made from the phone Market, the Wholesaler or the Upgrades app, shown
     * as a notice inside the open phone / stall. Keys: "k" (bought, no_money, locked, no_bank, upgraded), "src" (catalog
     * source), "item" (encoded stack, optional), "qty", "amount" (paid or needed), "bal" (balance), "tier".
     */
    public record Result(NbtCompound data) implements CustomPayload {
        public static final Id<Result> ID = new Id<>(Identifier.of(TcgShop.MOD_ID, "phone_result"));
        public static final PacketCodec<RegistryByteBuf, Result> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, Result::data, Result::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}
