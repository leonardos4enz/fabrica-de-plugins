package dev.leo.omnievents.debug;

/**
 * Promedio reciente del tiempo de una regla para su budget (spec 006, H7). Ignora los primeros disparos (la JVM aún
 * no compiló el código y carga clases) y luego usa un promedio móvil exponencial, que olvida lo viejo.
 */
public final class Budget {
    static final int WARMUP = 10;
    static final int MIN_FIRES = 20;
    static final double ALPHA = 0.2;

    private long fires;
    private double avgNanos;
    private long lastWarnMs;
    private boolean warned;

    /**
     * Suma un disparo. Devuelve true si toca avisar: van al menos {@link #MIN_FIRES} disparos, el promedio reciente pasa
     * el presupuesto y pasó {@code warnEveryMs} desde el último aviso.
     */
    public synchronized boolean record(long nanos, long budgetNanos, long nowMs, long warnEveryMs) {
        fires++;
        if (fires <= WARMUP) return false;
        avgNanos = fires == WARMUP + 1 ? nanos : avgNanos + ALPHA * (nanos - avgNanos);
        if (fires < MIN_FIRES || avgNanos <= budgetNanos) return false;
        if (warned && nowMs - lastWarnMs < warnEveryMs) return false;
        warned = true;
        lastWarnMs = nowMs;
        return true;
    }

    /** Promedio reciente en milisegundos (0 mientras calienta). */
    public synchronized double recentMillis() {
        return fires <= WARMUP ? 0 : avgNanos / 1e6;
    }

    public synchronized long fires() {
        return fires;
    }

    public synchronized boolean warmingUp() {
        return fires < MIN_FIRES;
    }
}
