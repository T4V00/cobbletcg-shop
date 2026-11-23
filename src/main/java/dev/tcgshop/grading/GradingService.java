package dev.tcgshop.grading;

import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.GradePayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.ShopServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CobbleGrade orders: you pick cards from your inventory and a service, pay, the cards "travel" and once the
 * time passes (Minecraft time, while the server is running) you collect them already graded from the phone.
 * The grade is rolled on collection (revealed with an animation on the client).
 */
public final class GradingService extends PersistentState {
    private static final Type<GradingService> TYPE = new Type<>(GradingService::new, GradingService::fromNbt, null);

    static final class Order {
        int id, service;
        long submitted, ready;
        boolean notified;
        final List<ItemStack> cards = new ArrayList<>();
    }

    private final Map<UUID, List<Order>> orders = new HashMap<>();
    private int nextId = 1, nextCert = 100_001;

    public static GradingService get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "tcgshop_grading");
    }

    private static long now(MinecraftServer server) { return server.getOverworld().getTime(); }

    /** New certificate number (admin commands, etc.); shares the counter with orders. */
    public static int nextCert(MinecraftServer server) {
        GradingService s = get(server);
        s.markDirty();
        return s.nextCert++;
    }

    // ------------------------------------------------------------------ actions

    public static void handle(ServerPlayerEntity p, GradePayloads.Action a) {
        switch (a.action()) {
            case GradePayloads.SUBMIT -> {
                // 0.9: CobbleGrade is a phone app with a minimum level; collecting existing orders stays free
                if (dev.tcgshop.phone.PhoneUpgrades.requireApp(p, "grade")) submit(p, Grading.Service.of(a.service()), a);
            }
            case GradePayloads.COLLECT -> collect(p, a.order());
            default -> {}
        }
        sync(p);
    }

    private static void submit(ServerPlayerEntity p, Grading.Service service, GradePayloads.Action a) {
        List<Integer> slots = a.slots();
        Set<Integer> unique = new HashSet<>();
        List<Integer> valid = new ArrayList<>();
        long fee = 0;
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            if (!unique.add(slot)) continue;
            if (slot < 0 || (slot >= 36 && !dev.tcgshop.collection.BinderSlots.isBinder(slot))) continue;
            ItemStack st = dev.tcgshop.collection.BinderSlots.peek(p, slot);
            if (!CardInfo.isCard(st) || Grading.isGraded(st)) continue;
            // the card in the slot must be the one the player confirmed: if the Binder was reordered (another card came in
            // or left) or the slot changed while the packet was in flight, it is skipped instead of grading another card
            String want = a.expectedAt(i);
            if (!want.isEmpty() && !want.equals(CardInfo.id(st).toString())) continue;
            valid.add(slot);
            fee += service.fee(ShopServer.priceOf(st.copyWithCount(1), 1.0));
            if (valid.size() >= Grading.MAX_PER_ORDER) break;
        }
        if (valid.isEmpty()) {
            p.sendMessage(Text.translatable("tcgshop.grade.none_selected").formatted(Formatting.RED), true);
            return;
        }
        double evf = dev.tcgshop.events.EventManager.gradingFeeFactor(p, false); // 0.9 events: "expert visit"
        if (evf < 1) fee = Math.max(1, Math.round(fee * evf));
        if (!CobbleDollarsBridge.tryCharge(p, fee)) {
            p.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(fee)).formatted(Formatting.RED), true);
            return;
        }
        if (evf < 1) dev.tcgshop.events.EventManager.gradingFeeFactor(p, true);
        GradingService s = get(p.getServer());
        Order o = new Order();
        o.id = s.nextId++;
        o.service = service.ordinal();
        o.submitted = now(p.getServer());
        o.ready = o.submitted + service.ticks;
        // one card from each slot (inventory or Binder); Binder ones first, from highest to lowest slot,
        // so taking one out does not shift the index of the others
        List<Integer> order = new ArrayList<>(valid);
        order.sort((x, y) -> Integer.compare(y, x));
        for (int slot : order) {
            ItemStack one = dev.tcgshop.collection.BinderSlots.take(p, slot, 1);
            if (!one.isEmpty()) o.cards.add(one);
        }
        dev.tcgshop.collection.BinderSlots.sync(p);
        s.orders.computeIfAbsent(p.getUuid(), k -> new ArrayList<>()).add(o);
        s.markDirty();
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_BOOK_PUT, SoundCategory.PLAYERS, 1f, 1.2f);
        p.sendMessage(Text.translatable("tcgshop.grade.sent", o.cards.size(), Text.translatable("tcgshop.grade.service." + service.key),
                Text.literal(PriceService.format(fee)).formatted(Formatting.GOLD)).formatted(Formatting.AQUA), false);
        CardAdvancements.grant(p, "grade_send");
    }

    private static void collect(ServerPlayerEntity p, int id) {
        GradingService s = get(p.getServer());
        List<Order> mine = s.orders.getOrDefault(p.getUuid(), List.of());
        Order o = null;
        for (Order x : mine) if (x.id == id) o = x;
        if (o == null) return;
        if (now(p.getServer()) < o.ready) {
            p.sendMessage(Text.translatable("tcgshop.grade.not_ready").formatted(Formatting.GRAY), true);
            return;
        }
        List<ItemStack> graded = new ArrayList<>();
        long before = 0, after = 0;
        for (ItemStack card : o.cards) {
            // We pass each card's ID so Grading applies the extreme
            // penalty to prints from 2017 and earlier.
            int g = Grading.roll(p.getRandom(), CardInfo.id(card));
            ItemStack out = Grading.apply(card, g, s.nextCert++);
            before += PriceService.priceCd(card);
            after += PriceService.priceCd(out);
            graded.add(out);
            if (g >= 100) CardAdvancements.grant(p, "grade_10");
            if (g >= 95) CardAdvancements.grant(p, "grade_95");
            if (g <= 20) CardAdvancements.grant(p, "grade_bad");
        }
        mine.remove(o);
        s.markDirty();
        for (ItemStack g : graded) p.getInventory().offerOrDrop(g.copy());
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.4f);
        if (ServerPlayNetworking.canSend(p, GradePayloads.Reveal.ID)) ServerPlayNetworking.send(p, new GradePayloads.Reveal(graded, before, after));
    }

    /** 0.9 events: does this player have any order still unfinished? */
    public static boolean hasPending(MinecraftServer server, UUID player) {
        long now = now(server);
        for (Order o : get(server).orders.getOrDefault(player, List.of())) if (o.ready > now) return true;
        return false;
    }

    /** 0.9 events: speeds up ONE pending order (the latest one), removing that fraction of its remaining time. */
    public static boolean speedUpOne(MinecraftServer server, UUID player, double fraction) {
        long now = now(server);
        Order best = null;
        for (Order o : get(server).orders.getOrDefault(player, List.of())) if (o.ready > now && (best == null || o.ready > best.ready)) best = o;
        if (best == null) return false;
        long left = best.ready - now;
        best.ready = Math.max(now, best.ready - Math.round(left * Math.max(0, Math.min(0.9, fraction))));
        get(server).markDirty();
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
        if (p != null) sync(p);
        return true;
    }

    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, GradePayloads.Sync.ID)) return;
        GradingService s = get(p.getServer());
        List<GradePayloads.Order> out = new ArrayList<>();
        for (Order o : s.orders.getOrDefault(p.getUuid(), List.of()))
            out.add(new GradePayloads.Order(o.id, o.service, o.submitted, o.ready, List.copyOf(o.cards)));
        ServerPlayNetworking.send(p, new GradePayloads.Sync(now(p.getServer()), out));
    }

    /** Notifies players when their order is ready to collect. */
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 100 != 0) return;
        GradingService s = get(server);
        long now = now(server);
        Set<UUID> done = new HashSet<>();
        for (Map.Entry<UUID, List<Order>> e : s.orders.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null) continue;
            for (Order o : e.getValue()) {
                if (o.notified || now < o.ready) continue;
                o.notified = true;
                s.markDirty();
                if (done.add(p.getUuid())) {
                    p.sendMessage(Text.translatable("tcgshop.grade.ready", o.cards.size()).formatted(Formatting.GREEN), false);
                    p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1f, 1.5f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    private static GradingService fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        GradingService s = new GradingService();
        s.nextId = Math.max(1, nbt.getInt("next_id"));
        s.nextCert = Math.max(100_001, nbt.getInt("next_cert"));
        NbtCompound players = nbt.getCompound("players");
        for (String k : players.getKeys()) {
            UUID id;
            try { id = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
            List<Order> list = new ArrayList<>();
            NbtList ol = players.getList(k, NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < ol.size(); i++) {
                NbtCompound c = ol.getCompound(i);
                Order o = new Order();
                o.id = c.getInt("id");
                o.service = c.getInt("service");
                o.submitted = c.getLong("submitted");
                o.ready = c.getLong("ready");
                o.notified = c.getBoolean("notified");
                NbtList cl = c.getList("cards", NbtElement.COMPOUND_TYPE);
                for (int j = 0; j < cl.size(); j++) ItemStack.fromNbt(lookup, cl.getCompound(j)).ifPresent(o.cards::add);
                list.add(o);
            }
            s.orders.put(id, list);
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("next_id", nextId);
        nbt.putInt("next_cert", nextCert);
        NbtCompound players = new NbtCompound();
        orders.forEach((k, list) -> {
            NbtList ol = new NbtList();
            for (Order o : list) {
                NbtCompound c = new NbtCompound();
                c.putInt("id", o.id);
                c.putInt("service", o.service);
                c.putLong("submitted", o.submitted);
                c.putLong("ready", o.ready);
                c.putBoolean("notified", o.notified);
                NbtList cl = new NbtList();
                for (ItemStack st : o.cards) cl.add(st.encode(lookup));
                c.put("cards", cl);
                ol.add(c);
            }
            if (!ol.isEmpty()) players.put(k.toString(), ol);
        });
        nbt.put("players", players);
        return nbt;
    }
}
