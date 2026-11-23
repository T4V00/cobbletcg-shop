package dev.tcgshop.events;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * /tcgshop event (operator): list | start &lt;id&gt; [variant] | end &lt;uid&gt; | log | deck [reroll] | roll.
 * start works even if events are disabled or the frequency is 0 (it does not check conditions or the deck).
 */
public final class EventCommands {
    private EventCommands() {}

    private static final SuggestionProvider<ServerCommandSource> IDS = (ctx, b) ->
            CommandSource.suggestIdentifiers(EventRegistry.view().keySet().stream().map(Identifier::tryParse).filter(java.util.Objects::nonNull), b);

    private static final SuggestionProvider<ServerCommandSource> VARIANTS = (ctx, b) -> {
        EventDef d = EventRegistry.get(IdentifierArgumentType.getIdentifier(ctx, "id").toString());
        if (d != null) for (EventDef.Variant v : d.variants()) b.suggest(v.key());
        return b.buildFuture();
    };

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return CommandManager.literal("event").requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("list").executes(EventCommands::list))
                .then(CommandManager.literal("start")
                        .then(CommandManager.argument("id", IdentifierArgumentType.identifier()).suggests(IDS)
                                .executes(ctx -> start(ctx, null))
                                .then(CommandManager.argument("variant", StringArgumentType.word()).suggests(VARIANTS)
                                        .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "variant"))))))
                .then(CommandManager.literal("end")
                        .then(CommandManager.argument("uid", LongArgumentType.longArg(1)).executes(ctx -> {
                            long uid = LongArgumentType.getLong(ctx, "uid");
                            boolean ok = EventManager.end(uid, "command");
                            ctx.getSource().sendFeedback(() -> Text.translatable(ok ? "tcgshop.event.cmd.ended" : "tcgshop.event.cmd.unknown", uid)
                                    .formatted(ok ? Formatting.GREEN : Formatting.RED), true);
                            return ok ? 1 : 0;
                        })))
                .then(CommandManager.literal("log").executes(EventCommands::log))
                .then(CommandManager.literal("deck").executes(ctx -> deck(ctx, false))
                        .then(CommandManager.literal("reroll").executes(ctx -> deck(ctx, true))))
                .then(CommandManager.literal("roll").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    Text t = EventManager.forceRoll(p);
                    ctx.getSource().sendFeedback(() -> t.copy().formatted(Formatting.GREEN), true);
                    return 1;
                }));
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        int n = 0;
        for (ServerWorld w : src.getServer().getWorlds()) {
            long now = w.getTime();
            for (ShopZones.Zone z : ShopZones.get(w).all())
                for (EventInstance e : z.events.active) {
                    n++;
                    EventDef d = EventRegistry.get(e.defId);
                    MutableText line = Text.literal("#" + e.uid + " ").formatted(Formatting.GRAY)
                            .append(d == null ? Text.literal(e.defId) : EventManager.name(d).formatted(EventManager.color(d.category())))
                            .append(Text.literal(" (" + e.defId + (e.variant.isEmpty() ? "" : "/" + e.variant) + ") · " + z.ownerName + " " + z.core.toShortString()
                                    + " · " + Math.max(0, (e.end - now) / 20) + "s" + (e.resolved() ? " · ✔ " + e.resolvedHow : "")).formatted(Formatting.GRAY));
                    src.sendFeedback(() -> line, false);
                }
        }
        int total = n;
        src.sendFeedback(() -> Text.translatable("tcgshop.event.cmd.list", total, EventRegistry.all().size(), EventRegistry.lastErrors().size())
                .formatted(Formatting.AQUA), false);
        for (String err : EventRegistry.lastErrors()) src.sendFeedback(() -> Text.literal("  ✗ " + err).formatted(Formatting.RED), false);
        return total;
    }

    private static int start(CommandContext<ServerCommandSource> ctx, String variant) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        String id = IdentifierArgumentType.getIdentifier(ctx, "id").toString();
        EventDef def = EventRegistry.get(id);
        if (def == null) {
            src.sendError(Text.translatable("tcgshop.event.cmd.no_def", id));
            return 0;
        }
        ShopZones.Zone mine = EventManager.nearestZone(p, false);
        final ShopZones.Zone z = mine != null ? mine : EventManager.nearestZone(p, true);
        if (z == null) {
            src.sendError(Text.translatable("tcgshop.event.cmd.no_shop"));
            return 0;
        }
        if (variant != null && def.variants().stream().noneMatch(v -> v.key().equals(variant))) {
            src.sendError(Text.translatable("tcgshop.event.cmd.no_variant", variant));
            return 0;
        }
        StringBuilder why = new StringBuilder();
        EventState st = EventState.get(src.getServer());
        long day = EventManager.today(src.getServer());
        boolean ok = EventManager.eligible(p.getServerWorld(), z, def, st.owner(z.owner), day, st, src.getServer().getOverworld().getSeed(), why);
        EventInstance inst = EventManager.start(p.getServerWorld(), z, def, variant, new Random(), true);
        if (inst == null) {
            src.sendError(Text.translatable("tcgshop.event.cmd.failed", id));
            return 0;
        }
        String w = why.toString();
        src.sendFeedback(() -> Text.translatable("tcgshop.event.cmd.started", EventManager.name(def), inst.uid, z.ownerName)
                .formatted(Formatting.GREEN), true);
        if (!ok) src.sendFeedback(() -> Text.translatable("tcgshop.event.cmd.not_eligible", w).formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int log(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity p = src.getPlayerOrThrow();
        ShopZones.Zone z = EventManager.nearestZone(p, true);
        EventState st = EventState.get(src.getServer());
        EventState.OwnerData od = st.owner(z == null ? p.getUuid() : z.owner);
        long day = EventManager.today(src.getServer());
        src.sendFeedback(() -> Text.translatable("tcgshop.event.cmd.roll_state", day, od.rollDay, od.rollHit ? "✔" : "✗", od.fireTod, od.firedToday,
                EventManager.moneyLeft(src.getServer(), z == null ? p.getUuid() : z.owner)).formatted(Formatting.AQUA), false);
        for (int i = Math.max(0, od.hist.size() - 10); i < od.hist.size(); i++) {
            EventState.Hist h = od.hist.get(i);
            src.sendFeedback(() -> Text.literal("  " + (day - h.day()) + "d · " + h.id()).formatted(Formatting.GRAY), false);
        }
        if (z == null) return 1;
        long now = p.getServerWorld().getTime();
        for (IncidentRecord r : z.events.log)
            src.sendFeedback(() -> Text.literal("  log #" + r.uid + " " + r.id + " [" + r.tag + "] " + (now - r.start) / 20 + "s ago, "
                    + (r.resolvedAt >= 0 ? "fixed " + (now - r.resolvedAt) / 20 + "s ago" : "unfixed") + ", customers " + r.customersDuring)
                    .formatted(r.negative ? Formatting.RED : Formatting.GREEN), false);
        for (ZoneEvents.Hist h : z.events.hist)
            src.sendFeedback(() -> Text.literal("  " + (day - h.day()) + "d · " + h.id() + " → " + h.outcome()).formatted(Formatting.DARK_GRAY), false);
        return 1;
    }

    private static int deck(CommandContext<ServerCommandSource> ctx, boolean reroll) {
        ServerCommandSource src = ctx.getSource();
        EventState st = EventState.get(src.getServer());
        if (reroll) st.rerollDeck(new Random().nextLong());
        long seed = src.getServer().getOverworld().getSeed();
        Map<EventDef.Tier, int[]> count = new EnumMap<>(EventDef.Tier.class);
        StringBuilder out = new StringBuilder();
        for (EventDef d : EventRegistry.all()) {
            boolean in = st.inDeck(d, seed);
            int[] c = count.computeIfAbsent(d.tier(), k -> new int[2]);
            c[in ? 0 : 1]++;
            if (!in) out.append(d.id()).append(' ');
        }
        for (Map.Entry<EventDef.Tier, int[]> e : count.entrySet())
            src.sendFeedback(() -> Text.translatable("tcgshop.event.cmd.deck_tier", e.getKey().key(), e.getValue()[0], e.getValue()[0] + e.getValue()[1])
                    .formatted(Formatting.AQUA), false);
        String missing = out.toString().trim();
        if (!missing.isEmpty()) src.sendFeedback(() -> Text.translatable("tcgshop.event.cmd.deck_missing", missing).formatted(Formatting.GRAY), false);
        if (reroll) src.sendFeedback(() -> Text.translatable("tcgshop.event.cmd.deck_rerolled").formatted(Formatting.GREEN), true);
        return 1;
    }
}
