package dev.leo.omnievents.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Perfilador de /omni cpu (spec 006, H6). Apagado no mide nada; encendido mide 1 de cada N disparos: el total de la
 * regla y cada condición y acción. Cada regla cuenta sus disparos aparte: una regla frecuente no le quita muestras a
 * las demás. Atómico: las reglas de chat corren en otro hilo.
 */
public final class Profiler {
    /** Tiempo acumulado de algo medido (una regla, o una de sus partes). */
    public static final class Acc {
        private final LongAdder nanos = new LongAdder();
        private final LongAdder count = new LongAdder();

        void add(long n) {
            nanos.add(n);
            count.increment();
        }

        public long nanos() {
            return nanos.sum();
        }

        public long count() {
            return count.sum();
        }

        /** Promedio por medición, en microsegundos. */
        public double avgMicros() {
            long c = count();
            return c == 0 ? 0 : nanos() / 1e3 / c;
        }
    }

    /** Lo medido de una regla: su total y cada parte (conditions[0], actions[2].then[0]…). */
    public static final class RuleProfile {
        private final String trigger;
        private final boolean async;
        private final Acc total = new Acc();
        private final AtomicLong fired = new AtomicLong();
        private final Map<String, Acc> parts = new ConcurrentHashMap<>();

        RuleProfile(String trigger, boolean async) {
            this.trigger = trigger;
            this.async = async;
        }

        public String trigger() {
            return trigger;
        }

        public boolean async() {
            return async;
        }

        public Acc total() {
            return total;
        }

        /** Las partes, de la más cara a la más barata (por tiempo total). */
        public List<Map.Entry<String, Acc>> parts() {
            List<Map.Entry<String, Acc>> out = new ArrayList<>(parts.entrySet());
            out.sort(Comparator.comparingLong((Map.Entry<String, Acc> e) -> e.getValue().nanos()).reversed());
            return out;
        }
    }

    private volatile boolean enabled;
    private volatile int sampleEvery = 10;
    private volatile long startedNanos;
    private final Map<String, RuleProfile> rules = new ConcurrentHashMap<>();

    public boolean enabled() {
        return enabled;
    }

    public int sampleEvery() {
        return sampleEvery;
    }

    public void sampleEvery(int n) {
        sampleEvery = Math.max(1, n);
    }

    /** Enciende (y reinicia lo medido si estaba apagado). */
    public void start() {
        if (!enabled) reset();
        enabled = true;
    }

    public void stop() {
        enabled = false;
    }

    public void reset() {
        rules.clear();
        startedNanos = System.nanoTime();
    }

    /** ¿Se mide este disparo de la regla? Apagado cuesta una lectura volatile. */
    public boolean sample(String rule, String trigger, boolean async) {
        return enabled && profile(rule, trigger, async).fired.incrementAndGet() % sampleEvery == 0;
    }

    /** Nanosegundos desde que se encendió o reinició. */
    public long elapsedNanos() {
        return System.nanoTime() - startedNanos;
    }

    private RuleProfile profile(String rule, String trigger, boolean async) {
        return rules.computeIfAbsent(rule, k -> new RuleProfile(trigger, async));
    }

    public void recordRule(String rule, String trigger, boolean async, long nanos) {
        profile(rule, trigger, async).total.add(nanos);
    }

    public void recordPart(String rule, String trigger, boolean async, String part, long nanos) {
        profile(rule, trigger, async).parts.computeIfAbsent(part, k -> new Acc()).add(nanos);
    }

    /** Las reglas con al menos un disparo medido. */
    public Map<String, RuleProfile> rules() {
        Map<String, RuleProfile> out = new java.util.HashMap<>();
        rules.forEach((k, v) -> {
            if (v.total.count() > 0) out.put(k, v);
        });
        return out;
    }

    /**
     * Porcentaje estimado del hilo principal: lo medido por N (el muestreo) sobre el tiempo transcurrido. Con 20 TPS es
     * el % de cada tick de 50 ms. Las reglas asíncronas no gastan el hilo principal y no cuentan.
     */
    public double mainThreadPercent(long sampledNanos) {
        long el = elapsedNanos();
        return el <= 0 ? 0 : sampledNanos * (double) sampleEvery / el * 100;
    }
}
