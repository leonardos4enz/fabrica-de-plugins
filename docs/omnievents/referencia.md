# Referencia de OmniEvents

> Generada del código con `./gradlew :omnievents:schema` (o `/omni schema` en el servidor): no se edita a mano.
> Una prueba falla si queda desactualizada. Guía y ejemplos: [README](../../plugins/omnievents/README.md).

Autocompletado y errores en VS Code (extensión YAML de Red Hat): pon esta línea al inicio de cada archivo de reglas.

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/docs/omnievents/omnievents.schema.json
```

El editor revisa opciones, disparadores, acciones y la forma de las condiciones; las expresiones, los materiales, los sonidos y los efectos se revisan con `/omni validate`. Los nombres van en minúsculas.

Contenido: [opciones de una regla](#opciones-de-una-regla) · [condiciones](#condiciones) · [disparadores](#disparadores) · [acciones](#acciones) · [funciones](#funciones)

## Opciones de una regla
| Opción | Valor | Disparador | Qué hace |
|---|---|---|---|
| `trigger` | disparador | todos | Disparador: el evento que hace correr la regla (/omni triggers los lista). |
| `type` | disparador (sinónimo de `trigger`) | todos | Sinónimo de trigger. Disparador: el evento que hace correr la regla (/omni triggers los lista). |
| `description` | texto | todos | Nota libre para quien lee la regla; el plugin no la usa. |
| `enabled` | `true` / `false` | todos | false apaga la regla sin borrarla. |
| `priority` | `lowest`, `low`, `normal`, `high`, `highest`, `monitor` | todos | Prioridad del oyente de Bukkit (normal por defecto). |
| `ignore_cancelled` | `true` / `false` | todos | true: no corre si otro plugin ya canceló el evento. |
| `worlds` | texto o lista | todos | Mundo o lista de mundos donde corre la regla (sin la opción, en todos). |
| `permission` | texto | todos | Permiso que necesita el jugador para que la regla corra. |
| `bypass_permission` | texto | todos | Con este permiso, la regla ignora al jugador (no corre para él). |
| `ignore_with_permission` | texto (sinónimo de `bypass_permission`) | todos | Sinónimo de bypass_permission. Con este permiso, la regla ignora al jugador (no corre para él). |
| `conditions` | condiciones | todos | Una condición o una lista (todas deben cumplirse). Cada una es una expresión, o un mapa con expr o any + of y su propio deny. |
| `if` | condiciones (sinónimo de `conditions`) | todos | Sinónimo de conditions. Una condición o una lista (todas deben cumplirse). Cada una es una expresión, o un mapa con expr o any + of y su propio deny. |
| `actions` | acciones | todos | Acciones que corren si se cumplen las condiciones. |
| `then` | acciones (sinónimo de `actions`) | todos | Sinónimo de actions. Acciones que corren si se cumplen las condiciones. |
| `else` | acciones | todos | Acciones que corren si falla una condición sin deny. |
| `cooldown` | duración | todos | Enfriamiento entre usos: 30s, 5m, 1h30m, 2d, 20t (ticks) o un número de segundos. |
| `cooldown_scope` | `player`, `jugador`, `global`, `server`, `servidor` | todos | player (por jugador, por defecto) o global (uno para todo el servidor). |
| `on_cooldown` | acciones | todos | Acciones si la regla está en enfriamiento (var.cooldown_left tiene los segundos que faltan). |
| `limit` | entero | todos | Usos máximos (0 = sin límite). |
| `one_time` | `true` / `false` | todos | true equivale a limit: 1 (el one_time de ConditionalEvents). |
| `limit_scope` | `player`, `jugador`, `global`, `server`, `servidor` | todos | player (por jugador, por defecto) o global. |
| `limit_reset` | `never`, `nunca`, `daily`, `diario`, `weekly`, `semanal`, `monthly`, `mensual` o una duración | todos | Cuándo se reinician los usos: never, daily, weekly, monthly o una duración (12h). |
| `on_limit` | acciones | todos | Acciones si ya no quedan usos. |
| `budget` | `0.5ms`, `200us` | todos | Presupuesto de tiempo por disparo: 0.5ms o 200us. Avisa en consola si el promedio reciente lo pasa. |
| `every` | duración | `timer` | Cada cuánto corre el timer (10s, 5m; mínimo 1 tick). |
| `repetitive_time` | entero | `timer` | Ticks entre ejecuciones (forma de ConditionalEvents; mejor every). |
| `per_player` | `true` / `false` | `timer, state` | true (por defecto): una ejecución (o un estado) por cada jugador conectado; false: una sola, sin jugador. |
| `at` | `HH:mm` o lista | `clock` | Hora u horas del día, HH:mm ("08:00" o ["08:00", "20:30"]). |
| `timezone` | texto | `clock` | Zona horaria de at (p. ej. America/Mexico_City); por defecto, la del servidor. |
| `command` | nombre o mapa | `command` | El comando real que registra la regla: un nombre, o un mapa con name, aliases, permission, description, usage y tab. |
| `when` | condiciones | `state` | El estado: una expresión o una lista de condiciones (todas). Se revisa solo cuando puede haber cambiado. |
| `on_enter` | acciones | `state` | Acciones cuando when pasa a ser verdad (una vez; pasan por permiso, límite y enfriamiento). |
| `on_exit` | acciones | `state` | Acciones cuando when deja de ser verdad, si corrió su on_enter (var.state_seconds: cuánto duró). |
| `check_every` | duración | `state` | Respaldo por intervalo para lo que no se puede deducir (placeholders, perm(), la hora): 5s, 1m. |
| `watch` | texto o lista | `state` | Disparadores extra que hacen revisar el estado (p. ej. [player_interact]). |
| `initial` | `enter`, `silent` | `state` | Al entrar al servidor con el estado ya verdadero: enter (corre on_enter, por defecto) o silent. |
| `event` | texto | `custom` | Nombre completo de la clase del evento (p. ej. org.bukkit.event.player.PlayerEggThrowEvent). |
| `player_from` | texto | `custom` | Ruta al jugador dentro del evento (p. ej. getPlayer o entity); por defecto player o entity. |
| `move_mode` | `block`, `chunk`, `any` | `player_move` | Cuándo cuenta un movimiento: block (cambia de bloque, por defecto), chunk o any. |
| `args` | lista de nombres | `call` | Parámetros de la regla call; llegan como var.&lt;nombre>. |

Bloque `command` (para `trigger: command`):

| Clave | Qué hace |
|---|---|
| `name` | Nombre del comando, sin la barra (letras, números, _ y -). |
| `aliases` | Otros nombres del comando. |
| `permission` | Permiso para usarlo (sin él, no aparece en el autocompletado). |
| `description` | Descripción que muestra /help. |
| `usage` | Uso que se muestra al equivocarse. |
| `tab` | Autocompletado: una lista por argumento; "{players}" son los jugadores conectados. |

## Condiciones
Cada elemento de `conditions` es una expresión, o un mapa con estas claves:

| Clave | Qué hace |
|---|---|
| `expr` | Expresión que debe cumplirse. |
| `any` | Cuántas de las condiciones de of deben cumplirse como mínimo (entero entre 1 y el tamaño de of). |
| `of` | Condiciones del grupo any (textos o mapas; se puede anidar otro any). |
| `deny` | Acciones si esta condición es la primera que falla; reemplazan al else de la regla. Dentro de of se ignoran. |

```yaml
conditions:
  - "player != null"
  - expr: "player.level >= 5"
    deny:
      - message: "<red>Necesitas nivel 5."
  - any: 2
    of: ["perm('omnievents.vip')", "has_item('EMERALD', 1)", "player.world.time > 12000"]
