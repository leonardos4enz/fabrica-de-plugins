package dev.leo.omnievents.action;

/** Una acción compilada. Devuelve 0 para seguir, o los ticks que la ejecución debe esperar. */
@FunctionalInterface
public interface Action {
    long run(ExecContext ctx, Execution exec);

    /** ¿Puede correr en el hilo de un evento asíncrono? (plan 001 §3) */
    default boolean asyncSafe() {
        return false;
    }

    /** Envuelve una acción marcándola como segura en asíncrono. */
    static Action safe(Action a) {
        return new Action() {
            @Override
            public long run(ExecContext ctx, Execution exec) {
                return a.run(ctx, exec);
            }

            @Override
            public boolean asyncSafe() {
                return true;
            }
        };
    }

    /** Acción que no espera: ejecuta y sigue. */
    static Action of(java.util.function.Consumer<ExecContext> body) {
        return (ctx, exec) -> {
            body.accept(ctx);
            return 0;
        };
    }
}
