package dev.tcgshop.events.incident;

import dev.tcgshop.events.EffectTypes;
import dev.tcgshop.events.EventDef;
import dev.tcgshop.events.EventEffect;
import dev.tcgshop.events.EventInstance;
import dev.tcgshop.events.EventManager;
import dev.tcgshop.events.EventRegistry;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Módulo de incidentes (charcos, escombros, chispas, quemaduras...): bloque tcgshop:incident, herramientas para
 * arreglarlos (tcgshop:mop, tcgshop:repair_kit) y el tipo de efecto "incident". Contrato: dev/tcgshop/events/EFFECTS.md.
 *
 * API para el núcleo: {@link #cleanupZone} (barrido barato de incidentes huérfanos de una tienda; pensado para el
 * recálculo de ShopLife, crítica E4).
 */
public final class IncidentModule {
    private IncidentModule() {}

    /** true mientras quitamos nosotros un incidente (para que onStateReplaced no lo cuente como "arreglado"). */
    private static boolean silent;

    /** Servidor y común (TcgShop.onInitialize, antes de cargar los datapacks). */
    public static void init() {
        EffectTypes.register("incident", IncidentEffect::new);
    }

    /** Cliente (TcgShopClient.onInitializeClient): capa de render translúcida del bloque. */
    public static void initClient() {
        IncidentClient.init();
    }

    // ------------------------------------------------------------------ limpieza

    /** Quita un incidente sin avisar a nadie (sin vecinos, sin drops, sin contarlo como arreglado). */
    public static void removeSilently(ServerWorld w, BlockPos pos) {
        if (!w.getBlockState(pos).isOf(TcgBlocks.INCIDENT)) return;
        silent = true;
        try {
            w.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        } finally {
            silent = false;
        }
    }

    /**
     * Barrido de incidentes huérfanos de una tienda (eventos ya terminados o arreglados, tiendas borradas): recorre
     * sólo los bloques-entidad de los chunks CARGADOS de la zona, así que es barato. Lo llama el efecto en cada
     * recálculo; el núcleo puede llamarlo también tras ShopLife.recompute para tiendas sin eventos.
     */
    public static int cleanupZone(ServerWorld w, ShopZones.Zone z) {
        if (EventManager.server() == null) return 0;
        List<BlockPos> orphans = new ArrayList<>();
        for (BlockEntity be : IncidentPlacer.zoneBlockEntities(w, z))
            if (be instanceof IncidentBlockEntity ie && !ie.stillWanted(w)) orphans.add(be.getPos());
        for (BlockPos p : orphans) removeSilently(w, p);
        return orphans.size();
    }

    /** El bloque desaparece por otra vía (creativo, comando, wither...): cuenta como arreglado, sin premio. */
    static void onBlockGone(ServerWorld w, BlockPos pos, IncidentBlockEntity be) {
        if (silent || be.eventUid < 0 || !EventManager.isUnresolved(be.eventUid)) return;
        EventInstance inst = EventManager.instance(be.eventUid);
        ShopZones.Zone z = zoneOf(w, be, pos);
        if (inst == null || z == null) return;
        EventDef def = EventRegistry.get(inst.defId);
        IncidentEffect eff = effectOf(def, inst, be.effectIndex);
        if (eff != null) eff.onFixed(w, z, inst, def, be.effectIndex, pos, "removed", null);
    }

    // ------------------------------------------------------------------ arreglar (clic del dueño)

