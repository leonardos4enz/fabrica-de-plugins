package dev.leo.omnievents.debug;

import dev.leo.omnievents.data.ObjectData;
import dev.leo.omnievents.expr.EvalContext;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;
import dev.leo.omnievents.text.TextFormat;
import dev.leo.omnievents.util.Durations;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Casos que /omni selftest corre en el servidor vivo (compuerta 2 de VERIFY) y que las unitarias también corren.
 * Cada caso de regresión encontrado en una revisión exploratoria se agrega aquí.
 */
public final class SelfTest {
    public record Result(String name, boolean ok, String detail) {
    }

    private SelfTest() {
    }

    private static final EvalContext CTX = new EvalContext() {
        private final Map<String, Object> roots = Map.of("n", 3.0, "texto", "Hola Mundo", "lista", List.of("a", "b"));

        @Override
        public Object resolve(String name) {
            return roots.get(name);
        }
    };

    public static List<Result> run() {
        List<Result> out = new ArrayList<>();
        expr(out, "precedencia", "true || false && false", true);
        expr(out, "aritmética", "2 + 3 * 4 - 1", 13.0);
        expr(out, "paréntesis", "(2 + 3) * 4", 20.0);
        expr(out, "ternario", "n > 2 ? 'si' : 'no'", "si");
        expr(out, "texto", "texto contains 'Mundo' && texto startsWith 'Hola'", true);
        expr(out, "in", "'b' in lista && 'z' not in lista", true);
        expr(out, "nulos", "nada < 5 || nada > 5", false);
        expr(out, "ignorar mayúsculas", "texto ~= 'hola mundo'", true);
        expr(out, "funciones", "max(1, n, 2) + round(2.567, 1)", 5.6);
        check(out, "duraciones", Durations.parseMillis("1h30m") == 5_400_000 && Durations.parseMillis("20t") == 1000, "1h30m y 20t");
        String escaped = Template.compile("<green>{texto + '<red>'}").render(new EvalContext() {
            @Override
            public Object resolve(String name) {
                return "<click:run_command:/op yo>x";
            }
        }, Template.Mode.MESSAGE);
        check(out, "escape de lo interpolado (H4 CA6)", !TextFormat.plain(escaped).equals("x") && TextFormat.plain(escaped).contains("<click"), escaped);
        String cmd = Template.compile("say {texto}").render(new EvalContext() {
            @Override
            public Object resolve(String name) {
                return "hola\nop yo§c";
            }
        }, Template.Mode.COMMAND);
        check(out, "saneo de comandos (H4 CA6)", !cmd.contains("\n") && !cmd.contains("§"), cmd);
        return out;
    }

    /**
     * Variables de objetos sobre el PDC real (spec 009): un ítem en memoria y el bloque más bajo del spawn, que se
     * deja como estaba. Solo en el servidor vivo (las unitarias no tienen PDC).
     */
    public static List<Result> objectData(ObjectData od) {
        List<Result> out = new ArrayList<>();
        try {
            ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
            od.set(item, "dueño", "Leo", 0);
            od.set(item, "usos", 3, 0);
            od.set(item, "ligada", true, System.currentTimeMillis() + 60_000);
            Double ttl = od.ttl(item, "ligada");
            boolean ok = "Leo".equals(od.get(item, "dueño")) && Values.looseEquals(od.get(item, "usos"), 3.0)
                    && Boolean.TRUE.equals(od.get(item, "ligada")) && ttl != null && ttl > 50 && Values.looseEquals(od.ttl(item, "dueño"), -1.0);
            od.set(item, "dueño", null, 0);
            check(out, "variables de un ítem (spec 009)", ok && od.get(item, "dueño") == null && od.entries(item).size() == 2, od.entries(item).toString());

            World w = Bukkit.getWorlds().get(0);
            Block b = w.getBlockAt(w.getSpawnLocation().getBlockX(), w.getMinHeight(), w.getSpawnLocation().getBlockZ());
            if (b.getType().isAir()) {
                check(out, "variables de un bloque (spec 009)", true, "omitido: el bloque más bajo del spawn es aire");
            } else {
                od.set(b, "selftest", 1, 0);
                boolean read = Values.looseEquals(od.get(b, "selftest"), 1.0);
                ObjectData.clearBlock(b);
                check(out, "variables de un bloque (spec 009)", read && od.get(b, "selftest") == null, "en el chunk de " + b.getX() + "," + b.getY() + "," + b.getZ());
            }
        } catch (RuntimeException e) {
            check(out, "variables de objetos (spec 009)", false, e.toString());
        }
        return out;
    }

    private static void expr(List<Result> out, String name, String src, Object expected) {
        try {
            Object v = Expression.compile(src).eval(CTX);
            boolean ok = Values.looseEquals(v, expected);
            out.add(new Result(name, ok, src + " = " + Values.str(v) + (ok ? "" : " (esperado " + Values.str(expected) + ")")));
        } catch (RuntimeException e) {
            out.add(new Result(name, false, src + ": " + e.getMessage()));
        }
    }

    private static void check(List<Result> out, String name, boolean ok, String detail) {
        out.add(new Result(name, ok, detail));
    }
}
