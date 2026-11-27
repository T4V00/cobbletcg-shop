package dev.tcgshop.shop;

import dev.tcgshop.TcgShop;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.TcgEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 1.1 (cleanliness update) selftest "cleanliness", on a small floating floor near spawn (everything is removed again):
 *  · the score and its effects (buy chance, arrivals, mood, reputation factor, droppings cap and chance) behave;
 *  · droppings: placed on the floor, never next to another one, capped per shop, lower the score, vanish without a
 *    floor and are forgotten when gone;
 *  · the wall freshener puffs (smell and grime down, one charge used), respects the per-shop cap and its item keeps
 *    the charges (not repairable: no MAX_DAMAGE), an empty can shows empty when placed;
 *  · graffiti: painted on a wall, lowers the score, erased by a freshener puff; droppings outside a moved zone are
 *    pruned even with unloaded chunks; nothing grows (smell) while the owner is away;
 *  · the 3-wide door: six parts, opens / closes as one, no collision when open, never closes on someone standing in it
 *    (and closes by itself once the doorway is clear), refuses placement on someone or in another's shop,
 *    counts as ONE door of the shop, a customer finds a path through it and gets across, and breaking any part removes it;
 *  · the new decor: decoration points, the furniture catalog ("shop" category, prices, buyback below the price).
 */
public final class CleanSelfTest {
    private CleanSelfTest() {}

