package dev.leo.omnievents.expr;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Contexto de prueba: raíces y variables en mapas; los placeholders devuelven lo que diga el mapa. */
final class TestContext implements EvalContext {
    final Map<String, Object> roots = new HashMap<>();
    final Map<String, Object> vars = new HashMap<>();
    final Map<String, Object> placeholders = new HashMap<>();
    /** Spec 009: variables de objetos (objeto → nombre → valor) y lo que devuelven los servicios (ttl…). */
    final Map<Object, Map<String, Object>> objectVars = new HashMap<>();
    final Map<String, Object> services = new HashMap<>();

    TestContext with(String name, Object value) {
        roots.put(name, value);
        return this;
    }

    @Override
    public Object resolve(String name) {
        return roots.get(name);
    }

    @Override
    public Set<String> roots() {
        return roots.keySet();
    }

    @Override
    public Object getVar(Scope scope, String name) {
        return vars.get(scope.prefix + "." + name);
    }

    @Override
    public Object objectVar(Object target, String name) {
        Map<String, Object> m = objectVars.get(target);
        return m == null ? NOT_OBJECT : m.get(name);
    }

    @Override
    public Object service(String name, Object... args) {
        StringBuilder k = new StringBuilder(name);
        for (Object a : args) k.append(":").append(a);
        return services.get(k.toString());
    }

    @Override
    public Object placeholder(String raw, Object player) {
        return placeholders.getOrDefault(raw, raw);
    }
}