```

## Disparadores
Raíces que todos ofrecen además de las suyas: `server` `rule` `online` `source` `target` `item` `block` `entity`.

### Jugador
| Disparador | Raíces | Qué es |
|---|---|---|
| `player_join` | `event` `world` `location` `player` `first_join` | Un jugador entra al servidor (first_join: si es su primera vez). |
| `player_quit` | `event` `world` `location` `player` `reason` | Un jugador sale del servidor. |
| `player_pre_login` | `event` `world` `location` `name` `uuid` `ip` | Antes de entrar, en un hilo asíncrono: aún no hay jugador, solo name, uuid e ip (deny_join lo rechaza). *(asíncrono)* |
| `player_respawn` | `event` `world` `location` `player` `reason` | Un jugador reaparece tras morir. |
| `player_death` | `event` `world` `location` `player` `killer` `cause` | Un jugador muere (killer: quien lo mató, si fue otro jugador). *(se puede cancelar)* |
| `player_chat` | `event` `world` `location` `player` `message` | Un jugador escribe en el chat (asíncrono; message es el texto, sin formato). *(se puede cancelar, asíncrono)* |
| `player_command` | `event` `world` `location` `player` `cmd` `args` | Un jugador escribe un comando, de cualquier plugin (cmd.name, args). *(se puede cancelar)* |
| `player_move` | `event` `world` `location` `player` `from` `to` | Un jugador se mueve (move_mode: block, chunk o any). *(se puede cancelar)* |
| `player_teleport` | `event` `world` `location` `player` `from` `to` `cause` | Un jugador es teletransportado. *(se puede cancelar)* |
| `player_world_change` | `event` `world` `location` `player` `from_world` | Un jugador cambia de mundo (from_world: de dónde viene). |
| `player_sneak` | `event` `world` `location` `player` `sneaking` | Un jugador empieza o deja de agacharse (sneaking). *(se puede cancelar)* |
| `player_sprint` | `event` `world` `location` `player` `sprinting` | Un jugador empieza o deja de correr (sprinting). *(se puede cancelar)* |
| `player_jump` | `event` `world` `location` `player` `from` `to` | Un jugador salta. *(se puede cancelar)* |
| `player_level_change` | `event` `world` `location` `player` `old_level` `new_level` | Cambia el nivel de experiencia de un jugador. |
| `player_gamemode_change` | `event` `world` `location` `player` `new_mode` | Un jugador cambia de modo de juego (new_mode). *(se puede cancelar)* |
| `player_toggle_flight` | `event` `world` `location` `player` `flying` | Un jugador empieza o deja de volar (flying). *(se puede cancelar)* |
| `player_bed_enter` | `event` `world` `location` `player` `block` `result` | Un jugador intenta acostarse en una cama. *(se puede cancelar)* |
| `player_fish` | `event` `world` `location` `player` `target` `state` | Un jugador pesca (state: estado de la caña; target: lo que sacó). *(se puede cancelar)* |
| `player_regain_health` | `event` `world` `location` `player` `amount` `reason` | Un jugador recupera vida (amount, reason). *(se puede cancelar)* |
| `player_food_change` | `event` `world` `location` `player` `old_food` `new_food` | Cambia el hambre de un jugador (old_food, new_food). *(se puede cancelar)* |
| `player_advancement` | `event` `world` `location` `player` `advancement` | Un jugador consigue un logro (sin las recetas). |
| `player_swap_hands` | `event` `world` `location` `player` `item` `offhand` | Un jugador cambia los ítems de mano (tecla F). *(se puede cancelar)* |
| `player_armor_change` | `event` `world` `location` `player` `item` `old_item` `slot` | Un jugador se pone o se quita una pieza de armadura. |
| `player_portal` | `event` `world` `location` `player` `from` `to` `cause` | Un jugador usa un portal. *(se puede cancelar)* |
| `player_kick` | `event` `world` `location` `player` `reason` | Un jugador es expulsado del servidor. *(se puede cancelar)* |
| `player_resource_pack` | `event` `world` `location` `player` `status` | Un jugador responde al paquete de recursos (status). |

### Combate
| Disparador | Raíces | Qué es |
|---|---|---|
| `player_damage` | `event` `world` `location` `player` `cause` `damage` `damager` | Un jugador recibe daño (cause; damager si fue una entidad o un jugador, también con proyectil). *(se puede cancelar)* |
| `player_attack` | `event` `world` `location` `player` `target` `damage` `projectile` `item` | Un jugador golpea a una entidad, directo o con un proyectil (target, damage). *(se puede cancelar)* |
| `player_kill` | `event` `world` `location` `player` `target` `item` | Un jugador mata a una entidad (target). *(se puede cancelar)* |
| `player_shoot` | `event` `world` `location` `player` `item` `projectile` `force` | Un jugador dispara un arco o una ballesta (force). *(se puede cancelar)* |
| `projectile_hit` | `event` `world` `location` `player` `target` `block` `projectile` | El proyectil de un jugador pega en una entidad o un bloque. *(se puede cancelar)* |

### Bloques
| Disparador | Raíces | Qué es |
|---|---|---|
| `block_break` | `event` `world` `location` `player` `block` `item` | Un jugador rompe un bloque. *(se puede cancelar)* |
| `block_place` | `event` `world` `location` `player` `block` `against` `item` | Un jugador coloca un bloque (against: el bloque en que se apoya). *(se puede cancelar)* |
| `block_interact` | `event` `world` `location` `player` `block` `action` `face` `item` | Un jugador hace clic en un bloque con la mano principal (action: RIGHT_CLICK, SHIFT_LEFT_CLICK…). *(se puede cancelar)* |
| `sign_change` | `event` `world` `location` `player` `lines` `block` | Un jugador escribe un cartel (lines). *(se puede cancelar)* |
| `bucket_fill` | `event` `world` `location` `player` `block` `item` | Un jugador llena un cubo. *(se puede cancelar)* |
| `bucket_empty` | `event` `world` `location` `player` `block` `bucket` | Un jugador vacía un cubo. *(se puede cancelar)* |

### Ítems
| Disparador | Raíces | Qué es |
|---|---|---|
| `player_interact` | `event` `world` `location` `player` `action` `block` `item` | Un jugador hace clic, con o sin bloque, con la mano principal (action). *(se puede cancelar)* |
| `item_interact` | `event` `world` `location` `player` `item` `action` `block` | Un jugador hace clic con un ítem en la mano principal (action). *(se puede cancelar)* |
| `item_consume` | `event` `world` `location` `player` `item` | Un jugador come o bebe un ítem. *(se puede cancelar)* |
| `item_craft` | `event` `world` `location` `player` `item` `click` | Un jugador fabrica un ítem (item: el resultado). *(se puede cancelar)* |
| `item_drop` | `event` `world` `location` `player` `item` `entity` | Un jugador tira un ítem. *(se puede cancelar)* |
| `item_pickup` | `event` `world` `location` `player` `item` `entity` | Un jugador recoge un ítem del suelo. *(se puede cancelar)* |
| `item_held` | `event` `world` `location` `player` `item` `previous_item` `slot` | Un jugador cambia de ranura en la barra (item: el nuevo, previous_item: el anterior). *(se puede cancelar)* |
| `item_enchant` | `event` `world` `location` `player` `item` `enchants` `cost` | Un jugador encanta un ítem en la mesa (enchants, cost). *(se puede cancelar)* |
| `item_break` | `event` `world` `location` `player` `item` | Se rompe la herramienta o la armadura de un jugador. |

### Inventario
| Disparador | Raíces | Qué es |
|---|---|---|
| `inventory_open` | `event` `world` `location` `player` `title` `inventory_type` | Un jugador abre un inventario (title, inventory_type). *(se puede cancelar)* |
| `inventory_close` | `event` `world` `location` `player` `title` `inventory_type` | Un jugador cierra un inventario. |
| `inventory_click` | `event` `world` `location` `player` `item` `cursor` `slot` `raw_slot` `click` `title` `inventory_type` | Un jugador hace clic en un inventario (slot, raw_slot, click, cursor, title). *(se puede cancelar)* |

### Entidades
| Disparador | Raíces | Qué es |
|---|---|---|
| `entity_interact` | `event` `world` `location` `player` `target` `item` | Un jugador hace clic derecho en una entidad (target). *(se puede cancelar)* |
| `entity_spawn` | `event` `world` `location` `entity` `reason` | Aparece una criatura (entity, reason); sin jugador. *(se puede cancelar)* |
| `entity_death` | `event` `world` `location` `player` `entity` `killer` | Muere una entidad (entity; player y killer si la mató un jugador). *(se puede cancelar)* |

### Servidor
| Disparador | Raíces | Qué es |
|---|---|---|
| `console_command` | `event` `world` `location` `cmd` `args` `sender` | La consola ejecuta un comando (cmd, args, sender). *(se puede cancelar)* |
| `server_start` | — | Al arrancar el servidor, cuando OmniEvents termina de cargar. |
| `server_stop` | — | El servidor se apaga. |
| `timer` | `player` `world` `location` | Cada cierto tiempo (every); por defecto una vez por jugador conectado (per_player). |
| `clock` | `player` `world` `location` `time` | A una hora del día (at: HH:mm, timezone). |
| `call` | `player` `event` `world` `location` `sender` `caller` | Solo corre cuando otra regla la llama (call) o con /omni call; sus args llegan como var.&lt;nombre>. |
| `command` | `player` `sender` `cmd` `args` `world` `location` | Registra un comando real (command: name, aliases, permission, tab) y corre al usarlo. |
| `state` | `player` `world` `location` | Cuando la expresión de when pasa a ser verdad (on_enter) o deja de serlo (on_exit), por jugador; sin sondeo: se revisa solo con los eventos de los que depende. |
| `custom` | `player` `event` `world` `location` | Cualquier evento de Bukkit o de otro plugin por su clase (event, player_from). |

Alias: `player_kill_entity` → `player_kill`, `player_leave` → `player_quit`, `repetitive` → `timer`, `repetitive_server` → `timer`.

## Acciones
Dos formas equivalentes: `- message: hola` (mapa) o `- "message: hola"` (texto). Modificadores de cualquier acción (salvo `to` en las de flujo):

| Modificador | Qué hace |
|---|---|
| `to` | A quién se aplica: self, target, all, world:&lt;mundo>, radius:&lt;n>, nearby:&lt;n>, player:&lt;nombre> o una expresión. |
| `where` | Filtro por destinatario de to (expresión; player es cada destinatario). |
| `chance` | Probabilidad de que corra, en porcentaje (0 a 100). |
| `when` | Guarda: la acción solo corre si la expresión se cumple. |

### Flujo

#### `call`
Corre otra regla (trigger: call) con argumentos, que llegan como var.&lt;nombre>.

Valor: regla; nombre=expresión… o un mapa con rule y args.

En forma de mapa:
- `rule`: Regla que se llama.
- `args`: Mapa nombre: expresión con los argumentos.

```yaml
- call: "sumar_monedas; jugador=player.name; cantidad=10"
```

#### `delay`
Espera antes de seguir con las acciones siguientes.

Valor: duración (10s, 5m, 20t).

```yaml
- delay: 2s
```

#### `for_each`
Corre do para cada destinatario; dentro, player es cada uno y source el original (con as, el destinatario queda en ese nombre). var.index es la vuelta.

Valor: destinatarios (all, world:&lt;mundo>, radius:&lt;n>, nearby:&lt;n> o una expresión).

Claves:
- `do`: Acciones para cada destinatario.
- `as`: Nombre de la raíz con el destinatario (sin as: player y source).

```yaml
- for_each: "radius:10"
  as: cercano
  do:
    - message: "{cercano.name} está cerca"
