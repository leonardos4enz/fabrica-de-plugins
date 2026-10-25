package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.data.PackVersions;
import dev.leo.omnievents.trigger.Triggers;
import dev.leo.omnievents.util.Versions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 011: paquetes, plantillas y versión. */
class PaquetesTest {
    @TempDir
    File dir;
    private RuleLoader loader = new RuleLoader(new Actions(null), new Triggers(), "1.6.0");
    private final Problems p = new Problems();

    private void write(String rel, String content) throws IOException {
        File f = new File(dir, rel);
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
    }

    private Map<String, Rule> load() {
        return loader.loadFolder(dir, p);
    }

    private String dump() {
        return p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    private boolean has(String text) {
        return p.all().stream().anyMatch(x -> x.toString().contains(text));
    }

    /** Corre las pruebas (tests:) de todas las reglas cargadas y devuelve las fallas. */
    private static String failures(Map<String, Rule> rules) {
        RuleTester.Summary s = new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16).runAll(rules.values());
        return s.results().stream().filter(r -> !r.ok()).map(r -> r.rule().name() + " «" + r.test().name() + "»: " + r.failures()
                + "\n  " + String.join("\n  ", r.trace())).collect(Collectors.joining("\n"));
    }

    // ------------------------------------------------------------------ H1: paquetes

    @Test
    void H1_CA1_una_carpeta_con_pack_yml_da_nombres_paquete_regla() throws IOException {
        write("tienda/pack.yml", "version: 1.2.0\n");
        write("tienda/sub/reglas.yml", "diaria:\n  trigger: player_join\n  actions: [\"message: a\"]\n");
        write("sueltas/reglas.yml", "diaria:\n  trigger: player_join\n  actions: [\"message: b\"]\n");
        Map<String, Rule> rules = load();
        assertEquals(0, p.size(), dump());
        assertEquals("tienda:diaria", rules.get("tienda:diaria").name());
        assertEquals("tienda", rules.get("tienda:diaria").pack());
        assertEquals("diaria", rules.get("diaria").name());
        assertNull(rules.get("diaria").pack());
        Pack pack = loader.packs().get(0);
        assertEquals("tienda", pack.name());
        assertEquals("1.2.0", pack.version());
        assertEquals(List.of("tienda:diaria"), pack.rules());
    }

    @Test
    void H1_CA2_los_nombres_sin_paquete_buscan_primero_en_el_paquete() throws IOException {
        write("a/pack.yml", "name: a\n");
        write("a/r.yml", """
                dar:
                  trigger: command
                  command: dar_a
                  actions: ["call: avisar", "call: b:avisar", "call: solo_global"]
                  tests:
                    - given: { player.name: Ana }
                      expect: { messages: ["avisar de a", "avisar de b", "global"] }
                avisar:
                  trigger: call
                  actions: ["message: avisar de a"]
                """);
        write("b/pack.yml", "name: b\n");
        write("b/r.yml", "avisar:\n  trigger: call\n  actions: [\"message: avisar de b\"]\n");
        write("g.yml", """
                avisar:
                  trigger: call
                  actions: ["message: avisar global"]
                solo_global:
                  trigger: call
                  actions: ["message: global"]
                """);
        Map<String, Rule> rules = load();
        assertEquals(0, p.size(), dump());
        assertEquals("", failures(rules));
        // Desde una regla global, avisar es la global; desde a, la de a.
        java.util.function.Function<String, Rule> get = n -> rules.get(n.toLowerCase());
        assertSame(rules.get("avisar"), Rule.resolve("avisar", rules.get("solo_global"), get));
        assertSame(rules.get("a:avisar"), Rule.resolve("avisar", rules.get("a:dar"), get));
        assertSame(rules.get("b:avisar"), Rule.resolve("b:avisar", rules.get("a:dar"), get));
        assertSame(rules.get("solo_global"), Rule.resolve("solo_global", rules.get("a:dar"), get));
    }

