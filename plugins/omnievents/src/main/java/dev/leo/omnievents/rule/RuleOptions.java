package dev.leo.omnievents.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Las opciones de una regla, de una condición en forma de mapa y del bloque command: el registro del que salen las
 * claves que acepta el cargador, el esquema JSON y la referencia (spec 007).
 */
public final class RuleOptions {
    /** Qué tipo de valor lleva una opción (decide su forma en el esquema). */
    public enum Kind { TRIGGER, BOOLEAN, INTEGER, TEXT, TEXTS, ENUM, DURATION, RESET, TIMES, CONDITIONS, ACTIONS, COMMAND, BUDGET, LIST, TESTS, PARAMS, COST }

    /**
     * @param values  valores permitidos (ENUM y RESET)
     * @param trigger disparador al que aplica, o null si aplica a todos
     * @param aliasOf si es un sinónimo, la opción que reemplaza
     */
    public record Option(String name, Kind kind, String description, List<String> values, String trigger, String aliasOf) {
    }

    private static final Map<String, Option> OPTIONS = new LinkedHashMap<>();
    /** Claves de una condición en forma de mapa (spec 006). */
    public static final Map<String, String> CONDITION_KEYS = new LinkedHashMap<>();
    /** Claves de un caso de tests (spec 010). */
    public static final Map<String, String> TEST_KEYS = new LinkedHashMap<>();
    /** Claves de expect de un caso (spec 010); además, variables como pvar.x, gvar.x, var.x o block.data.x. */
    public static final Map<String, String> EXPECT_KEYS = new LinkedHashMap<>();
    /** Claves del bloque command de un trigger command. */
    public static final Map<String, String> COMMAND_KEYS = new LinkedHashMap<>();
    /** Claves de cost (spec 012); además pvar.x y gvar.x. */
    public static final Map<String, String> COST_KEYS = new LinkedHashMap<>();
    /** Claves de pack.yml (spec 011). */
    public static final Map<String, String> PACK_KEYS = new LinkedHashMap<>();
    /** Claves de requires en pack.yml (spec 011). */
    public static final Map<String, String> REQUIRES_KEYS = new LinkedHashMap<>();

    private RuleOptions() {
    }

    private static void add(String name, Kind kind, String description) {
        OPTIONS.put(name, new Option(name, kind, description, List.of(), null, null));
    }

    private static void values(String name, Kind kind, String description, String... values) {
        OPTIONS.put(name, new Option(name, kind, description, List.of(values), null, null));
    }

    private static void only(String trigger, String name, Kind kind, String description, String... values) {
        OPTIONS.put(name, new Option(name, kind, description, List.of(values), trigger, null));
    }

    private static void alias(String name, String of) {
        Option o = OPTIONS.get(of);
        OPTIONS.put(name, new Option(name, o.kind(), "Sinónimo de " + of + ". " + o.description(), o.values(), o.trigger(), of));
    }

