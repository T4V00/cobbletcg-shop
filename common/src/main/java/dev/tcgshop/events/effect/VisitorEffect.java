package dev.tcgshop.events.effect;

import com.google.gson.JsonObject;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.EmployeeEntity;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventInstance;
import dev.tcgshop.events.EventManager;
import dev.tcgshop.events.EventRegistry;
import dev.tcgshop.events.EventsConfig;
import dev.tcgshop.events.SetHelper;
import dev.tcgshop.events.VisitorSpec;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopLife;
import dev.tcgshop.shop.ShopZones;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * P4 "visitor": special customers that walk in like the others (they respect capacity and the queue; if the shop is
 * full, it retries). Max price ~1.15x market and they are NOT announced before arriving (only after the visit).
 * When they are all done: optional result (outcome) by stars or by inspection. See EFFECTS.md.
 */
public final class VisitorEffect implements EventEffect {
    final CustomerEntity.Type type;
    final long[] count, delay, gap, browses, purchases;
    final int moodStart, minStars, linesCount;
    final double patienceMult, buyMult, priceCap;
    final String filter, title, lines, selfTag;
    final boolean troublemaker, inspector, fairSeller, noBuy, regular, resolveOnVisit;
    @Nullable
    final Outcome outcome;

    record Result(int rep, double spawn, int charity, long ticks, long money) {
        static Result parse(@Nullable JsonObject o) {
            if (o == null) return new Result(0, 1, 0, 0, 0);
            return new Result(Math.max(-4, Math.min(6, EventDef.integer(o, "rep", 0))), EventDef.clamp(EventDef.dbl(o, "spawn", 1), 0.5, 1.5),
                    Math.max(-3, Math.min(3, EventDef.integer(o, "charity", 0))), Math.max(0, Math.min(36_000, EventDef.integer(o, "ticks", 0))),
                    Math.max(0, EventDef.integer(o, "money", 0)));
        }
    }

    record Outcome(String grade, double goodAt, double badAt, Result good, Result bad) {}