    @Test
    void H1_CA2_in_state_relativo_en_un_paquete() throws IOException {
        write("a/pack.yml", "");
        write("a/r.yml", """
                herido:
                  trigger: state
                  when: "player.health <= 6"
                  on_enter: ["message: herido"]
                aviso:
                  trigger: player_join
                  conditions: ["in_state('herido')"]
                  actions: ["message: hola"]
                """);
        write("g.yml", "otra:\n  trigger: player_join\n  conditions: [\"in_state('herido')\"]\n  actions: [\"message: x\"]\n");
        Map<String, Rule> rules = load();
        assertNotNull(rules.get("a:aviso"), dump());
        // Desde una regla global, 'herido' no existe: es a:herido.
        assertNull(rules.get("otra"));
        assertTrue(has("in_state('herido'): no hay una regla state"), dump());
    }

    @Test
    void H1_CA3_errores_de_pack_yml_con_ruta() throws IOException {
        write("uno/pack.yml", "nmae: x\nversion: uno\n");
        write("uno/r.yml", "r:\n  trigger: player_join\n  actions: [\"message: a\"]\n");
        write("Dos Grande/pack.yml", "");
        write("tres/pack.yml", "name: cuatro\n");
        write("cuatro/pack.yml", "");
        write("cuatro/dentro/pack.yml", "");
        write("cinco/pack.yml", "requires: { omnievents: \"mayor\", otra: 1 }\nparams: [1]\nmigrations: { \"x\": [] }\n");
        Map<String, Rule> rules = load();
        assertTrue(rules.isEmpty(), rules.keySet().toString());
        assertTrue(has("uno/pack.yml › nmae: clave desconocida en pack.yml (¿quisiste decir 'name'?)"), dump());
        assertTrue(has("uno/pack.yml › version: versión inválida 'uno'"), dump());
        assertTrue(has("uno/pack.yml: el paquete no carga: sus reglas se omiten"), dump());
        assertTrue(has("nombre de paquete inválido 'dos grande'"), dump());
        assertTrue(has("tres/pack.yml › name: ya hay un paquete 'cuatro' en cuatro"), dump());
        assertTrue(has("cuatro/dentro/pack.yml: un paquete no puede estar dentro de otro"), dump());
        assertTrue(has("cinco/pack.yml › requires.omnievents: restricción inválida 'mayor'"), dump());
        assertTrue(has("cinco/pack.yml › requires.otra: clave desconocida"), dump());
        assertTrue(has("cinco/pack.yml › params: params es un mapa"), dump());
        assertTrue(has("cinco/pack.yml › migrations.x: versión inválida"), dump());
    }

    @Test
    void H1_CA3_dos_puntos_en_el_nombre() throws IOException {
        write("a/pack.yml", "");
        write("a/r.yml", "b:c:\n  trigger: player_join\n  actions: [\"message: a\"]\n");
        write("g.yml", "x:y:\n  trigger: player_join\n  actions: [\"message: a\"]\n");
        Map<String, Rule> rules = load();
        assertNull(rules.get("a:b:c"));
        assertTrue(has("dentro del paquete a el nombre va sin paquete"), dump());
        // Fuera de un paquete solo avisa y carga como antes (IV.5).
        assertNotNull(rules.get("x:y"));
        assertTrue(p.all().stream().anyMatch(x -> !x.error() && x.message().contains("se reservan para las reglas de un paquete")), dump());
    }

    @Test
    void H1_CA1_enabled_false_carga_las_reglas_desactivadas() throws IOException {
        write("a/pack.yml", "enabled: false\n");
        write("a/r.yml", "r:\n  trigger: player_join\n  actions: [\"message: a\"]\n");
        Map<String, Rule> rules = load();
        assertFalse(rules.get("a:r").enabled());
        assertFalse(loader.packs().get(0).enabled());
    }

    // ------------------------------------------------------------------ H2: plantillas

