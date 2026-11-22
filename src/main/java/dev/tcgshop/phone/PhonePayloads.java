package dev.tcgshop.phone;

import dev.tcgshop.TcgShop;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Mejoras del teléfono. Sync (servidor -> cliente): nivel, costes, llamada y candados del catálogo que se va a abrir.
 * Act (cliente -> servidor): "buy" (subir de nivel) o "call" (llamada diaria). El servidor valida todo.
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
}
