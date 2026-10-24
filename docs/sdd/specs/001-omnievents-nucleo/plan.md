# Plan 001 — Diseño técnico de OmniEvents

## 1. Arquitectura
```
dev.leo.omnievents
├─ OmniEvents            JavaPlugin: arranque, recarga, servicios
├─ api/                  OmniEventsAPI (registro de acciones, funciones y disparadores; call)
├─ expr/                 lenguaje: Lexer → Parser (Pratt) → Node (árbol inmutable) → evaluación
│   ├─ Values            coerciones (número, texto, booleano, igualdad laxa, Named para enums)
│   ├─ Properties        acceso a propiedades: alias por tipo + reflexión de solo lectura cacheada
│   └─ Functions         funciones incorporadas y registradas
├─ text/                 Template ({expr}, %papi%) y TextFormat (& → MiniMessage, escape, saneo de comandos)
├─ rule/                 Rule (compilada), RuleLoader (YAML → Rule + problemas), RuleManager (índice por disparador)
├─ trigger/              TriggerType, Triggers (catálogo), ListenerManager (registro bajo demanda), TimerService
├─ action/               Action, ActionNode (modificadores), Actions (catálogo), control de flujo, Execution (pila)
├─ data/                 DataStore (pvar, gvar, enfriamientos y usos; YAML atómico, guardado asíncrono)
├─ command/              OmniCommand (/omni), CommandRules (comandos declarados por reglas)
├─ debug/                Debugger (suscripciones y trazas), Stats
├─ hook/                 PapiHook + OmniExpansion (cargados solo si PlaceholderAPI está presente)
└─ importer/             ConditionalEventsImporter
```

Flujo de un disparo: `Listener (por evento y prioridad)` → `TriggerType.extract(evento)` → contexto →
`RuleManager.rules(disparador)` → por regla: filtros → condiciones → límite → enfriamiento → `Execution` sobre
`actions` (o `else`, `on_limit`, `on_cooldown`) → registro de uso y enfriamiento → estadísticas y trazas.

## 2. Contrato de configuración

### 2.1 `config.yml`
```yaml
config-version: 1
cooldown-bypass-op: false
expressions:
  allow-unsafe-methods: false   # métodos que no son get/is/has/can en expresiones
  max-loop-iterations: 1000     # tope de repeat/while/for_each por ejecución
  max-call-depth: 16            # tope de call anidados
storage:
  autosave-minutes: 5
placeholderapi: true            # registra %omni_...% si PlaceholderAPI está presente
```

### 2.2 Reglas (`rules/**/*.yml`)
```yaml
nombre_unico:
  trigger: block_break            # requerido (ver §2.6)
  enabled: true
  priority: normal                # lowest | low | normal | high | highest | monitor
  ignore_cancelled: false         # true: no corre si otro plugin ya canceló el evento
  worlds: [world, mineria]        # filtro rápido
  permission: omnievents.vip      # solo jugadores con este permiso
  bypass_permission: staff.bypass # los que lo tienen se ignoran
  conditions:                     # una expresión o lista (todas)
    - "block.type in ['DIAMOND_ORE', 'DEEPSLATE_DIAMOND_ORE']"
    - "!player.hasPermission('vip') || pvar.diamantes_hoy > 10"
  cooldown: 30s                   # 10s, 5m, 1h30m, 2d, 20t (ticks); número = segundos
  cooldown_scope: player          # player | global
  limit: 1                        # usos máximos
  limit_scope: player             # player | global
  limit_reset: daily              # never | daily | weekly | monthly | <duración>
  actions: [ ... ]                # condiciones verdaderas
  else: [ ... ]                   # condiciones falsas
  on_cooldown: [ ... ]            # en enfriamiento (var.cooldown_left en segundos)
  on_limit: [ ... ]               # límite alcanzado
  # propias de algunos disparadores
  every: 5s                       # timer
  per_player: true                # timer: una ejecución por jugador en línea
  at: ["08:00", "20:30"]          # clock
  timezone: America/Mexico_City   # clock
  move_mode: block                # player_move: block | chunk | any
  command:                        # command
    name: spawnvip
    aliases: [svip]
    permission: omnievents.cmd.spawnvip
    description: Ir al spawn VIP
    usage: /spawnvip [jugador]
    tab: [["{players}"]]          # sugerencias por argumento; {players} = jugadores en línea
  event: com.ejemplo.MiEvento     # custom: clase del evento
  player_from: player             # custom: ruta de propiedad al jugador (opcional)
  args: [cantidad, motivo]        # call: parámetros declarados (var.cantidad, var.motivo)
```
Orden de evaluación: `enabled` → `worlds` → `permission` → `bypass_permission` → `conditions` (`else`) → `limit`
(`on_limit`) → `cooldown` (`on_cooldown`) → `actions` → registrar uso y enfriamiento (salvo `no_cooldown`).

