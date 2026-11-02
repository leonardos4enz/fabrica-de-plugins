package dev.leo.worldcraft.region;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 028 H4: dónde puede pararse un jugador (plan §1.4). */
class SpotsTest {
    /** Piso en y = 63 (se para en 64), un segundo piso en y = 70 (se para en 71) y una pared en x = 3. */
    final Set<String> solid = new HashSet<>();
    final Spots.World w = new Spots.World() {
        @Override
        public boolean passable(int x, int y, int z) {
            return !solid.contains(x + "," + y + "," + z);
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 319;
        }
    };

    SpotsTest() {
        for (int x = -5; x <= 10; x++) {
            solid.add(x + ",63,0");
            solid.add(x + ",70,0");
        }
        for (int y = 64; y <= 69; y++) solid.add("3," + y + ",0");
    }

    @Test
    void H4_CA1_ascend_y_descend_entre_pisos() {
        assertEquals(71, Spots.ascend(w, 0, 64, 0, 1));
        assertNull(Spots.ascend(w, 0, 71, 0, 1), "no hay otro piso arriba");
        assertEquals(64, Spots.descend(w, 0, 71, 0, 1));
        assertEquals(71, Spots.ascend(w, 0, 64, 0, 5), "si hay menos pisos, el último");
    }

    @Test
    void H4_CA1_ceil_unstuck_y_jumpto() {
        assertEquals(68, Spots.ceil(w, 0, 64, 0, 0), "la cabeza justo bajo el techo de y = 70");
        assertEquals(67, Spots.ceil(w, 0, 64, 0, 1));
        assertNull(Spots.ceil(w, 0, 71, 0, 0), "sin techo");
        assertEquals(64, Spots.unstuck(w, 0, 63, 0), "dentro del piso: arriba");
        assertEquals(64, Spots.onTop(w, 1, 63, 0));
    }

    @Test
    void H4_CA1_thru_atraviesa_la_pared_y_el_techo() {
        assertArrayEquals(new int[]{4, 64, 0}, Spots.thru(w, 0, 64, 0, 1, 0, 0, 32));
        assertNull(Spots.thru(w, 0, 64, 0, -1, 0, 0, 32), "sin pared en esa dirección");
        assertArrayEquals(new int[]{0, 71, 0}, Spots.thru(w, 0, 64, 0, 0, 1, 0, 32), "hacia arriba, al otro lado del techo");
    }

    @Test
    void H4_CA2_up_no_atraviesa_techos() {
        assertEquals(68, Spots.up(w, 0, 64, 0, 4));
        assertNull(Spots.up(w, 0, 64, 0, 6), "la cabeza chocaría con el techo");
    }
}
