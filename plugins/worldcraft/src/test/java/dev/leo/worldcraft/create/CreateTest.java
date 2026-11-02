package dev.leo.worldcraft.create;

import dev.leo.worldcraft.color.BlockColors;
import dev.leo.worldcraft.edit.PointsSource;
import dev.leo.worldcraft.formula.Formula;
import dev.leo.worldcraft.formula.Shapes;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Cylinder;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 035 H2, H3 y H4: vetas, cuevas e imagen, en Java puro (sin servidor). */
class CreateTest {
    static final Shapes.Work FREE = new Shapes.Work(Long.MAX_VALUE);

    private static Set<Long> set(long[] pts) {
        Set<Long> s = new HashSet<>();
        for (long p : pts) s.add(p);
        return s;
    }

    /** Cuántos grupos de puntos conectados por una cara hay. */
    private static int groups(Set<Long> pts) {
        return sizes(pts).size();
    }

    private static int largest(Set<Long> pts) {
        return sizes(pts).stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static java.util.List<Integer> sizes(Set<Long> pts) {
        Set<Long> seen = new HashSet<>();
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (long start : pts) {
            if (!seen.add(start)) continue;
            int n = 1;
            ArrayDeque<Long> q = new ArrayDeque<>();
            q.add(start);
            while (!q.isEmpty()) {
                long p = q.poll();
                int x = PointsSource.unpackX(p), y = PointsSource.unpackY(p), z = PointsSource.unpackZ(p);
                int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] k : d) {
                    long o = PointsSource.pack(x + k[0], y + k[1], z + k[2]);
                    if (pts.contains(o) && seen.add(o)) {
                        q.add(o);
                        n++;
                    }
                }
            }
            out.add(n);
        }
        return out;
    }

    // ---------------------------------------------------------------- H2 vetas

    @Test
    void H2_CA1_una_veta_tiene_mas_o_menos_el_tamano_pedido_y_esta_junta() {
        Box all = new Box(-100, -100, -100, 100, 100, 100);
        for (int size : new int[]{3, 8, 17}) {
            int total = 0;
            for (int i = 0; i < 40; i++) {
                Shapes.Longs out = new Shapes.Longs();
                Ores.vein(0, 0, 0, size, new SplittableRandom(i), all, out);
                Set<Long> s = set(out.toArray());
                assertFalse(s.isEmpty(), "toda veta pone algo");
                // Como en el juego, las esferas de una veta a veces no se tocan; pero no queda desparramada.
                assertTrue(groups(s) <= 3, groups(s) + " grupos (tamaño " + size + ")");
                for (long p : s) assertTrue(Math.abs(PointsSource.unpackX(p)) <= size && Math.abs(PointsSource.unpackY(p)) <= size, "junta");
                total += s.size();
            }
            double avg = total / 40.0;
            assertTrue(avg >= size * 0.3 && avg <= size * 2.5, "tamaño " + size + ": " + avg + " bloques de media");
        }
    }

    @Test
    void H2_CA1_frecuencia_rareza_alturas_y_solo_en_la_region() {
        Box box = new Box(0, 0, 0, 31, 40, 31); // 4 chunks
        Ores.Result r = Ores.place(box, new Ores.Spec(8, 10, 100, 0, 40), 7, FREE);
        assertEquals(40, r.veins(), "10 intentos por chunk, todos salen");
        assertEquals(0, Ores.place(box, new Ores.Spec(8, 10, 0, 0, 40), 7, FREE).points().length, "rareza 0: nada");
        int half = Ores.place(box, new Ores.Spec(8, 50, 50, 0, 40), 7, FREE).veins();
        assertTrue(half > 60 && half < 140, "rareza 50 %: la mitad de 200 intentos (" + half + ")");
        Ores.Result low = Ores.place(box, new Ores.Spec(8, 10, 100, 5, 10), 7, FREE);
        for (long p : low.points()) assertTrue(PointsSource.unpackY(p) >= 3 && PointsSource.unpackY(p) <= 12, "cerca de las alturas pedidas");
        assertEquals(0, Ores.place(box, new Ores.Spec(8, 10, 100, 50, 60), 7, FREE).points().length, "alturas fuera de la región");
        Cylinder cyl = new Cylinder(16, 16, 6, 6, 0, 40);
        for (long p : Ores.place(cyl, new Ores.Spec(12, 30, 100, 0, 40), 3, FREE).points()) {
            assertTrue(cyl.contains(PointsSource.unpackX(p), PointsSource.unpackY(p), PointsSource.unpackZ(p)), "solo en la región");
        }
        assertArrayEquals(r.points(), Ores.place(box, new Ores.Spec(8, 10, 100, 0, 40), 7, FREE).points(), "misma semilla, lo mismo");
        assertThrows(Formula.TimedOut.class, () -> Ores.place(box, new Ores.Spec(8, 10, 100, 0, 40), 7, new Shapes.Work(System.nanoTime() - 1)));
    }

    // ---------------------------------------------------------------- H3 cuevas

    @Test
    void H3_CA1_tuneles_que_se_conectan_mas_con_salas_y_mas_anchos() {
        Box box = new Box(0, 0, 0, 79, 47, 79);
        long vol = box.volume();
        Set<Long> base = set(Caves.carve(box, 3, 40, false, 11, FREE));
        double f = (double) base.size() / vol;
        assertTrue(f > 0.003 && f < 0.08, "la fracción tallada es la de unas cuevas: " + f);
        // Los túneles son largos: el grupo más grande junta buena parte de lo tallado (no son agujeros sueltos).
        assertTrue(largest(base) > base.size() / 5, "el grupo más grande tiene " + largest(base) + " de " + base.size());
        Set<Long> wide = set(Caves.carve(box, 6, 40, false, 11, FREE));
        assertTrue(wide.size() > base.size() * 2, "el doble de ancho talla bastante más: " + wide.size() + " vs " + base.size());
        assertTrue(wide.containsAll(base), "lo angosto está dentro de lo ancho (la misma semilla)");
        Set<Long> rooms = set(Caves.carve(box, 3, 40, true, 11, FREE));
        assertTrue(rooms.containsAll(base));
        Set<Long> more = set(Caves.carve(box, 3, 90, false, 11, FREE));
        assertTrue(more.size() > base.size(), "más frecuencia, más túneles: " + more.size() + " vs " + base.size());
        assertNotEquals(base, set(Caves.carve(box, 3, 40, false, 12, FREE)), "otra semilla, otras cuevas");
        assertThrows(Formula.TimedOut.class, () -> Caves.carve(box, 3, 40, false, 1, new Shapes.Work(System.nanoTime() - 1)));
    }

    @Test
    void H3_CA1_salas_cuando_se_piden() {
        Box box = new Box(0, 0, 0, 127, 63, 127);
        long a = Caves.carve(box, 3, 40, false, 5, FREE).length, b = Caves.carve(box, 3, 40, true, 5, FREE).length;
        assertTrue(b > a, "--rooms agrega salas: " + b + " vs " + a);
    }

    // ---------------------------------------------------------------- H4 imagen

    static byte[] png(BufferedImage img, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, format, out));
        return out.toByteArray();
    }

    @Test
    void H4_CA1_leer_achicar_promediando_y_transparencia() throws Exception {
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF0000);
        img.setRGB(1, 0, 0xFF0000FF);
        img.setRGB(2, 0, 0x00000000);
        img.setRGB(3, 0, 0x00000000);
        for (int x = 0; x < 4; x++) img.setRGB(x, 1, 0xFFFFFFFF);
        ImageBlocks.Pixels p = ImageBlocks.read(png(img, "png"));
        assertEquals(4, p.width());
        assertEquals(0xFFFF0000, p.argb()[0]);
        ImageBlocks.Pixels half = ImageBlocks.scale(p, 2);
        assertEquals(2, half.width());
        assertEquals(1, half.height());
        // Rojo, azul, blanco y blanco: el promedio de cada canal; lo transparente (al lado) no oscurece.
        int c = half.argb()[0];
        assertEquals(0xFF, c >>> 24);
        assertEquals(191, (c >> 16) & 0xFF, 1);
        assertEquals(127, (c >> 8) & 0xFF, 1);
        assertEquals(191, c & 0xFF, 1);
        int t = half.argb()[1];
        assertEquals(255 / 2, t >>> 24, 1, "mitad transparente");
        assertEquals(0xFFFFFF, t & 0xFFFFFF, "el color es el de lo que se ve (blanco), no gris");
        ImageBlocks.Pixels big = ImageBlocks.scale(p, 8);
        assertEquals(8, big.width());
        assertEquals(0xFFFF0000, big.argb()[1], "al agrandar, el más cercano");
        BlockColors colors = new BlockColors(BlockColors.builtin());
        int[] b = ImageBlocks.blocks(p, colors);
        assertEquals(-1, b[2], "transparente: se deja");
        assertTrue(colors.entries().get(b[4]).lab().l() > 85, "el blanco es el bloque más claro: " + colors.entries().get(b[4]).id());
        // JPG y GIF también.
        BufferedImage rgb = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        assertEquals(3, ImageBlocks.read(png(rgb, "jpg")).width());
        assertEquals(3, ImageBlocks.read(png(rgb, "gif")).height());
    }

    @Test
    void H4_CA4_lo_que_no_es_imagen_y_las_bombas_se_rechazan_sin_decodificar() throws Exception {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ImageBlocks.read("<html>hola</html>".getBytes())).getMessage()
                .contains("not a PNG"));
        assertThrows(IllegalArgumentException.class, () -> ImageBlocks.read(new byte[0]));
        // Una PNG que dice medir 60 000 × 60 000 en su cabecera: se rechaza por la cabecera (no hay memoria para decodificarla).
        byte[] small = png(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png");
        byte[] bomb = small.clone();
        writeInt(bomb, 16, 60_000);
        writeInt(bomb, 20, 60_000);
        fixCrc(bomb);
        String e = assertThrows(IllegalArgumentException.class, () -> ImageBlocks.read(bomb)).getMessage();
        assertTrue(e.contains("60000×60000") && e.contains("4096"), e);
    }

    private static void writeInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }

    /** El CRC del bloque IHDR (bytes 12..28: tipo y datos; el CRC en 29..32). */
    private static void fixCrc(byte[] b) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(b, 12, 17);
        writeInt(b, 29, (int) crc.getValue());
    }

    @Test
    void H4_CA2_de_pie_y_acostada_sin_espejar_en_las_cuatro_direcciones() {
        // Para cada mirada: la columna 0 (izquierda de la imagen) a la izquierda de quien mira, la fila 0 (arriba) arriba
        // o lejos. Izquierda de una mirada (dx, dz) = (dz, −dx).
        int[][] dirs = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        for (int[] d : dirs) {
            int lx = d[1], lz = -d[0];
            ImageBlocks.Placement w = ImageBlocks.wall(100, 64, 200, d[0], d[1], 5, 3);
            // La columna 0 está más a la izquierda que la 4.
            assertTrue((w.x(0, 0) - w.x(4, 0)) * lx + (w.z(0, 0) - w.z(4, 0)) * lz > 0, "de pie, mirando " + d[0] + "," + d[1]);
            assertEquals(66, w.y(0, 0), "la fila de arriba arriba");
            assertEquals(64, w.y(0, 2), "la de abajo a la altura de los pies");
            // A 2 bloques delante y centrada (columna 2 = el centro).
            assertEquals(100 + 2 * d[0], w.x(2, 0));
            assertEquals(200 + 2 * d[1], w.z(2, 0));
            assertEquals(3, w.col(w.x(3, 1), w.y(3, 1), w.z(3, 1)));
            assertEquals(1, w.row(w.x(3, 1), w.y(3, 1), w.z(3, 1)));

            ImageBlocks.Placement f = ImageBlocks.floor(100, 64, 200, d[0], d[1], 5, 3);
            assertTrue((f.x(0, 0) - f.x(4, 0)) * lx + (f.z(0, 0) - f.z(4, 0)) * lz > 0, "acostada, mirando " + d[0] + "," + d[1]);
            // La fila de arriba la más lejana, la de abajo a 1 bloque.
            int far = (f.x(2, 0) - 100) * d[0] + (f.z(2, 0) - 200) * d[1], near = (f.x(2, 2) - 100) * d[0] + (f.z(2, 2) - 200) * d[1];
            assertEquals(3, far);
            assertEquals(1, near);
            assertEquals(64, f.y(1, 1));
            assertEquals(2, f.col(f.x(2, 1), 64, f.z(2, 1)));
            assertEquals(1, f.row(f.x(2, 1), 64, f.z(2, 1)));
        }
        assertArrayEquals(new int[]{0, 1}, ImageBlocks.facing(10));
        assertArrayEquals(new int[]{-1, 0}, ImageBlocks.facing(80));
        assertArrayEquals(new int[]{0, -1}, ImageBlocks.facing(-170));
        assertArrayEquals(new int[]{1, 0}, ImageBlocks.facing(-90));
    }
}
