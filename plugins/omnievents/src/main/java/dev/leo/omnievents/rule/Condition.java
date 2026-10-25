package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionNode;
import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Una condición de regla (spec 006, H1 y H2): una expresión, o un grupo «al menos N de M», cada una con sus acciones
 * deny opcionales.
 */
public sealed interface Condition {
    /** Ruta en el archivo (regla.conditions[1].of[0]). */
    String path();

    /** Acciones que corren si esta es la primera condición que falla (vacía = el else de la regla). */
    List<ActionNode> deny();

    /** Texto para /omni info. */
    String source();

    /**
     * Evalúa la condición. Con {@code out} agrega la explicación (✔/✘ y los valores). Con {@code all} no corta un
     * grupo en cuanto se decide (/omni check). Un error de evaluación cuenta como falso y se informa a {@code onError}.
     */
    boolean test(EvalContext ctx, Consumer<String> out, String ind, boolean all, BiConsumer<Single, ExprException> onError);

    record Single(String path, Expression expr, List<ActionNode> deny) implements Condition {
        @Override
        public String source() {
            return expr.source();
        }

        @Override
        public boolean test(EvalContext ctx, Consumer<String> out, String ind, boolean all, BiConsumer<Single, ExprException> onError) {
            try {
                boolean ok = expr.test(ctx);
                if (out != null) out.accept(ind + (ok ? "✔ " : "✘ ") + expr.explain(ctx));
                return ok;
            } catch (ExprException e) {
                if (onError != null) onError.accept(this, e);
                if (out != null) out.accept(ind + "✘ error en «" + expr.source() + "»: " + e.getMessage());
                return false;
            }
        }
    }

    record AnyOf(String path, int min, List<Condition> of, List<ActionNode> deny) implements Condition {
        @Override
        public String source() {
            List<String> parts = new ArrayList<>();
            for (Condition c : of) parts.add(c.source());
            return "al menos " + min + " de [" + String.join(" | ", parts) + "]";
        }

        @Override
        public boolean test(EvalContext ctx, Consumer<String> out, String ind, boolean all, BiConsumer<Single, ExprException> onError) {
            List<String> lines = out == null ? null : new ArrayList<>();
            int passed = 0;
            int evaluated = 0;
            for (int i = 0; i < of.size(); i++) {
                if (!all && (passed >= min || passed + (of.size() - i) < min)) break;
                evaluated++;
                if (of.get(i).test(ctx, lines == null ? null : lines::add, ind + "    ", all, onError)) passed++;
            }
            boolean ok = passed >= min;
            if (out != null) {
                out.accept(ind + (ok ? "✔ " : "✘ ") + "al menos " + min + " de " + of.size() + " → se cumplen " + passed
                        + (evaluated < of.size() ? " (de " + evaluated + " evaluadas)" : ""));
                lines.forEach(out);
            }
            return ok;
        }
    }
}