    public VisitorEffect(JsonObject o, String defId) {
        try {
            type = CustomerEntity.Type.valueOf(EventDef.str(o, "customer", "REGULAR").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown visitor.customer");
        }
        if (type == CustomerEntity.Type.THIEF) throw new IllegalArgumentException("visitor: thieves go through typeW, not as a visitor");
        count = EventDef.range(o.get("count"), 1);
        count[0] = Math.max(1, Math.min(6, count[0]));
        count[1] = Math.max(count[0], Math.min(6, count[1]));
        delay = o.has("delay") ? EventDef.range(o.get("delay"), 200) : new long[]{100, 600};
        gap = o.has("gap") ? EventDef.range(o.get("gap"), 60) : new long[]{40, 160};
        browses = o.has("browses") ? EventDef.range(o.get("browses"), -1) : new long[]{-1, -1};
        purchases = o.has("purchases") ? EventDef.range(o.get("purchases"), 1) : new long[]{1, 1};
        moodStart = Math.max(-3, Math.min(3, EventDef.integer(o, "moodStart", 0)));
        patienceMult = EventDef.clamp(EventDef.dbl(o, "patienceMult", 1), 0.3, 2);
        buyMult = EventDef.clamp(EventDef.dbl(o, "buyMult", 1), 0, 2);
        priceCap = EventDef.clamp(EventDef.dbl(o, "priceCap", -1), -1, 1.3);
        filter = EventDef.str(o, "filter", "");
        title = EventDef.str(o, "title", "");
        lines = EventDef.str(o, "lines", "");
        linesCount = Math.max(0, EventDef.integer(o, "linesCount", lines.isEmpty() ? 0 : 6));
        selfTag = EventDef.str(o, "selfTag", "");
        troublemaker = EventDef.bool(o, "troublemaker", false);
        inspector = EventDef.bool(o, "inspector", false);
        fairSeller = EventDef.bool(o, "fairSeller", false);
        noBuy = EventDef.bool(o, "noBuy", inspector);
        regular = EventDef.bool(o, "regular", false);
        resolveOnVisit = EventDef.bool(o, "resolveOnVisit", false);
        minStars = Math.max(1, Math.min(5, EventDef.integer(o, "minStars", 3)));
        if (!List.of("", "most_stocked_set", "old_sets", "newest_set", "packs", "cards").contains(filter))
            throw new IllegalArgumentException("unknown visitor.filter: " + filter);
        if (o.has("outcome")) {
            JsonObject oc = o.getAsJsonObject("outcome");
            String grade = EventDef.str(oc, "grade", inspector ? "inspection" : "stars");
            if (!grade.equals("stars") && !grade.equals("inspection")) throw new IllegalArgumentException("invalid visitor.outcome.grade");
            outcome = new Outcome(grade, EventDef.dbl(oc, "goodAt", grade.equals("inspection") ? 5 : 4), EventDef.dbl(oc, "badAt", grade.equals("inspection") ? 3 : 2),
                    Result.parse(oc.has("good") ? oc.getAsJsonObject("good") : null), Result.parse(oc.has("bad") ? oc.getAsJsonObject("bad") : null));
        } else outcome = null;
    }

    private static long roll(java.util.Random r, long[] range) {
        return range[0] + (range[1] > range[0] ? r.nextInt((int) (range[1] - range[0] + 1)) : 0);
    }

    @Override
    public void start(EventContext ctx) {
        CompoundTag d = ctx.data();
        d.putInt("left", (int) roll(ctx.rng, count));
        d.putLong("next", ctx.now() + roll(ctx.rng, delay));
        d.put("vis", new ListTag());
        d.put("fin", new ListTag());
    }

    @Override
    public void tick(EventContext ctx) {
        CompoundTag d = ctx.data();
        if (d.getBoolean("complete")) return;
        long now = ctx.now();
        if (d.getInt("left") > 0 && now >= d.getLong("next")) {
            CustomerEntity c = ShopLife.forceSpawnVisitor(ctx.world, ctx.zone, spec(ctx));
            if (c != null) {
                d.putInt("left", d.getInt("left") - 1);
                d.putInt("spawned", d.getInt("spawned") + 1);
                d.getList("vis", Tag.TAG_STRING).add(StringTag.valueOf(c.getStringUUID()));
                if (!d.contains("vis")) d.put("vis", new ListTag());
                ctx.instance.visitors.add(c.getUUID());
                d.putLong("next", now + roll(ctx.rng, gap));
                if (inspector) ctx.tellOwner(Component.translatable("tcgshop.event.visitor.inspector_here").withStyle(ChatFormatting.GOLD), false);
            } else d.putLong("next", now + 200);
            ctx.markDirty();
        }
        // visitors who are no longer here (they left, the world reloaded...): count as finished, never recreated
        ListTag vis = d.getList("vis", Tag.TAG_STRING), fin = d.getList("fin", Tag.TAG_STRING);
        for (int i = vis.size() - 1; i >= 0; i--) {
            UUID u;
            try { u = UUID.fromString(vis.getString(i)); } catch (IllegalArgumentException e) { vis.remove(i); continue; }
            Entity e = ctx.world.getEntity(u);
            if (troublemaker && e != null && e.isAlive() && !ctx.instance.resolved()) troublemakerTick(ctx, e);
            if (e == null || !e.isAlive()) {
                vis.remove(i);
                ctx.instance.visitors.remove(u);
                if (!fin.contains(StringTag.valueOf(u.toString()))) d.putInt("done", d.getInt("done") + 1);
                ctx.markDirty();
            }
        }
        d.put("vis", vis);
        if (d.getInt("left") <= 0 && d.getInt("spawned") > 0 && d.getInt("done") >= d.getInt("spawned")) complete(ctx);
    }

    /** The troublemaker customer: the guard calms them down only after they have been inside a while; meanwhile, they grumble. */
    private void troublemakerTick(EventContext ctx, Entity e) {
        CompoundTag d = ctx.data();
        if (!ctx.zone.contains(e.blockPosition())) return;
        if (!d.contains("inside")) d.putLong("inside", ctx.now());
        if (ctx.now() % 200 < 20) {
            if (e instanceof CustomerEntity c && ctx.rng.nextFloat() < 0.5f)
                dev.tcgshop.entity.NpcLines.speak(c, Component.translatable("tcgshop.evline.rude." + ctx.rng.nextInt(8)));
            ctx.world.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, e.getX(), e.getY() + 2.1, e.getZ(), 2, 0.3, 0.2, 0.3, 0);
        }
        if (ctx.zone.guard && ctx.now() - d.getLong("inside") > 600) {
            List<EmployeeEntity> g = ctx.world.getEntitiesOfClass(EmployeeEntity.class, ctx.zone.box().inflate(16), x -> x.isGuard() && ctx.zone.id.equals(x.zoneId()));
            if (!g.isEmpty()) {
                g.get(0).announce(Component.translatable("tcgshop.evline.guard_calms." + ctx.rng.nextInt(4)));
                ctx.resolve("guard");
            }
        }
    }

