package dev.tcgshop.events.incident;

import dev.tcgshop.events.EffectTypes;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventInstance;
import dev.tcgshop.events.EventManager;
import dev.tcgshop.events.EventRegistry;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Incident module (puddles, debris, sparks, scorch marks...): tcgshop:incident block, tools to
 * fix them (tcgshop:mop, tcgshop:repair_kit) and the "incident" effect type. Contract: dev/tcgshop/events/EFFECTS.md.
 *
 * API for the core: {@link #cleanupZone} (cheap sweep of a shop's orphaned incidents; meant for the
 * ShopLife recompute, critique E4).
 */
public final class IncidentModule {
    private IncidentModule() {}

    /** true while we ourselves are removing an incident (so onStateReplaced does not count it as "fixed"). */
    private static boolean silent;

    /** Server and common (TcgShop.onInitialize, before datapacks load). */
    public static void init() {
        EffectTypes.register("incident", IncidentEffect::new);
    }

    /** Client (TcgShopClient.onInitializeClient): translucent render layer for the block. */
    public static void initClient() {
        IncidentClient.init();
    }

    // ------------------------------------------------------------------ cleanup

    /** Removes an incident without notifying anyone (no neighbors, no drops, not counted as fixed). */
    public static void removeSilently(ServerLevel w, BlockPos pos) {
        if (!w.getBlockState(pos).is(TcgBlocks.INCIDENT)) return;
        silent = true;
        try {
            w.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        } finally {
            silent = false;
        }
    }

    /**
     * Sweep of a shop's orphaned incidents (events already ended or fixed, deleted shops): walks
     * only the block entities in the zone's LOADED chunks, so it is cheap. The effect calls it on every
     * recompute; the core can also call it after ShopLife.recompute for shops without events.
     */
    public static int cleanupZone(ServerLevel w, ShopZones.Zone z) {
        if (EventManager.server() == null) return 0;
        List<BlockPos> orphans = new ArrayList<>();
        for (BlockEntity be : IncidentPlacer.zoneBlockEntities(w, z))
            if (be instanceof IncidentBlockEntity ie && !ie.stillWanted(w)) orphans.add(be.getBlockPos());
        for (BlockPos p : orphans) removeSilently(w, p);
        return orphans.size();
    }

    /**
     * 1.1 (cleanliness update): floor-mess incidents (puddle, dirt, droppings, mess) still waiting in the zone, only
     * from its loaded chunks. Each counts like one dropping in the cleanliness score (Cleanliness.visibleDirt).
     */
    public static int floorDirt(ServerLevel w, ShopZones.Zone z) {
        int n = 0;
        for (BlockEntity be : IncidentPlacer.zoneBlockEntities(w, z)) {
            if (!(be instanceof IncidentBlockEntity ie)) continue;
            BlockState st = be.getBlockState();
            if (!st.is(TcgBlocks.INCIDENT)) continue;
            IncidentKind k = st.getValue(IncidentBlock.KIND);
            if ((k == IncidentKind.PUDDLE || k == IncidentKind.DIRT || k == IncidentKind.DROPPINGS || k == IncidentKind.MESS) && ie.stillWanted(w)) n++;
        }
        return n;
    }

    /** The block disappears some other way (creative, command, wither...): counts as fixed, no reward. */
    static void onBlockGone(ServerLevel w, BlockPos pos, IncidentBlockEntity be) {
        if (silent || be.eventUid < 0 || !EventManager.isUnresolved(be.eventUid)) return;
        EventInstance inst = EventManager.instance(be.eventUid);
        ShopZones.Zone z = zoneOf(w, be, pos);
        if (inst == null || z == null) return;
        EventDef def = EventRegistry.get(inst.defId);
        IncidentEffect eff = effectOf(def, inst, be.effectIndex);
        if (eff != null) eff.onFixed(w, z, inst, def, be.effectIndex, pos, "removed", null);
    }

    // ------------------------------------------------------------------ fix (owner's click)