```

#### `if`
Si la expresión se cumple corre then; si no, else.

Valor: expresión.

Claves:
- `then`: Acciones si se cumple.
- `else`: Acciones si no se cumple.

```yaml
- if: "player.level >= 10"
  then:
    - message: "<green>Nivel alto"
  else:
    - message: "<gray>Sube de nivel"
```

#### `no_cooldown`
Esta ejecución no cuenta para el enfriamiento ni para el límite.

Valor: (sin valor).

```yaml
- no_cooldown
```

#### `random`
Elige una rama al azar según su weight (1 por defecto) y corre su do.

Valor: lista de ramas con weight y do.

Cada rama:
- `weight`: Peso de la rama (1 por defecto).
- `do`: Acciones de la rama.

```yaml
- random:
    - weight: 3
      do:
        - message: "Común"
    - weight: 1
      do:
        - message: "Rara"
```

#### `repeat`
Repite do N veces; la vuelta (desde 0) está en var.i, o en var.&lt;as>.

Valor: expresión (veces).

Claves:
- `do`: Acciones de cada vuelta.
- `as`: Nombre de la variable con el número de vuelta (i por defecto).

```yaml
- repeat: 3
  as: vuelta
  do:
    - message: "Vuelta {var.vuelta}"
```

#### `reset_cooldown`
Reinicia el enfriamiento de una regla para el jugador (o el global, sin jugador).

Valor: regla.

```yaml
- reset_cooldown: bienvenida
```

#### `set`
Asigna una variable: var (de la ejecución), pvar (del jugador, persistente) o gvar (global, persistente); también +=, -=, *= y /=.

Valor: ámbito.nombre = expresión.

```yaml
- set: "pvar.monedas += 10"
```

#### `stop`
Detiene las acciones que faltan de esta ejecución.

Valor: (sin valor).

```yaml
- stop
```

#### `unset`
Borra una variable.

Valor: ámbito.nombre.

```yaml
- unset: "pvar.monedas"
```

#### `wait_ticks`
Espera N ticks (20 = 1 segundo).

Valor: expresión (ticks).

```yaml
- wait_ticks: 40
```

#### `while`
Repite do mientras la expresión se cumpla, con tope de iteraciones (config.yml).

Valor: expresión.

Claves:
- `do`: Acciones de cada vuelta.

```yaml
- while: "default(var.n, 0) < 3"
  do:
    - set: "var.n += 1"
