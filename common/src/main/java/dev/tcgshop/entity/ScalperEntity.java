package dev.tcgshop.entity;

import dev.masterball.api.platform.Net;
import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.ScalperOfferPayload;
import dev.tcgshop.pack.PackOpener;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scalper: humanoid reseller (Steve-like look) that stalks collectors.
 *  · If it sees you opening packs, it attacks you to rob you.
 *  · Otherwise, it approaches and offers you a pack at an inflated price (2.5x-4x the market price, in Pokédollars).
 *    If you reject or ignore it, it turns hostile.
 *  · If it manages to hit you, it steals a stack of packs or cards from your inventory and flees.
 *    Cards stored in the General Binder are safe. If you kill it, it drops what it stole.
 * Damage = player with a diamond sword (7). Defense = full netherite armor (20 armor, 12 toughness,
 * 0.4 knockback resistance), with no visible gear.
 * 1.1: from Stone tier (EconomyConfig.scalperShopMinTier) a Scalper may also walk into your open shop while you are
 * there ({@link #shopVisitSpawner}) and offer you an overpriced pack. A shop visitor who is turned down or ignored
 * leaves grumbling instead of turning hostile (it still attacks if it sees you opening packs, or if you hit it).
 */
public class ScalperEntity extends Monster implements TcgNpc, dev.tcgshop.compat.CobblemonBattles.Fighter {
    public enum Mode { CALM, OFFERING, HOSTILE, FLEEING, SATISFIED, BRAWL }

    /** "Shady" Pokémon that Scalpers send out in battle. */
    private static final String[] POOL = {"meowth", "sneasel", "murkrow", "purrloin", "scraggy", "sableye", "nickit", "impidimp",
            "grimer", "koffing", "houndour", "poochyena", "stunky", "zorua", "trubbish", "croagunk"};
    /** Ticks left in the current fight (against another Scalper or a monster). */
    private int brawlTimer;

    private Mode mode = Mode.CALM;
    private UUID mark;
    private int timer;
    private int talkCooldown;
    private ResourceLocation offerPack;
    private long offerPrice;
    private final SimpleContainer stolen = new SimpleContainer(9);
    private final List<UUID> alreadyOffered = new ArrayList<>();
    /** 1.1: came into a shop to sell (see the class doc); where it came from (it leaves that way and vanishes). */
    private boolean shopVisit, shopLeaving;
    private Vec3 shopExit;
    /** While fleeing it alternates running / stopping to admire the loot (ticks left in the current phase). */
    private int fleePhaseTicks;
    private boolean admiring;
    private int lostTicks;

    /** 0 = nothing, 1 = thinking about money, 2 = loves what it sees. Synced to the client for the bubble. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> EMOTE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ScalperEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    private static final net.minecraft.network.syncher.EntityDataAccessor<String> TITLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ScalperEntity.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SKIN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ScalperEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EMOTE, 0);
        builder.define(TITLE, "");
        builder.define(SKIN, 0);
    }

    @Override public String roleKey() { return "tcgshop.role.scalper"; }
    @Override public String npcTitle() { return entityData.get(TITLE); }
    @Override public int npcSkin() { return entityData.get(SKIN); }

    /** Real name, skin and (40%) a Pokémon title. */
    private void ensureIdentity() {
        if (hasCustomName()) return;
        setCustomName(Component.literal(NpcIdentity.randomName(random)));
        setCustomNameVisible(true);
        entityData.set(TITLE, NpcIdentity.randomTitle(random, 0.4f));
        entityData.set(SKIN, NpcIdentity.randomSkin(random));
    }

    public int emote() {
        return entityData.get(EMOTE);
    }

    public ScalperEntity(EntityType<? extends Monster> type, Level world) {
        super(type, world);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)       // diamond sword
                .add(Attributes.ARMOR, 20.0)              // full netherite armor
                .add(Attributes.ARMOR_TOUGHNESS, 12.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true) {
            @Override
            public boolean canUse() { return (mode == Mode.HOSTILE || mode == Mode.BRAWL) && super.canUse(); }

            @Override
            public boolean canContinueToUse() { return (mode == Mode.HOSTILE || mode == Mode.BRAWL) && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() { return (mode == Mode.CALM || mode == Mode.SATISFIED) && super.canUse(); }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public Mode mode() { return mode; }

    private ServerPlayer markPlayer() {
        if (mark == null || !(level() instanceof ServerLevel sw)) return null;
        Player p = sw.getPlayerByUUID(mark);
        return p instanceof ServerPlayer sp && sp.isAlive() && !sp.isSpectator() && !sp.isCreative() ? sp : null;
    }

    private void say(ServerPlayer p, String key, Object... args) {
        if (p == null) return;
        NpcLines.speak(this, Component.translatable(key, args));
    }

    private void becomeHostile(ServerPlayer p, String line) {
        mode = Mode.HOSTILE;
        mark = p.getUUID();
        setTarget(p);
        say(p, line);
        playSound(SoundEvents.PILLAGER_AMBIENT, 1f, 0.9f);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ensureIdentity();
        if (talkCooldown > 0) talkCooldown--;
        if (timer > 0) timer--;
        if (tickCount % 10 != 0 || !(level() instanceof ServerLevel sw)) return;
        if (dev.tcgshop.compat.CobblemonBattles.isFighting(this) || dev.tcgshop.compat.NpcPokemonDuel.busy(this)) return;
        if ((mode == Mode.CALM || mode == Mode.SATISFIED) && random.nextFloat() < 0.006f) lookForRival(sw);

        switch (mode) {
            case BRAWL -> {
                brawlTimer -= 10;
                net.minecraft.world.entity.LivingEntity t = getTarget();
                if (t == null || !t.isAlive() || brawlTimer <= 0 || distanceToSqr(t) > 24 * 24) {
                    if (t instanceof ScalperEntity && !t.isAlive()) shout(sw, "tcgshop.scalper.brawl_won", getDisplayName());
                    setTarget(null);
                    if (!stolen.isEmpty()) startFleeing();
                    else mode = Mode.CALM;
                }
            }
            case CALM -> {
                if (shopVisit) { // 1.1: walks straight to the shop owner
                    ServerPlayer owner = markPlayer();
                    if (owner == null || alreadyOffered.contains(owner.getUUID()) || distanceToSqr(owner) > 64 * 64) { leaveShop(null); return; }
                    if (PackOpener.isOpening(owner)) { becomeHostile(owner, "tcgshop.scalper.saw_opening"); return; }
                    if (distanceToSqr(owner) < 3.5 * 3.5) makeOffer(owner);
                    else if (getNavigation().isDone() || tickCount % 40 == 0) getNavigation().moveTo(owner, 1.0);
                    return;
                }
                Player near = sw.getNearestPlayer(this, 16);
                if (!(near instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
                if (PackOpener.isOpening(p)) {
                    becomeHostile(p, "tcgshop.scalper.saw_opening");
                    return;
                }
                if (alreadyOffered.contains(p.getUUID())) return;
                double d = distanceToSqr(p);
                if (d < 3.5 * 3.5) makeOffer(p);
                else getNavigation().moveTo(p, 1.0);
            }
            case OFFERING -> {
                ServerPlayer p = markPlayer();
                if (p == null) { mode = Mode.CALM; return; }
                getNavigation().stop();
                getLookControl().setLookAt(p, 30f, 30f);
                if (PackOpener.isOpening(p)) becomeHostile(p, "tcgshop.scalper.saw_opening");
                else if (timer <= 0) { if (shopVisit) leaveShop("tcgshop.scalper.shop_ignored"); else becomeHostile(p, "tcgshop.scalper.ignored"); }
                else if (distanceToSqr(p) > 12 * 12) { if (shopVisit) leaveShop("tcgshop.scalper.shop_ignored"); else becomeHostile(p, "tcgshop.scalper.ran_away"); }
            }
            case HOSTILE -> {
                ServerPlayer p = markPlayer();
                if (p == null || distanceToSqr(p) > 48 * 48) {
                    if (!stolen.isEmpty()) startFleeing();
                    else { mode = Mode.CALM; setTarget(null); }
                    return;
                }
                setTarget(p);
            }
            case FLEEING -> {
                ServerPlayer p = markPlayer();
                fleePhaseTicks -= 10;
                if (fleePhaseTicks <= 0) {
                    admiring = !admiring;
                    if (admiring) {
                        // stops to look at what it stole: the player can catch up
                        fleePhaseTicks = 50 + random.nextInt(30);
                        entityData.set(EMOTE, 1 + random.nextInt(2));
                        playSound(SoundEvents.PIGLIN_ADMIRING_ITEM, 1f, 1.1f);
                    } else {
                        fleePhaseTicks = 60 + random.nextInt(50);
                        entityData.set(EMOTE, 0);
                    }
                }
                if (admiring) {
                    getNavigation().stop();
                    setXRot(45f);
                    if (tickCount % 20 == 0) ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                            getX(), getY() + 2.2, getZ(), 2, 0.2, 0.1, 0.2, 0);
                } else if (p != null && distanceToSqr(p) < 40 * 40) {
                    Vec3 away = position().subtract(p.position()).normalize().scale(12);
                    getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.45);
                }
                // only despawns if the robbed player died/left or is very far away
                lostTicks = (p == null || distanceToSqr(p) > 64 * 64) ? lostTicks + 10 : 0;
                if (lostTicks > 600) discard();
            }
            case SATISFIED -> {
                if (shopLeaving) { // 1.1: a shop visitor walks back out the way it came and vanishes
                    if (shopExit == null) shopExit = position();
                    if (getNavigation().isDone() || tickCount % 40 == 0) getNavigation().moveTo(shopExit.x, shopExit.y, shopExit.z, 1.0);
                    Player near = sw.getNearestPlayer(this, 40);
                    if (distanceToSqr(shopExit) < 2.5 * 2.5 || timer <= 0 || near == null) {
                        sw.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                        discard();
                    }
                    return;
                }
                if (timer <= 0) {
                    Player near = sw.getNearestPlayer(this, 24);
                    if (near == null) discard();
                }
            }
        }
    }

    /** Another Scalper in its "territory": sometimes they fight each other. */
    private void lookForRival(ServerLevel sw) {
        List<ScalperEntity> near = sw.getEntitiesOfClass(ScalperEntity.class, getBoundingBox().inflate(12),
                o -> o != this && o.isAlive() && (o.mode == Mode.CALM || o.mode == Mode.SATISFIED) && !dev.tcgshop.compat.CobblemonBattles.isFighting(o));
        if (near.isEmpty()) return;
        ScalperEntity rival = near.get(random.nextInt(near.size()));
        // with Cobblemon, half the time they settle it with a Pokémon duel
        if (random.nextFloat() < 0.5f && dev.tcgshop.compat.NpcPokemonDuel.start(this, rival, POOL, POOL, 20 + random.nextInt(25), 0,
                (winner, loser) -> {
                    if (loser instanceof ScalperEntity ls) ls.loseDuel();
                    if (winner instanceof ScalperEntity ws) ws.mode = Mode.CALM;
                })) return;
        startBrawl(rival);
        rival.startBrawl(this);
        shout(sw, "tcgshop.scalper.brawl", getDisplayName(), rival.getDisplayName());
        playSound(SoundEvents.VINDICATOR_CELEBRATE, 1f, 0.8f);
    }

    /** Lost a Pokémon duel against another Scalper: drops part of its loot and leaves. */
    void loseDuel() {
        for (int i = 0; i < stolen.getContainerSize(); i++) if (!stolen.getItem(i).isEmpty() && random.nextBoolean()) spawnAtLocation(stolen.removeItemNoUpdate(i));
        NpcLines.speak(this, Component.translatable("tcgshop.scalper.duel_lost"));
        mode = Mode.SATISFIED;
        timer = 200;
    }

    public void startBrawl(net.minecraft.world.entity.LivingEntity enemy) {
        if (mode == Mode.FLEEING && !stolen.isEmpty() && !(enemy instanceof Player)) return; // with loot it prefers to flee
        mode = Mode.BRAWL;
        brawlTimer = 20 * 30;
        admiring = false;
        entityData.set(EMOTE, EMOTE_ANGRY);
        setTarget(enemy);
    }

    /** Chat notice to nearby players. */
    private void shout(ServerLevel sw, String key, Object... args) {
        for (ServerPlayer p : sw.getPlayers(pl -> pl.distanceToSqr(this) < 40 * 40))
            p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GOLD), false);
    }

    @Override
    public String[] battlePool() { return POOL; }

    /** Right click (with Cobblemon installed): you challenge it to a Pokémon battle. If it loses, it dies. */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !dev.tcgshop.compat.CobblemonBattles.available()) return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer sp && !sp.isSpectator()) {
            if (dev.tcgshop.compat.CobblemonBattles.challenge(sp, this)) {
                mark = sp.getUUID();
                say(sp, "tcgshop.scalper.battle_start");
                playSound(SoundEvents.PILLAGER_CELEBRATE, 1f, 1f);
                CardAdvancements.grant(sp, "battle_scalper");
            }
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void onBattleEnd(ServerPlayer p, boolean playerWon) {
        if (playerWon) {
            say(p, "tcgshop.scalper.battle_lost");
            CardAdvancements.grant(p, "battle_win");
            hurt(((ServerLevel) level()).damageSources().playerAttack(p), 1000f);
        } else {
            say(p, "tcgshop.scalper.battle_won");
            mark = p.getUUID();
            ItemStack loot = steal(p);
            if (!loot.isEmpty()) {
                stolen.addItem(loot);
                say(p, "tcgshop.scalper.stole", loot.getDisplayName());
            }
            startFleeing();
        }
    }

    private void makeOffer(ServerPlayer p) {
        List<Item> sealed = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !dev.tcgshop.shop.PackLock.locked(id)) sealed.add(BuiltInRegistries.ITEM.get(id));
        if (sealed.isEmpty()) return;
        Item pick = sealed.get(random.nextInt(sealed.size()));
        offerPack = BuiltInRegistries.ITEM.getKey(pick);
        long market = PriceService.sealedCd(CardInfo.setId(offerPack), PriceService.Sealed.PACK);
        offerPrice = Math.max(50, Math.round(market * (2.5 + random.nextDouble() * 1.5) / 50.0) * 50);
        mode = Mode.OFFERING;
        mark = p.getUUID();
        timer = 400; // 20 s to decide
        alreadyOffered.add(p.getUUID());
        say(p, "tcgshop.scalper.offer_line");
        playSound(SoundEvents.VILLAGER_AMBIENT, 1f, 0.7f);
        CardAdvancements.grant(p, "scalper_meet");
        if (Net.INSTANCE.canSend(p, ScalperOfferPayload.ID)) {
            Net.INSTANCE.send(p, new ScalperOfferPayload(getId(), new ItemStack(pick), offerPrice, market));
        }
    }

    /** The player's answer to the offer. */
    public void answer(ServerPlayer p, boolean accept) {
        if (mode != Mode.OFFERING || !p.getUUID().equals(mark)) return;
        if (!accept) {
            if (shopVisit) leaveShop("tcgshop.scalper.shop_rejected"); // 1.1: in your shop it just leaves
            else becomeHostile(p, "tcgshop.scalper.rejected");
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(p, offerPrice)) {
            if (shopVisit) leaveShop("tcgshop.scalper.shop_no_money");
            else becomeHostile(p, "tcgshop.scalper.no_money");
            return;
        }
        Item item = CardInfo.item(offerPack);
        if (item != null) p.getInventory().placeItemBackInInventory(new ItemStack(item));
        say(p, "tcgshop.scalper.sold", Component.literal(PriceService.format(offerPrice)).withStyle(ChatFormatting.GOLD));
        playSound(SoundEvents.VILLAGER_YES, 1f, 0.8f);
        CardAdvancements.grant(p, "scalper_buy");
        mode = Mode.SATISFIED;
        timer = 1200;
        if (shopVisit) { shopLeaving = true; timer = 600; }
    }

    // ------------------------------------------------------------------ 1.1: shop visits

    /** Leaves the shop (a visitor turned down, ignored or done): no hostility, walks out and vanishes. */
    private void leaveShop(@org.jetbrains.annotations.Nullable String lineKey) {
        ServerPlayer p = markPlayer();
        if (lineKey != null && p != null) say(p, lineKey);
        mode = Mode.SATISFIED;
        shopLeaving = true;
        timer = 600;
        setTarget(null);
        entityData.set(EMOTE, 0);
    }

    public boolean isShopVisitor() { return shopVisit; }

    /** How often each shop rolls for a scalper visit (ticks); the half-day chance is spread over these checks. */
    private static final int SHOP_CHECK = 1200;
    /** At most this many scalper visits per shop and Minecraft day (in memory). */
    private static final int SHOP_MAX_PER_DAY = 2;
    private static final java.util.Map<UUID, long[]> SHOP_VISITS = new java.util.HashMap<>();

    /** Can a scalper come into this shop (tier, open, active)? */
    public static boolean shopEligible(dev.tcgshop.shop.ShopZones.Zone z) {
        if (z == null || z.closed || z.dormant) return false;
        dev.tcgshop.shop.ShopTier min = dev.tcgshop.shop.ShopTier.byKey(dev.tcgshop.EconomyConfig.INSTANCE.scalperShopMinTier);
        return z.tier >= (min == null ? dev.tcgshop.shop.ShopTier.STONE.rank() : min.rank());
    }

    /** Every minute, each eligible shop whose owner is inside it rolls EconomyConfig.scalperShopVisitChance (per half day). */
    public static void shopVisitSpawner(ServerLevel world) {
        if (world.getGameTime() % SHOP_CHECK != 900) return;
        double chance = dev.tcgshop.EconomyConfig.INSTANCE.scalperShopVisitChance;
        if (chance <= 0 || world.players().isEmpty()) return;
        double perCheck = Math.min(1, chance * SHOP_CHECK / 12_000.0);
        for (dev.tcgshop.shop.ShopZones.Zone z : new ArrayList<>(dev.tcgshop.shop.ShopZones.get(world).all())) {
            if (!shopEligible(z) || world.random.nextDouble() >= perCheck) continue;
            trySpawnAtShop(world, z);
        }
    }

    /** Sends a scalper to this shop if its owner is inside, none is around and today's visits are not used up. Returns it, or null. */
    @org.jetbrains.annotations.Nullable
    public static ScalperEntity trySpawnAtShop(ServerLevel world, dev.tcgshop.shop.ShopZones.Zone z) {
        if (!shopEligible(z)) return null;
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        if (owner == null || owner.level() != world || owner.isSpectator() || owner.isCreative() || !z.contains(owner.blockPosition())) return null;
        long day = world.getDayTime() / 24000;
        long[] v = SHOP_VISITS.computeIfAbsent(z.id, k -> new long[]{day, 0});
        if (v[0] != day) { v[0] = day; v[1] = 0; }
        if (v[1] >= SHOP_MAX_PER_DAY) return null;
        if (!world.getEntitiesOfClass(ScalperEntity.class, z.box().inflate(32), e -> e.isAlive()).isEmpty()) return null;
        Vec3 pos = dev.tcgshop.shop.ShopLife.arrivalSpot(world, z);
        if (pos == null) return null;
        ScalperEntity s = TcgEntities.SCALPER.create(world);
        if (s == null) return null;
        s.moveTo(pos.x, pos.y, pos.z, world.random.nextFloat() * 360f, 0f);
        s.shopVisit = true;
        s.shopExit = pos;
        s.mark = owner.getUUID();
        world.addFreshEntity(s);
        v[1]++;
        owner.displayClientMessage(Component.translatable("tcgshop.scalper.shop_arrived").withStyle(ChatFormatting.GOLD), false);
        dev.tcgshop.TcgShop.LOGGER.info("[shop] a Scalper walks into {}'s shop at {}", z.ownerName, z.core.toShortString());
        return s;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && mode == Mode.HOSTILE && target instanceof ServerPlayer p) {
            ItemStack loot = steal(p);
            if (loot.isEmpty() && !stolen.isEmpty()) {
                startFleeing(); // already has loot: flees again
            } else if (!loot.isEmpty()) {
                stolen.addItem(loot);
                say(p, "tcgshop.scalper.stole", loot.getDisplayName());
                p.displayClientMessage(Component.translatable("tcgshop.scalper.robbed_you", getDisplayName(), loot.getDisplayName()).withStyle(ChatFormatting.RED), true);
                playSound(SoundEvents.ITEM_PICKUP, 1f, 0.6f);
                CardAdvancements.grant(p, "scalper_robbed");
                startFleeing();
            }
        }
        return hit;
    }

    private void startFleeing() {
        mode = Mode.FLEEING;
        admiring = false;
        fleePhaseTicks = 60 + random.nextInt(40);
        lostTicks = 0;
        entityData.set(EMOTE, 0);
        setTarget(null);
        setPersistenceRequired(); // with loot it does not despawn naturally
    }

    /** If the player hits it, it fights back (even while fleeing with the loot). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && isAlive() && source.getEntity() instanceof net.minecraft.world.entity.Mob mob && mob != this && mob.isAlive()
                && mode != Mode.HOSTILE) {
            startBrawl(mob); // a zombie, a skeleton or another Scalper hits it: it fights back
        }
        if (hurt && isAlive() && source.getEntity() instanceof ServerPlayer p && !p.isCreative()) {
            if (mode != Mode.HOSTILE) {
                mode = Mode.HOSTILE;
                mark = p.getUUID();
                admiring = false;
                entityData.set(EMOTE, 0);
                say(p, "tcgshop.scalper.fight_back");
            }
            setTarget(p);
        }
        return hurt;
    }

    /** Steals a random stack of packs or cards from the inventory (not from the General Binder). */
    private ItemStack steal(ServerPlayer p) {
        Inventory inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (CardInfo.isCard(st) || CardInfo.isPack(st)) slots.add(i);
        }
        if (slots.isEmpty()) return ItemStack.EMPTY;
        int slot = slots.get(random.nextInt(slots.size()));
        ItemStack st = inv.getItem(slot).copy();
        inv.setItem(slot, ItemStack.EMPTY);
        return st;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
        super.dropCustomDeathLoot(world, source, causedByPlayer);
        for (int i = 0; i < stolen.getContainerSize(); i++) {
            ItemStack st = stolen.getItem(i);
            if (!st.isEmpty()) spawnAtLocation(st);
        }
        stolen.clearContent();
        // 1.1: a shop visitor brings its pack straight to you: killing it must not be a free-pack farm (it still drops what it stole)
        if (!shopVisit && random.nextFloat() < 0.35f && offerPack != null) {
            Item item = CardInfo.item(offerPack);
            if (item != null) spawnAtLocation(new ItemStack(item));
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (source.getEntity() instanceof ServerPlayer p) {
            CardCollection c = CollectionService.playerData(p);
            c.scalpersDefeated++;
            CardAdvancements.grant(p, "scalper_defeat");
            if (c.scalpersDefeated >= 10) CardAdvancements.grant(p, "scalper_defeat_10");
            say(p, "tcgshop.scalper.defeated");
        }
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || !stolen.isEmpty() || shopVisit;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("Mode", mode.name());
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
        nbt.put("Stolen", stolen.createTag(registryAccess()));
        if (shopVisit) { // 1.1
            nbt.putBoolean("ShopVisit", true);
            if (shopExit != null) { nbt.putDouble("ShopExitX", shopExit.x); nbt.putDouble("ShopExitY", shopExit.y); nbt.putDouble("ShopExitZ", shopExit.z); }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        entityData.set(TITLE, nbt.getString("NpcTitle"));
        entityData.set(SKIN, nbt.getInt("NpcSkin"));
        try { mode = Mode.valueOf(nbt.getString("Mode")); } catch (Exception e) { mode = Mode.CALM; }
        if (mode == Mode.OFFERING || mode == Mode.HOSTILE || mode == Mode.BRAWL) mode = Mode.CALM;
        stolen.fromTag(nbt.getList("Stolen", 10), registryAccess());
        shopVisit = nbt.getBoolean("ShopVisit");
        if (nbt.contains("ShopExitY")) shopExit = new Vec3(nbt.getDouble("ShopExitX"), nbt.getDouble("ShopExitY"), nbt.getDouble("ShopExitZ"));
        if (shopVisit && mode != Mode.FLEEING) { mode = Mode.SATISFIED; shopLeaving = true; timer = 600; } // on reload it just leaves
    }
}
