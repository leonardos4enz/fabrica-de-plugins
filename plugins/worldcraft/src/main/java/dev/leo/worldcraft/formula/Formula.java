package dev.leo.worldcraft.formula;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Una fórmula de Worldcraft (spec 034, ADR 0006): solo números, con la sintaxis de WorldEdit. Se lee una vez
 * ({@link #parse}) y se evalúa muchas con un {@link Run} (uno por hilo). Java puro.
 *
 * <pre>
 * x^2 + y^2 + z^2 &lt; 1
 * y -= 0.2
 * r = sqrt(x^2 + z^2); for (i = 0; i &lt; 3; i++) { r *= 0.9 }; r &lt; 0.5
 * </pre>
 */
public final class Formula {
    /**
     * Los topes (plan 034 §2).
     *
     * @param maxSteps  vueltas de bucle en una evaluación
     * @param maxLength caracteres de una fórmula
     * @param megabuf   celdas de megabuf()
     */
    public record Limits(int maxSteps, int maxLength, int megabuf) {
        public static final Limits DEFAULTS = new Limits(100_000, 1000, 65_536);
    }

    /** Se cortó una evaluación (plan 034 §1.1): sin traza, se lanza mucho. */
    public abstract static sealed class Stop extends RuntimeException {
        Stop() {
            super(null, null, false, false);
        }
    }

    /** Un bucle pasó de {@link Limits#maxSteps} vueltas en una evaluación. */
    public static final class TooManySteps extends Stop {
    }

    /** El cálculo pasó del plazo de /wc timeout. */
    public static final class TimedOut extends Stop {
    }

    private final String text;
    private final Node root;
    private final int slots;
    private final List<String> inputs;
    private final Limits limits;
    private final ThreadLocal<Run> local;

    Formula(String text, Node root, int slots, List<String> inputs, Limits limits) {
        this.text = text;
        this.root = root;
        this.slots = slots;
        this.inputs = List.copyOf(inputs);
        this.limits = limits;
        this.local = ThreadLocal.withInitial(() -> run(0));
    }

    /**
     * Lee una fórmula.
     *
     * @param inputs los nombres que da quien evalúa (x, y, z), en ese orden; se pueden cambiar (deform los lee después)
     */
    public static Formula parse(String text, List<String> inputs, Limits limits) throws FormulaException {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) throw new FormulaException("Missing formula: write one, like 'x^2+y^2+z^2<1'");
        if (t.length() > limits.maxLength()) {
            throw new FormulaException("The formula is too long: " + t.length() + " characters, at most " + limits.maxLength()
                    + " (formulas.max_length)");
        }
        return FormulaParser.parse(t, inputs, limits);
    }

    public String text() {
        return text;
    }

    public List<String> inputs() {
        return inputs;
    }

    /** Una evaluación nueva (variables en 0, megabuf vacío) con esa semilla para random(). */
    public Run run(long seed) {
        return new Run(this, seed);
    }

    /** La evaluación de este hilo (máscaras y patrones: muchas obras, cada hilo la suya). */
    public Run local() {
        return local.get();
    }

    @Override
    public String toString() {
        return text;
    }

    /** El estado de una evaluación: variables, megabuf, azar, vueltas y plazo. No es para varios hilos. */
    public static final class Run {
        static final int NONE = 0, BREAK = 1, CONTINUE = 2, RETURN = 3;

        private final Formula formula;
        final double[] slots;
        final SplittableRandom random;
        final int maxSteps, megabufMax;
        double[] megabuf = new double[0];
        long deadline = Long.MAX_VALUE;
        int steps;
        int flow;
        double ret;
        /** El ruido de la última semilla (casi siempre la misma). */
        long noiseSeed = Long.MIN_VALUE;
        Noise3 noise;

        Run(Formula f, long seed) {
            this.formula = f;
            this.slots = new double[f.slots];
            this.random = new SplittableRandom(seed);
            this.maxSteps = f.limits.maxSteps();
            this.megabufMax = f.limits.megabuf();
        }

        /** Hasta cuándo (System.nanoTime) puede calcular; Long.MAX_VALUE = sin plazo. */
        public Run deadline(long nanos) {
            this.deadline = nanos;
            return this;
        }

        public double eval() {
            if (deadline != Long.MAX_VALUE && System.nanoTime() > deadline) throw new TimedOut();
            steps = 0;
            flow = NONE;
            double v = formula.root.eval(this);
            if (flow == RETURN) v = ret;
            flow = NONE;
            return v;
        }

        public double eval(double x, double y, double z) {
            slots[0] = x;
            slots[1] = y;
            slots[2] = z;
            return eval();
        }

        /** El valor de una entrada después de evaluar (deform: dónde quedó x, y, z). */
        public double input(int i) {
            return slots[i];
        }

        public void input(int i, double v) {
            slots[i] = v;
        }

        /** Una vuelta de bucle: cuenta y, cada 1024, mira el plazo. */
        void step() {
            if (++steps > maxSteps) throw new TooManySteps();
            if ((steps & 1023) == 0 && deadline != Long.MAX_VALUE && System.nanoTime() > deadline) throw new TimedOut();
        }

        Noise3 noise(long seed) {
            if (noise == null || seed != noiseSeed) {
                noise = new Noise3(seed);
                noiseSeed = seed;
            }
            return noise;
        }

        double megabuf(double index) {
            if (!(index >= 0) || index >= megabuf.length) return 0;
            return megabuf[(int) index];
        }

        double megabuf(double index, double value) {
            if (!(index >= 0) || index >= megabufMax) return value;
            int i = (int) index;
            if (i >= megabuf.length) megabuf = java.util.Arrays.copyOf(megabuf, Math.min(megabufMax, Math.max(i + 1, megabuf.length * 2 + 16)));
            megabuf[i] = value;
            return value;
        }
    }
}
