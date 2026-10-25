package dev.leo.omnievents.expr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Árbol inmutable de una expresión compilada. */
public sealed interface Node {
    Object eval(EvalContext ctx);

    /** Texto equivalente (para trazas). */
    String text();

    /** Explicación para /omni debug: valores intermedios y resultado. */
    default String explain(EvalContext ctx) {
        return text() + " → " + show(eval(ctx));
    }

    static String show(Object v) {
        if (v == null) return "nulo";
        if (v instanceof String s) return "'" + s + "'";
        return Values.str(v);
    }

    record Literal(Object value) implements Node {
        public Object eval(EvalContext ctx) {
            return value;
        }

        public String text() {
            return value instanceof String s ? "'" + s.replace("'", "\\'") + "'" : show(value);
        }
    }

    record ListNode(List<Node> items) implements Node {
        public Object eval(EvalContext ctx) {
            List<Object> out = new ArrayList<>(items.size());
            for (Node n : items) out.add(n.eval(ctx));
            return out;
        }

        public String text() {
            return "[" + String.join(", ", items.stream().map(Node::text).toList()) + "]";
        }
    }

    record Ident(String name) implements Node {
        public Object eval(EvalContext ctx) {
            return Values.normalize(ctx.resolve(name));
        }

        public String text() {
            return name;
        }
    }

    record VarRef(Scope scope, String name) implements Node {
        public Object eval(EvalContext ctx) {
            return ctx.getVar(scope, name);
        }

        public String text() {
            return scope.prefix + "." + name;
        }
    }

    /**
     * Variable de un bloque, una entidad o un ítem: {@code X.data.nombre} (spec 009). Si X no es ninguno de esos,
     * conserva el significado anterior (la propiedad data de X y luego nombre).
     */
    record DataRef(Node target, String name) implements Node {
        public Object eval(EvalContext ctx) {
            Object t = target.eval(ctx);
            if (t == null) return null;
            Object v = ctx.objectVar(t, name);
            if (v != EvalContext.NOT_OBJECT) return Values.normalize(v);
            return Properties.get().read(Properties.get().read(t, "data"), name);
        }

        public String text() {
            return target.text() + ".data." + name;
        }
    }

    record Placeholder(String raw) implements Node {
        public Object eval(EvalContext ctx) {
            Object v = ctx.placeholder(raw, null);
            Double d = Values.toNumberOrNull(v);
            return d != null ? d : v;
        }

        public String text() {
            return raw;
        }
    }

    record Prop(Node target, String name) implements Node {
        public Object eval(EvalContext ctx) {
            return Properties.get().read(target.eval(ctx), name);
        }

        public String text() {
            return target.text() + "." + name;
        }
    }

    record Index(Node target, Node index) implements Node {
        public Object eval(EvalContext ctx) {
            Object t = target.eval(ctx);
            Object i = index.eval(ctx);
            if (t == null) return null;
            if (t instanceof Map<?, ?> m) {
                Object v = m.get(i instanceof String ? i : Values.str(i));
                return Values.normalize(v);
            }
            if (t instanceof String s) {
                int k = (int) Values.num(i);
                if (k < 0) k += s.length();
                return k >= 0 && k < s.length() ? String.valueOf(s.charAt(k)) : null;
            }
            if (Values.isNumeric(i)) {
                List<Object> l = Values.list(t);
                int k = (int) Values.num(i);
                if (k < 0) k += l.size();
                return k >= 0 && k < l.size() ? Values.normalize(l.get(k)) : null;
            }
            return Properties.get().read(t, Values.str(i));
        }

        public String text() {
            return target.text() + "[" + index.text() + "]";
        }
    }

    record Call(String name, Functions.Def def, List<Node> args) implements Node {
        public Object eval(EvalContext ctx) {
            if (def.lazy()) return def.impl().call(ctx, args.toArray());
            Object[] vals = new Object[args.size()];
            for (int i = 0; i < vals.length; i++) vals[i] = args.get(i).eval(ctx);
            return Values.normalize(def.impl().call(ctx, vals));
        }

        public String text() {
            return name + "(" + String.join(", ", args.stream().map(Node::text).toList()) + ")";
        }
    }

    record MethodCall(Node target, String name, List<Node> args) implements Node {
        public Object eval(EvalContext ctx) {
            Object t = target.eval(ctx);
            Object[] vals = new Object[args.size()];
            for (int i = 0; i < vals.length; i++) vals[i] = args.get(i).eval(ctx);
            return Properties.get().invoke(t, name, vals);
        }

        public String text() {
            return target.text() + "." + name + "(" + String.join(", ", args.stream().map(Node::text).toList()) + ")";
        }
    }

    record Not(Node operand) implements Node {
        public Object eval(EvalContext ctx) {
            return !Values.truthy(operand.eval(ctx));
        }

        public String text() {
            return "!" + operand.text();
        }

        public String explain(EvalContext ctx) {
            return "!(" + operand.explain(ctx) + ") → " + show(eval(ctx));
        }
    }

    record Neg(Node operand) implements Node {
        public Object eval(EvalContext ctx) {
            return -Values.num(operand.eval(ctx));
        }

        public String text() {
            return "-" + operand.text();
        }
    }

    record And(Node left, Node right) implements Node {
        public Object eval(EvalContext ctx) {
            return Values.truthy(left.eval(ctx)) && Values.truthy(right.eval(ctx));
        }

        public String text() {
            return "(" + left.text() + " && " + right.text() + ")";
        }

        public String explain(EvalContext ctx) {
            boolean l = Values.truthy(left.eval(ctx));
            if (!l) return "(" + left.explain(ctx) + ") && … → false";
            return "(" + left.explain(ctx) + ") && (" + right.explain(ctx) + ") → " + show(eval(ctx));
        }
    }

