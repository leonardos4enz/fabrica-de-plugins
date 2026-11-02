package dev.leo.worldcraft.formula;

import java.util.ArrayList;
import java.util.List;

/** Parte el texto de una fórmula en fichas (plan 034 §1.1), cada una con su posición (1 = primer carácter). */
final class Lexer {
    enum Kind { NUM, NAME, OP, END }

    record Token(Kind kind, String text, double value, int pos) {
        boolean is(String op) {
            return (kind == Kind.OP || kind == Kind.NAME) && text.equals(op);
        }
    }

    /** Los operadores, de más largo a más corto (se toma el más largo que coincide). */
    private static final String[] OPS = {
            "**=", "**", "++", "--", "+=", "-=", "*=", "/=", "%=", "^=", "==", "!=", "~=", "<=", ">=", "&&", "||", "<<", ">>",
            "+", "-", "*", "/", "%", "^", "<", ">", "=", "!", "~", "?", ":", "(", ")", "{", "}", ",", ";"
    };

    private Lexer() {
    }

    static List<Token> tokens(String s) throws FormulaException {
        List<Token> out = new ArrayList<>();
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                    int j = i + 1;
                    if (j < n && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
                    if (j < n && Character.isDigit(s.charAt(j))) {
                        i = j;
                        while (i < n && Character.isDigit(s.charAt(i))) i++;
                    }
                }
                String text = s.substring(start, i);
                try {
                    out.add(new Token(Kind.NUM, text, Double.parseDouble(text), start + 1));
                } catch (NumberFormatException e) {
                    throw new FormulaException("'" + text + "' at " + (start + 1) + " is not a number");
                }
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                out.add(new Token(Kind.NAME, s.substring(start, i), 0, start + 1));
                continue;
            }
            String op = null;
            for (String o : OPS) {
                if (s.startsWith(o, i)) {
                    op = o;
                    break;
                }
            }
            if (op == null) throw new FormulaException("Unexpected '" + c + "' at " + (start + 1));
            out.add(new Token(Kind.OP, op, 0, start + 1));
            i += op.length();
        }
        out.add(new Token(Kind.END, "", 0, n + 1));
        return out;
    }
}
