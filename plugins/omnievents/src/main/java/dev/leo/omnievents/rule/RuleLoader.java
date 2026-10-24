package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.action.ActionParser;
import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Properties;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.trigger.Triggers;
import dev.leo.omnievents.util.Durations;
import dev.leo.omnievents.util.Suggest;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Lee rules/**\/*.yml y compila cada regla, juntando todos los problemas (H1, H2 CA5, H3 CA4, H5 CA6). */
public final class RuleLoader {
    private static final Set<String> KNOWN_KEYS = Set.of(
            "trigger", "type", "enabled", "priority", "ignore_cancelled", "worlds", "permission", "bypass_permission",
            "ignore_with_permission", "conditions", "if", "cooldown", "cooldown_scope", "limit", "limit_scope", "limit_reset",
            "one_time", "actions", "then", "else", "on_cooldown", "on_limit", "every", "repetitive_time", "per_player", "at",
            "timezone", "move_mode", "command", "event", "player_from", "args", "description");

    private final Actions actions;
    private final Triggers triggers;

    public RuleLoader(Actions actions, Triggers triggers) {
        this.actions = actions;
        this.triggers = triggers;
    }

    public Map<String, Rule> loadFolder(File folder, Problems problems) {
        Map<String, Rule> out = new LinkedHashMap<>();
        Map<String, String> origin = new LinkedHashMap<>();
        List<File> files = new ArrayList<>();
        collect(folder, files);
        files.sort(null);
        for (File f : files) {
            String rel = folder.toPath().relativize(f.toPath()).toString().replace('\\', '/');
            problems.file(rel);
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.loadFromString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException e) {
                problems.error("", "YAML inválido: " + firstLine(e.getMessage()));
                continue;
            }
            for (String name : y.getKeys(false)) {
                if (!y.isConfigurationSection(name)) {
                    problems.error(name, "cada clave de primer nivel debe ser una regla (un mapa con trigger, conditions, actions…)");
                    continue;
                }
                String key = name.toLowerCase(Locale.ROOT);
                if (origin.containsKey(key)) {
                    problems.error(name, "el nombre de regla '" + name + "' ya existe en " + origin.get(key) + " (los nombres son únicos en todo el plugin)");
                    continue;
                }
                Rule r = parse(name, rel, y.getConfigurationSection(name), problems);
                if (r != null) {
                    out.put(key, r);
                    origin.put(key, rel);
                }
            }
        }
        return out;
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String[] lines = s.split("\n");
        return lines.length > 2 ? lines[0] + " " + lines[1].trim() + " " + lines[2].trim() : s.replace("\n", " ");
    }

    private static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".yml") || f.getName().endsWith(".yaml")) out.add(f);
        }
    }

    /** Compila una regla. Devuelve null si tiene errores (los problemas quedan registrados). */
    public Rule parse(String name, String file, ConfigurationSection s, Problems problems) {
        int mark = problems.size();
        for (String k : s.getKeys(false)) {
            if (!KNOWN_KEYS.contains(k)) problems.warn(name + "." + k, "opción desconocida" + Suggest.hint(k, KNOWN_KEYS));
        }
        String triggerName = s.getString("trigger", s.getString("type"));
        if (triggerName == null) {
            problems.error(name, "falta 'trigger' (p. ej. trigger: block_break)");
            return null;
        }
        TriggerType trigger = triggers.get(alias(triggerName));
        if (trigger == null) {
            problems.error(name + ".trigger", "disparador desconocido '" + triggerName + "'" + Suggest.hint(triggerName, triggers.names()) + " (lista: /omni triggers)");
            return null;
        }
        Predicate<Event> filter = null;
        if (trigger.kind() == TriggerType.Kind.CUSTOM) {
            trigger = customTrigger(name, s, problems);
            if (trigger == null) return null;
        }
        if (trigger.name().equals("player_move")) filter = moveFilter(s.getString("move_mode", "block"), name, problems);

        EventPriority priority = EventPriority.NORMAL;
        String pr = s.getString("priority");
        if (pr != null) {
            try {
                priority = EventPriority.valueOf(pr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                problems.error(name + ".priority", "prioridad inválida '" + pr + "' (lowest, low, normal, high, highest, monitor)");
            }
        }

        Expression.startCollecting();
        ActionParser parser = new ActionParser(actions, problems);
        List<Expression> conditions = new ArrayList<>();
        List<ActionNode> act, els, onCd, onLim;
        try {
            Object cond = s.get("conditions", s.get("if"));
            List<?> condList = cond instanceof List<?> l ? l : cond == null ? List.of() : List.of(cond);
            for (int i = 0; i < condList.size(); i++) {
                String c = Values.str(condList.get(i));
                String path = name + ".conditions" + (condList.size() > 1 || cond instanceof List<?> ? "[" + i + "]" : "");
                if (c.matches("(?is).*\\sexecute\\s+\\S+\\s*$")) {
                    problems.error(path, "'execute <grupo>' es de ConditionalEvents: usa un 'if' dentro de actions o /omni import conditionalevents");
                    continue;
                }
                try {
                    conditions.add(Expression.compile(c));
                } catch (ExprException e) {
                    problems.error(path, e.getMessage() + " en «" + c + "»");
                }
            }
            act = parser.parseList(s.get("actions", s.get("then")), name + ".actions");
            els = parser.parseList(s.get("else"), name + ".else");
            onCd = parser.parseList(s.get("on_cooldown"), name + ".on_cooldown");
            onLim = parser.parseList(s.get("on_limit"), name + ".on_limit");
        } finally {
            validateRoots(name, trigger, parser, Expression.stopCollecting(), problems);
        }
        if (act.isEmpty() && els.isEmpty() && !problems.hasErrorsSince(mark)) problems.warn(name, "la regla no tiene acciones");

        if (parser.usedTypes().contains("cancel") && trigger.kind() == TriggerType.Kind.EVENT && !trigger.cancellable()) {
            problems.warn(name, "'cancel' no tiene efecto: el evento de " + trigger.name() + " no se puede cancelar");
        }

        long cooldown = 0;
        if (s.contains("cooldown")) {
            try {
                cooldown = Durations.parseMillis(s.get("cooldown"));
            } catch (IllegalArgumentException e) {
                problems.error(name + ".cooldown", e.getMessage());
            }
        }
        boolean cdGlobal = scope(s.getString("cooldown_scope", "player"), name + ".cooldown_scope", problems);
        int limit = s.getBoolean("one_time") ? 1 : s.getInt("limit", 0);
        boolean limGlobal = scope(s.getString("limit_scope", "player"), name + ".limit_scope", problems);
        Rule.Reset reset = Rule.Reset.NEVER;
        long resetMs = 0;
        String rs = s.getString("limit_reset", "never").toLowerCase(Locale.ROOT);
        switch (rs) {
            case "never", "nunca" -> reset = Rule.Reset.NEVER;
            case "daily", "diario" -> reset = Rule.Reset.DAILY;
            case "weekly", "semanal" -> reset = Rule.Reset.WEEKLY;
            case "monthly", "mensual" -> reset = Rule.Reset.MONTHLY;
            default -> {
                try {
                    resetMs = Durations.parseMillis(rs);
                    reset = Rule.Reset.DURATION;
                } catch (IllegalArgumentException e) {
                    problems.error(name + ".limit_reset", "usa never, daily, weekly, monthly o una duración (" + e.getMessage() + ")");
                }
            }
        }
        if (!trigger.hasPlayer() && (cooldown > 0 && !cdGlobal || limit > 0 && !limGlobal)) {
            problems.warn(name, "el disparador " + trigger.name() + " no tiene jugador: un enfriamiento o límite por jugador no se aplica (usa cooldown_scope o limit_scope: global)");
        }

        switch (trigger.kind()) {
            case TIMER -> {
                Object every = s.get("every", s.get("repetitive_time") == null ? null : s.get("repetitive_time") + "t");
                if (every == null) problems.error(name, "un timer necesita 'every' (p. ej. every: 30s)");
                else try {
                    if (Durations.parseMillis(every) < 50) problems.error(name + ".every", "el intervalo mínimo es 1 tick (50ms)");
                } catch (IllegalArgumentException e) {
                    problems.error(name + ".every", e.getMessage());
                }
            }
            case CLOCK -> {
                List<String> at = s.isList("at") ? s.getStringList("at") : s.getString("at") == null ? List.of() : List.of(s.getString("at"));
                if (at.isEmpty()) problems.error(name, "un clock necesita 'at' (p. ej. at: [\"08:00\", \"20:30\"])");
                for (String t : at) if (!t.matches("([01]?\\d|2[0-3]):[0-5]\\d")) problems.error(name + ".at", "hora inválida '" + t + "' (formato HH:mm)");
                String tz = s.getString("timezone");
                if (tz != null) try {
                    java.time.ZoneId.of(tz);
                } catch (Exception e) {
                    problems.error(name + ".timezone", "zona horaria desconocida '" + tz + "' (p. ej. America/Mexico_City)");
                }
            }
            case COMMAND -> {
                ConfigurationSection c = s.getConfigurationSection("command");
                String cn = c == null ? s.getString("command") : c.getString("name");
                if (cn == null || !cn.matches("[A-Za-z0-9_\\-]{1,32}")) problems.error(name + ".command", "un trigger command necesita 'command.name' (letras, números, _ y -)");
            }
            default -> { }
        }

        if (problems.hasErrorsSince(mark)) return null;
        return new Rule(name, file, trigger, s.getBoolean("enabled", true), priority, s.getBoolean("ignore_cancelled", false),
                worlds(s), s.getString("permission"), s.getString("bypass_permission", s.getString("ignore_with_permission")),
                List.copyOf(conditions), cooldown, cdGlobal, limit, limGlobal, reset, resetMs, act, els, onCd, onLim, filter, s,
                s.getStringList("args"));
    }

    private static String alias(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return switch (l) {
            case "repetitive", "repetitive_server" -> "timer";
            case "player_leave" -> "player_quit";
            case "player_kill_entity" -> "player_kill";
            default -> l;
        };
    }

    private static boolean scope(String v, String path, Problems problems) {
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "player", "jugador" -> false;
            case "global", "server", "servidor" -> true;
            default -> {
                problems.error(path, "usa player o global");
                yield false;
            }
        };
    }

    private static Set<String> worlds(ConfigurationSection s) {
        List<String> w = s.isList("worlds") ? s.getStringList("worlds") : s.getString("worlds") == null ? List.of() : List.of(s.getString("worlds"));
        Set<String> out = new HashSet<>();
        for (String x : w) out.add(x.toLowerCase(Locale.ROOT));
        return out;
    }

    private void validateRoots(String rule, TriggerType trigger, ActionParser parser, List<Expression> exprs, Problems problems) {
        Set<String> allowed = new HashSet<>(trigger.roots());
        allowed.addAll(Triggers.COMMON_ROOTS);
        allowed.addAll(parser.declared());
        if (trigger.kind() == TriggerType.Kind.CALL || trigger.kind() == TriggerType.Kind.CUSTOM) return; // raíces dinámicas
        Set<String> warned = new HashSet<>();
        for (Expression e : exprs) {
            for (String root : e.roots()) {
                if (allowed.contains(root) || !warned.add(root)) continue;
                problems.warn(rule, "'" + root + "' no existe en el disparador " + trigger.name() + Suggest.hint(root, allowed)
                        + " (disponibles: " + String.join(", ", trigger.roots()) + ")");
            }
        }
    }

    private static Predicate<Event> moveFilter(String mode, String rule, Problems problems) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "any" -> null;
            case "chunk" -> e -> {
                PlayerMoveEvent m = (PlayerMoveEvent) e;
                return (m.getFrom().getBlockX() >> 4) != (m.getTo().getBlockX() >> 4) || (m.getFrom().getBlockZ() >> 4) != (m.getTo().getBlockZ() >> 4)
                        || m.getFrom().getWorld() != m.getTo().getWorld();
            };
            case "block" -> e -> {
                PlayerMoveEvent m = (PlayerMoveEvent) e;
                return m.getFrom().getBlockX() != m.getTo().getBlockX() || m.getFrom().getBlockY() != m.getTo().getBlockY()
                        || m.getFrom().getBlockZ() != m.getTo().getBlockZ() || m.getFrom().getWorld() != m.getTo().getWorld();
            };
            default -> {
                problems.error(rule + ".move_mode", "usa block, chunk o any");
                yield null;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static TriggerType customTrigger(String name, ConfigurationSection s, Problems problems) {
        String cls = s.getString("event");
        if (cls == null) {
            problems.error(name, "un trigger custom necesita 'event' con el nombre completo de la clase (p. ej. org.bukkit.event.player.PlayerEggThrowEvent)");
            return null;
        }
        Class<?> c = findClass(cls);
        if (c == null) {
            problems.error(name + ".event", "no se encontró la clase '" + cls + "' (¿el plugin que la define está instalado?)");
            return null;
        }
        if (!Event.class.isAssignableFrom(c)) {
            problems.error(name + ".event", cls + " no es un evento de Bukkit");
            return null;
        }
        String path = s.getString("player_from");
        Function<Event, Object> player;
        if (path == null) {
            player = e -> {
                Object p = Properties.get().read(e, "player");
                if (p == null) p = Properties.get().read(e, "entity");
                return Values.raw(p) instanceof Player ? Values.raw(p) : null;
            };
        } else {
            String[] steps = path.replace("()", "").split("\\.");
            player = e -> {
                Object cur = e;
                for (String st : steps) {
                    String k = st.startsWith("get") && st.length() > 3 ? st.substring(3) : st;
                    cur = Properties.get().read(cur, k);
                }
                return Values.raw(cur);
            };
        }
        return TriggerType.custom((Class<? extends Event>) c, player);
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, Bukkit.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // buscar en los plugins
        }
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            try {
                return Class.forName(name, false, p.getClass().getClassLoader());
            } catch (ClassNotFoundException ignored) {
                // siguiente
            }
        }
        return null;
    }
}
