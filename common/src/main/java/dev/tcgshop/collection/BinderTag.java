package dev.tcgshop.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Item component: binds a binder to its unique id and its owner (assigned when first opened). */
public record BinderTag(UUID id, UUID owner, String ownerName) {
    public static final Codec<BinderTag> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.AUTHLIB_CODEC.fieldOf("id").forGetter(BinderTag::id),
            UUIDUtil.AUTHLIB_CODEC.fieldOf("owner").forGetter(BinderTag::owner),
            Codec.STRING.fieldOf("owner_name").forGetter(BinderTag::ownerName)
    ).apply(i, BinderTag::new));

    public static final StreamCodec<ByteBuf, BinderTag> PACKET_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BinderTag::id,
            UUIDUtil.STREAM_CODEC, BinderTag::owner,
            ByteBufCodecs.STRING_UTF8, BinderTag::ownerName,
            BinderTag::new);
}
