package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 006, P1: condiciones con deny y grupos «al menos N de M». */
class CondicionesTest {
    private final RuleLoader loader = new RuleLoader(new Actions(null), new Triggers());

    private static String dump(Problems p) {
        return p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    private Rule parse(String yaml, Problems p) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        String name = y.getKeys(false).iterator().next();
        return loader.parse(name, "prueba.yml", y.getConfigurationSection(name), p.file("prueba.yml"));
    }

    /** Contexto con raíces numéricas fijas. */
    private static EvalContext ctx(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return new EvalContext() {
            @Override
            public Object resolve(String name) {
                return m.get(name);
            }
        };
    }

    private static final String TIENDA = """
            tienda:
              trigger: call
              conditions:
                - "a > 0"
                - expr: "b >= 10"
                  deny:
                    - "message: <red>Necesitas b 10 (tienes {b})"
                - any: 2
                  of: ["x == 1", "y == 1", {expr: "z == 1"}]
                  deny: ["message: faltan 2 de 3"]
              actions: ["message: hecho"]
              else: ["message: else"]
            """;

    @Test
    void H1_CA1_H2_CA1_mapas_con_expr_any_y_deny_cargan() throws Exception {
        Problems p = new Problems();
        Rule r = parse(TIENDA, p);
        assertNotNull(r, dump(p));
        assertEquals(0, p.size(), dump(p));
        assertEquals(3, r.conditions().size());
        assertInstanceOf(Condition.Single.class, r.conditions().get(0));
        assertTrue(r.conditions().get(0).deny().isEmpty());
        assertEquals(1, r.conditions().get(1).deny().size());
        Condition.AnyOf any = assertInstanceOf(Condition.AnyOf.class, r.conditions().get(2));
        assertEquals(2, any.min());
        assertEquals(3, any.of().size());
        assertEquals("tienda.conditions[2]", any.path());
        // Regresión (spec 006, F-01): un mapa quedaba con la ruta «….expr», y /omni check, debug y cpu lo nombraban
        // distinto que a una condición de texto («deny de conditions[1].expr»).
        assertEquals("tienda.conditions[1]", r.conditions().get(1).path());
        assertEquals("tienda.conditions[2].of[2]", any.of().get(2).path());
        assertEquals(1 + 1 + 1 + 1, r.actionCount(), "actions + else + los dos deny");
    }

    @Test
    void H1_CA3_la_forma_de_texto_sigue_igual() throws Exception {
        Problems p = new Problems();
        Rule r = parse("v:\n  trigger: call\n  conditions: [\"a > 0\", \"b > 0\"]\n  actions: [\"message: x\"]\n", p);
        assertEquals(0, p.size(), dump(p));
        assertEquals(2, r.conditions().size());
        assertEquals("a > 0", r.conditions().get(0).source());
        Rule suelta = parse("s:\n  trigger: call\n  conditions: \"a > 0\"\n  actions: [\"message: x\"]\n", p);
        assertEquals(1, suelta.conditions().size());
        assertEquals("s.conditions", suelta.conditions().get(0).path());
    }

    @Test
    void H1_CA3_un_mapa_suelto_tambien_es_una_condicion() throws Exception {
        Problems p = new Problems();
        Rule r = parse("m:\n  trigger: call\n  conditions:\n    expr: \"a > 0\"\n    deny: [\"message: no\"]\n  actions: [\"message: x\"]\n", p);
        assertEquals(0, p.size(), dump(p));
        assertEquals(1, r.conditions().get(0).deny().size());
    }

    @Test
    void H2_CA1_any_cuenta_y_corta_cuando_se_decide() throws Exception {
        Rule r = parse(TIENDA, new Problems());
        Condition any = r.conditions().get(2);
        assertTrue(any.test(ctx("x", 1.0, "y", 1.0, "z", 0.0), null, "", false, null));
        assertTrue(any.test(ctx("x", 0.0, "y", 1.0, "z", 1.0), null, "", false, null));
        assertFalse(any.test(ctx("x", 0.0, "y", 0.0, "z", 1.0), null, "", false, null));

        List<String> corto = new ArrayList<>();
        any.test(ctx("x", 1.0, "y", 1.0, "z", 0.0), corto::add, "", false, null);
        assertTrue(corto.get(0).startsWith("✔ al menos 2 de 3 → se cumplen 2 (de 2 evaluadas)"), corto.toString());
        assertEquals(3, corto.size(), "cabecera y las dos evaluadas: " + corto);

        List<String> todo = new ArrayList<>();
        any.test(ctx("x", 1.0, "y", 1.0, "z", 0.0), todo::add, "", true, null);
        assertEquals("✔ al menos 2 de 3 → se cumplen 2", todo.get(0));
        assertEquals(4, todo.size(), "con all se evalúan las tres: " + todo);
        assertTrue(todo.get(3).contains("✘") && todo.get(3).contains("z"), todo.toString());
    }

