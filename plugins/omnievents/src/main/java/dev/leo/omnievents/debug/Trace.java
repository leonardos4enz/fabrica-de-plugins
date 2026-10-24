package dev.leo.omnievents.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Traza de un disparo para /omni debug. Solo se crea si alguien está depurando la regla. */
public final class Trace {
    private final String rule;
    private final List<String> lines = Collections.synchronizedList(new ArrayList<>());
    private boolean flushed;

    public Trace(String rule) {
        this.rule = rule;
    }

    public String rule() {
        return rule;
    }

    public void add(String line) {
        lines.add(line);
    }

    public List<String> lines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    /** true la primera vez: una traza se envía una sola vez. */
    public synchronized boolean markFlushed() {
        if (flushed) return false;
        flushed = true;
        return true;
    }
}