    @Test
    void H2_CA1_CA2_herencia_en_cadena_y_reemplazo_entero() throws IOException {
        write("r.yml", """
                raiz:
                  template: true
                  trigger: player_join
                  cooldown: 10s
                  actions: ["message: raiz"]
                  tests:
                    - expect: { fired: true }
                medio:
                  template: true
                  extends: raiz
                  conditions: ["player.level > 1"]
                hoja:
                  extends: medio
                  actions: ["message: hoja"]
                """);
        Map<String, Rule> rules = load();
        assertEquals(0, p.size(), dump());
        assertEquals(1, rules.size(), "las plantillas no corren solas");
        Rule r = rules.get("hoja");
        assertEquals(10_000, r.cooldownMs());
        assertEquals(1, r.conditions().size());
        assertEquals("message", r.actions().get(0).type());
        assertTrue(r.tests().isEmpty(), "tests no se hereda");
    }

    @Test
    void H2_CA2_errores_de_herencia() throws IOException {
        write("r.yml", """
                base:
                  template: true
                  trigger: player_join
                normal:
                  trigger: player_join
                  actions: ["message: a"]
                a:
                  extends: bsae
                b:
                  extends: normal
                c1:
                  template: true
                  extends: c2
                c2:
                  template: true
                  extends: c1
                c:
                  extends: c1
                """);
        Map<String, Rule> rules = load();
        assertEquals(List.of("normal"), List.copyOf(rules.keySet()));
        assertTrue(has("a.extends: no existe la plantilla 'bsae' (¿quisiste decir 'base'?)"), dump());
        assertTrue(has("b.extends: 'normal' no es una plantilla: márcala con template: true"), dump());
        assertTrue(has("herencia en ciclo: c → c1 → c2 → c1"), dump());
    }

    @Test
    void H2_CA2_tope_de_niveles() throws IOException {
        StringBuilder sb = new StringBuilder("t0:\n  template: true\n  trigger: player_join\n  actions: [\"message: a\"]\n");
        for (int i = 1; i <= 9; i++) sb.append("t").append(i).append(":\n  template: true\n  extends: t").append(i - 1).append('\n');
        sb.append("hoja:\n  extends: t9\n");
        write("r.yml", sb.toString());
        load();
        assertTrue(has("más de 8 niveles de herencia"), dump());
    }

    @Test
    void H2_CA3_parametros_con_prioridad_tipo_y_escape() throws IOException {
        write("tienda/pack.yml", "params: { moneda: pvar.oro, cantidad: 1, saludo: hola }\n");
        write("tienda/r.yml", """
                base:
                  template: true
                  params: { cantidad: 2, espera: 30s }
                  trigger: command
                  cooldown: ${espera}
                  actions:
                    - set: "${moneda} += ${cantidad}"
                    - message: "${saludo}: {${moneda}} oro"
                    - set: "pvar.texto = '\\\\${literal}'"
                dar:
                  extends: base
                  with: { cantidad: 5 }
                  command: dar_oro
                  tests:
                    - given: { player.name: Ana, pvar.oro: 1 }
                      expect: { pvar.oro: 6, messages: ["hola: 6 oro"], pvar.texto: "\\\\${literal}" }
                dar_defecto:
                  extends: base
                  command: dar_oro2
                  tests:
                    - given: { player.name: Ana, pvar.oro: 1 }
                      expect: { pvar.oro: 3 }
                """);
        Map<String, Rule> rules = load();
        assertEquals(0, p.size(), dump());
        assertEquals(30_000, rules.get("tienda:dar").cooldownMs(), "un texto que es solo ${x} toma el valor");
        assertEquals("", failures(rules));
    }

    @Test
    void H2_CA3_with_usa_los_params_del_paquete() throws IOException {
        write("t/pack.yml", "params: { diaria: 50 }\n");
        write("t/r.yml", """
                base:
                  template: true
                  trigger: call
                  actions: ["set: pvar.x = ${cantidad}"]
                d:
                  extends: base
                  with: { cantidad: "${diaria}" }
                  tests: [{ expect: { pvar.x: 50 } }]
                """);
        Map<String, Rule> rules = load();
        assertEquals(0, p.size(), dump());
        assertEquals("", failures(rules));
    }

