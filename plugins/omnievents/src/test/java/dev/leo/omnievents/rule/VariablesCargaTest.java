package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.trigger.StateDeps;
import dev.leo.omnievents.trigger.Triggers;
import dev.leo.omnievents.expr.Expression;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Carga de reglas con expire, objeto.data y var_expire (spec 009). */
class VariablesCargaTest {
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

    private Rule ok(String yaml) throws Exception {
        Problems p = new Problems();
        Rule r = parse(yaml, p);
        assertNotNull(r, dump(p));
        assertEquals(0, p.size(), dump(p));
        return r;
    }

    private void error(String yaml, String fragment) throws Exception {
        Problems p = new Problems();
        assertNull(parse(yaml, p), dump(p));
        assertTrue(dump(p).contains(fragment), dump(p));
    }

    @Test
    void H1_CA1_expire_en_set_de_pvar_gvar_y_objeto() throws Exception {
        ok("a:\n  trigger: player_join\n  actions:\n    - set: \"pvar.racha += 1\"\n      expire: 10m\n    - set: \"gvar.evento = true\"\n      expire: \"{gvar.minutos}m\"\n");
        Rule r = ok("a:\n  trigger: block_place\n  actions:\n    - set: \"block.data.dueño = player.name\"\n      expire: 1h\n");
        assertTrue(r.writesObjectData());
    }

    @Test
    void H1_CA2_expire_en_var_o_con_duracion_invalida_es_error() throws Exception {
        error("a:\n  trigger: player_join\n  actions:\n    - set: \"var.x = 1\"\n      expire: 10m\n", "expire no sirve en var");
        error("a:\n  trigger: player_join\n  actions:\n    - set: \"pvar.x = 1\"\n      expire: pronto\n", "duración inválida");
        error("a:\n  trigger: player_join\n  actions:\n    - set: \"pvar.x = 1\"\n      expire: 0s\n", "positiva");
    }

    @Test
    void H3_CA1_set_y_unset_de_objeto_data_marcan_la_regla() throws Exception {
        Rule r = ok("a:\n  trigger: player_kill\n  actions:\n    - set: \"target.data.marcas += 1\"\n    - set: \"player.item.data.ligada = player.name\"\n    - unset: \"target.data.marcas\"\n");
        assertTrue(r.writesObjectData());
        assertFalse(ok("a:\n  trigger: player_join\n  actions:\n    - set: \"pvar.x = 1\"\n    - message: \"{player.item.data.ligada}\"\n").writesObjectData(),
                "leer no registra la limpieza de bloques");
        error("a:\n  trigger: player_join\n  actions:\n    - set: \"player.item = 1\"\n", "objeto.data.nombre");
        error("a:\n  trigger: player_join\n  actions:\n    - unset: \"player.item\"\n", "objeto.data.nombre");
    }

    @Test
    void H1_CA4_ttl_mal_usado_es_error_al_cargar() throws Exception {
        error("a:\n  trigger: player_join\n  actions:\n    - message: \"{ttl(var.x)}\"\n", "ttl");
    }

    @Test
    void H2_CA1_var_expire_con_y_sin_variables() throws Exception {
        assertEquals(Set.of("racha", "bono"), ok("a:\n  trigger: var_expire\n  variables: [racha, bono]\n  actions:\n    - message: \"{variable} = {value} ({scope})\"\n").expireVariables());
        assertEquals(Set.of(), ok("a:\n  trigger: var_expire\n  actions:\n    - message: \"{variable}\"\n").expireVariables());
        error("a:\n  trigger: var_expire\n  variables:\n    x: 1\n  actions:\n    - message: \"hola\"\n", "variables es una lista");
    }

    @Test
    void H2_CA2_un_estado_con_objeto_data_o_ttl_pide_check_every() {
        StateDeps d = StateDeps.analyze(List.of(Expression.compile("player.item.data.ligada == player.name")));
        assertFalse(d.unknown().isEmpty());
        d = StateDeps.analyze(List.of(Expression.compile("ttl(pvar.racha) > 5")));
        assertFalse(d.unknown().isEmpty());
        assertTrue(d.pvars().contains("racha"));
        d = StateDeps.analyze(List.of(Expression.compile("pvar.racha >= 3")));
        assertTrue(d.unknown().isEmpty(), "una pvar con caducidad avisa al caducar (H2 CA2)");
    }
}
