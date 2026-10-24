package dev.leo.omnievents.debug;

import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;
import dev.leo.omnievents.text.TextFormat;
import dev.leo.omnievents.util.Durations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Casos que /omni selftest corre en el servidor vivo (compuerta 2 de VERIFY) y que las unitarias también corren.
 * Cada caso de regresión encontrado en una revisión exploratoria se agrega aquí.
 */
public final class SelfTest {
    public record Result(String name, boolean ok, String detail) {
    }

    private SelfTest() {
    }

    private static final EvalContext CTX = new EvalContext() {
        private final Map<String, Object> roots = Map.of("n", 3.0, "texto", "Hola Mundo", "lista", List.of("a", "b"));

        @Override
        public Object resolve(String name) {
            return roots.get(name);
        }
    };

    public static List<Result> run() {
        List<Result> out = new ArrayList<>();
        expr(out, "precedencia", "true || false && false", true);
        expr(out, "aritmética", "2 + 3 * 4 - 1", 13.0);
        expr(out, "paréntesis", "(2 + 3) * 4", 20.0);
        expr(out, "ternario", "n > 2 ? 'si' : 'no'", "si");
        expr(out, "texto", "texto contains 'Mundo' && texto startsWith 'Hola'", true);
        expr(out, "in", "'b' in lista && 'z' not in lista", true);
        expr(out, "nulos", "nada < 5 || nada > 5", false);
        expr(out, "ignorar mayúsculas", "texto ~= 'hola mundo'", true);
        expr(out, "funciones", "max(1, n, 2) + round(2.567, 1)", 5.6);
        check(out, "duraciones", Durations.parseMillis("1h30m") == 5_400_000 && Durations.parseMillis("20t") == 1000, "1h30m y 20t");
        String escaped = Template.compile("<green>{texto + '<red>'}").render(new EvalContext() {
            @Override
            public Object resolve(String name) {
                return "<click:run_command:/op yo>x";
            }
        }, Template.Mode.MESSAGE);
        check(out, "escape de lo interpolado (H4 CA6)", !TextFormat.plain(escaped).equals("x") && TextFormat.plain(escaped).contains("<click"), escaped);
        String cmd = Template.compile("say {texto}").render(new EvalContext() {
            @Override
            public Object resolve(String name) {
                return "hola\nop yo§c";
            }
        }, Template.Mode.COMMAND);
        check(out, "saneo de comandos (H4 CA6)", !cmd.contains("\n") && !cmd.contains("§"), cmd);
        return out;
    }

    private static void expr(List<Result> out, String name, String src, Object expected) {
        try {
            Object v = Expression.compile(src).eval(CTX);
            boolean ok = Values.looseEquals(v, expected);
            out.add(new Result(name, ok, src + " = " + Values.str(v) + (ok ? "" : " (esperado " + Values.str(expected) + ")")));
        } catch (RuntimeException e) {
            out.add(new Result(name, false, src + ": " + e.getMessage()));
        }
    }

    private static void check(List<Result> out, String name, boolean ok, String detail) {
        out.add(new Result(name, ok, detail));
    }
}
