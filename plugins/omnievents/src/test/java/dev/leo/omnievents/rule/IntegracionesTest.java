package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.action.Purse;
import dev.leo.omnievents.action.Sandbox;
import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 012: integraciones con costos (cost, on_cost_fail, dinero, regiones y NPC). Sin servidor ni plugins. */
class IntegracionesTest {
    private final Triggers triggers = new Triggers();

    private static String dump(Problems p) {
        return p.all().stream().map(Problems.Problem::toString).collect(Collectors.joining("\n"));
    }

    private Map<String, Rule> load(RuleLoader loader, String yaml, Problems p) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        Map<String, Rule> out = new LinkedHashMap<>();
        for (String name : y.getKeys(false)) {
            Rule r = loader.parse(name, "prueba.yml", y.getConfigurationSection(name), p.file("prueba.yml"));
            if (r != null) out.put(name.toLowerCase(), r);
        }
        return out;
    }

    private Map<String, Rule> load(String yaml, Problems p) throws Exception {
        return load(new RuleLoader(new Actions(null), triggers), yaml, p);
    }

    private static List<RuleTester.Result> run(Map<String, Rule> rules) {
        RuleTester t = new RuleTester(n -> rules.get(n.toLowerCase()), 1000, 16);
        List<RuleTester.Result> out = new ArrayList<>();
        for (Rule r : rules.values()) out.addAll(t.run(r));
        return out;
    }

    private static String report(List<RuleTester.Result> res) {
        return res.stream().filter(r -> !r.ok()).map(r -> r.test().name() + ": " + r.failures() + "\n  " + String.join("\n  ", r.trace())).collect(Collectors.joining("\n"));
    }

    private static final String TIENDA = """
            tienda:
              trigger: command
              command: tienda
              cost:
                money: 500
                levels: 2
                exp: 30
                items: ["DIAMOND;3", "STICK"]
                pvar.fichas: 1
                gvar.bote: 10
              actions: ["message: <green>Comprado"]
              on_cost_fail: ["message: <red>Te falta: {var.cost_missing}"]
              tests:
                - name: alcanza
                  given: {player.level: 5, player.total_exp: 100, money: 800, inventory: {DIAMOND: 4, STICK: 1}, pvar.fichas: 1, gvar.bote: 10}
                  expect:
                    branch: actions
                    pvar.fichas: 0
                    gvar.bote: 0
                    messages: [Comprado]
                    assert: ["money() == 300", "player.level == 3", "player.total_exp == 70"]
                - name: falta dinero y diamantes
                  given: {player.level: 5, player.total_exp: 100, money: 300, inventory: {DIAMOND: 1, STICK: 1}, pvar.fichas: 1, gvar.bote: 10}
                  expect:
                    branch: on_cost_fail
                    fired: false
                    pvar.fichas: 1
                    gvar.bote: 10
                    messages: ["Te falta: 200 de dinero, 2 × DIAMOND"]
                    assert: ["money() == 300", "player.level == 5", "var.cost_missing_list == ['money', 'items']"]
                - name: sin saldo en la prueba es como sin economía
                  given: {player.level: 5, player.total_exp: 100, inventory: {DIAMOND: 4, STICK: 1}, pvar.fichas: 1, gvar.bote: 10}
                  expect: {branch: on_cost_fail, messages: ["Te falta: dinero (no hay economía)"]}
            """;

    @Test
    void H2_CA1_CA2_CA5_todo_o_nada_en_el_sandbox() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load(TIENDA, p);
        assertEquals(0, p.errors(), dump(p));
        assertEquals(0, p.warnings(), dump(p));
        List<RuleTester.Result> res = run(rules);
        assertEquals(3, res.size());
        assertTrue(res.stream().allMatch(RuleTester.Result::ok), report(res));
    }

    /** Un bolsillo en memoria que anota cada operación y puede rechazar el retiro de dinero o el depósito. */
    private static final class FakePurse implements Purse {
        final List<String> log = new ArrayList<>();
        final Map<String, Object> vars = new HashMap<>();
        Double money = 1000.0;
        int levels = 10, diamonds = 5;
        boolean rejectWithdraw, rejectDeposit;

        @Override
        public Double money() {
            return money;
        }

        @Override
        public boolean takeMoney(double n) {
            log.add("take money " + (int) n);
            if (rejectWithdraw) return false;
            money -= n;
            return true;
        }

        @Override
        public boolean giveMoney(double n) {
            log.add("give money " + (int) n);
            if (rejectDeposit) return false;
            money += n;
            return true;
        }

        @Override
        public String formatMoney(double n) {
            return "$" + (int) n;
        }

        @Override
        public int levels() {
            return levels;
        }

        @Override
        public void setLevels(int l) {
            log.add("levels " + l);
            levels = l;
        }

        @Override
        public int exp() {
            return 0;
        }

        @Override
        public void setExp(int total) {
        }

        @Override
        public int count(String material) {
            return material.equals("DIAMOND") ? diamonds : 0;
        }

        @Override
        public Object takeItems(String material, int n) {
            log.add("take " + n + " " + material);
            diamonds -= n;
            return n;
        }

        @Override
        public void giveBack(Object taken) {
            log.add("give back " + taken);
            diamonds += (Integer) taken;
        }

        @Override
        public Object var(Scope scope, String name) {
            return vars.get(name);
        }

        @Override
        public void setVar(Scope scope, String name, Object value) {
            log.add("set " + name + " " + value);
            vars.put(name, value);
        }

        @Override
        public String who() {
            return "Ana";
        }
    }

    private static Cost cost(String yaml, Problems p) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        return Cost.parse(y.get("cost"), "r.cost", p);
    }

    private static ExecContext ctx() {
        return new ExecContext(null, new HashMap<>(), new Sandbox(0, 100, 4));
    }

    @Test
    void H2_CA3_si_un_cobro_falla_se_devuelve_lo_cobrado_en_orden_inverso() throws Exception {
        Problems p = new Problems();
        Cost c = cost("cost: {levels: 2, items: 'DIAMOND;3', pvar.fichas: 1, money: 100}", p);
        assertEquals(0, p.errors(), dump(p));
        FakePurse purse = new FakePurse();
        purse.vars.put("fichas", 4.0);
        purse.rejectWithdraw = true;
        Cost.Quote q = c.quote(ctx(), purse);
        assertTrue(q.ok());
        List<String> refundErrors = new ArrayList<>();
        Cost.Line failed = c.charge(q, purse, (l, why) -> refundErrors.add(l.key() + ": " + why));
        assertNotNull(failed);
        assertEquals("money", failed.key());
        assertEquals(List.of("levels 8", "take 3 DIAMOND", "set fichas 3.0", "take money 100", "set fichas 4.0", "give back 3", "levels 10"), purse.log);
        assertEquals(10, purse.levels);
        assertEquals(5, purse.diamonds);
        assertEquals(4.0, purse.vars.get("fichas"));
        assertTrue(refundErrors.isEmpty());
    }

    @Test
    void H2_CA3_una_devolucion_que_falla_se_informa() throws Exception {
        Problems p = new Problems();
        Cost c = cost("cost: {money: 100, levels: 50}", p);
        FakePurse purse = new FakePurse();
        purse.levels = 60;
        Cost.Quote q = c.quote(ctx(), purse);
        purse.levels = 10; // cambió entre la cotización y el cobro
        purse.rejectDeposit = true;
        List<String> refundErrors = new ArrayList<>();
        Cost.Line failed = c.charge(q, purse, (l, why) -> refundErrors.add(l.key() + ": " + why));
        assertEquals("levels", failed.key());
        assertEquals(List.of("money: la economía rechazó el depósito"), refundErrors);
    }

    @Test
    void H2_CA1_las_cantidades_se_evaluan_una_vez_en_la_cotizacion() throws Exception {
        Problems p = new Problems();
        Cost c = cost("cost: {money: 'pvar.precio * 2'}", p);
        assertEquals(0, p.errors(), dump(p));
        Sandbox sb = new Sandbox(0, 100, 4);
        sb.put(Scope.PLAYER, "precio", 50.0, 0);
        ExecContext ctx = new ExecContext(null, new HashMap<>(), sb);
        FakePurse purse = new FakePurse();
        Cost.Quote q = c.quote(ctx, purse);
        sb.put(Scope.PLAYER, "precio", 999.0, 0);
        assertNull(c.charge(q, purse, (l, why) -> { }));
        assertEquals(List.of("take money 100"), purse.log);
    }

    // Regresión (spec 012, F-01): Bukkit lee «pvar.fichas: 1» dentro de cost como una sección pvar con fichas; la
    // clave salía como «pvar» desconocida y el ejemplo no cargaba.
    @Test
    void F01_pvar_con_punto_dentro_de_cost() throws Exception {
        Problems p = new Problems();
        Cost c = cost("cost:\n  pvar.fichas: 1\n  gvar.bote: 2\n", p);
        assertEquals(0, p.errors(), dump(p));
        assertEquals(List.of("pvar.fichas", "gvar.bote"), c.parts().stream().map(Cost.Part::key).toList());
    }

    // Regresión (spec 012, F-02): dos reglas con el mismo nombre en un archivo; YAML se quedaba con la última y la
    // carga daba 0 avisos (la regla e2e_tienda de la suite tapó a otra).
    @Test
    void F02_regla_repetida_en_un_archivo_avisa() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("omni-f02").toFile();
        java.nio.file.Files.writeString(new java.io.File(dir, "a.yml").toPath(), """
                # comentario: x
                uno:
                  trigger: command
                  command: uno
                  actions: ["message: primera"]
                "uno":
                  trigger: command
                  command: uno2
                  actions: ["message: segunda"]
                dos:
                  trigger: command
                  command: dos
                  actions:
                    - message: "uno: no cuenta"
                """);
        Problems p = new Problems();
        Map<String, Rule> rules = new RuleLoader(new Actions(null), triggers).loadFolder(dir, p);
        String d = dump(p);
        assertTrue(d.contains("uno: la regla está dos veces en este archivo"), d);
        assertEquals(1, p.warnings(), d);
        assertEquals(2, rules.size());
    }

    @Test
    void H2_CA4_errores_de_cost_con_ruta() throws Exception {
        Problems p = new Problems();
        load("""
                a:
                  trigger: command
                  command: a
                  cost: {monye: 5}
                  actions: [stop]
                b:
                  trigger: command
                  command: b
                  cost: {money: -5, levels: 1.5, items: "NO_EXISTE;2"}
                  actions: [stop]
                c:
                  trigger: player_chat
                  cost: {money: 5}
                  actions: [stop]
                d:
                  trigger: server_start
                  cost: {money: 5}
                  actions: [stop]
                e:
                  trigger: server_start
                  cost: {gvar.bote: 5}
                  actions: [stop]
                f:
                  trigger: command
                  command: f
                  on_cost_fail: [stop]
                  actions: [stop]
                g:
                  trigger: command
                  command: g
                  cost: 5
                  actions: [stop]
                """, p);
        String d = dump(p);
        assertTrue(d.contains("a.cost.monye: clave desconocida en cost (¿quisiste decir 'money'?)"), d);
        assertTrue(d.contains("b.cost.money: la cantidad debe ser mayor que 0"), d);
        assertTrue(d.contains("b.cost.levels: la cantidad debe ser un número entero"), d);
        assertTrue(d.contains("b.cost.items: material desconocido 'NO_EXISTE'"), d);
        assertTrue(d.contains("c.cost: cost no sirve en player_chat (asíncrono)"), d);
        assertTrue(d.contains("d.cost: el disparador server_start no tiene jugador"), d);
        assertFalse(d.contains("e.cost"), d);
        assertTrue(d.contains("f.on_cost_fail: on_cost_fail no se usa sin cost"), d);
        assertTrue(d.contains("g.cost: cost es un mapa"), d);
    }

    @Test
    void H1_CA2_H3_CA3_H4_CA3_avisos_sin_la_integracion() throws Exception {
        String yaml = """
                dinero:
                  trigger: command
                  command: dinero
                  actions: ["message: Tienes {money()}"]
                region:
                  trigger: region_enter
                  actions: [stop]
                npc:
                  trigger: npc_click
                  actions: [stop]
                """;
        Problems sin = new Problems();
        load(new RuleLoader(new Actions(null), triggers).integrations(Set.of()), yaml, sin);
        String d = dump(sin);
        assertEquals(0, sin.errors(), d);
        assertTrue(d.contains("dinero: el dinero (cost.money, give_money, take_money o money()) necesita Vault"), d);
        assertTrue(d.contains("region: las regiones (region_enter) necesita WorldGuard"), d);
        assertTrue(d.contains("npc: el disparador npc_click necesita Citizens"), d);
        Problems con = new Problems();
        load(new RuleLoader(new Actions(null), triggers).integrations(Set.of("Vault", "WorldGuard", "Citizens")), yaml, con);
        assertEquals(0, con.warnings(), dump(con));
    }

    @Test
    void H1_CA1_give_money_y_take_money_en_una_prueba() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                pagar:
                  trigger: command
                  command: pagar
                  actions:
                    - take_money: 30
                    - give_money: "5 * 2"
                    - "message: Saldo {money_format(money())}"
                  tests:
                    - given: {money: 100}
                      expect: {actions: [take_money, give_money], messages: ["Saldo 80"], assert: "money() == 80"}
                    - name: no alcanza
                      given: {money: 10}
                      expect: {assert: "money() == 20"}
                """, p);
        assertEquals(0, p.errors(), dump(p));
        List<RuleTester.Result> res = run(rules);
        assertTrue(res.get(0).ok(), report(res));
        // take_money sin saldo suficiente es un error de la acción y no retira nada (el give_money sí corre).
        assertTrue(res.get(1).failures().stream().anyMatch(f -> f.contains("take_money: no alcanza (tiene 10, pide 30)")), res.get(1).failures().toString());
        assertTrue(res.get(1).failures().stream().noneMatch(f -> f.startsWith("assert")), res.get(1).failures().toString());
    }

    @Test
    void H3_CA1_CA2_H4_CA1_CA2_regiones_y_npc_en_una_prueba() throws Exception {
        Problems p = new Problems();
        Map<String, Rule> rules = load("""
                entrar:
                  trigger: region_enter
                  conditions: ["region.id == 'mina'", "in_region('mina')", "size(regions()) == 2"]
                  actions: [cancel]
                  tests:
                    - given: {region.id: mina, regions: [mina, spawn]}
                      expect: {cancelled: true, branch: actions}
                    - given: {region.id: mina, regions: [spawn]}
                      expect: {fired: false}
                guia:
                  trigger: npc_click
                  conditions: ["is_npc(target)", "npc.id == 3"]
                  actions: ["message: Hola de {npc.name}"]
                  tests:
                    - given: {npc.id: 3, npc.name: Guía, target.npc: true}
                      expect: {messages: ["Hola de Guía"]}
                    - given: {npc.id: 3, target.name: Zombi}
                      expect: {fired: false}
                """, p);
        assertEquals(0, p.errors(), dump(p));
        assertEquals(0, p.warnings(), dump(p));
        List<RuleTester.Result> res = run(rules);
        assertTrue(res.stream().allMatch(RuleTester.Result::ok), report(res));
    }

    @Test
    void H2_CA5_given_de_cost_se_valida() throws Exception {
        Problems p = new Problems();
        load("""
                t:
                  trigger: command
                  command: t
                  cost: {money: 1}
                  actions: [stop]
                  tests:
                    - given: {money: mucho, inventory: {NO_EXISTE: 1}, regions: 5}
                      expect: {branch: on_cost_fail}
                """, p);
        String d = dump(p);
        assertTrue(d.contains("t.tests[0].given.money: money es un número"), d);
        assertTrue(d.contains("t.tests[0].given.inventory.NO_EXISTE: material desconocido"), d);
        assertTrue(d.contains("t.tests[0].given.regions: regions es una lista"), d);
        assertFalse(d.contains("expect.branch"), d);
    }

    @Test
    void H3_H4_disparadores_registrados() {
        assertEquals(TriggerType.Kind.REGION, triggers.get("region_enter").kind());
        assertEquals(TriggerType.Kind.REGION, triggers.get("region_leave").kind());
        assertTrue(triggers.get("npc_click").cancellable());
        assertTrue(triggers.get("npc_left_click").cancellable());
        assertTrue(triggers.get("npc_click").roots().containsAll(List.of("npc", "target", "player")));
        assertTrue(triggers.get("region_enter").roots().containsAll(List.of("region", "player", "event")));
    }
}
