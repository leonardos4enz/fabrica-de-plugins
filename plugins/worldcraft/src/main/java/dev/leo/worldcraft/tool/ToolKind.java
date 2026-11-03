package dev.leo.worldcraft.tool;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Los pinceles de construcción y las herramientas de un clic (spec 032), con los nombres y alias de WorldEdit y FAWE.
 * Spec 033: el pincel de bosque y la herramienta de árbol. Spec 034: el pincel de deformar.
 */
public enum ToolKind {
    SPHERE(true, "sphere"), CYL(true, "cyl", "cylinder"), SET(true, "set"), CLIPBOARD(true, "clipboard"),
    SPLATTER(true, "splatter"), SCATTER(true, "scatter"), ROCK(true, "rock", "blob"), GRAVITY(true, "gravity", "grav"),
    EXTINGUISH(true, "extinguish", "ex"), BUTCHER(true, "butcher", "kill"), FOREST(true, "forest"), DEFORM(true, "deform"),
    // Spec 040: los pinceles de WorldEdit que faltaban (paint … item es APPLY: /wc brush paint sin item es del terreno).
    SNOW(true, "snow"), SNOWSMOOTH(true, "snowsmooth"), BIOME(true, "biome"), MORPH(true, "morph"), DILATE(true, "dilate"),
    HEIGHTMAP(true, "heightmap"), FEATURE(true, "feature"), APPLY(true, "apply"),
    REPL(false, "repl"), FLOODFILL(false, "floodfill", "flood"), INFO(false, "info"), CYCLER(false, "cycler"),
    DELTREE(false, "deltree"), FARWAND(false, "farwand"), LRBUILD(false, "lrbuild"), NAVWAND(false, "navwand"), TREE(false, "tree"),
    STACKER(false, "stacker");

    public final boolean brush;
    private final String[] names;

    ToolKind(boolean brush, String... names) {
        this.brush = brush;
        this.names = names;
    }

    public String id() {
        return names[0];
    }

    /** ¿Tiene radio? (para el círculo de partículas y radius=) */
    public boolean hasRadius() {
        return brush && this != CLIPBOARD;
    }

    /** ¿Pone bloques de un patrón? (para mask=) */
    public boolean placesBlocks() {
        return brush && this != BUTCHER && this != GRAVITY && this != EXTINGUISH && this != FOREST && this != BIOME && this != FEATURE;
    }

    public static ToolKind parse(String s, boolean brush) {
        String t = s.toLowerCase(Locale.ROOT);
        for (ToolKind k : values()) if (k.brush == brush && Arrays.asList(k.names).contains(t)) return k;
        return null;
    }

    /** Los ids (sin alias) de los pinceles o de las herramientas. */
    public static List<String> ids(boolean brush) {
        return Arrays.stream(values()).filter(k -> k.brush == brush).map(ToolKind::id).toList();
    }
}
