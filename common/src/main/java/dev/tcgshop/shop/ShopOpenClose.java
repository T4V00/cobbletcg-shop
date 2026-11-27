package dev.tcgshop.shop;

import dev.tcgshop.TcgShop;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.pricing.PriceService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 1.1: shop OPEN / CLOSED and dormant shops.
 *  · Closed (owner's choice, Zone.closed): every customer leaves at once with no review or penalty, and nothing runs for the
 *    shop (customers, thieves, new events, shifts, wages, reputation drift, play tables, statue packs, sales). Active
 *    events are frozen (EventManager.shiftFrozen); the ones driven by people inside end quietly (EventManager.onShopClosed).
 *  · Dormant (core removed, Zone.dormant): the whole record is kept outside the active list and the core drops one item
 *    linked to it (TcgBlocks.SHOP_LINK). The owner placing that item restores the shop there (still closed); from the
 *    phone the owner can still empty its till or abandon it (which deletes it, like breaking the core used to).
 */
public final class ShopOpenClose {
    private ShopOpenClose() {}

    /** A shop found by id in some dimension (active or dormant). */
    public record Found(ServerLevel world, ShopZones zones, ShopZones.Zone zone) {}

    @Nullable
    public static Found find(MinecraftServer server, UUID id) {
        for (ServerLevel w : server.getAllLevels()) {
            ShopZones zs = ShopZones.get(w);
            ShopZones.Zone z = zs.byIdAny(id);
            if (z != null) return new Found(w, zs, z);
        }
        return null;
    }

    // ------------------------------------------------------------------ open / closed

    /** Phone / status window / sneak-click on the core: the owner opens or closes the shop. */
    public static void toggle(ServerPlayer player, UUID zoneId) {
        Found f = find(player.getServer(), zoneId);
        if (f == null || !f.zone.owner.equals(player.getUUID())) return;
        if (f.zone.dormant) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.dormant_cant_open").withStyle(ChatFormatting.RED), false);
            return;
        }
        boolean close = !f.zone.closed;
        setClosed(f.world, f.zone, close);
        player.displayClientMessage(Component.translatable(close ? "tcgshop.shop.now_closed" : "tcgshop.shop.now_open")
                .withStyle(close ? ChatFormatting.GOLD : ChatFormatting.GREEN), false);
        player.level().playSound(null, player.blockPosition(), close ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN,
                SoundSource.BLOCKS, 0.7f, 1.1f);
    }

    /** Sets the flag; closing sends everyone home. Returns whether anything changed. */
    public static boolean setClosed(ServerLevel w, ShopZones.Zone z, boolean closed) {
        if (z.closed == closed) return false;
        z.closed = closed;
        if (closed) {
            dev.tcgshop.events.EventManager.onShopClosed(w, z);
            sendCustomersAway(w, z);
            ShopLife.forgetZone(z.id);
            z.shiftSeen = null;
        }
        dev.tcgshop.events.EventManager.invalidate(z);
        ShopZones.get(w).setDirty();
        ShopServer.syncZones(w.getServer());
        return true;
    }

    /**
     * Every customer of the shop leaves right away, returning what they held, with no review, mood or reputation
     * penalty (CustomerEntity.leaveForClosing). Fleeing thieves are left alone. Cashiers go home on their own
     * (EmployeeEntity.tick); the guard stays.
     */
    public static int sendCustomersAway(ServerLevel w, ShopZones.Zone z) {
        int n = 0;
        List<CustomerEntity> cs = w.getEntitiesOfClass(CustomerEntity.class, z.box().inflate(64), c -> c.isAlive() && z.id.equals(c.zoneId()));
        for (CustomerEntity c : cs) if (c.leaveForClosing()) n++;
        return n;
    }

    // ------------------------------------------------------------------ dormant shops

    /** The core block was removed (not an upgrade): the shop goes dormant, nothing is lost. */
    public static void onCoreRemoved(ServerLevel w, ShopZones.Zone z) {
        ShopZones zones = ShopZones.get(w);
        setClosed(w, z, true);
        zones.makeDormant(z, w.getGameTime());
        TcgShop.LOGGER.info("[shop] {}'s shop {} is dormant (core removed at {})", z.ownerName, z.id, z.core.toShortString());
        ServerPlayer owner = w.getServer().getPlayerList().getPlayer(z.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("tcgshop.shop.dormant_now").withStyle(ChatFormatting.GOLD), false);
            owner.displayClientMessage(Component.translatable("tcgshop.shop.dormant_now.hint").withStyle(ChatFormatting.GRAY), false);
        }
        ShopServer.syncZones(w.getServer());
    }

    /** The core item that restores this shop (the zone's current tier, linked by id). */
    public static ItemStack linkedItem(ShopZones.Zone z) {
        ItemStack st = new ItemStack(TcgBlocks.CORE_ITEMS.get(z.tierInfo()));
        CompoundTag l = new CompoundTag();
        l.putUUID("zone", z.id);
        l.putString("owner", z.ownerName);
        l.putString("tier", z.tierInfo().key);
        l.putInt("size", z.size());
        st.set(TcgBlocks.SHOP_LINK, l);
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return st;
    }

    /**
     * The shop whose core at this position went dormant during this very tick (for the core's drop, which runs right after
     * onRemove). An older dormant shop that once stood here never matches, so no second linked core can ever drop.
     */
    @Nullable
    public static ShopZones.Zone dormantAt(ServerLevel w, BlockPos core) {
        List<ShopZones.Zone> d = ShopZones.get(w).dormant();
        long now = w.getGameTime();
        for (int i = d.size() - 1; i >= 0; i--) if (d.get(i).core.equals(core) && d.get(i).frozenAt == now) return d.get(i);
        return null;
    }

    // ------------------------------------------------------------------ the linked core's drop (exactly one per break)

    /** A core whose shop went dormant in onRemove this tick and whose linked item has not dropped yet. */
    private record PendingDrop(ServerLevel world, BlockPos pos, UUID zone) {}
    private static final List<PendingDrop> PENDING = new ArrayList<>();

    /** Server events: the end-of-tick fallback for linked cores removed without drops. */
    public static void register() {
        dev.masterball.api.platform.event.ServerEvents.TICK_END.register(ShopOpenClose::flushPendingDrops);
        dev.masterball.api.platform.event.ServerEvents.STOPPED.register(s -> PENDING.clear());
    }

    /** onRemove made the shop dormant: its linked core drops once, from getDrops right after or, failing that, at tick end. */
    public static void expectDrop(ServerLevel w, BlockPos pos, UUID zone) {
        PENDING.add(new PendingDrop(w, pos.immutable(), zone));
    }

    /** getDrops: claims the pending drop of this core (true = this call is the one that drops the linked core). */
    public static boolean takeDrop(ServerLevel w, BlockPos pos, UUID zone) {
        return PENDING.removeIf(p -> p.world == w && p.pos.equals(pos) && p.zone.equals(zone));
    }

    /**
     * End of every server tick: a core removed with no drops at all (creative mode, /setblock, destroyBlock without
     * drops, a tool that cannot harvest it) still gives its one linked core, so a dormant shop always has its key.
     */
    public static void flushPendingDrops(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        List<PendingDrop> list = new ArrayList<>(PENDING);
        PENDING.clear();
        for (PendingDrop p : list) {
            Found f = find(server, p.zone);
            if (f == null || !f.zone.dormant || f.world != p.world) continue; // restored or abandoned meanwhile
            Block.popResource(p.world, p.pos, linkedItem(f.zone));
        }
    }

    /** What placing a linked core did. */
    public enum Restore { NOT_LINKED, RESTORED, REFUSED }

    /**
     * A core item linked to a shop was placed. The owner gets the dormant shop back here (moved; still closed). Anyone
     * else is refused and keeps the item, so the owner's only way to restore the shop is never used up by someone else.
     * A stale link (shop abandoned, or not dormant) counts as a plain core.
     */
    public static Restore tryRestore(ServerLevel sw, BlockPos pos, ServerPlayer player, ShopCoreBlock placed, ItemStack stack) {
        CompoundTag link = stack.get(TcgBlocks.SHOP_LINK);
        if (link == null || !link.hasUUID("zone")) return Restore.NOT_LINKED;
        Found f = find(sw.getServer(), link.getUUID("zone"));
        if (f == null || !f.zone.dormant) return Restore.NOT_LINKED;
        ShopZones.Zone z = f.zone;
        if (!z.owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.restore_not_owner", z.ownerName).withStyle(ChatFormatting.RED), false);
            return Restore.REFUSED;
        }
        if (!ShopCoreBlock.belowShopCap(player, z.id)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.max_shops", dev.tcgshop.EconomyConfig.INSTANCE.maxShopsPerPlayer)
                    .withStyle(ChatFormatting.RED), false);
            return Restore.REFUSED;
        }
        ShopZones here = ShopZones.get(sw);
        if (here.overlaps(ShopZones.boxFor(pos, z.size(), z.up()))) { // the zone itself is dormant, so never in the way
            player.displayClientMessage(Component.translatable("tcgshop.shop.overlap").withStyle(ChatFormatting.RED), false);
            return Restore.REFUSED;
        }
        boolean moved = f.world != sw || !z.core.equals(pos);
        long frozen = z.frozenAt;
        // its frozen events, settled against the OLD world before the zone moves (so incident blocks are removed where
        // they really are): negative unfixed ones end with their normal penalty ("expired"); the rest resume on the same
        // spot or end quietly when it moved (EventManager.onShopRestoring)
        dev.tcgshop.events.EventManager.onShopRestoring(f.world, z, moved);
        // a guard of the old spot that has not ticked away yet is removed, so the respawn below never doubles him
        if (z.guard) dev.tcgshop.entity.EmployeeEntity.removeFor(f.world, z, false);
        f.zones.removeDormant(z);
        if (moved) Cleanliness.forgetSite(f.world, z); // its droppings / graffiti stay behind: removed where loaded, forgotten
        here.restore(z, pos);
        if (!moved) {
            if (frozen >= 0) dev.tcgshop.events.EventManager.shiftFrozen(sw, z, Math.max(0, sw.getGameTime() - frozen));
            dev.tcgshop.events.EventManager.onShopRestoredInPlace(sw, z);
        }
        long day = sw.getDayTime() / 24000;
        if (z.lastWageDay >= 0) z.lastWageDay = day; // dormant days are not paid
        z.lastCalc = -1;
        here.setDirty();
        // the block always matches the shop's tier
        ShopCoreBlock want = TcgBlocks.CORES.get(z.tierInfo());
        if (want != null && want != placed) sw.setBlock(pos, want.defaultBlockState(), Block.UPDATE_ALL);
        // the guard was discarded while the shop was dormant: he is back on duty (wages are paid for him)
        if (z.guard) dev.tcgshop.entity.EmployeeEntity.spawnMissingGuards(sw, z); // 1.1: every hired guard
        player.displayClientMessage(Component.translatable("tcgshop.shop.restored", Component.translatable("tcgshop.shop.tier." + z.tierInfo().key), z.size(), z.size())
                .withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.translatable("tcgshop.shop.restored.hint").withStyle(ChatFormatting.GRAY), false);
        sw.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
        TcgShop.LOGGER.info("[shop] {}'s shop {} restored at {}", z.ownerName, z.id, pos.toShortString());
        ShopServer.syncZones(sw.getServer());
        return Restore.RESTORED;
    }

    /** Phone: the owner gives up a dormant shop. Its till is paid out first, then it is deleted (the pre-1.1 core-break behaviour). */
    public static void abandon(ServerPlayer player, UUID zoneId) {
        Found f = find(player.getServer(), zoneId);
        if (f == null || !f.zone.owner.equals(player.getUUID())) return;
        ShopZones.Zone z = f.zone;
        if (!z.dormant) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.abandon_not_dormant").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (z.balance > 0) {
            if (!CobbleDollarsBridge.add(player, z.balance)) {
                player.displayClientMessage(Component.translatable("tcgshop.shop.no_cobbledollars").withStyle(ChatFormatting.RED), false);
                return; // nothing is deleted while its money cannot be paid out
            }
            player.displayClientMessage(Component.translatable("tcgshop.shop.collected",
                    Component.literal(PriceService.format(z.balance)).withStyle(ChatFormatting.GOLD)), false);
            z.balance = 0;
        }
        dev.tcgshop.events.EventManager.onZoneRemoved(f.world, z); // events clean up their own state before the zone is deleted
        f.zones.removeDormant(z);
        MissionLedger.forget(player.getServer(), z.id);
        player.displayClientMessage(Component.translatable("tcgshop.shop.abandoned").withStyle(ChatFormatting.YELLOW), false);
        TcgShop.LOGGER.info("[shop] {} abandoned dormant shop {}", player.getName().getString(), z.id);
        ShopServer.syncZones(player.getServer());
    }

    // ------------------------------------------------------------------ selftest

    /**
     * /tcgshop debug selftest "shop_close": NBT round trip of the new keys (and an old zone without them), closing,
     * the closed gates, going dormant, the lookups that must skip a dormant shop, the linked item and restoring.
     * Uses a throwaway zone far from spawn and removes it at the end.
     */
    public static void selfTest(ServerLevel w, BlockPos near) {
        ShopZones zones = ShopZones.get(w);
        BlockPos core = near.offset(4096, 0, 4096);
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", core, ShopTier.WOOD.rank());
        // old save without the 1.1 keys: open and active
        CompoundTag old = z.write();
        if (old.getCompound("ext").contains("closed") || old.getCompound("ext").contains("dormant")) throw new IllegalStateException("new keys written for an open shop");
        ShopZones.Zone r = ShopZones.Zone.read(old);
        if (r.closed || r.dormant || r.frozenAt != -1) throw new IllegalStateException("old zone does not load open");
        zones.add(z);
        try {
            if (zones.at(core) != z) throw new IllegalStateException("zone not found at its core");
            setClosed(w, z, true);
            if (!z.closed || !ShopZones.Zone.read(z.write()).closed) throw new IllegalStateException("closed flag not saved");
            if (ShopLife.forceSpawnCustomer(w, z, "WHALE") || ShopLife.forceSpawnVisitor(w, z, new dev.tcgshop.events.VisitorSpec()) != null)
                throw new IllegalStateException("a closed shop received a customer");
            setClosed(w, z, false);
            long balance = 12345;
            z.balance = balance;
            z.reputation = 77;
            zones.makeDormant(z, w.getGameTime());
            if (zones.at(core) != null || zones.byCore(core) != null || zones.byId(z.id) != null || zones.all().contains(z))
                throw new IllegalStateException("a dormant shop is still active");
            if (zones.overlaps(ShopZones.boxFor(core, ShopTier.WOOD)) || zones.byIdAny(z.id) != z || dormantAt(w, core) != z)
                throw new IllegalStateException("dormant lookups");
            ShopZones.Zone rd = ShopZones.Zone.read(z.write());
            if (!rd.dormant || !rd.closed || rd.balance != balance || rd.reputation != 77) throw new IllegalStateException("dormant record not saved whole");
            ItemStack link = linkedItem(z);
            CompoundTag l = link.get(TcgBlocks.SHOP_LINK);
            if (l == null || !z.id.equals(l.getUUID("zone")) || !link.is(TcgBlocks.CORE_ITEMS.get(ShopTier.WOOD))) throw new IllegalStateException("linked core item");
            BlockPos moved = core.offset(40, 0, 0);
            zones.restore(z, moved);
            if (zones.byCore(moved) != z || z.dormant || !z.closed || z.balance != balance || zones.dormant().contains(z))
                throw new IllegalStateException("restore");
        } finally {
            zones.remove(z);
            zones.removeDormant(z);
        }
        selfTestRestoreEvents(w, near);
        selfTestOtherDimensionDirty(w, near);
        selfTestCoreDrops(w, near);
    }

    /** Restoring never wipes a negative unfixed event for free (A2): classification + a real end through "expired". */
    private static void selfTestRestoreEvents(ServerLevel w, BlockPos near) {
        dev.tcgshop.events.EventDef neg = null, pos = null;
        for (dev.tcgshop.events.EventDef d : dev.tcgshop.events.EventRegistry.all()) {
            boolean plain = true; // only effects with no world side (modifiers, notices, metrics) for the real end below
            for (dev.tcgshop.events.EventEffect e : d.effectsFor(""))
                if (!(e instanceof dev.tcgshop.events.effect.ModifierEffect || e instanceof dev.tcgshop.events.effect.NotifyEffect
                        || e instanceof dev.tcgshop.events.effect.MetricEffect)) plain = false;
            if (!plain) continue;
            if (d.negative() && neg == null) neg = d;
            if (d.category() == dev.tcgshop.events.EventDef.Category.POSITIVE && pos == null) pos = d;
        }
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", near.offset(4096, 0, -4096), ShopTier.WOOD.rank());
        dev.tcgshop.events.EventInstance bad = new dev.tcgshop.events.EventInstance(-9001, neg == null ? "tcgshop:none" : neg.id(), "", z.id, z.owner, 0, Long.MAX_VALUE / 4, 0);
        if (!"removed".equals(dev.tcgshop.events.EventManager.restoreOutcome(null, bad, false))) throw new IllegalStateException("restore: unknown event");
        if (neg == null || pos == null) {
            TcgShop.LOGGER.info("[selftest] shop_close: no plain negative/positive event definition, restore-events check limited");
            return;
        }
        dev.tcgshop.events.EventInstance good = new dev.tcgshop.events.EventInstance(-9002, pos.id(), "", z.id, z.owner, 0, Long.MAX_VALUE / 4, 0);
        dev.tcgshop.events.EventInstance fixed = new dev.tcgshop.events.EventInstance(-9003, neg.id(), "", z.id, z.owner, 0, Long.MAX_VALUE / 4, 0);
        fixed.resolvedAt = 1;
        fixed.resolvedHow = "mop";
        for (boolean moved : new boolean[]{false, true})
            if (!"expired".equals(dev.tcgshop.events.EventManager.restoreOutcome(neg, bad, moved))) throw new IllegalStateException("restore: a negative unfixed event is not expired");
        if (dev.tcgshop.events.EventManager.restoreOutcome(pos, good, false) != null || dev.tcgshop.events.EventManager.restoreOutcome(neg, fixed, false) != null)
            throw new IllegalStateException("restore in place: a good / fixed event does not resume");
        if (!"closed".equals(dev.tcgshop.events.EventManager.restoreOutcome(pos, good, true))) throw new IllegalStateException("restore moved: a good event does not end quietly");
        // real run in place: the bad one ends "expired" (history), the good one keeps running
        z.events.active.add(bad);
        z.events.active.add(good);
        dev.tcgshop.events.EventManager.onShopRestoring(w, z, false);
        if (z.events.active.contains(bad) || !z.events.active.contains(good)) throw new IllegalStateException("restore in place: wrong events ended");
        if (z.events.hist.isEmpty() || !"expired".equals(z.events.hist.get(0).outcome())) throw new IllegalStateException("restore: bad event not ended as expired");
        dev.tcgshop.events.EventManager.onShopRestoring(w, z, true); // moved: the good one ends quietly
        if (!z.events.active.isEmpty() || !"closed".equals(z.events.hist.get(0).outcome())) throw new IllegalStateException("restore moved: good event not ended quietly");
    }

    /** A till change on a dormant shop in another dimension marks THAT dimension's data dirty (A3). */
    private static void selfTestOtherDimensionDirty(ServerLevel w, BlockPos near) {
        ServerLevel other = w.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (other == null) other = w;
        ShopZones zs = ShopZones.get(other);
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", near.offset(4096, 0, 8192), ShopTier.WOOD.rank());
        zs.add(z);
        try {
            zs.makeDormant(z, other.getGameTime());
            zs.setDirty(false);
            ShopZones.markDirty(w.getServer(), z);
            if (!zs.isDirty()) throw new IllegalStateException("the zone's own dimension was not marked dirty");
        } finally {
            zs.remove(z);
            zs.removeDormant(z);
            zs.setDirty();
        }
    }

    /** Exactly one linked core drops, whatever the order of drops and removal (A6). Uses real blocks near the test spot. */
    private static void selfTestCoreDrops(ServerLevel w, BlockPos near) {
        ShopZones zones = ShopZones.get(w);
        BlockPos at = near.offset(-7, 14, -9);
        ShopCoreBlock block = TcgBlocks.CORES.get(ShopTier.WOOD);
        net.minecraft.world.level.block.state.BlockState prev = w.getBlockState(at);
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", at, ShopTier.WOOD.rank());
        try {
            // 1) drops BEFORE removal (Level.destroyBlock, Wither, Create drills)
            w.setBlock(at, block.defaultBlockState(), Block.UPDATE_ALL);
            zones.add(z);
            List<ItemStack> d1 = Block.getDrops(w.getBlockState(at), w, at, null);
            if (!z.dormant || linkedCount(d1, z) != 1 || d1.size() != 1) throw new IllegalStateException("drops before removal: " + d1);
            w.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (!PENDING.isEmpty() || !zones.dormant().contains(z)) throw new IllegalStateException("removal after drops processed twice");
            zones.removeDormant(z);
            // 2) removal first, then drops (player break), then a second drop call for the same break
            z.dormant = false; z.closed = false; z.frozenAt = -1;
            w.setBlock(at, block.defaultBlockState(), Block.UPDATE_ALL);
            zones.add(z);
            net.minecraft.world.level.block.state.BlockState st = w.getBlockState(at);
            w.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (!z.dormant || PENDING.size() != 1) throw new IllegalStateException("removal did not make the shop dormant / expect its drop");
            List<ItemStack> d2 = Block.getDrops(st, w, at, null);
            List<ItemStack> d3 = Block.getDrops(st, w, at, null);
            if (linkedCount(d2, z) != 1 || d2.size() != 1 || !d3.isEmpty() || !PENDING.isEmpty()) throw new IllegalStateException("player-break drops: " + d2 + " / " + d3);
            zones.removeDormant(z);
            // 3) removal with no drops at all (creative, /setblock): the end-of-tick fallback drops it
            z.dormant = false; z.closed = false; z.frozenAt = -1;
            w.setBlock(at, block.defaultBlockState(), Block.UPDATE_ALL);
            zones.add(z);
            w.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            flushPendingDrops(w.getServer());
            List<net.minecraft.world.entity.item.ItemEntity> items = w.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(at).inflate(2), e -> e.getItem().get(TcgBlocks.SHOP_LINK) != null);
            int n = 0;
            for (net.minecraft.world.entity.item.ItemEntity e : items) {
                if (linkedCount(List.of(e.getItem()), z) == 1) n++;
                e.discard();
            }
            if (n != 1 || !PENDING.isEmpty()) throw new IllegalStateException("no-drop removal gave " + n + " linked cores");
            // a still-dormant shop's key is locked (selling, and in-place upgrades: ShopCoreBlock.useItemOn)
            if (!CoreResale.lockedByDormantShop(w.getServer(), linkedItem(z))) throw new IllegalStateException("dormant key not locked");
        } finally {
            PENDING.clear();
            zones.remove(z);
            zones.removeDormant(z);
            w.setBlock(at, prev, Block.UPDATE_ALL);
        }
    }

    private static int linkedCount(List<ItemStack> drops, ShopZones.Zone z) {
        int n = 0;
        for (ItemStack s : drops) {
            CompoundTag l = s.get(TcgBlocks.SHOP_LINK);
            if (l != null && l.hasUUID("zone") && z.id.equals(l.getUUID("zone"))) n += s.getCount();
        }
        return n;
    }
}
