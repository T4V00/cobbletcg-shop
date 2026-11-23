package dev.tcgshop.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Contents of ONE specific General Binder (each binder has its own and is bound to its owner). */
public final class BinderData {
    public UUID owner;
    public String ownerName;
    public final Map<String, Integer> counts;
    public final Set<String> fresh;

    public BinderData(UUID owner, String ownerName, Map<String, Integer> counts, Set<String> fresh) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.counts = new HashMap<>(counts);
        this.fresh = new HashSet<>(fresh);
    }

    public static final Codec<BinderData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Uuids.CODEC.fieldOf("owner").forGetter(b -> b.owner),
            Codec.STRING.optionalFieldOf("owner_name", "?").forGetter(b -> b.ownerName),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counts", Map.of()).forGetter(b -> b.counts),
            Codec.STRING.listOf().optionalFieldOf("fresh", List.of()).forGetter(b -> List.copyOf(b.fresh))
    ).apply(i, (o, n, c, f) -> new BinderData(o, n, migrateCounts(c), migrateIds(f))));

    /** Ids saved with the ScalperTCG namespace ("cobbletcg:") are migrated to the MasterBall Traders API ones. */
    static Map<String, Integer> migrateCounts(Map<String, Integer> in) {
        Map<String, Integer> out = new HashMap<>();
        in.forEach((k, v) -> { String id = dev.masterball.api.card.CardInfo.migrate(k); if (!dev.masterball.api.card.CardCatalog.isRemovedId(id)) out.merge(id, v, Integer::sum); });
        return out;
    }

    static HashSet<String> migrateIds(java.util.Collection<String> in) {
        HashSet<String> out = new HashSet<>();
        for (String s : in) { String id = dev.masterball.api.card.CardInfo.migrate(s); if (!dev.masterball.api.card.CardCatalog.isRemovedId(id)) out.add(id); }
        return out;
    }

    public int count(String id) { return counts.getOrDefault(id, 0); }

    public void add(String id, int n) { counts.merge(id, n, Integer::sum); }

    public int remove(String id, int n) {
        int have = count(id), take = Math.min(have, n);
        if (take <= 0) return 0;
        if (have - take == 0) counts.remove(id);
        else counts.put(id, have - take);
        return take;
    }

    public long totalCards() {
        long t = 0;
        for (int v : counts.values()) t += v;
        for (Special s : specials) t += s.count;
        return t;
    }

    // ------------------------------------------------------------------ special cards (1.0)

    /**
     * A card that cannot be filed by its id alone: a print variant (reverse holo, 1st edition...) or a graded slab.
     * Kept as a full stack (template with count 1) plus how many identical copies there are. Saved by BinderRegistry
     * under "specials" (outside this codec, because item components need the registry lookup).
     */
    public static final class Special {
        public final ItemStack stack;
        public int count;

        public Special(ItemStack stack, int count) {
            this.stack = stack.copyWithCount(1);
            this.count = count;
        }

        public String signature() { return BinderData.signature(stack); }
    }

    public final List<Special> specials = new ArrayList<>();

    /** Does this card have to be kept as a full stack (variant or graded) instead of by its id? */
    public static boolean isSpecial(ItemStack st) {
        return dev.tcgshop.grading.Grading.isGraded(st) || dev.masterball.api.card.PrintVariant.isSpecial(st);
    }

    /** Stable key of a special card (same on client and server): id|variant|grade|cert. */
    public static String signature(ItemStack st) {
        return dev.masterball.api.card.CardInfo.id(st) + "|" + dev.masterball.api.card.PrintVariant.of(st).key + "|"
                + dev.tcgshop.grading.Grading.grade(st) + "|" + dev.tcgshop.grading.Grading.cert(st);
    }

    public void addSpecial(ItemStack st, int n) {
        if (st.isEmpty() || n <= 0) return;
        for (Special s : specials) {
            if (ItemStack.areItemsAndComponentsEqual(s.stack, st)) {
                s.count += n;
                return;
            }
        }
        specials.add(new Special(st, n));
    }

    @Nullable
    public Special findSpecial(String signature) {
        for (Special s : specials) if (s.signature().equals(signature)) return s;
        return null;
    }

    /** Takes up to n copies of that special card out. Returns them (empty if there were none). */
    public ItemStack removeSpecial(Special s, int n) {
        int take = Math.min(s.count, n);
        if (take <= 0 || !specials.contains(s)) return ItemStack.EMPTY;
        s.count -= take;
        if (s.count <= 0) specials.remove(s);
        return s.stack.copyWithCount(take);
    }

    public int specialCount(String signature) {
        int n = 0;
        for (Special s : specials) if (s.signature().equals(signature)) n += s.count;
        return n;
    }
}
