package dev.leo.worldcraft.formula;

import dev.leo.worldcraft.formula.Formula.Run;

/**
 * El árbol de una fórmula ya resuelta (plan 034 §1.1): las variables son posiciones de {@link Run#slots}. Verdadero es
 * mayor que 0; comparaciones y lógicos dan 1 o 0. break, continue y return son una bandera en el Run.
 */
abstract class Node {
    abstract double eval(Run r);

    static boolean truth(double v) {
        return v > 0;
    }

    static double bool(boolean b) {
        return b ? 1 : 0;
    }

    static final class Num extends Node {
        final double v;

        Num(double v) {
            this.v = v;
        }

        double eval(Run r) {
            return v;
        }
    }

    static final class Var extends Node {
        final int slot;

        Var(int slot) {
            this.slot = slot;
        }

        double eval(Run r) {
            return r.slots[slot];
        }
    }

    /** = += -= *= /= %= ^= (op es el operador sin el =; vacío para =). */
    static final class Assign extends Node {
        final int slot;
        final char op;
        final Node value;

        Assign(int slot, char op, Node value) {
            this.slot = slot;
            this.op = op;
            this.value = value;
        }

        double eval(Run r) {
            double v = value.eval(r), old = r.slots[slot];
            double out = switch (op) {
                case '+' -> old + v;
                case '-' -> old - v;
                case '*' -> old * v;
                case '/' -> old / v;
                case '%' -> old % v;
                case '^' -> Math.pow(old, v);
                default -> v;
            };
            r.slots[slot] = out;
            return out;
        }
    }

    /** ++x, x++, --x, x--. */
    static final class Step extends Node {
        final int slot;
        final double delta;
        final boolean prefix;

        Step(int slot, double delta, boolean prefix) {
            this.slot = slot;
            this.delta = delta;
            this.prefix = prefix;
        }

        double eval(Run r) {
            double old = r.slots[slot];
            r.slots[slot] = old + delta;
            return prefix ? old + delta : old;
        }
    }

    static final class Unary extends Node {
        final char op;
        final Node a;

        Unary(char op, Node a) {
            this.op = op;
            this.a = a;
        }

        double eval(Run r) {
            double v = a.eval(r);
            return switch (op) {
                case '-' -> -v;
                case '!' -> bool(!truth(v));
                case '~' -> ~(long) v;
                default -> v;
            };
        }
    }

    static final class Factorial extends Node {
        final Node a;

        Factorial(Node a) {
            this.a = a;
        }

        double eval(Run r) {
            double v = Math.floor(a.eval(r));
            if (v < 0 || Double.isNaN(v)) return Double.NaN;
            if (v > 170) return Double.POSITIVE_INFINITY;
            double f = 1;
            for (int i = 2; i <= v; i++) f *= i;
            return f;
        }
    }

    static final class Binary extends Node {
        static final int ADD = 0, SUB = 1, MUL = 2, DIV = 3, MOD = 4, POW = 5, LT = 6, LE = 7, GT = 8, GE = 9, EQ = 10, NE = 11,
                NEAR = 12, SHL = 13, SHR = 14;
        final int op;
        final Node a, b;

        Binary(int op, Node a, Node b) {
            this.op = op;
            this.a = a;
            this.b = b;
        }

        double eval(Run r) {
            double x = a.eval(r), y = b.eval(r);
            return switch (op) {
                case ADD -> x + y;
                case SUB -> x - y;
                case MUL -> x * y;
                case DIV -> x / y;
                case MOD -> x % y;
                case POW -> Math.pow(x, y);
                case LT -> bool(x < y);
                case LE -> bool(x <= y);
                case GT -> bool(x > y);
                case GE -> bool(x >= y);
                case EQ -> bool(x == y);
                case NE -> bool(x != y);
                case NEAR -> bool(Math.abs(x - y) <= 1e-7 * Math.max(1, Math.max(Math.abs(x), Math.abs(y))));
                case SHL -> (double) ((long) x << (long) y);
                default -> (double) ((long) x >> (long) y);
            };
        }
    }

    static final class And extends Node {
        final Node a, b;

        And(Node a, Node b) {
            this.a = a;
            this.b = b;
        }

        double eval(Run r) {
            return bool(truth(a.eval(r)) && truth(b.eval(r)));
        }
    }

    static final class Or extends Node {
        final Node a, b;

