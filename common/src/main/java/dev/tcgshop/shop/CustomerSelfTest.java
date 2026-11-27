package dev.tcgshop.shop;

import dev.tcgshop.TcgShop;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.entity.NpcIdentity;
import dev.tcgshop.entity.NpcLines;
import dev.tcgshop.entity.ScalperEntity;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.luxury.LuxuryBlocks;
import dev.tcgshop.luxury.StatueTier;
import dev.tcgshop.network.DealAnswerPayload;
import dev.tcgshop.network.DealOpenPayload;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * 1.1 (customers update) selftest "customers":
 *  · markup tolerance by reputation, charm chances and attempts, generous / greedy detection;
 *  · a generous haggler: charm attempts keep the price between your price and the tip cap, and charisma helps;
 *  · decoration points and liking, the Latin American names and that every NPC line category is complete in both languages;
 *  · a small room built in the sky: lenient wall detection (windows and a hole still count), shop-roof detection, and a
 *    recompute that sees a luxury statue and decor (decoration liking and best statue tier);
 *  · Scalper shop visits only from the configured tier.
 * Group B review fixes: displays/counters are not walls, a peaceful Scalper is not a threat, roof overhang and indoor
 * upper floors, decor placed high on the walls counted, the server's charm odds in the deal payload, small talk, and
 * "let me think" counting as greed with a generous customer.
 * Everything it builds is removed again.
 */
public final class CustomerSelfTest {
    private CustomerSelfTest() {}

