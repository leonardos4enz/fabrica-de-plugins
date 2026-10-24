package dev.leo.omnievents.importer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte configuraciones de ConditionalEvents (Ajneb97) al formato de OmniEvents (H10). No modifica el original.
 * Lo que no se puede traducir se deja como acción log «REVISAR» para que se vea al cargar y al ejecutar.
 */
public final class ConditionalEventsImporter {
    public record Report(int events, int review, List<String> notes) {
    }

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("player_leave", "player_quit"), Map.entry("player_pre_join", "player_pre_login"),
            Map.entry("player_levelup", "player_level_change"), Map.entry("player_armor", "player_armor_change"),
            Map.entry("player_offhand", "player_swap_hands"), Map.entry("player_open_inventory", "inventory_open"),
            Map.entry("player_close_inventory", "inventory_close"), Map.entry("player_click_inventory", "inventory_click"),
            Map.entry("player_run", "player_sprint"), Map.entry("player_change_food", "player_food_change"),
            Map.entry("item_select", "item_held"), Map.entry("repetitive", "timer"), Map.entry("repetitive_server", "timer"));

    /** Variables de ConditionalEvents → expresión de OmniEvents. */
    private static final Map<String, String> VARS = Map.ofEntries(
            Map.entry("player", "player.name"), Map.entry("main_command", "('/' + cmd.name)"), Map.entry("command", "('/' + cmd.full)"),
            Map.entry("args_length", "args.size"), Map.entry("message", "message"), Map.entry("block", "block.type"),
            Map.entry("block_x", "block.x"), Map.entry("block_y", "block.y"), Map.entry("block_z", "block.z"),
            Map.entry("block_world", "block.world.name"), Map.entry("item", "item.type"), Map.entry("item_name", "item.name"),
            Map.entry("item_amount", "item.amount"), Map.entry("item_lore", "item.lore"), Map.entry("action_type", "action"),
            Map.entry("victim", "target.type"), Map.entry("victim_name", "target.name"), Map.entry("entity", "target.type"),
            Map.entry("entity_name", "target.name"), Map.entry("damage", "event.damage"), Map.entry("cause", "cause"),
            Map.entry("killer_name", "killer.name"), Map.entry("killer_type", "killer.type"), Map.entry("world_from", "from_world.name"),
            Map.entry("world_to", "player.world.name"), Map.entry("old_level", "old_level"), Map.entry("new_level", "new_level"),
            Map.entry("is_sneaking", "sneaking"), Map.entry("is_running", "sprinting"), Map.entry("empty", "''"),
            Map.entry("respawn_world", "location.world.name"), Map.entry("x", "location.block_x"), Map.entry("y", "location.block_y"),
            Map.entry("z", "location.block_z"), Map.entry("inventory_title", "title"), Map.entry("inventory_type", "inventory_type"),
            Map.entry("click_type", "click"), Map.entry("slot", "slot"), Map.entry("name", "name"), Map.entry("ip", "ip"),
            Map.entry("uuid", "uuid"), Map.entry("reason", "reason"), Map.entry("state", "state"));

    private static final String[] OPS = {"!equalsIgnoreCase", "equalsIgnoreCase", "!startsWith", "startsWith", "!endsWith", "endsWith",
            "!contains", "contains", "!matches", "matches", "!equals", "equals", "==", "!=", ">=", "<=", ">", "<"};
    private static final Pattern VAR = Pattern.compile("%([A-Za-z0-9_:{}]+)%");
    private static final Pattern EXECUTE = Pattern.compile("^(.*?)\\s+execute\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);

    private int review;
    private final List<String> notes = new ArrayList<>();

    public Report importFolder(File ceFolder, File outFolder) {
        int events = 0;
        if (!ceFolder.isDirectory()) {
            notes.add("no se encontró la carpeta " + ceFolder.getPath());
            return new Report(0, 0, notes);
        }
        List<File> files = new ArrayList<>();
        File config = new File(ceFolder, "config.yml");
        if (config.exists()) files.add(config);
        collect(new File(ceFolder, "events"), files);
        outFolder.mkdirs();
        for (File f : files) {
            YamlConfiguration in = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection root = in.isConfigurationSection("Events") ? in.getConfigurationSection("Events") : f.equals(config) ? null : in;
            if (root == null) continue;
            YamlConfiguration out = new YamlConfiguration();
            int n = 0;
            for (String name : root.getKeys(false)) {
                ConfigurationSection ev = root.getConfigurationSection(name);
                if (ev == null || !ev.contains("type")) continue;
                out.set(name, convertEvent(name, ev));
                n++;
            }
            if (n == 0) continue;
            events += n;
            String outName = (f.equals(config) ? "config" : f.getName().replaceAll("\\.ya?ml$", "")) + ".yml";
            try {
                out.options().setHeader(List.of("Importado de ConditionalEvents (" + f.getName() + ") por /omni import.",
                        "Busca 'REVISAR' para lo que no se pudo traducir automáticamente."));
                out.save(new File(outFolder, outName));
            } catch (IOException e) {
                notes.add("no se pudo escribir " + outName + ": " + e.getMessage());
            }
        }
        return new Report(events, review, notes);
    }

    private static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) collect(f, out);
            else if (f.getName().endsWith(".yml")) out.add(f);
        }
    }

    Map<String, Object> convertEvent(String name, ConfigurationSection ev) {
        Map<String, Object> r = new LinkedHashMap<>();
        String type = ev.getString("type", "").toLowerCase(Locale.ROOT);
        r.put("trigger", TYPES.getOrDefault(type, type));
        if (type.equals("repetitive_server")) r.put("per_player", false);
        if (ev.contains("repetitive_time")) r.put("every", ev.getInt("repetitive_time") + "t");
        if (ev.contains("enabled")) r.put("enabled", ev.getBoolean("enabled"));
        if (ev.contains("ignore_with_permission")) r.put("bypass_permission", ev.getString("ignore_with_permission"));
        if (ev.getBoolean("ignore_if_cancelled")) r.put("ignore_cancelled", true);
        if (ev.contains("cooldown")) r.put("cooldown", ev.getInt("cooldown") + "s");
        if (ev.getBoolean("one_time")) r.put("limit", 1);
        if (ev.isConfigurationSection("custom_event_data")) {
            ConfigurationSection c = ev.getConfigurationSection("custom_event_data");
            r.put("event", c.getString("event"));
            if (c.contains("player_variable")) r.put("player_from", c.getString("player_variable"));
            if (c.contains("variables_to_capture")) note(name, "variables_to_capture: usa event.<propiedad> directamente en las expresiones");
        }
        ConfigurationSection groups = ev.getConfigurationSection("actions");
        Map<String, List<Object>> converted = new LinkedHashMap<>();
        if (groups != null) for (String g : groups.getKeys(false)) converted.put(g, convertActions(name, groups.getStringList(g)));
        List<Object> def = converted.getOrDefault("default", List.of());
        if (converted.containsKey("cooldown")) r.put("on_cooldown", converted.get("cooldown"));
        if (converted.containsKey("one_time")) r.put("on_limit", converted.get("one_time"));

        List<String> lines = ev.getStringList("conditions");
        List<String> plain = new ArrayList<>();
        int i = 0;
        while (i < lines.size() && !EXECUTE.matcher(lines.get(i)).matches()) plain.add(convertCondition(lines.get(i++)));
        if (!plain.isEmpty()) r.put("conditions", plain);
        r.put("actions", i < lines.size() ? nest(name, lines, i, converted, def) : def);
        return r;
    }

    /** Una línea normal que falla detiene todo; una con execute que se cumple corre su grupo (orden de ConditionalEvents). */
    private List<Object> nest(String event, List<String> lines, int i, Map<String, List<Object>> groups, List<Object> def) {
        if (i >= lines.size()) return def;
        String line = lines.get(i);
        Matcher m = EXECUTE.matcher(line);
        Map<String, Object> node = new LinkedHashMap<>();
        if (m.matches()) {
            List<Object> group = groups.get(m.group(2));
            if (group == null) {
                note(event, "el grupo '" + m.group(2) + "' no existe");
                group = List.of(review("grupo inexistente " + m.group(2)));
            }
            node.put("if", convertCondition(m.group(1)));
            node.put("then", group);
            node.put("else", nest(event, lines, i + 1, groups, def));
        } else {
            node.put("if", convertCondition(line));
            node.put("then", nest(event, lines, i + 1, groups, def));
        }
        return List.of(node);
    }

    /** "%main_command% == /checklevel or %args_length% < 1" → "('/' + cmd.name) == '/checklevel' || args.size < 1" */
    public String convertCondition(String line) {
        String sep = null;
        if (line.contains(" or ")) sep = " or ";
        else if (line.contains(" and ")) sep = " and ";
        if (sep != null) {
            List<String> parts = new ArrayList<>();
            for (String p : line.split(Pattern.quote(sep))) parts.add(convertCondition(p));
            return String.join(sep.equals(" or ") ? " || " : " && ", parts);
        }
        String t = line.trim();
        for (String op : OPS) {
            int at = indexOfOp(t, op);
            if (at < 0) continue;
            String left = operand(t.substring(0, at).trim());
            String right = operand(t.substring(at + op.length()).trim());
            String o = switch (op) {
                case "equals" -> "==";
                case "!equals" -> "!=";
                case "equalsIgnoreCase" -> "~=";
                case "!equalsIgnoreCase" -> "!~=";
                default -> op;
            };
            return left + " " + o + " " + right;
        }
        return operand(t);
    }

    private static int indexOfOp(String t, String op) {
        boolean word = Character.isLetter(op.charAt(op.length() - 1));
        int from = 0;
        while (true) {
            int i = t.indexOf(word ? " " + op + " " : " " + op + " ", from);
            if (i < 0) return -1;
            // '!contains' no debe confundirse con 'contains'
            if (!op.startsWith("!") && i + 1 < t.length() && t.charAt(i + 1) == '!') {
                from = i + 1;
                continue;
            }
            return i + 1;
        }
    }

    /** Un lado de la comparación: número, una variable o texto con variables. */
    String operand(String s) {
        if (s.matches("-?\\d+(\\.\\d+)?")) return s;
        Matcher m = VAR.matcher(s);
        if (m.matches()) return var(m.group(1));
        List<String> pieces = new ArrayList<>();
        int last = 0;
        m.reset();
        while (m.find()) {
            if (m.start() > last) pieces.add(quote(s.substring(last, m.start())));
            pieces.add(var(m.group(1)));
            last = m.end();
        }
        if (last < s.length() || pieces.isEmpty()) pieces.add(quote(s.substring(last)));
        return pieces.size() == 1 ? pieces.get(0) : "(" + String.join(" + ", pieces) + ")";
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String var(String v) {
        String k = v.toLowerCase(Locale.ROOT);
        if (VARS.containsKey(k)) return VARS.get(k);
        Matcher arg = Pattern.compile("arg_(\\d+)").matcher(k);
        if (arg.matches()) return "args[" + (Integer.parseInt(arg.group(1)) - 1) + "]";
        if (k.startsWith("target:")) return "papi('%" + v.substring(7) + "%', target)";
        return "%" + v + "%";
    }

    /** Texto con %variables% → texto con {expresiones} (las desconocidas quedan para PlaceholderAPI). */
    String text(String s) {
        Matcher m = VAR.matcher(s.replace("{", "\\{").replace("}", "\\}"));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = var(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(expr.startsWith("%") ? expr : "{" + expr + "}"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    List<Object> convertActions(String event, List<String> lines) {
        List<Object> out = new ArrayList<>();
        for (String line : lines) {
            Object a = convertAction(event, line);
            if (a != null) out.add(a);
        }
        return out;
    }

    private Object convertAction(String event, String line) {
        String prefix = "";
        String t = line.trim();
        Matcher pre = Pattern.compile("^(to_all|to_target|to_world: *[^:]+|to_player: *[^:]+|to_range: *[^:]+|to_condition: *[^:]+):\\s*(.*)$").matcher(t);
        if (pre.matches()) {
            if (pre.group(1).startsWith("to_condition")) {
                note(event, "to_condition no existe: usa 'to' con 'where'");
                return review(line);
            }
            prefix = pre.group(1) + ": ";
            t = pre.group(2);
        }
        int c = t.indexOf(':');
        String type = (c < 0 ? t : t.substring(0, c)).trim().toLowerCase(Locale.ROOT);
        String v = c < 0 ? "" : t.substring(c + 1).trim();
        String[] p = v.split(";");
        String res = switch (type) {
            case "message", "centered_message", "json_message", "mini_message" -> "message: " + text(v);
            case "console_message" -> "console_message: " + text(v);
            case "console_command" -> "console: " + text(v);
            case "player_command" -> "command: " + text(v);
            case "player_send_chat" -> "chat: " + text(v);
            case "kick", "send_to_server", "teleport", "gamemode", "damage", "heal", "freeze", "set_damage", "set_death_message" -> type + ": " + text(v);
            case "actionbar" -> "actionbar: " + text(p[0]);
            case "title" -> p.length >= 5 ? "title: " + text(p[3]) + ";" + text(p[4]) + ";" + p[0] + ";" + p[1] + ";" + p[2] : "title: " + text(v);
            case "playsound" -> "sound: " + p[0] + (p.length > 2 ? ";" + p[1] + ";" + p[2] : "");
            case "stopsound" -> "stop_sound: " + v;
            case "cancel_event" -> "cancel: " + v;
            case "give_potion_effect" -> "effect: " + p[0] + ";" + (p.length > 1 ? seconds(p[1]) : "10") + ";" + (p.length > 2 ? p[2] : "1");
            case "remove_potion_effect" -> "remove_effect: " + v;
            case "set_on_fire" -> "fire: " + v;
            case "set_food_level" -> "set_food: " + v;
            case "close_inventory", "clear_inventory" -> type;
            case "wait" -> "delay: " + v + "s";
            case "wait_ticks" -> "delay: " + v + "t";
            case "keep_items" -> "keep_inventory: true";
            case "cancel_drop" -> "cancel_drops: " + v;
            case "hide_join_message" -> "set_join_message: none";
            case "hide_leave_message" -> "set_quit_message: none";
            case "prevent_join" -> "deny_join: " + text(v);
            case "lightning_strike" -> "lightning: " + v;
            case "remove_item" -> "remove_item: " + p[0] + ";" + (p.length > 1 ? p[1] : "1");
            case "throw_directional" -> "launch: " + v;
            case "call_event" -> {
                StringBuilder sb = new StringBuilder("call: " + p[0]);
                for (int i = 1; i < p.length; i++) {
                    int eq = p[i].indexOf('=');
                    if (eq > 0) sb.append("; ").append(p[i].substring(0, eq).replace("%", "").trim()).append("=").append(quote(p[i].substring(eq + 1)));
                }
                yield sb.toString();
            }
            case "particle" -> {
                Map<String, String> kv = keyValues(v);
                yield kv.containsKey("effect") ? "particle: " + kv.get("effect") + ";" + kv.getOrDefault("amount", "10") : null;
            }
            case "summon" -> {
                Map<String, String> kv = keyValues(v);
                String loc = kv.get("location");
                yield kv.containsKey("entity") ? "summon: " + kv.get("entity") + (loc == null ? "" : ";" + loc.replace(",", ";").replaceAll("^(.*);(.*);(.*);(.*)$", "$4;$1;$2;$3")) : null;
            }
            case "give_item" -> {
                Map<String, String> kv = keyValues(v);
                yield kv.containsKey("id") ? "give_item: " + kv.get("id") + ";" + kv.getOrDefault("amount", "1")
                        + (kv.containsKey("name") ? ";" + text(kv.get("name")) : "") : null;
            }
            default -> null;
        };
        if (res == null) {
            if (type.equals("player_command_as_op")) note(event, "player_command_as_op no existe en OmniEvents por seguridad (ver plan 001 STRIDE)");
            return review(line);
        }
        return prefix + res;
    }

    /** Ticks de ConditionalEvents → segundos de OmniEvents (-1 = infinito se conserva). */
    private static String seconds(String ticks) {
        try {
            int t = Integer.parseInt(ticks.trim());
            return t < 0 ? "-1" : dev.leo.omnievents.expr.Values.formatNumber(t / 20.0);
        } catch (NumberFormatException e) {
            return ticks;
        }
    }

    private static Map<String, String> keyValues(String v) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String part : v.split(";")) {
            int c = part.indexOf(':');
            if (c > 0) m.put(part.substring(0, c).trim().toLowerCase(Locale.ROOT), part.substring(c + 1).trim());
        }
        return m;
    }

    private String review(String original) {
        review++;
        return "log: REVISAR (ConditionalEvents) " + original.replace("{", "\\{").replace("}", "\\}");
    }

    private void note(String event, String msg) {
        notes.add(event + ": " + msg);
    }
}
