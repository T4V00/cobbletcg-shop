package dev.tcgshop.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.masterball.api.card.CardCatalog;
import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.PrintVariant;
import dev.masterball.api.card.Rarity;
import dev.masterball.api.item.CardItems;
import dev.tcgshop.grading.Grading;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Admin commands to give specific cards (with whatever Grade and variant you want), packs and random
 * cards, and to view the data of the card in hand. Everything hangs off "/tcgshop card" and requires permission level 2.
 *
 * <pre>
 *  /tcgshop card give &lt;players&gt; &lt;card&gt; [grade] [variant] [count]
 *  /tcgshop card givepack &lt;players&gt; &lt;set&gt; [sealed|normal|semigod|god] [count]
 *  /tcgshop card random &lt;players&gt; [family|any] [count]
 *  /tcgshop card info
 * </pre>
 * &lt;card&gt; accepts the full id (masterball:&lt;set&gt;/cards/&lt;leaf&gt;), the short one (&lt;set&gt;/&lt;leaf&gt;), the legacy one
 * (cobbletcg:...) or just the leaf if it is unique. [grade] 1.0-10.0 with one decimal; 0 = ungraded.
 */
public final class CardCommands {
    private static final int MAX_SUGGESTIONS = 200;
    private static final String[] PACK_TIERS = {"sealed", "normal", "semigod", "god"};

    private static final DynamicCommandExceptionType UNKNOWN_CARD = new DynamicCommandExceptionType(
            o -> Text.translatable("tcgshop.cmd.card.unknown", String.valueOf(o)));
    private static final DynamicCommandExceptionType UNKNOWN_VARIANT = new DynamicCommandExceptionType(
            o -> Text.translatable("tcgshop.cmd.card.bad_variant", String.valueOf(o)));
    private static final DynamicCommandExceptionType UNKNOWN_SET = new DynamicCommandExceptionType(
            o -> Text.translatable("tcgshop.cmd.card.unknown_set", String.valueOf(o)));
    private static final DynamicCommandExceptionType UNKNOWN_TIER = new DynamicCommandExceptionType(
            o -> Text.translatable("tcgshop.cmd.card.bad_tier", String.valueOf(o)));
    private static final DynamicCommandExceptionType UNKNOWN_FAMILY = new DynamicCommandExceptionType(
            o -> Text.translatable("tcgshop.cmd.card.bad_family", String.valueOf(o)));
    private static final SimpleCommandExceptionType BAD_GRADE = new SimpleCommandExceptionType(
            Text.translatable("tcgshop.cmd.card.bad_grade"));
    private static final SimpleCommandExceptionType NO_CARDS = new SimpleCommandExceptionType(
            Text.translatable("tcgshop.cmd.card.no_cards"));

    /** A catalog card for suggestions (all lowercase for comparison). */
    private record Entry(String full, String shortId, String leaf, String nameLower, String display) {}

    private static List<Entry> entries;
    private static Map<Rarity.Family, List<Item>> byFamily;
    private static List<Item> allCards;

    private CardCommands() {}

    // ------------------------------------------------------------------ tree