    public static void run(ServerLevel w, BlockPos near) {
        pureChecks();
        linesComplete();
        charmRun(w);
        BlockPos origin = near.offset(0, 60, 0);
        ShopZones zones = ShopZones.get(w);
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest-customers", origin, ShopTier.STONE.rank());
        zones.add(z);
        try {
            room(w, origin, true);
            double walled = ShopLife.wallScore(w, origin.above(), 6);
            boolean onRoof = ShopZones.isShopRoof(w, origin.offset(1, 5, 1));
            boolean inside = ShopZones.isShopRoof(w, origin.offset(1, 1, 1));
            boolean outside = ShopZones.isShopRoof(w, origin.offset(7, 1, 0));
            boolean upperFloor = ShopZones.isShopRoof(w, origin.offset(1, 3, 1)); // under the roof: an indoor upper floor, not a roof
            int edge = z.size() / 2;
            boolean eaves = ShopZones.isShopRoof(w, origin.offset(edge + ShopZones.ROOF_OVERHANG - 1, 5, 0));
            boolean beyond = ShopZones.isShopRoof(w, origin.offset(edge + ShopZones.ROOF_OVERHANG + 2, 5, 0));
            // decor: a gold statue, posters and a pedestal inside the room
            w.setBlockAndUpdate(origin.offset(-2, 1, -2), LuxuryBlocks.BLOCKS.get(StatueTier.GOLD).defaultBlockState());
            for (int i = 0; i < 3; i++) w.setBlockAndUpdate(origin.offset(-3, 2, -1 + i), TcgBlocks.POSTER.defaultBlockState());
            w.setBlockAndUpdate(origin.offset(2, 1, 2), TcgBlocks.PEDESTAL.defaultBlockState());
            w.setBlockAndUpdate(origin.offset(0, 5, 0), TcgBlocks.PEDESTAL.defaultBlockState()); // decor high up (above the old y band)
            ShopLife.preview(w, z);
            int pct = DecorScore.zonePct(z), statue = DecorScore.zoneStatue(z);
            double expectPts = DecorScore.points(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(LuxuryBlocks.BLOCKS.get(StatueTier.GOLD)))
                    + 3 * DecorScore.points(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(TcgBlocks.POSTER)) + 2 * DecorScore.points(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(TcgBlocks.PEDESTAL));
            int gotPts10 = z.factors.getOrDefault("m_decor_pts", 0);
            int pedestalCopies = z.decorCopies.getOrDefault(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(TcgBlocks.PEDESTAL), 0);
            room(w, origin, false);
            double open = ShopLife.wallScore(w, origin.above(), 6);
            // a furnished floor with no walls: a ring of displays and a register around the spot is not a wall
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == 2)
                        w.setBlockAndUpdate(origin.offset(dx, 1, dz), ((dx + dz) & 1) == 0 ? TcgBlocks.PEDESTAL.defaultBlockState() : TcgBlocks.SHOWCASE.defaultBlockState());
            w.setBlockAndUpdate(origin.offset(0, 2, 2), TcgBlocks.CASH_REGISTER.defaultBlockState());
            double furnished = ShopLife.wallScore(w, origin.above(), 6);
            TcgShop.LOGGER.info("[selftest] customers: walls with windows and a hole {} / no walls {} / displays only {}; roof spot {} inside {} outside {} upper floor {} eaves {} beyond {}; decoration {}% ({} pts x10, expected {}; pedestals {}) (statue tier {})",
                    String.format("%.2f", walled), String.format("%.2f", open), String.format("%.2f", furnished), onRoof, inside, outside,
                    upperFloor, eaves, beyond, pct, gotPts10, Math.round(expectPts * 10), pedestalCopies, statue);
            if (furnished > 0.15) throw new IllegalStateException("displays and counters with no walls counted as walls: " + furnished);
            if (upperFloor || !eaves || beyond) throw new IllegalStateException("roof overhang / upper floor: upper=" + upperFloor + " eaves=" + eaves + " beyond=" + beyond);
            if (gotPts10 != Math.round(expectPts * 10) || pedestalCopies != 2)
                throw new IllegalStateException("decor points over the zone's height: " + gotPts10 + " vs " + Math.round(expectPts * 10) + ", pedestals " + pedestalCopies);
            if (walled < 0.9) throw new IllegalStateException("a wall with windows and a hole should still count: " + walled);
            if (open > 0.1) throw new IllegalStateException("an open floor counted as walled: " + open);
            if (!onRoof || inside || outside) throw new IllegalStateException("shop roof detection: roof=" + onRoof + " inside=" + inside + " outside=" + outside);
            if (statue != StatueTier.GOLD.index()) throw new IllegalStateException("the recompute did not see the gold statue: " + statue);
            if (pct <= 0 || pct >= 60) throw new IllegalStateException("decoration liking out of range for a little decor: " + pct);
            // scalper visits from Stone (default), not in Wood, not when closed
            ShopZones.Zone wood = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "w", BlockPos.ZERO, ShopTier.WOOD.rank());
            if (!ScalperEntity.shopEligible(z) || ScalperEntity.shopEligible(wood)) throw new IllegalStateException("scalper shop tier gate");
            z.closed = true;
            boolean closedOk = !ScalperEntity.shopEligible(z);
            z.closed = false;
            if (!closedOk) throw new IllegalStateException("a scalper may not visit a closed shop");
            // a peaceful Scalper (shop visitor) does not make the shop unsafe; a zombie does
            ScalperEntity sc = TcgEntities.SCALPER.create(w);
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(w);
            boolean scThreat = sc != null && ShopLife.threat(sc), zThreat = zombie != null && ShopLife.threat(zombie);
            if (sc != null) sc.discard();
            if (zombie != null) zombie.discard();
            if (scThreat || !zThreat) throw new IllegalStateException("hostile check: peaceful scalper=" + scThreat + " zombie=" + zThreat);
        } finally {
            clear(w, origin, 7);
            zones.remove(z);
        }
    }

    /** Logic with no world. */
    private static void pureChecks() {
        // markup: free up to market, steep above it, gentler with reputation
        if (MarkupTolerance.buyMult(1.0, 0, 1) != 1) throw new IllegalStateException("markup at market");
        double low = MarkupTolerance.buyMult(1.2, 0, 1), high = MarkupTolerance.buyMult(1.2, 100, 1), whale = MarkupTolerance.buyMult(1.2, 0, 3);
        if (low > 0.05 || high < low * 3 || whale <= low) throw new IllegalStateException("markup falloff: " + low + " / " + high + " / " + whale);
        if (MarkupTolerance.ceilMult(1.2, 0) >= MarkupTolerance.ceilMult(1.2, 100) || MarkupTolerance.ceilMult(0.95, 0) != 0.95)
            throw new IllegalStateException("haggler ceiling vs reputation");
        // charm
        for (int k = 0; k < Charisma.CHARM_COUNT; k++)
            for (int p = -1; p < CustomerEntity.PERSONAS.length; p++) {
                double a = Charisma.charmChance(k, 1, p), b = Charisma.charmChance(k, 20, p);
                if (a < 0.05 || b > 0.95 || b <= a) throw new IllegalStateException("charm chance " + k + "/" + p + ": " + a + " -> " + b);
            }
        if (Charisma.charmsPerDeal(1) != 2 || Charisma.charmsPerDeal(10) != 3) throw new IllegalStateException("charm attempts per deal");
        if (Charisma.CHARM_COUNT != 4 || !"smalltalk".equals(Charisma.CHARM_KEYS[Charisma.CHARM_SMALLTALK])) throw new IllegalStateException("small talk charm option");
        if (Charisma.charmChanceNow(0, 5, 0, 1) >= Charisma.charmChance(0, 5, 0) || Charisma.charmChanceNow(0, 5, 0, 6) <= Charisma.charmChance(0, 5, 0))
            throw new IllegalStateException("mood in the charm chance");
        // the deal window gets the server's odds and the flags of all four charm options
        DealOpenPayload dp = new DealOpenPayload(0, net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty(), ItemStack.EMPTY,
                1, 1, DealOpenPayload.BUY, ItemStack.EMPTY, 0, 0, 1, 1, 0, DealOpenPayload.flags(true, 0b1001, 2), DealOpenPayload.odds(0.35, 0.0, 1.0, 0.655, 0.4));
        if (!dp.generous() || !dp.charmUsed(0) || dp.charmUsed(1) || !dp.charmUsed(Charisma.CHARM_SMALLTALK) || dp.charmsLeft() != 2
                || dp.oddsPct(0) != 35 || dp.oddsPct(1) != 0 || dp.oddsPct(2) != 100 || dp.oddsPct(3) != 66 || dp.oddsPct(DealOpenPayload.ODDS_PERSUADE) != 40
                || dp.oddsPct(5) != -1)
            throw new IllegalStateException("deal payload flags / odds");
        if (Charisma.persuadeChance(20) <= Charisma.persuadeChance(1)) throw new IllegalStateException("persuade chance");
        // generous / greedy
        if (!CustomerEntity.isGenerous(DealOpenPayload.BUY, 0, 1600, 1500, 1500, false) || CustomerEntity.isGenerous(DealOpenPayload.BUY, 0, 1400, 1500, 1500, false)
                || CustomerEntity.isGenerous(DealOpenPayload.BUY, -1, 1600, 1500, 1500, false)
                || !CustomerEntity.isGenerous(DealOpenPayload.SELL, 2, 800, 0, 1000, false) || CustomerEntity.isGenerous(DealOpenPayload.SELL, 2, 800, 0, 1000, true)
                || CustomerEntity.isGenerous(DealOpenPayload.SELL, 2, 990, 0, 1000, false))
            throw new IllegalStateException("generous detection");
        if (!CustomerEntity.isGreedy(DealOpenPayload.BUY, DealAnswerPayload.COUNTER, 1700, 1600) || CustomerEntity.isGreedy(DealOpenPayload.BUY, DealAnswerPayload.COUNTER, 1600, 1600)
                || CustomerEntity.isGreedy(DealOpenPayload.BUY, DealAnswerPayload.JOKE, 9999, 1600) || !CustomerEntity.isGreedy(DealOpenPayload.SELL, DealAnswerPayload.PERSUADE, 700, 800)
                || !CustomerEntity.isGreedy(DealOpenPayload.SELL, DealAnswerPayload.INTEREST, 0, 800) || !CustomerEntity.isGreedy(DealOpenPayload.BUY, DealAnswerPayload.INTEREST, 0, 800)
                || CustomerEntity.isGreedy(DealOpenPayload.SELL, DealAnswerPayload.ACCEPT, 1, 800))
            throw new IllegalStateException("greedy detection");
        // decoration
        int prev = 0;
        for (StatueTier t : StatueTier.values()) {
            int pts = DecorScore.points(LuxuryBlocks.BLOCKS.get(t).asItem().getDefaultInstance());
            if (pts <= prev) throw new IllegalStateException("statue decor points must grow with the tier: " + t);
            prev = pts;
        }
        if (DecorScore.points(ResourceLocation.fromNamespaceAndPath(TcgShop.MOD_ID, "statue_part")) != 0
                || DecorScore.points(new ItemStack(TcgBlocks.PEDESTAL_ITEM)) != 3 || DecorScore.points(new ItemStack(TcgBlocks.HOME_PEDESTAL_ITEM)) != 0
                || DecorScore.points(new ItemStack(TcgBlocks.CASH_REGISTER_ITEM)) != 0 || DecorScore.points(new ItemStack(Items.FLOWER_POT)) != 0
                || DecorScore.points(ResourceLocation.fromNamespaceAndPath("pokeblocks", "pikachu_pokedoll")) != 2)
            throw new IllegalStateException("decor points of single objects");
        if (DecorScore.pct(0, 24) != 0 || DecorScore.pct(20, 24) <= DecorScore.pct(10, 24) || DecorScore.pct(400, 24) > 100
                || DecorScore.pct(30, 50) >= DecorScore.pct(30, 16) || DecorScore.pct(15, 24) > 30)
            throw new IllegalStateException("decoration liking curve");
        if (DecorScore.copyWeight(1) != 1 || DecorScore.copyWeight(5) >= 1 || DecorScore.copyWeight(20) >= DecorScore.copyWeight(5))
            throw new IllegalStateException("decor copy weights");
        if (DecorScore.repFactor(0) >= DecorScore.repFactor(50) || DecorScore.repFactor(100) != 15) throw new IllegalStateException("decor reputation factor");
        // tooltips: one more copy adds the copy-weighted points; the head of a bed / upper half of a door counts once
        if (DecorScore.addedPoints(4, 0) != 4 || DecorScore.addedPoints(4, 3) != 2 || DecorScore.addedPoints(4, 8) != 1) throw new IllegalStateException("decor added points");
        var bed = Blocks.RED_BED.defaultBlockState();
        if (!DecorScore.isSecondaryPart(bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD))
                || DecorScore.isSecondaryPart(bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT))
                || !DecorScore.isSecondaryPart(Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER))
                || DecorScore.isSecondaryPart(Blocks.STONE.defaultBlockState()))
            throw new IllegalStateException("2-block decor parts");
        // names: 200+ new Latin American first names, surnames keep the sex of the first name
        if (NpcIdentity.LATAM_SURNAMES.length < 80 || NpcIdentity.FEMALE_NAMES.length + NpcIdentity.MALE_NAMES.length < 330 + 200)
            throw new IllegalStateException("name pools too small");
        if (NpcIdentity.sexOfName("Lupita Hernández") != NpcIdentity.Sex.FEMALE || NpcIdentity.sexOfName("José Luis Pérez") != NpcIdentity.Sex.MALE
                || !NpcIdentity.isLatamName("Xóchitl") || NpcIdentity.isLatamName("Olivia"))
            throw new IllegalStateException("name sex / pool");
        RandomSource r = RandomSource.create(7);
        int withSurname = 0;
        for (int i = 0; i < 400; i++) if (NpcIdentity.randomName(r, NpcIdentity.Sex.FEMALE).contains(" ") ) withSurname++;
        if (withSurname == 0) throw new IllegalStateException("no name ever got a surname");
    }

    /** Every NpcLines category has exactly its count of lines in both languages (the 1.1 categories included). */
    private static void linesComplete() {
        int missing = 0;
        for (String lang : new String[]{"en_us", "es_es"}) {
            InputStream in = CustomerSelfTest.class.getClassLoader().getResourceAsStream("assets/tcgshop/lang/" + lang + ".json");
            if (in == null) throw new IllegalStateException("lang file not found: " + lang);
            com.google.gson.JsonObject o;
            try (InputStreamReader rd = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                o = com.google.gson.JsonParser.parseReader(rd).getAsJsonObject();
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            for (Map.Entry<String, Integer> e : NpcLines.COUNTS.entrySet()) {
                String k = "tcgshop.npc." + e.getKey() + ".";
                if (!o.has(k + (e.getValue() - 1)) || o.has(k + e.getValue())) { missing++; TcgShop.LOGGER.error("[selftest] customers: {} lines of {} incomplete", lang, e.getKey()); }
            }
            for (String cat : new String[]{"owner_joke", "charm_joke_good", "greedy_leave", "statue_missing", "comment_decor_high"})
                if (!NpcLines.COUNTS.containsKey(cat)) throw new IllegalStateException("missing line category " + cat);
            if (!o.has("tcgshop.reviewtext.deal_greedy.5.7") || !o.has("tcgshop.review.deal_greedy")) throw new IllegalStateException("missing greedy review texts in " + lang);
        }
        if (missing > 0) throw new IllegalStateException(missing + " incomplete line categories");
        int total = 0;
        for (int n : NpcLines.COUNTS.values()) total += n;
        TcgShop.LOGGER.info("[selftest] customers: {} NPC lines in {} categories", total, NpcLines.COUNTS.size());
    }

    /** Charm attempts on a generous haggler (not added to the world): price bounds and charisma helping. */
    private static void charmRun(ServerLevel w) {
        CustomerEntity c = TcgEntities.CUSTOMER.create(w);
        if (c == null) throw new IllegalStateException("no customer");
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "charm", BlockPos.ZERO, ShopTier.STONE.rank());
        ItemStack item = new ItemStack(Items.PAPER);
        c.debugHaggler(z, BlockPos.ZERO, 0, item, 1000, 2000, 1500);
        if (c.debugGenerous()) throw new IllegalStateException("an offer below your price is not generous");
        long cap = TipLedger.cap(Long.MAX_VALUE);
        int[] wins = new int[2];
        int trials = 300;
        for (int li = 0; li < 2; li++)
            for (int i = 0; i < trials; i++) {
                c.debugHaggler(z, BlockPos.ZERO, 0, item, 1600, 2000, 1500);
                if (!c.debugGenerous()) throw new IllegalStateException("an offer above your price must be generous");
                for (int k = 0; k < Charisma.CHARM_COUNT; k++) {
                    boolean ok = c.debugCharm(k, li == 0 ? 1 : 20);
                    if (k == Charisma.CHARM_JOKE && ok) wins[li]++;
                    long p = c.debugDealPrice();
                    if (p < 1500 || p > 1500 + cap) throw new IllegalStateException("charm price out of bounds: " + p);
                }
            }
        c.discard();
        TcgShop.LOGGER.info("[selftest] customers: joke success {}% at charisma 1 / {}% at charisma 20", wins[0] * 100 / trials, wins[1] * 100 / trials);
        if (wins[1] <= wins[0]) throw new IllegalStateException("charisma does not help charm");
    }

    /** A 9x9 room (walls with glass panes and a hole at foot height, flat roof) or just its floor. */
    private static void room(ServerLevel w, BlockPos o, boolean walls) {
        clear(w, o, 7);
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) w.setBlockAndUpdate(o.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
        if (!walls) return;
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                if (edge) for (int dy = 1; dy <= 3; dy++) {
                    int lat = Math.abs(dx) == 4 ? dz : dx;
                    var st = Blocks.STONE.defaultBlockState();
                    if (dy == 2 && Math.abs(lat) <= 1) st = Blocks.GLASS_PANE.defaultBlockState(); // a window
                    if (dy == 1 && lat == 0) st = Blocks.AIR.defaultBlockState(); // a hole at foot height
                    w.setBlockAndUpdate(o.offset(dx, dy, dz), st);
                }
                w.setBlockAndUpdate(o.offset(dx, 4, dz), Blocks.STONE.defaultBlockState());
            }
    }

    private static void clear(ServerLevel w, BlockPos o, int r) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = 0; dy <= 6; dy++) w.setBlockAndUpdate(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
    }
}
