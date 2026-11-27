package dev.tcgshop.npc;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.TcgShop;
import dev.tcgshop.collection.BinderData;
import dev.tcgshop.compat.MayorBattle;
import dev.tcgshop.entity.CollectorHunt;
import dev.tcgshop.entity.EngineerEntity;
import dev.tcgshop.entity.MayorEntity;
import dev.tcgshop.entity.TcgEntities;
import dev.tcgshop.entity.TraderEntity;
import dev.tcgshop.shop.ShopTier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 1.1 (NPC update) selftest "npcs", no players needed:
 *  · taxes: the bill (property + income share by tier, min/max), the TaxOffice entry saved and loaded, the Mayor's
 *    random binder card (each copy once, then nothing), the Mayor entity (save/load, cannot be hurt), his six-Pokémon
 *    team specs and level clamp;
 *  · collectors: trade limits per phone tier never go down, charisma talk chances (no talk needed at 85%, out of reach
 *    far below, better with more charisma), the hunting AI's block rules / restore / flight / teleport spot
 *    (CollectorHunt.selfTest), the tougher robbed-collector defaults reached existing configs;
 *  · the CREATive Engineer: catalog prices follow the rules (basic stacks around $3,000, machines over $50,000, nothing
 *    too advanced), no catalog without Create, save/load.
 */
public final class NpcSelfTest {
    private NpcSelfTest() {}

    public static void run(ServerLevel w, BlockPos near) {
        taxes(w);
        collectors(w, near);
        engineer(w, near);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException(what);
    }

    // ------------------------------------------------------------------ taxes

    private static void taxes(ServerLevel w) {
        NpcConfig c = NpcConfig.INSTANCE;
        TaxOffice.Bill iron = TaxOffice.bill(List.of(ShopTier.IRON.price), 1_000_000, ShopTier.IRON.rank());
        check(iron.property() == Math.round(ShopTier.IRON.price * c.taxPropertyRate), "property part " + iron.property());
        check(iron.income() == Math.round(1_000_000 * c.taxIncomeRate), "income part at Iron " + iron.income());
        TaxOffice.Bill neth = TaxOffice.bill(List.of(ShopTier.NETHERITE.price, ShopTier.IRON.price), 1_000_000, ShopTier.NETHERITE.rank());
        check(neth.rate() > iron.rate() && neth.rate() <= c.taxIncomeRateMax + 1e-9, "income rate by tier " + neth.rate());
        check(neth.total() > iron.total(), "a Netherite owner pays more");
        check(TaxOffice.bill(List.of(), 0, ShopTier.IRON.rank()).total() == c.taxMinimum, "minimum tax");
        check(TaxOffice.bill(List.of(), Long.MAX_VALUE / 4, ShopTier.NETHERITE.rank()).total() == c.taxMaximum, "maximum tax");
        check(TaxOffice.bill(List.of(), -5_000, ShopTier.IRON.rank()).income() == 0, "negative revenue is no income");
        for (int r = ShopTier.IRON.rank(); r < ShopTier.NETHERITE.rank(); r++)
            check(TaxOffice.incomeRate(r + 1) >= TaxOffice.incomeRate(r), "income rate goes down at rank " + r);

        // the entry survives save / load
        TaxOffice office = TaxOffice.get(w.getServer());
        UUID fake = UUID.randomUUID();
        TaxOffice.Entry e = office.entryOrNew(fake);
        e.state = TaxOffice.STATE_DUE;
        e.amount = 41_100;
        e.nextDue = 99;
        e.mayor = UUID.randomUUID();
        CompoundTag nbt = office.save(new CompoundTag(), w.registryAccess());
        TaxOffice back = TaxOffice.fromNbt(nbt, w.registryAccess());
        TaxOffice.Entry b = back.entry(fake);
        check(b != null && b.state == TaxOffice.STATE_DUE && b.amount == 41_100 && b.nextDue == 99 && e.mayor.equals(b.mayor), "tax entry save/load");
        check(TaxOffice.expects(w.getServer(), fake, e.mayor) && !TaxOffice.expects(w.getServer(), fake, UUID.randomUUID()), "expected Mayor");
        office.remove(fake);

        // the random binder card: every copy can be taken once, then nothing is left
        List<String> ids = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (CardInfo.isCard(id)) ids.add(id.toString());
            if (ids.size() >= 2) break;
        }
        check(ids.size() == 2, "no cards registered");
        Map<String, Integer> counts = new HashMap<>();
        counts.put(ids.get(0), 3);
        counts.put(ids.get(1), 1);
        BinderData bd = new BinderData(fake, "selftest", counts, Set.of());
        RandomSource r = RandomSource.create(7);
        int taken = 0;
        long value = 0;
        while (true) {
            TaxOffice.Taken t = TaxOffice.takeFrom(bd, r);
            if (t == null) break;
            taken++;
            value += t.value();
            check(taken <= 4, "took more cards than the binder had");
        }
        check(taken == 4 && bd.totalCards() == 0, "binder cards taken " + taken);
        // every owned binder counts, weighted by copies (a carried binder with one junk card shields nothing)
        Map<String, Integer> one = new HashMap<>(), many = new HashMap<>();
        one.put(ids.get(0), 1);
        many.put(ids.get(1), 99);
        List<BinderData> pair = List.of(new BinderData(fake, "selftest", one, Set.of()), new BinderData(fake, "selftest", many, Set.of()));
        int junk = 0;
        for (int i = 0; i < 2000; i++) if (TaxOffice.pickBinder(pair, r) == 0) junk++;
        check(junk > 0 && junk < 100, "binder pick not weighted by copies: the 1-card binder was picked " + junk + "/2000");
        check(TaxOffice.pickBinder(List.of(new BinderData(fake, "selftest", Map.of(), Set.of())), r) == -1, "an empty binder was picked");

