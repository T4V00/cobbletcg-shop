package dev.tcgshop.entity;

import dev.masterball.api.card.CardInfo;
import dev.masterball.api.card.Rarity;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.MarketPayloads;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.CardSales;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coleccionista ambulante: NPC pacífico que llega de vez en cuando con unas cuantas cartas. Te las cambia por cartas
 * tuyas de valor parecido (al menos el 85% de la suya) y siempre se encapricha de una de las tuyas: te la compra
 * por encima de mercado (115%). Pensado para ganar algo de dinero antes de tener tienda. Se marcha pasado día y medio.
 */
public class TraderEntity extends PathAwareEntity implements TcgNpc {
    public static final double MIN_RATIO = 0.85, WISH_BONUS = 1.15;
    private static final int STOCK = 6, TRADES = 4;
    private static final long MAX_STOCK_VALUE = 150_000;
    private static List<Identifier> cardPool;

    private final List<ItemStack> stock = new ArrayList<>();
    /** Carta que le interesa de cada jugador ("" = ya se la vendió). */
    private final Map<UUID, String> wishes = new HashMap<>();
    private int tradesLeft = TRADES;
    private int lifeTicks = 36_000;

    private static final net.minecraft.entity.data.TrackedData<String> TITLE =
            net.minecraft.entity.data.DataTracker.registerData(TraderEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.STRING);
    private static final net.minecraft.entity.data.TrackedData<Integer> SKIN =
            net.minecraft.entity.data.DataTracker.registerData(TraderEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);

