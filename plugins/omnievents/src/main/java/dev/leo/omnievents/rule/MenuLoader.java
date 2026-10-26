package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionParser;
import dev.leo.omnievents.action.ItemSpec;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.menu.Menu;
import dev.leo.omnievents.text.Template;
import dev.leo.omnievents.trigger.TriggerType;
import dev.leo.omnievents.util.Suggest;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compila una entrada menu: (spec 013, plan §1.1). La apertura, el cierre y cada clic son reglas que compila
 * {@link RuleLoader#parse}: condiciones, deny, cost, enfriamientos y tests sin código nuevo. Lo propio del menú
 * (título, filas, casillas, ítems, view, bedrock) se valida aquí, con la ruta del YAML.
 */
final class MenuLoader {
    /** Claves que puede tener la entrada además de menu (las de herencia las consume Templates). */
    private static final Set<String> ENTRY_KEYS = Set.of("menu", "description", "enabled", "template", "extends", "with", "params");
    /** Claves del menú que son de su regla de apertura. */
    private static final List<String> OPEN_KEYS = List.of("permission", "conditions", "else", "cost", "on_cost_fail", "cooldown", "on_cooldown", "on_open", "tests");
    /** Claves de ítem de give_item que también se aceptan sueltas en el ítem del menú (como en DeluxeMenus). */
    private static final Set<String> INLINE_ITEM_KEYS = Set.of("material", "id", "amount", "name", "lore", "model", "enchants", "glow", "unbreakable", "hide_flags", "saved",
            "flags", "durability", "item_model", "skull_owner", "skull_texture");

    private final RuleLoader loader;

    MenuLoader(RuleLoader loader) {
        this.loader = loader;
    }

    Menu parse(String name, String file, Pack pack, Map<String, Object> entry, Problems problems) {
        int mark = problems.size();
        for (String k : entry.keySet()) {
            if (ENTRY_KEYS.contains(k)) continue;
            if (k.equals("trigger") || k.equals("type")) problems.error(name + "." + k, "un menú no lleva trigger: se abre con la acción open_menu: " + name);
            else problems.error(name + "." + k, "en una entrada con menu: va todo dentro de menu (" + k + " va en menu." + k + "?)");
        }
        if (!(entry.get("menu") instanceof Map<?, ?> raw)) {
            problems.error(name + ".menu", "menu es un mapa con title, rows e items");
            return null;
        }
        Map<String, Object> m = strings(raw);
        String base = name + ".menu";
        for (String k : m.keySet()) {
            if (!RuleOptions.MENU_KEYS.containsKey(k)) problems.error(base + "." + k, "clave desconocida del menú" + Suggest.hint(k, RuleOptions.MENU_KEYS.keySet()));
        }

        int rows = 3;
        if (m.get("rows") != null) {
            Double d = Values.toNumberOrNull(m.get("rows"));
            if (d == null || d != Math.floor(d) || d < 1 || d > 6) problems.error(base + ".rows", "rows va de 1 a 6 (filas de 9 casillas)");
            else rows = d.intValue();
        }
        TriggerType openTrigger = loader.triggers().get("menu_open");
        Expression.startCollecting();
        Template title;
        ItemSpec fill = null;
        Template content = null;
        boolean bedrock = true;
        try {
            title = Template.compile(m.get("title") == null ? "" : Values.str(m.get("title")));
            if (m.get("fill") != null) {
                String mat = Values.str(m.get("fill")).trim();
                if (Material.matchMaterial(mat) == null) problems.error(base + ".fill", "material desconocido '" + mat + "'" + Suggest.hint(mat, materials()));
                else fill = ItemSpec.parse(Map.of("material", mat, "name", " "), base + ".fill", loader.actions().plugin());
            }
            if (m.get("bedrock") != null) {
                if (!(m.get("bedrock") instanceof Map<?, ?> b)) {
                    problems.error(base + ".bedrock", "bedrock es un mapa con content y enabled");
                } else {
                    Map<String, Object> bm = strings(b);
                    for (String k : bm.keySet()) {
                        if (!RuleOptions.MENU_BEDROCK_KEYS.containsKey(k)) problems.error(base + ".bedrock." + k, "clave desconocida" + Suggest.hint(k, RuleOptions.MENU_BEDROCK_KEYS.keySet()));
                    }
                    if (bm.get("content") != null) content = Template.compile(Values.str(bm.get("content")));
                    if (bm.get("enabled") != null) {
                        if (bm.get("enabled") instanceof Boolean on) bedrock = on;
                        else problems.error(base + ".bedrock.enabled", "va true o false");
                    }
                }
            }
        } catch (ExprException ex) {
            problems.error(base + ".title", ex.getMessage());
            title = Template.compile("");
        } finally {
            loader.validateRoots(base, openTrigger, new ActionParser(loader.actions(), problems), Expression.stopCollecting(), problems);
        }

        // La apertura y el cierre: reglas con las claves de regla del menú.
        Map<String, Object> openMap = new LinkedHashMap<>();
        openMap.put("trigger", "menu_open");
        for (String k : OPEN_KEYS) if (m.containsKey(k)) openMap.put(k, m.get(k));
        Map<String, Object> closeMap = new LinkedHashMap<>();
        closeMap.put("trigger", "menu_close");
        if (m.containsKey("on_close")) closeMap.put("on_close", m.get("on_close"));
        org.bukkit.configuration.ConfigurationSection openSection = RuleLoader.section(openMap), closeSection = RuleLoader.section(closeMap);
        Rule open, close;
        loader.compilingMenu = true;
        try {
            open = loader.parse(base, file, openSection, problems, pack);
            close = loader.parse(base + ".on_close", file, closeSection, problems, pack);
        } finally {
            loader.compilingMenu = false;
        }

        List<Menu.Item> items = new ArrayList<>();
        Object rawItems = m.get("items");
        if (rawItems == null) {
            problems.warn(base, "el menú no tiene items");
        } else if (!(rawItems instanceof Map<?, ?> im)) {
            problems.error(base + ".items", "items es un mapa nombre → ítem (slot, item, click…)");
        } else {
            Map<Integer, String> taken = new HashMap<>();
            for (Map.Entry<?, ?> e : im.entrySet()) {
                String key = String.valueOf(e.getKey());
                Menu.Item it = item(key, e.getValue(), base + ".items." + key, rows, file, pack, problems);
                if (it == null) continue;
                for (int s : it.slots()) {
                    String first = taken.get(s);
                    if (first == null) {
                        if (it.view() == null) taken.put(s, key);
                    } else {
                        problems.warn(base + ".items." + key, "la casilla " + s + " ya la ocupa " + first + " sin view: este ítem nunca se ve ahí (pon view en " + first + " o cambia la casilla)");
                    }
                }
                items.add(it);
            }
        }
        if (problems.hasErrorsSince(mark) || open == null || close == null) return null;
        return new Menu(name, file, pack == null ? null : pack.name, title, rows, fill, open, close, bedrock, content, items);
    }

    private Menu.Item item(String key, Object raw, String path, int rows, String file, Pack pack, Problems problems) {
        if (!(raw instanceof Map<?, ?> r)) {
            problems.error(path, "cada ítem es un mapa con slot, item y sus clics");
            return null;
        }
        int mark = problems.size();
        Map<String, Object> m = strings(r);
        boolean inline = !m.containsKey("item") && (m.containsKey("material") || m.containsKey("id") || m.containsKey("saved"));
        for (String k : m.keySet()) {
            if (RuleOptions.MENU_ITEM_KEYS.containsKey(k) || inline && INLINE_ITEM_KEYS.contains(k)) continue;
            Set<String> known = new LinkedHashSet<>(RuleOptions.MENU_ITEM_KEYS.keySet());
            known.addAll(INLINE_ITEM_KEYS);
            problems.error(path + "." + k, "clave desconocida del ítem" + Suggest.hint(k, known));
        }
        List<Integer> slots = slots(m.containsKey("slot") ? m.get("slot") : m.get("slots"), path + (m.containsKey("slot") ? ".slot" : ".slots"), rows, problems);

        Object itemRaw = inline ? itemMap(m) : m.get("item");
        ItemSpec spec = null;
        Expression view = null;
        Template button = null;
        String image = null;
        Expression.startCollecting();
        try {
            if (itemRaw == null) {
                problems.error(path, "falta item (p. ej. item: \"DIAMOND;1;<aqua>Diamante\")");
            } else {
                String ip = path + (inline ? "" : ".item");
                try {
                    spec = ItemSpec.parse(itemRaw, ip, loader.actions().plugin());
                    Template mat = spec.material();
                    if (mat != null && mat.isConstant() && Material.matchMaterial(mat.source().trim()) == null) {
                        problems.error(ip, "material desconocido '" + mat.source().trim() + "'" + Suggest.hint(mat.source().trim(), materials()));
                    }
                } catch (IllegalArgumentException | ExprException ex) {
                    problems.error(ip, ex.getMessage());
                }
            }
            if (m.get("view") != null) {
                try {
                    view = Expression.compile(Values.str(m.get("view")));
                } catch (ExprException ex) {
                    problems.error(path + ".view", ex.getMessage());
                }
            }
            if (m.get("bedrock") != null) {
                if (!(m.get("bedrock") instanceof Map<?, ?> b)) {
                    problems.error(path + ".bedrock", "bedrock es un mapa con button e image");
                } else {
                    Map<String, Object> bm = strings(b);
                    for (String k : bm.keySet()) {
                        if (!RuleOptions.ITEM_BEDROCK_KEYS.containsKey(k)) problems.error(path + ".bedrock." + k, "clave desconocida" + Suggest.hint(k, RuleOptions.ITEM_BEDROCK_KEYS.keySet()));
                    }
                    if (bm.get("button") != null) button = Template.compile(Values.str(bm.get("button")));
                    if (bm.get("image") != null) image = Values.str(bm.get("image")).trim();
                }
            }
        } catch (ExprException ex) {
            problems.error(path, ex.getMessage());
        } finally {
            loader.validateRoots(path, loader.triggers().get("menu_open"), new ActionParser(loader.actions(), problems), Expression.stopCollecting(), problems);
        }

        Map<String, Rule> clicks = new LinkedHashMap<>();
        Set<String> closing = new LinkedHashSet<>();
        for (String ck : Menu.CLICK_KEYS) {
            if (!m.containsKey(ck)) continue;
            String cp = path + "." + ck;
            if (!(m.get(ck) instanceof Map<?, ?> cr)) {
                problems.error(cp, ck + " es un cuerpo de regla: un mapa con actions (y conditions, cost, cooldown, tests…)");
                continue;
            }
            Map<String, Object> cm = strings(cr);
            if (cm.containsKey("trigger") || cm.containsKey("type")) {
                problems.error(cp + ".trigger", "un clic no lleva trigger: lo pone el menú (menu_click)");
                continue;
            }
            Object close = cm.remove("close");
            if (close != null) {
                if (close instanceof Boolean b) {
                    if (b) closing.add(ck);
                } else {
                    problems.error(cp + ".close", "va true o false");
                }
            }
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("trigger", "menu_click");
            sm.putAll(cm);
            org.bukkit.configuration.ConfigurationSection s = RuleLoader.section(sm);
            loader.compilingMenu = true;
            try {
                Rule rule = loader.parse(cp, file, s, problems, pack);
                if (rule != null) clicks.put(ck, rule);
            } finally {
                loader.compilingMenu = false;
            }
        }
        if (problems.hasErrorsSince(mark) || spec == null || slots == null) return null;
        return new Menu.Item(key, slots, spec, view, clicks, closing, button, image);
    }

    /** El ítem escrito suelto (material, name, lore… en el ítem del menú) como el mapa de give_item. */
    private static Map<String, Object> itemMap(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : INLINE_ITEM_KEYS) if (m.containsKey(k)) out.put(k, m.get(k));
        return out;
    }

    /** slot: un número, una lista de números o rangos ("0-8"), todo dentro del menú. */
    static List<Integer> slots(Object raw, String path, int rows, Problems problems) {
        if (raw == null) {
            problems.error(path, "falta slot (la casilla, desde 0; p. ej. slot: 13 o slot: \"0-8\")");
            return null;
        }
        List<Object> parts = raw instanceof List<?> l ? new ArrayList<>(l) : List.of(raw);
        Set<Integer> out = new LinkedHashSet<>();
        int max = rows * 9 - 1;
        for (Object p : parts) {
            String s = Values.str(p).trim();
            java.util.regex.Matcher range = java.util.regex.Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$").matcher(s);
            int a, b;
            if (range.matches()) {
                a = Integer.parseInt(range.group(1));
                b = Integer.parseInt(range.group(2));
            } else if (s.matches("^\\d+(\\.0)?$")) {
                a = b = (int) Double.parseDouble(s);
            } else {
                problems.error(path, "casilla inválida '" + s + "' (un número desde 0, una lista o un rango \"0-8\")");
                return null;
            }
            if (a > b || b > max) {
                problems.error(path, "la casilla " + s + " está fuera del menú (" + (max + 1) / 9 + " filas: de 0 a " + max + ")");
                return null;
            }
            for (int i = a; i <= b; i++) out.add(i);
        }
        return List.copyOf(out);
    }

    private static Map<String, Object> strings(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(String.valueOf(k), v instanceof org.bukkit.configuration.ConfigurationSection cs ? Templates.plain(cs) : v));
        return out;
    }

    private static List<String> materials() {
        List<String> out = new ArrayList<>();
        for (Material mt : Material.values()) if (!mt.isLegacy()) out.add(mt.name());
        return out;
    }
}