### 2.3 Lenguaje de expresiones
Precedencia (de menor a mayor):
| Nivel | Operadores |
|---|---|
| 1 | `c ? a : b` |
| 2 | `\|\|` `or` |
| 3 | `&&` `and` |
| 4 | `==` `!=` `~=` `!~=` |
| 5 | `<` `<=` `>` `>=` `contains` `startsWith` `endsWith` `matches` `in` (y `!contains`, `!startsWith`, `!endsWith`, `!matches`, `not in`) |
| 6 | `+` `-` (con texto, `+` concatena) |
| 7 | `*` `/` `%` |
| 8 | unarios `!` `not` `-` |
| 9 | posfijos `.prop` `[índice]` `.metodo(args)` |

Primarios: números, `'texto'`/`"texto"`, `true`, `false`, `null`, listas `[a, b]`, `(expr)`, identificadores,
`funcion(args)`, `%placeholder%` (PlaceholderAPI del jugador del contexto; si el resultado es numérico, es número).
Espacios de nombres: `var.x` (ejecución), `pvar.x` (jugador del contexto), `gvar.x` (servidor).
Semántica: `==` compara números como números y enums con texto sin distinguir mayúsculas; `&&`/`||` cortocircuitan;
comparar orden con nulo es falso; división por cero es error de ejecución (H9 CA5); `in` acepta lista, texto o mapa.

Funciones: `random(a,b)` `randomf(a,b)` `chance(p)` `min` `max` `abs` `round(x[,d])` `floor` `ceil` `sqrt` `pow`
`clamp(x,a,b)` `len` `lower` `upper` `trim` `replace(s,a,b)` `substr(s,a[,b])` `split(s,sep)` `join(l,sep)` `str` `num`
`int` `bool` `strip(s)` `format(n,d)` `if(c,a,b)` `papi(texto[,jugador])` `online(nombre)` `player(nombre)` `players()`
`perm(p)` `now()` `hour()` `minute()` `weekday()` `date([patron])` `cooldown(regla)` `uses(regla)` `distance(a,b)`
`in_area(mundo,x1,y1,z1,x2,y2,z2)` `has_item(material[,cantidad])` `pvar_of(jugador,nombre)` `size(x)`.

### 2.4 Textos
`{expr}` interpola el resultado de una expresión (`\{` escribe una llave), `%x%` pasa por PlaceholderAPI y luego se
aplican códigos `&`/`§`, `&#RRGGBB` y MiniMessage. Lo interpolado se **escapa** (no puede abrir etiquetas MiniMessage).
En comandos, lo interpolado pierde saltos de línea y `§`. Un `Location` se escribe como `mundo;x;y;z;yaw;pitch`.

