package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionException;
import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.action.ExecContext;
import dev.leo.omnievents.action.Execution;
import dev.leo.omnievents.action.Purse;
import dev.leo.omnievents.action.Sandbox;
import dev.leo.omnievents.debug.Trace;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Named;
import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.TextFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corre los casos de tests de las reglas (spec 010, plan §1.3) con el motor de verdad sobre un {@link Sandbox}: sin
 * servidor, sin jugadores y sin cambiar ningún dato real. Lo usan /omni test, la carga y las unitarias.
 */
public final class RuleTester {
    /** Resultado de un caso: las diferencias con expect (vacías si pasó) y la traza de la regla. */
    public record Result(Rule rule, RuleTest test, List<String> failures, List<String> trace) {
        public boolean ok() {
            return failures.isEmpty();
        }
    }

    /** Resultado de varias reglas; {@code untested}: reglas sin tests. */
    public record Summary(List<Result> results, int untested) {
        public long failed() {
            return results.stream().filter(r -> !r.ok()).count();
        }

        public long passed() {
            return results.size() - failed();
        }
    }

    /** Tipos de acción que cuentan como mensajes (H2 CA4). */
    public static final Set<String> MESSAGE_TYPES = Set.of("message", "centered_message", "json_message", "broadcast", "actionbar", "title", "bossbar", "console_message",
            "log", "kick", "chat", "deny_join", "set_message", "set_join_message", "set_quit_message", "set_death_message");

    private static final Pattern SCOPED = Pattern.compile("^(pvar|gvar|var)\\.([A-Za-z0-9_\\-]+)$");
    private static final Pattern DATA = Pattern.compile("^(.+?)\\.data\\.([\\p{L}0-9_]+)$");

    private final Function<String, Rule> rules;
    private final int maxLoops;
    private final int maxCallDepth;

    public RuleTester(Function<String, Rule> rules, int maxLoops, int maxCallDepth) {
        this.rules = rules;
        this.maxLoops = maxLoops;
        this.maxCallDepth = maxCallDepth;
    }

    public Summary runAll(Collection<Rule> all) {
        List<Rule> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        List<Result> out = new ArrayList<>();
        int untested = 0;
        for (Rule r : sorted) {
            if (r.tests().isEmpty()) untested++;
            else out.addAll(run(r));
        }
        return new Summary(out, untested);
    }

    public List<Result> run(Rule rule) {
        List<Result> out = new ArrayList<>();
        for (RuleTest t : rule.tests()) out.add(run(rule, t));
        return out;
    }