```

### Mensajes

#### `actionbar`
Muestra un texto en la barra de acción.

Valor: texto.

```yaml
- actionbar: "<aqua>Diamantes: {pvar.diamantes}"
```

#### `bossbar`
Muestra una barra de jefe por unos segundos.

Valor: texto;segundos;color;progreso (0 a 1).

```yaml
- bossbar: "<red>El Guardián;10;RED;0.65"
```

#### `broadcast`
Envía un mensaje a todo el servidor.

Valor: texto.

```yaml
- broadcast: "<gold>{player.name} encontró un tesoro"
```

#### `console_message`
Escribe un mensaje en la consola.

Valor: texto.

```yaml
- console_message: "La regla corrió para {player.name}"
```

#### `log`
Escribe en el log del servidor, con el nombre de la regla.

Valor: texto.

```yaml
- log: "{player.name} usó la regla"
```

#### `message`
Envía un mensaje al jugador (sin jugador, a quien ejecutó o a la consola). Acepta MiniMessage, & y {expresiones}.

Valor: texto.

```yaml
- message: "<green>Hola, {player.name}"
```

#### `sound`
Reproduce un sonido al jugador (o en la ubicación); también acepta claves como minecraft:block.note_block.pling.

Valor: SONIDO;volumen;tono.

```yaml
- sound: "ENTITY_PLAYER_LEVELUP;1;1.2"
```

#### `stop_sound`
Detiene un sonido del jugador, o todos (all o sin valor).

Valor: SONIDO o all.

```yaml
- stop_sound: all
```

#### `title`
Muestra un título y un subtítulo (none deja uno vacío); los tiempos van en ticks.

Valor: título;subtítulo;entrada;estancia;salida.

```yaml
- title: "<gold>¡Victoria!;<gray>Ronda 3;10;70;20"
```

### Comandos

#### `chat`
Hace que el jugador escriba en el chat.

Valor: texto.

```yaml
- chat: "¡Hola a todos!"
```

#### `command`
Hace que el jugador ejecute un comando.

Valor: comando.

```yaml
- command: "spawn"
```

#### `console`
Ejecuta un comando como la consola (sin la barra). Lo que escribió el jugador pierde los saltos de línea.

Valor: comando.

```yaml
- console: "give {player.name} diamond 1"
```

#### `send_to_server`
Manda al jugador a otro servidor de la red (BungeeCord o Velocity).

Valor: servidor.

```yaml
- send_to_server: lobby
```

### Jugador

#### `clear_inventory`
Vacía el inventario.

Valor: (sin valor).

```yaml
- clear_inventory
```

#### `close_inventory`
Cierra el inventario abierto.

Valor: (sin valor).

```yaml
- close_inventory
```

#### `damage`
Hace daño.

Valor: expresión.

```yaml
- damage: 4
```

#### `drop_item`
Suelta un ítem en el suelo, donde está el jugador o en location.

Valor: como give_item.

En forma de mapa:
- `material`: Material (DIAMOND, IRON_SWORD…).
- `id`: Sinónimo de material.
- `amount`: Cantidad.
- `name`: Nombre (MiniMessage, & y {expresiones}).
- `lore`: Líneas de descripción: una lista, o un texto con |.
- `model`: custom_model_data.
- `enchants`: Encantamientos: mapa encantamiento: nivel, o lista "SHARPNESS:2".
- `glow`: Brillo de encantado sin encantamientos.
- `unbreakable`: Irrompible.
- `hide_flags`: Oculta los atributos.
- `saved`: Ítem guardado con /omni item save.

Claves:
- `location`: Dónde soltarlo: mundo;x;y;z (~ relativo).

```yaml
- drop_item: "GOLD_INGOT;3"
  location: "world;0;64;0"
