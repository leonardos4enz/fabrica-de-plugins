package dev.leo.worldcraft.nature;

import dev.leo.worldcraft.config.Issues;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Los árboles del juego (spec 033 H5 CA1) con los nombres de WorldEdit y sus alias, y el {@code TreeType} de Paper que
 * los genera. {@link #RANDOM} elige uno de la superficie para cada árbol. Java puro.
 */
public enum TreeKind {
    OAK("TREE", "oak", "tree", "regular"), BIG_OAK("BIG_TREE", "big_oak", "big", "bigtree", "fancy_oak"),
    SPRUCE("REDWOOD", "spruce", "redwood", "sw"), TALL_SPRUCE("TALL_REDWOOD", "tall_spruce", "tallredwood", "tallspruce"),
    MEGA_SPRUCE("MEGA_REDWOOD", "mega_spruce", "megaredwood", "megaspruce"), MEGA_PINE("MEGA_PINE", "mega_pine", "pine", "megapine"),
    BIRCH("BIRCH", "birch"), TALL_BIRCH("TALL_BIRCH", "tall_birch", "tallbirch"),
    JUNGLE("JUNGLE", "jungle", "bigjungle", "big_jungle"), SMALL_JUNGLE("SMALL_JUNGLE", "small_jungle", "smalljungle", "shortjungle"),
    JUNGLE_BUSH("JUNGLE_BUSH", "jungle_bush", "junglebush"), COCOA("COCOA_TREE", "cocoa", "cocoa_tree"),
    SWAMP("SWAMP", "swamp", "swamptree"), ACACIA("ACACIA", "acacia"), DARK_OAK("DARK_OAK", "dark_oak", "darkoak"),
    CHERRY("CHERRY", "cherry"), AZALEA("AZALEA", "azalea"), MANGROVE("MANGROVE", "mangrove"),
    TALL_MANGROVE("TALL_MANGROVE", "tall_mangrove", "tallmangrove"), PALE_OAK("PALE_OAK", "pale_oak", "paleoak"),
    PALE_OAK_CREAKING("PALE_OAK_CREAKING", "pale_oak_creaking"),
    RED_MUSHROOM("RED_MUSHROOM", "red_mushroom", "redmushroom"), BROWN_MUSHROOM("BROWN_MUSHROOM", "brown_mushroom", "brownmushroom"),
    CRIMSON_FUNGUS("CRIMSON_FUNGUS", "crimson_fungus", "crimson"), WARPED_FUNGUS("WARPED_FUNGUS", "warped_fungus", "warped"),
    CHORUS("CHORUS_PLANT", "chorus", "chorus_plant"), RANDOM(null, "random");

    /** El nombre en {@code org.bukkit.TreeType}; null para random. */
    public final String paper;
    private final String[] names;

    /** Los que elige random: los de la superficie que crecen sobre pasto. */
    private static final TreeKind[] SURFACE = {OAK, BIG_OAK, SPRUCE, TALL_SPRUCE, BIRCH, TALL_BIRCH, SMALL_JUNGLE, ACACIA,
            DARK_OAK, CHERRY, SWAMP};

    TreeKind(String paper, String... names) {
        this.paper = paper;
        this.names = names;
    }

    public String id() {
        return names[0];
    }

    /** Lo que, además de nature.soil, sirve de suelo (los hongos del Nether en el nylium, el coro en la piedra del End). */
    public String extraSoil() {
        return switch (this) {
            case CRIMSON_FUNGUS, WARPED_FUNGUS -> "#nylium";
            case CHORUS -> "end_stone";
            default -> "";
        };
    }

    /** El árbol de verdad: random elige uno de la superficie. */
    public TreeKind pick(Random r) {
        return this == RANDOM ? SURFACE[r.nextInt(SURFACE.length)] : this;
    }

    public static TreeKind parse(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        for (TreeKind k : values()) if (Arrays.asList(k.names).contains(t)) return k;
        throw new IllegalArgumentException("Unknown tree '" + s + "'" + Issues.hint(t, ids()) + ". Trees: " + String.join(", ", ids()));
    }

    /** Los ids (sin alias), para el autocompletado y los errores. */
    public static List<String> ids() {
        return Arrays.stream(values()).map(TreeKind::id).toList();
    }
}
