package dev.leo.omnievents.rule;

import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.util.Suggest;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Herencia (extends) y parámetros ${nombre} de las reglas, resueltos al cargar (spec 011, H2). */
final class Templates {
    /** Una regla o plantilla tal como está en su archivo, antes de compilar. */
    record Entry(String name, String file, Pack pack, Map<String, Object> map, ConfigurationSection section) {
        boolean template() {
            return Boolean.TRUE.equals(map.get("template"));
        }
    }

    static final int MAX_DEPTH = 8;
    /** Claves propias de cada nivel: no se heredan (H2 CA2). */
    private static final Set<String> OWN = Set.of("template", "extends", "with", "params", "tests");
    private static final Pattern WHOLE = Pattern.compile("^\\$\\{([A-Za-z0-9_\\-]+)}$");
    private static final Pattern PARAM = Pattern.compile("(\\\\?)\\$\\{([A-Za-z0-9_\\-]*)}");

    private Templates() {
    }

    /**
     * El mapa final de una regla: su cadena de plantillas mezclada y los parámetros reemplazados. null si hay errores
     * (quedan en problems). Sin extends, paquete ni params devuelve el mapa tal cual (una regla de antes no cambia).
     */
    static Map<String, Object> expand(Entry e, Function<String, Entry> byName, Set<String> templateNames, Problems problems) {
        List<Entry> chain = new ArrayList<>();
        chain.add(e);
        Set<String> seen = new HashSet<>();
        seen.add(e.name().toLowerCase(Locale.ROOT));
        Entry cur = e;
        while (cur.map().get("extends") != null) {
            String ref = Values.str(cur.map().get("extends")).trim();
            String path = cur.name() + ".extends";
            Entry t = find(ref, cur.pack(), byName);
            if (t == null || !t.template()) {
                problems.file(cur.file()).error(path, t == null
                        ? "no existe la plantilla '" + ref + "'" + Suggest.hint(ref, templateNames)
                        : "'" + t.name() + "' no es una plantilla: márcala con template: true");
                return null;
            }
            if (!seen.add(t.name().toLowerCase(Locale.ROOT))) {
                problems.file(cur.file()).error(path, "herencia en ciclo: " + names(chain) + " → " + t.name());
                return null;
            }
            if (chain.size() > MAX_DEPTH) {
                problems.file(cur.file()).error(path, "más de " + MAX_DEPTH + " niveles de herencia (" + names(chain) + "…)");
                return null;
            }
            chain.add(t);
            cur = t;
        }
        problems.file(e.file());
        if (e.map().containsKey("with") && !e.map().containsKey("extends")) problems.warn(e.name() + ".with", "with solo se usa con extends (se ignora)");
        boolean params = e.pack() != null || chain.size() > 1 || e.map().containsKey("params");
        if (!params) return e.map();

        Map<String, Object> merged = new LinkedHashMap<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (Map.Entry<String, Object> kv : chain.get(i).map().entrySet()) {
                if (i > 0 && OWN.contains(kv.getKey())) continue;
                merged.put(kv.getKey(), kv.getValue());
            }
        }
        // Fuentes en orden: with de la regla, params de la regla y de cada plantilla, params de cada paquete.
        List<Map<String, Object>> rest = new ArrayList<>();
        for (Entry c : chain) rest.add(map(c.map().get("params"), c.name() + ".params", problems));
        Set<Pack> packs = new LinkedHashSet<>();
        for (Entry c : chain) if (c.pack() != null) packs.add(c.pack());
        for (Pack p : packs) rest.add(p.params);
        int mark = problems.size();
        Map<String, Object> with = new LinkedHashMap<>();
        for (Map.Entry<String, Object> kv : map(e.map().get("with"), e.name() + ".with", problems).entrySet()) {
            with.put(kv.getKey(), substitute(kv.getValue(), e.name() + ".with." + kv.getKey(), rest, problems));
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        sources.add(with);
        sources.addAll(rest);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> kv : merged.entrySet()) {
            if (kv.getKey().equals("with") || kv.getKey().equals("params")) out.put(kv.getKey(), kv.getValue());
            else out.put(kv.getKey(), substitute(kv.getValue(), e.name() + "." + kv.getKey(), sources, problems));
        }
        return problems.hasErrorsSince(mark) ? null : out;
    }

    private static Entry find(String ref, Pack from, Function<String, Entry> byName) {
        if (from != null && ref.indexOf(':') < 0) {
            Entry t = byName.apply(from.name + ":" + ref);
            if (t != null) return t;
        }
        return byName.apply(ref);
    }

    private static String names(List<Entry> chain) {
        List<String> n = new ArrayList<>();
        for (Entry c : chain) n.add(c.name());
        return String.join(" → ", n);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object raw, String path, Problems problems) {
        if (raw == null) return Map.of();
        if (raw instanceof ConfigurationSection cs) return plain(cs);
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        problems.error(path, "va un mapa nombre: valor (p. ej. { cantidad: 5 })");
        return Map.of();
    }

    /** Una sección de Bukkit como mapas y listas simples. */
    static Map<String, Object> plain(ConfigurationSection s) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) {
            Object v = s.get(k);
            out.put(k, v instanceof ConfigurationSection cs ? plain(cs) : v);
        }
        return out;
    }

    /** Reemplaza ${n} en los textos de un valor (también dentro de listas y mapas); los valores no se re-reemplazan. */
    static Object substitute(Object v, String path, List<Map<String, Object>> sources, Problems problems) {
        if (v instanceof ConfigurationSection cs) v = plain(cs);
        if (v instanceof String s) return text(s, path, sources, problems);
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) out.add(substitute(l.get(i), path + "[" + i + "]", sources, problems));
            return out;
        }
        if (v instanceof Map<?, ?> m) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> kv : m.entrySet()) out.put(kv.getKey(), substitute(kv.getValue(), path + "." + kv.getKey(), sources, problems));
            return out;
        }
        return v;
    }

    private static Object text(String s, String path, List<Map<String, Object>> sources, Problems problems) {
        if (!s.contains("${")) return s;
        Matcher whole = WHOLE.matcher(s);
        if (whole.matches()) {
            Object val = lookup(whole.group(1), path, sources, problems);
            return val == null ? s : val;
        }
        Matcher m = PARAM.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rep;
            if (!m.group(1).isEmpty()) {
                rep = "${" + m.group(2) + "}";
            } else {
                Object val = lookup(m.group(2), path, sources, problems);
                rep = val == null ? m.group() : Values.str(val);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Object lookup(String name, String path, List<Map<String, Object>> sources, Problems problems) {
        Set<String> known = new LinkedHashSet<>();
        for (Map<String, Object> src : sources) {
            if (src.containsKey(name)) {
                Object v = src.get(name);
                return v instanceof ConfigurationSection cs ? plain(cs) : v == null ? "" : v;
            }
            known.addAll(src.keySet());
        }
        problems.error(path, (name.isEmpty() ? "${} sin nombre" : "parámetro desconocido ${" + name + "}" + Suggest.hint(name, known))
                + (known.isEmpty() ? " (decláralo en params del paquete o de la plantilla, o en with)" : " (hay: " + String.join(", ", known) + ")"));
        return null;
    }
}