    @Test
    void H2_CA1_any_se_puede_anidar() throws Exception {
        Problems p = new Problems();
        Rule r = parse("""
                n:
                  trigger: call
                  conditions:
                    - any: 1
                      of:
                        - "a == 1"
                        - any: 2
                          of: ["b == 1", "c == 1"]
                  actions: ["message: x"]
                """, p);
        assertEquals(0, p.size(), dump(p));
        Condition c = r.conditions().get(0);
        assertTrue(c.test(ctx("a", 0.0, "b", 1.0, "c", 1.0), null, "", false, null));
        assertFalse(c.test(ctx("a", 0.0, "b", 1.0, "c", 0.0), null, "", false, null));
    }

    @Test
    void H2_CA3_any_fuera_de_rango_es_error() throws Exception {
        for (String n : List.of("0", "4", "1.5", "muchos")) {
            Problems p = new Problems();
            Rule r = parse("e:\n  trigger: call\n  conditions:\n    - any: " + n + "\n      of: [\"a\", \"b\", \"c\"]\n  actions: [\"message: x\"]\n", p);
            assertNull(r, "any: " + n);
            assertTrue(dump(p).contains("e.conditions[0].any") && dump(p).contains("entre 1 y 3"), dump(p));
        }
    }

    @Test
    void H1_CA4_errores_de_forma_con_ruta() throws Exception {
        Problems p = new Problems();
        assertNull(parse("e:\n  trigger: call\n  conditions:\n    - deny: [\"message: x\"]\n  actions: [\"message: x\"]\n", p));
        assertTrue(dump(p).contains("e.conditions[0]") && dump(p).contains("necesita 'expr'"), dump(p));

        p = new Problems();
        assertNull(parse("e:\n  trigger: call\n  conditions:\n    - expr: \"a >\"\n  actions: [\"message: x\"]\n", p));
        assertTrue(dump(p).contains("e.conditions[0].expr"), dump(p));

        p = new Problems();
        assertNull(parse("e:\n  trigger: call\n  conditions:\n    - expr: \"a\"\n      deny: [\"mesage: x\"]\n  actions: [\"message: x\"]\n", p));
        assertTrue(dump(p).contains("e.conditions[0].deny[0]") && dump(p).contains("message"), dump(p));

        p = new Problems();
        assertNull(parse("e:\n  trigger: call\n  conditions:\n    - any: 1\n  actions: [\"message: x\"]\n", p));
        assertTrue(dump(p).contains("e.conditions[0].of"), dump(p));

        p = new Problems();
        assertNull(parse("e:\n  trigger: call\n  conditions:\n    - expr: \"a\"\n      any: 1\n      of: [\"a\"]\n  actions: [\"message: x\"]\n", p));
        assertTrue(dump(p).contains("no ambas"), dump(p));
    }

    @Test
    void H1_CA4_clave_desconocida_sugiere() throws Exception {
        Problems p = new Problems();
        Rule r = parse("e:\n  trigger: call\n  conditions:\n    - expr: \"a\"\n      deni: [\"message: x\"]\n  actions: [\"message: x\"]\n", p);
        assertNotNull(r, dump(p));
        assertEquals(1, p.warnings(), dump(p));
        assertTrue(dump(p).contains("e.conditions[0].deni") && dump(p).contains("deny"), dump(p));
    }

    @Test
    void H2_CA2_deny_dentro_de_of_se_ignora_con_aviso() throws Exception {
        Problems p = new Problems();
        Rule r = parse("e:\n  trigger: call\n  conditions:\n    - any: 1\n      of:\n        - expr: \"a\"\n          deny: [\"message: x\"]\n  actions: [\"message: x\"]\n", p);
        assertNotNull(r, dump(p));
        assertEquals(1, p.warnings(), dump(p));
        assertTrue(dump(p).contains("e.conditions[0].of[0].deny"), dump(p));
        Condition.AnyOf any = (Condition.AnyOf) r.conditions().get(0);
        assertTrue(any.of().get(0).deny().isEmpty());
    }

