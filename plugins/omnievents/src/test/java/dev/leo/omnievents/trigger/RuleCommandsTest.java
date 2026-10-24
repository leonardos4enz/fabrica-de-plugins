package dev.leo.omnievents.trigger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