    static void onPlayerUse(ServerLevel w, BlockPos pos, BlockState state, ServerPlayer p, ItemStack stack, InteractionHand hand) {
        if (!(w.getBlockEntity(pos) instanceof IncidentBlockEntity be) || !be.stillWanted(w)) {
            removeSilently(w, pos);
            return;
        }
        EventInstance inst = EventManager.instance(be.eventUid);
        ShopZones.Zone z = zoneOf(w, be, pos);
        if (inst == null || z == null) {
            removeSilently(w, pos);
            return;
        }
        // critique C3: ShopProtection does not cover onUse; only the owner (or an op in creative) fixes or opens
        if (!z.owner.equals(p.getUUID()) && !(p.isCreative() && p.hasPermissions(2))) {
            p.displayClientMessage(Component.translatable("tcgshop.incident.not_owner").withStyle(ChatFormatting.RED), true);
            return;
        }
        IncidentKind kind = state.getValue(IncidentBlock.KIND);
        // the package is the owner's reward: an op does not open it for them (neither takes the loot nor uses up their weekly cap)
        if (kind == IncidentKind.PACKAGE && !z.owner.equals(p.getUUID())) {
            p.displayClientMessage(Component.translatable("tcgshop.incident.not_owner").withStyle(ChatFormatting.RED), true);
            return;
        }
        EventDef def = EventRegistry.get(inst.defId);
        IncidentEffect eff = effectOf(def, inst, be.effectIndex);
        IncidentEffect.FixSpec spec = eff != null ? eff.fix : IncidentEffect.FixSpec.defaults(kind);
        // empty main hand: if the hand is not enough but the tool is in the other hand, that one is used
        if (stack.isEmpty() && hand == InteractionHand.MAIN_HAND && !spec.matches(stack)) {
            ItemStack off = p.getOffhandItem();
            if (!off.isEmpty() && spec.matches(off)) {
                stack = off;
                hand = InteractionHand.OFF_HAND;
            }
        }
        if (!spec.matches(stack)) {
            p.displayClientMessage(Component.translatable("tcgshop.incident.need." + kind.key()).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!stack.isEmpty() && spec.uses() > 0) {
            if (stack.isDamageableItem()) stack.hurtAndBreak(spec.uses(), p, LivingEntity.getSlotForHand(hand));
            else if (spec.consume()) stack.consume(spec.uses(), p);
        }
        p.swing(hand, true);
        be.hitsLeft--;
        be.setChanged();
        feedback(w, pos, state, kind, be.hitsLeft <= 0);
        if (be.hitsLeft > 0) {
            p.displayClientMessage(Component.translatable("tcgshop.incident.hits_left", be.hitsLeft).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        String how = how(stack);
        int index = be.effectIndex;
        removeSilently(w, pos);
        if (eff != null) eff.onFixed(w, z, inst, def, index, pos, how, p);
        else if (EventManager.isUnresolved(inst.uid) && IncidentEffect.allIncidentsDone(inst, def)) EventManager.resolve(inst.uid, how);
    }

    private static String how(ItemStack st) {
        if (st.isEmpty()) return "hand";
        if (st.is(dev.tcgshop.item.TcgItems.MOP)) return "mop";
        if (st.is(dev.tcgshop.item.TcgItems.REPAIR_KIT)) return "repair_kit";
        if (st.is(net.minecraft.tags.ItemTags.SHOVELS)) return "shovel";
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    private static void feedback(ServerLevel w, BlockPos pos, BlockState state, IncidentKind kind, boolean last) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.15, z = pos.getZ() + 0.5;
        SoundEvent s = switch (kind) {
            case PUDDLE, DROPPINGS -> SoundEvents.WET_GRASS_BREAK;
            case DIRT -> SoundEvents.SHOVEL_FLATTEN;
            case DEBRIS -> SoundEvents.GRAVEL_BREAK;
            case SCORCH, SPARKS -> SoundEvents.SMITHING_TABLE_USE;
            case MESS -> SoundEvents.SCAFFOLDING_BREAK;
            case PACKAGE -> SoundEvents.BARREL_OPEN;
        };
        w.playSound(null, pos, s, SoundSource.BLOCKS, 0.7f, 0.9f + w.random.nextFloat() * 0.3f);
        w.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), x, y, z, last ? 12 : 5, 0.3, 0.05, 0.3, 0.05);
        if (last) {
            w.sendParticles(kind == IncidentKind.PACKAGE ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.POOF, x, y + 0.2, z, 4, 0.25, 0.1, 0.25, 0.01);
            if (kind != IncidentKind.PACKAGE) w.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.4f);
        }
    }

    // ------------------------------------------------------------------ utilities

    static ShopZones.Zone zoneOf(ServerLevel w, IncidentBlockEntity be, BlockPos pos) {
        ShopZones zs = ShopZones.get(w);
        ShopZones.Zone z = be.zoneId != null ? zs.byId(be.zoneId) : null;
        return z != null ? z : zs.at(pos);
    }

    static IncidentEffect effectOf(EventDef def, EventInstance inst, int index) {
        if (def == null || index < 0) return null;
        List<EventEffect> effs = def.effectsFor(inst.variant);
        return index < effs.size() && effs.get(index) instanceof IncidentEffect ie ? ie : null;
    }

    /** Package rewards: no cards, packs, boxes or shop items even if someone edits the table. */
    static boolean safeLoot(ItemStack st) {
        if (dev.masterball.api.card.CardInfo.isCard(st) || dev.masterball.api.card.CardInfo.isPack(st)) return false;
        String ns = BuiltInRegistries.ITEM.getKey(st.getItem()).getNamespace();
        return !ns.equals("tcgshop") && !ns.equals(dev.masterball.api.card.CardInfo.NS);
    }
}
