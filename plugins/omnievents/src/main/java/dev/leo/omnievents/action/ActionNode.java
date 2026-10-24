package dev.leo.omnievents.action;

import dev.leo.omnievents.expr.Expression;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Una acción con sus modificadores: to, where, chance, when (H4 CA3). */
public record ActionNode(String type, String path, Action action, TargetSpec to, Expression where, double chance, Expression when) {

    public boolean asyncSafe() {
        return to == null && action.asyncSafe();
    }

    public long execute(ExecContext ctx, Execution exec) {
        if (when != null && !when.test(ctx)) {
            exec.trace("  " + path + " " + type + ": when falso → omitida");
            return 0;
        }
        if (chance < 100 && ThreadLocalRandom.current().nextDouble(100) >= chance) {
            exec.trace("  " + path + " " + type + ": chance " + chance + "% → omitida");
            return 0;
        }
        if (to == null) {
            exec.trace("  " + path + " " + type);
            return action.run(ctx, exec);
        }
        List<Entity> targets = to.resolve(ctx);
        Object source = ctx.subject();
        int n = 0;
        long wait = 0;
        for (Entity e : targets) {
            Map<String, Object> b = new HashMap<>();
            b.put("player", e);
            b.put("source", source);
            ExecContext child = ctx.child(b);
            if (where != null && !where.test(child)) continue;
            wait = Math.max(wait, action.run(child, exec));
            n++;
        }
        exec.trace("  " + path + " " + type + " → " + n + " destinatario(s)");
        return wait;
    }
}
