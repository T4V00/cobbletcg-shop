package dev.tcgshop.shop;

import dev.tcgshop.TcgShop;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.TraderEntity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 1.1 selftest step "progression" (/tcgshop debug selftest): core resale prices and the dormant-shop lock, the tier
 * difficulty curve (unchanged early game, never easier going up) and a collector's shop visit surviving a save / load.
 */
public final class ProgressionSelfTest {
    private ProgressionSelfTest() {}

    public static void run(ServerLevel w, BlockPos near) {
        // --- core resale
        ItemStack wood = new ItemStack(TcgBlocks.CORE_ITEMS.get(ShopTier.WOOD));
        long expect = Math.round(ShopServer.priceOf(wood, 1.0) * CoreResale.rate());
        if (CoreResale.rate() > 0 && (CoreResale.unitPrice(wood) != Math.max(1, expect) || !CoreResale.isCore(wood)))
            throw new IllegalStateException("core resale price " + CoreResale.unitPrice(wood) + " != " + expect);
        if (CoreResale.unitPrice(new ItemStack(Items.DIRT)) != 0 || CoreResale.isCore(new ItemStack(TcgBlocks.DISPLAY_CASE_ITEM)))
            throw new IllegalStateException("non-core priced as a core");
        if (CoreResale.unitPrice(new ItemStack(TcgBlocks.CORE_ITEMS.get(ShopTier.NETHERITE))) < CoreResale.unitPrice(wood))
            throw new IllegalStateException("core resale not ordered by tier");
        // crafted tiers (Paper: shop_core_paper recipe) are never bought, and the rate never beats the biggest store sale
        ItemStack paper = new ItemStack(TcgBlocks.CORE_ITEMS.get(ShopTier.PAPER));
        if (CoreResale.unitPrice(paper) != 0 || CoreResale.resellable(paper) || !CoreResale.isCore(paper))
            throw new IllegalStateException("a craftable Paper core can be resold");
        double savedRate = dev.tcgshop.EconomyConfig.INSTANCE.coreResaleRate;
        try {
            dev.tcgshop.EconomyConfig.INSTANCE.coreResaleRate = 0.95;
            if (Math.abs(CoreResale.maxRate() - 0.5) > 1e-9 || CoreResale.rate() > CoreResale.maxRate())
                throw new IllegalStateException("core resale rate not capped at " + CoreResale.maxRate());
        } finally {
            dev.tcgshop.EconomyConfig.INSTANCE.coreResaleRate = savedRate;
        }
        ShopZones zones = ShopZones.get(w);
        BlockPos core = near.offset(-4096, 0, 4096);
        ShopZones.Zone z = new ShopZones.Zone(UUID.randomUUID(), UUID.randomUUID(), "selftest", core, ShopTier.STONE.rank());
        zones.add(z);
        try {
            ItemStack link = ShopOpenClose.linkedItem(z);
            if (CoreResale.lockedByDormantShop(w.getServer(), link)) throw new IllegalStateException("an active shop's core is locked");
            zones.makeDormant(z, w.getGameTime());
            if (!CoreResale.lockedByDormantShop(w.getServer(), link) || !z.id.equals(CoreResale.linkedZone(link)))
                throw new IllegalStateException("a dormant shop's core can be sold");
            zones.removeDormant(z);
            if (CoreResale.lockedByDormantShop(w.getServer(), link)) throw new IllegalStateException("a stale link is still locked");
            zones.add(z);
            z.dormant = false;
            z.closed = false;

            // --- tier difficulty: unchanged up to Brick, never easier going up
            for (int r = 0; r < 3 && r < dev.tcgshop.EconomyConfig.INSTANCE.tierDifficultyStartRank; r++) {
                z.tier = r;
                if (TierDifficulty.perceivedMult(z) != 1 || TierDifficulty.haggleCeilMult(z) != 1 || TierDifficulty.thiefWeightAdd(z) != 0
                        || TierDifficulty.thiefTierShift(z) != 0) throw new IllegalStateException("early tier " + r + " changed");
            }
            double pm = 0, hm = 2, ta = -1, ts = -1;
            for (int r = 0; r <= TierDifficulty.MAX_RANK; r++) {
                z.tier = r;
                double p = TierDifficulty.perceivedMult(z), h = TierDifficulty.haggleCeilMult(z), a = TierDifficulty.thiefWeightAdd(z), s = TierDifficulty.thiefTierShift(z);
                if (p < pm || h > hm || a < ta || s < ts || p > 2 || h < 0.5) throw new IllegalStateException("tier difficulty not monotonic at rank " + r);
                pm = p; hm = h; ta = a; ts = s;
            }
            z.tier = ShopTier.NETHERITE.rank();
            TcgShop.LOGGER.info("[selftest] progression: core resale Wood {} · Netherite perceived x{} haggle x{} thief +{} shift {}",
                    CoreResale.unitPrice(wood), String.format("%.2f", pm), String.format("%.2f", hm), String.format("%.3f", ta), String.format("%.2f", ts));
            z.tier = ShopTier.STONE.rank();

            // --- collector shop visit: anchored, saved and loaded, ended
            TraderEntity tr = Objects.requireNonNull(TcgEntities.TRADER.create(w));
            tr.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5, 0, 0);
            tr.visitShop(z);
            if (!core.equals(tr.shopHome()) || !tr.hasRestriction()) throw new IllegalStateException("visit not anchored");
            CompoundTag nbt = new CompoundTag();
            tr.saveWithoutId(nbt);
            TraderEntity back = Objects.requireNonNull(TcgEntities.TRADER.create(w));
            back.load(nbt);
            if (!core.equals(back.shopHome()) || !z.id.equals(back.shopZone()) || !back.hasRestriction()) throw new IllegalStateException("visit not saved");
            back.endShopVisit();
            if (back.hasRestriction() || back.shopHome() != null) throw new IllegalStateException("visit not ended");
            CompoundTag plain = new CompoundTag();
            back.saveWithoutId(plain);
            if (plain.contains("ShopHome")) throw new IllegalStateException("wandering collector saved a shop");
            if (TcgEntities.trySpawnAtShop(w, z, true) != null) throw new IllegalStateException("visit spawned with the owner offline");
            tr.discard();
            back.discard();
        } finally {
            zones.remove(z);
            zones.removeDormant(z);
        }
    }
}