### 2.5 Acciones
Forma mapa (`- message: "hola"` + modificadores) o texto (`- "message: hola"`). Modificadores en cualquier acción:
`to`, `where`, `chance`, `when`. Control de flujo:
```yaml
- if: "player.level >= 10"
  then: [ ... ]
  else: [ ... ]
- repeat: 3
  do: [ ... ]                 # var.i = índice (0..n-1)
- while: "var.n < 5"
  do: [ ... ]                 # tope max-loop-iterations
- for_each: all               # all | world:<w> | radius:<n> | expresión que da lista
  as: p                       # opcional; sin as, 'player' pasa a ser el elemento y 'source' el original
  do: [ ... ]
- random:
    - weight: 70
      do: [ ... ]
    - weight: 30
      do: [ ... ]
- delay: 2s
- call: "recompensa; cantidad=5; motivo='evento'"
- set: "pvar.monedas += 5"
- unset: pvar.monedas
- stop
- no_cooldown
```
Catálogo y sintaxis de valores (`;` separa parámetros, todos interpolables):
| Acción | Valor |
|---|---|
| `message`, `broadcast`, `actionbar`, `console_message`, `log` | texto |
| `title` | `título;subtítulo[;entrada;estancia;salida]` (ticks) |
| `bossbar` | `texto;segundos[;color;progreso]` |
| `sound` | `SONIDO[;volumen;tono]` · `stop_sound`: `SONIDO\|all` |
| `console`, `command`, `chat` | comando o texto |
| `send_to_server` | servidor (BungeeCord/Velocity) |
| `teleport` | `mundo;x;y;z[;yaw;pitch]` |
| `heal`, `damage`, `feed`, `give_exp`, `give_level` | número |
| `gamemode` | modo · `fly`: `true\|false` · `fire`: ticks · `kick`: motivo |
| `effect` | `EFECTO;segundos;nivel[;partículas]` · `remove_effect`: `EFECTO\|all` |
| `velocity` | `x;y;z` · `launch`: potencia hacia donde mira |
| `give_item`, `drop_item` | `MATERIAL[;cantidad;nombre;lore1\|lore2]`, mapa (`material, amount, name, lore, model, enchants, glow, unbreakable, location`) o `saved:<nombre>` |
| `remove_item` | `MATERIAL;cantidad` · `clear_inventory`, `close_inventory`: sin valor |
| `set_block` | `MATERIAL` en el bloque del evento, o `mundo;x;y;z;MATERIAL` |
| `lightning`, `explosion` | `[mundo;x;y;z]` (vacío = posición del jugador) · `explosion`: `potencia[;fuego;romper]` |
| `particle` | `PARTÍCULA[;cantidad;dx;dy;dz;velocidad]` |
| `summon` | `ENTIDAD[;mundo;x;y;z]` |
| `cancel` | `true\|false` (vacío = true) |
| `set_event` | `propiedad = expresión` (setter de la API) |
| `set_damage` | expresión · `set_message`, `set_join_message`, `set_quit_message`, `set_death_message`, `deny_join`: texto |
| `keep_inventory`, `cancel_drops` | `true\|false` |

### 2.6 Disparadores (raíces disponibles además de `player`, `event`, `world`, `location`)
| Grupo | Disparadores |
|---|---|
| Jugador | `player_join`, `player_quit`, `player_pre_login` (sin jugador: `name`, `uuid`, `ip`), `player_respawn`, `player_death` (`killer`), `player_chat` (`message`), `player_command` (`cmd`, `args`), `player_move`, `player_teleport`, `player_world_change`, `player_sneak`, `player_sprint`, `player_jump`, `player_level_change`, `player_gamemode_change`, `player_toggle_flight`, `player_bed_enter`, `player_fish` (`target`), `player_regain_health`, `player_food_change`, `player_advancement`, `player_swap_hands` (`item`), `player_armor_change` (`item`), `player_portal`, `player_kick`, `player_resource_pack` |
| Combate | `player_damage` (`damager`), `player_attack` (`target`, `item`), `player_kill` (`target`, `item`), `player_shoot` (`item`, `projectile`), `projectile_hit` (`target`, `block`, `projectile`) |
| Bloques | `block_break`, `block_place`, `block_interact` (`block`, `item`, `action`), `sign_change` (`lines`), `bucket_fill`, `bucket_empty` |
| Ítems | `item_interact` (`item`, `action`), `item_consume`, `item_craft`, `item_drop`, `item_pickup`, `item_held` (`item`, `previous_item`), `item_enchant`, `item_break` |
| Inventario | `inventory_open`, `inventory_close`, `inventory_click` (`item`, `cursor`, `slot`, `click`, `title`) |
| Entidades | `entity_interact` (`target`), `entity_spawn` (`entity`), `entity_death` (`entity`, `killer`) |
| Servidor | `console_command` (`cmd`, `args`), `server_start`, `server_stop`, `timer`, `clock`, `call`, `command`, `custom` |

