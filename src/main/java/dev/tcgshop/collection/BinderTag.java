package dev.tcgshop.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;

import java.util.UUID;

/** Componente de ítem: vincula un binder a su id único y a su dueño (se asigna al abrirlo por primera vez). */
public record BinderTag(UUID id, UUID owner, String ownerName) {
    public static final Codec<BinderTag> CODEC = RecordCodecBuilder.create(i -> i.group(
            Uuids.CODEC.fieldOf("id").forGetter(BinderTag::id),
            Uuids.CODEC.fieldOf("owner").forGetter(BinderTag::owner),
            Codec.STRING.fieldOf("owner_name").forGetter(BinderTag::ownerName)
    ).apply(i, BinderTag::new));

    public static final PacketCodec<ByteBuf, BinderTag> PACKET_CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, BinderTag::id,
            Uuids.PACKET_CODEC, BinderTag::owner,
            PacketCodecs.STRING, BinderTag::ownerName,
            BinderTag::new);
}
