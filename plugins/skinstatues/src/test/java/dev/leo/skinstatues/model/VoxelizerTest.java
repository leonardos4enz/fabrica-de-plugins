package dev.leo.skinstatues.model;

import dev.leo.skinstatues.model.Pose.Angles;
import dev.leo.skinstatues.model.Pose.Part;
import dev.leo.skinstatues.config.Issues;
import dev.leo.skinstatues.config.LoadedConfig;
import dev.leo.skinstatues.config.PoseLoader;
import dev.leo.skinstatues.config.TestResources;
import dev.leo.skinstatues.skin.Skin;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.leo.skinstatues.model.TestSkins.*;
import static org.junit.jupiter.api.Assertions.*;

class VoxelizerTest {
    private static Voxelizer.Options flat(double h) {
        return new Voxelizer.Options(h, 0, false, 0.5, 128, false);
    }

    private static Voxelizer.Options layered(double h) {
        return new Voxelizer.Options(h, 0, true, 0.5, 128, false);
    }

    private static Map<Long, Integer> index(VoxelGrid g) {
        Map<Long, Integer> m = new HashMap<>();
        for (int i = 0; i < g.size(); i++) m.put(VoxelGrid.key(g.x(i), g.y(i), g.z(i)), g.color(i));
        return m;
    }

    private static Integer at(Map<Long, Integer> m, int x, int y, int z) {
        return m.get(VoxelGrid.key(x, y, z));
    }

    static Pose pose(String name, Object... parts) {
        Map<Part, Angles> m = new HashMap<>();
        for (int i = 0; i < parts.length; i += 2) {
            double[] a = (double[]) parts[i + 1];
            m.put((Part) parts[i], new Angles(a[0], a[1], a[2], a.length > 3 ? a[3] : 0));
        }
        return new Pose(name, m);
    }

    /** Las poses que trae el plugin, de builtin-poses.yml (spec 022 H1 CA5: todas pasan las mismas pruebas). */
    static List<Pose> examplePoses() {
        Issues issues = new Issues();
        List<Pose> out = List.copyOf(PoseLoader.load(TestResources.builtinPoses(), LoadedConfig.BUILTIN_POSES, null, null,
                issues).poses().values());
        assertTrue(issues.all().isEmpty(), issues.all().toString());
        assertEquals("sitting", out.get(5).name());
        return out;
    }

    @Test
    void H1_CA1_alto_y_ancho_de_pie() {
        VoxelGrid g = Voxelizer.run(plain(), Pose.STANDING, flat(32));
        assertEquals(32, g.height());
        assertEquals(-8, g.minX());
        assertEquals(7, g.maxX());
        assertEquals(-4, g.minZ());
        assertEquals(3, g.maxZ());
        VoxelGrid slim = Voxelizer.run(Skin.of(image(), true, null), Pose.STANDING, flat(32));
        assertEquals(-7, slim.minX(), "brazos de 3 píxeles en el modelo delgado");
        assertEquals(6, slim.maxX());
    }

    @Test
    void H1_CA1_la_altura_pedida_escala_la_figura() {
        assertEquals(64, Voxelizer.run(plain(), Pose.STANDING, flat(64)).height());
        assertEquals(128, Voxelizer.run(plain(), Pose.STANDING, flat(128)).height());
        BufferedImage hat = image();
        fill(hat, 40, 0, 48, 8, YELLOW); // arriba del sombrero
        assertEquals(64, Voxelizer.run(Skin.of(hat, false, null), Pose.STANDING, layered(64)).height(), "el sombrero entra en el alto");
        assertEquals(64, Voxelizer.run(plain(), Pose.STANDING, layered(64)).height(), "sin sombrero no se descuenta");
    }

    @Test
    void H1_CA4_hueca_con_la_skin_en_la_superficie() {
        Map<Long, Integer> m = index(Voxelizer.run(plain(), Pose.STANDING, flat(32)));
        assertEquals(RED, at(m, 0, 28, 3), "frente de la cabeza");
        assertEquals(BLUE, at(m, 0, 18, 1), "frente del cuerpo");
        assertEquals(GREEN, at(m, -6, 18, 1), "brazo derecho en −x");
        assertNull(at(m, 0, 28, 0), "el interior de la cabeza está vacío");
        assertNull(at(m, 0, 18, 0), "el interior del cuerpo está vacío");
    }