        // the Mayor: saved taxpayer, cannot be hurt
        MayorEntity m = TcgEntities.MAYOR.create(w);
        check(m != null, "no Mayor entity");
        BlockPos at = w.getSharedSpawnPos().above(3);
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        m.setTaxpayer(fake);
        w.addFreshEntity(m);
        boolean hurt = m.hurt(w.damageSources().generic(), 10);
        CompoundTag mn = new CompoundTag();
        m.saveWithoutId(mn);
        MayorEntity m2 = TcgEntities.MAYOR.create(w);
        check(m2 != null, "no Mayor copy");
        m2.load(mn);
        m.discard();
        check(!hurt && m.getHealth() == m.getMaxHealth(), "the Mayor can be hurt");
        check(fake.equals(m2.taxpayer()), "the Mayor forgot his taxpayer");

        // his team
        for (int t = 0; t < 3; t++) {
            List<String> specs = MayorBattle.teamSpecs(t, 57);
            check(specs.size() == 6, "team " + t + " has " + specs.size());
            for (String s : specs) check(s.contains("level=57") && s.contains("moves=") && s.contains("held_item=") && !s.contains("|"), "spec " + s);
        }
        check(MayorBattle.teamLevel(1) == c.mayorTeamMinLevel && MayorBattle.teamLevel(500) == c.mayorTeamMaxLevel, "team level clamp");
        check(MayorBattle.allSpecies().length == 18, "species list");
        TcgShop.LOGGER.info("[selftest] npcs: taxes Iron {} (property {} + income {} = {}%), Netherite {} ({}%); {} binder cards taken (${}); Mayor save/load + immune OK; 3 teams x6",
                iron.total(), iron.property(), iron.income(), Math.round(iron.rate() * 1000) / 10.0, neth.total(), Math.round(neth.rate() * 1000) / 10.0, taken, value);
    }

    // ------------------------------------------------------------------ collectors

    private static void collectors(ServerLevel w, BlockPos near) {
        for (int t = 0; t < 6; t++) {
            check(TraderEntity.giveMax(t + 1) >= TraderEntity.giveMax(t), "give limit drops at tier " + t);
            check(TraderEntity.takeMax(t + 1) >= TraderEntity.takeMax(t), "take limit drops at tier " + t);
            check(TraderEntity.tradesMax(t + 1) >= TraderEntity.tradesMax(t), "trades drop at tier " + t);
            check(TraderEntity.talksMax(t + 1) >= TraderEntity.talksMax(t), "talks drop at tier " + t);
            check(TraderEntity.giveMax(t + 1) > TraderEntity.giveMax(t) || TraderEntity.takeMax(t + 1) > TraderEntity.takeMax(t)
                    || TraderEntity.tradesMax(t + 1) > TraderEntity.tradesMax(t) || TraderEntity.talksMax(t + 1) > TraderEntity.talksMax(t),
                    "phone tier " + (t + 1) + " changes nothing with collectors");
        }
        check(NpcConfig.INSTANCE.version >= 2, "npc config not migrated to v2");
        check(TraderEntity.takeMax(6) > TraderEntity.takeMax(0) && TraderEntity.giveMax(6) > TraderEntity.giveMax(0)
                && TraderEntity.tradesMax(6) > TraderEntity.tradesMax(0), "a better phone must allow more");
        for (int k = 0; k < TraderEntity.TALKS; k++) {
            check(TraderEntity.talkChance(k, 1, TraderEntity.MIN_RATIO) == 1, "no talk needed at the threshold");
            check(TraderEntity.talkChance(k, 20, 0.2) == 0, "a terrible offer must be out of reach");
            double near1 = TraderEntity.talkChance(k, 1, TraderEntity.MIN_RATIO - 0.03), near20 = TraderEntity.talkChance(k, 20, TraderEntity.MIN_RATIO - 0.03);
            check(near1 > 0 && near1 < 1 && near20 > near1, "talk " + k + " chance " + near1 + " / " + near20);
            check(TraderEntity.talkReach(k, 20) > TraderEntity.talkReach(k, 1), "charisma must widen the reach");
        }
        check(EconomyConfig.INSTANCE.collectorHuntVersion >= 1 && EconomyConfig.INSTANCE.collectorRobbedHealth != 80, "robbed collector defaults not migrated");
        String hunt = CollectorHunt.selfTest(w, near.offset(40, 50, 40));
        TcgShop.LOGGER.info("[selftest] npcs: collector limits give {}->{}, take {}->{}, trades {}->{}; talk reach at charisma 20: {}%; hunt: {}",
                TraderEntity.giveMax(0), TraderEntity.giveMax(6), TraderEntity.takeMax(0), TraderEntity.takeMax(6), TraderEntity.tradesMax(0), TraderEntity.tradesMax(6),
                Math.round(TraderEntity.talkReach(TraderEntity.TALK_PROMISE, 20) * 100), hunt);
    }

    // ------------------------------------------------------------------ engineer

    private static void engineer(ServerLevel w, BlockPos near) {
        for (Map.Entry<String, Long> e : EngineerGoods.defaultBasic().entrySet())
            check(e.getValue() >= 2_000 && e.getValue() <= 4_000 && e.getKey().startsWith("create:"), "basic price " + e);
        for (Map.Entry<String, Long> e : EngineerGoods.defaultMachines().entrySet())
            check(e.getValue() > 50_000 && e.getKey().startsWith("create:"), "machine price " + e);
        for (String id : EngineerGoods.NEVER)
            check(!EngineerGoods.defaultBasic().containsKey(id) && !EngineerGoods.defaultMachines().containsKey(id), "too advanced: " + id);
        boolean create = EngineerGoods.createInstalled();
        List<EngineerGoods.Offer> cat = EngineerGoods.catalog();
        check(create || cat.isEmpty(), "a catalog without Create");
        for (EngineerGoods.Offer o : cat) check(o.price() > 0 && !o.stack().isEmpty(), "bad offer");
        // a purchase is only honored against the catalog the window showed
        List<EngineerGoods.Offer> fakeCat = List.of(new EngineerGoods.Offer(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64), 3_000),
                new EngineerGoods.Offer(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT), 60_000));
        List<EngineerGoods.Offer> repriced = List.of(fakeCat.get(0), new EngineerGoods.Offer(fakeCat.get(1).stack(), 90_000));
        List<EngineerGoods.Offer> reordered = List.of(fakeCat.get(1), fakeCat.get(0));
        check(EngineerEntity.catalogStamp(fakeCat) == EngineerEntity.catalogStamp(List.copyOf(fakeCat)), "catalog stamp not stable");
        check(EngineerEntity.catalogStamp(fakeCat) != EngineerEntity.catalogStamp(repriced)
                && EngineerEntity.catalogStamp(fakeCat) != EngineerEntity.catalogStamp(reordered), "a changed catalog was not noticed");
        EngineerEntity en = TcgEntities.ENGINEER.create(w);
        check(en != null, "no Engineer entity");
        en.moveTo(near.getX() + 0.5, near.getY() + 3, near.getZ() + 4.5, 0, 0);
        CompoundTag n = new CompoundTag();
        en.saveWithoutId(n);
        en.load(n);
        if (!create) check(EngineerEntity.spawnFor(w, null) != null, "the Engineer must not come without Create");
        TcgShop.LOGGER.info("[selftest] npcs: Engineer {} basic + {} machines by default, Create {} ({} products)",
                EngineerGoods.defaultBasic().size(), EngineerGoods.defaultMachines().size(), create ? "installed" : "absent", cat.size());
    }

    // ------------------------------------------------------------------ debug commands

    /** /tcgshop debug mayor: your taxes fall due now and the Mayor comes. */
    public static LiteralArgumentBuilder<CommandSourceStack> debugMayor() {
        return Commands.literal("mayor").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            TaxOffice.debugDue(p);
            ctx.getSource().sendSuccess(() -> Component.literal("[mayor] tax due now"), false);
            return 1;
        });
    }

    /** /tcgshop debug engineer: brings the CREATive Engineer (needs Create). */
    public static LiteralArgumentBuilder<CommandSourceStack> debugEngineer() {
        return Commands.literal("engineer").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            String why = EngineerEntity.spawnFor(p.serverLevel(), p);
            if (why != null) ctx.getSource().sendFailure(Component.translatable(why));
            return why == null ? 1 : 0;
        });
    }
}
