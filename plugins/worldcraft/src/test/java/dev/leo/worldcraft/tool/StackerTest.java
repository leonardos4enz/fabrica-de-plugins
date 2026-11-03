package dev.leo.worldcraft.tool;

import dev.leo.worldcraft.edit.PointsSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 040 H3 CA5: /wc tool stacker repite el bloque hacia un lado hasta el alcance o el primero que no cumple. */
class StackerTest {
    @Test
    void hasta_el_alcance_o_lo_que_no_cumple() {
        Map<Long, Integer> world = new HashMap<>();
        world.put(PointsSource.pack(0, 5, 0), 1);
        Stacker.At at = (x, y, z) -> world.getOrDefault(PointsSource.pack(x, y, z), 0);
        long[] up = Stacker.points(0, 5, 0, 0, 1, 0, 4, s -> s == 0, at);
        assertArrayEquals(new long[]{PointsSource.pack(0, 6, 0), PointsSource.pack(0, 7, 0), PointsSource.pack(0, 8, 0), PointsSource.pack(0, 9, 0)}, up);
        world.put(PointsSource.pack(2, 5, 0), 7);
        long[] east = Stacker.points(0, 5, 0, 1, 0, 0, 10, s -> s == 0, at);
        assertArrayEquals(new long[]{PointsSource.pack(1, 5, 0)}, east, "para en el primero que no es aire");
        assertEquals(0, Stacker.points(0, 5, 0, 1, 0, 0, 0, s -> true, at).length);
    }
}
