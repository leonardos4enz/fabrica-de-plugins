package dev.leo.omnievents.action;

import dev.leo.omnievents.OmniEvents;
import dev.leo.omnievents.data.ObjectData;
import dev.leo.omnievents.expr.Expression;
import dev.leo.omnievents.expr.Properties;
import dev.leo.omnievents.expr.Scope;
import dev.leo.omnievents.expr.Values;
import dev.leo.omnievents.text.Template;
import dev.leo.omnievents.util.Durations;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Catálogo de acciones (plan 001 §2.5). Otros plugins agregan las suyas con {@link #register}. */
public final class Actions {
    @FunctionalInterface
    public interface Factory {
        Action create(Args a);
    }

    /** Forma del valor de una acción en el esquema (spec 007). */
    public enum Shape {
        /** Texto, número, booleano o nada. */
        SCALAR,
        /** Texto o un mapa de ítem (valueKeys). */
        ITEM,
        /** Texto o un mapa con rule y args (valueKeys). */
        CALL,
        /** Lista de ramas (valueKeys de cada rama). */
        BRANCHES
    }

    /**
     * Documentación de una acción, puesta al registrarla (spec 007): de aquí salen el esquema JSON y la referencia.
     *
     * @param value     forma del valor en palabras ({@code MATERIAL;cantidad})
     * @param keys      claves hermanas que lee la acción ({@code then}, {@code do}, {@code as}…)
     * @param valueKeys claves del valor cuando es un mapa (ítem, call) o de cada rama (random)
     * @param example   YAML de la acción, que carga sin problemas (lo comprueba una prueba)
     */
    public record Doc(String group, String description, String value, Shape shape, Map<String, String> keys,
                      Map<String, String> valueKeys, String example) {
        public Doc keys(String... pairs) {
            Map<String, String> k = new LinkedHashMap<>(keys);
            for (int i = 0; i < pairs.length; i += 2) k.put(pairs[i], pairs[i + 1]);
            return new Doc(group, description, value, shape, k, valueKeys, example);
        }

        public Doc shape(Shape s, String... pairs) {
            Map<String, String> k = new LinkedHashMap<>();
            for (int i = 0; i < pairs.length; i += 2) k.put(pairs[i], pairs[i + 1]);
            return new Doc(group, description, value, s, keys, k, example);
        }

        Doc group(String g) {
            return new Doc(g, description, value, shape, keys, valueKeys, example);
        }
    }

    /** Lo que tiene una acción registrada por otro plugin sin documentación propia (spec 007, H3 CA3). */
    public static final Doc EXTERNAL = new Doc("otros plugins", "Acción registrada por otro plugin.", "texto", Shape.SCALAR,
            Map.of(), Map.of(), "");

    /** Claves de un ítem en forma de mapa (give_item, drop_item y los ítems de un menú, spec 013). */
    public static final String[] ITEM_KEYS = {
            "material", "Material (DIAMOND, IRON_SWORD…).", "id", "Sinónimo de material.", "amount", "Cantidad.",
            "name", "Nombre (MiniMessage, & y {expresiones}).", "lore", "Líneas de descripción: una lista, o un texto con |.",
            "model", "custom_model_data.", "enchants", "Encantamientos: mapa encantamiento: nivel, o lista \"SHARPNESS:2\".",
            "glow", "Brillo de encantado sin encantamientos.", "unbreakable", "Irrompible.", "hide_flags", "Oculta los atributos.",
            "saved", "Ítem guardado con /omni item save.",
            "flags", "Banderas (spec 015): lista o texto con comas de HIDE_ENCHANTS, HIDE_ATTRIBUTES…",
            "durability", "Daño del ítem (durabilidad gastada, como en ConditionalEvents).",
            "item_model", "Modelo del ítem: clave espacio:modelo (1.21.4+).",
            "skull_owner", "Para PLAYER_HEAD: nombre del jugador de la cabeza.",
            "skull_texture", "Para PLAYER_HEAD: textura en base64."};

    /** Claves de give_item: las del ítem más la casilla (spec 015). */
    public static final String[] GIVE_KEYS = concat(ITEM_KEYS,
            "slot", "Casilla donde ponerlo: 0 a 40 o HAND, OFF_HAND, HELMET, CHESTPLATE, LEGGINGS, BOOTS. Si está ocupada, va al inventario (salvo slot_replace).",
            "slot_replace", "true: reemplaza lo que haya en la casilla.");

    private static String[] concat(String[] a, String... b) {
        String[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private record Entry(Factory factory, boolean flow, Doc doc) {
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final OmniEvents plugin;
    private String group = "";

    public Actions(OmniEvents plugin) {
        this.plugin = plugin;
        group = "flujo";
        registerFlow();
        group = "mensajes";
        registerMessages();
        group = "comandos";
        registerCommands();
        group = "jugador";
        registerPlayer();
        group = "mundo";
        registerWorld();
        group = "evento";
        registerEvent();
    }

    private FireworkGuard fireworkGuard;

    /** El que quita el daño a los cohetes de firework (spec 014); se crea con el primero. */
    public FireworkGuard fireworkGuard() {
        if (fireworkGuard == null) fireworkGuard = new FireworkGuard(plugin);
        return fireworkGuard;
    }

    /** El plugin (null en las unitarias). */
    public OmniEvents plugin() {
        return plugin;
    }

    public void register(String name, Factory f) {
        register(name, EXTERNAL, f);
    }

    /** Registra una acción con su documentación (aparece así en /omni schema). */
    public void register(String name, Doc doc, Factory f) {
        entries.put(name.toLowerCase(Locale.ROOT), new Entry(f, false, doc.group().isEmpty() ? doc.group(group) : doc));
    }

    private void flow(String name, Doc doc, Factory f) {
        entries.put(name, new Entry(f, true, doc.group(group)));
    }

    private static Doc doc(String description, String value, String example) {
        return new Doc("", description, value, Shape.SCALAR, Map.of(), Map.of(), example);
    }

    public Doc doc(String name) {
        Entry e = entries.get(name);
        return e == null ? null : e.doc();
    }

    public boolean has(String name) {
        return entries.containsKey(name);
    }

    public boolean isFlow(String name) {
        Entry e = entries.get(name);
        return e != null && e.flow();
    }

    public Set<String> names() {
        return new TreeSet<>(entries.keySet());
    }

    Action create(String name, Args a) {
        return entries.get(name).factory().create(a);
    }

    // ------------------------------------------------------------------ control de flujo

    private static final Pattern ASSIGN = Pattern.compile("^\\s*(var|pvar|gvar)\\.([A-Za-z0-9_\\-]+)\\s*(=|\\+=|-=|\\*=|/=)\\s*(.+)$", Pattern.DOTALL);
    private static final Pattern VARNAME = Pattern.compile("^\\s*(var|pvar|gvar)\\.([A-Za-z0-9_\\-]+)\\s*$");
    // Spec 009: objeto.data.nombre; el primer «.data.» seguido de un nombre (y del operador) separa el objeto.
    private static final Pattern DATA_ASSIGN = Pattern.compile("^\\s*(.+?)\\.data\\.([\\p{L}0-9_]+)\\s*(=|\\+=|-=|\\*=|/=)\\s*(.+)$", Pattern.DOTALL);
    private static final Pattern DATA_NAME = Pattern.compile("^\\s*(.+?)\\.data\\.([\\p{L}0-9_]+)\\s*$", Pattern.DOTALL);

    /** Valor de set con +=, -=, *= o /= sobre el valor actual. */
    private static Object combine(String op, Object cur, Object v) {
        double c = cur == null ? 0 : Values.num(cur), x = Values.num(v);
        return switch (op) {
            case "+=" -> cur instanceof String s && !Values.isNumeric(s) ? s + Values.str(v) : (Object) (c + x);
            case "-=" -> c - x;
            case "*=" -> c * x;
            default -> {
                if (x == 0) throw new ActionException("división entre cero en set");
                yield c / x;
            }
        };
    }

    /** La clave expire de set (spec 009, H1 CA1–CA2): fija se valida al cargar; en var es error. */
    private static Template expire(Args a, Scope scope) {
        Object raw = a.extra("expire");
        if (raw == null) return null;
        if (scope == Scope.LOCAL) throw new IllegalArgumentException("expire no sirve en var (vive solo durante la ejecución); usa pvar, gvar u objeto.data");
        Template t = Template.compile(Values.str(raw));
        if (t.isConstant() && Durations.parseMillis(t.source()) <= 0) throw new IllegalArgumentException("expire debe ser una duración positiva (10s, 5m, 1h)");
        return t;
    }

    private static long expireMillis(Template t, ExecContext ctx) {
        long ms;
        try {
            ms = Durations.parseMillis(t.isConstant() ? t.source() : t.render(ctx, Template.Mode.RAW));
        } catch (IllegalArgumentException e) {
            throw new ActionException("expire: " + e.getMessage());
        }
        if (ms <= 0) throw new ActionException("expire debe ser una duración positiva y dio " + ms + "ms");
        return ms;
    }

    private static Object dataTarget(Expression object, ExecContext ctx) {
        Object t = Values.raw(object.eval(ctx));
        if (!ctx.isDataTarget(t)) throw new ActionException(object.source() + " no es un bloque, una entidad ni un ítem (es " + Values.typeName(t) + "): no guarda variables");
        return t;
    }

    private void registerFlow() {
        flow("if", doc("Si la expresión se cumple corre then; si no, else.", "expresión", "if: \"player.level >= 10\"\nthen:\n  - message: \"<green>Nivel alto\"\nelse:\n  - message: \"<gray>Sube de nivel\"").keys("then", "Acciones si se cumple.", "else", "Acciones si no se cumple."), a -> {
            Expression cond = a.expr();
            List<ActionNode> then = a.nested("then");
            List<ActionNode> otherwise = a.nested("else");
            if (then.isEmpty() && otherwise.isEmpty()) a.parser().problems().warn(a.path(), "'if' sin 'then' ni 'else' no hace nada");
            return Action.safe((ctx, exec) -> {
                boolean ok = cond.test(ctx);
                exec.trace("    if " + (exec.traceObj() != null ? cond.explain(ctx) : ""));
                exec.pushBlock(ok ? then : otherwise, ctx);
                return 0;
            });
        });
        flow("repeat", doc("Repite do N veces; la vuelta (desde 0) está en var.i, o en var.<as>.", "expresión (veces)", "repeat: 3\nas: vuelta\ndo:\n  - message: \"Vuelta {var.vuelta}\"").keys("do", "Acciones de cada vuelta.", "as", "Nombre de la variable con el número de vuelta (i por defecto)."), a -> {
            Expression times = a.expr();
            List<ActionNode> body = a.nested("do");
            String as = a.extra("as") == null ? "i" : Values.str(a.extra("as"));
            return Action.safe((ctx, exec) -> {
                int n = (int) Values.num(times.eval(ctx));
                exec.push(exec.loop(body, i -> {
                    if (i >= n) return null;
                    ctx.locals().put(as, (double) i);
                    return ctx;
                }));
                return 0;
            });
        });
        flow("while", doc("Repite do mientras la expresión se cumpla, con tope de iteraciones (config.yml).", "expresión", "while: \"default(var.n, 0) < 3\"\ndo:\n  - set: \"var.n += 1\"").keys("do", "Acciones de cada vuelta."), a -> {
            Expression cond = a.expr();
            List<ActionNode> body = a.nested("do");
            return Action.safe((ctx, exec) -> {
                exec.push(exec.loop(body, i -> cond.test(ctx) ? ctx : null));
                return 0;
            });
        });
        flow("for_each", doc("Corre do para cada destinatario; dentro, player es cada uno y source el original (con as, el destinatario queda en ese nombre). var.index es la vuelta.", "destinatarios (all, world:<mundo>, radius:<n>, nearby:<n> o una expresión)", "for_each: \"radius:10\"\nas: cercano\ndo:\n  - message: \"{cercano.name} está cerca\"").keys("do", "Acciones para cada destinatario.", "as", "Nombre de la raíz con el destinatario (sin as: player y source)."), a -> {
            TargetSpec spec = TargetSpec.parse(a.text());
            List<ActionNode> body = a.nested("do");
            String as = a.extra("as") == null ? null : Values.str(a.extra("as"));
            if (as != null) a.parser().declare(as);
            return (ctx, exec) -> {
                if (ctx.sandbox() != null) {
                    // Spec 010: una prueba no tiene jugadores ni entidades de verdad a los que recorrer.
                    exec.trace("    for_each " + spec + ": en la prueba no da vueltas (no hay destinatarios reales)");
                    return 0;
                }
                List<Entity> items = spec.resolve(ctx);
                Object source = ctx.subject();
                exec.push(exec.loop(body, i -> {
                    if (i >= items.size()) return null;
                    Map<String, Object> b = new HashMap<>();
                    if (as != null) b.put(as, items.get(i));
                    else {
                        b.put("player", items.get(i));
                        b.put("source", source);
                    }
                    ctx.locals().put("index", (double) i);
                    return ctx.child(b);
                }));
                return 0;
            };
        });
        flow("random", doc("Elige una rama al azar según su weight (1 por defecto) y corre su do.", "lista de ramas con weight y do", "random:\n  - weight: 3\n    do:\n      - message: \"Común\"\n  - weight: 1\n    do:\n      - message: \"Rara\"").shape(Shape.BRANCHES, "weight", "Peso de la rama (1 por defecto).", "do", "Acciones de la rama."), a -> {
            if (!(a.value() instanceof List<?> list) || list.isEmpty()) throw new IllegalArgumentException("'random' necesita una lista de ramas con weight y do");
            List<Double> weights = new ArrayList<>();
            List<List<ActionNode>> branches = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof Map<?, ?> m)) throw new IllegalArgumentException("cada rama de 'random' es un mapa con weight y do");
                Object w = m.get("weight");
                weights.add(w == null ? 1.0 : Values.num(w));
                branches.add(a.parser().parseList(m.get("do"), a.path() + ".random[" + i + "].do"));
            }
            double total = weights.stream().mapToDouble(Double::doubleValue).sum();
            return Action.safe((ctx, exec) -> {
                // Spec 010: en una prueba, con la semilla del caso (reproducible).
                double r = ctx.sandbox() != null ? ctx.sandbox().random().nextDouble(total) : ThreadLocalRandom.current().nextDouble(total);
                for (int i = 0; i < branches.size(); i++) {
                    r -= weights.get(i);
                    if (r < 0) {
                        exec.trace("    random → rama " + i);
                        exec.pushBlock(branches.get(i), ctx);
                        break;
                    }
                }
                return 0;
            });
        });
        flow("delay", doc("Espera antes de seguir con las acciones siguientes.", "duración (10s, 5m, 20t)", "delay: 2s"), a -> {
            Template t = a.template();
            if (t.isConstant()) {
                long ticks = Durations.toTicks(Durations.parseMillis(t.source()));
                return (ctx, exec) -> Math.max(1, ticks);
            }
            return (ctx, exec) -> Math.max(1, Durations.toTicks(Durations.parseMillis(t.render(ctx, Template.Mode.RAW))));
        });
        flow("wait_ticks", doc("Espera N ticks (20 = 1 segundo).", "expresión (ticks)", "wait_ticks: 40"), a -> {
            Expression e = a.expr();
            return (ctx, exec) -> Math.max(1, (long) Values.num(e.eval(ctx)));
        });
        flow("stop", doc("Detiene las acciones que faltan de esta ejecución.", "(sin valor)", "stop"), a -> Action.safe((ctx, exec) -> {
            exec.stop();
            return 0;
        }));
        flow("no_cooldown", doc("Esta ejecución no cuenta para el enfriamiento ni para el límite.", "(sin valor)", "no_cooldown"), a -> Action.safe((ctx, exec) -> {
            exec.cancelUsage();
            return 0;
        }));
        register("set", doc("Asigna una variable: var (de la ejecución), pvar (del jugador, persistente), gvar (global, persistente) u objeto.data.nombre (de un bloque, entidad o ítem, guardada en el objeto); también +=, -=, *= y /=.", "ámbito.nombre = expresión, u objeto.data.nombre = expresión", "set: \"pvar.racha += 1\"\nexpire: 10m").keys("expire", "Duración tras la que la variable se borra sola (10m, 1h; spec 009). Cada set la vuelve a contar; un set sin expire la quita."), a -> {
            Matcher m = ASSIGN.matcher(a.text());
            Matcher d = m.matches() ? null : DATA_ASSIGN.matcher(a.text());
            if (d != null && !d.matches()) throw new IllegalArgumentException("'set' se escribe como 'pvar.nombre = expresión' u 'objeto.data.nombre = expresión' (también +=, -=, *=, /=); ámbitos: var, pvar, gvar");
            Scope scope = d == null ? Scope.byPrefix(m.group(1)) : null;
            Expression object = d == null ? null : Expression.compile(d.group(1));
            String name = d == null ? m.group(2) : d.group(2), op = d == null ? m.group(3) : d.group(3);
            Expression rhs = Expression.compile(d == null ? m.group(4) : d.group(4));
            Template expire = expire(a, scope);
            if (object != null) a.parser().markObjectData();
            return Action.safe((ctx, exec) -> {
                Object v = rhs.eval(ctx);
                long expireAt = expire == null ? 0 : ctx.now() + expireMillis(expire, ctx);
                if (object != null) {
                    Object target = dataTarget(object, ctx);
                    if (!op.equals("=")) v = combine(op, ctx.objectGet(target, name), v);
                    ctx.objectSet(target, name, v, expireAt);
                    exec.trace("    " + object.source() + ".data." + name + " = " + Values.str(v));
                    return 0;
                }
                if (!op.equals("=")) v = combine(op, ctx.getVar(scope, name), v);
                ctx.setVar(scope, name, v, expireAt);
                exec.trace("    " + scope.prefix + "." + name + " = " + Values.str(v));
                return 0;
            });
        });
        register("unset", doc("Borra una variable (y su caducidad).", "ámbito.nombre u objeto.data.nombre", "unset: \"pvar.monedas\""), a -> {
            Matcher m = VARNAME.matcher(a.text());
            if (m.matches()) {
                Scope scope = Scope.byPrefix(m.group(1));
                String name = m.group(2);
                return Action.safe(Action.of(ctx -> ctx.setVar(scope, name, null)));
            }
            Matcher d = DATA_NAME.matcher(a.text());
            if (!d.matches()) throw new IllegalArgumentException("'unset' recibe una variable como pvar.nombre u objeto.data.nombre");
            Expression object = Expression.compile(d.group(1));
            String name = d.group(2);
            a.parser().markObjectData();
            return Action.safe(Action.of(ctx -> ctx.objectSet(dataTarget(object, ctx), name, null, 0)));
        });
        register("call", doc("Corre otra regla (trigger: call) con argumentos, que llegan como var.<nombre>.", "regla; nombre=expresión… o un mapa con rule y args", "call: \"sumar_monedas; jugador=player.name; cantidad=10\"").shape(Shape.CALL, "rule", "Regla que se llama.", "args", "Mapa nombre: expresión con los argumentos."), a -> {
            Template rule;
            Map<String, Expression> params = new LinkedHashMap<>();
            if (a.isMap()) {
                rule = Template.compile(Values.str(a.map().get("rule")));
                if (a.map().get("args") instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) params.put(String.valueOf(e.getKey()), Expression.compile(Values.str(e.getValue())));
                }
            } else {
                List<String> parts = Args.split(a.text(), ';');
                rule = Template.compile(parts.get(0).trim());
                for (int i = 1; i < parts.size(); i++) {
                    String p = parts.get(i).trim();
                    if (p.isEmpty()) continue;
                    int eq = p.indexOf('=');
                    if (eq < 0) throw new IllegalArgumentException("argumento de call sin '=': '" + p + "' (usa nombre=expresión)");
                    String key = p.substring(0, eq).trim().replaceFirst("^%(.*)%$", "$1").replaceFirst("^var\\.", "");
                    params.put(key, Expression.compile(p.substring(eq + 1).trim()));
                }
            }
            return (ctx, exec) -> {
                Map<String, Object> vals = new HashMap<>();
                for (Map.Entry<String, Expression> e : params.entrySet()) vals.put(e.getKey(), e.getValue().eval(ctx));
                String target = rule.render(ctx, Template.Mode.RAW);
                if (ctx.sandbox() != null) ctx.sandbox().call(target, ctx, vals, exec.depth() + 1, exec.traceObj());
                else plugin.rules().call(target, ctx, vals, exec.depth() + 1, exec.traceObj());
                return 0;
            };
        });
        register("reset_cooldown", doc("Reinicia el enfriamiento de una regla para el jugador (o el global, sin jugador).", "regla", "reset_cooldown: bienvenida"), a -> {
            Template rule = a.template();
            return Action.of(ctx -> {
                Player p = ctx.playerOrNull();
                plugin.rules().resetCooldown(rule.render(ctx, Template.Mode.RAW), p == null ? null : p.getUniqueId());
            });
        });
    }

    // ------------------------------------------------------------------ mensajes

    private void registerMessages() {
        register("message", doc("Envía un mensaje al jugador (sin jugador, a quien ejecutó o a la consola). Acepta MiniMessage, & y {expresiones}.", "texto", "message: \"<green>Hola, {player.name}\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> {
                Object s = ctx.subject();
                if (s instanceof Player p) p.sendMessage(t.component(ctx));
                else if (ctx.sender() != null) ctx.sender().sendMessage(t.component(ctx));
                else Bukkit.getConsoleSender().sendMessage(t.component(ctx));
            }));
        });
        register("centered_message", doc("Como message, con cada línea centrada en el chat (spec 014; la fuente por defecto de Minecraft, negritas incluidas). \\n o <newline> separan líneas.", "texto", "centered_message: \"<gold><b>EVENTO</b>\\n<gray>Empieza en 5 minutos\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> {
                Object s = ctx.subject();
                Component msg = t.component(ctx);
                if (s instanceof Player p) {
                    for (Component line : dev.leo.omnievents.text.Centered.lines(msg)) p.sendMessage(line);
                } else {
                    (ctx.sender() != null ? ctx.sender() : Bukkit.getConsoleSender()).sendMessage(msg);
                }
            }));
        });
        register("json_message", doc("Envía un mensaje escrito en JSON de texto de Minecraft (clic, hover…), como json_message de ConditionalEvents (spec 015). Se convierte al cargar; los {expresiones} dentro de los textos se resuelven y escapan como en message.",
                "JSON de texto", "json_message: '{\"text\":\"Clic aquí\",\"color\":\"gold\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/spawn\"}}'"), a -> {
            Template t = jsonTemplate(a.text());
            return Action.safe(Action.of(ctx -> {
                Object s = ctx.subject();
                if (s instanceof Player p) p.sendMessage(t.component(ctx));
                else (ctx.sender() != null ? ctx.sender() : Bukkit.getConsoleSender()).sendMessage(t.component(ctx));
            }));
        });
        register("broadcast", doc("Envía un mensaje a todo el servidor.", "texto", "broadcast: \"<gold>{player.name} encontró un tesoro\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> Bukkit.getServer().broadcast(t.component(ctx))));
        });
        register("console_message", doc("Escribe un mensaje en la consola.", "texto", "console_message: \"La regla corrió para {player.name}\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> Bukkit.getConsoleSender().sendMessage(t.component(ctx))));
        });
        register("log", doc("Escribe en el log del servidor, con el nombre de la regla.", "texto", "log: \"{player.name} usó la regla\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> plugin.getLogger().info("[" + (ctx.rule() == null ? "?" : ctx.rule().name()) + "] " + t.render(ctx, Template.Mode.COMMAND))));
        });
        register("actionbar", doc("Muestra un texto en la barra de acción; con duración (texto;ticks, como ConditionalEvents, o la clave duration) se reenvía cada 2 s hasta cubrirla (spec 016).",
                "texto[;ticks]", "actionbar: \"<gold>Evento en 1 minuto\"\nduration: 6s").keys("duration", "Cuánto se mantiene: ticks (120) o con unidad (6s)."), a -> {
            String text = a.text();
            String duration = a.extra("duration") == null ? null : Values.str(a.extra("duration"));
            java.util.regex.Matcher m = ACTIONBAR_TICKS.matcher(text);
            if (duration == null && m.matches()) {
                text = m.group(1);
                duration = m.group(2).trim();
            }
            Template t = Template.compile(text);
            Template d = duration == null ? null : Template.compile(duration);
            if (d != null && d.isConstant()) {
                try {
                    actionbarTicks(d.source());
                } catch (ActionException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
            return Action.of(ctx -> {
                Player p = ctx.requirePlayer();
                Component c = t.component(ctx);
                p.sendActionBar(c);
                long ticks = d == null ? 0 : actionbarTicks(d.render(ctx, Template.Mode.RAW));
                if (ticks <= ACTIONBAR_EVERY) return;
                long[] sent = {0};
                Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                    sent[0] += ACTIONBAR_EVERY;
                    if (sent[0] >= ticks || !p.isOnline()) {
                        task.cancel();
                        return;
                    }
                    p.sendActionBar(c);
                }, ACTIONBAR_EVERY, ACTIONBAR_EVERY);
            });
        });
        register("title", doc("Muestra un título y un subtítulo (none deja uno vacío); los tiempos van en ticks.", "título;subtítulo;entrada;estancia;salida", "title: \"<gold>¡Victoria!;<gray>Ronda 3;10;70;20\""), a -> {
            List<Template> p = a.parts();
            return Action.of(ctx -> {
                Player pl = ctx.requirePlayer();
                Component title = part(p, 0) == null || isNone(p.get(0), ctx) ? Component.empty() : p.get(0).component(ctx);
                Component sub = part(p, 1) == null || isNone(p.get(1), ctx) ? Component.empty() : p.get(1).component(ctx);
                long in = ticks(p, 2, ctx, 10), stay = ticks(p, 3, ctx, 70), out = ticks(p, 4, ctx, 20);
                pl.showTitle(Title.title(title, sub, Title.Times.times(Duration.ofMillis(in * 50), Duration.ofMillis(stay * 50), Duration.ofMillis(out * 50))));
            });
        });
        register("bossbar", doc("Muestra una barra de jefe por unos segundos.", "texto;segundos;color;progreso (0 a 1)", "bossbar: \"<red>El Guardián;10;RED;0.65\""), a -> {
            List<Template> p = a.parts();
            return Action.of(ctx -> {
                Player pl = ctx.requirePlayer();
                double seconds = part(p, 1) == null ? 5 : Values.num(p.get(1).render(ctx, Template.Mode.RAW));
                BossBar.Color color = part(p, 2) == null ? BossBar.Color.PURPLE : enumOf(BossBar.Color.class, p.get(2).render(ctx, Template.Mode.RAW), "color de bossbar");
                float progress = part(p, 3) == null ? 1f : (float) Math.max(0, Math.min(1, Values.num(p.get(3).render(ctx, Template.Mode.RAW))));
                BossBar bar = BossBar.bossBar(p.get(0).component(ctx), progress, color, BossBar.Overlay.PROGRESS);
                pl.showBossBar(bar);
                Bukkit.getScheduler().runTaskLater(plugin, () -> pl.hideBossBar(bar), Math.max(1, Math.round(seconds * 20)));
            });
        });
        register("sound", doc("Reproduce un sonido al jugador (o en la ubicación); también acepta claves como minecraft:block.note_block.pling.", "SONIDO;volumen;tono", "sound: \"ENTITY_PLAYER_LEVELUP;1;1.2\"\nlocation: \"~;~;~\"")
                .keys("location", "Dónde suena: mundo;x;y;z (~ relativo; spec 015). Lo oyen los cercanos, no solo el jugador."), a -> {
            List<Template> p = a.parts();
            List<Template> locP = a.extra("location") == null ? null : new Args("location", a.extra("location"), Map.of(), a.path(), a.parser()).parts();
            return Action.of(ctx -> {
                String name = p.get(0).render(ctx, Template.Mode.RAW);
                float vol = part(p, 1) == null ? 1f : (float) Values.num(p.get(1).render(ctx, Template.Mode.RAW));
                float pitch = part(p, 2) == null ? 1f : (float) Values.num(p.get(2).render(ctx, Template.Mode.RAW));
                Player pl = locP != null ? null : ctx.playerOrNull();
                Location l = locP != null ? location(locP, 0, ctx, ctx.location(), true) : ctx.location();
                if (name.contains(".") || name.contains(":")) {
                    if (pl != null) pl.playSound(pl.getLocation(), name.toLowerCase(Locale.ROOT), vol, pitch);
                    else if (l != null) l.getWorld().playSound(l, name.toLowerCase(Locale.ROOT), vol, pitch);
                    return;
                }
                org.bukkit.Sound s = sound(name);
                if (pl != null) pl.playSound(pl.getLocation(), s, vol, pitch);
                else if (l != null) l.getWorld().playSound(l, s, vol, pitch);
            });
        });
        register("stop_sound", doc("Detiene un sonido del jugador, o todos (all o sin valor); también claves de paquete de recursos (mi_pack:musica, spec 015).", "SONIDO o all", "stop_sound: all"), a -> {
            Template t = a.template();
            return Action.of(ctx -> {
                Player pl = ctx.requirePlayer();
                String name = t.render(ctx, Template.Mode.RAW).trim();
                if (name.isEmpty() || name.equalsIgnoreCase("all")) pl.stopAllSounds();
                else if (name.contains(".") || name.contains(":")) pl.stopSound(name.toLowerCase(Locale.ROOT));
                else pl.stopSound(sound(name));
            });
        });
    }

    /**
     * JSON de texto → plantilla de MiniMessage (spec 015, H3 CA1). Se hace una vez al cargar; un JSON inválido es un
     * error de carga con la ruta.
     */
    public static Template jsonTemplate(String json) {
        Component c;
        try {
            c = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(json.trim());
        } catch (RuntimeException e) {
            String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException("json_message no es JSON de texto válido: " + why.lines().findFirst().orElse(why));
        }
        if (c == null) throw new IllegalArgumentException("json_message está vacío");
        // MiniMessage escapa los < del texto; las llaves quedan para las {expresiones}.
        return Template.compile(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(c));
    }

    @SuppressWarnings("deprecation")
    private static org.bukkit.Sound sound(String name) {
        try {
            return org.bukkit.Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ActionException("sonido desconocido '" + name + "'");
        }
    }

    private static boolean isNone(Template t, ExecContext ctx) {
        String s = t.render(ctx, Template.Mode.RAW);
        return s.isEmpty() || s.equalsIgnoreCase("none");
    }

    private static Template part(List<Template> p, int i) {
        return i < p.size() && !p.get(i).source().isEmpty() ? p.get(i) : null;
    }

    /** actionbar «texto;ticks» de ConditionalEvents: el último trozo es un número o una {expresión} (spec 016). */
    public static final Pattern ACTIONBAR_TICKS = Pattern.compile("(?s)^(.*);\\s*(\\d+|\\{[^{}]*})\\s*$");
    /** Cada cuánto se reenvía la barra de acción (el cliente la muestra unos 3 s). */
    static final long ACTIONBAR_EVERY = 40;

    /** Duración de actionbar en ticks: un número son ticks; con unidad (6s, 1m), Durations. */
    public static long actionbarTicks(String s) {
        String t = s.trim();
        if (Values.isNumeric(t)) return (long) Values.num(t);
        try {
            return Durations.toTicks(Durations.parseMillis(t));
        } catch (IllegalArgumentException e) {
            throw new ActionException("duración de actionbar inválida '" + s + "' (ticks, p. ej. 120, o con unidad: 6s)");
        }
    }

    private static long ticks(List<Template> p, int i, ExecContext ctx, long def) {
        return part(p, i) == null ? def : (long) Values.num(p.get(i).render(ctx, Template.Mode.RAW));
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, String name, String what) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ActionException(what + " desconocido: '" + name + "'");
        }
    }

    // ------------------------------------------------------------------ comandos

    private void registerCommands() {
        register("console", doc("Ejecuta un comando como la consola (sin la barra). Lo que escribió el jugador pierde los saltos de línea.", "comando", "console: \"give {player.name} diamond 1\""), a -> {
            Template t = a.template();
            return Action.of(ctx -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(t.render(ctx, Template.Mode.COMMAND))));
        });
        register("command", doc("Hace que el jugador ejecute un comando.", "comando", "command: \"spawn\""), a -> {
            Template t = a.template();
            return Action.of(ctx -> ctx.requirePlayer().performCommand(stripSlash(t.render(ctx, Template.Mode.COMMAND))));
        });
        register("chat", doc("Hace que el jugador escriba en el chat.", "texto", "chat: \"¡Hola a todos!\""), a -> {
            Template t = a.template();
            return Action.of(ctx -> ctx.requirePlayer().chat(t.render(ctx, Template.Mode.COMMAND)));
        });
        register("send_to_server", doc("Manda al jugador a otro servidor de la red (BungeeCord o Velocity).", "servidor", "send_to_server: lobby"), a -> {
            Template t = a.template();
            return Action.of(ctx -> {
                Player p = ctx.requirePlayer();
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(bytes);
                    out.writeUTF("Connect");
                    out.writeUTF(t.render(ctx, Template.Mode.RAW));
                    p.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
                } catch (IOException e) {
                    throw new ActionException("no se pudo enviar al servidor: " + e.getMessage());
                }
            });
        });
    }

    private static String stripSlash(String s) {
        s = s.trim();
        return s.startsWith("/") ? s.substring(1) : s;
    }

    // ------------------------------------------------------------------ jugador / entidad

    private void registerPlayer() {
        register("teleport", doc("Teletransporta al jugador o la entidad; ~ es relativo a su posición.", "mundo;x;y;z;yaw;pitch", "teleport: \"world;0.5;100;0.5\""), a -> {
            List<Template> p = a.parts();
            return Action.of(ctx -> {
                Entity e = requireEntity(ctx);
                e.teleport(location(p, 0, ctx, e.getLocation(), true));
            });
        });
        register("heal", doc("Cura (sin valor, por completo).", "expresión (vida)", "heal: 10"), a -> {
            Expression n = a.text().isEmpty() ? Expression.compile("1000") : a.expr();
            return Action.of(ctx -> {
                LivingEntity l = requireLiving(ctx);
                AttributeInstance max = l.getAttribute(Attribute.MAX_HEALTH);
                double cap = max == null ? 20 : max.getValue();
                l.setHealth(Math.max(0, Math.min(cap, l.getHealth() + Values.num(n.eval(ctx)))));
            });
        });
        register("damage", doc("Hace daño.", "expresión", "damage: 4"), a -> {
            Expression n = a.expr();
            return Action.of(ctx -> requireLiving(ctx).damage(Values.num(n.eval(ctx))));
        });
        register("feed", doc("Da comida (sin valor, la llena).", "expresión", "feed: 6"), a -> {
            Expression n = a.text().isEmpty() ? Expression.compile("20") : a.expr();
            return Action.of(ctx -> {
                Player p = ctx.requirePlayer();
                p.setFoodLevel((int) Math.max(0, Math.min(20, p.getFoodLevel() + Values.num(n.eval(ctx)))));
                p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + 5));
            });
        });
        register("set_food", doc("Fija el hambre (0 a 20).", "expresión", "set_food: 20"), a -> {
            Expression n = a.expr();
            return Action.of(ctx -> ctx.requirePlayer().setFoodLevel((int) Math.max(0, Math.min(20, Values.num(n.eval(ctx))))));
        });
        register("gamemode", doc("Cambia el modo de juego.", "SURVIVAL, CREATIVE, ADVENTURE o SPECTATOR", "gamemode: CREATIVE"), a -> {
            Template t = a.template();
            return Action.of(ctx -> ctx.requirePlayer().setGameMode(enumOf(GameMode.class, t.render(ctx, Template.Mode.RAW), "modo de juego")));
        });
        register("fly", doc("Permite volar o lo quita (sin valor, lo permite).", "expresión (true o false)", "fly: true"), a -> {
            Expression e = a.text().isEmpty() ? Expression.compile("true") : a.expr();
            return Action.of(ctx -> {
                Player p = ctx.requirePlayer();
                boolean on = Values.truthy(e.eval(ctx));
                p.setAllowFlight(on);
                if (!on) p.setFlying(false);
            });
        });
        register("effect", doc("Da un efecto de poción (segundos negativos: infinito); ícono false lo oculta de la pantalla (spec 016).", "EFECTO;segundos;nivel;partículas;ícono", "effect: \"SPEED;30;2;false;false\""), a -> {
            List<Template> p = a.parts();
            return Action.of(ctx -> {
                LivingEntity l = requireLiving(ctx);
                PotionEffectType type = effect(p.get(0).render(ctx, Template.Mode.RAW));
                double secs = part(p, 1) == null ? 10 : Values.num(p.get(1).render(ctx, Template.Mode.RAW));
                int level = part(p, 2) == null ? 1 : (int) Values.num(p.get(2).render(ctx, Template.Mode.RAW));
                boolean particles = part(p, 3) == null || Values.truthy(p.get(3).render(ctx, Template.Mode.RAW));
                boolean icon = part(p, 4) == null || Values.truthy(p.get(4).render(ctx, Template.Mode.RAW));
                int duration = secs < 0 ? PotionEffect.INFINITE_DURATION : (int) Math.round(secs * 20);
                l.addPotionEffect(new PotionEffect(type, duration, Math.max(0, level - 1), false, particles, icon));
            });
        });
        register("remove_effect", doc("Quita un efecto, o todos (all).", "EFECTO o all", "remove_effect: SPEED"), a -> {
            Template t = a.template();
            return Action.of(ctx -> {
                LivingEntity l = requireLiving(ctx);
                String n = t.render(ctx, Template.Mode.RAW);
                if (n.equalsIgnoreCase("all")) l.getActivePotionEffects().forEach(e -> l.removePotionEffect(e.getType()));
                else l.removePotionEffect(effect(n));
            });
        });
        register("fire", doc("Prende fuego por N ticks.", "expresión (ticks)", "fire: 60"), a -> {
            Expression n = a.expr();
            return Action.of(ctx -> requireEntity(ctx).setFireTicks((int) Values.num(n.eval(ctx))));
        });
        register("freeze", doc("Congela (como la nieve en polvo) por N ticks.", "expresión (ticks)", "freeze: 140"), a -> {
            Expression n = a.expr();
            return Action.of(ctx -> requireEntity(ctx).setFreezeTicks((int) Values.num(n.eval(ctx))));
        });
        register("velocity", doc("Fija la velocidad.", "x;y;z", "velocity: \"0;1.2;0\""), a -> {
            List<Template> p = a.parts();
            if (p.size() < 3) throw new IllegalArgumentException("'velocity' recibe x;y;z");
            return Action.of(ctx -> requireEntity(ctx).setVelocity(new Vector(num(p, 0, ctx), num(p, 1, ctx), num(p, 2, ctx))));
        });
        register("launch", doc("Impulsa hacia donde mira (sin valor, fuerza 1.5; máximo 10).", "expresión (fuerza)", "launch: 2"), a -> {
            Expression n = a.text().isEmpty() ? Expression.compile("1.5") : a.expr();
            return Action.of(ctx -> {
                Entity e = requireEntity(ctx);
                Vector dir = e.getLocation().getDirection().normalize().multiply(Math.min(10, Values.num(n.eval(ctx))));
                e.setVelocity(dir);
            });
        });
        register("give_item", doc("Da un ítem; lo que no cabe cae al suelo. En forma de mapa, slot lo pone en una casilla (spec 015).", "MATERIAL;cantidad;nombre;lore1|lore2, saved:<nombre> o un mapa", "give_item: \"DIAMOND;2;<aqua>Diamante;<gray>Premio\"").shape(Shape.ITEM, GIVE_KEYS), a -> {
            ItemSpec spec = ItemSpec.parse(a, plugin);
            Template slotT = a.isMap() && a.map().get("slot") != null ? Template.compile(Values.str(a.map().get("slot"))) : null;
            if (slotT != null && slotT.isConstant()) Slots.parse(slotT.source());
            boolean replace = a.isMap() && Values.truthy(a.map().get("slot_replace"));
            return Action.of(ctx -> {
                Player p = ctx.requirePlayer();
                ItemStack it = spec.build(ctx);
                PlayerInventory inv = p.getInventory();
                if (slotT != null) {
                    int slot = slot(slotT.render(ctx, Template.Mode.RAW), inv);
                    ItemStack cur = inv.getItem(slot);
                    if (replace || cur == null || cur.getType().isAir()) {
                        inv.setItem(slot, it);
                        return;
                    }
                }
                for (ItemStack left : inv.addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            });
        });
        register("drop_item", doc("Suelta un ítem en el suelo, donde está el jugador o en location.", "como give_item", "drop_item: \"GOLD_INGOT;3\"\nlocation: \"world;0;64;0\"").shape(Shape.ITEM, ITEM_KEYS).keys("location", "Dónde soltarlo: mundo;x;y;z (~ relativo)."), a -> {
            ItemSpec spec = ItemSpec.parse(a, plugin);
            Object locRaw = a.extra("location");
            List<Template> loc = locRaw == null ? null : new Args("location", locRaw, Map.of(), a.path(), a.parser()).parts();
            return Action.of(ctx -> {
                Location l = loc == null ? ctx.location() : location(loc, 0, ctx, ctx.location(), true);
                if (l == null) throw new ActionException("drop_item no tiene dónde soltar el ítem (usa location: mundo;x;y;z)");
                l.getWorld().dropItemNaturally(l, spec.build(ctx));
            });
        });
        register("remove_item", doc("Quita ítems por material, tengan o no nombre; con name: o lorecontains: (spec 015), solo los de ese nombre o con esa línea de lore (sin colores).", "MATERIAL;cantidad[;name:<nombre>][;lorecontains:<texto>]", "remove_item: \"PAPER;1;name:Ticket VIP\""), a -> {
            List<Template> p = a.parts();
            Template nameF = null, loreF = null;
            for (int i = 2; i < p.size(); i++) {
                String src = p.get(i).source().trim();
                String low = src.toLowerCase(Locale.ROOT);
                if (low.startsWith("name:")) nameF = Template.compile(src.substring(5));
                else if (low.startsWith("lorecontains:")) loreF = Template.compile(src.substring(13));
                else if (!src.isEmpty()) throw new IllegalArgumentException("filtro de remove_item desconocido '" + src + "' (usa name:<nombre> o lorecontains:<texto>)");
            }
            Template fName = nameF, fLore = loreF;
            return Action.of(ctx -> {
                Player pl = ctx.requirePlayer();
                Material m = material(p.get(0).render(ctx, Template.Mode.RAW));
                int amount = part(p, 1) == null ? 1 : (int) num(p, 1, ctx);
                String n = fName == null ? null : plainFilter(fName.render(ctx, Template.Mode.RAW));
                String l = fLore == null ? null : plainFilter(fLore.render(ctx, Template.Mode.RAW));
                removeByMaterial(pl.getInventory(), m, amount, it -> matches(it, n, l));
            });
        });
        register("remove_item_slot", doc("Quita ítems de una casilla (spec 015): HAND, OFF_HAND, HELMET, CHESTPLATE, LEGGINGS, BOOTS o 0 a 40; sin cantidad, toda la pila.", "CASILLA;cantidad", "remove_item_slot: \"HAND;1\""), a -> {
            List<Template> p = a.parts();
            if (p.get(0).isConstant()) Slots.parse(p.get(0).source());
            return Action.of(ctx -> {
                PlayerInventory inv = ctx.requirePlayer().getInventory();
                int slot = slot(p.get(0).render(ctx, Template.Mode.RAW), inv);
                ItemStack it = inv.getItem(slot);
                if (it == null || it.getType().isAir()) return;
                int take = part(p, 1) == null ? it.getAmount() : (int) Math.max(0, num(p, 1, ctx));
                if (take >= it.getAmount()) inv.setItem(slot, null);
                else {
                    it.setAmount(it.getAmount() - take);
                    inv.setItem(slot, it);
                }
            });
        });
        register("set_caught_item", doc("Cambia lo que se pescó (player_fish) por otro ítem, con el formato de give_item (spec 015; set_item de ConditionalEvents).", "como give_item", "set_caught_item: \"DIAMOND;1;<aqua>Diamante del lago\"").shape(Shape.ITEM, ITEM_KEYS), a -> {
            ItemSpec spec = ItemSpec.parse(a, plugin);
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof org.bukkit.event.player.PlayerFishEvent f)) throw new ActionException("set_caught_item solo funciona en player_fish");
                if (!(f.getCaught() instanceof org.bukkit.entity.Item caught)) throw new ActionException("set_caught_item: lo pescado no es un ítem (usa la condición state == 'CAUGHT_FISH')");
                caught.setItemStack(spec.build(ctx));
            }));
        });
        register("clear_inventory", doc("Vacía el inventario.", "(sin valor)", "clear_inventory"), a -> Action.of(ctx -> ctx.requirePlayer().getInventory().clear()));
        register("close_inventory", doc("Cierra el inventario abierto.", "(sin valor)", "close_inventory"), a -> Action.of(ctx -> ctx.requirePlayer().closeInventory()));
        // Spec 013: menús de inventario (y formularios de Bedrock con Floodgate).
        register("open_menu", doc("Abre un menú (una entrada con menu:) al jugador; a uno de Bedrock con Floodgate se le muestra como formulario de botones. Los args llegan al menú y a sus clics como var.<nombre>.",
                "nombre del menú, o un mapa con menu y args", "open_menu: tienda").shape(Shape.CALL, "menu", "Menú que se abre (en un paquete, primero el del paquete).", "args", "Mapa nombre: expresión; en el menú y sus clics, var.<nombre>."), a -> {
            Template menu = Template.compile(a.isMap() ? Values.str(a.map().get("menu")) : a.text());
            Map<String, Expression> params = new LinkedHashMap<>();
            if (a.isMap() && a.map().get("args") instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) params.put(String.valueOf(e.getKey()), Expression.compile(Values.str(e.getValue())));
            }
            if (menu.source().isBlank()) throw new IllegalArgumentException("open_menu necesita el nombre de un menú (open_menu: tienda)");
            if (menu.isConstant() && a.parser() != null) a.parser().referMenu(menu.source().trim(), a.path());
            return Action.of(ctx -> {
                Player p = ctx.requirePlayer();
                String name = menu.render(ctx, Template.Mode.RAW).trim();
                dev.leo.omnievents.menu.Menu m = ctx.plugin().menus().get(name, ctx.rule() == null ? null : ctx.rule().pack());
                if (m == null) throw new ActionException("no hay un menú '" + name + "' (/omni menu list)");
                Map<String, Object> args = new LinkedHashMap<>();
                params.forEach((k, e) -> args.put(k, e.eval(ctx)));
                ctx.plugin().menus().open(p, m, args);
            });
        });
        register("close_menu", doc("Cierra el menú de OmniEvents que tenga abierto el jugador (otro inventario no lo toca); corre su on_close.", "(sin valor)", "close_menu"),
                a -> Action.of(ctx -> ctx.plugin().menus().close(ctx.requirePlayer())));
        register("give_exp", doc("Da puntos de experiencia.", "expresión", "give_exp: 100"), a -> {
            Expression n = a.expr();
            return Action.of(ctx -> ctx.requirePlayer().giveExp((int) Values.num(n.eval(ctx))));
        });
        register("give_level", doc("Da niveles de experiencia.", "expresión", "give_level: 5"), a -> {
            Expression n = a.expr();
            return Action.of(ctx -> ctx.requirePlayer().giveExpLevels((int) Values.num(n.eval(ctx))));
        });
        // Spec 012, H1: dinero con Vault. En una prueba mueven el saldo de given.money.
        register("give_money", doc("Deposita dinero en la economía de Vault (necesita Vault y un plugin de economía).", "expresión (cantidad > 0)", "give_money: 100"), a -> money(a, true));
        register("take_money", doc("Retira dinero de la economía de Vault; si no alcanza, es un error y no retira nada.", "expresión (cantidad > 0)", "take_money: \"var.precio\""), a -> money(a, false));
        register("kick", doc("Expulsa al jugador con un mensaje.", "texto", "kick: \"<red>Vuelve pronto\""), a -> {
            Template t = a.template();
            return Action.of(ctx -> ctx.requirePlayer().kick(t.component(ctx)));
        });
    }

    private static Action money(Args a, boolean give) {
        Expression n = a.expr();
        String type = give ? "give_money" : "take_money";
        return (ctx, exec) -> {
            double amount = Values.num(n.eval(ctx));
            if (!(amount > 0)) throw new ActionException(type + " dio " + Values.formatNumber(amount) + ": la cantidad debe ser mayor que 0");
            if (exec.testing()) exec.sandbox().effect(type, "", Values.formatNumber(amount));
            if (ctx.sandbox() == null) ctx.requirePlayer();
            Purse purse = ctx.purse();
            Double balance = purse.money();
            if (balance == null) throw new ActionException(ctx.sandbox() != null ? type + ": la prueba no tiene saldo (given.money)" : dev.leo.omnievents.hook.Economy.MISSING);
            if (!give && balance < amount) {
                throw new ActionException("take_money: no alcanza (tiene " + purse.formatMoney(balance) + ", pide " + purse.formatMoney(amount) + ")");
            }
            boolean ok = give ? purse.giveMoney(amount) : purse.takeMoney(amount);
            if (!ok) throw new ActionException(type + ": la economía rechazó la operación de " + purse.formatMoney(amount));
            exec.trace("    " + type + " " + purse.formatMoney(amount));
            return 0;
        };
    }

    /**
     * Quita hasta {@code amount} ítems del material, tengan o no nombre, lore o encantamientos (inventario y mano
     * secundaria; la armadura no). Regresión (spec 002, F-02): {@code removeItem} de Bukkit solo quita ítems idénticos
     * y no tocaba los que da el propio plugin con nombre.
     */
    private static void removeByMaterial(PlayerInventory inv, Material m, int amount, java.util.function.Predicate<ItemStack> filter) {
        int left = amount;
        for (int i = 0; i < inv.getSize() && left > 0; i++) {
            if (i >= 36 && i < 40) continue;
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != m || !filter.test(it)) continue;
            int take = Math.min(left, it.getAmount());
            left -= take;
            if (take == it.getAmount()) inv.setItem(i, null);
            else {
                it.setAmount(it.getAmount() - take);
                inv.setItem(i, it);
            }
        }
    }

    /** Texto de un filtro sin colores (&, § y etiquetas MiniMessage). */
    static String plainFilter(String s) {
        return dev.leo.omnievents.text.TextFormat.plain(s).trim();
    }

    /** Spec 015, H1 CA2: el ítem tiene ese nombre (sin colores) y una línea de lore que contiene el texto. */
    static boolean matches(ItemStack it, String name, String loreContains) {
        if (name == null && loreContains == null) return true;
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        if (name != null) {
            if (meta == null || !meta.hasDisplayName()) return false;
            if (!plain(meta.displayName()).trim().equals(name)) return false;
        }
        if (loreContains != null) {
            List<Component> lore = meta == null ? null : meta.lore();
            if (lore == null) return false;
            boolean found = false;
            for (Component c : lore) if (plain(c).contains(loreContains)) found = true;
            if (!found) return false;
        }
        return true;
    }

    private static String plain(Component c) {
        return c == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** La casilla de Slots en el inventario (HAND: la que tiene seleccionada el jugador). */
    private static int slot(String text, PlayerInventory inv) {
        int s;
        try {
            s = Slots.parse(text);
        } catch (IllegalArgumentException e) {
            throw new ActionException(e.getMessage());
        }
        return s == Slots.HAND ? inv.getHeldItemSlot() : s;
    }

    private static Entity requireEntity(ExecContext ctx) {
        Entity e = ctx.entityOrNull();
        if (e == null) throw new ActionException("esta acción necesita un jugador o una entidad");
        return e;
    }

    private static LivingEntity requireLiving(ExecContext ctx) {
        if (requireEntity(ctx) instanceof LivingEntity l) return l;
        throw new ActionException("esta acción necesita una entidad viva");
    }

    private static double num(List<Template> p, int i, ExecContext ctx) {
        return Values.num(p.get(i).render(ctx, Template.Mode.RAW));
    }

    @SuppressWarnings("deprecation")
    static PotionEffectType effect(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        PotionEffectType t = Bukkit.getRegistry(PotionEffectType.class) == null ? null
                : org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(n));
        if (t == null) t = PotionEffectType.getByName(n.toUpperCase(Locale.ROOT));
        if (t == null) throw new ActionException("efecto desconocido '" + name + "'");
        return t;
    }

    static Material material(String name) {
        Material m = Material.matchMaterial(name.trim());
        if (m == null) throw new ActionException("material desconocido '" + name + "'");
        return m;
    }

    /**
     * mundo;x;y;z[;yaw;pitch] desde la parte i. Con relative, '~' es relativo a base. Con 3 partes, el mundo es el
     * de base.
     */
    static Location location(List<Template> p, int i, ExecContext ctx, Location base, boolean relative) {
        int n = p.size() - i;
        if (n == 1) {
            String one = p.get(i).render(ctx, Template.Mode.RAW);
            List<String> sub = Args.split(one, ';');
            if (sub.size() >= 3) {
                List<Template> again = new ArrayList<>();
                for (String s : sub) again.add(Template.compile(s));
                return location(again, 0, ctx, base, relative);
            }
            throw new ActionException("ubicación inválida '" + one + "' (usa mundo;x;y;z)");
        }
        int k = i;
        World w;
        if (n >= 4 && !Values.isNumeric(p.get(k).render(ctx, Template.Mode.RAW)) && !p.get(k).render(ctx, Template.Mode.RAW).startsWith("~")) {
            String wn = p.get(k++).render(ctx, Template.Mode.RAW);
            w = Bukkit.getWorld(wn);
            if (w == null) throw new ActionException("mundo desconocido '" + wn + "'");
        } else {
            w = base != null ? base.getWorld() : Bukkit.getWorlds().get(0);
        }
        double x = coord(p.get(k++).render(ctx, Template.Mode.RAW), base == null ? 0 : base.getX(), relative);
        double y = coord(p.get(k++).render(ctx, Template.Mode.RAW), base == null ? 0 : base.getY(), relative);
        double z = coord(p.get(k++).render(ctx, Template.Mode.RAW), base == null ? 0 : base.getZ(), relative);
        float yaw = k < p.size() ? (float) coord(p.get(k++).render(ctx, Template.Mode.RAW), base == null ? 0 : base.getYaw(), relative) : base == null ? 0 : base.getYaw();
        float pitch = k < p.size() ? (float) coord(p.get(k).render(ctx, Template.Mode.RAW), base == null ? 0 : base.getPitch(), relative) : base == null ? 0 : base.getPitch();
        return new Location(w, x, y, z, yaw, pitch);
    }

    private static double coord(String s, double base, boolean relative) {
        s = s.trim();
        if (relative && s.startsWith("~")) return base + (s.length() == 1 ? 0 : Values.num(s.substring(1)));
        return Values.num(s);
    }

    // ------------------------------------------------------------------ mundo

    private void registerWorld() {
        register("set_block", doc("Cambia un bloque: con solo el material, el bloque del evento; con coordenadas, ese (~ relativo). El material acepta datos de bloque: OAK_STAIRS[facing=north] (spec 015).", "[mundo;]x;y;z;MATERIAL[datos] o MATERIAL[datos]", "set_block: \"~;~-1;~;GOLD_BLOCK\""), a -> {
            List<Template> p = a.parts();
            return Action.of(ctx -> {
                Block target;
                if (p.size() == 1) {
                    Object b = Values.raw(ctx.resolve("block"));
                    if (!(b instanceof Block block)) throw new ActionException("set_block sin coordenadas necesita un disparador con bloque");
                    target = block;
                } else {
                    target = location(p.subList(0, p.size() - 1), 0, ctx, ctx.location(), true).getBlock();
                }
                setBlock(target, p.get(p.size() - 1).render(ctx, Template.Mode.RAW).trim());
            });
        });
        register("lightning", doc("Cae un rayo (solo el efecto, sin daño) donde está el jugador o en mundo;x;y;z.", "(sin valor) o mundo;x;y;z", "lightning"), a -> {
            List<Template> p = a.text().isEmpty() ? List.of() : a.parts();
            return Action.of(ctx -> {
                Location l = p.isEmpty() ? ctx.location() : location(p, 0, ctx, ctx.location(), true);
                if (l == null) throw new ActionException("lightning no tiene ubicación");
                l.getWorld().strikeLightningEffect(l);
            });
        });
        register("explosion", doc("Explosión donde está el jugador (potencia máxima 10).", "potencia;fuego;romper bloques", "explosion: \"2;false;false\""), a -> {
            List<Template> p = a.text().isEmpty() ? List.of() : a.parts();
            return Action.of(ctx -> {
                Location l = ctx.location();
                if (l == null) throw new ActionException("explosion no tiene ubicación");
                float power = part(p, 0) == null ? 2f : (float) num(p, 0, ctx);
                boolean fire = part(p, 1) != null && Values.truthy(p.get(1).render(ctx, Template.Mode.RAW));
                boolean breakBlocks = part(p, 2) != null && Values.truthy(p.get(2).render(ctx, Template.Mode.RAW));
                l.getWorld().createExplosion(l, Math.min(power, 10f), fire, breakBlocks);
            });
        });
        register("particle", doc("Partículas donde está el jugador o en location (DUST lleva el color r;g;b).", "PARTÍCULA;cantidad;dx;dy;dz;velocidad[;r;g;b]", "particle: \"HEART;10\"\nlocation: \"~;~2;~\"\nforce: false\nfor_player: true")
                .keys("location", "Dónde: mundo;x;y;z (~ relativo; spec 015). Sin ella, un bloque encima del jugador.",
                        "force", "true: se ve desde lejos (spec 015).", "for_player", "true: solo la ve el jugador (spec 015)."), a -> {
            List<Template> p = a.parts();
            List<Template> locP = a.extra("location") == null ? null : new Args("location", a.extra("location"), Map.of(), a.path(), a.parser()).parts();
            Expression force = a.extra("force") == null ? null : Expression.compile(Values.str(a.extra("force")));
            Expression forPlayer = a.extra("for_player") == null ? null : Expression.compile(Values.str(a.extra("for_player")));
            return Action.of(ctx -> {
                Location l = ctx.location();
                if (l == null && locP == null) throw new ActionException("particle no tiene ubicación (usa location: mundo;x;y;z)");
                org.bukkit.Particle type = enumOf(org.bukkit.Particle.class, p.get(0).render(ctx, Template.Mode.RAW), "partícula");
                int count = part(p, 1) == null ? 10 : (int) num(p, 1, ctx);
                double dx = part(p, 2) == null ? 0.3 : num(p, 2, ctx), dy = part(p, 3) == null ? 0.3 : num(p, 3, ctx), dz = part(p, 4) == null ? 0.3 : num(p, 4, ctx);
                double speed = part(p, 5) == null ? 0 : num(p, 5, ctx);
                Location at = locP != null ? location(locP, 0, ctx, l, true) : l.clone().add(0, 1, 0);
                Object data = null;
                if (type.getDataType() == org.bukkit.Particle.DustOptions.class) {
                    int r = part(p, 6) == null ? 255 : (int) num(p, 6, ctx), g = part(p, 7) == null ? 0 : (int) num(p, 7, ctx), b = part(p, 8) == null ? 0 : (int) num(p, 8, ctx);
                    data = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(clamp255(r), clamp255(g), clamp255(b)), 1f);
                } else if (type.getDataType() != Void.class) {
                    throw new ActionException("la partícula " + type + " necesita datos que OmniEvents aún no soporta");
                }
                int n = Math.min(count, 2000);
                if (forPlayer != null && forPlayer.test(ctx)) {
                    ctx.requirePlayer().spawnParticle(type, at, n, dx, dy, dz, speed, data);
                } else {
                    at.getWorld().spawnParticle(type, at, n, dx, dy, dz, speed, data, force != null && force.test(ctx));
                }
            });
        });
        register("firework", doc("Lanza un fuego artificial desde el jugador (o cada destinatario de to) o desde location; sin daño salvo damage:true (spec 014). Forma de ConditionalEvents: pares clave:valor separados por espacios.",
                "colors:C1,C2 [type:BALL|BALL_LARGE|STAR|BURST|CREEPER] [power:0-3] [fade:C1,C2] [flicker:true] [trail:true] [location:mundo;x;y;z] [damage:true] [shot_direction:xmin,xmax;ymin,ymax;zmin,zmax;vmin-vmax]; colores por nombre (RED, AQUA…) o #RRGGBB; shot_direction lo dispara en ángulo (spec 016)",
                "firework: \"colors:RED,#FFAA00 type:STAR power:1 fade:WHITE trail:true\""), a -> {
            Firework fw = Firework.parse(a.text());
            return Action.of(ctx -> {
                Location at;
                if (fw.location() != null) {
                    List<String> parts = Args.split(fw.location().render(ctx, Template.Mode.RAW), ';');
                    // ConditionalEvents escribe x;y;z;mundo: el mundo va primero en OmniEvents.
                    if (parts.size() == 4 && Values.isNumeric(parts.get(0).trim()) && !Values.isNumeric(parts.get(3).trim()) && !parts.get(3).trim().startsWith("~")) {
                        parts = List.of(parts.get(3), parts.get(0), parts.get(1), parts.get(2));
                    }
                    List<Template> lp = new ArrayList<>();
                    for (String s : parts) lp.add(Template.compile(s.trim()));
                    at = location(lp, 0, ctx, ctx.location(), true);
                } else {
                    Location l = ctx.location();
                    if (l == null) throw new ActionException("firework no tiene ubicación (usa location:mundo;x;y;z)");
                    at = l.clone().add(0, 1, 0);
                }
                org.bukkit.FireworkEffect effect = fw.effect(ctx);
                int power = fw.power(ctx);
                boolean damage = fw.damage(ctx);
                Vector shot = fw.shot(ctx);
                at.getWorld().spawn(at, org.bukkit.entity.Firework.class, f -> {
                    org.bukkit.inventory.meta.FireworkMeta meta = f.getFireworkMeta();
                    meta.addEffect(effect);
                    meta.setPower(power);
                    f.setFireworkMeta(meta);
                    if (shot != null) {
                        f.setShotAtAngle(true);
                        f.setVelocity(shot);
                    }
                    if (!damage) fireworkGuard().mark(f);
                });
            });
        });
        register("summon", doc("Invoca una entidad donde está el jugador o en mundo;x;y;z. En forma de mapa (spec 015): name, health, amount, scale y equipo.", "TIPO[;mundo;x;y;z] o un mapa", "summon: ZOMBIE").shape(Shape.ITEM,
                "entity", "Tipo de entidad (ZOMBIE…).", "type", "Sinónimo de entity.", "location", "Dónde: mundo;x;y;z (~ relativo); sin ella, donde está el jugador.",
                "name", "Nombre visible (MiniMessage, & y {expresiones}).", "health", "Vida máxima y actual.", "amount", "Cuántas (1 por defecto, máximo 50).",
                "scale", "Escala de tamaño (atributo scale; 1 es normal).", "equipment", "Armadura: casco,peto,pantalones,botas (materiales o none).",
                "hand_equipment", "Manos: principal,secundaria (materiales o none)."), a -> {
            if (a.isMap()) return summonMap(a);
            List<Template> p = a.parts();
            return Action.of(ctx -> {
                EntityType type = enumOf(EntityType.class, p.get(0).render(ctx, Template.Mode.RAW), "tipo de entidad");
                Location l = p.size() > 1 ? location(p, 1, ctx, ctx.location(), true) : ctx.location();
                if (l == null) throw new ActionException("summon no tiene ubicación");
                l.getWorld().spawnEntity(l, type);
            });
        });
    }

    /** Spec 015, H2 CA1: summon con las claves de ConditionalEvents. */
    private static Action summonMap(Args a) {
        Map<String, Object> m = a.map();
        Object typeRaw = m.get("entity") != null ? m.get("entity") : m.get("type");
        if (typeRaw == null) throw new IllegalArgumentException("summon en forma de mapa necesita entity (ZOMBIE…)");
        Template type = Template.compile(Values.str(typeRaw));
        List<Template> loc = m.get("location") == null ? null : new Args("location", m.get("location"), Map.of(), a.path(), a.parser()).parts();
        Template name = opt(m, "name"), health = opt(m, "health"), amount = opt(m, "amount"), scale = opt(m, "scale");
        List<Template> armor = equipment(m.get("equipment"), 4, "equipment"), hands = equipment(m.get("hand_equipment"), 2, "hand_equipment");
        return Action.of(ctx -> {
            EntityType t = enumOf(EntityType.class, type.render(ctx, Template.Mode.RAW), "tipo de entidad");
            Location l = loc == null ? ctx.location() : location(loc, 0, ctx, ctx.location(), true);
            if (l == null) throw new ActionException("summon no tiene ubicación (usa location: mundo;x;y;z)");
            int n = amount == null ? 1 : (int) Math.max(1, Math.min(50, Values.num(amount.render(ctx, Template.Mode.RAW))));
            for (int i = 0; i < n; i++) {
                Entity e = l.getWorld().spawnEntity(l, t);
                if (name != null) {
                    e.customName(name.component(ctx));
                    e.setCustomNameVisible(true);
                }
                if (!(e instanceof LivingEntity le)) continue;
                if (health != null) {
                    double h = Math.max(1, Values.num(health.render(ctx, Template.Mode.RAW)));
                    AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) max.setBaseValue(h);
                    le.setHealth(Math.min(h, max == null ? h : max.getValue()));
                }
                if (scale != null) {
                    AttributeInstance sc = le.getAttribute(Attribute.SCALE);
                    if (sc != null) sc.setBaseValue(Math.max(0.0625, Math.min(16, Values.num(scale.render(ctx, Template.Mode.RAW)))));
                }
                org.bukkit.inventory.EntityEquipment eq = le.getEquipment();
                if (eq == null) continue;
                org.bukkit.inventory.EquipmentSlot[] armorSlots = {org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.CHEST,
                        org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET};
                org.bukkit.inventory.EquipmentSlot[] handSlots = {org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.inventory.EquipmentSlot.OFF_HAND};
                equip(eq, armor, armorSlots, ctx);
                equip(eq, hands, handSlots, ctx);
            }
        });
    }

    /** MATERIAL o MATERIAL[clave=valor,…] (datos de bloque, spec 015 H2 CA2). */
    private static void setBlock(Block b, String spec) {
        if (spec.indexOf('[') < 0) {
            b.setType(material(spec));
            return;
        }
        String id = spec.contains(":") ? spec : "minecraft:" + spec.toLowerCase(Locale.ROOT).replaceFirst("\\[.*", "") + spec.substring(spec.indexOf('['));
        try {
            b.setBlockData(Bukkit.createBlockData(id));
        } catch (IllegalArgumentException e) {
            throw new ActionException("datos de bloque inválidos '" + spec + "'");
        }
    }

    private static Template opt(Map<String, Object> m, String key) {
        return m.get(key) == null ? null : Template.compile(Values.str(m.get(key)));
    }

    /** casco,peto,pantalones,botas (o principal,secundaria): texto con comas o lista; none deja la casilla vacía. */
    private static List<Template> equipment(Object raw, int max, String key) {
        if (raw == null) return List.of();
        List<?> items = raw instanceof List<?> l ? l : List.of(Values.str(raw).split(","));
        if (items.size() > max) throw new IllegalArgumentException(key + " lleva hasta " + max + " ítems separados por comas");
        List<Template> out = new ArrayList<>();
        for (Object o : items) out.add(Template.compile(Values.str(o).trim()));
        return out;
    }

    private static void equip(org.bukkit.inventory.EntityEquipment eq, List<Template> items, org.bukkit.inventory.EquipmentSlot[] slots, ExecContext ctx) {
        for (int i = 0; i < items.size(); i++) {
            String v = items.get(i).render(ctx, Template.Mode.RAW).trim();
            if (v.isEmpty() || v.equalsIgnoreCase("none")) continue;
            eq.setItem(slots[i], new ItemStack(material(v)));
            // Regresión (spec 015, F-02): Bukkit no deja fijar la probabilidad de soltar en lo que no es un Mob
            // (un soporte de armadura) y el summon entero fallaba.
            if (eq.getHolder() instanceof org.bukkit.entity.Mob) eq.setDropChance(slots[i], 0f);
        }
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ------------------------------------------------------------------ evento

    private void registerEvent() {
        register("cancel", doc("Cancela el evento (o no, con false o una expresión).", "(sin valor) o expresión", "cancel"), a -> {
            Expression e = a.text().isEmpty() ? Expression.compile("true") : a.expr();
            return Action.safe(Action.of(ctx -> {
                if (ctx.sandbox() != null) {
                    ctx.sandbox().cancel(Values.truthy(e.eval(ctx)));
                    return;
                }
                Event ev = ctx.event();
                if (!(ev instanceof Cancellable c)) throw new ActionException("este evento no se puede cancelar");
                c.setCancelled(Values.truthy(e.eval(ctx)));
            }));
        });
        register("set_event", doc("Cambia una propiedad del evento con su setter.", "propiedad = expresión", "set_event: \"damage = event.damage * 2\""), a -> {
            String t = a.text();
            int eq = t.indexOf('=');
            if (eq <= 0 || t.startsWith("==", eq)) throw new IllegalArgumentException("'set_event' se escribe 'propiedad = expresión', p. ej. 'damage = event.damage * 2'");
            String prop = t.substring(0, eq).trim();
            Expression value = Expression.compile(t.substring(eq + 1).trim());
            return Action.safe(Action.of(ctx -> {
                Event ev = ctx.event();
                if (ev == null) throw new ActionException("no hay evento que modificar");
                Properties.get().write(ev, prop, value.eval(ctx));
            }));
        });
        register("set_damage", doc("Fija el daño del evento: un número o un porcentaje del actual.", "número o porcentaje (150%)", "set_damage: \"150%\""), a -> {
            String t = a.text();
            boolean percent = t.endsWith("%");
            Expression value = Expression.compile(percent ? t.substring(0, t.length() - 1) : t);
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof EntityDamageEvent d)) throw new ActionException("set_damage solo funciona en eventos de daño");
                double v = Values.num(value.eval(ctx));
                d.setDamage(Math.max(0, percent ? d.getDamage() * v / 100 : v));
            }));
        });
        register("set_message", doc("Cambia el mensaje de chat (player_chat).", "texto", "set_message: \"<gray>{message}\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof AsyncChatEvent chat)) throw new ActionException("set_message solo funciona en player_chat");
                chat.message(t.component(ctx));
            }));
        });
        register("set_join_message", doc("Cambia el mensaje de entrada (none lo oculta).", "texto o none", "set_join_message: \"<green>+ {player.name}\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof PlayerJoinEvent e)) throw new ActionException("set_join_message solo funciona en player_join");
                e.joinMessage(isNone(t, ctx) ? null : t.component(ctx));
            }));
        });
        register("set_quit_message", doc("Cambia el mensaje de salida (none lo oculta).", "texto o none", "set_quit_message: none"), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof PlayerQuitEvent e)) throw new ActionException("set_quit_message solo funciona en player_quit");
                e.quitMessage(isNone(t, ctx) ? null : t.component(ctx));
            }));
        });
        register("set_death_message", doc("Cambia el mensaje de muerte (none lo oculta).", "texto o none", "set_death_message: \"<red>{player.name} cayó\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof PlayerDeathEvent e)) throw new ActionException("set_death_message solo funciona en player_death o player_kill");
                e.deathMessage(isNone(t, ctx) ? null : t.component(ctx));
            }));
        });
        register("keep_inventory", doc("El jugador conserva al morir (player_death) los ítems, el nivel o los dos: items, xp o all (spec 015); sin valor o con una expresión verdadera, all.", "items, xp, all, (sin valor) o expresión", "keep_inventory: items"), a -> {
            String mode = a.text().trim().toLowerCase(Locale.ROOT);
            boolean fixed = mode.equals("items") || mode.equals("xp") || mode.equals("all");
            Expression e = a.text().isEmpty() || fixed ? Expression.compile("true") : a.expr();
            boolean items = !fixed || !mode.equals("xp"), xp = !fixed || !mode.equals("items");
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof PlayerDeathEvent d)) throw new ActionException("keep_inventory solo funciona en player_death");
                if (!Values.truthy(e.eval(ctx))) return;
                if (items) {
                    d.setKeepInventory(true);
                    d.getDrops().clear();
                }
                if (xp) {
                    d.setKeepLevel(true);
                    d.setDroppedExp(0);
                }
            }));
        });
        register("cancel_drops", doc("El evento no suelta ítems (block_break, entity_death, player_kill).", "(sin valor) o expresión", "cancel_drops"), a -> {
            Expression e = a.text().isEmpty() ? Expression.compile("true") : a.expr();
            return Action.safe(Action.of(ctx -> {
                if (!Values.truthy(e.eval(ctx))) return;
                Event ev = ctx.event();
                if (ev instanceof BlockBreakEvent b) b.setDropItems(false);
                else if (ev instanceof EntityDeathEvent d) d.getDrops().clear();
                else throw new ActionException("cancel_drops solo funciona en block_break, entity_death y player_kill");
            }));
        });
        register("tab_complete", doc("Cambia las sugerencias de autocompletado (player_tab_complete, spec 015): set deja solo las de la lista, add las suma (las dos solo las que empiezan por lo que lleva escrito, y set las ordena), remove las quita y clear las borra todas.",
                "set|add|remove|clear;a,b,c", "tab_complete: \"set;spawn,tienda,arena\""), a -> {
            List<Template> p = a.parts();
            String mode = p.get(0).source().trim().toLowerCase(Locale.ROOT);
            if (!List.of("set", "add", "remove", "clear").contains(mode)) {
                throw new IllegalArgumentException("modo de tab_complete desconocido '" + mode + "' (usa set, add, remove o clear)" + dev.leo.omnievents.util.Suggest.hint(mode, List.of("set", "add", "remove", "clear")));
            }
            Template list = part(p, 1);
            if (list == null && !mode.equals("clear")) throw new IllegalArgumentException("tab_complete " + mode + " necesita la lista: \"" + mode + ";a,b,c\"");
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof org.bukkit.event.server.TabCompleteEvent e)) throw new ActionException("tab_complete solo funciona en player_tab_complete");
                List<String> items = new ArrayList<>();
                if (list != null) for (String s : list.render(ctx, Template.Mode.RAW).split(",")) if (!s.isBlank()) items.add(s.trim());
                e.setCompletions(TabCompletions.apply(mode, e.getCompletions(), items, TabCompletions.last(e.getBuffer())));
            }));
        });
        register("deny_join", doc("Rechaza la entrada con un mensaje (player_pre_login).", "texto", "deny_join: \"<red>El servidor está en mantenimiento\""), a -> {
            Template t = a.template();
            return Action.safe(Action.of(ctx -> {
                if (!(ctx.event() instanceof AsyncPlayerPreLoginEvent e)) throw new ActionException("deny_join solo funciona en player_pre_login");
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, t.component(ctx));
            }));
        });
    }
}