    @Test
    void H2_CA3_parametro_desconocido_con_ruta_y_sugerencia() throws IOException {
        write("t/pack.yml", "params: { cantidad: 1 }\n");
        write("t/r.yml", "r:\n  trigger: player_join\n  actions:\n    - message: \"a\"\n    - message: \"${cantidda}\"\n");
        Map<String, Rule> rules = load();
        assertTrue(rules.isEmpty());
        assertTrue(has("t/r.yml › t:r.actions[1].message: parámetro desconocido ${cantidda} (¿quisiste decir 'cantidad'?)"), dump());
    }

    @Test
    void H2_CA4_una_regla_global_sin_extends_no_cambia() throws IOException {
        write("g.yml", "r:\n  trigger: call\n  actions: [\"set: pvar.t = 'cuesta ${precio}'\"]\n  tests: [{ expect: { pvar.t: \"cuesta ${precio}\" } }]\n"
                + "w:\n  trigger: call\n  with: { a: 1 }\n  actions: [\"message: x\"]\n");
        Map<String, Rule> rules = load();
        assertEquals(1, p.size(), dump());
        assertTrue(has("w.with: with solo se usa con extends"), dump());
        assertEquals("", failures(rules));
    }

    // ------------------------------------------------------------------ H3: versión, requisitos y migraciones

    @Test
    void H3_CA1_versiones_y_restricciones() {
        assertTrue(Versions.satisfies("1.6.0", ">=1.6.0"));
        assertTrue(Versions.satisfies("1.10.0", ">1.9.9, <2.0"));
        assertFalse(Versions.satisfies("2.0.0", ">=1.0, <2.0"));
        assertTrue(Versions.satisfies("1.2", "=1.2.0"));
        assertTrue(Versions.satisfies("1.2.0", "1.2.0"));
        assertFalse(Versions.satisfies("1.2.1", "<=1.2.0"));
        assertTrue(Versions.compare("1.10.0", "1.9.0") > 0, "se compara como número, no como texto");
        assertThrows(IllegalArgumentException.class, () -> Versions.constraint(">= uno"));
        assertThrows(IllegalArgumentException.class, () -> Versions.parse("1"));
    }

    @Test
    void H3_CA1_requires_hasta_un_punto_fijo() throws IOException {
        write("base/pack.yml", "version: 1.0.0\n");
        write("base/r.yml", "r:\n  trigger: call\n  actions: [\"message: a\"]\n");
        write("tienda/pack.yml", "requires: { packs: { base: \">=2.0.0\" } }\n");
        write("tienda/r.yml", "r:\n  trigger: call\n  actions: [\"message: a\"]\n");
        write("extra/pack.yml", "requires: { packs: { tienda: \">=1.0\" } }\n");
        write("nuevo/pack.yml", "requires: { omnievents: \">=9.0.0\" }\n");
        write("falta/pack.yml", "requires: { packs: { bse: \"1.0.0\" } }\n");
        Map<String, Rule> rules = load();
        assertEquals(List.of("base:r"), List.copyOf(rules.keySet()), dump());
        assertTrue(has("tienda/pack.yml › requires: necesita el paquete base >=2.0.0 y hay 1.0.0: el paquete no carga"), dump());
        assertTrue(has("extra/pack.yml › requires: necesita el paquete tienda, que no carga"), dump());
        assertTrue(has("nuevo/pack.yml › requires: necesita OmniEvents >=9.0.0 y este es 1.6.0"), dump());
        assertTrue(has("necesita el paquete bse 1.0.0 y no está (¿quisiste decir 'base'?)"), dump());
    }

