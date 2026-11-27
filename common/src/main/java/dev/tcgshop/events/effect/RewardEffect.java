package dev.tcgshop.events.effect;

import com.google.gson.JsonObject;
import dev.tcgshop.events.EventContext;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventManager;
import dev.tcgshop.shop.ShopZones;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * P6 "reward": SMALL rewards, always with the per-owner money cap (EventManager.grantMoney). Never packs,
 * boxes or graded cards: items only come from tcgshop:events/* loot tables (food, berries, decoration).
 * when: start | resolve | end. See EFFECTS.md for all fields.
 */
public final class RewardEffect implements EventEffect {
    final String when, scale, loot, rank;
    final long moneyMin, moneyMax, perUnit;
    final int charity, rolls, tempRep, tempRepMax, minDecor, rankMinRep;
    final String tempRepScale;
    final boolean smellReset;
    final double tip;

    public RewardEffect(JsonObject o, String defId) {
        when = EventDef.str(o, "when", "start");
        if (!List.of("start", "resolve", "end").contains(when)) throw new IllegalArgumentException("invalid reward.when: " + when);
        long[] m = EventDef.range(o.get("money"), 0);
        moneyMin = Math.max(0, m[0]);
        moneyMax = Math.max(moneyMin, m[1]);
        scale = EventDef.str(o, "scale", "");
        perUnit = Math.max(0, EventDef.integer(o, "perUnit", 0));
        charity = Math.max(-3, Math.min(3, EventDef.integer(o, "charity", 0)));
        smellReset = EventDef.bool(o, "smellReset", false);
        loot = EventDef.str(o, "loot", "");
        rolls = Math.max(1, Math.min(3, EventDef.integer(o, "rolls", 1)));
        tip = EventDef.clamp(EventDef.dbl(o, "tip", 0), 0, 0.5);
        tempRep = Math.max(0, Math.min(6, EventDef.integer(o, "tempRep", 0)));
        tempRepScale = EventDef.str(o, "tempRepScale", "");
        tempRepMax = Math.max(0, Math.min(6, EventDef.integer(o, "tempRepMax", 5)));
        minDecor = EventDef.integer(o, "minDecor", 0);
        rank = EventDef.str(o, "rank", "");
        rankMinRep = EventDef.integer(o, "rankMinRep", 60);
        if (!loot.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(loot);
            if (id == null || !id.getNamespace().equals("tcgshop") || !id.getPath().startsWith("events/"))
                throw new IllegalArgumentException("reward.loot can only use tcgshop:events/* tables");
        }
        if (!List.of("", "regulars", "decor", "displays").contains(scale)) throw new IllegalArgumentException("invalid reward.scale");
        if (!List.of("", "regulars", "decor", "displays").contains(tempRepScale)) throw new IllegalArgumentException("invalid reward.tempRepScale");
        if (!List.of("", "rep").contains(rank)) throw new IllegalArgumentException("invalid reward.rank");
    }

    @Override
    public void start(EventContext ctx) {
        if (tip > 0) EventManager.addPerk(ctx.world.getServer(), ctx.zone.owner, ctx.instance.uid, "tip", tip, 1, ctx.instance.end);
        if (when.equals("start")) give(ctx);
    }

    @Override
    public void resolve(EventContext ctx, String how) {
        if (when.equals("resolve")) give(ctx);
    }

    @Override
    public void end(EventContext ctx) {
        if (when.equals("end") && !ctx.data().getBoolean("given")) give(ctx);
    }

    private int units(String s, ShopZones.Zone z) {
        return switch (s) {
            case "regulars" -> z.regulars.size();
            case "decor" -> z.decor;
            case "displays" -> z.displays;
            default -> 1;
        };
    }

