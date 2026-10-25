package dev.leo.omnievents.trigger;

import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.trigger.StateDeps.Source;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deducción de dependencias de un disparador state (spec 008, H2 y plan §1.2) y su tabla de estado (H1, H3). */
class StateDepsTest {
    private static StateDeps deps(String... exprs) {
        return StateDeps.analyze(java.util.Arrays.stream(exprs).map(Expression::compile).toList());
    }

    private static void fuentes(String expr, Source... expected) {
        StateDeps d = deps(expr);
        assertEquals(expected.length == 0 ? EnumSet.noneOf(Source.class) : EnumSet.of(expected[0], expected), d.sources(), expr);
        assertTrue(d.unknown().isEmpty(), expr + " → " + d.unknown());
    }

    @Test
    void H2_CA1_cada_propiedad_del_jugador_va_a_su_fuente() {
        fuentes("player.health <= 6", Source.HEALTH);
        fuentes("player.max_health > 20", Source.HEALTH);
        fuentes("player.food < 6", Source.FOOD);
        fuentes("player.food_level < 6", Source.FOOD);
        fuentes("player.location.block.type == 'WATER'", Source.MOVE);
        fuentes("player.location.biome == 'DESERT'", Source.MOVE);
        fuentes("player.y < 0", Source.MOVE);
        fuentes("player.world.name == 'world_nether'", Source.MOVE);
        fuentes("player.in_water", Source.MOVE);
        fuentes("player.level >= 30", Source.XP);
        fuentes("player.gamemode == 'creative'", Source.GAMEMODE);
        fuentes("player.sneaking && player.sprinting", Source.SNEAK, Source.SPRINT);
        fuentes("player.flying", Source.FLIGHT);
        fuentes("player.helmet.type == 'DIAMOND_HELMET'", Source.ARMOR);
        fuentes("player.item.type == 'DIAMOND_SWORD'", Source.INVENTORY);
        fuentes("item.type == 'STICK'", Source.INVENTORY);
        fuentes("location.y > 100", Source.MOVE);
        fuentes("world.name == 'world'", Source.MOVE);
    }

    @Test
    void H2_CA1_funciones_con_estado_y_funciones_puras() {
        fuentes("in_area('world', 0, 0, 0, 10, 10, 10)", Source.MOVE);
        fuentes("distance(player, location) < 5", Source.MOVE);
        fuentes("has_item('DIAMOND', 3)", Source.INVENTORY);
        fuentes("online >= 2", Source.ONLINE);
        fuentes("len(players()) > 1", Source.ONLINE);
        fuentes("abs(round(player.health)) < max(2, min(3, 4))", Source.HEALTH);
        fuentes("if(player.sneaking, player.food, 20) < 5", Source.SNEAK, Source.FOOD);
        fuentes("player.name == 'Leo'");
        fuentes("world.storm", Source.WEATHER, Source.MOVE);
    }

    @Test
    void H2_CA1_variables_y_otros_estados() {
        StateDeps d = deps("default(pvar.racha, 0) >= 10 && gvar.evento == true", "in_state('vida_baja')");
        assertEquals(Set.of("racha"), d.pvars());
        assertEquals(Set.of("evento"), d.gvars());
        assertEquals(Set.of("vida_baja"), d.states());
        assertTrue(d.sources().isEmpty());
        assertTrue(d.unknown().isEmpty(), d.unknown().toString());
        assertTrue(d.describe().containsAll(List.of("pvar.racha", "gvar.evento", "in_state('vida_baja')")), d.describe().toString());
    }

    @Test
    void H2_CA3_lo_que_no_se_puede_deducir_se_nombra() {
        assertTrue(deps("perm('vip')").unknown().contains("la función perm()"));
        assertTrue(deps("%player_level% > 3").unknown().stream().anyMatch(u -> u.contains("%player_level%")));
        assertTrue(deps("hour() >= 20").unknown().contains("la función hour()"));
        assertTrue(deps("!world.day").unknown().contains("world.day"));
        assertTrue(deps("player.op").unknown().contains("player.op"));
        assertTrue(deps("cooldown('x') > 0").unknown().contains("la función cooldown()"));
        assertTrue(deps("in_state(rule)").unknown().stream().anyMatch(u -> u.contains("texto fijo")));
        assertTrue(deps("player.health < 5").unknown().isEmpty());
    }

    @Test
    void H2_CA4_el_azar_se_detecta() {
        assertEquals(Set.of("chance()"), deps("player.health < 5 && chance(50)").random());
        assertEquals(Set.of("random()"), deps("random(1, 6) == 6").random());
    }

    @Test
    void H6_CA2_lo_que_necesita_jugador_se_nombra() {
        assertFalse(deps("player.health < 5").playerUses().isEmpty());
        assertFalse(deps("pvar.x == 1").playerUses().isEmpty());
        assertFalse(deps("in_area('w', 0, 0, 0, 1, 1, 1)").playerUses().isEmpty());
        assertTrue(deps("gvar.evento == true && online >= 2").playerUses().isEmpty());
        assertTrue(deps("world.storm").playerUses().isEmpty());
    }

    @Test
    void H2_constante_si_nada_cambia() {
        assertTrue(deps("1 == 1").constant());
        assertTrue(deps("player.name == 'Leo'").constant());
        assertFalse(deps("player.health < 3").constant());
    }

    // ------------------------------------------------------------------ tabla de estado

    private static final UUID ANA = UUID.randomUUID();

    @Test
    void H1_CA2_entra_y_sale_una_vez() {
        StateTable t = new StateTable();
        assertEquals(StateTable.Kind.NONE, t.update(ANA, false, 0, false).kind());
        assertEquals(StateTable.Kind.ENTER, t.update(ANA, true, 1000, false).kind());
        t.paired(ANA);
        assertEquals(StateTable.Kind.NONE, t.update(ANA, true, 2000, false).kind(), "mientras no cambia, no se repite");
        StateTable.Change out = t.update(ANA, false, 4500, false);
        assertEquals(StateTable.Kind.EXIT, out.kind());
        assertTrue(out.entry().paired());
        assertEquals(1000, out.entry().since());
        assertFalse(t.isIn(ANA));
    }

    @Test
    void H1_CA3_sin_on_enter_la_salida_no_tiene_pareja() {
        StateTable t = new StateTable();
        t.update(ANA, true, 0, false);
        assertFalse(t.update(ANA, false, 10, false).entry().paired());
    }

    @Test
    void H3_CA1_primera_revision_al_entrar_y_silencio() {
        StateTable t = new StateTable();
        assertEquals(StateTable.Kind.ENTER, t.update(ANA, true, 0, false).kind(), "initial: enter corre on_enter al entrar");
        t.forget(ANA);
        assertFalse(t.known(ANA));
        assertEquals(StateTable.Kind.ENTER_SILENT, t.update(ANA, true, 0, true).kind(), "initial: silent");
        t.update(ANA, false, 5, true);
        assertEquals(StateTable.Kind.ENTER, t.update(ANA, true, 9, true).kind(), "el silencio es solo para la primera revisión");
    }

    @Test
    void H3_CA1_salir_del_servidor_olvida_sin_on_exit() {
        StateTable t = new StateTable();
        t.update(ANA, true, 0, false);
        t.forget(ANA);
        assertEquals(0, t.size());
        assertEquals(StateTable.Kind.NONE, t.update(ANA, false, 1, false).kind());
    }
}