    /** "card" node to attach under "/tcgshop" (see the hook in TcgShop.registerCommands). */
    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("card").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("give")
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .then(CommandManager.argument("card", IdentifierArgumentType.identifier()).suggests(CARD_SUGGESTIONS)
                                        .executes(c -> give(c, 0, null, 1))
                                        .then(CommandManager.argument("grade", DoubleArgumentType.doubleArg(0, 10))
                                                .executes(c -> give(c, DoubleArgumentType.getDouble(c, "grade"), null, 1))
                                                .then(CommandManager.argument("variant", StringArgumentType.word()).suggests(VARIANT_SUGGESTIONS)
                                                        .executes(c -> give(c, DoubleArgumentType.getDouble(c, "grade"),
                                                                StringArgumentType.getString(c, "variant"), 1))
                                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                                                .executes(c -> give(c, DoubleArgumentType.getDouble(c, "grade"),
                                                                        StringArgumentType.getString(c, "variant"),
                                                                        IntegerArgumentType.getInteger(c, "count")))))))))
                .then(CommandManager.literal("givepack")
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .then(CommandManager.argument("set", StringArgumentType.word()).suggests(SET_SUGGESTIONS)
                                        .executes(c -> givePack(c, "sealed", 1))
                                        .then(CommandManager.argument("tier", StringArgumentType.word()).suggests(TIER_SUGGESTIONS)
                                                .executes(c -> givePack(c, StringArgumentType.getString(c, "tier"), 1))
                                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                                        .executes(c -> givePack(c, StringArgumentType.getString(c, "tier"),
                                                                IntegerArgumentType.getInteger(c, "count"))))))))
                .then(CommandManager.literal("random")
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .executes(c -> giveRandom(c, null, 1))
                                .then(CommandManager.argument("family", StringArgumentType.word()).suggests(FAMILY_SUGGESTIONS)
                                        .executes(c -> giveRandom(c, StringArgumentType.getString(c, "family"), 1))
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> giveRandom(c, StringArgumentType.getString(c, "family"),
                                                        IntegerArgumentType.getInteger(c, "count")))))))
                .then(CommandManager.literal("info").executes(CardCommands::info));
    }

    // ------------------------------------------------------------------ give

    private static int give(CommandContext<ServerCommandSource> c, double grade, @Nullable String variantKey, int count) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(c, "targets");
        Identifier raw = IdentifierArgumentType.getIdentifier(c, "card");
        Identifier id = resolveCard(raw);
        if (id == null) throw UNKNOWN_CARD.create(raw.toString());
        Item item = CardInfo.item(id);
        if (item == null) throw UNKNOWN_CARD.create(raw.toString());

        int tenths = 0;
        if (grade > 0) {
            if (grade < 1.0) throw BAD_GRADE.create();
            tenths = Math.max(10, Math.min(100, (int) Math.round(grade * 10)));
        }
        PrintVariant variant = PrintVariant.STANDARD;
        if (variantKey != null) {
            variant = PrintVariant.byKey(variantKey.toLowerCase(Locale.ROOT));
            if (variant == null) throw UNKNOWN_VARIANT.create(variantKey);
        }

        ServerCommandSource src = c.getSource();
        ItemStack base = PrintVariant.apply(new ItemStack(item), variant);
        for (ServerPlayerEntity p : targets) {
            if (tenths > 0) {
                // each graded card is unique: its own certificate number
                for (int i = 0; i < count; i++)
                    p.getInventory().offerOrDrop(Grading.apply(base, tenths, CertNumbers.next(src.getServer())));
            } else {
                giveSplit(p, base, count);
            }
        }

        Text cardText = base.toHoverableText();
        MutableText extra = Text.empty();
        if (variant != PrintVariant.STANDARD) extra.append(Text.literal(" · ")).append(variant.displayName());
        if (tenths > 0) extra.append(Text.literal(" · ")).append(Grading.tooltip(tenths));
        feedback(src, targets, "give", count, Text.empty().append(cardText).append(extra));
        return count * targets.size();
    }

    // ------------------------------------------------------------------ givepack

    private static int givePack(CommandContext<ServerCommandSource> c, String tierKey, int count) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(c, "targets");
        String set = StringArgumentType.getString(c, "set").toLowerCase(Locale.ROOT);
        if (CardCatalog.set(set) == null) throw UNKNOWN_SET.create(set);
        int tier = -1;
        for (int i = 0; i < PACK_TIERS.length; i++) if (PACK_TIERS[i].equalsIgnoreCase(tierKey)) tier = i;
        if (tier < 0) throw UNKNOWN_TIER.create(tierKey);
        Identifier packId = tier == 0 ? CardInfo.sealedOf(set) : CardInfo.rippedOf(set, tier);
        Item item = packId == null ? null : CardInfo.item(packId);
        if (item == null) throw UNKNOWN_SET.create(set);

        ItemStack base = new ItemStack(item);
        for (ServerPlayerEntity p : targets) giveSplit(p, base, count);
        Text packText = Text.empty().append(base.toHoverableText()).append(Text.literal(" · "))
                .append(Text.translatable("tcgshop.cmd.card.tier." + PACK_TIERS[tier]));
        feedback(c.getSource(), targets, "givepack", count, packText);
        return count * targets.size();
    }

    // ------------------------------------------------------------------ random

    private static int giveRandom(CommandContext<ServerCommandSource> c, @Nullable String familyKey, int count) throws CommandSyntaxException {
        Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(c, "targets");
        loadFamilies();
        List<Item> pool;
        Text label;
        if (familyKey == null || familyKey.equalsIgnoreCase("any")) {
            pool = allCards;
            label = Text.translatable("tcgshop.cmd.card.any_family");
        } else {
            Rarity.Family family = null;
            for (Rarity.Family f : Rarity.Family.values()) if (f.key.equalsIgnoreCase(familyKey)) family = f;
            if (family == null) throw UNKNOWN_FAMILY.create(familyKey);
            pool = byFamily.getOrDefault(family, List.of());
            label = family.displayName().copy().withColor(family.color & 0xFFFFFF);
        }
        if (pool.isEmpty()) throw NO_CARDS.create();

        Random r = c.getSource().getWorld().getRandom();
        for (ServerPlayerEntity p : targets) {
            for (int i = 0; i < count; i++) p.getInventory().offerOrDrop(new ItemStack(pool.get(r.nextInt(pool.size()))));
        }
        feedback(c.getSource(), targets, "random", count, label);
        return count * targets.size();
    }

    // ------------------------------------------------------------------ info

    private static int info(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerCommandSource src = c.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        ItemStack st = p.getMainHandStack();
        if (CardInfo.isPack(st)) {
            Identifier id = CardInfo.id(st);
            int tier = CardInfo.packTier(st);
            CardCatalog.SetInfo set = CardCatalog.set(CardInfo.setId(id));
            src.sendFeedback(() -> Text.translatable("tcgshop.cmd.card.info.header", st.toHoverableText()).formatted(Formatting.GOLD), false);
            line(src, "id", Text.literal(id.toString()));
            line(src, "set", Text.literal(set == null ? CardInfo.setId(id) : set.name() + " (" + set.code() + ")"));
            line(src, "pack_tier", Text.translatable("tcgshop.cmd.card.tier." + PACK_TIERS[Math.max(0, Math.min(3, tier))]));
            return 1;
        }
        if (!CardInfo.isCard(st)) {
            src.sendError(Text.translatable("tcgshop.cmd.notcard"));
            return 0;
        }
        Identifier id = CardInfo.id(st);
        String setId = CardInfo.setId(id);
        CardCatalog.SetInfo set = CardCatalog.set(setId);
        CardCatalog.CardDef def = CardCatalog.card(setId, CardInfo.cardId(id));
        Rarity rarity = Rarity.of(st);
        PrintVariant variant = PrintVariant.of(st);
        int g = Grading.grade(st);

        src.sendFeedback(() -> Text.translatable("tcgshop.cmd.card.info.header", st.toHoverableText()).formatted(Formatting.GOLD), false);
        line(src, "id", Text.literal(id.toString()));
        String setText = (set == null ? setId : set.name() + " (" + set.code() + ")")
                + (def != null && !def.number().isEmpty() ? " #" + def.number() : "");
        line(src, "set", Text.literal(setText));
        line(src, "rarity", Text.empty().append(rarity.displayName().copy().withColor(rarity.color & 0xFFFFFF))
                .append(Text.literal(" · ")).append(rarity.family.displayName().copy().withColor(rarity.family.color & 0xFFFFFF)));
        line(src, "variant", variant.displayName());
        if (g > 0) line(src, "grade", Text.empty().append(Grading.tooltip(g))
                .append(Text.translatable("tcgshop.cmd.card.info.cert", Grading.cert(st)).formatted(Formatting.GRAY)));
        else line(src, "grade", Text.translatable("tcgshop.cmd.card.info.ungraded").formatted(Formatting.GRAY));
        long price = PriceService.priceCd(st);
        line(src, "price", Text.literal(PriceService.format(price)).formatted(Formatting.GREEN));
        if (st.getCount() > 1)
            line(src, "price_stack", Text.literal(PriceService.format(price * st.getCount())).formatted(Formatting.GREEN));
        return 1;
    }

    private static void line(ServerCommandSource src, String key, Text value) {
        src.sendFeedback(() -> Text.translatable("tcgshop.cmd.card.info." + key, value).formatted(Formatting.GRAY), false);
    }

    // ------------------------------------------------------------------ utilities

    /** Gives count copies respecting the max stack size (overflow dropped on the ground via offerOrDrop). */
    private static void giveSplit(ServerPlayerEntity p, ItemStack base, int count) {
        int max = Math.max(1, base.getMaxCount());
        int left = count;
        while (left > 0) {
            int n = Math.min(max, left);
            p.getInventory().offerOrDrop(base.copyWithCount(n));
            left -= n;
        }
    }

    /** "Gave 3 × X to Steve" / "... to 4 players". */
    private static void feedback(ServerCommandSource src, Collection<ServerPlayerEntity> targets, String kind, int count, Text what) {
        if (targets.size() == 1) {
            Text who = targets.iterator().next().getDisplayName();
            src.sendFeedback(() -> Text.translatable("tcgshop.cmd.card." + kind + ".one", count, what, who).formatted(Formatting.GREEN), true);
        } else {
            int n = targets.size();
            src.sendFeedback(() -> Text.translatable("tcgshop.cmd.card." + kind + ".many", count, what, n).formatted(Formatting.GREEN), true);
        }
    }

    /**
     * Converts the typed input into the id of a registered card, or null:
     * masterball:set/cards/leaf · cobbletcg:set/cards/leaf · set/cards/leaf · set/leaf · masterball:set/leaf · leaf (if unique).
     */
    @Nullable
    static Identifier resolveCard(Identifier raw) {
        String ns = raw.getNamespace();
        if (!ns.equals(Identifier.DEFAULT_NAMESPACE) && !ns.equals(CardInfo.NS) && !ns.equals(CardInfo.LEGACY_NS)) return null;
        String path = raw.getPath();
        Identifier id = null;
        if (path.contains("/cards/")) {
            id = Identifier.of(CardInfo.NS, path);
        } else {
            int slash = path.indexOf('/');
            if (slash > 0 && slash == path.lastIndexOf('/')) {
                id = Identifier.of(CardInfo.NS, path.substring(0, slash) + "/cards/" + path.substring(slash + 1));
            } else if (slash < 0) {
                // leaf only: valid if it is not repeated in another set
                Entry found = null;
                for (Entry e : entries()) {
                    if (!e.leaf.equals(path)) continue;
                    if (found != null) return null;
                    found = e;
                }
                if (found != null) id = Identifier.of(found.full);
            }
        }
        if (id == null || !CardInfo.isCard(id) || !Registries.ITEM.containsId(id)) return null;
        return id;
    }

    private static synchronized List<Entry> entries() {
        if (entries != null) return entries;
        List<Entry> out = new ArrayList<>();
        for (CardCatalog.SetInfo s : CardCatalog.sets()) {
            for (CardCatalog.CardDef d : s.cards().values()) {
                String shortId = s.id() + "/" + d.leaf();
                String display = d.name() + " · " + s.name() + (d.number().isEmpty() ? "" : " #" + d.number());
                out.add(new Entry(CardInfo.NS + ":" + s.id() + "/cards/" + d.leaf(), shortId.toLowerCase(Locale.ROOT),
                        d.leaf().toLowerCase(Locale.ROOT), d.name().toLowerCase(Locale.ROOT), display));
            }
        }
        entries = Collections.unmodifiableList(out);
        return entries;
    }

    private static synchronized void loadFamilies() {
        if (byFamily != null) return;
        Map<Rarity.Family, List<Item>> map = new EnumMap<>(Rarity.Family.class);
        List<Item> all = new ArrayList<>();
        for (Item it : CardItems.cards()) {
            Identifier id = Registries.ITEM.getId(it);
            all.add(it);
            map.computeIfAbsent(Rarity.of(id).family, k -> new ArrayList<>()).add(it);
        }
        allCards = Collections.unmodifiableList(all);
        byFamily = map;
    }

    // ------------------------------------------------------------------ suggestions

    /** Filters the ~12,500 cards by id or name; those starting with the typed text first. Max 200. */
    private static final SuggestionProvider<ServerCommandSource> CARD_SUGGESTIONS = (ctx, builder) -> suggestCards(builder);

    private static CompletableFuture<Suggestions> suggestCards(SuggestionsBuilder builder) {
        String q = builder.getRemaining().toLowerCase(Locale.ROOT).trim();
        if (q.startsWith(CardInfo.LEGACY_NS + ":")) q = CardInfo.NS + q.substring(CardInfo.LEGACY_NS.length());
        String bare = q.startsWith(CardInfo.NS + ":") ? q.substring(CardInfo.NS.length() + 1) : q;
        Set<String> added = new HashSet<>();
        int n = 0;
        // 1st pass: the id, short id or name starts with the typed text
        for (Entry e : entries()) {
            if (n >= MAX_SUGGESTIONS) break;
            if (e.full.startsWith(q) || e.shortId.startsWith(bare) || e.leaf.startsWith(bare) || e.nameLower.startsWith(bare)) {
                builder.suggest(e.full, Text.literal(e.display));
                added.add(e.full);
                n++;
            }
        }
        // 2nd pass: contains it anywhere
        if (!bare.isEmpty()) {
            for (Entry e : entries()) {
                if (n >= MAX_SUGGESTIONS) break;
                if (added.contains(e.full)) continue;
                if (e.shortId.contains(bare) || e.nameLower.contains(bare) || e.full.contains(q)) {
                    builder.suggest(e.full, Text.literal(e.display));
                    n++;
                }
            }
        }
        return builder.buildFuture();
    }

    private static final SuggestionProvider<ServerCommandSource> VARIANT_SUGGESTIONS = (ctx, builder) ->
            CommandSource.suggestMatching(List.of(PrintVariant.values()), builder, v -> v.key, PrintVariant::displayName);

    private static final SuggestionProvider<ServerCommandSource> SET_SUGGESTIONS = (ctx, builder) ->
            CommandSource.suggestMatching(CardCatalog.sets(), builder, CardCatalog.SetInfo::id, s -> Text.literal(s.name() + " (" + s.code() + ")"));

    private static final SuggestionProvider<ServerCommandSource> TIER_SUGGESTIONS = (ctx, builder) ->
            CommandSource.suggestMatching(List.of(PACK_TIERS), builder, t -> t, t -> Text.translatable("tcgshop.cmd.card.tier." + t));

    private static final SuggestionProvider<ServerCommandSource> FAMILY_SUGGESTIONS = (ctx, builder) -> {
        List<String> keys = new ArrayList<>();
        keys.add("any");
        for (Rarity.Family f : Rarity.Family.values()) keys.add(f.key);
        return CommandSource.suggestMatching(keys, builder, k -> k, k -> {
            for (Rarity.Family f : Rarity.Family.values()) if (f.key.equals(k)) return f.displayName();
            return Text.translatable("tcgshop.cmd.card.any_family");
        });
    };
}
