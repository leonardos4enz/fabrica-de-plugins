package dev.leo.skinstatues.tools;

import dev.leo.skinstatues.color.BlockPalette;
import dev.leo.skinstatues.config.BlockCatalog;
import dev.leo.skinstatues.config.Issues;
import dev.leo.skinstatues.config.PoseLoader;
import dev.leo.skinstatues.model.Pose;
import dev.leo.skinstatues.model.VoxelGrid;
import dev.leo.skinstatues.model.Voxelizer;
import dev.leo.skinstatues.skin.Skin;
import org.bukkit.configuration.file.YamlConfiguration;

import dev.leo.skinstatues.config.LoadedConfig;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vista previa sin servidor (herramienta de desarrollo): cada pose de poses.yml con la skin de ejemplo, vista de frente,
 * del costado derecho, de espaldas y en una isométrica simple, con los colores de los bloques elegidos de la paleta.
 * Lee las poses incorporadas y poses.yml como el servidor (spec 022). Con una lista de poses dibuja solo esas (para
 * ajustarlas); con grid=true, la hoja de todas en isométrica con su nombre (la imagen del README).
 * Uso: java … PreviewMain <carpeta de recursos> <salida.png> [alto] [pose,pose…|""] [grid]
 */
public final class PreviewMain {
    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 48;
        Issues issues = new Issues();
        BlockCatalog cat = BlockCatalog.load(YamlConfiguration.loadConfiguration(new File(res, "block-colors.yml")),
                "block-colors.yml", id -> null, issues);
        BlockPalette pal = cat.palettes(YamlConfiguration.loadConfiguration(new File(res, "palettes.yml")), "palettes.yml", issues).get("all");
        PoseLoader.Result loaded = PoseLoader.load(
                YamlConfiguration.loadConfiguration(new File(res, LoadedConfig.BUILTIN_POSES)), LoadedConfig.BUILTIN_POSES,
                YamlConfiguration.loadConfiguration(new File(res, "poses.yml")), "poses.yml", issues);
        Map<String, Pose> poses = new LinkedHashMap<>(loaded.poses());
        if (args.length > 3 && !args[3].isBlank()) poses.keySet().retainAll(List.of(args[3].split(",")));
        boolean grid = args.length > 4 && Boolean.parseBoolean(args[4]);
        // La hoja del README muestra las incorporadas, no los ejemplos de poses.yml.
        if (grid) poses.keySet().removeIf(n -> loaded.origins().get(n) != PoseLoader.Origin.BUILTIN);
        issues.all().forEach(System.out::println);
        Skin skin = Skin.of(ImageIO.read(new File(res, "skins/Example.png")), false, ImageIO.read(new File(res, "skins/Example-cape.png")));

