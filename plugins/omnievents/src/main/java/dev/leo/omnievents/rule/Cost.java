package dev.leo.omnievents.rule;

import dev.leo.omnievents.action.ActionException;
import dev.leo.omnievents.action.Purse;
import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.ExprException;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;
import dev.leo.omnievents.util.Suggest;
import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El costo de una regla (spec 012, H2; plan §1.3): partes compiladas al cargar que se cotizan una vez y se cobran todo o
 * nada. Si un cobro falla después de otros, lo cobrado se devuelve en orden inverso.
 */
public final class Cost {
    public enum Kind { MONEY, LEVELS, EXP, ITEMS, VAR }

    /** Claves fijas de cost; además pvar.x y gvar.x. */
    public static final List<String> KEYS = List.of("money", "levels", "exp", "items");
    private static final Pattern VAR = Pattern.compile("^(pvar|gvar)\\.([A-Za-z0-9_\\-]+)$");

    /** Una parte: amount es la cantidad (en items, la del ítem); material solo en items; scope y var solo en VAR. */
    public record Part(String key, Kind kind, Expression amount, Template material, Template itemAmount, Scope scope, String var) {
        /** ¿Necesita jugador? Todas menos gvar. */
        public boolean needsPlayer() {
            return !(kind == Kind.VAR && scope == Scope.GLOBAL);
        }
    }

    /** Una parte cotizada: lo que pide (ya evaluado) y lo que hay. */
    public record Line(Part part, String material, double amount, double have) {
        public boolean ok() {
            return have >= amount;
        }

        /** Nombre corto para cost_missing_list y /omni check: money, items, pvar.fichas. */
        public String key() {
            return part.key;
        }
    }

    /** La cotización; noEconomy si pide dinero y no hay economía. */
    public record Quote(List<Line> lines, boolean noEconomy) {
        public List<Line> missing() {
            return lines.stream().filter(l -> !l.ok()).toList();
        }

        public boolean ok() {
            return !noEconomy && missing().isEmpty();
        }
    }

    private final List<Part> parts;

    private Cost(List<Part> parts) {
        this.parts = List.copyOf(parts);
    }

    public List<Part> parts() {
        return parts;
    }

    public boolean usesMoney() {
        return parts.stream().anyMatch(p -> p.kind == Kind.MONEY);
    }

    public boolean needsPlayer() {
        return parts.stream().anyMatch(Part::needsPlayer);
    }

    // ------------------------------------------------------------------ carga

