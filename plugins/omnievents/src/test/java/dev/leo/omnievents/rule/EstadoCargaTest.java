package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.StateDeps;
import dev.leo.omnievents.trigger.Triggers;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Carga de reglas con trigger: state (spec 008). */
class EstadoCargaTest {
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

    private void error(String yaml, String fragment) throws Exception {
        Problems p = new Problems();
        assertNull(parse(yaml, p), dump(p));
        assertTrue(p.all().stream().anyMatch(x -> x.error() && (x.message() + " " + x.path()).contains(fragment)), "se esperaba «" + fragment + "»:\n" + dump(p));
    }

    @Test
    void H1_CA1_regla_state_completa() throws Exception {
        Problems p = new Problems();
        Rule r = parse("""
                vida_baja:
                  trigger: state
                  when: "player.health <= 6"
                  on_enter: ["title: <red>Peligro"]
                  on_exit: ["actionbar: a salvo {var.state_seconds}"]
                  cooldown: 20s
                """, p);
        assertNotNull(r, dump(p));
        assertEquals(0, p.size(), dump(p));
        StateSpec s = r.state();
        assertNotNull(s);
        assertEquals(1, s.when().size());
        assertEquals(1, r.actions().size(), "on_enter va en las actions de la regla");
        assertEquals(1, s.onExit().size());
        assertTrue(r.conditions().isEmpty(), "when no son las conditions: RuleManager.run no las vuelve a evaluar");
        assertTrue(s.perPlayer());
        assertFalse(s.initialSilent());
        assertEquals(java.util.Set.of(StateDeps.Source.HEALTH), s.deps().sources());
        assertEquals(2, r.actionCount());
    }

    @Test
    void H1_CA1_when_como_lista_y_worlds_suma_movimiento() throws Exception {
        Problems p = new Problems();
        Rule r = parse("""
                agua:
                  trigger: state
                  worlds: [world]
                  when: ["player.in_water", {expr: "player.food < 10"}]
                  on_enter: ["message: mojado"]
                """, p);
        assertNotNull(r, dump(p));
        assertEquals(2, r.state().when().size());
        assertTrue(r.state().deps().sources().containsAll(java.util.Set.of(StateDeps.Source.MOVE, StateDeps.Source.FOOD)));
        assertEquals("prueba.yml", p.all().isEmpty() ? "prueba.yml" : p.all().get(0).file());
    }

    @Test
    void H1_CA5_errores_de_forma() throws Exception {
        error("a:\n  trigger: state\n  on_enter: [\"message: x\"]\n", "necesita 'when'");
        error("a:\n  trigger: state\n  when: \"player.health < 5\"\n", "on_enter, on_exit o ambos");
        error("a:\n  trigger: state\n  when: \"player.health < 5\"\n  actions: [\"message: x\"]\n", "usa on_enter");
        error("a:\n  trigger: state\n  conditions: \"player.health < 5\"\n  on_enter: [\"message: x\"]\n", "usa when");
        error("a:\n  trigger: state\n  when: \"player.health < 5\"\n  on_enter: [\"message: x\"]\n  else: [\"message: y\"]\n", "usa on_exit");
        error("a:\n  trigger: state\n  when: \"player.health < 5\"\n  on_enter: [\"message: x\"]\n  initial: nunca\n", "enter o silent");
        error("a:\n  trigger: state\n  when: \"player.health < 5\"\n  on_enter: [\"message: x\"]\n  watch: [player_dmage]\n", "player_damage");
        error("a:\n  trigger: state\n  when: \"player.health < 5\"\n  on_enter: [\"message: x\"]\n  watch: [timer]\n", "no es un disparador de evento");
        error("a:\n  trigger: state\n  when: \"player.health <\"\n  on_enter: [\"message: x\"]\n", "a.when");
    }

    @Test
    void H2_CA3_no_deducible_pide_check_every() throws Exception {
        error("vip:\n  trigger: state\n  when: \"perm('vip')\"\n  on_enter: [\"message: x\"]\n", "check_every");
        Problems p = new Problems();
        Rule r = parse("vip:\n  trigger: state\n  when: \"perm('vip')\"\n  check_every: 5s\n  on_enter: [\"message: x\"]\n", p);
        assertNotNull(r, dump(p));
        assertEquals(100, r.state().checkEveryTicks());
        error("vip:\n  trigger: state\n  when: \"perm('vip')\"\n  check_every: 10ms\n  on_enter: [\"message: x\"]\n", "1 tick");
    }

