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
 *  rewardedSets  1.1: sets whose completion reward (Master Ball + money) was already given
 *  pulled        1.1: cards this player got from the game itself (packs, NPC gifts): only these count for the set
 *                completion reward, so one set of cards passed from player to player cannot pay every account.
 *                Absent in older saves: seeded from credited (there was nothing to farm before the reward existed)
 *  pendingSetMoney 1.1: set completion money that could not be paid yet (CobbleDollars missing or refused), retried
 */
public final class CardCollection {
    public final Map<String, Integer> legacyCounts;
    public final Set<String> legacyFresh;
    public final Set<String> credited;
    public final Set<Integer> facts;
    public final Set<String> wishlist;
    public final Map<String, Integer> species;
    public String title;
    public final Set<String> rewardedSets = new HashSet<>();
    public final Set<String> pulled = new HashSet<>();
    public long pendingSetMoney;
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
            Codec.STRING.optionalFieldOf("title", "").forGetter(c -> c.title),
            Codec.STRING.listOf().optionalFieldOf("rewarded_sets", List.of()).forGetter(c -> List.copyOf(c.rewardedSets)),
            Codec.STRING.listOf().optionalFieldOf("pulled").forGetter(c -> java.util.Optional.of(List.copyOf(c.pulled))),
            Codec.LONG.optionalFieldOf("pending_set_money", 0L).forGetter(c -> c.pendingSetMoney)
    ).apply(i, (counts, fresh, credited, facts, total, opened, ruined, god, scalpers, wish, species, title, rewarded, pulled, pending) -> {
        CardCollection c = new CardCollection(
                BinderData.migrateCounts(counts), BinderData.migrateIds(fresh), BinderData.migrateIds(credited), new HashSet<>(facts),
                total, opened, ruined, god, scalpers, BinderData.migrateIds(wish), species, title);
        c.rewardedSets.addAll(rewarded);
        if (pulled.isPresent()) c.pulled.addAll(BinderData.migrateIds(pulled.get()));
        else c.pulled.addAll(c.credited); // save from before the field: everything already counted is trusted
        c.pendingSetMoney = Math.max(0, pending);
        return c;
    }));
}
