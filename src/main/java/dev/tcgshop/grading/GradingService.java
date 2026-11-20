package dev.tcgshop.grading;

import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.card.CardInfo;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pedidos a CobbleGrade: eliges cartas del inventario y un servicio, pagas, las cartas "viajan" y cuando pasa el
 * tiempo (de Minecraft, mientras el servidor está en marcha) las recoges desde el teléfono ya gradeadas.
 * La nota se tira al recoger (revelado con animación en el cliente).
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

    // ------------------------------------------------------------------ acciones

    public static void handle(ServerPlayerEntity p, GradePayloads.Action a) {
        switch (a.action()) {
            case GradePayloads.SUBMIT -> submit(p, Grading.Service.of(a.service()), a.slots());
            case GradePayloads.COLLECT -> collect(p, a.order());
            default -> {}
        }
        sync(p);
    }

    private static void submit(ServerPlayerEntity p, Grading.Service service, List<Integer> slots) {
        Set<Integer> unique = new LinkedHashSet<>(slots);
        List<Integer> valid = new ArrayList<>();
        long fee = 0;
        for (int slot : unique) {
            if (slot < 0 || slot >= 36) continue;
            ItemStack st = p.getInventory().getStack(slot);
            if (!CardInfo.isCard(st) || Grading.isGraded(st)) continue;
            valid.add(slot);
            fee += service.fee(ShopServer.priceOf(st.copyWithCount(1), 1.0));
            if (valid.size() >= Grading.MAX_PER_ORDER) break;
        }
        if (valid.isEmpty()) {
            p.sendMessage(Text.translatable("tcgshop.grade.none_selected").formatted(Formatting.RED), true);
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(p, fee)) {
            p.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(fee)).formatted(Formatting.RED), true);
            return;
        }
        GradingService s = get(p.getServer());
        Order o = new Order();
        o.id = s.nextId++;
        o.service = service.ordinal();
        o.submitted = now(p.getServer());
        o.ready = o.submitted + service.ticks;
        for (int slot : valid) o.cards.add(p.getInventory().getStack(slot).split(1)); // una carta de cada hueco
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
            int g = Grading.roll(p.getRandom());
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

    public static void sync(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, GradePayloads.Sync.ID)) return;
        GradingService s = get(p.getServer());
        List<GradePayloads.Order> out = new ArrayList<>();
        for (Order o : s.orders.getOrDefault(p.getUuid(), List.of()))
            out.add(new GradePayloads.Order(o.id, o.service, o.submitted, o.ready, List.copyOf(o.cards)));
        ServerPlayNetworking.send(p, new GradePayloads.Sync(now(p.getServer()), out));
    }

    /** Avisa a los jugadores cuando su pedido está listo para recoger. */
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

    // ------------------------------------------------------------------ guardado

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
