package dev.leo.omnievents.expr;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Contexto de prueba: raíces y variables en mapas; los placeholders devuelven lo que diga el mapa. */
final class TestContext implements EvalContext {
    final Map<String, Object> roots = new HashMap<>();
    final Map<String, Object> vars = new HashMap<>();
    final Map<String, Object> placeholders = new HashMap<>();

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
    public Object placeholder(String raw, Object player) {
        return placeholders.getOrDefault(raw, raw);
    }
}
