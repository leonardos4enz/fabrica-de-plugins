package dev.leo.omnievents.api;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.expr.Functions;
import dev.leo.omnievents.expr.Properties;
import dev.leo.omnievents.trigger.TriggerType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * API para otros plugins (H12). Registrar antes de que OmniEvents cargue las reglas (en onLoad o con
 * depend/softdepend) o llamar a {@link #reload()} después.
 */
public final class OmniEventsAPI {
    private static OmniEvents plugin;

    private OmniEventsAPI() {
    }

    public static void init(OmniEvents p) {
        plugin = p;
    }

    private static OmniEvents plugin() {
        if (plugin == null) throw new IllegalStateException("OmniEvents no está habilitado");
        return plugin;
    }

    /** Acción nueva: la fábrica recibe los argumentos al cargar y devuelve la acción compilada. */
    public static void registerAction(String name, Actions.Factory factory) {
        plugin().actions().register(name, factory);
    }

    /** Función nueva del lenguaje de expresiones. */
    public static void registerFunction(String name, int minArgs, int maxArgs, Functions.Impl impl) {
        Functions.register(name, minArgs, maxArgs, impl);
    }

    /** Disparador nuevo (TriggerType.event(...)). */
    public static void registerTrigger(TriggerType type) {
        plugin().triggers().register(type);
    }

    /** Alias de propiedad para un tipo (p. ej. "balance" en Player). */
    public static <T> void registerProperty(Class<T> type, String name, Function<T, Object> getter) {
        Properties.get().alias(type, name, getter);
    }

    /** Ejecuta una regla con trigger: call. */
    public static void callRule(String rule, Player player, Map<String, Object> args) {
        Map<String, Object> b = new HashMap<>();
        b.put("player", player);
        if (player != null) {
            b.put("world", player.getWorld());
            b.put("location", player.getLocation());
        }
        ExecContext ctx = new ExecContext(plugin(), null, b);
        plugin().rules().call(rule, ctx, args == null ? Map.of() : args, 1, null);
    }

    public static void reload() {
        plugin().logProblems(plugin().reload());
    }
}
