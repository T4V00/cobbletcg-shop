package dev.tcgshop.entity;

import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.card.CardInfo;
import dev.tcgshop.card.Rarity;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.compat.CobblemonBattles;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.DealOpenPayload;
import dev.tcgshop.network.DealAnswerPayload;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.BoosterBox;
import dev.tcgshop.shop.DecorBlocks;
import dev.tcgshop.shop.ShopDisplayBlock;
import dev.tcgshop.shop.ShopDisplayBlockEntity;
import dev.tcgshop.shop.ShopServer;
import dev.tcgshop.shop.ShopZones;
import dev.tcgshop.shop.TcgBlocks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cliente de la tienda. Llega andando desde fuera, recorre los expositores, mira los productos y decide según el
 * precio frente al mercado, su personalidad y la reputación. Si compra, coge el producto (lo lleva en la mano),
 * va a pagar a la caja registradora y se marcha con él. Al irse deja una reseña (1-5 estrellas) y, si salió
 * contento, puede volver otro día como cliente habitual.
 *
 * Tipos:
 *  · REGULAR   compra si el precio es razonable.
 *  · PICKY     quisquilloso: sólo compra por debajo del mercado y se queja si está caro.
 *  · WHALE     coleccionista adinerado: paga hasta casi el doble por cosas raras.
 *  · THIEF     ladrón: roba un producto y huye. Con guardia, siempre lo detienen; sin guardia huye como un
 *              Scalper (puedes perseguirlo, pegarle o retarlo a un combate Pokémon para recuperar lo robado).
 *  · HAGGLER   regateador: te ofrece comprar algo por encima o por debajo de tu precio.
 *  · SELLER    vendedor: quiere venderte una carta, un sobre o una caja, y te cuenta por qué.
 *  · TRADER    coleccionista que quiere cambiarte una carta suya por una de tus expositores.
 * Los que quieren un trato muestran un "!" sobre la cabeza: haz clic derecho para verlo.
 */
public class CustomerEntity extends PathAwareEntity implements TcgNpc, CobblemonBattles.Fighter {
    public enum Type { REGULAR, PICKY, WHALE, THIEF, HAGGLER, SELLER, TRADER }

    private enum State { ENTER, GO, BROWSE, CHECKOUT, WAIT_DEAL, LEAVE, FLEE }

    private static final String[] THIEF_POOL = {"purrloin", "nickit", "sneasel", "murkrow", "zorua", "meowth", "sableye", "impidimp", "poochyena"};

    private static final TrackedData<String> TITLE = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> SKIN = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EMOTE = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TYPE = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WANTS = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SMELLY = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> WAIT = DataTracker.registerData(CustomerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID zoneId;
    private State state = State.ENTER;
    private BlockPos target;
    private int targetSlot = -1, timer, browsesLeft;
    private boolean detected, entered, reviewed, returning;
    /** Ya ha tenido un duelo Pokémon con el guardia (sólo uno por huida). */
    public boolean duelled;
    private ItemStack loot = ItemStack.EMPTY;
    private ItemStack dealItem = ItemStack.EMPTY, dealWant = ItemStack.EMPTY;
    private int dealMode;
    private long dealPrice;
    private int reasonKey;
    // comerciante que te vende algo: personalidad, motivo y regateo
    public static final int P_COLLECTOR = 0, P_BEGINNER = 1, P_EXPERT = 2, P_NEGOTIATOR = 3, P_DESPERATE = 4, P_CASUAL = 5;
    public static final String[] PERSONAS = {"collector", "beginner", "expert", "negotiator", "desperate", "casual"};
    private static final int[] PERSONA_W = {16, 18, 12, 18, 14, 22};
    /** Motivos de venta (tcgshop.motive.<cat>.<n>): dinero urgente, sacar beneficio y otros. */
    public static final int MOTIVES_CASH = 40, MOTIVES_PROFIT = 35, MOTIVES_OTHER = 45;
    /** Peso de cada tipo de motivo según la personalidad (dinero, beneficio, otros): casi siempre es por dinero. */
    private static final int[][] MOTIVE_W = {{25, 35, 40}, {35, 25, 40}, {25, 60, 15}, {20, 70, 10}, {85, 5, 10}, {40, 35, 25}};
    private int persona = -1, patience, maxPatience, thinks, waitMax = 1;
    private String motive = "";
    private long floorPrice;
    private Vec3d exit, spot;
    /** Compra pendiente de pagar en caja. */
    private ItemStack bought = ItemStack.EMPTY;
    private long payPrice;
    private BlockPos register;
    /** Ánimo para la reseña final (empieza en 3 estrellas). */
    private int mood = 3;
    private String reviewKey = "browse";
    // huida "a lo Scalper"
    private int fleePhaseTicks, lostTicks;
    private boolean admiring;

    public CustomerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        if (getNavigation() instanceof net.minecraft.entity.ai.pathing.MobNavigation nav) {
            nav.setCanPathThroughDoors(true); // abren las puertas de madera de la tienda
            nav.setCanEnterOpenDoors(true);
        }
    }

