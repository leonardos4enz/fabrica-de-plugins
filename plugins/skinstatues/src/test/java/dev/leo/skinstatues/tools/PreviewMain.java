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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Vista previa sin servidor (herramienta de desarrollo): cada pose de poses.yml con la skin de ejemplo, vista de frente,
 * del costado derecho, de espaldas y en una isométrica simple, con los colores de los bloques elegidos de la paleta.
 * Uso: java … PreviewMain <carpeta de recursos> <salida.png> [alto]
 */
public final class PreviewMain {
    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 48;
        Issues issues = new Issues();
        BlockCatalog cat = BlockCatalog.load(YamlConfiguration.loadConfiguration(new File(res, "block-colors.yml")),
                "block-colors.yml", id -> null, issues);
        BlockPalette pal = cat.palettes(YamlConfiguration.loadConfiguration(new File(res, "palettes.yml")), "palettes.yml", issues).get("all");
        Map<String, Pose> poses = PoseLoader.load(YamlConfiguration.loadConfiguration(new File(res, "poses.yml")), "poses.yml", issues);
        issues.all().forEach(System.out::println);
        Skin skin = Skin.of(ImageIO.read(new File(res, "skins/Example.png")), false, ImageIO.read(new File(res, "skins/Example-cape.png")));

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
