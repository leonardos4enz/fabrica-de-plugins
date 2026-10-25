package dev.leo.omnievents.expr;

/** Expresión compilada junto con su texto original. */
public record Expression(String source, Node root) {
    /** Mientras se carga una regla, junta las expresiones compiladas para validarlas. */
    private static final ThreadLocal<java.util.List<Expression>> COLLECT = new ThreadLocal<>();

    public static Expression compile(String source) {
        Expression e = new Expression(source, Parser.parse(source));
        java.util.List<Expression> c = COLLECT.get();
        if (c != null) c.add(e);
        return e;
    }

    public static void startCollecting() {
        COLLECT.set(new java.util.ArrayList<>());
    }

    public static java.util.List<Expression> stopCollecting() {
        java.util.List<Expression> c = COLLECT.get();
        COLLECT.remove();
        return c == null ? java.util.List.of() : c;
    }

    /** Nombres raíz (identificadores sueltos) que usa la expresión. */
    public java.util.Set<String> roots() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        collect(root, out);
        return out;
    }

    private static void collect(Node n, java.util.Set<String> out) {
        switch (n) {
            case Node.Ident i -> out.add(i.name());
            case Node.ListNode l -> l.items().forEach(x -> collect(x, out));
            case Node.Prop p -> collect(p.target(), out);
            case Node.Index i -> {
                collect(i.target(), out);
                collect(i.index(), out);
            }
            case Node.Call c -> c.args().forEach(x -> collect(x, out));
            case Node.MethodCall m -> {
                collect(m.target(), out);
                m.args().forEach(x -> collect(x, out));
            }
            case Node.Not x -> collect(x.operand(), out);
            case Node.Neg x -> collect(x.operand(), out);
            case Node.And a -> {
                collect(a.left(), out);
                collect(a.right(), out);
            }
            case Node.Or o -> {
                collect(o.left(), out);
                collect(o.right(), out);
            }
            case Node.Ternary t -> {
                collect(t.cond(), out);
                collect(t.then(), out);
                collect(t.otherwise(), out);
            }
            case Node.Binary b -> {
                collect(b.left(), out);
                collect(b.right(), out);
            }
            case Node.Literal l -> { }
            case Node.VarRef v -> { }
            case Node.DataRef d -> collect(d.target(), out);
            case Node.Placeholder p -> { }
        }
    }

    public Object eval(EvalContext ctx) {
        return root.eval(ctx);
    }

    public boolean test(EvalContext ctx) {
        return Values.truthy(root.eval(ctx));
    }

    public String explain(EvalContext ctx) {
        return root.explain(ctx);
    }

    @Override
    public String toString() {
        return source;
    }
}