        if (grid) {
            sheet(poses, skin, pal, height, new File(args[1]));
            return;
        }
        int cell = 4, view = height + 8, cols = 4;
        BufferedImage out = new BufferedImage(cols * view * cell, poses.size() * view * cell, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < out.getWidth(); i++) for (int j = 0; j < out.getHeight(); j++) out.setRGB(i, j, 0x87CEEB);
        int row = 0;
        for (Pose p : poses.values()) {
            VoxelGrid g = Voxelizer.run(skin, p, new Voxelizer.Options(height, 0, true, 0.5, 128, true));
            System.out.println(p.name() + ": " + g.size() + " blocks, height " + g.height());
            Map<Long, Integer> m = new HashMap<>();
            for (int k = 0; k < g.size(); k++) m.put(VoxelGrid.key(g.x(k), g.y(k), g.z(k)), pal.entries().get(pal.nearest(g.color(k))).rgb());
            int half = view / 2;
            for (int y = 0; y < view; y++) {
                for (int a = -half; a < half; a++) {
                    // Frente (desde +z), costado derecho (desde −x), espalda (desde −z).
                    paint(out, 0, row, view, cell, a + half, y, first(m, a, y, 0, 0, -1, half));
                    paint(out, 1, row, view, cell, a + half, y, first(m, -half, y, a, 1, 0, half));
                    paint(out, 2, row, view, cell, a + half, y, first(m, -a, y, -half, 0, 1, half));
                }
            }
            iso(out, 3, row, view, cell, m, g, half);
            row++;
        }
        ImageIO.write(out, "png", new File(args[1]));
    }

    /** El primer bloque a lo largo de un rayo (x, y, z) + t·(dx, 0, dz). Para el frente el rayo empieza en +z. */
    private static int first(Map<Long, Integer> m, int x, int y, int z, int dx, int dz, int half) {
        int sx = x, sz = dz == -1 ? half : z;
        for (int t = 0; t < 2 * half; t++) {
            Integer c = m.get(VoxelGrid.key(sx + dx * t, y, sz + dz * t));
            if (c != null) {
                int shade = 255 - Math.min(120, t * 2);
                return mul(c, shade);
            }
        }
        return -1;
    }

    private static void iso(BufferedImage out, int col, int row, int view, int cell, Map<Long, Integer> m, VoxelGrid g, int half) {
        // Proyección isométrica simple desde el frente-izquierda: pinta de atrás hacia adelante.
        int ox = col * view * cell + view * cell / 2, oy = (row + 1) * view * cell - 2 * cell;
        int s = Math.max(1, cell / 2);
        for (int y = 0; y < g.height(); y++) {
            for (int z = -half; z < half; z++) {
                for (int x = half; x >= -half; x--) {
                    Integer c = m.get(VoxelGrid.key(x, y, z));
                    if (c == null) continue;
                    int px = ox + (-x + z) * s, py = oy - y * 2 * s + (x + z) * s / 2;
                    for (int i = 0; i < 2 * s; i++) {
                        for (int j = 0; j < 2 * s; j++) {
                            int X = px + i, Y = py + j;
                            if (X >= col * view * cell && X < (col + 1) * view * cell && Y >= row * view * cell && Y < (row + 1) * view * cell) {
                                out.setRGB(X, Y, j < s / 2 ? mul(c, 255) : mul(c, i < s ? 200 : 160));
                            }
                        }
                    }
                }
            }
        }
    }

    /** Hoja de todas las poses: isométrica de cada una con su nombre debajo, en filas de 6. */
    private static void sheet(Map<String, Pose> poses, Skin skin, BlockPalette pal, int height, File out) throws Exception {
        int cols = 6, view = height + 8, cell = 4, label = 40;
        int rows = (poses.size() + cols - 1) / cols;
        int cw = view * cell, ch = view * cell + label;
        BufferedImage img = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0x87CEEB));
        g2.fillRect(0, 0, img.getWidth(), img.getHeight());
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        int i = 0;
        for (Pose p : poses.values()) {
            VoxelGrid g = Voxelizer.run(skin, p, new Voxelizer.Options(height, 0, true, 0.5, 128, true));
            Map<Long, Integer> m = new HashMap<>();
            for (int k = 0; k < g.size(); k++) m.put(VoxelGrid.key(g.x(k), g.y(k), g.z(k)), pal.entries().get(pal.nearest(g.color(k))).rgb());
            BufferedImage one = new BufferedImage(cw, cw, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < cw; x++) for (int y = 0; y < cw; y++) one.setRGB(x, y, 0x87CEEB);
            iso(one, 0, 0, view, cell, m, g, view / 2);
            int ox = (i % cols) * cw, oy = (i / cols) * ch;
            g2.drawImage(one, ox, oy, null);
            g2.setColor(new Color(0x1E2A38));
            int w = g2.getFontMetrics().stringWidth(p.name());
            g2.drawString(p.name(), ox + (cw - w) / 2, oy + cw + label - 12);
            i++;
        }
        g2.dispose();
        ImageIO.write(img, "png", out);
    }

    private static void paint(BufferedImage out, int col, int row, int view, int cell, int a, int y, int rgb) {
        if (rgb < 0) return;
        int x0 = (col * view + a) * cell, y0 = ((row + 1) * view - 1 - y - 2) * cell;
        for (int i = 0; i < cell; i++) for (int j = 0; j < cell; j++) out.setRGB(x0 + i, y0 + j, rgb);
    }

    private static int mul(int rgb, int k) {
        int r = ((rgb >> 16) & 0xFF) * k / 255, g = ((rgb >> 8) & 0xFF) * k / 255, b = (rgb & 0xFF) * k / 255;
        return r << 16 | g << 8 | b;
    }
}
