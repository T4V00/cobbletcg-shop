package dev.tcgshop.entity;

import dev.tcgshop.collection.Titles;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Identidad de los NPC del mod: nombre de persona real, piel propia pintada por código y, con cierta probabilidad,
 * un título de Pokémon ("Fan de Pikachu", "Obsesionado con Gengar"...).
 */
public final class NpcIdentity {
    public static final String[] NAMES = {
            "Lucía", "Mateo", "Sofía", "Santiago", "Valentina", "Sebastián", "Isabella", "Matías", "Camila", "Nicolás",
            "Martina", "Alejandro", "Daniela", "Diego", "Mariana", "Samuel", "Gabriela", "Tomás", "Victoria", "Joaquín",
            "Paula", "Andrés", "Emilia", "Felipe", "Renata", "Gabriel", "Antonella", "Emiliano", "Regina", "Benjamín",
            "Ximena", "Julián", "Luciana", "David", "Valeria", "Juan Pablo", "Sara", "Carlos", "Natalia", "Javier",
            "Andrea", "Miguel", "Carolina", "Pedro", "Laura", "Ricardo", "Elena", "Fernando", "Claudia", "Raúl",
            "Patricia", "Hugo", "Adriana", "Iván", "Mónica", "Óscar", "Verónica", "Rodrigo", "Lorena", "Esteban",
            "Pilar", "Gonzalo", "Rocío", "Álvaro", "Nerea", "Marcos", "Irene", "Rubén", "Alba", "Sergio",
            "Olivia", "Liam", "Emma", "Noah", "Ava", "Ethan", "Mia", "Lucas", "Chloe", "Mason",
            "Harper", "Logan", "Ella", "Jacob", "Grace", "Aiden", "Lily", "Owen", "Zoe", "Caleb",
            "Hannah", "Ryan", "Nora", "Dylan", "Ruby", "Leo", "Stella", "Isaac", "Hazel", "Elias",
            "Yuki", "Haruto", "Sakura", "Ren", "Aiko", "Kenji", "Hana", "Takumi", "Mei", "Sora",
            "Ji-woo", "Min-jun", "Seo-yeon", "Ha-eun", "Chen", "Li Wei", "Mei Lin", "Arjun", "Priya", "Ravi",
            "Ananya", "Omar", "Layla", "Yusuf", "Amira", "Karim", "Fatima", "Kwame", "Amara", "Kofi",
            "Zainab", "Thiago", "Bruna", "Rafael", "Larissa", "João", "Beatriz", "Luca", "Giulia", "Marco",
            "Chiara", "Pierre", "Camille", "Louis", "Manon", "Hans", "Lena", "Felix", "Ingrid", "Sven",
            "Astrid", "Mikhail", "Anya", "Dmitri", "Katya", "Kai", "Zuri", "Noor", "Efe", "Makena"
    };

    /** Variantes de piel propias (se pintan en el cliente según el rol: client/render/NpcSkins). */
    public static final int SKIN_COUNT = 48;

    private NpcIdentity() {}

    public static String randomName(Random r) {
        return NAMES[r.nextInt(NAMES.length)];
    }

    public static int randomSkin(Random r) {
        return r.nextInt(SKIN_COUNT);
    }

    /** Con probabilidad `chance`, un título "especie|tier" de un Pokémon con cartas; si no, "". */
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
