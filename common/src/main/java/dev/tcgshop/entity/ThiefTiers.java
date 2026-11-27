package dev.tcgshop.entity;

import dev.tcgshop.EconomyConfig;
import dev.tcgshop.phone.PhoneMessages;
import dev.tcgshop.pricing.PriceService;
import dev.tcgshop.shop.GuardUpgrades;
import dev.tcgshop.shop.SecurityLedger;
import dev.tcgshop.shop.ShopZones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * 1.0 beta 3: thief tiers. The more popular a shop (settled reputation, shop tier and sales), the better the thieves
 * it attracts. Tiers (0-based in code, shown 1-5):
 *
 *  0 PICKPOCKET  takes 1 item; any guard sees and detains it (the pre-1.0 thief).
 *  1 SHOPLIFTER  takes 2 items, faster; may go unnoticed and may slip out of a guard's grip once.
 *  2 PRO         takes the 3 priciest items, light armor, quick; hard to spot, slips up to twice.
 *  3 ROBBER      armed (iron sword, chain armor, 40 HP): grabs up to 8 of the priciest items at sword point and
 *                fights guards and you. Cannot be detained, only defeated. Popular shops only (Copper tier and up).
 *  4 HEIST       crew boss (diamond sword, iron armor, 70 HP): VERY rare, announced dramatically, empties EVERY display
 *                and fights its way out. Only very popular shops (Gold tier and up), at most once every heistCooldownDays.
 *
 * Whatever they carry goes back to its display (or drops) when they die before escaping. An unupgraded guard loses to
 * tiers 3-4; level 2 gear is an even fight against the heist boss and full gear wins it (GuardUpgrades).
 */
public final class ThiefTiers {
    public static final int PICKPOCKET = 0, SHOPLIFTER = 1, PRO = 2, ROBBER = 3, HEIST = 4;
    public static final String[] KEYS = {"pickpocket", "shoplifter", "pro", "robber", "heist"};
    private static final double[] HEALTH = {16, 22, 28, 40, 70};
    private static final double[] SPEED = {0.27, 0.30, 0.32, 0.31, 0.30};
    /** Most items each tier takes (the heist takes everything). */
    private static final int[] MAX_ITEMS = {1, 2, 3, 8, Integer.MAX_VALUE};
    /** How much one theft weighs in the shop's theft counter (ShopZones.Zone.stolen, used by the reputation). */
    private static final int[] INCIDENT_CAP = {1, 2, 3, 3, 5};
    /** Times a skilled thief may wriggle out of a guard's grip. */
    private static final int[] SLIPS = {0, 1, 2, 0, 0};
    /** Ticks between two sword swings. */
    private static final int[] ATTACK_COOLDOWN = {20, 20, 20, 20, 16};

    private ThiefTiers() {}

    public static int clamp(int tier) {
        return Math.max(PICKPOCKET, Math.min(HEIST, tier));
    }

    public static boolean armed(int tier) {
        return tier >= ROBBER;
    }

    public static int maxItems(int tier) {
        return MAX_ITEMS[clamp(tier)];
    }

    public static int incidentWeight(int tier, int items) {
        return Math.max(1, Math.min(items, INCIDENT_CAP[clamp(tier)]));
    }

    public static int slips(int tier) {
        return SLIPS[clamp(tier)];
    }

    public static int attackCooldown(int tier) {
        return ATTACK_COOLDOWN[clamp(tier)];
    }

    public static String roleKey(int tier) {
        return "tcgshop.role.thief_" + KEYS[clamp(tier)];
    }

    /** 0-100: settled reputation (55 %), shop tier (up to 30) and sales (up to 15). */
    public static double popularity(ShopZones.Zone z) {
        return 0.55 * Math.max(0, Math.min(100, z.reputation)) + 30.0 * z.tierInfo().rank() / 11.0 + Math.min(15, z.sales / 50.0);
    }

