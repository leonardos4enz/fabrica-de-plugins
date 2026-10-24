package dev.leo.omnievents.action;

import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lo que una fábrica de acciones recibe al cargar: el valor, las claves extra del mapa y el analizador. */
public final class Args {
    private final String type;
    private final Object value;
    private final Map<String, Object> extra;
    private final String path;
    private final ActionParser parser;
    private final Set<String> used = new HashSet<>();

    Args(String type, Object value, Map<String, Object> extra, String path, ActionParser parser) {
        this.type = type;
        this.value = value;
        this.extra = extra;
        this.path = path;
        this.parser = parser;
    }

    public String type() {
        return type;
    }

    public Object value() {
        return value;
    }

    public String path() {
        return path;
    }

    public ActionParser parser() {
        return parser;
    }

    public boolean isMap() {
        return value instanceof Map<?, ?>;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> map() {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    public String text() {
        return value == null ? "" : Values.str(value).trim();
    }

    public Template template() {
        return Template.compile(text());
    }

    /** Partes separadas por ';' (ignorando los ';' dentro de {expresiones}), cada una como plantilla. */
    public List<Template> parts() {
        List<Template> out = new ArrayList<>();
        for (String s : split(text(), ';')) out.add(Template.compile(s.trim()));
        return out;
    }

    public Expression expr() {
        String t = text();
        if (t.isEmpty()) throw new IllegalArgumentException("'" + type + "' necesita una expresión");
        return Expression.compile(t);
    }

    /** Clave adicional del nodo (además de la acción y los modificadores), marcándola como usada. */
    public Object extra(String key) {
        used.add(key);
        Object v = extra.get(key);
        if (v == null && isMap()) v = map().get(key);
        return v;
    }

    Set<String> unusedExtras() {
        Set<String> s = new HashSet<>(extra.keySet());
        s.removeAll(used);
        return s;
    }

    public List<ActionNode> nested(String key) {
        used.add(key);
        Object v = extra.get(key);
        return parser.parseList(v, path + "." + key);
    }

    public static List<String> split(String s, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (depth > 0 && quote != 0) {
                if (c == quote) quote = 0;
            } else if (depth > 0 && (c == '\'' || c == '"')) {
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
            } else if (c == sep && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }
}