    record Or(Node left, Node right) implements Node {
        public Object eval(EvalContext ctx) {
            return Values.truthy(left.eval(ctx)) || Values.truthy(right.eval(ctx));
        }

        public String text() {
            return "(" + left.text() + " || " + right.text() + ")";
        }

        public String explain(EvalContext ctx) {
            boolean l = Values.truthy(left.eval(ctx));
            if (l) return "(" + left.explain(ctx) + ") || … → true";
            return "(" + left.explain(ctx) + ") || (" + right.explain(ctx) + ") → " + show(eval(ctx));
        }
    }

    record Ternary(Node cond, Node then, Node otherwise) implements Node {
        public Object eval(EvalContext ctx) {
            return Values.truthy(cond.eval(ctx)) ? then.eval(ctx) : otherwise.eval(ctx);
        }

        public String text() {
            return "(" + cond.text() + " ? " + then.text() + " : " + otherwise.text() + ")";
        }
    }

    record Binary(Op op, boolean negated, Node left, Node right) implements Node {
        public Object eval(EvalContext ctx) {
            Object r = op.apply(left.eval(ctx), right.eval(ctx));
            return negated ? !Values.truthy(r) : r;
        }

        public String text() {
            return left.text() + " " + (negated ? "!" : "") + op.symbol + " " + right.text();
        }

        public String explain(EvalContext ctx) {
            Object l = left.eval(ctx), r = right.eval(ctx);
            Object res = op.apply(l, r);
            if (negated) res = !Values.truthy(res);
            String ls = left instanceof Literal ? left.text() : left.text() + " (" + show(l) + ")";
            String rs = right instanceof Literal ? right.text() : right.text() + " (" + show(r) + ")";
            return ls + " " + (negated ? "!" : "") + op.symbol + " " + rs + " → " + show(res);
        }
    }

    enum Op {
        EQ("=="), NE("!="), IEQ("~="), INE("!~="), LT("<"), LE("<="), GT(">"), GE(">="),
        CONTAINS("contains"), STARTS("startsWith"), ENDS("endsWith"), MATCHES("matches"), IN("in"),
        ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%");

        private static final Map<String, Pattern> REGEX = new ConcurrentHashMap<>();
        public final String symbol;

        Op(String symbol) {
            this.symbol = symbol;
        }

        public Object apply(Object a, Object b) {
            return switch (this) {
                case EQ -> Values.looseEquals(a, b);
                case NE -> !Values.looseEquals(a, b);
                case IEQ -> Values.str(a).equalsIgnoreCase(Values.str(b));
                case INE -> !Values.str(a).equalsIgnoreCase(Values.str(b));
                case LT -> cmp(a, b, c -> c < 0);
                case LE -> cmp(a, b, c -> c <= 0);
                case GT -> cmp(a, b, c -> c > 0);
                case GE -> cmp(a, b, c -> c >= 0);
                case CONTAINS -> contains(a, b);
                case STARTS -> a != null && Values.str(a).startsWith(Values.str(b));
                case ENDS -> a != null && Values.str(a).endsWith(Values.str(b));
                case MATCHES -> a != null && regex(Values.str(b)).matcher(Values.str(a)).matches();
                case IN -> contains(b, a);
                case ADD -> add(a, b);
                case SUB -> Values.num(a) - Values.num(b);
                case MUL -> Values.num(a) * Values.num(b);
                case DIV -> {
                    double d = Values.num(b);
                    if (d == 0) throw new ExprException("División entre cero");
                    yield Values.num(a) / d;
                }
                case MOD -> {
                    double d = Values.num(b);
                    if (d == 0) throw new ExprException("Módulo entre cero");
                    yield Values.num(a) % d;
                }
            };
        }

        private static boolean cmp(Object a, Object b, java.util.function.IntPredicate p) {
            Integer c = Values.compare(a, b);
            return c != null && p.test(c);
        }

        private static Object add(Object a, Object b) {
            if (Values.isNumeric(a) && Values.isNumeric(b) && !(a instanceof String && b instanceof String)) {
                return Values.num(a) + Values.num(b);
            }
            if (a instanceof Number && b == null) return a;
            if (b instanceof Number && a == null) return b;
            if (a instanceof List<?> la) {
                List<Object> out = new ArrayList<>(la);
                if (b instanceof List<?> lb) out.addAll(lb);
                else out.add(b);
                return out;
            }
            return Values.str(a) + Values.str(b);
        }

        static boolean contains(Object container, Object item) {
            if (container == null) return false;
            if (container instanceof Map<?, ?> m) {
                String k = Values.str(item);
                for (Object key : m.keySet()) if (Values.str(key).equalsIgnoreCase(k)) return true;
                return false;
            }
            if (container instanceof java.util.Collection<?> c) {
                for (Object o : c) if (Values.looseEquals(Values.normalize(o), item)) return true;
                return false;
            }
            return Values.str(container).contains(Values.str(item));
        }

        static Pattern regex(String p) {
            return REGEX.computeIfAbsent(p, k -> {
                try {
                    return Pattern.compile(k);
                } catch (PatternSyntaxException e) {
                    throw new ExprException("Expresión regular inválida: " + e.getDescription());
                }
            });
        }

        static Op byWord(String w) {
            return switch (w.toLowerCase(Locale.ROOT)) {
                case "contains" -> CONTAINS;
                case "startswith" -> STARTS;
                case "endswith" -> ENDS;
                case "matches" -> MATCHES;
                case "in" -> IN;
                case "equals" -> EQ;
                case "equalsignorecase" -> IEQ;
                default -> null;
            };
        }
    }
}
