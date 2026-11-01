package dev.leo.skinstatues.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Spec 022 H3: lo que sigue a /statue build <name> <height>. */
class BuildArgsTest {
    private static BuildArgs.Parsed parse(String line) {
        String[] args = ("build Example 32 " + line).trim().split(" +");
        return BuildArgs.parse(args, 3, true, true);
    }

    private static BuildArgs ok(String line) {
        BuildArgs.Parsed p = parse(line);
        assertNull(p.error(), () -> line + " → " + p.error().key());
        return p.args();
    }

    private static void fails(String line, String key) {
        BuildArgs.Parsed p = parse(line);
        assertNotNull(p.error(), line);
        assertEquals(key, p.error().key(), line);
    }

    @Test
    void H3_CA3_sin_opciones_todo_como_antes() {
        BuildArgs b = ok("");
        assertNull(b.pose());
        assertNull(b.palette());
        assertFalse(b.hasAt());
        assertNull(b.facing());
        assertNull(b.world());
        assertTrue(b.overlay() && b.cape());
        BuildArgs w = ok("waving --palette Wool --no-cape --no-overlay");
        assertEquals("waving", w.pose());
        assertEquals("wool", w.palette());
        assertFalse(w.overlay() || w.cape());
    }

    @Test
    void H3_CA1_at_absoluto_relativo_y_con_decimales() {
        BuildArgs b = ok("--at 100 64 -20");
        assertFalse(b.relative());
        assertEquals(100, b.at()[0].resolve(5));
        assertEquals(64, b.at()[1].resolve(5));
        assertEquals(-20, b.at()[2].resolve(5));
        BuildArgs r = ok("t_pose --at ~ ~5 ~-3");
        assertEquals("t_pose", r.pose(), "la pose puede ir antes");
        assertTrue(r.relative());
        assertEquals(10, r.at()[0].resolve(10.7), "~ es el bloque de quien construye");
        assertEquals(69, r.at()[1].resolve(64));
        assertEquals(-4, r.at()[2].resolve(-0.5), "−0,5 − 3 = −3,5 → bloque −4");
        assertEquals(-5, ok("--at -4.5 0 .5").at()[0].resolve(0), "un decimal se redondea hacia abajo al bloque");
        assertEquals("sitting", ok("--at 1 2 3 sitting").pose(), "o después");
        assertTrue(ok("--at 1 ~ 3").relative(), "una sola relativa basta");
    }

    @Test
    void H3_CA2_facing_y_world() {
        assertEquals(180, ok("--facing North").facing());
        assertEquals(0, ok("--facing south").facing());
        assertEquals(90, ok("--facing west").facing());
        assertEquals(270, ok("--facing east").facing());
        assertEquals("world_nether", ok("--world world_nether --at 0 70 0").world(), "el nombre del mundo tal cual");
    }

    @Test
    void H3_CA5_errores_con_su_mensaje() {
        fails("--at 1 2", "missing-coords");
        fails("--at", "missing-coords");
        fails("--at 1 dos 3", "invalid-coord");
        fails("--at 1 NaN 3", "invalid-coord");
        fails("--at Infinity 2 3", "invalid-coord");
        fails("--at 5d 2 3", "invalid-coord");
        fails("--at 1 2 99999999", "invalid-coord");
        fails("--at ~~ 2 3", "invalid-coord");
        fails("--at 1 2 3 --facing up", "invalid-facing");
        fails("--facing norht", "invalid-facing");
        assertTrue(parse("--facing norht").error().args()[3].toString().contains("north"), "con «did you mean»");
        fails("--world", "usage-build");
        fails("--palette", "usage-build");
        fails("--en 1 2 3", "unknown-flag");
        assertTrue(parse("--wrld x").error().args()[3].toString().contains("--world"));
        fails("waving victory", "usage-build");
    }
}
