package dev.tcgshop.shop;

import dev.masterball.api.platform.event.ServerEvents;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tcgshop.EconomyConfig;
import dev.tcgshop.advancement.CardAdvancements;
import dev.tcgshop.economy.CobbleDollarsBridge;
import dev.tcgshop.entity.EmployeeEntity;
import dev.tcgshop.pricing.PriceService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
import java.util.Locale;
import java.util.UUID;

/**
 * 1.0 beta 3: guard upgrades, bought per shop and kept in {@link SecurityLedger} (they stay with the shop: a new guard
 * hired after the old one fell gets the same gear). Four stats, levels 0-3:
 *
 *  · ARMOR      none / leather uniform / iron / diamond (worn and shown on the guard).
 *  · WEAPON     fists / stone sword / iron sword / diamond sword (attack damage 3 / 7 / 8 / 9).
 *  · TRAINING   max health 40 / 55 / 70 / 90 and faster recovery.
 *  · DETECTION  chase radius 14 / 20 / 26 / 32 blocks, better odds of spotting skilled thieves when they steal,
 *               recognizes known thieves at the door (level 2+) and masked robbers before they strike (level 3).
 *
 * Balance (see ThiefTiers): an unupgraded guard detains pickpockets but loses to an armed robber; level 2 gear is an even
 * fight against a heist boss; full gear wins it. Bought from the phone (StaffPayload role "guard_up_<stat>", routed by
 * ShopServer.manageStaff; "guard_up_menu" shows the menu below) or from the menu the guard shows when its owner sneak + right-clicks it (clickable chat lines
 * that run /tcgguard upgrade <zone> <stat>).
 */
public final class GuardUpgrades {
    public enum Stat { ARMOR, WEAPON, TRAINING, DETECTION }

    public static final int MAX_LEVEL = 3;
    /** Base price of each level (level 1, 2, 3) per stat, before EconomyConfig.guardUpgradeCostMult. */
    private static final long[][] COST = {
            {6_000, 15_000, 40_000},   // armor
            {5_000, 12_000, 35_000},   // weapon
            {4_000, 10_000, 25_000},   // training
            {4_000, 10_000, 25_000}};  // detection
    private static final double[] HEALTH = {40, 55, 70, 90};
    private static final int[] RADIUS = {14, 20, 26, 32};
    /** Navy blue of the guard uniform (leather armor, level 1). */
    private static final int UNIFORM = 0x263248;

    private GuardUpgrades() {}

