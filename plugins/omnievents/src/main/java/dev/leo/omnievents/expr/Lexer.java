package dev.leo.omnievents.expr;

import java.util.ArrayList;
import java.util.List;

/** Convierte el texto de una expresión en tokens. */
public final class Lexer {
    private static final String[] SYMBOLS = {"!~=", "==", "!=", "~=", "<=", ">=", "&&", "||", "<", ">", "+", "-", "*", "/", "%", "!"};

    private final String src;
    private int i;

    private Lexer(String src) {
        this.src = src;
    }

    public static List<Token> tokenize(String src) {
        return new Lexer(src).run();
    }

    private List<Token> run() {
        List<Token> out = new ArrayList<>();
        while (true) {
            skipSpaces();
            if (i >= src.length()) {
                out.add(new Token(Token.Type.EOF, "", null, i + 1));
                return out;
            }
            int start = i;
            char c = src.charAt(i);
            if (Character.isDigit(c) || (c == '.' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1)))) {
                out.add(number());
            } else if (c == '\'' || c == '"') {
                out.add(string(c));
            } else if (Character.isLetter(c) || c == '_') {
                while (i < src.length() && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
                out.add(new Token(Token.Type.IDENT, src.substring(start, i), null, start + 1));
            } else if (c == '%' && placeholderEnd() > 0) {
                int end = placeholderEnd();
                out.add(new Token(Token.Type.PLACEHOLDER, src.substring(start, end + 1), null, start + 1));
                i = end + 1;
            } else {
                Token t = single(c, start);
                if (t == null) t = symbol(start);
                out.add(t);
            }
        }
    }

    private void skipSpaces() {
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) i++;
    }

    private Token number() {
        int start = i;
        while (i < src.length() && Character.isDigit(src.charAt(i))) i++;
        if (i < src.length() && src.charAt(i) == '.' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1))) {
            i++;
            while (i < src.length() && Character.isDigit(src.charAt(i))) i++;
        }
        String text = src.substring(start, i);
        return new Token(Token.Type.NUMBER, text, Double.parseDouble(text), start + 1);
    }

    private Token string(char quote) {
        int start = i++;
        StringBuilder sb = new StringBuilder();
        while (i < src.length()) {
            char c = src.charAt(i++);
            if (c == '\\' && i < src.length()) {
                char n = src.charAt(i++);
                // Solo \n, \t, \\ y las comillas son escapes; el resto (\d, \b…) llega tal cual a las regex.
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '\\', '\'', '"' -> sb.append(n);
                    default -> sb.append('\\').append(n);
                }
            } else if (c == quote) {
                return new Token(Token.Type.STRING, src.substring(start, i), sb.toString(), start + 1);
            } else {
                sb.append(c);
            }
        }
        throw new ExprException("Texto sin cerrar: falta " + quote, start + 1);
    }

    /** Índice del % que cierra un placeholder que empieza en i, o -1. */
    private int placeholderEnd() {
        int j = i + 1;
        while (j < src.length()) {
            char c = src.charAt(j);
            if (c == '%') return j > i + 1 ? j : -1;
            if (!(Character.isLetterOrDigit(c) || "_:{}.-#".indexOf(c) >= 0)) return -1;
            j++;
        }
        return -1;
    }

    private Token single(char c, int start) {
        Token.Type t = switch (c) {
            case '(' -> Token.Type.LPAREN;
            case ')' -> Token.Type.RPAREN;
            case '[' -> Token.Type.LBRACK;
            case ']' -> Token.Type.RBRACK;
            case ',' -> Token.Type.COMMA;
            case '.' -> Token.Type.DOT;
            case '?' -> Token.Type.QUESTION;
            case ':' -> Token.Type.COLON;
            default -> null;
        };
        if (t == null) return null;
        i++;
        return new Token(t, String.valueOf(c), null, start + 1);
    }

    private Token symbol(int start) {
        for (String s : SYMBOLS) {
            if (src.startsWith(s, i)) {
                i += s.length();
                return new Token(Token.Type.OP, s, null, start + 1);
            }
        }
        if (src.startsWith("=", i)) { // '=' suelto: lo tratamos como '==' (estilo ConditionalEvents)
            i++;
            return new Token(Token.Type.OP, "==", null, start + 1);
        }
        if (src.startsWith("&", i) || src.startsWith("|", i)) {
            throw new ExprException("Usa '" + src.charAt(i) + src.charAt(i) + "' (doble) para " + (src.charAt(i) == '&' ? "Y" : "O"), start + 1);
        }
        throw new ExprException("Carácter inesperado '" + src.charAt(i) + "'", start + 1);
    }
}
