package dev.tcgshop.luxury;

import dev.masterball.api.platform.event.ServerEvents;
import dev.masterball.api.card.CardInfo;
import dev.tcgshop.entity.CustomerEntity;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.PackLock;
import dev.tcgshop.shop.ShopSeats;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 1.0 · Statue packs (ROADMAP "Statue tier 3 and up"): customers walking past a luxury statue in a shop sometimes drop
 * a random sealed pack on the floor next to it, as if the statue distracted them.
 * <p>
 * Tier 3 is the Gold statue. The roadmap says "the chance grows with the tier, but it is almost never at gold": Gold is
 * the FIRST eligible tier, so it gets an "almost never" chance and every tier above it a growing one (Emerald, Diamond,
 * Netherite, MasterBall). Defaults and switches live in config/tcgshop-luxury.json ({@link LuxuryConfig}:
 * statuePacksEnabled, statuePackMinTier, statuePackChance per tier, statuePackRadius, statuePackCooldownSeconds).
 * <p>
 * Each customer rolls once per statue per visit. The pack is never from the locked top sets ({@link PackLock}); if no
 * set is locked (lockTopPackSets = 0) the {@link #TOP_EXCLUDED} priciest sets are skipped anyway.
 */
public final class StatuePacks {
    /** Priciest pack sets always skipped when PackLock locks nothing. */
    private static final int TOP_EXCLUDED = 3;

    private record Key(ResourceKey<Level> dim, long pos) {}

    /** Statues each customer already rolled for (weak: a removed customer never leaks). */
    private static final Map<Entity, Set<Long>> ROLLED = new WeakHashMap<>();
    /** Last drop per statue (world time). */
    private static final Map<Key, Long> LAST_DROP = new HashMap<>();

    private StatuePacks() {}

    public static void register() {
        ServerEvents.TICK_END.register(StatuePacks::tick);
        ServerEvents.STOPPED.register(s -> { ROLLED.clear(); LAST_DROP.clear(); });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 11) return;
        LuxuryConfig cfg = LuxuryConfig.INSTANCE;
        if (!cfg.statuePacksEnabled) return;
        double r = cfg.statuePackRadius, r2 = r * r;
        long cooldown = Math.max(0, cfg.statuePackCooldownSeconds) * 20L;
        for (ServerLevel w : server.getAllLevels()) {
            for (ShopZones.Zone z : ShopZones.get(w).all()) {
                if (z.closed || !w.hasChunkAt(z.core)) continue; // 1.1: closed shop
                List<CustomerEntity> cs = w.getEntitiesOfClass(CustomerEntity.class, z.box(),
                        c -> c.isAlive() && c.type() != CustomerEntity.Type.THIEF && z.contains(c.blockPosition()));
                if (cs.isEmpty()) continue;
                for (BlockPos sp : ShopSeats.statues(w, z)) {
                    BlockState st = w.getBlockState(sp);
                    if (!(st.getBlock() instanceof StatueBlock sb)) continue;
                    double chance = cfg.packChance(sb.tier);
                    if (chance <= 0) continue;
                    Key key = new Key(w.dimension(), sp.asLong());
                    Long last = LAST_DROP.get(key);
                    long now = w.getGameTime();
                    if (last != null && now - last < cooldown && now >= last) continue;
                    Vec3 center = sp.getCenter();
                    for (CustomerEntity c : cs) {
                        double dx = c.getX() - center.x, dz = c.getZ() - center.z;
                        if (dx * dx + dz * dz > r2 || Math.abs(c.getY() - sp.getY()) > 3) continue;
                        if (!ROLLED.computeIfAbsent(c, k -> new HashSet<>()).add(sp.asLong())) continue; // once per visit
                        if (w.getRandom().nextDouble() >= chance) continue;
                        if (drop(w, z, sp, sb, c)) {
                            LAST_DROP.put(key, now);
                            break;
                        }
                    }
                }
            }
        }
    }

    /** Drops a random allowed pack on the floor between the customer and the statue. */
    private static boolean drop(ServerLevel w, ShopZones.Zone z, BlockPos statue, StatueBlock sb, CustomerEntity c) {
        Item pack = randomPack(w.getRandom());
        if (pack == null) return false;
        Vec3 at = dropSpot(w, z, statue, c);
        ItemEntity e = new ItemEntity(w, at.x, at.y + 0.25, at.z, new ItemStack(pack));
        e.setDeltaMovement((w.getRandom().nextDouble() - 0.5) * 0.08, 0.18, (w.getRandom().nextDouble() - 0.5) * 0.08);
        e.setDefaultPickUpDelay();
        if (!w.addFreshEntity(e)) return false;
        w.playSound(null, BlockPos.containing(at), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 0.7f, 1.2f);
        w.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.4, at.z, 5, 0.2, 0.2, 0.2, 0.02);
        ServerPlayer owner = w.getServer().getPlayerList().getPlayer(z.owner);
        if (owner != null && owner.serverLevel() == w && owner.distanceToSqr(at) < 48 * 48)
            owner.displayClientMessage(Component.translatable("tcgshop.statue.pack_dropped", sb.getName()).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** A free spot next to the statue on the customer's side (or the customer's own position). */
    private static Vec3 dropSpot(ServerLevel w, ShopZones.Zone z, BlockPos statue, CustomerEntity c) {
        Vec3 s = statue.getCenter(), from = c.position();
        Vec3 dir = new Vec3(from.x - s.x, 0, from.z - s.z);
        if (dir.lengthSqr() > 1e-4) {
            dir = dir.normalize();
            for (double d = 1.2; d <= 2.2; d += 0.5) {
                BlockPos q = BlockPos.containing(s.x + dir.x * d, statue.getY(), s.z + dir.z * d);
                if (z.contains(q) && !w.getBlockState(q).blocksMotion() && !w.getBlockState(q.above()).blocksMotion())
                    return new Vec3(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
            }
        }
        return from;
    }

    /** Random sealed pack: never from a locked set nor (when nothing is locked) from the priciest sets. */
    @Nullable
    private static Item randomPack(RandomSource r) {
        Set<String> skip = new HashSet<>(PackLock.lockedSets());
        List<ResourceLocation> packs = new ArrayList<>();
        Set<String> sets = new LinkedHashSet<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet())
            if (CardInfo.NS.equals(id.getNamespace()) && id.getPath().contains("/sealed_")) {
                packs.add(id);
                sets.add(CardInfo.setId(id));
            }
        if (skip.isEmpty() && sets.size() > TOP_EXCLUDED + 1) {
            List<Map.Entry<String, Double>> ranked = new ArrayList<>();
            for (String s : sets) ranked.add(Map.entry(s, PriceService.sealedUsd(s, PriceService.Sealed.PACK)));
            ranked.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
            for (int i = 0; i < TOP_EXCLUDED; i++) skip.add(ranked.get(i).getKey());
        }
        List<Item> ok = new ArrayList<>();
        for (ResourceLocation id : packs) {
            String set = CardInfo.setId(id);
            if (set == null || skip.contains(set) || PackLock.locked(id)) continue;
            Item it = BuiltInRegistries.ITEM.get(id);
            if (CardInfo.isPack(new ItemStack(it))) ok.add(it);
        }
        return ok.isEmpty() ? null : ok.get(r.nextInt(ok.size()));
    }
}