    /** Compila cost; los errores van con la ruta regla.cost.clave (H2 CA4). Devuelve null si no hay partes válidas. */
    public static Cost parse(Object raw, String path, Problems problems) {
        Map<?, ?> top = raw instanceof Map<?, ?> mm ? mm : raw instanceof org.bukkit.configuration.ConfigurationSection s ? s.getValues(false) : null;
        if (top == null) {
            problems.error(path, "cost es un mapa: money, levels, exp, items, pvar.x o gvar.x con su cantidad (p. ej. cost: { money: 500 })");
            return null;
        }
        // Bukkit lee «pvar.fichas: 1» como una sección pvar con fichas dentro: se vuelve a juntar.
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        top.forEach((k, v) -> {
            Map<?, ?> inner = v instanceof Map<?, ?> im ? im : v instanceof org.bukkit.configuration.ConfigurationSection is ? is.getValues(false) : null;
            if (inner != null && (k.equals("pvar") || k.equals("gvar"))) inner.forEach((ik, iv) -> m.put(k + "." + ik, iv));
            else m.put(String.valueOf(k), v);
        });
        List<Part> out = new ArrayList<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey()).trim();
            String p = path + "." + key;
            Object v = e.getValue();
            try {
                Matcher vm = VAR.matcher(key);
                switch (key) {
                    case "money" -> out.add(new Part(key, Kind.MONEY, amount(v, p, false, problems), null, null, null, null));
                    case "levels" -> out.add(new Part(key, Kind.LEVELS, amount(v, p, true, problems), null, null, null, null));
                    case "exp" -> out.add(new Part(key, Kind.EXP, amount(v, p, true, problems), null, null, null, null));
                    case "items" -> {
                        List<?> list = v instanceof List<?> l ? l : List.of(v == null ? "" : v);
                        for (int i = 0; i < list.size(); i++) {
                            Part it = item(Values.str(list.get(i)), list.size() > 1 || v instanceof List<?> ? p + "[" + i + "]" : p, problems);
                            if (it != null) out.add(it);
                        }
                    }
                    default -> {
                        if (vm.matches()) {
                            Scope sc = vm.group(1).equals("pvar") ? Scope.PLAYER : Scope.GLOBAL;
                            out.add(new Part(key, Kind.VAR, amount(v, p, false, problems), null, null, sc, vm.group(2)));
                        } else {
                            List<String> known = new ArrayList<>(KEYS);
                            known.add("pvar.");
                            known.add("gvar.");
                            problems.error(p, "clave desconocida en cost" + Suggest.hint(key, known) + " (money, levels, exp, items, pvar.x o gvar.x)");
                        }
                    }
                }
            } catch (ExprException ex) {
                problems.error(p, ex.getMessage());
            }
        }
        return out.isEmpty() ? null : new Cost(out);
    }

    private static Expression amount(Object v, String path, boolean integer, Problems problems) {
        if (v == null) throw new ExprException("falta la cantidad");
        Expression e = Expression.compile(Values.str(v));
        Double c = Values.toNumberOrNull(v);
        if (c != null) {
            if (c <= 0) problems.error(path, "la cantidad debe ser mayor que 0 (dio " + Values.formatNumber(c) + ")");
            else if (integer && c != Math.floor(c)) problems.error(path, "la cantidad debe ser un número entero");
        }
        return e;
    }

    private static Part item(String text, String path, Problems problems) {
        String[] parts = text.split(";", 2);
        Template mat = Template.compile(parts[0].trim());
        Template amt = Template.compile(parts.length > 1 ? parts[1].trim() : "1");
        if (mat.isConstant()) {
            if (mat.source().isEmpty() || Material.matchMaterial(mat.source()) == null) {
                problems.error(path, "material desconocido '" + mat.source() + "' (usa MATERIAL;cantidad, p. ej. \"DIAMOND;3\")");
                return null;
            }
        }
        if (amt.isConstant()) {
            Double c = Values.toNumberOrNull(amt.source());
            if (c == null || c <= 0 || c != Math.floor(c)) {
                problems.error(path, "la cantidad de " + mat.source() + " debe ser un entero mayor que 0 (dio '" + amt.source() + "')");
                return null;
            }
        }
        return new Part("items", Kind.ITEMS, null, mat, amt, null, null);
    }

    // ------------------------------------------------------------------ cotizar y cobrar

    /** Evalúa cada cantidad una vez y mira lo que hay (plan §1.3, paso 1). */
    public Quote quote(EvalContext ctx, Purse purse) {
        List<Line> lines = new ArrayList<>();
        boolean noEco = false;
        for (Part p : parts) {
            double n;
            String mat = null;
            if (p.kind == Kind.ITEMS) {
                mat = p.material.render(ctx, Template.Mode.RAW).trim();
                Material m = Material.matchMaterial(mat);
                if (m == null) throw new ActionException("cost.items: material desconocido '" + mat + "'");
                mat = m.name();
                n = Values.num(p.itemAmount.render(ctx, Template.Mode.RAW));
            } else {
                n = Values.num(p.amount.eval(ctx));
            }
            if (!(n > 0)) throw new ActionException("cost." + p.key + " dio " + Values.formatNumber(n) + ": la cantidad debe ser mayor que 0");
            if (p.kind != Kind.MONEY && p.kind != Kind.VAR) n = Math.ceil(n);
            double have = switch (p.kind) {
                case MONEY -> {
                    Double b = purse.money();
                    if (b == null) noEco = true;
                    yield b == null ? 0 : b;
                }
                case LEVELS -> purse.levels();
                case EXP -> purse.exp();
                case ITEMS -> purse.count(mat);
                case VAR -> {
                    Object cur = purse.var(p.scope, p.var);
                    yield cur == null ? 0 : Values.num(cur);
                }
            };
            lines.add(new Line(p, mat, n, have));
        }
        return new Quote(List.copyOf(lines), noEco);
    }

    /**
     * Cobra todo lo cotizado (plan §1.3, paso 3). Devuelve null si cobró todo, o la parte que falló (lo ya cobrado se
     * devolvió en orden inverso; una devolución que falla se informa con onRefundError).
     */
    public Line charge(Quote q, Purse purse, BiConsumer<Line, String> onRefundError) {
        Deque<Runnable> refunds = new ArrayDeque<>();
        Deque<Line> charged = new ArrayDeque<>();
        for (Line l : q.lines()) {
            Runnable refund = take(l, purse, onRefundError);
            if (refund == null) {
                while (!refunds.isEmpty()) {
                    Line done = charged.pop();
                    try {
                        refunds.pop().run();
                    } catch (RuntimeException e) {
                        onRefundError.accept(done, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    }
                }
                return l;
            }
            refunds.push(refund);
            charged.push(l);
        }
        return null;
    }

    /** Cobra una línea; devuelve cómo devolverla, o null si no pudo cobrar. */
    private static Runnable take(Line l, Purse purse, BiConsumer<Line, String> onRefundError) {
        int n = (int) l.amount;
        switch (l.part.kind) {
            case MONEY -> {
                Double b = purse.money();
                if (b == null || b < l.amount || !purse.takeMoney(l.amount)) return null;
                return () -> {
                    if (!purse.giveMoney(l.amount)) onRefundError.accept(l, "la economía rechazó el depósito");
                };
            }
            case LEVELS -> {
                int cur = purse.levels();
                if (cur < n) return null;
                purse.setLevels(cur - n);
                return () -> purse.setLevels(purse.levels() + n);
            }
            case EXP -> {
                int cur = purse.exp();
                if (cur < n) return null;
                purse.setExp(cur - n);
                return () -> purse.setExp(purse.exp() + n);
            }
            case ITEMS -> {
                Object taken = purse.takeItems(l.material, n);
                if (taken == null) return null;
                return () -> purse.giveBack(taken);
            }
            case VAR -> {
                Object prev = purse.var(l.part.scope, l.part.var);
                double cur = prev == null ? 0 : Values.num(prev);
                if (cur < l.amount) return null;
                purse.setVar(l.part.scope, l.part.var, cur - l.amount);
                return () -> purse.setVar(l.part.scope, l.part.var, prev);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ textos

    /** var.cost_missing: «200 de dinero, 2 × DIAMOND» (H2 CA2). */
    public static String missingText(Quote q, Purse purse) {
        List<String> out = new ArrayList<>();
        if (q.noEconomy()) out.add("dinero (no hay economía)");
        for (Line l : q.missing()) {
            if (l.part.kind == Kind.MONEY && q.noEconomy()) continue;
            out.add(describe(l, l.amount - l.have, purse));
        }
        return String.join(", ", out);
    }

    public static List<Object> missingKeys(Quote q) {
        List<Object> out = new ArrayList<>();
        if (q.noEconomy()) out.add("money");
        for (Line l : q.missing()) if (!out.contains(l.key())) out.add(l.key());
        return out;
    }

    /** Una cantidad de una parte en palabras: «$200.00 de dinero», «3 niveles», «2 × DIAMOND», «1 de pvar.fichas». */
    public static String describe(Line l, double n, Purse purse) {
        String num = Values.formatNumber(Values.round(n, 2));
        return switch (l.part.kind) {
            case MONEY -> (purse == null ? num : purse.formatMoney(n)) + " de dinero";
            case LEVELS -> num + (n == 1 ? " nivel" : " niveles");
            case EXP -> num + " de experiencia";
            case ITEMS -> num + " × " + l.material;
            case VAR -> num + " de " + l.part.key;
        };
    }

    /** Línea de /omni check: «✔ money: 800 / 500». */
    public static String checkLine(Line l) {
        String what = l.part.kind == Kind.ITEMS ? "items " + l.material : l.key();
        return (l.ok() ? "✔ " : "✘ ") + "costo " + what + ": " + Values.formatNumber(Values.round(l.have, 2)) + " / " + Values.formatNumber(Values.round(l.amount, 2));
    }

    @Override
    public String toString() {
        return parts.stream().map(p -> p.key).toList().toString().toLowerCase(Locale.ROOT);
    }
}
