package dev.tcgshop.shop;

import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.item.TcgItems;
import dev.tcgshop.network.ShopActionPayload;
import dev.tcgshop.network.ShopViewPayload;
import dev.tcgshop.network.ShopZonesPayload;
import dev.tcgshop.network.StoreBuyPayload;
import dev.tcgshop.network.StoreOpenPayload;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lógica de la tienda: productos y precios, compra directa, empleados, caja, catálogo y sincronización. */
public final class ShopServer {
    private static final Map<Item, Long> FIXED = new java.util.LinkedHashMap<>();
    // Economía (v3): muebles, empleados y teléfono se amortizan en pocos días de ventas; el stock al por mayor deja margen.
    // Valores por defecto; los reales vienen de config/tcgshop-economy.json (EconomyConfig.apply)
    public static long CASHIER_HIRE = 30_000, CASHIER_WAGE = 4_000, GUARD_HIRE = 45_000, GUARD_WAGE = 6_000;
    public static final long MYSTERY_PRICE = 25_000;
    private static final Map<String, Long> DEFAULT_FIXED = new java.util.LinkedHashMap<>();
    private static long defaultItemPrice = 5_000, pokedollPrice = 40_000, giantPokedollPrice = 150_000;
    private static long minPack = 100, minBox = 1_000, minCard = 10;
    /** Factor sobre el precio de mercado: Mayorista (cajas mucho más baratas) y Mercado del teléfono (cómodo, algo más caro). */
    public static double WHOLESALE_FACTOR = 0.6, PHONE_FACTOR = 0.8;

    /** Precios fijos por defecto (id de objeto -> precio), para rellenar el archivo de economía. */
    public static Map<String, Long> defaultFixedPrices() {
        if (DEFAULT_FIXED.isEmpty())
            for (Map.Entry<Item, Long> e : FIXED.entrySet()) DEFAULT_FIXED.put(Registries.ITEM.getId(e.getKey()).toString(), e.getValue());
        return DEFAULT_FIXED;
    }

    public static void applyEconomy(dev.tcgshop.EconomyConfig c) {
        defaultFixedPrices(); // fija los valores por defecto antes de sobrescribir
        CASHIER_HIRE = c.cashierHire;
        CASHIER_WAGE = c.cashierWage;
        GUARD_HIRE = c.guardHire;
        GUARD_WAGE = c.guardWage;
        WHOLESALE_FACTOR = c.wholesalerBuyFactor;
        PHONE_FACTOR = c.phoneBuyFactor;
        defaultItemPrice = c.defaultItemPrice;
        pokedollPrice = c.pokedollPrice;
        giantPokedollPrice = c.giganticPokedollPrice;
        minPack = c.minPackPrice;
        minBox = c.minBoxPrice;
        minCard = c.minCardPrice;
        for (Map.Entry<String, Long> e : c.itemPrices.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            if (id == null || !Registries.ITEM.containsId(id)) {
                dev.tcgshop.TcgShop.LOGGER.warn("tcgshop-economy.json: objeto desconocido {}", e.getKey());
                continue;
            }
            FIXED.put(Registries.ITEM.get(id), e.getValue());
        }
    }

    private ShopServer() {}