    @Test
    void H3_CA2_migraciones_que_corren_y_version_guardada(@TempDir File data) throws IOException {
        write("t/pack.yml", """
                version: 1.3.0
                migrations:
                  "1.3.0": ["log: tres"]
                  "1.1.0": ["log: uno"]
                  "1.2.0": ["log: dos", "set: gvar.m = var.from + ' a ' + var.to"]
                  "2.0.0": ["log: futuro"]
                """);
        load();
        assertEquals(0, p.size(), dump());
        Pack pack = loader.packs().get(0);
        assertEquals(List.of("1.1.0", "1.2.0", "1.3.0", "2.0.0"), pack.migrations().stream().map(Pack.Migration::version).toList(), "en orden de versión");
        assertEquals("t", pack.migrations().get(0).rule().pack(), "una migración busca nombres desde su paquete");

        PackVersions v = new PackVersions(data, null);
        v.load();
        List<PackVersions.Step> first = v.plan(List.of(pack));
        assertEquals(1, first.size());
        assertNull(first.get(0).from());
        assertTrue(first.get(0).migrations().isEmpty(), "la primera vez solo se guarda la versión");
        v.record(first.get(0));
        v.save();

        Files.writeString(new File(data, "data/packs.yml").toPath(), "packs:\n  t: 1.1.0\n", StandardCharsets.UTF_8);
        v.load();
        PackVersions.Step up = v.plan(List.of(pack)).get(0);
        assertEquals(List.of("1.2.0", "1.3.0"), up.migrations().stream().map(Pack.Migration::version).toList(), "guardada < v <= nueva");
        v.record(up);
        v.save();
        v.load();
        assertEquals("1.3.0", v.get("t"));
        assertTrue(v.plan(List.of(pack)).isEmpty(), "misma versión: nada");

        Files.writeString(new File(data, "data/packs.yml").toPath(), "packs:\n  t: 1.5.0\n", StandardCharsets.UTF_8);
        v.load();
        PackVersions.Step down = v.plan(List.of(pack)).get(0);
        assertTrue(down.migrations().isEmpty(), "bajar de versión no corre nada");
        v.record(down);
        assertEquals("1.5.0", v.get("t"), "ni cambia lo guardado");

        Files.writeString(new File(data, "data/packs.yml").toPath(), "packs: [roto\n", StandardCharsets.UTF_8);
        v.load();
        assertTrue(v.plan(List.of(pack)).get(0).migrations().isEmpty(), "un archivo roto es vacío: no corre migraciones de más");
    }

    @Test
    void H3_CA2_un_paquete_apagado_no_migra(@TempDir File data) throws IOException {
        write("t/pack.yml", "enabled: false\nversion: 2.0.0\n");
        load();
        PackVersions v = new PackVersions(data, null);
        v.load();
        assertTrue(v.plan(loader.packs()).isEmpty());
    }

    @Test
    void H3_CA2_migracion_con_error_es_error_del_paquete() throws IOException {
        write("t/pack.yml", "migrations:\n  \"1.1.0\": [\"mesage: hola\"]\n");
        write("t/r.yml", "r:\n  trigger: call\n  actions: [\"message: a\"]\n");
        Map<String, Rule> rules = load();
        assertTrue(rules.isEmpty());
        assertTrue(has("t/pack.yml › migrations.1.1.0.actions[0]"), dump());
    }

    // ------------------------------------------------------------------ H5: el ejemplo

    @Test
    void H5_CA2_el_paquete_de_ejemplo_carga_y_sus_pruebas_pasan() {
        loader = new RuleLoader(new Actions(null), new Triggers(), "1.6.0");
        Map<String, Rule> rules = loader.loadFolder(new File("src/main/resources/rules"), p);
        assertEquals(0, p.size(), dump());
        Pack pack = loader.packs().stream().filter(x -> x.name().equals("recompensas")).findFirst().orElseThrow();
        assertEquals(List.of("recompensas:base"), pack.templates());
        assertTrue(pack.rules().containsAll(List.of("recompensas:diaria", "recompensas:semanal", "recompensas:avisar")), pack.rules().toString());
        assertEquals(86_400_000, rules.get("recompensas:diaria").cooldownMs());
        assertEquals("", failures(rules));
    }
}