    private VisitorSpec spec(EventContext ctx) {
        VisitorSpec s = new VisitorSpec();
        s.type = type;
        s.eventUid = ctx.instance.uid;
        s.effectIndex = ctx.index;
        s.moodStart = moodStart;
        s.patienceMult = patienceMult;
        s.buyMult = noBuy ? 0 : buyMult;
        s.priceCap = priceCap > 0 ? priceCap : EventsConfig.INSTANCE.visitorPriceCap;
        s.browses = (int) roll(ctx.rng, browses);
        s.purchases = (int) Math.max(1, roll(ctx.rng, purchases));
        s.filter = switch (filter) {
            case "most_stocked_set" -> mostStockedSet(ctx);
            case "" -> null;
            default -> filter;
        };
        s.troublemaker = troublemaker;
        s.inspector = inspector;
        s.fairSeller = fairSeller;
        s.noBuy = noBuy;
        s.titleKey = title;
        s.lines = lines;
        s.linesCount = linesCount;
        if (regular && !ctx.zone.regulars.isEmpty()) s.regular = ctx.zone.regulars.get(ctx.rng.nextInt(ctx.zone.regulars.size()));
        return s;
    }

    /** "set:&lt;id&gt;" of the set with the most products on display (or null). */
    @Nullable
    private static String mostStockedSet(EventContext ctx) {
        ShopZones.Zone z = ctx.zone;
        Map<String, Integer> n = new HashMap<>();
        int half = z.size() / 2;
        for (int cx = (z.core.getX() - half) >> 4; cx <= (z.core.getX() + half) >> 4; cx++)
            for (int cz = (z.core.getZ() - half) >> 4; cz <= (z.core.getZ() + half) >> 4; cz++) {
                LevelChunk ch = ctx.world.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof ShopDisplayBlockEntity d) || d.isHome() || !z.contains(be.getBlockPos())) continue;
                    for (int i = 0; i < d.getContainerSize(); i++) {
                        ItemStack st = d.getItem(i);
                        String set = SetHelper.setOf(st);
                        if (set != null) n.merge(set, st.getCount(), Integer::sum);
                    }
                }
            }
        String best = null;
        int bn = 0;
        for (Map.Entry<String, Integer> e : n.entrySet()) if (e.getValue() > bn) { bn = e.getValue(); best = e.getKey(); }
        return best == null ? null : "set:" + best;
    }

    /** Called by EventManager.visitorFinished when a visitor leaves their review. */
    public EventManager.VisitorResult finished(EventContext ctx, CustomerEntity c, VisitorSpec spec, int stars) {
        CompoundTag d = ctx.data();
        ListTag fin = d.getList("fin", Tag.TAG_STRING);
        if (fin.contains(StringTag.valueOf(c.getStringUUID()))) return new EventManager.VisitorResult(null, 0);
        fin.add(StringTag.valueOf(c.getStringUUID()));
        d.put("fin", fin);
        String key = selfTag.isEmpty() ? null : "ev_" + selfTag;
        int forced = 0;
        if (inspector) {
            int score = inspectionScore(ctx);
            d.putInt("score", score);
            boolean good = score >= (outcome == null ? 5 : outcome.goodAt()), bad = score <= (outcome == null ? 3 : outcome.badAt());
            key = good ? "ev_inspect_good" : bad ? "ev_inspect_bad" : null;
            forced = good ? 5 : bad ? 2 : 3;
            stars = forced;
        }
        d.putInt("done", d.getInt("done") + 1);
        d.putInt("starSum", d.getInt("starSum") + stars);
        d.putInt("starN", d.getInt("starN") + 1);
        ctx.markDirty();
        return new EventManager.VisitorResult(key, forced);
    }

    /** Inspection score (0-7): smell, light, monsters, roof and walls, stocked displays, no other unfixed problems, reviews. */
    public static int inspectionScore(EventContext ctx) {
        ShopZones.Zone z = ctx.zone;
        int s = 0;
        if (z.smell <= 10) s++;
        if (!z.flags.contains("dark")) s++;
        if (!z.flags.contains("hostile")) s++;
        if (!z.flags.contains("no_roof") && !z.flags.contains("no_walls")) s++;
        if (!z.flags.contains("empty")) s++;
        boolean other = false;
        for (EventInstance e : z.events.active) {
            if (e.uid == ctx.instance.uid || e.resolved()) continue;
            EventDef d = EventRegistry.get(e.defId);
            if (d != null && d.negative()) other = true;
        }
        if (!other) s++;
        if (z.reviews.size() < 3 || z.avgStars() >= 3.5) s++;
        return s;
    }

    private void complete(EventContext ctx) {
        CompoundTag d = ctx.data();
        d.putBoolean("complete", true);
        ctx.markDirty();
        int n = d.getInt("starN");
        double avg = n > 0 ? d.getInt("starSum") / (double) n : -1;
        boolean good = false, bad = false;
        if (outcome != null) {
            double score = outcome.grade().equals("inspection") ? (d.contains("score") ? d.getInt("score") : -1) : avg;
            good = score >= 0 && score >= outcome.goodAt();
            bad = score >= 0 && score <= outcome.badAt();
            Result r = good ? outcome.good() : bad ? outcome.bad() : null;
            if (r != null) {
                if (r.ticks() > 0) {
                    ctx.instance.data.putInt("o_rep", ctx.instance.data.getInt("o_rep") + r.rep());
                    if (r.spawn() != 1) ctx.instance.data.putDouble("o_spawn", r.spawn());
                    long until = ctx.now() + r.ticks();
                    ctx.instance.data.putLong("o_until", until);
                    ctx.instance.end = Math.max(ctx.instance.end, until);
                }
                if (r.charity() != 0) ctx.zone.charity = Math.max(-10, Math.min(10, ctx.zone.charity + r.charity()));
                if (r.money() > 0) EventManager.grantMoney(ctx.world, ctx.zone, r.money(), true);
                ctx.invalidate();
            }
        }
        // announcement AFTER the visit (never before: so prices cannot be raised for the collector)
        String res = good ? "good" : bad ? "bad" : "neutral";
        ctx.tellOwner(Component.translatable("tcgshop.event.visitor.done_" + res, EventManager.name(ctx.def))
                .withStyle(good ? ChatFormatting.GREEN : bad ? ChatFormatting.RED : ChatFormatting.GRAY), false);
        if (ctx.instance.resolved()) return;
        if (inspector) { // the inspection is over: "resolved" in both cases (the result, good or bad, lasts o_until)
            ctx.resolve(bad ? "failed_inspection" : "inspected");
            return;
        }
        if (resolveOnVisit) {
            if (avg >= minStars) ctx.resolve("visit");
            else EventManager.end(ctx.instance.uid, "failed");
            return;
        }
        if (troublemaker) { EventManager.end(ctx.instance.uid, "left"); return; }
        boolean lasting = (good && outcome.good().ticks() > 0) || (bad && outcome.bad().ticks() > 0);
        if (!lasting) EventManager.end(ctx.instance.uid, "done");
    }

    @Override
    public boolean providesFix() { return troublemaker || resolveOnVisit || inspector; }

    @Override
    public boolean keepsAfterResolve() {
        return outcome != null && (outcome.good().ticks() > 0 || outcome.bad().ticks() > 0);
    }

    @Override
    public java.util.Set<String> implicitRequires() { return java.util.Set.of(); }
}