    public static void init() {
        FIXED.put(TcgBlocks.CARD_STAND_ITEM, 6_000L);
        FIXED.put(TcgBlocks.DISPLAY_CASE_ITEM, 18_000L);
        FIXED.put(TcgBlocks.PACK_SHELF_ITEM, 16_000L);
        FIXED.put(TcgBlocks.GONDOLA_ITEM, 22_000L);
        FIXED.put(TcgBlocks.BOX_PALLET_ITEM, 9_000L);
        FIXED.put(TcgBlocks.PEDESTAL_ITEM, 28_000L);
        FIXED.put(TcgBlocks.CARD_WALL_ITEM, 20_000L);
        FIXED.put(TcgBlocks.COUNTER_DISPLAY_ITEM, 8_000L);
        FIXED.put(TcgBlocks.SHOWCASE_ITEM, 14_000L);
        FIXED.put(TcgBlocks.CARD_LEDGE_ITEM, 4_000L);
        FIXED.put(TcgBlocks.BOX_SHELF_ITEM, 12_000L);
        FIXED.put(TcgBlocks.CARD_FRAME_ITEM, 2_500L);
        FIXED.put(TcgBlocks.HOME_FRAME_ITEM, 2_000L);
        FIXED.put(TcgBlocks.HOME_LEDGE_ITEM, 3_000L);
        FIXED.put(TcgBlocks.HOME_SHOWCASE_ITEM, 12_000L);
        FIXED.put(TcgBlocks.HOME_CARD_WALL_ITEM, 18_000L);
        FIXED.put(TcgBlocks.HOME_PEDESTAL_ITEM, 24_000L);
        FIXED.put(TcgBlocks.CASH_REGISTER_ITEM, 12_000L);
        FIXED.put(TcgBlocks.POSTER_ITEM, 1_500L);
        FIXED.put(TcgBlocks.PLAY_TABLE_ITEM, 9_000L);
        FIXED.put(TcgBlocks.THEFT_DETECTOR_ITEM, 20_000L);
        FIXED.put(TcgBlocks.ROULETTE_ITEM, 150_000L);
        FIXED.put(TcgBlocks.MYSTERY_BOX, MYSTERY_PRICE);
        FIXED.put(TcgItems.MANAGER_GOGGLES, 15_000L);
        FIXED.put(TcgItems.TCG_PHONE, 25_000L);
        FIXED.put(TcgItems.DEODORANT_SPRAY, 2_500L);
        FIXED.put(TcgItems.NERD_GLOVES, 3_000L);
        FIXED.put(TcgItems.GENERAL_BINDER, 6_000L);
        FIXED.put(TcgItems.PERSONAL_BINDER, 5_000L);
        for (ShopTier t : ShopTier.values()) FIXED.put(TcgBlocks.CORE_ITEMS.get(t), t.price);
    }

    // ------------------------------------------------------------------ productos y precios

    public static boolean isPokedoll(ItemStack st) {
        Identifier id = Registries.ITEM.getId(st.getItem());
        return id.getNamespace().equals("pokeblocks") && id.getPath().contains("pokedoll");
    }

    /** ¿Se puede poner a la venta en un expositor? */
    public static boolean isProduct(ItemStack st) {
        return CardInfo.isCard(st) || CardInfo.isPack(st) || st.isOf(TcgBlocks.BOOSTER_BOX_ITEM) || st.isOf(TcgBlocks.MYSTERY_BOX) || isPokedoll(st);
    }

    /** Sobres, cajas y mystery boxes sólo se venden con un cajero contratado. */
    public static boolean needsCashier(ItemStack st) {
        return CardInfo.isPack(st) || st.isOf(TcgBlocks.BOOSTER_BOX_ITEM) || st.isOf(TcgBlocks.MYSTERY_BOX);
    }

    /** Precio. factor: WHOLESALE_FACTOR, PHONE_FACTOR o 1.0 (mercado). Muebles y herramientas: precio fijo. */
    public static long priceOf(ItemStack st, double factor) {
        if (st.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) {
            String set = st.getOrDefault(TcgBlocks.BOX_SET, "mew151");
            return Math.max(minBox, Math.round(PriceService.sealedCd(set, PriceService.Sealed.BOX) * factor));
        }
        if (CardInfo.isPack(st)) return Math.max(minPack, Math.round(PriceService.sealedCd(CardInfo.setId(CardInfo.id(st)), PriceService.Sealed.PACK) * factor));
        if (CardInfo.isCard(st)) return Math.max(minCard, Math.round(PriceService.priceCd(st) * factor));
        if (isPokedoll(st)) {
            boolean giant = Registries.ITEM.getId(st.getItem()).getPath().contains("gigantic");
            return Math.round((giant ? giantPokedollPrice : pokedollPrice) * Math.max(0.8, factor));
        }
        Long fixed = FIXED.get(st.getItem());
        return fixed == null ? defaultItemPrice : fixed;
    }

