package dev.leo.omnievents.trigger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCommandsTest {
    /** Como el mapa de comandos de Paper: remove(clave) funciona, borrar desde el iterador no. */
    private static final class PaperLikeMap extends AbstractMap<String, Command> {
        private final Map<String, Command> inner = new HashMap<>();

        @Override
        public Set<Entry<String, Command>> entrySet() {
            return Collections.unmodifiableMap(inner).entrySet();
        }

        @Override
        public Command put(String k, Command v) {
            return inner.put(k, v);
        }

        @Override
        public Command remove(Object k) {
            return inner.remove(k);
        }
    }

    private static Command cmd(String name) {
        return new Command(name) {
            @Override
            public boolean execute(@NotNull CommandSender s, @NotNull String l, @NotNull String[] a) {
                return true;
            }
        };
    }

    @Test
    void quitar_comandos_de_un_mapa_que_no_permite_borrar_desde_el_iterador() {
        // Regresión (servidor real 001, F-03): /omni reload y el apagado fallaban con UnsupportedOperationException.
        PaperLikeMap known = new PaperLikeMap();
        Command mine = cmd("monedas"), other = cmd("spawn");
        known.put("monedas", mine);
        known.put("omnievents:monedas", mine);
        known.put("spawn", other);
        assertThrows(UnsupportedOperationException.class, () -> known.entrySet().removeIf(e -> e.getValue() == mine),
                "así lo hacía el código anterior");
        RuleCommands.removeFrom(known, List.of(mine));
        assertEquals(1, known.size());
        assertTrue(known.containsKey("spawn"));
    }

    private static ConfigurationSection rule(String yaml) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(yaml);
        return y;
    }

    private static final String MONEDAS = """
            trigger: command
            command:
              name: Monedas
              aliases: [coins]
              permission: srv.monedas
              tab: [[dar, ver], "{players}"]
            """;

    @Test
    void H1_CA1_la_misma_seccion_da_la_misma_spec_y_no_hay_que_registrar() throws Exception {
        // Spec 005: si las Spec son iguales, /omni enable|disable y reload no tocan el árbol de comandos.
        RuleCommands.Spec a = RuleCommands.Spec.of("monedas", rule(MONEDAS));
        RuleCommands.Spec b = RuleCommands.Spec.of("monedas", rule(MONEDAS + "enabled: false\nactions: [{message: hola}]\n"));
        assertEquals(a, b, "el estado enabled y las acciones no cambian el comando");
        assertEquals("monedas", a.name());
        assertEquals(List.of(List.of("dar", "ver"), List.of("{players}")), a.tab());
        assertEquals("/monedas", a.usage());
    }

    @Test
    void H1_CA1_cambiar_alias_permiso_o_autocompletado_cambia_la_spec() throws Exception {
        RuleCommands.Spec base = RuleCommands.Spec.of("monedas", rule(MONEDAS));
        assertNotEquals(base, RuleCommands.Spec.of("monedas", rule(MONEDAS.replace("[coins]", "[coins, plata]"))));
        assertNotEquals(base, RuleCommands.Spec.of("monedas", rule(MONEDAS.replace("srv.monedas", "srv.otro"))));
        assertNotEquals(base, RuleCommands.Spec.of("monedas", rule(MONEDAS.replace("[dar, ver]", "[dar]"))));
        assertNotEquals(base, RuleCommands.Spec.of("otra_regla", rule(MONEDAS)));
    }

    @Test
    void forma_corta_y_regla_sin_nombre() throws Exception {
        RuleCommands.Spec corta = RuleCommands.Spec.of("eco", rule("trigger: command\ncommand: eco\n"));
        assertEquals("eco", corta.name());
        assertEquals(List.of(), corta.aliases());
        assertNull(corta.permission());
        assertNull(RuleCommands.Spec.of("nada", rule("trigger: command\n")));
    }
}