    /** Turnos de la fila de la caja (quien llegó antes, paga antes). */
    private static final java.util.concurrent.atomic.AtomicLong QUEUE_TICKETS = new java.util.concurrent.atomic.AtomicLong();
    private long queueTicket;
    private int serviceTicks;
    /** Puerta por la que entra (y por la que sale); null si la tienda no tiene puertas. */
    @Nullable
    private BlockPos entryDoor;
    @Nullable
    private Vec3d entryOutside;

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(TITLE, "");
        builder.add(SKIN, 0);
        builder.add(EMOTE, 0);
        builder.add(TYPE, 0);
        builder.add(WANTS, false);
        builder.add(SMELLY, false);
        builder.add(WAIT, -1);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new net.minecraft.entity.ai.goal.LongDoorInteractGoal(this, true)); // abre y cierra la puerta al pasar
        goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 6f));
        goalSelector.add(8, new LookAroundGoal(this));
    }

    public Type type() { return Type.values()[Math.floorMod(dataTracker.get(TYPE), Type.values().length)]; }

    @Override public String roleKey() { return returning ? "tcgshop.role.regular_back" : "tcgshop.role." + type().name().toLowerCase(java.util.Locale.ROOT); }
    @Override public String npcTitle() { return dataTracker.get(TITLE); }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }
    @Override public int emote() { return dataTracker.get(EMOTE); }
    @Override public boolean wantsAttention() { return dataTracker.get(WANTS); }

    private void emote(int e) { dataTracker.set(EMOTE, e); }

    public boolean isSmelly() { return dataTracker.get(SMELLY); }

    /** Le has echado desodorante: deja de oler mal. */
    public void sprayed(PlayerEntity by) {
        if (!isSmelly()) return;
        dataTracker.set(SMELLY, false);
        if (getWorld() instanceof ServerWorld sw) sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.6, getZ(), 8, 0.3, 0.4, 0.3, 0);
        NpcLines.sayNear(this, "spray_thanks");
        if (by instanceof ServerPlayerEntity sp) CardAdvancements.grant(sp, "spray_customer");
    }

    public boolean isFleeingThief() { return type() == Type.THIEF && state == State.FLEE && !loot.isEmpty(); }
    public boolean isDetected() { return detected; }

    // ------------------------------------------------------------------ aparición

    public static CustomerEntity spawn(ServerWorld world, ShopZones.Zone z, Type type, Vec3d pos, Vec3d exit, @Nullable ShopZones.Regular regular) {
        return spawn(world, z, type, pos, exit, regular, null, null);
    }

    public static CustomerEntity spawn(ServerWorld world, ShopZones.Zone z, Type type, Vec3d pos, Vec3d exit, @Nullable ShopZones.Regular regular,
                                       @Nullable BlockPos door, @Nullable Vec3d doorOutside) {
        CustomerEntity c = TcgEntities.CUSTOMER.create(world);
        if (c == null) return null;
        c.zoneId = z.id;
        c.entryDoor = door;
        c.entryOutside = doorOutside;
        c.dataTracker.set(TYPE, type.ordinal());
        if (regular != null) {
            c.returning = true;
            c.dataTracker.set(SKIN, regular.skin());
            c.dataTracker.set(TITLE, regular.title());
            c.setCustomName(Text.literal(regular.name()));
        } else {
            c.dataTracker.set(SKIN, NpcIdentity.randomSkin(world.random));
            float titleChance = switch (type) { case WHALE -> 0.8f; case PICKY, TRADER -> 0.45f; case THIEF -> 0.15f; default -> 0.3f; };
            c.dataTracker.set(TITLE, NpcIdentity.randomTitle(world.random, titleChance));
            c.setCustomName(Text.literal(NpcIdentity.randomName(world.random)));
        }
        c.setCustomNameVisible(true);
        c.exit = exit;
        c.browsesLeft = 1 + world.random.nextInt(type == Type.WHALE ? 4 : 3);
        c.refreshPositionAndAngles(pos.x, pos.y, pos.z, world.random.nextFloat() * 360, 0);
        if (type == Type.SELLER) c.prepareSellOffer(world);
        if (type != Type.WHALE && world.random.nextFloat() < 0.12f) c.dataTracker.set(SMELLY, true);
        world.spawnEntity(c);
        return c;
    }

    /** Lo que trae para venderte: casi siempre una carta, a veces un sobre y muy de vez en cuando una caja. */
    private void prepareSellOffer(ServerWorld world) {
        float r = random.nextFloat();
        if (r < 0.08f) {
            List<String> sets = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !dev.tcgshop.shop.PackLock.locked(id)) sets.add(CardInfo.setId(id));
            if (!sets.isEmpty()) dealItem = BoosterBox.of(sets.get(random.nextInt(sets.size())));
        } else if (r < 0.3f) {
            List<Item> sealed = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds())
                if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !dev.tcgshop.shop.PackLock.locked(id)) sealed.add(Registries.ITEM.get(id));
            if (!sealed.isEmpty()) dealItem = new ItemStack(sealed.get(random.nextInt(sealed.size())));
        }
        if (dealItem.isEmpty()) {
            List<Item> cards = allCards();
            if (cards.isEmpty()) return;
            ItemStack pick = new ItemStack(cards.get(random.nextInt(cards.size())));
            for (int tries = 0; tries < 6 && Rarity.of(pick).tier == 0 && random.nextFloat() < 0.6f; tries++)
                pick = new ItemStack(cards.get(random.nextInt(cards.size())));
            dealItem = pick;
        }
        long market = Math.max(1, ShopServer.priceOf(dealItem, 1.0));
        persona = weighted(PERSONA_W);
        int cat = weighted(MOTIVE_W[persona]);
        motive = switch (cat) {
            case 0 -> "cash." + random.nextInt(MOTIVES_CASH);
            case 1 -> "profit." + random.nextInt(MOTIVES_PROFIT);
            default -> "other." + random.nextInt(MOTIVES_OTHER);
        };
        // lo que pide (sobre el mercado) y lo mínimo que aceptaría (no lo ves)
        double[] ask = {0.9, 1.3, 0.6, 1.1, 0.95, 1.25, 1.05, 1.35, 0.5, 0.85, 0.7, 1.05};
        double a = ask[persona * 2] + random.nextDouble() * (ask[persona * 2 + 1] - ask[persona * 2]);
        if (cat == 0) a -= 0.08; // necesita el dinero ya
        else if (cat == 1) a += 0.08; // quiere ganar dinero, le dé igual si a ti te sale bien o no
        dealPrice = Math.max(10, Math.round(market * a));
        double[] fl = {0.85, 0.97, 0.7, 0.85, 0.85, 0.95, 0.72, 0.85, 0.6, 0.75, 0.75, 0.9};
        double f = fl[persona * 2] + random.nextDouble() * (fl[persona * 2 + 1] - fl[persona * 2]);
        floorPrice = Math.min(dealPrice, Math.max(Math.round(market * 0.45), Math.round(dealPrice * f)));
        int[] pat = {3, 4, 3, 5, 2, 3};
        maxPatience = patience = Math.max(1, (int) Math.round((pat[persona] + random.nextInt(2)) * EconomyConfig.INSTANCE.merchantPatience));
        dealMode = DealOpenPayload.SELL;
        reasonKey = 0;
    }

    private int weighted(int[] w) {
        int sum = 0;
        for (int x : w) sum += x;
        int r = random.nextInt(Math.max(1, sum));
        for (int i = 0; i < w.length; i++) if ((r -= w[i]) < 0) return i;
        return w.length - 1;
    }

    private static List<Item> allCards() {
        List<Item> cards = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds()) if (CardInfo.isCard(id)) cards.add(Registries.ITEM.get(id));
        return cards;
    }

    /** Coleccionista: elige una carta de tus expositores y te ofrece a cambio una suya de valor parecido. */
    private boolean prepareTrade(ServerWorld world, ShopZones.Zone z) {
        List<BlockPos> poss = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        forDisplays(world, z, (d, i) -> {
            if (CardInfo.isCard(d.getStack(i))) { poss.add(d.getPos()); slots.add(i); }
        });
        if (poss.isEmpty()) return false;
        int k = random.nextInt(poss.size());
        if (!(world.getBlockEntity(poss.get(k)) instanceof ShopDisplayBlockEntity be)) return false;
        ItemStack want = be.getStack(slots.get(k)).copyWithCount(1);
        long wantValue = ShopServer.priceOf(want, 1.0);
        List<Item> cards = allCards();
        ItemStack offer = ItemStack.EMPTY;
        double bestDiff = Double.MAX_VALUE;
        for (int tries = 0; tries < 80 && !cards.isEmpty(); tries++) {
            ItemStack c = new ItemStack(cards.get(random.nextInt(cards.size())));
            if (c.isOf(want.getItem())) continue;
            double ratio = ShopServer.priceOf(c, 1.0) / (double) Math.max(1, wantValue);
            double target = 0.75 + random.nextDouble() * 0.55; // a veces sale a cuenta, a veces no
            double diff = Math.abs(ratio - target);
            if (diff < bestDiff) { bestDiff = diff; offer = c; }
        }
        if (offer.isEmpty()) return false;
        target = poss.get(k);
        targetSlot = slots.get(k);
        dealWant = want;
        dealItem = offer;
        dealPrice = ShopServer.priceOf(offer, 1.0);
        dealMode = DealOpenPayload.TRADE;
        return true;
    }

    // ------------------------------------------------------------------ comportamiento

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (age % 5 != 0 || CobblemonBattles.isFighting(this) || dev.tcgshop.compat.NpcPokemonDuel.busy(this)) return;
        ServerWorld world = (ServerWorld) getWorld();
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z == null) { discard(); return; }
        if (age > 20 * 60 * 8 && state != State.FLEE && state != State.LEAVE) leave(world, z, null);
        timer -= 5;
        if (isSmelly() && age % 20 == 0) {
            world.spawnParticles(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.45f, 0.62f, 0.2f), 1.2f),
                    getX(), getY() + 1.9, getZ(), 3, 0.25, 0.3, 0.25, 0);
            if (age % 100 == 0 && z.contains(getBlockPos())) { z.smell = Math.min(100, z.smell + 1); ShopZones.get(world).markDirty(); }
        }
        if (!entered && z.contains(getBlockPos())) {
            entered = true;
            if (returning) NpcLines.sayNear(this, type() == Type.SELLER ? "seller_back" : "regular_back");
            else if (!z.flags.isEmpty() && random.nextFloat() < 0.35f) commentOnShop(z);
            else if (random.nextFloat() < 0.3f) NpcLines.sayNear(this, "greet");
        }
        switch (state) {
            case ENTER -> {
                if (!entered && entryOutside != null && squaredDistanceTo(entryOutside) > 2.2 * 2.2) {
                    // camina desde lejos hasta la puerta de la tienda (la navegación la abre al pasar)
                    if (getNavigation().isIdle() || age % 40 == 0) getNavigation().startMovingTo(entryOutside.x, entryOutside.y, entryOutside.z, 0.85);
                    if (timer < -20 * 90) { entryOutside = null; } // atascado: que entre como pueda
                    return;
                }
                if (type() == Type.SELLER) { goDeal(world, z, "seller_hello"); return; }
                if (type() == Type.TRADER) {
                    if (prepareTrade(world, z)) { goDeal(world, z, "trader_hello"); return; }
                    dataTracker.set(TYPE, Type.REGULAR.ordinal()); // no hay cartas que le interesen: mira como uno más
                }
                if (!pickTarget(world, z)) { leave(world, z, "nothing"); return; }
                state = State.GO;
                timer = 400;
            }
            case GO -> {
                ShopDisplayBlockEntity be = targetDisplay(world);
                if (be == null || be.getStack(targetSlot).isEmpty()) { state = State.ENTER; return; }
                if (spot == null || squaredDistanceTo(spot) < 1.6 * 1.6 || squaredDistanceTo(target.toCenterPos()) < 2.4 * 2.4) {
                    getNavigation().stop();
                    state = State.BROWSE;
                    timer = 50 + random.nextInt(70);
                    emote(EMOTE_THINK);
                } else if (getNavigation().isIdle()) getNavigation().startMovingTo(spot.x, spot.y, spot.z, 0.9);
                if (timer <= 0) state = State.ENTER;
            }
            case BROWSE -> {
                lookAtTarget(world);
                if (timer <= 0) decide(world, z);
            }
            case CHECKOUT -> checkoutTick(world, z);
            case WAIT_DEAL -> {
                ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
                if (owner != null && owner.getServerWorld() == world) {
                    double d = squaredDistanceTo(owner);
                    if (d > 3.5 * 3.5 && d < 32 * 32 && getNavigation().isIdle()) getNavigation().startMovingTo(owner, 0.8);
                    else if (d <= 3 * 3) getNavigation().stop();
                    getLookControl().lookAt(owner);
                }
                if (age % 40 == 0) world.spawnParticles(ParticleTypes.NOTE, getX(), getY() + 2.6, getZ(), 1, 0.1, 0.1, 0.1, 0);
                dataTracker.set(WAIT, (int) Math.max(0, Math.min(1000, timer * 1000L / Math.max(1, waitMax))));
                if (timer <= 0) {
                    mood -= 1;
                    reviewKey = "ignored";
                    z.unattended++;
                    ShopZones.get(world).markDirty();
                    if (owner != null) closeDeal(owner, NpcLines.random(random, "deal_timeout"));
                    leave(world, z, "deal_timeout");
                }
            }
            case FLEE -> fleeTick(world, z);
            case LEAVE -> {
                if (exit == null) exit = getPos();
                if (!reviewed && !z.contains(getBlockPos())) finishVisit(world, z);
                // se aleja andando de la tienda: al llegar a un punto, sigue más lejos
                if (squaredDistanceTo(exit) < 2.5 * 2.5) {
                    Vec3d away = getPos().subtract(z.core.toCenterPos()).multiply(1, 0, 1);
                    if (away.lengthSquared() < 1e-3) away = new Vec3d(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
                    exit = getPos().add(away.normalize().multiply(12));
                    getNavigation().stop();
                }
                if (getNavigation().isIdle()) getNavigation().startMovingTo(exit.x, exit.y, exit.z, 0.9);
                // solo desaparece lejos de la tienda y donde ningún jugador lo vea (o, si se atasca, al cabo de 2 minutos)
                double half = z.tierInfo().size / 2.0 + 14;
                net.minecraft.entity.player.PlayerEntity near = world.getClosestPlayer(this, 48);
                boolean unseen = near == null || squaredDistanceTo(near) > 40 * 40 || (squaredDistanceTo(near) > 20 * 20 && !near.canSee(this));
                boolean far = !z.contains(getBlockPos()) && squaredDistanceTo(z.core.toCenterPos()) > half * half;
                if ((far && unseen) || timer < -2400) {
                    if (!reviewed) finishVisit(world, z);
                    if (!unseen) world.spawnParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                    discard();
                }
            }
        }
    }

    private void lookAtTarget(ServerWorld world) {
        if (target == null) return;
        BlockState s = world.getBlockState(target);
        if (s.getBlock() instanceof ShopDisplayBlock b) {
            Vec3d rel = dev.tcgshop.shop.DisplayKind.toWorld(s.get(ShopDisplayBlock.FACING),
                    new Vec3d(b.kind.slots[targetSlot].x(), b.kind.slots[targetSlot].y(), b.kind.slots[targetSlot].z()));
            getLookControl().lookAt(target.getX() + rel.x, target.getY() + rel.y, target.getZ() + rel.z);
        } else getLookControl().lookAt(target.toCenterPos());
    }

    @Nullable
    private ShopDisplayBlockEntity targetDisplay(ServerWorld world) {
        return target != null && targetSlot >= 0 && world.getBlockEntity(target) instanceof ShopDisplayBlockEntity be && targetSlot < be.size() ? be : null;
    }

    private interface SlotVisitor { void visit(ShopDisplayBlockEntity d, int slot); }

    private static void forDisplays(ServerWorld world, ShopZones.Zone z, SlotVisitor v) {
        int half = z.tierInfo().size / 2;
        int cx0 = (z.core.getX() - half) >> 4, cx1 = (z.core.getX() + half) >> 4, cz0 = (z.core.getZ() - half) >> 4, cz1 = (z.core.getZ() + half) >> 4;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                WorldChunk ch = world.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof ShopDisplayBlockEntity d) || d.isHome() || !z.contains(be.getPos())) continue;
                    for (int i = 0; i < d.size(); i++) if (!d.getStack(i).isEmpty()) v.visit(d, i);
                }
            }
    }

    /** Elige un producto expuesto (los coleccionistas y los ladrones prefieren lo caro). */
    private boolean pickTarget(ServerWorld world, ShopZones.Zone z) {
        if (browsesLeft-- <= 0) return false;
        List<BlockPos> cands = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        forDisplays(world, z, (d, i) -> { cands.add(d.getPos()); slots.add(i); });
        if (cands.isEmpty()) return false;
        int pick = random.nextInt(cands.size());
        if (type() == Type.WHALE || type() == Type.THIEF) {
            long best = -1;
            for (int i = 0; i < cands.size(); i++) {
                if (world.getBlockEntity(cands.get(i)) instanceof ShopDisplayBlockEntity d) {
                    long p = d.price(slots.get(i));
                    if (p > best && random.nextFloat() < 0.8f) { best = p; pick = i; }
                }
            }
        }
        target = cands.get(pick);
        targetSlot = slots.get(pick);
        BlockState s = world.getBlockState(target);
        spot = s.getBlock() instanceof ShopDisplayBlock b ? b.kind.slotWorld(s.get(ShopDisplayBlock.FACING), target, targetSlot) : target.toCenterPos();
        return true;
    }

    private void decide(ServerWorld world, ShopZones.Zone z) {
        ShopDisplayBlockEntity be = targetDisplay(world);
        if (be == null || be.getStack(targetSlot).isEmpty()) { state = State.ENTER; return; }
        ItemStack st = be.getStack(targetSlot);
        long price = be.price(targetSlot), market = ShopServer.priceOf(st, 1.0);
        double ratio = market <= 0 ? 1 : price / (double) market;
        double rep = 0.6 + z.reputation / 125.0;
        if (type() == Type.THIEF) { steal(world, z, be); return; }
        if (ShopServer.needsCashier(st) && !ShopServer.cashierOnDuty(world, z)) {
            NpcLines.sayNear(this, "no_cashier");
            z.complaints++;
            mood -= 2;
            reviewKey = "no_cashier";
            leave(world, z, null);
            return;
        }
        if (type() == Type.HAGGLER) {
            dealItem = st.copyWithCount(1);
            dealPrice = Math.max(10, Math.round(market * (0.75 + random.nextDouble() * 0.5)));
            dealMode = DealOpenPayload.BUY;
            goDeal(world, z, "haggle");
            return;
        }
        double p = switch (type()) {
            case REGULAR -> Math.max(0.05, Math.min(0.9, 1.45 - ratio)) * rep;
            case PICKY -> ratio <= 0.92 ? 0.8 : 0;
            case WHALE -> ratio <= 1.9 ? 0.92 : 0.1;
            default -> 0;
        };
        if (returning) p = Math.min(0.95, p + 0.15);
        if (z.smell > 40) {
            p *= Math.max(0.2, 1 - (z.smell - 40) / 80.0);
            if (random.nextFloat() < 0.35f) {
                NpcLines.sayNear(this, "smell_complain");
                mood -= 1;
                reviewKey = "smell";
            }
        }
        if (random.nextDouble() < p) {
            // coge el producto y va a pagar a la caja
            bought = be.removeStack(targetSlot, 1);
            payPrice = price;
            if (bought.isEmpty()) { state = State.ENTER; return; }
            equipStack(EquipmentSlot.MAINHAND, bought.copy());
            setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
            emote(EMOTE_LOVE);
            mood += ratio <= 1.0 ? 2 : 1;
            reviewKey = ratio <= 0.9 ? "bargain" : ratio <= 1.15 ? "fair" : "pricey";
            if (type() == Type.WHALE) {
                mood += 1;
                z.whales++;
                ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
                if (owner != null) CardAdvancements.grant(owner, "whale_customer");
            }
            NpcLines.sayNear(this, type() == Type.WHALE ? "whale" : "buy");
            playSound(SoundEvents.ENTITY_VILLAGER_YES, 0.8f, 1.1f);
            register = nearestRegister(world, z);
            if (register == null) pay(world, z);
            else joinQueue();
            return;
        }
        emote(ratio > 1.15 ? EMOTE_ANGRY : EMOTE_THINK);
        if (ratio > 1.15) {
            z.complaints++;
            mood -= 1;
            reviewKey = "expensive";
            NpcLines.sayNear(this, "expensive");
        } else if (random.nextFloat() < 0.4f) NpcLines.sayNear(this, "nobuy");
        ShopZones.get(world).markDirty();
        state = State.ENTER;
    }

    // ------------------------------------------------------------------ fila de la caja

    /** Se pone a la cola de la caja: coge turno y empieza a contar su paciencia. */
    private void joinQueue() {
        state = State.CHECKOUT;
        queueTicket = QUEUE_TICKETS.incrementAndGet();
        serviceTicks = 0;
        timer = waitMax = 20 * EconomyConfig.INSTANCE.customerWaitSeconds;
        dataTracker.set(WAIT, 1000);
    }

    /** Cuántos van delante en la misma caja. */
    private int queueIndex(ServerWorld world, ShopZones.Zone z) {
        final BlockPos reg = register;
        return world.getEntitiesByClass(CustomerEntity.class, z.box().expand(8),
                c -> c != this && c.isAlive() && c.state == State.CHECKOUT && reg.equals(c.register) && c.queueTicket < queueTicket).size();
    }

    private static boolean standable(ServerWorld world, BlockPos p) {
        return !world.getBlockState(p).blocksMovement() && !world.getBlockState(p.up()).blocksMovement()
                && world.getBlockState(p.down()).isSolidBlock(world, p.down());
    }

    /**
     * Sitio número k de la fila: el primero delante del mostrador y los demás uno detrás de otro, alejándose de la caja;
     * si hay una pared o un mueble, la fila dobla hacia un lado.
     */
    private static BlockPos queueSpot(ServerWorld world, BlockPos front, Direction f, int k) {
        java.util.List<BlockPos> line = new java.util.ArrayList<>();
        line.add(front);
        BlockPos cur = front;
        Direction[] prefs = {f, f.rotateYClockwise(), f.rotateYCounterclockwise()};
        while (line.size() <= k && line.size() < 12) {
            BlockPos next = null;
            for (Direction d : prefs) {
                BlockPos n = cur.offset(d);
                if (!line.contains(n) && standable(world, n)) { next = n; break; }
            }
            if (next == null) break;
            line.add(next);
            cur = next;
        }
        return line.get(Math.min(k, line.size() - 1));
    }

    /**
     * En la cola: va a su sitio (detrás del de delante), espera mirando a la caja y, cuando es su turno y está en el
     * mostrador, le cobran (más rápido con cajero). Todos tienen su barra de paciencia: si se acaba, deja el producto
     * en su sitio y se va sin comprar.
     */
    private void checkoutTick(ServerWorld world, ShopZones.Zone z) {
        if (register == null || !world.getBlockState(register).isOf(TcgBlocks.CASH_REGISTER)) { pay(world, z); return; }
        Direction f = world.getBlockState(register).get(Properties.HORIZONTAL_FACING);
        BlockPos front = register.offset(f);
        int idx = queueIndex(world, z);
        BlockPos spotPos = queueSpot(world, front, f, idx);
        double dx = spotPos.getX() + 0.5 - getX(), dz = spotPos.getZ() + 0.5 - getZ();
        boolean there = dx * dx + dz * dz < 0.7 * 0.7;
        if (!there) {
            if (getNavigation().isIdle() || age % 20 == 0) getNavigation().startMovingTo(spotPos.getX() + 0.5, spotPos.getY(), spotPos.getZ() + 0.5, 0.75);
        } else {
            getNavigation().stop();
            getLookControl().lookAt(register.toCenterPos());
        }
        if (idx == 0 && dx * dx + dz * dz < 1.3 * 1.3) {
            serviceTicks += 5;
            int need = ShopServer.cashierOnDuty(world, z) ? 40 : 70;
            if (serviceTicks >= need) { pay(world, z); return; }
        } else serviceTicks = 0;
        dataTracker.set(WAIT, (int) Math.max(0, Math.min(1000, timer * 1000L / Math.max(1, waitMax))));
        if (timer <= 0) leaveQueue(world, z);
    }

    /** Se cansa de esperar: devuelve el producto y se va. */
    private void leaveQueue(ServerWorld world, ShopZones.Zone z) {
        returnBought(world);
        NpcLines.sayNear(this, "queue_leave");
        mood -= 2;
        reviewKey = "queue_slow";
        z.unattended++;
        ShopZones.get(world).markDirty();
        register = null;
        leave(world, z, null);
    }

    /** Deja el producto que iba a comprar en su casilla (o en otra libre del mismo mueble; si no hay, en el suelo). */
    private void returnBought(ServerWorld world) {
        if (bought.isEmpty()) return;
        ShopDisplayBlockEntity be = targetDisplay(world);
        boolean placed = false;
        if (be != null) {
            if (be.getStack(targetSlot).isEmpty()) { be.setStack(targetSlot, bought); placed = true; }
            else for (int i = 0; i < be.size() && !placed; i++)
                if (be.getStack(i).isEmpty()) { be.setStack(i, bought); placed = true; }
        }
        if (!placed) dropStack(bought);
        bought = ItemStack.EMPTY;
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Nullable
    private BlockPos nearestRegister(ServerWorld world, ShopZones.Zone z) {
        int half = z.tierInfo().size / 2;
        for (BlockPos p : BlockPos.iterateOutwards(getBlockPos(), half + 2, 4, half + 2)) {
            if (z.contains(p) && world.getBlockState(p).isOf(TcgBlocks.CASH_REGISTER)) return p.toImmutable();
        }
        return null;
    }

    /** Paga lo que lleva en la mano: el dinero entra en la caja de la tienda. */
    private void pay(ServerWorld world, ShopZones.Zone z) {
        if (bought.isEmpty()) { leave(world, z, null); return; }
        long earned = ShopServer.payFromNpc(world, z, bought, payPrice, register != null ? register : getBlockPos());
        if (register != null && random.nextFloat() < 0.5f) NpcLines.sayNear(this, "checkout");
        notifyOwner(world, z, Text.translatable("tcgshop.customer.bought", getDisplayName(), bought.toHoverableText(),
                Text.literal(PriceService.format(earned)).formatted(Formatting.GOLD)));
        bought = ItemStack.EMPTY;
        register = null;
        ShopServer.syncZones(world.getServer());
        leave(world, z, null);
    }

    private void steal(ServerWorld world, ShopZones.Zone z, ShopDisplayBlockEntity be) {
        loot = be.removeStack(targetSlot, 1);
        if (loot.isEmpty()) { state = State.ENTER; return; }
        equipStack(EquipmentSlot.MAINHAND, loot.copy());
        setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
        z.stolen++;
        ShopZones.get(world).markDirty();
        state = State.FLEE;
        fleePhaseTicks = 60;
        admiring = false;
        setPersistent();
        emote(EMOTE_MONEY);
        boolean guard = guardOf(world, z) != null;
        for (BlockPos p : BlockPos.iterateOutwards(getBlockPos(), 12, 3, 12)) {
            if (world.getBlockState(p).isOf(TcgBlocks.THEFT_DETECTOR)) {
                DecorBlocks.TheftDetectorBlock.trigger(world, p);
                detected = true;
                break;
            }
        }
        if (guard) detected = true; // el guardia lo ve todo
        if (detected) {
            addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 40, 0, false, false));
            addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 30, guard ? 1 : 0, false, false));
        }
        notifyOwner(world, z, Text.translatable(detected ? "tcgshop.thief.alarm" : "tcgshop.thief.stole", getDisplayName(), loot.toHoverableText())
                .formatted(Formatting.RED));
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null && !guard) owner.sendMessage(Text.translatable(CobblemonBattles.available() ? "tcgshop.thief.chase_hint_battle" : "tcgshop.thief.chase_hint")
                .formatted(Formatting.YELLOW), false);
    }

    @Nullable
    private EmployeeEntity guardOf(ServerWorld world, ShopZones.Zone z) {
        if (!z.guard) return null;
        List<EmployeeEntity> g = world.getEntitiesByClass(EmployeeEntity.class, z.box().expand(48), e -> e.isGuard() && z.id.equals(e.zoneId()));
        return g.isEmpty() ? null : g.get(0);
    }

    /** Huida del ladrón: con guardia lo atrapan sí o sí; sin guardia huye como un Scalper hasta que lo pilles o te alejes. */
    private void fleeTick(ServerWorld world, ShopZones.Zone z) {
        EmployeeEntity guard = guardOf(world, z);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        double escape = Math.pow(z.tierInfo().size / 2.0 + 8, 2);
        if (guard != null) {
            if (getNavigation().isIdle() || age % 20 == 0) {
                Vec3d away = getPos().subtract(z.core.toCenterPos()).normalize().multiply(14);
                getNavigation().startMovingTo(getX() + away.x, getY(), getZ() + away.z, 1.0);
            }
            // si llega a salir, el guardia avisa por radio y lo detienen igualmente
            if (squaredDistanceTo(z.core.toCenterPos()) > escape) detain(world, z, guard);
            return;
        }
        fleePhaseTicks -= 5;
        if (fleePhaseTicks <= 0) {
            admiring = !admiring;
            if (admiring) {
                fleePhaseTicks = 50 + random.nextInt(30);
                emote(1 + random.nextInt(2));
                playSound(SoundEvents.ENTITY_PIGLIN_ADMIRING_ITEM, 1f, 1.2f);
            } else {
                fleePhaseTicks = 60 + random.nextInt(50);
                emote(EMOTE_NONE);
            }
        }
        if (admiring) {
            getNavigation().stop();
            setPitch(45f);
            if (age % 20 == 0) world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2.2, getZ(), 2, 0.2, 0.1, 0.2, 0);
        } else if (getNavigation().isIdle() || age % 20 == 0) {
            Vec3d from = owner != null && owner.squaredDistanceTo(this) < 40 * 40 ? owner.getPos() : z.core.toCenterPos();
            Vec3d away = getPos().subtract(from).normalize().multiply(12);
            getNavigation().startMovingTo(getX() + away.x, getY(), getZ() + away.z, detected ? 1.15 : 1.35);
        }
        lostTicks = (owner == null || owner.getServerWorld() != world || squaredDistanceTo(owner) > 64 * 64) ? lostTicks + 5 : 0;
        if (lostTicks > 600) {
            NpcLines.sayNear(this, "thief_escape");
            if (owner != null) owner.sendMessage(Text.translatable("tcgshop.thief.escaped", getDisplayName(), loot.toHoverableText()).formatted(Formatting.RED), false);
            discard();
        }
    }

    /** Lo detienen: el producto vuelve a su expositor (o se abona en caja). guard puede ser null (lo pilla el dueño). */
    public void detain(ServerWorld world, ShopZones.Zone z, @Nullable EmployeeEntity guard) {
        if (loot.isEmpty()) return;
        returnLoot(world, z);
        z.detained++;
        ShopZones.get(world).markDirty();
        if (guard != null) guard.announce(Text.translatable("tcgshop.thief.caught", getDisplayName(), Text.translatable("tcgshop.thief.loot_back")));
        NpcLines.sayNear(this, "thief_caught");
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.8, getZ(), 6, 0.3, 0.3, 0.3, 0);
        playSound(SoundEvents.ENTITY_VILLAGER_HURT, 1f, 0.8f);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null) {
            CardAdvancements.grant(owner, "catch_thief");
            if (z.detained >= 10) CardAdvancements.grant(owner, "catch_thief_10");
        }
        discard();
    }

    private void returnLoot(ServerWorld world, ShopZones.Zone z) {
        if (loot.isEmpty()) return;
        ShopDisplayBlockEntity be = targetDisplay(world);
        if (be != null && be.getStack(targetSlot).isEmpty()) be.setStack(targetSlot, loot);
        else z.balance += ShopServer.priceOf(loot, 1.0); // el hueco ya no está libre: se abona su valor en caja
        z.recovered++;
        loot = ItemStack.EMPTY;
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        ShopZones.get(world).markDirty();
    }

    private void goDeal(ServerWorld world, ShopZones.Zone z, String line) {
        if (dealItem.isEmpty()) { leave(world, z, null); return; }
        state = State.WAIT_DEAL;
        timer = waitMax = 20 * EconomyConfig.INSTANCE.customerWaitSeconds;
        dataTracker.set(WAIT, 1000);
        emote(EMOTE_NONE);
        dataTracker.set(WANTS, true);
        NpcLines.sayNear(this, line);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null && owner.squaredDistanceTo(this) < 64 * 64)
            owner.sendMessage(Text.translatable("tcgshop.deal.waiting", getDisplayName()).formatted(Formatting.YELLOW), true);
    }

    private void leave(ServerWorld world, ShopZones.Zone z, String line) {
        if (line != null && random.nextFloat() < 0.5f) NpcLines.sayNear(this, line);
        dataTracker.set(WANTS, false);
        dataTracker.set(WAIT, -1);
        int stars = stars();
        emote(stars >= 4 ? EMOTE_LOVE : stars <= 2 ? EMOTE_ANGRY : EMOTE_NONE);
        state = State.LEAVE;
        timer = 0;
        getNavigation().stop();
    }

    private int stars() { return Math.max(1, Math.min(5, mood)); }

    /** Comenta algo que ha notado de la tienda (techo, luz, monstruos cerca, animales, precios...) y le afecta un poco. */
    private void commentOnShop(ShopZones.Zone z) {
        List<String> f = new ArrayList<>(z.flags);
        String k = f.get(random.nextInt(f.size()));
        NpcLines.sayNear(this, "comment_" + k);
        switch (k) {
            case "hostile", "infamy" -> { mood -= 1; reviewKey = k.equals("hostile") ? "unsafe" : "infamy"; }
            case "no_roof", "no_walls", "dark", "empty" -> { if (random.nextBoolean()) { mood -= 1; reviewKey = "env_" + k; } }
            case "cozy", "bright", "pokemon", "pets", "cheap" -> { if (random.nextFloat() < 0.4f) { mood += 1; reviewKey = "env_" + k; } }
            default -> {}
        }
    }

    @Override
    public int waitPermille() { return dataTracker.get(WAIT); }

    /** Ha visto cómo matabas a alguien: se asusta y se va con la peor reseña. */
    public void witnessMurder() {
        if (!(getWorld() instanceof ServerWorld world) || type() == Type.THIEF) return;
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z == null || state == State.FLEE) return;
        NpcLines.sayNear(this, "murder_witness");
        mood = 1;
        reviewKey = "murder";
        dealItem = ItemStack.EMPTY;
        leave(world, z, null);
        getNavigation().stop();
    }

    /** Al salir de la tienda: deja su reseña y, si salió contento, se apunta como cliente habitual. */
    private void finishVisit(ServerWorld world, ShopZones.Zone z) {
        reviewed = true;
        if (type() == Type.THIEF) return;
        int stars = stars();
        long day = world.getTimeOfDay() / 24000;
        z.addReview(getName().getString(), stars, reviewKey, day);
        if (stars >= 4) z.remember(getName().getString(), npcSkin(), npcTitle());
        if (random.nextFloat() < 0.35f) NpcLines.sayNear(this, stars >= 4 ? "leave_happy" : stars <= 2 ? "leave_sad" : "nothing");
        ShopZones.get(world).markDirty();
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null && owner.squaredDistanceTo(this) < 96 * 96)
            owner.sendMessage(Text.translatable("tcgshop.review.left", getDisplayName(), "★".repeat(stars) + "☆".repeat(5 - stars),
                    Text.translatable("tcgshop.review." + reviewKey)).formatted(stars >= 4 ? Formatting.GREEN : stars <= 2 ? Formatting.RED : Formatting.GRAY), true);
    }

    private void notifyOwner(ServerWorld world, ShopZones.Zone z, Text msg) {
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
        if (owner != null && owner.squaredDistanceTo(this) < 96 * 96) owner.sendMessage(msg, true);
    }

    // ------------------------------------------------------------------ interacción, tratos y combate

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp) || getWorld().isClient) return ActionResult.success(getWorld().isClient);
        ShopZones.Zone z = ShopZones.get(sp.getServerWorld()).byId(zoneId);
        if (type() == Type.THIEF && state == State.FLEE && !loot.isEmpty()) {
            if (CobblemonBattles.available() && CobblemonBattles.challenge(sp, this)) {
                NpcLines.sayNear(this, "thief_battle");
                CardAdvancements.grant(sp, "battle_thief");
            } else NpcLines.say(this, sp, "thief_talk");
            return ActionResult.SUCCESS;
        }
        if (state == State.WAIT_DEAL && z != null && z.owner.equals(sp.getUuid()) && !dealItem.isEmpty()) {
            long market = ShopServer.priceOf(dealItem, 1.0);
            Text line = switch (dealMode) {
                case DealOpenPayload.SELL -> motive.isEmpty() ? Text.translatable("tcgshop.npc.reason." + reasonKey) : Text.translatable("tcgshop.motive." + motive, dealItem.getName());
                case DealOpenPayload.TRADE -> NpcLines.random(random, "trader_hello");
                default -> NpcLines.random(random, "haggle_offer");
            };
            long wantMarket = dealWant.isEmpty() ? 0 : ShopServer.priceOf(dealWant, 1.0);
            if (ServerPlayNetworking.canSend(sp, DealOpenPayload.ID))
                ServerPlayNetworking.send(sp, new DealOpenPayload(getId(), getDisplayName(), line, dealItem, dealPrice, market, dealMode, dealWant, wantMarket,
                        dealMode == DealOpenPayload.SELL ? persona : -1, patience, maxPatience));
        } else {
            NpcLines.say(this, sp, switch (type()) { case PICKY -> "picky"; case WHALE -> "whale_talk"; case THIEF -> "thief_talk"; default -> "greet"; });
        }
        return ActionResult.SUCCESS;
    }

    /** Respuesta del dueño a un trato. */
    public void answerDeal(ServerPlayerEntity owner, boolean accept) {
        respond(owner, accept ? DealAnswerPayload.ACCEPT : DealAnswerPayload.REJECT_POLITE, 0);
    }

    /** Respuesta del dueño: aceptar, rechazar o regatear (sólo con quien te vende algo). */
    public void respond(ServerPlayerEntity owner, int action, long counter) {
        ServerWorld world = owner.getServerWorld();
        ShopZones.Zone z = ShopZones.get(world).byId(zoneId);
        if (state != State.WAIT_DEAL || z == null || !z.owner.equals(owner.getUuid()) || dealItem.isEmpty()) return;
        switch (action) {
            case DealAnswerPayload.ACCEPT -> { acceptDeal(owner); return; }
            case DealAnswerPayload.REJECT_POLITE, DealAnswerPayload.REJECT_RUDE -> { rejectDeal(owner, world, z, action == DealAnswerPayload.REJECT_RUDE); return; }
            default -> {}
        }
        if (dealMode != DealOpenPayload.SELL || persona < 0) return;
        timer = Math.min(waitMax, timer + 20 * 10); // mientras habláis, espera un poco más
        if (action == DealAnswerPayload.INTEREST) { think(owner, world, z); return; }
        negotiate(owner, world, z, action, counter);
    }

    private String pk() { return PERSONAS[Math.max(0, Math.min(PERSONAS.length - 1, persona))]; }

    private void rejectDeal(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z, boolean rude) {
        EconomyConfig c = EconomyConfig.INSTANCE;
        double neg = rude ? c.rejectNegativeChanceRude : c.rejectNegativeChancePolite;
        double[] pm = {1.2, 1.0, 0.8, 0.6, 1.3, 0.8};
        if (persona >= 0) neg *= pm[persona];
        long day = world.getTimeOfDay() / 24000;
        if (z.rejectDay != day) { z.rejectDay = day; z.rejectPenalties = 0; }
        // la reseña neutral es lo normal; la negativa es posible pero con tope diario (sin castigos desproporcionados)
        boolean bad = z.rejectPenalties < c.maxRejectPenaltiesPerDay && random.nextDouble() < Math.min(1, neg);
        if (bad) {
            z.rejectPenalties++;
            mood = rude ? 1 : 2;
            reviewKey = rude ? "deal_rude" : "deal_no_bad";
            emote(EMOTE_ANGRY);
        } else {
            mood = 3;
            reviewKey = "deal_no_neutral";
        }
        Text line = NpcLines.random(random, rude ? "neg_rude" : bad ? "neg_reject_bad" : "neg_reject_ok");
        NpcLines.speak(this, line);
        closeDeal(owner, line);
        ShopZones.get(world).markDirty();
        leave(world, z, null);
    }

    private void negotiate(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z, int action, long counter) {
        long market = Math.max(1, ShopServer.priceOf(dealItem, 1.0));
        counter = Math.max(1, counter);
        if (counter >= dealPrice) { acceptDeal(owner); return; }
        int pi = Math.max(0, persona);
        double mod = 1;
        net.minecraft.text.MutableText say = Text.empty();
        switch (action) {
            case DealAnswerPayload.ARG_MARKET -> {
                boolean fair = counter >= market * 0.8;
                mod = fair ? new double[]{0.97, 0.98, 0.95, 0.97, 0.98, 0.98}[pi] : 1.05;
                say.append(NpcLines.random(random, fair ? "neg_arg_market_good" : "neg_arg_market_bad")).append(" ");
            }
            case DealAnswerPayload.ARG_ELSEWHERE -> {
                mod = new double[]{1.0, 0.92, 1.06, 1.03, 0.9, 0.96}[pi];
                say.append(NpcLines.random(random, mod < 1 ? "neg_arg_elsewhere_good" : "neg_arg_elsewhere_bad")).append(" ");
            }
            case DealAnswerPayload.ARG_BROKE -> {
                mod = new double[]{0.97, 0.95, 1.02, 1.04, 1.0, 0.95}[pi];
                say.append(NpcLines.random(random, mod < 1 ? "neg_arg_broke_good" : "neg_arg_broke_bad")).append(" ");
            }
            default -> {}
        }
        long eff = Math.round(floorPrice * mod);
        if (counter < floorPrice * 0.6) { // oferta ridícula: se ofende
            patience -= 2;
            mood -= 1;
            say.append(NpcLines.random(random, "neg_insulted_" + pk()));
            if (patience <= 0) { walkAway(owner, world, z, say); return; }
            updateDeal(owner, say);
            return;
        }
        boolean last = action == DealAnswerPayload.ARG_MAX;
        if (counter >= eff || (last && persona == P_DESPERATE && counter >= floorPrice * 0.92 && random.nextBoolean())) {
            dealPrice = counter;
            acceptDeal(owner);
            return;
        }
        if (last) { walkAway(owner, world, z, say.append(NpcLines.random(random, "neg_final_no"))); return; }
        patience--;
        if (patience <= 0) { walkAway(owner, world, z, say.append(NpcLines.random(random, "neg_walkaway_" + pk()))); return; }
        double[] conc = {0.3, 0.5, 0.25, 0.35, 0.6, 0.45};
        long next = Math.max(eff, Math.round(dealPrice - (dealPrice - counter) * conc[pi]));
        if (next >= dealPrice) say.append(NpcLines.random(random, "neg_hold_" + pk(), PriceService.format(dealPrice)));
        else {
            dealPrice = next;
            say.append(NpcLines.random(random, "neg_counter_" + pk(), PriceService.format(dealPrice)));
        }
        updateDeal(owner, say);
    }

    /** "Me interesa, déjame pensarlo": a veces baja un poco; si insistes mucho se cansa. */
    private void think(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z) {
        thinks++;
        if (thinks > 2) patience--;
        if (patience <= 0) { walkAway(owner, world, z, NpcLines.random(random, "neg_walkaway_" + pk()).copy()); return; }
        double[] lower = {0.25, 0.5, 0.2, 0.35, 0.7, 0.45};
        if (random.nextDouble() < lower[Math.max(0, persona)] && dealPrice > floorPrice) {
            dealPrice = Math.max(floorPrice, Math.round(dealPrice * (0.92 + random.nextDouble() * 0.05)));
            updateDeal(owner, NpcLines.random(random, "neg_think_lower", PriceService.format(dealPrice)));
        } else updateDeal(owner, NpcLines.random(random, "neg_think_same").copy());
    }

    /** Se va sin trato porque no os entendéis (no es un rechazo tuyo: reseña neutral). */
    private void walkAway(ServerPlayerEntity owner, ServerWorld world, ShopZones.Zone z, Text line) {
        mood = Math.min(mood, 3);
        reviewKey = "deal_walk";
        NpcLines.speak(this, line);
        closeDeal(owner, line);
        leave(world, z, null);
    }

    private void updateDeal(ServerPlayerEntity owner, Text line) {
        if (!ServerPlayNetworking.canSend(owner, DealOpenPayload.ID)) return;
        long market = ShopServer.priceOf(dealItem, 1.0);
        ServerPlayNetworking.send(owner, new DealOpenPayload(getId(), getDisplayName(), line, dealItem, dealPrice, market, dealMode, dealWant,
                dealWant.isEmpty() ? 0 : ShopServer.priceOf(dealWant, 1.0), persona, patience, maxPatience));
    }

    /** Cierra la ventana del trato en el cliente mostrando la última frase. */
    private void closeDeal(ServerPlayerEntity owner, Text line) {
        if (!ServerPlayNetworking.canSend(owner, DealOpenPayload.ID)) return;
        ServerPlayNetworking.send(owner, new DealOpenPayload(getId(), getDisplayName(), line, ItemStack.EMPTY, 0, 0, DealOpenPayload.CLOSED,
                ItemStack.EMPTY, 0, persona, 0, maxPatience));
    }

    private void acceptDeal(ServerPlayerEntity owner) {
        ServerWorld world = owner.getServerWorld();
        ShopZones.Zone z = ShopZones.get(world).byId(zoneId);
        if (state != State.WAIT_DEAL || z == null || !z.owner.equals(owner.getUuid()) || dealItem.isEmpty()) return;
        long market = ShopServer.priceOf(dealItem, 1.0);
        switch (dealMode) {
            case DealOpenPayload.SELL -> { // tú le compras
                if (!CobbleDollarsBridge.tryCharge(owner, dealPrice)) {
                    owner.sendMessage(Text.translatable("tcgshop.shop.no_money", PriceService.format(dealPrice)).formatted(Formatting.RED), true);
                    updateDeal(owner, NpcLines.random(random, "neg_no_money"));
                    return;
                }
                give(owner, dealItem.copy());
                ShopZones.get(world).addCost(owner.getUuid(), dealPrice);
                if (dealPrice < market) CardAdvancements.grant(owner, "deal_bargain");
            }
            case DealOpenPayload.TRADE -> { // cambias tu carta expuesta por la suya
                ShopDisplayBlockEntity be = targetDisplay(world);
                if (be == null || !ItemStack.areItemsAndComponentsEqual(be.getStack(targetSlot), dealWant)) {
                    owner.sendMessage(Text.translatable("tcgshop.deal.gone").formatted(Formatting.GRAY), true);
                    leave(world, z, null);
                    return;
                }
                ItemStack got = be.removeStack(targetSlot, 1);
                give(owner, dealItem.copy());
                equipStack(EquipmentSlot.MAINHAND, got);
                setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
                CardAdvancements.grant(owner, "npc_trade");
            }
            default -> { // le vendes tu producto al precio que ofrece
                ShopDisplayBlockEntity be = targetDisplay(world);
                if (be == null || !ItemStack.areItemsAndComponentsEqual(be.getStack(targetSlot), dealItem)) {
                    owner.sendMessage(Text.translatable("tcgshop.deal.gone").formatted(Formatting.GRAY), true);
                    leave(world, z, null);
                    return;
                }
                ItemStack sold = be.getStack(targetSlot).copyWithCount(1);
                ShopServer.sellToNpc(world, z, be, targetSlot, dealPrice);
                equipStack(EquipmentSlot.MAINHAND, sold);
                setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
                if (dealPrice > market) CardAdvancements.grant(owner, "deal_above_market");
            }
        }
        z.dealsAccepted++;
        mood += 2;
        reviewKey = "deal_yes";
        ShopZones.get(world).markDirty();
        CardAdvancements.grant(owner, "first_deal");
        Text done = persona >= 0 && dealMode == DealOpenPayload.SELL ? NpcLines.random(random, "neg_accept_" + pk()) : NpcLines.random(random, "deal_accepted");
        NpcLines.speak(this, done);
        closeDeal(owner, done);
        dealItem = ItemStack.EMPTY;
        leave(world, z, null);
        ShopServer.syncZones(world.getServer());
    }

    private static void give(ServerPlayerEntity owner, ItemStack st) {
        if (CardInfo.isCard(st) && CollectionService.store(owner, List.of(st.copy()))) return;
        owner.getInventory().offerOrDrop(st);
    }

    @Override
    public String[] battlePool() { return THIEF_POOL; }

    @Override
    public void onBattleEnd(ServerPlayerEntity player, boolean playerWon) {
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (playerWon) {
            CardAdvancements.grant(player, "battle_win");
            damage(world.getDamageSources().playerAttack(player), 1000f);
        } else {
            ShopZones.Zone z = ShopZones.get(world).byId(zoneId);
            NpcLines.sayNear(this, "thief_escape");
            player.sendMessage(Text.translatable("tcgshop.thief.escaped", getDisplayName(), loot.toHoverableText()).formatted(Formatting.RED), false);
            if (z != null) ShopZones.get(world).markDirty();
            discard();
        }
    }

    /** A los clientes normales no se les puede pegar (se quejan); a los ladrones sí. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (type() != Type.THIEF && source.getAttacker() instanceof PlayerEntity p && !p.isCreative()) {
            if (!getWorld().isClient && age % 2 == 0) {
                NpcLines.sayNear(this, "ouch");
                mood -= 2;
                reviewKey = "hit";
            }
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!(getWorld() instanceof ServerWorld world) || loot.isEmpty()) return;
        ShopZones.Zone z = zoneId == null ? null : ShopZones.get(world).byId(zoneId);
        if (z != null) {
            returnLoot(world, z);
            z.detained++;
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(z.owner);
            if (owner != null) {
                owner.sendMessage(Text.translatable("tcgshop.thief.recovered", getDisplayName()).formatted(Formatting.GREEN), false);
                CardAdvancements.grant(owner, "catch_thief");
                if (z.detained >= 10) CardAdvancements.grant(owner, "catch_thief_10");
            }
        } else dropStack(loot);
        loot = ItemStack.EMPTY;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (zoneId != null) nbt.putUuid("Zone", zoneId);
        if (!loot.isEmpty()) nbt.put("Loot", loot.encode(getRegistryManager()));
        if (!bought.isEmpty()) nbt.put("Bought", bought.encode(getRegistryManager()));
        nbt.putLong("PayPrice", payPrice);
        nbt.putInt("Type", dataTracker.get(TYPE));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Zone")) zoneId = nbt.getUuid("Zone");
        dataTracker.set(TYPE, nbt.getInt("Type"));
        loot = nbt.contains("Loot") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Loot")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        bought = nbt.contains("Bought") ? ItemStack.fromNbt(getRegistryManager(), nbt.get("Bought")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        payPrice = nbt.getLong("PayPrice");
        exit = null; // al recargar: se marcha andando desde donde esté
        reviewed = true;
        // al recargar: el ladrón sigue huyendo con su botín; quien iba a pagar paga; el resto se marcha
        if (!loot.isEmpty()) { state = State.FLEE; fleePhaseTicks = 40; }
        else if (!bought.isEmpty()) { joinQueue(); register = null; } // sin caja: pagará al recalcular (pay)
        else state = State.LEAVE;
    }
}
