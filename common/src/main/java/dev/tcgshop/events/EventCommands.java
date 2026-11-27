package dev.tcgshop.events;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.tcgshop.shop.ShopZones;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /tcgshop event (operator): list | start &lt;id&gt; [variant] | end &lt;uid&gt; | log | deck [reroll] | roll.
 * start works even if events are disabled or the frequency is 0 (it does not check conditions or the deck).
 */
public final class EventCommands {
    private EventCommands() {}

    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, b) ->
            SharedSuggestionProvider.suggestResource(EventRegistry.view().keySet().stream().map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull), b);

    private static final SuggestionProvider<CommandSourceStack> VARIANTS = (ctx, b) -> {
        EventDef d = EventRegistry.get(ResourceLocationArgument.getId(ctx, "id").toString());
        if (d != null) for (EventDef.Variant v : d.variants()) b.suggest(v.key());
        return b.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("event").requires(src -> src.hasPermission(2))
                .then(Commands.literal("list").executes(EventCommands::list))
                .then(Commands.literal("start")
                        .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(IDS)
                                .executes(ctx -> start(ctx, null))
                                .then(Commands.argument("variant", StringArgumentType.word()).suggests(VARIANTS)
                                        .executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "variant"))))))
                .then(Commands.literal("end")
                        .then(Commands.argument("uid", LongArgumentType.longArg(1)).executes(ctx -> {
                            long uid = LongArgumentType.getLong(ctx, "uid");
                            boolean ok = EventManager.end(uid, "command");
                            ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "tcgshop.event.cmd.ended" : "tcgshop.event.cmd.unknown", uid)
                                    .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("log").executes(EventCommands::log))
                .then(Commands.literal("deck").executes(ctx -> deck(ctx, false))
                        .then(Commands.literal("reroll").executes(ctx -> deck(ctx, true))))
                .then(Commands.literal("roll").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Component t = EventManager.forceRoll(p);
                    ctx.getSource().sendSuccess(() -> t.copy().withStyle(ChatFormatting.GREEN), true);
                    return 1;
                }));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int n = 0;
        for (ServerLevel w : src.getServer().getAllLevels()) {
            long now = w.getGameTime();
            for (ShopZones.Zone z : ShopZones.get(w).all())
                for (EventInstance e : z.events.active) {
                    n++;
                    EventDef d = EventRegistry.get(e.defId);
                    MutableComponent line = Component.literal("#" + e.uid + " ").withStyle(ChatFormatting.GRAY)
                            .append(d == null ? Component.literal(e.defId) : EventManager.name(d).withStyle(EventManager.color(d.category())))
                            .append(Component.literal(" (" + e.defId + (e.variant.isEmpty() ? "" : "/" + e.variant) + ") · " + z.ownerName + " " + z.core.toShortString()
                                    + " · " + Math.max(0, (e.end - now) / 20) + "s" + (e.resolved() ? " · ✔ " + e.resolvedHow : "")).withStyle(ChatFormatting.GRAY));
                    src.sendSuccess(() -> line, false);
                }
        }
        int total = n;
        src.sendSuccess(() -> Component.translatable("tcgshop.event.cmd.list", total, EventRegistry.all().size(), EventRegistry.lastErrors().size())
                .withStyle(ChatFormatting.AQUA), false);
        for (String err : EventRegistry.lastErrors()) src.sendSuccess(() -> Component.literal("  ✗ " + err).withStyle(ChatFormatting.RED), false);
        return total;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, String variant) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayerOrException();
        String id = ResourceLocationArgument.getId(ctx, "id").toString();
        EventDef def = EventRegistry.get(id);
        if (def == null) {
            src.sendFailure(Component.translatable("tcgshop.event.cmd.no_def", id));
            return 0;
        }
        ShopZones.Zone mine = EventManager.nearestZone(p, false);
        final ShopZones.Zone z = mine != null ? mine : EventManager.nearestZone(p, true);
        if (z == null) {
            src.sendFailure(Component.translatable("tcgshop.event.cmd.no_shop"));
            return 0;
        }
        if (variant != null && def.variants().stream().noneMatch(v -> v.key().equals(variant))) {
            src.sendFailure(Component.translatable("tcgshop.event.cmd.no_variant", variant));
            return 0;
        }
        StringBuilder why = new StringBuilder();
        EventState st = EventState.get(src.getServer());
        long day = EventManager.today(src.getServer());
        boolean ok = EventManager.eligible(p.serverLevel(), z, def, st.owner(z.owner), day, st, src.getServer().overworld().getSeed(), why);
        EventInstance inst = EventManager.start(p.serverLevel(), z, def, variant, new Random(), true);
        if (inst == null) {
            src.sendFailure(Component.translatable("tcgshop.event.cmd.failed", id));
            return 0;
        }
        String w = why.toString();
        src.sendSuccess(() -> Component.translatable("tcgshop.event.cmd.started", EventManager.name(def), inst.uid, z.ownerName)
                .withStyle(ChatFormatting.GREEN), true);
        if (!ok) src.sendSuccess(() -> Component.translatable("tcgshop.event.cmd.not_eligible", w).withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int log(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer p = src.getPlayerOrException();
        ShopZones.Zone z = EventManager.nearestZone(p, true);
        EventState st = EventState.get(src.getServer());
        EventState.OwnerData od = st.owner(z == null ? p.getUUID() : z.owner);
        long day = EventManager.today(src.getServer());
        src.sendSuccess(() -> Component.translatable("tcgshop.event.cmd.roll_state", day, od.rollDay, od.rollHit ? "✔" : "✗", od.fireTod, od.firedToday,
                EventManager.moneyLeft(src.getServer(), z == null ? p.getUUID() : z.owner)).withStyle(ChatFormatting.AQUA), false);
        for (int i = Math.max(0, od.hist.size() - 10); i < od.hist.size(); i++) {
            EventState.Hist h = od.hist.get(i);
            src.sendSuccess(() -> Component.literal("  " + (day - h.day()) + "d · " + h.id()).withStyle(ChatFormatting.GRAY), false);
        }
        if (z == null) return 1;
        long now = p.serverLevel().getGameTime();
        for (IncidentRecord r : z.events.log)
            src.sendSuccess(() -> Component.literal("  log #" + r.uid + " " + r.id + " [" + r.tag + "] " + (now - r.start) / 20 + "s ago, "
                    + (r.resolvedAt >= 0 ? "fixed " + (now - r.resolvedAt) / 20 + "s ago" : "unfixed") + ", customers " + r.customersDuring)
                    .withStyle(r.negative ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        for (ZoneEvents.Hist h : z.events.hist)
            src.sendSuccess(() -> Component.literal("  " + (day - h.day()) + "d · " + h.id() + " → " + h.outcome()).withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int deck(CommandContext<CommandSourceStack> ctx, boolean reroll) {
        CommandSourceStack src = ctx.getSource();
        EventState st = EventState.get(src.getServer());
        if (reroll) st.rerollDeck(new Random().nextLong());
        long seed = src.getServer().overworld().getSeed();
        Map<EventDef.Tier, int[]> count = new EnumMap<>(EventDef.Tier.class);
        StringBuilder out = new StringBuilder();
        for (EventDef d : EventRegistry.all()) {
            boolean in = st.inDeck(d, seed);
            int[] c = count.computeIfAbsent(d.tier(), k -> new int[2]);
            c[in ? 0 : 1]++;
            if (!in) out.append(d.id()).append(' ');
        }
        for (Map.Entry<EventDef.Tier, int[]> e : count.entrySet())
            src.sendSuccess(() -> Component.translatable("tcgshop.event.cmd.deck_tier", e.getKey().key(), e.getValue()[0], e.getValue()[0] + e.getValue()[1])
                    .withStyle(ChatFormatting.AQUA), false);
        String missing = out.toString().trim();
        if (!missing.isEmpty()) src.sendSuccess(() -> Component.translatable("tcgshop.event.cmd.deck_missing", missing).withStyle(ChatFormatting.GRAY), false);
        if (reroll) src.sendSuccess(() -> Component.translatable("tcgshop.event.cmd.deck_rerolled").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
