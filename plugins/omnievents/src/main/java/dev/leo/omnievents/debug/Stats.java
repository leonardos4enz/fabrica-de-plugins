package dev.leo.omnievents.debug;

import java.util.concurrent.atomic.AtomicLong;

/** Estadísticas de una regla (H9 CA3). Atómicas: el chat asíncrono también cuenta. */
public final class Stats {
    private final AtomicLong fired = new AtomicLong();
    private final AtomicLong passed = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicLong maxNanos = new AtomicLong();

    public void fired() {
        fired.incrementAndGet();
    }

    public void passed() {
        passed.incrementAndGet();
    }

    public void error() {
        errors.incrementAndGet();
    }

    public void time(long n) {
        nanos.addAndGet(n);
        maxNanos.accumulateAndGet(n, Math::max);
    }

    public long firedCount() {
        return fired.get();
    }

    public long passedCount() {
        return passed.get();
    }

    public long errorCount() {
        return errors.get();
    }

    public double avgMillis() {
        long f = fired.get();
        return f == 0 ? 0 : nanos.get() / 1e6 / f;
    }

    public double maxMillis() {
        return maxNanos.get() / 1e6;
    }

    public double totalMillis() {
        return nanos.get() / 1e6;
    }
}
