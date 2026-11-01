package dev.leo.blockwright.region;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegionTest {

    @Test
    void H1_CA4_agrandar_achicar_y_mover_con_palabras() {
        Box b = Box.of(5, 64, 5, 0, 60, 0);
        assertEquals(new Box(0, 60, 0, 5, 64, 5), b);
        assertEquals("6×5×6", b.size());
        assertEquals(180, b.volume());
        assertEquals(new Box(0, 60, 0, 5, 74, 5), b.expand(Direction.UP, 10));
        assertEquals(new Box(0, 60, -3, 5, 64, 5), b.expand(Direction.NORTH, 3));
        assertEquals(new Box(0, 60, 0, 5, 64, 3), b.contract(Direction.SOUTH, 2));
        assertEquals(new Box(2, 60, 0, 7, 64, 5), b.shift(Direction.EAST, 2));
        assertThrows(IllegalArgumentException.class, () -> b.contract(Direction.UP, 5));
        assertEquals(Direction.WEST, Direction.parse("w"));
        assertEquals(Direction.NORTH, Direction.parse("North"));
        assertNull(Direction.parse("left"));
    }

    @Test
    void H1_CA4_hacia_donde_mira_el_jugador() {
        assertEquals(Direction.SOUTH, Direction.facing(0, 0));
        assertEquals(Direction.WEST, Direction.facing(90, 10));
        assertEquals(Direction.NORTH, Direction.facing(-180, 0));
        assertEquals(Direction.EAST, Direction.facing(-90, 0));
        assertEquals(Direction.UP, Direction.facing(30, -80));
        assertEquals(Direction.DOWN, Direction.facing(30, 75));
    }

    @Test
    void H5_CA3_recorre_cada_posicion_una_vez_chunk_por_chunk() {
        Box b = Box.of(-20, 0, 7, 37, 3, 50);
        for (Shape s : Shape.values()) {
            Set<String> seen = new HashSet<>();
            Set<Long> finished = new HashSet<>();
            long last = Long.MIN_VALUE;
            ChunkOrder o = new ChunkOrder(b, s);
            int[] p = new int[3];
            while (o.next(p)) {
                assertTrue(b.contains(p[0], p[1], p[2]));
                assertTrue(seen.add(p[0] + "," + p[1] + "," + p[2]), "repetida");
                long c = ChunkOrder.pack(p[0] >> 4, p[2] >> 4);
                if (c != last) {
                    assertFalse(finished.contains(c), "volvió a un chunk ya terminado");
                    if (last != Long.MIN_VALUE) finished.add(last);
                    last = c;
                }
                boolean edge = p[0] == b.minX() || p[0] == b.maxX() || p[2] == b.minZ() || p[2] == b.maxZ();
                boolean cap = p[1] == b.minY() || p[1] == b.maxY();
                if (s == Shape.WALLS) assertTrue(edge);
                if (s == Shape.FACES) assertTrue(edge || cap);
            }
            assertEquals(b.count(s), seen.size(), s.name());
        }
        assertEquals(ChunkOrder.chunks(b).length, (37 / 16 - (-20 >> 4) + 1) * (50 / 16 - 0 + 1));
    }

    @Test
    void cuentas_de_formas_finas() {
        Box flat = Box.of(0, 5, 0, 9, 5, 9);
        assertEquals(100, flat.count(Shape.FACES), "una capa: todo es cara");
        assertEquals(36, flat.count(Shape.WALLS));
        Box thin = Box.of(0, 0, 0, 1, 9, 9);
        assertEquals(thin.volume(), thin.count(Shape.WALLS), "dos de ancho: todo es pared");
    }
}