    static {
        add("trigger", Kind.TRIGGER, "Disparador: el evento que hace correr la regla (/omni triggers los lista).");
        alias("type", "trigger");
        add("description", Kind.TEXT, "Nota libre para quien lee la regla; el plugin no la usa.");
        add("enabled", Kind.BOOLEAN, "false apaga la regla sin borrarla.");
        values("priority", Kind.ENUM, "Prioridad del oyente de Bukkit (normal por defecto).", "lowest", "low", "normal", "high", "highest", "monitor");
        add("ignore_cancelled", Kind.BOOLEAN, "true: no corre si otro plugin ya canceló el evento.");
        add("worlds", Kind.TEXTS, "Mundo o lista de mundos donde corre la regla (sin la opción, en todos).");
        add("permission", Kind.TEXT, "Permiso que necesita el jugador para que la regla corra.");
        add("bypass_permission", Kind.TEXT, "Con este permiso, la regla ignora al jugador (no corre para él).");
        alias("ignore_with_permission", "bypass_permission");
        add("conditions", Kind.CONDITIONS, "Una condición o una lista (todas deben cumplirse). Cada una es una expresión, o un mapa con expr o any + of y su propio deny.");
        alias("if", "conditions");
        add("actions", Kind.ACTIONS, "Acciones que corren si se cumplen las condiciones.");
        alias("then", "actions");
        add("else", Kind.ACTIONS, "Acciones que corren si falla una condición sin deny.");
        add("cooldown", Kind.DURATION, "Enfriamiento entre usos: 30s, 5m, 1h30m, 2d, 20t (ticks) o un número de segundos.");
        values("cooldown_scope", Kind.ENUM, "player (por jugador, por defecto) o global (uno para todo el servidor).", "player", "jugador", "global", "server", "servidor");
        add("on_cooldown", Kind.ACTIONS, "Acciones si la regla está en enfriamiento (var.cooldown_left tiene los segundos que faltan).");
        add("limit", Kind.INTEGER, "Usos máximos (0 = sin límite).");
        add("one_time", Kind.BOOLEAN, "true equivale a limit: 1 (el one_time de ConditionalEvents).");
        values("limit_scope", Kind.ENUM, "player (por jugador, por defecto) o global.", "player", "jugador", "global", "server", "servidor");
        values("limit_reset", Kind.RESET, "Cuándo se reinician los usos: never, daily, weekly, monthly o una duración (12h).", "never", "nunca", "daily", "diario", "weekly", "semanal", "monthly", "mensual");
        add("on_limit", Kind.ACTIONS, "Acciones si ya no quedan usos.");
        add("cost", Kind.COST, "Lo que cuesta la regla, todo o nada: money (Vault), levels, exp, items y variables pvar.x o gvar.x. Se cobra después de las condiciones, el límite y el enfriamiento; si algo no alcanza no se cobra nada ni cuenta el uso.");
        add("on_cost_fail", Kind.ACTIONS, "Acciones si el costo no alcanza o un cobro falla (lo cobrado se devuelve). var.cost_missing dice qué falta; var.cost_missing_list, las claves.");
        add("tests", Kind.TESTS, "Pruebas de la regla: casos con given (el contexto falso) y expect (lo esperado). Corren con /omni test y al cargar, sin efectos.");
        add("template", Kind.BOOLEAN, "true: la regla es una plantilla; no corre sola (no necesita trigger) y otras la usan con extends.");
        add("extends", Kind.TEXT, "Plantilla de la que hereda (template: true): copia sus claves y las que escribe esta regla las reemplazan enteras. En un paquete busca primero en el paquete; paquete:plantilla es absoluto.");
        add("with", Kind.PARAMS, "Valores de los parámetros ${nombre} para la plantilla de extends (ganan a los params de la plantilla y del paquete).");
        add("params", Kind.PARAMS, "Valores por defecto de los parámetros ${nombre} de esta regla o plantilla.");
        add("budget", Kind.BUDGET,"Presupuesto de tiempo por disparo: 0.5ms o 200us. Avisa en consola si el promedio reciente lo pasa.");
        only("timer", "every", Kind.DURATION, "Cada cuánto corre el timer (10s, 5m; mínimo 1 tick).");
        only("timer", "repetitive_time", Kind.INTEGER, "Ticks entre ejecuciones (forma de ConditionalEvents; mejor every).");
        only("timer, state", "per_player", Kind.BOOLEAN, "true (por defecto): una ejecución (o un estado) por cada jugador conectado; false: una sola, sin jugador.");
        only("clock", "at", Kind.TIMES, "Hora u horas del día, HH:mm (\"08:00\" o [\"08:00\", \"20:30\"]).");
        only("clock", "timezone", Kind.TEXT, "Zona horaria de at (p. ej. America/Mexico_City); por defecto, la del servidor.");
        only("command", "command", Kind.COMMAND, "El comando real que registra la regla: un nombre, o un mapa con name, aliases, permission, description, usage y tab.");
        only("state", "when", Kind.CONDITIONS, "El estado: una expresión o una lista de condiciones (todas). Se revisa solo cuando puede haber cambiado.");
        only("state", "on_enter", Kind.ACTIONS, "Acciones cuando when pasa a ser verdad (una vez; pasan por permiso, límite y enfriamiento).");
        only("state", "on_exit", Kind.ACTIONS, "Acciones cuando when deja de ser verdad, si corrió su on_enter (var.state_seconds: cuánto duró).");
        only("state", "check_every", Kind.DURATION, "Respaldo por intervalo para lo que no se puede deducir (placeholders, perm(), la hora): 5s, 1m.");
        only("state", "watch", Kind.TEXTS, "Disparadores extra que hacen revisar el estado (p. ej. [player_interact]).");
        only("state", "initial", Kind.ENUM, "Al entrar al servidor con el estado ya verdadero: enter (corre on_enter, por defecto) o silent.", "enter", "silent");
        only("var_expire", "variables", Kind.TEXTS, "Variables que hacen correr la regla (p. ej. [racha, bono]); sin la opción, todas.");
        only("custom", "event", Kind.TEXT, "Nombre completo de la clase del evento (p. ej. org.bukkit.event.player.PlayerEggThrowEvent).");
        only("custom", "player_from", Kind.TEXT, "Ruta al jugador dentro del evento (p. ej. getPlayer o entity); por defecto player o entity.");
        only("player_move", "move_mode", Kind.ENUM, "Cuándo cuenta un movimiento: block (cambia de bloque, por defecto), chunk o any.", "block", "chunk", "any");
        only("call", "args", Kind.LIST, "Parámetros de la regla call; llegan como var.<nombre>.");

        CONDITION_KEYS.put("expr", "Expresión que debe cumplirse.");
        CONDITION_KEYS.put("any", "Cuántas de las condiciones de of deben cumplirse como mínimo (entero entre 1 y el tamaño de of).");
        CONDITION_KEYS.put("of", "Condiciones del grupo any (textos o mapas; se puede anidar otro any).");
        CONDITION_KEYS.put("deny", "Acciones si esta condición es la primera que falla; reemplazan al else de la regla. Dentro de of se ignoran.");

        TEST_KEYS.put("name", "Nombre del caso (sin él: caso 1, caso 2…).");
        TEST_KEYS.put("given", "Contexto falso: rutas con puntos y su valor (block.type: DIAMOND_ORE, player.level: 12, event.damage: 5), variables iniciales (pvar.x, gvar.x, var.x, block.data.x), placeholders (\"%vault_eco_balance%\": 100) y, para cost y las integraciones (spec 012), money (saldo), inventory (material: cantidad) y regions (ids).");
        TEST_KEYS.put("expect", "Lo esperado: fired, branch, variables, messages, commands, actions, cancelled y assert.");
        TEST_KEYS.put("seed", "Semilla de random en este caso (0 por defecto): el mismo número elige siempre la misma rama.");
        EXPECT_KEYS.put("fired", "true si corrieron las acciones principales (actions, u on_enter en un state).");
        EXPECT_KEYS.put("branch", "La rama que corrió: actions, else, deny (o deny conditions[1]), none, on_cost_fail (el costo no alcanzó) u on_enter en un state.");
        EXPECT_KEYS.put("messages", "Textos que deben aparecer, en orden y sin formato, en los mensajes que mandaría (message, broadcast, actionbar, title…).");
        EXPECT_KEYS.put("commands", "Textos que deben aparecer, en orden, en los comandos que correría (console, command).");
        EXPECT_KEYS.put("actions", "Acciones que correría, en orden: el tipo (give_item) o el tipo y un texto (\"sound: LEVELUP\").");
        EXPECT_KEYS.put("cancelled", "true si cancela el evento.");
        EXPECT_KEYS.put("assert", "Expresión o lista de expresiones que deben dar verdadero al terminar (\"pvar.d >= 10\").");

        COST_KEYS.put("money", "Dinero de la economía de Vault (expresión, > 0). Necesita Vault y un plugin de economía.");
        COST_KEYS.put("levels", "Niveles de experiencia (entero > 0).");
        COST_KEYS.put("exp", "Puntos de experiencia (entero > 0).");
        COST_KEYS.put("items", "Ítems por material, tengan o no nombre: \"MATERIAL;cantidad\" o una lista de esos (sin la armadura). Al devolver, vuelven las mismas pilas.");
        COST_KEYS.put("pvar.<nombre>", "Variable numérica del jugador (expresión, > 0); nula cuenta como 0.");
        COST_KEYS.put("gvar.<nombre>", "Variable numérica global (expresión, > 0); sirve también sin jugador.");

        COMMAND_KEYS.put("name", "Nombre del comando, sin la barra (letras, números, _ y -).");
        COMMAND_KEYS.put("aliases", "Otros nombres del comando.");
        COMMAND_KEYS.put("permission", "Permiso para usarlo (sin él, no aparece en el autocompletado).");
        COMMAND_KEYS.put("description", "Descripción que muestra /help.");
        COMMAND_KEYS.put("usage", "Uso que se muestra al equivocarse.");
        COMMAND_KEYS.put("tab", "Autocompletado: una lista por argumento; \"{players}\" son los jugadores conectados.");

        PACK_KEYS.put("name", "Nombre del paquete (por defecto, el de la carpeta): minúsculas, números, _ y -. Sus reglas se llaman nombre:regla.");
        PACK_KEYS.put("version", "Versión del paquete, x.y.z (1.0.0 por defecto). Al subirla corren las migrations que correspondan.");
        PACK_KEYS.put("description", "Qué hace el paquete (lo muestra /omni pack info).");
        PACK_KEYS.put("author", "Quién lo hizo.");
        PACK_KEYS.put("enabled", "false: sus reglas cargan desactivadas y no corren sus migraciones.");
        PACK_KEYS.put("params", "Parámetros de todas las reglas del paquete: ${nombre} en sus textos se reemplaza al cargar.");
        PACK_KEYS.put("requires", "Lo que necesita para cargar: omnievents y packs, con restricciones como \">=1.2.0, <2.0.0\".");
        PACK_KEYS.put("migrations", "Versión → acciones de servidor que corren una vez al subir a esa versión (var.from y var.to: de qué versión a cuál).");
        REQUIRES_KEYS.put("omnievents", "Versión de OmniEvents que necesita (p. ej. \">=1.6.0\").");
        REQUIRES_KEYS.put("packs", "Otros paquetes que necesita: nombre → restricción (p. ej. plantillas: \">=1.0.0\").");
    }

    public static Map<String, Option> all() {
        return OPTIONS;
    }

    public static Set<String> names() {
        return OPTIONS.keySet();
    }
}