    public Result run(Rule rule, RuleTest test) {
        Sandbox sb = new Sandbox(test.seed(), maxLoops, maxCallDepth);
        Trace trace = new Trace(rule.name());
        List<String> failures = new ArrayList<>();
        Map<String, Object> bindings = new HashMap<>();
        Map<String, Object> locals = new LinkedHashMap<>();
        try {
            given(test, bindings, locals, sb);
        } catch (IllegalArgumentException | ExprException e) {
            failures.add("given: " + e.getMessage());
            return new Result(rule, test, failures, trace.lines());
        }
        ExecContext ctx = new ExecContext(rule, bindings, sb);
        ctx.locals().putAll(locals);
        sb.caller((name, from, args, depth, tr) -> {
            Rule target = Rule.resolve(name, from.rule(), rules);
            if (target == null) throw new ActionException("no existe la regla '" + name + "' para call");
            ExecContext c = from.forCall(target, new LinkedHashMap<>(args));
            if (from.rule() != null) c.locals().putIfAbsent("caller", from.rule().name());
            tr.add("  ↳ call " + target.name());
            walk(target, c, sb, depth, tr, "  ↳ ");
            tr.add("  ↳ (fin de " + target.name() + ")");
        });
        String branch;
        try {
            branch = walk(rule, ctx, sb, 0, trace, "");
        } catch (RuntimeException e) {
            failures.add("error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return new Result(rule, test, failures, trace.lines());
        }
        compare(test.expect(), branch, ctx, sb, failures);
        return new Result(rule, test, failures, trace.lines());
    }

    // ------------------------------------------------------------------ given → contexto

    @SuppressWarnings("unchecked")
    private static void given(RuleTest test, Map<String, Object> bindings, Map<String, Object> locals, Sandbox sb) {
        for (Map.Entry<String, Object> e : test.given()) {
            String key = e.getKey();
            Object value = value(e.getValue());
            if (key.startsWith("%") && key.endsWith("%")) {
                sb.placeholder(key, value);
                continue;
            }
            // Spec 012: saldo, inventario y regiones del caso.
            switch (key) {
                case "money" -> {
                    sb.money(Values.num(value));
                    continue;
                }
                case "inventory" -> {
                    if (e.getValue() instanceof Map<?, ?> inv) inv.forEach((m, n) -> sb.inventory(String.valueOf(m), (int) Values.num(n)));
                    continue;
                }
                case "regions" -> {
                    sb.regions(Values.list(e.getValue()).stream().map(Values::str).toList());
                    continue;
                }
                // Spec 013: si el jugador es de Bedrock (is_bedrock()).
                case "bedrock" -> {
                    sb.bedrock(Values.truthy(value));
                    continue;
                }
                default -> { }
            }
            Matcher sc = SCOPED.matcher(key);
            if (sc.matches()) {
                String name = sc.group(2);
                switch (sc.group(1)) {
                    case "pvar" -> sb.put(Scope.PLAYER, name, value, 0);
                    case "gvar" -> sb.put(Scope.GLOBAL, name, value, 0);
                    default -> locals.put(name, value);
                }
                continue;
            }
            Matcher d = DATA.matcher(key);
            if (d.matches()) {
                sb.objectSet(object(bindings, d.group(1)), d.group(2), value);
                continue;
            }
            int dot = key.lastIndexOf('.');
            if (dot < 0) {
                if (bindings.get(key) instanceof Map<?, ?> && !(value instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException("'" + key + "' ya tiene propiedades; no puede valer también " + Values.str(value));
                }
                bindings.put(key, value);
            } else {
                Map<String, Object> parent = (Map<String, Object>) object(bindings, key.substring(0, dot));
                parent.put(key.substring(dot + 1), value);
            }
        }
    }

    /** El mapa en esa ruta (block, player.location), creándolo si falta. */
    @SuppressWarnings("unchecked")
    private static Object object(Map<String, Object> bindings, String path) {
        Map<String, Object> cur = bindings;
        String done = "";
        for (String part : path.split("\\.")) {
            done = done.isEmpty() ? part : done + "." + part;
            Object next = cur.get(part);
            if (next == null) {
                next = new LinkedHashMap<String, Object>();
                cur.put(part, next);
            } else if (!(next instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("'" + done + "' ya vale " + Values.str(next) + "; no puede tener propiedades");
            }
            cur = (Map<String, Object>) next;
        }
        return cur;
    }

    /** Un texto como DIAMOND_ORE o SURVIVAL: en el servidor es un enum o un valor de registro. */
    private static final Pattern CONSTANT = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    /** Valores de YAML como los del motor: números en double, constantes como Named, mapas y listas por dentro. */
    @SuppressWarnings("unchecked")
    static Object value(Object v) {
        if (v == RuleLoader.NULL) return null;
        if (v instanceof Number n) return n.doubleValue();
        // Regresión (spec 010, F-01): player.gamemode == 'survival' se cumple en el servidor (GameMode es un enum y
        // se compara sin mayúsculas), pero no con el texto SURVIVAL de given. Una constante se comporta como un enum.
        if (v instanceof String s && CONSTANT.matcher(s).matches()) return new Named(s, s);
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<Object, Object>) m).forEach((k, x) -> out.put(String.valueOf(k), value(x)));
            return out;
        }
        if (v instanceof List<?> l) return new ArrayList<>(l.stream().map(RuleTester::value).toList());
        return v;
    }

    // ------------------------------------------------------------------ recorrido

    /** Recorre la regla como un disparo (plan §1.3, paso 2); devuelve la rama que corrió. */
    private String walk(Rule rule, ExecContext ctx, Sandbox sb, int depth, Trace trace, String ind) {
        boolean state = rule.state() != null;
        List<String> skipped = new ArrayList<>();
        if (!rule.worlds().isEmpty()) skipped.add("worlds");
        if (rule.permission != null) skipped.add("permission");
        if (rule.bypassPermission != null) skipped.add("bypass_permission");
        if (rule.cooldownMs > 0) skipped.add("cooldown");
        if (rule.limit > 0) skipped.add("limit");
        if (!skipped.isEmpty()) trace.add(ind + "• la prueba no revisa " + String.join(", ", skipped));
        List<Condition> conditions = state ? rule.state().when() : rule.conditions();
        for (Condition c : conditions) {
            boolean ok = c.test(ctx, trace::add, ind, false, (single, e) -> sb.error(RuleManager.part(rule, single.path()) + ": " + e.getMessage()));
            if (ok) continue;
            if (state) return "none";
            List<ActionNode> branch = c.deny().isEmpty() ? rule.elseActions : c.deny();
            String name = c.deny().isEmpty() ? "else" : "deny " + RuleManager.part(rule, c.path());
            if (branch.isEmpty()) return "none";
            trace.add(ind + "→ " + name);
            execute(rule, branch, ctx, sb, depth, trace);
            return name;
        }
        // Spec 012, H2 CA5: el costo se cobra sobre el Sandbox; si falta algo, la rama es on_cost_fail.
        if (rule.cost != null) {
            Purse purse = sb.purse(ctx);
            Cost.Quote q = rule.cost.quote(ctx, purse);
            Cost.Line failed = q.ok() ? rule.cost.charge(q, purse, (l, why) -> sb.error("devolución de " + l.key() + ": " + why)) : null;
            if (!q.ok() || failed != null) {
                costFailed(ctx, q, failed, purse);
                trace.add(ind + "✘ costo: falta " + ctx.locals().get("cost_missing") + " → on_cost_fail");
                execute(rule, rule.onCostFail, ctx, sb, depth, trace);
                return "on_cost_fail";
            }
            trace.add(ind + "✔ costo cobrado");
        }
        trace.add(ind + "→ " + (state ? "on_enter" : "actions"));
        execute(rule, rule.actions, ctx, sb, depth, trace);
        return state ? "on_enter" : "actions";
    }

