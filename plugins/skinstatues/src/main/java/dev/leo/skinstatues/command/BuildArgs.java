package dev.leo.skinstatues.command;

import dev.leo.skinstatues.config.Issues;

import java.util.List;
import java.util.Locale;

/**
 * Lo que sigue a `/statue build <name> <height>` (plan 022 §2.1), sin Bukkit para probarlo en unitarias:
 * `[pose] [--palette <p>] [--no-overlay] [--no-cape] [--at <x> <y> <z>] [--facing <dir>] [--world <w>]`.
 * Un valor que falta o no se entiende devuelve el error con la clave del mensaje y sus valores.
 */
public record BuildArgs(String pose, String palette, boolean overlay, boolean cape, Coord[] at, Integer facing, String world) {
    public static final List<String> FLAGS = List.of("--palette", "--no-overlay", "--no-cape", "--at", "--facing", "--world");
    public static final List<String> FACINGS = List.of("north", "south", "east", "west");
    /** Más allá del borde máximo de un mundo de Minecraft no hay nada que construir. */
    public static final double MAX_COORD = 30_000_000;

    /** Una coordenada absoluta o relativa (`~`, `~5`) a la posición de quien construye. */
    public record Coord(boolean relative, double value) {
        public int resolve(double origin) {
            return (int) Math.floor(relative ? origin + value : value);
        }
    }

    public record Error(String key, Object... args) {
    }

    /** O los argumentos, o el error. */
    public record Parsed(BuildArgs args, Error error) {
    }

    public boolean hasAt() {
        return at != null;
    }

    public boolean relative() {
        return at != null && (at[0].relative() || at[1].relative() || at[2].relative());
    }

    /**
     * @param overlay valor por defecto de la segunda capa
     * @param cape    valor por defecto de la capa
     */
    public static Parsed parse(String[] args, int from, boolean overlay, boolean cape) {
        String pose = null, palette = null, world = null;
        Coord[] at = null;
        Integer facing = null;
        for (int i = from; i < args.length; i++) {
            String a = args[i].toLowerCase(Locale.ROOT);
            switch (a) {
                case "--no-overlay" -> overlay = false;
                case "--no-cape" -> cape = false;
                case "--palette", "--world", "--facing" -> {
                    if (i + 1 >= args.length) return fail("usage-build");
                    String v = args[++i];
                    if (a.equals("--palette")) palette = v.toLowerCase(Locale.ROOT);
                    else if (a.equals("--world")) world = v;
                    else {
                        facing = facing(v);
                        if (facing == null) return fail("invalid-facing", "facing", v, "hint", Issues.hint(v, FACINGS));
                    }
                }
                case "--at" -> {
                    if (i + 3 >= args.length) return fail("missing-coords");
                    at = new Coord[3];
                    for (int k = 0; k < 3; k++) {
                        String v = args[++i];
                        at[k] = coord(v);
                        if (at[k] == null) return fail("invalid-coord", "value", v);
                    }
                }
                default -> {
                    if (a.startsWith("--")) return fail("unknown-flag", "flag", args[i], "hint", Issues.hint(a, FLAGS));
                    if (pose != null) return fail("usage-build");
                    pose = a;
                }
            }
        }
        return new Parsed(new BuildArgs(pose, palette, overlay, cape, at, facing, world), null);
    }

    /** `123`, `-4.5`, `~`, `~5`, `~-2.5`; null si no es una coordenada. */
    static Coord coord(String s) {
        boolean rel = s.startsWith("~");
        String num = rel ? s.substring(1) : s;
        if (rel && num.isEmpty()) return new Coord(true, 0);
        double v;
        try {
            v = Double.parseDouble(num);
        } catch (NumberFormatException e) {
            return null;
        }
        // parseDouble acepta "NaN", "Infinity" y sufijos como "5d": solo números escritos como tales.
        if (!num.matches("[-+]?(\\d+\\.?\\d*|\\.\\d+)") || Math.abs(v) > MAX_COORD) return null;
        return new Coord(rel, v);
    }

    /** El giro de la estatua (0 = sur, como el yaw de Minecraft) para un nombre de dirección. */
    static Integer facing(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "south" -> 0;
            case "west" -> 90;
            case "north" -> 180;
            case "east" -> 270;
            default -> null;
        };
    }

    private static Parsed fail(String key, Object... args) {
        return new Parsed(null, new Error(key, args));
    }
}
