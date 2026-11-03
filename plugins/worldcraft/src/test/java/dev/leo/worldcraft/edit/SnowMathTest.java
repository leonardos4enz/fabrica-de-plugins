package dev.leo.worldcraft.edit;

import dev.leo.worldcraft.block.StateTable;
import dev.leo.worldcraft.region.Box;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 040 H1 CA2, H2 CA1 y CA2: alisar la nieve en capas y la nieve del pincel. */
class SnowMathTest {
    final StateTable table = new StateTable();
    final int air = table.intern("minecraft:air"), stone = table.intern("minecraft:stone"), glass = table.intern("minecraft:glass");
    final int water = table.intern("minecraft:water[level=0]"), ice = table.intern("minecraft:ice");
    final int[] snow = new int[9];

    {
        for (int i = 1; i <= 8; i++) snow[i] = table.intern("minecraft:snow[layers=" + i + "]");
    }

    final SnowMath.Rules rules = new SnowMath.Rules(s -> s == air, s -> {
        for (int i = 1; i <= 8; i++) if (snow[i] == s) return i;
        return 0;
    }, s -> s == stone, snow, air, s -> s == water, ice);

    /** Piedra en y = 0 en toda la caja de -4..4, aire de 1 a 4. */
    Snapshot flat() {
        Snapshot s = new Snapshot(new Box(-4, 0, -4, 4, 4, 4));
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++)
                for (int y = 0; y <= 4; y++) s.put(x, y, z, y == 0 ? stone : air);
        return s;
    }

    int layersAt(Map<Long, Integer> changes, Snapshot s, int x, int y, int z) {
        Integer c = changes.get(PointsSource.pack(x, y, z));
        int st = c != null ? c : s.get(x, y, z);
        return rules.layers().applyAsInt(st);
    }

    @Test
    void H1_CA2_alisa_un_monton_de_nieve_sin_tocar_el_suelo() {
        Snapshot s = flat();
        s.put(0, 1, 0, snow[8]);
        Map<Long, Integer> out = SnowMath.smooth(s, (x, z) -> true, 1, rules);
        assertTrue(layersAt(out, s, 0, 1, 0) < 8, "el montón baja");
        assertTrue(layersAt(out, s, 1, 1, 0) > 0, "el vecino recibe nieve");
        for (long p : out.keySet()) assertEquals(1, PointsSource.unpackY(p), "solo cambian capas sobre el suelo");
        for (int v : out.values()) assertTrue(v == air || rules.layers().applyAsInt(v) > 0, "solo pone nieve o aire");
    }

    @Test
    void H1_CA2_un_suelo_que_no_aguanta_nieve_no_la_recibe_y_fuera_de_la_forma_no_cambia() {
        Snapshot s = flat();
        s.put(1, 0, 0, glass);
        s.put(0, 1, 0, snow[8]);
        Map<Long, Integer> out = SnowMath.smooth(s, (x, z) -> x <= 1, 2, rules);
        assertFalse(out.containsKey(PointsSource.pack(1, 1, 0)), "sobre vidrio no");
        for (long p : out.keySet()) assertTrue(PointsSource.unpackX(p) <= 1, "fuera de la forma no cambia");
    }

    @Test
    void H1_CA2_nieve_pareja_no_cambia() {
        Snapshot s = flat();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) s.put(x, 1, z, snow[3]);
        assertTrue(SnowMath.smooth(s, (x, z) -> true, 3, rules).isEmpty());
    }

    @Test
    void H2_CA1_el_pincel_de_nieve_y_con_stack_suma_una_capa() {
        Snapshot s = flat();
        s.put(1, 0, 0, water);
        s.put(0, 1, 1, snow[3]);
        Map<Long, Integer> out = SnowMath.fall(s, 0, 0, 0, 2, false, rules);
        assertEquals(snow[1], out.get(PointsSource.pack(0, 1, 0)), "una capa sobre la piedra");
        assertEquals(ice, out.get(PointsSource.pack(1, 0, 0)), "el agua se congela");
        assertFalse(out.containsKey(PointsSource.pack(0, 1, 1)), "sin stack, la nieve que hay se deja");
        assertFalse(out.containsKey(PointsSource.pack(4, 1, 4)), "fuera del círculo nada");
        Map<Long, Integer> stacked = SnowMath.fall(s, 0, 0, 0, 2, true, rules);
        assertEquals(snow[4], stacked.get(PointsSource.pack(0, 1, 1)), "con stack, una capa más");
    }
}
