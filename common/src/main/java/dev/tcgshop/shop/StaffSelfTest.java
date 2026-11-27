package dev.tcgshop.shop;

import dev.tcgshop.EconomyConfig;
import dev.tcgshop.TcgShop;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.EmployeeEntity;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.ThiefTiers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 1.1 (staff update) selftest "staff": builds a small floating shop floor near spawn and runs the real entity AI tick by
 * tick (no players needed):
 *  · guards catch a fleeing shoplifter and beat an armed robber before and after buying every guard upgrade, and a guard
 *    left frozen by an interrupted duel (saved NoAI flag) moves again;
 *  · a cashier serves a queue of customers at a register on the floor and at one standing on a counter, with the
 *    register placed facing either side (walking up to the ones not at the counter);
 *  · a cashier with the "handles hagglers" upgrade closes a waiting haggler at a fair price;
 *  · up to three guards (save / load, removal shifts the slots), the tip cap and theft risk vs reputation.
 * Everything it creates is removed again (the world time is restored too).
 */
public final class StaffSelfTest {
    private StaffSelfTest() {}

    public static void run(ServerLevel w, BlockPos near) {
        pureChecks();
        BlockPos origin = near.offset(0, 40, 0);
        List<Entity> spawned = new ArrayList<>();
        ShopZones zones = ShopZones.get(w);
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", origin, ShopTier.STONE.rank());
        zones.add(z);
        SecurityLedger.ZoneSec sec = SecurityLedger.zone(w.getServer(), z.id);
        long dayTime = w.getDayTime();
        w.setDayTime(dayTime - Math.floorMod(dayTime, 24000L) + 1000); // cashiers only work the day shift
        try {
            floor(w, origin, 12);
            // ---- guards: catch a shoplifter and beat an armed robber, with no upgrades and with every upgrade maxed
            z.guard = true;
            z.guardName = "Guard";
            int caught0 = guardRun(w, z, origin, spawned, ThiefTiers.SHOPLIFTER, false);
            for (int i = 0; i < 4; i++) sec.guard[i] = GuardUpgrades.MAX_LEVEL;
            int caught3 = guardRun(w, z, origin, spawned, ThiefTiers.SHOPLIFTER, false);
            int robber3 = guardRun(w, z, origin, spawned, ThiefTiers.ROBBER, false);
            int frozen = guardRun(w, z, origin, spawned, ThiefTiers.SHOPLIFTER, true);
            TcgShop.LOGGER.info("[selftest] staff: guard caught a shoplifter in {} ticks (no upgrades) / {} ticks (all upgrades); badly hurt an armed robber in {} ticks; frozen guard recovered in {} ticks",
                    caught0, caught3, robber3, frozen);
            if (caught0 < 0) throw new IllegalStateException("an unupgraded guard never caught the thief");
            if (caught3 < 0) throw new IllegalStateException("an upgraded guard never caught the thief");
            if (robber3 < 0) throw new IllegalStateException("a fully upgraded guard did not beat an armed robber");
            if (frozen < 0) throw new IllegalStateException("a guard left with NoAI never moved again");
            z.guard = false;
            for (int i = 0; i < 4; i++) sec.guard[i] = 0;
            // ---- cashiers: a queue of 3 at a register on the floor and on a counter, facing either way
            z.cashier = true;
            z.cashierName = "Cashier";
            for (boolean counter : new boolean[]{false, true})
                for (boolean flipped : new boolean[]{false, true}) {
                    int served = cashierRun(w, z, origin, counter, flipped, spawned);
                    TcgShop.LOGGER.info("[selftest] staff: cashier served {}/3 (register {}, facing {})", served, counter ? "on a counter" : "on the floor",
                            flipped ? "the cashier" : "the customers");
                    if (served < 3) throw new IllegalStateException("the cashier left customers unserved (counter=" + counter + ", flipped=" + flipped + ")");
                }
            // ---- the top cashier upgrade: closes a haggler the owner is not dealing with
            sec.cashier[0] = 3; sec.cashier[1] = 3; sec.cashier[2] = 3; sec.cashier[3] = 1;
            long price = haggleRun(w, z, origin, spawned);
            TcgShop.LOGGER.info("[selftest] staff: cashier closed a haggler at {} (offer 1000, ceiling 2000, your price 1500)", price);
            if (price < 1000 || price > 2000) throw new IllegalStateException("the cashier did not close the haggle at a fair price: " + price);
        } finally {
            for (int i = 0; i < 4; i++) { sec.cashier[i] = 0; sec.guard[i] = 0; }
            for (Entity e : spawned) if (!e.isRemoved()) e.discard();
            for (EmployeeEntity e : w.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(16), e -> z.id.equals(e.zoneId()))) e.discard();
            clear(w, origin, 12);
            zones.remove(z);
            w.setDayTime(dayTime);
        }
    }

    /** Logic with no world: guard slots, the tip cap, theft risk vs reputation. */
    private static void pureChecks() {
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", BlockPos.ZERO, ShopTier.STONE.rank());
        if (z.addGuard("A") != 0 || z.addGuard("B") != 1 || z.addGuard("C") != 2 || z.addGuard("D") != -1 || z.guardCount() != 3)
            throw new IllegalStateException("guard slots");
        ShopZones.Zone r = ShopZones.Zone.read(z.write());
        if (r.guardCount() != 3 || !"C".equals(r.guardNameOf(2))) throw new IllegalStateException("guards not saved");
        if (!"B".equals(r.removeGuard(1)) || r.guardCount() != 2 || !"C".equals(r.guardNameOf(1))) throw new IllegalStateException("guard removal does not shift");
        r.removeGuard(0);
        r.removeGuard(0);
        if (r.guard || r.guardCount() != 0) throw new IllegalStateException("last guard removal");
        ShopZones.Zone old = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", BlockPos.ZERO, ShopTier.STONE.rank());
        old.guard = true;
        if (old.write().getCompound("ext").contains("guards_extra") || ShopZones.Zone.read(old.write()).guardCount() != 1)
            throw new IllegalStateException("a one-guard shop saves differently");
        // tips: never more than the cap, whatever the multipliers
        if (TipLedger.cap(10_000_000) > 50_000 || TipLedger.cap(-5) != 0 || TipLedger.cap(1234) != Math.min(1234, EconomyConfig.INSTANCE.maxTipPerCustomer))
            throw new IllegalStateException("tip cap");
        // theft risk: the lower the reputation, the higher (with the default influence)
        if (EconomyConfig.INSTANCE.theftRepInfluence > 0) {
            ShopZones.Zone t = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", BlockPos.ZERO, ShopTier.STONE.rank());
            t.reputation = 10;
            double low = ShopLife.theftChance(null, t);
            t.reputation = 90;
            double high = ShopLife.theftChance(null, t);
            TcgShop.LOGGER.info("[selftest] staff: theft risk {}% at reputation 10, {}% at 90", Math.round(low * 1000) / 10.0, Math.round(high * 1000) / 10.0);
            if (!(low > high * 1.5)) throw new IllegalStateException("reputation does not move the theft risk");
        }
        // cashier upgrades: the top one needs the other three maxed
        if (CashierUpgrades.unlocked(new int[]{3, 3, 2, 0}, CashierUpgrades.Stat.HAGGLE, ShopTier.NETHERITE.rank())
                || !CashierUpgrades.unlocked(new int[]{3, 3, 3, 0}, CashierUpgrades.Stat.HAGGLE, ShopTier.NETHERITE.rank())
                || CashierUpgrades.unlocked(new int[]{3, 3, 3, 0}, CashierUpgrades.Stat.HAGGLE, ShopTier.PAPER.rank()))
            throw new IllegalStateException("haggle upgrade requirements");
    }

    /**
     * Spawns the guard and a fleeing thief of `tier` 5 blocks away and ticks both. frozen = the guard starts with NoAI set
     * (as after a world saved mid-duel). Returns the ticks until the thief was detained / killed, or -1.
     */
    private static int guardRun(ServerLevel w, ShopZones.Zone z, BlockPos origin, List<Entity> spawned, int tier, boolean frozen) {
        for (EmployeeEntity e : w.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(16), e -> z.id.equals(e.zoneId()))) e.discard();
        EmployeeEntity.spawnFor(w, z, false);
        EmployeeEntity guard = w.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(16), e -> z.id.equals(e.zoneId()) && e.isGuard()).stream().findFirst().orElse(null);
        if (guard == null) throw new IllegalStateException("no guard spawned");
        spawned.add(guard);
        if (frozen) guard.setNoAi(true);
        CustomerEntity thief = TcgEntities.CUSTOMER.create(w);
        if (thief == null) throw new IllegalStateException("no customer type");
        thief.moveTo(origin.getX() + 5.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
        w.addFreshEntity(thief);
        ThiefTiers.equip(thief, tier);
        thief.debugFleeingThief(z, new ItemStack(Items.PAPER), tier);
        spawned.add(thief);
        int detained = z.detained;
        boolean armed = ThiefTiers.armed(tier);
        try {
            for (int t = 0; t < 2400; t++) {
                if (!guard.isRemoved()) w.tickNonPassenger(guard);
                if (!thief.isRemoved()) w.tickNonPassenger(thief);
                // a shoplifter is detained; an armed robber (who escapes fast with the owner offline) must be fought hard
                if (!armed && z.detained > detained) return t;
                if (armed && thief.getHealth() < thief.getMaxHealth() * 0.6f) return t;
                if (thief.isRemoved() || guard.isRemoved() || !guard.isAlive()) return -1;
            }
            return -1;
        } finally {
            thief.discard();
            guard.discard();
        }
    }

    /** A register (on the floor or on a counter), a cashier and 3 customers in the queue. Returns how many paid. */
    private static int cashierRun(ServerLevel w, ShopZones.Zone z, BlockPos origin, boolean counter, boolean flipped, List<Entity> spawned) {
        clear(w, origin, 12);
        floor(w, origin, 12);
        for (EmployeeEntity e : w.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(16), e -> z.id.equals(e.zoneId()))) e.discard();
        BlockPos reg = origin.offset(0, counter ? 2 : 1, -2);
        // a counter along x with a wall 2 blocks behind it (the usual narrow aisle for the cashier)
        if (counter) for (int dx = -3; dx <= 3; dx++) w.setBlockAndUpdate(origin.offset(dx, 1, -2), Blocks.SMOOTH_STONE.defaultBlockState());
        for (int dx = -5; dx <= 5; dx++)
            for (int dy = 1; dy <= 3; dy++) w.setBlockAndUpdate(origin.offset(dx, dy, -5), Blocks.STONE_BRICKS.defaultBlockState());
        // customers come from the south (+z); "flipped" = the register faces the cashier side (placed from behind the counter)
        Direction facing = flipped ? Direction.NORTH : Direction.SOUTH;
        w.setBlockAndUpdate(reg, TcgBlocks.CASH_REGISTER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
        if (RegisterSpots.customerSide(w, reg) != Direction.SOUTH) throw new IllegalStateException("register customer side not detected (flipped=" + flipped + ")");
        EmployeeEntity.spawnFor(w, z, true, 0);
        EmployeeEntity cashier = w.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(16), e -> z.id.equals(e.zoneId()) && !e.isGuard()).stream().findFirst().orElse(null);
        if (cashier == null) throw new IllegalStateException("no cashier spawned");
        spawned.add(cashier);
        List<CustomerEntity> queue = new ArrayList<>();
        long before = z.balance;
        for (int i = 0; i < 3; i++) {
            CustomerEntity c = TcgEntities.CUSTOMER.create(w);
            if (c == null) throw new IllegalStateException("no customer type");
            c.moveTo(origin.getX() + 0.5 + (i - 1) * 3, origin.getY() + 1, origin.getZ() + 6.5, 0, 0);
            c.debugCheckout(z, new ItemStack(Items.PAPER), 100, reg);
            w.addFreshEntity(c);
            spawned.add(c);
            queue.add(c);
        }
        for (int t = 0; t < 1600; t++) {
            if (!cashier.isRemoved()) w.tickNonPassenger(cashier);
            for (CustomerEntity c : queue) if (!c.isRemoved()) w.tickNonPassenger(c);
            if (queue.stream().noneMatch(CustomerEntity::debugInQueue)) break;
        }
        int served = (int) ((z.balance - before) / 100);
        if (served < 3) {
            TcgShop.LOGGER.info("[selftest] staff: cashier {} (customer side {}, front {})", cashier.debugState(), RegisterSpots.customerSide(w, reg), RegisterSpots.front(w, reg));
            for (CustomerEntity c : queue) TcgShop.LOGGER.info("[selftest] staff:   customer {}", c.debugState());
        }
        for (CustomerEntity c : queue) c.discard();
        cashier.discard();
        return served;
    }

    /** A display with one item, a waiting haggler and a cashier with every upgrade. Returns the price it sold for (-1 = not sold). */
    private static long haggleRun(ServerLevel w, ShopZones.Zone z, BlockPos origin, List<Entity> spawned) {
        clear(w, origin, 12);
        floor(w, origin, 12);
        BlockPos reg = origin.offset(0, 1, -2);
        w.setBlockAndUpdate(reg, TcgBlocks.CASH_REGISTER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        BlockPos disp = origin.offset(4, 1, 3);
        w.setBlockAndUpdate(disp, TcgBlocks.DISPLAY_CASE.defaultBlockState());
        if (!(w.getBlockEntity(disp) instanceof ShopDisplayBlockEntity be)) throw new IllegalStateException("no display");
        ItemStack item = new ItemStack(Items.PAPER);
        be.setItem(0, item.copy());
        be.setPrice(0, 1500);
        EmployeeEntity.spawnFor(w, z, true, 0);
        EmployeeEntity cashier = w.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(16), e -> z.id.equals(e.zoneId()) && !e.isGuard()).stream().findFirst().orElse(null);
        if (cashier == null) throw new IllegalStateException("no cashier spawned");
        spawned.add(cashier);
        CustomerEntity c = TcgEntities.CUSTOMER.create(w);
        if (c == null) throw new IllegalStateException("no customer type");
        c.moveTo(origin.getX() + 3.5, origin.getY() + 1, origin.getZ() + 1.5, 0, 0);
        c.debugHaggler(z, disp, 0, item, 1000, 2000, 1500);
        w.addFreshEntity(c);
        spawned.add(c);
        long before = z.balance;
        try {
            for (int t = 0; t < 1200; t++) {
                if (!cashier.isRemoved()) w.tickNonPassenger(cashier);
                if (!c.isRemoved()) w.tickNonPassenger(c);
                if (!c.debugWaitingDeal()) break;
            }
            return be.getItem(0).isEmpty() ? z.balance - before : -1;
        } finally {
            c.discard();
            cashier.discard();
            w.removeBlock(disp, false);
        }
    }

    private static void floor(ServerLevel w, BlockPos o, int r) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) w.setBlockAndUpdate(o.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
    }

    private static void clear(ServerLevel w, BlockPos o, int r) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = 0; dy <= 4; dy++) w.setBlockAndUpdate(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
    }
}