    @Test
    void H1_CA4_las_raices_del_deny_se_validan() throws Exception {
        Problems p = new Problems();
        parse("e:\n  trigger: player_join\n  conditions:\n    - expr: \"player != null\"\n      deny: [\"message: {bloque.type}\"]\n  actions: [\"message: x\"]\n", p);
        assertTrue(dump(p).contains("'bloque' no existe"), dump(p));
    }

    @Test
    void H3_error_de_evaluacion_cuenta_como_falso_y_se_informa() throws Exception {
        Rule r = parse("e:\n  trigger: call\n  conditions: [\"a / 0 > 1\"]\n  actions: [\"message: x\"]\n", new Problems());
        List<String> lines = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        boolean ok = r.conditions().get(0).test(ctx("a", 1.0), lines::add, "", false, (c, e) -> errores.add(c.source()));
        assertFalse(ok);
        assertEquals(List.of("a / 0 > 1"), errores);
        assertTrue(lines.get(0).startsWith("✘ error en «a / 0 > 1»"), lines.toString());
    }

    @Test
    void H4_CA1_solo_las_acciones_de_control_corren_en_una_simulacion() throws Exception {
        Problems p = new Problems();
        Rule r = parse("""
                s:
                  trigger: call
                  actions:
                    - "message: <green>Hola {player}"
                    - set: "pvar.x = 1"
                    - if: "a > 0"
                      then: ["console: say hola"]
                    - "delay: 1s"
                    - "call: otra"
                    - give_item: {material: DIAMOND, amount: 2}
                      chance: 30
                """, p);
        assertEquals(0, p.errors(), dump(p));
        var acts = r.actions();
        assertEquals(List.of(false, false, true, true, true, false), acts.stream().map(a -> a.control()).toList());
        assertEquals("message: <green>Hola {player}", acts.get(0).source());
        assertEquals("set: pvar.x = 1", acts.get(1).source());
        assertTrue(acts.get(5).source().startsWith("give_item: {"), acts.get(5).source());
    }

    @Test
    void H3_CA3_avisa_las_raices_sin_valor_separando_al_jugador() {
        // Regresión (spec 006, F-02): desde la consola decía «sin evento: player valen nulo», como si el jugador fuera
        // un dato del evento, y con la concordancia mal.
        java.util.Set<String> roots = java.util.Set.of("player", "block", "event", "world");
        assertEquals(List.of("• sin jugador: player vale nulo (usa /omni check <regla> <jugador>)"),
                RuleManager.missingNotes(java.util.Set.of("player"), roots, r -> false));
        assertEquals(List.of("• sin evento: block vale nulo en esta revisión"),
                RuleManager.missingNotes(java.util.Set.of("block", "player", "pvar"), roots, r -> r.equals("player")));
        assertEquals(List.of("• sin jugador: player vale nulo (usa /omni check <regla> <jugador>)",
                        "• sin evento: block, event valen nulo en esta revisión"),
                RuleManager.missingNotes(java.util.Set.of("block", "event", "player"), roots, r -> false));
        assertEquals(List.of(), RuleManager.missingNotes(java.util.Set.of("world"), roots, r -> true));
    }

    @Test
    void H7_CA1_budget_valido_e_invalido() throws Exception {
        Problems p = new Problems();
        Rule r = parse("b:\n  trigger: call\n  budget: 0.5ms\n  actions: [\"message: x\"]\n", p);
        assertEquals(0, p.size(), dump(p));
        assertEquals(500_000, r.budgetNanos());
        assertEquals(200_000, parse("b:\n  trigger: call\n  budget: 200us\n  actions: [\"message: x\"]\n", p).budgetNanos());
        assertEquals(0, parse("b:\n  trigger: call\n  actions: [\"message: x\"]\n", p).budgetNanos());
        p = new Problems();
        assertNull(parse("b:\n  trigger: call\n  budget: rápido\n  actions: [\"message: x\"]\n", p));
        assertTrue(dump(p).contains("b.budget") && dump(p).contains("0.5ms"), dump(p));
    }
}
