package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.pattern.ParseException;
import dev.leo.worldcraft.region.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Spec 041 H3 CA3: dónde caen los bloques de una obra (//gtransform, /transform), como las transformaciones de FAWE:
 * {@code #offset[x][y][z]}, {@code #rotate[grados]}, {@code #flip[x|y|z]}, {@code #spread[n]}, {@code #scale[n]};
 * varias, separadas por espacios, en orden. También {@code #offset:1,2,3}. Java puro.
 */
public final class Transforms {
    public static final List<String> NAMES = List.of("offset", "rotate", "flip", "spread", "scale");
    public static final int MAX_SPREAD = 16, MAX_SCALE = 4;

    /** Un paso: el nombre y sus números (flip guarda el eje como 0, 1 o 2). */
    record Step(String name, int[] args) {
        String text() {
            return switch (name) {
                case "offset" -> "#offset[" + args[0] + "][" + args[1] + "][" + args[2] + "]";
                case "flip" -> "#flip[" + "xyz".charAt(args[0]) + "]";
                default -> "#" + name + "[" + args[0] + "]";
            };
        }
    }

    private final List<Step> steps;

    private Transforms(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    List<Step> steps() {
        return steps;
    }

    static Transforms single(Step s) {
        return new Transforms(List.of(s));
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Step s : steps) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(s.text());
        }
        return sb.toString();
    }

    /** Cuántos bloques pone por cada uno de la obra (scale n: n³). */
    public long factor() {
        long f = 1;
        for (Step s : steps) if (s.name.equals("scale")) f *= (long) s.args[0] * s.args[0] * s.args[0];
        return f;
    }

    public static Transforms parse(String text) throws ParseException {
        List<Step> out = new ArrayList<>();
        for (String w : text.trim().split("\\s+|&&")) {
            if (w.isBlank()) continue;
            out.add(step(w.trim()));
        }
        if (out.isEmpty()) throw new ParseException("Write a transform, like '#offset[0][5][0]', '#rotate[90]', '#flip[x]', '#spread[3]' or '#scale[2]'");
        return new Transforms(out);
    }

    private static Step step(String w) throws ParseException {
        if (!w.startsWith("#")) throw new ParseException("'" + w + "' is not a transform: they start with #, like '#rotate[90]'");
        String body = w.substring(1).toLowerCase(Locale.ROOT);
        String name;
        List<String> args = new ArrayList<>();
        int open = body.indexOf('['), colon = body.indexOf(':');
        if (open >= 0) {
            name = body.substring(0, open);
            String rest = body.substring(open);
            while (!rest.isEmpty()) {
                int close = rest.indexOf(']');
                if (!rest.startsWith("[") || close < 0) throw new ParseException("'" + w + "': write each number in [ ], like '#offset[0][5][0]'");
                args.add(rest.substring(1, close).trim());
                rest = rest.substring(close + 1);
            }
        } else if (colon >= 0) {
            name = body.substring(0, colon);
            for (String a : body.substring(colon + 1).split(",")) args.add(a.trim());
        } else {
            name = body;
        }
        if (!NAMES.contains(name)) {
            String hint = Issues.closest(name, NAMES);
            throw new ParseException("Unknown transform '#" + name + "'" + (hint == null ? "" : " (did you mean '#" + hint + "'?)")
                    + ". Transforms: #" + String.join(", #", NAMES));
        }
        try {
            return switch (name) {
                case "offset" -> {
                    if (args.size() != 3) throw new ParseException("'#offset' takes three numbers: '#offset[x][y][z]'");
                    yield new Step(name, new int[]{Integer.parseInt(args.get(0)), Integer.parseInt(args.get(1)), Integer.parseInt(args.get(2))});
                }
                case "rotate" -> {
                    int deg = one(name, args);
                    if (deg % 90 != 0) throw new ParseException("'#rotate' turns in steps of 90 degrees (90, 180, 270, -90)");
                    yield new Step(name, new int[]{Math.floorMod(deg, 360)});
                }
                case "flip" -> {
                    if (args.size() != 1 || !List.of("x", "y", "z").contains(args.get(0))) throw new ParseException("'#flip' takes an axis: '#flip[x]', '#flip[y]' or '#flip[z]'");
                    yield new Step(name, new int[]{"xyz".indexOf(args.get(0))});
                }
                case "spread" -> {
                    int n = one(name, args);
                    if (n < 0 || n > MAX_SPREAD) throw new ParseException("'#spread' goes from 0 to " + MAX_SPREAD);
                    yield new Step(name, new int[]{n});
                }
                default -> {
                    int n = one(name, args);
                    if (n < 1 || n > MAX_SCALE) throw new ParseException("'#scale' goes from 1 to " + MAX_SCALE);
                    yield new Step(name, new int[]{n});
                }
            };
        } catch (NumberFormatException e) {
            throw new ParseException("'" + w + "': the numbers must be whole numbers");
        }
    }

    private static int one(String name, List<String> args) throws ParseException {
        if (args.size() != 1) throw new ParseException("'#" + name + "' takes one number: '#" + name + "[2]'");
        return Integer.parseInt(args.get(0));
    }

    /**
     * La caja de una obra con esta caja después de transformarla (para los límites y los chunks): rotate y flip alrededor
     * del centro de la caja de antes de cada paso.
     */
    public Box bounds(Box b) {
        Box cur = b;
        for (Step s : steps) {
            switch (s.name) {
                case "offset" -> cur = new Box(cur.minX() + s.args[0], cur.minY() + s.args[1], cur.minZ() + s.args[2],
                        cur.maxX() + s.args[0], cur.maxY() + s.args[1], cur.maxZ() + s.args[2]);
                case "spread" -> cur = new Box(cur.minX() - s.args[0], cur.minY() - s.args[0], cur.minZ() - s.args[0],
                        cur.maxX() + s.args[0], cur.maxY() + s.args[0], cur.maxZ() + s.args[0]);
                case "flip" -> {
                    // El espejo deja la caja igual (se refleja dentro de sí misma).
                }
                case "rotate" -> {
                    int[] a = new int[3], c = new int[3];
                    rotate(s.args[0], cur, cur.minX(), cur.minY(), cur.minZ(), a);
                    rotate(s.args[0], cur, cur.maxX(), cur.maxY(), cur.maxZ(), c);
                    cur = Box.of(a[0], a[1], a[2], c[0], c[1], c[2]);
                }
                default -> {
                    int n = s.args[0];
                    int cx = Math.floorDiv(cur.minX() + cur.maxX(), 2), cy = Math.floorDiv(cur.minY() + cur.maxY(), 2), cz = Math.floorDiv(cur.minZ() + cur.maxZ(), 2);
                    cur = new Box(cx + (cur.minX() - cx) * n, cy + (cur.minY() - cy) * n, cz + (cur.minZ() - cz) * n,
                            cx + (cur.maxX() - cx) * n + n - 1, cy + (cur.maxY() - cy) * n + n - 1, cz + (cur.maxZ() - cz) * n + n - 1);
                }
            }
        }
        return cur;
    }

    /** Gira (x, y, z) deg grados en y alrededor del centro de la caja (como el reloj visto desde arriba, como Transform). */
    static void rotate(int deg, Box around, int x, int y, int z, int[] out) {
        int cx = Math.floorDiv(around.minX() + around.maxX(), 2), cz = Math.floorDiv(around.minZ() + around.maxZ(), 2);
        int dx = x - cx, dz = z - cz;
        for (int i = 0; i < deg / 90; i++) {
            int t = dx;
            dx = -dz;
            dz = t;
        }
        out[0] = cx + dx;
        out[1] = y;
        out[2] = cz + dz;
    }
}
