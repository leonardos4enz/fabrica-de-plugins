package dev.leo.omnievents.debug;

import dev.leo.omnievents.util.Durations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 006: presupuesto (H7), perfilador (H6) y exportación de trazas (H5). */
class RendimientoYTrazasTest {

    @Test
    void H7_CA1_budget_acepta_ms_us_y_µs() {
        assertEquals(500_000, Durations.parseBudgetNanos("0.5ms"));
        assertEquals(200_000, Durations.parseBudgetNanos("200us"));
        assertEquals(200_000, Durations.parseBudgetNanos("200 µs"));
        assertEquals(2_000_000, Durations.parseBudgetNanos("2MS"));
        for (String mal : List.of("", "0ms", "1s", "ms", "-1ms", "0.5")) {
            assertThrows(IllegalArgumentException.class, () -> Durations.parseBudgetNanos(mal), mal);
        }
    }

    @Test
    void H7_CA2_budget_calienta_avisa_y_no_repite_hasta_el_intervalo() {
        Budget b = new Budget();
        long budget = 1_000_000; // 1 ms
        // Los primeros disparos (calentamiento) no cuentan aunque sean lentísimos.
        for (int i = 0; i < Budget.WARMUP; i++) assertFalse(b.record(50_000_000, budget, 0, 60_000));
        assertEquals(0, b.recentMillis());
        // Después, rápidos: no avisa.
        for (int i = Budget.WARMUP; i < Budget.MIN_FIRES + 5; i++) assertFalse(b.record(100_000, budget, 0, 60_000));
        assertTrue(b.recentMillis() < 1);
        // Se vuelve lenta: avisa una vez, y no otra vez antes del intervalo.
        int avisos = 0;
        for (int i = 0; i < 30; i++) if (b.record(3_000_000, budget, 1_000, 60_000)) avisos++;
        assertEquals(1, avisos);
        assertTrue(b.recentMillis() > 1);
        assertTrue(b.record(3_000_000, budget, 61_001, 60_000), "pasado el intervalo vuelve a avisar");
    }

    @Test
    void H7_CA2_budget_no_avisa_antes_del_minimo_de_disparos() {
        Budget b = new Budget();
        for (int i = 0; i < Budget.MIN_FIRES - 1; i++) assertFalse(b.record(10_000_000, 1, 0, 0));
        assertTrue(b.warmingUp());
        assertTrue(b.record(10_000_000, 1, 0, 0));
    }

    @Test
    void H6_CA1_apagado_no_mide_nada() {
        Profiler p = new Profiler();
        for (int i = 0; i < 100; i++) assertFalse(p.sample("r", "call", false));
        assertTrue(p.rules().isEmpty());
    }

    @Test
    void H6_CA2_mide_uno_de_cada_n_y_estima_el_porcentaje() {
        Profiler p = new Profiler();
        p.sampleEvery(4);
        p.start();
        int medidos = 0;
        for (int i = 0; i < 40; i++) {
            if (p.sample("r", "block_break", false)) medidos++;
            p.sample("otra", "call", false); // otra regla frecuente no le quita muestras a r
        }
        assertEquals(10, medidos);
        assertTrue(p.rules().isEmpty(), "sin tiempos medidos todavía");
        p.recordRule("r", "block_break", false, 1_000);
        p.recordPart("r", "block_break", false, "conditions[0]", 300);
        p.recordPart("r", "block_break", false, "actions[0]", 700);
        p.recordPart("r", "block_break", false, "actions[0]", 700);
        Profiler.RuleProfile rp = p.rules().get("r");
        assertEquals(1, rp.total().count());
        assertEquals("actions[0]", rp.parts().get(0).getKey(), "la parte más cara primero");
        assertEquals(0.7, rp.parts().get(0).getValue().avgMicros(), 1e-9);
        // 1 ms medido × 4 (muestreo) sobre lo transcurrido.
        double pct = p.mainThreadPercent(1_000_000);
        assertTrue(pct > 0, "porcentaje " + pct);
        p.reset();
        assertTrue(p.rules().isEmpty());
        p.stop();
        assertFalse(p.sample("r", "block_break", false));
    }

    @Test
    void H5_CA1_el_texto_lleva_encabezado_problemas_trazas_y_reglas() {
        Trace t = new Trace("tienda");
        t.add("✘ player.level >= 10 (3 >= 10)");
        Trace s = new Trace("tienda", true);
        s.add("  • tienda.actions[0] message: hola");
        String txt = TraceExport.render(List.of("OmniEvents 1.1.0 · Paper"), List.of("aviso a.yml › x: algo"), List.of(t, s),
                Map.of("tienda", "tienda:\n  trigger: call\n"));
        assertTrue(txt.startsWith("# Traza de OmniEvents\nOmniEvents 1.1.0 · Paper\n"), txt);
        assertTrue(txt.contains("## Problemas de la última carga (1)\naviso a.yml › x: algo"), txt);
        assertTrue(txt.contains("] regla tienda\n  ✘ player.level >= 10 (3 >= 10)"), txt);
        assertTrue(txt.contains("] simulación de tienda\n"), txt);
        assertTrue(txt.contains("# tienda\ntienda:\n  trigger: call\n"), txt);
    }

    @Test
    void H5_CA2_escribe_atomico_y_no_pisa_un_archivo_existente(@TempDir Path dir) throws Exception {
        Path folder = dir.resolve("trazas");
        LocalDateTime now = LocalDateTime.of(2026, 10, 24, 15, 30, 12);
        Path a = TraceExport.write(folder, "uno", now);
        Path b = TraceExport.write(folder, "dos", now);
        assertEquals("2026-10-24_15-30-12.txt", a.getFileName().toString());
        assertEquals("2026-10-24_15-30-12-2.txt", b.getFileName().toString());
        assertEquals("uno", Files.readString(a, StandardCharsets.UTF_8));
        assertEquals("dos", Files.readString(b, StandardCharsets.UTF_8));
        try (var files = Files.list(folder)) {
            assertEquals(2, files.count(), "sin temporales");
        }
    }
}