    /** Lo que vende el Mayorista (y también el teléfono, que además tiene sobres sueltos y la ruleta). */
    public static List<ItemStack> wholesaleGoods(List<String> boxSets, @Nullable net.minecraft.util.math.random.Random r) {
        List<ItemStack> g = new ArrayList<>();
        for (String s : boxSets) g.add(BoosterBox.of(s));
        g.add(new ItemStack(TcgBlocks.MYSTERY_BOX));
        for (ShopTier t : ShopTier.values()) g.add(new ItemStack(TcgBlocks.CORE_ITEMS.get(t)));
        for (Item it : TcgBlocks.NEW_SHOP_DISPLAYS) g.add(new ItemStack(it));
        for (Item it : TcgBlocks.HOME_DISPLAYS) g.add(new ItemStack(it));
        for (Item it : new Item[]{TcgBlocks.CARD_STAND_ITEM, TcgBlocks.DISPLAY_CASE_ITEM, TcgBlocks.PACK_SHELF_ITEM, TcgBlocks.GONDOLA_ITEM,
                TcgBlocks.BOX_PALLET_ITEM, TcgBlocks.PEDESTAL_ITEM, TcgBlocks.CARD_WALL_ITEM, TcgBlocks.CASH_REGISTER_ITEM, TcgBlocks.POSTER_ITEM, TcgBlocks.PLAY_TABLE_ITEM,
                TcgBlocks.THEFT_DETECTOR_ITEM, TcgItems.MANAGER_GOGGLES, TcgItems.TCG_PHONE, TcgItems.DEODORANT_SPRAY, TcgItems.NERD_GLOVES, TcgItems.GENERAL_BINDER, TcgItems.PERSONAL_BINDER})
            g.add(new ItemStack(it));
        List<Item> dolls = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds())
            if (id.getNamespace().equals("pokeblocks") && id.getPath().contains("pokedoll")) dolls.add(Registries.ITEM.get(id));
        if (!dolls.isEmpty()) {
            if (r == null) for (Item d : dolls) g.add(new ItemStack(d));
            else for (int i = 0; i < 6; i++) g.add(new ItemStack(dolls.get(r.nextInt(dolls.size()))));
        }
        return g;
    }

    private static List<ItemStack> phoneCache;

    /** Catálogo del teléfono: TODO lo del Mayorista (todas las cajas), sobres sueltos, peluches y la Ruleta Poké. */
    public static List<ItemStack> phoneCatalog() {
        if (phoneCache != null) return phoneCache;
        List<String> sets = new ArrayList<>();
        List<ItemStack> packs = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) {
                sets.add(CardInfo.setId(id));
                packs.add(new ItemStack(Registries.ITEM.get(id)));
            }
        List<ItemStack> out = wholesaleGoods(sets, null);
        out.addAll(packs);
        out.add(new ItemStack(TcgBlocks.ROULETTE_ITEM)); // sólo se vende por el teléfono
        phoneCache = out;
        return out;
    }

    // ------------------------------------------------------------------ expositores

    /** El dueño pone a la venta lo que tiene en la mano en la casilla `slot` (o en la primera libre). */
    /** ¿Se puede reponer esa casilla con lo que llevas? (mismo producto y aún cabe más). */
    public static boolean canMerge(ItemStack inSlot, ItemStack held) {
        return !inSlot.isEmpty() && !held.isEmpty() && inSlot.getMaxCount() > 1 && inSlot.getCount() < inSlot.getMaxCount()
                && ItemStack.areItemsAndComponentsEqual(inSlot, held);
    }

    public static void stock(ServerPlayerEntity owner, ShopDisplayBlockEntity be, ItemStack held, int slot) {
        if (slot >= 0 && canMerge(be.getStack(slot), held)) {
            ItemStack cur = be.getStack(slot).copy();
            int add = Math.min(held.getCount(), cur.getMaxCount() - cur.getCount());
            cur.increment(add);
            held.decrement(add);
            be.setStack(slot, cur);
            owner.sendMessage(Text.translatable("tcgshop.shop.restocked", add, cur.toHoverableText(), cur.getCount()), true);
            owner.getWorld().playSound(null, be.getPos(), SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1f, 1.3f);
            return;
        }
        int target = slot >= 0 && be.getStack(slot).isEmpty() ? slot : -1;
        if (target < 0) for (int i = 0; i < be.size(); i++) if (be.getStack(i).isEmpty()) { target = i; break; }
        if (target < 0) {
            owner.sendMessage(Text.translatable("tcgshop.shop.full").formatted(Formatting.RED), true);
            return;
        }
        ItemStack put = held.split(held.getMaxCount() > 1 ? held.getCount() : 1);
        be.setStack(target, put);
        be.setPrice(target, priceOf(put, 1.0));
        owner.sendMessage(Text.translatable("tcgshop.shop.stocked", put.toHoverableText(),
                Text.literal(PriceService.format(be.price(target))).formatted(Formatting.GOLD)), true);
        owner.getWorld().playSound(null, be.getPos(), SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1f, 1.1f);
        if (needsCashier(put)) {
            ShopZones.Zone z = ShopZones.get(owner.getServerWorld()).at(be.getPos());
            if (z != null && !z.cashier) owner.sendMessage(Text.translatable("tcgshop.shop.needs_cashier_hint").formatted(Formatting.YELLOW), false);
        }
    }

    /** Compra directa con clic derecho (también el dueño, para probar): el dinero va a la caja de la tienda. */
    public static void buyDirect(ServerPlayerEntity player, ShopDisplayBlockEntity be, int slot) {
        ServerWorld world = player.getServerWorld();
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone z = zones.at(be.getPos());
        ItemStack st = be.getStack(slot);
        if (z == null || st.isEmpty()) return;
        if (needsCashier(st) && !cashierOnDuty(world, z)) {
            player.sendMessage(Text.translatable(z.cashier || z.cashier2 ? "tcgshop.shop.no_cashier_night" : "tcgshop.shop.no_cashier").formatted(Formatting.RED), true);
            return;
        }
        long price = be.price(slot);
        if (!CobbleDollarsBridge.tryCharge(player, price)) {
            player.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(price)).formatted(Formatting.RED), true);
            world.playSound(null, be.getPos(), SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 0.6f, 1f);
            return;
        }
        ItemStack bought = be.removeStack(slot, 1);
        player.getInventory().offerOrDrop(bought);
        boolean own = z.owner.equals(player.getUuid());
        long earned = bought.isOf(TcgBlocks.MYSTERY_BOX) && !own ? MysteryBoxItem.ownerEarning(price, world.random) : price;
        z.balance += earned;
        if (!own) {
            z.revenue += earned;
            z.sales++;
            CardAdvancements.grant(player, "customer");
            ServerPlayerEntity ownerP = world.getServer().getPlayerManager().getPlayer(z.owner);
            if (ownerP != null) {
                ownerP.sendMessage(Text.translatable("tcgshop.shop.sale", player.getDisplayName(), bought.toHoverableText(),
                        Text.literal(PriceService.format(earned)).formatted(Formatting.GOLD)), false);
                saleAdvancements(ownerP, z);
            }
        }
        zones.markDirty();
        player.sendMessage(Text.translatable("tcgshop.shop.bought", bought.toHoverableText(),
                Text.literal(PriceService.format(price)).formatted(Formatting.GOLD)), true);
        ringRegister(world, be.getPos());
        world.playSound(null, be.getPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.8f, 1.4f);
        syncZones(world.getServer());
    }

    /** Venta a un NPC: quita 1 unidad del expositor y cobra (con la suerte de la mystery box si aplica). */
    public static long sellToNpc(ServerWorld world, ShopZones.Zone z, ShopDisplayBlockEntity be, int slot, long price) {
        ItemStack bought = be.removeStack(slot, 1);
        if (bought.isEmpty()) return 0;
        return payFromNpc(world, z, bought, price, be.getPos());
    }

    /** Un NPC paga un producto que ya ha cogido (en la caja registradora más cercana a `at`). */
    public static long payFromNpc(ServerWorld world, ShopZones.Zone z, ItemStack bought, long price, BlockPos at) {
        long earned = bought.isOf(TcgBlocks.MYSTERY_BOX) ? MysteryBoxItem.ownerEarning(price, world.random) : price;
        z.balance += earned;
        z.revenue += earned;
        z.sales++;
        z.customers++;
        ShopZones.get(world).markDirty();
        ringRegister(world, at);
        ServerPlayerEntity ownerP = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (ownerP != null) saleAdvancements(ownerP, z);
        return earned;
    }

    public static void saleAdvancements(ServerPlayerEntity owner, ShopZones.Zone z) {
        CardAdvancements.grant(owner, "first_sale");
        if (z.sales >= 100) CardAdvancements.grant(owner, "sales_100");
        if (z.sales >= 1000) CardAdvancements.grant(owner, "sales_1000");
        if (z.revenue >= 1_000_000) CardAdvancements.grant(owner, "revenue_1m");
    }

    /** Abre el cajón de la caja registradora más cercana (animación). */
    public static void ringRegister(ServerWorld world, BlockPos near) {
        for (BlockPos p : BlockPos.iterateOutwards(near, 8, 3, 8)) {
            if (world.getBlockEntity(p) instanceof DecorBlocks.DecorEntity be && world.getBlockState(p).isOf(TcgBlocks.CASH_REGISTER)) {
                be.animate(0);
                world.playSound(null, p, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.7f, 1.8f);
                return;
            }
        }
    }

    public static void openDisplay(ServerPlayerEntity player, ShopDisplayBlockEntity be) {
        ShopZones.Zone z = ShopZones.get(player.getServerWorld()).at(be.getPos());
        boolean owner = z != null && z.owner.equals(player.getUuid());
        List<ItemStack> items = new ArrayList<>();
        List<Long> prices = new ArrayList<>();
        for (int i = 0; i < be.size(); i++) {
            items.add(be.getStack(i).copy());
            prices.add(be.price(i));
        }
        send(player, new ShopViewPayload(be.getPos(), items, prices, owner, z == null ? "?" : z.ownerName));
    }

    public static void handleAction(ShopActionPayload p, ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (player.squaredDistanceTo(p.pos().toCenterPos()) > 64) return;
        if (!(world.getBlockEntity(p.pos()) instanceof ShopDisplayBlockEntity be)) return;
        ShopZones.Zone z = ShopZones.get(world).at(p.pos());
        if (z == null || p.slot() < 0 || p.slot() >= be.size()) return;
        boolean owner = z.owner.equals(player.getUuid());
        switch (p.action()) {
            case ShopActionPayload.BUY -> buyDirect(player, be, p.slot());
            case ShopActionPayload.SET_PRICE -> { if (owner) be.setPrice(p.slot(), p.value()); }
            case ShopActionPayload.TAKE -> { if (owner && !be.getStack(p.slot()).isEmpty()) player.getInventory().offerOrDrop(be.removeStack(p.slot())); }
            default -> {}
        }
        openDisplay(player, be);
    }

    // ------------------------------------------------------------------ editar una casilla concreta

    /** Abre el menú de una carta/producto colocado: tomarlo o editar precio, tamaño y giro. */
    public static void openSlot(ServerPlayerEntity player, ShopDisplayBlockEntity be, int slot) {
        ItemStack st = be.getStack(slot);
        if (st.isEmpty()) return;
        net.minecraft.nbt.NbtCompound d = new net.minecraft.nbt.NbtCompound();
        d.putLong("pos", be.getPos().asLong());
        d.putInt("slot", slot);
        d.put("item", st.encode(player.getRegistryManager()));
        d.putLong("price", be.price(slot));
        d.putLong("market", priceOf(st.copyWithCount(1), 1.0));
        d.putFloat("scale", be.lookScale(slot));
        d.putInt("rx", be.lookRot(slot, 0));
        d.putInt("ry", be.lookRot(slot, 1));
        d.putInt("rz", be.lookRot(slot, 2));
        d.putBoolean("home", be.isHome());
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, dev.tcgshop.network.UiPayloads.Open.ID))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.tcgshop.network.UiPayloads.Open("slot", d));
    }

    /** Respuesta del menú: take (a tu inventario), save (precio + aspecto) o reset (aspecto por defecto y precio de mercado). */
    public static void slotAction(ServerPlayerEntity player, net.minecraft.nbt.NbtCompound d) {
        ServerWorld world = player.getServerWorld();
        BlockPos pos = BlockPos.fromLong(d.getLong("pos"));
        if (player.squaredDistanceTo(pos.toCenterPos()) > 10 * 10 || !(world.getBlockEntity(pos) instanceof ShopDisplayBlockEntity be)) return;
        int slot = d.getInt("slot");
        if (slot < 0 || slot >= be.size() || be.getStack(slot).isEmpty()) return;
        boolean allowed;
        if (be.isHome()) allowed = be.mayEdit(player);
        else {
            ShopZones.Zone z = ShopZones.get(world).at(pos);
            allowed = z != null && z.owner.equals(player.getUuid());
        }
        if (!allowed) return;
        switch (d.getString("action")) {
            case "take" -> {
                player.getInventory().offerOrDrop(be.removeStack(slot));
                world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1f, 1f);
            }
            case "save" -> {
                if (!be.isHome() && d.contains("price")) be.setPrice(slot, d.getLong("price"));
                be.setLook(slot, d.getFloat("scale"), d.getInt("rx"), d.getInt("ry"), d.getInt("rz"));
                world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.BLOCKS, 1f, 1.2f);
                player.sendMessage(Text.translatable("tcgshop.slot.saved").formatted(Formatting.GREEN), true);
            }
            case "reset" -> {
                be.resetLook(slot);
                if (!be.isHome()) be.setPrice(slot, priceOf(be.getStack(slot).copyWithCount(1), 1.0));
                world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ROTATE_ITEM, SoundCategory.BLOCKS, 1f, 0.9f);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ empleados

    /** Contratar / despedir desde el teléfono. role: "cashier" o "guard". */
    public static void manageStaff(ServerPlayerEntity player, UUID zoneId, String role, boolean hire) {
        ServerWorld world = player.getServerWorld();
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone z = zones.byId(zoneId);
        if (z == null || !z.owner.equals(player.getUuid())) return;
        boolean cashier = role.equals("cashier") || role.equals("cashier2");
        boolean second = role.equals("cashier2");
        if (hire) {
            if (second ? z.cashier2 : cashier ? z.cashier : z.guard) return;
            long cost = cashier ? CASHIER_HIRE : GUARD_HIRE;
            if (!CobbleDollarsBridge.tryCharge(player, cost)) {
                player.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(cost)).formatted(Formatting.RED), false);
                return;
            }
            zones.addCost(player.getUuid(), cost);
            String name = dev.tcgshop.entity.NpcIdentity.randomName(world.random);
            if (second) { z.cashier2 = true; z.cashier2Name = name; }
            else if (cashier) { z.cashier = true; z.cashierName = name; } else { z.guard = true; z.guardName = name; }
            if (z.lastWageDay < 0) z.lastWageDay = world.getTimeOfDay() / 24000;
            if (!cashier || isDayShift(world)) dev.tcgshop.entity.EmployeeEntity.spawnFor(world, z, cashier, second ? 1 : 0);
            else player.sendMessage(Text.translatable("tcgshop.staff.starts_morning", name).formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.translatable(cashier ? "tcgshop.staff.hired_cashier" : "tcgshop.staff.hired_guard", name).formatted(Formatting.GREEN), false);
            CardAdvancements.grant(player, cashier ? "hire_cashier" : "hire_guard");
        } else {
            String who = second ? z.cashier2Name : cashier ? z.cashierName : z.guardName;
            if (second) z.cashier2 = false; else if (cashier) z.cashier = false; else z.guard = false;
            dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, cashier, second ? 1 : 0);
            player.sendMessage(Text.translatable("tcgshop.staff.fired", who).formatted(Formatting.YELLOW), false);
        }
        zones.markDirty();
        syncZones(world.getServer());
    }

    /** Turno de los cajeros: de 6:00 a 18:30 (de noche se van a casa). */
    public static boolean isDayShift(ServerWorld world) {
        return Math.floorMod(world.getTimeOfDay(), 24000L) < 12500;
    }

    /** ¿Hay alguien cobrando? Un cajero en su turno, o el dueño junto a una caja registradora de la tienda. */
    public static boolean cashierOnDuty(ServerWorld world, ShopZones.Zone z) {
        if ((z.cashier || z.cashier2) && isDayShift(world)) return true;
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner == null || owner.getServerWorld() != world || !z.contains(owner.getBlockPos())) return false;
        for (BlockPos p : BlockPos.iterateOutwards(owner.getBlockPos(), 5, 2, 5))
            if (world.getBlockState(p).isOf(TcgBlocks.CASH_REGISTER)) return true;
        return false;
    }

    /** Cada pocos segundos: los cajeros entran al amanecer y se van al anochecer; se avisa al dueño. */
    public static void shiftTick(ServerWorld world) {
        boolean day = isDayShift(world);
        ShopZones zones = ShopZones.get(world);
        for (ShopZones.Zone z : zones.all()) {
            if (!z.cashier && !z.cashier2) { z.shiftSeen = null; continue; }
            if (day) {
                for (int slot = 0; slot < 2; slot++) {
                    if (slot == 0 ? !z.cashier : !z.cashier2) continue;
                    final int sl = slot;
                    boolean present = !world.getEntitiesByClass(dev.tcgshop.entity.EmployeeEntity.class, z.box().expand(16),
                            e -> z.id.equals(e.zoneId()) && !e.isGuard() && e.slot() == sl).isEmpty();
                    if (!present && world.isChunkLoaded(z.core)) dev.tcgshop.entity.EmployeeEntity.spawnFor(world, z, true, slot);
                }
            }
            if (z.shiftSeen != null && z.shiftSeen != day) {
                ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
                if (owner != null) owner.sendMessage(Text.translatable(day ? "tcgshop.staff.shift_start" : "tcgshop.staff.shift_end").formatted(day ? Formatting.GREEN : Formatting.GOLD), false);
                syncZones(world.getServer());
            }
            z.shiftSeen = day;
        }
    }

    /** Sueldos: una vez por día de Minecraft se pagan de la caja de la tienda (si no hay dinero, el empleado se va). */
    public static void payWages(ServerWorld world) {
        ShopZones zones = ShopZones.get(world);
        long day = world.getTimeOfDay() / 24000;
        boolean changed = false;
        for (ShopZones.Zone z : zones.all()) {
            if ((!z.cashier && !z.cashier2 && !z.guard) || z.lastWageDay < 0 || z.lastWageDay >= day) continue;
            z.lastWageDay = day;
            long wage = (z.cashier ? CASHIER_WAGE : 0) + (z.cashier2 ? CASHIER_WAGE : 0) + (z.guard ? GUARD_WAGE : 0);
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
            if (z.balance >= wage) {
                z.balance -= wage;
                z.wagesPaid += wage;
                if (owner != null) owner.sendMessage(Text.translatable("tcgshop.staff.wages_paid", PriceService.format(wage)).formatted(Formatting.GRAY), false);
            } else {
                if (z.cashier2) { z.cashier2 = false; dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, true, 1); }
                else if (z.guard) { z.guard = false; dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, false, 0); }
                else if (z.cashier) { z.cashier = false; dev.tcgshop.entity.EmployeeEntity.removeFor(world, z, true, 0); }
                if (owner != null) owner.sendMessage(Text.translatable("tcgshop.staff.quit_unpaid").formatted(Formatting.RED), false);
            }
            changed = true;
        }
        if (changed) {
            zones.markDirty();
            syncZones(world.getServer());
        }
    }

    // ------------------------------------------------------------------ zona / caja registradora

    public static void describe(ServerPlayerEntity player, ShopZones.Zone z) {
        ShopZones zones = ShopZones.get(player.getServerWorld());
        player.sendMessage(Text.translatable("tcgshop.shop.describe", z.ownerName, Text.translatable("tcgshop.shop.tier." + z.tierInfo().key),
                z.tierInfo().size, z.tierInfo().size).formatted(Formatting.GOLD), false);
        if (z.owner.equals(player.getUuid())) {
            long profit = zones.revenue(player.getUuid()) - zones.costs(player.getUuid());
            player.sendMessage(Text.translatable("tcgshop.shop.stats", PriceService.format(z.revenue), z.sales, PriceService.format(z.balance),
                    Text.literal(PriceService.format(profit)).formatted(profit >= 0 ? Formatting.GREEN : Formatting.RED)).formatted(Formatting.GRAY), false);
            player.sendMessage(Text.translatable("tcgshop.shop.rep_line", z.reputation).formatted(Formatting.AQUA), false);
        }
    }

    public static void useRegister(ServerPlayerEntity player, ShopZones.Zone z) {
        if (!z.owner.equals(player.getUuid())) {
            player.sendMessage(Text.translatable("tcgshop.shop.register_of", z.ownerName).formatted(Formatting.GRAY), true);
            return;
        }
        if (z.balance <= 0) {
            describe(player, z);
            return;
        }
        collect(player, z, 0);
    }

    /** Sueldos de un día de toda la plantilla (para "retirar dejando sueldos"). */
    public static long dailyWages(ShopZones.Zone z) {
        return (z.cashier ? CASHIER_WAGE : 0) + (z.cashier2 ? CASHIER_WAGE : 0) + (z.guard ? GUARD_WAGE : 0);
    }

    /** Retira el dinero de la caja a tu cuenta de CobbleDollars; keep = lo que se deja dentro (p. ej. para sueldos). */
    public static void collect(ServerPlayerEntity player, ShopZones.Zone z, long keep) {
        if (!z.owner.equals(player.getUuid())) return;
        long amount = z.balance - Math.max(0, keep);
        if (amount <= 0) {
            player.sendMessage(Text.translatable("tcgshop.shop.nothing_to_collect").formatted(Formatting.GRAY), true);
            return;
        }
        if (!CobbleDollarsBridge.add(player, amount)) {
            player.sendMessage(Text.translatable("tcgshop.shop.no_cobbledollars").formatted(Formatting.RED), false);
            return;
        }
        z.balance -= amount;
        ShopZones.get(player.getServerWorld()).markDirty();
        player.sendMessage(Text.translatable("tcgshop.shop.collected", Text.literal(PriceService.format(amount)).formatted(Formatting.GOLD)), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1f, 1.6f);
        syncZones(player.getServer());
    }

    // ------------------------------------------------------------------ catálogo (Mayorista / teléfono)

    public static void openStore(ServerPlayerEntity player, int source, List<ItemStack> goods, double factor) {
        List<ItemStack> all = goods;
        goods = PackLock.visibleTo(player, goods);
        if (goods.size() < all.size())
            player.sendMessage(Text.translatable("tcgshop.lock.need_tier", Text.translatable("tcgshop.shop.tier." + PackLock.minTier().key)).formatted(Formatting.GOLD), false);
        List<Long> prices = new ArrayList<>();
        for (ItemStack st : goods) prices.add(priceOf(st, factor));
        send(player, new StoreOpenPayload(source, goods, prices));
    }

    public static void buy(StoreBuyPayload p, ServerPlayerEntity player, List<ItemStack> goods, double factor) {
        goods = PackLock.visibleTo(player, goods);
        if (p.index() < 0 || p.index() >= goods.size()) return;
        int qty = Math.max(1, Math.min(64, p.qty()));
        ItemStack item = goods.get(p.index());
        long total = priceOf(item, factor) * qty;
        if (!CobbleDollarsBridge.tryCharge(player, total)) {
            player.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(total)).formatted(Formatting.RED), true);
            return;
        }
        for (int i = 0; i < qty; i++) player.getInventory().offerOrDrop(item.copyWithCount(1));
        ShopZones.get(player.getServerWorld()).addCost(player.getUuid(), total);
        player.sendMessage(Text.translatable("tcgshop.store.bought", qty, item.getName(),
                Text.literal(PriceService.format(total)).formatted(Formatting.GOLD)), false);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.6f, 1.2f);
        if (item.isOf(TcgBlocks.BOOSTER_BOX_ITEM)) CardAdvancements.grant(player, "buy_box");
        syncZones(player.getServer());
    }

    // ------------------------------------------------------------------ sincronización de zonas

    public static void syncZones(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) syncZones(p);
    }

    public static void syncZones(ServerPlayerEntity player) {
        ShopZones zones = ShopZones.get(player.getServerWorld());
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();
        for (ShopZones.Zone z : zones.all()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("id", z.id);
            c.putUuid("owner", z.owner);
            c.putString("name", z.ownerName);
            c.putLong("core", z.core.asLong());
            c.putInt("tier", z.tier);
            c.putLong("revenue", z.revenue);
            c.putLong("balance", z.balance);
            c.putInt("sales", z.sales);
            if (z.owner.equals(player.getUuid())) { // detalles sólo para el dueño
                c.putBoolean("cashier", z.cashier);
                c.putBoolean("guard", z.guard);
                c.putString("cashier_name", z.cashierName);
                c.putString("guard_name", z.guardName);
                NbtCompound ex = new NbtCompound();
                ex.putBoolean("cashier2", z.cashier2);
                ex.putString("cashier2_name", z.cashier2Name);
                ex.putBoolean("day", isDayShift(player.getServerWorld()));
                c.put("ext", ex);
                c.putLong("wages", z.wagesPaid);
                c.putIntArray("metrics", new int[]{z.stolen, z.detained, z.recovered, z.customers, z.complaints, z.dealsAccepted, z.whales,
                        z.reputation, z.decor, z.detectors, z.displays, z.buzz, z.smell});
                NbtCompound f = new NbtCompound();
                z.factors.forEach(f::putInt);
                c.put("factors", f);
                net.minecraft.nbt.NbtList rl = new net.minecraft.nbt.NbtList();
                for (ShopZones.Review r : z.reviews) rl.add(r.toNbt());
                c.put("reviews", rl);
                c.putInt("regulars", z.regulars.size());
            } else c.putIntArray("metrics", new int[]{0, 0, 0, 0, 0, 0, 0, z.reputation});
            list.add(c);
        }
        root.put("zones", list);
        root.putLong("myCosts", zones.costs(player.getUuid()));
        root.putLong("myRevenue", zones.revenue(player.getUuid()));
        send(player, new ShopZonesPayload(root));
    }

    private static void send(ServerPlayerEntity player, net.minecraft.network.packet.CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) ServerPlayNetworking.send(player, payload);
    }
}
