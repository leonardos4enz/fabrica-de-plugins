package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.TestBlocks;
import dev.leo.worldcraft.TestBlocks.MemoryWorld;
import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.pattern.Parser;
import dev.leo.worldcraft.pattern.Pattern;
import dev.leo.worldcraft.region.Box;
import dev.leo.worldcraft.region.Cylinder;
import dev.leo.worldcraft.region.Direction;
import dev.leo.worldcraft.region.Ellipsoid;
import dev.leo.worldcraft.region.PointSet;
import dev.leo.worldcraft.region.Polygon;
import dev.leo.worldcraft.region.Region;
import dev.leo.worldcraft.region.Shape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 030 H2 CA8 y H4: los comandos de región sobre una forma que no es caja, en el mundo en memoria. */
class ShapeOpsTest {
    static final UUID ANA = new UUID(1, 1);
    final StateTable table = new StateTable();
    final MemoryWorld world = new MemoryWorld(table);
    final Parser parser = new Parser(TestBlocks.REGISTRY, Map.of(), Map.of());
    final IntPredicate air = s -> table.material(s).endsWith("air");

    EditJob apply(EditSource src) {
        EditJob j = new EditJob(1, ANA, "test", world, src, EditJob.Mode.APPLY, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, null, Long.MAX_VALUE, System::nanoTime);
        EditTest.run(j);
        return j;
    }

    void read(Region r, EditJob.Capture into) {
        EditJob j = new EditJob(2, ANA, "read", world, new RegionSource(r, Shape.FILL, null, null, 0), EditJob.Mode.READ, new EditJob.Sink() {
            @Override
            public void changed(int x, int y, int z, int b, int a, byte[] tb, byte[] ta) {
            }
        }, into, Long.MAX_VALUE, System::nanoTime);
        EditTest.run(j);
    }

    Pattern.Compiled pattern(String p) throws Exception {
        return parser.pattern(p).compile(table);
    }

    @Test
    void H4_CA1_set_en_un_cilindro_no_toca_las_esquinas_de_la_caja() throws Exception {
        Cylinder c = new Cylinder(8, 8, 4, 4, 40, 43);
        EditJob j = apply(new RegionSource(c, Shape.FILL, null, pattern("stone"), 1));
        assertEquals(c.volume(), j.changed());
        assertEquals("minecraft:stone", world.at(8, 40, 8));
        assertEquals("minecraft:stone", world.at(12, 43, 8));
        assertEquals("minecraft:air", world.at(12, 40, 12), "la esquina de la caja");
    }

    @Test
    void H4_CA2_walls_de_un_cilindro_es_el_tubo_y_faces_le_pone_tapas() throws Exception {
        Cylinder c = new Cylinder(8, 8, 4, 4, 40, 43);
        EditJob w = apply(new RegionSource(c, Shape.WALLS, null, pattern("glass"), 1));
        assertEquals(c.count(Shape.WALLS), w.changed());
        assertEquals("minecraft:glass", world.at(12, 41, 8));
        assertEquals("minecraft:air", world.at(8, 40, 8), "walls: sin tapa");
        EditJob f = apply(new RegionSource(c, Shape.FACES, null, pattern("glass"), 1));
        assertEquals(c.count(Shape.FACES) - c.count(Shape.WALLS), f.changed(), "faces agrega solo el piso y el techo");
        assertEquals("minecraft:glass", world.at(8, 40, 8));
        assertEquals("minecraft:air", world.at(8, 41, 8), "hueco por dentro");
    }

    @Test
    void H4_CA3_copiar_una_esfera_no_pega_las_esquinas_ni_con_aire() throws Exception {
        Ellipsoid e = new Ellipsoid(4, 50, 4, 3, 3, 3, true);
        apply(new RegionSource(e.bounds(), Shape.FILL, null, pattern("dirt"), 1));
        Box box = e.bounds();
        Clipboard clip = new Clipboard(box, 4, 50, 4);
        read(e, (x, y, z, s, tile) -> clip.put(box, x, y, z, s, tile));
        assertEquals(e.volume(), java.util.Arrays.stream(clip.rawStates()).filter(s -> s >= 0).count(), "solo lo de la esfera");
        world.put(101, 47, 101, "minecraft:gold_block"); // la esquina de la caja donde se pega
        EditJob j = apply(new PasteSource(clip, 104, 50, 104, true, air, TestBlocks.ops(table)));
        assertEquals(e.volume(), j.changed(), "con --air se pega la esfera y nada más");
        assertEquals("minecraft:dirt", world.at(104, 50, 104));
        assertEquals("minecraft:gold_block", world.at(101, 47, 101), "la esquina quedó como estaba");
    }

