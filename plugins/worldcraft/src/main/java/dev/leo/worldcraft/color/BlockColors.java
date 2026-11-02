package dev.leo.worldcraft.color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La tabla de colores de bloques (spec 035 H1, plan §1.1): la de SkinStatues (block-colors.yml del jar) con los cambios
 * de config.yml. Elige el bloque más parecido a un color como lo ve el ojo, el más oscuro o más claro de uno, y mezcla
 * colores como FAWE. Java puro: se usa desde el hilo de las imágenes y desde los patrones.
 */
public final class BlockColors {
    public record Entry(String id, int rgb, Lab lab) {
    }

    /** Los 16 colores de los tintes, con el color de su lana (así #color[red] es la lana roja si está en la tabla). */
    public static final Map<String, Integer> NAMED;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("white", 0xE9ECEC);
        m.put("orange", 0xF07613);
        m.put("magenta", 0xBD44B3);
        m.put("light_blue", 0x3AAFD9);
        m.put("yellow", 0xF8C527);
        m.put("lime", 0x70B919);
        m.put("pink", 0xED8DAC);
        m.put("gray", 0x3E4447);
        m.put("light_gray", 0x8E8E86);
        m.put("cyan", 0x158991);
        m.put("purple", 0x792AAC);
        m.put("blue", 0x35399D);
        m.put("brown", 0x724728);
        m.put("green", 0x546D1B);
        m.put("red", 0xA02722);
        m.put("black", 0x141519);
        NAMED = java.util.Collections.unmodifiableMap(m);
    }

    private final List<Entry> entries;
    private final Map<String, Integer> index = new HashMap<>();
    private final ConcurrentHashMap<Integer, Integer> cache = new ConcurrentHashMap<>();

    /** table: id con espacio de nombres («minecraft:white_wool») → 0xRRGGBB, en el orden de la tabla. */
    public BlockColors(Map<String, Integer> table) {
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<String, Integer> e : table.entrySet()) {
            index.put(e.getKey(), list.size());
            list.add(new Entry(e.getKey(), e.getValue() & 0xFFFFFF, Lab.of(e.getValue())));
        }
        if (list.isEmpty()) throw new IllegalArgumentException("the color table is empty");
        this.entries = List.copyOf(list);
    }

    /** La tabla del jar (block-colors.yml: «white_wool: "#E9ECEC"»), con «minecraft:» delante. */
    public static Map<String, Integer> builtin() {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (InputStream in = BlockColors.class.getResourceAsStream("/block-colors.yml")) {
            if (in == null) throw new IllegalStateException("block-colors.yml is missing from the jar");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String id = line.substring(0, colon).trim();
                String v = line.substring(colon + 1).trim().replace("\"", "");
                out.put(id.contains(":") ? id : "minecraft:" + id, parse(v));
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read block-colors.yml: " + e.getMessage(), e);
        }
        return out;
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) out.add(e.id);
        return out;
    }

    public int size() {
        return entries.size();
    }

    /** El índice del bloque más parecido (se ignora el alfa). */
    public int nearest(int argb) {
        return cache.computeIfAbsent(argb & 0xFFFFFF, c -> {
            Lab lab = Lab.of(c);
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < entries.size(); i++) {
                double d = entries.get(i).lab.distance2(lab);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            return best;
        });
    }

    public String nearestId(int argb) {
        return entries.get(nearest(argb)).id;
    }

    /** El color de un bloque de la tabla («minecraft:stone»), o −1 si no está. */
    public int colorOf(String material) {
        Integer i = index.get(material);
        return i == null ? -1 : entries.get(i).rgb;
    }

    /** Un bloque de color parecido y más oscuro (H1 CA2), o null si no hay o el bloque no está en la tabla. */
    public String darker(String material) {
        return shade(material, -1);
    }

    public String lighter(String material) {
        return shade(material, 1);
    }

    /** El de menos diferencia de tono, con la luminosidad entre 3 y 25 hacia ese lado (mejor 10). */
    private String shade(String material, int sign) {
        Integer i = index.get(material);
        if (i == null) return null;
        Lab c = entries.get(i).lab;
        String best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entry e : entries) {
            if (e.id.equals(material)) continue;
            double dl = (e.lab.l() - c.l()) * sign;
            if (dl < 3 || dl > 25) continue;
            double da = e.lab.a() - c.a(), db = e.lab.b() - c.b();
            double score = da * da + db * db + 0.5 * (dl - 10) * (dl - 10);
            if (score < bestScore) {
                bestScore = score;
                best = e.id;
            }
        }
        return best;
    }

    /** #saturate de FAWE: los dos colores multiplicados canal por canal. */
    public static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255, g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255,
                bl = (a & 0xFF) * (b & 0xFF) / 255;
        return r << 16 | g << 8 | bl;
    }

    /** #averagecolor de FAWE: el promedio canal por canal. */
    public static int average(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2, g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2,
                bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return r << 16 | g << 8 | bl;
    }

    /** #desaturate de FAWE: cada canal se acerca ese % a la luminancia (100 = gris). */
    public static int desaturate(int rgb, double percent) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        double l = 0.3 * r + 0.59 * g + 0.11 * b, k = Math.max(0, Math.min(100, percent)) / 100;
        return clamp(r + k * (l - r)) << 16 | clamp(g + k * (l - g)) << 8 | clamp(b + k * (l - b));
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /**
     * Un color escrito por alguien: «#3a7bd5», «3a7bd5», «#fff» o uno de los 16 nombres de los tintes.
     *
     * @throws IllegalArgumentException con el motivo en inglés (y «did you mean» para un nombre)
     */
    public static int parse(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED.get(t);
        if (named != null) return named;
        String hex = t.startsWith("#") ? t.substring(1) : t;
        if (hex.matches("[0-9a-f]{3}")) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.matches("[0-9a-f]{6}")) return Integer.parseInt(hex, 16);
        String hint = closest(t, NAMED.keySet());
        throw new IllegalArgumentException("'" + text.trim() + "' is not a color: write #RRGGBB (like #3a7bd5) or a dye color (red, light_blue...)"
                + (hint == null ? "" : " (did you mean '" + hint + "'?)"));
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** «did you mean» sin depender de config (el mismo umbral que Issues.closest). */
    static String closest(String input, Collection<String> options) {
        return dev.leo.worldcraft.config.Issues.closest(input, options);
    }
}