    /** Tier of a thief that just walked toward this shop. */
    public static int roll(ServerLevel world, ShopZones.Zone z) {
        EconomyConfig cfg = EconomyConfig.INSTANCE;
        double p = popularity(z);
        int rank = z.tierInfo().rank();
        // 1.1: higher shop tiers lean toward the stronger tiers (0 up to Brick .. tierThiefTierShift at Netherite). The armed
        // tiers keep their popularity, tier and cooldown gates, so guards and detectors stay what stops them.
        double shift = dev.tcgshop.shop.TierDifficulty.thiefTierShift(z);
        double r = world.random.nextDouble();
        if (cfg.armedThieves && !armedActive(world, z)) {
            long today = world.getDayTime() / 24000;
            SecurityLedger.ZoneSec sec = SecurityLedger.zone(world.getServer(), z.id);
            double mult = Math.max(0, Math.min(10, cfg.armedThiefChanceMult));
            if (p >= 85 && rank >= 6 && today - sec.lastHeistDay >= Math.max(1, cfg.heistCooldownDays)) {
                double c = 0.004 * mult * (1 + shift);
                if (r < c) return HEIST;
                r -= c;
            }
            if (p >= 70 && rank >= 4 && today - sec.lastRobberyDay >= Math.max(1, cfg.robberyCooldownDays)) {
                double c = (0.015 + 0.015 * Math.min(1, (p - 70) / 30.0)) * mult * (1 + 0.75 * shift);
                if (r < c) return ROBBER;
                r -= c;
            }
        }
        if (p >= 50 || shift > 0) {
            double c = (p >= 50 ? 0.06 + 0.06 * Math.min(1, (p - 50) / 50.0) : 0) + 0.08 * shift;
            if (r < c) return PRO;
            r -= c;
        }
        if (p >= 25 || shift > 0) {
            double c = (p >= 25 ? 0.20 + 0.15 * Math.min(1, (p - 25) / 75.0) : 0) + 0.10 * shift;
            if (r < c) return SHOPLIFTER;
        }
        return PICKPOCKET;
    }

    /** Is an armed robber or a heist crew already around this shop? (one at a time) */
    public static boolean armedActive(ServerLevel world, ShopZones.Zone z) {
        return !world.getEntitiesOfClass(CustomerEntity.class, z.box().inflate(96), c -> c.isAlive() && armed(c.thiefTier())).isEmpty();
    }