    @Test
    void H4_CA4_move_de_un_cilindro_deja_atras_solo_lo_suyo() throws Exception {
        Cylinder c = new Cylinder(0, 0, 2, 2, 10, 11);
        apply(new RegionSource(c, Shape.FILL, null, pattern("stone"), 1));
        world.put(-2, 10, 2, "minecraft:gold_block"); // la esquina de la caja, fuera del cilindro (y del movido)
        world.put(5, 10, 2, "minecraft:gold_block"); // la esquina de la caja movida
        Snapshot snap = new Snapshot(c.bounds());
        read(c, snap);
        apply(new MoveSource(snap, 3, 0, 0, pattern("air"), 1));
        assertEquals("minecraft:stone", world.at(5, 10, 0), "movido");
        assertEquals("minecraft:air", world.at(-2, 10, 0), "lo que quedó atrás");
        assertEquals("minecraft:stone", world.at(1, 10, 0), "cubierto por el cilindro movido");
        assertEquals("minecraft:gold_block", world.at(-2, 10, 2), "fuera de la forma: no se borra");
        assertEquals("minecraft:stone", world.at(2, 10, 1), "lo de la forma movida sí se pone, aunque antes fuera una esquina");
        assertEquals("minecraft:gold_block", world.at(5, 10, 2), "fuera de la forma movida: no se pisa");
    }

    @Test
    void H4_CA4_stack_de_un_poligono_repite_solo_el_triangulo() throws Exception {
        Polygon p = new Polygon(List.of(new int[]{0, 0}, new int[]{4, 0}, new int[]{0, 4}), 20, 20);
        apply(new RegionSource(p.bounds(), Shape.FILL, null, pattern("dirt"), 1));
        Snapshot snap = new Snapshot(p.bounds());
        read(p, snap);
        EditJob j = apply(new StackSource(snap, Direction.UP, 2, true, air));
        assertEquals(2 * p.volume(), j.changed());
        assertEquals("minecraft:dirt", world.at(0, 22, 0));
        assertEquals("minecraft:air", world.at(4, 22, 4), "la esquina de la caja no se repite");
    }

    @Test
    void H4_CA1_count_cuenta_solo_lo_de_adentro() throws Exception {
        Ellipsoid e = new Ellipsoid(0, 30, 0, 2, 2, 2, true);
        apply(new RegionSource(e.bounds(), Shape.FILL, null, pattern("stone"), 1));
        Census c = new Census(null, null, 0, false, table);
        read(e, c);
        assertEquals(e.volume(), c.total());
        Snapshot snap = new Snapshot(e.bounds().grow(1));
        read(e.bounds().grow(1), snap);
        assertEquals(e.volume(), new Census(null, null, 0, false, table).over(snap, e).total(), "con la foto (máscaras con vecinos)");
    }

    @Test
    void H2_CA8_la_magica_toma_los_conectados_del_mismo_material() {
        Box around = Box.of(-3, 0, -3, 6, 3, 6);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) world.put(x, 1, z, "minecraft:gold_block");
        world.put(3, 1, 1, "minecraft:diamond_block");
        world.put(5, 1, 5, "minecraft:gold_block"); // suelto: no está conectado
        Snapshot snap = new Snapshot(around);
        read(around, snap);
        int gold = table.intern("minecraft:gold_block"), diamond = table.intern("minecraft:diamond_block");
        PointSet g = Magic.flood(snap, 1, 1, 1, s -> s == gold);
        assertEquals(9, g.volume());
        assertFalse(g.contains(5, 1, 5));
        PointSet d = Magic.flood(snap, 3, 1, 1, s -> s == diamond);
        assertEquals(10, g.union(d).volume());
        assertNull(Magic.flood(snap, 1, 2, 1, s -> s == gold), "aire: nada");
        assertEquals(9, Magic.flood(snap, 2, 1, 2, s -> s == gold).volume(), "desde cualquier bloque del grupo");
    }
}
