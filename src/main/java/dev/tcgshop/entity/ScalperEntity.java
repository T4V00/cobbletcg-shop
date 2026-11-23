package dev.tcgshop.entity;

import dev.tcgshop.advancement.CardAdvancements;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.collection.CardCollection;
import dev.tcgshop.collection.CollectionService;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.network.ScalperOfferPayload;
import dev.tcgshop.pack.PackOpener;
import dev.tcgshop.pricing.PriceService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
 */
public class ScalperEntity extends HostileEntity implements TcgNpc, dev.tcgshop.compat.CobblemonBattles.Fighter {
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
    private Identifier offerPack;
    private long offerPrice;
    private final SimpleInventory stolen = new SimpleInventory(9);
    private final List<UUID> alreadyOffered = new ArrayList<>();
    /** While fleeing it alternates running / stopping to admire the loot (ticks left in the current phase). */
    private int fleePhaseTicks;
    private boolean admiring;
    private int lostTicks;

    /** 0 = nothing, 1 = thinking about money, 2 = loves what it sees. Synced to the client for the bubble. */
    private static final net.minecraft.entity.data.TrackedData<Integer> EMOTE =
            net.minecraft.entity.data.DataTracker.registerData(ScalperEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);

    private static final net.minecraft.entity.data.TrackedData<String> TITLE =
            net.minecraft.entity.data.DataTracker.registerData(ScalperEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.STRING);
    private static final net.minecraft.entity.data.TrackedData<Integer> SKIN =
            net.minecraft.entity.data.DataTracker.registerData(ScalperEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(EMOTE, 0);
        builder.add(TITLE, "");
        builder.add(SKIN, 0);
    }

    @Override public String roleKey() { return "tcgshop.role.scalper"; }
    @Override public String npcTitle() { return dataTracker.get(TITLE); }
    @Override public int npcSkin() { return dataTracker.get(SKIN); }

    /** Real name, skin and (40%) a Pokémon title. */
    private void ensureIdentity() {
        if (hasCustomName()) return;
        setCustomName(Text.literal(NpcIdentity.randomName(random)));
        setCustomNameVisible(true);
        dataTracker.set(TITLE, NpcIdentity.randomTitle(random, 0.4f));
        dataTracker.set(SKIN, NpcIdentity.randomSkin(random));
    }

    public int emote() {
        return dataTracker.get(EMOTE);
    }

