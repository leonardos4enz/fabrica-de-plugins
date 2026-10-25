package dev.leo.omnievents.action;

import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;
import dev.leo.omnievents.text.TextFormat;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Una acción con sus modificadores: to, where, chance, when (H4 CA3). {@code control} marca las acciones que solo
 * dirigen la ejecución (if, bucles, random, delay, stop, call): son las únicas que corren en una simulación (spec
 * 006, H4); las demás se listan con {@code source}, su texto en el YAML. En una prueba (spec 010) también corren
 * {@link #SANDBOXED} sobre el Sandbox; las demás se registran con {@code preview}, el valor de texto ya armado.
 */
public record ActionNode(String type, String path, Action action, TargetSpec to, Expression where, double chance, Expression when,
                         boolean control, String source, Template preview) {
    /** Acciones que en una prueba corren de verdad, porque solo tocan el Sandbox. */
    public static final Set<String> SANDBOXED = Set.of("set", "unset", "cancel", "give_money", "take_money");
    /** Acciones cuyo texto es un comando (se arma como comando, sin formato que quitar). */
    public static final Set<String> COMMANDS = Set.of("console", "command");

    ActionNode withTarget(TargetSpec forced) {
        return new ActionNode(type, path, action, forced, where, chance, when, control, source, preview);
    }

    public boolean asyncSafe() {
        return to == null && action.asyncSafe();
    }

    public long execute(ExecContext ctx, Execution exec) {
        if (when != null && !when.test(ctx)) {
            exec.trace("  " + path + " " + type + ": when falso → omitida");
            return 0;
        }
        boolean sim = exec.simulating();
        boolean test = exec.testing();
        if (chance < 100 && test) exec.trace("  " + path + " " + type + ": chance " + Values.formatNumber(chance) + "% → en la prueba corre");
        if (chance < 100 && !sim && !test && ThreadLocalRandom.current().nextDouble(100) >= chance) {
            exec.trace("  " + path + " " + type + ": chance " + chance + "% → omitida");
            return 0;
        }
        if (sim && !control) return simulate(ctx, exec);
        if (test && (to != null || !control && !SANDBOXED.contains(type))) return record(ctx, exec);
        if (to == null) {
            exec.trace("  " + path + " " + type);
            return action.run(ctx, exec);
        }
        List<Entity> targets = to.resolve(ctx);
        Object source = ctx.subject();
        int n = 0;
        long wait = 0;
        for (Entity e : targets) {
            ExecContext child = target(ctx, e, source);
            if (where != null && !where.test(child)) continue;
            wait = Math.max(wait, action.run(child, exec));
            n++;
        }
        exec.trace("  " + path + " " + type + " → " + n + " destinatario(s)");
        return wait;
    }

    private static ExecContext target(ExecContext ctx, Entity e, Object source) {
        Map<String, Object> b = new HashMap<>();
        b.put("player", e);
        b.put("source", source);
        return ctx.child(b);
    }

    /** En una prueba (spec 010): registra la acción con su texto armado, sin correrla ni resolver a quién iría. */
    private long record(ExecContext ctx, Execution exec) {
        String text;
        if (preview == null) text = source;
        else if (COMMANDS.contains(type)) text = preview.render(ctx, Template.Mode.COMMAND);
        else text = TextFormat.plain(preview.render(ctx, Template.Mode.MESSAGE));
        if (to != null) text += " → " + to;
        exec.sandbox().effect(type, path, text);
        exec.trace("  • " + path + " " + type + ": " + text);
        return 0;
    }

    /** En una simulación: lista la acción (y a quién iría) sin correrla. */
    private long simulate(ExecContext ctx, Execution exec) {
        String note = chance < 100 ? " (chance " + Values.formatNumber(chance) + "%: no se sortea)" : "";
        if (to == null) {
            exec.trace("  • " + path + " " + source + note);
            return 0;
        }
        List<String> names = new ArrayList<>();
        Object src = ctx.subject();
        for (Entity e : to.resolve(ctx)) {
            if (where != null && !where.test(target(ctx, e, src))) continue;
            names.add(e.getName());
        }
        String shown = names.size() <= 5 ? String.join(", ", names) : String.join(", ", names.subList(0, 5)) + "…";
        exec.trace("  • " + path + " " + source + note + " → " + names.size() + " destinatario(s)" + (names.isEmpty() ? "" : ": " + shown));
        return 0;
    }
}
