package dev.leo.omnievents.expr;

public record Token(Type type, String text, Object value, int pos) {
    public enum Type { NUMBER, STRING, IDENT, PLACEHOLDER, OP, LPAREN, RPAREN, LBRACK, RBRACK, COMMA, DOT, QUESTION, COLON, EOF }

    public boolean is(Type t, String s) {
        return type == t && text.equalsIgnoreCase(s);
    }

    public boolean isOp(String s) {
        return type == Type.OP && text.equals(s);
    }
}
