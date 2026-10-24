package dev.leo.omnievents.action;

import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Destinatarios de una acción: self, target, all, world:w, radius:n, nearby:n, player:nombre o una expresión. */
public final class TargetSpec {
    private enum Kind { SELF, TARGET, ALL, WORLD, RADIUS, NEARBY, PLAYER, EXPR }

    private final Kind kind;
    private final Template arg;
    private final Expression expr;
    private final String source;

    private TargetSpec(Kind kind, Template arg, Expression expr, String source) {
        this.kind = kind;
        this.arg = arg;
        this.expr = expr;
        this.source = source;
    }

    /** Analiza el valor de 'to'. Lanza IllegalArgumentException con mensaje legible. */
    public static TargetSpec parse(String raw) {
        String s = raw.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        String head = colon < 0 ? lower : lower.substring(0, colon).trim();
        String rest = colon < 0 ? "" : s.substring(colon + 1).trim();
        return switch (head) {
            case "self", "player" -> colon < 0 ? new TargetSpec(Kind.SELF, null, null, s) : new TargetSpec(Kind.PLAYER, Template.compile(rest), null, s);
            case "target" -> new TargetSpec(Kind.TARGET, null, null, s);
            case "all" -> new TargetSpec(Kind.ALL, null, null, s);
            case "world" -> new TargetSpec(Kind.WORLD, colon < 0 ? null : Template.compile(rest), null, s);
            case "radius", "range" -> new TargetSpec(Kind.RADIUS, require(rest, head), null, s);
            case "nearby" -> new TargetSpec(Kind.NEARBY, require(rest, head), null, s);
            default -> new TargetSpec(Kind.EXPR, null, Expression.compile(s), s);
        };
    }

    private static Template require(String rest, String head) {
        if (rest.isEmpty()) throw new IllegalArgumentException("'" + head + ":' necesita un número de bloques, p. ej. " + head + ":10");
        return Template.compile(rest);
    }

    public List<Entity> resolve(ExecContext ctx) {
        List<Entity> out = new ArrayList<>();
        switch (kind) {
            case SELF -> {
                if (ctx.subject() instanceof Entity e) out.add(e);
            }
            case TARGET -> {
                Object t = Values.raw(ctx.resolve("target"));
                if (t instanceof Entity e) out.add(e);
            }
            case ALL -> out.addAll(Bukkit.getOnlinePlayers());
            case WORLD -> {
                World w = arg == null ? ctx.world() : Bukkit.getWorld(arg.render(ctx, Template.Mode.RAW));
                if (w != null) out.addAll(w.getPlayers());
            }
            case RADIUS, NEARBY -> {
                Location l = ctx.location();
                double r = Values.num(arg.render(ctx, Template.Mode.RAW));
                if (l != null && l.getWorld() != null) {
                    for (Entity e : l.getWorld().getNearbyEntities(l, r, r, r)) {
                        if (e.getLocation().distanceSquared(l) > r * r) continue;
                        if (kind == Kind.RADIUS ? e instanceof Player : e instanceof org.bukkit.entity.LivingEntity) out.add(e);
                    }
                }
            }
            case PLAYER -> {
                Player p = Bukkit.getPlayerExact(arg.render(ctx, Template.Mode.RAW));
                if (p != null) out.add(p);
            }
            case EXPR -> {
                for (Object o : Values.list(expr.eval(ctx))) {
                    Object r = Values.raw(o);
                    if (r instanceof Entity e) out.add(e);
                    else if (r != null) {
                        Player p = Bukkit.getPlayerExact(Values.str(r));
                        if (p != null) out.add(p);
                    }
                }
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return source;
    }
}