## 3. Hilos y rendimiento
- Todo se compila en la carga: expresiones (árbol), plantillas (segmentos), acciones (objetos). En el disparo no se
  analiza texto.
- `ListenerManager` registra un `EventExecutor` por (clase de evento, prioridad) usada por reglas habilitadas, y los
  desregistra todos al recargar (`HandlerList.unregisterAll(listener)`).
- `Execution` es una pila de marcos (bloque, repeat, while, for_each); `delay` suspende y reanuda con el scheduler,
  sin hilos bloqueados. Tope de iteraciones y de profundidad de `call` (constitución III.4).
- Eventos asíncronos (`player_chat`, `player_pre_login`): condiciones y acciones *seguras en asíncrono* (`cancel`,
  `set_*`, `deny_join`, `set`, `unset`, `message`, `log`, control de flujo) corren en el hilo del evento; en la
  primera acción que no lo es, el resto de la ejecución pasa al hilo principal. Validación advierte `cancel` o `set_*`
  después de una acción no segura en un disparador asíncrono.
- `Properties` cachea por (clase, nombre) el método resuelto; la reflexión solo ocurre la primera vez.

## 4. Persistencia
`plugins/OmniEvents/data/global.yml` y `data/players/<uuid>.yml`:
```yaml
vars: { monedas: 25 }
cooldowns: { regla: 1761350000000 }          # vence (epoch ms)
uses: { regla: { count: 3, since: 1761300000000 } }
```
Carga de jugadores en `AsyncPlayerPreLoginEvent` (fuera del hilo principal); al habilitar, la de los conectados.
Guardado: marca de sucio, instantánea en el hilo principal, escritura asíncrona en `*.tmp` + `ATOMIC_MOVE`. Al salir
un jugador se guarda y se libera; al deshabilitar, guardado síncrono.

## 5. Amenazas (STRIDE)
| Amenaza | Escenario | Mitigación |
|---|---|---|
| Suplantación | Un jugador se llama como un comando o usa `§` en su nombre de ítem para fingir un mensaje del sistema | Interpolación escapada (§2.4); `strip()` disponible |
| Manipulación | Un jugador escribe `<click:run_command:/op yo>` en el chat y una regla lo repite | `{message}` se escapa: MiniMessage no lo interpreta (H4 CA6) |
| Manipulación | Nombre de ítem con salto de línea interpolado en `console` ejecuta un segundo comando | En comandos se eliminan `\n`, `\r` y `§` (§2.4) |
| Repudio | Un admin cambia variables de jugadores | `/omni var set` queda en el log del servidor con quién lo hizo |
| Divulgación | `/omni eval` revela datos de otros jugadores | Permiso `omnievents.command.eval` solo OP |
| Denegación de servicio | `while: true` o `call` recursivo congela el servidor | `max-loop-iterations`, `max-call-depth`; error registrado, ejecución detenida |
| Denegación de servicio | Regla de `player_move` costosa | `move_mode: block` por defecto; `stats` muestra el costo |
| Elevación de privilegios | Expresión `player.setOp(true)` | Solo métodos `get/is/has/can` (ADR-0003); `allow-unsafe-methods` apagado |
| Elevación de privilegios | `player_command_as_op` de ConditionalEvents | No existe en OmniEvents; el importador lo marca `# REVISAR` |