    @Test
    void H2_CA4_azar_y_si_mismo_son_error() throws Exception {
        error("a:\n  trigger: state\n  when: \"chance(10)\"\n  on_enter: [\"message: x\"]\n", "azar");
        error("a:\n  trigger: state\n  when: \"in_state('a')\"\n  on_enter: [\"message: x\"]\n", "sí mismo");
    }

    @Test
    void H6_CA2_estado_del_servidor_sin_jugador() throws Exception {
        error("e:\n  trigger: state\n  per_player: false\n  when: \"player.health < 5\"\n  on_enter: [\"message: x\"]\n", "no tiene jugador");
        error("e:\n  trigger: state\n  per_player: false\n  when: \"pvar.x == 1\"\n  on_enter: [\"message: x\"]\n", "pvar.x");
        Problems p = new Problems();
        Rule r = parse("e:\n  trigger: state\n  per_player: false\n  when: \"gvar.evento == true && online >= 2\"\n  on_enter: [\"broadcast: x\"]\n", p);
        assertNotNull(r, dump(p));
        assertFalse(r.state().perPlayer());
        assertEquals(0, p.size(), dump(p));
        Problems q = new Problems();
        parse("e:\n  trigger: state\n  per_player: false\n  when: \"gvar.x == 1\"\n  cooldown: 10s\n  on_enter: [\"broadcast: x\"]\n", q);
        assertTrue(q.all().stream().anyMatch(x -> !x.error() && x.message().contains("cooldown_scope")), dump(q));
    }

    @Test
    void H1_aviso_si_when_no_depende_de_nada() throws Exception {
        Problems p = new Problems();
        assertNotNull(parse("a:\n  trigger: state\n  when: \"player.name == 'Leo'\"\n  on_enter: [\"message: x\"]\n", p));
        assertTrue(p.all().stream().anyMatch(x -> !x.error() && x.message().contains("no depende de nada")), dump(p));
    }

    @Test
    void H3_CA2_la_huella_cambia_con_when_worlds_y_per_player() throws Exception {
        Problems p = new Problems();
        String base = "a:\n  trigger: state\n  when: \"pvar.x == 1\"\n  on_enter: [\"message: x\"]\n";
        String f1 = parse(base, p).state().fingerprint();
        assertEquals(f1, parse(base + "  on_exit: [\"message: y\"]\n", p).state().fingerprint(), "cambiar acciones conserva el estado");
        assertFalse(f1.equals(parse(base.replace("== 1", "== 2"), p).state().fingerprint()));
        assertFalse(f1.equals(parse(base + "  worlds: [world]\n", p).state().fingerprint()));
    }

    @Test
    void H5_CA1_in_state_apunta_a_una_regla_state(@TempDir File dir) throws IOException {
        Files.writeString(new File(dir, "a.yml").toPath(), """
                peligro:
                  trigger: state
                  when: "player.health <= 6"
                  on_enter: ["message: x"]
                curar:
                  trigger: command
                  command: curar
                  conditions: "in_state('peligro')"
                  actions: ["message: ok"]
                mal:
                  trigger: command
                  command: mal
                  conditions: "in_state('pelgro')"
                  actions: ["message: ok"]
                noestado:
                  trigger: command
                  command: otro
                  conditions: "in_state('curar')"
                  actions: ["message: ok"]
                servidor:
                  trigger: state
                  per_player: false
                  when: "in_state('peligro')"
                  on_enter: ["broadcast: x"]
                """, StandardCharsets.UTF_8);
        Problems p = new Problems();
        Map<String, Rule> rules = loader.loadFolder(dir, p);
        assertTrue(rules.containsKey("peligro") && rules.containsKey("curar"), dump(p));
        assertFalse(rules.containsKey("mal"));
        assertFalse(rules.containsKey("noestado"));
        assertFalse(rules.containsKey("servidor"));
        assertTrue(dump(p).contains("peligro"), "¿quisiste decir peligro?\n" + dump(p));
        assertTrue(dump(p).contains("un estado del servidor no puede leer un estado por jugador"), dump(p));
        assertEquals(3, p.errors(), dump(p));
    }

    @Test
    void H7_compatibilidad_reglas_anteriores_sin_estado() throws Exception {
        Problems p = new Problems();
        Rule r = parse("t:\n  trigger: timer\n  every: 5s\n  per_player: false\n  actions: [\"log: x\"]\n", p);
        assertNotNull(r, dump(p));
        assertNull(r.state());
        assertEquals(0, p.size(), dump(p));
    }
}