    public TraderEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TITLE, "");
        builder.add(SKIN, 1);
    }

    @Override public String roleKey() { return "tcgshop.role.trader"; }
    @Override public String npcTitle() { return dataTracker.get(TITLE); }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return tradesLeft > 0 ? EMOTE_DEAL : EMOTE_NONE; }
    @Override public boolean wantsAttention() { return tradesLeft > 0; }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.5));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    private static List<Identifier> pool() {
        if (cardPool == null) {
            cardPool = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) if (CardInfo.isCard(id)) cardPool.add(id);
        }
        return cardPool;
    }

    /** 6 cartas: 3 cualquiera, 2 raras y 1 muy rara (con un tope de valor). */
    private void rollStock() {
        if (!stock.isEmpty() || pool().isEmpty()) return;
        var r = net.minecraft.util.math.random.Random.create(getUuid().getLeastSignificantBits());
        int[] minTier = {0, 0, 0, 1, 1, 2};
        for (int k = 0; k < STOCK; k++) {
            ItemStack best = ItemStack.EMPTY;
            for (int attempt = 0; attempt < 60; attempt++) {
                ItemStack st = new ItemStack(Registries.ITEM.get(pool().get(r.nextInt(pool().size()))));
                if (Rarity.of(st).tier < minTier[k] || CardSales.unitValue(st) > MAX_STOCK_VALUE) continue;
                best = st;
                break;
            }
            if (best.isEmpty()) best = new ItemStack(Registries.ITEM.get(pool().get(r.nextInt(pool().size()))));
            stock.add(best);
        }
    }

    /** La carta del inventario del jugador que le interesa (hueco), o -1. */
    private int wishSlot(ServerPlayerEntity player) {
        String want = wishes.get(player.getUuid());
        if (want != null && want.isEmpty()) return -1;
        var inv = player.getInventory();
        if (want != null) {
            // "id" o "id#variante" (reverse, 1ª edición...): la variante sólo puede estar en el inventario
            int hash = want.indexOf('#');
            String wid = hash < 0 ? want : want.substring(0, hash), wvar = hash < 0 ? "standard" : want.substring(hash + 1);
            for (int i = 0; i < 36; i++) {
                ItemStack st = inv.getStack(i);
                if (CardInfo.isCard(st) && CardInfo.id(st).toString().equals(wid) && dev.masterball.api.card.PrintVariant.of(st).key.equals(wvar)) return i;
            }
            if (hash < 0) {
                int b = dev.tcgshop.collection.BinderSlots.findInBinder(player, dev.tcgshop.collection.BinderSlots.stackFor(wid, 1));
                if (b >= 0) return b;
            }
        }
        // elige una nueva entre inventario y Binder: prefiere las de más valor, con algo de azar
        List<Integer> cards = new ArrayList<>();
        for (int i = 0; i < 36; i++) if (CardInfo.isCard(inv.getStack(i))) cards.add(i);
        var bd = dev.tcgshop.collection.CollectionService.ownedBinder(player);
        if (bd != null) {
            int n = dev.tcgshop.collection.BinderSlots.ids(bd.counts).size();
            for (int i = 0; i < n; i++) cards.add(dev.tcgshop.collection.BinderSlots.BASE + i);
        }
        if (cards.isEmpty()) return -1;
        cards.sort((a, b) -> Long.compare(CardSales.unitValue(dev.tcgshop.collection.BinderSlots.peek(player, b)),
                CardSales.unitValue(dev.tcgshop.collection.BinderSlots.peek(player, a))));
        int top = Math.max(1, Math.min(12, (cards.size() + 1) / 2));
        int slot = cards.get(random.nextInt(top));
        ItemStack picked = dev.tcgshop.collection.BinderSlots.peek(player, slot);
        dev.masterball.api.card.PrintVariant pv = dev.masterball.api.card.PrintVariant.of(picked);
        wishes.put(player.getUuid(), CardInfo.id(picked).toString() + (pv == dev.masterball.api.card.PrintVariant.STANDARD ? "" : "#" + pv.key));
        return slot;
    }

    public void open(ServerPlayerEntity player) {
        rollStock();
        List<Long> prices = new ArrayList<>();
        for (ItemStack st : stock) prices.add(CardSales.unitValue(st));
        int ws = tradesLeft > 0 ? wishSlot(player) : -1;
        long wp = ws < 0 ? 0 : Math.round(CardSales.unitValue(dev.tcgshop.collection.BinderSlots.peek(player, ws)) * WISH_BONUS);
        var p = new MarketPayloads.TraderOpen(getId(), List.copyOf(stock), prices, ws, wp, tradesLeft);
        if (ServerPlayNetworking.canSend(player, p.getId())) ServerPlayNetworking.send(player, p);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity sp) {
            playSound(SoundEvents.ENTITY_WANDERING_TRADER_YES, 1f, 1.1f);
            getNavigation().stop();
            open(sp);
        }
        return ActionResult.success(getWorld().isClient);
    }

    public void action(ServerPlayerEntity player, MarketPayloads.TraderAction a) {
        if (squaredDistanceTo(player) > 10 * 10) return;
        if (tradesLeft <= 0) {
            player.sendMessage(Text.translatable("tcgshop.trader.no_more").formatted(Formatting.GRAY), true);
            return;
        }
        var inv = player.getInventory();
        if (a.slot() < 0 || (a.slot() >= 36 && !dev.tcgshop.collection.BinderSlots.isBinder(a.slot()))) return;
        ItemStack mine = dev.tcgshop.collection.BinderSlots.peek(player, a.slot());
        if (!CardInfo.isCard(mine)) return;
        if (a.action() == MarketPayloads.SELL_WISH) {
            if (a.slot() != wishSlot(player)) return;
            if (!CobbleDollarsBridge.isAvailable()) {
                player.sendMessage(Text.translatable("tcgshop.sell.no_bank").formatted(Formatting.RED), true);
                return;
            }
            long price = Math.round(CardSales.unitValue(mine) * WISH_BONUS);
            Text name = mine.getName();
            if (dev.tcgshop.collection.BinderSlots.take(player, a.slot(), 1).isEmpty()) return;
            dev.tcgshop.collection.BinderSlots.sync(player);
            CobbleDollarsBridge.add(player, price);
            wishes.put(player.getUuid(), "");
            tradesLeft--;
            player.sendMessage(Text.translatable("tcgshop.trader.sold", name, Text.literal(PriceService.format(price)).formatted(Formatting.GOLD)), false);
            playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, 1f, 1.1f);
        } else if (a.action() == MarketPayloads.TRADE) {
            if (a.index() < 0 || a.index() >= stock.size() || stock.get(a.index()).isEmpty()) return;
            ItemStack his = stock.get(a.index());
            long hv = CardSales.unitValue(his), mv = CardSales.unitValue(mine);
            if (mv < hv * MIN_RATIO) {
                player.sendMessage(Text.translatable("tcgshop.trader.too_low").formatted(Formatting.RED), true);
                playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            ItemStack given = dev.tcgshop.collection.BinderSlots.take(player, a.slot(), 1);
            if (given.isEmpty()) return;
            dev.tcgshop.collection.BinderSlots.sync(player);
            inv.offerOrDrop(his.copy());
            stock.set(a.index(), given);
            tradesLeft--;
            player.sendMessage(Text.translatable("tcgshop.trader.traded", given.getName(), his.getName()).formatted(Formatting.GREEN), false);
            playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1f, 1f);
        }
        open(player);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (!hasCustomName()) {
            setCustomName(Text.literal(NpcIdentity.randomName(random)));
            setCustomNameVisible(true);
            dataTracker.set(TITLE, NpcIdentity.randomTitle(random, 0.6f));
            dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
        }
        if (--lifeTicks <= 0) discard();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        rollStock();
        NbtList l = new NbtList();
        for (ItemStack st : stock) if (!st.isEmpty()) l.add(st.encode(getRegistryManager()));
        nbt.put("Stock", l);
        NbtCompound w = new NbtCompound();
        wishes.forEach((u, id) -> w.putString(u.toString(), id));
        nbt.put("Wishes", w);
        nbt.putInt("Trades", tradesLeft);
        nbt.putInt("Life", lifeTicks);
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        stock.clear();
        NbtList l = nbt.getList("Stock", 10);
        for (int i = 0; i < l.size(); i++) ItemStack.fromNbt(getRegistryManager(), l.getCompound(i)).ifPresent(stock::add);
        wishes.clear();
        NbtCompound w = nbt.getCompound("Wishes");
        for (String k : w.getKeys()) {
            String wid = dev.masterball.api.card.CardInfo.migrate(w.getString(k));
            if (dev.masterball.api.card.CardCatalog.isRemovedId(wid)) continue; // TCG Pocket: quitado en la 0.9
            try { wishes.put(UUID.fromString(k), wid); } catch (IllegalArgumentException ignored) {}
        }
        if (nbt.contains("Trades")) tradesLeft = nbt.getInt("Trades");
        if (nbt.contains("Life")) lifeTicks = nbt.getInt("Life");
        dataTracker.set(TITLE, nbt.getString("NpcTitle"));
        dataTracker.set(SKIN, nbt.contains("NpcSkin") ? nbt.getInt("NpcSkin") : 1);
    }
}