    static void onPlayerUse(ServerWorld w, BlockPos pos, BlockState state, ServerPlayerEntity p, ItemStack stack, Hand hand) {
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
        // crítica C3: ShopProtection no cubre onUse; sólo el dueño (u op en creativo) arregla o abre
        if (!z.owner.equals(p.getUuid()) && !(p.isCreative() && p.hasPermissionLevel(2))) {
            p.sendMessage(Text.translatable("tcgshop.incident.not_owner").formatted(Formatting.RED), true);
            return;
        }
        IncidentKind kind = state.get(IncidentBlock.KIND);
        // el paquete es un premio del dueño: un op no lo abre por él (ni se lleva el botín ni gasta su tope semanal)
        if (kind == IncidentKind.PACKAGE && !z.owner.equals(p.getUuid())) {
            p.sendMessage(Text.translatable("tcgshop.incident.not_owner").formatted(Formatting.RED), true);
            return;
        }
        EventDef def = EventRegistry.get(inst.defId);
        IncidentEffect eff = effectOf(def, inst, be.effectIndex);
        IncidentEffect.FixSpec spec = eff != null ? eff.fix : IncidentEffect.FixSpec.defaults(kind);
        // mano principal vacía: si no basta la mano pero la herramienta está en la otra mano, se usa esa
        if (stack.isEmpty() && hand == Hand.MAIN_HAND && !spec.matches(stack)) {
            ItemStack off = p.getOffHandStack();
            if (!off.isEmpty() && spec.matches(off)) {
                stack = off;
                hand = Hand.OFF_HAND;
            }
        }
        if (!spec.matches(stack)) {
            p.sendMessage(Text.translatable("tcgshop.incident.need." + kind.key()).formatted(Formatting.YELLOW), true);
            return;
        }
        if (!stack.isEmpty() && spec.uses() > 0) {
            if (stack.isDamageable()) stack.damage(spec.uses(), p, LivingEntity.getSlotForHand(hand));
            else if (spec.consume()) stack.decrementUnlessCreative(spec.uses(), p);
        }
        p.swingHand(hand, true);
        be.hitsLeft--;
        be.markDirty();
        feedback(w, pos, state, kind, be.hitsLeft <= 0);
        if (be.hitsLeft > 0) {
            p.sendMessage(Text.translatable("tcgshop.incident.hits_left", be.hitsLeft).formatted(Formatting.YELLOW), true);
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
        if (st.isOf(dev.tcgshop.item.TcgItems.MOP)) return "mop";
        if (st.isOf(dev.tcgshop.item.TcgItems.REPAIR_KIT)) return "repair_kit";
        if (st.isIn(net.minecraft.registry.tag.ItemTags.SHOVELS)) return "shovel";
        return Registries.ITEM.getId(st.getItem()).getPath();
    }

    private static void feedback(ServerWorld w, BlockPos pos, BlockState state, IncidentKind kind, boolean last) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.15, z = pos.getZ() + 0.5;
        SoundEvent s = switch (kind) {
            case PUDDLE, DROPPINGS -> SoundEvents.BLOCK_WET_GRASS_BREAK;
            case DIRT -> SoundEvents.ITEM_SHOVEL_FLATTEN;
            case DEBRIS -> SoundEvents.BLOCK_GRAVEL_BREAK;
            case SCORCH, SPARKS -> SoundEvents.BLOCK_SMITHING_TABLE_USE;
            case MESS -> SoundEvents.BLOCK_SCAFFOLDING_BREAK;
            case PACKAGE -> SoundEvents.BLOCK_BARREL_OPEN;
        };
        w.playSound(null, pos, s, SoundCategory.BLOCKS, 0.7f, 0.9f + w.random.nextFloat() * 0.3f);
        w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), x, y, z, last ? 12 : 5, 0.3, 0.05, 0.3, 0.05);
        if (last) {
            w.spawnParticles(kind == IncidentKind.PACKAGE ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.POOF, x, y + 0.2, z, 4, 0.25, 0.1, 0.25, 0.01);
            if (kind != IncidentKind.PACKAGE) w.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.4f, 1.4f);
        }
    }

    // ------------------------------------------------------------------ utilidades

    static ShopZones.Zone zoneOf(ServerWorld w, IncidentBlockEntity be, BlockPos pos) {
        ShopZones zs = ShopZones.get(w);
        ShopZones.Zone z = be.zoneId != null ? zs.byId(be.zoneId) : null;
        return z != null ? z : zs.at(pos);
    }

    static IncidentEffect effectOf(EventDef def, EventInstance inst, int index) {
        if (def == null || index < 0) return null;
        List<EventEffect> effs = def.effectsFor(inst.variant);
        return index < effs.size() && effs.get(index) instanceof IncidentEffect ie ? ie : null;
    }

    /** Premios del paquete: nada de cartas, sobres, cajas ni objetos de la tienda aunque alguien edite la tabla. */
    static boolean safeLoot(ItemStack st) {
        if (dev.masterball.api.card.CardInfo.isCard(st) || dev.masterball.api.card.CardInfo.isPack(st)) return false;
        String ns = Registries.ITEM.getId(st.getItem()).getNamespace();
        return !ns.equals("tcgshop") && !ns.equals(dev.masterball.api.card.CardInfo.NS);
    }
}