    @Test
    void H1_CA3_escala_chica_promedia_los_pixeles() {
        VoxelGrid g = Voxelizer.run(plain(), Pose.STANDING, flat(16));
        assertEquals(16, g.height());
        Map<Long, Integer> m = index(g);
        assertEquals(RED, at(m, 0, 14, 1));
        // Mitad roja, mitad gris en la huella del bloque de la esquina de la cara: el promedio.
        BufferedImage img = image();
        fill(img, 10, 8, 11, 16, GRAY);
        Integer mixed = at(index(Voxelizer.run(Skin.of(img, false, null), Pose.STANDING, flat(16))), -1, 14, 1);
        assertEquals(0xFFBF4040, mixed);
    }

    @Test
    void H1_CA2_mira_hacia_donde_mira_el_jugador() {
        Map<Long, Integer> west = index(Voxelizer.run(plain(), Pose.STANDING, new Voxelizer.Options(32, 90, false, 0.5, 128, false)));
        assertEquals(RED, at(west, -4, 28, 0), "mirando al oeste la cara está en −x");
        Map<Long, Integer> north = index(Voxelizer.run(plain(), Pose.STANDING, new Voxelizer.Options(32, 180, false, 0.5, 128, false)));
        assertEquals(RED, at(north, -1, 28, -4), "mirando al norte la cara está en −z");
        assertEquals(GREEN, at(north, 5, 18, -2), "y el brazo derecho del lado del este");
    }

    @Test
    void H3_CA1_segunda_capa_pegada_sin_hueco() {
        BufferedImage img = image();
        fill(img, 40, 8, 48, 16, YELLOW); // frente del sombrero
        VoxelGrid g = Voxelizer.run(Skin.of(img, false, null), Pose.STANDING, layered(33));
        assertEquals(33, g.height());
        Map<Long, Integer> m = index(g);
        assertEquals(YELLOW, at(m, 0, 28, 4), "sombrero justo por fuera de la cara");
        assertEquals(RED, at(m, 0, 28, 3), "y la cara pegada detrás, sin hueco");
        assertNull(at(m, 0, 28, 5), "no flota más afuera");
    }

    // Regresión (spec 021, F-03): a 32 de alto, descontar el bloque del sombrero bajaba la escala a 31/32 y la decisión
    // «relieve o pintada» se tomaba con esa escala: quedaba pintada y la estatua medía 31.
    @Test
    void H3_CA1_a_32_de_alto_el_sombrero_sale_en_relieve_y_mide_32() {
        BufferedImage img = image();
        fill(img, 40, 0, 48, 8, YELLOW);  // arriba del sombrero
        fill(img, 40, 8, 48, 16, YELLOW); // frente del sombrero
        VoxelGrid g = Voxelizer.run(Skin.of(img, false, null), Pose.STANDING, layered(32));
        assertEquals(32, g.height());
        Map<Long, Integer> m = index(g);
        assertEquals(YELLOW, at(m, 0, 31, 0), "el sombrero arriba, en su propio bloque");
        assertNotEquals(YELLOW, at(m, 0, 30, 0), "y la cabeza debajo, sin pintar");
    }

    @Test
    void H3_CA2_transparente_no_pone_nada_y_el_umbral_cuenta() {
        BufferedImage img = image();
        fill(img, 40, 8, 48, 16, 0x64FFFF00); // alfa 100
        assertNull(at(index(Voxelizer.run(Skin.of(img, false, null), Pose.STANDING, layered(33))), 0, 28, 4));
        assertNull(at(index(Voxelizer.run(Skin.of(img, false, null), Pose.STANDING, layered(33))), 4, 28, 0),
                "el costado del sombrero es transparente");
        Map<Long, Integer> low = index(Voxelizer.run(Skin.of(img, false, null), Pose.STANDING,
                new Voxelizer.Options(33, 0, true, 0.5, 50, false)));
        assertNotNull(at(low, 0, 28, 4), "con umbral 50 el píxel de alfa 100 cuenta");
    }

    @Test
    void H3_CA3_a_escala_chica_la_segunda_capa_pinta_la_primera() {
        BufferedImage img = image();
        fill(img, 40, 8, 48, 16, YELLOW);
        Map<Long, Integer> m = index(Voxelizer.run(Skin.of(img, false, null), Pose.STANDING, layered(16)));
        assertEquals(YELLOW, at(m, 0, 14, 1));
        assertNull(at(m, 0, 14, 2), "sin bloques propios");
    }

    @Test
    void H3_CA4_sin_segunda_capa() {
        BufferedImage img = image();
        fill(img, 40, 8, 48, 16, YELLOW);
        Map<Long, Integer> m = index(Voxelizer.run(Skin.of(img, false, null), Pose.STANDING, flat(33)));
        assertNull(at(m, 0, 28, 4));
    }