    private void give(EventContext ctx) {
        if (ctx.data().getBoolean("given")) return;
        ctx.data().putBoolean("given", true);
        ShopZones.Zone z = ctx.zone;
        if (z.decor < minDecor) {
            ctx.tellOwner(Component.translatable("tcgshop.event.reward.not_enough_decor", z.decor, minDecor).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        if (rank.equals("rep") && !isTop(ctx)) return;
        long money = 0;
        if (perUnit > 0) money = Math.min(moneyMax > 0 ? moneyMax : Long.MAX_VALUE, (long) units(scale, z) * perUnit);
        else if (moneyMax > 0) money = moneyMin + (moneyMax > moneyMin ? (long) (ctx.rng.nextDouble() * (moneyMax - moneyMin)) : 0);
        if (money > 0) EventManager.grantMoney(ctx.world, z, money, true);
        if (charity != 0) {
            z.charity = Math.max(-10, Math.min(10, z.charity + charity));
            ctx.markDirty();
        }
        if (smellReset && z.smell > 0) {
            z.smell = 0;
            ctx.markDirty();
        }
        if (tempRep > 0 || !tempRepScale.isEmpty()) {
            int r = tempRepScale.isEmpty() ? tempRep : Math.min(tempRepMax, 1 + units(tempRepScale, z) / 4);
            ctx.instance.data.putInt("o_rep", ctx.instance.data.getInt("o_rep") + r);
            ctx.instance.data.putLong("o_until", Math.max(ctx.instance.data.getLong("o_until"), ctx.instance.end));
            ctx.invalidate();
        }
        if (!loot.isEmpty()) giveLoot(ctx);
    }

    /** Is it the shop with the best reputation among those of other experienced owners (one per owner)? Alone in the world: reputation &gt;= rankMinRep. */
    private boolean isTop(EventContext ctx) {
        Map<UUID, Integer> best = new HashMap<>();
        for (ServerLevel w : ctx.world.getServer().getAllLevels())
            for (ShopZones.Zone o : ShopZones.get(w).all())
                if (o.sales >= 20) best.merge(o.owner, o.reputation, Math::max);
        int mine = best.getOrDefault(ctx.zone.owner, ctx.zone.reputation);
        int place = 1;
        for (Map.Entry<UUID, Integer> e : best.entrySet()) if (!e.getKey().equals(ctx.zone.owner) && e.getValue() > mine) place++;
        boolean top = best.size() <= 1 ? mine >= rankMinRep : place == 1;
        ctx.tellOwner(Component.translatable(top ? "tcgshop.event.reward.rank_top" : "tcgshop.event.reward.rank_place", place, best.size())
                .withStyle(top ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return top;
    }

    private void giveLoot(EventContext ctx) {
        ServerPlayer owner = ctx.owner();
        if (owner == null) return;
        LootTable table = lootTable(ctx.world.getServer(), ResourceLocation.parse(loot));
        if (table == LootTable.EMPTY) return;
        LootParams params = new LootParams.Builder(ctx.world)
                .withParameter(LootContextParams.ORIGIN, ctx.zone.core.getCenter())
                .create(LootContextParamSets.CHEST);
        int n = 0;
        for (int k = 0; k < rolls; k++) {
            for (ItemStack st : table.getRandomItems(params)) {
                if (st.isEmpty() || !safe(st)) continue;
                n += st.getCount();
                owner.getInventory().placeItemBackInInventory(st);
            }
        }
        if (n > 0) owner.displayClientMessage(Component.translatable("tcgshop.event.reward.items", n).withStyle(ChatFormatting.GREEN), false);
    }

    /**
     * tcgshop:events/* table by id; if it was not loaded (e.g. events/berries has a Fabric condition and only exists
     * with Cobblemon) uses its substitute with vanilla items (berries -&gt; fruit). LootTable.EMPTY if there is none either.
     * Public so other modules (actors, incidents) give the same loot.
     */
    public static LootTable lootTable(net.minecraft.server.MinecraftServer server, ResourceLocation id) {
        LootTable t = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        if (t != LootTable.EMPTY) return t;
        String fb = FALLBACK.get(id.getPath());
        if (fb == null) {
            dev.tcgshop.TcgShop.LOGGER.debug("[events] loot table {} not loaded: no item reward", id);
            return LootTable.EMPTY;
        }
        return server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), fb)));
    }

    /** Tables with items from other mods -&gt; vanilla substitute of the same kind. */
    private static final Map<String, String> FALLBACK = Map.of("events/berries", "events/fruit");

    /** No cards, packs, boxes or shop items: only small things even if someone edits the table. */
    private static boolean safe(ItemStack st) {
        if (dev.masterball.api.card.CardInfo.isCard(st) || dev.masterball.api.card.CardInfo.isPack(st)) return false;
        String ns = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace();
        return !ns.equals("tcgshop") && !ns.equals(dev.masterball.api.card.CardInfo.NS);
    }
}
