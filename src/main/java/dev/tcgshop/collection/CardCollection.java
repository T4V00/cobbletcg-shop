package dev.tcgshop.collection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The PLAYER's TCG data (Fabric Data Attachment, persistent and kept on death).
 * The contents of each General Binder live in BinderRegistry (each binder is unique and has an owner).
 *
 *  credited      cards already counted for advancements (updated WHEN OPENING a binder)
 *  facts         fun-fact milestones already shown
 *  wishlist      wanted cards
 *  species       Pokémon -> cards of that Pokémon pulled from packs (for titles)
 *  title         chosen title ("species|tier" or empty)
 *  legacyCounts  binder contents from previous versions (migrated to the first binder you register)
 */
public final class CardCollection {
    public final Map<String, Integer> legacyCounts;
    public final Set<String> legacyFresh;
    public final Set<String> credited;
    public final Set<Integer> facts;
    public final Set<String> wishlist;
    public final Map<String, Integer> species;
    public String title;
    public long totalObtained;
    public int packsOpened, packsRuined, godPacks, scalpersDefeated;

    public CardCollection() {
        this(Map.of(), Set.of(), Set.of(), Set.of(), 0, 0, 0, 0, 0, Set.of(), Map.of(), "");
    }

    public CardCollection(Map<String, Integer> legacyCounts, Set<String> legacyFresh, Set<String> credited, Set<Integer> facts,
                          long totalObtained, int packsOpened, int packsRuined, int godPacks, int scalpersDefeated,
                          Set<String> wishlist, Map<String, Integer> species, String title) {
        this.legacyCounts = new HashMap<>(legacyCounts);
        this.legacyFresh = new HashSet<>(legacyFresh);
        this.credited = new HashSet<>(credited);
        this.facts = new HashSet<>(facts);
        this.totalObtained = totalObtained;
        this.packsOpened = packsOpened;
        this.packsRuined = packsRuined;
        this.godPacks = godPacks;
        this.scalpersDefeated = scalpersDefeated;
        this.wishlist = new HashSet<>(wishlist);
        this.species = new HashMap<>(species);
        this.title = title;
    }

    public static final Codec<CardCollection> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counts", Map.of()).forGetter(c -> c.legacyCounts),
            Codec.STRING.listOf().optionalFieldOf("fresh", List.of()).forGetter(c -> List.copyOf(c.legacyFresh)),
            Codec.STRING.listOf().optionalFieldOf("credited", List.of()).forGetter(c -> List.copyOf(c.credited)),
            Codec.INT.listOf().optionalFieldOf("facts", List.of()).forGetter(c -> List.copyOf(c.facts)),
            Codec.LONG.optionalFieldOf("total_obtained", 0L).forGetter(c -> c.totalObtained),
            Codec.INT.optionalFieldOf("packs_opened", 0).forGetter(c -> c.packsOpened),
            Codec.INT.optionalFieldOf("packs_ruined", 0).forGetter(c -> c.packsRuined),
            Codec.INT.optionalFieldOf("god_packs", 0).forGetter(c -> c.godPacks),
            Codec.INT.optionalFieldOf("scalpers_defeated", 0).forGetter(c -> c.scalpersDefeated),
            Codec.STRING.listOf().optionalFieldOf("wishlist", List.of()).forGetter(c -> List.copyOf(c.wishlist)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("species", Map.of()).forGetter(c -> c.species),
            Codec.STRING.optionalFieldOf("title", "").forGetter(c -> c.title)
    ).apply(i, (counts, fresh, credited, facts, total, opened, ruined, god, scalpers, wish, species, title) -> new CardCollection(
            BinderData.migrateCounts(counts), BinderData.migrateIds(fresh), BinderData.migrateIds(credited), new HashSet<>(facts),
            total, opened, ruined, god, scalpers, BinderData.migrateIds(wish), species, title)));
}
