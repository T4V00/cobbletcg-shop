package dev.tcgshop.binder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenido del Personal Binder (componente de datos del ítem).
 *  slots   un id de carta por bolsillo ("" = vacío). 9 bolsillos por página.
 *  style   ids de personalización: fondo, patrón y paleta (ver BinderStyles).
 */
public record PersonalBinderData(List<String> slots, String background, String pattern, String palette) {
    public static final int POCKETS_PER_PAGE = 9;
    public static final int MAX_PAGES = 200;

    public static final PersonalBinderData DEFAULT = new PersonalBinderData(emptyPages(4), "none", "none", "classic_red");

    public static final Codec<PersonalBinderData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("slots").forGetter(PersonalBinderData::slots),
            Codec.STRING.optionalFieldOf("background", "none").forGetter(PersonalBinderData::background),
            Codec.STRING.optionalFieldOf("pattern", "none").forGetter(PersonalBinderData::pattern),
            Codec.STRING.optionalFieldOf("palette", "classic_red").forGetter(PersonalBinderData::palette)
    ).apply(i, PersonalBinderData::new));

    public static final PacketCodec<ByteBuf, PersonalBinderData> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING.collect(PacketCodecs.toList()), PersonalBinderData::slots,
            PacketCodecs.STRING, PersonalBinderData::background,
            PacketCodecs.STRING, PersonalBinderData::pattern,
            PacketCodecs.STRING, PersonalBinderData::palette,
            PersonalBinderData::new);

    public static List<String> emptyPages(int pages) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < pages * POCKETS_PER_PAGE; i++) l.add("");
        return l;
    }

    public int pages() {
        return Math.max(1, (slots.size() + POCKETS_PER_PAGE - 1) / POCKETS_PER_PAGE);
    }

    public int filled() {
        int n = 0;
        for (String s : slots) if (!s.isEmpty()) n++;
        return n;
    }

    public PersonalBinderData withSlot(int index, String id) {
        List<String> l = new ArrayList<>(slots);
        while (l.size() <= index) l.add("");
        l.set(index, id);
        return new PersonalBinderData(l, background, pattern, palette);
    }

    public PersonalBinderData withPages(int pages) {
        List<String> l = new ArrayList<>(slots);
        while (l.size() < pages * POCKETS_PER_PAGE) l.add("");
        return new PersonalBinderData(l, background, pattern, palette);
    }

    public PersonalBinderData withStyle(String kind, String value) {
        return switch (kind) {
            case "background" -> new PersonalBinderData(slots, value, pattern, palette);
            case "pattern" -> new PersonalBinderData(slots, background, value, palette);
            default -> new PersonalBinderData(slots, background, pattern, value);
        };
    }
}