    /** Stats and gear of the tier (the loot is carried in the off hand by armed tiers, so their weapon shows). */
    public static void equip(CustomerEntity c, int tier) {
        tier = clamp(tier);
        set(c, c.getAttribute(Attributes.MAX_HEALTH), HEALTH[tier]);
        c.setHealth((float) HEALTH[tier]);
        set(c, c.getAttribute(Attributes.MOVEMENT_SPEED), SPEED[tier]);
        set(c, c.getAttribute(Attributes.KNOCKBACK_RESISTANCE), tier == HEIST ? 0.5 : tier == ROBBER ? 0.25 : 0);
        Item[] armor = switch (tier) {
            case PRO -> new Item[]{Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, null, null};
            case ROBBER -> new Item[]{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
            case HEIST -> new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
            default -> new Item[]{null, null, null, null};
        };
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            ItemStack st = armor[i] == null ? ItemStack.EMPTY : new ItemStack(armor[i]);
            if (tier == PRO && !st.isEmpty()) st.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1E1E24, false)); // dark hoodie
            c.setItemSlot(slots[i], st);
            c.setDropChance(slots[i], 0f);
        }
        if (armed(tier)) {
            c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tier == HEIST ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
            c.setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
    }

    private static void set(CustomerEntity c, AttributeInstance a, double v) {
        if (a != null) a.setBaseValue(v);
    }

    // ------------------------------------------------------------------ announcements

    private static void title(ServerPlayer p, Component title, Component sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** An armed thief is on its way: the heist is announced in advance (anonymous tip); the robber only to a sharp guard. */
    public static void onArmedSpawn(ServerLevel world, ShopZones.Zone z, CustomerEntity c, int tier) {
        long today = world.getDayTime() / 24000;
        SecurityLedger.ZoneSec sec = SecurityLedger.zone(world.getServer(), z.id);
        if (tier == HEIST) sec.lastHeistDay = today;
        sec.lastRobberyDay = today; // a heist also counts as the period's robbery
        SecurityLedger.dirty(world.getServer());
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        if (tier == HEIST) {
            PhoneMessages.send(world.getServer(), z.owner, "tcgshop.msg.sender.security", Component.translatable("tcgshop.heist.tip_msg", z.guard
                    ? Component.translatable("tcgshop.heist.tip_guard", z.guardName) : Component.translatable("tcgshop.heist.tip_noguard")), true);
            if (owner != null) {
                title(owner, Component.translatable("tcgshop.heist.incoming_title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.translatable("tcgshop.heist.incoming_sub").withStyle(ChatFormatting.GOLD));
                owner.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.9f, 0.9f);
            }
        } else if (z.guard && GuardUpgrades.level(world.getServer(), z.id, GuardUpgrades.Stat.DETECTION) >= 3 && owner != null) {
            owner.displayClientMessage(Component.translatable("tcgshop.robbery.radio", z.guardName).withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** The robbery / heist starts: titles, alarm and (heist) a server-wide broadcast. */
    public static void onStrike(ServerLevel world, ShopZones.Zone z, CustomerEntity c, int tier, int items, long value) {
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        world.playSound(null, c.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 0.6f);
        if (tier == HEIST) {
            world.playSound(null, c.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 1f);
            Component bc = Component.translatable("tcgshop.heist.broadcast", z.ownerName, items, PriceService.format(value)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) p.displayClientMessage(bc, false);
            if (owner != null) title(owner, Component.translatable("tcgshop.heist.strike_title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    Component.translatable("tcgshop.heist.strike_sub", items).withStyle(ChatFormatting.GOLD));
        } else if (owner != null) {
            title(owner, Component.translatable("tcgshop.robbery.strike_title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    Component.translatable("tcgshop.robbery.strike_sub", items).withStyle(ChatFormatting.GOLD));
            owner.displayClientMessage(Component.translatable("tcgshop.robbery.strike_chat", c.getDisplayName(), items, PriceService.format(value)).withStyle(ChatFormatting.RED), false);
        }
    }

    /** End of an armed theft: foiled (killed before escaping) or got away. Phone message, ledger stats, advancements. */
    public static void onOutcome(ServerLevel world, ShopZones.Zone z, CustomerEntity c, int tier, boolean foiled, int items, long value,
                                 @org.jetbrains.annotations.Nullable ServerPlayer killer) {
        if (!armed(tier)) return;
        SecurityLedger.ZoneSec sec = SecurityLedger.zone(world.getServer(), z.id);
        boolean heist = tier == HEIST;
        if (foiled) { if (heist) sec.heistsFoiled++; else sec.robberiesFoiled++; }
        else if (heist) sec.heistsLost++;
        else sec.robberiesLost++;
        SecurityLedger.dirty(world.getServer());
        String k = (heist ? "tcgshop.heist." : "tcgshop.robbery.") + (foiled ? "foiled_msg" : "lost_msg");
        PhoneMessages.send(world.getServer(), z.owner, "tcgshop.msg.sender.security", Component.translatable(k, items, PriceService.format(value)), heist);
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(z.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable(k, items, PriceService.format(value)).withStyle(foiled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            if (foiled) {
                dev.tcgshop.advancement.CardAdvancements.grant(owner, heist ? "stop_heist" : "stop_robbery");
                if (heist) {
                    Component bc = Component.translatable("tcgshop.heist.foiled_broadcast", z.ownerName).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
                    for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) p.displayClientMessage(bc, false);
                }
            }
        }
        if (killer != null && killer != owner) dev.tcgshop.advancement.CardAdvancements.grant(killer, heist ? "stop_heist" : "stop_robbery");
    }
}