```

#### `effect`
Da un efecto de poción (segundos negativos: infinito).

Valor: EFECTO;segundos;nivel;partículas.

```yaml
- effect: "SPEED;30;2;false"
```

#### `feed`
Da comida (sin valor, la llena).

Valor: expresión.

```yaml
- feed: 6
```

#### `fire`
Prende fuego por N ticks.

Valor: expresión (ticks).

```yaml
- fire: 60
```

#### `fly`
Permite volar o lo quita (sin valor, lo permite).

Valor: expresión (true o false).

```yaml
- fly: true
```

#### `freeze`
Congela (como la nieve en polvo) por N ticks.

Valor: expresión (ticks).

```yaml
- freeze: 140
```

#### `gamemode`
Cambia el modo de juego.

Valor: SURVIVAL, CREATIVE, ADVENTURE o SPECTATOR.

```yaml
- gamemode: CREATIVE
```

#### `give_exp`
Da puntos de experiencia.

Valor: expresión.

```yaml
- give_exp: 100
```

#### `give_item`
Da un ítem; lo que no cabe cae al suelo.

Valor: MATERIAL;cantidad;nombre;lore1|lore2, saved:&lt;nombre> o un mapa.

En forma de mapa:
- `material`: Material (DIAMOND, IRON_SWORD…).
- `id`: Sinónimo de material.
- `amount`: Cantidad.
- `name`: Nombre (MiniMessage, & y {expresiones}).
- `lore`: Líneas de descripción: una lista, o un texto con |.
- `model`: custom_model_data.
- `enchants`: Encantamientos: mapa encantamiento: nivel, o lista "SHARPNESS:2".
- `glow`: Brillo de encantado sin encantamientos.
- `unbreakable`: Irrompible.
- `hide_flags`: Oculta los atributos.
- `saved`: Ítem guardado con /omni item save.

```yaml
- give_item: "DIAMOND;2;<aqua>Diamante;<gray>Premio"
```

#### `give_level`
Da niveles de experiencia.

Valor: expresión.

```yaml
- give_level: 5
```

#### `heal`
Cura (sin valor, por completo).

Valor: expresión (vida).

```yaml
- heal: 10
```

#### `kick`
Expulsa al jugador con un mensaje.

Valor: texto.

```yaml
- kick: "<red>Vuelve pronto"
```

#### `launch`
Impulsa hacia donde mira (sin valor, fuerza 1.5; máximo 10).

Valor: expresión (fuerza).

```yaml
- launch: 2
```

#### `remove_effect`
Quita un efecto, o todos (all).

Valor: EFECTO o all.

```yaml
- remove_effect: SPEED
```

#### `remove_item`
Quita ítems por material, tengan o no nombre.

Valor: MATERIAL;cantidad.

```yaml
- remove_item: "EMERALD;5"
```

#### `set_food`
Fija el hambre (0 a 20).

Valor: expresión.

```yaml
- set_food: 20
```

#### `teleport`
Teletransporta al jugador o la entidad; ~ es relativo a su posición.

Valor: mundo;x;y;z;yaw;pitch.

```yaml
- teleport: "world;0.5;100;0.5"
```

#### `velocity`
Fija la velocidad.

Valor: x;y;z.

```yaml
- velocity: "0;1.2;0"
```

### Mundo

#### `explosion`
Explosión donde está el jugador (potencia máxima 10).

Valor: potencia;fuego;romper bloques.

```yaml
- explosion: "2;false;false"
```

#### `lightning`
Cae un rayo (solo el efecto, sin daño) donde está el jugador o en mundo;x;y;z.

Valor: (sin valor) o mundo;x;y;z.

```yaml
- lightning
```

#### `particle`
Partículas donde está el jugador (DUST lleva el color r;g;b).

Valor: PARTÍCULA;cantidad;dx;dy;dz;velocidad[;r;g;b].

```yaml
- particle: "HEART;10"
```

#### `set_block`
Cambia un bloque: con solo el material, el bloque del evento; con coordenadas, ese (~ relativo).

Valor: [mundo;]x;y;z;MATERIAL o MATERIAL.

```yaml
- set_block: "~;~-1;~;GOLD_BLOCK"
```

#### `summon`
Invoca una entidad donde está el jugador o en mundo;x;y;z.

Valor: TIPO[;mundo;x;y;z].

```yaml
- summon: ZOMBIE
```

### Evento

#### `cancel`
Cancela el evento (o no, con false o una expresión).

Valor: (sin valor) o expresión.

```yaml
- cancel
```

#### `cancel_drops`
El evento no suelta ítems (block_break, entity_death, player_kill).

Valor: (sin valor) o expresión.

```yaml
- cancel_drops
```

#### `deny_join`
Rechaza la entrada con un mensaje (player_pre_login).

Valor: texto.

```yaml
- deny_join: "<red>El servidor está en mantenimiento"
```

#### `keep_inventory`
El jugador conserva el inventario y el nivel al morir (player_death).

Valor: (sin valor) o expresión.

```yaml
- keep_inventory
```

#### `set_damage`
Fija el daño del evento: un número o un porcentaje del actual.

Valor: número o porcentaje (150%).

```yaml
- set_damage: "150%"
```

#### `set_death_message`
Cambia el mensaje de muerte (none lo oculta).

Valor: texto o none.

```yaml
- set_death_message: "<red>{player.name} cayó"
```

#### `set_event`
Cambia una propiedad del evento con su setter.

Valor: propiedad = expresión.

```yaml
- set_event: "damage = event.damage * 2"
```

#### `set_join_message`
Cambia el mensaje de entrada (none lo oculta).

Valor: texto o none.

```yaml
- set_join_message: "<green>+ {player.name}"
```

#### `set_message`
Cambia el mensaje de chat (player_chat).

Valor: texto.

```yaml
- set_message: "<gray>{message}"
```

#### `set_quit_message`
Cambia el mensaje de salida (none lo oculta).

Valor: texto o none.

```yaml
- set_quit_message: none
```

Alias (de ConditionalEvents y sinónimos): `bc` → `broadcast`, `call_event` → `call`, `cancel_event` → `cancel`, `centered_message` → `message`, `console_command` → `console`, `execute` → `call`, `give_potion_effect` → `effect`, `json_message` → `message`, `lightning_strike` → `lightning`, `mini_message` → `message`, `msg` → `message`, `player_command` → `command`, `player_send_chat` → `chat`, `playsound` → `sound`, `potion` → `effect`, `prevent_join` → `deny_join`, `remove_potion_effect` → `remove_effect`, `set_on_fire` → `fire`, `stopsound` → `stop_sound`, `tp` → `teleport`, `wait` → `delay`.

## Funciones
Operadores: `&&` `||` `!` (`and` `or` `not`), `==` `!=` `<` `<=` `>` `>=`, `~=` (igual sin mayúsculas), `contains` `startsWith` `endsWith` `matches` `in`, `+ - * / %`, `c ? a : b`. Variables: `var.x` (de la ejecución), `pvar.x` (del jugador), `gvar.x` (global).

| Función | Qué hace |
|---|---|
| `abs(x)` | Valor absoluto. |
| `bool(x)` | Convierte a booleano. |
| `ceil(x)` | Redondea hacia arriba. |
| `chance(p)` | true con una probabilidad de p por ciento. |
| `clamp(x, min, max)` | x limitado al rango [min, max]. |
| `cooldown(regla)` | Segundos de enfriamiento que le quedan al jugador en esa regla. |
| `date([patrón])` | Fecha de hoy con un patrón de Java (yyyy-MM-dd por defecto). |
| `default(x, otro)` | x, o otro si x es nulo o vacío. |
| `distance(a, b)` | Distancia entre dos ubicaciones, entidades o bloques (-1 si están en mundos distintos). |
| `floor(x)` | Redondea hacia abajo. |
| `format(x[, decimales])` | Número con separador de miles (1,234.5). |
| `has_item(material[, n])` | true si el jugador lleva al menos n (1 por defecto) del material. |
| `hour()` | Hora del día del servidor (0 a 23). |
| `if(condición, a, b)` | a si la condición se cumple, si no b (solo evalúa la rama elegida). |
| `in_area(mundo, x1, y1, z1, x2, y2, z2)` | true si el jugador está dentro del cubo. |
| `in_state(regla)` | true si el jugador está en el estado de esa regla state (o si el estado del servidor está activo). |
| `int(x)` | Convierte a entero, sin decimales (0 si no se puede). |
| `join(lista[, separador])` | Une una lista en un texto (", " por defecto). |
| `len(x)` | Largo de un texto, o tamaño de una lista o un mapa. |
| `lower(t)` | Texto en minúsculas. |
| `max(a, b, …)` | El mayor de los números (o de una lista). |
| `min(a, b, …)` | El menor de los números (o de una lista). |
| `minute()` | Minuto de la hora (0 a 59). |
| `now()` | Milisegundos desde 1970 (hora del servidor). |
| `num(x)` | Convierte a número (0 si no se puede). |
| `online(nombre)` | true si ese jugador está conectado. |
| `papi(texto[, jugador])` | Resuelve placeholders de PlaceholderAPI; si el resultado es un número, devuelve número. |
| `perm(permiso[, jugador])` | true si el jugador (el del evento por defecto) tiene el permiso. |
| `player(nombre)` | El jugador conectado con ese nombre, o nulo. |
| `players()` | Lista de jugadores conectados. |
| `pow(x, y)` | x elevado a y. |
| `pvar_of(jugador, nombre)` | Variable pvar de otro jugador (por nombre). |
| `random(a, b)` | Entero al azar entre a y b, ambos incluidos. |
| `randomf(a, b)` | Decimal al azar entre a y b. |
| `replace(t, buscar, poner)` | Reemplaza cada aparición de buscar. |
| `round(x[, decimales])` | Redondea (a 0 decimales por defecto). |
| `size(x)` | Igual que len. |
| `split(t, separador)` | Lista con las partes del texto. |
| `sqrt(x)` | Raíz cuadrada. |
| `str(x)` | Convierte a texto. |
| `strip(t)` | Quita los colores & y § y las etiquetas MiniMessage. |
| `substr(t, desde[, hasta])` | Parte de un texto (posiciones desde 0). |
| `trim(t)` | Quita los espacios de los extremos. |
| `upper(t)` | Texto en mayúsculas. |
| `uses(regla)` | Usos que lleva el jugador en esa regla. |
| `weekday()` | Día de la semana: 1 es lunes, 7 es domingo. |
