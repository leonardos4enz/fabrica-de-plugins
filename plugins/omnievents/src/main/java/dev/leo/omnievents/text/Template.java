package dev.leo.omnievents.text;

import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Texto con {@code {expresiones}} y {@code %placeholders%}, compilado al cargar (plan 001 §2.4).
 * Lo interpolado con llaves es entrada no confiable: se escapa para mensajes y se sanea para comandos.
 */
public final class Template {
    public enum Mode {
        /** Para mostrar: MiniMessage + &amp;, lo interpolado escapado. */
        MESSAGE,
        /** Para comandos y chat: sin saltos de línea ni § en lo interpolado. */
        COMMAND,
        /** Valor tal cual (coordenadas, nombres, parámetros). */
        RAW
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("%[A-Za-z0-9_:{}.\\-#]+%");

    private sealed interface Part permits Lit, Expr {
    }

    private record Lit(String text, boolean hasPlaceholders) implements Part {
    }

    private record Expr(Expression expr) implements Part {
    }

    private final String source;
    private final List<Part> parts;

    private Template(String source, List<Part> parts) {
        this.source = source;
        this.parts = parts;
    }

    public static Template compile(String src) {
        if (src == null) src = "";
        List<Part> parts = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '\\' && i + 1 < src.length() && (src.charAt(i + 1) == '{' || src.charAt(i + 1) == '}')) {
                lit.append(src.charAt(i + 1));
                i += 2;
            } else if (c == '{') {
                int end = closing(src, i);
                if (end < 0) throw new ExprException("Falta '}' para cerrar la expresión que empieza aquí", i + 1);
                String inner = src.substring(i + 1, end).trim();
                if (lit.length() > 0) {
                    parts.add(lit(lit.toString()));
                    lit.setLength(0);
                }
                try {
                    parts.add(new Expr(Expression.compile(inner)));
                } catch (ExprException e) {
                    throw new ExprException("En {" + inner + "}: " + e.getMessage(), i + 1);
                }
                i = end + 1;
            } else {
                lit.append(c);
                i++;
            }
        }
        if (lit.length() > 0) parts.add(lit(lit.toString()));
        return new Template(src, List.copyOf(parts));
    }

    private static Lit lit(String s) {
        return new Lit(s, PLACEHOLDER.matcher(s).find());
    }

    private static int closing(String s, int open) {
        int depth = 0;
        char quote = 0;
        for (int j = open; j < s.length(); j++) {
            char c = s.charAt(j);
            if (quote != 0) {
                if (c == '\\') j++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return j;
            }
        }
        return -1;
    }

    public String source() {
        return source;
    }

    public boolean isConstant() {
        for (Part p : parts) if (p instanceof Expr || p instanceof Lit l && l.hasPlaceholders()) return false;
        return true;
    }

    /** Expresiones contenidas (para validación). */
    public List<Expression> expressions() {
        List<Expression> out = new ArrayList<>();
        for (Part p : parts) if (p instanceof Expr e) out.add(e.expr());
        return out;
    }

    public String render(EvalContext ctx, Mode mode) {
        StringBuilder sb = new StringBuilder();
        for (Part p : parts) {
            if (p instanceof Lit l) {
                String text = l.hasPlaceholders() && ctx != null ? placeholders(l.text(), ctx) : l.text();
                sb.append(mode == Mode.MESSAGE ? TextFormat.legacyToMini(text) : text);
            } else if (p instanceof Expr e) {
                String v = Values.str(e.expr().eval(ctx));
                sb.append(switch (mode) {
                    case MESSAGE -> TextFormat.escapeForMessage(v);
                    case COMMAND -> TextFormat.sanitizeForCommand(v);
                    case RAW -> v;
                });
            }
        }
        return sb.toString();
    }

    public Component component(EvalContext ctx) {
        return MiniMessage.miniMessage().deserialize(render(ctx, Mode.MESSAGE));
    }

    private static String placeholders(String text, EvalContext ctx) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = ctx.placeholder(m.group(), null);
            m.appendReplacement(sb, Matcher.quoteReplacement(Values.str(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Override
    public String toString() {
        return source;
    }
}
