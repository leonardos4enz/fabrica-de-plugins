package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.action.ActionParser;
import dev.leo.omnievents.action.Actions;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Properties;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.trigger.StateDeps;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.trigger.Triggers;
import dev.leo.omnievents.util.Durations;
import dev.leo.omnievents.util.Suggest;
import dev.leo.omnievents.util.Versions;
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
    /** Opciones de regla que se aceptan: salen del registro de {@link RuleOptions} (spec 007). */
    public static final Set<String> KNOWN_KEYS = RuleOptions.names();
    private static final Set<String> CONDITION_KEYS = RuleOptions.CONDITION_KEYS.keySet();
    /** Nombres de ConditionalEvents y sinónimos de disparadores → nombre propio. */
    public static final Map<String, String> TRIGGER_ALIASES = Map.of(
            "repetitive", "timer", "repetitive_server", "timer", "player_leave", "player_quit", "player_kill_entity", "player_kill");

    private final Actions actions;
    private final Triggers triggers;
    /** in_state('regla') que usa cada regla (spec 008, H5 CA1): se validan al terminar la carpeta. */
    private final Map<String, Set<String>> stateRefs = new LinkedHashMap<>();

    /** Versión de OmniEvents para requires de los paquetes (null: no se comprueba, p. ej. en unitarias sin plugin). */
    private final String pluginVersion;
    private final List<Pack> packs = new ArrayList<>();

    public RuleLoader(Actions actions, Triggers triggers) {
        this(actions, triggers, null);
    }

    public RuleLoader(Actions actions, Triggers triggers, String pluginVersion) {
        this.actions = actions;
        this.triggers = triggers;
        this.pluginVersion = pluginVersion == null ? null : pluginVersion.replaceFirst("[-+].*$", "");
    }

    /** Los paquetes de la última carga, también los que no cargaron (con su error). */
    public List<Pack> packs() {
        return List.copyOf(packs);
    }

    public static final String PACK_FILE = "pack.yml";
    private static final java.util.regex.Pattern PACK_NAME = java.util.regex.Pattern.compile("^[a-z0-9_\\-]{1,32}$");
    private static final java.util.regex.Pattern PARAM_NAME = java.util.regex.Pattern.compile("^[A-Za-z0-9_\\-]+$");

    /**
     * Carga rules/ en tres pasos (spec 011): paquetes (pack.yml), entradas de cada archivo (nombres paquete:regla) y
     * compilación, con la herencia y los parámetros resueltos. Sin pack.yml ni extends, igual que antes.
     */
    public Map<String, Rule> loadFolder(File folder, Problems problems) {
        packs.clear();
        List<File> files = new ArrayList<>();
        collect(folder, files);
        files.sort(null);
        Map<String, Pack> byDir = loadPacks(folder, files, problems);

        // Entradas: cada clave de primer nivel de cada archivo, con su nombre completo.
        Map<String, Templates.Entry> entries = new LinkedHashMap<>();
        Map<String, String> origin = new LinkedHashMap<>();
        for (File f : files) {
            if (f.getName().equals(PACK_FILE)) continue;
            String rel = rel(folder, f);
            Pack pack = packOf(rel, byDir);
            if (pack == SKIP || pack != null && !pack.loaded()) continue;
            problems.file(rel);
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.loadFromString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException e) {
                problems.error("", "YAML inválido: " + firstLine(e.getMessage()));
                continue;
            }
            for (String key : y.getKeys(false)) {
                String name = pack == null ? key : pack.name + ":" + key;
                if (!y.isConfigurationSection(key)) {
                    problems.error(name, "cada clave de primer nivel debe ser una regla (un mapa con trigger, conditions, actions…)");
                    continue;
                }
                if (key.indexOf(':') >= 0) {
                    if (pack != null) {
                        problems.error(name, "dentro del paquete " + pack.name + " el nombre va sin paquete (" + key.substring(key.lastIndexOf(':') + 1) + "): se llamará " + pack.name + ":…");
                        continue;
                    }
                    problems.warn(key, "los nombres con ':' se reservan para las reglas de un paquete (paquete:regla); cámbiale el nombre");
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (origin.containsKey(lower)) {
                    problems.error(name, "el nombre de regla '" + name + "' ya existe en " + origin.get(lower) + " (los nombres son únicos en todo el plugin)");
                    continue;
                }
                origin.put(lower, rel);
                ConfigurationSection s = y.getConfigurationSection(key);
                entries.put(lower, new Templates.Entry(name, rel, pack, Templates.plain(s), s));
            }
        }

        Set<String> templateNames = new java.util.TreeSet<>();
        for (Templates.Entry e : entries.values()) {
            if (e.template()) {
                templateNames.add(e.name());
                if (e.pack() != null) e.pack().templates.add(e.name());
            }
        }
        Map<String, Rule> out = new LinkedHashMap<>();
        for (Map.Entry<String, Templates.Entry> me : entries.entrySet()) {
            Templates.Entry e = me.getValue();
            if (e.template()) continue;
            Map<String, Object> m = Templates.expand(e, n -> entries.get(n.toLowerCase(Locale.ROOT)), templateNames, problems);
            if (m == null) continue;
            problems.file(e.file());
            ConfigurationSection s = m == e.map() ? e.section() : section(m);
            Rule r = parse(e.name(), e.file(), s, problems, e.pack());
            if (r != null) out.put(me.getKey(), r);
        }
        checkStateRefs(out, problems);
        for (Rule r : out.values()) {
            Pack p = r.pack == null ? null : byDir.values().stream().filter(x -> x != SKIP && x.name.equals(r.pack)).findFirst().orElse(null);
            if (p == null) continue;
            p.rules.add(r.name());
            if (!p.enabled) r.setEnabled(false);
        }
        return out;
    }

    /** Marca de una carpeta cuyos archivos se saltan (un pack.yml dentro de otro paquete). */
    private static final Pack SKIP = new Pack("", "", "");

    private static String rel(File folder, File f) {
        return folder.toPath().relativize(f.toPath()).toString().replace('\\', '/');
    }

    /** El paquete al que pertenece un archivo: el de la carpeta más cercana con pack.yml. */
    private static Pack packOf(String rel, Map<String, Pack> byDir) {
        String dir = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "";
        while (true) {
            Pack p = byDir.get(dir);
            if (p != null) return p;
            if (dir.isEmpty()) return null;
            dir = dir.contains("/") ? dir.substring(0, dir.lastIndexOf('/')) : "";
        }
    }

    /** Un mapa como sección, con los mapas anidados como secciones (como al leer el YAML). */
    @SuppressWarnings("unchecked")
    private static ConfigurationSection section(Map<String, Object> m) {
        org.bukkit.configuration.MemoryConfiguration c = new org.bukkit.configuration.MemoryConfiguration();
        m.forEach((k, v) -> {
            if (v instanceof Map<?, ?> map) c.createSection(k, (Map<String, Object>) map);
            else c.set(k, v);
        });
        return c;
    }

    // ------------------------------------------------------------------ paquetes (spec 011)

    /** Lee cada pack.yml; carpeta relativa → paquete (o SKIP). Los paquetes quedan en packs, con su error si no cargan. */
    private Map<String, Pack> loadPacks(File folder, List<File> files, Problems problems) {
        Map<String, Pack> byDir = new LinkedHashMap<>();
        List<String> dirs = new ArrayList<>();
        for (File f : files) if (f.getName().equals(PACK_FILE)) dirs.add(rel(folder, f.getParentFile()).replaceFirst("^\\.?$", ""));
        dirs.sort(null);
        Map<String, Pack> byName = new LinkedHashMap<>();
        for (String dir : dirs) {
            String file = dir.isEmpty() ? PACK_FILE : dir + "/" + PACK_FILE;
            problems.file(file);
            Pack outer = packOf(file, byDir);
            if (dir.isEmpty()) {
                problems.error("", "rules/ no puede ser un paquete: pon pack.yml en una carpeta dentro de rules/");
                byDir.put(dir, SKIP);
                continue;
            }
            if (outer != null) {
                problems.error("", "un paquete no puede estar dentro de otro (" + (outer == SKIP ? "carpeta omitida" : outer.folder) + "): esta carpeta se omite");
                byDir.put(dir, SKIP);
                continue;
            }
            String folderName = dir.contains("/") ? dir.substring(dir.lastIndexOf('/') + 1) : dir;
            Pack p = readPack(new File(folder, file), dir, file, folderName.toLowerCase(Locale.ROOT), problems);
            if (p.loaded() && byName.containsKey(p.name)) {
                problems.error("name", "ya hay un paquete '" + p.name + "' en " + byName.get(p.name).folder);
                p.error = "nombre repetido";
            }
            if (p.loaded()) byName.put(p.name, p);
            byDir.put(dir, p);
            packs.add(p);
        }
        // requires hasta un punto fijo: si un paquete requerido no carga, el que lo requiere tampoco (H3 CA1).
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Pack p : packs) {
                if (!p.loaded()) continue;
                String why = null;
                if (p.requiresOmni != null && pluginVersion != null && Versions.valid(pluginVersion) && !Versions.satisfies(pluginVersion, p.requiresOmni)) {
                    why = "necesita OmniEvents " + p.requiresOmni + " y este es " + pluginVersion;
                }
                for (Map.Entry<String, String> r : p.requiresPacks.entrySet()) {
                    if (why != null) break;
                    Pack dep = byName.get(r.getKey());
                    if (dep == null) why = "necesita el paquete " + r.getKey() + " " + r.getValue() + " y no está" + Suggest.hint(r.getKey(), byName.keySet());
                    else if (!dep.loaded()) why = "necesita el paquete " + r.getKey() + ", que no carga";
                    else if (!Versions.satisfies(dep.version, r.getValue())) why = "necesita el paquete " + r.getKey() + " " + r.getValue() + " y hay " + dep.version;
                }
                if (why != null) {
                    problems.file(p.file).error("requires", why + ": el paquete no carga");
                    p.error = why;
                    changed = true;
                }
            }
        }
        for (Pack p : packs) {
            if (!p.loaded() && p.error != null && !p.error.startsWith("necesita")) problems.file(p.file).error("", "el paquete no carga: sus reglas se omiten");
        }
        return byDir;
    }

    private Pack readPack(File f, String dir, String file, String folderName, Problems problems) {
        Pack fallback = new Pack(folderName, dir, file);
        YamlConfiguration y = new YamlConfiguration();
        // Las versiones de migrations llevan puntos ("1.2.0"): que no se lean como rutas.
        y.options().pathSeparator('\u0001');
        try {
            y.loadFromString(Files.readString(f.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            problems.error("", "YAML inválido: " + firstLine(e.getMessage()));
            fallback.error = "YAML inválido";
            return fallback;
        }
        int mark = problems.size();
        Map<String, Object> m = Templates.plain(y);
        for (String k : m.keySet()) {
            if (!RuleOptions.PACK_KEYS.containsKey(k)) problems.error(k, "clave desconocida en pack.yml" + Suggest.hint(k, RuleOptions.PACK_KEYS.keySet()));
        }
        String name = m.get("name") == null ? folderName : Values.str(m.get("name")).trim();
        if (!PACK_NAME.matcher(name).matches()) {
            problems.error(m.get("name") == null ? "" : "name", "nombre de paquete inválido '" + name + "' (minúsculas, números, _ y -; hasta 32)"
                    + (m.get("name") == null ? ": ponle name en pack.yml o cambia el nombre de la carpeta" : ""));
            name = folderName;
        }
        Pack p = new Pack(name, dir, file);
        if (m.get("version") != null) {
            String v = Values.str(m.get("version")).trim();
            if (Versions.valid(v)) p.version = v;
            else problems.error("version", "versión inválida '" + v + "' (usa x.y.z, p. ej. 1.2.0)");
        }
        p.description = m.get("description") == null ? null : Values.str(m.get("description"));
        p.author = m.get("author") == null ? null : Values.str(m.get("author"));
        if (m.get("enabled") != null) {
            if (m.get("enabled") instanceof Boolean b) p.enabled = b;
            else problems.error("enabled", "va true o false");
        }
        if (m.get("params") != null) {
            if (m.get("params") instanceof Map<?, ?> pm) {
                Map<String, Object> params = new LinkedHashMap<>();
                pm.forEach((k, v) -> {
                    String pk = String.valueOf(k);
                    if (!PARAM_NAME.matcher(pk).matches()) problems.error("params." + pk, "nombre de parámetro inválido (letras, números, _ y -)");
                    params.put(pk, v);
                });
                p.params = params;
            } else {
                problems.error("params", "params es un mapa nombre: valor (p. ej. { diaria: 50 })");
            }
        }
        Object req = m.get("requires");
        if (req instanceof Map<?, ?> rm) {
            for (Map.Entry<?, ?> e : rm.entrySet()) {
                String k = String.valueOf(e.getKey());
                switch (k) {
                    case "omnievents" -> {
                        String c = Values.str(e.getValue());
                        if (constraint(c, "requires.omnievents", problems)) p.requiresOmni = c;
                    }
                    case "packs" -> {
                        if (e.getValue() instanceof Map<?, ?> pk) {
                            pk.forEach((dn, dc) -> {
                                String c = Values.str(dc);
                                if (constraint(c, "requires.packs." + dn, problems)) p.requiresPacks.put(String.valueOf(dn).toLowerCase(Locale.ROOT), c);
                            });
                        } else {
                            problems.error("requires.packs", "packs es un mapa paquete: restricción (p. ej. { plantillas: \">=1.0.0\" })");
                        }
                    }
                    default -> problems.error("requires." + k, "clave desconocida" + Suggest.hint(k, RuleOptions.REQUIRES_KEYS.keySet()));
                }
            }
        } else if (req != null) {
            problems.error("requires", "requires es un mapa con omnievents y packs");
        }
        Object mig = m.get("migrations");
        if (mig instanceof Map<?, ?> mm) {
            List<Pack.Migration> list = new ArrayList<>();
            for (Map.Entry<?, ?> e : mm.entrySet()) {
                String v = String.valueOf(e.getKey()).trim();
                if (!Versions.valid(v)) {
                    problems.error("migrations." + v, "versión inválida '" + v + "' (usa x.y.z, p. ej. 1.2.0)");
                    continue;
                }
                org.bukkit.configuration.MemoryConfiguration c = new org.bukkit.configuration.MemoryConfiguration();
                c.set("trigger", "call");
                c.set("actions", e.getValue());
                Rule r = parse("migrations." + v, file, c, problems, p);
                if (r != null) list.add(new Pack.Migration(v, r));
            }
            list.sort((a, b) -> Versions.compare(a.version(), b.version()));
            p.migrations = List.copyOf(list);
        } else if (mig != null) {
            problems.error("migrations", "migrations es un mapa versión: acciones (p. ej. \"1.2.0\": [log: hola])");
        }
        if (problems.hasErrorsSince(mark)) p.error = "errores en pack.yml";
        return p;
    }

    private static boolean constraint(String c, String path, Problems problems) {
        try {
            Versions.constraint(c);
            return true;
        } catch (IllegalArgumentException e) {
            problems.error(path, e.getMessage());
            return false;
        }
    }

    /** Cada in_state('x') con un nombre fijo apunta a una regla state; un estado del servidor solo lee otros del servidor. */
    private void checkStateRefs(Map<String, Rule> out, Problems problems) {
        List<String> stateNames = new ArrayList<>();
        for (Rule r : out.values()) if (r.state() != null) stateNames.add(r.name());
        for (Map.Entry<String, Set<String>> e : stateRefs.entrySet()) {
            Rule r = out.get(e.getKey());
            if (r == null) continue;
            for (String ref : e.getValue()) {
                Rule target = Rule.resolve(ref, r, n -> out.get(n.toLowerCase(Locale.ROOT)));
                String msg = null;
                if (target == null || target.state() == null) {
                    msg = "in_state('" + ref + "'): no hay una regla state con ese nombre" + Suggest.hint(ref, stateNames);
                } else if (r.state() != null && !r.state().perPlayer() && target.state().perPlayer()) {
                    msg = "in_state('" + ref + "'): un estado del servidor no puede leer un estado por jugador";
                }
                if (msg != null) {
                    problems.file(r.file()).error(r.name() + (r.state() != null ? ".when" : ""), msg);
                    out.remove(e.getKey());
                    break;
                }
            }
        }
        stateRefs.clear();
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
        return parse(name, file, s, problems, null);
    }

    private Rule parse(String name, String file, ConfigurationSection s, Problems problems, Pack pack) {
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

        // Una regla state (spec 008) declara when, on_enter y on_exit en lugar de conditions, actions y else.
        boolean isState = trigger.kind() == TriggerType.Kind.STATE;
        if (isState) {
            for (String k : List.of("conditions", "if", "actions", "then", "else")) {
                if (s.contains(k)) problems.error(name + "." + k, "una regla state no usa '" + k + "': usa "
                        + (k.equals("conditions") || k.equals("if") ? "when" : k.equals("else") ? "on_exit" : "on_enter"));
            }
        }
        String condKey = isState ? "when" : "conditions";
        Expression.startCollecting();
        ActionParser parser = new ActionParser(actions, problems);
        List<Condition> conditions = new ArrayList<>();
        List<ActionNode> act, els, onCd, onLim, onExit;
        List<Expression> collected = List.of();
        try {
            Object cond = isState ? s.get("when") : s.get("conditions", s.get("if"));
            List<?> condList = cond instanceof List<?> l ? l : cond == null ? List.of() : List.of(cond);
            for (int i = 0; i < condList.size(); i++) {
                String path = name + "." + condKey + (condList.size() > 1 || cond instanceof List<?> ? "[" + i + "]" : "");
                Condition c = condition(condList.get(i), path, parser, problems, true);
                if (c != null) conditions.add(c);
            }
            act = parser.parseList(isState ? s.get("on_enter") : s.get("actions", s.get("then")), name + (isState ? ".on_enter" : ".actions"));
            els = isState ? List.of() : parser.parseList(s.get("else"), name + ".else");
            onExit = isState ? parser.parseList(s.get("on_exit"), name + ".on_exit") : List.of();
            onCd = parser.parseList(s.get("on_cooldown"), name + ".on_cooldown");
            onLim = parser.parseList(s.get("on_limit"), name + ".on_limit");
        } finally {
            collected = Expression.stopCollecting();
            validateRoots(name, trigger, parser, collected, problems);
        }
        Set<String> refs = StateDeps.analyze(collected).states();
        if (!refs.isEmpty()) stateRefs.put(name.toLowerCase(Locale.ROOT), Set.copyOf(refs));
        if (isState) {
            if (act.isEmpty() && onExit.isEmpty() && !problems.hasErrorsSince(mark)) problems.error(name, "una regla state necesita on_enter, on_exit o ambos");
        } else if (act.isEmpty() && els.isEmpty() && !problems.hasErrorsSince(mark)) {
            problems.warn(name, "la regla no tiene acciones");
        }

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
        StateSpec state = isState ? state(name, s, conditions, onExit, problems) : null;
        boolean noPlayer = !trigger.hasPlayer() || state != null && !state.perPlayer();
        if (noPlayer && (cooldown > 0 && !cdGlobal || limit > 0 && !limGlobal)) {
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

        long budget = 0;
        if (s.contains("budget")) {
            try {
                budget = Durations.parseBudgetNanos(s.get("budget"));
            } catch (IllegalArgumentException e) {
                problems.error(name + ".budget", e.getMessage());
            }
        }

        Set<String> expireVars = Set.of();
        if (trigger.kind() == TriggerType.Kind.VAR_EXPIRE && s.contains("variables")) {
            Object v = s.get("variables");
            if (v instanceof List<?> l) expireVars = l.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
            else if (v instanceof String one) expireVars = Set.of(one);
            else problems.error(name + ".variables", "variables es una lista de nombres (p. ej. variables: [racha, bono])");
        }
        if (problems.hasErrorsSince(mark)) return null;
        Rule rule = new Rule(name, file, trigger, s.getBoolean("enabled", true), priority, s.getBoolean("ignore_cancelled", false),
                worlds(s), s.getString("permission"), s.getString("bypass_permission", s.getString("ignore_with_permission")),
                isState ? List.of() : List.copyOf(conditions), cooldown, cdGlobal, limit, limGlobal, reset, resetMs, act, els, onCd, onLim, filter, s,
                s.getStringList("args"), budget, state);
        rule.writesObjectData = parser.writesObjectData();
        rule.expireVariables = expireVars;
        rule.pack = pack == null ? null : pack.name;
        // Spec 010: después de compilar la regla (assert no cuenta para las dependencias de un estado). Un caso mal
        // escrito se descarta con su error, pero la regla carga (H1 CA3).
        if (s.contains("tests")) rule.tests = tests(name, s.get("tests"), trigger, problems);
        return rule;
    }

    // ------------------------------------------------------------------ pruebas (spec 010)

    /** Clave de expect que es una variable: pvar.x, gvar.x, var.x u objeto.data.x (también en el esquema). */
    public static final String TEST_VAR_PATTERN = "^((pvar|gvar|var)\\.[A-Za-z0-9_\\-]+|.+\\.data\\.[^.\\s]+)$";
    private static final java.util.regex.Pattern TEST_SCOPED = java.util.regex.Pattern.compile("^(pvar|gvar|var)\\.([A-Za-z0-9_\\-]+)$");
    private static final java.util.regex.Pattern TEST_DATA = java.util.regex.Pattern.compile("^(.+?)\\.data\\.([\\p{L}0-9_]+)$");
    private static final Set<String> BRANCHES = Set.of("actions", "else", "deny", "none", "on_enter");

    private List<RuleTest> tests(String rule, Object raw, TriggerType trigger, Problems problems) {
        String base = rule + ".tests";
        if (!(raw instanceof List<?> list)) {
            problems.error(base, "tests es una lista de casos, cada uno con given y expect");
            return List.of();
        }
        Set<String> roots = new HashSet<>(trigger.roots());
        roots.addAll(Triggers.COMMON_ROOTS);
        boolean anyRoot = trigger.kind() == TriggerType.Kind.CALL || trigger.kind() == TriggerType.Kind.CUSTOM;
        List<RuleTest> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String path = base + "[" + i + "]";
            int mark = problems.size();
            if (!(list.get(i) instanceof Map<?, ?> m)) {
                problems.error(path, "cada caso es un mapa con given y expect");
                continue;
            }
            for (Object k : m.keySet()) {
                if (!RuleOptions.TEST_KEYS.containsKey(String.valueOf(k))) problems.error(path + "." + k, "clave desconocida" + Suggest.hint(String.valueOf(k), RuleOptions.TEST_KEYS.keySet()));
            }
            String name = m.get("name") == null ? "caso " + (i + 1) : Values.str(m.get("name"));
            List<Map.Entry<String, Object>> given = new ArrayList<>();
            Object g = m.get("given");
            if (g instanceof Map<?, ?> gm) {
                for (Map.Entry<?, ?> e : gm.entrySet()) {
                    String key = String.valueOf(e.getKey()).trim();
                    testRoot(key, path + ".given." + key, roots, anyRoot, problems);
                    given.add(Map.entry(key, e.getValue() == null ? NULL : e.getValue()));
                }
            } else if (g != null) {
                problems.error(path + ".given", "given es un mapa ruta: valor (p. ej. block.type: DIAMOND_ORE)");
            }
            long seed = 0;
            if (m.get("seed") != null) {
                Double d = Values.toNumberOrNull(m.get("seed"));
                if (d == null || d != Math.floor(d)) problems.error(path + ".seed", "seed es un número entero");
                else seed = d.longValue();
            }
            RuleTest.Expect expect = null;
            if (!(m.get("expect") instanceof Map<?, ?> em)) {
                problems.error(path + (m.containsKey("expect") ? ".expect" : ""), "cada caso necesita expect, un mapa con lo esperado (p. ej. fired: true)");
            } else {
                expect = expect(em, path + ".expect", roots, anyRoot, trigger, problems);
            }
            if (!problems.hasErrorsSince(mark)) out.add(new RuleTest(name, path, List.copyOf(given), seed, expect));
        }
        return List.copyOf(out);
    }

    /** Marca un valor null de given (Map.entry no acepta null). */
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    private RuleTest.Expect expect(Map<?, ?> m, String path, Set<String> roots, boolean anyRoot, TriggerType trigger, Problems problems) {
        Boolean fired = null, cancelled = null;
        String branch = null;
        List<RuleTest.VarCheck> vars = new ArrayList<>();
        List<String> messages = List.of(), commands = List.of(), acts = List.of();
        List<Expression> asserts = new ArrayList<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey()).trim();
            String p = path + "." + key;
            Object v = e.getValue();
            switch (key) {
                case "fired" -> fired = bool(v, p, problems);
                case "cancelled" -> cancelled = bool(v, p, problems);
                case "branch" -> {
                    branch = Values.str(v).trim().toLowerCase(Locale.ROOT);
                    if (!BRANCHES.contains(branch) && !branch.startsWith("deny ")) {
                        problems.error(p, "rama desconocida '" + branch + "'" + Suggest.hint(branch, BRANCHES) + " (actions, else, deny, deny conditions[1], none, on_enter)");
                    }
                }
                case "messages" -> messages = texts(v, p, problems);
                case "commands" -> commands = texts(v, p, problems);
                case "actions" -> {
                    acts = texts(v, p, problems);
                    for (String a : acts) {
                        String type = ActionParser.canonical(a.contains(":") ? a.substring(0, a.indexOf(':')) : a);
                        if (!actions.has(type)) problems.error(p, "acción desconocida '" + type + "'" + Suggest.hint(type, actions.names()));
                    }
                }
                case "assert" -> {
                    for (String src : texts(v, p, problems)) {
                        try {
                            asserts.add(Expression.compile(src));
                        } catch (ExprException ex) {
                            problems.error(p, ex.getMessage());
                        }
                    }
                }
                default -> {
                    if (!key.matches(TEST_VAR_PATTERN)) {
                        problems.error(p, "clave desconocida" + Suggest.hint(key, RuleOptions.EXPECT_KEYS.keySet())
                                + " (o una variable: pvar.x, gvar.x, var.x, block.data.x)");
                        continue;
                    }
                    testRoot(key, p, roots, anyRoot, problems);
                    try {
                        vars.add(new RuleTest.VarCheck(key, Expression.compile(key), v));
                    } catch (ExprException ex) {
                        problems.error(p, ex.getMessage());
                    }
                }
            }
        }
        boolean isState = trigger.kind() == TriggerType.Kind.STATE;
        if (branch != null && isState && !branch.equals("on_enter") && !branch.equals("none")) {
            problems.error(path + ".branch", "una regla state solo tiene las ramas on_enter y none");
        } else if (branch != null && !isState && branch.equals("on_enter")) {
            problems.error(path + ".branch", "on_enter es la rama de una regla state; esta usa actions");
        }
        return new RuleTest.Expect(fired, branch, List.copyOf(vars), messages, commands, acts, cancelled, List.copyOf(asserts));
    }

    /** La raíz de una clave de given o de una variable de expect existe (H1 CA3). */
    private static void testRoot(String key, String path, Set<String> roots, boolean anyRoot, Problems problems) {
        if (key.startsWith("%") && key.endsWith("%") && key.length() > 2) return;
        java.util.regex.Matcher sc = TEST_SCOPED.matcher(key);
        if (sc.matches()) return;
        java.util.regex.Matcher d = TEST_DATA.matcher(key);
        String obj = d.matches() ? d.group(1) : key;
        String root = obj.contains(".") ? obj.substring(0, obj.indexOf('.')) : obj;
        if (root.equals("pvar") || root.equals("gvar") || root.equals("var")) {
            problems.error(path, "una variable se escribe " + root + ".nombre (letras, números, _ y -)");
            return;
        }
        if (anyRoot || roots.contains(root)) return;
        problems.error(path, "'" + root + "' no existe en este disparador" + Suggest.hint(root, roots) + " (o usa pvar.x, gvar.x, var.x o un %placeholder%)");
    }

    private static Boolean bool(Object v, String path, Problems problems) {
        if (v instanceof Boolean b) return b;
        problems.error(path, "va true o false");
        return null;
    }

    private static List<String> texts(Object v, String path, Problems problems) {
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object o : l) {
                if (o instanceof Map<?, ?> || o instanceof List<?>) problems.error(path, "va un texto o una lista de textos");
                else out.add(Values.str(o));
            }
            return List.copyOf(out);
        }
        if (v instanceof Map<?, ?> || v == null) {
            problems.error(path, "va un texto o una lista de textos");
            return List.of();
        }
        return List.of(Values.str(v));
    }

    /** Lo propio de una regla state (spec 008, H1 CA5, H2 CA3–CA4, H6 CA2). Los errores quedan en problems. */
    private StateSpec state(String name, ConfigurationSection s, List<Condition> when, List<ActionNode> onExit, Problems problems) {
        if (!s.contains("when")) {
            problems.error(name, "una regla state necesita 'when' (p. ej. when: \"player.health <= 6\")");
            return null;
        }
        List<Expression> exprs = new ArrayList<>();
        for (Condition c : when) {
            if (!c.deny().isEmpty()) problems.warn(c.path() + ".deny", "deny no se usa en when (se ignora): usa on_enter y on_exit");
            expressions(c, exprs);
        }
        boolean perPlayer = s.getBoolean("per_player", true);
        StateDeps deps = StateDeps.analyze(exprs);
        if (perPlayer && !worlds(s).isEmpty()) deps.add(StateDeps.Source.MOVE);
        long every = 0;
        if (s.contains("check_every")) {
            try {
                long ms = Durations.parseMillis(s.get("check_every"));
                if (ms < 50) problems.error(name + ".check_every", "el intervalo mínimo es 1 tick (50ms)");
                every = Math.max(1, Durations.toTicks(ms));
            } catch (IllegalArgumentException e) {
                problems.error(name + ".check_every", e.getMessage());
            }
        }
        if (!deps.random().isEmpty()) {
            problems.error(name + ".when", "un estado no puede depender del azar (" + String.join(", ", deps.random()) + "): usa chance o random en las acciones");
        }
        if (deps.states().contains(name.toLowerCase(Locale.ROOT))) {
            problems.error(name + ".when", "un estado no puede depender de sí mismo (in_state('" + name + "'))");
        }
        if (!perPlayer && !deps.playerUses().isEmpty()) {
            problems.error(name + ".when", "un estado del servidor (per_player: false) no tiene jugador: no puede usar " + String.join(", ", deps.playerUses()));
        }
        if (!deps.unknown().isEmpty() && every == 0) {
            problems.error(name + ".when", "no se puede deducir cuándo cambia " + String.join(", ", deps.unknown())
                    + ": agrega check_every (p. ej. check_every: 5s) o usa algo que el motor sigue (vida, ubicación, pvar…)");
        }
        List<String> watchNames = s.isList("watch") ? s.getStringList("watch") : s.getString("watch") == null ? List.of() : List.of(s.getString("watch"));
        List<TriggerType> watch = new ArrayList<>();
        List<String> eventTriggers = new ArrayList<>();
        for (TriggerType t : triggers.all()) if (t.eventClass() != null) eventTriggers.add(t.name());
        for (String w : watchNames) {
            TriggerType t = triggers.get(alias(w));
            if (t == null || t.eventClass() == null) {
                problems.error(name + ".watch", "'" + w + "' no es un disparador de evento" + Suggest.hint(w, eventTriggers) + " (lista: /omni triggers)");
            } else {
                watch.add(t);
            }
        }
        if (deps.constant() && every == 0 && watch.isEmpty() && !when.isEmpty()) {
            problems.warn(name + ".when", "when no depende de nada que cambie: el estado solo se revisa al entrar al servidor o al cargar");
        }
        String initial = s.getString("initial", "enter").toLowerCase(Locale.ROOT);
        if (!initial.equals("enter") && !initial.equals("silent")) problems.error(name + ".initial", "usa enter o silent");
        List<String> src = new ArrayList<>();
        for (Condition c : when) src.add(c.source());
        List<String> w = new ArrayList<>(worlds(s));
        w.sort(null);
        String fingerprint = String.join(" && ", src) + "|" + w + "|" + perPlayer;
        return new StateSpec(List.copyOf(when), onExit, deps, every, perPlayer, initial.equals("silent"), List.copyOf(watch), fingerprint);
    }

    private static void expressions(Condition c, List<Expression> out) {
        switch (c) {
            case Condition.Single x -> out.add(x.expr());
            case Condition.AnyOf a -> a.of().forEach(o -> expressions(o, out));
        }
    }

    /**
     * Un elemento de conditions (spec 006, H1 y H2): un texto (forma corta de siempre), o un mapa con expr o any + of,
     * y deny opcional. Devuelve null si tiene errores (quedan registrados).
     */
    private Condition condition(Object raw, String path, ActionParser parser, Problems problems, boolean denyAllowed) {
        if (!(raw instanceof Map<?, ?> || raw instanceof ConfigurationSection)) {
            String c = Values.str(raw);
            if (c.matches("(?is).*\\sexecute\\s+\\S+\\s*$")) {
                problems.error(path, "'execute <grupo>' es de ConditionalEvents: usa un 'if' dentro de actions o /omni import conditionalevents");
                return null;
            }
            try {
                return new Condition.Single(path, Expression.compile(c), List.of());
            } catch (ExprException e) {
                problems.error(path, e.getMessage() + " en «" + c + "»");
                return null;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        if (raw instanceof ConfigurationSection cs) cs.getKeys(false).forEach(k -> m.put(k, cs.get(k)));
        else ((Map<?, ?>) raw).forEach((k, v) -> m.put(String.valueOf(k), v));
        for (String k : m.keySet()) {
            if (!CONDITION_KEYS.contains(k)) problems.warn(path + "." + k, "clave desconocida en una condición" + Suggest.hint(k, CONDITION_KEYS) + " (usa expr, any, of o deny)");
        }
        List<ActionNode> deny = List.of();
        if (m.containsKey("deny")) {
            if (denyAllowed) deny = parser.parseList(m.get("deny"), path + ".deny");
            else problems.warn(path + ".deny", "un deny dentro de 'of' no se usa: ponlo en el grupo any (se ignora)");
        }
        boolean hasExpr = m.containsKey("expr"), hasAny = m.containsKey("any");
        if (hasExpr == hasAny) {
            problems.error(path, hasExpr ? "una condición lleva 'expr' o 'any', no ambas" : "una condición en forma de mapa necesita 'expr' (o 'any' con 'of')");
            return null;
        }
        if (hasExpr) {
            if (m.containsKey("of")) problems.warn(path + ".of", "'of' solo se usa con 'any' (se ignora)");
            String c = Values.str(m.get("expr"));
            try {
                return new Condition.Single(path, Expression.compile(c), deny);
            } catch (ExprException e) {
                problems.error(path + ".expr", e.getMessage() + " en «" + c + "»");
                return null;
            }
        }
        if (!(m.get("of") instanceof List<?> of) || of.isEmpty()) {
            problems.error(path + ".of", "'any' necesita 'of' con una lista de condiciones");
            return null;
        }
        Double n = m.get("any") instanceof Number num ? num.doubleValue() : Values.toNumberOrNull(Values.str(m.get("any")));
        if (n == null || n != Math.floor(n) || n < 1 || n > of.size()) {
            problems.error(path + ".any", "'any' debe ser un entero entre 1 y " + of.size() + " (el número de condiciones en 'of')");
            return null;
        }
        List<Condition> items = new ArrayList<>();
        boolean ok = true;
        for (int i = 0; i < of.size(); i++) {
            Condition c = condition(of.get(i), path + ".of[" + i + "]", parser, problems, false);
            if (c == null) ok = false;
            else items.add(c);
        }
        return ok ? new Condition.AnyOf(path, n.intValue(), List.copyOf(items), deny) : null;
    }

    private static String alias(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return TRIGGER_ALIASES.getOrDefault(l, l);
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
