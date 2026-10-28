# Referencia de OmniEvents

> Generada del código con `./gradlew :omnievents:schema` (o `/omni schema` en el servidor): no se edita a mano.
> Una prueba falla si queda desactualizada. Guía y ejemplos: [README](../../plugins/omnievents/README.md).

Autocompletado y errores en VS Code (extensión YAML de Red Hat): pon esta línea al inicio de cada archivo de reglas.

```yaml
# yaml-language-server: $schema=https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/docs/omnievents/omnievents.schema.json
```

El editor revisa opciones, disparadores, acciones y la forma de las condiciones; las expresiones, los materiales, los sonidos y los efectos se revisan con `/omni validate`. Los nombres van en minúsculas.

Contenido: [opciones de una regla](#opciones-de-una-regla) · [menús](#menús) · [paquetes y plantillas](#paquetes-y-plantillas) · [condiciones](#condiciones) · [disparadores](#disparadores) · [acciones](#acciones) · [funciones](#funciones)

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
| `cooldown_bypass` | texto | todos | Permiso que salta el enfriamiento de esta regla (spec 014); sin la opción, omnievents.bypasscooldown.&lt;regla>. Solo cuenta si está dado al jugador (a un OP sin él no lo salta; eso es cooldown-bypass-op). |
| `limit` | entero | todos | Usos máximos (0 = sin límite). |
| `one_time` | `true` / `false` | todos | true equivale a limit: 1 (el one_time de ConditionalEvents). |
| `limit_scope` | `player`, `jugador`, `global`, `server`, `servidor` | todos | player (por jugador, por defecto) o global. |
| `limit_reset` | `never`, `nunca`, `daily`, `diario`, `weekly`, `semanal`, `monthly`, `mensual` o una duración | todos | Cuándo se reinician los usos: never, daily, weekly, monthly o una duración (12h). |
| `on_limit` | acciones | todos | Acciones si ya no quedan usos. |
| `cost` | mapa (ver abajo) | todos | Lo que cuesta la regla, todo o nada: money (Vault), levels, exp, items y variables pvar.x o gvar.x. Se cobra después de las condiciones, el límite y el enfriamiento; si algo no alcanza no se cobra nada ni cuenta el uso. |
| `on_cost_fail` | acciones | todos | Acciones si el costo no alcanza o un cobro falla (lo cobrado se devuelve). var.cost_missing dice qué falta; var.cost_missing_list, las claves. |
| `menu` | mapa (ver [menús](#menús)) | todos | Esta entrada es un menú de inventario (spec 013) en vez de una regla: title, rows, items con sus clics… Se abre con open_menu; a Bedrock, con Floodgate, como formulario de botones. |
| `tests` | lista de casos | todos | Pruebas de la regla: casos con given (el contexto falso) y expect (lo esperado). Corren con /omni test y al cargar, sin efectos. |
| `template` | `true` / `false` | todos | true: la regla es una plantilla; no corre sola (no necesita trigger) y otras la usan con extends. |
| `extends` | texto | todos | Plantilla de la que hereda (template: true): copia sus claves y las que escribe esta regla las reemplazan enteras. En un paquete busca primero en el paquete; paquete:plantilla es absoluto. |
| `with` | mapa nombre: valor | todos | Valores de los parámetros ${nombre} para la plantilla de extends (ganan a los params de la plantilla y del paquete). |
| `params` | mapa nombre: valor | todos | Valores por defecto de los parámetros ${nombre} de esta regla o plantilla. |
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
| `variables` | texto o lista | `var_expire` | Variables que hacen correr la regla (p. ej. [racha, bono]); sin la opción, todas. |
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

Claves de `cost` (spec 012; todo o nada, con `on_cost_fail` si algo no alcanza):

| Clave | Qué cobra |
|---|---|
| `money` | Dinero de la economía de Vault (expresión, > 0). Necesita Vault y un plugin de economía. |
| `levels` | Niveles de experiencia (entero > 0). |
| `exp` | Puntos de experiencia (entero > 0). |
| `items` | Ítems por material, tengan o no nombre: "MATERIAL;cantidad" o una lista de esos (sin la armadura). Al devolver, vuelven las mismas pilas. |
| `pvar.<nombre>` | Variable numérica del jugador (expresión, > 0); nula cuenta como 0. |
| `gvar.<nombre>` | Variable numérica global (expresión, > 0); sirve también sin jugador. |

Cada caso de `tests` (spec 010; corren con `/omni test` y al cargar):

| Clave | Qué hace |
|---|---|
| `name` | Nombre del caso (sin él: caso 1, caso 2…). |
| `given` | Contexto falso: rutas con puntos y su valor (block.type: DIAMOND_ORE, player.level: 12, event.damage: 5), variables iniciales (pvar.x, gvar.x, var.x, block.data.x), placeholders ("%vault_eco_balance%": 100) y, para cost y las integraciones (spec 012), money (saldo), inventory (material: cantidad) y regions (ids). |
| `expect` | Lo esperado: fired, branch, variables, messages, commands, actions, cancelled y assert. |
| `seed` | Semilla de random en este caso (0 por defecto): el mismo número elige siempre la misma rama. |

Claves de `expect` (además, variables: `pvar.x`, `gvar.x`, `var.x`, `block.data.x`; `null` = que no exista):

| Clave | Qué hace |
|---|---|
| `fired` | true si corrieron las acciones principales (actions, u on_enter en un state). |
| `branch` | La rama que corrió: actions, else, deny (o deny conditions[1]), none, on_cost_fail (el costo no alcanzó) u on_enter en un state. |
| `messages` | Textos que deben aparecer, en orden y sin formato, en los mensajes que mandaría (message, broadcast, actionbar, title…). |
| `commands` | Textos que deben aparecer, en orden, en los comandos que correría (console, command). |
| `actions` | Acciones que correría, en orden: el tipo (give_item) o el tipo y un texto ("sound: LEVELUP"). |
| `cancelled` | true si cancela el evento. |
| `assert` | Expresión o lista de expresiones que deben dar verdadero al terminar ("pvar.d >= 10"). |

## Menús
Una entrada con `menu:` (en vez de `trigger:`) es un menú de inventario (spec 013). Se abre con la acción `open_menu` y cada clic es un cuerpo de regla (`conditions`, `cost`, `cooldown`, `actions`, `tests`…) con el disparador `menu_click`: se llama como su ruta (`tienda.menu.items.espada.click`). Con Floodgate, a un jugador de Bedrock se le muestra como un formulario de botones.

| Clave de `menu` | Qué hace |
|---|---|
| `title` | Título (MiniMessage, & y {expresiones}); se evalúa al abrir (los ítems, además, después de cada clic). |
| `rows` | Filas del inventario, de 1 a 6 (3 por defecto). |
| `fill` | Material para las casillas vacías (p. ej. GRAY_STAINED_GLASS_PANE), sin nombre. |
| `permission` | Permiso para abrirlo; sin él no se abre. |
| `conditions` | Requisito para abrirlo, como las conditions de una regla (con su deny); si falla, no se abre. |
| `else` | Acciones si falla una condición sin deny (el menú no se abre). |
| `cost` | Lo que cuesta abrirlo, todo o nada (spec 012); on_cost_fail si no alcanza. |
| `on_cost_fail` | Acciones si el costo de abrir no alcanza. |
| `cooldown` | Enfriamiento entre aperturas. |
| `on_cooldown` | Acciones si se intenta abrir en enfriamiento. |
| `on_open` | Acciones al abrirse (después del requisito). |
| `on_close` | Acciones al cerrarse (el jugador lo cierra, close_menu o close: true; no al recargar). |
| `bedrock` | Cómo se ve en Bedrock con Floodgate: content (texto del formulario) y enabled (false: el inventario). |
| `items` | Los ítems: nombre → slot, item, view y sus clics. Si dos comparten casilla se ve el primero visible. |
| `tests` | Pruebas de la apertura (given y expect, como en una regla); las de cada clic van en el clic. |

| Clave de un ítem (`menu.items.<nombre>`) | Qué hace |
|---|---|
| `slot` | Casilla (desde 0): un número, una lista o un rango "0-8". |
| `slots` | Sinónimo de slot. |
| `item` | El ítem, como en give_item: "MATERIAL;cantidad;nombre;lore1\|lore2" o un mapa con material, name, lore, amount, glow… Acepta {expresiones}. |
| `view` | Expresión: el ítem solo se ve si da verdadero (se revisa al abrir y después de cada clic). |
| `click` | Lo que hace cualquier clic: un cuerpo de regla (conditions, cost, cooldown, actions, tests…) sin trigger. |
| `left_click` | Clic izquierdo (gana a click). En Bedrock, si no hay click. |
| `right_click` | Clic derecho (gana a click). |
| `shift_click` | Shift + clic, izquierdo o derecho (gana a click). |
| `bedrock` | Su botón en Bedrock: button (texto) e image (URL o ruta de textura). |
| `<clic>.close` | true: cierra el menú después de las acciones del clic. |

| Clave de `bedrock` | Dónde | Qué hace |
|---|---|---|
| `content` | menú | Texto bajo el título del formulario ({expresiones}). |
| `enabled` | menú | false: a Bedrock también se le muestra el inventario (Geyser lo traduce). |
| `button` | ítem | Texto del botón ({expresiones}); por defecto, el nombre del ítem sin formato. |
| `image` | ítem | Imagen del botón: una URL (http…) o una ruta de textura (textures/items/diamond). |

## Paquetes y plantillas
Una carpeta de `rules/` con un `pack.yml` es un paquete (spec 011): sus reglas se llaman `paquete:regla` y, desde ellas, un nombre sin paquete (`call`, `cooldown()`, `uses()`, `in_state()`, `extends`) se busca primero en el paquete y luego entre las globales. `${nombre}` en los textos de una regla de un paquete, que usa `extends` o que declara `params` se reemplaza al cargar con `with`, `params` de la regla y de sus plantillas, y `params` del paquete, en ese orden; `\${` deja `${`. Esquema de `pack.yml`: `https://raw.githubusercontent.com/leonardos4enz/fabrica-de-plugins/main/docs/omnievents/omnievents-pack.schema.json`.

| Clave de `pack.yml` | Qué hace |
|---|---|
| `name` | Nombre del paquete (por defecto, el de la carpeta): minúsculas, números, _ y -. Sus reglas se llaman nombre:regla. |
| `version` | Versión del paquete, x.y.z (1.0.0 por defecto). Al subirla corren las migrations que correspondan. |
| `description` | Qué hace el paquete (lo muestra /omni pack info). |
| `author` | Quién lo hizo. |
| `enabled` | false: sus reglas cargan desactivadas y no corren sus migraciones. |
| `params` | Parámetros de todas las reglas del paquete: ${nombre} en sus textos se reemplaza al cargar. |
| `requires` | Lo que necesita para cargar: omnievents y packs, con restricciones como ">=1.2.0, &lt;2.0.0". |
| `migrations` | Versión → acciones de servidor que corren una vez al subir a esa versión (var.from y var.to: de qué versión a cuál). |

Claves de `requires`:

| Clave | Qué hace |
|---|---|
| `omnievents` | Versión de OmniEvents que necesita (p. ej. ">=1.6.0"). |
| `packs` | Otros paquetes que necesita: nombre → restricción (p. ej. plantillas: ">=1.0.0"). |

```yaml
# rules/recompensas/pack.yml
version: 1.1.0
params: { moneda: pvar.monedas }
migrations:
  "1.1.0":
    - log: "recompensas {var.from} → {var.to}"
# rules/recompensas/reglas.yml → recompensas:base (plantilla) y recompensas:diaria
base:
  template: true
  params: { cantidad: 1 }
  trigger: command
  actions:
    - set: "${moneda} += ${cantidad}"
diaria:
  extends: base
  with: { cantidad: 50 }
  command: diaria
```

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
| `player_move` | `event` `world` `location` `player` `from` `to` | Un jugador se mueve (move_mode: block, chunk o any). player.location todavía es el origen (from): el bloque al que llega es to.block y la altura nueva, to.y. *(se puede cancelar)* |
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
| `player_statistic` | `event` `world` `location` `player` `statistic` `previous_value` `new_value` `material` `entity_type` | Sube una estadística del jugador (statistic: JUMP, MOB_KILLS…; previous_value, new_value; material o entity_type si la estadística los tiene). Algunas, como caminar, suben muy seguido. *(se puede cancelar)* |
| `player_air_change` | `event` `world` `location` `player` `old_air` `new_air` `max_air` | Cambia el aire de un jugador (old_air, new_air, max_air, en ticks: 300 es lleno). Bajo el agua corre en cada tick. *(se puede cancelar)* |
| `player_tab_complete` | `event` `world` `location` `player` `buffer` `cmd` `args` `last` `completions` | Un jugador pide autocompletar un comando de un plugin (buffer: lo escrito; cmd, args; last: la palabra a medio escribir; completions: las sugerencias). La acción tab_complete las cambia. Los comandos de Minecraft los completa el cliente: no pasan por aquí. *(se puede cancelar)* |

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
| `item_repair` | `event` `world` `location` `player` `item` `repair_type` `base_item` `addition` `rename_text` `level_cost` | Un jugador saca el resultado de un yunque o una afiladora (repair_type: ANVIL o GRINDSTONE; rename_text, level_cost, base_item, addition). *(se puede cancelar)* |
| `item_mend` | `event` `world` `location` `player` `item` `amount` `orb` | Mending repara un ítem del jugador con un orbe de experiencia (spec 015; amount: durabilidad que repara, orb). cancel hace que el orbe dé experiencia en vez de reparar. *(se puede cancelar)* |
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
| `var_expire` | `player` `world` `location` `variable` `value` `scope` | Caduca una variable con expire (spec 009): una pvar de un jugador conectado (con player) o una gvar. variable: el nombre; value: lo último que valía; scope: pvar o gvar. |
| `custom` | `player` `event` `world` `location` | Cualquier evento de Bukkit o de otro plugin por su clase (event, player_from). |

### Integraciones
| Disparador | Raíces | Qué es |
|---|---|---|
| `region_enter` | `player` `world` `location` `region` `event` | El jugador entra a una región de WorldGuard (region.id, region.priority); cancel impide la entrada. Necesita WorldGuard. |
| `region_leave` | `player` `world` `location` `region` `event` | El jugador sale de una región de WorldGuard (region.id); cancel lo deja dentro. Necesita WorldGuard. |
| `npc_click` | `event` `world` `location` `player` `npc` `target` `item` | Un jugador hace clic derecho en un NPC de Citizens (npc.id, npc.name, npc.entity). Necesita Citizens. *(se puede cancelar)* |
| `npc_left_click` | `event` `world` `location` `player` `npc` `target` `item` | Un jugador golpea (clic izquierdo) un NPC de Citizens (npc.id, npc.name, npc.entity). Necesita Citizens. *(se puede cancelar)* |
| `player_receive_chat` | `event` `world` `location` `player` `message` `message_legacy` `json` `type` | Un jugador va a recibir un mensaje del chat o del sistema (asíncrono, en el hilo de red): message sin formato, message_legacy con códigos &, json, type (SYSTEM, DISGUISED o PLAYER). cancel hace que no lo vea; la barra de acción no cuenta. Es texto no confiable. Necesita ProtocolLib. *(se puede cancelar, asíncrono)* |

### Menús
| Disparador | Raíces | Qué es |
|---|---|---|
| `menu_open` | `player` `world` `location` `menu` | Abrir un menú (open_menu): su permission, conditions y cost son el requisito y on_open las acciones. Lo compila el menú; no se escribe a mano. |
| `menu_click` | `player` `world` `location` `menu` `slot` `click` `item` | Un clic en un ítem de un menú (click, left_click, right_click, shift_click). click: left, right, shift_left, shift_right, middle, number, drop o bedrock. Lo compila el menú; no se escribe a mano. |
| `menu_close` | `player` `world` `location` `menu` | Cerrar un menú (on_close). Lo compila el menú; no se escribe a mano. |

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
Asigna una variable: var (de la ejecución), pvar (del jugador, persistente), gvar (global, persistente) u objeto.data.nombre (de un bloque, entidad o ítem, guardada en el objeto); también +=, -=, *= y /=.

Valor: ámbito.nombre = expresión, u objeto.data.nombre = expresión.

Claves:
- `expire`: Duración tras la que la variable se borra sola (10m, 1h; spec 009). Cada set la vuelve a contar; un set sin expire la quita.

```yaml
- set: "pvar.racha += 1"
  expire: 10m
```

#### `stop`
Detiene las acciones que faltan de esta ejecución.

Valor: (sin valor).

```yaml
- stop
```

#### `unset`
Borra una variable (y su caducidad).

Valor: ámbito.nombre u objeto.data.nombre.

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
Muestra un texto en la barra de acción; con duración (texto;ticks, como ConditionalEvents, o la clave duration) se reenvía cada 2 s hasta cubrirla (spec 016).

Valor: texto[;ticks].

Claves:
- `duration`: Cuánto se mantiene: ticks (120) o con unidad (6s).

```yaml
- actionbar: "<gold>Evento en 1 minuto"
  duration: 6s
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

#### `centered_message`
Como message, con cada línea centrada en el chat (spec 014; la fuente por defecto de Minecraft, negritas incluidas). \n o &lt;newline> separan líneas.

Valor: texto.

```yaml
- centered_message: "<gold><b>EVENTO</b>\n<gray>Empieza en 5 minutos"
```

#### `console_message`
Escribe un mensaje en la consola.

Valor: texto.

```yaml
- console_message: "La regla corrió para {player.name}"
```

#### `json_message`
Envía un mensaje escrito en JSON de texto de Minecraft (clic, hover…), como json_message de ConditionalEvents (spec 015). Se convierte al cargar; los {expresiones} dentro de los textos se resuelven y escapan como en message.

Valor: JSON de texto.

```yaml
- json_message: '{"text":"Clic aquí","color":"gold","clickEvent":{"action":"run_command","value":"/spawn"}}'
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

Claves:
- `location`: Dónde suena: mundo;x;y;z (~ relativo; spec 015). Lo oyen los cercanos, no solo el jugador.

```yaml
- sound: "ENTITY_PLAYER_LEVELUP;1;1.2"
  location: "~;~;~"
```

#### `stop_sound`
Detiene un sonido del jugador, o todos (all o sin valor); también claves de paquete de recursos (mi_pack:musica, spec 015).

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

#### `close_menu`
Cierra el menú de OmniEvents que tenga abierto el jugador (otro inventario no lo toca); corre su on_close.

Valor: (sin valor).

```yaml
- close_menu
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
- `flags`: Banderas (spec 015): lista o texto con comas de HIDE_ENCHANTS, HIDE_ATTRIBUTES…
- `durability`: Daño del ítem (durabilidad gastada, como en ConditionalEvents).
- `item_model`: Modelo del ítem: clave espacio:modelo (1.21.4+).
- `skull_owner`: Para PLAYER_HEAD: nombre del jugador de la cabeza.
- `skull_texture`: Para PLAYER_HEAD: textura en base64.

Claves:
- `location`: Dónde soltarlo: mundo;x;y;z (~ relativo).

```yaml
- drop_item: "GOLD_INGOT;3"
  location: "world;0;64;0"
```

#### `effect`
Da un efecto de poción (segundos negativos: infinito); ícono false lo oculta de la pantalla (spec 016).

Valor: EFECTO;segundos;nivel;partículas;ícono.

```yaml
- effect: "SPEED;30;2;false;false"
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
Da un ítem; lo que no cabe cae al suelo. En forma de mapa, slot lo pone en una casilla (spec 015).

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
- `flags`: Banderas (spec 015): lista o texto con comas de HIDE_ENCHANTS, HIDE_ATTRIBUTES…
- `durability`: Daño del ítem (durabilidad gastada, como en ConditionalEvents).
- `item_model`: Modelo del ítem: clave espacio:modelo (1.21.4+).
- `skull_owner`: Para PLAYER_HEAD: nombre del jugador de la cabeza.
- `skull_texture`: Para PLAYER_HEAD: textura en base64.
- `slot`: Casilla donde ponerlo: 0 a 40 o HAND, OFF_HAND, HELMET, CHESTPLATE, LEGGINGS, BOOTS. Si está ocupada, va al inventario (salvo slot_replace).
- `slot_replace`: true: reemplaza lo que haya en la casilla.

```yaml
- give_item: "DIAMOND;2;<aqua>Diamante;<gray>Premio"
```

#### `give_level`
Da niveles de experiencia.

Valor: expresión.

```yaml
- give_level: 5
```

#### `give_money`
Deposita dinero en la economía de Vault (necesita Vault y un plugin de economía).

Valor: expresión (cantidad > 0).

```yaml
- give_money: 100
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

#### `open_menu`
Abre un menú (una entrada con menu:) al jugador; a uno de Bedrock con Floodgate se le muestra como formulario de botones. Los args llegan al menú y a sus clics como var.&lt;nombre>.

Valor: nombre del menú, o un mapa con menu y args.

En forma de mapa:
- `menu`: Menú que se abre (en un paquete, primero el del paquete).
- `args`: Mapa nombre: expresión; en el menú y sus clics, var.&lt;nombre>.

```yaml
- open_menu: tienda
```

#### `remove_effect`
Quita un efecto, o todos (all).

Valor: EFECTO o all.

```yaml
- remove_effect: SPEED
```

#### `remove_item`
Quita ítems por material, tengan o no nombre; con name: o lorecontains: (spec 015), solo los de ese nombre o con esa línea de lore (sin colores).

Valor: MATERIAL;cantidad[;name:&lt;nombre>][;lorecontains:&lt;texto>].

```yaml
- remove_item: "PAPER;1;name:Ticket VIP"
```

#### `remove_item_slot`
Quita ítems de una casilla (spec 015): HAND, OFF_HAND, HELMET, CHESTPLATE, LEGGINGS, BOOTS o 0 a 40; sin cantidad, toda la pila.

Valor: CASILLA;cantidad.

```yaml
- remove_item_slot: "HAND;1"
```

#### `set_caught_item`
Cambia lo que se pescó (player_fish) por otro ítem, con el formato de give_item (spec 015; set_item de ConditionalEvents).

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
- `flags`: Banderas (spec 015): lista o texto con comas de HIDE_ENCHANTS, HIDE_ATTRIBUTES…
- `durability`: Daño del ítem (durabilidad gastada, como en ConditionalEvents).
- `item_model`: Modelo del ítem: clave espacio:modelo (1.21.4+).
- `skull_owner`: Para PLAYER_HEAD: nombre del jugador de la cabeza.
- `skull_texture`: Para PLAYER_HEAD: textura en base64.

```yaml
- set_caught_item: "DIAMOND;1;<aqua>Diamante del lago"
```

#### `set_food`
Fija el hambre (0 a 20).

Valor: expresión.

```yaml
- set_food: 20
```

#### `take_money`
Retira dinero de la economía de Vault; si no alcanza, es un error y no retira nada.

Valor: expresión (cantidad > 0).

```yaml
- take_money: "var.precio"
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

#### `firework`
Lanza un fuego artificial desde el jugador (o cada destinatario de to) o desde location; sin daño salvo damage:true (spec 014). Forma de ConditionalEvents: pares clave:valor separados por espacios.

Valor: colors:C1,C2 [type:BALL|BALL_LARGE|STAR|BURST|CREEPER] [power:0-3] [fade:C1,C2] [flicker:true] [trail:true] [location:mundo;x;y;z] [damage:true] [shot_direction:xmin,xmax;ymin,ymax;zmin,zmax;vmin-vmax]; colores por nombre (RED, AQUA…) o #RRGGBB; shot_direction lo dispara en ángulo (spec 016).

```yaml
- firework: "colors:RED,#FFAA00 type:STAR power:1 fade:WHITE trail:true"
```

#### `lightning`
Cae un rayo (solo el efecto, sin daño) donde está el jugador o en mundo;x;y;z.

Valor: (sin valor) o mundo;x;y;z.

```yaml
- lightning
```

#### `particle`
Partículas donde está el jugador o en location (DUST lleva el color r;g;b).

Valor: PARTÍCULA;cantidad;dx;dy;dz;velocidad[;r;g;b].

Claves:
- `location`: Dónde: mundo;x;y;z (~ relativo; spec 015). Sin ella, un bloque encima del jugador.
- `force`: true: se ve desde lejos (spec 015).
- `for_player`: true: solo la ve el jugador (spec 015).

```yaml
- particle: "HEART;10"
  location: "~;~2;~"
  force: false
  for_player: true
```

#### `set_block`
Cambia un bloque: con solo el material, el bloque del evento; con coordenadas, ese (~ relativo). El material acepta datos de bloque: OAK_STAIRS[facing=north] (spec 015).

Valor: [mundo;]x;y;z;MATERIAL[datos] o MATERIAL[datos].

```yaml
- set_block: "~;~-1;~;GOLD_BLOCK"
```

#### `summon`
Invoca una entidad donde está el jugador o en mundo;x;y;z. En forma de mapa (spec 015): name, health, amount, scale y equipo.

Valor: TIPO[;mundo;x;y;z] o un mapa.

En forma de mapa:
- `entity`: Tipo de entidad (ZOMBIE…).
- `type`: Sinónimo de entity.
- `location`: Dónde: mundo;x;y;z (~ relativo); sin ella, donde está el jugador.
- `name`: Nombre visible (MiniMessage, & y {expresiones}).
- `health`: Vida máxima y actual.
- `amount`: Cuántas (1 por defecto, máximo 50).
- `scale`: Escala de tamaño (atributo scale; 1 es normal).
- `equipment`: Armadura: casco,peto,pantalones,botas (materiales o none).
- `hand_equipment`: Manos: principal,secundaria (materiales o none).

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
El jugador conserva al morir (player_death) los ítems, el nivel o los dos: items, xp o all (spec 015); sin valor o con una expresión verdadera, all.

Valor: items, xp, all, (sin valor) o expresión.

```yaml
- keep_inventory: items
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

#### `tab_complete`
Cambia las sugerencias de autocompletado (player_tab_complete, spec 015): set deja solo las de la lista, add las suma (las dos solo las que empiezan por lo que lleva escrito, y set las ordena), remove las quita y clear las borra todas.

Valor: set|add|remove|clear;a,b,c.

```yaml
- tab_complete: "set;spawn,tienda,arena"
```

Alias (de ConditionalEvents y sinónimos): `bc` → `broadcast`, `call_event` → `call`, `cancel_event` → `cancel`, `console_command` → `console`, `execute` → `call`, `give_potion_effect` → `effect`, `lightning_strike` → `lightning`, `mini_message` → `message`, `msg` → `message`, `player_command` → `command`, `player_send_chat` → `chat`, `playsound` → `sound`, `potion` → `effect`, `prevent_join` → `deny_join`, `remove_potion_effect` → `remove_effect`, `set_item` → `set_caught_item`, `set_on_fire` → `fire`, `stopsound` → `stop_sound`, `tp` → `teleport`, `wait` → `delay`.

## Funciones
Operadores: `&&` `||` `!` (`and` `or` `not`), `==` `!=` `<` `<=` `>` `>=`, `~=` (igual sin mayúsculas), `contains` `startsWith` `endsWith` `matches` `in`, `+ - * / %`, `c ? a : b`. Variables: `var.x` (de la ejecución), `pvar.x` (del jugador), `gvar.x` (global).

| Función | Qué hace |
|---|---|
| `abs(x)` | Valor absoluto. |
| `block_at(mundo, x, y, z)` | El bloque en esas coordenadas (.type, .data…; spec 015); nulo si el mundo no existe o el chunk no está cargado (no lo carga). Solo en el hilo principal. |
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
| `has_item(material[, n])` | true si el jugador lleva al menos n (1 por defecto) del material, tengan o no nombre (sin contar la armadura). |
| `hour()` | Hora del día del servidor (0 a 23). |
| `if(condición, a, b)` | a si la condición se cumple, si no b (solo evalúa la rama elegida). |
| `in_area(mundo,x1, y1, z1, x2, y2, z2)` | true si el jugador está dentro del cubo. |
| `in_region(id)` | true si el jugador está en esa región de WorldGuard (sin WorldGuard, false). En tests:, given.regions. |
| `in_state(regla)` | true si el jugador está en el estado de esa regla state (o si el estado del servidor está activo). |
| `int(x)` | Convierte a entero, sin decimales (0 si no se puede). |
| `is_bedrock()` | true si el jugador entró desde Bedrock (Geyser + Floodgate); sin Floodgate, false. En tests:, given.bedrock. |
| `is_npc(entidad)` | true si la entidad es un NPC de Citizens. En tests:, un objeto de given con npc: true. |
| `is_number(x)` | true si x es un número o un texto con un número (spec 015; %is_number_x% de ConditionalEvents). |
| `is_outside([entidad])` | true si no hay ningún bloque (que no sea aire) encima del jugador hasta el techo del mundo (%player_is_outside% de ConditionalEvents). Solo en el hilo principal. |
| `join(lista[, separador])` | Une una lista en un texto (", " por defecto). |
| `len(x)` | Largo de un texto, o tamaño de una lista o un mapa. |
| `location(mundo, x, y, z)` | Una ubicación (mundo por nombre o un mundo); nulo si el mundo no existe. |
| `looking_block(distancia[, entidad])` | El bloque que mira el jugador (o la entidad) a esa distancia o menos, o nulo. Solo en el hilo principal. |
| `looking_entity(distancia[, entidad])` | La entidad que mira el jugador (o la entidad) a esa distancia o menos, o nulo. Solo en el hilo principal. |
| `lower(t)` | Texto en minúsculas. |
| `max(a, b, …)` | El mayor de los números (o de una lista). |
| `min(a, b, …)` | El menor de los números (o de una lista). |
| `minute()` | Minuto de la hora (0 a 59). |
| `money()` | Saldo del jugador en la economía de Vault; nulo sin Vault o sin jugador. En tests:, el de given.money. |
| `money_format(n)` | La cantidad con la moneda de la economía (p. ej. $1,500.00); sin economía, el número. |
| `near(a, b, radio)` | true si a y b (ubicaciones, entidades o bloques) están en el mismo mundo a radio bloques o menos (%is_nearby_…% de ConditionalEvents). |
| `now()` | Milisegundos desde 1970 (hora del servidor). |
| `num(x)` | Convierte a número (0 si no se puede). |
| `online(nombre)` | true si ese jugador está conectado. |
| `papi(texto[, jugador])` | Resuelve placeholders de PlaceholderAPI; si el resultado es un número, devuelve número. |
| `perm(permiso[, jugador])` | true si el jugador (el del evento por defecto) tiene el permiso. |
| `pick(lista)` | Un elemento al azar de la lista, o de los argumentos con pick(a, b, …); nulo si no hay ninguno. pick(players()).name es un jugador al azar. |
| `player(nombre)` | El jugador conectado con ese nombre, o nulo. |
| `players()` | Lista de jugadores conectados. |
| `pow(x, y)` | x elevado a y. |
| `pvar_of(jugador, nombre)` | Variable pvar de otro jugador (por nombre). |
| `random(a, b)` | Entero al azar entre a y b, ambos incluidos. |
| `random_last()` | El último número de random(a, b) o %random_a_b% en este hilo, o nulo (%random_last% de ConditionalEvents). |
| `randomf(a, b)` | Decimal al azar entre a y b. |
| `regions()` | Ids de las regiones de WorldGuard donde está el jugador, de mayor a menor prioridad (sin WorldGuard, vacía). |
| `relative(entidad, adelante, lado, arriba)` | La ubicación de la entidad corrida según hacia dónde mira (solo el giro horizontal): adelante y derecha positivos, arriba positivo (%player_location_…% de ConditionalEvents). |
| `replace(t, buscar, poner)` | Reemplaza cada aparición de buscar. |
| `round(x[, decimales])` | Redondea (a 0 decimales por defecto). |
| `size(x)` | Igual que len. |
| `slice(x, desde[, hasta])` | Parte de una lista (o de un texto): posiciones desde 0, hasta sin incluir y recortado al largo. join(slice(args, 1, 4), ' ') une los argumentos 2 a 4. |
| `split(t, separador)` | Lista con las partes del texto. |
| `sqrt(x)` | Raíz cuadrada. |
| `str(x)` | Convierte a texto. |
| `strip(t)` | Quita los colores & y § y las etiquetas MiniMessage. |
| `substr(t, desde[, hasta])` | Parte de un texto (posiciones desde 0). |
| `trim(t)` | Quita los espacios de los extremos. |
| `ttl(variable)` | Segundos que le quedan a una variable con expire (pvar.x, gvar.x u objeto.data.x); -1 si no caduca; nulo si no existe. |
| `upper(t)` | Texto en mayúsculas. |
| `uses(regla)` | Usos que lleva el jugador en esa regla. |
| `weekday()` | Día de la semana: 1 es lunes, 7 es domingo. |
| `world(nombre)` | El mundo con ese nombre, o nulo (world('nether').time). |
