package dev.tcgshop.entity;

import dev.tcgshop.collection.Titles;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Identity of the mod's NPCs: a real person's first name, a skin and, with some probability, a Pokémon title
 * ("Pikachu Fan", "Obsessed with Gengar"...).
 *
 * <h2>Skin int (the "NpcSkin" NBT value / SKIN tracked data)</h2>
 * The skin is still a single int, so saves, regulars and the data trackers keep working unchanged:
 * <ul>
 *   <li>bits 0-11: variant (picks one of the role's skin textures, modulo how many exist);</li>
 *   <li>bits 12-13: sex: 0 = follow the NPC's name (old saves and {@link #randomSkin(Random)}), 1 = female, 2 = male;</li>
 *   <li>bit 16: fixed skin flag; then bits 0-7 are the fixed skin id ({@code fixed_0} .. {@code fixed_9}).</li>
 * </ul>
 * Old NPCs (values 0..63, no sex bits) get their sex from their name ({@link #sexOfName}); names we don't know get a
 * deterministic one from the name's hash, so the same NPC always looks the same.
 *
 * <h2>API for other systems</h2>
 * <ul>
 *   <li>{@link #roll(Random)}: coherent name + skin + sex for a new NPC.</li>
 *   <li>{@link #randomName(Random, Sex)}, {@link #randomSkin(Random, Sex)}: per-sex picks.</li>
 *   <li>{@link #fixedSkin(int)} / {@link #fixedSkin(String)}: skin int of one of the 10 fixed unique customers
 *       ({@code "fixed_3"} or {@code 3}); {@link #FIXED_SEX} and {@link #FIXED_NAMES} hold their sex and name.</li>
 *   <li>Also accepted by the renderer: role key {@code tcgshop.role.fixed} with the raw id 0..9 as skin int
 *       (what CustomerEntity.applyFixed stores).</li>
 *   <li>{@link #sexOf(int, String)}: works on the server too (e.g. gendered Spanish lines).</li>
 * </ul>
 */
public final class NpcIdentity {
    public enum Sex { FEMALE, MALE }

    public static final String[] FEMALE_NAMES = {
            // Spanish / Latin American
            "Lucía", "Sofía", "Valentina", "Isabella", "Camila", "Martina", "Daniela", "Mariana", "Gabriela", "Victoria",
            "Paula", "Emilia", "Renata", "Antonella", "Regina", "Ximena", "Luciana", "Valeria", "Sara", "Natalia",
            "Andrea", "Carolina", "Laura", "Elena", "Claudia", "Patricia", "Adriana", "Mónica", "Verónica", "Lorena",
            "Pilar", "Rocío", "Nerea", "Irene", "Alba", "María", "Carmen", "Ana", "Marta", "Julia",
            "Noa", "Carla", "Inés", "Lola", "Rosa", "Teresa", "Silvia", "Cristina", "Alicia", "Eva",
            "Nuria", "Raquel", "Sandra", "Fernanda", "Guadalupe", "Paola", "Jimena", "Florencia", "Abril", "Agustina",
            // English
            "Olivia", "Emma", "Ava", "Mia", "Chloe", "Harper", "Ella", "Grace", "Lily", "Zoe",
            "Hannah", "Nora", "Ruby", "Stella", "Hazel", "Sophia", "Amelia", "Charlotte", "Evelyn", "Abigail",
            "Emily", "Madison", "Scarlett", "Aria", "Luna", "Ellie", "Avery", "Mila", "Riley", "Penelope",
            "Lucy", "Anna", "Alice", "Maya", "Sarah", "Jessica", "Jennifer", "Ashley", "Megan", "Rachel",
            "Amy", "Kate", "Molly", "Ivy", "Rose", "Clara", "Eleanor", "Audrey", "Naomi", "Jasmine",
            // the rest of the world
            "Yuki", "Sakura", "Aiko", "Hana", "Mei", "Sora", "Ayumi", "Yui", "Rin", "Mio",
            "Ji-woo", "Seo-yeon", "Ha-eun", "Mei Lin", "Xiu", "Lan", "Linh", "Mai", "Priya", "Ananya",
            "Aisha", "Layla", "Amira", "Fatima", "Zainab", "Leila", "Yasmin", "Sana", "Mariam", "Nadia",
            "Noor", "Elif", "Zeynep", "Amara", "Zuri", "Makena", "Nia", "Imani", "Ama", "Chiamaka",
            "Bruna", "Larissa", "Beatriz", "Giulia", "Chiara", "Francesca", "Alessia", "Camille", "Manon", "Léa",
            "Juliette", "Lena", "Ingrid", "Astrid", "Freya", "Anya", "Katya", "Olga", "Natasha", "Svetlana"
    };

    public static final String[] MALE_NAMES = {
            // Spanish / Latin American
            "Mateo", "Santiago", "Sebastián", "Matías", "Nicolás", "Alejandro", "Diego", "Samuel", "Tomás", "Joaquín",
            "Andrés", "Felipe", "Gabriel", "Emiliano", "Benjamín", "Julián", "David", "Juan Pablo", "Carlos", "Javier",
            "Miguel", "Pedro", "Ricardo", "Fernando", "Raúl", "Hugo", "Iván", "Óscar", "Rodrigo", "Esteban",
            "Gonzalo", "Álvaro", "Marcos", "Rubén", "Sergio", "José", "Manuel", "Antonio", "Francisco", "Juan",
            "Luis", "Pablo", "Daniel", "Adrián", "Jorge", "Alberto", "Jaime", "Víctor", "Mario", "Martín",
            "Bruno", "Iker", "Unai", "Enzo", "Lautaro", "Ignacio", "Emilio", "Agustín", "Rafa", "Leonardo",
            // English
            "Liam", "Noah", "Ethan", "Lucas", "Mason", "Logan", "Jacob", "Aiden", "Owen", "Caleb",
            "Ryan", "Dylan", "Leo", "Isaac", "Elias", "James", "John", "Michael", "William", "Henry",
            "Alexander", "Jack", "Oliver", "Matthew", "Joseph", "Luke", "Nathan", "Adam", "Tyler", "Kevin",
            "Brandon", "Justin", "Eric", "Sam", "Max", "Charlie", "Harry", "George", "Theo", "Finn",
            // the rest of the world
            "Haruto", "Ren", "Kenji", "Takumi", "Hiroshi", "Yuto", "Sota", "Jun", "Min-jun", "Chen",
            "Li Wei", "Wei", "Hao", "Minh", "Duc", "Arjun", "Ravi", "Rohan", "Vikram", "Omar",
            "Yusuf", "Karim", "Ahmed", "Ali", "Hassan", "Ibrahim", "Mehmet", "Emre", "Kwame", "Kofi",
            "Tunde", "Chidi", "Jabari", "Malik", "Efe", "Kai", "Thiago", "Rafael", "João", "Luca",
            "Marco", "Matteo", "Giovanni", "Lorenzo", "Pierre", "Louis", "Hans", "Felix", "Sven", "Lars",
            "Erik", "Mikhail", "Dmitri", "Nikolai", "Alexei", "Jakub", "Piotr", "Pavel", "Viktor", "Anton"
    };

    /** All names (female then male). Kept for code that only needs "a name". */
    public static final String[] NAMES;

    /** Variant space of {@link #randomSkin(Random)}: each role has far fewer textures, the renderer takes it modulo. */
    public static final int SKIN_COUNT = 4096;

    // ---- skin int layout (see class javadoc)
    private static final int VARIANT_MASK = 0xFFF;
    private static final int SEX_SHIFT = 12;
    private static final int SEX_MASK = 0x3 << SEX_SHIFT;
    private static final int FIXED_FLAG = 1 << 16;

    /** Fixed skin ids are FIXED_PREFIX + index ("fixed_0" .. "fixed_9"); textures/entity/npc/fixed_N.png. */
    public static final String FIXED_PREFIX = "fixed_";
    public static final int FIXED_COUNT = 10;

    /**
     * Sex of each fixed skin (drives the slim/wide model). Ids match entity/FixedCustomers; designs in
     * tools/gen_npc_skins.py:
     * 0 Don Aurelio, the whale (navy pinstripe suit, gold tie and chain, shades), 1 Vinnie Vex, the known thief (black
     * hood up, striped mask, gloves, loot backpack), 2 Nana Rosa (white bun, round glasses, lavender cardigan, pearls),
     * 3 Timmy Turbo (backwards red cap, yellow tee, shorts, backpack), 4 Dr. Margarita (platinum bob, gold reading
     * glasses on a chain, magenta blazer), 5 Gus Grumble (bald, unibrow, stained tank top, holey cardigan, stink lines),
     * 6 Chad Flipson (backwards cap, shades, orange puffer vest, gold chain), 7 Luna Star (pink twin tails, star shades,
     * headset mic, white crop jacket), 8 Barnaby Brine (captain's cap, white beard, navy peacoat with gold buttons),
     * 9 Madame Noir (black hat with a veil, purple streak, lace dress, choker).
     */
    public static final Sex[] FIXED_SEX = {
            Sex.MALE, Sex.MALE, Sex.FEMALE, Sex.MALE, Sex.FEMALE, Sex.MALE, Sex.MALE, Sex.FEMALE, Sex.MALE, Sex.FEMALE};

    /** Names of the fixed customers (same as entity/FixedCustomers.DEFS). */
    public static final String[] FIXED_NAMES = {
            "Don Aurelio", "Vinnie Vex", "Nana Rosa", "Timmy Turbo", "Dr. Margarita", "Gus Grumble", "Chad Flipson",
            "Luna Star", "Barnaby Brine", "Madame Noir"};

    private static final Map<String, Sex> SEX_BY_NAME = new HashMap<>();

    static {
        NAMES = new String[FEMALE_NAMES.length + MALE_NAMES.length];
        System.arraycopy(FEMALE_NAMES, 0, NAMES, 0, FEMALE_NAMES.length);
        System.arraycopy(MALE_NAMES, 0, NAMES, FEMALE_NAMES.length, MALE_NAMES.length);
        for (String n : FEMALE_NAMES) SEX_BY_NAME.put(n.toLowerCase(Locale.ROOT), Sex.FEMALE);
        for (String n : MALE_NAMES) SEX_BY_NAME.putIfAbsent(n.toLowerCase(Locale.ROOT), Sex.MALE);
        for (int i = 0; i < FIXED_COUNT; i++) SEX_BY_NAME.putIfAbsent(FIXED_NAMES[i].toLowerCase(Locale.ROOT), FIXED_SEX[i]);
    }

    private NpcIdentity() {}

    /** Coherent identity for a new NPC: the skin carries the sex explicitly, matching the name. */
    public record Rolled(String name, int skin, Sex sex) {}

    public static Rolled roll(Random r) {
        Sex s = randomSex(r);
        return new Rolled(randomName(r, s), randomSkin(r, s), s);
    }

    public static Sex randomSex(Random r) {
        return r.nextBoolean() ? Sex.FEMALE : Sex.MALE;
    }

    /** A name of a random sex. Skins from {@link #randomSkin(Random)} follow the name, so they always match. */
    public static String randomName(Random r) {
        return randomName(r, randomSex(r));
    }

    public static String randomName(Random r, Sex sex) {
        String[] pool = sex == Sex.FEMALE ? FEMALE_NAMES : MALE_NAMES;
        return pool[r.nextInt(pool.length)];
    }

    /** Random variant whose sex follows the NPC's name (used by every NPC type that sets its name separately). */
    public static int randomSkin(Random r) {
        return r.nextInt(SKIN_COUNT);
    }

    /** Random variant with an explicit sex (stays the same even if the NPC is renamed). */
    public static int randomSkin(Random r, Sex sex) {
        return skinOf(sex, r.nextInt(SKIN_COUNT));
    }

    public static int skinOf(@Nullable Sex sex, int variant) {
        int s = sex == null ? 0 : sex == Sex.FEMALE ? 1 : 2;
        return (variant & VARIANT_MASK) | (s << SEX_SHIFT);
    }

    // ---- fixed skins

    /** Skin int of the fixed skin {@code fixed_<index>} (index 0..9, wrapped). */
    public static int fixedSkin(int index) {
        return FIXED_FLAG | Math.floorMod(index, FIXED_COUNT);
    }

    /** Skin int from a skin id such as "fixed_3" (or just "3"); -1 if the id is not a fixed skin. */
    public static int fixedSkin(String skinId) {
        if (skinId == null) return -1;
        String s = skinId.startsWith(FIXED_PREFIX) ? skinId.substring(FIXED_PREFIX.length()) : skinId;
        try {
            int i = Integer.parseInt(s.trim());
            return i >= 0 && i < FIXED_COUNT ? fixedSkin(i) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean isFixedSkin(int skin) {
        return skin >= 0 && (skin & FIXED_FLAG) != 0;
    }

    /** Index 0..9 of a fixed skin int (only meaningful when {@link #isFixedSkin}). */
    public static int fixedIndex(int skin) {
        return Math.floorMod(skin & 0xFF, FIXED_COUNT);
    }

    /** "fixed_3" for a fixed skin int, otherwise null. */
    @Nullable
    public static String fixedSkinId(int skin) {
        return isFixedSkin(skin) ? FIXED_PREFIX + fixedIndex(skin) : null;
    }

    // ---- reading a skin int

    /** Variant bits (0..4095). Negative legacy values are wrapped. */
    public static int skinVariant(int skin) {
        return skin < 0 ? Math.floorMod(skin, SKIN_COUNT) : skin & VARIANT_MASK;
    }

    /** Sex stored in the skin int, or null when it follows the name (old saves, {@link #randomSkin(Random)}). */
    @Nullable
    public static Sex explicitSex(int skin) {
        if (skin < 0) return null;
        if (isFixedSkin(skin)) return FIXED_SEX[fixedIndex(skin)];
        int s = (skin & SEX_MASK) >> SEX_SHIFT;
        return s == 1 ? Sex.FEMALE : s == 2 ? Sex.MALE : null;
    }

    /** Sex of a known first name (full name, then its first word), or null if we don't know it. */
    @Nullable
    public static Sex sexOfName(@Nullable String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return null;
        Sex s = SEX_BY_NAME.get(n);
        if (s != null) return s;
        int sp = n.indexOf(' ');
        return sp > 0 ? SEX_BY_NAME.get(n.substring(0, sp)) : null;
    }

    /**
     * Final sex of an NPC: explicit bits / fixed skin, else its name, else a deterministic pick (name hash, or the
     * variant when there is no name). Same inputs always give the same answer on server and client.
     */
    public static Sex sexOf(int skin, @Nullable String name) {
        Sex s = explicitSex(skin);
        if (s != null) return s;
        s = sexOfName(name);
        if (s != null) return s;
        int h = name != null && !name.isBlank() ? name.trim().toLowerCase(Locale.ROOT).hashCode() : skinVariant(skin) * 0x9E3779B1;
        return ((h >>> 7) & 1) == 0 ? Sex.FEMALE : Sex.MALE;
    }

    /** With probability `chance`, a "species|tier" title of a Pokémon that has cards; otherwise "". */
    public static String randomTitle(Random r, float chance) {
        if (r.nextFloat() >= chance) return "";
        List<String> species = new ArrayList<>(Titles.allSpecies());
        if (species.isEmpty()) return "";
        String sp = species.get(r.nextInt(species.size()));
        float f = r.nextFloat();
        Titles.Tier t = f < 0.5f ? Titles.Tier.FAN : f < 0.8f ? Titles.Tier.ENTHUSIAST : f < 0.95f ? Titles.Tier.OBSESSED : Titles.Tier.DEMENTED;
        return sp + "|" + t.key;
    }
}
