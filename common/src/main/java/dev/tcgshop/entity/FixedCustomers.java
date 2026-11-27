package dev.tcgshop.entity;

import dev.tcgshop.EconomyConfig;
import dev.tcgshop.shop.SecurityLedger;
import dev.tcgshop.shop.ShopZones;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * 1.0 beta 3: the 10 unique customers. Each one always has the same name, skin ("fixed_<id>", stored as
 * NpcIdentity.fixedSkin(skinId) and resolved by client/render/NpcSkins) and personality, its own lines (tcgshop.fixed.<id>.<cat>.<n>,
 * tools/gen_lang_10c_customers.py) and a name drawn in animated rainbow colors (client/render/CustomerNpcRenderer).
 * They remember the players whose shops they visited (SecurityLedger) and greet them by name when they come back.
 *
 *  0 Don Aurelio     the excellent customer: a wealthy gentleman who buys the priciest cards, several of them, and tips big.
 *  1 Vinnie Vex      the known thief: a skilled (tier 3) thief; a guard with detection 2+ may recognize him at the door.
 *  2 Nana Rosa       sweet grandmother buying for her grandchildren; always in a good mood, small tips.
 *  3 Timmy Turbo     hyperactive kid who loves packs.
 *  4 Dr. Margarita   haughty card expert: picky, lectures you about prices.
 *  5 Gus Grumble     grumpy, smelly and never happy: picky and starts in a bad mood.
 *  6 Chad Flipson    reseller: haggles with insulting low offers.
 *  7 Luna Star       friendly streamer who narrates her visit to her followers.
 *  8 Barnaby Brine   old sailor who trades cards from his "travels".
 *  9 Madame Noir     cold, mysterious seller of rare cards at stiff prices.
 *
 * Spawn: EconomyConfig.uniqueCustomerChance per natural arrival picks one of them (weights below: the excellent customer
 * and the thief are the rarest), never one already walking around, and never the same one twice on the same day for the
 * same owner.
 */
public final class FixedCustomers {
    public static final int COUNT = 10, EXCELLENT = 0, THIEF = 1;

    /** name (same in every language), behavior type, spawn weight, starting mood bonus, smelly, nasty. */
    public record Def(String name, CustomerEntity.Type type, int weight, int mood, boolean smelly, boolean nasty) {}

    public static final Def[] DEFS = {
            new Def("Don Aurelio", CustomerEntity.Type.WHALE, 3, 1, false, false),
            new Def("Vinnie Vex", CustomerEntity.Type.THIEF, 3, 0, false, true),
            new Def("Nana Rosa", CustomerEntity.Type.REGULAR, 10, 1, false, false),
            new Def("Timmy Turbo", CustomerEntity.Type.REGULAR, 10, 1, false, false),
            new Def("Dr. Margarita", CustomerEntity.Type.PICKY, 10, 0, false, true),
            new Def("Gus Grumble", CustomerEntity.Type.PICKY, 10, -1, true, true),
            new Def("Chad Flipson", CustomerEntity.Type.HAGGLER, 10, 0, false, true),
            new Def("Luna Star", CustomerEntity.Type.REGULAR, 10, 1, false, false),
            new Def("Barnaby Brine", CustomerEntity.Type.TRADER, 10, 0, false, false),
            new Def("Madame Noir", CustomerEntity.Type.SELLER, 10, 0, false, true)};

    /** Lines every one of them has (category -> how many). "back" takes the player's name (%s). */
    public static final Map<String, Integer> COMMON = Map.of("hello", 4, "back", 3, "buy", 3, "nobuy", 3, "talk", 4,
            "bye_happy", 3, "bye_sad", 3);
    /** Extra categories (3 lines each) only some of them have. */
    private static final Map<Integer, Set<String>> SPECIAL = Map.of(
            0, Set.of("tip"),
            1, Set.of("steal", "caught", "escape", "spotted"),
            2, Set.of("tip"),
            4, Set.of("complain"),
            5, Set.of("complain"),
            6, Set.of("deal"),
            8, Set.of("deal"),
            9, Set.of("deal"));
    public static final int SPECIAL_LINES = 3;
    /** Lines of the guard that recognizes Vinnie at the door (tcgshop.fixed.guard_spot.<n>). */
    public static final int GUARD_SPOT_LINES = 4;

    private FixedCustomers() {}

    public static boolean valid(int id) {
        return id >= 0 && id < COUNT;
    }

    public static Def def(int id) {
        return DEFS[Math.max(0, Math.min(COUNT - 1, id))];
    }

    /** The skin id the npcskins renderer resolves ("fixed_<id>"). */
    public static String skinId(int id) {
        return "fixed_" + id;
    }

    public static boolean has(int id, String cat) {
        if (!valid(id)) return false;
        if (COMMON.containsKey(cat)) return true;
        Set<String> s = SPECIAL.get(id);
        return s != null && s.contains(cat);
    }

    /** A random line of that customer and category (args fill %s). */
    public static MutableComponent line(RandomSource r, int id, String cat, Object... args) {
        int n = COMMON.getOrDefault(cat, SPECIAL_LINES);
        return Component.translatable("tcgshop.fixed." + id + "." + cat + "." + r.nextInt(Math.max(1, n)), args);
    }

    /**
     * Rolls whether a naturally arriving customer is one of the 10. Returns the id, or -1 for a normal customer.
     * Skips the ones already alive in this world and the ones that already came today to this owner.
     */
    public static int roll(ServerLevel world, ShopZones.Zone z) {
        double chance = Math.max(0, Math.min(0.5, EconomyConfig.INSTANCE.uniqueCustomerChance));
        if (chance <= 0 || world.random.nextDouble() >= chance) return -1;
        long today = world.getDayTime() / 24000;
        int[] w = new int[COUNT];
        int sum = 0;
        for (int i = 0; i < COUNT; i++) {
            if (SecurityLedger.lastDay(world.getServer(), i, z.owner) == today || alive(world, i)) continue;
            // the known thief doesn't try a shop with no stock worth taking, nor a brand-new stall
            if (i == THIEF && (z.displays == 0 || z.tierInfo().rank() < 1)) continue;
            w[i] = DEFS[i].weight();
            sum += w[i];
        }
        if (sum <= 0) return -1;
        int r = world.random.nextInt(sum);
        for (int i = 0; i < COUNT; i++) if ((r -= w[i]) < 0) return i;
        return -1;
    }

    /** The name with a static rainbow (for chat; the name tag above the head is animated on the client). */
    public static MutableComponent rainbow(String s) {
        MutableComponent out = Component.empty();
        int i = 0;
        for (int cp : s.codePoints().toArray()) {
            int rgb = net.minecraft.util.Mth.hsvToRgb((i * 0.08f) % 1f, 0.7f, 1f);
            out.append(Component.literal(new String(Character.toChars(cp))).withStyle(st -> st.withColor(rgb).withBold(true)));
            i++;
        }
        return out;
    }

    /** Is unique customer `id` already walking around in this world? */
    public static boolean alive(ServerLevel world, int id) {
        return !world.getEntities(TcgEntities.CUSTOMER, c -> c.isAlive() && c.fixedId() == id).isEmpty();
    }
}