        Or(Node a, Node b) {
            this.a = a;
            this.b = b;
        }

        double eval(Run r) {
            return bool(truth(a.eval(r)) || truth(b.eval(r)));
        }
    }

    static final class Cond extends Node {
        final Node c, a, b;

        Cond(Node c, Node a, Node b) {
            this.c = c;
            this.a = a;
            this.b = b;
        }

        double eval(Run r) {
            return truth(c.eval(r)) ? a.eval(r) : b.eval(r);
        }
    }

    /** Una función matemática de la tabla. */
    static final class Call extends Node {
        final Functions.Fn fn;
        final Node[] args;

        Call(Functions.Fn fn, Node[] args) {
            this.fn = fn;
            this.args = args;
        }

        double eval(Run r) {
            double[] v = new double[args.length];
            for (int i = 0; i < v.length; i++) v[i] = args[i].eval(r);
            return Functions.apply(fn, v, r);
        }
    }

    /** rotate(x, y, ángulo): gira el par de variables; swap(x, y): las cambia. */
    static final class Pair extends Node {
        final int a, b;
        final Node angle;

        Pair(int a, int b, Node angle) {
            this.a = a;
            this.b = b;
            this.angle = angle;
        }

        double eval(Run r) {
            double x = r.slots[a], y = r.slots[b];
            if (angle == null) {
                r.slots[a] = y;
                r.slots[b] = x;
                return 0;
            }
            double t = angle.eval(r), c = Math.cos(t), s = Math.sin(t);
            r.slots[a] = x * c - y * s;
            r.slots[b] = x * s + y * c;
            return 0;
        }
    }

    static final class Seq extends Node {
        final Node[] items;

        Seq(Node[] items) {
            this.items = items;
        }

        double eval(Run r) {
            double v = 0;
            for (Node n : items) {
                v = n.eval(r);
                if (r.flow != Run.NONE) return v;
            }
            return v;
        }
    }

    static final class If extends Node {
        final Node c, a, b;

        If(Node c, Node a, Node b) {
            this.c = c;
            this.a = a;
            this.b = b;
        }

        double eval(Run r) {
            if (truth(c.eval(r))) return a.eval(r);
            return b == null ? 0 : b.eval(r);
        }
    }

    /** while, do … while y for (a; b; c): el cuerpo y, si hay, la condición antes o después y el paso. */
    static final class Loop extends Node {
        final Node init, cond, inc, body;
        final boolean condAfter;

        Loop(Node init, Node cond, Node inc, Node body, boolean condAfter) {
            this.init = init;
            this.cond = cond;
            this.inc = inc;
            this.body = body;
            this.condAfter = condAfter;
        }

        double eval(Run r) {
            double v = 0;
            if (init != null) init.eval(r);
            while (true) {
                if (!condAfter && cond != null && !truth(cond.eval(r))) break;
                r.step();
                v = body.eval(r);
                if (r.flow == Run.BREAK) {
                    r.flow = Run.NONE;
                    break;
                }
                if (r.flow == Run.RETURN) return v;
                if (r.flow == Run.CONTINUE) r.flow = Run.NONE;
                if (inc != null) inc.eval(r);
                if (condAfter && !truth(cond.eval(r))) break;
            }
            return v;
        }
    }

    /** for (i = a, b): i de a a b, de a 1. */
    static final class Range extends Node {
        final int slot;
        final Node from, to, body;

        Range(int slot, Node from, Node to, Node body) {
            this.slot = slot;
            this.from = from;
            this.to = to;
            this.body = body;
        }

        double eval(Run r) {
            double v = 0, end = to.eval(r);
            for (double i = from.eval(r); i <= end; i++) {
                r.slots[slot] = i;
                r.step();
                v = body.eval(r);
                if (r.flow == Run.BREAK) {
                    r.flow = Run.NONE;
                    break;
                }
                if (r.flow == Run.RETURN) return v;
                if (r.flow == Run.CONTINUE) r.flow = Run.NONE;
            }
            return v;
        }
    }

    static final class Jump extends Node {
        final int flow;
        final Node value;

        Jump(int flow, Node value) {
            this.flow = flow;
            this.value = value;
        }

        double eval(Run r) {
            double v = value == null ? 0 : value.eval(r);
            if (flow == Run.RETURN) r.ret = v;
            r.flow = flow;
            return v;
        }
    }
}
