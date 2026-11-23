package dev.tcgshop.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Uuids;

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
        return t;
    }
}