    public static void run(ServerLevel w, BlockPos near) {
        pureChecks();
        catalogChecks();
        BlockPos origin = near.offset(0, 70, 0);
        ShopZones zones = ShopZones.get(w);
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", origin, ShopTier.STONE.rank());
        zones.add(z);
        List<Entity> spawned = new ArrayList<>();
        try {
            floor(w, origin, 10);
            int poops = poopChecks(w, z, origin);
            int puffs = freshenerChecks(w, z, origin);
            int graffiti = graffitiChecks(w, z, origin);
            String door = doorChecks(w, z, origin, spawned);
            TcgShop.LOGGER.info("[selftest] cleanliness: score {} -> {} with {} droppings (cap {}); freshener {} puffs; graffiti {}; wide door {}",
                    Cleanliness.score(0, 0, 0), Cleanliness.score(0, 0, poops), poops, Cleanliness.cap(z.size()), puffs, graffiti, door);
        } finally {
            for (Entity e : spawned) if (!e.isRemoved()) e.discard();
            clear(w, origin, 10);
            zones.remove(z);
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException(what);
    }

    /** The score and its effects, without a world. */
    private static void pureChecks() {
        check(Cleanliness.score(0, 0, 0) == 100, "a spotless shop must score 100");
        check(Cleanliness.score(100, 100, 10) == 0, "a filthy shop must score 0");
        check(Cleanliness.score(40, 0, 0) > Cleanliness.score(40, 0, 1), "a dropping must lower the score");
        check(Cleanliness.score(10, 0, 0) > Cleanliness.score(60, 0, 0), "smell must lower the score");
        check(Cleanliness.score(0, 10, 0) > Cleanliness.score(0, 60, 0), "grime must lower the score");
        check(Cleanliness.buyMult(100) > 1 && Cleanliness.buyMult(80) == 1 && Cleanliness.buyMult(40) < 0.8 && Cleanliness.buyMult(0) >= 0.25,
                "buy multiplier out of range");
        check(Cleanliness.spawnMult(100) == 1 && Cleanliness.spawnMult(0) < 1 && Cleanliness.spawnMult(0) >= 0.3, "arrival multiplier out of range");
        check(Cleanliness.enterMood(100) > 0 && Cleanliness.enterMood(70) == 0 && Cleanliness.enterMood(10) < 0, "entering mood wrong");
        check(Cleanliness.repFactor(100) > 0 && Cleanliness.repFactor(75) == 0 && Cleanliness.repFactor(0) <= -15 && Cleanliness.repFactor(0) >= -20,
                "reputation factor out of range");
        check(Cleanliness.cap(10) >= 2 && Cleanliness.cap(200) <= CleanConfig.INSTANCE.poopMax, "droppings cap wrong");
        check(Cleanliness.poopChance(0, 0, 50) == 0, "an empty shop must never get droppings");
        check(Cleanliness.poopChance(6, 2, 20) > Cleanliness.poopChance(1, 0, 100), "a busy, dirty shop must get more droppings");
        check(Cleanliness.poopChance(100, 100, 0) <= 0.9, "droppings chance must stay below 0.9");
        check(Cleanliness.graffitiChance(5, 0) == 0 && Cleanliness.graffitiChance(0, 90) == 0, "graffiti without grime or customers");
        check(!CleanConfig.INSTANCE.graffitiEnabled || Cleanliness.graffitiChance(5, 90) > Cleanliness.graffitiChance(1, CleanConfig.INSTANCE.graffitiMinGrime),
                "a busy, grimy shop must get more graffiti");
        // the freshener item is not damageable, so vanilla repair / grindstone can't merge cans into free charges
        ItemStack can = new ItemStack(CleanBlocks.WALL_FRESHENER_ITEM);
        can.set(DataComponents.DAMAGE, 100);
        check(!can.isDamageableItem() && WallFreshenerBlock.itemCharges(can) == WallFreshenerBlock.MAX_CHARGES - 100, "freshener item charges / repairable");
        // the clean factor replaced "smell" in the reputation and goes into the aesthetics score like the old one did
    }

    /** Decor points and the furniture catalog. */
    private static void catalogChecks() {
        check(CleanBlocks.DECOR.size() >= 10, "fewer than 10 new decor objects");
        for (Item it : CleanBlocks.DECOR) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(it);
            check(DecorScore.points(id) > 0, id + " gives no decoration");
            check(DecorScore.points(new ItemStack(it)) > 0, id + " shows no decoration in its tooltip");
        }
        for (Item it : CleanBlocks.CATALOG) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(it);
            check(CleanBlocks.isCatalog(id), id + " not in the catalog");
            check("shop".equals(FurnitureShop.category(id)), id + " not in the shop category");
            long base = FurnitureShop.basePrice(id);
            check(base > 0 && base <= 20_000, id + " price " + base);
            long sell = FurnitureShop.sellPrice(id, FurnitureShop.KIND_SELLER);
            long cheapest = Math.min(FurnitureShop.buyPrice(id, FurnitureShop.KIND_SELLER), Math.min(FurnitureShop.buyPrice(id, FurnitureShop.KIND_PHONE),
                    FurnitureShop.buyPrice(id, FurnitureShop.KIND_WHOLESALER)));
            check(sell < cheapest, id + " buyback " + sell + " >= price " + cheapest);
            check(ShopServer.priceOf(new ItemStack(it), 1.0) == CleanBlocks.PRICES.get(it), id + " fixed price missing");
        }
        boolean cat = false;
        for (String c : FurnitureShop.CATEGORIES) cat |= c.equals("shop");
        check(cat, "the furniture app has no shop category");
        check(!CleanBlocks.isCatalog(BuiltInRegistries.BLOCK.getKey(CleanBlocks.POOP)), "droppings must never be sold");
        check(DecorScore.points(BuiltInRegistries.ITEM.getKey(CleanBlocks.WIDE_DOOR_ITEM)) == 0, "the door must not count as decor");
    }

    /** Droppings: placing, spacing, cap, score, floor loss and pruning. Returns how many were placed. */
    private static int poopChecks(ServerLevel w, ShopZones.Zone z, BlockPos o) {
        BlockPos a = o.offset(2, 1, 2);
        BlockPos p1 = Cleanliness.placePoop(w, z, a, false);
        check(p1 != null && w.getBlockState(p1).is(CleanBlocks.POOP) && z.poops.size() == 1, "a dropping was not placed");
        check(w.getBlockState(p1).getCollisionShape(w, p1, CollisionContext.empty()).isEmpty(), "droppings must have no collision");
        BlockPos side = Cleanliness.placePoop(w, z, a.east(), false);
        check(side == null || Math.max(Math.abs(side.getX() - p1.getX()), Math.abs(side.getZ() - p1.getZ())) > 1, "two droppings were placed side by side");
        if (side != null) Cleanliness.removePoop(w, side);
        int before = Cleanliness.score(z);
        // fill up to the cap on spread spots
        int placed = z.poops.size();
        for (int i = 0; i < 12; i++) {
            BlockPos q = Cleanliness.placePoop(w, z, o.offset(-6 + (i % 4) * 3, 1, -6 + (i / 4) * 3), i % 2 == 0);
            if (q != null) placed++;
        }
        int cap = Cleanliness.cap(z.size());
        check(z.poops.size() <= cap && z.poops.size() == Math.min(cap, placed), "droppings went over the cap (" + z.poops.size() + "/" + cap + ")");
        check(Cleanliness.score(z) < before, "more droppings did not lower the score");
        check(Cleanliness.placePoop(w, z, o.offset(7, 1, 7), false) == null, "a dropping was placed over the cap");
        int n = z.poops.size();
        // a dropping without its floor goes away, and the list forgets it
        w.setBlockAndUpdate(p1.below(), Blocks.AIR.defaultBlockState());
        check(!w.getBlockState(p1).is(CleanBlocks.POOP), "a dropping stayed in the air");
        Cleanliness.prune(w, z);
        check(z.poops.size() == n - 1, "a vanished dropping was not forgotten");
        w.setBlockAndUpdate(p1.below(), Blocks.STONE.defaultBlockState());
        // removing all of them restores the score
        for (Long l : new ArrayList<>(z.poops)) Cleanliness.removePoop(w, BlockPos.of(l));
        check(z.poops.isEmpty() && Cleanliness.score(z) == 100, "cleaning every dropping did not restore the score");
        // floor-mess incidents count like droppings
        z.floorDirt = 2;
        check(Cleanliness.score(z) < 100, "floor-mess incidents do not lower the score");
        z.floorDirt = 0;
        // a remembered dropping outside the zone (the shop moved) is forgotten even when its chunk is not loaded
        long far = o.offset(100_000, 0, 100_000).asLong();
        z.poops.add(far);
        Cleanliness.prune(w, z);
        check(!z.poops.contains(far), "a dropping outside the (moved) zone was not forgotten");
        // the owner is away: droppings don't make the shop smell
        BlockPos q1 = Cleanliness.placePoop(w, z, a, false);
        check(q1 != null, "dropping for the away test");
        z.smell = 0;
        Cleanliness.tick(w, z);
        check(z.smell == 0, "droppings added smell while the owner was away");
        // restoring a dormant shop somewhere else forgets the old site's droppings
        Cleanliness.forgetSite(w, z);
        check(z.poops.isEmpty() && !w.getBlockState(q1).is(CleanBlocks.POOP), "the old site's droppings were not removed");
        // the zone keeps them through a save
        Cleanliness.placePoop(w, z, a, true);
        z.grime = 17;
        ShopZones.Zone back = ShopZones.Zone.read(z.write());
        check(back.poops.equals(z.poops) && back.grime == 17, "droppings / grime were not saved");
        for (Long l : new ArrayList<>(z.poops)) Cleanliness.removePoop(w, BlockPos.of(l));
        z.grime = 0;
        return n;
    }

    /** Wall freshener: puffs, charges, per-shop cap, item charges. Returns the puffs made. */
    private static int freshenerChecks(ServerLevel w, ShopZones.Zone z, BlockPos o) {
        Cleanliness.reset();
        List<BlockPos> fresheners = new ArrayList<>();
        int cap = CleanConfig.INSTANCE.sprayMaxPerShop;
        for (int i = 0; i <= cap; i++) {
            BlockPos wall = o.offset(-8 + i * 2, 1, 8);
            w.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            BlockPos f = wall.north();
            BlockState st = CleanBlocks.WALL_FRESHENER.defaultBlockState().setValue(WallFreshenerBlock.FACING, Direction.NORTH);
            check(st.canSurvive(w, f), "the freshener can't hang on a wall");
            w.setBlockAndUpdate(f, st);
            fresheners.add(f);
        }
        z.smell = 60;
        z.grime = 20;
        int puffs = 0;
        for (BlockPos f : fresheners) {
            if (!(w.getBlockEntity(f) instanceof WallFreshenerBlock.FreshenerEntity be)) throw new IllegalStateException("no freshener block entity");
            check(be.charges() == WallFreshenerBlock.MAX_CHARGES, "a new freshener is not full");
            if (be.tryPuff(w, w.getBlockState(f))) puffs++;
        }
        check(puffs == cap, "fresheners per shop not capped: " + puffs + " puffed, cap " + cap);
        check(z.smell == 60 - cap * CleanConfig.INSTANCE.spraySmell, "freshener smell removal wrong: " + z.smell);
        check(z.grime == Math.max(0, 20 - cap * CleanConfig.INSTANCE.sprayGrime), "freshener grime removal wrong: " + z.grime);
        WallFreshenerBlock.FreshenerEntity first = (WallFreshenerBlock.FreshenerEntity) w.getBlockEntity(fresheners.get(0));
        check(first != null && first.charges() == WallFreshenerBlock.MAX_CHARGES - 1, "a puff did not use a charge");
        // the dropped item keeps the charges, and placing it back restores them
        Integer dmg = first.collectComponents().get(DataComponents.DAMAGE);
        check(dmg != null && dmg == 1, "the freshener item does not keep its charges");
        ItemStack half = new ItemStack(CleanBlocks.WALL_FRESHENER_ITEM);
        half.set(DataComponents.DAMAGE, 200);
        first.applyComponentsFromItemStack(half);
        check(first.charges() == WallFreshenerBlock.MAX_CHARGES - 200, "placing a used freshener did not restore its charges");
        // an empty can placed from the item shows as empty at once
        ItemStack empty = new ItemStack(CleanBlocks.WALL_FRESHENER_ITEM);
        empty.set(DataComponents.DAMAGE, WallFreshenerBlock.MAX_CHARGES);
        WallFreshenerBlock.FreshenerEntity third = (WallFreshenerBlock.FreshenerEntity) w.getBlockEntity(fresheners.get(2));
        check(third != null, "third freshener");
        third.applyComponentsFromItemStack(empty);
        CleanBlocks.WALL_FRESHENER.setPlacedBy(w, fresheners.get(2), w.getBlockState(fresheners.get(2)), null, empty);
        check(third.charges() == 0 && w.getBlockState(fresheners.get(2)).getValue(WallFreshenerBlock.EMPTY), "an empty can placed from the item looks full");
        // outside a shop / with nothing to clean it does not puff (and keeps its charges)
        Cleanliness.reset();
        z.smell = 0;
        z.grime = 0;
        check(!first.tryPuff(w, w.getBlockState(fresheners.get(0))), "a freshener puffed in a clean shop");
        // an empty can shows as empty
        first.setCharges(0);
        check(w.getBlockState(fresheners.get(0)).getValue(WallFreshenerBlock.EMPTY), "an empty freshener does not look empty");
        // losing its wall drops it
        w.setBlockAndUpdate(fresheners.get(1).south(), Blocks.AIR.defaultBlockState());
        check(!w.getBlockState(fresheners.get(1)).is(CleanBlocks.WALL_FRESHENER), "a freshener stayed without its wall");
        Cleanliness.reset();
        return puffs;
    }

    /** Graffiti: painted on a wall, lowers the score, a freshener puff erases it. Returns how many were painted. */
    private static int graffitiChecks(ServerLevel w, ShopZones.Zone z, BlockPos o) {
        if (Cleanliness.graffitiCap() <= 0) return 0;
        Cleanliness.reset();
        // a wall along X at z = o.z + 5, with a freshener on it
        for (int dx = -4; dx <= 4; dx++)
            for (int dy = 1; dy <= 2; dy++) w.setBlockAndUpdate(o.offset(dx, dy, 5), Blocks.STONE.defaultBlockState());
        BlockPos g = Cleanliness.placeGraffiti(w, z, o.offset(0, 1, 2), w.random); // spots reach z + 5 (the wall) at most
        check(g != null && w.getBlockState(g).is(CleanBlocks.GRAFFITI) && z.graffiti.contains(g.asLong()), "no graffiti was painted");
        check(w.getBlockState(g).getCollisionShape(w, g, CollisionContext.empty()).isEmpty(), "graffiti must have no collision");
        check(Cleanliness.score(z) < 100, "graffiti does not lower the score");
        ShopZones.Zone back = ShopZones.Zone.read(z.write());
        check(back.graffiti.equals(z.graffiti), "graffiti was not saved");
        BlockPos f = o.offset(3, 2, 6); // on the other side of the wall
        w.setBlockAndUpdate(f, CleanBlocks.WALL_FRESHENER.defaultBlockState().setValue(WallFreshenerBlock.FACING, Direction.SOUTH));
        if (!(w.getBlockEntity(f) instanceof WallFreshenerBlock.FreshenerEntity be)) throw new IllegalStateException("no freshener for the graffiti");
        z.smell = 0;
        z.grime = 0;
        check(be.tryPuff(w, w.getBlockState(f)), "a freshener did not puff at a graffiti");
        check(z.graffiti.isEmpty() && !w.getBlockState(g).is(CleanBlocks.GRAFFITI) && Cleanliness.score(z) == 100, "the freshener did not erase the graffiti");
        Cleanliness.reset();
        w.setBlock(f, Blocks.AIR.defaultBlockState(), 2 | 16);
        for (int dx = -4; dx <= 4; dx++)
            for (int dy = 1; dy <= 2; dy++) w.setBlock(o.offset(dx, dy, 5), Blocks.AIR.defaultBlockState(), 2 | 16);
        return 1;
    }

    /** The 3-wide door. */
    private static String doorChecks(ServerLevel w, ShopZones.Zone z, BlockPos o, List<Entity> spawned) {
        // a wall along X at z = o.z with the door in the middle; inside the shop zone
        BlockPos main = o.offset(0, 1, 0);
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = 1; dy <= 3; dy++) if (Math.abs(dx) > 1 || dy == 3) w.setBlockAndUpdate(o.offset(dx, dy, 0), Blocks.STONE.defaultBlockState());
        BlockState st = CleanBlocks.WIDE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        w.setBlockAndUpdate(main, st);
        CleanBlocks.WIDE_DOOR.setPlacedBy(w, main, st, null, ItemStack.EMPTY);
        List<BlockPos> parts = WideDoorBlock.parts(main, Direction.NORTH);
        for (BlockPos p : parts) {
            BlockState ps = w.getBlockState(p);
            check(ps.is(CleanBlocks.WIDE_DOOR), "door part missing at " + p);
            check(WideDoorBlock.mainPos(ps, p).equals(main), "door part " + p + " does not know its main part");
            check(ps.is(BlockTags.WOODEN_DOORS), "the wide door is not tagged as a wooden door");
        }
        check(ShopLife.shopDoors(w, z).stream().filter(p -> parts.contains(p)).count() == 1, "the wide door must count as exactly one shop door");
        check(ShopLife.shopDoors(w, z).contains(main), "the shop door is not the main part");
        // open / close as one
        CleanBlocks.WIDE_DOOR.setOpen(null, w, w.getBlockState(parts.get(0)), parts.get(0), true);
        for (BlockPos p : parts) {
            check(w.getBlockState(p).getValue(DoorBlock.OPEN), "a door part did not open");
            check(w.getBlockState(p).getCollisionShape(w, p, CollisionContext.empty()).isEmpty(), "an open door part still has collision");
        }
        // a customer can walk across the open door
        CustomerEntity c = TcgEntities.CUSTOMER.create(w);
        if (c == null) throw new IllegalStateException("no customer type");
        c.moveTo(main.getX() + 1.5, main.getY(), main.getZ() - 2.5, 0, 0);
        double startZ = c.getZ();
        for (int i = 0; i < 25; i++) c.move(MoverType.SELF, new Vec3(0, -0.05, 0.2));
        double walked = c.getZ() - startZ;
        check(walked > 4, "a customer could not walk through the open door (z " + startZ + " -> " + c.getZ() + ")");
        // standing in the doorway: it refuses to close
        c.moveTo(main.getX() + 0.5, main.getY(), main.getZ() + 0.5, 0, 0);
        w.addFreshEntity(c);
        spawned.add(c);
        CleanBlocks.WIDE_DOOR.setOpen(null, w, w.getBlockState(main), main, false);
        check(w.getBlockState(main).getValue(DoorBlock.OPEN), "the door closed on a customer standing in it");
        check(WideDoorBlock.closePending(w, main), "a refused close was not scheduled again");
        // placing a door where someone stands is refused; so is any part in another player's shop
        BlockPos spot = o.offset(0, 1, -6);
        check(WideDoorBlock.placementProblem(w, spot, Direction.NORTH, null) == null, "a free spot refused the door: " + WideDoorBlock.placementProblem(w, spot, Direction.NORTH, null));
        c.moveTo(spot.getX() + 1.5, spot.getY(), spot.getZ() + 0.5, 0, 0); // standing on the right part's cell
        check("tcgshop.wide_door.occupied".equals(WideDoorBlock.placementProblem(w, spot, Direction.NORTH, null)), "placing a door over a customer was allowed");
        check(WideDoorBlock.foreignZone(w, null, parts) == z, "door parts in someone else's shop were not detected");
        c.discard();
        // the doorway is clear: the scheduled retry closes it
        w.getBlockState(main).tick(w, main, w.random);
        check(!WideDoorBlock.closePending(w, main), "the retried close is still pending");
        for (BlockPos p : parts) check(!w.getBlockState(p).getValue(DoorBlock.OPEN), "a door part did not close");
        // closed: it blocks
        CustomerEntity c2 = TcgEntities.CUSTOMER.create(w);
        if (c2 == null) throw new IllegalStateException("no customer type");
        c2.moveTo(main.getX() + 0.5, main.getY(), main.getZ() - 2.5, 0, 0);
        for (int i = 0; i < 25; i++) c2.move(MoverType.SELF, new Vec3(0, -0.05, 0.2));
        check(c2.getZ() < main.getZ() + 1, "a customer walked through the closed door (z " + c2.getZ() + ")");
        // customers find a path through the closed door (they open wooden doors)
        c2.moveTo(main.getX() + 0.5, main.getY(), main.getZ() - 4.5, 0, 0);
        c2.setOnGround(true);
        Path path = c2.getNavigation().createPath(main.offset(0, 0, 4), 0);
        check(path != null && path.canReach(), "customers find no path through the wide door");
        boolean through = false;
        for (int i = 0; i < path.getNodeCount(); i++) {
            var n = path.getNode(i);
            if (n.z == main.getZ() && Math.abs(n.x - main.getX()) <= 1) through = true;
        }
        check(through, "the customers' path does not go through the door");
        c2.discard();
        // breaking any part removes the whole door
        w.destroyBlock(parts.get(5), false);
        for (BlockPos p : parts) check(!w.getBlockState(p).is(CleanBlocks.WIDE_DOOR), "a door part was left over at " + p);
        // a half-built door (no floor under a side) can't be placed: checked through canSurvive of the lower parts
        BlockState lower = CleanBlocks.WIDE_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        check(lower.canSurvive(w, main), "a door can't stand on a floor");
        return "OK (" + parts.size() + " parts, path " + path.getNodeCount() + " nodes, walked " + String.format(java.util.Locale.ROOT, "%.1f", walked) + " blocks)";
    }

    private static void floor(ServerLevel w, BlockPos o, int r) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) w.setBlockAndUpdate(o.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
    }

    private static void clear(ServerLevel w, BlockPos o, int r) {
        for (int dy = 4; dy >= 0; dy--)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) w.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
    }
}
