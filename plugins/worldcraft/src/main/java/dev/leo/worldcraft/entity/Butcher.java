package dev.leo.worldcraft.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Qué quita {@code /wc butcher} (spec 031 H4, como {@code /butcher} de WorldEdit): de fábrica las criaturas hostiles; lo
 * demás con su opción. Nunca jugadores. El orden importa: un aldeano con nombre es un npc; un lobo domesticado es mascota
 * antes que animal; un monstruo con nombre se salva sin {@code --named}.
 */
public final class Butcher {
    public enum Flag { PETS, NPCS, GOLEMS, ANIMALS, AMBIENT, WATER, NAMED, ARMORSTANDS, FRIENDLY }

    /** Los nombres de las opciones (--pets…), en el orden de la ayuda. */
    public static final List<String> FLAGS = List.of("pets", "npcs", "golems", "animals", "ambient", "water", "named",
            "armorstands", "friendly");

    private final Set<Flag> flags;

    public Butcher(Set<Flag> flags) {
        this.flags = flags.isEmpty() ? EnumSet.noneOf(Flag.class) : EnumSet.copyOf(flags);
    }

    /** De las opciones escritas (sin los guiones); las que no son de butcher se ignoran. */
    public static Butcher of(Set<String> given) {
        Set<Flag> f = EnumSet.noneOf(Flag.class);
        for (String g : given) {
            if (FLAGS.contains(g)) f.add(Flag.valueOf(g.toUpperCase(Locale.ROOT)));
        }
        return new Butcher(f);
    }

    private boolean has(Flag f) {
        return flags.contains(f);
    }

    /** --friendly es pets, npcs, golems, animals, ambient y named (como -f de WorldEdit), más las criaturas que no son hostiles. */
    private boolean friendly(Flag f) {
        return has(f) || has(Flag.FRIENDLY);
    }

    public boolean kills(Set<Kind> k) {
        if (k.contains(Kind.PLAYER)) return false;
        if (k.contains(Kind.ARMOR_STAND)) return has(Flag.ARMORSTANDS);
        if (!k.contains(Kind.LIVING)) return false;
        if (k.contains(Kind.NPC)) return friendly(Flag.NPCS);
        if (k.contains(Kind.NAMED) && !friendly(Flag.NAMED)) return false;
        if (k.contains(Kind.PET)) return friendly(Flag.PETS);
        if (k.contains(Kind.MONSTER)) return true;
        if (k.contains(Kind.GOLEM)) return friendly(Flag.GOLEMS);
        if (k.contains(Kind.WATER)) return has(Flag.WATER);
        if (k.contains(Kind.AMBIENT)) return friendly(Flag.AMBIENT);
        if (k.contains(Kind.ANIMAL)) return friendly(Flag.ANIMALS);
        return has(Flag.FRIENDLY);
    }
}
