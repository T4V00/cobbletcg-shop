package dev.tcgshop.binder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.ArrayList;
import java.util.List;

/**
 * Personal Binder contents (item data component).
 *  slots   one card id per pocket ("" = empty). 9 pockets per page.
 *  style   customization ids: background, pattern and palette (see BinderStyles).
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
    ).apply(i, (slots, bg, pat, pal) -> new PersonalBinderData(migrate(slots), bg, pat, pal)));

    /**
     * Slots saved with ScalperTCG ids ("cobbletcg:..." or "wish:cobbletcg:...") -> MasterBall Traders API.
     * TCG Pocket cards (removed in 0.9) leave the slot empty.
     */
    private static List<String> migrate(List<String> slots) {
        List<String> out = new ArrayList<>(slots.size());
        for (String s : slots) {
            boolean wish = s.startsWith("wish:");
            String id = dev.masterball.api.card.CardInfo.migrate(wish ? s.substring(5) : s);
            if (dev.masterball.api.card.CardCatalog.isRemovedId(id)) out.add("");
            else out.add(wish ? "wish:" + id : id);
        }
        return out;
    }

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
