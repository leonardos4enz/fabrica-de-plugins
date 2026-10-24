package dev.leo.omnievents.expr;

/** Error de sintaxis (con columna) o de evaluación de una expresión. */
public class ExprException extends RuntimeException {
    private final int column;

    public ExprException(String message, int column) {
        super(message);
        this.column = column;
    }

    public ExprException(String message) {
        this(message, -1);
    }

    /** Columna (1-based) donde está el error, o -1 si es de evaluación. */
    public int column() {
        return column;
    }

    @Override
    public String getMessage() {
        return column > 0 ? super.getMessage() + " (columna " + column + ")" : super.getMessage();
    }
}
