package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 010: pruebas de reglas en el YAML (tests:, RuleTester). Sin servidor: plugin es null en todo el recorrido. */
class PruebasReglasTest {
    private final RuleLoader loader = new RuleLoader(new Actions(null), new Triggers());

    private static String dump(Problems p) {
        return p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    /** Carga todas las reglas del YAML (en un mapa, para que call las encuentre). */
    private Map<String, Rule> load(String yaml, Problems p) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        Map<String, Rule> out = new LinkedHashMap<>();
        for (String name : y.getKeys(false)) {
            Rule r = loader.parse(name, "prueba.yml", y.getConfigurationSection(name), p.file("prueba.yml"));
            if (r != null) out.put(name.toLowerCase(), r);
        }
        return out;
    }

    private static RuleTester tester(Map<String, Rule> rules) {
        return new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16);
    }

    private static String report(List<RuleTester.Result> res) {
        return res.stream().map(r -> r.test().name() + ": " + r.failures() + "\n  " + String.join("\n  ", r.trace())).collect(Collectors.joining("\n"));
    }

    private static final String DIAMANTES = """
            diamantes:
              trigger: block_break
              conditions:
                - "block.type == 'DIAMOND_ORE'"
                - expr: "player.level >= 5"
                  deny: ["message: <red>Necesitas nivel 5 (tienes {player.level})"]
              actions:
                - set: "pvar.diamantes += 1"
                - message: "<aqua>Llevas {pvar.diamantes} diamantes"
                - console: "give {player.name} emerald 1"
                - sound: "ENTITY_PLAYER_LEVELUP;1;1"
              else: ["cancel"]
              tests:
                - name: suma uno
                  given: {player.name: Ana, player.level: 12, block.type: DIAMOND_ORE, pvar.diamantes: 9}
                  expect:
                    fired: true
                    branch: actions
                    pvar.diamantes: 10
                    messages: ["llevas 10 diamantes"]
                    commands: ["give Ana emerald 1"]
                    actions: [message, console, "sound: LEVELUP"]
                    cancelled: false
                    assert: ["pvar.diamantes >= 10"]
                - name: falta nivel
                  given: {player.level: 2, block.type: DIAMOND_ORE}
                  expect: {fired: false, branch: "deny conditions[1]", messages: ["Necesitas nivel 5 (tienes 2)"], pvar.diamantes: null}
                - name: otra piedra
                  given: {block.type: STONE}
                  expect: {fired: false, branch: else, cancelled: true}
            """;

    @Test
    void H1_CA1_H2_H3_CA1_casos_que_pasan() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load(DIAMANTES, p);
        assertEquals(0, p.size(), dump(p));
        Rule r = rules.get("diamantes");
        assertEquals(3, r.tests().size());
        List<RuleTester.Result> res = tester(rules).run(r);
        assertTrue(res.stream().allMatch(RuleTester.Result::ok), report(res));
        assertEquals("suma uno", res.get(0).test().name());
    }

    @Test
    void H3_CA2_un_caso_que_falla_dice_que_esperaba_y_que_salio() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load(DIAMANTES.replace("pvar.diamantes: 10", "pvar.diamantes: 11")
                .replace("[\"llevas 10 diamantes\"]", "[\"llevas 11\"]"), p);
        RuleTester.Result first = tester(rules).run(rules.get("diamantes")).get(0);
        assertFalse(first.ok());
        String f = String.join("\n", first.failures());
        assertTrue(f.contains("pvar.diamantes: esperaba 11, dio 10"), f);
        assertTrue(f.contains("messages: no salió «llevas 11»") && f.contains("Llevas 10 diamantes"), f);
        assertTrue(first.trace().stream().anyMatch(l -> l.contains("block.type")), String.join("\n", first.trace()));
    }

    @Test
    void H1_CA3_errores_de_forma_con_ruta_y_sugerencia_sin_tumbar_la_regla() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                r:
                  trigger: block_break
                  actions: ["message: hola"]
                  tests:
                    - given: {blok.type: STONE}
                      expect: {fired: true}
                    - expect: {mensajes: [hola], fired: si}
                    - given: [1]
                    - expect: {branch: on_enter, actions: [mesage], assert: "1 +"}
                    - name: bueno
                      expect: {fired: true, messages: hola}
                    - gvien: {}
                      expect: {}
                """, p);
        Rule r = rules.get("r");
        assertNotNull(r, dump(p));
        assertEquals(1, r.tests().size(), dump(p));
        assertEquals("bueno", r.tests().get(0).name());
        String d = dump(p);
        assertTrue(d.contains("r.tests[0].given.blok.type") && d.contains("¿quisiste decir 'block'?"), d);
        assertTrue(d.contains("r.tests[1].expect.mensajes") && d.contains("¿quisiste decir 'messages'?"), d);
        assertTrue(d.contains("r.tests[1].expect.fired: va true o false"), d);
        assertTrue(d.contains("r.tests[2].given"), d);
        assertTrue(d.contains("r.tests[3].expect.branch") && d.contains("acción desconocida 'mesage'") && d.contains("r.tests[3].expect.assert"), d);
        assertTrue(d.contains("r.tests[5].gvien") && d.contains("¿quisiste decir 'given'?"), d);
        assertTrue(tester(rules).run(r).get(0).ok());
    }

    @Test
    void H3_CA1_flujo_call_random_y_datos_de_objetos() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                cofre:
                  trigger: player_interact
                  conditions: ["block.data.dueño == null || block.data.dueño == player.name"]
                  actions:
                    - set: "block.data.dueño = player.name"
                    - repeat: 3
                      do: [set: "var.n = default(var.n, 0) + 1"]
                    - if: "var.n == 3"
                      then: [call: "premio; cuanto=5"]
                    - random:
                        - do: [set: "gvar.rama = 'a'"]
                        - do: [set: "gvar.rama = 'b'"]
                    - delay: 5s
                    - message: "después del delay"
                      chance: 10
                  else: ["message: Este cofre es de {block.data.dueño}"]
                  tests:
                    - given: {player.name: Ana, block.type: CHEST}
                      seed: 7
                      expect: {block.data.dueño: Ana, var.n: 3, pvar.monedas: 5, messages: ["premio de 5", "después del delay"], assert: "gvar.rama in ['a', 'b']"}
                    - given: {player.name: Ana, block.type: CHEST, block.data.dueño: Leo}
                      expect: {branch: else, messages: ["es de Leo"], block.data.dueño: Leo}
                premio:
                  trigger: call
                  actions:
                    - set: "pvar.monedas += var.cuanto"
                    - message: "premio de {var.cuanto} para {var.caller}"
                """, p);
        assertEquals(0, p.errors(), dump(p));
        List<RuleTester.Result> res = tester(rules).run(rules.get("cofre"));
        assertTrue(res.stream().allMatch(RuleTester.Result::ok), report(res));
        // La misma semilla elige siempre la misma rama (H3 CA1).
        Rule cofre = rules.get("cofre");
        String a = tester(rules).run(cofre).get(0).trace().stream().filter(l -> l.contains("random")).findFirst().orElse("");
        String b = tester(rules).run(cofre).get(0).trace().stream().filter(l -> l.contains("random")).findFirst().orElse("");
        assertEquals(a, b);
        assertFalse(a.isEmpty());
    }

    @Test
    void H3_CA1_un_bucle_sin_fin_o_un_error_es_una_falla_y_no_congela() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                bucle:
                  trigger: call
                  actions:
                    - while: "true"
                      do: [set: "var.x = 1"]
                  tests:
                    - expect: {fired: true}
                error:
                  trigger: call
                  actions: ["set: pvar.x = 1 / 0"]
                  tests:
                    - expect: {fired: true}
                """, p);
        RuleTester t = tester(rules);
        RuleTester.Result loop = t.run(rules.get("bucle")).get(0);
        assertFalse(loop.ok());
        assertTrue(String.join("\n", loop.failures()).contains("tope"), loop.failures().toString());
        RuleTester.Result err = t.run(rules.get("error")).get(0);
        assertFalse(err.ok(), err.failures().toString());
    }

    @Test
    void H4_CA3_una_regla_state_se_prueba_con_su_when() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                vida_baja:
                  trigger: state
                  when: "player.health <= 6"
                  on_enter: ["actionbar: <red>¡Vida baja! ({player.health})"]
                  on_exit: ["actionbar: a salvo"]
                  tests:
                    - given: {player.health: 4}
                      expect: {fired: true, branch: on_enter, messages: ["vida baja! (4)"]}
                    - given: {player.health: 20}
                      expect: {fired: false, branch: none}
                """, p);
        assertEquals(0, p.errors(), dump(p));
        List<RuleTester.Result> res = tester(rules).run(rules.get("vida_baja"));
        assertTrue(res.stream().allMatch(RuleTester.Result::ok), report(res));
    }

    @Test
    void H3_CA1_placeholders_permisos_y_destinos_no_tocan_el_servidor() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                vip:
                  trigger: player_join
                  permission: omni.vip
                  cooldown: 1h
                  conditions: ["%vault_eco_balance% >= 100"]
                  actions:
                    - broadcast: "{player.name} tiene %vault_eco_balance%"
                      to: all
                    - for_each: all
                      do: ["message: nunca"]
                    - give_item: "DIAMOND;1"
                  tests:
                    - given: {player.name: Ana, "%vault_eco_balance%": 150}
                      expect: {fired: true, messages: ["Ana tiene 150"], actions: ["broadcast: all", "give_item: DIAMOND"]}
                """, p);
        assertEquals(0, p.errors(), dump(p));
        RuleTester.Result r = tester(rules).run(rules.get("vip")).get(0);
        assertTrue(r.ok(), report(List.of(r)));
        assertTrue(r.trace().stream().anyMatch(l -> l.contains("la prueba no revisa permission, cooldown")), String.join("\n", r.trace()));
        assertTrue(r.trace().stream().noneMatch(l -> l.contains("nunca")), String.join("\n", r.trace()));
    }

    @Test
    void F01_una_constante_de_given_se_compara_como_un_enum_del_servidor() throws Exception {
        // Regresión (spec 010, F-01): en el servidor player.gamemode es un enum y == 'survival' se cumple; con el
        // texto SURVIVAL de given no se cumplía y la prueba del ejemplo diamantes_contados fallaba.
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                modo:
                  trigger: block_break
                  conditions: ["player.gamemode == 'survival'", "block.type in ['diamond_ore']"]
                  actions: ["message: ok"]
                  tests:
                    - given: {player.gamemode: SURVIVAL, block.type: DIAMOND_ORE}
                      expect: {fired: true}
                    - given: {player.gamemode: Survival}
                      expect: {fired: false}
                """, p);
        List<RuleTester.Result> res = tester(rules).run(rules.get("modo"));
        assertTrue(res.stream().allMatch(RuleTester.Result::ok), report(res));
    }

    @Test
    void H4_resumen_cuenta_reglas_sin_pruebas() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load(DIAMANTES + """
                sin_pruebas:
                  trigger: call
                  actions: ["message: hola"]
                """, p);
        RuleTester.Summary s = tester(rules).runAll(rules.values());
        assertEquals(3, s.passed());
        assertEquals(0, s.failed());
        assertEquals(1, s.untested());
    }
}
