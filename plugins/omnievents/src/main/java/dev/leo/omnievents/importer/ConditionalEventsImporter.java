package dev.leo.omnievents.importer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte configuraciones de ConditionalEvents (Ajneb97) al formato de OmniEvents (H10). No modifica el original.
 * Lo que no se puede traducir se deja como acción log «REVISAR» para que se vea al cargar y al ejecutar. La spec 014
 * lo completó contra el jar 4.65.1: cada evento, acción, variable y opción con equivalente se traduce.
 */
public final class ConditionalEventsImporter {
    public record Report(int events, int review, List<String> notes) {
    }

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("player_leave", "player_quit"), Map.entry("player_pre_join", "player_pre_login"),
            Map.entry("player_levelup", "player_level_change"), Map.entry("player_armor", "player_armor_change"),
            Map.entry("player_offhand", "player_swap_hands"), Map.entry("player_swap_hand", "player_swap_hands"),
            Map.entry("player_open_inventory", "inventory_open"), Map.entry("player_close_inventory", "inventory_close"),
            Map.entry("player_click_inventory", "inventory_click"), Map.entry("item_move", "inventory_click"),
            Map.entry("player_run", "player_sprint"), Map.entry("player_change_food", "player_food_change"),
            Map.entry("item_select", "item_held"), Map.entry("repetitive", "timer"), Map.entry("repetitive_server", "timer"),
            Map.entry("citizens_right_click_npc", "npc_click"), Map.entry("wgevents_region_enter", "region_enter"),
            Map.entry("wgevents_region_leave", "region_leave"));

    /** Eventos de ConditionalEvents sin disparador en OmniEvents (spec 014, fuera de alcance o «no»). */
    private static final Map<String, String> NO_TYPE = Map.of(
            "player_statistic", "aún no hay disparador player_statistic en OmniEvents",
            "player_change_air", "aún no hay disparador de cambio de aire en OmniEvents",
            "player_tab_complete", "aún no hay disparador player_tab_complete en OmniEvents",
            "protocollib_receive_message", "OmniEvents no lee paquetes (ProtocolLib)",
            "player_receive_chat", "OmniEvents no lee paquetes (PacketEvents)");

    /** Variables de ConditionalEvents → expresión de OmniEvents. */
    private static final Map<String, String> VARS = Map.ofEntries(
            Map.entry("player", "player.name"), Map.entry("main_command", "('/' + cmd.name)"), Map.entry("command", "('/' + cmd.full)"),
            Map.entry("args_length", "args.size"), Map.entry("message", "message"), Map.entry("block", "block.type"),
            Map.entry("block_x", "block.x"), Map.entry("block_y", "block.y"), Map.entry("block_z", "block.z"),
            Map.entry("block_world", "block.world.name"), Map.entry("block_data", "block.data"), Map.entry("action_type", "action"),
            Map.entry("face", "face"), Map.entry("victim", "target.type"), Map.entry("victim_name", "target.name"),
            Map.entry("victim_color_format_name", "target.name"), Map.entry("victim_uuid", "target.uuid"),
            Map.entry("victim_health", "target.health"), Map.entry("victim_block_x", "target.block_x"),
            Map.entry("victim_block_y", "target.block_y"), Map.entry("victim_block_z", "target.block_z"),
            Map.entry("victim_block_world", "target.world.name"), Map.entry("entity", "target.type"),
            Map.entry("entity_name", "target.name"), Map.entry("entity_color_format_name", "target.name"),
            Map.entry("entity_x", "target.x"), Map.entry("entity_y", "target.y"), Map.entry("entity_z", "target.z"),
            Map.entry("entity_world", "target.world.name"), Map.entry("entity_uuid", "target.uuid"),
            Map.entry("damage", "event.damage"), Map.entry("cause", "cause"), Map.entry("killer_name", "killer.name"),
            Map.entry("killer_color_format_name", "killer.name"), Map.entry("killer_type", "killer.type"),
            Map.entry("damager_type", "damager.type"), Map.entry("damager_name", "damager.name"),
            Map.entry("damager_color_format_name", "damager.name"), Map.entry("is_critical", "event.critical"),
            Map.entry("world_from", "from_world.name"), Map.entry("world_to", "player.world.name"),
            Map.entry("online_players_from", "from_world.players"), Map.entry("online_players_to", "player.world.players"),
            Map.entry("old_level", "old_level"), Map.entry("new_level", "new_level"),
            Map.entry("old_food_level", "old_food"), Map.entry("new_food_level", "new_food"),
            Map.entry("is_sneaking", "sneaking"), Map.entry("is_running", "sprinting"), Map.entry("empty", "''"),
            Map.entry("respawn_world", "location.world.name"), Map.entry("x", "location.block_x"), Map.entry("y", "location.block_y"),
            Map.entry("z", "location.block_z"), Map.entry("inventory_title", "title"), Map.entry("inventory_title_color_format", "title"),
            Map.entry("inventory_type", "inventory_type"), Map.entry("click_type", "click"), Map.entry("slot", "slot"),
            Map.entry("slot_type", "event.slot_type"), Map.entry("name", "name"), Map.entry("ip", "ip"),
            Map.entry("uuid", "uuid"), Map.entry("reason", "reason"), Map.entry("state", "state"), Map.entry("result", "result"),
            Map.entry("amount", "amount"), Map.entry("caught_type", "target.type"), Map.entry("caught_x", "target.x"),
            Map.entry("caught_y", "target.y"), Map.entry("caught_z", "target.z"), Map.entry("armor_type", "slot"),
            Map.entry("equip_type", "(item.is_air ? 'UNEQUIP' : 'EQUIP')"), Map.entry("enchantment_list", "enchants"),
            Map.entry("rename_text", "rename_text"), Map.entry("repair_type", "repair_type"), Map.entry("npc_id", "npc.id"),
            Map.entry("npc_name", "npc.name"), Map.entry("region", "region.id"), Map.entry("time", "var.cooldown_left"),
            Map.entry("world_is_raining", "player.world.has_storm"), Map.entry("player_is_present", "(player != null)"),
            Map.entry("attack_type", "(projectile != null ? str(projectile.type) : 'PLAYER')"),
            Map.entry("world_time_{player_world}", "player.world.time"));

    /** Variables que cambian de significado según el evento. */
    private static final Map<String, Map<String, String>> BY_TYPE = Map.of(
            "player_click_inventory", Map.of("action_type", "event.action"),
            "item_move", Map.of("action_type", "event.action"),
            "entity_spawn", entityRoot("entity"),
            "item_pickup", entityRoot("entity"));

    /** Variables de ConditionalEvents sin equivalente directo (se avisa una vez por evento). */
    private static final List<String> CE_ONLY = List.of("random_player", "randomword_", "block_at_", "block_data_at_", "is_nearby_",
            "playerblock_", "player_is_outside", "is_number_", "args_substring_", "block_above_", "block_below_",
            "player_attack_cooldown", "random_last", "player_location_", "player_looking_", "format_remove_", "world_time_",
            "original_damage", "movement_type", "select_type", "statistic_name", "previous_value", "new_value", "item_meta",
            "item_itemsadder_id", "block_head_texture", "item_model", "normal_message", "json_message", "message_type");

    private static final String[] OPS = {"!equalsIgnoreCase", "equalsIgnoreCase", "!startsWith", "startsWith", "!endsWith", "endsWith",
            "!contains", "contains", "!matches", "matches", "!equals", "equals", "==", "!=", ">=", "<=", ">", "<"};
    /** %variable%; en text() las llaves llegan escapadas (\{), por eso la barra. */
    private static final Pattern VAR = Pattern.compile("%([A-Za-z0-9_:{}.\\\\-]+)%");
    private static final Pattern EXECUTE = Pattern.compile("^(.*?)\\s+execute\\s+(\\S+?)(\\{(.*)})?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_TO = Pattern.compile("(from|to)_(x|y|z|yaw|pitch|world)");
    private static final Pattern PREFIX = Pattern.compile("^(to_all|to_target|to_world: *[^:]+|to_player: *[^:]+|to_range: *[^:]+|to_condition: *[^:]+):\\s*(.*)$");

    private int review;
    private final List<String> notes = new ArrayList<>();
    private final Set<String> noted = new HashSet<>();
    /** Grupos de to_condition (Config.to_condition_groups del config.yml de CE). */
    private final Map<String, List<String>> conditionGroups = new HashMap<>();
    /** El evento que se está convirtiendo: su nombre (para las notas) y su tipo de CE (variables por evento). */
    private String event = "?";
    private String type = "";
    /** Dentro de una acción con to_…: el jugador del evento es source; el destinatario, player (spec 014, F-01). */
    private boolean inTarget;
    private Map<String, List<String>> rawGroups = Map.of();
    private final Map<String, List<Object>> groupCache = new HashMap<>();
    private final Set<String> converting = new HashSet<>();
    /** Variables de execute grupo{%v%=x} mientras se convierte ese grupo. */
    private final Set<String> locals = new HashSet<>();

    private static Map<String, String> entityRoot(String root) {
        return Map.of("entity", root + ".type", "entity_name", root + ".name", "entity_color_format_name", root + ".name",
                "entity_x", root + ".x", "entity_y", root + ".y", "entity_z", root + ".z", "entity_world", root + ".world.name",
                "entity_uuid", root + ".uuid");
    }

    public Report importFolder(File ceFolder, File outFolder) {
        int events = 0;
        if (!ceFolder.isDirectory()) {
            notes.add("no se encontró la carpeta " + ceFolder.getPath());
            return new Report(0, 0, notes);
        }
        List<File> files = new ArrayList<>();
        File config = new File(ceFolder, "config.yml");
        if (config.exists()) {
            files.add(config);
            ConfigurationSection groups = YamlConfiguration.loadConfiguration(config).getConfigurationSection("Config.to_condition_groups");
            if (groups != null) for (String g : groups.getKeys(false)) conditionGroups.put(g, groups.getStringList(g));
        }
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
        event = name;
        type = ev.getString("type", "").toLowerCase(Locale.ROOT);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("trigger", TYPES.getOrDefault(type, type));
        if (NO_TYPE.containsKey(type)) note(name, "el evento " + type + " no tiene equivalente: " + NO_TYPE.get(type));
        if (type.equals("repetitive_server")) r.put("per_player", false);
        if (ev.contains("repetitive_time")) r.put("every", ev.getInt("repetitive_time") + "t");
        if (ev.contains("enabled")) r.put("enabled", ev.getBoolean("enabled"));
        if (ev.contains("ignore_with_permission")) r.put("bypass_permission", ev.getString("ignore_with_permission"));
        if (ev.getBoolean("ignore_if_cancelled")) r.put("ignore_cancelled", true);
        if (ev.contains("cooldown")) {
            r.put("cooldown", ev.getInt("cooldown") + "s");
            // Spec 014, H4: el permiso de CE sigue sirviendo a los rangos del servidor que migra.
            r.put("cooldown_bypass", "conditionalevents.bypasscooldown." + name);
        }
        if (ev.getBoolean("one_time")) r.put("limit", 1);
        if (ev.isConfigurationSection("custom_event_data")) {
            ConfigurationSection c = ev.getConfigurationSection("custom_event_data");
            r.put("event", c.getString("event"));
            if (c.contains("player_variable")) r.put("player_from", c.getString("player_variable"));
            if (c.contains("variables_to_capture")) note(name, "variables_to_capture: usa event.<propiedad> directamente en las expresiones");
        }
        ConfigurationSection groups = ev.getConfigurationSection("actions");
        rawGroups = new LinkedHashMap<>();
        groupCache.clear();
        if (groups != null) for (String g : groups.getKeys(false)) rawGroups.put(g, groups.getStringList(g));
        // prevent_cooldown_activation / prevent_one_time_activation: esos grupos no cuentan el uso (no_cooldown).
        Set<String> noUsage = new LinkedHashSet<>();
        noUsage.addAll(ev.getStringList("prevent_cooldown_activation"));
        noUsage.addAll(ev.getStringList("prevent_one_time_activation"));
        if (!ev.getStringList("prevent_cooldown_activation").isEmpty() && ev.getBoolean("one_time")
                || !ev.getStringList("prevent_one_time_activation").isEmpty() && ev.contains("cooldown")) {
            note(name, "no_cooldown libera el enfriamiento y el límite a la vez (CE separa prevent_cooldown_activation y prevent_one_time_activation)");
        }
        for (String g : noUsage) {
            List<Object> conv = new ArrayList<>(group(g));
            conv.add(0, "no_cooldown");
            groupCache.put(g, conv);
        }
        List<Object> def = group("default");
        if (rawGroups.containsKey("cooldown")) r.put("on_cooldown", group("cooldown"));
        if (rawGroups.containsKey("one_time")) r.put("on_limit", group("one_time"));

        List<String> lines = ev.getStringList("conditions");
        List<String> plain = new ArrayList<>();
        int i = 0;
        while (i < lines.size() && !EXECUTE.matcher(lines.get(i)).matches()) plain.add(convertCondition(lines.get(i++)));
        if (!plain.isEmpty()) r.put("conditions", plain);
        r.put("actions", i < lines.size() ? nest(lines, i, def) : def);
        return r;
    }

    /** Un grupo de acciones convertido (una vez; execute_action_group puede pedir otro). */
    private List<Object> group(String g) {
        List<Object> cached = groupCache.get(g);
        if (cached != null) return cached;
        List<String> raw = rawGroups.get(g);
        if (raw == null) return List.of();
        if (!converting.add(g)) {
            note(event, "el grupo '" + g + "' se llama a sí mismo con execute_action_group");
            return List.of(review("grupo recursivo " + g));
        }
        List<Object> conv = convertActions(event, raw);
        converting.remove(g);
        groupCache.put(g, conv);
        return conv;
    }

    /** Una línea normal que falla detiene todo; una con execute que se cumple corre su grupo (orden de ConditionalEvents). */
    private List<Object> nest(List<String> lines, int i, List<Object> def) {
        if (i >= lines.size()) return def;
        String line = lines.get(i);
        Matcher m = EXECUTE.matcher(line);
        Map<String, Object> node = new LinkedHashMap<>();
        if (m.matches()) {
            List<Object> group;
            if (!rawGroups.containsKey(m.group(2))) {
                note(event, "el grupo '" + m.group(2) + "' no existe");
                group = List.of(review("grupo inexistente " + m.group(2)));
            } else if (m.group(4) == null) {
                group = group(m.group(2));
            } else {
                // execute grupo{%v%=x;%w%=y}: variables temporales para el grupo (var.v); el grupo se convierte de nuevo con
                // %v% como var.v.
                List<Object> withVars = new ArrayList<>();
                for (String kv : m.group(4).split(";")) {
                    int eq = kv.indexOf('=');
                    if (eq <= 0) continue;
                    String v = kv.substring(0, eq).replace("%", "").trim();
                    withVars.add("set: var." + v + " = " + operand(kv.substring(eq + 1).trim()));
                    locals.add(v.toLowerCase(Locale.ROOT));
                }
                withVars.addAll(convertActions(event, rawGroups.get(m.group(2))));
                locals.clear();
                group = withVars;
            }
            node.put("if", convertCondition(m.group(1)));
            node.put("then", group);
            node.put("else", nest(lines, i + 1, def));
        } else {
            node.put("if", convertCondition(line));
            node.put("then", nest(lines, i + 1, def));
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

    /** Una variable de CE como expresión; las que no conoce quedan como %placeholder% (PlaceholderAPI). */
    private String var(String v) {
        String k = v.toLowerCase(Locale.ROOT);
        // %to:x%: la variable del destinatario de una acción to_… (en OmniEvents, player dentro de la acción).
        if (k.startsWith("to:")) {
            String inner = k.substring(3);
            return inner.equals("player") || inner.equals("player_name") ? "player.name" : "papi('%" + v.substring(3) + "%', player)";
        }
        if (locals.contains(k)) return "var." + k;
        String expr = known(k, v);
        if (expr == null) {
            for (String p : CE_ONLY) {
                if (k.startsWith(p) && noted.add(event + "|" + p)) {
                    note(event, "la variable %" + v + "% de ConditionalEvents no tiene equivalente directo: revisa la condición o la acción");
                    break;
                }
            }
            // Un placeholder de PlaceholderAPI: dentro de to_… se resuelve para el jugador del evento, como en CE.
            return inTarget ? "papi('%" + v + "%', source)" : "%" + v + "%";
        }
        return inTarget ? expr.replaceAll("(?<![\\w.])player(?=\\.|\\b)", "source") : expr;
    }

    private String known(String k, String v) {
        Map<String, String> byType = BY_TYPE.get(type);
        if (byType != null && byType.containsKey(k)) return byType.get(k);
        if (VARS.containsKey(k)) return VARS.get(k);
        Matcher arg = Pattern.compile("arg_(\\d+)").matcher(k);
        if (arg.matches()) return "args[" + (Integer.parseInt(arg.group(1)) - 1) + "]";
        if (k.startsWith("target:")) return "papi('%" + v.substring(7) + "%', target)";
        Matcher ft = FROM_TO.matcher(k);
        if (ft.matches()) return ft.group(1) + "." + (ft.group(2).equals("world") ? "world.name" : ft.group(2));
        if (k.startsWith("playerarmor_name_")) return "player." + k.substring(17) + ".name";
        if (k.startsWith("playerarmor_")) return "player." + k.substring(12) + ".type";
        if (k.startsWith("offhand:")) return item(k.substring(8), "offhand");
        if (k.startsWith("cursor:")) return item(k.substring(7), "cursor");
        return item(k, "item");
    }

    /** Variables de ítem de CE sobre una raíz (item, offhand, cursor). */
    private static String item(String k, String root) {
        Matcher line = Pattern.compile("item_(?:color_format_)?lore_line_(\\d+)").matcher(k);
        if (line.matches()) return root + ".lore[" + (Integer.parseInt(line.group(1)) - 1) + "]";
        return switch (k) {
            case "item" -> root + ".type";
            case "item_name", "item_color_format_name" -> root + ".name";
            case "item_amount" -> root + ".amount";
            case "item_lore", "item_color_format_lore" -> root + ".lore";
            case "item_durability" -> root + ".durability";
            case "item_custom_model_data" -> root + ".model";
            default -> null;
        };
    }

    /** Texto con %variables% → texto con {expresiones} (las desconocidas quedan para PlaceholderAPI). */
    String text(String s) {
        Matcher m = VAR.matcher(s.replace("{", "\\{").replace("}", "\\}"));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = var(m.group(1).replace("\\{", "{").replace("\\}", "}"));
            m.appendReplacement(sb, Matcher.quoteReplacement(expr.startsWith("%") ? expr : "{" + expr + "}"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    List<Object> convertActions(String event, List<String> lines) {
        List<Object> out = new ArrayList<>();
        for (String line : lines) {
            Object a = convertAction(event, line);
            if (a instanceof List<?> many) out.addAll(many);
            else if (a != null) out.add(a);
        }
        return out;
    }

    private Object convertAction(String event, String line) {
        String t = line.trim();
        Map<String, Object> target = null;
        Matcher pre = PREFIX.matcher(t);
        if (pre.matches()) {
            target = target(event, pre.group(1));
            if (target == null) return review(line);
            t = pre.group(2);
        }
        inTarget = target != null;
        try {
            Object res = action(event, t);
            if (res == null) return review(line);
            if (target == null) return res;
            // El destino va como modificador to (y where) del nodo.
            Map<String, Object> node = new LinkedHashMap<>();
            if (res instanceof String s) {
                int c = s.indexOf(':');
                String val = c < 0 ? null : s.substring(c + 1).trim();
                if (val != null && val.length() > 1 && val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                node.put(c < 0 ? s : s.substring(0, c).trim(), val);
            } else if (res instanceof Map<?, ?> m) {
                m.forEach((k, v) -> node.put(String.valueOf(k), v));
            } else {
                return review(line);
            }
            node.putAll(target);
            return node;
        } finally {
            inTarget = false;
        }
    }

    /** to_all, to_target, to_world, to_player, to_range y to_condition → to (y where). */
    private Map<String, Object> target(String event, String prefix) {
        int c = prefix.indexOf(':');
        String kind = c < 0 ? prefix : prefix.substring(0, c).trim();
        String param = c < 0 ? "" : prefix.substring(c + 1).trim();
        Map<String, Object> t = new LinkedHashMap<>();
        switch (kind) {
            case "to_all" -> t.put("to", "all");
            case "to_target" -> t.put("to", "target");
            case "to_world" -> t.put("to", "world:" + text(param));
            case "to_player" -> t.put("to", "player:" + text(param));
            case "to_range" -> {
                // radio;incluir_al_jugador[;incluir_entidades]
                String[] p = param.split(";");
                boolean entities = p.length > 2 && p[2].trim().equalsIgnoreCase("true");
                t.put("to", (entities ? "nearby:" : "radius:") + p[0].trim());
                if (p.length > 1 && p[1].trim().equalsIgnoreCase("false")) t.put("where", "player != source");
            }
            case "to_condition" -> {
                List<String> conds = conditionGroups.get(param);
                if (conds == null) {
                    note(event, "to_condition: el grupo '" + param + "' no está en Config.to_condition_groups");
                    return null;
                }
                // Las condiciones del grupo se evalúan para cada destinatario (player); %player% es el destinatario.
                List<String> parts = new ArrayList<>();
                for (String cond : conds) parts.add("(" + convertCondition(cond) + ")");
                t.put("to", "all");
                t.put("where", parts.isEmpty() ? "true" : String.join(" && ", parts));
            }
            default -> {
                return null;
            }
        }
        return t;
    }

    /** Una acción sin el destino; null si no tiene traducción. */
    private Object action(String event, String t) {
        int c = t.indexOf(':');
        String type = (c < 0 ? t : t.substring(0, c)).trim().toLowerCase(Locale.ROOT);
        String v = c < 0 ? "" : t.substring(c + 1).trim();
        String[] p = v.split(";");
        return switch (type) {
            case "message", "mini_message" -> "message: " + text(v);
            case "centered_message" -> "centered_message: " + text(v);
            case "json_message" -> json(event, v);
            case "console_message" -> "console_message: " + text(v);
            case "console_command" -> "console: " + text(v);
            case "player_command" -> "command: " + text(v);
            case "player_send_chat" -> "chat: " + text(v);
            case "kick", "send_to_server", "teleport", "gamemode", "damage", "heal", "freeze", "set_damage", "set_death_message" -> type + ": " + text(v);
            case "actionbar" -> "actionbar: " + text(p[0]);
            case "title" -> p.length >= 5 ? "title: " + text(p[3]) + ";" + text(p[4]) + ";" + p[0] + ";" + p[1] + ";" + p[2] : "title: " + text(v);
            case "playsound", "playsound_resource_pack" -> {
                if (p.length > 3) note(event, "playsound con ubicación: OmniEvents lo reproduce donde está el jugador");
                String sound = text(p[0].trim());
                if (type.equals("playsound_resource_pack") && !sound.contains(":") && !sound.contains(".")) sound = "minecraft:" + sound;
                yield "sound: " + sound + (p.length > 2 ? ";" + p[1] + ";" + p[2] : "");
            }
            case "stopsound" -> "stop_sound: " + v;
            case "cancel_event" -> "cancel: " + v;
            case "give_potion_effect" -> "effect: " + p[0] + ";" + (p.length > 1 ? seconds(p[1]) : "10") + ";" + (p.length > 2 ? p[2] : "1")
                    + (p.length > 3 ? ";" + p[3] : "");
            case "remove_potion_effect" -> "remove_effect: " + v;
            case "set_on_fire" -> "fire: " + v;
            case "set_food_level" -> "set_food: " + v;
            case "close_inventory", "clear_inventory" -> type;
            case "wait" -> "delay: " + v + "s";
            case "wait_ticks" -> "delay: " + v + "t";
            case "keep_items" -> {
                if (!v.equalsIgnoreCase("all") && !v.isEmpty()) note(event, "keep_items: " + v + " — keep_inventory conserva ítems y nivel a la vez");
                yield "keep_inventory";
            }
            case "cancel_drop" -> "cancel_drops: " + v;
            case "set_event_xp" -> "set_event: exp_to_drop = " + v;
            case "hide_join_message" -> "set_join_message: none";
            case "hide_leave_message" -> "set_quit_message: none";
            case "prevent_join" -> "deny_join: " + text(v);
            case "lightning_strike" -> {
                // CE: mundo;x;y;z (igual que OmniEvents)
                yield "lightning: " + text(v);
            }
            case "remove_item" -> {
                if (p.length > 2) {
                    note(event, "remove_item con name/lorecontains/datavalue: OmniEvents quita por material (los filtros quedan para otra spec)");
                    yield null;
                }
                yield "remove_item: " + p[0] + ";" + (p.length > 1 ? p[1] : "1");
            }
            case "throw_directional" -> "launch: " + v;
            case "throw_coordinate" -> "velocity: " + v;
            case "firework" -> firework(event, v);
            case "execute_action_group" -> randomGroups(event, v);
            case "call_event" -> {
                StringBuilder sb = new StringBuilder("call: " + p[0]);
                for (int i = 1; i < p.length; i++) {
                    if (p[i].trim().equalsIgnoreCase("already_stored")) {
                        note(event, "call_event con already_stored: las var.<nombre> de la regla no pasan solas; agrégalas como argumentos");
                        continue;
                    }
                    int eq = p[i].indexOf('=');
                    if (eq > 0) sb.append("; ").append(p[i].substring(0, eq).replace("%", "").trim()).append("=").append(quote(p[i].substring(eq + 1)));
                }
                yield sb.toString();
            }
            case "particle" -> particle(event, v);
            case "summon" -> summon(event, v);
            case "give_item", "drop_item" -> item(event, type, v);
            case "set_block" -> setBlock(event, v);
            default -> {
                switch (type) {
                    case "player_command_as_op" -> note(event, "player_command_as_op no existe en OmniEvents por seguridad (ver plan 001 STRIDE)");
                    case "discordsrv_embed" -> note(event, "discordsrv_embed: OmniEvents aún no se integra con DiscordSRV");
                    case "remove_item_slot", "set_item", "tab_complete", "stopsound_resource_pack", "vector" ->
                            note(event, type + ": aún no tiene equivalente en OmniEvents");
                    default -> note(event, "acción desconocida '" + type + "'");
                }
                yield null;
            }
        };
    }

    /** json_message: el JSON de texto se convierte a MiniMessage (clic y hover incluidos). */
    private Object json(String event, String v) {
        try {
            Component c = GsonComponentSerializer.gson().deserialize(v);
            return "message: " + text(MiniMessage.miniMessage().serialize(c));
        } catch (RuntimeException e) {
            note(event, "json_message con JSON inválido: " + e.getMessage());
            return null;
        }
    }

    /** firework: la misma forma (clave:valor con espacios); shot_direction no se soporta. */
    private Object firework(String event, String v) {
        StringBuilder sb = new StringBuilder();
        for (String tok : v.trim().split("\\s+")) {
            if (tok.toLowerCase(Locale.ROOT).startsWith("shot_direction:")) {
                note(event, "firework: shot_direction no se soporta (el cohete sube recto)");
                continue;
            }
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(tok);
        }
        return "firework: " + text(sb.toString());
    }

    /** execute_action_group: g1:70;g2:30 → random con weight y do. */
    private Object randomGroups(String event, String v) {
        List<Object> branches = new ArrayList<>();
        for (String part : v.split(";")) {
            String[] gw = part.trim().split(":");
            if (gw[0].isEmpty()) continue;
            if (!rawGroups.containsKey(gw[0])) {
                note(event, "execute_action_group: el grupo '" + gw[0] + "' no existe");
                return null;
            }
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("weight", gw.length > 1 ? num(gw[1]) : 1);
            b.put("do", group(gw[0]));
            branches.add(b);
        }
        if (branches.isEmpty()) return null;
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("random", branches);
        return node;
    }

    private static Object num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return s.trim();
        }
    }

    /** particle: effect:X amount:N offset:x;y;z speed:s (CE, separado por espacios). */
    private Object particle(String event, String v) {
        Map<String, String> kv = spaced(v);
        if (!kv.containsKey("effect")) return null;
        String[] eff = kv.get("effect").split(";");
        String name = eff[0].equalsIgnoreCase("REDSTONE") ? "DUST" : eff[0];
        String[] off = kv.getOrDefault("offset", "").split(";");
        boolean hasOff = off.length == 3;
        StringBuilder sb = new StringBuilder("particle: " + name + ";" + kv.getOrDefault("amount", "10"));
        if (hasOff || kv.containsKey("speed") || eff.length == 4) {
            sb.append(';').append(hasOff ? off[0] + ";" + off[1] + ";" + off[2] : "0.3;0.3;0.3");
            sb.append(';').append(kv.getOrDefault("speed", "0"));
            if (eff.length == 4) sb.append(';').append(eff[1]).append(';').append(eff[2]).append(';').append(eff[3]);
        }
        for (String k : List.of("location", "force", "for_player")) {
            if (kv.containsKey(k)) note(event, "particle: " + k + " aún no se soporta (las partículas salen donde está el jugador)");
        }
        return text(sb.toString());
    }

    /** summon: location:x,y,z,mundo;entity:TIPO (las demás claves de CE quedan en la nota). */
    private Object summon(String event, String v) {
        Map<String, String> kv = keyValues(v);
        if (!kv.containsKey("entity")) return null;
        for (String k : kv.keySet()) {
            if (!k.equals("entity") && !k.equals("location")) note(event, "summon: " + k + " aún no se soporta");
        }
        String loc = kv.get("location");
        return "summon: " + kv.get("entity") + (loc == null ? "" : ";" + location(loc));
    }

    /** x,y,z,mundo (CE) → mundo;x;y;z (OmniEvents). */
    private String location(String ce) {
        String[] p = ce.split(",");
        if (p.length != 4) return text(ce.replace(",", ";"));
        return text(p[3].trim() + ";" + p[0].trim() + ";" + p[1].trim() + ";" + p[2].trim());
    }

    /** give_item / drop_item: id:…;amount:…;name:…;lore:a|b;enchants:X-1|Y-2;custom_model_data:N;saved_item:n → forma de mapa. */
    private Object item(String event, String type, String v) {
        Map<String, String> kv = keyValues(v);
        Map<String, Object> item = new LinkedHashMap<>();
        if (kv.containsKey("saved_item")) {
            item.put("saved", kv.get("saved_item"));
        } else if (kv.containsKey("id")) {
            item.put("material", kv.get("id"));
        } else {
            return null;
        }
        if (kv.containsKey("amount")) item.put("amount", text(kv.get("amount")));
        if (kv.containsKey("name")) item.put("name", text(kv.get("name")));
        if (kv.containsKey("lore")) {
            List<String> lore = new ArrayList<>();
            for (String l : kv.get("lore").split("\\|")) lore.add(text(l));
            item.put("lore", lore);
        }
        if (kv.containsKey("enchants")) {
            List<String> ench = new ArrayList<>();
            for (String e : kv.get("enchants").split("\\|")) ench.add(e.trim().replace('-', ':'));
            item.put("enchants", ench);
        }
        if (kv.containsKey("custom_model_data")) item.put("model", text(kv.get("custom_model_data")));
        Set<String> known = Set.of("id", "saved_item", "amount", "name", "lore", "enchants", "custom_model_data", "location");
        for (String k : kv.keySet()) if (!known.contains(k)) note(event, type + ": " + k + " aún no se soporta (el ítem se da sin esa propiedad)");
        Map<String, Object> node = new LinkedHashMap<>();
        node.put(type, item);
        if (type.equals("drop_item") && kv.containsKey("location")) node.put("location", location(kv.get("location")));
        return node;
    }

    /** set_block: location:x,y,z,mundo;id:MATERIAL[;block_data:…]. */
    private Object setBlock(String event, String v) {
        Map<String, String> kv = keyValues(v);
        if (!kv.containsKey("id") || !kv.containsKey("location")) return null;
        for (String k : kv.keySet()) {
            if (!k.equals("id") && !k.equals("location")) note(event, "set_block: " + k + " aún no se soporta");
        }
        return "set_block: " + location(kv.get("location")) + ";" + text(kv.get("id"));
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

    /** clave:valor;clave:valor (give_item, summon, set_block). */
    private static Map<String, String> keyValues(String v) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String part : v.split(";")) {
            int c = part.indexOf(':');
            if (c > 0) m.put(part.substring(0, c).trim().toLowerCase(Locale.ROOT), part.substring(c + 1).trim());
        }
        return m;
    }

    /** clave:valor clave:valor (particle, firework). */
    private static Map<String, String> spaced(String v) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String part : v.trim().split("\\s+")) {
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
