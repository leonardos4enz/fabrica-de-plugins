package dev.leo.skinstatues.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Genera src/main/resources/block-colors.yml (plan 021 §1.2) desde el jar del cliente de Minecraft instalado del dueño:
 * el color promedio de la textura lateral de cada bloque. Al repositorio solo van los números, nunca las texturas.
 * Uso: ./gradlew :skinstatues:blockColors -Pmc="%APPDATA%\.minecraft\versions\1.21.4\1.21.4.jar"
 */
public final class BlockColorsMain {
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "pale_oak", "bamboo", "crimson", "warped"};

    /**
     * Bloques sólidos, sin gravedad ni redstone, de un color parejo: id → textura (la del costado). Sin glowstone ni
     * sea_lantern: Paper no los cuenta como opacos y avisaban en cada arranque (spec 021, F-02).
     */
    static Map<String, String> blocks() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String c : COLORS) m.put(c + "_wool", c + "_wool");
        for (String c : COLORS) m.put(c + "_concrete", c + "_concrete");
        m.put("terracotta", "terracotta");
        for (String c : COLORS) m.put(c + "_terracotta", c + "_terracotta");
        for (String w : WOODS) m.put(w + "_planks", w + "_planks");
        for (String s : new String[]{"stone", "cobblestone", "mossy_cobblestone", "smooth_stone", "stone_bricks",
                "mossy_stone_bricks", "cracked_stone_bricks", "granite", "polished_granite", "diorite", "polished_diorite",
                "andesite", "polished_andesite", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks",
                "deepslate_tiles", "tuff", "polished_tuff", "tuff_bricks", "calcite", "dripstone_block",
                "polished_blackstone", "polished_blackstone_bricks", "smooth_basalt", "bricks", "mud_bricks", "packed_mud",
                "cut_sandstone", "cut_red_sandstone", "prismarine_bricks", "dark_prismarine", "purpur_block", "end_stone",
                "end_stone_bricks", "netherrack", "nether_bricks", "red_nether_bricks", "nether_wart_block",
                "warped_wart_block", "quartz_bricks", "obsidian", "amethyst_block", "shroomlight", "clay",
                "honeycomb_block", "moss_block", "pale_moss_block", "packed_ice", "blue_ice", "iron_block", "gold_block",
                "diamond_block", "emerald_block", "lapis_block", "coal_block", "netherite_block", "raw_iron_block",
                "raw_copper_block", "raw_gold_block", "resin_block", "resin_bricks", "red_mushroom_block",
                "brown_mushroom_block", "mushroom_stem", "prismarine"}) {
            m.put(s, s);
        }
        m.put("deepslate", "deepslate");
        m.put("blackstone", "blackstone");
        m.put("sandstone", "sandstone");
        m.put("smooth_sandstone", "sandstone_top");
        m.put("red_sandstone", "red_sandstone");
        m.put("smooth_red_sandstone", "red_sandstone_top");
        m.put("quartz_block", "quartz_block_side");
        m.put("smooth_quartz", "quartz_block_bottom");
        m.put("snow_block", "snow");
        m.put("bone_block", "bone_block_side");
        m.put("hay_block", "hay_block_side");
        m.put("dried_kelp_block", "dried_kelp_side");
        m.put("pumpkin", "pumpkin_side");
        m.put("melon", "melon_side");
        // El cobre encerado no se oxida: el color no cambia con el tiempo.
        m.put("waxed_copper_block", "copper_block");
        m.put("waxed_exposed_copper", "exposed_copper");
        m.put("waxed_weathered_copper", "weathered_copper");
        m.put("waxed_oxidized_copper", "oxidized_copper");
        return m;
    }

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]), out = Path.of(args[1]);
        List<String> lines = new ArrayList<>();
        lines.add("# SkinStatues - the color of each block, used to match skin pixels (H6).");
        lines.add("# Measured as the average of each block's side texture in Minecraft " + version(jar) + ".");
        lines.add("# Edit a color to change how often a block is picked; remove a line to never use that block.");
        lines.add("# Regenerate with: ./gradlew :skinstatues:blockColors -Pmc=<path to the client .jar>");
        int missing = 0;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (Map.Entry<String, String> e : blocks().entrySet()) {
                ZipEntry z = zip.getEntry("assets/minecraft/textures/block/" + e.getValue() + ".png");
                if (z == null) {
                    System.err.println("no texture: " + e.getValue());
                    missing++;
                    continue;
                }
                try (InputStream in = zip.getInputStream(z)) {
                    lines.add(e.getKey() + ": \"" + hex(average(ImageIO.read(in))) + "\"");
                }
            }
        }
        Files.write(out, (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        System.out.println("block-colors.yml: " + (lines.size() - 4) + " blocks" + (missing > 0 ? ", " + missing + " missing" : ""));
        if (missing > 0) System.exit(1);
    }

    /** Promedio pesado por el alfa; si la textura es animada (alta), solo el primer cuadro. */
    static int average(BufferedImage img) {
        int w = img.getWidth(), h = Math.min(img.getHeight(), w);
        long r = 0, g = 0, b = 0, a = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = img.getRGB(x, y), al = c >>> 24;
                r += (long) ((c >> 16) & 0xFF) * al;
                g += (long) ((c >> 8) & 0xFF) * al;
                b += (long) (c & 0xFF) * al;
                a += al;
            }
        }
        if (a == 0) return 0;
        return (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static String version(Path jar) {
        String n = jar.getFileName().toString();
        return n.endsWith(".jar") ? n.substring(0, n.length() - 4) : n;
    }
}
