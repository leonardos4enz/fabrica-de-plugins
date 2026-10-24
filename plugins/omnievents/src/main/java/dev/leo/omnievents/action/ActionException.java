package dev.leo.omnievents.action;

/** Error al ejecutar una acción: se registra con regla y acción, y la ejecución sigue (H9 CA5). */
public class ActionException extends RuntimeException {
    public ActionException(String message) {
        super(message);
    }
}