    /** var.cost_missing y var.cost_missing_list (H2 CA2): lo que falta, o la parte que no se pudo cobrar. */
    static void costFailed(ExecContext ctx, Cost.Quote q, Cost.Line failed, Purse purse) {
        if (failed == null) {
            ctx.locals().put("cost_missing", Cost.missingText(q, purse));
            ctx.locals().put("cost_missing_list", Cost.missingKeys(q));
        } else {
            ctx.locals().put("cost_missing", Cost.describe(failed, failed.amount(), purse) + " (no se pudo cobrar)");
            ctx.locals().put("cost_missing_list", new ArrayList<Object>(List.of(failed.key())));
        }
    }

    private static void execute(Rule rule, List<ActionNode> nodes, ExecContext ctx, Sandbox sb, int depth, Trace trace) {
        new Execution(sb, rule, depth, trace).start(nodes, ctx);
    }

    // ------------------------------------------------------------------ comparación

    private static void compare(RuleTest.Expect ex, String branch, ExecContext ctx, Sandbox sb, List<String> failures) {
        for (String err : sb.errors()) failures.add("error en " + err);
        boolean fired = branch.equals("actions") || branch.equals("on_enter");
        if (ex.fired() != null && ex.fired() != fired) {
            failures.add("fired: esperaba " + ex.fired() + ", dio " + fired + " (rama " + branch + ")");
        }
        if (ex.branch() != null) {
            String want = ex.branch();
            boolean ok = want.equals("deny") ? branch.startsWith("deny ") : want.equalsIgnoreCase(branch);
            if (!ok) failures.add("branch: esperaba " + want + ", dio " + branch);
        }
        for (RuleTest.VarCheck v : ex.vars()) {
            Object got;
            try {
                got = v.ref().eval(ctx);
            } catch (ExprException e) {
                failures.add(v.key() + ": " + e.getMessage());
                continue;
            }
            Object want = v.value();
            boolean ok = want == null ? got == null : got != null && Values.looseEquals(got, want instanceof Number n ? n.doubleValue() : want);
            if (!ok) failures.add(v.key() + ": esperaba " + show(want) + ", dio " + show(got));
        }
        List<Sandbox.Effect> effects = sb.effects();
        inOrder("messages", ex.messages(), effects.stream().filter(e -> MESSAGE_TYPES.contains(e.type())).map(Sandbox.Effect::text).toList(), failures);
        inOrder("commands", ex.commands(), effects.stream().filter(e -> ActionNode.COMMANDS.contains(e.type())).map(Sandbox.Effect::text).toList(), failures);
        actions(ex.actions(), effects, failures);
        if (ex.cancelled() != null) {
            boolean got = Boolean.TRUE.equals(sb.cancelled());
            if (got != ex.cancelled()) failures.add("cancelled: esperaba " + ex.cancelled() + ", dio " + got);
        }
        for (Expression a : ex.asserts()) {
            try {
                if (!a.test(ctx)) failures.add("assert: " + a.explain(ctx) + " → false");
            } catch (ExprException e) {
                failures.add("assert «" + a.source() + "»: " + e.getMessage());
            }
        }
    }

    private static String show(Object v) {
        if (v == null) return "nulo";
        return v instanceof String s ? "'" + s + "'" : Values.str(v);
    }

    /** H2 CA4: cada texto esperado aparece, en orden, dentro de uno de los que salieron (sin formato ni mayúsculas). */
    private static void inOrder(String what, List<String> want, List<String> got, List<String> failures) {
        int i = 0;
        for (String w : want) {
            String needle = norm(w);
            while (i < got.size() && !norm(got.get(i)).contains(needle)) i++;
            if (i >= got.size()) {
                failures.add(what + ": no salió «" + w + "» (en orden); salieron: " + (got.isEmpty() ? "ninguno" : "«" + String.join("», «", got) + "»"));
                return;
            }
            i++;
        }
    }

    private static void actions(List<String> want, List<Sandbox.Effect> got, List<String> failures) {
        int i = 0;
        for (String w : want) {
            int colon = w.indexOf(':');
            String type = dev.leo.omnievents.action.ActionParser.canonical(colon < 0 ? w : w.substring(0, colon));
            String text = colon < 0 ? "" : norm(w.substring(colon + 1));
            while (i < got.size() && !(got.get(i).type().equals(type) && norm(got.get(i).text()).contains(text))) i++;
            if (i >= got.size()) {
                List<String> seen = got.stream().map(e -> e.type() + ": " + e.text()).toList();
                failures.add("actions: no salió «" + w + "» (en orden); salieron: " + (seen.isEmpty() ? "ninguna" : "«" + String.join("», «", seen) + "»"));
                return;
            }
            i++;
        }
    }

    private static String norm(String s) {
        return TextFormat.plain(s).trim().toLowerCase(Locale.ROOT);
    }
}
