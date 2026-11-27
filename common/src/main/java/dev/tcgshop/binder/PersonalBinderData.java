package dev.tcgshop.binder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Personal Binder contents (item data component).
 *  slots     one card id per pocket ("" = empty, "wish:<id>" = reserved for a wanted card). 9 pockets per page.
 *  style     customization ids: background, pattern and palette (see BinderStyles).
 *  capacity  usable pockets (1.0). 0 = classic binder: no fixed limit, pages are added with "+".
 *            > 0 = fixed-size binder (the 25-card starter binder): pockets past it are locked and no pages can be added.
 *  extra     full stack of the pockets holding a special card (print variant or graded slab), keyed by pocket index.
 * Old items have neither capacity nor extra: they keep working exactly as before.
 */
public record PersonalBinderData(List<String> slots, String background, String pattern, String palette, int capacity,
                                 Map<String, ItemStack> extra) {
    public static final int POCKETS_PER_PAGE = 9;
    public static final int MAX_PAGES = 200;
    /** Starter binder: 5 starter packs x 5 cards. */
    public static final int STARTER_CAPACITY = 25;

    public static final PersonalBinderData DEFAULT = new PersonalBinderData(emptyPages(4), "none", "none", "classic_red");

    public PersonalBinderData {
        slots = slots == null ? List.of() : slots;
        extra = extra == null || extra.isEmpty() ? Map.of() : Map.copyOf(extra);
        capacity = Math.max(0, capacity);
    }

    public PersonalBinderData(List<String> slots, String background, String pattern, String palette) {
        this(slots, background, pattern, palette, 0, Map.of());
    }

    public static final Codec<PersonalBinderData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().fieldOf("slots").forGetter(PersonalBinderData::slots),
            Codec.STRING.optionalFieldOf("background", "none").forGetter(PersonalBinderData::background),
            Codec.STRING.optionalFieldOf("pattern", "none").forGetter(PersonalBinderData::pattern),
            Codec.STRING.optionalFieldOf("palette", "classic_red").forGetter(PersonalBinderData::palette),
            Codec.INT.optionalFieldOf("capacity", 0).forGetter(PersonalBinderData::capacity),
            Codec.unboundedMap(Codec.STRING, ItemStack.CODEC).optionalFieldOf("extra", Map.of()).forGetter(PersonalBinderData::extra)
    ).apply(i, (slots, bg, pat, pal, cap, extra) -> {
        List<String> migrated = migrate(slots);
        return new PersonalBinderData(migrated, bg, pat, pal, cap, pruneExtra(migrated, extra));
    }));

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

    /** Drops full stacks whose pocket is empty, a wish or out of range (never leaves a stale stack behind). */
    private static Map<String, ItemStack> pruneExtra(List<String> slots, Map<String, ItemStack> extra) {
        if (extra.isEmpty()) return extra;
        Map<String, ItemStack> out = new HashMap<>();
        for (Map.Entry<String, ItemStack> e : extra.entrySet()) {
            int i;
            try { i = Integer.parseInt(e.getKey()); } catch (NumberFormatException ex) { continue; }
            if (i < 0 || i >= slots.size() || e.getValue().isEmpty()) continue;
            String s = slots.get(i);
            if (s.isEmpty() || s.startsWith("wish:")) continue;
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PersonalBinderData> PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PersonalBinderData::slots,
            ByteBufCodecs.STRING_UTF8, PersonalBinderData::background,
            ByteBufCodecs.STRING_UTF8, PersonalBinderData::pattern,
            ByteBufCodecs.STRING_UTF8, PersonalBinderData::palette,
            ByteBufCodecs.VAR_INT, PersonalBinderData::capacity,
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, ItemStack, Map<String, ItemStack>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ItemStack.STREAM_CODEC),
            PersonalBinderData::extra,
            PersonalBinderData::new);

    public static List<String> emptyPages(int pages) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < pages * POCKETS_PER_PAGE; i++) l.add("");
        return l;
    }

    /** New player's binder: STARTER_CAPACITY pockets (3 pages, the last 2 pockets locked). */
    public static PersonalBinderData starter() {
        int pages = (STARTER_CAPACITY + POCKETS_PER_PAGE - 1) / POCKETS_PER_PAGE;
        return new PersonalBinderData(emptyPages(pages), "none", "none", "classic_red", STARTER_CAPACITY, Map.of());
    }

    public int pages() {
        return Math.max(1, (slots.size() + POCKETS_PER_PAGE - 1) / POCKETS_PER_PAGE);
    }

    /** Fixed-size binder (starter)? */
    public boolean fixedSize() { return capacity > 0; }

    /** Number of usable pockets. */
    public int limit() {
        return capacity > 0 ? Math.min(capacity, slots.size()) : slots.size();
    }

    /** Pocket past the capacity of a fixed-size binder. */
    public boolean isLocked(int index) {
        return capacity > 0 && index >= capacity;
    }

    /** Pockets in use (cards + wish reservations). */
    public int filled() {
        int n = 0;
        for (String s : slots) if (!s.isEmpty()) n++;
        return n;
    }

    /** Real cards in the binder (wishes not counted). */
    public int cards() {
        int n = 0;
        for (String s : slots) if (!s.isEmpty() && !s.startsWith("wish:")) n++;
        return n;
    }

    /** Full stack of a special card in that pocket, or null if it holds a plain card (or nothing). */
    @Nullable
    public ItemStack special(int index) {
        return extra.get(String.valueOf(index));
    }

    /** First usable empty pocket, or -1 if the binder is full. */
    public int firstFree() {
        int lim = limit();
        for (int i = 0; i < lim; i++) if (slots.get(i).isEmpty()) return i;
        return -1;
    }

    /** Usable pocket reserved for this card as a wish, or -1. */
    public int wishSlot(String id) {
        int lim = limit();
        String w = "wish:" + id;
        for (int i = 0; i < lim; i++) if (slots.get(i).equals(w)) return i;
        return -1;
    }

    /** Puts a plain card id (or "" / a wish) in that pocket; any special stack the pocket had is dropped. */
    public PersonalBinderData withSlot(int index, String id) {
        List<String> l = new ArrayList<>(slots);
        while (l.size() <= index) l.add("");
        l.set(index, id);
        Map<String, ItemStack> x = new HashMap<>(extra);
        x.remove(String.valueOf(index));
        return new PersonalBinderData(l, background, pattern, palette, capacity, x);
    }

    /** Puts a special card (variant / graded) in that pocket, keeping its full stack. */
    public PersonalBinderData withSpecial(int index, ItemStack stack) {
        PersonalBinderData d = withSlot(index, dev.masterball.api.card.CardInfo.id(stack).toString());
        Map<String, ItemStack> x = new HashMap<>(d.extra);
        x.put(String.valueOf(index), stack.copyWithCount(1));
        return new PersonalBinderData(d.slots, background, pattern, palette, capacity, x);
    }

    /** Moves the card of one pocket to another; if the target is occupied, the two swap places. */
    public PersonalBinderData swap(int from, int to) {
        List<String> l = new ArrayList<>(slots);
        while (l.size() <= Math.max(from, to)) l.add("");
        String a = l.get(from), b = l.get(to);
        l.set(from, b);
        l.set(to, a);
        Map<String, ItemStack> x = new HashMap<>(extra);
        ItemStack sa = x.remove(String.valueOf(from)), sb = x.remove(String.valueOf(to));
        if (sa != null) x.put(String.valueOf(to), sa);
        if (sb != null) x.put(String.valueOf(from), sb);
        return new PersonalBinderData(l, background, pattern, palette, capacity, x);
    }

    public PersonalBinderData withPages(int pages) {
        List<String> l = new ArrayList<>(slots);
        while (l.size() < pages * POCKETS_PER_PAGE) l.add("");
        return new PersonalBinderData(l, background, pattern, palette, capacity, extra);
    }

    public PersonalBinderData withStyle(String kind, String value) {
        return switch (kind) {
            case "background" -> new PersonalBinderData(slots, value, pattern, palette, capacity, extra);
            case "pattern" -> new PersonalBinderData(slots, background, value, palette, capacity, extra);
            default -> new PersonalBinderData(slots, background, pattern, value, capacity, extra);
        };
    }

    // ItemStack has no value equality: compare the stacks by item + components so that identical data is equal
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PersonalBinderData d)) return false;
        if (capacity != d.capacity || !slots.equals(d.slots) || !background.equals(d.background) || !pattern.equals(d.pattern)
                || !palette.equals(d.palette) || !extra.keySet().equals(d.extra.keySet())) return false;
        for (Map.Entry<String, ItemStack> e : extra.entrySet())
            if (!ItemStack.matches(e.getValue(), d.extra.get(e.getKey()))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(slots, background, pattern, palette, capacity, extra.keySet());
    }
}
