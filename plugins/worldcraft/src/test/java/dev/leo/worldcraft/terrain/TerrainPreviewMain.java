package dev.leo.worldcraft.terrain;

import dev.leo.worldcraft.edit.EditJob;
import dev.leo.worldcraft.region.Box;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vista sin servidor de montañas e islas (herramienta de desarrollo, tarea T11): corre las obras en el mundo en memoria
 * por el motor y dibuja cada una en perspectiva oblicua con colores aproximados de los bloques.
 * <pre>./gradlew :worldcraft:terrainPreview -Pout=terreno.png -Pwhat="mountain medium;island medium;mountain volcano"</pre>
 */
public final class TerrainPreviewMain {
    private static final Map<String, Color> COLORS = Map.ofEntries(
            Map.entry("grass_block", new Color(96, 156, 64)), Map.entry("dirt", new Color(134, 96, 67)),
            Map.entry("stone", new Color(125, 125, 125)), Map.entry("andesite", new Color(136, 136, 137)),
            Map.entry("tuff", new Color(108, 109, 102)), Map.entry("gravel", new Color(131, 127, 126)),
            Map.entry("snow_block", new Color(240, 250, 250)), Map.entry("sand", new Color(219, 207, 163)),
            Map.entry("red_sand", new Color(190, 102, 33)), Map.entry("sandstone", new Color(216, 203, 155)),
            Map.entry("red_sandstone", new Color(181, 97, 31)), Map.entry("terracotta", new Color(152, 94, 67)),
            Map.entry("magma_block", new Color(142, 63, 31)), Map.entry("blackstone", new Color(42, 36, 41)),
            Map.entry("basalt", new Color(80, 81, 86)), Map.entry("packed_ice", new Color(141, 180, 250)),
            Map.entry("end_stone", new Color(219, 222, 158)), Map.entry("end_stone_bricks", new Color(218, 224, 162)),
            Map.entry("oak_log", new Color(109, 85, 50)), Map.entry("spruce_log", new Color(58, 37, 16)),
            Map.entry("oak_leaves", new Color(60, 120, 30)), Map.entry("spruce_leaves", new Color(40, 80, 50)),
            Map.entry("hanging_roots", new Color(161, 115, 91)));

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/terrain.png";
        String what = args.length > 1 && !args[1].isBlank() ? args[1] : "mountain medium;mountain large seed=7;mountain volcano;island medium;island large;mountain hills";
        List<BufferedImage> imgs = new ArrayList<>();
        for (String spec : what.split(";")) imgs.add(render(spec.trim()));
        int w = imgs.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int h = imgs.stream().mapToInt(BufferedImage::getHeight).sum();
        BufferedImage all = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = all.createGraphics();
        g.setColor(new Color(150, 190, 230));
        g.fillRect(0, 0, w, h);
        int y = 0;
        for (BufferedImage i : imgs) {
            g.drawImage(i, 0, y, null);
            y += i.getHeight();
        }
        g.dispose();
        File f = new File(out);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        ImageIO.write(all, "png", f);
        System.out.println("Wrote " + f.getAbsolutePath());
    }

    static BufferedImage render(String spec) {
        TerrainLab lab = new TerrainLab();
        String[] words = spec.split(" ");
        String tool = words[0];
        String[] rest = java.util.Arrays.copyOfRange(words, 1, words.length);
        Box box;
        if (tool.equals("island")) {
            Options.Resolved o = lab.options(Options.Tool.ISLAND, rest);
            IslandShape.Plan plan = TerrainLab.island(o).plan(0, 80, 0, -64, 319);
            lab.run(lab.islandSource(plan, o, false), EditJob.Mode.APPLY, null, null);
            box = new Box(plan.rect().minX(), plan.minY() - 1, plan.rect().minZ(), plan.rect().maxX(), plan.maxY() + 8, plan.rect().maxZ());
        } else {
            TerrainLab.Mountain m = lab.planMountain(0, 0, -1, rest);
            lab.run(lab.mountainSource(m, false), EditJob.Mode.APPLY, null, null);
            int r = m.options().integer("radius");
            box = new Box(-r, -2, -r, r, m.plan().peak() + 1, r);
        }
        int s = Math.max(2, 600 / box.width());
        int W = (box.width() + box.depth() / 2 + 2) * s, H = (box.height() + box.depth() / 2 + 2) * s;
        BufferedImage img = new BufferedImage(W, H + 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(150, 190, 230));
        g.fillRect(0, 0, W, H + 16);
        // De atrás (z menor) hacia adelante, de abajo hacia arriba: lo de adelante tapa.
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    int st = lab.world.get(x, y, z);
                    String name = lab.table.material(st).replace("minecraft:", "");
                    if (name.endsWith("air")) continue;
                    Color c = COLORS.getOrDefault(name, Color.MAGENTA);
                    boolean topOpen = lab.world.get(x, y + 1, z) == lab.world.air;
                    boolean frontOpen = lab.world.get(x, y, z + 1) == lab.world.air;
                    if (!topOpen && !frontOpen) continue;
                    int dz = box.maxZ() - z;
                    int px = (x - box.minX() + (box.depth() - dz) / 2) * s;
                    int py = (box.maxY() - y + (box.depth() - (z - box.minZ())) / 2) * s;
                    py = (box.maxY() - y) * s + (z - box.minZ()) * s / 2;
                    g.setColor(topOpen ? c : c.darker());
                    g.fillRect(px, py, s, s + (s + 1) / 2);
                }
            }
        }
        g.setColor(Color.BLACK);
        g.drawString(spec, 4, H + 12);
        g.dispose();
        return img;
    }
}