    @Test
    void H3_la_chaqueta_no_tapa_el_brazo() {
        BufferedImage img = image();
        fill(img, 16, 32, 40, 48, MAGENTA); // chaqueta opaca entera
        Map<Long, Integer> m = index(Voxelizer.run(Skin.of(img, false, null), Pose.STANDING, layered(33)));
        assertEquals(GREEN, at(m, -5, 18, 0), "la primera capa del brazo gana a la chaqueta");
        assertEquals(MAGENTA, at(m, 0, 18, 2), "la chaqueta sí va por fuera del frente del cuerpo");
    }

    @Test
    void H5_capa_en_la_espalda_e_inclinada() {
        Skin caped = Skin.of(image(), false, cape());
        Map<Long, Integer> m = index(Voxelizer.run(caped, Pose.STANDING, new Voxelizer.Options(32, 0, false, 0.5, 128, true)));
        assertEquals(CYAN, at(m, 0, 16, -3), "por fuera se ve el frente de la textura de la capa");
        assertEquals(GRAY, at(m, 0, 16, -2), "la espalda sigue detrás");
        Pose lifted = pose("x", Part.CAPE, new double[]{30, 0, 0});
        Map<Long, Integer> l = index(Voxelizer.run(caped, lifted, new Voxelizer.Options(32, 0, false, 0.5, 128, true)));
        assertNotEquals(CYAN, at(l, 0, 9, -3), "inclinada, la punta se separa de la espalda");
        assertTrue(l.entrySet().stream().anyMatch(e -> e.getValue() == CYAN && (int) (e.getKey() & 0x1FFFFF) - VoxelGrid.OFF < -5));
        Map<Long, Integer> none = index(Voxelizer.run(caped, Pose.STANDING, flat(32)));
        assertFalse(none.containsValue(CYAN), "H5 CA3: sin capa no se pone nada");
    }

    // Regresión (spec 021, F-01): a escala 1.5 la capa queda de un bloque de grueso y su centro está más cerca de la
    // cara interior; desde atrás se veía la textura de adentro (sin la «S» del ejemplo) en vez de la de afuera.
    @Test
    void H5_CA1_capa_delgada_muestra_la_cara_de_afuera() {
        Skin caped = Skin.of(image(), false, cape());
        VoxelGrid g = Voxelizer.run(caped, Pose.STANDING, new Voxelizer.Options(48, 0, false, 0.5, 128, true));
        Map<Long, Integer> m = index(g);
        int back = 0, inner = 0;
        for (int x = -6; x < 6; x++) {
            for (int y = 14; y < 34; y++) {
                for (int z = g.minZ(); z <= g.maxZ(); z++) {
                    Integer c = at(m, x, y, z);
                    if (c == null) continue;
                    if (c == CYAN) back++;
                    if (c == ORANGE) inner++;
                    break; // el primero desde atrás
                }
            }
        }
        assertTrue(back > 100, "desde atrás se ve la cara de afuera de la capa: " + back);
        assertEquals(0, inner, "y nunca la de adentro");
    }

