package dev.leo.omnievents.data;

import dev.leo.omnievents.expr.Scope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Caducidad, barrido, formato 2 y respaldo de data/ (spec 009, H1, H2 y H4) sobre una carpeta temporal. */
class DataStoreTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Logger log = Logger.getLogger("prueba");

    private DataStore store(File dir) {
        return new DataStore(dir, log, now::get);
    }

    private static String read(File f) throws IOException {
        return Files.readString(f.toPath(), StandardCharsets.UTF_8);
    }

    @Test
    void H1_CA1_caduca_al_leer_y_un_set_sin_expire_la_quita(@TempDir File dir) {
        DataStore s = store(dir);
        DataStore.Holder g = s.global();
        g.put("bono", 2.0, now.get() + 10_000);
        assertEquals(2.0, g.get("bono"));
        assertEquals(10.0, g.ttl("bono"));
        now.addAndGet(10_000);
        assertNull(g.get("bono"), "vencida vale nulo aunque el barrido no haya pasado");
        assertNull(g.ttl("bono"));
        assertTrue(g.live().isEmpty());

        g.put("bono", 3.0, now.get() + 5_000);
        g.put("bono", 4.0, 0);
        now.addAndGet(60_000);
        assertEquals(4.0, g.get("bono"), "un set sin expire quita la caducidad");
        assertEquals(-1.0, g.ttl("bono"));
        assertFalse(g.remove("nada"));
        assertTrue(g.remove("bono"));
    }

    // Regresión (spec 013, F-03): el enfriamiento y los usos de un clic de menú (nombre con puntos) se perdían al
    // volver a cargar: el diario o un kit diario se cobraban de nuevo reconectándose.
    @Test
    void F03_enfriamientos_y_usos_de_reglas_con_puntos_persisten(@TempDir File dir) {
        DataStore s = store(dir);
        UUID id = UUID.randomUUID();
        DataStore.Holder h = s.player(id);
        h.cooldowns().put("demo:principal.menu.items.diario.click", now.get() + 3_600_000);
        h.cooldowns().put("simple", now.get() + 60_000);
        h.uses().put("kits.menu.items.minero.click", new long[]{1, 123});
        h.uses().put("diaria", new long[]{2, 456});
        h.markDirty();
        s.saveAllSync();

        DataStore.Holder again = store(dir).player(id);
        assertEquals(now.get() + 3_600_000, again.cooldowns().get("demo:principal.menu.items.diario.click"));
        assertEquals(now.get() + 60_000, again.cooldowns().get("simple"));
        assertEquals(2, again.cooldowns().size(), again.cooldowns().toString());
        assertEquals(List.of(1L, 123L), java.util.Arrays.stream(again.uses().get("kits.menu.items.minero.click")).boxed().toList());
        assertEquals(List.of(2L, 456L), java.util.Arrays.stream(again.uses().get("diaria")).boxed().toList());
        assertEquals(2, again.uses().size(), again.uses().keySet().toString());
    }

    @Test
    void S014_H4_CA3_reset_de_todos_toca_solo_esas_claves_de_los_desconectados(@TempDir File dir) {
        DataStore s = store(dir);
        UUID offline = UUID.randomUUID(), online = UUID.randomUUID();
        DataStore.Holder off = s.player(offline);
        off.cooldowns().put("kit", now.get() + 60_000);
        off.cooldowns().put("otra", now.get() + 60_000);
        off.uses().put("kit", new long[]{1, 0});
        off.put("monedas", 7.0, 0);
        s.saveAllSync();

        DataStore fresh = store(dir);
        DataStore.Holder on = fresh.player(online); // conectado: lo limpia el hilo principal, no el barrido
        on.cooldowns().put("kit", now.get() + 60_000);
        assertEquals(List.of(offline), fresh.storedPlayers());
        assertEquals(1, fresh.removeUsageOffline(List.of("kit"), true));
        assertEquals(now.get() + 60_000, on.cooldowns().get("kit"), "los cargados no se tocan aquí");
        assertTrue(DataStore.removeUsage(on, List.of("kit"), true));

        DataStore.Holder again = store(dir).player(offline);
        assertNull(again.cooldowns().get("kit"));
        assertEquals(now.get() + 60_000, again.cooldowns().get("otra"), "las demás reglas quedan");
        assertEquals(1, again.uses().size(), "los usos no se tocan al reiniciar el enfriamiento");
        assertEquals(7.0, again.get("monedas"));
        assertEquals(0, store(dir).removeUsageOffline(List.of("kit"), true), "ya no queda nada que cambiar");
    }

    @Test
    void H1_CA3_la_caducidad_persiste_y_lo_vencido_no_vuelve(@TempDir File dir) throws IOException {
        DataStore s = store(dir);
        UUID id = UUID.randomUUID();
        s.player(id).put("racha", 5.0, now.get() + 60_000);
        s.player(id).put("monedas", 10.0, 0);
        s.global().put("evento", "pascua", now.get() + 1_000);
        s.saveAllSync();
        String file = read(new File(dir, "data/players/" + id + ".yml"));
        assertTrue(file.contains("format: 2"), file);
        assertTrue(file.contains("expire:"), file);

        now.addAndGet(30_000);
        DataStore again = store(dir);
        assertEquals(5.0, again.player(id).get("racha"));
        assertEquals(30.0, again.player(id).ttl("racha"));
        assertEquals(10.0, again.player(id).get("monedas"));
        assertNull(again.global().get("evento"), "caducó con el servidor apagado");
        assertFalse(again.global().vars().containsKey("evento"), "se descarta al cargar");
    }

    @Test
    void H2_CA3_el_barrido_devuelve_lo_vencido_y_descarta_entradas_viejas(@TempDir File dir) {
        DataStore s = store(dir);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        s.player(a).put("racha", 3.0, now.get() + 1_000);
        s.player(a).put("racha", 4.0, now.get() + 5_000);   // la entrada de 1 s queda vieja
        s.player(b).put("racha", 1.0, now.get() + 1_000);
        s.global().put("evento", true, now.get() + 2_000);
        s.unload(b, Runnable::run);                          // salió: caduca sin aviso

        assertTrue(s.sweep().isEmpty(), "nada vencido todavía");
        now.addAndGet(2_000);
        List<DataStore.Expired> out = s.sweep();
        assertEquals(1, out.size(), out.toString());
        assertEquals(new DataStore.Expired(Scope.GLOBAL, null, "evento", true), out.get(0));
        assertEquals(4.0, s.player(a).get("racha"), "el set posterior manda");

        now.addAndGet(3_000);
        out = s.sweep();
        assertEquals(List.of(new DataStore.Expired(Scope.PLAYER, a, "racha", 4.0)), out);
        assertFalse(s.player(a).vars().containsKey("racha"));
        assertTrue(s.sweep().isEmpty());
        assertEquals(0, s.pendingExpiries());
    }

    @Test
    void H4_CA1_CA2_el_formato_1_carga_igual_y_se_respalda_una_sola_vez(@TempDir File dir) throws IOException {
        File data = new File(dir, "data");
        new File(data, "players").mkdirs();
        UUID id = UUID.randomUUID();
        // Archivos tal como los escribe la 1.3.0 (formato 1, sin «format» ni «expire»).
        String v1 = "vars:\n  monedas: 50.0\n  rango: vip\ncooldowns:\n  regalo: 9999999999999\nuses:\n  bono:\n    count: 2\n    since: 1\n";
        Files.writeString(new File(data, "players/" + id + ".yml").toPath(), v1, StandardCharsets.UTF_8);
        Files.writeString(new File(data, "global.yml").toPath(), "vars:\n  evento: true\n", StandardCharsets.UTF_8);

        DataStore s = store(dir);
        assertEquals(50.0, s.player(id).get("monedas"));
        assertEquals("vip", s.player(id).get("rango"));
        assertEquals(2, s.player(id).uses().get("bono")[0]);
        assertTrue(s.player(id).cooldowns().containsKey("regalo"));
        assertEquals(true, s.global().get("evento"));

        File[] backups = data.listFiles(f -> f.getName().startsWith("respaldo-formato1-"));
        assertEquals(1, backups.length);
        assertEquals(v1, read(new File(backups[0], "players/" + id + ".yml")), "el respaldo es el original");
        assertTrue(new File(backups[0], "global.yml").exists());
        assertTrue(new File(data, DataStore.MARK).exists());

        s.player(id).put("monedas", 60.0, 0);
        s.saveAllSync();
        assertTrue(read(new File(data, "players/" + id + ".yml")).contains("format: 2"));
        store(dir);
        assertEquals(1, data.listFiles(f -> f.getName().startsWith("respaldo-")).length, "con la marca no se repite");
    }

    @Test
    void H4_CA2_una_instalacion_nueva_no_crea_respaldo(@TempDir File dir) {
        store(dir);
        File data = new File(dir, "data");
        assertEquals(0, data.listFiles(f -> f.getName().startsWith("respaldo-")).length);
        assertTrue(new File(data, DataStore.MARK).exists());
    }

    @Test
    void H4_CA3_un_formato_mas_nuevo_se_lee_pero_no_se_sobrescribe(@TempDir File dir) throws IOException {
        File data = new File(dir, "data");
        data.mkdirs();
        String v3 = "format: 3\nvars:\n  monedas: 7.0\nalgo_nuevo:\n  x: 1\n";
        Files.writeString(new File(data, "global.yml").toPath(), v3, StandardCharsets.UTF_8);
        DataStore s = store(dir);
        assertEquals(7.0, s.global().get("monedas"));
        assertTrue(s.global().readOnly());
        s.global().put("monedas", 8.0, 0);
        s.saveAllSync();
        assertEquals(v3, read(new File(data, "global.yml")));
    }

    @Test
    void T_una_caducidad_que_no_es_numero_deja_la_variable_sin_caducar(@TempDir File dir) throws IOException {
        File data = new File(dir, "data");
        data.mkdirs();
        Files.writeString(new File(data, "global.yml").toPath(), "format: 2\nvars:\n  a: 1.0\nexpire:\n  a: mañana\n", StandardCharsets.UTF_8);
        DataStore s = store(dir);
        assertEquals(1.0, s.global().get("a"));
        assertEquals(-1.0, s.global().ttl("a"));
    }
}
