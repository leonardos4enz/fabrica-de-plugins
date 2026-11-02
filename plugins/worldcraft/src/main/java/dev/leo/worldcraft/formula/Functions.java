package dev.leo.worldcraft.formula;

import dev.leo.worldcraft.formula.Formula.Run;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Las funciones de las fórmulas (spec 034 H1 CA2), con los nombres de WorldEdit. rotate y swap las arma el analizador. */
final class Functions {
    enum Fn {
        ABS(1), ACOS(1), ASIN(1), ATAN(1), ATAN2(2), CBRT(1), CEIL(1), COS(1), COSH(1), EXP(1), FLOOR(1), LN(1), LOG(1),
        LOG10(1), MAX(1, 64), MIN(1, 64), RINT(1), ROUND(1), SIN(1), SINH(1), SQRT(1), TAN(1), TANH(1), MOD(2),
        RANDOM(0), RANDINT(1), PERLIN(7), RIDGEDMULTI(6), VORONOI(5), MEGABUF(1, 2);

        final int min, max;

        Fn(int n) {
            this(n, n);
        }

        Fn(int min, int max) {
            this.min = min;
            this.max = max;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Lo de WorldEdit que no está (plan 034 §1.1), con por qué. */
    static String missing(String name) {
        return switch (name) {
            case "query", "queryrel", "queryabs" -> name + "() is not supported: Worldcraft formulas don't read blocks (use a mask, like '=y<64 and stone')";
            case "gmegabuf" -> "gmegabuf() is not supported: use megabuf() (it keeps its values in one command)";
            case "closest" -> "closest() is not supported";
            default -> null;
        };
    }

    static Fn find(String name) {
        for (Fn f : Fn.values()) if (f.id().equals(name)) return f;
        return null;
    }

    static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Fn f : Fn.values()) out.add(f.id());
        out.add("rotate");
        out.add("swap");
        return out;
    }

    private Functions() {
    }

    static double apply(Fn fn, double[] a, Run r) {
        return switch (fn) {
            case ABS -> Math.abs(a[0]);
            case ACOS -> Math.acos(a[0]);
            case ASIN -> Math.asin(a[0]);
            case ATAN -> Math.atan(a[0]);
            case ATAN2 -> Math.atan2(a[0], a[1]);
            case CBRT -> Math.cbrt(a[0]);
            case CEIL -> Math.ceil(a[0]);
            case COS -> Math.cos(a[0]);
            case COSH -> Math.cosh(a[0]);
            case EXP -> Math.exp(a[0]);
            case FLOOR -> Math.floor(a[0]);
            case LN, LOG -> Math.log(a[0]);
            case LOG10 -> Math.log10(a[0]);
            case MAX -> {
                double m = a[0];
                for (int i = 1; i < a.length; i++) m = Math.max(m, a[i]);
                yield m;
            }
            case MIN -> {
                double m = a[0];
                for (int i = 1; i < a.length; i++) m = Math.min(m, a[i]);
                yield m;
            }
            case RINT -> Math.rint(a[0]);
            case ROUND -> (double) Math.round(a[0]);
            case SIN -> Math.sin(a[0]);
            case SINH -> Math.sinh(a[0]);
            case SQRT -> Math.sqrt(a[0]);
            case TAN -> Math.tan(a[0]);
            case TANH -> Math.tanh(a[0]);
            case MOD -> {
                double m = a[0] % a[1];
                yield m != 0 && (m < 0) != (a[1] < 0) ? m + a[1] : m;
            }
            case RANDOM -> r.random.nextDouble();
            case RANDINT -> a[0] >= 1 ? Math.floor(r.random.nextDouble() * Math.floor(a[0])) : 0;
            case PERLIN -> r.noise((long) a[0]).perlin(a[1], a[2], a[3], a[4], (int) a[5], a[6]);
            case RIDGEDMULTI -> r.noise((long) a[0]).ridged(a[1], a[2], a[3], a[4], (int) a[5]);
            case VORONOI -> r.noise((long) a[0]).voronoi(a[1], a[2], a[3], a[4]);
            case MEGABUF -> a.length == 1 ? r.megabuf(a[0]) : r.megabuf(a[0], a[1]);
        };
    }
}