    public ScalperEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)       // diamond sword
                .add(EntityAttributes.GENERIC_ARMOR, 20.0)              // full netherite armor
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 12.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.31)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.25, true) {
            @Override
            public boolean canStart() { return (mode == Mode.HOSTILE || mode == Mode.BRAWL) && super.canStart(); }

            @Override
            public boolean shouldContinue() { return (mode == Mode.HOSTILE || mode == Mode.BRAWL) && super.shouldContinue(); }
        });
        goalSelector.add(6, new WanderAroundFarGoal(this, 0.8) {
            @Override
            public boolean canStart() { return (mode == Mode.CALM || mode == Mode.SATISFIED) && super.canStart(); }
        });
        goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        goalSelector.add(8, new LookAroundGoal(this));
    }

    public Mode mode() { return mode; }

    private ServerPlayerEntity markPlayer() {
        if (mark == null || !(getWorld() instanceof ServerWorld sw)) return null;
        PlayerEntity p = sw.getPlayerByUuid(mark);
        return p instanceof ServerPlayerEntity sp && sp.isAlive() && !sp.isSpectator() && !sp.isCreative() ? sp : null;
    }

    private void say(ServerPlayerEntity p, String key, Object... args) {
        if (p == null) return;
        NpcLines.speak(this, Text.translatable(key, args));
    }

    private void becomeHostile(ServerPlayerEntity p, String line) {
        mode = Mode.HOSTILE;
        mark = p.getUuid();
        setTarget(p);
        say(p, line);
        playSound(SoundEvents.ENTITY_PILLAGER_AMBIENT, 1f, 0.9f);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        ensureIdentity();
        if (talkCooldown > 0) talkCooldown--;
        if (timer > 0) timer--;
        if (age % 10 != 0 || !(getWorld() instanceof ServerWorld sw)) return;
        if (dev.tcgshop.compat.CobblemonBattles.isFighting(this) || dev.tcgshop.compat.NpcPokemonDuel.busy(this)) return;
        if ((mode == Mode.CALM || mode == Mode.SATISFIED) && random.nextFloat() < 0.006f) lookForRival(sw);

        switch (mode) {
            case BRAWL -> {
                brawlTimer -= 10;
                net.minecraft.entity.LivingEntity t = getTarget();
                if (t == null || !t.isAlive() || brawlTimer <= 0 || squaredDistanceTo(t) > 24 * 24) {
                    if (t instanceof ScalperEntity && !t.isAlive()) shout(sw, "tcgshop.scalper.brawl_won", getDisplayName());
                    setTarget(null);
                    if (!stolen.isEmpty()) startFleeing();
                    else mode = Mode.CALM;
                }
            }
            case CALM -> {
                PlayerEntity near = sw.getClosestPlayer(this, 16);
                if (!(near instanceof ServerPlayerEntity p) || p.isCreative() || p.isSpectator()) return;
                if (PackOpener.isOpening(p)) {
                    becomeHostile(p, "tcgshop.scalper.saw_opening");
                    return;
                }
                if (alreadyOffered.contains(p.getUuid())) return;
                double d = squaredDistanceTo(p);
                if (d < 3.5 * 3.5) makeOffer(p);
                else getNavigation().startMovingTo(p, 1.0);
            }
            case OFFERING -> {
                ServerPlayerEntity p = markPlayer();
                if (p == null) { mode = Mode.CALM; return; }
                getNavigation().stop();
                getLookControl().lookAt(p, 30f, 30f);
                if (PackOpener.isOpening(p)) becomeHostile(p, "tcgshop.scalper.saw_opening");
                else if (timer <= 0) becomeHostile(p, "tcgshop.scalper.ignored");
                else if (squaredDistanceTo(p) > 12 * 12) becomeHostile(p, "tcgshop.scalper.ran_away");
            }
            case HOSTILE -> {
                ServerPlayerEntity p = markPlayer();
                if (p == null || squaredDistanceTo(p) > 48 * 48) {
                    if (!stolen.isEmpty()) startFleeing();
                    else { mode = Mode.CALM; setTarget(null); }
                    return;
                }
                setTarget(p);
            }
            case FLEEING -> {
                ServerPlayerEntity p = markPlayer();
                fleePhaseTicks -= 10;
                if (fleePhaseTicks <= 0) {
                    admiring = !admiring;
                    if (admiring) {
                        // stops to look at what it stole: the player can catch up
                        fleePhaseTicks = 50 + random.nextInt(30);
                        dataTracker.set(EMOTE, 1 + random.nextInt(2));
                        playSound(SoundEvents.ENTITY_PIGLIN_ADMIRING_ITEM, 1f, 1.1f);
                    } else {
                        fleePhaseTicks = 60 + random.nextInt(50);
                        dataTracker.set(EMOTE, 0);
                    }
                }
                if (admiring) {
                    getNavigation().stop();
                    setPitch(45f);
                    if (age % 20 == 0) ((ServerWorld) getWorld()).spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                            getX(), getY() + 2.2, getZ(), 2, 0.2, 0.1, 0.2, 0);
                } else if (p != null && squaredDistanceTo(p) < 40 * 40) {
                    Vec3d away = getPos().subtract(p.getPos()).normalize().multiply(12);
                    getNavigation().startMovingTo(getX() + away.x, getY(), getZ() + away.z, 1.45);
                }
                // only despawns if the robbed player died/left or is very far away
                lostTicks = (p == null || squaredDistanceTo(p) > 64 * 64) ? lostTicks + 10 : 0;
                if (lostTicks > 600) discard();
            }
            case SATISFIED -> {
                if (timer <= 0) {
                    PlayerEntity near = sw.getClosestPlayer(this, 24);
                    if (near == null) discard();
                }
            }
        }
    }

    /** Another Scalper in its "territory": sometimes they fight each other. */
    private void lookForRival(ServerWorld sw) {
        List<ScalperEntity> near = sw.getEntitiesByClass(ScalperEntity.class, getBoundingBox().expand(12),
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
        playSound(SoundEvents.ENTITY_VINDICATOR_CELEBRATE, 1f, 0.8f);
    }

    /** Lost a Pokémon duel against another Scalper: drops part of its loot and leaves. */
    void loseDuel() {
        for (int i = 0; i < stolen.size(); i++) if (!stolen.getStack(i).isEmpty() && random.nextBoolean()) dropStack(stolen.removeStack(i));
        NpcLines.speak(this, Text.translatable("tcgshop.scalper.duel_lost"));
        mode = Mode.SATISFIED;
        timer = 200;
    }

    public void startBrawl(net.minecraft.entity.LivingEntity enemy) {
        if (mode == Mode.FLEEING && !stolen.isEmpty() && !(enemy instanceof PlayerEntity)) return; // with loot it prefers to flee
        mode = Mode.BRAWL;
        brawlTimer = 20 * 30;
        admiring = false;
        dataTracker.set(EMOTE, EMOTE_ANGRY);
        setTarget(enemy);
    }

    /** Chat notice to nearby players. */
    private void shout(ServerWorld sw, String key, Object... args) {
        for (ServerPlayerEntity p : sw.getPlayers(pl -> pl.squaredDistanceTo(this) < 40 * 40))
            p.sendMessage(Text.translatable(key, args).formatted(Formatting.GOLD), false);
    }

    @Override
    public String[] battlePool() { return POOL; }

    /** Right click (with Cobblemon installed): you challenge it to a Pokémon battle. If it loses, it dies. */
    @Override
    protected net.minecraft.util.ActionResult interactMob(PlayerEntity player, net.minecraft.util.Hand hand) {
        if (hand != net.minecraft.util.Hand.MAIN_HAND || !dev.tcgshop.compat.CobblemonBattles.available()) return super.interactMob(player, hand);
        if (player instanceof ServerPlayerEntity sp && !sp.isSpectator()) {
            if (dev.tcgshop.compat.CobblemonBattles.challenge(sp, this)) {
                mark = sp.getUuid();
                say(sp, "tcgshop.scalper.battle_start");
                playSound(SoundEvents.ENTITY_PILLAGER_CELEBRATE, 1f, 1f);
                CardAdvancements.grant(sp, "battle_scalper");
            }
        }
        return net.minecraft.util.ActionResult.success(getWorld().isClient);
    }

    @Override
    public void onBattleEnd(ServerPlayerEntity p, boolean playerWon) {
        if (playerWon) {
            say(p, "tcgshop.scalper.battle_lost");
            CardAdvancements.grant(p, "battle_win");
            damage(((ServerWorld) getWorld()).getDamageSources().playerAttack(p), 1000f);
        } else {
            say(p, "tcgshop.scalper.battle_won");
            mark = p.getUuid();
            ItemStack loot = steal(p);
            if (!loot.isEmpty()) {
                stolen.addStack(loot);
                say(p, "tcgshop.scalper.stole", loot.toHoverableText());
            }
            startFleeing();
        }
    }

    private void makeOffer(ServerPlayerEntity p) {
        List<Item> sealed = new ArrayList<>();
        for (Identifier id : Registries.ITEM.getIds())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_") && !dev.tcgshop.shop.PackLock.locked(id)) sealed.add(Registries.ITEM.get(id));
        if (sealed.isEmpty()) return;
        Item pick = sealed.get(random.nextInt(sealed.size()));
        offerPack = Registries.ITEM.getId(pick);
        long market = PriceService.sealedCd(CardInfo.setId(offerPack), PriceService.Sealed.PACK);
        offerPrice = Math.max(50, Math.round(market * (2.5 + random.nextDouble() * 1.5) / 50.0) * 50);
        mode = Mode.OFFERING;
        mark = p.getUuid();
        timer = 400; // 20 s to decide
        alreadyOffered.add(p.getUuid());
        say(p, "tcgshop.scalper.offer_line");
        playSound(SoundEvents.ENTITY_VILLAGER_AMBIENT, 1f, 0.7f);
        CardAdvancements.grant(p, "scalper_meet");
        if (ServerPlayNetworking.canSend(p, ScalperOfferPayload.ID)) {
            ServerPlayNetworking.send(p, new ScalperOfferPayload(getId(), new ItemStack(pick), offerPrice, market));
        }
    }

    /** The player's answer to the offer. */
    public void answer(ServerPlayerEntity p, boolean accept) {
        if (mode != Mode.OFFERING || !p.getUuid().equals(mark)) return;
        if (!accept) {
            becomeHostile(p, "tcgshop.scalper.rejected");
            return;
        }
        if (!CobbleDollarsBridge.tryCharge(p, offerPrice)) {
            becomeHostile(p, "tcgshop.scalper.no_money");
            return;
        }
        Item item = CardInfo.item(offerPack);
        if (item != null) p.getInventory().offerOrDrop(new ItemStack(item));
        say(p, "tcgshop.scalper.sold", Text.literal(PriceService.format(offerPrice)).formatted(Formatting.GOLD));
        playSound(SoundEvents.ENTITY_VILLAGER_YES, 1f, 0.8f);
        CardAdvancements.grant(p, "scalper_buy");
        mode = Mode.SATISFIED;
        timer = 1200;
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && mode == Mode.HOSTILE && target instanceof ServerPlayerEntity p) {
            ItemStack loot = steal(p);
            if (loot.isEmpty() && !stolen.isEmpty()) {
                startFleeing(); // already has loot: flees again
            } else if (!loot.isEmpty()) {
                stolen.addStack(loot);
                say(p, "tcgshop.scalper.stole", loot.toHoverableText());
                p.sendMessage(Text.translatable("tcgshop.scalper.robbed_you", getDisplayName(), loot.toHoverableText()).formatted(Formatting.RED), true);
                playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1f, 0.6f);
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
        dataTracker.set(EMOTE, 0);
        setTarget(null);
        setPersistent(); // with loot it does not despawn naturally
    }

    /** If the player hits it, it fights back (even while fleeing with the loot). */
    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && isAlive() && source.getAttacker() instanceof net.minecraft.entity.mob.MobEntity mob && mob != this && mob.isAlive()
                && mode != Mode.HOSTILE) {
            startBrawl(mob); // a zombie, a skeleton or another Scalper hits it: it fights back
        }
        if (hurt && isAlive() && source.getAttacker() instanceof ServerPlayerEntity p && !p.isCreative()) {
            if (mode != Mode.HOSTILE) {
                mode = Mode.HOSTILE;
                mark = p.getUuid();
                admiring = false;
                dataTracker.set(EMOTE, 0);
                say(p, "tcgshop.scalper.fight_back");
            }
            setTarget(p);
        }
        return hurt;
    }

    /** Steals a random stack of packs or cards from the inventory (not from the General Binder). */
    private ItemStack steal(ServerPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (CardInfo.isCard(st) || CardInfo.isPack(st)) slots.add(i);
        }
        if (slots.isEmpty()) return ItemStack.EMPTY;
        int slot = slots.get(random.nextInt(slots.size()));
        ItemStack st = inv.getStack(slot).copy();
        inv.setStack(slot, ItemStack.EMPTY);
        return st;
    }

    @Override
    protected void dropEquipment(ServerWorld world, DamageSource source, boolean causedByPlayer) {
        super.dropEquipment(world, source, causedByPlayer);
        for (int i = 0; i < stolen.size(); i++) {
            ItemStack st = stolen.getStack(i);
            if (!st.isEmpty()) dropStack(st);
        }
        stolen.clear();
        if (random.nextFloat() < 0.35f && offerPack != null) {
            Item item = CardInfo.item(offerPack);
            if (item != null) dropStack(new ItemStack(item));
        }
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (source.getAttacker() instanceof ServerPlayerEntity p) {
            CardCollection c = CollectionService.playerData(p);
            c.scalpersDefeated++;
            CardAdvancements.grant(p, "scalper_defeat");
            if (c.scalpersDefeated >= 10) CardAdvancements.grant(p, "scalper_defeat_10");
            say(p, "tcgshop.scalper.defeated");
        }
    }

    @Override
    public boolean cannotDespawn() {
        return super.cannotDespawn() || !stolen.isEmpty();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Mode", mode.name());
        nbt.putString("NpcTitle", npcTitle());
        nbt.putInt("NpcSkin", npcSkin());
        nbt.put("Stolen", stolen.toNbtList(getRegistryManager()));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(TITLE, nbt.getString("NpcTitle"));
        dataTracker.set(SKIN, nbt.getInt("NpcSkin"));
        try { mode = Mode.valueOf(nbt.getString("Mode")); } catch (Exception e) { mode = Mode.CALM; }
        if (mode == Mode.OFFERING || mode == Mode.HOSTILE || mode == Mode.BRAWL) mode = Mode.CALM;
        stolen.readNbtList(nbt.getList("Stolen", 10), getRegistryManager());
    }
}