    @Test
    void H4_CA6_lo_mas_bajo_queda_en_los_pies() {
        VoxelGrid sit = Voxelizer.run(plain(), examplePoses().get(5), flat(32));
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < sit.size(); i++) min = Math.min(min, sit.y(i));
        assertEquals(0, min);
        assertTrue(sit.shift() < 0, "sentado, las piernas quedan altas y se baja la figura");
        Pose kneeDown = pose("x", Part.RIGHT_LEG, new double[]{-60, 0, 0});
        assertTrue(Voxelizer.run(plain(), kneeDown, flat(32)).shift() >= 0);
    }

    @Test
    void spec022_H1_CA5_ninguna_pose_incorporada_baja_de_los_pies() {
        for (Pose p : examplePoses()) {
            VoxelGrid g = Voxelizer.run(Skin.of(image(), false, cape()), p, layered(32));
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < g.size(); i++) min = Math.min(min, g.y(i));
            assertEquals(0, min, p.name());
            assertTrue(g.height() <= 36, p.name() + " no pasa mucho del alto pedido: " + g.height());
        }
    }

    @Test
    void spec022_H2_CA2_el_espejo_de_una_pose_es_la_estatua_reflejada() {
        BufferedImage one = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        fill(one, 0, 0, 64, 64, RED);
        Skin skin = Skin.of(one, false, null);
        for (Pose p : examplePoses()) {
            Set<Long> want = new HashSet<>();
            VoxelGrid g = Voxelizer.run(skin, p, flat(32));
            for (int i = 0; i < g.size(); i++) want.add(VoxelGrid.key(-1 - g.x(i), g.y(i), g.z(i)));
            VoxelGrid m = Voxelizer.run(skin, p.mirrored(p.name() + "_mirrored"), flat(32));
            Set<Long> got = new HashSet<>();
            for (int i = 0; i < m.size(); i++) got.add(VoxelGrid.key(m.x(i), m.y(i), m.z(i)));
            assertEquals(want, got, p.name() + ": el espejo bloque por bloque");
        }
    }

    @Test
    void H4_CA2_y_H1_CA4_cascara_cerrada_en_todas_las_poses() {
        Skin skin = Skin.of(image(), false, cape());
        int checked = 0, overlapped = 0;
        for (Pose p : examplePoses()) {
            for (double yaw : new double[]{0, 90}) {
                Voxelizer.Options o = new Voxelizer.Options(128, yaw, true, 0.5, 128, false);
                VoxelGrid g = Voxelizer.run(skin, p, o);
                Set<Long> solid = new HashSet<>();
                for (int i = 0; i < g.size(); i++) solid.add(VoxelGrid.key(g.x(i), g.y(i), g.z(i)));
                Mat3 facing = Mat3.rotY(-yaw);
                for (Segment seg : Voxelizer.segmentsFor(skin, p, false)) {
                    // El centro de la mitad y, en los miembros, la articulación misma.
                    Vec3 c = new Vec3((seg.tmin[0] + seg.tmax[0]) / 2, (seg.yLo + seg.yHi) / 2, (seg.tmin[2] + seg.tmax[2]) / 2);
                    for (Vec3 start : seg.miterPoint == null ? List.of(c) : List.of(c, seg.miterPoint)) {
                        Vec3 w = facing.mul(seg.toModel.apply(start)).scale(g.scale());
                        int sx = (int) Math.floor(w.x()), sy = (int) Math.floor(w.y()) + g.shift(), sz = (int) Math.floor(w.z());
                        if (solid.contains(VoxelGrid.key(sx, sy, sz))) {
                            // Otra parte pasa por dentro (la cara metida en el brazo en dabbing): no hay hueco que medir.
                            assertNotEquals(Pose.STANDING, p, seg.name + ": de pie, el interior es hueco");
                            overlapped++;
                            continue;
                        }
                        checked++;
                        assertFalse(leaks(solid, g, sx, sy, sz), p.name() + " (" + yaw + "°) " + seg.name + ": la cáscara tiene un hueco");
                    }
                }
            }
        }
        assertTrue(overlapped * 20 < checked, "casi todos los puntos se comprueban: " + overlapped + " de " + checked);
    }

    /** Inundación por bloques vacíos (6 vecinos) desde dentro: si llega al borde de la caja, hay un agujero. */
    private static boolean leaks(Set<Long> solid, VoxelGrid g, int sx, int sy, int sz) {
        int x0 = g.minX() - 2, x1 = g.maxX() + 2, y0 = -2, y1 = g.height() + 2, z0 = g.minZ() - 2, z1 = g.maxZ() + 2;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        Map<Long, Long> from = new HashMap<>();
        q.add(new int[]{sx, sy, sz});
        seen.add(VoxelGrid.key(sx, sy, sz));
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[0] <= x0 || p[0] >= x1 || p[1] <= y0 || p[1] >= y1 || p[2] <= z0 || p[2] >= z1) {
                long k = VoxelGrid.key(p[0], p[1], p[2]);
                StringBuilder sb = new StringBuilder();
                while (from.containsKey(k)) { sb.insert(0, "(" + (((k >>> 42) & 0x1FFFFF) - VoxelGrid.OFF) + "," + (((k >>> 21) & 0x1FFFFF) - VoxelGrid.OFF) + "," + ((k & 0x1FFFFF) - VoxelGrid.OFF) + ") "); k = from.get(k); }
                System.out.println("FUGA: " + sb);
                return true;
            }
            for (int[] d : dirs) {
                int x = p[0] + d[0], y = p[1] + d[1], z = p[2] + d[2];
                long k = VoxelGrid.key(x, y, z);
                if (!solid.contains(k) && seen.add(k)) { from.put(k, VoxelGrid.key(p[0], p[1], p[2])); q.add(new int[]{x, y, z}); }
            }
        }
        return false;
    }
}