    public static void register() {
        ServerEvents.COMMANDS.register((d, access, env) -> d.register(Commands.literal("tcgguard")
                .then(Commands.literal("upgrade")
                        .then(Commands.argument("zone", StringArgumentType.word())
                                .then(Commands.argument("stat", StringArgumentType.word()).executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    UUID zone;
                                    try {
                                        zone = UUID.fromString(StringArgumentType.getString(ctx, "zone"));
                                    } catch (IllegalArgumentException e) {
                                        return 0;
                                    }
                                    buy(p, zone, StringArgumentType.getString(ctx, "stat"));
                                    return 1;
                                }))))
                .then(Commands.literal("menu")
                        .then(Commands.argument("zone", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            try {
                                ShopZones.Zone z = ShopZones.get(p.serverLevel()).byId(UUID.fromString(StringArgumentType.getString(ctx, "zone")));
                                if (z != null && z.owner.equals(p.getUUID())) showMenu(p, z);
                            } catch (IllegalArgumentException ignored) {
                            }
                            return 1;
                        })))));
    }

    // ------------------------------------------------------------------ levels

    public static int[] levels(net.minecraft.server.MinecraftServer server, UUID zoneId) {
        if (server == null || zoneId == null) return new int[4];
        return SecurityLedger.zone(server, zoneId).guard;
    }

    public static int level(net.minecraft.server.MinecraftServer server, UUID zoneId, Stat s) {
        return levels(server, zoneId)[s.ordinal()];
    }

    /** Chase / spotting radius (blocks) of that shop's guard. */
    public static int radius(net.minecraft.server.MinecraftServer server, UUID zoneId) {
        return RADIUS[clampLevel(level(server, zoneId, Stat.DETECTION))];
    }

    /** Sum of all levels (0-12). */
    public static int total(int[] lv) {
        int t = 0;
        for (int v : lv) t += v;
        return t;
    }

    /**
     * Chance that the guard notices a theft the moment it happens, by thief tier (0 = pickpocket ... 4 = heist boss).
     * Pickpockets are always seen (as before 1.0); armed robbers and heists are loud, so they are always seen too.
     */
    public static double spotChance(int[] lv, int tier) {
        int d = lv[Stat.DETECTION.ordinal()];
        return switch (tier) {
            case 1 -> 0.80 + 0.06 * d;
            case 2 -> 0.55 + 0.12 * d;
            default -> 1.0;
        };
    }

    /**
     * When a guard grabs a skilled thief (tier 1-2): chance that the thief wriggles free once more. Weapon and detection
     * levels make it smaller. Pickpockets never slip.
     */
    public static double slipChance(int[] lv, int tier) {
        if (tier <= 0) return 0;
        double base = tier == 1 ? 0.30 : 0.50;
        return Math.max(0.03, base - 0.06 * (lv[Stat.WEAPON.ordinal()] + lv[Stat.DETECTION.ordinal()]));
    }

    public static long cost(Stat s, int nextLevel) {
        if (nextLevel < 1 || nextLevel > MAX_LEVEL) return -1;
        double mult = Math.max(0, Math.min(100, EconomyConfig.INSTANCE.guardUpgradeCostMult));
        return Math.max(0, Math.round(COST[s.ordinal()][nextLevel - 1] * mult));
    }

    @org.jetbrains.annotations.Nullable
    public static Stat parse(String key) {
        if (key == null) return null;
        try {
            return Stat.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return switch (key.trim().toLowerCase(Locale.ROOT)) {
                case "health", "hp" -> Stat.TRAINING;
                case "radius", "radio" -> Stat.DETECTION;
                default -> null;
            };
        }
    }

    private static int clampLevel(int l) {
        return Math.max(0, Math.min(MAX_LEVEL, l));
    }

    // ------------------------------------------------------------------ buying

    /** Buys the next level of `statKey` for that shop's guard (owner only, with a guard hired). */
    public static void buy(ServerPlayer player, UUID zoneId, String statKey) {
        ServerLevel world = player.serverLevel();
        ShopZones zones = ShopZones.get(world);
        ShopZones.Zone z = zones.byId(zoneId);
        if (z == null || !z.owner.equals(player.getUUID())) return;
        // "guard_up_menu" from the phone: just shows the upgrade menu in chat
        if ("menu".equalsIgnoreCase(statKey == null ? "" : statKey.trim())) {
            if (z.guard) showMenu(player, z);
            else player.displayClientMessage(Component.translatable("tcgshop.guardup.no_guard").withStyle(ChatFormatting.RED), false);
            return;
        }
        Stat s = parse(statKey);
        if (s == null) return;
        if (!z.guard) {
            player.displayClientMessage(Component.translatable("tcgshop.guardup.no_guard").withStyle(ChatFormatting.RED), false);
            return;
        }
        int[] lv = levels(world.getServer(), z.id);
        int cur = lv[s.ordinal()];
        if (cur >= MAX_LEVEL) {
            player.displayClientMessage(Component.translatable("tcgshop.guardup.maxed", statName(s)).withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        // like hiring, no upgrades while a Wholesaler loan is active
        if (dev.tcgshop.phone.Loans.blocksPurchase(player, "tcgshop.loan.notice.blocked_staff")) return;
        long price = cost(s, cur + 1);
        if (!CobbleDollarsBridge.tryCharge(player, price)) {
            player.displayClientMessage(Component.translatable("tcgshop.shop.no_money", PriceService.format(price)).withStyle(ChatFormatting.RED), false);
            return;
        }
        zones.addCost(player.getUUID(), price);
        lv[s.ordinal()] = cur + 1;
        SecurityLedger.dirty(world.getServer());
        zones.setDirty();
        for (EmployeeEntity e : world.getEntitiesOfClass(EmployeeEntity.class, z.box().inflate(64), e -> e.isGuard() && z.id.equals(e.zoneId()))) {
            apply(e, lv);
            e.upgradedLine(s);
        }
        world.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
        player.displayClientMessage(Component.translatable("tcgshop.guardup.bought", statName(s), cur + 1, MAX_LEVEL, z.guardName,
                Component.literal(PriceService.format(price)).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN), false);
        if (total(lv) >= MAX_LEVEL * 4) CardAdvancements.grant(player, "guard_maxed");
        else CardAdvancements.grant(player, "guard_upgrade");
        ShopServer.syncZones(world.getServer());
    }

    public static Component statName(Stat s) {
        return Component.translatable("tcgshop.guardup.stat." + s.name().toLowerCase(Locale.ROOT));
    }

    /** Clickable chat menu with the guard's gear and the next level's price (the owner sneak + right-clicks the guard). */
    public static void showMenu(ServerPlayer player, ShopZones.Zone z) {
        int[] lv = levels(player.getServer(), z.id);
        player.displayClientMessage(Component.translatable("tcgshop.guardup.menu_title", z.guardName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (Stat s : Stat.values()) {
            int l = lv[s.ordinal()];
            MutableComponent line = Component.literal(" ").append(statName(s).copy().withStyle(ChatFormatting.WHITE)).append(" ")
                    .append(Component.literal("★".repeat(l)).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("☆".repeat(MAX_LEVEL - l)).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("  "));
            line.append(Component.translatable("tcgshop.guardup.effect." + s.name().toLowerCase(Locale.ROOT) + "." + l).withStyle(ChatFormatting.GRAY));
            if (l < MAX_LEVEL) {
                long price = cost(s, l + 1);
                String cmd = "/tcgguard upgrade " + z.id + " " + s.name().toLowerCase(Locale.ROOT);
                line.append(Component.literal("  "))
                        .append(Component.translatable("tcgshop.guardup.button", PriceService.format(price)).setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                                .withBold(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable("tcgshop.guardup.effect." + s.name().toLowerCase(Locale.ROOT) + "." + (l + 1))))));
            } else line.append(Component.literal("  ")).append(Component.translatable("tcgshop.guardup.max").withStyle(ChatFormatting.AQUA));
            player.displayClientMessage(line, false);
        }
        SecurityLedger.ZoneSec sec = SecurityLedger.zone(player.getServer(), z.id);
        player.displayClientMessage(Component.translatable("tcgshop.guardup.record", z.detained, sec.robberiesFoiled + sec.heistsFoiled,
                sec.robberiesLost + sec.heistsLost).withStyle(ChatFormatting.DARK_GRAY), false);
    }

    // ------------------------------------------------------------------ applying to the entity

    /** Puts the shop's gear on a guard: armor, weapon, max health (keeping the same health fraction). */
    public static void apply(EmployeeEntity e, int[] lv) {
        int a = clampLevel(lv[Stat.ARMOR.ordinal()]), w = clampLevel(lv[Stat.WEAPON.ordinal()]), t = clampLevel(lv[Stat.TRAINING.ordinal()]);
        Item[][] armor = {
                {null, null, null, null},
                {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
                {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
                {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            ItemStack st = armor[a][i] == null ? ItemStack.EMPTY : new ItemStack(armor[a][i]);
            if (a == 1) st.set(DataComponents.DYED_COLOR, new DyedItemColor(UNIFORM, false));
            e.setItemSlot(slots[i], st);
            e.setDropChance(slots[i], 0f);
        }
        Item[] weapons = {null, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD};
        e.setItemSlot(EquipmentSlot.MAINHAND, weapons[w] == null ? ItemStack.EMPTY : new ItemStack(weapons[w]));
        e.setDropChance(EquipmentSlot.MAINHAND, 0f);
        AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getBaseValue() != HEALTH[t]) {
            float frac = e.getMaxHealth() <= 0 ? 1 : e.getHealth() / e.getMaxHealth();
            hp.setBaseValue(HEALTH[t]);
            e.setHealth(Math.max(1, (float) (HEALTH[t] * frac)));
        }
    }

    /** Health the guard recovers every 2 seconds out of combat (training makes it faster). */
    public static float regen(int[] lv) {
        return 0.5f + 0.5f * clampLevel(lv[Stat.TRAINING.ordinal()]);
    }
}
